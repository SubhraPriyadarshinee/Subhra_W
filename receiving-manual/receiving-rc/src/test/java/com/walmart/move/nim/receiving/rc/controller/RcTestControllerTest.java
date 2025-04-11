package com.walmart.move.nim.receiving.rc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.core.service.ItemTrackerService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rc.entity.PackageRLog;
import com.walmart.move.nim.receiving.rc.service.RcContainerService;
import com.walmart.move.nim.receiving.rc.service.RcPackageTrackerService;
import com.walmart.move.nim.receiving.rc.service.RcWorkflowService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcTestControllerTest extends ReceivingControllerTestBase {
  @InjectMocks private RcTestController rcTestController;
  @Mock private RcContainerService rcContainerService;
  @Mock private RcPackageTrackerService rcPackageTrackerService;
  @Mock private ItemTrackerService itemTrackerService;
  @Mock private RcWorkflowService rcWorkflowService;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  private RestResponseExceptionHandler restResponseExceptionHandler;
  private MockMvc mockMvc;
  private Gson gson;
  private PackageRLog packageRLog;
  private ItemTracker itemTracker;

  @BeforeClass
  public void init() throws IOException {
    MockitoAnnotations.initMocks(this);
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rcTestController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
    gson = new Gson();
    String dataPathPackageRLog =
        new File("../../receiving-test/src/main/resources/json/RcPackageRLog.json")
            .getCanonicalPath();
    packageRLog =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageRLog))), PackageRLog.class);
    String dataPathItemTracker =
        new File("../../receiving-test/src/main/resources/json/ItemTracker.json")
            .getCanonicalPath();
    itemTracker =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTracker))), ItemTracker.class);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(rcContainerService);
    Mockito.reset(rcPackageTrackerService);
    Mockito.reset(itemTrackerService);
  }

  @Test
  public void testReceiveContainer() throws Exception {
    doNothing().when(rcContainerService).deleteContainersByPackageBarcode(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/delete/containers/5512098217046")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1)).deleteContainersByPackageBarcode(anyString());
  }

  @Test
  public void testReceiveContainerNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
            "5512098217046");
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription))
        .when(rcContainerService)
        .deleteContainersByPackageBarcode(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/delete/containers/5512098217046")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcContainerService, times(1)).deleteContainersByPackageBarcode(anyString());
  }

  @Test
  public void testGetTrackedPackage() throws Exception {
    when(rcPackageTrackerService.getTrackedPackage(anyString()))
        .thenReturn(Collections.singletonList(packageRLog));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/test/tracked/package/5512098217046")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcPackageTrackerService, times(1)).getTrackedPackage(anyString());
  }

  @Test
  public void testGetTrackedPackageNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.PACKAGE_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
            "5512098217047");
    when(rcPackageTrackerService.getTrackedPackage(anyString()))
        .thenThrow(
            new ReceivingDataNotFoundException(ExceptionCodes.PACKAGE_NOT_FOUND, errorDescription));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/test/tracked/package/5512098217047")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcPackageTrackerService, times(1)).getTrackedPackage(anyString());
  }

  @Test
  public void testDeleteTrackedPackage() throws Exception {
    doNothing().when(rcPackageTrackerService).deleteTrackedPackage(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/tracked/package/5512098217046")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcPackageTrackerService, times(1)).deleteTrackedPackage(anyString());
  }

  @Test
  public void testDeleteTrackedPackageNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.PACKAGE_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
            "5512098217047");
    doThrow(new ReceivingDataNotFoundException(ExceptionCodes.PACKAGE_NOT_FOUND, errorDescription))
        .when(rcPackageTrackerService)
        .deleteTrackedPackage(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/tracked/package/5512098217047")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcPackageTrackerService, times(1)).deleteTrackedPackage(anyString());
  }

  @Test
  public void testGetTrackedItemByTrackingId() throws Exception {
    when(itemTrackerService.getTrackedItemByTrackingId(anyString()))
        .thenReturn(Collections.singletonList(itemTracker));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/test/tracked/item/trackingId/5512098217046")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(itemTrackerService, times(1)).getTrackedItemByTrackingId(anyString());
  }

  @Test
  public void testGetTrackedItemByTrackingIdNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
            "5512098217047");
    when(itemTrackerService.getTrackedItemByTrackingId(anyString()))
        .thenThrow(
            new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/test/tracked/item/trackingId/5512098217047")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(itemTrackerService, times(1)).getTrackedItemByTrackingId(anyString());
  }

  @Test
  public void testDeleteTrackedItemByTrackingId() throws Exception {
    doNothing().when(itemTrackerService).deleteTrackedItemByTrackingId(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/tracked/item/trackingId/5512098217046")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(itemTrackerService, times(1)).deleteTrackedItemByTrackingId(anyString());
  }

  @Test
  public void testDeleteTrackedItemByTrackingIdNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
            "5512098217047");
    doThrow(new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription))
        .when(itemTrackerService)
        .deleteTrackedItemByTrackingId(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/tracked/item/trackingId/5512098217047")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(itemTrackerService, times(1)).deleteTrackedItemByTrackingId(anyString());
  }

  @Test
  public void testGetTrackedItemByGtin() throws Exception {
    when(itemTrackerService.getTrackedItemByGtin(anyString()))
        .thenReturn(Collections.singletonList(itemTracker));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/test/tracked/item/gtin/00604015693198")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(itemTrackerService, times(1)).getTrackedItemByGtin(anyString());
  }

  @Test
  public void testGetTrackedItemByGtinNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_GTIN_ERROR_MSG, "00604015693199");
    when(itemTrackerService.getTrackedItemByGtin(anyString()))
        .thenThrow(
            new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/test/tracked/item/gtin/00604015693199")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(itemTrackerService, times(1)).getTrackedItemByGtin(anyString());
  }

  @Test
  public void testDeleteTrackedItemByGtin() throws Exception {
    doNothing().when(itemTrackerService).deleteTrackedItemByGtin(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/tracked/item/gtin/00604015693198")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(itemTrackerService, times(1)).deleteTrackedItemByGtin(anyString());
  }

  @Test
  public void testDeleteTrackedItemByGtinNotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_GTIN_ERROR_MSG, "00604015693199");
    doThrow(new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription))
        .when(itemTrackerService)
        .deleteTrackedItemByGtin(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/tracked/item/gtin/00604015693199")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(itemTrackerService, times(1)).deleteTrackedItemByGtin(anyString());
  }

  @Test
  public void testDeleteWorkflowById() throws Exception {
    doNothing().when(rcWorkflowService).deleteWorkflowById(any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/workflow/1")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcWorkflowService, times(1)).deleteWorkflowById(any());
  }

  @Test
  public void testDeleteWorkflowItemById() throws Exception {
    doNothing().when(rcWorkflowService).deleteWorkflowItemById(any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/returns/test/workflow-item/1")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcWorkflowService, times(1)).deleteWorkflowItemById(any());
  }
}
