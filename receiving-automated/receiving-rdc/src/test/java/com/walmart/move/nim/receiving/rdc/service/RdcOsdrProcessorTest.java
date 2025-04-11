package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsPageResponse;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.utils.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcOsdrProcessorTest {
  @InjectMocks private RdcOsdrProcessor rdcOsdrProcessor;
  @Mock private OsdrConfig osdrConfig;
  @Mock private RdcOsdrService rdcOsdrService;
  @Mock private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Mock RdcMessagePublisher rdcMessagePublisher;
  @Mock RdcManagedConfig rdcManagedConfig;
  @Mock private DeliveryServiceRetryableImpl deliveryService;

  private Gson gson = new Gson();
  File resource = null;
  OsdrSummary osdrSummary = null;

  @BeforeClass
  public void setRootUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(9610);
    ReflectionTestUtils.setField(
        rdcOsdrProcessor, "rdcDeliveryMetaDataService", rdcDeliveryMetaDataService);
    ReflectionTestUtils.setField(rdcOsdrProcessor, "osdrConfig", osdrConfig);

    resource = new ClassPathResource("OsdrReceiptsSummary.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        osdrConfig,
        rdcDeliveryMetaDataService,
        rdcOsdrService,
        rdcMessagePublisher,
        deliveryService);
  }

  @Test
  public void testProcess() throws ReceivingException {

    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());
    verify(rdcOsdrService, times(2)).getOsdrDetails(any(), any(), any(), any());
    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(rdcMessagePublisher, times(2)).publishDeliveryReceipts(any(), any());
  }

  @Test
  public void testProcess_NoOsdrDetailsForOneDelivery() throws ReceivingException {
    when(osdrConfig.getPageSize()).thenReturn(10);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(false);

    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.RECEIPTS_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants.OSDR_RECEIPTS_NOT_FOUND_ERROR_MSG, "12333334")))
        .when(rdcOsdrService)
        .getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(rdcOsdrService, times(2)).getOsdrDetails(any(), any(), any(), any());
    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(osdrConfig, times(1)).getPageSize();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
  }

  @Test
  public void testProcess_NoDeliveryToProcessOsdr() throws ReceivingException {
    when(osdrConfig.getPageSize()).thenReturn(10);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(new ArrayList<>());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(rdcOsdrService, times(0)).getOsdrDetails(anyLong(), any(), anyString(), anyString());
    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(osdrConfig, times(1)).getPageSize();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
  }

  @Test
  public void testProcess_DeliveryStatusCheckEnabledToSendOsdrSummary_AllEligibleDeliveries()
      throws ReceivingException, IOException {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryMetaDataForDeliveryStatusCheckAllEligibleDeliveries();
    when(osdrConfig.getPageSize()).thenReturn(50);
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(true);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(deliveryMetaDataList);
    when(deliveryService.getDeliveryHeaderDetailsByDeliveryNumbers(anyList()))
        .thenReturn(
            MockDeliveryMetaData
                .getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetAllEligible());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(rdcOsdrService, times(deliveryMetaDataList.size()))
        .getOsdrDetails(any(), any(), any(), any());
    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(rdcMessagePublisher, times(deliveryMetaDataList.size()))
        .publishDeliveryReceipts(any(), any());
    verify(deliveryService, times(1)).getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
  }

  @Test
  public void testProcess_DeliveryStatusCheckEnabledToSendOsdrSummary_PartiallyEligibleDeliveries()
      throws ReceivingException, IOException {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryMetaDataForDeliveryStatusCheckPartiallyEligibleDeliveries();
    when(osdrConfig.getPageSize()).thenReturn(50);
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(true);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(deliveryMetaDataList);
    when(deliveryService.getDeliveryHeaderDetailsByDeliveryNumbers(anyList()))
        .thenReturn(
            MockDeliveryMetaData
                .getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetPartiallyEligible());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(rdcMessagePublisher, times(30)).publishDeliveryReceipts(any(), any());
    verify(rdcOsdrService, times(30)).getOsdrDetails(any(), any(), any(), any());
    verify(deliveryService, times(1)).getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
  }

  @Test
  public void testProcess_DeliveryStatusCheckEnabledToSendOsdrSummary_MoreThan100DeliveriesOffset()
      throws ReceivingException, IOException {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryNumbersForMoreThan100DeliveriesOffset();
    when(osdrConfig.getPageSize()).thenReturn(100);
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(true);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(deliveryMetaDataList);
    when(deliveryService.getDeliveryHeaderDetailsByDeliveryNumbers(anyList()))
        .thenReturn(
            MockDeliveryMetaData
                .getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetPartiallyEligible());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(deliveryService, times(3)).getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
  }

  @Test
  public void testProcess_DeliveryStatusCheckEnabledGDMReturnsEmptyReceipts_DoNotPublishOsdr()
      throws ReceivingException, IOException {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryMetaDataForDeliveryStatusCheckPartiallyEligibleDeliveries();
    GdmDeliveryHeaderDetailsPageResponse response = new GdmDeliveryHeaderDetailsPageResponse();
    response.setData(new ArrayList<>());
    when(osdrConfig.getPageSize()).thenReturn(100);
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(true);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(deliveryMetaDataList);
    when(deliveryService.getDeliveryHeaderDetailsByDeliveryNumbers(anyList())).thenReturn(response);
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), any());
    verify(rdcOsdrService, times(0)).getOsdrDetails(any(), any(), any(), any());
    verify(deliveryService, times(1)).getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
  }

  @Test
  public void testProcess_DeliveryStatusCheckEnabledAndGDMThrowsError_DoNotPublishOsdr()
      throws ReceivingException, IOException {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryMetaDataForDeliveryStatusCheckPartiallyEligibleDeliveries();
    when(osdrConfig.getPageSize()).thenReturn(100);
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(true);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(deliveryMetaDataList);
    doThrow(
            new ReceivingException(
                ReceivingException.GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE))
        .when(deliveryService)
        .getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), any());
    verify(rdcOsdrService, times(0)).getOsdrDetails(any(), any(), any(), any());
    verify(deliveryService, times(1)).getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
  }

  @Test
  public void
      testProcess_DeliveryStatusCheckEnabledAndGDMThrowsErrorForMoreThan100DeliveriesOffSet_DoNotPublishOsdr()
          throws ReceivingException {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryNumbersForMoreThan100DeliveriesOffset();
    when(osdrConfig.getPageSize()).thenReturn(100);
    when(rdcManagedConfig.isDeliveryStatusCheckEnabled()).thenReturn(true);
    when(osdrConfig.getFrequencyIntervalInMinutes()).thenReturn(Integer.valueOf(240).longValue());
    when(rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            anyInt(), anyLong(), anyInt(), any()))
        .thenReturn(deliveryMetaDataList);
    doThrow(
            new ReceivingException(
                ReceivingException.GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE))
        .when(deliveryService)
        .getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
    doReturn(osdrSummary).when(rdcOsdrService).getOsdrDetails(any(), any(), any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), any());
    rdcOsdrProcessor.process(getOsdrConfigSpecification());

    verify(osdrConfig, times(1)).getFrequencyIntervalInMinutes();
    verify(rdcDeliveryMetaDataService, times(1))
        .findAndUpdateForOsdrProcessing(anyInt(), anyLong(), anyInt(), any());
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), any());
    verify(rdcOsdrService, times(0)).getOsdrDetails(any(), any(), any(), any());
    verify(deliveryService, times(3)).getDeliveryHeaderDetailsByDeliveryNumbers(anyList());
  }

  private OsdrConfigSpecification getOsdrConfigSpecification() {
    OsdrConfigSpecification osdrConfigSpecification = new OsdrConfigSpecification();
    osdrConfigSpecification.setFacilityNum(32818);
    osdrConfigSpecification.setFacilityCountryCode("US");
    osdrConfigSpecification.setUom(ReceivingConstants.Uom.VNPK);
    return osdrConfigSpecification;
  }
}
