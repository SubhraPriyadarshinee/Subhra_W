package com.walmart.move.nim.receiving.core.model;

import com.google.gson.internal.LinkedTreeMap;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Model to represent instruction summary(used in door scan flow)
 *
 * @author g0k0072
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class InstructionSummary {
  private Long id;
  private Long _id;
  private String problemTagId;
  private InstructionData instructionData;
  private Integer receivedQuantity;
  private String receivedQuantityUOM;
  private Integer projectedReceiveQty;
  private String projectedReceiveQtyUOM;
  private String createUserId;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String gtin;
  private String deliveryDocument;
  private Long itemNumber;
  private String purchaseRefType;
  private String itemDescription;
  private String ndc;
  private String poDcNumber;
  private Date createTs;
  private String completeUserId;
  private Date completeTs;
  private String lastChangeUserId;
  private Date lastChangeTs;
  private Boolean firstExpiryFirstOut;
  private String activityName;
  private String dockTagId;
  private String instructionCode;
  private String sscc;
  private String ssccNumber; // Adding for client dependency, once resolved sscc will be removed.
  private LinkedTreeMap<String, Object> move;
  private Long instructionSetId;
  private List<InstructionSummary> instructionSet;
}
