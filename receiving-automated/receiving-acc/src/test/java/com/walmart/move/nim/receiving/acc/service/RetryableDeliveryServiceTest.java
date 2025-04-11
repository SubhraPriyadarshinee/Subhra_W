package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RetryableDeliveryServiceTest extends ReceivingTestBase {
  @InjectMocks private RetryableDeliveryService retryableDeliveryService;
  @Mock private AppConfig appConfig;
  @Mock private RestConnector restConnector;
  @Mock private RestConnector simpleRestConnector;
  private HttpHeaders httpHeaders;
  private DeliveryUpdateMessage deliveryUpdateMessage;
  private String deliveryDetailsJson;
  private Long deliveryNumber;

  @BeforeClass
  private void setup() {
    MockitoAnnotations.initMocks(this);
    httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "6561");
    deliveryNumber = 12345L;
    deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("12345")
            .countryCode("US")
            .siteNumber("6051")
            .deliveryStatus("ARV")
            .url("https://delivery.test")
            .build();
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @AfterMethod
  public void resetMocks() {
    reset(simpleRestConnector, restConnector);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Method not implemented.")
  public void testFindDeliveryDocument() throws ReceivingException {
    retryableDeliveryService.findDeliveryDocument(1L, "00029057358162", httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Method not implemented.")
  public void testGetGDMData() throws ReceivingException {
    retryableDeliveryService.getGDMData(deliveryUpdateMessage);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "No delivery found")
  public void testGetDeliveryDetails_whenRestConnectorThrowsRestClientResponseException()
      throws ReceivingException {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(restConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    retryableDeliveryService.getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.")
  public void testGetDeliveryDetails_whenRestConnectorThrowsResourceAccessException()
      throws ReceivingException {
    doThrow(new ResourceAccessException("IO Error."))
        .when(restConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    retryableDeliveryService.getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "No delivery found*")
  public void testGetDeliveryDetails_whenRestConnectorReturnsNull() throws ReceivingException {
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    retryableDeliveryService.getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "No delivery found")
  public void testGetDeliveryDetails_whenRestConnectorReturnsEmpty() throws ReceivingException {
    doReturn(null)
        .when(restConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    retryableDeliveryService.getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
  }

  @Test
  public void testGetDeliveryDetails_whenRestConnectorReturnsDeliveryDocumentInResponse()
      throws ReceivingException {
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/GDMDeliveryDocument.json")
              .getCanonicalPath();
      deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    doReturn(new ResponseEntity<>(deliveryDetailsJson, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    DeliveryDetails deliveryDetails =
        retryableDeliveryService.getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    assertNotNull(deliveryDetails);
    assertEquals(deliveryDetails.getDeliveryNumber(), 891100);
  }

  @Test
  public void testFindDeliveryDocumentforPoCon_EmptyResponseScenario() {
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(simpleRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      retryableDeliveryService.getPOInfoFromDelivery(
          1l, "9888888843", MockHttpHeaders.getHeaders());
      fail();
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
      retryableDeliveryService.getPOInfoFromDelivery(1l, "56734837", MockHttpHeaders.getHeaders());
      fail();
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
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
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
      retryableDeliveryService.getPOInfoFromDelivery(1l, "56734837", MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      AssertJUnit.assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      retryableDeliveryService.findDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      AssertJUnit.assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      AssertJUnit.assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }
}
