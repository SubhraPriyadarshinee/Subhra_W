package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.client.orderfulfillment.OrderFulfillmentRestApiClient;
import com.walmart.move.nim.receiving.core.client.orderfulfillment.model.PrintShippingLabelRequest;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.item.rules.HazmatValidateRule;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionResponse;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdsResponse;
import com.walmart.move.nim.receiving.rdc.model.*;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.utils.RdcAutoReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.collections.CollectionUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.Optional;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.*;

public class RdcReceiveExceptionHandlerTest {
  @InjectMocks RdcReceiveExceptionHandler rdcReceiveExceptionHandler;

  @Mock private ProblemServiceFixit fixitPlatformService;
  @Mock private MirageRestApiClient mirageRestApiClient;
  @Mock private RdcExceptionReceivingService rdcExceptionReceivingService;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private NgrRestApiClient ngrRestApiClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcReceiveInstructionHandler rdcReceiveInstructionHandler;
  @Mock private NimRdsService nimRdsService;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcDaService rdcDaService;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private ContainerService containerService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private HazmatValidateRule hazmatValidateRule;
  @Mock private RdcItemServiceHandler rdcItemServiceHandler;
  @Mock private RdcAutoReceiveService rdcAutoReceiveService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private LabelDataService labelDataService;
  @Mock private RdcUtils rdcUtils;
  @Mock private InventoryRestApiClient inventoryRestApiClient;
  @Mock private OrderFulfillmentRestApiClient orderFulfillmentRestApiClient;
  @Mock private RdcAutoReceivingUtils rdcAutoReceivingUtils;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private AppConfig appConfig;
  @Mock private RdcContainerService rdcContainerService;

  private Gson gson;

