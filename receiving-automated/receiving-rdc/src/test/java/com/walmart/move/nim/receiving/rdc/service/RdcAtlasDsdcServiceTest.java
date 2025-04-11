package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackRequest;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogRequest;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcAtlasDsdcServiceTest {

  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private PrintJobService printJobService;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcInstructionService rdcInstructionService;
  @Mock private LabelDataService labelDataService;
  @Mock private AuditLogPersisterService auditLogPersisterService;
  @Mock private RdcDaService rdcDaService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerService containerService;
  @Mock private ReceiptService receiptService;
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  @InjectMocks private RdcAtlasDsdcService rdcAtlasDsdcService;
  @Mock private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private DefaultAuditLogProcessor defaultAuditLogProcessor;
  @Mock private AuditLogProcessor auditLogProcessor;
  @Mock private RdcDeliveryService rdcDeliveryService;
  @Mock private RdcMessagePublisher rdcMessagePublisher;
  @Mock private RdcOsdrService rdcOsdrSummaryService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private OutboxConfig outboxConfig;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    MockitoAnnotations.initMocks(this);
    Gson gson = new Gson();
    ReflectionTestUtils.setField(rdcAtlasDsdcService, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        rdcInstructionUtils,
        instructionPersisterService,
        tenantSpecificConfigReader,
        printJobService,
        rdcContainerUtils,
        rdcReceivingUtils,
        rdcInstructionService,
        labelDataService,
        auditLogPersisterService,
        rdcDaService,
        containerPersisterService,
        containerService,
        receiptService,
        kafkaAthenaPublisher,
        defaultAuditLogProcessor,
        auditLogProcessor,
        symboticPutawayPublishHelper,
        rdcMessagePublisher,
        rdcOsdrSummaryService,
        rdcDeliveryService);
  }

  @Test
  public void testReceiveAuditPack() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);
    Container container = new Container();
    container.setDeliveryNumber(22223L);
    PrintJob printJob = mockPrintJob();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    assertNotNull(receivePackResponse);
    assertNotNull(receivePackResponse.getDeliveryNumber());
    assertNotNull(receivePackResponse.getPackNumber());
    assertNotNull(receivePackResponse.getAsnNumber());
    assertNotNull(receivePackResponse.getTrackingId());
    assertNotNull(receivePackResponse.getAuditStatus());
    assertNotNull(receivePackResponse.getReceivingStatus());
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(2))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1)).getContainerDetails(any(), any(), any(), any());
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(any());
    verify(rdcDeliveryService, times(1)).callGdmToUpdatePackStatus(any(), any());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveAuditPackThrowsException() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    PrintJob printJob = mockPrintJob();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new Container());
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    Mockito.doThrow(ReceivingInternalException.class)
        .when(rdcDeliveryService)
        .callGdmToUpdatePackStatus(any(), any());

    rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());

    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(2))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1)).getContainerDetails(any(), any(), any(), any());
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(any());
    verify(rdcDeliveryService, times(1)).callGdmToUpdatePackStatus(any(), any());
  }

  @Test
  public void testReceivePackDeliveryNumberNotExist() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                gdmError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                gdmError.getErrorCode(),
                gdmError.getErrorHeader()));
    try {
      rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingException);
      ReceivingException receivingException = (ReceivingException) exception;
      assertNotNull(receivingException.getHttpStatus());
      assertNotNull(receivingException.getErrorResponse());
      assertNotNull(receivingException.getMessage());
    }
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceivePackLabelDataNotExist() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(Collections.emptyList());
    try {
      rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingBadDataException);
      ReceivingBadDataException receivingBadDataException = (ReceivingBadDataException) exception;
      assertNotNull(receivingBadDataException.getErrorCode());
      assertNotNull(receivingBadDataException.getDescription());
      assertTrue(
          receivingBadDataException
              .getErrorCode()
              .equalsIgnoreCase(ExceptionCodes.NO_ALLOCATIONS_FOR_DSDC_FREIGHT));
      assertTrue(
          receivingBadDataException
              .getDescription()
              .equalsIgnoreCase(
                  String.format(
                      ReceivingException.NO_ALLOCATIONS_FOR_DSDC_FREIGHT,
                      receivePackRequest.getPackNumber(),
                      receivePackRequest.getAsnNumber())));
    }
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
  }

  @Test
  public void testReceiveAuditPackWithSsccAlreadyReceivedAndPrintJob() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(auditLogEntity);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false))
        .thenReturn(false);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    PrintJob printJob = mockPrintJob();
    when(printJobService.preparePrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(labelDataList);
    rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(printJobService, times(1)).preparePrintJob(any(), any(), any(), any());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(auditLogPersisterService)
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false);
    verify(labelDataService)
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
  }

  @Test
  public void testReceivePackDeliveryNumberNotFound() {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = null;
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    try {
      rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingBadDataException);
      ReceivingBadDataException receivingBadDataException = (ReceivingBadDataException) exception;
      assertNotNull(receivingBadDataException.getErrorCode());
      assertNotNull(receivingBadDataException.getDescription());
      assertTrue(
          receivingBadDataException
              .getErrorCode()
              .equalsIgnoreCase(ExceptionCodes.RECEIVE_PACK_INTERNAL_ERROR));
      assertTrue(
          receivingBadDataException
              .getDescription()
              .equalsIgnoreCase(ReceivingException.DELIVERY_NUMBER_NOT_FOUND));
    }
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
  }

  @Test
  public void testReceiveAuditPackWithNoPendingAudits() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    PrintJob printJob = mockPrintJob();
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Arrays.asList());
    osdrSummary.setAuditPending(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_COMPLETE_DELIVERY_FOR_LAST_AUDIT_TAG_ENABLED,
            false))
        .thenReturn(true);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditLogByDeliveryNumberAndStatus(
            gdmDeliveryDocumentList.get(0).getDeliveryNumber(), AuditStatus.PENDING))
        .thenReturn(new ArrayList<>());
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new Container());
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
    when(rdcOsdrSummaryService.getOsdrSummary(anyLong(), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    assertNotNull(receivePackResponse);
    assertNotNull(receivePackResponse.getDeliveryNumber());
    assertNotNull(receivePackResponse.getPackNumber());
    assertNotNull(receivePackResponse.getAsnNumber());
    assertNotNull(receivePackResponse.getTrackingId());
    assertNotNull(receivePackResponse.getAuditStatus());
    assertNotNull(receivePackResponse.getReceivingStatus());
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(2))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1)).getContainerDetails(any(), any(), any(), any());
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(any());
    verify(rdcDeliveryService, times(1)).callGdmToUpdatePackStatus(any(), any());
    verify(rdcMessagePublisher, times(1)).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(anyLong(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveAuditPackWithNoPendingAudits_LabelsCountNotMatching() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData1 = new LabelData();
    LabelData labelData2 = new LabelData();
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Arrays.asList());
    osdrSummary.setAuditPending(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_COMPLETE_DELIVERY_FOR_LAST_AUDIT_TAG_ENABLED,
            false))
        .thenReturn(true);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditLogByDeliveryNumberAndStatus(
            gdmDeliveryDocumentList.get(0).getDeliveryNumber(), AuditStatus.PENDING))
        .thenReturn(new ArrayList<>());
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DUPLICATE_SSCC_BLOCKED_FOR_MULTIPLE_DELIVERIES,
            false))
        .thenReturn(true);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.AVAILABLE.name()))
        .thenReturn(labelDataList);
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.AVAILABLE.name());
  }

  @Test
  public void testReceiveAuditPackWithNoPendingAudits_OSDRSummary_ThrowsException()
      throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    PrintJob printJob = mockPrintJob();
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Arrays.asList());
    osdrSummary.setAuditPending(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_COMPLETE_DELIVERY_FOR_LAST_AUDIT_TAG_ENABLED,
            false))
        .thenReturn(true);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditLogByDeliveryNumberAndStatus(
            gdmDeliveryDocumentList.get(0).getDeliveryNumber(), AuditStatus.PENDING))
        .thenReturn(new ArrayList<>());
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new Container());
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
    when(rdcOsdrSummaryService.getOsdrSummary(anyLong(), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);
    when(rdcOsdrSummaryService.getOsdrSummary(anyLong(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ExceptionCodes.INVALID_DELIVERY_RECEIPTS_REQ,
                HttpStatus.NOT_FOUND,
                ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE));
    try {
      rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingBadDataException);
    }
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(anyLong(), any(HttpHeaders.class));
  }

  /**
   * Mocking of ReceivePackRequest
   *
   * @return
   */
  private ReceivePackRequest mockReceivePackRequest() {
    String asnNumber = "8062558";
    String packNumber = "00000227002624669200";
    ReceivePackRequest receivePackRequest = new ReceivePackRequest(asnNumber, packNumber, null);
    return receivePackRequest;
  }

  /**
   * Mocking of AuditLogEntity
   *
   * @return
   */
  private AuditLogEntity mockAuditLogEntity() throws Exception {
    AuditLogEntity auditLogEntity =
        (AuditLogEntity)
            convertData(
                readFileData("dsdc_receive_pack_mock_data/audit_log.json"), AuditLogEntity.class);
    return auditLogEntity;
  }

  /**
   * Mocking of mockDeliveryDocument
   *
   * @return
   */
  private List<DeliveryDocument> mockDeliveryDocument() throws Exception {
    Type listType = new TypeToken<List<DeliveryDocument>>() {}.getType();
    return new Gson()
        .fromJson(readFileData("dsdc_receive_pack_mock_data/gdm_response.json"), listType);
  }

  /**
   * Mocking of LabelData
   *
   * @return
   */
  private List<LabelData> mockLabelData() throws Exception {
    Type listType = new TypeToken<List<LabelData>>() {}.getType();
    return new Gson()
        .fromJson(readFileData("dsdc_receive_pack_mock_data/label_data.json"), listType);
  }

  /**
   * Mocking of PrintJob
   *
   * @return
   */
  private PrintJob mockPrintJob() throws Exception {
    return (PrintJob)
        convertData(readFileData("dsdc_receive_pack_mock_data/print_job.json"), PrintJob.class);
  }

  /**
   * Mocking of PrintJob
   *
   * @return
   */
  private Map<String, Object> mockPrintLabelData() throws Exception {
    return (Map<String, Object>)
        convertData(readFileData("dsdc_receive_pack_mock_data/print_label_data.json"), Map.class);
  }

  private Object convertData(String data, Class clas) throws Exception {
    return new Gson().fromJson(data, clas);
  }

  private String readFileData(String path) throws IOException {
    File resource = new ClassPathResource(path).getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  /**
   * Mocking of Instruction
   *
   * @return
   */
  private Instruction mockInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(1L);
    return instruction;
  }

  @Test
  public void testReceiveDsdcPacksInAtlas() throws Exception {
    Instruction instruction = mockInstruction();
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    gdmDeliveryDocumentList.get(0).setAuditDetails(Boolean.TRUE);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(any(), any()))
        .thenReturn(gdmDeliveryDocumentList);
    PrintJob printJob = new PrintJob();
    printJob.setId(231456L);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(defaultAuditLogProcessor);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
        instructionRequest, httpHeaders, gdmDeliveryDocumentList);
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
  }

  @Test
  public void testBuildParentContainerForDsdc() throws Exception {
    Instruction instruction = mockInstruction();
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("097123456");
    receivedContainer1.setParentTrackingId("E23434576534576");
    receivedContainer1.setPoNumber("MOCK_PO");
    receivedContainer1.setPoLine(1);
    receivedContainer1.setReceiver(1);
    receivedContainers.add(receivedContainer1);
    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("097123457");
    receivedContainer2.setParentTrackingId("E23434576534576");
    receivedContainer2.setPoNumber("MOCK_PO");
    receivedContainer2.setPoLine(2);
    receivedContainer2.setReceiver(2);
    receivedContainers.add(receivedContainer2);
    gdmDeliveryDocumentList.get(0).setAuditDetails(Boolean.TRUE);
    rdcAtlasDsdcService.buildParentContainerForDsdc(
        labelDataList.get(0),
        receivedContainers,
        "E23434576534576",
        gdmDeliveryDocumentList.get(0));
    assertNotNull(receivedContainers);
  }

  @Test
  public void testBuildParentContainerForDsdc_NoContainerDetails() throws Exception {
    Instruction instruction = mockInstruction();
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).getAllocation().setContainer(null);
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("097123456");
    receivedContainer1.setParentTrackingId("E23434576534576");
    receivedContainer1.setPoNumber("MOCK_PO");
    receivedContainer1.setPoLine(1);
    receivedContainer1.setReceiver(1);
    receivedContainers.add(receivedContainer1);
    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("097123457");
    receivedContainer1.setParentTrackingId("E23434576534576");
    receivedContainer2.setPoNumber("MOCK_PO");
    receivedContainer2.setPoLine(2);
    receivedContainer2.setReceiver(2);
    receivedContainers.add(receivedContainer2);
    gdmDeliveryDocumentList.get(0).setAuditDetails(Boolean.TRUE);
    rdcAtlasDsdcService.buildParentContainerForDsdc(
        labelDataList.get(0),
        receivedContainers,
        "E23434576534576",
        gdmDeliveryDocumentList.get(0));
    assertNotNull(receivedContainers);
  }

  private InstructionRequest mockInstructionForDSDCPacks() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);
    instructionRequest.setSscc("43432323");
    instructionRequest.setDeliveryNumber("21688370");
    instructionRequest.setDoorNumber(ReceivingConstants.DEFAULT_DOOR);
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    return instructionRequest;
  }

  @Test
  public void testSaveAuditLogsWithPendingAuditStatus() {
    AuditLogRequest auditLogRequest = mockAuditLogRequest(AuditStatus.PENDING);
    Instruction instruction = getMockInstructionForAudit();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(2134567L);
    deliveryDocument.setAsnNumber("ASN1324827478654");
    doReturn(null)
        .when(auditLogPersisterService)
        .getAuditDetailsByAsnNumberAndSsccAndStatus(any(), any(), any());
    doReturn(mockAuditLogEntity(AuditStatus.PENDING).get(0))
        .when(auditLogPersisterService)
        .saveAuditLogData(any());
    AuditLogEntity auditLogEntity =
        rdcAtlasDsdcService.saveAuditLogs(instruction, deliveryDocument);
    assertNotNull(auditLogEntity);
  }

  @Test
  public void testSaveAuditLogsWithPendingAuditStatus_AuditLogExists() {
    AuditLogRequest auditLogRequest = mockAuditLogRequest(AuditStatus.PENDING);
    Instruction instruction = getMockInstructionForAudit();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(2134567L);
    deliveryDocument.setAsnNumber("ASN1324827478654");
    doReturn(mockAuditLogEntity(AuditStatus.PENDING).get(0))
        .when(auditLogPersisterService)
        .getAuditDetailsByAsnNumberAndSsccAndStatus(any(), any(), any());
    AuditLogEntity auditLogEntity =
        rdcAtlasDsdcService.saveAuditLogs(instruction, deliveryDocument);
    assertNotNull(auditLogEntity);
  }

  @Test
  public void testReceiveDsdcPacksInAtlasWhenAuditIsNotRequired() throws Exception {
    Instruction instruction = mockInstruction();
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    List<LabelData> labelDataList = mockLabelData();
    gdmDeliveryDocumentList.get(0).setAuditDetails(Boolean.FALSE);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(any(), any()))
        .thenReturn(gdmDeliveryDocumentList);
    PrintJob printJob = new PrintJob();
    printJob.setId(231456L);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(defaultAuditLogProcessor);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber("43432323", "8062558"))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber("43432323", "8062558")).thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new Container());
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory((Container) any());
    rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
        instructionRequest, httpHeaders, gdmDeliveryDocumentList);
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
  }

  @Test
  public void testAggregateContainerItemQuantityByPoPoLineCasePackItem_1PoLine() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setQuantity(3);
    containerItem1.setPurchaseReferenceNumber("0323232323");
    containerItem1.setPurchaseReferenceLineNumber(1);
    containerItems.add(containerItem1);
    List<ContainerItem> result =
        rdcAtlasDsdcService.aggregateContainerItemQuantityByPoPoLine(containerItems);

    assertNotNull(result);
    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getQuantity().intValue(), 3);
  }

  @Test
  public void testAggregateContainerItemQuantityByPoPoLineCasePackItem_1PoLineMultipleQuantities() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setQuantity(3);
    containerItem1.setPurchaseReferenceNumber("0323232323");
    containerItem1.setPurchaseReferenceLineNumber(1);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setQuantity(3);
    containerItem2.setPurchaseReferenceNumber("0323232323");
    containerItem2.setPurchaseReferenceLineNumber(1);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    List<ContainerItem> result =
        rdcAtlasDsdcService.aggregateContainerItemQuantityByPoPoLine(containerItems);

    assertNotNull(result);
    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getQuantity().intValue(), 6);
  }

  @Test
  public void testAggregateContainerItemQuantityByPoPoLineCasePackItem_MultiplePoLines() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setQuantity(3);
    containerItem1.setPurchaseReferenceNumber("0323232323");
    containerItem1.setPurchaseReferenceLineNumber(1);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setQuantity(5);
    containerItem2.setPurchaseReferenceNumber("0323232323");
    containerItem2.setPurchaseReferenceLineNumber(2);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    List<ContainerItem> result =
        rdcAtlasDsdcService.aggregateContainerItemQuantityByPoPoLine(containerItems);

    assertNotNull(result);
    assertEquals(result.size(), 2);
    assertEquals(result.get(0).getQuantity().intValue(), 3);
    assertEquals(result.get(1).getQuantity().intValue(), 5);

    // Quantities in original list of containerItems remains same
    assertEquals(containerItems.get(0).getQuantity().intValue(), 3);
    assertEquals(containerItems.get(1).getQuantity().intValue(), 5);
  }

  @Test
  public void
      testAggregateContainerItemQuantityByPoPoLineCasePackItem_MultiplePoLinesWithMoreReceivedQty() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setQuantity(3);
    containerItem1.setPurchaseReferenceNumber("0323232323");
    containerItem1.setPurchaseReferenceLineNumber(1);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setQuantity(5);
    containerItem2.setPurchaseReferenceNumber("0323232324");
    containerItem2.setPurchaseReferenceLineNumber(1);
    ContainerItem containerItem3 = new ContainerItem();
    containerItem3.setQuantity(10);
    containerItem3.setPurchaseReferenceNumber("0323232324");
    containerItem3.setPurchaseReferenceLineNumber(1);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    containerItems.add(containerItem3);
    List<ContainerItem> result =
        rdcAtlasDsdcService.aggregateContainerItemQuantityByPoPoLine(containerItems);

    assertNotNull(result);
    assertEquals(result.size(), 2);
    assertEquals(result.get(0).getQuantity().intValue(), 3);
    assertEquals(result.get(1).getQuantity().intValue(), 15);

    // Quantities in original list of containerItems remains same
    assertEquals(containerItems.get(0).getQuantity().intValue(), 3);
    assertEquals(containerItems.get(1).getQuantity().intValue(), 5);
    assertEquals(containerItems.get(2).getQuantity().intValue(), 10);
  }

  @Test
  public void testReceiveDsdcPacksInAtlasForAsyncFlow() throws Exception {
    Instruction instruction = mockInstruction();
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    List<LabelData> labelDataList = mockLabelData();
    gdmDeliveryDocumentList.get(0).setAuditDetails(Boolean.FALSE);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(any(), any()))
        .thenReturn(gdmDeliveryDocumentList);
    PrintJob printJob = new PrintJob();
    printJob.setId(231456L);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(defaultAuditLogProcessor);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber("43432323", "8062558"))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber("43432323", "8062558")).thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new Container());
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    doNothing().when(rdcReceivingUtils).persistOutboxEvents(any());
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(), any());
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put("countryCode", "us");
    destination.put("buNumber", "32679");
    container.setDestination(destination);
    when(containerPersisterService.getConsolidatedContainerForPublish(any())).thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RAPID_RELAYER_CALL_BACK_ENABLED_FOR_DSDC_RECEIVING,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getDsdcAsyncFlowChildContainerCount()).thenReturn(0);
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory((Container) any());
    rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
        instructionRequest, httpHeaders, gdmDeliveryDocumentList);
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
  }

  @Test
  public void testReceiveDsdcPacksInAtlasForAsyncFlow_WithOutboxEnabled() throws Exception {
    Instruction instruction = mockInstruction();
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    List<LabelData> labelDataList = mockLabelData();
    gdmDeliveryDocumentList.get(0).setAuditDetails(Boolean.FALSE);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(any(), any()))
        .thenReturn(gdmDeliveryDocumentList);
    PrintJob printJob = new PrintJob();
    printJob.setId(231456L);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(defaultAuditLogProcessor);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber("43432323", "8062558"))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber("43432323", "8062558")).thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new Container());
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    doNothing().when(rdcReceivingUtils).persistOutboxEvents(any());
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(), any());
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put("countryCode", "us");
    destination.put("buNumber", "32679");
    container.setDestination(destination);
    when(containerPersisterService.getConsolidatedContainerForPublish(any())).thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RAPID_RELAYER_CALL_BACK_ENABLED_FOR_DSDC_RECEIVING,
            false))
        .thenReturn(true);
    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .metaData(null)
            .eventIdentifier("")
            .payloadRef(new PayloadRef())
            .build();
    when(rdcReceivingUtils.buildOutboxEvent(any(), any()))
        .thenReturn(Collections.singletonList(outboxEvent));
    when(rdcManagedConfig.getDsdcAsyncFlowChildContainerCount()).thenReturn(0);
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory((Container) any());
    rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
        instructionRequest, httpHeaders, gdmDeliveryDocumentList);
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
  }

  private Instruction getMockInstructionForAudit() {
    Instruction instruction = new Instruction();
    instruction.setCreateUserId("sysadmin");
    instruction.setSsccNumber("001234567890");
    instruction.setFacilityCountryCode("us");
    instruction.setFacilityNum(32679);
    instruction.setCompleteUserId("sysadmin");
    return instruction;
  }

  private List<AuditLogEntity> mockAuditLogEntity(AuditStatus auditStatus) {
    AuditLogEntity auditLogEntity = new AuditLogEntity();
    auditLogEntity.setId(1l);
    auditLogEntity.setAsnNumber("ASN1719188772378238721");
    auditLogEntity.setDeliveryNumber(658790751l);
    auditLogEntity.setSsccNumber("SSCC167217");
    auditLogEntity.setAuditStatus(auditStatus);
    auditLogEntity.setCreatedBy("sysadmin");
    auditLogEntity.setCreatedTs(new Date());
    auditLogEntity.setCompletedBy("sysadmin");
    auditLogEntity.setCompletedTs(new Date());
    auditLogEntity.setUpdatedBy("sysadmin");
    auditLogEntity.setLastUpdatedTs(new Date());
    auditLogEntity.setVersion(1);
    return Arrays.asList(auditLogEntity);
  }

  private AuditLogRequest mockAuditLogRequest(AuditStatus auditStatus) {
    return new AuditLogRequest(658790751l, auditStatus.getStatus(), MockHttpHeaders.getHeaders());
  }

  @Test
  public void receivePackByTrackingId() throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setSscc("00000747640093894854");
    labelDataList.get(0).setAsnNumber("658790752");
    Container containerByTrackingId = mockContainer();
    Instruction instruction = mockInstruction();
    List<DeliveryDocument> gdmDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    gdmDeliveryDocumentList
        .stream()
        .forEach(
            doc -> {
              doc.getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      deliveryDocumentLine -> {
                        deliveryDocumentLine.setPurchaseReferenceNumber("6180390353");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(240);
                      });
            });
    ReceivePackRequest receivePackRequest =
        ReceivePackRequest.builder()
            .packNumber(labelDataList.get(0).getSscc())
            .asnNumber(labelDataList.get(0).getAsnNumber())
            .build();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Container container = new Container();
    container.setDeliveryNumber(22223L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);

    Container consolidateContainer = new Container();
    consolidateContainer.setInventoryStatus(InventoryStatus.PICKED.name());

    when(labelDataService.findByTrackingId(trackingId)).thenReturn(labelDataList.get(0));
    when(containerService.findByTrackingId(trackingId)).thenReturn(containerByTrackingId);
    when(instructionPersisterService.getInstructionById(containerByTrackingId.getInstructionId()))
        .thenReturn(instruction);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(null);
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(auditLogPersisterService.saveAuditLogData(any(AuditLogEntity.class)))
        .thenReturn(auditLogEntity);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(containerItem);
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(consolidateContainer);
    doNothing().when(rdcContainerUtils).publishContainersToInventory((Container) any());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
    assertNotNull(receivePackResponse);
    assertEquals(receivePackResponse.getDeliveryNumber(), "22223");
    assertEquals(receivePackResponse.getPackNumber(), "00000747640093894854");
    assertEquals(receivePackResponse.getTrackingId(), trackingId);
    assertEquals(receivePackResponse.getReceivingStatus(), "COMPLETE");
    verify(labelDataService, times(1)).findByTrackingId(trackingId);
    verify(containerService, times(1)).findByTrackingId(trackingId);
    verify(instructionPersisterService, times(1))
        .getInstructionById(containerByTrackingId.getInstructionId());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(auditLogPersisterService, times(1)).saveAuditLogData(any(AuditLogEntity.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(any());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory((Container) any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
  }

  @Test
  public void receivePackByTrackingIdWithOutboxPatternEnabled() throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setSscc("00000747640093894854");
    labelDataList.get(0).setAsnNumber("658790752");
    Container containerByTrackingId = mockContainer();
    Instruction instruction = mockInstruction();
    List<DeliveryDocument> gdmDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    gdmDeliveryDocumentList
        .stream()
        .forEach(
            doc -> {
              doc.getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      deliveryDocumentLine -> {
                        deliveryDocumentLine.setPurchaseReferenceNumber("6180390353");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(240);
                      });
            });
    ReceivePackRequest receivePackRequest =
        ReceivePackRequest.builder()
            .packNumber(labelDataList.get(0).getSscc())
            .asnNumber(labelDataList.get(0).getAsnNumber())
            .build();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Container container = new Container();
    container.setDeliveryNumber(22223L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);

    Container consolidateContainer = new Container();
    consolidateContainer.setInventoryStatus(InventoryStatus.PICKED.name());

    when(labelDataService.findByTrackingId(trackingId)).thenReturn(labelDataList.get(0));
    when(containerService.findByTrackingId(trackingId)).thenReturn(containerByTrackingId);
    when(instructionPersisterService.getInstructionById(containerByTrackingId.getInstructionId()))
        .thenReturn(instruction);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(null);
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(auditLogPersisterService.saveAuditLogData(any(AuditLogEntity.class)))
        .thenReturn(auditLogEntity);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(containerItem);
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false))
        .thenReturn(true);
    when(rdcReceivingUtils.buildOutboxEventsForAsyncFlow(any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing().when(rdcReceivingUtils).persistOutboxEvents(any());
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
    assertNotNull(receivePackResponse);
    assertEquals(receivePackResponse.getDeliveryNumber(), "22223");
    assertEquals(receivePackResponse.getPackNumber(), "00000747640093894854");
    assertEquals(receivePackResponse.getTrackingId(), trackingId);
    assertEquals(receivePackResponse.getReceivingStatus(), "COMPLETE");
    verify(labelDataService, times(1)).findByTrackingId(trackingId);
    verify(containerService, times(1)).findByTrackingId(trackingId);
    verify(instructionPersisterService, times(1))
        .getInstructionById(containerByTrackingId.getInstructionId());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(auditLogPersisterService, times(1)).saveAuditLogData(any(AuditLogEntity.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false);
    verify(rdcReceivingUtils, times(1)).buildOutboxEventsForAsyncFlow(any(), any());
    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(any());
  }

  @Test
  public void receivePackByTrackingIdWithOutboxPatternEnabledThrowException() throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setSscc("00000747640093894854");
    labelDataList.get(0).setAsnNumber("658790752");
    Container containerByTrackingId = mockContainer();
    Instruction instruction = mockInstruction();
    List<DeliveryDocument> gdmDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    gdmDeliveryDocumentList
        .stream()
        .forEach(
            doc -> {
              doc.getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      deliveryDocumentLine -> {
                        deliveryDocumentLine.setPurchaseReferenceNumber("6180390353");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(240);
                      });
            });
    ReceivePackRequest receivePackRequest =
        ReceivePackRequest.builder()
            .packNumber(labelDataList.get(0).getSscc())
            .asnNumber(labelDataList.get(0).getAsnNumber())
            .build();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Container container = new Container();
    container.setDeliveryNumber(22223L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);

    Container consolidateContainer = new Container();
    consolidateContainer.setInventoryStatus(InventoryStatus.PICKED.name());

    when(labelDataService.findByTrackingId(trackingId)).thenReturn(labelDataList.get(0));
    when(containerService.findByTrackingId(trackingId)).thenReturn(containerByTrackingId);
    when(instructionPersisterService.getInstructionById(containerByTrackingId.getInstructionId()))
        .thenReturn(instruction);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(null);
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(auditLogPersisterService.saveAuditLogData(any(AuditLogEntity.class)))
        .thenReturn(auditLogEntity);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(containerItem);
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false))
        .thenReturn(true);
    when(rdcReceivingUtils.buildOutboxEventsForAsyncFlow(any(), any()))
        .thenReturn(Collections.emptyList());
    doThrow(new RuntimeException()).when(rdcReceivingUtils).persistOutboxEvents(any());

    try {
      rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
    } catch (Exception ex) {
    }

    verify(labelDataService, times(1)).findByTrackingId(trackingId);
    verify(containerService, times(1)).findByTrackingId(trackingId);
    verify(instructionPersisterService, times(1))
        .getInstructionById(containerByTrackingId.getInstructionId());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(auditLogPersisterService, times(1)).saveAuditLogData(any(AuditLogEntity.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false);
    verify(rdcReceivingUtils, times(1)).buildOutboxEventsForAsyncFlow(any(), any());
    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(any());
  }

  @Test
  public void receivePackByTrackingIdWithOutboxPatternEnabledAndAuditLogNotExist()
      throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setSscc("00000747640093894854");
    labelDataList.get(0).setAsnNumber("658790752");
    Container containerByTrackingId = mockContainer();
    Instruction instruction = mockInstruction();
    List<DeliveryDocument> gdmDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    gdmDeliveryDocumentList
        .stream()
        .forEach(
            doc -> {
              doc.getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      deliveryDocumentLine -> {
                        deliveryDocumentLine.setPurchaseReferenceNumber("6180390353");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(240);
                      });
            });
    ReceivePackRequest receivePackRequest =
        ReceivePackRequest.builder()
            .packNumber(labelDataList.get(0).getSscc())
            .asnNumber(labelDataList.get(0).getAsnNumber())
            .build();
    AuditLogEntity auditLogEntity = null;
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Container container = new Container();
    container.setDeliveryNumber(22223L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);

    Container consolidateContainer = new Container();
    consolidateContainer.setInventoryStatus(InventoryStatus.PICKED.name());

    when(labelDataService.findByTrackingId(trackingId)).thenReturn(labelDataList.get(0));
    when(containerService.findByTrackingId(trackingId)).thenReturn(containerByTrackingId);
    when(instructionPersisterService.getInstructionById(containerByTrackingId.getInstructionId()))
        .thenReturn(instruction);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(null);
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(auditLogPersisterService.saveAuditLogData(any(AuditLogEntity.class)))
        .thenReturn(auditLogEntity);
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(containerItem);
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false))
        .thenReturn(true);
    when(rdcReceivingUtils.buildOutboxEventsForAsyncFlow(any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing().when(rdcReceivingUtils).persistOutboxEvents(any());
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
    assertNotNull(receivePackResponse);
    assertEquals(receivePackResponse.getDeliveryNumber(), "22223");
    assertEquals(receivePackResponse.getPackNumber(), "00000747640093894854");
    assertEquals(receivePackResponse.getTrackingId(), trackingId);
    assertEquals(receivePackResponse.getReceivingStatus(), "COMPLETE");
    verify(labelDataService, times(1)).findByTrackingId(trackingId);
    verify(containerService, times(1)).findByTrackingId(trackingId);
    verify(instructionPersisterService, times(1))
        .getInstructionById(containerByTrackingId.getInstructionId());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(auditLogPersisterService, times(0)).saveAuditLogData(any(AuditLogEntity.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
            false);
    verify(rdcReceivingUtils, times(1)).buildOutboxEventsForAsyncFlow(any(), any());
    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(any());
  }

  @Test
  public void receivePackByTrackingIdLabelDoesNotExist() throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = null;
    when(labelDataService.findByTrackingId(trackingId)).thenReturn(null);
    try {
      rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
    } catch (Exception ex) {
      assertTrue(ex instanceof ReceivingBadDataException);
    }
    verify(labelDataService, times(1)).findByTrackingId(trackingId);
  }

  @Test
  public void testReceiveAuditPackWithMoreThanOneLabelCountForGivenSSCC() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelDataWithMultipleLabels();
    Instruction instruction = mockInstruction();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);
    Container container = new Container();
    container.setDeliveryNumber(22223L);
    PrintJob printJob = mockPrintJob();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DUPLICATE_SSCC_BLOCKED_FOR_MULTIPLE_DELIVERIES,
            false))
        .thenReturn(false);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(Collections.emptyList());
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    assertNotNull(receivePackResponse);
    assertNotNull(receivePackResponse.getDeliveryNumber());
    assertNotNull(receivePackResponse.getPackNumber());
    assertNotNull(receivePackResponse.getAsnNumber());
    assertNotNull(receivePackResponse.getTrackingId());
    assertNotNull(receivePackResponse.getAuditStatus());
    assertNotNull(receivePackResponse.getReceivingStatus());
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(labelDataService, times(1))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(printJobService, times(1)).createPrintJob(any(), any(), any(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(2))
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(rdcContainerUtils, times(1)).getContainerDetails(any(), any(), any(), any());
    verify(receiptService, times(1))
        .buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(any());
    verify(rdcDeliveryService, times(1)).callGdmToUpdatePackStatus(any(), any());
  }

  @Test
  public void testUpdateAuditPack() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    receivePackRequest.setEventType("CANCELLED");
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);
    Container container = new Container();
    container.setDeliveryNumber(22223L);
    PrintJob printJob = mockPrintJob();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    String receivePackResponse =
        rdcAtlasDsdcService.updatePack(receivePackRequest, MockHttpHeaders.getHeaders());
    assertNotNull(receivePackResponse);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateAuditPack_SSCCAlreadyReceived() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    receivePackRequest.setEventType("CANCELLED");
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);
    Container container = new Container();
    container.setDeliveryNumber(22223L);
    PrintJob printJob = mockPrintJob();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    String receivePackResponse =
        rdcAtlasDsdcService.updatePack(receivePackRequest, MockHttpHeaders.getHeaders());
    assertNotNull(receivePackResponse);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateAuditPack_SSCCNotFound() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    receivePackRequest.setEventType("CANCELLED");
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    Instruction instruction = mockInstruction();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);
    Container container = new Container();
    container.setDeliveryNumber(22223L);
    PrintJob printJob = mockPrintJob();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(null);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            any(List.class), any(InstructionRequest.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber()))
        .thenReturn(labelDataList);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(printJobService.createPrintJob(any(), any(), any(), any())).thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(container);
    when(rdcContainerUtils.getContainerDetails(any(), any(), any(), any()))
        .thenReturn(new ContainerDetails());
    when(receiptService.buildReceiptsFromContainerItems(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(any(), any(), any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(any()))
        .thenReturn(new Container());
    doNothing().when(rdcDeliveryService).callGdmToUpdatePackStatus(any(), any());
    String receivePackResponse =
        rdcAtlasDsdcService.updatePack(receivePackRequest, MockHttpHeaders.getHeaders());
    assertNotNull(receivePackResponse);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
  }

  /**
   * Mock continer
   *
   * @return
   */
  private Container mockContainer() {
    Container container = new Container();
    container.setInstructionId(90552L);
    container.setDeliveryNumber(22223L);
    container.setSsccNumber("00001402188023599070");
    return container;
  }

  /**
   * LabelDataList With Multiple labels
   *
   * @return
   */
  private List<LabelData> mockLabelDataWithMultipleLabels() throws Exception {
    Type listType = new TypeToken<List<LabelData>>() {}.getType();
    return new Gson()
        .fromJson(
            readFileData("dsdc_receive_pack_mock_data/label_data_multiple_count.json"), listType);
  }

  @Test
  public void receivePackByTrackingIdSsccAlreadyReceivedAndPrintJob() throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setSscc("00000747640093894854");
    labelDataList.get(0).setAsnNumber("658790752");
    Container containerByTrackingId = mockContainer();
    Instruction instruction = mockInstruction();
    List<DeliveryDocument> gdmDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    gdmDeliveryDocumentList
        .stream()
        .forEach(
            doc -> {
              doc.getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      deliveryDocumentLine -> {
                        deliveryDocumentLine.setPurchaseReferenceNumber("6180390353");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(240);
                      });
            });
    ReceivePackRequest receivePackRequest =
        ReceivePackRequest.builder()
            .packNumber(labelDataList.get(0).getSscc())
            .asnNumber(labelDataList.get(0).getAsnNumber())
            .build();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Container container = new Container();
    container.setDeliveryNumber(22223L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);

    Container consolidateContainer = new Container();
    consolidateContainer.setInventoryStatus(InventoryStatus.PICKED.name());

    when(labelDataService.findByTrackingId(trackingId)).thenReturn(labelDataList.get(0));
    when(containerService.findByTrackingId(trackingId)).thenReturn(containerByTrackingId);
    when(instructionPersisterService.getInstructionById(containerByTrackingId.getInstructionId()))
        .thenReturn(instruction);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(auditLogEntity);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false))
        .thenReturn(false);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(labelDataList);
    ReceivePackResponse receivePackResponse =
        rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
    assertNotNull(receivePackResponse);
    assertEquals(receivePackResponse.getDeliveryNumber(), "22223");
    assertEquals(receivePackResponse.getPackNumber(), "00000747640093894854");
    assertEquals(receivePackResponse.getTrackingId(), trackingId);
    assertEquals(receivePackResponse.getReceivingStatus(), "COMPLETE");
    verify(labelDataService, times(1)).findByTrackingId(trackingId);
    verify(containerService, times(1)).findByTrackingId(trackingId);
    verify(instructionPersisterService, times(1))
        .getInstructionById(containerByTrackingId.getInstructionId());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false);
    verify(labelDataService)
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
  }

  @Test
  public void testReceiveAuditPackWithSsccAlreadyReceivedAndBlockRequest() throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(auditLogEntity);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false))
        .thenReturn(true);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(labelDataList);
    try {
      rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingBadDataException);
      ReceivingBadDataException receivingBadDataException = (ReceivingBadDataException) exception;
      assertNotNull(receivingBadDataException.getErrorCode());
      assertNotNull(receivingBadDataException.getDescription());
    }
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(printJobService, times(0)).preparePrintJob(any(), any(), any(), any());
    verify(labelDataService, times(0))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(auditLogPersisterService)
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false);
    verify(labelDataService)
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
  }

  @Test
  public void receivePackByTrackingIdSsccAlreadyReceivedAndBlockRequest() throws Exception {
    String trackingId = "E23434576534576";
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setSscc("00000747640093894854");
    labelDataList.get(0).setAsnNumber("658790752");
    Container containerByTrackingId = mockContainer();
    Instruction instruction = mockInstruction();
    List<DeliveryDocument> gdmDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    gdmDeliveryDocumentList
        .stream()
        .forEach(
            doc -> {
              doc.getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      deliveryDocumentLine -> {
                        deliveryDocumentLine.setPurchaseReferenceNumber("6180390353");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(240);
                      });
            });
    ReceivePackRequest receivePackRequest =
        ReceivePackRequest.builder()
            .packNumber(labelDataList.get(0).getSscc())
            .asnNumber(labelDataList.get(0).getAsnNumber())
            .build();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Container container = new Container();
    container.setDeliveryNumber(22223L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("032323233");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(4);

    Container consolidateContainer = new Container();
    consolidateContainer.setInventoryStatus(InventoryStatus.PICKED.name());

    when(labelDataService.findByTrackingId(trackingId)).thenReturn(labelDataList.get(0));
    when(containerService.findByTrackingId(trackingId)).thenReturn(containerByTrackingId);
    when(instructionPersisterService.getInstructionById(containerByTrackingId.getInstructionId()))
        .thenReturn(instruction);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING))
        .thenReturn(auditLogEntity);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(auditLogEntity);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(labelDataList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false))
        .thenReturn(true);
    try {
      rdcAtlasDsdcService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);

    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingBadDataException);
      ReceivingBadDataException receivingBadDataException = (ReceivingBadDataException) exception;
      assertNotNull(receivingBadDataException.getErrorCode());
      assertNotNull(receivingBadDataException.getDescription());
    }
    verify(labelDataService, times(1)).findByTrackingId(trackingId);
    verify(containerService, times(1)).findByTrackingId(trackingId);
    verify(instructionPersisterService, times(1))
        .getInstructionById(containerByTrackingId.getInstructionId());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.PENDING);
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false);
    verify(labelDataService)
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
  }

  @Test
  public void testReceiveAuditPackWithSsccAlreadyReceivedAndBlockRequestWithLabelCompleted()
      throws Exception {
    ReceivePackRequest receivePackRequest = mockReceivePackRequest();
    AuditLogEntity auditLogEntity = mockAuditLogEntity();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<LabelData> labelDataList = mockLabelData();
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber()))
        .thenReturn(auditLogEntity);
    when(rdcInstructionService.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryDocumentList);
    when(auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false))
        .thenReturn(true);
    when(labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name()))
        .thenReturn(labelDataList);
    try {
      rdcAtlasDsdcService.receivePack(receivePackRequest, MockHttpHeaders.getHeaders());
    } catch (Exception exception) {
      assertNotNull(exception);
      assertTrue(exception instanceof ReceivingBadDataException);
      ReceivingBadDataException receivingBadDataException = (ReceivingBadDataException) exception;
      assertNotNull(receivingBadDataException.getErrorCode());
      assertNotNull(receivingBadDataException.getDescription());
    }
    verify(auditLogPersisterService, times(1))
        .getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    verify(rdcInstructionService, times(1))
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(any(List.class), any(InstructionRequest.class));
    verify(printJobService, times(0)).preparePrintJob(any(), any(), any(), any());
    verify(labelDataService, times(0))
        .findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    verify(auditLogPersisterService)
        .getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    verify(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false);
    verify(labelDataService)
        .findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
  }
}
