package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import org.springframework.http.HttpHeaders;

public interface ProcessInstructionService {

    InstructionResponse validateUserEnteredQty(
            UpdateInstructionRequest instructionUpdateRequestFromClient, Instruction instruction4mDB)
            throws ReceivingException;

    void processUpdateInstruction(
            UpdateInstructionRequest instructionUpdateRequestFromClient,
            DataHolder dataForUpdateInstruction, DeliveryDocument deliveryDocument4mDB,
            DeliveryDocumentLine deliveryDocumentLine4mDB,
            boolean setAuditQty, HttpHeaders httpHeaders) throws ReceivingException;

    InstructionResponseImplNew buildContainerAndUpdateInstruction(
            UpdateInstructionRequest instructionUpdateRequestFromClient,
            DataHolder dataForUpdateInstruction, String parentTrackingId, HttpHeaders httpHeaders);
}