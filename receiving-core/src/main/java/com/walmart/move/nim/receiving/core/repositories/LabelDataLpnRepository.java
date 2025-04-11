package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelDataLpnRepository extends JpaRepository<LabelDataLpn, Long> {

  LabelDataLpn findByLpn(String lpn);
}
