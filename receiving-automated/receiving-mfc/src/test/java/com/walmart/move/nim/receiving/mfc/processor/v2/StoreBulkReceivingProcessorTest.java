package com.walmart.move.nim.receiving.mfc.processor.v2;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;
import static com.walmart.move.nim.receiving.mfc.common.OperationType.MANUAL_FINALISE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import com.walmart.move.nim.receiving.mfc.service.MFCReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreBulkReceivingProcessorTest {

  @InjectMocks private StoreBulkReceivingProcessor storeBulkReceivingProcessor;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private ContainerService containerService;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private MFCProblemService mfcProblemService;

  @Mock private MFCReceiptService mfcReceiptService;

  @Mock private ContainerRepository containerRepository;

  @Mock private ContainerItemRepository containerItemRepository;

  @Mock private ProcessInitiator processInitiator;

  @Mock private ReceivingCounterService receivingCounterService;

  @Mock private ContainerTransformer containerTransformer;

  @Mock private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private AppConfig appConfig;

  @BeforeClass
  private void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(5504);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
  }

  @AfterMethod
  public void resetMock() {
    reset(containerRepository);
    reset(mfcContainerService);
    reset(containerService);
    reset(containerItemRepository);
    reset(containerTransformer);
    reset(mfcManagedConfig);
    reset(processInitiator);
    reset(receivingCounterService);
    reset(mfcReceiptService);
    reset(mfcDeliveryMetadataService);
    reset(tenantSpecificConfigReader);
    reset(appConfig);
  }

  @Test
  public void testDoExecuteIsStorePalletIncludedAndReceiptCreationDisabled() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer3.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setQuantity(50);
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);
    List<ContainerItem> containerItemList = containers.get(0).getContainerItems();

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentManualFinalization.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "STORE");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, true);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, false);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(0)).saveReceipt(anyList());
  }

  @Test
  public void testDoExecuteIsMFCPalletIncludedAndReceiptCreationDisabled() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItemList = Collections.singletonList(containerItem);

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "MFC");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, false);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(0)).saveReceipt(anyList());
  }

  @Test
  public void testDoExecuteIsStorePalletIncludedAndReceiptCreationEnabled() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer3.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setQuantity(50);
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);
    List<ContainerItem> containerItemList = containers.get(0).getContainerItems();

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentManualFinalization.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "STORE");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, true);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, false);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(1)).saveReceipt(anyList());
  }

  @Test
  public void testDoExecuteIsMFCPalletIncludedAndReceiptCreationEnabled() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItemList = Collections.singletonList(containerItem);

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "MFC");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, false);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(1)).saveReceipt(anyList());
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNotAbleToCreateContainerAsPalletIsDifferentType() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItemList = Collections.singletonList(containerItem);

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "MFC");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, true);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, false);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
  }

  @Test
  public void testDoExecuteIsMFCStorePalletEnabled() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItemList = Collections.singletonList(containerItem);

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "MFC");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, false);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    List<ContainerDTO> containerDTOList = getContainerDTOList();
    containerDTOList.get(0).getContainerMiscInfo().put("palletType", "STORE");
    when(containerTransformer.transformList(any())).thenReturn(containerDTOList);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(Boolean.FALSE);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(0)).saveReceipt(anyList());
  }

  @Test
  public void testDoExecuteIsMFCStorePalletDisabled() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItemList = Collections.singletonList(containerItem);

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "MFC");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, false);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    List<ContainerDTO> containerDTOList = getContainerDTOList();
    containerDTOList.get(0).getContainerMiscInfo().put("palletType", "MFC");
    when(containerTransformer.transformList(any())).thenReturn(containerDTOList);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(Boolean.FALSE);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(0)).saveReceipt(anyList());
    verify(mfcContainerService, times(2)).getContainerService();
  }

  @Test
  public void testDoExecuteIsMFCStorePalletWithEmptyMiscInfo() {

    Container container =
        MFCTestUtils.getContainer(
            "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
    List<Container> containers = Collections.singletonList(container);

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItemList = Collections.singletonList(containerItem);

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    asnDocument.setShipment(asnDocument.getShipments().get(0));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(String.valueOf(JacksonParser.writeValueAsString(asnDocument)))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    when(mfcManagedConfig.isAsyncBulkReceivingEnabled()).thenReturn(true);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    Map<String, Object> additionAttribute = new HashMap<>();
    additionAttribute.put(OPERATION_TYPE, MANUAL_FINALISE);
    additionAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, "MFC");
    additionAttribute.put(MFCConstant.STORE_PALLET_INCLUDED, false);
    additionAttribute.put(MFCConstant.MFC_PALLET_INCLUDED, true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(container);
    when(containerRepository.saveAll(any())).thenReturn(containers);
    when(containerItemRepository.saveAll(any())).thenReturn(containerItemList);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    List<Container> receivedContainers = new ArrayList<>();
    receivedContainers.add(container);
    receivingEvent.setAdditionalAttributes(additionAttribute);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenReturn(container);
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), any()))
        .thenReturn(containerItem);
    when(appConfig.getInSqlBatchSize()).thenReturn(10);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    List<ContainerDTO> containerDTOList = getContainerDTOList();
    containerDTOList.get(0).setContainerMiscInfo(null);
    when(containerTransformer.transformList(any())).thenReturn(containerDTOList);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(Boolean.FALSE);
    storeBulkReceivingProcessor.doExecute(receivingEvent);
    verify(mfcContainerService, times(1)).findContainerByDeliveryNumber(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(ASNDocument.class), any(Pack.class), anyMap());
    verify(mfcContainerService, times(3))
        .createPackItem(any(Pack.class), any(Item.class), any(ItemDetails.class), any());
    verify(containerService, times(1)).saveAll(containers);
    verify(mfcReceiptService, times(0)).saveReceipt(anyList());
    verify(mfcContainerService, times(2)).getContainerService();
  }

  private List<ContainerDTO> getContainerDTOList() {
    List<ContainerDTO> containerDTOList = new ArrayList<>();
    ContainerDTO containerDTO = new ContainerDTO();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("palletType", "STORE");
    containerDTO.setContainerMiscInfo(containerMiscInfo);
    containerDTOList.add(containerDTO);
    return containerDTOList;
  }
}
