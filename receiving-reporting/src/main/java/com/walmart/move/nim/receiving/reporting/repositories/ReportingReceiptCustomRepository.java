package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.reporting.model.UserCaseChannelTypeResponse;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ReportingReceiptCustomRepository {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReportingReceiptCustomRepository.class);

  @PersistenceContext(unitName = ReportingConstants.SECONDARY_PERSISTENCE_UNIT)
  EntityManager entityManager;

  /**
   * This method is responsible for providing the received quantity in for the User for that
   * OutBoundChannel
   */
  public List<UserCaseChannelTypeResponse> getUserCasesByChannelType(
      Integer facilityNum, String facilityCountryCode, Date fromDate, Date toDate) {

    try {
      Query query =
          entityManager.createNativeQuery(
              "select re.CREATE_USER_ID,recon.ocm,sum(re.QUANTITY)cases from RECEIPT re,\n"
                  + " (select r.PURCHASE_REFERENCE_NUMBER, r.PURCHASE_REFERENCE_LINE_NUMBER, max(ci.OUTBOUND_CHANNEL_METHOD) ocm from RECEIPT r, CONTAINER_ITEM ci\n"
                  + " where r.PURCHASE_REFERENCE_NUMBER = ci.PURCHASE_REFERENCE_NUMBER\n"
                  + " AND r.PURCHASE_REFERENCE_LINE_NUMBER = ci.PURCHASE_REFERENCE_LINE_NUMBER\n"
                  + " AND r.facilityNum = ?1 and r.facilityCountryCode = ?2 and r.CREATE_TS between ?3 and ?4\n"
                  + " group by r.PURCHASE_REFERENCE_NUMBER, r.PURCHASE_REFERENCE_LINE_NUMBER) recon\n"
                  + " where re.PURCHASE_REFERENCE_NUMBER = recon.PURCHASE_REFERENCE_NUMBER\n"
                  + " AND re.PURCHASE_REFERENCE_LINE_NUMBER = recon.PURCHASE_REFERENCE_LINE_NUMBER\n"
                  + " AND re.CREATE_TS between ?3 and ?4 "
                  + " group by re.CREATE_USER_ID, recon.ocm");

      query.setParameter(1, facilityNum);
      query.setParameter(2, facilityCountryCode);
      query.setParameter(3, fromDate, TemporalType.DATE);
      query.setParameter(4, toDate, TemporalType.DATE);

      List<Object[]> resultLists = query.getResultList();
      List<UserCaseChannelTypeResponse> userCaseChannelTypeResponses =
          new ArrayList<>(resultLists.size());
      for (Object[] resultList : resultLists) {
        UserCaseChannelTypeResponse userChannelTypeResponse = new UserCaseChannelTypeResponse();
        userChannelTypeResponse.setUser(String.valueOf(resultList[0]));
        userChannelTypeResponse.setChannelType(String.valueOf(resultList[1]));
        userChannelTypeResponse.setCasesCount(((Number) resultList[2]).longValue());

        userCaseChannelTypeResponses.add(userChannelTypeResponse);
      }
      return userCaseChannelTypeResponses;

    } catch (NoResultException e) {
      LOGGER.warn(
          "No User Cases found for the Outbound Channel {} ", ExceptionUtils.getStackTrace(e));
      return null;
    }
  }
}
