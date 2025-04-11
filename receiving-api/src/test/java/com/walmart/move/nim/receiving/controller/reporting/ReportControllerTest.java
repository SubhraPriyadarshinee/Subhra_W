package com.walmart.move.nim.receiving.controller.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.sso.SSOConstants;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.SSOService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.reporting.model.ReportData;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.Silent.class)
@AutoConfigureMockMvc
public class ReportControllerTest extends ReceivingControllerTestBase {
  private static final String PO = "poNum";
  private static final String PO_LINE = "poLineNum";
  private static final String DELIVERY_NUMBER = "delivery";

  @Autowired private MockMvc mockMvc;

  @InjectMocks private ReportController reportController;

  private Gson gson = new Gson();

  @Resource(name = ReceivingConstants.DEFAULT_REPORT_SERVICE)
  @Mock
  private ReportService reportService;

  @Autowired @Mock private InstructionPersisterService instructionPersisterService;

  @Autowired @Mock private ReceiptService receiptService;

  @Autowired @Mock private SSOService ssoService;

  @Autowired @Mock private TenantSpecificReportConfig tenantSpecificReportConfig;

  @Autowired private WebApplicationContext wac;

  private List<Instruction> instructionList = new ArrayList<>();
  private MultiValueMap<String, String> reportPathParams = new LinkedMultiValueMap<>();
  private List<Receipt> receipts;

  private final String fromdatetime = "1586768580000";
  private final String todatetime = "1586772180000";

  private final String purchaseReferenceNumber = "9876543210";
  private final Integer purchaseReferenceLineNumber = 1;
  private final String deliveryNumber = "12345678";

  @Autowired @MockBean private TenantSpecificConfigReader configUtils;

  @BeforeClass
  public void initMocks() {
    Instruction instruction1 = new Instruction();
    instruction1.setId(Long.valueOf("1"));
    instruction1.setContainer(null);
    instruction1.setChildContainers(null);
    instruction1.setCreateTs(new Date());
    instruction1.setCreateUserId("sysadmin");
    instruction1.setLastChangeTs(new Date());
    instruction1.setLastChangeUserId("sysadmin");
    instruction1.setDeliveryNumber(Long.valueOf("21119003"));
    instruction1.setGtin("00000943037204");
    instruction1.setInstructionCode("Build Container");
    instruction1.setInstructionMsg("Build the Container");
    instruction1.setItemDescription("HEM VALUE PACK (5)");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction1.setMove(null);
    instruction1.setPoDcNumber("32899");
    instruction1.setPrintChildContainerLabels(false);
    instruction1.setPurchaseReferenceNumber("9763140005");
    instruction1.setPurchaseReferenceLineNumber(1);
    instruction1.setProjectedReceiveQty(2);
    instruction1.setProviderId("DA");
    instruction1.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction1.setActivityName("DANonCon");
    instructionList.add(instruction1);

    Instruction instruction2 = new Instruction();
    instruction2.setId(Long.valueOf("2"));
    instruction2.setContainer(null);
    instruction2.setChildContainers(null);
    instruction2.setCreateTs(new Date());
    instruction2.setCreateUserId("sysadmin");
    instruction2.setLastChangeTs(new Date());
    instruction2.setLastChangeUserId("sysadmin");
    instruction2.setCompleteTs(new Date());
    instruction2.setCompleteUserId("sysadmin");
    instruction2.setDeliveryNumber(Long.valueOf("21119003"));
    instruction2.setGtin("00000943037194");
    instruction2.setInstructionCode("Build Container");
    instruction2.setInstructionMsg("Build the Container");
    instruction2.setItemDescription("HEM VALUE PACK (4)");
    instruction2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction2.setMove(null);
    instruction2.setPoDcNumber("32899");
    instruction2.setPrintChildContainerLabels(false);
    instruction2.setPurchaseReferenceNumber("9763140004");
    instruction2.setPurchaseReferenceLineNumber(1);
    instruction2.setProjectedReceiveQty(2);
    instruction2.setProviderId("DA");
    instruction2.setReceivedQuantity(2);
    instruction2.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setFirstExpiryFirstOut(Boolean.TRUE);
    instruction2.setActivityName("DANonCon");
    instructionList.add(instruction2);

    Receipt receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId("sysadmin");
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(21119003L);
    receipt.setDoorNumber("171");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140005");
    receipt.setQuantity(2);
    receipt.setQuantityUom("ZA");
    receipts = new ArrayList<>();
    receipts.add(receipt);

    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
  }

  @AfterMethod
  public void cleanup() {
    reportPathParams.clear();
    reset(ssoService);
  }

  @Test
  public void testViewReportData() throws Exception {
    Type type = new TypeToken<List<Pair<String, Object>>>() {}.getType();

    reportPathParams.add("fromdatetime", fromdatetime);
    reportPathParams.add("todatetime", todatetime);
    reportPathParams.add("isUTC", "true");

    ReportData expectedResponse = getMockReportData();

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    Mockito.when(
            reportService.populateReportData(
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.any(HttpHeaders.class)))
        .thenReturn(expectedResponse);
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/report/stats/data")
                    .headers(httpHeaders)
                    .params(reportPathParams)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Pair<String, Object>> actualResponse = gson.fromJson(response, type);

    assertEquals(actualResponse, expectedResponse.getStatisticsData());
  }

