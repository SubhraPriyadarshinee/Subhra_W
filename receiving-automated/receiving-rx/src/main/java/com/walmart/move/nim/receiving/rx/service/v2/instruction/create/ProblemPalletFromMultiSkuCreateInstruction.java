package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

import static com.walmart.move.nim.receiving.rx.common.RxUtils.isASNReceivingOverrideEligible;

@Slf4j
@Service
public class ProblemPalletFromMultiSkuCreateInstruction extends ProblemCaseCreateInstructionService{
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
        if(latestDeliveryDocuments.isPresent()) {
            deliveryDocument = latestDeliveryDocuments.get().get(0);
        }

        if (CollectionUtils.containsAny(gdmContainer.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)
                && RxReceivingType.MULTI_SKU_FLOW == createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest)) {
            throw new ReceivingException(ReceivingException.CASE_RECEIVING_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }

        List<SsccScanResponse.Container> currentAndSiblingsContainers =
                getCurrentAndSiblings(instructionRequest, gdmContainer, httpHeaders);

        if (!currentAndSiblingsContainers.isEmpty()) {
            int attpQty = ReceivingUtils.conversionToEaches(
                    currentAndSiblingsContainers.size(),
                    ReceivingConstants.Uom.VNPK,
                    deliveryDocumentLine.getVendorPack(),
                    deliveryDocumentLine.getWarehousePack());

            deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);

            if (!dataHolder.getReceivingFlow().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE)) {
                deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());
            } else {
                deliveryDocumentLine.getAdditionalInfo().setIsPalletInsForCaseRecv(Boolean.TRUE);
            }

            if (gdmContainer.getSerial() != null) {
                deliveryDocumentLine.getAdditionalInfo().setSgtinScannedSerial(gdmContainer.getSerial());
            }

            deliveryDocumentLine.getAdditionalInfo().setScannedCaseAttpQty(deliveryDocumentLine.getVendorPack());
            deliveryDocumentLine.getAdditionalInfo().setPalletFlowInMultiSku(true);

            instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);
            instruction.setReceivingMethod(dataHolder.getReceivingFlow());
            instruction.setSsccNumber(gdmContainer.getTopLevelContainerSscc());
            instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
            instruction.setInstructionMsg(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionMsg());
            instruction.setProblemTagId(instructionRequest.getProblemTagId());

            return constructAndPersistProblemReceivingInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders, fitProblemTagResponse);
        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.GDM_NOT_ACCESSIBLE,
                    ExceptionDescriptionConstants.GDM_BAD_DATA_ERROR_MSG);
        }
    }
}

