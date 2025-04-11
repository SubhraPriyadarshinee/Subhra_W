package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.PO_VERSION_MISMATCH;
import static com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderErrorCode.getErrorValue;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.RECEIPTS_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.RECEIPTS_NOT_FOUND_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.builder.*;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSDeliveryDetailsResponse;
import com.walmart.move.nim.receiving.core.client.gls.model.POLine;
import com.walmart.move.nim.receiving.core.client.gls.model.POS;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.exception.Error;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.entity.Rejections;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloadingProcessor;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ShipmentInfo;
import com.walmart.move.nim.receiving.core.message.publisher.DefaultDeliveryMessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmDeliveryHeaderDetails;
import com.walmart.move.nim.receiving.core.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.osdr.service.DefaultOsdrService;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;
import com.walmart.move.nim.receiving.core.repositories.*;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.data.MockS2SResponseFromGDM;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.DeliveryUnloaderEventType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.http.nio.reactor.IOReactorException;
import io.strati.libs.joda.time.DateTime;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.rules.ExpectedException;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test cases for delivery services
 *
 * @author g0k0072
 */
public class DeliveryServiceTest extends ReceivingTestBase {
  @Mock private RestUtils restUtils;
  @Mock private SimpleRestConnector simpleRestConnector;
  @Mock private RestConnector retryableRestConnector;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private DeliveryUnloadingProcessor deliveryUnloadingProcessor;
  @Mock private AppConfig appConfig;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private InstructionRepository instructionRepository;
  @Mock private RecordOSDRResponseBuilder recordOSDRResponseBuilder;
  @Mock private ConfirmPoResponseBuilder confirmPoResponseBuilder;
  @Mock private POHashKeyBuilder poHashKeyBuilder;
  @Mock private DeliveryItemOverrideRepository deliveryItemOverrideRepository;
  @Mock private DeliveryWithOSDRResponseBuilder deliveryWithOSDRResponseBuilder;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private DCFinRestApiClient mockDCFinRestApiClient;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private DockTagService dockTagService;
  @Mock private InstructionService instructionService;
  @Mock private GDMRestApiClient gdmRestApiClient;

  @InjectMocks private DefaultOsdrService defaultOsdrService;
  @Mock private OsdrService osdrService;
  @PersistenceContext EntityManager entityManager;
  @InjectMocks @Spy private DeliveryServiceImpl deliveryService;
  @InjectMocks private DeliveryStatusPublisher deliveryStatusPublisherInjectMocked;
  @InjectMocks private ReceiptCustomRepository receiptCustomRepositoryInjectMocked;

  @Mock private ReceiptCustomRepository receiptCustomRepository;
  @Mock private ContainerItemCustomRepository containerItemCustomRepository;

  @InjectMocks private ReceiptService receiptServiceInjectMocked;
  @InjectMocks private DefaultDeliveryMessagePublisher defaultDeliveryMessagePublisher;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private DefaultCompleteDeliveryProcessor completeDeliveryProcessor;
  @Autowired ReceiptRepository receiptRepositoryAutowired;
  @Mock private AsnToDeliveryDocumentsCustomMapper asnToDeliveryDocumentsCustomMapper;
  @Mock private GlsRestApiClient glsRestApiClient;
  @Mock private OSDRRecordCountAggregator osdrRecordCountAggregator;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private DefaultDeliveryUnloaderProcessor defaultDeliveryUnloaderProcessor;
  @Mock private RetryableRestConnector restConnector;
  @Mock private RejectionsRepository rejectionsRepository;

  private List<ReceiptSummaryResponse> receiptSummaryEachesResponse;
  private List<ContainerPoLineQuantity> containerPoLineQuantityListMatching;
  private List<ContainerPoLineQuantity> containerPoLineQuantityListMatchingNot;
  DeliveryInfo expectedDeliverInfoResponse = new DeliveryInfo();

  private final String url = "http://localhost:8080";
  private String deliveryNumber = "123231212";
  private String doorNumber = "D101";
  private String facilityNumber = "4093";
  private String poNbr = "4445530688";
  private List<String> purchaseReferenceNumbers;
  private List<String> poList;
  private ConfirmPurchaseOrdersRequest mockPoCloseRequest;
  private Map<String, Object> mockHttpHeaders = GdcHttpHeaders.getMockHeadersMap();
  private Gson gson;
  private Gson gsonForInstantAdapter;
  private GdmError gdmError;
  public final ExpectedException exception = ExpectedException.none();
  final String po1 = "7836237741";
  private DeliveryMetaData mockDeliveryMetaData;
  private Date lastUpdatedDate;
  private List<String> deliveryStatusList;

