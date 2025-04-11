package com.walmart.move.nim.receiving.rx.service.v2.instruction.update;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.v2.ProcessInstructionService;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

import static io.strati.libs.reflections.Reflections.log;

public abstract class DefaultBaseUpdateInstructionService implements ProcessInstructionService {

    @Autowired
    UpdateInstructionServiceHelper updateInstructionServiceHelper;

    @Resource private RxDeliveryServiceImpl rxDeliveryServiceImpl;

    @Autowired private RxReceiptsBuilder rxReceiptsBuilder;

    @ManagedConfiguration private RxManagedConfig rxManagedConfig;

    @Autowired private ReceiptService receiptService;

    @Autowired private RxInstructionHelperService rxInstructionHelperService;

    private static final String DB_PROCESS_TIME_LOG_MESSAGE =
            "Time take to complete update instruction DB Tx is {} ms and correlation id is {}";


    @Autowired
    private Gson gson;

    @Autowired
    UpdateInstructionDataValidator updateInstructionDataValidator;

    @Override
    public InstructionResponse validateUserEnteredQty(UpdateInstructionRequest instructionUpdateRequestFromClient, Instruction instruction4mDB) throws ReceivingException {
        return null;
    }

    @Override
    public void processUpdateInstruction(UpdateInstructionRequest instructionUpdateRequestFromClient, DataHolder dataForUpdateInstruction, DeliveryDocument deliveryDocument4mDB, DeliveryDocumentLine deliveryDocumentLine4mDB, boolean setAuditQty, HttpHeaders httpHeaders) throws ReceivingException {
    }

    protected void buildReceipts(UpdateInstructionRequest instructionUpdateRequestFromClient, DataHolder dataForUpdateInstruction, HttpHeaders httpHeaders){
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        List<Receipt> receipts =
                rxReceiptsBuilder.buildReceipts(
                        dataForUpdateInstruction.getInstruction(),
                        instructionUpdateRequestFromClient,
                        userId,
                        dataForUpdateInstruction.getQuantityToBeReceivedInEaches(),
                        dataForUpdateInstruction.getQuantityToBeReceivedInVNPK(),
                        dataForUpdateInstruction.getGdmResponseForScannedData().getShipmentDocumentId());
        dataForUpdateInstruction.setReceipts(receipts);
    }

    @Transactional(readOnly = true)
    @Override
    public InstructionResponseImplNew buildContainerAndUpdateInstruction(UpdateInstructionRequest instructionUpdateRequestFromClient, DataHolder dataForUpdateInstruction, String parentTrackingId, HttpHeaders httpHeaders) {

        buildReceipts(instructionUpdateRequestFromClient, dataForUpdateInstruction, httpHeaders);
        if (Objects.isNull(gson))
            gson = new Gson();
        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        List<ScannedData> scannedDataList = instructionUpdateRequestFromClient.getScannedDataList();
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
        Instruction instruction = dataForUpdateInstruction.getInstruction();
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        DocumentLine documentLine =
                instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
        DeliveryDocument deliveryDocument =
                gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine =
                deliveryDocument.getDeliveryDocumentLines().get(0);
        instruction.setReceivedQuantity(
                instruction.getReceivedQuantity() + dataForUpdateInstruction.getQuantityToBeReceivedInEaches());

        String instructionPackageInfo = instruction.getInstructionCreatedByPackageInfo();
        SsccScanResponse.Container instructionPack = gson.fromJson(instructionPackageInfo, SsccScanResponse.Container.class);

        String scannedCaseTrackingId = updateInstructionServiceHelper.generateTrackingId(httpHeaders);

        updateInstructionServiceHelper.createPalletContainer(containers, containerItems, instruction,deliveryDocument,
                instructionUpdateRequestFromClient, httpHeaders, userId, instructionPack, documentLine,
                dataForUpdateInstruction.getQuantityToBeReceivedInEaches());

        String caseContainerTrackingId = getCaseContainerTrackingId(containers, containerItems, instruction, deliveryDocument,
                instructionUpdateRequestFromClient, httpHeaders, userId, instructionPack, documentLine,
                scannedDataMap, dataForUpdateInstruction.getQuantityToBeReceivedInEaches(), parentTrackingId);

        List<ContainerDetails> instructionChildContainers4mDB = instruction.getChildContainers();
        instructionChildContainers4mDB = CollectionUtils.isEmpty(instructionChildContainers4mDB)? new ArrayList<>() : instructionChildContainers4mDB;

        ContainerItem scannedContainerItem = updateInstructionServiceHelper.createScannedContainer(containers, containerItems, instruction, deliveryDocument,
                instructionUpdateRequestFromClient, userId, documentLine, scannedDataMap, dataForUpdateInstruction.getQuantityToBeReceivedInEaches(),
                caseContainerTrackingId, scannedCaseTrackingId, instructionChildContainers4mDB, dataForUpdateInstruction.getGdmResponseForScannedData());

        instruction.setChildContainers(instructionChildContainers4mDB);
        instruction.setLastChangeUserId(userId);
        instruction.setLastChangeTs(new Date());
        dataForUpdateInstruction.setInstruction(instruction);

        persistForUpdateInstruction(
                instruction,
                containers,
                containerItems,
                dataForUpdateInstruction.getReceipts(),
                scannedContainerItem.getSerial(),
                scannedContainerItem.getLotNumber());

        return frameResponse(dataForUpdateInstruction.getInstruction());
    }

