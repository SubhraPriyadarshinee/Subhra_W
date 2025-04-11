package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import freemarker.template.Template;
import java.io.IOException;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CcPbylLabelDataProcessorTest extends ReceivingTestBase {
  @InjectMocks CcPbyllabelProcessor ccPbylDataProcessor;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForPalletLabel pbylContainer = pbylContainerMetaData();
    String labelData = null;
    try {
      labelData = ccPbylDataProcessor.populateLabelData(getTemplate(), pbylContainer);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForPalletLabel pbylContainer = pbylContainerMetaData();
    String labelData = null;
    pbylContainer.setCreateUser(null);
    try {
      labelData = ccPbylDataProcessor.populateLabelData(getTemplate(), pbylContainer);
    } catch (Exception e) {
      fail();
    } finally {
      pbylContainer.setCreateUser("sysadmin");
    }
    assertNull(labelData);
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        ""
            + "{\"labelIdentifier\": \"${trackingId}\","
            + "\"formatName\": \"pallet_lpn_format\","
            + "\"data\": [{\"key\": \"LPN\",\"value\": \"${trackingId}\"},"
            + "{\"key\": \"TYPE\",\"value\": \"PBYL\"},"
            + "{\"key\": \"DESTINATION\",\"value\": \"\"},"
            + "{\"key\": \"ITEM\",\"value\": \"${itemNumber}\"},"
            + "{\"key\": \"UPCBAR\",\"value\": \"${gtin}\"},"
            + "{\"key\": \"DESC1\",\"value\": \"${description}\"},"
            + "{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},"
            + "{\"key\": \"ORIGIN\",\"value\": \"${origin}\"},"
            + "{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},"
            + "{\"key\": \"DOOR\",\"value\": \"${location}\"}],"
            + "\"ttlInHours\": 72.0}");
  }

  private ContainerMetaDataForPalletLabel pbylContainerMetaData() {
    ContainerMetaDataForPalletLabel containerMetaDataForPalletLabel =
        new ContainerMetaDataForPalletLabel();
    containerMetaDataForPalletLabel.setTrackingId("1234567abcde");
    containerMetaDataForPalletLabel.setDestination(null);
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
