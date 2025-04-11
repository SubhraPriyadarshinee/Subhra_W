package com.walmart.move.nim.receiving.mfc.repositories;

import com.walmart.move.nim.receiving.mfc.entity.DecantAudit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DecantAuditRepository extends JpaRepository<DecantAudit, Long> {

  Optional<DecantAudit> findByCorrelationId(String corelationId);
}
