package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.config.FdeConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockFdeSpec;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RetryableFDEServiceTest extends ReceivingTestBase {

  @InjectMocks private RetryableFDEService fdeService;
  @Mock private FdeConfig fdeConfig;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private String requestPayload;
  private FdeCreateContainerRequest fdeCreateContainerRequest;

  private InstructionError instructionError;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    requestPayload = MockACLMessageData.getBuildContainerRequestForACLReceiving();
    fdeCreateContainerRequest =
        JacksonParser.convertJsonToObject(requestPayload, FdeCreateContainerRequest.class);
  }

  @AfterMethod
  public void tearDown() {
    reset(fdeConfig);
    reset(retryableRestConnector);
  }

  @Test
  public void testReceiveOnConveyorCase() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    String expectedResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.CREATED));
    try {
      String response = fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
      verify(retryableRestConnector, times(1))
          .post(anyString(), captor.capture(), headerCaptor.capture(), same(String.class));
      assertEquals(captor.getValue().replaceAll("\\s+", ""), requestPayload.replaceAll("\\s+", ""));

      assertEquals(response, expectedResponse);
      HttpHeaders capturedHttpHeaders = headerCaptor.getValue();
      assertEquals(
          capturedHttpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
          TenantContext.getFacilityNum().toString());
      assertEquals(
          capturedHttpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE).toUpperCase(),
          TenantContext.getFacilityCountryCode().toUpperCase());
      assertEquals(
          capturedHttpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY), "1a2bc3d4");
      assertEquals(capturedHttpHeaders.getContentType(), MediaType.APPLICATION_JSON_UTF8);
    } catch (ReceivingException e) {
      fail("No exception expected.");
    }
  }

  @Test
  public void testReceiveNullResponseFromOF() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(null)
        .when(retryableRestConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));

    InstructionError instructionError =
        InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
    try {
      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");

    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(instructionError.getErrorMessage(), "null"));
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveClientSeriesError() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.CONFLICT,
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00009\",\"desc\":\"No allocations\"}]}"
                    .getBytes(),
                Charset.forName("UTF-8")));

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00009");
    try {

      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveServerSeriesError() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00010\",\"desc\":\"internal server error\"}]}"
                    .getBytes(),
                Charset.forName("UTF-8")));

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
    try {

      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveOFServiceDown() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    InstructionError instructionError =
        InstructionErrorCode.getErrorValue(ReceivingException.OF_NETWORK_ERROR);
    try {
      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveClient_DetailedErrorMessage_NoAllocation() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)))
        .thenReturn(Boolean.TRUE);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.CONFLICT,
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00009\",\"desc\":\"No allocations\"}]}"
                    .getBytes(),
                Charset.forName("UTF-8")));

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00009");
    try {

      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(e.getErrorResponse().getErrorKey(), ExceptionCodes.NO_ALLOCATION);
    }
  }

  @Test
  public void test_receiveClient_DetailedErrorMessage_InternalServerError() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)))
        .thenReturn(Boolean.TRUE);

    when(retryableRestConnector.post(any(), any(), any(HttpHeaders.class), any()))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00010\",\"desc\":\"internal server error\",\"detailed_desc\":\"Test detailed message\"}]}"
                    .getBytes(),
                Charset.forName("UTF-8")));

    try {
      instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("ReceivingException: 'Test detailed message' should be thrown");
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), "Test detailed message");
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(e.getErrorResponse().getErrorKey(), "GLS-RCV-INVALID-ALLOCATION-500");
    }
  }

  @Test
  public void test_receiveClient_NoDetailedErrorMessage_NoAllocation() {
    // this flow will be for when feature flag is off, so normal ACC flow
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)))
        .thenReturn(Boolean.FALSE);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.CONFLICT,
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00009\",\"desc\":\"No allocations\"}]}"
                    .getBytes(),
                Charset.forName("UTF-8")));

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00009");
    try {
      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertNull(e.getErrorResponse().getErrorKey());
    }
  }

  @Test
  public void test_receiveClient_NoDetailedErrorMessage_ExcludingNoAllocationError() {
    // this flow will be for when feature flag is off, so normal ACC flow
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)))
        .thenReturn(Boolean.FALSE);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00010\",\"desc\":\"internal server error\"}]}"
                    .getBytes(),
                Charset.forName("UTF-8")));

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
    try {
      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertNull(e.getErrorResponse().getErrorKey());
    }
  }

  @Test
  public void test_receiveClient_NoDetailedErrorMessage_IsConveyableMissingException() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)))
        .thenReturn(Boolean.FALSE);
    HttpClientErrorException clientErrorException =
        new HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            null,
            "{\"errors\":[{\"errorCode\":1004,\"errorMsg\":\"isConveyable is missing\"}]}"
                .getBytes(),
            StandardCharsets.UTF_8);
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenThrow(clientErrorException);

    InstructionError instructionError = InstructionErrorCode.getErrorValue("OF_GENERIC_ERROR");
    try {
      fdeService.receive(fdeCreateContainerRequest, MockHttpHeaders.getHeaders());
      fail("Exception is expected in this flow.");
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorHeader(), instructionError.getErrorHeader());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertNull(e.getErrorResponse().getErrorKey());
    }
  }
}
