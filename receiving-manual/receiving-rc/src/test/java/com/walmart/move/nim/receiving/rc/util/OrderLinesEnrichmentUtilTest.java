package com.walmart.move.nim.receiving.rc.util;

import static com.walmart.move.nim.receiving.rc.contants.RcConstants.PO;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.RMA;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.RMAT;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.SO;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.SOT;

import com.walmart.move.nim.receiving.rc.model.container.RcContainer;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerAdditionalAttributes;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerItem;
import com.walmart.move.nim.receiving.rc.model.gdm.CarrierInformation;
import com.walmart.move.nim.receiving.rc.model.gdm.GDMItemDetails;
import com.walmart.move.nim.receiving.rc.model.gdm.ReturnOrder;
import com.walmart.move.nim.receiving.rc.model.gdm.ReturnOrderLine;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrderLine;
import java.util.Arrays;
import java.util.Collections;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OrderLinesEnrichmentUtilTest {

  private SalesOrder salesOrderSingleQty;
  private SalesOrder salesOrderMultiQty;
  private OrderLinesEnrichmentUtil orderLinesEnrichmentUtil;
  private RcContainer rcContainer;
  private final String SO_NUMBER = "2000150001600017";
  private final String PO_NUMBER = "10001200024";
  private final String SOT_NUMBER = "67000150067";
  private final String RO_NUMBER1 = "2500014000200001200";
  private final String RO_NUMBER2 = "7700014000200009900";
  private final String RMAT_NUMBER1 = "890001200034";
  private final String RMAT_NUMBER2 = "220001200088";
  private final String GTIN = "00070008000999";

  @BeforeClass
  public void initMocksAndFields() {
    orderLinesEnrichmentUtil = new OrderLinesEnrichmentUtil();
    salesOrderSingleQty =
        SalesOrder.builder()
            .soNumber(SO_NUMBER)
            .lines(
                Collections.singletonList(
                    SalesOrderLine.builder()
                        .lineNumber(10001)
                        .poNumber(PO_NUMBER)
                        .trackingNumber(SOT_NUMBER)
                        .itemDetails(GDMItemDetails.builder().consumableGTIN(GTIN).build())
                        .build()))
            .returnOrders(
                Collections.singletonList(
                    ReturnOrder.builder()
                        .roNumber(RO_NUMBER1)
                        .lines(
                            Collections.singletonList(
                                ReturnOrderLine.builder()
                                    .lineNumber(1)
                                    .soLineNumber(10001)
                                    .carrierInformation(
                                        CarrierInformation.builder()
                                            .trackingNumber(RMAT_NUMBER1)
                                            .build())
                                    .build()))
                        .build()))
            .build();
    salesOrderMultiQty =
        SalesOrder.builder()
            .soNumber(SO_NUMBER)
            .lines(
                Arrays.asList(
                    SalesOrderLine.builder()
                        .lineNumber(10001)
                        .trackingNumber(SOT_NUMBER)
                        .itemDetails(GDMItemDetails.builder().consumableGTIN(GTIN).build())
                        .build(),
                    SalesOrderLine.builder()
                        .lineNumber(10002)
                        .poNumber(PO_NUMBER)
                        .itemDetails(GDMItemDetails.builder().consumableGTIN(GTIN).build())
                        .build()))
            .returnOrders(
                Arrays.asList(
                    ReturnOrder.builder()
                        .roNumber(RO_NUMBER1)
                        .lines(
                            Collections.singletonList(
                                ReturnOrderLine.builder()
                                    .lineNumber(1)
                                    .soLineNumber(10001)
                                    .carrierInformation(
                                        CarrierInformation.builder()
                                            .trackingNumber(RMAT_NUMBER1)
                                            .build())
                                    .build()))
                        .build(),
                    ReturnOrder.builder()
                        .roNumber(RO_NUMBER2)
                        .lines(
                            Collections.singletonList(
                                ReturnOrderLine.builder()
                                    .lineNumber(2)
                                    .soLineNumber(10002)
                                    .carrierInformation(
                                        CarrierInformation.builder()
                                            .trackingNumber(RMAT_NUMBER2)
                                            .build())
                                    .build()))
                        .build()))
            .build();
    rcContainer =
        RcContainer.builder()
            .contents(
                Collections.singletonList(
                    RcContainerItem.builder()
                        .additionalAttributes(RcContainerAdditionalAttributes.builder().build())
                        .build()))
            .build();
  }

  @Test
  public void testEnrichEventWithOrderLines_SOScan() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderSingleQty, SO_NUMBER, SO, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_SOTScan() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderSingleQty, SOT_NUMBER, SOT, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_RMAScan() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderSingleQty, RO_NUMBER1, RMA, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_RMATScan() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderSingleQty, RMAT_NUMBER1, RMAT, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_POScan() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderSingleQty, PO_NUMBER, PO, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_InvalidPackageBarcodeType() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderSingleQty, SO_NUMBER, "INVALID_PACKAGE_BARCODE_TYPE", GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_SOScanMultiQty() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, SO_NUMBER, SO, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_SOTScanMultiQty() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, SOT_NUMBER, SOT, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_RMAScanMultiQty_ScanRMA1() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, RO_NUMBER1, RMA, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_RMAScanMultiQty_ScanRMA2() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, RO_NUMBER2, RMA, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10002));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER2);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(2));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER2);
  }

  @Test
  public void testEnrichEventWithOrderLines_RMATScanMultiQty_ScanRMAT1() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, RMAT_NUMBER1, RMAT, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10001));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER1);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(1));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER1);
  }

  @Test
  public void testEnrichEventWithOrderLines_RMATScanMultiQty_ScanRMAT2() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, RMAT_NUMBER2, RMAT, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10002));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER2);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(2));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER2);
  }

  @Test
  public void testEnrichEventWithOrderLines_POScanMultiQty() {
    orderLinesEnrichmentUtil.enrichEventWithOrderLines(
        salesOrderMultiQty, PO_NUMBER, PO, GTIN, rcContainer);
    Assert.assertEquals(rcContainer.getContents().get(0).getSalesOrderNumber(), SO_NUMBER);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getSalesOrderLineNumber(), new Integer(10002));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderNumber(),
        RO_NUMBER2);
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getReturnOrderLineNumber(),
        new Integer(2));
    Assert.assertEquals(
        rcContainer.getContents().get(0).getAdditionalAttributes().getTrackingNumber(),
        RMAT_NUMBER2);
  }
}
