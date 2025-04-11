package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.core.entity.DockTag;
import java.util.Date;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Instruction JPA repository
 *
 * @author sks0013
 */
public interface ReportingDockTagRepository extends JpaRepository<DockTag, Long> {

  Integer countByCreateTsBetween(Date fromDate, Date toDate);
}
