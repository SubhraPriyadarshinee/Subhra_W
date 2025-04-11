package com.walmart.move.nim.receiving.core.client.slotting;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.StandardCharsets;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SlottingErrorHandlerTest {

  @InjectMocks private SlottingErrorHandler slottingErrorHandler;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test_handle_RestClientResponseException_ClientError4xx() throws Exception {
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_pallet_no_prime_mock_error_response.json");
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            mockErrorResponse,
            HttpStatus.CONFLICT.value(),
            "",
            null,
            mockErrorResponse.getBytes(),
            null);

    try {
      slottingErrorHandler.handle(restClientResponseException);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);
      assertEquals(e.getDescription(), ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }
  }

  @Test
  public void test_handle_RestClientResponseException_no_body_ClientError4xx() {
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(null, HttpStatus.CONFLICT.value(), "", null, null, null);

    try {
      slottingErrorHandler.handle(restClientResponseException);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);
      assertEquals(e.getDescription(), ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }
  }

  @Test
  public void test_handle_RestClientResponseException_ServerError5xx() throws Exception {
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "error fetching URL",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "",
            null,
            "".getBytes(),
            StandardCharsets.UTF_8);

    try {
      slottingErrorHandler.handle(restClientResponseException);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SMART_SLOTTING_NOT_AVAILABLE);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Message = error fetching URL");
    }
  }

  @Test
  public void test_handle_ResourceAccessException() {
    ResourceAccessException resourceAccessException = new ResourceAccessException("MOCK_BODY");
    try {
      slottingErrorHandler.handle(resourceAccessException);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SMART_SLOTTING_NOT_AVAILABLE);
      assertEquals(
          e.getDescription(), "Resource exception from SMART-SLOTTING. Error Message = MOCK_BODY");
    }
  }
}
