package com.walmart.move.nim.receiving.endgame.message.common;

import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.Dimensions;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class ScanEventData extends MessageData {
  private String doorNumber;
  private long deliveryNumber;
  private @NotEmpty String trailerCaseLabel;
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
  private List<String> poNumbers;
  private List<String> boxIds;
  private String shipmentId;
  private PreLabelData preLabelData;
}
