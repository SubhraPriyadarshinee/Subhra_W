package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_TI_HI;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FEFO_FOR_WRTC4_ENABLED;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.core.mock.data.MockTransportationModes;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests to validate methods in instruction utils having logic
 *
 * @author g0k0072
 */
@ActiveProfiles("test")
public class DeliveryDocumentHelperTest extends AbstractTestNGSpringContextTests {

  private Gson gson = new Gson();

  private DeliveryDetails deliveryDetailsSSTK;

  private DeliveryDetails deliveryDetailsCROSSU;

  private DeliveryDocumentLine deliveryDocumentLine;

  private DeliveryDocumentLine deliveryDocumentLine2;
  private com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine
      deliveryDocumentLineV2;
  List<TransportationModes> transportationModes;
  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();

  @Mock private RuleSet itemCategoryRuleSet;

  @InjectMocks private DeliveryDocumentHelper deliveryDocumentHelper = new DeliveryDocumentHelper();
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private LithiumIonLimitedQtyRule lithiumIonLimitedQtyRule;
  @Mock private LimitedQtyRule limitedQtyRule;
  @Mock private LithiumIonRule lithiumIonRule;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");

    deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setItemUpc("00000943037204");
    deliveryDocumentLine.setCaseUpc("00000943037204");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setOrderableQuantity(9);
    deliveryDocumentLine.setWarehousePackQuantity(9);

    TransportationModes transportationModes1 = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes1.setDotHazardousClass(dotHazardousClass);
    Mode mode1 = new Mode();
    mode1.setCode(1);
    transportationModes1.setProperShipping("Lithium Metal BATTERY Contained in Equipment");
    transportationModes1.setPkgInstruction(Arrays.asList("969"));
    transportationModes1.setMode(mode1);

    TransportationModes transportationModes2 = new TransportationModes();
    Mode mode2 = new Mode();
    mode2.setCode(2);
    DotHazardousClass dotHazardousClass2 = new DotHazardousClass();
    dotHazardousClass2.setCode("N/A");
    transportationModes2.setDotHazardousClass(dotHazardousClass2);
    transportationModes2.setProperShipping("Lithium Metal Battery");
    transportationModes2.setPkgInstruction(Arrays.asList("967"));
    transportationModes2.setMode(mode2);

    transportationModes = new ArrayList<>();
    transportationModes.add(transportationModes1);
    transportationModes.add(transportationModes2);

    deliveryDocumentLineV2 =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine();

    deliveryDocumentLineV2.setTransportationModes(transportationModes);
    deliveryDocumentLineV2.setLithiumIonVerifiedOn(
        Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    deliveryDocumentLineV2.setHazmatVerifiedOn(
        Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    deliveryDocumentLineV2.setLimitedQtyVerifiedOn(
        Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
  }

  @BeforeMethod
  public void beforeMethod() {
    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsSSTK.json")
              .getCanonicalPath();
      deliveryDetailsSSTK =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
      String dataPath2 =
          new File("../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetailsCROSSU =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath2))), DeliveryDetails.class);
    } catch (IOException e) {
      assert (false);
    }
  }

  @AfterMethod
  public void resetMocks() {
    reset(itemCategoryRuleSet);
    reset(configUtils);
    reset(limitedQtyRule);
    reset(lithiumIonLimitedQtyRule);
    reset(lithiumIonRule);
  }

  @Test
  public void testGetItemType() {
    String typeCode = null;
    assertEquals(
        deliveryDocumentHelper.getItemType(LabelCode.UN3090.getValue()),
        LithiumIonType.METAL.getValue());
    assertEquals(
        deliveryDocumentHelper.getItemType(LabelCode.UN3091.getValue()),
        LithiumIonType.METAL.getValue());
    assertEquals(
        deliveryDocumentHelper.getItemType(LabelCode.UN3480.getValue()),
        LithiumIonType.ION.getValue());
    assertEquals(
        deliveryDocumentHelper.getItemType(LabelCode.UN3481.getValue()),
        LithiumIonType.ION.getValue());
    assertEquals(deliveryDocumentHelper.getItemType(typeCode), typeCode);
  }

  @Test
  public void testUpdateCommonFieldsInDeliveryDocLine() {
    deliveryDocumentHelper.updateCommonFieldsInDeliveryDocLine(deliveryDocumentLine);
  }

  @Test
  public void testUpdateCommonFieldsInDeliveryDocLine_IQSIntegrationEnabled() {
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED))
        .thenReturn(true);
    deliveryDocumentLine.setTransportationModes(MockTransportationModes.getValidHazmat());
    deliveryDocumentHelper.updateCommonFieldsInDeliveryDocLine(deliveryDocumentLine);
    deliveryDocumentLine.setTransportationModes(null);
    assertEquals(deliveryDocumentLine.getIsHazmat(), Boolean.TRUE);
  }

