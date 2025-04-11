package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.replaceContainerWithSSCC;
import static com.walmart.move.nim.receiving.core.utils.UomUtils.getScaledQuantity;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.getOverageType;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.isStorePalletPublishingDisabled;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.consumer.BiParameterConsumer;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentList;
import com.walmart.move.nim.receiving.core.model.InventoryContainerAdjustmentPayload;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.decant.ItemInfos;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.osdr.v2.OSDRPayload;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.mfc.common.*;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerOperation;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerRequestPayload;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerResponse;
import com.walmart.move.nim.receiving.mfc.model.controller.Invoice;
import com.walmart.move.nim.receiving.mfc.model.controller.InvoiceMeta;
import com.walmart.move.nim.receiving.mfc.model.controller.InvoiceNumberDetectionRequest;
import com.walmart.move.nim.receiving.mfc.model.controller.InvoiceNumberDetectionResponse;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.DecantItem;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.StockStateExchange;
import com.walmart.move.nim.receiving.mfc.model.inventory.AdjustmentTO;
import com.walmart.move.nim.receiving.mfc.model.inventory.EventObject;
import com.walmart.move.nim.receiving.mfc.model.inventory.ItemListItem;
import com.walmart.move.nim.receiving.mfc.model.inventory.MFCInventoryAdjustmentDTO;
import com.walmart.move.nim.receiving.mfc.transformer.HawkeyeReceiptTransformer;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

