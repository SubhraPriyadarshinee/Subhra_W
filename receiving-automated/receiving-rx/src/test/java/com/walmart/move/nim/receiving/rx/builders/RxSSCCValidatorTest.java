package com.walmart.move.nim.receiving.rx.builders;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxSSCCValidatorTest {

  @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
  @InjectMocks private RxSSCCValidator rxSSCCValidator;
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testValidateScannedSSCC() throws Exception {
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();

    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rxDeliveryServiceImpl.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    Optional<List<DeliveryDocumentLine>> rxSSCCValidatorResponse =
        rxSSCCValidator.validateScannedSSCC(1234L, "12345678934", httpHeaders);
    assertTrue(rxSSCCValidatorResponse.isPresent());
  }

  @Test
  public void testValidateScannedSSCC_error() throws Exception {
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();

    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryDocument[] deliveryDocumentResponse =
        gson.fromJson(mockResponse, DeliveryDocument[].class);
    deliveryDocumentResponse[0].getDeliveryDocumentLines().get(0).setPalletSSCC("12345678934");
    when(rxDeliveryServiceImpl.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(deliveryDocumentResponse));
    try {
      Optional<List<DeliveryDocumentLine>> rxSSCCValidatorResponse =
          rxSSCCValidator.validateScannedSSCC(1234L, "12345678934", httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SCANNED_SSCC_NOT_VALID);
      assertEquals(
          e.getMessage(), String.format(RxConstants.SCANNED_SSCC_NOT_VALID, "12345678934"));
    }
  }

  @Test
  public void testValidateScannedSSCC_error_multi_sku() throws Exception {
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();

    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryDocument[] deliveryDocumentResponse =
        gson.fromJson(mockResponse, DeliveryDocument[].class);
    deliveryDocumentResponse[0].getDeliveryDocumentLines().get(0).setPalletSSCC("12345678934");
    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setItemNbr(9876l);
    deliveryDocumentLine2.setGtin("00029695410988");
    deliveryDocumentResponse[0].getDeliveryDocumentLines().add(deliveryDocumentLine2);

    when(rxDeliveryServiceImpl.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(deliveryDocumentResponse));
    try {
      Optional<List<DeliveryDocumentLine>> rxSSCCValidatorResponse =
          rxSSCCValidator.validateScannedSSCC(1234L, "12345678934", httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.MULTI_SKU_PALLET);
      assertEquals(e.getMessage(), ReceivingConstants.MULTI_SKU_PALLET);
    }
  }
}
