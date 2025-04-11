package com.walmart.move.nim.receiving.utils.constants;

/**
 * This class will have all the String constants literals for reporting
 *
 * @author sks0013
 */
public final class ReportingConstants {

  // Constants for secondary data source
  public static final String SECONDARY_PERSISTENCE_UNIT = "secondaryPersistenceUnit";
  public static final String SECONDARY_DATA_SOURCE = "secondaryDataSource";
  public static final String SECONDARY_TRANSACTION_MANAGER = "secondaryTransactionManager";
  public static final String SECONDARY_ENTITY_MANAGER_FACTORY = "secondaryEntityManagerFactory";

  // Constants for reporting bean names
  public static final String TENANT_FILTER_INJECTOR_ASPECT_SECONDARY =
      "tenantFilterInjectorAspectSecondary";
  public static final String CC_OVERAGE_PALLET = "CCOveragePallet";

  private ReportingConstants() {}
  // Constants for reporting
  public static final String MAIL_HOST_KEY = "mail.smtp.host";
  public static final String MAIL_HOST_VALUE = "smtp-gw1.wal-mart.com";
  public static final String STATS_REPORT_SUBJECT_LINE = "Atlas-Receiving Weekly Statistics";
  public static final String ITEM_CATALOG_REPORT_SUBJECT_LINE =
      "Atlas-Receiving Item Catalog Report";
  public static final String ITEM_CATALOG_REPORT_PHARMACY_SUBJECT_LINE =
      "Atlas-Receiving Pharmacy Item Catalog Report";
  public static final String PHARMACY_REPORT_SUBJECT_LINE =
      "Atlas-Receiving Pharmacy receiving Metrics report";
  public static final String SUCCESSFUL_MAIL_SENT_MESSAGE = "Mail sent successfully";
  public static final String STATS_REPORT_FILE_NAME = "Atlas_Receiving_Statistics";
  public static final String ITEM_CATALOG_REPORT_FILE_NAME = "Atlas_Receiving_Item_Catalog";
  public static final String ITEM_CATALOG_RPHARMACY_REPORT_FILE_NAME =
      "Pharmacy Atlas_Receiving_Item_Catalog";
  public static final String REPORT_GENERATION_ERROR =
      "Something went wrong while generating report and error is {}";
  public static final String NUMBER_OF_DELIVERIES_STAT = "Number of deliveries received against";
  public static final String NUMBER_OF_POS_STAT = "Number of POs";
  public static final String NUMBER_OF_USERS_STAT = "Number of users";
  public static final String NUMBER_OF_ITEMS_RECEIVED_STAT = "Number of items Received";
  public static final String NUMBER_OF_LABELS_PRINTED_STAT = "Number of labels Printed";
  public static final String NUMBER_OF_DA_CON_PALLETS_STAT = "Number of DA conveyable pallets";
  public static final String NUMBER_OF_DA_CON_CASES_STAT = "Number of DA conveyable Cases";
  public static final String NUMBER_OF_DA_NON_CON_PALLETS_STAT =
      "Number of DA non-conveyable pallets";
  public static final String NUMBER_OF_DA_NON_CON_CASES_STAT = "Number of DA non-conveyable Cases";
  public static final String NUMBER_OF_SSTK_PALLETS_STAT = "Number of SSTK pallets";
  public static final String NUMBER_OF_SSTK_CASES_STAT = "Number of SSTK Cases";
  public static final String NUMBER_OF_PBYL_PALLETS_STAT = "Number of PBYL pallets";
  public static final String NUMBER_OF_PBYL_CASES_STAT = "Number of PBYL Cases";
  public static final String NUMBER_OF_ACL_CASES_STAT = "Number of cases received through ACL";
  public static final String NUMBER_OF_MANUAL_CASES_STAT =
      "Number of cases received manually on conveyor";
  public static final String NUMBER_OF_PO_CON_PALLETS_STAT = "Number of PoCon pallets";
  public static final String NUMBER_OF_PO_CON_CASES_STAT = "Number of PoCon Cases";
  public static final String NUMBER_OF_DSDC_PALLETS_STAT = "Number of DSDC pallets";
  public static final String NUMBER_OF_DSDC_CASES_STAT = "Number of DSDC Cases";
  public static final String AVERAGE_NUMBER_OF_PALLETS_PER_DELIVERY_STAT =
      "Average number of pallets per delivery";
  public static final String AVERAGE_PALLET_BUILD_TIME_STAT =
      "Average pallet build time in seconds";
  public static final String AVERAGE_DELIVERY_COMPLETION_TIME_STAT = "Average LOS in hrs";
  public static final String NUMBER_OF_VTR_CONTAINERS_STAT = "Number of labels voided(VTRed)";
  public static final String NUMBER_OF_VTR_CASES_STAT = "Number of Cases voided(VTRed)";
  public static final String NUMBER_OF_DOCK_TAGS_STAT = "Number of dock tags";
  public static final String NUMBER_OF_SYS_REO_CASES_STAT =
      "Number of cases received after sys reopen";
  public static final String NUMBER_OF_PROBLEM_PALLETS_STAT = "Number of answered problem pallets";
  public static final String ACL_NOTIFICATION_REPORT_SUBJECT_LINE =
      "Atlas-Receiving ACL Log Report";
  public static final String ACL_NOTIFICATION_REPORT_FILE_NAME = "Atlas_Receiving_Acl_Log";
  public static final String TOTAL_DELIVERY_COMPLETION_TIME_STAT = "Total LOS in hrs";
  public static final String PERCENTAGE_OF_DELIVERIES_MEETING_LOS_STAT =
      "Percentage of deliveries meeting LOS criteria";
  public static final String AVERAGE_PO_PROCESSING_TIME_STAT = "Average PO processing time in hrs";
  public static final String TOTAL_PO_PROCESSING_TIME_STAT = "Total PO processing time in hrs";
  public static final String AVERAGE_DOOR_OPEN_TIME_STAT =
      "Average time taken to open a delivery after arrival in hrs";
  public static final String TOTAL_DOOR_OPEN_TIME_STAT =
      "Total time taken to open a delivery after arrival in hrs";
  public static final String AVERAGE_DELIVERY_RECEIVING_TIME_STAT =
      "Average time taken to complete the delivery after door open in hrs";
  public static final String TOTAL_DELIVERY_RECEIVING_TIME_STAT =
      "Total time taken to complete the delivery after door open in hrs";
  public static final String NUMBER_OF_CASES_RECEIVED_STAT = "Number of cases received";
  public static final String WRKB_STATSSHEET = "-stats";
  public static final String WRKB_DELIVERYINFOSSHEET = "-DeliveryInfo";
  public static final String WRKB_USERINFOSSHEET = "-CasesByUser+ChannelType";
  public static final String DC_TIMEZONE = "timeZone";
  public static final String EMAIL_SUBJECT_PARTIALPENDING = "Partial Pending Instructions";
  public static final String BUILD_CONTAINER = "Build Container";

