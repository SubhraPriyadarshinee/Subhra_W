package com.walmart.move.nim.receiving.rdc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.model.docktag.CreateDockTagRequest;
import com.walmart.move.nim.receiving.core.model.docktag.DockTagResponse;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.rdc.service.RdcDockTagService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcDockTagControllerTest extends ReceivingControllerTestBase {

  private MockMvc mockMvc;

  @InjectMocks private RdcDockTagController rdcDockTagController;

  @Mock private RdcDockTagService dockTagService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private CreateDockTagRequest request;
  private String dockTagId = "DT215489654712545845";
  private HttpHeaders headers;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    request =
        new CreateDockTagRequest(12345678L, "200", null, 1, "PRIM", "12345", null, null, null);
    mockMvc = MockMvcBuilders.standaloneSetup(rdcDockTagController).build();
    headers = MockHttpHeaders.getHeaders();
    headers.add(RdcConstants.WFT_LOCATION_ID, "4920");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "Door-6");
    headers.add(RdcConstants.WFT_SCC_CODE, "001001001");

    TenantContext.setFacilityNum(6020);
  }

  @AfterMethod
  public void resetMocks() {
    reset(dockTagService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testCreateDockTagForHappyFlow() throws ReceivingException {
    headers.add(RdcConstants.WFT_LOCATION_ID, "4920");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "Door-6");
    headers.add(RdcConstants.WFT_SCC_CODE, "001001001");

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(dockTagService);

    try {
      DockTagResponse response =
          DockTagResponse.builder()
              .deliveryNumber(12345678L)
              .dockTags(Collections.singletonList("DT215489654712545845"))
              .build();
      when(dockTagService.createDockTags(any(CreateDockTagRequest.class), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/rdc/docktags")
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
              MockMvcRequestBuilders.post("/rdc/docktags")
                  .content("")
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void test_SearchDockTag_dockTagId() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      List<DockTagData> response =
          Arrays.asList(
              DockTagData.builder()
                  .deliveryNumber(12345678L)
                  .dockTagId("DT215489654712545845")
                  .build());
      when(dockTagService.searchDockTag(any(), any(), any(), any())).thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/search?dockTagId=DT215489654712545845")
                  .content(new Gson().toJson(request))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void test_SearchDockTag_deliveryNumber() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      List<DockTagData> response =
          Arrays.asList(
              DockTagData.builder()
                  .deliveryNumber(12345678L)
                  .dockTagId("DT215489654712545845")
                  .build());
      when(dockTagService.searchDockTag(any(), any(), any(), any())).thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/search?deliveryNumber=12345678")
                  .content(new Gson().toJson(request))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void test_SearchDockTag_With_Optional_Params() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      List<DockTagData> response =
          Arrays.asList(
              DockTagData.builder()
                  .deliveryNumber(12345678L)
                  .dockTagId("DT215489654712545845")
                  .build());
      when(dockTagService.searchDockTag(any(), any(), any(), any())).thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.get(
                      "/rdc/docktags/search?deliveryNumber=12345678&fromDate=2154896547125&toDate=2154896547526")
                  .content(new Gson().toJson(request))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testReceiveDockTagSuccess() throws Exception {
    when(dockTagService.receiveDockTag(anyString(), any(HttpHeaders.class)))
        .thenReturn(getDockTagData(getMockDelivery()));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testReceiveInvalidDockTagThrowsExceptionWhenGivenDockTagIsNotFoundInDB()
      throws Exception {
    when(dockTagService.receiveDockTag(anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.DOCK_TAG_NOT_FOUND,
                String.format(ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, dockTagId),
                dockTagId));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testReceiveDockTagInCompletedStatusThrowsException() throws Exception {
    when(dockTagService.receiveDockTag(anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.INVALID_DOCK_TAG,
                String.format(
                    ReceivingConstants.DOCK_TAG_ALREADY_RECEIVED_MESSAGE, dockTagId, "sysadmin"),
                dockTagId));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testReceiveInvalidDockTagThrowsExceptionWhenGdmServiceIsDown() throws Exception {
    when(dockTagService.receiveDockTag(anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testReceiveInvalidDockTagThrowsExceptionWhenDeliveryNotFoundInGDM() throws Exception {
    when(dockTagService.receiveDockTag(anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testCompleteDockTagSuccess() throws Exception {
    when(dockTagService.completeDockTagById(any(), any()))
        .thenReturn(getDockTagData(InstructionStatus.COMPLETED));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testCompleteInvalidDockTagThrowsException() throws Exception {
    when(dockTagService.completeDockTagById(any(), any()))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.DOCK_TAG_NOT_FOUND,
                String.format(ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, dockTagId),
                dockTagId));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testCompleteDockTagInCompletedStatusThrowsException() throws Exception {
    when(dockTagService.completeDockTagById(any(), any()))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.INVALID_DOCK_TAG,
                String.format(
                    ReceivingConstants.DOCK_TAG_ALREADY_RECEIVED_MESSAGE, dockTagId, "sysadmin"),
                dockTagId));
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/rdc/docktags/DT215489654712545845").headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  private DockTagData getDockTagData(InstructionStatus status) {
    DockTagData dockTagData;
    if (status.getInstructionStatus().equals(InstructionStatus.COMPLETED)) {
      dockTagData =
          DockTagData.builder()
              .dockTagId("DT215489654712545845")
              .deliveryNumber(12345678L)
              .status(status)
              .createUserId("sysadmin")
              .createTs(new Date().getTime())
              .scannedLocation("100")
              .lastChangedUserId("sysadmin")
              .lastChangedTs(new Date().getTime())
              .completeUserId("sysadmin")
              .completeTs(new Date().getTime())
              .build();

    } else {
      dockTagData =
          DockTagData.builder()
              .dockTagId("DT215489654712545845")
              .deliveryNumber(12345678L)
              .status(status)
              .createUserId("sysadmin")
              .createTs(new Date().getTime())
              .scannedLocation("100")
              .build();
    }
    return dockTagData;
  }

  private DockTagData getDockTagData(String deliveryInfo) {
    return DockTagData.builder()
        .dockTagId("DT215489654712545845")
        .deliveryNumber(12345678L)
        .status(InstructionStatus.CREATED)
        .createUserId("sysadmin")
        .createTs(new Date().getTime())
        .scannedLocation("100")
        .lastChangedUserId("sysadmin")
        .lastChangedTs(new Date().getTime())
        .completeUserId("sysadmin")
        .completeTs(new Date().getTime())
        .deliveryInfo(deliveryInfo)
        .build();
  }

  private String getMockDelivery() {
    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryNumber", 123L);
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.WRK.name());
    return mockDeliveryResponse.toString();
  }
}
