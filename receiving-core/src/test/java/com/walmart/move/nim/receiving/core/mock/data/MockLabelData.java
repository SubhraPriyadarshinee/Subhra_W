package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import java.util.*;
import java.util.stream.Collectors;

public class MockLabelData {

  private static Date date = new Date(2020, 5, 12, 0, 0, 0);

  private static List<String> getLabels() {
    List<String> labels = new ArrayList<>();
    labels.add("c32987000000000000000001");
    labels.add("c32987000000000000000002");
    labels.add("c32987000000000000000003");
    labels.add("c32987000000000000000004");
    labels.add("c32987000000000000000005");
    labels.add("c32987000000000000000006");
    return labels;
  }

  private static List<String> getExceptionLabels() {
    List<String> labels = new ArrayList<>();
    labels.add("c32987000000000000000007");
    return labels;
  }

  public static LabelData getMockLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .possibleUPC(
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}")
        .lpns(
            "[\"c32987000000000000000001\",\"c32987000000000000000002\",\"c32987000000000000000003\",\"c32987000000000000000004\",\"c32987000000000000000005\",\"c32987000000000000000006\"]")
        .label(
            "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
        .lpnsCount(6)
        .labelSequenceNbr(20231023000100001L)
        .labelType(LabelType.ORDERED)
        .createTs(date)
        .mustArriveBeforeDate(date)
        .lastChangeTs(date)
        .build();
  }

