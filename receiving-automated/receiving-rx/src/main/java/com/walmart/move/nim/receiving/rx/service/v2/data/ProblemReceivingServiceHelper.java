package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;

@Slf4j
@Component
public class ProblemReceivingServiceHelper {

    @ManagedConfiguration
    private AppConfig appConfig;
    @Autowired
    private RxDeliveryServiceImpl rxDeliveryService;
    @ManagedConfiguration
    private RxManagedConfig rxManagedConfig;
    @Autowired
    private RxInstructionHelperService rxInstructionHelperService;
    @Autowired
    InstructionHelperService instructionHelperService;
    protected InstructionError instructionError;

    public Optional<List<DeliveryDocument>> checkForLatestShipments(
            InstructionRequest instructionRequest,
            Map<String, ScannedData> scannedDataMap,
            HttpHeaders httpHeaders)
            throws ReceivingException {

        if (appConfig.isAttachLatestShipments()) {
            log.info("Checking for latest shipment, delivery {} for problemTagId {}",
                    instructionRequest.getDeliveryNumber(),
                    instructionRequest.getProblemTagId());

            if (StringUtils.isNotBlank(instructionRequest.getSscc())) {
                return rxDeliveryService.findDeliveryDocumentBySSCCWithLatestShipmentLinking(
                        instructionRequest.getDeliveryNumber(),
                        instructionRequest.getSscc(),
                        httpHeaders);
            } else {
                return rxDeliveryService.linkDeliveryAndShipmentByGtinAndLotNumber(
                        instructionRequest.getDeliveryNumber(),
                        scannedDataMap,
                        httpHeaders);
            }
        }

        return Optional.empty();
    }

    public FitProblemTagResponse fetchFitResponseForProblemTagId(
            InstructionRequest instructionRequest
    ) {
        log.info("Inside  fetchFitResponseForProblemTagId: {}", instructionRequest.getProblemTagId());
        FitProblemTagResponse fitProblemTagResponse = instructionRequest.getFitProblemTagResponse();
        if (rxManagedConfig.isProblemItemCheckEnabled()) { // Feature Flag
            Optional<FitProblemTagResponse> fitProblemTagResponseOptional;
            if (Objects.isNull(fitProblemTagResponse)) {
                log.info("Call Fit API for problem response: {}", instructionRequest.getProblemTagId());
                fitProblemTagResponseOptional =
                        rxInstructionHelperService.getFitProblemTagResponse(
                                instructionRequest.getProblemTagId());
                if (fitProblemTagResponseOptional.isPresent()) {
                    fitProblemTagResponse = fitProblemTagResponseOptional.get();
                }

            }
        }

        return fitProblemTagResponse;
    }


    public void validateFitProblemResponse(
            InstructionRequest instructionRequest,
            DeliveryDocumentLine deliveryDocumentLine,
            FitProblemTagResponse fitProblemTagResponse) {
        if (Objects.nonNull(fitProblemTagResponse)) {
            // CHECK FOR SAME ITEM VALIDATION
            log.info("Perform validations for problemTagId: {}", instructionRequest.getProblemTagId());
            rxInstructionHelperService.sameItemOnProblem(
                    fitProblemTagResponse, deliveryDocumentLine);
            if (RxUtils.is2DScanInstructionRequest(instructionRequest.getScannedDataList())) {
                // CHECK FOR EXPIRATION DATE
                rxInstructionHelperService.checkIfContainerIsCloseDated(
                        fitProblemTagResponse,
                        RxUtils.scannedDataMap(instructionRequest.getScannedDataList()));
            }
        }
    }

    public int getProjectedReceivedQtyInEaches(
            FitProblemTagResponse fitProblemTagResponse,
            InstructionRequest instructionRequest,
            List<DeliveryDocument> deliveryDocuments,
            DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
        int projectedReceiveQtyInEaches = 0;
        if (rxManagedConfig.isProblemItemCheckEnabled()) { // Feature flag

            Pair<Integer, Long> receivedQtyDetails =
                    instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
                            instructionRequest.getProblemTagId(),
                            deliveryDocuments.get(0),
                            instructionRequest.getDeliveryNumber());

            long totalReceivedQtyInEaches = receivedQtyDetails.getValue();

            Resolution activeResolution = RxUtils.getActiveResolution(fitProblemTagResponse);
            if (Objects.nonNull(activeResolution)) {

                Integer problemQty =
                        ReceivingUtils.conversionToEaches(
                                Math.min(activeResolution.getQuantity(), fitProblemTagResponse.getReportedQty()),
                                getProblemTicketUom(fitProblemTagResponse),
                                deliveryDocumentLine.getVendorPack(),
                                deliveryDocumentLine.getWarehousePack());

                int problemResolutionQtyInEa = (int) (problemQty - totalReceivedQtyInEaches);

                projectedReceiveQtyInEaches =
                        getProjectedReceivedQtyInEaForProblem(
                                deliveryDocumentLine,
                                totalReceivedQtyInEaches,
                                problemResolutionQtyInEa);
            }
        }
        return projectedReceiveQtyInEaches;
    }

    private String getProblemTicketUom(FitProblemTagResponse fitProblemTagResponse) {
        String problemUom = fitProblemTagResponse.getIssue().getUom();
        if (StringUtils.isBlank(problemUom)) {
            problemUom = ReceivingConstants.Uom.VNPK;
        }
        return problemUom;
    }

    public static int getProjectedReceivedQtyInEaForProblem(
            DeliveryDocumentLine deliveryDocumentLine,
            long totalReceiptQtyByDeliveryPoPoLine,
            int problemQuantity) {

        int minProjReceivedQtyBtwPoAndEPCIS =
                getProjectedReceivedQtyInEaches(deliveryDocumentLine, totalReceiptQtyByDeliveryPoPoLine);
        return Math.min(minProjReceivedQtyBtwPoAndEPCIS, problemQuantity);
    }

    public static int getProjectedReceivedQtyInEaches(
            DeliveryDocumentLine deliveryDocumentLine, long totalReceiptQtyByDeliveryPoPoLineInEaches) {

        // min((poQty - receiptQty), shippedQty)
        int poQtyInEaches =
                deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
        int receiptQtyInEaches = (int) totalReceiptQtyByDeliveryPoPoLineInEaches;
        int attpQtyInEaches =
                ReceivingUtils.conversionToEaches(
                        deliveryDocumentLine.getAdditionalInfo().getAttpQtyInEaches(),
                        EACHES,
                        deliveryDocumentLine.getVendorPack(),
                        deliveryDocumentLine.getWarehousePack());

        return Math.min((poQtyInEaches - receiptQtyInEaches), attpQtyInEaches);
    }


}