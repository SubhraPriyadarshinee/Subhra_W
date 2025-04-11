package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.ProblemReceivingServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Objects;

@Slf4j
@Service
public abstract class DefaultProblemReceivingCreateInstructionService extends DefaultBaseCreateInstructionService {

    @Autowired
    protected Gson gson;
    @Autowired
    InstructionFactory factory;
    @Autowired
    ProblemReceivingServiceHelper problemReceivingServiceHelper;
    @Autowired
    private RxInstructionHelperService rxInstructionHelperService;
    @Resource
    private RxInstructionService rxInstructionService;

    public abstract InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException;

    protected InstructionResponse constructAndPersistProblemReceivingInstruction(
            Instruction instruction,
            DeliveryDocument deliveryDocument,
            DeliveryDocumentLine deliveryDocumentLine,
            InstructionRequest instructionRequest,
            HttpHeaders httpHeaders,
            FitProblemTagResponse fitProblemTagResponse

    ) throws ReceivingException {
        InstructionResponse instructionResponse = new InstructionResponseImplNew();
        calculateQuantitiesAndPersistIns(instruction, deliveryDocumentLine, instructionRequest, deliveryDocument, httpHeaders, fitProblemTagResponse);
        convertQuantitiesAndConstructResponse(instruction, deliveryDocumentLine);
        instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
        instructionResponse.setInstruction(instruction);
        return instructionResponse;
    }

    protected void calculateQuantitiesAndPersistIns(
            Instruction instruction,
            DeliveryDocumentLine deliveryDocumentLine,
            InstructionRequest instructionRequest,
            DeliveryDocument deliveryDocument,
            HttpHeaders httpHeaders,
            FitProblemTagResponse fitProblemTagResponse)
            throws ReceivingException {

        String deliveryNumber = instructionRequest.getDeliveryNumber();

        Pair<Integer, Long> receivedQtyDetails =
                instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
                        instructionRequest.getProblemTagId(), deliveryDocument, deliveryNumber);

        long totalReceivedQtyInEaches = receivedQtyDetails.getValue();

        int totalOrderQuantityInEaches = deliveryDocumentLine.getTotalOrderQty();
        deliveryDocumentLine.setOpenQty(totalOrderQuantityInEaches - (int) totalReceivedQtyInEaches);
        deliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.EACHES);

        deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
        if (Objects.isNull(gson)) gson = new Gson();
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        int projectedReceiveQtyInEaches = 0;
        projectedReceiveQtyInEaches = problemReceivingServiceHelper
                .getProjectedReceivedQtyInEaches(fitProblemTagResponse,
                        instructionRequest,
                        Collections.singletonList(deliveryDocument),
                        deliveryDocumentLine
                );

        validateProjectedReceivedQuantity(instructionRequest, httpHeaders, deliveryDocumentLine, projectedReceiveQtyInEaches, totalReceivedQtyInEaches);

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