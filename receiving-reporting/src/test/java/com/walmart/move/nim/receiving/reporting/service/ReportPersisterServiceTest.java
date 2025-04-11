package com.walmart.move.nim.receiving.reporting.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.reporting.mock.data.MockItemCatalog;
import com.walmart.move.nim.receiving.reporting.mock.data.MockReportData;
import com.walmart.move.nim.receiving.reporting.model.UserCaseChannelTypeResponse;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingContainerRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingDockTagRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingInstructionRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingItemCatalogRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingReceiptCustomRepository;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingReceiptRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReportPersisterServiceTest {

  @Mock private ReportingInstructionRepository instructionRepository;

  @Mock private ReportingReceiptRepository receiptRepository;

  @Mock private ReportingReceiptCustomRepository receiptCustomRepository;

  @Mock private ReportingContainerRepository containerRepository;

  @Mock private ReportingItemCatalogRepository itemCatalogRepository;

  @Mock private ReportingDockTagRepository dockTagRepository;

  @InjectMocks private ReportPersisterService reportPersisterService;

  private Date fromDate;
  private Date toDate;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    fromDate = new Date(2020, Calendar.OCTOBER, 1, 0, 0, 0);
    toDate = new Date(2020, Calendar.OCTOBER, 2, 0, 0, 0);
  }

  @AfterMethod
  public void tearDown() {
    reset(instructionRepository);
    reset(receiptRepository);
    reset(receiptCustomRepository);
    reset(containerRepository);
    reset(itemCatalogRepository);
    reset(dockTagRepository);
  }

  @Test
  public void testGetUserCaseChannelTypeData() {
    when(receiptCustomRepository.getUserCasesByChannelType(
            anyInt(), anyString(), any(Date.class), any(Date.class)))
        .thenReturn(MockReportData.getMockReportData().getCaseChannelTypeResponses());
    List<UserCaseChannelTypeResponse> userCaseChannelTypeResponses =
        reportPersisterService.getUserCaseChannelTypeData(fromDate, toDate);
    Assert.assertEquals(
        userCaseChannelTypeResponses,
        MockReportData.getMockReportData().getCaseChannelTypeResponses());
  }

  @Test
  public void testGetCountOfPos() {
    when(receiptRepository.countDistinctPosByCreateTsBetween(any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(reportPersisterService.getCountOfPos(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfPos_Null() {
    when(receiptRepository.countDistinctPosByCreateTsBetween(any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfPos(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfDeliveries() {
    when(receiptRepository.countDistinctDeliveryNumberByCreateTsBetween(
            any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(reportPersisterService.getCountOfDeliveries(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfDeliveries_Null() {
    when(receiptRepository.countDistinctDeliveryNumberByCreateTsBetween(
            any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfDeliveries(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfLabels() {
    when(containerRepository.countByCreateTsAfterAndCreateTsBefore(
            any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(reportPersisterService.getCountOfLabels(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfLabels_Null() {
    when(containerRepository.countByCreateTsAfterAndCreateTsBefore(
            any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfLabels(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfSstkPalletsAndCases() {
    when(containerRepository.countOfSstkPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    assertEquals(
        reportPersisterService.getCountOfSstkPalletsAndCases(fromDate, toDate), new Pair<>(2, 2));
  }

  @Test
  public void testGetCountOfDaConPallets() {
    when(containerRepository.countOfDaConPallets(
            any(Date.class), any(Date.class), anyInt(), anyString()))
        .thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfDaConPallets(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfDaConPallets_Null() {
    when(containerRepository.countOfDaConPallets(
            any(Date.class), any(Date.class), anyInt(), anyString()))
        .thenReturn(null);
    assertEquals(
        reportPersisterService.getCountOfDaConPallets(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfDaNonConPalletsAndCases() {
    when(containerRepository.countOfDaNonConPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    assertEquals(
        reportPersisterService.getCountOfDaNonConPalletsAndCases(fromDate, toDate),
        new Pair<>(2, 2));
  }

  @Test
  public void testGetCountOfPoConPalletsAndCases() {
    when(containerRepository.countOfPoConPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    assertEquals(
        reportPersisterService.getCountOfPoConPalletsAndCases(fromDate, toDate), new Pair<>(2, 2));
  }

  @Test
  public void testGetCountOfPbylPalletsAndCases() {
    when(containerRepository.countOfPbylPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    assertEquals(
        reportPersisterService.getCountOfPbylPalletsAndCases(fromDate, toDate), new Pair<>(2, 2));
  }

  @Test
  public void testCountOfAclCases() {
    when(containerRepository.countByCreateTsBetweenAndOnConveyorIsTrueAndContainerExceptionIsNull(
            any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(reportPersisterService.getCountOfAclCases(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testCountOfAclCases_Null() {
    when(containerRepository.countByCreateTsBetweenAndOnConveyorIsTrueAndContainerExceptionIsNull(
            any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfAclCases(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testCountOfAccManualCases() {
    when(containerRepository.countOfManualReceivingCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfAccManualCases(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testCountOfAccManualCases_Null() {
    when(containerRepository.countOfManualReceivingCases(any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(
        reportPersisterService.getCountOfAccManualCases(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfDaConCases() {
    when(containerRepository.countByContainerTypeAndCreateTsBetween(
            anyString(), any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(reportPersisterService.getCountOfDaConCases(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfDaConCases_Null() {
    when(containerRepository.countByContainerTypeAndCreateTsBetween(
            anyString(), any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfDaConCases(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfUsers() {
    when(receiptRepository.countDistinctCreateUserByCreateTsBetween(
            any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(reportPersisterService.getCountOfUsers(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfUsers_Null() {
    when(receiptRepository.countDistinctCreateUserByCreateTsBetween(
            any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfUsers(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfVtrContainers() {
    when(containerRepository.countByContainerStatusAndCreateTsBetween(
            anyString(), any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfVtrContainers(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfVtrContainers_Null() {
    when(containerRepository.countByContainerStatusAndCreateTsBetween(
            anyString(), any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(
        reportPersisterService.getCountOfVtrContainers(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfVtrCases() {
    when(receiptRepository.countByVtrCasesReceived(any(Date.class), any(Date.class))).thenReturn(2);
    assertEquals(reportPersisterService.getCountOfVtrCases(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfVtrCases_Null() {
    when(receiptRepository.countByVtrCasesReceived(any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(reportPersisterService.getCountOfVtrCases(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testCountOfProblemPallets() {
    when(instructionRepository
            .countByProblemTagIdIsNotNullAndCreateTsAfterAndCreateTsBeforeAndCompleteTsNotNull(
                any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfProblemPallets(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testCountOfProblemPallets_Null() {
    when(instructionRepository
            .countByProblemTagIdIsNotNullAndCreateTsAfterAndCreateTsBeforeAndCompleteTsNotNull(
                any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(
        reportPersisterService.getCountOfProblemPallets(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfReceivedCases() {
    when(receiptRepository.countByCasesReceived(any(Date.class), any(Date.class))).thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfReceivedCases(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfReceivedCases_Null() {
    when(receiptRepository.countByCasesReceived(any(Date.class), any(Date.class))).thenReturn(null);
    assertEquals(
        reportPersisterService.getCountOfReceivedCases(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetCountOfCasesAfterSysReopen() {
    when(containerRepository.countOfCasesReceivedAfterSysReopen(any(Date.class), any(Date.class)))
        .thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfCasesAfterSysReopen(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetCountOfCasesAfterSysReopen_Null() {
    when(containerRepository.countOfCasesReceivedAfterSysReopen(any(Date.class), any(Date.class)))
        .thenReturn(null);
    assertEquals(
        reportPersisterService.getCountOfCasesAfterSysReopen(fromDate, toDate), Integer.valueOf(0));
  }

  @Test
  public void testGetAverageCountOfPalletsPerDelivery() {
    when(containerRepository.averagePalletCountPerDelivery(
            any(Date.class), any(Date.class), anyInt(), anyString()))
        .thenReturn(2.0D);
    assertEquals(
        reportPersisterService.getAverageCountOfPalletsPerDelivery(fromDate, toDate), 2.0D);
  }

  @Test
  public void testGetAveragePalletBuildTime() {
    when(instructionRepository.averagePalletBuildTime(
            any(Date.class), any(Date.class), anyInt(), anyString()))
        .thenReturn(2L);
    assertEquals(
        reportPersisterService.getAveragePalletBuildTime(fromDate, toDate), Long.valueOf(2));
  }

  @Test
  public void testGetCountOfDockTagContainers() {
    when(dockTagRepository.countByCreateTsBetween(any(Date.class), any(Date.class))).thenReturn(2);
    assertEquals(
        reportPersisterService.getCountOfDockTagContainers(fromDate, toDate), Integer.valueOf(2));
  }

  @Test
  public void testGetOldestContainerList() {
    when(containerRepository.findOldestContainerTsByDelivery(anyList()))
        .thenReturn(Collections.singletonList(new Object[] {123456L, fromDate}));
    assertEquals(
        reportPersisterService.getOldestContainerList(Collections.singletonList(123456L)).get(0),
        Collections.singletonList(new Object[] {123456L, fromDate}).get(0));
  }

  @Test
  public void testGetItemCatalogUpdateLogsByDate() {
    when(itemCatalogRepository.findAllByCreateTsBetweenAndFacilityNumNotIn(
            any(Date.class), any(Date.class), anyList()))
        .thenReturn(MockItemCatalog.getItemCatalogUpdateLogs());
    assertEquals(
        reportPersisterService.getItemCatalogUpdateLogsByDate(fromDate, toDate, Arrays.asList(1)),
        MockItemCatalog.getItemCatalogUpdateLogs());
  }

  @Test
  public void testfindContainerByTrackingId() {
    Container container = new Container();
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container);
    assertEquals(reportPersisterService.findContainerByTrackingId("a123000000000123"), container);
  }

  @Test
  public void testfindContainerByDeliveryNumberAndUser() {
    Container container = new Container();
    List<Container> containers = Arrays.asList(container);
    when(containerRepository.findByDeliveryNumberAndCreateUserAndCreateTsBetween(
            any(), anyString(), any(), any()))
        .thenReturn(containers);
    assertEquals(
        reportPersisterService.findContainerByDeliveryNumberAndUser(
            12345678L, "sys", new Date(), new Date()),
        containers);
  }

  @Test
  public void testfindInstructionByIds() {
    Instruction instruction = new Instruction();
    List<Instruction> instructionList = Arrays.asList(instruction);
    when(instructionRepository.findByIdIn(anyList())).thenReturn(instructionList);
    assertEquals(reportPersisterService.findInstructionByIds(new ArrayList<>()), instructionList);
  }
}
