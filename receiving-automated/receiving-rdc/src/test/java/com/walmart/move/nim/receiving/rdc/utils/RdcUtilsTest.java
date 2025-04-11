package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static org.junit.Assert.assertNull;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.netty.util.internal.StringUtil;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RdcUtilsTest {

  @Test
  public void test_getSSTKTimestampLabelTimeCode_1_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_1);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_1_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 2, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_1);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_1_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 2, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_1);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_2_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 3, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_2);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_2_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 5, 5, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_2);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_2_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 4, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_2);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_3_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 6, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_3);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_3_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 7, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_3);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_3_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 6, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_3);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_4_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 9, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_4);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_4_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 11, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_4);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_4_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 9, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_4);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_5_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 12, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_5);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_5_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 14, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_5);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_5_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 13, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_5);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_6_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 15, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_6);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_6_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 17, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_6);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_6_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 16, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_6);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_7_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 18, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_7);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_7_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 20, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_7);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_7_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 20, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_7);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_8_StartOfshift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 21, 0, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_8);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_8_EndOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 23, 59, 59, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_8);
  }

  @Test
  public void test_getSSTKTimestampLabelTimeCode_8_MiddleOfShift() {
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 3, 1, 23, 10, 0, 0, ZoneId.of("UTC"));
    String timeCode = RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime);
    assertNotNull(timeCode);
    assertEquals(timeCode, RdcConstants.TIMESTAMP_LABEL_TIME_CODE_8);
  }

  @Test
  public void test_validateMandatoryLocationRequestHeaders_Success() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "102");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "000232323");
    RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_validateMandatoryLocationRequestHeaders_Exception_MissingLocationId() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "000232323");
    RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_validateMandatoryLocationRequestHeaders_Exception_MissingLocationType() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "102");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "000232323");
    RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_validateMandatoryLocationRequestHeaders_Exception_MissingLocationSccCode() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "102");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR");
    RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
  }

  @Test
  public void test_getDCDateTime() {
    String dcTimeZone = "US/Central";
    ZonedDateTime zonedDateTime = ReceivingUtils.getDCDateTime(dcTimeZone);
    assertNotNull(zonedDateTime);
  }

  @Test
  public void test_getZonedDateTimeByDCTimezone() {
    String dcTimeZone = "US/Eastern";
    ZonedDateTime zonedDateTime = RdcUtils.getZonedDateTimeByDCTimezone(dcTimeZone, new Date());
    assertNotNull(zonedDateTime);
  }

  @Test
  public void test_getDateByDCTimezone() {
    String dcTimeZone = "US/Eastern";
    Date date = RdcUtils.getDateByDCTimezone(dcTimeZone, new Date());
    assertNotNull(date);
  }

  @Test
  public void test_getDateByDCTimezone_No_Date() {
    String dcTimeZone = "US/Central";
    Date date = RdcUtils.getDateByDCTimezone(dcTimeZone, null);
    assertNull(date);
  }

  @Test
  public void test_getMappedTenantFromGivenBaseURL() {
    String nimdRdsBaseUrl = "http://nimservices.s32698.us.wal-mart.com:7099";
    String tenant = RdcUtils.getMappedTenant(nimdRdsBaseUrl);
    assertNotNull(tenant);
    assertEquals(tenant, "32698");
  }

  @Test
  public void testBreakPackConveyPicks_ReturnsFalse() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    boolean isBreakPackConveyPickItem =
        RdcUtils.isBreakPackConveyPicks(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertFalse(isBreakPackConveyPickItem);
  }

  @Test
  public void testBreakPackConveyPicks_ReturnsTrue() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    boolean isBreakPackConveyPickItem =
        RdcUtils.isBreakPackConveyPicks(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertTrue(isBreakPackConveyPickItem);
  }

  @Test
  public void testNonConHandlingCode_ReturnsFalse() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    boolean isNonConveyableItem =
        RdcUtils.isNonConveyableItem(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertFalse(isNonConveyableItem);
  }

  @Test
  public void testNonConHandlingCode_ReturnsTrue() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    boolean isNonConHandlingCode =
        RdcUtils.isNonConveyableItem(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertTrue(isNonConHandlingCode);
  }

  @Test
  public void testGetForwardableHttpHeadersWithLocationInfo() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("32679", "US");
    TenantContext.setFacilityNum(32679);
    TenantContext.setFacilityCountryCode("US");
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");

    HttpHeaders result = RdcUtils.getForwardableHttpHeadersWithLocationInfo(httpHeaders);
    assertNotNull(result);
    assertEquals(result.getFirst(USER_ID_HEADER_KEY), "sysadmin");
    assertEquals(result.getFirst(RdcConstants.WFT_LOCATION_ID), "23");
    assertEquals(result.getFirst(RdcConstants.WFT_LOCATION_TYPE), "DOOR-23");
    assertEquals(result.getFirst(RdcConstants.WFT_SCC_CODE), "0086623");
    assertEquals(result.getFirst(RdcConstants.DA_RECEIVING_CAPABILITY), "true");
  }

  @Test
  public void testGetForwardableHttpHeadersWithLocationInfo_MissingDAReceivingCapabilityHeader() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("32679", "US");
    TenantContext.setFacilityNum(32679);
    TenantContext.setFacilityCountryCode("US");
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");

    HttpHeaders result = RdcUtils.getForwardableHttpHeadersWithLocationInfo(httpHeaders);
    assertNotNull(result);
    assertTrue(Objects.isNull(result.getFirst(RdcConstants.DA_RECEIVING_CAPABILITY)));
  }

  @Test
  public void testGetZoneRangeForDAReceivingWithMoreZonesAsSameZone() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWithMoreZonesAsSameZone());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "3-3");
  }

  @Test
  public void testGetZoneRangeForDAReceivingWith2DifferentZones() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWith2DifferentZones());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "3-4");
  }

  @Test
  public void testGetZoneRangeForDAReceivingWith2SameZones() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWith2SameZones());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "3-3");
  }

  @Test
  public void testGetZoneRangeForDAReceivingWith6DifferentOrderedZones() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWith6DifferentOrderedZones());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "8-16");
  }

  @Test
  public void testGetZoneRangeForDAReceivingWith6DifferentUnOrderedZones() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWith6DifferentUnOrderedZones());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "8-16");
  }

  @Test
  public void testGetZoneRangeForDAReceivingWithMoreZonesAsDifferentZone() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWithMoreZonesAsDifferentZone());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "1-8");
  }

  private List<Destination> getMockDestinationsWith1Zone() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setZone("01");
    destinations.add(destination);
    return destinations;
  }

  private List<Destination> getMockDestinationsWithMoreZonesAsDifferentZone() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination1 = new Destination();
    destination1.setZone("08");
    Destination destination2 = new Destination();
    destination2.setZone("03");
    Destination destination3 = new Destination();
    destination3.setZone("01");
    destinations.add(destination1);
    destinations.add(destination2);
    destinations.add(destination3);
    return destinations;
  }

  private List<Destination> getMockDestinationsWithMoreZonesAsSameZone() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination1 = new Destination();
    destination1.setZone("03");
    Destination destination2 = new Destination();
    destination2.setZone("03");
    Destination destination3 = new Destination();
    destination3.setZone("03");
    destinations.add(destination1);
    destinations.add(destination2);
    destinations.add(destination3);
    return destinations;
  }

  private List<Destination> getMockDestinationsWith2DifferentZones() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination1 = new Destination();
    destination1.setZone("03");
    Destination destination2 = new Destination();
    destination2.setZone("04");
    destinations.add(destination1);
    destinations.add(destination2);
    return destinations;
  }

  private List<Destination> getMockDestinationsWith2SameZones() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination1 = new Destination();
    destination1.setZone("03");
    Destination destination2 = new Destination();
    destination2.setZone("03");
    destinations.add(destination1);
    destinations.add(destination2);
    return destinations;
  }

  private List<Destination> getMockDestinationsWith6DifferentOrderedZones() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination1 = new Destination();
    destination1.setZone("08");
    Destination destination2 = new Destination();
    destination2.setZone("11");
    Destination destination3 = new Destination();
    destination3.setZone("12");
    Destination destination4 = new Destination();
    destination4.setZone("14");
    Destination destination5 = new Destination();
    destination5.setZone("15");
    Destination destination6 = new Destination();
    destination6.setZone("16");
    destinations.add(destination1);
    destinations.add(destination2);
    destinations.add(destination3);
    destinations.add(destination4);
    destinations.add(destination5);
    destinations.add(destination6);
    return destinations;
  }

  private List<Destination> getMockDestinationsWith6DifferentUnOrderedZones() {
    List<Destination> destinations = new ArrayList<>();
    Destination destination1 = new Destination();
    destination1.setZone("16");
    Destination destination2 = new Destination();
    destination2.setZone("11");
    Destination destination3 = new Destination();
    destination3.setZone("15");
    Destination destination4 = new Destination();
    destination4.setZone("12");
    Destination destination5 = new Destination();
    destination5.setZone("08");
    Destination destination6 = new Destination();
    destination6.setZone("14");
    destinations.add(destination1);
    destinations.add(destination2);
    destinations.add(destination3);
    destinations.add(destination4);
    destinations.add(destination5);
    destinations.add(destination6);
    return destinations;
  }

  @Test
  public void testGetZoneRangeForDAReceivingWith1Zone() {
    String zoneRange = RdcUtils.getZoneRange(getMockDestinationsWith1Zone());
    assertNotNull(zoneRange);
    assertEquals(zoneRange, "1-1");
  }

  @Test
  public void testStringToDate() {
    String dateTime = "2024-02-04 01:01:01.123";
    Date date = RdcUtils.stringToDate(dateTime);
    assertNotNull(date);
  }

  @Test
  public void testStringToDateWithInvalidDate() {
    String dateTime = "2024-02-04 01:01:01";
    Date date = RdcUtils.stringToDate(dateTime);
    assertNull(date);
  }

  @Test
  public void testGetLabelFormatDateAndTime() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    String date = RdcUtils.getLabelFormatDateAndTime(zonedDateTime);
    assertNotNull(date);
  }

  @Test
  public void testGetLabelFormatDateAndTimeWithInvalidDate() {
    ZonedDateTime zonedDateTime = null;
    String date = RdcUtils.getLabelFormatDateAndTime(zonedDateTime);
    assertNotNull(date);
    assertTrue(date.isEmpty());
  }

  @Test
  public void testGetLabelFormatDate() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    String date = RdcUtils.getLabelFormatDate(zonedDateTime);
    assertNotNull(date);
  }

  @Test
  public void testGetLabelFormatDateWithInvalidDate() {
    ZonedDateTime zonedDateTime = null;
    String date = RdcUtils.getLabelFormatDate(zonedDateTime);
    assertNotNull(date);
    assertTrue(date.isEmpty());
  }

  @Test
  public void testGetLabelFormatTime() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    String date = RdcUtils.getLabelFormatTime(zonedDateTime);
    assertNotNull(date);
  }

  @Test
  public void testGetLabelFormatTimeWithInvalidDate() {
    ZonedDateTime zonedDateTime = null;
    String date = RdcUtils.getLabelFormatTime(zonedDateTime);
    assertNotNull(date);
    assertTrue(date.isEmpty());
  }

  @Test
  public void testGetSSTKTimestampLabelFormatDate() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    String date = RdcUtils.getSSTKTimestampLabelFormatDate(zonedDateTime);
    assertNotNull(date);
  }

  @Test
  public void testGetSSTKTimestampLabelFormatDateWithInvalidDate() {
    ZonedDateTime zonedDateTime = null;
    String date = RdcUtils.getSSTKTimestampLabelFormatDate(zonedDateTime);
    assertNotNull(date);
    assertTrue(date.isEmpty());
  }

  @Test
  public void testGetSSTKTimestampLabelFormatTime() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    String date = RdcUtils.getSSTKTimestampLabelFormatTime(zonedDateTime);
    assertNotNull(date);
  }

  @Test
  public void testGetSSTKTimestampLabelFormatTimeWithInvalidDate() {
    ZonedDateTime zonedDateTime = null;
    String date = RdcUtils.getSSTKTimestampLabelFormatTime(zonedDateTime);
    assertNotNull(date);
    assertTrue(date.isEmpty());
  }

  @Test
  public void testGetStringValue() {
    String input = "input";
    String output = RdcUtils.getStringValue(input);
    assertNotNull(output);
    assertTrue(!output.isEmpty());
  }

  @Test
  public void testGetStringValueWithNullInput() {
    String input = null;
    String output = RdcUtils.getStringValue(input);
    assertNotNull(output);
    assertTrue(output.isEmpty());
  }

  @Test
  public void testConvertToReceivingBadDataException() {
    ReceivingException receivingException =
        new ReceivingException("error", HttpStatus.INTERNAL_SERVER_ERROR, "error", 1);
    ReceivingBadDataException receivingBadDataException =
        RdcUtils.convertToReceivingBadDataException(receivingException);
    assertNotNull(receivingBadDataException);
  }

  @Test
  public void testConvertToReceivingBadDataExceptionWithNullErrorCode() {
    ReceivingException receivingException =
        new ReceivingException("error", HttpStatus.INTERNAL_SERVER_ERROR, null, 1);
    ReceivingBadDataException receivingBadDataException =
        RdcUtils.convertToReceivingBadDataException(receivingException);
    assertNotNull(receivingBadDataException);
  }

  @Test
  public void testGetBreakPackRatioWithDefaultPackRatio() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    int breakPackRatio = RdcUtils.getBreakPackRatio(deliveryDocumentLine);
    assertTrue(breakPackRatio == RdcConstants.DEFAULT_BREAK_PACK_RATIO);
  }

  @Test
  public void testGetBreakPackRatio() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(1);
    deliveryDocumentLine.setWarehousePack(1);
    int breakPackRatio = RdcUtils.getBreakPackRatio(deliveryDocumentLine);
    assertTrue(breakPackRatio == 1);
  }

  @Test
  public void testGetPackTypeCodeByBreakPackRatio_casepack() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(2);
    deliveryDocumentLine.setWarehousePack(2);
    assertEquals("C", RdcUtils.getPackTypeCodeByBreakPackRatio(deliveryDocumentLine));
  }

  @Test
  public void testGetPackTypeCodeByBreakPackRatio_breakpack() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(6);
    deliveryDocumentLine.setWarehousePack(2);
    assertEquals("B", RdcUtils.getPackTypeCodeByBreakPackRatio(deliveryDocumentLine));
  }

  @Test
  public void testPickQuantity() {
    Integer receivedQty = 1;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemPackAndHandlingCode(
        RdcConstants.DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    Integer quantity = RdcUtils.pickQuantity(receivedQty, deliveryDocumentLine);
    assertNotNull(quantity);
    additionalInfo.setItemPackAndHandlingCode("CP");
    quantity = RdcUtils.pickQuantity(receivedQty, deliveryDocumentLine);
    assertNotNull(quantity);
    additionalInfo.setItemPackAndHandlingCode("PP");
    quantity = RdcUtils.pickQuantity(receivedQty, deliveryDocumentLine);
    assertNotNull(quantity);
    additionalInfo.setItemPackAndHandlingCode("");
    quantity = RdcUtils.pickQuantity(receivedQty, deliveryDocumentLine);
    assertNotNull(quantity);
  }

  @Test
  public void testisUserAllowedToReceiveDaFreight() {
    HttpHeaders httpHeaders = new HttpHeaders();
    boolean isDaReceivingAllowed = RdcUtils.isUserAllowedToReceiveDaFreight(httpHeaders);
    assertFalse(isDaReceivingAllowed);
    httpHeaders.put(RdcConstants.DA_RECEIVING_CAPABILITY, Arrays.asList("true"));
    isDaReceivingAllowed = RdcUtils.isUserAllowedToReceiveDaFreight(httpHeaders);
    assertTrue(isDaReceivingAllowed);
  }

  @Test
  public void testPopulateReceivedQtyDetailsInDeliveryDocument() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(new DeliveryDocumentLine()));
    Integer totalReceivedQty = 1;
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setTotalOrderQty(1);
    deliveryDocumentLine.setOverageQtyLimit(1);
    RdcUtils.populateReceivedQtyDetailsInDeliveryDocument(deliveryDocument, totalReceivedQty);
  }

  @Test
  public void testGetparsedDsdcErrorMessage() {
    DsdcReceiveResponse dsdcReceiveResponse = new DsdcReceiveResponse();
    dsdcReceiveResponse.setMessage("Error:");
    String response = RdcUtils.getparsedDsdcErrorMessage(dsdcReceiveResponse);
    assertNull(response);
  }

  @Test
  public void testIsWorkStationAndScanToPrintReceivingModeEnabled() {
    String featureType = "WORK_STATION";
    boolean isWorkStationAndScanToPrintReceivingModeEnabled =
        RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(featureType);
    assertTrue(isWorkStationAndScanToPrintReceivingModeEnabled);
  }

  @Test
  public void testBuildCommonReceivedContainerDetails() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(new DeliveryDocumentLine()));
    ReceivedContainer receivedContainer = new ReceivedContainer();
    String labelTrackingId = "id";
    RdcUtils.buildCommonReceivedContainerDetails(
        labelTrackingId, receivedContainer, deliveryDocument);
  }

  @Test
  public void testPrepareDistributions() {
    InstructionDownloadDistributionsDTO instructionDownloadDistributionsDTO =
        new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    instructionDownloadDistributionsDTO.setOrderId("123");
    instructionDownloadDistributionsDTO.setItem(item);
    List<InstructionDownloadDistributionsDTO> instructionDownloadDistributionsDTOS =
        Arrays.asList(instructionDownloadDistributionsDTO);
    String buNumber = "123";
    List<Distribution> distributionList =
        RdcUtils.prepareDistributions(instructionDownloadDistributionsDTOS, buNumber);
    assertNotNull(distributionList);
    assertTrue(!distributionList.isEmpty());
  }

  @Test
  public void testIsBreakPackItem() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(2);
    deliveryDocumentLine.setWarehousePack(1);
    Boolean isbreakPackItem =
        RdcUtils.isBreakPackItem(
            deliveryDocumentLine.getVendorPack(), deliveryDocumentLine.getWarehousePack());
    assertTrue(isbreakPackItem);
  }

  @Test
  public void testIsBreakPackItem_CasePack() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(1);
    deliveryDocumentLine.setWarehousePack(1);
    Boolean isbreakPackItem =
        RdcUtils.isBreakPackItem(
            deliveryDocumentLine.getVendorPack(), deliveryDocumentLine.getWarehousePack());
    assertFalse(isbreakPackItem);
  }

  @Test
  public void testCreateItemUpdateHawkeyeRequest() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
        RdcUtils.createHawkeyeItemUpdateRequest(
            123456L, 345678L, RejectReason.RDC_NONCON, null, true);
    assertEquals(hawkeyeItemUpdateRequest.getItemNumber(), String.valueOf(123456L));
    assertEquals(hawkeyeItemUpdateRequest.getReject(), RejectReason.RDC_NONCON.getRejectCode());
  }

  @Test
  public void testCreateItemUpdateRequest_onlyItemNumber() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
        RdcUtils.createHawkeyeItemUpdateRequest(123456L, null, null, null, true);
    assertEquals(hawkeyeItemUpdateRequest.getItemNumber(), String.valueOf(123456L));
    assertEquals(hawkeyeItemUpdateRequest.getReject(), StringUtil.EMPTY_STRING);
    Assert.assertNull(hawkeyeItemUpdateRequest.getGroupNumber());
  }

  @Test
  public void testCreateItemUpdateRequest_CatalogUpdate() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
        RdcUtils.createHawkeyeItemUpdateRequest(123456L, 345678L, null, "20000943037194", false);
    assertEquals(hawkeyeItemUpdateRequest.getItemNumber(), String.valueOf(123456L));
    assertEquals(hawkeyeItemUpdateRequest.getReject(), null);
    assertEquals(hawkeyeItemUpdateRequest.getCatalogGTIN(), "20000943037194");
  }

  @Test
  public void testIsBreakPackNonConRtsPutAtlasItem() throws Exception {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.NON_CON_RTS_PUT_HANDLING_CODE);
    boolean isBreakPackNonConRtsPutAtlasItem =
        RdcUtils.isBreakPackNonConRtsPutItem(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertTrue(isBreakPackNonConRtsPutAtlasItem);
  }

  @Test
  public void testIsBreakPackNonConRtsPutAtlasItem_With_Not_A_AtlasConvertedItem()
      throws Exception {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(false);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.NON_CON_RTS_PUT_HANDLING_CODE);
    boolean isBreakPackNonConRtsPutAtlasItem =
        RdcUtils.isBreakPackNonConRtsPutItem(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertTrue(isBreakPackNonConRtsPutAtlasItem);
  }

  @Test
  public void testIsBreakPackNonConRtsPutAtlasItem_With_Not_A_BreakPackTypeCode() throws Exception {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.CASE_PACK_TYPE_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.NON_CON_RTS_PUT_HANDLING_CODE);
    boolean isBreakPackNonConRtsPutAtlasItem =
        RdcUtils.isBreakPackNonConRtsPutItem(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertFalse(isBreakPackNonConRtsPutAtlasItem);
  }

  @Test
  public void testIsBreakPackNonConRtsPutAtlasItem_With_Not_A_NonConRtsPutHandlingCode()
      throws Exception {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    boolean isBreakPackNonConRtsPutAtlasItem =
        RdcUtils.isBreakPackNonConRtsPutItem(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertFalse(isBreakPackNonConRtsPutAtlasItem);
  }
}
