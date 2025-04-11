package com.walmart.move.nim.receiving.fixture.repositories;

import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlTowerTrackerRepository extends JpaRepository<ControlTowerTracker, Long> {

  List<ControlTowerTracker> findBySubmissionStatusNotAndRetriesCountLessThanAndCreateTsLessThan(
      EventTargetStatus eventTargetStatus,
      Integer retriesCount,
      Date currentTime,
      Pageable pageable);

  ControlTowerTracker findByLpn(String lpn);
}
