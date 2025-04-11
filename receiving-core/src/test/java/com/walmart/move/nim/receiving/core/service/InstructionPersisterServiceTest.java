package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.DockTagRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class InstructionPersisterServiceTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private DockTagService dockTagService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private MovePublisher movePublisher;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private AppConfig appConfig;
  @Mock private DefaultPutawayHandler defaultPutawayHandler;
  @Mock private PrintJobService printJobService;
  @Mock private ContainerService containerService;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private DockTagRepository dockTagRepository;
  @Mock private ReceiptService receiptService;

  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;

  private final String purchaseReferenceNumber = "9876543210";
  private final Integer purchaseReferenceLineNumber = 1;
  final Long deliveryNumber = 12345678l;
  private List<Instruction> instructionList = new ArrayList<>();

  private PageRequest pageReq;

  @BeforeClass
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);

    pageReq = PageRequest.of(0, 10);

    Instruction instruction1 = MockInstruction.getCreatedInstruction();
    Instruction instruction2 = MockInstruction.getPendingInstruction();

    instructionList.add(instruction1);
    instructionList.add(instruction2);
    ReflectionTestUtils.setField(
        instructionPersisterService, "instructionHelperService", instructionHelperService);
  }

  @AfterMethod
  public void teardown() {
    reset(instructionRepository);
    reset(receiptService);
  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId() {

    Instruction mockInstruction4mDB = MockInstruction.getCreatedInstruction();
    mockInstruction4mDB.setCreateUserId("rxTestUser");

    doReturn(mockInstruction4mDB)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        instructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNotNull(fetchInstructionBySSCCAndUserIdResponse);
    assertSame(fetchInstructionBySSCCAndUserIdResponse.getId(), mockInstruction4mDB.getId());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId_splitPallet() {

    Instruction mockInstruction4mDB = MockInstruction.getCreatedInstruction();
    mockInstruction4mDB.setCreateUserId("rxTestUser");
    mockInstruction4mDB.setInstructionSetId(1l);

    doReturn(mockInstruction4mDB)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyString(), anyLong());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");
    mockInstructionRequest.setInstructionSetId(1l);

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        instructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNotNull(fetchInstructionBySSCCAndUserIdResponse);
    assertSame(fetchInstructionBySSCCAndUserIdResponse.getId(), mockInstruction4mDB.getId());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId_none_indb() {

    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        instructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNull(fetchInstructionBySSCCAndUserIdResponse);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Instruction instruction = MockInstruction.getCompleteInstruction();
    instruction.setCreateTs(cal.getTime());

    Instruction instruction1 = MockInstruction.getCompleteInstruction();
    instruction1.setId(10L);
    instruction1.setCreateTs(cal.getTime());

    when(instructionRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(instruction, instruction1));
    doNothing().when(instructionRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.INSTRUCTION)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = instructionPersisterService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Instruction instruction = MockInstruction.getCompleteInstruction();
    instruction.setCreateTs(cal.getTime());

    Instruction instruction1 = MockInstruction.getCompleteInstruction();
    instruction1.setId(10L);
    instruction1.setCreateTs(cal.getTime());

    when(instructionRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(instruction, instruction1));
    doNothing().when(instructionRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.INSTRUCTION)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = instructionPersisterService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Instruction instruction = MockInstruction.getCompleteInstruction();
    instruction.setCreateTs(cal.getTime());

    Instruction instruction1 = MockInstruction.getCompleteInstruction();
    instruction1.setId(10L);

    when(instructionRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(instruction, instruction1));
    doNothing().when(instructionRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.INSTRUCTION)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = instructionPersisterService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  /** Test report's get instruction by po/po line and delivery */
  @Test
  public void testGetInstructionByPoPoLineAndDeliveryNumber() {
    when(instructionRepository
            .findByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndDeliveryNumber(
                anyString(), anyInt(), anyLong()))
        .thenReturn(instructionList.subList(0, 1));

    List<Instruction> instructionsByPoPoLine =
        instructionPersisterService.getInstructionByPoPoLineAndDeliveryNumber(
            purchaseReferenceNumber, purchaseReferenceLineNumber, deliveryNumber);

    assertEquals(instructionsByPoPoLine.size(), 1);

    verify(instructionRepository, times(1))
        .findByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndDeliveryNumber(
            purchaseReferenceNumber, purchaseReferenceLineNumber, deliveryNumber);
  }

  @Test
  public void testSaveContainersAndInstructionForNonConDockTag() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDoorNumber("101");
    instructionRequest.setDeliveryNumber("1234567");
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    String dockTagId = "c32987000000000000000001";
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    when(configUtils.getDCSpecificMoveDestinationForNonConDockTag(any())).thenReturn("PSN");
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
    Container dockTagContainer = MockInstruction.getDockTagContainer();
    dockTagContainer.setIsConveyable(false);
    dockTagContainer.setCreateTs(new Date());
    dockTagContainer.setLastChangedTs(new Date());
    dockTagContainer.setCompleteTs(new Date());
    dockTagContainer.setPublishTs(new Date());
    when(containerService.createDockTagContainer(any(), any(), any(), anyBoolean()))
        .thenReturn(dockTagContainer);
    when(dockTagService.createDockTag(any(), any(), any(), any())).thenReturn(new DockTag());
    Pair<Instruction, Container> instructionContainerPair =
        instructionPersisterService.saveContainersAndInstructionForNonConDockTag(
            instructionRequest, headers, null);

    assertNotNull(instructionContainerPair.getKey());
    assertNotNull(instructionContainerPair.getValue());
  }

  @Test
  public void testSaveContainersAndInstruction() {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    String dockTagId = "c32987000000000000000001";
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    when(configUtils.getDCSpecificMoveDestinationForNonConDockTag(any())).thenReturn("PSN");
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
    Container dockTagContainer = MockInstruction.getDockTagContainer();
    when(containerService.createDockTagContainer(any(), any(), any(), anyBoolean()))
        .thenReturn(dockTagContainer);

    Instruction instruction =
        instructionPersisterService.saveDockTagInstructionAndContainer(
            instructionRequest, headers, Long.parseLong(instructionRequest.getDeliveryNumber()));

    assertNotNull(instruction);
  }

  @Test
  public void testCreateContainersReceiptsAndSaveInstruction() throws ReceivingException {
    String mockUserId = "MOCK_JUNIT_USER";
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setManualInstruction(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE))
        .thenReturn(true);
    when(instructionRepository.save(instruction)).thenReturn(MockInstruction.getInstruction());
    when(containerService.createAndCompleteParentContainer(
            MockInstruction.getInstruction(), MockInstruction.getUpdateInstructionRequest(), true))
        .thenReturn(MockInstruction.getContainer());
    when(receiptService.createReceiptsFromInstruction(
            MockInstruction.getUpdateInstructionRequest(),
            MockInstruction.getInstruction().getProblemTagId(),
            mockUserId))
        .thenReturn(new ArrayList<>());
    instructionPersisterService.createContainersReceiptsAndSaveInstruction(
        MockInstruction.getUpdateInstructionRequest(), mockUserId, instruction, true);
    verify(instructionRepository, times(1)).save(any(Instruction.class));
  }

  @Test
  public void testFetchInstructionByDeliveryAndGtinAndUserIdReturnsInstruction() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
            anyLong(), anyString(), anyString());

    Instruction instruction =
        instructionPersisterService.fetchInstructionByDeliveryAndGtinAndUserId(
            mockInstructionRequest, mockUserId);
    assertNotNull(instruction);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
            anyLong(), anyString(), anyString());
  }

  @Test
  public void testFetchInstructionByDeliveryAndGtinAndUserIdReturnsNull() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
            anyLong(), anyString(), anyString());

    Instruction instruction =
        instructionPersisterService.fetchInstructionByDeliveryAndGtinAndUserId(
            mockInstructionRequest, mockUserId);

    assertNull(instruction);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
            anyLong(), anyString(), anyString());
  }

  @Test
  public void fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdReturnsInstruction() {
    String mockUserId = "sysadmin";
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setKey(ReceivingConstants.KEY_GTIN);
    gtinScannedData.setValue("00028000114603");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("ABCDEF1234");
    scannedDataMap.put(ReceivingConstants.KEY_GTIN, gtinScannedData);
    scannedDataMap.put(ReceivingConstants.KEY_LOT, lotNumberScannedData);
    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);

    doReturn(Arrays.asList(mockInstructionWithManufactureDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());

    Instruction instructionResponse =
        instructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            Long.parseLong("798001"), scannedDataMap, mockUserId, null);
    assertNotNull(instructionResponse);
  }

  @Test
  public void fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdReturnsNull() {
    String mockUserId = "sysadmin";
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    gtinScannedData.setKey(ReceivingConstants.KEY_GTIN);
    gtinScannedData.setValue("00028000114603");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(
        ApplicationIdentifier.LOT.getApplicationIdentifier());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("ABCDEF1234");
    scannedDataMap.put(ReceivingConstants.KEY_GTIN, gtinScannedData);
    scannedDataMap.put(ReceivingConstants.KEY_LOT, lotNumberScannedData);
    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
            anyLong(), anyString(), anyString());

    Instruction instructionResponse =
        instructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            Long.parseLong("798001"), scannedDataMap, mockUserId, null);
    assertNull(instructionResponse);
  }

  @Test
  public void test_fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId() {

    String mockUserId = "MOCK_UNIT_TEST_USER";

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setProblemTagId("MOCK_PROBLEM_TAG_ID");

    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);

    doReturn(mockInstructionWithManufactureDetails)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());

    Instruction mockInstuctionResponse =
        instructionPersisterService.fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(
            mockInstructionRequest, mockUserId);
    assertNotNull(mockInstuctionResponse);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void test_fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId() {
    String mockUserId = "MOCK_UNIT_TEST_USER";
    String mockProblemTagId = "MOCK_PROBLEM_TAG_ID";

    Map<String, ScannedData> mockScannedDataMap = new HashMap<>();
    mockScannedDataMap.put(
        ApplicationIdentifier.GTIN.getKey(),
        getScannedData(
            ApplicationIdentifier.GTIN.getApplicationIdentifier(),
            ApplicationIdentifier.GTIN.getKey(),
            "00028000114603"));
    mockScannedDataMap.put(
        ApplicationIdentifier.LOT.getKey(),
        getScannedData(
            ApplicationIdentifier.LOT.getApplicationIdentifier(),
            ApplicationIdentifier.LOT.getKey(),
            "ABCDEF1234"));

    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);

    doReturn(Arrays.asList(mockInstructionWithManufactureDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());

    Instruction mockInstructionResponse =
        instructionPersisterService
            .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
                deliveryNumber, mockScannedDataMap, mockUserId, mockProblemTagId);
    assertNotNull(mockInstructionResponse);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  private ScannedData getScannedData(String ai, String key, String value) {
    ScannedData scannedData = new ScannedData();

    scannedData.setApplicationIdentifier(ai);
    scannedData.setKey(key);
    scannedData.setValue(value);

    return scannedData;
  }

  @Test
  public void test_fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setProblemTagId("MOCK_PROBLEM_TAG_ID");

    String mockUserId = "MOCK_UNIT_TEST_USER";
    String mockProblemTagId = "MOCK_PROBLEM_TAG_ID";

    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);

    doReturn(Arrays.asList(mockInstructionWithManufactureDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());

    Instruction mockInstructionResponse =
        instructionPersisterService
            .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
                mockInstructionRequest, mockUserId, mockProblemTagId);
    assertNotNull(mockInstructionResponse);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void
      test_fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setProblemTagId("MOCK_PROBLEM_TAG_ID");

    String mockUserId = "MOCK_UNIT_TEST_USER";

    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);

    doReturn(Arrays.asList(mockInstructionWithManufactureDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());

    instructionPersisterService
        .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
            mockInstructionRequest, mockUserId);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void
      test_fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull_splitPallet() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setInstructionSetId(1l);

    String mockUserId = "MOCK_UNIT_TEST_USER";

    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);
    mockInstructionWithManufactureDetails.setInstructionSetId(1l);

    doReturn(Arrays.asList(mockInstructionWithManufactureDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyString(), anyLong());

    instructionPersisterService
        .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
            mockInstructionRequest, mockUserId);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  public void
      testFetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPoAndPoLineReturnsInstruction() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocument());

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());

    Instruction instruction =
        instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
                mockInstructionRequest.getDeliveryDocuments().get(0),
                mockInstructionRequest,
                mockUserId);
    assertNotNull(instruction);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());
  }

  @Test
  public void
      testFetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPoAndPoLineIsNUllForPOCONReturnsInstruction() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocumentForPOCON());

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberIsNullAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString());

    Instruction instruction =
        instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
                mockInstructionRequest.getDeliveryDocuments().get(0),
                mockInstructionRequest,
                mockUserId);
    assertNotNull(instruction);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberIsNullAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString());
  }

  @Test
  public void
      testFetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocument());

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());

    Instruction instruction =
        instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                mockInstructionRequest.getDeliveryDocuments().get(0),
                mockInstructionRequest,
                mockUserId);
    assertNotNull(instruction);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());
  }

  @Test
  public void testFetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPoAndPoLineReturnsNull() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocument());

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());

    Instruction instruction =
        instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
                mockInstructionRequest.getDeliveryDocuments().get(0),
                mockInstructionRequest,
                mockUserId);

    assertNull(instruction);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());
  }

  @Test
  public void testCheckIfInstructionExistsWithSsccAndItemReturnsNull() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setSscc("009876543221");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocument());

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString());

    List<Instruction> instructions =
        instructionPersisterService.checkIfInstructionExistsWithSscc(mockInstructionRequest);
    assertNull(instructions);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString());
  }

  private List<DeliveryDocument> getMockDeliveryDocument() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("9763140004");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setItemUpc("00000943037194");
    deliveryDocumentLine.setCaseUpc("100000943037194");
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    return Collections.singletonList(deliveryDocument);
  }

  private List<DeliveryDocument> getMockDeliveryDocumentForPOCON() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("9763140004");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setPurchaseRefType("POCON");
    deliveryDocumentLine.setItemUpc("00000943037194");
    deliveryDocumentLine.setCaseUpc("100000943037194");
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    return Collections.singletonList(deliveryDocument);
  }

  private List<DeliveryDocument> getMockDeliveryDocumentFor14DigitUPC() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("9763140004");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setItemUpc("0089943037194");
    deliveryDocumentLine.setCaseUpc("00765843037194");
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    return Collections.singletonList(deliveryDocument);
  }

  @Test
  public void
      testFetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPoAndPoLineReturnsInstruction_split_pallet() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocument());
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_UPC");

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());

    Instruction instruction =
        instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
                mockInstructionRequest.getDeliveryDocuments().get(0),
                mockInstructionRequest,
                mockUserId);
    assertNotNull(instruction);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());
  }

  @Test
  public void
      testFetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPoAndPoLineReturnsInstruction_split_pallet_with_instruction_set_id() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setDeliveryDocuments(getMockDeliveryDocument());
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequest.setInstructionSetId(1234l);

    String mockUserId = "MOCK_JUNIT_USER";

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
            anyLong(), anyList(), anyString(), anyString(), anyInt(), anyLong());

    Instruction instruction =
        instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
                mockInstructionRequest.getDeliveryDocuments().get(0),
                mockInstructionRequest,
                mockUserId,
                mockInstructionRequest.getInstructionSetId());
    assertNotNull(instruction);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
            anyLong(), anyList(), anyString(), anyString(), anyInt(), anyLong());
  }

  @Test
  public void test_findNonSplitPalletInstructionCount() {

    doReturn(0)
        .when(instructionRepository)
        .countByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndCompleteTsIsNull(
            anyString(), anyInt());
    instructionPersisterService.findNonSplitPalletInstructionCount("MOCK_PO_NUM", 1);
    verify(instructionRepository, times(1))
        .countByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndCompleteTsIsNull(
            anyString(), anyInt());
  }

  @Test
  public void
      test_getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLineAndInstructionSetIdIsNull() {
    doReturn(null)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    int prjRcvQtySum =
        instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                32323323L, "MOCK_PO_NUM", 1);
    assertEquals(prjRcvQtySum, 0);
    verify(instructionRepository, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
  }

  @Test
  public void test_getSumOfReceivedQuantityOfPendingInstructionsByPoPoLine() {
    doReturn(null)
        .when(instructionRepository)
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    int rcvQtySum =
        instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                32323323L, "MOCK_PO_NUM", 1);
    assertEquals(rcvQtySum, 0);
    verify(instructionRepository, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
  }

  @Test
  public void
      testGetSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine_ProblemTagPoPoLineNotExists() {
    doReturn(null)
        .when(instructionRepository)
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    Long rcvQtySum =
        instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                "213321212", 1, "32670840244");
    assertEquals(0, (long) rcvQtySum);
    verify(instructionRepository, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  @Test
  public void
      testGetSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine_ProblemTagPoPoLineExists() {
    doReturn(10L)
        .when(instructionRepository)
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    Long rcvQtySum =
        instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                "213321212", 1, "32670840244");
    assertEquals(10, (long) rcvQtySum);
    verify(instructionRepository, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  @Test
  public void testFetchExistingOpenInstructionWithMatchingMessageId() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    when(instructionRepository.findByMessageId(anyString()))
        .thenReturn(MockInstruction.getInstruction());

    Instruction instruction =
        instructionPersisterService.fetchExistingOpenInstruction(
            deliveryDocument, instructionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instruction);
    verify(instructionRepository, times(1)).findByMessageId(anyString());
  }

  @Test
  public void testFetchExistingOpenInstructionWithMatchingSSCC() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("001897898789878");
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    when(instructionRepository
            .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                anyLong(), anyString()))
        .thenReturn(Arrays.asList(MockInstruction.getInstruction()));

    Instruction instruction =
        instructionPersisterService.fetchExistingOpenInstruction(
            deliveryDocument, instructionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instruction);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testFetchExistingOpenInstructionWithMatchingSSCC_MultiUser()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("001897898789878");
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    when(instructionRepository
            .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                anyLong(), anyString()))
        .thenReturn(Arrays.asList(MockInstruction.getInstruction()));

    Instruction instruction =
        instructionPersisterService.fetchExistingOpenInstruction(
            deliveryDocument, instructionRequest, MockHttpHeaders.getUserIdHeader("testUser1"));

    assertNotNull(instruction);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString());
  }

  @Test
  public void testFetchExistingOpenInstructionWithMatchingProblemTagId() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getProblemInstructionRequest();
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    when(instructionRepository.findByMessageId(anyString())).thenReturn(null);

    doReturn(Arrays.asList(MockInstruction.getInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());

    Instruction instruction =
        instructionPersisterService.fetchExistingOpenInstruction(
            deliveryDocument, instructionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instruction);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void testFetchExistingOpenInstructionWithMatchingGTIN() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    when(instructionRepository.findByMessageId(anyString())).thenReturn(null);

    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
            anyLong(), anyString(), anyString(), anyString(), anyString());

    doReturn(Arrays.asList(MockInstruction.getOpenInstruction()))
        .when(instructionRepository)
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());

    Instruction instruction =
        instructionPersisterService.fetchExistingOpenInstruction(
            deliveryDocument, instructionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instruction);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
            anyLong(), anyList(), anyString(), anyString(), anyInt());
  }

  @Test
  public void testCheckIfNewInstructionCanBeCreated_isKotlinEnabled_is_true() {
    OpenQtyResult openQtyResult =
        OpenQtyResult.builder()
            .openQty(10L)
            .totalReceivedQty(Math.toIntExact(10))
            .maxReceiveQty(Long.valueOf(10))
            .flowType(OpenQtyFlowType.POLINE)
            .build();
    doReturn(1l)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    try {
      instructionPersisterService.checkIfNewInstructionCanBeCreated(
          "123", 1, 123L, openQtyResult, true);
    } catch (ReceivingException e) {
      System.out.println("Error code : " + REQUEST_TRANSFTER_INSTR_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorCode(), REQUEST_TRANSFTER_INSTR_ERROR_CODE);
    }
  }

  @Test
  public void testCheckIfNewInstructionCanBeCreated_isKotlinEnabled_is_false() {
    OpenQtyResult openQtyResult =
        OpenQtyResult.builder()
            .openQty(10L)
            .totalReceivedQty(Math.toIntExact(10))
            .maxReceiveQty(Long.valueOf(10))
            .flowType(OpenQtyFlowType.POLINE)
            .build();
    doReturn(1l)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    try {
      instructionPersisterService.checkIfNewInstructionCanBeCreated(
          "123", 1, 123L, openQtyResult, false);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "createInstruction");
    }
  }

  @Test
  public void fetchMultiSkuInstrDeliveryDocument() {
    instructionPersisterService.fetchMultiSkuInstrDeliveryDocument(
        anyString(), anyLong(), anyString(), anyString(), anyString());
    verify(instructionRepository, times(1))
        .findFirstByInstructionCodeAndDeliveryNumberAndSsccNumberAndPurchaseReferenceNumberAndCompleteUserIdOrderByCreateTsDesc(
            anyString(), anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void fetchMultiSkuInstrDeliveryDocumentByDelivery() {
    instructionPersisterService.fetchMultiSkuInstrDeliveryDocumentByDelivery(
        anyString(), anyLong(), anyString(), anyString());
    verify(instructionRepository, times(1))
        .findFirstByInstructionCodeAndDeliveryNumberAndPurchaseReferenceNumberAndCompleteUserIdAndSsccNumberIsNullOrderByCreateTsDesc(
            anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  public void fetchMultiSkuInstrByDelivery() {
    instructionPersisterService.fetchMultiSkuInstrByDelivery(
        anyString(), anyLong(), anyString(), anyString());
    verify(instructionRepository, times(1))
        .findFirstByInstructionCodeAndDeliveryNumberAndPurchaseReferenceNumberAndCompleteUserIdOrderByCreateTsDesc(
            anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  public void test_completeInstructionAndCreateContainerAndReceipt() throws ReceivingException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setManualInstruction(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE))
        .thenReturn(true);
    when(instructionRepository.save(instruction)).thenReturn(MockInstruction.getInstruction());
    when(containerService.createAndCompleteParentContainer(
            MockInstruction.getInstruction(), MockInstruction.getUpdateInstructionRequest(), true))
        .thenReturn(MockInstruction.getContainer());

    UpdateInstructionRequest updateInstructionReq = new UpdateInstructionRequest();
    Instruction instructionResponse = new Instruction();
    final int receivedQuantity = 100;
    final String userId = "k0c0e5k";
    //    instructionPersisterService.updateInstructionAndCreateContainerAndReceipt(
    //        updateInstructionReq,
    //        MockHttpHeaders.getHeaders(),
    //        userId,
    //        instructionResponse,
    //        receivedQuantity);
    //    verify(instructionRepository, times(1)).save(any(Instruction.class));
  }

  @Test
  public void test_completeInstructionAndContainer() throws ReceivingException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setManualInstruction(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE))
        .thenReturn(true);
    when(instructionRepository.save(instruction)).thenReturn(MockInstruction.getInstruction());
    when(containerService.createAndCompleteParentContainer(
            MockInstruction.getInstruction(), MockInstruction.getUpdateInstructionRequest(), true))
        .thenReturn(MockInstruction.getContainer());

    UpdateInstructionRequest updateInstructionReq = new UpdateInstructionRequest();
    Instruction instructionResponse = new Instruction();
    final int receivedQuantity = 100;
    final String userId = "k0c0e5k";
    instructionPersisterService.completeInstructionAndContainer(
        MockHttpHeaders.getHeaders(), instruction);

    verify(containerService, times(1)).containerListComplete(anyList(), anyString());
    verify(instructionRepository, times(1)).save(any(Instruction.class));
  }

  @Test
  public void test_updateInstructionAndCreateContainerAndReceipt() throws ReceivingException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setManualInstruction(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE))
        .thenReturn(true);
    when(instructionRepository.save(instruction)).thenReturn(MockInstruction.getInstruction());
    when(containerService.createAndCompleteParentContainer(
            MockInstruction.getInstruction(), MockInstruction.getUpdateInstructionRequest(), true))
        .thenReturn(MockInstruction.getContainer());
    List<Receipt> persistedReceipts = new ArrayList<>();
    Receipt receipt = new Receipt();
    receipt.setCreateUserId("testUser");
    receipt.setQuantity(40);
    persistedReceipts.add(receipt);
    when(receiptService.createReceiptsFromUpdateInstructionRequestWithOsdrMaster(any(), any()))
        .thenReturn(persistedReceipts);
    when(containerService.constructContainerList(any(), any())).thenReturn(new Container());

    UpdateInstructionRequest updateInstructionReq = MockInstruction.getUpdateInstructionRequest();
    updateInstructionReq.getDeliveryDocumentLines().get(0).setPalletHi(1);
    updateInstructionReq.getDeliveryDocumentLines().get(0).setPalletTi(1);
    Instruction instructionResponse = new Instruction();
    final int receivedQuantity = 100;
    final String userId = "k0c0e5k";
    PoLine poLineReq = new PoLine();
    poLineReq.setReceiveQty(receivedQuantity);
    instructionPersisterService.updateInstructionAndCreateContainerAndReceipt(
        poLineReq,
        updateInstructionReq,
        MockHttpHeaders.getHeaders(),
        instruction,
        instruction.getReceivedQuantity(),
        Arrays.asList("TEST1234"));

    verify(containerService, times(1)).saveAll(any());
    verify(receiptService, times(1))
        .createReceiptsFromUpdateInstructionRequestWithOsdrMaster(
            any(UpdateInstructionRequest.class), any(HttpHeaders.class));
    verify(instructionRepository, times(1)).save(any(Instruction.class));
  }

  @Test
  public void test_updateInstructionAndCreateContainerAndReceipt_WithRejectDamages()
      throws ReceivingException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setManualInstruction(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE))
        .thenReturn(true);
    when(instructionRepository.save(instruction)).thenReturn(MockInstruction.getInstruction());
    when(containerService.createAndCompleteParentContainer(
            MockInstruction.getInstruction(), MockInstruction.getUpdateInstructionRequest(), true))
        .thenReturn(MockInstruction.getContainer());
    List<Receipt> persistedReceipts = new ArrayList<>();
    Receipt receipt = new Receipt();
    receipt.setCreateUserId("testUser");
    receipt.setQuantity(40);
    persistedReceipts.add(receipt);
    when(receiptService.createReceiptsFromUpdateInstructionRequestWithOsdrMaster(any(), any()))
        .thenReturn(persistedReceipts);
    when(containerService.constructContainerList(any(), any())).thenReturn(new Container());

    UpdateInstructionRequest updateInstructionReq = MockInstruction.getUpdateInstructionRequest();
    updateInstructionReq.getDeliveryDocumentLines().get(0).setPalletHi(10);
    updateInstructionReq.getDeliveryDocumentLines().get(0).setPalletTi(9);
    Instruction instructionResponse = new Instruction();
    final int receivedQuantity = 100;
    final String userId = "k0c0e5k";
    PoLine poLineReq = new PoLine();
    poLineReq.setReceiveQty(receivedQuantity);
    poLineReq.setRejectQty(10);
    poLineReq.setDamageQty(10);
    instructionPersisterService.updateInstructionAndCreateContainerAndReceipt(
        poLineReq,
        updateInstructionReq,
        MockHttpHeaders.getHeaders(),
        instruction,
        instruction.getReceivedQuantity(),
        Arrays.asList("TEST1234", "TEST2345"));

    verify(containerService, atLeastOnce()).saveAll(any());
    verify(receiptService, atLeastOnce())
        .createReceiptsFromUpdateInstructionRequestWithOsdrMaster(
            any(UpdateInstructionRequest.class), any(HttpHeaders.class));
    verify(receiptService, times(1)).updateDamages(any(), any(), any(), any(), any());
    verify(receiptService, times(1)).updateRejects(any(), any(), any(), any(), any());
    verify(instructionRepository, times(1)).save(any(Instruction.class));
  }

  @Test
  public void test_cancelOpenInstructionsIfAny() throws ReceivingException {
    doReturn(instructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndCompleteTsIsNull(
            anyLong(), anyString(), anyInt());

    instructionPersisterService.cancelOpenInstructionsIfAny(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndCompleteTsIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionRepository, times(1)).saveAll(any());
  }

  @Test
  public void testSaveInstructionWithInstructionCodeAsErrorForWFS() {
    Instruction instruction = MockInstruction.getCompleteInstruction();
    instruction.setInstructionCode("INITIAL_CODE");
    instructionPersisterService.saveInstructionWithInstructionCodeAsErrorForWFS(instruction);
    assertEquals("WFS_ERROR", instruction.getInstructionCode());
    verify(instructionRepository, times(1)).save(instruction);
  }

  @Test
  public void testSaveInstructionWithInstructionCodeAsErrorForWFS_NullInstruction() {
    Instruction instruction = null;
    assertThrows(
        NullPointerException.class,
        () -> {
          instructionPersisterService.saveInstructionWithInstructionCodeAsErrorForWFS(instruction);
        });
    verify(instructionRepository, times(0)).save(any());
  }

  @Test
  public void testFetchDsdcInstructionsByDeliveryNumberAndSSCC() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");

    Instruction mockInstruction4mDB = MockInstruction.getCreatedInstruction();
    mockInstruction4mDB.setCreateUserId("rxTestUser");

    doReturn(Collections.singletonList(mockInstruction4mDB))
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNotNull(anyLong(), anyString());

    instructionPersisterService.findInstructionByDeliveryNumberAndSscc(mockInstructionRequest);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNotNull(anyLong(), anyString());
  }
}