  public static final String D40_UNIT_UPC_RECEIVING = "BuildPrtlContnr";

  public static final String RX_BUILD_PALLET = "RxBuildPallet";
  public static final String RX_CNTR_CASE_SCAN = "RxCntrCaseScan";
  public static final String RX_BUILD_UNIT_SCAN = "RxBuildUnitScan";

  public static final String RX_CNTR_GTIN_AND_LOT = "RxCntrGtinLotScan";

  public static final String RX_SER_CNTR_GTIN_AND_LOT = "RxSerCntrGtinLotScan";

  public static final String RX_SER_BUILD_PALLET = "RxSerBuildContainer";
  public static final String RX_SER_CNTR_CASE_SCAN = "RxSerCntrCaseScan";
  public static final String RX_SER_BUILD_UNIT_SCAN = "RxSerBuildUnitScan";
  public static final String RX_SER_MULTI_SKU_PALLET = "RxSerMultiSkuPallet";

  public static final String BACKOUT = "backout";
  public static final String BREAKPACK_BACKOUT_REPORT_SUBJECT_LINE =
      "Atlas-Receiving BreakPack Backout report for facility Number: %s";

  public static final String OCC_ACK_PENDING = "ItemCOOValidationRequired";
  public static final String ITEM_COO_AND_PACK_SIZE_VALIDATION_PENDING =
      "ItemCOOAndPackSizeValidationRequired";
  public static final String ITEM_COO_AND_PACK_SIZE_VALIDATION_MESSAGE =
      "Item Country Of Origin, VNPK/WHPK Validations are Required";
  public static final String OCC_ACK_MESSAGE = "Item Country Of Origin Validation Required";

  public static final String PACK_ACK_PENDING = "ItemPackSizeValidationRequired";
  public static final String PACK_ACK_MESSAGE = "Item VNPK/WHPK Validation Required";
  public static final int DEFAULT_ATLAS_DA_BREAK_PACK_BACK_OUT_REPORT_RECORDS_FETCH_COUNT = 200;
  public static final Integer DEFAULT_PAGE = 1;
  public static final Integer DEFAULT_LIMIT = 50;

}
