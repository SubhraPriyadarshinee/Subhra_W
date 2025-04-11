package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncLocationServiceTest {

  @Mock LocationService locationService;

  @InjectMocks AsyncLocationService asyncLocationServiceTest = new AsyncLocationService();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(locationService);
  }

  @SneakyThrows
  @Test
  public void getBulkLocationInfo_SuccessResponse() {

    JsonObject jsonObjects = new JsonObject();

    doReturn(jsonObjects)
        .when(locationService)
        .getBulkLocationInfo(anyList(), any(HttpHeaders.class));

    CompletableFuture<JsonObject> completableFuture =
        asyncLocationServiceTest.getBulkLocationInfo(
            Collections.singletonList("124"), getHttpHeaders());
    JsonObject response = completableFuture.get();
    assertEquals(response, jsonObjects);
    verify(locationService, times(1)).getBulkLocationInfo(anyList(), any(HttpHeaders.class));
  }

  public static HttpHeaders getHttpHeaders() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    requestHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.DEFAULT_USER);
    return requestHeaders;
  }
}
