package com.walmart.move.nim.receiving.core.client.inventory;

import static com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient.INV_HOLD_REASON_CODE_DEFAULT;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.inventory.model.InventoryOffHoldRequest;
import com.walmart.move.nim.receiving.core.client.inventory.model.InventoryOnHoldRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryOssReceivingRequest;
import com.walmart.move.nim.receiving.core.model.inventory.TargetContainer;
import com.walmart.move.nim.receiving.core.model.inventory.Transfer;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.google.common.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class InventoryRestApiClientTest extends ReceivingTestBase {
  @Mock private AppConfig appConfig;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RestConnector simpleRestConnector;
  @InjectMocks private InventoryRestApiClient inventoryRestApiClient;

  private Gson gson = new Gson();
  private HttpHeaders httpHeaders;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String inventoryBaseUrl = "http://localhost:8080";

  private InventoryOssReceivingRequest inventoryOssReceivingRequest =
      new InventoryOssReceivingRequest();

  @BeforeMethod
  public void initMock() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(inventoryRestApiClient, "gson", gson);
    TargetContainer targetContainer = new TargetContainer();
    targetContainer.setCreateUserid("TestId");
    Transfer transfer = new Transfer();
    transfer.setTargetContainer(targetContainer);
    inventoryOssReceivingRequest.setTransfer(transfer);
  }

  @AfterMethod
  public void shutdown() {
    reset(retryableRestConnector, tenantSpecificConfigReader, simpleRestConnector, appConfig);
  }

  @Test
  public void testNotifyBackoutAdjustmentApiReturnsSuccessResponse() {
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("Success", HttpStatus.OK);
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockResponseEntity);

    CancelContainerResponse response =
        inventoryRestApiClient.notifyBackoutAdjustment(getInventoryExceptionRequest(), httpHeaders);

    assertNull(response);

    verify(tenantSpecificConfigReader, times(1)).getInventoryBaseUrlByFacility();
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testNotifyBackoutAdjustmentApiReturnsServiceUnAvailableErrorResponse() {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(new ResponseEntity<>("Service Unavailable", HttpStatus.SERVICE_UNAVAILABLE));

    CancelContainerResponse response =
        inventoryRestApiClient.notifyBackoutAdjustment(getInventoryExceptionRequest(), httpHeaders);

    assertNotNull(response);
    assertEquals(response.getErrorCode(), ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE);
    assertNotNull(response.getErrorMessage(), ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
  }

  @Test
  public void testNotifyBackoutAdjustmentApiReturnsInternalServerErrorResponse() {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(
            new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));

    CancelContainerResponse response =
        inventoryRestApiClient.notifyBackoutAdjustment(getInventoryExceptionRequest(), httpHeaders);

    assertNotNull(response);
    assertEquals(response.getErrorCode(), ReceivingException.INVENTORY_ERROR_CODE);
    assertNotNull(response.getErrorMessage(), ReceivingException.INVENTORY_ERROR_MSG);
  }

  @Test
  public void testNotifyReceivingCorrectionAdjustmentApiReturnsSuccessResponseOf200()
      throws ReceivingException {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(getMockSuccessResponse());

    ResponseEntity<String> receivingCorrectionResponse =
        inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
            getInventoryReceivingCorrectionRequest(1, 12), httpHeaders);

    assertNotNull(receivingCorrectionResponse);
    assertTrue(receivingCorrectionResponse.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void testNotifyReceivingCorrectionAdjustmentApiReturnsSuccessResponseOf201()
      throws ReceivingException {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(getMockCreatedResponse());

    ResponseEntity<String> receivingCorrectionResponse =
        inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
            getInventoryReceivingCorrectionRequest(1, 12), httpHeaders);

    assertNotNull(receivingCorrectionResponse);
    assertTrue(receivingCorrectionResponse.getStatusCode().is2xxSuccessful());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testNotifyReceivingCorrectionAdjustmentApiReturnsFailureResponse()
      throws ReceivingException {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(getMockFailureResponse());

    inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
        getInventoryReceivingCorrectionRequest(1, 12), httpHeaders);
  }

  private InventoryExceptionRequest getInventoryExceptionRequest() {
    InventoryExceptionRequest inventoryExceptionRequest = new InventoryExceptionRequest();
    inventoryExceptionRequest.setAdjustBy(0);
    inventoryExceptionRequest.setAdjustedQuantityUOM(ReceivingConstants.Uom.VNPK);
    inventoryExceptionRequest.setComment(ReceivingConstants.VTR_COMMENT);
    inventoryExceptionRequest.setReasonCode(String.valueOf(ReceivingConstants.VTR_REASON_CODE));
    return inventoryExceptionRequest;
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(67752L);
    container.setLocation("100");
    container.setTrackingId("lpn1");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setContainerStatus("Created");

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn1");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);

    container.setContainerItems(Arrays.asList(containerItem));
    return container;
  }

  private InventoryReceivingCorrectionRequest getInventoryReceivingCorrectionRequest(
      Integer newContainerQty, Integer currentContainerQtyInVnpk) {
    InventoryReceivingCorrectionRequest inventoryReceivingCorrectionRequest =
        new InventoryReceivingCorrectionRequest();
    ContainerItem containerItem = getMockContainer().getContainerItems().get(0);

    inventoryReceivingCorrectionRequest.setTrackingId("lpn1");
    inventoryReceivingCorrectionRequest.setItemNumber(containerItem.getItemNumber());
    inventoryReceivingCorrectionRequest.setItemUpc(containerItem.getItemUPC());
    inventoryReceivingCorrectionRequest.setCurrentQty(newContainerQty);
    inventoryReceivingCorrectionRequest.setAdjustBy(newContainerQty - currentContainerQtyInVnpk);
    inventoryReceivingCorrectionRequest.setCurrentQuantityUOM(ReceivingConstants.Uom.VNPK);
    inventoryReceivingCorrectionRequest.setAdjustedQuantityUOM(ReceivingConstants.Uom.EACHES);
    inventoryReceivingCorrectionRequest.setReasonCode(ReceivingConstants.DAMAGE_REASON_CODE);
    inventoryReceivingCorrectionRequest.setReasonDesc(ReceivingConstants.VTR_COMMENT);
    inventoryReceivingCorrectionRequest.setComment(ReceivingConstants.VTR_COMMENT);
    inventoryReceivingCorrectionRequest.setFinancialReportingGroup(
        containerItem.getFinancialReportingGroupCode());
    inventoryReceivingCorrectionRequest.setBaseDivisionCode(containerItem.getBaseDivisionCode());

    return inventoryReceivingCorrectionRequest;
  }

  private ResponseEntity<String> getMockSuccessResponse() {
    return new ResponseEntity<String>("Success", HttpStatus.OK);
  }

  private ResponseEntity<String> getMockCreatedResponse() {
    return new ResponseEntity<String>("Success", HttpStatus.CREATED);
  }

  private ResponseEntity<String> getMockFailureResponse() {
    return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testCreateInvAdjustmentRequestV2() {
    when(tenantSpecificConfigReader.getInventoryBaseUrlByFacility()).thenReturn(inventoryBaseUrl);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(getMockSuccessResponse());

    final Container c = getMockContainer();
    c.setTrackingId("B08852000020437868");
    final String trackingId = c.getTrackingId();
    final ContainerItem ci = c.getContainerItems().get(0);
    ci.setQuantity(640);
    ci.setItemNumber(9126006L);
    ci.setBaseDivisionCode(BASE_DIVISION_CODE);
    ci.setFinancialReportingGroupCode(FINANCIAL_REPORTING_GROUP_CODE);
    final String actualRequestV2 =
        inventoryRestApiClient.createInventoryAdjustRequest(
            trackingId,
            ci.getItemNumber().toString(),
            ci.getBaseDivisionCode(),
            ci.getFinancialReportingGroupCode(),
            10,
            ci.getQuantity());
    assertNotNull(actualRequestV2);
    final String expectedRequestV2 =
        "{\"adjustmentData\":{\"trackingId\":\"B08852000020437868\",\"itemDetails\":{\"itemIdentifierType\":\"ITEM_NUMBER\",\"itemIdentifierValue\":\"9126006\",\"baseDivisionCode\":\"WM\",\"financialReportingGroup\":\"US\"},\"currentQty\":640,\"adjustBy\":10,\"uom\":\"EA\",\"reasonCode\":52,\"client\":\"MOBILE\"}}";
    assertEquals(actualRequestV2, expectedRequestV2);
  }

  @Test
  public void testCreateInventoryAdjustRequest() {}

  @Test
  public void testCreateInventoryOnHoldRequest() {
    final String trackingId = "R08852000020071729";
    doReturn("1")
        .when(tenantSpecificConfigReader)
        .getCcmValue(getFacilityNum(), INV_HOLD_REASON_CODE_DEFAULT, "1");
    final String expectedRequestV2 =
        "[{\"trackingId\":\""
            + trackingId
            + "\",\"holdAllQty\":true,\"putCompleteHierarchyOnHold\":false,\"holdReasons\":[1],\"holdDirectedBy\":\"DC\",\"holdInitiatedTime\":\"2022-08-31T23:07:05.223Z\"}]";
    Type TypeInventoryOnHoldList = new TypeToken<ArrayList<InventoryOnHoldRequest>>() {}.getType();
    List<InventoryOnHoldRequest> inventoryOnHoldList_expected =
        gson.fromJson(expectedRequestV2, TypeInventoryOnHoldList);
    assertNotNull(inventoryOnHoldList_expected);
    assertTrue(inventoryOnHoldList_expected.size() > 0);
    final InventoryOnHoldRequest onHold_expected = inventoryOnHoldList_expected.get(0);

    // execute
    final String actualRequestV2 = inventoryRestApiClient.createInventoryOnHoldRequest(trackingId);

    // verify
    assertNotNull(actualRequestV2);
    JsonParser parser = new JsonParser();
    JsonElement json_actualRequestV2 = parser.parse(actualRequestV2);
    assertTrue(json_actualRequestV2.isJsonArray());

    List<InventoryOnHoldRequest> inventoryOnHoldList_actual =
        gson.fromJson(actualRequestV2, TypeInventoryOnHoldList);
    assertTrue(inventoryOnHoldList_actual.size() > 0);
    final InventoryOnHoldRequest onHold_actual = inventoryOnHoldList_actual.get(0);
    assertEquals(onHold_actual.getTrackingId(), onHold_expected.getTrackingId());
    assertEquals(onHold_actual.getHoldDirectedBy(), onHold_expected.getHoldDirectedBy());
    assertEquals(
        onHold_actual.isPutCompleteHierarchyOnHold(),
        onHold_expected.isPutCompleteHierarchyOnHold());
    assertEquals(onHold_actual.isHoldAllQty(), onHold_expected.isHoldAllQty());
  }

  @Test
  public void testCreateInventoryOffHoldRequest() {
    final String trackingId = "R08852000020071729";
    doReturn("1")
        .when(tenantSpecificConfigReader)
        .getCcmValue(getFacilityNum(), INV_HOLD_REASON_CODE_DEFAULT, "1");
    final String expectedRequestV2 =
        "[\n"
            + "    {\n"
            + "        \"trackingId\": \""
            + trackingId
            + "\",\n"
            + "        \"removeHierarchyOnUnHold\": false,\n"
            + "        \"holdReasons\":\n"
            + "        [\n"
            + "            1\n"
            + "        ],\n"
            + "        \"statusPostUnHold\": \"AVAILABLE\",\n"
            + "        \"itemStatePostHold\": \"AVAILABLE_TO_SELL\"\n"
            + "    }\n"
            + "]";
    Type typeInventoryOffHoldRequestList =
        new TypeToken<ArrayList<InventoryOffHoldRequest>>() {}.getType();
    List<InventoryOffHoldRequest> inventoryOffHoldList_expected =
        gson.fromJson(expectedRequestV2, typeInventoryOffHoldRequestList);
    assertNotNull(inventoryOffHoldList_expected);
    assertTrue(inventoryOffHoldList_expected.size() > 0);
    final InventoryOffHoldRequest offHold_expected = inventoryOffHoldList_expected.get(0);

    // execute
    final String actualRequestV2 = inventoryRestApiClient.createInventoryOffHoldRequest(trackingId);

    // verify
    assertNotNull(actualRequestV2);
    JsonParser parser = new JsonParser();
    JsonElement json_actualRequestV2 = parser.parse(actualRequestV2);
    assertTrue(json_actualRequestV2.isJsonArray());

    List<InventoryOffHoldRequest> inventoryOffHoldList_actual =
        gson.fromJson(actualRequestV2, typeInventoryOffHoldRequestList);
    assertTrue(inventoryOffHoldList_actual.size() > 0);
    final InventoryOffHoldRequest offHold_actual = inventoryOffHoldList_actual.get(0);
    assertEquals(offHold_actual.getTrackingId(), offHold_expected.getTrackingId());
    assertEquals(offHold_actual.getHoldReasons(), offHold_expected.getHoldReasons());
    assertEquals(offHold_actual.getStatusPostUnHold(), offHold_expected.getStatusPostUnHold());
    assertEquals(offHold_actual.getItemStatePostHold(), offHold_expected.getItemStatePostHold());
  }

  @Test
  public void testNotifyVtrToInventory() {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), INV_V2_ENABLED, false);
    doReturn("http://localhost").when(appConfig).getInventoryCoreBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("Success", HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    CancelContainerResponse cancelContainerResponse =
        inventoryRestApiClient.notifyVtrToInventory("lpn1", httpHeaders);

    assertNull(cancelContainerResponse);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(appConfig, times(1)).getInventoryCoreBaseUrl();
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetInventoryBohQtyByItem_positive1() throws IOException, ReceivingException {
    File resource = new ClassPathResource("item_boh_unified_success.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    String invBohQty =
        inventoryRestApiClient.getInventoryBohQtyByItem(
            12345L, BASE_DIVISION_CODE, FINANCIAL_REPORTING_GROUP_CODE, "1", httpHeaders);
    assertEquals(invBohQty, "2046");
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(appConfig, times(1)).getInventoryQueryBaseUrl();
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetInventoryBohQtyByItem_positive2() throws IOException, ReceivingException {
    File resource = new ClassPathResource("item_boh_unified_failure.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    inventoryRestApiClient.getInventoryBohQtyByItem(
        12345L, BASE_DIVISION_CODE, FINANCIAL_REPORTING_GROUP_CODE, "1", httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      dataProvider = "RestExceptionStatus",
      expectedExceptionsMessageRegExp =
          "Unable to verify BOH quantity from Inventory for item 12345, please contact your supervisor or QA.")
  public void testGetInventoryBohQtyByItem_negative1(HttpStatus httpStatus)
      throws IOException, ReceivingException {
    File resource = new ClassPathResource("item_boh_unified_failure.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, httpStatus);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    inventoryRestApiClient.getInventoryBohQtyByItem(
        12345L, BASE_DIVISION_CODE, FINANCIAL_REPORTING_GROUP_CODE, "1", httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Unable to verify BOH quantity from Inventory for item 12345, please contact your supervisor or QA.")
  public void testGetInventoryBohQtyByItem_negative2() throws IOException, ReceivingException {
    doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();
    doThrow(new RuntimeException())
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    inventoryRestApiClient.getInventoryBohQtyByItem(
        12345L, BASE_DIVISION_CODE, FINANCIAL_REPORTING_GROUP_CODE, "1", httpHeaders);
  }

  @DataProvider(name = "RestExceptionStatus")
  public static Object[][] restExceptionStatus() {
    return new Object[][] {
      {HttpStatus.SERVICE_UNAVAILABLE},
      {HttpStatus.BAD_REQUEST},
      {HttpStatus.INTERNAL_SERVER_ERROR},
      {HttpStatus.BAD_GATEWAY}
    };
  }

  @Test
  public void testPostInventoryOssReceiving_positive() throws IOException, ReceivingException {
    doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("Success", HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    inventoryRestApiClient.postInventoryOssReceiving(inventoryOssReceivingRequest, httpHeaders);
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(appConfig, times(1)).getInventoryCoreBaseUrl();
  }

  @Test(dataProvider = "RestExceptionStatus")
  public void testPostInventoryOssReceiving_negative(HttpStatus httpStatus) {
    File resource = null;
    try {
      resource = new ClassPathResource("item_boh_unified_failure.json").getFile();

      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();

      ResponseEntity<String> mockResponseEntity =
          new ResponseEntity<String>(mockResponse, httpStatus);

      doReturn(mockResponseEntity)
          .when(simpleRestConnector)
          .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
      inventoryRestApiClient.postInventoryOssReceiving(inventoryOssReceivingRequest, httpHeaders);
    } catch (Exception e) {
      if (httpStatus == HttpStatus.SERVICE_UNAVAILABLE) {
        assertEquals(e.getMessage(), "Inventory services are unavailable. Try again.");
      } else {
        assertEquals(e.getMessage(), "Unable to update Inventory");
      }
    }
  }

  @Test
  public void testNotifyOssVtrToInventory() throws IOException, ReceivingException {
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(FROM_SUBCENTER, "5");

    doReturn("http://localhost").when(appConfig).getInventoryCoreBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("Success", HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    CancelContainerResponse cancelContainerResponse =
        inventoryRestApiClient.notifyOssVtrToInventory("lpn5", containerItemMiscInfo, httpHeaders);

    assertNull(cancelContainerResponse);
    verify(appConfig, times(1)).getInventoryCoreBaseUrl();
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetHttpHeadersForOssTransfer() {
    HttpHeaders headers =
        inventoryRestApiClient.getHttpHeadersForOssTransfer(MockHttpHeaders.getHeaders(), "testId");
    assertEquals(headers.size(), 10);
  }

  @Test
  public void testPostInventoryOssReceiving_negative_HttpClientErrorException() {
    try {
      doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();
      HttpClientErrorException clientErrorException =
          new HttpClientErrorException(
              HttpStatus.CONFLICT,
              null,
              "[{\"errorCode\":\"GLS-INV-BE-000378\",\"description\":\"Insufficient Quantity, Requested: 1500, available: 0\"}]"
                  .getBytes(),
              StandardCharsets.UTF_8);

      when(simpleRestConnector.exchange(
              anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
          .thenThrow(clientErrorException);

      inventoryRestApiClient.postInventoryOssReceiving(inventoryOssReceivingRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "You entered a quantity that exceeds the amount of inventory available in Outside Storage. Please check the quantity entered to continue or contact your supervisor or QA.");
    }
  }

  @Test
  public void testPostInventoryOssReceiving_negative_HttpClientErrorException1() {
    try {
      doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();
      HttpClientErrorException clientErrorException =
          new HttpClientErrorException(
              HttpStatus.BAD_REQUEST,
              null,
              "[{\"errorCode\":\"GLS-INV-BE-000378\",\"description\":\"Can not create target Container 719-AAK already present in org unit 1\"}]"
                  .getBytes(),
              StandardCharsets.UTF_8);

      when(simpleRestConnector.exchange(
              anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
          .thenThrow(clientErrorException);

      inventoryRestApiClient.postInventoryOssReceiving(
          new InventoryOssReceivingRequest(), httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(ReceivingException.INVENTORY_ERROR_MSG, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testPostInventoryOssReceiving_negative_HttpClientErrorException2() {
    try {
      doReturn("http://localhost").when(appConfig).getInventoryQueryBaseUrl();
      HttpClientErrorException clientErrorException =
          new HttpClientErrorException(
              HttpStatus.CONFLICT,
              null,
              "[{\"errorCode\":\"GLS-INV-BE-000379\",\"description\":\"Can not create target Container 719-AAK already present in org unit 1\"}]"
                  .getBytes(),
              StandardCharsets.UTF_8);

      when(simpleRestConnector.exchange(
              anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
          .thenThrow(clientErrorException);

      inventoryRestApiClient.postInventoryOssReceiving(inventoryOssReceivingRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          "Can not create target Container 719-AAK already present in org unit 1",
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testUpdateLocation_Success() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    InventoryLocationUpdateRequest build =
        InventoryLocationUpdateRequest.builder()
            .trackingIds(Arrays.asList("Test123"))
            .destinationLocation(
                DestinationLocation.builder().orgUnitId(0).locationName("F02").build())
            .build();
    doReturn(new ResponseEntity<>("{}", HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    inventoryRestApiClient.updateLocation(build, MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testUpdateLocation_Failure() {
    when(appConfig.getInventoryBaseUrl()).thenReturn(inventoryBaseUrl);
    InventoryLocationUpdateRequest build =
        InventoryLocationUpdateRequest.builder()
            .trackingIds(Arrays.asList("Test123"))
            .destinationLocation(
                DestinationLocation.builder().orgUnitId(0).locationName("F02").build())
            .build();
    doReturn(new ResponseEntity<>("{}", HttpStatus.BAD_REQUEST))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    inventoryRestApiClient.updateLocation(build, MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testSendPutawayConfirmation() throws IOException {
    InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest =
        getInventoryPutawayConfirmationRequest();
    File resources = new ClassPathResource("inventory_get_bulk_containers_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resources.toPath()));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
    String s = inventoryRestApiClient.sendPutawayConfirmation(inventoryPutawayConfirmationRequest);
    assertNotNull(s);
    verify(retryableRestConnector, times(1))
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = INVENTORY_UNEXPECTED_DATA_ERROR)
  public void testSendPutawayConfirmationException() {
    InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest =
        getInventoryPutawayConfirmationRequest();
    doThrow(
            new RestClientResponseException(
                "call failed", HttpStatus.BAD_REQUEST.value(), "call failed", null, null, null))
        .when(retryableRestConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
    inventoryRestApiClient.sendPutawayConfirmation(inventoryPutawayConfirmationRequest);
    verify(retryableRestConnector, times(1))
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = INVENTORY_SERVICE_DOWN)
  public void testPutawayConfirmationResourceAccessException() {
    InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest =
        getInventoryPutawayConfirmationRequest();
    doThrow(new ResourceAccessException("Error"))
        .when(retryableRestConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
    inventoryRestApiClient.sendPutawayConfirmation(inventoryPutawayConfirmationRequest);
  }

  private InventoryPutawayConfirmationRequest getInventoryPutawayConfirmationRequest() {
    InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest =
        new InventoryPutawayConfirmationRequest();
    inventoryPutawayConfirmationRequest.setTrackingId("a76876969097");
    inventoryPutawayConfirmationRequest.setForceComplete(true);
    inventoryPutawayConfirmationRequest.setStatus(STATUS_COMPLETE);
    inventoryPutawayConfirmationRequest.setQuantity(2);
    inventoryPutawayConfirmationRequest.setItemNumber(87768769l);
    inventoryPutawayConfirmationRequest.setQuantityUOM("2");
    inventoryPutawayConfirmationRequest.setForceComplete(true);
    return inventoryPutawayConfirmationRequest;
  }
}
