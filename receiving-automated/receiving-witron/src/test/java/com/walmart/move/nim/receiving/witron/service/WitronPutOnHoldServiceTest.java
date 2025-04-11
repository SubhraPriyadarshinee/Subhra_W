package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.ContainerErrorResponse;
import com.walmart.move.nim.receiving.core.model.PalletsHoldRequest;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WitronPutOnHoldServiceTest extends ReceivingTestBase {
  @InjectMocks private WitronPutOnHoldService witronPutOnHoldHandler;
  @Mock private WitronContainerService witronContainerService;

  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private PalletsHoldRequest palletsHoldRequest = new PalletsHoldRequest();
  private String trackingId1 = "030181107692957111";
  private String trackingId2 = "030181107692957112";
  private String trackingId3 = "030181107692957113";
  private String trackingId4 = "030181107692957114";
  private String trackingId5 = "030181107692957115";

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    List<String> trackingIds = new ArrayList<String>();
    trackingIds.add(trackingId1);
    trackingIds.add(trackingId2);
    trackingIds.add(trackingId3);
    trackingIds.add(trackingId4);
    trackingIds.add(trackingId5);
    palletsHoldRequest.setTrackingIds(trackingIds);
  }

  @AfterMethod
  public void tearDown() {
    reset(witronContainerService);
  }

  @Test
  public void testPalletsOnHold() throws ReceivingException {
    doNothing().when(witronContainerService).palletOnHold(trackingId1, httpHeaders);

    doThrow(
            new ReceivingException(
                ReceivingException.MATCHING_CONTAINER_NOT_FOUND,
                HttpStatus.BAD_REQUEST,
                ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE))
        .when(witronContainerService)
        .palletOnHold(trackingId2, httpHeaders);

    doNothing().when(witronContainerService).palletOnHold(trackingId3, httpHeaders);

    doThrow(
            new ReceivingException(
                ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_CODE))
        .when(witronContainerService)
        .palletOnHold(trackingId4, httpHeaders);

    doThrow(
            new ReceivingException(
                ReceivingException.INVENTORY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.INVENTORY_ERROR_CODE))
        .when(witronContainerService)
        .palletOnHold(trackingId5, httpHeaders);

    List<ContainerErrorResponse> containerErrorResponse =
        witronPutOnHoldHandler.palletsOnHold(palletsHoldRequest, httpHeaders);

    assert (containerErrorResponse.size() == 3);

    assertEquals(containerErrorResponse.get(0).getTrackingId(), trackingId2);
    assertEquals(
        containerErrorResponse.get(0).getErrorCode(),
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    assertEquals(
        containerErrorResponse.get(0).getErrorMessage(),
        ReceivingException.MATCHING_CONTAINER_NOT_FOUND);

    assertEquals(containerErrorResponse.get(1).getTrackingId(), trackingId4);
    assertEquals(
        containerErrorResponse.get(1).getErrorCode(),
        ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_CODE);
    assertEquals(
        containerErrorResponse.get(1).getErrorMessage(),
        ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_MSG);

    assertEquals(containerErrorResponse.get(2).getTrackingId(), trackingId5);
    assertEquals(
        containerErrorResponse.get(2).getErrorCode(), ReceivingException.INVENTORY_ERROR_CODE);
    assertEquals(
        containerErrorResponse.get(2).getErrorMessage(), ReceivingException.INVENTORY_ERROR_MSG);
  }

  @Test
  public void testPalletsOffHold() throws ReceivingException {
    doNothing().when(witronContainerService).palletOffHold(trackingId1, httpHeaders);

    doThrow(
            new ReceivingException(
                ReceivingException.MATCHING_CONTAINER_NOT_FOUND,
                HttpStatus.BAD_REQUEST,
                ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE))
        .when(witronContainerService)
        .palletOffHold(trackingId2, httpHeaders);

    doNothing().when(witronContainerService).palletOffHold(trackingId3, httpHeaders);

    doThrow(
            new ReceivingException(
                ReceivingException.MATCHING_CONTAINER_NOT_FOUND,
                HttpStatus.BAD_REQUEST,
                ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE))
        .when(witronContainerService)
        .palletOffHold(trackingId4, httpHeaders);

    doThrow(
            new ReceivingException(
                ReceivingException.INVENTORY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.INVENTORY_ERROR_CODE))
        .when(witronContainerService)
        .palletOffHold(trackingId5, httpHeaders);

    List<ContainerErrorResponse> containerErrorResponse =
        witronPutOnHoldHandler.palletsOffHold(palletsHoldRequest, httpHeaders);

    assert (containerErrorResponse.size() == 3);

    assertEquals(containerErrorResponse.get(0).getTrackingId(), trackingId2);
    assertEquals(
        containerErrorResponse.get(0).getErrorCode(),
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    assertEquals(
        containerErrorResponse.get(0).getErrorMessage(),
        ReceivingException.MATCHING_CONTAINER_NOT_FOUND);

    assertEquals(containerErrorResponse.get(1).getTrackingId(), trackingId4);
    assertEquals(
        containerErrorResponse.get(1).getErrorCode(),
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    assertEquals(
        containerErrorResponse.get(1).getErrorMessage(),
        ReceivingException.MATCHING_CONTAINER_NOT_FOUND);

    assertEquals(containerErrorResponse.get(2).getTrackingId(), trackingId5);
    assertEquals(
        containerErrorResponse.get(2).getErrorCode(), ReceivingException.INVENTORY_ERROR_CODE);
    assertEquals(
        containerErrorResponse.get(2).getErrorMessage(), ReceivingException.INVENTORY_ERROR_MSG);
  }
}
