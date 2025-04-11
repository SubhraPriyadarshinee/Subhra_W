package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.BulkCompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.CompleteMultipleInstructionResponse;
import org.springframework.http.HttpHeaders;

public interface CompleteMultipleInstructionService {
    CompleteMultipleInstructionResponse complete(
            BulkCompleteInstructionRequest bulkCompleteInstructionRequest,
            HttpHeaders httpHeaders
    ) throws ReceivingException;
}
