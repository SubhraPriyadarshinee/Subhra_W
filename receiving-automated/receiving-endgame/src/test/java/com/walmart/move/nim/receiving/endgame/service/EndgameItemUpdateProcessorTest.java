package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class EndgameItemUpdateProcessorTest extends ReceivingTestBase {
  private Gson gson = null;
  @Mock private EndGameSlottingService endGameSlottingService;

  @InjectMocks private EndgameItemUpdateProcessor endgameItemUpdateProcessor;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    this.gson = new Gson();
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(9610);
  }

  @BeforeMethod
  public void resetMocks() {
    reset(endGameSlottingService);
  }

  @Test
  public void testFTSUpdate() throws ReceivingException {
    doNothing().when(endGameSlottingService).updateDivertForItem(any(UpdateAttributesData.class));

    endgameItemUpdateProcessor.processEvent(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE));

    verify(endGameSlottingService, times(1)).updateDivertForItem(any(UpdateAttributesData.class));
  }

  @Test
  public void testFTSUpdateInvalidPayload() throws ReceivingException {
    doNothing().when(endGameSlottingService).updateDivertForItem(any(UpdateAttributesData.class));

    endgameItemUpdateProcessor.processEvent(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE));

    verify(endGameSlottingService, times(0)).updateDivertForItem(any(UpdateAttributesData.class));
  }

  @Test
  public void testFTSUpdate_EmptyPayload() throws ReceivingException {
    doNothing().when(endGameSlottingService).updateDivertForItem(any(UpdateAttributesData.class));

    endgameItemUpdateProcessor.processEvent(null);

    verify(endGameSlottingService, times(0)).updateDivertForItem(any(UpdateAttributesData.class));
  }
}
