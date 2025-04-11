package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DefaultGetContainerRequestHandlerTest {
  @Mock private ContainerService containerService;

  @InjectMocks private DefaultGetContainerRequestHandler defaultGetContainerRequestHandler;

  HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test_getContainerByTrackingId() throws ReceivingException {
    doThrow(
            new ReceivingException(
                LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
                NOT_FOUND,
                LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
                LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER))
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean(), anyString());

    try {
      defaultGetContainerRequestHandler.getContainerByTrackingId(
          "97123456789", true, "ZA", false, mockHeaders);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE);
      assertEquals(rbde.getDescription(), LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE);
    }
  }
}
