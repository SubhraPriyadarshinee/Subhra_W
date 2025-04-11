package com.walmart.move.nim.receiving.core.common;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.OperationalInfo;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class PurchaseReferenceValidatorTest {

  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @InjectMocks private PurchaseReferenceValidator purchaseReferenceValidator;

  private final String deliveryNumber = "3724378";
  private final String poNumber = "6983298493";
  private DeliveryDocumentLine deliveryDocumentLine;

  @BeforeMethod
  public void initMocks() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    MockitoAnnotations.initMocks(this);

    deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber(poNumber);
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setItemNbr(240129l);
    deliveryDocumentLine.setBolWeight(null);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWeightFormatTypeCode(ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
  }

  @AfterMethod
  public void tearDown() {
    reset(receiptService);
    reset(tenantSpecificConfigReader);
  }

  @Test()
  public void testValidatePOConfirmation() {
    try {
      when(tenantSpecificConfigReader.isPoConfirmationFlagEnabled(anyInt())).thenReturn(true);
      when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(true);

      purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, poNumber);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());

      GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_FINALIZED_ERROR);
      String errorMessage = String.format(gdmError.getErrorMessage(), poNumber);

      assertEquals(errorMessage, e.getMessage());
    }
  }

  @Test()
  public void testvalidateVariableWeightWithNullWeight() {
    try {
      purchaseReferenceValidator.validateVariableWeight(deliveryDocumentLine);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(
          InstructionErrorCode.getErrorValue("INVALID_BOL_WEIGHT_ERROR").getErrorMessage(),
          e.getMessage());
    }
  }

  @Test()
  public void testvalidateItemNotOnBOL() {
    try {
      DeliveryDocumentLine deliveryDocumentLine1 = deliveryDocumentLine;
      OperationalInfo operationalInfo = new OperationalInfo();
      operationalInfo.setState("NOT_ON_BOL");
      operationalInfo.setTime("2023-12-08T15:15:59.451Z");
      operationalInfo.setUserId("sysadmin");
      deliveryDocumentLine1.setOperationalInfo(operationalInfo);
      purchaseReferenceValidator.checkIfPOLineNotOnBOL(deliveryDocumentLine1, Boolean.FALSE);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(
          InstructionErrorCode.getErrorValue("ITEM_NOT_ON_BOL_ERROR").getErrorMessage(),
          e.getMessage());
    }
  }

  @Test()
  public void testvalidateVariableWeightWithZeroWeight() {
    try {
      deliveryDocumentLine.setBolWeight(0.0f);
      purchaseReferenceValidator.validateVariableWeight(deliveryDocumentLine);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(
          InstructionErrorCode.getErrorValue("INVALID_BOL_WEIGHT_ERROR").getErrorMessage(),
          e.getMessage());
    }
  }

  @Test()
  public void testvalidateVariableWeightWithValidWeight() {
    try {
      deliveryDocumentLine.setBolWeight(0.834f);
      purchaseReferenceValidator.validateVariableWeight(deliveryDocumentLine);
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test()
  public void testValidateIfReceiveAsCorrection_1() {
    try {
      when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(true);

      doReturn(false)
          .when(tenantSpecificConfigReader)
          .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      instructionRequest.setReceiveAsCorrection(Boolean.FALSE);
      purchaseReferenceValidator.validateReceiveAsCorrection(
          deliveryNumber, poNumber, false, instructionRequest);
      assertTrue(false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getHttpStatus(), HttpStatus.CONFLICT);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(
              InstructionErrorCode.getErrorValue("RCV_AS_CORRECTION_ERROR").getErrorMessage(),
              poNumber));
    }
  }

  @Test()
  public void testValidateIfReceiveAsCorrection_2() {
    try {
      when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(true);
      doReturn(false)
          .when(tenantSpecificConfigReader)
          .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      instructionRequest.setReceiveAsCorrection(Boolean.TRUE);

      purchaseReferenceValidator.validateReceiveAsCorrection(
          deliveryNumber, poNumber, false, instructionRequest);
      assertTrue(true);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  @Test()
  public void testValidateIfReceiveAsCorrection_3() {
    try {
      when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(false);
      doReturn(false)
          .when(tenantSpecificConfigReader)
          .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      instructionRequest.setReceiveAsCorrection(Boolean.FALSE);

      purchaseReferenceValidator.validateReceiveAsCorrection(
          deliveryNumber, poNumber, false, instructionRequest);
      assertTrue(true);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  @Test()
  public void testValidateIfReceiveAsCorrection_4() {
    try {
      when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(false);
      doReturn(false)
          .when(tenantSpecificConfigReader)
          .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      instructionRequest.setReceiveAsCorrection(Boolean.TRUE);

      purchaseReferenceValidator.validateReceiveAsCorrection(
          deliveryNumber, poNumber, false, instructionRequest);
      assertTrue(true);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  @Test()
  public void testValidateReceiveAsCorrection_IsProblemFlow_PoConfirmed()
      throws ReceivingException {
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(true);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    final boolean receiveAsCorrection_Initial = false;
    instructionRequest.setReceiveAsCorrection(receiveAsCorrection_Initial);
    final boolean receiveAsCorrection = instructionRequest.isReceiveAsCorrection();
    assertFalse(receiveAsCorrection);

    purchaseReferenceValidator.validateReceiveAsCorrection(
        deliveryNumber, poNumber, true, instructionRequest);

    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    final boolean receiveAsCorrection_Updated = instructionRequest.isReceiveAsCorrection();
    assertTrue(receiveAsCorrection_Updated);
  }

  @Test()
  public void testValidateReceiveAsCorrection_IsProblemFlow_Po_NOT_Confirmed()
      throws ReceivingException {
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(false);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    final boolean receiveAsCorrection_Initial = false;
    instructionRequest.setReceiveAsCorrection(receiveAsCorrection_Initial);
    final boolean receiveAsCorrection = instructionRequest.isReceiveAsCorrection();
    assertFalse(receiveAsCorrection);

    purchaseReferenceValidator.validateReceiveAsCorrection(
        deliveryNumber, poNumber, true, instructionRequest);

    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    final boolean receiveAsCorrection_Updated = instructionRequest.isReceiveAsCorrection();
    assertFalse(receiveAsCorrection_Updated);
  }

  @Test
  public void testValidateIfReceiveAsCorrection_dcFinCheck() throws ReceivingException {
    when(dcFinRestApiClient.isPoFinalizedInDcFin(anyString(), anyString())).thenReturn(true);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    final boolean receiveAsCorrection_Initial = true;
    instructionRequest.setReceiveAsCorrection(receiveAsCorrection_Initial);
    final boolean receiveAsCorrection = instructionRequest.isReceiveAsCorrection();
    assertTrue(receiveAsCorrection);
    purchaseReferenceValidator.validateReceiveAsCorrection(
        deliveryNumber, poNumber, true, instructionRequest);
    verify(dcFinRestApiClient, times(1)).isPoFinalizedInDcFin(anyString(), anyString());
  }
}
