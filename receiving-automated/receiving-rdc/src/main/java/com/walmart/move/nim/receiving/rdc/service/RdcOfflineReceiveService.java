package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getTimeDifferenceInMillis;

import com.google.common.collect.Lists;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadBlobDataDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadChildContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadMessageDTO;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcOfflineRecieveUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang.StringUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** This class has all methods required for Offline Receiving flow. Author: s0g0g7u */
@Service
public class RdcOfflineReceiveService {
  @Autowired private RdcDaService rdcDaService;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private ReceiptService receiptService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcOfflineRecieveUtils rdcOfflineRecieveUtils;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  @Value("${label.data.insert.batch.size:100}")
  private int labelDataBatchSize;

  @Autowired private LabelDataService labelDataService;
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcOfflineReceiveService.class);
  private static final Logger LOG = LoggerFactory.getLogger(RdcOfflineReceiveService.class);

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Map<Object, Boolean> seen = new ConcurrentHashMap<>();
    return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

  /**
   * Perform offline receiving
   *
   * @param labelDataList
   */
  @Transactional
  @InjectTenantFilter
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "autoReceiveContainersForOfflineRcv")
  public void autoReceiveContainersForOfflineReceiving(
      List<LabelData> labelDataList, InstructionDownloadMessageDTO instructionDownloadMessageDTO)
      throws ReceivingException {

    TenantContext.get().setOfflineReceivingProcessingStart(System.currentTimeMillis());
    // Persisting the label data in the same transaction as of receipt, container and container
    // items
    // If the feature flag is true, then only the persistence will get executed in batches.
    // Also added the time trace logs.
    if (rdcManagedConfig.getEnableSingleTransactionForOffline()) {
      Lists.partition(labelDataList, labelDataBatchSize)
          .forEach(labelDataBatch -> labelDataService.saveAll(labelDataBatch));
    }

    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();

    // TODO: Changes have to be made below - get container label
    Map<String, InstructionDownloadBlobDataDTO> offlineRcvInfoMap =
        instructionDownloadMessageDTO.getMiscOfflineRcvInfoMap();
    LOGGER.info(
        "Populating delivery document and document lines for delivery number : {}",
        instructionDownloadMessageDTO.getDeliveryNumber());
    List<DeliveryDocument> deliveryDocumentList =
        fetchDeliveryDocumentForOfflineRcv(instructionDownloadMessageDTO);

    Map<String, DeliveryDocument> deliveryDocumentMap =
        deliveryDocumentList
            .stream()
            .collect(Collectors.toMap(DeliveryDocument::getTrackingId, Function.identity()));

    // Transform Label Data into received containers
    List<ReceivedContainer> receivedContainers =
        new ArrayList<>(
            transformLabelDataForOfflineRcv(
                labelDataList,
                deliveryDocumentMap,
                instructionDownloadMessageDTO.getHttpHeaders(),
                false));

    LOGGER.info(
        "Created received Containers for Delivery Nbr: {}, having size of received containers {} , having tracking ids: {}",
        instructionDownloadMessageDTO.getDeliveryNumber(),
        receivedContainers.size(),
        receivedContainers);

    // Populate containers and container items
    for (ReceivedContainer receivedContainer : receivedContainers) {
      DeliveryDocument deliveryDocument =
          deliveryDocumentMap.get(
              receivedContainer.getParentTrackingId() != null
                  ? receivedContainer.getParentTrackingId()
                  : receivedContainer.getLabelTrackingId());
      Integer projectedQty = getProjectedQtyBasedOnChild(offlineRcvInfoMap, receivedContainer);
      ContainerItem containerItem = null;
      Container container = null;
      containerItem =
          rdcContainerUtils.buildContainerItemDetails(
              receivedContainer.getLabelTrackingId(),
              deliveryDocument,
              projectedQty,
              containerItem,
              receivedContainer.getStoreAlignment(),
              receivedContainer.getDistributions(),
              receivedContainer.getDestType());

      LOGGER.info(
          "Created container item for delivery {} having tracking id : {} ",
          instructionDownloadMessageDTO.getDeliveryNumber(),
          receivedContainer.getLabelTrackingId());

      container =
          rdcContainerUtils.buildContainer(
              ReceivingConstants.OFFLINE_DEFAULT_DOOR_LOCATION,
              null, // Equivalent of instruction.getId()
              offlineRcvInfoMap
                  .get(
                      receivedContainer.getParentTrackingId() != null
                          ? receivedContainer.getParentTrackingId()
                          : receivedContainer.getLabelTrackingId())
                  .getDeliveryNbr(),
              offlineRcvInfoMap
                  .get(
                      receivedContainer.getParentTrackingId() != null
                          ? receivedContainer.getParentTrackingId()
                          : receivedContainer.getLabelTrackingId())
                  .getInstructionMsg(),
              deliveryDocument,
              ReceivingConstants.DEFAULT_USER,
              receivedContainer,
              container,
              null);

      LOGGER.info(
          "Created container for delivery {} having parent tracking id : {} , tracking id {} ",
          instructionDownloadMessageDTO.getDeliveryNumber(),
          receivedContainer.getParentTrackingId(),
          receivedContainer.getLabelTrackingId());

      containers.add(container);
      containerItems.add(containerItem);
    }

    LOGGER.info(
        "Created Container and Container Items for Delivery Nbr: {}",
        instructionDownloadMessageDTO.getDeliveryNumber());

    // Building receipts
    List<Receipt> receipts =
        buildReceiptForOfflineRcv(deliveryDocumentMap, instructionDownloadMessageDTO);

    // Persist the container, container items & receipt details
    rdcReceivingUtils.persistReceivedContainerDetails(
        Collections.emptyList(), containers, containerItems, receipts, Collections.emptyList());

    /**
     * prepare consolidated containers using containers, containerItems & receivedContainers instead
     * of fetching using repository call
     */
    List<Container> consolidatedContainerList = new ArrayList<>();
    if (Boolean.TRUE.equals(rdcManagedConfig.getEnableSingleTransactionForOffline())
        && (rdcManagedConfig
                .getDcListEligibleForPrepareConsolidatedContainer()
                .stream()
                .filter(config -> org.apache.commons.lang3.StringUtils.isNotEmpty(config))
                .collect(Collectors.toList())
                .isEmpty()
            || rdcManagedConfig
                .getDcListEligibleForPrepareConsolidatedContainer()
                .contains(String.valueOf(labelDataList.get(0).getSourceFacilityNumber())))) {
      consolidatedContainerList =
          prepareConsolidatedContainers(containers, containerItems, receivedContainers);
    }
    LOGGER.info(
        "Persisted Container, Container Items, Receipts for Delivery Nbr: {}",
        instructionDownloadMessageDTO.getDeliveryNumber());

    // Prepare and publish the payload
    postReceivingUpdatesForOfflineRcv(
        instructionDownloadMessageDTO,
        true,
        receivedContainers,
        deliveryDocumentMap,
        consolidatedContainerList);

    TenantContext.get().setOfflineReceivingProcessingEnd(System.currentTimeMillis());

    LOGGER.info(
        "Successfully completed for persist and publish offline receiving flow for Delivery Nbr: {} and overall time taken is : {}",
        instructionDownloadMessageDTO.getDeliveryNumber(),
        getTimeDifferenceInMillis(
            TenantContext.get().getOfflineReceivingProcessingStart(),
            TenantContext.get().getOfflineReceivingProcessingEnd()));
  }

  /**
   * Prepares consolidated containers for Offline with received containers, containers and
   * containerItems.
   *
   * @param containers
   * @param containerItems
   * @param receivedContainers
   */
  private List<Container> prepareConsolidatedContainers(
      List<Container> containers,
      List<ContainerItem> containerItems,
      List<ReceivedContainer> receivedContainers) {
    Map<String, Container> containerMap =
        containers
            .stream()
            .filter(container -> container.getParentTrackingId() == null)
            .collect(
                Collectors.toMap(
                    Container::getTrackingId, Function.identity(), (o, n) -> o, HashMap::new));

    Map<String, List<ContainerItem>> containerItemMap =
        containerItems.stream().collect(Collectors.groupingBy(ContainerItem::getTrackingId));

    Map<String, List<Container>> parentToChildContainerMap =
        containers
            .stream()
            .filter(container -> container.getParentTrackingId() != null)
            .collect(Collectors.groupingBy(Container::getParentTrackingId));

    Map<String, List<Container>> childContainerMap =
        containers
            .stream()
            .filter(container -> container.getParentTrackingId() != null)
            .collect(Collectors.groupingBy(Container::getTrackingId));

    Map<String, List<ContainerItem>> childContainerItemMap =
        containerItems
            .stream()
            .filter(item -> childContainerMap.containsKey(item.getTrackingId()))
            .collect(Collectors.groupingBy(ContainerItem::getTrackingId));

    List<Container> consolidatedContainers = new ArrayList<>();

    for (ReceivedContainer receivedContainer : receivedContainers) {
      if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
        String trackingId = receivedContainer.getLabelTrackingId();
        Container container = containerMap.get(trackingId);
        container.setAsnNumber(container.getShipmentId());
        container.setDocumentType(DocumentType.ASN.getDocType());
        container.setContainerItems(containerItemMap.get(trackingId));

        List<Container> childContainerList = parentToChildContainerMap.get(trackingId);
        Set<Container> childContainerToBeAdded = new HashSet<>();

        if (CollectionUtils.isNotEmpty(childContainerList)) {
          childContainerList
              .stream()
              .forEach(
                  child -> {
                    child.setContainerItems(childContainerItemMap.get(child.getTrackingId()));
                    child.setAsnNumber(child.getShipmentId());
                    child.setDocumentType(DocumentType.ASN.getDocType());
                    childContainerToBeAdded.add(child);
                  });
        }
        container.setChildContainers(childContainerToBeAdded);
        container.setHasChildContainers(!CollectionUtils.isEmpty(childContainerToBeAdded));

        Container parentContainer = setParentContainerIfPalletExists(receivedContainer, container);
        LOG.info(
            "Prepared consolidated container for tracking ID: {} and Delivery Number: {}",
            trackingId,
            container.getDeliveryNumber());
        if (Objects.nonNull(parentContainer.getChildContainers())) {
          consolidatedContainers.add(parentContainer);
        } else {
          consolidatedContainers.add(container);
        }
      }
    }
    return consolidatedContainers;
  }

  /**
   * Added one more layer of pallet in consolidatedContainers parentContainer = pallet
   *
   * @param receivedContainer
   * @param container
   * @return
   */
  private static Container setParentContainerIfPalletExists(
      ReceivedContainer receivedContainer, Container container) {
    Container parentContainer = new Container();
    if (Objects.nonNull(receivedContainer.getPalletId())) {
      Set<Container> childCaseContainers = new HashSet<>();
      childCaseContainers.add(container);
      parentContainer.setChildContainers(childCaseContainers);
      parentContainer.setTrackingId(
          receivedContainer.getPalletId()); // setting parent tracking id as the pallet id
      parentContainer.setHasChildContainers(!CollectionUtils.isEmpty(childCaseContainers));
      parentContainer.setAsnNumber(container.getShipmentId());
      parentContainer.setDocumentType(DocumentType.ASN.getDocType());
      parentContainer.setDeliveryNumber(container.getDeliveryNumber());
      parentContainer.setMessageId(container.getMessageId());
      parentContainer.setContainerType(ReceivingConstants.PALLET);
      parentContainer.setInventoryStatus(container.getInventoryStatus());
      parentContainer.setContainerItems(container.getContainerItems());
      parentContainer.setContainerMiscInfo(container.getContainerMiscInfo());
      parentContainer.setDestination(container.getDestination());
      LOGGER.info(
          "[xdk] Pallet id : {} created for delivery : {}",
          parentContainer.getTrackingId(),
          parentContainer.getDeliveryNumber());
    }
    return parentContainer;
  }

  /**
   * If the parentTracking is empty then we need to fetch it from child container to buid container
   * item with proper alloc qty
   *
   * @param offlineRcvInfo
   * @param receivedContainer
   * @return
   */
  private static Integer getProjectedQtyBasedOnChild(
      Map<String, InstructionDownloadBlobDataDTO> offlineRcvInfo,
      ReceivedContainer receivedContainer) {
    Integer projectedQty = 0;
    if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
      projectedQty = offlineRcvInfo.get(receivedContainer.getLabelTrackingId()).getProjectedQty();
    } else {
      OptionalInt childDistributionQty =
          offlineRcvInfo
              .get(receivedContainer.getParentTrackingId())
              .getChildContainers()
              .stream()
              .filter(child -> receivedContainer.getLabelTrackingId().equals(child.getTrackingId()))
              .mapToInt(child -> child.getDistributions().get(0).getItem().getWhpk())
              .findFirst();
      if (childDistributionQty.isPresent()) {
        projectedQty = childDistributionQty.getAsInt();
      }
    }
    return projectedQty;
  }

  /**
   * Fetch delivery documents and line with delivery & container number
   *
   * @param instructionDownloadMessageDTO
   * @return
   * @throws ReceivingException
   */
  private List<DeliveryDocument> fetchDeliveryDocumentForOfflineRcv(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    Map<String, InstructionDownloadBlobDataDTO> offlineRcvInfoMap =
        instructionDownloadMessageDTO.getMiscOfflineRcvInfoMap();

    /**
     * Passing eventType to fetchDeliveryDocumentForOfflineRcv, to set it in DeliveryDocument for
     * further use
     */
    List<String> eventTypeList =
        instructionDownloadMessageDTO.getHttpHeaders().get(ReceivingConstants.EVENT_TYPE);
    String eventTypeVal =
        CollectionUtils.isNotEmpty(eventTypeList)
            ? eventTypeList.get(0)
            : ReceivingConstants.EMPTY_STRING;

    EventType eventType = EventType.valueOfEventType(eventTypeVal);

    List<DeliveryDocument> deliveryDocumentList =
        populateDeliveryDocumentForOfflineRcv(offlineRcvInfoMap, eventType);

    return deliveryDocumentList;
  }

  /**
   * populating deliveryDocument and deliveryDocumentLine from OP
   *
   * @param offlineRcvInfoMap
   */
  private List<DeliveryDocument> populateDeliveryDocumentForOfflineRcv(
      Map<String, InstructionDownloadBlobDataDTO> offlineRcvInfoMap, EventType eventType) {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    offlineRcvInfoMap.forEach(
        (containerTrackingId, offlineRcvInfo) -> {
          DeliveryDocument deliveryDocument = new DeliveryDocument();
          deliveryDocument.setEventType(eventType);
          deliveryDocument.setPalletId(offlineRcvInfo.getPalletId());
          deliveryDocument.setChannelMethod(offlineRcvInfo.getContainer().getChannelMethod());
          deliveryDocument.setPurchaseReferenceNumber(offlineRcvInfo.getPoNbr());
          deliveryDocument.setDeliveryNumber(offlineRcvInfo.getDeliveryNbr());
          deliveryDocument.setPoDCNumber(offlineRcvInfo.getSourceFacilityNumber());
          deliveryDocument.setPurchaseCompanyId(ReceivingConstants.PURCHASE_COMPANY_ID);
          deliveryDocument.setPoTypeCode(offlineRcvInfo.getPoTypeCode());
          deliveryDocument.setDeliveryStatus(DeliveryStatus.WRK);
          deliveryDocument.setPurchaseReferenceStatus(POStatus.ACTV.name());
          deliveryDocument.setCtrType(offlineRcvInfo.getContainer().getCtrType());
          deliveryDocument.setAsnNumber(offlineRcvInfo.getAsnNumber());
          deliveryDocument.setTrackingId(containerTrackingId);
          deliveryDocument.setProjectedQty(offlineRcvInfo.getProjectedQty());
          if (offlineRcvInfo.getSourceFacilityNumber() != null) {
            deliveryDocument.setOriginFacilityNum(
                Integer.valueOf(offlineRcvInfo.getSourceFacilityNumber())); // source Nbr from AOS
          }
          List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();

          DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
          ItemData additionalInfo = new ItemData();

          if (CollectionUtils.isEmpty(offlineRcvInfo.getChildContainers())) {
            deliveryDocumentLine.setTrackingId(offlineRcvInfo.getContainer().getTrackingId());
            deliveryDocumentLine.setPurchaseReferenceNumber(offlineRcvInfo.getPoNbr());
            deliveryDocumentLine.setPurchaseReferenceLineNumber(offlineRcvInfo.getPoLineNbr());
            deliveryDocumentLine.setItemUpc(
                offlineRcvInfo.getContainer().getDistributions().get(0).getItem().getItemUpc());
            deliveryDocument.setLabelType(offlineRcvInfo.getContainer().getLabelType());
            deliveryDocument.setBaseDivisionCode(
                offlineRcvInfo
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getBaseDivisionCode());
            deliveryDocument.setMessageNumber(
                offlineRcvInfo
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getMessageNumber());
            deliveryDocument.setFinancialReportingGroup(
                offlineRcvInfo
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getFinancialReportingGroup());
            deliveryDocument.setDeptNumber(
                offlineRcvInfo
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getDepartmentNumber());
            deliveryDocumentLine.setItemNbr(
                offlineRcvInfo.getContainer().getDistributions().get(0).getItem().getItemNbr());
            deliveryDocumentLine.setVendorPack(
                offlineRcvInfo.getContainer().getDistributions().get(0).getItem().getVnpk());
            deliveryDocumentLine.setWarehousePack(
                offlineRcvInfo.getContainer().getDistributions().get(0).getItem().getWhpk());
            deliveryDocumentLine.setDeptNumber(
                offlineRcvInfo
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getDepartmentNumber());
            deliveryDocumentLine.setPurchaseRefType(
                offlineRcvInfo.getContainer().getChannelMethod());
            // Added as part of sorter contract version 2
            setPoEventForDeliveryDocumentLine(offlineRcvInfo, deliveryDocumentLine);
            additionalInfo.setAtlasConvertedItem(true);
            additionalInfo.setPackTypeCode(
                offlineRcvInfo.getContainer().getDistributions().get(0).getItem().getPackType());
            additionalInfo.setItemHandlingMethod(
                offlineRcvInfo
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getItemHandlingCode());
            deliveryDocumentLine.setTotalOrderQty(offlineRcvInfo.getProjectedQty());
            deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.ACTIVE.name());
            deliveryDocumentLine.setAdditionalInfo(additionalInfo);
            deliveryDocumentLineList.add(deliveryDocumentLine);
          } else {
            // make multiple document lines in case of repack scenario

            if (rdcManagedConfig.getWpmSites().contains(offlineRcvInfo.getSourceFacilityNumber())
                || rdcManagedConfig
                    .getRdc2rdcSites()
                    .contains(offlineRcvInfo.getSourceFacilityNumber())) {
              /**
               * If it is a WPM site, at delivery document (parent) set container level label type
               * Else, for CC or Imports label type needs to be fetched from child container level
               */
              deliveryDocument.setLabelType(offlineRcvInfo.getContainer().getLabelType());
            } else {
              deliveryDocument.setLabelType(
                  offlineRcvInfo.getChildContainers().get(0).getLabelType());
            }
            deliveryDocument.setBaseDivisionCode(
                offlineRcvInfo
                    .getChildContainers()
                    .get(0)
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getBaseDivisionCode());
            deliveryDocument.setFinancialReportingGroup(
                offlineRcvInfo
                    .getChildContainers()
                    .get(0)
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getFinancialReportingGroup());
            deliveryDocument.setDeptNumber(
                offlineRcvInfo
                    .getChildContainers()
                    .get(0)
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getDepartmentNumber());

            offlineRcvInfo
                .getChildContainers()
                .forEach(
                    childContainer -> {
                      DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
                      ItemData additionalInfoBP = new ItemData();

                      documentLine.setPurchaseReferenceNumber(offlineRcvInfo.getPoNbr());
                      documentLine.setPurchaseReferenceLineNumber(offlineRcvInfo.getPoLineNbr());
                      documentLine.setItemNbr(
                          childContainer.getDistributions().get(0).getItem().getItemNbr());
                      // Added as part of sorter contract version 2
                      setPoEventForDeliveryDocumentLine(offlineRcvInfo, documentLine);
                      documentLine.setVendorPack(
                          childContainer.getDistributions().get(0).getItem().getVnpk());
                      documentLine.setWarehousePack(
                          childContainer.getDistributions().get(0).getItem().getWhpk());
                      documentLine.setDeptNumber(
                          childContainer.getDistributions().get(0).getItem().getDepartmentNumber());
                      documentLine.setChildTrackingId(childContainer.getTrackingId());
                      documentLine.setItemUpc(
                          childContainer.getDistributions().get(0).getItem().getItemUpc());
                      documentLine.setPurchaseRefType(childContainer.getChannelMethod());
                      documentLine.setTotalOrderQty(offlineRcvInfo.getProjectedQty());
                      documentLine.setPurchaseReferenceLineStatus(POLineStatus.ACTIVE.name());
                      documentLine.setMessageNumber(
                          childContainer.getDistributions().get(0).getItem().getMessageNumber());
                      additionalInfoBP.setAtlasConvertedItem(true);
                      additionalInfoBP.setPackTypeCode(
                          childContainer.getDistributions().get(0).getItem().getPackType());
                      additionalInfoBP.setItemHandlingMethod(
                          childContainer.getDistributions().get(0).getItem().getItemHandlingCode());
                      documentLine.setAdditionalInfo(additionalInfoBP);
                      deliveryDocumentLineList.add(documentLine);
                    });
          }
          deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
          LOGGER.info(
              "Populated delivery document and document lines for delivery number : {}",
              offlineRcvInfo.getDeliveryNbr());
          deliveryDocumentList.add(deliveryDocument);
        });
    return deliveryDocumentList;
  }

  private void setPoEventForDeliveryDocumentLine(
      InstructionDownloadBlobDataDTO offlineRcvInfo, DeliveryDocumentLine deliveryDocumentLine) {
    if (ReceivingConstants.ONE
        < tenantSpecificConfigReader.getSorterContractVersion(TenantContext.getFacilityNum())) {
      deliveryDocumentLine.setEvent(offlineRcvInfo.getContainer().getPoEvent());
    }
  }

  /**
   * Building receipts for Offline Receiving Flow
   *
   * @param deliveryDocumentMap
   * @param instructionDownloadMessageDTO
   * @return
   */
  private List<Receipt> buildReceiptForOfflineRcv(
      Map<String, DeliveryDocument> deliveryDocumentMap,
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    List<Receipt> receipts = new ArrayList<>();
    deliveryDocumentMap
        .values()
        .stream()
        .forEach(
            deliveryDocument -> {
              receipts.addAll(
                  receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
                      deliveryDocument,
                      ReceivingConstants.OFFLINE_DEFAULT_DOOR_LOCATION,
                      null,
                      ReceivingConstants.DEFAULT_USER,
                      deliveryDocument.getProjectedQty()));
            });

    return receipts;
  }

  /**
   * This method publishes receipts/purchases to Inventory/DcFin & WFT for offline receiving
   *
   * @param instructionDownloadMessageDTO
   * @param isAtlasConvertedItem
   * @param receivedContainers
   */
  public void postReceivingUpdatesForOfflineRcv(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      boolean isAtlasConvertedItem,
      List<ReceivedContainer> receivedContainers,
      Map<String, DeliveryDocument> deliveryDocumentMap,
      List<Container> consolidatedContainerList)
      throws ReceivingException {

    // Prepare container details to be passed in for instruction
    List<Content> contents = new ArrayList<>();

    Map<String, InstructionDownloadBlobDataDTO> map =
        instructionDownloadMessageDTO.getMiscOfflineRcvInfoMap();

    map.forEach(
        (tracking, instructionDownloadBlobDataDTO) -> {
          if (org.springframework.util.CollectionUtils.isEmpty(
              instructionDownloadBlobDataDTO.getChildContainers())) {
            Content content = new Content();
            content.setItemNbr(
                instructionDownloadBlobDataDTO
                    .getContainer()
                    .getDistributions()
                    .get(0)
                    .getItem()
                    .getItemNbr());
            contents.add(content);
          } else {
            instructionDownloadBlobDataDTO
                .getChildContainers()
                .stream()
                .distinct()
                .filter(
                    distinctByKey(child -> child.getDistributions().get(0).getItem().getItemNbr()))
                .collect(Collectors.toList())
                .forEach(
                    childContainerDTO -> {
                      Content childContent = new Content();
                      childContent.setItemNbr(
                          childContainerDTO.getDistributions().get(0).getItem().getItemNbr());
                      contents.add(childContent);
                    });
          }
        });
    ContainerDetails containerDetail = new ContainerDetails();
    containerDetail.setContents(contents);
    List<ContainerDetails> containerDetails = new ArrayList<>();
    containerDetails.add(containerDetail);
    // Prepare instruction to be passed in existing methods
    Instruction instruction = new Instruction();
    instruction.setChildContainers(containerDetails);
    String trackingId =
        receivedContainers
            .stream()
            .filter(receivedContainer -> receivedContainer.getParentTrackingId() == null)
            .map(receivedContainer -> receivedContainer.getLabelTrackingId())
            .collect(Collectors.toList())
            .get(0);

    instruction.setReceivedQuantity(
        instructionDownloadMessageDTO.getMiscOfflineRcvInfoMap().get(trackingId).getProjectedQty());
    instruction.setReceivedQuantityUOM(
        instructionDownloadMessageDTO
            .getMiscOfflineRcvInfoMap()
            .get(trackingId)
            .getProjectedQtyUom());

    // Outbox flow
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED,
        false)) {
      TenantContext.get().setPersistOutboxEventsForDAStart(System.currentTimeMillis());
      Collection<OutboxEvent> outboxEvents =
          rdcOfflineRecieveUtils.buildOutboxEventsForOffline(
              receivedContainers,
              instructionDownloadMessageDTO.getHttpHeaders(),
              new Instruction(),
              deliveryDocumentMap,
              consolidatedContainerList);
      rdcReceivingUtils.persistOutboxEvents(outboxEvents);
      TenantContext.get().setPersistOutboxEventsForDAEnd(System.currentTimeMillis());
    } else {
      rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
          instruction,
          deliveryDocumentMap,
          instructionDownloadMessageDTO.getHttpHeaders(),
          isAtlasConvertedItem,
          receivedContainers,
          consolidatedContainerList);
    }
  }

  /**
   * Build received container for Offline flow - using deliveryDocumentMap
   *
   * @param labelDataList
   * @param deliveryDocumentMap
   * @param httpHeaders
   * @param isPalletPullByStore
   * @return
   * @throws ReceivingException
   */
  public List<ReceivedContainer> transformLabelDataForOfflineRcv(
      List<LabelData> labelDataList,
      Map<String, DeliveryDocument> deliveryDocumentMap,
      HttpHeaders httpHeaders,
      boolean isPalletPullByStore)
      throws ReceivingException {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    for (LabelData labelData : labelDataList) {

      DeliveryDocument deliveryDocument = deliveryDocumentMap.get(labelData.getTrackingId());
      // DeliveryDocumentLine deliveryDocumentLine =
      // deliveryDocument.getDeliveryDocumentLines().get(0);
      Map<String, DeliveryDocumentLine> deliveryDocumentLineMap = new HashMap<>();
      deliveryDocumentLineMap =
          deliveryDocument
              .getDeliveryDocumentLines()
              .stream()
              .collect(
                  Collectors.toMap(
                      deliveryDocumentLine ->
                          deliveryDocumentLine.getChildTrackingId() != null
                              ? deliveryDocumentLine.getChildTrackingId()
                              : deliveryDocumentLine.getTrackingId(),
                      Function.identity()));

      List<InstructionDownloadChildContainerDTO> childLabelDataContainers =
          labelData.getAllocation().getChildContainers();
      boolean isBreakPackPutContainer =
          !org.springframework.util.CollectionUtils.isEmpty(childLabelDataContainers);
      if (isBreakPackPutContainer) {
        // build break pack child containers for PUT
        String parentTrackingId = labelData.getTrackingId();
        List<ReceivedContainer> childReceivedContainers = new ArrayList<>();
        Map<String, DeliveryDocumentLine> finalDeliveryDocumentLineMap = deliveryDocumentLineMap;
        childLabelDataContainers.forEach(
            childContainer -> {
              DeliveryDocumentLine documentLine =
                  finalDeliveryDocumentLineMap.get(childContainer.getTrackingId());
              ReceivedContainer childReceivedContainer =
                  rdcDaService.buildReceivedContainer(
                      labelData,
                      deliveryDocument,
                      documentLine,
                      childContainer.getTrackingId(),
                      parentTrackingId,
                      childContainer.getDistributions(),
                      childContainer.getCtrDestination(),
                      false,
                      isPalletPullByStore,
                      false);
              childReceivedContainers.add(childReceivedContainer);
            });

        Integer receivedQtyForParentContainerInEaches =
            childReceivedContainers
                .stream()
                .map(ReceivedContainer::getPack)
                .reduce(0, Integer::sum);
        receivedContainers.addAll(childReceivedContainers);

        // build parent container for PUT
        ReceivedContainer parentReceivedContainer =
            rdcDaService.buildParentContainerForPutLabels(
                parentTrackingId,
                deliveryDocument,
                receivedQtyForParentContainerInEaches,
                childReceivedContainers);
        if (Objects.nonNull(parentReceivedContainer)) {
          receivedContainers.add(parentReceivedContainer);
        }
        LOGGER.info(
            "Prepared received container for break pack for delivery Nbr : {} and for container : {} ",
            receivedContainers.get(0).getDeliveryNumber(),
            labelData.getTrackingId());
      } else {
        DeliveryDocumentLine documentLine =
            deliveryDocumentMap.get(labelData.getTrackingId()).getDeliveryDocumentLines().get(0);
        // build case pack shipping/routing containers
        ReceivedContainer receivedContainer =
            rdcDaService.buildReceivedContainer(
                labelData,
                deliveryDocument,
                documentLine,
                labelData.getTrackingId(),
                null,
                labelData.getAllocation().getContainer().getDistributions(),
                labelData.getAllocation().getContainer().getFinalDestination(),
                false,
                isPalletPullByStore,
                false);
        receivedContainers.add(receivedContainer);
        LOGGER.info(
            "Prepared received container for case pack for delivery Nbr : {} and for container : {}",
            receivedContainers.get(0).getDeliveryNumber(),
            labelData.getTrackingId());
      }
    }

    return receivedContainers;
  }
}
