package com.walmart.move.nim.receiving.endgame.mock.data;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.endgame.model.Datum;
import com.walmart.move.nim.receiving.endgame.model.LabelResponse;
import com.walmart.move.nim.receiving.endgame.model.PrintRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockLabelResponse {

  public static Map<String, String> createMockLabelHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("WMT-CorrelationId", "abc");
    headers.put("facilityCountryCode", "US");
    headers.put("facilityNum", "54321");
    return headers;
  }

  public static List<PrintRequest> createMockPrintRequests() {
    List<PrintRequest> printRequests = new ArrayList<>();
    PrintRequest printRequest =
        PrintRequest.builder()
            .data(createMockData())
            .formatName("rcv_tpl_eg_zeb")
            .labelIdentifier("PQ00000257")
            .ttlInHours(72)
            .build();
    printRequests.add(printRequest);
    return printRequests;
  }

  public static List<Datum> createMockData() {
    List<Datum> printingData = new ArrayList<>();
    printingData.add(new Datum("trailer", ReceivingConstants.EMPTY_STRING));
    printingData.add(new Datum("Date", ReceivingUtils.dateInEST()));
    printingData.add(new Datum("deliveryNumber", "60077104"));
    printingData.add(new Datum("DESTINATION", ReceivingConstants.EMPTY_STRING));
    printingData.add(new Datum("Qty", "80"));
    printingData.add(new Datum("ITEM", "553708208"));
    printingData.add(new Datum("DESC", "ROYAL BASMATI 20LB"));
    printingData.add(new Datum("UPCBAR", "10745042112010"));
    printingData.add(new Datum("user", "sysadmin"));
    printingData.add(new Datum("TCL", "PQ00000257"));
    printingData.add(new Datum("TCLPREFIX", "PQ0000"));
    printingData.add(new Datum("TCLSUFFIX", "0257"));
    return printingData;
  }

  public static LabelResponse createMockLabelResponse() {
    return LabelResponse.builder()
        .headers(createMockLabelHeaders())
        .clientId("receiving")
        .printRequests(createMockPrintRequests())
        .build();
  }

  public static String createMockPrintableZPLTemplate() {
    return "{\"default\": \"Test Template\"}";
  }
}
