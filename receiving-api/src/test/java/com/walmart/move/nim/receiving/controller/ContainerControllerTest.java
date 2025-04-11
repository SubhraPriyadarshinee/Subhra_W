package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_INVALID;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.MATCHING_CONTAINER_NOT_FOUND;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DefaultCancelContainerProcessor;
import com.walmart.move.nim.receiving.core.service.DefaultGetContainerRequestHandler;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.service.WitronPutOnHoldService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ContainerControllerTest extends TenantSpecificConfigReaderTestBase {
  @Autowired private MockMvc mockMvc;
  @Autowired @Mock private DefaultCancelContainerProcessor cancelContainerProcessor;
  @Autowired @Mock private WitronPutOnHoldService witronPutOnHoldService;
  @Autowired @Mock private ContainerService containerService;
  @Autowired @Mock private ContainerTransformer containerTransformer;

  private Gson gson = new Gson();
  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private List<CancelContainerResponse> cancelContainerResponse = new ArrayList<>();
  private CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
  private PalletsHoldRequest palletsHoldRequest = new PalletsHoldRequest();
  private List<ContainerErrorResponse> containerErrorResponse = new ArrayList<>();
  private Container container = new Container();
  private String trackingId = "E67387000020002016";
  private ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
  private ContainerUpdateResponse containerUpdateResponse = new ContainerUpdateResponse();
  private String upc = "0956789323";
  private String itemNumber = "678910";
  private ContainerItem containerItem = new ContainerItem();

  private List<ContainerDTO> containerDTOs = new ArrayList<>();
  private Long deliveryNumber = 5504007l;

  private List<PalletHistory> palletHistory = new ArrayList<>();

  @BeforeClass
  public void initMocks() {

    List<String> trackingIds = new ArrayList<String>();
    trackingIds.add("027734368100444931");
    trackingIds.add("027734368100444932");
    cancelContainerRequest.setTrackingIds(trackingIds);
    palletsHoldRequest.setTrackingIds(trackingIds);
    containerUpdateRequest.setPrinterId(10);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void beforeMethod() {
    reset(containerService);
    reset(containerTransformer);
  }

  @Test
  public void testBackoutContainers() {
    try {
      when(cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders))
          .thenReturn(cancelContainerResponse);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/containers/cancel")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(cancelContainerRequest))
                      .headers(httpHeaders))
              .andExpect(status().isAccepted())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testSwapCancelContainers_Returns200() {
    List<SwapContainerRequest> swapContainerRequests = new ArrayList<>();
    SwapContainerRequest swapContainerRequest = new SwapContainerRequest();
    swapContainerRequest.setSourceLpn("027734368100444931");
    swapContainerRequest.setTargetLpn("027734368100444932");
    swapContainerRequests.add(swapContainerRequest);
    try {
      when(cancelContainerProcessor.swapContainers(swapContainerRequests, httpHeaders))
          .thenReturn(cancelContainerResponse);
      when(containerService.swapContainers(swapContainerRequests, httpHeaders))
          .thenReturn(cancelContainerResponse);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/containers/swap")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(swapContainerRequests))
                      .headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testSwapCancelContainers_Returns409() {
    List<SwapContainerRequest> swapContainerRequests = new ArrayList<>();
    SwapContainerRequest swapContainerRequest = new SwapContainerRequest();
    swapContainerRequest.setSourceLpn("027734368100444931");
    swapContainerRequest.setTargetLpn("027734368100444932");
    swapContainerRequests.add(swapContainerRequest);
    CancelContainerResponse cancelContainer =
        new CancelContainerResponse(
            "027734368100444931",
            ExceptionCodes.SOURCE_CONTAINER_NOT_FOUND,
            String.format(ReceivingException.SOURCE_CONTAINER_NOT_FOUND, "027734368100444931"));
    cancelContainerResponse.add(cancelContainer);
    try {
      when(cancelContainerProcessor.swapContainers(swapContainerRequests, httpHeaders))
          .thenReturn(cancelContainerResponse);
      when(containerService.swapContainers(swapContainerRequests, httpHeaders))
          .thenReturn(cancelContainerResponse);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/containers/swap")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(swapContainerRequests))
                      .headers(httpHeaders))
              .andExpect(status().isConflict())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testPalletsOnHold() {
    try {
      when(tenantSpecificConfigReader.getPutOnHoldServiceByFacility("32612"))
          .thenReturn(witronPutOnHoldService);
      when(witronPutOnHoldService.palletsOnHold(palletsHoldRequest, httpHeaders))
          .thenReturn(containerErrorResponse);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.put("/containers/hold")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(palletsHoldRequest))
                      .headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testPalletsOffHold() {
    try {
      when(tenantSpecificConfigReader.getPutOnHoldServiceByFacility("32612"))
          .thenReturn(witronPutOnHoldService);
      when(witronPutOnHoldService.palletsOnHold(palletsHoldRequest, httpHeaders))
          .thenReturn(containerErrorResponse);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.put("/containers/offHold")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(palletsHoldRequest))
                      .headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotNull(response);

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void getContainerByTrackingIdReturnsSuccessWhenIncludeInventoryFlagIsTrue() {

    try {
      when(containerService.getContainerWithChildsByTrackingId(
              trackingId, true, ReceivingConstants.Uom.EACHES))
          .thenReturn(container);

      when(containerService.updateContainerData(
              any(Container.class), anyBoolean(), any(HttpHeaders.class)))
          .thenReturn(container);

      String response =
          mockMvc
              .perform(
                  get("/containers/" + trackingId + "?includeChilds=true&includeInventoryData=true")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

      verify(containerService, times(1))
          .getContainerWithChildsByTrackingId(anyString(), anyBoolean(), anyString());
      verify(containerService, times(1))
          .updateContainerData(any(Container.class), anyBoolean(), any(HttpHeaders.class));

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void getContainerByTrackingIdReturnsSuccessWhenIncludeInventoryFlagIsFalse() {

    try {
      when(containerService.getContainerWithChildsByTrackingId(
              trackingId, true, ReceivingConstants.Uom.EACHES))
          .thenReturn(container);

      DefaultGetContainerRequestHandler containerRequestHandler =
          new DefaultGetContainerRequestHandler();
      ReflectionTestUtils.setField(containerRequestHandler, "containerService", containerService);

      when(tenantSpecificConfigReader.getConfiguredInstance(
              anyString(), anyString(), any(Class.class)))
          .thenReturn(containerRequestHandler);

      String response =
          mockMvc
              .perform(
                  get("/containers/"
                          + trackingId
                          + "?includeChilds=true&includeInventoryData=false")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

      verify(containerService, times(1))
          .getContainerWithChildsByTrackingId(anyString(), anyBoolean(), anyString());
      verify(containerService, times(0))
          .updateContainerData(any(Container.class), anyBoolean(), any(HttpHeaders.class));

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testUpdateQuantityByTrackingId() {
    try {
      containerUpdateRequest.setAdjustQuantity(10);
      containerUpdateRequest.setInventoryQuantity(containerUpdateRequest.getAdjustQuantity() + 1);

      doReturn(containerUpdateResponse)
          .when(containerService)
          .updateQuantityByTrackingId(any(), any(), any());

      MockHttpServletRequestBuilder request =
          post("/containers/" + trackingId + "/adjust")
              .contentType(MediaType.APPLICATION_JSON)
              .content(gson.toJson(containerUpdateRequest))
              .headers(httpHeaders);

      ResultActions resultActions = mockMvc.perform(request);

      MockHttpServletResponse mockHttpServletResponse =
          resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse();

      assertNotNull(mockHttpServletResponse.getContentAsString());

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testUpdateQuantityByTrackingId_NoPrinterId() {
    try {
      containerUpdateRequest.setPrinterId(null);
      containerUpdateRequest.setAdjustQuantity(10);
      containerUpdateRequest.setInventoryQuantity(containerUpdateRequest.getAdjustQuantity() + 1);

      doReturn(containerUpdateResponse)
          .when(containerService)
          .updateQuantityByTrackingId(any(), any(), any());

      MockHttpServletRequestBuilder request =
          post("/containers/" + trackingId + "/adjust")
              .contentType(MediaType.APPLICATION_JSON)
              .content(gson.toJson(containerUpdateRequest))
              .headers(httpHeaders);

      ResultActions resultActions = mockMvc.perform(request);

      MockHttpServletResponse mockHttpServletResponse =
          resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse();

      assertNotNull(mockHttpServletResponse.getContentAsString());

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testPalletCorrectionWithNegativeQtyErrorMessage() {
    try {
      doReturn(containerUpdateResponse)
          .when(containerService)
          .updateQuantityByTrackingId(any(), any(), any());

      containerUpdateRequest.setAdjustQuantity(-1);

      MockHttpServletRequestBuilder request =
          post("/containers/" + trackingId + "/adjust")
              .contentType(MediaType.APPLICATION_JSON)
              .content(gson.toJson(containerUpdateRequest))
              .headers(httpHeaders);

      final MvcResult mvcResult = mockMvc.perform(request).andReturn();

      assertTrue(400 == mvcResult.getResponse().getStatus());
      assertTrue(
          mvcResult.getResolvedException().getMessage().contains(ADJUST_PALLET_QUANTITY_INVALID));

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void getContainers() {

    try {
      List<Container> result = new ArrayList<>();
      result.add(container);

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<String> orderBycolumnCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> sortOrderColumnCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Boolean> parentOnlyColumnCaptor = ArgumentCaptor.forClass(Boolean.class);
      doReturn(result)
          .when(containerService)
          .getContainers(
              orderBycolumnCaptor.capture(),
              sortOrderColumnCaptor.capture(),
              pageCaptor.capture(),
              limitCaptor.capture(),
              parentOnlyColumnCaptor.capture());

      String response =
          mockMvc
              .perform(
                  get("/containers?page=1&limit=10&orderBy=id&sortOrder=asc&parentOnly=false")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      Gson gson = new Gson();
      JsonArray jsonArrayResponse = gson.fromJson(response, JsonArray.class);
      assertEquals(jsonArrayResponse.size(), 1);
      assertSame(limitCaptor.getValue(), 10);
      assertSame(pageCaptor.getValue(), 1);
      assertEquals(orderBycolumnCaptor.getValue(), "id");
      assertEquals(sortOrderColumnCaptor.getValue(), "asc");
      assertSame(parentOnlyColumnCaptor.getValue(), false);

      verify(containerService)
          .getContainers(anyString(), anyString(), anyInt(), anyInt(), anyBoolean());

    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void getContainers_onlyLimit() {

    try {
      List<Container> result = new ArrayList<>();
      result.add(container);

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<String> orderBycolumnCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> sortOrderColumnCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Boolean> parentOnlyColumnCaptor = ArgumentCaptor.forClass(Boolean.class);
      doReturn(result)
          .when(containerService)
          .getContainers(
              orderBycolumnCaptor.capture(),
              sortOrderColumnCaptor.capture(),
              pageCaptor.capture(),
              limitCaptor.capture(),
              parentOnlyColumnCaptor.capture());

      String response =
          mockMvc
              .perform(
                  get("/containers?limit=4")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      Gson gson = new Gson();
      JsonArray jsonArrayResponse = gson.fromJson(response, JsonArray.class);
      assertEquals(jsonArrayResponse.size(), 1);
      assertSame(limitCaptor.getValue(), 4);
      assertSame(pageCaptor.getValue(), 0);
      assertEquals(orderBycolumnCaptor.getValue(), "createTs");
      assertEquals(sortOrderColumnCaptor.getValue(), "desc");
      assertSame(parentOnlyColumnCaptor.getValue(), true);

      verify(containerService)
          .getContainers(anyString(), anyString(), anyInt(), anyInt(), anyBoolean());

    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void testGetContainerLabelsByTrackingIdsIsSuccess() throws ReceivingException {
    when(containerService.getContainerLabelsByTrackingIds(anyList(), any(HttpHeaders.class)))
        .thenReturn(MockContainer.getMockContainerLabelResponse());

    try {
      MockHttpServletRequestBuilder request =
          post("/containers/labels/reprint")
              .contentType(MediaType.APPLICATION_JSON)
              .content(gson.toJson(MockContainer.getMockReprintLabelRequest()))
              .headers(httpHeaders);
      ResultActions resultActions = mockMvc.perform(request);

      MockHttpServletResponse mockHttpServletResponse =
          resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse();

      assertNotNull(mockHttpServletResponse);
      assertTrue(mockHttpServletResponse.getStatus() == HttpStatus.OK.value());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetContainerLabelsByTrackingIdsReturns404Response() throws ReceivingException {
    when(containerService.getContainerLabelsByTrackingIds(anyList(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ExceptionCodes.CONTAINER_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                MATCHING_CONTAINER_NOT_FOUND));

    try {
      MockHttpServletRequestBuilder request =
          post("/containers/labels/reprint")
              .contentType(MediaType.APPLICATION_JSON)
              .content(gson.toJson(MockContainer.getMockReprintLabelRequest()))
              .headers(httpHeaders);
      ResultActions resultActions = mockMvc.perform(request);

      MockHttpServletResponse mockHttpServletResponse =
          resultActions
              .andExpect(status().is4xxClientError())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse();

      assertNotNull(mockHttpServletResponse);
      assertTrue(mockHttpServletResponse.getStatus() == HttpStatus.NOT_FOUND.value());
    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void testGetContainerItemByUpcAndItemNumber() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      when(containerService.getContainerItemByUpcAndItemNumber(anyString(), anyLong()))
          .thenReturn(containerItem);

      String response =
          mockMvc
              .perform(get("/containers/upc/" + upc + "/item/" + itemNumber).headers(headers))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotEquals(response, null);

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetContainerItemByUpcAndItemNumberNotFound() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      when(containerService.getContainerItemByUpcAndItemNumber(anyString(), anyLong()))
          .thenThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.CONTAINER_ITEM_DATA_NOT_FOUND,
                  String.format(
                      ExceptionDescriptionConstants
                          .CONTAINER_ITEM_NOT_FOUND_BY_ITEM_AND_UPC_ERROR_MSG,
                      itemNumber,
                      upc)));

      mockMvc
          .perform(get("/containers/upc/" + upc + "/item/" + itemNumber).headers(headers))
          .andExpect(status().is4xxClientError());

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testGetContainersByDelivery() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      when(containerService.getContainerByDeliveryNumber(deliveryNumber, null))
          .thenReturn(Collections.singletonList(container));
      when(containerTransformer.transformList(anyList())).thenReturn(containerDTOs);
      String response =
          mockMvc
              .perform(get("/containers/delivery/" + deliveryNumber).headers(headers))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertNotNull(response);
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetContainersByDeliveryNotFound() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      when(containerService.getContainerByDeliveryNumber(deliveryNumber, null))
          .thenThrow(
              new ReceivingException(
                  "no record's found for this delivery number in container table",
                  HttpStatus.NOT_FOUND));

      mockMvc
          .perform(get("/containers/delivery/" + deliveryNumber).headers(headers))
          .andExpect(status().is4xxClientError());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testGetContainersLabelsByDeliveryNumberOrPO_validResponse() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      when(containerService.getReceivedHistoryByDeliveryNumber(deliveryNumber))
          .thenReturn(palletHistory);

      String response =
          mockMvc
              .perform(get("/containers/labels/" + deliveryNumber).headers(headers))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertNotNull(response);
      verify(containerService, Mockito.times(1)).getReceivedHistoryByDeliveryNumber(deliveryNumber);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