  @BeforeClass
  public void init() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
    ReflectionTestUtils.setField(rdcReceiveExceptionHandler, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        mirageRestApiClient,
        tenantSpecificConfigReader,
        rdcExceptionReceivingService,
        rdcReceiveInstructionHandler,
        rdcInstructionUtils,
        containerService,
        rdcAutoReceiveService,
        labelDataService,
        rdcUtils,
        inventoryRestApiClient,
        orderFulfillmentRestApiClient);
  }

  @DataProvider(name = "BlockedExceptions")
  public static Object[][] blockedExceptions() {
    return new Object[][] {
      {"X_BLOCK"},
      {"NONCON"},
      {"DSDC_AUDIT_LABEL"},
      {"INVALID_REQUEST"},
      {"NO_DATA_ASSOCIATED"},
      {"SYSTEM_ERROR"},
      {"SSTK_ATLAS_ITEM"},
      {"SSTK"},
      {"RCV_ERROR"}
    };
  }

  @Test
  public void testReceiveException_isSuccessForLabelTypeStore()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    Instruction instruction = buildMockInstruction(EXCEPTION_LPN_RECEIVED);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Store_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(instruction).when(rdcExceptionReceivingService).buildInstruction(anyString());
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.EXCEPTION_LPN_RECEIVED.getInstructionCode());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(inventoryRestApiClient, times(1))
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_isSuccessForLabelTypeStore_AtlasParityEnabled()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    Instruction instruction = buildMockInstruction(EXCEPTION_LPN_RECEIVED);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Store_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(IS_ATLAS_PARITY_EXCEPTION_RECEIVING_ENABLED), anyBoolean());
    doReturn(instruction).when(rdcExceptionReceivingService).buildInstruction(anyString());
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.EXCEPTION_LPN_RECEIVED.getInstructionCode());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(inventoryRestApiClient, times(0))
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(IS_ATLAS_PARITY_EXCEPTION_RECEIVING_ENABLED), anyBoolean());
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_isSuccessForLabelTypePut_Con()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    Instruction instruction = buildMockInstruction(EXCEPTION_LPN_RECEIVED);
    doReturn(instruction).when(rdcExceptionReceivingService).buildInstruction(anyString());
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Put_Con_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.EXCEPTION_LPN_RECEIVED.getInstructionCode());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_isSuccessForLabelTypePut_Noncon()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    Instruction instruction = buildMockInstruction(EXCEPTION_LPN_RECEIVED);
    doReturn(instruction).when(rdcExceptionReceivingService).buildInstruction(anyString());
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(getMockLpnResponse_Put_Noncon_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.EXCEPTION_LPN_RECEIVED.getInstructionCode());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    assertNotNull(instructionResponse);
  }

  @Test(dataProvider = "BlockedExceptions")
  public void testReceiveException_isSuccessForBlockedExceptions(String exceptionMessage)
      throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(exceptionMessage);
    InstructionResponse mockinstructionResponse =
        buildMockInstructionResponse(receiveExceptionRequest.getExceptionMessage(), null);
    doReturn(mockinstructionResponse)
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(anyString(), isNull());
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcReceiveExceptionHandler.receiveException(
                receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        EXCEPTION_INSTRUCTION_TYPE_MAP
            .get(receiveExceptionRequest.getExceptionMessage())
            .getInstructionCode());
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getDescription(),
        RdcExceptionMsg.valueOf(receiveExceptionRequest.getExceptionMessage()).getDescription());
    verify(rdcExceptionReceivingService, times(1)).buildInstructionResponse(anyString(), isNull());
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_isSuccessForLabelTypeDsdc() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("");
    Instruction instruction = buildMockInstruction(EXCEPTION_LPN_RECEIVED);
    doReturn(instruction).when(rdcExceptionReceivingService).buildInstruction(anyString());
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(getMockLpnResponse_Dsdc_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_isSuccessForAtlasLabel() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("");
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Dsdc_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    Container container = buildMockContainer();
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    Map<String, String> destination = new HashMap<>();
    destination.put(SLOT, DA_R8000_SLOT);
    container.setDestination(destination);
    doReturn(container).when(containerService).findByTrackingId(anyString());
    doReturn(getMockPrintJob())
        .when(rdcContainerService)
        .getContainerLabelsByTrackingIds(anyList(), any(HttpHeaders.class));
    Instruction instruction = getMockInstructionContainerDetails();
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(anyString());
    doNothing()
        .when(inventoryRestApiClient)
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcReceiveExceptionHandler.receiveException(
                receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(inventoryRestApiClient, times(1))
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getIsAtlasReceivedContainer());
  }

  @Test
  public void testReceiveException_isSuccessForAtlasLabel_ShippingLabel()
      throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("");
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Dsdc_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(buildMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(getMockPrintJob())
        .when(rdcContainerService)
        .getContainerLabelsByTrackingIds(anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(anyString());
    doNothing()
        .when(inventoryRestApiClient)
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcReceiveExceptionHandler.receiveException(
                receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(inventoryRestApiClient, times(1))
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    assertNotNull(instructionResponse);
    assertFalse(instructionResponse.getIsAtlasReceivedContainer());
  }

  private Instruction getMockInstructionContainerDetails() {
    Instruction instruction = buildInstructionContainerDetails();
    ContainerDetails containerDetails = new ContainerDetails();
    Map<String, Object> printJob = new HashMap<>();
    List<Map<String, Object>> printRequests = new ArrayList<>();
    Map<String, Object> printRequest = new HashMap<>();
    List<Map<String, Object>> labelData = new ArrayList<>();
    printRequest.put("data", labelData);
    printRequests.add(printRequest);
    printJob.put("printRequests", printRequests);
    containerDetails.setCtrLabel(printJob);
    instruction.setContainer(containerDetails);
    return instruction;
  }

  private Container buildMockContainer() {
    Container container = new Container();
    container.setInstructionId(123L);
    container.setTrackingId("a602042323232323");
    ContainerItem containerItem = new ContainerItem();
    containerItem.setAsrsAlignment("SYM2_5");
    containerItem.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container buildNullMockContainer() {
    return null;
  }

  private Instruction buildInstructionContainerDetails() {
    return new Instruction();
  }

  @Test
  public void testReceiveException_ThrowsExceptionError_LabelMatch() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    InstructionResponseImplException instructionResponseImplException =
        new InstructionResponseImplException();
    ExceptionInstructionMsg exceptionInstructionMsg = new ExceptionInstructionMsg();
    exceptionInstructionMsg.setInfo(RdcExceptionMsg.MATCH_FOUND.getInfo());
    exceptionInstructionMsg.setDescription(RdcExceptionMsg.MATCH_FOUND.getDescription());
    instructionResponseImplException.setExceptionInstructionMsg(exceptionInstructionMsg);
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(
        EXCEPTION_INSTRUCTION_TYPE_MAP.get("MATCH_FOUND").getInstructionCode());
    instructionResponseImplException.setInstruction(instruction);
    String body =
        "{\n"
            + "    \"title\": \"Match Found\",\n"
            + "    \"message\": \"Place case onto conveyor with label\",\n"
            + "    \"code\": \"MATCH_FOUND\"\n"
            + "}";
    JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
    doThrow(
            new RestClientResponseException(
                String.valueOf(jsonObject.get("message")),
                404,
                null,
                null,
                jsonObject.toString().getBytes(StandardCharsets.UTF_8),
                null))
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(instructionResponseImplException)
        .when(rdcExceptionReceivingService)
        .parseMirageExceptionErrorResponse(
            any(ReceiveExceptionRequest.class), any(MirageLpnExceptionErrorResponse.class));
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcReceiveExceptionHandler.receiveException(
                receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(rdcExceptionReceivingService, times(1))
        .parseMirageExceptionErrorResponse(
            any(ReceiveExceptionRequest.class), any(MirageLpnExceptionErrorResponse.class));
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_ThrowsExceptionError_LpnNotFound()
      throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    InstructionResponseImplException instructionResponseImplException =
        new InstructionResponseImplException();
    ExceptionInstructionMsg exceptionInstructionMsg = new ExceptionInstructionMsg();
    exceptionInstructionMsg.setInfo(RdcExceptionMsg.ERROR_LPN_BACKOUT.getInfo());
    exceptionInstructionMsg.setDescription(RdcExceptionMsg.ERROR_LPN_BACKOUT.getInfo());
    instructionResponseImplException.setExceptionInstructionMsg(exceptionInstructionMsg);
    Instruction instruction = new Instruction();
    instruction.setInstructionCode("offlineLabelValidated");
    instructionResponseImplException.setInstruction(instruction);
    String body =
        "{\n"
            + "    \"title\": \"Label backed-out\",\n"
            + "    \"message\": \"Remove Label then place case onto conveyor without Label\",\n"
            + "    \"code\": \"ERROR_LPN_BACKOUT\"\n"
            + "}";
    JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
    doThrow(
            new RestClientResponseException(
                String.valueOf(jsonObject.get("message")),
                409,
                null,
                null,
                jsonObject.toString().getBytes(StandardCharsets.UTF_8),
                null))
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(instructionResponseImplException)
        .when(rdcExceptionReceivingService)
        .parseMirageExceptionErrorResponse(
            any(ReceiveExceptionRequest.class), any(MirageLpnExceptionErrorResponse.class));
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcReceiveExceptionHandler.receiveException(
                receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(rdcExceptionReceivingService, times(1))
        .parseMirageExceptionErrorResponse(
            any(ReceiveExceptionRequest.class), any(MirageLpnExceptionErrorResponse.class));
    assertNotNull(instructionResponse);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveException_ThrowsReceivingInternalException() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doThrow(new ReceivingInternalException("Mock_error", "Mock_error"))
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveException_ThrowsReceivingBadDataException() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest(null);
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doThrow(new ReceivingBadDataException("mock_error", "mock_error"))
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
  }

  @Test
  public void testReceiveException_lpnNotReceived_lpnAvailableInLabelData()
      throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest =
        ReceiveExceptionRequest.builder()
            .lpns(Collections.singletonList("1234567890123456789012345"))
            .build();
    doReturn(new InstructionResponseImplException())
        .when(rdcExceptionReceivingService)
        .processExceptionLabel(anyString());
    doReturn(null).when(containerService).findByTrackingId(anyString());
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);
    instructionResponse.setInstruction(new Instruction());
    LabelData labelData =
        LabelData.builder()
            .lpns("1234567890")
            .deliveryNumber(35453453L)
            .itemNumber(34535456L)
            .label(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType())
            .status(LabelInstructionStatus.AVAILABLE.toString())
            .build();
    doReturn(labelData).when(labelDataService).findByTrackingIdAndStatus(anyString(), anyString());
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(instructionResponse)
        .when(rdcAutoReceiveService)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(rdcExceptionReceivingService, times(1)).processExceptionLabel(anyString());
    verify(labelDataService, times(1)).findByTrackingIdAndStatus(anyString(), anyString());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcAutoReceiveService, times(1))
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_lpnNotReceived_SSTKLabelData()
      throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest =
        ReceiveExceptionRequest.builder()
            .lpns(Collections.singletonList("1234567890123456789012345"))
            .build();
    doReturn(new InstructionResponseImplException())
        .when(rdcExceptionReceivingService)
        .processExceptionLabel(anyString());
    doReturn(null).when(containerService).findByTrackingId(anyString());
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    InstructionResponse instructionResponse =
        buildMockInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    instructionResponse.setInstruction(new Instruction());
    LabelData labelData =
        LabelData.builder()
            .lpns("1234567890")
            .deliveryNumber(35453453L)
            .itemNumber(34535456L)
            .label(STAPLE_STOCK_LABEL)
            .status(LabelInstructionStatus.AVAILABLE.toString())
            .build();
    doReturn(labelData).when(labelDataService).findByTrackingIdAndStatus(anyString(), anyString());
    doReturn(instructionResponse)
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(anyString(), isNull());
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(rdcExceptionReceivingService, times(1)).processExceptionLabel(anyString());
    verify(labelDataService, times(1)).findByTrackingIdAndStatus(anyString(), anyString());
    verify(rdcExceptionReceivingService, times(1)).buildInstructionResponse(anyString(), isNull());
  }

  @Test
  public void testReceiveException_lpnNotFoundOrNotInAvailableStatusInLabelData()
      throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest =
        ReceiveExceptionRequest.builder()
            .lpns(Collections.singletonList("1234567890123456789012345"))
            .build();
    doReturn(new InstructionResponseImplException())
        .when(rdcExceptionReceivingService)
        .processExceptionLabel(anyString());
    doReturn(null).when(containerService).findByTrackingId(anyString());
    doReturn(null).when(labelDataService).findByTrackingIdAndStatus(anyString(), anyString());
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockMirageExceptionResponse())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(anyString());
    doNothing()
        .when(inventoryRestApiClient)
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(rdcExceptionReceivingService, times(1)).processExceptionLabel(anyString());
    verify(labelDataService, times(1)).findByTrackingIdAndStatus(anyString(), anyString());
    verify(rdcExceptionReceivingService, times(1))
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    verify(mirageRestApiClient, times(1)).processException(any(MirageExceptionRequest.class));
    verify(rdcExceptionReceivingService, times(1)).buildInstruction(anyString());
    verify(inventoryRestApiClient, times(1))
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_18digitlpn_notReceived() throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest =
        ReceiveExceptionRequest.builder()
            .lpns(Collections.singletonList("123456789012345678"))
            .build();
    doReturn(null).when(containerService).findByTrackingId(anyString());
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockMirageExceptionResponse())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(anyString());
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(rdcExceptionReceivingService, times(0)).processExceptionLabel(anyString());
    verify(labelDataService, times(0)).findByTrackingIdAndStatus(anyString(), anyString());
    verify(rdcExceptionReceivingService, times(1))
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    verify(mirageRestApiClient, times(1)).processException(any(MirageExceptionRequest.class));
    verify(rdcExceptionReceivingService, times(1)).buildInstruction(anyString());
  }

  @Test
  public void testReceiveException_offlineLabel() throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest =
        ReceiveExceptionRequest.builder()
            .lpns(Collections.singletonList("1234567890123456789012345"))
            .build();
    LabelData labelData =
        LabelData.builder()
            .lpns("1234567890")
            .deliveryNumber(35453453L)
            .itemNumber(34535456L)
            .status(LabelInstructionStatus.AVAILABLE.toString())
            .label(InventoryLabelType.XDK1.name())
            .build();
    InstructionResponse instructionResponse = new InstructionResponseImplException();
    instructionResponse.setInstruction(buildMockInstruction(MATCH_FOUND));
    doReturn(instructionResponse)
        .when(rdcExceptionReceivingService)
        .processExceptionLabel(anyString());
    InstructionResponse actualInstructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(rdcExceptionReceivingService, times(1)).processExceptionLabel(anyString());
    assertNotNull(actualInstructionResponse);
    assertEquals(actualInstructionResponse.getInstruction(), buildMockInstruction(MATCH_FOUND));
  }

  @Test
  public void testReceiveException_FlibInEligible() throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("NOT ELIGIBLE");
    receiveExceptionRequest.setLpns(Collections.singletonList("1234567890123456789012345"));
    doReturn(null).when(containerService).findByTrackingId(anyString());
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);
    instructionResponse.setInstruction(new Instruction());
    LabelData labelData =
        LabelData.builder()
            .lpns("1234567890")
            .deliveryNumber(35453453L)
            .itemNumber(34535456L)
            .label(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType())
            .status(LabelInstructionStatus.AVAILABLE.toString())
            .build();
    doReturn(labelData).when(labelDataService).findByTrackingIdAndStatus(anyString(), anyString());
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(instructionResponse)
        .when(rdcAutoReceiveService)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(labelDataService, times(1)).findByTrackingIdAndStatus(anyString(), anyString());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcAutoReceiveService, times(1))
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_FlibInEligible_Legacy() throws ReceivingException, IOException {
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("NOT ELIGIBLE");
    receiveExceptionRequest.setLpns(Collections.singletonList("1234567890123456789012345"));
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    doReturn(null).when(containerService).findByTrackingId(anyString());
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(false);
    when(rdcManagedConfig.getFlibInEligibleExceptions())
        .thenReturn(Arrays.asList("DIMENSION_INVALID", "NOT_ELIGIBLE"));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Store_NotReceived())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(instructionResponse)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doNothing()
        .when(mirageRestApiClient)
        .voidLPN(any(VoidLPNRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcReceiveInstructionHandler, times(1))
        .receiveInstruction(
            any(ReceiveInstructionRequest.class), httpHeadersArgumentCaptor.capture());
    assertNull(httpHeadersArgumentCaptor.getValue().getFirst(IS_REINDUCT_ROUTING_LABEL));
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_Breakout_isBreakpackConveyPicks()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("BREAKOUT");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    InstructionResponse instructionResponse =
        buildMockInstructionResponse(
            receiveExceptionRequest.getExceptionMessage(), mockDeliveryDocuments);
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    doReturn(instructionResponse)
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(anyString(), anyList());
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.BREAKOUT.getInstructionCode());
  }

  @Test
  public void testReceiveException_Hazmat_False() throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("HAZMAT");

    InstructionResponse instructionResponse = new InstructionResponseImplException();
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    InstructionResponse instructionResponse1 = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    instructionResponse1.setInstruction(instruction);

    doReturn(instructionResponse1)
        .when(rdcAutoReceiveService)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    when(hazmatValidateRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    InstructionResponse instructionResponse2 =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse2);
    assertNotNull(instructionResponse2.getInstruction());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse2.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse2.getInstruction().getInstructionMsg());
  }

  @Test
  public void testReceiveException_Hazmat_True() throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setIsHazmat(Boolean.TRUE);

    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("HAZMAT");

    InstructionResponse instructionResponse = new InstructionResponseImplException();
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    InstructionResponse instructionResponse1 = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    instructionResponse1.setInstruction(instruction);

    doReturn(instructionResponse1)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    when(hazmatValidateRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(true);
    InstructionResponse instructionResponse2 =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse2);
    assertNull(instructionResponse2.getInstruction());
    assertNotNull(instructionResponse2.getDeliveryDocuments());
    assertEquals(instructionResponse2.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse2.getDeliveryDocuments().get(0).getDeliveryDocumentLines());
    assertEquals(
        instructionResponse2.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);
    assertTrue(
        instructionResponse2
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
  }

  @Test
  public void testReceiveException_isFailureForLimitedLithium()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("LITHIUM");
    receiveExceptionRequest.setRegulatedItemType(null);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLithiumIonVerificationRequired(Boolean.TRUE);

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, mockDeliveryDocuments);

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    InstructionResponse instructionResponse1 = new InstructionResponseImplNew();
    doReturn(instructionResponse1)
        .when(rdcReceivingUtils)
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class), any(), any(InstructionResponse.class));

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);
    Instruction instruction = new Instruction();
    instructionResponse.setInstruction(instruction);
    doReturn(instructionResponse)
        .when(rdcAutoReceiveService)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse2 =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instructionResponse1);
    assertNotNull(instructionResponse2);
    assertNotNull(instructionResponse2.getInstruction());
    assertNotNull(instructionResponse2.getDeliveryDocuments());
    assertEquals(instructionResponse2.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse2.getInstruction().getInstructionCode());
    assertNotNull(instructionResponse2.getInstruction().getInstructionMsg());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
  }

  @Test
  public void testReceiveException_isSuccessForLimitedLithium()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("LITHIUM");
    receiveExceptionRequest.setRegulatedItemType(null);

    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLithiumIonVerificationRequired(Boolean.TRUE);

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, mockDeliveryDocuments);

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    InstructionResponse mockInstructionResponse = new InstructionResponseImplException();
    mockInstructionResponse.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(mockInstructionResponse)
        .when(rdcReceivingUtils)
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class), any(), any(InstructionResponse.class));

    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);
  }

  @Test
  public void testReceiveException_Breakout_ReceiveInstruction()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("BREAKOUT");
    receiveExceptionRequest.setRegulatedItemType(null);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    receiveExceptionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(instructionResponse)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    InstructionRequest instructionRequest = new InstructionRequest();

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcReceiveInstructionHandler, times(1))
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_hasSSTKDeliveryDocuments()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("BREAKOUT");
    receiveExceptionRequest.setRegulatedItemType(null);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(Collections.emptyList())
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    InstructionResponse mockInstructionResponse =
        buildMockInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    doReturn(mockInstructionResponse)
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(anyString(), any());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        RdcInstructionType.LPN_NOT_RECEIVED_SSTK.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.LPN_NOT_RECEIVED_SSTK.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_isSuccess_ReceiveInstruction()
      throws IOException, ReceivingException {
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("RCV_LATENCY");
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(false);
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Arrays.asList("CI", "CJ", "CC"));
    when(rdcManagedConfig.getFlibInEligibleExceptions())
        .thenReturn(Arrays.asList("DIMENSION_INVALID", "NOT_ELIGIBLE"));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    instructionResponse.setDeliveryDocuments(mockDeliveryDocuments);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Store_NotReceived())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(instructionResponse)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(
            any(ReceiveInstructionRequest.class), httpHeadersArgumentCaptor.capture());
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doNothing()
        .when(mirageRestApiClient)
        .voidLPN(any(VoidLPNRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcReceiveInstructionHandler, times(1))
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    assertTrue(
        Boolean.parseBoolean(
            httpHeadersArgumentCaptor.getValue().getFirst(IS_REINDUCT_ROUTING_LABEL)));
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_NO_BARCODE_SEEN_returnsDeliveryDocumentsMultipleDelivery()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMulipleDADifferentDelivery();
    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, null);
    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), eq(null));
    instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instructionResponse.getDeliveryDocuments());

    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
  }

  @Test
  public void testReceiveException_NO_BARCODE_SEEN_returnsDeliveryDocumentsMultipleItems()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).hasMoreUniqueItems(anyList());
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, null);
    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), eq(null));

    instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());

    assertNotNull(instructionResponse.getDeliveryDocuments());

    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
  }

  @Test
  public void testReceiveException_NO_BARCODE_SEEN_receiveInstruction()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(Boolean.FALSE).when(rdcInstructionUtils).hasMoreUniqueItems(anyList());
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, null);
    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), eq(null));

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    doReturn(instructionResponse)
        .when(rdcAutoReceiveService)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcAutoReceiveService, times(1))
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_NO_BARCODE_SEEN_returnDeliveryDocumentsForCatalog()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    receiveExceptionRequest.setIsCatalogRequired(Boolean.TRUE);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
        new Pair<>(mockDeliveryDocuments.get(0), 5L);
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(autoSelectedDeliveryDocument)
        .when(rdcInstructionUtils)
        .autoSelectDocumentAndDocumentLine(
            anyList(), anyInt(), anyString(), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), eq(null));
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1))
        .autoSelectDocumentAndDocumentLine(
            anyList(), anyInt(), anyString(), any(HttpHeaders.class));
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_NO_BARCODE_SEEN_hasSSTKDeliveryDocuments()
      throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    doReturn(Collections.emptyList())
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    when(rdcExceptionReceivingService.getInstructionRequest(
            any(ReceiveExceptionRequest.class), isNull()))
        .thenReturn(new InstructionRequest());
    InstructionResponse mockInstructionResponse =
        buildMockInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    doReturn(mockInstructionResponse)
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(anyString(), any());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        RdcInstructionType.LPN_NOT_RECEIVED_SSTK.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.LPN_NOT_RECEIVED_SSTK.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_NO_BARCODE_SEEN_throwsExceptionInvalidRequest()
      throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("NO_BARCODE_SEEN");
    try {
      InstructionResponse instructionResponse =
          rdcReceiveExceptionHandler.receiveException(
              receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_RECEIVE_EXCEPTION_REQUEST);
      assertEquals(e.getMessage(), ReceivingConstants.INVALID_UPC);
    }
  }

  @Test
  public void testReceiveException_Breakout_isMasterPackOrBreakPack_ReceiveInstruction()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("BREAKOUT");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    doReturn(instructionResponse)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcReceiveInstructionHandler, times(1))
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_isSuccess_PreLabeled_ReceiveInstruction()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForPreLabeled();
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 5L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    doReturn(getMockLpnResponse_Store_NotReceived())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(instructionResponse)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcReceiveInstructionHandler, times(1))
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveException_preLabeled_AtlasBackedOutItem() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForPreLabeled();
    receiveExceptionRequest.setLpns(Collections.singletonList("123456789012345678"));
    Container container = new Container();
    container.setContainerStatus("backout");
    when(containerService.findByTrackingId(anyString())).thenReturn(container);
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(ERROR_LPN_BACKOUT, null);
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(ERROR_LPN_BACKOUT);
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(containerService, times(1)).findByTrackingId(anyString());
    verify(rdcExceptionReceivingService, times(1))
        .buildInstructionResponse(ERROR_LPN_BACKOUT, null);
  }

  @Test
  public void testReceiveException_preLabeled_SSTKReceived() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForPreLabeled();
    receiveExceptionRequest.setLpns(Collections.singletonList("123456789012345678"));
    Container container = new Container();
    container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    when(containerService.findByTrackingId(anyString())).thenReturn(container);
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(LPN_RECEIVED_SSTK, null);
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(LPN_RECEIVED_SSTK);
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(containerService, times(1)).findByTrackingId(anyString());
    verify(rdcExceptionReceivingService, times(1))
        .buildInstructionResponse(LPN_RECEIVED_SSTK, null);
  }

  @Test
  public void testReceiveException_preLabeled_AtlasBackedOutItem_ValidateLabel()
      throws ReceivingException, IOException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForPreLabeled();
    receiveExceptionRequest.setLpns(Collections.singletonList("058440106043008723"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(IS_ATLAS_EXCEPTION_RECEIVING), anyBoolean()))
        .thenReturn(true);
    InstructionResponse instructionResponse = MockInstructionResponse.getMockInstructionResponse();
    when(rdcExceptionReceivingService.processExceptionLabel(anyString()))
        .thenReturn(instructionResponse);
    rdcReceiveExceptionHandler.receiveException(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(containerService, times(1)).findByTrackingId(anyString());
    verify(rdcExceptionReceivingService, times(1))
        .processExceptionLabel(receiveExceptionRequest.getLpns().get(0));
  }

  @Test
  public void testReceiveException_maxOverageReceived_hasSSTKDocuments() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    doReturn(Collections.emptyList())
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    InstructionResponse mockInstructionResponse =
        buildMockInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    doReturn(mockInstructionResponse)
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(anyString(), any());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveException(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        RdcInstructionType.LPN_NOT_RECEIVED_SSTK.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.LPN_NOT_RECEIVED_SSTK.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_maxOverageReceived_sendsInstructionMessage_lpnOverage()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);

    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    try {
      InstructionResponse instructionResponse =
          rdcReceiveExceptionHandler.receiveException(
              receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
      assertEquals(receivingBadDataException.getDescription(), ReceivingException.OVERAGE_ERROR);
    }
  }

  @Test
  public void
      testReceiveException_maxOverageReceived_sendsInstructionMessage_lpnOverage_feature_flag_false()
          throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);

    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));

    try {
      rdcReceiveExceptionHandler.receiveException(
          receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
      assertEquals(receivingBadDataException.getDescription(), ReceivingException.OVERAGE_ERROR);
    }
  }

  @Test
  public void testReceiveException_maxOverageReceived_sendsInstructionMessage_not_lpnOverage()
      throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("LITHIUM_ION");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    ReceiveInstructionRequest rdcInstructionRequest = new ReceiveInstructionRequest();
    rdcInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    doReturn(rdcInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(), any());
    doReturn(getMockLpnResponse_Store_Received())
        .when(mirageRestApiClient)
        .processException(any(MirageExceptionRequest.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doReturn(aclExceptionRequest)
        .when(rdcExceptionReceivingService)
        .getMirageExceptionRequest(any(ReceiveExceptionRequest.class));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(buildNullMockContainer()).when(containerService).findByTrackingId(anyString());
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    instructionResponse.setInstruction(instruction);
    doReturn(instructionResponse)
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);
    instructionResponse =
        (InstructionResponseImplNew)
            rdcReceiveExceptionHandler.receiveException(
                receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode());
    verify(mirageRestApiClient, times(1)).processException(aclExceptionRequest);
    assertNotNull(instructionResponse);
  }

  @Test
  public void
      testReceiveException_maxOverageReceived_sendsInstructionMessage_lpnOverage_feature_flag_true_exception_msg_overage()
          throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);

    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));

    try {
      rdcReceiveExceptionHandler.receiveException(
          receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
      assertEquals(receivingBadDataException.getDescription(), ReceivingException.OVERAGE_ERROR);
    }
  }

  @Test
  public void
      testReceiveException_maxOverageReceived_sendsInstructionMessage_lpnOverage_exception_error_code_not_overage()
          throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);

    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG,
                ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));

    try {
      rdcReceiveExceptionHandler.receiveException(
          receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          receivingBadDataException.getErrorCode(), ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG);
      assertEquals(
          receivingBadDataException.getDescription(),
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG);
    }
  }

  @Test
  public void testReceiveException_throws_receivingBadDataException()
      throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("LITHIUM_ION");
    receiveExceptionRequest.setUpcNumber("65675787");
    receiveExceptionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcItemServiceHandler)
        .updateItemRejectReason(
            nullable(RejectReason.class), any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());
    doThrow(new ReceivingBadDataException("mock error", "mock error"))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
    try {
      rdcReceiveExceptionHandler.receiveException(
          receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "mock error");
      assertEquals(e.getDescription(), "mock error");
      verify(tenantSpecificConfigReader, times(0))
          .getConfiguredFeatureFlag(
              anyString(), eq(IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE), anyBoolean());
    }
  }

  private ReceiveExceptionRequest getReceiveExceptionRequest(String exceptionMessage) {
    return ReceiveExceptionRequest.builder()
        .exceptionMessage(exceptionMessage)
        .regulatedItemType(VendorCompliance.LIMITED_QTY.name())
        .receiver("123456")
        .lpns(Collections.singletonList("1234567890"))
        .itemNumber(550000000)
        .slot("R8000")
        .deliveryNumbers(Collections.singletonList("345123"))
        .tokenId("12345")
        .build();
  }

  @Test
  public void testGetHistoryDeliveriesFromHawkeye_Success() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    Optional<List<String>> mockDeliveryNumbers =
        Optional.of(Arrays.asList("deliveryNumber1", "deliveryNumber2"));
    doReturn(mockDeliveryNumbers)
        .when(hawkeyeRestApiClient)
        .getHistoryDeliveriesFromHawkeye(any(DeliverySearchRequest.class), any(HttpHeaders.class));
    List<String> deliveryNumbers =
        rdcReceiveExceptionHandler.getHistoryDeliveriesFromHawkeye(
            deliverySearchRequest, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryNumbers);
    assertEquals(deliveryNumbers.size(), mockDeliveryNumbers.get().size());
    assertEquals(deliveryNumbers.get(0), mockDeliveryNumbers.get().get(0));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetHistoryDeliveriesFromHawkeye_Exception() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    doReturn(Optional.empty())
        .when(hawkeyeRestApiClient)
        .getHistoryDeliveriesFromHawkeye(any(DeliverySearchRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.getHistoryDeliveriesFromHawkeye(
        deliverySearchRequest, MockHttpHeaders.getHeaders());
    verify(rdcReceiveExceptionHandler, times(1))
        .getHistoryDeliveriesFromHawkeye(any(DeliverySearchRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetHistoryDeliveriesFromHawkeye_EmptyList_Exception() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    doReturn(Optional.of(Collections.EMPTY_LIST))
        .when(hawkeyeRestApiClient)
        .getHistoryDeliveriesFromHawkeye(any(DeliverySearchRequest.class), any(HttpHeaders.class));
    rdcReceiveExceptionHandler.getHistoryDeliveriesFromHawkeye(
        deliverySearchRequest, MockHttpHeaders.getHeaders());
    verify(rdcReceiveExceptionHandler, times(1))
        .getHistoryDeliveriesFromHawkeye(any(DeliverySearchRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testGetDeliveryDocumentsForDeliverySearch() throws IOException {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setOpenQty(10);
    List<String> deliveryNumbers = Arrays.asList("60032433");
    doReturn(Optional.of(deliveryNumbers))
        .when(hawkeyeRestApiClient)
        .getHistoryDeliveriesFromHawkeye(any(DeliverySearchRequest.class), any(HttpHeaders.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            anyList(), any(InstructionRequest.class), any(HttpHeaders.class));
    List<DeliveryDocument> deliveryDocumentsList = new ArrayList<>();
    Pair<DeliveryDocument, Long> deliveryDocumentLongPair =
        new Pair<>(mockDeliveryDocuments.get(0), 1L);
    deliveryDocumentsList.add(deliveryDocumentLongPair.getKey());
    doReturn(deliveryDocumentLongPair)
        .when(rdcInstructionUtils)
        .autoSelectDocumentAndDocumentLine(
            anyList(), anyInt(), anyString(), any(HttpHeaders.class));
    List<DeliveryDocument> deliveryDocuments =
        rdcReceiveExceptionHandler.getDeliveryDocumentsForDeliverySearch(
            deliverySearchRequest, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryDocumentsList);
    assertEquals(deliveryDocumentsList.size(), mockDeliveryDocuments.size());
    assertEquals(deliveryDocuments.get(0), mockDeliveryDocuments.get(0));
  }

  @Test
  public void testGetInstructionRequest() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    InstructionRequest instructionRequest =
        rdcReceiveExceptionHandler.getInstructionRequest(deliverySearchRequest);
    assertNotNull(instructionRequest);
    assertEquals(instructionRequest.getMessageId(), deliverySearchRequest.getMessageId());
    assertEquals(instructionRequest.getDoorNumber(), deliverySearchRequest.getDoorNumber());
    assertEquals(instructionRequest.getUpcNumber(), deliverySearchRequest.getUpc());
    assertEquals(instructionRequest.getReceivingType(), ReceivingConstants.UPC);
  }

  private MirageExceptionResponse getMockMirageExceptionResponse() throws IOException {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(true);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setStoreInfo(
        MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived().get(0));
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setSlot("P1001");
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setZone("12");
    mirageExceptionResponse.setVendorPack(4);
    mirageExceptionResponse.setWarehousePack(2);
    mirageExceptionResponse.setRdsHandlingCode("C");
    mirageExceptionResponse.setRdsPackTypeCode("C");
    return mirageExceptionResponse;
  }

  public void testVoidLPNsOnExceptionReceiving() throws IOException {
    InstructionResponse instructionResponse = MockInstructionResponse.getMockInstructionResponse();
    Mockito.doNothing()
        .when(mirageRestApiClient)
        .voidLPN(any(VoidLPNRequest.class), any(HttpHeaders.class));
    ReflectionTestUtils.invokeMethod(
        rdcReceiveExceptionHandler,
        "voidLPNsOnExceptionReceiving",
        instructionResponse,
        MockHttpHeaders.getHeaders());
    verify(mirageRestApiClient, times(1))
        .voidLPN(any(VoidLPNRequest.class), any(HttpHeaders.class));
  }

  public InstructionRequest getMockInstructionRequest(
      ReceiveExceptionRequest receiveExceptionRequest, List<DeliveryDocument> deliveryDocuments) {
    InstructionRequest instructionRequest = new InstructionRequest();
    if (!CollectionUtils.isEmpty(deliveryDocuments)) {
      instructionRequest.setDeliveryStatus(deliveryDocuments.get(0).getDeliveryStatus().toString());
      instructionRequest.setDeliveryNumber(
          String.valueOf(deliveryDocuments.get(0).getDeliveryNumber()));
      instructionRequest.setVendorComplianceValidated(
          receiveExceptionRequest.isVendorComplianceValidated());
      instructionRequest.setDeliveryDocuments(deliveryDocuments);
    }
    instructionRequest.setMessageId(receiveExceptionRequest.getMessageId());
    instructionRequest.setDoorNumber(receiveExceptionRequest.getDoorNumber());
    if (Objects.nonNull(receiveExceptionRequest.getUpcNumber())) {
      instructionRequest.setUpcNumber(receiveExceptionRequest.getUpcNumber());
      instructionRequest.setReceivingType(UPC);
    }
    return instructionRequest;
  }

  private MirageExceptionRequest getACLExceptionRequest(
      ReceiveExceptionRequest receiveExceptionRequest) {
    MirageExceptionRequest aclExceptionRequest = new MirageExceptionRequest();
    aclExceptionRequest.setAclErrorString(receiveExceptionRequest.getExceptionMessage());
    aclExceptionRequest.setLpn(receiveExceptionRequest.getLpns().get(0));
    aclExceptionRequest.setItemNbr(String.valueOf(receiveExceptionRequest.getItemNumber()));
    aclExceptionRequest.setPrinterNbr(receiveExceptionRequest.getPrinterNumber());
    aclExceptionRequest.setGroupNbr(receiveExceptionRequest.getDeliveryNumbers());
    aclExceptionRequest.setTokenId(receiveExceptionRequest.getTokenId());
    return aclExceptionRequest;
  }

  private MirageExceptionResponse getMockLpnResponse_Store_Received() throws IOException {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(true);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setStoreInfo(
        MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived().get(0));
    return mirageExceptionResponse;
  }

  private MirageExceptionResponse getMockLpnResponse_Store_NotReceived() throws IOException {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(false);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setReceiveRequest(new MirageLabelReceiveRequest());
    return mirageExceptionResponse;
  }

  private MirageExceptionResponse getMockLpnResponse_Put_Noncon_Received() throws IOException {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(true);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setStoreInfo(
        MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived().get(0));
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setSlot("V0050");
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setZone("12");
    mirageExceptionResponse.setVendorPack(4);
    mirageExceptionResponse.setWarehousePack(2);
    mirageExceptionResponse.setRdsHandlingCode("B");
    mirageExceptionResponse.setRdsPackTypeCode("M");
    return mirageExceptionResponse;
  }

  private MirageExceptionResponse getMockLpnResponse_Put_Con_Received() throws IOException {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(true);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setStoreInfo(
        MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived().get(0));
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setSlot("P1001");
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setZone("12");
    mirageExceptionResponse.setVendorPack(4);
    mirageExceptionResponse.setWarehousePack(2);
    mirageExceptionResponse.setRdsHandlingCode("C");
    mirageExceptionResponse.setRdsPackTypeCode("C");
    return mirageExceptionResponse;
  }

  private MirageExceptionResponse getMockLpnResponse_Dsdc_Received() {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(true);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setPackinfo(getMockDsdcReceiveResponseSuccess());
    mirageExceptionResponse.setLabelDate("2023-02-14T14:43:19.357Z");
    return mirageExceptionResponse;
  }

  private DsdcReceiveResponse getMockDsdcReceiveResponseSuccess() {
    return DsdcReceiveResponse.builder()
        .message("SUCCESS")
        .auditFlag("N")
        .batch("123")
        .dccarton("12345678")
        .dept("1")
        .div("2")
        .event("POS REPLEN")
        .hazmat("")
        .label_bar_code("123456789012345678")
        .lane_nbr("1")
        .po_nbr("12345678")
        .pocode("73")
        .rcvr_nbr("12345")
        .slot("R8002")
        .sneEnabled("1")
        .store("12345")
        .build();
  }

  private InstructionResponse buildMockInstructionResponse(
      String exceptionMessage, List<DeliveryDocument> deliveryDocuments) {
    Instruction instruction = new Instruction();
    RdcInstructionType rdcInstructionType = EXCEPTION_INSTRUCTION_TYPE_MAP.get(exceptionMessage);
    instruction.setInstructionCode(rdcInstructionType.getInstructionCode());
    instruction.setInstructionMsg(rdcInstructionType.getInstructionMsg());
    ExceptionInstructionMsg exceptionInstructionMsg = new ExceptionInstructionMsg();
    exceptionInstructionMsg.setDescription(
        RdcExceptionMsg.valueOf(exceptionMessage).getDescription());
    exceptionInstructionMsg.setTitle(RdcExceptionMsg.valueOf(exceptionMessage).getTitle());
    exceptionInstructionMsg.setInfo(RdcExceptionMsg.valueOf(exceptionMessage).getInfo());
    InstructionResponseImplException instructionResponseImplException =
        new InstructionResponseImplException();
    instructionResponseImplException.setInstruction(instruction);
    instructionResponseImplException.setExceptionInstructionMsg(exceptionInstructionMsg);
    if (Objects.nonNull(deliveryDocuments)) {
      instructionResponseImplException.setDeliveryDocuments(deliveryDocuments);
    }
    return instructionResponseImplException;
  }

  private ReceiveExceptionRequest getReceiveExceptionRequestForNoBarcodeSeen() {
    return ReceiveExceptionRequest.builder()
        .exceptionMessage("NO_BARCODE_SEEN")
        .lpns(Collections.singletonList("1234567890"))
        .slot("R8000")
        .deliveryNumbers(Arrays.asList("345123", "345123"))
        .tokenId("12345")
        .upcNumber("12345943012345")
        .isCatalogRequired(Boolean.FALSE)
        .build();
  }

  private DeliverySearchRequest getDeliverySearchRequest() {
    DeliverySearchRequest deliverySearchRequest = new DeliverySearchRequest();
    deliverySearchRequest.setDoorNumber("102");
    deliverySearchRequest.setMessageId("1246caaf-8cf7-4ad9-8151-20d20a4c3210");
    deliverySearchRequest.setUpc("UPC");
    deliverySearchRequest.setFromDate("2023-06-01T13:14:15.123+01:00");
    deliverySearchRequest.setToDate("2023-06-20T13:14:15.123+01:00");
    deliverySearchRequest.setLocationId("LocationId");

    Map<String, String> scannedData = new HashMap<>();
    scannedData.put("BARCODE_SCAN", "01234567891234");
    List<Map<String, String>> scannedDataList = Arrays.asList(scannedData);
    deliverySearchRequest.setScannedDataList(scannedDataList);
    return deliverySearchRequest;
  }

  private ReceiveExceptionRequest getReceiveExceptionRequestForPreLabeled() {
    return ReceiveExceptionRequest.builder()
        .doorNumber("100")
        .lpns(Collections.singletonList("1234567890"))
        .build();
  }

  public Instruction buildMockInstruction(String instructionType) {
    Instruction instruction = new Instruction();
    RdcInstructionType rdcInstructionType = EXCEPTION_INSTRUCTION_TYPE_MAP.get(instructionType);
    instruction.setInstructionCode(rdcInstructionType.getInstructionCode());
    instruction.setInstructionMsg(rdcInstructionType.getInstructionMsg());
    return instruction;
  }

  @Test
  public void test_getInstructionResponse() throws IOException, ReceivingException {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE),
            anyBoolean());

    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    ReceivingBadDataException receivingBadDataException =
        new ReceivingBadDataException(
            ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR);
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(OVERAGE, mockDeliveryDocuments);
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(OVERAGE);
    try {
      instructionResponse =
          rdcReceiveExceptionHandler.getOverageInstructionResponse(
              mockDeliveryDocuments, receiveExceptionRequest, receivingBadDataException);
    } catch (ReceivingBadDataException e) {
      fail();
    }
    assertEquals(
        RdcInstructionType.OVERAGE.getInstructionCode(),
        instructionResponse.getInstruction().getInstructionCode());
    assertEquals(
        RdcInstructionType.OVERAGE.getInstructionMsg(),
        instructionResponse.getInstruction().getInstructionMsg());
  }

  @Test
  public void test_getInstructionResponse_not_overage() throws IOException {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE),
            anyBoolean());

    ReceiveExceptionRequest receiveExceptionRequest =
        getReceiveExceptionRequest("LPN_NOT_RECEIVED");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    ReceivingBadDataException receivingBadDataException =
        new ReceivingBadDataException(
            ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR);
    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .buildInstructionResponse(LPN_NOT_RECEIVED, mockDeliveryDocuments);
    doCallRealMethod().when(rdcExceptionReceivingService).buildInstruction(LPN_NOT_RECEIVED);
    try {
      rdcReceiveExceptionHandler.getOverageInstructionResponse(
          mockDeliveryDocuments, receiveExceptionRequest, receivingBadDataException);
    } catch (ReceivingBadDataException e) {
      fail();
    }
  }

  @Test
  public void test_getInstructionResponse_receiveException() throws IOException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    ReceivingBadDataException receivingBadDataException =
        new ReceivingBadDataException(
            ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG,
            ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG);
    try {
      rdcReceiveExceptionHandler.getOverageInstructionResponse(
          mockDeliveryDocuments, receiveExceptionRequest, receivingBadDataException);
      fail();
    } catch (ReceivingBadDataException e) {
      assertNotEquals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
      assertNotEquals(receivingBadDataException.getDescription(), ReceivingException.OVERAGE_ERROR);
    }
  }

  @Test
  public void test_getInstructionResponse_receiveException_flag_false() throws IOException {
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE),
            anyBoolean());

    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("LITHIUM_ION");
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    ReceivingBadDataException receivingBadDataException =
        new ReceivingBadDataException(
            ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR);
    try {
      rdcReceiveExceptionHandler.getOverageInstructionResponse(
          mockDeliveryDocuments, receiveExceptionRequest, receivingBadDataException);
    } catch (ReceivingBadDataException e) {
      assertEquals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
      assertEquals(receivingBadDataException.getDescription(), ReceivingException.OVERAGE_ERROR);
    }
  }

  @Test
  public void test_create_problem_ticket() throws IOException, ReceivingException {

    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    receiveExceptionRequest.setPrinterNumber("1");
    receiveExceptionRequest.setQuantity(4);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, mockDeliveryDocuments);

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    File resource = new ClassPathResource("ProblemResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(mockResponse).when(fixitPlatformService).createProblemTag(anyString());
    assertEquals(receiveExceptionRequest.getExceptionMessage(), OVERAGE);
    try {
      InstructionResponse instructionResponse =
          rdcReceiveExceptionHandler.createProblemTicket(
              mockDeliveryDocuments, receiveExceptionRequest, new HttpHeaders());
      assertTrue(
          ((InstructionResponseImplNew) instructionResponse)
              .getPrintJob()
              .containsKey(PRINT_REQUEST_KEY));
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void test_create_problem_ticket_no_print_label() throws IOException, ReceivingException {

    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    receiveExceptionRequest.setQuantity(1);
    receiveExceptionRequest.setPrinterNumber("1");
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, mockDeliveryDocuments);

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());

    File resource = new ClassPathResource("ProblemResponse_NoPrintData.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(mockResponse).when(fixitPlatformService).createProblemTag(anyString());
    assertEquals(receiveExceptionRequest.getExceptionMessage(), OVERAGE);
    try {
      InstructionResponse instructionResponse =
          rdcReceiveExceptionHandler.createProblemTicket(
              mockDeliveryDocuments, receiveExceptionRequest, new HttpHeaders());
      assertFalse(
          ((InstructionResponseImplNew) instructionResponse)
              .getPrintJob()
              .containsKey(PRINT_REQUEST_KEY));
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void test_create_problem_ticket_exception() throws IOException, ReceivingException {

    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    receiveExceptionRequest.setPrinterNumber("1");
    receiveExceptionRequest.setQuantity(0);
    receiveExceptionRequest.setLpns(new ArrayList<>());
    doThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.BAD_REQUEST)
                .errorResponse(
                    ErrorResponse.builder()
                        .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE_INVALID_QTY)
                        .errorCode(ReceivingException.GET_PTAG_ERROR_CODE)
                        .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                        .build())
                .build())
        .when(fixitPlatformService)
        .createProblemTag(anyString());
    try {
      rdcReceiveExceptionHandler.createProblemTicket(
          mockDeliveryDocuments, receiveExceptionRequest, new HttpHeaders());
      fail();
    } catch (ReceivingException e) {
    }
  }

  @Test
  public void
      testReceiveException_maxOverageReceived_sendsInstructionMessage_lpnOverage_exception_empty_delivery_doc()
          throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    receiveExceptionRequest.setQuantity(1);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    receiveExceptionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    receiveExceptionRequest.setPrinterNumber("1");

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);

    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, mockDeliveryDocuments);

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG,
                ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));

    File resource = new ClassPathResource("ProblemResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(mockResponse).when(fixitPlatformService).createProblemTag(anyString());

    try {
      rdcReceiveExceptionHandler.receiveException(
          receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          receivingBadDataException.getErrorCode(), ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG);
      assertEquals(
          receivingBadDataException.getDescription(),
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG);
    }
  }

  @Test
  public void
      testReceiveException_maxOverageReceived_sendsInstructionMessage_lpnOverage_empty_delivery_doc()
          throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("OVERAGE");
    receiveExceptionRequest.setRegulatedItemType(NULL);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    receiveExceptionRequest.setDeliveryDocuments(mockDeliveryDocuments);
    receiveExceptionRequest.setPrinterNumber("1");

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("8458708163-1", 10L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);

    doReturn(receivedQuantityResponseFromRDS)
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), any());

    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(mockDeliveryDocuments);

    doReturn(receiveInstructionRequest)
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            "32679", ReceivingConstants.IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveExceptionRequest, mockDeliveryDocuments);

    doReturn(instructionRequest)
        .when(rdcExceptionReceivingService)
        .getInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG,
                ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG))
        .when(rdcReceiveInstructionHandler)
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));

    File resource = new ClassPathResource("ProblemResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(mockResponse).when(fixitPlatformService).createProblemTag(anyString());

    try {
      rdcReceiveExceptionHandler.receiveException(
          receiveExceptionRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingException receivingException) {
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.CREATE_PTAG_ERROR_MESSAGE_INVALID_QTY);
      assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.GET_PTAG_ERROR_CODE);
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          receivingBadDataException.getErrorCode(), ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG);
      assertEquals(
          receivingBadDataException.getDescription(),
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG);
    }
  }

  @Test
  public void testPrintShippingLabel_Success() throws ReceivingException {
    String trackingId = "a328990000000000000106509";
    Map<String, Object> mockPrintJob = MockRdcInstruction.getContainerDetails().getCtrLabel();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.WFT_LOCATION_NAME, "54");
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(buildMockContainer());
    when(rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
            any(Container.class), anyString(), anyString()))
        .thenReturn(mockPrintJob);
    Map<String, Object> printJob =
        rdcReceiveExceptionHandler.printShippingLabel(trackingId, httpHeaders);
    List<Map<String, Object>> printLabelRequests =
        (List<Map<String, Object>>) printJob.get(PRINT_REQUEST_KEY);
    Map<String, Object> printRequest = printLabelRequests.get(0);
    assertNotNull(printRequest);
    assertNotNull(printJob);
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(rdcExceptionReceivingService, times(1))
        .getPrintRequestPayLoadForShippingLabel(any(), any(), any());
    verify(orderFulfillmentRestApiClient, times(1))
        .printShippingLabelFromRoutingLabel(
            any(PrintShippingLabelRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testPrintShippingLabel() throws ReceivingException {
    String trackingId = "a338990000000000000106509";
    Map<String, Object> mockPrintJob = MockRdcInstruction.getContainerDetails().getCtrLabel();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.WFT_LOCATION_NAME, "54");
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(buildMockContainer());
    when(rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
            any(Container.class), anyString(), anyString()))
        .thenReturn(mockPrintJob);
    Map<String, Object> printJob =
        rdcReceiveExceptionHandler.printShippingLabel(trackingId, httpHeaders);
    assertNotNull(printJob);
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(rdcExceptionReceivingService, times(1))
        .getPrintRequestPayLoadForShippingLabel(any(), any(), any());
    verify(orderFulfillmentRestApiClient, times(1))
        .printShippingLabelFromRoutingLabel(
            any(PrintShippingLabelRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testPrintShippingLabel_EmptyContainer() throws ReceivingException {
    String trackingId = "a602042323232323";
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(null);
    rdcReceiveExceptionHandler.printShippingLabel(trackingId, MockHttpHeaders.getHeaders());
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(rdcExceptionReceivingService, times(0))
        .getPrintRequestPayLoadForShippingLabel(any(Container.class), anyString(), anyString());
    verify(orderFulfillmentRestApiClient, times(0))
        .printShippingLabelFromRoutingLabel(
            any(PrintShippingLabelRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testreceiveInstruction_Success() throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("RCV_LATENCY");
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    ItemData itemData =
        mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo();
    itemData.setHandlingCode("C");
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);

    doNothing()
        .when(rdcAutoReceivingUtils)
        .validateDeliveryDocuments(
            anyList(), any(AutoReceiveRequest.class), any(HttpHeaders.class));

    doNothing().when(rdcInstructionUtils).validateItemXBlocked(any(DeliveryDocumentLine.class));
    doReturn(false).when(rdcExceptionReceivingService).validateBreakPack(anyList());

    InstructionResponse mockInstructionResponse = getMockInstructionResponse();
    doReturn(mockInstructionResponse)
        .when(rdcAutoReceiveService)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));

    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    InstructionResponse instructionResponse =
        rdcReceiveExceptionHandler.receiveInstruction(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());

    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    assertNotNull(instructionResponse);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testreceiveInstruction_XBlockItem_Exception() throws IOException, ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest("RCV_LATENCY");
    MirageExceptionRequest aclExceptionRequest = getACLExceptionRequest(receiveExceptionRequest);
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    mockDeliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(false);
    ItemData itemData =
        mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo();
    itemData.setHandlingCode("X");
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);

    doNothing()
        .when(rdcAutoReceivingUtils)
        .validateDeliveryDocuments(
            anyList(), any(AutoReceiveRequest.class), any(HttpHeaders.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcExceptionReceivingService)
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));

    doThrow(new ReceivingBadDataException("Mock_error", "Mock_error"))
        .when(rdcInstructionUtils)
        .validateItemXBlocked(any(DeliveryDocumentLine.class));

    doCallRealMethod()
        .when(rdcExceptionReceivingService)
        .getReceiveInstructionRequest(any(ReceiveExceptionRequest.class), anyList());
    rdcReceiveExceptionHandler.receiveInstruction(
        receiveExceptionRequest, MockHttpHeaders.getHeaders());

    verify(rdcExceptionReceivingService, times(1))
        .fetchDeliveryDocumentsFromGDM(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    verify(rdcReceiveInstructionHandler, times(0))
        .receiveInstruction(any(ReceiveInstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void updateContainerHappyPathTest() throws ReceivingException {
    InventoryUpdateRequest inventoryUpdateRequest = new InventoryUpdateRequest();
    inventoryUpdateRequest.setTrackingId("876875ufb7ijhgh767y4");
    inventoryUpdateRequest.setLocationName("102");
    inventoryUpdateRequest.setProcessInLIUI(true);
    Container container = buildMockContainer();
    Map<String, String> destination = new HashMap<>();
    destination.put(SLOT, SYMCP_SLOT);
    container.setDestination(destination);
    container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(
            Collections.singletonList(container.getContainerItems().get(0).getAsrsAlignment()));
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(container);
    rdcReceiveExceptionHandler.inventoryContainerUpdate(
        inventoryUpdateRequest, MockHttpHeaders.getHeaders());
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void updateContainerWithProcessInLIUIFalseTest() throws ReceivingException {
    InventoryUpdateRequest updateContainerRequest = new InventoryUpdateRequest();
    updateContainerRequest.setTrackingId("876875ufb7ijhgh767y4");
    updateContainerRequest.setLocationName("102");
    rdcReceiveExceptionHandler.inventoryContainerUpdate(
        updateContainerRequest, MockHttpHeaders.getHeaders());
    verify(inventoryRestApiClient, times(1))
        .updateLocation(any(InventoryLocationUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void receiveExceptionWithRegulatedItemType() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = new ReceiveExceptionRequest();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(765876000l);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocumentLine.setItemNbr(76765778l);
    deliveryDocumentLine.setCaseUpc("9879990");
    deliveryDocumentLine.setPurchaseReferenceNumber("9879990");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(31);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocuments.add(deliveryDocument);
    receiveExceptionRequest.setItemNumber(76765778);
    receiveExceptionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveExceptionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY.name());
    HttpHeaders httpHeaders = new HttpHeaders();
    when(rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            any(ReceiveExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcExceptionReceivingService.getReceiveInstructionRequest(
            any(ReceiveExceptionRequest.class), anyList()))
        .thenReturn(receiveInstructionRequest);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("9879990-31", 99555l);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_ATLAS_EXCEPTION_RECEIVING, false))
        .thenReturn(true);
    InstructionResponse InstructionResponse = new InstructionResponseImplException();
    Instruction instruction = new Instruction();
    InstructionResponse.setInstruction(instruction);
    when(rdcReceiveInstructionHandler.receiveInstruction(
            any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(InstructionResponse);
    InstructionResponse response =
        rdcReceiveExceptionHandler.receiveException(receiveExceptionRequest, httpHeaders);
    assertNotNull(response);
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode());
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg());
  }

  private Map<String, Object> getMockPrintJob() {
    Map<String, Object> printJob = new HashMap<>();
    PrintLabelRequest printLabelRequest1 =
        PrintLabelRequest.builder().labelIdentifier("a602042323232323").build();
    PrintLabelRequest printLabelRequest2 =
        PrintLabelRequest.builder().labelIdentifier("a602042323232323").build();
    printJob.put(PRINT_REQUEST_KEY, Arrays.asList(printLabelRequest1, printLabelRequest2));
    return printJob;
  }

  private InstructionResponse getMockInstructionResponse() {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    instructionResponse.setInstructions(new ArrayList<>());
    instructionResponse.setDeliveryDocuments(new ArrayList<>());
    instructionResponse.setDeliveryStatus("WRK");
    return instructionResponse;
  }

  @Test
  public void receiveExceptionWithRegulatedItemTypeAndNGRServicesEnabled()
      throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = new ReceiveExceptionRequest();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(765876000l);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocumentLine.setItemNbr(76765778l);
    deliveryDocumentLine.setCaseUpc("9879990");
    deliveryDocumentLine.setPurchaseReferenceNumber("9879990");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(31);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocuments.add(deliveryDocument);
    receiveExceptionRequest.setItemNumber(76765778);
    receiveExceptionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveExceptionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY.name());
    HttpHeaders httpHeaders = new HttpHeaders();
    when(rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            any(ReceiveExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcExceptionReceivingService.getReceiveInstructionRequest(
            any(ReceiveExceptionRequest.class), anyList()))
        .thenReturn(receiveInstructionRequest);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    receivedQtyMapByPoAndPoLine.put("9879990-31", 99555l);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_ATLAS_EXCEPTION_RECEIVING, false))
        .thenReturn(true);
    InstructionResponse InstructionResponse = new InstructionResponseImplException();
    Instruction instruction = new Instruction();
    InstructionResponse.setInstruction(instruction);
    when(rdcReceiveInstructionHandler.receiveInstruction(
            any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(InstructionResponse);
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    InstructionResponse response =
        rdcReceiveExceptionHandler.receiveException(receiveExceptionRequest, httpHeaders);
    assertNotNull(response);
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode());
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg());
    verify(rdcReceivingUtils, times(2)).isNGRServicesEnabled();
  }
}
