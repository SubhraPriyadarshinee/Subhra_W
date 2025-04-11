package com.walmart.move.nim.receiving.acc.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.service.ACCDeliveryMetaDataService;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationService;
import com.walmart.move.nim.receiving.acc.service.PreLabelDeliveryService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.PurgeConfig;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.core.service.MailService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AccSchedulerJobsTest extends ReceivingTestBase {
  @InjectMocks AccSchedulerJobs accSchedulerJobs;
  @Mock private ACLNotificationService aclNotificationService;
  @Mock private MailService mailService;
  @Mock private ReportService reportService;
  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;
  @Mock private DeliveryService deliveryService;
  @Mock private LocationService locationService;

  @Mock private PreLabelDeliveryService genericPreLabelDeliveryEventProcessor;

  @Mock private ACCManagedConfig accManagedConfig;
  @Mock private AppConfig appConfig;
  @Spy private ReportConfig reportConfig;
  @Spy private PurgeConfig purgeConfig;
  @Mock private ACCDeliveryMetaDataService deliveryMetaDataService;

  private MimeMessage mimeMessage = null;
  private DeliveryEvent doorAssignDeliveryEvent;
  private DeliveryEvent deliveryEvent;
  private List<DeliveryEvent> deliveryEvents;
  private DeliveryDetails deliveryDetails;
  private Long deliveryNumber;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    doorAssignDeliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventType(ReceivingConstants.EVENT_DOOR_ASSIGNED)
            .deliveryNumber(123456L)
            .url("https://delivery.test")
            .retriesCount(0)
            .build();

    deliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventType(ReceivingConstants.EVENT_PO_ADDED)
            .eventStatus(EventTargetStatus.IN_RETRY)
            .deliveryNumber(123456L)
            .url("https://delivery.test")
            .retriesCount(0)
            .build();

    doorAssignDeliveryEvent.setFacilityCountryCode("US");
    doorAssignDeliveryEvent.setFacilityNum(32818);
    deliveryEvent.setFacilityCountryCode("US");
    deliveryEvent.setFacilityNum(32818);

    deliveryEvents = new ArrayList<>();

    deliveryNumber = 123456L;

    Properties props = new Properties();
    props.put(ReportingConstants.MAIL_HOST_KEY, ReportingConstants.MAIL_HOST_VALUE);
    Session session = Session.getDefaultInstance(props, null);
    mimeMessage = new MimeMessage(session);

    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
    } catch (IOException e) {
      assert (false);
    }
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryService);
    reset(locationService);
    reset(mailService);
    reset(deliveryEventPersisterService);
    reset(genericPreLabelDeliveryEventProcessor);
  }

  @Test
  public void testAclNotificationReportGeneratorScheduler() throws ReceivingException {

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doNothing().when(mailService).sendMail(any(MimeMessage.class));
    when(aclNotificationService.createExcelReportForAclNotificationLogs(any())).thenReturn(null);
    StringBuilder message = new StringBuilder("Test Mail Report");
    when(reportService.createHtmlTemplateForReportingForEntity(anyList(), anyString(), anyString()))
        .thenReturn(message);

    try {
      accSchedulerJobs.aclNotificationReportGeneratorScheduler();
    } catch (IOException e) {
      fail("Exception should not be thrown.");
    }

    verify(mailService, Mockito.times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, Mockito.times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testAclNotificationReportGeneratorSchedulerException()
      throws ReceivingException, IOException {

    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doThrow(ReceivingException.class).when(mailService).sendMail(any(MimeMessage.class));

    accSchedulerJobs.aclNotificationReportGeneratorScheduler();

    verify(mailService, times(1)).prepareMailMessage(any(MailTemplate.class));
    verify(mailService, times(1)).sendMail(any(MimeMessage.class));
  }

  @Test
  public void testPreLabelGenerationSchedulerForDoorAssign() {
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.PENDING);
    deliveryEvents.add(doorAssignDeliveryEvent);
    doReturn(deliveryDetails)
        .when(genericPreLabelDeliveryEventProcessor)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.TRUE)
        .when(genericPreLabelDeliveryEventProcessor)
        .validateDeliveryDetailsAndLocationInfo(anyLong(), any());
    when(deliveryEventPersisterService.getDeliveryForScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(deliveryEvents);
    accSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(1))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.DELETE));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEventForScheduler(doorAssignDeliveryEvent);
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .fetchDeliveryDetails(anyString(), eq(deliveryNumber));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .validateDeliveryDetailsAndLocationInfo(eq(deliveryNumber), any());
  }

  @Test
  public void testPreLabelGenerationSchedulerForDoorAssign_NullDeliveryDetails() {
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.PENDING);
    deliveryEvents.add(doorAssignDeliveryEvent);
    doReturn(null)
        .when(genericPreLabelDeliveryEventProcessor)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.TRUE)
        .when(genericPreLabelDeliveryEventProcessor)
        .validateDeliveryDetailsAndLocationInfo(anyLong(), any());
    when(deliveryEventPersisterService.getDeliveryForScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(deliveryEvents);
    accSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(1))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.PENDING));
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEventForScheduler(doorAssignDeliveryEvent);
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .fetchDeliveryDetails(anyString(), eq(deliveryNumber));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .validateDeliveryDetailsAndLocationInfo(eq(deliveryNumber), any());
  }

  @Test
  public void testPreLabelGenerationSchedulerForDoorAssign_DoorNotOnline() {
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.PENDING);
    deliveryEvents.add(doorAssignDeliveryEvent);
    doReturn(deliveryDetails)
        .when(genericPreLabelDeliveryEventProcessor)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.FALSE)
        .when(genericPreLabelDeliveryEventProcessor)
        .validateDeliveryDetailsAndLocationInfo(anyLong(), any());
    when(deliveryEventPersisterService.getDeliveryForScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(deliveryEvents);
    accSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(1))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.DELETE));
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEventForScheduler(doorAssignDeliveryEvent);
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .fetchDeliveryDetails(anyString(), eq(deliveryNumber));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .validateDeliveryDetailsAndLocationInfo(eq(deliveryNumber), any());
  }

  @Test
  public void testPreLabelGenerationSchedulerForDoorAssign_DoorInfoNotAvailable() {
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.PENDING);
    deliveryEvents.add(doorAssignDeliveryEvent);
    doReturn(deliveryDetails)
        .when(genericPreLabelDeliveryEventProcessor)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(null)
        .when(genericPreLabelDeliveryEventProcessor)
        .validateDeliveryDetailsAndLocationInfo(anyLong(), any());
    when(deliveryEventPersisterService.getDeliveryForScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(deliveryEvents);
    accSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(1))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.PENDING));
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEventForScheduler(doorAssignDeliveryEvent);
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .fetchDeliveryDetails(anyString(), eq(deliveryNumber));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .validateDeliveryDetailsAndLocationInfo(eq(deliveryNumber), any());
  }

  @Test
  public void testPreLabelGenerationForOtherEvents() {
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.DELETE);
    doReturn(deliveryDetails)
        .when(genericPreLabelDeliveryEventProcessor)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.TRUE)
        .when(genericPreLabelDeliveryEventProcessor)
        .validateDeliveryDetailsAndLocationInfo(anyLong(), any());
    when(deliveryEventPersisterService.getDeliveryForScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(Collections.singletonList(deliveryEvent));
    accSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(0))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.DELETE));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEventForScheduler(deliveryEvent);
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .fetchDeliveryDetails(anyString(), eq(deliveryNumber));
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .validateDeliveryDetailsAndLocationInfo(eq(deliveryNumber), any());
  }

  @Test
  public void testPreLabelStalenessCheck() {
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    when(deliveryEventPersisterService.getStaleDeliveryEvents(any(Date.class)))
        .thenReturn(Collections.singletonList(doorAssignDeliveryEvent));
    accSchedulerJobs.preLabelStaleCheck();
    verify(deliveryEventPersisterService, times(1))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.STALE));
  }

  @Test
  public void testCompleteSystematicallyReopenedDeliveries() {
    when(accManagedConfig.getSysReopenedLifeInMin()).thenReturn(30);
    accSchedulerJobs.completeSystematicallyReopenedDeliveries();
    verify(deliveryMetaDataService, times(1)).completeSystematicallyReopenedDeliveriesBefore(any());
  }
}
