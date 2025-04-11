package com.walmart.move.nim.receiving.fixture.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.model.FixturesItemAttribute;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemREPServiceClientTest {
  @Mock private FixtureManagedConfig fixtureManagedConfig;

  @Mock private RetryableRestConnector retryableRestConnector;

  private final Gson gson = new Gson();

  @InjectMocks private ItemREPServiceClient itemREPServiceClient;

  private HttpHeaders httpHeaders;
  private static final String facilityNum = "10805";
  private static final String countryCode = "US";

  @BeforeMethod
  public void createItemRepRestApiClient() {

    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(itemREPServiceClient, "gson", gson);
  }

  @Test
  public void testGetItemDetailsOfItemNumbersFromREP() throws IOException {

    File resource =
        new File("../../receiving-test/src/main/resources/json/itemRepResponse.json")
            .getCanonicalFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ResponseEntity<FixturesItemAttribute[]> mockResponseEntity =
        new ResponseEntity<FixturesItemAttribute[]>(
            gson.fromJson(mockResponse, FixturesItemAttribute[].class), HttpStatus.OK);
    Set<Long> itemNumbers = new HashSet<>();
    itemNumbers.add(100600004L);
    when(fixtureManagedConfig.getItemRepBatchSize()).thenReturn(3);
    when(fixtureManagedConfig.getItemRepBaseUrl())
        .thenReturn("http://localhost:8080/v1/api/articles/details");
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockResponseEntity);
    Map<Integer, FixturesItemAttribute> response =
        itemREPServiceClient.getItemDetailsOfItemNumbersFromREP(itemNumbers);

    assertNotNull(response);

    verify(fixtureManagedConfig, times(1)).getItemRepBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
