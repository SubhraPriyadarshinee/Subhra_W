package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.ENABLE_STORE_PALLET_OSDR;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.INCLUDE_STORE_PALLETS;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MARKET_FULFILLMENT_CENTER;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MFC;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.OPERATION_TYPE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.OVERAGE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.PALLET_TYPE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.STORE;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.removeLoosePacks;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EA;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.v2.*;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.utils.UomUtils;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRContainer;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRItem;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRPayload;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRReceipt;
import com.walmart.move.nim.receiving.mfc.utils.MFCOSDRReceiptDecorator;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class MFCOSDRService extends OsdrService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MFCOSDRService.class);

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ReceiptService receiptService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = MFCConstant.MFC_DELIVERY_SERVICE)
  private MFCDeliveryService deliveryService;

  @Autowired protected MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  private Gson gson;

  @Value("${atlas.delivery.status.topic}")
  private String deliveryOsdrTopic;

  @ManagedConfiguration private AppConfig appConfig;

  public MFCOSDRService() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public OSDRPayload createOSDRv2Payload(Long deliveryNumber, String include) {
    OSDRPayload osdrPayload = new OSDRPayload();
    Summary summary = new Summary();

    LOGGER.info("Calculating OSDR for deliveryNumber: {}", deliveryNumber);

    osdrPayload.setDeliveryNumber(deliveryNumber);
    osdrPayload.setEventType("rcv_asn_osdr");
    osdrPayload.setUserId(ReceivingUtils.retrieveUserId());
    osdrPayload.setTs(new Date());
    osdrPayload.setSummary(summary);

    // Get all containers for the delivery
    List<Container> containers = new ArrayList<>();
    List<Receipt> receipts = new ArrayList<>();
    Map<Pair<String, Integer>, Receipt> receiptMap = new HashMap<>();
    try {
      containers = getContainers(deliveryNumber);
    } catch (ReceivingDataNotFoundException exception) {
      LOGGER.error("No Container has been received and hence, falling back", exception);
    }

    try {

      // Get all receipts for the delivery
      receipts = getReceipts(deliveryNumber);
      receiptMap = createReceiptMap(receipts);
    } catch (ReceivingDataNotFoundException exception) {
      LOGGER.error("No Receipt has been received and hence, falling back", exception);
    }

    // Get all asnDocuments from GDM for not received container info
    List<ASNDocument> asnDocumentList = getAsnDocuments(deliveryNumber, containers);
    // Create PO section
    Map<Pair<String, Integer>, Item> invoiceItemMap =
        createInvoiceItemMap(asnDocumentList, include);
    Map<String, String> invoiceTypeMap = createInvoiceTypeMap(asnDocumentList);
    Set<Pair<String, Integer>> overageInvoiceSet = createOverageInvoiceSet(containers);
    summary.setPurchaseOrders(
        createPurchaseOrders(
            receiptMap, invoiceItemMap, invoiceTypeMap, overageInvoiceSet, include));

    // Create containers
    summary.setContainers(createContainers(containers, receiptMap, include));

    // Add all containers which aren't received
    Map<Pack, String> notReceivedPacks = getNotReceivedPacks(containers, asnDocumentList);
    summary.getContainers().addAll(createNotReceivedContainers(notReceivedPacks, include));
    LOGGER.info("Returning OSDR info: {}", osdrPayload);
    return osdrPayload;
  }

  private String getDeliveryStaus(Long deliveryNumber, List<ASNDocument> asnDocumentList) {

    try {

      return mfcDeliveryMetadataService
          .findByDeliveryNumber(String.valueOf(deliveryNumber))
          .get()
          .getDeliveryStatus()
          .toString();

    } catch (Exception e) {
      LOGGER.error(
          "Error while retrieving delivery status and hence , falling back for delivery={}",
          deliveryNumber);
      return asnDocumentList
          .stream()
          .findAny()
          .get()
          .getDelivery()
          .getStatusInformation()
          .getStatus();
    }
  }

  private List<Container> getContainers(Long deliveryNumber) {
    List<Container> containers =
        containerPersisterService.getContainerByDeliveryNumber(deliveryNumber);
    if (CollectionUtils.isEmpty(containers)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND,
          "Containers not found for delivery:" + deliveryNumber);
    }
    return containers;
  }

  private List<Receipt> getReceipts(Long deliveryNumber) {
    List<Receipt> receipts = receiptService.findByDeliveryNumber(deliveryNumber);
    if (CollectionUtils.isEmpty(receipts)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.RECEIPTS_NOT_FOUND, "Receipts not found for delivery:" + deliveryNumber);
    }
    return receipts;
  }

  public List<ASNDocument> getAsnDocuments(Long deliveryNumber, List<Container> containers) {

    AtomicReference<Boolean> hasOveragePallet = new AtomicReference<>(Boolean.FALSE);
    Set<String> shipmentIds =
        containers
            .stream()
            .filter(
                container -> {
                  if (isOverage(container)) {
                    hasOveragePallet.set(Boolean.TRUE);
                  }
                  return Boolean.TRUE;
                })
            .map(Container::getShipmentId)
            .collect(Collectors.toSet());
    if (hasOveragePallet.get().booleanValue()) {
      // Clearing shipment list to get update shipment from GDM
      shipmentIds.clear();
      LOGGER.info(
          "Overage pallet are there in the delivery = {} and hence, shipmentIds should be refreshed from GDM",
          deliveryNumber);
    }

    List<ASNDocument> asnDocumentList = new ArrayList<>();

    if (shipmentIds.isEmpty()) {
      try {
        DeliveryUpdateMessage deliveryUpdateMessage =
            DeliveryUpdateMessage.builder().deliveryNumber(String.valueOf(deliveryNumber)).build();
        Delivery delivery = deliveryService.getGDMData(deliveryUpdateMessage);
        shipmentIds =
            delivery
                .getShipments()
                .stream()
                .map(doc -> doc.getDocumentId())
                .collect(Collectors.toSet());
        LOGGER.info("Retrieved shipmentId for delivery={} is {}", delivery, shipmentIds);
      } catch (Exception e) {
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.DELIVERY_NOT_FOUND,
            String.format("Unable to fetch delivery from GDM deliveryNumber=%s", deliveryNumber));
      }
    }

    shipmentIds.forEach(
        shipmentId -> {
          asnDocumentList.add(deliveryService.getGDMData(deliveryNumber.toString(), shipmentId));
        });
    if (CollectionUtils.isEmpty(asnDocumentList)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.NO_DELIVERY_FOUND, "Document not found for delivery:" + deliveryNumber);
    }

    return removeLoosePacks(asnDocumentList);
  }

  // Filtering pallets which aren't received for adding in OSDR payload
  private Map<Pack, String> getNotReceivedPacks(
      List<Container> containers, List<ASNDocument> asnDocumentList) {
    List<String> ssccNumbers =
        containers.stream().map(Container::getSsccNumber).collect(Collectors.toList());
    Map<Pack, String> nonReceivingPacks = new HashMap<>();
    asnDocumentList
        .stream()
        .filter(asnDocument -> !MFCUtils.isDSDShipment(asnDocument.getShipment()))
        .forEach(
            asnDocument ->
                nonReceivingPacks.putAll(
                    asnDocument
                        .getPacks()
                        .stream()
                        .filter(pack -> !ssccNumbers.contains(pack.getPalletNumber()))
                        .collect(
                            Collectors.toMap(
                                pack -> pack, pack -> asnDocument.getShipment().getDocumentId()))));
    return nonReceivingPacks;
  }

  private List<OSDRContainer> createContainers(
      List<Container> containers, Map<Pair<String, Integer>, Receipt> receiptMap, String include) {
    List<OSDRContainer> osdrContainers = new ArrayList<>();
    containers.forEach(
        container -> {
          String palletType =
              Objects.nonNull(container.getContainerMiscInfo())
                  ? String.valueOf(container.getContainerMiscInfo().get(PALLET_TYPE))
                  : null;
          boolean isEligibleForOSDR = isEligibleForOSDR(palletType, include);
          if (!isEligibleForOSDR) {
            return;
          }
          addContainerDetails(container, osdrContainers, receiptMap, palletType);
        });
    return osdrContainers;
  }

  private void addContainerDetails(
      Container container,
      List<OSDRContainer> osdrContainers,
      Map<Pair<String, Integer>, Receipt> receiptMap,
      String palletType) {
    if (CollectionUtils.isNotEmpty(container.getContainerItems())) {

      Map<String, List<ContainerItem>> uomTypeItemMap =
          container
              .getContainerItems()
              .stream()
              .collect(Collectors.groupingBy(ContainerItem::getQuantityUOM));
      uomTypeItemMap.forEach(
          (uom, items) -> {
            OSDRContainer osdrContainer = new OSDRContainer();
            osdrContainer.setTrackingId(container.getSsccNumber());
            osdrContainer.setSscc(container.getSsccNumber());
            osdrContainer.setContainerType(container.getContainerType());
            osdrContainer.setShipmentDocumentId(container.getShipmentId());
            osdrContainer.setReceivedFor(STORE.equals(palletType) ? STORE : MFC);
            osdrContainer.setItems(
                createContainerItems(container, items, receiptMap, osdrContainer));
            osdrContainer.setUomType(UomUtils.getBaseUom(uom));

            if (!CollectionUtils.isEmpty(osdrContainer.getItems())) {
              osdrContainers.add(osdrContainer);
            }
          });
    }
  }

  private List<OSDRItem> createContainerItems(
      Container container,
      List<ContainerItem> containerItems,
      Map<Pair<String, Integer>, Receipt> receiptMap,
      OSDRContainer osdrContainer) {
    List<OSDRItem> osdrItems = new ArrayList<>();
    for (ContainerItem containerItem : containerItems) {
      Pair<String, Integer> deliveryInvoiceLineKey =
          new Pair<>(containerItem.getInvoiceNumber(), containerItem.getInvoiceLineNumber());
      Receipt aggregateReceipt =
          getAggregareReceipt(receiptMap, deliveryInvoiceLineKey, containerItem);

      OSDRItem osdrItem = new OSDRItem();
      osdrItem.setItemNumber(containerItem.getItemNumber().toString());
      osdrItem.setInvoiceLineNumber(containerItem.getInvoiceLineNumber().toString());
      osdrItem.setGtin(containerItem.getGtin());
      osdrItem.setInvoiceNumber(containerItem.getInvoiceNumber());

      updateItemReportedQty(osdrItem, containerItem, aggregateReceipt, osdrContainer);
      updateReceivedQty(osdrItem, containerItem, aggregateReceipt);
      String itemUom = osdrItem.getRcvdQtyUom();
      if (isQtyPresent(aggregateReceipt.getFbDamagedQty())) {
        OsdrDataWeightBased damage =
            prepareOSDRData(
                aggregateReceipt.getFbDamagedQty(),
                aggregateReceipt.getFbDamagedQtyUOM(),
                aggregateReceipt.getFbDamagedReasonCode());
        osdrItem.setDamage(damage);
        osdrContainer.setDamage(addOSDRDetails(osdrContainer.getDamage(), damage));
      }

      if (isQtyPresent(aggregateReceipt.getFbRejectedQty())) {
        OsdrDataWeightBased reject =
            prepareOSDRData(
                aggregateReceipt.getFbRejectedQty(),
                aggregateReceipt.getFbRejectedQtyUOM(),
                aggregateReceipt.getFbRejectedReasonCode());
        osdrItem.setReject(reject);
        osdrContainer.setReject(addOSDRDetails(osdrContainer.getReject(), reject));
      }

      // Expectation for overage container after delivery finalisation to be entirely overage
      int qtyDiff = getQuantityDiff(osdrItem, container);
      if (qtyDiff < 0 || isQtyPresent(aggregateReceipt.getFbOverQty())) {
        int qty = qtyDiff != 0 ? -qtyDiff : aggregateReceipt.getFbOverQty();
        OsdrDataWeightBased overage =
            prepareOSDRData(
                qty,
                StringUtils.defaultIfEmpty(aggregateReceipt.getFbOverQtyUOM(), itemUom),
                aggregateReceipt.getFbOverReasonCode());
        osdrItem.setOverage(overage);
        osdrContainer.setOverage(addOSDRDetails(osdrContainer.getOverage(), overage));
      }

      if (qtyDiff > 0 || isQtyPresent(aggregateReceipt.getFbShortQty())) {
        int qty = qtyDiff != 0 ? qtyDiff : aggregateReceipt.getFbShortQty();
        OsdrDataWeightBased shortage =
            prepareOSDRData(
                qty,
                StringUtils.defaultIfEmpty(aggregateReceipt.getFbShortQtyUOM(), itemUom),
                aggregateReceipt.getFbShortReasonCode());
        osdrItem.setShortage(shortage);
        osdrContainer.setShortage(addOSDRDetails(osdrContainer.getShortage(), shortage));
      }
      addContainerQty(osdrContainer, osdrItem);
      osdrItems.add(osdrItem);
    }
    return osdrItems;
  }

  private void updateReceivedQty(
      OSDRItem osdrItem, ContainerItem containerItem, Receipt aggregateReceipt) {
    String itemUom =
        StringUtils.defaultIfEmpty(
            containerItem.getQuantityUOM(), aggregateReceipt.getQuantityUom());
    int rcvdQty = aggregateReceipt.getQuantity();
    if (Objects.nonNull(containerItem.getOrderFilledQty())
        && containerItem.getOrderFilledQty() != 0L) {
      rcvdQty = containerItem.getOrderFilledQty().intValue();
      if (isQtyPresent(aggregateReceipt.getFbConcealedShortageQty())) {
        rcvdQty -= aggregateReceipt.getFbConcealedShortageQty();
      }
    }
    osdrItem.setRcvdQty(rcvdQty);
    osdrItem.setRcvdQtyUom(itemUom);
    org.springframework.data.util.Pair<Double, String> baseQtyDetails =
        UomUtils.getBaseUnitQuantity(osdrItem.getRcvdQty(), osdrItem.getRcvdQtyUom());
    osdrItem.setDerivedRcvdQty(baseQtyDetails.getFirst());
    osdrItem.setDerivedRcvdQtyUom(baseQtyDetails.getSecond());
  }

  private void updateItemReportedQty(
      OSDRItem osdrItem,
      ContainerItem containerItem,
      Receipt aggregateReceipt,
      OSDRContainer osdrContainer) {
    Integer reportedQty = containerItem.getQuantity();
    if (isQtyPresent(aggregateReceipt.getFbConcealedShortageQty())) {
      reportedQty -= aggregateReceipt.getFbConcealedShortageQty();
    }
    osdrItem.setReportedQty(reportedQty);
    osdrItem.setReportedQtyUom(containerItem.getQuantityUOM());
    org.springframework.data.util.Pair<Double, String> baseQtyDetails =
        UomUtils.getBaseUnitQuantity(osdrItem.getReportedQty(), osdrItem.getReportedQtyUom());
    osdrItem.setDerivedReportedQty(baseQtyDetails.getFirst());
    osdrItem.setDerivedReportedQtyUom(baseQtyDetails.getSecond());
    osdrContainer.addReportedQty(osdrItem.getReportedQty());
    osdrContainer.addDerivedReportedQty(osdrItem.getDerivedReportedQty());
  }

  private void addContainerQty(OSDRContainer osdrContainer, OSDRItem osdrItem) {
    if (Objects.isNull(osdrContainer.getRcvdQty())) {
      osdrContainer.setRcvdQty(osdrItem.getRcvdQty());
      osdrContainer.setRcvdQtyUom(osdrItem.getRcvdQtyUom());
      osdrContainer.setDerivedRcvdQty(osdrItem.getDerivedRcvdQty());
      osdrContainer.setDerivedRcvdQtyUom(osdrItem.getDerivedRcvdQtyUom());
    } else {
      osdrContainer.addRcvdQty(osdrItem.getRcvdQty());
      osdrContainer.addDerivedRcvdQty(osdrItem.getDerivedRcvdQty());
    }
  }

  private int getQuantityDiff(OSDRItem osdrItem, Container container) {
    int expectedQty = getQuantity(osdrItem.getReportedQty());
    if (isOverage(container)) {
      expectedQty = 0;
      osdrItem.setReportedQty(expectedQty);
    }
    return expectedQty - (getQuantity(osdrItem.getRcvdQty()));
  }

  private Receipt getAggregareReceipt(
      Map<Pair<String, Integer>, Receipt> receiptMap,
      Pair<String, Integer> deliveryInvoiceLineKey,
      ContainerItem containerItem) {
    Receipt aggregateReceipt = receiptMap.get(deliveryInvoiceLineKey);
    if (Objects.isNull(aggregateReceipt)) {
      aggregateReceipt = new Receipt();
      aggregateReceipt.setQuantity(0);
      aggregateReceipt.setQuantityUom(containerItem.getQuantityUOM());
      aggregateReceipt.setFbOverQty(0);
      aggregateReceipt.setFbOverQtyUOM(containerItem.getQuantityUOM());
      aggregateReceipt.setFbShortQty(0);
      aggregateReceipt.setFbShortQtyUOM(containerItem.getQuantityUOM());
      aggregateReceipt.setFbDamagedQty(0);
      aggregateReceipt.setFbDamagedQtyUOM(containerItem.getQuantityUOM());
      aggregateReceipt.setFbRejectedQty(0);
      aggregateReceipt.setFbRejectedQtyUOM(containerItem.getQuantityUOM());
    }
    return aggregateReceipt;
  }

  private OsdrDataWeightBased addOSDRDetails(
      OsdrDataWeightBased totalOSDR, OsdrDataWeightBased itemOSDR) {
    if (Objects.nonNull(itemOSDR)) {
      if (Objects.isNull(totalOSDR)) {
        return OsdrDataWeightBased.builder()
            .quantity(itemOSDR.getQuantity())
            .uom(itemOSDR.getUom())
            .derivedQuantity(itemOSDR.getQuantity())
            .derivedUom(itemOSDR.getUom())
            .code(itemOSDR.getCode())
            .build();
      } else {
        totalOSDR.addQuantity(itemOSDR.getQuantity());
      }
    }
    return totalOSDR;
  }

  private List<OSDRPurchaseOrder> createPurchaseOrders(
      Map<Pair<String, Integer>, Receipt> receiptMap,
      Map<Pair<String, Integer>, Item> invoiceItemMap,
      Map<String, String> invoiceTypeMap,
      Set<Pair<String, Integer>> overageInvoiceSet,
      String include) {

    Map<Pair<String, String>, OSDRPurchaseOrder> poMap = new HashMap<>();

    invoiceItemMap.forEach(
        (invoiceKey, item) -> {
          Receipt aggregateReceipt = receiptMap.get(invoiceKey);
          if (Objects.isNull(aggregateReceipt)) {
            aggregateReceipt = new Receipt();
            aggregateReceipt.setInvoiceNumber(invoiceKey.getKey());
            aggregateReceipt.setInvoiceLineNumber(invoiceKey.getValue());
            aggregateReceipt.setQuantity(0);
            String qtyUom = EA;
            if (tenantSpecificConfigReader
                .getScalingQtyEnabledForReplenishmentTypes()
                .contains(item.getReplenishmentCode())) {
              qtyUom =
                  UomUtils.getScaledUom(
                      item.getInventoryDetail().getReportedUom(),
                      appConfig.getUomScalingPrefix(),
                      appConfig.getScalableUomList());
            }
            aggregateReceipt.setQuantityUom(qtyUom);
            aggregateReceipt.setFbOverQty(0);
            aggregateReceipt.setFbOverQtyUOM(qtyUom);
            aggregateReceipt.setFbShortQty(0);
            aggregateReceipt.setFbShortQtyUOM(qtyUom);
            aggregateReceipt.setFbDamagedQty(0);
            aggregateReceipt.setFbDamagedQtyUOM(qtyUom);
            aggregateReceipt.setFbRejectedQty(0);
            aggregateReceipt.setFbRejectedQtyUOM(qtyUom);
            aggregateReceipt.setFbConcealedShortageQty(0);
          }
          String palletType = invoiceTypeMap.get(invoiceKey.getKey());
          boolean isEligibleForOSDR = isEligibleForOSDR(palletType, include);
          if (!isEligibleForOSDR) {
            return;
          }
          OSDRLine line =
              createPurchaseOrderLine(aggregateReceipt, invoiceItemMap, overageInvoiceSet);
          Pair<String, String> poKey = new Pair<>(invoiceKey.getKey(), line.getDerivedRcvdQtyUom());
          OSDRPurchaseOrder osdrPurchaseOrder = poMap.get(poKey);
          if (Objects.isNull(osdrPurchaseOrder)) {
            osdrPurchaseOrder = new OSDRPurchaseOrder();
            osdrPurchaseOrder.setInvoiceNumber(invoiceKey.getKey());
            osdrPurchaseOrder.setReceivedFor(STORE.equals(palletType) ? STORE : MFC);

            List<OSDRLine> osdrLines = new ArrayList<>();
            osdrLines.add(line);
            osdrPurchaseOrder.setLines(osdrLines);
            poMap.put(poKey, osdrPurchaseOrder);
            osdrPurchaseOrder.setDerivedRcvdQtyUom(line.getDerivedRcvdQtyUom());
            osdrPurchaseOrder.setDerivedRcvdQty(line.getDerivedRcvdQty());
            osdrPurchaseOrder.setRcvdQty(line.getRcvdQty());
            osdrPurchaseOrder.setRcvdQtyUom(line.getRcvdQtyUom());
            osdrPurchaseOrder.setUomType(line.getDerivedRcvdQtyUom());
            osdrPurchaseOrder.setReportedQty(line.getReportedQty());
            osdrPurchaseOrder.setDerivedReportedQty(line.getDerivedReportedQty());
          } else {
            osdrPurchaseOrder.getLines().add(line);
            osdrPurchaseOrder.setDerivedRcvdQty(
                osdrPurchaseOrder.getDerivedRcvdQty() + line.getDerivedRcvdQty());
            osdrPurchaseOrder.setRcvdQty(osdrPurchaseOrder.getRcvdQty() + line.getRcvdQty());
            osdrPurchaseOrder.addReportedQty(line.getReportedQty());
            osdrPurchaseOrder.addDerivedReportedQty(line.getDerivedReportedQty());
          }
          osdrPurchaseOrder.setOverage(
              addOSDRDetails(osdrPurchaseOrder.getOverage(), line.getOverage()));
          osdrPurchaseOrder.setShortage(
              addOSDRDetails(osdrPurchaseOrder.getShortage(), line.getShortage()));
          osdrPurchaseOrder.setReject(
              addOSDRDetails(osdrPurchaseOrder.getReject(), line.getReject()));
          osdrPurchaseOrder.setDamage(
              addOSDRDetails(osdrPurchaseOrder.getDamage(), line.getDamage()));
        });
    return new ArrayList<>(poMap.values());
  }

  private OSDRLine createPurchaseOrderLine(
      Receipt aggregateReceipt,
      Map<Pair<String, Integer>, Item> invoiceItemMap,
      Set<Pair<String, Integer>> overageInvoiceSet) {
    Pair<String, Integer> invoiceLinePair =
        new Pair<>(aggregateReceipt.getInvoiceNumber(), aggregateReceipt.getInvoiceLineNumber());
    Item item = invoiceItemMap.get(invoiceLinePair);
    OSDRLine osdrLine = new OSDRLine();
    osdrLine.setLineNumber(aggregateReceipt.getInvoiceLineNumber());
    osdrLine.setGtin(item.getGtin());
    setReceivedQty(osdrLine, item, aggregateReceipt);
    if (isQtyPresent(aggregateReceipt.getFbDamagedQty())) {
      OsdrDataWeightBased damage =
          prepareOSDRData(
              aggregateReceipt.getFbDamagedQty(),
              aggregateReceipt.getFbDamagedQtyUOM(),
              aggregateReceipt.getFbDamagedReasonCode());
      osdrLine.setDamage(damage);
    }

    if (isQtyPresent(aggregateReceipt.getFbRejectedQty())) {
      OsdrDataWeightBased reject =
          prepareOSDRData(
              aggregateReceipt.getFbRejectedQty(),
              aggregateReceipt.getFbRejectedQtyUOM(),
              aggregateReceipt.getFbRejectedReasonCode());
      osdrLine.setReject(reject);
    }

    Pair<Integer, String> reportedQtyPair = getReportedQty(item, aggregateReceipt);
    Integer reportedQty = reportedQtyPair.getKey();
    String qtyUom = reportedQtyPair.getValue();
    if (overageInvoiceSet.contains(invoiceLinePair)) {
      reportedQty = 0;
    }
    osdrLine.setReportedQty(reportedQty);
    org.springframework.data.util.Pair<Double, String> baseUnitQty =
        UomUtils.getBaseUnitQuantity(reportedQty, qtyUom);
    osdrLine.setDerivedReportedQty(baseUnitQty.getFirst());
    int qtyDiff =
        reportedQty
            - (getQuantity(osdrLine.getRcvdQty())
                + getQuantity(osdrLine.getDamage())
                + getQuantity(osdrLine.getReject()));

    if (qtyDiff < 0 || isQtyPresent(aggregateReceipt.getFbOverQty())) {
      int qty = qtyDiff != 0 ? -qtyDiff : aggregateReceipt.getFbOverQty();
      OsdrDataWeightBased overage =
          prepareOSDRData(
              qty,
              Objects.nonNull(aggregateReceipt.getFbOverQtyUOM())
                  ? aggregateReceipt.getFbOverQtyUOM()
                  : qtyUom,
              aggregateReceipt.getFbOverReasonCode());
      osdrLine.setOverage(overage);
    }

    if (qtyDiff > 0 || isQtyPresent(aggregateReceipt.getFbShortQty())) {
      int qty = qtyDiff != 0 ? qtyDiff : aggregateReceipt.getFbShortQty();
      OsdrDataWeightBased shortage =
          prepareOSDRData(
              qty,
              Objects.nonNull(aggregateReceipt.getFbShortQtyUOM())
                  ? aggregateReceipt.getFbShortQtyUOM()
                  : qtyUom,
              aggregateReceipt.getFbShortReasonCode());
      osdrLine.setShortage(shortage);
    }
    return osdrLine;
  }

  private Pair<Integer, String> getReportedQty(Item item, Receipt aggregateReceipt) {
    int reportedQty;
    String qtyUom = EA;
    if (tenantSpecificConfigReader
        .getScalingQtyEnabledForReplenishmentTypes()
        .contains(item.getReplenishmentCode())) {
      org.springframework.data.util.Pair<Integer, String> scaledQuantity =
          UomUtils.getScaledQuantity(
              item.getInventoryDetail().getReportedQuantity(),
              item.getInventoryDetail().getReportedUom(),
              appConfig.getUomScalingPrefix(),
              appConfig.getScalableUomList());
      reportedQty = scaledQuantity.getFirst();
      qtyUom = scaledQuantity.getSecond();
    } else {
      reportedQty = (int) Math.ceil(item.getInventoryDetail().getReportedQuantity());
    }
    if (isQtyPresent(aggregateReceipt.getFbConcealedShortageQty())) {
      reportedQty -= aggregateReceipt.getFbConcealedShortageQty();
    }
    return new Pair<>(reportedQty, qtyUom);
  }

  private void setReceivedQty(OSDRLine osdrLine, Item item, Receipt aggregateReceipt) {
    if (isQtyPresent(aggregateReceipt.getQuantity())) {
      Integer scaleRcvdQty =
          Objects.isNull(aggregateReceipt.getQuantity()) ? 0 : aggregateReceipt.getQuantity();
      String scaleRcvdUom = aggregateReceipt.getQuantityUom();
      org.springframework.data.util.Pair<Double, String> baseQtyDetails =
          UomUtils.getBaseUnitQuantity(scaleRcvdQty, scaleRcvdUom);
      osdrLine.setDerivedRcvdQty(baseQtyDetails.getFirst());
      osdrLine.setDerivedRcvdQtyUom(baseQtyDetails.getSecond());
      osdrLine.setRcvdQty(scaleRcvdQty);
      osdrLine.setRcvdQtyUom(scaleRcvdUom);
    } else {
      osdrLine.setDerivedRcvdQty(0.0);
      osdrLine.setDerivedRcvdQtyUom(
          tenantSpecificConfigReader
                  .getScalingQtyEnabledForReplenishmentTypes()
                  .contains(item.getReplenishmentCode())
              ? item.getInventoryDetail().getReportedUom()
              : EA);
      osdrLine.setRcvdQty(0);
      String qtyUom = EA;
      if (tenantSpecificConfigReader
          .getScalingQtyEnabledForReplenishmentTypes()
          .contains(item.getReplenishmentCode())) {
        qtyUom =
            UomUtils.getScaledUom(
                item.getInventoryDetail().getReportedUom(),
                appConfig.getUomScalingPrefix(),
                appConfig.getScalableUomList());
      }
      osdrLine.setRcvdQtyUom(qtyUom);
    }
  }

  private OsdrDataWeightBased prepareOSDRData(
      Integer scaledQty, String scaledUom, OSDRCode reasonCode) {
    org.springframework.data.util.Pair<Double, String> baseUnitQty =
        UomUtils.getBaseUnitQuantity(scaledQty, scaledUom);
    return OsdrDataWeightBased.builder()
        .quantity(baseUnitQty.getFirst())
        .uom(baseUnitQty.getSecond())
        .code(Objects.nonNull(reasonCode) ? reasonCode.getCode() : null)
        .scaledQuantity(scaledQty)
        .scaledUom(scaledUom)
        .derivedQuantity(baseUnitQty.getFirst())
        .derivedUom(baseUnitQty.getSecond())
        .build();
  }

  // Creating containers which aren't received. Since they aren't received, whole quantity is
  // shortage
  private List<OSDRContainer> createNotReceivedContainers(
      Map<Pack, String> notReceivedPacks, String include) {
    Map<Pair<String, String>, OSDRContainer> osdrContainerMap = new HashMap<>();
    notReceivedPacks.forEach(
        (pack, shipmentId) -> {
          if (!(INCLUDE_STORE_PALLETS.equals(include)
              || tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_OSDR))) {
            List<Item> mfcItems =
                pack.getItems()
                    .stream()
                    .filter(
                        item ->
                            StringUtils.equalsIgnoreCase(
                                item.getReplenishmentCode(), MARKET_FULFILLMENT_CENTER))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(mfcItems)) {
              return;
            } else {
              pack.setItems(mfcItems);
            }
          }
          if (CollectionUtils.isNotEmpty(pack.getItems())) {
            Map<String, List<Item>> groupedUomItems =
                pack.getItems()
                    .stream()
                    .collect(
                        Collectors.groupingBy(item -> item.getInventoryDetail().getReportedUom()));
            groupedUomItems.forEach(
                (uom, items) -> {
                  OSDRContainer osdrContainer = null;
                  Pair<String, String> key = new Pair<>(pack.getPalletNumber(), uom);
                  if (Objects.isNull(osdrContainerMap.get(key))) {
                    osdrContainer = new OSDRContainer();
                    osdrContainer.setTrackingId(pack.getPalletNumber());
                    osdrContainer.setSscc(pack.getPalletNumber());
                    osdrContainer.setContainerType(MFCConstant.PALLET);
                    osdrContainer.setShipmentDocumentId(shipmentId);
                    osdrContainer.setDerivedRcvdQty(0.0);
                    osdrContainer.setDerivedRcvdQtyUom(uom);
                    osdrContainer.setRcvdQty(0);
                    osdrContainer.setRcvdQtyUom(
                        tenantSpecificConfigReader
                                .getScalingQtyEnabledForReplenishmentTypes()
                                .contains(MFCUtils.getPalletType(Collections.singletonList(pack)))
                            ? UomUtils.getScaledUom(
                                uom,
                                appConfig.getUomScalingPrefix(),
                                appConfig.getScalableUomList())
                            : EA);
                    osdrContainer.setItems(createNotReceivedContainerItems(items, osdrContainer));
                  } else {
                    osdrContainer = osdrContainerMap.get(key);
                    osdrContainer
                        .getItems()
                        .addAll(createNotReceivedContainerItems(items, osdrContainer));
                  }
                  osdrContainerMap.put(key, osdrContainer);
                });
          }
        });
    return new ArrayList<>(osdrContainerMap.values());
  }

  private List<OSDRItem> createNotReceivedContainerItems(
      List<Item> packItems, OSDRContainer osdrContainer) {
    List<OSDRItem> osdrItems = new ArrayList<>();
    packItems.forEach(
        item -> {
          OSDRItem osdrItem = new OSDRItem();
          osdrItem.setItemNumber(item.getItemNumber().toString());
          osdrItem.setInvoiceLineNumber(item.getInvoice().getInvoiceLineNumber().toString());
          osdrItem.setGtin(item.getGtin());
          osdrItem.setInvoiceNumber(item.getInvoice().getInvoiceNumber());

          int reportedQty = 0;
          String qtyUom = EA;
          if (tenantSpecificConfigReader
              .getScalingQtyEnabledForReplenishmentTypes()
              .contains(item.getReplenishmentCode())) {
            org.springframework.data.util.Pair<Integer, String> scaledQuantity =
                UomUtils.getScaledQuantity(
                    item.getInventoryDetail().getReportedQuantity(),
                    item.getInventoryDetail().getReportedUom(),
                    appConfig.getUomScalingPrefix(),
                    appConfig.getScalableUomList());
            reportedQty = scaledQuantity.getFirst();
            qtyUom = scaledQuantity.getSecond();
          } else {
            reportedQty = (int) Math.ceil(item.getInventoryDetail().getReportedQuantity());
          }
          osdrItem.setDerivedReportedQty(item.getInventoryDetail().getReportedQuantity());
          osdrItem.setDerivedReportedQtyUom(item.getInventoryDetail().getReportedUom());
          osdrItem.setReportedQty(reportedQty);
          osdrItem.setReportedQtyUom(qtyUom);
          osdrItem.setDerivedRcvdQty(0.0);
          osdrItem.setDerivedRcvdQtyUom(item.getInventoryDetail().getReportedUom());
          osdrItem.setRcvdQtyUom(qtyUom);
          osdrItem.setRcvdQty(0);
          osdrItem.setRcvdQtyUom(qtyUom);
          OsdrDataWeightBased shortage =
              OsdrDataWeightBased.builder()
                  .quantity(osdrItem.getDerivedReportedQty())
                  .uom(osdrItem.getDerivedReportedQtyUom())
                  .scaledQuantity(osdrItem.getReportedQty())
                  .scaledUom(osdrItem.getReportedQtyUom())
                  .derivedQuantity(osdrItem.getDerivedReportedQty())
                  .derivedUom(osdrItem.getDerivedReportedQtyUom())
                  .build();
          osdrItem.setShortage(shortage);
          osdrItems.add(osdrItem);
          osdrContainer.setReceivedFor(
              StringUtils.equalsIgnoreCase(item.getReplenishmentCode(), MARKET_FULFILLMENT_CENTER)
                  ? MFC
                  : STORE);
          osdrContainer.setUomType(item.getInventoryDetail().getReportedUom());
          osdrContainer.setShortage(addOSDRDetails(osdrContainer.getShortage(), shortage));
          addContainerQty(osdrContainer, osdrItem);
        });
    return osdrItems;
  }

  private Map<Pair<String, Integer>, Receipt> createReceiptMap(List<Receipt> receipts) {
    Map<Pair<String, Integer>, Receipt> receiptMap = new HashMap<>();
    receipts.forEach(
        receipt -> {
          Pair<String, Integer> deliveryInvoiceLineKey =
              new Pair<>(receipt.getInvoiceNumber(), receipt.getInvoiceLineNumber());
          Receipt aggregateReceipt = receiptMap.get(deliveryInvoiceLineKey);
          MFCOSDRReceiptDecorator receiptDecorator = new MFCOSDRReceiptDecorator(receipt);
          if (Objects.isNull(aggregateReceipt)) {
            aggregateReceipt = new Receipt();
            aggregateReceipt.setDeliveryNumber(receipt.getDeliveryNumber());
            aggregateReceipt.setInvoiceNumber(receipt.getInvoiceNumber());
            aggregateReceipt.setInvoiceLineNumber(receipt.getInvoiceLineNumber());
            aggregateReceipt.setInboundShipmentDocId(receipt.getInboundShipmentDocId());
            aggregateReceipt.setQuantity(receiptDecorator.getQuantity());
            aggregateReceipt.setQuantityUom(receiptDecorator.getQuantityUom());
            aggregateReceipt.setFbOverQty(receiptDecorator.getOverageQuantity());
            aggregateReceipt.setFbOverQtyUOM(receiptDecorator.getOverageQtyUom());
            aggregateReceipt.setFbOverReasonCode(receiptDecorator.getOverageReasoncode());
            aggregateReceipt.setFbShortQty(receiptDecorator.getShortQuantity());
            aggregateReceipt.setFbShortQtyUOM(receiptDecorator.getShortQuantityUom());
            aggregateReceipt.setFbShortReasonCode(receiptDecorator.getShortageReasoncode());
            aggregateReceipt.setFbDamagedQty(receiptDecorator.getDamagedQuantity());
            aggregateReceipt.setFbDamagedQtyUOM(receiptDecorator.getDamagedQuantityUom());
            aggregateReceipt.setFbDamagedReasonCode(receiptDecorator.getDamagedReasonCode());
            aggregateReceipt.setFbRejectedQty(receiptDecorator.getRejectedQuantity());
            aggregateReceipt.setFbRejectedQtyUOM(receiptDecorator.getRejectedQuantityUom());
            aggregateReceipt.setFbRejectedReasonCode(receiptDecorator.getRejectedReasonCode());
            aggregateReceipt.setFbConcealedShortageQty(receiptDecorator.getConcealedShortageQty());
            aggregateReceipt.setFbConcealedShortageReasonCode(
                receiptDecorator.getConcealedShortageReasonCode());
            receiptMap.put(deliveryInvoiceLineKey, aggregateReceipt);
          } else {
            aggregateReceipt.setQuantity(
                aggregateReceipt.getQuantity() + receiptDecorator.getQuantity());
            if (Objects.isNull(aggregateReceipt.getQuantityUom())) {
              aggregateReceipt.setQuantityUom(receiptDecorator.getQuantityUom());
            }
            aggregateReceipt.setFbOverQty(
                aggregateReceipt.getFbOverQty() + receiptDecorator.getOverageQuantity());
            if (Objects.isNull(aggregateReceipt.getFbOverQtyUOM())) {
              aggregateReceipt.setFbOverQtyUOM(receiptDecorator.getOverageQtyUom());
            }
            if (Objects.isNull(aggregateReceipt.getFbOverReasonCode())) {
              aggregateReceipt.setFbOverReasonCode(receiptDecorator.getOverageReasoncode());
            }
            aggregateReceipt.setFbShortQty(
                aggregateReceipt.getFbShortQty() + receiptDecorator.getShortQuantity());
            if (Objects.isNull(aggregateReceipt.getFbShortQtyUOM())) {
              aggregateReceipt.setFbShortQtyUOM(receiptDecorator.getShortQuantityUom());
            }
            if (Objects.isNull(aggregateReceipt.getFbShortReasonCode())) {
              aggregateReceipt.setFbShortReasonCode(receiptDecorator.getShortageReasoncode());
            }
            aggregateReceipt.setFbDamagedQty(
                aggregateReceipt.getFbDamagedQty() + receiptDecorator.getDamagedQuantity());
            if (Objects.isNull(aggregateReceipt.getFbDamagedQtyUOM())) {
              aggregateReceipt.setFbDamagedQtyUOM(receiptDecorator.getDamagedQuantityUom());
            }
            if (Objects.isNull(aggregateReceipt.getFbDamagedReasonCode())) {
              aggregateReceipt.setFbDamagedReasonCode(receiptDecorator.getDamagedReasonCode());
            }
            aggregateReceipt.setFbRejectedQty(
                aggregateReceipt.getFbRejectedQty() + receiptDecorator.getRejectedQuantity());
            if (Objects.isNull(aggregateReceipt.getFbRejectedQtyUOM())) {
              aggregateReceipt.setFbRejectedQtyUOM(receiptDecorator.getRejectedQuantityUom());
            }
            if (Objects.isNull(aggregateReceipt.getFbRejectedReasonCode())) {
              aggregateReceipt.setFbRejectedReasonCode(receiptDecorator.getRejectedReasonCode());
            }
            aggregateReceipt.setFbConcealedShortageQty(
                aggregateReceipt.getFbConcealedShortageQty()
                    + receiptDecorator.getConcealedShortageQty());
          }
        });
    return receiptMap;
  }

  private Map<Pair<String, Integer>, Item> createInvoiceItemMap(
      List<ASNDocument> asnDocumentList, String include) {
    Map<Pair<String, Integer>, Item> invoiceItemMap = new HashMap<>();
    asnDocumentList.forEach(
        asnDocument ->
            asnDocument
                .getPacks()
                .forEach(
                    pack ->
                        pack.getItems()
                            .forEach(
                                item -> {
                                  if (!(INCLUDE_STORE_PALLETS.equals(include)
                                      || tenantSpecificConfigReader.isFeatureFlagEnabled(
                                          ENABLE_STORE_PALLET_OSDR))) {
                                    if (!StringUtils.equalsIgnoreCase(
                                        item.getReplenishmentCode(), MARKET_FULFILLMENT_CENTER))
                                      return;
                                  }
                                  invoiceItemMap.put(
                                      new Pair<>(
                                          item.getInvoice().getInvoiceNumber(),
                                          item.getInvoice().getInvoiceLineNumber()),
                                      item);
                                })));

    return invoiceItemMap;
  }

  private Map<String, String> createInvoiceTypeMap(List<ASNDocument> asnDocumentList) {
    // ASN will contain all the pack having pallet number on it. This enrichment has been done in
    // getAsnDocuments method
    Map<String, String> invoiceTypeMap = new HashMap<>();
    asnDocumentList.forEach(
        asnDocument -> {
          if (!CollectionUtils.isEmpty(asnDocument.getPacks())) {
            Map<String, String> palletTypeMap = MFCUtils.getPalletTypeMap(asnDocument.getPacks());
            asnDocument
                .getPacks()
                .forEach(
                    pack ->
                        pack.getItems()
                            .forEach(
                                item ->
                                    invoiceTypeMap.put(
                                        item.getInvoice().getInvoiceNumber(),
                                        palletTypeMap.get(pack.getPalletNumber()))));
          }
        });
    return invoiceTypeMap;
  }

  private Set<Pair<String, Integer>> createOverageInvoiceSet(List<Container> containers) {
    Set<Pair<String, Integer>> overageInvoiceSet = new HashSet<>();
    containers.forEach(
        container ->
            container
                .getContainerItems()
                .forEach(
                    containerItem -> {
                      if (isOverage(container))
                        overageInvoiceSet.add(
                            new Pair<>(
                                containerItem.getInvoiceNumber(),
                                containerItem.getInvoiceLineNumber()));
                    }));
    return overageInvoiceSet;
  }

  public void publishOSDR(OSDRPayload osdrPayload, Map<String, Object> forwardablHeader) {
    LOGGER.info("MFC OSDR initiate for delivery={}", osdrPayload.getDeliveryNumber());
    ReceivingUtils.populateTenantContext(forwardablHeader);
    forwardablHeader.put("eventType", osdrPayload.getEventType());
    LOGGER.info(
        "MFC OSDR initiate for delivery={} with headers {}",
        osdrPayload.getDeliveryNumber(),
        forwardablHeader);
    Message<String> message =
        KafkaHelper.buildKafkaMessage(
            osdrPayload.getDeliveryNumber(),
            gson.toJson(osdrPayload),
            deliveryOsdrTopic,
            forwardablHeader);

    kafkaTemplate.send(message);

    LOGGER.info("Successfully publish osdr event. payload = {}", gson.toJson(osdrPayload));
  }

  @Override
  protected OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary) {
    throw new ReceivingDataNotFoundException("NOT_SUPPORTED", " No implementation found");
  }

  private boolean isQtyPresent(Integer qty) {
    return Objects.nonNull(qty) && qty != 0;
  }

  private int getQuantity(OsdrDataWeightBased osdrDataWeightBased) {
    if (Objects.nonNull(osdrDataWeightBased)) {
      return osdrDataWeightBased.getScaledQuantity();
    }
    return 0;
  }

  private int getQuantity(Integer qty) {
    if (Objects.nonNull(qty)) {
      return qty;
    }
    return 0;
  }

  private boolean isOverage(Container container) {
    String operationType =
        Objects.nonNull(container.getContainerMiscInfo())
            ? String.valueOf(container.getContainerMiscInfo().get(OPERATION_TYPE))
            : StringUtils.EMPTY;
    return OVERAGE.equalsIgnoreCase(operationType);
  }

  public List<MFCOSDRPayload> fetchOSDRDetails(List<Long> deliveryNumbers) {
    List<MFCOSDRPayload> osdrDetails = new ArrayList<>();

    List<Container> containers =
        containerPersisterService.getContainerByDeliveryNumberIn(deliveryNumbers);
    if (!CollectionUtils.isEmpty(containers)) {
      Map<Long, List<Container>> containersMap =
          containers.stream().collect(Collectors.groupingBy(Container::getDeliveryNumber));
      List<Receipt> receipts = receiptService.findByDeliveryNumberIn(deliveryNumbers);
      Map<Long, List<Receipt>> receiptsMap = new HashMap<>();
      if (!CollectionUtils.isEmpty(receipts)) {
        receiptsMap = receipts.stream().collect(Collectors.groupingBy(Receipt::getDeliveryNumber));
      }
      for (Long deliveryNumber : deliveryNumbers) {
        try {
          if (containersMap.containsKey(deliveryNumber)) {
            osdrDetails.add(
                getOsdrDetails(
                    deliveryNumber,
                    containersMap.get(deliveryNumber),
                    receiptsMap.getOrDefault(deliveryNumber, Collections.emptyList())));
          } else {
            throw new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND,
                "Containers not found for delivery:" + deliveryNumber);
          }
        } catch (ReceivingDataNotFoundException e) {
          LOGGER.info(
              "No containers present for delivery number {}. Hence skipping it", deliveryNumber);
          osdrDetails.add(
              MFCOSDRPayload.builder()
                  .deliveryNumber(deliveryNumber)
                  .exceptionCode(e.getErrorCode())
                  .errorMessage(e.getDescription())
                  .build());
        } catch (Exception e) {
          LOGGER.error("Error while processing delivery number {}.", deliveryNumber, e);
          osdrDetails.add(
              MFCOSDRPayload.builder()
                  .deliveryNumber(deliveryNumber)
                  .exceptionCode(ExceptionCodes.RESOURCE_NOT_FOUND)
                  .errorMessage(e.getMessage())
                  .build());
        }
      }
    }

    return osdrDetails;
  }

  private MFCOSDRPayload getOsdrDetails(
      Long deliveryNumber, List<Container> containers, List<Receipt> receipts) {

    Map<Pair<String, Integer>, Receipt> receiptMap = createReceiptMap(receipts);
    List<MFCOSDRContainer> containerList = new ArrayList<>();
    Set<String> shipmentIds = new HashSet<>();
    for (Container container : containers) {
      List<MFCOSDRItem> items = new ArrayList<>();
      shipmentIds.add(container.getShipmentId());
      for (ContainerItem containerItem : container.getContainerItems()) {
        Receipt receipt =
            receiptMap.get(
                new Pair<>(containerItem.getInvoiceNumber(), containerItem.getInvoiceLineNumber()));
        List<MFCOSDRReceipt> mfcosdrReceipts = new ArrayList<>();
        if (Objects.nonNull(receipt)) {
          addReceiptData(
              mfcosdrReceipts,
              receipt.getQuantity(),
              receipt.getQuantityUom(),
              null,
              QuantityType.RECEIVED.getType());
          addReceiptData(
              mfcosdrReceipts,
              receipt.getFbRejectedQty(),
              receipt.getFbRejectedQtyUOM(),
              receipt.getFbRejectedReasonCode(),
              QuantityType.REJECTED.getType());
          addReceiptData(
              mfcosdrReceipts,
              receipt.getFbDamagedQty(),
              receipt.getFbDamagedQtyUOM(),
              receipt.getFbDamagedReasonCode(),
              QuantityType.DAMAGE.getType());
        }
        items.add(
            MFCOSDRItem.builder()
                .invoiceNumber(containerItem.getInvoiceNumber())
                .invoiceLineNumber(containerItem.getInvoiceLineNumber())
                .gtin(containerItem.getGtin())
                .itemNumber(containerItem.getItemNumber())
                .vnpk(containerItem.getVnpkQty())
                .whpk(containerItem.getWhpkQty())
                .receipt(mfcosdrReceipts)
                .quantity(containerItem.getQuantity())
                .uom(containerItem.getQuantityUOM())
                .build());
      }

      containerList.add(
          MFCOSDRContainer.builder()
              .trackingId(container.getSsccNumber())
              .operationType(
                  Objects.nonNull(container.getContainerMiscInfo())
                          && container.getContainerMiscInfo().containsKey(OPERATION_TYPE)
                      ? container.getContainerMiscInfo().get(OPERATION_TYPE).toString()
                      : null)
              .type(
                  Objects.nonNull(container.getContainerMiscInfo())
                          && container.getContainerMiscInfo().containsKey(PALLET_TYPE)
                      ? container.getContainerMiscInfo().get(PALLET_TYPE).toString()
                      : null)
              .content(items)
              .build());
    }

    return MFCOSDRPayload.builder()
        .deliveryNumber(deliveryNumber)
        .asnNumber(shipmentIds)
        .containers(containerList)
        .build();
  }

  private void addReceiptData(
      List<MFCOSDRReceipt> mfcosdrReceipts,
      Integer quantity,
      String quantityUom,
      OSDRCode reasonCode,
      String type) {
    if (Objects.nonNull(quantity) && quantity > 0) {
      mfcosdrReceipts.add(
          MFCOSDRReceipt.builder()
              .type(type)
              .quantity(quantity)
              .uom(quantityUom)
              .reasonCode(Objects.nonNull(reasonCode) ? reasonCode.getCode() : null)
              .build());
    }
  }

  private boolean isEligibleForOSDR(String palletType, String include) {
    boolean isStorePallet = STORE.equals(palletType);
    boolean isEligibleForOSDR = true;
    if (isStorePallet
        && !(INCLUDE_STORE_PALLETS.equals(include)
            || tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_OSDR))) {
      isEligibleForOSDR = false;
    }
    return isEligibleForOSDR;
  }
}
