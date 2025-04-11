package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ItemData implements Serializable {
  private String warehouseAreaCode;
  private String warehouseAreaDesc;
  private String warehouseGroupCode;
  private boolean isNewItem = false;
  private String profiledWarehouseArea;
  private String warehouseRotationTypeCode;
  private Integer allowedTimeInWarehouseQty;
  private int warehouseMinLifeRemainingToReceive;
  private Boolean recall;
  private Float weight;
  private String weightQty;
  private String cubeQty;
  private String weightFormatTypeCode;
  private String dcWeightFormatTypeCode;
  private String omsWeightFormatTypeCode;
  private String weightUOM;
  private String weightQtyUom;
  private String cubeUomCode;
  private Boolean isDscsaExemptionInd;
  private Boolean isTemperatureSensitive = false;
  private Boolean isControlledSubstance = false;
  private Integer gdmPalletTi;
  private Integer gdmPalletHi;
  private Boolean isHACCP;
  // RDC specific fields
  private String primeSlot;
  private int primeSlotSize;
  private String handlingCode;
  private String packTypeCode;
  private String symEligibleIndicator;
  private Integer palletTi;
  private Integer palletHi;
  private int isHazardous;
  private String itemHandlingMethod;
  private String itemPackAndHandlingCode;
  private boolean atlasConvertedItem;
  private String slotType;
  private String asrsAlignment;
  private Boolean isWholesaler = false;
  private List<Map<String, String>> addonServices;
  private Map<String, String> images;
  private Boolean isDefaultTiHiUsed = false;
  private String imageUrl;
  private Boolean qtyValidationDone = false;
  private Boolean isEpcisEnabledVendor = false;
  private int auditQty;
  private List<String> lotList;
  private List<String> gtinList;
  private List<String> unitLotList;
  private List<String> unitGtinList;
  private int attpQtyInEaches;
  private WHPKDimensions whpkDimensions;
  private ReceivingTier receivingTier;
  private Boolean isPalletSSCCRequest;
  private int auditCompletedQty;
  private String scannedCaseAttpQtyUOM;
  private int scannedCaseAttpQty;
  private String sgtinScannedSerial;
  private List<ManufactureDetail> serializedInfo;
  private String palletOfCase;
  private ManufactureDetail scannedCase;
  private Boolean isSerUnit2DScan;
  private List<Pack> packsOfMultiSkuPallet;
  private String documentId;
  private String shipmentNumber;
  private Boolean skipEvents = false;
  private Boolean partialPallet = false;
  private Boolean multiPOPallet = false;
  private Boolean isCompliancePack = false;
  private Boolean skipUnitEvents = false;
  private Boolean partOfMultiSkuPallet = false;
  private int packCountInEaches;
  private Boolean autoSwitchEpcisToAsn = false;
  private boolean palletFlowInMultiSku = false;
  private Boolean isEpcisSmartReceivingEnabled = false;
  private String fromPoLineDCNumber;
  private boolean itemConfigEligibleItem;
  private Boolean isPalletInsForCaseRecv = false;
  private boolean isLotNumberRequired;
}
