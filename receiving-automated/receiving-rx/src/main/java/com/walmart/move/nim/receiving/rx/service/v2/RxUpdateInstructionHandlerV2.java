package com.walmart.move.nim.receiving.rx.service.v2;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.service.v2.ProcessInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


@Component
@Slf4j
public class RxUpdateInstructionHandlerV2 extends com.walmart.move.nim.receiving.rx.service.RxUpdateInstructionHandler {

    @Autowired
    InstructionFactory factory;
    @Autowired
    UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Resource
    private UpdateInstructionDataValidator updateInstructionDataValidator;

    public InstructionResponse updateInstruction(Long instructionId,
                                                      UpdateInstructionRequest instructionUpdateRequestFromClient,
                                                      String parentTrackingId,
                                                      HttpHeaders httpHeaders) throws ReceivingException {

        log.info("Got request for instruction update : {} , with headers {} ", instructionUpdateRequestFromClient.toString(), httpHeaders);
        DataHolder dataForUpdateInstruction = updateInstructionServiceHelper.getDataForUpdateInstruction(instructionId,
                instructionUpdateRequestFromClient,
                parentTrackingId);

        log.info("checkIfSwitchToV1 {} ", dataForUpdateInstruction);
        if (!updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(dataForUpdateInstruction.getInstruction())) {
            return super.updateInstruction(
                    instructionId,
                    instructionUpdateRequestFromClient,
                    parentTrackingId,
                    httpHeaders);
        }

        log.info("Get updateInstruction service for the given receiving flow");
        ProcessInstructionService updateInstructionService = factory.getUpdateInstructionService(dataForUpdateInstruction.getReceivingFlow());

        log.info("Validating update instruction request and owner");
        updateInstructionDataValidator.validateInstructionAndInstructionOwner(dataForUpdateInstruction,httpHeaders);

        log.info("Validating User Entered Qty  {} ", instructionUpdateRequestFromClient);
        if (!CollectionUtils.isEmpty(
                instructionUpdateRequestFromClient.getUserEnteredDataList())) {
            return updateInstructionService.validateUserEnteredQty(instructionUpdateRequestFromClient,  dataForUpdateInstruction.getInstruction());
        }

        log.info("Checking that the scanned container info matches the getCurrentNode API response info");
        updateInstructionService.processUpdateInstruction(instructionUpdateRequestFromClient, dataForUpdateInstruction,
                dataForUpdateInstruction.getDeliveryDocument(), dataForUpdateInstruction.getDeliveryDocument().getDeliveryDocumentLines().get(0), true, httpHeaders);

        log.info("Building containers to be persisted to DB and returning updated instruction");
        return updateInstructionService.buildContainerAndUpdateInstruction(instructionUpdateRequestFromClient,dataForUpdateInstruction,parentTrackingId, httpHeaders);
    }

}