  @Test
  public void testFullyDaConForNoChannelFlip_SSTK() {
    assertFalse(deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetailsSSTK));
  }

  @Test
  public void testFullyDaConForCancelledPo() {
    deliveryDetailsSSTK
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(POStatus.CNCL.name());
    assertTrue(deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetailsSSTK));
  }

  @Test
  public void testFullyDaConForCancelledPoLine() {
    deliveryDetailsSSTK
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    assertTrue(deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetailsSSTK));
  }

  @Test
  public void testFullyDaConForRejectedPoLine() {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getOperationalInfo()
        .setState(POLineStatus.REJECTED.name());
    assertTrue(deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetailsCROSSU));
  }

  @Test
  public void testFullyDaConFalseForRegulatedItems() {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.emptyList());
    when(itemCategoryRuleSet.validateRuleSet(any())).thenReturn(Boolean.TRUE);
    assertFalse(deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetailsCROSSU));
  }

  @Test
  public void testFullyDaConForNoChannelFlip_CROSSU() {
    assertTrue(deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetailsCROSSU));
  }

  @Test
  public void testDocLineMapper() {
    deliveryDocumentLine2 = deliveryDocumentHelper.docLineMapper(deliveryDocumentLineV2);
    assertTrue(
        deliveryDocumentLine2
            .getTransportationModes()
            .containsAll(deliveryDocumentLineV2.getTransportationModes()));
    assertEquals(
        deliveryDocumentLine2.getLimitedQtyVerifiedOn(),
        deliveryDocumentLineV2.getLimitedQtyVerifiedOn());
    assertEquals(
        deliveryDocumentLine2.getHazmatVerifiedOn(), deliveryDocumentLineV2.getHazmatVerifiedOn());
    assertEquals(
        deliveryDocumentLine2.getLithiumIonVerifiedOn(),
        deliveryDocumentLineV2.getLithiumIonVerifiedOn());
  }

  @Test
  public void test_validatePoStatus_deliveryDocNull() throws ReceivingException {
    deliveryDocumentHelper.validatePoStatus(null);
  }

  @Test
  public void test_validatePoStatus_ccmValueEmpty() {
    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    deliveryDocument.setPurchaseReferenceStatus(POStatus.CNCL.name());
    doReturn("").when(configUtils).getCcmValue(anyInt(), anyString(), anyString());
    try {
      deliveryDocumentHelper.validatePoStatus(deliveryDocument);
    } catch (ReceivingException e) {
      Assert.fail("should not a exception");
    }
  }

  @Test
  public void test_validatePoStatus_cancelled() {
    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    deliveryDocument.setPurchaseReferenceStatus(POStatus.CNCL.name());
    doReturn("CNCL,HISTORY").when(configUtils).getCcmValue(anyInt(), anyString(), anyString());
    try {
      deliveryDocumentHelper.validatePoStatus(deliveryDocument);
      Assert.fail("should not reach here as it has to go to exception flow");
    } catch (ReceivingException e) {
      final ErrorResponse errorResponse = e.getErrorResponse();
      final Object errorMessage = errorResponse.getErrorMessage();
      assertEquals(
          errorMessage,
          "The PO: 4763030227 or PO Line:  for this item has been cancelled. Please see a supervisor for assistance with this item.");
      assertEquals(errorResponse.getErrorCode(), "createInstruction");
      assertEquals(errorResponse.getErrorHeader(), "PO/POL Rejected");
    }
  }

  @Test
  public void test_validatePoStatus_history() {
    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    deliveryDocument.setPurchaseReferenceStatus(POStatus.HISTORY.name());
    doReturn("CNCL,HISTORY").when(configUtils).getCcmValue(anyInt(), anyString(), anyString());
    try {
      deliveryDocumentHelper.validatePoStatus(deliveryDocument);
      Assert.fail("should not reach here as it has to go to exception flow");
    } catch (ReceivingException e) {
      final ErrorResponse errorResponse = e.getErrorResponse();
      final Object errorMessage = errorResponse.getErrorMessage();
      assertEquals(
          errorMessage,
          "The PO: 4763030227 or PO Line:  for this item has been cancelled. Please see a supervisor for assistance with this item.");
      assertEquals(errorResponse.getErrorCode(), "createInstruction");
      assertEquals(errorResponse.getErrorHeader(), "PO/POL Rejected");
    }
  }

  @Test
  public void test_validatePoLineStatus_ccmValueEmpty() {
    DeliveryDocumentLine line =
        MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    line.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    doReturn("").when(configUtils).getCcmValue(anyInt(), anyString(), anyString());
    try {
      deliveryDocumentHelper.validatePoLineStatus(line);
    } catch (ReceivingException e) {
      Assert.fail("should not a exception");
    }
  }

  @Test
  public void test_validatePoLineStatus_CANCELLED() {
    DeliveryDocumentLine line =
        MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    line.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    doReturn("CANCELLED").when(configUtils).getCcmValue(anyInt(), anyString(), anyString());
    try {
      deliveryDocumentHelper.validatePoLineStatus(line);
      Assert.fail("should not reach here as it has to go to exception flow");
    } catch (ReceivingException e) {
      final ErrorResponse errorResponse = e.getErrorResponse();
      final Object errorMessage = errorResponse.getErrorMessage();
      assertEquals(
          errorMessage,
          "The PO: 4763030227 or PO Line: 1 for this item has been cancelled. Please see a supervisor for assistance with this item.");
      assertEquals(errorResponse.getErrorCode(), "createInstruction");
      assertEquals(errorResponse.getErrorHeader(), "PO/POL Rejected");
    }
  }

  @Test
  public void testupdateVendorComplianceReturnsFalseForNullTransportationModes()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance()
            .getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setTransportationModes(null);

    when(lithiumIonLimitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(limitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(lithiumIonRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(true);

    boolean isVendorComplianceRequired =
        deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine);

    assertFalse(isVendorComplianceRequired);

    verify(lithiumIonLimitedQtyRule, times(0)).validateRule(any(DeliveryDocumentLine.class));
    verify(limitedQtyRule, times(0)).validateRule(any(DeliveryDocumentLine.class));
    verify(lithiumIonRule, times(0)).validateRule(any(DeliveryDocumentLine.class));
  }

  @Test
  public void testupdateVendorComplianceReturnsTrueWhenComplianceIsNotMetForLithiumMetalItem()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance()
            .getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(lithiumIonLimitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(limitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(lithiumIonRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(true);

    boolean isVendorComplianceRequired =
        deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine);

    assertTrue(isVendorComplianceRequired);
    assertTrue(deliveryDocumentLine.isLithiumIonVerificationRequired());
    assertFalse(deliveryDocumentLine.isLimitedQtyVerificationRequired());
    assertEquals(
        deliveryDocumentLine.getLabelTypeCode(), ReceivingConstants.LITHIUM_LABEL_CODE_3480);

    verify(lithiumIonLimitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(limitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(lithiumIonRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
  }

  @Test
  public void
      testupdateVendorComplianceReturnsLabelTypeCodeWhenComplianceIsNotMetForLithiumIonItem()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance()
            .getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    TransportationModes transportationModes = deliveryDocumentLine.getTransportationModes().get(0);
    transportationModes.setPkgInstruction(
        Arrays.asList(
            ReceivingConstants.PKG_INSTRUCTION_CODE_966,
            ReceivingConstants.PKG_INSTRUCTION_CODE_967));
    deliveryDocumentLine.setTransportationModes(Collections.singletonList(transportationModes));

    when(lithiumIonLimitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(limitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(lithiumIonRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(true);

    boolean isVendorComplianceRequired =
        deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine);

    assertTrue(isVendorComplianceRequired);
    assertTrue(deliveryDocumentLine.isLithiumIonVerificationRequired());
    assertFalse(deliveryDocumentLine.isLimitedQtyVerificationRequired());
    assertEquals(
        deliveryDocumentLine.getLabelTypeCode(), ReceivingConstants.LITHIUM_LABEL_CODE_3481);

    verify(lithiumIonLimitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(limitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(lithiumIonRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
  }

  @Test
  public void testupdateVendorComplianceReturnsTrueWhenComplianceIsNotMetForLimitedQtyItem()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance()
            .getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(lithiumIonLimitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(limitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(true);
    when(lithiumIonRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);

    boolean isVendorComplianceRequired =
        deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine);

    assertTrue(isVendorComplianceRequired);
    assertTrue(deliveryDocumentLine.isLimitedQtyVerificationRequired());
    assertFalse(deliveryDocumentLine.isLithiumIonVerificationRequired());
    assertNull(deliveryDocumentLine.getLabelTypeCode());

    verify(lithiumIonLimitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(limitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(lithiumIonRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
  }

  @Test
  public void
      testupdateVendorComplianceReturnsFalseWhenComplianceIsMetForBothLithiumAndLimitedQtyItem()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance()
            .getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(lithiumIonLimitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(limitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(lithiumIonRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);

    boolean isVendorComplianceRequired =
        deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine);

    assertFalse(isVendorComplianceRequired);
    assertFalse(deliveryDocumentLine.isLimitedQtyVerificationRequired());
    assertFalse(deliveryDocumentLine.isLithiumIonVerificationRequired());
    assertNull(deliveryDocumentLine.getLabelTypeCode());

    verify(lithiumIonLimitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(limitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(lithiumIonRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
  }

  @Test
  public void testupdateVendorComplianceReturnsFalseWhenComplianceIsMetForLithiumAndLimitedQtyItem()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithLithiumIonAndLimitedQtyCompliance()
            .getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(lithiumIonLimitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(true);
    when(limitedQtyRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);
    when(lithiumIonRule.validateRule(any(DeliveryDocumentLine.class))).thenReturn(false);

    boolean isVendorComplianceRequired =
        deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine);

    assertTrue(isVendorComplianceRequired);
    assertTrue(deliveryDocumentLine.isLimitedQtyVerificationRequired());
    assertTrue(deliveryDocumentLine.isLithiumIonVerificationRequired());
    assertNotNull(deliveryDocumentLine.getLabelTypeCode());
    assertTrue(
        deliveryDocumentLine
            .getLabelTypeCode()
            .equalsIgnoreCase(ReceivingConstants.LITHIUM_LABEL_CODE_3091));

    verify(lithiumIonLimitedQtyRule, times(1)).validateRule(any(DeliveryDocumentLine.class));
    verify(limitedQtyRule, times(0)).validateRule(any(DeliveryDocumentLine.class));
    verify(lithiumIonRule, times(0)).validateRule(any(DeliveryDocumentLine.class));
  }

  @SneakyThrows
  @Test
  public void testIsFirstExpiryFirstOut_BAU_3() {
    // setup
    String warehouseRotationTypeCode = "3";
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.TRUE);

    // execute
    final Boolean firstExpiryFirstOut =
        deliveryDocumentHelper.isFirstExpiryFirstOut(warehouseRotationTypeCode);
    // assert
    assertNotNull(firstExpiryFirstOut);
    assertTrue(firstExpiryFirstOut);
  }

  @SneakyThrows
  @Test
  public void testIsFirstExpiryFirstOut_FlagOffOrMissing_4() {
    // setup
    String warehouseRotationTypeCode = "4";
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.TRUE);

    // execute
    final Boolean firstExpiryFirstOut =
        deliveryDocumentHelper.isFirstExpiryFirstOut(warehouseRotationTypeCode);
    // assert
    assertNotNull(firstExpiryFirstOut);
    assertFalse(firstExpiryFirstOut);
  }

  @SneakyThrows
  @Test
  public void testIsFirstExpiryFirstOut_FlagOn_for_4() {
    // setup
    String warehouseRotationTypeCode = "4";
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(FEFO_FOR_WRTC4_ENABLED)).thenReturn(true);
    // execute
    final Boolean firstExpiryFirstOut =
        deliveryDocumentHelper.isFirstExpiryFirstOut(warehouseRotationTypeCode);
    // assert
    assertNotNull(firstExpiryFirstOut);
    assertTrue(firstExpiryFirstOut);
  }

  @Test
  public void validateTiHi_Nulls() {
    DeliveryDocumentLine deliveryDocumentLineTemp = deliveryDocumentLine;
    deliveryDocumentLineTemp.setPalletTie(null);
    deliveryDocumentLineTemp.setPalletHigh(null);
    try {
      deliveryDocumentHelper.validateTiHi(deliveryDocumentLineTemp);
      fail();
    } catch (ReceivingBadDataException e) {
      final InstructionError invalidTiHi = InstructionErrorCode.getErrorValue(INVALID_TI_HI);
      assertEquals(e.getErrorCode(), invalidTiHi.getErrorCode());
      assertEquals(
          e.getMessage(), "Unable to receive item due to Invalid Ti[null]*Hi[null] in GDM.");
    }
  }

  @Test
  public void validateTiHi_Null_Ti() {
    DeliveryDocumentLine deliveryDocumentLineTemp = deliveryDocumentLine;
    deliveryDocumentLineTemp.setPalletTie(null);
    deliveryDocumentLineTemp.setPalletHigh(4);

    try {
      deliveryDocumentHelper.validateTiHi(deliveryDocumentLineTemp);
      fail();
    } catch (ReceivingBadDataException e) {
      final InstructionError invalidTiHi = InstructionErrorCode.getErrorValue(INVALID_TI_HI);
      assertEquals(e.getErrorCode(), invalidTiHi.getErrorCode());
      assertEquals(e.getMessage(), "Unable to receive item due to Invalid Ti[null]*Hi[4] in GDM.");
    }
  }

  @Test
  public void validateTiHi_Null_Hi() {
    DeliveryDocumentLine deliveryDocumentLineTemp = deliveryDocumentLine;
    deliveryDocumentLineTemp.setPalletTie(5);
    deliveryDocumentLineTemp.setPalletHigh(null);
    try {
      deliveryDocumentHelper.validateTiHi(deliveryDocumentLineTemp);
      fail();
    } catch (ReceivingBadDataException e) {
      final InstructionError invalidTiHi = InstructionErrorCode.getErrorValue(INVALID_TI_HI);
      assertEquals(e.getErrorCode(), invalidTiHi.getErrorCode());
      assertEquals(e.getMessage(), "Unable to receive item due to Invalid Ti[5]*Hi[null] in GDM.");
    }
  }

  @Test
  public void validateTiHi_0_Ti() {
    DeliveryDocumentLine deliveryDocumentLineTemp = deliveryDocumentLine;
    deliveryDocumentLineTemp.setPalletTie(0);
    deliveryDocumentLineTemp.setPalletHigh(4);

    try {
      deliveryDocumentHelper.validateTiHi(deliveryDocumentLineTemp);
      fail();
    } catch (ReceivingBadDataException e) {
      final InstructionError invalidTiHi = InstructionErrorCode.getErrorValue(INVALID_TI_HI);
      assertEquals(e.getErrorCode(), invalidTiHi.getErrorCode());
      assertEquals(e.getMessage(), "Unable to receive item due to Invalid Ti[0]*Hi[4] in GDM.");
    }
  }

  @Test
  public void validateTiHi_0_Hi() {
    DeliveryDocumentLine deliveryDocumentLineTemp = deliveryDocumentLine;
    deliveryDocumentLineTemp.setPalletTie(5);
    deliveryDocumentLineTemp.setPalletHigh(0);
    try {
      deliveryDocumentHelper.validateTiHi(deliveryDocumentLineTemp);
      fail();
    } catch (ReceivingBadDataException e) {
      final InstructionError invalidTiHi = InstructionErrorCode.getErrorValue(INVALID_TI_HI);
      assertEquals(e.getErrorCode(), invalidTiHi.getErrorCode());
      assertEquals(e.getMessage(), "Unable to receive item due to Invalid Ti[5]*Hi[0] in GDM.");
    }
  }

  @Test
  public void validateTiHi_NonZero_Ti_Hi() {
    DeliveryDocumentLine deliveryDocumentLineTemp = deliveryDocumentLine;
    deliveryDocumentLineTemp.setPalletTie(5);
    deliveryDocumentLineTemp.setPalletHigh(4);
    try {
      deliveryDocumentHelper.validateTiHi(deliveryDocumentLineTemp);
    } catch (ReceivingBadDataException e) {
      fail();
    }
  }
}
