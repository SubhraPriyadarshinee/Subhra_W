package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AdditionalInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.rx.service.RxInstructionPersisterService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.PROBLEM;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.*;

@Slf4j
@Component
public class CreateInstructionServiceHelper {

    @Autowired
    private RxDeliveryDocumentsSearchHandlerV2 deliveryDocumentsSearchHandlerV2;

    @Autowired protected Gson gson;
    @Autowired private RxInstructionPersisterService rxInstructionPersisterService;
    @Autowired private InstructionPersisterService instructionPersisterService;
    @Autowired private InstructionHelperService instructionHelperService;
    @Resource private CreateInstructionDataValidator createInstructionDataValidator;


    private Instruction checkIfInstructionExistsBeforeAllowingPartialReceivingV2(
            String deliveryNumber, String gtin, String serial, String lot, String expiryDate,  String userId) {
        Instruction existingInstruction = null;
        List<Instruction> instructionsByDeliveryAndGtin =
                instructionPersisterService.findInstructionByDeliveryAndGtin(Long.valueOf(deliveryNumber));

        // CALL TO VALIDATE GTIN SERIAL AND LOT
        instructionsByDeliveryAndGtin =
                filterInstructionMatching2DV2(instructionsByDeliveryAndGtin, gtin, serial, lot, expiryDate);
        Optional<Instruction> opartialInstruction = Optional.empty();
        if (CollectionUtils.isNotEmpty(instructionsByDeliveryAndGtin)) {
            opartialInstruction =
                    instructionsByDeliveryAndGtin
                            .stream()
                            .filter(instruction -> RxUtils.isPartialInstruction(instruction.getInstructionCode()))
                            .findFirst();
        }

        if (opartialInstruction.isPresent()) {
            Instruction partialInstruction = opartialInstruction.get();
            if (userId.equalsIgnoreCase(partialInstruction.getCreateUserId())
                    || userId.equalsIgnoreCase(partialInstruction.getLastChangeUserId())) {
                existingInstruction = partialInstruction;
            } else {
                throw new ReceivingBadDataException(
                        ExceptionCodes.MULTI_INSTRUCTION_NOT_SUPPORTED,
                        RxConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE);
            }
        } else if (CollectionUtils.isNotEmpty(instructionsByDeliveryAndGtin)) { // Regular instruction exists, has to be completed before
            // requesting partial
            // instruction
            throw new ReceivingBadDataException(
                    ExceptionCodes.COMPLETE_EXISTING_INSTRUCTION, RxConstants.COMPLETE_EXISTING_INSTRUCTION);
        }
        return existingInstruction;
    }



    public InstructionResponse checkAndValidateExistingInstruction(InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
        Map<String, ScannedData> scannedDataMap =
                RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        String deliveryNumber = instructionRequest.getDeliveryNumber();

        Instruction existingInstruction = null;
        Optional<RxReceivingType> receivingTypeOptional =
                RxReceivingType.fromString(instructionRequest.getReceivingType());
        RxReceivingType receivingType = null;
        if (receivingTypeOptional.isPresent()) {
            receivingType = receivingTypeOptional.get();
        }

        if (RxReceivingType.TWOD_BARCODE_PARTIALS == receivingType) {
            existingInstruction =
                    checkIfInstructionExistsBeforeAllowingPartialReceivingV2(
                            deliveryNumber,
                            scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue(),
                            scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey()).getValue(),
                            scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue(),
                            scannedDataMap.get(ApplicationIdentifier.EXP.getKey()).getValue(),
                            userId);
        }

