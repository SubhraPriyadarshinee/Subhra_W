package com.walmart.move.nim.receiving.rx.service.v2.instruction.complete;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRestClient;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.SlotDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import com.walmart.move.nim.receiving.core.transformer.RxContainerTransformer;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.*;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.platform.service.OutboxEventSinkService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class CompleteInstructionOutboxServiceTest {



   // @Mock Gson gson;
    @Mock
    private RxInstructionHelperService rxInstructionHelperService;


    @Mock private OutboxEventSinkService outboxEventSinkService;
    @Mock private EpcisService epcisService;
    @Mock private EpcisRestClient epcisRestClient;

    @InjectMocks private RxContainerTransformer rxContainerTransformer;

    @Mock private ContainerService containerService;
    @Mock private ContainerPersisterService containerPersisterService;
    @Autowired
    private PrintJobService printJobService;
    @Mock private InstructionPersisterService instructionPersisterService;

    @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
    @Mock private AppConfig appConfig;
    @Mock private OutboxConfig outboxConfig;
    @Mock private RxInstructionService rxInstructionService;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private ContainerRepository containerRepository;
    @Mock private InstructionFactory instructionFactory;

    @Mock RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
    @Mock private LPNCacheService lpnCacheService;

    @InjectMocks
    private CompleteInstructionOutboxService completeInstructionOutboxService;
    private Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(completeInstructionOutboxService, "gsonBuilder", gson);
        ReflectionTestUtils.setField(completeInstructionOutboxService, "gson", gson);
        ReflectionTestUtils.setField(
                completeInstructionOutboxService, "rxContainerTransformer", rxContainerTransformer);
        ReflectionTestUtils.setField(rxContainerTransformer, "gson", gson);
        ReflectionTestUtils.setField(rxCompleteInstructionOutboxHandler, "gson", gson);
    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInit() {
        completeInstructionOutboxService.init();
        Assert.assertNotNull(gson);
    }

    @AfterMethod
    public void teardown() {
        reset(appConfig);
        reset(containerPersisterService);
        reset(instructionPersisterService);
        reset(containerService);
        reset(lpnCacheService);
    }

    @Test
    public void testOutboxCompleteInstruction()  {
        Instruction instruction = MockInstruction.getMockInstructionEpcis();
        instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
        Container parent = MockInstruction.getContainer();


        Mockito.doNothing().when(rxInstructionHelperService).persist(any(Container.class), any(Instruction.class), anyString());
        Mockito.when(outboxEventSinkService.saveEvent(any())).thenReturn(true);
        Mockito.doNothing().when(rxInstructionService).findSlotFromSmartSlotting(any(), any(), any(), any(), anyString(), anyBoolean());
        Mockito.doNothing().when(rxCompleteInstructionOutboxHandler).constructAndOutboxCreateMoves(any(Instruction.class), anyString(), any(HttpHeaders.class));

        completeInstructionOutboxService.outboxCompleteInstruction(parent, instruction, "test", mock(SlotDetails.class),
                MockRxHttpHeaders.getHeaders());


        Mockito.verify(rxInstructionHelperService).persist(any(), any(), anyString());

    }

    @Test
    public void testPendingContainers_createContainer() throws Exception {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");

        File gdmPackResponseFile = new ClassPathResource("currentNodePallet.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        EpcisPostingService service = new EpcisPostingService() {
            @Override
            public void publishSerializedData(Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {

            }
        };


        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.when(instructionFactory.getEpcisPostingService(anyString())).thenReturn(service);


        completeInstructionOutboxService.pendingContainers("123", MockRxHttpHeaders.getHeaders());

        Mockito.verify(instructionPersisterService, times(1)).getInstructionById(anyLong());

    }

    @Test
    public void testProcessGdmUnits() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodePallet.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Container container = mockResponseForGetParentContainer("1234",  "123", 2);

        Mockito.when(rxDeliveryServiceImpl.getUnitLevelContainers(any(),  any())).thenReturn(ssccScanResponse);

        ReflectionTestUtils.invokeMethod(
                completeInstructionOutboxService, "processGdmUnits", container, MockRxHttpHeaders.getHeaders() );

        Mockito.verify(rxDeliveryServiceImpl, times(1)).getUnitLevelContainers(any(), any());
    }

    @Test
    public void testPendingContainers_existingContainer() throws Exception {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");

        File gdmPackResponseFile = new ClassPathResource("currentNodePallet.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        EpcisPostingService service = new EpcisPostingService() {
            @Override
            public void publishSerializedData(Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {

            }
        };


        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.when(instructionFactory.getEpcisPostingService(anyString())).thenReturn(service);


        completeInstructionOutboxService.pendingContainers("123", MockRxHttpHeaders.getHeaders());

        Mockito.verify(instructionPersisterService, times(1)).getInstructionById(anyLong());

    }




    @Test
    public void testEachesDetail() throws ReceivingException {
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        Mockito.when(outboxEventSinkService.saveEvent(any())).thenReturn(true);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        completeInstructionOutboxService.eachesDetail(mockResponseForGetParentContainer("1234",  "123", 2), MockRxHttpHeaders.getHeaders());

        Mockito.verify(outboxEventSinkService, times(1)).saveEvent(any());
    }

    @Test
    public void testEachesDetail_none_rxbuildcontainerType() throws ReceivingException, IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodePallet.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        container.getContainerMiscInfo().put(RxConstants.INSTRUCTION_CODE, "test_instruction");
        Mockito.when(outboxEventSinkService.saveEvent(any())).thenReturn(true);
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.when(rxDeliveryServiceImpl.getUnitLevelContainers(any(),  any())).thenReturn(ssccScanResponse);
        completeInstructionOutboxService.eachesDetail(container, MockRxHttpHeaders.getHeaders());

        Mockito.verify(outboxEventSinkService, times(1)).saveEvent(any());
    }

    @Test
    public void testGetCurrentAndSiblings() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodePallet.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(KEY_SSCC, "test_sscc");
        map.put(KEY_GTIN, "test_gtin");
        map.put(KEY_SERIAL, "1234");
        map.put(KEY_LOT, "test_lot");

        SsccScanResponse output = completeInstructionOutboxService.getCurrentAndSiblings(map, MockRxHttpHeaders.getHeaders(), "1234");
        Assert.assertNotNull(output);
    }

    @Test
    public void testGetUnitLevelContainers() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodePallet.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Container container = mockResponseForGetParentContainer("1234",  "123", 2);

        Mockito.when(rxDeliveryServiceImpl.getUnitLevelContainers(any(),  any())).thenReturn(ssccScanResponse);

        SsccScanResponse output = completeInstructionOutboxService.getUnitLevelContainers(container, MockRxHttpHeaders.getHeaders());
        Assert.assertNotNull(output);


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

    @Test
    public void testPendingContainers_createContainer_notMatchingGtinSerial() throws Exception {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");

        File gdmPackResponseFile = new ClassPathResource("currentAndSiblings.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        EpcisPostingService service = new EpcisPostingService() {
            @Override
            public void publishSerializedData(Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {

            }
        };


        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        container.getChildContainers().stream().forEach(cnt -> cnt.getContainerItems()
                .stream().forEach(containerItem -> {containerItem.setGtin("2345"); containerItem.setSerial("23556");}));
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.when(instructionFactory.getEpcisPostingService(anyString())).thenReturn(service);


        completeInstructionOutboxService.pendingContainers("123", MockRxHttpHeaders.getHeaders());

        Mockito.verify(instructionPersisterService, times(1)).getInstructionById(anyLong());

    }

    @Test
    public void testPendingContainers_createContainer_matchingGtinSerial() throws Exception {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");

        File gdmPackResponseFile = new ClassPathResource("currentAndSiblings.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        EpcisPostingService service = new EpcisPostingService() {
            @Override
            public void publishSerializedData(Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {

            }
        };

        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        container.getChildContainers().stream().forEach(cnt -> cnt.getContainerItems()
                .stream().forEach(containerItem -> {containerItem.setGtin("2345"); containerItem.setSerial("23556");}));
        Container childContainer = container.getChildContainers().stream().findFirst().get();
        childContainer.getContainerItems()
                .stream().forEach(containerItem -> {containerItem.setGtin("40301680146308"); containerItem.setSerial("10438777795106");});
        Mockito.when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean())).thenReturn(container);
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.doNothing().when(containerService).enrichContainerForDcfin(any(), any());
        Mockito.when(instructionFactory.getEpcisPostingService(anyString())).thenReturn(service);


        completeInstructionOutboxService.pendingContainers("123", MockRxHttpHeaders.getHeaders());

        Mockito.verify(instructionPersisterService, times(1)).getInstructionById(anyLong());

    }
}