package com.walmart.move.nim.receiving.acc.repositories;

import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository class for UserLocation table
 *
 * @author s0g015w
 */
@Repository
public interface UserLocationRepo extends JpaRepository<UserLocation, Long> {

  Optional<UserLocation> findByUserId(String userId);

  List<UserLocation> findAllByLocationId(String locationId);

  List<UserLocation> findAllByLocationIdOrParentLocationId(
      String locationId, String parentLocationId);

  void deleteByLocationId(String locationId);

  List<UserLocation> findAllByUserId(String user);

  UserLocation findFirstByLocationIdOrderByCreateTsDesc(String locationId);
}
