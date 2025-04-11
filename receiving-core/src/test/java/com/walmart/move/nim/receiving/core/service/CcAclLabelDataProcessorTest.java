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
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForCaseLabel;
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

public class CcAclLabelDataProcessorTest extends ReceivingTestBase {
  @InjectMocks CcAclLabelDataProcessor ccAclDataProcessor;
  @Mock ContainerService containerService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForCaseLabel childContainer = caseContainerMetaData();
    String labelData = null;
    try {
      labelData = ccAclDataProcessor.populateLabelData(getTemplate(), childContainer);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForCaseLabel caseContainer = caseContainerMetaData();
    caseContainer.setCreateUser(null);
    String labelData = null;
    try {
      labelData = ccAclDataProcessor.populateLabelData(getTemplate(), caseContainer);
    } catch (Exception e) {
      fail();
    }
    assertNull(labelData);
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        "{\"labelIdentifier\": \"${trackingId}\","
            + "\"formatName\": \"case_lpn_format\","
            + "\"data\": ["
            + "{\"key\": \"LPN\",\"value\": \"${trackingId}\"},"
            + "{\"key\": \"DESTINATION\",\"value\": \"${destination}\"},"
            + "{\"key\": \"ITEM\",\"value\": \"${itemNumber}\"},"
            + "{\"key\": \"QTY\",\"value\": \"${qty}\"},"
            + "{\"key\": \"UPCBAR\",\"value\": \"${gtin}\"},"
            + "{\"key\": \"DESC1\",\"value\": \"${description}\"},"
            + "{\"key\": \"DESC2\",\"value\": \"${secondaryDescription}\"},"
            + "{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},"
            + "{\"key\": \"PACK\",\"value\": \"${vnpk}\"},"
            + "{\"key\": \"SIZE\",\"value\": \"${size}\"},"
            + "{\"key\": \"COLOR\",\"value\": \"${color}\"},"
            + "{\"key\": \"POCODE\",\"value\": \"${pocode}\"},"
            + "{\"key\": \"PO\",\"value\": \"${purchaseReferenceNumber}\"},"
            + "{\"key\": \"VENDORID\",\"value\": \"${vendorId}\"},"
            + "{\"key\": \"POLINE\",\"value\": \"${purchaseReferenceLine}\"},"
            + "{\"key\": \"POEVENT\",\"value\": \"${poevent}\"},"
            + "{\"key\": \"REPRINT\",\"value\": \"\"},"
            + "{\"key\": \"HAZMAT\",\"value\": \"${isHazmat}\"},"
            + "{\"key\": \"STOREZONE\",\"value\": \"${storezone}\"},"
            + "{\"key\": \"EVENTCHAR\",\"value\": \" \"},"
            + "{\"key\": \"PRINTER\",\"value\": \"\"},"
            + "{ \"key\": \"CPQTY\",\"value\": \"${vnpk}\"},"
            + "{\"key\": \"DEPT\",\"value\": \"${dept}\"},"
            + "{\"key\": \"CHANNEL\",\"value\": \"DIST\"},"
            + "{\"key\": \"PACKTYPE\",\"value\": \"${packType}\"},"
            + "{\"key\": \"DSDC\",\"value\": \"\"},"
            + "{\"key\": \"ORIGIN\",\"value\": \"${origin}\"},"
            + "{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},"
            + "{\"key\": \"DOOR\",\"value\": \"${location}\"}],"
            + "\"ttlInHours\": 72.0}");
  }

  private ContainerMetaDataForCaseLabel caseContainerMetaData() {
    ContainerMetaDataForCaseLabel containerMetaDataForCaseLabel =
        new ContainerMetaDataForCaseLabel();
    containerMetaDataForCaseLabel.setTrackingId("1234567abcde");
    containerMetaDataForCaseLabel.setDestination(MockContainer.getDestinationInfo());
    containerMetaDataForCaseLabel.setItemNumber(12345L);
    containerMetaDataForCaseLabel.setGtin("gtin12345");
    containerMetaDataForCaseLabel.setDescription("DaCon pallet");
    containerMetaDataForCaseLabel.setSecondaryDescription("DaCon pallet secondary Description");
    containerMetaDataForCaseLabel.setCreateUser("sysadmin");
    containerMetaDataForCaseLabel.setDeliveryNumber(98765432L);
    containerMetaDataForCaseLabel.setLocation("D47");
    containerMetaDataForCaseLabel.setVnpkQty(2);
    containerMetaDataForCaseLabel.setWhpkQty(2);
    containerMetaDataForCaseLabel.setPoDeptNumber("14");

    Map<String, String> miscInfo = new HashMap<>();
    miscInfo.put("size", "343435");
    miscInfo.put("pocode", "23");
    containerMetaDataForCaseLabel.setContainerItemMiscInfo(miscInfo);
    containerMetaDataForCaseLabel.setPurchaseReferenceNumber("324324324");
    containerMetaDataForCaseLabel.setPurchaseReferenceLineNumber(2);
    containerMetaDataForCaseLabel.setInboundChannelMethod("CROSSU");
    containerMetaDataForCaseLabel.setVendorNumber(null);
    return containerMetaDataForCaseLabel;
  }

  @Test
  public void testGetContainersMetaDataByTrackingIds() {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap();
    when(containerService.getContainerMetaDataForCaseLabelByTrackingIds(any()))
        .thenReturn(Arrays.asList(caseContainerMetaData()));
    containerMetaDataMap.putAll(
        ccAclDataProcessor.getContainersMetaDataByTrackingIds(Arrays.asList("1234556")));
    assertEquals(1, containerMetaDataMap.size());
  }
}
