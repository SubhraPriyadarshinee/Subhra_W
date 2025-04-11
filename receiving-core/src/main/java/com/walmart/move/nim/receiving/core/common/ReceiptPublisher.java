package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaMessagePublisher;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class ReceiptPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptPublisher.class);

  @Autowired private JmsPublisher jmsPublisher;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ContainerTransformer containerTransformer;
  @Autowired private KafkaMessagePublisher kafkaMessagePublisher;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  private final Gson gson;

  @Value("${atlas.receipts.updates.topic:null}")
  protected String receiptUpdatesTopic; // kafka

  /** MaasTopic MQ. By Default value TOPIC/RECEIVE/UPDATES. Publish receive updates to SCT */
  @Value("${pub.receipts.update.topic:TOPIC/RECEIVE/UPDATES}")
  protected String pubReceiptsUpdateTopic;

  public ReceiptPublisher() {
    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  /**
   * Publish receiving updates to SCT
   *
   * @param trackingId
   * @param httpHeaders
   * @param putForRetry
   * @throws ReceivingException
   */
  public void publishReceiptUpdate(String trackingId, HttpHeaders httpHeaders, Boolean putForRetry)
      throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.PUB_RECEIVE_UPDATES_ENABLED, false)) {

      Container container =
          containerPersisterService.getConsolidatedContainerForPublish(trackingId);

      Map<String, Object> headersToSend =
          ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
      headersToSend.put(ReceivingConstants.IDEM_POTENCY_KEY, container.getTrackingId());

      // Send flowDescriptor header for identifying the split pallet
      final String flowDescriptorHeader = httpHeaders.getFirst(ReceivingConstants.FLOW_DESCRIPTOR);
      if (StringUtils.isNotBlank(flowDescriptorHeader))
        headersToSend.put(ReceivingConstants.FLOW_DESCRIPTOR, flowDescriptorHeader);

      LOGGER.info(
          "publish receipt update with LPN: {} and headers: {}",
          container.getTrackingId(),
          headersToSend);
      publishMessage(container, headersToSend, putForRetry, container.getTrackingId());
    }
  }

  /**
   * @param container
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void publishReceiptUpdate(Container container, HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, Object> headersToSend =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    headersToSend.put(ReceivingConstants.IDEM_POTENCY_KEY, container.getTrackingId());
    publishMessage(container, headersToSend, Boolean.TRUE, container.getTrackingId());
  }

  private void publishMessage(
      Container container,
      Map<String, Object> headersToSend,
      Boolean isPutForRetriesEnabled,
      String key) {
    // Publish with kafka if enabled else Default JMS
    ContainerDTO containerDTO = null;
    try {
      containerDTO = transformer.transform(container);
      addAdditionalData(containerDTO);
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.KAFKA_RECEIPT_UPDATES_PUBLISH_ENABLED,
          false)) {
        kafkaMessagePublisher.publish(
            key, Arrays.asList(containerDTO), receiptUpdatesTopic, headersToSend);
      } else {
        ReceivingJMSEvent jmsEvent =
            new ReceivingJMSEvent(headersToSend, gson.toJson(containerDTO));
        jmsPublisher.publish(pubReceiptsUpdateTopic, jmsEvent, isPutForRetriesEnabled);
      }
      LOGGER.info("published Receipt updates {} ", gson.toJson(containerDTO));
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to publish Receipt updates {} {}",
          gson.toJson(containerDTO),
          ExceptionUtils.getStackTrace(exception));
    }
  }

  public void addAdditionalData(ContainerDTO containerDTO) {
    containerDTO.setLabelPrintInd(ReceivingConstants.Y);
    if (containerDTO.getOrgUnitIdInfo() == null) {
      containerDTO.setOrgUnitId(
          Integer.valueOf(
              tenantSpecificConfigReader.getCcmValue(
                  TenantContext.getFacilityNum(),
                  ReceivingConstants.ORG_UNIT_ID_HEADER,
                  ReceivingConstants.ORG_UNIT_ID_DEFAULT_VALUE)));
    }
  }
}
