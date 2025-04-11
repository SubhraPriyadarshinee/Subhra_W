package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.PurgeData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurgeDataRepository extends JpaRepository<PurgeData, Long> {}
