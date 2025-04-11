package com.walmart.move.nim.receiving.core.event.processor.unload;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.UnloaderInfo;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import java.util.List;
import org.springframework.http.HttpHeaders;

public interface DeliveryUnloaderProcessor {
  void publishDeliveryEvent(long deliveryNumber, String deliveryEventType, HttpHeaders headers)
      throws ReceivingBadDataException;

  void saveUnloaderInfo(UnloaderInfoDTO unloaderInfo, HttpHeaders headers)
      throws ReceivingBadDataException;

  List<UnloaderInfo> getUnloaderInfo(Long deliveryNumber, String poNumber, Integer poLineNumber)
      throws ReceivingBadDataException;
}
