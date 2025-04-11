package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DefaultUpdateContainerQuantityRequestHandler;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdcInventoryEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private GdcInventoryEventProcessor gdcInventoryEventProcessor;

  @Spy private InventoryAdjustmentTO messageData;

  @Spy private ContainerService containerService;

  @Mock private WitronSplitPalletService witronSplitPalletService;
  @Mock private DefaultUpdateContainerQuantityRequestHandler updateContainerQuantityRequestHandler;
  @Mock private WitronContainerService witronContainerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private GDCFlagReader gdcFlagReader;
  @Spy private ItemConfigApiClient itemConfigApiClient;
  @Captor ArgumentCaptor<String> updatedContainerTrackingId;
  @Captor ArgumentCaptor<Integer> adjustQty;
  @Captor ArgumentCaptor<String> newContainerTrackingId;
  @Captor ArgumentCaptor<String> trackingId;
  @Captor ArgumentCaptor<ContainerUpdateRequest> containerUpdateRequest;
  @Captor ArgumentCaptor<Integer> availableToSellQty;
  @Mock private AppConfig appConfig;

  private final JsonParser parser = new JsonParser();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void cleanup() {
    reset(messageData);
    reset(updateContainerQuantityRequestHandler);
    reset(witronSplitPalletService);
    reset(containerService);
    reset(gdcFlagReader);
    reset(itemConfigApiClient);
    reset(witronContainerService);
    reset(appConfig);
  }

  @Test
  public void testInventoryAdjustmentWithDamageEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.VALID_DAMAGE_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithConcealedDamageEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_CONCEALED_DAMAGE_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));

    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithVdgEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VDM_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithConcealedShortageOrOverageEvent()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_CONCEALED_SHORTAGE_OVERAGE_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithValidEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(witronContainerService)
        .processVTR(anyString(), any(HttpHeaders.class), anyInt(), anyString(), anyInt());

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(witronContainerService, times(1))
        .processVTR(anyString(), any(HttpHeaders.class), anyInt(), anyString(), anyInt());
  }

  @Test
  public void testInventoryAdjustmentWithInvalidEvent() throws ReceivingException {

    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(HttpHeaders.class), anyInt(), anyString(), anyInt());
  }

  @Test
  public void testInventorySplitFailMessagePalletMessage_update_message() throws Exception {
    File resource = new ClassPathResource("witron_inventory_container_update_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(witronSplitPalletService)
        .splitPallet(
            updatedContainerTrackingId.capture(),
            availableToSellQty.capture(),
            newContainerTrackingId.capture(),
            any(),
            adjustQty.capture(),
            any(HttpHeaders.class));

    assertSame(-50, adjustQty.getValue());
    assertEquals("sourceContainer", updatedContainerTrackingId.getValue());
    assertEquals("newlyCreatedDestContainer", newContainerTrackingId.getValue());
  }

  @Test
  public void testInventorySplitFailMessagePalletMessage_create_message() throws Exception {

    File resource = new ClassPathResource("witron_inventory_container_create_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(witronSplitPalletService, times(0))
        .splitPallet(
            anyString(), anyInt(), anyString(), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void test_witron_initiated_VTR_coming_as_receive_correction_v1() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);

    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(1))
        .updateQuantityByTrackingId(any(), any(), any());

    verify(updateContainerQuantityRequestHandler)
        .updateQuantityByTrackingId(trackingId.capture(), containerUpdateRequest.capture(), any());

    assertEquals("B32612000022764292", trackingId.getValue());

    // should not have valid values for Witron initiated flow via Inventory
    assertEquals(containerUpdateRequest.getValue().getAdjustQuantity().intValue(), 0);
    assertEquals(null, containerUpdateRequest.getValue().getPrinterId());
  }

  @Test
  public void test_witronInitiated_ReceivingCorrection_as_VRT_v2() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);

    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(1))
        .updateQuantityByTrackingId(any(), any(), any());

    verify(updateContainerQuantityRequestHandler)
        .updateQuantityByTrackingId(trackingId.capture(), containerUpdateRequest.capture(), any());

    assertEquals("B32612000022764292", trackingId.getValue());

    final ContainerUpdateRequest receivingRequest = containerUpdateRequest.getValue();

    // should have valid values for Witron initiated flow via Inventory
    final Integer adjustQuantity = receivingRequest.getAdjustQuantity();
    final boolean inventoryReceivingCorrection = receivingRequest.isInventoryReceivingCorrection();
    assertTrue(inventoryReceivingCorrection);
    assertNotNull(adjustQuantity);
    assertEquals(adjustQuantity.intValue(), 0);
    final Integer inventoryQuantity = receivingRequest.getInventoryQuantity();
    assertNotNull(inventoryQuantity);
    assertEquals(inventoryQuantity.intValue(), 10);

    assertEquals(null, receivingRequest.getPrinterId());
  }

  @Test
  public void test_witron_initiated_ReceivingCorrection_v2() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);

    File resource =
        new ClassPathResource("witron_initiated_receiving_correction_v2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(1))
        .updateQuantityByTrackingId(any(), any(), any());

    verify(updateContainerQuantityRequestHandler)
        .updateQuantityByTrackingId(trackingId.capture(), containerUpdateRequest.capture(), any());

    assertEquals(trackingId.getValue(), "D32612000021401986");

    final ContainerUpdateRequest receivingRequest = containerUpdateRequest.getValue();

    // should have valid values for Witron initiated flow via Inventory
    final Integer adjustQuantity = receivingRequest.getAdjustQuantity();
    final boolean inventoryReceivingCorrection = receivingRequest.isInventoryReceivingCorrection();
    assertTrue(inventoryReceivingCorrection);
    assertNotNull(adjustQuantity);
    assertEquals(adjustQuantity.intValue(), 45);
    final Integer inventoryQuantity = receivingRequest.getInventoryQuantity();
    assertNotNull(inventoryQuantity);
    assertEquals(inventoryQuantity.intValue(), 40);

    assertEquals(null, receivingRequest.getPrinterId());
  }

  @Test
  public void test_PalletCorrection_ForAdjustmentReasonCode52_fromReceivingClientUI()
      throws Exception {
    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    // receiving client UI
    HttpHeaders receivingClientUiHeaders = MockHttpHeaders.getHeaders();
    receivingClientUiHeaders.set(REQUEST_ORIGINATOR, RECEIVING);
    when(messageData.getHttpHeaders()).thenReturn(receivingClientUiHeaders);

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(any(), any(), any());
  }

  @Test
  public void test_PalletCorrection_ForAdjustmentReasonCode52_fromReceivingServerAPI()
      throws Exception {
    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders receivingServerApiHeaders = MockHttpHeaders.getHeaders();
    receivingServerApiHeaders.set(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    when(messageData.getHttpHeaders()).thenReturn(receivingServerApiHeaders);

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(any(), any(), any());
  }

  @Test
  public void testInventorySplitPallet() throws Exception {
    File resource = new ClassPathResource("witron_inventory_split_pallet_event.json").getFile();
    String splitPalletEvent = new String(Files.readAllBytes(resource.toPath()));
    when(messageData.getJsonObject()).thenReturn(parser.parse(splitPalletEvent).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(GdcHttpHeaders.getHeaders());
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(witronSplitPalletService)
        .splitPallet(
            updatedContainerTrackingId.capture(),
            availableToSellQty.capture(),
            newContainerTrackingId.capture(),
            anyString(),
            adjustQty.capture(),
            any(HttpHeaders.class));

    assertSame(24, availableToSellQty.getValue());
    assertSame(-16, adjustQty.getValue());
    assertEquals("B32612000020826581", updatedContainerTrackingId.getValue());
    assertEquals("B32612000020829640", newContainerTrackingId.getValue());
  }

  @Test
  public void test_inventoryDisabled_doNotProcess_VtrOrRcvCorrectionOrSplitPallet()
      throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);
    File resource =
        new ClassPathResource("witron_initiated_receiving_correction_v2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);
    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);

    //    when(gdcFlagReader.isInventoryApiDisabled()).thenReturn(true);
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate Clear Tenant
    assertNull(TenantContext.getFacilityCountryCode());
    assertNull(TenantContext.getFacilityNum());

    // Ensure no call for (VTR or pallet correction, splitPallet)
    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(), anyInt(), anyString(), anyInt());
    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(anyString(), any(), any());
    verify(witronSplitPalletService, times(0))
        .splitPallet(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void test_BAU_do_Process_case1() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);

    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(false).when(gdcFlagReader).isDCOneAtlasEnabled();

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(1))
        .updateQuantityByTrackingId(any(), any(), any());

    verify(updateContainerQuantityRequestHandler)
        .updateQuantityByTrackingId(trackingId.capture(), containerUpdateRequest.capture(), any());

    assertEquals("B32612000022764292", trackingId.getValue());

    // should not have valid values for Witron initiated flow via Inventory
    assertEquals(containerUpdateRequest.getValue().getAdjustQuantity().intValue(), 0);
    assertEquals(null, containerUpdateRequest.getValue().getPrinterId());
  }

  @Test
  public void test_doNotProcess_ManualGdcTrue_NoOneAtlas_fullGls_case2() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);
    File resource =
        new ClassPathResource("witron_initiated_receiving_correction_v2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);
    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);

    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(false).when(gdcFlagReader).isDCOneAtlasEnabled();

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate Clear Tenant
    assertNull(TenantContext.getFacilityCountryCode());
    assertNull(TenantContext.getFacilityNum());

    // Ensure no call for (VTR or pallet correction, splitPallet)
    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(), anyInt(), anyString(), anyInt());
    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(anyString(), any(), any());
    verify(witronSplitPalletService, times(0))
        .splitPallet(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void test_doNotProcess_ManualGdcTrue_OneAtlasTrueItemNotConverted_case3()
      throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);
    File resource =
        new ClassPathResource("witron_initiated_receiving_correction_v2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);
    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);

    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(false)
        .when(itemConfigApiClient)
        .isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate Clear Tenant
    assertNull(TenantContext.getFacilityCountryCode());
    assertNull(TenantContext.getFacilityNum());

    // Ensure no call for (VTR or pallet correction, splitPallet)
    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(), anyInt(), anyString(), anyInt());
    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(anyString(), any(), any());
    verify(witronSplitPalletService, times(0))
        .splitPallet(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void test_do_Process_OneAtlasAndItemConverted_case4() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(8852);

    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    HttpHeaders witronHeaders = MockHttpHeaders.getHeaders();
    witronHeaders.set(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    when(messageData.getHttpHeaders()).thenReturn(witronHeaders);
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(true)
        .when(itemConfigApiClient)
        .isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));

    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    // validate
    verify(updateContainerQuantityRequestHandler, times(1))
        .updateQuantityByTrackingId(any(), any(), any());

    verify(updateContainerQuantityRequestHandler)
        .updateQuantityByTrackingId(trackingId.capture(), containerUpdateRequest.capture(), any());

    assertEquals("B32612000022764292", trackingId.getValue());

    // should not have valid values for Witron initiated flow via Inventory
    assertEquals(containerUpdateRequest.getValue().getAdjustQuantity().intValue(), 0);
    assertEquals(null, containerUpdateRequest.getValue().getPrinterId());
  }

  @Test
  public void test_blockMQ_Kafka_enabled_by_tenant_level() throws ReceivingException, IOException {
    TenantContext.setFacilityNum(Integer.valueOf("6085"));
    when(appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(6085));
    File resource =
        new ClassPathResource("witron_initiated_vtr_as_receiving_correction.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    // execute
    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(any(), any(), any());
  }

  @Test
  public void testInvalidInventoryAdjustment() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.INVALID_INVENTORY_EVENT_ITEMLIST_EMPTY)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(containerService, times(0))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testIgnoreInventoryAdjustmentMessage() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isIgnoreAdjFromInventory();
    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(containerService, times(0))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithConcealedOverageEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_CONCEALED_RCO_OVERAGE_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInvalidInventoryAdjustmentNotPresent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.INVALID_INVENTORY_EVENT_ITEMLIST_NOT_PRESENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(containerService, times(0))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInvalidTrackingIdNotPresent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.INVALID_INVENTORY_EVENT_TRACKINGID_EMPTY)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(containerService, times(0))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testIgnoreReceiveRequestOriginatorMessage() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_CONCEALED_DAMAGE_EVENT)
                .getAsJsonObject());
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, RECEIVING);
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isIgnoreAdjFromInventory();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(containerService, times(0))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInvalidAdjustmentToMessage() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.INVALID_ADJUSTMENT_TO_EMPTY)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcInventoryEventProcessor.processEvent(messageData);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(containerService, times(0))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }
}
