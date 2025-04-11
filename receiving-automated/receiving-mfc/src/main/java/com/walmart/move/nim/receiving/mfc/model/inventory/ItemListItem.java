package com.walmart.move.nim.receiving.mfc.model.inventory;

import java.util.List;
import lombok.ToString;

@ToString
public class ItemListItem {

  private Integer itemNumber;

  private WhsePackSell whsePackSell;

  private String ossIndicator;

  private Integer purchaseCompanyId;

  private Double totalItemWeight;

  private String baseDivisionCode;

  private String description;

  private String caseUPC;

  private String channelType;

  private Integer availableToPickQty;

  private Integer availabletosellQty;

  private String financialReportingGroup;

  private Integer problemfreightQty;

  private Integer intransitQty;

  private Integer warehousePkRatio;

  private ItemStatusChange itemStatusChange;

  private Integer availableNonPickableQty;

  private Integer allocatedQty;

  private String receivedDate;

  private List<Integer> purchaseReferenceLineNumbers;

  private Integer virtualQty;

  private String unitOfMeasurement;

  private Integer onHoldQty;

  private Integer holdQty;

  private List<PoDetailsItem> poDetails;

  private String totalItemWeightUOM;

  private Integer deptNumber;

  private Integer claimsQty;

  private Boolean isSoftAllocImpacted;

  private List<Object> orderDetails;

  private Integer receivedQty;

  private Integer vendorPkRatio;

  private Integer qualitycontrolQty;

  private Integer caseQty;

  private Boolean isHardAllocImpacted;

  private Integer onorderQty;

  private String itemUPC;

  private Integer workInProgressQty;

  private Integer frozenQty;

  private Integer replenishedQty;

  private AdjustmentTO adjustmentTO;

  private String poType;

  private Integer bohQty;

  private Integer nonInductedHoldQty;

  private Integer pickedQty;

  private String invoiceNumber;

  private Integer pluNumber;
  private String cid;
  private Integer deptCatNbr;
  private String hybridStorageFlag;
  private String deptSubcatgNbr;

  public Integer getItemNumber() {
    return itemNumber;
  }

  public WhsePackSell getWhsePackSell() {
    return whsePackSell;
  }

  public String getOssIndicator() {
    return ossIndicator;
  }

  public Integer getPurchaseCompanyId() {
    return purchaseCompanyId;
  }

  public Double getTotalItemWeight() {
    return totalItemWeight;
  }

  public String getBaseDivisionCode() {
    return baseDivisionCode;
  }

  public String getDescription() {
    return description;
  }

  public String getCaseUPC() {
    return caseUPC;
  }

  public String getChannelType() {
    return channelType;
  }

  public Integer getAvailableToPickQty() {
    return availableToPickQty;
  }

  public Integer getAvailabletosellQty() {
    return availabletosellQty;
  }

  public String getFinancialReportingGroup() {
    return financialReportingGroup;
  }

  public Integer getProblemfreightQty() {
    return problemfreightQty;
  }

  public Integer getIntransitQty() {
    return intransitQty;
  }

  public Integer getWarehousePkRatio() {
    return warehousePkRatio;
  }

  public ItemStatusChange getItemStatusChange() {
    return itemStatusChange;
  }

  public Integer getAvailableNonPickableQty() {
    return availableNonPickableQty;
  }

  public Integer getAllocatedQty() {
    return allocatedQty;
  }

  public String getReceivedDate() {
    return receivedDate;
  }

  public List<Integer> getPurchaseReferenceLineNumbers() {
    return purchaseReferenceLineNumbers;
  }

  public Integer getVirtualQty() {
    return virtualQty;
  }

  public String getUnitOfMeasurement() {
    return unitOfMeasurement;
  }

  public Integer getOnHoldQty() {
    return onHoldQty;
  }

  public Integer getHoldQty() {
    return holdQty;
  }

  public List<PoDetailsItem> getPoDetails() {
    return poDetails;
  }

  public String getTotalItemWeightUOM() {
    return totalItemWeightUOM;
  }

  public Integer getDeptNumber() {
    return deptNumber;
  }

  public Integer getClaimsQty() {
    return claimsQty;
  }

  public Boolean isIsSoftAllocImpacted() {
    return isSoftAllocImpacted;
  }

  public List<Object> getOrderDetails() {
    return orderDetails;
  }

  public Integer getReceivedQty() {
    return receivedQty;
  }

  public Integer getVendorPkRatio() {
    return vendorPkRatio;
  }

  public Integer getQualitycontrolQty() {
    return qualitycontrolQty;
  }

  public Integer getCaseQty() {
    return caseQty;
  }

  public Boolean isIsHardAllocImpacted() {
    return isHardAllocImpacted;
  }

  public Integer getOnorderQty() {
    return onorderQty;
  }

  public String getItemUPC() {
    return itemUPC;
  }

  public Integer getWorkInProgressQty() {
    return workInProgressQty;
  }

  public Integer getFrozenQty() {
    return frozenQty;
  }

  public Integer getReplenishedQty() {
    return replenishedQty;
  }

  public AdjustmentTO getAdjustmentTO() {
    return adjustmentTO;
  }

  public String getPoType() {
    return poType;
  }

  public Integer getBohQty() {
    return bohQty;
  }

  public Integer getNonInductedHoldQty() {
    return nonInductedHoldQty;
  }

  public Integer getPickedQty() {
    return pickedQty;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public Integer getPluNumber() {
    return pluNumber;
  }

  public void setPluNumber(Integer pluNumber) {
    this.pluNumber = pluNumber;
  }

  public String getCid() {
    return cid;
  }

  public void setCid(String cid) {
    this.cid = cid;
  }

  public Integer getDeptCatNbr() {
    return deptCatNbr;
  }

  public void setDeptCatNbr(Integer deptCatNbr) {
    this.deptCatNbr = deptCatNbr;
  }

  public String getHybridStorageFlag() {
    return hybridStorageFlag;
  }

  public void setHybridStorageFlag(String hybridStorageFlag) {
    this.hybridStorageFlag = hybridStorageFlag;
  }

  public String getDeptSubcatgNbr() {
    return deptSubcatgNbr;
  }

  public void setDeptSubcatgNbr(String deptSubcatgNbr) {
    this.deptSubcatgNbr = deptSubcatgNbr;
  }
}
