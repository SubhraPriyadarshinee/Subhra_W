package com.walmart.move.nim.receiving.rx.service.v2.instruction.complete;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.transformer.RxContainerTransformer;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxFixitProblemService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CompleteInstructionDataValidator;
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
import java.util.concurrent.atomic.AtomicInteger;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;

public class CompleteInstructionServiceTest {

    @Mock
    private RxInstructionService rxInstructionService;
    @Mock private ContainerService containerService;
    @Mock private RxLpnUtils rxLpnUtils;
    @Mock private RxContainerLabelBuilder rxContainerLabelBuilder;
    @Mock private RxFixitProblemService rxFixitProblemService;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private CompleteInstructionDataValidator completeInstructionDataValidator;
    @Mock private CompleteInstructionOutboxService completeInstructionOutboxService;
   // @Mock private HttpStatus httpStatus;
    @InjectMocks
    private CompleteInstructionService completeInstructionService;
    @InjectMocks private RxContainerTransformer rxContainerTransformer;

    private Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
       // ReflectionTestUtils.setField(completeInstructionService, "gsonBuilder", gson);
        ReflectionTestUtils.setField(completeInstructionService, "gson", gson);
        ReflectionTestUtils.setField(
                completeInstructionOutboxService, "rxContainerTransformer", rxContainerTransformer);
        ReflectionTestUtils.setField(rxContainerTransformer, "gson", gson);

    }
    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCompleteInstruction_regularReceiving() throws ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        List<String> lpns = new ArrayList<>();
        lpns.add("lpn1");
        lpns.add("lpn2");


        ReceiveContainersResponseBody receiveContainersResponseBody =
                new ReceiveContainersResponseBody();
        ReceivedContainer receivedContainer = new ReceivedContainer();
        receivedContainer.setLabelTrackingId(lpns.get(0));
        Destination destination = new Destination();
        destination.setSlot("testSlot");
        receivedContainer.setDestinations(Collections.singletonList(destination));
        receiveContainersResponseBody.setReceived(Collections.singletonList(receivedContainer));

        Set<Container> containerList = new HashSet<>();
        container
                .getChildContainers()
                .forEach(
                        childContainer -> {
                            childContainer.setParentTrackingId(lpns.get(0));
                            childContainer.setInventoryStatus(AVAILABLE);
                            containerList.add(childContainer);
                        });


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

        MockHttpServletResponse mockHttpResponse = new MockHttpServletResponse();
        mockHttpResponse.setStatus(HttpStatus.OK.value());


        Mockito.when(completeInstructionDataValidator.validateAndGetInstruction(anyLong(), anyString())).thenReturn(instruction);
        Mockito.when(completeInstructionDataValidator.isEpcisSmartReceivingFlow(any(Instruction.class), any(DeliveryDocument.class))).thenReturn(true);
        Mockito.when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
        Mockito.doNothing().when(rxInstructionService).findSlotFromSmartSlotting(any(), any(), any(), any(), anyString(), anyBoolean());
        Mockito.when(rxInstructionService.mockRDSResponseObj(anyString(), any())).thenReturn(receiveContainersResponseBody);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.doNothing().when(containerService).setDistributionAndComplete(anyString(), any(Container.class));
        Mockito.when(rxInstructionService.updateParentContainerTrackingId(any(), anyString())).thenReturn(containerList);
        Mockito.when(rxContainerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any())).thenReturn(containerLabel);
        Mockito.when(rxInstructionService.getNewCtrLabel(any(), any())).thenReturn(getNewCtrLabel(containerLabel,  MockRxHttpHeaders.getHeaders()));
        Mockito.doNothing().when(rxFixitProblemService).completeProblem(any(), any(), any());
        Mockito.doNothing().when(rxInstructionService).publishDeliveryStatus(anyLong(), any());
        Mockito.when(rxDeliveryServiceImpl.updateEpcisReceivingStatus(any(), any())).thenReturn(HttpStatus.valueOf(mockHttpResponse.getStatus()));
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstruction(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(rxInstructionService).calculateAndLogElapsedTimeSummary();

        CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
        completeInstructionRequest.setPrinterId(101);

        InstructionResponse instructionResponse = completeInstructionService.completeInstruction(1234L, completeInstructionRequest, MockRxHttpHeaders.getHeaders());
        Assert.assertTrue(Objects.nonNull(instructionResponse));
    }


    @Test
    public void testCompleteInstruction_regularReceiving_Container_FULL_PALLET() throws ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        container.setRcvgContainerType("FULL-PALLET");
        Map<String, Object> misc = new HashMap<>();
        misc.put("gdmContainerId", "gdmContainerId");
        container.setContainerMiscInfo(misc);


        List<String> lpns = new ArrayList<>();
        lpns.add("lpn1");
        lpns.add("lpn2");


        ReceiveContainersResponseBody receiveContainersResponseBody =
                new ReceiveContainersResponseBody();
        ReceivedContainer receivedContainer = new ReceivedContainer();
        receivedContainer.setLabelTrackingId(lpns.get(0));
        Destination destination = new Destination();
        destination.setSlot("testSlot");
        receivedContainer.setDestinations(Collections.singletonList(destination));
        receiveContainersResponseBody.setReceived(Collections.singletonList(receivedContainer));

        Set<Container> containerList = new HashSet<>();
        container
                .getChildContainers()
                .forEach(
                        childContainer -> {
                            childContainer.setParentTrackingId(lpns.get(0));
                            childContainer.setInventoryStatus(AVAILABLE);
                            containerList.add(childContainer);
                        });


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

        MockHttpServletResponse mockHttpResponse = new MockHttpServletResponse();
        mockHttpResponse.setStatus(HttpStatus.OK.value());


        Mockito.when(completeInstructionDataValidator.validateAndGetInstruction(anyLong(), anyString())).thenReturn(instruction);
        Mockito.when(completeInstructionDataValidator.isEpcisSmartReceivingFlow(any(Instruction.class), any(DeliveryDocument.class))).thenReturn(true);
        Mockito.when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
        Mockito.doNothing().when(rxInstructionService).findSlotFromSmartSlotting(any(), any(), any(), any(), anyString(), anyBoolean());
        Mockito.when(completeInstructionOutboxService.getContainerWithChildsByTrackingId(anyString())).thenReturn(container);
        Mockito.when(rxInstructionService.mockRDSResponseObj(anyString(), any())).thenReturn(receiveContainersResponseBody);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.doNothing().when(containerService).setDistributionAndComplete(anyString(), any(Container.class));
        Mockito.when(rxInstructionService.updateParentContainerTrackingId(any(), anyString())).thenReturn(containerList);
        Mockito.when(rxContainerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any())).thenReturn(containerLabel);
        Mockito.when(rxInstructionService.getNewCtrLabel(any(), any())).thenReturn(getNewCtrLabel(containerLabel,  MockRxHttpHeaders.getHeaders()));
        Mockito.doNothing().when(rxFixitProblemService).completeProblem(any(), any(), any());
        Mockito.doNothing().when(rxInstructionService).publishDeliveryStatus(anyLong(), any());
        Mockito.when(rxDeliveryServiceImpl.updateEpcisReceivingStatus(any(), any())).thenReturn(HttpStatus.valueOf(mockHttpResponse.getStatus()));
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstruction(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(rxInstructionService).calculateAndLogElapsedTimeSummary();

        CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
        completeInstructionRequest.setPrinterId(101);

        InstructionResponse instructionResponse = completeInstructionService.completeInstruction(1234L, completeInstructionRequest, MockRxHttpHeaders.getHeaders());
        Assert.assertTrue(Objects.nonNull(instructionResponse));


    }
    @Test
    public void testCompleteInstruction_regularReceiving_childContainer_PARTIAL_CASE() throws ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        //container.setRcvgContainerType("PARTIAL-CASE");
        AtomicInteger count = new AtomicInteger();
        container.getChildContainers().forEach(
                child -> {
                    count.getAndIncrement();
                    child.setRcvgContainerType("PARTIAL-CASE");
                    child.setTrackingId("trackingId" + count.toString() );
                }

        );

        List<String> lpns = new ArrayList<>();
        lpns.add("lpn1");
        lpns.add("lpn2");


        ReceiveContainersResponseBody receiveContainersResponseBody =
                new ReceiveContainersResponseBody();
        ReceivedContainer receivedContainer = new ReceivedContainer();
        receivedContainer.setLabelTrackingId(lpns.get(0));
        Destination destination = new Destination();
        destination.setSlot("testSlot");
        receivedContainer.setDestinations(Collections.singletonList(destination));
        receiveContainersResponseBody.setReceived(Collections.singletonList(receivedContainer));

        Set<Container> containerList = new HashSet<>();
        container
                .getChildContainers()
                .forEach(
                        childContainer -> {
                            childContainer.setParentTrackingId(lpns.get(0));
                            childContainer.setInventoryStatus(AVAILABLE);
                            containerList.add(childContainer);
                        });


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

        MockHttpServletResponse mockHttpResponse = new MockHttpServletResponse();
        mockHttpResponse.setStatus(HttpStatus.OK.value());


        Mockito.when(completeInstructionDataValidator.validateAndGetInstruction(anyLong(), anyString())).thenReturn(instruction);
        Mockito.when(completeInstructionDataValidator.isEpcisSmartReceivingFlow(any(Instruction.class), any(DeliveryDocument.class))).thenReturn(true);
        Mockito.when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
        Mockito.doNothing().when(rxInstructionService).findSlotFromSmartSlotting(any(), any(), any(), any(), anyString(), anyBoolean());
        Mockito.when(completeInstructionOutboxService.getContainerWithChildsByTrackingId(anyString())).thenReturn(container);
        Mockito.when(rxInstructionService.mockRDSResponseObj(anyString(), any())).thenReturn(receiveContainersResponseBody);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.doNothing().when(containerService).setDistributionAndComplete(anyString(), any(Container.class));
        Mockito.when(rxInstructionService.updateParentContainerTrackingId(any(), anyString())).thenReturn(containerList);
        Mockito.when(rxContainerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any())).thenReturn(containerLabel);
        Mockito.when(rxInstructionService.getNewCtrLabel(any(), any())).thenReturn(getNewCtrLabel(containerLabel,  MockRxHttpHeaders.getHeaders()));
        Mockito.doNothing().when(rxFixitProblemService).completeProblem(any(), any(), any());
        Mockito.doNothing().when(rxInstructionService).publishDeliveryStatus(anyLong(), any());
        Mockito.when(rxDeliveryServiceImpl.updateEpcisReceivingStatus(any(), any())).thenReturn(HttpStatus.valueOf(mockHttpResponse.getStatus()));
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstruction(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(rxInstructionService).calculateAndLogElapsedTimeSummary();

        CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
        completeInstructionRequest.setPrinterId(101);

        InstructionResponse instructionResponse = completeInstructionService.completeInstruction(1234L, completeInstructionRequest, MockRxHttpHeaders.getHeaders());
        Assert.assertTrue(Objects.nonNull(instructionResponse));


    }

    @Test
    public void testCompleteInstruction_regularReceiving_childContainer_CASE() throws ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        //container.setRcvgContainerType("PARTIAL-CASE");
        AtomicInteger count = new AtomicInteger();
        container.getChildContainers().forEach(
                child -> {
                    count.getAndIncrement();
                    child.setRcvgContainerType("CASE");
                    child.setTrackingId("trackingId" + count.toString() );
                }

        );

        List<String> lpns = new ArrayList<>();
        lpns.add("lpn1");
        lpns.add("lpn2");


        ReceiveContainersResponseBody receiveContainersResponseBody =
                new ReceiveContainersResponseBody();
        ReceivedContainer receivedContainer = new ReceivedContainer();
        receivedContainer.setLabelTrackingId(lpns.get(0));
        Destination destination = new Destination();
        destination.setSlot("testSlot");
        receivedContainer.setDestinations(Collections.singletonList(destination));
        receiveContainersResponseBody.setReceived(Collections.singletonList(receivedContainer));

        Set<Container> containerList = new HashSet<>();
        container
                .getChildContainers()
                .forEach(
                        childContainer -> {
                            childContainer.setParentTrackingId(lpns.get(0));
                            childContainer.setInventoryStatus(AVAILABLE);
                            containerList.add(childContainer);
                        });


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

        MockHttpServletResponse mockHttpResponse = new MockHttpServletResponse();
        mockHttpResponse.setStatus(HttpStatus.OK.value());


        Mockito.when(completeInstructionDataValidator.validateAndGetInstruction(anyLong(), anyString())).thenReturn(instruction);
        Mockito.when(completeInstructionDataValidator.isEpcisSmartReceivingFlow(any(Instruction.class), any(DeliveryDocument.class))).thenReturn(true);
        Mockito.when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
        Mockito.doNothing().when(rxInstructionService).findSlotFromSmartSlotting(any(), any(), any(), any(), anyString(), anyBoolean());
        Mockito.when(completeInstructionOutboxService.getContainerWithChildsByTrackingId(anyString())).thenReturn(container);
        Mockito.when(rxInstructionService.mockRDSResponseObj(anyString(), any())).thenReturn(receiveContainersResponseBody);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.doNothing().when(containerService).setDistributionAndComplete(anyString(), any(Container.class));
        Mockito.when(rxInstructionService.updateParentContainerTrackingId(any(), anyString())).thenReturn(containerList);
        Mockito.when(rxContainerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any())).thenReturn(containerLabel);
        Mockito.when(rxInstructionService.getNewCtrLabel(any(), any())).thenReturn(getNewCtrLabel(containerLabel,  MockRxHttpHeaders.getHeaders()));
        Mockito.doNothing().when(rxFixitProblemService).completeProblem(any(), any(), any());
        Mockito.doNothing().when(rxInstructionService).publishDeliveryStatus(anyLong(), any());
        Mockito.when(rxDeliveryServiceImpl.updateEpcisReceivingStatus(any(), any())).thenReturn(HttpStatus.valueOf(mockHttpResponse.getStatus()));
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstruction(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(rxInstructionService).calculateAndLogElapsedTimeSummary();

        CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
        completeInstructionRequest.setPrinterId(101);

        InstructionResponse instructionResponse = completeInstructionService.completeInstruction(1234L, completeInstructionRequest, MockRxHttpHeaders.getHeaders());
        Assert.assertTrue(Objects.nonNull(instructionResponse));


    }


    @Test
    public void testCompleteInstruction_ProblemReceiving() throws ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        instruction.setProblemTagId("prob123");
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        List<String> lpns = new ArrayList<>();
        lpns.add("lpn1");
        lpns.add("lpn2");

        ReceiveContainersResponseBody receiveContainersResponseBody =
                new ReceiveContainersResponseBody();
        ReceivedContainer receivedContainer = new ReceivedContainer();
        receivedContainer.setLabelTrackingId(lpns.get(0));
        Destination destination = new Destination();
        destination.setSlot("testSlot");
        receivedContainer.setDestinations(Collections.singletonList(destination));
        receiveContainersResponseBody.setReceived(Collections.singletonList(receivedContainer));

        Set<Container> containerList = new HashSet<>();
        container
                .getChildContainers()
                .forEach(
                        childContainer -> {
                            childContainer.setParentTrackingId(lpns.get(0));
                            childContainer.setInventoryStatus(AVAILABLE);
                            containerList.add(childContainer);
                        });


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

        MockHttpServletResponse mockHttpResponse = new MockHttpServletResponse();
        mockHttpResponse.setStatus(HttpStatus.OK.value());


        Mockito.when(completeInstructionDataValidator.validateAndGetInstruction(anyLong(), anyString())).thenReturn(instruction);
        Mockito.when(completeInstructionDataValidator.isEpcisSmartReceivingFlow(any(Instruction.class), any(DeliveryDocument.class))).thenReturn(true);
        Mockito.when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
        Mockito.doNothing().when(rxInstructionService).findSlotFromSmartSlotting(any(), any(), any(), any(), anyString(), anyBoolean());
        Mockito.when(rxInstructionService.mockRDSResponseObj(anyString(), any())).thenReturn(receiveContainersResponseBody);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.doNothing().when(containerService).setDistributionAndComplete(anyString(), any(Container.class));
        Mockito.when(rxInstructionService.updateParentContainerTrackingId(any(), anyString())).thenReturn(containerList);
        Mockito.when(rxContainerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any())).thenReturn(containerLabel);
        Mockito.when(rxInstructionService.getNewCtrLabel(any(), any())).thenReturn(getNewCtrLabel(containerLabel,  MockRxHttpHeaders.getHeaders()));
        Mockito.doNothing().when(rxFixitProblemService).completeProblem(any(), any(), any());
        Mockito.doNothing().when(rxInstructionService).publishDeliveryStatus(anyLong(), any());
        Mockito.when(rxDeliveryServiceImpl.updateEpcisReceivingStatus(any(), any())).thenReturn(HttpStatus.valueOf(mockHttpResponse.getStatus()));
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.doNothing().when(completeInstructionOutboxService).outboxCompleteInstruction(any(), any(), anyString(), any(), any());
        Mockito.doNothing().when(rxInstructionService).calculateAndLogElapsedTimeSummary();

        CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
        completeInstructionRequest.setPrinterId(101);

        InstructionResponse instructionResponse = completeInstructionService.completeInstruction(1234L, completeInstructionRequest, MockRxHttpHeaders.getHeaders());
        Assert.assertTrue(Objects.nonNull(instructionResponse));
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
        map.put("gdmContainerId", "gdmContainerId");
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
}