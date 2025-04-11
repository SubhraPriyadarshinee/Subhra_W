package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.PrinterACK;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockContainer;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.DimensionPayload;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameDivertAckEventProcessorTest extends ReceivingTestBase {

  @Mock private EndgameManagedConfig endgameManagedConfig;
  @Mock private EndGameReceivingService endGameReceivingService;
  @Mock private EndgameDecantService endgameDecantService;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private EndGameLabelingService endGameLabelingService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private EndGameDivertAckEventProcessor endGameDivertAckEventProcessor;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void resetMocks() {
    reset(endGameReceivingService);
    reset(endgameDecantService);
    reset(endGameDeliveryService);
    reset(endgameManagedConfig);
    reset(endGameLabelingService);
  }

  @Test
  public void testReceiveForWrongMessage() {
    MessageData messageData = new PrinterACK();
    endGameDivertAckEventProcessor.processEvent(messageData);
    verify(endGameReceivingService, never()).receiveVendorPack(any());
  }

  @Test
  public void testNotReceive_WhenCaseIsNotDivertedToDecantOrPalletBuild() {
    MessageData messageData = MockMessageData.getMockReceivingRequestDataForQA();
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCLNotScanned());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.DYNAMIC_PO_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(sampleTCLNotScanned())
        .when(endGameLabelingService)
        .saveOrUpdateLabel(any(PreLabelData.class));
    endGameDivertAckEventProcessor.processEvent(messageData);
    verify(endGameReceivingService, never()).receiveVendorPack(any());
    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameLabelingService, times(1)).saveOrUpdateLabel(any(PreLabelData.class));
  }

  @Test
  public void testPublishingVendorDimension() {
    ScanEventData messageData = MockMessageData.getScanEventDataWithDimensions();
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCLNotScanned());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.DYNAMIC_PO_FEATURE_FLAG))
        .thenReturn(true);
    doReturn(sampleTCLNotScanned())
        .when(endGameLabelingService)
        .saveOrUpdateLabel(any(PreLabelData.class));
    doNothing().when(endgameDecantService).publish(any(DimensionPayload.class));
    when(endgameManagedConfig.isPublishVendorDimension()).thenReturn(true);
    when(endGameReceivingService.receiveVendorPack(messageData))
        .thenReturn(ReceiveVendorPack.builder().container(MockContainer.getContainer()).build());
    endGameDivertAckEventProcessor.processEvent(messageData);
    verify(endGameReceivingService, times(1)).persistAttachPurchaseOrderRequestToOutbox(any());
    verify(endGameReceivingService, times(1)).receiveVendorPack(any());
    verify(endgameDecantService, times(1)).publish(any());
    verify(endgameManagedConfig, times(1)).isPublishVendorDimension();
    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameLabelingService, times(1)).saveOrUpdateLabel(any(PreLabelData.class));
  }

  @Test
  public void testAlreadyScannedTCL() {
    ScanEventData messageData = MockMessageData.getScanEventDataWithDimensions();
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCLAlreadyScanned());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.DYNAMIC_PO_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(sampleTCLNotScanned())
        .when(endGameLabelingService)
        .saveOrUpdateLabel(any(PreLabelData.class));
    doNothing().when(endgameDecantService).publish(any(DimensionPayload.class));
    when(endgameManagedConfig.isPublishVendorDimension()).thenReturn(true);
    when(endGameReceivingService.receiveVendorPack(messageData))
        .thenReturn(ReceiveVendorPack.builder().container(MockContainer.getContainer()).build());
    endGameDivertAckEventProcessor.processEvent(messageData);
    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameReceivingService, times(0)).receiveVendorPack(any());
    verify(endgameDecantService, times(0)).publish(any());
    verify(endgameManagedConfig, times(0)).isPublishVendorDimension();
    verify(endGameLabelingService, times(1)).saveOrUpdateLabel(any(PreLabelData.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "TCL= TC00000001 not found*")
  public void testTclNotFound() {
    ScanEventData messageData = MockMessageData.getScanEventDataWithDimensions();
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.DYNAMIC_PO_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(sampleTCLNotScanned())
        .when(endGameLabelingService)
        .saveOrUpdateLabel(any(PreLabelData.class));
    doNothing().when(endgameDecantService).publish(any(DimensionPayload.class));
    when(endgameManagedConfig.isPublishVendorDimension()).thenReturn(true);
    when(endGameReceivingService.receiveVendorPack(messageData))
        .thenReturn(ReceiveVendorPack.builder().container(MockContainer.getContainer()).build());
    endGameDivertAckEventProcessor.processEvent(messageData);
    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameReceivingService, times(0)).receiveVendorPack(any());
    verify(endgameDecantService, times(0)).publish(any());
    verify(endgameManagedConfig, times(0)).isPublishVendorDimension();
    verify(endGameLabelingService, times(0)).saveOrUpdateLabel(any(PreLabelData.class));
  }

  private Optional<PreLabelData> sampleTCLNotScanned() {
    return Optional.of(
        PreLabelData.builder().tcl("TC12354321").status(LabelStatus.ATTACHED).build());
  }

  private Optional<PreLabelData> sampleTCLAlreadyScanned() {
    return Optional.of(
        PreLabelData.builder().tcl("TC12354321").status(LabelStatus.SCANNED).build());
  }
}
