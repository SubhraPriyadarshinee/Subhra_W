package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.UnloaderInfo;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.repositories.UnloaderInfoRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcDeliveryUnloaderProcessorTest extends ReceivingTestBase {

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private UnloaderInfoRepository unloaderInfoRepository;
  @InjectMocks private GdcDeliveryUnloaderProcessor gdcDeliveryUnloaderProcessor;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setUpTestDataBeforeEachTest() {}

  @AfterMethod
  public void tearDown() {
    reset(deliveryStatusPublisher);
    reset(unloaderInfoRepository);
  }

  @Test
  public void test_publishDeliveryEvent_postive() {
    doReturn("UNLOAD_START,UNLOAD_STOP")
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());
    gdcDeliveryUnloaderProcessor.publishDeliveryEvent(
        12345L, "UNLOAD_START", MockHttpHeaders.getHeaders());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), eq(null), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_publishDeliveryEvent_negative() {
    doReturn("UNLOAD_START,UNLOAD_STOP")
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());
    doThrow(new NullPointerException())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), eq(null), anyMap());
    gdcDeliveryUnloaderProcessor.publishDeliveryEvent(
        12345L, "UNLOAD_START", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_publishDeliveryEvent_negative_invalidEventtype() {
    doReturn("UNLOAD_OPEN,UNLOAD_WORKING,UNLOAD_CLOSE")
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());
    gdcDeliveryUnloaderProcessor.publishDeliveryEvent(
        12345L, "UNLOAD_START", MockHttpHeaders.getHeaders());
  }

  @Test
  public void test_saveUnloaderInfo_postive() {
    gdcDeliveryUnloaderProcessor.saveUnloaderInfo(getUnloaderInfo(), MockHttpHeaders.getHeaders());
    verify(unloaderInfoRepository, times(1)).save(any(UnloaderInfo.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_saveUnloaderInfo_negative() {
    RuntimeException mockException = new RuntimeException();
    doThrow(mockException).when(unloaderInfoRepository).save(any(UnloaderInfo.class));
    gdcDeliveryUnloaderProcessor.saveUnloaderInfo(getUnloaderInfo(), MockHttpHeaders.getHeaders());
  }

  @Test
  public void test_getUnloaderInfo_postive1() {
    gdcDeliveryUnloaderProcessor.getUnloaderInfo(123L, "345", 2);
    verify(unloaderInfoRepository, times(1))
        .findByDeliveryNumberAndFacilityCountryCodeAndFacilityNumAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt(), anyString(), anyInt());
  }

  @Test
  public void test_getUnloaderInfo_postive2() {
    gdcDeliveryUnloaderProcessor.getUnloaderInfo(123L, "", 2);
    verify(unloaderInfoRepository, times(1))
        .findByDeliveryNumberAndFacilityCountryCodeAndFacilityNum(anyLong(), anyString(), anyInt());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_getUnloaderInfo_negative() {
    RuntimeException mockException = new RuntimeException();
    doThrow(mockException)
        .when(unloaderInfoRepository)
        .findByDeliveryNumberAndFacilityCountryCodeAndFacilityNum(anyLong(), anyString(), anyInt());
    gdcDeliveryUnloaderProcessor.getUnloaderInfo(123L, "", 2);
  }

  private static UnloaderInfoDTO getUnloaderInfo() {
    UnloaderInfoDTO unloaderInfoToDb = new UnloaderInfoDTO();
    unloaderInfoToDb.setDeliveryNumber(123456L);
    unloaderInfoToDb.setPurchaseReferenceNumber("2345");
    unloaderInfoToDb.setPurchaseReferenceLineNumber(2);
    unloaderInfoToDb.setItemNumber(98765L);
    unloaderInfoToDb.setActualHi(5);
    unloaderInfoToDb.setActualTi(5);
    unloaderInfoToDb.setFbq(1000);
    unloaderInfoToDb.setCaseQty(200);
    unloaderInfoToDb.setPalletQty(20);
    unloaderInfoToDb.setUnloadedFullFbq(false);
    unloaderInfoToDb.setOrgUnitId(2);
    return unloaderInfoToDb;
  }
}
