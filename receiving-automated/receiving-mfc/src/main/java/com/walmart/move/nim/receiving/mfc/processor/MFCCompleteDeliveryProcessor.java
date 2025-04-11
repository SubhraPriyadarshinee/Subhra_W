package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryHeaderSearchDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.PageDetails;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchByStatusRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.service.CompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.StoreDeliveryStatus;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class MFCCompleteDeliveryProcessor implements CompleteDeliveryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(MFCCompleteDeliveryProcessor.class);

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;
  @Autowired protected DeliveryStatusPublisher deliveryStatusPublisher;

  @Autowired protected MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Autowired protected MFCDeliveryService mfcDeliveryService;

  @Autowired private ProcessInitiator processInitiator;

  @Override
  public DeliveryInfo completeDelivery(
      Long deliveryNumber, boolean performUnload, HttpHeaders headers) throws ReceivingException {
    DeliveryInfo deliveryInfo;
    if (performUnload) {
      DeliveryMetaData deliveryMetaData =
          mfcDeliveryMetadataService
              .findByDeliveryNumber(String.valueOf(deliveryNumber))
              .orElse(null);
      deliveryInfo = initiateDeliveryComplete(deliveryNumber, deliveryMetaData, headers);
    } else {
      deliveryInfo = completeDelivery(deliveryNumber, headers);
    }
    return deliveryInfo;
  }

  public DeliveryInfo completeDelivery(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {

    mfcDeliveryMetadataService.findAndUpdateDeliveryStatus(
        String.valueOf(deliveryNumber), DeliveryStatus.COMPLETE);

    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    deliveryInfo.setTs(new Date());
    deliveryInfo.setUserId(ReceivingUtils.retrieveUserId());

    deliveryStatusPublisher.publishDeliveryStatus(
        deliveryNumber,
        DeliveryStatus.COMPLETE.name(),
        null,
        ReceivingUtils.getForwardablHeader(headers));

    initiateDeliveryCompleteFlow(deliveryInfo, headers);
    return deliveryInfo;
  }

  private void initiateDeliveryCompleteFlow(DeliveryInfo deliveryInfo, HttpHeaders headers) {

    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(headers);

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(deliveryInfo.getDeliveryNumber()))
            .name(POST_DELIVERY_COMPLETE_EVENT)
            .additionalAttributes(forwardableHeaders)
            .build();
    processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
  }

  @Override
  public void autoCompleteDeliveries(Integer facilityNumber) throws ReceivingException {
    populateContext();
    List<Delivery> deliveries = fetchEligibleDeliveries();
    if (CollectionUtils.isEmpty(deliveries)) {
      LOGGER.info("No eligible deliveries applicable for auto closure so skipping the flow.");
      return;
    }
    LOGGER.info(
        "AUTO-COMPLETE - {} eligible deliveries for auto delivery complete.", deliveries.size());
    Map<Long, DeliveryMetaData> deliveryMetaDataMap = getDeliveryMetaDataDetails(deliveries);
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(USER_ID_HEADER_KEY, TenantContext.getUserId());
    deliveries.forEach(
        delivery ->
            initiateDeliveryComplete(
                delivery.getDeliveryNumber(),
                deliveryMetaDataMap.get(delivery.getDeliveryNumber()),
                httpHeaders));
  }

  public DeliveryInfo initiateDeliveryComplete(
      Long deliveryNumber, DeliveryMetaData deliveryMetaData, HttpHeaders httpHeaders) {
    try {
      if (Objects.isNull(deliveryMetaData)
          || StoreDeliveryStatus.getDeliveryStatus(deliveryMetaData.getDeliveryStatus()).getOrder()
              < StoreDeliveryStatus.UNLOADING_COMPLETE.getOrder()) {
        LOGGER.info("Initiating Unload Complete for delivery number {}.", deliveryNumber);
        DeliveryInfo deliveryInfo =
            mfcDeliveryService.unloadComplete(
                deliveryNumber, ReceivingConstants.DEFAULT_DOOR_NUM, null, httpHeaders);
        createDeliveryAutoCompleteEvent(deliveryNumber);
        return deliveryInfo;
      } else {
        LOGGER.info("Initiating Delivery Complete for delivery number {}.", deliveryNumber);
        return completeDelivery(deliveryNumber, httpHeaders);
      }
    } catch (Exception e) {
      LOGGER.error(
          "Error while initiating Auto Complete flow for delivery number {}", deliveryNumber, e);
    }
    return null;
  }

  private void createDeliveryAutoCompleteEvent(Long delivery) {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(
        MFCConstant.EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES,
        mfcManagedConfig.getDeliveryAutoCompleteEventThresholdMins());
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_DELIVERY_COMPLETE_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(delivery))
            .processor(AUTO_DELIVERY_COMPLETE_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    processInitiator.initiateProcess(receivingEvent, null);
  }

  private Map<Long, DeliveryMetaData> getDeliveryMetaDataDetails(List<Delivery> deliveries) {
    List<String> deliveryNumbers =
        deliveries
            .stream()
            .map(delivery -> String.valueOf(delivery.getDeliveryNumber()))
            .collect(Collectors.toList());
    LOGGER.info("Fetching delivery metadata for delivery numbers {}", deliveryNumbers);
    List<DeliveryMetaData> deliveryMetaDataList =
        mfcDeliveryMetadataService.findAllByDeliveryNumberIn(deliveryNumbers);
    if (CollectionUtils.isNotEmpty(deliveryMetaDataList)) {
      return deliveryMetaDataList
          .stream()
          .collect(
              Collectors.toMap(
                  deliveryMetaData -> Long.parseLong(deliveryMetaData.getDeliveryNumber()),
                  deliveryMetaData -> deliveryMetaData));
    }
    return Collections.emptyMap();
  }

  private void populateContext() {
    TenantContext.setAdditionalParams(
        USER_ID_HEADER_KEY, ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID);
    CoreUtil.setMDC();
    MDC.put(USER_ID_HEADER_KEY, ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID);
  }

  private List<Delivery> fetchEligibleDeliveries() throws ReceivingException {

    DeliveryList deliveryList =
        mfcDeliveryService.fetchDeliveries(
            GdmDeliverySearchByStatusRequest.builder()
                .criteria(
                    DeliveryHeaderSearchDetails.builder()
                        .deliveryStatusList(mfcManagedConfig.getAutoCompleteDeliveryStatus())
                        .ageThresholdInHours(
                            mfcManagedConfig.getDeliveryAutoCompleteThresholdHours())
                        .build())
                .page(
                    PageDetails.builder()
                        .size(appConfig.getAutoCompleteDeliveryPageSize())
                        .number(0)
                        .build())
                .build());

    if (Objects.nonNull(deliveryList) && CollectionUtils.isNotEmpty(deliveryList.getData())) {
      return deliveryList
          .getData()
          .stream()
          .filter(
              delivery -> {
                List<String> documentType =
                    (List<String>)
                        delivery
                            .getAdditionalInformation()
                            .getOrDefault("shipmentDocumentTypes", Collections.emptyList());
                return CollectionUtils.isNotEmpty(documentType)
                    && DocumentType.ASN.equalsType(documentType.get(0));
              })
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public DeliveryInfo completeDeliveryAndPO(Long deliveryNumber, HttpHeaders headers) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }
}
