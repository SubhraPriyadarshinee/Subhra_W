package com.walmart.move.nim.receiving.rc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rc.contants.ActionType;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerDetails;
import com.walmart.move.nim.receiving.rc.model.dto.request.*;
import com.walmart.move.nim.receiving.rc.service.RcContainerService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcContainerControllerTest extends ReceivingControllerTestBase {
  @InjectMocks private RcContainerController rcContainerController;
  @Mock private RcContainerService rcContainerService;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  private RestResponseExceptionHandler restResponseExceptionHandler;
  private MockMvc mockMvc;
  private Gson gson;
  private ReceiveContainerRequest receiveContainerRequest;
  private UpdateContainerRequest updateContainerRequest;
  private PublishContainerItem publishContainerItem;
  private ContainerRLog containerRLog, containerRLogWithReturnOrderAsNull;
  private UpdateReturnOrderDataRequest updateReturnOrderDataRequest;

  @BeforeClass
  public void init() throws IOException {
    MockitoAnnotations.initMocks(this);
    gson =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .setExclusionStrategies(
                new ExclusionStrategy() {
                  @Override
                  public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                    return fieldAttributes.getName().equals("question");
                  }

                  @Override
                  public boolean shouldSkipClass(Class<?> aClass) {
                    return false;
                  }
                })
            .create();
    String receiveContainerDataPath =
        new File("../../receiving-test/src/main/resources/json/RcReceiveContainerRequest.json")
            .getCanonicalPath();
    receiveContainerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(receiveContainerDataPath))),
            ReceiveContainerRequest.class);
    String updateContainerDataPath =
        new File("../../receiving-test/src/main/resources/json/RcUpdateContainerRequest.json")
            .getCanonicalPath();
    updateContainerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(updateContainerDataPath))),
            UpdateContainerRequest.class);
    String dataPathContainer =
        new File("../../receiving-test/src/main/resources/json/RcContainer.json")
            .getCanonicalPath();
    containerRLog =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainer))), ContainerRLog.class);
    publishContainerItem =
        PublishContainerItem.builder()
            .actionType(ActionType.ITEM_MISSING_UPDATE)
            .ignoreSct(true)
            .rcContainerDetails(RcContainerDetails.builder().containerRLog(containerRLog).build())
            .build();
    String dataPathUpdateReturnOrderDataRequest =
        new File("../../receiving-test/src/main/resources/json/RcUpdateReturnOrderDataRequest.json")
            .getCanonicalPath();
    updateReturnOrderDataRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathUpdateReturnOrderDataRequest))),
            UpdateReturnOrderDataRequest.class);
    String dataPathContainerWhenReturnOrderNull =
        new File("../../receiving-test/src/main/resources/json/RcContainerWithoutReturnOrder.json")
            .getCanonicalPath();
    containerRLogWithReturnOrderAsNull =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerWhenReturnOrderNull))),
            ContainerRLog.class);
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rcContainerController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(rcContainerService);
  }

  @Test
  public void testReceiveContainer() throws Exception {
    when(rcContainerService.receiveContainer(
            any(ReceiveContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(RcContainerDetails.builder().containerRLog(containerRLog).build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/receive")
                .content(gson.toJson(receiveContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());

    verify(rcContainerService, times(1))
        .receiveContainer(any(ReceiveContainerRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testUpdateContainer() throws Exception {
    when(rcContainerService.updateContainer(
            any(), any(UpdateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(RcContainerDetails.builder().containerRLog(containerRLog).build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/container/update/e09074000100020003")
                .content(gson.toJson(updateContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());

    verify(rcContainerService, times(1))
        .updateContainer(anyString(), any(UpdateContainerRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testPublishContainers() throws Exception {
    publishContainerItem.setIgnoreRap("false");
    publishContainerItem.setIgnoreWfs("false");
    PublishContainerRequest publishContainerRequest =
        PublishContainerRequest.builder()
            .publishContainerItemList(Collections.singletonList(publishContainerItem))
            .build();
    doNothing()
        .when(rcContainerService)
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/publish")
                .content(gson.toJson(publishContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1))
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
  }

  @Test
  public void testPublishContainers_Multiple() throws Exception {
    PublishContainerRequest publishContainerRequest =
        PublishContainerRequest.builder()
            .publishContainerItemList(Arrays.asList(publishContainerItem, publishContainerItem))
            .build();
    doNothing()
        .when(rcContainerService)
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/publish")
                .content(gson.toJson(publishContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(2))
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
  }

  @Test
  public void testPublishContainers_MissingRcContainerDetails() throws Exception {
    PublishContainerRequest publishContainerRequest =
        PublishContainerRequest.builder()
            .publishContainerItemList(
                Collections.singletonList(
                    PublishContainerItem.builder()
                        .actionType(ActionType.ITEM_MISSING)
                        .ignoreSct(true)
                        .build()))
            .build();
    doNothing()
        .when(rcContainerService)
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/publish")
                .content(gson.toJson(publishContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().is4xxClientError());
    verify(rcContainerService, times(0))
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
  }

  @Test
  public void testPublishContainers_MissingActionType() throws Exception {
    PublishContainerRequest publishContainerRequest =
        PublishContainerRequest.builder()
            .publishContainerItemList(
                Collections.singletonList(
                    PublishContainerItem.builder()
                        .rcContainerDetails(
                            RcContainerDetails.builder().containerRLog(containerRLog).build())
                        .ignoreSct(true)
                        .build()))
            .build();
    doNothing()
        .when(rcContainerService)
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/publish")
                .content(gson.toJson(publishContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().is4xxClientError());
    verify(rcContainerService, times(0))
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
  }

  @Test
  public void testPublishContainers_MissingIgnoreSCT() throws Exception {
    PublishContainerRequest publishContainerRequest =
        PublishContainerRequest.builder()
            .publishContainerItemList(
                Collections.singletonList(
                    PublishContainerItem.builder()
                        .rcContainerDetails(
                            RcContainerDetails.builder().containerRLog(containerRLog).build())
                        .actionType(ActionType.ITEM_MISSING_RECEIPT)
                        .build()))
            .build();
    doNothing()
        .when(rcContainerService)
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/publish")
                .content(gson.toJson(publishContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().is4xxClientError());
    verify(rcContainerService, times(0))
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
  }

  @Test
  public void testPublishContainers_MissingContainerRlog() throws Exception {
    PublishContainerRequest publishContainerRequest =
        PublishContainerRequest.builder()
            .publishContainerItemList(
                Collections.singletonList(
                    PublishContainerItem.builder()
                        .rcContainerDetails(
                            RcContainerDetails.builder().containerRLog(null).build())
                        .actionType(ActionType.ITEM_MISSING_RECEIPT)
                        .ignoreSct(true)
                        .build()))
            .build();
    doNothing()
        .when(rcContainerService)
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/container/publish")
                .content(gson.toJson(publishContainerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().is4xxClientError());
    verify(rcContainerService, times(0))
        .publishContainer(
            any(RcContainerDetails.class), any(HttpHeaders.class), any(), any(), any(), any());
  }

  @Test
  public void testGetContainerByGtin() throws Exception {
    when(rcContainerService.getLatestReceivedContainerByGtin(anyString(), any()))
        .thenReturn(containerRLog);
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/gtin/00604015693198")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1)).getLatestReceivedContainerByGtin(anyString(), any());
  }

  @Test
  public void testGetContainerByGtin_NotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_GTIN_ERROR_MSG, "00604015693199");
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription))
        .when(rcContainerService)
        .getLatestReceivedContainerByGtin(anyString(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/gtin/00604015693199")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcContainerService, times(1)).getLatestReceivedContainerByGtin(anyString(), any());
  }

  @Test
  public void testGetContainerByGtin_WithDispositionType() throws Exception {
    when(rcContainerService.getLatestReceivedContainerByGtin(anyString(), anyString()))
        .thenReturn(containerRLog);
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/gtin/00604015693198?dispositionType=RTV")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1)).getLatestReceivedContainerByGtin(anyString(), anyString());
  }

  @Test
  public void testGetContainerByGtin_WithDispositionType_NotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants
                .CONTAINER_NOT_FOUND_BY_GTIN_FOR_DISPOSITION_TYPE_ERROR_MSG,
            "00604015693199",
            "RTV");
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription))
        .when(rcContainerService)
        .getLatestReceivedContainerByGtin(anyString(), anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/gtin/00604015693199?dispositionType=RTV")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcContainerService, times(1)).getLatestReceivedContainerByGtin(anyString(), anyString());
  }

  @Test
  public void testGetContainerByTrackingId() throws Exception {
    when(rcContainerService.getReceivedContainerByTrackingId(anyString()))
        .thenReturn(containerRLog);
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/b090740000100000001352859")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1)).getReceivedContainerByTrackingId(anyString());
  }

  @Test
  public void testGetContainerByTrackingId_NotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
            "b090740000100000001352859");
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription))
        .when(rcContainerService)
        .getReceivedContainerByTrackingId(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/b090740000100000001352859")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcContainerService, times(1)).getReceivedContainerByTrackingId(anyString());
  }

  @Test
  public void testGetContainerByPackageBarCode() throws Exception {
    when(rcContainerService.getReceivedContainersByPackageBarCode(anyString()))
        .thenReturn(Collections.singletonList(containerRLog));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/package/ba12cd456000100000001352859")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1)).getReceivedContainersByPackageBarCode(anyString());
  }

  @Test
  public void testGetContainerByPackageBarCode_NotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
            "ba12cd456000100000001234567");
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription))
        .when(rcContainerService)
        .getReceivedContainersByPackageBarCode(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/container/package/ba12cd456000100000001234567")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcContainerService, times(1)).getReceivedContainersByPackageBarCode(anyString());
  }

  @Test
  public void testGetContainerBySoNumber() throws Exception {
    when(rcContainerService.getReceivedContainersBySoNumber(anyString()))
        .thenReturn(Collections.singletonList(containerRLog));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/returns/container/package/soNumber/ba12cd456000100000001352859")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());
    verify(rcContainerService, times(1)).getReceivedContainersBySoNumber(anyString());
  }

  @Test
  public void testGetContainerBySoNumber_NotFound() throws Exception {
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_SO_NUMBER_ERROR_MSG,
            "ba12cd456000100000001234567");
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription))
        .when(rcContainerService)
        .getReceivedContainersBySoNumber(anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/returns/container/package/soNumber/ba12cd456000100000001234567")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isNotFound());
    verify(rcContainerService, times(1)).getReceivedContainersBySoNumber(anyString());
  }

  @Test
  public void testUpdateReturOrderData() throws Exception {
    doNothing()
        .when(rcContainerService)
        .updateReturnOrderData(
            updateReturnOrderDataRequest, MockHttpHeaders.getHeaders("9074", "US"));
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/container/update/ro/b090740000200000000679908")
                .content(gson.toJson(updateReturnOrderDataRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());

    verify(rcContainerService, times(1))
        .updateReturnOrderData(any(UpdateReturnOrderDataRequest.class), any());
  }
}
