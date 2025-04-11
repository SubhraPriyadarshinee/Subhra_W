/** */
package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.repositories.PrintJobRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * @author a0b02ft Service responsible for providing implementation for print job related operations
 */
@Service(ReceivingConstants.PRINTJOB_SERVICE)
public class PrintJobService implements Purge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptService.class);
  @Autowired private PrintJobRepository printJobRepository;

  /**
   * Persist print job
   *
   * @param deliveryNumber
   * @param instructionId
   * @param printJobLpnSet
   * @param userId
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public PrintJob createPrintJob(
      Long deliveryNumber, Long instructionId, Set<String> printJobLpnSet, String userId) {
    PrintJob printJob = preparePrintJob(deliveryNumber, instructionId, printJobLpnSet, userId);

    return printJobRepository.save(printJob);
  }

  /**
   * Preparation of print job
   *
   * @param deliveryNumber
   * @param instructionId
   * @param printJobLpnSet
   * @param userId
   * @return
   */
  public PrintJob preparePrintJob(
      Long deliveryNumber, Long instructionId, Set<String> printJobLpnSet, String userId) {
    PrintJob printJob = new PrintJob();
    printJob.setDeliveryNumber(deliveryNumber);
    printJob.setInstructionId(instructionId);
    printJob.setLabelIdentifier(printJobLpnSet);
    printJob.setCreateUserId(userId);
    return printJob;
  }

  @Transactional
  @InjectTenantFilter
  public List<PrintJob> savePrintJobs(List<PrintJob> printJobs) {
    return printJobRepository.saveAll(printJobs);
  }

  @Transactional
  @InjectTenantFilter
  public List<PrintJob> getPrintJobsByDeliveryNumber(Long deliveryNumber) {
    return printJobRepository.findByDeliveryNumber(deliveryNumber);
  }

  @Transactional(readOnly = true)
  public List<PrintJob> getRecentlyPrintedLabelsByDeliveryNumber(
      Long deliveryNumber, Integer labelCount) {
    return printJobRepository.getRecentlyPrintedLabelsByDeliveryNumber(
        labelCount,
        deliveryNumber,
        TenantContext.getFacilityNum(),
        TenantContext.getFacilityCountryCode());
  }

  @Transactional(readOnly = true)
  public List<PrintJob> getRecentlyPrintedLabelsByDeliveryNumberAndUserId(
      Long deliveryNumber, String userId, Integer labelCount) {
    return printJobRepository.findRecentlyPrintedLabelsByDeliveryNumberAndUserId(
        labelCount,
        deliveryNumber,
        userId,
        TenantContext.getFacilityNum(),
        TenantContext.getFacilityCountryCode());
  }

  /**
   * This method will delete print job records which are created for integration test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public void deletePrintjobs(Long deliveryNumber) throws ReceivingException {
    List<PrintJob> printJobData = printJobRepository.findByDeliveryNumber(deliveryNumber);
    if (printJobData == null || printJobData.isEmpty()) {
      throw new ReceivingException(
          ReceivingException.NO_PRINTJOBS_FOR_DELIVERY, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    printJobRepository.deleteAll(printJobData);
  }

  /**
   * This method will get print job records based on instruction id which are created for
   * integration test
   *
   * @param instructionId
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public List<PrintJob> getPrintJobByInstruction(Long instructionId) throws ReceivingException {
    List<PrintJob> printJobData = printJobRepository.findByInstructionId(instructionId);

    if (printJobData == null || printJobData.isEmpty()) {
      throw new ReceivingException(
          ReceivingException.NO_PRINTJOBS_FOR_INSTRUCTION, HttpStatus.NOT_FOUND);
    }
    return printJobData;
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<PrintJob> printJobList =
        printJobRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    printJobList =
        printJobList
            .stream()
            .filter(printJob -> printJob.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(PrintJob::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(printJobList)) {
      LOGGER.info("Purge PRINTJOB: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = printJobList.get(printJobList.size() - 1).getId();

    LOGGER.info(
        "Purge PRINTJOB: {} records : ID {} to {} : START",
        printJobList.size(),
        printJobList.get(0).getId(),
        lastDeletedId);
    printJobRepository.deleteAll(printJobList);
    LOGGER.info("Purge PRINTJOB: END");
    return lastDeletedId;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumber(Long deliveryNumber) {
    printJobRepository.deleteByDeliveryNumber(deliveryNumber);
  }
}
