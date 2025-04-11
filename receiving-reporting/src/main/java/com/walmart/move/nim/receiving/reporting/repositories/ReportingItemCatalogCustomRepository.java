package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.reporting.model.RxItemCatalogReportData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ReportingItemCatalogCustomRepository {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReportingItemCatalogCustomRepository.class);

  private static final String EXEMPT_ITEM_INSTRUCTION_CODE = "Build Container";

  @PersistenceContext(unitName = ReportingConstants.SECONDARY_PERSISTENCE_UNIT)
  private EntityManager entityManager;

  /**
   * This method is responsible for providing the received quantity in for the User for that
   * OutBoundChannel
   */
  public List<RxItemCatalogReportData> getRxItemCatalogReportData(
      List<Integer> facilityNum, String facilityCountryCode, Date fromDate, Date toDate) {

    try {
      Query query =
          entityManager.createNativeQuery(
              "SELECT DISTINCT icul.facilityNum, icul.CREATE_USER_ID, icul.CREATE_TS, icul.DELIVERY_NUMBER, "
                  + "icul.ITEM_NUMBER, icul.OLD_ITEM_UPC, icul.NEW_ITEM_UPC, icul.VENDOR_NUMBER, icul.VENDOR_STOCK_NUMBER, correlationId,"
                  + " d.INSTRUCTION_CODE FROM ITEM_CATALOG_UPDATE_LOG icul, ("
                  + "     SELECT DISTINCT i.facilityNum, JSON_VALUE(i.DELIVERY_DOCUMENT , '$.deliveryDocumentLines[0].itemNbr') as ITEM_NUMBER, "
                  + "     i.DELIVERY_NUMBER, JSON_VALUE(i.MOVE , '$.correlationID') AS correlationId, i.INSTRUCTION_CODE "
                  + "     FROM INSTRUCTION i WHERE i.facilityNum IN ?1 AND i.facilityCountryCode = ?2 AND"
                  + "     i.CREATE_TS BETWEEN ?3 AND ?4"
                  + ") d"
                  + " WHERE icul.DELIVERY_NUMBER = d.DELIVERY_NUMBER AND icul.ITEM_NUMBER = d.ITEM_NUMBER "
                  + " AND icul.facilityNum = d.facilityNum AND icul.CREATE_TS BETWEEN ?3 AND ?4");

      query.setParameter(1, facilityNum);
      query.setParameter(2, facilityCountryCode);
      query.setParameter(3, fromDate, TemporalType.DATE);
      query.setParameter(4, toDate, TemporalType.DATE);

      List<Object[]> resultLists = query.getResultList();
      List<RxItemCatalogReportData> userCaseChannelTypeResponses =
          new ArrayList<>(resultLists.size());
      for (Object[] resultList : resultLists) {

        RxItemCatalogReportData rxItemCatalogReportData = new RxItemCatalogReportData();
        rxItemCatalogReportData.setFacilityNum(Integer.valueOf(resultList[0].toString()));
        rxItemCatalogReportData.setCreateUserId(String.valueOf(resultList[1]));
        rxItemCatalogReportData.setCreateTs(String.valueOf(resultList[2]));
        rxItemCatalogReportData.setDeliveryNumber(String.valueOf(resultList[3]));
        rxItemCatalogReportData.setItemNumber(String.valueOf(resultList[4]));
        rxItemCatalogReportData.setOldItemUPC(String.valueOf(resultList[5]));
        rxItemCatalogReportData.setNewItemUPC(String.valueOf(resultList[6]));
        rxItemCatalogReportData.setVendorNumber(
            Objects.isNull(resultList[7]) ? null : String.valueOf(resultList[7]));
        rxItemCatalogReportData.setVendorStockNumber(
            Objects.isNull(resultList[8]) ? null : String.valueOf(resultList[8]));
        rxItemCatalogReportData.setCorrelationId(String.valueOf(resultList[9]));
        String exemptItem = String.valueOf(resultList[10]);
        rxItemCatalogReportData.setExemptItem(
            EXEMPT_ITEM_INSTRUCTION_CODE.equals(exemptItem) ? "Y" : "N");

        userCaseChannelTypeResponses.add(rxItemCatalogReportData);
      }
      return userCaseChannelTypeResponses;

    } catch (NoResultException e) {

      String facilityNumCsv =
          facilityNum.stream().map(String::valueOf).collect(Collectors.joining(","));
      String fromDateStr = DateFormatUtils.format(fromDate, ReceivingConstants.UTC_DATE_FORMAT);
      String toDateStr = DateFormatUtils.format(fromDate, ReceivingConstants.UTC_DATE_FORMAT);

      LOGGER.warn(
          "Error while fetching ItemCatalogData for facilityNum : {}, fromDate : {}, toDate : {}",
          facilityNumCsv,
          fromDateStr,
          toDateStr,
          e);
      return Collections.emptyList();
    }
  }
}
