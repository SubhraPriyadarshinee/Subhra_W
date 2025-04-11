package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_REQUEST_KEY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.iqs.AsyncIqsRestApiClient;
import com.walmart.move.nim.receiving.core.client.iqs.model.ItemBulkResponseDto;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionIdAndTrackingIdPair;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcContainerServiceTest {

  @InjectMocks private RdcContainerService rdcContainerService;
  @Mock private SlottingServiceImpl slottingServiceImpl;
  @Mock private ContainerItemService containerItemService;
  @Spy private ContainerRepository containerRepository;
  @Mock private InstructionRepository instructionRepository;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private AsyncIqsRestApiClient asyncIqsRestApiClient;
  @Mock private AsyncLocationService asyncLocationService;
  @Mock private AppConfig appConfig;
  @Mock private InventoryService inventoryService;
  private Gson gson = new Gson();
  private HttpHeaders httpHeaders;
  String upcNumber = "000894343434";

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @BeforeMethod
  public void setup() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    httpHeaders = MockHttpHeaders.getHeaders();
  }

  public static Instruction getInstructionResponse() throws IOException {
    String resourcePath =
        new File("../../receiving-core/src/test/resources/GetInstructionResponse.json")
            .getCanonicalPath();
    String mockInstructionResponse = new String(Files.readAllBytes(Paths.get(resourcePath)));
    Instruction instruction = new Gson().fromJson(mockInstructionResponse, Instruction.class);
    return instruction;
  }

  public static Instruction getInstructionResponse_QtyReceiving() throws IOException {
    String resourcePath =
        new File(
                "../../receiving-core/src/test/resources/InstructionResponseForQtyReceivingLabels.json")
            .getCanonicalPath();
    String mockInstructionResponse = new String(Files.readAllBytes(Paths.get(resourcePath)));
    Instruction instruction = new Gson().fromJson(mockInstructionResponse, Instruction.class);
    return instruction;
  }

  private List<String> getTrackingIds() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("9784587526");
    trackingIds.add("9434343");
    return trackingIds;
  }

  @AfterMethod
  public void tearDown() {
    reset(
        slottingServiceImpl,
        containerItemService,
        containerRepository,
        containerPersisterService,
        instructionRepository,
        asyncIqsRestApiClient,
        asyncLocationService,
        appConfig,
        inventoryService);
  }

  @Test
  public void testGetContainerItemsByUPC_NoItemsReturnedForUPC() {
    when(containerItemService.getContainerItemMetaDataByUpcNumber(anyString()))
        .thenReturn(Collections.emptyList());
    List<ContainerItem> containerItemList =
        rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);

    assertEquals(containerItemList.size(), 0);
    assertTrue(containerItemList.isEmpty());
    verify(containerItemService, times(1)).getContainerItemMetaDataByUpcNumber(anyString());
  }

  @Test
  public void testGetContainerItemsByUPC_SlottingReturnsSuccessResponseForAllItems()
      throws IOException {
    File resource =
        new ClassPathResource("MockSlottingSuccessResponseForMultiItems.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    SlottingPalletResponse slottingPalletResponse =
        gson.fromJson(mockResponse, SlottingPalletResponse.class);

    when(containerItemService.getContainerItemMetaDataByUpcNumber(anyString()))
        .thenReturn(getMockContainerItems());
    when(slottingServiceImpl.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(slottingPalletResponse);
    List<ContainerItem> containerItemList =
        rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);

    assertNotNull(containerItemList);
    assertFalse(containerItemList.isEmpty());
    assertEquals(containerItemList.size(), 2);
    assertEquals((long) containerItemList.get(0).getItemNumber(), 553857311L);
    assertEquals(containerItemList.get(0).getAsrsAlignment(), "SYM2_5");
    assertEquals(containerItemList.get(0).getSlotType(), "PRIME");
    assertNotNull(
        containerItemList.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));
    assertEquals(
        containerItemList.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID),
        "SYMBP");

    assertEquals((long) containerItemList.get(1).getItemNumber(), 553857312L);
    assertEquals(containerItemList.get(1).getSlotType(), "PRIME");
    assertNotNull(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));
    assertEquals(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID),
        "SYMBP");

    verify(containerItemService, times(1)).getContainerItemMetaDataByUpcNumber(anyString());
    verify(slottingServiceImpl, times(1)).getPrimeSlot(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void
      testGetContainerItemsByUPC_SlottingReturnsSuccessResponseForAllItems_ASRSAlignmentReturnedEmptyFromSlotting()
          throws IOException {
    File resource =
        new ClassPathResource("MockSlottingSuccessResponseForMultiItemsWithAsrsAlignmentEmpty.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    SlottingPalletResponse slottingPalletResponse =
        gson.fromJson(mockResponse, SlottingPalletResponse.class);

    when(containerItemService.getContainerItemMetaDataByUpcNumber(anyString()))
        .thenReturn(getMockContainerItems());
    when(slottingServiceImpl.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(slottingPalletResponse);
    List<ContainerItem> containerItemList =
        rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);

    assertNotNull(containerItemList);
    assertFalse(containerItemList.isEmpty());
    assertEquals(containerItemList.size(), 2);
    assertEquals((long) containerItemList.get(0).getItemNumber(), 553857311L);
    assertNull(containerItemList.get(0).getAsrsAlignment());
    assertEquals(containerItemList.get(0).getSlotType(), "RESERVE");
    assertNotNull(
        containerItemList.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));
    assertEquals(
        containerItemList.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID),
        "D0863");

    assertEquals((long) containerItemList.get(1).getItemNumber(), 553857312L);
    assertEquals(containerItemList.get(1).getSlotType(), "RESERVE");
    assertNotNull(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));
    assertEquals(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID),
        "D0869");

    verify(containerItemService, times(1)).getContainerItemMetaDataByUpcNumber(anyString());
    verify(slottingServiceImpl, times(1)).getPrimeSlot(anyList(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetContainerItemsByUPC_SlottingThrowsException() throws IOException {
    when(containerItemService.getContainerItemMetaDataByUpcNumber(anyString()))
        .thenReturn(getMockContainerItems());
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingServiceImpl)
        .getPrimeSlot(anyList(), any(HttpHeaders.class));

    rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);
    verify(containerItemService, times(1)).getContainerItemMetaDataByUpcNumber(anyString());
    verify(slottingServiceImpl, times(1)).getPrimeSlot(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testGetContainerItemsByUPC_SlottingReturnsSuccessResponseForPartialItems()
      throws IOException {
    File resource =
        new ClassPathResource("MockSlottingSuccessResponseForPartialItems.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    SlottingPalletResponse slottingPalletResponse =
        gson.fromJson(mockResponse, SlottingPalletResponse.class);

    when(containerItemService.getContainerItemMetaDataByUpcNumber(anyString()))
        .thenReturn(getMockContainerItems());
    when(slottingServiceImpl.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(slottingPalletResponse);
    List<ContainerItem> containerItemList =
        rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);

    assertNotNull(containerItemList);
    assertFalse(containerItemList.isEmpty());
    assertEquals(containerItemList.size(), 2);
    assertEquals((long) containerItemList.get(0).getItemNumber(), 553857311L);
    assertEquals(containerItemList.get(0).getAsrsAlignment(), "SYM2_5");
    assertEquals(containerItemList.get(0).getSlotType(), "PRIME");
    assertNotNull(
        containerItemList.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));
    assertEquals(
        containerItemList.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID),
        "SYMBP");

    assertEquals((long) containerItemList.get(1).getItemNumber(), 553857312L);
    assertNull(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));

    verify(containerItemService, times(1)).getContainerItemMetaDataByUpcNumber(anyString());
    verify(slottingServiceImpl, times(1)).getPrimeSlot(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testGetContainerItemsByUPC_SlottingReturnsNoSlotErrorForAllItems()
      throws IOException {
    File resource = new ClassPathResource("MockSlottingErrorResponseForAllItems.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    SlottingPalletResponse slottingPalletResponse =
        gson.fromJson(mockResponse, SlottingPalletResponse.class);

    when(containerItemService.getContainerItemMetaDataByUpcNumber(anyString()))
        .thenReturn(getMockContainerItems());
    when(slottingServiceImpl.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(slottingPalletResponse);
    List<ContainerItem> containerItemList =
        rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);

    assertNotNull(containerItemList);
    assertFalse(containerItemList.isEmpty());
    assertEquals(containerItemList.size(), 2);
    assertEquals((long) containerItemList.get(1).getItemNumber(), 553857312L);
    assertNull(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));
    assertEquals((long) containerItemList.get(1).getItemNumber(), 553857312L);
    assertNull(
        containerItemList.get(1).getContainerItemMiscInfo().get(ReceivingConstants.PRIME_SLOT_ID));

    verify(containerItemService, times(1)).getContainerItemMetaDataByUpcNumber(anyString());
    verify(slottingServiceImpl, times(1)).getPrimeSlot(anyList(), any(HttpHeaders.class));
  }

  private List<ContainerItem> getMockContainerItems() throws IOException {
    File resource = new ClassPathResource("MockContainerItemResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return Arrays.asList(gson.fromJson(mockResponse, ContainerItem[].class));
  }

  @Test
  public void testGetContainerLabelsByTrackingIdReturnsSuccessResponse()
      throws ReceivingException, IOException {
    List<InstructionIdAndTrackingIdPair> instructionIdAndTrackingIdPairs = new ArrayList<>();
    instructionIdAndTrackingIdPairs.add(new InstructionIdAndTrackingIdPair(12345l, "9784587526"));
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(anyList()))
        .thenReturn(instructionIdAndTrackingIdPairs);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(getInstructionResponse()));

    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(getTrackingIds(), httpHeaders);

    assertNotNull(printJob);
    assertNotNull(printJob.get(PRINT_HEADERS_KEY));
    assertNotNull(printJob.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(printJob.get(PRINT_REQUEST_KEY));

    verify(containerPersisterService, times(1)).getInstructionIdsObjByTrackingIds(anyList());
    verify(instructionRepository, times(1)).findByIdIn(anyList());
  }

  @Test
  public void testGetContainerLabelsByTrackingIdReturnsSuccessResponse_QtyReceivedLpns()
      throws ReceivingException, IOException {
    List<InstructionIdAndTrackingIdPair> instructionIdAndTrackingIdPairs = new ArrayList<>();
    instructionIdAndTrackingIdPairs.add(
        new InstructionIdAndTrackingIdPair(12345l, "b06030323232322223"));
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(anyList()))
        .thenReturn(instructionIdAndTrackingIdPairs);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(getInstructionResponse_QtyReceiving()));

    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(
            Arrays.asList("b06030323232322223", "l06030323232322223"), httpHeaders);

    assertNotNull(printJob);
    assertNotNull(printJob.get(PRINT_HEADERS_KEY));
    assertNotNull(printJob.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(printJob.get(PRINT_REQUEST_KEY));

    verify(containerPersisterService, times(1)).getInstructionIdsObjByTrackingIds(anyList());
    verify(instructionRepository, times(1)).findByIdIn(anyList());
  }

  @SneakyThrows
  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetContainerLabelByTrackingIdThrowsExceptionWhenContainerIsNotFound()
      throws ReceivingException {
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(anyList())).thenReturn(null);
    rdcContainerService.getContainerLabelsByTrackingIds(getTrackingIds(), httpHeaders);
    verify(containerPersisterService, times(1)).getInstructionIdsObjByTrackingIds(anyList());
    verify(instructionRepository, times(0)).findByIdIn(anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetContainerLabelByTrackingIdThrowsExceptionForInvalidData()
      throws ReceivingException {
    rdcContainerService.getContainerLabelsByTrackingIds(Arrays.asList(), httpHeaders);
    verify(containerPersisterService, times(0)).getInstructionIdsObjByTrackingIds(anyList());
    verify(instructionRepository, times(0)).findByIdIn(anyList());
  }

  @SneakyThrows
  @Test
  void testGetContainerLabelsByTrackingIds_WhenNoInstructionsFound_ShouldReturnEmptyPrintJob() {
    List<String> trackingIds = Arrays.asList("id1", "id2");
    List<InstructionIdAndTrackingIdPair> instructionIdAndTrackingIdPairs = new ArrayList<>();
    instructionIdAndTrackingIdPairs.add(new InstructionIdAndTrackingIdPair(12345l, "id1"));
    HttpHeaders httpHeaders = new HttpHeaders();
    when(appConfig.isReprintOldLabelsEnabled()).thenReturn(true);
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(trackingIds))
        .thenReturn(instructionIdAndTrackingIdPairs);
    when(instructionRepository.findByIdIn(anyList())).thenReturn(Collections.emptyList());
    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(trackingIds, httpHeaders);
    assertFalse(printJob.isEmpty());
  }

  @SneakyThrows
  @Test
  void testGetContainerLabelsByTrackingIds_WhenContainerDetailsFound_ShouldReturnPrintJob() {
    List<String> trackingIds = Arrays.asList("id1", "id2");
    List<InstructionIdAndTrackingIdPair> instructionIdAndTrackingIdPairs = new ArrayList<>();
    instructionIdAndTrackingIdPairs.add(new InstructionIdAndTrackingIdPair(12345l, "id1"));
    HttpHeaders httpHeaders = new HttpHeaders();
    when(appConfig.isReprintOldLabelsEnabled()).thenReturn(true);
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(trackingIds))
        .thenReturn(instructionIdAndTrackingIdPairs);
    String resourcesPath =
        new File(
                "../../receiving-core/src/test/resources/inventory_get_bulk_containers_response.json")
            .getCanonicalPath();
    String mockResponse = new String(Files.readAllBytes(Paths.get(resourcesPath)));
    when(inventoryService.getBulkContainerDetails(trackingIds, httpHeaders))
        .thenReturn(mockResponse);
    when(asyncIqsRestApiClient.getItemDetailsFromItemNumber(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(new ItemBulkResponseDto())));
    when(asyncLocationService.getBulkLocationInfo(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JsonObject()));

    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(trackingIds, httpHeaders);
    assertFalse(printJob.isEmpty());
  }

  @SneakyThrows
  @Test(expectedExceptions = ReceivingInternalException.class)
  public void
      testGetContainerLabelByTrackingIdThrowsExceptionWhenFetchingContainerItemDetailsFromIqsAndLocationAPIs() {
    List<String> trackingIds = Arrays.asList("id1", "id2");
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(trackingIds))
        .thenReturn(Collections.emptyList());
    when(appConfig.isReprintOldLabelsEnabled()).thenReturn(true);
    String dataPath =
        new File(
                "../../receiving-core/src/test/resources/inventory_get_bulk_containers_response.json")
            .getCanonicalPath();
    String mockResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    when(inventoryService.getBulkContainerDetails(trackingIds, httpHeaders))
        .thenReturn(mockResponse);

    String iqsResourcesPath =
        new File("../../receiving-core/src/test/resources/iqs_item_details_by_item_numbers.json")
            .getCanonicalPath();
    String iqsResponse = new String(Files.readAllBytes(Paths.get(iqsResourcesPath)));
    Optional<ItemBulkResponseDto> optionalItemBulkResponseDto =
        Optional.of(new Gson().fromJson(iqsResponse, ItemBulkResponseDto.class));
    when(asyncIqsRestApiClient.getItemDetailsFromItemNumber(
            new HashSet<>(trackingIds), "32679", httpHeaders))
        .thenReturn(CompletableFuture.completedFuture(optionalItemBulkResponseDto));

    String locationResourcesPath =
        new File("../../receiving-core/src/test/resources/location_search_success_response.json")
            .getCanonicalPath();
    String locationResponse = new String(Files.readAllBytes(Paths.get(locationResourcesPath)));
    JsonObject response = new Gson().fromJson(locationResponse, JsonObject.class);
    when(asyncLocationService.getBulkLocationInfo(Arrays.asList("A0003"), httpHeaders))
        .thenReturn(CompletableFuture.completedFuture(response));

    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(trackingIds, httpHeaders);
    assertTrue(printJob.isEmpty());
  }

  @SneakyThrows
  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetContainerLabelByTrackingId_Success() {
    List<String> trackingIds = Arrays.asList("001000132679203193");
    HttpHeaders rdcHeader = MockHttpHeaders.getRDCHeaders();
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(trackingIds))
        .thenReturn(Collections.emptyList());
    when(appConfig.isReprintOldLabelsEnabled()).thenReturn(true);
    String resourcesPath =
        new File(
                "../../receiving-core/src/test/resources/inventory_get_bulk_containers_response.json")
            .getCanonicalPath();
    String mockResponse = new String(Files.readAllBytes(Paths.get(resourcesPath)));
    when(inventoryService.getBulkContainerDetails(trackingIds, rdcHeader)).thenReturn(mockResponse);

    String iqsResourcesPath =
        new File("../../receiving-core/src/test/resources/iqs_item_details_by_item_numbers.json")
            .getCanonicalPath();
    String iqsResponse = new String(Files.readAllBytes(Paths.get(iqsResourcesPath)));
    Type type = new TypeToken<ItemBulkResponseDto>() {}.getType();
    Optional<ItemBulkResponseDto> optionalItemBulkResponseDto =
        Optional.of(new Gson().fromJson(iqsResponse, type));
    doReturn(CompletableFuture.completedFuture(optionalItemBulkResponseDto))
        .when(asyncIqsRestApiClient)
        .getItemDetailsFromItemNumber(anySet(), anyString(), any(HttpHeaders.class));

    String locationResourcesPath =
        new File("../../receiving-core/src/test/resources/location_search_success_response.json")
            .getCanonicalPath();
    String locationResponse = new String(Files.readAllBytes(Paths.get(locationResourcesPath)));
    Type locType = new TypeToken<JsonObject>() {}.getType();
    JsonObject response = new Gson().fromJson(locationResponse, locType);
    doReturn(CompletableFuture.completedFuture(response))
        .when(asyncLocationService)
        .getBulkLocationInfo(anyList(), any(HttpHeaders.class));
    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(trackingIds, rdcHeader);
    assertTrue(printJob.isEmpty());
  }

  @SneakyThrows
  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetContainerLabelByTrackingId_Success_Without_Itemlevel_palletNode() {
    List<String> trackingIds = Arrays.asList("001000132679203193");
    HttpHeaders rdcHeader = MockHttpHeaders.getRDCHeaders();
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(trackingIds))
        .thenReturn(Collections.emptyList());
    when(appConfig.isReprintOldLabelsEnabled()).thenReturn(true);
    String resourcesPath =
        new File(
                "../../receiving-core/src/test/resources/inventory_get_bulk_containers_response.json")
            .getCanonicalPath();
    String mockResponse = new String(Files.readAllBytes(Paths.get(resourcesPath)));
    when(inventoryService.getBulkContainerDetails(trackingIds, rdcHeader)).thenReturn(mockResponse);

    String iqsResourcesPath =
        new File(
                "../../receiving-core/src/test/resources/iqs_item_details_by_item_numbers_without_itemlevel_palletnode.json")
            .getCanonicalPath();
    String iqsResponse = new String(Files.readAllBytes(Paths.get(iqsResourcesPath)));
    Type type = new TypeToken<ItemBulkResponseDto>() {}.getType();
    Optional<ItemBulkResponseDto> optionalItemBulkResponseDto =
        Optional.of(new Gson().fromJson(iqsResponse, type));
    doReturn(CompletableFuture.completedFuture(optionalItemBulkResponseDto))
        .when(asyncIqsRestApiClient)
        .getItemDetailsFromItemNumber(anySet(), anyString(), any(HttpHeaders.class));

    String locationResourcesPath =
        new File("../../receiving-core/src/test/resources/location_search_success_response.json")
            .getCanonicalPath();
    String locationResponse = new String(Files.readAllBytes(Paths.get(locationResourcesPath)));
    Type locType = new TypeToken<JsonObject>() {}.getType();
    JsonObject response = new Gson().fromJson(locationResponse, locType);
    doReturn(CompletableFuture.completedFuture(response))
        .when(asyncLocationService)
        .getBulkLocationInfo(anyList(), any(HttpHeaders.class));
    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(trackingIds, rdcHeader);
    assertTrue(printJob.isEmpty());
  }

  @Test
  public void testGetContainerLabelsByTrackingIdReturnsSuccessResponse_WithReprintValueAsR()
      throws ReceivingException, IOException {
    List<InstructionIdAndTrackingIdPair> instructionIdAndTrackingIdPairs = new ArrayList<>();
    instructionIdAndTrackingIdPairs.add(
        new InstructionIdAndTrackingIdPair(12345l, "b06030323232322223"));
    when(containerPersisterService.getInstructionIdsObjByTrackingIds(anyList()))
        .thenReturn(instructionIdAndTrackingIdPairs);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(getInstructionResponse_QtyReceiving()));

    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(
            Arrays.asList("b06030323232322223", "l06030323232322223"), httpHeaders);

    assertNotNull(printJob);
    assertNotNull(printJob.get(PRINT_HEADERS_KEY));
    assertNotNull(printJob.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(printJob.get(PRINT_REQUEST_KEY));

    verify(containerPersisterService, times(1)).getInstructionIdsObjByTrackingIds(anyList());
    verify(instructionRepository, times(1)).findByIdIn(anyList());
  }
}
