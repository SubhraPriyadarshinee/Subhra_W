package com.walmart.move.nim.receiving.reporting.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.reporting.mock.data.MockGdmDeliveryHeaderDetails;
import com.walmart.move.nim.receiving.reporting.mock.data.MockItemCatalog;
import com.walmart.move.nim.receiving.reporting.mock.data.MockReportData;
import com.walmart.move.nim.receiving.reporting.model.BreakPackChildLabelInfo;
import com.walmart.move.nim.receiving.reporting.model.BreakPackLabelInfo;
import com.walmart.move.nim.receiving.reporting.model.ReportData;
import com.walmart.move.nim.receiving.reporting.model.RxItemCatalogReportData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author sks0013 */
public class ReportServiceTest extends ReceivingTestBase {

  @InjectMocks private ReportService reportService;

  @Mock private ReportPersisterService reportPersisterService;
  @Mock private DeliveryService deliveryService;

  @Mock private AppConfig appConfig;
  @Mock private ReportConfig reportConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private TenantSpecificReportConfig tenantSpecificReportConfig;
  private Container container1, container2;
  private ReportData report;
  @Mock private ContainerRepository containerRepository;

  private List<Integer> facilityNumberList;
  private Map<Integer, ReportData> reportForAllfacilityNumbers;
  private List<GdmDeliveryHeaderDetailsResponse> gdmDeliveryHeaderResponse;
  private Map<String, Integer> pharmacyReport;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    facilityNumberList = new ArrayList<>();
    facilityNumberList.add(32987);
    facilityNumberList.add(6938);

    report = MockReportData.getMockReportData();

    reportForAllfacilityNumbers = new HashMap<>();
    gdmDeliveryHeaderResponse = MockGdmDeliveryHeaderDetails.getGdmDeliveryHeaderDetailsResponse();
    report.setDeliveryHeaderDetailsResponses(gdmDeliveryHeaderResponse);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    for (Integer facilityNumber : facilityNumberList) {
      reportForAllfacilityNumbers.put(facilityNumber, report);
    }