    protected String getCaseContainerTrackingId(List<Container> containers, List<ContainerItem> containerItems, Instruction instruction, DeliveryDocument deliveryDocument,
                                                UpdateInstructionRequest instructionUpdateRequestFromClient, HttpHeaders httpHeaders, String userId,
                                                SsccScanResponse.Container instructionPack, DocumentLine documentLine, Map<String, ScannedData> scannedDataMap,
                                                Integer quantityToBeReceivedInEaches, String parentTrackingId) {
        return updateInstructionServiceHelper.createCaseContainer(containers, containerItems, instruction, deliveryDocument,
                instructionUpdateRequestFromClient, httpHeaders, userId, instructionPack, documentLine,
                scannedDataMap, quantityToBeReceivedInEaches, parentTrackingId);
    }
    protected void verifyIfCaseCanBeReceived(Map<String, ScannedData> scannedDataMap,
                                             DataHolder dataForUpdateInstruction) {
        Instruction instruction = dataForUpdateInstruction.getInstruction();
        if (Objects.isNull(gson)) gson = new Gson();
        DeliveryDocument document =
                gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine documentDocumentLine = document.getDeliveryDocumentLines().get(0);
        Long receiveQtySummary =
                receiptService.receivedQtyByDeliveryPoAndPoLineInEaches(
                        instruction.getDeliveryNumber(), instruction.getPurchaseReferenceNumber(), instruction.getPurchaseReferenceLineNumber());
        long totalReceivedQty = Objects.isNull(receiveQtySummary) ? 0 : receiveQtySummary;
        Integer totalQuantityValueAfterReceiving = updateInstructionServiceHelper.totalQuantityValueAfterReceiving(instruction.getReceivedQuantity(), dataForUpdateInstruction.getQuantityToBeReceivedInEaches());
        updateInstructionDataValidator.verifyIfCaseCanBeReceived(
                totalReceivedQty,
                instruction.getProjectedReceiveQty(),
                documentDocumentLine,
                totalQuantityValueAfterReceiving
        );

        Optional<FitProblemTagResponse> fitProblemTagResponseOptional =
                rxInstructionHelperService.getFitProblemTagResponse(dataForUpdateInstruction.getInstruction().getProblemTagId());

        if (fitProblemTagResponseOptional.isPresent()) {
            rxInstructionHelperService.checkIfContainerIsCloseDated(
                    fitProblemTagResponseOptional.get(), scannedDataMap);
        } else {
            rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);
        }
    }

    protected InstructionResponseImplNew frameResponse(Instruction instruction) {
        RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(instruction);
        return new InstructionResponseImplNew(null, null, instruction, null);
    }


    protected void persistForUpdateInstruction(
            Instruction instruction4mDB,
            List<Container> containers,
            List<ContainerItem> containerItems,
            List<Receipt> receipts,
            String serial,
            String lotNumber) {
        long startTimeOfDBTx = System.currentTimeMillis();
        try {
            rxInstructionHelperService.persistForUpdateInstruction(
                    instruction4mDB, receipts, containers, containerItems);
        } catch (ObjectOptimisticLockingFailureException e) {
            String errorCode = ExceptionCodes.INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR;
            String errorDescription = RxConstants.INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR;
            if (StringUtils.isNotBlank(serial) && StringUtils.isNotBlank(lotNumber)) {
                errorCode = ExceptionCodes.INSTR_OPTIMISTIC_LOCK_ERROR;
                errorDescription =
                        String.format(RxConstants.INSTR_OPTIMISTIC_LOCK_ERROR, serial, lotNumber);
            }
            throw new ReceivingConflictException(errorCode, errorDescription, serial, lotNumber);
        } finally {
            long timeTakenInMills =
                    ReceivingUtils.getTimeDifferenceInMillis(startTimeOfDBTx, System.currentTimeMillis());
            log.warn(DB_PROCESS_TIME_LOG_MESSAGE, timeTakenInMills, TenantContext.getCorrelationId());
        }
    }
}
