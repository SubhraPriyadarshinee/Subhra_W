package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ContainerAdjustmentValidatorTest {
  @Mock private ContainerRepository containerRepository;
  @Mock private DeliveryService deliveryService;
  @InjectMocks private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Mock private ContainerPersisterService containerPersisterService;

  @BeforeMethod
  public void createContainerAdjustmentValidator() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @SneakyThrows
  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = CONTAINER_NOT_FOUND_ERROR_MSG)
  public void testGetValidContainer_NullContainer() {
    doReturn(null)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());
    containerAdjustmentValidator.getValidContainer("12345");
  }

  @SneakyThrows
  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = CONTAINER_ALREADY_CANCELED_ERROR_MSG)
  public void testGetValidContainer_ExceptionStatusBackout() {
    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(22223333l);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    doReturn(mockContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());
    containerAdjustmentValidator.getValidContainer("12345");
  }

  @SneakyThrows
  @Test()
  public void testGetValidContainer_happyPathpath_returnsValidContainer() {
    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(22223333l);
    ContainerItem containerItem = new ContainerItem();
    mockContainer.setContainerItems(Collections.singletonList(containerItem));

    doReturn(mockContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());
    final Container validContainer = containerAdjustmentValidator.getValidContainer("12345");
    assertNotNull(validContainer);
  }

  @Test
  private void testValidateContainerAdjustmentForParentContainer_ReturnsNullResponse() {
    Container container = getMockContainer();
    CancelContainerResponse cancelContainerResponse =
        containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(container);
    assertNull(cancelContainerResponse);
  }

  @Test
  private void testValidateContainerAdjustmentForParentContainer_ReturnsErrorResponse_Backout() {
    Container container = getMockContainer();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    CancelContainerResponse cancelContainerResponse =
        containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(container);
    assertNotNull(cancelContainerResponse);
  }

  @Test
  private void
      testValidateContainerAdjustmentForParentContainer_ReturnsErrorResponse_PutawayAlreadyCompleted() {
    Container container = getMockContainer();
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    CancelContainerResponse cancelContainerResponse =
        containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(container);
    assertNotNull(cancelContainerResponse);
  }

  @Test
  private void
      testValidateContainerAdjustmentForParentContainer_ReturnsErrorResponse_EmptyChildContents() {
    Container container = new Container();
    container.setTrackingId("12345");
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    CancelContainerResponse cancelContainerResponse =
        containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(container);
    assertNotNull(cancelContainerResponse);
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setTrackingId("12345");
    container.setContainerStatus("COMPLETE");
    ContainerItem containerItem = new ContainerItem();
    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }
}
