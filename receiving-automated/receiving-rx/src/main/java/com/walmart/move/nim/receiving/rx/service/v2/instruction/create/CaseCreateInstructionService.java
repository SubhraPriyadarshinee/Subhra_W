package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;

@Slf4j
@Service
public class CaseCreateInstructionService extends DefaultBaseCreateInstructionService {

    @Resource
    private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;

    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException {

        Instruction instruction = null;

        log.info("Create Instruction request for Cases - {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        SsccScanResponse.Container gdmContainer = dataHolder.getContainer();
        DeliveryDocument deliveryDocument = dataHolder.getDeliveryDocument();
        DeliveryDocumentLine deliveryDocumentLine = dataHolder.getDeliveryDocumentLine();

        List<SsccScanResponse.Container> currentAndSiblingsContainers =
                getCurrentAndSiblings(instructionRequest, gdmContainer, httpHeaders);

        // result containers are having only CASE_PACK_ITEM
        if (!currentAndSiblingsContainers.isEmpty()) {
            int attpQty = ReceivingUtils.conversionToEaches(
                    currentAndSiblingsContainers.size(),
                    VNPK,
                    deliveryDocumentLine.getVendorPack(),
                    deliveryDocumentLine.getWarehousePack());
            deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);
            
            //setting palletSSCC only for cases which are having parent
            if (!dataHolder.getReceivingFlow().equalsIgnoreCase(RxConstants.ReceivingTypes.FLOOR_LOADED_CASE)) {
                deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());
            }

            if (Objects.nonNull(gdmContainer.getSerial())
            && Objects.nonNull(gdmContainer.getGtin())) {
                //Setting the serial of Scanned-2D to be used in UI
                deliveryDocumentLine.getAdditionalInfo().setSgtinScannedSerial(
                        gdmContainer.getSerial());
                deliveryDocumentLine.setGtin(gdmContainer.getGtin());
            }
            deliveryDocumentLine.getAdditionalInfo().setScannedCaseAttpQty(
                    deliveryDocumentLine.getVendorPack());

            instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

            instruction.setReceivingMethod(dataHolder.getReceivingFlow());

            if (Objects.nonNull(instructionRequest.getSscc())) {
                instruction.setInstructionCode(RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType());
                instruction.setInstructionMsg(RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionMsg());
                instruction.setSsccNumber(instructionRequest.getSscc());
            } else {
                instruction.setInstructionCode(RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT.getInstructionType());
                instruction.setInstructionMsg(RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT.getInstructionMsg());
            }

            return constructAndPersistEPCISInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.GDM_NOT_ACCESSIBLE,
                    ExceptionDescriptionConstants.GDM_BAD_DATA_ERROR_MSG);
        }
    }

    @Override
    public void validateProjectedReceivedQuantity(InstructionRequest instructionRequest, HttpHeaders httpHeaders, DeliveryDocumentLine deliveryDocumentLine,
                                                  int projectedReceiveQtyInEaches, long totalReceivedQtyInEaches) throws ReceivingException {

        if (projectedReceiveQtyInEaches <= 0) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.CREATE_INSTRUCTION_ERROR_NO_OPEN_QTY,
                    RxConstants.NO_OPEN_QTY);
        }

        if (projectedReceiveQtyInEaches < deliveryDocumentLine.getVendorPack()){
            throw new ReceivingException(
                    ReceivingException.HINT_PERFORM_UNIT_RCV, HttpStatus.BAD_REQUEST);
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
    }


}
