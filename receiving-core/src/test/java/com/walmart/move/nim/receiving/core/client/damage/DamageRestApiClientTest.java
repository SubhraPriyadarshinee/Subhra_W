package com.walmart.move.nim.receiving.core.client.damage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

public class DamageRestApiClientTest {
  public static final String FIXIT_DAMAGE_BASE_URL_WITH_V1 =
      "https://fixit-platform-application.dev.walmart.net/api/v1";

  @Mock private AppConfig appConfig;
  @Mock private RestConnector restConnector;
  @Mock private TenantSpecificConfigReader configUtils;
  @InjectMocks private DamageRestApiClient damageRestApiClient;

  @BeforeClass
  public void createdamageRestApiClient() throws Exception {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(damageRestApiClient, "gson", new Gson());
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
  public void testFindDamagesByDelivery() throws DamageRestApiClientException, IOException {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    File resource =
        new ClassPathResource("damage_getDamageCountByDelivery_mock_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();

    List<DamageDeliveryInfo> damagesByDelivery =
        damageRestApiClient.findDamagesByDelivery(31119003l, mockHeaders).get();

    assertNotNull(damagesByDelivery);
    assertEquals(damagesByDelivery.size(), 3);
    assertEquals(damagesByDelivery.get(0).getDeliveryNumber(), "9967271326");

    verify(restConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testFindDamagesByDelivery_enableFixit()
      throws DamageRestApiClientException, IOException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    File resource =
        new ClassPathResource("damage_getDamageCountByDelivery_mock_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    doReturn("https://fixit-platform-application.dev.walmart.net")
        .when(appConfig)
        .getFixitPlatformBaseUrl();

    List<DamageDeliveryInfo> damagesByDelivery =
        damageRestApiClient.findDamagesByDelivery(31119003l, mockHeaders).get();

    assertNotNull(damagesByDelivery);
    assertEquals(damagesByDelivery.size(), 3);
    assertEquals(damagesByDelivery.get(0).getDeliveryNumber(), "9967271326");

    ArgumentCaptor<String> captureURL = ArgumentCaptor.forClass(String.class);
    verify(restConnector, atLeastOnce())
        .exchange(
            captureURL.capture(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    final String fixitDamageUrl = captureURL.getValue();
    assertNotNull(fixitDamageUrl);
    assertTrue(fixitDamageUrl.contains(FIXIT_DAMAGE_BASE_URL_WITH_V1));
  }

  @Test
  public void testfindDamagesByDeliveryResourceNotFound() {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    doThrow(new HttpServerErrorException(HttpStatus.NOT_FOUND))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    try {
      List<DamageDeliveryInfo> damagesByDelivery =
          damageRestApiClient.findDamagesByDelivery(19495017l, mockHeaders).get();
    } catch (DamageRestApiClientException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.NOT_FOUND);
    }

    verify(restConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testfindDamagesByDeliveryInternalError() {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    try {
      List<DamageDeliveryInfo> damagesByDelivery =
          damageRestApiClient.findDamagesByDelivery(19495017l, mockHeaders).get();
    } catch (DamageRestApiClientException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(restConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testfindDamagesByDeliveryNoData() throws DamageRestApiClientException {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.FIXIT_ENABLED, false);

    doReturn(new ResponseEntity<String>(HttpStatus.NO_CONTENT))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();
    Optional<List<DamageDeliveryInfo>> damagesByDelivery =
        damageRestApiClient.findDamagesByDelivery(19495017l, mockHeaders);

    assertFalse(damagesByDelivery.isPresent());

    verify(restConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
