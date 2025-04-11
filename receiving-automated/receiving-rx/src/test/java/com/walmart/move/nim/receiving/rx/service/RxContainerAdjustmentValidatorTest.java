package com.walmart.move.nim.receiving.rx.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxContainerAdjustmentValidatorTest {

  @Mock private ContainerRepository containerRepository;
  @Mock private DeliveryService deliveryService;

  @InjectMocks private RxContainerAdjustmentValidator rxContainerAdjustmentValidator;

  @BeforeMethod
  public void createRxContainerAdjustmentValidator() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test_validateContainerForAdjustment_backout_status() throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.OPN.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(
            mockContainer, MockRxHttpHeaders.getHeaders());

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_empty_container_items()
      throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.OPN.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(
            mockContainer, MockRxHttpHeaders.getHeaders());

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_unpublished_container()
      throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.OPN.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);
    mockContainer.setParentTrackingId("MOCK_PARENT_TRACKING_ID");
    List<ContainerItem> asList = new ArrayList<>();
    asList.add(new ContainerItem());
    mockContainer.setContainerItems(asList);

    Container parentContainer = new Container();
    parentContainer.setDeliveryNumber(12345l);
    doReturn(parentContainer).when(containerRepository).findByTrackingId("MOCK_PARENT_TRACKING_ID");

    CancelContainerResponse validateContainerForAdjustmentResponse =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(
            mockContainer, MockRxHttpHeaders.getHeaders());

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CONTAINER_ON_UNFINISHED_PALLET_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CONTAINER_ON_UNFINISHED_PALLET_ERROR_MSG);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_delivery_fnl_status() throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.FNL.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(
            mockContainer, MockRxHttpHeaders.getHeaders());

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_MESSAGE);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_delivery_pndfnl_status()
      throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.PNDFNL.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(
            mockContainer, MockRxHttpHeaders.getHeaders());

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_MESSAGE);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_gdm_error() throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.FNL.name());

    doThrow(new ReceivingException("MOCK_ERROR_MESSAGE"))
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(
            mockContainer, MockRxHttpHeaders.getHeaders());

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.GDM_SERVICE_DOWN);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }
}
