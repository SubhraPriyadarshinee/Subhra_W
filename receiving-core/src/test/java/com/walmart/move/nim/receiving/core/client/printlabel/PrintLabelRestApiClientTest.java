package com.walmart.move.nim.receiving.core.client.printlabel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelServiceResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Junit class for PrintLabelRestApiClientTest
 *
 * @author vn50o7n
 */
public class PrintLabelRestApiClientTest {

  @Mock private RestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @InjectMocks
  private PrintLabelRestApiClient printLabelRestApiClient = new PrintLabelRestApiClient();

  @BeforeClass
  public void setup() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(printLabelRestApiClient, "gson", new Gson());
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
  public void testGetPrintLabel() throws Exception {

    File resource = new ClassPathResource("printlable_get_response_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://ng-label-printing-stg-int.prod.us.walmart.net")
        .when(appConfig)
        .getLpaasBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> headers = getMockHeader();

    final PrintLabelServiceResponse printLabelServiceResponse =
        printLabelRestApiClient.getPrintLabel("D32612000020115184", headers);

    assertNotNull(printLabelServiceResponse);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
