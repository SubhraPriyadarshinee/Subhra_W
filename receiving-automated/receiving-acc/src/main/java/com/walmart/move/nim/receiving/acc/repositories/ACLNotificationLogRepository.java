package com.walmart.move.nim.receiving.acc.repositories;

import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ACLNotificationLogRepository extends JpaRepository<NotificationLog, String> {

  List<NotificationLog> findByLocationId(String locationId, Pageable pageable);

  void deleteByLocationId(String locationId);

  List<NotificationLog> findAllByLogTsBetween(Date fromDate, Date toDate);

  List<NotificationLog> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);
}
