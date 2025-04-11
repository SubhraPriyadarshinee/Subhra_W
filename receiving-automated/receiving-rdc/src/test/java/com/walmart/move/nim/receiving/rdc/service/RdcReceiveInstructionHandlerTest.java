package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponseWithRdsResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdsResponse;
import com.walmart.move.nim.receiving.rdc.model.VoidLPNRequest;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class RdcReceiveInstructionHandlerTest {

  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionStateValidator instructionStateValidator;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private NimRdsService nimRdsService;
  @Mock private ReceiptService receiptService;
  @Mock private PrintJobService printJobService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private RdcFixitProblemService rdcFixitProblemService;
  @Mock private RdcLpnUtils rdcLpnUtils;
  @Mock private MirageRestApiClient mirageRestApiClient;
  @Mock private RdcDeliveryService rdcDeliveryService;
  @Mock private RdcSlottingUtils rdcSlottingUtils;
  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @Mock private RdcAsyncUtils rdcAsyncUtils;
  @Mock private LabelDataService labelDataService;
  @Mock private RdcDaService rdcDaService;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Mock private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  @InjectMocks private RdcReceiveInstructionHandler rdcReceiveInstructionHandler;

  private Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String LPN = "F32818000020003005";
  private HttpHeaders headers;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
    ReflectionTestUtils.setField(rdcReceiveInstructionHandler, "gson", gson);
    ReflectionTestUtils.setField(rdcInstructionUtils, "rdcManagedConfig", rdcManagedConfig);
    ReflectionTestUtils.setField(rdcInstructionUtils, "rdcContainerUtils", rdcContainerUtils);
  }

  @BeforeMethod
  public void setup() {
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    headers.add(RdcConstants.WFT_LOCATION_ID, "23");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    headers.add(RdcConstants.WFT_SCC_CODE, "0086623");
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        instructionPersisterService,
        instructionHelperService,
        instructionStateValidator,
        rdcInstructionUtils,
        rdcContainerUtils,
        nimRdsService,
        receiptService,
        containerPersisterService,
        rdcManagedConfig,
        appConfig,
        printJobService,
        tenantSpecificConfigReader,
        containerItemRepository,
        symboticPutawayPublishHelper,
        tenantSpecificConfigReader,
        rdcFixitProblemService,
        rdcLpnUtils,
        mirageRestApiClient,
        rdcDeliveryService,
        rdcSlottingUtils,
        rdcDaService,
        rdcSSTKInstructionUtils);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_LocationNotAvailable()
      throws ReceivingBadDataException, IOException {
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(
        12345L, receiveInstructionRequest, mockHttpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_LocationNotAvailable()
      throws ReceivingBadDataException, IOException {
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(receiveInstructionRequest, mockHttpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_InstructionNotFound() throws ReceivingException, IOException {
    doThrow(
            new ReceivingException(
                ReceivingException.INSTRUCTION_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR))
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_InstructionPalletCancelled()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doThrow(
            new ReceivingException(
                String.format(
                    ReceivingException.COMPLETE_INSTRUCTION_PALLET_CANCELLED,
                    getMockInstruction().getCompleteUserId()),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
                ReceivingException.ERROR_HEADER_PALLET_CANCELLED))
        .when(instructionStateValidator)
        .validate(any());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_InstructionIsAlreadyCompleted()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doThrow(
            new ReceivingException(
                String.format(
                    ReceivingException.COMPLETE_INSTRUCTION_ALREADY_COMPLETE,
                    getMockInstruction().getCompleteUserId()),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
                ReceivingException.ERROR_HEADER_PALLET_COMPLETED))
        .when(instructionStateValidator)
        .validate(any());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_MultiUserUnableToVerify()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstructionForInvalidUserId());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_MultiUserDifferentOwner()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstructionForDifferentOwner());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_receiveContainers_RDSErrorPath()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DocumentLine> documentLines =
                    (List<DocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVE_CONTAINERS_RDS_ERROR,
                ReceivingConstants.NO_CONTAINERS_RECEIVED_IN_RDS))
        .when(nimRdsService)
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(1))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(printJobService, times(0)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
  }

  @Test
  public void testReceiveInstruction_validateOverageExceedsMaxReceiveQty_throwsException()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DocumentLine> documentLines =
                    (List<DocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 60L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));

    try {
      ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
      rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    } catch (ReceivingBadDataException exception) {
      assertEquals(
          exception.getDescription(),
          String.format(
              RdcConstants.RDC_OVERAGE_EXCEED_ERROR_MESSAGE,
              getMockReceiveInstructionRequest()
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getPurchaseReferenceNumber(),
              getMockReceiveInstructionRequest()
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getPurchaseReferenceLineNumber()));
      assertEquals(exception.getErrorCode(), ReceivingException.OVERAGE_ERROR_CODE);
    }

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(1))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(nimRdsService, times(0))
        .quantityChange(any(Integer.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_DA_validateOverageExceedsMaxReceiveQty_throwsException()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR))
        .when(rdcReceivingUtils)
        .validateOverage(anyList(), any(Integer.class), any(HttpHeaders.class), anyBoolean());
    try {
      ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
      rdcReceiveInstructionHandler.receiveInstruction(receiveInstructionRequest, headers);
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
    }
    verify(rdcReceivingUtils, times(2))
        .validateOverage(anyList(), any(Integer.class), any(HttpHeaders.class), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_validateNearOverageLimit_throwsException()
      throws ReceivingException, IOException {
    Long currentReceiveQtyFromRDS = 50L;
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DocumentLine> documentLines =
                    (List<DocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0),
                    currentReceiveQtyFromRDS);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));

    try {
      ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
      rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    } catch (ReceivingBadDataException exception) {
      assertEquals(
          exception.getDescription(),
          String.format(
              ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_OVERAGE_LIMIT,
              getMockReceiveInstructionRequest()
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getPurchaseReferenceNumber(),
              getMockReceiveInstructionRequest()
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getPurchaseReferenceLineNumber()));
      assertEquals(
          exception.getErrorCode(), ReceivingException.TOTAL_RECEIVE_QTY_EXCEEDS_OVERAGE_LIMIT);
    }

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(1))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveSSCCInstruction_validateNearOverageLimit_throwsException()
      throws ReceivingException, IOException {
    Long currentReceiveQtyFromRDS = 50L;
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstructionWithSscc());
    doReturn(0).when(rdcContainerUtils).receivedContainerQuantityBySSCC(anyString());
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .checkIfContainerAlreadyReceived(anyString(), anyLong(), anyInt(), anyInt());
    try {
      ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
      rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.SSCC_RECEIVED_ALREADY);
    }
    verify(rdcContainerUtils, times(1)).isContainerReceivedBySSCCAndItem(anyString(), anyLong());
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(0))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testReceiveSSCCInstruction_validateNearOverageLimit_SSCCReceivedAlready_throwsException()
          throws ReceivingException, IOException {
    Long currentReceiveQtyFromRDS = 50L;
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstructionWithSscc());
    doReturn(1).when(rdcContainerUtils).receivedContainerQuantityBySSCC(anyString());
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .checkIfContainerAlreadyReceived(anyString(), anyLong(), anyInt(), anyInt());
    try {
      ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
      rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.SSCC_RECEIVED_ALREADY);
    }

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(0))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).isContainerReceivedBySSCCAndItem(anyString(), anyLong());
  }

  @Test
  public void testReceiveSSCCInstruction_validateNearOverageLimit_SSCCNotReceived_throwsException()
      throws ReceivingException, IOException {
    Long currentReceiveQtyFromRDS = 50L;
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstructionWithSscc());
    doReturn(0).when(rdcContainerUtils).receivedContainerQuantityBySSCC(anyString());
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .checkIfContainerAlreadyReceived(anyString(), anyLong(), anyInt(), anyInt());

    try {
      ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
      rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.SSCC_RECEIVED_ALREADY);
    }

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(0))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).isContainerReceivedBySSCCAndItem(anyString(), anyLong());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_validateOverage_RDSError_throwsException()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.GET_RECEIPTS_ERROR_RESPONSE_IN_RDS,
                String.format(
                    ReceivingConstants.GET_RECEIPTS_ERROR_RESPONSE_IN_RDS, "PO Not Found"),
                "PO Not Found"))
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(rdcInstructionUtils, times(1))
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    verify(nimRdsService, times(0))
        .quantityChange(any(Integer.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_HappyPath_ForNonAtlasItem()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);
    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    List<PrintLabelRequest> labelPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .collect(Collectors.toList());

    List<LabelData> containerTagId =
        labelPrintRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        labelPrintRequests
            .get(1)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
  }

  @Test
  public void testReceiveInstruction_LabelFormatChange_ForNonAtlasItem()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getReceiveContainersSuccessResponse();
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(receiveContainersResponseBody);
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), any()))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL), anyBoolean()))
        .thenReturn(Boolean.TRUE);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemHandlingMethod(RdcConstants.SYM_MANUAL_HANDLING_CODE);
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);
    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    List<PrintLabelRequest> labelPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .collect(Collectors.toList());

    List<LabelData> containerTagId =
        labelPrintRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        labelPrintRequests
            .get(1)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(anyString(), any(DeliveryDocument.class), anyInt(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_LabelFormatChangeToMCPIB_ForNonAtlasItem()
      throws ReceivingException, IOException {
    Instruction instruction = getMockInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemHandlingMethod(RdcConstants.SYM_MANUAL_HANDLING_CODE);
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getReceiveContainersSuccessResponse();
    receiveContainersResponseBody
        .getReceived()
        .get(0)
        .getDestinations()
        .get(0)
        .setSlot(RdcConstants.SYMCP_SLOT);
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(receiveContainersResponseBody);
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), any()))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL), anyBoolean()))
        .thenReturn(Boolean.TRUE);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);
    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    List<PrintLabelRequest> labelPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .collect(Collectors.toList());

    List<LabelData> containerTagId =
        labelPrintRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        labelPrintRequests
            .get(1)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(anyString(), any(DeliveryDocument.class), anyInt(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_LabelFormatChangeToAIB_ForNonAtlasItem()
      throws ReceivingException, IOException {
    Instruction instruction = getMockInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemHandlingMethod("");
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getReceiveContainersSuccessResponse();
    receiveContainersResponseBody
        .getReceived()
        .get(0)
        .getDestinations()
        .get(0)
        .setSlot(RdcConstants.SYMCP_SLOT);
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(receiveContainersResponseBody);
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), any()))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL), anyBoolean()))
        .thenReturn(Boolean.TRUE);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);
    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    List<PrintLabelRequest> labelPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .collect(Collectors.toList());

    List<LabelData> containerTagId =
        labelPrintRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        labelPrintRequests
            .get(1)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(anyString(), any(DeliveryDocument.class), anyInt(), any());
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_DA_HappyPath_ForNonAtlasItem() throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    String DCTimeZone = "US/Eastern";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcDaService.receiveContainers(
            any(DeliveryDocument.class),
            any(HttpHeaders.class),
            any(InstructionRequest.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(new InstructionResponseImplNew());
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(receiveInstructionRequest, headers);

    assertNotNull(instructionResponse);
    verify(rdcDaService, times(1))
        .receiveContainers(
            any(DeliveryDocument.class),
            any(HttpHeaders.class),
            any(InstructionRequest.class),
            anyInt(),
            any(ReceiveInstructionRequest.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_ForNonAtlasItem_RunTimeExceptionThrowsException_BackOutRDSLabel()
          throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    doThrow(new ReceivingException("container not found", HttpStatus.BAD_REQUEST))
        .when(containerPersisterService)
        .getConsolidatedContainerForPublish(anyString());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    doNothing()
        .when(nimRdsService)
        .quantityChange(any(Integer.class), anyString(), any(HttpHeaders.class));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            anyString());
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(0))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(0))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1))
        .getContainerDetails(anyString(), anyMap(), any(ContainerType.class), anyString());
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(nimRdsService, times(1))
        .quantityChange(any(Integer.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_SmartSlottingIntegrationEnabledWithRDS_NonAtlasItem_HappyPath()
      throws Exception {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    when(nimRdsService.getReceiveContainersRequestBody(anyMap(), anyString()))
        .thenReturn(getMockReceiveContainersRequestBody());
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            nullable(String.class),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class)))
        .thenReturn(getMockSlottingPalletResponseWithRdsContainers());
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);

    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(0))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(rdcSlottingUtils, times(1))
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            nullable(String.class),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_SmartSlottingIntegrationEnabledWithRDS_NonAtlasItem_Error()
      throws Exception {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    when(nimRdsService.getReceiveContainersRequestBody(anyMap(), anyString()))
        .thenReturn(getMockReceiveContainersRequestBody());
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(rdcSlottingUtils)
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            anyString());
    verify(rdcSlottingUtils, times(1))
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            nullable(String.class),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class));
  }

  @Test
  public void
      testReceiveInstruction_HappyPath_AndNotPublishInstructionToWFT_WhenFeatureFlagIsDisabled()
          throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    com.walmart.move.nim.receiving.core.entity.LabelData labelData =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    when(labelDataService.findByTrackingId(anyString())).thenReturn(labelData);
    doNothing().when(rdcAsyncUtils).labelUpdateToHawkeye(any(HttpHeaders.class), anyList());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionHelperService, times(0))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(rdcInstructionUtils, times(0))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(instructionHelperService, times(0))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), anyString(), anyString());
  }

  @Test
  public void
      testReceiveInstruction_HappyPath_ForAtlasSymItem_PublishSymPutAwayMessageToHawkeye_FlibDelivery()
          throws ReceivingException, IOException {
    headers.add(ReceivingConstants.WMT_FLIB_LOCATION_HEADER_KEY, "true");
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction_Atlas_Converted_Item());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class)))
        .thenReturn(MockRdcInstruction.getAutoSlotFromSlotting());
    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(getMockReceipts());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItemForAtlasItem("SYM1")));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainerForAtlasItem());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem("SYM1"));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_SSTK);

    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    List<PrintLabelRequest> labelPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.ATLAS_RDC_SSTK.getFormat()))
            .collect(Collectors.toList());

    List<LabelData> containerTagId =
        labelPrintRequests
            .get(1)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        labelPrintRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(rdcContainerUtils, times(1))
        .publishContainersToInventory(containerArgumentCaptor.capture());
    assertTrue(
        containerArgumentCaptor.getValue().getContainerItems().get(0).getPromoBuyInd().equals("N"));

    assertNull(
        containerArgumentCaptor.getValue().getContainerItems().get(0).getOutboundChannelMethod());

    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
  }

  @Test
  public void
      testReceiveInstruction_HappyPath_ForAtlasSymItem_proDateValidationIsNotInvoked_WhenDCFinIsNotEnabled()
          throws ReceivingException, IOException {
    headers.add(ReceivingConstants.WMT_FLIB_LOCATION_HEADER_KEY, "true");
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction_Atlas_Converted_Item());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class)))
        .thenReturn(MockRdcInstruction.getAutoSlotFromSlotting());
    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(getMockReceipts());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItemForAtlasItem("SYM1")));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainerForAtlasItem());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem("SYM1"));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_SSTK);

    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
  }

  @Test
  public void testReceiveInstruction_HappyPath_ForAtlasNonSymItem_NotFlibDelivery()
      throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction_Atlas_Converted_Item());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class)))
        .thenReturn(MockRdcInstruction.getAutoSlotFromSlotting());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItemForAtlasItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(getMockReceipts());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItemForAtlasItem(null));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_SSTK);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(mirageRestApiClient, times(0))
        .voidLPN(any(VoidLPNRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_forProblemTag_withAtlasConvertedItems_happyPath()
      throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction_AtlasConvertedItems();
    mockInstruction.setProblemTagId("PTAG1");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isSmartSlottingIntegrationEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class)))
        .thenReturn(MockRdcInstruction.getAutoSlotFromSlotting());
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem("SYM1"));
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(receiptService.createReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(getMockReceipts());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem("SYM1")));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(rdcFixitProblemService).completeProblem(any(Instruction.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.IS_MOVE_PUBLISH_ENABLED, false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .publishMove(anyString(), anyInt(), any(LinkedTreeMap.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_SSTK);
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.ATLAS_RDC_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);
    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(0))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .createReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyInt(), any(), anyString());
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(rdcContainerUtils, times(1))
        .publishContainersToInventory(containerArgumentCaptor.capture());
    assertEquals(
        containerArgumentCaptor.getValue().getInventoryStatus(), InventoryStatus.AVAILABLE.name());
    assertTrue(
        containerArgumentCaptor.getValue().getContainerItems().get(0).getPromoBuyInd().equals("N"));
    verify(rdcLpnUtils, times(1)).getLPNs(anyInt(), any(HttpHeaders.class));
    verify(rdcSlottingUtils, times(1))
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class));
    verify(rdcContainerUtils, times(1))
        .publishMove(anyString(), anyInt(), any(LinkedTreeMap.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1))
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Resource exception from SMART-SLOTTING. Error Code = GLS-RCV-SMART-SLOT-PRIME-404, Error Message = Invalid Slot ID")
  public void
      testReceiveInstruction_receiveAndPublishContainers_AtlasConvertedItems_ErrorFromSmartSlotting()
          throws ReceivingException, IOException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction_AtlasConvertedItems());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isSmartSlottingIntegrationEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(rdcSlottingUtils)
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class));

    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();

    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(0))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(0)).getDCTimeZone(any(Integer.class));
    verify(printJobService, times(0)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(0)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(0))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(0))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(0)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(rdcLpnUtils, times(1)).getLPNs(anyInt(), any(HttpHeaders.class));
    verify(rdcSlottingUtils, times(1))
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class));
    verify(nimRdsService, times(0))
        .quantityChange(any(Integer.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_forProblemTag_andNonAtlasItem_happyPath()
      throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction();
    mockInstruction.setProblemTagId("PTAG123");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(rdcFixitProblemService).completeProblem(any(Instruction.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);

    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
  }

  @Test
  public void testReceiveInstruction_forSSTKVoidLabels() throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction();
    mockInstruction.setProblemTagId("PTAG123");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(rdcFixitProblemService).completeProblem(any(Instruction.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false))
        .thenReturn(true);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .fetchLabelDataAndUpdateLabelStatusToCancelled(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class), anyInt());
    when(rdcLabelGenerationUtils.isSSTKPilotDeliveryEnabled()).thenReturn(true);
    when(rdcLabelGenerationUtils.isAtlasSSTKPilotDelivery(anyLong())).thenReturn(true);
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);

    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcInstructionUtils, times(1)).isAtlasItemSymEligible(any(DeliveryDocumentLine.class));
    verify(rdcLabelGenerationService)
        .fetchLabelDataAndUpdateLabelStatusToCancelled(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class), anyInt());
  }

  @Test
  public void testReceiveInstruction_SymIneligible() throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction();
    mockInstruction.setProblemTagId("PTAG123");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(rdcFixitProblemService).completeProblem(any(Instruction.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false))
        .thenReturn(true);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .fetchLabelDataAndUpdateLabelStatusToCancelled(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class), anyInt());
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);

    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcInstructionUtils, times(1)).isAtlasItemSymEligible(any(DeliveryDocumentLine.class));
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false);
    verify(rdcLabelGenerationService, times(0))
        .fetchLabelDataAndUpdateLabelStatusToCancelled(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class), anyInt());
  }

  @Test
  public void testReceiveInstruction_LabelGenerationDisabled()
      throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction();
    mockInstruction.setProblemTagId("PTAG123");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(rdcFixitProblemService).completeProblem(any(Instruction.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem(null));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    when(rdcInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcLabelGenerationService)
        .fetchLabelDataAndUpdateLabelStatusToCancelled(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class), anyInt());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);

    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false);
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
    verify(rdcInstructionUtils, times(1)).isAtlasItemSymEligible(any(DeliveryDocumentLine.class));
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcLabelGenerationService, times(0))
        .fetchLabelDataAndUpdateLabelStatusToCancelled(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class), anyInt());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_forProblemTag_withNonAtlasItem_throwsException()
      throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction();
    mockInstruction.setProblemTagId("PTAG123");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) throws IOException {
                List<DeliveryDocumentLine> deliveryDocumentLines =
                    (List<DeliveryDocumentLine>) invocation.getArguments()[0];

                return new Pair<>(
                    getMockReceiveInstructionRequest().getDeliveryDocumentLines().get(0), 25L);
              }
            })
        .when(rdcInstructionUtils)
        .getReceiptsFromRDSByDocumentLine(anyList(), any(HttpHeaders.class));

    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(nimRdsService.getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem(null)));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doThrow(new ReceivingException(ReceivingException.COMPLETE_PTAG_ERROR, INTERNAL_SERVER_ERROR))
        .when(rdcFixitProblemService)
        .completeProblem(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(1))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(0))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), any(), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
  }

  private ReceiveContainersRequestBody getMockReceiveContainersRequestBody() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setContainerGroupId("333434");
    containerOrder.setDoorNum("232");
    containerOrder.setQty(344);
    containerOrders.add(containerOrder);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("6");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setDeliveryNumber(Long.valueOf("2356895623"));
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    receiveInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    receiveInstructionRequest.setDeliveryDocumentLines(deliveryDocument.getDeliveryDocumentLines());
    return receiveInstructionRequest;
  }

  private Instruction getMockInstruction() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(23371015L);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  private Instruction getMockInstructionWithSscc() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(23371015L);
    instruction.setSsccNumber("00123456789099");
    instruction.setProjectedReceiveQty(10);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  private Instruction getMockInstruction_AtlasConvertedItems() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(23371015L);
    instruction.setMessageId("32323-43434");
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocumentList.get(0)));

    LinkedTreeMap<String, Object> moveMap = new LinkedTreeMap<>();
    moveMap.put(ReceivingConstants.MOVE_CONTAINER_TAG, "lpn123");
    moveMap.put(ReceivingConstants.MOVE_TO_LOCATION, "100");
    moveMap.put(ReceivingConstants.MOVE_SEQUENCE_NBR, 1);
    instruction.setMove(moveMap);

    return instruction;
  }

  private Instruction getMockInstructionForInvalidUserId() {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("sysadmin");
    return instruction;
  }

  private Instruction getMockInstructionForDifferentOwner() {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("differentOwner");
    instruction.setLastChangeUserId("differentOwner");
    instruction.setCreateUserId("differentOwner");
    return instruction;
  }

  private List<Receipt> getMockReceipts() {
    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(232323L);
    receipt.setPurchaseReferenceNumber("42312323");
    receipts.add(receipt);
    return receipts;
  }

  private PrintJob getMockPrintJob() {
    PrintJob printJob = new PrintJob();
    printJob.setLabelIdentifier(new HashSet<>(Collections.singletonList("232323")));
    printJob.setInstructionId(232323L);
    printJob.setId(1234L);
    return printJob;
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setInstructionId(123L);
    container.setTrackingId("lpn123");
    container.setDeliveryNumber(123456L);
    container.setParentTrackingId(null);
    container.setInventoryStatus("AVAILABLE");
    container.setContainerItems(Collections.singletonList(getMockContainerItem("SYM1")));
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    return container;
  }

  private ContainerItem getMockContainerItem(String asrsAlignment) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setAsrsAlignment(asrsAlignment);
    containerItem.setRotateDate(new Date());
    containerItem.setSlotType(ReceivingConstants.RESERVE_SLOT_TYPE);
    containerItem.setPromoBuyInd("N");
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "false");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    return containerItem;
  }

  private Container getMockContainerForAtlasItem() {
    Container container = new Container();
    container.setInstructionId(123L);
    container.setTrackingId("lpn123");
    container.setDeliveryNumber(123456L);
    container.setParentTrackingId(null);
    container.setInventoryStatus("AVAILABLE");
    container.setContainerItems(
        Collections.singletonList(getMockContainerItemForAtlasItem("SYM1")));
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    return container;
  }

  private ContainerItem getMockContainerItemForAtlasItem(String asrsAlignment) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setAsrsAlignment(asrsAlignment);
    containerItem.setRotateDate(new Date());
    containerItem.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    containerItem.setPromoBuyInd("N");
    return containerItem;
  }

  private Instruction getMockInstruction_Atlas_Converted_Item() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(23371015L);
    instruction.setMessageId("123e4567-e89b-12d3-a456-426655440000");
    instruction.setPurchaseReferenceNumber("4223042727");
    instruction.setPurchaseReferenceLineNumber(2);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  private Optional<DeliveryMetaData> getMockDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("D1234");
    deliveryMetaData.setDeliveryStatus(DeliveryStatus.WRK);
    deliveryMetaData.setDoorNumber("100");
    deliveryMetaData.setCarrierName("SAC123");
    deliveryMetaData.setBillCode("PMO");
    deliveryMetaData.setCarrierScacCode("SCAC1");
    deliveryMetaData.setId(123L);
    return Optional.of(deliveryMetaData);
  }

  private ContainerDetails getMockContainerDetails() {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setContainerId("lpn123");
    containerDetails.setCtrType(ContainerType.PALLET.getText());
    containerDetails.setCtrReusable(false);
    containerDetails.setCtrShippable(false);
    containerDetails.setCtrLabel(new HashMap<>());
    return containerDetails;
  }

  private ProblemLabel getMockProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("issue-123");
    problemLabel.setResolutionId("RES123");
    return problemLabel;
  }

  private List<ContainerDTO> getMockContainerDTO() {
    Transformer<Container, ContainerDTO> transformer = new ContainerTransformer();
    List<Container> containers = Arrays.asList(getMockContainerForAtlasItem());
    return transformer.transformList(containers);
  }

  public SlottingPalletResponse getMockSlottingPalletResponseWithRdsContainers() {
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
        new SlottingPalletResponseWithRdsResponse();
    ReceiveContainersResponseBody receiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("9786855548");
    Destination destination = new Destination();
    destination.setSlot("J8k98");
    List<Destination> destinations = new ArrayList<>();
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainers.add(receivedContainer);
    receiveContainersResponseBody.setReceived(receivedContainers);
    slottingPalletResponseWithRdsResponse.setRds(receiveContainersResponseBody);
    return slottingPalletResponseWithRdsResponse;
  }

  private Instruction getMockDAInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(2323L);
    instruction.setActivityName(WFTInstruction.DA.getActivityName());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    return instruction;
  }

  private ReceiveContainersRequestBody getMockRdsContainersRequest() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setQty(1);
    containerOrder.setPoNumber("34232323");
    containerOrder.setPoLine(1);
    containerOrder.setBreakpackRatio(1);
    containerOrder.setDoorNum("423");
    containerOrder.setUserId("vr03fd4");
    ContainerOrder containerOrder1 = new ContainerOrder();
    containerOrder1.setQty(1);
    containerOrder1.setPoNumber("34232323");
    containerOrder1.setPoLine(1);
    containerOrder1.setBreakpackRatio(1);
    containerOrder1.setDoorNum("423");
    containerOrder1.setUserId("vr03fd4");
    containerOrders.add(containerOrder);
    containerOrders.add(containerOrder1);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  @Test
  public void
      testReceiveInstruction_forProblemTag_withAtlasConvertedItems_happyPath_PrintLabel_WIth_DeliveryNumber()
          throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction_AtlasConvertedItems();
    mockInstruction.setProblemTagId("PTAG1");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(rdcManagedConfig.isSmartSlottingIntegrationEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class)))
        .thenReturn(MockRdcInstruction.getAutoSlotFromSlotting());
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(getMockContainerItem("SYM1"));
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(receiptService.createReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(getMockReceipts());
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(getMockContainerItem("SYM1")));
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class)))
        .thenReturn(getMockContainer());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(rdcFixitProblemService).completeProblem(any(Instruction.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainer());
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateOverage(anyList(), anyInt(), any(Instruction.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.IS_MOVE_PUBLISH_ENABLED, false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .publishMove(anyString(), anyInt(), any(LinkedTreeMap.class), any(HttpHeaders.class));
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_SSTK);
    doNothing()
        .when(rdcContainerUtils)
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    InstructionResponse instructionResponse =
        rdcReceiveInstructionHandler.receiveInstruction(12345L, receiveInstructionRequest, headers);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getContainer().getCtrLabel());
    assertNotNull(response.getPrintJob());
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 3);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.ATLAS_RDC_SSTK.getFormat()))
            .count(),
        2);

    List<PrintLabelRequest> timestampPrintRequests =
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.SSTK_TIMESTAMP.getFormat()))
            .collect(Collectors.toList());
    assertEquals(timestampPrintRequests.size(), 1);
    assertTrue(timestampPrintRequests.get(0).getLabelIdentifier().startsWith("ts-"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(1)).validate(any());
    verify(nimRdsService, times(0))
        .getContainerLabelFromRDS(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .createReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyInt(), any(), anyString());
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any(Integer.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(Integer.class),
            any(HttpHeaders.class));
    verify(appConfig, times(1)).isWftPublishEnabled();
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(rdcContainerUtils, times(1))
        .publishContainersToInventory(containerArgumentCaptor.capture());
    assertEquals(
        containerArgumentCaptor.getValue().getInventoryStatus(), InventoryStatus.AVAILABLE.name());
    assertTrue(
        containerArgumentCaptor.getValue().getContainerItems().get(0).getPromoBuyInd().equals("N"));
    verify(rdcLpnUtils, times(1)).getLPNs(anyInt(), any(HttpHeaders.class));
    verify(rdcSlottingUtils, times(1))
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class));
    verify(rdcContainerUtils, times(1))
        .publishMove(anyString(), anyInt(), any(LinkedTreeMap.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1))
        .publishPutawayMessageToHawkeye(
            any(DeliveryDocument.class),
            any(ReceivedContainer.class),
            any(Instruction.class),
            any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(rdcFixitProblemService, times(1)).completeProblem(any(Instruction.class));
    assertTrue(
        printLabelRequests
            .stream()
            .anyMatch(
                labelRequest ->
                    Optional.ofNullable(labelRequest.getData())
                        .orElse(Collections.emptyList())
                        .stream()
                        .anyMatch(
                            labelData ->
                                LabelConstants.LBL_DELIVERY_NUMBER.equalsIgnoreCase(
                                        labelData.getKey())
                                    && labelData
                                        .getValue()
                                        .equals(
                                            String.valueOf(
                                                gson.fromJson(
                                                        mockInstruction.getDeliveryDocument(),
                                                        DeliveryDocument.class)
                                                    .getDeliveryNumber())))));
  }
}
