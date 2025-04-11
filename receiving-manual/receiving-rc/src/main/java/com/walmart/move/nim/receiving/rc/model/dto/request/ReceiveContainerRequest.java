package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.contants.ActionType;
import com.walmart.move.nim.receiving.rc.model.container.Location;
import com.walmart.move.nim.receiving.rc.model.gad.Answers;
import com.walmart.move.nim.receiving.rc.model.gad.Disposition;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import com.walmart.move.nim.receiving.rc.model.item.ItemCategoryDetails;
import com.walmart.move.nim.receiving.rc.model.item.ItemDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReceiveContainerRequest {
  private String trackingId;
  private ActionType actionType;
  private String packageItemIdentificationCode;
  private String packageItemIdentificationMessage;
  private List<String> reasonCodes;
  private String scannedItemLabel;
  private String scannedSerialNumber;
  private String scannedLabel;
  private String scannedLabelType;
  private String scannedBin;
  private String scannedCart;
  private String containerTag;
  private Disposition disposition;
  private Answers answers;
  private SalesOrder salesOrder;
  private ItemDetails itemDetails;
  private Integer lineNumber;
  private Location location;
  private String workflowCreateReason;
  private String workflowId;
  private String prePopulatedCategory;
  private ItemCategoryDetails itemCategoryDetails;
}
