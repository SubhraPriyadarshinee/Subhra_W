package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.Error;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsnToDeliveryDocumentsCustomMapperTest {
  private static Gson gson = new Gson();
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @InjectMocks private AsnToDeliveryDocumentsCustomMapper asnToDeliveryDocumentsCustomMapper;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);
  }

  @Test
  public void testMapGdmResponse_PalletSSCC() throws Exception {
    File resource = new ClassPathResource("GdmResponseSSCC.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00909700302232310301",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getNdc(), "43547-282-11");
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        "00909700302232310301");
    assertNull(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPackSSCC(),
        "909899000020014304");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "9091747200");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getDescription());
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .get(0)
            .getQty()
            .intValue(),
        432);
  }

  @Test
  public void testMapGdmResponse_PalletSSCC_NoPoLine() throws Exception {
    File resource = new ClassPathResource("GdmResponseSSCC_NoPoLine.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00909700302232310301",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 2);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getNdc(), "43547-282-11");
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        "00909700302232310301");
    assertNull(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPackSSCC(),
        "909899000020014304");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "9091747200");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getDescription());
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .get(0)
            .getQty()
            .intValue(),
        432);
  }

  @Test
  public void testMapGdmResponse_MultiSkuPalletSSCC() throws Exception {
    File resource = new ClassPathResource("GDMResponseSSCC_MultiSkuPallet.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00000669930006995024",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 3);
  }

  @Test
  public void testMapGdmResponse_PalletSSCC_POOrderedQtyInEa() throws Exception {
    File resource = new ClassPathResource("GdmResponseSSCC.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "32898", ReceivingConstants.CONVERT_QUANTITY_TO_EACHES_FLAG, false))
        .thenReturn(true);
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00909700302232310301",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalOrderQty()
            .intValue(),
        20160);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getQtyUOM(),
        ReceivingConstants.Uom.EACHES);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getNdc(), "43547-282-11");
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        "00909700302232310301");
    assertNull(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPackSSCC(),
        "909899000020014304");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "9091747200");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getDescription());
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .get(0)
            .getQty()
            .intValue(),
        432);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCheckIfPartialResponse() {
    List<Error> errors = new ArrayList<>();
    errors.add(
        new Error("PARTIAL_CONTENT", Arrays.asList("PO not found in delivery : [8458708681]")));

    asnToDeliveryDocumentsCustomMapper.checkIfPartialContent(errors);
  }

  @Test
  public void testMapGdmResponse_PalletSsccMultiLot() throws Exception {
    File resource = new ClassPathResource("GdmResponseSSCCMultiLot.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00909700302232310301",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        2);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getNdc(), "43547-282-11");
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        "00909700302232310301");
    assertNull(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPackSSCC(),
        "909899000020014304");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "9091747200");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getDescription());
  }

  @Test
  public void testMapGdmResponse_PalletSSCC_MultiSKU() throws Exception {
    File resource = new ClassPathResource("GdmMultiSkuPalletResponse.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00909700302232310301",
            new HttpHeaders());
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        "00909700302232310301");
  }

  @Test
  public void testMapGdmResponse_PackSSCC() throws Exception {
    File resource = new ClassPathResource("GdmCaseScanResponse.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "909899000020014304",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getNdc(), "43547-282-11");
    /*    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPackSSCC(),
        "909899000020014304");
    assertNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC());*/
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "9091747200");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getDescription());
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .get(0)
            .getQty()
            .intValue(),
        10);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        36);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(), "ZA");
  }

  @Test
  public void test_checkIfPartialContent() {

    List<Error> errors = new ArrayList<>();
    asnToDeliveryDocumentsCustomMapper.checkIfPartialContent(errors);
  }

  @Test
  public void test_checkIfPartialContent_error_exists() {
    try {
      Error error =
          new Error("PARTIAL_CONTENT", Arrays.asList("PO not found in delivery : [8458708681]"));
      asnToDeliveryDocumentsCustomMapper.checkIfPartialContent(Arrays.asList(error));
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_PARTIAL_SHIPMENT_DATA);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_PARTIAL_RESPONSE);
      assertEquals(e.getDescription(), ReceivingConstants.GDM_PARTIAL_RESPONSE);
    }
  }

  @Test
  public void testMapGdmResponseForGtinAndLotNumberSearchResponse()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response.json")
            .getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());
    assertTrue(!CollectionUtils.isEmpty(mappedDeliveryDocuments));
    assertTrue(mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().size() == 1);
  }

  @Test
  public void testMapGdmResponseForGtinAndLotNumberSearchResponse_v1()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response_v1.json")
            .getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    when(appConfig.getShipmentSearchVersion()).thenReturn(ReceivingConstants.VERSION_V1);
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());
    assertTrue(!CollectionUtils.isEmpty(mappedDeliveryDocuments));
    assertTrue(mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().size() == 1);
  }

  @Test
  public void testMapGdmResponseForGtinAndLotNumberSearchResponse_1()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response_2.json")
            .getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());

    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        90);
  }

  @Test
  public void testMapGdmResponseForGtinAndLotNumberSearchMultiShipment_1()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response_3.json")
            .getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());

    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        3);
    assertSame(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(), "ZA");
    assertEquals(
        mappedDeliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        2);
    assertSame(
        mappedDeliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getShippedQtyUom(), "ZA");
  }

  @Test
  public void testMapGdmResponseForGtinAndLotNumberSearchMultiShipment_4()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response_4.json")
            .getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());

    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        5);
  }

  @Test
  public void testMapGdmResponseForGtinAndLotNumber_multi_lot()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource(
                "gdm_get_shipments_by_gtin_and_lotNumber_success_response_4_multi_lot.json")
            .getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());

    assertFalse(CollectionUtils.isEmpty(mappedDeliveryDocuments));
    assertFalse(CollectionUtils.isEmpty(mappedDeliveryDocuments.get(0).getDeliveryDocumentLines()));
    assertFalse(
        CollectionUtils.isEmpty(
            mappedDeliveryDocuments
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getManufactureDetails()));
    assertEquals(
        mappedDeliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        2);
    assertEquals(
        mappedDeliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .get(0)
            .getLot(),
        "01L032C09B");
    assertEquals(
        mappedDeliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .get(1)
            .getLot(),
        "01L032C09A");
  }

  @Test
  public void testMapGdmResponseForSSCC_SameSSCCSameItemMultiplePacks()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("sample_asn_po.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());
    assertEquals(mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        7);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void testMapGdmResponseForSSCC_SingleSSCCSameItemVendorPacks()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("gdm_asn_sscc_single_sku.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class), null, new HttpHeaders());
    assertEquals(mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().size(), 2);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        10);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(),
        ReceivingConstants.Uom.VNPK);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(1).getShippedQty().intValue(),
        6);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void testMapGdmResponseForSSCC_InternalAsn() throws ReceivingException, IOException {
    File resource = new ClassPathResource("gdm_asn_sscc_internal_asn.json").getFile();
    String scannedSscc = "06020000704245077935";
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocuments =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            scannedSscc,
            new HttpHeaders());
    assertEquals(mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQty().intValue(),
        384);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(),
        ReceivingConstants.Uom.VNPK);
    assertEquals(
        mappedDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        scannedSscc);
  }

  @Test
  public void testMapGdmResponse_PalletSSCC_AdditionalItemAttributes() throws Exception {
    File resource = new ClassPathResource("gdm_asn_sscc_additionalItemAttributes.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00006641941209544694",
            new HttpHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);

    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC(),
        "00006641941209544694");

    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "0657531161");
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertNotNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getDescription());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWarehouseAreaCode());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWarehouseAreaDesc());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getProfiledWarehouseArea());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWeight());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWeightUOM());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWarehouseMinLifeRemainingToReceive());
    assertNotNull(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWarehouseRotationTypeCode());
  }

  @Test
  public void testMapGdmResponse_PackSSCC_IQS_Enrichment() throws Exception {
    File resource = new ClassPathResource("GdmCaseScanResponseIqsAttributes.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class);
    ItemDetails itemDetails =
        gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class)
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getItemDetails();
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            ssccScanResponse, "00100370001432506927", new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getHandlingCode(),
        itemDetails.getHandlingCode());
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPackTypeCode(),
        itemDetails.getPackType());
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).isNewItem(),
        itemDetails.getIsNewItem().booleanValue());
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getLithiumIonVerifiedOn(),
        itemDetails.getLimitedIonVerifiedOn());
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getLimitedQtyVerifiedOn(),
        itemDetails.getLimitedQtyVerifiedOn());
  }

  @Test
  public void isGtinMatchingWithPOLine() {
    // given
    Pack pack = new Pack();
    pack.setGtin("1234");
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    ItemDetails itemDetails = new ItemDetails();
    itemDetails.setOrderableGTIN("1234");
    purchaseOrderLine.setItemDetails(itemDetails);

    // when then
    assertTrue(
        asnToDeliveryDocumentsCustomMapper.isGtinMatchingWithPOLine(pack, purchaseOrderLine));

    itemDetails = new ItemDetails();
    itemDetails.setConsumableGTIN("1234");
    purchaseOrderLine.setItemDetails(itemDetails);
    assertTrue(
        asnToDeliveryDocumentsCustomMapper.isGtinMatchingWithPOLine(pack, purchaseOrderLine));

    itemDetails = new ItemDetails();
    itemDetails.setWarehousePackGTIN("1234");
    purchaseOrderLine.setItemDetails(itemDetails);
    assertTrue(
        asnToDeliveryDocumentsCustomMapper.isGtinMatchingWithPOLine(pack, purchaseOrderLine));
  }

  @Test
  public void checkIfPoLineExistsinEpcisDoc() {
    // given
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    deliveryDocuments.get(0).setPurchaseReferenceNumber("1");
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    purchaseOrderLine.setPoLineNumber(1);

    // when
    Optional<DeliveryDocumentLine> deliveryDocumentLine =
        asnToDeliveryDocumentsCustomMapper.checkIfPoLineExistsinEpcisDoc(
            deliveryDocuments, purchaseOrderLine, "1");

    // then
    assertTrue(deliveryDocumentLine.isPresent());
  }

  @Test
  public void populateEpcisDocumentLine() {
    // given
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    deliveryDocuments.get(0).setPurchaseReferenceNumber("1");
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    // when
    asnToDeliveryDocumentsCustomMapper.populateEpcisDocumentLine(
        deliveryDocuments, "1", deliveryDocumentLine);
  }

  @Test
  public void populateEpcisDocumentLine_NullLines() {
    // given
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    deliveryDocuments.get(0).setPurchaseReferenceNumber("1");
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocuments.get(0).setDeliveryDocumentLines(null);

    // when
    asnToDeliveryDocumentsCustomMapper.populateEpcisDocumentLine(
        deliveryDocuments, "1", deliveryDocumentLine);
  }

  @Test
  public void populateEpcisShipmentDetails() throws IOException {
    // given
    String mockPacks =
        FileUtils.readFileToString(
            new ClassPathResource("GdmPackResponse.json").getFile(), Charset.defaultCharset());
    SsccScanResponse ssccScanResponse = gson.fromJson(mockPacks, SsccScanResponse.class);

    ShipmentDetails shipmentDetails =
        ShipmentDetails.builder()
            .shipmentNumber("1")
            .inboundShipmentDocId("1")
            .destinationGlobalLocationNumber("1")
            .loadNumber("1")
            .shipperId("1")
            .sourceGlobalLocationNumber("1")
            .build();
    Pack pack = ssccScanResponse.getPacks().get(0);

    Map<String, ShipmentDetails> shipmentDetailsMap =
        Collections.singletonMap(pack.getShipmentNumber(), shipmentDetails);
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    purchaseOrderLine.setOrderedQty(1);
    purchaseOrderLine.setOrderedQtyUom("ZA");

    // when
    asnToDeliveryDocumentsCustomMapper.populateEpcisShipmentDetails(
        shipmentDetailsMap, pack, deliveryDocumentLine, purchaseOrderLine);

    // then
    assertNotNull(deliveryDocumentLine);
  }

  @Test
  public void populateEpcisPacks() throws IOException {
    // given
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    Pack pack = new Pack();

    // when
    asnToDeliveryDocumentsCustomMapper.populateEpcisPacks(deliveryDocumentLine, pack);

    // then
    assertNotNull(deliveryDocumentLine);
  }

  @Test
  public void testMapGdmResponse_PalletSSCCEpcis() throws IOException, ReceivingException {
    // given
    File resource = new ClassPathResource("GdmResponseSSCC.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class);
    PurchaseOrder purchaseOrder = ssccScanResponse.getPurchaseOrders().get(0);
    purchaseOrder.getVendorInformation().setSerialInfoEnabled(true);
    Pack pack = ssccScanResponse.getPacks().get(0);
    pack.setUnitCount(1.0);
    ssccScanResponse.setPurchaseOrders(Collections.singletonList(purchaseOrder));
    ssccScanResponse.setPacks(Collections.singletonList(pack));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    // when
    List<DeliveryDocument> mappedDeliveryDocLines =
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
            ssccScanResponse, "00909700302232310301", new HttpHeaders());

    // then
    assertNotNull(mappedDeliveryDocLines);
  }

  @Test
  public void testUpdateDeliveryDocumentIfDsdcDelivery() throws IOException, ReceivingException {
    // given
    File resource = new ClassPathResource("GdmResponseSSCC.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class);
    PurchaseOrder purchaseOrder = ssccScanResponse.getPurchaseOrders().get(0);
    purchaseOrder.getVendorInformation().setSerialInfoEnabled(true);
    Pack pack = ssccScanResponse.getPacks().get(0);
    pack.setUnitCount(1.0);
    ssccScanResponse.setPurchaseOrders(Collections.singletonList(purchaseOrder));
    ssccScanResponse.setPacks(Collections.singletonList(pack));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDSDCDeliveryDocuments();
    deliveryDocuments.get(0).setDeliveryNumber(83077757);
    // when
    asnToDeliveryDocumentsCustomMapper.updateDeliveryDocumentIfDsdcDelivery(
        deliveryDocuments, ssccScanResponse, pack);

    // then
    assertNotNull(deliveryDocuments.get(0).getAuditDetails());
  }

  @Test
  public void testUpdateDeliveryDocumentIfDsdcDelivery_ReceivingStatus_Open()
      throws IOException, ReceivingException {
    // given
    File resource = new ClassPathResource("GdmResponseSSCC.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class);
    PurchaseOrder purchaseOrder = ssccScanResponse.getPurchaseOrders().get(0);
    purchaseOrder.getVendorInformation().setSerialInfoEnabled(true);
    Pack pack = ssccScanResponse.getPacks().get(0);
    pack.setAuditStatus(ReceivingConstants.PENDING);
    pack.setReceivingStatus(ReceivingConstants.OPEN);
    pack.setUnitCount(1.0);
    ssccScanResponse.setPurchaseOrders(Collections.singletonList(purchaseOrder));
    ssccScanResponse.setPacks(Collections.singletonList(pack));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDSDCDeliveryDocuments();
    deliveryDocuments.get(0).setDeliveryNumber(83077757);
    // when
    asnToDeliveryDocumentsCustomMapper.updateDeliveryDocumentIfDsdcDelivery(
        deliveryDocuments, ssccScanResponse, pack);

    // then
    assertNotNull(deliveryDocuments.get(0).getAuditDetails());
  }

  @Test
  public void testUpdateDeliveryDocumentIfDADelivery() throws IOException, ReceivingException {
    // given
    File resource = new ClassPathResource("GdmResponseSSCC.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class);
    PurchaseOrder purchaseOrder = ssccScanResponse.getPurchaseOrders().get(0);
    purchaseOrder.getVendorInformation().setSerialInfoEnabled(true);
    Pack pack = ssccScanResponse.getPacks().get(0);
    pack.setUnitCount(1.0);
    ssccScanResponse.setPurchaseOrders(Collections.singletonList(purchaseOrder));
    ssccScanResponse.setPacks(Collections.singletonList(pack));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDSDCDeliveryDocuments();
    deliveryDocuments.get(0).setAuditDetails(Boolean.TRUE);
    deliveryDocuments.get(0).setDeliveryNumber(83077757);
    // when
    asnToDeliveryDocumentsCustomMapper.updateDeliveryDocumentIfDsdcDelivery(
        deliveryDocuments, ssccScanResponse, pack);

    // then
    assertNotNull(deliveryDocuments.get(0).getAuditDetails());
  }

  private static List<DeliveryDocument> getDSDCDeliveryDocuments() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_ListOfDA_Items_For_DSDC.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

    @Test
    public void test_mapGdmResponse_v4() throws IOException, ReceivingException {
        // given
        File resource = new ClassPathResource("gdm_get_shipment_by_delivery_v4.json").getFile();
        String ssccScanResponse = new String(Files.readAllBytes(resource.toPath()));
        when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
                "32898", ReceivingConstants.IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false))
                .thenReturn(true);
        // when
        List<DeliveryDocument> deliveryDocuments =
                asnToDeliveryDocumentsCustomMapper.mapGdmResponse(
                        gson.fromJson(ssccScanResponse, SsccScanResponse.class),
                        "00106001483896805972",
                        new HttpHeaders());
        // then
        assertNotNull(deliveryDocuments);
        assertEquals(deliveryDocuments.size(), 1);
        List<DeliveryDocumentLine> deliveryDocumentLines =
                deliveryDocuments.get(0).getDeliveryDocumentLines();
        assertEquals(deliveryDocumentLines.size(), 1);
        ItemData additionalInfo = deliveryDocumentLines.get(0).getAdditionalInfo();
        assertNotNull(additionalInfo);
        assertNotNull(additionalInfo.getHandlingCode());
    }
}
