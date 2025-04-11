package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProblemRepository extends JpaRepository<ProblemLabel, Long> {

  ProblemLabel findProblemLabelByProblemTagId(String problemId);

  List<ProblemLabel> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  Optional<ProblemLabel> findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
      String problemStatus, String problemTagId, Long deliveryNUmber);

  Set<ProblemLabel> findProblemLabelByProblemStatusAndProblemTagIdInAndDeliveryNumber(
      String problemStatus, Set<String> problemTagIds, Long deliveryNumber);

  Optional<ProblemLabel> findOneByProblemTagIdAndDeliveryNumber(
      String problemTagId, Long deliveryNUmber);
}
