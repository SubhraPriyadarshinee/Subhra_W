package com.walmart.move.nim.receiving.core.client.fit;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.testng.Assert.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncFitRestApiClientTest {

  @Mock private FitRestApiClient fitRestApiClient;

  @InjectMocks private AsyncFitRestApiClient asyncFitRestApiClient = new AsyncFitRestApiClient();

  @BeforeClass
  public void beforeClass() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testFindProblemCountByDelivery()
      throws InterruptedException, ExecutionException, FitRestApiClientException {

    ProblemCountByDeliveryResponse mockRespose = new ProblemCountByDeliveryResponse();

    doReturn(Optional.of(mockRespose))
        .when(fitRestApiClient)
        .findProblemCountByDelivery(anyLong(), any(Map.class));

    Map<String, Object> mockHeaders = new HashMap<>();

    CompletableFuture<Optional<ProblemCountByDeliveryResponse>> responseFeature =
        asyncFitRestApiClient.findProblemCountByDelivery(12345l, mockHeaders);

    assertTrue(responseFeature.get().isPresent());
    assertSame(mockRespose, responseFeature.get().get());
  }

  @Test(expectedExceptions = CompletionException.class)
  public void testFindProblemCountByDeliveryException()
      throws InterruptedException, ExecutionException, FitRestApiClientException {

    doThrow(new FitRestApiClientException("Mock Error", HttpStatus.BAD_GATEWAY))
        .when(fitRestApiClient)
        .findProblemCountByDelivery(anyLong(), any(Map.class));

    Map<String, Object> mockHeaders = new HashMap<>();

    CompletableFuture<Optional<ProblemCountByDeliveryResponse>> responseFeature =
        asyncFitRestApiClient.findProblemCountByDelivery(12345l, mockHeaders);
  }
}
