package com.walmart.move.nim.receiving.rx.service.v2.instruction.update;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.UPDATE_INSTRUCTION_ENTER_VALID_QTY;

@Slf4j
@Service
public class PalletProcessInstructionService extends DefaultBaseUpdateInstructionService {

    @Autowired private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Autowired private Gson gson;
    @Autowired private TenantSpecificConfigReader configUtils;
    @Autowired private RxInstructionHelperService rxInstructionHelperService;
    @Autowired private UpdateInstructionDataValidator updateInstructionDataValidator;

    @Transactional
    @Override
    public InstructionResponse validateUserEnteredQty(
            UpdateInstructionRequest instructionUpdateRequestFromClient, Instruction instruction4mDB) throws ReceivingException {

        ItemData additionalInfo = null;
        DeliveryDocument deliveryDocument;
        DeliveryDocumentLine deliveryDocumentLine = null;
        deliveryDocument = gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
        if (deliveryDocument!= null) {
            List<DeliveryDocumentLine> lines = deliveryDocument.getDeliveryDocumentLines();
            if (lines!= null &&!lines.isEmpty()) {
                deliveryDocumentLine = lines.get(0);
                if (deliveryDocumentLine!= null) {
                    additionalInfo = deliveryDocumentLine.getAdditionalInfo();
                }
            }
        }

        DocumentLine documentLine =
                instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
        Integer quantityToBeReceivedInVNPK = 0;

        Integer caseCount = 0;
        List<ScannedData> enteredDataList = instructionUpdateRequestFromClient.getUserEnteredDataList();
        Map<String, ScannedData> enteredDataMap = RxUtils.scannedDataMap(enteredDataList);

        String instructionPackageInfo = instruction4mDB.getInstructionCreatedByPackageInfo();
        SsccScanResponse.Container instructionPack = gson.fromJson(instructionPackageInfo, SsccScanResponse.Container.class);


        quantityToBeReceivedInVNPK =
                ReceivingUtils.conversionToVendorPack(
                        instruction4mDB.getProjectedReceiveQty(),
                        instruction4mDB.getProjectedReceiveQtyUOM(),
                        documentLine.getVnpkQty(),
                        documentLine.getWhpkQty());

        caseCount = Integer.valueOf(enteredDataMap.get(ReceivingConstants.ZA).getValue());
        log.info(
                "validateUserEnteredQty ::ZA validation:: quantityToBeReceivedInVNPK {} :: caseCount {}",
                quantityToBeReceivedInVNPK,
                caseCount);

        if (caseCount > 0 && caseCount.equals(quantityToBeReceivedInVNPK) && !additionalInfo.isPalletFlowInMultiSku()) {
            additionalInfo.setAuditQty(
                    Integer.valueOf(
                            configUtils.getCcmValue(
                                    TenantContext.getFacilityNum(),
                                    ReceivingConstants.DEFAULT_PALLET_AUDIT_COUNT,
                                    ReceivingConstants.ONE.toString())));
        } else {
            if (CollectionUtils.containsAny(instructionPack.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)) {
                throw new ReceivingBadDataException(ExceptionCodes.UPDATE_INSTRUCTION_ERROR, UPDATE_INSTRUCTION_ENTER_VALID_QTY);
            }
            if (instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_FULL_PALLET)) {
                instruction4mDB.setReceivingMethod(RxConstants.ReceivingTypes.PROBLEM_PALLET_RECEIVED_WITH_CASE_SCANS);
            } else {
                instruction4mDB.setReceivingMethod(RxConstants.ReceivingTypes.PALLET_RECEIVED_WITH_CASE_SCANS);
            }
            additionalInfo.setAuditQty(quantityToBeReceivedInVNPK);
        }

