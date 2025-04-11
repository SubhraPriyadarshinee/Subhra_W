package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.Mockito.reset;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.ArrayList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WFSDeliveryMetaDataServiceTest extends ReceivingTestBase {
  @InjectMocks private WFSDeliveryMetaDataService wfsDeliveryMetaDataService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        wfsDeliveryMetaDataService, "deliveryMetaDataRepository", deliveryMetaDataRepository);
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryMetaDataRepository);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testFindAndUpdateForOsdrProcessing() {
    wfsDeliveryMetaDataService.findAndUpdateForOsdrProcessing(1, 1L, 1, null);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testUpdateAuditInfo() {
    wfsDeliveryMetaDataService.updateAuditInfo(null, null);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testUpdateDeliveryMetaDataForItemOverrides() {
    wfsDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        DeliveryMetaData.builder().build(), "", "", "");
  }

  @Test
  public void testFindAndUpdateDeliveryStatus() {
    // dummy method, only logs that it has been called, so no test to cover it
    wfsDeliveryMetaDataService.findAndUpdateDeliveryStatus("", DeliveryStatus.SYS_REO);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testUpdateAuditInfoInDeliveryMetaData() {
    wfsDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(new ArrayList<>(), 1, 1L);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testGetReceivedQtyFromMetadata() {
    wfsDeliveryMetaDataService.getReceivedQtyFromMetadata(1234L, 1L);
  }
}
