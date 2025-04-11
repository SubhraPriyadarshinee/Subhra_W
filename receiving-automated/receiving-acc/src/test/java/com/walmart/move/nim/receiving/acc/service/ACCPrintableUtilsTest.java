package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.Mockito.doReturn;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.util.PrintableUtils;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACCPrintableUtilsTest extends ReceivingTestBase {

  @InjectMocks private PrintableUtils accPrintableUtils;
  @Mock private ACCManagedConfig accManagedConfig;
  private Gson gson;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  private void resetMocks() {}

  @Test
  public void testLabelData() {
    String dataPath;
    List<PrintableLabelDataRequest> printableLabelDataRequests = new ArrayList<>();
    PrintableLabelDataRequest printableLabelDataRequest = new PrintableLabelDataRequest();
    printableLabelDataRequest.setFormatName("case_lpn_format");
    List<Pair<String, String>> labelData = new ArrayList<>();
    String genericLabelData = null;
    gson = new Gson();
    try {
      dataPath =
          new File("../../receiving-test/src/main/resources/json/GenericLabelData.json")
              .getCanonicalPath();
      genericLabelData = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }
    PrintableLabelDataRequest[] labelDataRequests =
        gson.fromJson(genericLabelData, PrintableLabelDataRequest[].class);
    printableLabelDataRequests.add(labelDataRequests[0]);

    doReturn(getZplData()).when(accManagedConfig).getAccPrintableZPL();

    String labelDataResponse =
        accPrintableUtils.getLabelData(
            printableLabelDataRequests,
            ReceivingConstants.PRINT_TYPE_ZEBRA,
            ReceivingConstants.PRINT_MODE_CONTINUOUS);

    assert (labelDataResponse.contains("Store Friendly Shipping"));
  }

  private String getZplData() {
    String zplTemplate =
        "^XA^PW660^POI^LH12,0^LS0^LT0^FWR,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO172,30,0^GB3,585,2^FS^FO154,30,0^GB3,522,2^FS^FO98,560,0^GB3,64,2^FS^FO112,30,0^GB3,208,2^FS^FO88,236,0^GB3,313,2^FS^FO48,30,0^GB3,459,2^FS^FX === VERTICAL LINES ===^FO154,138,0^GB19,3,2^FS^FO90,501,0^GB65,3,2^FS^FX === LABEL BARCODE ===^FO330,51^BY3^BCR,220,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT302,136,0^CF0,17,30^FD=DESC1=^FS^FX === DESC2 ===^FT282,136,0^CF0,17,30^FD=DESC2=^FS^FX === CPQTY ===^FT262,136,0^CF0,20,30^FDQTY^FS^FT262,194,0^CF0,20,30^FD=CPQTY=^FS^FX === ITEM ===^FT262,284,0^CF0,20,30^FD=ITEM=^FS^FX === HAZMAT ===^FT207,47,0^CF0,70,80^FD=HAZMAT=^FS^FX === UPC BARCODE ===^FO189,182^BY2^BCR,61,n,,,A^FD=UPCBAR=^FS^FX === DEPT ===^FT277,584,0^CF0,50,50^FD=DEPT=^FS^FX === STOREZONE ===^FT227,468,0^CF0,60,70^FD=STOREZONE=^FS^FX === EVENTCHAR ===^FT227,504,0^CF0,60,70^FD=EVENTCHAR=^FS^FX === EVENTCHAR ===^FT227,534,0^CF0,60,70^FD=EVENTCHAR=^FS^FX === EVENTCHAR ===^FT227,564,0^CF0,60,70^FD=EVENTCHAR=^FS^FX === EVENTCHAR ===^FT197,504,0^CF0,60,70^FD=EVENTCHAR=^FS^FX === EVENTCHAR ===^FT197,534,0^CF0,60,70^FD=EVENTCHAR=^FS^FX === EVENTCHAR ===^FT197,564,0^CF0,60,70^FD=EVENTCHAR=^FS^FX === PO EVENT ===^FT187,468,0^CF0,25,25^FD=POEVENT=^FS^FX === REPRINT ===^FT156,44,0^CF0,15,17^FD=REPRINT=^FS^FX === PO ===^FT160,202,0^CF0,15,17^FDPO^FS^FT160,226,0^CF0,15,17^FD=PO=^FS^FX === POLINE ===^FT160,338,0^CF0,15,17^FDLn^FS^FT160,358,0^CF0,15,17^FD=POLINE=^FS^FX === COLOR ===^FT136,44,0^CF0,20,25^FD=COLOR=^FS^FX === SIZE ===^FT136,169,0^CF0,20,25^FD=SIZE=^FS^FX === LABELDATE ===^FT119,44,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT119,254,0^CF0,20,25^FD=POCODE=^FS^FX === UPCBAR ===^FT136,274,0^CF0,12,15^FDUPC^FS^FT136,304,0^CF0,20,25^FD=UPCBAR=^FS^FX === VENDORID ===^FT93,260,0^CF0,30,27^FD=VENDORID=^FS^FX === PACK ===^FT136,506,0^CF0,12,15^FDPACK^FS^FT96,506,0^CF0,50,40^FD=PACK=^FS^FX === PACKTYPE ===^FT121,579,0^CF0,60,50^FD=PACKTYPE=^FS^FX === LPN ===^FT58,64,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT52,524,0^CF0,50,47^FD=CHANNEL=^FS^FX === PRINTER ===^FT29,44,0^CF0,25,30^FD=PRINTER=^FS^FX === ORIGIN_DC ===^FT29,32,0^CF0,25,25^FD=ORIGIN=^FS^FX === DELIVERY ===^FT29,113,0^CF0,25,20^FDDelivery^FS^FT29,188,0^CF0,25,25^FD=DELIVERY=^FS^FX === DOOR ===^FT29,305,0^CF0,25,20^FDDoor^FS^FT29,353,0^CF0,25,25^FD=DOOR=^FS^FX === FULLUSERID ===^FT29,410,0^CF0,25,25^FD=FULLUSERID=^FS^FX === DESTINATION ===^FT29,528,0^CF0,25,30^FD=DESTINATION=^FS^XZ";

    return zplTemplate;
  }
}
