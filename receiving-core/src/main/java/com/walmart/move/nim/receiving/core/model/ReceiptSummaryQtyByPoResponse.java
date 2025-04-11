package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptSummaryQtyByPoResponse {

  private Long deliveryNumber;
  private Integer freightBillQuantity;
  private Integer totalBolFbq;
  private Integer receivedQty;
  private String receivedQtyUom;
  private List<ReceiptSummaryQtyByPo> summary;
  private Integer asnQty;
  private List<Shipment> shipments;
  private String deliveryTypeCode;
  // This is needed for YMS 2.0 delivery update flow
  private GdmPOLineResponse gdmPOLineResponse;
}