    pharmacyReport = new HashMap<>();
    pharmacyReport.put("Total no of deliveries received through Atlas ", 1);
    pharmacyReport.put("% split of Non Exempted Items", 1);
    pharmacyReport.put("% split of Exempted Items", 1);
    pharmacyReport.put("No of pallets received through Pallet SSCC Scan", 1);
    pharmacyReport.put("No of pallets received through Case SSCC Scan", 1);
    pharmacyReport.put("No of Pallets received with Case SSCC + unit 2D Scans", 1);
    pharmacyReport.put("No of Pallet Label Canceled ", 1);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(reportPersisterService);
    reset(deliveryService);
    reset(tenantSpecificReportConfig);
    reset(reportConfig);
    reset(containerRepository);
    reset(tenantSpecificConfigReader);
  }

  /** Test report's statistics service implementation */
  @Test
  public void testGetReceivingReportData() throws ReceivingException {
    ReportData expectedResponse = MockReportData.getMockReportData();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    when(reportPersisterService.getCountOfDeliveries(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfPos(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfLabels(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfPbylPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfSstkPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfDaConPallets(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfPoConPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfDsdcPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfDaNonConPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfAclCases(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfDaConCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfAccManualCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfCasesAfterSysReopen(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfUsers(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfItems(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getAverageCountOfPalletsPerDelivery(
            any(Date.class), any(Date.class)))
        .thenReturn(2.0);
    when(reportPersisterService.getAveragePalletBuildTime(any(Date.class), any(Date.class)))
        .thenReturn(50L);
    when(deliveryService.getDeliveryHeaderDetails(
            any(Date.class), any(Date.class), anyList(), any(HttpHeaders.class)))
        .thenReturn(MockGdmDeliveryHeaderDetails.getGdmDeliveryHeaderDetailsResponse());
    when(reportPersisterService.getCountOfVtrContainers(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfVtrCases(any(Date.class), any(Date.class))).thenReturn(1);
    when(reportPersisterService.getCountOfProblemPallets(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfReceivedCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(tenantSpecificReportConfig.isFeatureFlagEnabled(anyString())).thenReturn(true);
    when(reportConfig.getLosGoalInHours()).thenReturn(48);
    when(reportPersisterService.getOldestContainerList(anyList()))
        .thenReturn(
            Collections.singletonList(
                new Object[] {12345678L, (new Date(2020, Calendar.JANUARY, 1, 12, 0, 0))}));
    when(reportPersisterService.getCountOfDockTagContainers(any(Date.class), any(Date.class)))
        .thenReturn(2);
    ReportData actualResponse =
        reportService.populateReportData(1586788477009L, 1586961277009L, true, false, httpHeaders);
    assertEquals(actualResponse.getStatisticsData(), expectedResponse.getStatisticsData());
  }

  @Test
  public void testGetReceivingReportData_AllStatsDisabled() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    when(reportPersisterService.getCountOfDeliveries(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfPos(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfLabels(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfPbylPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfSstkPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfDaConPallets(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfPoConPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfDsdcPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfDaNonConPalletsAndCases(any(Date.class), any(Date.class)))
        .thenReturn(new Pair<>(2, 2));
    when(reportPersisterService.getCountOfAclCases(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfDaConCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfAccManualCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfCasesAfterSysReopen(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfUsers(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getCountOfItems(any(Date.class), any(Date.class))).thenReturn(2);
    when(reportPersisterService.getAverageCountOfPalletsPerDelivery(
            any(Date.class), any(Date.class)))
        .thenReturn(2.0);
    when(reportPersisterService.getAveragePalletBuildTime(any(Date.class), any(Date.class)))
        .thenReturn(50L);
    when(deliveryService.getDeliveryHeaderDetails(
            any(Date.class), any(Date.class), anyList(), any(HttpHeaders.class)))
        .thenReturn(MockGdmDeliveryHeaderDetails.getGdmDeliveryHeaderDetailsResponse());
    when(reportPersisterService.getCountOfVtrContainers(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfVtrCases(any(Date.class), any(Date.class))).thenReturn(1);
    when(reportPersisterService.getCountOfProblemPallets(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(reportPersisterService.getCountOfReceivedCases(any(Date.class), any(Date.class)))
        .thenReturn(2);
    when(tenantSpecificReportConfig.isFeatureFlagEnabled(anyString())).thenReturn(false);
    when(reportConfig.getLosGoalInHours()).thenReturn(48);
    when(reportPersisterService.getOldestContainerList(anyList()))
        .thenReturn(
            Collections.singletonList(
                new Object[] {12345678L, (new Date(2020, Calendar.JANUARY, 1, 12, 0, 0))}));
    when(reportPersisterService.getCountOfDockTagContainers(any(Date.class), any(Date.class)))
        .thenReturn(2);
    ReportData actualResponse =
        reportService.populateReportData(1586788477009L, 1586961277009L, true, false, httpHeaders);
    assertEquals(actualResponse.getStatisticsData(), Collections.emptyList());
  }

  /** This method is used to create mail body in html format. */
  @Test
  public void testCreateHtmlTemplate() {
    StringBuilder actualHtmlTemplate =
        reportService.createHtmlTemplateForStatistics(reportForAllfacilityNumbers);
    Assert.assertEquals(actualHtmlTemplate.toString().trim(), MockReportData.expectedMailTemplate);
  }

  /** This method is used to test excel sheet report generation. */
  @Test
  public void createExcelReportTest() {
    Workbook workbook = reportService.createExcelReport(reportForAllfacilityNumbers);
    assertNotNull(workbook);
  }

  /** This method is used to create mail body in html format. */
  @Test
  public void testCreateHtmlTemplateForEmail() {
    doReturn(Collections.singletonList(32987))
        .when(tenantSpecificConfigReader)
        .getMissingFacilityNumList(any(), eq(ReceivingConstants.ITEM_CATALOG_ENABLED));
    StringBuilder actualHtmlTemplate =
        reportService.createHtmlTemplateForReportingForEntity(
            MockItemCatalog.getItemCatalogUpdateLogs(),
            "item catalog",
            ReceivingConstants.ITEM_CATALOG_ENABLED);
    Assert.assertTrue(
        actualHtmlTemplate
            .toString()
            .trim()
            .contains(
                "Please find the item catalog report of each of the following DCs: 32818, 6561"));
  }

  @Test
  public void testCreateExcelReportForItemCatalog() {

    doReturn(Arrays.asList(6001))
        .when(tenantSpecificConfigReader)
        .getEnabledFacilityNumListForFeature(ReceivingConstants.ITEM_CATALOG_ENABLED);

    Workbook workbook =
        reportService.createExcelReportForItemCatalog(MockItemCatalog.getItemCatalogUpdateLogs());
    assertNotNull(workbook);
    // assertEquals(workbook.getSheetAt(0).getPhysicalNumberOfRows(), 2);
  }

  @Test
  public void test_createRxExcelReportForItemCatalog() {

    doReturn(Arrays.asList(6001))
        .when(tenantSpecificConfigReader)
        .getEnabledFacilityNumListForFeature(ReceivingConstants.ITEM_CATALOG_ENABLED);

    RxItemCatalogReportData rxItemCatalogReportData = new RxItemCatalogReportData();
    rxItemCatalogReportData.setCorrelationId("a1-b2-c3-d4");
    rxItemCatalogReportData.setCreateTs(
        DateFormatUtils.format(new Date(), ReceivingConstants.UTC_DATE_FORMAT));
    rxItemCatalogReportData.setCreateUserId("MOCK_USER");
    rxItemCatalogReportData.setDeliveryNumber("12345");
    rxItemCatalogReportData.setExemptItem("Y");
    rxItemCatalogReportData.setFacilityCountryCode("us");
    rxItemCatalogReportData.setFacilityNum(6001);
    rxItemCatalogReportData.setItemNumber("98765");
    rxItemCatalogReportData.setNewItemUPC("MOCK_NEW_ITEM_UPC");
    rxItemCatalogReportData.setOldItemUPC("MOCK_OLD_ITEM_UPC");
    rxItemCatalogReportData.setVendorNumber("MOCK_VENDOR_NUMBER");
    rxItemCatalogReportData.setVendorStockNumber("MOCK_VENDORSTOCKNUMBER");

    RxItemCatalogReportData rxItemCatalogReportData2 = new RxItemCatalogReportData();
    rxItemCatalogReportData2.setCorrelationId("a1-b2-c3-d4");
    rxItemCatalogReportData2.setCreateTs(
        DateFormatUtils.format(new Date(), ReceivingConstants.UTC_DATE_FORMAT));
    rxItemCatalogReportData2.setCreateUserId("MOCK_USER");
    rxItemCatalogReportData2.setDeliveryNumber("12345");
    rxItemCatalogReportData2.setExemptItem("Y");
    rxItemCatalogReportData2.setFacilityCountryCode("us");
    rxItemCatalogReportData2.setFacilityNum(6001);
    rxItemCatalogReportData2.setItemNumber("98765");
    rxItemCatalogReportData2.setNewItemUPC("MOCK_NEW_ITEM_UPC");
    rxItemCatalogReportData2.setOldItemUPC("MOCK_OLD_ITEM_UPC");

    Workbook workbook =
        reportService.createRxExcelReportForItemCatalog(
            Arrays.asList(rxItemCatalogReportData, rxItemCatalogReportData2));

    assertNotNull(workbook);
    //    assertEquals(workbook.getSheetAt(0).getPhysicalNumberOfRows(), 3);
  }

  @Test
  public void testCreateExcelReportForPharmacyReceivingMetrics() {

    Workbook workbook = reportService.createExcelReportForPharmacyReceivingMetrics(pharmacyReport);

    assertNotNull(workbook);
    //    assertEquals(workbook.getSheetAt(0).getPhysicalNumberOfRows(), 8);
  }

  @Test
  public void testCreateHtmlTemplateForPharmacyReceivingMetrics() {
    StringBuilder actualHtmlTemplate =
        reportService.createHtmlTemplateForPharmacyReceivingMetrics(pharmacyReport);
    Assert.assertEquals(
        actualHtmlTemplate.toString().trim(), MockReportData.expectedMetricReportdMailTemplate);
  }

  @Test
  public void testFetchBreakPackReceiveContainerDetails() {
    List<Integer> facilityIds = new ArrayList<>();
    facilityIds.add(32679);
    List<Container> backOutContainers = mockBackOutContainers();
    backOutContainers.stream().map(Container::getTrackingId).collect(Collectors.toSet());
    when(reportConfig.getAtlasDaBreakPackBackOutReportRecordsFetchCount()).thenReturn(200);
    when(containerRepository.findBreakPackReceiveContainer(any(), any(), any(), any(), any()))
        .thenReturn(backOutContainers);
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("UTC");
    when(containerRepository.fetchAllocatedStores(any(Set.class), any(List.class)))
        .thenReturn(mockAllocatedStores());
    List<List<BreakPackLabelInfo>> breakPackReceiveContainerDetails =
        reportService.fetchBreakPackReceiveContainerDetails(123);
    assertNotNull(breakPackReceiveContainerDetails);
    assertTrue(breakPackReceiveContainerDetails.size() > 0);
    assertEquals(breakPackReceiveContainerDetails.get(0).get(0).getInductLabelId(), "932938993");
    verify(reportConfig, Mockito.times(2)).getAtlasDaBreakPackBackOutReportRecordsFetchCount();
    verify(containerRepository, Mockito.times(1))
        .findBreakPackReceiveContainer(any(), any(), any(), any(), any());
    verify(tenantSpecificConfigReader, Mockito.times(1)).getDCTimeZone(any());
    verify(containerRepository, Mockito.times(1))
        .fetchAllocatedStores(any(Set.class), any(List.class));
  }

  @Test
  public void testFetchBreakPackReceiveContainerDetailsWithEmptyContainers() {
    List<Integer> facilityIds = new ArrayList<>();
    facilityIds.add(32679);
    when(reportConfig.getAtlasDaBreakPackBackOutReportRecordsFetchCount()).thenReturn(200);
    when(containerRepository.findBreakPackReceiveContainer(any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    List<List<BreakPackLabelInfo>> combinedBreakPackReceiveContainerDetails =
        reportService.fetchBreakPackReceiveContainerDetails(123);
    for (List<BreakPackLabelInfo> breakPackReceiveContainerDetails :
        combinedBreakPackReceiveContainerDetails) {
      assertNotNull(breakPackReceiveContainerDetails);
      assertTrue(breakPackReceiveContainerDetails.size() == 0);
    }
    verify(reportConfig, Mockito.times(2)).getAtlasDaBreakPackBackOutReportRecordsFetchCount();
    verify(containerRepository, Mockito.times(1))
        .findBreakPackReceiveContainer(any(), any(), any(), any(), any());
  }

  @Test
  public void testCreateExcelReportForBreakPackReceivedContainers() {
    List<BreakPackLabelInfo> breakPackReceiveContainerDetails =
        mockBreakPackReceiveContainerDetails();
    Workbook workbook =
        reportService.createExcelReportForBreakPackReceivedContainers(
            breakPackReceiveContainerDetails);
    assertNotNull(workbook);
    assertTrue(workbook instanceof XSSFWorkbook);
  }

  @Test
  public void testCreateHtmlTemplateForBreakPackBackOutContainers() {
    List<BreakPackLabelInfo> breakPackReceiveContainerDetails =
        mockBreakPackReceiveContainerDetails();
    String mailTemplate =
        reportService.createHtmlTemplateForBreakPackBackOutContainers(
            breakPackReceiveContainerDetails);
    assertNotNull(mailTemplate);
    assertTrue(mailTemplate.length() > 0);
  }

  /**
   * Mocking of back out containers
   *
   * @return
   */
  private List<Container> mockBackOutContainers() {
    Instant currentTime = ReceivingUtils.getDCDateTime("UTC").toInstant();
    Container container = new Container();
    container.setTrackingId("932938993");
    container.setLastChangedTs(new Date(currentTime.toEpochMilli()));
    container.setLastChangedUser("SYS");
    return Arrays.asList(container);
  }

  /**
   * Mocking of allocated stores
   *
   * @return
   */
  private List<Container> mockAllocatedStores() {
    Container container = new Container();
    container.setTrackingId("932938993123");
    container.setParentTrackingId("932938993");
    container.setDestination(new HashMap<>());
    container.setLastChangedUser("SYS");
    return Arrays.asList(container);
  }

  /**
   * Mocking of BreakPackLabelInfo
   *
   * @return
   */
  private List<BreakPackLabelInfo> mockBreakPackReceiveContainerDetails() {
    BreakPackLabelInfo breakPackLabelInfo =
        BreakPackLabelInfo.builder()
            .inductLabelId("23832723787")
            .breakPackChildLabelInfo(mockBreakPackChildLabelInfo())
            .backOutDate("2024-04-1")
            .backOutTimeStamp("10:00")
            .userId("SYS")
            .build();
    return Arrays.asList(breakPackLabelInfo);
  }

  /**
   * Mocking of BreakPackChildLabelInfo
   *
   * @return
   */
  private List<BreakPackChildLabelInfo> mockBreakPackChildLabelInfo() {
    BreakPackChildLabelInfo breakPackChildLabelInfo =
        BreakPackChildLabelInfo.builder()
            .childLabel("2383272378732823")
            .allocatedStore("1263")
            .build();
    return Arrays.asList(breakPackChildLabelInfo);
  }
}
