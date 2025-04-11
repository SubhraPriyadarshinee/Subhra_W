package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.VERSION_NOT_NULL;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MISSING_ORG_UNIT_ID_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MISSING_ORG_UNIT_ID_DESC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ORG_UNIT_ID_HEADER;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ConfirmPurchaseOrdersError;
import com.walmart.move.nim.receiving.core.model.ConfirmPurchaseOrdersRequest;
import com.walmart.move.nim.receiving.core.model.ConfirmPurchaseOrdersResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.OverrideRequest;
import com.walmart.move.nim.receiving.core.model.ReOpenDeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.RecordOSDRRequest;
import com.walmart.move.nim.receiving.core.model.RecordOSDRResponse;
import com.walmart.move.nim.receiving.core.model.RejectPalletRequest;
import com.walmart.move.nim.receiving.core.model.TemporaryPalletTiHiRequest;
import com.walmart.move.nim.receiving.core.model.TemporaryPalletTiHiResponse;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.core.service.SecurityService;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.Silent.class)
public class DeliveryControllerTest extends ReceivingControllerTestBase {

  private DeliveryInfo deliveryInfo;
  private ReOpenDeliveryInfo reOpenDeliveryInfo;
  @Autowired private MockMvc mockMvc;

  @Mock private EventProcessor eventProcessor;

