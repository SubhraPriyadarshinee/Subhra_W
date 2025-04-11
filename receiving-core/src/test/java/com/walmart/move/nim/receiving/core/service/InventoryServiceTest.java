package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVENTORY_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IDEM_POTENCY_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_PALLET_OFF_HOLD_V2;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_PALLET_ON_HOLD_V2;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INV_V2_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_LABEL_USER_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import lombok.SneakyThrows;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class InventoryServiceTest extends ReceivingTestBase {
  @InjectMocks private InventoryService inventoryService;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RestUtils restUtils;
  @Mock private RestConnector simpleRestConnector;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private AsyncPersister asyncPersister;
  @Mock private InventoryRestApiClient inventoryRestApiClient;
  @Mock private ContainerItemRepository containerItemRepository;
  @Captor private ArgumentCaptor<HttpEntity> httpEntityArgumentCaptor;
  @Mock private Gson gson = new Gson();
  @Mock RestClientResponseException restClientResponseException;
  private String inventoryBaseUrl = "http://localhost:8080/inventory";
  private String trackingId = "030181107692957111";
  private List<String> trackingIds = Arrays.asList("001000132679203193");
  @Mock EndgameOutboxHandler endgameOutboxHandler;

  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();

  @BeforeMethod
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(inventoryService, "gsonUTCDateAdapter", new Gson());
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  private Container getContainer() {
    ContainerItem ci = new ContainerItem();
    ci.setTrackingId(trackingId);
    ci.setFinancialReportingGroupCode("us");
    ci.setBaseDivisionCode("wm");
    Container container = new Container();
    container.setTrackingId(trackingId);
    container.setContainerItems(Arrays.asList(ci));
    return container;
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(appConfig, tenantSpecificConfigReader, restUtils, asyncPersister);
  }

  @Test
  public void testUpdateInventoryStatusFailedForBadRequest() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);

    when(restUtils.post(any(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("{}", HttpStatus.BAD_REQUEST));

    try {
      inventoryService.onHold(getContainer(), httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(ReceivingException.INVENTORY_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(ReceivingException.INVENTORY_ERROR_MSG, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testUpdateInventoryStatusFailedForServiceUnavailability() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);

    when(restUtils.post(any(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("{}", HttpStatus.SERVICE_UNAVAILABLE));

    try {
      inventoryService.onHold(getContainer(), httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(
          ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
          e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testUpdateInventoryStatusSuccess() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);

    final ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    when(restUtils.post(any(), any(), any(), any())).thenReturn(responseEntity);

    try {
      inventoryService.onHold(getContainer(), httpHeaders);
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @SneakyThrows
  @Test
  public void testUpdateInventoryStatusSuccess_Inventory1Url() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);

    final ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    when(restUtils.post(any(), any(), any(), any())).thenReturn(responseEntity);

    // setup
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32987", INV_V2_ENABLED, false);
    doReturn("https://inventory-server.dev.prod.us.walmart.net")
        .when(appConfig)
        .getInventoryBaseUrl();
    doReturn("https://gls-atlas-inventory-core-gdc-qa.walmart.com")
        .when(appConfig)
        .getInventoryCoreBaseUrl();

    // execute
    inventoryService.onHold(getContainer(), httpHeaders);

    // verify
    ArgumentCaptor<String> urlArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restUtils, times(1))
        .post(urlArgumentCaptor.capture(), httpHeadersArgumentCaptor.capture(), any(), anyString());
    final String argumentCaptorUrlValue = urlArgumentCaptor.getValue();
    assertEquals(
        argumentCaptorUrlValue,
        "https://inventory-server.dev.prod.us.walmart.net/inventory/inventories/items/hold");
    final HttpHeaders argumentCaptorHeaderValue = httpHeadersArgumentCaptor.getValue();
    final String idemPotencyKey = argumentCaptorHeaderValue.getFirst(IDEM_POTENCY_KEY);
    assertNotNull(idemPotencyKey);
  }

  @SneakyThrows
  @Test
  public void testUpdateInventoryStatusSuccess_Inventory2Url() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);

    final ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    when(restUtils.post(any(), any(), any(), any())).thenReturn(responseEntity);
    // setup
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32987", INV_V2_ENABLED, false);
    final String invCoreUrl = "https://gls-atlas-inventory-core-gdc-qa.walmart.com";
    doReturn(invCoreUrl).when(appConfig).getInventoryCoreBaseUrl();
    doReturn("dummybody").when(inventoryRestApiClient).createInventoryOnHoldRequest(anyString());

    // execute
    inventoryService.onHold(getContainer(), httpHeaders);

    // verify
    ArgumentCaptor<String> urlArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restUtils, times(1))
        .post(urlArgumentCaptor.capture(), httpHeadersArgumentCaptor.capture(), any(), anyString());
    final String argumentCaptorUrlValue = urlArgumentCaptor.getValue();
    assertEquals(argumentCaptorUrlValue, invCoreUrl + INVENTORY_PALLET_ON_HOLD_V2);
    final HttpHeaders argumentCaptorHeaderValue = httpHeadersArgumentCaptor.getValue();
    final String idemPotencyKey = argumentCaptorHeaderValue.getFirst(IDEM_POTENCY_KEY);
    assertNotNull(idemPotencyKey);
  }

  @Test
  public void testDeleteContainerIsSuccess() {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID, trackingId);
    String url =
        inventoryBaseUrl
            + ReceivingUtils.replacePathParams(
                ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH, pathParams);

    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), same(String.class)))
        .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
    when(appConfig.getInventoryContainerDeletePath())
        .thenReturn(ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH);
    inventoryService.deleteContainer(trackingId, httpHeaders);

    verify(simpleRestConnector, times(1))
        .exchange(
            eq(url), eq(HttpMethod.DELETE), httpEntityArgumentCaptor.capture(), same(String.class));

    HttpHeaders captorHeaders = httpEntityArgumentCaptor.getValue().getHeaders();
    assertTrue(captorHeaders.containsKey(ReceivingConstants.REQUEST_ORIGINATOR));
    assertSame(
        captorHeaders.getFirst(ReceivingConstants.REQUEST_ORIGINATOR),
        ReceivingConstants.APP_NAME_VALUE);
  }

  @Test
  public void testDeleteContainer_inventory404() {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID, trackingId);
    String url =
        inventoryBaseUrl
            + ReceivingUtils.replacePathParams(
                ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH, pathParams);
    when(appConfig.getInventoryContainerDeletePath())
        .thenReturn(ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH);

    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), same(String.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.NOT_FOUND, null, "".getBytes(), Charset.forName("UTF-8")));

    inventoryService.deleteContainer(trackingId, httpHeaders);
    verify(simpleRestConnector, times(1))
        .exchange(eq(url), eq(HttpMethod.DELETE), any(HttpEntity.class), same(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Inventory not found for tracking id.*")
  public void testDeleteContainer_inventoryError() {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID, trackingId);
    String url =
        inventoryBaseUrl
            + ReceivingUtils.replacePathParams(
                ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH, pathParams);
    when(appConfig.getInventoryContainerDeletePath())
        .thenReturn(ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH);

    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), same(String.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.CONFLICT, null, "".getBytes(), Charset.forName("UTF-8")));

    inventoryService.deleteContainer(trackingId, httpHeaders);
    verify(simpleRestConnector, times(1))
        .exchange(eq(url), eq(HttpMethod.DELETE), any(HttpEntity.class), same(String.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Error while calling Inventory")
  public void testInventoryDown() {
    doThrow(new ResourceAccessException("Error"))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), same(String.class));
    when(appConfig.getInventoryContainerDeletePath())
        .thenReturn(ReceivingConstants.INVENTORY_CONTAINERS_DELETE_PATH);
    inventoryService.deleteContainer(trackingId, httpHeaders);
  }

  @SneakyThrows
  @Test
  public void palletOffHold_success_200() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    final ResponseEntity<String> goodResponse_http200 =
        new ResponseEntity<>("{success}", HttpStatus.OK);
    doReturn(goodResponse_http200).when(restUtils).post(any(), any(), any(), any());
    // setup
    // doReturn(false).when(tenantSpecificConfigReader).getConfiguredFeatureFlag("32987",
    // INV_V2_ENABLED, false);
    doReturn("https://inventory-server.dev.prod.us.walmart.net")
        .when(appConfig)
        .getInventoryBaseUrl();
    inventoryService.palletOffHold(trackingId, httpHeaders);

    // verify
    ArgumentCaptor<String> argumentCaptorUrl = ArgumentCaptor.forClass(String.class);
    verify(restUtils, times(1)).post(argumentCaptorUrl.capture(), any(), any(), anyString());
    final String argumentCaptorUrlValue = argumentCaptorUrl.getValue();
    assertEquals(
        argumentCaptorUrlValue,
        "https://inventory-server.dev.prod.us.walmart.net/inventory/inventories/items/unhold");
  }

  @SneakyThrows
  @Test
  public void palletOffHold_success_Inventory2Url() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    final ResponseEntity<String> goodResponse_http200 =
        new ResponseEntity<>("{success}", HttpStatus.OK);
    doReturn(goodResponse_http200).when(restUtils).post(any(), any(), any(), any());

    // setup
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32987", INV_V2_ENABLED, false);
    final String invCoreUrl = "https://gls-atlas-inventory-core-gdc-qa.walmart.com";
    doReturn(invCoreUrl).when(appConfig).getInventoryCoreBaseUrl();
    doReturn("dummybody").when(inventoryRestApiClient).createInventoryOffHoldRequest(anyString());
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);

    inventoryService.palletOffHold(trackingId, httpHeaders);

    // verify
    ArgumentCaptor<String> argumentCaptorUrl = ArgumentCaptor.forClass(String.class);
    verify(restUtils, times(1))
        .post(argumentCaptorUrl.capture(), httpHeadersArgumentCaptor.capture(), any(), anyString());
    final String argumentCaptorUrlValue = argumentCaptorUrl.getValue();
    assertEquals(argumentCaptorUrlValue, invCoreUrl + INVENTORY_PALLET_OFF_HOLD_V2);

    // mandatory headers
    final HttpHeaders argumentCaptorHeaderValue = httpHeadersArgumentCaptor.getValue();
    final String idemPotencyKey = argumentCaptorHeaderValue.getFirst(IDEM_POTENCY_KEY);
    assertNotNull(idemPotencyKey);
  }

  @SneakyThrows
  @Test
  public void palletOffHold_BadRequest_400() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    try {
      String badDataResponseBody =
          "[\n"
              + "  {\n"
              + "    \"type\": \"error\",\n"
              + "    \"code\": \"GLS-INV-BE-000250\",\n"
              + "    \"desc\": \"No InventoryLines found for itemOnHold request.\"\n"
              + "  }\n"
              + "]";
      final ResponseEntity<String> badDataResponse_http400 =
          new ResponseEntity<>(badDataResponseBody, HttpStatus.BAD_REQUEST);
      doReturn(badDataResponse_http400).when(restUtils).post(any(), any(), any(), any());
      inventoryService.palletOffHold(trackingId, httpHeaders);
    } catch (ReceivingException e) {
      assertTrue(!e.getHttpStatus().is2xxSuccessful());
    }
  }

  @SneakyThrows
  @Test
  public void palletOffHold_BadRequest_500() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    String badDataResponseBody =
        "[\n"
            + "  {\n"
            + "    \"type\": \"error\",\n"
            + "    \"code\": \"GLS-INV-BE-000500\",\n"
            + "    \"desc\": \"INVENTORY_SERVICE_DOWN .\"\n"
            + "  }\n"
            + "]";
    try {
      final ResponseEntity<String> badResponse_http500 =
          new ResponseEntity<>(badDataResponseBody, HttpStatus.INTERNAL_SERVER_ERROR);
      doReturn(badResponse_http500).when(restUtils).post(any(), any(), any(), any());
      inventoryService.palletOffHold(trackingId, httpHeaders);
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus().is5xxServerError());
    }
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void getContainerLocation_InventoryNotFoundForTrackingId() throws ReceivingException {
    final ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    inventoryService.getContainerLocation(trackingId, httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid header key=WMT_UserId, value=null")
  public void getContainerLocation_NoUserId() throws ReceivingException {
    HttpHeaders httpHeadersNoUserId = GdcHttpHeaders.getHeaders();
    httpHeadersNoUserId.set(PRINT_LABEL_USER_ID, null);
    httpHeadersNoUserId.set(USER_ID_HEADER_KEY, null);
    inventoryService.getContainerLocation(trackingId, httpHeadersNoUserId);
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    fail("Bad request exception is supposed to be thrown");
  }

  @SneakyThrows
  @Test
  public void getContainerLocation_success() {
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final String containerLocation = inventoryService.getContainerLocation(trackingId, httpHeaders);
    assertEquals(containerLocation, "211");
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Inventory not found for tracking id = .*")
  public void getContainerLocation_throw_RestClientResponseException() throws ReceivingException {
    doThrow(
            new RestClientResponseException(
                "call failed", HttpStatus.BAD_REQUEST.value(), "call failed", null, null, null))
        .when(simpleRestConnector)
        .exchange(anyString(), any(), any(HttpEntity.class), any(Class.class));

    inventoryService.getContainerLocation(trackingId, httpHeaders);

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    fail("Bad request exception is supposed to be thrown");
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Error while calling Inventory")
  public void getContainerLocation_throw_ResourceAccessException() throws ReceivingException {
    doThrow(new ResourceAccessException("call failed"))
        .when(simpleRestConnector)
        .exchange(anyString(), any(), any(HttpEntity.class), any(Class.class));
    inventoryService.getContainerLocation(trackingId, httpHeaders);

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    fail("Bad request exception is supposed to be thrown");
  }

  @SneakyThrows
  @Test
  public void getContainerDetails() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final String containerDetails = inventoryService.getContainerDetails(trackingId, httpHeaders);
    assertEquals(containerDetails, successResponse);

    verify(simpleRestConnector, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.GET),
            httpEntityArgumentCaptor.capture(),
            same(String.class));
    HttpHeaders captorHeaders = httpEntityArgumentCaptor.getValue().getHeaders();
    assertTrue(captorHeaders.containsKey(ReceivingConstants.REQUEST_ORIGINATOR));
    assertSame(
        captorHeaders.getFirst(ReceivingConstants.REQUEST_ORIGINATOR),
        ReceivingConstants.APP_NAME_VALUE);
  }

  @SneakyThrows
  @Test
  public void getContainerDetails_Inventory2Url() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    // setup
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", INV_V2_ENABLED, false);
    doReturn("https://gls-atlas-inventory-QUERY-gdc-qa.walmart.com")
        .when(appConfig)
        .getInventoryQueryBaseUrl();
    ArgumentCaptor<String> urlArgumentCaptor = ArgumentCaptor.forClass(String.class);

    final String containerDetails = inventoryService.getContainerDetails(trackingId, httpHeaders);
    assertEquals(containerDetails, successResponse);

    verify(simpleRestConnector, times(1))
        .exchange(
            urlArgumentCaptor.capture(),
            eq(HttpMethod.GET),
            httpEntityArgumentCaptor.capture(),
            same(String.class));

    final String argumentCaptorUrlValue = urlArgumentCaptor.getValue();
    assertEquals(
        argumentCaptorUrlValue,
        "https://gls-atlas-inventory-QUERY-gdc-qa.walmart.com/inventory/inventories/containers/030181107692957111?details=true");

    final HttpEntity httpEntityArgumentCaptorValue = httpEntityArgumentCaptor.getValue();
    HttpHeaders captorheaders = httpEntityArgumentCaptorValue.getHeaders();
    final String idemPotencyKey = captorheaders.getFirst(IDEM_POTENCY_KEY);
    assertNotNull(idemPotencyKey);

    assertTrue(captorheaders.containsKey(ReceivingConstants.REQUEST_ORIGINATOR));
    assertSame(
        captorheaders.getFirst(ReceivingConstants.REQUEST_ORIGINATOR),
        ReceivingConstants.APP_NAME_VALUE);
  }

  @SneakyThrows
  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid header key=WMT_UserId, value=.*")
  public void getContainerDetails_failure_forInvalidUserIdInHeaders() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    HttpHeaders httpHeadersNotHavingUserId = GdcHttpHeaders.getHeaders();
    httpHeadersNotHavingUserId.set(ReceivingConstants.USER_ID_HEADER_KEY, null);

    inventoryService.getContainerDetails(trackingId, httpHeadersNotHavingUserId);
  }

  @SneakyThrows
  @Test
  public void getInventoryQty_success() {
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final Integer inventoryQty =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders).getInventoryQty();
    assertEquals(inventoryQty.intValue(), 160);
  }

  @SneakyThrows
  @Test
  public void getInventory_multi_containerlist_success() {
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", IS_INVENTORY_VALID_ITEM_CHECK_ENABLED, false);
    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final InventoryContainerDetails inventoryContainerDetails =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders);
    assertEquals(inventoryContainerDetails.getInventoryQty().intValue(), 160);
    assertEquals(inventoryContainerDetails.getContainerStatus(), AVAILABLE);
    assertEquals(inventoryContainerDetails.getDestinationLocationId(), ZERO_QTY);
    assertEquals(inventoryContainerDetails.getAllocatedQty(), ZERO_QTY);
  }

  @SneakyThrows
  @Test
  public void getInventory_single_containerlist_success() {
    File resources =
        new ClassPathResource("inventory_get_multi_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", IS_INVENTORY_VALID_ITEM_CHECK_ENABLED, false);
    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final InventoryContainerDetails inventoryContainerDetails =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders);
    assertEquals(inventoryContainerDetails.getInventoryQty().intValue(), 160);
    assertEquals(inventoryContainerDetails.getContainerStatus(), AVAILABLE);
    assertEquals(inventoryContainerDetails.getDestinationLocationId(), ZERO_QTY);
    assertEquals(inventoryContainerDetails.getAllocatedQty(), ZERO_QTY);
  }

  @SneakyThrows
  @Test
  public void getInventoryContainerStatus_success() {
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final String containerStatus =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders).getContainerStatus();
    assertEquals(containerStatus, "AVAILABLE");
  }

  @Test
  public void testUpdateInventoryPoDetails() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    inventoryService.updateInventoryPoDetails(new InventoryItemPODetailUpdateRequest());
    verify(asyncPersister, times(1)).persistAsyncHttp(any(), any(), any(), any(), any());
  }

  @Test
  public void testUpdateLocation() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    InventoryLocationUpdateRequest build =
        InventoryLocationUpdateRequest.builder()
            .trackingIds(Arrays.asList("Test123"))
            .destinationLocation(
                DestinationLocation.builder().orgUnitId(0).locationName("F02").build())
            .build();
    inventoryService.updateLocation(build, MockHttpHeaders.getHeaders());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(asyncPersister, times(1))
        .persistAsyncHttp(
            eq(HttpMethod.PUT),
            eq(inventoryBaseUrl + ReceivingConstants.UPDATE_INVENTORY_LOCATION_URI),
            captor.capture(),
            any(),
            any());
    assertEquals(
        captor.getValue(),
        "{\"trackingIds\":[\"Test123\"],\"destinationLocation\":{\"locationName\":\"F02\",\"orgUnitId\":0}}");
  }

  @SneakyThrows
  @Test
  public void getInventoryQtyFromGLS_success() {
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_INVENTORY_FROM_GLS_ENABLED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("https://gls.url.com");

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final Integer inventoryQty =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders).getInventoryQty();
    assertEquals(inventoryQty.intValue(), 160);
  }

  @SneakyThrows
  @Test
  public void getAllocatedQty_success() {
    File resources = new ClassPathResource("inventory_get_containers_response.json").getFile();
    String successResponse = new String(Files.readAllBytes(resources.toPath()));

    final ResponseEntity<String> responseEntity =
        new ResponseEntity<>(successResponse, HttpStatus.OK);

    doReturn(responseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), same(String.class));

    final Integer allocatedQty =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders).getAllocatedQty();
    assertEquals(allocatedQty.intValue(), 0);
  }

  @SneakyThrows
  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid header key=WMT_UserId, value=.*")
  public void getBulkContainerDetails_failure_forInvalidUserIdInHeaders() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_bulk_containers_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resources.toPath()));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), same(String.class));

    HttpHeaders httpHeadersNotHavingUserId = MockHttpHeaders.getInventoryContainerDetailsHeaders();
    httpHeadersNotHavingUserId.set(ReceivingConstants.USER_ID_HEADER_KEY, null);
    inventoryService.getBulkContainerDetails(trackingIds, httpHeadersNotHavingUserId);
  }

  @SneakyThrows
  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Inventory not found.*")
  public void getBulkContainerDetails_4XX() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    HttpHeaders httpHeadersNotHavingUserId = MockHttpHeaders.getInventoryContainerDetailsHeaders();
    doThrow(
            new ReceivingDataNotFoundException(
                INVENTORY_NOT_FOUND, String.format(INVENTORY_NOT_FOUND_MESSAGE, trackingIds)))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    inventoryService.getBulkContainerDetails(trackingIds, httpHeadersNotHavingUserId);
  }

  @SneakyThrows
  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Error while calling Inventory.*")
  public void getBulkContainerDetails_5XX() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    HttpHeaders httpHeadersNotHavingUserId = MockHttpHeaders.getInventoryContainerDetailsHeaders();
    doThrow(new ReceivingInternalException(UNABLE_TO_PROCESS_INVENTORY, INVENTORY_SERVICE_DOWN))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    inventoryService.getBulkContainerDetails(trackingIds, httpHeadersNotHavingUserId);
  }

  @SneakyThrows
  @Test
  public void getBulkContainerDetails_Success() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_bulk_containers_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resources.toPath()));

    doReturn(new Gson().toJson(trackingIds)).when(gson).toJson(anyString());
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), same(String.class));
    HttpHeaders headers = MockHttpHeaders.getInventoryContainerDetailsHeaders();
    final String responseData = inventoryService.getBulkContainerDetails(trackingIds, headers);
    assertEquals(responseData, mockResponse);

    verify(retryableRestConnector, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.POST),
            httpEntityArgumentCaptor.capture(),
            same(String.class));
    HttpHeaders captorHeaders = httpEntityArgumentCaptor.getValue().getHeaders();
    assertTrue(captorHeaders.containsKey(ReceivingConstants.REQUEST_ORIGINATOR));
    assertSame(
        captorHeaders.getFirst(ReceivingConstants.REQUEST_ORIGINATOR),
        ReceivingConstants.APP_NAME_VALUE);
  }

  @SneakyThrows
  @Test(expectedExceptions = ReceivingInternalException.class)
  public void getBulkContainerDetails_Failure() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_bulk_containers_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resources.toPath()));

    doReturn(new Gson().toJson(trackingIds)).when(gson).toJson(anyString());
    doThrow(ResourceAccessException.class)
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), same(String.class));
    when(restClientResponseException.getResponseBodyAsString()).thenReturn("Message from test");
    HttpHeaders headers = MockHttpHeaders.getInventoryContainerDetailsHeaders();
    String responseData = inventoryService.getBulkContainerDetails(trackingIds, headers);
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void getBulkContainerDetails_Failure1() throws IOException {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    File resources = new ClassPathResource("inventory_get_bulk_containers_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resources.toPath()));

    doReturn(new Gson().toJson(trackingIds)).when(gson).toJson(anyString());
    doThrow(RestClientResponseException.class)
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), same(String.class));
    when(restClientResponseException.getResponseBodyAsString()).thenReturn("Message from test");
    HttpHeaders headers = MockHttpHeaders.getInventoryContainerDetailsHeaders();
    String responseData = inventoryService.getBulkContainerDetails(trackingIds, headers);
  }

  @Test
  public void testCreateContainersThroughOutboxSuccess() {
    List<ContainerDTO> containers = Arrays.asList(new ContainerDTO(), new ContainerDTO());
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);

    doNothing()
        .when(endgameOutboxHandler)
        .sendToOutbox(anyString(), anyString(), any(HttpHeaders.class));

    inventoryService.createContainersThroughOutbox(containers);

    verify(endgameOutboxHandler, times(1))
        .sendToOutbox(anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCreateContainersThroughOutboxException() {
    List<ContainerDTO> containers = Arrays.asList(new ContainerDTO(), new ContainerDTO());
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);

    doThrow(new RuntimeException("Test Exception"))
        .when(endgameOutboxHandler)
        .sendToOutbox(anyString(), anyString(), any(HttpHeaders.class));

    inventoryService.createContainersThroughOutbox(containers);
  }
}
