package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.model.ItemCatalogDeleteRequest;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.service.DefaultItemCatalogService;
import com.walmart.move.nim.receiving.core.service.ItemCatalogService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemCatalogingControllerTest extends ReceivingControllerTestBase {
  public static final String ITEM_CATALOG_URI = "/item-catalog";

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private ItemCatalogingController itemCatalogingController;
  @Mock private DefaultItemCatalogService itemCatalogService;
  private RestResponseExceptionHandler restResponseExceptionHandler;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private ItemCatalogUpdateRequest itemCatalogUpdateRequest;
  private ItemCatalogDeleteRequest itemCatalogDeleteRequest;
  private MockMvc mockMvc;
  private Gson gson;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");

    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    itemCatalogDeleteRequest = new ItemCatalogDeleteRequest();

    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);
    mockMvc =
        MockMvcBuilders.standaloneSetup(itemCatalogingController)
            .setControllerAdvice(restResponseExceptionHandler)
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
    itemCatalogDeleteRequest.setDeliveryNumber("87654321");
    itemCatalogDeleteRequest.setNewItemUPC("20000943037194");
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(itemCatalogService);
  }

  @Test
  public void testItemCatalogItemNumberIsNull() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_CATALOG_SERVICE), eq(ItemCatalogService.class)))
        .thenReturn(itemCatalogService);

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
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_CATALOG_SERVICE), eq(ItemCatalogService.class)))
        .thenReturn(itemCatalogService);
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
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_CATALOG_SERVICE), eq(ItemCatalogService.class)))
        .thenReturn(itemCatalogService);
    itemCatalogUpdateRequest.setNewItemUPC(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  public void testItemCatalogNewItemUpcAndOldUPCAreSame() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_CATALOG_SERVICE), eq(ItemCatalogService.class)))
        .thenReturn(itemCatalogService);
    itemCatalogUpdateRequest.setNewItemUPC("00078742011752");
    itemCatalogUpdateRequest.setOldItemUPC("00078742011752");
    try {
      mockMvc.perform(
          MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
              .headers(httpHeaders)
              .content(gson.toJson(itemCatalogUpdateRequest)));
    } catch (Exception e) {
      Assert.assertTrue(true);
    }
    verify(itemCatalogService, times(0)).updateVendorUPC(any(), any());
  }

  @Test
  public void testItemCatalogSuccessful() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_CATALOG_SERVICE), eq(ItemCatalogService.class)))
        .thenReturn(itemCatalogService);
    when(itemCatalogService.updateVendorUPC(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(getUpdatedItemCatalogLog()));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }

  private ItemCatalogUpdateLog getUpdatedItemCatalogLog() {
    return ItemCatalogUpdateLog.builder()
        .deliveryNumber(87654321L)
        .itemNumber(567898765L)
        .newItemUPC("20000943037194")
        .oldItemUPC("00000943037194")
        .itemInfoHandKeyed(Boolean.FALSE)
        .createUserId("sysadmin")
        .build();
  }

  @Test
  public void testDeleteItemCatalog() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_CATALOG_SERVICE), eq(ItemCatalogService.class)))
        .thenReturn(itemCatalogService);
    doNothing().when(itemCatalogService).deleteItemCatalog(any(ItemCatalogDeleteRequest.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(ITEM_CATALOG_URI)
                .headers(httpHeaders)
                .content(gson.toJson(itemCatalogUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }
}
