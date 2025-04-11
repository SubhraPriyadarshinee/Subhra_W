package com.walmart.move.nim.receiving.reporting.service;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceivingProductivityResponseDTO;
import com.walmart.move.nim.receiving.reporting.advice.InjectTenantFilterSecondary;
import com.walmart.move.nim.receiving.reporting.model.UserCaseChannelTypeResponse;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingContainerRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingDockTagRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingInstructionRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingItemCatalogRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingReceiptCustomRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingReceiptRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @author sks0013 Service implementation for report queries */
@Service
public class ReportPersisterService {

  @Autowired private ReportingInstructionRepository instructionRepository;

  @Autowired private ReportingReceiptRepository receiptRepository;

  @Autowired private ReportingReceiptCustomRepository receiptCustomRepository;

  @Autowired private ReportingContainerRepository containerRepository;

  @Autowired private ReportingItemCatalogRepository itemCatalogRepository;

  @Autowired private ReportingDockTagRepository dockTagRepository;

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getUserCaseChannelTypeData")
  public List<UserCaseChannelTypeResponse> getUserCaseChannelTypeData(Date fromDate, Date toDate) {
    return receiptCustomRepository.getUserCasesByChannelType(
        TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode(), fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfPos")
  public Integer getCountOfPos(Date fromDate, Date toDate) {
    Integer countOfPos = receiptRepository.countDistinctPosByCreateTsBetween(fromDate, toDate);
    return Objects.nonNull(countOfPos) ? countOfPos : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfDeliveries")
  public Integer getCountOfDeliveries(Date fromDate, Date toDate) {
    Integer countOfDeliveries =
        receiptRepository.countDistinctDeliveryNumberByCreateTsBetween(fromDate, toDate);
    return Objects.nonNull(countOfDeliveries) ? countOfDeliveries : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfLabels")
  public Integer getCountOfLabels(Date fromDate, Date toDate) {
    Integer countOfLabels =
        containerRepository.countByCreateTsAfterAndCreateTsBefore(fromDate, toDate);
    return Objects.nonNull(countOfLabels) ? countOfLabels : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfSstkPalletsAndCases")
  public Pair<Integer, Integer> getCountOfSstkPalletsAndCases(Date fromDate, Date toDate) {
    return containerRepository.countOfSstkPalletsAndCases(fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfDaConPallets")
  public Integer getCountOfDaConPallets(Date fromDate, Date toDate) {
    Integer countOfPallets =
        containerRepository.countOfDaConPallets(
            fromDate,
            toDate,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
    return Objects.nonNull(countOfPallets) ? countOfPallets : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfDaNonConPalletsAndCases")
  public Pair<Integer, Integer> getCountOfDaNonConPalletsAndCases(Date fromDate, Date toDate) {
    return containerRepository.countOfDaNonConPalletsAndCases(fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfPoConPalletsAndCases")
  public Pair<Integer, Integer> getCountOfPoConPalletsAndCases(Date fromDate, Date toDate) {
    return containerRepository.countOfPoConPalletsAndCases(fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfDsdcPalletsAndCases")
  public Pair<Integer, Integer> getCountOfDsdcPalletsAndCases(Date fromDate, Date toDate) {
    return containerRepository.countOfDsdcPalletsAndCases(fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfPbylCasesAndCases")
  public Pair<Integer, Integer> getCountOfPbylPalletsAndCases(Date fromDate, Date toDate) {
    return containerRepository.countOfPbylPalletsAndCases(fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfAclCases")
  public Integer getCountOfAclCases(Date fromDate, Date toDate) {
    Integer countOfPallets =
        containerRepository.countByCreateTsBetweenAndOnConveyorIsTrueAndContainerExceptionIsNull(
            fromDate, toDate);
    return Objects.nonNull(countOfPallets) ? countOfPallets : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfAccManualCases")
  public Integer getCountOfAccManualCases(Date fromDate, Date toDate) {
    Integer countOfAccManualCases =
        containerRepository.countOfManualReceivingCases(fromDate, toDate);
    return Objects.nonNull(countOfAccManualCases) ? countOfAccManualCases : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfDaConCases")
  public Integer getCountOfDaConCases(Date fromDate, Date toDate) {
    Integer countOfDaConCases =
        containerRepository.countByContainerTypeAndCreateTsBetween("Vendor Pack", fromDate, toDate);
    return Objects.nonNull(countOfDaConCases) ? countOfDaConCases : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfUsers")
  public Integer getCountOfUsers(Date fromDate, Date toDate) {
    Integer countOfUsers =
        receiptRepository.countDistinctCreateUserByCreateTsBetween(fromDate, toDate);
    return Objects.nonNull(countOfUsers) ? countOfUsers : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfItems")
  public Integer getCountOfItems(Date fromDate, Date toDate) {
    Integer countOfItems = containerRepository.countOfItems(fromDate, toDate);
    return Objects.nonNull(countOfItems) ? countOfItems : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfVtrContainers")
  public Integer getCountOfVtrContainers(Date fromDate, Date toDate) {
    Integer countOfVtrContainers =
        containerRepository.countByContainerStatusAndCreateTsBetween(
            ReceivingConstants.STATUS_BACKOUT, fromDate, toDate);
    return Objects.nonNull(countOfVtrContainers) ? countOfVtrContainers : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfVtrCases")
  public Integer getCountOfVtrCases(Date fromDate, Date toDate) {

    Integer countOfVtrCases = receiptRepository.countByVtrCasesReceived(fromDate, toDate);
    return Objects.nonNull(countOfVtrCases) ? countOfVtrCases : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfProblemPallets")
  public Integer getCountOfProblemPallets(Date fromDate, Date toDate) {
    Integer countOfProblemPallets =
        instructionRepository
            .countByProblemTagIdIsNotNullAndCreateTsAfterAndCreateTsBeforeAndCompleteTsNotNull(
                fromDate, toDate);
    return Objects.nonNull(countOfProblemPallets) ? countOfProblemPallets : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfReceivedCases")
  public Integer getCountOfReceivedCases(Date fromDate, Date toDate) {
    Integer countOfReceivedCases = receiptRepository.countByCasesReceived(fromDate, toDate);
    return Objects.nonNull(countOfReceivedCases) ? countOfReceivedCases : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfCasesAfterSysReopen")
  public Integer getCountOfCasesAfterSysReopen(Date fromDate, Date toDate) {
    Integer countOfCasesReceivedAfterSysReopen =
        containerRepository.countOfCasesReceivedAfterSysReopen(fromDate, toDate);
    return Objects.nonNull(countOfCasesReceivedAfterSysReopen)
        ? countOfCasesReceivedAfterSysReopen
        : 0;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getAverageCountOfPalletsPerDelivery")
  public Double getAverageCountOfPalletsPerDelivery(Date fromDate, Date toDate) {
    return containerRepository.averagePalletCountPerDelivery(
        fromDate, toDate, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getAveragePalletBuildTime")
  public Long getAveragePalletBuildTime(Date fromDate, Date toDate) {
    Long averagePalletBuildTime =
        instructionRepository.averagePalletBuildTime(
            fromDate,
            toDate,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
    return Objects.isNull(averagePalletBuildTime) ? 0 : averagePalletBuildTime;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getOldestContainerList")
  public List<Object[]> getOldestContainerList(List<Long> deliveryList) {
    return containerRepository.findOldestContainerTsByDelivery(deliveryList);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getItemCatalogUpdateLogsByDate")
  public List<ItemCatalogUpdateLog> getItemCatalogUpdateLogsByDate(
      Date fromDate, Date toDate, List<Integer> facilityNum) {
    return itemCatalogRepository.findAllByCreateTsBetweenAndFacilityNumNotIn(
        fromDate, toDate, facilityNum);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getItemCatalogUpdateLogsByDate")
  public List<ItemCatalogUpdateLog> getPharmacyItemCatalogUpdateLogsByDate(
      Date fromDate, Date toDate, List<Integer> facilityNum) {
    return itemCatalogRepository.findAllByCreateTsBetweenAndFacilityNumIn(
        fromDate, toDate, facilityNum);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getCountOfDockTagContainers")
  public Integer getCountOfDockTagContainers(Date fromDate, Date toDate) {
    return dockTagRepository.countByCreateTsBetween(fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  public Container findContainerByTrackingId(String trackingId) {
    return containerRepository.findByTrackingId(trackingId);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  public List<Container> findContainerByDeliveryNumberAndUser(
      Long deliveryNumber, String createUser, Date fromDate, Date toDate) {
    return containerRepository.findByDeliveryNumberAndCreateUserAndCreateTsBetween(
        deliveryNumber, createUser, fromDate, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  public List<Container> findContainersByDeliveryNumberAndCreateUserAndCreateTsBefore(
      Long deliveryNumber, String createUser, Date toDate) {
    return containerRepository.findByDeliveryNumberAndCreateUserAndCreateTsBefore(
        deliveryNumber, createUser, toDate);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilterSecondary
  public List<Instruction> findInstructionByIds(List<Long> instructionIdList) {
    return instructionRepository.findByIdIn(instructionIdList);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getAtlasReportByDate")
  public Integer getAtlasReportCount(
      Date fromDate, Date toDate, Integer facilityNum, String facilityCode) {
    return instructionRepository.getAtlasReportCount(fromDate, toDate, facilityNum, facilityCode);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getPalletSSCSScanCount")
  public Integer getPalletSSCCScanCount(
      Date fromDate,
      Date toDate,
      Integer facilityNum,
      String facilityCode,
      List<String> instructionCode) {
    return instructionRepository.getPalletSSCCScanCount(
        fromDate, toDate, facilityNum, facilityCode, instructionCode);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getPalletSSCSScanCount")
  public Integer getCaseSSCCScanCount(
      Date fromDate,
      Date toDate,
      Integer facilityNum,
      String facilityCode,
      List<String> instructionCode) {
    return instructionRepository.getCaseSSCCScanCount(
        fromDate, toDate, facilityNum, facilityCode, instructionCode);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getPalletSSCSScanCount")
  public Integer get2DScanCounts(
      Date fromDate,
      Date toDate,
      Integer facilityNum,
      String facilityCode,
      List<String> instructionCode) {
    return instructionRepository.get2DScanCounts(
        fromDate, toDate, facilityNum, facilityCode, instructionCode);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      flow = "Reporting_getPalletSSCSScanCount")
  public Integer getPalletLabelCanceledCount(
      Date fromDate,
      Date toDate,
      Integer facilityNum,
      String facilityCode,
      String containerStatus) {
    return instructionRepository.getPalletLabelCanceledCount(
        fromDate, toDate, facilityNum, facilityCode, containerStatus);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
          component = AppComponent.CORE,
          type = Type.INTERNAL,
          flow = "Reporting_getReceivingProductivity")
  public Page<ReceivingProductivityResponseDTO> getReceivingProductivityForOneUser(Integer facilityNum,
                                                                                   String facilityCountryCode,
                                                                                   String userId,
                                                                                   String fromDate,
                                                                                   String toDate,
                                                                                   Pageable reportPage) {
    return containerRepository.getReceivingProductivityForOneUser(facilityNum, facilityCountryCode, userId, fromDate, toDate, reportPage);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
          component = AppComponent.CORE,
          type = Type.INTERNAL,
          flow = "Reporting_getReceivingProductivity")
  public Page<ReceivingProductivityResponseDTO> getReceivingProductivityForAllUsers(Integer facilityNum,
                                                                                   String facilityCountryCode,
                                                                                   String fromDate,
                                                                                   String toDate,
                                                                                    Pageable reportPage) {
    return containerRepository.getReceivingProductivityForAllUsers(facilityNum, facilityCountryCode, fromDate, toDate, reportPage);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
          component = AppComponent.CORE,
          type = Type.INTERNAL,
          flow = "Reporting_getCasesReceivedCount")
  public Integer getNumberOfCasesReceived(
          Date fromDate,
          Date toDate,
          Integer facilityNum,
          String facilityCode,
          List<String> instructionCodes) {
    return instructionRepository.getNumberOfCasesReceived(
            fromDate, toDate, facilityNum, facilityCode, instructionCodes);
  }

    @Transactional(readOnly = true)
    @TimeTracing(
            component = AppComponent.CORE,
            type = Type.INTERNAL,
            flow = "Reporting_getUnitsReceivedCount")
    public Integer getNumberOfUnitsReceived(
            Date fromDate,
            Date toDate,
            Integer facilityNum,
            String facilityCode,
            List<String> instructionCodes) {
      return instructionRepository.getNumberOfUnitsReceived(
              fromDate, toDate, facilityNum, facilityCode, instructionCodes);
  }

  @Transactional(readOnly = true)
  @TimeTracing(
          component = AppComponent.CORE,
          type = Type.INTERNAL,
          flow = "Reporting_getNumberOfItemsReceived")
  public Integer getNumberOfItemsReceived(
          Date fromDate,
          Date toDate,
          List<Integer> facilityNum,
          String facilityCode,
          List<String> instructionCodes) {
    return instructionRepository.getNumberOfItemsReceived(
            fromDate, toDate, facilityNum, facilityCode, instructionCodes);
  }

}
