package com.walmart.move.nim.receiving.rx.service.v2.instruction.complete;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.BulkCompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.CompleteMultipleInstructionData;
import com.walmart.move.nim.receiving.core.model.CompleteMultipleInstructionResponse;
import com.walmart.move.nim.receiving.core.model.SlotDetails;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.transformer.RxContainerTransformer;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.*;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class RxCompleteSplitPalletInstructionServiceTest {

    @Mock private RxManagedConfig rxManagedConfig;
    @Mock private InstructionPersisterService instructionPersisterService;
    @Mock private EpcisService epcisService;
    @Mock private RxContainerLabelBuilder rxContainerLabelBuilder;
    @Mock private NimRdsServiceImpl nimRdsServiceImpl;
    @Mock private ContainerService containerService;
    @Mock private RxSlottingServiceImpl rxSlottingServiceImpl;
    @Mock private RxInstructionHelperService rxInstructionHelperService;
    @Mock private RxInstructionValidator rxInstructionValidator;
    @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
    @Mock  private CompleteInstructionOutboxService completeInstructionOutboxService;
    @Mock private RxInstructionService rxInstructionService;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private RxLpnUtils rxLpnUtils;
    @InjectMocks private RxCompleteSplitPalletInstructionService rxCompleteSplitPalletInstructionService;
    @InjectMocks private RxContainerTransformer rxContainerTransformer;
    private Gson gson = new Gson();
    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        // ReflectionTestUtils.setField(completeInstructionService, "gsonBuilder", gson);
        ReflectionTestUtils.setField(rxCompleteSplitPalletInstructionService, "gson", gson);
        ReflectionTestUtils.setField(
                completeInstructionOutboxService, "rxContainerTransformer", rxContainerTransformer);
        ReflectionTestUtils.setField(rxContainerTransformer, "gson", gson);

    }
    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testComplete() throws ReceivingException {
        Instruction instruction1 = MockInstruction.getInstructionV2("RxSerBuildContainer");
        Instruction instruction2 = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction1.setId(1234L);
        instruction1.setMove(getMoveData());
        instruction1.setInstructionSetId(1L);
        instruction2.setId(5678L);
        instruction2.setMove(getMoveData());
        instruction2.setInstructionSetId(2L);
        SlotDetails slotDetails = new SlotDetails();
        slotDetails.setSlot("slot_1234");
        BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
                new BulkCompleteInstructionRequest();

        CompleteMultipleInstructionData mockCompleteMultipleInstructionData1 =
                new CompleteMultipleInstructionData();

        mockCompleteMultipleInstructionData1.setInstructionId(1234L);
        mockCompleteMultipleInstructionData1.setSlotDetails(slotDetails);
        CompleteMultipleInstructionData mockCompleteMultipleInstructionData2 =
                new CompleteMultipleInstructionData();
        mockCompleteMultipleInstructionData2.setInstructionId(5678L);
        mockCompleteMultipleInstructionData2.setSlotDetails(slotDetails);
        mockBulkCompleteInstructionRequest.setInstructionData(
                Arrays.asList(mockCompleteMultipleInstructionData1, mockCompleteMultipleInstructionData2));
        List<String> lpns = new ArrayList<>();
        lpns.add("lpn1");
        lpns.add("lpn2");
        PrintLabelRequest printLabelRequest = new PrintLabelRequest();
        printLabelRequest.setFormatName("case_lpn_format");
        printLabelRequest.setLabelIdentifier(lpns.get(0));
        printLabelRequest.setTtlInHours(72);
        printLabelRequest.setData(new ArrayList<>());

        List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
        printLabelRequests.add(printLabelRequest);

        // Build container label
        PrintLabelData containerLabel = new PrintLabelData();
        containerLabel.setClientId(RxConstants.CLIENT_ID);
        containerLabel.setHeaders(new HashMap<>());
        containerLabel.setPrintRequests(printLabelRequests);

        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        MockHttpServletResponse mockHttpResponse = new MockHttpServletResponse();
        mockHttpResponse.setStatus(HttpStatus.OK.value());

        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction1);
        Mockito.doNothing().when(rxInstructionValidator).validateInstructionStatus(any(Instruction.class));
        Mockito.doNothing().when(rxInstructionValidator).verifyCompleteUser(any(Instruction.class), anyString(), anyString());
        Mockito.when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
        Mockito.when(rxSlottingServiceImpl.acquireSlotMultiPallets(anyString(), anyInt(), anyList(), anyList(), anyString(), any())).thenReturn(mockSlottingPalletResponse());
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.when(rxContainerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any())).thenReturn(containerLabel);
        Mockito.doNothing().when(containerService).setDistributionAndComplete(anyString(), any(Container.class));
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.when(rxDeliveryServiceImpl.updateEpcisReceivingStatus(any(), any())).thenReturn(HttpStatus.valueOf(mockHttpResponse.getStatus()));
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstruction(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstructionAsnFlow(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(rxInstructionHelperService).persist(any(Container.class), any(Instruction.class), anyString());
        Mockito.doNothing().when(rxInstructionHelperService).publishContainers(any());

        CompleteMultipleInstructionResponse output = rxCompleteSplitPalletInstructionService.complete(mockBulkCompleteInstructionRequest,  MockRxHttpHeaders.getHeaders());
        Assert.assertNotNull(output);
    }

    @Test
    public void testMockRDSResponseObj() {
        Instruction instruction1 = MockInstruction.getInstructionV2("RxSerBuildContainer");
        instruction1.setMove(getMoveData());
        instruction1.setId(1234L);
        Instruction instruction2 = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction2.setMove(getMoveData());
        instruction2.setId(5678L);
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(instruction1);
        instructions.add(instruction2);

        List<String> trackingIds = new ArrayList<>();
        trackingIds.add("Tracking_1234");
        trackingIds.add("Tracking_5678");

        SlotDetails slotDetails = new SlotDetails();
        slotDetails.setSlot("slot_1234");

        ReceiveContainersResponseBody response = rxCompleteSplitPalletInstructionService.mockRDSResponseObj(trackingIds, slotDetails, instructions);
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getReceived().get(0).getLabelTrackingId(), "Tracking_1234");




    }


    private Map<String, Object> getNewCtrLabel(
            PrintLabelData containerLabel, HttpHeaders httpHeaders) {
        Map<String, String> headers = new HashMap<>();
        headers.put(
                ReceivingConstants.TENENT_FACLITYNUM,
                httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
        headers.put(
                ReceivingConstants.TENENT_COUNTRY_CODE,
                httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
        headers.put(
                ReceivingConstants.CORRELATION_ID_HEADER_KEY,
                httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        Map<String, Object> ctrLabel = new HashMap<>();
        ctrLabel.put("clientId", containerLabel.getClientId());
        ctrLabel.put("headers", headers);
        ctrLabel.put(
                "printRequests", gson.fromJson(gson.toJson(containerLabel.getPrintRequests()), List.class));

        return ctrLabel;
    }


    private Container mockResponseForGetParentContainer(
            String parentTrackingId, String trackingId, int quantity) {
        Container container = new Container();
        container.setDeliveryNumber(12345l);
        container.setTrackingId(trackingId);
        container.setParentTrackingId(parentTrackingId);
        container.setChildContainers(mockResponseForGetContainerIncludesChildren(trackingId));
        container.setInstructionId(1L);
        container.setContainerItems(Arrays.asList(MockInstruction.getContainerItem()));
        container.setSsccNumber("test_sscc");
        HashMap<String, Object> map = new HashMap<>();
        map.put("instructionCode", "RxSerBuildUnitScan");
        container.setContainerMiscInfo(map);
        return container;
    }

    private Set<Container> mockResponseForGetContainerIncludesChildren(String trackingId) {
        Container childContainer1 = createChildContainer("12345", "123", 6);
        Container childContainer2 = createChildContainer("12345", "456", 6);
        Set<Container> childContainers = new HashSet<>();
        childContainers.add(childContainer1);
        childContainers.add(childContainer2);
        return childContainers;
    }

    private Container createChildContainer(String parentTrackingId, String trackingId, int quantity) {
        Container container = new Container();
        container.setDeliveryNumber(12345l);
        container.setTrackingId(trackingId);
        container.setParentTrackingId(parentTrackingId);

        ContainerItem containerItem = new ContainerItem();
        containerItem.setTrackingId(trackingId);
        containerItem.setPurchaseReferenceNumber("987654321");
        containerItem.setPurchaseReferenceLineNumber(1);
        containerItem.setQuantity(quantity);
        containerItem.setVnpkQty(6);
        containerItem.setWhpkQty(6);

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(KEY_SSCC, "test_sscc");
        map.put(KEY_GTIN, "test_gtin");
        map.put(KEY_SERIAL, trackingId.toString());
        map.put(KEY_LOT, "test_lot");
        container.setContainerMiscInfo(map);

        container.setContainerItems(Arrays.asList(containerItem));

        return container;
    }

    private LinkedTreeMap<String, Object> getMoveData() {
        LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
        move.put("lastChangedBy", "OF-SYS");
        move.put("lastChangedOn", new Date());
        move.put("sequenceNbr", 543397582);
        move.put("containerTag", "b328990000000000000048571");
        move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
        move.put("toLocation", "302");
        return move;
    }

    private SlottingPalletResponse mockSlottingPalletResponse() {

        SlottingDivertLocations location = new SlottingDivertLocations();
        location.setType("success");
        location.setLocation("A1234");
        location.setItemNbr(579516308);
        location.setAsrsAlignment("SYM2");
        location.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
        List<SlottingDivertLocations> locationList = new ArrayList();
        locationList.add(location);

        SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
        mockSlottingResponseBody.setMessageId("a1-b1-c1");
        mockSlottingResponseBody.setLocations(locationList);
        return mockSlottingResponseBody;
    }
}