package com.walmart.move.nim.receiving.rx.service.v2.instruction.update;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class PartialCaseProcessInstructionService extends DefaultBaseUpdateInstructionService  {

    @Autowired private InstructionStateValidator instructionStateValidator;
    @Autowired private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Autowired private RxInstructionService rxInstructionService;
    @Resource private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Autowired private Gson gson;
    @Autowired private TenantSpecificConfigReader configUtils;
    @Autowired private RxInstructionHelperService rxInstructionHelperService;
    @Autowired private ReceiptService receiptService;
    @ManagedConfiguration private RxManagedConfig rxManagedConfig;
    @Autowired private RxReceiptsBuilder rxReceiptsBuilder;
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
        Integer quantityToBeReceivedInWhpk = 0;

        Integer eachCount = 0;
        List<ScannedData> enteredDataList = instructionUpdateRequestFromClient.getUserEnteredDataList();
        Map<String, ScannedData> enteredDataMap = RxUtils.scannedDataMap(enteredDataList);

        quantityToBeReceivedInWhpk =
                ReceivingUtils.conversionToWareHousePack(
                        additionalInfo.getScannedCaseAttpQty(),
                        ReceivingConstants.Uom.EACHES,
                        documentLine.getVnpkQty(),
                        documentLine.getWhpkQty());

        eachCount = Integer.valueOf(enteredDataMap.get(ReceivingConstants.EA).getValue());
        log.info(
                "validateUserEnteredQty ::EA validation:: quantityToBeReceivedInWhpk {} :: eachCount {}",
                quantityToBeReceivedInWhpk,
                eachCount);
        if (eachCount > 0
                && eachCount.equals(quantityToBeReceivedInWhpk)
                && !instruction4mDB
                .getInstructionCode()
                .equals(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType())) {
            additionalInfo.setAuditQty(
                    Integer.valueOf(
                            configUtils.getCcmValue(
                                    TenantContext.getFacilityNum(),
                                    ReceivingConstants.DEFAULT_CASE_AUDIT_COUNT,
                                    ReceivingConstants.ONE.toString())));
        } else {
            additionalInfo.setAuditQty(quantityToBeReceivedInWhpk);
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
        updateInstructionDataValidator.validateScannedUnitBelongsToCase(dataForUpdateInstruction.getInstruction(), dataForUpdateInstruction.getGdmResponseForScannedData());


        Integer quantityToBeReceivedInEaches = 0;
        Integer quantityToBeReceivedInVNPK = 0;
        Integer totalVnpkQtyForPallet = null;
        setAuditQty = true;
        ItemData additionalInfo = updateInstructionServiceHelper.getAdditionalInfoFromDeliveryDoc(dataForUpdateInstruction.getInstruction());
        DocumentLine documentLine =
                instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
        List<ScannedData> scannedDataList = instructionUpdateRequestFromClient.getScannedDataList();
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);

        Map<ItemData, SsccScanResponse.Container> selectedSerialInfoList = null;


        Integer auditQtyinWhpk =
                ReceivingUtils.conversionToWareHousePack(
                        additionalInfo.getScannedCaseAttpQty(), ReceivingConstants.Uom.EACHES,
                        documentLine.getVnpkQty(), documentLine.getWhpkQty());

        if (auditQtyinWhpk != additionalInfo.getAuditQty()) {
            quantityToBeReceivedInEaches =
                    ReceivingUtils.conversionToEaches(
                            additionalInfo.getScannedCaseAttpQty(),
                            ReceivingConstants.Uom.EACHES,
                            documentLine.getVnpkQty(), documentLine.getWhpkQty());

            selectedSerialInfoList =
                    updateInstructionServiceHelper.verifyScanned2DWithGDMData(
                            dataForUpdateInstruction.getInstruction(),
                            additionalInfo,
                            scannedDataMap,
                            auditQtyinWhpk,
                            deliveryDocument4mDB,
                            deliveryDocumentLine4mDB,
                            true,
                            dataForUpdateInstruction.getGdmResponseForScannedData());
            Map.Entry<ItemData, SsccScanResponse.Container> selectedSerialInfoListEntry =
                    selectedSerialInfoList.entrySet().iterator().next();
            additionalInfo = selectedSerialInfoListEntry.getKey();
            log.debug(
                    "epcisEnabled flow:  receiving  :: auditQtyinWhpk {} :: totalVnpkQtyForPallet {} :: "
                            + " :: additionalInfo {} :: selectedPackDetails{} ",
                    auditQtyinWhpk,
                    totalVnpkQtyForPallet,
                    additionalInfo,
                    dataForUpdateInstruction.getGdmResponseForScannedData());

        } else {
            quantityToBeReceivedInEaches =
                    ReceivingUtils.conversionToEaches(
                            ReceivingConstants.ONE,
                            ReceivingConstants.Uom.WHPK,
                            documentLine.getVnpkQty(),
                            documentLine.getWhpkQty());

            selectedSerialInfoList =
                    updateInstructionServiceHelper.verifyScanned2DWithGDMData(
                            dataForUpdateInstruction.getInstruction(),
                            additionalInfo,
                            scannedDataMap,
                            auditQtyinWhpk,
                            deliveryDocument4mDB,
                            deliveryDocumentLine4mDB,
                            false,
                            dataForUpdateInstruction.getGdmResponseForScannedData());
            Map.Entry<ItemData, SsccScanResponse.Container> selectedSerialInfoListEntry =
                    selectedSerialInfoList.entrySet().iterator().next();
            additionalInfo = selectedSerialInfoListEntry.getKey();
            log.debug(
                    "EpcisEnabled flow:  receiving  :: quantityToBeReceivedInEaches {} :: additionalInfo {} :: "
                            + " :: gdmResponseForScannedData {}",
                    quantityToBeReceivedInEaches,
                    additionalInfo,
                    dataForUpdateInstruction.getGdmResponseForScannedData());
        }

        if (additionalInfo.getAuditCompletedQty() == additionalInfo.getAuditQty()) {
            quantityToBeReceivedInVNPK =
                    ReceivingUtils.conversionToVNPK(
                            ReceivingConstants.ONE,
                            ReceivingConstants.Uom.VNPK,
                            documentLine.getVnpkQty(),
                            documentLine.getWhpkQty());
        }


        dataForUpdateInstruction.setQuantityToBeReceivedInEaches(quantityToBeReceivedInEaches);
        dataForUpdateInstruction.setQuantityToBeReceivedInVNPK(quantityToBeReceivedInVNPK);
        deliveryDocumentLine4mDB.setAdditionalInfo(additionalInfo);
        deliveryDocument4mDB.setDeliveryDocumentLines(
                Collections.singletonList(deliveryDocumentLine4mDB));
        dataForUpdateInstruction.getInstruction().setDeliveryDocument(gson.toJson(deliveryDocument4mDB));
        verifyIfCaseCanBeReceived(scannedDataMap, dataForUpdateInstruction);
    }
}