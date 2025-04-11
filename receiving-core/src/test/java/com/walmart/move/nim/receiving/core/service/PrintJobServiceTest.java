/** */
package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.repositories.PrintJobRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author a0b02ft */
public class PrintJobServiceTest extends ReceivingTestBase {

  @InjectMocks private PrintJobService printJobService;

  @Mock private PrintJobRepository printJobRepository;

  private PrintJob printJob;
  private List<PrintJob> printJobs = new ArrayList<>();
  private Set<String> printJobLpnSet = new HashSet<>();

  private String userId = "sysadmin";
  private Long instructionId = Long.valueOf("183");
  private String lpn = "a328990000000000000106509";
  private Long deliveryNumber = Long.valueOf("21119003");
  private PageRequest pageReq;

  /** Initialization for mockito annotations. */
  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    printJobLpnSet.add(lpn);

    printJob = new PrintJob();
    printJob.setDeliveryNumber(deliveryNumber);
    printJob.setCreateUserId(userId);
    printJob.setInstructionId(instructionId);
    printJob.setLabelIdentifier(printJobLpnSet);
    printJobs.add(printJob);

    pageReq = PageRequest.of(0, 10);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(Integer.valueOf(32818));
  }

  /** Test case for persisting print job */
  @Test
  public void testCreatePrintJob() {
    when(printJobRepository.save(any(PrintJob.class))).thenReturn(printJob);

    PrintJob response =
        printJobService.createPrintJob(deliveryNumber, instructionId, printJobLpnSet, userId);

    assertEquals(response.getDeliveryNumber(), printJob.getDeliveryNumber());
    assertEquals(response.getInstructionId(), printJob.getInstructionId());
    assertEquals(response.getCreateUserId(), printJob.getCreateUserId());
    assertEquals(response.getLabelIdentifier(), printJob.getLabelIdentifier());

    verify(printJobRepository, times(1)).save(any(PrintJob.class));
    reset(printJobRepository);
  }

  @Test
  public void testSavePrintJobs() {
    List<PrintJob> printJobs = new ArrayList<>();
    printJobs.add(printJob);
    when(printJobRepository.saveAll(anyList())).thenReturn(printJobs);

    List<PrintJob> response = printJobService.savePrintJobs(printJobs);

    assertEquals(response.size(), printJobs.size());
    verify(printJobRepository, times(1)).saveAll(anyList());
    reset(printJobRepository);
  }

  @Test
  public void testGetPrintJobsByDeliveryNumber() {
    when(printJobRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(printJobs);

    List<PrintJob> response = printJobService.getPrintJobsByDeliveryNumber(deliveryNumber);

    assertEquals(response, printJobs);
    verify(printJobRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    reset(printJobRepository);
  }

  @Test
  public void testGetRecentlyPrintedLabelsByDeliveryNumber() {
    when(printJobRepository.getRecentlyPrintedLabelsByDeliveryNumber(
            10, deliveryNumber, 32818, "US"))
        .thenReturn(printJobs);

    List<PrintJob> response =
        printJobService.getRecentlyPrintedLabelsByDeliveryNumber(deliveryNumber, 10);

    assertEquals(response, printJobs);
    verify(printJobRepository, times(1))
        .getRecentlyPrintedLabelsByDeliveryNumber(10, deliveryNumber, 32818, "US");
    reset(printJobRepository);
  }

  @Test
  public void testGetRecentlyPrintedLabelsByDeliveryNumberAndUserId() {
    when(printJobRepository.findRecentlyPrintedLabelsByDeliveryNumberAndUserId(
            10, deliveryNumber, "sysadmin", 32818, "US"))
        .thenReturn(printJobs);

    List<PrintJob> response =
        printJobService.getRecentlyPrintedLabelsByDeliveryNumberAndUserId(
            deliveryNumber, "sysadmin", 10);

    assertEquals(response, printJobs);
    verify(printJobRepository, times(1))
        .findRecentlyPrintedLabelsByDeliveryNumberAndUserId(
            10, deliveryNumber, "sysadmin", 32818, "US");
    reset(printJobRepository);
  }

  @Test
  public void testGetPrintJobsByInstruction() throws ReceivingException {
    when(printJobRepository.findByInstructionId(instructionId)).thenReturn(printJobs);

    List<PrintJob> response = printJobService.getPrintJobByInstruction(instructionId);

    assertEquals(response, printJobs);
    verify(printJobRepository, times(1)).findByInstructionId(instructionId);
    reset(printJobRepository);
  }

  @Test
  public void testGetPrintJobsByInstructionWithNullResponse() {
    when(printJobRepository.findByInstructionId(instructionId)).thenReturn(null);

    try {
      printJobService.getPrintJobByInstruction(instructionId);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.NO_PRINTJOBS_FOR_INSTRUCTION);
    }

    verify(printJobRepository, times(1)).findByInstructionId(instructionId);
    reset(printJobRepository);
  }

  @Test
  public void testGetPrintJobsByInstructionWithEmptyResponse() {
    when(printJobRepository.findByInstructionId(instructionId)).thenReturn(new ArrayList<>());

    try {
      printJobService.getPrintJobByInstruction(instructionId);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.NO_PRINTJOBS_FOR_INSTRUCTION);
    }

    verify(printJobRepository, times(1)).findByInstructionId(instructionId);
    reset(printJobRepository);
  }

  @Test
  public void testDeletePrintjobs() throws ReceivingException {
    when(printJobRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(printJobs);
    doNothing().when(printJobRepository).deleteAll(printJobs);

    printJobService.deletePrintjobs(deliveryNumber);

    verify(printJobRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    verify(printJobRepository, times(1)).deleteAll(printJobs);
    reset(printJobRepository);
  }

  @Test
  public void testDeletePrintjobsWithNullResponse() {
    when(printJobRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(null);

    try {
      printJobService.deletePrintjobs(deliveryNumber);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.NO_PRINTJOBS_FOR_DELIVERY);
    }

    verify(printJobRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    reset(printJobRepository);
  }

  @Test
  public void testDeletePrintjobsWithEmptyResponse() {
    when(printJobRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(new ArrayList<>());

    try {
      printJobService.deletePrintjobs(deliveryNumber);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.NO_PRINTJOBS_FOR_DELIVERY);
    }

    verify(printJobRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    reset(printJobRepository);
  }

  private PrintJob getPrintJob() {
    PrintJob printJob = new PrintJob();
    printJob.setDeliveryNumber(deliveryNumber);
    printJob.setCreateTs(new Date());
    printJob.setCreateUserId(userId);
    printJob.setInstructionId(instructionId);
    printJob.setLabelIdentifier(printJobLpnSet);
    return printJob;
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    PrintJob printJob = getPrintJob();
    printJob.setId(1L);
    printJob.setCreateTs(cal.getTime());

    PrintJob printJob1 = getPrintJob();
    printJob1.setId(10L);
    printJob1.setCreateTs(cal.getTime());

    when(printJobRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(printJob, printJob1));
    doNothing().when(printJobRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.PRINTJOB)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = printJobService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    PrintJob printJob = getPrintJob();
    printJob.setId(1L);
    printJob.setCreateTs(cal.getTime());

    PrintJob printJob1 = getPrintJob();
    printJob1.setId(10L);
    printJob1.setCreateTs(cal.getTime());

    when(printJobRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(printJob, printJob1));
    doNothing().when(printJobRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.PRINTJOB)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = printJobService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    PrintJob printJob = getPrintJob();
    printJob.setId(1L);
    printJob.setCreateTs(cal.getTime());

    PrintJob printJob1 = getPrintJob();
    printJob1.setId(10L);
    printJob1.setCreateTs(new Date());

    when(printJobRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(printJob, printJob1));
    doNothing().when(printJobRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.PRINTJOB)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = printJobService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testPreparePrintJob() {
    PrintJob response =
        printJobService.preparePrintJob(deliveryNumber, instructionId, printJobLpnSet, userId);
    assertEquals(response.getDeliveryNumber(), printJob.getDeliveryNumber());
    assertEquals(response.getInstructionId(), printJob.getInstructionId());
    assertEquals(response.getCreateUserId(), printJob.getCreateUserId());
    assertEquals(response.getLabelIdentifier(), printJob.getLabelIdentifier());
  }
}
