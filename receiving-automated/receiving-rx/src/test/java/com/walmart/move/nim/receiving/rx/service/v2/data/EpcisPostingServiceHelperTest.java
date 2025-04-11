package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AdditionalInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.ProcessingInfoRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.GMT_OFFSET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.testng.Assert.*;

public class EpcisPostingServiceHelperTest {

    @Mock private AppConfig appConfig;
    @Mock private OutboxConfig outboxConfig;
    @Mock private ProcessingInfoRepository processingInfoRepository;
    @Mock private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
    @Mock private OutboxEventSinkService outboxEventSinkService;

    @InjectMocks private EpcisPostingServiceHelper epcisPostingServiceHelper;

    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(epcisPostingServiceHelper, "gson", gson);

    }
    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }
    @AfterMethod
    public void tearDown() {
        Mockito.reset(appConfig);
        Mockito.reset(outboxConfig);
        Mockito.reset(processingInfoRepository);
        Mockito.reset(rxCompleteInstructionOutboxHandler);
        Mockito.reset(outboxEventSinkService);
    }

    @Test
    public void testLoadData() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        SsccScanResponse.Container currentContainer = getgdmContainer();
        SsccScanResponse.Container rootContainer = getgdmContainer();
        currentContainer.setParentId("123");
        currentContainer.setGtin("00800109399919082945");
        currentContainer.setSerial("testSerialChild");
        currentContainer.setExpiryDate("261231");
        currentContainer.setLotNumber("testLot");
        rootContainer.setId("123");
        rootContainer.setParentId("123");


        doReturn("{\n" + "  \"32987\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();



        // GIVEN: ROOT CONTAINER SGTIN is not empty
        rootContainer.setGtin("00800109399919082945");
        rootContainer.setSerial("testSerialRoot");
        rootContainer.setExpiryDate("261231");
        rootContainer.setLotNumber("testLot");
        rootContainer.setSscc(null);
        AdditionalInfo additionalInfo = new AdditionalInfo();
        additionalInfo.setContainers(Arrays.asList(rootContainer));
        deliveryDocument.setGdmCurrentNodeDetail(new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(currentContainer), additionalInfo
        ));
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        EpcisPostingServiceHelper.EpcisPostingData epcisPostingData = epcisPostingServiceHelper.loadData(instruction, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(epcisPostingData);
        Assert.assertEquals(epcisPostingData.getFullGln(),  "0078742027753");
        Assert.assertEquals(epcisPostingData.getSsccOrSgtin(), "(01)00800109399919082945(21)testSerialRoot");

        // GIVEN: ROOT CONTAINER SSCC IS NOT EMPTY
        rootContainer.setSscc("C06085000020338321");
        rootContainer.setGtin(null);
        additionalInfo.setContainers(Arrays.asList(rootContainer));
        deliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(currentContainer), additionalInfo
        ));
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        EpcisPostingServiceHelper.EpcisPostingData epcisPostingData1 = epcisPostingServiceHelper.loadData(instruction, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(epcisPostingData1);
        Assert.assertEquals(epcisPostingData1.getFullGln(),  "0078742027753");
        Assert.assertEquals(epcisPostingData1.getSsccOrSgtin(), "(00)C06085000020338321");

        // GIVEN: CURRENT CONTAINER GTIN IS NOT EMPTY AND ROOT NODE CONTAINER SSCC and SGTIN ARE EMPTY
        rootContainer.setSscc(null);
        rootContainer.setGtin(null);
        currentContainer.setSscc(null);
        additionalInfo.setContainers(Arrays.asList(rootContainer));
        deliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(currentContainer), additionalInfo
        ));
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        EpcisPostingServiceHelper.EpcisPostingData epcisPostingData3 = epcisPostingServiceHelper.loadData(instruction, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(epcisPostingData3);
        Assert.assertEquals(epcisPostingData3.getFullGln(),  "0078742027753");
        Assert.assertEquals(epcisPostingData3.getSsccOrSgtin(), "(01)00800109399919082945(21)testSerialChild");

        // GIVEN: CURRENT CONTAINER SSCC IS NOT EMPTY AND ROOT NODE CONTAINER SSCC and SGTIN ARE EMPTY
        rootContainer.setSscc(null);
        rootContainer.setGtin(null);
        currentContainer.setSscc("B06085000020338321");
        currentContainer.setGtin(null);
        additionalInfo.setContainers(Arrays.asList(rootContainer));
        deliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(currentContainer), additionalInfo
        ));
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        EpcisPostingServiceHelper.EpcisPostingData epcisPostingData4 = epcisPostingServiceHelper.loadData(instruction, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(epcisPostingData4);
        Assert.assertEquals(epcisPostingData4.getFullGln(),  "0078742027753");
        Assert.assertEquals(epcisPostingData4.getSsccOrSgtin(), "(00)B06085000020338321");

    }

   // TODO: REVISE TEST CASE IN FUTURE AS THERE IS NO ASSERTION BECAUSE OF VOID RETURN TYPE
    @Test
    public void testOutboxEpcisEvent() {
        EpcisRequest epcisRequest = new EpcisRequest();
        epcisRequest.setIch(true);
        epcisRequest.setValidationPerformed(true);
        epcisRequest.setEventTimeZoneOffset(GMT_OFFSET);
        epcisRequest.setReadPoint("MockLocation");
        epcisRequest.setBizLocation("MockLocation");

        Mockito.when(outboxEventSinkService.saveEvent(any(OutboxEvent.class))).thenReturn(true);
        epcisPostingServiceHelper.outboxEpcisEvent(epcisRequest, MockHttpHeaders.getHeaders(),
                "gdmRootContainerId", "gdmContainerId",
                Instant.now());
        // VERIFY THAT saveEvent method is called.
        Mockito.verify(outboxEventSinkService, times(1)).saveEvent(any(OutboxEvent.class));
    }

    // TODO: REVISE TEST CASE AS THERE IS NO ASSERTION BECAUSE OF VOID RETURN TYPE
    @Test
    public void testOutboxEpcisEvents() {
        Set<EpcisRequest> requests = new HashSet<>();
        EpcisRequest epcisRequest = new EpcisRequest();
        epcisRequest.setIch(true);
        epcisRequest.setValidationPerformed(true);
        epcisRequest.setEventTimeZoneOffset(GMT_OFFSET);
        epcisRequest.setReadPoint("MockLocation");
        epcisRequest.setBizLocation("MockLocation");
        requests.add(epcisRequest);

        Mockito.when(outboxEventSinkService.saveEvent(any(OutboxEvent.class))).thenReturn(true);
        epcisPostingServiceHelper.outboxEpcisEvents(requests, "clubbed", MockHttpHeaders.getHeaders(), "gdmRootContainerId", Instant.now());

        // VERIFY THAT saveEvent method is called.
        Mockito.verify(outboxEventSinkService, times(1)).saveEvent(any(OutboxEvent.class));
    }

    private SsccScanResponse.Container getgdmContainer() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("gdmContainer.json").getFile();
        SsccScanResponse.Container gdmContainer =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.Container.class);

       return gdmContainer;
    }
}