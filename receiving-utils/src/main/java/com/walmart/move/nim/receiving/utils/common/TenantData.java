package com.walmart.move.nim.receiving.utils.common;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantData {

  private Integer facilityNum;
  private String facilityCountryCode;
  private String correlationId;
  private String messageId;
  private String messageIdempotencyId;
  private long atlasRcvCrInsStart;
  private long atlasRcvCrInsEnd;
  private long atlasRcvGdmDelStatPubStart;
  private long atlasRcvGdmDelStatPubEnd;
  private long atlasRcvGdmGetDocLineStart;
  private long atlasRcvGdmGetDocLineEnd;
  private long atlasRcvGetRcvdQtyStart;
  private long atlasRcvGetRcvdQtyEnd;
  private long atlasRcvChkInsExistStart;
  private long atlasRcvChkInsExistEnd;
  private long atlasRcvChkNewInstCanBeCreatedStart;
  private long atlasRcvChkNewInstCanBeCreatedEnd;
  private long atlasRcvOfCallStart;
  private long atlasRcvOfCallEnd;
  private long atlasRcvCompInsSaveStart;
  private long atlasRcvCompInsSaveEnd;
  private long atlasRcvWfmPubStart;
  private long atlasRcvWfmPubEnd;
  private long atlasRcvOfHeaders;
  private long atlasRcvOpnQty;
  private long atlasRcvLpnCallStart;
  private long atlasRcvLpnCallEnd;
  private Map<String, Object> additionalParams;

  // Below are being used in Rx Complete Instruction
  private long completeInstrStart;
  private long completeInstrSlottingCallStart;
  private long completeInstrSlottingCallEnd;
  private long completeInstrNimRdsCallStart;
  private long completeInstrNimRdsCallEnd;
  private long completeInstrEpcisCallStart;
  private long completeInstrEpcisCallEnd;
  private long completeInstrPersistDBCallStart;
  private long completeInstrPersistDBCallEnd;
  private long completeInstrCompleteProblemCallStart;
  private long completeInstrCompleteProblemCallEnd;
  private long completeInstrEnd;

  // Update instruction
  private long updateInstrStart;
  private long updateInstrEnd;

  // Create instruction
  private long createInstrStart;
  private long createInstrEnd;
  private long createInstrUpdateComplianceDateToGDMCallStart;
  private long createInstrUpdateComplianceDateToGDMCallEnd;
  private long createInstr4UpcReceivingCallStart;
  private long createInstr4UpcReceivingCallEnd;
  private long createInstrUpdateItemDetailsNimRdsCallStart;
  private long createInstrUpdateItemDetailsNimRdsCallEnd;
  private long createInstrGetPrimeSlotCallStart;
  private long createInstrGetPrimeSlotCallEnd;
  private long createInstrGetPrimeSlotForSplitPalletCallStart;
  private long createInstrGetPrimeSlotForSplitPalletCallEnd;

  // Receive instruction
  private long receiveInstrStart;
  private long receiveInstrEnd;
  private long receiveInstrNimRdsCallStart;
  private long receiveInstrNimRdsCallEnd;
  private long receiveInstrCreateLabelCallStart;
  private long receiveInstrCreateLabelCallEnd;
  private long receiveInstrGetReceiptsNimRdsCallStart;
  private long receiveInstrGetReceiptsNimRdsCallEnd;
  private long receiveInstrPublishWftCallStart;
  private long receiveInstrPublishWftCallEnd;
  private long receiveInstrPublishReceiptsCallStart;
  private long receiveInstrPublishReceiptsCallEnd;
  private long receiveInstrCreateReceiptsCallStart;
  private long receiveInstrCreateReceiptsCallEnd;
  private long receiveInstrLPNCallStart;
  private long receiveInstrLPNCallEnd;
  private long receiveInstrGetSlotCallStart;
  private long receiveInstrGetSlotCallEnd;
  private long receiveInstrGetSlotCallWithRdsPayloadStart;
  private long receiveInstrGetSlotCallWithRdsPayloadEnd;
  private long receiveInstrPublishSymPutawayCallStart;
  private long receiveInstrPublishSymPutawayCallEnd;
  private long receiveInstrGetProblemDetailsCallStart;
  private long receiveInstrGetProblemDetailsCallEnd;
  private long receiveInstrReportProblemReceivedQtyCallStart;
  private long receiveInstrReportProblemReceivedQtyCallEnd;
  private long receiveInstrPublishMoveCallStart;
  private long receiveInstrPublishMoveCallEnd;
  private long receiveInstrPostDcFinReceiptsCallStart;
  private long receiveInstrPostDcFinReceiptsCallEnd;

  // DA Receiving
  private long daCaseReceivingStart;
  private long daCaseReceivingEnd;
  private long daCaseReceivingRdsCallStart;
  private long daCaseReceivingRdsCallEnd;
  private long daCaseReceivingPublishWFTCallStart;
  private long daCaseReceivingPublishWFTCallEnd;
  private long daCaseReceivingDataPersistStart;
  private long daCaseReceivingDataPersistEnd;
  private long daQtyReceivingStart;
  private long daQtyReceivingEnd;
  private long daCaseReceivingAthenaPublishStart;
  private long daCaseReceivingAthenaPublishEnd;
  private long daCaseReceivingSorterPublishStart;
  private long daCaseReceivingSorterPublishEnd;
  private long daCaseReceivingPutawayPublishStart;
  private long daCaseReceivingPutawayPublishEnd;
  private long persistOutboxEventsForDAStart;
  private long persistOutboxEventsForDAEnd;
  private long fetchItemOverrideDBCallStart;
  private long fetchItemOverrideDBCallEnd;
  private long fetchDeliveryItemByDeliveryNumberAndItemNumberDBCallStart;
  private long fetchDeliveryItemByDeliveryNumberAndItemNumberDBCallEnd;
  private long fetchDeliveryItemByItemNumberDBCallEnd;
  private long fetchDeliveryItemByItemNumberDBCallStart;
  private long fetchPutawayQtyByContainerDBStart;
  private long fetchPutawayQtyByContainerDBEnd;
  private long fetchLabelDataByPoAndItemNumberDBCallStart;
  private long fetchLabelDataByPoAndItemNumberDBCallEnd;
  private long fetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallStart;
  private long fetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallEnd;
  private long fetchItemConfigServiceCallStart;
  private long fetchItemConfigServiceCallEnd;
  private long postReceivingUpdatesInKafkaStart;
  private long postReceivingUpdatesInKafkaEnd;
  private long fetchSlotFromSmartSlottingStart;
  private long fetchSlotFromSmartSlottingEnd;
  private long receiveContainersInRdsStart;
  private long receiveContainersInRdsEnd;

  // DSDC Receiving
  private long dsdcCaseReceivingStart;
  private long dsdcCaseReceivingEnd;
  private long dsdcCaseReceivingRdsCallStart;
  private long dsdcCaseReceivingRdsCallEnd;
  private long dsdcCaseReceivingDataPersistStart;
  private long dsdcCaseReceivingDataPersistEnd;
  private long dsdcCaseReceivingPublishWFTCallStart;
  private long dsdcCaseReceivingPublishWFTCallEnd;

  // RDS Receive Instruction Handler
  private long publishRdcInstructionCallStart;
  private long publishRdcInstructionCallEnd;
  private long publishRdcVoidLpnToMirageCallStart;
  private long publishRdcVoidLpnToMirageCallEnd;

  // Async NimRds Receive Containers
  private long asyncNimRdsReceivedContainersCallStart;
  private long asyncNimRdsReceivedContainersCallEnd;

  // Hawkeye API
  private long fetchLabelDataListByGettingLpnsFromHawkeyeDBCallStart;
  private long fetchLabelDataListByGettingLpnsFromHawkeyeDBCallEnd;
  private long fetchLpnsFromHawkeyeStart;
  private long fetchLpnsFromHawkeyeEnd;

  // Auto Receive Cases
  private long autoReceiveCaseStart;
  private long autoReceiveCaseEnd;

  private long atlasAutoReceiveStartTs;
  private long atlasAutoReceiveEndTs;

  // Refresh instruction
  private long refreshInstrStart;
  private long refreshInstrEnd;
  private long refreshInstrDBCallStart;
  private long refreshInstrDBCallEnd;
  private long refreshInstrValidateExistringInstrCallStart;
  private long refreshInstrValidateExistringInstrCallEnd;

  // Cancel instruction
  private long cancelInstrStart;
  private long cancelInstrEnd;

  // DockTag
  private long createDockTagStart;
  private long createDockTagEnd;
  private long createDTPublishContainerAndMoveCallStart;
  private long createDTPublishContainerAndMoveCallEnd;
  private long createDockTagFetchLpnsCallStart;
  private long createDockTagFetchLpnsCallEnd;
  private long createDockTagGenerateLabelDataCallStart;
  private long createDockTagGenerateLabelDataCallEnd;
  private long createDockTagInstrCallStart;
  private long createDockTagInstrCallEnd;
  private long createDockTagContainerCallStart;
  private long createDockTagContainerCallEnd;

  private long receiveDockTagStart;
  private long receiveDockTagEnd;

  private long completeDockTagStart;
  private long completeDockTagEnd;

  private long searchDockTagStart;
  private long searchDockTagEnd;

  private long partialCompleteDockTagStart;
  private long partialCompleteDockTagEnd;

  // RDC Nim-RDS
  private long nimRdsReceivedQtyByDeliveryDocumentsCallStart;
  private long nimRdsReceivedQtyByDeliveryDocumentsCallEnd;
  private long nimRdsReceivedQtyByDeliveryDocumentLineCallStart;
  private long nimRdsReceivedQtyByDeliveryDocumentLineCallEnd;
  private long nimRdsItemDetailsCallStart;
  private long nimRdsItemDetailsCallEnd;
  private long nimRdsReceiveContainersCallStart;
  private long nimRdsReceiveContainersCallEnd;
  private long nimRdsUpdateQuantityCallStart;
  private long nimRdsUpdateQuantityCallEnd;
  private long nimRdsBackoutDALabelStart;
  private long nimRdsBackoutDALabelEnd;
  private long nimRdsReceiveDsdcStart;
  private long nimRdsReceiveDsdcEnd;

  // RDC - Receipt Summary
  private long gdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoAsyncCallStart;
  private long gdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoAsyncCallEnd;
  private long gdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoLineAsyncCallStart;
  private long gdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoLineAsyncCallEnd;

  private long fetchContainerItemByUpcAndItemNumberCallStart;
  private long fetchContainerItemByUpcAndItemNumberCallEnd;
  private Integer subcenterId;
  private Integer orgUnitId;

  private long gdmDeliveryInfoAndNimRdsStoreDistributionByDeliveryPoPoLineAsyncCallStart;
  private long gdmDeliveryInfoAndNimRdsStoreDistributionByDeliveryPoPoLineAsyncCallEnd;
  private String eventType;

  private long publishEICallStart;
  private long publishEICallEnd;

  // RDC - Label Download to Sym
  private long labelDownloadStart;
  private long labelDownloadEnd;
  private long gdmDeliveryInfoByDeliveryItemNumberCallStart;
  private long gdmDeliveryInfoByDeliveryItemNumberCallEnd;
  private long labelConstructAndPublishToHawkeyeStart;
  private long labelConstructAndPublishToHawkeyeEnd;
  private long labelDownloadEventPersistStart;
  private long labelDownloadEventPersistEnd;
  private long labelDataPossibleUpcPersistStart;
  private long labelDataPossibleUpcPersistEnd;
  private long LabelDataFetchWithPoAndPoLineAndItemNumberAndStatusStart;
  private long LabelDataFetchWithPoAndPoLineAndItemNumberAndStatusEnd;

  private long instructionBlobDownloadStart;
  private long instructionBlobDownloadEnd;
  private long instructionDownloadStart;
  private long instructionDownloadEnd;
  private long labelDownloadEventIncludingAutomationStart;
  private long labelDownloadEventIncludingAutomationEnd;

  private long fetchExistingLpnInLabelDataStart;
  private long fetchExistingLpnInLabelDataEnd;
  private long fetchPreviousTrackingInLabelDataStart;
  private long fetchPreviousTrackingInLabelDataEnd;
  private long fetchUpdatedTrackingInLabelDataStart;
  private long fetchUpdatedTrackingInLabelDataEnd;
  private long updateTrackingIdStatusCancelledStart;
  private long updateTrackingIdStatusCancelledEnd;
  private long insertLabelDataStart;
  private long insertLabelDataEnd;
  private long insertUpdatedLabelDataStart;
  private long insertUpdatedLabelDataEnd;
  private long offlineReceivingProcessingStart;
  private long offlineReceivingProcessingEnd;
  private long offlinePublishPostReceivingTimeStart;
  private long offlinePublishPostReceivingTimeEnd;

  private long atlasDsdcReceivePackStart;
  private long atlasDsdcReceivePackEnd;

  private long atlasDsdcReceivePackDBPersistStart;
  private long atlasDsdcReceivePackDBPersistEnd;

  private long atlasDsdcReceivePackPostReceivingUpdatesStart;
  private long atlasDsdcReceivePackPostReceivingUpdatesEnd;

  private long receiveAuditPackCallStart;
  private long receiveAuditPackCallEnd;
  private long receiveDsdcCallStart;
  private long receiveDsdcCallEnd;
  private long receiveAuditPackDBPersistCallStart;
  private long receiveAuditPackDBPersistCallEnd;
  private long updateAuditStatusInGdmStart;
  private long updateAuditStatusInGdmEnd;
  private long republishToHawkeyeStart;
  private long republishToHawkeyeEnd;
  private long gdmItemUpdateEventStart;
  private long gdmItemUpdateEventEnd;
  private long fetchAndFilterLabelDownloadEventsStart;
  private long fetchAndFilterLabelDownloadEventsEnd;
  private long atlasDsdcAsyncReceivePackStart;
  private long atlasDsdcAsyncReceivePackEnd;

  // OrderFulfillment Api
  private long orderFulfillmentShippingLabelFromRoutingLabelStart;
  private long orderFulfillmentShippingLabelFromRoutingLabelEnd;

  // SSTK Label Generation
  private long overallSSTKLabelGenerationStart;
  private long overallSSTKLabelGenerationEnd;
  private long fetchSSTKAutoDeliveryDetailsStart;
  private long fetchSSTKAutoDeliveryDetailsEnd;
  private long validateDeliveryEventAndPublishStatusStart;
  private long validateDeliveryEventAndPublishStatusEnd;
  private long generateGenericLabelForSSTKStart;
  private long generateGenericLabelForSSTKEnd;
  private long fetchAndFilterSSTKDeliveryDocumentsStart;
  private long fetchAndFilterSSTKDeliveryDocumentsEnd;
  private long deliveryEventPersistDBStart;
  private long deliveryEventPersistDBEnd;
  private long smartSlottingPrimeSlotDetailsStart;
  private long smartSlottingPrimeSlotDetailsEnd;
  private long findInstructionByDeliveryNumberAndSsccStart;
  private long findInstructionByDeliveryNumberAndSsccEnd;
  private long atlasDsdcUpdatePackStart;
  private long atlasDsdcUpdatePackEnd;
  private long atlasDsdcUpdatePackDBPersistStart;
  private long atlasDsdcUpdatePackDBPersistEnd;
  private long fetchAuditPackCallStart;
  private long fetchAuditPackCallEnd;
}
