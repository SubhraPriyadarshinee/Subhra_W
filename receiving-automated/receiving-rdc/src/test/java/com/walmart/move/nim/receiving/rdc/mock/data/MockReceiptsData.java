package com.walmart.move.nim.receiving.rdc.mock.data;

import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import java.util.ArrayList;
import java.util.List;

public class MockReceiptsData {
  public static List<ReceiptSummaryVnpkResponse> getReceiptsFromAtlasSinglePoPoL() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt =
        new ReceiptSummaryVnpkResponse("1467778615", 1, "ZA", 30L, 20L);
    receipts.add(receipt);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getReceiptsFromAtlasSinglePoMultiPoL() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 =
        new ReceiptSummaryVnpkResponse("1467778615", 2, "ZA", 30L, 15L);
    ReceiptSummaryVnpkResponse receipt2 =
        new ReceiptSummaryVnpkResponse("1467778615", 3, "ZA", 40L, 25L);
    ReceiptSummaryVnpkResponse receipt3 =
        new ReceiptSummaryVnpkResponse("1467778615", 4, "ZA", 50L, 30L);
    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getReceiptsFromAtlasDifferentPoL() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 =
        new ReceiptSummaryVnpkResponse("1467778699", 1, "ZA", 10L, 40L);
    ReceiptSummaryVnpkResponse receipt2 =
        new ReceiptSummaryVnpkResponse("1467778699", 2, "ZA", 20L, 50L);
    ReceiptSummaryVnpkResponse receipt3 =
        new ReceiptSummaryVnpkResponse("1467778699", 3, "ZA", 30L, 60L);
    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getReceiptsFromAtlasMultiPoPoL() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 =
        new ReceiptSummaryVnpkResponse("1467778688", 1, "ZA", 10L, 15L);
    ReceiptSummaryVnpkResponse receipt2 =
        new ReceiptSummaryVnpkResponse("1467778688", 2, "ZA", 20L, 30L);
    ReceiptSummaryVnpkResponse receipt3 =
        new ReceiptSummaryVnpkResponse("1621130072", 49, "ZA", 30L, 20L);
    ReceiptSummaryVnpkResponse receipt4 =
        new ReceiptSummaryVnpkResponse("1621130072", 50, "ZA", 40L, 40L);

    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    receipts.add(receipt4);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getRcvdPackCountByDeliveryForMultiplePO() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 = new ReceiptSummaryVnpkResponse("1467778688", 3);
    ReceiptSummaryVnpkResponse receipt2 = new ReceiptSummaryVnpkResponse("1621130072", 5);

    receipts.add(receipt1);
    receipts.add(receipt2);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getRcvdPackCountByDeliverySinglePoPoL() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt = new ReceiptSummaryVnpkResponse("1467778615", 2);
    receipts.add(receipt);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getRcvdPackCountByDeliveryDifferentPo() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt = new ReceiptSummaryVnpkResponse("1467778699", 3);
    receipts.add(receipt);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse>
      getRcvdPackCountByDeliveryForMultipleRDSandAtlasPO() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 = new ReceiptSummaryVnpkResponse("1621130072", 5);
    ReceiptSummaryVnpkResponse receipt2 = new ReceiptSummaryVnpkResponse("1669115709", 5);

    receipts.add(receipt1);
    receipts.add(receipt2);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getReceiptsForMultipleRDSandAtlasPO() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 =
        new ReceiptSummaryVnpkResponse("1467778688", 1, "ZA", 10L, 15L);
    ReceiptSummaryVnpkResponse receipt2 =
        new ReceiptSummaryVnpkResponse("1467778688", 2, "ZA", 20L, 30L);
    ReceiptSummaryVnpkResponse receipt3 =
        new ReceiptSummaryVnpkResponse("1621130072", 49, "ZA", 30L, 20L);
    ReceiptSummaryVnpkResponse receipt4 =
        new ReceiptSummaryVnpkResponse("1669115709", 1, "ZA", 40L, 40L);

    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    receipts.add(receipt4);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getReceiptsFromAtlasMultiPoPoLLessThanCase() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 =
        new ReceiptSummaryVnpkResponse("1467778615", 2, "ZA", 30L, 15L);
    ReceiptSummaryVnpkResponse receipt2 =
        new ReceiptSummaryVnpkResponse("1621130073", 3, "PH", 0L, 25L);
    ReceiptSummaryVnpkResponse receipt3 =
        new ReceiptSummaryVnpkResponse("1621130072", 4, "ZA", 50L, 30L);
    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    return receipts;
  }

  public static List<ReceiptSummaryVnpkResponse> getReceiptsFromAtlasDifferentPoPoLLessThanCase() {
    List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse receipt1 =
        new ReceiptSummaryVnpkResponse("1621130072", 1, "ZA", 10L, 40L);
    ReceiptSummaryVnpkResponse receipt2 =
        new ReceiptSummaryVnpkResponse("1669115709", 3, "ZA", 30L, 50L);
    ReceiptSummaryVnpkResponse receipt3 =
        new ReceiptSummaryVnpkResponse("1669115709", 3, "PH", 0L, 60L);
    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    return receipts;
  }
}
