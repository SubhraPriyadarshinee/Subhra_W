package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.ProblemReceivingServiceHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.*;

@Slf4j
@Service
public class ProblemPalletCreateInstructionService extends DefaultProblemReceivingCreateInstructionService {
    @Resource
    private RxInstructionService rxInstructionService;
    @Autowired
    private RxInstructionHelperService rxInstructionHelperService;
    @Autowired
    protected Gson gson;
    @Autowired
    ProblemReceivingServiceHelper problemReceivingServiceHelper;

    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException {

        FitProblemTagResponse fitProblemTagResponse = problemReceivingServiceHelper.fetchFitResponseForProblemTagId(instructionRequest);

        if(Objects.nonNull(fitProblemTagResponse)){
            problemReceivingServiceHelper.validateFitProblemResponse(instructionRequest,dataHolder.getDeliveryDocumentLine(),fitProblemTagResponse);
        }

        Instruction instruction = null;
        log.info("Create Instruction request for Cases - {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        SsccScanResponse.Container gdmContainer = dataHolder.getContainer();
        DeliveryDocument deliveryDocument = dataHolder.getDeliveryDocument();
        DeliveryDocumentLine deliveryDocumentLine = dataHolder.getDeliveryDocumentLine();

        Optional<List<DeliveryDocument>> latestDeliveryDocuments =
                problemReceivingServiceHelper.checkForLatestShipments(
                        instructionRequest,
                        RxUtils.scannedDataMap(instructionRequest.getScannedDataList()),
                        httpHeaders);
        if (latestDeliveryDocuments.isPresent()) {
            deliveryDocument = latestDeliveryDocuments.get().get(0);
        }

        int unitCnt =  gdmContainer.getUnitCount().intValue();
        int fullCaseQty =
                deliveryDocumentLine.getVendorPack() / deliveryDocumentLine.getWarehousePack();
        int caseQty = unitCnt / fullCaseQty;

        int attpQty = ReceivingUtils.conversionToEaches(
                caseQty,
                ReceivingConstants.Uom.VNPK,
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());

        deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);
        deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());

        instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);
        instruction.setReceivingMethod(dataHolder.getReceivingFlow());
        if (StringUtils.isNotEmpty(instructionRequest.getSscc())) {
            instruction.setSsccNumber(instructionRequest.getSscc());
        }
        instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
        instruction.setInstructionMsg(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionMsg());
        instruction.setProblemTagId(instructionRequest.getProblemTagId());

        return constructAndPersistProblemReceivingInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders, fitProblemTagResponse);

    }

    @Override
    protected void calculateQuantitiesAndPersistIns(
            Instruction instruction,
            DeliveryDocumentLine deliveryDocumentLine,
            InstructionRequest instructionRequest,
            DeliveryDocument deliveryDocument,
            HttpHeaders httpHeaders,
            FitProblemTagResponse fitProblemTagResponse)
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
        int projectedReceiveQtyInEaches = 0;
        projectedReceiveQtyInEaches = problemReceivingServiceHelper
                .getProjectedReceivedQtyInEaches(fitProblemTagResponse,
                        instructionRequest,
                        Collections.singletonList(deliveryDocument),
                        deliveryDocumentLine
                );

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

