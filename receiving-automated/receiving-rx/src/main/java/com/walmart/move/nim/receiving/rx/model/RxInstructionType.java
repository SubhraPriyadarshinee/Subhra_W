package com.walmart.move.nim.receiving.rx.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum RxInstructionType {
  SERIALIZED_CONTAINER("RxSerBuildContainer", "RxSerializedBuildContainer"),
  SERIALIZED_CASES_SCAN("RxSerCntrCaseScan", "RxSerializedBuildContainerCaseScan"),
  BUILD_CONTAINER("RxBuildPallet", "RxBuildPallet"),
  BUILDCONTAINER_CASES_SCAN("RxCntrCaseScan", "RxBuildContainerByScanningCases"),
  BUILD_PARTIAL_CONTAINER("RxBuildUnitScan", "RxBuildContainerByScanningUnits"),
  BUILDCONTAINER_SCAN_BY_GTIN_LOT("RxCntrGtinLotScan", "RxBuildContainerByScanningGtinAndLot"),
  BUILD_CONTAINER_FOR_UPC_RECEIVING("Build Container", "Build Container"),
  BUILD_PARTIAL_CONTAINER_UPC_RECEIVING("BuildPrtlContnr", "BuildPrtlContnr"),
  SPLIT_PALLET("SplitPallet", "Split Pallet"),
  MULTISKU_ASN("RxMultiSkuPallet", "Multi Sku Pallet"),
  SERIALIZED_MULTISKU_PALLET("RxSerMultiSkuPallet", "RxSerializedMultiSkuPallet"),
  RX_SER_BUILD_CONTAINER("RxSerBuildContainer", "RxSerBuildContainer"),
  RX_SER_CNTR_CASE_SCAN("RxSerCntrCaseScan", "RxSerCntrCaseScan"),
  RX_SER_CNTR_GTIN_AND_LOT("RxSerCntrGtinLotScan", "RxSerCntrGtinLotScan"),
  RX_SER_BUILD_UNITS_SCAN("RxSerBuildUnitScan", "RxSerBuildUnitScan"),
  RX_SER_MULTI_SKU_PALLET("RxSerMultiSkuPallet", "RxSerMultiSkuPallet");

  @Getter private String instructionType;
  @Getter private String instructionMsg;

  public static boolean isExemptedItemInstructionGroup(String instructionType) {
    return (BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType().equals(instructionType)
        || BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType().equals(instructionType));
  }
}
