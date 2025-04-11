package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsPageResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.core.service.OsdrProcessor;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;

public class RdcOsdrProcessor implements OsdrProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcOsdrProcessor.class);

  @Autowired RdcOsdrService rdcOsdrService;
  @Autowired private RdcMessagePublisher rdcMessagePublisher;
  @ManagedConfiguration private OsdrConfig osdrConfig;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  @Resource(name = RdcConstants.RDC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService rdcDeliveryMetaDataService;

  @Autowired private DeliveryServiceRetryableImpl deliveryService;

  /**
   * This method is responsible for processing the {@link OsdrSummary} for each delivery number for
   * which unloading has been completed
   *
   * @param osdrConfigSpecification
   */
  @Override
  public void process(OsdrConfigSpecification osdrConfigSpecification) {
    DeliveryPOMap deliveryPOMap = new DeliveryPOMap();
    List<DeliveryMetaData> deliveryMetaDataList =
        rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            osdrConfigSpecification.getNosOfDay(),
            osdrConfig.getFrequencyIntervalInMinutes(),
            osdrConfig.getPageSize(),
            deliveryPOMap);
    List<Long> eligibleDeliveriesForOsdrSummary = new ArrayList<>();

    if (!CollectionUtils.isEmpty(deliveryMetaDataList)) {
      if (rdcManagedConfig.isDeliveryStatusCheckEnabled()) {
        List<GdmDeliveryHeaderDetailsResponse> overallDeliveryDetailsResponse =
            getDeliveryStatus(deliveryMetaDataList);

        if (!CollectionUtils.isEmpty(overallDeliveryDetailsResponse)) {
          eligibleDeliveriesForOsdrSummary =
              overallDeliveryDetailsResponse
                  .stream()
                  .parallel()
                  .filter(
                      delivery ->
                          Objects.nonNull(delivery.getStatus())
                              && !delivery
                                  .getStatus()
                                  .name()
                                  .equalsIgnoreCase(DeliveryStatus.FINALIZED.name()))
                  .map(GdmDeliveryHeaderDetailsResponse::getDeliveryNumber)
                  .collect(Collectors.toList());
        }
      } else {
        eligibleDeliveriesForOsdrSummary =
            deliveryMetaDataList
                .stream()
                .parallel()
                .map(deliveryMetaData -> Long.valueOf(deliveryMetaData.getDeliveryNumber()))
                .collect(Collectors.toList());
      }

      if (!CollectionUtils.isEmpty(eligibleDeliveriesForOsdrSummary)) {
        processOsdrSummary(eligibleDeliveriesForOsdrSummary);
      }
    }
  }

  /**
   * This method gets OSDR summary for each delivery number and publish the summary to GDM
   *
   * @param deliveryNumbers
   */
  private void processOsdrSummary(List<Long> deliveryNumbers) {
    for (Long deliveryNumber : deliveryNumbers) {
      try {
        OsdrSummary osdrSummary = rdcOsdrService.getOsdrDetails(deliveryNumber, null, null, null);
        publishOsdrSummary(osdrSummary);
      } catch (ReceivingDataNotFoundException e) {
        LOGGER.error(
            ReceivingConstants.OSDR_DETAILS_NOT_FOUND_ERROR_MSG,
            deliveryNumber,
            ExceptionUtils.getStackTrace(e));
      }
    }
  }

  /**
   * This method fetches delivery status from GDM through delivery header details API 1. Gets list
   * of delivery meta data and split into batches with the max allowed page limit (100) 2. Invokes
   * GDM delivery header details API for each batch list and accumulate the overall GDM delivery
   * status responses
   *
   * @param deliveryMetaDataList
   * @return List<GdmDeliveryHeaderDetailsResponse>
   */
  private List<GdmDeliveryHeaderDetailsResponse> getDeliveryStatus(
      List<DeliveryMetaData> deliveryMetaDataList) {
    List<GdmDeliveryHeaderDetailsResponse> overallDeliveryDetailsResponse = new ArrayList<>();

    List<Long> deliveryNumberList =
        deliveryMetaDataList
            .stream()
            .parallel()
            .map(deliveryMetaData -> Long.valueOf(deliveryMetaData.getDeliveryNumber()))
            .collect(Collectors.toList());

    Collection<List<Long>> deliveryNumberListBatches =
        ReceivingUtils.batchifyCollection(
            deliveryNumberList, ReceivingConstants.DELIVERY_NUMBERS_MAX_PAGE_OFFSET);

    for (List<Long> deliveryNumbers : deliveryNumberListBatches) {
      try {
        GdmDeliveryHeaderDetailsPageResponse gdmDeliveryHeaderDetailsPageResponse =
            deliveryService.getDeliveryHeaderDetailsByDeliveryNumbers(deliveryNumbers);
        overallDeliveryDetailsResponse.addAll(gdmDeliveryHeaderDetailsPageResponse.getData());
      } catch (ReceivingException receivingException) {
        LOGGER.error(
            "Error while fetching delivery header details from GDM. Error Response {}",
            receivingException.getErrorResponse());
      }
    }
    return overallDeliveryDetailsResponse;
  }

  /**
   * This method is responsible for publishing {@link OsdrSummary} to GDM
   *
   * @param osdrSummary
   */
  @TimeTracing(
      component = AppComponent.RDC,
      flow = "GDM-Publish",
      externalCall = true,
      type = Type.MESSAGE)
  private void publishOsdrSummary(OsdrSummary osdrSummary) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, osdrSummary.getUserId());
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    messageHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER, UUID.randomUUID().toString());
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, osdrSummary.getDeliveryNumber());
    messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.ATLAS_RECEIVING);
    messageHeaders.put(
        ReceivingConstants.OSDR_EVENT_TYPE_KEY, ReceivingConstants.OSDR_EVENT_TYPE_VALUE);
    rdcMessagePublisher.publishDeliveryReceipts(osdrSummary, messageHeaders);
  }
}