  ArgumentCaptor<DeliveryInfo> deliveryInfoCaptor = ArgumentCaptor.forClass(DeliveryInfo.class);

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    deliveryStatusList = Arrays.asList("WRK");
    gson = new Gson();
    ReflectionTestUtils.setField(deliveryService, "gson", gson);
    gsonForInstantAdapter =
        new GsonBuilder().registerTypeAdapter(Instant.class, new GsonUTCInstantAdapter()).create();
    ReflectionTestUtils.setField(deliveryService, "gsonForInstantAdapter", gsonForInstantAdapter);
    ReflectionTestUtils.setField(
        deliveryService, "deliveryMetaDataService", deliveryMetaDataService);
    receiptSummaryEachesResponse = new ArrayList<>();
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));

    containerPoLineQuantityListMatching = new ArrayList<>();
    containerPoLineQuantityListMatching.add(
        new ContainerPoLineQuantity("9763140004", 1, Long.valueOf(96)));
    containerPoLineQuantityListMatching.add(
        new ContainerPoLineQuantity("9763140005", 1, Long.valueOf(96)));
    containerPoLineQuantityListMatching.add(
        new ContainerPoLineQuantity("9763140007", 1, Long.valueOf(144)));

    containerPoLineQuantityListMatchingNot = new ArrayList<>();
    containerPoLineQuantityListMatchingNot.add(
        new ContainerPoLineQuantity("9763140004", 1, Long.valueOf(11))); // expected 96 but has 11
    containerPoLineQuantityListMatchingNot.add(
        new ContainerPoLineQuantity("9763140005", 1, Long.valueOf(96)));
    containerPoLineQuantityListMatchingNot.add(
        new ContainerPoLineQuantity("9763140007", 1, Long.valueOf(44))); // expected 144 but has 44

    ReOpenDeliveryInfo reOpenDeliveryInfo = new ReOpenDeliveryInfo();
    reOpenDeliveryInfo.setDeliveryNumber(27777L);
    reOpenDeliveryInfo.setReceiverUserId("sysAdmin");
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId("WitronTest-1111-2222-abcd");

    expectedDeliverInfoResponse.setDeliveryNumber(1L);
    expectedDeliverInfoResponse.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE.name());
    expectedDeliverInfoResponse.setReceipts(receiptSummaryEachesResponse);
    expectedDeliverInfoResponse.setDoorNumber("D101");
    expectedDeliverInfoResponse.setTrailerNumber("TLR1001");
    expectedDeliverInfoResponse.setUserId(
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    expectedDeliverInfoResponse.setTs(new Date());

    purchaseReferenceNumbers = new ArrayList<>();
    purchaseReferenceNumbers.add(po1);
    purchaseReferenceNumbers.add("7836237742");
    purchaseReferenceNumbers.add("7836237743");
    purchaseReferenceNumbers.add("7836237744");
    purchaseReferenceNumbers.add("7836237745");

    poList = new ArrayList<>();
    poList.add(poNbr);
    mockPoCloseRequest = new ConfirmPurchaseOrdersRequest();
    mockPoCloseRequest.setPurchaseReferenceNumbers(poList);
    mockDeliveryMetaData = DeliveryMetaData.builder().deliveryNumber("12345789").build();
    lastUpdatedDate = new Date();
  }

  @Test
  public void testFindDeliveryDocument() {
    try {
      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.findDeliveryDocument(1l, "00016017039630", MockHttpHeaders.getHeaders());
      verify(simpleRestConnector, times(1))
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      AssertJUnit.assertTrue(false);
    }
  }

  private String updatedBy;

  @AfterMethod
  public void restRestUtilCalls() {
    reset(
        receiptCustomRepository,
        containerItemCustomRepository,
        simpleRestConnector,
        retryableRestConnector,
        restUtils,
        instructionRepository,
        receiptRepository,
        receiptService,
        deliveryStatusPublisher,
        finalizePORequestBodyBuilder,
        confirmPoResponseBuilder,
        asnToDeliveryDocumentsCustomMapper,
        gdmRestApiClient,
        deliveryService,
        glsRestApiClient,
        rejectionsRepository);
  }

  @Test
  public void testFindDeliveryDocument_EmptyResponseScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.findDeliveryDocument(1l, "00016017039630", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFindDeliveryDocument_ExceptionScenario_GDM_DOWN() {
    try {
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
      doThrow(new ResourceAccessException("IO Error."))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.findDeliveryDocument(1l, "00016017039630", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (GDMServiceUnavailableException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    } catch (ReceivingException e) {
      fail("should throw GDMServiceUnavailableException");
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFindDeliveryDocument_ExceptionScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      doThrow(
              new RestClientResponseException(
                  "Some error.",
                  INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  StandardCharsets.UTF_8))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.findDeliveryDocument(1l, "00016017039630", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryByDeliveryNumber() throws ReceivingException {
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    deliveryService.getDeliveryByDeliveryNumber(1l, MockHttpHeaders.getHeaders());
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetDeliveryByDeliveryNumber_EmptyResponseScenario() throws ReceivingException {
    try {
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(restUtils)
          .get(anyString(), any(), anyMap());
      deliveryService.getDeliveryByDeliveryNumber(1l, MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), "getDeliveryByDeliveryNumber");
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.DELIVERY_NOT_FOUND);
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetDeliveryByDeliveryNumber_ExceptionScenario_GDM_DOWN()
      throws ReceivingException {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
    try {
      doReturn(
              ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                  .headers((HttpHeaders) null)
                  .body("Error in fetching resource."))
          .when(restUtils)
          .get(anyString(), any(), anyMap());
      deliveryService.getDeliveryByDeliveryNumber(1l, MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), "getDeliveryByDeliveryNumber");
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetDeliveryByDeliveryNumber_ExceptionScenario() throws ReceivingException {
    try {
      doReturn(
              ResponseEntity.status(INTERNAL_SERVER_ERROR)
                  .headers((HttpHeaders) null)
                  .body("Internal Server Error."))
          .when(restUtils)
          .get(anyString(), any(), anyMap());
      deliveryService.getDeliveryByDeliveryNumber(1l, MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), "getDeliveryByDeliveryNumber");
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.DELIVERY_NOT_FOUND);
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  /* TODO: Get PO's approval before applying below condition
  @Test
  public void testCompleteDelivery_whenNoCasesHasBeenReceived() {
    try {
      doReturn(null)
          .when(receiptService)
          .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
      deliveryService.completeDelivery(1L, MockHttpHeaders.getHeaders());
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.COMPLETE_DELIVERY_ERROR_CODE);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.COMPLETE_DELIVERY_NO_RECEIVING_ERROR_MESSAGE);
    }
    verify(receiptService, times(1)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
  }*/

  @Test
  public void testCompleteDelivery_whenDeliveryHasOpenInstruction() {
    try {
      doReturn(1L)
          .when(instructionRepository)
          .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
      deliveryService.completeDelivery(1L, false, MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_CODE);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
    }
    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
  }

  @Test
  public void testCompleteDelivery_whenDeliveryHasNoOpenInstruction() throws ReceivingException {
    DeliveryInfo expectedResponse = new DeliveryInfo();
    expectedResponse.setDeliveryNumber(1L);
    expectedResponse.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    expectedResponse.setReceipts(receiptSummaryEachesResponse);
    expectedResponse.setUserId(
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    expectedResponse.setTs(new Date());
    try {
      doReturn(receiptSummaryEachesResponse)
          .when(receiptService)
          .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
      doReturn(0L)
          .when(instructionRepository)
          .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
      doReturn(expectedResponse)
          .when(deliveryStatusPublisher)
          .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());

      DeliveryInfo actualResponse =
          deliveryService.completeDelivery(1L, false, MockHttpHeaders.getHeaders());
      AssertJUnit.assertEquals(
          actualResponse.getDeliveryNumber(), expectedResponse.getDeliveryNumber());
      AssertJUnit.assertEquals(
          actualResponse.getDeliveryStatus(), expectedResponse.getDeliveryStatus());
      AssertJUnit.assertEquals(actualResponse.getTs(), expectedResponse.getTs());
      AssertJUnit.assertEquals(actualResponse.getUserId(), expectedResponse.getUserId());
      AssertJUnit.assertEquals(
          actualResponse.getReceipts().size(), expectedResponse.getReceipts().size());

    } catch (ReceivingException e) {
      AssertJUnit.assertTrue(false);
    }
    verify(receiptService, times(1)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
    verify(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
  }

  @Test
  public void testCompleteDeliveryWithUnconfirmedPO() {
    try {

      List<Receipt> receipts = new ArrayList<>();
      Receipt receipt = new Receipt();
      receipt.setDeliveryNumber(Long.valueOf(deliveryNumber));
      receipt.setPurchaseReferenceNumber(poNbr);
      receipt.setPurchaseReferenceLineNumber(1);
      receipt.setOsdrMaster(1);
      receipt.setFinalizeTs(null);
      receipts.add(receipt);

      doReturn(TRUE).when(tenantSpecificConfigReader).isPoConfirmationFlagEnabled(anyInt());
      doReturn(receipts)
          .when(receiptRepository)
          .findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(anyLong(), anyInt());
      doReturn(completeDeliveryProcessor)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));

      deliveryService.completeDelivery(
          Long.parseLong(deliveryNumber), false, MockHttpHeaders.getHeaders());

      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE);
      assertEquals(
          e.getErrorResponse().getErrorHeader(),
          ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_MESSAGE, 1));
    }

    verify(receiptRepository, times(1))
        .findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(anyLong(), anyInt());
  }

  @Test
  public void testGetContainerInfoByAsnBarcode_whenErrorResponseOtherThanHttpNotFoundFromGDM()
      throws ReceivingException {
    doReturn(new ResponseEntity<String>("Internal Server Error", INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorMessage(), "Internal Server Error");
    }
    doReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.GDM_SERVICE_DOWN);
    }
    verify(restUtils, times(2)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetContainerInfoByAsnBarcode_whenErrorResponseHttpNotFoundFromGDM()
      throws ReceivingException {
    doReturn(
            new ResponseEntity<String>(
                "{\"messages\":[{\"code\":\"GLS-IBD-BE-00026\",\"desc\":\"No container Found\",\"type\":\"error\"}]}",
                HttpStatus.NOT_FOUND))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_FOR_NO_MATCHING_ASN_ERROR_CODE);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.CREATE_INSTRUCTION_FOR_NO_MATCHING_ASN_ERROR_MESSAGE);
    }

    doReturn(
            new ResponseEntity<String>(
                "{\"messages\":[{\"code\":\"GLS-IBD-BE-00026\",\"desc\":\"Freight already received\",\"type\":\"error\"}]}",
                HttpStatus.NOT_FOUND))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.FREIGHT_ALREADY_RCVD);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          String.format(
              ReceivingException.CREATE_INSTRUCTION_FOR_ASN_LABEL_ALREADY_RECEIVED_ERROR_MESSAGE,
              "1"));
    }

    doReturn(new ResponseEntity<String>("null", HttpStatus.NOT_FOUND))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
      AssertJUnit.assertEquals(receivingException.getErrorResponse().getErrorMessage(), "null");
    }

    doReturn(new ResponseEntity<String>("{}", HttpStatus.NOT_FOUND))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
    }

    doReturn(
            new ResponseEntity<String>(
                "{\"messages\":[{\"code\":\"\",\"desc\":\"Unknown error\",\"type\":\"error\"}]}",
                HttpStatus.NOT_FOUND))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      deliveryService.getContainerInfoByAsnBarcode("1", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), INTERNAL_SERVER_ERROR);
    }

    verify(restUtils, times(5)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetContainerInfoByAsnBarcode_whenSuccessResponseFromGDM()
      throws ReceivingException {
    doReturn(new ResponseEntity<String>(MockS2SResponseFromGDM.MOCK_S2S_RESPONSE, HttpStatus.OK))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    try {
      ShipmentResponseData actualResponse =
          deliveryService.getContainerInfoByAsnBarcode(
              "00100077672010779635", MockHttpHeaders.getHeaders());
      AssertJUnit.assertEquals(actualResponse.getContainer().getLabel(), "00100077672010779635");
      // assertEquals(actualResponse.getAsnNumber(), "737295");
      AssertJUnit.assertEquals(actualResponse.getBolNumber(), "0000010776737295");
      // assertEquals(actualResponse.getDeliveryNumber(), "20030791");
      // assertEquals(actualResponse.getIsPallet(), Boolean.TRUE);
      // assertTrue(actualResponse.getChildContainerLabels().contains("00000077670100204725"));
      // assertEquals(actualResponse.getDestBuNbr(), Integer.valueOf(6036));
      AssertJUnit.assertEquals(
          actualResponse.getContainer().getDestinationNumber(), Integer.valueOf(6036));
      AssertJUnit.assertEquals(actualResponse.getContainer().getDestinationCountryCode(), "US");
      AssertJUnit.assertEquals(actualResponse.getContainer().getDestinationType(), "DC");
      // assertEquals(actualResponse.getWeight(), 146.0F);
      // assertEquals(actualResponse.getWeightUOM(), "lb");
      actualResponse
          .getContainer()
          .getContainers()
          .stream()
          .forEach(
              r -> {
                assertEquals(r.getLabel(), "00000077670100204725");
                // assertEquals(r.getAsnNumber(), "737295");
                // assertEquals(r.getBolNumber(), "0000010776737295");
                // assertEquals(r.getIsPallet(), Boolean.FALSE);
                assertEquals(r.getChannel(), "S2S");
                // assertEquals(r.getInvoiceNumber(), "338117271");
                // assertEquals(r.getDestBuNbr(), Integer.valueOf(5210));
                assertEquals(r.getDestinationNumber(), Integer.valueOf(5210));
                assertEquals(r.getDestinationCountryCode(), "US");
                assertEquals(r.getDestinationType(), "STORE");
                assertEquals(r.getSourceNumber(), Integer.valueOf(413356050));
                assertEquals(r.getSourceCountryCode(), "US");
                assertEquals(r.getSourceType(), "FC");

                r.getItems()
                    .stream()
                    .forEach(
                        i -> {
                          assertEquals(
                              i.getPurchaseOrder().getPurchaseReferenceNumber(), "0338117271");
                          assertEquals(
                              i.getPurchaseOrder().getPurchaseReferenceLineNumber(),
                              Integer.valueOf(0));
                          assertEquals(
                              i.getPurchaseOrder().getPurchaseReferenceType(), Integer.valueOf(88));
                          assertEquals(i.getItemUpc(), "0752113650145");
                          assertEquals(i.getItemQuantity(), Integer.valueOf(1));
                          assertEquals(i.getQuantityUOM(), ReceivingConstants.Uom.EACHES);
                          assertEquals(i.getItemNumber(), Integer.valueOf(0));
                        });
              });
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertTrue(false);
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetPOLineInfoFromGDM_GdmServiceDown() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));
    try {
      deliveryService.getPOLineInfoFromGDM(deliveryNumber, poNbr, 1, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(INTERNAL_SERVER_ERROR, e.getHttpStatus());
      AssertJUnit.assertEquals(
          ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      AssertJUnit.assertEquals(
          ReceivingException.GDM_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
      reset(restUtils);
    }
  }

  @Test
  public void testGetPOLineInfoFromGDM_returnEmptyResponse() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<String>("", HttpStatus.OK));

    try {
      deliveryService.getPOLineInfoFromGDM(deliveryNumber, poNbr, 1, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      AssertJUnit.assertEquals(
          ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      AssertJUnit.assertEquals(
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE, e.getErrorResponse().getErrorMessage());
      reset(restUtils);
    }
  }

  @Test
  public void testGetPOLineInfoFromGDM_returnException() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<String>("Unkown Error", INTERNAL_SERVER_ERROR));
    try {
      deliveryService.getPOLineInfoFromGDM(deliveryNumber, poNbr, 1, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      AssertJUnit.assertEquals(
          ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      AssertJUnit.assertEquals(
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE, e.getErrorResponse().getErrorMessage());
      reset(restUtils);
    }
  }

  @Test
  public void testReOpenDeliveryForSuccess() throws ReceivingException {
    when(restUtils.put(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("", HttpStatus.OK));

    deliveryService.reOpenDelivery(16017039630L, MockHttpHeaders.getHeaders());
    verify(restUtils, times(1)).put(anyString(), any(), any(), any());
  }

  @Test
  public void testReOpenDeliveryForNotFoundException() throws ReceivingException {
    try {
      doReturn(
              new ResponseEntity<String>(
                  "{\"errorCode\":\"DATA_NOT_FOUND\",\"description\":[\"Delivery with deliveryNumber 12364574|32987|US not found\"],\"dateTime\":\"2019-10-31T17:47:14.096Z\"}",
                  MockHttpHeaders.getHeaders(),
                  HttpStatus.NOT_FOUND))
          .when(restUtils)
          .put(anyString(), any(), any(), any());

      deliveryService.reOpenDelivery(16017039630L, MockHttpHeaders.getHeaders());
      verify(restUtils, times(1)).put(anyString(), any(), any(), any());
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.UNABLE_TO_FIND_DELIVERY_TO_REOPEN);
      reset(restUtils);
    }
  }

  @Test
  public void testReOpenDeliveryForNotAbleToReopenException() throws ReceivingException {
    try {
      doReturn(
              new ResponseEntity<String>(
                  "{\"errorCode\":\"DELIVERY_STATE_DOES_NOT_ALLOW_REOPEN\",\"description\":[\"DeliveryApplicationException.DELIVERY_STATE_DOES_NOT_ALLOW_REOPEN\"],\"dateTime\":\"2019-10-31T09:56:52.859Z\"}",
                  MockHttpHeaders.getHeaders(),
                  HttpStatus.BAD_REQUEST))
          .when(restUtils)
          .put(anyString(), any(), any(), any());

      deliveryService.reOpenDelivery(16017039630L, MockHttpHeaders.getHeaders());
      verify(restUtils, times(1)).put(anyString(), any(), any(), any());
    } catch (ReceivingException receivingException) {
      AssertJUnit.assertEquals(receivingException.getHttpStatus(), HttpStatus.BAD_REQUEST);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE);
      AssertJUnit.assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.UNABLE_TO_REOPEN_DELIVERY);
      reset(restUtils);
    }
  }

  @Test
  public void testReOpenDelivery_GdmServiceDownException() throws ReceivingException {
    when(restUtils.put(anyString(), any(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));
    try {
      deliveryService.reOpenDelivery(16017039630L, MockHttpHeaders.getHeaders());
      verify(restUtils, times(1)).put(anyString(), any(), any(), any());
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(INTERNAL_SERVER_ERROR, e.getHttpStatus());
      AssertJUnit.assertEquals(
          ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE, e.getErrorResponse().getErrorCode());
      AssertJUnit.assertEquals(
          ReceivingException.GDM_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
      reset(restUtils);
    }
  }

  private RestClientResponseException mockRestClientException(HttpStatus httpStatus) {
    return new RestClientResponseException(
        "Some error.", httpStatus.value(), "", null, "".getBytes(), StandardCharsets.UTF_8);
  }

  private ResourceAccessException mockResourceAccessException() {
    return new ResourceAccessException("Some IO Exception.", new IOReactorException("Errror"));
  }

  @Test
  public void testUpdateDeliveryStatusToWorkingForSuccess() {
    GdmDeliveryStatusUpdateEvent gdmDeliveryStatusUpdateEvent = new GdmDeliveryStatusUpdateEvent();
    gdmDeliveryStatusUpdateEvent.setDeliveryNumber(16017039630L);
    gdmDeliveryStatusUpdateEvent.setReceiverUserId("sysadmin");
    doReturn(url).when(appConfig).getGdmBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(gson.toJson(gdmDeliveryStatusUpdateEvent), HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToWorking(16017039630L, MockHttpHeaders.getHeaders());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToWorkingForNotFoundException() {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockRestClientException(HttpStatus.NOT_FOUND))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToWorking(16017039630L, MockHttpHeaders.getHeaders());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToWorkingEventException() {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockRestClientException(HttpStatus.BAD_REQUEST))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToWorking(16017039630L, MockHttpHeaders.getHeaders());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToWorking_GdmServiceDownException() {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockRestClientException(HttpStatus.SERVICE_UNAVAILABLE))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToWorking(16017039630L, MockHttpHeaders.getHeaders());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUpdateDeliveryStatusToWorking_IOException() throws ReceivingInternalException {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockResourceAccessException())
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToWorking(16017039630L, MockHttpHeaders.getHeaders());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToDoorOpenForSuccess() {
    GdmDeliveryStatusUpdateEvent gdmDeliveryStatusUpdateEvent = new GdmDeliveryStatusUpdateEvent();
    gdmDeliveryStatusUpdateEvent.setDeliveryNumber(16017039630L);
    gdmDeliveryStatusUpdateEvent.setReceiverUserId("sysadmin");
    doReturn(url).when(appConfig).getGdmBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(gson.toJson(gdmDeliveryStatusUpdateEvent), HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToOpen(16017039630L, MockHttpHeaders.getHttpHeadersMap());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToDoorOpenForNotFoundException() {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockRestClientException(HttpStatus.NOT_FOUND))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToOpen(16017039630L, MockHttpHeaders.getHttpHeadersMap());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToDoorOpenEventException() {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockRestClientException(HttpStatus.SERVICE_UNAVAILABLE))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToOpen(16017039630L, MockHttpHeaders.getHttpHeadersMap());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testUpdateDeliveryStatusToDoorOpen_GdmServiceDownException() {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockRestClientException(HttpStatus.BAD_REQUEST))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToOpen(16017039630L, MockHttpHeaders.getHttpHeadersMap());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testupdateDeliveryStatusToOpen_IOException() throws ReceivingInternalException {
    doReturn(url).when(appConfig).getGdmBaseUrl();
    doThrow(mockResourceAccessException())
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryService.updateDeliveryStatusToOpen(16017039630L, MockHttpHeaders.getHttpHeadersMap());
    verify(restConnector, times(1)).exchange(any(), any(), any(), eq(String.class));
    reset(restConnector);
  }

  @Test
  public void testGetGDMData() {
    try {
      deliveryService.getGDMData(new DeliveryUpdateMessage());
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(HttpStatus.NOT_IMPLEMENTED, e.getHttpStatus());
      AssertJUnit.assertEquals(
          ReceivingException.NOT_IMPLEMENTED_EXCEPTION, e.getErrorResponse().getErrorMessage());
      reset(restUtils);
    }
  }

  @Test
  public void testGetDeliveryHeaderDetailsEmpty() {
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("[]", HttpStatus.SERVICE_UNAVAILABLE));
    try {
      deliveryService.getDeliveryHeaderDetails(
          new Date(), new Date(), Collections.singletonList(2L), MockHttpHeaders.getHeaders());
      verify(simpleRestConnector, times(1))
          .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND);
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryHeaderDetailsNotFound() {
    try {
      doThrow(
              new RestClientResponseException(
                  "Some error.",
                  INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  StandardCharsets.UTF_8))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
      deliveryService.getDeliveryHeaderDetails(
          new Date(), new Date(), Collections.singletonList(2L), MockHttpHeaders.getHeaders());
      Assert.fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND);
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryHeaderDetails() throws ReceivingException {
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gsonForInstantAdapter.toJson(
                    MockGdmDeliveryHeaderDetails.getGdmDeliveryHeaderDetailsResponse()),
                HttpStatus.OK));
    List<GdmDeliveryHeaderDetailsResponse> actualResponse =
        deliveryService.getDeliveryHeaderDetails(
            new Date(), new Date(), Collections.singletonList(2L), MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    AssertJUnit.assertEquals(
        actualResponse, MockGdmDeliveryHeaderDetails.getGdmDeliveryHeaderDetailsResponse());
  }

  @Test
  public void testGetDeliveryHeaderDetails_ExceptionScenario_GDM_DOWN() {
    try {
      doThrow(new ResourceAccessException("IO Error."))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
      deliveryService.getDeliveryHeaderDetails(
          new Date(), new Date(), Collections.singletonList(2L), MockHttpHeaders.getHeaders());
      Assert.fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.GDM_SERVICE_DOWN);
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testRecordOSDRReasonCodes() throws Exception {

    RecordOSDRResponse mockResponse = new RecordOSDRResponse();

    doReturn(mockResponse)
        .when(recordOSDRResponseBuilder)
        .build(anyLong(), anyString(), any(Integer.class), any(), any());

    RecordOSDRRequest recordOSDRRequest = new RecordOSDRRequest();
    deliveryService.recordOSDR(123l, "123456", 1, recordOSDRRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testFindDeliveryDocumentforPoCon_EmptyResponseScenario() {
    try {
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.getPOInfoFromDelivery(1l, "9888888843", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFindDeliveryDocumentForPoCon_ExceptionScenario_GDM_DOWN()
      throws ReceivingException {
    try {
      doThrow(new ResourceAccessException("IO Error."))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.getPOInfoFromDelivery(1l, "56734837", MockHttpHeaders.getHeaders());
      Assert.fail();
    } catch (GDMServiceUnavailableException e) {
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.GDM_SERVICE_DOWN);
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFindDeliveryDocumentForPoCon_ExceptionScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      doThrow(
              new RestClientResponseException(
                  "Some error.",
                  INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  StandardCharsets.UTF_8))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryService.getPOInfoFromDelivery(1l, "56734837", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void test_getDeliveryWithOSDRByDeliveryNumber() throws ReceivingException {

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    doReturn(mockDeliveryWithOSDRResponse)
        .when(deliveryWithOSDRResponseBuilder)
        .build(anyLong(), anyMap(), any(Boolean.class), any());

    DeliveryWithOSDRResponse response =
        deliveryService.getDeliveryWithOSDRByDeliveryNumber(
            12345l, Collections.emptyMap(), true, null);
    assertNotNull(response);

    verify(deliveryWithOSDRResponseBuilder, times(1))
        .build(anyLong(), anyMap(), any(Boolean.class), any());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Error while calling GDM")
  public void testUpdateVendorUpcGdmDown() {
    doThrow(new ResourceAccessException("IO Error."))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUPC(
        "87654321", 567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    fail("Service down exception is supposed to be thrown");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid request for upc update, delivery = 87654321, item = 567898765")
  public void testUpdateVendorUpcBadRequest() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUPC(
        "87654321", 567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    fail("Bad request exception is supposed to be thrown");
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Delivery 87654321 not found")
  public void testUpdateVendorUpcNotFound() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.NOT_FOUND.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUPC(
        "87654321", 567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    fail("Not Found exception is supposed to be thrown");
  }

  @Test
  public void testUpdateVendorUpcSuccessful() {
    doReturn(new ResponseEntity<String>("", HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUPC(
        "87654321", 567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testUpdateVendorUpcInGDMItemV3Successful() {
    doReturn(new ResponseEntity<String>("", HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUpcItemV3(
        567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid request for VendorUPC update for item number = 567898765")
  public void testUpdateVendorUpcInGDMItemV3BadRequest() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUpcItemV3(
        567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    fail("Bad request exception is supposed to be thrown");
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Item 567898765 not found")
  public void testUpdateVendorUpcInGDMItemV3NotFound() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.NOT_FOUND.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUpcItemV3(
        567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    fail("Not Found exception is supposed to be thrown");
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Error while calling GDM")
  public void testUpdateVendorUpcInGDMItemV3GdmDown() {
    doThrow(new ResourceAccessException("IO Error."))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    deliveryService.updateVendorUpcItemV3(
        567898765L, "20000943037194", MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    fail("Service down exception is supposed to be thrown");
  }

  @Test
  public void testConfirmPOsWithAllSuccessTOConfirm() throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(5))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(5))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(5))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(5)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(5))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOs_GDM_nonPO_FinalizeException_Should_Rollback_Receiving_Finalize()
      throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());

    // throw gdmException
    ConfirmPurchaseOrderError gdmError = getErrorValue(ConfirmPurchaseOrderErrorCode.GDM_ERROR);
    final ReceivingException gdmException =
        new ReceivingException(
            gdmError.getErrorMessage(), INTERNAL_SERVER_ERROR, gdmError.getErrorCode());
    doThrow(gdmException)
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), eq(po1), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    // 1 gdm exception
    final List<ConfirmPurchaseOrdersError> errors = confirmPOsResponse.getErrors();
    assertEquals(errors.size(), 1);
    final ConfirmPurchaseOrdersError gdmErr = errors.get(0);
    assertEquals(gdmErr.getErrorCode(), ConfirmPurchaseOrderError.GDM_ERROR.getErrorCode());
    assertEquals(gdmErr.getPurchaseReferenceNumber(), po1);
    assertEquals(gdmErr.getErrorMessage(), ConfirmPurchaseOrderError.GDM_ERROR.getErrorMessage());

    // 1 gdm exception should not call DcFin
    verify(receiptService, times(4))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(5))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(5))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(4)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(4))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOs_gdm_AlreadyFinalizedError_continueFlow_to_Finalize_ReceivingDb()
      throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    // execute
    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    // 1 gdm exception
    final List<ConfirmPurchaseOrdersError> errors = confirmPOsResponse.getErrors();
    assertEquals(errors.size(), 0);

    verify(receiptService, times(5))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(5))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(5))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(5)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(5))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    // 1gdm exception should NOT make additional rcv call to rollback finalization
    verify(confirmPoResponseBuilder, times(0))
        .updateReceiptsWithFinalizedDetails(anyList(), eq(null), eq(null));
  }

  @Test
  public void testConfirmPOs_ObjectOptimisticLockingFailureException() throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), eq(po1), any(Map.class), any(FinalizePORequestBody.class));
    doThrow(new ObjectOptimisticLockingFailureException("", null))
        .doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    // 1  exception
    final List<ConfirmPurchaseOrdersError> errors = confirmPOsResponse.getErrors();
    assertEquals(errors.size(), 1);
    final ConfirmPurchaseOrdersError gdmErr = errors.get(0);
    assertEquals(gdmErr.getErrorCode(), "unableToConfirm");
    assertEquals(gdmErr.getPurchaseReferenceNumber(), po1);
    assertEquals(gdmErr.getErrorMessage(), PO_VERSION_MISMATCH);

    verify(receiptService, times(5))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(5))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(5))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(4)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(5))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  /**
   * findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber
   * returns two master records.
   *
   * <p>org.springframework.dao.IncorrectResultSizeDataAccessException: query did not return a
   * unique result: 2; nested exception is javax.persistence.NonUniqueResultException: query did not
   * return a unique result: 2
   *
   * @throws Exception
   */
  @Test
  public void testConfirmPOs_Unknown_Exception() throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    final IncorrectResultSizeDataAccessException incorrectResultSizeDataAccessException =
        new IncorrectResultSizeDataAccessException(1, 2);
    doThrow(incorrectResultSizeDataAccessException)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), eq(po1), any(Map.class), any(FinalizePORequestBody.class));

    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    // Exception
    final List<ConfirmPurchaseOrdersError> errors = confirmPOsResponse.getErrors();
    assertEquals(errors.size(), 5);
    final ConfirmPurchaseOrdersError defErr = errors.get(0);
    assertEquals(defErr.getPurchaseReferenceNumber(), po1);
    assertEquals(defErr.getErrorCode(), ConfirmPurchaseOrderError.DEFAULT_ERROR.getErrorCode());
    assertEquals(
        defErr.getErrorMessage(), ConfirmPurchaseOrderError.DEFAULT_ERROR.getErrorMessage());

    verify(receiptService, times(5))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(5))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(5))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(0)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(0))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOOsWithAllSuccessTOCConfirm_poQuantityMisMatch() throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    // poQuantityMisMatch exception
    final ArrayList<String> poQuantityMisMatch = new ArrayList<>();
    poQuantityMisMatch.add(po1);
    doReturn(poQuantityMisMatch).when(deliveryService).getPoQuantityMisMatch(any());
    final ReceivingException isPoQuantityMisMatchException =
        new ReceivingException(
            "PO " + po1 + "'s Receipts Quantity not matching with Container",
            INTERNAL_SERVER_ERROR);
    doThrow(isPoQuantityMisMatchException)
        .when(deliveryService)
        .checkQuantityMisMatch(any(), eq(po1));
    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);
    // should be 1 that mismatched
    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    verify(receiptService, times(4))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    // po1 mismatch to ignore processing it
    verify(finalizePORequestBodyBuilder, times(4))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(4))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(4)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(4))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOsWithAllFailedTOConfirm() throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    ReceivingException receivingException =
        new ReceivingException("Something went wrong", INTERNAL_SERVER_ERROR, "errorCode");

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doThrow(receivingException)
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 5);
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(0));
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "unableToConfirm");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "Unable to confirm this PO. Please contact your supervisor or support");

    assertEquals(
        confirmPOsResponse.getErrors().get(1).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(1));
    assertEquals(
        confirmPOsResponse.getErrors().get(2).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(2));
    assertEquals(
        confirmPOsResponse.getErrors().get(3).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(3));

    assertEquals(
        confirmPOsResponse.getErrors().get(4).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(4));
    assertEquals(confirmPOsResponse.getErrors().get(4).getErrorCode(), "unableToConfirm");
    assertEquals(
        confirmPOsResponse.getErrors().get(4).getErrorMessage(),
        "Unable to confirm this PO. Please contact your supervisor or support");
  }

  @Test
  public void testConfirmPOsWithFewFailedTOConfirm() throws Exception {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    ReceivingException receivingException =
        new ReceivingException("Something went wrong", INTERNAL_SERVER_ERROR, "errorCode");

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(finalizePORequestBodyBuilder.buildFrom(anyLong(), anyString(), any(Map.class), any()))
        .thenReturn(new FinalizePORequestBody())
        .thenThrow(receivingException)
        .thenReturn(new FinalizePORequestBody())
        .thenThrow(receivingException)
        .thenReturn(new FinalizePORequestBody());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);
    assertEquals(confirmPOsResponse.getErrors().size(), 2);

    assertEquals(
        confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(1));
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "unableToConfirm");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "Unable to confirm this PO. Please contact your supervisor or support");

    assertEquals(
        confirmPOsResponse.getErrors().get(1).getPurchaseReferenceNumber(),
        mockConfirmPOsRequest.getPurchaseReferenceNumbers().get(3));
    assertEquals(confirmPOsResponse.getErrors().get(1).getErrorCode(), "unableToConfirm");
    assertEquals(
        confirmPOsResponse.getErrors().get(1).getErrorMessage(),
        "Unable to confirm this PO. Please contact your supervisor or support");
  }

  private List<Instruction> getMockInstructions() {

    List<Instruction> instructionResult = new ArrayList<>();

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setPurchaseReferenceLegacyType("28");

    Instruction instruction = new Instruction();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    instructionResult.add(instruction);

    return instructionResult;
  }

  private void saveReceiptOnTable() {
    List<Receipt> receipts = new ArrayList<>();

    Receipt nationalPOReceipt = new Receipt();
    nationalPOReceipt.setEachQty(3);
    nationalPOReceipt.setDeliveryNumber(12345678L);
    nationalPOReceipt.setPurchaseReferenceNumber("81345678");
    nationalPOReceipt.setPurchaseReferenceLineNumber(2);
    receipts.add(nationalPOReceipt);

    Receipt poconPOReceipt = new Receipt();
    poconPOReceipt.setEachQty(5);
    poconPOReceipt.setDeliveryNumber(12345678L);
    poconPOReceipt.setPurchaseReferenceNumber("81765438");
    receipts.add(poconPOReceipt);

    Receipt dsdcPOReceipt = new Receipt();
    dsdcPOReceipt.setEachQty(8);
    dsdcPOReceipt.setPalletQty(3);
    dsdcPOReceipt.setDeliveryNumber(12345678L);
    dsdcPOReceipt.setPurchaseReferenceNumber("81762348");
    receipts.add(dsdcPOReceipt);

    receiptRepositoryAutowired.saveAll(receipts);
  }

  @Test
  public void testGDMReceiptFormatAfterDeliveryComplete() throws ReceivingException, IOException {
    saveReceiptOnTable();
    ReflectionTestUtils.setField(
        receiptServiceInjectMocked, "receiptCustomRepository", receiptCustomRepositoryInjectMocked);
    ReflectionTestUtils.setField(
        receiptCustomRepositoryInjectMocked, "entityManager", entityManager);
    ReflectionTestUtils.setField(defaultDeliveryMessagePublisher, "jmsPublisher", jmsPublisher);
    ReflectionTestUtils.setField(deliveryService, "receiptService", receiptServiceInjectMocked);
    ReflectionTestUtils.setField(
        deliveryService, "deliveryStatusPublisher", deliveryStatusPublisherInjectMocked);

    ReflectionTestUtils.setField(
        completeDeliveryProcessor, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(completeDeliveryProcessor, "receiptRepository", receiptRepository);
    ReflectionTestUtils.setField(
        completeDeliveryProcessor, "instructionRepository", instructionRepository);
    ReflectionTestUtils.setField(completeDeliveryProcessor, "receiptService", receiptService);
    ReflectionTestUtils.setField(
        completeDeliveryProcessor, "deliveryStatusPublisher", deliveryStatusPublisher);

    doReturn(completeDeliveryProcessor)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(tenantSpecificConfigReader.isPoConfirmationFlagEnabled(any())).thenReturn(false);
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(0L);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_STATUS_PUBLISHER), any()))
        .thenReturn(defaultDeliveryMessagePublisher);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), any()))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(null);
    try {
      deliveryService.completeDelivery(12345678L, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingException re) {
      fail();
    } finally {
      ReflectionTestUtils.setField(
          deliveryService, "deliveryStatusPublisher", deliveryStatusPublisher);
    }
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Tenant-32987 not supported")
  public void testDefaultOSDRService() throws ReceivingException {
    when(receiptService.getReceiptSummary(12333333L, null, null))
        .thenReturn(MockReceipt.getReceiptsForOSDRDetails());
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
        .thenReturn(defaultOsdrService);
    deliveryService.getOsdrInformation(12333333L, null, null, "sysadmin", "EA", null);
  }

  @Test()
  public void testOSDRService_Include_openInstruction() throws ReceivingException {
    doReturn(MockReceipt.getReceiptsForOSDRDetails())
        .when(receiptRepository)
        .findByDeliveryNumber(any());
    when(receiptService.getReceiptSummary(12333333L, null, null))
        .thenReturn(MockReceipt.getReceiptsForOSDRDetails());
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
        .thenReturn(osdrService);
    final List<Instruction> mockInstructions = getMockInstructions();
    final String purchaseReferenceNumber = "7227446500";
    mockInstructions.get(0).setPurchaseReferenceNumber(purchaseReferenceNumber);
    mockInstructions.get(0).setPurchaseReferenceLineNumber(1);
    doReturn(mockInstructions)
        .when(instructionRepository)
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(any());
    doReturn(new OsdrSummary()).when(osdrService).getOsdrDetails(any(), any(), any(), any());
    final String expectedOpenInstructions =
        "openInstructions=[{purchaseReferenceNumber=7227446500, lineNumber=1}]";
    final OsdrSummary osdrInformation =
        deliveryService.getOsdrInformation(
            12333333L, null, null, "sysadmin", "EA", RCV_GDM_INCLUDE_OPEN_INSTRUCTIONS);
    Assert.assertNotNull(osdrInformation);
    assertTrue(osdrInformation.toString().contains(expectedOpenInstructions));
  }

  @Test()
  public void testOSDRService_NoOSDR_Include_openInstruction() throws ReceivingException {
    final ReceivingDataNotFoundException exception =
        new ReceivingDataNotFoundException(
            RECEIPTS_NOT_FOUND, String.format(RECEIPTS_NOT_FOUND_ERROR_MSG, deliveryNumber));
    final Long deliverNumber = Long.valueOf(12333333);
    when(receiptService.getReceiptSummary(deliverNumber, null, null)).thenThrow(exception);

    final List<Instruction> mockInstructions = getMockInstructions();
    final String purchaseReferenceNumber = "7227446500";
    mockInstructions.get(0).setPurchaseReferenceNumber(purchaseReferenceNumber);
    mockInstructions.get(0).setPurchaseReferenceLineNumber(1);
    doReturn(mockInstructions)
        .when(instructionRepository)
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(any());
    final String expectedOpenInstructions =
        "openInstructions=[{purchaseReferenceNumber=7227446500, lineNumber=1}]";
    final OsdrSummary osdrInformation =
        deliveryService.getOsdrInformation(
            deliverNumber, null, null, "sysadmin", "EA", RCV_GDM_INCLUDE_OPEN_INSTRUCTIONS);
    assertNotNull(osdrInformation);
    assertEquals(osdrInformation.getEventType(), OSDR_EVENT_TYPE_VALUE);
    assertEquals(osdrInformation.getDeliveryNumber(), deliverNumber);
    assertNotNull(osdrInformation.getTs());
    assertTrue(osdrInformation.toString().contains(expectedOpenInstructions));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "No record found for this delivery number 12333333 in receipt")
  public void testOSDRService_NoOSDR_NoInclude_throwException() {
    final ReceivingDataNotFoundException exception =
        new ReceivingDataNotFoundException(
            RECEIPTS_NOT_FOUND, String.format(RECEIPTS_NOT_FOUND_ERROR_MSG, deliveryNumber));
    when(receiptService.getReceiptSummary(12333333L, null, null)).thenThrow(exception);
    deliveryService.getOsdrInformation(12333333L, null, null, "sysadmin", "EA", null);
  }

  @Test
  public void test_setVendorComplianceDateOnGDM_success() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32835);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());

    doReturn("gdm").when(appConfig).getGdmBaseUrl();
    doReturn(ResponseEntity.status(HttpStatus.OK).headers((HttpHeaders) null).body(null))
        .when(restUtils)
        .put(any(), any(), any(), any());

    VendorComplianceRequestDates vendorComplianceRequestDates = new VendorComplianceRequestDates();
    vendorComplianceRequestDates.setHazmatVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    try {
      deliveryService.setVendorComplianceDateOnGDM("99999999", vendorComplianceRequestDates);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "At least one verified date is mandatory")
  public void test_setVendorComplianceDateOnGDM_badRequest() throws ReceivingException {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32835);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());

    doReturn("gdm").when(appConfig).getGdmBaseUrl();
    doReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).headers((HttpHeaders) null).body(null))
        .when(restUtils)
        .put(any(), any(), any(), any());

    deliveryService.setVendorComplianceDateOnGDM("99999999", new VendorComplianceRequestDates());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "This item number is not valid. Please try again or report this to your supervisor if it continues.")
  public void test_setVendorComplianceDateOnGDM_itemNotFound() throws ReceivingException {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32835);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());

    doReturn("gdm").when(appConfig).getGdmBaseUrl();
    doReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).headers((HttpHeaders) null).body(null))
        .when(restUtils)
        .put(any(), any(), any(), any());

    VendorComplianceRequestDates vendorComplianceRequestDates = new VendorComplianceRequestDates();
    vendorComplianceRequestDates.setHazmatVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    deliveryService.setVendorComplianceDateOnGDM("99999999", vendorComplianceRequestDates);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.")
  public void test_setVendorComplianceDateOnGDM_gdmDown() throws ReceivingException {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32835);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());

    doReturn("gdm").when(appConfig).getGdmBaseUrl();
    doReturn(ResponseEntity.status(INTERNAL_SERVER_ERROR).headers((HttpHeaders) null).body(null))
        .when(restUtils)
        .put(any(), any(), any(), any());

    VendorComplianceRequestDates vendorComplianceRequestDates = new VendorComplianceRequestDates();
    vendorComplianceRequestDates.setHazmatVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    deliveryService.setVendorComplianceDateOnGDM("99999999", vendorComplianceRequestDates);
  }

  @Test
  public void test_searchShipment_rest_invocation() throws Exception {

    File resource = new ClassPathResource("gdm_searchShipment_response.json").getFile();
    String response = new String(Files.readAllBytes(resource.toPath()));

    doReturn(new ResponseEntity<>(response, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Shipment shipment =
        deliveryService.searchShipment("9898", "123456789", MockRxHttpHeaders.getHeaders());
    Assert.assertNotNull(shipment);
    assertEquals(shipment.getDocumentId(), "35595947_20191106_719468_VENDOR_US");
  }

  @Test
  public void test_searchShipment_rest_invocation_error() throws Exception {

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      deliveryService.searchShipment("9898", "123456789", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_SEARCH_SHIPMENT_FAILED);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_SEARCH_SHIPMENT_FAILED);
    }
  }

  @Test
  public void test_searchShipment_rest_server_error() throws Exception {

    doThrow(new ResourceAccessException("Service unavailable"))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      deliveryService.searchShipment("9898", "123456789", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_NOT_ACCESSIBLE);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }

  @Test
  public void test_searchShipment_204_no_content() throws Exception {

    doReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Shipment shipment =
          deliveryService.searchShipment("9898", "123456789", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_DSDC_OR_SSTK_SSCC_NOT_FOUND);
      assertEquals(
          e.getMessage(), String.format(ReceivingConstants.GDM_SHIPMENT_NOT_FOUND, "123456789"));
    }
  }

  @Test
  public void testGetSsccScanDetails() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
  }

  @Test
  public void testGetSsccScanDetails_V1() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(ReceivingConstants.VERSION_V1).when(appConfig).getShipmentSearchVersion();

    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetSsccScanDetails_GdmNotFound() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));

    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    String gdmPackResponse = new String(Files.readAllBytes(gdmPackResponseFile.toPath()));
    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetSsccScanDetails_GdmUnavailable() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    String gdmPackResponse = new String(Files.readAllBytes(gdmPackResponseFile.toPath()));
    doThrow(new ResourceAccessException("Service unavailable"))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
  }

  @Test
  public void testAutoCompleteDelivery() throws ReceivingException {

    List<LifeCycleInformation> lifeCycleInformations = new ArrayList<>();
    LifeCycleInformation lifeCycleInformation = new LifeCycleInformation();
    lifeCycleInformation.setUserId("sysadmin");
    lifeCycleInformation.setType("DOOR_OPEN");
    lifeCycleInformation.setTime("2021-01-22T05:10:46.361Z");
    lifeCycleInformations.add(lifeCycleInformation);
    List<String> statusReasonCode = Arrays.asList("DOOR_OPEN");

    List<Delivery> deliveries1 = new ArrayList<>();
    Delivery delivery =
        Delivery.builder()
            .deliveryNumber(1234567L)
            .lifeCycleInformation(lifeCycleInformations)
            .statusReasonCode(statusReasonCode)
            .build();
    deliveries1.add(delivery);
    DeliveryList deliveryList = DeliveryList.builder().data(deliveries1).build();

    InstructionDetails instructionDetails1 =
        InstructionDetails.builder()
            .deliveryNumber(1234567L)
            .lastChangeUserId("sysadmin2")
            .receivedQuantity(0)
            .createUserId("sysadmin")
            .build();

    InstructionDetails instructionDetails2 =
        InstructionDetails.builder()
            .deliveryNumber(1234567L)
            .lastChangeUserId(null)
            .receivedQuantity(0)
            .createUserId("sysadmin")
            .build();

    List<InstructionDetails> instructionListWithOpenInstructions = new ArrayList<>();
    instructionListWithOpenInstructions.add(instructionDetails1);
    instructionListWithOpenInstructions.add(instructionDetails2);

    List<InstructionDetails> instructionListWithPendingInstructions = new ArrayList<>();

    instructionListWithPendingInstructions.add(
        InstructionDetails.builder()
            .deliveryNumber(1234568L)
            .createUserId("sysadmin")
            .receivedQuantity(1)
            .build());

    ReflectionTestUtils.setField(completeDeliveryProcessor, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(completeDeliveryProcessor, "gson", gson);
    ReflectionTestUtils.setField(
        completeDeliveryProcessor, "instructionHelperService", instructionHelperService);

    doReturn(gson.toJsonTree(Integer.valueOf(48)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR));

    doReturn(gson.toJsonTree(Integer.valueOf(4)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR));

    Receipt receiptWithIdealTime = MockReceipt.getReceipt();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    doReturn(null, MockReceipt.getOSDRMasterReceipt(), receiptWithIdealTime)
        .when(receiptRepository)
        .findFirstByDeliveryNumberOrderByCreateTsDesc(any());

    doReturn(completeDeliveryProcessor)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(any(), any(), any());

    doReturn((new ResponseEntity<>(gson.toJson(deliveryList), HttpStatus.OK)))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    doReturn(instructionListWithOpenInstructions)
        .when(instructionRepository)
        .getUncompletedInstructionDetailsByDeliveryNumber(1234567L, 32835);

    when(instructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(null);

    try {
      completeDeliveryProcessor.autoCompleteDeliveries(32835);
    } catch (Exception ex) {
      fail();
    }
    try {
      completeDeliveryProcessor.autoCompleteDeliveries(32835);
    } catch (Exception ex) {
      fail();
    }
    try {
      completeDeliveryProcessor.autoCompleteDeliveries(32835);
    } catch (Exception ex) {
      fail();
    }
  }

  @Test
  public void testDeliveryFetchByStatus_AllCases() throws ReceivingException {
    doReturn(
            (new ResponseEntity<>(HttpStatus.NOT_FOUND)),
            (new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE)),
            (new ResponseEntity<>(HttpStatus.BAD_REQUEST)),
            (new ResponseEntity<>(HttpStatus.OK)))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    try {
      deliveryService.fetchDeliveriesByStatus("32835", 0);
      fail("Should throw exception");
    } catch (ReceivingException ex) {
      exception.expectMessage(ReceivingException.GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR);
    }

    try {
      deliveryService.fetchDeliveriesByStatus("32835", 0);
      fail("Should throw exception");
    } catch (ReceivingException ex) {
      exception.expectMessage(ReceivingException.GDM_SERVICE_DOWN);
    }

    try {
      deliveryService.fetchDeliveriesByStatus("32835", 0);
      fail("Should throw exception");
    } catch (ReceivingException ex) {
      exception.expectMessage(ReceivingException.BAD_REQUEST);
    }

    try {
      deliveryService.fetchDeliveriesByStatus("32835", 0);
      fail("Should throw exception");
    } catch (ReceivingException ex) {
      exception.expectMessage(ReceivingException.GDM_SERVICE_DOWN);
    }
  }

  @Test
  public void testGlobalPackSearch() throws IOException {
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    deliveryService.globalPackSearch("00003011610010351794", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGlobalPackSearch_EmptyResp() throws IOException {
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    deliveryService.globalPackSearch("00003011610010351794", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGlobalPackSearch_GdmNotFound() throws Exception {
    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    deliveryService.globalPackSearch("00003011610010351794", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGlobalPackSearch_GdmUnavailable() throws Exception {
    doThrow(new ResourceAccessException("Service unavailable"))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    deliveryService.globalPackSearch("00003011610010351794", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testConfirmPOs_openInstructions_singleUser() throws Exception {
    List<Instruction> instructionList = new ArrayList<>();
    for (int i = 1; i < 3; i++) {
      Instruction instruction = new Instruction();
      instruction.setFacilityNum(32612);
      instruction.setCreateUserId("witronTest");
      instruction.setInstructionCode("Build Container");
      instruction.setDeliveryNumber(Long.valueOf(deliveryNumber));
      instruction.setPurchaseReferenceNumber(poNbr);
      instruction.setPurchaseReferenceLineNumber(i);
      instructionList.add(instruction);
    }

    doReturn(instructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong(), anyString());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "openInstructions");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "witronTest has open instruction(s) against this PO, finish or cancel receiving");
  }

  @Test
  public void testConfirmPOs_openInstructions_transferUser() throws Exception {
    List<Instruction> instructionList = new ArrayList<>();
    Instruction instruction = new Instruction();
    instruction.setFacilityNum(32612);
    instruction.setCreateUserId("witronTest1");
    instruction.setInstructionCode("Build Container");
    instruction.setDeliveryNumber(Long.valueOf(deliveryNumber));
    instruction.setPurchaseReferenceNumber(poNbr);
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setLastChangeUserId("witronTest2");
    instructionList.add(instruction);

    doReturn(instructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong(), anyString());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "openInstructions");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "witronTest2 has open instruction(s) against this PO, finish or cancel receiving");
  }

  @Test
  public void testConfirmPOs_openInstructions_multiUser() throws Exception {
    List<Instruction> instructionList = new ArrayList<>();
    for (int i = 1; i < 6; i++) {
      Instruction instruction = new Instruction();
      instruction.setFacilityNum(32612);
      instruction.setCreateUserId("sysadmin" + i);
      instruction.setInstructionCode("Build Container");
      instruction.setDeliveryNumber(Long.valueOf(deliveryNumber));
      instruction.setPurchaseReferenceNumber(poNbr);
      instruction.setPurchaseReferenceLineNumber(i);
      instructionList.add(instruction);
    }

    doReturn(instructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong(), anyString());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "openInstructions");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "sysadmin5 + 4 other(s) have open instruction(s) against this PO, finish or cancel receiving");
  }

  @Test
  public void testConfirmPOs_problemAndDamageDataOutOfSync() throws Exception {
    doThrow(
            new ReceivingException(
                "Problem or damage data has changed, refresh the screen",
                HttpStatus.BAD_REQUEST,
                "problemAndDamageDataOutOfSync"))
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorCode(), "problemAndDamageDataOutOfSync");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "Problem or damage data has changed, refresh the screen");
  }

  @Test
  public void testConfirmPOs_unableToReachDCFin() throws Exception {
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(), any());
    doThrow(
            new ReceivingException(
                "Couldn’t connect to DC Finance, retry or contact support",
                HttpStatus.BAD_REQUEST,
                "unableToReachDCFin"))
        .when(confirmPoResponseBuilder)
        .closePO(any(), any(Map.class), anyBoolean());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "unableToReachDCFin");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "Couldn’t connect to DC Finance, retry or contact support");
  }

  @Test
  public void testConfirmPOs_unableToReachGDM() throws Exception {
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doThrow(
            new ReceivingException(
                "Couldn’t connect to GDM, retry or contact support",
                HttpStatus.BAD_REQUEST,
                "unableToReachGDM"))
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(), any(FinalizePORequestBody.class));

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "unableToReachGDM");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "Couldn’t connect to GDM, retry or contact support");
  }

  @Test
  public void testConfirmPOs_unableToConfirm() throws Exception {
    doThrow(new ReceivingException("Something went wrong", INTERNAL_SERVER_ERROR, "errorCode"))
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(), any());

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockPoCloseRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    assertEquals(confirmPOsResponse.getErrors().get(0).getPurchaseReferenceNumber(), poNbr);
    assertEquals(confirmPOsResponse.getErrors().get(0).getErrorCode(), "unableToConfirm");
    assertEquals(
        confirmPOsResponse.getErrors().get(0).getErrorMessage(),
        "Unable to confirm this PO. Please contact your supervisor or support");
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testPublishDeliveryStatus() throws Exception {
    deliveryService.publishDeliveryStatus(new DeliveryInfo(), MockHttpHeaders.getHeaders());
  }

  @Test
  public void test_isPoQuantityMisMatch() {
    String poNumber = "12345";
    ArrayList<String> poQuantityMisMatchList = new ArrayList<>();
    poQuantityMisMatchList.add(poNumber);
    try {
      deliveryService.checkQuantityMisMatch(poQuantityMisMatchList, poNumber);
      fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      assertEquals(e.getMessage(), "PO 12345's Receipts Quantity not matching with Container");
    }
  }

  @Test
  public void test_checkPoConfirmed_1_nulls() {
    try {
      deliveryService.checkPoConfirmed(null, null);
      deliveryService.checkPoConfirmed(null, "012345");
      deliveryService.checkPoConfirmed(1234L, null);
      deliveryService.checkPoConfirmed(1234L, "012345");
      // default CHECK_PO_CONFIRMED_ENABLED is false
      assertTrue(
          true,
          "should not throw any error for invalid data or no specific error for null delivery, po");
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void test_checkPoConfirmed_2_nullMasterList() {
    Long deliveryNumber = 1233456L;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(null)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    try {
      deliveryService.checkPoConfirmed(deliveryNumber, "012345");
      assertTrue(true, "should not throw any error for null masterReceipts List");
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void test_checkPoConfirmed_3_NotFinazizedMasterList() throws ReceivingException {
    Long deliveryNumber = 1233456L;
    final String purchaseReferenceNumber = "012345";
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<Receipt> mockReceiptList = new ArrayList<>();

    Receipt receiptNotFinalized1 = new Receipt();
    receiptNotFinalized1.setDeliveryNumber(deliveryNumber);
    receiptNotFinalized1.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receiptNotFinalized1.setPurchaseReferenceLineNumber(1);
    mockReceiptList.add(receiptNotFinalized1);

    Receipt receiptNotFinalized2 = new Receipt();
    receiptNotFinalized2.setDeliveryNumber(deliveryNumber);
    receiptNotFinalized2.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receiptNotFinalized2.setPurchaseReferenceLineNumber(1);
    mockReceiptList.add(receiptNotFinalized2);

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    deliveryService.checkPoConfirmed(deliveryNumber, purchaseReferenceNumber);
    assertTrue(true, "should not throw any error for null masterReceipts List");
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "PO is already confirmed")
  public void test_checkPoConfirmed_4_FinazizedMasterList() throws ReceivingException {
    Long deliveryNumber = 1233456L;
    final String purchaseReferenceNumber = "012345";
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<Receipt> receiptsWithAtLeastOneFinalizedReceipt = new ArrayList<>();

    Receipt receipt_1_NotFinalized = new Receipt();
    receipt_1_NotFinalized.setDeliveryNumber(deliveryNumber);
    receipt_1_NotFinalized.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receipt_1_NotFinalized.setPurchaseReferenceLineNumber(1);
    receiptsWithAtLeastOneFinalizedReceipt.add(receipt_1_NotFinalized);

    Receipt receipt_2_NotFinalized = new Receipt();
    receipt_2_NotFinalized.setDeliveryNumber(deliveryNumber);
    receipt_2_NotFinalized.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receipt_2_NotFinalized.setPurchaseReferenceLineNumber(2);
    receiptsWithAtLeastOneFinalizedReceipt.add(receipt_2_NotFinalized);

    Receipt receipt_3_Finalized = new Receipt();
    receipt_3_Finalized.setDeliveryNumber(deliveryNumber);
    receipt_3_Finalized.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receipt_3_Finalized.setPurchaseReferenceLineNumber(3);
    receipt_3_Finalized.setFinalizedUserId("user1");
    receipt_3_Finalized.setFinalizeTs(new Date());
    receiptsWithAtLeastOneFinalizedReceipt.add(receipt_3_Finalized);

    ReflectionTestUtils.setField(deliveryService, "receiptService", receiptService);

    doReturn(receiptsWithAtLeastOneFinalizedReceipt)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    try {
      deliveryService.checkPoConfirmed(deliveryNumber, purchaseReferenceNumber);
      fail();
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorCode(), "alreadyConfirmed");
      assertNull(err.getErrorHeader());
      assertEquals(err.getErrorMessage(), "PO is already confirmed");

      throw e;
    }
  }

  @Test
  public void test_isPoQuantityMisMatch_nulls() {
    try {
      deliveryService.checkQuantityMisMatch(null, null);
      assertTrue(true, "should not throw any error if no missmatch in quantity");
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void test_getPoQuantityMisMatch_null() {
    final ArrayList<String> poQuantityMisMatch = deliveryService.getPoQuantityMisMatch(1233456L);
    assertNotNull(poQuantityMisMatch);
    assertEquals(poQuantityMisMatch.size(), 0);
  }

  @Test
  public void test_getPoQuantityMisMatch_all_match() {
    Long deliveryNumber = 1233456L;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    when(containerItemCustomRepository.getContainerQuantity(any(Long.class)))
        .thenReturn(containerPoLineQuantityListMatching);
    final ArrayList<String> poQuantityMisMatch =
        deliveryService.getPoQuantityMisMatch(deliveryNumber);
    assertNotNull(poQuantityMisMatch);
    assertEquals(poQuantityMisMatch.size(), 0);
  }

  @Test
  public void test_getPoQuantityMisMatch_NO_match() {
    Long deliveryNumber = 1233456L;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    when(containerItemCustomRepository.getContainerQuantity(any(Long.class)))
        .thenReturn(containerPoLineQuantityListMatchingNot);
    final ArrayList<String> poQuantityMisMatch =
        deliveryService.getPoQuantityMisMatch(deliveryNumber);
    assertNotNull(poQuantityMisMatch);
    assertEquals(poQuantityMisMatch.size(), 2);
  }

  @Test
  public void test_getContainerSsccDetails_inline_partial_error() throws Exception {

    try {
      File gdmPackResponseFile =
          new ClassPathResource("GdmMappedResponseV2_inline_error.json").getFile();
      SsccScanResponse gdmPackResponse =
          gson.fromJson(
              new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
      doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.PARTIAL_CONTENT))
          .when(simpleRestConnector)
          .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
      doReturn(asnToDeliveryDocumentsCustomMapper)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));
      doThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.GDM_PARTIAL_SHIPMENT_DATA,
                  ReceivingConstants.GDM_PARTIAL_RESPONSE,
                  "PO not found in delivery : [8458708681]"))
          .when(asnToDeliveryDocumentsCustomMapper)
          .checkIfPartialContent(any(List.class));

      deliveryService.getContainerSsccDetails(
          "9898", "mockShipmentNumber", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_PARTIAL_SHIPMENT_DATA);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_PARTIAL_RESPONSE);
      assertEquals(e.getDescription(), ReceivingConstants.GDM_PARTIAL_RESPONSE);
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(asnToDeliveryDocumentsCustomMapper, times(1)).checkIfPartialContent(any(List.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_findDeliveryDocumentBySSCCWithShipmentLinking_exception()
      throws ReceivingException {

    doReturn(Optional.empty())
        .when(deliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));

    Shipment mockShipment = new Shipment();
    mockShipment.setShipmentNumber("MOCK_SHIPMENT_NUMBER");
    mockShipment.setDocumentId("MOCK_DOCUMENT_ID");
    doReturn(mockShipment)
        .when(deliveryService)
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));

    doReturn("MOCK_LINK_DELIVERY_RESPONSE")
        .when(deliveryService)
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    List<DeliveryDocument> deliveryDocumentsResponse =
        deliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            "9898", "123456789", MockRxHttpHeaders.getHeaders());

    assertTrue(CollectionUtils.isNotEmpty(deliveryDocumentsResponse));
    verify(deliveryService, times(2))
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    verify(deliveryService, times(1))
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));
    verify(deliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_findDeliveryDocumentBySSCCWithShipmentLinking_DSDC_Receiving_exception()
      throws ReceivingException {

    doReturn(Optional.empty())
        .when(deliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM, false);

    Shipment mockShipment = new Shipment();
    mockShipment.setShipmentNumber("MOCK_SHIPMENT_NUMBER");
    mockShipment.setDocumentId("MOCK_DOCUMENT_ID");
    doReturn(mockShipment)
        .when(deliveryService)
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));

    doReturn("MOCK_LINK_DELIVERY_RESPONSE")
        .when(deliveryService)
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    List<DeliveryDocument> deliveryDocumentsResponse =
        deliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            "9898", "123456789", MockRxHttpHeaders.getHeaders());

    assertTrue(CollectionUtils.isNotEmpty(deliveryDocumentsResponse));
    verify(deliveryService, times(2))
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    verify(deliveryService, times(1))
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));
    verify(deliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_getContainerSsccDetails_DSDC() throws ReceivingException {

    SsccScanResponse ssccScanResponse = new SsccScanResponse();
    List<Error> errors = new ArrayList<>();
    errors.add(
        new Error(
            ReceivingException.GDM_SSCC_SCAN_NOT_FOUND_ERROR_CODE,
            Arrays.asList(ReceivingConstants.GDM_SSCC_SCAN_NOT_FOUND_ERROR_MESSAGE)));
    ssccScanResponse.setErrors(errors);
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(Long.valueOf(deliveryNumber));
    List<PurchaseOrder> purchaseOrders = new ArrayList<>();
    delivery.setPurchaseOrders(purchaseOrders);
    List<Shipment> shipments = new ArrayList<>();
    Shipment shipment = new Shipment();
    shipments.add(shipment);
    List<Pack> packs = new ArrayList<>();
    Pack pack = new Pack();
    packs.add(pack);
    ssccScanResponse.setDelivery(delivery);
    ssccScanResponse.setShipments(shipments);
    ssccScanResponse.setPacks(packs);
    doReturn(ssccScanResponse)
        .when(deliveryService)
        .getSsccScanDetails(anyString(), anyString(), any(HttpHeaders.class));

    Optional<List<DeliveryDocument>> response =
        deliveryService.getContainerSsccDetails(
            "34235235", "223423457658912343213", MockRxHttpHeaders.getHeaders());
    assertNotNull(response);
    verify(deliveryService, times(1))
        .getSsccScanDetails(anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testGetSsccScanDetails_GDM_Error_Fallback() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.NO_CONTENT))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
  }

  @Test
  public void testGetSsccScanDetails_V1_SSCC_AvailableInGdm() throws Exception {
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.NO_CONTENT))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(ReceivingConstants.VERSION_V1).when(appConfig).getShipmentSearchVersion();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_ALL_VENDORS_ENABLED_IN_GDM,
            false))
        .thenReturn(true);
    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
    assertEquals(ssccScanResponse, null);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetSsccScanDetails_V1_SSCC_GdmReturnsNull() throws Exception {
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(ReceivingConstants.VERSION_V1).when(appConfig).getShipmentSearchVersion();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_ALL_VENDORS_ENABLED_IN_GDM,
            false))
        .thenReturn(true);
    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
  }

  @Test
  public void testGetSsccScanDetails_V1_AllVendorsNotEnabled_InGdm() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.NO_CONTENT))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(ReceivingConstants.VERSION_V1).when(appConfig).getShipmentSearchVersion();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_ALL_VENDORS_ENABLED_IN_GDM,
            false))
        .thenReturn(false);
    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
    assertNotNull(ssccScanResponse);
  }

  @Test
  public void testGetSsccScanDetails_V1_Gdm_ReturnsNull() throws Exception {
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(ReceivingConstants.VERSION_V1).when(appConfig).getShipmentSearchVersion();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_ALL_VENDORS_ENABLED_IN_GDM,
            false))
        .thenReturn(false);
    SsccScanResponse ssccScanResponse =
        deliveryService.getSsccScanDetails("9898", "123456789", new HttpHeaders());
    assertNotNull(ssccScanResponse);
  }

  @Test
  public void test_linkDeliveryWithShipment_rest_invocation() throws Exception {

    doReturn(new ResponseEntity<>("MOCK RESPONSE", HttpStatus.OK))
        .when(simpleRestConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));

    deliveryService.linkDeliveryWithShipment(
        "9898", "mockShipmentNumber", "mockShipmentDocumentId", MockRxHttpHeaders.getHeaders());

    verify(simpleRestConnector, times(1))
        .put(anyString(), anyString(), any(Map.class), any(Class.class));
  }

  @Test
  public void test_linkDeliveryWithShipment_invocation_NoData() throws Exception {

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(simpleRestConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));
    try {
      deliveryService.linkDeliveryWithShipment(
          "9898", "mockShipmentNumber", "mockShipmentDocumentId", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_SHIPMENT_DELIVERY_LINK_FAILURE);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_SHIPMENT_DELIVERY_LINK_FAILURE);
    }
    verify(simpleRestConnector, times(1))
        .put(anyString(), anyString(), any(Map.class), any(Class.class));
  }

  @Test
  public void test_linkDeliveryWithShipment_server_error() throws Exception {

    doThrow(new ResourceAccessException("Service unavailable"))
        .when(simpleRestConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));
    try {
      deliveryService.linkDeliveryWithShipment(
          "9898", "mockShipmentNumber", "mockShipmentDocumentId", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_NOT_ACCESSIBLE);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_SERVICE_DOWN);
    }
    verify(simpleRestConnector, times(1))
        .put(anyString(), anyString(), any(Map.class), any(Class.class));
  }

  @Test
  public void test_getTrailerZoneTemperature() throws ReceivingException {

    GDMDeliveryTrailerTemperatureInfo trailerZoneTemperatureResponse =
        new GDMDeliveryTrailerTemperatureInfo();
    doReturn(trailerZoneTemperatureResponse)
        .when(gdmRestApiClient)
        .buildTrailerZoneTemperatureResponse(anyLong(), any(HttpHeaders.class));

    GDMDeliveryTrailerTemperatureInfo response =
        deliveryService.getTrailerZoneTemperature(12345l, MockRxHttpHeaders.getHeaders());
    assertNotNull(response);

    verify(gdmRestApiClient, times(1))
        .buildTrailerZoneTemperatureResponse(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_invalidrequest() {
    // prepare data
    GDMDeliveryTrailerTemperatureInfo request;
    // scenario - 2
    request = createGDMDeliveryTrailerTemperatureInfoInvalidRequest2();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBadRequestException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-400");
      assertEquals(
          e.getErrorMessage(),
          "Make sure all entered Zone temperatures have a corresponding PO assigned before saving.");
      assertEquals(e.getDescription(), "Missing PO assignment");
    }

    // scenario - 3
    // Temp values are incorrect (i.e.) not between -999.9 and 999.9
    request = createGDMDeliveryTrailerTemperatureInfoInvalidRequest3();
    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBadRequestException e) {
      assertEquals(
          e.getErrorCode(),
          ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_CODE);
      assertEquals(
          e.getErrorMessage(),
          ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_MESSAGE);
      assertEquals(
          e.getDescription(),
          ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_DESCRIPTION);
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_success_200()
      throws GDMTrailerTemperatureBaseException {
    // mock
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    GDMDeliveryTrailerTemperatureInfo response =
        deliveryService.updateDeliveryTrailerTemperature(
            12345789L, request, MockHttpHeaders.getHeaders());

    // verify
    assertEquals(response.getHasOneZone(), request.getHasOneZone());
    assertEquals(response.getZones(), request.getZones());
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_400()
      throws GDMTrailerTemperatureBaseException {
    GDMTemperatureResponse response = new GDMTemperatureResponse();

    doReturn(new ResponseEntity<>(response, HttpStatus.BAD_REQUEST))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBadRequestException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-400");
      assertEquals(
          e.getErrorMessage(),
          "Make sure all entered Zone temperatures have a corresponding PO assigned before saving.");
      assertEquals(e.getDescription(), "Missing PO assignment");
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_500()
      throws GDMTrailerTemperatureBaseException {
    GDMTemperatureResponse response = new GDMTemperatureResponse();

    doReturn(new ResponseEntity<>(response, INTERNAL_SERVER_ERROR))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureServiceFailedException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-500");
      assertEquals(
          e.getErrorMessage(),
          "We are unable to process the request at this time. This may be due to a system issue. Please try again or contact your supervisor if this continues.");
      assertEquals(e.getDescription(), "GDM service is down.");
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_401()
      throws GDMTrailerTemperatureBaseException {
    GDMTemperatureResponse response = new GDMTemperatureResponse();

    doReturn(new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBadRequestException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-400");
      assertEquals(
          e.getErrorMessage(),
          "Make sure all entered Zone temperatures have a corresponding PO assigned before saving.");
      assertEquals(e.getDescription(), "Missing PO assignment");
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_403()
      throws GDMTrailerTemperatureBaseException {
    GDMTemperatureResponse response = new GDMTemperatureResponse();

    doReturn(new ResponseEntity<>(response, HttpStatus.FORBIDDEN))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBadRequestException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-400");
      assertEquals(
          e.getErrorMessage(),
          "Make sure all entered Zone temperatures have a corresponding PO assigned before saving.");
      assertEquals(e.getDescription(), "Missing PO assignment");
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_404()
      throws GDMTrailerTemperatureBaseException {
    GDMTemperatureResponse response = new GDMTemperatureResponse();

    doReturn(new ResponseEntity<>(response, HttpStatus.NOT_FOUND))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBadRequestException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-400");
      assertEquals(
          e.getErrorMessage(),
          "Make sure all entered Zone temperatures have a corresponding PO assigned before saving.");
      assertEquals(e.getDescription(), "Missing PO assignment");
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_partial_po_finalized_failed_206()
      throws GDMTrailerTemperatureBaseException {
    // mock
    GDMTemperatureResponse response =
        new GDMTemperatureResponse(
            ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_PO_FINALIZED_ERROR_CODE,
            new HashSet<>(Arrays.asList("3490349")));

    doReturn(new ResponseEntity<>(response, HttpStatus.PARTIAL_CONTENT))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperaturePartialPoFinalizedException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-206");
      assertEquals(
          e.getErrorMessage(),
          "All POs got updated except the following as they are already finalized: 3490349.");
      assertEquals(e.getDescription(), "Zone temperatures partially updated.");
    }
  }

  @Test
  public void test_updateDeliveryTrailerTemperature_all_po_finalized_failed_409()
      throws GDMTrailerTemperatureBaseException {
    // mock
    GDMTemperatureResponse response =
        new GDMTemperatureResponse(
            ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_PO_FINALIZED_ERROR_CODE,
            new HashSet<>(Arrays.asList("3490349", "1340504")));

    doReturn(new ResponseEntity<>(response, HttpStatus.PARTIAL_CONTENT))
        .when(gdmRestApiClient)
        .saveZoneTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    // execute
    GDMDeliveryTrailerTemperatureInfo request =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();

    try {
      deliveryService.updateDeliveryTrailerTemperature(
          12345789L, request, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureAllPoFinalizedException e) {
      // verify
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-TRAILER-SAVE-409");
      assertEquals(
          e.getErrorMessage(),
          "Temperatures cannot be updated because all POs have been finalized.");
      assertEquals(e.getDescription(), "Zone temperatures not updated.");
    }
  }

  private GDMDeliveryTrailerTemperatureInfo createGDMDeliveryTrailerTemperatureInfoValidRequest1() {
    GDMDeliveryTrailerTemperatureInfo request = new GDMDeliveryTrailerTemperatureInfo();
    Set<TrailerZoneTemperature> zones = new HashSet<>();
    zones.add(
        new TrailerZoneTemperature(
            "1",
            new TrailerTemperature("1", "F"),
            new HashSet<>(Arrays.asList("1340504", "3490349"))));
    zones.add(
        new TrailerZoneTemperature(
            "2", new TrailerTemperature("5", "F"), new HashSet<>(Arrays.asList("1340504"))));

    request.setZones(zones);
    request.setHasOneZone(false);
    request.setIsNoRecorderFound(false);
    return request;
  }

  private GDMDeliveryTrailerTemperatureInfo
      createGDMDeliveryTrailerTemperatureInfoInvalidRequest1() {
    GDMDeliveryTrailerTemperatureInfo request = new GDMDeliveryTrailerTemperatureInfo();
    Set<TrailerZoneTemperature> zones = new HashSet<>();
    zones.add(
        new TrailerZoneTemperature(
            "1",
            new TrailerTemperature("1", "F"),
            new HashSet<>(Arrays.asList("1340504", "3490349"))));
    zones.add(
        new TrailerZoneTemperature(
            "2", new TrailerTemperature("5", "F"), new HashSet<>(Arrays.asList("1340504"))));

    request.setZones(zones);
    request.setHasOneZone(true);
    return request;
  }

  private GDMDeliveryTrailerTemperatureInfo
      createGDMDeliveryTrailerTemperatureInfoInvalidRequest2() {
    GDMDeliveryTrailerTemperatureInfo request = new GDMDeliveryTrailerTemperatureInfo();
    Set<TrailerZoneTemperature> zones = new HashSet<>();
    zones.add(
        new TrailerZoneTemperature(
            "1",
            new TrailerTemperature("1", "C"),
            new HashSet<>(Arrays.asList("1340504", "3490349"))));

    request.setZones(zones);
    request.setHasOneZone(false);
    request.setIsNoRecorderFound(false);
    return request;
  }

  private GDMDeliveryTrailerTemperatureInfo
      createGDMDeliveryTrailerTemperatureInfoInvalidRequest3() {
    GDMDeliveryTrailerTemperatureInfo request = new GDMDeliveryTrailerTemperatureInfo();
    Set<TrailerZoneTemperature> zones = new HashSet<>();
    zones.add(
        new TrailerZoneTemperature(
            "1",
            new TrailerTemperature("1000.1", "F"),
            new HashSet<>(Arrays.asList("1340504", "3490349"))));
    zones.add(
        new TrailerZoneTemperature(
            "2", new TrailerTemperature("5", "F"), new HashSet<>(Arrays.asList("1340504"))));

    request.setZones(zones);
    request.setHasOneZone(false);
    request.setIsNoRecorderFound(false);
    return request;
  }

  @Test
  public void testPublishArrivedDeliveryStatusToOpen_WhenStatusIsARVAndFeatureFlagIsFalse() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(false);
    deliveryService.publishArrivedDeliveryStatusToOpen(
        123456789L, DeliveryStatus.ARV.toString(), MockHttpHeaders.getHeaders());

    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(
            anyLong(), eq(DeliveryStatus.OPEN.toString()), nullable(List.class), any(Map.class));
  }

  @Test
  public void testPublishArrivedDeliveryStatusToOpen_WhenStatusIsARVAndFeatureFlagIsTrue() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(true);

    when(deliveryStatusPublisher.publishDeliveryStatus(
            anyLong(), eq(DeliveryStatus.OPEN.toString()), nullable(List.class), any(Map.class)))
        .thenReturn(null);

    deliveryService.publishArrivedDeliveryStatusToOpen(
        123456789L, DeliveryStatus.ARV.toString(), MockHttpHeaders.getHeaders());

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            anyLong(), eq(DeliveryStatus.OPEN.toString()), nullable(List.class), any(Map.class));
  }

  @Test
  public void testPublishArrivedDeliveryStatusToOpen_WhenStatusIsNull() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(false);
    deliveryService.publishArrivedDeliveryStatusToOpen(
        123456789L, null, MockHttpHeaders.getHeaders());

    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(anyLong(), any(String.class), nullable(List.class), any(Map.class));
  }

  private DeliveryWithOSDRResponse getMockDeliveryResponse() throws Exception {
    File resource = new ClassPathResource("gdm_v3_getDelivery.json").getFile();
    String mockDeliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);

    return mockDeliveryWithOSDRResponse;
  }

  private DeliveryWithOSDRResponse getMockSingleItemDeliveryResponse() throws Exception {
    File resource = new ClassPathResource("gdm_getDelivery_singleItem.json").getFile();
    String mockDeliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);

    return mockDeliveryWithOSDRResponse;
  }

  @Test
  public void testGetDeliverySummary_receiveAllDisabled() throws Exception {
    doReturn(getMockDeliveryResponse())
        .when(gdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    doReturn(0)
        .when(gdmRestApiClient)
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    doReturn(new ArrayList<>())
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMaster(anyLong(), anyInt());

    DeliverySummary deliverySummary =
        deliveryService.getDeliverySummary(Long.valueOf("123456789"), MockHttpHeaders.getHeaders());

    assertNotNull(deliverySummary);
    assertEquals(deliverySummary.getTrailerTempZonesRecorded().intValue(), 0);
    assertEquals(deliverySummary.getTotalTrailerTempZones().intValue(), 3);
    assertEquals(deliverySummary.getConfirmedPOsCount().intValue(), 0);
    assertEquals(deliverySummary.getTotalPOsCount().intValue(), 2);
    assertFalse(deliverySummary.getIsReceiveAll());
  }

  @Test
  public void testGetDeliverySummary_receiveAllWithMultipleItems() throws Exception {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_RECEIVE_ALL_ENABLED, false);
    doReturn(getMockDeliveryResponse())
        .when(gdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));
    doReturn(3)
        .when(gdmRestApiClient)
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    doReturn(new ArrayList<>())
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMaster(anyLong(), anyInt());

    DeliverySummary deliverySummary =
        deliveryService.getDeliverySummary(Long.valueOf("123456789"), MockHttpHeaders.getHeaders());

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
    verify(gdmRestApiClient, times(1))
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    assertNotNull(deliverySummary);
    assertEquals(deliverySummary.getTrailerTempZonesRecorded().intValue(), 3);
    assertEquals(deliverySummary.getTotalTrailerTempZones().intValue(), 3);
    assertEquals(deliverySummary.getConfirmedPOsCount().intValue(), 0);
    assertEquals(deliverySummary.getTotalPOsCount().intValue(), 2);
    assertFalse(deliverySummary.getIsReceiveAll());
  }

  @Test
  public void testGetDeliverySummary_receiveAllWithPosClosed() throws Exception {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_RECEIVE_ALL_ENABLED, false);
    doReturn(getMockSingleItemDeliveryResponse())
        .when(gdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));
    doReturn(3)
        .when(gdmRestApiClient)
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt = new Receipt();
    receipt.setPurchaseReferenceNumber("9164390046");
    receipt.setFinalizedUserId("test-user");
    receipt.setFinalizeTs(new Date());
    receipts.add(receipt);
    doReturn(receipts)
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMaster(anyLong(), anyInt());

    DeliverySummary deliverySummary =
        deliveryService.getDeliverySummary(Long.valueOf("123456789"), MockHttpHeaders.getHeaders());

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
    verify(gdmRestApiClient, times(1))
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    assertNotNull(deliverySummary);
    assertEquals(deliverySummary.getTrailerTempZonesRecorded().intValue(), 3);
    assertEquals(deliverySummary.getTotalTrailerTempZones().intValue(), 3);
    assertEquals(deliverySummary.getConfirmedPOsCount().intValue(), 1);
    assertEquals(deliverySummary.getTotalPOsCount().intValue(), 1);
    assertFalse(deliverySummary.getIsReceiveAll());
  }

  @Test
  public void testGetDeliverySummary_receiveAllWithSingleItem() throws Exception {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_RECEIVE_ALL_ENABLED, false);
    doReturn(getMockSingleItemDeliveryResponse())
        .when(gdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));
    doReturn(3)
        .when(gdmRestApiClient)
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    doReturn(new ArrayList<>())
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMaster(anyLong(), anyInt());

    DeliverySummary deliverySummary =
        deliveryService.getDeliverySummary(Long.valueOf("123456789"), MockHttpHeaders.getHeaders());

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
    verify(gdmRestApiClient, times(1))
        .getTrailerTempZonesRecorded(anyLong(), any(HttpHeaders.class));

    assertNotNull(deliverySummary);
    assertEquals(deliverySummary.getTrailerTempZonesRecorded().intValue(), 3);
    assertEquals(deliverySummary.getTotalTrailerTempZones().intValue(), 3);
    assertEquals(deliverySummary.getConfirmedPOsCount().intValue(), 0);
    assertEquals(deliverySummary.getTotalPOsCount().intValue(), 1);
    assertTrue(deliverySummary.getIsReceiveAll());
  }

  @Test
  public void testConfirmPOs_ManualGDC_DCFin_disabled() throws Exception {

    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DCFIN_API_DISABLED, false);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(confirmPoResponseBuilder, times(0)).closePO(any(), any(Map.class), anyBoolean());
    verify(receiptService, times(5))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(5))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(5))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(5))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DCFIN_API_DISABLED, false);
  }

  @Test
  public void testConfirmPOsWithAllSuccessTOConfirmManualGdc() throws Exception {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseNoMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  /**
   * Gls POs as Baseline validate if quantity miss match
   *
   * @throws Exception
   */
  @Test
  public void testConfirmPOsWithAllSuccessTOConfirmManualGdc_glsBaseline() throws Exception {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseNoMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED, false);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOsWithAllSuccessTOConfirmManualGdc_RcvMissing_gls_POs_glsBaseline()
      throws Exception {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);

    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseNoMisMatch());

    // missing one receipt in receiving that exist in GLS
    final List<ReceiptSummaryResponse> receiptSummaryResponses2 =
        receiptSummaryEachesResponse.subList(0, 2);
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryResponses2);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED, false);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 1);
    verify(receiptService, times(2))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(2))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(2))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(2)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(2))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  /**
   * when RCV has more POs than GLS, taking GLS as baseline POs list validate if all GLS POs present
   * else throw exception and not the other way if All RCV POs exist in GLS to match with.
   *
   * @throws Exception
   */
  @Test
  public void testConfirmPOsWithAllSuccessTOConfirmManualGdc_RcvHasMorePOs_than_glsPOs_glsBaseline()
      throws Exception {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseNoMisMatch());
    List<ReceiptSummaryResponse> receiptSummaryEachesResponse2HavingMorePOsThanGls =
        receiptSummaryEachesResponse;
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140011", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140012", 1, null, Long.valueOf(96)));

    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse2HavingMorePOsThanGls);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED, false);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOsWithAllSuccessTOConfirmManualGdc_PerfV1() throws Exception {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseNoMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_CONFIRM_POS_PERF_V1_ENABLED, false);

    doReturn(new LinkedHashMap<>())
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOsTOConfirmManual_nonblocking_qty_misMatch() throws ReceivingException {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseWithMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOsTOConfirmManual_nonblocking_qty_misMatch_glsBaseLine()
      throws ReceivingException {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseWithMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED, false);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testConfirmPOsTOConfirmManual_glsServiceDown_error()
      throws IOException, ReceivingException {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_DOWN, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenThrow(
            new ReceivingException("Failed to call GLS", INTERNAL_SERVER_ERROR, "glsServiceError"));
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    try {
      ConfirmPurchaseOrdersResponse confirmPOsResponse =
          deliveryService.confirmPOs(
              Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);
    } catch (ReceivingBadDataException ex) {
      assertEquals(ex.getErrorCode(), ReceivingException.GLS_SERVICE_DOWN_CODE);
      assertEquals(ex.getDescription(), ReceivingException.GLS_SERVICE_DOWN_MSG);
      verify(glsRestApiClient, times(1)).deliveryDetails(anyString(), any());
      verify(receiptService, times(0))
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
      verify(finalizePORequestBodyBuilder, times(0))
          .buildFrom(anyLong(), anyString(), any(Map.class));
      verify(confirmPoResponseBuilder, times(0))
          .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
      verify(confirmPoResponseBuilder, times(0)).closePO(any(), any(Map.class), anyBoolean());
      verify(confirmPoResponseBuilder, times(0))
          .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    }
  }

  @Test
  public void testConfirmPOsTOConfirmManual_blocking_qtyMisMatch() throws ReceivingException {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false))
        .thenReturn(true);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseWithMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    try {
      ConfirmPurchaseOrdersResponse confirmPOsResponse =
          deliveryService.confirmPOs(
              Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);
      final List<ConfirmPurchaseOrdersError> errors = confirmPOsResponse.getErrors();
      assertEquals(errors.size(), 2);
      verify(glsRestApiClient, times(1)).deliveryDetails(anyString(), any());
      verify(receiptService, times(1))
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
      verify(finalizePORequestBodyBuilder, times(1))
          .buildFrom(anyLong(), anyString(), any(Map.class), any());
      verify(confirmPoResponseBuilder, times(1))
          .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
      verify(confirmPoResponseBuilder, times(1)).closePO(any(), any(Map.class), anyBoolean());
      verify(confirmPoResponseBuilder, times(1))
          .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    } catch (ReceivingBadDataException ex) {
      fail("should not be in exception but valid response having 2 errors");
    }
  }

  @Test
  public void testConfirmPOsTOConfirmManual_blocking_qtyMisMatch_glsBaseLine()
      throws ReceivingException {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false))
        .thenReturn(true);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseWithMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED, false);
    try {
      ConfirmPurchaseOrdersResponse confirmPOsResponse =
          deliveryService.confirmPOs(
              Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);
      final List<ConfirmPurchaseOrdersError> errors = confirmPOsResponse.getErrors();
      assertEquals(errors.size(), 2);
      verify(glsRestApiClient, times(1)).deliveryDetails(anyString(), any());
      verify(receiptService, times(1))
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
      verify(finalizePORequestBodyBuilder, times(1))
          .buildFrom(anyLong(), anyString(), any(Map.class), any());
      verify(confirmPoResponseBuilder, times(1))
          .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
      verify(confirmPoResponseBuilder, times(1)).closePO(any(), any(Map.class), anyBoolean());
      verify(confirmPoResponseBuilder, times(1))
          .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    } catch (ReceivingBadDataException ex) {
      fail("should not be in exception but valid response having 2 errors");
    }
  }

  @Test
  public void testConfirmPOsTOConfirmManual_onlyPosInRequest() throws ReceivingException {
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(Arrays.asList("9763140004", "9763140007"));

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);
    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(mockGLSDeliveryDetailsResponseWithMisMatch());
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    verify(glsRestApiClient, times(1)).deliveryDetails(anyString(), any());
    verify(receiptService, times(2))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(2))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(2))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(2)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(2))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  /**
   * With GLS POs as baseline this test case should not give error but success
   *
   * @throws Exception
   */
  @Test
  public void testConfirmPOsWithGLSMissingPO() throws Exception {
    List<String> purchaseReferenceNumbersRequest =
        Arrays.asList("9763140004", "9763140005", "9763140007");
    ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
    mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbersRequest);

    POLine poLine1 = new POLine(1, 96l, "ZA");
    POS pos1 = new POS("9763140004", Arrays.asList(poLine1));
    GLSDeliveryDetailsResponse glsDeliveryDetailsResponse =
        new GLSDeliveryDetailsResponse("1233456", Arrays.asList(pos1));

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(new Receipt());
    mockReceiptList.add(new Receipt());

    doReturn(mockReceiptList)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    doNothing().when(confirmPoResponseBuilder).closePO(any(), any(Map.class), anyBoolean());
    doNothing()
        .when(confirmPoResponseBuilder)
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    doNothing()
        .when(confirmPoResponseBuilder)
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);

    when(glsRestApiClient.deliveryDetails(anyString(), any()))
        .thenReturn(glsDeliveryDetailsResponse);
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(
            Long.valueOf(deliveryNumber), mockConfirmPOsRequest, mockHttpHeaders);

    assertEquals(confirmPOsResponse.getErrors().size(), 0);
    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(finalizePORequestBodyBuilder, times(3))
        .buildFrom(anyLong(), anyString(), any(Map.class), any());
    verify(confirmPoResponseBuilder, times(3))
        .finalizePO(anyLong(), anyString(), any(Map.class), any(FinalizePORequestBody.class));
    verify(confirmPoResponseBuilder, times(3)).closePO(any(), any(Map.class), anyBoolean());
    verify(confirmPoResponseBuilder, times(3))
        .updateReceiptsWithFinalizedDetails(anyList(), anyString(), any());
  }

  @Test
  public void testCloseTrailer() {
    doNothing()
        .when(deliveryStatusPublisher)
        .publishDeliveryStatusMessage(any(), deliveryInfoCaptor.capture());
    deliveryService.closeTrailer(Long.valueOf(deliveryNumber), MockHttpHeaders.getHeaders());

    verify(deliveryStatusPublisher, times(1)).publishDeliveryStatusMessage(any(), any());
    assertEquals(deliveryInfoCaptor.getValue().getDeliveryNumber().intValue(), 123231212);
    assertEquals(deliveryInfoCaptor.getValue().getDeliveryStatus(), "TRAILER_CLOSE");
    assertEquals(deliveryInfoCaptor.getValue().getUserId(), "sysadmin");
  }

  private GLSDeliveryDetailsResponse mockGLSDeliveryDetailsResponseNoMisMatch() {
    POLine poLine1 = new POLine(1, 96l, "ZA");
    POLine poLine2 = new POLine(1, 96l, "ZA");
    POLine poLine3 = new POLine(1, 144l, "ZA");
    POS pos1 = new POS("9763140004", Arrays.asList(poLine1));
    POS pos2 = new POS("9763140005", Arrays.asList(poLine2));
    POS pos3 = new POS("9763140007", Arrays.asList(poLine3));
    return new GLSDeliveryDetailsResponse("1233456", Arrays.asList(pos1, pos2, pos3));
  }

  private GLSDeliveryDetailsResponse mockGLSDeliveryDetailsResponseWithMisMatch() {
    POLine poLine1 = new POLine(1, 90l, "ZA");
    POLine poLine2 = new POLine(1, 96l, "ZA");
    POLine poLine3 = new POLine(1, 145l, "ZA");
    POS pos1 = new POS("9763140004", Arrays.asList(poLine1));
    POS pos2 = new POS("9763140005", Arrays.asList(poLine2));
    POS pos3 = new POS("9763140007", Arrays.asList(poLine3));
    return new GLSDeliveryDetailsResponse("1233456", Arrays.asList(pos1, pos2, pos3));
  }

  @Test
  public void test_getDeliveryStatusSummary_404() throws ReceivingDataNotFoundException {
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    try {
      deliveryService.getDeliveryStatusSummary(12345789l);
    } catch (ReceivingDataNotFoundException e) {
      // verify
      assertEquals(e.getErrorCode(), "No delivery found");
      assertEquals(e.getDescription(), "No delivery found for 12345789");
    }
  }

  @Test
  public void test_getDeliveryStatusSummaryARV_200() throws ReceivingDataNotFoundException {
    mockDeliveryMetaData.setDeliveryStatus(DeliveryStatus.ARV);
    mockDeliveryMetaData.setLastUpdatedDate(lastUpdatedDate);
    mockDeliveryMetaData.setUpdatedBy(updatedBy);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(mockDeliveryMetaData));
    try {
      DeliveryStatusSummary deliveryStatusSummary =
          deliveryService.getDeliveryStatusSummary(12345789l);
      assertEquals(deliveryStatusSummary.getStatus(), 200);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().size(), 1);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(0).getDate(), lastUpdatedDate);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().get(0).getUserId(), updatedBy);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(0).getType(), DeliveryStatus.ARV);
    } catch (ReceivingDataNotFoundException e) {
      fail("Should not throw exception");
    }
  }

  @Test
  public void test_getDeliveryStatusSummaryUnloadComplete_200()
      throws ReceivingDataNotFoundException {
    mockDeliveryMetaData.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE);
    mockDeliveryMetaData.setUnloadingCompleteDate(lastUpdatedDate);
    mockDeliveryMetaData.setLastUpdatedDate(lastUpdatedDate);
    mockDeliveryMetaData.setUpdatedBy(updatedBy);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(mockDeliveryMetaData));
    try {
      DeliveryStatusSummary deliveryStatusSummary =
          deliveryService.getDeliveryStatusSummary(12345789l);
      assertEquals(deliveryStatusSummary.getStatus(), 200);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().size(), 1);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(0).getDate(), lastUpdatedDate);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().get(0).getUserId(), updatedBy);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(0).getType(),
          DeliveryStatus.UNLOADING_COMPLETE);
    } catch (ReceivingDataNotFoundException e) {
      fail("Should not throw exception");
    }
  }

  @Test
  public void testUnloadingComplete() throws ReceivingDataNotFoundException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());

    doReturn(expectedDeliverInfoResponse)
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(
            anyLong(), anyString(), anyString(), anyList(), anyMap(), anyString());

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DELIVERY_UNLOADING_PROCESOR),
            eq(ReceivingConstants.DEFAULT_DELIVERY_UNLOADING_PROCESSOR),
            any()))
        .thenReturn(deliveryUnloadingProcessor);

    deliveryService.unloadComplete(Long.valueOf(deliveryNumber), doorNumber, "update", httpHeaders);

    verify(deliveryService, times(1))
        .unloadComplete(Long.valueOf(deliveryNumber), doorNumber, "update", httpHeaders);
  }

  @Test
  public void test_getDeliveryStatusSummaryComplete_200() throws ReceivingDataNotFoundException {
    mockDeliveryMetaData.setDeliveryStatus(DeliveryStatus.COMPLETE);
    mockDeliveryMetaData.setUnloadingCompleteDate(lastUpdatedDate);
    mockDeliveryMetaData.setLastUpdatedDate(lastUpdatedDate);
    mockDeliveryMetaData.setUpdatedBy(updatedBy);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(mockDeliveryMetaData));
    try {
      DeliveryStatusSummary deliveryStatusSummary =
          deliveryService.getDeliveryStatusSummary(12345789l);
      assertEquals(deliveryStatusSummary.getStatus(), 200);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().size(), 2);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(0).getDate(), lastUpdatedDate);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().get(0).getUserId(), updatedBy);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(0).getType(),
          DeliveryStatus.UNLOADING_COMPLETE);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(1).getDate(), lastUpdatedDate);
      assertEquals(deliveryStatusSummary.getLifeCycleInformation().get(1).getUserId(), updatedBy);
      assertEquals(
          deliveryStatusSummary.getLifeCycleInformation().get(1).getType(),
          DeliveryStatus.COMPLETE);
    } catch (ReceivingDataNotFoundException e) {
      fail("Should not throw exception");
    }
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Tenant-32987 not supported")
  public void testCompleteAll() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
        .thenReturn(completeDeliveryProcessor);
    deliveryService.completeAll(Long.valueOf(deliveryNumber), MockHttpHeaders.getHeaders());
  }

  @Test()
  public void testDeliveryEventTypePublisher() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any(), any()))
        .thenReturn(defaultDeliveryUnloaderProcessor);
    deliveryService.deliveryEventTypePublisher(
        Long.valueOf(deliveryNumber),
        DeliveryUnloaderEventType.UNLOAD_START.toString(),
        MockHttpHeaders.getHeaders());
    verify(defaultDeliveryUnloaderProcessor, times(1))
        .publishDeliveryEvent(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test()
  public void testsaveUnloaderInfo() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any(), any()))
        .thenReturn(defaultDeliveryUnloaderProcessor);
    deliveryService.saveUnloaderInfo(new UnloaderInfoDTO(), MockHttpHeaders.getHeaders());
    verify(defaultDeliveryUnloaderProcessor, times(1))
        .saveUnloaderInfo(any(UnloaderInfoDTO.class), any(HttpHeaders.class));
  }

  @Test()
  public void testgetUnloaderInfo() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any(), any()))
        .thenReturn(defaultDeliveryUnloaderProcessor);
    deliveryService.getUnloaderInfo(123L, "3456", 2);
    verify(defaultDeliveryUnloaderProcessor, times(1))
        .getUnloaderInfo(anyLong(), anyString(), anyInt());
  }

  @Test
  public void testCallGdmToUpdatePackStatus_Returns500Error() throws ReceivingException {
    List<ShipmentInfo> shipmentInfoList = new ArrayList<>();
    when(appConfig.getGdmBaseUrl()).thenReturn("gdm");
    when(simpleRestConnector.put(anyString(), anyString(), any(Map.class), any(Class.class)))
        .thenReturn(new ResponseEntity<>("{}", INTERNAL_SERVER_ERROR));
    deliveryService.callGdmToUpdatePackStatus(shipmentInfoList, MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .put(anyString(), anyString(), any(Map.class), any(Class.class));
  }

  @Test
  public void testGdmStatusApi() throws ReceivingException {
    when(simpleRestConnector.exchange(anyString(), any(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
    List<String> workingList = Arrays.asList("WRK", "PNDFNL", "OPN");
    List<String> poNumberList = Collections.singletonList("1234567GDM");
    String responseBody =
        deliveryService.fetchDeliveriesByStatusUpcAndPoNumber(
            workingList, "3124", "4093", 45, poNumberList);
    Assertions.assertEquals("{}", responseBody);
  }

  @Test
  public void testGdmStatusApiThrows500() throws ReceivingException {
    when(simpleRestConnector.exchange(anyString(), any(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("{}", INTERNAL_SERVER_ERROR));
    List<String> workingList = Arrays.asList("WRK", "PNDFNL", "OPN");
    List<String> poNumberList = Collections.singletonList("1234567GDM");
    Assertions.assertThrows(
        ReceivingException.class,
        () ->
            deliveryService.fetchDeliveriesByStatusUpcAndPoNumber(
                workingList, "3124", "4093", 45, poNumberList));
  }

  @Test
  public void testGdmStatusApiThrowsNotFound() throws ReceivingException {
    when(simpleRestConnector.exchange(anyString(), any(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("{}", NOT_FOUND));
    List<String> workingList = Arrays.asList("WRK", "PNDFNL", "OPN");
    List<String> poNumberList = Collections.singletonList("1234567GDM");
    Assertions.assertThrows(
        ReceivingException.class,
        () ->
            deliveryService.fetchDeliveriesByStatusUpcAndPoNumber(
                workingList, "3124", "4093", 45, poNumberList));
  }

  @Test
  public void testGdmStatusApiThrowsBadRequest() throws ReceivingException {
    when(simpleRestConnector.exchange(anyString(), any(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("{}", BAD_REQUEST));
    List<String> workingList = Arrays.asList("WRK", "PNDFNL", "OPN");
    List<String> poNumberList = Collections.singletonList("1234567GDM");
    Assertions.assertThrows(
        ReceivingException.class,
        () ->
            deliveryService.fetchDeliveriesByStatusUpcAndPoNumber(
                workingList, "3124", "4093", 45, poNumberList));
  }

  @Test
  public void testFetchDeliveriesApi() throws ReceivingException {
    when(simpleRestConnector.exchange(anyString(), any(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
    List<Long> deliveriesList = Arrays.asList(1234344L, 4567892L);
    String responseBody = deliveryService.fetchDeliveries(deliveriesList, "3124", "4093", 45);
    Assertions.assertEquals("{}", responseBody);
  }

  @Test()
  public void testGetRejectionMetadata_NoRecordFound() throws ReceivingException {
    when(rejectionsRepository
            .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
                any(), any(), any()))
        .thenReturn(null);
    RejectionMetadata rejectionMetadata = deliveryService.getRejectionMetadata(123L);
    verify(rejectionsRepository, times(1))
        .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
            any(), any(), any());
    assertEquals(rejectionMetadata.getRejectionReason(), null);
  }

  @Test()
  public void testGetRejectionMetadata_isDeliveryRejectFalse() throws ReceivingException {
    Rejections rejections =
        Rejections.builder().deliveryNumber(123L).entireDeliveryReject(false).build();
    when(rejectionsRepository
            .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
                any(), any(), any()))
        .thenReturn(rejections);
    RejectionMetadata rejectionMetadata = deliveryService.getRejectionMetadata(123L);
    verify(rejectionsRepository, times(1))
        .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
            any(), any(), any());
    assertEquals(rejectionMetadata.getRejectionReason(), null);
  }

  @Test()
  public void testGetRejectionMetadata_success() throws ReceivingException {
    Rejections rejections =
        Rejections.builder()
            .deliveryNumber(123L)
            .entireDeliveryReject(true)
            .createUser("Test")
            .quantity(10)
            .reason("Test")
            .build();
    when(rejectionsRepository
            .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
                any(), any(), any()))
        .thenReturn(rejections);
    RejectionMetadata rejectionMetadata = deliveryService.getRejectionMetadata(123L);
    verify(rejectionsRepository, times(1))
        .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
            any(), any(), any());
    assertEquals(rejectionMetadata.getRejectionReason(), "Test");
  }

  @Test
  public void testGetLpnDetailsByLpnNumber() throws ReceivingException {
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .get(anyString(), any(), anyMap());
    deliveryService.getLpnDetailsByLpnNumber("1l", MockHttpHeaders.getHeaders());
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetLpnDetailsByLpnNumber_EmptyResponseScenario() throws ReceivingException {
    try {
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(restUtils)
          .get(anyString(), any(), anyMap());
      deliveryService.getLpnDetailsByLpnNumber("1l", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), "getLpnDetailsByLpnNumber");
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.LPN_DETAILS_NOT_FOUND);
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetLpnDetailsByLpnNumber_ExceptionScenario_GDM_DOWN() throws ReceivingException {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
    try {
      doReturn(
              ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                  .headers((HttpHeaders) null)
                  .body("Error in fetching resource."))
          .when(restUtils)
          .get(anyString(), any(), anyMap());
      deliveryService.getLpnDetailsByLpnNumber("1l", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), "getLpnDetailsByLpnNumber");
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  @Test
  public void testGetLpnDetailsByLpnNumber_ExceptionScenario() throws ReceivingException {
    try {
      doReturn(
              ResponseEntity.status(INTERNAL_SERVER_ERROR)
                  .headers((HttpHeaders) null)
                  .body("Internal Server Error."))
          .when(restUtils)
          .get(anyString(), any(), anyMap());
      deliveryService.getLpnDetailsByLpnNumber("1l", MockHttpHeaders.getHeaders());
      AssertJUnit.assertTrue(false);
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), "getLpnDetailsByLpnNumber");
      AssertJUnit.assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.LPN_DETAILS_NOT_FOUND);
    }
    verify(restUtils, times(1)).get(anyString(), any(), anyMap());
  }

  public void testGetDeliveryDocumentBySearchCriteria() throws ReceivingException {
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    deliveryService.getDeliveryDocumentBySearchCriteria("{}");
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }
}
