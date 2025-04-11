package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.SUCCESS_MSG;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockLabelResponse;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequestVO;
import com.walmart.move.nim.receiving.endgame.model.LabelSummary;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataCustomRepository;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameLabelingServiceTest extends ReceivingTestBase {

  private static final String TCL = "TC87654321";
  private static final String TPL = "PA87654321";
  private static final String DELIVERY_NUMBER = "12345678";
  private static final String DOOR_NUMBER = "100";
  private static final String TRAILER_ID = "7897";

  @InjectMocks private EndGameLabelingService endGameLabelingService;
  @Mock private PreLabelDataRepository preLabelDataRepository;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private ReceivingCounterService receivingCounterService;
  @Mock private AppConfig appConfig;
  @Mock private EndgameManagedConfig endgameManagedConfig;
  @Mock private PreLabelDataCustomRepository preLabelDataCustomRepository;
  @Mock private Page<PreLabelData> preLabelData;
  @Mock private KafkaConfig kafkaConfig;
  @Mock private KafkaTemplate securePublisher;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private IOutboxPublisherService outboxPublisherService;

  private Gson gson;

  @BeforeClass
  public void setRootUp() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endGameLabelingService, "gson", gson);
    ReflectionTestUtils.setField(endGameLabelingService, "securePublisher", securePublisher);

    TenantContext.setFacilityCountryCode("us");
    TenantContext.setFacilityNum(54321);
    ReflectionTestUtils.setField(endGameLabelingService, "hawkeyeLabelTopic", "label-topic");
  }

  @AfterMethod
  public void resetMocks() {
    reset(deliveryMetaDataService);
    reset(securePublisher);
    reset(preLabelDataRepository);
  }

  @Test
  public void testGenerateLabelTCL() {
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(endgameManagedConfig.getPrintableZPLTemplate())
        .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    EndGameLabelData endGameLabelData =
        endGameLabelingService.generateLabel(
            createLabelRequestVO(
                DELIVERY_NUMBER,
                DOOR_NUMBER,
                TRAILER_ID,
                851,
                LabelType.TCL,
                LabelGenMode.AUTOMATED));
    assertEquals(endGameLabelData.getDeliveryNumber(), DELIVERY_NUMBER);
    assertEquals(endGameLabelData.getTrailer(), TRAILER_ID);
    assertEquals(endGameLabelData.getDoorNumber(), DOOR_NUMBER);
    assertEquals(endGameLabelData.getLabelGenMode(), LabelGenMode.AUTOMATED.getMode());
    assertEquals(
        endGameLabelData.getDefaultTCL(),
        endGameLabelingService.generateDefaultTCL(DELIVERY_NUMBER));
    assertTrue(endGameLabelData.getTrailerCaseLabels().size() > 0);
    assertEquals(endGameLabelData.getTrailerCaseLabels().size(), 851);
    String anyTCL = endGameLabelData.getTrailerCaseLabels().iterator().next();
    assertTrue(anyTCL.startsWith("T"));
  }

  private LabelRequestVO createLabelRequestVO(
      String deliveryNumber,
      String doorNumber,
      String trailerId,
      int qty,
      LabelType labelType,
      LabelGenMode labelGenMode) {
    return LabelRequestVO.builder()
        .deliveryNumber(deliveryNumber)
        .door(doorNumber)
        .trailerId(trailerId)
        .quantity(qty)
        .type(labelType)
        .labelGenMode(labelGenMode)
        .build();
  }

  @Test
  public void testGenerateLabelTPL() {
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTPLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    EndGameLabelData endGameLabelData =
        endGameLabelingService.generateLabel(
            createLabelRequestVO(
                DELIVERY_NUMBER, DOOR_NUMBER, TRAILER_ID, 1, LabelType.TPL, LabelGenMode.MOBILE));
    assertEquals(endGameLabelData.getDeliveryNumber(), DELIVERY_NUMBER);
    assertEquals(endGameLabelData.getTrailer(), TRAILER_ID);
    assertEquals(endGameLabelData.getDoorNumber(), DOOR_NUMBER);
    assertEquals(endGameLabelData.getLabelGenMode(), LabelGenMode.MOBILE.getMode());
    assertEquals(
        endGameLabelData.getDefaultTCL(),
        endGameLabelingService.generateDefaultTCL(DELIVERY_NUMBER));
    assertTrue(endGameLabelData.getTrailerCaseLabels().size() > 0);
    assertEquals(endGameLabelData.getTrailerCaseLabels().size(), 1);
    String anyTCL = endGameLabelData.getTrailerCaseLabels().iterator().next();
    assertTrue(anyTCL.startsWith("TP"));
  }

  @Test
  public void testSendData() throws ReceivingException {
    when(endgameManagedConfig.getPrintableZPLTemplate())
        .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    when(tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(false);

    EndGameLabelData endGameLabelData =
        endGameLabelingService.generateLabel(
            createLabelRequestVO(
                DELIVERY_NUMBER,
                DOOR_NUMBER,
                TRAILER_ID,
                851,
                LabelType.TCL,
                LabelGenMode.AUTOMATED));

    String status = endGameLabelingService.send(endGameLabelData);
    assertEquals("success", status);
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
  }

  @Test
  public void testSendDataOnSecureKafka() throws ReceivingException {
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(kafkaConfig.isHawkeyeSecurePublish()).thenReturn(true);
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    when(tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(false);
    EndGameLabelData endGameLabelData =
        endGameLabelingService.generateLabel(
            createLabelRequestVO(
                DELIVERY_NUMBER,
                DOOR_NUMBER,
                TRAILER_ID,
                851,
                LabelType.TCL,
                LabelGenMode.AUTOMATED));

    String status = endGameLabelingService.send(endGameLabelData);
    assertEquals("success", status);
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
  }

  @Test
  public void testSendDataFailed() throws ReceivingException {
    when(endgameManagedConfig.getPrintableZPLTemplate())
        .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    when(tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(false);
    when(securePublisher.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    EndgameConstants.TCL_UPLOAD_FLOW)));

    EndGameLabelData endGameLabelData =
        endGameLabelingService.generateLabel(
            createLabelRequestVO(
                DELIVERY_NUMBER,
                DOOR_NUMBER,
                TRAILER_ID,
                851,
                LabelType.TCL,
                LabelGenMode.AUTOMATED));

    assertEquals(
        endGameLabelingService.send(endGameLabelData),
        "Unable to access Kafka. Flow= TCL_UPLOADING");
    verify(securePublisher, times(1)).send(any(Message.class));
  }

  @Test
  public void testDeleteDeliveryMetadata() {

    doNothing().when(deliveryMetaDataService).deleteByDeliveryNumber(anyString());

    endGameLabelingService.deleteDeliveryMetaData(DELIVERY_NUMBER);
    verify(deliveryMetaDataService, times(1)).deleteByDeliveryNumber(anyString());
  }

  @Test
  public void testDeleteTCL() {

    doNothing().when(preLabelDataRepository).deleteByDeliveryNumber(anyLong());

    endGameLabelingService.deleteTCLByDeliveryNumber(12345678);
    verify(preLabelDataRepository, times(1)).deleteByDeliveryNumber(anyLong());
  }

  @Test
  public void testFindDeliveryMetaData() {

    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());

    DeliveryMetaData optionalDeliveryMetaData =
        endGameLabelingService.findDeliveryMetadataByDeliveryNumber(DELIVERY_NUMBER).orElse(null);
    assertEquals(optionalDeliveryMetaData.getTotalCaseLabelSent(), 1000);
    assertEquals(optionalDeliveryMetaData.getTotalCaseCount(), 1000);
  }

  @Test
  public void testFindByTCL() {

    when(preLabelDataRepository.findByTcl(TCL)).thenReturn(preLabelData());

    PreLabelData preLabelData = endGameLabelingService.findByTcl(TCL).get();
    assertEquals(preLabelData.getTcl(), TCL);
    assertEquals(preLabelData.getStatus(), LabelStatus.ATTACHED);
  }

  @Test
  public void testFindAllByDeliveryNumber() {
    when(preLabelDataRepository.findByDeliveryNumber(Long.valueOf(DELIVERY_NUMBER)))
        .thenReturn(listPreLabelData());

    List<PreLabelData> preLabelDataList =
        endGameLabelingService.findTCLsByDeliveryNumber(Long.valueOf(DELIVERY_NUMBER));
    assertEquals(preLabelDataList.size(), 1);
    PreLabelData preLabelData = preLabelDataList.get(0);
    assertEquals(preLabelData.getTcl(), TCL);
    assertEquals(preLabelData.getStatus(), LabelStatus.ATTACHED);
  }

  @Test
  public void testFindByDeliveryNumber() {
    when(preLabelDataRepository.findByDeliveryNumber(anyLong(), any()))
        .thenReturn(preLabelDataPage());
    Page<PreLabelData> preLabelDataList =
        endGameLabelingService.findByDeliveryNumber(Long.valueOf(DELIVERY_NUMBER), 1, 10);
    Optional<PreLabelData> preLabelData = preLabelDataList.getContent().stream().findFirst();
    assertEquals(preLabelData.isPresent(), true);
  }

  @Test
  public void testFindByDeliveryNumberNotFound() {
    when(preLabelDataRepository.findByDeliveryNumber(anyLong(), any())).thenReturn(preLabelData);
    Page<PreLabelData> preLabelDataList =
        endGameLabelingService.findByDeliveryNumber(Long.valueOf(DELIVERY_NUMBER), 2, 10);
    Optional<PreLabelData> preLabelData = preLabelDataList.getContent().stream().findFirst();
    assertEquals(preLabelData.isPresent(), false);
  }

  @Test
  public void testFindByDeliveryNumberAndType() {
    when(preLabelDataRepository.findByDeliveryNumberAndType(anyLong(), any(LabelType.class), any()))
        .thenReturn(preLabelDataPage());
    Page<PreLabelData> preLabelDataList =
        endGameLabelingService.findByDeliveryNumberAndLabelType(
            Long.valueOf(DELIVERY_NUMBER), LabelType.TCL, 1, 10);
    Optional<PreLabelData> preLabelData = preLabelDataList.getContent().stream().findFirst();
    assertEquals(preLabelData.isPresent(), true);
  }

  @Test
  public void testFindByDeliveryNumberAndTypeException() {
    when(preLabelDataRepository.findByDeliveryNumberAndType(anyLong(), any(LabelType.class), any()))
        .thenReturn(preLabelData);
    Page<PreLabelData> preLabelDataList =
        endGameLabelingService.findByDeliveryNumberAndLabelType(
            Long.valueOf(DELIVERY_NUMBER), LabelType.TCL, 2, 10);
    assertEquals(preLabelDataList.getContent().isEmpty(), true);
  }

  @Test
  public void findTPLAndTCLCountTest() {
    when(preLabelDataCustomRepository.findLabelSummary(anyLong(), anyString()))
        .thenReturn(labelSummary());
    List<LabelSummary> labelSummaryList =
        endGameLabelingService.findLabelSummary(Long.valueOf(DELIVERY_NUMBER), "TPL");
    assertEquals(labelSummaryList.size(), 2);
  }

  private List<LabelSummary> labelSummary() {
    List<LabelSummary> labelSummary = new ArrayList<>();
    labelSummary.add(new LabelSummary(LabelType.TCL, 123l));
    labelSummary.add(new LabelSummary(LabelType.TPL, 20l));
    return labelSummary;
  }

  private Page<PreLabelData> preLabelDataPage() {
    List<PreLabelData> preLabelData = new ArrayList<>();
    preLabelData.add(
        PreLabelData.builder()
            .deliveryNumber(12345678L)
            .reason(null)
            .type(LabelType.TCL)
            .status(LabelStatus.SENT)
            .id(876452L)
            .tcl("TA00870795")
            .build());
    Page<PreLabelData> page = new PageImpl<>(preLabelData);
    return page;
  }

  private List<PreLabelData> listPreLabelData() {
    return Arrays.asList(preLabelData().get());
  }

  private Optional<PreLabelData> preLabelData() {
    PreLabelData preLabelData =
        PreLabelData.builder().status(LabelStatus.ATTACHED).tcl("TC87654321").id(10).build();
    return Optional.of(preLabelData);
  }

  private Optional<DeliveryMetaData> deliveryMetaData() {
    return Optional.of(
        DeliveryMetaData.builder()
            .totalCaseLabelSent(1000)
            .deliveryNumber("891100")
            .totalCaseCount(1000)
            .trailerNumber("12345678")
            .build());
  }

  private ReceivingCounter getTCLCounter() {
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setType(EndgameConstants.COUNTER_TYPE_TCL);
    receivingCounter.setCounterNumber(0);
    receivingCounter.setPrefixIndex('A');
    receivingCounter.setPrefix("TA");
    return receivingCounter;
  }

  private ReceivingCounter getTPLCounter() {
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setType(EndgameConstants.COUNTER_TYPE_TPL);
    receivingCounter.setCounterNumber(0);
    receivingCounter.setPrefixIndex('P');
    receivingCounter.setPrefix("TP");
    return receivingCounter;
  }

  @Test
  public void saveLabelsTest() {
    when(preLabelDataRepository.saveAll(anyList())).thenReturn(preLabelDataList());
    List<PreLabelData> preLabelDataList = endGameLabelingService.saveLabels(anyList());
    assertEquals(preLabelDataList.get(0).getDeliveryNumber(), 123456789l);
    assertEquals(preLabelDataList.get(0).getTcl(), TPL);
    assertNotNull(preLabelDataList);
  }

  private List<PreLabelData> preLabelDataList() {
    List<PreLabelData> preLabelDataList = new ArrayList<>();
    preLabelDataList.add(
        PreLabelData.builder()
            .deliveryNumber(123456789l)
            .tcl(TPL)
            .status(LabelStatus.SCANNED)
            .type(LabelType.TPL)
            .build());
    return preLabelDataList;
  }

  @Test
  public void sendDataToKafkaWhenOutboxKafkaPublishEnabled() throws ReceivingException {
    when(tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(true);
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(endgameManagedConfig.getPrintableZPLTemplate())
            .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    EndGameLabelData endGameLabelData =
            endGameLabelingService.generateLabel(
                    createLabelRequestVO(
                            DELIVERY_NUMBER,
                            DOOR_NUMBER,
                            TRAILER_ID,
                            851,
                            LabelType.TCL,
                            LabelGenMode.AUTOMATED));

    String status = endGameLabelingService.send(endGameLabelData);
    assertEquals(status,"success");
    verify(outboxPublisherService, times(1)).publishToKafka(anyString(), anyMap(), anyString(), anyInt(), anyString(), anyString());
  }


  @Test
  public void sendDataToKafkaWhenOutboxKafkaPublishDisabled() throws ReceivingException {
    when(tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(false);
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(endgameManagedConfig.getPrintableZPLTemplate())
            .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    EndGameLabelData endGameLabelData =
            endGameLabelingService.generateLabel(
                    createLabelRequestVO(
                            DELIVERY_NUMBER,
                            DOOR_NUMBER,
                            TRAILER_ID,
                            851,
                            LabelType.TCL,
                            LabelGenMode.AUTOMATED));

    String status = endGameLabelingService.send(endGameLabelData);
    assertEquals(status, SUCCESS_MSG);
    verify(securePublisher, times(1)).send(any(Message.class));
  }

  @Test
  public void sendDataToKafkaThrowsException() throws ReceivingException {
    when(tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(false);
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(endgameManagedConfig.getPrintableZPLTemplate())
            .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getTCLCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(repository -> repository.getArgument(0));
    when(securePublisher.send(any(Message.class)))
            .thenThrow(
                    new ReceivingInternalException(
                            ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                            String.format(
                                    ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                                    EndgameConstants.TCL_UPLOAD_FLOW)));

    EndGameLabelData endGameLabelData =
            endGameLabelingService.generateLabel(
                    createLabelRequestVO(
                            DELIVERY_NUMBER,
                            DOOR_NUMBER,
                            TRAILER_ID,
                            851,
                            LabelType.TCL,
                            LabelGenMode.AUTOMATED));

    String status = endGameLabelingService.send(endGameLabelData);
    assertEquals(status, "Unable to access Kafka. Flow= TCL_UPLOADING");
    verify(securePublisher, times(1)).send(any(Message.class));
  }
}
