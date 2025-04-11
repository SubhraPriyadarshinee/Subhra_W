package com.walmart.move.nim.receiving.sib.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.GdmDeliverySummary;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreDeliveryServiceTest {

  private Gson gson;
  private static final int FACILITY_NUMBER = 266;
  private static final String FACILITY_COUNTRY_CODE = "US";
  private static final String CORRELATION_ID = "correlation-id";
  private static final String PALLET_NUMBER = "00266010606400710306";

  @Mock private RestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @InjectMocks private StoreDeliveryService storeDeliveryService;

  @Captor ArgumentCaptor<String> uriCaptor;

  @Captor ArgumentCaptor<HttpMethod> gdmDeliverySearchHttpMethodCaptor;

  @Captor ArgumentCaptor<HttpEntity> gdmDeliverySearchHttpEntityCaptor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);

    gson = new Gson();
    TenantContext.setFacilityCountryCode(FACILITY_COUNTRY_CODE);
    TenantContext.setFacilityNum(FACILITY_NUMBER);
    TenantContext.setCorrelationId(CORRELATION_ID);

    ReflectionTestUtils.setField(storeDeliveryService, "gson", new Gson());
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(retryableRestConnector);
    Mockito.reset(appConfig);
  }

  @Test
  public void testSearchDelivery_success() {
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    GdmDeliverySummary gdmDeliverySummary1 = createGdmDeliverySummary();
    GdmDeliverySummary gdmDeliverySummary2 = createGdmDeliverySummary();
    gdmDeliverySummary2.setDeliveryNumber(123456789L);

    GdmDeliverySearchResponse gdmDeliverySearchResponse = new GdmDeliverySearchResponse();
    gdmDeliverySearchResponse.setDeliveries(
        Arrays.asList(gdmDeliverySummary1, gdmDeliverySummary2));
    String response = gson.toJson(gdmDeliverySearchResponse);
    ResponseEntity<String> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

    doReturn(responseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    GdmDeliverySearchResponse actualResponse =
        storeDeliveryService.searchDelivery(createGdmDeliveryRequest());

    assertNotNull(actualResponse);
    assertNotNull(actualResponse.getDeliveries());
    assertEquals(2, actualResponse.getDeliveries().size());
    List<GdmDeliverySummary> deliverySummaries = actualResponse.getDeliveries();
    assertEquals(Long.valueOf(550478600065364L), deliverySummaries.get(0).getDeliveryNumber());
    assertEquals(Long.valueOf(123456789L), deliverySummaries.get(1).getDeliveryNumber());

    verify(retryableRestConnector, times(1))
        .exchange(
            uriCaptor.capture(),
            gdmDeliverySearchHttpMethodCaptor.capture(),
            gdmDeliverySearchHttpEntityCaptor.capture(),
            any(Class.class));

    assertEquals(
        "https://dev.gdm.prod.us.walmart.net/api/deliveries/shipments", uriCaptor.getValue());

    assertEquals(
        PALLET_NUMBER,
        ((GdmDeliverySearchRequest) gdmDeliverySearchHttpEntityCaptor.getValue().getBody())
            .getPalletNumber());

    HttpHeaders httpHeaders = gdmDeliverySearchHttpEntityCaptor.getValue().getHeaders();
    assertEquals(
        Arrays.asList(String.valueOf(FACILITY_NUMBER)),
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        Arrays.asList(FACILITY_COUNTRY_CODE),
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        Arrays.asList(CORRELATION_ID),
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertEquals(
        Arrays.asList("application/vnd.PalletPackDeliveryShipmentSearchResponse2+json"),
        httpHeaders.get(HttpHeaders.ACCEPT));
  }

  @Test
  public void testSearchDelivery_generalRestClientResponseException() {
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "Some error.",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "",
            null,
            "".getBytes(),
            StandardCharsets.UTF_8);
    doThrow(restClientResponseException)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      storeDeliveryService.searchDelivery(createGdmDeliveryRequest());
      fail("expected exception was not occurred.");
    } catch (Exception ex) {
      assertTrue(ex instanceof ReceivingInternalException);
      assertEquals(
          ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE,
          ((ReceivingInternalException) ex).getErrorCode());
      assertEquals(
          ReceivingException.GDM_SERVICE_DOWN, ((ReceivingInternalException) ex).getDescription());
    }
  }

  @Test
  public void testSearchDelivery_NotFoundRestClientResponseException() {
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "Some error.",
            HttpStatus.NO_CONTENT.value(),
            "",
            null,
            "".getBytes(),
            StandardCharsets.UTF_8);
    doThrow(restClientResponseException)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      storeDeliveryService.searchDelivery(createGdmDeliveryRequest());
      fail("expected exception was not occurred.");
    } catch (Exception ex) {
      assertTrue(ex instanceof ReceivingDataNotFoundException);
      assertEquals(
          ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE,
          ((ReceivingDataNotFoundException) ex).getErrorCode());
      assertEquals(
          ReceivingException.DELIVERY_NOT_FOUND,
          ((ReceivingDataNotFoundException) ex).getDescription());
    }
  }

  @Test
  public void testSearchDelivery_resourceAccessException() {
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    doThrow(ResourceAccessException.class)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      storeDeliveryService.searchDelivery(createGdmDeliveryRequest());
      fail("expected exception was not occurred.");
    } catch (Exception ex) {
      assertTrue(ex instanceof ReceivingInternalException);
      assertEquals(
          ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE,
          ((ReceivingInternalException) ex).getErrorCode());
      assertEquals(
          ReceivingException.GDM_SERVICE_DOWN, ((ReceivingInternalException) ex).getDescription());
    }
  }

  private GdmDeliverySummary createGdmDeliverySummary() {
    GdmDeliverySummary gdmDeliverySummary = new GdmDeliverySummary();
    gdmDeliverySummary.setDeliveryNumber(550478600065364L);
    return gdmDeliverySummary;
  }

  private GdmDeliverySearchRequest createGdmDeliveryRequest() {
    GdmDeliverySearchRequest request = new GdmDeliverySearchRequest();
    request.setPalletNumber(PALLET_NUMBER);
    return request;
  }
}
