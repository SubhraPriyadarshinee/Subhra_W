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
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.ProblemReceivingServiceHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

import static com.walmart.move.nim.receiving.rx.common.RxUtils.isASNReceivingOverrideEligible;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WHPK;

@Slf4j
@Service
public class ProblemCaseCreateInstructionService extends DefaultProblemReceivingCreateInstructionService {
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

        if(Objects.nonNull(fitProblemTagResponse)) {
            problemReceivingServiceHelper.validateFitProblemResponse(instructionRequest,dataHolder.getDeliveryDocumentLine(),fitProblemTagResponse);
            String problemUom = fitProblemTagResponse.getIssue().getUom();
            if (!(problemUom.equalsIgnoreCase(ReceivingConstants.Uom.CA)
                    || problemUom.equalsIgnoreCase(ReceivingConstants.Uom.VNPK))) {
                throw new ReceivingBadDataException(
                        ExceptionCodes.FIXIT_NOT_FOUND,
                        RxConstants.INVALID_PROBLEM_RECEIVING);
            }
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

        List<SsccScanResponse.Container> currentAndSiblingsContainers = getCurrentAndSiblings(instructionRequest, gdmContainer, httpHeaders);

        int attpQty = ReceivingUtils.conversionToEaches(
                currentAndSiblingsContainers.size(),
                ReceivingConstants.Uom.VNPK,
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());

        deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);

        if (!dataHolder.getReceivingFlow().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_FLOOR_LOADED_CASE)) {
            deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());
        }

        if (gdmContainer.getSerial() != null) {
            deliveryDocumentLine.getAdditionalInfo().setSgtinScannedSerial(gdmContainer.getSerial());
        }

        deliveryDocumentLine.getAdditionalInfo().setScannedCaseAttpQty(deliveryDocumentLine.getVendorPack());

        instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);
        instruction.setReceivingMethod(dataHolder.getReceivingFlow());
        RxInstructionType rxInstructionType = null;
        if (Objects.nonNull(instructionRequest.getSscc())) {
            rxInstructionType = RxInstructionType.RX_SER_CNTR_CASE_SCAN;
        } else {
            rxInstructionType = RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT;
        }
        if (StringUtils.isNotEmpty(instructionRequest.getSscc())) {
            instruction.setSsccNumber(instructionRequest.getSscc());
        }
        instruction.setProblemTagId(instructionRequest.getProblemTagId());
        instruction.setInstructionCode(rxInstructionType.getInstructionType());
        instruction.setInstructionMsg(rxInstructionType.getInstructionMsg());

        return constructAndPersistProblemReceivingInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders, fitProblemTagResponse);
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

