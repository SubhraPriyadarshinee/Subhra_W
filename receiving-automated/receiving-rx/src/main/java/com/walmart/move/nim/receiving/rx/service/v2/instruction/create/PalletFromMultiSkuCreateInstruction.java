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
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;


@Slf4j
@Service
public class PalletFromMultiSkuCreateInstruction extends CaseCreateInstructionService {

    @Resource
    private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;

    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException {

        Instruction instruction = null;

        log.info("Create Instruction request for Cases inside Multi-sku - {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        SsccScanResponse.Container gdmContainer = dataHolder.getContainer();
        DeliveryDocument deliveryDocument = dataHolder.getDeliveryDocument();
        DeliveryDocumentLine deliveryDocumentLine = dataHolder.getDeliveryDocumentLine();

        if (CollectionUtils.containsAny(gdmContainer.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)
                && RxReceivingType.MULTI_SKU_FLOW == createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest)) {
            throw new ReceivingException(ReceivingException.CASE_RECEIVING_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }

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
            if (!dataHolder.getReceivingFlow().equalsIgnoreCase(RxConstants.ReceivingTypes.HNDL_AS_CSPK_FLOOR_LOADED_CASE)) {
                deliveryDocumentLine.setPalletSSCC(gdmContainer.getTopLevelContainerSscc());
            } else {
                deliveryDocumentLine.getAdditionalInfo().setIsPalletInsForCaseRecv(Boolean.TRUE);
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
            deliveryDocumentLine.getAdditionalInfo().setPalletFlowInMultiSku(true);

            instruction = createEPCISInstruction(deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

            instruction.setReceivingMethod(dataHolder.getReceivingFlow());

            instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
            instruction.setInstructionMsg(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionMsg());
            instruction.setSsccNumber(gdmContainer.getTopLevelContainerSscc());

            return constructAndPersistEPCISInstruction(instruction, deliveryDocument, deliveryDocumentLine, instructionRequest, httpHeaders);

        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.GDM_NOT_ACCESSIBLE,
                    ExceptionDescriptionConstants.GDM_BAD_DATA_ERROR_MSG);
        }
    }
}
