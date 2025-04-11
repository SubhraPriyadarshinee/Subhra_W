package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class LabelDataCustomRepositoryImpl implements LabelDataCustomRepository {
  private static Logger LOGGER = LoggerFactory.getLogger(LabelDataCustomRepositoryImpl.class);

  @PersistenceContext EntityManager entityManager;

  private static final String LIKE_PRE_SUF = "%";

  @Override
  public PurchaseOrderInfo findByDeliveryNumberAndContainsLPN(Long deliveryNumber, String lpn) {
    PurchaseOrderInfo purchaseOrderInfo = null;
    try {
      List purchaseOrderInfoList =
          entityManager
              .createNamedQuery("LabelData.findByDeliveryAndContainsLPN")
              .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
              .setParameter(
                  ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
              .setParameter("deliveryNumber", deliveryNumber)
              .setParameter("lpn", lpn)
              .getResultList();
      if (!CollectionUtils.isEmpty(purchaseOrderInfoList)
          && (purchaseOrderInfoList.get(0) instanceof PurchaseOrderInfo)) {
        purchaseOrderInfo = (PurchaseOrderInfo) purchaseOrderInfoList.get(0);
      }
    } catch (NoResultException noResultException) {
      LOGGER.error(
          "No result for contains query. Params delivery number {} and lpn {}",
          deliveryNumber,
          lpn);
    } catch (Exception exception) {
      LOGGER.error(
          "Exception in contains query. Params delivery number {} and lpn {}",
          deliveryNumber,
          lpn,
          exception);
    }
    return purchaseOrderInfo;
  }

  @Override
  public LabelData findByDeliveryNumberAndUPCAndLabelType(
      Long deliveryNumber, String upc, LabelType labelType) {
    LabelData labelData = null;
    try {
      List labelDataList =
          entityManager
              .createNamedQuery("LabelData.findByDeliveryNumberAndUPCAndLabelType")
              .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
              .setParameter(
                  ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
              .setParameter("deliveryNumber", deliveryNumber)
              .setParameter("upc", upc)
              .setParameter("labelType", labelType.ordinal())
              .getResultList();
      if (!CollectionUtils.isEmpty(labelDataList) && (labelDataList.get(0) instanceof LabelData)) {
        labelData = (LabelData) labelDataList.get(0);
      }
    } catch (NoResultException noResultException) {
      LOGGER.error("No result for query params delivery number {} and upc {}", deliveryNumber, upc);
    }
    return labelData;
  }

  @Override
  public PurchaseOrderInfo findByDeliveryNumberAndLPNLike(Long deliveryNumber, String lpn) {
    PurchaseOrderInfo purchaseOrderInfo = null;
    try {
      StringBuilder enclosed = new StringBuilder(LIKE_PRE_SUF);
      enclosed.append(lpn);
      enclosed.append(LIKE_PRE_SUF);
      List purchaseOrderInfoList =
          entityManager
              .createNamedQuery("LabelData.findByDeliveryAndLPNLike")
              .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
              .setParameter(
                  ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
              .setParameter("deliveryNumber", deliveryNumber)
              .setParameter("lpn", enclosed.toString())
              .getResultList();
      if (!CollectionUtils.isEmpty(purchaseOrderInfoList)
          && (purchaseOrderInfoList.get(0) instanceof PurchaseOrderInfo)) {
        purchaseOrderInfo = (PurchaseOrderInfo) purchaseOrderInfoList.get(0);
      }
    } catch (NoResultException noResultException) {
      LOGGER.error(
          "No result for like query. Params delivery number {} and lpn {}", deliveryNumber, lpn);
    } catch (Exception exception) {
      LOGGER.error(
          "Exception in like query. Params delivery number {} and lpn {}",
          deliveryNumber,
          lpn,
          exception);
    }
    return purchaseOrderInfo;
  }
}
