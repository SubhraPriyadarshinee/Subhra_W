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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static com.walmart.move.nim.receiving.rx.common.RxUtils.isASNReceivingOverrideEligible;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.OPEN_ATTP_SERIALIZED_RECEIVING_STATUS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WHPK;

@Slf4j
@Service
public class ProblemPartialCaseCreateInstructionService extends DefaultProblemReceivingCreateInstructionService {
    @Resource
    private RxInstructionService rxInstructionService;
    @Autowired
    private RxInstructionHelperService rxInstructionHelperService;
    @Autowired
    protected Gson gson;
    @Autowired
    ProblemReceivingServiceHelper problemReceivingServiceHelper;
    @Resource
    private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;

    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException {

        FitProblemTagResponse fitProblemTagResponse = problemReceivingServiceHelper.fetchFitResponseForProblemTagId(instructionRequest);
        if(Objects.nonNull(fitProblemTagResponse)) {
            problemReceivingServiceHelper.validateFitProblemResponse(instructionRequest,dataHolder.getDeliveryDocumentLine(),fitProblemTagResponse);
            String problemUom = fitProblemTagResponse.getIssue().getUom();
            if (!problemUom.equalsIgnoreCase(ReceivingConstants.Uom.WHPK)) {
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

        if (CollectionUtils.containsAny(gdmContainer.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)) {
            throw new ReceivingException(ReceivingException.UNIT_RECEIVING_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }
        List<SsccScanResponse.Container> currentAndSiblingsContainers = extractUnitContainers(instructionRequest, httpHeaders, gdmContainer, deliveryDocumentLine);

        if (!currentAndSiblingsContainers.isEmpty()) {
            int attpQty = ReceivingUtils.conversionToEaches(
                    currentAndSiblingsContainers.size(),
                    ReceivingConstants.Uom.WHPK,
                    deliveryDocumentLine.getVendorPack(),
                    deliveryDocumentLine.getWarehousePack());

            deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);

            if (gdmContainer.getSerial() != null) {
                deliveryDocumentLine.getAdditionalInfo().setSgtinScannedSerial(gdmContainer.getSerial());
            }

            Resolution activeResolution = RxUtils.getActiveResolution(fitProblemTagResponse);
            if (Objects.nonNull(activeResolution)) {

                int problemQtyInEaches = ReceivingUtils.conversionToEaches(
                        activeResolution.getQuantity(),
                        ReceivingConstants.Uom.WHPK,
                        deliveryDocumentLine.getVendorPack(),
                        deliveryDocumentLine.getWarehousePack());

                deliveryDocumentLine
                        .getAdditionalInfo()
                        .setScannedCaseAttpQty(problemQtyInEaches);
            }
            deliveryDocumentLine.setGtin(gdmContainer.getGtin());

            instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

            instruction.setReceivingMethod(dataHolder.getReceivingFlow());
            if (StringUtils.isNotEmpty(instructionRequest.getSscc())) {
                instruction.setSsccNumber(instructionRequest.getSscc());
            }
            instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());
            instruction.setInstructionMsg(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionMsg());
            instruction.setProblemTagId(instructionRequest.getProblemTagId());

            return constructAndPersistProblemReceivingInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders, fitProblemTagResponse);

        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.GDM_NOT_ACCESSIBLE,
                    ExceptionDescriptionConstants.GDM_BAD_DATA_ERROR_MSG);
        }
    }

    private List<SsccScanResponse.Container> extractUnitContainers(InstructionRequest instructionRequest, HttpHeaders httpHeaders, SsccScanResponse.Container gdmContainer, DeliveryDocumentLine deliveryDocumentLine) {
        List<SsccScanResponse.Container> currentAndSiblingsContainers;
        List<SsccScanResponse.Container> responseContainers = Collections.singletonList(gdmContainer);
        String parentId = gdmContainer.getParentId();
        deliveryDocumentLine.getAdditionalInfo().setPalletOfCase(
                parentId);

        deliveryDocumentLine.getAdditionalInfo().setIsSerUnit2DScan(Boolean.TRUE);

        if (Objects.nonNull(parentId)) {
            deliveryDocumentLine.getAdditionalInfo().setPalletOfCase(
                    parentId);

            responseContainers =
                    rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(
                            instructionRequest, httpHeaders, parentId).getContainers();
        }

        ArrayList<SsccScanResponse.Container> unitItemContainers = new ArrayList<>();
        responseContainers
                .stream()
                .filter(
                        container -> {
                            if (CollectionUtils.containsAny(
                                    container.getHints(),
                                    RxConstants.GdmHints.UNIT_ITEM)
                                    && OPEN_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(container.getReceivingStatus())
                                    && container.getParentId().equalsIgnoreCase(parentId)) {
                                unitItemContainers.add(container);
                            }
                            return true;
                        })
                .collect(Collectors.toList());

        currentAndSiblingsContainers = unitItemContainers;
        return currentAndSiblingsContainers;
    }

    @Override
    protected void convertQuantitiesAndConstructResponse(
            Instruction instruction, DeliveryDocumentLine deliveryDocumentLine) {
        int qtyToBeRcvdInInstrResp = ReceivingUtils.conversionToWareHousePack(
                instruction.getProjectedReceiveQty(),
                ReceivingConstants.Uom.EACHES,
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());

        instruction.setProjectedReceiveQty(qtyToBeRcvdInInstrResp);
        instruction.setProjectedReceiveQtyUOM(WHPK);
        log.info("Projected quantity {} with UOM {}", qtyToBeRcvdInInstrResp, WHPK);
    }
}

