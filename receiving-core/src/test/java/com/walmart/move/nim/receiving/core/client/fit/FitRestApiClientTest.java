package com.walmart.move.nim.receiving.core.client.fit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FitRestApiClientTest {

  public static final String FIT_PROD_BASE_URL_WITH_V1 =
      "https://api.fit.prod.us.walmart.net/api/v1";

  public static final String FIXIT_BASE_URL_WITH_V1 =
      "https://fixit-platform-application.dev.walmart.net/api/v1";

  @Mock private AppConfig appConfig;
  @Mock private RestConnector restConnector;
  @Mock private TenantSpecificConfigReader configUtils;

  @InjectMocks private FitRestApiClient fitRestApiClient;

  @BeforeClass
  public void createFitRestApiClient() throws Exception {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(fitRestApiClient, "gson", new Gson());
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
  }

  private Map<String, Object> getMockHeader() {

    Map<String, Object> mockHeaders = new HashMap<>();
    mockHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "WMT-UserId");
    mockHeaders.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    mockHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32612");
    mockHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "a1-b2-c3-d4");

    return mockHeaders;
  }

  @Test
  public void testFindProblemCountByDelivery() throws FitRestApiClientException, IOException {

    File resource =
        new ClassPathResource("fit_getProblemCntByDelivery_mock_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    doReturn("https://api.fit.prod.us.walmart.net").when(appConfig).getFitBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    ProblemCountByDeliveryResponse problemCountByDelivery =
        fitRestApiClient.findProblemCountByDelivery(19495017l, mockHeaders).get();

    assertNotNull(problemCountByDelivery);
    assertEquals(problemCountByDelivery.getDeliveryNumber(), "9967271326");
    assertEquals(problemCountByDelivery.getPurchaseOrders().size(), 4);
    assertEquals(problemCountByDelivery.getPurchaseOrders().get(0).getPoLines().size(), 1);

    ArgumentCaptor<String> captureURL = ArgumentCaptor.forClass(String.class);
    verify(restConnector, atLeastOnce())
        .exchange(
            captureURL.capture(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    final String fitUrl = captureURL.getValue();
    assertNotNull(fitUrl);
    assertTrue(fitUrl.contains(FIT_PROD_BASE_URL_WITH_V1));
  }

  @Test
  public void testFindProblemCountByDelivery_enableFixit()
      throws FitRestApiClientException, IOException {
    File resource =
        new ClassPathResource("fit_getProblemCntByDelivery_mock_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    doReturn("https://fixit-platform-application.dev.walmart.net")
        .when(appConfig)
        .getFixitPlatformBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    ProblemCountByDeliveryResponse problemCountByDelivery =
        fitRestApiClient.findProblemCountByDelivery(19495017l, mockHeaders).get();

    assertNotNull(problemCountByDelivery);
    assertEquals(problemCountByDelivery.getDeliveryNumber(), "9967271326");
    assertEquals(problemCountByDelivery.getPurchaseOrders().size(), 4);
    assertEquals(problemCountByDelivery.getPurchaseOrders().get(0).getPoLines().size(), 1);

    ArgumentCaptor<String> captureURL = ArgumentCaptor.forClass(String.class);
    verify(restConnector, atLeastOnce())
        .exchange(
            captureURL.capture(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    final String fixitUrl = captureURL.getValue();
    assertNotNull(fixitUrl);
    assertTrue(fixitUrl.contains(FIXIT_BASE_URL_WITH_V1));
  }

  @Test
  public void testFindProblemCountByDeliveryNotFound() throws FitRestApiClientException {

    doThrow(new HttpServerErrorException(HttpStatus.NOT_FOUND))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    Map<String, Object> mockHeaders = getMockHeader();
    Optional<ProblemCountByDeliveryResponse> problemCountByDeliveryOptional =
        fitRestApiClient.findProblemCountByDelivery(19495017l, mockHeaders);

    assertFalse(problemCountByDeliveryOptional.isPresent());

    verify(restConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testFindProblemCountByDeliveryInternalError() {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    try {
      ProblemCountByDeliveryResponse problemCountByDelivery =
          fitRestApiClient.findProblemCountByDelivery(19495017l, mockHeaders).get();
    } catch (FitRestApiClientException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(restConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
