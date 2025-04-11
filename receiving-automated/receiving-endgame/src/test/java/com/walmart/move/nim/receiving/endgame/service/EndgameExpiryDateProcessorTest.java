package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.Mockito.*;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.ExpiryDateUpdatePublisherData;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndgameExpiryDateProcessorTest extends ReceivingTestBase {
  @Mock private EndgameContainerService endgameContainerService;
  @Mock private EndGameSlottingService endGameSlottingService;

  @InjectMocks private EndgameExpiryDateProcessor endgameExpiryDateProcessor;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(endGameSlottingService);
    reset(endgameContainerService);
  }

  @Test
  public void testListen() throws ReceivingException {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        MockMessageData.getMockExpiryDateUpdatePublisherData();
    when(endgameContainerService.updateRotateDate(anyLong(), any(UpdateAttributesData.class)))
        .thenReturn(expiryDateUpdatePublisherData);
    doNothing()
        .when(endgameContainerService)
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    endgameExpiryDateProcessor.processEvent(updateAttributesData);
    verify(endgameContainerService, times(1))
        .updateRotateDate(anyLong(), any(UpdateAttributesData.class));
    verify(endgameContainerService, times(1))
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    verify(endGameSlottingService, times(1))
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
  }

  @Test
  public void testListen_EmptyMessage() throws ReceivingException {
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        MockMessageData.getMockExpiryDateUpdatePublisherData();
    when(endgameContainerService.updateRotateDate(anyLong(), any(UpdateAttributesData.class)))
        .thenReturn(expiryDateUpdatePublisherData);
    doNothing()
        .when(endgameContainerService)
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    endgameExpiryDateProcessor.processEvent(null);
    verify(endgameContainerService, times(0))
        .updateRotateDate(anyLong(), any(UpdateAttributesData.class));
    verify(endgameContainerService, times(0))
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    verify(endGameSlottingService, times(0))
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid expiry date listener data.*")
  public void testListen_DeliveryNumberNotExists() throws ReceivingException {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockExpiryDateUpdateListenerDataWithoutDeliveryNumber();
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        MockMessageData.getMockExpiryDateUpdatePublisherData();
    endgameExpiryDateProcessor.processEvent(updateAttributesData);
    verify(endgameContainerService, times(0))
        .updateRotateDate(anyLong(), any(UpdateAttributesData.class));
    verify(endgameContainerService, times(0))
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    verify(endGameSlottingService, times(0))
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
  }

  @Test
  public void testListen_NoContainerToUpdate() throws ReceivingException {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    when(endgameContainerService.updateRotateDate(anyLong(), any(UpdateAttributesData.class)))
        .thenReturn(null);
    endgameExpiryDateProcessor.processEvent(updateAttributesData);
    verify(endgameContainerService, times(1))
        .updateRotateDate(anyLong(), any(UpdateAttributesData.class));
    verify(endgameContainerService, times(0))
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    verify(endGameSlottingService, times(1))
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid expiry date listener data.*")
  public void testListen_InvalidExpiryDateListenerData() throws ReceivingException {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockExpiryDateUpdateListenerDataWithoutTrackingId();
    endgameExpiryDateProcessor.processEvent(updateAttributesData);
    verify(endgameContainerService, times(0))
        .updateRotateDate(anyLong(), any(UpdateAttributesData.class));
    verify(endgameContainerService, times(0))
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    verify(endGameSlottingService, times(0))
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
  }

  @Test
  public void testListen_IfHawkeyeUpdateFails() throws ReceivingException {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        MockMessageData.getMockExpiryDateUpdatePublisherData();
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.ITEM_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants.ITEM_MDM_SERVICE_DOWN_ERROR_MSG,
                    "Something went wrong")))
        .when(endGameSlottingService)
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
    when(endgameContainerService.updateRotateDate(anyLong(), any(UpdateAttributesData.class)))
        .thenReturn(expiryDateUpdatePublisherData);
    doNothing()
        .when(endgameContainerService)
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    endgameExpiryDateProcessor.processEvent(updateAttributesData);
    verify(endgameContainerService, times(1))
        .updateRotateDate(anyLong(), any(UpdateAttributesData.class));
    verify(endgameContainerService, times(1))
        .publishContainerUpdate(any(ExpiryDateUpdatePublisherData.class));
    verify(endGameSlottingService, times(1))
        .updateDivertForItemAndDelivery(any(UpdateAttributesData.class));
  }
}
