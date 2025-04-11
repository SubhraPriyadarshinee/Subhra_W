package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryDocumentResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class VendorBasedDeliveryDocumentsSearchHandlerTest {

  @InjectMocks
  private VendorBasedDeliveryDocumentsSearchHandler vendorBasedDeliveryDocumentsSearchHandler;

  @Mock private VendorValidator vendorValidator;
  @Mock private DeliveryService deliveryService;
  @Mock private ASNReceivingAuditLogger asnReceivingAuditLogger;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse =
      new GdmDeliveryDocumentResponse();
  private Gson gson = new Gson();

  @BeforeMethod
  public void beforeMethod() throws IOException {
    TenantContext.setFacilityNum(32818);

    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any(Class.class));

    doReturn(vendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.VENDOR_VALIDATOR), any(Class.class));

    doReturn(true)
        .when(asnReceivingAuditLogger)
        .isVendorEnabledForAsnReceiving(any(DeliveryDocument.class), any(InstructionRequest.class));
  }

  @AfterMethod
  public void tearDown() {
    reset(asnReceivingAuditLogger, tenantSpecificConfigReader);
  }

  @BeforeClass
  public void initMocks() throws IOException {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(vendorBasedDeliveryDocumentsSearchHandler, "gson", gson);

    List<DeliveryDocument> deliveryDocuments1 =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument d = deliveryDocuments1.get(0);
    d.setSourceType("SHIPPER");
    deliveryDocuments.add(deliveryDocuments1.get(0));

    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocuments);
  }

  @Test
  public void testFetchDeliveryDocument_UPC() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();

    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test
  public void testFetchDeliveryDocument_SSCC() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);

    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(Arrays.asList("DC", "FC", "CC")).when(vendorValidator).getInternalAsnSourceTypes();
    doReturn(gdmDeliveryDocumentResponse.getDeliveryDocuments())
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_SSCC_InternalAsn_nosscc()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.LPN);

    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(Arrays.asList("DC", "FC", "CC", "SHIPPER"))
        .when(vendorValidator)
        .getInternalAsnSourceTypes();
    doReturn(Optional.<List>empty())
        .when(deliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_SSCC_InternalAsn_exception()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.LPN);

    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(Arrays.asList("DC", "FC", "CC", "SHIPPER"))
        .when(vendorValidator)
        .getInternalAsnSourceTypes();
    doThrow(new ReceivingException("Mock Exception"))
        .when(deliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_SSCC_InternalAsn_asn_receiving_not_enabled()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.LPN);

    doReturn(false).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(Arrays.asList("DC", "FC", "CC", "SHIPPER"))
        .when(vendorValidator)
        .getInternalAsnSourceTypes();
    doThrow(new ReceivingException("Mock Exception"))
        .when(deliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_UPC_exception() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    ErrorResponse errorResponse = new ErrorResponse("errorCode", "errorMessage");
    doThrow(new ReceivingException(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR, "error"))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_SSCC_exception() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);

    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doThrow(new ReceivingException("Exception"))
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testSearchAsnBySscc() throws IOException, ReceivingException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("00123456789098");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(false).when(vendorValidator).isAsnVendorCheckEnabled();
    doReturn(Arrays.asList("DC", "FC", "CC")).when(vendorValidator).getInternalAsnSourceTypes();

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocuments);
    doReturn(gdmDeliveryDocumentResponse.getDeliveryDocuments())
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    List<DeliveryDocument> deliveryDocumentsResponse =
        vendorBasedDeliveryDocumentsSearchHandler.findDeliveryDocumentBySSCCWithShipmentLinking(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocumentsResponse.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchAsnBySscc_NotSupported() throws IOException, ReceivingException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("00123456789098");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    doReturn(Arrays.asList("12345")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(false).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(true).when(vendorValidator).isAsnVendorCheckEnabled();
    doReturn(Arrays.asList("DC", "FC", "CC")).when(vendorValidator).getInternalAsnSourceTypes();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocuments);
    doReturn(gdmDeliveryDocumentResponse.getDeliveryDocuments())
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    List<DeliveryDocument> deliveryDocumentsResponse =
        vendorBasedDeliveryDocumentsSearchHandler.findDeliveryDocumentBySSCCWithShipmentLinking(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocumentsResponse.size() > 0);
  }

  @Test
  public void test_autoPopulateReceivingQtyFlag_SSCC() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    doReturn(Collections.emptyList()).when(vendorValidator).getAutoPopulateReceiveQtyVendorList();
    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(Arrays.asList("DC", "FC", "CC")).when(vendorValidator).getInternalAsnSourceTypes();
    doReturn(gdmDeliveryDocumentResponse.getDeliveryDocuments())
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
    assertFalse(
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).isAutoPopulateReceivingQty());
  }

  @Test
  public void test_autoPopulateReceivingQtyFlag_SSCC_white_listed_vendor()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setSscc("0012345678909876");
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAutoPopulateReceiveQtyVendorList();
    doReturn(true).when(vendorValidator).isAsnReceivingEnabled();
    doReturn(Arrays.asList("39833")).when(vendorValidator).getAsnEnabledVendorsList();
    doReturn(Arrays.asList("DC", "FC", "CC")).when(vendorValidator).getInternalAsnSourceTypes();
    doReturn(gdmDeliveryDocumentResponse.getDeliveryDocuments())
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(anyString(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
    assertTrue(
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).isAutoPopulateReceivingQty());
  }

  @Test
  public void testFetchDeliveryDocumentByUpc() throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentsFromGdm = MockInstruction.getDeliveryDocuments();
    doReturn(gson.toJson(deliveryDocumentsFromGdm))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            21119003, "00000943037204", MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentsFromGdm = MockInstruction.getDeliveryDocuments();
    doReturn(deliveryDocumentsFromGdm)
        .when(deliveryService)
        .findDeliveryDocumentByItemNumber(anyString(), anyInt(), any());
    List<DeliveryDocument> deliveryDocuments =
        vendorBasedDeliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            "21119003", 943037204, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }
}
