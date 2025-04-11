package com.walmart.move.nim.receiving.endgame.mock.data;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CASES_TO_BE_AUDITED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_AUDIT_IN_PROGRESS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_AUDIT_REQUIRED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIVED_CASE_QTY;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MockDeliveryMetaData {
  public static DeliveryMetaData getDeliveryMetaData_WithNoItemDetails() {
    return DeliveryMetaData.builder()
        .deliveryNumber("12333333")
        .totalCaseCount(3)
        .totalCaseLabelSent(3)
        .trailerNumber("123")
        .doorNumber("123")
        .itemDetails(null)
        .build();
  }

  public static DeliveryMetaData getDeliveryMetaData_WithItemDetails() {
    return getDeliveryMetaData_WithItemDetails("2019-02-11T00:00:00.000Z", "PALLET_BUILD");
  }

  public static DeliveryMetaData getDeliveryMetaData_WithItemDetails(
      String rotateDate, String divert) {
    String key = "561298341";
    LinkedTreeMap<String, String> itemDetails = new LinkedTreeMap<>();
    itemDetails.put(EndgameConstants.ROTATE_DATE, rotateDate);
    itemDetails.put(EndgameConstants.DIVERT_DESTINATION, divert);
    LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
    itemDetailsMap.put(key, itemDetails);
    return DeliveryMetaData.builder()
        .deliveryNumber("12333333")
        .totalCaseCount(3)
        .totalCaseLabelSent(3)
        .trailerNumber("123")
        .doorNumber("123")
        .itemDetails(itemDetailsMap)
        .build();
  }

  public static DeliveryMetaData getDeliveryMetaData_WithDifferentItem() {
    String key = "661298341";
    LinkedTreeMap<String, String> itemDetails = new LinkedTreeMap<>();
    itemDetails.put(EndgameConstants.ROTATE_DATE, "2019-02-11T00:00:00.000Z");
    itemDetails.put(EndgameConstants.DIVERT_DESTINATION, "DECANT");
    LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
    itemDetailsMap.put(key, itemDetails);
    return DeliveryMetaData.builder()
        .deliveryNumber("12333333")
        .totalCaseCount(3)
        .totalCaseLabelSent(3)
        .trailerNumber("123")
        .doorNumber("123")
        .itemDetails(itemDetailsMap)
        .build();
  }

  public static List<DeliveryMetaData> getDeliveryMetaData_ForOSDR() {
    DeliveryMetaData deliveryMetaData1 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333333")
            .unloadingCompleteDate(new Date())
            .build();
    DeliveryMetaData deliveryMetaData2 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333334")
            .unloadingCompleteDate(new Date())
            .osdrLastProcessedDate(new Date())
            .build();
    return Arrays.asList(deliveryMetaData1, deliveryMetaData2);
  }

  public static List<DeliveryMetaData> getDeliveryMetaData_ForRepositoryTest() {

    Date unloadingCompleteDateTwoDaysBack = Date.from(Instant.now().minus(Duration.ofDays(2)));
    Date unloadingCompleteDateTenDaysBack = Date.from(Instant.now().minus(Duration.ofDays(10)));

    Date osdrProcessedDateFiveHoursBack = Date.from(Instant.now().minus(Duration.ofMinutes(300)));
    Date osdrProcessedDateOneMinuteBack = Date.from(Instant.now().minus(Duration.ofMinutes(1)));

    DeliveryMetaData deliveryMetaData1 =
        DeliveryMetaData.builder().deliveryNumber("12333332").unloadingCompleteDate(null).build();
    DeliveryMetaData deliveryMetaData2 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333333")
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .unloadingCompleteDate(unloadingCompleteDateTwoDaysBack)
            .build();
    DeliveryMetaData deliveryMetaData3 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333334")
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .unloadingCompleteDate(unloadingCompleteDateTwoDaysBack)
            .osdrLastProcessedDate(osdrProcessedDateOneMinuteBack)
            .build();
    DeliveryMetaData deliveryMetaData4 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333335")
            .unloadingCompleteDate(unloadingCompleteDateTenDaysBack)
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .osdrLastProcessedDate(osdrProcessedDateFiveHoursBack)
            .build();
    DeliveryMetaData deliveryMetaData5 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333336")
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .unloadingCompleteDate(unloadingCompleteDateTwoDaysBack)
            .osdrLastProcessedDate(osdrProcessedDateFiveHoursBack)
            .build();
    return Arrays.asList(
        deliveryMetaData1,
        deliveryMetaData2,
        deliveryMetaData3,
        deliveryMetaData4,
        deliveryMetaData5);
  }

  public static DeliveryMetaData getDeliveryMetaData_WithReceivedQty(boolean auditRequired) {
    String key = "561298341";
    LinkedTreeMap<String, String> itemDetails = new LinkedTreeMap<>();
    itemDetails.put(IS_AUDIT_REQUIRED, String.valueOf(auditRequired));
    itemDetails.put(IS_AUDIT_IN_PROGRESS, String.valueOf(auditRequired));
    itemDetails.put(CASES_TO_BE_AUDITED, "1");
    itemDetails.put(RECEIVED_CASE_QTY, "1");
    LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
    itemDetailsMap.put(key, itemDetails);
    return DeliveryMetaData.builder()
        .deliveryNumber("12333333")
        .totalCaseCount(3)
        .totalCaseLabelSent(3)
        .trailerNumber("123")
        .doorNumber("123")
        .itemDetails(itemDetailsMap)
        .build();
  }

  public static DeliveryMetaData getDeliveryMetaData_WithoutReceivedQty(boolean auditRequired) {
    String key = "561298341";
    LinkedTreeMap<String, String> itemDetails = new LinkedTreeMap<>();
    itemDetails.put(IS_AUDIT_REQUIRED, String.valueOf(auditRequired));
    itemDetails.put(IS_AUDIT_IN_PROGRESS, String.valueOf(auditRequired));
    itemDetails.put(CASES_TO_BE_AUDITED, "1");
    LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
    itemDetailsMap.put(key, itemDetails);
    return DeliveryMetaData.builder()
        .deliveryNumber("12333333")
        .totalCaseCount(3)
        .totalCaseLabelSent(3)
        .trailerNumber("123")
        .doorNumber("123")
        .itemDetails(itemDetailsMap)
        .build();
  }
}
