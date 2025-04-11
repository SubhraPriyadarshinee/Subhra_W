// package com.walmart.move.nim.receiving.core;
//
// import static org.mockito.Mockito.reset;
// import static org.testng.Assert.assertEquals;
// import static org.testng.Assert.assertNull;
//
// import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
// import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
// import com.walmart.move.nim.receiving.core.service.DefaultOpenQtyCalculator;
// import com.walmart.move.nim.receiving.core.service.ReceiptService;
// import io.strati.libs.commons.lang3.tuple.ImmutablePair;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.testng.annotations.AfterMethod;
// import org.testng.annotations.BeforeClass;
// import org.testng.annotations.Test;
//
// public class DefaultOpenQtyCalculatorTest {
//
//  @Mock private ReceiptService receiptService;
//
//  @InjectMocks private DefaultOpenQtyCalculator defaultOpenAndMaxReceivedQtyCalculator;
//
//  @BeforeClass
//  public void setUp() {
//    MockitoAnnotations.initMocks(this);
//  }
//
//  @AfterMethod
//  public void tearDown() {
//    reset(receiptService);
//  }
//
////  @Test
//  public void testGetOpenQtyandReceivedQty() {
//    Long deliveryNumber = 11111L;
//    DeliveryDocument deliveryDocument = new DeliveryDocument();
//    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
//    documentLine.setTotalOrderQty(5);
//    documentLine.setOverageQtyLimit(0);
//    documentLine.setPurchaseReferenceNumber("9763140004");
//    documentLine.setPurchaseReferenceLineNumber(1);
//
//    ImmutablePair<Long, Long> openQtyAndReceivedQty =
//        defaultOpenAndMaxReceivedQtyCalculator.getOpenQtyAndReceivedQty(
//            deliveryNumber, deliveryDocument, documentLine);
//    assertEquals(openQtyAndReceivedQty, ImmutablePair.of(2l, 3l));
//  }
//
////  @Test
//  public void testGetOpenQtyandReceivedQtyWithNull() {
//    Long deliveryNumber = 11111L;
//    DeliveryDocument deliveryDocument = new DeliveryDocument();
//    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
//    ImmutablePair<Long, Long> openQtyAndReceivedQty =
//        defaultOpenAndMaxReceivedQtyCalculator.getOpenQtyAndReceivedQty(
//            deliveryNumber, deliveryDocument, documentLine);
//    assertNull(openQtyAndReceivedQty);
//  }
//
////  @Test
//  public void testGetMaxReceivedQtyWithAllowedOverage() {
//    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
//    documentLine.setTotalOrderQty(5);
//    documentLine.setOverageQtyLimit(2);
//    assertEquals(
//        defaultOpenAndMaxReceivedQtyCalculator.getMaxReceivedQuantity(documentLine),
//        Integer.valueOf(7));
//  }
//
////  @Test
//  public void testGetMaxReceivedQtyWithoutAllowedOverage() {
//    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
//    documentLine.setTotalOrderQty(5);
//    assertEquals(
//        defaultOpenAndMaxReceivedQtyCalculator.getMaxReceivedQuantity(documentLine),
//        Integer.valueOf(5));
//  }
// }
