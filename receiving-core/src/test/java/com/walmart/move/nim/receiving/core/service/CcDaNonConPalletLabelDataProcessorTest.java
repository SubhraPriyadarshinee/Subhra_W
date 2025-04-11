package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import freemarker.template.Template;
import java.io.IOException;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CcDaNonConPalletLabelDataProcessorTest extends ReceivingTestBase {

  @InjectMocks CcDaNonConPalletLabelProcessor ccDaNonConPalletLabelProcessor;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForPalletLabel danonConContainer = danonconContainerMetaData();

    String labelData = null;
    try {
      labelData =
          ccDaNonConPalletLabelProcessor.populateLabelData(getTemplate(), danonConContainer);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForPalletLabel daConContainer = danonconContainerMetaData();
    String labelData = null;
    daConContainer.setCreateUser(null);
    try {
      labelData = ccDaNonConPalletLabelProcessor.populateLabelData(getTemplate(), daConContainer);
    } catch (Exception e) {
      fail();
    }
    assertNull(labelData);
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        "{\"labelIdentifier\": \"${trackingId}\","
            + "\"formatName\": \"pallet_lpn_format\","
            + "\"data\": [{\"key\": \"LPN\",\"value\": \"${trackingId}\"},"
            + "{\"key\": \"TYPE\",\"value\": \"DA_NC\"},"
            + "{\"key\": \"DESTINATION\",\"value\": \"${destination}\"},"
            + "{\"key\": \"ITEM\",\"value\": \"${itemNumber}\"},"
            + "{\"key\": \"UPCBAR\",\"value\": \"${gtin}\"},"
            + "{\"key\": \"DESC1\",\"value\": \"${description}\"},"
            + "{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},"
            + "{\"key\": \"ORIGIN\",\"value\": \"${origin}\"},"
            + "{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},"
            + "{\"key\": \"DOOR\",\"value\": \"${location}\"},"
            + "{\"key\": \"QTY\",\"value\": \"${qty}\"}],"
            + "\"ttlInHours\": 72.0}");
  }

  private ContainerMetaDataForPalletLabel danonconContainerMetaData() {
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
    return containerMetaDataForPalletLabel;
  }
}
