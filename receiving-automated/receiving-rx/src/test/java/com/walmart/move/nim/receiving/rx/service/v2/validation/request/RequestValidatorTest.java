package com.walmart.move.nim.receiving.rx.service.v2.validation.request;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.*;

public class RequestValidatorTest {



    @Test
    public void testValidateCreateRequest_bad_request() {
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> RequestValidator.validateCreateRequest(null, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals("Request and Headers must not be empty", exception.getMessage());

        InstructionRequest request = MockInstruction.getInstructionRequest();
        request.setSscc(null);
        request.setScannedDataList(null);
        Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> RequestValidator.validateCreateRequest(request, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals( "SSCC / 2d bar code should be passed", exception1.getMessage());
    }

    @Test
    public void testValidateCreateRequest_good_request() {
        InstructionRequest request = MockInstruction.getInstructionRequest();
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("200109395464720439");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testLot");
        scannedData4.setApplicationIdentifier("10");
        request.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        RequestValidator.validateCreateRequest(request, MockHttpHeaders.getHeaders());
    }
}