  @Test
  public void testSearchDeliveryByDeliveryNumber() throws Exception {

    String expectedResponse = "test delivery response";

    doReturn(reportService)
        .when(configUtils)
        .getConfiguredInstance(anyString(), anyString(), any());

    Mockito.when(
            reportService.getDeliveryDetailsForReport(Mockito.anyLong(), any(HttpHeaders.class)))
        .thenReturn(expectedResponse);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    String actualResponse =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/report/search/delivery/" + deliveryNumber)
                    .headers(httpHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(actualResponse, expectedResponse);
  }

  @Test
  public void testGetInstructions() throws Exception {
    Type instructionListType = new TypeToken<List<Instruction>>() {}.getType();

    reportPathParams.add(PO, purchaseReferenceNumber);
    reportPathParams.add(PO_LINE, purchaseReferenceLineNumber.toString());
    reportPathParams.add(DELIVERY_NUMBER, deliveryNumber);

    Mockito.when(
            instructionPersisterService.getInstructionByPoPoLineAndDeliveryNumber(
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyLong()))
        .thenReturn(instructionList);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    String actualResponseString =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/report/search/instructions")
                    .headers(httpHeaders)
                    .params(reportPathParams)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Instruction> actualResponse =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                new JsonDeserializer<Date>() {
                  public Date deserialize(
                      JsonElement jsonElement, Type type, JsonDeserializationContext context)
                      throws JsonParseException {
                    return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                  }
                })
            .create()
            .fromJson(actualResponseString, instructionListType);

    assertEquals(actualResponse, instructionList);
  }

  @Test
  public void testGetReceipts() throws Exception {
    Type receiptListType = new TypeToken<List<Receipt>>() {}.getType();

    reportPathParams.add(PO, purchaseReferenceNumber);
    reportPathParams.add(PO_LINE, purchaseReferenceLineNumber.toString());
    reportPathParams.add(DELIVERY_NUMBER, deliveryNumber);

    Mockito.when(receiptService.getReceiptsByAndPoPoLine("9876543210", 1)).thenReturn(receipts);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    String actualResponseString =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/report/search/receipts")
                    .headers(httpHeaders)
                    .params(reportPathParams)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Instruction> actualResponse =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                new JsonDeserializer<Date>() {
                  public Date deserialize(
                      JsonElement jsonElement, Type type, JsonDeserializationContext context)
                      throws JsonParseException {
                    return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                  }
                })
            .create()
            .fromJson(actualResponseString, receiptListType);

    assertEquals(actualResponse, receipts);
  }

  public ReportData getMockReportData() {

    List<Pair<String, Object>> statisticsData = new ArrayList<>();
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DELIVERIES_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_POS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_USERS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_CASES_RECEIVED_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_ITEMS_RECEIVED_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_LABELS_PRINTED_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DA_CON_PALLETS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DA_NON_CON_PALLETS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_SSTK_PALLETS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PBYL_PALLETS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_ACL_CASES_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PO_CON_PALLETS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_VTR_CONTAINERS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PROBLEM_PALLETS_STAT, 2.0d));
    statisticsData.add(
        new Pair<>(
            ReportingConstants.AVERAGE_NUMBER_OF_PALLETS_PER_DELIVERY_STAT,
            2.0d)); // Kept one decimal place after consulting PO
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DOCK_TAGS_STAT, 2.0d));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_PALLET_BUILD_TIME_STAT, 50.0d));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_DELIVERY_COMPLETION_TIME_STAT, 1.0d));
    statisticsData.add(new Pair<>(ReportingConstants.TOTAL_DELIVERY_COMPLETION_TIME_STAT, 1.0d));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_PO_PROCESSING_TIME_STAT, 1.0d));
    statisticsData.add(new Pair<>(ReportingConstants.TOTAL_PO_PROCESSING_TIME_STAT, 1.0d));

    ReportData reportData = new ReportData();
    reportData.setStatisticsData(statisticsData);
    return reportData;
  }

  @Test
  public void testLoadStatsPage_NoToken() throws Exception {
    when(ssoService.getRedirectUri()).thenReturn("sso-redirect-url");
    this.mockMvc
        .perform(MockMvcRequestBuilders.get("/report/stats"))
        .andExpect(redirectedUrl("sso-redirect-url"));
  }

  @Test
  public void testLoadStatsPage_InvalidToken() throws Exception {
    reportPathParams.add(SSOConstants.TOKEN_PARAM_NAME, "dummy-token");
    when(ssoService.validateToken(eq("dummy-token"))).thenReturn(null);
    when(ssoService.getRedirectUri()).thenReturn("sso-redirect-url");
    this.mockMvc
        .perform(MockMvcRequestBuilders.get("/report/stats").params(reportPathParams))
        .andExpect(redirectedUrl("sso-redirect-url"));
  }

  @Test
  public void testLoadStatsPage_ValidToken() throws Exception {
    reportPathParams.add(SSOConstants.TOKEN_PARAM_NAME, "dummy-token");
    when(ssoService.validateToken(eq("dummy-token"))).thenReturn(new Pair<>("j0d0007", "John Doe"));
    when(ssoService.getRedirectUri()).thenReturn("sso-redirect-url");
    this.mockMvc
        .perform(MockMvcRequestBuilders.get("/report/stats").params(reportPathParams))
        .andExpect(status().isOk());
  }

  @Test
  public void testAuthenticate() throws Exception {
    reportPathParams.add(SSOConstants.OPENID_RESTYPE_CODE, "dummy-code");
    when(ssoService.authenticate(eq("dummy-code"))).thenReturn("dummy-token");
    this.mockMvc
        .perform(MockMvcRequestBuilders.get("/report/authenticate").params(reportPathParams))
        .andExpect(redirectedUrl("stats?token=dummy-token"));
  }
}
