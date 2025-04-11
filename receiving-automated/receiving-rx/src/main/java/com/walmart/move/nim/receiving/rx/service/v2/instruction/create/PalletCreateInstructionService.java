package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.InstructionSetIdGenerator;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Objects;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;

@Slf4j
@Service
public class PalletCreateInstructionService extends DefaultBaseCreateInstructionService {

    @Resource
    private CreateInstructionServiceHelper createInstructionServiceHelper;
    @Autowired
    private InstructionSetIdGenerator instructionSetIdGenerator;
    @Resource
    private CreateInstructionDataValidator createInstructionDataValidator;


    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException {

        Instruction instruction = null;

        log.info("Create Instruction request for Pallet - {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        SsccScanResponse.Container gdmContainer = dataHolder.getContainer();
        DeliveryDocument deliveryDocument = dataHolder.getDeliveryDocument();
        DeliveryDocumentLine deliveryDocumentLine = dataHolder.getDeliveryDocumentLine();
        int unitCnt = gdmContainer.getUnitCount().intValue();
        int fullCaseQty =
                deliveryDocumentLine.getVendorPack() / deliveryDocumentLine.getWarehousePack();
        int caseQty = unitCnt / fullCaseQty;

        int attpQty =
                ReceivingUtils.conversionToEaches(
                        caseQty,
                        VNPK,
                        deliveryDocumentLine.getVendorPack(),
                        deliveryDocumentLine.getWarehousePack());
        deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);
        deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());


        instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

        instruction.setReceivingMethod(dataHolder.getReceivingFlow());

        instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
        instruction.setInstructionMsg(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionMsg());
        instruction.setSsccNumber(instructionRequest.getSscc());

        return constructAndPersistEPCISInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);
    }

    @Override
    protected void calculateQuantitiesAndPersistIns(
            Instruction instruction,
            DeliveryDocumentLine deliveryDocumentLine,
            InstructionRequest instructionRequest,
            DeliveryDocument deliveryDocument,
            HttpHeaders httpHeaders)
            throws ReceivingException {

        String deliveryNumber = instructionRequest.getDeliveryNumber();

        int attpEpcisQtyInEaches = deliveryDocumentLine.getAdditionalInfo().getAttpQtyInEaches();
        Pair<Integer, Long> receivedQtyDetails =
                instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
                        instructionRequest.getProblemTagId(), deliveryDocument, deliveryNumber);

        long totalReceivedQtyInEaches = receivedQtyDetails.getValue();

        int totalOrderQuantityInEaches = deliveryDocumentLine.getTotalOrderQty();
        deliveryDocumentLine.setOpenQty(totalOrderQuantityInEaches - (int) totalReceivedQtyInEaches);
        deliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.EACHES);

        deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        int projectedReceiveQtyInEaches =
                RxUtils.deriveProjectedReceiveQtyInEachesForEpcisEnabledFlow(
                        deliveryDocumentLine, totalReceivedQtyInEaches, attpEpcisQtyInEaches);

        if (projectedReceiveQtyInEaches < attpEpcisQtyInEaches) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.BARCODE_ALREADY_RECEIVED,
                    ReceivingException.BARCODE_UNABLE_TO_SCAN);
        }

        if (projectedReceiveQtyInEaches <= 0) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.CREATE_INSTRUCTION_ERROR_NO_OPEN_QTY,
                    RxConstants.NO_OPEN_QTY);
        }

        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

        createInstructionDataValidator.isNewInstructionCanBeCreated(
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                projectedReceiveQtyInEaches,
                totalReceivedQtyInEaches,
                StringUtils.isNotBlank(instructionRequest.getProblemTagId()),
                RxUtils.isSplitPalletInstructionRequest(instructionRequest),
                userId);

        instruction.setProjectedReceiveQty(projectedReceiveQtyInEaches);
        instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
        instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);

        RxReceivingType receivingType = createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest);
        if (receivingType.isSplitPalletGroup()) {
            if (Objects.isNull(instructionRequest.getInstructionSetId())) {
                instruction.setInstructionSetId(instructionSetIdGenerator.generateInstructionSetId());
            } else {
                instruction.setInstructionSetId(instructionRequest.getInstructionSetId());
            }
        }
        instructionPersisterService.saveInstruction(instruction);
        log.info("Instruction id {} created for quantities {} (in eaches) - {}", instruction.getId(), projectedReceiveQtyInEaches, httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    }
}
