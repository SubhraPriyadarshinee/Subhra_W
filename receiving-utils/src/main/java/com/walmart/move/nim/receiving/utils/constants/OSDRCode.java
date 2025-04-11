package com.walmart.move.nim.receiving.utils.constants;

public enum OSDRCode {
  // TODO: Fill O11's description
  O11("O11", ""),
  O13("O13", "Cartons Over Freight Bill Quantity"),
  O31(
      "O31",
      "Problem Freight, Awaiting Buyers Instruction."
          + "The completed problem freight tag must be scanned to Accounting when this code is used. "
          + "The problem must be entered into Remedy"),
  O32(
      "O32",
      "Return to Vendor (RTV) per Buyer.  The RTV must be scanned to Accounting when this code is used"),

  S10("S10", "Supplier Claim"),
  S11("S11", "Carrier, Dispatch, Consolidator Claim Over $100"),
  S12("S12", "Carrier Claim Less Than $100"),
  S13("S13", "Supplier, Claim Shipper Load and Count"),

  D10("D10", "Supplier Claim"),
  D11("D11", "Carrier Claim Over $100"),
  D12("D12", "Carrier Claim Damage Less Than $100"),
  D13("D13", "Supplier Claim Shipper Load and Count"),
  D29("D29", "Concealed  Damage"),
  D53("53", "VDM vendor damage"),

  R10("R10", "Supplier Claim"),
  R11("R11", "Carrier Claim"),

  S29("S29", "Concealed Shortage WPM/Pallet/Case"),
  O55("55", "RCO"),
  S54("54", "RCS"),
  D74("74", "Damage identified during Decant"),
  R78("78", "Expired Item identified during Decant"),
  R83("83", "Rejected cold chain issue"),
  R86("86", "Non MFC item identified during decanting"),
  R87("87", "Item past OPD freshness identified during decanting"),
  R88("88", "Oversized item found during decanting"),
  R91("91", "MFC to Store transfer during decanting"),
  R98("98", "Rejected wrong temp zone during decanting"),
  R152("152", "Reject during NGR Receiving"),
  S153("153", "Shortage during NGR Receiving");

  private String code;
  private String description;

  OSDRCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
