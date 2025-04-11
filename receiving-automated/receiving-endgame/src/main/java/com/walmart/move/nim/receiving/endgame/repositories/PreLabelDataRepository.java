package com.walmart.move.nim.receiving.endgame.repositories;

import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

/**
 * * Repository for {@link PreLabelData}
 *
 * @author sitakant
 */
public interface PreLabelDataRepository extends JpaRepository<PreLabelData, Long> {

  /**
   * This will update the pre-label record to a given {@link LabelStatus} and reason based on
   * trailer Number and currentStatus supplied
   *
   * @param labelStatus
   * @param expectedStatus
   * @param reason
   * @param deliveryNumber
   */
  @Modifying
  void updateStatus(
      @Param("currentStatus") LabelStatus expectedStatus,
      @Param("updatedStatus") LabelStatus labelStatus,
      @Param("reason") String reason,
      @Param("deliveryNumber") long deliveryNumber);

  Optional<PreLabelData> findByTcl(String tcl);

  long countByStatusInAndDeliveryNumber(List<LabelStatus> labelStatus, long deliveryNumber);

  void deleteByDeliveryNumber(long deliveryNumber);

  List<PreLabelData> findByDeliveryNumber(long deliveryNumber);

  List<PreLabelData> findByDeliveryNumberAndStatus(long deliveryNumber, LabelStatus labelStatus);

  /**
   * This method will fetch the PreLabelData based on the DeliveryNumber and the pageable
   *
   * @param deliveryNumber DeliveryNumber
   * @param pageable pageable
   * @return PreLabelData
   */
  Page<PreLabelData> findByDeliveryNumber(long deliveryNumber, Pageable pageable);

  /**
   * This method will fetch the PreLabelData based on the DeliveryNumber,Type and the pageable
   *
   * @param deliveryNumber DeliveryNumber
   * @param labelType labelType
   * @param pageable pageable
   * @return PreLabelData
   */
  Page<PreLabelData> findByDeliveryNumberAndType(
      long deliveryNumber, LabelType labelType, Pageable pageable);

  List<PreLabelData> findByDeliveryNumberAndCaseUpcAndStatusAndDiverAckEventIsNotNull(
      long deliveryNumber, String upc, LabelStatus labelStatus);
}
