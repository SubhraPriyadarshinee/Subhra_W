package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Instruction JPA repository
 *
 * @author sks0013
 */
@Repository
public interface DockTagRepository extends JpaRepository<DockTag, Long> {

  Integer countByDeliveryNumberAndDockTagStatusIn(
      Long deliveryNumber, List<InstructionStatus> dockTagStatus);

  Integer countByDeliveryNumber(Long deliveryNumber);

  DockTag findByDockTagId(String dockTagId);

  void deleteByDeliveryNumber(Long deliveryNumber);

  List<DockTag> findByDeliveryNumber(Long deliveryNumber);

  List<DockTag> findByDeliveryNumberIn(List<Long> deliveryNumber);

  List<DockTag> findByDeliveryNumberInAndDockTagStatusIn(
      List<Long> deliveryNumber, List<InstructionStatus> dockTagStatus);

  List<DockTag> findByDockTagIdIn(List<String> dockTagIds);

  List<DockTag> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  List<DockTag> findByDockTagStatusInAndCreateTsLessThanOrderByCreateTs(
      List<InstructionStatus> dockTagStatus, Date currentTime, Pageable pageable);

  int countByDockTagStatusIn(List<InstructionStatus> dockTagStatus);

  List<DockTag> findByDockTagStatusIn(List<InstructionStatus> dockTagStatus);

  List<DockTag> findByScannedLocationAndDockTagStatus(
      String scannedLocation, InstructionStatus dockTagStatus);
}
