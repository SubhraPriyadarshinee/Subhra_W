package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelMetaData;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelMetaDataRepository extends JpaRepository<LabelMetaData, Long> {
  List<LabelMetaData> findAllByLabelIdIn(Set<Integer> labelIds);
}
