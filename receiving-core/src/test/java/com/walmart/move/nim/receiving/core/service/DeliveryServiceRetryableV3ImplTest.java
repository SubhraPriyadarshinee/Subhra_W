package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.helper.TestUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeliveryServiceRetryableV3ImplTest extends ReceivingTestBase {

  @InjectMocks private DeliveryServiceRetryableV3Impl deliveryServiceRetryableV3;
  @Mock private RestConnector restConnector;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private GdmError gdmError;
  private ArgumentCaptor<String> gdmUrlCaptor;
  private HttpHeaders httpHeaders;
  private static final String GDM_BASE_URL = "https://dev.gdm.prod.us.walmart.net";

  @BeforeClass
  private void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @BeforeMethod
  private void init() {
    httpHeaders = MockHttpHeaders.getHeaders();
    gdmUrlCaptor = ArgumentCaptor.forClass(String.class);
    when(appConfig.getGdmBaseUrl()).thenReturn(GDM_BASE_URL);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.IS_IQS_ITEM_SCAN_ACTIVE_CHANNELS_ENABLED);
    reset(restConnector);
  }

  @Test
  public void testFindDeliveryDocument() throws Exception {
    try {
      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(restConnector)
          .exchange(
              gdmUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document =
          deliveryServiceRetryableV3.findDeliveryDocument(12345L, "01245557855555", httpHeaders);
      assertNotNull(document);
      Map<String, String> queryMap = TestUtils.getQueryMap(gdmUrlCaptor.getValue());
      assertEquals(queryMap.get(ReceivingConstants.QUERY_GTIN), "01245557855555");
      assertEquals(queryMap.get(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS), "false");
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testFindDeliveryDocument_includeActiveChannelMethods() throws Exception {
    try {
      doReturn(Boolean.TRUE)
          .when(tenantSpecificConfigReader)
          .isFeatureFlagEnabled(ReceivingConstants.IS_IQS_ITEM_SCAN_ACTIVE_CHANNELS_ENABLED);

      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(restConnector)
          .exchange(
              gdmUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document =
          deliveryServiceRetryableV3.findDeliveryDocument(12345L, "01245557855555", httpHeaders);
      assertNotNull(document);

      Map<String, String> queryMap = TestUtils.getQueryMap(gdmUrlCaptor.getValue());
      assertEquals(queryMap.get(ReceivingConstants.QUERY_GTIN), "01245557855555");
      assertEquals(queryMap.get(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS), "true");
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testFindDeliveryDocument_EmptyResponseScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document =
          deliveryServiceRetryableV3.findDeliveryDocument(12345L, "01245557855555", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
  }

  @Test
  public void testFindDeliveryDocument_ExceptionScenarioGDMDown() throws ReceivingException {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
    try {
      doThrow(new ResourceAccessException("IO Error"))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document =
          deliveryServiceRetryableV3.findDeliveryDocument(12345L, "01245557855555", httpHeaders);
    } catch (GDMServiceUnavailableException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }
  }

  @Test
  public void testFindDeliveryDocument_ExceptionScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      doThrow(
              new RestClientResponseException(
                  "Error Fetching URL",
                  HttpStatus.INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  StandardCharsets.UTF_8))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      deliveryServiceRetryableV3.findDeliveryDocument(12345L, "01245557855555", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }
  }
}
