package com.walmart.move.nim.receiving.endgame.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequestVO;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PrintingAckHelperTest extends ReceivingTestBase {
  private Gson gson;

  @InjectMocks private PrintingAckHelper printingAckHelper;

  @Mock private EndGameLabelingService endGameLabelingService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private EndgameManagedConfig endgameManagedConfig;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(printingAckHelper, "gson", gson);
    TenantContext.setFacilityCountryCode("us");
    TenantContext.setFacilityNum(9610);
  }

  @AfterMethod
  public void resetMocks() {
    reset(deliveryMetaDataService);
    reset(endGameLabelingService);
    reset(endgameManagedConfig);
    reset(configUtils);
  }

  @Test
  public void testDoProcess() {
    when(endgameManagedConfig.getLabelingThresholdLimit()).thenReturn(0.8f);
    when(endgameManagedConfig.getExtraTCLToSend()).thenReturn(Long.valueOf(100));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCL());
    when(endGameLabelingService.saveOrUpdateLabel(any(PreLabelData.class))).thenReturn(sampleTCL());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(endGameLabelingService.countByStatusInAndDeliveryNumber(any(List.class), anyLong()))
        .thenReturn(
            Long.valueOf((long) (deliveryMetaData().orElse(null).getTotalCaseLabelSent() * 0.6f)));
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    doNothing().when(endGameLabelingService).persistLabel(any(EndGameLabelData.class));
    when(endGameLabelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    when(endGameLabelingService.generateLabel(any(LabelRequestVO.class)))
        .thenReturn(generateLabel());
    printingAckHelper.doProcess(printAckMessage(), sampleKafkaHeader());

    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameLabelingService, times(1))
        .countByStatusInAndDeliveryNumber(any(List.class), anyLong());
    verify(endGameLabelingService, times(1)).saveOrUpdateLabel(any(PreLabelData.class));
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(endgameManagedConfig, times(1)).getLabelingThresholdLimit();
    verify(endgameManagedConfig, times(0)).getExtraTCLToSend();
    verify(endGameLabelingService, times(0))
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    verify(endGameLabelingService, times(0)).persistLabel(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(0)).send(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(0)).generateLabel(any(LabelRequestVO.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testFailedMessage() {
    when(endgameManagedConfig.getLabelingThresholdLimit()).thenReturn(0.8f);
    when(endgameManagedConfig.getExtraTCLToSend()).thenReturn(Long.valueOf(100));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCL());
    when(endGameLabelingService.saveOrUpdateLabel(any(PreLabelData.class))).thenReturn(sampleTCL());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(endGameLabelingService.countByStatusInAndDeliveryNumber(any(List.class), anyLong()))
        .thenReturn(
            Long.valueOf((long) (deliveryMetaData().orElse(null).getTotalCaseLabelSent() * 0.8f)));
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    doNothing().when(endGameLabelingService).persistLabel(any(EndGameLabelData.class));
    when(endGameLabelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    when(endGameLabelingService.generateLabel(any(LabelRequestVO.class)))
        .thenReturn(generateLabel());
    printingAckHelper.doProcess(printFailedAckMessage(), sampleKafkaHeader());

    verify(endGameLabelingService, times(0)).findByTcl(anyString());
    verify(endGameLabelingService, times(0))
        .countByStatusInAndDeliveryNumber(any(List.class), anyLong());
    verify(endGameLabelingService, times(0)).saveOrUpdateLabel(any(PreLabelData.class));
    verify(endGameLabelingService, times(0))
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    verify(endGameLabelingService, times(0)).persistLabel(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(0)).send(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(0)).generateLabel(any(LabelRequestVO.class));
    verify(deliveryMetaDataService, times(0)).findByDeliveryNumber(anyString());
    verify(endgameManagedConfig, times(0)).getLabelingThresholdLimit();
    verify(endgameManagedConfig, times(0)).getExtraTCLToSend();
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testDoProcessAndSendExtraLabel() {
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    when(endgameManagedConfig.getLabelingThresholdLimit()).thenReturn(0.8f);
    when(endgameManagedConfig.getExtraTCLToSend()).thenReturn(Long.valueOf(100));
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCL());
    when(endGameLabelingService.saveOrUpdateLabel(any(PreLabelData.class))).thenReturn(sampleTCL());
    when(endGameLabelingService.countByStatusInAndDeliveryNumber(any(List.class), anyLong()))
        .thenReturn(
            Long.valueOf((long) (deliveryMetaData().orElse(null).getTotalCaseLabelSent() * 0.8f)));
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    doNothing().when(endGameLabelingService).persistLabel(any(EndGameLabelData.class));
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(endGameLabelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    when(endGameLabelingService.generateLabel(any(LabelRequestVO.class)))
        .thenReturn(generateLabel());

    printingAckHelper.doProcess(printAckMessage(), sampleKafkaHeader());

    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameLabelingService, times(1))
        .countByStatusInAndDeliveryNumber(any(List.class), anyLong());
    verify(endGameLabelingService, times(1)).saveOrUpdateLabel(any(PreLabelData.class));
    verify(endGameLabelingService, times(1))
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    verify(endGameLabelingService, times(1)).persistLabel(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(1)).send(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(1)).generateLabel(any(LabelRequestVO.class));
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(endgameManagedConfig, times(1)).getLabelingThresholdLimit();
    verify(endgameManagedConfig, times(1)).getExtraTCLToSend();
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to access Kafka.*")
  public void testDoProcessAndSendExtraLabel_FailedPublishing() {
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    when(endgameManagedConfig.getLabelingThresholdLimit()).thenReturn(0.8f);
    when(endgameManagedConfig.getExtraTCLToSend()).thenReturn(Long.valueOf(100));
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(sampleTCL());
    when(endGameLabelingService.saveOrUpdateLabel(any(PreLabelData.class))).thenReturn(sampleTCL());
    when(endGameLabelingService.countByStatusInAndDeliveryNumber(any(List.class), anyLong()))
        .thenReturn(
            Long.valueOf((long) (deliveryMetaData().orElse(null).getTotalCaseLabelSent() * 0.8f)));
    doNothing()
        .when(endGameLabelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    doNothing().when(endGameLabelingService).persistLabel(any(EndGameLabelData.class));
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(endGameLabelingService.send(any(EndGameLabelData.class))).thenReturn("Exception");
    when(endGameLabelingService.generateLabel(any(LabelRequestVO.class)))
        .thenReturn(generateLabel());

    printingAckHelper.doProcess(printAckMessage(), sampleKafkaHeader());

    verify(endGameLabelingService, times(1)).findByTcl(anyString());
    verify(endGameLabelingService, times(1))
        .countByStatusInAndDeliveryNumber(any(List.class), anyLong());
    verify(endGameLabelingService, times(1)).saveOrUpdateLabel(any(PreLabelData.class));
    verify(endGameLabelingService, times(1))
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    verify(endGameLabelingService, times(1)).persistLabel(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(1)).send(any(EndGameLabelData.class));
    verify(endGameLabelingService, times(1)).generateLabel(any(LabelRequestVO.class));
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(endgameManagedConfig, times(1)).getLabelingThresholdLimit();
    verify(endgameManagedConfig, times(1)).getExtraTCLToSend();
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testExtraLabelGenFixedQty() {
    when(endgameManagedConfig.getLabelingThresholdLimit()).thenReturn(0.8f);
    when(endgameManagedConfig.getExtraTCLToSend()).thenReturn(Long.valueOf(100));
    long qty =
        (long)
            (deliveryMetaData().orElse(null).getTotalCaseLabelSent()
                * endgameManagedConfig.getLabelingThresholdLimit());

    long value =
        ReflectionTestUtils.invokeMethod(
            printingAckHelper, "calculateThreshold", deliveryMetaData().orElse(null), qty);
    assertEquals(value, 100);

    // Less then threshld so , it will not get
    value =
        ReflectionTestUtils.invokeMethod(
            printingAckHelper,
            "calculateThreshold",
            deliveryMetaData().orElse(null),
            Long.valueOf(
                (long)
                    (deliveryMetaData().orElse(null).getTotalCaseLabelSent()
                        * (endgameManagedConfig.getLabelingThresholdLimit() - 0.1f))));
    assertEquals(value, 0);
  }

  private Optional<PreLabelData> sampleTCL() {
    return Optional.of(
        PreLabelData.builder()
            .tcl("TC12354321")
            .deliveryNumber(891100l)
            .status(LabelStatus.SENT)
            .build());
  }

  private Optional<DeliveryMetaData> deliveryMetaData() {
    return Optional.of(
        DeliveryMetaData.builder()
            .totalCaseLabelSent(1000)
            .deliveryNumber("891100")
            .totalCaseCount(1000)
            .build());
  }

  private String printAckMessage() {
    Map<String, String> message = new HashMap<>();
    message.put("trailerCaseLabel", "TC12354321");
    message.put("status", "PRINTED");
    return gson.toJson(message);
  }

  private String printFailedAckMessage() {
    Map<String, String> message = new HashMap<>();
    message.put("trailerCaseLabel", "TC12354321");
    message.put("status", "FAILED");
    message.put("reason", "out of ink/paper");
    return gson.toJson(message);
  }

  private Map<String, byte[]> sampleKafkaHeader() {
    Map<String, byte[]> headers = new HashMap<>();
    headers.put(EndgameConstants.TENENT_FACLITYNUM, "32987".getBytes());
    headers.put(EndgameConstants.TENENT_COUNTRY_CODE, "us".getBytes());
    return headers;
  }

  private EndGameLabelData generateLabel() {
    return EndGameLabelData.builder()
        .defaultTCL("TA000001")
        .defaultDestination("DECANT")
        .trailerCaseLabels(Collections.singleton("TA000001"))
        .doorNumber("123")
        .clientId(EndgameConstants.CLIENT_ID)
        .formatName(EndgameConstants.TCL_LABEL_FORMAT_NAME)
        .deliveryNumber("891100")
        .trailer("123")
        .user(EndgameConstants.DEFAULT_USER)
        .tclTemplate("Test_FORMAT")
        .labelGenMode(LabelGenMode.AUTOMATED.getMode())
        .type(LabelType.TCL)
        .build();
  }
}
