package com.walmart.move.nim.receiving.core.message.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.sorter.ProgramSorterTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaAthenaPublisherTest {

  @Mock KafkaTemplate secureKafkaTemplate;
  @InjectMocks private KafkaAthenaPublisher kafkaAthenaPublisher;

  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  private static final String LABEL_TYPE_PUT = "PUT";

  @BeforeMethod
  public void setUp() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32679);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(kafkaAthenaPublisher, "secureKafkaTemplate", secureKafkaTemplate);
  }

  @AfterMethod
  public void tearDown() {
    reset(secureKafkaTemplate);
  }

  @Test
  public void testAthenaPublishSuccess() {
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    settableListenableFuture.set(new Object());
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    kafkaAthenaPublisher.publishLabelToSorter(getMockContainer(), LabelType.STORE.name());
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testAthenaPublishSuccessForContractV2() {
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    settableListenableFuture.set(new Object());
    Container container = getMockContainer();
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    when(tenantSpecificConfigReader.getSorterContractVersion(
            getMockContainerForPUTLabelType(Boolean.TRUE).getFacilityNum()))
        .thenReturn(Integer.valueOf(2));
    kafkaAthenaPublisher.publishLabelToSorter(
        getMockContainerForPUTLabelType(Boolean.TRUE), LABEL_TYPE_PUT);
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(2);
    container.setMessageId(null);
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.ORIGIN_FACILITY_NUM, "32679");
    container.setContainerMiscInfo(containerMiscInfo);
    kafkaAthenaPublisher.publishLabelToSorter(container, LabelType.STORE.name());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testAthenaPublishFailure() {
    when(secureKafkaTemplate.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    ReceivingConstants.CONTAINERS_PUBLISH)));
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(1);
    kafkaAthenaPublisher.publishLabelToSorter(getMockContainer(), LabelType.STORE.name());
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(2);
    kafkaAthenaPublisher.publishLabelToSorter(
        getMockContainerForPUTLabelType(Boolean.TRUE), LabelType.PUT.name());
  }

  @Test
  public void testGetSorterDivertPayloadByLabelTypeWithPUTLabelType() {
    SorterPublisher sorterPublisherTestable = new SorterPublisher() {};
    Container container = getMockContainerForPUTLabelType(Boolean.TRUE);
    ProgramSorterTO programSorterTO =
        sorterPublisherTestable.getSorterDivertPayLoadByLabelType(container, LABEL_TYPE_PUT);
    assertNotNull(programSorterTO);
    assertEquals(programSorterTO.getLabelType(), LABEL_TYPE_PUT);
    assertEquals(programSorterTO.getStoreNbr(), "32679");
  }

  @Test
  public void testGetSorterDivertPayloadByLabelTypeV2WithPUTLabelType() {
    SorterPublisher sorterPublisherTestable = new SorterPublisher() {};
    Container container = getMockContainerForPUTLabelType(Boolean.TRUE);
    ProgramSorterTO programSorterTO =
        sorterPublisherTestable.getSorterDivertPayLoadByLabelTypeV2(container, LABEL_TYPE_PUT);
    assertNotNull(programSorterTO);
    assertEquals(programSorterTO.getLabelType(), ReceivingConstants.PUT_INBOUND);
    assertEquals(programSorterTO.getStoreNbr(), "32679");
  }

  @Test
  public void testGetSorterDivertPayloadByLabelTypeV2LabelType() {
    SorterPublisher sorterPublisherTestable = new SorterPublisher() {};
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "32679");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    container.setDestination(destination);
    container.setContainerMiscInfo(containerMiscInfo);
    sorterPublisherTestable.getSorterDivertPayLoadByLabelTypeV2(container, LABEL_TYPE_PUT);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(550953821L);
    Distribution distribution = new Distribution();
    distribution.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    distribution.setDestNbr(32679);
    containerItem.setDistributions(Arrays.asList(distribution));
    container.setContainerItems(Arrays.asList(containerItem));
    sorterPublisherTestable.getSorterDivertPayLoadByLabelTypeV2(container, LABEL_TYPE_PUT);
    Map<String, String> itemMap = new HashMap<>();
    itemMap.put("financialReportingGroup", "US");
    itemMap.put("baseDivisionCode", "WM");
    itemMap.put("divisionNumber", "2");
    distribution.setItem(itemMap);
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    sorterPublisherTestable.getSorterDivertPayLoadByLabelTypeV2(container, "SYM00025");
    containerItemMiscInfo.put("poEvent", null);
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    container.setContainerMiscInfo(containerMiscInfo);
    sorterPublisherTestable.getSorterDivertPayLoadByLabelTypeV2(container, "SYM00020");
  }

  @Test
  public void testGetSorterDivertPayloadByLabelTypeWithPUTLabelTypeAndNoChildContainers() {
    SorterPublisher sorterPublisherTestable = new SorterPublisher() {};
    Container container = getMockContainerForPUTLabelType(Boolean.FALSE);
    ProgramSorterTO programSorterTO =
        sorterPublisherTestable.getSorterDivertPayLoadByLabelType(container, LABEL_TYPE_PUT);
    assertNotNull(programSorterTO);
    assertEquals(programSorterTO.getLabelType(), LABEL_TYPE_PUT);
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setTrackingId("o323232320232323");
    //    container.setMessageId("c53e19a1-1f1c-4e22-90b4-f40d7f79d868");
    container.setCreateTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "01232");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDestination(destination);
    return container;
  }

  private Container getMockContainerForPUTLabelType(Boolean hasChildContainers) {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "32679");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("r2323232308969587");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);

    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setItemNumber(550953821L);
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.ORIGIN_FACILITY_NUM, "32679");
    container.setContainerMiscInfo(containerMiscInfo);

    container.setContainerItems(Arrays.asList(parentContainerItem));

    if (hasChildContainers) {
      Container childContainer = new Container();
      ContainerItem childContainerItem = new ContainerItem();

      childContainerItem.setQuantity(50);
      childContainerItem.setItemNumber(550953821L);
      childContainerItem.setDeptNumber(95);
      childContainerItem.setTrackingId("434916453434");

      Map<String, String> itemMap = new HashMap<>();
      itemMap.put("financialReportingGroup", "US");
      itemMap.put("baseDivisionCode", "WM");
      itemMap.put("itemNbr", "1084445");
      itemMap.put("divisionNumber", "2");

      Distribution distribution = new Distribution();
      distribution.setAllocQty(5);
      distribution.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
      distribution.setDestNbr(32679);
      distribution.setItem(itemMap);

      List<Distribution> distributions = new ArrayList<>();
      distributions.add(distribution);

      childContainerItem.setDistributions(distributions);

      childContainer.setContainerItems(Arrays.asList(childContainerItem));

      Set<Container> childContainerSet = new HashSet<>();
      childContainerSet.add(childContainer);
      container.setChildContainers(childContainerSet);
    }

    return container;
  }
}
