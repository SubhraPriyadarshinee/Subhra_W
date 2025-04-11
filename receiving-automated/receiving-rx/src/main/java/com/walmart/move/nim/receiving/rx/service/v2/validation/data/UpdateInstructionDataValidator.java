package com.walmart.move.nim.receiving.rx.service.v2.validation.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.ContainerItemService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UpdateInstructionDataValidator {

    @Autowired private ContainerService containerService;
    @Autowired private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Autowired private ContainerItemService containerItemService;

    public void validateContainerDoesNotAlreadyExist(Integer poTypeCode, String gtin, String serial, Integer facilityNum, String facilityCountryCode) {
        if ((ReceivingConstants.transferPosTypes.contains(poTypeCode) &&
                containerItemService.getContainerItemByFacilityAndGtinAndSerial(facilityNum, facilityCountryCode, gtin, serial).isPresent())
                || (!ReceivingConstants.transferPosTypes.contains(poTypeCode)
                && containerItemService.getContainerItemByGtinAndSerial(gtin, serial).isPresent())) {
            throw new ReceivingInternalException(
                    ExceptionCodes.CONTAINER_ALREADY_EXISTS, ReceivingException.INSERT_CONTAINER_FAILED);
        }
    }

    public void validateBarcodeNotAlreadyScanned(SsccScanResponse.Container instructionPack, SsccScanResponse.Container containerScannedFromUI) {
        if (Objects.isNull(instructionPack.getSscc())
                && instructionPack.getGtin().equals(containerScannedFromUI.getGtin())
                && instructionPack.getLotNumber().equals(containerScannedFromUI.getLotNumber())
                && instructionPack.getExpiryDate().equals(containerScannedFromUI.getExpiryDate())
                && instructionPack.getSerial().equals(containerScannedFromUI.getSerial())) {
            throw new RuntimeException(RxConstants.BARCODE_ALREADY_SCANNED);
        }
    }


    public void validateScannedCaseBelongsToPallet(SsccScanResponse.Container instructionPack, SsccScanResponse.Container gdmResponseForScannedData) {
        if (!Objects.isNull(gdmResponseForScannedData.getTopLevelContainerId()) && !gdmResponseForScannedData.getTopLevelContainerId().equalsIgnoreCase(instructionPack.getTopLevelContainerId())) {
            throw new RuntimeException(RxConstants.WRONG_PALLET);
        }
    }

    public void validateScannedUnitBelongsToCase(Instruction instruction4mDB, SsccScanResponse.Container gdmResponseForScannedData) {
        String json = instruction4mDB.getInstructionCreatedByPackageInfo();
        JsonElement element = JsonParser.parseString(json);
        JsonObject obj = element.getAsJsonObject();
        String instructionContainerParentID = null;

        if (obj.has("parentId") && !obj.get("parentId").isJsonNull()) {
            instructionContainerParentID = obj.get("parentId").getAsString();
        }

        if (!Objects.isNull(gdmResponseForScannedData.getParentId())
                && !Objects.isNull(instructionContainerParentID)
                && !gdmResponseForScannedData.getParentId().equalsIgnoreCase(instructionContainerParentID)) {
            throw new RuntimeException(RxConstants.WRONG_CASE);
        }
    }

    public void validateScannedUnitBelongsToCasev2(Instruction instruction4mDB, SsccScanResponse.Container gdmResponseForScannedData) {
        String json = instruction4mDB.getInstructionCreatedByPackageInfo();
        JsonElement element = JsonParser.parseString(json);
        JsonObject obj = element.getAsJsonObject();
        String instructionContainerId = null;

        if (obj.has("id") && !obj.get("id").isJsonNull()) {
            instructionContainerId = obj.get("id").getAsString();
        }

        if (!Objects.isNull(gdmResponseForScannedData.getParentId())
                && !Objects.isNull(instructionContainerId)
                && !gdmResponseForScannedData.getParentId().equalsIgnoreCase(instructionContainerId)) {
            throw new RuntimeException(RxConstants.WRONG_CASE);
        }
    }


    public void validateOpenReceivingStatus(SsccScanResponse.Container gdmResponseForScannedData) {
        if (!gdmResponseForScannedData.getReceivingStatus().equalsIgnoreCase(RxConstants.OPEN_ATTP_SERIALIZED_RECEIVING_STATUS)) {
            throw new RuntimeException(RxConstants.ERROR_IN_PATCHING_INSTRUCTION);
        }
    }


    public void validateInstructionGtinMatchesCurrentNodeApiGtin(Instruction instruction4mDB, SsccScanResponse.Container gdmResponseForScannedData) {
        if (!instruction4mDB.getGtin().equalsIgnoreCase(gdmResponseForScannedData.getGtin())) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.UPDATE_INSTRUCTION_ERROR,
                    RxConstants.SCANNED_GTIN_DOES_NOT_MATCH_INSTRUCTION_GTIN);
        }
    }

    public void verifyIfCaseCanBeReceived(
            Long totalReceivedQty4DeliveryPoPoLine,
            Integer projectedQuantity,
            DeliveryDocumentLine deliveryDocumentLine4mDB,
            Integer totalQuantityValueAfterReceiving) {

        if (totalQuantityValueAfterReceiving > projectedQuantity) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.ALLOWED_CASES_RECEIVED, RxConstants.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY);
        }

        if (RxUtils.deriveProjectedReceiveQtyInEaches(
                deliveryDocumentLine4mDB, totalReceivedQty4DeliveryPoPoLine, 0)
                <= 0) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.ALL_CASES_RECEIVED,
                    RxConstants.UPDATE_INSTRUCTION_EXCEEDS_PALLET_QUANTITY);
        }

        if (org.apache.commons.collections4.CollectionUtils.isEmpty(deliveryDocumentLine4mDB.getShipmentDetailsList())) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.SHIPMENT_UNAVAILABLE, RxConstants.SHIPMENT_DETAILS_UNAVAILABLE);
        }
    }

    public void validateInstructionAndInstructionOwner(DataHolder dataForUpdateInstruction, HttpHeaders httpHeaders) throws ReceivingException {
        updateInstructionServiceHelper.validateRequest(dataForUpdateInstruction.getInstruction());
        updateInstructionServiceHelper.validateInstructionOwner(httpHeaders, dataForUpdateInstruction);
    }

}
