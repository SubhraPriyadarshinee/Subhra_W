package com.walmart.move.nim.receiving.mfc.processor.v2;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.replaceContainerWithSSCC;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.RESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.UNRESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemType.OVERAGE;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.createASNDeepClone;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.isStorePalletPublishingDisabled;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DELIM_DASH;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.mfc.common.*;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.processor.ProblemHandingProcessor;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import com.walmart.move.nim.receiving.mfc.service.MFCReceiptService;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Processor for bulk receiving on manual finalisation */
public class StoreBulkReceivingProcessor implements ProcessExecutor, ProblemHandingProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreBulkReceivingProcessor.class);

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Autowired private MFCDeliveryService mfcDeliveryService;

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private MFCProblemService mfcProblemService;

  @Autowired private MFCReceiptService mfcReceiptService;

  @Autowired private ContainerTransformer containerTransformer;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @ManagedConfiguration private AppConfig appConfig;

  @Override
  public boolean isAsync() {
    return mfcManagedConfig.isAsyncBulkReceivingEnabled();
  }

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    LOGGER.info("StoreManualFinaliseProcessor : Entry ... parameter = {} ", receivingEvent);

    ASNDocument asnDocument =
        JacksonParser.convertJsonToObject(receivingEvent.getPayload(), ASNDocument.class);

    Long deliveryNumber = asnDocument.getDelivery().getDeliveryNumber();

    Map<String, Object> additionAttribute = receivingEvent.getAdditionalAttributes();

    List<Container> receivedContainers =
        mfcContainerService.findContainerByDeliveryNumber(deliveryNumber);

    Map<String, PalletInfo> palletInfoMap = MFCUtils.getPalletInfoMap(asnDocument.getPacks());

    boolean isStorePalletIncluded =
        Objects.nonNull(additionAttribute.get(MFCConstant.STORE_PALLET_INCLUDED))
                && Boolean.valueOf(
                    additionAttribute.get(MFCConstant.STORE_PALLET_INCLUDED).toString())
            ? Boolean.TRUE
            : Boolean.FALSE;
    boolean isMFCPalletIncluded =
        Objects.nonNull(additionAttribute.get(MFCConstant.MFC_PALLET_INCLUDED))
                && Boolean.valueOf(
                    additionAttribute.get(MFCConstant.MFC_PALLET_INCLUDED).toString())
            ? Boolean.TRUE
            : Boolean.FALSE;

    Set<String> receivedContainerTrackingIds =
        receivedContainers
            .stream()
            .map(container -> container.getSsccNumber())
            .collect(Collectors.toSet());

    DeliveryMetaData deliveryMetaData =
        mfcDeliveryMetadataService
            .findByDeliveryNumber(deliveryNumber.toString())
            .orElse(
                DeliveryMetaData.builder()
                    .deliveryNumber(deliveryNumber.toString())
                    .trailerNumber(asnDocument.getDelivery().getTrailerId())
                    .deliveryStatus(
                        DeliveryStatus.valueOf(
                            asnDocument.getDelivery().getStatusInformation().getStatus()))
                    .build());
    mfcContainerService.publishWorkingIfApplicable(deliveryMetaData);

    String storePalletStatus =
        isStorePalletIncluded
            ? performReceiving(
                deliveryNumber,
                asnDocument,
                PalletType.STORE.name(),
                receivedContainerTrackingIds,
                palletInfoMap)
            : performException(asnDocument, palletInfoMap, PalletType.STORE.name());
    String mfcPalletStatus =
        isMFCPalletIncluded
            ? performReceiving(
                deliveryNumber,
                asnDocument,
                PalletType.MFC.name(),
                receivedContainerTrackingIds,
                palletInfoMap)
            : performException(asnDocument, palletInfoMap, PalletType.MFC.name());

    LOGGER.info(
        "Bulk container operation is completed. mfc_pallet_status={} and store_pallet_status={}",
        mfcPalletStatus,
        storePalletStatus);
  }

  private String performReceiving(
      Long deliveryNumber,
      ASNDocument asnDocument,
      String palletType,
      Set<String> receivedContainerTrackingIds,
      Map<String, PalletInfo> palletInfoMap) {

    Map<String, String> filteredPalletTypeMap =
        palletInfoMap
            .entrySet()
            .stream()
            .filter(entry -> Objects.nonNull(entry.getValue()))
            .filter(entry -> palletType.equalsIgnoreCase(entry.getValue().getPalletType()))
            .filter(
                entry ->
                    !receivedContainerTrackingIds.contains(
                        entry.getKey())) // Filter Received container
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey(), entry -> entry.getValue().getPalletType()));

    // createTransientContainer
    List<Container> containers =
        createContainers(asnDocument, filteredPalletTypeMap, palletInfoMap);

    // check Overage-UNRESOLVED -> ProblemUpdation
    // skip above containers
    containers = handleOverages(containers, deliveryNumber);

    // save container
    mfcContainerService.getContainerService().saveAll(containers);

    // save receipt
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)) {
      createReceiptForStorePallet(containers);
    }

    // get the latest container and publish container
    publishContainers(containers);

    LOGGER.info("Receiving Process successfully for palletType={}", palletType);

    return "success";
  }

  private String performException(
      ASNDocument asnDocument, Map<String, PalletInfo> palletInfoMap, String palletType) {

    ASNDocument _customizedASN = createASNDeepClone(asnDocument);
    List<Pack> packs =
        _customizedASN
            .getPacks()
            .stream()
            .filter(
                pack ->
                    palletType.equalsIgnoreCase(
                        MFCUtils.getPalletTypeFromPalletInfoMap(
                            palletInfoMap, pack.getPalletNumber())))
            .collect(Collectors.toList());
    _customizedASN.setPacks(packs);

    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());

    // Action for overage / shortage handling .
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(_customizedASN))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(forwardableHeaders)
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    LOGGER.info(
        "Going to initiate the process for unloading processing for delivery Number {}",
        asnDocument.getDelivery().getDeliveryNumber());
    processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
    LOGGER.info("Completed the process for unloading processing");

    return "success";
  }

  private ASNDocument getASNDocument(Long deliveryNumber) {
    return mfcDeliveryService.getShipmentDataFromGDM(deliveryNumber, null);
  }

  // TODO: Add failure handling & retry
  private List<Container> createContainers(
      ASNDocument asnDocument,
      Map<String, String> filteredPalletTypeMap,
      Map<String, PalletInfo> palletInfoMap) {
    Map<String, Container> containerMap = new HashMap<>();
    Map<Long, ItemDetails> itemMap = CoreUtil.getItemMap(asnDocument);
    for (Pack pack : asnDocument.getPacks()) {
      // ignore the pallet/case which is not there in the map
      if (!filteredPalletTypeMap.containsKey(MFCUtils.getPackId(pack))) {
        LOGGER.warn("Not able to create container as pallet/case is of different type");
        continue;
      }

      // Process container creation
      Container container;
      if (containerMap.containsKey(MFCUtils.getPackId(pack))) {
        container = containerMap.get(pack.getPalletNumber());
        LOGGER.info(
            "Adding items to pallet: {} from pack: {}",
            container.getTrackingId(),
            pack.getPackNumber());
      } else {
        container = mfcContainerService.createContainer(asnDocument, pack, palletInfoMap);
        LOGGER.info(
            "Creating container: {} from pack: {}",
            container.getTrackingId(),
            pack.getPackNumber());
        mfcContainerService.populateContainerMiscInfo(asnDocument, container, palletInfoMap);
        container.setContainerItems(new ArrayList<>());
        containerMap.put(MFCUtils.getPackId(pack), container);
      }
      List<ContainerItem> containerItems = new ArrayList<>();
      for (Item item : pack.getItems()) {
        ItemDetails itemDetails = itemMap.get(item.getItemNumber());
        ContainerItem containerItem =
            mfcContainerService.createPackItem(pack, item, itemDetails, null);
        if (Objects.nonNull(containerItem)) {
          containerItem.setTrackingId(container.getTrackingId());
          if (MFCConstant.SHIPPER.equals(item.getAssortmentType())
              && CollectionUtils.isNotEmpty(item.getChildItems())) {
            containerItems.addAll(
                mfcContainerService.createChildItems(containerItem, item.getChildItems()));
          } else {
            containerItems.add(containerItem);
          }
        }
      }
      container.getContainerItems().addAll(containerItems);
    }
    return new ArrayList<>(containerMap.values());
  }

  /**
   * Resolve past overages which may be present for the delivery. Ignore these IDs for container
   * creation
   *
   * @param containers
   * @param deliveryNumber
   * @return
   */
  private List<Container> handleOverages(List<Container> containers, Long deliveryNumber) {
    Set<String> palletNumbers =
        containers.stream().map(Container::getSsccNumber).collect(Collectors.toSet());
    Collection<List<String>> partitionedPalletNumbers =
        ReceivingUtils.batchifyCollection(palletNumbers, appConfig.getInSqlBatchSize());
    Set<ProblemLabel> problemLabels = new HashSet<>();
    partitionedPalletNumbers.forEach(
        palletNumber ->
            problemLabels.addAll(
                mfcProblemService.getProblemLabels(
                    OVERAGE + DELIM_DASH + UNRESOLVED,
                    new HashSet<>(palletNumber),
                    deliveryNumber)));

    problemLabels.forEach(problemLabel -> handleProblemUpdation(problemLabel, OVERAGE, RESOLVED));
    Set<String> problemTagIds =
        problemLabels.stream().map(ProblemLabel::getProblemTagId).collect(Collectors.toSet());
    return containers
        .stream()
        .filter(container -> !problemTagIds.contains(container.getSsccNumber()))
        .collect(Collectors.toList());
  }

  // TODO: Batchify
  private void createReceiptForStorePallet(List<Container> containers) {
    List<Receipt> receipts = new ArrayList<>();
    containers.forEach(
        container -> {
          if (MFCUtils.isStorePallet(container)) {
            receipts.addAll(
                container
                    .getContainerItems()
                    .stream()
                    .map(
                        containerItem ->
                            MFCUtils.createContainerReceipt(
                                container,
                                containerItem,
                                Quantity.builder()
                                    .type(QuantityType.RECEIVED)
                                    .uom(containerItem.getQuantityUOM())
                                    .value((long) containerItem.getQuantity())
                                    .build(),
                                ReceivingUtils.conversionToEaches(
                                    containerItem.getQuantity(),
                                    containerItem.getQuantityUOM(),
                                    containerItem.getVnpkQty(),
                                    containerItem.getWhpkQty()),
                                containerItem.getQuantityUOM()))
                    .collect(Collectors.toList()));
          }
        });
    LOGGER.info("Going to save receipts for all containers: {}", receipts);
    mfcReceiptService.saveReceipt(receipts);
  }

  private void publishContainers(List<Container> containers) {
    List<ContainerDTO> containerDTOs = containerTransformer.transformList(containers);

    containerDTOs.removeIf(
        containerDTO -> {
          if (isStorePalletPublishingDisabled(containerDTO, tenantSpecificConfigReader)) {
            return true;
          }
          replaceContainerWithSSCC(containerDTO);
          containerDTO.setEventType("PALLET_RECEIVED");
          return false;
        });

    mfcContainerService.getContainerService().publishMultipleContainersToInventory(containerDTOs);
  }

  @Override
  public void handleProblemCreation(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {}

  @Override
  public void handleProblemUpdation(
      ProblemLabel problemLabel,
      ProblemType problemType,
      ProblemResolutionType problemResolutionType) {
    if (mfcManagedConfig.isProblemRegistrationEnabled()) {
      ProblemRegistrationService problemRegistrationService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              PROBLEM_REGISTRATION_SERVICE,
              FIXIT_PROBLEM_SERVICE,
              ProblemRegistrationService.class);
      try {
        problemRegistrationService.closeProblem(problemLabel, problemType);
      } catch (Exception e) {
        LOGGER.error(
            "Fixit ticket update failed for facility:{}, container: {}",
            problemLabel.getFacilityNum(),
            problemLabel.getProblemTagId(),
            e);
      }
    }
    mfcProblemService.updateProblem(problemLabel, problemType, problemResolutionType);
  }
}