  public static LabelData getMockHawkEyeLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .purchaseReferenceLineNumber(8)
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .possibleUPC(
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}")
        .lpns(
            "[\"c32987000000000000000001\",\"c32987000000000000000002\",\"c32987000000000000000003\",\"c32987000000000000000004\",\"c32987000000000000000005\",\"c32987000000000000000006\"]")
        .label(null)
        .lpnsCount(6)
        .labelSequenceNbr(20231023000100001L)
        .labelType(LabelType.ORDERED)
        .createTs(date)
        .mustArriveBeforeDate(date)
        .lastChangeTs(date)
        .build();
  }

  public static LabelData getMockExceptionLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .possibleUPC(
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}")
        .lpns("[\"c32987000000000000000007\"]")
        .label(
            "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
        .lpnsCount(1)
        .labelSequenceNbr(20231023000100001L)
        .labelType(LabelType.EXCEPTION)
        .createTs(date)
        .mustArriveBeforeDate(date)
        .lastChangeTs(date)
        .build();
  }

  public static LabelData getMockHawkEyeExceptionLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .purchaseReferenceLineNumber(8)
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .possibleUPC(
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}")
        .lpns("[\"c32987000000000000000007\"]")
        .label(
            "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
        .lpnsCount(1)
        .sequenceNo(3)
        .labelType(LabelType.EXCEPTION)
        .createTs(date)
        .mustArriveBeforeDate(date)
        .lastChangeTs(date)
        .build();
  }

  public static LabelData getMockLabelDataNonCon() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .purchaseReferenceLineNumber(9)
        .isDAConveyable(Boolean.FALSE)
        .itemNumber(566051127L)
        .possibleUPC(
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}")
        .lpns("[]")
        .label(null)
        .lpnsCount(0)
        .labelSequenceNbr(20231023000100001L)
        .labelType(LabelType.ORDERED)
        .createTs(date)
        .mustArriveBeforeDate(date)
        .lastChangeTs(date)
        .build();
  }

  public static ACLLabelDataTO getMockACLLabelDataTO() {
    return ACLLabelDataTO.builder()
        .deliveryNbr(94769060L)
        .scanItems(
            Collections.singletonList(
                ScanItem.builder()
                    .reject(null)
                    .labels(Collections.singletonList(getFormattedLabels()))
                    .exceptionLabelURL(
                        "null/label-gen/deliveries/94769060/upcs/10074451115207/exceptionLabels")
                    .item(566051127L)
                    .possibleUPC(
                        PossibleUPC.builder()
                            .orderableGTIN("10074451115207")
                            .consumableGTIN("00074451115200")
                            .sscc(null)
                            .catalogGTIN(null)
                            .build())
                    .exceptionLabels(
                        FormattedLabels.builder()
                            .seqNo("10000000")
                            .purchaseReferenceNumber("3615852071")
                            .lpns(getExceptionLabels())
                            .labelData(
                                "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
                            .build())
                    .build()))
        .build();
  }

  public static FormattedLabels getFormattedLabels() {
    return FormattedLabels.builder()
        .seqNo("1")
        .purchaseReferenceNumber("3615852071")
        .lpns(getLabels())
        .labelData(
            "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
        .build();
  }

  public static FormattedLabels getHawkEyeFormattedLabels() {
    return FormattedLabels.builder()
        .seqNo("1")
        .purchaseReferenceNumber("3615852071")
        .purchaseReferenceLineNumber(1)
        .lpns(getLabels())
        .labelData(
            "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
        .poCode("AD")
        .poEvent("POS REPLEN")
        .poTypeCode("33")
        .build();
  }

  private static final PossibleUPC getPossibleUPC() {
    return JacksonParser.convertJsonToObject(
        "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}",
        PossibleUPC.class);
  }

  private static FormattedLabels getFormattedExceptionLabels() {
    return FormattedLabels.builder()
        .seqNo("10000000")
        .purchaseReferenceNumber("3615852071")
        .labelData(
            "^XA^PW660^LH0,0^LS0^LT12^FWn,0^FX ==== Store Friendly Shipping ====^FX === HORIZONTAL LINES ===^FO12,436,0^GB609,3,2^FS^FO12,454,0^GB546,3,2^FS^FO556,510,0^GB64,3,2^FS^FO12,496,0^GB232,3,2^FS^FO242,520,0^GB313,3,2^FS^FO12,560,0^GB478,3,2^FS^FX === VERTICAL LINES ===^FO144,438,0^GB3,19,2^FS^FO507,458,0^GB3,63,2^FS^FX === LABEL BARCODE ===^FO65,28^BY3^BCn,240,n,,,A^FD=LPN=^FS^FX === DESC1 ===^FT142,290,0^CF0,20,30^FDFEEDER QUICK SET 30G^FS^FX === DESC2 ===^FT142,310,0^CF0,20,30^FDMOD FALL 2017^FS^FX === CPQTY ===^FT142,330,0^CF0,20,30^FDQTY^FS^FT200,330,0^CF0,20,30^FD1^FS^FX === ITEM ===^FT290,330,0^CF0,20,30^FD33333388^FS^FX === HAZMAT ===^FT53,390,0^CF0,70,80^FDfalse^FS^FX === UPC BARCODE ===^FO188,345^BY2^BCn,61,n,,,A^FD00916376506093^FS^FX === DEPT ===^FT590,320,0^CF0,50,50^FD14^FS^FX === STOREZONE ===^FT474,370,0^CF0,60,70^FDA^FS^FX === EVENTCHAR ===^FT510,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,370,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT510,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT540,400,0^CF0,60,70^FD^FS^FX === EVENTCHAR ===^FT570,400,0^CF0,60,70^FD^FS^FX === PO EVENT ===^FT474,410,0^CF0,25,25^FDPOS REPLEN^FS^FX === REPRINT ===^FT50,451,0^CF0,15,17^FD^FS^FX === PO ===^FT208,451,0^CF0,15,17^FDPO^FS^FT232,451,0^CF0,15,17^FD40000015000^FS^FX === POLINE ===^FT344,451,0^CF0,15,17^FDLn^FS^FT364,451,0^CF0,15,17^FD1^FS^FX === COLOR ===^FT50,475,0^CF0,20,25^FD^FS^FX === SIZE ===^FT175,475,0^CF0,20,25^FD^FS^FX === LABELDATE ===^FT50,492,0^CF0,20,25^FD=LABELTIMESTAMP=^FS^FX === POCODE ===^FT260,492,0^CF0,20,25^FDAD^FS^FX === UPCBAR ===^FT280,475,0^CF0,12,15^FDUPC^FS^FT310,475,0^CF0,20,25^FD00916376506093^FS^FX === VENDORID ===^FT266,518,0^CF0,30,27^FD2222^FS^FX === PACK ===^FT512,475,0^CF0,12,15^FDPACK^FS^FT512,515,0^CF0,50,40^FD1^FS^FX === PACKTYPE ===^FT585,490,0^CF0,60,50^FDCP^FS^FX === LPN ===^FT70,553,0^CF0,40,37^FD=LPN=^FS^FX === CHANNEL ===^FT530,559,0^CF0,50,47^FDDIST^FS^FX === PRINTER ===^FT50,580,0^CF0,25,30^FD^FS^FX === FULLUSERID ===^FT170,580,0^CF0,25,30^FDSYS-ACL^FS^FX === DESTINATION ===^FT430,580,0^CF0,25,30^FD^FS^XZ")
        .lpns(new ArrayList<>())
        .build();
  }

  public static FormattedLabels getHawkEyeFormattedExceptionLabels() {
    return FormattedLabels.builder()
        .seqNo("10000000")
        .purchaseReferenceNumber("3615852071")
        .purchaseReferenceLineNumber(8)
        .labelData(null)
        .lpns(new ArrayList<>())
        .poCode("AD")
        .poEvent("POS REPLEN")
        .poTypeCode("33")
        .build();
  }

  public static LabelData getMockHawkeyeLabelDataWithLabelDataLpns() {
    LabelData labelData = getMockHawkEyeLabelData();
    setLabelDataLpns(labelData);
    return labelData;
  }

  public static LabelData getMockHawkeyeExceptionLabelDataWithLabelDataLpns() {
    LabelData labelData = getMockHawkEyeExceptionLabelData();
    setLabelDataLpns(labelData);
    return labelData;
  }

  public static void setLabelDataLpns(LabelData labelData) {
    List<String> lpns =
        new ArrayList<>(
            Arrays.asList(JacksonParser.convertJsonToObject(labelData.getLpns(), String[].class)));
    labelData.setLabelDataLpnList(
        lpns.stream()
            .map(lpn -> LabelDataLpn.builder().lpn(lpn).createTs(date).lastChangeTs(date).build())
            .collect(Collectors.toList()));
  }
}
