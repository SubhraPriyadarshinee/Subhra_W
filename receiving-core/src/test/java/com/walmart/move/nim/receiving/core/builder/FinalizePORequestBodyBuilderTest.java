package com.walmart.move.nim.receiving.core.builder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.service.GDMOSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FinalizePORequestBodyBuilderTest {

  @Mock private ReceiptService receiptService;

  @Spy private OSDRCalculator osdrCalculator = new OSDRCalculator();

  @Spy private GDMOSDRCalculator gdmosdrCalculator = new GDMOSDRCalculator();

  @Mock private OSDRRecordCountAggregator osdrRecordCountAggregator;

  @InjectMocks private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void before() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(8852);
  }

  @BeforeMethod
  public void after() {
    reset(receiptService);
    reset(osdrCalculator);
    reset(gdmosdrCalculator);
    reset(osdrRecordCountAggregator);
    reset(tenantSpecificConfigReader);
  }

  // @Test
  public void testBuild_finalize_with_out_receiving() throws ReceivingException {

    doReturn(null)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Map<POPOLineKey, ReceivingCountSummary> mockMap = new LinkedHashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(30);
    receivingCountSummary.setTotalFBQtyUOM("ZA");
    receivingCountSummary.setDamageQty(1);
    receivingCountSummary.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary.setDamageQtyUOM("ZA");
    receivingCountSummary.setRejectedQty(1);
    receivingCountSummary.setRejectedQtyUOM("ZA");
    receivingCountSummary.setProblemQty(1);
    receivingCountSummary.setProblemQtyUOM("ZA");
    receivingCountSummary.setTotalBolFbq(250);
    receivingCountSummary.setVnpkQty(1);
    receivingCountSummary.setWhpkQty(1);

    ReceivingCountSummary receivingCountSummary1 = new ReceivingCountSummary();
    receivingCountSummary1.setTotalFBQty(30);
    receivingCountSummary1.setTotalFBQtyUOM("ZA");
    receivingCountSummary1.setDamageQty(1);
    receivingCountSummary1.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary1.setDamageQtyUOM("ZA");
    receivingCountSummary1.setRejectedQty(1);
    receivingCountSummary1.setRejectedQtyUOM("ZA");
    receivingCountSummary1.setProblemQty(1);
    receivingCountSummary1.setProblemQtyUOM("ZA");
    receivingCountSummary1.setTotalBolFbq(250);
    receivingCountSummary1.setVnpkQty(1);
    receivingCountSummary1.setWhpkQty(2);

    ReceivingCountSummary receivingCountSummary2 = new ReceivingCountSummary();
    receivingCountSummary2.setTotalFBQty(30);
    receivingCountSummary2.setTotalFBQtyUOM("ZA");
    receivingCountSummary2.setDamageQty(1);
    receivingCountSummary2.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary2.setDamageQtyUOM("ZA");
    receivingCountSummary2.setRejectedQty(1);
    receivingCountSummary2.setRejectedQtyUOM("ZA");
    receivingCountSummary2.setProblemQty(1);
    receivingCountSummary2.setProblemQtyUOM("ZA");
    receivingCountSummary2.setTotalBolFbq(250);
    receivingCountSummary2.setVnpkQty(2);
    receivingCountSummary2.setWhpkQty(2);

    mockMap.put(new POPOLineKey("1234567", 1), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 2), receivingCountSummary1);
    mockMap.put(new POPOLineKey("1234567", 3), receivingCountSummary2);

    doReturn(mockMap)
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);

    Map<String, Object> mockHeaders = new HashMap<>();
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(12345l, "1234567", mockHeaders);

    assertNotNull(finalizePORequestBody);

    assertNull(finalizePORequestBody.getFinalizedTime());
    assertNull(finalizePORequestBody.getUserId());

    assertEquals(finalizePORequestBody.getTotalBolFbq(), 250);

    assertEquals(finalizePORequestBody.getLines().size(), 3);
    assertEquals(finalizePORequestBody.getRcvdQty(), 0);
    assertEquals(finalizePORequestBody.getRcvdQtyUom(), "ZA");

    assertEquals(finalizePORequestBody.getDamage().getQuantity().intValue(), 3);
    assertEquals(finalizePORequestBody.getDamage().getUom(), "ZA");

    assertEquals(finalizePORequestBody.getReject().getQuantity().intValue(), 3);
    assertEquals(finalizePORequestBody.getReject().getUom(), "ZA");

    assertEquals(finalizePORequestBody.getShortage().getQuantity().intValue(), 84);
    assertEquals(finalizePORequestBody.getShortage().getUom(), "ZA");

    assertNull(finalizePORequestBody.getOverage());

    for (int i = 0; i < 3; i++) {

      assertTrue(finalizePORequestBody.getLines().get(i).getLineNumber() > 0);

      assertEquals(finalizePORequestBody.getLines().get(i).getRcvdQty(), 0);
      assertEquals(finalizePORequestBody.getLines().get(i).getRcvdQtyUom(), "ZA");

      assertEquals(
          finalizePORequestBody.getLines().get(i).getShortage().getQuantity().intValue(), 28);
      assertEquals(finalizePORequestBody.getLines().get(i).getShortage().getUom(), "ZA");
      assertEquals(
          finalizePORequestBody.getLines().get(i).getShortage().getCode(), OSDRCode.S10.name());

      assertEquals(finalizePORequestBody.getLines().get(i).getDamage().getQuantity().intValue(), 1);
      assertEquals(finalizePORequestBody.getLines().get(i).getDamage().getUom(), "ZA");

      assertEquals(finalizePORequestBody.getLines().get(i).getReject().getQuantity().intValue(), 1);
      assertEquals(finalizePORequestBody.getLines().get(i).getReject().getUom(), "ZA");
    }

    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    verify(osdrRecordCountAggregator, times(1)).getReceivingCountSummary(anyLong(), any(Map.class));
    verify(osdrCalculator, times(3)).calculate(any(ReceivingCountSummary.class));
    verify(gdmosdrCalculator, times(4)).calculate(any(ReceivingCountSummary.class));
  }

  // @Test
  public void testBuild_finalize_with_out_receiving_zeroQuantityMasterReceipt()
      throws ReceivingException {

    doReturn(null)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Map<POPOLineKey, ReceivingCountSummary> mockMap = new LinkedHashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(30);
    receivingCountSummary.setTotalFBQtyUOM("ZA");
    receivingCountSummary.setDamageQty(1);
    receivingCountSummary.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary.setDamageQtyUOM("ZA");
    receivingCountSummary.setRejectedQty(1);
    receivingCountSummary.setRejectedQtyUOM("ZA");
    receivingCountSummary.setProblemQty(1);
    receivingCountSummary.setProblemQtyUOM("ZA");
    receivingCountSummary.setTotalBolFbq(250);
    receivingCountSummary.setVnpkQty(1);
    receivingCountSummary.setWhpkQty(1);

    ReceivingCountSummary receivingCountSummary1 = new ReceivingCountSummary();
    receivingCountSummary1.setTotalFBQty(30);
    receivingCountSummary1.setTotalFBQtyUOM("ZA");
    receivingCountSummary1.setDamageQty(1);
    receivingCountSummary1.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary1.setDamageQtyUOM("ZA");
    receivingCountSummary1.setRejectedQty(1);
    receivingCountSummary1.setRejectedQtyUOM("ZA");
    receivingCountSummary1.setProblemQty(1);
    receivingCountSummary1.setProblemQtyUOM("ZA");
    receivingCountSummary1.setTotalBolFbq(250);
    receivingCountSummary1.setVnpkQty(1);
    receivingCountSummary1.setWhpkQty(2);

    ReceivingCountSummary receivingCountSummary2 = new ReceivingCountSummary();
    receivingCountSummary2.setTotalFBQty(30);
    receivingCountSummary2.setTotalFBQtyUOM("ZA");
    receivingCountSummary2.setDamageQty(1);
    receivingCountSummary2.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary2.setDamageQtyUOM("ZA");
    receivingCountSummary2.setRejectedQty(1);
    receivingCountSummary2.setRejectedQtyUOM("ZA");
    receivingCountSummary2.setProblemQty(1);
    receivingCountSummary2.setProblemQtyUOM("ZA");
    receivingCountSummary2.setTotalBolFbq(250);
    receivingCountSummary2.setVnpkQty(2);
    receivingCountSummary2.setWhpkQty(2);

    mockMap.put(new POPOLineKey("1234567", 1), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 2), receivingCountSummary1);
    mockMap.put(new POPOLineKey("1234567", 3), receivingCountSummary2);

    doReturn(mockMap)
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    Map<String, Object> mockHeaders = new HashMap<>();
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(12345l, "1234567", mockHeaders);

    assertNotNull(finalizePORequestBody);

    assertNull(finalizePORequestBody.getFinalizedTime());
    assertNull(finalizePORequestBody.getUserId());

    assertEquals(finalizePORequestBody.getTotalBolFbq(), 250);

    assertEquals(finalizePORequestBody.getLines().size(), 3);
    assertEquals(finalizePORequestBody.getRcvdQty(), 0);
    assertEquals(finalizePORequestBody.getRcvdQtyUom(), "ZA");

    assertEquals(finalizePORequestBody.getDamage().getQuantity().intValue(), 3);
    assertEquals(finalizePORequestBody.getDamage().getUom(), "ZA");

    assertEquals(finalizePORequestBody.getReject().getQuantity().intValue(), 3);
    assertEquals(finalizePORequestBody.getReject().getUom(), "ZA");

    assertEquals(finalizePORequestBody.getShortage().getQuantity().intValue(), 84);
    assertEquals(finalizePORequestBody.getShortage().getUom(), "ZA");

    assertNull(finalizePORequestBody.getOverage());

    for (int i = 0; i < 3; i++) {

      assertTrue(finalizePORequestBody.getLines().get(i).getLineNumber() > 0);

      assertEquals(finalizePORequestBody.getLines().get(i).getRcvdQty(), 0);
      assertEquals(finalizePORequestBody.getLines().get(i).getRcvdQtyUom(), "ZA");

      assertEquals(
          finalizePORequestBody.getLines().get(i).getShortage().getQuantity().intValue(), 28);
      assertEquals(finalizePORequestBody.getLines().get(i).getShortage().getUom(), "ZA");
      assertEquals(
          finalizePORequestBody.getLines().get(i).getShortage().getCode(), OSDRCode.S10.name());

      assertEquals(finalizePORequestBody.getLines().get(i).getDamage().getQuantity().intValue(), 1);
      assertEquals(finalizePORequestBody.getLines().get(i).getDamage().getUom(), "ZA");

      assertEquals(finalizePORequestBody.getLines().get(i).getReject().getQuantity().intValue(), 1);
      assertEquals(finalizePORequestBody.getLines().get(i).getReject().getUom(), "ZA");
    }

    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    verify(osdrRecordCountAggregator, times(1)).getReceivingCountSummary(anyLong(), any(Map.class));
    verify(osdrCalculator, times(3)).calculate(any(ReceivingCountSummary.class));
    verify(gdmosdrCalculator, times(4)).calculate(any(ReceivingCountSummary.class));
  }

  // @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testBuild_finalize_with_out_receiving_noVnpk() throws ReceivingException {

    doReturn(null)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Map<POPOLineKey, ReceivingCountSummary> mockMap = new LinkedHashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(30);
    receivingCountSummary.setTotalFBQtyUOM("ZA");
    receivingCountSummary.setDamageQty(1);
    receivingCountSummary.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary.setDamageQtyUOM("ZA");
    receivingCountSummary.setRejectedQty(1);
    receivingCountSummary.setRejectedQtyUOM("ZA");
    receivingCountSummary.setProblemQty(1);
    receivingCountSummary.setProblemQtyUOM("ZA");
    receivingCountSummary.setTotalBolFbq(250);
    receivingCountSummary.setWhpkQty(1);

    ReceivingCountSummary receivingCountSummary1 = new ReceivingCountSummary();
    receivingCountSummary1.setTotalFBQty(30);
    receivingCountSummary1.setTotalFBQtyUOM("ZA");
    receivingCountSummary1.setDamageQty(1);
    receivingCountSummary1.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary1.setDamageQtyUOM("ZA");
    receivingCountSummary1.setRejectedQty(1);
    receivingCountSummary1.setRejectedQtyUOM("ZA");
    receivingCountSummary1.setProblemQty(1);
    receivingCountSummary1.setProblemQtyUOM("ZA");
    receivingCountSummary1.setTotalBolFbq(250);
    receivingCountSummary1.setWhpkQty(2);

    ReceivingCountSummary receivingCountSummary2 = new ReceivingCountSummary();
    receivingCountSummary2.setTotalFBQty(30);
    receivingCountSummary2.setTotalFBQtyUOM("ZA");
    receivingCountSummary2.setDamageQty(1);
    receivingCountSummary2.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary2.setDamageQtyUOM("ZA");
    receivingCountSummary2.setRejectedQty(1);
    receivingCountSummary2.setRejectedQtyUOM("ZA");
    receivingCountSummary2.setProblemQty(1);
    receivingCountSummary2.setProblemQtyUOM("ZA");
    receivingCountSummary2.setTotalBolFbq(250);
    receivingCountSummary2.setWhpkQty(3);

    mockMap.put(new POPOLineKey("1234567", 1), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 2), receivingCountSummary1);
    mockMap.put(new POPOLineKey("1234567", 3), receivingCountSummary2);

    doReturn(mockMap)
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    Map<String, Object> mockHeaders = new HashMap<>();
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(12345l, "1234567", mockHeaders);
  }

  // @Test
  public void testBuild_finalize_with_partial_receiving() throws ReceivingException {

    doAnswer(
            new Answer<Receipt>() {
              public Receipt answer(InvocationOnMock invocation) {
                String poNum = (String) invocation.getArguments()[1];
                int poLine = (int) invocation.getArguments()[2];

                if (poLine != 1) {
                  return null;
                }
                Receipt receipt1 = new Receipt();
                receipt1.setPurchaseReferenceNumber(poNum);
                receipt1.setPurchaseReferenceLineNumber(poLine);
                receipt1.setQuantity(10);
                receipt1.setQuantityUom("ZA");
                receipt1.setFbRejectedQty(0);
                receipt1.setFbRejectedQtyUOM("ZA");
                receipt1.setFbDamagedQty(1);
                receipt1.setFbDamagedReasonCode(OSDRCode.D10);
                receipt1.setFbDamagedQtyUOM("ZA");
                receipt1.setFbProblemQty(1);
                receipt1.setFbProblemQtyUOM("ZA");

                return receipt1;
              }
            })
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Map<POPOLineKey, ReceivingCountSummary> mockMap = new LinkedHashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(30);
    receivingCountSummary.setTotalFBQtyUOM("ZA");
    receivingCountSummary.setReceiveQty(10);
    receivingCountSummary.setReceiveQtyUOM("ZA");
    receivingCountSummary.setDamageQty(1);
    receivingCountSummary.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary.setDamageQtyUOM("ZA");
    receivingCountSummary.setProblemQty(1);
    receivingCountSummary.setProblemQtyUOM("ZA");
    receivingCountSummary.setTotalBolFbq(250);
    receivingCountSummary.setVnpkQty(1);
    receivingCountSummary.setWhpkQty(1);

    ReceivingCountSummary receivingCountSummary1 = new ReceivingCountSummary();
    receivingCountSummary1.setTotalFBQty(30);
    receivingCountSummary1.setTotalFBQtyUOM("ZA");
    receivingCountSummary1.setDamageQty(1);
    receivingCountSummary1.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary1.setDamageQtyUOM("ZA");
    receivingCountSummary1.setRejectedQty(1);
    receivingCountSummary1.setRejectedQtyUOM("ZA");
    receivingCountSummary1.setProblemQty(1);
    receivingCountSummary1.setProblemQtyUOM("ZA");
    receivingCountSummary1.setTotalBolFbq(250);
    receivingCountSummary1.setVnpkQty(1);
    receivingCountSummary1.setWhpkQty(2);

    ReceivingCountSummary receivingCountSummary2 = new ReceivingCountSummary();
    receivingCountSummary2.setTotalFBQty(30);
    receivingCountSummary2.setTotalFBQtyUOM("ZA");
    receivingCountSummary2.setDamageQty(1);
    receivingCountSummary2.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary2.setDamageQtyUOM("ZA");
    receivingCountSummary2.setRejectedQty(1);
    receivingCountSummary2.setRejectedQtyUOM("ZA");
    receivingCountSummary2.setProblemQty(1);
    receivingCountSummary2.setProblemQtyUOM("ZA");
    receivingCountSummary2.setTotalBolFbq(250);
    receivingCountSummary2.setVnpkQty(2);
    receivingCountSummary2.setWhpkQty(2);

    mockMap.put(new POPOLineKey("1234567", 1), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 2), receivingCountSummary1);
    mockMap.put(new POPOLineKey("1234567", 3), receivingCountSummary2);

    doReturn(mockMap)
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    Map<String, Object> mockHeaders = new HashMap<>();
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(12345l, "1234567", mockHeaders);

    assertNotNull(finalizePORequestBody);

    assertNull(finalizePORequestBody.getFinalizedTime());
    assertNull(finalizePORequestBody.getUserId());

    assertEquals(finalizePORequestBody.getTotalBolFbq(), 250);

    assertEquals(finalizePORequestBody.getLines().size(), 3);

    assertEquals(finalizePORequestBody.getRcvdQty(), 10);
    assertEquals(finalizePORequestBody.getRcvdQtyUom(), "ZA");

    assertEquals(finalizePORequestBody.getDamage().getQuantity().intValue(), 3);
    assertEquals(finalizePORequestBody.getDamage().getUom(), "ZA");

    assertEquals(finalizePORequestBody.getReject().getQuantity().intValue(), 2);
    assertEquals(finalizePORequestBody.getReject().getUom(), "ZA");

    assertEquals(finalizePORequestBody.getShortage().getQuantity().intValue(), 75);
    assertEquals(finalizePORequestBody.getShortage().getUom(), "ZA");

    assertNull(finalizePORequestBody.getOverage());

    for (FinalizePOLine poLine : finalizePORequestBody.getLines()) {

      if (poLine.getLineNumber() == 1) {
        assertEquals(poLine.getLineNumber(), 1);

        assertEquals(poLine.getRcvdQty(), 10);
        assertEquals(poLine.getRcvdQtyUom(), "ZA");

        assertEquals(poLine.getShortage().getQuantity().intValue(), 19);
        assertEquals(poLine.getShortage().getUom(), "ZA");
        assertEquals(poLine.getShortage().getCode(), OSDRCode.S10.name());

        assertEquals(poLine.getDamage().getQuantity().intValue(), 1);
        assertEquals(poLine.getDamage().getUom(), "ZA");

        assertEquals(poLine.getReject().getQuantity().intValue(), 0);
        assertEquals(poLine.getReject().getUom(), "ZA");
      }
    }

    verify(receiptService, times(3))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    verify(osdrRecordCountAggregator, times(1)).getReceivingCountSummary(anyLong(), any(Map.class));
    verify(osdrCalculator, times(3)).calculate(any(ReceivingCountSummary.class));
    verify(gdmosdrCalculator, times(4)).calculate(any(ReceivingCountSummary.class));
  }

  // @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testBuild_finalize_with_partial_receiving_noWhpk() throws ReceivingException {

    doAnswer(
            new Answer<Receipt>() {
              public Receipt answer(InvocationOnMock invocation) {
                String poNum = (String) invocation.getArguments()[1];
                int poLine = (int) invocation.getArguments()[2];

                if (poLine != 1) {
                  return null;
                }
                Receipt receipt1 = new Receipt();
                receipt1.setPurchaseReferenceNumber(poNum);
                receipt1.setPurchaseReferenceLineNumber(poLine);
                receipt1.setQuantity(10);
                receipt1.setQuantityUom("ZA");
                receipt1.setFbRejectedQty(0);
                receipt1.setFbRejectedQtyUOM("ZA");
                receipt1.setFbDamagedQty(1);
                receipt1.setFbDamagedReasonCode(OSDRCode.D10);
                receipt1.setFbDamagedQtyUOM("ZA");
                receipt1.setFbProblemQty(1);
                receipt1.setFbProblemQtyUOM("ZA");

                return receipt1;
              }
            })
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Map<POPOLineKey, ReceivingCountSummary> mockMap = new LinkedHashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(30);
    receivingCountSummary.setTotalFBQtyUOM("ZA");
    receivingCountSummary.setReceiveQty(10);
    receivingCountSummary.setReceiveQtyUOM("ZA");
    receivingCountSummary.setDamageQty(1);
    receivingCountSummary.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary.setDamageQtyUOM("ZA");
    receivingCountSummary.setProblemQty(1);
    receivingCountSummary.setProblemQtyUOM("ZA");
    receivingCountSummary.setTotalBolFbq(250);
    receivingCountSummary.setVnpkQty(1);

    ReceivingCountSummary receivingCountSummary1 = new ReceivingCountSummary();
    receivingCountSummary1.setTotalFBQty(30);
    receivingCountSummary1.setTotalFBQtyUOM("ZA");
    receivingCountSummary1.setDamageQty(1);
    receivingCountSummary1.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary1.setDamageQtyUOM("ZA");
    receivingCountSummary1.setRejectedQty(1);
    receivingCountSummary1.setRejectedQtyUOM("ZA");
    receivingCountSummary1.setProblemQty(1);
    receivingCountSummary1.setProblemQtyUOM("ZA");
    receivingCountSummary1.setTotalBolFbq(250);
    receivingCountSummary1.setVnpkQty(1);

    ReceivingCountSummary receivingCountSummary2 = new ReceivingCountSummary();
    receivingCountSummary2.setTotalFBQty(30);
    receivingCountSummary2.setTotalFBQtyUOM("ZA");
    receivingCountSummary2.setDamageQty(1);
    receivingCountSummary2.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary2.setDamageQtyUOM("ZA");
    receivingCountSummary2.setRejectedQty(1);
    receivingCountSummary2.setRejectedQtyUOM("ZA");
    receivingCountSummary2.setProblemQty(1);
    receivingCountSummary2.setProblemQtyUOM("ZA");
    receivingCountSummary2.setTotalBolFbq(250);
    receivingCountSummary.setVnpkQty(1);

    mockMap.put(new POPOLineKey("1234567", 1), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 2), receivingCountSummary1);
    mockMap.put(new POPOLineKey("1234567", 3), receivingCountSummary2);

    doReturn(mockMap)
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);

    Map<String, Object> mockHeaders = new HashMap<>();
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(12345l, "1234567", mockHeaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testBuildOutOfSyncException() throws ReceivingException {

    doAnswer(
            new Answer<Receipt>() {
              public Receipt answer(InvocationOnMock invocation) {
                String poNum = (String) invocation.getArguments()[1];
                int poLine = (int) invocation.getArguments()[2];

                if (poLine == 1) {
                  Receipt receipt1 = new Receipt();
                  receipt1.setPurchaseReferenceNumber(poNum);
                  receipt1.setPurchaseReferenceLineNumber(1);
                  receipt1.setVnpkQty(10);
                  receipt1.setFbProblemQty(0);
                  receipt1.setFbProblemQtyUOM("ZA");
                  receipt1.setFbDamagedQty(1);
                  receipt1.setFbDamagedQtyUOM("ZA");
                  receipt1.setFbDamagedReasonCode(OSDRCode.D10);
                  return receipt1;
                }
                if (poLine == 2) {
                  Receipt receipt2 = new Receipt();
                  receipt2.setPurchaseReferenceNumber(poNum);
                  receipt2.setPurchaseReferenceLineNumber(2);
                  receipt2.setVnpkQty(10);
                  receipt2.setFbOverQty(1);
                  receipt2.setFbOverQtyUOM("ZA");
                  receipt2.setFbOverReasonCode(OSDRCode.O13);
                  receipt2.setFbProblemQty(0);
                  receipt2.setFbProblemQtyUOM("ZA");
                  receipt2.setFbDamagedQty(1);
                  receipt2.setFbDamagedQtyUOM("ZA");
                  receipt2.setFbDamagedReasonCode(OSDRCode.D10);
                  return receipt2;
                }

                if (poLine == 3) {
                  Receipt receipt3 = new Receipt();
                  receipt3.setPurchaseReferenceNumber(poNum);
                  receipt3.setPurchaseReferenceLineNumber(3);
                  receipt3.setVnpkQty(10);
                  receipt3.setFbShortQty(1);
                  receipt3.setFbShortQtyUOM("ZA");
                  receipt3.setFbShortReasonCode(OSDRCode.S10);
                  receipt3.setFbProblemQty(0);
                  receipt3.setFbProblemQtyUOM("ZA");
                  receipt3.setFbDamagedQty(1);
                  receipt3.setFbDamagedQtyUOM("ZA");
                  receipt3.setFbDamagedReasonCode(OSDRCode.D10);
                  return receipt3;
                }

                return null;
              }
            })
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Map<POPOLineKey, ReceivingCountSummary> mockMap = new HashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setProblemQty(1);
    receivingCountSummary.setProblemQtyUOM("ZA");
    receivingCountSummary.setDamageQty(1);
    receivingCountSummary.setDamageQtyUOM("ZA");
    receivingCountSummary.setDamageReasonCode(OSDRCode.D10.name());
    receivingCountSummary.setOverageQty(1);
    receivingCountSummary.setOverageQtyUOM("ZA");
    receivingCountSummary.setShortageQty(1);
    receivingCountSummary.setShortageQtyUOM("ZA");
    mockMap.put(new POPOLineKey("1234567", 1), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 2), receivingCountSummary);
    mockMap.put(new POPOLineKey("1234567", 3), receivingCountSummary);

    doReturn(mockMap)
        .when(osdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));

    doNothing().when(osdrCalculator).calculate(any(ReceivingCountSummary.class));

    Map<String, Object> mockHeaders = new HashMap<>();
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(12345l, "1234567", mockHeaders);
  }
}