public class MFCContainerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MFCContainerService.class);

  @Value("${delivery.metadata.retrieval.size:10}")
  private Integer deliveryMetadataPageSize;

  @Value("${default.invoice.line.start.value:100}")
  private Integer defaultInvoiceLineStartValue;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ContainerItemRepository containerItemRepository;

  @Autowired private MFCDeliveryMetadataService deliveryMetaDataService;

  @Autowired private ContainerTransformer containerTransformer;

  @Autowired private ContainerService containerService;

  @Autowired private InventoryService inventoryService;

  @Autowired private Gson gson;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Autowired private MFCReceiptService mfcReceiptService;

  @Autowired private MFCDeliveryService deliveryService;

  @Autowired private DecantService decantService;

  @Autowired private ReceivingCounterService receivingCounterService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private HawkeyeReceiptTransformer hawkeyeReceiptTransformer;

  @Autowired private AsyncPersister asyncPersister;
  @Autowired private ContainerRepository containerRepository;
  @ManagedConfiguration private AppConfig appConfig;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  public List<Container> detectContainers(CommonReceiptDTO receiptDTO) {
    LOGGER.info("Selecting Containers for : {}", receiptDTO);
    List<Container> containerList;

    if (Objects.nonNull(receiptDTO.getContainerId())) {
      validateData(receiptDTO, MANUAL_MFC);
      Container container =
          containerPersisterService.findBySSCCAndDeliveryNumber(
              receiptDTO.getContainerId(), receiptDTO.getDeliveryNumber());
      List<ContainerItem> selectedContainerItems =
          container
              .getContainerItems()
              .stream()
              .filter(
                  ci ->
                      Arrays.asList(ci.getGtin(), ci.getItemUPC(), ci.getCaseUPC())
                          .contains(receiptDTO.getGtin()))
              .collect(Collectors.toList());
      if (selectedContainerItems.isEmpty()) {
        throw new ReceivingInternalException(
            ExceptionCodes.CONTAINER_NOT_FOUND,
            String.format(
                "Unable to select container item for gtin= %s on container=%s",
                receiptDTO.getGtin(), container.getTrackingId()));
      }
      container.setContainerItems(selectedContainerItems);
      publishWorkingIfApplicable(
          deliveryMetaDataService
              .findByDeliveryNumber(String.valueOf(receiptDTO.getDeliveryNumber()))
              .orElseThrow(
                  () ->
                      new ReceivingDataNotFoundException(
                          ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                          String.format(
                              "Unable to find delivery metadata with deliveryNumber=%s",
                              receiptDTO.getDeliveryNumber()))));
      containerList = Collections.singletonList(container);
    } else {
      validateData(receiptDTO, AUTO_MFC);
      try {
        containerList = selectContainers(receiptDTO.getGtin());
      } catch (ReceivingException e) {
        LOGGER.error("Unable to select container for receiptDTO = {}", receiptDTO, e);
        throw new ReceivingInternalException(
            ExceptionCodes.RECEIVING_INTERNAL_ERROR, "Unable to select container ");
      }
    }
    return containerList;
  }

  private void validateData(CommonReceiptDTO receipt, String facilityType) {
    switch (facilityType) {
      case MANUAL_MFC:
        boolean isValidated =
            Objects.isNull(receipt.getContainerId())
                || Objects.isNull(receipt.getDeliveryNumber())
                || Objects.isNull(receipt.getGtin())
                || Objects.isNull(receipt.getQuantities());
        if (isValidated) {
          throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_DATA,
              String.format(
                  "Inventory Data for Manual MFC is not proper for deliveryNumber=%s",
                  receipt.getDeliveryNumber()));
        }
        break;

      case AUTO_MFC:
        boolean isProperAutoData =
            Objects.isNull(receipt.getGtin()) || Objects.isNull(receipt.getQuantities());
        if (isProperAutoData) {
          throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_DATA,
              String.format(
                  "Inventory Data for Auto MFC is not proper for Gtin=%s", receipt.getGtin()));
        }
        break;

      default:
        LOGGER.info("Invalidate MFC FacilityType is passed");
        throw new ReceivingInternalException(
            ExceptionCodes.INVALID_DATA, "No Proper data found to perform MFC Operation");
    }
  }

  // TODO: Have to put the logic for past N days
  // TODO: May need to revisit DB fields for optimisation
  // TODO: Add containerStatus as well to the query along with Gtin
  private List<Container> selectContainers(String gtin) throws ReceivingException {
    // Find all invoiceNumbers & trackingIds from ContainerItem using gtin
    List<ContainerItem> containerItems =
        containerItemRepository
            .findByGtinAndFacilityCountryCodeAndFacilityNum(
                gtin, TenantContext.getFacilityCountryCode(), TenantContext.getFacilityNum())
            .stream()
            .filter(
                containerItem -> !containerItem.getInvoiceNumber().endsWith(DUMMY_INVOICE_SUFFIX))
            .collect(Collectors.toList());

    // Find all Containers using trackingIds
    Set<String> trackingIds =
        containerItems.stream().map(ContainerItem::getTrackingId).collect(Collectors.toSet());
    Set<Container> containers =
        containerService.getContainerListByTrackingIdList(new ArrayList<>(trackingIds));

    // Find all DeliveryMetaData using deliveryNumber
    Set<String> deliveryNumbers =
        containers
            .stream()
            .map(c -> c.getDeliveryNumber().toString())
            .distinct()
            .collect(Collectors.toSet());
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataService.findAllByDeliveryNumberIn(new ArrayList<>(deliveryNumbers));

    // Select a delivery which is active:
    // Finding WRK delivery
    Container selectedContainer;
    ContainerItem selectedContainerItem;
    Optional<DeliveryMetaData> openDelivery =
        deliveryMetaDataList
            .stream()
            .filter(
                deliveryMetaData -> deliveryMetaData.getDeliveryStatus().equals(DeliveryStatus.WRK))
            .min(Comparator.comparing(DeliveryMetaData::getCreatedDate));
    if (openDelivery.isPresent()) {
      return getContainer(containerItems, containers, openDelivery.get());
    }

    // finding ARV/SCH delivery
    Optional<DeliveryMetaData> preOpenDelivery =
        deliveryMetaDataList
            .stream()
            .filter(
                deliveryMetaData ->
                    deliveryMetaData.getDeliveryStatus().equals(DeliveryStatus.SCH)
                        || deliveryMetaData.getDeliveryStatus().equals(DeliveryStatus.ARV))
            .min(Comparator.comparing(DeliveryMetaData::getCreatedDate));
    if (preOpenDelivery.isPresent()) {
      return getContainer(containerItems, containers, preOpenDelivery.get());
    }

    throw new ReceivingInternalException(
        ExceptionCodes.RECEIVING_INTERNAL_ERROR, "Unable to select container ");
  }

  private List<Container> getContainer(
      List<ContainerItem> containerItems,
      Set<Container> containers,
      DeliveryMetaData deliveryMetaData) {
    List<ContainerItem> selectedContainerItems;
    List<Container> selectedContainers;
    selectedContainers =
        containers
            .stream()
            .filter(
                c -> c.getDeliveryNumber().toString().equals(deliveryMetaData.getDeliveryNumber()))
            .collect(Collectors.toList());
    Set<String> trackingIds =
        selectedContainers.stream().map(Container::getTrackingId).collect(Collectors.toSet());
    selectedContainerItems =
        containerItems
            .stream()
            .filter(ci -> trackingIds.contains(ci.getTrackingId()))
            .collect(Collectors.toList());
    ContainerUtils.populateContainerItemInContainer(selectedContainers, selectedContainerItems);
    publishWorkingIfApplicable(deliveryMetaData);
    return selectedContainers;
  }

  public void publishWorkingIfApplicable(DeliveryMetaData deliveryMetaData) {
    if (StoreDeliveryStatus.isValidDeliveryStatusForUpdate(
        StoreDeliveryStatus.getDeliveryStatus(deliveryMetaData.getDeliveryStatus()),
        StoreDeliveryStatus.WORKING)) {
      deliveryStatusPublisher.publishDeliveryStatus(
          Long.parseLong(deliveryMetaData.getDeliveryNumber()),
          DeliveryStatus.WORKING.toString(),
          new ArrayList<>(),
          ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders()));
      deliveryMetaData.setDeliveryStatus(DeliveryStatus.WRK);
      deliveryMetaDataService.save(deliveryMetaData);
      LOGGER.info(
          "Published WorkingEvent successfully for deliveryNumber={}",
          deliveryMetaData.getDeliveryNumber());
    }
  }

  public ContainerResponse publishSelectedContainer(
      ContainerRequestPayload containerRequestPayload) {
    LOGGER.info(
        "Selected container to publish sscc = {} and deliveryNumber={}",
        containerRequestPayload.getTrackingId(),
        containerRequestPayload.getDeliveryNumber());

    Container container =
        containerPersisterService.findBySSCCAndDeliveryNumber(
            containerRequestPayload.getTrackingId(), containerRequestPayload.getDeliveryNumber());
    LOGGER.info(
        "Selected container for publishing is sscc_number = {} and deliveryNumber={}",
        containerRequestPayload.getTrackingId(),
        containerRequestPayload.getDeliveryNumber());

    updateContainer(
        containerRequestPayload.getTrackingId(),
        containerRequestPayload.getDeliveryNumber(),
        container);

    List<ContainerDTO> containerDTOList =
        containerTransformer.transformList(Arrays.asList(container));

    containerDTOList.removeIf(
        containerDTO -> isStorePalletPublishingDisabled(containerDTO, tenantSpecificConfigReader));

    containerDTOList.forEach(containerDTO -> replaceContainerWithSSCC(containerDTO));

    TenantContext.setAdditionalParams("kafkaHeaders:containerLocation", "MFC");
    containerService.publishMultipleContainersToInventory(containerDTOList);
    return ContainerResponse.builder()
        .type(ContainerOperation.CONTAINER_PUBLISHED)
        .containers(containerDTOList)
        .build();
  }

  private void updateContainer(String trackingId, Long deliveryNumber, Container container) {

    container.setInventoryStatus(MFCConstant.AVAILABLE);
    containerPersisterService.saveContainer(container);

    LOGGER.info(
        "Container is successfully updated with sscc = {} deliveryNumber = {}",
        trackingId,
        deliveryNumber);
  }

  private void throwErrorIfContainerNotFound(String trackingId, List<ContainerDTO> containers) {
    if (Objects.isNull(containers) || containers.isEmpty()) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND,
          String.format("Container = %s not found ", trackingId));
    }
  }

  private Map<Long, DeliveryStatus> findOpenDeliveries() {

    List<DeliveryStatus> deliveryStatuses = new ArrayList<>();

    mfcManagedConfig
        .getDeliveryStatusForOpenDeliveries()
        .forEach(status -> deliveryStatuses.add(DeliveryStatus.getDeliveryStatus(status)));

    final List<DeliveryMetaData> deliveryMetaDataList = new ArrayList<>();
    int counter = 0;

    Page<DeliveryMetaData> pagedData = null;
    LOGGER.info("Going to retrieve openDelivery for request");
    do {
      Pageable pageable =
          PageRequest.of(counter, deliveryMetadataPageSize, Sort.by("lastUpdatedDate").ascending());
      pagedData = deliveryMetaDataService.findByDeliveryStatusIn(deliveryStatuses, pageable);

      pagedData
          .getContent()
          .forEach(deliveryMetaData -> deliveryMetaDataList.add(deliveryMetaData));

      ++counter;
      LOGGER.info(
          "Retrieve open deliveries batch={} and isLastPage={}", counter, pagedData.isLast());
    } while (!pagedData.isLast());
    return deliveryMetaDataList
        .stream()
        .collect(
            Collectors.toMap(
                data -> Long.valueOf(data.getDeliveryNumber()), data -> data.getDeliveryStatus()));
  }

  public InvoiceNumberDetectionResponse detectInvoiceNumber(
      InvoiceNumberDetectionRequest invoiceNumberDetectionRequest) {

    ContainerItem containerItem = null;
    ContainerDTO containerDTO = null;

    if (Objects.nonNull(invoiceNumberDetectionRequest.getDeliveryNumber())) {
      LOGGER.info(
          "Going to retrieve the container by deliveryNumber={} and trackingId={}",
          invoiceNumberDetectionRequest.getTrackingId(),
          invoiceNumberDetectionRequest.getDeliveryNumber());
      Container container =
          containerPersisterService.findBySSCCAndDeliveryNumber(
              invoiceNumberDetectionRequest.getTrackingId(),
              invoiceNumberDetectionRequest.getDeliveryNumber());

      containerItem =
          container
              .getContainerItems()
              .stream()
              .filter(
                  ci ->
                      StringUtils.equalsIgnoreCase(
                              ci.getItemUPC(), invoiceNumberDetectionRequest.getGtin())
                          || StringUtils.equalsIgnoreCase(
                              ci.getCaseUPC(), invoiceNumberDetectionRequest.getGtin())
                          || StringUtils.equalsIgnoreCase(
                              ci.getGtin(), invoiceNumberDetectionRequest.getGtin()))
              .findFirst()
              .orElse(container.getContainerItems().stream().findAny().get());
      LOGGER.info(
          "Selected container_item for deliveryNumber={} and trackingId={} is {} ",
          invoiceNumberDetectionRequest.getDeliveryNumber(),
          invoiceNumberDetectionRequest.getTrackingId(),
          containerItem.getTrackingId());

    } else {
      LOGGER.info(
          "Going to retrieve the container by  trackingId={} on openDeliveries",
          invoiceNumberDetectionRequest.getTrackingId());

      containerDTO =
          findContainerFromOpenDelivery(invoiceNumberDetectionRequest.getTrackingId())
              .stream()
              .findFirst()
              .get();
      containerItem =
          containerDTO
              .getContainerItems()
              .stream()
              .filter(
                  ci ->
                      StringUtils.equalsIgnoreCase(
                              ci.getItemUPC(), invoiceNumberDetectionRequest.getGtin())
                          || StringUtils.equalsIgnoreCase(
                              ci.getCaseUPC(), invoiceNumberDetectionRequest.getGtin())
                          || StringUtils.equalsIgnoreCase(
                              ci.getGtin(), invoiceNumberDetectionRequest.getGtin()))
              .findFirst()
              .orElse(containerDTO.getContainerItems().stream().findAny().get());

      LOGGER.info(
          "Selected container_item for open delivery deliveryNumber={} and trackingId={} is {} ",
          containerDTO.getDeliveryNumber(),
          invoiceNumberDetectionRequest.getTrackingId(),
          containerItem.getTrackingId());
    }

    Invoice invoice =
        Invoice.builder()
            .number(containerItem.getInvoiceNumber())
            .lineNumber(containerItem.getInvoiceLineNumber())
            .build();

    InvoiceMeta invoiceMeta =
        InvoiceMeta.builder()
            .foundTrackingId(containerItem.getTrackingId())
            .foundTrackingId(invoiceNumberDetectionRequest.getTrackingId())
            .foundDelivery(
                Objects.isNull(containerDTO)
                    ? invoiceNumberDetectionRequest.getDeliveryNumber()
                    : containerDTO.getDeliveryNumber())
            .build();

    return InvoiceNumberDetectionResponse.builder()
        .deliveryNumber(invoiceNumberDetectionRequest.getDeliveryNumber())
        .gtin(invoiceNumberDetectionRequest.getGtin())
        .trackingId(invoiceNumberDetectionRequest.getTrackingId())
        .invoice(invoice)
        .meta(invoiceMeta)
        .build();
  }

  public void initiateContainerRemoval(OSDRPayload osdrPayload) {

    List<String> ununsedContainerIds =
        osdrPayload
            .getSummary()
            .getContainers()
            .stream()
            .map(container -> container.getTrackingId())
            .collect(Collectors.toList());

    if (Objects.isNull(ununsedContainerIds) || ununsedContainerIds.isEmpty()) {
      LOGGER.warn(
          "No unused container found for deliveryNumber={} and hence, exiting",
          osdrPayload.getDeliveryNumber());
      return;
    }

    LOGGER.info(
        "Unused container for deliveryNumber={} are {}",
        osdrPayload.getDeliveryNumber(),
        ununsedContainerIds);

    List<Container> containers =
        containerPersisterService.findByDeliveryNumberAndSsccIn(
            osdrPayload.getDeliveryNumber(), ununsedContainerIds);

    List<InventoryContainerAdjustmentPayload> inventoryContainerAdjustmentPayload =
        new ArrayList<>();

    if (CollectionUtils.isNotEmpty(containers)) {
      for (Container container : containers) {
        inventoryContainerAdjustmentPayload.add(
            InventoryContainerAdjustmentPayload.builder()
                .reasonCode(QuantityType.SHORTAGE.getInventoryErrorReason())
                .trackingId(container.getSsccNumber())
                .build());
      }
    }

    if (inventoryContainerAdjustmentPayload.isEmpty()) {
      LOGGER.info("There's no container received. So skipping inventory.");
      return;
    }

    //
    List<List<InventoryContainerAdjustmentPayload>> batchedInventoryAdjustmentPayload =
        ListUtils.partition(
            inventoryContainerAdjustmentPayload,
            mfcManagedConfig.getInventoryContainerRemovalBatchSize());
    for (List<InventoryContainerAdjustmentPayload> adjustmentPayload :
        batchedInventoryAdjustmentPayload) {
      InventoryAdjustmentList adjustmentList =
          InventoryAdjustmentList.builder().adjustments(adjustmentPayload).build();

      String responsePayload = inventoryService.performInventoryBulkAdjustment(adjustmentList);
      LOGGER.info("Removed unused container from inventory . Response = {}", responsePayload);
    }
  }

  private Integer getAdjustedQuantity(Integer quantity, Map<String, String> containerItemMisc) {
    if (Objects.isNull(containerItemMisc)) {
      return quantity;
    }

    Integer decantedQty =
        Integer.valueOf(containerItemMisc.getOrDefault(QuantityType.DECANTED.toString(), "0"));
    Integer damageQty =
        Integer.valueOf(containerItemMisc.getOrDefault(QuantityType.DAMAGE.toString(), "0"));
    Integer rejectQty =
        Integer.valueOf(containerItemMisc.getOrDefault(QuantityType.REJECTED.toString(), "0"))
            + Integer.valueOf(
                containerItemMisc.getOrDefault(QuantityType.COLD_CHAIN_REJECT.toString(), "0"))
            + Integer.valueOf(
                containerItemMisc.getOrDefault(QuantityType.NOTMFCASSORTMENT.toString(), "0"))
            + Integer.valueOf(
                containerItemMisc.getOrDefault(QuantityType.FRESHNESSEXPIRATION.toString(), "0"))
            + Integer.valueOf(
                containerItemMisc.getOrDefault(QuantityType.MFCOVERSIZE.toString(), "0"))
            + Integer.valueOf(
                containerItemMisc.getOrDefault(QuantityType.MFC_TO_STORE_TRANSFER.toString(), "0"));
    if ((decantedQty + damageQty + rejectQty) < quantity) {
      return quantity - (decantedQty + damageQty + rejectQty);
    }
    return quantity;
  }

  @Timed(
      name = "autoMFCExceptionReceiptCreationTimed",
      level1 = "uwms-receiving-api",
      level2 = "mfcReceiptService")
  @ExceptionCounted(
      name = "autoMFCExceptionReceiptCreationExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "mfcReceiptService")
  public void initiateAutoException(
      HawkeyeAdjustment hawkeyeAdjustment, DeliveryMetaData deliveryMetaData) {

    if (Objects.isNull(deliveryMetaData)) {
      deliveryMetaData = retrieveDelivery();
    }

    LOGGER.info(
        "Initiating exception container creation flow for hawkeyeAdjustment={} and deliveryNumber={}",
        hawkeyeAdjustment,
        deliveryMetaData.getDeliveryNumber());

    String flow = AddContainerEvent.ADD_PACK_ITEM.getEventType();

    StringBuilder dummyInvoiceNumber =
        new StringBuilder(deliveryMetaData.getDeliveryNumber()).append(DUMMY_INVOICE_SUFFIX);

    int invoiceLineNumber = 1;

    Optional<ContainerItem> retrievedContainerItem =
        containerPersisterService.findByInvoiceNumber(dummyInvoiceNumber.toString());

    Container container = null;

    if (retrievedContainerItem.isPresent()) {
      try {
        invoiceLineNumber = retrievedContainerItem.get().getInvoiceLineNumber() + 1;
        container =
            containerService.getContainerByTrackingId(retrievedContainerItem.get().getTrackingId());
      } catch (ReceivingException e) {
        LOGGER.error("unable to retrieve container information. ", e);
      }
    }

    if (Objects.isNull(container)) {
      container = createContainer(deliveryMetaData);
      flow = AddContainerEvent.ADD_PALLET.getEventType();
    }

    ContainerItem containerItem =
        createContainerItem(
            container, hawkeyeAdjustment, dummyInvoiceNumber.toString(), invoiceLineNumber);
    MFCUtils.populatePackNumber(container, containerItem);
    container.getContainerItems().add(containerItem);

    // Saved Exception container
    containerPersisterService.saveContainer(container);
    LOGGER.info(
        "Created exception container successfully with trackingId={}", container.getTrackingId());

    CommonReceiptDTO receiptDTO = hawkeyeReceiptTransformer.transform(hawkeyeAdjustment);
    LOGGER.info("Going to create receipt for {}", receiptDTO);
    Receipt receipt = mfcReceiptService.createReceipt(container, containerItem, receiptDTO);
    mfcReceiptService.saveReceipt(Collections.singletonList(receipt));
    publishWorkingIfApplicable(deliveryMetaData);
    deliveryService.publishNewInvoice(container, Arrays.asList(containerItem), flow, null);
  }

  private Container createContainer(DeliveryMetaData deliveryMetaData) {
    Container container = new Container();
    // Pallet Number is the trackingId
    String trackingId = "EXCEPTION-" + generateTCL(LabelType.OTHERS);
    container.setTrackingId(trackingId);
    container.setSsccNumber(trackingId);
    container.setDeliveryNumber(Long.valueOf(deliveryMetaData.getDeliveryNumber()));
    container.setContainerStatus(ReceivingConstants.RECEIVED);
    container.setInventoryStatus(MFCConstant.AVAILABLE);
    container.setContainerType(MFCConstant.PALLET);

    // Defaulting it as it is not relevant in MFC
    container.setWeight(0.0f);
    container.setWeightUOM("LB");
    container.setCube(0.0f);
    container.setCubeUOM("CF");

    container.setCtrShippable(false);
    container.setCtrReusable(false);
    String user = ReceivingConstants.DEFAULT_USER;
    if (Objects.nonNull(TenantContext.getAdditionalParams())
        && Objects.nonNull(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY))) {
      user =
          String.valueOf(
              TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    container.setCreateUser(user);
    container.setLastChangedUser(user);

    Date date = new Date();
    container.setCreateTs(date);
    container.setCompleteTs(date);
    container.setPublishTs(date);
    container.setMessageId(UUID.randomUUID().toString());

    // MFC Specific
    Container randomContainer =
        containerService
            .findByDeliveryNumberAndShipmentIdNotNull(
                Long.valueOf(deliveryMetaData.getDeliveryNumber()))
            .stream()
            .findAny()
            .get();
    container.setShipmentId(randomContainer.getShipmentId());
    container.setContainerItems(new ArrayList<>());
    Map<String, Object> containerMiscInfo =
        Objects.nonNull(container.getContainerMiscInfo())
            ? container.getContainerMiscInfo()
            : new HashMap<>();
    containerMiscInfo.putIfAbsent(OPERATION_TYPE, OperationType.OVERAGE);
    containerMiscInfo.put(PALLET_TYPE, PalletType.MFC);
    container.setLocation(PalletType.MFC.name());
    Map<String, Object> existingContainerMiscInfo = randomContainer.getContainerMiscInfo();
    containerMiscInfo.put(BANNER_CODE, existingContainerMiscInfo.get(BANNER_CODE));
    containerMiscInfo.put(BANNER_DESCRIPTION, existingContainerMiscInfo.get(BANNER_DESCRIPTION));
    containerMiscInfo.put(TIMEZONE_CODE, existingContainerMiscInfo.get(TIMEZONE_CODE));
    container.setContainerMiscInfo(containerMiscInfo);
    LOGGER.info("Container misc is set as {}", containerMiscInfo);
    return container;
  }

  public String generateTCL(LabelType type) {
    ReceivingCounter receivingCounter = receivingCounterService.counterUpdation(1, type);

    long endIndex = receivingCounter.getCounterNumber() % 100000000;
    Set<String> tcls = new HashSet<>();
    for (long tclNumber = (endIndex - 1 + 1); tclNumber <= endIndex; tclNumber++) {
      tcls.add(receivingCounter.getPrefix() + prependZero(String.valueOf(tclNumber), 8));
    }
    return tcls.stream().findFirst().get();
  }

  private String prependZero(String number, int i) {
    if (number.length() == i) {
      return number;
    }
    while (i - number.length() > 0) {
      number = "0" + number;
    }
    return number;
  }

  private DeliveryMetaData retrieveDelivery() {
    Pageable pageable = PageRequest.of(0, 1, Sort.by("lastUpdatedDate").ascending());
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataService.findByDeliveryStatus(DeliveryStatus.WRK, pageable);
    if (deliveryMetaDataList.size() > 0) {
      return deliveryMetaDataList.get(0);
    }

    deliveryMetaDataList =
        deliveryMetaDataService.findByDeliveryStatus(DeliveryStatus.ARV, pageable);
    if (deliveryMetaDataList.size() > 0) {
      return deliveryMetaDataList.get(0);
    }

    deliveryMetaDataList =
        deliveryMetaDataService.findByDeliveryStatus(DeliveryStatus.SCH, pageable);
    if (deliveryMetaDataList.size() > 0) {
      return deliveryMetaDataList.get(0);
    }
    throw new ReceivingInternalException(
        ExceptionCodes.UNABLE_TO_CREATE_CONTAINER, "Unable to retrieve proper delivery ");
  }

  private ContainerItem createContainerItem(
      Container container,
      HawkeyeAdjustment hawkeyeAdjustment,
      String invoiceNumber,
      int invoiceLineNumber) {

    DecantItem decantItem =
        hawkeyeAdjustment
            .getItems()
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ReceivingInternalException(
                        ExceptionCodes.UNABLE_TO_CREATE_CONTAINER, "Invalid Hawkeye Message"));
    StockStateExchange stockStateExchange =
        decantItem
            .getStockStateChange()
            .stream()
            .findAny()
            .orElseThrow(
                () ->
                    new ReceivingInternalException(
                        ExceptionCodes.UNABLE_TO_CREATE_CONTAINER,
                        "Unable to retrieve Decanted message"));

    ItemInfos itemInfos = decantService.retrieveItem(decantItem.getGtin());

    ContainerItem containerItem =
        createContainerItem(
            container.getTrackingId(),
            invoiceNumber,
            invoiceLineNumber,
            decantItem.getGtin(),
            stockStateExchange.getQuantity(),
            itemInfos);

    // Invoking for decanting event
    MFCUtils.populateContainerItemOrderFilledQty(
        containerItem,
        QuantityType.getQuantityType(stockStateExchange.getReasonCode()),
        Long.valueOf(stockStateExchange.getQuantity()),
        containerItem.getQuantityUOM());

    return containerItem;
  }

  private ContainerItem createContainerItem(
      String trackingId,
      String invoiceNumber,
      int invoiceLineNumber,
      String gtin,
      Integer quantity,
      ItemInfos itemInfos) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(itemInfos.getItemNumber());
    containerItem.setQuantity(0);
    containerItem.setQuantityUOM(getQuantityUom(itemInfos));
    containerItem.setGtin(gtin);
    containerItem.setItemUPC(itemInfos.getConsumableGtin());
    containerItem.setCaseUPC(itemInfos.getOrderableGtin());
    containerItem.setInboundChannelMethod(MFCConstant.STAPLESTOCK);
    containerItem.setOutboundChannelMethod(MFCConstant.STAPLESTOCK);
    containerItem.setTrackingId(trackingId);
    containerItem.setBaseDivisionCode(itemInfos.getBaseDivisionCode());
    containerItem.setFinancialReportingGroupCode(itemInfos.getFinancialReportingGroupCode());
    containerItem.setVnpkQty(itemInfos.getVnpkQty());
    containerItem.setWhpkQty(itemInfos.getWhpkQty());
    containerItem.setWhpkSell(itemInfos.getWhpkSell());

    containerItem.setDeptNumber(itemInfos.getDeptNumber());
    containerItem.setVendorNumber(itemInfos.getVendorNumber());

    // Total Reported Qty
    containerItem.setTotalPurchaseReferenceQty(quantity);

    containerItem.setVnpkWgtQty(itemInfos.getVnpkWgtQty());
    containerItem.setVnpkWgtUom(itemInfos.getVnpkWgtUom());

    containerItem.setVnpkcbqty(itemInfos.getVnpkcbqty());
    containerItem.setVnpkcbuomcd(itemInfos.getVnpkcbuomcd());

    List<String> itemDescriptions = itemInfos.getDescriptions();
    if (!org.springframework.util.CollectionUtils.isEmpty(itemDescriptions)) {
      containerItem.setDescription(itemInfos.getDescriptions().get(0));
      /*
       * Item descriptions may contain more than 2 elements.
       * In that case also we will send 2 descriptions only
       * as per the contract.
       */
      if (itemDescriptions.size() > 1) {
        containerItem.setSecondaryDescription(itemInfos.getDescriptions().get(1));
      }
    }

    containerItem.setOrderFilledQty(0L);
    containerItem.setOrderFilledQtyUom(getQuantityUom(itemInfos));

    containerItem.setInvoiceNumber(invoiceNumber);
    containerItem.setInvoiceLineNumber(invoiceLineNumber);
    containerItem.setPluNumber(itemInfos.getPluNumber());
    containerItem.setPurchaseReferenceNumber(DUMMY_PURCHASE_REF_NUMBER);
    containerItem.setPurchaseReferenceLineNumber(invoiceLineNumber);
    if (Objects.nonNull(itemInfos.getCid())) {
      containerItem.setCid(itemInfos.getCid());
    }
    containerItem.setDeptCatNbr(itemInfos.getDeptCategory());
    containerItem.setHybridStorageFlag(itemInfos.getHybridStorageFlag());
    containerItem.setDeptSubcatgNbr(itemInfos.getDeptSubcatgNbr());
    return containerItem;
  }

  // ProcessAddSKU for manual MFC
  public boolean addSkuIfRequired(MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO) {
    if (Objects.nonNull(mfcInventoryAdjustmentDTO.getEventObject())
        && Objects.nonNull(mfcInventoryAdjustmentDTO.getEventObject().getItemList())
        && mfcInventoryAdjustmentDTO.getEventObject().getItemList().isEmpty()) {
      LOGGER.info("Request is for inventory adjustment and hence, skipping addSku workflow ");
      return Boolean.FALSE;
    }

    EventObject eventObject = mfcInventoryAdjustmentDTO.getEventObject();

    Container container =
        containerPersisterService.findBySSCCAndDeliveryNumber(
            eventObject.getTrackingId(), Long.valueOf(eventObject.getDeliveryNumber()));

    if (Objects.isNull(container)) {
      LOGGER.info("Container is not exists in receiving and hence, skipping addSku workflow ");
      return Boolean.FALSE;
    }

    Set<Long> dbItems =
        container
            .getContainerItems()
            .stream()
            .map(ci -> ci.getItemNumber())
            .collect(Collectors.toSet());

    Optional<Long> selectedItemNumber =
        eventObject
            .getItemList()
            .stream()
            .filter(invItem -> Objects.nonNull(invItem.getAdjustmentTO()))
            .map(item -> Long.valueOf(item.getItemNumber()))
            .filter(item -> !dbItems.contains(item))
            .findFirst();

    if (!selectedItemNumber.isPresent()) {
      LOGGER.info("None of the item is new in inventory and hence, skipping addSku workflow ");
      return Boolean.FALSE;
    }

    Long itemNumber = selectedItemNumber.get();

    Optional<ItemListItem> optionalSelectedItem =
        eventObject
            .getItemList()
            .stream()
            .filter(
                item ->
                    Objects.nonNull(item.getItemNumber())
                        && item.getItemNumber().intValue() == itemNumber.intValue())
            .findFirst();

    if (!optionalSelectedItem.isPresent()) {
      LOGGER.info("Unable to select item for adding the sku and hence, skipping addSku workflow ");
      return Boolean.FALSE;
    }

    ItemListItem selectedItem = optionalSelectedItem.get();
    if (PalletType.STORE.equalsType(mfcInventoryAdjustmentDTO.getEventObject().getLocationName())
        && StringUtils.isBlank(selectedItem.getInvoiceNumber())) {
      LOGGER.error(
          "Invoice number is null for item {} , tracking ID {} and delivery number {} in add sku.",
          selectedItem.getItemNumber(),
          eventObject.getTrackingId(),
          eventObject.getDeliveryNumber());
      selectedItem.setInvoiceNumber(
          CollectionUtils.isNotEmpty(container.getContainerItems())
              ? container.getContainerItems().get(0).getInvoiceNumber()
              : new StringBuilder()
                  .append(container.getDeliveryNumber())
                  .append(DUMMY_INVOICE_SUFFIX)
                  .toString());
    }

    AdjustmentTO adjustmentTO = selectedItem.getAdjustmentTO();

    if (Objects.nonNull(adjustmentTO)
        && Objects.nonNull(adjustmentTO.getReasonCode())
        && (adjustmentTO.getReasonCode().intValue()
            != QuantityType.OVERAGE.getInventoryErrorReason().intValue())) {
      LOGGER.info("Error Reason code is not overage and hence, skipping addSku workflow ");
      return Boolean.FALSE;
    }

    LOGGER.info(
        "Going to publish working if applicable for deliveryNumber= {} ",
        eventObject.getDeliveryNumber());
    publishWorkingIfApplicable(
        deliveryMetaDataService
            .findByDeliveryNumber(eventObject.getDeliveryNumber())
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                        String.format(
                            "Unable to find delivery metadata with deliveryNumber=%s",
                            eventObject.getDeliveryNumber()))));

    ItemInfos itemInfos =
        ItemInfos.builder()
            .itemNumber(Long.valueOf(selectedItem.getItemNumber()))
            .baseDivisionCode(selectedItem.getBaseDivisionCode())
            .orderableGtin(selectedItem.getCaseUPC())
            .consumableGtin(selectedItem.getItemUPC())
            .deptNumber(
                Objects.isNull(selectedItem.getDeptNumber()) ? -1 : selectedItem.getDeptNumber())
            .descriptions(Arrays.asList(selectedItem.getDescription()))
            .financialReportingGroupCode(selectedItem.getFinancialReportingGroup())
            .vendorNumber(0)
            .vnpkcbqty(0.0f)
            .vnpkcbuomcd("CF")
            .vnpkQty(selectedItem.getVendorPkRatio())
            .whpkQty(selectedItem.getWarehousePkRatio())
            .whpkSell(
                Objects.isNull(selectedItem.getWhsePackSell())
                    ? 0
                    : selectedItem.getWhsePackSell().getValue())
            .vnpkWgtQty(
                Objects.isNull(selectedItem.getTotalItemWeight())
                    ? 0
                    : selectedItem.getTotalItemWeight().floatValue())
            .vnpkWgtUom(selectedItem.getTotalItemWeightUOM())
            .pluNumber(selectedItem.getPluNumber())
            .cid(selectedItem.getCid())
            .deptCategory(selectedItem.getDeptCatNbr())
            .quantityUom(getQuantityUom(adjustmentTO))
            .hybridStorageFlag(selectedItem.getHybridStorageFlag())
            .deptSubcatgNbr(selectedItem.getDeptSubcatgNbr())
            .build();

    int latestInvoiceNumber = MFCUtils.generateInvoiceLine(container);
    LOGGER.info(
        "Adding item to invoiceLineNumber = {}",
        (defaultInvoiceLineStartValue + latestInvoiceNumber));
    ContainerItem containerItem =
        createContainerItem(
            container.getTrackingId(),
            selectedItem.getInvoiceNumber(),
            defaultInvoiceLineStartValue + latestInvoiceNumber,
            selectedItem.getItemUPC(),
            adjustmentTO.getValue(),
            itemInfos);

    MFCUtils.populatePackNumber(container, containerItem);
    container.getContainerItems().add(containerItem);

    containerPersisterService.saveContainer(container);
    LOGGER.info(
        "Added the item to receiving system successfully. itemNumber={} and invoiceNumber={}",
        itemInfos.getItemNumber(),
        selectedItem.getInvoiceNumber());

    addContainerToGDM(
        container,
        Arrays.asList(containerItem),
        Optional.empty(),
        AddContainerEvent.ADD_PACK_ITEM.getEventType());

    return Boolean.TRUE;
  }

  private int getDeptNbr(ItemListItem selectedItem) {
    return Objects.nonNull(selectedItem.getDeptNumber()) ? selectedItem.getDeptNumber() : -1;
  }

  private String getQuantityUom(AdjustmentTO adjustmentTO) {
    if (Objects.nonNull(adjustmentTO)) {
      return EACHES.equalsIgnoreCase(adjustmentTO.getUom()) ? UOM_EA : adjustmentTO.getUom();
    }
    return null;
  }

  private String getQuantityUom(ItemInfos itemInfos) {
    return StringUtils.isNotEmpty(itemInfos.getQuantityUom())
        ? itemInfos.getQuantityUom()
        : MFCConstant.UOM_EA;
  }

  private void addContainerToGDM(
      Container container,
      List<ContainerItem> containerItems,
      Optional<List<Pack>> optionalPacks,
      String eventType) {

    Map<String, String> replenishmentCodeMap = new HashMap<>();

    if (optionalPacks.isPresent()) {
      List<Pack> packs = optionalPacks.get();
      packs
          .stream()
          .forEach(
              pk ->
                  pk.getItems()
                      .stream()
                      .forEach(
                          item -> {
                            replenishmentCodeMap.put(item.getGtin(), item.getReplenishmentCode());
                          }));
      LOGGER.info("Replenishment map = {}", replenishmentCodeMap);
    }

    BiParameterConsumer<
            Container,
            com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack,
            com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack>
        packEnricher =
            (Container _container,
                com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack pack) -> {
              pack.setPalletNumber(_container.getSsccNumber());
              String packNumber =
                  Objects.isNull(pack.getPackNumber())
                      ? String.valueOf(System.currentTimeMillis())
                      : pack.getPackNumber();

              pack.setPackNumber(packNumber);

              List<com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item> items =
                  pack.getItems();

              if (optionalPacks.isPresent()) {
                items.forEach(
                    _item -> {
                      _item.setReplenishmentCode(replenishmentCodeMap.get(_item.getGtin()));
                    });
              }

              LOGGER.info("Enrich pack information = {} ", gson.toJson(pack));

              return pack;
            };

    deliveryService.publishNewInvoice(container, containerItems, eventType, packEnricher);
  }

  public Container createContainer(
      Container existingContainer,
      ASNDocument asnDocument,
      Pack pack,
      Map<Long, ItemDetails> itemMap,
      BiFunction<Item, ItemDetails, Boolean> eligibleChecker,
      OverageType overageType,
      Map<String, PalletInfo> palletInfoMap) {
    // TODO needs to be optimized
    Container container = retrieveContainer(existingContainer, asnDocument, pack);
    Map<Pair<String, Integer>, Object> existingContainerItems = new HashMap<>();
    if (Objects.nonNull(container) && CollectionUtils.isNotEmpty(container.getContainerItems())) {
      container
          .getContainerItems()
          .forEach(
              containerItem ->
                  existingContainerItems.put(
                      new Pair<>(
                          containerItem.getInvoiceNumber(), containerItem.getInvoiceLineNumber()),
                      Boolean.TRUE));
    } else {
      container = createContainer(asnDocument, pack, palletInfoMap);
    }
    populateContainerMiscInfo(asnDocument, container, overageType, palletInfoMap);

    List<ContainerItem> containerItems = new ArrayList<>();

    for (Item item : pack.getItems()) {
      ItemDetails itemDetails = itemMap.get(item.getItemNumber());
      if (existingContainerItems.containsKey(
          new Pair<>(
              item.getInvoice().getInvoiceNumber(), item.getInvoice().getInvoiceLineNumber()))) {
        LOGGER.error(
            "Container Item already exists for invoiceNumber {} invoiceLineNumber {}.Hence error.",
            item.getInvoice().getInvoiceNumber(),
            item.getInvoice().getInvoiceLineNumber());
        asyncPersister.publishMetric(
            "duplicate_container_item", "uwms-receiving", "store-mfc", "store-mfc_processPack");
        continue;
      }
      ContainerItem containerItem = createPackItem(pack, item, itemDetails, eligibleChecker);
      if (Objects.nonNull(containerItem)) {
        containerItem.setTrackingId(container.getTrackingId());
        updateChannelTypeCode(container, containerItem);
        if (MFCConstant.SHIPPER.equals(item.getAssortmentType())
            && CollectionUtils.isNotEmpty(item.getChildItems())) {
          containerItems.addAll(createChildItems(containerItem, item.getChildItems()));
        } else {
          containerItems.add(containerItem);
        }
        existingContainerItems.put(
            new Pair<>(containerItem.getInvoiceNumber(), containerItem.getInvoiceLineNumber()),
            Boolean.TRUE);
      }
    }

    if (Objects.isNull(container.getContainerItems())) {
      container.setContainerItems(containerItems);
    } else {
      container.getContainerItems().addAll(containerItems);
    }
    if (container.getContainerItems().isEmpty()) {
      LOGGER.warn(
          "No eligible containerItem for containerId={} and hence, ignoring it ",
          container.getTrackingId());
      return null;
    }

    return container;
  }

  private void updateChannelTypeCode(Container container, ContainerItem containerItem) {
    if (MapUtils.isNotEmpty(container.getContainerMiscInfo())
        && container.getContainerMiscInfo().containsKey(CHANNEL_TYPE)) {
      String channelType = (String) container.getContainerMiscInfo().get(CHANNEL_TYPE);
      containerItem.setInboundChannelMethod(channelType);
      containerItem.setOutboundChannelMethod(channelType);
    }
  }

  public List<ContainerItem> createChildItems(ContainerItem containerItem, List<Item> childItems) {
    List<ContainerItem> childContainerItemList = new ArrayList<>();
    String packNumber = containerItem.getContainerItemMiscInfo().get(PACK_NUMBER);
    String eventType = containerItem.getContainerItemMiscInfo().get(EVENT_TYPE);
    String parentGtin = containerItem.getGtin();
    childItems.forEach(
        childItem -> {
          ContainerItem childContainerItem =
              gson.fromJson(gson.toJson(containerItem), ContainerItem.class);
          childContainerItem.setItemNumber(childItem.getItemNumber());
          childContainerItem.setGtin(childItem.getGtin());
          childContainerItem.setItemUPC(childItem.getGtin());
          // setting parent gtin as case upc to handle pdq scenario
          childContainerItem.setCaseUPC(parentGtin);
          childContainerItem.setPurchaseReferenceLineNumber(
              childItem.getInvoice().getInvoiceLineNumber());
          childContainerItem.setQuantity(
              childItem.getInventoryDetail().getDerivedQuantity().intValue());
          childContainerItem.setQuantityUOM(childItem.getInventoryDetail().getDerivedUom());
          childContainerItem.setInvoiceNumber(childItem.getInvoice().getInvoiceNumber());
          childContainerItem.setInvoiceLineNumber(childItem.getInvoice().getInvoiceLineNumber());
          childContainerItem.setCid(childItem.getReplenishmentGroupNumber());
          Map<String, String> containerItemMisc = new HashMap<>();
          containerItemMisc.put(PACK_NUMBER, packNumber);
          containerItemMisc.put(EVENT_TYPE, eventType);
          childContainerItem.setContainerItemMiscInfo(containerItemMisc);
          childContainerItemList.add(childContainerItem);
        });
    return childContainerItemList;
  }

  protected Container retrieveContainer(
      Container existingContainer, ASNDocument asnDocument, Pack pack) {
    if (Objects.nonNull(existingContainer)) {
      return existingContainer;
    }
    try {
      return containerPersisterService.findBySSCCAndDeliveryNumber(
          pack.getPalletNumber(), asnDocument.getDelivery().getDeliveryNumber());
    } catch (Exception e) {
      LOGGER.warn(
          "No Container found and hence, going for container creation flow. {}",
          e.getClass() + " : " + e.getMessage());
      return null;
    }
  }

  public Container createContainer(
      ASNDocument asnDocument, Pack pack, Map<String, PalletInfo> palletInfoMap) {
    Container container = new Container();
    // Pallet Number is the trackingId
    container.setTrackingId(generateTCL(LabelType.OTHERS));
    container.setSsccNumber(MFCUtils.getPackId(pack));
    if (Objects.nonNull(pack.getPalletNumber())) {
      container.setContainerType(MFCConstant.PALLET);
    } else {
      container.setContainerType(MFCConstant.CASE);
    }
    container.setDeliveryNumber(asnDocument.getDelivery().getDeliveryNumber());
    container.setContainerStatus(ReceivingConstants.RECEIVED);
    container.setInventoryStatus(populateInventoryStatus(pack));
    container.setLocation(
        MFCUtils.getPalletTypeFromPalletInfoMap(palletInfoMap, MFCUtils.getPackId(pack)));

    // Defaulting it as it is not relevant in MFC
    container.setWeight(0.0f);
    container.setWeightUOM("LB");
    container.setCube(0.0f);
    container.setCubeUOM("CF");

    container.setCtrShippable(false);
    container.setCtrReusable(false);
    String user = ReceivingConstants.DEFAULT_USER;
    if (Objects.nonNull(TenantContext.getAdditionalParams())
        && Objects.nonNull(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY))) {
      user =
          String.valueOf(
              TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    container.setCreateUser(user);
    container.setLastChangedUser(user);

    Date date = new Date();
    container.setCreateTs(date);
    container.setCompleteTs(date);
    container.setPublishTs(date);
    container.setMessageId(UUID.randomUUID().toString());

    // MFC Specific
    container.setShipmentId(asnDocument.getShipment().getDocumentId());
    container.setEligibility(
        MFCUtils.getEligibilityFromPalletInfoMap(palletInfoMap, MFCUtils.getPackId(pack)));
    return container;
  }

  public void populateContainerMiscInfo(
      ASNDocument asnDocument, Container container, Map<String, PalletInfo> palletInfoMap) {
    populateContainerMiscInfo(asnDocument, container, null, palletInfoMap);
  }

  public void populateContainerMiscInfo(
      ASNDocument asnDocument,
      Container container,
      OverageType overageType,
      Map<String, PalletInfo> palletInfoMap) {
    Map<String, Object> containerMiscInfo =
        Objects.nonNull(container.getContainerMiscInfo())
            ? container.getContainerMiscInfo()
            : new HashMap<>();
    containerMiscInfo.putIfAbsent(OPERATION_TYPE, MFCUtils.getOperationType(asnDocument));
    if (asnDocument.isOverage()) {
      containerMiscInfo.putIfAbsent(RECEIVED_OVG_TYPE, getOverageType(overageType));
    }
    containerMiscInfo.putIfAbsent(
        PALLET_TYPE,
        MFCUtils.getPalletTypeFromPalletInfoMap(palletInfoMap, container.getSsccNumber()));
    if (Objects.nonNull(asnDocument.getShipment().getAdditionalInfo())) {
      containerMiscInfo.putIfAbsent(
          BANNER_CODE, asnDocument.getShipment().getAdditionalInfo().getBannerCode());
      containerMiscInfo.putIfAbsent(
          BANNER_DESCRIPTION, asnDocument.getShipment().getAdditionalInfo().getBannerDescription());
      containerMiscInfo.putIfAbsent(
          TIMEZONE_CODE, asnDocument.getShipment().getAdditionalInfo().getTimeZoneCode());
    }
    if (Objects.isNull(containerMiscInfo.get(DEST_TRACKING_ID))) {
      containerMiscInfo.put(DEST_TRACKING_ID, generateTCL(LabelType.TPL));
    }
    containerMiscInfo.putIfAbsent(ORIGIN_DC_NUM, asnDocument.getShipment().getSource().getNumber());
    containerMiscInfo.putIfAbsent(
        ORIGIN_COUNTRY_CODE, asnDocument.getShipment().getSource().getCountryCode());
    containerMiscInfo.putIfAbsent(SOURCE_TYPE, asnDocument.getShipment().getSource().getType());
    containerMiscInfo.putIfAbsent(
        CHANNEL_TYPE,
        ChannelType.getChannelType(
                asnDocument.getShipment().getSource().getType(),
                asnDocument.getShipment().getDocumentType())
            .name());
    container.setContainerMiscInfo(containerMiscInfo);
    populateContainerAdditionalInfo(asnDocument.getDelivery(), container);
  }

  public void populateContainerAdditionalInfo(Delivery delivery, Container container) {
    Map<String, Object> containerAdditionalInfo =
        Objects.nonNull(container.getAdditionalInformation())
            ? container.getAdditionalInformation()
            : new HashMap<>();
    containerAdditionalInfo.putIfAbsent(ARRIVAL_TS, delivery.getArrivalTimeStamp());
    containerAdditionalInfo.putIfAbsent(
        RECEIVING_COMPLETE_TS, delivery.getReceivingCompletedTimeStamp());
    container.setAdditionalInformation(containerAdditionalInfo);
  }

  protected String populateInventoryStatus(Pack pack) {
    List<Container> containers =
        containerPersisterService.findBySSCCAndInventoryStatus(
            pack.getPalletNumber(), MFCConstant.AVAILABLE);
    String inventoryStatus =
        Objects.nonNull(containers) && containers.size() == 1
            ? MFCConstant.YET_TO_PUBLISH
            : MFCConstant.AVAILABLE;
    LOGGER.info(
        "Inventory status for packNumber={} is {}", pack.getPalletNumber(), inventoryStatus);
    return inventoryStatus;
  }

  public ContainerItem createPackItem(
      Pack pack,
      Item item,
      ItemDetails itemDetails,
      BiFunction<Item, ItemDetails, Boolean> eligibleChecker) {

    if (Objects.nonNull(eligibleChecker)
        && !BooleanUtils.toBooleanDefaultIfNull(eligibleChecker.apply(item, itemDetails), true)) {
      LOGGER.info(
          "Pallet={} pack={} packItem={} is not eligible for MFC and hence ignoring it",
          pack.getPalletNumber(),
          pack.getPackNumber(),
          item.getItemNumber());
      return null;
    }

    // MFC enable Item
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(item.getItemNumber());
    if (tenantSpecificConfigReader
        .getScalingQtyEnabledForReplenishmentTypes()
        .contains(item.getReplenishmentCode())) {
      org.springframework.data.util.Pair<Integer, String> scaledQuantity =
          getScaledQuantity(
              item.getInventoryDetail().getReportedQuantity(),
              item.getInventoryDetail().getReportedUom(),
              appConfig.getUomScalingPrefix(),
              appConfig.getScalableUomList());
      containerItem.setQuantityUOM(scaledQuantity.getSecond());
      containerItem.setQuantity(scaledQuantity.getFirst());

      // MFC Specific
      // Need to be persisted as a part of fulfillment
      //
      containerItem.setOrderFilledQty(0L);
      containerItem.setOrderFilledQtyUom(scaledQuantity.getSecond());
    } else {
      // Rounding qty to nearest integer for non-standard UOMs. 12.45 LB -> 12 EA, 4.78 LB->5 EA
      // Doing this to avoid downstream flows being affected by scaled qty like 1245 centi-LB
      containerItem.setQuantityUOM(EA);
      containerItem.setQuantity((int) Math.ceil(item.getInventoryDetail().getReportedQuantity()));

      // MFC Specific
      // Need to be persisted as a part of fulfillment
      //
      containerItem.setOrderFilledQty(0L);
      containerItem.setOrderFilledQtyUom(EA);
    }
    containerItem.setGtin(item.getGtin());
    containerItem.setInboundChannelMethod(MFCConstant.STAPLESTOCK);
    containerItem.setOutboundChannelMethod(MFCConstant.STAPLESTOCK);
    containerItem.setTrackingId(MFCUtils.getPackId(pack));
    containerItem.setDeptNumber(Integer.valueOf(item.getItemDepartment()));
    containerItem.setHybridStorageFlag(item.getHybridStorageFlag());

    if (Objects.isNull(item.getVendorId())) {
      populateDefaultVendor(containerItem);
    } else {
      populateVendor(containerItem, Integer.valueOf(item.getVendorId()));
    }

    // Total Reported Qty
    containerItem.setTotalPurchaseReferenceQty(
        item.getInventoryDetail().getReportedQuantity().intValue());
    //    containerItem.setPurchaseCompanyId(purchaseOrder.getPurchaseCompanyId());

    containerItem.setDescription(item.getItemDescription());

    containerItem.setInvoiceNumber(item.getInvoice().getInvoiceNumber());
    containerItem.setInvoiceLineNumber(item.getInvoice().getInvoiceLineNumber());

    // setting purchase order for sct
    containerItem.setPurchaseReferenceNumber(
        Objects.nonNull(item.getPurchaseOrder())
                && Objects.nonNull(item.getPurchaseOrder().getPoNumber())
            ? item.getPurchaseOrder().getPoNumber()
            : DUMMY_PURCHASE_REF_NUMBER);
    containerItem.setPurchaseReferenceLineNumber(item.getInvoice().getInvoiceLineNumber());

    Map<String, String> containerItemMisc = new HashMap<>();
    containerItemMisc.put(MFCConstant.PACK_NUMBER, pack.getPackNumber());
    containerItem.setContainerItemMiscInfo(containerItemMisc);

    populateDefaultLineWeight(containerItem);
    populateDefaultLineCube(containerItem);
    populateContainerItemAdditionalInfo(item, containerItem);

    if (Objects.isNull(itemDetails)) {
      // TODO Raise an alert
      LOGGER.warn(
          "ItemDetails are not present in item section for item number {}", item.getItemNumber());
      containerItem.setItemUPC(item.getGtin());
      containerItem.setBaseDivisionCode(BASE_DIVISION_CODE);
      containerItem.setFinancialReportingGroupCode(FINANCIAL_REPORTING_GROUP_CODE);
      containerItem.setVnpkQty(item.getInventoryDetail().getVendorCaseQuantity());
      containerItem.setWhpkQty(item.getInventoryDetail().getWarehouseCaseQuantity());
      if (Objects.nonNull(item.getFinancialDetail())) {
        containerItem.setWhpkSell(item.getFinancialDetail().getDerivedCost());
      }
      if (Objects.nonNull(item.getReplenishmentGroupNumber())) {
        containerItem.setCid(item.getReplenishmentGroupNumber());
      }

    } else {
      containerItem.setItemUPC(itemDetails.getConsumableGTIN());
      containerItem.setCaseUPC(itemDetails.getOrderableGTIN());
      containerItem.setBaseDivisionCode(
          (String) itemDetails.getItemAdditonalInformation().get("baseDivisionCode"));
      containerItem.setFinancialReportingGroupCode(
          (String) itemDetails.getItemAdditonalInformation().get("financialReportingGroupCode"));
      containerItem.setVnpkQty(retrieveVnpkQty(item, itemDetails));
      containerItem.setWhpkQty(retrieveWhpkQty(item, itemDetails));
      containerItem.setWhpkSell(retrieveWhpkSell(item, itemDetails));
      containerItem.setWarehouseAreaCode(retrieveWarehouseAreaCode(itemDetails));
      populateDeptCategory(itemDetails, containerItem);
      populateDeptSubCategory(itemDetails, containerItem);
      populateItemTypeCode(itemDetails, containerItem);
      populateReplenishmentCode(itemDetails, containerItem);
      if (Objects.nonNull(item.getReplenishmentGroupNumber())) {
        containerItem.setCid(item.getReplenishmentGroupNumber());
      } else if (Objects.nonNull(itemDetails.getItemAdditonalInformation())
          && Objects.nonNull(
              itemDetails
                  .getItemAdditonalInformation()
                  .get(MFCConstant.REPLENISHMENT_GROUP_NUMBER))) {
        containerItem.setCid(
            itemDetails
                .getItemAdditonalInformation()
                .get(MFCConstant.REPLENISHMENT_GROUP_NUMBER)
                .toString());
      }

      containerItemMisc.put(
          EVENT_TYPE,
          (Objects.nonNull(item.getAdditionalInfo())
                  && Objects.nonNull(item.getAdditionalInfo().getEventType())
              ? String.valueOf(item.getAdditionalInfo().getEventType())
              : null));
      List<String> itemDescriptions = itemDetails.getDescriptions();

      if (!org.springframework.util.CollectionUtils.isEmpty(itemDescriptions)) {
        /*
         * Item descriptions may contain more than 2 elements.
         * In that case also we will send 2 descriptions only
         * as per the contract.
         */
        containerItem.setSecondaryDescription(itemDetails.getDescriptions().get(0));
      }

      if (!Objects.isNull(itemDetails.getItemAdditonalInformation())
          && !Objects.isNull(itemDetails.getItemAdditonalInformation().get(MFCConstant.WEIGHT))) {
        populateLineWeight(
            containerItem,
            Float.valueOf(
                itemDetails.getItemAdditonalInformation().get(MFCConstant.WEIGHT).toString()));
      }
      if (!Objects.isNull(itemDetails.getItemAdditonalInformation())
          && !Objects.isNull(itemDetails.getItemAdditonalInformation().get(MFCConstant.CUBE))) {
        populateLineCube(
            containerItem,
            Float.valueOf(
                itemDetails.getItemAdditonalInformation().get(MFCConstant.CUBE).toString()));
      }
      // MFC initiative for PLU
      containerItem.setPluNumber(itemDetails.getPluNumber());
    }

    return containerItem;
  }

  private void populateReplenishmentCode(ItemDetails itemDetails, ContainerItem containerItem) {
    if (Objects.nonNull(itemDetails.getItemAdditonalInformation())
        && Objects.nonNull(
            itemDetails.getItemAdditonalInformation().get(REPLENISHMENT_SUB_TYPE_CODE))) {
      containerItem.setReplenishSubTypeCode(
          (Integer)
              itemDetails
                  .getItemAdditonalInformation()
                  .get(MFCConstant.REPLENISHMENT_SUB_TYPE_CODE));
    }
  }

  private void populateItemTypeCode(ItemDetails itemDetails, ContainerItem containerItem) {
    if (StringUtils.isNotBlank(itemDetails.getItemTypeCode())) {
      containerItem.setItemTypeCode(Integer.valueOf(itemDetails.getItemTypeCode()));
    }
  }

  private void populateContainerItemAdditionalInfo(Item item, ContainerItem containerItem) {
    Map<String, Object> additionalInfo =
        Objects.isNull(containerItem.getAdditionalInformation())
            ? new HashMap<>()
            : containerItem.getAdditionalInformation();
    additionalInfo.put(REPLENISHMENT_CODE, item.getReplenishmentCode());
    if (Objects.nonNull(item.getFinancialDetail())) {
      additionalInfo.put(COST_CURRENCY, item.getFinancialDetail().getReportedCostCurrency());
    }
    containerItem.setAdditionalInformation(additionalInfo);
  }

  private void populateDeptCategory(ItemDetails itemDetails, ContainerItem containerItem) {
    if (Objects.nonNull(itemDetails.getItemAdditonalInformation())
        && itemDetails.getItemAdditonalInformation().containsKey(DEPT_CATEGORY_NBR)) {
      containerItem.setDeptCatNbr(
          (Integer) itemDetails.getItemAdditonalInformation().get(DEPT_CATEGORY_NBR));
    }
  }

  private void populateDeptSubCategory(ItemDetails itemDetails, ContainerItem containerItem) {
    if (Objects.nonNull(itemDetails.getItemAdditonalInformation())
        && itemDetails.getItemAdditonalInformation().containsKey(DEPT_SUB_CATEGORY_NBR)) {
      containerItem.setDeptSubcatgNbr(
          (String) itemDetails.getItemAdditonalInformation().get(DEPT_SUB_CATEGORY_NBR));
    }
  }

  // Todo
  private Double retrieveWhpkSell(Item item, ItemDetails itemDetails) {
    return Objects.isNull(itemDetails.getItemAdditonalInformation().get("warehousePackSellAmt"))
        ? 0.0
        : Double.parseDouble(
            String.valueOf(itemDetails.getItemAdditonalInformation().get("warehousePackSellAmt")));
  }

  private String retrieveWarehouseAreaCode(ItemDetails itemDetails) {
    return Objects.isNull(itemDetails.getItemAdditonalInformation().get("warehouseAreaCode"))
        ? null
        : String.valueOf(
            Double.valueOf(
                    itemDetails.getItemAdditonalInformation().get("warehouseAreaCode").toString())
                .intValue());
  }

  private Integer retrieveVnpkQty(Item item, ItemDetails itemDetails) {
    return Objects.isNull(item.getInventoryDetail().getVendorCaseQuantity())
        ? itemDetails.getOrderableQuantity()
        : item.getInventoryDetail().getVendorCaseQuantity();
  }

  private Integer retrieveWhpkQty(Item item, ItemDetails itemDetails) {
    return Objects.isNull(item.getInventoryDetail().getWarehouseCaseQuantity())
        ? itemDetails.getWarehousePackQuantity()
        : item.getInventoryDetail().getWarehouseCaseQuantity();
  }

  private ContainerItem populateDefaultLineWeight(ContainerItem containerItem) {
    containerItem.setVnpkWgtQty(0.0f);
    containerItem.setVnpkWgtUom(MFCConstant.UOM_LB);
    LOGGER.warn(
        "Weight is not present in deliveryDocument Line . hence, falling back to default. weight = {} and weightUOM = {}",
        0.0f,
        MFCConstant.UOM_LB);
    return containerItem;
  }

  private ContainerItem populateLineWeight(ContainerItem containerItem, Float weight) {
    containerItem.setVnpkWgtQty(weight);
    containerItem.setVnpkWgtUom(MFCConstant.UOM_LB);
    return containerItem;
  }

  private ContainerItem populateLineCube(ContainerItem containerItem, Float cube) {

    float cubeQty = Objects.isNull(cube) ? 0.0f : cube;
    containerItem.setVnpkcbqty(cubeQty);
    containerItem.setVnpkcbuomcd(MFCConstant.UOM_CF);
    return containerItem;
  }

  private ContainerItem populateDefaultLineCube(ContainerItem containerItem) {
    containerItem.setVnpkcbqty(0.0f);
    containerItem.setVnpkcbuomcd(MFCConstant.UOM_CF);
    LOGGER.warn(
        "Cube is not present in deliveryDocument Line : . hence, falling back to default. cube = {} and cubeUOM = {}",
        0.0f,
        MFCConstant.UOM_CF);
    return containerItem;
  }

  private ContainerItem populateDefaultVendor(ContainerItem containerItem) {
    containerItem.setVendorNumber(0);
    LOGGER.warn(
        "Vendor details not found in PO . Hence, falling back to default : vendorNumber = {} , vendorDeptNumber = {}",
        0,
        0);
    return containerItem;
  }

  private ContainerItem populateVendor(ContainerItem containerItem, Integer vendor) {

    int vendorNumber = Objects.isNull(vendor) ? 0 : vendor;
    containerItem.setVendorNumber(vendorNumber);
    // PODept is not required
    //    containerItem.setPoDeptNumber(String.valueOf(vendor.getDepartment()));
    return containerItem;
  }

  public ContainerResponse processDuplicateContainer(
      ContainerRequestPayload containerRequestPayload, boolean includeAllDelivery) {

    LOGGER.info(
        "Receive request to search container for trackingId = {} deliveryNumber={} and includeAllDelivery={}",
        containerRequestPayload.getTrackingId(),
        containerRequestPayload.getDeliveryNumber(),
        includeAllDelivery);
    List<ContainerDTO> containers =
        includeAllDelivery
            ? findAllContainer(containerRequestPayload)
            : findContainerFromOpenDelivery(containerRequestPayload.getTrackingId());

    throwErrorIfContainerNotFound(containerRequestPayload.getTrackingId(), containers);

    return ContainerResponse.builder()
        .type(ContainerOperation.CONTAINER_FOUND)
        .containers(containers)
        .build();
  }

  private List<ContainerDTO> findAllContainer(ContainerRequestPayload containerRequestPaylod) {
    LOGGER.info(
        "Finding container across all the delivery for request trackingId = {}",
        containerRequestPaylod.getTrackingId());
    List<Container> containers =
        containerPersisterService.findAllBySSCC(containerRequestPaylod.getTrackingId());

    if (Objects.isNull(containers) || containers.isEmpty()) {
      return null;
    }

    return populateContainerDTOList(containers);
  }

  private List<ContainerDTO> findContainerFromOpenDelivery(String trackingId) {
    LOGGER.info("Finding container on open deliveries for request trackingId = {}", trackingId);
    Map<Long, DeliveryStatus> openDeliveryMap = findOpenDeliveries();
    LOGGER.info("Open deliveries details for sscc = {} are {}", trackingId, openDeliveryMap);

    Set<Long> openDeliveries = openDeliveryMap.keySet();

    List<Container> containers =
        containerPersisterService
            .findAllBySSCC(trackingId)
            .stream()
            .filter(container -> openDeliveries.contains(container.getDeliveryNumber()))
            .collect(Collectors.toList());

    return populateContainerDTOList(containers);
  }

  /* Populate Container Items and transform Container object to ContainerDTO */
  private List<ContainerDTO> populateContainerDTOList(List<Container> containers) {
    Set<String> trackingIds =
        containers.stream().map(Container::getTrackingId).collect(Collectors.toSet());

    List<ContainerItem> containerItems =
        containerPersisterService.findAllItemByTrackingId(new ArrayList<>(trackingIds));

    ContainerUtils.populateContainerItemInContainer(containers, containerItems);

    List<ContainerDTO> containerDTOList = containerTransformer.transformList(containers);

    containerDTOList.forEach(ContainerUtils::replaceContainerWithSSCC);
    return containerDTOList;
  }

  public List<Container> findContainerBySSCC(String trackingId) {
    return containerPersisterService.findAllBySSCC(trackingId);
  }

  public ContainerDTO createContainer(Container _container, ASNDocument asnDocument) {
    // Persisting the container
    Container container = containerPersisterService.saveContainer(_container);

    ContainerDTO containerDTO = containerTransformer.transform(container);
    replaceContainerWithSSCC(containerDTO);
    containerDTO.setEventType("PALLET_RECEIVED");

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)) {
      createReceiptForStorePallet(container);
    }

    if (Objects.nonNull(container.getContainerMiscInfo())
        && StringUtils.equalsIgnoreCase(
            OperationType.OVERAGE.toString(),
            container
                .getContainerMiscInfo()
                .getOrDefault(OPERATION_TYPE, StringUtils.EMPTY)
                .toString())) {
      LOGGER.info(
          "Going to add overage pallet to GDM for pallet number = {} and delivery number = {}",
          container.getSsccNumber(),
          container.getDeliveryNumber());
      List<Pack> packs =
          asnDocument
              .getPacks()
              .stream()
              .filter(
                  pack ->
                      StringUtils.equalsIgnoreCase(
                          pack.getPalletNumber(), _container.getSsccNumber()))
              .collect(Collectors.toList());
      addContainerToGDM(
          container,
          container.getContainerItems(),
          Optional.of(packs),
          AddContainerEvent.ADD_PALLET.getEventType());
    }
    return containerDTO;
  }

  public Container createTransientContainer(
      ContainerScanRequest containerScanRequest, ASNDocument asnDocument) {
    List<Pack> packs =
        asnDocument
            .getPacks()
            .stream()
            .filter(
                pack ->
                    StringUtils.equalsIgnoreCase(
                        pack.getPalletNumber(), containerScanRequest.getTrackingId()))
            .collect(Collectors.toList());

    LOGGER.info(
        "Got packs with trackingId = {} having packSize = {} ",
        containerScanRequest.getTrackingId(),
        packs.size());

    if (Objects.isNull(packs) || packs.isEmpty()) {
      throw new ReceivingConflictException(
          ExceptionCodes.INVALID_GDM_DOCUMENT_DATA,
          String.format(
              "Unable to select packs for asnId=%s for palletNumber=%s on deliveryNumber=%d",
              asnDocument.getShipments().get(0).getDocumentId(),
              containerScanRequest.getTrackingId(),
              containerScanRequest.getDeliveryNumber()));
    }
    populateInvoiceDetail(packs, containerScanRequest.getDeliveryNumber());
    asnDocument.setPacks(packs);
    publishWorkingIfApplicable(findDeliveryMetaData(asnDocument));

    Map<Long, ItemDetails> itemMap = CoreUtil.getItemMap(asnDocument);

    BiFunction<Item, ItemDetails, Boolean> eligibleChecker =
        (item, itemDetails) -> {
          LOGGER.info("Eligible Checker is not applicable in store inbound flow");
          return null;
        };

    Container container = null;
    Map<String, PalletInfo> palletInfoMap = MFCUtils.getPalletInfoMap(asnDocument.getPacks());
    for (Pack pack : asnDocument.getPacks()) {
      try {
        LOGGER.info(
            "StoreInbound : create container for pallet {} and pack number {}",
            pack.getPalletNumber(),
            pack.getPackNumber());

        container =
            createContainer(
                container,
                asnDocument,
                pack,
                itemMap,
                eligibleChecker,
                containerScanRequest.getOverageType(),
                palletInfoMap);
        if (Objects.nonNull(container)) {
          Map<String, Object> additionalContainerAttributes =
              createAdditionalAttributesMap(containerScanRequest);
          populateContainerMiscInfo(container, additionalContainerAttributes);
          populateContainerStatus(container, containerScanRequest);
        }

      } catch (Exception e) {
        LOGGER.error(
            "StoreInbound : Exception while processing pack number {} of pallet {} ",
            pack.getPackNumber(),
            pack.getPalletNumber(),
            e);
        asyncPersister.publishMetric(
            "storeInboundFailedPackCreation",
            "uwms-receiving",
            "storeInbound",
            "storeInbound_createContainer");

      } finally {
        LOGGER.info(
            "StoreInbound : Container creation for pallet {} and pack number {} finished.",
            pack.getPalletNumber(),
            pack.getPackNumber());
      }
    }
    return container;
  }

  private void populateInvoiceDetail(List<Pack> packs, Long deliveryNumber) {
    boolean isInvoiceDetailMissing =
        packs
            .stream()
            .flatMap(
                pack ->
                    Optional.ofNullable(pack.getItems()).orElse(Collections.emptyList()).stream())
            .anyMatch(
                item ->
                    Objects.isNull(item.getInvoice())
                        || Objects.isNull(item.getInvoice().getInvoiceNumber()));
    if (isInvoiceDetailMissing) {
      String dummyInvoiceNumber =
          new StringBuilder().append(deliveryNumber).append(DUMMY_INVOICE_SUFFIX).toString();
      Optional<ContainerItem> retrievedContainerItem =
          containerPersisterService.findByInvoiceNumber(dummyInvoiceNumber);
      int lineNumber =
          retrievedContainerItem.isPresent()
              ? retrievedContainerItem.get().getInvoiceLineNumber() + defaultInvoiceLineStartValue
              : defaultInvoiceLineStartValue;
      for (Pack pack : packs) {
        for (Item item : pack.getItems()) {
          if (Objects.isNull(item.getInvoice())
              || Objects.isNull(item.getInvoice().getInvoiceNumber())) {
            item.setInvoice(
                InvoiceDetail.builder()
                    .invoiceNumber(dummyInvoiceNumber)
                    .invoiceLineNumber(lineNumber++)
                    .build());
          }
        }
      }
    }
  }

  // This will take backward compatibility as originalDeliveryNumber will not be there in phase-1
  private void populateContainerStatus(
      Container container, ContainerScanRequest containerScanRequest) {
    if (Objects.isNull(containerScanRequest.getOverageType())
        || Objects.isNull(containerScanRequest.getOriginalDeliveryNumber())) {
      LOGGER.warn(
          "Not setting container as problem container as overageType={} and originalDeliveryNUmber={}",
          containerScanRequest.getOverageType(),
          containerScanRequest.getOriginalDeliveryNumber());
      return;
    }

    container.setContainerStatus(PROBLEM_OV);
    LOGGER.info("Set container status as {}", PROBLEM_OV);
  }

  private void populateContainerMiscInfo(
      Container container, Map<String, Object> additionalContainerAttributes) {
    if (Objects.isNull(additionalContainerAttributes)) {
      return;
    }

    Map<String, Object> containerMisc = container.getContainerMiscInfo();

    for (Map.Entry<String, Object> entry : additionalContainerAttributes.entrySet()) {
      if (containerMisc.containsKey(entry.getKey())) {
        LOGGER.info(
            "Attributes exists in container misc and hence , going next . key={}", entry.getKey());
        continue;
      }

      containerMisc.put(entry.getKey(), entry.getValue());
      LOGGER.info(
          "Populated containerMisc with key={} and value={}", entry.getKey(), entry.getValue());
    }
  }

  private Map<String, Object> createAdditionalAttributesMap(
      ContainerScanRequest containerScanRequest) {
    Map<String, Object> additionalAttributes = new HashMap<>();
    if (Objects.nonNull(containerScanRequest.getMiscInfo())) {
      additionalAttributes.putAll(containerScanRequest.getMiscInfo());
    }
    if (Objects.nonNull(containerScanRequest.getOriginalDeliveryNumber())) {
      additionalAttributes.put(
          MFCConstant.ORIGINAL_DELIVERY_NUMBER,
          containerScanRequest.getOriginalDeliveryNumber().toString());
    }
    return additionalAttributes;
  }

  public void createReceiptForStorePallet(Container container) {
    // Creating receipt for Store pallet so that for MFC pallet it would be shortage
    if (MFCUtils.isStorePallet(container)) {
      List<Receipt> receipts =
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
              .collect(Collectors.toList());
      mfcReceiptService.saveReceipt(receipts);
      LOGGER.info(
          "Created receipt for tracking id {} and receipts {}",
          container.getTrackingId(),
          receipts);
    }
  }

  private DeliveryMetaData findDeliveryMetaData(ASNDocument asnDocument) {

    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(asnDocument.getDelivery().getDeliveryNumber()))
            .orElse(
                DeliveryMetaData.builder()
                    .deliveryNumber(String.valueOf(asnDocument.getDelivery().getDeliveryNumber()))
                    .trailerNumber(asnDocument.getDelivery().getTrailerId())
                    .deliveryStatus(
                        DeliveryStatus.valueOf(
                            asnDocument.getDelivery().getStatusInformation().getStatus()))
                    .build());

    LOGGER.info("Retrieved DeliveryMetadata for the current request is = {} ", deliveryMetaData);
    return deliveryMetaData;
  }

  public void publishContainer(ContainerDTO containerDTO) {

    if (StringUtils.equalsIgnoreCase(
        containerDTO.getInventoryStatus(), MFCConstant.YET_TO_PUBLISH)) {
      LOGGER.info(
          "Duplicate container detected and hence, not publishing to inventory . containers={}",
          containerDTO);
      return;
    }

    if (isStorePalletPublishingDisabled(containerDTO, tenantSpecificConfigReader)) {
      return;
    }

    // Store Inbound Phase-2 : Overage Container publish will happen from
    // StoreInboundCreateContainerProcessorV2
    if ((tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_SKIP_OVERAGE_STORE_PALLET_PUBLISH)
            || tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_PUBLISH))
        && Objects.nonNull(containerDTO.getContainerMiscInfo())
        && StringUtils.equalsIgnoreCase(
            OperationType.OVERAGE.name(),
            containerDTO
                .getContainerMiscInfo()
                .getOrDefault(OPERATION_TYPE, EMPTY_STRING)
                .toString())) {
      LOGGER.warn("No Container publish for overage container from here");
      return;
    }

    containerService.publishMultipleContainersToInventory(Arrays.asList(containerDTO));
    LOGGER.info("StoreInbound : Container got published successfully to inventory");
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Container findBySSCCAndDeliveryNumber(String ssccNumber, Long deliveryNumber) {
    return containerPersisterService.findBySSCCAndDeliveryNumber(ssccNumber, deliveryNumber);
  }

  public ContainerService getContainerService() {
    return containerService;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Container findTopBySsccNumberAndDeliveryNumber(String trackingId, Long deliveryNumber) {
    return containerRepository.findTopBySsccNumberAndDeliveryNumber(trackingId, deliveryNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findContainerByDeliveryNumber(Long deliveryNumber) {
    return containerRepository.findByDeliveryNumber(deliveryNumber);
  }
}
