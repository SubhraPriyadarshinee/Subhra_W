package com.walmart.move.nim.receiving.core.common.validators;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WeightThresholdValidatorTest extends ReceivingTestBase {
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks WeightThresholdValidator weightThresholdValidator;
  private Gson gson;

  // persistent test data
  private DocumentLine deliveryDocumentLine;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(1582);
    TenantContext.setFacilityCountryCode("US");
    gson = new Gson();
  }

  private DocumentLine getMockDeliveryDocumentLine() {
    DocumentLine documentLine = new DocumentLine();
    documentLine.setItemNumber(65676518L);
    documentLine.setPurchaseReferenceNumber("7657293GDM");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setVnpkQty(10);
    documentLine.setWhpkQty(20);
    documentLine.setVnpkWgtQty(1500.0F);
    documentLine.setVnpkWgtUom(ReceivingConstants.Uom.LB);
    return documentLine;
  }

  @BeforeMethod
  public void setUp() {
    when(tenantSpecificConfigReader.getCcmConfigValue(
            anyInt(), eq(ReceivingConstants.TIHI_WEIGHT_THRESHOLD_POUNDS)))
        .thenReturn(gson.toJsonTree(2000.0F));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.TIHI_WEIGHT_THRESHOLD_VALIDATION_ENABLED))
        .thenReturn(Boolean.TRUE);

    deliveryDocumentLine = getMockDeliveryDocumentLine();
  }

  @Test
  public void testValidate() {
    try {
      // valid pallet weight scenario
      // already received 80 cases
      // to be received 10 more
      // total cases after receiving = 90
      // total weight = 90 * 20.0 pounds = 1800.0 < 2000
      deliveryDocumentLine.setVnpkWgtQty(20.0F);
      weightThresholdValidator.validate(deliveryDocumentLine, 80, 10, ReceivingConstants.Uom.VNPK);

    } catch (ReceivingBadDataException receivingBadDataException) {
      fail();
    }
  }

  @Test
  public void testValidate_InvalidScenario() {
    try {
      // invalid pallet weight scenario
      // already received qty = 100 cases
      // to be received = 20 cases
      // total weight = 2280.0 pounds > 2000.0
      // extra cases ~= ceil(280 / 19.0) = ceil(14.73) ~ 15 cases
      // so allowed cases = 20 - 15 = 5
      deliveryDocumentLine.setVnpkWgtQty(19.0F);
      weightThresholdValidator.validate(deliveryDocumentLine, 100, 20, ReceivingConstants.Uom.VNPK);
      fail();

    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          receivingBadDataException.getErrorCode(),
          ExceptionCodes.WEIGHT_THRESHOLD_EXCEEDED_ERROR_CODE);
      int allowedCases =
          (int) Arrays.stream(receivingBadDataException.getValues()).findFirst().get();
      assertEquals(allowedCases, 5);
    }
  }

  @Test
  public void testValidate_InvalidScenario_NothingAlreadyReceived() {
    try {
      // invalid pallet weight scenario
      // already received qty = 0 cases
      // to be received = 120 cases
      // total weight = 2280.0 pounds > 2000.0
      // extra cases ~= ceil(280 / 19.0) = ceil(14.73) ~ 15 cases
      // so allowed cases = 120 - 15 = 105
      deliveryDocumentLine.setVnpkWgtQty(19.0F);
      weightThresholdValidator.validate(deliveryDocumentLine, 0, 120, ReceivingConstants.Uom.VNPK);
      fail();

    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          receivingBadDataException.getErrorCode(),
          ExceptionCodes.WEIGHT_THRESHOLD_EXCEEDED_ERROR_CODE);
      int allowedCases =
          (int) Arrays.stream(receivingBadDataException.getValues()).findFirst().get();
      assertEquals(allowedCases, 105);
    }
  }

  @Test
  public void testValidate_InvalidWeightUom() {
    try {
      deliveryDocumentLine.setVnpkWgtQty(19.0F);
      deliveryDocumentLine.setVnpkWgtUom("KG");
      weightThresholdValidator.validate(
          deliveryDocumentLine, 100, 120, ReceivingConstants.Uom.VNPK);
      fail();
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          receivingBadDataException.getErrorCode(), ExceptionCodes.WEIGHT_UOM_INVALID_ERROR_CODE);
    }
  }
}
