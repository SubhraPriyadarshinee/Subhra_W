package com.walmart.move.nim.receiving.endgame.mock.data;

import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockMessageData {

  public static ScanEventData getMockScanEventData() {
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setCaseUPC("12333334");
    scanEventData.setDeliveryNumber(12333333L);
    scanEventData.setDiverted(DivertStatus.DECANT);
    scanEventData.setDoorNumber("101");
    scanEventData.setTrailerCaseLabel("TC00000001");
    scanEventData.setFacilityCountryCode("US");
    scanEventData.setFacilityNum(32987);
    scanEventData.setPurchaseOrder(getPurchaseOrders());
    scanEventData.setPoNumbers(Arrays.asList("1901300600", "4301481GDM"));
    scanEventData.setBoxIds(Collections.singletonList("R1627901835661834"));
    return scanEventData;
  }

  public static ScanEventData getMockScanEventDataWithEmptyPONumbersList() {
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setCaseUPC("12333334");
    scanEventData.setDeliveryNumber(12333333L);
    scanEventData.setDiverted(DivertStatus.DECANT);
    scanEventData.setDoorNumber("101");
    scanEventData.setTrailerCaseLabel("TC00000001");
    scanEventData.setFacilityCountryCode("US");
    scanEventData.setFacilityNum(32987);
    scanEventData.setPoNumbers(Arrays.asList());
    return scanEventData;
  }
  public static ScanEventData getMockScanEventDataSscc() {
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setCaseUPC("12333334");
    scanEventData.setDeliveryNumber(12333333L);
    scanEventData.setDiverted(DivertStatus.DECANT);
    scanEventData.setDoorNumber("101");
    scanEventData.setTrailerCaseLabel("TC00000001");
    scanEventData.setFacilityCountryCode("US");
    scanEventData.setFacilityNum(32987);
    scanEventData.setPurchaseOrder(getPurchaseOrders());
    scanEventData.setPoNumbers(Arrays.asList("1901300600", "4301481GDM"));
    return scanEventData;
  }

  public static ScanEventData getMockScanEventDataWithOutPONumbersField() {
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setCaseUPC("12333334");
    scanEventData.setDeliveryNumber(12333333L);
    scanEventData.setDiverted(DivertStatus.DECANT);
    scanEventData.setDoorNumber("101");
    scanEventData.setTrailerCaseLabel("TC00000001");
    scanEventData.setFacilityCountryCode("US");
    scanEventData.setFacilityNum(32987);
    return scanEventData;
  }

  public static ReceivingRequest getMockReceivingRequestDataForQA() {
    ReceivingRequest receivingRequest = new ReceivingRequest();
    receivingRequest.setCaseUPC("12333334");
    receivingRequest.setDeliveryNumber(12333333L);
    receivingRequest.setDiverted(DivertStatus.QA);
    receivingRequest.setDoorNumber("101");
    receivingRequest.setTrailerCaseLabel("TC00000001");
    receivingRequest.setFacilityCountryCode("US");
    receivingRequest.setFacilityNum(32987);
    receivingRequest.setIsMultiSKU(false);
    receivingRequest.setPoNumbers(Arrays.asList("1901300600", "4301481GDM"));
    return receivingRequest;
  }

  public static ReceivingRequest getScanEventDataWithDimensions() {
    ReceivingRequest receivingRequest = new ReceivingRequest();
    receivingRequest.setCaseUPC("12333334");
    receivingRequest.setDeliveryNumber(12333333L);
    receivingRequest.setDiverted(DivertStatus.DECANT);
    receivingRequest.setDoorNumber("101");
    receivingRequest.setTrailerCaseLabel("TC00000001");
    receivingRequest.setFacilityCountryCode("US");
    receivingRequest.setFacilityNum(32987);
    receivingRequest.setPoNumbers(Arrays.asList("1901300600", "4301481GDM"));

    Dimensions dimensions = new Dimensions();
    dimensions.setHeight(1.2);
    dimensions.setLength(1.2);
    dimensions.setWidth(1.2);
    receivingRequest.setDimensions(dimensions);
    receivingRequest.setDimensionsUnitOfMeasure("IN");
    receivingRequest.setIsMultiSKU(false);
    return receivingRequest;
  }

  public static Map<String, byte[]> getMockKafkaListenerHeaders() {
    String facilityNum = "32897";
    String facilityCountryCode = "US";
    Map<String, byte[]> mapHeaders = new HashMap<>();
    mapHeaders.put(EndgameConstants.TENENT_COUNTRY_CODE, facilityCountryCode.getBytes());
    mapHeaders.put(EndgameConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    return mapHeaders;
  }

  public static UpdateAttributesData getMockAttributeUpdateListenerData(
      boolean isFTS, boolean isExpired) {
    SearchCriteria searchCriteria =
        SearchCriteria.builder()
            .deliveryNumber(12333333L)
            .itemNumber("561298341")
            .itemUPC("00029695410987")
            .baseDivisionCode("WM")
            .financialReportingGroup("US")
            .caseUPC("100029695410987")
            .trackingId("TC00000001")
            .build();
    UpdateAttributes updateAttributes = null;

    ItemAttributes itemAttributes = null;

    if (isFTS) {
      itemAttributes = ItemAttributes.builder().isNewItem(Boolean.FALSE).build();
    } else {
      if (isExpired) {
        updateAttributes =
            UpdateAttributes.builder()
                .rotateDate("2019-02-12T00:00:00.000Z")
                .isExpired(true)
                .build();
      } else {
        updateAttributes =
            UpdateAttributes.builder().rotateDate("2019-02-12T00:00:00.000Z").build();
      }
    }

    return UpdateAttributesData.builder()
        .searchCriteria(searchCriteria)
        .updateAttributes(updateAttributes)
        .itemAttributes(itemAttributes)
        .build();
  }

  public static UpdateAttributesData getMockExpiryDateUpdateListenerDataWithoutDeliveryNumber() {
    SearchCriteria searchCriteria =
        SearchCriteria.builder()
            .itemNumber("561298341")
            .itemUPC("00029695410987")
            .baseDivisionCode("WM")
            .financialReportingGroup("US")
            .trackingId("TC00000001")
            .build();
    UpdateAttributes updateAttributes =
        UpdateAttributes.builder().rotateDate("2019-02-12T00:00:00.000Z").build();
    return UpdateAttributesData.builder()
        .searchCriteria(searchCriteria)
        .updateAttributes(updateAttributes)
        .build();
  }

  public static UpdateAttributesData getMockExpiryDateUpdateListenerDataWithoutTrackingId() {
    SearchCriteria searchCriteria =
        SearchCriteria.builder()
            .deliveryNumber(12333333L)
            .itemNumber("561298341")
            .itemUPC("00029695410987")
            .baseDivisionCode("WM")
            .financialReportingGroup("US")
            .build();
    UpdateAttributes updateAttributes =
        UpdateAttributes.builder().rotateDate("2019-02-12T00:00:00.000Z").build();
    return UpdateAttributesData.builder()
        .searchCriteria(searchCriteria)
        .updateAttributes(updateAttributes)
        .build();
  }

  public static SearchCriteria getMockSearchCriteria() {
    return SearchCriteria.builder()
        .deliveryNumber(12333333L)
        .itemNumber("561298341")
        .itemUPC("00078742229430")
        .baseDivisionCode("WM")
        .financialReportingGroup("US")
        .caseUPC("20078742229434")
        .trackingId("TC00000001")
        .build();
  }

  public static UpdateAttributesData getMockAttributeUpdateListenerDataWithoutItemUPCAndCaseUPC(
      boolean isFTS, boolean isExpired) {
    SearchCriteria searchCriteria =
        SearchCriteria.builder()
            .deliveryNumber(12333333L)
            .itemNumber("561298341")
            .baseDivisionCode("WM")
            .financialReportingGroup("US")
            .build();
    UpdateAttributes updateAttributes = null;

    ItemAttributes itemAttributes = null;

    if (isFTS) {
      itemAttributes = ItemAttributes.builder().isNewItem(Boolean.FALSE).build();
    } else {
      if (isExpired) {
        updateAttributes =
            UpdateAttributes.builder()
                .rotateDate("2019-02-12T00:00:00.000Z")
                .isExpired(true)
                .build();
      } else {
        updateAttributes =
            UpdateAttributes.builder().rotateDate("2019-02-12T00:00:00.000Z").build();
      }
    }
    return UpdateAttributesData.builder()
        .searchCriteria(searchCriteria)
        .updateAttributes(updateAttributes)
        .itemAttributes(itemAttributes)
        .build();
  }

  public static UpdateAttributes getMockUpdateAttributes() {
    return UpdateAttributes.builder()
        .rotateDate("2019-02-12T00:00:00.000Z")
        .isExpired(false)
        .build();
  }

  public static ExpiryDateUpdatePublisherData getMockExpiryDateUpdatePublisherData() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("TC00000001");
    SearchCriteria searchCriteria =
        SearchCriteria.builder()
            .itemNumber("561298341")
            .itemUPC("00029695410987")
            .baseDivisionCode("WM")
            .financialReportingGroup("US")
            .trackingIds(trackingIds)
            .build();
    UpdateAttributes updateAttributes =
        UpdateAttributes.builder().rotateDate("2019-02-12T00:00:00.000Z").build();
    return ExpiryDateUpdatePublisherData.builder()
        .searchCriteria(searchCriteria)
        .updateAttributes(updateAttributes)
        .build();
  }

  public static DeliveryInfo getDeliveryInfo(
      long deliveryNumber, String doorNumber, String trailerNumber, DeliveryStatus deliveryStatus) {

    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setTs(new Date());
    deliveryInfo.setDoorNumber(doorNumber);
    deliveryInfo.setTrailerNumber(trailerNumber);
    deliveryInfo.setDeliveryStatus(deliveryStatus.name());
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    return deliveryInfo;
  }

  public static ReceivingRequest getMockReceivingRequest() {
    ReceivingRequest receivingRequest = new ReceivingRequest();
    receivingRequest.setCaseUPC("12333334");
    receivingRequest.setDeliveryNumber(12333333L);
    receivingRequest.setDiverted(DivertStatus.DECANT);
    receivingRequest.setDoorNumber("101");
    receivingRequest.setTrailerCaseLabel("TC00000001");
    receivingRequest.setFacilityCountryCode("US");
    receivingRequest.setFacilityNum(32987);
    receivingRequest.setIsMultiSKU(true);
    receivingRequest.setQuantity(2);
    receivingRequest.setQuantityUOM("EA");
    return receivingRequest;
  }

  public static EndgameReceivingRequest getMockEndgameReceivingRequest() {
    EndgameReceivingRequest receivingRequest = new EndgameReceivingRequest();
    receivingRequest.setCaseUPC("12333334");
    receivingRequest.setDeliveryNumber(12333333L);
    receivingRequest.setDiverted(DivertStatus.DECANT);
    receivingRequest.setDoorNumber("101");
    receivingRequest.setTrackingId("TC00000001");
    receivingRequest.setFacilityCountryCode("US");
    receivingRequest.setFacilityNum(32987);
    receivingRequest.setQuantity(2);
    receivingRequest.setQuantityUOM("EA");
    receivingRequest.setPurchaseOrder(getPurchaseOrders());
    return receivingRequest;
  }

  private static PurchaseOrder getPurchaseOrders() {
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    PurchaseOrderLine purchaseOrderLine  = new PurchaseOrderLine();
    purchaseOrderLine.setSscc("12345");
    purchaseOrder.setLines(Collections.singletonList(purchaseOrderLine));
    return purchaseOrder;
  }

  public static ReceivingRequest getAuditCaseMockReceivingRequest(
      int qty, double weight, String requestOriginator) {
    ReceivingRequest receivingRequest = new ReceivingRequest();
    receivingRequest.setCaseUPC("12333334");
    receivingRequest.setDeliveryNumber(12333333L);
    receivingRequest.setDiverted(DivertStatus.DECANT);
    receivingRequest.setDoorNumber("101");
    receivingRequest.setTrailerCaseLabel("TC00000001");
    receivingRequest.setFacilityCountryCode("US");
    receivingRequest.setFacilityNum(32987);
    receivingRequest.setIsMultiSKU(true);
    receivingRequest.setQuantity(qty);
    receivingRequest.setQuantityUOM("EA");
    receivingRequest.setWeight(weight);
    receivingRequest.setWeightUnitOfMeasure("Grams");
    receivingRequest.setRequestOriginator(requestOriginator);
    receivingRequest.setPreLabelData(new PreLabelData());
    return receivingRequest;
  }
}
