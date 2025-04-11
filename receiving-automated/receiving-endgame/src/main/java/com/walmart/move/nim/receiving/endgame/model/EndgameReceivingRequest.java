package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EndgameReceivingRequest extends MessageData {
  private Integer quantity;
  private String quantityUOM;
  private String doorNumber;
  private long deliveryNumber;
  private String parentTrackingId;
  private @NotEmpty String trackingId;
  private @NotEmpty String caseUPC;
  private DivertStatus diverted;
  private Dimensions dimensions;
  private String dimensionsUnitOfMeasure;
  private List<ContainerTag> containerTagList;
  private PurchaseOrder purchaseOrder;
  private Boolean isAuditRequired;
  private String requestOriginator;
  private String weightUnitOfMeasure;
  private Double weight;
  private @NotNull DeliveryMetaDataRequest deliveryMetaData;
}
