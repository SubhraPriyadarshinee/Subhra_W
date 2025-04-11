package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ContainerErrorResponse;
import com.walmart.move.nim.receiving.core.model.PalletsHoldRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;

/**
 * Interface for pallet hold
 *
 * @author lkotthi
 */
public interface PutOnHoldService {

  /**
   * This functions puts pallets on hold
   *
   * @param palletsHoldRequest
   * @param httpHeaders
   * @return List<ContainerErrorResponse>
   */
  List<ContainerErrorResponse> palletsOnHold(
      PalletsHoldRequest palletsHoldRequest, HttpHeaders httpHeaders);

  List<ContainerErrorResponse> palletsOffHold(
      PalletsHoldRequest palletsHoldRequest, HttpHeaders httpHeaders);
}
