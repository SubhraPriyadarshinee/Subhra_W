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
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForNonNationalPoLabel;
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

public class CcNonNationPalletLabelDataProcessorTest extends ReceivingTestBase {

  @InjectMocks CcNonNationalLabelDataProcessor ccNonNationalLabelDataProcessor;
  @Mock ContainerService containerService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForNonNationalPoLabel nonNationalContainer = getNonNationalContainerMetaData();
    String labelData = null;
    try {
      labelData =
          ccNonNationalLabelDataProcessor.populateLabelData(getTemplate(), nonNationalContainer);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForNonNationalPoLabel nonNationalPOContainer =
        getNonNationalContainerMetaData();
    nonNationalPOContainer.setPoList(null);
    String labelData = null;
    try {
      labelData =
          ccNonNationalLabelDataProcessor.populateLabelData(getTemplate(), nonNationalPOContainer);
      fail();
    } catch (Exception e) {
      assertNull(labelData);
    }
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        "{\"labelIdentifier\": \"${trackingId}\", "
            + "\"formatName\": \"non_national_lable_format\", "
            + "\"data\": ["
            + "{ \"key\": \"LPN\", \"value\": \"${trackingId}\" },  "
            + "{\"key\": \"TYPE\", \"value\": \"${outboundChannelMethod}\"},"
            + "{\"key\": \"DESTINATION\",\"value\": \"${destination}\"},"
            + "{\"key\": \"ITEM\",\"value\": \"\" },"
            + "{\"key\": \"UPCBAR\",\"value\": \"\" },"
            + "{\"key\": \"DESC1\",\"value\": \"\" },"
            + "{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},"
            + "{\"key\": \"ORIGIN\", \"value\": \"${origin}\"},"
            + "{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},"
            + "{\"key\": \"DOOR\", \"value\": \"${location}\" },"
            + "{\"key\": \"MULTIPO\", \"value\": \"${multipo}\"},"
            + "{\"key\": \"PO1\", \"value\": \"${po1}\"},"
            + "{\"key\": \"PO2\", \"value\": \"${po2}\"},"
            + "{\"key\": \"PO3\", \"value\": \"${po3}\"},"
            + "{\"key\": \"ELLIPSIS\",\"value\": \"\"},"
            + "{\"key\": \"QTY\",\"value\": \"${qty}\"},"
            + "{\"key\": \"CHANNELMETHOD\",\"value\": \"${channelMethod}\"}],"
            + "\"ttlInHours\": 72.0 }");
  }

  private ContainerMetaDataForNonNationalPoLabel getNonNationalContainerMetaData() {
    ContainerMetaDataForNonNationalPoLabel containerMetaDataForNonNationalLabel =
        new ContainerMetaDataForNonNationalPoLabel();
    containerMetaDataForNonNationalLabel.setTrackingId("1234567abcde");
    containerMetaDataForNonNationalLabel.setDestination(MockContainer.getDestinationInfo());
    containerMetaDataForNonNationalLabel.setOutboundChannelMethod("POCON");
    containerMetaDataForNonNationalLabel.setCreateUser("sysadmin");
    containerMetaDataForNonNationalLabel.setDeliveryNumber(98765432L);
    containerMetaDataForNonNationalLabel.setLocation("D47");
    Map<String, String> miscInfo = new HashMap<>();
    miscInfo.put("originalChannelMethod", "DA");
    containerMetaDataForNonNationalLabel.setPurchaseReferenceNumber("454354545");
    containerMetaDataForNonNationalLabel.setPoList(Arrays.asList("12345", "456767", "987669"));
    containerMetaDataForNonNationalLabel.setQuantity(3);
    return containerMetaDataForNonNationalLabel;
  }

  @Test
  public void testGetContainersMetaDataByTrackingIds() {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    ContainerMetaDataForNonNationalPoLabel containerMetaDataForNonNationalPoLabel2 =
        getNonNationalContainerMetaData();
    containerMetaDataForNonNationalPoLabel2.setPurchaseReferenceNumber("56789");
    when(containerService.getContainerItemMetaDataForNonNationalLabelByTrackingIds(any()))
        .thenReturn(
            Arrays.asList(
                getNonNationalContainerMetaData(), containerMetaDataForNonNationalPoLabel2));
    containerMetaDataMap.putAll(
        ccNonNationalLabelDataProcessor.getContainersMetaDataByTrackingIds(
            Arrays.asList("123455")));
    assertEquals(1, containerMetaDataMap.size());
  }
}
