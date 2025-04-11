package com.walmart.move.nim.receiving.rx.service.v2.posting;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProcessingInfo;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.ProcessingInfoRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.mock.RxMockContainer;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.v2.data.EpcisPostingServiceHelper;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.GMT_OFFSET;
import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class CaseReceivedPostingServiceTest {

    @Mock private RxManagedConfig rxManagedConfig;
    @Mock private ProcessingInfoRepository processingInfoRepository;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private EpcisPostingServiceHelper epcisPostingServiceHelper;
    @InjectMocks private CaseReceivedPostingService caseReceivedPostingService;

    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(caseReceivedPostingService, "gson", gson);
        ReflectionTestUtils.setField(epcisPostingServiceHelper, "gson", gson);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterMethod
    public void tearDown() {
        Mockito.reset(rxManagedConfig);
        Mockito.reset(processingInfoRepository);
        Mockito.reset(rxDeliveryServiceImpl);
        Mockito.reset(epcisPostingServiceHelper);

    }

    @Test
    public void testPublishSerializedData() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());

        Container cContainer = RxMockContainer.getChildContainer();
        cContainer.setDeliveryNumber(1234L);
        Map<String, Object> containerMiscInfo = new HashMap<>();
        containerMiscInfo.put("gdmContainerId", "gdmContainerId123");
        cContainer.setContainerMiscInfo(containerMiscInfo);

        Container mockContainer = RxMockContainer.getContainer();
        Set<Container> childContainer = new HashSet<>();
        childContainer.add(cContainer);
        mockContainer.setChildContainers(childContainer);

        Mockito.when(rxManagedConfig.getAttpEventLagTimeInterval()).thenReturn(1);
        Mockito.when(processingInfoRepository.save(any(ProcessingInfo.class))).thenReturn(new ProcessingInfo());

        EpcisRequest epcisRequest = new EpcisRequest();
        epcisRequest.setIch(true);
        epcisRequest.setValidationPerformed(true);
        epcisRequest.setEventTimeZoneOffset(GMT_OFFSET);
        epcisRequest.setReadPoint("MockLocation");
        epcisRequest.setBizLocation("MockLocation");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        Content content = instruction.getChildContainers().get(0).getContents().get(0);
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                deliveryDocument.getGdmCurrentNodeDetail();
        SsccScanResponse.Container gdmRootContainer =
                gdmCurrentNodeDetail.getAdditionalInfo().getContainers().get(0);
        String gdmRootContainerId = gdmRootContainer.getId();


        EpcisPostingServiceHelper.EpcisPostingData epcisPostingData = EpcisPostingServiceHelper.EpcisPostingData.builder()
                .epcisRequest(epcisRequest)
                .deliveryDocument(deliveryDocument)
                .deliveryDocumentLine(deliveryDocumentLine)
                .content(content)
                .gdmRootContainer(gdmRootContainer)
                .gdmRootContainerId(gdmRootContainerId)
                .gdmCurrentContainerId("testCurrentContainerId123")
                .processingInfo(null)
                .systemContainerId("testSystemContainerId")
                .ssccOrSgtin("gtin")
                .fullGln("testFullGtin")
                .bizLocation("testLocation")
                .build();

        Mockito.when(epcisPostingServiceHelper.loadData(any(Instruction.class), any(HttpHeaders.class))).thenReturn(epcisPostingData);
        /*Mockito.when(epcisPostingServiceHelper.loadData(any(Instruction.class), any(HttpHeaders.class))).thenAnswer(
                new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        Object[] args = invocationOnMock.getArguments();
                        Instruction instruction1 = (Instruction) args[0];
                        HttpHeaders httpHeaders = (HttpHeaders) args[1];

                        Object result = invocationOnMock.callRealMethod();
                        return result;
                    }
                }
        );*/

        Mockito.when(rxDeliveryServiceImpl.getUnitLevelContainers(any(), any())).thenReturn(ssccScanResponse);
        Mockito.doNothing().when(epcisPostingServiceHelper).outboxEpcisEvents(anySet(), anyString(), any(HttpHeaders.class), anyString(), any(Instant.class));
        caseReceivedPostingService.publishSerializedData(mockContainer, instruction, MockHttpHeaders.getHeaders());
        Mockito.verify(rxDeliveryServiceImpl, Mockito.times(1)).getUnitLevelContainers(any(), any());

    }
}