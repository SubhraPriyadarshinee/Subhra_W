package com.walmart.move.nim.receiving.controller;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.reporting.service.ReconServiceSecondary;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.libs.commons.lang3.time.DateFormatUtils;
import java.util.*;
import org.apache.commons.lang3.time.DateUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ReconControllerTest extends ReceivingControllerTestBase {

  @Mock private ContainerService containerService;
  @Mock private InstructionService instructionService;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private PrintJobService printJobService;
  @Mock private ReceiptService receiptService;
  @Mock private DCFinService dcFinService;
  @Mock private ReceiptPublisher receiptPublisher;

  @Mock private KafkaTemplate secureKafkaTemplate;
  @Mock private EpcisService epcisService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private RxInstructionService rxInstructionService;

  @Mock private ContainerPersisterService containerPersisterService;

  private Gson gson = new Gson();
  @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;
  @InjectMocks private ReconController reconController;

  @Mock private ReconServiceSecondary reconServiceSecondary;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private MockMvc mockMvc;
  private Container container;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    reconController = new ReconController();
    ReflectionTestUtils.setField(reconController, "containerService", containerService);
    ReflectionTestUtils.setField(reconController, "instructionService", instructionService);
    ReflectionTestUtils.setField(reconController, "printJobService", printJobService);
    ReflectionTestUtils.setField(reconController, "receiptService", receiptService);
    ReflectionTestUtils.setField(reconController, "jmsPublisher", jmsPublisher);
    ReflectionTestUtils.setField(reconController, "dcFinService", dcFinService);
    ReflectionTestUtils.setField(reconController, "gson", gson);
    ReflectionTestUtils.setField(reconController, "reconService", reconServiceSecondary);
    ReflectionTestUtils.setField(reconController, "receiptPublisher", receiptPublisher);
    ReflectionTestUtils.setField(reconController, "epcisService", epcisService);
    ReflectionTestUtils.setField(
        reconController, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(reconController, "secureKafkaTemplate", secureKafkaTemplate);
    ReflectionTestUtils.setField(reconController, "rxInstructionService", rxInstructionService);
    ReflectionTestUtils.setField(
        reconController, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(
        reconController, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(reconController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void init() {
    ReflectionTestUtils.setField(
        reconController, "kafkaPublishRestrictedTopics", Arrays.asList("test-topic"));
  }

  @AfterMethod
  public void tearDown() {
    reset(containerService);
    reset(instructionService);
    reset(jmsPublisher);
    reset(printJobService);
    reset(receiptService);
    reset(reconServiceSecondary);
    reset(receiptPublisher);
    reset(containerPersisterService);
    reset(tenantSpecificConfigReader);
    reset(secureKafkaTemplate);
  }

  @Test
  public void testPostReceiptGivenTrackingId() throws ReceivingException {

    container = MockContainer.getContainerInfo();
    container.setParentTrackingId(null);
    container.setCompleteTs(new Date());

    when(containerService.getContainerByTrackingId("a329870000000000000000001"))
        .thenReturn(container);
    when(instructionService.getConsolidatedContainerAndPublishContainer(any(), any(), anyBoolean()))
        .thenReturn(container);
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/recon/container/a329870000000000000000001/receipts")
                  .headers(httpHeaders))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void testGetAllContainersByDelivery() throws Exception {
    when(containerService.getContainerByDeliveryNumber(111223344L))
        .thenReturn(Collections.singletonList(MockContainer.getContainerInfo()));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/containers/111223344")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(containerService, times(1)).getContainerByDeliveryNumber(111223344L);
  }

  @Test
  public void testGetAllContainersForInstruction() throws Exception {
    when(containerService.getContainerByInstruction(1L))
        .thenReturn(Collections.singletonList(MockContainer.getContainerInfo()));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/containers/instruction/1")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(containerService, times(1)).getContainerByInstruction(1L);
  }

  @Test
  public void testGetAllContainersByTime() throws Exception {
    when(containerService.getContainerByTime(10))
        .thenReturn(Collections.singletonList(MockContainer.getContainerInfo()));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/containers/bytime/10")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(containerService, times(1)).getContainerByTime(10);
  }

  @Test
  public void testGetAllContainersByTrackingId() throws Exception {
    when(containerService.getContainerByTrackingId("a1000000000000000"))
        .thenReturn(MockContainer.getContainerInfo());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/container/bytrackingid/a1000000000000000")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(containerService, times(1)).getContainerByTrackingId("a1000000000000000");
  }

  @Test
  public void testGetPrintJobForInstruction() throws Exception {
    PrintJob printJob = new PrintJob();
    when(printJobService.getPrintJobByInstruction(1L))
        .thenReturn(Collections.singletonList(printJob));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/printjobs/instruction/1")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(printJobService, times(1)).getPrintJobByInstruction(1L);
  }

  @Test
  public void testGetPrintJobForDelivery() throws Exception {
    PrintJob printJob = new PrintJob();
    when(printJobService.getPrintJobsByDeliveryNumber(111223344L))
        .thenReturn(Collections.singletonList(printJob));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/printjobs/111223344")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(printJobService, times(1)).getPrintJobsByDeliveryNumber(111223344L);
  }

  @Test
  public void testGetReceiptForDelivery() throws Exception {
    Receipt receipt = new Receipt();
    when(receiptService.findByDeliveryNumber(111223344L))
        .thenReturn(Collections.singletonList(receipt));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/receipts/111223344")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(receiptService, times(1)).findByDeliveryNumber(111223344L);
  }

  @Test
  public void testPublishMessageTopic() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/publishMessage/TOPIC.TEST/publish")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" + "   \"test\":\"test\"\n" + "}"))
        .andExpect(status().isAccepted());
    verify(jmsPublisher, times(1)).publish(eq("TOPIC/TEST"), any(), eq(Boolean.TRUE));
  }

  @Test
  public void testPublishMessageQueue() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/publishMessage/QUEUE.TEST/publish")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" + "   \"test\":\"test\"\n" + "}"))
        .andExpect(status().isAccepted());
    verify(jmsPublisher, times(1))
        .publish(eq("QUEUE.TEST"), any(ReceivingJMSEvent.class), eq(Boolean.TRUE));
  }

  @Test
  public void testInstructionByMessageId() throws Exception {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(instructionService.getInstructionByMessageId("a-b-c-d", headers))
        .thenReturn(new InstructionResponseImplNew());
    mockMvc
        .perform(MockMvcRequestBuilders.get("/recon/instruction/a-b-c-d").headers(headers))
        .andExpect(status().isOk());
    verify(instructionService, times(1)).getInstructionByMessageId("a-b-c-d", headers);
  }

  @Test
  public void testPostReceiptsByTrackingId() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/container/a10000000000/purchases")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    verify(dcFinService, times(1)).postReceiptsToDCFin("a10000000000");
  }

  @Test
  public void testPostReceiptsByDelivery() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/delivery/11223344/container/purchases")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    verify(dcFinService, times(1)).postReceiptsForDelivery(11223344L);
  }

  @Test
  public void testGetInstruction() throws Exception {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(instructionService.getInstructionAndContainerDetailsForWFT("a100000000", "100", headers))
        .thenReturn(new WFTResponse());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/WFT/search?trackingId=a100000000&instructionId=100")
                .headers(headers))
        .andExpect(status().isOk());
    verify(instructionService, times(1))
        .getInstructionAndContainerDetailsForWFT("a100000000", "100", headers);
  }

  @Test
  public void testGetReconciledDataByTime() throws Exception {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    DcFinReconciledDate dcFinReconciledDate = new DcFinReconciledDate();
    dcFinReconciledDate.setContainerId("a10000000");
    when(reconServiceSecondary.getReconciledDataSummaryByTime(any(), any(), any()))
        .thenReturn(Collections.singletonList(dcFinReconciledDate));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/purchaseDetails")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\n"
                        + "  \"fromDate\": \"2020-08-21T08:00:00Z\",\n"
                        + "  \"toDate\": \"2020-08-22T08:00:00Z\"\n"
                        + "}")
                .headers(headers))
        .andExpect(status().isOk());
    verify(reconServiceSecondary, times(1)).getReconciledDataSummaryByTime(any(), any(), any());
  }

  @Test
  public void testReceivedQtyGivenActivityNameAndTime() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/recon/receivedQty")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{ \"activityName\" : \"PBYL\","
                          + "    \"fromDate\" : \"2020-02-02T22:30:00Z\","
                          + "    \"toDate\" : \"2020-07-11T22:30:00Z\" }")
                  .headers(headers))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void testReprintLabels() throws Exception {

    when(reconServiceSecondary.postLabels(any(), any()))
        .thenReturn(
            ReprintLabelResponse.builder()
                .trackingIds(Arrays.asList("a328180000200000002820817"))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/container/re-label")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\n"
                        + "    \"trackingId\": \"a328180000200000002820815\",\n"
                        + "    \"deliveryNumber\": 1234567,\n"
                        + "    \"fromDate\": \"2020-11-10T06:30:02.074Z\",\n"
                        + "    \"toDate\": \"2020-11-10T06:30:02.074Z\"\n"
                        + "}"))
        .andExpect(status().isOk());
    ArgumentCaptor<ReprintLabelRequest> captor = ArgumentCaptor.forClass(ReprintLabelRequest.class);

    verify(reconServiceSecondary, times(1)).postLabels(captor.capture(), any());
    assertEquals(captor.getValue().getDeliveryNumber(), Long.valueOf(1234567));
    assertEquals(captor.getValue().getTrackingId(), "a328180000200000002820815");
    assertNotNull(captor.getValue().getFromDate());
    assertNotNull(captor.getValue().getToDate());
  }

  @Test
  public void testReprintLabelsNoContent() throws Exception {

    when(reconServiceSecondary.postLabels(any(), any()))
        .thenReturn(ReprintLabelResponse.builder().build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/container/re-label")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\n"
                        + "    \"trackingId\": \"a328180000200000002820815\",\n"
                        + "    \"deliveryNumber\": 1234567,\n"
                        + "    \"fromDate\": \"2020-11-10T06:30:02.074Z\",\n"
                        + "    \"toDate\": \"2020-11-10T06:30:02.074Z\"\n"
                        + "}"))
        .andExpect(status().isNoContent());
    ArgumentCaptor<ReprintLabelRequest> captor = ArgumentCaptor.forClass(ReprintLabelRequest.class);

    verify(reconServiceSecondary, times(1)).postLabels(captor.capture(), any());
    assertEquals(captor.getValue().getDeliveryNumber(), Long.valueOf(1234567));
    assertEquals(captor.getValue().getTrackingId(), "a328180000200000002820815");
    assertNotNull(captor.getValue().getFromDate());
    assertNotNull(captor.getValue().getToDate());
  }

  @Test
  public void testPublishContainerUpdate() throws Exception {
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/publish/updates/a32987000000000001")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(receiptPublisher, times(1))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
  }

  @Test
  public void testPublishSerializedData() throws Exception {
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(new Instruction()).when(instructionPersisterService).getInstructionById(anyLong());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(
                    "/recon/publish/serializedData?instructionIds=103704,103705")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(epcisService, times(2))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testPublishMessageOnKafka() throws Exception {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(
                    "/recon/publishMessage/kafka/TOPIC_RECEIVE_CONTAINERS_DEV/publish")
                .headers(MockHttpHeaders.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" + "   \"test\":\"test\"\n" + "}"))
        .andExpect(status().isAccepted());
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void test_linkRecentShipments() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      InstructionRequest mockInstructionRequest = new InstructionRequest();
      mockInstructionRequest.setDeliveryNumber("1234");
      doReturn(Optional.empty())
          .when(rxInstructionService)
          .checkForLatestShipments(any(InstructionRequest.class), any(HttpHeaders.class), any());
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/recon/linkRecentShipments")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(new Gson().toJson(mockInstructionRequest))
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());

    } catch (Exception e) {
      assertNull(e);
    }
  }

  @Test
  public void test_resetJmsRetryCount() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      JsonObject activityWithTimeRangeRequest = new JsonObject();
      activityWithTimeRangeRequest.addProperty(
          "fromDate",
          DateFormatUtils.format(DateUtils.addDays(new Date(), -2), "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
      activityWithTimeRangeRequest.addProperty(
          "toDate", DateFormatUtils.format(new Date(), "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
      activityWithTimeRangeRequest.addProperty("activityName", "REST");

      doNothing().when(reconServiceSecondary).resetJmsRetryCount(any(JmsRetryResetRequest.class));
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/recon/jmsretry/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(new Gson().toJson(activityWithTimeRangeRequest))
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());

      verify(reconServiceSecondary, times(1)).resetJmsRetryCount(any(JmsRetryResetRequest.class));
    } catch (Exception e) {
      assertNull(e);
    }
  }

  @Test
  public void test_resetJmsRetryCountById() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      JsonObject activityWithTimeRangeRequest = new JsonObject();
      activityWithTimeRangeRequest.addProperty("activityName", "REST");
      JsonArray ids = new JsonArray();
      ids.add(1l);
      activityWithTimeRangeRequest.add("ids", ids);

      doNothing().when(reconServiceSecondary).resetJmsRetryCount(any(JmsRetryResetRequest.class));
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/recon/jmsretry/resetById")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(new Gson().toJson(activityWithTimeRangeRequest))
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());

      verify(reconServiceSecondary, times(1))
          .resetJmsRetryCountById(any(ActivityWithIdRequest.class));
    } catch (Exception e) {
      assertNull(e);
    }
  }

  @Test
  public void testGetTrackingIdByItemNumber() throws Exception {
    when(containerService.getContainerByItemNumber(anyLong())).thenReturn(new ContainerItem());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/recon/container/byitem/9398504")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());

    verify(containerService, times(1)).getContainerByItemNumber(anyLong());
  }

  @Test
  public void testPublishMessageOnKafkaWithRestrictedTopics() throws Exception {
    String topicName = "test-topic";
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false))
        .thenReturn(true);
    String url = "/recon/publishMessage/kafka/" + topicName + "/publish";
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post(url)
                  .headers(httpHeaders)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\n" + "   \"test\":\"test\"\n" + "}"))
          .andExpect(status().is5xxServerError());
    } catch (Exception ex) {
      assertTrue(
          ex.getMessage()
              .equalsIgnoreCase(
                  "Request processing failed; nested exception is com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException: Publish to kafka topic test-topic is restricted"));
    }
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false);
  }

  @Test
  public void testPublishMessageOnKafkaWithRestrictedTopicNotExist() throws Exception {
    ReflectionTestUtils.setField(
        reconController, "kafkaPublishRestrictedTopics", Arrays.asList(""));
    String topicName = "test-topic";
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false))
        .thenReturn(true);
    String url = "/recon/publishMessage/kafka/" + topicName + "/publish";
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(url)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" + "   \"test\":\"test\"\n" + "}"))
        .andExpect(status().isAccepted());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false);
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublishMessageOnKafkaWithRestrictedTopicFeatureDisabled() throws Exception {
    String topicName = "test-topic";
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    String url = "/recon/publishMessage/kafka/" + topicName + "/publish";
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(url)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" + "   \"test\":\"test\"\n" + "}"))
        .andExpect(status().isAccepted());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false);
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublishMessageOnKafkaWithRestrictedTopicNotMatched() throws Exception {
    ReflectionTestUtils.setField(
        reconController, "kafkaPublishRestrictedTopics", Arrays.asList("test2-topic"));
    String topicName = "test-topic";
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false))
        .thenReturn(true);
    String url = "/recon/publishMessage/kafka/" + topicName + "/publish";
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(url)
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" + "   \"test\":\"test\"\n" + "}"))
        .andExpect(status().isAccepted());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
            false);
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testTriggerBackoutPrinting() throws Exception {
    Container container1 = new Container();
    String trackingId = "454634643548704";
    when(containerService.updateContainerInventoryStatus(anyString(), anyString()))
        .thenReturn(container1);
    when(containerService.cancelContainers(any(), any())).thenReturn(new ArrayList<>());

    reconController.triggerBackoutPrinting(trackingId, httpHeaders);

    verify(containerService, times(1)).updateContainerInventoryStatus(anyString(), anyString());
    verify(containerService, times(1)).cancelContainers(any(), any());
  }
}
