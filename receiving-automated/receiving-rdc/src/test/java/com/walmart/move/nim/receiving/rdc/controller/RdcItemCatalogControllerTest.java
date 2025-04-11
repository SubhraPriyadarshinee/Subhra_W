package com.walmart.move.nim.receiving.rdc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.service.RdcItemCatalogService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcItemCatalogControllerTest extends ReceivingControllerTestBase {
  public static final String ITEM_CATALOG_URI = "/rdc/itemcatalog";

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private RdcItemCatalogController rdcItemCatalogingController;
  @Mock private RdcItemCatalogService rdcItemCatalogService;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private ItemCatalogUpdateRequest itemCatalogUpdateRequest;
  private MockMvc mockMvc;
  private Gson gson;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");

    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    mockMvc =
        MockMvcBuilders.standaloneSetup(rdcItemCatalogingController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
  }

  @BeforeMethod
  public void setup() {
    itemCatalogUpdateRequest.setDeliveryNumber("87654321");
    itemCatalogUpdateRequest.setItemNumber(567898765L);
    itemCatalogUpdateRequest.setLocationId("100");
    itemCatalogUpdateRequest.setNewItemUPC("20000943037194");
    itemCatalogUpdateRequest.setOldItemUPC("00000943037194");
    itemCatalogUpdateRequest.setItemInfoHandKeyed(Boolean.TRUE);
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(rdcItemCatalogService);
  }

  @Test
  public void testItemCatalogItemNumberIsNull() throws Exception {
    itemCatalogUpdateRequest.setItemNumber(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  public void testItemCatalogDeliveryNumberIsNull() throws Exception {
    itemCatalogUpdateRequest.setDeliveryNumber(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  public void testItemCatalogNewItemUpcIsNull() throws Exception {
    itemCatalogUpdateRequest.setNewItemUPC(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  public void testItemCatalogSuccessful() throws Exception {
    when(rdcItemCatalogService.updateVendorUPC(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(getUpdatedItemCatalogLogResponse()));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }

  private ItemCatalogUpdateLog getUpdatedItemCatalogLogResponse() {
    return ItemCatalogUpdateLog.builder()
        .deliveryNumber(87654321L)
        .itemNumber(567898765L)
        .newItemUPC("20000943037194")
        .oldItemUPC("10000943037194")
        .createUserId("sysadmin")
        .build();
  }
}
