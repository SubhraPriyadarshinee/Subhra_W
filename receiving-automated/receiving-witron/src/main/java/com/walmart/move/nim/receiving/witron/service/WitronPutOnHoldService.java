package com.walmart.move.nim.receiving.witron.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.ContainerErrorResponse;
import com.walmart.move.nim.receiving.core.model.PalletsHoldRequest;
import com.walmart.move.nim.receiving.core.service.PutOnHoldService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Witron specific pallets on hold service implementation
 *
 * @author lkotthi
 */
@Service
public class WitronPutOnHoldService implements PutOnHoldService {
  private static final Logger LOG = LoggerFactory.getLogger(WitronPutOnHoldService.class);

  @Autowired private WitronContainerService witronContainerService;

  @Override
  public List<ContainerErrorResponse> palletsOnHold(
      PalletsHoldRequest palletsHoldRequest, HttpHeaders httpHeaders) {
    List<ContainerErrorResponse> responseList = new ArrayList<>();
    for (String trackingId : palletsHoldRequest.getTrackingIds()) {
      ContainerErrorResponse containerErrorResponse;
      try {
        witronContainerService.palletOnHold(trackingId, httpHeaders);
        LOG.info("Pallet hold success for trackingId :{}", trackingId);
      } catch (ReceivingException receivingException) {
        containerErrorResponse =
            new ContainerErrorResponse(
                trackingId,
                receivingException.getErrorResponse().getErrorCode(),
                receivingException.getErrorResponse().getErrorMessage().toString());

        LOG.error("Pallet on hold failed, adding to error response :{}", containerErrorResponse);
        responseList.add(containerErrorResponse);
      }
    }

    return responseList;
  }

  @Override
  public List<ContainerErrorResponse> palletsOffHold(
      PalletsHoldRequest palletsHoldRequest, HttpHeaders httpHeaders) {
    List<ContainerErrorResponse> responseList = new ArrayList<>();
    for (String trackingId : palletsHoldRequest.getTrackingIds()) {
      ContainerErrorResponse containerErrorResponse;
      try {
        witronContainerService.palletOffHold(trackingId, httpHeaders);
        LOG.info("Pallet off hold success for trackingId :{}", trackingId);
      } catch (ReceivingException receivingException) {
        containerErrorResponse =
            new ContainerErrorResponse(
                trackingId,
                receivingException.getErrorResponse().getErrorCode(),
                receivingException.getErrorResponse().getErrorMessage().toString());
        LOG.error("Pallet off hold failed, adding to error response :{}", containerErrorResponse);
        responseList.add(containerErrorResponse);
      }
    }

    return responseList;
  }
}
