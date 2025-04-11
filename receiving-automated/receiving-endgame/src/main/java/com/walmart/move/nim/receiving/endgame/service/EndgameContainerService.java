package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.convertValue;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.createContainerTag;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ADD_ON_SERVICES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_DELIVERY_NUMBER;
import static io.strati.libs.commons.lang.StringUtils.isNumeric;
import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AddOnService;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PropertyDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Vendor;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.AbstractContainerService;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.model.EndgameReceivingRequest;
import com.walmart.move.nim.receiving.endgame.model.ExpiryDateUpdatePublisherData;
import com.walmart.move.nim.receiving.endgame.model.SearchCriteria;
import com.walmart.move.nim.receiving.endgame.model.UpdateAttributes;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class EndgameContainerService extends AbstractContainerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndgameContainerService.class);

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private JmsPublisher jmsPublisher;

  @Autowired private ContainerItemRepository containerItemRepository;

  @ManagedConfiguration private MaasTopics maasTopics;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Resource(name = EndgameConstants.ENDGAME_ITEM_UPDATE_PROCESSOR)
  private EventProcessor endgameItemUpdateProcessor;

  @Resource(name = EndgameConstants.ENDGAME_EXPIRY_DATE_PROCESSOR)
  private EventProcessor endgameExpiryDateProcessor;

  @Autowired private Gson gson;

  private Gson gsonWithDateAdapter;

  public EndgameContainerService() {
    gsonWithDateAdapter =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  /**
   * This method will create a new container instance. This method will call core container
   * persister service. This method will also create receipts and return the container to publish.
   * The order should be first save container then save receipts, otherwise lock exception will
   * come. As the above order is followed elsewhere.
   *
   * @param scanEventData
   * @param purchaseOrder
   * @param eachQuantity
   * @param purchaseOrderLine
   * @return {@link Container}
   */
  @Transactional
  @InjectTenantFilter
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.INTERNAL,
      executionFlow = "INVTR-CTR-Create")
  public Container createAndSaveContainerAndReceipt(
      ScanEventData scanEventData,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity,
      Container container) {
    LOGGER.debug("[containerId={}] is going to be created", container.getTrackingId());
    Receipt receipt =
        createReceiptFromPurchaseOrderLine(
            scanEventData.getDoorNumber(),
            scanEventData.getDeliveryNumber(),
            purchaseOrder.getPoNumber(),
            purchaseOrderLine,
            eachQuantity,
            container);
    containerPersisterService.createReceiptAndContainer(
        Collections.singletonList(receipt), container);
    return container;
  }

  @Transactional
  @InjectTenantFilter
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.INTERNAL,
      executionFlow = "ICC-INVTR-CTR-Create")
  public Container createAndSaveContainerAndReceipt(
      EndgameReceivingRequest receivingRequest,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      Container container) {
    LOGGER.debug("[containerId={}] is going to be created", container.getTrackingId());
    Receipt receipt =
        createReceiptFromPurchaseOrderLine(
            receivingRequest.getDoorNumber(),
            receivingRequest.getDeliveryNumber(),
            purchaseOrder.getPoNumber(),
            purchaseOrderLine,
            receivingRequest.getQuantity(),
            container);
    containerPersisterService.createReceiptAndContainer(
        Collections.singletonList(receipt), container);
    return container;
  }

  public Container getContainer(
      ScanEventData scanEventData,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity) {
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(scanEventData.getDeliveryNumber()))
            .orElse(null);
    Container container =
        createContainer(
            scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, deliveryMetaData);
    return container;
  }

  /**
   * This method will add item to a container.
   *
   * @param scanEventData
   * @param purchaseOrder
   * @param eachQuantity
   * @param purchaseOrderLine
   * @return {@link Container}
   */
  public Container addItemAndGetContainer(
      Container container,
      ScanEventData scanEventData,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity) {
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(scanEventData.getDeliveryNumber()))
            .orElse(null);

    ContainerItem containerItem =
        createContainerItem(
            scanEventData.getTrailerCaseLabel(),
            purchaseOrder,
            purchaseOrderLine,
            eachQuantity,
            deliveryMetaData);
    container.setContainerItems(Collections.singletonList(containerItem));
    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    return container;
  }

  public Container createContainer(
      ScanEventData scanEventData,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity,
      DeliveryMetaData deliveryMetaData) {

    Container container = getContainer(purchaseOrderLine);

    container.setTrackingId(scanEventData.getTrailerCaseLabel());
    container.setDeliveryNumber(scanEventData.getDeliveryNumber());
    container.setLocation(scanEventData.getDiverted().getStatus());
    container.setIsAuditRequired(scanEventData.getIsAuditRequired());
    container.setContainerMiscInfo(
        prepareContainerMiscInfo(scanEventData.getContainerTagList(), purchaseOrderLine));
    ContainerItem containerItem =
        createContainerItem(
            scanEventData.getTrailerCaseLabel(),
            purchaseOrder,
            purchaseOrderLine,
            eachQuantity,
            deliveryMetaData);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);
    container.setSsccNumber(getSsccNumber(scanEventData, purchaseOrderLine.getSscc()));
    container.setShipmentId(scanEventData.getShipmentId());
    return container;
  }

  private String getSsccNumber(ScanEventData scanEventData, String sscc) {
    if (hasLength(sscc)) return sscc;

    return isEmpty(scanEventData.getBoxIds()) ? null : scanEventData.getBoxIds().get(0);
  }

  public Container getContainer(
      EndgameReceivingRequest receivingRequest,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine) {
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(receivingRequest.getDeliveryNumber()))
            .orElse(null);
    return createContainer(receivingRequest, purchaseOrder, purchaseOrderLine, deliveryMetaData);
  }

  public Container createContainer(
      EndgameReceivingRequest receivingRequest,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      DeliveryMetaData deliveryMetaData) {

    Container container = getContainer(purchaseOrderLine);

    container.setParentTrackingId(receivingRequest.getParentTrackingId());
    container.setTrackingId(receivingRequest.getTrackingId());
    container.setDeliveryNumber(receivingRequest.getDeliveryNumber());
    container.setLocation(receivingRequest.getDiverted().getStatus());
    container.setIsAuditRequired(receivingRequest.getIsAuditRequired());
    container.setSsccNumber(purchaseOrderLine.getSscc());
    container.setContainerMiscInfo(
        prepareContainerMiscInfo(receivingRequest.getContainerTagList(), purchaseOrderLine));
    ContainerItem containerItem =
        createContainerItem(
            receivingRequest.getTrackingId(),
            purchaseOrder,
            purchaseOrderLine,
            receivingRequest.getQuantity(),
            deliveryMetaData);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  private Container getContainer(PurchaseOrderLine purchaseOrderLine) {
    Container container = new Container();
    container.setContainerType(EndgameConstants.VENDOR_PACK);
    container.setIsAuditRequired(Boolean.FALSE);
    container.setContainerStatus(EndgameConstants.AVAILABLE);
    container.setInventoryStatus(EndgameConstants.AVAILABLE);

    container =
        Objects.isNull(purchaseOrderLine.getVnpk().getWeight())
            ? populateDefaultWeight(container)
            : populateWeight(container, purchaseOrderLine.getVnpk().getWeight());

    container =
        Objects.isNull(purchaseOrderLine.getVnpk().getCube())
            ? populateDefaultCube(container)
            : populateCube(container, purchaseOrderLine.getVnpk().getCube());

    container.setCtrShippable(false);
    container.setCtrReusable(false);
    String user = EndgameConstants.DEFAULT_AUDIT_USER;
    if (nonNull(TenantContext.getAdditionalParams())
        && nonNull(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY))) {
      user =
          String.valueOf(
              TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    container.setCreateUser(user);
    container.setLastChangedUser(user);
    container.setCreateTs(new Date());
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());
    container.setMessageId(TenantContext.getCorrelationId());

    return container;
  }

  private Map<String, Object> prepareContainerMiscInfo(
      List<ContainerTag> containerTags, PurchaseOrderLine purchaseOrderLine) {
    List<ContainerTag> containerTagList = new ArrayList<>();
    if (!isEmpty(containerTags)) {
      containerTagList.addAll(containerTags);
    }

    Map<String, Object> additionalInformation = purchaseOrderLine.getAdditionalInformation();
    if (!isEmpty(additionalInformation) && additionalInformation.containsKey(ADD_ON_SERVICES)) {
      List<AddOnService> addOnServices =
          convertValue(
              additionalInformation.get(ADD_ON_SERVICES),
              new TypeReference<List<AddOnService>>() {});
      addOnServices
          .stream()
          .map(AddOnService::getServiceType)
          .map(prepType -> createContainerTag(ReceivingConstants.PREP_TYPE_PREFIX + prepType))
          .forEach(containerTagList::add);
    }

    Map<String, Object> containerMiscInfo = new HashMap<>();
    if (!isEmpty(containerTagList)) {
      containerMiscInfo.put(
          ReceivingConstants.CONTAINER_TAG, ReceivingUtils.stringfyJson(containerTagList));
    }
    return containerMiscInfo;
  }

  private Container populateCube(Container container, PropertyDetail cube) {
    float cubeQty = Objects.isNull(cube.getQuantity()) ? 0.0f : cube.getQuantity();

    container.setCube(cubeQty);

    String uom = Objects.isNull(cube.getUom()) ? EndgameConstants.UOM_CF : cube.getUom();

    container.setCubeUOM(uom);
    return container;
  }

  private Container populateDefaultCube(Container container) {
    container.setCube(0.0f);
    container.setCubeUOM(EndgameConstants.UOM_CF);
    LOGGER.warn(
        "Cube is not present in deliveryDocument. Hence falling back to default [cube={}] and [cubeUOM={}]",
        0.0f,
        EndgameConstants.UOM_CF);
    return container;
  }

  private Container populateWeight(Container container, PropertyDetail weight) {
    container.setWeight(weight.getQuantity());
    String uom = Objects.isNull(weight.getUom()) ? EndgameConstants.UOM_LB : weight.getUom();
    container.setWeightUOM(uom);
    return container;
  }

  private Container populateDefaultWeight(Container container) {
    container.setWeight(0.0f);
    container.setWeightUOM(EndgameConstants.UOM_LB);
    LOGGER.warn(
        "Weight is not present in deliveryDocument. Hence falling back to default [weight={}] and [weightUOM={}]",
        0.0f,
        EndgameConstants.UOM_LB);
    return container;
  }

  private ContainerItem createContainerItem(
      String trackingId,
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity,
      DeliveryMetaData deliveryMetaData) {
    ContainerItem containerItem = new ContainerItem();

    containerItem.setPurchaseReferenceNumber(purchaseOrder.getPoNumber());
    containerItem.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());
    containerItem.setItemNumber(purchaseOrderLine.getItemDetails().getNumber());
    containerItem.setQuantity(eachQuantity);
    containerItem.setGtin(purchaseOrderLine.getItemDetails().getConsumableGTIN());
    containerItem.setItemUPC(purchaseOrderLine.getItemDetails().getConsumableGTIN());
    containerItem.setCaseUPC(purchaseOrderLine.getItemDetails().getOrderableGTIN());
    containerItem.setInboundChannelMethod(purchaseOrderLine.getChannel());
    containerItem.setOutboundChannelMethod(EndgameConstants.STAPLESTOCK);
    containerItem.setTrackingId(trackingId);
    containerItem.setBaseDivisionCode(purchaseOrder.getBaseDivisionCode());
    containerItem.setFinancialReportingGroupCode(purchaseOrder.getFinancialGroupCode());
    containerItem.setVnpkQty(purchaseOrderLine.getVnpk().getQuantity());
    containerItem.setWhpkQty(purchaseOrderLine.getWhpk().getQuantity());
    containerItem.setWhpkSell(purchaseOrderLine.getWhpk().getSell());
    containerItem.setFreightBillQty(purchaseOrderLine.getFreightBillQty());
    containerItem.setDeptNumber(purchaseOrderLine.getItemDetails().getVendorDepartment());
    containerItem.setSellerId(purchaseOrder.getSellerId());
    containerItem.setSellerType(purchaseOrder.getSellerType());
    containerItem.setPoTypeCode(getPoTypeCode(purchaseOrder));
    if (Objects.nonNull(deliveryMetaData)) {
      containerItem.setCarrierScacCode(deliveryMetaData.getCarrierScacCode());
      containerItem.setCarrierName(deliveryMetaData.getCarrierName());
      containerItem.setTrailerNbr(deliveryMetaData.getTrailerNumber());
      containerItem.setBillCode(deliveryMetaData.getBillCode());
    }

    containerItem =
        Objects.isNull(purchaseOrderLine.getVendor())
            ? Objects.isNull(purchaseOrder.getVendor())
                ? populateDefaultVendor(containerItem)
                : populateVendor(containerItem, purchaseOrder.getVendor())
            : populateVendor(containerItem, purchaseOrderLine.getVendor());

    containerItem.setTotalPurchaseReferenceQty(purchaseOrder.getFreightBillQty());
    containerItem.setPurchaseCompanyId(purchaseOrder.getPurchaseCompanyId());

    containerItem =
        Objects.isNull(purchaseOrderLine.getVnpk().getWeight())
            ? Objects.isNull(purchaseOrder.getWeight())
                ? populateDefaultLineWeight(containerItem)
                : populateLineWeight(containerItem, purchaseOrder.getWeight())
            : populateLineWeight(containerItem, purchaseOrderLine.getVnpk().getWeight());

    containerItem =
        Objects.isNull(purchaseOrderLine.getVnpk().getCube())
            ? Objects.isNull(purchaseOrder.getCube())
                ? populateDefaultLineCube(containerItem)
                : populateLineCube(containerItem, purchaseOrder.getCube())
            : populateLineCube(containerItem, purchaseOrderLine.getVnpk().getCube());

    List<String> itemDescriptions = purchaseOrderLine.getItemDetails().getDescriptions();
    if (!isEmpty(itemDescriptions)) {
      containerItem.setDescription(purchaseOrderLine.getItemDetails().getDescriptions().get(0));
      /*
       * Item descriptions may contain more than 2 elements.
       * In that case also we will send 2 descriptions only
       * as per the contract.
       */
      if (itemDescriptions.size() > 1) {
        containerItem.setSecondaryDescription(
            purchaseOrderLine.getItemDetails().getDescriptions().get(1));
      }
    }

    /*
     Check if rotate date is available in delivery meta data
      for this delivery and item number, then set rotate date in container item.
    */

    String strRotateDate =
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData,
            String.valueOf(purchaseOrderLine.getItemDetails().getNumber()),
            EndgameConstants.ROTATE_DATE);
    Date rotateDate = EndGameUtils.parseRotateDate(strRotateDate);
    if (nonNull(rotateDate)) {
      containerItem.setRotateDate(rotateDate);
    }
    return containerItem;
  }

  private Integer getPoTypeCode(PurchaseOrder purchaseOrder) {
    if (nonNull(purchaseOrder.getPoTypeCode())) {
      return purchaseOrder.getPoTypeCode();
    } else if (isNumeric(purchaseOrder.getLegacyType())) {
      return Integer.valueOf(purchaseOrder.getLegacyType());
    }
    return null;
  }

  private ContainerItem populateDefaultLineWeight(ContainerItem containerItem) {
    containerItem.setVnpkWgtQty(0.0f);
    containerItem.setVnpkWgtUom(EndgameConstants.UOM_LB);
    LOGGER.warn(
        "Weight is not present in deliveryDocument Line. Hence falling back to default [weight={}] and [weightUOM={}]",
        0.0f,
        EndgameConstants.UOM_LB);
    return containerItem;
  }

  private ContainerItem populateLineWeight(ContainerItem containerItem, PropertyDetail weight) {
    containerItem.setVnpkWgtQty(weight.getQuantity());

    String uom = Objects.isNull(weight.getUom()) ? EndgameConstants.UOM_LB : weight.getUom();

    containerItem.setVnpkWgtUom(uom);
    return containerItem;
  }

  private ContainerItem populateLineCube(ContainerItem containerItem, PropertyDetail cube) {

    float cubeQty = Objects.isNull(cube.getQuantity()) ? 0.0f : cube.getQuantity();
    containerItem.setVnpkcbqty(cubeQty);

    String uom = Objects.isNull(cube.getUom()) ? EndgameConstants.UOM_CF : cube.getUom();
    containerItem.setVnpkcbuomcd(uom);
    return containerItem;
  }

  private ContainerItem populateDefaultLineCube(ContainerItem containerItem) {
    containerItem.setVnpkcbqty(0.0f);
    containerItem.setVnpkcbuomcd(EndgameConstants.UOM_CF);
    LOGGER.warn(
        "Cube is not present in deliveryDocument Line. Hence falling back to default. [cube={}] and [cubeUOM={}]",
        0.0f,
        EndgameConstants.UOM_CF);
    return containerItem;
  }

  private ContainerItem populateDefaultVendor(ContainerItem containerItem) {
    containerItem.setVendorNumber(0);
    containerItem.setPoDeptNumber(String.valueOf(containerItem.getVendorNumber()));
    LOGGER.warn(
        "Vendor details not found in PO. Hence falling back to default [vendorNumber={}] [vendorDeptNumber={}]",
        0,
        0);
    return containerItem;
  }

  private ContainerItem populateVendor(ContainerItem containerItem, Vendor vendor) {

    int vendorNumber = Objects.isNull(vendor.getNumber()) ? 0 : vendor.getNumber();
    containerItem.setVendorNumber(vendorNumber);
    containerItem.setPoDeptNumber(String.valueOf(vendor.getDepartment()));
    return containerItem;
  }

  private Receipt createReceiptFromPurchaseOrderLine(
      String doorNumber,
      Long deliveryNumber,
      String poNumber,
      PurchaseOrderLine purchaseOrderLine,
      int eachQuantity,
      Container container) {

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setDoorNumber(doorNumber);
    receipt.setPurchaseReferenceNumber(poNumber);
    receipt.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());
    receipt.setVnpkQty(purchaseOrderLine.getVnpk().getQuantity());
    receipt.setWhpkQty(purchaseOrderLine.getWhpk().getQuantity());
    receipt.setQuantity(eachQuantity);
    receipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
    receipt.setEachQty(eachQuantity);
    receipt.setSsccNumber(container.getSsccNumber());

    return receipt;
  }

  private Receipt createReceiptFromPurchaseOrderLine(
      String poNumber, PurchaseOrderLine purchaseOrderLine, int eachQuantity) {
    Receipt receipt = new Receipt();
    receipt.setPurchaseReferenceNumber(poNumber);
    receipt.setDeliveryNumber(DEFAULT_DELIVERY_NUMBER);
    receipt.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());
    receipt.setVnpkQty(purchaseOrderLine.getVnpk().getQuantity());
    receipt.setWhpkQty(purchaseOrderLine.getWhpk().getQuantity());

    receipt.setQuantity(eachQuantity);
    receipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
    receipt.setEachQty(eachQuantity);

    return receipt;
  }

  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "INVTR-CTR-Pub",
      externalCall = true)
  public void publishContainer(ContainerDTO container) {
    Map<String, Object> headers =
        ReceivingUtils.getForwardableHeadersWithRequestOriginator(ReceivingUtils.getHeaders());

    addExtraHeadersIfRequire(headers);

    headers.put(ReceivingConstants.IDEM_POTENCY_KEY, container.getTrackingId());
    // converting container Object into String
    String jsonObject = gsonWithDateAdapter.toJson(container);

    // publishing container information to inventory
    ReceivingJMSEvent jmsEvent = new ReceivingJMSEvent(headers, jsonObject);
    jmsPublisher.publish(maasTopics.getPubReceiptsTopic(), jmsEvent, Boolean.TRUE);
  }

  private void addExtraHeadersIfRequire(Map<String, Object> headers) {

    if (Objects.isNull(TenantContext.getAdditionalParams())) {
      LOGGER.warn("No extra headers require to pass on for container publishing");
      return;
    }

    for (Map.Entry<String, Object> entry : TenantContext.getAdditionalParams().entrySet()) {
      LOGGER.info(
          "Extra Header for container: [key={}] and [value={}]", entry.getKey(), entry.getValue());
      if (nonNull(entry.getValue())) {
        headers.put(entry.getKey(), entry.getValue());
      }
    }

    LOGGER.info("Extra headers are set for container");
  }

  /**
   * This method is responsible for updating all the container Items with rotate Date for a
   * particular delivery, purchase reference number and purchase reference line number.
   *
   * <p>Since we don't have delivery number and container status in container item, also we don't
   * have purchase reference number and purchase reference line number in container, So first
   * fetching all the container for a delivery then fetching all the container items by purchase
   * reference number and purchase reference line number. Then after filtering the container list by
   * container status collecting all the tracking ids into a list. Then selecting those container
   * items by filtered tracking id's only from the container items list. And updating those
   * container items only.
   *
   * @param deliveryNumber
   * @param updateAttributesData
   * @return ExpiryDateUpdatePublisherData
   */
  @Transactional
  @InjectTenantFilter
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.INTERNAL,
      executionFlow = "Container-Expiry-Update")
  public ExpiryDateUpdatePublisherData updateRotateDate(
      Long deliveryNumber, UpdateAttributesData updateAttributesData) {
    SearchCriteria searchCriteria = updateAttributesData.getSearchCriteria();
    Date rotateDate =
        EndGameUtils.parseRotateDate(updateAttributesData.getUpdateAttributes().getRotateDate());

    List<Container> containers =
        containerPersisterService.getContainerByDeliveryNumber(deliveryNumber);
    LOGGER.info("Got containers for [deliveryNumber={}]", deliveryNumber);

    if (isEmpty(containers)) {
      LOGGER.warn(EndgameConstants.CONTAINERS_NOT_FOUND_FOR_DELIVERY_NUMBER, deliveryNumber);
      return null;
    }
    List<Container> filteredContainersByItemNumber =
        containers
            .stream()
            .filter(
                container ->
                    !ReceivingConstants.STATUS_BACKOUT.equals(container.getContainerStatus())
                        && searchCriteria
                            .getItemNumber()
                            .equals(
                                String.valueOf(
                                    container.getContainerItems().get(0).getItemNumber())))
            .collect(Collectors.toList());

    List<ContainerItem> containerItemsNeedToUpdate = new ArrayList<>();
    List<String> trackingIdsNeedToPublish = new ArrayList<>();

    for (Container container : filteredContainersByItemNumber) {
      String trackingId = container.getTrackingId();
      /*
       Excluding the tracking id from publishing list to inventory
       which already got updated from Decant station.
      */
      if (!trackingId.equals(searchCriteria.getTrackingId())) {
        trackingIdsNeedToPublish.add(trackingId);
      }
      // Scenario to look back on - doing changes in an entity within a transaction will be
      // committed/flush to back to the database on transaction close/commit
      // Keywords: Abnormal / Exception / Entity Manager / Entity Transaction
      if (!updateAttributesData.getUpdateAttributes().isExpired()) {
        ContainerItem containerItem = container.getContainerItems().get(0);
        containerItem.setRotateDate(rotateDate);
        containerItemsNeedToUpdate.add(containerItem);
      }
    }
    if (!isEmpty(containerItemsNeedToUpdate)) {
      LOGGER.info("Going to save containerItems: {}", containerItemsNeedToUpdate);
      containerItemRepository.saveAll(containerItemsNeedToUpdate);
      LOGGER.info("Saved containerItems with rotateDate");
    }
    if (!isEmpty(trackingIdsNeedToPublish)) {
      UpdateAttributes updateAttributes =
          UpdateAttributes.builder()
              .rotateDate(updateAttributesData.getUpdateAttributes().getRotateDate())
              .isExpired(updateAttributesData.getUpdateAttributes().isExpired())
              .build();
      SearchCriteria searchCriteriaNeedToPublish =
          SearchCriteria.builder()
              .baseDivisionCode(searchCriteria.getBaseDivisionCode())
              .financialReportingGroup(searchCriteria.getFinancialReportingGroup())
              .itemNumber(searchCriteria.getItemNumber())
              .itemUPC(searchCriteria.getItemUPC())
              .trackingIds(trackingIdsNeedToPublish)
              .build();
      return ExpiryDateUpdatePublisherData.builder()
          .searchCriteria(searchCriteriaNeedToPublish)
          .updateAttributes(updateAttributes)
          .build();
    }
    return null;
  }

  /**
   * Publish tcl's/tracking id's and rotate date where rotate date needs to be updated for a
   * particular delivery, purchase reference number and purchase reference line number. If
   * publishing to JMS topic is failed then it will be retried.
   *
   * @param expiryDateUpdatePublisherData
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "INVTR-BLK-Upload",
      externalCall = true)
  public void publishContainerUpdate(ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData) {
    Map<String, Object> headers =
        ReceivingUtils.getForwardableHeadersWithRequestOriginator(ReceivingUtils.getHeaders());
    // converting container Object into String
    String jsonObject = gson.toJson(expiryDateUpdatePublisherData);

    // publishing container information to inventory
    ReceivingJMSEvent jmsEvent = new ReceivingJMSEvent(headers, jsonObject);
    jmsPublisher.publish(maasTopics.getPubContainerUpdateTopic(), jmsEvent, Boolean.TRUE);
  }

  public void processContainerUpdates(String message) {
    UpdateAttributesData updateAttributesData = gson.fromJson(message, UpdateAttributesData.class);

    if (ObjectUtils.allNotNull(
        updateAttributesData,
        updateAttributesData.getSearchCriteria(),
        updateAttributesData.getUpdateAttributes())) {
      try {
        endgameExpiryDateProcessor.processEvent(updateAttributesData);
      } catch (ReceivingException e) {

        LOGGER.error(
            "Unable to process the expiry date request {}", ExceptionUtils.getStackTrace(e));

        throw new ReceivingInternalException(
            ExceptionCodes.UNABLE_TO_PROCESS_EXPIRY_UPDATION,
            String.format(
                EndgameConstants.UNABLE_TO_PROCESS_EXPIRY_UPDATION_ERROR_MSG,
                gson.toJson(updateAttributesData)),
            e);
      }
    }

    if (ObjectUtils.allNotNull(
        updateAttributesData,
        updateAttributesData.getSearchCriteria(),
        updateAttributesData.getItemAttributes())) {
      try {
        endgameItemUpdateProcessor.processEvent(updateAttributesData);
      } catch (ReceivingException e) {
        LOGGER.error("Unable to process the fts update {}", ExceptionUtils.getStackTrace(e));
        throw new ReceivingInternalException(
            ExceptionCodes.UNABLE_TO_PROCESS_FTS_UPDATION,
            String.format(
                EndgameConstants.UNABLE_TO_PROCESS_FTS_UPDATION_ERROR_MSG,
                gson.toJson(updateAttributesData)),
            e);
      }
    }
  }
}