  @Autowired
  @Mock
  @Qualifier(ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryServiceImpl deliveryService;

  @Autowired
  @Mock
  @Qualifier(value = "rdcDeliveryService")
  private DeliveryServiceImpl rdcDeliveryService;

  @Autowired @Mock private SecurityService securityService;
  @Autowired @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  private List<ReceiptSummaryResponse> receiptSummaryVnpkResponse;

  @InjectMocks DeliveryController deliveryController;

  @Autowired @Mock protected TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired @Mock protected DeliveryStatusPublisher deliveryStatusPublisher;

  private Gson gson;
  private List<String> purchaseReferenceNumbers;
  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  private Map<String, Object> authDetailsRsp = new HashMap<>();
  private OverrideRequest overrideRequest = new OverrideRequest();

  @BeforeClass
  public void initMocks() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    receiptSummaryVnpkResponse = new ArrayList<>();
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(4)));
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(2)));
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(3)));

    deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(21231313L);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    deliveryInfo.setTs(new Date());
    deliveryInfo.setUserId(
        MockHttpHeaders.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0));
    deliveryInfo.setReceipts(receiptSummaryVnpkResponse);

    reOpenDeliveryInfo = new ReOpenDeliveryInfo();
    reOpenDeliveryInfo.setDeliveryNumber(21231313L);
    reOpenDeliveryInfo.setReceiverUserId(
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));

    purchaseReferenceNumbers = new ArrayList<>();
    purchaseReferenceNumbers.add("7836237741");
    purchaseReferenceNumbers.add("7836237742");
    purchaseReferenceNumbers.add("7836237743");
    purchaseReferenceNumbers.add("7836237744");
    purchaseReferenceNumbers.add("7836237745");

    overrideRequest.setUserId("sysadmin");
    overrideRequest.setPassword("pwd");
    overrideRequest.setPurchaseReferenceNumber("784349344");
    overrideRequest.setPurchaseReferenceLineNumber(1);

    authDetailsRsp.put(ReceivingConstants.SECURITY_ID, 1);
    authDetailsRsp.put(ReceivingConstants.TOKEN, "dummy");

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(securityService);
    reset(deliveryService);
    reset(deliveryItemOverrideService);
    reset(tenantSpecificConfigReader);
    reset(rdcDeliveryService);
  }

  @Test
  public void testCompleteDelivery() {
    try {

      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

      when(deliveryService.completeDelivery(any(Long.class), false, any(HttpHeaders.class)))
          .thenReturn(deliveryInfo);

      String response =
          mockMvc
              .perform(MockMvcRequestBuilders.put("/deliveries/21231313").headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      DeliveryInfo deliveryInfoActual =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  (JsonDeserializer<Date>)
                      (jsonElement, type, context) ->
                          new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
              .create()
              .fromJson(response, DeliveryInfo.class);

      assertEquals(deliveryInfoActual.getDeliveryNumber(), deliveryInfo.getDeliveryNumber());
      assertEquals(deliveryInfoActual.getDeliveryStatus(), deliveryInfo.getDeliveryStatus());
      assertEquals(deliveryInfoActual.getTs(), deliveryInfo.getTs());
      assertEquals(deliveryInfoActual.getUserId(), deliveryInfo.getUserId());
      assertEquals(deliveryInfoActual.getReceipts().size(), deliveryInfo.getReceipts().size());

      when(deliveryService.completeDelivery(any(Long.class), false, any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingException(
                  ReceivingException.COMPLETE_DELIVERY_NO_RECEIVING_ERROR_MESSAGE,
                  HttpStatus.BAD_REQUEST,
                  ReceivingException.COMPLETE_DELIVERY_ERROR_CODE));

      mockMvc
          .perform(MockMvcRequestBuilders.put("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isBadRequest());

      when(deliveryService.completeDelivery(any(Long.class), false, any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingException(
                  ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_CODE));

      mockMvc
          .perform(MockMvcRequestBuilders.put("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isInternalServerError());

      /*
       * With out HTTP headers will produce bad request.
       */
      mockMvc
          .perform(MockMvcRequestBuilders.put("/deliveries/21231313"))
          .andExpect(status().isBadRequest());

      /*
       * With out path parameter will produce bad request.
       */
      mockMvc
          .perform(MockMvcRequestBuilders.put("/deliveries/null"))
          .andExpect(status().isBadRequest());

      verify(deliveryService, times(3))
          .completeDelivery(any(Long.class), false, any(HttpHeaders.class));

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testReOpenDelivery() {
    try {
      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

      when(deliveryService.reOpenDelivery(any(Long.class), any(HttpHeaders.class)))
          .thenReturn(reOpenDeliveryInfo);

      String response =
          mockMvc
              .perform(patch("/deliveries/21231313").headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      ReOpenDeliveryInfo reOpendeliveryInfoActual =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  (JsonDeserializer<Date>)
                      (jsonElement, type, context) ->
                          new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
              .create()
              .fromJson(response, ReOpenDeliveryInfo.class);

      assertEquals(
          reOpendeliveryInfoActual.getDeliveryNumber(), reOpenDeliveryInfo.getDeliveryNumber());
      assertEquals(
          reOpendeliveryInfoActual.getReceiverUserId(), reOpenDeliveryInfo.getReceiverUserId());

      when(deliveryService.reOpenDelivery(any(Long.class), any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingException(
                  ReceivingException.UNABLE_TO_REOPEN_DELIVERY,
                  HttpStatus.BAD_REQUEST,
                  ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE,
                  ReceivingException.ERROR_HEADER_REOPEN_DELIVERY_FAILED));

      mockMvc
          .perform(patch("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isBadRequest());

      when(deliveryService.reOpenDelivery(any(Long.class), any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingException(
                  ReceivingException.UNABLE_TO_FIND_DELIVERY_TO_REOPEN,
                  HttpStatus.NOT_FOUND,
                  ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE,
                  ReceivingException.ERROR_HEADER_REOPEN_DELIVERY_FAILED));

      mockMvc
          .perform(patch("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isNotFound());

      when(deliveryService.completeDelivery(any(Long.class), false, any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingException(
                  ReceivingException.GDM_SERVICE_DOWN,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE));

      mockMvc
          .perform(MockMvcRequestBuilders.put("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isInternalServerError());

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testRecordOSDRReasonCodes() throws Exception {

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    RecordOSDRResponse mockResponse = new RecordOSDRResponse();
    doReturn(mockResponse)
        .when(deliveryService)
        .recordOSDR(anyLong(), anyString(), anyInt(), any(RecordOSDRRequest.class), any());

    RecordOSDRRequest mockRequestBody = new RecordOSDRRequest();
    mockRequestBody.setRejectedQty(0);
    mockRequestBody.setVersion(1);

    mockMvc
        .perform(
            patch("/deliveries/123231212/ref/4445530688/line/1")
                .content(new Gson().toJson(mockRequestBody))
                .headers(httpHeaders))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  public void testNegativeRejectQty() throws Exception {

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    RecordOSDRRequest mockRecordOSDRRequest = new RecordOSDRRequest();
    mockRecordOSDRRequest.setRejectedQty(-10);
    mockRecordOSDRRequest.setVersion(1);

    mockMvc
        .perform(
            patch("/deliveries/123231212/ref/4445530688/line/1")
                .content(new Gson().toJson(mockRecordOSDRRequest))
                .headers(httpHeaders))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void testRecordOSDRRequest_no_version() throws Exception {

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    RecordOSDRResponse mockResponse = new RecordOSDRResponse();
    doReturn(mockResponse)
        .when(deliveryService)
        .recordOSDR(anyLong(), anyString(), anyInt(), any(RecordOSDRRequest.class), any());

    RecordOSDRRequest mockRequestBody = new RecordOSDRRequest();
    mockRequestBody.setRejectedQty(0);

    final MvcResult mvcResult =
        mockMvc
            .perform(
                patch("/deliveries/123231212/ref/4445530688/line/1")
                    .content(new Gson().toJson(mockRequestBody))
                    .headers(httpHeaders))
            .andReturn();

    assertEquals(mvcResult.getResponse().getStatus(), 400);
    assertTrue(mvcResult.getResolvedException().getMessage().contains(VERSION_NOT_NULL));
  }

  @Test
  public void testInvalidRecordOSDRReasonCodesRequest() throws Exception {

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    doThrow(
            new ReceivingException(
                "", HttpStatus.BAD_REQUEST, ReceivingException.RECORD_OSDR_ERROR_CODE))
        .when(deliveryService)
        .recordOSDR(anyLong(), anyString(), anyInt(), any(RecordOSDRRequest.class), any());

    try {
      RecordOSDRRequest mockBody = new RecordOSDRRequest();
      deliveryController.recordOSDR(123231212l, "4445530688", 1, mockBody, httpHeaders);
    } catch (ReceivingException e) {
      assertSame("Https Staus should be 422", HttpStatus.BAD_REQUEST, e.getHttpStatus());
    }

    doThrow(
            new ReceivingException(
                "", HttpStatus.NOT_FOUND, ReceivingException.RECORD_OSDR_ERROR_CODE))
        .when(deliveryService)
        .recordOSDR(anyLong(), anyString(), anyInt(), any(RecordOSDRRequest.class), any());

    try {
      RecordOSDRRequest mockBody = new RecordOSDRRequest();
      deliveryController.recordOSDR(123231212l, "4445530688", 1, mockBody, httpHeaders);
    } catch (ReceivingException e) {
      assertSame("Https Staus should be 404", HttpStatus.NOT_FOUND, e.getHttpStatus());
    }
  }

  @Test
  public void testGetDelivery() {

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      DeliveryWithOSDRResponse response = new DeliveryWithOSDRResponse();
      when(deliveryService.getDeliveryWithOSDRByDeliveryNumber(
              any(Long.class), any(Map.class), any(Boolean.class), any()))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/21231313?includeOSDR=true")
                  .headers(httpHeaders))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      when(deliveryService.getDeliveryWithOSDRByDeliveryNumber(
              any(Long.class), any(Map.class), any(Boolean.class), any()))
          .thenThrow(new ReceivingException("", HttpStatus.NOT_FOUND));

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/21231313?includeOSDR=true")
                  .headers(httpHeaders))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      DeliveryWithOSDRResponse response = new DeliveryWithOSDRResponse();
      when(deliveryService.getDeliveryWithOSDRByDeliveryNumber(
              any(Long.class), any(Map.class), any(Boolean.class), any()))
          .thenReturn(response);

      mockMvc
          .perform(MockMvcRequestBuilders.get("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isBadRequest());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testSaveTemporaryPalletTiHi() {
    try {
      TemporaryPalletTiHiRequest mockRequestBody = new TemporaryPalletTiHiRequest();
      mockRequestBody.setPalletTi(9);
      mockRequestBody.setVersion(1);

      TemporaryPalletTiHiResponse temporaryTiHiResponse = new TemporaryPalletTiHiResponse();
      temporaryTiHiResponse.setVersion(2);

      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

      when(deliveryItemOverrideService.saveTemporaryPalletTiHi(
              anyLong(), anyLong(), any(TemporaryPalletTiHiRequest.class), httpHeaders))
          .thenReturn(new DeliveryItemOverride());

      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/deliveries/21231313/item/31231313")
                  .content(new Gson().toJson(mockRequestBody))
                  .headers(httpHeaders))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testUnloadingComplete() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(deliveryService.unloadComplete(
            anyLong(), anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(getDeliveryInfo());

    this.mockMvc
        .perform(
            MockMvcRequestBuilders.put("/deliveries/123456789/unload?doorNumber=101")
                .headers(httpHeaders))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

    verify(deliveryService, times(1))
        .unloadComplete(anyLong(), anyString(), eq(null), any(HttpHeaders.class));
  }

  @Test
  public void testPutDeliveryToWorking() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DeliveryInfo workingDeliveryInfo = getDeliveryInfo();
    workingDeliveryInfo.setDeliveryStatus(DeliveryStatus.WORKING.name());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(workingDeliveryInfo);

    this.mockMvc
        .perform(
            MockMvcRequestBuilders.put("/deliveries/123456789/publish/WORKING")
                .headers(httpHeaders))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testPutDeliveryToStatus_InvalidStatus() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DeliveryInfo workingDeliveryInfo = getDeliveryInfo();
    workingDeliveryInfo.setDeliveryStatus(DeliveryStatus.WORKING.name());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(workingDeliveryInfo);

    this.mockMvc
        .perform(
            MockMvcRequestBuilders.put("/deliveries/123456789/publish/RANDOM").headers(httpHeaders))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().isBadRequest());

    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testUnloadingCompleteWithOutDoorNumber() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(deliveryService.unloadComplete(anyLong(), eq(null), eq(null), any(HttpHeaders.class)))
        .thenReturn(getDeliveryInfo());

    this.mockMvc
        .perform(MockMvcRequestBuilders.put("/deliveries/123456789/unload").headers(httpHeaders))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

    verify(deliveryService, times(1))
        .unloadComplete(anyLong(), eq(null), eq(null), any(HttpHeaders.class));
  }

  @Test
  public void testConfirmPurchaseOrders() throws Exception {
    try {
      ConfirmPurchaseOrdersRequest mockConfirmPOsRequest = new ConfirmPurchaseOrdersRequest();
      mockConfirmPOsRequest.setPurchaseReferenceNumbers(purchaseReferenceNumbers);

      ConfirmPurchaseOrdersResponse mockConfirmPOsResponse = new ConfirmPurchaseOrdersResponse();
      List<ConfirmPurchaseOrdersError> errors = new ArrayList<>();
      mockConfirmPOsResponse.setErrors(errors);

      doReturn(mockConfirmPOsResponse)
          .when(deliveryService)
          .confirmPOs(any(Long.class), any(ConfirmPurchaseOrdersRequest.class), any(Map.class));

      mockMvc
          .perform(
              patch("/deliveries/93108420/ref?action=confirmPO")
                  .content(new Gson().toJson(mockConfirmPOsRequest))
                  .headers(GdcHttpHeaders.getHeaders()))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private DeliveryInfo getDeliveryInfo() {

    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(123456789L);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE.name());
    deliveryInfo.setDoorNumber("D101");
    deliveryInfo.setTrailerNumber("TLR1001");
    return deliveryInfo;
  }

  @Test
  public void testGetOsdrSummary() throws ReceivingException {

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    when(deliveryService.getOsdrInformation(
            anyLong(), anyString(), anyInt(), any(), anyString(), anyString()))
        .thenReturn(new OsdrSummary());

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/osdr")
                  .headers(httpHeaders)
                  .param("deliveryNumber", "89555977"))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      Assert.fail();
    }

    verify(deliveryService, times(1))
        .getOsdrInformation(eq(89555977L), eq(null), eq(null), any(), eq(null), any());
  }

  @Test
  public void testOverrideExpiryWithKotlinApp() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ReceivingConstants.IS_KOTLIN_CLIENT, "true");
    when(securityService.authorizeWithCcmToken(anyString(), anyString())).thenReturn(true);
    doNothing()
        .when(securityService)
        .validateAuthorization(anyString(), anyBoolean(), anyBoolean());

    when(deliveryItemOverrideService.override(anyString(), anyString(), any(OverrideRequest.class)))
        .thenReturn(new DeliveryMetaData());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/12345678/override?action=expiry")
                .content(new Gson().toJson(overrideRequest))
                .headers(httpHeaders))
        .andExpect(status().is2xxSuccessful());

    verify(securityService, times(1)).authorizeWithCcmToken(anyString(), anyString());
    verify(deliveryItemOverrideService, times(1))
        .override(anyString(), anyString(), any(OverrideRequest.class));
  }

  @Test
  public void testOverrideExpiry() throws Exception {
    when(securityService.authenticate(any(OverrideRequest.class), any(Map.class)))
        .thenReturn(authDetailsRsp);
    when(securityService.authorize(
            anyString(), anyString(), anyString(), anyString(), any(Map.class)))
        .thenReturn(true);
    doNothing()
        .when(securityService)
        .validateAuthorization(anyString(), anyBoolean(), anyBoolean());
    when(deliveryItemOverrideService.override(anyString(), anyString(), any(OverrideRequest.class)))
        .thenReturn(new DeliveryMetaData());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/21231313/override?action=expiry")
                .content(new Gson().toJson(overrideRequest))
                .headers(headers))
        .andExpect(status().is2xxSuccessful());

    verify(securityService, times(1)).authenticate(any(OverrideRequest.class), any(Map.class));
    verify(securityService, times(1))
        .authorize(anyString(), anyString(), anyString(), anyString(), any(Map.class));
    verify(deliveryItemOverrideService, times(1))
        .override(anyString(), anyString(), any(OverrideRequest.class));
  }

  @Test
  public void testOverrideOverage() throws Exception {
    when(securityService.authenticate(any(OverrideRequest.class), any(Map.class)))
        .thenReturn(authDetailsRsp);
    when(securityService.authorize(
            anyString(), anyString(), anyString(), anyString(), any(Map.class)))
        .thenReturn(true);
    doNothing()
        .when(securityService)
        .validateAuthorization(anyString(), anyBoolean(), anyBoolean());
    when(deliveryItemOverrideService.override(anyString(), anyString(), any(OverrideRequest.class)))
        .thenReturn(new DeliveryMetaData());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/21231313/override?action=overages")
                .content(new Gson().toJson(overrideRequest))
                .headers(headers))
        .andExpect(status().is2xxSuccessful());

    verify(securityService, times(1)).authenticate(any(OverrideRequest.class), any(Map.class));
    verify(securityService, times(1))
        .authorize(anyString(), anyString(), anyString(), anyString(), any(Map.class));
    verify(deliveryItemOverrideService, times(1))
        .override(anyString(), anyString(), any(OverrideRequest.class));
  }

  @Test
  public void testOverrideHaccp() throws Exception {
    when(securityService.authenticate(any(OverrideRequest.class), any(Map.class)))
        .thenReturn(authDetailsRsp);
    when(securityService.authorize(
            anyString(), anyString(), anyString(), anyString(), any(Map.class)))
        .thenReturn(true);
    doNothing()
        .when(securityService)
        .validateAuthorization(anyString(), anyBoolean(), anyBoolean());
    when(deliveryItemOverrideService.override(anyString(), anyString(), any(OverrideRequest.class)))
        .thenReturn(new DeliveryMetaData());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/21231313/override?action=haccp")
                .content(new Gson().toJson(overrideRequest))
                .headers(headers))
        .andExpect(status().is2xxSuccessful());

    verify(securityService, times(1)).authenticate(any(OverrideRequest.class), any(Map.class));
    verify(securityService, times(1))
        .authorize(anyString(), anyString(), anyString(), anyString(), any(Map.class));
    verify(deliveryItemOverrideService, times(1))
        .override(anyString(), anyString(), any(OverrideRequest.class));
  }

  @Test
  public void testOverrideBadRequest() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/21231313/override?action=")
                .content(new Gson().toJson(overrideRequest))
                .headers(headers))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreatePalletReject() throws Exception {

    RejectPalletRequest rejectPalletRequest = createPalletRejectRequest();

    doNothing().when(deliveryService).recordPalletReject(any(RejectPalletRequest.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/deliveries/osdr/pallet/reject")
                .content(gson.toJson(rejectPalletRequest))
                .headers(headers))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn();

    verify(deliveryService, times(1)).recordPalletReject(any(RejectPalletRequest.class));
  }

  @Test
  public void test_publishDeliveryStatusUpdate_Success() throws Exception {
    DeliveryInfo mockDeliveryInfo = new DeliveryInfo();
    mockDeliveryInfo.setDeliveryNumber(121212l);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(rdcDeliveryService);
    doNothing()
        .when(rdcDeliveryService)
        .publishDeliveryStatus(any(DeliveryInfo.class), any(HttpHeaders.class));
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/deliveries/publishstatus")
                    .content(gson.toJson(mockDeliveryInfo))
                    .headers(headers))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(result.getResponse().getStatus(), 200);
    assertEquals(
        result.getResponse().getContentAsString(), "Successfully published delivery status");
    verify(rdcDeliveryService, times(1))
        .publishDeliveryStatus(any(DeliveryInfo.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_publishDeliveryStatusUpdate_Failure() throws Exception {
    DeliveryInfo mockDeliveryInfo = new DeliveryInfo();
    mockDeliveryInfo.setDeliveryNumber(121212l);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(rdcDeliveryService);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_DELIVERY_STATUS_UPDATE_REQUEST,
                String.format(
                    ReceivingConstants.INVALID_DELIVERY_STATUS_UPDATE_REQUEST,
                    mockDeliveryInfo.getDeliveryNumber()),
                mockDeliveryInfo.getDeliveryNumber().toString()))
        .when(rdcDeliveryService)
        .publishDeliveryStatus(any(DeliveryInfo.class), any(HttpHeaders.class));
    deliveryController.publishDeliveryStatus(mockDeliveryInfo, headers);
    verify(rdcDeliveryService, times(1))
        .publishDeliveryStatus(any(DeliveryInfo.class), any(HttpHeaders.class));
  }

  @Test
  public void test_save_trailer_temperature_success_200() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    GDMDeliveryTrailerTemperatureInfo mockResponse =
        createGDMDeliveryTrailerTemperatureInfoRequest1();
    doReturn(mockResponse)
        .when(deliveryService)
        .updateDeliveryTrailerTemperature(
            anyLong(), any(GDMDeliveryTrailerTemperatureInfo.class), any(HttpHeaders.class));

    GDMDeliveryTrailerTemperatureInfo mockRequestBody =
        createGDMDeliveryTrailerTemperatureInfoRequest1();

    final MvcResult mvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/deliveries/123231212/trailerTemperature")
                    .content(new Gson().toJson(mockRequestBody))
                    .headers(httpHeaders))
            .andReturn();

    assertEquals(mvcResult.getResponse().getStatus(), 200);
  }

  private GDMDeliveryTrailerTemperatureInfo createGDMDeliveryTrailerTemperatureInfoRequest1() {
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

  private GDMDeliveryTrailerTemperatureInfo createGDMDeliveryTrailerTemperatureInfoRequest2() {
    GDMDeliveryTrailerTemperatureInfo request = new GDMDeliveryTrailerTemperatureInfo();
    Set<TrailerZoneTemperature> zones = new HashSet<>();
    zones.add(
        new TrailerZoneTemperature(
            "1",
            new TrailerTemperature("1", "F"),
            new HashSet<>(Arrays.asList("1340504", "3490349"))));

    request.setZones(zones);
    request.setHasOneZone(true);
    return request;
  }

  private RejectPalletRequest createPalletRejectRequest() {
    RejectPalletRequest rejectPalletRequest = new RejectPalletRequest();
    rejectPalletRequest.setDeliveryNumber(12345678L);
    rejectPalletRequest.setDoorNumber("D101");
    rejectPalletRequest.setPurchaseReferenceNumber("1234555");
    rejectPalletRequest.setPurchaseReferenceLineNumber(1);
    rejectPalletRequest.setWhpkQty(2);
    rejectPalletRequest.setVnpkQty(2);
    rejectPalletRequest.setItemNumber(1234555L);
    rejectPalletRequest.setOrderableGTIN("111111");
    rejectPalletRequest.setConsumableGTIN("222222");
    rejectPalletRequest.setRotateDate(new Date());
    rejectPalletRequest.setRejectedQty(12);
    rejectPalletRequest.setRejectedUOM("ZA");
    rejectPalletRequest.setRejectedReasonCode("R11");
    rejectPalletRequest.setRejectionComment("Expired");
    return rejectPalletRequest;
  }

  @Test
  public void testGetTrailerZoneTemperature() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    try {
      GDMDeliveryTrailerTemperatureInfo response = new GDMDeliveryTrailerTemperatureInfo();
      when(deliveryService.getTrailerZoneTemperature(any(Long.class), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/21231313/trailerTemperature")
                  .headers(httpHeaders))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      when(deliveryService.getTrailerZoneTemperature(any(Long.class), any(HttpHeaders.class)))
          .thenThrow(new ReceivingException("", HttpStatus.NOT_FOUND));

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/21231313/trailerTemperature")
                  .headers(httpHeaders))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      GDMDeliveryTrailerTemperatureInfo response = new GDMDeliveryTrailerTemperatureInfo();
      when(deliveryService.getTrailerZoneTemperature(any(Long.class), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(MockMvcRequestBuilders.get("/deliveries/21231313").headers(httpHeaders))
          .andExpect(status().isBadRequest());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testGetDeliverySummary() throws Exception {
    doReturn(new DeliverySummary())
        .when(deliveryService)
        .getDeliverySummary(anyLong(), any(HttpHeaders.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/deliveries/21231313/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  public void testCloseTrailer() throws Exception {
    doNothing().when(deliveryService).closeTrailer(anyLong(), any(HttpHeaders.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/deliveries/21231313/closetrailer")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  public void testGetDeliveryStatusSummary() throws Exception {
    doReturn(new DeliveryStatusSummary()).when(deliveryService).getDeliveryStatusSummary(anyLong());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/deliveries/21231313/status")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  public void testCompleteAll() throws Exception {
    doReturn(new DeliveryInfo()).when(deliveryService).completeAll(anyLong(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/deliveries/20634023/completeAll")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is2xxSuccessful());

    verify(deliveryService, times(1)).completeAll(anyLong(), any());
  }

  @Test
  public void test_receiveintoOss() throws Exception {
    doReturn(new ReceiveIntoOssResponse())
        .when(deliveryService)
        .receiveIntoOss(anyLong(), any(ReceiveIntoOssRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/deliveries/receive/20634023")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is2xxSuccessful());
    verify(deliveryService, times(1)).receiveIntoOss(anyLong(), any(), any(HttpHeaders.class));
  }

  @Test
  public void test_receiveIntoOss() throws Exception {
    doReturn(new ReceiveIntoOssResponse())
        .when(deliveryService)
        .receiveIntoOss(anyLong(), any(ReceiveIntoOssRequest.class), any(HttpHeaders.class));
    try {
      final HttpHeaders headers1 = MockHttpHeaders.getHeaders();
      headers1.set(ORG_UNIT_ID_HEADER, "3");
      final ReceiveIntoOssResponse receiveIntoOssResponse =
          deliveryController.receiveIntoOss(123L, new ReceiveIntoOssRequest(), headers1);
      assertNotNull(receiveIntoOssResponse);
    } catch (ReceivingException e) {
      fail("should not throw exception ");
    }
    verify(deliveryService, times(1)).receiveIntoOss(anyLong(), any(), any(HttpHeaders.class));
  }

  @Test
  public void test_receiveIntoOss_missingHeader() throws Exception {
    doReturn(new ReceiveIntoOssResponse())
        .when(deliveryService)
        .receiveIntoOss(anyLong(), any(ReceiveIntoOssRequest.class), any(HttpHeaders.class));
    try {
      final HttpHeaders headers1 = MockHttpHeaders.getHeaders();
      final String orgUnitId = headers1.getFirst(ORG_UNIT_ID_HEADER);
      if (StringUtils.isNotBlank(orgUnitId)) {
        headers1.remove(ORG_UNIT_ID_HEADER);
      }
      final ReceiveIntoOssResponse receiveIntoOssResponse =
          deliveryController.receiveIntoOss(123L, new ReceiveIntoOssRequest(), headers1);
      fail("should not throw exception ");

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), MISSING_ORG_UNIT_ID_CODE);
      assertEquals(e.getDescription(), MISSING_ORG_UNIT_ID_DESC);
    }
    verify(deliveryService, times(1)).receiveIntoOss(anyLong(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testDeliveryUpdate() throws Exception {
    DeliveryUpdateMessage mockDeliveryUpdateMessage = new DeliveryUpdateMessage();
    mockDeliveryUpdateMessage.setCountryCode("US");
    mockDeliveryUpdateMessage.setSiteNumber("4321");
    mockDeliveryUpdateMessage.setDeliveryNumber("39568046");
    mockDeliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    mockDeliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.name());
    mockDeliveryUpdateMessage.setUrl(
        "https://gls-atlas-gdm-core-wm-fc-qa.walmart.com/document/v2/deliveries/update");
    when(tenantSpecificConfigReader.getDeliveryEventProcessor(anyString()))
        .thenReturn(eventProcessor);
    doNothing().when(eventProcessor).processEvent(any());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(mockDeliveryUpdateMessage))
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testDeliveryUpdateWithFailure() throws Exception {
    DeliveryUpdateMessage mockDeliveryUpdateMessage = new DeliveryUpdateMessage();
    mockDeliveryUpdateMessage.setCountryCode("US");
    mockDeliveryUpdateMessage.setSiteNumber("4321");
    mockDeliveryUpdateMessage.setDeliveryNumber("39568046");
    mockDeliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    mockDeliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.name());
    mockDeliveryUpdateMessage.setUrl(
        "https://gls-atlas-gdm-core-wm-fc-qa.walmart.com/document/v2/deliveries/update");
    when(tenantSpecificConfigReader.getDeliveryEventProcessor(anyString()))
        .thenReturn(eventProcessor);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    doThrow(new ReceivingException(ExceptionCodes.INVALID_GDM_DOCUMENT_DATA))
        .when(eventProcessor)
        .processEvent(any(DeliveryUpdateMessage.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/deliveries/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(mockDeliveryUpdateMessage))
                .headers(httpHeaders))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void test_getRejectionMetadata() throws Exception {
    doReturn(new RejectionMetadata()).when(deliveryService).getRejectionMetadata(anyLong());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/deliveries/20634023/getRejectionMetadata")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is2xxSuccessful());
    verify(deliveryService, times(1)).getRejectionMetadata(anyLong());
  }
}
