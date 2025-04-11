package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AutoReceiveRequest {

  private String doorNumber;
  @NotNull private Long deliveryNumber;
  private Integer quantity;
  private String quantityUOM;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  @NotNull private String lpn;
  private String messageId;
  List<DeliveryDocument> deliveryDocuments;
  private String featureType;
  private boolean flibEligible;
}
