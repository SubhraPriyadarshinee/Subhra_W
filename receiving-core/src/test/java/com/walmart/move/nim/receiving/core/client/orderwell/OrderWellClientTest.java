package com.walmart.move.nim.receiving.core.client.orderwell;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.OrderWellZoneRequest;
import com.walmart.move.nim.receiving.core.common.OrderWellZoneResponse;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.nio.charset.Charset;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OrderWellClientTest {
  @Mock private AppConfig appConfig;
  @Mock private RestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private OrderWellClient orderWellClient;
  private Gson gson = new Gson();

  @BeforeMethod
  public void createorderWellRestApiClient() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(orderWellClient, "gson", gson);
  }

  @AfterMethod
  public void afterMethod() {
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void getStoreMFCDistributionforStoreNbrandPOwithOkreponse() throws Exception {
    String mockRequest =
        "{\n"
            + "  \"data\": [\n"
            + "      {\n"
            + "          \"destNbr\": 5505,\n"
            + "          \"poNbr\": \"12345678\",\n"
            + "          \"poType\": 33,\n"
            + "          \"whpkOrderQty\": 10,\n"
            + "          \"sourceNbr\": 7001,\n"
            + "          \"wmtItemNbr\": 123456789\n"
            + "      }\n"
            + "  ]\n"
            + "}";
    String mockResponse =
        "{\n"
            + "  \"data\": [\n"
            + "      {\n"
            + "          \"mfcDistribution\": {\n"
            + "              \"orderDate\": 1641427200000,\n"
            + "              \"sourceNbr\": 7001,\n"
            + "              \"wmtItemNbr\": 118307079,\n"
            + "              \"destNbr\": 5505,\n"
            + "              \"whpkOrderQty\": 10,\n"
            + "              \"zone\": \"MFC\",\n"
            + "              \"orderTrackingNbr\": \"8ef1ff39-87be-4654-1206-000000000005\"\n"
            + "          },\n"
            + "          \"storeDistribution\": {\n"
            + "              \"orderDate\": 1641427200000,\n"
            + "              \"sourceNbr\": 7001,\n"
            + "              \"wmtItemNbr\": 118307079,\n"
            + "              \"destNbr\": 5505,\n"
            + "              \"whpkOrderQty\": 0,\n"
            + "              \"zone\": \"ST\",\n"
            + "              \"orderTrackingNbr\": \"8ef1ff39-87be-4654-1206-000000000005\"\n"
            + "          }\n"
            + "      }\n"
            + "  ]\n"
            + "}";
    doReturn("https://ow-mfc-distro-svc-rdcstage.replflow.walmart.com/")
        .when(appConfig)
        .getOrderWellBaseUrl();

    doReturn("mockservice").when(appConfig).getOrderWellServiceEnv();
    doReturn("mockenv").when(appConfig).getOrderWellServiceEnv();
    doReturn("mockconsumerid").when(appConfig).getOrderWellConsumerId();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    OrderWellZoneRequest orderWellZoneRequest =
        gson.fromJson(mockRequest, OrderWellZoneRequest.class);
    OrderWellZoneResponse orderWellZoneResponse =
        orderWellClient.getStoreMFCDistributionforStoreNbrandPO(orderWellZoneRequest);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertNotNull(orderWellZoneResponse);
  }

  @Test
  public void getStoreMFCDistributionforStoreNbrandPOwithNullreponse() throws Exception {
    String mockRequest =
        "{\n"
            + "  \"data\": [\n"
            + "      {\n"
            + "          \"destNbr\": 5505,\n"
            + "          \"poNbr\": \"12345678\",\n"
            + "          \"poType\": 33,\n"
            + "          \"whpkOrderQty\": 10,\n"
            + "          \"sourceNbr\": 7001,\n"
            + "          \"wmtItemNbr\": 123456789\n"
            + "      }\n"
            + "  ]\n"
            + "}";
    String mockResponse = "";
    doReturn("https://ow-mfc-distro-svc-rdcstage.replflow.walmart.com/")
        .when(appConfig)
        .getOrderWellBaseUrl();

    doReturn("mockservice").when(appConfig).getOrderWellServiceEnv();
    doReturn("mockenv").when(appConfig).getOrderWellServiceEnv();
    doReturn("mockconsumerid").when(appConfig).getOrderWellConsumerId();

    final RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "503 Service Unavailable: \"no healthy upstream\"",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "",
            null,
            "".getBytes(),
            Charset.forName("UTF-8"));

    doThrow(restClientResponseException)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    OrderWellZoneRequest orderWellZoneRequest =
        gson.fromJson(mockRequest, OrderWellZoneRequest.class);
    OrderWellZoneResponse orderWellZoneResponse =
        orderWellClient.getStoreMFCDistributionforStoreNbrandPO(orderWellZoneRequest);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertNull(orderWellZoneResponse);
  }
}
