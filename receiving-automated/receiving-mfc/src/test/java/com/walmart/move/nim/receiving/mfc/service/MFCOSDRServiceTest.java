package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MARKET_FULFILLMENT_CENTER;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MFC;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.STORE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.osdr.v2.*;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.common.OperationType;
import com.walmart.move.nim.receiving.mfc.common.PalletType;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRContainer;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRItem;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRPayload;
import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRReceipt;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MFCOSDRServiceTest extends ReceivingTestBase {

  @InjectMocks private MFCOSDRService mfcosdrService;

  @Mock private ContainerPersisterService containerPersisterService;

  @Mock private ReceiptService receiptService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private MFCDeliveryService deliveryService;

  @Mock private KafkaTemplate kafkaTemplate;

  @Mock protected MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Mock private AppConfig appConfig;

  private Gson gson = new Gson();
  private static final String EACHES = "EA";
  private static final String LB = "LB";
  private static final String CENTI_LB = "centi-LB";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(5504);
    when(appConfig.getUomScalingPrefix()).thenReturn("centi");
    when(appConfig.getScalableUomList()).thenReturn(Arrays.asList("LB", "OZ"));
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerPersisterService);
    reset(receiptService);
    reset(deliveryService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testCreateOSDRv2Payload_WRKDelivery() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainers.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceipts.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/MFCASN.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs2.json"));

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 1);
              break;
            case "100000000000000316":
              assertNotNull(container.getOverage());
              assertEquals(container.getOverage().getQuantity().intValue(), 3);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getOverage());
              assertEquals(purchaseOrder.getOverage().getQuantity().intValue(), 3);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 6);
              break;
          }
        });
  }

  private Optional<DeliveryMetaData> getDeliveryMetadata(DeliveryStatus status) {
    return Optional.of(DeliveryMetaData.builder().deliveryStatus(status).build());
  }

  @Test
  public void testCreateOSDRv2Payload_FNLDelivery() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainers.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceipts.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs2.json"));

    ASNDocument asnDocument = MFCTestUtils.getASNDocument("src/test/resources/osdr/MFCASN.json");
    asnDocument.getDelivery().getStatusInformation().setStatus("FNL");
    when(deliveryService.getGDMData(anyString(), anyString())).thenReturn(asnDocument);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.FNL));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 1);
              break;
            case "100000000000000316":
              assertNotNull(container.getOverage());
              assertEquals(container.getOverage().getQuantity().intValue(), 3);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getOverage());
              assertEquals(purchaseOrder.getOverage().getQuantity().intValue(), 3);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 6);
              break;
          }
        });
  }

  @Test
  public void testCreateOSDRv2Payload_CompleteDelivery() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainers.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceipts.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs2.json"));

    ASNDocument asnDocument = MFCTestUtils.getASNDocument("src/test/resources/osdr/MFCASN.json");
    asnDocument.getDelivery().getStatusInformation().setStatus("FNL");
    when(deliveryService.getGDMData(anyString(), anyString())).thenReturn(asnDocument);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.COMPLETE));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 1);
              break;
            case "100000000000000316":
              assertNotNull(container.getOverage());
              assertEquals(container.getOverage().getQuantity().intValue(), 3);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getOverage());
              assertEquals(purchaseOrder.getOverage().getQuantity().intValue(), 3);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 6);
              break;
          }
        });
  }

  @Test
  public void testCreateOSDRv2Payload_WRKDeliveryStore() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/StoreContainers.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceipts.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/StoreASN.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs2.json"));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 2);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000316":
              assertNotNull(container.getOverage());
              assertEquals(container.getOverage().getQuantity().intValue(), 3);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getOverage());
              assertEquals(purchaseOrder.getOverage().getQuantity().intValue(), 3);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
  }

  @Test
  public void testCreateOSDRv2Payload_WRKDeliveryStoreIncluded() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/StoreContainers.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceipts.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/StoreASN.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs2.json"));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, "storePallets");

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 1);
              break;
            case "100000000000000316":
              assertNotNull(container.getOverage());
              assertEquals(container.getOverage().getQuantity().intValue(), 3);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getOverage());
              assertEquals(purchaseOrder.getOverage().getQuantity().intValue(), 3);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 6);
              break;
          }
        });
  }

  // If none of the container is received
  @Test
  public void testShortageWithOutContainer() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    doThrow(ReceivingDataNotFoundException.class)
        .when(containerPersisterService)
        .getContainerByDeliveryNumber(anyLong());
    doThrow(ReceivingDataNotFoundException.class)
        .when(receiptService)
        .findByDeliveryNumber(anyLong());

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/StoreASN.json"));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
            case "100000000000000316":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 5);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 10);
              break;
          }
        });
  }

  // If none of the container is received and we got osdr endpoint request stating
  // include=storePallets
  @Test
  public void testShortageWithOutContainer_WithURLParam() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    doThrow(ReceivingDataNotFoundException.class)
        .when(containerPersisterService)
        .getContainerByDeliveryNumber(anyLong());
    doThrow(ReceivingDataNotFoundException.class)
        .when(receiptService)
        .findByDeliveryNumber(anyLong());

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/StoreASN.json"));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, "storePallets");

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
            case "100000000000000316":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 5);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 10);
              break;
          }
        });
  }

  // If MFC Container received , but not decanted
  @Test
  public void testShortageWithOutContainer_forMFCPallet() throws ReceivingException {
    Long deliveryNumber = 55040217L;
    doThrow(ReceivingDataNotFoundException.class)
        .when(receiptService)
        .findByDeliveryNumber(anyLong());

    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainers.json"));

    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/MFCASN.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocs2.json"));

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 3);
    assertEquals(purchaseOrders.size(), 2);

    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
            case "100000000000000316":
              assertNull(container.getShortage());
              assertNull(container.getOverage());
              break;
            case "100000000000000314":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 5);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertNull(purchaseOrder.getShortage());
              assertNull(purchaseOrder.getOverage());
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 10);
              break;
          }
        });
  }

  @Test
  public void testShortageWithLooseCase() throws ReceivingException {
    Long deliveryNumber = 55040505L;
    doThrow(ReceivingDataNotFoundException.class)
        .when(receiptService)
        .findByDeliveryNumber(anyLong());

    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(
            MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainersLooseCase.json"));

    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(
            MFCTestUtils.getASNDocument("src/test/resources/osdr/ASNDocumentWithLooseCase.json"));

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);

    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));
    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, "storePallets");

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 11);
    assertEquals(purchaseOrders.size(), 2);

    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "6064013196":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 40);
              break;
            case "6064013286":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 37);
              break;
          }
        });
  }

  @Test
  public void testCreateOSDRv2Payload_RejectAndDamage() throws ReceivingException {
    Long deliveryNumber = 55040541L;
    doReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainerWithRejects.json"))
        .when(containerPersisterService)
        .getContainerByDeliveryNumber(anyLong());
    doReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceiptWithRejects.json"))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());

    doReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/ASNDocumentReject.json"))
        .when(deliveryService)
        .getGDMData(anyString(), anyString());
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(
            MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DeliveryDocsReject.json"));

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 1);
    assertEquals(purchaseOrders.size(), 2);
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "8000000217":
              assertNotNull(purchaseOrder.getShortage());
              assertNotNull(purchaseOrder.getReject());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 2);
              assertEquals(purchaseOrder.getDamage().getQuantity().intValue(), 3);
              assertEquals(purchaseOrder.getDamage().getCode(), OSDRCode.D74.getCode());
              assertEquals(purchaseOrder.getReject().getQuantity().intValue(), 10);
              assertEquals(purchaseOrder.getReject().getCode(), OSDRCode.R88.getCode());
              break;
            case "8000000218":
              assertNotNull(purchaseOrder.getReject());
              assertEquals(purchaseOrder.getReject().getQuantity().intValue(), 15);
              assertTrue(
                  Arrays.asList(
                          OSDRCode.R78.getCode(),
                          OSDRCode.R83.getCode(),
                          OSDRCode.R86.getCode(),
                          OSDRCode.R87.getCode())
                      .contains(purchaseOrder.getReject().getCode()));
              break;
          }
        });
  }

  @Test
  public void testfetchOSDRDetails() {
    List<Long> deliveryNumber = Arrays.asList(55040217L, 55040218L);
    when(containerPersisterService.getContainerByDeliveryNumberIn(anyList()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainers.json"));
    when(receiptService.findByDeliveryNumberIn(anyList()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceipts.json"));

    List<MFCOSDRPayload> mfcosdrPayloads = mfcosdrService.fetchOSDRDetails(deliveryNumber);
    assertNotNull(mfcosdrPayloads);
    assertEquals(mfcosdrPayloads.size(), deliveryNumber.size());
    for (MFCOSDRPayload mfcosdrPayload : mfcosdrPayloads) {
      switch (mfcosdrPayload.getDeliveryNumber().toString()) {
        case "55040217":
          assertNotNull(mfcosdrPayload.getContainers());
          assertEquals(mfcosdrPayload.getContainers().size(), 2);
          for (MFCOSDRContainer mfcosdrContainer : mfcosdrPayload.getContainers()) {
            MFCOSDRItem containerItem;
            MFCOSDRReceipt mfcosdrReceipt;
            switch (mfcosdrContainer.getTrackingId()) {
              case "100000000000000316":
                assertEquals(mfcosdrContainer.getType(), PalletType.MFC.name());
                assertEquals(mfcosdrContainer.getOperationType(), OperationType.OVERAGE.name());
                assertNotNull(mfcosdrContainer.getContent());
                assertEquals(mfcosdrContainer.getContent().size(), 1);
                containerItem = mfcosdrContainer.getContent().get(0);
                assertEquals(containerItem.getGtin(), "00078742351926");
                assertEquals(containerItem.getItemNumber().intValue(), 565209430);
                assertEquals(containerItem.getInvoiceLineNumber().intValue(), 3);
                assertEquals(containerItem.getInvoiceNumber(), "7000000078");
                assertEquals(containerItem.getVnpk().intValue(), 1);
                assertEquals(containerItem.getWhpk().intValue(), 1);
                assertEquals(containerItem.getReceipt().size(), 1);
                mfcosdrReceipt = containerItem.getReceipt().get(0);
                assertEquals(mfcosdrReceipt.getQuantity().intValue(), 3);
                assertEquals(mfcosdrReceipt.getUom(), "EA");
                assertEquals(mfcosdrReceipt.getType(), QuantityType.RECEIVED.getType());
                break;
              case "100000000000000306":
                assertEquals(mfcosdrContainer.getType(), PalletType.MFC.name());
                assertEquals(mfcosdrContainer.getOperationType(), OperationType.NORMAL.name());
                assertEquals(mfcosdrContainer.getContent().size(), 1);
                containerItem = mfcosdrContainer.getContent().get(0);
                assertEquals(containerItem.getGtin(), "00078742351926");
                assertEquals(containerItem.getItemNumber().intValue(), 565209430);
                assertEquals(containerItem.getInvoiceLineNumber().intValue(), 4);
                assertEquals(containerItem.getInvoiceNumber(), "7000000077");
                assertEquals(containerItem.getVnpk().intValue(), 1);
                assertEquals(containerItem.getWhpk().intValue(), 1);
                assertEquals(containerItem.getReceipt().size(), 1);
                mfcosdrReceipt = containerItem.getReceipt().get(0);
                assertEquals(mfcosdrReceipt.getQuantity().intValue(), 4);
                assertEquals(mfcosdrReceipt.getUom(), "EA");
                assertEquals(mfcosdrReceipt.getType(), QuantityType.RECEIVED.getType());
                break;
            }
          }
          break;
        case "55040218":
          assertNotNull(mfcosdrPayload.getExceptionCode());
          assertEquals(mfcosdrPayload.getExceptionCode(), ExceptionCodes.CONTAINER_NOT_FOUND);
          break;
      }
    }
  }

  @Test
  public void testfetchOSDRDetailsWithRejectAndDamage() {
    Long deliveryNumber = 55040541l;
    when(containerPersisterService.getContainerByDeliveryNumberIn(anyList()))
        .thenReturn(
            MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainerWithRejects.json"));
    when(receiptService.findByDeliveryNumberIn(anyList()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceiptWithRejects.json"));

    List<MFCOSDRPayload> mfcosdrPayloads =
        mfcosdrService.fetchOSDRDetails(Arrays.asList(deliveryNumber));
    assertNotNull(mfcosdrPayloads);
    MFCOSDRPayload mfcosdrPayload = mfcosdrPayloads.get(0);
    assertNotNull(mfcosdrPayload);
    assertEquals(mfcosdrPayload.getDeliveryNumber(), deliveryNumber);
    assertNotNull(mfcosdrPayload.getContainers());
    assertEquals(mfcosdrPayload.getContainers().size(), 1);
    MFCOSDRContainer mfcosdrContainer = mfcosdrPayload.getContainers().get(0);
    assertEquals(mfcosdrContainer.getTrackingId(), "120000000000000026");
    assertEquals(mfcosdrContainer.getType(), PalletType.MFC.name());
    assertEquals(mfcosdrContainer.getOperationType(), OperationType.NORMAL.name());
    assertEquals(mfcosdrContainer.getContent().size(), 2);
    for (MFCOSDRItem containerItem : mfcosdrContainer.getContent()) {
      switch (containerItem.getInvoiceNumber()) {
        case "8000000217":
          assertEquals(containerItem.getGtin(), "00078742351926");
          assertEquals(containerItem.getItemNumber().intValue(), 565209430);
          assertEquals(containerItem.getInvoiceLineNumber().intValue(), 1);
          assertEquals(containerItem.getVnpk().intValue(), 1);
          assertEquals(containerItem.getWhpk().intValue(), 1);
          assertEquals(containerItem.getReceipt().size(), 2);
          for (MFCOSDRReceipt mfcosdrReceipt : containerItem.getReceipt()) {
            if (QuantityType.REJECTED.getType().equalsIgnoreCase(mfcosdrReceipt.getType())) {
              assertEquals(mfcosdrReceipt.getQuantity().intValue(), 10);
              assertTrue(
                  Arrays.asList(
                          OSDRCode.R78.getCode(), OSDRCode.R83.getCode(), OSDRCode.R88.getCode())
                      .contains(mfcosdrReceipt.getReasonCode()));
            } else if (QuantityType.DAMAGE.getType().equalsIgnoreCase(mfcosdrReceipt.getType())) {
              assertEquals(mfcosdrReceipt.getQuantity().intValue(), 3);
              assertEquals(mfcosdrReceipt.getReasonCode(), OSDRCode.D74.getCode());
            }
          }
          break;
        case "8000000218":
          assertEquals(containerItem.getGtin(), "00078742351926");
          assertEquals(containerItem.getItemNumber().intValue(), 565209430);
          assertEquals(containerItem.getInvoiceLineNumber().intValue(), 1);
          assertEquals(containerItem.getVnpk().intValue(), 1);
          assertEquals(containerItem.getWhpk().intValue(), 1);
          assertEquals(containerItem.getReceipt().size(), 1);
          MFCOSDRReceipt mfcosdrReceipt = containerItem.getReceipt().get(0);
          assertEquals(mfcosdrReceipt.getQuantity().intValue(), 15);
          assertEquals(mfcosdrReceipt.getUom(), "EA");
          assertEquals(mfcosdrReceipt.getType(), QuantityType.REJECTED.getType());
          assertTrue(
              Arrays.asList(OSDRCode.R78.getCode(), OSDRCode.R83.getCode())
                  .contains(mfcosdrReceipt.getReasonCode()));
          break;
      }
    }
  }

  @Test
  public void testfetchOSDRDetails_WithNoReceipts() {
    Long deliveryNumber = 55040217L;
    when(containerPersisterService.getContainerByDeliveryNumberIn(anyList()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainers.json"));
    when(receiptService.findByDeliveryNumberIn(anyList())).thenReturn(null);

    List<MFCOSDRPayload> mfcosdrPayloads =
        mfcosdrService.fetchOSDRDetails(Arrays.asList(deliveryNumber));
    assertNotNull(mfcosdrPayloads);
    MFCOSDRPayload mfcosdrPayload = mfcosdrPayloads.get(0);
    assertNotNull(mfcosdrPayload);
    assertEquals(mfcosdrPayload.getDeliveryNumber(), deliveryNumber);
    assertNotNull(mfcosdrPayload.getContainers());
    assertEquals(mfcosdrPayload.getContainers().size(), 2);
    for (MFCOSDRContainer mfcosdrContainer : mfcosdrPayload.getContainers()) {
      MFCOSDRItem containerItem;
      switch (mfcosdrContainer.getTrackingId()) {
        case "100000000000000316":
          assertEquals(mfcosdrContainer.getType(), PalletType.MFC.name());
          assertEquals(mfcosdrContainer.getOperationType(), OperationType.OVERAGE.name());
          assertNotNull(mfcosdrContainer.getContent());
          assertEquals(mfcosdrContainer.getContent().size(), 1);
          containerItem = mfcosdrContainer.getContent().get(0);
          assertEquals(containerItem.getGtin(), "00078742351926");
          assertEquals(containerItem.getItemNumber().intValue(), 565209430);
          assertEquals(containerItem.getInvoiceLineNumber().intValue(), 3);
          assertEquals(containerItem.getInvoiceNumber(), "7000000078");
          assertEquals(containerItem.getVnpk().intValue(), 1);
          assertEquals(containerItem.getWhpk().intValue(), 1);
          assertEquals(containerItem.getReceipt().size(), 0);
          break;
        case "100000000000000306":
          assertEquals(mfcosdrContainer.getType(), PalletType.MFC.name());
          assertEquals(mfcosdrContainer.getOperationType(), OperationType.NORMAL.name());
          assertEquals(mfcosdrContainer.getContent().size(), 1);
          containerItem = mfcosdrContainer.getContent().get(0);
          assertEquals(containerItem.getGtin(), "00078742351926");
          assertEquals(containerItem.getItemNumber().intValue(), 565209430);
          assertEquals(containerItem.getInvoiceLineNumber().intValue(), 4);
          assertEquals(containerItem.getInvoiceNumber(), "7000000077");
          assertEquals(containerItem.getVnpk().intValue(), 1);
          assertEquals(containerItem.getWhpk().intValue(), 1);
          assertEquals(containerItem.getReceipt().size(), 0);
          break;
      }
    }
  }

  @Test
  public void testCreateOSDRPayloadWithWeightedItemsNoContainersReceived()
      throws ReceivingException {
    Long deliveryNumber = 550478600045777L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong())).thenReturn(null);
    when(receiptService.findByDeliveryNumber(anyLong())).thenReturn(null);
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(
            MFCTestUtils.getASNDocument(
                "src/test/resources/osdr/ASNDocumentWithWeightedItems.json"));
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(
            MFCTestUtils.getDeliveryDocument(
                "src/test/resources/osdr/DeliveryDocsWeightedItem.json"));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, "storePallets");
    assertNotNull(osdrPayload);
    assertEquals(osdrPayload.getDeliveryNumber(), deliveryNumber);
    assertNotNull(osdrPayload.getSummary());
    validatePurchaseOrderDetails(osdrPayload);
    validateContainerDetails(osdrPayload);
  }

  private void validateContainerDetails(OSDRPayload osdrPayload) {
    assertEquals(osdrPayload.getSummary().getContainers().size(), 3);
    Map<String, List<OSDRContainer>> groupedContainers =
        osdrPayload
            .getSummary()
            .getContainers()
            .stream()
            .collect(Collectors.groupingBy(OSDRContainer::getUomType));
    assertEquals(groupedContainers.keySet().size(), 2);
    assertEquals(groupedContainers.get(LB).size(), 2);
    assertEquals(groupedContainers.get(EACHES).size(), 1);

    OSDRContainer osdrContainer = groupedContainers.get(EACHES).get(0);
    assertNotNull(osdrContainer.getShortage());
    assertEquals(osdrContainer.getRcvdQty().intValue(), 0);
    assertEquals(osdrContainer.getDerivedRcvdQty(), 0.0);
    assertEquals(osdrContainer.getRcvdQtyUom(), EACHES);
    assertEquals(osdrContainer.getDerivedRcvdQtyUom(), EACHES);

    assertEquals(osdrContainer.getReceivedFor(), STORE);
    assertEquals(osdrContainer.getSscc(), "6071160707052123003");
    assertEquals(osdrContainer.getShortage().getQuantity().intValue(), 3);
    assertEquals(osdrContainer.getShortage().getUom(), EACHES);
    assertEquals(osdrContainer.getItems().size(), 1);
    OSDRItem item = osdrContainer.getItems().get(0);
    assertEquals(item.getDerivedRcvdQty().intValue(), 0);
    assertEquals(item.getDerivedRcvdQtyUom(), EACHES);
    assertEquals(item.getDerivedReportedQty(), 3.0);
    assertEquals(item.getDerivedReportedQtyUom(), EACHES);
    assertEquals(item.getReportedQty().intValue(), 3);
    assertEquals(item.getDerivedRcvdQtyUom(), EACHES);
    assertEquals(item.getShortage().getQuantity(), 3.0);
    assertEquals(item.getShortage().getUom(), EACHES);
    assertEquals(item.getShortage().getDerivedQuantity(), item.getShortage().getQuantity());
    assertEquals(item.getShortage().getDerivedUom(), item.getShortage().getUom());

    groupedContainers
        .get(LB)
        .forEach(
            container -> {
              if (container.getSscc().equalsIgnoreCase("6071160707052123003")) {
                OSDRItem _item = container.getItems().get(0);
                assertEquals(container.getReceivedFor(), STORE);
                assertEquals(container.getRcvdQty().intValue(), 0);
                assertEquals(container.getDerivedRcvdQty(), 0.0);
                assertEquals(container.getRcvdQtyUom(), EACHES);
                assertEquals(container.getDerivedRcvdQtyUom(), LB);
                assertEquals(_item.getDerivedRcvdQty(), 0.0);
                assertEquals(_item.getDerivedRcvdQtyUom(), LB);
                assertEquals(_item.getRcvdQty().intValue(), 0);
                assertEquals(_item.getRcvdQtyUom(), EACHES);
                assertEquals(_item.getDerivedReportedQty(), 7.4);
                assertEquals(_item.getDerivedReportedQtyUom(), LB);
                assertEquals(_item.getReportedQty().intValue(), 8);
                assertEquals(_item.getReportedQtyUom(), EACHES);
                assertEquals(_item.getShortage().getQuantity(), 7.4);
                assertEquals(_item.getShortage().getUom(), LB);
                assertEquals(
                    _item.getShortage().getDerivedQuantity(), _item.getShortage().getQuantity());
                assertEquals(_item.getShortage().getDerivedUom(), _item.getShortage().getUom());
              } else {
                OSDRItem _item = container.getItems().get(0);
                assertEquals(container.getReceivedFor(), MFC);
                assertEquals(_item.getDerivedRcvdQty(), 0.0);
                assertEquals(_item.getDerivedRcvdQtyUom(), LB);
                assertEquals(_item.getRcvdQty().intValue(), 0);
                assertEquals(_item.getRcvdQtyUom(), CENTI_LB);
                assertEquals(_item.getDerivedReportedQty(), 6.6);
                assertEquals(_item.getDerivedReportedQtyUom(), LB);
                assertEquals(_item.getReportedQty().intValue(), 660);
                assertEquals(_item.getReportedQtyUom(), CENTI_LB);
                assertEquals(_item.getShortage().getQuantity(), 6.6);
                assertEquals(_item.getShortage().getUom(), LB);
                assertEquals(
                    _item.getShortage().getDerivedQuantity(), _item.getShortage().getQuantity());
                assertEquals(_item.getShortage().getDerivedUom(), _item.getShortage().getUom());
              }
            });
  }

  private void validatePurchaseOrderDetails(OSDRPayload osdrPayload) {

    assertEquals(osdrPayload.getSummary().getPurchaseOrders().size(), 2);
    for (OSDRPurchaseOrder purchaseOrder : osdrPayload.getSummary().getPurchaseOrders()) {
      if (purchaseOrder.getUomType().equalsIgnoreCase(EACHES)) {
        assertNotNull(purchaseOrder.getShortage());
        assertEquals(purchaseOrder.getShortage().getQuantity(), 11.0);
        assertEquals(purchaseOrder.getShortage().getUom(), EACHES);
        assertEquals(purchaseOrder.getLines().size(), 2);

        for (OSDRLine line : purchaseOrder.getLines()) {
          if (line.getLineNumber() == 1) {
            assertEquals(line.getDerivedRcvdQty().intValue(), 0);
            assertEquals(line.getDerivedRcvdQtyUom(), EACHES);
            assertEquals(line.getShortage().getQuantity().intValue(), 8);
            assertEquals(line.getShortage().getUom(), EACHES);
            assertEquals(
                line.getShortage().getDerivedQuantity().intValue(),
                line.getShortage().getQuantity().intValue());
            assertEquals(line.getShortage().getDerivedUom(), line.getShortage().getUom());
          } else {
            assertEquals(line.getDerivedRcvdQty(), 0.0);
            assertEquals(line.getDerivedRcvdQtyUom(), EACHES);
            assertEquals(line.getRcvdQty().intValue(), 0);
            assertEquals(line.getRcvdQtyUom(), EACHES);
            assertEquals(line.getDerivedRcvdQtyUom(), EACHES);
            assertEquals(line.getShortage().getQuantity().intValue(), 3);
            assertEquals(line.getShortage().getUom(), EACHES);
          }
        }
      } else if (purchaseOrder.getUomType().equalsIgnoreCase(LB)) {
        assertNotNull(purchaseOrder.getShortage());
        assertEquals(purchaseOrder.getShortage().getQuantity(), 6.6);
        assertEquals(purchaseOrder.getShortage().getUom(), LB);
        assertEquals(purchaseOrder.getLines().size(), 1);

        OSDRLine line = purchaseOrder.getLines().get(0);
        assertEquals(line.getDerivedRcvdQty(), 0.0);
        assertEquals(line.getDerivedRcvdQtyUom(), LB);
        assertEquals(line.getRcvdQty().intValue(), 0);
        assertEquals(line.getRcvdQtyUom(), CENTI_LB);
        assertEquals(line.getDerivedRcvdQtyUom(), LB);
        assertEquals(line.getShortage().getQuantity(), 6.6);
        assertEquals(line.getShortage().getUom(), LB);
      }
    }
  }

  @Test
  public void testCreateOSDRPayloadWithWeightedItemsAndReceipts() throws ReceivingException {
    Long deliveryNumber = 550478600045777L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(
            MFCTestUtils.getContainers("src/test/resources/osdr/MFCContainersWeightedItem.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(
            MFCTestUtils.getReceipts("src/test/resources/osdr/MFCReceiptsWeightedItems.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(
            MFCTestUtils.getASNDocument(
                "src/test/resources/osdr/ASNDocumentWithWeightedItems.json"));
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(
            MFCTestUtils.getDeliveryDocument(
                "src/test/resources/osdr/DeliveryDocsWeightedItem.json"));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, "storePallets");
    assertNotNull(osdrPayload);
    assertEquals(osdrPayload.getDeliveryNumber(), deliveryNumber);
    assertNotNull(osdrPayload.getSummary());
    validatePurchaseOrderDetailsWithReceipt(osdrPayload);
    validateContainerDetailsWithReceipt(osdrPayload);
  }

  private void validateContainerDetailsWithReceipt(OSDRPayload osdrPayload) {
    assertEquals(osdrPayload.getSummary().getContainers().size(), 2);
    Map<String, List<OSDRContainer>> groupedContainers =
        osdrPayload
            .getSummary()
            .getContainers()
            .stream()
            .collect(Collectors.groupingBy(OSDRContainer::getUomType));
    assertEquals(groupedContainers.keySet().size(), 2);
    assertEquals(groupedContainers.get(LB).size(), 1);
    assertEquals(groupedContainers.get(EACHES).size(), 1);

    OSDRContainer mfcOSDRContainer = groupedContainers.get(LB).get(0);
    OSDRItem item = mfcOSDRContainer.getItems().get(0);
    assertEquals(mfcOSDRContainer.getReceivedFor(), MFC);
    assertEquals(mfcOSDRContainer.getRcvdQty().intValue(), 0);
    assertEquals(mfcOSDRContainer.getDerivedRcvdQty(), 0.0);
    assertEquals(mfcOSDRContainer.getRcvdQtyUom(), CENTI_LB);
    assertEquals(mfcOSDRContainer.getDerivedRcvdQtyUom(), LB);

    assertEquals(item.getDerivedRcvdQty(), 0.0);
    assertEquals(item.getDerivedRcvdQtyUom(), LB);
    assertEquals(item.getRcvdQty().intValue(), 0);
    assertEquals(item.getRcvdQtyUom(), CENTI_LB);
    assertEquals(item.getDerivedReportedQty(), 6.6);
    assertEquals(item.getDerivedReportedQtyUom(), LB);
    assertEquals(item.getReportedQty().intValue(), 660);
    assertEquals(item.getReportedQtyUom(), CENTI_LB);
    assertEquals(item.getShortage().getQuantity(), 6.6);
    assertEquals(item.getShortage().getUom(), LB);
    assertEquals(mfcOSDRContainer.getShortage().getQuantity(), 6.6);
    assertEquals(mfcOSDRContainer.getShortage().getUom(), LB);
    assertEquals(
        mfcOSDRContainer.getShortage().getDerivedQuantity(),
        mfcOSDRContainer.getShortage().getQuantity());
    assertEquals(
        mfcOSDRContainer.getShortage().getDerivedUom(), mfcOSDRContainer.getShortage().getUom());
    assertEquals(mfcOSDRContainer.getShortage().getUom(), LB);

    OSDRContainer storeOSDRContainer = groupedContainers.get(EACHES).get(0);
    assertEquals(storeOSDRContainer.getSscc(), "6071160707052123003");
    assertEquals(storeOSDRContainer.getReceivedFor(), STORE);
    assertEquals(storeOSDRContainer.getRcvdQty().intValue(), 8);
    assertEquals(storeOSDRContainer.getRcvdQtyUom(), EACHES);
    assertEquals(storeOSDRContainer.getDerivedRcvdQty(), 8.0);
    assertEquals(storeOSDRContainer.getDerivedRcvdQtyUom(), EACHES);
    for (OSDRItem _item : storeOSDRContainer.getItems()) {
      if (_item.getInvoiceLineNumber().equals("1")) {
        assertEquals(_item.getDerivedRcvdQty(), 7.0);
        assertEquals(_item.getDerivedRcvdQtyUom(), EACHES);
        assertEquals(_item.getRcvdQty().intValue(), 7);
        assertEquals(_item.getRcvdQtyUom(), EACHES);
        assertEquals(_item.getDerivedReportedQty(), 8.0);
        assertEquals(_item.getDerivedReportedQtyUom(), EACHES);
        assertEquals(_item.getReportedQty().intValue(), 8);
        assertEquals(_item.getReportedQtyUom(), EACHES);
        assertEquals(_item.getShortage().getQuantity(), 1.0);
        assertEquals(_item.getShortage().getUom(), EACHES);
      } else {
        assertEquals(_item.getDerivedRcvdQty().intValue(), 1);
        assertEquals(_item.getDerivedRcvdQtyUom(), EACHES);
        assertEquals(_item.getDerivedReportedQty(), 3.0);
        assertEquals(_item.getDerivedRcvdQtyUom(), EACHES);
        assertEquals(_item.getReportedQty().intValue(), 3);
        assertEquals(_item.getDerivedRcvdQtyUom(), EACHES);
        assertEquals(_item.getShortage().getQuantity(), 2.0);
        assertEquals(_item.getShortage().getUom(), EACHES);
      }
    }
  }

  private void validatePurchaseOrderDetailsWithReceipt(OSDRPayload osdrPayload) {

    assertEquals(osdrPayload.getSummary().getPurchaseOrders().size(), 2);
    for (OSDRPurchaseOrder purchaseOrder : osdrPayload.getSummary().getPurchaseOrders()) {
      if (purchaseOrder.getUomType().equalsIgnoreCase(EACHES)) {
        assertNotNull(purchaseOrder.getShortage());
        assertEquals(purchaseOrder.getShortage().getQuantity(), 3.0);
        assertEquals(purchaseOrder.getShortage().getUom(), EACHES);
        assertEquals(purchaseOrder.getLines().size(), 2);

        for (OSDRLine line : purchaseOrder.getLines()) {
          if (line.getLineNumber() == 1) {
            assertEquals(line.getDerivedRcvdQty().intValue(), 7);
            assertEquals(line.getDerivedRcvdQtyUom(), EACHES);
            assertEquals(line.getShortage().getQuantity().intValue(), 1);
            assertEquals(line.getShortage().getUom(), EACHES);
            assertEquals(
                line.getShortage().getDerivedQuantity().intValue(),
                line.getShortage().getQuantity().intValue());
            assertEquals(line.getShortage().getDerivedUom(), line.getShortage().getUom());
          } else {
            assertEquals(line.getDerivedRcvdQty(), 1.0);
            assertEquals(line.getDerivedRcvdQtyUom(), EACHES);
            assertEquals(line.getRcvdQty().intValue(), 1);
            assertEquals(line.getRcvdQtyUom(), EACHES);
            assertEquals(line.getDerivedRcvdQtyUom(), EACHES);
            assertEquals(line.getShortage().getQuantity(), 2.0);
            assertEquals(line.getShortage().getUom(), EACHES);
          }
        }
      } else if (purchaseOrder.getUomType().equalsIgnoreCase(LB)) {
        assertNotNull(purchaseOrder.getShortage());
        assertEquals(purchaseOrder.getShortage().getQuantity(), 6.6);
        assertEquals(purchaseOrder.getShortage().getUom(), LB);
        assertEquals(purchaseOrder.getLines().size(), 1);

        OSDRLine line = purchaseOrder.getLines().get(0);
        assertEquals(line.getDerivedRcvdQty(), 0.0);
        assertEquals(line.getDerivedRcvdQtyUom(), LB);
        assertEquals(line.getRcvdQty().intValue(), 0);
        assertEquals(line.getRcvdQtyUom(), CENTI_LB);
        assertEquals(line.getDerivedRcvdQtyUom(), LB);
        assertEquals(line.getShortage().getQuantity(), 6.6);
        assertEquals(line.getShortage().getUom(), LB);
      }
    }
  }

  @Test
  public void testCreateOSDRv2Payload_DSDDelivery() throws ReceivingException {
    Long deliveryNumber = 5504021700L;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getContainers("src/test/resources/osdr/MFCDsdContainers.json"));
    when(receiptService.findByDeliveryNumber(anyLong()))
        .thenReturn(MFCTestUtils.getReceipts("src/test/resources/osdr/MFCDsdReceipts.json"));
    when(deliveryService.getGDMData(anyString(), anyString()))
        .thenReturn(MFCTestUtils.getASNDocument("src/test/resources/osdr/MfcDsdASN.json"));

    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(
            MFCTestUtils.getDeliveryDocument("src/test/resources/osdr/DsdDeliveryDocs2.json"));

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(false);
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetadata(DeliveryStatus.OPEN));

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);

    verify(deliveryService, times(1)).getGDMData(anyString(), anyString());

    List<OSDRContainer> containers = osdrPayload.getSummary().getContainers();
    List<OSDRPurchaseOrder> purchaseOrders = osdrPayload.getSummary().getPurchaseOrders();

    assertEquals(containers.size(), 2);
    assertEquals(purchaseOrders.size(), 2);
    Set<String> osdrContainers =
        containers.stream().map(OSDRContainer::getTrackingId).collect(Collectors.toSet());
    assertTrue(!osdrContainers.contains("100000000000000314"));
    containers.forEach(
        container -> {
          switch (container.getSscc()) {
            case "100000000000000306":
              assertNotNull(container.getShortage());
              assertEquals(container.getShortage().getQuantity().intValue(), 1);
              break;
            case "1659691641700":
              assertEquals(container.getReportedQty().intValue(), 2);
              assertEquals(container.getRcvdQty().intValue(), 1);
              assertEquals(container.getShortage().getQuantity().intValue(), 1);
              break;
          }
        });
    purchaseOrders.forEach(
        purchaseOrder -> {
          switch (purchaseOrder.getInvoiceNumber()) {
            case "7000000078":
              assertEquals(purchaseOrder.getReportedQty().intValue(), 2);
              assertEquals(purchaseOrder.getRcvdQty().intValue(), 1);
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 1);
              break;
            case "7000000077":
              assertNotNull(purchaseOrder.getShortage());
              assertEquals(purchaseOrder.getShortage().getQuantity().intValue(), 6);
              break;
          }
        });
  }
}
