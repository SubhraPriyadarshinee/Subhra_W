package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.repositories.LabelDownloadEventRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service(ReceivingConstants.LABEL_DOWNLOAD_EVENT_SERVICE)
public class LabelDownloadEventService {
  @Autowired private LabelDownloadEventRepository labelDownloadEventRepository;

  @Transactional
  @InjectTenantFilter
  public List<LabelDownloadEvent> saveAll(List<LabelDownloadEvent> labelDownloadEventList) {
    return labelDownloadEventRepository.saveAll(labelDownloadEventList);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelDownloadEvent> findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
      Long deliveryNumber, String purchaseReferenceNumber, Long itemNumber) {
    return labelDownloadEventRepository.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
        deliveryNumber, purchaseReferenceNumber, itemNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelDownloadEvent> findByDeliveryNumberAndItemNumber(
      Long deliveryNumber, Long itemNumber) {
    return labelDownloadEventRepository.findByDeliveryNumberAndItemNumber(
        deliveryNumber, itemNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelDownloadEvent> findByItemNumber(Long itemNumber) {
    return labelDownloadEventRepository.findByItemNumber(itemNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelDownloadEvent> findByPurchaseReferenceNumberAndItemNumber(
      String purchaseReferenceNumber, Long itemNumber) {
    return labelDownloadEventRepository.findByPurchaseReferenceNumberAndItemNumber(
        purchaseReferenceNumber, itemNumber);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<LabelDownloadEvent>
      findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
          Long deliveryNumber, String purchaseReferenceNumber, Long itemNumber, String status) {
    return labelDownloadEventRepository
        .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
            deliveryNumber, purchaseReferenceNumber, itemNumber, status);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<LabelDownloadEvent> findByItemNumberAndDeliveryNumberIn(
      Long itemNumber, List<Long> deliveryNumbers) {
    return labelDownloadEventRepository.findByItemNumberAndDeliveryNumberIn(
        itemNumber, deliveryNumbers);
  }
}