        if (Objects.isNull(existingInstruction)) {
            if (RxUtils.is2DScanInstructionRequest(instructionRequest.getScannedDataList())) {
                // 2d barcode
                existingInstruction =
                        StringUtils.isNotBlank(instructionRequest.getProblemTagId()) ?
                                instructionPersisterService.fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
                                        instructionRequest, userId, instructionRequest.getProblemTagId()
                                )
                                : rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(
                                instructionRequest, userId);

            } else if (isSSCCScanRequest(instructionRequest)) {
                //SSCC
                createInstructionDataValidator.validateScannedData(
                        scannedDataMap,
                        ApplicationIdentifier.SSCC.getKey(),
                        RxConstants.INVALID_SCANNED_DATA_SSCC_NOT_AVAILABLE);
                existingInstruction = StringUtils.isNotBlank(instructionRequest.getProblemTagId()) ?
                        instructionPersisterService
                                .fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(
                                        instructionRequest, userId)
                        : rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
                        instructionRequest, userId);

            }  else {
                // Message Id
                existingInstruction =
                        rxInstructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
            }

            if (Objects.nonNull(existingInstruction)) {
                instructionRequest.setDeliveryDocuments(
                        Arrays.asList(
                                gson.fromJson(
                                        existingInstruction.getDeliveryDocument(), DeliveryDocument.class)));
            }

            if (Objects.nonNull(existingInstruction)) {
                RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(existingInstruction);
            }

        }

        if (Objects.nonNull(existingInstruction)) {
            InstructionResponse response = new InstructionResponseImplNew();
            response.setInstruction(existingInstruction);
            response.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
            return response;
        } else return null;


    }


    public List<Instruction> filterInstructionMatching2DV2(
            List<Instruction> existingInstructionsList, String upcNumber, String serial, String lotNumber, String expiryDate) {
        if (CollectionUtils.isNotEmpty(existingInstructionsList)) {
            for (Instruction instruction : existingInstructionsList) {
                SsccScanResponse.Container gdmContainer = getGdmContainerForInstruction(instruction);
                try {
                    if (gdmContainer != null && Objects.equals(gdmContainer.getSerial(), serial) &&
                            Objects.equals(gdmContainer.getGtin(), upcNumber) &&
                            Objects.equals(gdmContainer.getLotNumber(), lotNumber) &&
                            Objects.equals(RxUtils.formatDate(gdmContainer.getExpiryDate()), expiryDate)) {
                        return Arrays.asList(instruction);
                    }
                }
                catch (ParseException ex) {
                    throw new ReceivingBadDataException(
                            ExceptionCodes.INVALID_GDM_EXPIRY_DATE, RxConstants.INVALID_GDM_EXPIRY_DATE);
                }

            }
        }
        return null;
    }

    private static SsccScanResponse.Container getGdmContainerForInstruction(Instruction instruction) {
        return instruction.getInstructionCreatedByPackageInfo() != null ?
                new Gson().fromJson(instruction.getInstructionCreatedByPackageInfo(),
                        SsccScanResponse.Container.class) : null;

    }

    private boolean isSSCCScanRequest(InstructionRequest instructionRequest)  {
        return StringUtils.isNotBlank(instructionRequest.getSscc());
    }

    public DataHolder getDataForCreateInstruction(InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
        //get current node and parent node details along with delivery and PO information.
        List<DeliveryDocument> deliveryDocuments = deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(instructionRequest, httpHeaders);

        log.info("Delivery Documents created for the request {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        DeliveryDocument deliveryDocument = null;
        DeliveryDocumentLine deliveryDocumentLine = null;
        String receivingFlow = null;
        SsccScanResponse.Container container = null;
        SsccScanResponse.Container rootContainer;

        if (!deliveryDocuments.isEmpty()) {
            container = getCurrentContainer(deliveryDocuments);
            List<String> hints = container.getHints();

            rootContainer = getRootContainer(deliveryDocuments);

            log.info("Hints provided by GDM - {} for the request {}", hints, httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));


            if (isMultiSkuNode(container)) {
                receivingFlow = MULTI_SKU;
            } else {
                //choose DeliveryDocument and DeliveryDocumentLine
                deliveryDocument =
                        instructionHelperService
                                .autoSelectDocumentAndDocumentLineMABD(
                                        deliveryDocuments, 1, ReceivingConstants.Uom.EACHES)
                                .getKey();
                if (Objects.nonNull(deliveryDocument)) {
                    deliveryDocumentLine = selectDocumentAndDocumentLine(deliveryDocument);
                }
                receivingFlow = getReceivingFlow(instructionRequest, container, rootContainer, hints, deliveryDocumentLine);
            }
            log.info("Receiving flow {} derived for this container {} in delivery {}", receivingFlow, container.getId(), instructionRequest.getDeliveryNumber());
        }

        DataHolder dataHolder = DataHolder.builder()
                .deliveryDocuments(deliveryDocuments)
                .deliveryDocument(deliveryDocument)
                .deliveryDocumentLine(deliveryDocumentLine)
                .container(container)
                .receivingFlow(receivingFlow)
                .build();
        return dataHolder;
    }

    private boolean isMultiSkuNode(SsccScanResponse.Container container) {

        return !CollectionUtils.containsAny(container.getHints(), RxConstants.GdmHints.UNIT_ITEM)
                && (CollectionUtils.containsAny(container.getHints(), RxConstants.GdmHints.MULTI_SKU_PACKAGE)
                && Objects.nonNull(container.getItemInfo()));
    }

    public boolean isMultiSkuRootNode(SsccScanResponse currentNodeResponse) {
        if (Objects.nonNull(currentNodeResponse.getContainers()) && !CollectionUtils.containsAny(currentNodeResponse.getContainers().get(0).getHints(),
                RxConstants.GdmHints.UNIT_ITEM) && !isMultiSkuNode(currentNodeResponse.getContainers().get(0))){
            if (Objects.nonNull(currentNodeResponse.getAdditionalInfo())
                    && Objects.nonNull(currentNodeResponse.getAdditionalInfo().getContainers())) {
                AdditionalInfo additionalInfo = currentNodeResponse.getAdditionalInfo();
                return CollectionUtils.containsAny(additionalInfo.getContainers().get(0).getHints(),
                        RxConstants.GdmHints.MULTI_SKU_PACKAGE);
            }
        }
        return false;
    }

    private String getReceivingFlow(InstructionRequest instructionRequest, SsccScanResponse.Container container, SsccScanResponse.Container rootContainer, List<String> hints, DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
        String receivingFlow;

        int fullCaseQty =
                deliveryDocumentLine.getVendorPack() / deliveryDocumentLine.getWarehousePack();

        if (Objects.nonNull(instructionRequest.getProblemTagId())) {
            receivingFlow = PROBLEM + "-" + getReceivingMethod(instructionRequest, container, rootContainer, hints, fullCaseQty) ;
        } else {
            receivingFlow = getReceivingMethod(instructionRequest, container, rootContainer, hints, fullCaseQty);
        }
        return receivingFlow;
    }

    private String getReceivingMethod(InstructionRequest instructionRequest, SsccScanResponse.Container container, SsccScanResponse.Container rootContainer, List<String> hints, int fullCaseQty) throws ReceivingException {
        String receivingFlow;
        if (CollectionUtils.containsAny(hints, RxConstants.GdmHints.UNIT_ITEM)) {
            if(CollectionUtils.containsAny(rootContainer.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)){
                throw new ReceivingException(ReceivingException.UNIT_RECEIVING_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
            }
            receivingFlow = PARTIAL_CASE;
        } else if (CollectionUtils.containsAny(hints, RxConstants.GdmHints.PARTIAL_PACK_ITEM)) {
            if (StringUtils.isNotBlank(instructionRequest.getSscc())) {
                if (container.getUnitCount() > fullCaseQty) {
                    throw new ReceivingException(ReceivingException.HINT_SCAN_CASES, HttpStatus.BAD_REQUEST);
                }
                else {
                    throw new ReceivingException(
                            ReceivingException.HINT_PERFORM_UNIT_RCV, HttpStatus.BAD_REQUEST);
                }
            } else {
                throw new ReceivingException(
                        ReceivingException.HINT_PERFORM_UNIT_RCV, HttpStatus.BAD_REQUEST);
            }
        } else if (CollectionUtils.containsAny(hints, RxConstants.GdmHints.CNTR_WITH_MULTI_LABEL_CHILD_CNTRS)) {
            throw new ReceivingException(ReceivingException.SCAN_CASES_OR_UNITS, HttpStatus.BAD_REQUEST);
        } else if (CollectionUtils.containsAny(hints, RxConstants.GdmHints.SSCC_SGTIN_PACKAGE)) {
            throw new ReceivingException(ReceivingException.HINT_SCAN_CASE_2D, HttpStatus.BAD_REQUEST);
        } else if (CollectionUtils.containsAll(
                hints,
                Arrays.asList(
                        RxConstants.GdmHints.CASE_PACK_ITEM, RxConstants.GdmHints.SINGLE_SKU_PACKAGE))) {
            if (Objects.equals(container.getChildCount(), container.getUnitCount())
            || container.getUnitCount().intValue() == fullCaseQty) {
                if (RxReceivingType.MULTI_SKU_FLOW.equals(getReceivingTypeFromUI(instructionRequest))) {
                    if (CollectionUtils.containsAny(hints, RxConstants.GdmHints.HANDLE_AS_CASEPACK)) {
                        receivingFlow = HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU;
                    } else {
                        receivingFlow = PALLET_FROM_MULTI_SKU;
                    }
                } else {
                    if (CollectionUtils.containsAny(hints, RxConstants.GdmHints.HANDLE_AS_CASEPACK)) {
                        if (container.getUnitCount().intValue() > fullCaseQty) {
                            receivingFlow = HNDL_AS_CSPK_FULL_PALLET;
                        } else {
                            if (isFloorLoadedCase(container, rootContainer)) {
                                receivingFlow = HNDL_AS_CSPK_FLOOR_LOADED_CASE;
                            } else {
                                receivingFlow = HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD;
                            }
                        }
                    } else {
                        if (isFloorLoadedCase(container, rootContainer)) {
                            receivingFlow = FLOOR_LOADED_CASE;
                        } else {
                            receivingFlow = CASE;
                        }
                    }
                }
            } else {
                if (container.getUnitCount() < fullCaseQty
                || (container.getUnitCount() % fullCaseQty != 0)) {
                    throw new ReceivingException(
                            ReceivingException.HINT_PERFORM_UNIT_RCV, HttpStatus.BAD_REQUEST);
                } else {
                    if (StringUtils.isNotBlank(instructionRequest.getSscc())) {
                        receivingFlow = FULL_PALLET;
                    }
                    else {
                        throw new ReceivingException(
                                ReceivingException.HINT_PERFORM_UNIT_RCV, HttpStatus.BAD_REQUEST);
                    }
                }
            }
        }else {
                throw new ReceivingException(
                        ReceivingException.HINT_PERFORM_UNIT_RCV, HttpStatus.BAD_REQUEST);
        }
        return receivingFlow;
    }

    // currentContainer has FLOOR_LOADED hint or [rootContainer has FLOOR_LOADED hint and parentIds are same]
    private boolean isFloorLoadedCase(SsccScanResponse.Container currentContainer, SsccScanResponse.Container rootContainer) {
        List<String> gdmCurrentContainerHints = currentContainer.getHints();
        List<String> gdmRootContainerHints = rootContainer.getHints();

        return CollectionUtils.containsAny(gdmCurrentContainerHints, RxConstants.GdmHints.FLOOR_LOADED_PACKAGE) ||
                (CollectionUtils.containsAny(gdmRootContainerHints, RxConstants.GdmHints.FLOOR_LOADED_PACKAGE) &&
                        currentContainer.getParentId().equalsIgnoreCase(rootContainer.getId()));
    }
    
    public DeliveryDocumentLine selectDocumentAndDocumentLine(DeliveryDocument selectedDocument) {
        List<DeliveryDocumentLine> deliveryDocumentLines = selectedDocument.getDeliveryDocumentLines();
        return deliveryDocumentLines
                .stream()
                .sorted(
                        Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceNumber)
                                .thenComparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
                .collect(Collectors.toList())
                .get(0);
    }

    public SsccScanResponse.Container getCurrentContainer(List<DeliveryDocument> deliveryDocuments){
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                deliveryDocuments.get(0).getGdmCurrentNodeDetail();
        return gdmCurrentNodeDetail.getContainers().get(0);
    }

    public SsccScanResponse.Container getRootContainer(List<DeliveryDocument> deliveryDocuments) {
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                deliveryDocuments.get(0).getGdmCurrentNodeDetail();
        return gdmCurrentNodeDetail.getAdditionalInfo().getContainers().get(0);
    }

    public RxReceivingType getReceivingTypeFromUI(InstructionRequest instructionRequest) {
        Optional<RxReceivingType> receivingTypeOptional =
                RxReceivingType.fromString(instructionRequest.getReceivingType());
        if (receivingTypeOptional.isPresent()) {
            return receivingTypeOptional.get();
        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.NO_RECEIVING_TYPE,
                    ReceivingException.NO_RECEIVING_TYPE_SPECIFIED);
        }
    }

    public boolean isEpcisSmartReceivingEnabledFromClient(HttpHeaders headers) {
        if (headers.containsKey(RxConstants.EPCIS_SMART_RECV_CLIENT_ENABLED)) {
            return (RxConstants.TRUE_STRING)
                    .equalsIgnoreCase(headers.getFirst(RxConstants.EPCIS_SMART_RECV_CLIENT_ENABLED));
        }
        return false;
    }
}
