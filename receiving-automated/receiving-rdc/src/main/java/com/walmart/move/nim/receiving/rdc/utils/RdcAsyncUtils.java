package com.walmart.move.nim.receiving.rdc.utils;

import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.LabelUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.label.LabelStatus;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RdcAsyncUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcAsyncUtils.class);
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * This method void LPNs at Hawkeye end
   *
   * @param trackingIdList
   * @param httpHeaders
   */
  @Async
  public void updateLabelStatusVoidToHawkeye(List<String> trackingIdList, HttpHeaders httpHeaders) {
    HttpHeaders forwardableHeaders =
        ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    // Setting tenant context as it is an async function
    TenantContext.setFacilityNum(
        Integer.valueOf(
            Objects.requireNonNull(forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM))
                .get(0)));
    TenantContext.setFacilityCountryCode(
        Objects.requireNonNull(forwardableHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE))
            .get(0));
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false)
        && org.apache.commons.collections4.CollectionUtils.isNotEmpty(trackingIdList)) {
      LOGGER.info(
          "Updating the label LPN status to VOID for lpn's of size:{}", trackingIdList.size());
      List<LabelUpdateRequest> labelUpdateRequests = new ArrayList<>();
      trackingIdList.forEach(
          trackingId -> {
            LabelUpdateRequest labelUpdateRequest = new LabelUpdateRequest();
            labelUpdateRequest.setLpn(trackingId);
            labelUpdateRequest.setStatus(LabelStatus.VOID.name());
            labelUpdateRequests.add(labelUpdateRequest);
          });
      try {
        hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequests, httpHeaders);
      } catch (ReceivingBadDataException | ReceivingInternalException e) {
        LOGGER.error(
            "Update label status to VOID failed errorCode: {} description: {}",
            e.getErrorCode(),
            e.getDescription(),
            e);
      }
    }
  }

  /**
   * Label Update to Hawkeye - This method can be called to update the status as DOWNLOADED of the
   * Label to Hawkeye
   *
   * @param labelDataList
   * @param httpHeaders
   */
  @Async
  public void labelUpdateToHawkeye(HttpHeaders httpHeaders, List<LabelData> labelDataList) {
    HttpHeaders forwardableHeaders =
        ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    // Setting tenant context as it is an async function
    TenantContext.setFacilityNum(
        Integer.valueOf(
            Objects.requireNonNull(forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM))
                .get(0)));
    TenantContext.setFacilityCountryCode(
        Objects.requireNonNull(forwardableHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE))
            .get(0));
    List<LabelUpdateRequest> labelUpdateRequests = new ArrayList<>();
    labelDataList.forEach(
        labelData -> {
          LabelUpdateRequest labelUpdateRequest = new LabelUpdateRequest();
          labelUpdateRequest.setLpn(labelData.getTrackingId());
          labelUpdateRequest.setStatus(LabelStatus.DOWNLOADED.name());
          labelUpdateRequests.add(labelUpdateRequest);
        });
    try {
      hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequests, httpHeaders);
    } catch (ReceivingBadDataException | ReceivingInternalException e) {
      LOGGER.error(
          "Update label status to VOID failed errorCode: {} description: {}",
          e.getErrorCode(),
          e.getDescription(),
          e);
    }
  }
}
