package com.walmart.move.nim.receiving.endgame.repositories;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.LabelSummary;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** This is a custom PreLabel Repository class */
@Repository
@Transactional(readOnly = true)
public class PreLabelDataCustomRepository {
  private static Logger LOGGER = LoggerFactory.getLogger(PreLabelDataCustomRepository.class);

  @PersistenceContext EntityManager entityManager;

  /**
   * This method gives the tpl and tcl count
   *
   * @param deliveryNumber Delivery Number
   * @param labelType Label type
   * @return List of LabelCount
   */
  @InjectTenantFilter
  public List<LabelSummary> findLabelSummary(Long deliveryNumber, String labelType) {

    List<LabelSummary> labelSummary = new ArrayList<>();

    try {
      labelSummary =
          labelType.equalsIgnoreCase(EndgameConstants.ALL)
              ? entityManager
                  .createNamedQuery("PreLabelData.getSummaryDetailsByDeliveryNumber")
                  .setParameter(EndgameConstants.DELIVERY_NUMBER, deliveryNumber)
                  .getResultList()
              : entityManager
                  .createNamedQuery("PreLabelData.getSummaryDetailsByDeliveryNumberAndType")
                  .setParameter(EndgameConstants.DELIVERY_NUMBER, deliveryNumber)
                  .setParameter("labelType", LabelType.valueOf(labelType))
                  .getResultList();

    } catch (Exception ex) {
      LOGGER.error(
          "Error While Executing the query to find TCL TPL count for the DeliveryNumber {} and LabelType {} Exception : {}",
          deliveryNumber,
          labelType,
          ex);
    }

    return labelSummary;
  }
}
