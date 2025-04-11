package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.EVENT_TYPE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.POST_DSD_CONTAINER_CREATE_PROCESSOR;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentHeaderSearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.mfc.common.AddContainerEvent;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.gdm.ScanPalletRequest;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.NewInvoiceLine;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class StoreNGRFinalizationEventProcessor implements EventProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreNGRFinalizationEventProcessor.class);

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Autowired private MFCDeliveryService deliveryService;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private MFCContainerService mfcContainerService;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  private Gson gson;

  @Value("${gdm.invoice.changes.topic:abc}")
  private String invoiceOperationTopic;

  public StoreNGRFinalizationEventProcessor() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    if (!(messageData instanceof NGRPack)) {
      LOGGER.error(ReceivingConstants.WRONG_NGR_FINALIZATION_LISTENER_MESSAGE_FORMAT, messageData);
      return;
    }
    NGRPack finalizedPack = (NGRPack) messageData;
    if (CollectionUtils.isEmpty(finalizedPack.getItems())) {
      LOGGER.info(
          "No received items present in the pack. Hence skipping the flow for pack {} and document Id {}.",
          finalizedPack.getPackNumber(),
          finalizedPack.getInboundDocumentId());
      return;
    }

    Long deliveryNumber = getDeliveryDetails(finalizedPack);
    processContainer(finalizedPack, deliveryNumber);
    initiatePostProcessor(finalizedPack, deliveryNumber);
    LOGGER.info("Processing NGR Finalization event");
  }

  private void initiatePostProcessor(NGRPack finalizedPack, Long deliveryNumber) {
    Map<String, Object> forwardableHeaders = getForwardablHeader();

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(finalizedPack))
            .processor(POST_DSD_CONTAINER_CREATE_PROCESSOR)
            .additionalAttributes(forwardableHeaders)
            .key(String.valueOf(deliveryNumber))
            .build();
    processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
  }

  private Map<String, Object> getForwardablHeader() {
    Map<String, Object> forwardableHeaders = new HashMap<>();
    forwardableHeaders.put(USER_ID_HEADER_KEY, TenantContext.getUserId());
    forwardableHeaders.put(CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    forwardableHeaders.put(TENENT_FACLITYNUM, getFacilityNum().toString());
    forwardableHeaders.put(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    return forwardableHeaders;
  }

  private void processContainer(NGRPack finalizedPack, Long deliveryNumber) {
    ScanPalletRequest scanPalletRequest =
        ScanPalletRequest.builder()
            .palletNumber(finalizedPack.getPackNumber())
            .deliveryNumber(deliveryNumber)
            .build();
    ASNDocument asnDocument =
        deliveryService.findDeliveryDocumentByPalletAndDelivery(scanPalletRequest, true);
    if (Objects.isNull(asnDocument.getShipment()) && !asnDocument.getShipments().isEmpty()) {
      asnDocument.setShipment(asnDocument.getShipments().get(0));
    }
    Map<String, String> replenishmentCodeMismatching =
        MFCUtils.getReplenishmentCodeIfMismatching(asnDocument, finalizedPack);
    if (!replenishmentCodeMismatching.isEmpty()) {
      publishReplenishmentCodeMismatchEvent(
          asnDocument, finalizedPack, replenishmentCodeMismatching);
    }
    updateAsnDetails(asnDocument, finalizedPack.getPackNumber(), replenishmentCodeMismatching);

    Container container =
        mfcContainerService.createTransientContainer(
            ContainerScanRequest.builder()
                .deliveryNumber(deliveryNumber)
                .trackingId(scanPalletRequest.getPalletNumber())
                .build(),
            asnDocument);
    if (!Objects.isNull(container)) {
      LOGGER.info(
          "Going to persist the container into DB for palletId = {}", container.getSsccNumber());
      ContainerDTO containerDTO = mfcContainerService.createContainer(container, asnDocument);
      mfcContainerService.publishContainer(containerDTO);
    }
  }

  private void updateAsnDetails(
      ASNDocument asnDocument, String packNumber, Map<String, String> updateReplenishCodeMap) {
    asnDocument
        .getPacks()
        .forEach(
            pack -> {
              if (packNumber.equalsIgnoreCase(pack.getPackNumber())
                  && StringUtils.isEmpty(pack.getPalletNumber())) {
                pack.setPalletNumber(packNumber);
              }
              if (CollectionUtils.isNotEmpty(pack.getItems())
                  && MapUtils.isNotEmpty(updateReplenishCodeMap)) {
                pack.getItems()
                    .forEach(
                        item -> {
                          String updatedReplenishmentCode =
                              updateReplenishCodeMap.get(String.valueOf(item.getItemNumber()));
                          if (Objects.nonNull(updatedReplenishmentCode)) {
                            item.setReplenishmentCode(updatedReplenishmentCode);
                          }
                        });
              }
            });
  }

  private void publishReplenishmentCodeMismatchEvent(
      ASNDocument asnDocument,
      NGRPack finalizedPack,
      Map<String, String> replenishmentCodeMismatching) {

    List<com.walmart.move.nim.receiving.core.model.gdm.v3.Pack> asnPacks =
        asnDocument
            .getPacks()
            .stream()
            .filter(
                pack -> {
                  if (StringUtils.isNotEmpty(pack.getPalletNumber())) {
                    return pack.getPalletNumber().equalsIgnoreCase(finalizedPack.getPackNumber());
                  } else {
                    return pack.getPackNumber().equalsIgnoreCase(finalizedPack.getPackNumber());
                  }
                })
            .collect(Collectors.toList());

    NewInvoiceLine newInvoiceLine =
        NewInvoiceLine.builder()
            .eventType(AddContainerEvent.UPDATE_REPLEN_CODE_EVENT.getEventType())
            .shipmentDocumenId(finalizedPack.getInboundDocumentId())
            .packs(MFCUtils.getPacks(asnPacks, replenishmentCodeMismatching))
            .userId(ReceivingUtils.retrieveUserId())
            .ts(new Date())
            .build();

    Map<String, Object> headers = new HashMap<>();
    headers.put(EVENT_TYPE, newInvoiceLine.getEventType());
    Message<String> message =
        KafkaHelper.buildKafkaMessage(
            finalizedPack.getPackNumber(),
            gson.toJson(newInvoiceLine),
            invoiceOperationTopic,
            headers);
    kafkaTemplate.send(message);
  }

  private Long getDeliveryDetails(NGRPack finalizedPack) throws ReceivingException {

    List<GDMShipmentHeaderSearchResponse> shipmentResponse =
        deliveryService.getShipmentDetails(finalizedPack.getDocumentNumber());

    if (CollectionUtils.isEmpty(shipmentResponse)) {
      LOGGER.error("No shipment flow found for documentNbr {}", finalizedPack.getDocumentNumber());
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.GDM_NOT_FOUND,
          String.format(
              "No shipment flow found for documentNbr %s", finalizedPack.getDocumentNumber()));
    }
    Optional<Long> deliveryNumberOpt = fetchValidDelivery(shipmentResponse);
    if (!deliveryNumberOpt.isPresent()) {
      LOGGER.error("No shipment flow found for documentNbr {}", finalizedPack.getDocumentNumber());
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.GDM_NOT_FOUND,
          String.format(
              "No shipment flow found for documentId %s", finalizedPack.getDocumentNumber()));
    }
    return deliveryNumberOpt.get();
  }

  private Optional<Long> fetchValidDelivery(
      List<GDMShipmentHeaderSearchResponse> shipmentResponse) {
    if (CollectionUtils.isNotEmpty(shipmentResponse)) {
      Optional<Long> deliveryNumberOpt =
          shipmentResponse
              .stream()
              .filter(dataElement -> CollectionUtils.isNotEmpty(dataElement.getDelivery()))
              .flatMap(dataElement -> dataElement.getDelivery().stream())
              .filter(
                  delivery ->
                      Objects.nonNull(delivery.getStatusInformation())
                          && mfcManagedConfig
                              .getDeliveryStatusForOpenDeliveries()
                              .contains(delivery.getStatusInformation().getStatus()))
              .map(Delivery::getDeliveryNumber)
              .findFirst();
      return deliveryNumberOpt;
    }
    return Optional.empty();
  }
}
