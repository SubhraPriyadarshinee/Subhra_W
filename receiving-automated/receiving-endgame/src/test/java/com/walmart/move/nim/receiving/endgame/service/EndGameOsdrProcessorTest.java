package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_DELIVERY_NUMBER;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.mock.data.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockEndgameReceipt;
import com.walmart.move.nim.receiving.endgame.mock.data.MockOsdrSummary;
import com.walmart.move.nim.receiving.endgame.model.OSDRRequest;
import com.walmart.move.nim.receiving.endgame.model.PoReceipt;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameOsdrProcessorTest extends ReceivingTestBase {
  @InjectMocks private EndGameOsdrProcessor endGameOsdrProcessor;
  @Mock private OsdrConfig osdrConfig;
  @Mock private EndGameOsdrService endGameOsdrService;
  @Mock private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;
  @Mock EndgameDeliveryStatusPublisher endgameDeliveryStatusPublisher;
  @Mock ReceiptService receiptService;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  @Mock private EndgameOutboxHandler endgameOutboxHandler;
  private Gson gson = new Gson();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endGameOsdrProcessor, "gson", gson);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(9610);
    ReflectionTestUtils.setField(
        endGameOsdrProcessor, "endGameDeliveryMetaDataService", endGameDeliveryMetaDataService);
    ReflectionTestUtils.setField(endGameOsdrProcessor, "osdrConfig", osdrConfig);
  }

  @AfterMethod
  public void resetMocks() {
    reset(osdrConfig);
    reset(endGameDeliveryMetaDataService);
    reset(endGameOsdrService);
    reset(endgameDeliveryStatusPublisher);
  }

  @Test
  public void testProcess() throws ReceivingException {

    doReturn(MockEndgameReceipt.getReceiptsForOSDRDetails())
        .when(receiptService)
        .getReceiptSummary(any(), any(), any());

    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(endGameDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    doReturn(MockOsdrSummary.getOsdrSummary())
        .when(endGameOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    doNothing().when(endgameDeliveryStatusPublisher).publishMessage(any(), any());
    OsdrConfigSpecification osdrConfigSpecification = new OsdrConfigSpecification();
    osdrConfigSpecification.setFacilityNum(9610);
    osdrConfigSpecification.setFacilityCountryCode("US");
    osdrConfigSpecification.setUom(ReceivingConstants.Uom.VNPK);
    endGameOsdrProcessor.process(osdrConfigSpecification);
    verify(endGameOsdrService, times(2)).getOsdrDetails(any(), any(), any(), any());
    verify(endGameDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(endgameDeliveryStatusPublisher, times(2)).publishMessage(any(), any());
  }

  @Test
  public void testProcess_NoOsdrDetailsForOneDelivery() throws ReceivingException {
    when(osdrConfig.getPageSize()).thenReturn(10);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(endGameDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());

    doReturn(MockEndgameReceipt.getReceiptsForOSDRDetails())
        .when(receiptService)
        .getReceiptSummary(any(), any(), any());

    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.RECEIPTS_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants.OSDR_RECEIPTS_NOT_FOUND_ERROR_MSG, "12333334")))
        .when(endGameOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    doNothing().when(endgameDeliveryStatusPublisher).publishMessage(any(), any());
    OsdrConfigSpecification osdrConfigSpecification = new OsdrConfigSpecification();
    osdrConfigSpecification.setFacilityNum(9610);
    osdrConfigSpecification.setFacilityCountryCode("US");
    osdrConfigSpecification.setUom(ReceivingConstants.Uom.VNPK);

    endGameOsdrProcessor.process(osdrConfigSpecification);

    verify(endGameOsdrService, times(2)).getOsdrDetails(any(), any(), any(), any());
    verify(osdrConfig, times(1)).getPageSize();
    verify(endGameDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
  }

  @Test
  public void testProcess_NoDeliveryToProcessOsdr() throws ReceivingException {
    when(osdrConfig.getPageSize()).thenReturn(10);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(endGameDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(new ArrayList<>());
    doReturn(MockOsdrSummary.getOsdrSummary())
        .when(endGameOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    OsdrConfigSpecification osdrConfigSpecification = new OsdrConfigSpecification();
    osdrConfigSpecification.setFacilityNum(9610);
    osdrConfigSpecification.setFacilityCountryCode("US");
    osdrConfigSpecification.setUom(ReceivingConstants.Uom.VNPK);

    endGameOsdrProcessor.process(osdrConfigSpecification);

    verify(endGameOsdrService, times(0)).getOsdrDetails(anyLong(), any(), anyString(), anyString());
    verify(osdrConfig, times(1)).getPageSize();
    verify(endGameDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testProcessOSDRException() throws ReceivingException {

    String osdrConfigs1 =
        "[\n"
            + "  {\n"
            + "    \"facilityNum\": 7054,\n"
            + "    \"facilityCountryCode\": \"US\",\n"
            + "    \"uom\": \"ZA\",\n"
            + "    \"nosOfDay\": 5,\n"
            + "    \"frequencyFactor\": 2,\n"
            + "    \"osdrPOBatchSize\": 3\n"
            + "  }\n"
            + "]";

    doReturn(MockEndgameReceipt.getReceiptsForOSDRDetails())
        .when(receiptService)
        .getReceiptSummary(any(), any(), any());

    when(osdrConfig.getSpecifications()).thenReturn(osdrConfigs1);
    when(endGameDeliveryMetaDataService.findAllByDeliveryNumber(anyList()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    doReturn(MockOsdrSummary.getOsdrSummary())
        .when(endGameOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    doNothing().when(endgameDeliveryStatusPublisher).publishMessage(any(), any());
    OSDRRequest request = new OSDRRequest();
    List<String> delNos = new ArrayList<>();
    delNos.add("123");
    request.setDeliveryNos(delNos);
    endGameOsdrProcessor.processOSDR(request);
    verify(endGameOsdrService, times(2)).getOsdrDetails(anyLong(), any(), anyString(), anyString());
    verify(osdrConfig, times(0)).getFrequencyIntervalInMinutes();
    verify(osdrConfig, times(0)).getPageSize();
  }

  @Test
  public void testProcessOSDR() throws ReceivingException {

    String osdrConfigs =
        "[\n"
            + "  {\n"
            + "    \"facilityNum\": 9610,\n"
            + "    \"facilityCountryCode\": \"US\",\n"
            + "    \"uom\": \"ZA\",\n"
            + "    \"nosOfDay\": 5,\n"
            + "    \"frequencyFactor\": 2,\n"
            + "    \"osdrPOBatchSize\": 3\n"
            + "  }\n"
            + "]";

    doReturn(MockEndgameReceipt.getReceiptsForOSDRDetails())
        .when(receiptService)
        .getReceiptSummary(any(), any(), any());

    when(osdrConfig.getSpecifications()).thenReturn(osdrConfigs);
    when(endGameDeliveryMetaDataService.findAllByDeliveryNumber(anyList()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    doReturn(MockOsdrSummary.getOsdrSummary())
        .when(endGameOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    doNothing().when(endgameDeliveryStatusPublisher).publishMessage(any(), any());
    OSDRRequest request = new OSDRRequest();
    List<String> delNos = new ArrayList<>();
    delNos.add("123");
    request.setDeliveryNos(delNos);
    endGameOsdrProcessor.processOSDR(request);
    verify(endGameOsdrService, times(2)).getOsdrDetails(anyLong(), any(), anyString(), anyString());
    verify(osdrConfig, times(0)).getFrequencyIntervalInMinutes();
    verify(osdrConfig, times(0)).getPageSize();
  }

  @Test
  public void testProcessOSDRByPOs() throws ReceivingException {
    String osdrConfigs =
        "[\n"
            + "  {\n"
            + "    \"facilityNum\": 9610,\n"
            + "    \"facilityCountryCode\": \"US\",\n"
            + "    \"uom\": \"ZA\",\n"
            + "    \"nosOfDay\": 5,\n"
            + "    \"frequencyFactor\": 2,\n"
            + "    \"osdrPOBatchSize\": 3\n"
            + "  }\n"
            + "]";

    doReturn(MockEndgameReceipt.getReceiptsForOSDRDetails())
        .when(receiptService)
        .getReceiptSummary(any());
    doReturn(MockEndgameReceipt.getReceiptsForOSDRDetails())
        .when(receiptService)
        .getReceiptSummary(any(), any(), any());

    when(osdrConfig.getSpecifications()).thenReturn(osdrConfigs);
    when(endGameDeliveryMetaDataService.findAllByDeliveryNumber(anyList()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    doReturn(MockOsdrSummary.getOsdrSummary())
        .when(endGameOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    doNothing().when(endgameDeliveryStatusPublisher).publishMessage(any(), any());
    OSDRRequest request = new OSDRRequest();
    List<String> delNos = new ArrayList<>();
    delNos.add("123");
    request.setPoNos(delNos);
    endGameOsdrProcessor.processOSDR(request);
    verify(endGameOsdrService, times(2)).getOsdrDetails(anyLong(), any(), anyString(), anyString());
    verify(osdrConfig, times(0)).getFrequencyIntervalInMinutes();
    verify(osdrConfig, times(0)).getPageSize();
  }

  @Test
  public void testPoReceipt() {
    List<Receipt> receipts = MockEndgameReceipt.getReceiptsForOSDRDetails();
    receipts.get(0).setDeliveryNumber(DEFAULT_DELIVERY_NUMBER);
    doReturn(receipts).when(receiptService).fetchPoReceipts(any(), any());
    doReturn(PoReceipt.builder().build()).when(endGameOsdrService).generatePoReceipt(any(), any());
    endGameOsdrProcessor.processPoReceipt(LocalDateTime.now(), LocalDateTime.now());
    verify(endgameOutboxHandler, times(1)).sentToOutbox(any(), anyString(), any());
  }
}
