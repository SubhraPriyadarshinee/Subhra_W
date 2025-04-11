package com.walmart.move.nim.receiving.core.builder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.assertSame;

import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderLineWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class TemporaryTiHiEnricherTest {

  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;

  @InjectMocks TemporaryTiHiEnricher temporaryTiHiEnricher;

  @BeforeTest
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test_enrich() {

    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(9);
    deliveryItemOverride.setTempPalletHi(9);
    deliveryItemOverride.setVersion(0);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    DeliveryWithOSDRResponse deliveryResponse = new DeliveryWithOSDRResponse();
    deliveryResponse.setDeliveryNumber(123456l);
    PurchaseOrderWithOSDRResponse po = new PurchaseOrderWithOSDRResponse();

    PurchaseOrderLineWithOSDRResponse poLine = new PurchaseOrderLineWithOSDRResponse();
    ItemDetails itemDetails = new ItemDetails();
    itemDetails.setNumber(9876l);
    itemDetails.setPalletTi(5);
    itemDetails.setPalletHi(5);

    poLine.setItemDetails(itemDetails);
    po.getLines().add(poLine);
    List<PurchaseOrderWithOSDRResponse> purchaseOrders =
        new ArrayList<PurchaseOrderWithOSDRResponse>();
    purchaseOrders.add(po);
    deliveryResponse.setPurchaseOrders(purchaseOrders);

    temporaryTiHiEnricher.enrich(deliveryResponse);

    assertSame(
        9,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletHi());
    assertSame(
        9,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletTi());

    assertSame(
        9,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletTiHi()
            .getPalletHi());
    assertSame(
        9,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletTiHi()
            .getPalletTi());
  }

  @Test
  public void test_enrich_noDeliveryItemOverride_exists() {

    doReturn(Optional.empty())
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    DeliveryWithOSDRResponse deliveryResponse = new DeliveryWithOSDRResponse();
    deliveryResponse.setDeliveryNumber(123456l);
    PurchaseOrderWithOSDRResponse po = new PurchaseOrderWithOSDRResponse();

    PurchaseOrderLineWithOSDRResponse poLine = new PurchaseOrderLineWithOSDRResponse();
    ItemDetails itemDetails = new ItemDetails();
    itemDetails.setNumber(9876l);
    itemDetails.setPalletTi(5);
    itemDetails.setPalletHi(5);

    poLine.setItemDetails(itemDetails);
    po.getLines().add(poLine);
    List<PurchaseOrderWithOSDRResponse> purchaseOrders =
        new ArrayList<PurchaseOrderWithOSDRResponse>();
    purchaseOrders.add(po);
    deliveryResponse.setPurchaseOrders(purchaseOrders);

    temporaryTiHiEnricher.enrich(deliveryResponse);

    assertSame(
        5,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletHi());
    assertSame(
        5,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletTi());

    assertSame(
        5,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletTiHi()
            .getPalletHi());
    assertSame(
        5,
        deliveryResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails()
            .getPalletTiHi()
            .getPalletTi());
  }
}
