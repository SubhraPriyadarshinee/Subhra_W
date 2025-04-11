package com.walmart.move.nim.receiving.wfs.config;

import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnExpression("${enable.wfs.app:false}")
@Configuration
public class WFSConfig {

  @Bean(WFSConstants.WFS_INSTRUCTION_SERVICE)
  public WFSInstructionService wfsInstructionService() {
    return new WFSInstructionService();
  }

  @Bean(WFSConstants.WFS_CONTAINER_SERVICE)
  public WFSContainerService wfsContainerService() {
    return new WFSContainerService();
  }

  @Bean(WFSConstants.WFS_INVENTORY_ADJUSTMENT_PROCESSOR)
  public EventProcessor wfsInventoryEventProcessor() {
    return new WFSInventoryEventProcessor();
  }

  @Bean(WFSConstants.WFS_DOCK_TAG_SERVICE)
  public DockTagService wfsDockTagService() {
    return new WFSDockTagService();
  }

  @Bean(WFSConstants.WFS_CONTAINER_REQUEST_HANDLER)
  public WFSContainerRequestHandler wfsContainerRequestHandler() {
    return new WFSContainerRequestHandler();
  }

  @Bean(WFSConstants.WFS_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER)
  public UpdateContainerQuantityRequestHandler WFSUpdateContainerQuantityRequestHandler() {
    return new WFSUpdateContainerQuantityRequestHandler();
  }

  @Bean(WFSConstants.WFS_RECEIPT_SUMMARY_PROCESSOR)
  public WFSReceiptSummaryProcessor wfsReceiptSummaryProcessor() {
    return new WFSReceiptSummaryProcessor();
  }

  @Bean(WFSConstants.WFS_DELIVERY_METADATA_SERVICE)
  public WFSDeliveryMetaDataService wfsDeliveryMetaDataService() {
    return new WFSDeliveryMetaDataService();
  }

  @Bean(WFSConstants.WFS_RECEIVE_INSTRUCTION_HANDLER)
  public ReceiveInstructionHandler wfsReceiveInstructionHandler() {
    return new WFSReceiveInstructionHandler();
  }

  @Bean(WFSConstants.WFS_CANCEL_MULTIPLE_INSTRUCTION_REQUEST_HANDLER)
  public CancelMultipleInstructionRequestHandler wfsCancelMultipleInstructionRequestHandler() {
    return new WFSCancelMultipleInstructionRequestHandler();
  }

  @Bean(WFSConstants.WFS_INSTRUCTION_UTILS)
  public WFSInstructionUtils wfsInstructionUtils() {
    return new WFSInstructionUtils();
  }

  @Bean(WFSConstants.WFS_INSTRUCTION_SEARCH_REQUEST_HANDLER)
  public WFSInstructionSearchRequestHandler wfsInstructionSearchRequestHandler() {
    return new WFSInstructionSearchRequestHandler();
  }

  @Bean(WFSConstants.WFS_LABEL_ID_PROCESSOR)
  public LabelIdProcessor wfsLabelIdProcessor() {
    return new WFSLabelIdProcessor();
  }

  @Bean(ReceivingConstants.WFS_LABEL_DATA_PROCESSOR)
  public LabelDataProcessor wfsLabelDataProcessor() {
    return new WFSLabelDataProcessor();
  }

  @Bean(WFSConstants.WFS_TWO_D_BARCODE_SCAN_TYPE_DOCUMENT_SEARCH_HANDLER)
  public WFSTwoDBarcodeScanTypeDocumentSearchHandler wfsTwoDBarcodeScanTypeDocumentSearchHandler() {
    return new WFSTwoDBarcodeScanTypeDocumentSearchHandler();
  }

  @Bean(WFSConstants.WFS_INSTRUCTION_HELPER_SERVICE)
  public WFSInstructionHelperService wfsInstructionHelperService() {
    return new WFSInstructionHelperService();
  }
}
