package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DefaultUpdateContainerQuantityRequestHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdcKafkaInventoryEventProcessorTest extends ReceivingTestBase {
  @InjectMocks private GdcKafkaInventoryEventProcessor gdcKafkaInventoryEventProcessor;
  @Spy private InventoryAdjustmentTO messageData;
  @Spy private ContainerService containerService;
  @Mock private GDCFlagReader gdcFlagReader;
  @Mock private DefaultUpdateContainerQuantityRequestHandler updateContainerQuantityRequestHandler;
  @Spy private WitronContainerService witronContainerService;
  @Spy private WitronSplitPalletService witronSplitPalletService;
  private final JsonParser parser = new JsonParser();
  private HttpHeaders httpHeaders;
  @Spy private ItemConfigApiClient itemConfigApiClient;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(EVENT_TYPE, INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    httpHeaders.set(FLOW_NAME, ADJUSTMENT_FLOW);
  }

  @AfterMethod
  public void tearDown() {
    reset(containerService);
    reset(witronContainerService);
    reset(witronSplitPalletService);
    reset(itemConfigApiClient);
    httpHeaders.set(EVENT_TYPE, INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    httpHeaders.set(FLOW_NAME, ADJUSTMENT_FLOW);
  }

  @Test
  public void processEvent_DamageTest() throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdc_inventory_adjust_damage_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);

    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void processEvent_VendorDamageTest() throws ReceivingException, IOException {

    File resource =
        new ClassPathResource("gdc_inventory_adjust_damage_vendor_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);

    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void processEvent_ConcealedTest() throws ReceivingException, IOException {

    File resource =
        new ClassPathResource("gdc_inventory_adjust_concealed_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);

    doNothing()
        .when(containerService)
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void processEvent_ReceivingTest() throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdc_inventory_receiving_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    when(updateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            anyString(), any(ContainerUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(new ContainerUpdateResponse());

    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(updateContainerQuantityRequestHandler, times(0))
        .updateQuantityByTrackingId(
            anyString(), any(ContainerUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void processEvent_VtrTest() throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdc_inventory_vtr_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    doNothing()
        .when(witronContainerService)
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(witronContainerService, times(1))
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());
  }

  @Test
  public void processEvent_SplitPalletTest() throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdc_inventory_split_pallet_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    httpHeaders.remove(FLOW_NAME);
    httpHeaders.set(FLOW_NAME, SPLIT_PALLET_TRANSFER);
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    doNothing()
        .when(witronSplitPalletService)
        .splitPallet(
            anyString(), anyInt(), anyString(), anyString(), anyInt(), any(HttpHeaders.class));

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(witronSplitPalletService, times(1))
        .splitPallet(
            anyString(), anyInt(), anyString(), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void invalidHeadersTest() throws ReceivingException, IOException {
    HttpHeaders invalidHttpHeaders = MockHttpHeaders.getHeaders();
    invalidHttpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    invalidHttpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    invalidHttpHeaders.set(
        ReceivingConstants.EVENT_TYPE, INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    invalidHttpHeaders.set(ReceivingConstants.FLOW_NAME, "InvalidforReceive");
    when(messageData.getHttpHeaders()).thenReturn(invalidHttpHeaders);
    File resource = new ClassPathResource("gdc_inventory_split_pallet_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
    verify(witronSplitPalletService, times(0))
        .splitPallet(
            anyString(), anyInt(), anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());
  }

  @Test
  public void invalidMessageTest() throws ReceivingException, IOException {

    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    File resource = new ClassPathResource("gdc_inventory_split_pallet_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    JsonObject invalidMessageBody = new JsonObject();
    invalidMessageBody.addProperty("test", "message");
    when(messageData.getJsonObject()).thenReturn(invalidMessageBody);
    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
    verify(witronSplitPalletService, times(0))
        .splitPallet(
            anyString(), anyInt(), anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());
  }

  @Test
  public void processEvent_isOneAtlasNotConvertedItem_Test()
      throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdc_inventory_vtr_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(true)
        .when(itemConfigApiClient)
        .isOneAtlasNotConvertedItem(anyBoolean(), any(), anyLong(), any(HttpHeaders.class));

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(witronContainerService, times(0))
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());
  }

  @Test
  public void processEvent_isOneAtlasNotConvertedItem_Negative_Test()
      throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdc_inventory_vtr_mock_event.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(messageData.getJsonObject()).thenReturn(parser.parse(mockResponse).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(httpHeaders);
    doNothing()
        .when(witronContainerService)
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(false)
        .when(itemConfigApiClient)
        .isOneAtlasNotConvertedItem(anyBoolean(), any(), anyLong(), any(HttpHeaders.class));

    gdcKafkaInventoryEventProcessor.processEvent(messageData);

    verify(witronContainerService, times(1))
        .processVTR(anyString(), any(HttpHeaders.class), any(Integer.class), anyString(), anyInt());
  }
}
