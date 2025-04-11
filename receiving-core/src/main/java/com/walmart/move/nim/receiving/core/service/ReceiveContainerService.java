package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHeadersWithRequestOriginator;

import com.walmart.move.nim.receiving.core.common.GdcPutawayPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReceiveContainerService extends AbstractContainerService {
  private static final Logger log = LoggerFactory.getLogger(ReceiveContainerService.class);
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerService containerService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;

  /**
   * Wrapper function to receive container
   *
   * @param deliveryNumber
   * @param containerRequest
   * @param httpHeaders
   * @return Container
   * @throws ReceivingException
   */
  public Container receiveContainer(
      Long deliveryNumber, ContainerRequest containerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

      // Get the proper implementation of PutawayService based on tenant
      PutawayService putawayService =
          configUtils.getPutawayServiceByFacility(TenantContext.getFacilityNum().toString());

      // Prepare receipts
      List<Receipt> receipts =
          receiptService.prepareReceipts(deliveryNumber, containerRequest, userId);

      // Prepare container
      Container container =
          containerService.prepareContainer(deliveryNumber, containerRequest, userId);

      // Persist receipts and container
      containerPersisterService.createReceiptAndContainer(receipts, container);

      // Set child containers, this is placeholder for future enhancement
      Set<Container> childContainers = new HashSet<>();
      container.setChildContainers(childContainers);

      // Prepare JMS headers
      Map<String, Object> messageHeaders = getForwardableHeadersWithRequestOriginator(httpHeaders);
      messageHeaders.put(ReceivingConstants.IDEM_POTENCY_KEY, container.getTrackingId());

      // Publish container
      containerService.publishContainer(container, messageHeaders);

      // Publish putaway
      gdcPutawayPublisher.publishMessage(
          container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

      return container;
    } catch (Exception exception) {
      log.error("{}", ExceptionUtils.getStackTrace(exception));
      throw new ReceivingException(
          exception.getMessage(),
          HttpStatus.CONFLICT,
          ReceivingException.RECEIVE_CONTAINER_ERROR_CODE);
    }
  }
}
