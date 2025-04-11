package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ASNReceivingAuditLoggerTest {

  @Mock private VendorValidator rdcVendorValidator;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private ASNReceivingAuditLogger aSNReceivingAuditLogger;

  @BeforeMethod
  public void setUp() {
    TenantContext.setFacilityNum(32897);
    MockitoAnnotations.initMocks(this);
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.VENDOR_VALIDATOR), any(Class.class));
  }

  @Test
  public void testLog() throws IOException {
    when(rdcVendorValidator.isPilotVendorForAsnReceiving(anyString())).thenReturn(true);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    aSNReceivingAuditLogger.log(deliveryDocuments, instructionRequest);
  }

  @Test
  public void testIsVendorEnabledForAsnReceiving() throws IOException {
    when(rdcVendorValidator.isPilotVendorForAsnReceiving(anyString())).thenReturn(true);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    boolean result =
        aSNReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            deliveryDocuments.get(0), instructionRequest);
    Assert.assertEquals(result, true);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testIsVendorEnabledForAsnReceivingThrowsError() throws IOException {
    when(rdcVendorValidator.isPilotVendorForAsnReceiving(anyString())).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();

    aSNReceivingAuditLogger.isVendorEnabledForAsnReceiving(
        deliveryDocuments.get(0), instructionRequest);
  }

  @Test
  public void testisVendorEnabledForAtlasDsdcAsnReceiving_PilotVendorEnabled() throws IOException {
    when(rdcVendorValidator.isPilotVendorForDsdcAsnReceiving(anyString())).thenReturn(true);

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    InstructionRequest instructionRequest = MockInstructionRequest.getSSCCInstructionRequest();
    deliveryDocuments.get(0).setPoTypeCode(ReceivingConstants.DSDC_PO_TYPE_CODE);
    boolean result =
        aSNReceivingAuditLogger.isVendorEnabledForAtlasDsdcAsnReceiving(
            deliveryDocuments.get(0), instructionRequest);
    Assert.assertEquals(result, true);
  }

  @Test
  public void testisVendorEnabledForAtlasDsdcAsnReceiving_PilotVendorDisabled() throws IOException {
    when(rdcVendorValidator.isPilotVendorForDsdcAsnReceiving(anyString())).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    InstructionRequest instructionRequest = MockInstructionRequest.getSSCCInstructionRequest();
    deliveryDocuments.get(0).setPoTypeCode(ReceivingConstants.DSDC_PO_TYPE_CODE);
    boolean result =
        aSNReceivingAuditLogger.isVendorEnabledForAtlasDsdcAsnReceiving(
            deliveryDocuments.get(0), instructionRequest);
    Assert.assertEquals(result, false);
  }

  @Test
  public void testisVendorEnabledForAtlasDsdcAsnReceiving() throws IOException {
    when(rdcVendorValidator.isPilotVendorForDsdcAsnReceiving(anyString())).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    InstructionRequest instructionRequest = MockInstructionRequest.getSSCCInstructionRequest();
    boolean result =
        aSNReceivingAuditLogger.isVendorEnabledForAtlasDsdcAsnReceiving(
            deliveryDocuments.get(0), instructionRequest);
    Assert.assertEquals(result, false);
  }
}
