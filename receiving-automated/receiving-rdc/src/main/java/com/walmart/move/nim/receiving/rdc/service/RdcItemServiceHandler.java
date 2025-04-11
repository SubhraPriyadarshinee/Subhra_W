package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.DefaultItemServiceHandler;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RdcItemServiceHandler extends DefaultItemServiceHandler {
  @Autowired private NgrRestApiClient ngrRestApiClient;
  @Autowired private LabelDataService labelDataService;
  @Autowired private ItemUpdateUtils itemUpdateUtils;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired LabelDownloadEventService labelDownloadEventService;
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcItemServiceHandler.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  @Override
  public void updateItemProperties(
      ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders) {
    super.updateItemProperties(itemOverrideRequest, httpHeaders);
    if (StringUtils.isEmpty(httpHeaders.getFirst(ReceivingConstants.REQUEST_ORIGINATOR))
        && !Boolean.TRUE.equals(itemOverrideRequest.getIsAtlasItem())
        && rdcReceivingUtils.isNGRServicesEnabled()) {
      ngrRestApiClient.updateItemProperties(itemOverrideRequest, httpHeaders);
    }
  }

  public void updateItemRejectReason(
      RejectReason rejectReason, ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders) {
    RejectReason oldRejectReason;
    List<LabelDownloadEvent> labelDownloadEvents = new ArrayList<>();
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false)
        && rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(
            itemOverrideRequest.getDeliveryNumber(), itemOverrideRequest.getItemNumber())) {
      // Send item update details to Hawkeye
      HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
          RdcUtils.createHawkeyeItemUpdateRequest(
              itemOverrideRequest.getItemNumber(),
              itemOverrideRequest.getDeliveryNumber(),
              rejectReason,
              null,
              true);
      try {
        hawkeyeRestApiClient.sendItemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
        // SCTA-10018 update reject reason into labelDownloadEvent
        if (Objects.nonNull(itemOverrideRequest.getDeliveryNumber())) {
          labelDownloadEvents =
              labelDownloadEventService.findByDeliveryNumberAndItemNumber(
                  itemOverrideRequest.getDeliveryNumber(), itemOverrideRequest.getItemNumber());
        } else {
          labelDownloadEvents =
              labelDownloadEventService.findByItemNumber(itemOverrideRequest.getItemNumber());
        }
        labelDownloadEvents
            .stream()
            .filter(
                labelDownloadEvent ->
                    Objects.nonNull(labelDownloadEvent.getRejectReason())
                        && !labelDownloadEvent.getRejectReason().equals(rejectReason))
            .forEach(
                labelDownloadEvent -> {
                  LOGGER.info(
                      "oldRejectReason - {} newRejectReason - {}",
                      labelDownloadEvent.getRejectReason(),
                      rejectReason);
                  labelDownloadEvent.setRejectReason(rejectReason);
                });
        LOGGER.info(
            "Reject reason {} updated in labelDownloadEvent for item {}",
            rejectReason,
            itemOverrideRequest.getItemNumber());
        labelDownloadEventService.saveAll(labelDownloadEvents);
      } catch (ReceivingBadDataException | ReceivingInternalException e) {
        LOGGER.error(
            "Item update failed for request with error code {} and description {}",
            e.getErrorCode(),
            e.getDescription());
      }
    }
  }
}
