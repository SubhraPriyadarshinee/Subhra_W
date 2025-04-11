package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import freemarker.template.Template;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CcSstkLabelDataProcessorTest extends ReceivingTestBase {
  @InjectMocks CcSstkLabelProcessor ccSstkDataProcessor;
  @Mock ContainerService containerService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForPalletLabel sstkContainer = sstkContainerMetaData();
    String labelData = null;
    try {
      labelData = ccSstkDataProcessor.populateLabelData(getTemplate(), sstkContainer);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForPalletLabel sstkContainer = sstkContainerMetaData();
    sstkContainer.setCreateUser(null);
    String labelData = null;
    try {
      labelData = ccSstkDataProcessor.populateLabelData(getTemplate(), sstkContainer);
    } catch (Exception e) {
      fail();
    } finally {
      sstkContainer.setCreateUser("sysadmin");
    }
    assertNull(labelData);
  }

  @Test
  public void testGetContainersMetaDataByTrackingIds() {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    when(containerService.getContainerAndContainerItemMetaDataForPalletLabelByTrackingIds(any()))
        .thenReturn(Arrays.asList(sstkContainerMetaData()));
    containerMetaDataMap.putAll(
        ccSstkDataProcessor.getContainersMetaDataByTrackingIds(Arrays.asList("12345")));
    assertEquals(containerMetaDataMap.size(), 1);
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        "{\"labelIdentifier\": \"${trackingId}\","
            + "\"formatName\": \"pallet_lpn_format\","
            + "\"data\":[{\"key\": \"LPN\",\"value\": \"${trackingId}\"},"
            + "{\"key\": \"TYPE\",\"value\": \"SSTK\"},"
            + "{\"key\": \"DESTINATION\",\"value\": \"${destination}\"},"
            + "{\"key\": \"ITEM\",\"value\": \"${itemNumber}\"},"
            + "{\"key\": \"UPCBAR\",\"value\": \"${gtin}\"},"
            + "{\"key\": \"DESC1\",\"value\": \"${description}\"},"
            + "{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},"
            + "{\"key\": \"ORIGIN\",\"value\": \"${origin}\"},"
            + "{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},"
            + "{\"key\": \"DOOR\",\"value\": \"${location}\"}],"
            + "\"ttlInHours\": 72.0}");
  }

  private ContainerMetaDataForPalletLabel sstkContainerMetaData() {
    ContainerMetaDataForPalletLabel containerMetaDataForPalletLabel =
        new ContainerMetaDataForPalletLabel();
    containerMetaDataForPalletLabel.setTrackingId("1234567abcde");
    containerMetaDataForPalletLabel.setDestination(MockContainer.getDestinationInfo());
    containerMetaDataForPalletLabel.setItemNumber(12345L);
    containerMetaDataForPalletLabel.setGtin("gtin12345");
    containerMetaDataForPalletLabel.setDescription("DaCon pallet");
    containerMetaDataForPalletLabel.setCreateUser("sysadmin");
    containerMetaDataForPalletLabel.setDeliveryNumber(98765432L);
    containerMetaDataForPalletLabel.setLocation("D47");
    containerMetaDataForPalletLabel.setQuantity(4);
    containerMetaDataForPalletLabel.setVnpkQty(2);
    return containerMetaDataForPalletLabel;
  }
}
