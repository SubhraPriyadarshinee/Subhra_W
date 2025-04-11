package com.walmart.move.nim.receiving.rdc.controller;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcReconControllerTest {

  @InjectMocks private RdcReconController rdcReconController;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerTransformer containerTransformer;
  @Mock private ContainerService containerService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Mock private JMSSorterPublisher jmsSorterPublisher;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private AppConfig appConfig;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;

  private MockMvc mockMvc;
  private HttpHeaders httpHeaders;
  private Gson gson = new Gson();

  private static final String PUBLISH_CONTAINERS_TO_KAFKA = "/rdc/recon/container/publish/kafka";
  private static final String PUBLISH_CONTAINERS_TO_EI = "/rdc/recon/publish/ei";
  private static final String REPUBLISH_SORTER_DIVERT_MESSAGES_TO_ATHENA =
      "/rdc/recon/publish/sorter/divert";

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rdcReconController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
    httpHeaders = MockHttpHeaders.getHeaders();
  }

  @AfterMethod
  public void tearDown() {
    reset(
        containerTransformer,
        containerPersisterService,
        containerService,
        tenantSpecificConfigReader,
        kafkaAthenaPublisher,
        jmsSorterPublisher,
        rdcContainerUtils,
        appConfig,
        rdcManagedConfig,
        symboticPutawayPublishHelper);
  }

  @Test
  public void testRepublishPutawayMessage() throws ReceivingException {
    HttpHeaders headers = new HttpHeaders();
    headers.add("facilityCountryCode", "US");
    headers.add("facilityNum", "32987");
    headers.add("WMT-UserId", "sysadmin");
    List<String> trackingIds = Arrays.asList("trackingId1", "trackingId2");
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessageToKafka(trackingIds, headers);
    ResponseEntity<String> response =
        rdcReconController.republishPutawayMessage(trackingIds, headers);
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessageToKafka(trackingIds, headers);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  public void testPublishContainerToKafka() throws Exception {
    String trackingId = "a329870000000000000000001";
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add(trackingId);
    Container container = getContainerInfo();
    ContainerDTO containerDTO = new ContainerDTO();
    List<ContainerDTO> containerDTOList = Collections.singletonList(containerDTO);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerTransformer.transformList(Collections.singletonList(container)))
        .thenReturn(containerDTOList);
    when(rdcContainerUtils.convertDateFormatForProDate(any(), anyString())).thenReturn(container);
    doNothing()
        .when(containerService)
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_KAFKA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(containerTransformer, times(1)).transformList(Collections.singletonList(container));
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testPublishContainerToKafka_backoutContainers() throws Exception {
    String trackingId = "a329870000000000000000001";
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add(trackingId);
    Container container = getContainerInfo();
    container.setContainerStatus(ReceivingConstants.BACKOUT);
    ContainerDTO containerDTO = new ContainerDTO();
    List<ContainerDTO> containerDTOList = Collections.singletonList(containerDTO);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_KAFKA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isBadRequest());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(containerTransformer, times(0)).transformList(Collections.singletonList(container));
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testPostReceiptGivenTrackingId() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add("facilityCountryCode", "US");
    headers.add("facilityNum", "32987");
    headers.add("WMT-UserId", "sysadmin");
    String trackingId = "a329870000000000000000001";
    Container container = getContainerInfo();
    ContainerDTO containerDTO = new ContainerDTO();
    containerDTO.setTrackingId("a329870000000000000000001");
    String expectedJson =
        "[{\"trackingId\":\"a329870000000000000000001\",\"messageId\":\"b5001ce0-cfed-48b1-a908-6e5afe4e1983\",\"location\":\"999\",\"deliveryNumber\":26849922,\"facility\":{\"countryCode\":\"us\",\"buNumber\":\"6020\"},\"destination\":{\"countryCode\":\"us\",\"buNumber\":\"5250\",\"slot\":\"999\"},\"containerType\":\"Vendor Pack\",\"labelType\":\"XDK2\",\"containerStatus\":\"COMPLETE\",\"ctrShippable\":true,\"ctrReusable\":false,\"inventoryStatus\":\"ALLOCATED\",\"completeTs\":\"May 13, 2024 2:54:46 AM\",\"publishTs\":\"May 13, 2024 2:54:46 AM\",\"createTs\":\"May 13, 2024 2:54:46 AM\",\"createUser\":\"rcvuser\",\"lastChangedTs\":\"May 13, 2024 2:54:46 AM\",\"lastChangedUser\":\"rcvuser\",\"isAudited\":false,\"hasChildContainers\":false,\"childContainers\":[],\"onConveyor\":false,\"containerMiscInfo\":{\"channelMethod\":\"CROSSU\",\"labelType\":\"XDK2\",\"fulfillmentMethod\":\"RECEIVING\",\"originFacilityNum\":3930.0,\"destType\":\"STORE\"},\"shipmentId\":\"3415949239306020\",\"contents\":[{\"id\":535699184,\"trackingId\":\"f039300000200000000506909\",\"purchaseReferenceNumber\":\"6281660032\",\"purchaseReferenceLineNumber\":7,\"inboundChannelMethod\":\"CROSSU\",\"totalPurchaseReferenceQty\":3,\"purchaseCompanyId\":1,\"itemNumber\":599186799,\"quantity\":3,\"quantityUOM\":\"EA\",\"vnpkQty\":3,\"whpkQty\":3,\"baseDivisionCode\":\"WM\",\"financialReportingGroupCode\":\"US\",\"rotateDate\":\"May 13, 2024 2:54:46 AM\",\"distributions\":[{\"allocQty\":3,\"item\":{\"itemNbr\":\"599186799\",\"itemUpc\":\"00070896143136\",\"vnpk\":\"3\",\"whpk\":\"3\",\"itemdept\":\"67\",\"baseDivisionCode\":\"WM\",\"financialReportingGroup\":\"US\",\"reportingGroup\":\"US\",\"dcZone\":\"3\",\"pickBatch\":\"282\",\"printBatch\":\"282\",\"storeAlignment\":\"MANUAL\",\"shipLaneNumber\":\"21\",\"divisionNumber\":\"1\",\"packType\":\"CP\",\"itemHandlingCode\":\"C\"},\"orderId\":\"bbb8b048-5ec5-4f57-87fd-18d06e5e0745\",\"destNbr\":5250,\"qtyUom\":\"EA\"}],\"packagedAsUom\":\"EA\",\"itemUPC\":\"00070896143136\",\"poTypeCode\":33,\"poDCNumber\":\"3930\",\"containerItemMiscInfo\":{\"secondaryQtyUom\":\"LB/ZA\",\"packTypeCode\":\"CP\",\"isAtlasConvertedItem\":\"true\"},\"derivedQuantity\":3.0,\"derivedQuantityUOM\":\"EA\",\"isAudited\":false,\"facilityCountryCode\":\"us\",\"facilityNum\":6020}],\"documentType\":\"ASN\",\"fulfillmentMethod\":\"RECEIVING\",\"asnNumber\":\"3415949239306020\"}]";
    List<ContainerDTO> containerDTOList = new ArrayList<>();
    containerDTOList =
        gson.fromJson(expectedJson, new TypeToken<List<ContainerDTO>>() {}.getType());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerTransformer.transformList(Collections.singletonList(container)))
        .thenReturn(containerDTOList);
    rdcReconController.postReceiptGivenTrackingId(trackingId, headers);
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(containerTransformer, times(1)).transformList(Collections.singletonList(container));
  }

  @Test
  private void testPublishContainerToKafka_ReturnsBadRequest_MissingTrackingIds() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_KAFKA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
                .headers(httpHeaders))
        .andExpect(status().isBadRequest());
  }

  @Test
  private void testRepublishSorterDivertMessagesToAthena_ReturnsBadRequest_MissingTrackingIds()
      throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(REPUBLISH_SORTER_DIVERT_MESSAGES_TO_ATHENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
                .headers(httpHeaders))
        .andExpect(status().isBadRequest());
  }

  @Test
  private void testRepublishSorterDivertMessagesToAthena_ReturnsSuccessForJmsSorterPublisher()
      throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    container.setContainerItems(Arrays.asList(containerItem));
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(String.valueOf(SymAsrsSorterMapping.SYM2));
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(
            Collections.singletonList(container.getContainerItems().get(0).getAsrsAlignment()));
    when(rdcManagedConfig.getSymEligibleLabelType())
        .thenReturn(container.getContainerItems().get(0).getAsrsAlignment());
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(REPUBLISH_SORTER_DIVERT_MESSAGES_TO_ATHENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(jmsSorterPublisher, times(1)).publishLabelToSorter(any(Container.class), any());
  }

  @Test
  private void testRepublishSorterDivertMessagesToAthena_ReturnsSuccessForKafkaAthenaPublisher()
      throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    container.setContainerItems(Arrays.asList(containerItem));

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Collections.emptyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(Container.class), anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(REPUBLISH_SORTER_DIVERT_MESSAGES_TO_ATHENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(kafkaAthenaPublisher, times(1)).publishLabelToSorter(any(Container.class), any());
  }

  @Test
  private void testRepublishSorterDivertMessagesToAthena_ReturnsSuccessHavingChildContainers()
      throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    Container childContainer = new Container();

    ContainerItem containerItem = new ContainerItem();
    ContainerItem childContainerItem = new ContainerItem();
    childContainer.setContainerItems(Arrays.asList(childContainerItem));
    container.setContainerItems(Arrays.asList(containerItem));
    container.setChildContainers(new HashSet<>(Arrays.asList(childContainer)));
    container.setHasChildContainers(true);

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Collections.emptyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(Container.class), anyString());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(REPUBLISH_SORTER_DIVERT_MESSAGES_TO_ATHENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(kafkaAthenaPublisher, times(1)).publishLabelToSorter(any(Container.class), any());
  }

  @Test
  private void testPublishContainersToEI_ReturnsBadRequest_MissingTrackingIds() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
                .headers(httpHeaders))
        .andExpect(status().isBadRequest());
  }

  @Test
  private void testPublishContainersToEI_ReturnsSuccess() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    container.setContainerStatus("COMPLETE");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  private void testPublishContainersToEI_ReturnsSuccess_BackOutContaines() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    container.setContainerStatus("backout");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  private void testPublishContainersToEI_ByEventType_ReceivingEvent() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    container.setContainerStatus("backout");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .param("eventType", "DR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  private void testPublishContainersToEI_ByEventType_PickEvent() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    container.setContainerStatus("backout");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .param("eventType", "DP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  private void testPublishContainersToEI_ByEventType_VoidEvent() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    Container container = new Container();
    container.setContainerStatus("backout");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .param("eventType", "DV")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  private void testPublishContainersToEI_ReturnsSuccess_MultipleContainers() throws Exception {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("e3223232323");
    trackingIds.add("f6223232323");
    Container container = new Container();
    container.setContainerStatus("COMPLETE");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_CONTAINERS_TO_EI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(trackingIds))
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(containerPersisterService, times(2)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(2)).publishContainerToEI(any(Container.class), any());
  }

  public static Container getContainerInfo() {
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(1L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItems.add(containerItem);
    container.setDeliveryNumber(1234L);
    container.setTrackingId("a329870000000000000000001");
    container.setContainerStatus("");
    container.setContainerItems(containerItems);

    return container;
  }
}
