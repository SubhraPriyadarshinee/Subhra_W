-- liquibase formatted sql

-- changeset a0b02ft:1644838097307-1
--create table--
if not exists (select 1 from sysobjects where name='CONTAINER')
CREATE TABLE dbo.CONTAINER (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, COMPLETE_TS datetime2, CONTAINER_STATUS varchar(20), CONTAINER_TYPE varchar(20), CREATE_TS datetime2, CREATE_USER varchar(20), CONTAINER_REUSABLE tinyint, CONTAINER_SHIPPABLE tinyint, CONTAINER_CUBE float(53), CUBE_UOM varchar(2), DELIVERY_NUMBER bigint NOT NULL, DESTINATION varchar(80), FACILITY varchar(80), INSTRUCTION_ID bigint, INVENTORY_STATUS varchar(20), LAST_CHANGED_TS datetime2, LAST_CHANGED_USER varchar(20), CONTAINER_LOCATION varchar(20), MESSAGE_ID varchar(50) NOT NULL, ORG_UNIT_ID varchar(20), PARENT_TRACKING_ID varchar(50), PUBLISH_TS datetime2, TRACKING_ID varchar(50), CONTAINER_WEIGHT float(53), WEIGHT_UOM varchar(2), CONTAINER_EXCEPTION_CODE varchar(10), ON_CONVEYOR tinyint, IS_CONVEYABLE tinyint, LABEL_ID smallint, CONTAINER_MISC_INFO varchar(600), ACTIVITY_NAME varchar(50), SSCC_NUMBER varchar(40), CONSTRAINT PK__CONTAINE__3214EC2739B33640 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='CONTAINER_ITEM')
CREATE TABLE dbo.CONTAINER_ITEM (CONTAINER_ITEM_ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, ACTUAL_HI int, ACTUAL_TI int, BASE_DIVISION_CODE varchar(8), DEPT_NUMBER int, DESCRIPTION varchar(80), DISTRIBUTIONS nvarchar(MAX), FINANCIAL_REPORTING_GROUP_CODE varchar(8), GTIN varchar(40), INBOUND_CHANNEL_METHOD varchar(15), ITEM_NUMBER bigint, LOT_NUMBER varchar(20), OUTBOUND_CHANNEL_METHOD varchar(15), PACKAGED_AS_UOM varchar(2), PO_DEPT_NUMBER varchar(10), PURCHASE_COMPANY_ID int, PURCHASE_REFERENCE_LINE_NUMBER int, PURCHASE_REFERENCE_NUMBER varchar(20), QUANTITY int, QUANTITY_UOM varchar(2), ROTATE_DATE datetime2, SECONDARY_DESCRIPTION varchar(80), TOTAL_PURCHASE_REFERENCE_QTY int, TRACKING_ID varchar(50), VENDOR_GS_128 varchar(32), VENDOR_NUMBER int, VENDOR_PACK_COST float(53), VNPK_QTY int, VNPK_WEIGHT_QTY float(53), VNPK_WEIGHT_QTY_UOM varchar(4), VNPK_CUBE_QTY float(53), VNPK_CUBE_QTY_UOM varchar(4), WHPK_QTY int, WHPK_SELL float(53), PROMO_BUY_IND varchar(1), WAREHOSUE_GROUP_CODE varchar(8), WAREHOUSE_AC varchar(8), PROFILED_WHAC varchar(8), WAREHOUSE_RTC varchar(8), RECALL tinyint, VARIABLE_WEIGHT tinyint, ITEM_UPC varchar(40), CASE_UPC varchar(40), ORDERABLE_QUANTITY int, WAREHOUSE_PACK_QUANTITY int, PO_TYPE_CODE int, WEIGHT_FORMAT_TYPE_CODE varchar(1), SERIAL varchar(20), EXPIRY_DATE date, VENDOR_DEPT_SEQ_NUMBERS int, MULTI_PO_PALLET tinyint, IMPORT_IND tinyint, PO_DC_NUMBER varchar(10), PO_DC_COUNTRY_CODE varchar(10), CONTAINER_ITEM_MISC_INFO varchar(600), ASRS_ALIGNMENT varchar(10), SLOT_TYPE varchar(10), CONSTRAINT PK__CONTAINE__49CA288697DDFDD0 PRIMARY KEY (CONTAINER_ITEM_ID));
if not exists (select 1 from sysobjects where name='CONTAINER_RLOG')
CREATE TABLE dbo.CONTAINER_RLOG (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, TRACKING_ID varchar(50), CONTAINER_LOCATION varchar(20), ORG_UNIT_ID varchar(20), INVENTORY_STATUS varchar(10), DELIVERY_NUMBER bigint NOT NULL, DESTINATION_PARENT_TRACKING_ID varchar(50), DESTINATION_TRACKING_ID varchar(50), DESTINATION_CONTAINER_TAG varchar(50), PACKAGE_BARCODE_VALUE varchar(50), PACKAGE_BARCODE_TYPE varchar(50), DISPOSITION_TYPE varchar(50), MESSAGE_ID varchar(50) NOT NULL, CONTAINER_TYPE varchar(20), CONTAINER_STATUS varchar(20), CONTAINER_WEIGHT float(53), WEIGHT_UOM varchar(2), CONTAINER_CUBE float(53), CUBE_UOM varchar(2), CONTAINER_REUSABLE tinyint, CONTAINER_SHIPPABLE tinyint, HAS_CHILD_CONTAINERS tinyint, TOTAL_PURCHASE_REFERENCE_QTY int, PURCHASE_COMPANY_ID int, QUANTITY int, QUANTITY_UOM varchar(2), VNPK_QTY int, WHPK_QTY int, DESCRIPTION varchar(80), SECONDARY_DESCRIPTION varchar(80), ITEM_NUMBER bigint, PURCHASE_REFERENCE_LINE_NUMBER int, PURCHASE_REFERENCE_NUMBER varchar(100), SALES_ORDER_NUMBER varchar(100), SALES_ORDER_LINE_NUMBER int, SALES_ORDER_LINE_ID int, RETURN_ORDER_NUMBER varchar(100), RETURN_ORDER_LINE_NUMBER int, RETURN_TRACKING_NUMBER varchar(100), ITEM_CATEGORY nvarchar(1000), INBOUND_CHANNEL_METHOD varchar(15), OUTBOUND_CHANNEL_METHOD varchar(15), PIR_CODE varchar(20), PIR_MESSAGE nvarchar(MAX), SERVICE_TYPE varchar(20), PROPOSED_DISPOSITION_TYPE varchar(50), FINAL_DISPOSITION_TYPE varchar(50), IS_OVERRIDE tinyint, SELLER_COUNTRY_CODE varchar(10), IS_CONSUMABLE tinyint, ITEM_ID varchar(100), LEGACY_SELLER_ID varchar(100), ITEM_BARCODE_VALUE varchar(40), GTIN varchar(40), ITEM_UPC varchar(40), CASE_UPC varchar(40), QUESTION nvarchar(MAX), IS_FRAGILE tinyint, IS_HAZMAT tinyint, ITEM_CONDITION varchar(100), CREATE_TS datetime2, LAST_CHANGED_TS datetime2, COMPLETE_TS datetime2, PUBLISH_TS datetime2, CREATE_USER varchar(50), LAST_CHANGED_USER varchar(50), DESTINATION_PARENT_CONTAINER_TYPE varchar(20), DESTINATION_CONTAINER_TYPE varchar(20), CONSTRAINT PK__CONTAINER_RLOG__226FF1E5E8444DDEBFA9 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='DELIVERY_EVENT')
CREATE TABLE dbo.DELIVERY_EVENT (ID bigint NOT NULL, facilityNum int, facilityCountryCode varchar(20), DELIVERY_NUMBER bigint, CREATE_TS datetime2, LAST_CHANGE_TS datetime2, EVENT_TYPE varchar(40), URL varchar(255), EVENT_STATUS smallint, RETRIES_COUNT int, EVENT_TYPE_PRIORITY smallint, CONSTRAINT PK__DELIVERY__EVENT PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='DELIVERY_METADATA')
CREATE TABLE dbo.DELIVERY_METADATA (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATED_BY varchar(255), CREATED_DATE datetime2, LAST_UPDATED_DATE datetime2, LAST_UPDATED_BY varchar(255), DELIVERY_NUMBER varchar(255), DOOR_NUMBER varchar(255), TRAILER_NUMBER varchar(255), TOTAL_CASE bigint, TOTAL_CASE_SENT bigint, VERSION bigint, ITEM_OVERRIDES nvarchar(MAX), PO_LINE_OVERRIDES nvarchar(MAX), OSDR_LAST_PROCESSED_DATE datetime2, UNLOADING_COMPLETE_DATE datetime2, DELIVERY_STATUS varchar(20), CARRIER_NAME varchar(100), CARRIER_SCAC_CODE varchar(50), BILL_CODE varchar(10), CONSTRAINT PK__DELIVERY__3214EC27253C4072 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='ENDGAME_LABEL_DETAILS')
CREATE TABLE dbo.ENDGAME_LABEL_DETAILS (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATED_BY varchar(255), CREATED_DATE datetime2, LAST_UPDATED_DATE datetime2, LAST_UPDATED_BY varchar(255), REASON varchar(255), STATUS varchar(255), TCL_NUMBER varchar(255), DELIVERY_NUMBER bigint, TYPE varchar(10) DEFAULT 'TCL', CONSTRAINT PK__ENDGAME___3214EC272A279BCF PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='INSTRUCTION')
CREATE TABLE dbo.INSTRUCTION (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, ACTIVITY_NAME varchar(50), CHILD_CONTAINERS nvarchar(MAX), COMPLETE_TS datetime2, COMPLETE_USER_ID varchar(20), CONTAINER nvarchar(MAX), CREATE_TS datetime2, CREATE_USER_ID varchar(20), DELIVERY_NUMBER bigint NOT NULL, GTIN varchar(40), INSTRUCTION_CODE varchar(20), INSTRUCTION_MSG varchar(40), ITEM_DESCRIPTION varchar(80), LABELS nvarchar(MAX), LAST_CHANGE_TS datetime2, LAST_CHANGE_USER_ID varchar(20), MESSAGE_ID varchar(50), MOVE nvarchar(500), PO_DC_NUMBER varchar(10), PRINT_CHILD_CONTAINER_LABELS tinyint, PROBLEM_TAG_ID varchar(40), PROJECTED_RECEIVED_QTY int, PROJECTED_RECEIVED_QTY_UOM varchar(2), PROVIDER_ID varchar(15), PURCHASE_REFERENCE_LINE_NUMBER int, PURCHASE_REFERENCE_NUMBER varchar(20) NOT NULL, RECEIVED_QUANTITY int, RECEIVED_QUANTITY_UOM varchar(2), SSCC_NUMBER varchar(40), FIRST_EXPIRY_FIRST_OUT tinyint, DELIVERY_DOCUMENT nvarchar(MAX), DOCK_TAG_ID varchar(40), MANUAL_INSTRUCTION tinyint, ORIGINAL_CHANNEL varchar(20), IS_RECEIVE_CORRECTION tinyint, VERSION int DEFAULT 0, INSTRUCTION_SET_ID bigint, CONSTRAINT PK__INSTRUCT__3214EC271E1EFF23 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='PACKAGE_RLOG')
CREATE TABLE dbo.PACKAGE_RLOG (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, PACKAGE_BARCODE_VALUE varchar(100), PACKAGE_BARCODE_TYPE varchar(50), PACKAGE_COST float(53), IS_HIGH_VALUE tinyint, REASON_CODE varchar(100), CREATE_TS datetime2, CREATE_USER varchar(50), IS_SERIAL_SCAN_REQUIRED tinyint DEFAULT 0, CONSTRAINT PK__PACKAGE_RLOG__2EC4A1A3D48B4F15A31E PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='RECEIPT')
CREATE TABLE dbo.RECEIPT (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATE_TS datetime2 NOT NULL, CREATE_USER_ID varchar(32), DELIVERY_NUMBER bigint NOT NULL, DOOR_NUMBER varchar(255), EACH_QTY int, PROBLEM_ID varchar(40), PURCHASE_REFERENCE_LINE_NUMBER int, PURCHASE_REFERENCE_NUMBER varchar(20) NOT NULL, QUANTITY int, QUANTITY_UOM varchar(8), VNPK_QTY int, WHPK_QTY int, FB_OVER_QTY int, FB_OVER_QTY_UOM varchar(8), FB_OVER_REASON_CODE varchar(8), FB_SHORT_QTY int, FB_SHORT_QTY_UOM varchar(8), FB_SHORT_REASON_CODE varchar(8), FB_DAMAGED_QTY int, FB_DAMAGED_QTY_UOM varchar(8), FB_DAMAGED_REASON_CODE varchar(8), FB_REJECTED_QTY int, FB_REJECTED_QTY_UOM varchar(8), FB_REJECTED_REASON_CODE varchar(8), FB_CONCEALED_SHORTAGE_QTY int, FB_CONCEALED_SHORTAGE_REASON_CODE varchar(8), FB_PROBLEM_QTY int, FB_PROBLEM_QTY_UOM varchar(8), FB_REJECTION_COMMENT varchar(255), FINALIZED_USER_ID varchar(32), FINALIZE_TS datetime2, FB_DAMAGED_CLAIM_TYPE varchar(32), VERSION int DEFAULT 0, OSDR_MASTER int, PALLET_QTY int, SSCC_NUMBER varchar(40), SHIPMENT_DOCUMENT_ID varchar(255), ORDER_FILLED_QUANTITY int, CONSTRAINT PK__RECEIPT__3214EC27C2CFDDD3 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='DOCK_TAG')
CREATE TABLE dbo.DOCK_TAG (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATE_TS datetime2, CREATE_USER_ID varchar(20), COMPLETE_TS datetime2, COMPLETE_USER_ID varchar(20), DELIVERY_NUMBER bigint NOT NULL, DOCK_TAG_ID varchar(40) NOT NULL, ITEM_NUMBER bigint, DOCK_TAG_STATUS tinyint, GTIN varchar(40), SCANNED_LOCATION varchar(255), LAST_CHANGED_USER_ID varchar(20), LAST_CHANGED_TS datetime2, DOCK_TAG_TYPE tinyint, WORKSTATION_LOCATION varchar(255), CONSTRAINT PK__DOCK_TAG__3214EC279C7B32B6 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='ENDGAME_UPC_DESTINATION')
CREATE TABLE dbo.ENDGAME_UPC_DESTINATION (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATED_BY varchar(32), CREATED_DATE datetime2, LAST_UPDATED_DATE datetime2, LAST_UPDATED_BY varchar(32), DESTINATION varchar(255), CASE_UPC varchar(40), POSSIBLE_UPCS varchar(255), CONSTRAINT PK__ENDGAME___3214EC271C3D5914 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='FIXTURE_ITEM')
CREATE TABLE dbo.FIXTURE_ITEM (ID bigint NOT NULL, CREATE_TS datetime2, ITEM_NUMBER bigint NOT NULL, DESCRIPTION varchar(80), CONSTRAINT PK__FIXTURE___3214EC277A5777DF PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='ITEM_CATALOG_UPDATE_LOG')
CREATE TABLE dbo.ITEM_CATALOG_UPDATE_LOG (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATE_TS datetime2, CREATE_USER_ID varchar(20), DELIVERY_NUMBER bigint NOT NULL, OLD_ITEM_UPC varchar(40), NEW_ITEM_UPC varchar(40) NOT NULL, ITEM_NUMBER bigint, ITEM_INFO_HAND_KEYED bit, VENDOR_STOCK_NUMBER varchar(40), VENDOR_NUMBER varchar(40), CONSTRAINT PK__ITEM_CAT__3214EC27ED3BF258 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='JMS_EVENT_RETRY')
CREATE TABLE dbo.JMS_EVENT_RETRY (id bigint NOT NULL, RUNTIME_STATUS smallint, FUTURE_PICKUP_TIME datetime2, IS_ALERTED bit, JMS_QUEUE_NAME varchar(255), LAST_UPDATED_DATE datetime2, REQUEST_PAYLOAD varchar(MAX), RETRIES_COUNT bigint, APPLICATION_TYPE smallint, CONSTRAINT PK__JMS_EVEN__3213E83F33C15931 PRIMARY KEY (id));
if not exists (select 1 from sysobjects where name='LABEL_DATA')
CREATE TABLE dbo.LABEL_DATA (ID bigint NOT NULL, facilityNum int, facilityCountryCode varchar(20), DELIVERY_NUMBER bigint, ITEM_NUMBER bigint, MUST_ARRIVE_BEFORE_DATE datetime2, PURCHASE_REFERENCE_NUMBER varchar(20), PURCHASE_REFERENCE_LINE_NUMBER int, LPNS nvarchar(MAX), LABEL varchar(3000), LPNS_COUNT int, POSSIBLE_UPC varchar(400), IS_DA_CONVEYABLE bit, CREATE_TS datetime2, LAST_CHANGE_TS datetime2, SEQUENCE_NO int, LABEL_TYPE tinyint, CONSTRAINT PK__LABEL__DATA PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='NOTIFICATION_LOG')
CREATE TABLE dbo.NOTIFICATION_LOG (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, LOG_TS datetime2, SUMO_RESPONSE varchar(255), TYPE smallint, LOCATION_ID varchar(255), NOTIFICATION_MESSAGE nvarchar(MAX), CONSTRAINT PK__NOTIFICA__3214EC278FD6A96F PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='BATCH_MIGRATION')
CREATE TABLE dbo.BATCH_MIGRATION (id bigint NOT NULL, IP_ADDRESS varchar(255), IS_ON_RETRY bigint, IS_ON_UPDATE bit, LAST_OBJECT_ID varchar(255), MIGRATION_COMPLETE_TIME datetime2, MIGRATION_START_TIME datetime2, MIGRATION_STATUS varchar(255), PREV_BATCH_LAST_OBJECT_ID varchar(255), RETRY_COMPLETE_TIME datetime2, RETRY_START_TIME datetime2, TABLE_TYPE varchar(255), UPDATE_COMPLETE_TIME datetime2, UPDATE_START_TIME datetime2, FUTURE_UPDATE_TIME datetime2, CONSTRAINT PK__BATCH_MI__3213E83F97D64C84 PRIMARY KEY (id));
if not exists (select 1 from sysobjects where name='CT_TRACKER')
CREATE TABLE dbo.CT_TRACKER (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATE_TS datetime2, LPN varchar(50), ACK_KEY varchar(50), SUBMISSION_STATUS smallint, RETRIES_COUNT int, CONSTRAINT PK__CT_TRACK__3214EC271EA58128 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='DELIVERY_ITEM_OVERRIDE')
CREATE TABLE dbo.DELIVERY_ITEM_OVERRIDE (DELIVERY_NUMBER bigint NOT NULL, ITEM_NUMBER bigint NOT NULL, facilityCountryCode varchar(255) NOT NULL, facilityNum int NOT NULL, TEMP_PALLET_TI int NOT NULL, TEMP_PALLET_HI int, VERSION int NOT NULL, CONSTRAINT PK__DELIVERY_ITEM_OVERRIDE__3213E83F97D64C84 PRIMARY KEY (DELIVERY_NUMBER, ITEM_NUMBER, facilityCountryCode, facilityNum));
if not exists (select 1 from sysobjects where name='ITEM_TRACKER')
CREATE TABLE dbo.ITEM_TRACKER (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, PARENT_TRACKING_ID varchar(100), TRACKING_ID varchar(100), GTIN varchar(40), REASON_CODE varchar(100), CREATE_TS datetime2, CREATE_USER varchar(50), CONSTRAINT PK__ITEM_TRACKER__F7A0B19825DD4A36B94B PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='LABEL_META_DATA')
CREATE TABLE dbo.LABEL_META_DATA (ID bigint NOT NULL, facilityCountryCode varchar(255) NOT NULL, facilityNum int NOT NULL, LABEL_ID int NOT NULL, LABEL_NAME varchar(50), DESCRIPTION varchar(50), LPAAS_FORMAT_NAME varchar(50) NOT NULL, JSON_TEMPLATE nvarchar(MAX) NOT NULL, ZPL nvarchar(MAX), MPCL_F nvarchar(MAX), MPCL_D nvarchar(MAX), CONSTRAINT PK__LABEL_ME__3214EC27BCF78CC4 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='MIGRATED_DATA_INFO')
CREATE TABLE dbo.MIGRATED_DATA_INFO (id bigint NOT NULL, BATCH_MIGRATION_ID bigint, FAILED_MIGRATION_DATA nvarchar(MAX), IP_ADDRESS varchar(255), IS_COMPLETED bit, IS_ON_UPDATE_FAILURE bit, MIGRATION_COMPLETE_TIME datetime2, MIGRATION_START_TIME datetime2, OBJECT_ID varchar(255), TABLE_TYPE varchar(255), RETRY_COUNT bigint, DATA_HASH bigint, CONSTRAINT PK__MIGRATED__3213E83F69779B52 PRIMARY KEY (id));
if not exists (select 1 from sysobjects where name='PRINTJOB')
CREATE TABLE dbo.PRINTJOB (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, COMPLETE_TS datetime2, COMPLETED_LABEL_IDENTIFIER nvarchar(MAX), CREATE_TS datetime2, CREATE_USER_ID varchar(32) NOT NULL, DELIVERY_NUMBER bigint NOT NULL, INSTRUCTION_ID bigint NOT NULL, LABEL_IDENTIFIER nvarchar(MAX) NOT NULL, CONSTRAINT PK__PRINTJOB__3214EC270F9988A9 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='PROBLEM')
CREATE TABLE dbo.PROBLEM (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATE_TS datetime2, CREATE_USER_ID varchar(32), DELIVERY_NUMBER bigint NOT NULL, ISSUE_ID varchar(50) NOT NULL, LAST_CHANGE_TS datetime2, LAST_CHANGE_USER_ID varchar(32), PROBLEM_STATUS varchar(32) NOT NULL, PROBLEM_TAG_ID varchar(40) NOT NULL, RESOLUTION_ID varchar(50), PROBLEM_RESPONSE nvarchar(MAX), CONSTRAINT PK__PROBLEM__3214EC27DDC12129 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='PURGE_DATA')
CREATE TABLE dbo.PURGE_DATA (ID bigint NOT NULL, ENTITY_TYPE varchar(50) NOT NULL, LAST_DELETED_ID bigint NOT NULL, STATUS tinyint NOT NULL, CREATE_TS datetime2, LAST_CHANGE_TS datetime2, CONSTRAINT PK__PURGE_DA__3214EC2735EA62D5 PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='RECEIVING_COUNTER')
CREATE TABLE dbo.RECEIVING_COUNTER (ID bigint NOT NULL, TYPE varchar(255) NOT NULL, facilityCountryCode varchar(255), facilityNum int, CREATED_BY varchar(255), CREATED_DATE datetime2, LAST_UPDATED_DATE datetime2, LAST_UPDATED_BY varchar(255), COUNTER_VALUE bigint, PREFIX varchar(2), VERSION bigint, CONSTRAINT PK__RECEIVIN__3214EC27107FE2CF PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='USER_LOCATION')
CREATE TABLE dbo.USER_LOCATION (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, USER_ID varchar(255), LOCATION_ID varchar(255), CREATE_TS datetime2, LOCATION_TYPE tinyint, PARENT_LOCATION_ID varchar(255), CONSTRAINT PK__USER_LOC__3214EC2739A19BFE PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='shedlock')
CREATE TABLE dbo.shedlock (name varchar(64) NOT NULL, lock_until datetime, locked_at datetime, locked_by varchar(255) NOT NULL, CONSTRAINT PK_shedlock PRIMARY KEY (name));
--create non-clustered index--
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_DELIVERY' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_DELIVERY ON dbo.CONTAINER(DELIVERY_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_INSTRUCTION' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_INSTRUCTION ON dbo.CONTAINER(INSTRUCTION_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_PARENT_TRACKINGID' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_PARENT_TRACKINGID ON dbo.CONTAINER(PARENT_TRACKING_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'SSCC_NUMBER_BY_CONTAINER' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX SSCC_NUMBER_BY_CONTAINER ON dbo.CONTAINER(SSCC_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UK_9sj85rkilen88u713i3a3y3nm' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD CONSTRAINT UK_9sj85rkilen88u713i3a3y3nm UNIQUE (TRACKING_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_BY_TRACKINGID' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_BY_TRACKINGID ON dbo.CONTAINER_ITEM(TRACKING_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_BY_TRACKINGIDANDPO' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_BY_TRACKINGIDANDPO ON dbo.CONTAINER_ITEM(TRACKING_ID, PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_FacCountryCodeFaculNum' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_FacCountryCodeFaculNum ON dbo.CONTAINER_ITEM(facilityCountryCode, facilityNum);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_DISPOSITION_TYPE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_DISPOSITION_TYPE ON dbo.CONTAINER_RLOG(CREATE_TS, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_PACKAGE_BARCODE_VALUE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_PACKAGE_BARCODE_VALUE ON dbo.CONTAINER_RLOG(PACKAGE_BARCODE_VALUE, CREATE_TS, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_PACKAGE_BARCODE_VALUE_TYPE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_PACKAGE_BARCODE_VALUE_TYPE ON dbo.CONTAINER_RLOG(PACKAGE_BARCODE_VALUE, CREATE_TS, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_PIR_CODE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_PIR_CODE ON dbo.CONTAINER_RLOG(CREATE_TS, PIR_CODE, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_SERVICE_TYPE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_SERVICE_TYPE ON dbo.CONTAINER_RLOG(CREATE_TS, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UK_33FB0D9685A0423593B8' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD CONSTRAINT UK_33FB0D9685A0423593B8 UNIQUE (TRACKING_ID);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_EVENT_BY_DELIVERY' AND object_id = OBJECT_ID('DELIVERY_EVENT'))
CREATE NONCLUSTERED INDEX DELIVERY_EVENT_BY_DELIVERY ON dbo.DELIVERY_EVENT(facilityNum, facilityCountryCode, DELIVERY_NUMBER);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_EVENT_BY_EVENT_STATUS' AND object_id = OBJECT_ID('DELIVERY_EVENT'))
CREATE NONCLUSTERED INDEX DELIVERY_EVENT_BY_EVENT_STATUS ON dbo.DELIVERY_EVENT(EVENT_STATUS);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_METADATA_BY_DELIVERY_NUMBER' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
CREATE UNIQUE NONCLUSTERED INDEX DELIVERY_METADATA_BY_DELIVERY_NUMBER ON dbo.DELIVERY_METADATA(facilityCountryCode, facilityNum, DELIVERY_NUMBER);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_METADATA_BY_DELIVERY_STATUS' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
CREATE NONCLUSTERED INDEX DELIVERY_METADATA_BY_DELIVERY_STATUS ON dbo.DELIVERY_METADATA(facilityCountryCode, facilityNum, DELIVERY_STATUS);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_LABEL_DETAILS_BY_DELIVERY_NUMBER' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
CREATE NONCLUSTERED INDEX ENDGAME_LABEL_DETAILS_BY_DELIVERY_NUMBER ON dbo.ENDGAME_LABEL_DETAILS(DELIVERY_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_LABEL_DETAILS_BY_TCL' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
CREATE UNIQUE NONCLUSTERED INDEX ENDGAME_LABEL_DETAILS_BY_TCL ON dbo.ENDGAME_LABEL_DETAILS(TCL_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_DEL_CODE' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_DEL_CODE ON dbo.INSTRUCTION(DELIVERY_NUMBER, INSTRUCTION_CODE, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_DEL_CTS_CODE' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_DEL_CTS_CODE ON dbo.INSTRUCTION(DELIVERY_NUMBER, COMPLETE_TS, INSTRUCTION_CODE, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_DEL_PROB_CODE' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_DEL_PROB_CODE ON dbo.INSTRUCTION(DELIVERY_NUMBER, PROBLEM_TAG_ID, INSTRUCTION_CODE, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_DELIVERY_NUMBER' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_DELIVERY_NUMBER ON dbo.INSTRUCTION(DELIVERY_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_MESSAGEID' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_MESSAGEID ON dbo.INSTRUCTION(MESSAGE_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_PO_POLINE' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_PO_POLINE ON dbo.INSTRUCTION(PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER, COMPLETE_TS, INSTRUCTION_CODE, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'INSTRUCTION_BY_PROB_CODE' AND object_id = OBJECT_ID('INSTRUCTION'))
CREATE NONCLUSTERED INDEX INSTRUCTION_BY_PROB_CODE ON dbo.INSTRUCTION(PROBLEM_TAG_ID, INSTRUCTION_CODE, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_DELIVERY' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_DELIVERY ON dbo.RECEIPT(DELIVERY_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_DELIVERY_PO' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_DELIVERY_PO ON dbo.RECEIPT(DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_PO' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_PO ON dbo.RECEIPT(PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_PROB' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_PROB ON dbo.RECEIPT(PROBLEM_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DOCK_TAG_BY_DELIVERY_DOCKTAGSTATUS' AND object_id = OBJECT_ID('DOCK_TAG'))
CREATE NONCLUSTERED INDEX DOCK_TAG_BY_DELIVERY_DOCKTAGSTATUS ON dbo.DOCK_TAG(facilityNum, facilityCountryCode, DELIVERY_NUMBER, DOCK_TAG_STATUS);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DOCK_TAG_BY_TRACKING_ID' AND object_id = OBJECT_ID('DOCK_TAG'))
CREATE UNIQUE NONCLUSTERED INDEX DOCK_TAG_BY_TRACKING_ID ON dbo.DOCK_TAG(facilityCountryCode, facilityNum, DOCK_TAG_ID);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_DESTINATION_BY_CASE_UPC' AND object_id = OBJECT_ID('ENDGAME_UPC_DESTINATION'))
CREATE UNIQUE NONCLUSTERED INDEX ENDGAME_DESTINATION_BY_CASE_UPC ON dbo.ENDGAME_UPC_DESTINATION(CASE_UPC, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'FIXTURE_ITEM_BY_ITEM_NUMBER' AND object_id = OBJECT_ID('FIXTURE_ITEM'))
CREATE UNIQUE NONCLUSTERED INDEX FIXTURE_ITEM_BY_ITEM_NUMBER ON dbo.FIXTURE_ITEM(ITEM_NUMBER);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ITEM_CATALOG_UPDATE_LOG_BY_DELIVERY_NUMBER' AND object_id = OBJECT_ID('ITEM_CATALOG_UPDATE_LOG'))
CREATE NONCLUSTERED INDEX ITEM_CATALOG_UPDATE_LOG_BY_DELIVERY_NUMBER ON dbo.ITEM_CATALOG_UPDATE_LOG(DELIVERY_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'JMS_EVENT_RETRY_BY_TYPE_STATUS_TIME' AND object_id = OBJECT_ID('JMS_EVENT_RETRY'))
CREATE NONCLUSTERED INDEX JMS_EVENT_RETRY_BY_TYPE_STATUS_TIME ON dbo.JMS_EVENT_RETRY(APPLICATION_TYPE, RUNTIME_STATUS, FUTURE_PICKUP_TIME);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_BY_DEL_PO_POL' AND object_id = OBJECT_ID('LABEL_DATA'))
CREATE NONCLUSTERED INDEX LABEL_DATA_BY_DEL_PO_POL ON dbo.LABEL_DATA(facilityNum, facilityCountryCode, DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'NOTIFICATION_LOG_BY_LOCATION' AND object_id = OBJECT_ID('NOTIFICATION_LOG'))
CREATE NONCLUSTERED INDEX NOTIFICATION_LOG_BY_LOCATION ON dbo.NOTIFICATION_LOG(facilityCountryCode, facilityNum, LOCATION_ID);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UQ__CT_TRACK__C6568E1D7EEAA3A4' AND object_id = OBJECT_ID('CT_TRACKER'))
ALTER TABLE dbo.CT_TRACKER ADD CONSTRAINT UQ__CT_TRACK__C6568E1D7EEAA3A4 UNIQUE (LPN);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UQ__LABEL_ME__5AF3DD275B8C3F54' AND object_id = OBJECT_ID('LABEL_META_DATA'))
ALTER TABLE dbo.LABEL_META_DATA ADD CONSTRAINT UQ__LABEL_ME__5AF3DD275B8C3F54 UNIQUE (LABEL_ID, facilityCountryCode, facilityNum);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'PRINT_BY_DELIVERY' AND object_id = OBJECT_ID('PRINTJOB'))
CREATE NONCLUSTERED INDEX PRINT_BY_DELIVERY ON dbo.PRINTJOB(DELIVERY_NUMBER, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'PRINT_BY_INSTRUCTION' AND object_id = OBJECT_ID('PRINTJOB'))
CREATE NONCLUSTERED INDEX PRINT_BY_INSTRUCTION ON dbo.PRINTJOB(INSTRUCTION_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'PROBLEM_BY_TAG' AND object_id = OBJECT_ID('PROBLEM'))
CREATE NONCLUSTERED INDEX PROBLEM_BY_TAG ON dbo.PROBLEM(PROBLEM_TAG_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UK_k5ipc20br8sx04rt5qjgt48a4' AND object_id = OBJECT_ID('PROBLEM'))
ALTER TABLE dbo.PROBLEM ADD CONSTRAINT UK_k5ipc20br8sx04rt5qjgt48a4 UNIQUE (PROBLEM_TAG_ID);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UQ__PURGE_DA__0BB278B6AA1A385E' AND object_id = OBJECT_ID('PURGE_DATA'))
ALTER TABLE dbo.PURGE_DATA ADD CONSTRAINT UQ__PURGE_DA__0BB278B6AA1A385E UNIQUE (ENTITY_TYPE);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'USER_LOCATION_BY_LOCATION' AND object_id = OBJECT_ID('USER_LOCATION'))
CREATE NONCLUSTERED INDEX USER_LOCATION_BY_LOCATION ON dbo.USER_LOCATION(LOCATION_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'USER_LOCATION_BY_PARENT_LOCATION' AND object_id = OBJECT_ID('USER_LOCATION'))
CREATE NONCLUSTERED INDEX USER_LOCATION_BY_PARENT_LOCATION ON dbo.USER_LOCATION(PARENT_LOCATION_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'USER_LOCATION_BY_USER' AND object_id = OBJECT_ID('USER_LOCATION'))
CREATE NONCLUSTERED INDEX USER_LOCATION_BY_USER ON dbo.USER_LOCATION(USER_ID, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_TRACKINGID' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_TRACKINGID ON dbo.CONTAINER (TRACKING_ID, facilityNum, facilityCountryCode);
--create sequence--
if not exists (select 1 from sysobjects where name='CT_TRACKER_SEQUENCE')
CREATE SEQUENCE dbo.CT_TRACKER_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='DELIVERY_EVENT_SEQUENCE')
CREATE SEQUENCE dbo.DELIVERY_EVENT_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='DELIVERY_METADATA_SEQUENCE')
CREATE SEQUENCE dbo.DELIVERY_METADATA_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='DOCK_TAG_SEQUENCE')
CREATE SEQUENCE dbo.DOCK_TAG_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='ENDGAME_LABEL_DETAILS_SEQUENCE')
CREATE SEQUENCE dbo.ENDGAME_LABEL_DETAILS_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='ENDGAME_UPC_SLOTTING_SEQUENCE')
CREATE SEQUENCE dbo.ENDGAME_UPC_SLOTTING_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='FIXTURE_ITEM_SEQUENCE')
CREATE SEQUENCE dbo.FIXTURE_ITEM_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='INSTRUCTION_SET_ID_SEQUENCE')
CREATE SEQUENCE dbo.INSTRUCTION_SET_ID_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='ITEM_CATALOG_SEQUENCE')
CREATE SEQUENCE dbo.ITEM_CATALOG_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='LABEL_DATA_SEQUENCE')
CREATE SEQUENCE dbo.LABEL_DATA_SEQUENCE START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='NOTIFICATION_LOG_SEQUENCE')
CREATE SEQUENCE dbo.NOTIFICATION_LOG_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='PURGE_DATA_SEQUENCE')
CREATE SEQUENCE dbo.PURGE_DATA_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='RECEIVING_COUNTER_SEQUENCE')
CREATE SEQUENCE dbo.RECEIVING_COUNTER_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='USER_LOCATION_SEQUENCE')
CREATE SEQUENCE dbo.USER_LOCATION_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='container_item_sequence')
CREATE SEQUENCE dbo.container_item_sequence START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='container_rlog_sequence')
CREATE SEQUENCE dbo.container_rlog_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='container_sequence')
CREATE SEQUENCE dbo.container_sequence START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='hibernate_sequence')
CREATE SEQUENCE dbo.hibernate_sequence START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='instruction_sequence')
CREATE SEQUENCE dbo.instruction_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='item_tracker_sequence')
CREATE SEQUENCE dbo.item_tracker_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='jms_event_retry_sequence')
CREATE SEQUENCE dbo.jms_event_retry_sequence START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='labelMetaData_Sequence')
CREATE SEQUENCE dbo.labelMetaData_Sequence START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807
if not exists (select 1 from sysobjects where name='package_rlog_sequence')
CREATE SEQUENCE dbo.package_rlog_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='printjob_sequence')
CREATE SEQUENCE dbo.printjob_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='problem_sequence')
CREATE SEQUENCE dbo.problem_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='receipt_sequence')
CREATE SEQUENCE dbo.receipt_sequence START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists (select 1 from sysobjects where name='spec_sequence')
CREATE SEQUENCE dbo.spec_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
--create store procedure--
GO
CREATE OR ALTER PROCEDURE [dbo].[purge_table] @TableName varchar(100),@IdFieldName varchar(50),@TimeStampFieldName varchar(50), @RetentionDays int, @BatchSize bigint, @NumberOfRecords bigint, @WaitTimeInSec int
AS
BEGIN
  DECLARE @startId bigint = 0
  DECLARE @lastId bigint = 0
  DECLARE @recordSize bigint = 500
  DECLARE @batch bigint = 500
  DECLARE @rtDay int = 90
  DECLARE @waitInSec int = 4
  DECLARE @earliestTime datetime2
  DECLARE @createTime datetime2= GETDATE()
  DECLARE @TabName varchar(100) = ''
  DECLARE @Id varchar(100) = 'id'
  DECLARE @CTS varchar(100) = 'CREATE_TS'
  DECLARE @endId bigint
  DECLARE @query_select_id_ts nvarchar(max)
  DECLARE @query_select_max_cts nvarchar(max)
  DECLARE @query_delete_purge_records nvarchar(max)
  DECLARE @query_wait nvarchar(max)
  IF (@TableName IS NOT NULL) OR (LEN(@TableName) > 0)
                BEGIN
        IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = @TableName)
           BEGIN
               PRINT 'Table '+@TableName+' not exist, Please try again with correct table name'
               RETURN
          END
        ELSE
          BEGIN
            SET @TabName='dbo.'+@TableName
            PRINT 'Table Name '+ @TableName +' set as '+ @TabName
          END
        END
  ELSE
      BEGIN
         PRINT 'Table Name '+ @TableName +' can not be null '
         RETURN
      END
  IF (@IdFieldName IS NOT NULL) OR (LEN(@IdFieldName) > 0)
                BEGIN
                  SET @Id=@IdFieldName
        PRINT 'Table '+ @TableName +'  Id name set as '+ @Id
                END
  IF (@TimeStampFieldName IS NOT NULL) OR (LEN(@TimeStampFieldName) > 0)
                BEGIN
                  SET @CTS=@TimeStampFieldName
        PRINT 'Table '+ @TableName +'  Create TimeStamp set as '+ @CTS
                END
  IF (((@BatchSize IS NOT NULL) OR (LEN(@BatchSize) > 0)) AND (@BatchSize>0 AND @BatchSize<100000))
                BEGIN
                  SET @batch=@BatchSize
        PRINT 'Table '+ @TableName +'  Batch size set as '+ CAST(@batch as varchar)
                END
  ELSE
      BEGIN
         PRINT 'BatchSize Can be between 1 and 100000 only'
         RETURN
      END
  IF (((@NumberOfRecords IS NOT NULL) OR (LEN(@NumberOfRecords) > 0)) AND @NumberOfRecords>=@BatchSize)
                BEGIN
                  SET @recordSize=@NumberOfRecords
        PRINT 'Table '+ @TableName +'  Delete record size set as '+ CAST(@recordSize as varchar)
                END
  ELSE
      BEGIN
         PRINT 'BatchSize Can not be greater than Number of records'
        RETURN
      END
  IF ((@RetentionDays IS NOT NULL) OR (LEN(@RetentionDays) > 0)) AND (@RetentionDays>0 AND @RetentionDays<=365)
                BEGIN
                  SET @rtDay=@RetentionDays
        SET @earliestTime = DATEADD(DAY,-@rtDay,GETDATE())
        PRINT 'Table '+ @TableName +'  Retention day set as '+ CAST(@rtDay as varchar)
                END
  ELSE
      BEGIN
        PRINT 'RententionDays ' + CAST(@RetentionDays as varchar)+' are not correct, Please provide between 0 and 365'
        RETURN
      END
  IF (((@WaitTimeInSec IS NOT NULL) OR (LEN(@WaitTimeInSec)) > 0) AND (@WaitTimeInSec>=0 AND @WaitTimeInSec<=86400))
                BEGIN
                  SET @waitInSec=@WaitTimeInSec
        PRINT 'Table '+ @TableName +'  Waiting Seconds set as '+ CAST(@waitInSec as varchar)
                END
  ELSE
     BEGIN
        PRINT 'WaitTimeInSec ' + CAST(@WaitTimeInSec as varchar)+' is not correct, Please provide between 0 and 86400, means job can wait 1 Day max'
        RETURN
      END
  BEGIN
    set @query_select_id_ts = N'select @startId='+@Id+', @createTime='+@CTS+' from '+ @TabName + ' WHERE '+CAST(@Id as varchar)+'=(select min('+CAST(@Id as varchar)+') from '+@TabName+')'
    EXEC sys.sp_executesql @query_select_id_ts, N'@startId int OUTPUT, @createTime datetime2 OUTPUT',@startId OUTPUT, @createTime OUTPUT
  END
  BEGIN
    SET @lastId = @startId + @recordSize
    set @query_select_max_cts = N'select top(1) @createTime='+@CTS +' from '+@TabName+' WHERE '+ CAST(@Id as varchar)+ '<=' + CAST(@lastId as varchar) + ' order by  '+CAST(@Id as varchar)+' desc'
    EXEC sys.sp_executesql @query_select_max_cts, N'@createTime datetime2 OUTPUT',@createTime OUTPUT
  END
    if(@createTime>=@earliestTime)
    BEGIN
       PRINT 'Table '+ @TableName +'  Nothing to delete as createTime of last id is within retention time '+ CAST(@startId as varchar)
      RETURN
    END
    WHILE @startId < @lastId
    BEGIN
       PRINT 'Table '+ @TableName +'  Deleting from '+CAST(@startId as varchar)
      SET @endId = @startId+@batch
      BEGIN TRANSACTION
         set @query_delete_purge_records = 'delete from '+@TabName+' where '+@Id+'>='+ CAST(@startId as varchar)+' and '+@Id+'<= '+ CAST(@endId as varchar)
        EXEC sys.sp_executesql @query_delete_purge_records, N'@startId int,@endId int',@startId,@endId
      COMMIT TRANSACTION
      SET @startId = @endId
      set @query_wait = 'WAITFOR DELAY ''00:00:'+CAST(@waitInSec as varchar)+''''
      EXEC sys.sp_executesql @query_wait, N'@waitInSec int',@waitInSec
    END
    PRINT 'Table '+ @TableName +'  Done.'
END
-- changeset a0b02ft:1644838097307-2 (runInTransaction:false)
--create fulltext index--
if not exists (SELECT 1 from  sys.fulltext_catalogs where name = 'ft')
CREATE FULLTEXT CATALOG ft AS DEFAULT;
if not exists (SELECT 1 FROM sys.tables t JOIN sys.fulltext_indexes i ON t.object_id = i.object_id where t.name = 'LABEL_DATA')
CREATE FULLTEXT INDEX ON dbo.LABEL_DATA (LPNS LANGUAGE 1033)  KEY INDEX PK__LABEL__DATA  WITH STOPLIST = SYSTEM

-- changeset p0v00iz:SCTA-22857_1_1
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_BY_GTIN_AND_SERIAL' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_BY_GTIN_AND_SERIAL ON CONTAINER_ITEM(gtin, serial) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_BY_FACILITY_GTIN_AND_SERIAL' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_BY_FACILITY_GTIN_AND_SERIAL ON CONTAINER_ITEM(facilityNum, facilityCountryCode, gtin, serial) WITH (ONLINE = ON);
--changeset p0v00iz:OPIF-122188-sequence
if not exists (select 1 from sysobjects where name='processing_info_sequence')
CREATE SEQUENCE dbo.processing_info_sequence START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
--changeset vn522bw:NGRCV-7433
if not exists (select 1 from sysobjects where name='LPN_SWAP')
CREATE TABLE LPN_SWAP (ID bigint NOT NULL, LPN varchar(255),SWAPPED_LPN varchar(255),DESTINATION varchar(255),
SWAPPED_DESTINATION varchar(255),GROUP_NUMBER varchar(255),ITEM_NUMBER int,PO_NUMBER varchar(255),
PO_TYPE varchar(255),SWAP_STATUS tinyint,CREATE_TS datetime2,LAST_CHANGE_TS datetime2,SWAP_TS datetime2,
PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='LPN_SWAP_SEQUENCE')
CREATE SEQUENCE LPN_SWAP_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;
--changeset j0p0iz1:NGRCV-7545
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SELLER_ID' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD SELLER_ID varchar(32);
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SELLER_TYPE' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD SELLER_TYPE varchar(20);

-- changeset s0c034w:SCTEG-981-mfc
if not exists (select 1 from sysobjects where name='DECANT_AUDIT')
CREATE TABLE dbo.DECANT_AUDIT (
                                  ID bigint NOT NULL,
                                  facilityCountryCode varchar(255),
                                  facilityNum int ,
                                  CREATED_BY varchar(32) NULL,
                                  CREATED_DATE datetime2 NULL,
                                  LAST_UPDATED_DATE datetime2 NULL,
                                  LAST_UPDATED_BY varchar(32) NULL,
                                  ALERT_REQUIRED bit,
                                  MSG_IDEMPOTANT_ID varchar(255),
                                  DECANTED_CONTAINER_TRACKING_ID varchar(255) NULL,
                                  DECANTED_PAYLOAD nvarchar(MAX) NULL,
                                  PROCESSING_TS datetime2 NULL,
                                  QTY_REMAINING bigint NULL,
                                  STATUS varchar(255),
                                  UPC varchar(255)  NULL,
                                  CONSTRAINT PK__DECANT_A__3214EC279012B49E PRIMARY KEY (ID)
);


if not exists (select 1 from sysobjects where name='DECANT_AUDIT_SEQUENCE')
CREATE SEQUENCE dbo.DECANT_AUDIT_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'ASN_SHIPMENT_ID' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD ASN_SHIPMENT_ID VARCHAR(255) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'INVOICE_NUMBER' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD INVOICE_NUMBER VARCHAR(50) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'ORDER_FILLED_QTY' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD ORDER_FILLED_QTY BIGINT default 0;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'ORDER_FILLED_QTY_UOM' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD ORDER_FILLED_QTY_UOM VARCHAR(50) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'INVOICE_NUMBER' AND object_id = OBJECT_ID('RECEIPT'))
ALTER TABLE dbo.RECEIPT ADD INVOICE_NUMBER VARCHAR(50) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'INVOICE_LINE_NUMBER' AND object_id = OBJECT_ID('RECEIPT'))
ALTER TABLE dbo.RECEIPT ADD INVOICE_LINE_NUMBER BIGINT default NULL;

if not exists (select 1 from sysobjects where name='ASN_METADATA')
CREATE TABLE ASN_METADATA (
                              ID bigint NOT NULL,
                              facilityCountryCode varchar(255),
                              facilityNum int,
                              CREATED_BY varchar(32) NULL,
                              CREATED_DATE datetime2,
                              LAST_UPDATED_DATE datetime2,
                              LAST_UPDATED_BY varchar(32) NULL,
                              ASN_ID varchar(255) ,
                              DELIVERY_NUMBER varchar(255),
                              ASN_DOC_ID varchar(255) NULL,
                              ASN_SHIPMENT_ID varchar(255)  NULL,
                              ASN_TYPE varchar(255) NULL,
                              VERSION bigint NULL,
                              CONSTRAINT PK__ASN_META__3214EC276B611CA9 PRIMARY KEY (ID)
);


if not exists (select 1 from sysobjects where name='ASN_METADATA_SEQUENCE')
CREATE SEQUENCE dbo.ASN_METADATA_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

--changeset m0s0mqs:NGRCV-7783
if not exists(SELECT 1 FROM sys.columns WHERE name = 'REGULATED_ITEM_TYPE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD REGULATED_ITEM_TYPE varchar(32) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'REGULATED_ITEM_LABEL_CODE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD REGULATED_ITEM_LABEL_CODE varchar(20) default NULL;

--changeset r0r00g0:SCTEG-982-MFC
if not exists(SELECT 1 FROM sys.columns WHERE name = 'INVOICE_LINE_NUMBER' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD INVOICE_LINE_NUMBER int default NULL;

--changeset r0r00g0:SCTEG-982-MFC-2
if exists(SELECT 1 FROM sys.columns WHERE name = 'QTY_REMAINING' AND object_id = OBJECT_ID('DECANT_AUDIT'))
ALTER TABLE dbo.DECANT_AUDIT DROP COLUMN QTY_REMAINING;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'RECEIPT_ID' AND object_id = OBJECT_ID('DECANT_AUDIT'))
ALTER TABLE dbo.DECANT_AUDIT ADD RECEIPT_ID int default NULL;

--changeset sks0013:NGRCV-7908
if exists(SELECT 1 FROM sys.columns WHERE name = 'DESCRIPTION' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ALTER COLUMN DESCRIPTION varchar(255);

if exists(SELECT 1 FROM sys.columns WHERE name = 'SECONDARY_DESCRIPTION' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ALTER COLUMN SECONDARY_DESCRIPTION varchar(255);

if exists(SELECT 1 FROM sys.columns WHERE name = 'ITEM_DESCRIPTION' AND object_id = OBJECT_ID('INSTRUCTION'))
ALTER TABLE dbo.INSTRUCTION ALTER COLUMN ITEM_DESCRIPTION varchar(255);

--changeset a0b02ft:SCTNGMS-3430
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SELLER_ID' AND object_id = OBJECT_ID('ENDGAME_UPC_DESTINATION'))
ALTER TABLE dbo.ENDGAME_UPC_DESTINATION ADD SELLER_ID varchar(32) NOT NULL CONSTRAINT DF_UPC_DESTINATION_SELLER_ID DEFAULT 'F55CDC31AB754BB68FE0B39041159D63';
if exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_DESTINATION_BY_CASE_UPC' AND object_id = OBJECT_ID('ENDGAME_UPC_DESTINATION'))
DROP INDEX ENDGAME_DESTINATION_BY_CASE_UPC on dbo.ENDGAME_UPC_DESTINATION
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_DESTINATION_BY_CASE_UPC_AND_SELLER_ID' AND object_id = OBJECT_ID('ENDGAME_UPC_DESTINATION'))
CREATE UNIQUE NONCLUSTERED INDEX ENDGAME_DESTINATION_BY_CASE_UPC_AND_SELLER_ID ON dbo.ENDGAME_UPC_DESTINATION(CASE_UPC, SELLER_ID, facilityNum, facilityCountryCode);

--changeset r0r00g0:SCTETE-7301-2
if exists
(
    SELECT default_constraints.name
    FROM sys.all_columns
        INNER JOIN sys.tables
            ON all_columns.object_id = tables.object_id
        INNER JOIN sys.schemas
            ON tables.schema_id = schemas.schema_id
        INNER JOIN sys.default_constraints
            ON all_columns.default_object_id = default_constraints.object_id
    WHERE schemas.name = 'dbo'
          AND tables.name = 'DECANT_AUDIT'
          AND all_columns.name = 'RECEIPT_ID'
)
BEGIN
    DECLARE @CONSTRAINT_NAME nvarchar(max),
            @SQL_CMD nvarchar(max)
    SELECT @CONSTRAINT_NAME = default_constraints.name
    FROM sys.all_columns
        INNER JOIN sys.tables
            ON all_columns.object_id = tables.object_id
        INNER JOIN sys.schemas
            ON tables.schema_id = schemas.schema_id
        INNER JOIN sys.default_constraints
            ON all_columns.default_object_id = default_constraints.object_id
    WHERE schemas.name = 'dbo'
          AND tables.name = 'DECANT_AUDIT'
          AND all_columns.name = 'RECEIPT_ID'
    set @SQL_CMD = 'ALTER TABLE DECANT_AUDIT DROP CONSTRAINT ' + @CONSTRAINT_NAME
    EXEC (@SQL_CMD)
END;
if exists
(
    SELECT 1
    FROM sys.columns
    WHERE name = 'RECEIPT_ID'
          AND object_id = OBJECT_ID('DECANT_AUDIT')
)
    ALTER TABLE dbo.DECANT_AUDIT ALTER COLUMN RECEIPT_ID nvarchar(MAX);
    
--changeset s0s0nug:APPRC-287
if not exists(SELECT 1 FROM sys.columns WHERE name = 'GOODWILL_REASON' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD GOODWILL_REASON varchar(30) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'IS_GOODWILL' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD IS_GOODWILL BIT default NULL;

--changeset m0s0mqs:APPRC-236
if not exists(SELECT 1 FROM sys.columns WHERE name = 'IS_HAZARDOUS' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD IS_HAZARDOUS BIT default NULL;

--changeset a0b02ft:SCTNGMS-4153
if not exists(SELECT 1 FROM sys.columns WHERE name = 'APPLICATION_FLOW' AND object_id = OBJECT_ID('JMS_EVENT_RETRY'))
ALTER TABLE dbo.JMS_EVENT_RETRY ADD APPLICATION_FLOW smallint;

--changeset i0a02l3:SCTA-1156
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SOURCE_MESSAGE_ID' AND object_id = OBJECT_ID('INSTRUCTION'))
ALTER TABLE dbo.INSTRUCTION ADD SOURCE_MESSAGE_ID varchar(50) default NULL;

--changeset vn54o43:WFM-642
if not exists (select 1 from sysobjects where name='EVENT_SEQUENCE')
CREATE SEQUENCE dbo.EVENT_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

if not exists (select 1 from sysobjects where name='EVENT')
CREATE TABLE dbo.EVENT (ID bigint NOT NULL, EVENT_KEY varchar(255), STATUS varchar(20), PAYLOAD nvarchar(MAX), RETRY_COUNT tinyint,
PICKUP_TIME datetime2, EVENT_TYPE varchar(255), facilityCountryCode varchar(255), facilityNum int, CREATED_BY varchar(255),
CREATED_DATE datetime2, LAST_UPDATED_DATE datetime2, LAST_UPDATED_BY varchar(255), METADATA varchar(80),
CONSTRAINT PK__EVENT PRIMARY KEY (ID));

--changeset r0r00g0:WFM-679
if not exists(SELECT 1 FROM sys.columns WHERE name = 'DELIVERY_NUMBER' AND object_id = OBJECT_ID('EVENT'))
ALTER TABLE dbo.EVENT ADD DELIVERY_NUMBER bigint default NULL;
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'EVENT_BY_DELIVERY_AND_PICKUP_TIME' AND object_id = OBJECT_ID('EVENT'))
CREATE NONCLUSTERED INDEX EVENT_BY_DELIVERY_AND_PICKUP_TIME ON dbo.EVENT(DELIVERY_NUMBER, PICKUP_TIME, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'EVENT_BY_STATUS_AND_PICKUP_TIME_AND_ID' AND object_id = OBJECT_ID('EVENT'))
CREATE NONCLUSTERED INDEX EVENT_BY_STATUS_AND_PICKUP_TIME_AND_ID ON dbo.EVENT(STATUS, PICKUP_TIME, ID, facilityNum, facilityCountryCode);

--changeset m0s0mqs:WFM-695 runOnChange:true
CREATE or ALTER PROCEDURE [dbo].[purge_table]
    @TableName varchar(100),
    @IdFieldName varchar(50),
    @TimeStampFieldName varchar(50),
    @RetentionDays int,
    @BatchSize bigint,
    @NumberOfRecords bigint,
    @WaitTimeInSec int
AS
BEGIN
    DECLARE @startId bigint = 0
    DECLARE @lastId bigint = 0
    DECLARE @recordSize bigint = 500
    DECLARE @batch bigint = 500
    DECLARE @rtDay int = 90
    DECLARE @waitInSec int = 4
    DECLARE @earliestTime datetime2
    DECLARE @createTime datetime2 = GETDATE()
    DECLARE @TabName varchar(100) = ''
    DECLARE @Id varchar(100) = 'id'
    DECLARE @CTS varchar(100) = 'CREATE_TS'
    DECLARE @endId bigint
    DECLARE @query_select_id_ts nvarchar(max)
    DECLARE @query_select_max_cts nvarchar(max)
    DECLARE @query_delete_purge_records nvarchar(max)
    DECLARE @query_wait nvarchar(max)
    IF (@TableName IS NOT NULL) AND (LEN(@TableName) > 0)
    BEGIN
        IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = @TableName)
        BEGIN
            PRINT 'Table ' + @TableName + ' does not exist, Please try again with correct table name'
            RETURN
        END
        ELSE
        BEGIN
            SET @TabName = 'dbo.' + @TableName
            PRINT 'Table Name ' + @TableName + ' set as ' + @TabName
        END
    END
    ELSE
    BEGIN
        PRINT 'Table Name ' + @TableName + ' can not be null or empty '
        RETURN
    END
    IF (@IdFieldName IS NOT NULL) AND (LEN(@IdFieldName) > 0)
    BEGIN
        SET @Id = @IdFieldName
        PRINT 'Table ' + @TableName + '  ID field name set as ' + @Id
    END
    ELSE
    BEGIN
        PRINT 'Table ' + @TableName + ' ID field name can not be null or empty '
        RETURN
    END
    IF (@TimeStampFieldName IS NOT NULL) AND (LEN(@TimeStampFieldName) > 0)
    BEGIN
        SET @CTS = @TimeStampFieldName
        PRINT 'Table ' + @TableName + ' TimeStamp field name set as ' + @CTS
    END
    ELSE
    BEGIN
        PRINT 'Table ' + @TableName + ' TimeStamp field name can not be null or empty '
        RETURN
    END
    IF (@RetentionDays IS NOT NULL AND @RetentionDays > 0 AND @RetentionDays <= 365)
    BEGIN
        SET @rtDay = @RetentionDays
        SET @earliestTime = DATEADD(DAY, -@rtDay, GETDATE())
        PRINT 'Table ' + @TableName + '  Retention day set as ' + CAST(@rtDay as varchar)
        PRINT 'Earlist timestamp to be retained based on retention days: ' + CAST(@earliestTime as varchar)
    END
    ELSE
    BEGIN
        PRINT 'RententionDays ' + CAST(@RetentionDays as varchar) + ' are not correct, Please provide a value between 0 and 365'
        RETURN
    END
    IF (@BatchSize IS NOT NULL AND @BatchSize > 0 AND @BatchSize <= 100000)
    BEGIN
        SET @batch = @BatchSize
        PRINT 'Table ' + @TableName + ' Batch size set as ' + CAST(@batch as varchar)
    END
    ELSE
    BEGIN
        PRINT 'Batch size can be between 1 and 100000 only'
        RETURN
    END
    IF (@NumberOfRecords IS NOT NULL AND @NumberOfRecords > 0 AND @NumberOfRecords >= @BatchSize)
    BEGIN
        SET @recordSize = @NumberOfRecords
        PRINT 'Table ' + @TableName + '  Delete record size set as ' + CAST(@recordSize as varchar)
    END
    ELSE
    BEGIN
        PRINT 'Delete record size should be > 0 and batch size can not be greater than delete record size'
        RETURN
    END
    IF (@WaitTimeInSec IS NOT NULL AND @WaitTimeInSec >= 0 AND @WaitTimeInSec <= 86400)
    BEGIN
        SET @waitInSec = @WaitTimeInSec
        PRINT 'Table ' + @TableName + '  Waiting seconds set as ' + CAST(@waitInSec as varchar)
    END
    ELSE
    BEGIN
        PRINT 'WaitTimeInSec ' + CAST(@WaitTimeInSec as varchar)
              + ' is not correct, Please provide a value between 0 and 86400, means job can wait 1 day max'
        RETURN
    END
    BEGIN
        set @query_select_id_ts
            = N'select @startId=' + @Id + ', @createTime=' + @CTS + ' from ' + @TabName + ' WHERE '
              + CAST(@Id as varchar) + '=(select min(' + CAST(@Id as varchar) + ') from ' + @TabName + ')'
        EXEC sys.sp_executesql @query_select_id_ts,
                               N'@startId int OUTPUT, @createTime datetime2 OUTPUT',
                               @startId OUTPUT,
                               @createTime OUTPUT
        PRINT 'Start ID: ' + CAST(@startId as varchar)
    END
    BEGIN
        SET @lastId = @startId + @recordSize - 1
        set @query_select_max_cts
            = N'select top(1) @lastId=' + @Id + ', @createTime=' + @CTS + ' from ' + @TabName + ' WHERE '
              + CAST(@Id as varchar) + '<=' + CAST(@lastId as varchar) + ' order by  ' + CAST(@Id as varchar) + ' desc'
        EXEC sys.sp_executesql @query_select_max_cts,
                               N'@lastId int OUTPUT, @createTime datetime2 OUTPUT',
                               @lastId OUTPUT,
                               @createTime OUTPUT
        PRINT 'Last ID: ' + CAST(@lastId as varchar)
        PRINT 'Create timestamp of last ID: ' + CAST(@createTime as varchar)
    END
    if (@createTime >= @earliestTime)
    BEGIN
        PRINT 'Table ' + @TableName + '  Nothing to delete as createTime of last id is within retention time.'
        RETURN
    END
    BEGIN
        SET @endId = @startId + @batch - 1
        if (@endId > @lastId)
        BEGIN
          SET @endId = @lastId
        END
    END
    WHILE @endId <= @lastId
    BEGIN
        PRINT 'Table ' + @TableName + '  Deleting from ' + CAST(@startId as varchar)
        BEGIN TRANSACTION
        set @query_delete_purge_records
            = 'delete from ' + @TabName + ' where ' + @Id + '>=' + CAST(@startId as varchar) + ' and ' + @Id + '<= '
              + CAST(@endId as varchar)
        EXEC sys.sp_executesql @query_delete_purge_records,
                               N'@startId int,@endId int',
                               @startId,
                               @endId
        COMMIT TRANSACTION
        SET @startId = @endId + 1
        SET @endId = @startId + @batch - 1
        set @query_wait = 'WAITFOR DELAY ''00:00:' + CAST(@waitInSec as varchar) + ''''
        EXEC sys.sp_executesql @query_wait, N'@waitInSec int', @waitInSec
    END
    PRINT 'Table ' + @TableName + '  Done.'
