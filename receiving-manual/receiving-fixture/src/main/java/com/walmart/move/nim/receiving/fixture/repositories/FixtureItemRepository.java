package com.walmart.move.nim.receiving.fixture.repositories;

import com.walmart.move.nim.receiving.fixture.entity.FixtureItem;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FixtureItemRepository extends JpaRepository<FixtureItem, Long> {

  @Query(
      value =
          "SELECT * FROM Fixture_Item i WHERE i.item_Number LIKE %?1% OR i.description LIKE %?1%",
      countQuery =
          "SELECT count(*) FROM Fixture_Item i WHERE i.item_Number LIKE %?1% OR i.description LIKE %?1%",
      nativeQuery = true)
  List<FixtureItem> findByItemOrDescriptionContaining(String searchString, Pageable pageable);
}
