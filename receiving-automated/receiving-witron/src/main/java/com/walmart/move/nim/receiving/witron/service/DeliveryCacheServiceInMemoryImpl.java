package com.walmart.move.nim.receiving.witron.service;

import com.google.common.cache.Cache;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${enable.witron.app:false}")
public class DeliveryCacheServiceInMemoryImpl {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeliveryCacheServiceInMemoryImpl.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private Gson gson;

  @Resource(name = GdcConstants.WITRON_GDM_PO_LINE_RESPONSE_CACHE)
  private Cache<Long, Map<DeliveryCacheKey, DeliveryCacheValue>> deliveryByPoPOLineCache;

  public DeliveryCacheValue getDeliveryDetailsByPoPoLine(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      HttpHeaders headers)
      throws ReceivingException {
    DeliveryCacheKey key = new DeliveryCacheKey();
    key.setDeliveryNumber(deliveryNumber);
    key.setPurchaseReferenceNumber(purchaseReferenceNumber);
    key.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);

    Map<DeliveryCacheKey, DeliveryCacheValue> valueMap;
    try {
      valueMap = deliveryByPoPOLineCache.get(deliveryNumber, cacheLoader(deliveryNumber, headers));
      if (Objects.nonNull(valueMap)) {
        DeliveryCacheValue deliveryCacheValue = valueMap.get(key);
        if (Objects.isNull(deliveryCacheValue)) {
          LOGGER.info("DeliveryCacheValue not found for key :{}, so refreshing it.", key);
          deliveryByPoPOLineCache.invalidate(deliveryNumber);
          valueMap =
              deliveryByPoPOLineCache.get(deliveryNumber, cacheLoader(deliveryNumber, headers));
          LOGGER.info("Refreshed the deliveryCacheValue for key :{}", key);
          if (Objects.nonNull(valueMap)) {
            return valueMap.get(key);
          }
        } else {
          return valueMap.get(key);
        }
      }
    } catch (ExecutionException e) {
      LOGGER.error(
          "Error while fetching deliveryByPoPOLineCache for DELIVERY :{}, PO :{}, POLINE :{}",
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber,
          e);
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    }
    return null;
  }

  private Callable<? extends Map<DeliveryCacheKey, DeliveryCacheValue>> cacheLoader(
      Long deliveryNumber, HttpHeaders headers) {

    return new Callable<Map<DeliveryCacheKey, DeliveryCacheValue>>() {
      @Override
      public Map<DeliveryCacheKey, DeliveryCacheValue> call() throws Exception {
        Map<DeliveryCacheKey, DeliveryCacheValue> deliveryByPoPOLineMap = new HashMap<>();

        LOGGER.info("Enter cacheLoader() with deliveryNumber :{}", deliveryNumber);
        String deliveryByDeliveryNumberStr =
            deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, headers);
        GdmPOLineResponse gdmPOLineResponse =
            gson.fromJson(deliveryByDeliveryNumberStr, GdmPOLineResponse.class);
        for (DeliveryDocument deliveryDocumentObj : gdmPOLineResponse.getDeliveryDocuments()) {
          for (DeliveryDocumentLine deliveryDocumentLineObj :
              deliveryDocumentObj.getDeliveryDocumentLines()) {

            DeliveryCacheKey key = new DeliveryCacheKey();
            key.setDeliveryNumber(deliveryNumber);
            key.setPurchaseReferenceNumber(deliveryDocumentLineObj.getPurchaseReferenceNumber());
            key.setPurchaseReferenceLineNumber(
                deliveryDocumentLineObj.getPurchaseReferenceLineNumber());

            DeliveryCacheValue value = new DeliveryCacheValue();
            value.setTotalBolFbq(deliveryDocumentObj.getTotalBolFbq());
            value.setBolWeight(deliveryDocumentLineObj.getBolWeight());
            value.setTrailerId(gdmPOLineResponse.getTrailerId());
            value.setFreightTermCode(deliveryDocumentObj.getFreightTermCode());
            value.setScacCode(gdmPOLineResponse.getScacCode());
            value.setPurchaseReferenceLegacyType(
                deliveryDocumentObj.getPurchaseReferenceLegacyType());

            deliveryByPoPOLineMap.put(key, value);
          }
        }
        LOGGER.info("Exit cacheLoader() with deliveryNumber :{}", deliveryNumber);
        return deliveryByPoPOLineMap;
      }
    };
  }
}
