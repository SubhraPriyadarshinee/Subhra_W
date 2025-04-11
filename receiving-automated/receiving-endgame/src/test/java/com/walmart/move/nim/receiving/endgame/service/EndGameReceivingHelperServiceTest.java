package com.walmart.move.nim.receiving.endgame.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.model.EndgameReceivingRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

public class EndGameReceivingHelperServiceTest extends ReceivingTestBase {

    private Gson gson;
    @InjectMocks private EndGameReceivingHelperService endGameReceivingHelperService;
    private Transformer<Container, ContainerDTO> transformer;

    @Mock private EndGameDeliveryService endGameDeliveryService;
    @Mock private EndgameContainerService endgameContainerService;
    @Mock private DCFinServiceV2 dcFinServiceV2;
    @Mock private ContainerPersisterService containerPersisterService;
    @Mock private ContainerService containerService;
    @Mock private InventoryService inventoryService;

    private EndgameManagedConfig endgameManagedConfig;

    @Mock private DeliveryMetaDataService deliveryMetaDataService;

    @BeforeClass
    public void setRootUp() {
        gson = new Gson();
        endgameManagedConfig = new EndgameManagedConfig();
        ReflectionTestUtils.setField(endgameManagedConfig, "nosUPCForBulkScan", 1);
        ReflectionTestUtils.setField(endgameManagedConfig, "printerFormatName", "rcv_tpl_eg_zeb");
        ReflectionTestUtils.setField(
                endgameManagedConfig, "walmartDefaultSellerId", "F55CDC31AB754BB68FE0B39041159D63");
        ReflectionTestUtils.setField(endgameManagedConfig, "samsDefaultSellerId", "0");

        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(endGameReceivingHelperService, "gson", gson);
        ReflectionTestUtils.setField(
                endGameReceivingHelperService, "endgameManagedConfig", endgameManagedConfig);
        ReflectionTestUtils.setField(
                endGameReceivingHelperService, "endGameDeliveryService", endGameDeliveryService);
        TenantContext.setFacilityNum(54321);
        TenantContext.setFacilityCountryCode("us");
        TenantContext.setCorrelationId("abc");
        this.transformer = new ContainerTransformer();
        ReflectionTestUtils.setField(endGameReceivingHelperService, "transformer", transformer);
    }

    @AfterMethod
    public void resetMocks() {
        reset(endGameDeliveryService);
        reset(endgameContainerService);
        reset(dcFinServiceV2);
        reset(containerService);
        reset(containerPersisterService);
        reset(deliveryMetaDataService);
        reset(inventoryService);
    }

    @Test
    public void testCreateMultipleContainersOutbox() {
        List<Receipt> receipts = new ArrayList<>();
        List<Container> containers = new ArrayList<>();
        containers.add(new Container());
        List<ContainerItem> containerItems = new ArrayList<>();
        List<ContainerDTO> containerDTOs = new ArrayList<>();
        String docType = "testDocType";

        doNothing().when(containerPersisterService).createMultipleReceiptAndContainer(receipts, containers, containerItems);
        doNothing().when(inventoryService).createContainersThroughOutbox(containerDTOs);
        doNothing().when(containerService).publishMultipleContainersToInventory(containerDTOs);
        doNothing()
                .when(dcFinServiceV2)
                .postReceiptUpdateToDCFin(
                        anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());

        endGameReceivingHelperService.createMultipleContainersOutbox(receipts, containers, containerItems, containerDTOs, docType);

        verify(containerPersisterService, times(1)).createMultipleReceiptAndContainer(receipts, containers, containerItems);
        verify(inventoryService, times(1)).createContainersThroughOutbox(containerDTOs);
        verify(containerService, times(1)).publishMultipleContainersToInventory(containerDTOs);
    }

    @Test
    public void testCreateAndSaveContainerAndReceiptOutbox() {
        ScanEventData scanEventData = new ScanEventData();
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
        int eachQuantity = 10;
        Container container = new Container();

        Transformer<Container, ContainerDTO> transformer = Mockito.mock(Transformer.class);
        ReflectionTestUtils.setField(endGameReceivingHelperService, "transformer", transformer);
        when(endgameContainerService.createAndSaveContainerAndReceipt(scanEventData,
                purchaseOrder, purchaseOrderLine, eachQuantity, container)).thenReturn(new Container());
        doNothing().when(inventoryService).createContainersThroughOutbox(anyList());
        doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
        when(transformer.transform(container)).thenReturn(new ContainerDTO());
        when(transformer.transformList(Collections.singletonList(container))).thenReturn(new ArrayList<>());
        doNothing()
                .when(dcFinServiceV2)
                .postReceiptUpdateToDCFin(
                        anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());

        endGameReceivingHelperService.createAndSaveContainerAndReceiptOutbox(scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);

        verify(endgameContainerService, times(1)).createAndSaveContainerAndReceipt(scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);
        verify(inventoryService, times(1)).createContainersThroughOutbox(anyList());
        verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
        ReflectionTestUtils.setField(endGameReceivingHelperService, "transformer", this.transformer);

    }

    @Test
    public void testCreateReceiptAndContainerOutbox() {
        EndgameReceivingRequest receivingRequest = new EndgameReceivingRequest();
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
        Container container = new Container();

        Transformer<Container, ContainerDTO> transformer = Mockito.mock(Transformer.class);
        ReflectionTestUtils.setField(endGameReceivingHelperService, "transformer", transformer);
        when(endgameContainerService.getContainer(receivingRequest, purchaseOrder, purchaseOrderLine)).thenReturn(container);
        when(endgameContainerService.createAndSaveContainerAndReceipt(
                receivingRequest, purchaseOrder, purchaseOrderLine, container)).thenReturn(new Container());
        doNothing().when(inventoryService).createContainersThroughOutbox(anyList());
        when(transformer.transform(container)).thenReturn(new ContainerDTO());
        when(transformer.transformList(Collections.singletonList(container))).thenReturn(new ArrayList<>());
        doNothing()
                .when(dcFinServiceV2)
                .postReceiptUpdateToDCFin(
                        anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());

        Container result = endGameReceivingHelperService.createReceiptAndContainerOutbox(receivingRequest, purchaseOrder, purchaseOrderLine);

        assertEquals(container, result);
        verify(endgameContainerService, times(1)).getContainer(receivingRequest, purchaseOrder, purchaseOrderLine);
        verify(endgameContainerService, times(1)).createAndSaveContainerAndReceipt(receivingRequest, purchaseOrder, purchaseOrderLine, container);
        verify(inventoryService, times(1)).createContainersThroughOutbox(anyList());
        ReflectionTestUtils.setField(endGameReceivingHelperService, "transformer", this.transformer);
    }
}
