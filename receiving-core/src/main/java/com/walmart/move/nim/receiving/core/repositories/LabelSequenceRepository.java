package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelSequence;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import java.util.Date;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelSequenceRepository extends JpaRepository<LabelSequence, Long> {

  LabelSequence findByMustArriveBeforeDateAndPurchaseReferenceLineNumberAndItemNumberAndLabelType(
      Date mustArriveBeforeDate,
      int purchaseReferenceLineNumber,
      long itemNumber,
      LabelType labelType);
}
