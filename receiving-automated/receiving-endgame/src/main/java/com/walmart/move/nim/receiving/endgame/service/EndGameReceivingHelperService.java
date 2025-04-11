package com.walmart.move.nim.receiving.endgame.service;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.model.EndgameReceivingRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.Collections.singletonList;

public class EndGameReceivingHelperService extends EndGameReceivingService {

    @Transactional(rollbackFor = Exception.class)
    public void createMultipleContainersOutbox(
            List<Receipt> receipts,
            List<Container> containers,
            List<ContainerItem> containerItems,
            List<ContainerDTO> containerDTOs,
            String docType) {

        containerPersisterService.createMultipleReceiptAndContainer(
                receipts, containers, containerItems);
        inventoryService.createContainersThroughOutbox(containerDTOs);
        containerService.publishMultipleContainersToInventory(containerDTOs);
        postToDCFin(containers, docType, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createAndSaveContainerAndReceiptOutbox(
            ScanEventData scanEventData,
            PurchaseOrder purchaseOrder,
            PurchaseOrderLine purchaseOrderLine,
            int eachQuantity,
            Container container) {

        endgameContainerService.createAndSaveContainerAndReceipt(
                scanEventData, purchaseOrder, purchaseOrderLine, eachQuantity, container);
        inventoryService.createContainersThroughOutbox(
                transformer.transformList(singletonList(container)));
        containerService.publishMultipleContainersToInventory(
                singletonList(transformer.transform(container)));
        postToDCFin(singletonList(container), purchaseOrder.getLegacyType(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Container createReceiptAndContainerOutbox(
            EndgameReceivingRequest receivingRequest,
            PurchaseOrder purchaseOrder,
            PurchaseOrderLine purchaseOrderLine) {
        Container container =
                endgameContainerService.getContainer(receivingRequest, purchaseOrder, purchaseOrderLine);

        endgameContainerService.createAndSaveContainerAndReceipt(
                receivingRequest, purchaseOrder, purchaseOrderLine, container);
        inventoryService.createContainersThroughOutbox(
                transformer.transformList(singletonList(container)));
        postToDCFin(
                singletonList(container),
                purchaseOrder.getLegacyType(),
                receivingRequest.getDeliveryMetaData());

        return container;
    }
}
