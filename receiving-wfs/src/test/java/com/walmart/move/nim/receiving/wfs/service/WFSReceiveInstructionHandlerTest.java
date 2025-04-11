package com.walmart.move.nim.receiving.wfs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.PrintLabelHelper;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WFSReceiveInstructionHandlerTest {
  @InjectMocks private WFSReceiveInstructionHandler wfsReceiveInstructionHandler;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private WFSInstructionHelperService wfsInstructionHelperService;
  @Mock private InstructionHelperService instructionHelperService;
  @InjectMocks private ContainerService containerService;
  @Mock private PrintLabelHelper printLabelHelper;

  @Mock private ContainerService containerService1;
  @Mock private InstructionService instructionService;
  @Mock private InstructionStateValidator instructionStateValidator;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private WFSInstructionService wfsInstructionService;
  private static final String facilityNum = "4093";
  private static final String countryCode = "US";
  private Map<String, Object> mockInstructionContainerMap;
  private HttpHeaders httpheaders;
  private Gson gson;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(6280);
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    httpheaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @AfterMethod
  public void resetMocks() {
    // add all mocks
    reset(
        instructionPersisterService,
        instructionHelperService,
        instructionService,
        instructionStateValidator,
        wfsInstructionHelperService);
    Mockito.reset(containerService1);
  }

  String inputPayloadPath = "../receiving-wfs/src/test/resources/receiveInstructionRequest.json";
  String outputPayloadPath = "../receiving-wfs/src/test/resources/receiveInstructionResponse.json";
  String instructionPayloadPath = "../receiving-wfs/src/test/resources/ReceiveInstruction.json";
  String inputPayload = getJSONStringResponse(inputPayloadPath);
  String outputPayload = getJSONStringResponse(outputPayloadPath);
  String instructionPayload = getJSONStringResponse(instructionPayloadPath);

  @Test
  public void testReceiveInstruction_isSuccessful() throws ReceivingException {
    // Mock instruction and request
    Map<String, String> ctrDestination = new HashMap<>();
    ctrDestination.put(ReceivingConstants.FACILITY_NAME, "ORD4");
    when(wfsInstructionHelperService.mapFCNumberToFCName(anyMap())).thenReturn(ctrDestination);
    Instruction instruction = gson.fromJson(instructionPayload, Instruction.class);
    ReceiveInstructionRequest receiveInstructionRequest =
        gson.fromJson(inputPayload, ReceiveInstructionRequest.class);

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

    doNothing().when(instructionStateValidator).validate(instruction);
    when(containerService1.processCreateContainersForWFSPOwithRIR(
            any(Instruction.class), any(ReceiveInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockContainer());

    // Mock map
    mockInstructionContainerMap = new HashMap<>();
    mockInstructionContainerMap.put("instruction", instruction);
    mockInstructionContainerMap.put("container", getMockContainer());
    Map<String, Object> instructionContainerMap = mockInstructionContainerMap;

    when(wfsInstructionHelperService.createContainersAndReceiptsForWFSPosRIR(
            any(ReceiveInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class)))
        .thenReturn(instructionContainerMap);

    InstructionResponse realInstructionResponse =
        gson.fromJson(outputPayload, InstructionResponseImplNew.class);
    when(wfsInstructionHelperService.prepareWFSInstructionResponse(
            any(Instruction.class), any(Container.class), anyString()))
        .thenReturn(realInstructionResponse);

    String dcTimeZone = "";
    when(configUtils.getDCTimeZone(anyInt())).thenReturn(dcTimeZone);
    // real API call
    InstructionResponseImplNew instructionResponse =
        (InstructionResponseImplNew)
            wfsReceiveInstructionHandler.receiveInstruction(
                instruction.getId(),
                receiveInstructionRequest,
                MockHttpHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(wfsInstructionHelperService, times(1))
        .createContainersAndReceiptsForWFSPosRIR(
            any(ReceiveInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class));
    verify(wfsInstructionHelperService, times(1))
        .prepareWFSInstructionResponse(any(Instruction.class), any(Container.class), anyString());

    // assert
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) instructionResponse.getPrintJob().get("printRequests");

    List<Map<String, Object>> labelData =
        (List<Map<String, Object>>) printRequests.get(0).get("data");

    String actual_ = "";

    List<Map<String, Object>> labelDataElement =
        labelData
            .stream()
            .filter(x -> x.get("key").toString().equalsIgnoreCase("FCNAME"))
            .collect(Collectors.toList());

    actual_ = labelDataElement.get(0).get("value").toString();

    String expected_ = "ORD4";

    assertEquals(actual_, expected_);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveInstruction_instructionDoesNotExist() throws ReceivingException {
    Instruction instruction = gson.fromJson(instructionPayload, Instruction.class);
    ReceiveInstructionRequest receiveInstructionRequest =
        gson.fromJson(inputPayload, ReceiveInstructionRequest.class);
    doThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE))
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    wfsReceiveInstructionHandler.receiveInstruction(
        instruction.getId(),
        receiveInstructionRequest,
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "This instruction is already completed, so it cannot be cancelled")
  public void testReceiveInstruction_instructionIsInvalid() throws ReceivingException {
    Instruction instruction = gson.fromJson(instructionPayload, Instruction.class);
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

    ReceiveInstructionRequest receiveInstructionRequest =
        gson.fromJson(inputPayload, ReceiveInstructionRequest.class);
    doThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE))
        .when(instructionStateValidator)
        .validate(instruction);
    wfsReceiveInstructionHandler.receiveInstruction(
        instruction.getId(),
        receiveInstructionRequest,
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveInstruction_instructionContainerMapIsInvalid() throws ReceivingException {
    Instruction instruction = gson.fromJson(instructionPayload, Instruction.class);
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceiveInstructionRequest receiveInstructionRequest =
        gson.fromJson(inputPayload, ReceiveInstructionRequest.class);
    doThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE))
        .when(wfsInstructionHelperService)
        .createContainersAndReceiptsForWFSPosRIR(
            receiveInstructionRequest, httpHeaders, userId, instruction);
    wfsReceiveInstructionHandler.receiveInstruction(
        instruction.getId(),
        receiveInstructionRequest,
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveInstruction_InstructionResponseIsInvalid() throws ReceivingException {
    Instruction instruction = gson.fromJson(instructionPayload, Instruction.class);
    Container mockContainer = getMockContainer();
    mockInstructionContainerMap = new HashMap<>();
    mockInstructionContainerMap.put("instruction", instruction);
    mockInstructionContainerMap.put("container", mockContainer);
    Map<String, Object> instructionContainerMap = mockInstructionContainerMap;

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(wfsInstructionHelperService.createContainersAndReceiptsForWFSPosRIR(
            any(ReceiveInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class)))
        .thenReturn(instructionContainerMap);

    String dcTimeZone = configUtils.getDCTimeZone(TenantContext.getFacilityNum());
    ReceiveInstructionRequest receiveInstructionRequest =
        gson.fromJson(inputPayload, ReceiveInstructionRequest.class);
    doThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE))
        .when(wfsInstructionHelperService)
        .prepareWFSInstructionResponse(instruction, mockContainer, dcTimeZone);
    wfsReceiveInstructionHandler.receiveInstruction(
        instruction.getId(),
        receiveInstructionRequest,
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  private String getJSONStringResponse(String path) {
    String response = null;
    response = getResponseFromJSONFilePath(path);
    if (Objects.nonNull(response)) {
      return response;
    }
    assert (false);
    return null;
  }

  private String getResponseFromJSONFilePath(String path) {
    String payload = null;
    try {
      String filePath = new File(path).getCanonicalPath();
      payload = new String(Files.readAllBytes(Paths.get(filePath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return payload;
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setTrackingId("B67387000020002031");
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("100");
    container.setDeliveryNumber(Long.parseLong("18278904"));
    container.setParentTrackingId(null);
    container.setContainerType("PALLET");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");
    container.setIsConveyable(Boolean.TRUE);
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    container.setCompleteTs(new Date());

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("B67387000020002031");
    containerItem.setPurchaseReferenceNumber("199557349");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("556565795"));
    containerItem.setGtin("00049807100011");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setVendorNumber(1234);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setPromoBuyInd("Y");
    containerItem.setActualTi(6);
    containerItem.setActualHi(2);
    containerItem.setVnpkWgtQty(14.84F);
    containerItem.setVnpkWgtUom("LB");
    containerItem.setVnpkcbqty(0.432F);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setDescription("70QT XTREME BLUE");
    containerItem.setSecondaryDescription("WH TO ASM");
    containerItem.setRotateDate(new Date());
    containerItem.setPoTypeCode(20);
    containerItem.setVendorNbrDeptSeq(1234);
    containerItem.setLotNumber("LOT555");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);
    container.setPublishTs(new Date());
    return container;
  }
}
