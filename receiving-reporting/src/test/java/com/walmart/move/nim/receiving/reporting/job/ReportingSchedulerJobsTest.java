package com.walmart.move.nim.receiving.reporting.job;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.service.MailService;
import com.walmart.move.nim.receiving.reporting.mock.data.MockGdmDeliveryHeaderDetails;
import com.walmart.move.nim.receiving.reporting.mock.data.MockReportData;
import com.walmart.move.nim.receiving.reporting.model.BreakPackChildLabelInfo;
import com.walmart.move.nim.receiving.reporting.model.BreakPackLabelInfo;
import com.walmart.move.nim.receiving.reporting.model.ReportData;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingItemCatalogCustomRepository;
import com.walmart.move.nim.receiving.reporting.service.ReportPersisterService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.*;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.apache.poi.ss.usermodel.Workbook;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReportingSchedulerJobsTest extends ReceivingTestBase {

  @InjectMocks private ReportingSchedulerJobs reportingSchedulerJobs;

  @Mock private ReportService reportService;
  @Mock private ReportPersisterService reportPersisterService;
  @Mock private ReportingItemCatalogCustomRepository reportingItemCatalogCustomRepository;

  @Mock private TenantSpecificReportConfig configUtil;

  @Mock private MailService mailService;

  @Spy private ReportConfig reportConfig;

  private List<Integer> facilityNumberList;

  private Map<Integer, ReportData> reportForAllfacilityNumbers;
  private List<GdmDeliveryHeaderDetailsResponse> gdmDeliveryHeaderResponse;

  private ReportData report;

  private MimeMessage mimeMessage = null;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    facilityNumberList = new ArrayList<>();
    facilityNumberList.add(32987);
    facilityNumberList.add(6938);

    Properties props = new Properties();
    props.put(ReportingConstants.MAIL_HOST_KEY, ReportingConstants.MAIL_HOST_VALUE);
    Session session = Session.getDefaultInstance(props, null);
    mimeMessage = new MimeMessage(session);

    report = MockReportData.getMockReportData();
    gdmDeliveryHeaderResponse = MockGdmDeliveryHeaderDetails.getGdmDeliveryHeaderDetailsResponse();
    report.setDeliveryHeaderDetailsResponses(gdmDeliveryHeaderResponse);

    reportForAllfacilityNumbers = new HashMap<>();

    for (Integer facilityNumber : facilityNumberList) {
      reportForAllfacilityNumbers.put(facilityNumber, report);
    }
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtil);
    reset(reportPersisterService);
    reset(mailService);
    reset(reportService);
  }

  /**
   * This method is used to test report generation scheduler
   *
   * @throws Exception
   */
  @Test
  public void testReportGeneratorScheduler() throws Exception {

    when(reportConfig.isEmailReportEnabled()).thenReturn(Boolean.TRUE);
    when(configUtil.getFacilityNumList()).thenReturn(facilityNumberList);
    when(reportService.getReportForAllFacilityNumbers(Mockito.anyList(), Mockito.anyString()))
        .thenReturn(reportForAllfacilityNumbers);

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);

    doNothing().when(mailService).sendMail(any(MimeMessage.class));
    when(reportService.createExcelReport(any())).thenReturn(null);
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForStatistics(any())).thenReturn(message);

    reportingSchedulerJobs.reportGeneratorScheduler();

    verify(configUtil, Mockito.times(1)).getFacilityNumList();
    verify(reportService, Mockito.times(1))
        .getReportForAllFacilityNumbers(Mockito.anyList(), Mockito.anyString());
    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  /**
   * This method is used to test report generation scheduler failure scenario
   *
   * @throws Exception
   */
  @Test
  public void testReportGeneratorSchedulerExceptionCase() throws Exception {
    when(reportConfig.isEmailReportEnabled()).thenReturn(Boolean.TRUE);
    when(configUtil.getFacilityNumList()).thenReturn(facilityNumberList);
    when(reportService.getReportForAllFacilityNumbers(Mockito.anyList(), Mockito.anyString()))
        .thenReturn(reportForAllfacilityNumbers);
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForStatistics(anyMap())).thenReturn(message);

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);

    doThrow(ReceivingException.class).when(mailService).sendMail(any(MimeMessage.class));

    reportingSchedulerJobs.reportGeneratorScheduler();

    verify(configUtil, Mockito.times(1)).getFacilityNumList();
    verify(reportService, Mockito.times(1))
        .getReportForAllFacilityNumbers(Mockito.anyList(), Mockito.anyString());
    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  /**
   * This method is used to test report generation scheduler
   *
   * @throws Exception
   */
  @Test
  public void testItemCatalogReportGeneratorScheduler() throws Exception {

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doNothing().when(mailService).sendMail(any(MimeMessage.class));
    when(reportService.createExcelReportForItemCatalog(any())).thenReturn(null);
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForReportingForEntity(anyList(), anyString(), anyString()))
        .thenReturn(message);

    reportingSchedulerJobs.itemCatalogReportGeneratorScheduler();

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  /**
   * This method is used to test item catalog report generation scheduler exception case
   *
   * @throws Exception
   */
  @Test
  public void testItemCatalogReportGeneratorSchedulerExceptionCase() throws Exception {
    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doThrow(ReceivingException.class).when(mailService).sendMail(any(MimeMessage.class));
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForReportingForEntity(anyList(), anyString(), anyString()))
        .thenReturn(message);

    reportingSchedulerJobs.itemCatalogReportGeneratorScheduler();

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testPharmacyItemCatalogReportGeneratorSchedulerExceptionCase() throws Exception {
    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doThrow(ReceivingException.class).when(mailService).sendMail(any(MimeMessage.class));
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForReportingForEntity(anyList(), anyString(), anyString()))
        .thenReturn(message);

    reportingSchedulerJobs.pharmacyItemCatalogReportGeneratorScheduler();

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testPharmacyItemCatalogReportGeneratorScheduler() throws Exception {

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doNothing().when(mailService).sendMail(any(MimeMessage.class));
    when(reportService.createExcelReportForItemCatalog(any())).thenReturn(null);
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForReportingForEntity(anyList(), anyString(), anyString()))
        .thenReturn(message);

    reportingSchedulerJobs.pharmacyItemCatalogReportGeneratorScheduler();

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testPharmacyMetricReportScheduler() throws Exception {

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    when(reportConfig.getMetricsReportPharmacyIncludeDcList()).thenReturn(Arrays.asList(6001));
    doNothing().when(mailService).sendMail(any(MimeMessage.class));
    when(reportService.createExcelReportForPharmacyReceivingMetrics(any())).thenReturn(null);
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForPharmacyReceivingMetrics(anyMap())).thenReturn(message);

    reportingSchedulerJobs.pharmacyMetricReportScheduler();

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testPharmacyMetricReportSchedulerExceptionCase() throws Exception {
    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    when(reportConfig.getMetricsReportPharmacyIncludeDcList()).thenReturn(Arrays.asList(6001));
    doThrow(ReceivingException.class).when(mailService).sendMail(any(MimeMessage.class));
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForPharmacyReceivingMetrics(anyMap())).thenReturn(message);

    reportingSchedulerJobs.pharmacyMetricReportScheduler();

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testBreakPackReceivedContainersReportGeneratorScheduler() throws Exception {
    List<BreakPackLabelInfo> breakPackReceiveContainerDetails =
        mockBreakPackReceiveContainerDetails();
    Workbook workbook = mock(Workbook.class);
    String mailTemplate = "";
    when(reportService.fetchBreakPackReceiveContainerDetails(anyInt()))
        .thenReturn(Collections.singletonList(breakPackReceiveContainerDetails));
    when(reportConfig.getAtlasDaBreakPackBackOutReportFacilityIds())
        .thenReturn(Collections.singletonList(123));
    when(reportService.createExcelReportForBreakPackReceivedContainers(
            breakPackReceiveContainerDetails))
        .thenReturn(workbook);
    when(reportService.createHtmlTemplateForBreakPackBackOutContainers(
            breakPackReceiveContainerDetails))
        .thenReturn(mailTemplate);
    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doNothing().when(mailService).sendMail(any(MimeMessage.class));
    reportingSchedulerJobs.breakPackReceivedContainersReportGeneratorScheduler();
    verify(reportService, Mockito.times(1)).fetchBreakPackReceiveContainerDetails(anyInt());
    verify(reportService, Mockito.times(1))
        .createExcelReportForBreakPackReceivedContainers(breakPackReceiveContainerDetails);
    verify(reportService, Mockito.times(1))
        .createHtmlTemplateForBreakPackBackOutContainers(breakPackReceiveContainerDetails);
    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testBreakPackReceivedContainersReportGeneratorSchedulerThrowException()
      throws Exception {
    List<BreakPackLabelInfo> breakPackReceiveContainerDetails =
        mockBreakPackReceiveContainerDetails();
    Workbook workbook = mock(Workbook.class);
    String mailTemplate = "";
    when(reportService.fetchBreakPackReceiveContainerDetails(anyInt()))
        .thenReturn(Collections.singletonList(breakPackReceiveContainerDetails));
    when(reportService.createExcelReportForBreakPackReceivedContainers(
            breakPackReceiveContainerDetails))
        .thenReturn(workbook);
    when(reportService.createHtmlTemplateForBreakPackBackOutContainers(
            breakPackReceiveContainerDetails))
        .thenReturn(mailTemplate);
    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    when(reportConfig.getAtlasDaBreakPackBackOutReportFacilityIds())
        .thenReturn(Collections.singletonList(123));
    doThrow(ReceivingException.class).when(mailService).sendMail(any(MimeMessage.class));
    reportingSchedulerJobs.breakPackReceivedContainersReportGeneratorScheduler();
    verify(reportService, Mockito.times(1)).fetchBreakPackReceiveContainerDetails(anyInt());
    verify(reportService, Mockito.times(1))
        .createExcelReportForBreakPackReceivedContainers(breakPackReceiveContainerDetails);
    verify(reportService, Mockito.times(1))
        .createHtmlTemplateForBreakPackBackOutContainers(breakPackReceiveContainerDetails);
    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testBreakPackReceivedContainersReportGeneratorSchedulerWithEmptyContainers()
      throws Exception {
    when(reportConfig.getAtlasDaBreakPackBackOutReportFacilityIds())
        .thenReturn(Collections.singletonList(123));
    when(reportService.fetchBreakPackReceiveContainerDetails(anyInt()))
        .thenReturn(Collections.singletonList(Collections.emptyList()));
    reportingSchedulerJobs.breakPackReceivedContainersReportGeneratorScheduler();
    verify(reportService, Mockito.times(1)).fetchBreakPackReceiveContainerDetails(anyInt());
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
