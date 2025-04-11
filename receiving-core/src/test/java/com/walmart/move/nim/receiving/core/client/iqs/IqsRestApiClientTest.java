package com.walmart.move.nim.receiving.core.client.iqs;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.iqs.model.ItemBulkResponseDto;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class IqsRestApiClientTest {

  @Mock private AppConfig appConfig;
  @Mock private Gson gson;
  @Mock private RetryableRestConnector retryableRestConnector;
  @InjectMocks private IqsRestApiClient iqsRestApiClient;
  private String iqsBaseUrl = "http://quiqs.walmart.com";

  @BeforeClass
  public void createIqsRestApiClient() throws Exception {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(iqsRestApiClient, "gson", new Gson());
  }

  @SneakyThrows
  @Test
  public void testGetItemDetailsFromItemNumber_Success() {
    when(appConfig.getIqsBaseUrl()).thenReturn(iqsBaseUrl);
    File resources = new ClassPathResource("iqs_item_details_by_item_numbers.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resources.toPath()));
    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), same(String.class));
    HttpHeaders mockHeaders = addMockHeader();
    ItemBulkResponseDto response =
        iqsRestApiClient
            .getItemDetailsFromItemNumber(
                new HashSet<>(Arrays.asList("200184")), "32679", mockHeaders)
            .get();

    assertNotNull(response);
    assertEquals(response.getPayload().size(), 1);
    assertEquals(response.getPayload().get(0).getItemNbr(), "200184");

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetItemDetailsFromItemNumber_ResourceNotFound() {
    when(appConfig.getIqsBaseUrl()).thenReturn(iqsBaseUrl);
    doThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    HttpHeaders mockHeaders = addMockHeader();
    try {
      iqsRestApiClient.getItemDetailsFromItemNumber(
          new HashSet<>(Arrays.asList("1", "2")), "32679", mockHeaders);
    } catch (IqsRestApiClientException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.NOT_FOUND);
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetItemDetailsFromItemNumber_InternalServerError() {
    when(appConfig.getIqsBaseUrl()).thenReturn(iqsBaseUrl);
    doThrow(new ResourceAccessException("server error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    HttpHeaders mockHeaders = addMockHeader();
    try {
      iqsRestApiClient.getItemDetailsFromItemNumber(
          new HashSet<>(Arrays.asList("1", "2")), "12345", mockHeaders);
    } catch (IqsRestApiClientException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  private HttpHeaders addMockHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(APQ_ID, "ALL");
    headers.add(ACCEPT, ReceivingConstants.APPLICATION_JSON);
    headers.add(IQS_CONSUMER_ID_KEY, "as1234-da324-fsdf2");
    headers.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    headers.add(USER_ID_HEADER_KEY, "WMT-UserId");
    headers.add(IQS_CORRELATION_ID_KEY, "a1-s2-s3-s4");
    headers.add(IQS_COUNTRY_CODE, "US");
    return headers;
  }
}
