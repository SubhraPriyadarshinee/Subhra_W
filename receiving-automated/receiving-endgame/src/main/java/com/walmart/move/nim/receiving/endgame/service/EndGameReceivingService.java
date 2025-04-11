package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.createContainerTag;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.removePurchaseOrdersWithOldMustArriveByDate;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.retrieveVendorInfos;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.sortDeliveryDocumentByMustArriveByDate;
import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.PALLET_DIVERTS_TO_RECEIVE;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.AUDIT_CHECK_REQUIRED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.CASE_WEIGHT_CHECK_FAILED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.MULTIPLE_SELLER_ASSOCIATED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.PALLET_DIVERTED_TO_RECEIVE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.POLINE_EXHAUSTED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.WFS_CHECK_FAILED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static java.lang.Math.max;
import static java.util.Collections.singletonList;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.util.ObjectUtils.isEmpty;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.model.move.MoveInfo;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.common.LabelUtils;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.model.ContainerDetail;
import com.walmart.move.nim.receiving.endgame.model.ContainerItemsDetail;
import com.walmart.move.nim.receiving.endgame.model.Datum;
import com.walmart.move.nim.receiving.endgame.model.DeliveryMetaDataRequest;
import com.walmart.move.nim.receiving.endgame.model.EndgameReceivingRequest;
import com.walmart.move.nim.receiving.endgame.model.ExtraAttributes;
import com.walmart.move.nim.receiving.endgame.model.LabelResponse;
import com.walmart.move.nim.receiving.endgame.model.PalletSlotRequest;
import com.walmart.move.nim.receiving.endgame.model.PalletSlotResponse;
import com.walmart.move.nim.receiving.endgame.model.PrintRequest;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.endgame.model.SlotLocation;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EndgameOutboxServiceName;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class EndGameReceivingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameReceivingService.class);

  @ManagedConfiguration protected EndgameManagedConfig endgameManagedConfig;

  @ManagedConfiguration protected AppConfig appConfig;

  @Autowired protected Gson gson;

  @Autowired protected ReceiptService receiptService;

  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired protected EndgameContainerService endgameContainerService;

  @Autowired protected DCFinServiceV2 dCFinServiceV2;

  @Autowired protected EndGameSlottingService endGameSlottingService;

  @Autowired protected ContainerPersisterService containerPersisterService;

  @Autowired protected MovePublisher movePublisher;

  @Autowired protected EndGameLabelingService endGameLabelingService;

  @Autowired protected ContainerService containerService;

  @Autowired private EndgameOutboxHandler endgameOutboxHandler;

  @Resource(name = "containerTransformer")
  protected Transformer<Container, ContainerDTO> transformer;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  protected EndGameDeliveryService endGameDeliveryService;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  protected DeliveryMetaDataService deliveryMetaDataService;

  @Autowired protected AuditHelper auditHelper;

  @Autowired protected InventoryService inventoryService;

  @Autowired protected EndGameReceivingHelperService endGameReceivingHelperService;

  @Autowired EndGameOsdrProcessor endGameOsdrProcessor;

  /**
   * Method to receive "1 VendorPack"
   *
   * @param scanEventData
   * @return receiveVendorPack
   */
  public ReceiveVendorPack receiveVendorPack(ScanEventData scanEventData) {
    ReceivingRequest receivingRequestScanEventData = (ReceivingRequest) scanEventData;
    List<PurchaseOrder> purchaseOrderList = getPurchaseOrders(scanEventData);
    if (EndGameUtils.isMultipleSellerIdInPurchaseOrders(purchaseOrderList)) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), MULTIPLE_SELLER_ASSOCIATED);
      LOGGER.debug(
          "Got multiple seller id associated for [upc={}] [deliveryNumber={}]",
          scanEventData.getCaseUPC(),
          scanEventData.getDeliveryNumber());
      return ReceiveVendorPack.builder().purchaseOrderList(purchaseOrderList).build();
    }
    if (checkWfsPurchaseOrder(purchaseOrderList, endgameManagedConfig)) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), WFS_CHECK_FAILED);
      return ReceiveVendorPack.builder().purchaseOrderList(purchaseOrderList).build();
    }
    LOGGER.info("ReceivingRequest's DeliveryNumber(): {}", scanEventData.getDeliveryNumber());
    Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine =
        getPurchaseOrderAndLineToReceive(
            purchaseOrderList, Optional.empty(), scanEventData.getDeliveryNumber());

    checkValidPoAndLine(selectedPoAndLine, scanEventData);

    Integer quantity =
        Objects.nonNull(receivingRequestScanEventData.getQuantity())
            ? receivingRequestScanEventData.getQuantity()
            : 1;

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            ReceivingConstants.ENABLE_ENTER_QUANTITY_1P)
        && DECANT_API.equalsIgnoreCase(scanEventData.getRequestOriginator())) {
      return getReceiveVendorPackWithPO(selectedPoAndLine);
    }
    if (isAuditRequired(selectedPoAndLine, scanEventData)) {
      if (!PALLET_DIVERTS_TO_RECEIVE.contains(scanEventData.getDiverted().getStatus())) {
        updatePreLabelDataReason(scanEventData.getPreLabelData(), AUDIT_CHECK_REQUIRED);
        return getReceiveVendorPackWithPO(selectedPoAndLine);
      } else {
        addContainerAuditTag(scanEventData);
      }
    }

    String quantityUOM =
        ObjectUtils.isEmpty(receivingRequestScanEventData.getQuantityUOM())
            ? selectedPoAndLine.getValue().getOrdered().getUom()
            : receivingRequestScanEventData.getQuantityUOM();
    return getReceiveVendorPack(scanEventData, selectedPoAndLine, quantity, quantityUOM);
  }

  private void addContainerAuditTag(ScanEventData scanEventData) {
    ContainerTag auditTag = createContainerTag(PENDING_AUDIT);
    List<ContainerTag> tags =
        CollectionUtils.isEmpty(scanEventData.getContainerTagList())
            ? new ArrayList<>()
            : scanEventData.getContainerTagList();
    tags.add(auditTag);
    scanEventData.setContainerTagList(tags);
  }

  protected static void checkValidPoAndLine(
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine,
      String caseUpc,
      long deliverNumber) {
    if (Objects.isNull(selectedPoAndLine)) {
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(EndgameConstants.PO_PO_LINE_EXHAUSTED_ERROR_MSG, caseUpc, deliverNumber));
    }
  }

  protected ReceiveVendorPack getReceiveVendorPack(
      ScanEventData scanEventData,
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine,
      Integer quantity,
      String quantityUOM) {
    int receivedQty =
        deliveryMetaDataService.getReceivedQtyFromMetadata(
            selectedPoAndLine.getValue().getItemDetails().getNumber(),
            scanEventData.getDeliveryNumber());
    int eachQuantity;
    if (receivedQty != 0) {
      eachQuantity = receivedQty;
    } else {
      eachQuantity =
          ReceivingUtils.conversionToEaches(
              quantity,
              quantityUOM,
              selectedPoAndLine.getValue().getVnpk().getQuantity(),
              selectedPoAndLine.getValue().getWhpk().getQuantity());
    }

    boolean isValidCaseWeight = isValidCaseWeight(selectedPoAndLine, scanEventData, eachQuantity);
    String reasonCode = (isValidCaseWeight) ? PALLET_DIVERTED_TO_RECEIVE : CASE_WEIGHT_CHECK_FAILED;
    if (!isValidCaseWeight
        && !PALLET_DIVERTS_TO_RECEIVE.contains(scanEventData.getDiverted().getStatus())) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), reasonCode);
      LOGGER.info(
          "Required audit check for [upc={}] [deliveryNumber={}] [TCL={}]",
          scanEventData.getCaseUPC(),
          scanEventData.getDeliveryNumber(),
          scanEventData.getTrailerCaseLabel());
      return getReceiveVendorPackWithPO(selectedPoAndLine);
    }
    return getReceiveVendorPack(scanEventData, selectedPoAndLine, eachQuantity);
  }

  protected ReceiveVendorPack getReceiveVendorPack(
      ScanEventData scanEventData,
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine,
      int eachQuantity) {
    endGameDeliveryService.publishWorkingEventIfApplicable(
        selectedPoAndLine, scanEventData.getDeliveryNumber());
    Container container =
        createAndPublishContainer(
            scanEventData, selectedPoAndLine.getKey(), selectedPoAndLine.getValue(), eachQuantity);
    if (!tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      postToDCFin(singletonList(container), selectedPoAndLine.getKey().getLegacyType(), null);
    }
    return ReceiveVendorPack.builder().container(container).build();
  }

  protected ReceiveVendorPack getReceiveVendorPackWithPO(
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine) {
    PurchaseOrder purchaseOrder = selectedPoAndLine.getKey();
    purchaseOrder.setLines(singletonList(selectedPoAndLine.getValue()));
    return ReceiveVendorPack.builder().purchaseOrderList(singletonList(purchaseOrder)).build();
  }

  private static boolean checkWfsPurchaseOrder(
      List<PurchaseOrder> purchaseOrderList, EndgameManagedConfig endgameManagedConfig) {
    return purchaseOrderList
            .stream()
            .filter(
                po ->
                    EndGameUtils.isWFSPurchaseOrder(
                        po,
                        endgameManagedConfig.getWalmartDefaultSellerId(),
                        endgameManagedConfig.getSamsDefaultSellerId()))
            .count()
        > 0;
  }

  /**
   * Method to receive a MultiSKU container. Item Quantity is required and variable. If a container
   * exists, the item is added to the container. If the container doesn't exist, a new container is
   * created.
   *
   * @param receivingRequest
   * @return receiveVendorPack
   */
  public ReceiveVendorPack receiveMultiSKUContainer(ReceivingRequest receivingRequest) {
    List<PurchaseOrder> purchaseOrderList = getPurchaseOrders(receivingRequest);
    if (EndGameUtils.isMultipleSellerIdInPurchaseOrders(purchaseOrderList)) {
      return ReceiveVendorPack.builder().purchaseOrderList(purchaseOrderList).build();
    }
    if (checkWfsPurchaseOrder(purchaseOrderList, endgameManagedConfig)) {
      return ReceiveVendorPack.builder().purchaseOrderList(purchaseOrderList).build();
    }

    /*
     MultiSKU will get receive in Eaches only
    */
    Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine =
        getPurchaseOrderAndLineToReceive(
            purchaseOrderList,
            Optional.of(receivingRequest.getQuantity()),
            receivingRequest.getDeliveryNumber());

    checkValidPoAndLine(
        selectedPoAndLine, receivingRequest.getCaseUPC(), receivingRequest.getDeliveryNumber());
    TenantContext.setAdditionalParams("hasMultiSKU", Boolean.TRUE);
    Container container =
        createAndPublishMultiSKUContainer(
            receivingRequest,
            selectedPoAndLine.getKey(),
            selectedPoAndLine.getValue(),
            receivingRequest.getQuantity());
    if (!tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      postToDCFin(singletonList(container), selectedPoAndLine.getKey().getLegacyType(), null);
    }
    return ReceiveVendorPack.builder().container(container).build();
  }

  /**
   * Create container(s) & receipt(s) of one or more pallets and publish to downstream systems
   *
   * @param containers the container list
   * @return labelResponse for printing
   */
  public LabelResponse receiveMultiplePallets(
      List<ContainerDTO> containers, PalletSlotResponse palletSlotResponse, String docType) {

    List<Receipt> receipts = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    List<PrintRequest> printRequests = new ArrayList<>();
    List<Container> containerList = new ArrayList<>();

    containers.forEach(
        containerVO -> {
          SlotLocation slotLocation =
              LabelUtils.retrieveLocation(
                  palletSlotResponse.getLocations(), containerVO.getTrackingId());
          // Add Hold for Sale if not there
          enrichContainerTag(slotLocation, containerVO);
          Container container = transformer.reverseTransform(containerVO);
          containerList.add(populateAudit(container));
          ContainerItem containerItem = retrieveContainerItemFromContainer(containerVO);
          receipts.add(buildReceipt(container, containerItem));
          containerItems.add(containerItem);

          // Build the print data for a container
          printRequests.add(
              buildPrintRequest(container, containerItem, slotLocation.getLocation()));
        });

    List<ContainerDTO> containerDTOs = transformer.transformList(containerList);

    if (tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      endGameReceivingHelperService.createMultipleContainersOutbox(
          receipts, containerList, containerItems, containerDTOs, docType);
    } else {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)) {
        try {
          inventoryService.createContainers(containerDTOs);
        } catch (ReceivingInternalException e) {
          endGameSlottingService.cancelPalletMoves(containerList);
          throw e;
        }
      }
      containerPersisterService.createMultipleReceiptAndContainer(
          receipts, containerList, containerItems);
      containerService.publishMultipleContainersToInventory(containerDTOs);
      postToDCFin(containerList, docType, null);
    }

    Map<String, String> headers = new HashMap<>();
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM, String.valueOf((TenantContext.getFacilityNum())));
    headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    return LabelResponse.builder()
        .clientId(EndgameConstants.UI_CLIENT_ID)
        .headers(headers)
        .printRequests(printRequests)
        .build();
  }

  /**
   * Verify if container can be received
   *
   * @param trackingId the tracking id of container
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public void verifyContainerReceivable(String trackingId) {
    Container containerCheck = containerService.findByTrackingId(trackingId);
    checkContainerExists(containerCheck);
    checkContainerExpiry(containerCheck);
  }

  private void checkContainerExpiry(Container container) {
    if (Objects.nonNull(container)
        && StringUtils.endsWithIgnoreCase(
            container.getContainerStatus(), ReceivingConstants.STATUS_BACKOUT)) {
      LOGGER.error(
          "Container already present and it an expired container. Hence it cannot be received");
      throw new ReceivingConflictException(
          ExceptionCodes.CONTAINER_ALREADY_EXPIRED,
          "Container already expired for this TCL: " + container.getTrackingId());
    }
  }

  private void checkContainerExists(Container container) {
    if (Objects.nonNull(container)
        && StringUtils.endsWithIgnoreCase(
            container.getContainerStatus(), ReceivingConstants.AVAILABLE)) {
      LOGGER.error("Container already present, cannot receive");
      throw new ReceivingConflictException(
          ExceptionCodes.CONTAINER_ALREADY_EXISTS,
          "Container already exists for this TCL: " + container.getTrackingId());
    }
  }

  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.REST,
      executionFlow = "GDM-Scan-UPC",
      externalCall = true)
  protected List<PurchaseOrder> getPurchaseOrders(ScanEventData scanEventData) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    String deliveryDocument = null;

    List<String> caseUPCs = retrieveCaseUPC(scanEventData.getCaseUPC());

    Collection<List<String>> upcBatches =
        ReceivingUtils.batchifyCollection(caseUPCs, endgameManagedConfig.getNosUPCForBulkScan());

    LOGGER.info(
        "Got nos of possible UPC to scan for [upc={}] is [size={}]. And No of batch call expected for GDM is [batchSize={}]",
        scanEventData.getCaseUPC(),
        caseUPCs.size(),
        upcBatches.size());

    for (List<String> upcBatch : upcBatches) {
      try {
        LOGGER.info(
            "UPC Batch going for [scanUPC={}] for [deliveryNumber={}]",
            gson.toJson(upcBatch),
            scanEventData.getDeliveryNumber());
        deliveryDocument =
            endGameDeliveryService.findDeliveryDocument(
                scanEventData.getDeliveryNumber(),
                ReceivingUtils.convertCommaSeparatedString(upcBatch),
                httpHeaders);
      } catch (Exception exception) {
        LOGGER.error(
            "Got error in scanUPC for [upcBatch={}] [errorMessage={}]",
            gson.toJson(upcBatch),
            exception.getMessage());
      }
      if (!Objects.isNull(deliveryDocument)) {
        LOGGER.debug("Got the success response from GDM on scanUPC . Hence breaking the loop");
        break;
      }
    }

    if (Objects.isNull(deliveryDocument)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(
              ReceivingException.NO_PO_LINE_AUTOMATED_FLOW,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }

    List<PurchaseOrder> purchaseOrderList =
        Arrays.asList(gson.fromJson(deliveryDocument, PurchaseOrder[].class));

    purchaseOrderList =
        removePurchaseOrdersWithOldMustArriveByDate(
            purchaseOrderList,
            tenantSpecificConfigReader.getMabdNoOfDays(MABD_RESTRICT_NO_OF_DAYS));

    if (CollectionUtils.isEmpty(purchaseOrderList)) {
      LOGGER.error(
          String.format(
              ReceivingException.NO_PO_LINE_AUTOMATED_FLOW,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(
              ReceivingException.NO_PO_LINE_AUTOMATED_FLOW,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }
    sortDeliveryDocumentByMustArriveByDate(purchaseOrderList);
    EndGameUtils.enrichDefaultSellerIdInPurchaseOrders(
        purchaseOrderList,
        endgameManagedConfig.getWalmartDefaultSellerId(),
        endgameManagedConfig.getSamsDefaultSellerId());

    purchaseOrderList = EndGameUtils.removeCancelledDocument(purchaseOrderList);
    if (CollectionUtils.isEmpty(purchaseOrderList)) {
      LOGGER.error(
          String.format(
              ReceivingException.NO_PO_LINE_AUTOMATED_FLOW,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(
              ReceivingException.NO_PO_LINE_AUTOMATED_FLOW,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }
    return purchaseOrderList;
  }

  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.INTERNAL,
      executionFlow = "CaseUPC-LookUp")
  private List<String> retrieveCaseUPC(String caseUPC) {

    List<SlottingDestination> slottingDestinationList =
        endGameSlottingService.findByCaseUPC(caseUPC);

    /*
      For child upc scan, receiving will not get Slotting Destination.
      So, looking for the real caseUPC
    */

    if (CollectionUtils.isEmpty(slottingDestinationList)) {

      StringBuilder caseUPCBuilder =
          new StringBuilder(EndgameConstants.AT).append(caseUPC).append(EndgameConstants.AT);

      List<SlottingDestination> slottingDestinations =
          endGameSlottingService.findByPossibleUPCsContains(caseUPCBuilder.toString());
      if (CollectionUtils.isEmpty(slottingDestinations)) {
        slottingDestinations = Collections.emptyList();
      }
      List<String> upcs =
          slottingDestinations
              .stream()
              .map(SlottingDestination::getCaseUPC)
              .collect(Collectors.toList());
      upcs.add(caseUPC);

      LOGGER.info("UPCs to query to GDM for [scanUPC={}]", upcs);
      return upcs;
    }
    Set<String> caseUPCSet =
        slottingDestinationList
            .stream()
            .map(SlottingDestination::getCaseUPC)
            .collect(Collectors.toSet());
    return Collections.list(Collections.enumeration(caseUPCSet));
  }

  /**
   * This method is responsible for selecting the PO and POLine against which receiving will happen.
   *
   * @param purchaseOrders
   * @param deliveryNumber
   * @return
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.INTERNAL,
      executionFlow = "MABD-Calc-Line-Select")
  protected Pair<PurchaseOrder, PurchaseOrderLine> getPurchaseOrderAndLineToReceive(
      List<PurchaseOrder> purchaseOrders, Optional<Integer> eachQuantity, long deliveryNumber) {
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    for (PurchaseOrder purchaseOrder : purchaseOrders) {
      poNumberList.add(purchaseOrder.getPoNumber());
      for (PurchaseOrderLine purchaseOrderLine : ListUtils.emptyIfNull(purchaseOrder.getLines())) {
        poLineNumberSet.add(purchaseOrderLine.getPoLineNumber());
      }
    }

    List<ReceiptSummaryEachesResponse> qtyByPoAndPoLineList =
        receiptService.receivedQtyByPoAndPoLineList(poNumberList, poLineNumberSet);
    Map<String, Long> receivedQtyByPoAndPoLineMap = new HashMap<>();
    for (ReceiptSummaryEachesResponse qtyByPoAndPoLine : qtyByPoAndPoLineList) {
      String key =
          qtyByPoAndPoLine.getPurchaseReferenceNumber()
              + EndgameConstants.DELIM_DASH
              + qtyByPoAndPoLine.getPurchaseReferenceLineNumber();
      receivedQtyByPoAndPoLineMap.put(key, qtyByPoAndPoLine.getReceivedQty());
    }
    /*
     * First receive against totalOrderQty for all PO/POLine.
     * If receivedQty is equal to all the PO/POLine, then
     * start receiving against overageQtyLimit.
     */

    // Check suitable PO/POLine considering totalOrderQty
    Pair<PurchaseOrder, PurchaseOrderLine> purchaseOrderAndLine =
        findPurchaseOrderAndLine(
            purchaseOrders,
            receivedQtyByPoAndPoLineMap,
            eachQuantity,
            Boolean.FALSE,
            deliveryNumber);
    if (Objects.nonNull(purchaseOrderAndLine)) {
      return purchaseOrderAndLine;
    }
    /*
     Check suitable PO/POLine considering totalOrderQty and overageQtyLimit
    */
    purchaseOrderAndLine =
        findPurchaseOrderAndLine(
            purchaseOrders,
            receivedQtyByPoAndPoLineMap,
            eachQuantity,
            Boolean.TRUE,
            deliveryNumber);
    return purchaseOrderAndLine;
  }

  /**
   * This method is responsible to find a suitable PO/POLine
   *
   * @param purchaseOrders
   * @param qtyByPoAndPoLineMap
   * @param shouldAddOverageQtyLimit
   * @param deliveryNumber
   * @return {@link DeliveryDocumentLine}
   */
  private Pair<PurchaseOrder, PurchaseOrderLine> findPurchaseOrderAndLine(
      List<PurchaseOrder> purchaseOrders,
      Map<String, Long> qtyByPoAndPoLineMap,
      Optional<Integer> eachQty,
      boolean shouldAddOverageQtyLimit,
      long deliveryNumber) {
    Pair<PurchaseOrder, PurchaseOrderLine> selectedPOAndLine = null;
    for (PurchaseOrder purchaseOrder : purchaseOrders) {
      List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getLines();
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLines) {
        Integer maxReceiveEachQty =
            getPurchaseOrderLineMaxReceiveEachQty(purchaseOrderLine, shouldAddOverageQtyLimit);
        String key =
            purchaseOrder.getPoNumber()
                + EndgameConstants.DELIM_DASH
                + purchaseOrderLine.getPoLineNumber();
        Long totalReceivedQty = qtyByPoAndPoLineMap.get(key);
        totalReceivedQty = Objects.isNull(totalReceivedQty) ? 0 : totalReceivedQty;

        int eachQtyToReceive =
            eachQty.isPresent()
                ? eachQty.get()
                : calculateEachQtyToReceive(
                    purchaseOrderLine, deliveryNumber, purchaseOrder.getBaseDivisionCode());

        if (totalReceivedQty + eachQtyToReceive <= maxReceiveEachQty) {
          selectedPOAndLine = new Pair<>(purchaseOrder, purchaseOrderLine);
          break;
        }
      }
      if (Objects.nonNull(selectedPOAndLine)) {
        break;
      }
    }
    return selectedPOAndLine;
  }

  protected Integer getPurchaseOrderLineMaxReceiveEachQty(
      PurchaseOrderLine purchaseOrderLine, boolean shouldAddOverageQtyLimit) {
    return (shouldAddOverageQtyLimit
        ? (ReceivingUtils.conversionToEaches(
                purchaseOrderLine.getOrdered().getQuantity(),
                purchaseOrderLine.getOrdered().getUom(),
                purchaseOrderLine.getVnpk().getQuantity(),
                purchaseOrderLine.getWhpk().getQuantity())
            + ReceivingUtils.conversionToEaches(
                purchaseOrderLine.getOvgThresholdLimit().getQuantity(),
                purchaseOrderLine.getOvgThresholdLimit().getUom(),
                purchaseOrderLine.getVnpk().getQuantity(),
                purchaseOrderLine.getWhpk().getQuantity()))
        : (ReceivingUtils.conversionToEaches(
            purchaseOrderLine.getOrdered().getQuantity(),
            purchaseOrderLine.getOrdered().getUom(),
            purchaseOrderLine.getVnpk().getQuantity(),
            purchaseOrderLine.getWhpk().getQuantity())));
  }

  /**
   * This method is responsible for creating Receipt and Container and also publish the container to
   * Inventory and post to DCFin.
   *
   * @param scanEventData
   * @param purchaseOrder
   * @param purchaseOrderLine
   * @param eachQuantity
   */
  protected Container createAndPublishContainer(
      ScanEventData scanEventData,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity) {
    boolean isCreateContainerInSyncForInventory =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY);
    Container container =
        endgameContainerService.getContainer(
            scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity);
    if (tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      endGameReceivingHelperService.createAndSaveContainerAndReceiptOutbox(
          scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);
      return container;
    }
    if (isCreateContainerInSyncForInventory) {
      inventoryService.createContainers(transformer.transformList(singletonList(container)));
    }
    endgameContainerService.createAndSaveContainerAndReceipt(
        scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);
    if (isCreateContainerInSyncForInventory) {
      containerService.publishMultipleContainersToInventory(
          singletonList(transformer.transform(container)));
    } else {
      endgameContainerService.publishContainer(transformer.transform(container));
    }
    return container;
  }

  protected Container createReceiptAndContainer(
      EndgameReceivingRequest receivingRequest,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine) {
    Container container =
        endgameContainerService.getContainer(receivingRequest, purchaseOrder, purchaseOrderLine);

    inventoryService.createContainers(transformer.transformList(singletonList(container)));

    endgameContainerService.createAndSaveContainerAndReceipt(
        receivingRequest, purchaseOrder, purchaseOrderLine, container);

    return container;
  }

  /**
   * This method is responsible for creating Receipt and Container and also publish the container to
   * Inventory.
   *
   * @param scanEventData
   * @param purchaseOrder
   * @param purchaseOrderLine
   * @param eachQuantity
   */
  protected Container createAndPublishMultiSKUContainer(
      ScanEventData scanEventData,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity) {
    boolean isCreateContainerInSyncForInventory =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY);
    Container container = containerService.findByTrackingId(scanEventData.getTrailerCaseLabel());
    if (Objects.isNull(container)) {
      container =
          endgameContainerService.getContainer(
              scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity);
    } else {
      container =
          endgameContainerService.addItemAndGetContainer(
              container, scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity);
    }

    if (tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      endGameReceivingHelperService.createAndSaveContainerAndReceiptOutbox(
          scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);
      return container;
    }
    if (isCreateContainerInSyncForInventory) {
      inventoryService.createContainers(transformer.transformList(singletonList(container)));
    }
    endgameContainerService.createAndSaveContainerAndReceipt(
        scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);
    if (isCreateContainerInSyncForInventory) {
      containerService.publishMultipleContainersToInventory(
          singletonList(transformer.transform(container)));
    } else {
      endgameContainerService.publishContainer(transformer.transform(container));
    }
    return container;
  }

  /**
   * Multi Pallet Receiving - Send data to dcfin
   *
   * @param containers list of container
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.REST,
      externalCall = true,
      executionFlow = "DCFin-Pub")
  protected void postToDCFin(
      List<Container> containers, String docType, DeliveryMetaDataRequest deliveryMetaDataRequest) {

    containers.forEach(
        container -> {
          if (!CollectionUtils.isEmpty(container.getContainerItems())) {
            List<Distribution> distributions = new ArrayList<>();
            Distribution distribution = new Distribution();
            distribution.setDestNbr(TenantContext.getFacilityNum());
            distribution.setAllocQty(container.getContainerItems().get(0).getQuantity());
            distributions.add(distribution);
            container.getContainerItems().get(0).setDistributions(distributions);
          }
        });

    DeliveryMetaData deliveryMetaData =
        getDeliveryMetaData(
            deliveryMetaDataRequest, String.valueOf(containers.get(0).getDeliveryNumber()));
    dCFinServiceV2.postReceiptUpdateToDCFin(
        containers, ReceivingUtils.getHeaders(), true, deliveryMetaData, docType);
  }

  private DeliveryMetaData getDeliveryMetaData(
      DeliveryMetaDataRequest deliveryMetaDataRequest, String deliveryNumber) {
    DeliveryMetaData deliveryMetaData;
    if (deliveryMetaDataRequest == null) {
      deliveryMetaData = deliveryMetaDataService.findByDeliveryNumber(deliveryNumber).orElse(null);
    } else {
      deliveryMetaData =
          DeliveryMetaData.builder()
              .trailerNumber(deliveryMetaDataRequest.getTrailerNumber())
              .billCode(deliveryMetaDataRequest.getBillCode())
              .carrierName(deliveryMetaDataRequest.getCarrierName())
              .carrierScacCode(deliveryMetaDataRequest.getCarrierScacCode())
              .build();
    }
    return deliveryMetaData;
  }

  private void enrichContainerTag(SlotLocation slotLocation, ContainerDTO containerVO) {
    List<String> inventoryTags = slotLocation.getInventoryTags();

    if (CollectionUtils.isEmpty(inventoryTags)) {
      LOGGER.info(
          "Tags from [slotting={}] for [container={}]",
          slotLocation.getInventoryTags(),
          containerVO.getTrackingId());
      return;
    }

    List<ContainerTag> tags =
        CollectionUtils.isEmpty(containerVO.getTags()) ? new ArrayList<>() : containerVO.getTags();
    Optional<ContainerTag> retrivedContainerTag = Optional.empty();

    if (!CollectionUtils.isEmpty(tags)) {
      retrivedContainerTag =
          tags.stream()
              .filter(
                  containerTag ->
                      ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE.equalsIgnoreCase(
                          containerTag.getTag()))
              .findAny();
    }

    if (retrivedContainerTag.isPresent()) {
      inventoryTags =
          inventoryTags
              .stream()
              .filter(s -> !ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE.equalsIgnoreCase(s))
              .collect(Collectors.toList());
    }

    inventoryTags.forEach(
        inventoryTag -> {
          ContainerTag containerTag =
              new ContainerTag(inventoryTag, ReceivingConstants.CONTAINER_SET);
          tags.add(containerTag);
        });

    containerVO.setTags(tags);
    LOGGER.info(
        "Container Tag is added successfully into inventory payload for [contaierId={}]",
        containerVO.getTrackingId());
  }

  public void enrichContainerTag(ScanEventData scanEventData) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        ReceivingConstants.IS_HOLD_FOR_SALE_TAG_ENABLED)) {
      ContainerTag containerTag =
          new ContainerTag(
              ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE, ReceivingConstants.CONTAINER_SET);
      List<ContainerTag> tags =
          CollectionUtils.isEmpty(scanEventData.getContainerTagList())
              ? new ArrayList<>()
              : scanEventData.getContainerTagList();
      tags.add(containerTag);
      scanEventData.setContainerTagList(tags);
    }
  }

  /**
   * Retrieve container item from the given container
   *
   * @param container the container object
   * @return containerItem the container item details from the container
   */
  public ContainerItem retrieveContainerItemFromContainer(ContainerDTO container) {
    if (!CollectionUtils.isEmpty(container.getContainerItems())) {
      return container.getContainerItems().get(0);
    }
    LOGGER.error(EndgameConstants.EMPTY_CONTAINER_ITEM_LIST);
    throw new ReceivingBadDataException(
        ExceptionCodes.INVALID_DATA, EndgameConstants.EMPTY_CONTAINER_ITEM_LIST);
  }

  /**
   * Multi Pallet Receiving - Send data to MM
   *
   * @param containerDTOs list of containers
   * @param locations the slot location
   */
  public void publishMove(List<ContainerDTO> containerDTOs, List<SlotLocation> locations) {

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    List<Container> containers = transformer.reverseTransformList(containerDTOs);

    /*
     Adding moveType as bulk as multi container move will get generate
    */
    httpHeaders.add(ReceivingConstants.MOVE_TYPE, ReceivingConstants.MOVE_TYPE_BULK);

    List<MoveInfo> listOfMoveInfo = new ArrayList<>();
    Map<String, SlotLocation> slotLocationMap = LabelUtils.retrieveLocation(locations);

    /*
     skipping the move if moveRequired is false from slotting
    */
    containers
        .stream()
        .filter(container -> slotLocationMap.get(container.getTrackingId()).isMoveRequired())
        .forEach(
            container -> {
              SlotLocation slotLocation = slotLocationMap.get(container.getTrackingId());
              validateSlotMove(slotLocation, container.getTrackingId());
              MoveInfo moveInfo =
                  MoveInfo.builder()
                      .containerTag(container.getTrackingId())
                      .correlationID(TenantContext.getCorrelationId())
                      .fromLocation(container.getLocation())
                      .moveEvent(MoveEvent.CREATE.toString())
                      .moveQty(InstructionUtils.getMoveQuantity(container))
                      .moveQtyUOM(appConfig.getMoveQtyUom())
                      .moveType(generateMoveType(slotLocation))
                      .priority(appConfig.getMovePriority())
                      // TODO: Need to check this number, apparently comes from OF in other markets
                      .sequenceNbr(1)
                      .toLocation(slotLocation.getLocation())
                      .build();
              listOfMoveInfo.add(moveInfo);
            });

    movePublisher.publishMove(listOfMoveInfo, httpHeaders);
  }

  private void validateSlotMove(SlotLocation slotLocation, String trackingId) {
    if (Objects.isNull(slotLocation)) {
      LOGGER.error(EndgameConstants.LOG_SLOTTING_NO_LOCATION_RESPONSE, trackingId);

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SLOTTING_REQ,
          String.format(
              EndgameConstants.SLOTTING_BAD_RESPONSE_ERROR_MSG,
              HttpStatus.UNPROCESSABLE_ENTITY,
              trackingId));
    }
  }

  private MoveType generateMoveType(SlotLocation slotLocation) {

    MoveType moveType = null;

    switch (slotLocation.getMoveType()) {
      case PUTAWAY:
        moveType =
            MoveType.builder()
                .code(ReceivingConstants.PUTAWAY_MOVE_CODE)
                .desc(ReceivingConstants.PUTAWAY_MOVE_DESC)
                .build();
        break;
      case HAUL:
        moveType =
            MoveType.builder()
                .code(appConfig.getMoveTypeCode())
                .desc(appConfig.getMovetypeDesc())
                .build();
        break;
    }
    return moveType;
  }

  /**
   * Build Receipt
   *
   * @param container the container object
   * @param containerItem the container item object
   * @return the receipt info
   */
  private Receipt buildReceipt(Container container, ContainerItem containerItem) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(container.getDeliveryNumber());
    receipt.setDoorNumber(container.getLocation());
    receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
    receipt.setVnpkQty(containerItem.getVnpkQty());
    receipt.setWhpkQty(containerItem.getWhpkQty());
    receipt.setQuantity(containerItem.getQuantity());
    receipt.setQuantityUom(EACHES);
    receipt.setEachQty(containerItem.getQuantity());
    receipt.setSsccNumber(container.getSsccNumber());
    return receipt;
  }

  /**
   * Populate audit fields in container
   *
   * @param container the container object
   * @return container with audit fields
   */
  private Container populateAudit(Container container) {
    Date date = new Date();
    container.setCreateTs(date);
    container.setPublishTs(date);
    container.setCompleteTs(date);
    container.setLastChangedTs(date);
    return container;
  }

  /**
   * Build the label print request
   *
   * @param container the container details
   * @param containerItem the container item details
   * @param location the slot location of pallet
   * @return printRequest the print request
   */
  private PrintRequest buildPrintRequest(
      Container container, ContainerItem containerItem, String location) {

    String tpl = container.getTrackingId();
    String tplPrefix = tpl.substring(0, tpl.length() - 4);
    String tplSuffix = tpl.substring(tpl.length() - 4);
    String qty = calculateQuantity(containerItem.getQuantity(), containerItem.getCaseQty());

    List<Datum> printingData = new ArrayList<>();
    //    TODO - map trailer when value is available
    printingData.add(new Datum(EndgameConstants.TRAILER, EndgameConstants.EMPTY_STRING));
    printingData.add(new Datum(EndgameConstants.DATE, ReceivingUtils.dateInEST()));
    printingData.add(
        new Datum(EndgameConstants.DELIVERY_NUMBER, String.valueOf(container.getDeliveryNumber())));
    printingData.add(new Datum(EndgameConstants.DESTINATION, location));
    printingData.add(new Datum(EndgameConstants.QTY, qty));
    printingData.add(
        new Datum(EndgameConstants.ITEM, String.valueOf(containerItem.getItemNumber())));
    printingData.add(new Datum(EndgameConstants.DESCRIPTION, containerItem.getDescription()));
    printingData.add(new Datum(EndgameConstants.UPCBAR, containerItem.getCaseUPC()));
    printingData.add(new Datum(EndgameConstants.USER, ReceivingUtils.retrieveUserId()));
    printingData.add(new Datum(EndgameConstants.TCL, tpl));
    printingData.add(new Datum(EndgameConstants.TCLPREFIX, tplPrefix));
    printingData.add(new Datum(EndgameConstants.TCLSUFFIX, tplSuffix));

    return PrintRequest.builder()
        .formatName(endgameManagedConfig.getPrinterFormatName())
        .labelIdentifier(container.getTrackingId())
        .ttlInHours(EndgameConstants.LABEL_TTL)
        .data(printingData)
        .build();
  }

  /**
   * Calculate `quantity` value for print data
   *
   * @param quantity the eaches value
   * @param caseQty the no of eaches
   * @return quantity for print label
   */
  private String calculateQuantity(Integer quantity, Integer caseQty) {
    if (!Objects.isNull(quantity) && !Objects.isNull(caseQty) && caseQty != 0) {
      return String.valueOf(quantity / caseQty);
    }
    LOGGER.error(EndgameConstants.INVALID_QUANTITY);
    throw new ReceivingBadDataException(
        ExceptionCodes.INVALID_DATA, EndgameConstants.INVALID_QUANTITY);
  }

  /**
   * Multi Pallet Receiving - Send data to slotting
   *
   * @param containers list of containers
   * @param extraAttributes
   * @return slotting response with location
   */
  public PalletSlotResponse getSlotLocations(
      List<ContainerDTO> containers, ExtraAttributes extraAttributes) {
    String receivingType =
        ObjectUtils.isEmpty(extraAttributes.getReceivingType())
            ? ReceivingConstants.Uom.VNPK
            : extraAttributes.getReceivingType();
    List<ContainerDetail> containerDetailList = new ArrayList<>();
    containers.forEach(
        container -> {
          ContainerItem containerItem = retrieveContainerItemFromContainer(container);
          ContainerItemsDetail containerItemsDetail = new ContainerItemsDetail();
          containerItemsDetail.setCaseUPC(containerItem.getCaseUPC());
          containerItemsDetail.setItemNbr(containerItem.getItemNumber());
          containerItemsDetail.setItemUPC(containerItem.getItemUPC());
          containerItemsDetail.setSellerId(containerItem.getSellerId());
          /*
          Converting to VNPK as slotting does not support other UOM
          */
          if (EACHES.equals(receivingType)) {
            containerItemsDetail.setQty(containerItem.getQuantity());
            containerItemsDetail.setQtyUom(EACHES);
          } else {
            containerItemsDetail.setQty(
                ReceivingUtils.conversionToVendorPack(
                    containerItem.getQuantity(),
                    containerItem.getQuantityUOM(),
                    containerItem.getCaseQty(),
                    containerItem.getWhpkQty()));
            containerItemsDetail.setQtyUom(ReceivingConstants.Uom.VNPK);
            /*
            CaseQty = PalletSellableQty
            */
            containerItemsDetail.setSellableUnits(containerItem.getCaseQty().longValue());
          }

          containerItemsDetail.setRotateDate(containerItem.getRotateDate());
          containerItemsDetail.setPalletTi(containerItem.getActualTi());
          containerItemsDetail.setPalletHi(containerItem.getActualHi());
          containerItemsDetail.setBaseDivisionCode(containerItem.getBaseDivisionCode());
          containerItemsDetail.setFinancialReportingGroup(
              containerItem.getFinancialReportingGroupCode());
          ContainerDetail containerDetail = new ContainerDetail();
          containerDetail.setContainerItemsDetails(Arrays.asList(containerItemsDetail));
          containerDetail.setContainerTrackingId(container.getTrackingId());
          containerDetail.setContainerType(container.getContainerType());
          containerDetail.setContainerName(extraAttributes.getContainerSize());
          containerDetailList.add(containerDetail);
        });

    PalletSlotRequest slotRequest = new PalletSlotRequest();
    slotRequest.setContainerDetails(containerDetailList);
    slotRequest.setSourceLocation(containers.get(0).getLocation());
    slotRequest.setMessageId(TenantContext.getCorrelationId());

    return endGameSlottingService.multipleSlotsFromSlotting(
        slotRequest, extraAttributes.getIsOverboxingPallet());
  }

  protected Integer calculateEachQtyToReceive(
      PurchaseOrderLine purchaseOrderLine, long deliveryNumber, String baseDivCode) {

    String orderedUOM =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
                ReceivingConstants.ENABLE_ENTER_QUANTITY_1P)
            ? EACHES
            : purchaseOrderLine.getOrdered().getUom();
    if (EndGameUtils.isVnpkPalletItem(tenantSpecificConfigReader, purchaseOrderLine, baseDivCode)) {
      int receivedQty =
          deliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(
              purchaseOrderLine.getItemDetails().getNumber(), deliveryNumber);
      LOGGER.info("Previous Received Qty From Delivery MetData : {}", receivedQty);
      return ReceivingUtils.conversionToUOM(
          1,
          orderedUOM,
          EACHES,
          receivedQty != 0 ? receivedQty : purchaseOrderLine.getVnpk().getQuantity(),
          purchaseOrderLine.getWhpk().getQuantity());
    }
    return ReceivingUtils.conversionToUOM(
        1,
        orderedUOM,
        EACHES,
        purchaseOrderLine.getVnpk().getQuantity(),
        purchaseOrderLine.getWhpk().getQuantity());
  }

  /**
   * Check for case weight check through scan tunnel
   *
   * @param quantity
   * @param selectedPoAndLine
   * @param scanEventData
   * @return true or false
   */
  protected boolean isValidCaseWeight(
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine,
      ScanEventData scanEventData,
      Integer quantity) {
    Map<String, Object> additionalInformation =
        selectedPoAndLine.getValue().getItemDetails().getAdditionalInformation();
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_BY_CASE_WEIGHT)
        || isEmpty(additionalInformation)
        || ((boolean) additionalInformation.get(IS_NEW_ITEM))) {
      LOGGER.info(
          "Item additional information is Empty or Null for [upc={}] [deliveryNumber={}] [TCL={}]",
          scanEventData.getCaseUPC(),
          scanEventData.getDeliveryNumber(),
          scanEventData.getTrailerCaseLabel());
      return true;
    }
    if (isEmpty(scanEventData.getWeight())
        || scanEventData.getWeight() == 0
        || isEmpty(scanEventData.getWeightUnitOfMeasure())) {
      LOGGER.info(
          "Missing case weight information for [upc={}] [deliveryNumber={}] [TCL={}]",
          scanEventData.getCaseUPC(),
          scanEventData.getDeliveryNumber(),
          scanEventData.getTrailerCaseLabel());
      return true;
    }
    if (isEmpty(additionalInformation.get(EACH_WEIGHT))
        || (Double) additionalInformation.get(EACH_WEIGHT) == 0
        || isEmpty(additionalInformation.get(EACH_WEIGHT_UOM))) {
      LOGGER.info(
          "Each weight missing for [upc={}] [deliveryNumber={}] [TCL={}]",
          scanEventData.getCaseUPC(),
          scanEventData.getDeliveryNumber(),
          scanEventData.getTrailerCaseLabel());
      return true;
    }
    double itemUnitWeight =
        weightConversionToPound(
            (String) additionalInformation.get(EACH_WEIGHT_UOM),
            (Double) additionalInformation.get(EACH_WEIGHT));
    double actualWeight =
        weightConversionToPound(scanEventData.getWeightUnitOfMeasure(), scanEventData.getWeight());
    LOGGER.info(
        "Request payload data for [upc={}] [deliveryNumber={}] [TCL={}] and [quantity={} itemUnitWeight={} actualWeight={}]",
        scanEventData.getCaseUPC(),
        scanEventData.getDeliveryNumber(),
        scanEventData.getTrailerCaseLabel(),
        quantity,
        itemUnitWeight,
        actualWeight);
    return weightInValidRange(quantity, itemUnitWeight, actualWeight);
  }

  private boolean weightInValidRange(Integer quantity, double unitWeight, double actualWeight) {
    double expectedWeight = unitWeight * quantity;
    double variance = getVariance(unitWeight);
    double expWeightLower = max(0, expectedWeight - variance);
    double expWeightUpper = expectedWeight + variance;
    return (expWeightLower <= actualWeight) && (actualWeight <= expWeightUpper);
  }

  private double getVariance(double itemUnitWeight) {
    double itemLowLimit =
        max(
            itemUnitWeight,
            tenantSpecificConfigReader.getCaseWeightCheckConfig(CASE_WEIGHT_LOWER_LIMIT));
    return itemLowLimit
        * tenantSpecificConfigReader.getCaseWeightCheckConfig(CASE_WEIGHT_MULTIPLIER);
  }

  private double weightConversionToPound(String uom, double weight) {
    double lb1 = GRAM_TO_LB;
    if (uom.equalsIgnoreCase(Uom.GRAMS)) {
      weight = weight * lb1;
    }
    return weight;
  }

  protected boolean isAuditRequired(
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine, ScanEventData scanEventData) {
    boolean isAuditRequired = false;
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG)) {
      Integer vendorNumber =
          retrieveVendorInfos(selectedPoAndLine.getKey(), selectedPoAndLine.getValue());
      isAuditRequired =
          auditHelper.isAuditRequired(
              scanEventData.getDeliveryNumber(),
              selectedPoAndLine.getValue(),
              vendorNumber,
              selectedPoAndLine.getKey().getBaseDivisionCode());
      scanEventData.setIsAuditRequired(isAuditRequired);
    }
    return isAuditRequired;
  }

  protected void updateAuditEventInDeliveryMetadata(
      List<PurchaseOrder> purchaseOrderList, Long deliveryNumber, String caseUpc, int receivedQty) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG)
        && tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)) {
      boolean sendToOutbox =
          deliveryMetaDataService.updateAuditInfoInDeliveryMetaData(
              purchaseOrderList, receivedQty, deliveryNumber);
      if (sendToOutbox) {
        fetchLabelDetailsAndPersistToOutbox(deliveryNumber, caseUpc);
      }
    }
  }

  private void fetchLabelDetailsAndPersistToOutbox(long deliveryNumber, String caseUpc) {
    List<PreLabelData> labelData =
        endGameLabelingService.findByDeliveryNumberAndCaseUpcAndStatusAndDiverAckEventIsNotNull(
            deliveryNumber, caseUpc, LabelStatus.ATTACHED);
    Map<String, Object> headersMap = getAutoScanHeaders();
    labelData.forEach(
        data ->
            endgameOutboxHandler.sentToOutbox(
                data.getDiverAckEvent(),
                EndgameOutboxServiceName.OB_RECEIVE_TCL_AUTO_SCAN.getServiceName(),
                new HashMap<>(),
                headersMap));
  }

  private Map<String, Object> getAutoScanHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ACCEPT, APPLICATION_JSON_VALUE);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    headers.put(CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    headers.put(USER_ID_HEADER_KEY, DEFAULT_AUDIT_USER);
    headers.put(TENENT_FACLITYNUM, getFacilityNum());
    headers.put(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    return headers;
  }

  public void persistAttachPurchaseOrderRequestToOutbox(String requestBody) {
    endgameOutboxHandler.sentToOutbox(
        requestBody,
        EndgameOutboxServiceName.OB_ATTACH_PO_TO_DELIVERY.getServiceName(),
        new HashMap<>());
  }

  public void receiveContainer(EndgameReceivingRequest request) {
    PurchaseOrderLine line = request.getPurchaseOrder().getLines().get(0);
    Container container;
    if (tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      container = endGameReceivingHelperService
              .createReceiptAndContainerOutbox(request, request.getPurchaseOrder(), line);
    } else {
      container = createReceiptAndContainer(request, request.getPurchaseOrder(), line);
      postToDCFin(
          singletonList(container),
          request.getPurchaseOrder().getLegacyType(),
          request.getDeliveryMetaData());
    }
    publishToSCT(container);
  }

  private void publishToSCT(Container container) {
    containerService.publishMultipleContainersToInventory(
        singletonList(transformer.transform(container)));
  }

  protected void updatePreLabelDataReason(PreLabelData preLabelData, String reason) {
    if (Objects.nonNull(preLabelData)) {
      preLabelData.setReason(reason);
    }
  }

  private void checkValidPoAndLine(
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine, ScanEventData scanEventData) {
    if (Objects.isNull(selectedPoAndLine)) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), POLINE_EXHAUSTED);
      endGameLabelingService.saveOrUpdateLabel(scanEventData.getPreLabelData());
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(
              EndgameConstants.PO_PO_LINE_EXHAUSTED_ERROR_MSG,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }
  }
}
