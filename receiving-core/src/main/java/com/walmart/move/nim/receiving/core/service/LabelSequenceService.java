package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.LabelSequence;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.repositories.LabelSequenceRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import javax.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service(ReceivingConstants.LABEL_SEQUENCE_SERVICE)
public class LabelSequenceService {
  private static final Logger LOGGER = LoggerFactory.getLogger(LabelSequenceService.class);
  @Autowired private LabelSequenceRepository labelSequenceRepository;

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @InjectTenantFilter
  public LabelSequence findByMABDPOLineNumberItemNumberLabelType(
      Date mustArriveBeforeDate,
      int purchaseReferenceLineNumber,
      long itemNumber,
      LabelType labelType) {
    LOGGER.info(
        "Fetching LabelSequence List for mustArriveBeforeDate:{}, pOLineNumber:{} and itemNumber:{} and labelType:{}",
        mustArriveBeforeDate,
        purchaseReferenceLineNumber,
        itemNumber,
        labelType);
    return labelSequenceRepository
        .findByMustArriveBeforeDateAndPurchaseReferenceLineNumberAndItemNumberAndLabelType(
            mustArriveBeforeDate, purchaseReferenceLineNumber, itemNumber, labelType);
  }

  @Transactional
  @InjectTenantFilter
  public void save(LabelSequence labelSequence) {
    LOGGER.info("Persisting LabelSequence data for :{}", labelSequence);
    labelSequenceRepository.save(labelSequence);
  }
}