END

--changeset m0s0mqs:APPRC-444
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SCANNED_SERIAL_NUMBER' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD SCANNED_SERIAL_NUMBER varchar(100) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'EXPECTED_SERIAL_NUMBERS' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD EXPECTED_SERIAL_NUMBERS nvarchar(MAX) default NULL;

--changeset s0c034w:WFM-828
if exists (select 1 from sys.indexes where name='UK_k5ipc20br8sx04rt5qjgt48a4')
ALTER TABLE dbo.PROBLEM DROP CONSTRAINT UK_k5ipc20br8sx04rt5qjgt48a4;
if not exists (select 1 from sys.indexes where name='UK_delivery_problem_tag')
ALTER TABLE dbo.PROBLEM ADD CONSTRAINT UK_delivery_problem_tag UNIQUE (DELIVERY_NUMBER, PROBLEM_TAG_ID);

--changeset c0m0pip:SCTEG-1727
if not exists(SELECT 1 FROM sys.columns WHERE name = 'IS_AUDIT_REQUIRED' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD IS_AUDIT_REQUIRED BIT default NULL;

--changeset m0s0mqs:APPRC-551
if not exists (select 1 from sysobjects where name='RECEIVING_WORKFLOW')
CREATE TABLE dbo.RECEIVING_WORKFLOW (ID bigint NOT NULL, facilityCountryCode varchar(20) NOT NULL, facilityNum int NOT NULL,
PACKAGE_BARCODE_VALUE varchar(50) NOT NULL, TYPE varchar(20) NOT NULL, CREATE_REASON varchar(50) NOT NULL, STATUS varchar(20) NOT NULL,
CREATE_TS datetime2 NOT NULL, CREATE_USER varchar(50) NOT NULL, LAST_CHANGED_TS datetime2, LAST_CHANGED_USER varchar(50),
CONSTRAINT PK_RECEIVING_WORKFLOW PRIMARY KEY (ID));

if not exists (select 1 from sysobjects where name='receiving_workflow_sequence')
CREATE SEQUENCE dbo.receiving_workflow_sequence START WITH 1000 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

if not exists (select 1 from sysobjects where name='RECEIVING_WORKFLOW_ITEM')
CREATE TABLE dbo.RECEIVING_WORKFLOW_ITEM (ID bigint NOT NULL, facilityCountryCode varchar(20) NOT NULL, facilityNum int NOT NULL,
WORKFLOW_ID bigint NOT NULL FOREIGN KEY REFERENCES RECEIVING_WORKFLOW(ID),
ITEM_TRACKING_ID bigint FOREIGN KEY REFERENCES CONTAINER_RLOG(ID),
GTIN varchar(40) NOT NULL, ACTION varchar(20), CREATE_TS datetime2 NOT NULL, CREATE_USER varchar(50) NOT NULL,
LAST_CHANGED_TS datetime2, LAST_CHANGED_USER varchar(50),
CONSTRAINT PK_RECEIVING_WORKFLOW_ITEM PRIMARY KEY (ID));

if not exists (select 1 from sysobjects where name='receiving_workflow_item_sequence')
CREATE SEQUENCE dbo.receiving_workflow_item_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIVING_WORKFLOW_ITEM_WORKFLOW_ID' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW_ITEM'))
CREATE NONCLUSTERED INDEX RECEIVING_WORKFLOW_ITEM_WORKFLOW_ID ON dbo.RECEIVING_WORKFLOW_ITEM(WORKFLOW_ID);

