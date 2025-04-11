package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeaders;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.RefreshInstructionHandler;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class RdcRefreshInstructionHandler implements RefreshInstructionHandler {
  private static final Logger logger = LoggerFactory.getLogger(RdcRefreshInstructionHandler.class);

  @Autowired private Gson gson;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private InstructionRepository instructionRepository;

  /**
   * @param instruction
   * @param httpHeaders
   * @return
   */
  @Override
  @InjectTenantFilter
  @Transactional
  public InstructionResponse refreshInstruction(Instruction instruction, HttpHeaders httpHeaders) {
    logger.info("Enter RdcRefreshInstructionHandler with instructionId: {}", instruction.getId());
    TenantContext.get().setRefreshInstrStart(System.currentTimeMillis());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber(String.valueOf(instruction.getDeliveryNumber()));
    instructionRequest.setUpcNumber(instruction.getGtin());
    instructionRequest.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));

    httpHeaders = getForwardableHttpHeaders(httpHeaders);

    Pair<Instruction, List<DeliveryDocument>> refreshExistingInstructionResponse =
        rdcInstructionUtils.validateExistingInstruction(
            instruction, instructionRequest, httpHeaders);

    InstructionResponse refreshInstructionResponse = new InstructionResponseImplNew();
    refreshInstructionResponse.setInstruction(refreshExistingInstructionResponse.getKey());
    refreshInstructionResponse.setDeliveryDocuments(refreshExistingInstructionResponse.getValue());

    TenantContext.get().setRefreshInstrEnd(System.currentTimeMillis());
    calculateAndLogElapsedTimeSummary4RefreshInstruction();

    return refreshInstructionResponse;
  }

  private void calculateAndLogElapsedTimeSummary4RefreshInstruction() {
    long timeTakenForRefreshInstrValidateExistingInstrCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getRefreshInstrValidateExistringInstrCallStart(),
            TenantContext.get().getRefreshInstrValidateExistringInstrCallEnd());

    long timeTakenForRefreshInstrAtlasRcvChkNewInstCanBeCreatedCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedStart(),
            TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedEnd());

    long timeTakenForRefreshInstrUpdateItemDetailsNimRdsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallStart(),
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallEnd());

    long totalTimeTakenForRefreshInstr =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getRefreshInstrStart(), TenantContext.get().getRefreshInstrEnd());

    logger.warn(
        "LatencyCheck RefreshInstruction at ts={} time in "
            + "timeTakenForRefreshInstrValidateExistingInstrCall={}, timeTakenForRefreshInstrAtlasRcvChkNewInstCanBeCreatedCall={}, "
            + "timeTakenForRefreshInstrUpdateItemDetailsNimRdsCall={}, totaltimeTakenForCreateInstr={}, and correlationId={}",
        TenantContext.get().getCreateInstrStart(),
        timeTakenForRefreshInstrValidateExistingInstrCall,
        timeTakenForRefreshInstrAtlasRcvChkNewInstCanBeCreatedCall,
        timeTakenForRefreshInstrUpdateItemDetailsNimRdsCall,
        totalTimeTakenForRefreshInstr,
        TenantContext.getCorrelationId());
  }
}
