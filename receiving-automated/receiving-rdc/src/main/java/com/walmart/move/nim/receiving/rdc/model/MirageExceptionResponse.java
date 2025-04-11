package com.walmart.move.nim.receiving.rdc.model;

import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MirageExceptionResponse {
  private Long itemNumber;
  private String deliveryNumber;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String lpn;
  private boolean itemReceived;
  private String containerLabel;
  private String size;
  private String color;
  private String vendorStockNumber;
  private Boolean isAtlasConvertedItem;
  private MirageLabelReceiveRequest receiveRequest;
  private MirageDsdcReceiveRequest dsdcReceiveRequest;
  private boolean dsdcPack;
  private String itemDesc;
  private Date updatedTS;
  private String containerType;
  private String packNumber;
  private String labelType;
  private ReceivedContainer storeInfo;
  private DsdcReceiveResponse packinfo;
  private String itemUPC;
  private String desc;
  private String labelDate;
  private Integer vendorPack;
  private Integer warehousePack;
  private String rdsHandlingCode;
  private String rdsPackTypeCode;
}
