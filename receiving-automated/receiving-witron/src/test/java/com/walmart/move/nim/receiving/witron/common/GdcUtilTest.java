package com.walmart.move.nim.receiving.witron.common;

import static com.walmart.move.nim.receiving.witron.common.GdcUtil.convertDateToUTCZone;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.testng.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.PoLine;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class GdcUtilTest {
  private List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocumentsForDSDC();

  @Test
  public void isValidInventoryAdjustment_validContainerUpdateEvent() {
    final JsonParser jsonParser = new JsonParser();
    String validInvJson =
        "{\n"
            + "    \"id\": \"aa826b34-237b-40cd-b172-af3d27b7761c\",\n"
            + "    \"correlationId\": \"8519a2b3-a26d-42ad-a752-dd4b904b84fa\",\n"
            + "    \"user\": \"leanna\",\n"
            + "    \"event\": \"container.updated\",\n"
            + "    \"occurredOn\": \"10 Nov 2020 17:39:35.838+0000\",\n"
            + "    \"eventObject\": {\n"
            + "        \"trackingId\": \"B32612000022764292\",\n"
            + "        \"containerId\": 0,\n"
            + "        \"containerTypeAbbr\": \"Chep Pallet\",\n"
            + "        \"countryCode\": \"US\",\n"
            + "        \"dcNumber\": 8852,\n"
            + "        \"destinationLocationId\": 0,\n"
            + "        \"itemList\": [\n"
            + "            {\n"
            + "                \"itemNumber\": 556386040,\n"
            + "                \"itemUPC\": \"00078742126654\",\n"
            + "                \"purchaseCompanyId\": 1,\n"
            + "                \"financialReportingGroup\": \"US\",\n"
            + "                \"baseDivisionCode\": \"WM\",\n"
            + "                \"vendorPkRatio\": 10,\n"
            + "                \"warehousePkRatio\": 10,\n"
            + "                \"unitOfMeasurement\": \"EACHES\",\n"
            + "                \"availabletosellQty\": 0,\n"
            + "                \"availableNonPickableQty\": 0,\n"
            + "                \"claimsQty\": 0,\n"
            + "                \"allocatedQty\": 0,\n"
            + "                \"frozenQty\": 0,\n"
            + "                \"onorderQty\": 0,\n"
            + "                \"pickedQty\": 0,\n"
            + "                \"intransitQty\": 0,\n"
            + "                \"problemfreightQty\": 0,\n"
            + "                \"receivedQty\": 0,\n"
            + "                \"workInProgressQty\": 0,\n"
            + "                \"qualitycontrolQty\": 0,\n"
            + "                \"itemStatusChange\": {\n"
            + "                    \"availabletosellQty\": -2440\n"
            + "                },\n"
            + "                \"poType\": \"SSTKU\",\n"
            + "                \"channelType\": \"SSTKU\",\n"
            + "                \"whsePackSell\": {\n"
            + "                    \"value\": 33.6,\n"
            + "                    \"uom\": \"USD\"\n"
            + "                },\n"
            + "                \"adjustmentTO\": {\n"
            + "                    \"reasonCode\": 52,\n"
            + "                    \"reasonDesc\": \"Receiving Correction\",\n"
            + "                    \"value\": -100,\n"
            + "                    \"uom\": \"EACHES\"\n"
            + "                },\n"
            + "                \"orderDetails\": [],\n"
            + "                \"poDetails\": [\n"
            + "                    {\n"
            + "                        \"poNum\": \"1007916906\",\n"
            + "                        \"purchaseReferenceLineNumber\": 9,\n"
            + "                        \"poQty\": 2440\n"
            + "                    }\n"
            + "                ],\n"
            + "                \"purchaseReferenceLineNumbers\": [\n"
            + "                    9\n"
            + "                ],\n"
            + "                \"rotateDate\": \"06 Nov 2020 00:00:00.000+0000\",\n"
            + "                \"promoBuyInd\": \"N\",\n"
            + "                \"weightFormatType\": \"F\",\n"
            + "                \"ossIndicator\": \"N\",\n"
            + "                \"receivedDate\": \"07 Nov 2020 00:00:00.000+0000\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"childContainers\": [],\n"
            + "        \"rotateDates\": [\n"
            + "            \"06 Nov 2020 00:00:00.000+0000\"\n"
            + "        ],\n"
            + "        \"lotNumbers\": [],\n"
            + "        \"poNums\": [\n"
            + "            \"1007916906\"\n"
            + "        ],\n"
            + "        \"isNetItemWeightCalc\": true,\n"
            + "        \"itemWeightUOM\": \"LB\",\n"
            + "        \"itemNetWeight\": 0.0,\n"
            + "        \"isContainerCubeCalc\": true,\n"
            + "        \"containerCubeUOM\": \"CF\",\n"
            + "        \"containerCube\": 0.0,\n"
            + "        \"containerCreatedDate\": \"07 Nov 2020 04:27:39.250+0000\",\n"
            + "        \"containerStatus\": \"AVAILABLE\",\n"
            + "        \"isShippable\": false,\n"
            + "        \"isReusable\": false,\n"
            + "        \"deliveryNumber\": \"20782789\",\n"
            + "        \"containerUpdatedDate\": \"07 Nov 2020 04:27:39.250+0000\",\n"
            + "        \"locationName\": \"405\",\n"
            + "        \"locationOrgUnitId\": 0\n"
            + "    }\n"
            + "}";
    JsonObject jsonObject = jsonParser.parse(validInvJson).getAsJsonObject();
    assertTrue(GdcUtil.isValidInventoryAdjustment(jsonObject));
  }

  @Test
  public void isValidInventoryAdjustment_missing_containerUpdated() {
    String inValidInvJson1_missing_containerUpdated =
        "{\n"
            + "    \"id\": \"aa826b34-237b-40cd-b172-af3d27b7761c\",\n"
            + "    \"correlationId\": \"8519a2b3-a26d-42ad-a752-dd4b904b84fa\",\n"
            + "    \"user\": \"leanna\",\n"
            +
            // "    \"event\": \"container.updated\",\n" +
            "    \"occurredOn\": \"10 Nov 2020 17:39:35.838+0000\",\n"
            + "    \"eventObject\": {\n"
            + "        \"trackingId\": \"B32612000022764292\",\n"
            + "        \"containerId\": 0,\n"
            + "        \"containerTypeAbbr\": \"Chep Pallet\",\n"
            + "        \"countryCode\": \"US\",\n"
            + "        \"dcNumber\": 8852,\n"
            + "        \"destinationLocationId\": 0,\n"
            + "        \"itemList\": [\n"
            + "            {\n"
            + "                \"itemNumber\": 556386040,\n"
            + "                \"itemUPC\": \"00078742126654\",\n"
            + "                \"purchaseCompanyId\": 1,\n"
            + "                \"financialReportingGroup\": \"US\",\n"
            + "                \"baseDivisionCode\": \"WM\",\n"
            + "                \"vendorPkRatio\": 10,\n"
            + "                \"warehousePkRatio\": 10,\n"
            + "                \"unitOfMeasurement\": \"EACHES\",\n"
            + "                \"availabletosellQty\": 0,\n"
            + "                \"availableNonPickableQty\": 0,\n"
            + "                \"claimsQty\": 0,\n"
            + "                \"allocatedQty\": 0,\n"
            + "                \"frozenQty\": 0,\n"
            + "                \"onorderQty\": 0,\n"
            + "                \"pickedQty\": 0,\n"
            + "                \"intransitQty\": 0,\n"
            + "                \"problemfreightQty\": 0,\n"
            + "                \"receivedQty\": 0,\n"
            + "                \"workInProgressQty\": 0,\n"
            + "                \"qualitycontrolQty\": 0,\n"
            + "                \"itemStatusChange\": {\n"
            + "                    \"availabletosellQty\": -2440\n"
            + "                },\n"
            + "                \"poType\": \"SSTKU\",\n"
            + "                \"channelType\": \"SSTKU\",\n"
            + "                \"whsePackSell\": {\n"
            + "                    \"value\": 33.6,\n"
            + "                    \"uom\": \"USD\"\n"
            + "                },\n"
            + "                \"adjustmentTO\": {\n"
            + "                    \"reasonCode\": 52,\n"
            + "                    \"reasonDesc\": \"Receiving Correction\",\n"
            + "                    \"value\": -100,\n"
            + "                    \"uom\": \"EACHES\"\n"
            + "                },\n"
            + "                \"orderDetails\": [],\n"
            + "                \"poDetails\": [\n"
            + "                    {\n"
            + "                        \"poNum\": \"1007916906\",\n"
            + "                        \"purchaseReferenceLineNumber\": 9,\n"
            + "                        \"poQty\": 2440\n"
            + "                    }\n"
            + "                ],\n"
            + "                \"purchaseReferenceLineNumbers\": [\n"
            + "                    9\n"
            + "                ],\n"
            + "                \"rotateDate\": \"06 Nov 2020 00:00:00.000+0000\",\n"
            + "                \"promoBuyInd\": \"N\",\n"
            + "                \"weightFormatType\": \"F\",\n"
            + "                \"ossIndicator\": \"N\",\n"
            + "                \"receivedDate\": \"07 Nov 2020 00:00:00.000+0000\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"childContainers\": [],\n"
            + "        \"rotateDates\": [\n"
            + "            \"06 Nov 2020 00:00:00.000+0000\"\n"
            + "        ],\n"
            + "        \"lotNumbers\": [],\n"
            + "        \"poNums\": [\n"
            + "            \"1007916906\"\n"
            + "        ],\n"
            + "        \"isNetItemWeightCalc\": true,\n"
            + "        \"itemWeightUOM\": \"LB\",\n"
            + "        \"itemNetWeight\": 0.0,\n"
            + "        \"isContainerCubeCalc\": true,\n"
            + "        \"containerCubeUOM\": \"CF\",\n"
            + "        \"containerCube\": 0.0,\n"
            + "        \"containerCreatedDate\": \"07 Nov 2020 04:27:39.250+0000\",\n"
            + "        \"containerStatus\": \"AVAILABLE\",\n"
            + "        \"isShippable\": false,\n"
            + "        \"isReusable\": false,\n"
            + "        \"deliveryNumber\": \"20782789\",\n"
            + "        \"containerUpdatedDate\": \"07 Nov 2020 04:27:39.250+0000\",\n"
            + "        \"locationName\": \"405\",\n"
            + "        \"locationOrgUnitId\": 0\n"
            + "    }\n"
            + "}";
    final JsonParser jsonParser = new JsonParser();
    JsonObject jsonObject =
        jsonParser.parse(inValidInvJson1_missing_containerUpdated).getAsJsonObject();
    assertFalse(GdcUtil.isValidInventoryAdjustment(jsonObject));
  }

  @Test
  public void isValidInventoryAdjustment_missing_eventObject() {
    final JsonParser jsonParser = new JsonParser();
    String inValidInvJson2_missing_eventObject =
        "{\n"
            + "  \"id\": \"aa826b34-237b-40cd-b172-af3d27b7761c\",\n"
            + "  \"correlationId\": \"8519a2b3-a26d-42ad-a752-dd4b904b84fa\",\n"
            + "  \"user\": \"leanna\",\n"
            + "  \"event\": \"container.updated\",\n"
            + "  \"occurredOn\": \"10 Nov 2020 17:39:35.838+0000\"\n"
            + "}";
    JsonObject jsonObject = jsonParser.parse(inValidInvJson2_missing_eventObject).getAsJsonObject();
    assertFalse(GdcUtil.isValidInventoryAdjustment(jsonObject));
  }

  @Test
  public void isValidInventoryAdjustment_wrong_container_created() {
    final JsonParser jsonParser = new JsonParser();
    String inValidInvJson2_wrong_container =
        "{\n"
            + "    \"id\": \"aa826b34-237b-40cd-b172-af3d27b7761c\",\n"
            + "    \"correlationId\": \"8519a2b3-a26d-42ad-a752-dd4b904b84fa\",\n"
            + "    \"user\": \"leanna\",\n"
            + "    \"event\": \"container.created\",\n"
            + "    \"occurredOn\": \"10 Nov 2020 17:39:35.838+0000\",\n"
            + "    \"eventObject\": {\n"
            + "        \"trackingId\": \"B32612000022764292\",\n"
            + "        \"containerId\": 0,\n"
            + "        \"containerTypeAbbr\": \"Chep Pallet\",\n"
            + "        \"countryCode\": \"US\",\n"
            + "        \"dcNumber\": 8852,\n"
            + "        \"destinationLocationId\": 0,\n"
            + "        \"itemList\": [\n"
            + "            {\n"
            + "                \"itemNumber\": 556386040,\n"
            + "                \"itemUPC\": \"00078742126654\",\n"
            + "                \"purchaseCompanyId\": 1,\n"
            + "                \"financialReportingGroup\": \"US\",\n"
            + "                \"baseDivisionCode\": \"WM\",\n"
            + "                \"vendorPkRatio\": 10,\n"
            + "                \"warehousePkRatio\": 10,\n"
            + "                \"unitOfMeasurement\": \"EACHES\",\n"
            + "                \"availabletosellQty\": 0,\n"
            + "                \"availableNonPickableQty\": 0,\n"
            + "                \"claimsQty\": 0,\n"
            + "                \"allocatedQty\": 0,\n"
            + "                \"frozenQty\": 0,\n"
            + "                \"onorderQty\": 0,\n"
            + "                \"pickedQty\": 0,\n"
            + "                \"intransitQty\": 0,\n"
            + "                \"problemfreightQty\": 0,\n"
            + "                \"receivedQty\": 0,\n"
            + "                \"workInProgressQty\": 0,\n"
            + "                \"qualitycontrolQty\": 0,\n"
            + "                \"itemStatusChange\": {\n"
            + "                    \"availabletosellQty\": -2440\n"
            + "                },\n"
            + "                \"poType\": \"SSTKU\",\n"
            + "                \"channelType\": \"SSTKU\",\n"
            + "                \"whsePackSell\": {\n"
            + "                    \"value\": 33.6,\n"
            + "                    \"uom\": \"USD\"\n"
            + "                },\n"
            + "                \"adjustmentTO\": {\n"
            + "                    \"reasonCode\": 52,\n"
            + "                    \"reasonDesc\": \"Receiving Correction\",\n"
            + "                    \"value\": -100,\n"
            + "                    \"uom\": \"EACHES\"\n"
            + "                },\n"
            + "                \"orderDetails\": [],\n"
            + "                \"poDetails\": [\n"
            + "                    {\n"
            + "                        \"poNum\": \"1007916906\",\n"
            + "                        \"purchaseReferenceLineNumber\": 9,\n"
            + "                        \"poQty\": 2440\n"
            + "                    }\n"
            + "                ],\n"
            + "                \"purchaseReferenceLineNumbers\": [\n"
            + "                    9\n"
            + "                ],\n"
            + "                \"rotateDate\": \"06 Nov 2020 00:00:00.000+0000\",\n"
            + "                \"promoBuyInd\": \"N\",\n"
            + "                \"weightFormatType\": \"F\",\n"
            + "                \"ossIndicator\": \"N\",\n"
            + "                \"receivedDate\": \"07 Nov 2020 00:00:00.000+0000\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"childContainers\": [],\n"
            + "        \"rotateDates\": [\n"
            + "            \"06 Nov 2020 00:00:00.000+0000\"\n"
            + "        ],\n"
            + "        \"lotNumbers\": [],\n"
            + "        \"poNums\": [\n"
            + "            \"1007916906\"\n"
            + "        ],\n"
            + "        \"isNetItemWeightCalc\": true,\n"
            + "        \"itemWeightUOM\": \"LB\",\n"
            + "        \"itemNetWeight\": 0.0,\n"
            + "        \"isContainerCubeCalc\": true,\n"
            + "        \"containerCubeUOM\": \"CF\",\n"
            + "        \"containerCube\": 0.0,\n"
            + "        \"containerCreatedDate\": \"07 Nov 2020 04:27:39.250+0000\",\n"
            + "        \"containerStatus\": \"AVAILABLE\",\n"
            + "        \"isShippable\": false,\n"
            + "        \"isReusable\": false,\n"
            + "        \"deliveryNumber\": \"20782789\",\n"
            + "        \"containerUpdatedDate\": \"07 Nov 2020 04:27:39.250+0000\",\n"
            + "        \"locationName\": \"405\",\n"
            + "        \"locationOrgUnitId\": 0\n"
            + "    }\n"
            + "}";

    JsonObject jsonObject = jsonParser.parse(inValidInvJson2_wrong_container).getAsJsonObject();
    assertFalse(GdcUtil.isValidInventoryAdjustment(jsonObject));
  }

  @Test
  public void testDateUTCConversion() {
    Calendar cal =
        new Calendar.Builder()
            .setDate(2022, Calendar.AUGUST, 17)
            .setTimeOfDay(12, 15, 60, 777)
            .build();

    Date localDate = cal.getTime();
    String utcDate = convertDateToUTCZone(localDate);
    assertEquals(utcDate, String.valueOf(localDate.toInstant().atOffset(ZoneOffset.UTC)));
  }

  @Test
  public void isAtlasConvertedItem_notConverted() {
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());
    assertFalse(GdcUtil.isAtlasConvertedItem(deliveryDocumentLine));
  }

  @Test
  public void isAtlasConvertedItem_additionalInfo_null() {
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());
    deliveryDocumentLine.setAdditionalInfo(null);
    assertFalse(GdcUtil.isAtlasConvertedItem(deliveryDocumentLine));
  }

  @Test
  public void isAtlasConvertedItem_converted() {
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    assertTrue(GdcUtil.isAtlasConvertedItem(deliveryDocumentLine));
  }

  @Test
  public void test_validateRequestLineIntoOss_receiveQty() {
    PoLine poLine = new PoLine();
    poLine.setReceiveQty(-1);
    try {
      GdcUtil.validateRequestLineIntoOss(poLine);
      fail();
    } catch (Exception e) {
      assertEquals("Negative quantity (-1) cannot be received", e.getMessage());
    }
  }

  @Test
  public void test_validateRequestLineIntoOss_rejectQty() {
    PoLine poLine = new PoLine();
    poLine.setRejectQty(-1);
    try {
      GdcUtil.validateRequestLineIntoOss(poLine);
      fail();
    } catch (Exception e) {
      assertEquals("Negative quantity (-1) cannot be rejected", e.getMessage());
    }
  }

  @Test(dataProvider = "NegativeDeliveryStatus")
  public void testCheckIfDeliveryStatusNotReceivableForGdc(
      DeliveryStatus deliveryStatus, String legacyDeliveryStatus) throws ReceivingException {
    deliveryDocuments.get(0).setDeliveryStatus(deliveryStatus);
    deliveryDocuments.get(0).setDeliveryLegacyStatus(legacyDeliveryStatus);
    try {
      GdcUtil.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));

    } catch (ReceivingException e) {

      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      if (DeliveryStatus.FNL.toString().equalsIgnoreCase(legacyDeliveryStatus)) {
        assertEquals(
            e.getMessage(),
            "This delivery is in FNL status in GDM. Please reopen this delivery by entering the delivery number");
      } else {
        assertEquals(
            e.getMessage(),
            "This delivery can not be received as the status is in "
                + legacyDeliveryStatus
                + " in GDM .Please contact your supervisor");
      }
    }
  }

  @Test(dataProvider = "PositiveDeliveryStatus")
  public void testCheckIfDeliveryStatusReceivableForGdc(
      DeliveryStatus deliveryStatus, String legacyDeliveryStatus) {
    try {
      deliveryDocuments.get(0).setDeliveryStatus(deliveryStatus);
      deliveryDocuments.get(0).setDeliveryLegacyStatus(legacyDeliveryStatus);
      GdcUtil.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));

    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @DataProvider(name = "PositiveDeliveryStatus")
  public static Object[][] positiveDeliveryStatus() {
    return new Object[][] {
      {DeliveryStatus.OPN, DeliveryStatus.PNDPT.toString()},
      {DeliveryStatus.OPN, DeliveryStatus.REO.toString()},
      {DeliveryStatus.WRK, null}
    };
  }

  @DataProvider(name = "NegativeDeliveryStatus")
  public static Object[][] negativeDeliveryStatus() {
    return new Object[][] {
      {DeliveryStatus.FNL, DeliveryStatus.PNDFNL.toString()},
      {DeliveryStatus.PNDFNL, DeliveryStatus.FNL.toString()}
    };
  }
}
