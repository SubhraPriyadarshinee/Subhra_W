package com.walmart.move.nim.receiving.core.job;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ScheduleConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.yms.v2.ProgressUpdateDTO;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnExpression("${enable.yms.delivery.update.job:false}")
@Component
public class DeliveryReceiveProgressJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryReceiveProgressJob.class);

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private ProcessInitiator processInitiator;

  @ManagedConfiguration private ScheduleConfig scheduleConfig;

  private Gson gson;

  public DeliveryReceiveProgressJob() {
    this.gson = new Gson();
  }

  @SchedulerLock(
      name = "DeliveryReceiveProgressJob_receiveProgressUpdate",
      lockAtLeastFor = 60000,
      lockAtMostFor = 90000)
  @Scheduled(fixedDelayString = "${yms.receive.update.frequency:60000}")
  public void receiveProgressUpdate() {

    String correlationId = UUID.randomUUID().toString();

    scheduleConfig
        .getSchedulerTenantConfig()
        .forEach(
            pair -> {
              populateInfraValues(pair, correlationId);
              DeliveryMetaDataService deliveryMetaDataService =
                  tenantSpecificConfigReader.getConfiguredInstance(
                      TenantContext.getFacilityNum().toString(),
                      ReceivingConstants.DELIVERY_METADATA_SERVICE,
                      DeliveryMetaDataService.class);
              List<DeliveryMetaData> deliveryMetaDataInfo =
                  deliveryMetaDataService.findActiveDelivery();
              if (Objects.nonNull(deliveryMetaDataInfo) && !deliveryMetaDataInfo.isEmpty()) {
                deliveryMetaDataInfo.forEach(
                    dmd -> {
                      ProgressUpdateDTO progressUpdateDTO =
                          ProgressUpdateDTO.builder()
                              .deliveryNumber(Long.valueOf(dmd.getDeliveryNumber()))
                              .deliveryStatus(dmd.getDeliveryStatus())
                              .build();

                      Map<String, Object> additionalAttribute = new HashMap<>();

                      ReceivingEvent receivingEvent =
                          ReceivingEvent.builder()
                              .payload(gson.toJson(progressUpdateDTO))
                              .name(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
                              .additionalAttributes(additionalAttribute)
                              .processor(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
                              .build();
                      LOGGER.info(
                          "Going to initiate delivery update progress for delivery={}",
                          dmd.getDeliveryNumber());
                      processInitiator.initiateProcess(receivingEvent, additionalAttribute);
                    });
              }
            });
  }

  private void populateInfraValues(Pair<String, Integer> pair, String corelationId) {

    TenantContext.setFacilityCountryCode(pair.getKey());
    TenantContext.setFacilityNum(pair.getValue());
    TenantContext.setCorrelationId(corelationId);
    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, "delivery_update_job");

    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, String.valueOf(pair.getValue()));
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, pair.getKey());
    MDC.put(ReceivingConstants.USER_ID_HEADER_KEY, "delivery_update_job");
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, corelationId);
  }
}
