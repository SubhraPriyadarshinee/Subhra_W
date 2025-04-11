package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivingCounterRepository extends JpaRepository<ReceivingCounter, Long> {
  Optional<ReceivingCounter> findByType(String type);
}
