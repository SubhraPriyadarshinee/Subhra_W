package com.walmart.move.nim.receiving.fixture.service;

import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import java.util.ArrayList;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FixtureDeliveryMetadataServiceTest extends ReceivingTestBase {

  @InjectMocks private FixtureDeliveryMetadataService fixtureDeliveryMetadataService;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testUpdateAuditInfo() {
    fixtureDeliveryMetadataService.updateAuditInfo(null, null);
  }

  @Test
  public void testUpdateDeliveryMetaDataForItemOverrides() {
    fixtureDeliveryMetadataService.updateDeliveryMetaDataForItemOverrides(
        DeliveryMetaData.builder().build(), "", "", "");
  }

  @Test
  public void testFindAndUpdateForOsdrProcessing() {
    fixtureDeliveryMetadataService.findAndUpdateForOsdrProcessing(1, 1L, 1, null);
  }

  @Test
  public void testUpdateAuditInfoInDeliveryMetaData() {
    boolean result =
        fixtureDeliveryMetadataService.updateAuditInfoInDeliveryMetaData(new ArrayList<>(), 1, 1L);
    assertFalse(result);
  }

  @Test
  public void testGetReceivedQtyFromMetadata() {
    assertEquals(fixtureDeliveryMetadataService.getReceivedQtyFromMetadata(1234L, 1L), 0);
  }
}
