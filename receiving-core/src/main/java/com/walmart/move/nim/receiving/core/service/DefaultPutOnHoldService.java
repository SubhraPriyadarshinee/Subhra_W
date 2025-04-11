package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ContainerErrorResponse;
import com.walmart.move.nim.receiving.core.model.PalletsHoldRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * This is the default implementation of pallets on hold Service
 *
 * @author lkotthi
 */
@Service
public class DefaultPutOnHoldService implements PutOnHoldService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPutOnHoldService.class);

  @Override
  public List<ContainerErrorResponse> palletsOnHold(
      PalletsHoldRequest palletsHoldRequest, HttpHeaders httpHeaders) {
    LOGGER.warn("No Implementation for DefaultPutOnHoldService#palletsOnHold");

    return new ArrayList<>();
  }

  public List<ContainerErrorResponse> palletsOffHold(
      PalletsHoldRequest palletsHoldRequest, HttpHeaders httpHeaders) {
    LOGGER.warn("No Implementation for DefaultPutOnHoldService#palletsOffHold");

    return new ArrayList<>();
  }
}
