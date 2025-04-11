package com.walmart.move.nim.receiving.endgame.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.service.EndgameContainerService;
import com.walmart.move.nim.receiving.endgame.service.EndgameExpiryDateProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndgameItemUpdateProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SecureUpdateAttributeListenerTest extends ReceivingTestBase {

  @InjectMocks private EndgameContainerService endgameContainerService;

  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  private Gson gson;

  @Mock private EndgameItemUpdateProcessor endgameItemUpdateProcessor;
  @Mock private EndgameExpiryDateProcessor endgameExpiryDateProcessor;

  @BeforeClass
  public void setRootUp() {
    this.gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endgameContainerService, "gson", gson);
    ReflectionTestUtils.setField(
            endgameContainerService, "endgameItemUpdateProcessor", endgameItemUpdateProcessor);
    ReflectionTestUtils.setField(
            endgameContainerService, "endgameExpiryDateProcessor", endgameExpiryDateProcessor);
  }

  @AfterMethod
  public void resetMocks() {
    reset(endgameExpiryDateProcessor);
    reset(endgameItemUpdateProcessor);
  }

  @Test
  public void testProcessFTSUpdate() throws ReceivingException {
    doNothing().when(endgameExpiryDateProcessor).processEvent(any(MessageData.class));
    doNothing().when(endgameItemUpdateProcessor).processEvent(any(MessageData.class));

    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE);
    String itemupdate = this.gson.toJson(updateAttributesData);

    endgameContainerService.processContainerUpdates(itemupdate);

    verify(endgameItemUpdateProcessor, times(1)).processEvent(any(MessageData.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to process the FTS updation . payload = *.*")
  public void testProcessFTSUpdateError() throws ReceivingException {
    doNothing().when(endgameExpiryDateProcessor).processEvent(any(MessageData.class));

    doThrow(ReceivingException.class)
        .when(endgameItemUpdateProcessor)
        .processEvent(any(MessageData.class));

    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE);
    String itemupdate = this.gson.toJson(updateAttributesData);

    endgameContainerService.processContainerUpdates(itemupdate);

    verify(endgameExpiryDateProcessor, times(0)).processEvent(any(MessageData.class));
    verify(endgameItemUpdateProcessor, times(1)).processEvent(any(MessageData.class));
  }

  @Test
  public void testProcessExpiryUpdate() throws ReceivingException {
    doNothing().when(endgameExpiryDateProcessor).processEvent(any(MessageData.class));
    doNothing().when(endgameItemUpdateProcessor).processEvent(any(MessageData.class));

    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    String expiryPayload = this.gson.toJson(updateAttributesData);

    endgameContainerService.processContainerUpdates(expiryPayload);

    verify(endgameExpiryDateProcessor, times(1)).processEvent(any(MessageData.class));
    verify(endgameItemUpdateProcessor, times(0)).processEvent(any(MessageData.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to process the expiry . payload = *.*")
  public void testProcessExpiryUpdateError() throws ReceivingException {
    doNothing().when(endgameItemUpdateProcessor).processEvent(any(MessageData.class));

    doThrow(ReceivingException.class)
        .when(endgameExpiryDateProcessor)
        .processEvent(any(MessageData.class));

    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    String expiryPayload = this.gson.toJson(updateAttributesData);

    endgameContainerService.processContainerUpdates(expiryPayload);

    verify(endgameExpiryDateProcessor, times(1)).processEvent(any(MessageData.class));
    verify(endgameItemUpdateProcessor, times(0)).processEvent(any(MessageData.class));
  }
}
