package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public interface CreateInstructionService {
    InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) throws ReceivingException;

    void validateData(DataHolder dataHolder);

    void validateData(InstructionRequest instructionRequest, DataHolder dataHolder);

}
