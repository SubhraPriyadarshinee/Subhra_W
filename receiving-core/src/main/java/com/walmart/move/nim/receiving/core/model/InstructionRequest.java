package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** @author g0k0072 Model to represent instruction request */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class InstructionRequest {
  @NotNull private String messageId;
  @NotNull private String deliveryStatus;
  @NotNull private String deliveryNumber;
  @NotNull private String doorNumber;
  private Boolean isDSDC;
  private Boolean isPOCON;
  private String nonNationPo;
  private String asnBarcode;
  private String problemTagId;
  private Integer resolutionQty;
  private Integer enteredQty;
  private String enteredQtyUOM;
  private String upcNumber;
  private List<DeliveryDocument> deliveryDocuments;
  private boolean isOnline;
  private String mappedFloorLine;
  private boolean isManualReceivingEnabled;
  private String sscc;
  private List<String> previouslyScannedDataList;
  private List<ScannedData> scannedDataList;
  private Map<String, Object> additionalParams;
  private String pbylDockTagId;
  private String pbylLocation;
  private VendorCompliance regulatedItemType;
  private boolean isReceiveAsCorrection;
  private boolean isOverflowReceiving;
  private boolean isVendorComplianceValidated;
  private String receivingType;
  private Long instructionSetId;
  private boolean isMultiSKUItem;
  private String lastScannedFreightType;
  private String featureType;
  private boolean isReceiveAll;
  private boolean isItemValidationDone;
  private boolean isPoManualSelectionEnabled;
  private boolean isSyncPrintEnabled;
  private FitProblemTagResponse fitProblemTagResponse;
}