        additionalInfo.setQtyValidationDone(Boolean.TRUE);
        if (deliveryDocument != null  && deliveryDocumentLine != null) {
            deliveryDocumentLine.setAdditionalInfo(additionalInfo);
            deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
        }
        instruction4mDB.setDeliveryDocument(gson.toJson(deliveryDocument));
        rxInstructionHelperService.saveInstruction(instruction4mDB);
        return new InstructionResponseImplNew(null, null, instruction4mDB, null);
    }

    @Override
    public void processUpdateInstruction(UpdateInstructionRequest instructionUpdateRequestFromClient, DataHolder dataForUpdateInstruction, DeliveryDocument deliveryDocument4mDB, DeliveryDocumentLine deliveryDocumentLine4mDB, boolean setAuditQty, HttpHeaders httpHeaders) throws ReceivingException {

        updateInstructionServiceHelper.validateScannedContainer(instructionUpdateRequestFromClient,  dataForUpdateInstruction.getInstruction());
        updateInstructionServiceHelper.callGDMCurrentNodeApi( instructionUpdateRequestFromClient, httpHeaders,
                dataForUpdateInstruction.getInstruction(),dataForUpdateInstruction);
        updateInstructionDataValidator.validateOpenReceivingStatus(dataForUpdateInstruction.getGdmResponseForScannedData());
        updateInstructionDataValidator.validateInstructionGtinMatchesCurrentNodeApiGtin(dataForUpdateInstruction.getInstruction(),dataForUpdateInstruction.getGdmResponseForScannedData());

        Instruction instruction = dataForUpdateInstruction.getInstruction();
        String instructionPackageInfo = instruction.getInstructionCreatedByPackageInfo();
        SsccScanResponse.Container instructionPack = gson.fromJson(instructionPackageInfo, SsccScanResponse.Container.class);
        updateInstructionDataValidator.validateScannedCaseBelongsToPallet(instructionPack, dataForUpdateInstruction.getGdmResponseForScannedData());

        setAuditQty = true;
        ItemData additionalInfo = updateInstructionServiceHelper.getAdditionalInfoFromDeliveryDoc(dataForUpdateInstruction.getInstruction());
        DocumentLine documentLine =
                instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
        List<ScannedData> scannedDataList = instructionUpdateRequestFromClient.getScannedDataList();
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);

        Map<ItemData, SsccScanResponse.Container> selectedSerialInfoList = null;

        Integer quantityToBeReceivedInVNPK =
                ReceivingUtils.conversionToVendorPack(
                        ReceivingConstants.ONE,
                        ReceivingConstants.Uom.VNPK,
                        documentLine.getVnpkQty(),
                        documentLine.getWhpkQty());

        Integer totalVnpkQtyForPallet =
                ReceivingUtils.conversionToVendorPack(
                        dataForUpdateInstruction.getInstruction().getProjectedReceiveQty(),
                        dataForUpdateInstruction.getInstruction().getProjectedReceiveQtyUOM(),
                        documentLine.getVnpkQty(),
                        documentLine.getWhpkQty());

        Integer quantityToBeReceivedInEaches =
                ReceivingUtils.conversionToEaches(
                        documentLine.getQuantity(),
                        documentLine.getQuantityUOM(),
                        documentLine.getVnpkQty(),
                        documentLine.getWhpkQty());


        selectedSerialInfoList =
                updateInstructionServiceHelper.verifyScanned2DWithGDMData(dataForUpdateInstruction.getInstruction(),
                        additionalInfo,
                        scannedDataMap,
                        totalVnpkQtyForPallet,
                        deliveryDocument4mDB,
                        deliveryDocumentLine4mDB,
                        setAuditQty,
                        dataForUpdateInstruction.getGdmResponseForScannedData());
        Map.Entry<ItemData, SsccScanResponse.Container> selectedSerialInfoListEntry =
                selectedSerialInfoList.entrySet().iterator().next();
        additionalInfo = selectedSerialInfoListEntry.getKey();
        log.debug(
                "epcisEnabled flow: Pallet receiving  :: quantityToBeReceivedInVNPK {} :: totalVnpkQtyForPallet {} :: "
                        + "selectedSerialInfoList {}  :: additionalInfo {} :: selectedPackDetails{} ",
                quantityToBeReceivedInVNPK,
                totalVnpkQtyForPallet,
                selectedSerialInfoList,
                additionalInfo,
                dataForUpdateInstruction.getGdmResponseForScannedData());
        if ((totalVnpkQtyForPallet != additionalInfo.getAuditQty()
                && additionalInfo.getAuditQty() == additionalInfo.getAuditCompletedQty())) {
            quantityToBeReceivedInEaches =
                    ReceivingUtils.conversionToEaches(
                            dataForUpdateInstruction.getInstruction().getProjectedReceiveQty(),
                            dataForUpdateInstruction.getInstruction().getProjectedReceiveQtyUOM(),
                            documentLine.getVnpkQty(), documentLine.getWhpkQty());
            quantityToBeReceivedInVNPK = totalVnpkQtyForPallet;
        }

        dataForUpdateInstruction.setQuantityToBeReceivedInEaches(quantityToBeReceivedInEaches);
        dataForUpdateInstruction.setQuantityToBeReceivedInVNPK(quantityToBeReceivedInVNPK);
        deliveryDocumentLine4mDB.setAdditionalInfo(additionalInfo);
        deliveryDocument4mDB.setDeliveryDocumentLines(
                Collections.singletonList(deliveryDocumentLine4mDB));
        dataForUpdateInstruction.getInstruction().setDeliveryDocument(gson.toJson(deliveryDocument4mDB));
        verifyIfCaseCanBeReceived(scannedDataMap, dataForUpdateInstruction);
    }

    @Override
    protected String getCaseContainerTrackingId(List<Container> containers, List<ContainerItem> containerItems, Instruction instruction, DeliveryDocument deliveryDocument,
                                                UpdateInstructionRequest instructionUpdateRequestFromClient, HttpHeaders httpHeaders, String userId,
                                                SsccScanResponse.Container instructionPack, DocumentLine documentLine, Map<String, ScannedData> scannedDataMap,
                                                Integer quantityToBeReceivedInEaches, String parentTrackingId) {
        return null;
    }
}
