package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.DISPOSITION_RETURN_TO_VENDOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.ExceptionInfo;
import com.walmart.move.nim.receiving.rx.model.FixitAttpRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.libs.google.gson.JsonObject;
import io.strati.libs.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EpcisHelperTest {

  public static final String ARRIVE_EVENT_SGTIN =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\",\"epcList\":[\"(01)01123840356119(21)SN345678\"],"
          + "\"action\":\"OBSERVE\",\"bizStep\":\"urn:epcglobal:cbv:bizstep:arriving\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:in_progress\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"sourceList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party_id\",\"value\":\"123243\"}],"
          + "\"destinationList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party\","
          + "\"value\":\"urn:epc:id:sgln:0078742.02775.0\"}],\"ich\":true,\"isValidationPerformed\":true}";

  public static final String ARRIVE_EVENT_SSCC =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\",\"epcList\":[\"(00)78979879089034\"],"
          + "\"action\":\"OBSERVE\",\"bizStep\":\"urn:epcglobal:cbv:bizstep:arriving\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:in_progress\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"sourceList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party_id\",\"value\":\"123243\"}],"
          + "\"destinationList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party\","
          + "\"value\":\"urn:epc:id:sgln:0078742.02775.0\"}],\"ich\":true,\"isValidationPerformed\":true}";

  public static final String UNPACK_CASE =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\","
          + "\"action\":\"DELETE\",\"bizStep\":\"urn:epcglobal:cbv:bizstep:unpacking_case\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:in_progress\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\",\"ich\":true,"
          + "\"isValidationPerformed\":true,\"parentID\":\"(00)78979879089034\"}";
  public static final String DECOMISSION_EVENT =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\",\"epcList\":[\"(01)01123840356119(21)SN345678\"],"
          + "\"action\":\"DELETE\",\"bizStep\":\"urn:epcglobal:cbv:bizstep:decommissioning\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:inactive\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\",\"ich\":true,"
          + "\"isValidationPerformed\":true,\"reasonCode\":\"CONCEILED|VENDOR\"}";

  public static final String DECOMISSION_EVENT_SSCC =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\",\"epcList\":[\"(00)78979879089034\"],"
          + "\"action\":\"DELETE\",\"bizStep\":\"urn:epcglobal:cbv:bizstep:decommissioning\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:inactive\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\",\"ich\":true,"
          + "\"isValidationPerformed\":true,\"reasonCode\":\"CONCEILED|VENDOR\"}";

  public static final String RTV_EVENT_CASE_WMRA =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\",\"epcList\":[\"(01)01123840356119(21)SN345678\"],\"action\":\"OBSERVE\","
          + "\"bizStep\":\"urn:epcglobal:cbv:bizstep:shipping_return\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:returned\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\",\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizTransactionList\":[{\"type\":\"urn:epcglobal:cbv:btt:po\","
          + "\"value\":\"urn:epcglobal:cbv:bt:0078742027753:5344898983\"},"
          + "{\"type\":\"urn:epcglobal:cbv:btt:desadv\",\"value\":\"urn:epcglobal:cbv:bt:0078742027753:4389798\"}],"
          + "\"sourceList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party\",\"value\":\"urn:epc:id:sgln:0078742.02775.0\"}],"
          + "\"destinationList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party_id\",\"value\":\"123243\"}],"
          + "\"ich\":true,\"isValidationPerformed\":true,\"reasonCode\":\"CONCEILED|VENDOR\",\"returnType\":\"PREREC\"}";

  public static final String RTV_EVENT_CASE_WMRA_ABSENT =
      "{\"eventTime\":\"%s\",\"eventTimeZoneOffset\":\"+00:00\","
          + "\"recordTime\":\"%s\",\"epcList\":[\"(01)01123840356119(21)SN345678\"],\"action\":\"OBSERVE\","
          + "\"bizStep\":\"urn:epcglobal:cbv:bizstep:shipping_return\","
          + "\"disposition\":\"urn:epcglobal:cbv:disp:returned\","
          + "\"readPoint\":\"urn:epc:id:sgln:0078742.02775.0\",\"bizLocation\":\"urn:epc:id:sgln:0078742.02775.0\","
          + "\"bizTransactionList\":[{\"type\":\"urn:epcglobal:cbv:btt:po\","
          + "\"value\":\"urn:epcglobal:cbv:bt:0078742027753:5344898983\"}],"
          + "\"sourceList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party\",\"value\":\"urn:epc:id:sgln:0078742.02775.0\"}],"
          + "\"destinationList\":[{\"type\":\"urn:epcglobal:cbv:sdt:owning_party_id\",\"value\":\"123243\"}],"
          + "\"ich\":true,\"isValidationPerformed\":true,\"reasonCode\":\"CONCEILED|VENDOR\",\"returnType\":\"PREREC\"}";
  public static final String GDM_DECOMISSION_CASE_RESPONSE =
      "{\"shipment\":{\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\","
          + "\"shipmentNumber\":\"7172554417358423\",\"documentType\":\"EPCIS\","
          + "\"packs\":[{\"documentPackId\":\"88528711328996094_20200608_32899_MCC_US_00106002438511331753\","
          + "\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\",\"receivingStatus\":\"4\","
          + "\"multiskuPack\":false,\"partialPack\":false}]}}";
  public static final String GDM_DECOMISSION_EA =
      "{\"shipment\":{\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\","
          + "\"shipmentNumber\":\"7172554417358423\",\"documentType\":\"EPCIS\","
          + "\"packs\":[{\"documentPackId\":\"88528711328996094_20200608_32899_MCC_US_00106002438511331753\","
          + "\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\","
          + "\"items\":[{\"gtin\":\"01123840356119\",\"serial\":\"SN345678\",\"receivingStatus\":\"4\","
          + "\"documentPackId\":\"88528711328996094_20200608_32899_MCC_US_00106002438511331753\"}],"
          + "\"multiskuPack\":false,\"partialPack\":false}]}}";

  public static final String GDM_DECOMISSION_SSCC =
      "{\"shipment\":{\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\","
          + "\"shipmentNumber\":\"7172554417358423\",\"documentType\":\"EPCIS\","
          + "\"packs\":[{\"documentPackId\":\"88528711328996094_20200608_32899_MCC_US_00106002438511331753\","
          + "\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\",\"receivingStatus\":\"4\","
          + "\"multiskuPack\":false,\"partialPack\":false}]}}";
  public static final String GDM_RTV_CASE_RESPONSE =
      "{\"shipment\":{\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\","
          + "\"shipmentNumber\":\"7172554417358423\",\"documentType\":\"EPCIS\","
          + "\"packs\":[{\"documentPackId\":\"88528711328996094_20200608_32899_MCC_US_00106002438511331753\","
          + "\"documentId\":\"7172554417358423_20191106_719468_VENDOR_US\",\"receivingStatus\":\"5\","
          + "\"multiskuPack\":false,\"partialPack\":false}]}}";
  public static final String APP_CONFIG_GLN = "{  \"32897\": \"0078742027753\"}";
  @InjectMocks private EpsicHelper epsicHelper;

  @InjectMocks private EpcisService epcisService;

  @Mock private TenantSpecificConfigReader configUtils;

  @Mock private AppConfig appConfig;

  @Mock private RestUtils restUtils;

  @Mock private RxManagedConfig rxManagedConfig;

  @Mock private OutboxConfig outboxConfig;

  @InjectMocks RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;

  @Captor private ArgumentCaptor<String> gdmRequest;
  private Gson gson = new Gson();

  @Mock private OutboxEventSinkService outboxEventSinkService;

  @Captor private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

  private JsonParser jsonParser = new JsonParser();

  @BeforeClass
  public void initMocks() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(epcisService, "gson", gson);
    ReflectionTestUtils.setField(
        epcisService, "rxCompleteInstructionOutboxHandler", rxCompleteInstructionOutboxHandler);
    ReflectionTestUtils.setField(
        epsicHelper, "rxCompleteInstructionOutboxHandler", rxCompleteInstructionOutboxHandler);
    ReflectionTestUtils.setField(epsicHelper, "gson", gson);
    ReflectionTestUtils.setField(epsicHelper, "epcisService", epcisService);
    ReflectionTestUtils.setField(rxCompleteInstructionOutboxHandler, "gson", gson);
    ReflectionTestUtils.setField(rxCompleteInstructionOutboxHandler, "epcisService", epcisService);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
  }

  @Test
  public void testEpcisvalidateRequestExecptionInfo() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.setExceptionInfo(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_EXCEPTIONINFO_MISSING,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestDisposition() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setDisposition(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_DIPOSITION_MISSING,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestVendor() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setReasonCode("CONCEILED|VENDOR");
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_VENDOR_MISSING, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestPo() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setReasonCode("CONCEILED|VENDOR");
    request.getExceptionInfo().setVendorNbrDeptSeq("123243");
    request.getExceptionInfo().setDisposition(DISPOSITION_RETURN_TO_VENDOR);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_PO_MISSING, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestUom() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setReasonCode("CONCEILED|VENDOR");
    request.getExceptionInfo().setVendorNbrDeptSeq("123243");
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(ReceivingException.UOM_MISSING, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestScannerInfo() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setReasonCode("CONCEILED|VENDOR");
    request.getExceptionInfo().setVendorNbrDeptSeq("123243");
    request.getExceptionInfo().setPurchaseReference("45464123243");
    request.setScannedDataList(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.NO_SCANNED_CASE_OR_EACHES_TO_PUBLISH,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestScannerGtin() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setReasonCode("CONCEILED|VENDOR");
    request.getExceptionInfo().setVendorNbrDeptSeq("123243");
    request.getExceptionInfo().setPurchaseReference("45464123243");
    request.getScannedDataList().get(0).setGtin(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.NO_SCANNED_CASE_OR_EACHES_TO_PUBLISH,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestScannerSerial() {
    FixitAttpRequest request = getMockFixitAttpRequest();
    request.getExceptionInfo().setReasonCode("CONCEILED|VENDOR");
    request.getExceptionInfo().setVendorNbrDeptSeq("123243");
    request.getExceptionInfo().setPurchaseReference("45464123243");
    request.getScannedDataList().get(0).setSerial(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.NO_SCANNED_CASE_OR_EACHES_TO_PUBLISH,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestScannerShipment() {
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getScannedDataList().get(0).setShipmentNumber(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_SHIPMENT_NUMBER_MISSING,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestScannerDoc() {
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getScannedDataList().get(0).setDocumentId(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_DOCUMENT_ID_MISSING,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testEpcisvalidateRequestScannerDocPack() {
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getScannedDataList().get(0).setDocumentPackId(null);
    try {
      epsicHelper.validateRequest(request);
      Assert.fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.FIXIT_EVENT_DOCUMENT_PACK_ID_MISSING,
          e.getErrorResponse().getErrorMessage());
    }
  }

  private FixitAttpRequest getMockFixitAttpRequest() {
    FixitAttpRequest request = new FixitAttpRequest();
    ManufactureDetail details = new ManufactureDetail();

    details.setLot("12345678");
    details.setGtin("01123840356119");
    details.setSerial("SN345678");
    details.setExpiryDate("20-05-05");
    List<ManufactureDetail> scannedDetails = Arrays.asList(details);
    request.setScannedDataList(scannedDetails);
    ExceptionInfo info = new ExceptionInfo();

    info.setDisposition(RxConstants.DISPOSITION_DESTROY);
    request.setExceptionInfo(info);
    return request;
  }

  @Test
  public void testPublishFixitEventsToAttpDestroyGDMFail() {
    // GDM call mocks
    outboxEventCaptor.getAllValues().clear();
    doReturn(new ResponseEntity<>(HttpStatus.CONFLICT))
        .when(restUtils)
        .put(anyString(), any(HttpHeaders.class), any(), any());
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    doReturn("{\n" + "  \"32897\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    doReturn("").when(appConfig).getGdmBaseUrl();

    try {
      epsicHelper.publishFixitEventsToAttp(request, makeHeaders());
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.CONFLICT);
      assertEquals(GDM_UPDATE_STATUS_API_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(GDM_UPDATE_STATUS_API_ERROR, e.getErrorResponse().getErrorMessage());
      assertEquals(GDM_UPDATE_STATUS_API_ERROR_HEADER, e.getErrorResponse().getErrorHeader());
      return;
    }
    Assert.fail();
  }

  @Test
  public void testPublishFixitEventsToAttpDestroy() {
    // GDM call mocks
    outboxEventCaptor.getAllValues().clear();
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(restUtils)
        .put(anyString(), any(HttpHeaders.class), any(), gdmRequest.capture());
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    doReturn("{\n" + "  \"32897\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    doReturn("").when(appConfig).getGdmBaseUrl();

    // OutBox call mocks
    doReturn(true).when(outboxEventSinkService).saveEvent(outboxEventCaptor.capture());
    doReturn(5).when(rxManagedConfig).getAttpEventLagTimeInterval();

    try {
      epsicHelper.publishFixitEventsToAttp(request, makeHeaders());
    } catch (ReceivingException e) {
      Assert.fail();
    }

    // Verify GDM Call
    assertEquals(
        gdmRequest.getAllValues().get(gdmRequest.getAllValues().size() - 1),
        GDM_DECOMISSION_CASE_RESPONSE);
    // Verify OutBox Call
    verify(outboxEventSinkService, times(2)).saveEvent(any());
    String eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 2)
            .getPayloadRef()
            .getData()
            .getBody();
    String[] eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(ARRIVE_EVENT_SGTIN, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 1)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);

    assertEquals(eventbody, String.format(DECOMISSION_EVENT, eventTime[0], eventTime[1]));
  }

  @Test
  public void testPublishFixitEventsToAttpDestroyEA() {
    // GDM call mocks
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(restUtils)
        .put(anyString(), any(HttpHeaders.class), any(), gdmRequest.capture());
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getScannedDataList().get(0).setReportedUom(EA);
    doReturn("{\n" + "  \"32897\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    doReturn("").when(appConfig).getGdmBaseUrl();

    // OutBox call mocks
    doReturn(true).when(outboxEventSinkService).saveEvent(outboxEventCaptor.capture());
    doReturn(5).when(rxManagedConfig).getAttpEventLagTimeInterval();

    try {
      epsicHelper.publishFixitEventsToAttp(request, makeHeaders());
    } catch (ReceivingException e) {
      Assert.fail();
    }

    // Verify GDM Call
    assertEquals(
        gdmRequest.getAllValues().get(gdmRequest.getAllValues().size() - 1), GDM_DECOMISSION_EA);
    // Verify OutBox Call
    String eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 2)
            .getPayloadRef()
            .getData()
            .getBody();
    String[] eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(ARRIVE_EVENT_SGTIN, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 1)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(DECOMISSION_EVENT, eventTime[0], eventTime[1]));
  }

  @Test
  public void testPublishFixitEventsToAttpDestroySSCC() {
    // GDM call mocks
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(restUtils)
        .put(anyString(), any(HttpHeaders.class), any(), gdmRequest.capture());
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getScannedDataList().get(0).setSscc("78979879089034");
    request.getScannedDataList().get(0).setGtin(null);
    request.getScannedDataList().get(0).setSerial(null);
    doReturn("{\n" + "  \"32897\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    doReturn("").when(appConfig).getGdmBaseUrl();

    // OutBox call mocks
    doReturn(true).when(outboxEventSinkService).saveEvent(outboxEventCaptor.capture());
    doReturn(5).when(rxManagedConfig).getAttpEventLagTimeInterval();

    try {
      epsicHelper.publishFixitEventsToAttp(request, makeHeaders());
    } catch (ReceivingException e) {
      Assert.fail();
    }

    // Verify GDM Call
    assertEquals(
        gdmRequest.getAllValues().get(gdmRequest.getAllValues().size() - 1), GDM_DECOMISSION_SSCC);
    // Verify OutBox Call
    String eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 3)
            .getPayloadRef()
            .getData()
            .getBody();
    String[] eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(ARRIVE_EVENT_SSCC, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 2)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(UNPACK_CASE, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 1)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(DECOMISSION_EVENT_SSCC, eventTime[0], eventTime[1]));
  }

  @Test
  public void testPublishFixitEventsToAttpRTV() {
    outboxEventCaptor.getAllValues().clear();
    // GDM call mocks
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(restUtils)
        .put(anyString(), any(HttpHeaders.class), any(), gdmRequest.capture());
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getScannedDataList().get(0).setSscc("78979879089034");
    request.getExceptionInfo().setDisposition(DISPOSITION_RETURN_TO_VENDOR);
    doReturn(APP_CONFIG_GLN).when(appConfig).getGlnDetails();
    doReturn("").when(appConfig).getGdmBaseUrl();

    // OutBox call mocks
    doReturn(true).when(outboxEventSinkService).saveEvent(outboxEventCaptor.capture());
    doReturn(5).when(rxManagedConfig).getAttpEventLagTimeInterval();

    try {
      epsicHelper.publishFixitEventsToAttp(request, makeHeaders());
    } catch (ReceivingException e) {
      Assert.fail();
    }

    // Verify GDM Call
    assertEquals(
        gdmRequest.getAllValues().get(gdmRequest.getAllValues().size() - 1), GDM_RTV_CASE_RESPONSE);
    // Verify OutBox Call
    String eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 3)
            .getPayloadRef()
            .getData()
            .getBody();
    String[] eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(ARRIVE_EVENT_SSCC, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 2)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(UNPACK_CASE, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 1)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(RTV_EVENT_CASE_WMRA_ABSENT, eventTime[0], eventTime[1]));
  }

  @Test
  public void testPublishFixitEventsToAttpRTVWithWmra() {
    outboxEventCaptor.getAllValues().clear();
    // GDM call mocks
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(restUtils)
        .put(anyString(), any(HttpHeaders.class), any(), gdmRequest.capture());
    FixitAttpRequest request = getMockFixitAttpRequestSGTIN();
    request.getExceptionInfo().setDisposition(DISPOSITION_RETURN_TO_VENDOR);
    request.getExceptionInfo().setWmra("4389798");
    doReturn(APP_CONFIG_GLN).when(appConfig).getGlnDetails();
    doReturn("").when(appConfig).getGdmBaseUrl();

    // OutBox call mocks
    doReturn(true).when(outboxEventSinkService).saveEvent(outboxEventCaptor.capture());
    doReturn(5).when(rxManagedConfig).getAttpEventLagTimeInterval();

    try {
      epsicHelper.publishFixitEventsToAttp(request, makeHeaders());
    } catch (ReceivingException e) {
      Assert.fail();
    }

    // Verify GDM Call
    assertEquals(
        gdmRequest.getAllValues().get(gdmRequest.getAllValues().size() - 1), GDM_RTV_CASE_RESPONSE);
    // Verify OutBox Call
    String eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 2)
            .getPayloadRef()
            .getData()
            .getBody();
    String[] eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(ARRIVE_EVENT_SGTIN, eventTime[0], eventTime[1]));

    eventbody =
        outboxEventCaptor
            .getAllValues()
            .get(outboxEventCaptor.getAllValues().size() - 1)
            .getPayloadRef()
            .getData()
            .getBody();
    eventTime = getEventTime(eventbody);
    assertEquals(eventbody, String.format(RTV_EVENT_CASE_WMRA, eventTime[0], eventTime[1]));
  }

  private String[] getEventTime(String eventbody) {
    JsonParser jsonParser = new JsonParser();
    JsonObject jo = (JsonObject) jsonParser.parse(eventbody);
    return new String[] {jo.get("eventTime").getAsString(), jo.get("recordTime").getAsString()};
  }

  private static HttpHeaders makeHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set("Content-Type", "application/json");
    httpHeaders.set("Accept", "application/json");
    httpHeaders.set("facilityCountryCode", "US");
    httpHeaders.set("facilityNum", "32897");
    httpHeaders.set("WMT-UserId", "FIXIT");
    return httpHeaders;
  }

  private FixitAttpRequest getMockFixitAttpRequestSGTIN() {
    FixitAttpRequest request = new FixitAttpRequest();
    ManufactureDetail details = new ManufactureDetail();

    details.setLot("12345678");
    details.setGtin("01123840356119");
    details.setSerial("SN345678");
    details.setExpiryDate("20-05-05");
    details.setReportedUom("CA");

    details.setDocumentPackId("88528711328996094_20200608_32899_MCC_US_00106002438511331753");
    details.setDocumentId("7172554417358423_20191106_719468_VENDOR_US");
    details.setShipmentNumber("7172554417358423");

    List<ManufactureDetail> scannedDetails = Arrays.asList(details);
    request.setScannedDataList(scannedDetails);
    ExceptionInfo info = new ExceptionInfo();
    info.setDisposition(RxConstants.DISPOSITION_DESTROY);
    info.setReasonCode("CONCEILED|VENDOR");
    info.setVendorNbrDeptSeq("123243");
    info.setPurchaseReference("5344898983");
    request.setExceptionInfo(info);
    return request;
  }
}
