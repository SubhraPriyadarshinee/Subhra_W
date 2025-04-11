package com.walmart.move.nim.receiving.core.client.dcfin;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.*;
import static com.walmart.move.nim.receiving.utils.common.GdmToDCFinChannelMethodResolver.getDCFinChannelMethod;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_CODE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.dcfin.model.TransactionsItem;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import java.util.ArrayList;
import java.util.List;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** @author k0c0e5k */
public class DcFinUtilTest extends ReceivingTestBase {
  String deliveryDocStr = "";
  DeliveryDocument deliveryDocument = null;

  @BeforeMethod
  public void initMocks() {
    deliveryDocStr =
        "{\"purchaseReferenceNumber\":\"2080002467\",\"financialGroupCode\":\"US\",\"baseDivCode\":\"WM\",\"vendorNumber\":\"490925\",\"deptNumber\":\"95\",\"purchaseCompanyId\":\"1\",\"purchaseReferenceLegacyType\":\"20\",\"poDCNumber\":\"6085\",\"purchaseReferenceStatus\":\"ACTV\",\"deliveryDocumentLines\":[{\"gtin\":\"00078742279091\",\"itemUPC\":\"00078742279091\",\"caseUPC\":\"00078742279091\",\"purchaseReferenceNumber\":\"2080002467\",\"purchaseReferenceLineNumber\":1,\"event\":\"POS REPLEN\",\"purchaseReferenceLineStatus\":\"APPROVED\",\"whpkSell\":3.79,\"vendorPackCost\":3.79,\"currency\":\"\",\"vnpkQty\":1,\"whpkQty\":1,\"orderableQuantity\":1,\"warehousePackQuantity\":1,\"expectedQtyUOM\":\"ZA\",\"openQty\":912,\"expectedQty\":912,\"overageQtyLimit\":182,\"itemNbr\":566795839,\"purchaseRefType\":\"SSTKU\",\"palletTi\":8,\"palletHi\":6,\"vnpkWgtQty\":45.6,\"vnpkWgtUom\":\"LB\",\"vnpkcbqty\":1.264,\"vnpkcbuomcd\":\"CF\",\"color\":\"\",\"size\":\"40PK\",\"isHazmat\":false,\"itemDescription1\":\"GV .5L 40PK WATER\",\"itemDescription2\":\"SHIPPED FROM HVDC\",\"isConveyable\":true,\"itemType\":\"\",\"warehouseRotationTypeCode\":\"2\",\"firstExpiryFirstOut\":false,\"profiledWarehouseArea\":\"\",\"promoBuyInd\":\"N\",\"additionalInfo\":{\"warehouseAreaCode\":\"6\",\"warehouseAreaDesc\":\"DryGrocery\",\"warehouseGroupCode\":\"G\",\"isNewItem\":false,\"profiledWarehouseArea\":\"\",\"weight\":45.6,\"weightQty\":\"\",\"cubeQty\":\"\",\"weightFormatTypeCode\":\"F\",\"weightUOM\":\"LB\",\"weightQtyUom\":\"\",\"cubeUomCode\":\"\",\"warehouseMinLifeRemainingToReceive\":0,\"isHACCP\":false,\"primeSlot\":\"\",\"primeSlotSize\":0,\"handlingCode\":\"\",\"packTypeCode\":\"\",\"isHazardous\":0,\"itemHandlingMethod\":\"\",\"atlasConvertedItem\":true,\"isWholesaler\":false,\"isDefaultTiHiUsed\":false,\"imageUrl\":\"\",\"qtyValidationDone\":false,\"isEpcisEnabledVendor\":false,\"auditQty\":0,\"attpQtyInEaches\":0,\"whpkDimensions\":{\"depth\":18.0,\"width\":13.0,\"height\":8.3},\"auditCompletedQty\":0,\"scannedCaseAttpQty\":0},\"operationalInfo\":{\"state\":\"ACTIVE\"},\"freightBillQty\":912,\"activeChannelMethods\":[],\"dotIdNbr\":\"\",\"department\":\"95\",\"palletSSCC\":\"\",\"packSSCC\":\"\",\"ndc\":\"\",\"quantity\":912,\"shipmentDetailsList\":[],\"manufactureDetails\":[],\"deptNumber\":\"\",\"vendorStockNumber\":\"79091\",\"shippedQtyUom\":\"\",\"totalReceivedQty\":0,\"maxAllowedOverageQtyIncluded\":false,\"lithiumIonVerificationRequired\":false,\"limitedQtyVerificationRequired\":false,\"labelTypeCode\":\"\",\"gtinHierarchy\":[],\"isNewItem\":false,\"autoPopulateReceivingQty\":false,\"enteredQtyUOM\":\"\"}],\"totalPurchaseReferenceQty\":912,\"weight\":41587.2,\"weightUOM\":\"LB\",\"cubeQty\":1152.768,\"cubeUOM\":\"CF\",\"freightTermCode\":\"PRP\",\"deliveryStatus\":\"OPN\",\"poTypeCode\":20,\"totalBolFbq\":912,\"originalFreightType\":\"\",\"purchaseReferenceMustArriveByDate\":\"Jul 10, 2023 12:00:00 AM\",\"deliveryNumber\":21580924,\"sellerId\":\"F55CDC31AB754BB68FE0B39041159D63\",\"sellerType\":\"WM\"}";
    deliveryDocument = new Gson().fromJson(deliveryDocStr, DeliveryDocument.class);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testCreateDcFinAdjustRequest_VTR() {
    final Container container = MockContainer.getContainer();
    final String txId = "txId";
    final DcFinAdjustRequest vtrRequest =
        createDcFinAdjustRequest(container, txId, VTR_REASON_CODE, 0);
    assertNotNull(vtrRequest);
    assertEquals(vtrRequest.getTxnId(), txId);
    final List<TransactionsItem> transactions = vtrRequest.getTransactions();
    final TransactionsItem ti = transactions.get(0);
    assertEquals(ti.getPrimaryQty(), -80); // -ve of actual qty=80
    assertEquals(Integer.parseInt(ti.getReasonCode()), VTR_REASON_CODE);
  }

  @Test
  public void testCreateDcFinAdjustRequest_ReceiveCorrection() {
    final Container container = MockContainer.getContainer();
    final String txId = "txId";
    int quantityDiff = 10;
    final DcFinAdjustRequest vtrRequest =
        createDcFinAdjustRequest(
            container, txId, INVENTORY_RECEIVING_CORRECTION_REASON_CODE, quantityDiff);
    assertNotNull(vtrRequest);
    assertEquals(vtrRequest.getTxnId(), txId);
    final List<TransactionsItem> transactions = vtrRequest.getTransactions();
    final TransactionsItem ti = transactions.get(0);
    assertEquals(ti.getPrimaryQty(), quantityDiff);
    assertEquals(Integer.parseInt(ti.getReasonCode()), INVENTORY_RECEIVING_CORRECTION_REASON_CODE);
  }

  @Test
  public void testCreateDcFinAdjustRequest_ReceiveCorrection_negativeQuantityDiff() {
    final Container container = MockContainer.getContainer();
    final String txId = "txId";
    int quantityDiff = -10;
    final DcFinAdjustRequest vtrRequest =
        createDcFinAdjustRequest(
            container, txId, INVENTORY_RECEIVING_CORRECTION_REASON_CODE, quantityDiff);
    assertNotNull(vtrRequest);
    assertEquals(vtrRequest.getTxnId(), txId);
    final List<TransactionsItem> transactions = vtrRequest.getTransactions();
    final TransactionsItem ti = transactions.get(0);
    assertEquals(ti.getPrimaryQty(), quantityDiff);
    assertEquals(Integer.parseInt(ti.getReasonCode()), INVENTORY_RECEIVING_CORRECTION_REASON_CODE);
  }

  @Test
  public void testValidate_checkViolationsSize_0() {
    try {
      checkViolations(deliveryDocument);
    } catch (Exception e) {
      fail("should not go to exception flow. Error Stack");
      e.printStackTrace();
    }
  }

  @Test
  public void testValidate_checkViolationsSize_1() {
    deliveryDocument.setFreightTermCode("121212");
    try {
      checkViolations(deliveryDocument);
      fail("should not get here, should be in exception flow");
    } catch (ReceivingBadDataException receivingBadDataException) {
      final String description = receivingBadDataException.getDescription();
      assertEquals(
          description, "[Invalid billCode(121212), should be one amongst COL|PPD|COLL|PRP|UN]");
    }
  }

  @Test
  public void testValidate_checkViolationsSize_moreThan_1() {
    deliveryDocument.setPoTypeCode(null);
    deliveryDocument.setFreightTermCode("121212");
    deliveryDocument.setBaseDivisionCode(null);
    try {
      checkViolations(deliveryDocument);
      fail("should not get here, should be in exception flow");
    } catch (ReceivingBadDataException receivingBadDataException) {
      final String description = receivingBadDataException.getDescription();
      assertEquals(
          description,
          "[Invalid docType(null), should be one amongst PO|ASN|CO|STO or Any Number,"
              + " <br/>Invalid billCode(121212), should be one amongst COL|PPD|COLL|PRP|UN,"
              + " <br/>Missing required field BaseDivisionCode(null)]");
    }
  }

  @Test
  public void testValidateDocForDcFin_poTypeCode_valid() {
    ArrayList<String> violations = new ArrayList<>();
    checkSpecificFormatField(
        deliveryDocument.getPoTypeCode(),
        VALID_TYPES_PO_ASN_CO_STO_0_9_$,
        INVALID_DOC_TYPE_MSG,
        violations);

    assertNotNull(violations);
    assertTrue(violations.size() == 0);

    deliveryDocument.setPoTypeCode(100000000);
    checkSpecificFormatField(
        deliveryDocument.getPoTypeCode(),
        VALID_TYPES_PO_ASN_CO_STO_0_9_$,
        INVALID_DOC_TYPE_MSG,
        violations);
    assertNotNull(violations);
    assertTrue(violations.size() == 0);
  }

  @Test
  public void testValidateDocForDcFin_poTypeCode_invalid() {
    ArrayList<String> violations = new ArrayList<>();
    deliveryDocument.setPoTypeCode(null);
    checkSpecificFormatField(
        deliveryDocument.getPoTypeCode(),
        VALID_TYPES_PO_ASN_CO_STO_0_9_$,
        INVALID_DOC_TYPE_MSG,
        violations);

    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(
        violations.get(0),
        "Invalid docType(null), should be one amongst PO|ASN|CO|STO or Any Number");
  }

  @Test
  public void testValidateDocForDcFin_BillCode_valid() {
    ArrayList<String> violations = new ArrayList<>();

    checkSpecificFormatField(
        deliveryDocument.getFreightTermCode(), VALID_BILL_CODES, INVALID_BILL_CODE_MSG, violations);

    assertNotNull(violations);
    assertTrue(violations.size() == 0);
  }

  @Test
  public void testValidateDocForDcFin_BillCode_invalid() {
    ArrayList<String> violations = new ArrayList<>();
    deliveryDocument.setFreightTermCode("121212");

    checkSpecificFormatField(
        deliveryDocument.getFreightTermCode(), VALID_BILL_CODES, INVALID_BILL_CODE_MSG, violations);

    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(
        violations.get(0), "Invalid billCode(121212), should be one amongst COL|PPD|COLL|PRP|UN");
  }

  @Test
  public void testValidateDocForDcFin_BillCode_invalid_null() {
    ArrayList<String> violations = new ArrayList<>();
    deliveryDocument.setFreightTermCode(null);

    checkSpecificFormatField(
        deliveryDocument.getFreightTermCode(), VALID_BILL_CODES, INVALID_BILL_CODE_MSG, violations);

    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(
        violations.get(0), "Invalid billCode(null), should be one amongst COL|PPD|COLL|PRP|UN");
  }

  @Test
  public void testValidateDocForDcFin_QuantityUOM_valid() {

    ArrayList<String> violations = new ArrayList<>();
    final DeliveryDocumentLine delDocLn = deliveryDocument.getDeliveryDocumentLines().get(0);

    checkSpecificFormatField(delDocLn.getQtyUOM(), VALID_QTY_UOM, INVALID_QTY_UOM_MSG, violations);

    assertEquals(violations.size(), 0);
  }

  @Test
  public void testValidateDocForDcFin_QuantityUOM_invalid_null() {
    ArrayList<String> violations = new ArrayList<>();
    final DeliveryDocumentLine delDocLn = deliveryDocument.getDeliveryDocumentLines().get(0);
    delDocLn.setQtyUOM(null);
    checkSpecificFormatField(delDocLn.getQtyUOM(), VALID_QTY_UOM, INVALID_QTY_UOM_MSG, violations);

    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(violations.get(0), "Invalid QtyUOM(null), should be one amongst EA|PH|ZA");
  }

  @Test
  public void testValidateDocForDcFin_QuantityUOM_invalid() {

    ArrayList<String> violations = new ArrayList<>();
    final DeliveryDocumentLine delDocLn = deliveryDocument.getDeliveryDocumentLines().get(0);
    delDocLn.setQtyUOM("INVALID_UOM");
    checkSpecificFormatField(delDocLn.getQtyUOM(), VALID_QTY_UOM, INVALID_QTY_UOM_MSG, violations);

    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(violations.get(0), "Invalid QtyUOM(INVALID_UOM), should be one amongst EA|PH|ZA");
  }

  @Test
  public void testValidateDocForDcFin_purchaseRefType_inboundChannelMethod_invalid() {

    ArrayList<String> violations = new ArrayList<>();
    final DeliveryDocumentLine delDocLn = deliveryDocument.getDeliveryDocumentLines().get(0);
    delDocLn.setPurchaseRefType("InvalidRefTypeInbound");
    checkSpecificFormatField(
        delDocLn.getPurchaseRefType(),
        VALID_PURCHASE_REF_TYPE,
        INVALID_PURCHASE_REF_TYPE_MSG,
        violations);

    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(
        violations.get(0),
        "Invalid inboundChannelMethod(InvalidRefTypeInbound), should be one amongst Staplestock|Crossdock|DSDC|POCON");
  }

  @Test
  public void testValidateDocForDcFin_purchaseRefType_inboundChannelMethod_valid() {

    ArrayList<String> violations = new ArrayList<>();
    final DeliveryDocumentLine delDocLn = deliveryDocument.getDeliveryDocumentLines().get(0);
    checkSpecificFormatField(
        getDCFinChannelMethod(delDocLn.getPurchaseRefType()),
        VALID_PURCHASE_REF_TYPE,
        INVALID_PURCHASE_REF_TYPE_MSG,
        violations);
    assertNotNull(violations);
    assertEquals(violations.size(), 0);
  }

  @Test
  public void testValidateDocForDcFin_checkQuantityDivisible_qty_vnkp_whkp_err() {
    try {
      checkQuantityDivisible(null, 10, 10);
      fail("should hit exception");
    } catch (ReceivingBadDataException e) {
      final String description = e.getDescription();
      assertEquals(description, "Missing required field Quantity(null)");
    }
  }

  @Test
  public void testValidateDocForDcFin_checkQuantityDivisible_vnkp_whkp_err() {
    try {
      checkQuantityDivisible(912, 10, 10);
      fail("should hit exception");
    } catch (ReceivingBadDataException e) {
      final String description = e.getDescription();
      assertEquals(
          description,
          "Quantity(912) should be divisible by (10)VNPK<br/>Quantity(912) should be divisible by (10)WHPK");
    }
  }

  @Test
  public void testValidateDocForDcFin_checkQuantityDivisible_vnkp_err() {
    try {
      checkQuantityDivisible(912, 10, 8);
      fail("should hit exception");
    } catch (ReceivingBadDataException e) {
      final String description = e.getDescription();
      assertEquals(description, "Quantity(912) should be divisible by (10)VNPK");
    }
  }

  @Test
  public void testValidateDocForDcFin_checkQuantityDivisible_whkp_err() {
    try {
      checkQuantityDivisible(912, 8, 10);
      fail("should hit exception");
    } catch (ReceivingBadDataException e) {
      final String description = e.getDescription();
      assertEquals(description, "Quantity(912) should be divisible by (10)WHPK");
    }
  }

  @Test
  public void testValidateDocForDcFin_checkQuantityDivisible_valid() {
    try {
      checkQuantityDivisible(912, 8, 8);
    } catch (ReceivingBadDataException e) {
      fail("should not hit exception");
    }
  }

  @Test
  public void testValidateDocForDcFin_validateRequiredField_valid() {
    ArrayList<String> violations = new ArrayList<>();
    checkRequiredField("baseDivisionCode", deliveryDocument.getBaseDivisionCode(), violations);
    assertEquals(violations.size(), 0);
  }

  @Test
  public void testValidateDocForDcFin_validateRequiredField_invalid_null() {

    ArrayList<String> violations = new ArrayList<>();
    deliveryDocument.setBaseDivisionCode(null);
    checkRequiredField(BASE_DIVISION_CODE1, deliveryDocument.getBaseDivisionCode(), violations);
    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(violations.get(0), "Missing required field BaseDivisionCode(null)");
  }

  @Test
  public void testValidateDocForDcFin_validateRequiredField_invalid() {
    ArrayList<String> violations = new ArrayList<>();
    deliveryDocument.setBaseDivisionCode("  ");
    checkRequiredField(BASE_DIVISION_CODE1, deliveryDocument.getBaseDivisionCode(), violations);
    assertNotNull(violations);
    assertTrue(violations.size() > 0);
    assertEquals(violations.get(0), "Missing required field BaseDivisionCode(  )");
  }
}
