package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.ProcessingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProcessingInfoRepository extends JpaRepository<ProcessingInfo, Long> {

  @Transactional
  @InjectTenantFilter
  ProcessingInfo findBySystemContainerId(String systemContainerId);
}
