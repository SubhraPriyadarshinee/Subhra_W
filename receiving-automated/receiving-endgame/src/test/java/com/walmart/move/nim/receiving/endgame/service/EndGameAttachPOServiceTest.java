package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase.getPurchaseOrder;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.oms.OmsRestApiClient;
import com.walmart.move.nim.receiving.core.client.scheduler.SchedulerRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.endgame.model.AttachPurchaseOrderRequest;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@RunWith(MockitoJUnitRunner.class)
public class EndGameAttachPOServiceTest {

  @Mock private GDMRestApiClient gdmRestApiClient;

  @Mock private OmsRestApiClient omsRestApiClient;

  @Mock private SchedulerRestApiClient schedulerRestApiClient;

  @Mock private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;

  @Mock private AppConfig appConfig;

  @InjectMocks private EndGameAttachPOService endGameAttachPOService;

  @Test
  public void testNullResponseFromFetchPoDetailsBothOMSandGDM()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(
            123123123L,
            Arrays.asList(
                "908321GDM",
                "827632",
                "908321GDM",
                "908321GDM",
                "ppp",
                "kk333",
                "ll666",
                "mm7777",
                "oo888",
                "ee444",
                "qq234"));
    HttpHeaders headers = new HttpHeaders();
    headers.set("facilitynum", "3006");
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    po.setLegacyType("28");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(new DeliveryMetaData().builder().attachedPoNumbers(null).build());
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(9)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(9)).getPODetailsFromOMS(any());
  }

  @Test
  public void testAttaching1P_OMSpo_To_Non3pType28Delivery()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234"));
    HttpHeaders headers = new HttpHeaders();
    headers.set("facilitynum", "3006");
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    po.setLegacyType("40");
    OMPSRJDataItem ompsrjDataItem =
        OMPSRJDataItem.builder()
            .omspo(OMSPo.builder().xrefponbr("82763234").dcnbr("3006").build())
            .build();
    OMSPurchaseOrderResponse omsPurchaseOrderResponse =
        OMSPurchaseOrderResponse.builder()
            .OMPSRJ(OMPSRJ.builder().Data(Arrays.asList(ompsrjDataItem)).build())
            .build();
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(omsRestApiClient.getPODetailsFromOMS("82763234")).thenReturn(omsPurchaseOrderResponse);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(1)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(1)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testAttaching1P_OMSpo_To_3pType28Delivery()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    po.setLegacyType("28");
    OMPSRJDataItem ompsrjDataItem =
        OMPSRJDataItem.builder()
            .omspo(OMSPo.builder().xrefponbr("82763234").dcnbr("3006").build())
            .build();
    OMSPurchaseOrderResponse omsPurchaseOrderResponse =
        OMSPurchaseOrderResponse.builder()
            .OMPSRJ(OMPSRJ.builder().Data(Arrays.asList(ompsrjDataItem)).build())
            .build();
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(omsRestApiClient.getPODetailsFromOMS("82763234")).thenReturn(omsPurchaseOrderResponse);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(1)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(0)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testAttaching3PType28_OMSpo_To_3pType28Delivery()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234GDM"));
    HttpHeaders headers = new HttpHeaders();
    headers.set("facilitynum", "3006");
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    po.setLegacyType("28");
    Subpoext subpoext = Subpoext.builder().potypecd("28").build();
    OMPSRJDataItem ompsrjDataItem =
        OMPSRJDataItem.builder()
            .omspo(
                OMSPo.builder()
                    .subpoext(Arrays.asList(subpoext))
                    .xrefponbr("82763234GDM")
                    .dcnbr("3006")
                    .build())
            .build();
    OMSPurchaseOrderResponse omsPurchaseOrderResponse =
        OMSPurchaseOrderResponse.builder()
            .OMPSRJ(OMPSRJ.builder().Data(Arrays.asList(ompsrjDataItem)).build())
            .build();
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(omsRestApiClient.getPODetailsFromOMS("82763234GDM")).thenReturn(omsPurchaseOrderResponse);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(1)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(1)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testAttaching3PType28_GDMpo_To_3pType28Delivery()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234GDM"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder deliveryPO = getPurchaseOrder();
    deliveryPO.setPoNumber("9999999GDM");
    deliveryPO.setLegacyType("28");
    PurchaseOrder gdmPO = getPurchaseOrder();
    gdmPO.setPoNumber("82763234GDM");
    gdmPO.setLegacyType("28");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(deliveryPO))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getPurchaseOrder("82763234GDM")).thenReturn(gdmPO);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(0)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(1)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testAttaching3PType40_GDMpo_To_3pType28Delivery()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234GDM"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder deliveryPO = getPurchaseOrder();
    deliveryPO.setPoNumber("9999999GDM");
    deliveryPO.setLegacyType("28");
    PurchaseOrder gdmPO = getPurchaseOrder();
    gdmPO.setPoNumber("82763234GDM");
    gdmPO.setLegacyType("40");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(deliveryPO))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getPurchaseOrder("82763234GDM")).thenReturn(gdmPO);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(0)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(0)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testAttaching3PType28_GDMpo_To_Non3pType28Delivery()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234GDM"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder deliveryPO = getPurchaseOrder();
    deliveryPO.setPoNumber("9999999GDM");
    deliveryPO.setLegacyType("40");
    PurchaseOrder gdmPO = getPurchaseOrder();
    gdmPO.setPoNumber("82763234GDM");
    gdmPO.setLegacyType("28");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(deliveryPO))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getPurchaseOrder("82763234GDM")).thenReturn(gdmPO);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(0)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(0)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testWhenPOInDeliveryNotHavingPOType()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("908321GDM", "827632"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(2)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(2)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(0)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testFetchDeliveryDetailsFromGDMFailure()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("908321GDM", "827632"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    Exception e = new GDMRestApiClientException("GDM EXCEPTION", HttpStatus.INTERNAL_SERVER_ERROR);
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenThrow(e);
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    try {
      endGameAttachPOService.attachPOsToDelivery(payload, headers);
    } catch (Exception exception) {
      verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
      verify(gdmRestApiClient, times(0)).getPurchaseOrder(any());
      verify(omsRestApiClient, times(0)).getPODetailsFromOMS(any());
      verify(schedulerRestApiClient, times(0)).appendPoToDelivery(any(), any());
    }
  }

  @Test
  public void testAttaching3PType28_GDMpo_To_Non3pType28DeliveryWithNullDeliveryMetadata()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(123123123L, Arrays.asList("82763234GDM"));
    HttpHeaders headers = new HttpHeaders();
    PurchaseOrder deliveryPO = getPurchaseOrder();
    deliveryPO.setPoNumber("9999999GDM");
    deliveryPO.setLegacyType("40");
    PurchaseOrder gdmPO = getPurchaseOrder();
    gdmPO.setPoNumber("82763234GDM");
    gdmPO.setLegacyType("28");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(deliveryPO))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData().builder().attachedPoNumbers("po12321|po43253|po543534").build());
    when(gdmRestApiClient.getPurchaseOrder("82763234GDM")).thenReturn(gdmPO);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(1)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(0)).getPODetailsFromOMS(any());
    verify(schedulerRestApiClient, times(0)).appendPoToDelivery(any(), any());
  }

  @Test
  public void testNonNullResponseFromFetchPoDetailsBothOMSandGDMWithNullAttachedPODeliveryMetadata()
      throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(
            123123123L,
            Arrays.asList(
                "908321GDM",
                "827632",
                "908321GDM",
                "908321GDM",
                "ppp",
                "kk333",
                "ll666",
                "GDMMM7777",
                "oo888",
                "ee444",
                "qq234"));
    HttpHeaders headers = new HttpHeaders();
    headers.set("facilitynum", "3006");
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    po.setLegacyType("28");
    PurchaseOrder gdmPO = getPurchaseOrder();
    gdmPO.setPoNumber("GDMMM7777");
    gdmPO.setLegacyType("28");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(new DeliveryMetaData().builder().attachedPoNumbers(null).build());
    when(gdmRestApiClient.getPurchaseOrder("GDMMM7777")).thenReturn(gdmPO);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(9)).getPurchaseOrder(any());
    verify(omsRestApiClient, times(8)).getPODetailsFromOMS(any());
  }

  @Test
  public void
      testNonNullResponseFromFetchPoDetailsBothOMSandGDMWithNonNullAttachedPODeliveryMetadata()
          throws GDMRestApiClientException, ReceivingException {
    AttachPurchaseOrderRequest payload =
        new AttachPurchaseOrderRequest(
            123123123L,
            Arrays.asList(
                "908321GDM",
                "827632",
                "908321GDM",
                "908321GDM",
                "ppp",
                "kk333",
                "ll666",
                "mm7777",
                "oo888",
                "ee444",
                "GDMPO234"));
    HttpHeaders headers = new HttpHeaders();
    headers.set("facilitynum", "3006");
    PurchaseOrder po = new PurchaseOrder();
    po.setPoNumber("9999999GDM");
    po.setLegacyType("28");
    PurchaseOrder gdmPO = getPurchaseOrder();
    gdmPO.setPoNumber("GDMPO234");
    gdmPO.setLegacyType("28");
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(payload.getDeliveryNumber(), headers))
        .thenReturn(
            Delivery.builder()
                .deliveryNumber(123123123L)
                .purchaseOrders(Arrays.asList(po))
                .build());
    when(endGameDeliveryMetaDataService.getDeliveryMetaData(anyLong()))
        .thenReturn(
            new DeliveryMetaData()
                .builder()
                .attachedPoNumbers("908321GDM|827632|ppp|kk333")
                .build());
    when(gdmRestApiClient.getPurchaseOrder(any())).thenReturn(gdmPO);
    endGameAttachPOService.attachPOsToDelivery(payload, headers);
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(gdmRestApiClient, times(5)).getPurchaseOrder(any());
  }
}
