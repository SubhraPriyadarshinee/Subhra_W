package com.walmart.move.nim.receiving.rx.service.v2.validation.request;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Objects;

public class RequestValidator {
    public static void validateCreateRequest(InstructionRequest request, HttpHeaders httpHeaders) throws ReceivingBadDataException {
        if(Objects.isNull(request) || Objects.isNull(httpHeaders)){
            throw new ReceivingBadDataException(ExceptionCodes.CREATE_INSTRUCTION_ERROR,
                    "Request and Headers must not be empty");
        }
        if(Objects.isNull(request.getSscc()) && Objects.isNull(request.getScannedDataList())){
            throw new ReceivingBadDataException(ExceptionCodes.CREATE_INSTRUCTION_ERROR,
                    "SSCC / 2d bar code should be passed");
        }
        else if (Objects.nonNull(request.getScannedDataList())) {
            Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(request.getScannedDataList());
            if (scannedDataMap.size() >= 4)
                RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
        }
        // Add validation for all four attributes
    }




}
