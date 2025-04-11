package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.repositories.LabelDataLpnRepository;
import com.walmart.move.nim.receiving.core.repositories.LabelDataRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @author v0b03vz Service for LabelDataLpn entity */
@Service
public class LabelDataLpnService {

  @Autowired private LabelDataRepository labelDataRepository;
  @Autowired private LabelDataLpnRepository labelDataLpnRepository;

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Optional<LabelData> findLabelDataByLpn(String lpn) {
    return Optional.ofNullable(labelDataLpnRepository.findByLpn(lpn))
        .map(LabelDataLpn::getLabelDataId)
        .flatMap(labelDataId -> labelDataRepository.findById(labelDataId));
  }
}
