package com.walmart.move.nim.receiving.endgame.repositories;

import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * * This is the Repository for {@link SlottingDestination} entity
 *
 * @author sitakant
 */
public interface SlottingDestinationRepository extends JpaRepository<SlottingDestination, Long> {

  List<SlottingDestination> findByCaseUPCInAndSellerIdIn(
      List<String> caseUPCList, List<String> sellerIdList);

  List<SlottingDestination> findByCaseUPC(String caseUPC);

  Optional<SlottingDestination> findFirstByCaseUPCAndSellerId(String caseUPC, String sellerId);

  List<SlottingDestination> findByPossibleUPCsContains(String upc);
}
