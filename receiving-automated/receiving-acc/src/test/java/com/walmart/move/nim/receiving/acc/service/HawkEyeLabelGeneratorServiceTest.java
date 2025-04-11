package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.message.publisher.JMSLabelDataPublisher;
import com.walmart.move.nim.receiving.acc.mock.data.MockLabelData;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.contract.prelabel.LabelingService;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HawkEyeLabelGeneratorServiceTest extends ReceivingTestBase {

  @InjectMocks private HawkEyeLabelGeneratorService hawkEyeLabelGeneratorService;

  @Mock private LPNCacheService lpnCacheService;

  @Mock private LabelDataService labelDataService;

  @Mock private DeliveryService deliveryService;

  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;

  @Mock private LabelingService labelingService;

  @Mock private AppConfig appConfig;

  @Mock private ACCManagedConfig accManagedConfig;

  @Mock private JmsPublisher jmsPublisher;

  @Mock private AsyncLabelPersisterService asyncLabelPersisterService;

  @Mock private LabelingHelperService labelingHelperService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private JMSLabelDataPublisher jmsLabelDataPublisher;

  private DeliveryEvent deliveryEvent;

  private DeliveryDetails deliveryDetails;

  private final String[] lpnSet1 = {
    "c32987000000000000000001",
    "c32987000000000000000002",
    "c32987000000000000000003",
    "c32987000000000000000004",
    "c32987000000000000000005",
    "c32987000000000000000006"
  };

  private final String[] lpnSet2 = {"c32987000000000000000007"};

  private String hawkEyeLabelDataPayloadSchemaFilePath;

  private final Gson gson = new Gson();

  @BeforeClass
  public void initMocks() throws ReceivingException {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(hawkEyeLabelGeneratorService, "gson", gson);
    deliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventStatus(EventTargetStatus.IN_PROGRESS)
            .deliveryNumber(94769060L)
            .url("https://delivery.test")
            .retriesCount(0)
            .build();
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");

    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();

      hawkEyeLabelDataPayloadSchemaFilePath =
          new File(
                  "../../receiving-test/src/main/resources/jsonSchema/hawkEyeLabelDataPayload.json")
              .getCanonicalPath();

      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
    } catch (IOException e) {
      assert (false);
    }
  }

  @BeforeMethod
  public void beforeMethod() throws ReceivingException {
    doReturn(Arrays.asList(lpnSet1)).when(lpnCacheService).getLPNSBasedOnTenant(anyInt(), any());
    when(labelDataService.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn("")
        .when(labelingService)
        .getFormattedLabelData(any(), any(), anyString(), anyString(), anyString());
    doReturn(.15f).when(accManagedConfig).getExceptionLPNThreshold();
    doReturn(null).when(accManagedConfig).getRcvBaseUrl();
    doReturn("dummy tcl template").when(accManagedConfig).getAccPrintableZPL();
    doReturn(null).when(appConfig).getGdmBaseUrl();
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
    doReturn(jmsLabelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
  }

  @AfterMethod
  public void resetMocks() {
    reset(lpnCacheService);
    reset(labelDataService);
    reset(deliveryService);
    reset(deliveryEventPersisterService);
    reset(labelingService);
    reset(asyncLabelPersisterService);
    reset(jmsPublisher);
    reset(labelingHelperService);
    reset(tenantSpecificConfigReader);
    reset(jmsLabelDataPublisher);
  }

  private DeliveryDetails getDeliveryDetailsFromPath(String path) {
    DeliveryDetails deliveryDetails = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
    } catch (IOException e) {
      return null;
    }
    return deliveryDetails;
  }

  @Test
  public void testGenerateGenericLabel_HappyFlow_LabelingServiceShouldNotBeCalled()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(true).when(accManagedConfig).isLabelPostEnabled();
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    hawkEyeLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelingService, times(0))
        .getFormattedLabelData(any(), any(), anyString(), anyString(), anyString());
    verify(labelDataService, times(1)).saveAll(anyList());
    verify(asyncLabelPersisterService, times(1))
        .publishLabelDataToLabelling(any(), anyList(), any());
  }

  @Test
  public void testPublishACLLabelDataForDelivery() throws IOException, ReceivingException {
    DeliveryDetails deliveryDetails =
        getDeliveryDetailsFromPath(
            "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json");
    deliveryDetails.setDoorNumber("D102");
    doReturn(deliveryDetails).when(deliveryService).getDeliveryDetails(anyString(), anyLong());
    doReturn(
            Arrays.asList(
                MockLabelData.getMockHawkEyeLabelData(),
                MockLabelData.getMockHawkEyeExceptionLabelData()))
        .when(labelDataService)
        .getLabelDataByDeliveryNumberSortedBySeq(anyLong());
    doReturn(MockLabelData.getMockHawkEyeScanItem())
        .when(labelingHelperService)
        .buildHawkEyeScanItemFromLabelDataAndPoLine(
            anyLong(), any(DeliveryDocumentLine.class), any(LabelData.class));
    doReturn(MockLabelData.getHawkEyeFormattedLabels())
        .when(labelingHelperService)
        .buildHawkEyeFormattedLabel(any(LabelData.class), any(DeliveryDocumentLine.class));
    hawkEyeLabelGeneratorService.publishACLLabelDataForDelivery(
        94769060L, MockHttpHeaders.getHeaders());
    ArgumentCaptor<com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData> captor =
        ArgumentCaptor.forClass(
            com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData.class);
    verify(jmsLabelDataPublisher, times(1)).publish(captor.capture(), any());
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(hawkEyeLabelDataPayloadSchemaFilePath))),
            JacksonParser.writeValueAsString(captor.getValue())));
  }

  @Test
  public void testPublishACLLabelDataDelta() throws IOException {
    DeliveryDetails deliveryDetails =
        getDeliveryDetailsFromPath(
            "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json");
    doReturn(MockLabelData.getMockHawkEyeScanItem())
        .when(labelingHelperService)
        .buildHawkEyeScanItemFromLabelDataAndPoLine(
            anyLong(), any(DeliveryDocumentLine.class), any(LabelData.class));
    doReturn(MockLabelData.getHawkEyeFormattedLabels())
        .when(labelingHelperService)
        .buildHawkEyeFormattedLabel(any(LabelData.class), any(DeliveryDocumentLine.class));
    hawkEyeLabelGeneratorService.publishACLLabelDataDelta(
        Collections.singletonMap(
            deliveryDetails.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0),
            Collections.singletonList(MockLabelData.getMockHawkEyeLabelData())),
        94769060L,
        "",
        "322683",
        MockHttpHeaders.getHeaders());
    ArgumentCaptor<com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData> captor =
        ArgumentCaptor.forClass(
            com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData.class);
    verify(jmsLabelDataPublisher, times(1)).publish(captor.capture(), any());
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(hawkEyeLabelDataPayloadSchemaFilePath))),
            JacksonParser.writeValueAsString(captor.getValue())));
  }
}
