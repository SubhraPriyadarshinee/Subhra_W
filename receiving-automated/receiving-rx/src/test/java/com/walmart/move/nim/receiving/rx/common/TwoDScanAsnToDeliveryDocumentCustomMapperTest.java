package com.walmart.move.nim.receiving.rx.common;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TwoDScanAsnToDeliveryDocumentCustomMapperTest {

  private Gson gson = new Gson();
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @InjectMocks private TwoDScanAsnDeliveryDocumentMapper twoDScanAsnDeliveryDocumentMapper;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "32898", ReceivingConstants.CONVERT_QUANTITY_TO_EACHES_FLAG, false))
        .thenReturn(true);
  }

  @Test
  public void testMapGdmResponse_MultiSkuPalletSSCC() throws Exception {
    File resource = new ClassPathResource("GDMResponseSSCC_MultiSkuPallet.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        twoDScanAsnDeliveryDocumentMapper.mapGdmResponse(
            gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class),
            "00000669930006995024",
            new HttpHeaders());
    assertEquals(mappedDeliveryDocLines.size(), 1);
    assertEquals(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().size(), 3);
    assertEquals(
        (int)
            mappedDeliveryDocLines
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getShipmentDetailsList()
                .get(0)
                .getShippedQty(),
        73440);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getShipmentDetailsList()
            .get(0)
            .getShippedQtyUom(),
        ReceivingConstants.Uom.EACHES);
    assertEquals(
        (int)
            mappedDeliveryDocLines
                .get(0)
                .getDeliveryDocumentLines()
                .get(1)
                .getShipmentDetailsList()
                .get(0)
                .getShippedQty(),
        60480);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(1)
            .getShipmentDetailsList()
            .get(0)
            .getShippedQtyUom(),
        ReceivingConstants.Uom.EACHES);
    assertEquals(
        (int)
            mappedDeliveryDocLines
                .get(0)
                .getDeliveryDocumentLines()
                .get(2)
                .getShipmentDetailsList()
                .get(0)
                .getShippedQty(),
        23040);
    assertEquals(
        mappedDeliveryDocLines
            .get(0)
            .getDeliveryDocumentLines()
            .get(2)
            .getShipmentDetailsList()
            .get(0)
            .getShippedQtyUom(),
        ReceivingConstants.Uom.EACHES);
  }

  @Test
  public void testMapGdmResponse_PackSSCC() throws Exception {
    File resource = new ClassPathResource("GdmCaseScanResponse.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> mappedDeliveryDocLines =
        twoDScanAsnDeliveryDocumentMapper.mapGdmResponse(
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
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPackSSCC(),
        "909899000020014304");
    assertNull(mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC());
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
        20160);
    assertEquals(
        mappedDeliveryDocLines.get(0).getDeliveryDocumentLines().get(0).getShippedQtyUom(), "EA");
  }

  @Test
  public void testMapGdmResponse_PackSSCCSerialInfoEnabled() throws Exception {
    File resource = new ClassPathResource("GdmCaseScanResponse.json").getFile();
    String ssccScanGdmResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(ssccScanGdmResponse, SsccScanResponse.class);
    ssccScanResponse.getPurchaseOrders().get(0).getVendorInformation().setSerialInfoEnabled(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<DeliveryDocument> mappedDeliveryDocLines =
        twoDScanAsnDeliveryDocumentMapper.mapGdmResponse(
            ssccScanResponse, "909899000020014304", new HttpHeaders());
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
  }
}
