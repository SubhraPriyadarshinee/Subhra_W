package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForCaseLabel;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForDockTagLabel;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForNonNationalPoLabel;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
import com.walmart.move.nim.receiving.core.model.LabelIdAndTrackingIdPair;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ContainerMetaDataForPrintLabelTest extends ReceivingTestBase {
  @Autowired ContainerRepository containerRepository;
  @Autowired ContainerItemRepository containerItemRepository;
  @InjectMocks ContainerService containerService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
    TenantContext.setFacilityCountryCode("US");
    ReflectionTestUtils.setField(
        containerService, "containerItemRepository", containerItemRepository);
    ReflectionTestUtils.setField(containerService, "containerRepository", containerRepository);
    setUpData();
  }

  @AfterClass
  public void cleanUp() {
    containerRepository.deleteAll();
    containerItemRepository.deleteAll();
  }

  @Test
  public void testGetContainerMetaDataForCaseLabelByTrackingIds() {
    List<ContainerMetaDataForCaseLabel> containerMetaDataList =
        containerService.getContainerMetaDataForCaseLabelByTrackingIds(
            Arrays.asList("12345", "67890"));
    Assert.assertEquals(containerMetaDataList.size(), 2);
  }

  @Test
  public void testGetContainerMetaDataForPalletLabelByTrackingIds() {
    List<ContainerMetaDataForPalletLabel> containerMetaDataList =
        containerService.getContainerMetaDataForPalletLabelByTrackingIds(Arrays.asList("12345"));
    Assert.assertEquals(containerMetaDataList.size(), 1);
  }

  @Test
  public void testGetContainerItemMetaDataForPalletLabelByTrackingIds() {
    List<ContainerMetaDataForPalletLabel> containerMetaDataList =
        containerService.getContainerItemMetaDataForPalletLabelByTrackingIds(Arrays.asList("1234"));
    Assert.assertEquals(containerMetaDataList.size(), 1);
    Assert.assertEquals(containerMetaDataList.get(0).getNoOfChildContainers(), 2);
  }

  @Test
  public void testGetContainerAndContainerItemMetaDataForPalletLabelByTrackingIds() {
    List<ContainerMetaDataForPalletLabel> containerMetaDataList =
        containerService.getContainerAndContainerItemMetaDataForPalletLabelByTrackingIds(
            Arrays.asList("12345"));
    Assert.assertEquals(containerMetaDataList.size(), 1);
  }

  @Test
  public void getContainerItemMetaDataForNonNationalLabelByTrackingIds() {
    List<ContainerMetaDataForNonNationalPoLabel> containerMetaDataList =
        containerService.getContainerItemMetaDataForNonNationalLabelByTrackingIds(
            Arrays.asList("12345"));
    Assert.assertEquals(containerMetaDataList.size(), 1);
  }

  @Test
  public void testGetContainerItemMetaDataForDockTagLabelByTrackingIds() {
    List<ContainerMetaDataForDockTagLabel> containerMetaDataList =
        containerService.getContainerItemMetaDataForDockTagLabelByTrackingIds(
            Arrays.asList("12345"));
    Assert.assertEquals(containerMetaDataList.size(), 1);
  }

  @Test
  public void testGetLabelIdsByTrackingIds() {
    Set<String> trackingIdset = new HashSet<>();
    trackingIdset.add("12345");
    List<LabelIdAndTrackingIdPair> labelIdAndTrackingIdPairList =
        containerService.getLabelIdsByTrackingIdsWhereLabelIdNotNull(trackingIdset);
    Assert.assertEquals(labelIdAndTrackingIdPairList.size(), 1);
    Assert.assertEquals(labelIdAndTrackingIdPairList.get(0).getLabelId().intValue(), 101);
  }

  private void setUpData() {
    List<Container> containerList = new ArrayList<>();
    Container container1 = new Container();
    container1.setLabelId(101);
    container1.setId(1L);
    container1.setDeliveryNumber(12345L);
    container1.setMessageId("msgId1");
    container1.setParentTrackingId("1234");
    container1.setTrackingId("12345");
    container1.setCreateUser("sysadmin");
    containerList.add(container1);

    Container container2 = new Container();
    container2.setLabelId(102);
    container2.setId(2L);
    container2.setDeliveryNumber(34567L);
    container2.setMessageId("msgId1");
    container2.setTrackingId("34567");
    container2.setCreateUser("sysadmin");
    containerList.add(container2);

    Container container3 = new Container();
    container3.setLabelId(103);
    container3.setId(3L);
    container3.setDeliveryNumber(67890L);
    container3.setMessageId("msgId1");
    container3.setParentTrackingId("1234");
    container3.setTrackingId("67890");
    container3.setCreateUser("sysadmin");
    containerList.add(container3);
    containerRepository.saveAll(containerList);

    List<ContainerItem> containerItemList = new ArrayList<>();
    ContainerItem containerItem1;
    containerItem1 = MockContainer.getMockContainerItem().get(0);
    containerItem1.setTrackingId("12345");
    containerItem1.setOutboundChannelMethod("CROSSU");

    ContainerItem containerItem2;
    containerItem2 = MockContainer.getMockContainerItem().get(0);
    containerItem2.setTrackingId("34567");
    containerItem2.setOutboundChannelMethod("POCON");
    containerItem2.setPurchaseReferenceNumber("6747634");

    ContainerItem containerItem3;
    containerItem3 = MockContainer.getMockContainerItem().get(0);
    containerItem3.setTrackingId("34567");
    containerItem3.setOutboundChannelMethod("POCON");
    containerItem3.setPurchaseReferenceNumber("7843658");

    ContainerItem containerItem4;
    containerItem4 = MockContainer.getMockContainerItem().get(0);
    containerItem4 = MockContainer.getMockContainerItem().get(0);
    containerItem4.setTrackingId("67890");
    containerItem4.setOutboundChannelMethod("SSTKU");
    containerItemList.add(containerItem1);
    containerItemList.add(containerItem2);
    containerItemList.add(containerItem3);
    containerItemList.add(containerItem4);
    containerItemRepository.saveAll(containerItemList);
  }
}
