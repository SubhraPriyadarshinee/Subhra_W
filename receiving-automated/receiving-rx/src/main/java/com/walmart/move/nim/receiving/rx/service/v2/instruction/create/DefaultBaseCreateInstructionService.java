package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionSetIdGenerator;
import com.walmart.move.nim.receiving.core.service.v2.CreateInstructionService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.RxSlottingServiceImpl;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.OPEN_ATTP_SERIALIZED_RECEIVING_STATUS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;

@Slf4j
@Service
public abstract class DefaultBaseCreateInstructionService implements CreateInstructionService  {

    @Autowired protected InstructionHelperService instructionHelperService;
    @Autowired private RxSlottingServiceImpl rxSlottingServiceImpl;
    @Autowired protected Gson gson;
    @Resource
    protected CreateInstructionDataValidator createInstructionDataValidator;
    @ManagedConfiguration
    private AppConfig appConfig;
    @Autowired protected InstructionPersisterService instructionPersisterService;
    @Autowired protected InstructionSetIdGenerator instructionSetIdGenerator;
    @Resource
    private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;
    @Resource
    private RxInstructionService rxInstructionService;
    @Resource
    protected CreateInstructionServiceHelper createInstructionServiceHelper;


    public abstract InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException;

    @Override
    public void validateData(DataHolder dataHolder) {
        createInstructionDataValidator.performDeliveryDocumentLineValidations(dataHolder.getDeliveryDocumentLine());
        rxInstructionService.filterInvalidPOs(dataHolder.getDeliveryDocuments());
        createInstructionDataValidator.validatePartiallyReceivedContainers(dataHolder.getContainer().getReceivingStatus());
        createInstructionDataValidator.validateNodesReceivingStatus(dataHolder.getContainer().getReceivingStatus());
    }

    @Override
    public void validateData(InstructionRequest instructionRequest, DataHolder dataHolder) {
        // VALIDATE SPLIT PALLET
        if (!dataHolder.getReceivingFlow().equals(RxConstants.ReceivingTypes.MULTI_SKU)) {
            createInstructionDataValidator.validatePartialsInSplitPallet(instructionRequest,
                    CollectionUtils.containsAny(dataHolder.getDeliveryDocument().
                                    getGdmCurrentNodeDetail().getContainers().get(0).getHints(),
                            RxConstants.GdmHints.UNIT_ITEM));
        }
    }


