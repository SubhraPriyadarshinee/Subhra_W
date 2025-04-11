package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForDockTagLabel;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import freemarker.template.Template;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CcDockTagLabelDataProcessorTest extends ReceivingTestBase {
  @InjectMocks CcDocktagLabelProcessor ccDockTagDataProcessor;
  @Mock ContainerService containerService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
  }

  @Test
  public void testPopulateLabelData_Success() {
    ContainerMetaDataForDockTagLabel docktag = docktagContainerMetaData();
    docktag.setCreateTs(new Date());
    String labelData = null;
    try {
      labelData = ccDockTagDataProcessor.populateLabelData(getTemplate(), docktag);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(labelData);
  }

  @Test
  public void testPopulateLabelData_ErrorWhileTemplating() {
    ContainerMetaDataForDockTagLabel docktag = docktagContainerMetaData();
    docktag.setLocation(null);
    docktag.setCreateTs(new Date());
    String labelData = null;
    try {
      labelData = ccDockTagDataProcessor.populateLabelData(getTemplate(), docktag);
    } catch (Exception e) {
      fail();
    } finally {
      docktag.setLocation("14B");
    }
    assertNull(labelData);
  }

  private Template getTemplate() throws IOException {
    return ReprintUtils.getTemplate(
        "{\"ttlInHours\": 72.0,"
            + "\"labelIdentifier\": \"${trackingId}\","
            + "\"data\": [{\"value\": \"${location}\","
            + "\"key\": \"DOOR\"},{\"value\": \"${date}\","
            + "\"key\": \"DATE\"},{\"value\": \"${trackingId}\","
            + "\"key\": \"LPN\"},{\"value\": \"${userId}\","
            + "\"key\": \"FULLUSERID\"},{\"value\": \"${deliveryNumber}\","
            + "\"key\": \"DELIVERYNBR\"},{\"value\": \"Floorline\","
            + "\"key\": \"DOCKTAGTYPE\"}],\"formatName\": \"dock_tag_atlas\"}");
  }

  private ContainerMetaDataForDockTagLabel docktagContainerMetaData() {
    ContainerMetaDataForDockTagLabel containerMetaDataForDockTagLabel =
        new ContainerMetaDataForDockTagLabel();
    containerMetaDataForDockTagLabel.setTrackingId("1234567abcde");
    containerMetaDataForDockTagLabel.setCreateTs(new Date());
    containerMetaDataForDockTagLabel.setCreateUser("sysadmin");
    containerMetaDataForDockTagLabel.setDeliveryNumber(98765432L);
    containerMetaDataForDockTagLabel.setLocation("D47");
    return containerMetaDataForDockTagLabel;
  }

  @Test
  public void testGetContainersMetaDataByTrackingIds() {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap();
    when(containerService.getContainerItemMetaDataForDockTagLabelByTrackingIds(any()))
        .thenReturn(Arrays.asList(docktagContainerMetaData()));
    containerMetaDataMap.putAll(
        ccDockTagDataProcessor.getContainersMetaDataByTrackingIds(Arrays.asList("1234556")));
    assertEquals(1, containerMetaDataMap.size());
  }
}
