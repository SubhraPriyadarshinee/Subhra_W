package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameUnloadingCompleteDeliveryProcessorTest extends ReceivingTestBase {

  @InjectMocks
  private EndGameUnloadingCompleteDeliveryProcessor endGameUnloadingCompleteDeliveryProcessor;

  @Mock private EndGameLabelingService endGameLabelingService;

  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  private Gson gson = new Gson();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(9610);
  }

  @AfterMethod
  public void resetMocks() {
    reset(endGameLabelingService);
  }

  @Test
  public void testDoProcess() throws ReceivingException {
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(1L);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE.name());
    deliveryInfo.setTrailerNumber("TLR1001");
    endGameUnloadingCompleteDeliveryProcessor.doProcess(deliveryInfo);
  }

  @Test
  public void testDoProcess_deliveryStatusNull() throws ReceivingException {
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(1L);
    deliveryInfo.setTrailerNumber("TLR1001");
    endGameUnloadingCompleteDeliveryProcessor.doProcess(deliveryInfo);
  }

  @Test
  public void testDoProcess_deliveryStatus_Mismatch() throws ReceivingException {
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(1L);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    deliveryInfo.setTrailerNumber("TLR1001");
    endGameUnloadingCompleteDeliveryProcessor.doProcess(deliveryInfo);
  }
}