--changeset n0n02dt:WFM-1029
if not exists(SELECT 1 FROM sys.columns WHERE name = 'PLU_NUMBER' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD PLU_NUMBER int default NULL;

--changeset a0b0tab:SCTA-3194
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SELLER_TRUST_LEVEL' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD SELLER_TRUST_LEVEL varchar(10);

--changeset m0s0mqs:APPRC-582
if not exists(SELECT 1 FROM sys.columns WHERE name = 'WORKFLOW_ID' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD WORKFLOW_ID varchar(50) NOT NULL;

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIVING_WORKFLOW_UNIQUE_WORKFLOW_ID' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD CONSTRAINT RECEIVING_WORKFLOW_UNIQUE_WORKFLOW_ID UNIQUE (WORKFLOW_ID, facilityNum, facilityCountryCode);

--changeset s0s0nug:APPRC-555
if exists
(
    SELECT fk.name constraint_name
    FROM sys.foreign_keys fk
        INNER JOIN sys.foreign_key_columns fkc
        ON fk.object_id = fkc.constraint_object_id
    WHERE OBJECT_NAME(fk.parent_object_id) = 'RECEIVING_WORKFLOW_ITEM'
        AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'ITEM_TRACKING_ID'
        AND OBJECT_NAME (fk.referenced_object_id) = 'CONTAINER_RLOG'
        AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = 'ID'
)
BEGIN
    DECLARE @CONSTRAINT_NAME nvarchar(max),
            @SQL_CMD nvarchar(max)
    SELECT @CONSTRAINT_NAME = fk.name
    FROM sys.foreign_keys fk
            INNER JOIN sys.foreign_key_columns fkc
            ON fk.object_id = fkc.constraint_object_id
        WHERE OBJECT_NAME(fk.parent_object_id) = 'RECEIVING_WORKFLOW_ITEM'
            AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'ITEM_TRACKING_ID'
            AND OBJECT_NAME (fk.referenced_object_id) = 'CONTAINER_RLOG'
            AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = 'ID'
    set @SQL_CMD = 'ALTER TABLE RECEIVING_WORKFLOW_ITEM DROP CONSTRAINT ' + @CONSTRAINT_NAME
    EXEC (@SQL_CMD)
END;
if exists
(
    SELECT 1
    FROM sys.columns
    WHERE name = 'ITEM_TRACKING_ID'
    AND object_id = OBJECT_ID('RECEIVING_WORKFLOW_ITEM')
)
    ALTER TABLE dbo.RECEIVING_WORKFLOW_ITEM ALTER COLUMN ITEM_TRACKING_ID varchar(50);

--changeset s0c034w:GLSMAV-30396-index
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'facilityNum_CC_MSG_ID_idx' AND object_id = OBJECT_ID('DECANT_AUDIT'))
CREATE NONCLUSTERED INDEX facilityNum_CC_MSG_ID_idx ON dbo.DECANT_AUDIT(facilityNum,facilityCountryCode,MSG_IDEMPOTANT_ID)

--changeset n0b07p0:APPRC-601
if exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIVING_WORKFLOW_UNIQUE_WORKFLOW_ID' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW DROP CONSTRAINT RECEIVING_WORKFLOW_UNIQUE_WORKFLOW_ID;

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIVING_WORKFLOW_UNIQUE_WORKFLOW_ID' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD CONSTRAINT RECEIVING_WORKFLOW_UNIQUE_WORKFLOW_ID UNIQUE (WORKFLOW_ID);

--changeset r0r00g0:WFM-1139-uom
if exists(SELECT 1 FROM sys.columns WHERE name = 'QUANTITY_UOM' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ALTER COLUMN QUANTITY_UOM varchar(50);

-- changeset z0k015a:SCTA-5126
-- create outbox tables
if not exists (select 1 from sysobjects where name = 'OUTBOX_EVENT')
CREATE TABLE dbo.OUTBOX_EVENT (
    id varchar(200) NOT NULL,
    event_identifier varchar(200) NOT NULL,
    publisher_policy_id varchar(200) NOT NULL,
    meta_data nvarchar(MAX) NOT NULL,
    payload_ref nvarchar(MAX) NOT NULL,
    token bigint NOT NULL,
    status varchar(40) NOT NULL,
    create_ts datetimeoffset NOT NULL,
    execution_ts datetimeoffset NOT NULL,
    next_execution_ts datetimeoffset NOT NULL,
    execution_count int NOT NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

if not exists (select 1 from sysobjects where name = 'OUTBOX_NODE_MANAGER')
CREATE TABLE dbo.OUTBOX_NODE_MANAGER (
    name varchar(64) NOT NULL,
    lock_until datetime NULL,
    locked_at datetime NULL,
    locked_by varchar(255) NOT NULL
);

if not exists (select 1 from sysobjects where name = 'OUTBOX_NODE')
CREATE TABLE dbo.OUTBOX_NODE (
    node_id varchar(150) NOT NULL,
    vnode_id varchar(150) NOT NULL,
    node_name varchar(150) NOT NULL,
    ping_ts datetimeoffset NOT NULL,
    token_start_range bigint NULL,
    token_end_range bigint NULL,
    scheduler_policy_id varchar(200)  NOT NULL,
    status varchar(40)  NOT NULL,
    CONSTRAINT pk_node_id PRIMARY KEY (vnode_id, node_id)
);

if not exists (select 1 from sysobjects where name = 'POLICY')
CREATE TABLE dbo.POLICY (
    id varchar(200) NOT NULL,
    [type] varchar(200) NOT NULL,
    enabled bit NOT NULL,
    spec nvarchar(MAX) NOT NULL,
    CONSTRAINT pk_payload_id PRIMARY KEY (id)
);

-- changeset z0k015a:SCTA-5634
-- outbox upgrade
IF NOT EXISTS (SELECT * FROM sys.columns WHERE name = 'execution_epoc_ts' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
ALTER TABLE dbo.OUTBOX_EVENT add execution_epoc_ts bigint NULL;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE name = 'next_execution_epoc_ts' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
ALTER TABLE dbo.OUTBOX_EVENT  ADD next_execution_epoc_ts bigint NULL;

--changeset a0s0xp4:SCTA-6134
if not exists(SELECT 1 FROM sys.columns WHERE name = 'REJECT_REASON' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD REJECT_REASON smallint;

--changeset m0s0mqs:WFM-1602_1
if not exists (select 1 from sysobjects where name='ARUCO_LABEL')
CREATE TABLE dbo.ARUCO_LABEL (ID bigint NOT NULL, facilityCountryCode varchar(20) NOT NULL, facilityNum int NOT NULL,
ARUCO_ID varchar(10) NOT NULL, TYPE SMALLINT NOT NULL, STATUS SMALLINT NOT NULL, CREATED_DATE datetime2 NOT NULL,
CREATED_BY varchar(32) NOT NULL, LAST_UPDATED_DATE datetime2, LAST_UPDATED_BY varchar(32),
CONSTRAINT PK_ARUCO_LABEL PRIMARY KEY (ID));

if not exists (select 1 from sysobjects where name='aruco_label_sequence')
CREATE SEQUENCE dbo.aruco_label_sequence START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ARUCO_LABEL_STATUS_TYPE_IDX' AND object_id = OBJECT_ID('ARUCO_LABEL'))
CREATE NONCLUSTERED INDEX ARUCO_LABEL_STATUS_TYPE_IDX
ON dbo.ARUCO_LABEL (TYPE, STATUS, facilityNum, facilityCountryCode)

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ARUCO_LABEL_UNIQUE_ARUCO_ID' AND object_id = OBJECT_ID('ARUCO_LABEL'))
ALTER TABLE dbo.ARUCO_LABEL ADD CONSTRAINT ARUCO_LABEL_UNIQUE_ARUCO_ID UNIQUE (ARUCO_ID, facilityNum, facilityCountryCode);

--changeset m0s0mqs:WFM-1602_2 runOnChange:true
CREATE OR ALTER PROCEDURE [dbo].[get_aruco_labels]
    @Count int,
    @LabelType SMALLINT,
    @UserId varchar(10),
    @FacilityNum int,
    @FacilityCountryCode varchar(2)
AS
BEGIN
    IF (@Count IS NOT NULL AND @Count > 0)
        AND (@LabelType IS NOT NULL AND @LabelType >= 0)
        AND (@UserId IS NOT NULL AND LEN(@UserId) > 0)
        AND (@FacilityNum IS NOT NULL)
        AND (@FacilityCountryCode IS NOT NULL AND LEN(@FacilityCountryCode) > 0)
    BEGIN
        DECLARE @ArucoRowIds TABLE (ID INT)
        DECLARE @FetchedCount int
        SET NOCOUNT ON
        BEGIN TRANSACTION
            INSERT INTO @ArucoRowIds (ID)
                SELECT
                    TOP (@Count) ID
                FROM
                    ARUCO_LABEL WITH (UPDLOCK, READPAST)
                WHERE
                    status = 0 AND TYPE = @LabelType and facilityNum = @FacilityNum and facilityCountryCode = @FacilityCountryCode
                ORDER BY ID ASC

            SELECT @FetchedCount = COUNT(*) FROM @ArucoRowIds

            IF (@FetchedCount IS NOT NULL AND @FetchedCount = @Count)
            BEGIN
                UPDATE ARUCO_LABEL
                    SET STATUS  = 1, LAST_UPDATED_BY = @UserId, LAST_UPDATED_DATE = CURRENT_TIMESTAMP
                WHERE
                    ID in (SELECT ID from @ArucoRowIds)
                COMMIT TRANSACTION
            END

            ELSE
            BEGIN
                PRINT 'Not enough UNUSED labels to return!'
                COMMIT TRANSACTION
                RETURN
            END

        SELECT * FROM ARUCO_LABEL al WHERE ID in (SELECT ID from @ArucoRowIds) order by ID asc
    END

    ELSE
    BEGIN
        PRINT 'Invalid arguments!'
    END
END

-- changeset z0k015a:SCTA-7160
IF NOT EXISTS (SELECT * FROM sys.columns WHERE name = 'relay_error' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
BEGIN
ALTER TABLE dbo.OUTBOX_EVENT ADD relay_error varchar(300) NULL
END;

IF EXISTS (SELECT 1 FROM sys.columns WHERE name = 'execution_epoc_ts' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
ALTER TABLE dbo.OUTBOX_EVENT ALTER COLUMN execution_epoc_ts bigint NOT NULL;

IF EXISTS (SELECT 1 FROM sys.columns WHERE name = 'next_execution_epoc_ts' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
ALTER TABLE dbo.OUTBOX_EVENT ALTER COLUMN next_execution_epoc_ts bigint NOT NULL;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='idx_outbox_publisher_id_token_count_epoc_ts' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_outbox_publisher_id_token_count_epoc_ts ON dbo.OUTBOX_EVENT (publisher_policy_id ASC, token ASC, execution_count ASC, next_execution_epoc_ts ASC)
    INCLUDE (execution_epoc_ts)  with(online=on, maxdop=2)
END;

--changeset s0s0nug:WFM-1688
if not exists(SELECT 1 FROM sys.columns WHERE name = 'CID' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD CID VARCHAR(50) default NULL;

--changeset r0r00g0:WFM-1899
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'EVENT_BY_STATUS_AND_PICKUP_TIME_AND_EVENT_TYPE_AND_ID' AND object_id = OBJECT_ID('EVENT'))
CREATE NONCLUSTERED INDEX EVENT_BY_STATUS_AND_PICKUP_TIME_AND_EVENT_TYPE_AND_ID ON dbo.EVENT(STATUS, PICKUP_TIME, EVENT_TYPE, ID);


--changeset d0k05qf:SCTA-5605
if not exists (SELECT 1 FROM sys.columns WHERE name = 'ITEM_MISC_INFO' AND object_id = OBJECT_ID('DELIVERY_ITEM_OVERRIDE'))
ALTER TABLE dbo.DELIVERY_ITEM_OVERRIDE ADD ITEM_MISC_INFO VARCHAR(600);
if not exists (SELECT 1 FROM sys.columns WHERE name = 'LAST_CHANGED_USER' AND object_id = OBJECT_ID('DELIVERY_ITEM_OVERRIDE'))
ALTER TABLE dbo.DELIVERY_ITEM_OVERRIDE ADD LAST_CHANGED_USER VARCHAR(20);
if not exists (SELECT 1 FROM sys.columns WHERE name = 'LAST_CHANGED_TS' AND object_id = OBJECT_ID('DELIVERY_ITEM_OVERRIDE'))
ALTER TABLE dbo.DELIVERY_ITEM_OVERRIDE ADD LAST_CHANGED_TS DATETIME2(7);
if exists (SELECT 1 FROM sys.columns WHERE name = 'TEMP_PALLET_TI' AND object_id = OBJECT_ID('DELIVERY_ITEM_OVERRIDE'))
ALTER TABLE dbo.DELIVERY_ITEM_OVERRIDE ALTER COLUMN TEMP_PALLET_TI INT;
if exists (SELECT 1 FROM sys.columns WHERE name = 'VERSION' AND object_id = OBJECT_ID('DELIVERY_ITEM_OVERRIDE'))
ALTER TABLE dbo.DELIVERY_ITEM_OVERRIDE ALTER COLUMN VERSION INT;

-- changeset g0s03ck:APPRC-817
if not exists (select 1 from sysobjects where name = 'PRODUCT_CATEGORY_GROUP')
CREATE TABLE dbo.PRODUCT_CATEGORY_GROUP (
    l0 varchar(256) NOT NULL,
    l1 varchar(256) NOT NULL,
    l2 varchar(256) NOT NULL,
    l3 varchar(256) NOT NULL,
    product_type varchar(256) NOT NULL,
    [group] varchar(1) NOT NULL,
  --avg_aur DECIMAL(10, 2),
  --total_proc_rtns int DEFAULT 0,
    CREATE_TS datetime2 NOT NULL DEFAULT getdate(),
    CREATE_USER varchar(50) NOT NULL DEFAULT '-Imported-',
    LAST_CHANGED_TS datetime2 NOT NULL DEFAULT getdate(),
    LAST_CHANGED_USER varchar(50),

    CONSTRAINT PK_PRODUCT_CATEGORY_GROUP PRIMARY KEY NONCLUSTERED (l0, l1, l2, l3, product_type),
);

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'index_product_category_group_product_type' AND object_id = OBJECT_ID('dbo.PRODUCT_CATEGORY_GROUP'))
CREATE NONCLUSTERED INDEX index_product_category_group_product_type on dbo.PRODUCT_CATEGORY_GROUP (product_type);

--changeset s0g0gp4:AG-6776
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SUBCENTER_ID' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD SUBCENTER_ID int default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SUBCENTER_ID' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD SUBCENTER_ID int default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SUBCENTER_ID' AND object_id = OBJECT_ID('INSTRUCTION'))
ALTER TABLE dbo.INSTRUCTION ADD SUBCENTER_ID int default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SUBCENTER_ID' AND object_id = OBJECT_ID('RECEIPT'))
ALTER TABLE dbo.RECEIPT ADD SUBCENTER_ID int default NULL;

--changeset vn55oas:SCTA-12637
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SSCC' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD SSCC varchar(30) default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'ASN_NBR' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD ASN_NBR varchar(30) default NULL;

--changeset p0v00iz:OPIF-122188
if not exists(SELECT 1 FROM sys.columns WHERE name = 'INSTR_CREATED_BY_PACKAGE_INFO' AND object_id = OBJECT_ID('INSTRUCTION'))
ALTER TABLE dbo.INSTRUCTION ADD INSTR_CREATED_BY_PACKAGE_INFO VARCHAR(4000) default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'RECEIVING_METHOD' AND object_id = OBJECT_ID('INSTRUCTION'))
ALTER TABLE dbo.INSTRUCTION ADD RECEIVING_METHOD VARCHAR(50) default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'RCVG_CONTAINER_TYPE' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD RCVG_CONTAINER_TYPE VARCHAR(50) default NULL;
if exists(SELECT 1 FROM sys.columns WHERE name = 'CONTAINER_MISC_INFO' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ALTER COLUMN CONTAINER_MISC_INFO VARCHAR(4000);

--changeset p0v00iz:OPIF-122188-table_1
if not exists (select 1 from sysobjects where name = 'processing_info')
CREATE TABLE dbo.processing_info (
id bigint NOT NULL,
facilityCountryCode varchar(5),
facilityNum int,
system_container_id varchar(50) NOT NULL,
reference_info varchar(4000),
status varchar(40),
instruction_id bigint NOT NULL,
create_ts datetime2,
last_changed_ts datetime2,
create_user_id varchar(20)
);

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'processing_info_by_id' AND object_id = OBJECT_ID('processing_info'))
CREATE NONCLUSTERED INDEX processing_info_by_id ON dbo.processing_info (id, facilityCountryCode, facilityNum, system_container_id) WITH (ONLINE = ON);
if not exists (select 1 from sys.indexes where name='UK_processing_info')
ALTER TABLE dbo.processing_info ADD CONSTRAINT UK_processing_info UNIQUE (id, facilityCountryCode, facilityNum, instruction_id);


if exists(SELECT 1 FROM sys.columns WHERE name = 'CONTAINER_MISC_INFO' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ALTER COLUMN CONTAINER_MISC_INFO VARCHAR(4000);

--changeset vn55oas:SCTA-12637
if not exists(SELECT 1 FROM sys.columns WHERE name = 'SSCC' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD SSCC varchar(30) default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'ASN_NBR' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD ASN_NBR varchar(30) default NULL;

-- changeset g0s03ck:APPRC-817-2
DROP TABLE dbo.PRODUCT_CATEGORY_GROUP;

if not exists (select 1 from sysobjects where name = 'PRODUCT_CATEGORY_GROUP')
CREATE TABLE dbo.PRODUCT_CATEGORY_GROUP (
    l0 varchar(256) NOT NULL,
    l1 varchar(256) NOT NULL,
    l2 varchar(256) NOT NULL,
    l3 varchar(256) NOT NULL,
    product_type varchar(256) NOT NULL,
    [group] varchar(1) NOT NULL,
  --avg_aur DECIMAL(10, 2),
  --total_proc_rtns int DEFAULT 0,
    CREATE_TS datetime2 DEFAULT getdate() NOT NULL,
    CREATE_USER varchar(50) DEFAULT '-Imported-' NOT NULL,
    LAST_CHANGED_TS datetime2 DEFAULT getdate() NOT NULL,
    LAST_CHANGED_USER varchar(50),

    CONSTRAINT PK_PRODUCT_CATEGORY_GROUP PRIMARY KEY NONCLUSTERED (l0, l1, l2, l3, product_type),
);

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'index_product_category_group_product_type' AND object_id = OBJECT_ID('dbo.PRODUCT_CATEGORY_GROUP'))
CREATE NONCLUSTERED INDEX index_product_category_group_product_type on dbo.PRODUCT_CATEGORY_GROUP (product_type);

-- changeset g0s03ck:APPRC-998
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_SALES_ORDER_NUMBER' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_SALES_ORDER_NUMBER ON dbo.CONTAINER_RLOG(SALES_ORDER_NUMBER) WITH (ONLINE = ON, MAXDOP = 2);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_TRACKING_ID' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_TRACKING_ID ON dbo.CONTAINER_RLOG(TRACKING_ID) WITH (ONLINE = ON, MAXDOP = 2);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'COTAINER_RLOG_BY_GTIN_FINAL_DISPOSITION_TYPE' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
CREATE NONCLUSTERED INDEX COTAINER_RLOG_BY_GTIN_FINAL_DISPOSITION_TYPE ON dbo.CONTAINER_RLOG(GTIN, FINAL_DISPOSITION_TYPE) WITH (ONLINE = ON, MAXDOP = 2);

--changeset a0b02ft:SCTNGMS-5962_1 runOnChange:true
CREATE OR ALTER PROCEDURE [dbo].[usp_fc_db_purge_on_condition] @schemaName varchar(500), @tablename varchar(500), @batchSize int, @retentionDays int, @dateColumn varchar(500), @condition varchar(500)
AS
BEGIN
    DECLARE @loop bit = 1
    DECLARE @r int = 0
    DECLARE @rowcount int = 0
    DECLARE @days INT
    DECLARE @sqlCommand NVARCHAR(2048)
    DECLARE @totalRows int
    DECLARE @tblname varchar(500)= @schemaName+'.'+@tablename
    IF(@retentionDays < 60)
    BEGIN
        SET @retentionDays = 60
    END

    IF(@batchSize > 50000)
    BEGIN
        SET @batchSize = 50000
    END

    SET @days = -@retentionDays
    DECLARE @dateCondition varchar(500)
    SET @dateCondition = (SELECT DATEADD(DAY, @days, GETDATE()))

    DECLARE @sqlDelCommand NVARCHAR(2048)
    SET @sqlDelCommand = (' DELETE TOP (' + cast(@batchSize as varchar(10)) + ') FROM ' + @tblname +
                    ' WHERE ' + @dateColumn + ' < ''' + @dateCondition + ''' ' + @condition)

    WHILE (@loop = 1)
        BEGIN
            BEGIN TRY
                BEGIN TRANSACTION
                    EXEC sp_executesql @sqlDelCommand
                    SET @rowcount = @@ROWCOUNT
                    IF (@rowcount = 0 OR @rowcount < @batchSize) SET @loop = 0
                COMMIT TRANSACTION
            END TRY
            BEGIN CATCH
                SET @loop = 0
                DECLARE @ErrorMessage NVARCHAR(4000)
                DECLARE @ErrorSeverity INT
                DECLARE @ErrorState INT
                SELECT
                @ErrorMessage = ERROR_MESSAGE(),
                @ErrorSeverity = ERROR_SEVERITY(),
                @ErrorState = ERROR_STATE()
                -- Use RAISERROR inside the CATCH block to return error
                -- information about the original error that caused
                -- execution to jump to the CATCH block.
                RAISERROR (@ErrorMessage, -- Message text.
                @ErrorSeverity, -- Severity.
                @ErrorState -- State.
                )
                ROLLBACK
            END CATCH
        END
END

--changeset a0b02ft:SCTNGMS-5962_2 runOnChange:true
CREATE OR ALTER PROCEDURE [dbo].[usp_fc_db_purge_container_on_condition] @schemaName varchar(500), @batchSize int, @retentionDays int, @dateColumn varchar(500), @condition varchar(500)
AS
BEGIN
    DECLARE @loop bit = 1
    DECLARE @r int = 0
    DECLARE @rowcount int = 0
    DECLARE @days INT
    DECLARE @sqlCommand NVARCHAR(2048)
    DECLARE @totalRows int
    IF(@retentionDays < 60)
    BEGIN
        SET @retentionDays = 60
    END

    IF(@batchSize > 50000)
    BEGIN
        SET @batchSize = 50000
    END

    SET @days = -@retentionDays
    DECLARE @dateCondition varchar(500)
    SET @dateCondition = (SELECT DATEADD(DAY, @days, GETDATE()))

    DECLARE @sqlDelCommand NVARCHAR(2048)
    SET @sqlDelCommand= ('DECLARE @ContainerTempTable TABLE (cid varchar(50), facility_nbr int);
    INSERT into @ContainerTempTable (cid, facility_nbr)
    SELECT TOP (' + cast(@batchSize as varchar(10)) + ') TRACKING_ID, facilityNum from '+ cast(@schemaName as varchar(50)) +'.CONTAINER with (nolock) WHERE ' + @dateColumn + ' < ''' + @dateCondition + ''' ' + @condition +';

    DECLARE @MyCursor CURSOR;
    DECLARE @facilityNum INT;

    SET @MyCursor = CURSOR FOR select distinct facility_nbr from @ContainerTempTable;
    OPEN @MyCursor
    FETCH NEXT FROM @MyCursor INTO @facilityNum

    WHILE @@FETCH_STATUS = 0
    BEGIN
        delete from '+ cast(@schemaName as varchar(50)) +'.CONTAINER_ITEM where TRACKING_ID in (SELECT temp.cid from @ContainerTempTable temp where facility_nbr = @facilityNum) and facilityNum = @facilityNum;
        delete from '+ cast(@schemaName as varchar(50)) +'.CONTAINER where TRACKING_ID in (SELECT temp.cid from @ContainerTempTable temp where facility_nbr = @facilityNum) and facilityNum = @facilityNum;
        FETCH NEXT FROM @MyCursor INTO @facilityNum;
    END;

    CLOSE @MyCursor;
    DEALLOCATE @MyCursor;')

    WHILE (@loop = 1)
        BEGIN
            BEGIN TRY
                BEGIN TRANSACTION
                    EXEC sp_executesql @sqlDelCommand
                    SET @rowcount = @@ROWCOUNT
                    IF (@rowcount = 0) SET @loop = 0
                COMMIT TRANSACTION
            END TRY
            BEGIN CATCH
                SET @loop = 0
                DECLARE @ErrorMessage NVARCHAR(4000)
                DECLARE @ErrorSeverity INT
                DECLARE @ErrorState INT
                SELECT
                @ErrorMessage = ERROR_MESSAGE(),
                @ErrorSeverity = ERROR_SEVERITY(),
                @ErrorState = ERROR_STATE()
                -- Use RAISERROR inside the CATCH block to return error
                -- information about the original error that caused
                -- execution to jump to the CATCH block.
                RAISERROR (@ErrorMessage, -- Message text.
                @ErrorSeverity, -- Severity.
                @ErrorState -- State.
                )
                ROLLBACK
            END CATCH
        END
END
--changeset vn55snk:SCTA-7619
if not exists (SELECT 1 FROM sys.columns WHERE name = 'VNPK' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD VNPK INT;
if not exists (SELECT 1 FROM sys.columns WHERE name = 'WHPK' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD WHPK INT;
if not exists (SELECT 1 FROM sys.columns WHERE name = 'ALLOCATION' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD ALLOCATION VARCHAR(3000);
if not exists (SELECT 1 FROM sys.columns WHERE name = 'QUANTITY_UOM' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD QUANTITY_UOM VARCHAR(50);
if not exists (SELECT 1 FROM sys.columns WHERE name = 'STATUS' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD STATUS VARCHAR(20);
if not exists (SELECT 1 FROM sys.columns WHERE name = 'VERSION' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD VERSION INT;
if not exists (SELECT 1 FROM sys.columns WHERE name = 'ORDER_QTY' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD ORDER_QTY INT;
if not exists (SELECT 1 FROM sys.columns WHERE name = 'QUANTITY' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD QUANTITY INT;

--changeset vn55snk:SCTA-8455
if not exists (SELECT 1 FROM sys.columns WHERE name = 'LABEL_DATA_MISC_INFO' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD LABEL_DATA_MISC_INFO VARCHAR(3000);

--changeset tjhinri:SCTA-11338
if not exists(SELECT 1 FROM sys.columns WHERE name = 'DEST_STR_NBR' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD DEST_STR_NBR int default NULL;

-- changeset n0n02dt:WFM-2954
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DECANT_AUDIT_CREATED_DATE_IDX' AND object_id = OBJECT_ID('DECANT_AUDIT'))
CREATE NONCLUSTERED INDEX DECANT_AUDIT_CREATED_DATE_IDX ON dbo.DECANT_AUDIT(CREATED_DATE) WITH (ONLINE = ON);

-- changeset vn53zql:APPRC-1039
if not exists(SELECT 1 FROM sys.columns WHERE name = 'PRE_POPULATED_CATEGORY' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD PRE_POPULATED_CATEGORY varchar(1) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'CHOSEN_CATEGORY' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD CHOSEN_CATEGORY varchar(1) default NULL;

-- changeset s0g0gp4:AG-8052
if not exists(SELECT 1 FROM sys.columns WHERE name = 'RECEIVE_PROGRESS' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
ALTER TABLE dbo.DELIVERY_METADATA ADD RECEIVE_PROGRESS nvarchar(MAX) default NULL;

-- changeset n0n02dt:WFM-3045
if not exists(SELECT 1 FROM sys.columns WHERE name = 'DEPT_CATEGORY' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD DEPT_CATEGORY int default NULL;

--changeset g0s03ck:APPRC-1132
ALTER TABLE dbo.RECEIVING_WORKFLOW ALTER COLUMN CREATE_REASON varchar(150);

-- changeset g0s03ck:APPRC-850
if not exists(SELECT 1 FROM sys.columns WHERE name = 'IMAGE_URLS' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD IMAGE_URLS varchar(2048);

if not exists(SELECT 1 FROM sys.columns WHERE name = 'IMAGE_COUNT' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD IMAGE_COUNT int DEFAULT 0;

UPDATE dbo.RECEIVING_WORKFLOW set IMAGE_COUNT = 0;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'IMAGE_COMMENT' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD IMAGE_COMMENT varchar(1024);

--changeset s0g0gp4:AG-9496
if not exists (select 1 from sysobjects where name='UNLOADER_INFO')
CREATE TABLE dbo.UNLOADER_INFO (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int,DELIVERY_NUMBER bigint NOT NULL,PURCHASE_REFERENCE_NUMBER varchar(20) NOT NULL,PURCHASE_REFERENCE_LINE_NUMBER int NOT NULL,ITEM_NUMBER bigint,ACTUAL_HI int, ACTUAL_TI int,FBQ int,CASE_QTY int,PALLET_QTY int,IS_UNLOADED_FULL_FBQ bit,CREATE_USER_ID varchar(255) NOT NULL,CREATE_TS datetime2 NOT NULL,SUBCENTER_ID int default NULL, CONSTRAINT PK__UNLOADER__3214EC2739B55786 PRIMARY KEY (ID));

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UNLOADER_INFO_BY_DELIVERY' AND object_id = OBJECT_ID('UNLOADER_INFO'))
CREATE NONCLUSTERED INDEX UNLOADER_INFO_BY_DELIVERY ON dbo.UNLOADER_INFO(DELIVERY_NUMBER, facilityNum, facilityCountryCode);

if not exists (select 1 from sysobjects where name='UNLOADER_INFO_ID_SEQUENCE')
CREATE SEQUENCE dbo.UNLOADER_INFO_ID_SEQUENCE START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;

--changeset s0g0gp4:AG-9054_1
CREATE OR ALTER PROCEDURE  [dbo].[gdc_purge_container] @SchemaName NVARCHAR(300), @MaxRetentionDays int, @BatchSize int, @MaxExecutionCount int
AS
BEGIN
SET NOCOUNT ON
DECLARE @Loop bit = 1
DECLARE @Counter int = 1
DECLARE @Rowcount int = 0
DECLARE @Days int = -180
DECLARE @DeleteQuery NVARCHAR(2048)
DECLARE @ParmDefinition NVARCHAR (1000)
SET @Days = -@MaxRetentionDays
print 'Started gdc container purge for container and container_item'
WHILE (@Loop = 1)
BEGIN
BEGIN TRY
BEGIN TRANSACTION
Set @DeleteQuery= ('DECLARE @TempTable TABLE (id bigint, cid bigint);
insert into @TempTable (id, cid)
SELECT TOP(@LimitSize) c.ID, ci.CONTAINER_ITEM_ID from '+ cast(@schemaName as nvarchar(300)) +'.container c, '+ cast(@schemaName as nvarchar(300)) +'.CONTAINER_ITEM ci with (nolock) WHERE c.create_ts < DATEADD(DAY, @RetentionDays ,GETDATE()) and c.facilityNum = ci.facilityNum and c.TRACKING_ID = ci.TRACKING_ID
delete from '+ cast(@schemaName as nvarchar(300)) +'.container where id in (SELECT temp.id from @TempTable temp)
delete from '+ cast(@schemaName as nvarchar(300)) +'.container_item where container_item_id in (SELECT temp.cid from @TempTable temp)')
SET @ParmDefinition = '@RetentionDays INT, @LimitSize INT'
EXECUTE sp_executesql @DeleteQuery, @ParmDefinition, @RetentionDays= @Days, @LimitSize=@BatchSize
SET @Rowcount = @@ROWCOUNT
print 'Completed execution count : '+ cast(@Counter as varchar(10))
SET @Counter = @Counter + 1
IF (@Rowcount = 0 OR @Rowcount < @BatchSize OR @MaxExecutionCount < @Counter) SET @Loop = 0
COMMIT TRANSACTION
END TRY
BEGIN CATCH
SET @Loop = 0
DECLARE @ErrorMessage NVARCHAR(4000)
DECLARE @ErrorSeverity INT
DECLARE @ErrorState INT
SELECT
@ErrorMessage = ERROR_MESSAGE(),
@ErrorSeverity = ERROR_SEVERITY(),
@ErrorState = ERROR_STATE()
RAISERROR (@ErrorMessage, -- Message text.
@ErrorSeverity, -- Severity.
@ErrorState -- State.
)
ROLLBACK
END CATCH
END
END
print 'Completed gdc container purge at schema: '+cast(@SchemaName as nvarchar(300))

--changeset s0g0gp4:AG-9054_2
CREATE OR ALTER PROCEDURE  [dbo].[gdc_purge_table_on_condition] @SchemaName NVARCHAR(300), @Tablename NVARCHAR(500), @DateColumn NVARCHAR(500), @MaxRetentionDays int, @BatchSize int, @MaxExecutionCount int
AS
BEGIN
SET NOCOUNT ON
DECLARE @Loop bit = 1
DECLARE @Counter int = 1
DECLARE @Rowcount int = 0
DECLARE @Days int = -180
DECLARE @DeleteQuery NVARCHAR(2048)
DECLARE @ParmDefinition NVARCHAR (1000)
SET @Days = -@MaxRetentionDays
print 'Started gdc_purge_table_on_condition'
WHILE (@Loop = 1)
BEGIN
BEGIN TRY
BEGIN TRANSACTION
Set @DeleteQuery= ('DECLARE @TempTable TABLE (id bigint);
insert into @TempTable (id)
SELECT TOP(@LimitSize) id from '+ cast(@schemaName as nvarchar(300)) +'.'+ cast(@Tablename as nvarchar(300)) +' with (nolock) WHERE '+ cast(@DateColumn  as nvarchar(300)) +' < DATEADD(DAY, @RetentionDays ,GETDATE())
delete from '+ cast(@schemaName as nvarchar(300)) +'.'+ cast(@Tablename as nvarchar(300)) +' where id in (SELECT temp.id from @TempTable temp)')
SET @ParmDefinition = '@RetentionDays INT, @LimitSize INT'
EXECUTE sp_executesql @DeleteQuery, @ParmDefinition, @RetentionDays= @Days, @LimitSize=@BatchSize
SET @Rowcount = @@ROWCOUNT
print 'Completed execution count : '+ cast(@Counter as varchar(10))
SET @Counter = @Counter + 1
IF (@Rowcount = 0 OR @Rowcount < @BatchSize OR @MaxExecutionCount < @Counter) SET @Loop = 0
COMMIT TRANSACTION
END TRY
BEGIN CATCH
SET @Loop = 0
DECLARE @ErrorMessage NVARCHAR(4000)
DECLARE @ErrorSeverity INT
DECLARE @ErrorState INT
SELECT
@ErrorMessage = ERROR_MESSAGE(),
@ErrorSeverity = ERROR_SEVERITY(),
@ErrorState = ERROR_STATE()
RAISERROR (@ErrorMessage, -- Message text.
@ErrorSeverity, -- Severity.
@ErrorState -- State.
)
ROLLBACK
END CATCH
END
END
print 'Completed gdc_purge_table_on_condition for '+cast(@Tablename as nvarchar(300))

--changeset g0s03ck:APPRC-1337
if not exists(SELECT 1 FROM sys.columns WHERE name = 'MISSING_RETURN_INITIATED' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD MISSING_RETURN_INITIATED BIT default NULL;;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'MISSING_RETURN_RECEIVED' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD MISSING_RETURN_RECEIVED BIT default NULL;;

-- changeset s0g0gp4:AG-10255_1
if not exists(SELECT 1 FROM sys.columns WHERE name = 'LAST_CHANGED_TS' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD LAST_CHANGED_TS datetime2;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'LAST_CHANGED_TS' AND object_id = OBJECT_ID('RECEIPT'))
ALTER TABLE dbo.RECEIPT ADD LAST_CHANGED_TS datetime2;

-- changeset s0g0gp4:AG-10255_2
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_LAST_CHANGED_TS' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_LAST_CHANGED_TS ON dbo.CONTAINER(LAST_CHANGED_TS, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_BY_LAST_CHANGED_TS' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_BY_LAST_CHANGED_TS ON dbo.CONTAINER_ITEM(LAST_CHANGED_TS, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_LAST_CHANGED_TS' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_LAST_CHANGED_TS ON dbo.RECEIPT(LAST_CHANGED_TS, facilityNum, facilityCountryCode);

-- changeset vn54j03:SCTNGMS-7202
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'IDX_RECEIPT_BY_CREATE_TS' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX IDX_RECEIPT_BY_CREATE_TS ON dbo.RECEIPT(CREATE_TS) WITH (ONLINE = ON);
--changeset p0d00jo:WFM-3995
if not exists(SELECT 1 FROM sys.columns WHERE name = 'HYBRID_STORAGE_FLAG' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD HYBRID_STORAGE_FLAG VARCHAR(10) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'ELIGIBILITY' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD ELIGIBILITY smallint default NULL;

--changeset m0a0ggb:AG-12059
if not exists (select 1 from sysobjects where name='REJECTIONS')
CREATE TABLE dbo.REJECTIONS (
ID bigint NOT NULL,
facilityCountryCode varchar(255),
facilityNum int,
SUBCENTER_ID int default NULL,
DELIVERY_NUMBER bigint NOT NULL,
PURCHASE_REFERENCE_NUMBER varchar(20) NOT NULL,
PURCHASE_REFERENCE_LINE_NUMBER int NOT NULL,
ITEM_NUMBER bigint NOT NULL,
REASON varchar(100),
DESPOSITION varchar(100),
QUANTITY int,
IS_ENTIRE_DELIVERY_REJECT bit,
CREATE_USER_ID varchar(255) NOT NULL,
CREATE_TS datetime2 NOT NULL,
LAST_CHANGED_USER varchar(255) NOT NULL,
LAST_CHANGED_TS datetime2 NOT NULL,
CONSTRAINT PK__REJECTIONS__3214EC2739B55786 PRIMARY KEY (ID));

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'REJECTIONS_BY_DELIVERY_PO' AND object_id = OBJECT_ID('REJECTIONS'))
CREATE NONCLUSTERED INDEX REJECTIONS_BY_DELIVERY_PO ON dbo.REJECTIONS(DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER, facilityNum, facilityCountryCode);

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'REJECTIONS_BY_DELIVERY' AND object_id = OBJECT_ID('REJECTIONS'))
CREATE NONCLUSTERED INDEX REJECTIONS_BY_DELIVERY ON dbo.REJECTIONS(DELIVERY_NUMBER, facilityNum, facilityCountryCode);

if not exists (select 1 from sysobjects where name='REJECTIONS_ID_SEQUENCE')
CREATE SEQUENCE dbo.REJECTIONS_ID_SEQUENCE START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'REJECTIONS_BY_LAST_CHANGED_TS' AND object_id = OBJECT_ID('REJECTIONS'))
CREATE NONCLUSTERED INDEX REJECTIONS_BY_LAST_CHANGED_TS ON dbo.REJECTIONS(LAST_CHANGED_TS, facilityNum, facilityCountryCode);

--changeset lkotthi:AG-12132
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'PROBLEM_BY_LAST_CHANGE_TS' AND object_id = OBJECT_ID('PROBLEM'))
CREATE NONCLUSTERED INDEX PROBLEM_BY_LAST_CHANGE_TS ON dbo.PROBLEM(LAST_CHANGE_TS, facilityNum, facilityCountryCode) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DIO_BY_LAST_CHANGED_TS' AND object_id = OBJECT_ID('DELIVERY_ITEM_OVERRIDE'))
CREATE NONCLUSTERED INDEX DIO_BY_LAST_CHANGED_TS ON dbo.DELIVERY_ITEM_OVERRIDE(LAST_CHANGED_TS, facilityNum, facilityCountryCode) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_METADATA_BY_LAST_UPDATED_DATE' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
CREATE NONCLUSTERED INDEX DELIVERY_METADATA_BY_LAST_UPDATED_DATE ON dbo.DELIVERY_METADATA(LAST_UPDATED_DATE, facilityNum, facilityCountryCode) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'PRINTJOB_BY_CREATE_TS' AND object_id = OBJECT_ID('PRINTJOB'))
CREATE NONCLUSTERED INDEX PRINTJOB_BY_CREATE_TS ON dbo.PRINTJOB(CREATE_TS, facilityNum, facilityCountryCode) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'UNLOADER_INFO_BY_CREATE_TS' AND object_id = OBJECT_ID('UNLOADER_INFO'))
CREATE NONCLUSTERED INDEX UNLOADER_INFO_BY_CREATE_TS ON dbo.UNLOADER_INFO(CREATE_TS, facilityNum, facilityCountryCode) WITH (ONLINE = ON);

--changeset lkotthi:AG-12132-1
CREATE OR ALTER PROCEDURE  [dbo].[purge_table_on_condition] @SchemaName NVARCHAR(300), @Tablename NVARCHAR(500), @IdColumn NVARCHAR(500), @DateColumn NVARCHAR(500), @MaxRetentionDays int, @BatchSize int, @MaxExecutionCount int
AS
BEGIN
SET NOCOUNT ON
DECLARE @Loop bit = 1
DECLARE @Counter int = 1
DECLARE @Rowcount int = 0
DECLARE @Days int = -180
DECLARE @DeleteQuery NVARCHAR(2048)
DECLARE @ParmDefinition NVARCHAR (1000)
SET @Days = -@MaxRetentionDays
print 'Started purge_table_on_condition'
WHILE (@Loop = 1)
BEGIN
BEGIN TRY
BEGIN TRANSACTION
Set @DeleteQuery= ('DECLARE @GenericTempTable TABLE (identifier bigint);
INSERT INTO @GenericTempTable (identifier)
SELECT TOP(@LimitSize) '+ cast(@IdColumn as nvarchar(300)) + ' FROM '+ cast(@schemaName as nvarchar(300)) +'.'+ cast(@Tablename as nvarchar(300)) +' with (nolock) WHERE '+ cast(@DateColumn  as nvarchar(300)) +' < DATEADD(DAY, @RetentionDays ,GETDATE())
DELETE FROM '+ cast(@schemaName as nvarchar(300)) +'.'+ cast(@Tablename as nvarchar(300)) +' WHERE '+ cast(@IdColumn as nvarchar(300)) + ' IN (SELECT temp.identifier FROM @GenericTempTable temp)')
SET @ParmDefinition = '@RetentionDays INT, @LimitSize INT'
EXECUTE sp_executesql @DeleteQuery, @ParmDefinition, @RetentionDays= @Days, @LimitSize=@BatchSize
SET @Rowcount = @@ROWCOUNT
print 'Completed execution count : '+ cast(@Counter as varchar(10))
SET @Counter = @Counter + 1
IF (@Rowcount = 0 OR @Rowcount < @BatchSize OR @MaxExecutionCount < @Counter) SET @Loop = 0
COMMIT TRANSACTION
END TRY
BEGIN CATCH
SET @Loop = 0
DECLARE @ErrorMessage NVARCHAR(4000)
DECLARE @ErrorSeverity INT
DECLARE @ErrorState INT
SELECT
        @ErrorMessage = ERROR_MESSAGE(),
        @ErrorSeverity = ERROR_SEVERITY(),
        @ErrorState = ERROR_STATE()
            RAISERROR (@ErrorMessage, -- Message text.
@ErrorSeverity, -- Severity.
@ErrorState -- State.
)
    ROLLBACK
END CATCH
END
END
print 'Completed purge_table_on_condition for '+cast(@Tablename as nvarchar(300))

--changeset m0a0ggb:AG-12059-1
if not exists(SELECT 1 FROM sys.columns WHERE name = 'CLAIM_TYPE' AND object_id = OBJECT_ID('REJECTIONS'))
ALTER TABLE dbo.REJECTIONS ADD CLAIM_TYPE varchar(32) default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'IS_FULL_LOAD_PRODUCE' AND object_id = OBJECT_ID('REJECTIONS'))
ALTER TABLE dbo.REJECTIONS ADD IS_FULL_LOAD_PRODUCE bit default NULL;
if exists(SELECT 1 FROM sys.columns WHERE name = 'CREATE_USER_ID' AND object_id = OBJECT_ID('REJECTIONS'))
ALTER TABLE dbo.REJECTIONS DROP COLUMN CREATE_USER_ID;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'CREATE_USER' AND object_id = OBJECT_ID('REJECTIONS'))
ALTER TABLE dbo.REJECTIONS ADD CREATE_USER varchar(255) NOT NULL;
if exists(SELECT 1 FROM sys.columns WHERE name = 'DESPOSITION' AND object_id = OBJECT_ID('REJECTIONS'))
ALTER TABLE dbo.REJECTIONS DROP COLUMN DESPOSITION;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'DISPOSITION' AND object_id = OBJECT_ID('REJECTIONS'))
ALTER TABLE dbo.REJECTIONS ADD DISPOSITION varchar(100) default NULL;

--changeset s0g0cl7:SCTA-15852_1 runOnChange:true
CREATE OR ALTER PROCEDURE [dbo].[usp_cc_db_purge_on_condition] @schemaName varchar(500), @tablename varchar(500), @batchSize int, @retentionDays int, @dateColumn varchar(500), @condition varchar(500)
AS
BEGIN
    DECLARE @loop bit = 1
    DECLARE @r int = 0
    DECLARE @rowcount int = 0
    DECLARE @days INT
    DECLARE @sqlCommand NVARCHAR(2048)
    DECLARE @totalRows int
    DECLARE @tblname varchar(500)= @schemaName+'.'+@tablename
    IF(@retentionDays < 10)
    BEGIN
        SET @retentionDays = 10
    END

    IF(@batchSize > 50000)
    BEGIN
        SET @batchSize = 50000
    END

    SET @days = -@retentionDays
    DECLARE @dateCondition varchar(500)
    SET @dateCondition = (SELECT DATEADD(DAY, @days, GETDATE()))

    DECLARE @sqlDelCommand NVARCHAR(2048)
    SET @sqlDelCommand = (' DELETE TOP (' + cast(@batchSize as varchar(10)) + ') FROM ' + @tblname +
                    ' WHERE ' + @dateColumn + ' < ''' + @dateCondition + ''' ' + @condition)

    WHILE (@loop = 1)
        BEGIN
            BEGIN TRY
                BEGIN TRANSACTION
                    EXEC sp_executesql @sqlDelCommand
                    SET @rowcount = @@ROWCOUNT
                    IF (@rowcount = 0 OR @rowcount < @batchSize) SET @loop = 0
                COMMIT TRANSACTION
            END TRY
            BEGIN CATCH
                SET @loop = 0
                DECLARE @ErrorMessage NVARCHAR(4000)
                DECLARE @ErrorSeverity INT
                DECLARE @ErrorState INT
                SELECT
                @ErrorMessage = ERROR_MESSAGE(),
                @ErrorSeverity = ERROR_SEVERITY(),
                @ErrorState = ERROR_STATE()
                -- Use RAISERROR inside the CATCH block to return error
                -- information about the original error that caused
                -- execution to jump to the CATCH block.
                RAISERROR (@ErrorMessage, -- Message text.
                @ErrorSeverity, -- Severity.
                @ErrorState -- State.
                )
                ROLLBACK
            END CATCH
        END
END

-- changeset c0m0pip:SCTEG-2407
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_LABEL_DETAILS_BY_STATUS' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
CREATE NONCLUSTERED INDEX ENDGAME_LABEL_DETAILS_BY_STATUS ON dbo.ENDGAME_LABEL_DETAILS(facilityCountryCode, facilityNum, DELIVERY_NUMBER, STATUS) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_CREATE_TS' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_CREATE_TS ON dbo.RECEIPT(facilityCountryCode, facilityNum, CREATE_TS) INCLUDE (DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER) WITH (ONLINE = ON);

-- changeset c0m0pip:SCTEG-2199
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'JMS_EVENT_RETRY_BY_PICKUP_TIME' AND object_id = OBJECT_ID('JMS_EVENT_RETRY'))
CREATE NONCLUSTERED INDEX JMS_EVENT_RETRY_BY_PICKUP_TIME ON dbo.JMS_EVENT_RETRY(FUTURE_PICKUP_TIME) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_CREATE_TS' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_CREATE_TS ON dbo.RECEIPT(CREATE_TS) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_CREATE_TS' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_CREATE_TS ON dbo.CONTAINER(CREATE_TS) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_UPC_DESTINATION_BY_CREATED_DATE' AND object_id = OBJECT_ID('ENDGAME_UPC_DESTINATION'))
CREATE NONCLUSTERED INDEX ENDGAME_UPC_DESTINATION_BY_CREATED_DATE ON dbo.ENDGAME_UPC_DESTINATION(CREATED_DATE) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_LABEL_DETAILS_BY_CREATED_DATE' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
CREATE NONCLUSTERED INDEX ENDGAME_LABEL_DETAILS_BY_CREATED_DATE ON dbo.ENDGAME_LABEL_DETAILS(CREATED_DATE) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_METADATA_BY_CREATED_DATE' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
CREATE NONCLUSTERED INDEX DELIVERY_METADATA_BY_CREATED_DATE ON dbo.DELIVERY_METADATA(CREATED_DATE) WITH (ONLINE = ON);

--changeset c0m0pip:SCTNGMS-5962_3 runOnChange:true
CREATE OR ALTER PROCEDURE [dbo].[usp_fc_db_purge_container_on_condition] @schemaName varchar(500), @batchSize int, @retentionDays int, @dateColumn varchar(500), @condition varchar(500)
AS
BEGIN
    DECLARE @loop bit = 1
    DECLARE @r int = 0
    DECLARE @rowcount int = 0
    DECLARE @days INT
    DECLARE @sqlCommand NVARCHAR(2048)
    DECLARE @totalRows int
    IF(@retentionDays < 60)
    BEGIN
        SET @retentionDays = 60
    END

    IF(@batchSize > 50000)
    BEGIN
        SET @batchSize = 50000
    END

    SET @days = -@retentionDays
    DECLARE @dateCondition varchar(500)
    SET @dateCondition = (SELECT DATEADD(DAY, @days, GETDATE()))

    DECLARE @sqlDelCommand NVARCHAR(2048)
    SET @sqlDelCommand= ('DECLARE @ContainerTempTable TABLE (cid varchar(50), facility_nbr int);
    INSERT into @ContainerTempTable (cid, facility_nbr)
    SELECT TOP (' + cast(@batchSize as varchar(10)) + ') TRACKING_ID, facilityNum from '+ cast(@schemaName as varchar(50)) +'.CONTAINER with (nolock) WHERE ' + @dateColumn + ' < ''' + @dateCondition + ''' ' + @condition +';

    DECLARE @MyCursor CURSOR;
    DECLARE @facilityNum INT;

    SET @MyCursor = CURSOR FOR select distinct facility_nbr from @ContainerTempTable;

    OPEN @MyCursor
    FETCH NEXT FROM @MyCursor INTO @facilityNum

    WHILE @@FETCH_STATUS = 0
    BEGIN
        delete from '+ cast(@schemaName as varchar(50)) +'.CONTAINER_ITEM where TRACKING_ID in (SELECT temp.cid from @ContainerTempTable temp where facility_nbr = @facilityNum) and facilityNum = @facilityNum;
        delete from '+ cast(@schemaName as varchar(50)) +'.CONTAINER where TRACKING_ID in (SELECT temp.cid from @ContainerTempTable temp where facility_nbr = @facilityNum) and facilityNum = @facilityNum;
        FETCH NEXT FROM @MyCursor INTO @facilityNum;
    END;

    CLOSE @MyCursor;
    DEALLOCATE @MyCursor;
    DELETE from @ContainerTempTable;')

    WHILE (@loop = 1)
        BEGIN
            BEGIN TRY
                BEGIN TRANSACTION
                    EXEC sp_executesql @sqlDelCommand
                    SET @rowcount = @@ROWCOUNT
                    PRINT @rowcount
                    IF (@rowcount = 0) SET @loop = 0
                COMMIT TRANSACTION
            END TRY
            BEGIN CATCH
                SET @loop = 0
                DECLARE @ErrorMessage NVARCHAR(4000)
                DECLARE @ErrorSeverity INT
                DECLARE @ErrorState INT
                SELECT
                @ErrorMessage = ERROR_MESSAGE(),
                @ErrorSeverity = ERROR_SEVERITY(),
                @ErrorState = ERROR_STATE()
                -- Use RAISERROR inside the CATCH block to return error
                -- information about the original error that caused
                -- execution to jump to the CATCH block.
                RAISERROR (@ErrorMessage, -- Message text.
                @ErrorSeverity, -- Severity.
                @ErrorState -- State.
                )
                ROLLBACK
            END CATCH
        END
END


--changeset vn54j03:SCTNGMS-6811 runOnChange:true
if not exists (SELECT 1 FROM sys.columns WHERE name = 'CREATE_TS' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD CREATE_TS DATETIME2;
if not exists (SELECT 1 FROM sys.columns WHERE name = 'LAST_UPDATE_TS' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD LAST_UPDATE_TS DATETIME2;
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_LAST_CHANGED_TS' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_LAST_CHANGED_TS ON dbo.CONTAINER(LAST_CHANGED_TS) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_ITEM_BY_LAST_UPDATE_TS' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
CREATE NONCLUSTERED INDEX CONTAINER_ITEM_BY_LAST_UPDATE_TS ON dbo.CONTAINER_ITEM(LAST_UPDATE_TS) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'DELIVERY_METADATA_BY_LAST_UPDATED_DATE' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
CREATE NONCLUSTERED INDEX DELIVERY_METADATA_BY_LAST_UPDATED_DATE ON dbo.DELIVERY_METADATA(LAST_UPDATED_DATE) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_LABEL_DETAILS_BY_LAST_UPDATED_DATE' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
CREATE NONCLUSTERED INDEX ENDGAME_LABEL_DETAILS_BY_LAST_UPDATED_DATE ON dbo.ENDGAME_LABEL_DETAILS(LAST_UPDATED_DATE) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'ENDGAME_UPC_DESTINATION_BY_LAST_UPDATED_DATE' AND object_id = OBJECT_ID('ENDGAME_UPC_DESTINATION'))
CREATE NONCLUSTERED INDEX ENDGAME_UPC_DESTINATION_BY_LAST_UPDATED_DATE ON dbo.ENDGAME_UPC_DESTINATION(LAST_UPDATED_DATE) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_CREATE_TS' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_CREATE_TS ON dbo.RECEIPT(CREATE_TS) WITH (ONLINE = ON);

-- changeset c0s0ncz:SCTEG-2606
if not exists(SELECT 1 FROM sys.columns WHERE name = 'DIVERT_ACK_EVENT' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
ALTER TABLE dbo.ENDGAME_LABEL_DETAILS ADD DIVERT_ACK_EVENT nvarchar(MAX) default NULL;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'CASE_UPC' AND object_id = OBJECT_ID('ENDGAME_LABEL_DETAILS'))
ALTER TABLE dbo.ENDGAME_LABEL_DETAILS ADD CASE_UPC nvarchar(MAX) default NULL;

-- changeset c0s0ncz:SCTEG-2964
if not exists (select 1 from sysobjects where name = 'EVENT_STORE')
CREATE TABLE dbo.EVENT_STORE (
    ID bigint NOT NULL,
    facilityCountryCode varchar(255),
    facilityNum int NOT NULL,
    EVENT_STORE_KEY varchar(50),
    CONTAINER_ID varchar(50),
    DELIVERY_NUMBER bigint,
    STATUS varchar(20),
    EVENT_TYPE varchar(20),
    PAYLOAD nvarchar(MAX),
    RETRY_COUNT int,
    CREATED_DATE datetime2 NOT NULL DEFAULT getdate(),
    CREATED_BY varchar(32),
    LAST_UPDATED_DATE datetime2 NOT NULL DEFAULT getdate(),
    LAST_UPDATED_BY varchar(32),
  CONSTRAINT pk_event_store PRIMARY KEY (ID)
);

--changeset vn563vz:APPRC-1521
if not exists(SELECT 1 FROM sys.columns WHERE name = 'TENANT_ID' AND object_id = OBJECT_ID('CONTAINER_RLOG'))
ALTER TABLE dbo.CONTAINER_RLOG ADD TENANT_ID varchar(30) default NULL;

-- changeset c0s0ncz:SCTEG-2964-Sequence
if not exists (select 1 from sysobjects where name='EVENT_STORE_SEQUENCE')
CREATE SEQUENCE dbo.EVENT_STORE_SEQUENCE START WITH 1 INCREMENT BY 50 MINVALUE 1 MAXVALUE 9223372036854775807;

--changeset v0b03vz:SCTA-17761-1
if not exists (select 1 from sysobjects where name='LABEL_DATA_LPN')
CREATE TABLE dbo.LABEL_DATA_LPN (
    ID bigint NOT NULL,
    facilityNum int,
    facilityCountryCode varchar(20),
    LPN nvarchar(50) NOT NULL,
    CREATE_TS datetime2,
    LAST_CHANGE_TS datetime2,
    LABEL_DATA_ID bigint NOT NULL FOREIGN KEY REFERENCES LABEL_DATA(ID) ON DELETE CASCADE,
    CONSTRAINT PK_LABEL_DATA_LPN PRIMARY KEY (ID));
if not exists (select 1 from sysobjects where name='LABEL_DATA_LPN_SEQUENCE')
CREATE SEQUENCE dbo.LABEL_DATA_LPN_SEQUENCE START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_LPN_BY_LPN' AND object_id = OBJECT_ID('LABEL_DATA_LPN'))
CREATE UNIQUE NONCLUSTERED INDEX LABEL_DATA_LPN_BY_LPN ON dbo.LABEL_DATA_LPN(LPN, facilityNum, facilityCountryCode);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_LPN_CREATE_TS' AND object_id = OBJECT_ID('LABEL_DATA_LPN'))
CREATE NONCLUSTERED INDEX LABEL_DATA_LPN_CREATE_TS ON dbo.LABEL_DATA_LPN(CREATE_TS);


--changeset vn563vz:APPRC-1496
if not exists(SELECT 1 FROM sys.columns WHERE name = 'PACKAGE_BARCODE_TYPE' AND object_id = OBJECT_ID('RECEIVING_WORKFLOW'))
ALTER TABLE dbo.RECEIVING_WORKFLOW ADD PACKAGE_BARCODE_TYPE varchar(50) default NULL;


--changeset p0d00jo:WFM-4707
if not exists(SELECT 1 FROM sys.columns WHERE name = 'DEPT_SUBCATG_NBR' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD DEPT_SUBCATG_NBR VARCHAR(15) default NULL;

--changeset p0a0291:WFM-5751
If exists (select 1 from sysobjects where name='ARUCO_CONTAINER') and not exists (SELECT 1 FROM sys.columns WHERE name = 'demand_qty' AND object_id = OBJECT_ID('ARUCO_CONTAINER'))
Alter table dbo.ARUCO_CONTAINER add demand_qty float(53) default NULL;

--changeset p0a0291:WFM-6273
If exists (select 1 from sysobjects where name='ARUCO_CONTAINER') and not exists (SELECT 1 FROM sys.columns WHERE name = 'group_id' AND object_id = OBJECT_ID('ARUCO_CONTAINER'))
Alter table dbo.ARUCO_CONTAINER add group_id VARCHAR(50) default NULL;

--changeset n0n02dt:WFM-4818
if not exists(SELECT 1 FROM sys.columns WHERE name = 'ITEM_TYPE_CODE' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD ITEM_TYPE_CODE int default NULL;

--changeset s1b0irp:WFM-5992
if not exists(SELECT 1 FROM sys.columns WHERE name = 'REPLENISHMENT_SUB_TYPE_CODE' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD REPLENISHMENT_SUB_TYPE_CODE int default NULL;


--changeset d0k05qf:SCTA-9952
if not exists (select 1 from sysobjects where name='LABEL_DOWNLOAD_EVENT')
CREATE TABLE dbo.LABEL_DOWNLOAD_EVENT (ID bigint NOT NULL, facilityNum int, facilityCountryCode varchar(20), DELIVERY_NUMBER bigint, PURCHASE_REFERENCE_NUMBER varchar(20), ITEM_NUMBER bigint, REJECT_REASON varchar(50), STATUS varchar(20), CREATE_TS datetime2, PUBLISHED_TS datetime2, RETRY_COUNT int, MESSAGE_PAYLOAD varchar(1000), LAST_CHANGE_TS datetime2, MISC_INFO  varchar(3000), VERSION int PRIMARY KEY (ID));
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DOWNLOAD_EVENT_BY_DELIVERY_PO_ITEM' AND object_id = OBJECT_ID('LABEL_DOWNLOAD_EVENT'))
CREATE NONCLUSTERED INDEX LABEL_DOWNLOAD_EVENT_BY_DELIVERY_PO_ITEM ON dbo.LABEL_DOWNLOAD_EVENT(facilityNum, facilityCountryCode, DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER, ITEM_NUMBER);

--changeset s0k09hn:SCTA-10468
if not exists (select 1 from sysobjects where name='LABEL_DOWNLOAD_EVENT_SEQUENCE')
CREATE SEQUENCE dbo.LABEL_DOWNLOAD_EVENT_SEQUENCE START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;

--changeset vn55snk:SCTA-12205
if exists (SELECT 1 FROM sys.columns WHERE name = 'ALLOCATION' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ALTER COLUMN ALLOCATION VARCHAR(MAX);



--changeset vn55snk:SCTA-11687
if not exists (select 1 from sysobjects where name='AUDIT_DATA_LOG')
CREATE TABLE dbo.AUDIT_DATA_LOG (ID bigint NOT NULL, facilityCountryCode varchar(255), facilityNum int, ASN_NBR varchar(30), DELIVERY_NUMBER bigint NOT NULL, SSCC_NUMBER varchar(30), AUDIT_STATUS varchar(20), CREATED_BY varchar(32), CREATED_TS datetime2, COMPLETED_BY varchar(32), COMPLETED_TS datetime2, LAST_UPDATED_BY varchar(32), LAST_UPDATED_TS datetime2, VERSION int NOT NULL);

if not exists (select 1 from sysobjects where name='AUDIT_LOG_SEQUENCE')
CREATE SEQUENCE dbo.AUDIT_LOG_SEQUENCE START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807;

--changeset d0k05qf:SCTA-14596
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_BY_LPNS' AND object_id = OBJECT_ID('LABEL_DATA'))
CREATE NONCLUSTERED INDEX LABEL_DATA_BY_LPNS ON dbo.LABEL_DATA ( facilityNum, facilityCountryCode ) INCLUDE (lpns) WITH (ONLINE = ON, MAXDOP = 2);

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_BY_PO_AND_ITEM' AND object_id = OBJECT_ID('LABEL_DATA'))
CREATE NONCLUSTERED INDEX LABEL_DATA_BY_PO_AND_ITEM ON dbo.LABEL_DATA ( facilityNum, facilityCountryCode, PURCHASE_REFERENCE_NUMBER, ITEM_NUMBER ) WITH (ONLINE = ON, MAXDOP = 2);

--changeset vn564qj:SCTA-14534
if not exists(SELECT 1 FROM sys.columns WHERE name = 'TRACKING_ID' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD TRACKING_ID varchar(40) default NULL;

--changeset b0s06hg:SCTA-14729
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_BY_TRACKING_ID' AND object_id = OBJECT_ID('LABEL_DATA'))
CREATE NONCLUSTERED INDEX LABEL_DATA_BY_TRACKING_ID ON dbo.LABEL_DATA(facilityNum, facilityCountryCode, TRACKING_ID) WITH (ONLINE = ON);

--changeset s0s0qa1:SCTA-15341
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_BY_SSCC_AND_ASN' AND object_id = OBJECT_ID('LABEL_DATA'))
CREATE NONCLUSTERED INDEX LABEL_DATA_BY_SSCC_AND_ASN ON dbo.LABEL_DATA(facilityNum, facilityCountryCode, SSCC, ASN_NBR) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'AUDIT_DATA_LOG_BY_DELIVERY' AND object_id = OBJECT_ID('AUDIT_DATA_LOG'))
CREATE NONCLUSTERED INDEX AUDIT_DATA_LOG_BY_DELIVERY ON dbo.AUDIT_DATA_LOG(facilityNum, facilityCountryCode, DELIVERY_NUMBER) WITH (ONLINE = ON);
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'AUDIT_DATA_LOG_BY_ASN_SSCC' AND object_id = OBJECT_ID('AUDIT_DATA_LOG'))
CREATE NONCLUSTERED INDEX AUDIT_DATA_LOG_BY_ASN_SSCC ON dbo.AUDIT_DATA_LOG(facilityNum, facilityCountryCode, ASN_NBR, SSCC_NUMBER) WITH (ONLINE = ON);

--changeset b0s06hg:SCTA-15795
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'RECEIPT_BY_SSCC_AND_CREATE_TS' AND object_id = OBJECT_ID('RECEIPT'))
CREATE NONCLUSTERED INDEX RECEIPT_BY_SSCC_AND_CREATE_TS ON dbo.RECEIPT(facilityCountryCode, facilityNum, SSCC_NUMBER, SHIPMENT_DOCUMENT_ID, CREATE_TS) WITH (ONLINE = ON);

--changeset vn55pvl:SCTA-17157-2
if not exists (select 1 from sysobjects where name='LABEL_SEQUENCE')
CREATE TABLE dbo.LABEL_SEQUENCE (ID bigint NOT NULL, facilityNum int, facilityCountryCode varchar(20),ITEM_NUMBER bigint, MUST_ARRIVE_BEFORE_DATE datetime2,PURCHASE_REFERENCE_LINE_NUMBER int,  NEXT_SEQUENCE_NO bigint, LABEL_TYPE tinyint,CREATE_TS datetime2, LAST_CHANGE_TS datetime2, CONSTRAINT PK__LABEL__SEQUENCE PRIMARY KEY (ID));

if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_SEQUENCE_BY_MABD_POLINE_ITEM' AND object_id = OBJECT_ID('LABEL_SEQUENCE'))
CREATE NONCLUSTERED INDEX LABEL_SEQUENCE_BY_MABD_POLINE_ITEM ON dbo.LABEL_SEQUENCE ( facilityNum, facilityCountryCode,MUST_ARRIVE_BEFORE_DATE, PURCHASE_REFERENCE_LINE_NUMBER,ITEM_NUMBER) WITH (ONLINE = ON);

if not exists (select 1 from sysobjects where name='LABEL_SEQUENCE_SEQ')
CREATE SEQUENCE dbo.LABEL_SEQUENCE_SEQ START WITH 1 INCREMENT BY 100 MINVALUE 1 MAXVALUE 9223372036854775807;
-- changeset s0g0gp4:AG-10255_1
if not exists(SELECT 1 FROM sys.columns WHERE name = 'LAST_CHANGED_TS' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD LAST_CHANGED_TS datetime2;
if not exists(SELECT 1 FROM sys.columns WHERE name = 'LAST_CHANGED_TS' AND object_id = OBJECT_ID('RECEIPT'))
ALTER TABLE dbo.RECEIPT ADD LAST_CHANGED_TS datetime2;

--changeset p0d00jo:WFM-3995
if not exists(SELECT 1 FROM sys.columns WHERE name = 'HYBRID_STORAGE_FLAG' AND object_id = OBJECT_ID('CONTAINER_ITEM'))
ALTER TABLE dbo.CONTAINER_ITEM ADD HYBRID_STORAGE_FLAG VARCHAR(10) default NULL;

if not exists(SELECT 1 FROM sys.columns WHERE name = 'ELIGIBILITY' AND object_id = OBJECT_ID('CONTAINER'))
ALTER TABLE dbo.CONTAINER ADD ELIGIBILITY smallint default NULL;

--changeset b0s06hg:SCTA-10898
if not exists(SELECT 1 FROM sys.columns WHERE name = 'LABEL_SEQUENCE_NBR' AND object_id = OBJECT_ID('LABEL_DATA'))
ALTER TABLE dbo.LABEL_DATA ADD LABEL_SEQUENCE_NBR bigint default NULL;

--changeset s0k09hn:SCTA-18881-1
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'LABEL_DATA_BY_PO_AND_POLINE' AND object_id = OBJECT_ID('LABEL_DATA'))
CREATE NONCLUSTERED INDEX LABEL_DATA_BY_PO_AND_POLINE ON dbo.LABEL_DATA ( facilityNum, facilityCountryCode, PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER ) WITH (ONLINE = ON);

-- changeset s1b0roo:SCTEG-3222
if not exists(SELECT 1 FROM sys.columns WHERE name = 'ATTACHED_PO_NUMBERS' AND object_id = OBJECT_ID('DELIVERY_METADATA'))
ALTER TABLE dbo.DELIVERY_METADATA ADD ATTACHED_PO_NUMBERS nvarchar(MAX) default NULL;

--changeset vn57lke:APPRC-1773
if not exists(SELECT 1 FROM sys.columns WHERE name = 'additional_attributes' AND object_id = OBJECT_ID('receiving_workflow'))
ALTER TABLE dbo.receiving_workflow ADD additional_attributes nvarchar(max) default NULL;

-- changeset z0k015a:SCTA-8912
IF EXISTS (SELECT 1 FROM sys.columns WHERE name = 'relay_error' AND object_id = OBJECT_ID('dbo.OUTBOX_EVENT'))
BEGIN
ALTER TABLE dbo.OUTBOX_EVENT ALTER COLUMN relay_error varchar(4000)
END;


--changeset v0b03vz:SCTA-19096
if exists (select 1 from sysobjects where name='LABEL_DATA_LPN_SEQUENCE')
ALTER SEQUENCE dbo.LABEL_DATA_LPN_SEQUENCE INCREMENT BY 5000;
--changeset c0m0pip:SCTEG-3686
if not exists(SELECT 1 FROM sys.indexes WHERE name = 'CONTAINER_BY_FACILITY_NUM_PARENT_TRACKINGID' AND object_id = OBJECT_ID('CONTAINER'))
CREATE NONCLUSTERED INDEX CONTAINER_BY_FACILITY_NUM_PARENT_TRACKINGID ON dbo.CONTAINER(facilityCountryCode, facilityNum, PARENT_TRACKING_ID) WITH (ONLINE = ON);
