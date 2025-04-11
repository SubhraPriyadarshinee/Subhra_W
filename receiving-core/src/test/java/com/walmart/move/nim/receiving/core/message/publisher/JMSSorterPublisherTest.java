package com.walmart.move.nim.receiving.core.message.publisher;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.helper.JsonSchemaValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class JMSSorterPublisherTest {
  @InjectMocks JMSSorterPublisher jmsSorterPublisher;
  @Mock JmsPublisher jmsPublisher;
  @Mock AppConfig appConfig;
  @Mock MaasTopics maasTopics;
  private String lpn = "a0000000000000001234";
  private static final String SORTER_EXCEPTION_TOPIC = "WMSOP/OA/LPN/EXCEPTION";
  private static final String SORTER_TOPIC = "WMSOP/OA/LPN";
  private static final String LABEL_TYPE_STORE = "STORE";
  private static final String LABEL_TYPE_PUT = "PUT";
  Container mockContainer = new Container();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());

    mockContainer.setTrackingId(lpn);
    Map<String, String> mockDestination = new HashMap<>();
    mockDestination.put(ReceivingConstants.BU_NUMBER, "6040");
    mockDestination.put(ReceivingConstants.COUNTRY_CODE, "US");
    mockContainer.setDestination(mockDestination);
    mockContainer.setPublishTs(new Date());
  }

  @AfterMethod
  public void resetMocks() {
    reset(appConfig, jmsPublisher, maasTopics);
  }

  @Test
  public void testPublishException() throws IOException {
    when(appConfig.getSorterExceptionTopic()).thenReturn(SORTER_EXCEPTION_TOPIC);

    jmsSorterPublisher.publishException(lpn, SorterExceptionReason.OVERAGE, new Date());

    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_EXCEPTION_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_EXCEPTION);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String payload = receivingJMSEvent.getMessageBody();
    File resource = new ClassPathResource("sorterExceptionMessageSchema.json").getFile();
    String mockSorterSchemaPayLoad = new String(Files.readAllBytes(resource.toPath()));
    assertTrue(validateContract(mockSorterSchemaPayLoad, payload));
  }

  @Test
  public void testPublishStoreLabel() throws IOException {
    when(appConfig.getSorterTopic()).thenReturn(SORTER_TOPIC);

    jmsSorterPublisher.publishStoreLabel(mockContainer);

    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_CREATE);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String payload = receivingJMSEvent.getMessageBody();
    File resource = new ClassPathResource("sorterStoreLabelMessageSchema.json").getFile();
    String mockSorterSchemaPayLoad = new String(Files.readAllBytes(resource.toPath()));
    assertTrue(validateContract(mockSorterSchemaPayLoad, payload));
  }

  @Test
  public void testPublishLabelToSorter_RDC() throws IOException {
    when(maasTopics.getSorterDivertTopic()).thenReturn(SORTER_TOPIC);
    jmsSorterPublisher.publishLabelToSorter(mockContainer, "SYM00020");
    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_CREATE);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    // validate message contract
    String payload = receivingJMSEvent.getMessageBody();

    File resource = new ClassPathResource("sorterStoreLabelMessageSchema.json").getFile();
    String mockSorterSchemaPayLoad = new String(Files.readAllBytes(resource.toPath()));
    assertTrue(validateContract(mockSorterSchemaPayLoad, payload));
  }

  @Test
  public void testPublishStoreLabelToSorter_RDC_STORE_LabelType() throws IOException {
    when(maasTopics.getSorterDivertTopic()).thenReturn(SORTER_TOPIC);
    jmsSorterPublisher.publishLabelToSorter(mockContainer, LABEL_TYPE_STORE);
    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_CREATE);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    // validate message contract
    String payload = receivingJMSEvent.getMessageBody();
    File resource = new ClassPathResource("sorterStoreLabelMessageSchema.json").getFile();
    String mockSorterSchemaPayLoad = new String(Files.readAllBytes(resource.toPath()));
    assertTrue(validateContract(mockSorterSchemaPayLoad, payload));
  }

  @Test
  public void testGetSorterDivertPayloadWithInnerPicks() {
    when(maasTopics.getSorterDivertTopic()).thenReturn(SORTER_TOPIC);
    jmsSorterPublisher.publishLabelToSorter(mockContainer, LABEL_TYPE_STORE);
    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_CREATE);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertEquals(messageHeaders.get(ReceivingConstants.LABEL_TYPE_FOR_PUT_SYSTEM), null);
  }

  @Test
  public void testGetSorterDivertPayloadWithPutInnerPicks() {
    Container container = getMockContainerForPUTLabelType(Boolean.TRUE);
    when(maasTopics.getSorterDivertTopic()).thenReturn(SORTER_TOPIC);
    jmsSorterPublisher.publishLabelToSorter(container, LABEL_TYPE_PUT);
    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_CREATE);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.LABEL_TYPE_FOR_PUT_SYSTEM));
  }

  @Test
  public void testGetSorterDivertPayloadWithInnerPicksWithPUTLabelTypeAndNoChildContainers() {
    Container container = getMockContainerForPUTLabelType(Boolean.FALSE);
    when(maasTopics.getSorterDivertTopic()).thenReturn(SORTER_TOPIC);
    jmsSorterPublisher.publishLabelToSorter(container, LABEL_TYPE_PUT);
    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
    ReceivingJMSEvent receivingJMSEvent = sorterPublishCaptor.getValue();

    // validate headers sent to sorter
    Map<String, Object> messageHeaders = receivingJMSEvent.getHeaders();
    assertEquals(messageHeaders.get(ReceivingConstants.EVENT), ReceivingConstants.LPN_CREATE);
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.LABEL_TYPE_FOR_PUT_SYSTEM));
  }

  private Container getMockContainerForPUTLabelType(Boolean hasChildContainers) {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
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

    container.setContainerItems(Arrays.asList(parentContainerItem));

    if (hasChildContainers) {
      Container childContainer = new Container();
      ContainerItem childContainerItem = new ContainerItem();

      childContainerItem.setQuantity(50);
      childContainerItem.setItemNumber(550953821L);
      childContainerItem.setDeptNumber(95);
      childContainerItem.setTrackingId("060200112345678901");

      Map<String, String> itemMap = new HashMap<>();
      itemMap.put("financialReportingGroup", "US");
      itemMap.put("baseDivisionCode", "WM");
      itemMap.put("itemNbr", "1084445");

      Map<String, Object> containerMiscInfo = new HashMap<>();
      containerMiscInfo.put(ReceivingConstants.STORE_PRINT_BATCH, "281");
      containerMiscInfo.put(ReceivingConstants.STORE_PICK_BATCH, "281");
      containerMiscInfo.put(ReceivingConstants.STORE_AISLE, "E");
      childContainer.setContainerMiscInfo(containerMiscInfo);

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

  private boolean validateContract(String jsonSchema, String jsonMessage) {
    return JsonSchemaValidator.validateContract(jsonSchema, jsonMessage);
  }
}
