package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.LabelMetaData;
import com.walmart.move.nim.receiving.core.repositories.LabelMetaDataRepository;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelPersisterService {
  @Autowired LabelMetaDataRepository labelMetaDataRepository;

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<LabelMetaData> getLabelMetaDataByLabelIdsIn(Set<Integer> labelIds) {
    return labelMetaDataRepository.findAllByLabelIdIn(labelIds);
  }
}
