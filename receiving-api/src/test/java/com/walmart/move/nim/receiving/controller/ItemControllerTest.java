package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.testng.AssertJUnit.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.ItemInfoResponse;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.SaveConfirmationRequest;
import com.walmart.move.nim.receiving.core.model.UpdateVendorComplianceRequest;
import com.walmart.move.nim.receiving.core.model.VendorCompliance;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.ItemService;
import com.walmart.move.nim.receiving.core.service.RegulatedItemService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.testng.annotations.Test;

public class ItemControllerTest extends ReceivingControllerTestBase {

  @InjectMocks private ItemController itemController;

  @Mock private ItemService itemService;

  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private RegulatedItemService regulatedItemService;

  private MockMvc mockMvc;
  private static final String countryCode = "US";

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(itemController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(32818));
  }

  @AfterMethod
  public void resetMocks() {
    reset(itemService);
  }

  @Test
  private void testFindByCaseUPC() throws Exception {

    when(itemService.findLatestItemByUPC(anyString())).thenReturn(getSuccessItem());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/items/search/upcs/12345")
                .headers(MockHttpHeaders.getHeaders()))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(itemService, times(1)).findLatestItemByUPC(anyString());
  }

  @Test
  private void testFindItemBaseDivCodesByUPC() throws Exception {
    when(itemService.findItemBaseDivCodesByUPC(anyString())).thenReturn(getSuccessBaseDivCode());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/items/search/item-base-div-codes/12345")
                .headers(MockHttpHeaders.getHeaders()))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(itemService, times(1)).findItemBaseDivCodesByUPC(anyString());
  }

  @Test
  private void testFindByCaseUPCnotFound() throws Exception {

    when(itemService.findLatestItemByUPC(anyString())).thenReturn(new ArrayList<>());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/items/search/upcs").headers(MockHttpHeaders.getHeaders()))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().isNotFound());
    verify(itemService, times(0)).findLatestItemByUPC(anyString());
  }

  @Test
  private void testUpdateVendorCompliance_Success() throws Exception {
    doNothing().when(regulatedItemService).updateVendorComplianceItem(any(), anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/items/vendor-compliance")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(getUpdateVendorComplianceRequest())))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(regulatedItemService, times(1))
        .updateVendorComplianceItem(eq(VendorCompliance.LITHIUM_ION), eq("12345678"));
  }

  @Test
  private void testItemOverride_Success() throws Exception {
    doNothing()
        .when(itemService)
        .itemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/items/override")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(getItemOverrideRequest())))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(itemService, times(1))
        .itemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
  }

  @Test
  private void testItemOverride_ThrowsException() throws Exception {
    doThrow(new ReceivingInternalException("some error", "some error"))
        .when(itemService)
        .itemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/items/override")
                  .headers(MockHttpHeaders.getHeaders())
                  .content(JacksonParser.writeValueAsString(getItemOverrideRequest())))
          .andExpect(MockMvcResultMatchers.status().isInternalServerError());
    } catch (Exception e) {
      System.out.println("exception: " + e.getMessage() + e.getStackTrace());
      assertTrue(true);
    }
    verify(itemService, times(1))
        .itemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
  }

  private List<Long> getSuccessItem() {

    List<Long> itemNumbers = new ArrayList<>();
    itemNumbers.add(12345678L);
    itemNumbers.add(12345678L);
    itemNumbers.add(12345678L);
    return itemNumbers;
  }

  private List<ItemInfoResponse> getSuccessBaseDivCode() {
    List<ItemInfoResponse> baseDivCodes = new ArrayList<>();
    baseDivCodes.add(new ItemInfoResponse(1234567890l, "WM"));
    baseDivCodes.add(new ItemInfoResponse(1234567891l, "SAMS"));
    return baseDivCodes;
  }

  private UpdateVendorComplianceRequest getUpdateVendorComplianceRequest() {
    UpdateVendorComplianceRequest updateVendorComplianceRequest =
        new UpdateVendorComplianceRequest();
    updateVendorComplianceRequest.setItemNumber("12345678");
    updateVendorComplianceRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION);
    return updateVendorComplianceRequest;
  }

  @Test
  private void testSaveConfirmation_Success() throws Exception {
    doNothing()
        .when(deliveryItemOverrideService)
        .updateCountryOfOriginAndPackAknowlegementInfo(
            any(SaveConfirmationRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/items/saveConfirmation")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(getSaveConfirmationUserAckRequest())))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(deliveryItemOverrideService, times(1))
        .updateCountryOfOriginAndPackAknowlegementInfo(
            any(SaveConfirmationRequest.class), any(HttpHeaders.class));
  }

  @Test
  private void testSaveConfirmation_ThrowsException() throws Exception {
    doThrow(new ReceivingBadDataException("some error", "some error"))
        .when(itemService)
        .saveConfirmationRequest(any(SaveConfirmationRequest.class));
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/items/saveConfirmation")
                  .headers(MockHttpHeaders.getHeaders())
                  .content(JacksonParser.writeValueAsString(getSaveConfirmationUserAckRequest())))
          .andExpect(MockMvcResultMatchers.status().isBadRequest());
    } catch (Exception e) {
      System.out.println("exception: " + e.getMessage() + e.getStackTrace());
      assertTrue(true);
    }
    verify(itemService, times(1)).saveConfirmationRequest(any(SaveConfirmationRequest.class));
  }

  private ItemOverrideRequest getItemOverrideRequest() {
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    return itemOverrideRequest;
  }

  private SaveConfirmationRequest getSaveConfirmationUserAckRequest() {
    SaveConfirmationRequest saveCOOConfirmationRequest =
        new SaveConfirmationRequest(
            232L, 32432L, Collections.singletonList("MEXICO"), 1, 1, true, false, true);
    return saveCOOConfirmationRequest;
  }
}
