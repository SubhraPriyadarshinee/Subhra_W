package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.*;

@Data
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveExceptionRequest {
  private String doorNumber;
  private String messageId;
  private ScannedData scannedDataList;
  private List<String> lpns;
  private String exceptionMessage;
  private Integer itemNumber;
  private String printerNumber;
  private List<String> deliveryNumbers;
  private String upcNumber;
  private List<DeliveryDocument> deliveryDocuments;
  private String tokenId;
  private String slot;
  private String purchaseReferenceNumber;
  private String receiver;
  private String regulatedItemType;
  private boolean isVendorComplianceValidated;
  private String receivedType;
  private String featureType;
  private Integer quantity;
  private String quantityUOM;
  private Boolean isCatalogRequired;
  private boolean receiveScannedLpn;
}
