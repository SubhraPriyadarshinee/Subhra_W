package com.walmart.move.nim.receiving.rdc.repositories;

import com.walmart.move.nim.receiving.rdc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.*;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class DockTagCustomRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(DockTagCustomRepository.class);

  @PersistenceContext(unitName = ReportingConstants.SECONDARY_PERSISTENCE_UNIT)
  private EntityManager entityManager;

  @SuppressWarnings({"unchecked"})
  public List<DockTagData> searchDockTags(
      Long deliveryNumber,
      String dockTagId,
      Date fromDate,
      Date toDate,
      String facilityNum,
      String facilityCountryCode) {

    List<DockTagData> dockTags = new ArrayList<>();
    try {
      StringBuilder queryBuilder = new StringBuilder();

      queryBuilder.append(
          "SELECT facilityCountryCode,facilityNum,CREATE_TS,CREATE_USER_ID,COMPLETE_TS,COMPLETE_USER_ID,DELIVERY_NUMBER,DOCK_TAG_ID,DOCK_TAG_STATUS, SCANNED_LOCATION,LAST_CHANGED_USER_ID,LAST_CHANGED_TS FROM DOCK_TAG WHERE ");

      queryBuilder.append(
          "facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode ");

      if (deliveryNumber != null) {
        queryBuilder.append("AND DELIVERY_NUMBER = :deliveryNumber AND COMPLETE_TS IS NULL ");
      }

      if (StringUtils.isNotBlank(dockTagId)) {
        queryBuilder.append("AND DOCK_TAG_ID = :dockTagId ");
      }

      if (fromDate != null) {
        queryBuilder.append("AND CREATE_TS >= :fromDate ");
      }

      if (toDate != null) {
        queryBuilder.append("AND CREATE_TS >= :toDate ");
      }

      Query query = entityManager.createNativeQuery(queryBuilder.toString());
      query.setParameter("facilityNum", facilityNum);
      query.setParameter("facilityCountryCode", facilityCountryCode);
      if (deliveryNumber != null) {
        query.setParameter("deliveryNumber", deliveryNumber);
      }

      if (StringUtils.isNotBlank(dockTagId)) {
        query.setParameter("dockTagId", dockTagId);
      }

      if (fromDate != null) {
        query.setParameter("fromDate", fromDate, TemporalType.DATE);
      }

      if (toDate != null) {
        query.setParameter("toDate", toDate, TemporalType.DATE);
      }

      List<Object[]> resultLists = query.getResultList();
      for (Object[] resultList : resultLists) {
        DockTagData data = new DockTagData();
        data.setFacilityCountryCode(String.valueOf(resultList[0]));
        data.setFacilityNum(Integer.valueOf(resultList[1].toString()));
        data.setCreateTs(RdcUtils.stringToDate(resultList[2].toString()).getTime());
        data.setCreateUserId(String.valueOf(resultList[3]));
        data.setCompleteTs(
            Objects.nonNull(resultList[4])
                ? RdcUtils.stringToDate(resultList[4].toString()).getTime()
                : null);
        data.setCompleteUserId(Objects.nonNull(resultList[5]) ? resultList[5].toString() : null);
        data.setDeliveryNumber(Long.valueOf(resultList[6].toString()));
        data.setDockTagId(String.valueOf(resultList[7]));
        data.setStatus(
            Objects.nonNull(resultList[8])
                ? InstructionStatus.values()[Integer.valueOf(resultList[8].toString())]
                : null);
        data.setScannedLocation(String.valueOf(resultList[9]));
        data.setLastChangedUserId(
            Objects.nonNull(resultList[10]) ? resultList[10].toString() : null);
        data.setLastChangedTs(
            Objects.nonNull(resultList[11])
                ? RdcUtils.stringToDate(resultList[11].toString()).getTime()
                : null);
        dockTags.add(data);
      }
    } catch (Exception e) {
      String fromDateStr = DateFormatUtils.format(fromDate, ReceivingConstants.UTC_DATE_FORMAT);
      String toDateStr = DateFormatUtils.format(fromDate, ReceivingConstants.UTC_DATE_FORMAT);
      LOGGER.warn(
          "Error while fetching DockTag Details for deliveryNumber : {}, dockTagId : {}, fromDate : {}, toDate : {}",
          deliveryNumber,
          dockTagId,
          fromDateStr,
          toDateStr,
          e);
      return Collections.emptyList();
    }

    return dockTags;
  }
}
