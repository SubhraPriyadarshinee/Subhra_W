package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.EventType.OFFLINE_RECEIVING;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaSymPutawayMessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymHawkeyeEventType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class SymboticPutawayPublishHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(SymboticPutawayPublishHelper.class);

  @Autowired private KafkaSymPutawayMessagePublisher kafkaSymPutawayMessagePublisher;
  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ContainerPersisterService containerPersisterService;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public void publishSymPutawayUpdateOrDeleteMessage(
      String trackingId,
      ContainerItem containerItem,
      String action,
      Integer quantity,
      HttpHeaders httpHeaders) {

    String freightType = containerItem.getInboundChannelMethod();
    boolean isDA = ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(freightType);
    String alignment = containerItem.getAsrsAlignment();
    if (isDA && isSymCutOverEnabled()) {
      alignment =
          tenantSpecificConfigReader.getCcmValue(
              TenantContext.getFacilityNum(),
              ReceivingConstants.SYM_PUTAWAY_SYSTEM_DEFAULT_VALUE,
              ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE);
    }
    Map<String, Object> messageHeader =
        SymboticUtils.getSymPutawayMessageHeader(
            httpHeaders, alignment, SymHawkeyeEventType.PUTAWAY_REQUEST.toString());
    SymPutawayMessage symPutawayMessage = null;
    switch (action) {
      case ReceivingConstants.PUTAWAY_UPDATE_ACTION:
        Integer updatedContainerQuantityInVnpk =
            ReceivingUtils.conversionToVendorPack(
                quantity,
                ReceivingConstants.Uom.EACHES,
                containerItem.getVnpkQty(),
                containerItem.getWhpkQty());
        symPutawayMessage =
            SymboticUtils.createHawkeyePalletAdjustmentMessage(
                containerItem, trackingId, updatedContainerQuantityInVnpk);
        break;
      case ReceivingConstants.PUTAWAY_DELETE_ACTION:
        symPutawayMessage = SymboticUtils.createSymPutawayDeleteMessage(trackingId);
        break;
      default:
        LOGGER.warn("Putaway action: {} is not valid, skipping, message publish.", action);
        return;
    }
    publish(trackingId, messageHeader, symPutawayMessage);
  }

  public void publishPutawayAddMessage(
      ReceivedContainer receivedContainer,
      DeliveryDocument deliveryDocument,
      Instruction instruction4mDB,
      SymFreightType symFreightType,
      HttpHeaders httpHeaders) {
    ContainerItem containerItem =
        containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                receivedContainer.getLabelTrackingId(),
                receivedContainer.getPoNumber(),
                receivedContainer.getPoLine());

    boolean isSymPutawayEligible =
        symFreightType.equals(SymFreightType.SSTK)
            ? SymboticUtils.isValidForSymPutaway(
                containerItem.getAsrsAlignment(),
                appConfig.getValidSymAsrsAlignmentValues(),
                containerItem.getSlotType())
            : symFreightType.equals(SymFreightType.DA)
                || SymFreightType.XDOCK.equals(symFreightType);

    if (isSymPutawayEligible) {

      String alignment = containerItem.getAsrsAlignment();

      if (isSymCutOverEnabled() && symFreightType.equals(SymFreightType.DA)) {
        alignment =
            tenantSpecificConfigReader.getCcmValue(
                TenantContext.getFacilityNum(),
                ReceivingConstants.SYM_PUTAWAY_SYSTEM_DEFAULT_VALUE,
                ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE);
      } else if (symFreightType.equals(SymFreightType.XDOCK)) {
        alignment = enrichValidAsrsAlignmentForOffline(alignment);
      }

      SymPutawayMessage symPutawayMessage =
          SymboticUtils.createPutawayAddMessage(
              containerItem, deliveryDocument, instruction4mDB, receivedContainer, symFreightType);
      Map<String, Object> symMessageHeader =
          SymboticUtils.getSymPutawayMessageHeader(
              httpHeaders, alignment, SymHawkeyeEventType.PUTAWAY_REQUEST.toString());

      LOGGER.info(
          "Publishing putaway order message with payload : {} and headers :{} ",
          symPutawayMessage,
          symMessageHeader);
      publish(containerItem.getTrackingId(), symMessageHeader, symPutawayMessage);
    }
  }

  /**
   * This method fetched the alignment from ccm config otherwise set the default value
   *
   * @param alignment
   * @return
   */
  private String enrichValidAsrsAlignmentForOffline(String alignment) {
    if (!appConfig.getValidSymAsrsAlignmentValues().contains(alignment)) {
      LOGGER.info("Not a valid asrs alignment for offline : {}  ", alignment);
      alignment =
          tenantSpecificConfigReader.getCcmValue(
              TenantContext.getFacilityNum(),
              ReceivingConstants.SYM_PUTAWAY_SYSTEM_DEFAULT_VALUE,
              ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE);
    }
    return alignment;
  }

  /**
   * This method will validate the Symbotic CutOver enablement
   *
   * @return
   */
  private boolean isSymCutOverEnabled() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_SYM_CUTOVER_COMPLETED,
        false);
  }

  public void publish(
      String trackingId, Map<String, Object> messageHeader, SymPutawayMessage symPutawayMessage) {
    try {
      kafkaSymPutawayMessagePublisher.publish(symPutawayMessage, messageHeader);
    } catch (ReceivingInternalException exception) {
      LOGGER.warn(
          "Error in publishing putaway order message to hawkeye for trackingID {} with exception - {}",
          trackingId,
          ExceptionUtils.getStackTrace(exception));
    }
  }

  public void publishPutawayAddMessageToKafka(List<String> trackingIds, HttpHeaders httpHeaders)
      throws ReceivingException {

    List<String> eventTypeList =
        httpHeaders.containsKey(ReceivingConstants.EVENT_TYPE)
            ? httpHeaders.get(ReceivingConstants.EVENT_TYPE)
            : Collections.emptyList();
    String eventTypeVal =
        CollectionUtils.isNotEmpty(eventTypeList)
            ? eventTypeList.get(0)
            : ReceivingConstants.EMPTY_STRING;
    EventType eventType = EventType.valueOfEventType(eventTypeVal);

    for (String trackingId : trackingIds) {
      Container container =
          containerPersisterService.getConsolidatedContainerForPublish(trackingId);
      if (Objects.nonNull(container) && !CollectionUtils.isEmpty(container.getContainerItems())) {
        ContainerItem containerItem = container.getContainerItems().get(0);
        String freightType = containerItem.getInboundChannelMethod();
        boolean isSSTK = ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(freightType);
        boolean isDA = ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(freightType);
        boolean isXDOCK = false;
        if (container.getContainerMiscInfo() != null
            && container
                .getContainerMiscInfo()
                .containsKey(ReceivingConstants.INVENTORY_LABEL_TYPE)) {
          isXDOCK =
              ReceivingConstants.XDK1.equals(
                      container.getContainerMiscInfo().get(ReceivingConstants.INVENTORY_LABEL_TYPE))
                  || ReceivingConstants.XDK2.equals(
                      container
                          .getContainerMiscInfo()
                          .get(ReceivingConstants.INVENTORY_LABEL_TYPE));
        }
        boolean isSymPutawayEligible =
            isSSTK
                ? SymboticUtils.isValidForSymPutaway(
                    containerItem.getAsrsAlignment(),
                    appConfig.getValidSymAsrsAlignmentValues(),
                    containerItem.getSlotType())
                : isDA || isXDOCK;
        if (isSymPutawayEligible) {
          SymPutawayMessage symPutawayMessage =
              SymboticUtils.createPutawayAddMessage(
                  container,
                  containerItem,
                  isXDOCK ? SymFreightType.XDOCK.toString() : freightType);
          String alignment = containerItem.getAsrsAlignment();
          if (OFFLINE_RECEIVING.equals(eventType)) {
            alignment = enrichValidAsrsAlignmentForOffline(alignment);
          } else if (isDA && isSymCutOverEnabled()) {
            alignment =
                tenantSpecificConfigReader.getCcmValue(
                    TenantContext.getFacilityNum(),
                    ReceivingConstants.SYM_PUTAWAY_SYSTEM_DEFAULT_VALUE,
                    ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE);
          }
          Map<String, Object> symMessageHeader =
              SymboticUtils.getSymPutawayMessageHeader(
                  httpHeaders, alignment, SymHawkeyeEventType.PUTAWAY_REQUEST.toString());
          LOGGER.info(
              "Publishing putaway order message with payload : {} and headers :{} ",
              symPutawayMessage,
              symMessageHeader);
          publish(container.getTrackingId(), symMessageHeader, symPutawayMessage);
        }
      } else {
        LOGGER.info(
            "Unable to publish putaway message for container :{}. Container not found", trackingId);
      }
    }
  }
}
