package com.walmart.move.nim.receiving.rx.service.v2;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.v2.CompleteInstructionService;
import com.walmart.move.nim.receiving.core.service.v2.CompleteMultipleInstructionService;
import com.walmart.move.nim.receiving.core.service.v2.CreateInstructionService;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.data.ProblemReceivingServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.rx.service.v2.validation.request.RequestValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Objects;

import static com.walmart.move.nim.receiving.rx.common.RxUtils.isASNReceivingOverrideEligible;

@Component
@Slf4j
public class RxInstructionHandlerV2 extends RxInstructionService {
    @Autowired
    protected Gson gson;
    @Autowired
    CreateInstructionServiceHelper createInstructionServiceHelper;
    @Autowired
    InstructionFactory factory;
    @Resource private CompleteMultipleInstructionService rxCompleteSplitPalletInstructionService;
    @Autowired ProblemReceivingServiceHelper problemReceivingServiceHelper;
    @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
    @Resource private CompleteInstructionService completeInstructionService;

    public InstructionResponse serveInstructionRequest(
            String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {

        boolean isEpcisSmartReceivingEnabled ;

        //TODO - to be refactored after production pilot
        if (createInstructionServiceHelper.isEpcisSmartReceivingEnabledFromClient(httpHeaders)) {
            isEpcisSmartReceivingEnabled = true;
        } else {
            isEpcisSmartReceivingEnabled = tenantSpecificConfigReader.getConfiguredFeatureFlag(
                    TenantContext.getFacilityNum().toString(),
                    RxConstants.IS_EPCIS_SMART_RECV_ENABLED,
                    false);
        }

        log.info("Got request for instruction creation : {} , with headers {} ", instructionRequestString, httpHeaders);
        InstructionRequest instructionRequest =
                gson.fromJson(instructionRequestString, InstructionRequest.class);

        if (!isEpcisSmartReceivingEnabled
                || Arrays.asList(
                        RxReceivingType.UPC.getReceivingType(),
                        RxReceivingType.SPLIT_PALLET_UPC.getReceivingType(),
                        RxReceivingType.UPC_PARTIALS.getReceivingType())
                .contains(instructionRequest.getReceivingType())) { // bau flows
            log.info("[LT] isEpcisSmartReceivingEnabled flag is {} and receiving Type:  {}, falling back to BAU flows",
                    isEpcisSmartReceivingEnabled,
                    instructionRequest.getReceivingType());
            return super.serveInstructionRequest(instructionRequestString, httpHeaders);
        }

        if (Objects.nonNull(instructionRequest.getProblemTagId())){
            FitProblemTagResponse fitProblemTagResponse = problemReceivingServiceHelper.fetchFitResponseForProblemTagId(instructionRequest);
            if(Objects.nonNull(fitProblemTagResponse) && isASNReceivingOverrideEligible(fitProblemTagResponse)){
                return super.serveInstructionRequest(instructionRequestString, httpHeaders);
            }
        }

        // VALIDATION BEGINS HERE [START]
        RequestValidator.validateCreateRequest(instructionRequest, httpHeaders);
        // [END]

        log.info("Check for existing instruction: {}, with header: {}", instructionRequestString, httpHeaders);
        InstructionResponse response = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, httpHeaders);
        if (Objects.nonNull(response)) {
            log.info("Instruction exists for : {}, with header: {}", instructionRequestString, httpHeaders);
            return response;
        }

        log.info("Load data needed for the instruction ");
        DataHolder dataForCreateInstruction = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, httpHeaders);

        if (dataForCreateInstruction.getDeliveryDocuments().isEmpty()) { // bau flows
            log.info(
                    "[LT] (isEpcisDataNotFound && isAutoSwitchEpcisToAsn) || !isEpcisFlagEnabled, falling back to BAU flows");
            return super.serveInstructionRequest(instructionRequestString, httpHeaders);
        }

        log.info("Get the class for creating instruction");
        CreateInstructionService createInstructionService = factory.getCreateInstructionService(dataForCreateInstruction.getReceivingFlow());
        log.info("Validating the DeliveryDocumentLine and Container response from GDM");
        createInstructionService.validateData(dataForCreateInstruction);

        log.info("Validating split pallet");
        createInstructionService.validateData(instructionRequest, dataForCreateInstruction);

        log.info("Creating instruction using {} ", createInstructionService.getClass());
        InstructionResponse instructionResponse = createInstructionService.serveInstruction(instructionRequest, dataForCreateInstruction, httpHeaders);
        return instructionResponse;
    }

    @Override
    public InstructionSummary cancelInstruction(Long instructionId, HttpHeaders httpHeaders)
            throws ReceivingException {
        return super.cancelInstruction(instructionId, httpHeaders);
    }

  public InstructionResponse completeInstruction(
      Long instructionId,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    return completeInstructionService.completeInstruction(
        instructionId, completeInstructionRequest, httpHeaders);
  }

    public CompleteMultipleInstructionResponse bulkCompleteInstructions(
            BulkCompleteInstructionRequest bulkCompleteInstructionRequest,
            HttpHeaders httpHeaders
    ) throws ReceivingException {
        return rxCompleteSplitPalletInstructionService.complete(
                bulkCompleteInstructionRequest, httpHeaders
        );
    }
}
