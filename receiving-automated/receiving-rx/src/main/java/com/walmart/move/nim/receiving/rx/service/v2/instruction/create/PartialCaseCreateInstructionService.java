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
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.OPEN_ATTP_SERIALIZED_RECEIVING_STATUS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;

@Slf4j
@Service
public class PartialCaseCreateInstructionService extends DefaultBaseCreateInstructionService {

    @Resource
    private CreateInstructionServiceHelper createInstructionServiceHelper;
    @Resource private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;


    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException {

        Instruction instruction = null;

        log.info("Create Instruction request for Partial case - {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        SsccScanResponse.Container gdmContainer = dataHolder.getContainer();

        //preValidations
        if (CollectionUtils.containsAny(gdmContainer.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)) {
            throw new ReceivingException(ReceivingException.UNIT_RECEIVING_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }

        DeliveryDocument deliveryDocument = dataHolder.getDeliveryDocument();
        DeliveryDocumentLine deliveryDocumentLine = dataHolder.getDeliveryDocumentLine();


        List<SsccScanResponse.Container> responseContainers = Collections.singletonList(gdmContainer);

        String parentId = gdmContainer.getParentId();

        //no need to call siblings API for unit's without parent
        if (Objects.nonNull(parentId)) {
            deliveryDocumentLine.getAdditionalInfo().setPalletOfCase(
                    parentId);

            // call currentAndSiblings
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

        if (!unitItemContainers.isEmpty()) {
            int attpQty = ReceivingUtils.conversionToEaches(
                    unitItemContainers.size(),
                    WHPK,
                    deliveryDocumentLine.getVendorPack(),
                    deliveryDocumentLine.getWarehousePack());
            deliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(attpQty);
            deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());
            deliveryDocumentLine.getAdditionalInfo().setIsSerUnit2DScan(Boolean.TRUE);
            deliveryDocumentLine.getAdditionalInfo().setScannedCaseAttpQty(attpQty);
            deliveryDocumentLine.setGtin(gdmContainer.getGtin());

            instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

            instruction.setReceivingMethod(dataHolder.getReceivingFlow());

            instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());
            instruction.setInstructionMsg(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionMsg());

            return constructAndPersistEPCISInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.GDM_NOT_ACCESSIBLE,
                    ExceptionDescriptionConstants.GDM_BAD_DATA_ERROR_MSG);
        }
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
