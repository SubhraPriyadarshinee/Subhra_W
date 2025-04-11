package com.walmart.move.nim.receiving.mfc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentList;
import com.walmart.move.nim.receiving.core.model.InventoryContainerAdjustmentPayload;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.mfc.common.ChannelType;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.common.PalletType;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerOperation;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerRequestPayload;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerResponse;
import com.walmart.move.nim.receiving.mfc.model.inventory.MFCInventoryAdjustmentDTO;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.Eligibility;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MFCContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private MFCContainerService mfcContainerService;

  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private MFCDeliveryMetadataService deliveryMetaDataService;
  @Mock private ContainerTransformer containerTransformer;
  @Mock private ContainerService containerService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private MFCReceiptService mfcReceiptService;
  @Mock private MFCDeliveryService deliveryService;
  @Mock private DecantService decantService;
  @Mock private ReceivingCounterService receivingCounterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private AsyncPersister asyncPersister;
  private Integer defaultInvoiceLineStartValue;
  @Mock private AppConfig appconfig;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private InventoryService inventoryService;
  private Gson gson;
  @InjectMocks private TenantContext tenantContext;
  private Long DELIVERY_NUM = 55040153L;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.openMocks(this);
    gson = new Gson();
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(5504);
    ReflectionTestUtils.setField(mfcContainerService, "deliveryMetadataPageSize", 1);
    ReflectionTestUtils.setField(mfcContainerService, "asyncPersister", asyncPersister);
    ReflectionTestUtils.setField(mfcContainerService, "defaultInvoiceLineStartValue", 100);
    ReflectionTestUtils.setField(mfcContainerService, "appConfig", appconfig);
    ReflectionTestUtils.setField(mfcContainerService, "mfcManagedConfig", mfcManagedConfig);
    ReflectionTestUtils.setField(mfcContainerService, "inventoryService", inventoryService);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerPersisterService);
    reset(containerItemRepository);
    reset(deliveryMetaDataService);
    reset(containerTransformer);
    reset(containerService);
    reset(deliveryStatusPublisher);
    reset(mfcReceiptService);
    reset(deliveryService);
    reset(decantService);
    reset(receivingCounterService);
    reset(tenantSpecificConfigReader);
    reset(asyncPersister);
    reset(appconfig);
    reset(mfcManagedConfig);
    reset(inventoryService);
  }

  @Test
  public void testDetectContainers_happyPathManualMFC() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(Collections.singletonList(new Quantity(10L, "EA", QuantityType.DECANTED)))
            .build();

    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.ARV)
            .build();

    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));

    List<Container> containers = mfcContainerService.detectContainers(receiptDTO);

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(deliveryMetaDataService, times(1)).save(any(DeliveryMetaData.class));

    List<ContainerItem> selectedContainerItems = containers.get(0).getContainerItems();
    assertEquals(containers.size(), 1);
    assertEquals(selectedContainerItems.size(), 2);
  }

  @Test
  public void testDetectContainers_happyPathManualMFCWithExceptions() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Arrays.asList(
                    new Quantity(7L, "EA", QuantityType.DECANTED),
                    new Quantity(3L, "EA", QuantityType.DAMAGE),
                    new Quantity(3L, "EA", QuantityType.REJECTED)))
            .build();

    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.ARV)
            .build();

    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));

    List<Container> containers = mfcContainerService.detectContainers(receiptDTO);

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(deliveryMetaDataService, times(1)).save(any(DeliveryMetaData.class));

    List<ContainerItem> selectedContainerItems = containers.get(0).getContainerItems();
    assertEquals(containers.size(), 1);
    assertEquals(selectedContainerItems.size(), 2);
  }

  @Test
  public void testDetectContainers_noCapacityInContainer_manualMFCWithExceptions() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Arrays.asList(
                    new Quantity(7L, "EA", QuantityType.DECANTED),
                    new Quantity(3L, "EA", QuantityType.DAMAGE),
                    new Quantity(3L, "EA", QuantityType.REJECTED)))
            .build();

    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainerWithNoCapacity.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.ARV)
            .build();

    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));

    List<Container> containers = mfcContainerService.detectContainers(receiptDTO);

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(deliveryMetaDataService, times(1)).save(any(DeliveryMetaData.class));

    List<ContainerItem> selectedContainerItems = containers.get(0).getContainerItems();
    assertEquals(containers.size(), 1);
    assertEquals(selectedContainerItems.size(), 2);
  }

  @Test
  public void testDetectContainers_noCapacityInContainer_manualMFCWithExceptions2() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Arrays.asList(
                    new Quantity(7L, "EA", QuantityType.DECANTED),
                    new Quantity(3L, "EA", QuantityType.DAMAGE),
                    new Quantity(3L, "EA", QuantityType.REJECTED)))
            .build();

    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainerWithNoCapacity2.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.ARV)
            .build();

    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));

    List<Container> containers = mfcContainerService.detectContainers(receiptDTO);

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(deliveryMetaDataService, times(1)).save(any(DeliveryMetaData.class));

    List<ContainerItem> selectedContainerItems = containers.get(0).getContainerItems();
    assertEquals(containers.size(), 1);
    assertEquals(selectedContainerItems.size(), 2);
  }

  @Test
  public void testDetectContainers_happyPathAutoMFC() throws ReceivingException {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .gtin("00078742154640")
            .quantities(Collections.singletonList(new Quantity(10L, "EA", QuantityType.DECANTED)))
            .build();

    List<ContainerItem> mfcContainerItems =
        MFCTestUtils.getContainerItems(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainerItems.json");
    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.WRK)
            .build();

    when(containerItemRepository.findByGtinAndFacilityCountryCodeAndFacilityNum(
            anyString(), anyString(), anyInt()))
        .thenReturn(mfcContainerItems);
    when(containerService.getContainerListByTrackingIdList(any()))
        .thenReturn(Collections.singleton(mfcContainer));
    when(deliveryMetaDataService.findAllByDeliveryNumberIn(any()))
        .thenReturn(Collections.singletonList(deliveryMetaData));

    List<Container> containers = mfcContainerService.detectContainers(receiptDTO);

    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(deliveryMetaDataService, never()).save(any(DeliveryMetaData.class));

    List<ContainerItem> selectedContainerItems = containers.get(0).getContainerItems();
    assertEquals(containers.size(), 1);
    assertEquals(selectedContainerItems.size(), 2);
  }

  //  @Test
  public void testProcessDuplicateContainer() {

    when(deliveryMetaDataService.findByDeliveryStatusIn(any(List.class), any(Pageable.class)))
        .thenReturn(getDeliveryMetdaDataForDuplicateProcessing());
    when(containerPersisterService.findBySSCCAndDeliveryNumberIn(anyString(), anyList()))
        .thenReturn(getHappypathContainer());

    ContainerRequestPayload containerRequestPayload = new ContainerRequestPayload();

    ContainerResponse containerResponse =
        mfcContainerService.processDuplicateContainer(containerRequestPayload, false);
    assertEquals(containerResponse.getType(), ContainerOperation.CONTAINER_FOUND);
    assertEquals(containerResponse.getContainers().size(), 2);

    ContainerDTO containerDTO = containerResponse.getContainers().get(0);
    assertEquals(containerDTO.getContainerItems().size(), 3);
    assertEquals(
        containerDTO.getTrackingId(), containerDTO.getContainerItems().get(0).getTrackingId());
    assertNotEquals(containerDTO.getTrackingId(), containerDTO.getSsccNumber());

    verify(deliveryMetaDataService, times(1))
        .findByDeliveryStatusIn(any(List.class), any(Pageable.class));
    verify(containerPersisterService, times(1))
        .findBySSCCAndDeliveryNumberIn(anyString(), anyList());
  }

  private List<Container> getHappypathContainer() {
    return Arrays.asList(
        createContainer("123456789", 123456789L, Arrays.asList(123456L, 123478L, 123490L)),
        createContainer("987654321", 987654321L, Arrays.asList(123456L, 123478L, 123490L)));
  }

  private Container createContainer(
      String trackingId, Long deliveryNumber, List<Long> itemNumbers) {

    List<ContainerItem> containerItems = new ArrayList<>(itemNumbers.size());

    itemNumbers.forEach(
        item -> {
          ContainerItem containerItem = new ContainerItem();
          containerItem.setTrackingId("PA" + trackingId);
          containerItem.setInvoiceNumber(String.valueOf(System.currentTimeMillis()));
          containerItem.setInvoiceLineNumber(1);
          containerItem.setItemNumber(item);
          containerItem.setItemUPC("ItemUPC" + item);
          containerItem.setCaseUPC("CaseUPC" + item);
          containerItem.setGtin("ItemUPC" + item);
          containerItems.add(containerItem);
        });

    return Container.builder()
        .containerItems(containerItems)
        .deliveryNumber(deliveryNumber)
        .trackingId("PA" + trackingId)
        .ssccNumber(trackingId)
        .build();
  }

  private Page getDeliveryMetdaDataForDuplicateProcessing() {

    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("1234567890")
            .deliveryStatus(DeliveryStatus.ARV)
            .build();

    return new PageImpl(Arrays.asList(deliveryMetaData));
  }

  @Test(expectedExceptions = ReceivingConflictException.class)
  public void testCreateContainerWithDifferentTrackingId() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder().trackingId("999999").deliveryNumber(DELIVERY_NUM).build();
    mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
  }

  @Test
  public void testHappyPathCreateContainer() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));
    Map<String, Object> miscInfo = new HashMap<>();
    miscInfo.put("isReceivedThroughAutomatedSignal", Boolean.TRUE);
    miscInfo.put("isTempCompliance", Boolean.FALSE);
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .trackingId(asnDocument.getPacks().get(0).getPalletNumber())
            .deliveryNumber(DELIVERY_NUM)
            .miscInfo(miscInfo)
            .build();
    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    ContainerDTO containerDTO =
        ContainerDTO.builder()
            .ssccNumber(containerScanRequest.getTrackingId())
            .deliveryNumber(containerScanRequest.getDeliveryNumber())
            .build();

    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(null, null, null, container);
    when(containerTransformer.transform(any(Container.class))).thenReturn(containerDTO);
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(containerPersisterService.saveContainer(any())).thenReturn(container);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    Container container1 =
        mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
    verify(containerPersisterService, never()).saveContainer(containerArgumentCaptor.capture());
    verify(containerPersisterService, atLeastOnce())
        .findBySSCCAndDeliveryNumber(anyString(), anyLong());
    verify(containerPersisterService, never()).findByInvoiceNumber(anyString());
    assertNotNull(container1);
    assertEquals(container1.getContainerItems().size(), 3);
    for (ContainerItem containerItem : container1.getContainerItems()) {
      switch (String.valueOf(containerItem.getItemNumber())) {
        case "574153621":
          assertEquals(containerItem.getPluNumber().intValue(), 94069);
          assertEquals(containerItem.getCid(), "15834747");
          assertEquals(containerItem.getHybridStorageFlag(), "AMFC");
          assertEquals(containerItem.getDeptSubcatgNbr(), "47362");
          break;
        case "563866383":
          assertEquals(containerItem.getPluNumber().intValue(), 94068);
          assertEquals(containerItem.getCid(), "18892232");
          assertEquals(containerItem.getHybridStorageFlag(), "MFC");
          assertEquals(containerItem.getDeptSubcatgNbr(), "4841212");
          break;
        case "572519460":
          assertEquals(containerItem.getPluNumber().intValue(), 94067);
          assertEquals(containerItem.getCid(), "100802893");
          assertEquals(containerItem.getHybridStorageFlag(), "MFC");
          assertEquals(containerItem.getDeptSubcatgNbr(), "28897");
          break;
      }
    }

    Map<String, Object> containerMiscInfo = container1.getContainerMiscInfo();
    assertTrue((Boolean) containerMiscInfo.get("isReceivedThroughAutomatedSignal"));
    assertFalse((Boolean) containerMiscInfo.get("isTempCompliance"));
    assertEquals(container1.getEligibility(), Eligibility.HMFC);
  }

  @Test
  public void testCreateContainer_DuplicateInvoiceLineNumber() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentDuplicateInvoiceLineNumber.json");
    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainerDuplicateInvoice.json");
    BiFunction<Item, ItemDetails, Boolean> eligibleChecker =
        (item, itemDetails) ->
            StringUtils.equalsIgnoreCase(item.getReplenishmentCode(), "MARKET_FULFILLMENT_CENTER")
                && Objects.nonNull(itemDetails)
                && (boolean) itemDetails.getItemAdditonalInformation().get("mfcEnabled");

    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(999l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    Container container1 =
        mfcContainerService.createContainer(
            null,
            asnDocument,
            asnDocument.getPacks().get(2),
            createItemMap(asnDocument),
            eligibleChecker,
            OverageType.UKNOWN,
            new HashMap<>());
    assertEquals(container1.getContainerItems().size(), 3);
    assertNull(container1.getContainerItems().get(0).getDeptCatNbr());
  }

  @Test
  public void testCreateContainer_WithMultiplePacks() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMulitplePacks.json");
    BiFunction<Item, ItemDetails, Boolean> eligibleChecker = (item, itemDetails) -> null;

    Map<String, Container> containerMap = new HashMap<>();
    Map<Long, ItemDetails> itemDetailsMap = createItemMap(asnDocument);

    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");

    Map<String, PalletInfo> palletInfoMap = MFCUtils.getPalletInfoMap(asnDocument.getPacks());
    for (Pack pack : asnDocument.getPacks()) {
      when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
          .thenReturn(containerMap.get(pack.getPalletNumber()));
      when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
      Container container =
          mfcContainerService.createContainer(
              null,
              asnDocument,
              pack,
              itemDetailsMap,
              eligibleChecker,
              OverageType.UKNOWN,
              palletInfoMap);
      if (Objects.nonNull(container)) {
        containerMap.put(pack.getPalletNumber(), container);
      }
    }

    containerMap.forEach(
        (pallet, container) -> {
          switch (pallet) {
            case "120000000000000028":
              assertEquals(
                  container.getContainerMiscInfo().get(MFCConstant.PALLET_TYPE),
                  PalletType.MFC.toString());
              assertEquals(container.getContainerItems().get(0).getDeptCatNbr().intValue(), 1519);
              break;
            case "120000000000000029":
              assertEquals(
                  container.getContainerMiscInfo().get(MFCConstant.PALLET_TYPE),
                  PalletType.STORE.toString());
              assertEquals(container.getContainerItems().get(0).getDeptCatNbr().intValue(), 1519);
              break;
          }
        });
  }

  private Map<Long, ItemDetails> createItemMap(ASNDocument asnDocument) {
    return asnDocument
        .getItems()
        .stream()
        .collect(
            Collectors.toMap(itemDetails -> itemDetails.getNumber(), itemDetails -> itemDetails));
  }

  @Test
  public void testAddSkuIfRequired() {
    MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO =
        MFCTestUtils.getInventoryAdjustmentTo(
            "../../receiving-test/src/main/resources/json/mfc/inventoryAdjustment/inventoryAddSku.json");
    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.WRK)
            .build();
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    when(containerPersisterService.saveContainer(any())).thenReturn(mfcContainer);
    mfcContainerService.addSkuIfRequired(mfcInventoryAdjustmentDTO);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    assertNotNull(container);
    assertNotNull(container.getContainerItems());
    Optional<ContainerItem> _containerItem =
        container
            .getContainerItems()
            .stream()
            .filter(item -> item.getItemNumber().equals(583309842L))
            .findAny();
    ContainerItem containerItem = _containerItem.get();
    assertEquals(containerItem.getPluNumber().intValue(), 9987);
    assertEquals(containerItem.getCid(), "13873247");
    assertEquals(containerItem.getDeptCatNbr().intValue(), 5196);
    assertEquals(containerItem.getHybridStorageFlag(), "MFC");
  }

  @Test
  public void testPublishWorkingIfApplicable_CurrentStatusArv() {
    mfcContainerService.publishWorkingIfApplicable(
        DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.ARV).deliveryNumber("1").build());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), any(), anyList(), any());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testPublishWorkingIfApplicable_CurrentStatusSCH() {
    mfcContainerService.publishWorkingIfApplicable(
        DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.SCH).deliveryNumber("1").build());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), any(), anyList(), any());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testPublishWorkingIfApplicable_CurrentStatusWRK() {
    mfcContainerService.publishWorkingIfApplicable(
        DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.WRK).deliveryNumber("1").build());
    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(anyLong(), any(), anyList(), any());
    verify(deliveryMetaDataService, never()).save(any());
  }

  @Test
  public void testPublishWorkingIfApplicable_CurrentStatusUnloadingComplete() {
    mfcContainerService.publishWorkingIfApplicable(
        DeliveryMetaData.builder()
            .deliveryStatus(DeliveryStatus.UNLOADING_COMPLETE)
            .deliveryNumber("1")
            .build());
    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(anyLong(), any(), anyList(), any());
    verify(deliveryMetaDataService, never()).save(any());
  }

  @Test
  public void testPublishWorkingIfApplicable_CurrentStatusComplete() {
    mfcContainerService.publishWorkingIfApplicable(
        DeliveryMetaData.builder()
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .deliveryNumber("1")
            .build());
    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(anyLong(), any(), anyList(), any());
    verify(deliveryMetaDataService, never()).save(any());
  }

  @Test
  public void testInitiateContainerRemoval() {
    List<Container> containers =
        MFCTestUtils.getContainers("src/test/resources/osdr/receivedContainers.json");
    when(containerPersisterService.findByDeliveryNumberAndSsccIn(anyLong(), any()))
        .thenReturn(containers);
    when(mfcManagedConfig.getInventoryContainerRemovalBatchSize()).thenReturn(10);
    when(inventoryService.performInventoryBulkAdjustment(any())).thenReturn(StringUtils.EMPTY);
    ArgumentCaptor<InventoryAdjustmentList> adjustmentListArgumentCaptor =
        ArgumentCaptor.forClass(InventoryAdjustmentList.class);
    mfcContainerService.initiateContainerRemoval(
        MFCTestUtils.getOSDRPayload("src/test/resources/osdr/osdrPayload.json"));
    verify(inventoryService, times(1))
        .performInventoryBulkAdjustment(adjustmentListArgumentCaptor.capture());
    InventoryAdjustmentList inventoryAdjustmentList = adjustmentListArgumentCaptor.getValue();
    Assert.assertNotNull(inventoryAdjustmentList);
    Assert.assertEquals(inventoryAdjustmentList.getAdjustments().size(), 2);
    List<String> receivedContainers =
        containers.stream().map(Container::getSsccNumber).collect(Collectors.toList());
    for (InventoryContainerAdjustmentPayload inventoryContainerAdjustmentPayload :
        inventoryAdjustmentList.getAdjustments()) {
      Assert.assertTrue(
          receivedContainers.contains(inventoryContainerAdjustmentPayload.getTrackingId()));
    }
  }

  @Test
  public void testInitiateContainerRemovalNoReceivedContaner() {
    when(containerPersisterService.findByDeliveryNumberAndSsccIn(anyLong(), any()))
        .thenReturn(null);
    when(mfcManagedConfig.getInventoryContainerRemovalBatchSize()).thenReturn(10);
    when(inventoryService.performInventoryBulkAdjustment(any())).thenReturn(StringUtils.EMPTY);
    ArgumentCaptor<InventoryAdjustmentList> adjustmentListArgumentCaptor =
        ArgumentCaptor.forClass(InventoryAdjustmentList.class);
    mfcContainerService.initiateContainerRemoval(
        MFCTestUtils.getOSDRPayload("src/test/resources/osdr/osdrPayload.json"));
    verify(inventoryService, never())
        .performInventoryBulkAdjustment(adjustmentListArgumentCaptor.capture());
  }

  @Test
  public void testInitiateContainerRemovalBatches() {
    List<Container> containers =
        MFCTestUtils.getContainers("src/test/resources/osdr/receivedContainers.json");
    when(containerPersisterService.findByDeliveryNumberAndSsccIn(anyLong(), any()))
        .thenReturn(containers);
    when(mfcManagedConfig.getInventoryContainerRemovalBatchSize()).thenReturn(1);
    when(inventoryService.performInventoryBulkAdjustment(any())).thenReturn(StringUtils.EMPTY);
    ArgumentCaptor<InventoryAdjustmentList> adjustmentListArgumentCaptor =
        ArgumentCaptor.forClass(InventoryAdjustmentList.class);
    mfcContainerService.initiateContainerRemoval(
        MFCTestUtils.getOSDRPayload("src/test/resources/osdr/osdrPayload.json"));
    verify(inventoryService, times(2))
        .performInventoryBulkAdjustment(adjustmentListArgumentCaptor.capture());
  }

  @Test
  public void testAddSkuForStorePallet() {
    MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO =
        MFCTestUtils.getInventoryAdjustmentTo(
            "../../receiving-test/src/main/resources/json/mfc/inventoryAdjustment/inventoryAddSkuStore.json");
    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.WRK)
            .build();
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    ArgumentCaptor<List<ContainerItem>> containerItemArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    when(containerPersisterService.saveContainer(any())).thenReturn(mfcContainer);
    mfcContainerService.addSkuIfRequired(mfcInventoryAdjustmentDTO);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    verify(deliveryService, times(1))
        .publishNewInvoice(
            containerArgumentCaptor.capture(), containerItemArgumentCaptor.capture(), any(), any());
    List<ContainerItem> containerItem = containerItemArgumentCaptor.getValue();
    assertEquals(containerItem.size(), 1);
    assertEquals(containerItem.get(0).getPluNumber().intValue(), 9987);
    assertEquals(containerItem.get(0).getCid(), "13873247");
    assertEquals(containerItem.get(0).getInvoiceNumber(), "90650818924033");
  }

  @Test
  public void testAddSkuForStorePalletWithoutInvoice() {
    MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO =
        MFCTestUtils.getInventoryAdjustmentTo(
            "../../receiving-test/src/main/resources/json/mfc/inventoryAdjustment/inventoryAddSkuStoreWithoutInvoice.json");
    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.WRK)
            .build();
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    ArgumentCaptor<List<ContainerItem>> containerItemArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    when(containerPersisterService.saveContainer(any())).thenReturn(mfcContainer);
    doNothing().when(deliveryService).publishNewInvoice(any(), any(), any(), any());
    mfcContainerService.addSkuIfRequired(mfcInventoryAdjustmentDTO);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    verify(deliveryService, times(1))
        .publishNewInvoice(
            containerArgumentCaptor.capture(), containerItemArgumentCaptor.capture(), any(), any());
    List<ContainerItem> containerItem = containerItemArgumentCaptor.getValue();
    assertEquals(containerItem.size(), 1);
    assertEquals(containerItem.get(0).getPluNumber().intValue(), 9987);
    assertEquals(containerItem.get(0).getCid(), "13873247");
    assertEquals(containerItem.get(0).getInvoiceNumber(), "7014041144");
    assertEquals(containerItem.get(0).getQuantityUOM(), "EA");
    assertEquals(containerItem.get(0).getOrderFilledQtyUom(), "EA");
  }

  @Test
  public void testAddSkuWithWeightedItem() {
    MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO =
        MFCTestUtils.getInventoryAdjustmentTo(
            "../../receiving-test/src/main/resources/json/mfc/inventoryAdjustment/inventoryAddSkuWeightedItem.json");
    Container mfcContainer =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("55040037")
            .deliveryStatus(DeliveryStatus.WRK)
            .build();
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(mfcContainer);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    ArgumentCaptor<List<ContainerItem>> containerItemArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    when(containerPersisterService.saveContainer(any())).thenReturn(mfcContainer);
    doNothing().when(deliveryService).publishNewInvoice(any(), any(), any(), any());
    mfcContainerService.addSkuIfRequired(mfcInventoryAdjustmentDTO);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    verify(deliveryService, times(1))
        .publishNewInvoice(
            containerArgumentCaptor.capture(), containerItemArgumentCaptor.capture(), any(), any());
    List<ContainerItem> containerItem = containerItemArgumentCaptor.getValue();
    assertEquals(containerItem.size(), 1);
    assertEquals(containerItem.get(0).getPluNumber().intValue(), 9987);
    assertEquals(containerItem.get(0).getCid(), "13873247");
    assertEquals(containerItem.get(0).getInvoiceNumber(), "7014041144");
    assertEquals(containerItem.get(0).getQuantityUOM(), "centi-LB");
    assertEquals(containerItem.get(0).getOrderFilledQtyUom(), "centi-LB");
    assertEquals(containerItem.get(0).getDeptNumber().intValue(), 13);
  }

  @Test
  public void testHappyPathCreateContainerHybridMfcEligiblityAmfc() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentHybridMfcEligibilityAmfc.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .trackingId(asnDocument.getPacks().get(0).getPalletNumber())
            .deliveryNumber(DELIVERY_NUM)
            .build();
    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    ContainerDTO containerDTO =
        ContainerDTO.builder()
            .ssccNumber(containerScanRequest.getTrackingId())
            .deliveryNumber(containerScanRequest.getDeliveryNumber())
            .build();

    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(121l);
    receivingCounter.setPrefix("PA");
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(null, null, null, container);
    when(containerTransformer.transform(any(Container.class))).thenReturn(containerDTO);
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(containerPersisterService.saveContainer(any())).thenReturn(container);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    Container container1 =
        mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
    verify(containerPersisterService, never()).saveContainer(containerArgumentCaptor.capture());
    verify(containerPersisterService, atLeastOnce())
        .findBySSCCAndDeliveryNumber(anyString(), anyLong());
    assertNotNull(container1);
    assertEquals(container1.getEligibility(), Eligibility.AMFC);
    assertEquals(container1.getContainerItems().size(), 3);
    for (ContainerItem containerItem : container1.getContainerItems()) {
      switch (String.valueOf(containerItem.getItemNumber())) {
        case "574153621":
          assertEquals(containerItem.getHybridStorageFlag(), "AMFC");
          break;
        case "563866383":
          assertEquals(containerItem.getHybridStorageFlag(), "AMFC");
          break;
        case "572519460":
          assertEquals(containerItem.getHybridStorageFlag(), "AMFC");
          break;
      }
    }
  }

  @Test
  public void testHappyPathCreateContainerHybridMfcEligiblityManual() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentHybridMfcEligibilityManual.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .trackingId(asnDocument.getPacks().get(0).getPalletNumber())
            .deliveryNumber(DELIVERY_NUM)
            .build();
    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    ContainerDTO containerDTO =
        ContainerDTO.builder()
            .ssccNumber(containerScanRequest.getTrackingId())
            .deliveryNumber(containerScanRequest.getDeliveryNumber())
            .build();

    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(122l);
    receivingCounter.setPrefix("PA");
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(null, null, null, container);
    when(containerTransformer.transform(any(Container.class))).thenReturn(containerDTO);
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(containerPersisterService.saveContainer(any())).thenReturn(container);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    Container container1 =
        mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
    verify(containerPersisterService, never()).saveContainer(containerArgumentCaptor.capture());
    verify(containerPersisterService, atLeastOnce())
        .findBySSCCAndDeliveryNumber(anyString(), anyLong());
    assertNotNull(container1);
    assertEquals(container1.getEligibility(), Eligibility.MFC);
    assertEquals(container1.getContainerItems().size(), 3);
    for (ContainerItem containerItem : container1.getContainerItems()) {
      switch (String.valueOf(containerItem.getItemNumber())) {
        case "574153621":
          assertEquals(containerItem.getHybridStorageFlag(), "MFC");
          break;
        case "563866383":
          assertEquals(containerItem.getHybridStorageFlag(), "MFC");
          break;
        case "572519460":
          assertEquals(containerItem.getHybridStorageFlag(), "MFC");
          break;
      }
    }
  }

  @Test
  public void testHappyPathCreateContainerHybridMfcStorePallet() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentHybridMfcStorePallet.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .trackingId(asnDocument.getPacks().get(0).getPalletNumber())
            .deliveryNumber(DELIVERY_NUM)
            .build();
    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json");
    ContainerDTO containerDTO =
        ContainerDTO.builder()
            .ssccNumber(containerScanRequest.getTrackingId())
            .deliveryNumber(containerScanRequest.getDeliveryNumber())
            .build();

    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(123l);
    receivingCounter.setPrefix("PA");
    when(containerPersisterService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(null, null, null, container);
    when(containerTransformer.transform(any(Container.class))).thenReturn(containerDTO);
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(containerPersisterService.saveContainer(any())).thenReturn(container);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    Container container1 =
        mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
    verify(containerPersisterService, never()).saveContainer(containerArgumentCaptor.capture());
    verify(containerPersisterService, atLeastOnce())
        .findBySSCCAndDeliveryNumber(anyString(), anyLong());
    assertNotNull(container1);
    assertNull(container1.getEligibility());
    assertEquals(container1.getContainerItems().size(), 3);
    assertEquals(
        container1.getContainerMiscInfo().get(MFCConstant.CHANNEL_TYPE),
        ChannelType.STAPLESTOCK.name());
    assertEquals(
        container1.getContainerMiscInfo().get(MFCConstant.SOURCE_TYPE),
        asnDocument.getShipment().getSource().getType());
    for (ContainerItem containerItem : container1.getContainerItems()) {
      switch (String.valueOf(containerItem.getItemNumber())) {
        case "574153621":
          assertNull(containerItem.getHybridStorageFlag());
          break;
        case "563866383":
          assertNull(containerItem.getHybridStorageFlag());
          break;
        case "572519460":
          assertNull(containerItem.getHybridStorageFlag());
          break;
      }
    }
  }
}
