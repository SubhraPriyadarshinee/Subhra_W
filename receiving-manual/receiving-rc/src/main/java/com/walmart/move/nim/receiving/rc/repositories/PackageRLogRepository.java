package com.walmart.move.nim.receiving.rc.repositories;

import com.walmart.move.nim.receiving.rc.entity.PackageRLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageRLogRepository extends JpaRepository<PackageRLog, Long> {
  /**
   * This method is responsible to fetch all {@link PackageRLog} by packageBarcodeValue
   *
   * @param packageBarcodeValue
   * @return
   */
  List<PackageRLog> findByPackageBarCodeValue(String packageBarcodeValue);

  /**
   * This method is responsible to delete all {@link PackageRLog} by packageBarcodeValue
   *
   * @param packageBarcodeValue
   */
  void deleteByPackageBarCodeValue(String packageBarcodeValue);

  /**
   * This method is responsible for fetching all the {@link PackageRLog} those are having id greater
   * than @lastDeleteId
   *
   * @param lastDeleteId
   * @param pageable
   * @return
   */
  List<PackageRLog> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);
}
