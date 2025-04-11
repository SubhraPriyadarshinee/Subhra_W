package com.walmart.move.nim.receiving.mfc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryHeaderSearchDetails;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentHeaderSearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentSearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchByStatusRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MFCDeliveryServiceTest extends ReceivingTestBase {

  @InjectMocks private MFCDeliveryService mfcDeliveryService;

  @Mock private SimpleRestConnector simpleRestConnector;
  @Mock private AppConfig appConfig;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(5504);
  }

  @AfterMethod
  public void resetMocks() {}

  @Test
  public void testGroupByPacks_Valid() {

    List<Pack> validPacks = mfcDeliveryService.groupPacksByPackNumber(getPacks("valid"));
    assertEquals(2, validPacks.size());
    validPacks.forEach(
        pack -> {
          assertTrue(Arrays.asList("11111111112", "11111111111").contains(pack.getPackNumber()));
          assertEquals(1, pack.getItems().size());
        });
  }

  @Test
  public void testGroupByPacks_InValid() {

    List<Pack> validPacks = mfcDeliveryService.groupPacksByPackNumber(getPacks("invalid"));
    assertEquals(1, validPacks.size());
    validPacks.forEach(
        pack -> {
          assertTrue(Arrays.asList("11111111111").contains(pack.getPackNumber()));
          assertEquals(2, pack.getItems().size());
        });
  }

  @Test(expectedExceptions = AssertionError.class)
  public void testGroupByPacks_InValid2() {

    List<Pack> validPacks = mfcDeliveryService.groupPacksByPackNumber(getPacks("invalid"));
    // This will fail
    assertEquals(2, validPacks.size());
    validPacks.forEach(
        pack -> {
          assertTrue(Arrays.asList("11111111111", "11111111111").contains(pack.getPackNumber()));
          assertEquals(2, pack.getItems().size());
        });
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      mfcDeliveryService.findDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      AssertJUnit.assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      AssertJUnit.assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }

  private List<Pack> getPacks(String type) {

    List<Pack> packs = new ArrayList<>();
    switch (type) {
      case "valid":
        addPacks(packs, "11111111112", "11111111111");

        return packs;
      case "invalid":
        addPacks(packs, "11111111111", "11111111111");
        return packs;
    }

    return packs;
  }

  private void addPacks(List<Pack> packs, String... pack) {
    Item item =
        Item.builder()
            .itemDescription("hello")
            .itemNumber(123456789L)
            .replenishmentCode("MFC")
            .build();

    Item item1 =
        Item.builder()
            .itemDescription("hello")
            .itemNumber(12345678L)
            .replenishmentCode("MFC")
            .build();

    List<Item> _item1 = new ArrayList<>();
    _item1.add(item);

    List<Item> _item2 = new ArrayList<>();
    _item2.add(item1);

    packs.add(Pack.builder().packNumber(pack[0]).palletNumber(pack[0]).items(_item1).build());
    packs.add(Pack.builder().packNumber(pack[1]).palletNumber(pack[1]).items(_item2).build());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGDMHeaderSearchFetchDeliveries_404Error() throws ReceivingException {
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(any(), any(), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
    mfcDeliveryService.fetchDeliveries(
        GdmDeliverySearchByStatusRequest.builder()
            .criteria(
                DeliveryHeaderSearchDetails.builder()
                    .deliveryStatusList(
                        Arrays.asList(
                            DeliveryStatus.ARV.name(),
                            DeliveryStatus.WRK.name(),
                            DeliveryStatus.OPN.name()))
                    .build())
            .build());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGDMHeaderSearchFetchDeliveries_InternalServerError() throws ReceivingException {
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(any(), any(), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    mfcDeliveryService.fetchDeliveries(
        GdmDeliverySearchByStatusRequest.builder()
            .criteria(
                DeliveryHeaderSearchDetails.builder()
                    .deliveryStatusList(
                        Arrays.asList(
                            DeliveryStatus.ARV.name(),
                            DeliveryStatus.WRK.name(),
                            DeliveryStatus.OPN.name()))
                    .build())
            .build());
  }

  @Test
  public void testGDMHeaderSearchFetchDeliveries() throws ReceivingException, IOException {
    String response =
        FileUtils.readFileToString(
            new ClassPathResource("deliveryData/deliveryHeaderSearchResponse.json").getFile(),
            Charset.defaultCharset());
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(any(), any(), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    DeliveryList deliveryList =
        mfcDeliveryService.fetchDeliveries(
            GdmDeliverySearchByStatusRequest.builder()
                .criteria(
                    DeliveryHeaderSearchDetails.builder()
                        .deliveryStatusList(
                            Arrays.asList(
                                DeliveryStatus.ARV.name(),
                                DeliveryStatus.WRK.name(),
                                DeliveryStatus.OPN.name()))
                        .build())
                .build());
    assertNotNull(deliveryList);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = ReceivingConstants.UNABLE_TO_GET_SHIPMENT_FROM_GDM)
  public void testGetShipmentDetails_NotFound() throws ReceivingException {
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
    List<GDMShipmentHeaderSearchResponse> gdmSearchResponse =
        mfcDeliveryService.getShipmentDetails("1234");
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testGetShipmentDetails_NullRecord() throws ReceivingException {
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    List<GDMShipmentHeaderSearchResponse> gdmSearchResponse =
        mfcDeliveryService.getShipmentDetails("1234");
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = ReceivingConstants.UNABLE_TO_GET_DELIVERY_FROM_GDM)
  public void testGetShipmentDetails_RestClientException() throws ReceivingException {
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                null,
                null,
                null));
    List<GDMShipmentHeaderSearchResponse> gdmSearchResponse =
        mfcDeliveryService.getShipmentDetails("1234");
  }

  @Test
  public void testGetShipmentDetails() throws ReceivingException {
    GDMShipmentSearchResponse response = new GDMShipmentSearchResponse();
    response.setData(new ArrayList<>());
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url");
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    List<GDMShipmentHeaderSearchResponse> gdmSearchResponse =
        mfcDeliveryService.getShipmentDetails("1234");
    assertNotNull(gdmSearchResponse);
  }
}
