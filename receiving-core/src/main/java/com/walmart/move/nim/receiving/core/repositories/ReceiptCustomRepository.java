package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ReceiptCustomRepository {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptCustomRepository.class);

  @PersistenceContext EntityManager entityManager;

  /**
   * This method is responsible for providing the received quantity summary in eaches grouped by
   * purchase reference number and purchase reference line number for a particular delivery number.
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryResponse>
   */
  public List<ReceiptSummaryResponse> receivedQtySummaryInEachesByDelivery(Long deliveryNumber) {
    return entityManager
        .createNamedQuery("Receipt.receivedQtySummaryInEachesByDelivery")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  /**
   * This method is responsible for providing the received quantity summary in eaches grouped by
   * purchase reference number and purchase reference line number for a particular delivery number
   * along with Vnpk and whpk.
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryResponse>
   */
  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryInEAByDelivery(Long deliveryNumber) {
    return entityManager
        .createNamedQuery("Receipt.receivedQtySummaryInEAByDelivery")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  /**
   * This method is responsible for providing the received quantity summary in vnpk grouped by
   * purchase reference number and purchase reference line number for a particular delivery number.
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryResponse>
   */
  public List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber) {
    return entityManager
        .createNamedQuery("Receipt.receivedQtySummaryInVnpkByDelivery")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  /**
   * This method is responsible for providing the received quantity summary in vnpk grouped by
   * purchase reference number for a particular delivery number.
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryResponse>
   */
  public List<ReceiptSummaryResponse> receivedQtySummaryByPoInVnpkByDelivery(Long deliveryNumber) {
    return entityManager
        .createNamedQuery("Receipt.receivedQtySummaryByPoInVnpkByDelivery")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  /**
   * This method is responsible for providing the received quantity summary in vnpk grouped by po
   * and poLine for a particular delivery number.
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryResponse>
   */
  public List<ReceiptSummaryResponse> receivedQtySummaryByPoLineInVnpkByDelivery(
      Long deliveryNumber, String purchaseReferenceNumber) {
    return entityManager
        .createNamedQuery("Receipt.receivedQtySummaryByPoLineInVnpkByDelivery")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  /**
   * This method is responsible for providing the received quantity summary in vnpk by delivery
   * number and po number
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryVnpkResponse>
   */
  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryInEachesByDeliveryAndPo(
      Long deliveryNumber, String purchaseReferenceNumber) {
    return entityManager
        .createNamedQuery("Receipt.receivedQtySummaryInEachesByDeliveryAndPo")
        .setParameter("deliveryNumber", deliveryNumber)
        .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
        .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setParameter(
            ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .getResultList();
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public ReceiptSummaryQtyByPoAndPoLineResponse receivedQtyByPoAndPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    try {
      return (ReceiptSummaryQtyByPoAndPoLineResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByPoAndPoLine")
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public ReceiptSummaryQtyByPoAndPoLineResponse receivedQtyByPoAndPoLineInEach(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    try {
      return (ReceiptSummaryQtyByPoAndPoLineResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByPoAndPoLineInEach")
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity across Po's and delivery Number.
   *
   * @param purchaseReferenceNumber
   * @return
   */
  public ReceiptSummaryQtyByPoAndDeliveryResponse receivedQtyByPoAndDeliveryNumber(
      String purchaseReferenceNumber, Long deliveryNumber) {
    try {
      return (ReceiptSummaryQtyByPoAndDeliveryResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByPoWithDelivery")
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("deliveryNumber", deliveryNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity in both cases and pallets across
   * Po's and delivery Number.
   *
   * @param purchaseReferenceNumbers list of POs
   * @param deliveryNumber delivery number
   * @return
   */
  public List<ReceiptSummaryResponse> receivedQtyAndPalletQtyByPoAndDeliveryNumber(
      Set<String> purchaseReferenceNumbers, Long deliveryNumber) {
    try {
      TypedQuery<ReceiptSummaryResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyAndPalletQtyByPoWithDelivery", ReceiptSummaryResponse.class);
      query.setParameter("purchaseReferenceNumbers", purchaseReferenceNumbers);
      query.setParameter("deliveryNumber", deliveryNumber);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for purchaseReferenceNumber {} and delivery {}, error {}",
          purchaseReferenceNumbers,
          deliveryNumber,
          ExceptionUtils.getStackTrace(e));
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity by problem tag id
   *
   * @param problemId problem tag id
   * @return receipt summary
   */
  public ReceiptSummaryQtyByProblemIdResponse receivedQtyByProblemIdInVnpk(String problemId) {

    try {
      return (ReceiptSummaryQtyByProblemIdResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByProblemIdInVnpk")
              .setParameter("problemId", problemId)
              .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
              .setParameter(
                  ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity by problem tag id
   *
   * @param problemId problem tag id
   * @return receipt summary
   */
  public ReceiptSummaryQtyByProblemIdResponse getReceivedQtyByProblemIdInEa(String problemId) {
    try {
      return (ReceiptSummaryQtyByProblemIdResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByProblemIdInEa")
              .setParameter("problemId", problemId)
              .setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
              .setParameter(
                  ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }
  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param purchaseReferenceNumberList
   * @param purchaseReferenceLineNumberSet
   * @return
   */
  public List<ReceiptSummaryEachesResponse> receivedQtyByPoAndPoLineList(
      List<String> purchaseReferenceNumberList, Set<Integer> purchaseReferenceLineNumberSet) {

    try {
      TypedQuery<ReceiptSummaryEachesResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyByPoAndPoLineList", ReceiptSummaryEachesResponse.class);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for purchaseReferenceNumber {} and purchaseReferenceLineNumber {} error {}",
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryEachesResponse> receivedQtyByPoAndPoLinesAndDelivery(
      Long deliveryNumber,
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet) {
    try {
      TypedQuery<ReceiptSummaryEachesResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyByPoAndPoLineForDelivery", ReceiptSummaryEachesResponse.class);
      query.setParameter("deliveryNumber", deliveryNumber);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for delivery {} purchaseReferenceNumber {} and purchaseReferenceLineNumber {} error {}",
          deliveryNumber,
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyByPoAndPoLineListWithoutDelivery(
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet,
      Long deliveryNumber) {

    try {
      TypedQuery<ReceiptSummaryQtyByPoAndPoLineResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyByPoAndPoLineListWithoutDelivery",
              ReceiptSummaryQtyByPoAndPoLineResponse.class);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      query.setParameter("deliveryNumber", deliveryNumber);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for purchaseReferenceNumber {} and purchaseReferenceLineNumber {} not for delivery {}, error {}",
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          deliveryNumber,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param purchaseReferenceNumber
   * @return
   */
  public ReceiptSummaryQtyByPoAndPoLineResponse receivedQtyByPoForPoCon(
      String purchaseReferenceNumber) {

    try {
      return (ReceiptSummaryQtyByPoAndPoLineResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByPoAndPoLine")
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public ReceiptSummaryQtyByPoAndPoLineResponse receivedQtyByDeliveryPoAndPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    try {
      return (ReceiptSummaryQtyByPoAndPoLineResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByDeliveryPoAndPoLine")
              .setParameter("deliveryNumber", deliveryNumber)
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public ReceiptSummaryQtyByPoAndPoLineResponse receivedQtyByDeliveryPoAndPoLineInEaches(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    try {
      return (ReceiptSummaryQtyByPoAndPoLineResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByDeliveryPoAndPoLineInEaches")
              .setParameter("deliveryNumber", deliveryNumber)
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryByDelivery(Long deliveryNumber) {

    try {
      return (List<ReceiptSummaryVnpkResponse>)
          entityManager
              .createNamedQuery("Receipt.receivedQtySummaryByDelivery")
              .setParameter("deliveryNumber", deliveryNumber)
              .getResultList();
    } catch (NoResultException e) {
      LOGGER.error("No record found in receipt for deliveryNumber = {} ", deliveryNumber);
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryVnpkResponse> receiptSummaryByDelivery(Long deliveryNumber) {

    try {
      return (List<ReceiptSummaryVnpkResponse>)
          entityManager
              .createNamedQuery("Receipt.receiptSummaryByDelivery")
              .setParameter("deliveryNumber", deliveryNumber)
              .getResultList();
    } catch (NoResultException e) {
      LOGGER.error("No record found in receipt for deliveryNumber = {} ", deliveryNumber);
      return Collections.emptyList();
    }
  }

  /**
   * This method is responsible for providing the received quantity in VNPK across many POs and
   * POLines.
   *
   * @param purchaseReferenceNumberList list of purchase reference number
   * @param purchaseReferenceLineNumberSet set of purchase reference line number
   * @return
   */
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInVNPKByPoAndPoLineList(
      List<String> purchaseReferenceNumberList, Set<Integer> purchaseReferenceLineNumberSet) {
    try {
      TypedQuery<ReceiptSummaryQtyByPoAndPoLineResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyInVNPKByPoAndPoLineList",
              ReceiptSummaryQtyByPoAndPoLineResponse.class);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for purchaseReferenceNumber {} and purchaseReferenceLineNumber {}, error {}",
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInEaByPoAndPoLineList(
      List<String> purchaseReferenceNumberList, Set<Integer> purchaseReferenceLineNumberSet) {
    try {
      TypedQuery<ReceiptSummaryQtyByPoAndPoLineResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyInEaByPoAndPoLineList",
              ReceiptSummaryQtyByPoAndPoLineResponse.class);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for purchaseReferenceNumber {} and purchaseReferenceLineNumber {}, error {}",
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  /**
   * This method is responsible for providing the received quantity in VNPK across many POs and
   * POLines for a particular delivery.
   *
   * @param purchaseReferenceNumberList list of purchase reference number
   * @param purchaseReferenceLineNumberSet set of purchase reference line number
   * @return
   */
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInVNPKByDeliveryPoAndPoLineList(
      Long deliveryNumber,
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet) {
    try {
      TypedQuery<ReceiptSummaryQtyByPoAndPoLineResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyInVNPKByDeliveryPoAndPoLineList",
              ReceiptSummaryQtyByPoAndPoLineResponse.class);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      query.setParameter("deliveryNumber", deliveryNumber);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for delivery {} purchaseReferenceNumber {} and purchaseReferenceLineNumber {}, error {}",
          deliveryNumber,
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInEaByDeliveryPoAndPoLineList(
      Long deliveryNumber,
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet) {
    try {
      TypedQuery<ReceiptSummaryQtyByPoAndPoLineResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtyInEaByDeliveryPoAndPoLineList",
              ReceiptSummaryQtyByPoAndPoLineResponse.class);
      query.setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumberSet);
      query.setParameter("purchaseReferenceNumber", purchaseReferenceNumberList);
      query.setParameter("deliveryNumber", deliveryNumber);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for delivery {} purchaseReferenceNumber {} and purchaseReferenceLineNumber {}, error {}",
          deliveryNumber,
          purchaseReferenceNumberList,
          purchaseReferenceLineNumberSet,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  public RxReceiptsSummaryResponse getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      String ssccNumber) {
    try {
      return (RxReceiptsSummaryResponse)
          entityManager
              .createNamedQuery("Receipt.receivedQtyByDeliveryPoAndPoLineAndSSCC")
              .setParameter("deliveryNumber", deliveryNumber)
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumber)
              .setParameter("ssccNumber", ssccNumber)
              .getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  public List<ReceiptSummaryResponse> receivedQtySummaryByShipmentNumberForPoAndPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    try {
      return (List<ReceiptSummaryResponse>)
          entityManager
              .createNamedQuery("Receipt.receivedQtyInEAByShipmentNumberForPoPoLine")
              .setParameter("purchaseReferenceNumber", purchaseReferenceNumber)
              .setParameter("purchaseReferenceLineNumber", purchaseReferenceLineNumber)
              .getResultList();
    } catch (NoResultException e) {
      LOGGER.error(
          "No record found in receipt for purchaseReferenceNumber = {} and purchaseReferenceLineNumber = {}",
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryByDeliveryNumbers(
      List<String> deliveries) {
    try {
      List<Long> deliveryNumbers =
          deliveries.stream().map(Long::parseLong).collect(Collectors.toList());
      return (List<ReceiptSummaryVnpkResponse>)
          entityManager
              .createNamedQuery("Receipt.receiptsSummaryByDeliveries")
              .setParameter("deliveryNumbers", deliveryNumbers)
              .getResultList();
    } catch (NoResultException e) {
      LOGGER.error("No record found in receipt for deliveryNumbers: {}", deliveries);
      return new ArrayList<>();
    }
  }

  /**
   * This method is responsible for providing the received quantity summary grouped by purchase
   * reference number for a particular site.
   *
   * @param poNumbers list of POs
   * @return List<ReceiptQtySummaryByPoNumberResponse>
   */
  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryByPoNumbers(List<String> poNumbers) {

    try {
      return (List<ReceiptSummaryVnpkResponse>)
          entityManager
              .createNamedQuery("Receipt.receiptsSummaryByPoNumbers")
              .setParameter("poNumbers", poNumbers)
              .getResultList();
    } catch (NoResultException e) {
      LOGGER.error("No record found in receipt for poNumbers: {}", poNumbers);
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryByDeliveryPoResponse> receivedQtySummaryInVNPKByDeliveryPo(
      Long deliveryNumber, Set<String> purchaseReferenceNumbers) {
    try {
      TypedQuery<ReceiptSummaryByDeliveryPoResponse> query =
          entityManager.createNamedQuery(
              "Receipt.receivedQtySummaryByDeliveryAndPo",
              ReceiptSummaryByDeliveryPoResponse.class);
      query.setParameter("deliveryNumber", deliveryNumber);
      query.setParameter("purchaseReferenceNumbers", purchaseReferenceNumbers);
      return query.getResultList();
    } catch (NoResultException e) {
      LOGGER.warn(
          "No receipts available for delivery: {}, purchaseReferenceNumbers {}, error {}",
          deliveryNumber,
          purchaseReferenceNumbers,
          ExceptionUtils.getStackTrace(e));
      return new ArrayList<>();
    }
  }

  public List<ReceiptSummaryVnpkResponse> getReceivedPackCountSummaryByDeliveryNumber(
      Long deliveryNumber) {

    try {
      return (List<ReceiptSummaryVnpkResponse>)
          entityManager
              .createNamedQuery("Receipt.receivedPacksByDeliveryAndPo")
              .setParameter("deliveryNumber", deliveryNumber)
              .getResultList();
    } catch (NoResultException e) {
      LOGGER.error("No record found in receipt for deliveryNumber = {} ", deliveryNumber);
      return Collections.emptyList();
    }
  }
}
