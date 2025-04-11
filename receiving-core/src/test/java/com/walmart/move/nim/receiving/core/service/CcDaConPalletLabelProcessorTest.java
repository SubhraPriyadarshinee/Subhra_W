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

public class CcDaConPalletLabelProcessorTest extends ReceivingTestBase {
  @InjectMocks CcDaConPalletLabelProcessor ccDaConPalletLabelProcessor;
  @Mock ContainerService containerService;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForPalletLabel danonConContainer = daConContainerMetaData();
    danonConContainer.setCreateUser("sysadmin");
    String labelData = null;
    try {
      labelData = ccDaConPalletLabelProcessor.populateLabelData(getTemplate(), danonConContainer);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForPalletLabel danonConContainer = daConContainerMetaData();
    danonConContainer.setCreateUser(null);
    String labelData = null;
    try {
      labelData = ccDaConPalletLabelProcessor.populateLabelData(getTemplate(), danonConContainer);
    } catch (Exception e) {
      fail();
    } finally {
      danonConContainer.setCreateUser("sysadmin");
    }
    assertNull(labelData);
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        "{\"labelIdentifier\": \"${trackingId}\","
            + "\"formatName\": \"pallet_lpn_format\","
            + "\"data\": "
            + "[{\"key\": \"LPN\",\"value\": \"${trackingId}\"},"
            + "{\"key\": \"TYPE\",\"value\": \"DA\"},"
            + "{\"key\": \"DESTINATION\",\"value\": \"${destination}\"},"
            + "{\"key\": \"ITEM\",\"value\": \"${itemNumber}\"},"
            + "{\"key\": \"UPCBAR\",\"value\": \"${gtin}\"},"
            + "{\"key\": \"DESC1\",\"value\": \"${description}\"},"
            + "{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},"
            + "{\"key\": \"ORIGIN\",\"value\": \"${origin}\"},"
            + "{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},"
            + "{\"key\": \"DOOR\",\"value\": \"${location}\"},"
            + "{\"key\": \"QTY\",\"value\": \"${qty}\"}],\"ttlInHours\": 72.0}");
  }

  private ContainerMetaDataForPalletLabel daConContainerMetaData() {
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
    containerMetaDataForPalletLabel.setQuantity(3);
    containerMetaDataForPalletLabel.setVnpkQty(2);
    containerMetaDataForPalletLabel.setNoOfChildContainers(2);
    return containerMetaDataForPalletLabel;
  }

  @Test
  public void testGetContainersMetaDataByTrackingIds() {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    ContainerMetaDataForPalletLabel containerMetaDataForPalletLabel =
        new ContainerMetaDataForPalletLabel(
            "123456", 456789L, "40D", MockContainer.getDestinationInfo(), "sysadmin");
    ContainerMetaDataForPalletLabel containerItemMetaDataForPalletLabel =
        new ContainerMetaDataForPalletLabel(
            "123456", 98765L, "3647747392921", "dummy dacon pallet", 2);

    when(containerService.getContainerMetaDataForPalletLabelByTrackingIds(any()))
        .thenReturn(Arrays.asList(containerMetaDataForPalletLabel));
    when(containerService.getContainerItemMetaDataForPalletLabelByTrackingIds(any()))
        .thenReturn(Arrays.asList(containerItemMetaDataForPalletLabel));
    containerMetaDataMap.putAll(
        ccDaConPalletLabelProcessor.getContainersMetaDataByTrackingIds(Arrays.asList("123455")));
    assertEquals(1, containerMetaDataMap.size());
  }
}
