package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaMessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class ReceiptPublisherTest extends AbstractTestNGSpringContextTests {
  @InjectMocks private ReceiptPublisher receiptPublisher;

  @Mock private JmsPublisher jmsPublisher;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private KafkaMessagePublisher kafkaMessagePublisher;
  @Mock private ContainerTransformer containerTransformer;
  @Mock Transformer<Container, ContainerDTO> transformer;

  private Container container = MockContainer.getSSTKContainer();

  @BeforeMethod
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(
        receiptPublisher, "pubReceiptsUpdateTopic", "TOPIC/RECEIVE/UPDATES");

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @AfterMethod
  public void tearDown() {
    reset(jmsPublisher);
    reset(containerPersisterService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testPublishReceiptUpdateWithFlowDescriptor() throws ReceivingException {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED), anyBoolean());
    doReturn(container)
        .when(containerPersisterService)
        .getConsolidatedContainerForPublish(anyString());
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(transformer.transform(any(Container.class))).thenReturn(getMockContainerDTO());
    doReturn(ReceivingConstants.DEFAULT_VERSION)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.SUBCENTER_ID_HEADER), anyString());
    doReturn(ReceivingConstants.ORG_UNIT_ID_DEFAULT_VALUE)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.ORG_UNIT_ID_HEADER), anyString());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    String flowDescriptor =
        "{\"flowName\":\"SPLIT_PALLET_TRANSFER\",\"businessEvent\":\"INVENTORY_TRANSFER\",\"sourceCntrTrackingId\":\"J3261200001\",\"destCntrTrackingIds\":[\"J3261200002\"]}";
    httpHeaders.add("flowDescriptor", flowDescriptor);
    receiptPublisher.publishReceiptUpdate(container.getTrackingId(), httpHeaders, Boolean.TRUE);

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
  }

  @Test
  public void testPublishReceiptUpdateWithOutFlowDescriptor() throws ReceivingException {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED), anyBoolean());
    doReturn(ReceivingConstants.DEFAULT_VERSION)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.SUBCENTER_ID_HEADER), anyString());
    doReturn(ReceivingConstants.ORG_UNIT_ID_DEFAULT_VALUE)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.ORG_UNIT_ID_HEADER), anyString());
    doReturn(container)
        .when(containerPersisterService)
        .getConsolidatedContainerForPublish(anyString());
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(transformer.transform(any(Container.class))).thenReturn(getMockContainerDTO());
    receiptPublisher.publishReceiptUpdate(
        container.getTrackingId(), MockHttpHeaders.getHeaders(), Boolean.TRUE);

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
  }

  @Test
  public void testPublishReceiptUpdateWithFeatureFlagDisable() throws ReceivingException {
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED), anyBoolean());

    receiptPublisher.publishReceiptUpdate(
        container.getTrackingId(), MockHttpHeaders.getHeaders(), Boolean.TRUE);

    verify(containerPersisterService, times(0)).getConsolidatedContainerForPublish(anyString());
    verify(jmsPublisher, times(0))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
  }

  @Test
  public void testKafkaPublishReceiptUpdateWithFlowDescriptor() throws ReceivingException {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED), anyBoolean());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.KAFKA_RECEIPT_UPDATES_PUBLISH_ENABLED),
            anyBoolean());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), anyMap());
    when(transformer.transform(any(Container.class))).thenReturn(getMockContainerDTO());
    doReturn(container)
        .when(containerPersisterService)
        .getConsolidatedContainerForPublish(anyString());
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(ReceivingConstants.DEFAULT_VERSION)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.SUBCENTER_ID_HEADER), anyString());
    doReturn(ReceivingConstants.ORG_UNIT_ID_DEFAULT_VALUE)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.ORG_UNIT_ID_HEADER), anyString());

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    String flowDescriptor =
        "{\"flowName\":\"SPLIT_PALLET_TRANSFER\",\"businessEvent\":\"INVENTORY_TRANSFER\",\"sourceCntrTrackingId\":\"J3261200001\",\"destCntrTrackingIds\":[\"J3261200002\"]}";
    httpHeaders.add("flowDescriptor", flowDescriptor);
    receiptPublisher.publishReceiptUpdate(container.getTrackingId(), httpHeaders, Boolean.TRUE);

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(kafkaMessagePublisher, times(1))
        .publish(anyString(), any(Object.class), eq(null), anyMap());
  }

  @Test
  public void testKafkaPublishReceiptUpdateWithOutFlowDescriptor() throws ReceivingException {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED), anyBoolean());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.KAFKA_RECEIPT_UPDATES_PUBLISH_ENABLED),
            anyBoolean());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), anyMap());
    when(transformer.transform(any(Container.class))).thenReturn(getMockContainerDTO());
    doReturn(container)
        .when(containerPersisterService)
        .getConsolidatedContainerForPublish(anyString());
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(ReceivingConstants.DEFAULT_VERSION)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.SUBCENTER_ID_HEADER), anyString());
    doReturn(ReceivingConstants.ORG_UNIT_ID_DEFAULT_VALUE)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.ORG_UNIT_ID_HEADER), anyString());
    receiptPublisher.publishReceiptUpdate(
        container.getTrackingId(), MockHttpHeaders.getHeaders(), Boolean.TRUE);

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(kafkaMessagePublisher, times(1))
        .publish(anyString(), any(Object.class), eq(null), anyMap());
  }

  @Test
  public void testKafkaPublishReceiptUpdateWithFeatureFlagDisable() throws ReceivingException {
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED), anyBoolean());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.KAFKA_RECEIPT_UPDATES_PUBLISH_ENABLED),
            anyBoolean());
    doReturn(ReceivingConstants.DEFAULT_VERSION)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), eq(ReceivingConstants.SUBCENTER_ID_HEADER), anyString());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), anyMap());
    when(transformer.transform(any(Container.class))).thenReturn(getMockContainerDTO());

    receiptPublisher.publishReceiptUpdate(
        container.getTrackingId(), MockHttpHeaders.getHeaders(), Boolean.TRUE);

    verify(containerPersisterService, times(0)).getConsolidatedContainerForPublish(anyString());
    verify(kafkaMessagePublisher, times(0))
        .publish(anyString(), any(Object.class), eq(null), anyMap());
  }

  private ContainerDTO getMockContainerDTO() {
    Transformer<Container, ContainerDTO> transformer = new ContainerTransformer();
    return transformer.transform(container);
  }
}
