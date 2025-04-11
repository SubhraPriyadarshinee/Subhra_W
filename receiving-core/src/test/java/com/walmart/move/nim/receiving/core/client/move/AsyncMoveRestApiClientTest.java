package com.walmart.move.nim.receiving.core.client.move;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncMoveRestApiClientTest {
  @Mock MoveRestApiClient moveRestApiClient;
  @InjectMocks AsyncMoveRestApiClient asyncMoveRestApiClient = new AsyncMoveRestApiClient();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(moveRestApiClient);
  }

  @Test
  public void testMoveContainerDetails_Success()
      throws InterruptedException, ExecutionException, ReceivingException {
    List<String> moveContainerDetail = new ArrayList<>();
    moveContainerDetail.add("COMPLETED");

    doReturn(moveContainerDetail)
        .when(moveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));

    CompletableFuture<List<String>> completableFuture =
        asyncMoveRestApiClient.getMoveContainerDetails(
            "124", MoveRestApiClientTest.getMoveApiHttpHeaders());
    List<String> moveContainerDetailResponse = completableFuture.get();

    assertEquals(moveContainerDetailResponse, moveContainerDetail);

    verify(moveRestApiClient, times(1))
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testMoveContainerDetails_Error() throws ReceivingException {

    doThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(moveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));

    asyncMoveRestApiClient.getMoveContainerDetails(
        "124", MoveRestApiClientTest.getMoveApiHttpHeaders());

    verify(moveRestApiClient, times(1))
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
  }
}
