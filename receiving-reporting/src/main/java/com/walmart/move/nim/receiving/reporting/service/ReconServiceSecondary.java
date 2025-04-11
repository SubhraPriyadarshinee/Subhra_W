package com.walmart.move.nim.receiving.reporting.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.ReportingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.PrintingAndLabellingService;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingContainerRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingInstructionRepository;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class ReconServiceSecondary {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReconServiceSecondary.class);
  @Autowired private ReportingInstructionRepository instructionRepository;
  @Autowired private ReportingContainerRepository containerRepository;
  @Autowired private ReportPersisterService reportPersisterService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private RetryService retryService;

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  @Autowired
  private InstructionService instructionService;

  @Autowired protected InstructionHelperService instructionHelperService;

  @Autowired private PrintingAndLabellingService printingAndLabellingService;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private Gson gson;
  private ReportingUtils reportingUtils;

  @Transactional
  public List<DcFinReconciledDate> getReconciledDataSummaryByTime(
      Date fromDate, Date toDate, HttpHeaders headers) throws ReceivingException {
    reportingUtils = new ReportingUtils();
    int setInputTimeIntervalinHrs = 24;
    reportingUtils.validateDateFields(fromDate, toDate, setInputTimeIntervalinHrs);

    List<DcFinReconciledDate> dcFinReconciledDates =
        containerRepository.reconciledDataSummaryByTime(
            fromDate,
            toDate,
            headers.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
            Integer.valueOf(headers.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));

    return dcFinReconciledDates;
  }

  @Transactional
  public List<WFTResponse> postReceivedQtyGivenTimeAndActivityName(
      String activityName, Date fromDate, Date toDate, HttpHeaders headers)
      throws ReceivingException {
    if (Objects.isNull(fromDate) || Objects.isNull(toDate))
      throw new ReceivingException("REQUEST ERROR: fromDate and toDate are mandatory.");

    if (fromDate.compareTo(toDate) > 0) {
      throw new ReceivingException("REQUEST ERROR: fromDate should come before toDate.");
    }

    int timeIntervalinHrs =
        (int) TimeUnit.MILLISECONDS.toHours(toDate.getTime() - fromDate.getTime());
    if (timeIntervalinHrs > 24) {
      throw new ReceivingException("REQUEST ERROR: dateTime interval can not be more than 24 hrs.");
    }

    if (Objects.nonNull(activityName))
      return containerRepository.getReceivedQtyAgainstUserNameGivenActivityName(
          activityName,
          fromDate,
          toDate,
          headers.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
          Integer.valueOf(headers.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));

    return containerRepository.getReceivedQtyAgainstActivityNameByTime(
        fromDate,
        toDate,
        headers.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        Integer.valueOf(headers.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
  }

  public ReprintLabelResponse postLabels(
      ReprintLabelRequest reprintLabelRequest, HttpHeaders headers) {

    validateRequest(reprintLabelRequest);
    // set default toDate
    if (Objects.isNull(reprintLabelRequest.getToDate())) {
      reprintLabelRequest.setToDate(new Date());
    }

    List<Container> containerList = null;
    if (!StringUtils.isEmpty(reprintLabelRequest.getTrackingId())) {
      Container container =
          reportPersisterService.findContainerByTrackingId(reprintLabelRequest.getTrackingId());
      if (!Objects.isNull(container)) {
        LOGGER.info(
            "postLabels:Finding containers after given trackingId {} for user {}",
            container.getTrackingId(),
            container.getCreateUser());
        containerList =
            reportPersisterService.findContainerByDeliveryNumberAndUser(
                container.getDeliveryNumber(),
                container.getCreateUser(),
                container.getCreateTs(),
                reprintLabelRequest.getToDate());
      }
    }

    if (CollectionUtils.isEmpty(containerList) && reprintLabelRequest.getDeliveryNumber() != null) {
      String user = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      LOGGER.info(
          "postLabels:Finding containers for given delivery {} for user {}",
          reprintLabelRequest.getDeliveryNumber(),
          user);
      if (Objects.isNull(reprintLabelRequest.getFromDate())) {
        containerList =
            reportPersisterService.findContainersByDeliveryNumberAndCreateUserAndCreateTsBefore(
                reprintLabelRequest.getDeliveryNumber(), user, reprintLabelRequest.getToDate());
      } else {
        containerList =
            reportPersisterService.findContainerByDeliveryNumberAndUser(
                reprintLabelRequest.getDeliveryNumber(),
                user,
                reprintLabelRequest.getFromDate(),
                reprintLabelRequest.getToDate());
      }
    }
    if (!CollectionUtils.isEmpty(containerList)) {
      List<String> trackingIds = new ArrayList<>();
      Set<Long> instructionIds = new TreeSet<>();
      containerList.forEach(
          ctr -> {
            trackingIds.add(ctr.getTrackingId());
            if (!Objects.isNull(ctr.getInstructionId())) {
              instructionIds.add(ctr.getInstructionId());
            }
          });
      // get all instruction from DB
      List<Instruction> instructionListFromDB = getInstructionsFromDB(instructionIds);

      // prepare label and post
      prepareAndPostLabels(instructionListFromDB, headers);
      return ReprintLabelResponse.builder().trackingIds(trackingIds).build();
    }

    return ReprintLabelResponse.builder().build();
  }

  private void prepareAndPostLabels(List<Instruction> instructionListFromDB, HttpHeaders headers) {
    if (!CollectionUtils.isEmpty(instructionListFromDB)) {
      LOGGER.info(
          "postLabels: Preparing label data for {} instructions", instructionListFromDB.size());
      List<PrintableLabelDataRequest> printableLabelDataRequests = new ArrayList<>();

      instructionListFromDB.forEach(
          instruction -> {
            List<Map<String, Object>> printRequestList = new ArrayList<>();

            // get case labels
            instructionHelperService.createPrintRequestsForChildLabels(
                instruction, printRequestList);

            // get pallet labels
            instructionHelperService.createPrintRequestsForParentLabels(
                instruction, printRequestList);

            // Prepare PrintRequest for label data
            printRequestList.forEach(
                printRequest ->
                    printableLabelDataRequests.add(
                        printingAndLabellingService.getPrintableLabelDataRequest(printRequest)));
          });

      printingAndLabellingService.postToLabellingInBatches(printableLabelDataRequests, headers);
    }
  }

  private List<Instruction> getInstructionsFromDB(Set<Long> instructionIds) {
    List<Instruction> instructionListFromDB = new ArrayList<>();
    Collection<List<Long>> partitionedInstructionIdList =
        ReceivingUtils.batchifyCollection(instructionIds, 50);
    for (List<Long> instructionIdList : partitionedInstructionIdList) {
      LOGGER.info("postLabels: Getting instructions from DB for id {}", instructionIdList);
      List<Instruction> subInstructionList =
          reportPersisterService.findInstructionByIds(instructionIdList);
      if (!CollectionUtils.isEmpty(subInstructionList)) {
        instructionListFromDB.addAll(subInstructionList);
      }
    }
    return instructionListFromDB;
  }

  private void validateRequest(ReprintLabelRequest reprintLabelRequest) {
    if (StringUtils.isEmpty(reprintLabelRequest.getTrackingId())
        && reprintLabelRequest.getDeliveryNumber() == null) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA,
          String.format(
              ExceptionDescriptionConstants.INVALID_DATA_DESC, "trackingId,deliveryNumber"));
    }
  }

  public void resetJmsRetryCount(JmsRetryResetRequest jmsRetryResetRequest) {
    int batchSize = appConfig.getResetJmsretryBatchsize();
    Long maxRetryCount = 1l;
    RetryTargetType retryTargetType =
        RetryTargetType.valueOf(jmsRetryResetRequest.getActivityName());
    if (RetryTargetType.REST.equals(retryTargetType)) {
      maxRetryCount = appConfig.getRestMaxRetries();
    }
    int applicationType = retryTargetType.ordinal();
    EventTargetStatus[] eventTargetStatusArray = EventTargetStatus.values();
    if (jmsRetryResetRequest.getRuntimeStatus() >= eventTargetStatusArray.length) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_RUNTIME_STATUS, ReceivingConstants.INVALID_RUNTIME_STATUS);
    }
    retryService.resetJmsRetryCount(
        batchSize,
        maxRetryCount,
        applicationType,
        jmsRetryResetRequest.getFromDate(),
        jmsRetryResetRequest.getToDate(),
        jmsRetryResetRequest.getRuntimeStatus());
  }

  public void resetJmsRetryCountById(ActivityWithIdRequest activityWithIdRequest) {
    int batchSize = appConfig.getResetJmsretryBatchsize();
    Long maxRetryCount = 1l;
    RetryTargetType retryTargetType =
        RetryTargetType.valueOf(activityWithIdRequest.getActivityName());
    if (RetryTargetType.REST.equals(retryTargetType)) {
      maxRetryCount = appConfig.getRestMaxRetries();
    }
    int applicationType = retryTargetType.ordinal();
    retryService.resetJmsRetryCount(
        batchSize, maxRetryCount, applicationType, activityWithIdRequest.getIds());
  }
}
