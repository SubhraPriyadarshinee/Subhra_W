package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.FAILED;
import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.HAUL;
import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.OPEN;
import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.PUTAWAY;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_LPN_NUMBER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_PO_NUMBER;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import io.strati.libs.logging.log4j2.util.Strings;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageHeaders;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ReceivingUtilsTest extends ReceivingTestBase {
  @Mock private TenantSpecificConfigReader configUtils;
  private DeliveryDocumentLine deliveryDocumentLine;

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");

    deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("124456");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setItemNbr(240129l);
    deliveryDocumentLine.setBolWeight(null);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWeightFormatTypeCode(ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
  }

  @Test
  public void test_eaches_vnpk() {
    System.out.println(
        ReceivingUtils.conversionToVendorPack(1, ReceivingConstants.Uom.EACHES, 40, 1));
  }

  @Test
  public void testConversionToEaches() {
    assertEquals(
        ReceivingUtils.conversionToEaches(1, ReceivingConstants.Uom.VNPK, 12, 6),
        Integer.valueOf(12));
    assertEquals(
        ReceivingUtils.conversionToEaches(1, ReceivingConstants.Uom.CA, 12, 6),
        Integer.valueOf(12));
    assertEquals(
        ReceivingUtils.conversionToEaches(1, ReceivingConstants.Uom.WHPK, 12, 6),
        Integer.valueOf(6));
    assertEquals(
        ReceivingUtils.conversionToEaches(1, ReceivingConstants.Uom.EACHES, 12, 6),
        Integer.valueOf(1));
  }

  @Test
  public void testConversionToVendorPack() {
    assertEquals(
        ReceivingUtils.conversionToVendorPack(12, ReceivingConstants.Uom.VNPK, 12, 6),
        Integer.valueOf(12));
    assertEquals(
        ReceivingUtils.conversionToVendorPack(12, ReceivingConstants.Uom.WHPK, 12, 6),
        Integer.valueOf(6));
    assertEquals(
        ReceivingUtils.conversionToVendorPack(12, ReceivingConstants.Uom.EACHES, 12, 6),
        Integer.valueOf(1));
  }

  @Test
  public void test_conversionToWareHousePack() {
    assertEquals(
        ReceivingUtils.conversionToWareHousePack(20, ReceivingConstants.Uom.WHPK, 1440, 60),
        Integer.valueOf(20));
    assertEquals(
        ReceivingUtils.conversionToWareHousePack(1, ReceivingConstants.Uom.VNPK, 1440, 60),
        Integer.valueOf(24));
    assertEquals(
        ReceivingUtils.conversionToWareHousePack(1, ReceivingConstants.Uom.CA, 1440, 60),
        Integer.valueOf(24));
    assertEquals(
        ReceivingUtils.conversionToWareHousePack(2880, ReceivingConstants.Uom.EACHES, 1440, 60),
        Integer.valueOf(48));
  }

  @Test
  public void test_conversionToVendorPackRoundUp() {
    assertEquals(
        ReceivingUtils.conversionToVendorPackRoundUp(24, ReceivingConstants.Uom.WHPK, 48, 2),
        Integer.valueOf(1));
    assertEquals(
        ReceivingUtils.conversionToVendorPackRoundUp(25, ReceivingConstants.Uom.WHPK, 48, 2),
        Integer.valueOf(2));
    assertEquals(
        ReceivingUtils.conversionToVendorPackRoundUp(1, ReceivingConstants.Uom.VNPK, 48, 2),
        Integer.valueOf(1));
    assertEquals(
        ReceivingUtils.conversionToVendorPackRoundUp(48, ReceivingConstants.Uom.EACHES, 48, 2),
        Integer.valueOf(1));
  }

  @Test
  public void test_getOsdrDefaultSummaryResponse() {
    OsdrSummary osdrSummary = ReceivingUtils.getOsdrDefaultSummaryResponse(12345l);
    assertNotNull(osdrSummary);
    assertEquals(osdrSummary.getEventType(), OSDR_EVENT_TYPE_VALUE);
    assertEquals(osdrSummary.getUserId(), DEFAULT_AUDIT_USER);
    assertFalse(osdrSummary.getAuditPending());
    assertNotNull(osdrSummary.getTs());
    assertNotNull(osdrSummary.getDeliveryNumber());
  }

  @Test
  public void testDateConversionToUTC() throws ParseException {
    String utcDate = ReceivingUtils.dateConversionToUTC(new Date());
    assertTrue(utcDate.contains("Z"));
  }

  @Test
  public void testVerifyUserException() {

    int exceptionCount = 0;
    try {
      ReceivingUtils.verifyUser(
          MockInstruction.getCreatedInstruction(), "multiUser", RequestType.UPDATE);
    } catch (ReceivingException e) {
      exceptionCount++;
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(ReceivingException.MULTI_USER_ERROR_MESSAGE, "sysadmin"),
          "");
    }
    try {
      ReceivingUtils.verifyUser(
          MockInstruction.getPendingInstruction(), "multiUser", RequestType.COMPLETE);
    } catch (ReceivingException e) {
      exceptionCount++;
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(ReceivingException.MULTI_USER_ERROR_MESSAGE, "sysadmin"),
          "");
    }
    try {
      ReceivingUtils.verifyUser(
          MockInstruction.getCreatedInstruction(), "multiUser", RequestType.CANCEL);
    } catch (ReceivingException e) {
      exceptionCount++;
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(ReceivingException.MULTI_USER_ERROR_MESSAGE, "sysadmin"),
          "");
    }
    try {
      ReceivingUtils.verifyUser(MockInstruction.getCreatedInstruction(), null, RequestType.UPDATE);
    } catch (ReceivingException e) {
      exceptionCount++;
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.MULTI_USER_UNABLE_TO_VERIFY,
          "");
    }
    assertEquals(exceptionCount, 4, "All the test didn't result in exception.");
  }

  @Test
  public void testVerifyUser() {

    try {
      ReceivingUtils.verifyUser(
          MockInstruction.getCreatedInstruction(), "sysadmin", RequestType.UPDATE);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  @Test
  public void testGetTimeDifferenceInMillis() {

    ReceivingUtils.getTimeDifferenceInMillis(System.currentTimeMillis());
    assert (true);
  }

  @Test
  public void testGetInstructionOwner() {
    assertEquals(
        ReceivingUtils.getInstructionOwner(MockInstruction.getCreatedInstruction()), "sysadmin");
    assertEquals(
        ReceivingUtils.getInstructionOwner(MockInstruction.getPendingInstruction()), "sysadmin");
  }

  @Test
  public void testGetAllParentContainers() {
    List<Container> containers = new ArrayList<>();
    containers.add(MockContainer.getContainerInfo());
    containers.add(MockContainer.getChildContainer());
    List<Container> parentContainers = ReceivingUtils.getAllParentContainers(containers);
    assertEquals(parentContainers.size(), 1);
    assertEquals(
        parentContainers.get(0).getTrackingId(), MockContainer.getContainerInfo().getTrackingId());
  }

  @Test
  public void testGetAllParentContainers_Null() {
    assertEquals(ReceivingUtils.getAllParentContainers(null), null);
  }

  @Test
  public void testGetContainerUser() {
    assertEquals(
        ReceivingUtils.getContainerUser(MockContainer.getSSTKContainer()),
        MockContainer.getSSTKContainer().getLastChangedUser());
    assertEquals(
        ReceivingUtils.getContainerUser(MockContainer.getContainerInfo()),
        MockContainer.getContainerInfo().getCreateUser());
  }

  @Test
  public void testSetContextFromMsgHeaders() {
    TenantContext.clear();
    MessageHeaders headers = MockMessageHeaders.getHeaders();
    ReceivingUtils.setContextFromMsgHeaders(headers, this.getClass().getName());
    assertEquals(TenantContext.getFacilityCountryCode(), "us");
    assertEquals(TenantContext.getFacilityNum().toString(), "32987");
    assertEquals(
        TenantContext.getCorrelationId(),
        headers.get(ReceivingConstants.JMS_CORRELATION_ID).toString());
  }

  @Test
  public void testSetContextFromMsgHeadersWithoutCorrelationId() {
    TenantContext.clear();
    MessageHeaders headers = MockMessageHeaders.getHeadersWithoutCorrelationId();
    ReceivingUtils.setContextFromMsgHeaders(headers, this.getClass().getName());
    assertEquals(TenantContext.getFacilityCountryCode(), "us");
    assertEquals(TenantContext.getFacilityNum().toString(), "32987");
    assertNotNull(TenantContext.getCorrelationId());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid tenant headers in.*")
  public void testSetContextFromMsgHeadersException() {
    ReceivingUtils.setContextFromMsgHeaders(
        MockMessageHeaders.getHeadersWithoutFacilityNum(), this.getClass().getName());
  }

  @Test
  public void testBatchifyCollection() {

    List<Integer> collectedData = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // [1,2,3], [4,5,6] , [7,8,9] ,  [10]
    Collection<List<Integer>> partitionedData = ReceivingUtils.batchifyCollection(collectedData, 3);

    assertEquals(4, partitionedData.size());
    partitionedData
        .stream()
        .forEach(
            data -> {
              assertTrue(data.size() == 3 || data.size() == 1);
            });
  }

  @Test
  public void testBatchifyCollectionLowestBoundry() {

    List<Integer> collectedData = Arrays.asList(1);

    // [1]
    Collection<List<Integer>> partitionedData = ReceivingUtils.batchifyCollection(collectedData, 3);

    assertEquals(1, partitionedData.size());
    partitionedData
        .stream()
        .forEach(
            data -> {
              assertTrue(data.size() == 1);
            });

    collectedData = Arrays.asList(1, 2);
    // [1]
    partitionedData = ReceivingUtils.batchifyCollection(collectedData, 3);

    assertEquals(1, partitionedData.size());
    partitionedData
        .stream()
        .forEach(
            data -> {
              assertTrue(data.size() == 2);
            });
  }

  @Test
  public void testIsNumeric() {
    assertTrue(ReceivingUtils.isNumeric("12345"));
    assertFalse(ReceivingUtils.isNumeric("+12345"));
    assertFalse(ReceivingUtils.isNumeric("-12345"));
    assertFalse(ReceivingUtils.isNumeric("as12345"));
  }

  @Test
  public void test_getDcDateTimeCST_or_DST_forDayLightSaving() {
    final String dcDateTime = ReceivingUtils.getDcDateTime("US/Central");
    assertNotNull(dcDateTime);
    // assertEquals(dcDateTime.length(), 23 ); //2020-09-04 12:23:33 CDT
    if (dcDateTime.contains("CDT")) {
      assertTrue(dcDateTime.contains("CDT"));
    } else {
      assertTrue(dcDateTime.contains("CST"));
    }
  }

  @Test
  public void test_getDcDateTime_forDayLightSaving_Mountain() {
    final String dcDateTime = ReceivingUtils.getDcDateTime("US/Mountain");
    assertNotNull(dcDateTime);
    if (dcDateTime.contains("MDT")) {
      assertTrue(dcDateTime.contains("MDT"));
    } else {
      assertTrue(dcDateTime.contains("MST"));
    }
  }

  @Test
  public void test_getDcDateTimePST_or_PDT_forDayLightSaving() {
    final String dcDateTime = ReceivingUtils.getDcDateTime("US/Pacific");
    assertNotNull(dcDateTime);
    if (dcDateTime.contains("PDT")) {
      assertTrue(dcDateTime.contains("PDT"));
    } else {
      assertTrue(dcDateTime.contains("PST"));
    }
  }

  @Test
  public void test_getDcDateTime_null_emptyInput() {
    String dcTimeZone_input = "";
    String dcDateTime_result = ReceivingUtils.getDcDateTime(dcTimeZone_input);
    assertNotNull(dcDateTime_result);
    assertTrue(dcDateTime_result.contains("UTC"));

    dcTimeZone_input = null;
    dcDateTime_result = ReceivingUtils.getDcDateTime(dcTimeZone_input);
    assertNotNull(dcDateTime_result);
    assertTrue(dcDateTime_result.contains("UTC"));
  }

  private void setTenantContextForHeader() {
    TenantContext.clear();
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
  }

  @Test
  public void test_getHeadersProvidingCorrelationId() {
    setTenantContextForHeader();
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CONTENT_TYPE));
  }

  @Test
  public void test_getHeaders() {
    setTenantContextForHeader();
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CONTENT_TYPE));
  }

  @Test
  public void test_forwardableHeader() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    Map<String, Object> header = ReceivingUtils.getForwardablHeader(httpHeaders);
    assertNotNull(header);
    assertNotNull(header.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(header.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void test_getHeaderForGDMV3API() {
    setTenantContextForHeader();
    HttpHeaders httpHeaders = ReceivingUtils.getHeaderForGDMV3API();
    assertNotNull(httpHeaders);
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.GDM_DOCUMENT_GET_BY_POLEGACY_V3_CONTENT_TYPE);
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.ACCEPT),
        ReceivingConstants.GDM_DOCUMENT_GET_BY_DELIVERY_V3_ACCEPT_TYPE);
  }

  @Test
  public void test_applyDamageForNullAndNonNUll() {
    Integer damagedQty = ReceivingUtils.applyDamage(10, null);
    assertEquals(damagedQty, Integer.valueOf(10));

    OsdrData damagedata = new OsdrData();
    damagedata.setQuantity(2);
    damagedQty = ReceivingUtils.applyDamage(10, damagedata);
    assertEquals(damagedQty, Integer.valueOf(8));
  }

  @Test
  public void test_calculateConcealedTotal() {
    assertEquals(ReceivingUtils.calculateConcealedTotal(10, 2, 1), Integer.valueOf(11));
  }

  @Test
  public void test_parseDate() {
    assertNull(ReceivingUtils.parseDate(""));

    Date date = ReceivingUtils.parseDate("2019-12-10T17:45:00.000Z");
    assertNotNull(date);

    assertNull(ReceivingUtils.parseDate("2019-12-10T17:45:00Z"));
  }

  @Test
  public void test_parseUtcDateTime() {

    Date date = ReceivingUtils.parseUtcDateTime("Wed Sep 02 16:52:37 IST 2020");
    assertNotNull(date);

    assertNull(ReceivingUtils.parseUtcDateTime("INVALID"));
  }

  @Test
  public void test_isValidLocation() {
    assertFalse(ReceivingUtils.isValidLocation(null));
    assertFalse(ReceivingUtils.isValidLocation("@efc$"));
    assertTrue(ReceivingUtils.isValidLocation("S12_9-2"));
  }

  @Test
  public void test_isValidUnitOfMeasurementForQuantity() {
    assertTrue(ReceivingUtils.isValidUnitOfMeasurementForQuantity("PH"));
    assertTrue(ReceivingUtils.isValidUnitOfMeasurementForQuantity("EA"));
    assertTrue(ReceivingUtils.isValidUnitOfMeasurementForQuantity("ZA"));
    assertFalse(ReceivingUtils.isValidUnitOfMeasurementForQuantity("INVALID"));
  }

  @Test
  public void test_getTimeDifferenceInMillis() {
    assertEquals(ReceivingUtils.getTimeDifferenceInMillis(9999997L, 10000000L), 3L);
  }

  @Test
  public void test_isValidPreLabelEvent() {
    assertTrue(ReceivingUtils.isValidPreLabelEvent(ReceivingConstants.EVENT_DOOR_ASSIGNED));
    assertTrue(ReceivingUtils.isValidPreLabelEvent(ReceivingConstants.EVENT_PO_ADDED));
    assertTrue(ReceivingUtils.isValidPreLabelEvent(ReceivingConstants.EVENT_PO_UPDATED));
    assertTrue(ReceivingUtils.isValidPreLabelEvent(ReceivingConstants.EVENT_PO_LINE_ADDED));
    assertTrue(ReceivingUtils.isValidPreLabelEvent(ReceivingConstants.EVENT_PO_LINE_UPDATED));
    assertTrue(ReceivingUtils.isValidPreLabelEvent(ReceivingConstants.PRE_LABEL_GEN_FALLBACK));
    assertFalse(ReceivingUtils.isValidPreLabelEvent("INVALID"));
  }

  @Test
  public void test_isValidStatus() {
    assertTrue(ReceivingUtils.isValidStatus(DeliveryStatus.ARV));
    assertTrue(ReceivingUtils.isValidStatus(DeliveryStatus.OPN));
    assertTrue(ReceivingUtils.isValidStatus(DeliveryStatus.WRK));
    assertFalse(ReceivingUtils.isValidStatus(DeliveryStatus.REO));
  }

  @Test
  public void test_isPOChangeEvent() {
    assertTrue(ReceivingUtils.isPOChangeEvent(ReceivingConstants.EVENT_PO_ADDED));
    assertTrue(ReceivingUtils.isPOChangeEvent(ReceivingConstants.EVENT_PO_UPDATED));
    assertTrue(ReceivingUtils.isPOChangeEvent(ReceivingConstants.EVENT_PO_LINE_ADDED));
    assertTrue(ReceivingUtils.isPOChangeEvent(ReceivingConstants.EVENT_PO_LINE_UPDATED));
    assertFalse(ReceivingUtils.isPOChangeEvent("INVALID"));
  }

  @Test
  public void test_isPOLineChangeEvent() {
    assertTrue(ReceivingUtils.isPOLineChangeEvent(ReceivingConstants.EVENT_PO_LINE_ADDED));
    assertTrue(ReceivingUtils.isPOLineChangeEvent(ReceivingConstants.EVENT_PO_LINE_UPDATED));
    assertFalse(ReceivingUtils.isPOLineChangeEvent("INVALID"));
  }

  @Test
  public void test_calculateUOMSpecificQuantity() {
    assertEquals(ReceivingUtils.calculateUOMSpecificQuantity(10, "ZA", 2, 2), Integer.valueOf(5));
    assertEquals(ReceivingUtils.calculateUOMSpecificQuantity(10, "PH", 2, 2), Integer.valueOf(5));
    assertEquals(ReceivingUtils.calculateUOMSpecificQuantity(10, "EA", 2, 2), Integer.valueOf(10));
    assertEquals(
        ReceivingUtils.calculateUOMSpecificQuantity(-10, "EA", 2, 2), Integer.valueOf(-10));
    assertEquals(ReceivingUtils.calculateUOMSpecificQuantity(-2, "ZA", 3, 3), Integer.valueOf(-1));
    assertEquals(ReceivingUtils.calculateUOMSpecificQuantity(0, "EA", 2, 2), Integer.valueOf(0));
    assertEquals(ReceivingUtils.calculateUOMSpecificQuantity(2, "ZA", 3, 2), Integer.valueOf(1));
  }

  @Test
  public void test_calculateQuantityFromSourceUOMToDestinationUOM() {
    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(120, "EA", "ZA", 100, 10),
        Integer.valueOf(1));
    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(10, "EA", "PH", 100, 10),
        Integer.valueOf(1));

    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(22, "PH", "ZA", 100, 10),
        Integer.valueOf(2));
    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(13, "PH", "EA", 100, 10),
        Integer.valueOf(130));

    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(2, "ZA", "PH", 100, 10),
        Integer.valueOf(20));
    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(3, "ZA", "EA", 100, 10),
        Integer.valueOf(300));

    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(-10, "EA", "ZA", 100, 10),
        Integer.valueOf(-1));
    assertEquals(
        ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(-220, "EA", "ZA", 100, 10),
        Integer.valueOf(-2));
  }

  @Test
  public void test_isSinglePO() {
    assertFalse(ReceivingUtils.isSinglePO(new ArrayList<>()));
    assertTrue(ReceivingUtils.isSinglePO(MockInstruction.getDeliveryDocuments()));
  }

  @Test
  public void test_isSinglePoLine() {
    assertTrue(ReceivingUtils.isSinglePoLine(MockInstruction.getDeliveryDocuments().get(0)));
  }

  @Test
  public void test_getForwardablHeaderWithTenantData() {
    setTenantContextForHeader();
    Map<String, Object> header =
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders());
    assertNotNull(header);
    assertNotNull(header.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(header.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(header.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(header.get(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test
  public void test_getForwardableHttpHeaders() {
    setTenantContextForHeader();
    HttpHeaders httpHeaders = getForwardableHttpHeaders(MockHttpHeaders.getHeaders());
    assertNotNull(httpHeaders);
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CONTENT_TYPE));
  }

  // Returns a HttpHeaders object with all required headers set when provided with valid input.
  @Test
  public void test_valid_input() {
    HttpHeaders headersFromUI = new HttpHeaders();
    final String validCorrelationId = "b12b17ef-4544-44d2-89f2-5a607d0d97d9";
    final String validTenant = "7023";
    final String validCountryCode = "US";
    final String validUserId = "KiranCh";
    final String requestOriginator = "requestOriginator";

    headersFromUI.add(CORRELATION_ID_HEADER_KEY, validCorrelationId);
    headersFromUI.add(TENENT_FACLITYNUM, validTenant);
    headersFromUI.add(TENENT_COUNTRY_CODE, validCountryCode);
    headersFromUI.add(USER_ID_HEADER_KEY, validUserId);
    headersFromUI.add(REQUEST_ORIGINATOR, requestOriginator);

    HttpHeaders forwardableHttpHeaders = ReceivingUtils.getForwardableHttpHeadersV2(headersFromUI);

    assertEquals(forwardableHttpHeaders.getFirst(TENENT_FACLITYNUM), validTenant);
    assertEquals(forwardableHttpHeaders.getFirst(TENENT_COUNTRY_CODE), validCountryCode);
    assertEquals(forwardableHttpHeaders.getFirst(CORRELATION_ID_HEADER_KEY), validCorrelationId);
    assertEquals(forwardableHttpHeaders.getFirst(CONTENT_TYPE), APPLICATION_JSON);
    assertEquals(forwardableHttpHeaders.getFirst(USER_ID_HEADER_KEY), validUserId);
    assertEquals(forwardableHttpHeaders.getFirst(REQUEST_ORIGINATOR), requestOriginator);
  }

  // Returns a HttpHeaders object with all required headers set when provided with headers
  // containing invalid correlationId.
  @Test
  public void test_invalid_correlation_id() {
    HttpHeaders headersFromUI = new HttpHeaders();
    headersFromUI.add(CORRELATION_ID_HEADER_KEY, "");
    headersFromUI.add(TENENT_FACLITYNUM, "valid_tenant");
    headersFromUI.add(TENENT_COUNTRY_CODE, "valid_country_code");
    headersFromUI.add(USER_ID_HEADER_KEY, "valid_user_id");

    HttpHeaders forwardableHttpHeaders = ReceivingUtils.getForwardableHttpHeadersV2(headersFromUI);

    assertNotEquals("", forwardableHttpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    assertEquals("valid_tenant", forwardableHttpHeaders.getFirst(TENENT_FACLITYNUM));
    assertEquals("valid_country_code", forwardableHttpHeaders.getFirst(TENENT_COUNTRY_CODE));
    assertEquals("application/json", forwardableHttpHeaders.getFirst(CONTENT_TYPE));
    assertEquals("valid_user_id", forwardableHttpHeaders.getFirst(USER_ID_HEADER_KEY));
    assertEquals(forwardableHttpHeaders.getFirst(REQUEST_ORIGINATOR), "receiving-api");
  }

  @Test
  public void test_getHttpHeadersFromMessageHeaders() {
    setTenantContextForHeader();
    final MessageHeaders headers = MockMessageHeaders.getHeaders();

    HttpHeaders httpHeaders = ReceivingUtils.getHttpHeadersFromMessageHeaders(headers);

    assertNotNull(httpHeaders);
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(httpHeaders.getFirst(REQUEST_ORIGINATOR));
    assertNotNull(httpHeaders.getFirst(FLOW_DESCRIPTOR));
  }

  @Test
  public void test_replacePathParams() {
    Map<String, String> pathParam = new HashMap<>();
    pathParam.put("deliveryNumber", "987654321");
    URI uri = ReceivingUtils.replacePathParams("http://localhost:8080/{deliveryNumber}", pathParam);
    assertEquals(uri.toString(), "http://localhost:8080/987654321");

    assertEquals(
        "http://localhost:8080",
        ReceivingUtils.replacePathParams("http://localhost:8080", null).toString());
  }

  @Test
  public void test_replacePathParamsAndQueryParams() {
    Map<String, String> pathParam = new HashMap<>();
    pathParam.put("deliveryNumber", "987654321");

    Map<String, String> queryParam = new HashMap<>();
    queryParam.put("uom", "ZA");

    URI uri =
        ReceivingUtils.replacePathParamsAndQueryParams(
            "http://localhost:8080/{deliveryNumber}", pathParam, queryParam);
    assertEquals(uri.toString(), "http://localhost:8080/987654321?uom=ZA");

    assertEquals(
        ReceivingUtils.replacePathParamsAndQueryParams("http://localhost:8080", null, queryParam)
            .toString(),
        "http://localhost:8080?uom=ZA");
  }

  @Test
  public void testDefaultUserHeaders() {
    TenantContext.clear();
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    String userId = ReceivingUtils.retrieveUserId();
    assertEquals(userId, ReceivingConstants.DEFAULT_USER);

    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, "test_user");
    userId = ReceivingUtils.retrieveUserId();
    assertEquals(userId, "test_user");

    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, null);
    userId = ReceivingUtils.retrieveUserId();
    assertEquals(userId, ReceivingConstants.DEFAULT_USER);
  }

  @Test()
  public void test_validateTrackingId_valid_PO_Number() {
    validatePoNumber("1233412345");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_PO_NUMBER)
  public void test_validateTrackingId_invalid_PO_Number() {
    validatePoNumber(" 1233412345 ");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_PO_NUMBER)
  public void test_validateTrackingId_invalid_PO_Number_alphaNumberic() {
    validatePoNumber("ABC1233412345");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_PO_NUMBER)
  public void test_validateTrackingId_invalid_PO_Number_DB_HIT() {
    validatePoNumber("1233412345 AND 1=1");
  }

  @Test()
  public void test_validateTrackingId_valid_LPN() {
    validateTrackingId("E1233412345");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid_null_LPN() {
    validateTrackingId(null);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid_empty_LPN() {
    validateTrackingId(" ");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid1_LPN() {
    validateTrackingId("E12334 AND 1=1;");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid2_LPN() {
    validateTrackingId("E12334 ");
  }

  @Test()
  public void test_validateTrackingId_valid_LPN_GLS_1() {
    validateTrackingId("123-ABC12332344", GLS_LPN_REGEX_PATTERN);
  }

  @Test()
  public void test_validateTrackingId_SmartLabelsValidPattern() {
    validateTrackingId("123323223");
  }

  @Test()
  public void test_validateTrackingId_valid_LPN_GLS_2() {
    validateTrackingId("1234567-ABC12332344", GLS_LPN_REGEX_PATTERN);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid_null_LPN_GLS() {
    validateTrackingId(null, GLS_LPN_REGEX_PATTERN);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid_empty_LPN_GLS() {
    validateTrackingId(" ", GLS_LPN_REGEX_PATTERN);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid1_LPN_GLS() {
    validateTrackingId("E12334", GLS_LPN_REGEX_PATTERN);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid1_LPN_SQL_INJECTION() {
    validateTrackingId("E12334 AND 1=1;", GLS_LPN_REGEX_PATTERN);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = INVALID_LPN_NUMBER)
  public void test_validateTrackingId_invalid2_LPN_GLS() {
    validateTrackingId("E12334 ", GLS_LPN_REGEX_PATTERN);
  }

  @Test
  public void testValidateAtlasContainerTrue() {
    boolean isAtlasContainer = isValidLpn("w060200000200000001115003");
    assertTrue(isAtlasContainer);
  }

  @Test
  public void testValidateAtlasContainerFalse() {
    boolean isAtlasContainer = isValidLpn("3360200000200000001115003");
    assertFalse(isAtlasContainer);
  }

  @Test
  public void testValidateAtlasContainerFalse_LPN_Length_LessThan25() {
    boolean isAtlasContainer = isValidLpn("9712345678");
    assertFalse(isAtlasContainer);
  }

  @Test
  public void test_parseDateFromIsoFormat() {
    assertNull(ReceivingUtils.parseIsoTimeFormat(""));
    assertNull(ReceivingUtils.parseIsoTimeFormat("dummy"));
    assertNotNull(ReceivingUtils.parseIsoTimeFormat("2019-12-10T17:45:00Z"));
    assertNotNull(ReceivingUtils.parseIsoTimeFormat("2019-12-10T17:45:00.000Z"));
    assertNotNull(ReceivingUtils.parseIsoTimeFormat("2019-12-10T17:45:00.034Z"));
    assertNotNull(ReceivingUtils.parseIsoTimeFormat("2019-12-10T17:45:00.034123Z"));
  }

  @Test
  public void test_parseTime() {
    assertNull(ReceivingUtils.parseTime(null));
    assertNull(ReceivingUtils.parseTime(""));
    assertNull(ReceivingUtils.parseTime("     "));
    assertNull(ReceivingUtils.parseTime("dummy"));
    assertNull(ReceivingUtils.parseTime("2019-12-10T17:45:00"));
    assertNull(ReceivingUtils.parseTime("2019-12-10 17:45:00Z"));
    assertNull(ReceivingUtils.parseTime("2019-12-10 T17:45:00Z"));
    assertNotNull(ReceivingUtils.parseTime("2019-12-10T17:45:00Z"));
    assertNotNull(ReceivingUtils.parseTime("2019-12-10T17:45:00.000Z"));
    assertNotNull(ReceivingUtils.parseTime("2019-12-10T17:45:00.034Z"));
    assertNotNull(ReceivingUtils.parseTime("2019-12-10T17:45:00.034123Z"));
  }

  @Test
  public void test_correctFormat() {
    assertEquals(
        "2019-12-10T17:45:00.000Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00Z"));
    assertEquals(
        "2019-12-10T17:45:00.000Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.0Z"));
    assertEquals(
        "2019-12-10T17:45:00.000Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.00Z"));
    assertEquals(
        "2019-12-10T17:45:00.000Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.000Z"));
    assertEquals(
        "2019-12-10T17:45:00.000Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.0000Z"));
    assertEquals(
        "2019-12-10T17:45:00.034Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.034Z"));
    assertEquals(
        "2019-12-10T17:45:00.034Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.0341Z"));
    assertEquals(
        "2019-12-10T17:45:00.034Z",
        ReceivingUtils.convertToTimestampWithMillisecond("2019-12-10T17:45:00.0349Z"));
  }

  @Test
  public void test_getReceiptSummaryResponseForRDC_EmptyOsdrSummary() {
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        ReceivingUtils.getReceiptSummaryResponseForRDC(null);
    assertEquals(receiptSummaryResponseList.size(), 0);
  }

  @Test
  public void test_getReceiptSummaryResponseForRDCWithReceiptsSummary() {
    OsdrSummary osdrSummary = new OsdrSummary();
    OsdrPo osdrPo = new OsdrPo();
    osdrPo.setPurchaseReferenceNumber("23233");
    osdrPo.setRcvdQty(23);
    OsdrPoLine osdrPoLine = new OsdrPoLine();
    osdrPoLine.setLineNumber(1L);
    osdrPoLine.setRcvdQty(23);
    List<OsdrPoLine> osdrPoLines = new ArrayList<>();
    osdrPoLines.add(osdrPoLine);
    List<OsdrPo> osdrPoList = new ArrayList<>();
    osdrPo.setLines(osdrPoLines);
    osdrPoList.add(osdrPo);
    osdrSummary.setSummary(osdrPoList);
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        ReceivingUtils.getReceiptSummaryResponseForRDC(osdrSummary);
    assertTrue(receiptSummaryResponseList.size() > 0);
  }

  @Test
  public void test_getForwardableHeaderWithEventType() {
    TenantContext.clear();
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32835);
    Map<String, Object> forwardableHeader =
        ReceivingUtils.getForwardableHeaderWithEventType(MockHttpHeaders.getHeaders(), "testEvent");
    assertNotNull(forwardableHeader);
    assertNotNull(forwardableHeader.get(ReceivingConstants.EVENT_TYPE));
    assertNotNull(forwardableHeader.get(ReceivingConstants.MSG_TIME_STAMP));
    assertNotNull(forwardableHeader.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(forwardableHeader.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(forwardableHeader.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void test_isKotlinEnabled_false_when_null_input() {
    assertFalse(isKotlinEnabled(null, null));
  }

  @Test
  public void test_isKotlinEnabled_false_when_client_HeaderIsNull() {
    TenantContext.clear();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, null);
    assertFalse(isKotlinEnabled(null, null));
  }

  @Test
  public void test_isKotlinEnabled_false_when_client_HeaderIsFalse() {
    TenantContext.clear();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "false");
    assertFalse(isKotlinEnabled(httpHeaders, null));
  }

  @Test
  public void test_isKotlinEnabled_false_when_client_HeaderIsTrue() {
    TenantContext.clear();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "true");
    assertTrue(isKotlinEnabled(httpHeaders, null));
  }

  @Test
  public void test_getDCDateTime() {
    String dcTimeZone = "US/Pacific";
    ZonedDateTime zonedDateTime = ReceivingUtils.getDCDateTime(dcTimeZone);
    assertNotNull(zonedDateTime);
  }

  @Test
  public void test_getLabelFormatDateAndTimeWhenZonedDateTimeIsNotNull() {
    String pattern = "yyyy/MM/dd' 'HH:mm:ss";
    String dcTimeZone = "US/Pacific";
    ZonedDateTime zonedDateTime = ReceivingUtils.getDCDateTime(dcTimeZone);
    String labelFormatDateAndTime =
        ReceivingUtils.getLabelFormatDateAndTime(zonedDateTime, pattern);
    assertNotNull(labelFormatDateAndTime);
  }

  @Test
  public void test_getLabelFormatDateAndTimeWhenZonedDateTimeIsNull() {
    String pattern = "yyyy/MM/dd' 'HH:mm:ss";
    String labelFormatDateAndTime = ReceivingUtils.getLabelFormatDateAndTime(null, pattern);
    assertEquals(labelFormatDateAndTime, StringUtils.EMPTY);
  }

  @Test
  public void test_parseStringToDateTime() throws Exception {
    Date date = ReceivingUtils.parseStringToDateTime("Apr 18, 2022 10:29:23 AM ");
    assertNotNull(date);
  }

  @Test
  public void test_parseStringToDateTime_throwsException() throws Exception {
    try {
      Date date = ReceivingUtils.parseStringToDateTime("Wed Apr 18, 2022 10:29:23 AM ");
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.PRODATE_CONVERSION_ERROR);
    }
  }

  @Test
  public void
      test_getForwardableHttpHeadersWithRequestOriginator_clientHttpHeaders_RequestOriginator_Null() {
    setTenantContextForHeader();
    HttpHeaders clientHttpHeaders = new HttpHeaders();
    final String requestOriginator_before = clientHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    assertNull(requestOriginator_before, "requestOriginator should be null before adding header");
    final HttpHeaders serverHttpHeaders =
        getForwardableHttpHeadersWithRequestOriginator(clientHttpHeaders);
    final String requestOriginator_after = serverHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    assertNotNull(
        requestOriginator_after, "requestOriginator should NOT be null after adding header");
    assertEquals(requestOriginator_after, APP_NAME_VALUE);
  }

  @Test
  public void
      test_getForwardableHttpHeadersWithRequestOriginator_clientHttpHeaders_RequestOriginator_NotNull() {
    setTenantContextForHeader();
    HttpHeaders clientHttpHeaders = new HttpHeaders();
    final String clientRequestOriginator = "receiving";
    clientHttpHeaders.set(REQUEST_ORIGINATOR, clientRequestOriginator);
    final String requestOriginator_before = clientHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    assertNotNull(
        requestOriginator_before,
        "requestOriginator should NOT null as its coming from Client http already");
    final HttpHeaders serverHttpHeaders =
        getForwardableHttpHeadersWithRequestOriginator(clientHttpHeaders);
    final String requestOriginator_after = serverHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    assertNotNull(
        requestOriginator_after, "requestOriginator should NOT be null after adding header");
    assertEquals(requestOriginator_after, clientRequestOriginator);
    assertNotEquals(requestOriginator_after, APP_NAME_VALUE, "should not be server given name");
  }

  @Test
  public void
      test_getForwardableHeadersWithRequestOriginator_clientHttpHeaders_RequestOriginator_Null() {
    setTenantContextForHeader();
    HttpHeaders clientHttpHeaders = new HttpHeaders();
    final String requestOriginator_before = clientHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    assertNull(requestOriginator_before, "requestOriginator should be null before adding header");
    final Map<String, Object> serverHeaders =
        getForwardableHeadersWithRequestOriginator(clientHttpHeaders);
    final Object requestOriginator_after = serverHeaders.get(REQUEST_ORIGINATOR);
    assertNotNull(
        (String) requestOriginator_after,
        "requestOriginator should NOT be null after adding header");
    assertEquals(requestOriginator_after, APP_NAME_VALUE);
  }

  @Test
  public void
      test_getForwardableHeadersWithRequestOriginator_clientHttpHeaders_RequestOriginator_NotNull() {
    setTenantContextForHeader();
    HttpHeaders clientHttpHeaders = new HttpHeaders();
    final String clientRequestOriginator = "receiving";
    clientHttpHeaders.set(REQUEST_ORIGINATOR, clientRequestOriginator);
    final String requestOriginator_before = clientHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    assertNotNull(
        requestOriginator_before,
        "requestOriginator should NOT null as its coming from Client http already");

    final Map<String, Object> serverHeaders =
        getForwardableHeadersWithRequestOriginator(clientHttpHeaders);
    final Object requestOriginator_after = serverHeaders.get(REQUEST_ORIGINATOR);
    assertNotNull(
        (String) requestOriginator_after,
        "requestOriginator should NOT be null after adding header");
    assertEquals(requestOriginator_after, clientRequestOriginator);
    assertNotEquals(requestOriginator_after, APP_NAME_VALUE, "should not be server given name");
  }

  @Test
  public void testAddOrReplaceHeader_null_flag_false() {
    setTenantContextForHeader();
    String key = IGNORE_SCT;
    // do nothing for null header
    addOrReplaceHeader(null, false, key, TRUE_STRING);
    // do nothing for null header
    addOrReplaceHeader(null, true, key, TRUE_STRING);

    // do nothing for not null header but flag is false
    HttpHeaders headers = new HttpHeaders();
    final Map<String, Object> forwardableHeaders = getForwardablHeader(headers);
    addOrReplaceHeader(forwardableHeaders, false, key, TRUE_STRING);
    final Object object = forwardableHeaders.get(key);
    assertNull(object);
  }

  @Test
  public void testAddOrReplaceHeader_flag_true_add() {
    setTenantContextForHeader();
    HttpHeaders headers = new HttpHeaders();
    final Map<String, Object> forwardableHeaders = getForwardablHeader(headers);
    String key = IGNORE_SCT;
    addOrReplaceHeader(forwardableHeaders, true, key, TRUE_STRING);
    final Object object = forwardableHeaders.get(key);
    assertNotNull(object);
    assertEquals(object, TRUE_STRING, "should new key passed");
  }

  @Test
  public void testAddOrReplaceHeader_flag_true_replace() {
    setTenantContextForHeader();
    HttpHeaders headers = new HttpHeaders();
    final Map<String, Object> forwardableHeaders = getForwardablHeader(headers);
    String key = IGNORE_SCT;
    forwardableHeaders.put(key, "OLD VALUE");
    addOrReplaceHeader(forwardableHeaders, true, key, TRUE_STRING);
    final Object object = forwardableHeaders.get(key);
    assertNotNull(object);
    assertEquals(object, TRUE_STRING, "should new key passed");
  }

  @Test
  public void testGetLabelTypeCodeReturnsLithiumIonResponse() {
    List<String> pkgInstructions = new ArrayList<>();
    pkgInstructions.add(ReceivingConstants.PKG_INSTRUCTION_CODE_965);

    String labelTypeCode = ReceivingUtils.getLabelTypeCode(pkgInstructions);

    assertNotNull(labelTypeCode);
    assertEquals(labelTypeCode, ReceivingConstants.LITHIUM_LABEL_CODE_3480);

    pkgInstructions.clear();
    pkgInstructions.add(ReceivingConstants.PKG_INSTRUCTION_CODE_966);
    pkgInstructions.add(ReceivingConstants.PKG_INSTRUCTION_CODE_967);

    labelTypeCode = ReceivingUtils.getLabelTypeCode(pkgInstructions);

    assertNotNull(labelTypeCode);
    assertSame(labelTypeCode, ReceivingConstants.LITHIUM_LABEL_CODE_3481);
  }

  @Test
  public void testGetLabelTypeCodeReturnsLithiumMetalResponse() {
    List<String> pkgInstructions = new ArrayList<>();
    pkgInstructions.add(ReceivingConstants.PKG_INSTRUCTION_CODE_968);

    String labelTypeCode = ReceivingUtils.getLabelTypeCode(pkgInstructions);

    assertNotNull(labelTypeCode);
    assertSame(labelTypeCode, ReceivingConstants.LITHIUM_LABEL_CODE_3090);

    pkgInstructions.clear();
    pkgInstructions.add(ReceivingConstants.PKG_INSTRUCTION_CODE_969);
    pkgInstructions.add(ReceivingConstants.PKG_INSTRUCTION_CODE_970);

    labelTypeCode = ReceivingUtils.getLabelTypeCode(pkgInstructions);

    assertNotNull(labelTypeCode);
    assertSame(labelTypeCode, ReceivingConstants.LITHIUM_LABEL_CODE_3091);
  }

  @Test
  public void testGetLabelTypeCodeReturnsNullResponse() {
    List<String> pkgInstructions = new ArrayList<>();
    pkgInstructions.add("900");

    String labelTypeCode = ReceivingUtils.getLabelTypeCode(pkgInstructions);

    assertNull(labelTypeCode);
  }

  @Test
  public void testisMoveTypeAndStatusPresent_true() {
    List<String> moveResponse = new ArrayList<>();
    moveResponse.add("HAULWORKING");
    moveResponse.add("PUTAWAYPENDING");

    assertTrue(ReceivingUtils.isInvalidMovePresent(moveResponse, "WORKING"));
  }

  @Test
  public void testisMoveTypeAndStatusPresent_false() {
    List<String> moveResponse = new ArrayList<>();
    moveResponse.add("HAULPENDING");

    assertFalse(ReceivingUtils.isInvalidMovePresent(moveResponse, "WORKING"));
  }

  @Test
  public void test_handleRDSResponse_SuccessMap() throws IOException {
    File resource = new ClassPathResource("quantity_received_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    Gson gson = new Gson();
    RdsReceiptsResponse rdsReceiptsResponse =
        gson.fromJson(mockResponse, RdsReceiptsResponse.class);

    ReceivedQuantityResponseFromRDS responseFromRDS =
        ReceivingUtils.handleRDSResponse(rdsReceiptsResponse);
    assertNotNull(rdsReceiptsResponse);
    assertNotNull(responseFromRDS);
  }

  @Test
  public void test_handleRDSResponse_ErrorMap() throws IOException {
    File resource = new ClassPathResource("quantity_received_error.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    Gson gson = new Gson();
    RdsReceiptsResponse rdsReceiptsResponse =
        gson.fromJson(mockResponse, RdsReceiptsResponse.class);

    ReceivedQuantityResponseFromRDS responseFromRDS =
        ReceivingUtils.handleRDSResponse(rdsReceiptsResponse);
    assertNotNull(rdsReceiptsResponse);
    assertNotNull(responseFromRDS);
  }

  @Test
  public void test_addOrgUnitId() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ORG_UNIT_ID_HEADER, "3");
    assertEquals(getForwardableWithOrgUnitId(httpHeaders).getFirst(ORG_UNIT_ID_HEADER), "3");
  }

  @Test
  public void test_MissingOrgUnitId() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      getForwardableWithOrgUnitId(httpHeaders);
      fail();
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), MISSING_ORG_UNIT_ID_CODE);
      assertEquals(e.getDescription(), MISSING_ORG_UNIT_ID_DESC);
    }
  }

  @Test
  public void test_getForwardablHeader() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(SUBCENTER_ID_HEADER, "3");
    final Map<String, Object> forwardablHeader = getForwardablHeader(httpHeaders);
    assertEquals(forwardablHeader.get(SUBCENTER_ID_HEADER), "3");
  }

  @Test
  public void testComputeEffectiveTotalQtyForNonImportIndWithFBQFeatureOFF() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(false);
    boolean importInd = false;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);

    Integer effectiveTotalQty =
        computeEffectiveTotalQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveTotalQty, deliveryDocumentLine.getTotalOrderQty());
  }

  @Test
  public void testComputeEffectiveTotalQtyForNonImportIndWithFBQFeatureON() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(true);
    boolean importInd = false;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);

    Integer effectiveTotalQty =
        computeEffectiveTotalQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveTotalQty, deliveryDocumentLine.getTotalOrderQty());
  }

  @Test
  public void testComputeEffectiveTotalQtyForImportIndWithFBQFeatureON() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(true);
    boolean importInd = true;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);
    deliveryDocumentLine.setPurchaseRefType(PurchaseReferenceType.CROSSMU.name());

    Integer effectiveTotalQty =
        computeEffectiveTotalQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveTotalQty, deliveryDocumentLine.getFreightBillQty());
  }

  @Test
  public void testComputeEffectiveTotalQtyForImportIndWithFBQFeatureOFF() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(false);
    boolean importInd = true;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);

    Integer effectiveTotalQty =
        computeEffectiveTotalQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveTotalQty, deliveryDocumentLine.getTotalOrderQty());
  }

  @Test
  public void testComputeEffectiveMaxReceiveQtyForNonImportWithFBQFeatureOFF() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(false);
    boolean importInd = false;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);
    deliveryDocumentLine.setOverageQtyLimit(5);
    Integer actualEffectiveMaxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    Integer effectiveMaxReceiveQty =
        computeEffectiveMaxReceiveQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveMaxReceiveQty, actualEffectiveMaxReceiveQty);
  }

  @Test
  public void testComputeEffectiveMaxReceiveQtyForImportWithFBQFeatureOFF() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(false);
    boolean importInd = true;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);
    deliveryDocumentLine.setOverageQtyLimit(5);
    Integer actualEffectiveMaxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    Integer effectiveMaxReceiveQty =
        computeEffectiveMaxReceiveQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveMaxReceiveQty, actualEffectiveMaxReceiveQty);
  }

  @Test
  public void testComputeEffectiveMaxReceiveQtyForNonImportWithFBQFeatureOn() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(true);
    boolean importInd = false;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);
    deliveryDocumentLine.setOverageQtyLimit(5);
    Integer actualEffectiveMaxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    Integer effectiveMaxReceiveQty =
        computeEffectiveMaxReceiveQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveMaxReceiveQty, actualEffectiveMaxReceiveQty);
  }

  @Test
  public void testComputeEffectiveMaxReceiveQtyForImportWithFBQFeatureOn() {
    TenantSpecificConfigReader configUtils = mock(TenantSpecificConfigReader.class);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(true);
    boolean importInd = true;
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setFreightBillQty(3);
    deliveryDocumentLine.setTotalOrderQty(4);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setPurchaseRefType(PurchaseReferenceType.CROSSMU.name());

    Integer effectiveMaxReceiveQty =
        computeEffectiveMaxReceiveQty(deliveryDocumentLine, importInd, configUtils);
    assertEquals(effectiveMaxReceiveQty, deliveryDocumentLine.getFreightBillQty());
  }

  @Test
  public void testValidateUnloaderEventType_positive() {
    try {
      ReceivingUtils.validateUnloaderEventType("UNLOAD_START");
    } catch (ReceivingBadDataException ex) {
      fail();
    }
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateUnloaderEventType_negative() {
    ReceivingUtils.validateUnloaderEventType(null);
    fail();
  }

  @Test
  public void testisTransferMerchandiseFromOssToMain_negative() {
    assertFalse(ReceivingUtils.isTransferMerchandiseFromOssToMain(null));
  }

  @Test
  public void testisTransferMerchandiseFromOssToMain_positive() {
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_RECEIVE_FROM_OSS, "true");
    assertTrue(ReceivingUtils.isTransferMerchandiseFromOssToMain(containerItemMiscInfo));
  }

  @Test
  public void testisTransferMerchandiseFromOssToMain_positive1() {
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_RECEIVE_FROM_OSS, "false");
    assertFalse(ReceivingUtils.isTransferMerchandiseFromOssToMain(containerItemMiscInfo));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateUnloaderInfoRequiredFields_negative1() {
    ReceivingUtils.validateUnloaderInfoRequiredFields(null);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateUnloaderInfoRequiredFields_negative2() {
    ReceivingUtils.validateUnloaderInfoRequiredFields(new UnloaderInfoDTO());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateUnloaderInfoRequiredFields_negative3() {
    UnloaderInfoDTO unloaderInfoDTO = new UnloaderInfoDTO();
    unloaderInfoDTO.setDeliveryNumber(1234L);
    ReceivingUtils.validateUnloaderInfoRequiredFields(unloaderInfoDTO);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateUnloaderInfoRequiredFields_negative4() {
    UnloaderInfoDTO unloaderInfoDTO = new UnloaderInfoDTO();
    unloaderInfoDTO.setDeliveryNumber(1234L);
    unloaderInfoDTO.setPurchaseReferenceNumber("12345");
    ReceivingUtils.validateUnloaderInfoRequiredFields(unloaderInfoDTO);
  }

  @Test
  public void testValidateUnloaderInfoRequiredFields() {
    UnloaderInfoDTO unloaderInfoDTO = new UnloaderInfoDTO();
    unloaderInfoDTO.setDeliveryNumber(1234L);
    unloaderInfoDTO.setPurchaseReferenceNumber("12345");
    unloaderInfoDTO.setPurchaseReferenceLineNumber(2);
    ReceivingUtils.validateUnloaderInfoRequiredFields(unloaderInfoDTO);
  }

  @Test
  public void testvalidateDeliveryNumber_postive() {
    try {
      ReceivingUtils.validateDeliveryNumber(1234L);
    } catch (ReceivingBadDataException ex) {
      fail();
    }
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testvalidateDeliveryNumber_negative() {
    ReceivingUtils.validateDeliveryNumber(null);
    fail();
  }

  @Test()
  public void testValidateVariableWeightForVariableItem() {
    try {
      ReceivingUtils.validateVariableWeightForVariableItem(deliveryDocumentLine);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(
          InstructionErrorCode.getErrorValue("MISSING_BOL_WEIGHT_ERROR").getErrorMessage(),
          e.getMessage());
    }
  }

  @Test()
  public void testValidateVariableWeightForVariableItem_negative() {
    try {
      DeliveryDocumentLine deliveryDocument = new DeliveryDocumentLine();
      ReceivingUtils.validateVariableWeightForVariableItem(deliveryDocument);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test()
  public void testValidateVariableWeightForVariableItem_negative1() {
    try {
      DeliveryDocumentLine deliveryDocument = new DeliveryDocumentLine();
      ItemData additionalInfo = new ItemData();
      additionalInfo.setWeightFormatTypeCode(ReceivingConstants.FIXED_WEIGHT_FORMAT_TYPE_CODE);
      deliveryDocument.setAdditionalInfo(additionalInfo);
      ReceivingUtils.validateVariableWeightForVariableItem(deliveryDocument);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testPopulateHttpHeadersFromKafkaHeaders() {
    Map<String, byte[]> kafkaHeaders = getKafkaHeaders();
    kafkaHeaders.remove(ReceivingConstants.GROUP_NBR);
    kafkaHeaders.remove(CORRELATION_ID_HEADER_KEY);
    kafkaHeaders.put(CORRELATION_ID, "12bab2b8-6840-4a7e-8129-c052b3979ecf".getBytes());
    kafkaHeaders.put(USER_ID_HEADER_KEY, "sysadmin".getBytes());
    HttpHeaders httpHeaders = ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders);
    assertEquals(
        new String(kafkaHeaders.get(TENENT_FACLITYNUM)), httpHeaders.getFirst(TENENT_FACLITYNUM));
    assertEquals(
        new String(kafkaHeaders.get(TENENT_COUNTRY_CODE)),
        httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    assertEquals(
        new String(kafkaHeaders.get(TENENT_GROUP_TYPE)), httpHeaders.getFirst(TENENT_GROUP_TYPE));
    assertEquals(
        new String(kafkaHeaders.get(CORRELATION_ID)),
        httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void testPopulateHttpHeadersFromKafkaHeaders_noUserID() {
    Map<String, byte[]> kafkaHeaders = getKafkaHeaders();
    kafkaHeaders.remove(CORRELATION_ID_HEADER_KEY);
    kafkaHeaders.remove(TENENT_GROUP_TYPE);
    HttpHeaders httpHeaders = ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders);
    assertEquals(
        new String(kafkaHeaders.get(TENENT_FACLITYNUM)), httpHeaders.getFirst(TENENT_FACLITYNUM));
    assertEquals(
        new String(kafkaHeaders.get(TENENT_COUNTRY_CODE)),
        httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    assertNull((httpHeaders.getFirst(TENENT_GROUP_TYPE)));
    assertNotNull(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void testPopulateKafkaHeadersFromHttpHeaders() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.put(TENENT_GROUP_TYPE, Collections.singletonList("RCV_DA"));
    Map<String, byte[]> kafkaHeaders = ReceivingUtils.populateKafkaHeadersFromHttpHeaders(headers);
    assertEquals(
        headers.getFirst(TENENT_FACLITYNUM), new String(kafkaHeaders.get(TENENT_FACLITYNUM)));
    assertEquals(
        headers.getFirst(TENENT_COUNTRY_CODE), new String(kafkaHeaders.get(TENENT_COUNTRY_CODE)));
    assertEquals(
        headers.getFirst(TENENT_GROUP_TYPE), new String(kafkaHeaders.get(TENENT_GROUP_TYPE)));
    assertEquals(
        headers.getFirst(CORRELATION_ID_HEADER_KEY),
        new String(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY)));
  }

  @Test
  public void testPopulateKafkaHeadersFromHttpHeaders_noUserID() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.put(TENENT_GROUP_TYPE, Collections.singletonList("RCV_DA"));
    headers.remove(USER_ID_HEADER_KEY);
    Map<String, byte[]> kafkaHeaders = ReceivingUtils.populateKafkaHeadersFromHttpHeaders(headers);
    assertEquals(
        headers.getFirst(TENENT_FACLITYNUM), new String(kafkaHeaders.get(TENENT_FACLITYNUM)));
    assertEquals(
        headers.getFirst(TENENT_COUNTRY_CODE), new String(kafkaHeaders.get(TENENT_COUNTRY_CODE)));
    assertEquals(
        headers.getFirst(TENENT_GROUP_TYPE), new String(kafkaHeaders.get(TENENT_GROUP_TYPE)));
    assertEquals(
        headers.getFirst(CORRELATION_ID_HEADER_KEY),
        new String(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY)));
    assertEquals(new String(kafkaHeaders.get(USER_ID_HEADER_KEY)), FLIB_USER);
  }

  @Test
  public void testPopulateHttpHeadersFromKafkaHeaders_withFallBackCorrelationID() {
    Map<String, byte[]> kafkaHeaders = getKafkaHeaders();
    kafkaHeaders.remove(CORRELATION_ID_HEADER_KEY);
    kafkaHeaders.put(CORRELATION_ID_HEADER_KEY_FALLBACK, "1a2bc3d4".getBytes());
    HttpHeaders httpHeaders = ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders);
    assertEquals(
        new String(kafkaHeaders.get(TENENT_FACLITYNUM)), httpHeaders.getFirst(TENENT_FACLITYNUM));
    assertEquals(
        new String(kafkaHeaders.get(TENENT_COUNTRY_CODE)),
        httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    assertEquals(
        new String(kafkaHeaders.get(TENENT_GROUP_TYPE)), httpHeaders.getFirst(TENENT_GROUP_TYPE));
    assertEquals(
        new String(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY_FALLBACK)),
        httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void testGetHawkeyePublishHeaders() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    Map<String, Object> kafkaHeaders = ReceivingUtils.getHawkeyePublishHeaders(123456L, headers);
    assertEquals(headers.getFirst(TENENT_FACLITYNUM), (String) kafkaHeaders.get(TENENT_FACLITYNUM));
    assertEquals(
        headers.getFirst(TENENT_COUNTRY_CODE), (String) kafkaHeaders.get(TENENT_COUNTRY_CODE));
    assertEquals(headers.getFirst(TENENT_GROUP_TYPE), (String) kafkaHeaders.get(TENENT_GROUP_TYPE));
    assertEquals(
        headers.getFirst(CORRELATION_ID_HEADER_KEY),
        (String) kafkaHeaders.get(CORRELATION_ID_HEADER_KEY));
    assertEquals(
        (String) kafkaHeaders.get(PRODUCT_NAME_HEADER_KEY), ReceivingConstants.APP_NAME_VALUE);
    assertEquals(kafkaHeaders.get(DELIVERY_NUMBER), 123456L);
    assertEquals((String) kafkaHeaders.get(CONTENT_ENCODING), CONTENT_ENCODING_GZIP);
  }

  @Test
  public void testGetHawkeyeForwardableHeaders() {
    TenantContext.clear();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    HttpHeaders requestHeaders = ReceivingUtils.getHawkeyeForwardableHeaders(headers);
    assertEquals(headers.getFirst(TENENT_FACLITYNUM), requestHeaders.getFirst(WMT_FACILITY_NUM));
    assertEquals(
        headers.getFirst(TENENT_COUNTRY_CODE), requestHeaders.getFirst(WMT_FACILITY_COUNTRY_CODE));
    assertEquals(
        headers.getFirst(CORRELATION_ID_HEADER_KEY),
        requestHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    assertEquals(requestHeaders.getFirst(CONTENT_TYPE), APPLICATION_JSON);
  }

  @Test
  public void testParseDateToProvidedTimeZone() throws ParseException {
    String expectedDateString = "Tue Feb 27 00:00:00 CST 2024";
    String inputDateString = "02/27/2024";
    String dcTimeZone = "America/Chicago";
    TimeZone.setDefault(TimeZone.getTimeZone(dcTimeZone));

    Date cstDate =
        ReceivingUtils.parseDateToProvidedTimeZone(
            inputDateString, ReceivingConstants.DELIVERY_SEARCH_CLIENT_TIME_FORMAT, dcTimeZone);
    assertNotNull(cstDate);
    assertEquals(expectedDateString, cstDate.toString());
  }

  @Test
  public void testDateConversionToProvidedTimeZone() throws ParseException {
    String expectedDateString = "2024-03-05T17:59:59";
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.DATE, 4);
    calendar.set(Calendar.MONTH, 2);
    calendar.set(Calendar.YEAR, 2024);
    calendar.set(Calendar.HOUR, HOUR_END_OF_DAY);
    calendar.set(Calendar.MINUTE, MINUTE_END_OF_DAY);
    calendar.set(Calendar.SECOND, SECOND_END_OF_DAY);
    calendar.setTimeZone(TimeZone.getTimeZone("America/Chicago"));
    String cstDate =
        ReceivingUtils.dateInProvidedPatternAndTimeZone(
            calendar.getTime(), HAWKEYE_DATE_FORMAT, UTC_TIME_ZONE);
    assertNotNull(cstDate);
    // assertEquals(expectedDateString, cstDate);
  }

  private Map<String, byte[]> getKafkaHeaders() {
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getMockKafkaListenerHeaders();
    messageHeaders.put(ReceivingConstants.TENENT_GROUP_TYPE, "RCV_DA".getBytes());
    messageHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us".getBytes());
    messageHeaders.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        "12bab2b8-6840-4a7e-8129-c052b3979ecf".getBytes());
    messageHeaders.put(TENENT_FACLITYNUM, "32679".getBytes());
    messageHeaders.put(ReceivingConstants.GROUP_NBR, "124356".getBytes());
    return messageHeaders;
  }

  @Test
  public void test_isDSDCDocument_Returns_True_For_DSDC_Document() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    Boolean isDSDCDocument = ReceivingUtils.isDSDCDocument(deliveryDocuments.get(0));
    Assert.assertTrue(isDSDCDocument);
  }

  @Test
  public void test_isDSDCDocument_Returns_False_For_SSTK_Document() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    Boolean isDSDCDocument = ReceivingUtils.isDSDCDocument(deliveryDocuments.get(0));
    Assert.assertFalse(isDSDCDocument);
  }

  @Test
  public void test_isDSDCDocument_Returns_False_For_DA_Document() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Boolean isDSDCDocument = ReceivingUtils.isDSDCDocument(deliveryDocuments.get(0));
    Assert.assertFalse(isDSDCDocument);
  }

  @Test
  public void testValidateDsdcDocuments_ReturnTrue() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    Boolean isDsdcDocument = ReceivingUtils.isDsdcDeliveryDocuments(deliveryDocuments);
    Assert.assertTrue(isDsdcDocument);
  }

  @Test
  public void testIsValidDeliverySearchClientTimeFormat() {
    boolean isValidDeliverySearchClientTimeFormat =
        ReceivingUtils.isValidDeliverySearchClientTimeFormat("02/27/2024");
    assertTrue(isValidDeliverySearchClientTimeFormat);
  }

  @Test
  public void testIsValidDeliverySearchClientTimeFormat_InvalidDate() {
    boolean isValidDeliverySearchClientTimeFormat =
        ReceivingUtils.isValidDeliverySearchClientTimeFormat(null);
    assertFalse(isValidDeliverySearchClientTimeFormat);
  }

  @Test
  public void testIsValidDeliverySearchClientTimeFormat_EmptyDate() {
    boolean isValidDeliverySearchClientTimeFormat =
        ReceivingUtils.isValidDeliverySearchClientTimeFormat(Strings.EMPTY);
    assertFalse(isValidDeliverySearchClientTimeFormat);
  }

  @Test
  public void testHandleRdsResponseForInProgressPOs_SuccessMap() throws IOException {
    File resource = new ClassPathResource("quantity_received_response_atlasItems.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    Gson gson = new Gson();
    RdsReceiptsResponse rdsReceiptsResponse =
        gson.fromJson(mockResponse, RdsReceiptsResponse.class);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        ReceivingUtils.handleRdsResponseForInProgressPOs(rdsReceiptsResponse);
    assertNotNull(rdsReceiptsResponse);
    assertNotNull(receivedQuantityResponseFromRDS);
  }

  @Test
  public void testHandleRdsResponseForInProgressPOs_ErrorMap() throws IOException {
    File resource = new ClassPathResource("quantity_received_error.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    Gson gson = new Gson();
    RdsReceiptsResponse rdsReceiptsResponse =
        gson.fromJson(mockResponse, RdsReceiptsResponse.class);

    ReceivedQuantityResponseFromRDS responseFromRDS =
        ReceivingUtils.handleRdsResponseForInProgressPOs(rdsReceiptsResponse);
    assertNotNull(responseFromRDS);
    assertEquals(responseFromRDS.getErrorResponseMapByPoAndPoLine(), Collections.EMPTY_MAP);
    assertNotNull(responseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertNotNull(rdsReceiptsResponse);
  }

  @Test
  public void testHandleRdsResponseForInProgressPOs_MultiPos_TotalQtyValidation()
      throws IOException {
    File resource = new ClassPathResource("nimServices_GetPoPoLine_MultiPos.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    Gson gson = new Gson();
    RdsReceiptsResponse rdsReceiptsResponse =
        gson.fromJson(mockResponse, RdsReceiptsResponse.class);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        ReceivingUtils.handleRdsResponseForInProgressPOs(rdsReceiptsResponse);
    assertNotNull(rdsReceiptsResponse);
    assertNotNull(receivedQuantityResponseFromRDS);
    assertEquals(
        receivedQuantityResponseFromRDS
            .getReceivedQtyMapByPoAndPoLine()
            .get("8579817978-1")
            .intValue(),
        2150);
  }

  @Test
  public void test_isValidSmartLabelFormat() throws ReceivingException {

    boolean isValidLabelFormat1 = ReceivingUtils.isValidSmartLabelFormat("234536278976534256");
    assertTrue(isValidLabelFormat1);

    boolean isValidLabelFormat2 = ReceivingUtils.isValidSmartLabelFormat("be234536278976534256");
    assertFalse(isValidLabelFormat2);

    boolean isValidLabelFormat3 = ReceivingUtils.isValidSmartLabelFormat("");
    assertFalse(isValidLabelFormat3);
  }

  @Test
  public void testConvertDateFormat() throws ReceivingException {
    String utcDate = ReceivingUtils.convertDateFormat("Jun 12, 2024 3:13:52 PM");
    assertTrue(utcDate.contains("Z"));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testConvertDateFormat_wrongFormat() throws ReceivingException {
    String utcDate = ReceivingUtils.convertDateFormat(String.valueOf(new Date()));
    assertTrue(utcDate.contains("Z"));
  }

  @Test
  public void testHasInductedIntoMech_nulls() {
    String automationType = null;
    Boolean isPrime = null;
    assertFalse(ReceivingUtils.hasInductedIntoMech(automationType, isPrime));
  }

  @Test
  public void testHasInductedIntoMech_mech_swisslog() {
    String automationType = AUTOMATION_TYPE_SWISSLOG;
    Boolean isPrime = null;
    assertTrue(ReceivingUtils.hasInductedIntoMech(automationType, isPrime));
  }

  @Test
  public void testHasInductedIntoMech_mech_swisslog_isPrimeFalse() {
    String automationType = AUTOMATION_TYPE_SWISSLOG;
    Boolean isPrime = false;
    assertTrue(ReceivingUtils.hasInductedIntoMech(automationType, isPrime));
  }

  @Test
  public void testHasInductedIntoMech_mech_dematic_null_isPrimeNull() {
    String automationType = AUTOMATION_TYPE_DEMATIC;
    Boolean isPrime = null;
    assertFalse(ReceivingUtils.hasInductedIntoMech(automationType, isPrime));
  }

  @Test
  public void testHasInductedIntoMech_mech_dematic() {
    String automationType = AUTOMATION_TYPE_DEMATIC;
    Boolean isPrime = true;
    assertTrue(ReceivingUtils.hasInductedIntoMech(automationType, isPrime));
  }

  @Test
  public void testHasInductedIntoMech_mech_schaefer() {
    String automationType = AUTOMATION_TYPE_SCHAEFER;
    Boolean isPrime = true;
    assertTrue(ReceivingUtils.hasInductedIntoMech(automationType, isPrime));
  }

  @Test
  public void testIsMechContainer_null() {
    Map<String, Object> miscInfo = null;
    assertFalse(ReceivingUtils.isMechContainer(miscInfo));
  }

  @Test
  public void testIsMechContainer_OtherThan_IS_MECH_CONTAINER() {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("OtherThan_IS_MECH_CONTAINER", true);
    assertFalse(ReceivingUtils.isMechContainer(containerMiscInfo));
  }

  @Test
  public void testIsMechContainer_IS_MECH_CONTAINER_missing() {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("OtherThan_IS_MECH_CONTAINER", true);
    assertFalse(ReceivingUtils.isMechContainer(containerMiscInfo));
  }

  @Test
  public void testIsMechContainer_IS_MECH_CONTAINER_false() {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(IS_MECH_CONTAINER, false);
    assertFalse(ReceivingUtils.isMechContainer(containerMiscInfo));
  }

  @Test
  public void testIsMechContainer_IS_MECH_CONTAINER_true() {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(IS_MECH_CONTAINER, true);
    assertTrue(ReceivingUtils.isMechContainer(containerMiscInfo));
  }

  @Test
  public void test_isMoveTypeAndStatusPresent() {
    List<String> containerMoveTypeAndStatuses = Arrays.asList("HaulPending");
    assertTrue(
        ReceivingUtils.isMoveTypeAndStatusPresent(
            containerMoveTypeAndStatuses, "haulPENDING,haulOPEN,putawayPENDING,putawayOPEN"));
  }

  @Test
  public void test_isReceiveCorrectionPastThreshold_nulls() {
    assertFalse(isReceiveCorrectionPastThreshold(null, null));
  }

  @Test
  public void test_isReceiveCorrectionPastThreshold_3DaysAgo() {
    LocalDate finalizedDate_31DaysAgo = LocalDate.now().minusDays(3); // 3 days ago
    String allowedDaysAfterFinalised = "30";
    assertFalse(
        "Receiving correction can be performed before 30 days i.e just 3 days ago",
        isReceiveCorrectionPastThreshold(finalizedDate_31DaysAgo, allowedDaysAfterFinalised));
  }

  @Test
  public void test_isReceiveCorrectionPastThreshold_30DaysAgo() {
    LocalDate finalizedDate_31DaysAgo = LocalDate.now().minusDays(30); // 30 days ago
    String allowedDaysAfterFinalised = "30";
    assertFalse(
        "Receiving correction can be performed exactly 30 days ago",
        isReceiveCorrectionPastThreshold(finalizedDate_31DaysAgo, allowedDaysAfterFinalised));
  }

  @Test
  public void test_isReceiveCorrectionPastThreshold_31DaysAgo() {
    LocalDate finalizedDate_31DaysAgo = LocalDate.now().minusDays(31); // 31 days ago
    String allowedDaysAfterFinalised = "30";
    assertTrue(
        "Receiving correction can not be performed after 30 days from Receipt, i.e 1 day after threshold.",
        isReceiveCorrectionPastThreshold(finalizedDate_31DaysAgo, allowedDaysAfterFinalised));
  }

  @Test
  public void test_isReceiveCorrectionPastThreshold_50DaysAgo() {
    LocalDate finalizedDate_31DaysAgo = LocalDate.now().minusDays(50); // 50 days ago
    String allowedDaysAfterFinalised = "30";
    assertTrue(
        "Receiving correction can not be performed after 30 days from Receipt, i.e 20 day after threshold.",
        isReceiveCorrectionPastThreshold(finalizedDate_31DaysAgo, allowedDaysAfterFinalised));
  }

  @Test
  public void test_enrichKafkaHeaderForRapidRelayer() {
    Map<String, Object> headers =
        enrichKafkaHeaderForRapidRelayer(new HashMap<>(), "14c2-4138-ee59");
    assertEquals("14c2-4138-ee59", headers.get("WMT-CorrelationId"));
    assertEquals("receiving-api", headers.get("requestOriginator"));
    assertEquals("US", headers.get("facilityCountryCode"));
  }

  @Test
  public void testValidateMoveByHaulPutawayCombo_null() {
    // if no moves at all then allow VTR
    List<String> containerMoveTypeAndStatuses = null;
    assertTrue(ReceivingUtils.validateMoveByHaulPutawayCombo(null));
  }

  @Test
  public void testValidateMoveByHaulPutawayCombo_noHaul_NoPutaway() {
    // if no haul And no putaway listed then allow
    List<String> containerMoveTypeAndStatuses = Collections.singletonList("MechQA");
    assertTrue(ReceivingUtils.validateMoveByHaulPutawayCombo(containerMoveTypeAndStatuses));
  }

  @Test
  public void testValidateMoveByHaulPutawayCombo_noHaul_PutAwayOpen() {
    // if no haul listed then check putaway - if putaway-open  or putaway-failed then allow
    List<String> containerMoveTypeAndStatuses = Collections.singletonList(PUTAWAY + OPEN);
    assertTrue(ReceivingUtils.validateMoveByHaulPutawayCombo(containerMoveTypeAndStatuses));
  }

  @Test
  public void testValidateMoveByHaulPutawayCombo_noHaul_PutAwayFailed() {
    // if no haul listed then check putaway - if putaway-open  or putaway-failed then allow
    List<String> containerMoveTypeAndStatuses = Collections.singletonList(PUTAWAY + FAILED);
    assertTrue(ReceivingUtils.validateMoveByHaulPutawayCombo(containerMoveTypeAndStatuses));
  }

  @Test
  public void testValidateMoveByHaulPutawayCombo_HaulOpen_PutAwayFailed() {
    // isHaulOpenOrFailed && isPutAwayOpenOrFailed;
    List<String> containerMoveTypeAndStatuses = Arrays.asList(PUTAWAY + FAILED, HAUL + OPEN);
    assertTrue(ReceivingUtils.validateMoveByHaulPutawayCombo(containerMoveTypeAndStatuses));
  }

  @Test
  public void testisMoveTypeAndStatusPresent() {
    List<String> containerMoveTypeAndStatuses = Arrays.asList("HaulPending");
    assertTrue(
        ReceivingUtils.isMoveTypeAndStatusPresent(
            containerMoveTypeAndStatuses, "haulPENDING,haulOPEN,putawayPENDING,putawayOPEN"));
  }
}
