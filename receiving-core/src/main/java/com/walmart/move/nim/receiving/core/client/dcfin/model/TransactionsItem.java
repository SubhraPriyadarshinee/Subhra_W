package com.walmart.move.nim.receiving.core.client.dcfin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.core.model.DistributionsItem;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class TransactionsItem {
  private int accountingDeptNbr;
  private int costPerSecQty;
  private Long itemNumber;
  private String promoBuyInd;
  private String secondaryQtyUOM;
  private String documentType;
  private int freightBillQty;
  private String primaryQtyUOM;
  private int vendorPackQty;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  private Date dateAdjusted;

  private String returnNumber;
  private int targetItemNumber;
  private String weightFormatType;
  private int quantityToTransfer;
  private int baseRetailAmount;
  private String sellerId;
  private String carrierName;
  private String reasonCodeDesc;
  private int primaryQty;
  private String documentNum;
  private int qtyReceivedFromUpstream;
  private String reasonCode;
  private String trailerNbr;
  private String containerId;
  private int warehousePackQty;
  private String baseDivCode;
  private String billCode;
  private String inboundChannelMethod;
  private String labelNumber;
  private String carrierScacCode;
  private Float secondaryQty;
  private List<DistributionsItem> distributions;
  private String referenceDocNum;
  private int costPerPrimaryQty;
  private String deliveryNum;
  private String currencyCode;
  private String invTransferDestNbr;
  private int documentLineNo;
  private String financialReportGrpCode;
}
