package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MFCCompleteDeliveryProcessorTest extends ReceivingTestBase {

  @InjectMocks private MFCCompleteDeliveryProcessor mfcCompleteDeliveryProcessor;

  @Mock private AppConfig appConfig;
  @Mock private MFCManagedConfig mfcManagedConfig;
  @Mock protected DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock protected MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Mock protected MFCDeliveryService mfcDeliveryService;

  @Mock private ProcessInitiator processInitiator;

  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;

  public static final List<String> ELIGIBLE_STATUS_FOR_AUTO_COMPLETE =
      Arrays.asList("ARV", "OPN", "WRK");
  private Gson gson = new Gson();

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
    TenantContext.setFacilityNum(5505);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(appConfig);
    Mockito.reset(mfcManagedConfig);
    Mockito.reset(deliveryStatusPublisher);
    Mockito.reset(mfcDeliveryMetadataService);
    Mockito.reset(mfcDeliveryService);
    Mockito.reset(processInitiator);
    Mockito.reset(deliveryMetaDataRepository);
  }

  @Test
  public void testAutoCompleteDelivery_NoEligibleDeliveries() throws ReceivingException {
    when(mfcManagedConfig.getDeliveryAutoCompleteThresholdHours()).thenReturn(30);
    when(mfcDeliveryService.fetchDeliveries(any())).thenReturn(null);
    mfcCompleteDeliveryProcessor.autoCompleteDeliveries(5505);
  }

  @Test
  public void testAutoCompleteDelivery_EmptyResponse() throws ReceivingException {
    when(mfcManagedConfig.getDeliveryAutoCompleteThresholdHours()).thenReturn(30);
    when(mfcDeliveryService.fetchDeliveries(any())).thenReturn(DeliveryList.builder().build());
    mfcCompleteDeliveryProcessor.autoCompleteDeliveries(5505);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testAutoCompleteDelivery_GDMException() throws ReceivingException {
    when(mfcManagedConfig.getDeliveryAutoCompleteThresholdHours()).thenReturn(30);
    when(mfcDeliveryService.fetchDeliveries(any()))
        .thenThrow(
            new ReceivingException(
                ExceptionCodes.GDM_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingConstants.UNABLE_TO_GET_DELIVERY_FROM_GDM));
    mfcCompleteDeliveryProcessor.autoCompleteDeliveries(5505);
  }

  @Test
  public void testAutoCompleteDelivery_EligibleData() throws ReceivingException, IOException {
    when(mfcManagedConfig.getDeliveryAutoCompleteThresholdHours()).thenReturn(30);
    doCallRealMethod().when(mfcDeliveryMetadataService).findAndUpdateDeliveryStatus(any(), any());
    String response =
        FileUtils.readFileToString(
            new ClassPathResource("deliveryData/deliveryHeaderSearchResponse.json").getFile(),
            Charset.defaultCharset());
    Map<String, DeliveryStatus> deliveryStatusMap = new HashMap<>();
    deliveryStatusMap.put("55050000000009", DeliveryStatus.UNLOADING_COMPLETE);
    deliveryStatusMap.put("55050000000007", DeliveryStatus.ARV);
    when(mfcDeliveryMetadataService.findAllByDeliveryNumberIn(any()))
        .thenReturn(getDeliveryMetaData(deliveryStatusMap));
    when(mfcDeliveryService.fetchDeliveries(any()))
        .thenReturn(gson.fromJson(response, DeliveryList.class));
    when(deliveryMetaDataRepository.findByDeliveryNumber(any()))
        .thenReturn(
            Optional.of(
                DeliveryMetaData.builder()
                    .deliveryNumber("55050000000009")
                    .deliveryStatus(DeliveryStatus.UNLOADING_COMPLETE)
                    .build()));
    mfcCompleteDeliveryProcessor.autoCompleteDeliveries(5505);
    ArgumentCaptor<Long> unloadDeliveryArgumentCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> deliveryCompleteArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mfcDeliveryMetadataService, times(1))
        .findAndUpdateDeliveryStatus(deliveryCompleteArgumentCaptor.capture(), any());
    verify(mfcDeliveryService, times(2))
        .unloadComplete(
            unloadDeliveryArgumentCaptor.capture(),
            any(),
            any(),
            httpHeadersArgumentCaptor.capture());
    HttpHeaders headers = httpHeadersArgumentCaptor.getValue();
    assertNotNull(headers);
    assertEquals(
        Arrays.asList(ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID),
        headers.get(USER_ID_HEADER_KEY));
    List<Long> unloadDelivery = unloadDeliveryArgumentCaptor.getAllValues();
    assertNotNull(unloadDelivery);
    List<Long> expectedDeliveryForUnloadComplete = Arrays.asList(55050000000007l, 55050000000008l);
    unloadDelivery.forEach(
        delivery -> assertTrue(expectedDeliveryForUnloadComplete.contains(delivery)));
    List<String> deliveryEligibleForDeliveryComplete =
        deliveryCompleteArgumentCaptor.getAllValues();
    assertEquals(1, deliveryEligibleForDeliveryComplete.size());
    assertTrue(deliveryEligibleForDeliveryComplete.contains("55050000000009"));
  }

  @Test
  public void testAutoCompleteDelivery_WithInvalidDocumentTypes()
      throws ReceivingException, IOException {
    when(mfcManagedConfig.getDeliveryAutoCompleteThresholdHours()).thenReturn(30);
    doCallRealMethod().when(mfcDeliveryMetadataService).findAndUpdateDeliveryStatus(any(), any());
    String response =
        FileUtils.readFileToString(
            new ClassPathResource(
                    "deliveryData/deliveryHeaderSearchResponseInvalidDocumentType.json")
                .getFile(),
            Charset.defaultCharset());
    Map<String, DeliveryStatus> deliveryStatusMap = new HashMap<>();
    deliveryStatusMap.put("55050000000009", DeliveryStatus.UNLOADING_COMPLETE);
    when(mfcDeliveryMetadataService.findAllByDeliveryNumberIn(any()))
        .thenReturn(getDeliveryMetaData(deliveryStatusMap));
    when(mfcDeliveryService.fetchDeliveries(any()))
        .thenReturn(gson.fromJson(response, DeliveryList.class));
    when(deliveryMetaDataRepository.findByDeliveryNumber(any()))
        .thenReturn(
            Optional.of(
                DeliveryMetaData.builder()
                    .deliveryNumber("55050000000009")
                    .deliveryStatus(DeliveryStatus.UNLOADING_COMPLETE)
                    .build()));
    mfcCompleteDeliveryProcessor.autoCompleteDeliveries(5505);
    ArgumentCaptor<String> deliveryCompleteArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mfcDeliveryMetadataService, times(1))
        .findAndUpdateDeliveryStatus(deliveryCompleteArgumentCaptor.capture(), any());
    verify(mfcDeliveryService, never()).unloadComplete(anyLong(), any(), any(), any());
    List<String> deliveryEligibleForDeliveryComplete =
        deliveryCompleteArgumentCaptor.getAllValues();
    assertEquals(1, deliveryEligibleForDeliveryComplete.size());
    assertTrue(deliveryEligibleForDeliveryComplete.contains("55050000000009"));
  }

  @Test
  public void testAutoCompleteDelivery_NoDeliveryMetaData() throws ReceivingException, IOException {
    when(mfcManagedConfig.getDeliveryAutoCompleteThresholdHours()).thenReturn(30);
    String response =
        FileUtils.readFileToString(
            new ClassPathResource("deliveryData/deliveryHeaderSearchResponse.json").getFile(),
            Charset.defaultCharset());
    doReturn(Collections.emptyList())
        .when(mfcDeliveryMetadataService)
        .findAllByDeliveryNumberIn(any());
    doReturn(gson.fromJson(response, DeliveryList.class))
        .when(mfcDeliveryService)
        .fetchDeliveries(any());
    mfcCompleteDeliveryProcessor.autoCompleteDeliveries(5505);
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);
    verify(mfcDeliveryService, times(3))
        .unloadComplete(anyLong(), any(), any(), httpHeadersArgumentCaptor.capture());
    verify(mfcDeliveryMetadataService, never()).findAndUpdateDeliveryStatus(any(), any());
    HttpHeaders headers = httpHeadersArgumentCaptor.getValue();
    assertNotNull(headers);
    assertEquals(
        Arrays.asList(ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID),
        headers.get(USER_ID_HEADER_KEY));
  }

  @Test
  public void testDeliveryCompleteWithUnloadComplete() throws ReceivingException {
    Long deliveryNumber = 55050000000009L;
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("5504", "US");
    DeliveryInfo deliveryInfoUc = new DeliveryInfo();
    deliveryInfoUc.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE.name());
    deliveryInfoUc.setDeliveryNumber(deliveryNumber);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(any())).thenReturn(Optional.empty());
    when(mfcDeliveryService.unloadComplete(anyLong(), any(), any(), any()))
        .thenReturn(deliveryInfoUc);
    DeliveryInfo deliveryInfo =
        mfcCompleteDeliveryProcessor.completeDelivery(55050000000009L, true, httpHeaders);
    verify(mfcDeliveryService, times(1)).unloadComplete(anyLong(), any(), any(), any());
    assertNotNull(deliveryInfo);
    assertEquals(55050000000009L, deliveryInfo.getDeliveryNumber().longValue());
    assertEquals(DeliveryStatus.UNLOADING_COMPLETE.name(), deliveryInfo.getDeliveryStatus());
  }

  @Test
  public void testDeliveryCompleteWithoutUnloadComplete() throws ReceivingException {
    Long deliveryNumber = 55050000000009L;
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("5504", "US");
    DeliveryInfo deliveryInfoUc = new DeliveryInfo();
    deliveryInfoUc.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE.name());
    deliveryInfoUc.setDeliveryNumber(deliveryNumber);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(any())).thenReturn(Optional.empty());
    when(mfcDeliveryService.unloadComplete(anyLong(), any(), any(), any()))
        .thenReturn(deliveryInfoUc);
    doNothing().when(mfcDeliveryMetadataService).findAndUpdateDeliveryStatus(any(), any());
    DeliveryInfo deliveryInfo =
        mfcCompleteDeliveryProcessor.completeDelivery(55050000000009L, false, httpHeaders);
    verify(mfcDeliveryService, never()).unloadComplete(anyLong(), any(), any(), any());
    assertNotNull(deliveryInfo);
    assertEquals(55050000000009L, deliveryInfo.getDeliveryNumber().longValue());
    assertEquals(DeliveryStatus.COMPLETE.name(), deliveryInfo.getDeliveryStatus());
  }

  private List<DeliveryMetaData> getDeliveryMetaData(
      Map<String, DeliveryStatus> deliveryStatusMap) {
    List<DeliveryMetaData> deliveryMetaData = new ArrayList<>();
    deliveryStatusMap.forEach(
        (deliveryNum, status) -> {
          deliveryMetaData.add(
              DeliveryMetaData.builder()
                  .deliveryNumber(deliveryNum)
                  .deliveryStatus(status)
                  .build());
        });
    return deliveryMetaData;
  }
}
