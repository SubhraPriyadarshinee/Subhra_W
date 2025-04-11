package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
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

public class AsyncInventoryServiceTest {

  @Mock InventoryService inventoryService;
  @InjectMocks private AsyncInventoryService asyncInventoryService = new AsyncInventoryService();

  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(inventoryService);
  }

  @Test
  public void testInventoryContainerDetails_Success()
      throws InterruptedException, ExecutionException, ReceivingException {
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setContainerStatus("COMPLETED");
    inventoryContainerDetails.setInventoryQty(5);

    doReturn(inventoryContainerDetails)
        .when(inventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));

    CompletableFuture<InventoryContainerDetails> completableFuture =
        asyncInventoryService.getInventoryContainerDetails("124", httpHeaders);
    InventoryContainerDetails inventoryContainerDetailsResponse = completableFuture.get();

    assertEquals(
        inventoryContainerDetailsResponse.getContainerStatus(),
        inventoryContainerDetails.getContainerStatus());

    verify(inventoryService, times(1))
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testInventoryContainerDetails_Error() throws ReceivingException {

    doThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(inventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));

    asyncInventoryService.getInventoryContainerDetails("124", httpHeaders);

    verify(inventoryService, times(1))
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
  }
}
