package com.walmart.move.nim.receiving.core.mock.data;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE_BILL_SIGNED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE_FINALIZED;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryDocumentResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PropertyDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.QuantityDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Vnpk;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Whpk;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.LabelCode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.springframework.core.io.ClassPathResource;

public class MockGdmResponse {

  private static final Gson gson = new Gson();

  public static GdmDeliveryDocumentResponse getGdmDeliveryDocumentResponse() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine lineItem1 = new DeliveryDocumentLine();
    DeliveryDocumentLine lineItem2 = new DeliveryDocumentLine();
    DeliveryDocumentLine lineItem3 = new DeliveryDocumentLine();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose = new GdmDeliveryDocumentResponse();

    deliveryDocument.setPurchaseReferenceNumber("0294235326");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setVendorNumber("125486526");
    deliveryDocument.setDeptNumber("10");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoDCNumber("32899");
    deliveryDocument.setDeliveryStatus(DeliveryStatus.WRK);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));

    lineItem1.setGtin("0087876804154");
    lineItem1.setPurchaseReferenceLineNumber(1);
    lineItem1.setEvent("POS REPLEN");
    lineItem1.setWarehousePackSell(2.99f);
    lineItem1.setVendorPackCost(1.99f);
    lineItem1.setCurrency(null);
    lineItem1.setVendorPack(1);
    lineItem1.setWarehousePack(1);
    lineItem1.setQtyUOM("EA");
    lineItem1.setOpenQty(10);
    lineItem1.setTotalOrderQty(10);
    lineItem1.setOverageQtyLimit(5);
    lineItem1.setItemNbr(436617391l);
    lineItem1.setPurchaseRefType("CROSSU");
    lineItem1.setPalletHigh(2);
    lineItem1.setPalletTie(3);
    lineItem1.setWeight(1.25f);
    lineItem1.setWeightUom("LB");
    lineItem1.setCube(0f);
    lineItem1.setCubeUom("");
    lineItem1.setColor("WHITE");
    lineItem1.setSize("SMALL");
    lineItem1.setIsHazmat(Boolean.FALSE);
    lineItem1.setDescription("Sample item descr1");
    lineItem1.setSecondaryDescription("Sample item descr2");
    lineItem1.setIsConveyable(Boolean.FALSE);
    lineItem1.setItemType(null);

    ItemData itemData1 = new ItemData();
    itemData1.setWarehouseRotationTypeCode("1");
    itemData1.setWarehouseMinLifeRemainingToReceive(0);
    lineItem1.setAdditionalInfo(itemData1);

    lineItem2.setGtin("0087876804154");
    lineItem2.setPurchaseReferenceLineNumber(2);
    lineItem2.setEvent("POS REPLEN");
    lineItem2.setWarehousePackSell(2.99f);
    lineItem2.setVendorPackCost(1.99f);
    lineItem2.setCurrency(null);
    lineItem2.setVendorPack(1);
    lineItem2.setWarehousePack(1);
    lineItem2.setQtyUOM("EA");
    lineItem2.setOpenQty(10);
    lineItem2.setTotalOrderQty(10);
    lineItem2.setOverageQtyLimit(5);
    lineItem2.setItemNbr(436617392l);
    lineItem2.setPurchaseRefType("CROSSU");
    lineItem2.setPalletHigh(2);
    lineItem2.setPalletTie(3);
    lineItem2.setWeight(1.25f);
    lineItem2.setWeightUom("LB");
    lineItem2.setCube(0f);
    lineItem2.setCubeUom("");
    lineItem2.setColor("WHITE");
    lineItem2.setSize("SMALL");
    lineItem2.setIsHazmat(Boolean.FALSE);
    lineItem2.setDescription("Metal item descr1");
    lineItem2.setSecondaryDescription("Metal item descr2");
    lineItem2.setIsConveyable(Boolean.FALSE);
    lineItem2.setItemType(LabelCode.UN3090.getValue());

    ItemData itemData2 = new ItemData();
    itemData2.setWarehouseRotationTypeCode("2");
    itemData2.setWarehouseMinLifeRemainingToReceive(6);
    lineItem2.setAdditionalInfo(itemData2);

    lineItem3.setGtin("0087876804154");
    lineItem3.setPurchaseReferenceLineNumber(2);
    lineItem3.setEvent("POS REPLEN");
    lineItem3.setWarehousePackSell(2.99f);
    lineItem3.setVendorPackCost(1.99f);
    lineItem3.setCurrency(null);
    lineItem3.setVendorPack(1);
    lineItem3.setWarehousePack(1);
    lineItem3.setQtyUOM("EA");
    lineItem3.setOpenQty(10);
    lineItem3.setTotalOrderQty(10);
    lineItem3.setOverageQtyLimit(5);
    lineItem3.setItemNbr(436617392l);
    lineItem3.setPurchaseRefType("CROSSU");
    lineItem3.setPalletHigh(2);
    lineItem3.setPalletTie(3);
    lineItem3.setWeight(1.25f);
    lineItem3.setWeightUom("LB");
    lineItem3.setCube(0f);
    lineItem3.setCubeUom("");
    lineItem3.setColor("WHITE");
    lineItem3.setSize("SMALL");
    lineItem3.setIsHazmat(Boolean.FALSE);
    lineItem3.setDescription("Ion item descr1");
    lineItem3.setSecondaryDescription("Ion item descr2");
    lineItem3.setIsConveyable(Boolean.FALSE);
    lineItem3.setItemType(LabelCode.UN3480.getValue());

    ItemData itemData3 = new ItemData();
    itemData3.setWarehouseRotationTypeCode("3");
    itemData3.setWarehouseMinLifeRemainingToReceive(-4);
    lineItem3.setAdditionalInfo(itemData3);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(lineItem1);
    deliveryDocumentLines.add(lineItem2);
    deliveryDocumentLines.add(lineItem3);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    gdmDeliveryDocumentRespose.setDeliveryDocuments(deliveryDocuments);

    return gdmDeliveryDocumentRespose;
  }

  public static ShipmentResponseData getGdmContainerResponseWithChilds() {

    ShipmentResponseData shipmentResponseData = new ShipmentResponseData();
    ContainerResponseData gdmContainerResponse = new ContainerResponseData();
    ContainerItemResponseData containerItemResponseData = new ContainerItemResponseData();
    gdmContainerResponse.setLabel("00100077672010660414");
    // gdmContainerResponse.setAsnNumber("736795");
    shipmentResponseData.setBolNumber("0000010776736795");
    // gdmContainerResponse.setDeliveryNumber("20310001");
    // gdmContainerResponse.setIsPallet(Boolean.TRUE);
    // gdmContainerResponse.setDestBuNbr(7036);
    gdmContainerResponse.setDestinationNumber(7036);
    gdmContainerResponse.setDestinationCountryCode("US");
    gdmContainerResponse.setDestinationType("DC");
    // gdmContainerResponse.setWeight(222f);
    // gdmContainerResponse.setWeightUOM("lb");
    // gdmContainerResponse.setContainerCreatedTimeStamp(new Date());
    // Set<String> childContainerLabels = new HashSet<>();
    // childContainerLabels.add("00000077670099006775");
    // gdmContainerResponse.setChildContainerLabels(childContainerLabels);
    ContainerResponseData childContainerResponse = new ContainerResponseData();
    childContainerResponse.setLabel("00000077670099006775");
    // childContainerResponse.setAsnNumber("736795");
    // childContainerResponse.setBolNumber("0000010776736795");
    // childContainerResponse.setIsPallet(Boolean.FALSE);
    childContainerResponse.setChannel("S2S");
    // childContainerResponse.setInvoiceNumber("618437030");
    // childContainerResponse.setDestBuNbr(5091);
    childContainerResponse.setDestinationNumber(5091);
    childContainerResponse.setDestinationCountryCode("US");
    childContainerResponse.setDestinationType("STORE");
    childContainerResponse.setSourceType("FC");
    ContainerItemResponseData gdmContainerItemResponse = new ContainerItemResponseData();
    ContainerPOResponseData containerPOResponseData = new ContainerPOResponseData();
    containerPOResponseData.setPurchaseReferenceNumber("0618437030");
    containerPOResponseData.setPurchaseReferenceLineNumber(0);
    // containerPOResponseData.setPurchaseReferenceType("S2S");
    gdmContainerItemResponse.setItemNumber(0);
    gdmContainerItemResponse.setItemUpc("0752113654952");
    gdmContainerItemResponse.setItemQuantity(1);
    gdmContainerItemResponse.setQuantityUOM("EA");
    gdmContainerItemResponse.setPurchaseOrder(containerPOResponseData);
    List<ContainerResponseData> childContainers = new ArrayList<>();
    List<ContainerItemResponseData> containerItems = new ArrayList<>();
    containerItems.add(gdmContainerItemResponse);
    childContainerResponse.setItems(containerItems);
    childContainers.add(childContainerResponse);
    gdmContainerResponse.setContainers(childContainers);
    shipmentResponseData.setContainer(gdmContainerResponse);
    return shipmentResponseData;
  }

  public static ShipmentResponseData getGdmContainerResponse() {
    ShipmentResponseData shipmentResponseData = new ShipmentResponseData();
    shipmentResponseData.setBolNumber("0000010776736795");
    ContainerResponseData containerResponseData = new ContainerResponseData();
    ContainerResponseData childContainerResponse = new ContainerResponseData();
    ContainerItemResponseData containerItemResponseData = new ContainerItemResponseData();
    childContainerResponse.setLabel("00000077670099006775");
    // childContainerResponse.setAsnNumber("736795");
    // childContainerResponse.setIsPallet(Boolean.FALSE);
    childContainerResponse.setChannel("S2S");
    // childContainerResponse.setInvoiceNumber("618437030");
    // childContainerResponse.setDestBuNbr(5091);
    childContainerResponse.setDestinationNumber(5091);
    childContainerResponse.setDestinationCountryCode("US");
    childContainerResponse.setDestinationType("STORE");
    childContainerResponse.setSourceType("FC");
    ContainerPOResponseData containerPOResponseData = new ContainerPOResponseData();
    containerPOResponseData.setPurchaseReferenceNumber("0618437030");
    containerPOResponseData.setPurchaseReferenceLineNumber(0);
    // containerPOResponseData.setPurchaseReferenceType("S2S");
    containerItemResponseData.setItemNumber(0);
    containerItemResponseData.setItemUpc("0752113654952");
    containerItemResponseData.setItemQuantity(1);
    containerItemResponseData.setQuantityUOM("EA");
    containerItemResponseData.setPurchaseOrder(containerPOResponseData);
    List<ContainerResponseData> childContainers = new ArrayList<>();
    List<ContainerItemResponseData> containerItems = new ArrayList<>();
    containerItems.add(containerItemResponseData);
    childContainerResponse.setItems(containerItems);
    childContainers.add(childContainerResponse);
    containerResponseData.setLabel("00100077672010660414");
    // gdmContainerResponse.setAsnNumber("736795");
    // gdmContainerResponse.setBolNumber("0000010776736795");
    // gdmContainerResponse.setDeliveryNumber("20310001");
    // gdmContainerResponse.setIsPallet(Boolean.TRUE);
    // gdmContainerResponse.setDestBuNbr(7036);
    containerResponseData.setDestinationNumber(7036);
    containerResponseData.setDestinationCountryCode("US");
    containerResponseData.setDestinationType("DC");
    // containerResponseData.setWeight(222f);
    // containerResponseData.setWeightUOM("lb");
    // containerResponseData.setContainerCreatedTimeStamp(new Date());
    containerResponseData.setContainers(childContainers);
    ContainerItemResponseData gdmContainerItem = new ContainerItemResponseData();
    ContainerPOResponseData gdmContainerPoResponse = new ContainerPOResponseData();
    gdmContainerPoResponse.setPurchaseReferenceNumber("0618437030");
    gdmContainerPoResponse.setPurchaseReferenceLineNumber(0);
    // gdmContainerItem.setPurchaseReferenceType("S2S");
    gdmContainerItem.setItemNumber(0);
    gdmContainerItem.setItemUpc("0752113654952");
    gdmContainerItem.setItemQuantity(1);
    gdmContainerItem.setQuantityUOM("EA");
    gdmContainerItem.setPurchaseOrder(gdmContainerPoResponse);
    List<ContainerItemResponseData> items = new ArrayList<>();
    items.add(gdmContainerItem);
    containerResponseData.setItems(items);
    shipmentResponseData.setContainer(containerResponseData);
    return shipmentResponseData;
  }

  public static GdmDeliveryDocumentResponse getGdmResponseWithInvalidItemInfo() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose = new GdmDeliveryDocumentResponse();

    deliveryDocument.setPurchaseReferenceNumber("0294235326");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setVendorNumber("125486526");
    deliveryDocument.setDeptNumber("10");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoDCNumber("32899");
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));

    deliveryDocumentLine.setGtin("0087876804154");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(2.99f);
    deliveryDocumentLine.setVendorPackCost(1.99f);
    deliveryDocumentLine.setCurrency(null);
    deliveryDocumentLine.setVendorPack(1);
    deliveryDocumentLine.setWarehousePack(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(436617391l);
    deliveryDocumentLine.setPurchaseRefType("SSTKU");
    deliveryDocumentLine.setPalletHigh(2);
    deliveryDocumentLine.setPalletTie(3);
    deliveryDocumentLine.setWeight(1.25f);
    deliveryDocumentLine.setWeightUom("LB");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("WHITE");
    deliveryDocumentLine.setSize("SMALL");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("Sample item descr1");
    deliveryDocumentLine.setSecondaryDescription("Sample item descr2");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setItemType(null);
    deliveryDocumentLine.setWarehouseRotationTypeCode("3");
    deliveryDocumentLine.setWarehouseMinLifeRemainingToReceive(null);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    gdmDeliveryDocumentRespose.setDeliveryDocuments(deliveryDocuments);

    return gdmDeliveryDocumentRespose;
  }

  public static GdmDeliveryDocumentResponse getGdmRespWithoutProfiledWarehouseArea() {
    ItemData itemData = new ItemData();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose = new GdmDeliveryDocumentResponse();

    deliveryDocument.setPurchaseReferenceNumber("0294235326");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setGtin("0087876804154");
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(436617391l);
    deliveryDocumentLine.setPurchaseRefType("SSTKU");
    deliveryDocumentLine.setPalletHigh(2);
    deliveryDocumentLine.setPalletTie(3);
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setItemType(null);
    deliveryDocumentLine.setWarehouseRotationTypeCode("1");
    deliveryDocumentLine.setWarehouseMinLifeRemainingToReceive(0);
    itemData.setProfiledWarehouseArea(null);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocuments.add(deliveryDocument);
    gdmDeliveryDocumentRespose.setDeliveryDocuments(deliveryDocuments);

    return gdmDeliveryDocumentRespose;
  }

  public static GdmDeliveryDocumentResponse getGdmRespWithProfiledWarehouseArea() {
    ItemData itemData = new ItemData();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose = new GdmDeliveryDocumentResponse();

    deliveryDocument.setPurchaseReferenceNumber("0294235326");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setGtin("0087876804154");
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(436617391l);
    deliveryDocumentLine.setPurchaseRefType("SSTKU");
    deliveryDocumentLine.setPalletHigh(2);
    deliveryDocumentLine.setPalletTie(3);
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setItemType(null);
    deliveryDocumentLine.setWarehouseRotationTypeCode("1");
    deliveryDocumentLine.setWarehouseMinLifeRemainingToReceive(0);
    itemData.setProfiledWarehouseArea("CPS");
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocuments.add(deliveryDocument);
    gdmDeliveryDocumentRespose.setDeliveryDocuments(deliveryDocuments);

    return gdmDeliveryDocumentRespose;
  }

  public static PurchaseOrderLine getMockPurchaseOrderLine() {

    Whpk whpk = new Whpk();
    whpk.setSell(2.99);
    whpk.setQuantity(1);

    Vnpk vnpk = new Vnpk();
    vnpk.setCost(1.99f);
    vnpk.setQuantity(1);
    vnpk.setWeight(PropertyDetail.builder().quantity(1.25f).uom("LB").build());
    vnpk.setCube(PropertyDetail.builder().quantity(0f).uom("").build());

    ItemDetails itemDetails = new ItemDetails();
    itemDetails.setConsumableGTIN("0087876804154");
    itemDetails.setOrderableGTIN("0087876804154");
    itemDetails.setNumber(436617391l);
    itemDetails.setPalletHi(2);
    itemDetails.setPalletTi(3);
    itemDetails.setColor("WHITE");
    itemDetails.setSize("SMALL");
    itemDetails.setHazmat(Boolean.FALSE);
    itemDetails.setDescriptions(Arrays.asList("Sample item descr1", "Sample item descr2"));
    itemDetails.setConveyable(Boolean.FALSE);
    itemDetails.setItemTypeCode(null);

    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    purchaseOrderLine.setPoLineNumber(1);
    purchaseOrderLine.setEvent("POS REPLEN");
    purchaseOrderLine.setOrdered(new QuantityDetail("EA", 10));
    purchaseOrderLine.setOpenQuantity(10);
    purchaseOrderLine.setOvgThresholdLimit(new QuantityDetail("EA", 5));
    purchaseOrderLine.setChannel("CROSSU");
    purchaseOrderLine.setItemDetails(itemDetails);
    purchaseOrderLine.setVnpk(vnpk);
    purchaseOrderLine.setWhpk(whpk);

    return purchaseOrderLine;
  }

  public static DeliveryDtls getGdmDeliveryDocumentResponseForPoCon() {
    DeliveryDtls deliveryDtls = new DeliveryDtls();

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine lineItem1 = new DeliveryDocumentLine();
    DeliveryDocumentLine lineItem2 = new DeliveryDocumentLine();
    DeliveryDocumentLine lineItem3 = new DeliveryDocumentLine();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose = new GdmDeliveryDocumentResponse();

    deliveryDocument.setPurchaseReferenceNumber("0294235326");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setVendorNumber("125486526");
    deliveryDocument.setDeptNumber("10");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoDCNumber("32899");
    deliveryDocument.setWeight(202f);
    deliveryDocument.setCubeQty(201.1f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));

    lineItem1.setGtin("00878768041546");
    lineItem1.setPurchaseReferenceLineNumber(1);
    lineItem1.setEvent("POS REPLEN");
    lineItem1.setWarehousePackSell(2.99f);
    lineItem1.setVendorPackCost(1.99f);
    lineItem1.setCurrency(null);
    lineItem1.setVendorPack(1);
    lineItem1.setWarehousePack(1);
    lineItem1.setQtyUOM("EA");
    lineItem1.setOpenQty(10);
    lineItem1.setTotalOrderQty(10);
    lineItem1.setOverageQtyLimit(5);
    lineItem1.setItemNbr(436617391l);
    lineItem1.setPurchaseRefType("POCON");
    lineItem1.setPalletHigh(2);
    lineItem1.setPalletTie(3);
    lineItem1.setWeight(1.25f);
    lineItem1.setWeightUom("LB");
    lineItem1.setCube(0f);
    lineItem1.setCubeUom("");
    lineItem1.setColor("WHITE");
    lineItem1.setSize("SMALL");
    lineItem1.setIsHazmat(Boolean.FALSE);
    lineItem1.setDescription("Sample item descr1");
    lineItem1.setSecondaryDescription("Sample item descr2");
    lineItem1.setIsConveyable(Boolean.FALSE);
    lineItem1.setItemType(null);
    lineItem1.setWarehouseRotationTypeCode("1");
    lineItem1.setWarehouseMinLifeRemainingToReceive(0);
    lineItem1.setOriginalChannel("SSTKU");

    lineItem2.setGtin("00878768041547");
    lineItem2.setPurchaseReferenceLineNumber(2);
    lineItem2.setEvent("POS REPLEN");
    lineItem2.setWarehousePackSell(2.99f);
    lineItem2.setVendorPackCost(1.99f);
    lineItem2.setCurrency(null);
    lineItem2.setVendorPack(1);
    lineItem2.setWarehousePack(1);
    lineItem2.setQtyUOM("EA");
    lineItem2.setOpenQty(10);
    lineItem2.setTotalOrderQty(10);
    lineItem2.setOverageQtyLimit(5);
    lineItem2.setItemNbr(436617392l);
    lineItem2.setPurchaseRefType("POCON");
    lineItem2.setPalletHigh(2);
    lineItem2.setPalletTie(3);
    lineItem2.setWeight(1.25f);
    lineItem2.setWeightUom("LB");
    lineItem2.setCube(0f);
    lineItem2.setCubeUom("");
    lineItem2.setColor("WHITE");
    lineItem2.setSize("SMALL");
    lineItem2.setIsHazmat(Boolean.FALSE);
    lineItem2.setDescription("Metal item descr1");
    lineItem2.setSecondaryDescription("Metal item descr2");
    lineItem2.setIsConveyable(Boolean.FALSE);
    lineItem2.setItemType(LabelCode.UN3090.getValue());
    lineItem2.setWarehouseRotationTypeCode("2");
    lineItem2.setWarehouseMinLifeRemainingToReceive(6);
    lineItem2.setOriginalChannel("CROSSU");

    lineItem3.setGtin("00878768041548");
    lineItem3.setPurchaseReferenceLineNumber(2);
    lineItem3.setEvent("POS REPLEN");
    lineItem3.setWarehousePackSell(2.99f);
    lineItem3.setVendorPackCost(1.99f);
    lineItem3.setCurrency(null);
    lineItem3.setVendorPack(1);
    lineItem3.setWarehousePack(1);
    lineItem3.setQtyUOM("EA");
    lineItem3.setOpenQty(10);
    lineItem3.setTotalOrderQty(10);
    lineItem3.setOverageQtyLimit(5);
    lineItem3.setItemNbr(436617392l);
    lineItem3.setPurchaseRefType("POCON");
    lineItem3.setPalletHigh(2);
    lineItem3.setPalletTie(3);
    lineItem3.setWeight(1.25f);
    lineItem3.setWeightUom("LB");
    lineItem3.setCube(0f);
    lineItem3.setCubeUom("");
    lineItem3.setColor("WHITE");
    lineItem3.setSize("SMALL");
    lineItem3.setIsHazmat(Boolean.FALSE);
    lineItem3.setDescription("Ion item descr1");
    lineItem3.setSecondaryDescription("Ion item descr2");
    lineItem3.setIsConveyable(Boolean.FALSE);
    lineItem3.setItemType(LabelCode.UN3480.getValue());
    lineItem3.setWarehouseRotationTypeCode("3");
    lineItem3.setWarehouseMinLifeRemainingToReceive(-4);
    lineItem3.setOriginalChannel("SSTKU");

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(lineItem1);
    deliveryDocumentLines.add(lineItem2);
    deliveryDocumentLines.add(lineItem3);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    gdmDeliveryDocumentRespose.setDeliveryDocuments(deliveryDocuments);

    deliveryDtls.setDeliveryDocuments(deliveryDocuments);

    return deliveryDtls;
  }

  public static DeliveryDtls getGdmDeliveryDocumentResponseForPoConwithUnkownChannelMethod() {
    DeliveryDtls deliveryDtls = new DeliveryDtls();

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine lineItem1 = new DeliveryDocumentLine();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose = new GdmDeliveryDocumentResponse();

    deliveryDocument.setPurchaseReferenceNumber("0294235326");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setVendorNumber("125486526");
    deliveryDocument.setDeptNumber("10");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoDCNumber("32899");
    deliveryDocument.setWeight(202f);
    deliveryDocument.setCubeQty(201.1f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");

    lineItem1.setGtin("00878768041546");
    lineItem1.setPurchaseReferenceLineNumber(1);
    lineItem1.setEvent("POS REPLEN");
    lineItem1.setWarehousePackSell(2.99f);
    lineItem1.setVendorPackCost(1.99f);
    lineItem1.setCurrency(null);
    lineItem1.setVendorPack(1);
    lineItem1.setWarehousePack(1);
    lineItem1.setQtyUOM("EA");
    lineItem1.setOpenQty(10);
    lineItem1.setTotalOrderQty(10);
    lineItem1.setOverageQtyLimit(5);
    lineItem1.setItemNbr(436617391l);
    lineItem1.setPurchaseRefType("POCON");
    lineItem1.setPalletHigh(2);
    lineItem1.setPalletTie(3);
    lineItem1.setWeight(1.25f);
    lineItem1.setWeightUom("LB");
    lineItem1.setCube(0f);
    lineItem1.setCubeUom("");
    lineItem1.setColor("WHITE");
    lineItem1.setSize("SMALL");
    lineItem1.setIsHazmat(Boolean.FALSE);
    lineItem1.setDescription("Sample item descr1");
    lineItem1.setSecondaryDescription("Sample item descr2");
    lineItem1.setIsConveyable(Boolean.FALSE);
    lineItem1.setItemType(null);
    lineItem1.setWarehouseRotationTypeCode("1");
    lineItem1.setWarehouseMinLifeRemainingToReceive(0);
    lineItem1.setOriginalChannel("UN");
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(lineItem1);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    gdmDeliveryDocumentRespose.setDeliveryDocuments(deliveryDocuments);
    deliveryDtls.setDeliveryDocuments(deliveryDocuments);
    return deliveryDtls;
  }

  public static GdmPOLineResponse getRejectedLine() {
    DeliveryDocumentLine deliveryDocLine = new DeliveryDocumentLine();
    deliveryDocLine.setPurchaseReferenceNumber("0294235326");
    deliveryDocLine.setGtin("0087876804154");
    deliveryDocLine.setPurchaseReferenceLineNumber(1);
    deliveryDocLine.setOpenQty(100);
    deliveryDocLine.setTotalOrderQty(100);
    deliveryDocLine.setOverageQtyLimit(5);
    deliveryDocLine.setItemNbr(436617391l);
    deliveryDocLine.setPurchaseRefType("SSTKU");
    deliveryDocLine.setPalletHigh(2);
    deliveryDocLine.setPalletTie(3);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWarehouseRotationTypeCode("1");
    additionalInfo.setWarehouseMinLifeRemainingToReceive(0);
    additionalInfo.setWarehouseAreaCode("2");
    additionalInfo.setProfiledWarehouseArea("CPF");
    deliveryDocLine.setAdditionalInfo(additionalInfo);

    OperationalInfo operationalInfo = new OperationalInfo();
    operationalInfo.setState("REJECTED");
    deliveryDocLine.setOperationalInfo(operationalInfo);

    List<DeliveryDocumentLine> deliveryDocLines = new ArrayList<DeliveryDocumentLine>();
    deliveryDocLines.add(deliveryDocLine);

    DeliveryDocument deliveryDoc = new DeliveryDocument();
    deliveryDoc.setPurchaseReferenceNumber("0294235326");
    deliveryDoc.setPurchaseCompanyId("1");
    deliveryDoc.setDeliveryDocumentLines(deliveryDocLines);

    List<DeliveryDocument> deliveryDocs = new ArrayList<DeliveryDocument>();
    deliveryDocs.add(deliveryDoc);

    GdmPOLineResponse gdmRejectedLine = new GdmPOLineResponse();
    gdmRejectedLine.setDeliveryNumber(Long.valueOf("3273873232"));
    gdmRejectedLine.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmRejectedLine.setDeliveryDocuments(deliveryDocs);

    return gdmRejectedLine;
  }

  public static GdmPOLineResponse getPOLine() {
    DeliveryDocumentLine deliveryDocLine = new DeliveryDocumentLine();
    deliveryDocLine.setPurchaseReferenceNumber("34734743");
    deliveryDocLine.setGtin("0087876804154");
    deliveryDocLine.setPurchaseReferenceLineNumber(1);
    deliveryDocLine.setOpenQty(100);
    deliveryDocLine.setTotalOrderQty(100);
    deliveryDocLine.setOverageQtyLimit(5);
    deliveryDocLine.setItemNbr(436617391l);
    deliveryDocLine.setPurchaseRefType("SSTKU");
    deliveryDocLine.setPalletHigh(2);
    deliveryDocLine.setPalletTie(3);
    deliveryDocLine.setFreightBillQty(810);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWarehouseRotationTypeCode("1");
    additionalInfo.setWarehouseMinLifeRemainingToReceive(0);
    additionalInfo.setWarehouseAreaCode("2");
    additionalInfo.setProfiledWarehouseArea("CPF");
    deliveryDocLine.setAdditionalInfo(additionalInfo);

    List<DeliveryDocumentLine> deliveryDocLines = new ArrayList<DeliveryDocumentLine>();
    deliveryDocLines.add(deliveryDocLine);

    DeliveryDocument deliveryDoc = new DeliveryDocument();
    deliveryDoc.setPurchaseReferenceNumber("34734743");
    deliveryDoc.setPurchaseCompanyId("1");
    deliveryDoc.setDeliveryDocumentLines(deliveryDocLines);
    deliveryDoc.setFreightTermCode("PRP");
    deliveryDoc.setTotalBolFbq(810);

    List<DeliveryDocument> deliveryDocs = new ArrayList<DeliveryDocument>();
    deliveryDocs.add(deliveryDoc);

    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryNumber(Long.valueOf("3273873232"));
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocs);
    gdmPOLineResponse.setCarrierCode("Shane Carter");
    gdmPOLineResponse.setTrailerId("104681");

    return gdmPOLineResponse;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK_AtlasConvertedItem()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_AtlasConvertedItem.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static String getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse()
      throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_IncludeDummyPo.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponseNoAsn()
      throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_NoAsn.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getDeliveryDetailsByUriIncludesDummyPoReturnsEmptyResponse()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_EmptyDeliveryDocuments.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getDeliveryHistoryReturnsSuccessResponse() throws IOException {
    File resource = new ClassPathResource("gdm_v1_history_response.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getPurchaseOrder() throws IOException {
    File resource = new ClassPathResource("EndgamePurchaseOrder.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getDeliveryHistoryReturnsBillNotSignedResponse() throws IOException {
    File resource = new ClassPathResource("gdm_v1_history_bill_not_signed.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForDA_NonAtlasItem() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_DA_NonAtlasItem.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static GdmDeliveryHistoryResponse getGdmDeliveryHistoryValidFinalisedDate() {
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse = new GdmDeliveryHistoryResponse();
    DeliveryEvent deliveryEvent = new DeliveryEvent();
    deliveryEvent.setEvent(EVENT_TYPE_FINALIZED);
    Date validDate = new Date();
    deliveryEvent.setTimestamp(validDate);
    gdmDeliveryHistoryResponse.setDeliveryEvents(Arrays.asList(deliveryEvent));
    return gdmDeliveryHistoryResponse;
  }

  public static GdmDeliveryHistoryResponse getGdmDeliveryHistoryInValidFinalisedDate() {
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse = new GdmDeliveryHistoryResponse();
    DeliveryEvent deliveryEvent = new DeliveryEvent();
    deliveryEvent.setEvent(EVENT_TYPE_FINALIZED);
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MONTH, -1);
    Date inValidDate = cal.getTime();
    deliveryEvent.setTimestamp(inValidDate);
    gdmDeliveryHistoryResponse.setDeliveryEvents(Arrays.asList(deliveryEvent));
    return gdmDeliveryHistoryResponse;
  }

  public static GdmDeliveryHistoryResponse getGdmDeliveryHistoryBillSigned() {
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse = new GdmDeliveryHistoryResponse();
    DeliveryEvent deliveryEvent = new DeliveryEvent();
    deliveryEvent.setEvent(EVENT_TYPE_BILL_SIGNED);
    Date validDate = new Date();
    deliveryEvent.setTimestamp(validDate);
    gdmDeliveryHistoryResponse.setDeliveryEvents(Arrays.asList(deliveryEvent));
    return gdmDeliveryHistoryResponse;
  }

  public static GdmDeliveryHistoryResponse getGdmDeliveryHistoryValidFinalisedDateAndBillSigned() {
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse = new GdmDeliveryHistoryResponse();
    List<DeliveryEvent> deliveryEvents = new ArrayList<DeliveryEvent>();
    DeliveryEvent deliveryEvent = new DeliveryEvent();
    deliveryEvent.setEvent(EVENT_TYPE_FINALIZED);
    Date validDate = new Date();
    deliveryEvent.setTimestamp(validDate);
    deliveryEvents.add(deliveryEvent);
    DeliveryEvent deliveryEventBillSigned = new DeliveryEvent();
    deliveryEventBillSigned.setEvent(EVENT_TYPE_BILL_SIGNED);
    deliveryEvent.setTimestamp(validDate);
    deliveryEvents.add(deliveryEventBillSigned);
    gdmDeliveryHistoryResponse.setDeliveryEvents(deliveryEvents);
    return gdmDeliveryHistoryResponse;
  }
}
