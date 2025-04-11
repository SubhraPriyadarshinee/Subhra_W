package com.walmart.move.nim.receiving.core.client.iqs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.client.iqs.model.ItemBulkResponseDto;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncIqsRestApiClientTest {

  @Mock IqsRestApiClient iqsRestApiClient;

  @InjectMocks AsyncIqsRestApiClient asyncIqsRestApiClient = new AsyncIqsRestApiClient();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(iqsRestApiClient);
  }

  @SneakyThrows
  @Test
  public void getItemDetailsFromItemNumber_SuccessResponse() {

    Optional<ItemBulkResponseDto> itemBulkResponseDto = Optional.of(new ItemBulkResponseDto());
    doReturn(itemBulkResponseDto)
        .when(iqsRestApiClient)
        .getItemDetailsFromItemNumber(anySet(), anyString(), any(HttpHeaders.class));

    CompletableFuture<Optional<ItemBulkResponseDto>> completableFuture =
        asyncIqsRestApiClient.getItemDetailsFromItemNumber(
            new HashSet<>(Arrays.asList("123")), "32679", getHttpHeaders());
    ItemBulkResponseDto response =
        completableFuture.get().isPresent() ? completableFuture.get().get() : null;
    assertEquals(response, itemBulkResponseDto.get());
    verify(iqsRestApiClient, times(1))
        .getItemDetailsFromItemNumber(anySet(), anyString(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = CompletionException.class)
  public void testInventoryContainerDetails_Error() throws IqsRestApiClientException {

    doThrow(new IqsRestApiClientException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(iqsRestApiClient)
        .getItemDetailsFromItemNumber(anySet(), anyString(), any(HttpHeaders.class));

    asyncIqsRestApiClient.getItemDetailsFromItemNumber(
        new HashSet<>(Arrays.asList("123")), "32679", getHttpHeaders());

    verify(iqsRestApiClient, times(1))
        .getItemDetailsFromItemNumber(anySet(), anyString(), any(HttpHeaders.class));
  }

  public static HttpHeaders getHttpHeaders() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    requestHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.DEFAULT_USER);
    return requestHeaders;
  }
}
