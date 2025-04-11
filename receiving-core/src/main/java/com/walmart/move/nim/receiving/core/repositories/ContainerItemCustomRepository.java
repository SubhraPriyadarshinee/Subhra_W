package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom Queries around Container and Container Item tables
 *
 * @author vn50o7n
 */
@Repository
@Transactional(readOnly = true)
public class ContainerItemCustomRepository {

  @PersistenceContext EntityManager entityManager;

  /**
   * Gets given Containers total quantity in eaches grouped by purchase reference number and
   * purchase reference line number for a particular delivery number.
   *
   * @param deliveryNumber
   * @return List<ContainerPoLineQuantity>
   */
  public List<ContainerPoLineQuantity> getContainerQuantity(Long deliveryNumber) {
    return entityManager
        .createNamedQuery("ContainerItem.PoLineQuantity")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  public List<ReceipPutawayQtySummaryByContainer> getReceiptPutawayQtySummaryByDeliveryNumber(
      Long deliveryNumber) {
    return entityManager
        .createNamedQuery("ContainerItem.ReceiptPutawayQtySummary")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter("containerStatus", ReceivingConstants.STATUS_PUTAWAY_COMPLETE)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }
}
