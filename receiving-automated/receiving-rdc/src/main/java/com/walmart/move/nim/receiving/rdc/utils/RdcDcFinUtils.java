package com.walmart.move.nim.receiving.rdc.utils;

/** @author s0g015w This is an utility class for DCFin specific requests */
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class RdcDcFinUtils {
  @Resource(name = RdcConstants.RDC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService rdcDeliveryMetaDataService;

  @Autowired private DCFinServiceV2 dcFinServiceV2;

  /**
   * This method makes a call to DCFin api for posting receipts
   *
   * @param containers
   * @param docType
   */
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.REST,
      externalCall = true,
      executionFlow = "DCFin-receipts-post")
  @Counted(
      name = "postToDCFinHitCount",
      level1 = "uwms-receiving",
      level2 = "RdcDcFinUtils",
      level3 = "postToDCFin")
  @Timed(
      name = "postToDCFinAPITimed",
      level1 = "uwms-receiving",
      level2 = "RdcDcFinUtils",
      level3 = "postToDCFin")
  @ExceptionCounted(
      name = "postToDCFinAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RdcDcFinUtils",
      level3 = "postToDCFin")
  public void postToDCFin(List<Container> containers, String docType) {
    containers.forEach(
        container -> {
          if (!CollectionUtils.isEmpty(container.getContainerItems())) {
            List<Distribution> distributions = new ArrayList<>();
            Distribution distribution =
                !CollectionUtils.isEmpty(container.getContainerItems().get(0).getDistributions())
                    ? container.getContainerItems().get(0).getDistributions().get(0)
                    : new Distribution();
            distribution.setAllocQty(container.getContainerItems().get(0).getQuantity());
            if (MapUtils.isNotEmpty(container.getDestination())
                && StringUtils.isNotBlank(
                    container.getDestination().get(ReceivingConstants.BU_NUMBER))) {
              distribution.setDestNbr(
                  Integer.parseInt(container.getDestination().get(ReceivingConstants.BU_NUMBER)));
            } else {
              distribution.setDestNbr(TenantContext.getFacilityNum());
            }
            distributions.add(distribution);
            container.getContainerItems().get(0).setDistributions(distributions);
          }
        });

    DeliveryMetaData deliveryMetaData =
        rdcDeliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(containers.get(0).getDeliveryNumber()))
            .orElse(null);

    dcFinServiceV2.postReceiptUpdateToDCFin(
        containers, ReceivingUtils.getHeaders(), true, deliveryMetaData, docType);
  }
}
