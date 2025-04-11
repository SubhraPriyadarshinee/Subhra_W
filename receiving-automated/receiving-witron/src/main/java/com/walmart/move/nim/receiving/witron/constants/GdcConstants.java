package com.walmart.move.nim.receiving.witron.constants;

public final class GdcConstants {

  private GdcConstants() {}

  // GDC (Manual and automation)
  public static final String OUTBOUND_SSTK = "SSTKU";
  public static final String GDC_INSTRUCTION_SERVICE = "GdcInstructionService";
  public static final String GDC_COMPLETE_DELIVERY_PROCESSOR = "GdcCompleteDeliveryProcessor";
  public static final String GDC_INVENTORY_EVENT_PROCESSOR = "GdcInventoryEventProcessor";
  public static final String GDC_FIXIT_PROBLEM_SERVICE = "GdcFixitProblemService";
  public static final String GDC_CANCEL_CONTAINER_PROCESSOR = "GdcCancelContainerProcessor";

  // Automated GDC
  public static final String WITRON = "Witron";
  public static final String WITRON_LPN_SERVICE = "WitronLPNService";
  public static final String WITRON_OSDR_SERIVCE = "witronOsdrService";
  public static final String WITRON_DC_FIN_SERVICE = "WitronDCFinService";
  public static final String WITRON_MANAGED_CONFIG = "witronManagedConfig";
  public static final String WITRON_VENDOR_VALIDATOR = "witronVendorValidator";
  public static final String WITRON_LPN_CACHE_SERVICE = "WitronLPNCacheService";
  public static final String WITRON_INSTRUCTION_SERVICE = "WitronInstructionService";
  public static final String WITRON_GDM_PO_LINE_RESPONSE_CACHE = "gdmPoLineResponseCache";
  public static final String WITRON_UPDATE_INSTRUCTION_HANDLER = "WitronUpdateInstructionHandler";

  // Manual GDC
  public static final String THREE = "3";
  public static final String FOUR = "4";
  public static final String WM = "WM";
  public static final String SAMS = "SAMS";
  public static final String GLS = "gls";
  public static final String GDC_PROVIDER_ID = "GDC-RCV";
  public static final String IS_OSS_VTR_ENABLED = "isOssVtrEnabled";

  public static final String IS_SMART_SLOTTING_DISABLED = "isSmartSlottingApiDisabled";
  public static final String IS_DCFIN_API_DISABLED = "isDCFinApiDisabled";
  public static final String PUBLISH_TO_MM_DISABLED = "publishToMMDisabled";
  public static final String PUBLISH_TO_WFT_DISABLED = "publishToWFTDisabled";

  public static final String PUBLISH_VTR_TO_WFT_DISABLED = "publishVtrToWFTDisabled";

  // Print labels formats, flags
  public static final String GDC_LABEL_FORMAT_NAME = "gdc_pallet_lpn";
  public static final String GDC_LABEL_FORMAT_NAME_V2 = "gdc_pallet_lpn_v2";
  public static final String GDC_LABEL_FORMAT_NAME_V3 = "gdc_pallet_lpn_v3";
  public static final String GDC_LABEL_FORMAT_7_DIGIT = "gdc_pallet_lpn_7digit";
  public static final String IS_LPN_GEN_DISABLED = "isLpnGenApiDisabled";
  public static final String IS_GDC_LABEL_V2_ENABLED = "isGdcLabelV2Enabled";
  public static final String IS_WITRON_LABEL_ENABLE = "isWitronLabelEnable";
  public static final String IS_GDC_LABEL_V3_ENABLE = "isGDCLabelV3Enable";
  public static final String MECH_AUTOMATION_TYPE = "MECH";
  public static final String NON_MECH_AUTOMATION_TYPE = "NON-MECH";

  public static final String SLOT_NOT_FOUND = "SLT NT FND";
  public static final String SLOTTING_ROTATE_DATE_FORMAT_YYYY_MM_DD = "yyyy-MM-dd";
  public static final String SUBCENTER_ID_HEADER = "subcenterId";
  public static final String SUBCENTER_DEFAULT_VALUE = "1";
  public static final String PUBLISH_TO_WFT_JUMP_TRAILER_ENABLED = "publishToWFTJumpTrailerEnabled";
  public static final String IS_INCLUDE_PALLET_COUNT = "isIncludePalletCount";
  public static final String IS_MQ_RECEIPTS_ENABLED = "isMqReceiptsEnabled";
  public static final String IS_DC_FIN_HTTP_RECEIPTS_ENABLED = "isDCFinHttpReceiptsEnabled";
  public static final String IGNORE_ADJ_FROM_INVENTORY = "ignoreAdjFromInventory";
  public static final String NEGATIVE_RECEIVE_QUANTITY_ERROR =
      "Negative quantity (%s) cannot be received";
  public static final String NEGATIVE_REJECT_QUANTITY_ERROR =
      "Negative quantity (%s) cannot be rejected";
  public static final String DEFAULT_VIRTUAL_PRIME_SLOT_INTO_OSS = "glbl";
  public static final String VIRTUAL_PRIME_SLOT_INTO_OSS = "virtualPrimeSlotIntoOss";
  public static final String VIRTUAL_PRIME_SLOT_KEY_INTO_OSS = "VirtualPrime";

  public static final String DEFAULT_VALID_UNLOADER_EVENT_TYPES = "UNLOAD_START,UNLOAD_STOP";
  public static final String DEFAULT_DOOR_CCM = "defaultDoorNbr";
}
