package com.walmart.move.nim.receiving.acc.service;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.mock.data.MockLabelData;
import com.walmart.move.nim.receiving.acc.model.hawkeye.label.HawkEyeScanItem;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelingHelperServiceTest extends ReceivingTestBase {
  @InjectMocks private LabelingHelperService labelingHelperService;

  @Mock private AppConfig appConfig;
  @Mock private ACCManagedConfig accManagedConfig;

  private DeliveryDetails deliveryDetails;
  private DeliveryDocument deliveryDocument;
  private DeliveryDocumentLine deliveryDocumentLine;

  @BeforeClass
  private void setup() {
    MockitoAnnotations.initMocks(this);
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();

      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);

      deliveryDocument = deliveryDetails.getDeliveryDocuments().get(0);

      deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

      deliveryDocumentLine.setDepartment(deliveryDocument.getDeptNumber());
      deliveryDocumentLine.setPoDCNumber(Integer.valueOf(deliveryDocument.getPoDCNumber()));
      deliveryDocumentLine.setPurchaseReferenceLegacyType(
          Integer.valueOf(deliveryDocument.getPurchaseReferenceLegacyType()));
      deliveryDocumentLine.setVendorNumber(deliveryDocument.getVendorNumber());

    } catch (IOException e) {
      assert (false);
    }
  }

  @Test
  public void testGetPOCode_whenReturnsAD() {
    String[] purchaseRefType = {"23", "33", "73", "83", "93"};
    for (String s : purchaseRefType) {
      assertEquals(labelingHelperService.getPOCode(s), "AD");
    }
  }

  @Test
  public void testGetPOCode_whenReturnsWR() {
    String[] purchaseRefType = {"20", "22", "40", "42", "50"};
    for (String s : purchaseRefType) {
      assertEquals(labelingHelperService.getPOCode(s), "WR");
    }
  }

  @Test
  public void testGetPOCode_whenReturnsWPM() {
    String[] purchaseRefType = {"10", "11", "13", "14", "18"};
    for (String s : purchaseRefType) {
      assertEquals(labelingHelperService.getPOCode(s), "WPM");
    }
  }

  @Test
  public void testGetPOCode_whenReturnsGO() {
    String forAnyOtherString = "forAnyOtherString";
    assertEquals(labelingHelperService.getPOCode(forAnyOtherString), "GO");
  }

  @Test
  public void testBuildScanItemFromLabelData() {
    ScanItem scanItem =
        labelingHelperService.buildScanItemFromLabelData(
            94769060L, MockLabelData.getMockLabelData());
    assertEquals(scanItem, MockLabelData.getMockScanItem());
  }

  @Test
  public void testBuildHawkEyeScanItemFromLabelDataAndPoLine() {
    HawkEyeScanItem hawkEyeScanItem =
        labelingHelperService.buildHawkEyeScanItemFromLabelDataAndPoLine(
            94769060L, deliveryDocumentLine, MockLabelData.getMockHawkEyeLabelData());
    assertEquals(hawkEyeScanItem, MockLabelData.getMockHawkEyeScanItem());
  }
}
