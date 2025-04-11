package com.walmart.move.nim.receiving.core.client.oms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.OMPSRJDataItem;
import com.walmart.move.nim.receiving.core.model.OMSPurchaseOrderResponse;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OmsRestApiClientTest {

  @Mock RetryableRestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @InjectMocks private OmsRestApiClient omsRestApiClient = new OmsRestApiClient();

  @BeforeClass
  public void setup() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(omsRestApiClient, "gson", new Gson());
  }

  @AfterMethod
  public void tearDown() {
    reset(retryableRestConnector, appConfig);
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
  public void testGetPODetailsFromOMS_success() throws IOException {

    doReturn("http://devcicweb3.wal-mart.com:62331").when(appConfig).getOmsBaseUrl();
    File resource = new ClassPathResource("oms_po_mock_response.json").getFile();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            new String(Files.readAllBytes(resource.toPath())), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    OMSPurchaseOrderResponse purchaseOrder = omsRestApiClient.getPODetailsFromOMS("4580154042");

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    OMPSRJDataItem dataItem = purchaseOrder.getOMPSRJ().getData().get(0);
    assertEquals(dataItem.getOmspo().getXrefponbr(), "4580154042");
  }

  @Test()
  public void testGetPODetailsFromGDM_InternalServerError() {

    doReturn("http://devcicweb3.wal-mart.com:62331").when(appConfig).getOmsBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    OMSPurchaseOrderResponse purchaseOrder = omsRestApiClient.getPODetailsFromOMS("4580154042");

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertNull(purchaseOrder);
  }
}