    protected LinkedTreeMap<String, Object> getPrimeSlotDetails(
            InstructionRequest instructionRequest,
            DeliveryDocument deliveryDocument,
            HttpHeaders httpHeaders) {

        LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();

        if (!StringUtils.isEmpty(instructionRequest.getProblemTagId())) {
            moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, appConfig.getRxProblemDefaultDoor());
        } else {
            moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, instructionRequest.getDoorNumber());
        }
        moveTreeMap.put(
                "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
        moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
        moveTreeMap.put(
                ReceivingConstants.MOVE_LAST_CHANGED_BY,
                httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
        // Add Prime Slot info
        SlottingPalletResponse slottingPalletResponse = null;
        try {
            slottingPalletResponse =
                    rxSlottingServiceImpl.acquireSlot(
                            instructionRequest.getMessageId(),
                            Arrays.asList(deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr()),
                            0,
                            ReceivingConstants.SLOTTING_FIND_PRIME_SLOT,
                            httpHeaders);

            if (Objects.nonNull(slottingPalletResponse)
                    && !CollectionUtils.isEmpty(slottingPalletResponse.getLocations())) {
                log.info("Prime Slot {} found for the Instruction", slottingPalletResponse.getLocations().get(0).getLocation());
                moveTreeMap.put(
                        ReceivingConstants.MOVE_PRIME_LOCATION,
                        slottingPalletResponse.getLocations().get(0).getLocation());
                moveTreeMap.put(
                        ReceivingConstants.MOVE_PRIME_LOCATION_SIZE,
                        slottingPalletResponse.getLocations().get(0).getLocationSize());
            }
        } catch (ReceivingBadDataException e) {
            // Ignore the exception - Chances of no prime slot for Item
            log.error(
                    "Prime Slot not found for the item {}, "
                            + "Got Error from Slotting service errorCode {} and description {}",
                    deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr(),
                    e.getErrorCode(),
                    e.getDescription());

            throw e;
        }
        return moveTreeMap;
    }


    protected Instruction createEPCISInstruction(
            DeliveryDocument deliveryDocument,
            DeliveryDocumentLine deliveryDocumentLine,
            InstructionRequest instructionRequest,
            HttpHeaders httpHeaders
    ) throws ReceivingException {
        deliveryDocumentLine.getAdditionalInfo().setIsEpcisSmartReceivingEnabled(true);
        Instruction instruction =
                InstructionUtils.mapDeliveryDocumentToInstruction(
                        deliveryDocument,
                        InstructionUtils.mapHttpHeaderToInstruction(
                                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

        LinkedTreeMap<String, Object> moveTreeMap =
                getPrimeSlotDetails(instructionRequest, deliveryDocument, httpHeaders);
        instruction.setMove(moveTreeMap);

        if (Objects.isNull(gson)) gson = new Gson();
        String currentNode = gson.toJson(deliveryDocument.getGdmCurrentNodeDetail().getContainers().get(0));
        instruction.setInstructionCreatedByPackageInfo(currentNode);
        instruction.setProviderId(ReceivingConstants.RX_STK);
        instruction.setActivityName(ReceivingConstants.RX_STK);

        log.info("Create Instruction request for request {} - {}", currentNode, httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        return instruction;
    }

    protected InstructionResponse constructAndPersistEPCISInstruction(
            Instruction instruction,
            DeliveryDocument deliveryDocument,
            DeliveryDocumentLine deliveryDocumentLine,
            InstructionRequest instructionRequest,
            HttpHeaders httpHeaders

    ) throws ReceivingException {
        InstructionResponse instructionResponse = new InstructionResponseImplNew();
        calculateQuantitiesAndPersistIns(instruction, deliveryDocumentLine, instructionRequest, deliveryDocument, httpHeaders);
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

        // derive ProjectedQty
        int projectedReceiveQtyInEaches =
                RxUtils.deriveProjectedReceiveQtyInEachesForEpcisEnabledFlow(
                        deliveryDocumentLine, totalReceivedQtyInEaches, attpEpcisQtyInEaches);

        validateProjectedReceivedQuantity(instructionRequest, httpHeaders, deliveryDocumentLine, projectedReceiveQtyInEaches, totalReceivedQtyInEaches);

        // Quantities in Eaches when persisting in Instruction table
        instruction.setProjectedReceiveQty(projectedReceiveQtyInEaches);
        instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
        instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);

        //populate Instruction SET Id for split-pallet
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

    protected void convertQuantitiesAndConstructResponse(
            Instruction instruction, DeliveryDocumentLine deliveryDocumentLine) {
        int projectedReceiveQty =
                ReceivingUtils.conversionToVendorPack(
                        instruction.getProjectedReceiveQty(),
                        ReceivingConstants.Uom.EACHES,
                        deliveryDocumentLine.getVendorPack(),
                        deliveryDocumentLine.getWarehousePack());
        int qtyToBeRcvdInInstrResp = 0;
        String uomInInstrResp;
        if (projectedReceiveQty < 1) {
            qtyToBeRcvdInInstrResp = ReceivingUtils.conversionToWareHousePack(
                    instruction.getProjectedReceiveQty(),
                    ReceivingConstants.Uom.EACHES,
                    deliveryDocumentLine.getVendorPack(),
                    deliveryDocumentLine.getWarehousePack());
            uomInInstrResp = WHPK;
        } else {
            qtyToBeRcvdInInstrResp = projectedReceiveQty;
            uomInInstrResp = VNPK;
        }
        instruction.setProjectedReceiveQty(qtyToBeRcvdInInstrResp);
        instruction.setProjectedReceiveQtyUOM(uomInInstrResp);
        log.info("Projected quantity {} with UOM {}", qtyToBeRcvdInInstrResp, uomInInstrResp);

    }

    protected List<SsccScanResponse.Container> getCurrentAndSiblings(InstructionRequest instructionRequest, SsccScanResponse.Container gdmContainer, HttpHeaders httpHeaders) {
        String parentId = gdmContainer.getParentId();

        // call currentAndSiblings
        List<SsccScanResponse.Container> responseContainers =
                rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(
                        instructionRequest, httpHeaders, null).getContainers();

        log.info("Siblings count {} before filter - {}", responseContainers.size(), httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        ArrayList<SsccScanResponse.Container> currentAndSiblingsContainers = new ArrayList<>();
        responseContainers
                .stream()
                .filter(
                        container -> {
                            // ignore PARTIAL_PACK/MULTI_SKU
                            if (CollectionUtils.containsAny(
                                    container.getHints(),
                                    Arrays.asList(
                                            RxConstants.GdmHints.PARTIAL_PACK_ITEM,
                                            RxConstants.GdmHints.MULTI_SKU_PACKAGE,
                                            RxConstants.GdmHints.UNIT_ITEM))) {
                                return false;
                            }

                            // CASE_PACK_ITEM
                            if (CollectionUtils.containsAny(
                                    container.getHints(), RxConstants.GdmHints.CASE_PACK_ITEM)
                                    && OPEN_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(container.getReceivingStatus())) {
                                currentAndSiblingsContainers.add(container);
                            }
                            return true;
                        })
                .collect(Collectors.toList());

        log.info("Siblings count {} after filter - {}", currentAndSiblingsContainers.size(), httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        return currentAndSiblingsContainers;
    }

    public void validateProjectedReceivedQuantity(InstructionRequest instructionRequest, HttpHeaders httpHeaders, DeliveryDocumentLine deliveryDocumentLine,
                                                  int projectedReceiveQtyInEaches, long totalReceivedQtyInEaches) throws ReceivingException {

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
    }
}
