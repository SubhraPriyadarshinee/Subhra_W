package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.walmart.move.nim.receiving.core.message.common.AuditDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.GdmGtinHierarchy;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Pack {
  private String packNumber;
  private String palletNumber;
  private String shipmentNumber;
  private List<Item> items;
  private Destination handledOnBehalfOf;
  private String status;
  private String packType;
  private String gtin;
  private String expiryDate;
  private String serial;
  private String trackingStatus;
  private String uom;
  private String lotNumber;
  private boolean partialPack;
  private boolean multiskuPack;
  private Double unitCount;
  private String poNumber;
  private List<GdmGtinHierarchy> gtinHierarchy;
  private String documentPackId;
  private String documentId;
  private String receivingStatus;
  private String auditStatus;
  private AuditDetail auditDetail;
  private Map<String, Object> additionalInfo;
}
