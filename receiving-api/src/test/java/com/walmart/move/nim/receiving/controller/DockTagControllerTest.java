package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DockTagControllerTest extends ReceivingControllerTestBase {
  private MockMvc mockMvc;
  DockTagController dockTagController;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DockTagService dockTagService;
  private CreateDockTagRequest request;
  private HttpHeaders headers;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    MockitoAnnotations.initMocks(this);
    dockTagController = new DockTagController();

    ReflectionTestUtils.setField(
        dockTagController, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(dockTagController, "dockTagService", dockTagService);

    this.mockMvc = MockMvcBuilders.standaloneSetup(dockTagController).build();

    request =
        new CreateDockTagRequest(12345678L, "200", null, 1, "PRIM", "12345", null, null, null);
  }

  @AfterMethod
  public void clear() {
    reset(dockTagService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testSearchDockTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    when(dockTagService.searchDockTag(any(), eq(null)))
        .thenReturn(
            "[{\"dockTagId\":\"c32987000000000000000001\",\"deliveryNumber\":12340001,\"itemNumber\":100000001,\"createUserId\":\"sysadmin\",\"createTs\":\"2020-07-30T16:23:17.111Z\",\"completeTs\":\"2020-07-30T16:23:17.111Z\",\"dockTagStatus\":\"COMPLETED\",\"gtin\":\"10000000000001\",\"facilityCountryCode\":\"US\",\"facilityNum\":32818}]");

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\"deliveryNumbers\":[\"92098617\"]}"))
        .andExpect(status().isOk());
    ArgumentCaptor<SearchDockTagRequest> captor =
        ArgumentCaptor.forClass(SearchDockTagRequest.class);
    verify(dockTagService, times(1)).searchDockTag(captor.capture(), eq(null));
    assertEquals(captor.getValue().getDeliveryNumbers().size(), 1);
    assertEquals(captor.getValue().getDeliveryNumbers().get(0), "92098617");
  }

  @Test
  public void testSearchMultipleDeliverySuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    when(dockTagService.searchDockTag(any(), eq(null))).thenReturn(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\"deliveryNumbers\":[\"92098617\",\"15139683\"]}"))
        .andExpect(status().isNoContent());
    ArgumentCaptor<SearchDockTagRequest> captor =
        ArgumentCaptor.forClass(SearchDockTagRequest.class);
    verify(dockTagService, times(1)).searchDockTag(captor.capture(), eq(null));
    assertEquals(captor.getValue().getDeliveryNumbers().size(), 2);
    assertTrue(
        captor.getValue().getDeliveryNumbers().containsAll(Arrays.asList("92098617", "15139683")));
  }

  @Test
  public void testSearchWithStatus() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    when(dockTagService.searchDockTag(any(), any()))
        .thenReturn(
            "[{\"dockTagId\":\"c32987000000000000000001\",\"deliveryNumber\":12340001,\"itemNumber\":100000001,\"createUserId\":\"sysadmin\",\"createTs\":\"2020-07-30T16:23:17.111Z\",\"completeTs\":\"2020-07-30T16:23:17.111Z\",\"dockTagStatus\":\"COMPLETED\",\"gtin\":\"10000000000001\",\"facilityCountryCode\":\"US\",\"facilityNum\":32818}]");
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search?status=CREATED")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\"deliveryNumbers\":[\"92098617\"]}"))
        .andExpect(status().isOk());
    ArgumentCaptor<SearchDockTagRequest> captor =
        ArgumentCaptor.forClass(SearchDockTagRequest.class);
    verify(dockTagService, times(1)).searchDockTag(captor.capture(), eq(InstructionStatus.CREATED));
    assertEquals(captor.getValue().getDeliveryNumbers().size(), 1);
    assertEquals(captor.getValue().getDeliveryNumbers().get(0), "92098617");
  }

  @Test
  public void testSearchWithInvalidStatus() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search?status=STARTED")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\"deliveryNumbers\":[\"92098617\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testSearchDeliveryNull() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\"deliveryNumbers\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testSearchDeliveryEmpty() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\"deliveryNumbers\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testSearchEmptyPayload() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(""))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testBulkCompleteDockTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    CompleteDockTagResponse completeDockTagResponse =
        CompleteDockTagResponse.builder().success(Arrays.asList("a100000000")).build();
    when(dockTagService.completeDockTags(any(), any())).thenReturn(completeDockTagResponse);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "    \"deliveryNumber\":1234567,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099222\",\n"
                        + "        \"c328180000200000001099223\"\n"
                        + "    ]\n"
                        + "}"))
        .andExpect(status().isOk());
    ArgumentCaptor<CompleteDockTagRequest> captor =
        ArgumentCaptor.forClass(CompleteDockTagRequest.class);
    verify(dockTagService, times(1)).completeDockTags(captor.capture(), any());
    assertEquals(captor.getValue().getDeliveryNumber(), Long.valueOf(1234567L));
    assertEquals(captor.getValue().getDeliveryStatus(), "WRK");
    assertNotNull(captor.getValue().getDocktags());
  }

  @Test
  public void testBulkCompleteDockTagPartialSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    CompleteDockTagResponse completeDockTagResponse =
        CompleteDockTagResponse.builder()
            .success(Arrays.asList("a100000000"))
            .failed(Arrays.asList("a100000001"))
            .build();
    when(dockTagService.completeDockTags(any(), any())).thenReturn(completeDockTagResponse);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "    \"deliveryNumber\":1234567,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099222\",\n"
                        + "        \"c328180000200000001099223\"\n"
                        + "    ]\n"
                        + "}"))
        .andExpect(status().isMultiStatus());
    ArgumentCaptor<CompleteDockTagRequest> captor =
        ArgumentCaptor.forClass(CompleteDockTagRequest.class);
    verify(dockTagService, times(1)).completeDockTags(captor.capture(), any());
    assertEquals(captor.getValue().getDeliveryNumber(), Long.valueOf(1234567L));
    assertEquals(captor.getValue().getDeliveryStatus(), "WRK");
    assertNotNull(captor.getValue().getDocktags());
  }

  @Test
  public void testCompleteDeliveryNumNotPresent() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099222\",\n"
                        + "        \"c328180000200000001099223\"\n"
                        + "    ]\n"
                        + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCompleteDeliveryStatusNotPresent() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "    \"deliveryNumber\":1234567,\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099222\",\n"
                        + "        \"c328180000200000001099223\"\n"
                        + "    ]\n"
                        + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCompleteDockTagsNotPresent() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "    \"deliveryNumber\":1234567,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCompleteEmptyPayload() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(""))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCompleteDockTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    when(dockTagService.completeDockTag(any(), any())).thenReturn("");
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/docktags/a100000000/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers))
        .andExpect(status().isOk());
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dockTagService, times(1)).completeDockTag(captor.capture(), any());
    assertEquals(captor.getValue(), "a100000000");
  }

  @Test
  public void testReceiveDockTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.receiveDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content(
                    "{\n"
                        + "  \"dockTagId\": \"a100000000\",\n"
                        + "  \"mappedFloorLineLocation\": \"9999\"\n"
                        + "}"))
        .andExpect(status().isCreated());
    ArgumentCaptor<ReceiveDockTagRequest> captor =
        ArgumentCaptor.forClass(ReceiveDockTagRequest.class);
    verify(dockTagService, times(1)).receiveDockTag(captor.capture(), any());

    assertEquals(captor.getValue().getMappedFloorLineLocation(), "9999");
    assertEquals(captor.getValue().getDockTagId(), "a100000000");
  }

  @Test
  public void testReceiveDockTagSuccessDockTagEmpty() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.receiveDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content("{\n" + "  \"mappedFloorLineLocation\": \"9999\"\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testReceiveDockTagSuccessFloorLineEmpty() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.receiveDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content("{\n" + "  \"dockTagId\": \"a100000000\"\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateDockTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.createDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content(
                    "{\n"
                        + "  \"deliveryNumber\": 12345678,\n"
                        + "  \"doorNumber\": \"100\"\n"
                        + "}"))
        .andExpect(status().isCreated());
    ArgumentCaptor<CreateDockTagRequest> captor =
        ArgumentCaptor.forClass(CreateDockTagRequest.class);
    verify(dockTagService, times(1)).createDockTag(captor.capture(), any());
    assertEquals(captor.getValue().getDeliveryNumber(), Long.valueOf(12345678));
    assertEquals(captor.getValue().getDoorNumber(), "100");
  }

  @Test
  public void testCreateDockTagDeliveryNotPresent() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.createDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content("{\n" + "  \"doorNumber\": \"100\"\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateDockTagDeliveryNull() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.createDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content(
                    "{\n" + "  \"deliveryNumber\": null,\n" + "  \"doorNumber\": \"100\"\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateDockTagDoorNull() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.createDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content(
                    "{\n" + "  \"deliveryNumber\": 12345678,\n" + "  \"doorNumber\": null\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateDockTagDoorNotPresent() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(dockTagService.createDockTag(any(), any())).thenReturn(instructionResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/docktags/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers)
                .content("{\n" + "  \"deliveryNumber\": 12345678\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateDockTagForHappyFlow() throws ReceivingException {

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(dockTagService);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      DockTagResponse response =
          DockTagResponse.builder()
              .deliveryNumber(12345678L)
              .dockTags(Arrays.asList("DT215489654712545845"))
              .build();
      when(dockTagService.createDockTags(any(CreateDockTagRequest.class), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/docktags/createMultiple")
                  .content(new Gson().toJson(request))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testCreateDockTagWithInvalidHeadersReturnsBadRequest() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      DockTagResponse response =
          DockTagResponse.builder()
              .deliveryNumber(12345678L)
              .dockTags(Arrays.asList("DT215489654712545845"))
              .build();
      when(dockTagService.createDockTags(any(CreateDockTagRequest.class), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/docktags/createMultiple")
                  .content("")
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testReceiveNonConDockTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    //    when(dockTagService.receiveNonConDockTag(anyString(),
    // any())).thenReturn(deliveryDetailsJson);
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(MockMvcRequestBuilders.put("/docktags/a100000000/receive").headers(headers))
        .andExpect(status().isOk());
    verify(dockTagService, times(1)).receiveNonConDockTag(eq("a100000000"), eq(headers));
  }

  @Test
  public void testGetAllDockTagsForTenant_Success() throws Exception {

    when(dockTagService.searchAllDockTagForGivenTenant(InstructionStatus.CREATED))
        .thenReturn(anyList());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/docktags?status=CREATED")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    ArgumentCaptor<InstructionStatus> captor = ArgumentCaptor.forClass(InstructionStatus.class);
    verify(dockTagService, times(1)).searchAllDockTagForGivenTenant(captor.capture());
    assertEquals(captor.getValue(), InstructionStatus.CREATED);
  }

  @Test
  public void testGetAllDockTagsForTenant_NoContent() throws Exception {

    when(dockTagService.searchAllDockTagForGivenTenant(InstructionStatus.CREATED)).thenReturn(null);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/docktags?status=CREATED")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isNoContent());
    ArgumentCaptor<InstructionStatus> captor = ArgumentCaptor.forClass(InstructionStatus.class);
    verify(dockTagService, times(1)).searchAllDockTagForGivenTenant(captor.capture());
    assertEquals(captor.getValue(), InstructionStatus.CREATED);
  }

  @Test
  public void testGetAllDockTagsForTenant_ParamMissing() throws Exception {

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/docktags")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .headers(MockHttpHeaders.getHeaders()))
          .andExpect(status().isBadRequest());
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void testCountOfAllDockTagsForTenant_Success() throws Exception {

    when(dockTagService.countDockTag(InstructionStatus.CREATED)).thenReturn(any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/docktags/count?status=CREATED")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    ArgumentCaptor<InstructionStatus> captor = ArgumentCaptor.forClass(InstructionStatus.class);
    verify(dockTagService, times(1)).countDockTag(captor.capture());
    assertEquals(captor.getValue(), InstructionStatus.CREATED);
  }

  @Test
  public void testCountOfAllDockTagsForTenant_IncorrectParam() {

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/docktags/count")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .headers(MockHttpHeaders.getHeaders()))
          .andExpect(status().isBadRequest());
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void testCompleteDockTagsForMultipleDeliveries_PartialSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    List<CompleteDockTagResponse> completeDockTagResponses = new ArrayList<>();
    CompleteDockTagResponse completeDockTagResponse1 =
        CompleteDockTagResponse.builder()
            .success(Arrays.asList("a100000000"))
            .failed(Arrays.asList("a100000001"))
            .deliveryNumer(123456L)
            .build();
    CompleteDockTagResponse completeDockTagResponse2 =
        CompleteDockTagResponse.builder()
            .success(Arrays.asList("b100000000"))
            .failed(Arrays.asList("b100000001"))
            .deliveryNumer(987654L)
            .build();
    completeDockTagResponses.add(completeDockTagResponse1);
    completeDockTagResponses.add(completeDockTagResponse2);
    when(dockTagService.completeDockTagsForGivenDeliveries(any(), any()))
        .thenReturn(completeDockTagResponses);

    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{ \"list\" : [{\n"
                        + "    \"deliveryNumber\":123456,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"a100000000\",\n"
                        + "        \"a100000001\"\n"
                        + "    ]\n"
                        + "}, {\n"
                        + "    \"deliveryNumber\":987654,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"b100000000\",\n"
                        + "        \"b100000001\"\n"
                        + "    ]\n"
                        + "}]}"))
        .andExpect(status().isMultiStatus());
    ArgumentCaptor<CompleteDockTagRequestsList> captor =
        ArgumentCaptor.forClass(CompleteDockTagRequestsList.class);
    verify(dockTagService, times(1)).completeDockTagsForGivenDeliveries(captor.capture(), any());
    assertEquals(captor.getValue().getList().get(0).getDeliveryNumber(), Long.valueOf(123456L));
    assertEquals(captor.getValue().getList().get(0).getDeliveryStatus(), "WRK");
    assertNotNull(captor.getValue().getList().get(0).getDocktags());
    assertEquals(captor.getValue().getList().get(1).getDeliveryNumber(), Long.valueOf(987654L));
    assertEquals(captor.getValue().getList().get(1).getDeliveryStatus(), "WRK");
    assertNotNull(captor.getValue().getList().get(1).getDocktags());
  }

  @Test
  public void testCompleteDockTagsForMultipleDeliveries_DeliveryStatusMissingBadRequest()
      throws Exception {

    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    " { \"list\" : [{\n"
                        + "    \"deliveryNumber\":123456,\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099222\",\n"
                        + "        \"c328180000200000001099223\"\n"
                        + "    ]\n"
                        + "}, {\n"
                        + "    \"deliveryNumber\":987654,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099444\",\n"
                        + "        \"c328180000200000001099555\"\n"
                        + "    ]\n"
                        + "}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCompleteDockTagsForMultipleDeliveries_DockTagsMissingBadRequest()
      throws Exception {

    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    " { \"list\" : [{\n"
                        + "    \"deliveryNumber\":123456,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "}, {\n"
                        + "    \"deliveryNumber\":987654,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"c328180000200000001099444\",\n"
                        + "        \"c328180000200000001099555\"\n"
                        + "    ]\n"
                        + "}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCompleteDockTagsForMultipleDeliveries_AllSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    List<CompleteDockTagResponse> completeDockTagResponses = new ArrayList<>();
    CompleteDockTagResponse completeDockTagResponse1 =
        CompleteDockTagResponse.builder()
            .success(Arrays.asList("a100000000"))
            .success(Arrays.asList("a100000001"))
            .deliveryNumer(123456L)
            .build();
    CompleteDockTagResponse completeDockTagResponse2 =
        CompleteDockTagResponse.builder()
            .success(Arrays.asList("b100000000"))
            .success(Arrays.asList("b100000001"))
            .deliveryNumer(987654L)
            .build();
    completeDockTagResponses.add(completeDockTagResponse1);
    completeDockTagResponses.add(completeDockTagResponse2);
    when(dockTagService.completeDockTagsForGivenDeliveries(any(), any()))
        .thenReturn(completeDockTagResponses);

    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/docktags/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{ \"list\" : [{\n"
                        + "    \"deliveryNumber\":123456,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"a100000000\",\n"
                        + "        \"a100000001\"\n"
                        + "    ]\n"
                        + "}, {\n"
                        + "    \"deliveryNumber\":987654,\n"
                        + "    \"deliveryStatus\":\"WRK\",\n"
                        + "    \"docktags\": [\n"
                        + "        \"b100000000\",\n"
                        + "        \"b100000001\"\n"
                        + "    ]\n"
                        + "}]}"))
        .andExpect(status().isOk());
    ArgumentCaptor<CompleteDockTagRequestsList> captor =
        ArgumentCaptor.forClass(CompleteDockTagRequestsList.class);
    verify(dockTagService, times(1)).completeDockTagsForGivenDeliveries(captor.capture(), any());
    assertEquals(captor.getValue().getList().get(0).getDeliveryNumber(), Long.valueOf(123456L));
    assertEquals(captor.getValue().getList().get(0).getDeliveryStatus(), "WRK");
    assertNotNull(captor.getValue().getList().get(0).getDocktags());
    assertEquals(captor.getValue().getList().get(1).getDeliveryNumber(), Long.valueOf(987654L));
    assertEquals(captor.getValue().getList().get(1).getDeliveryStatus(), "WRK");
    assertNotNull(captor.getValue().getList().get(1).getDocktags());
  }

  @Test
  public void testCompleteWorkStationDockTagForHappyFlow() throws ReceivingException {
    headers = MockHttpHeaders.getHeaders();
    headers.add(RdcConstants.WFT_LOCATION_ID, "200");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "Door-200");
    headers.add(RdcConstants.WFT_SCC_CODE, "001001001");

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(dockTagService);

    try {
      DockTagResponse response =
          DockTagResponse.builder()
              .deliveryNumber(12345678L)
              .dockTags(Arrays.asList("b328180000200000043976845"))
              .build();
      when(dockTagService.partialCompleteDockTag(
              any(CreateDockTagRequest.class), anyString(), anyBoolean(), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.put(
                      "/docktags/v2/b328180000200000043976844/complete?retry=false")
                  .content(new Gson().toJson(request))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().isOk());

      verify(dockTagService, times(1))
          .partialCompleteDockTag(
              any(CreateDockTagRequest.class), anyString(), anyBoolean(), any(HttpHeaders.class));
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testCompleteWorkStationDockTagForExceptionFlow() throws ReceivingException {
    String dockTagId = "b328180000200000043976844";

    headers = MockHttpHeaders.getHeaders();
    headers.add(RdcConstants.WFT_LOCATION_ID, "200");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "Door-200");
    headers.add(RdcConstants.WFT_SCC_CODE, "001001001");

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(dockTagService);

    try {
      when(dockTagService.partialCompleteDockTag(
              any(CreateDockTagRequest.class), anyString(), anyBoolean(), any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingDataNotFoundException(
                  ExceptionCodes.DOCK_TAG_NOT_FOUND,
                  String.format(ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, dockTagId),
                  dockTagId));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put(
                      "/docktags/v2/b328180000200000043976844/complete?retry=false")
                  .content(new Gson().toJson(request))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is4xxClientError());

      verify(dockTagService, times(1))
          .partialCompleteDockTag(
              any(CreateDockTagRequest.class), anyString(), anyBoolean(), any(HttpHeaders.class));
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  void testGetDockTagDetailsByDockTagId() throws Exception {
    DockTagDTO dockTag =
        DockTagDTO.builder()
            .dockTagType(DockTagType.FLOOR_LINE)
            .dockTagId("b393080000200000043976844")
            .build();
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    when(dockTagService.searchDockTagById(any())).thenReturn(dockTag);
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/docktags/b393080000200000043976844")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());

    verify(dockTagService, times(1)).searchDockTagById(any());
  }
}
