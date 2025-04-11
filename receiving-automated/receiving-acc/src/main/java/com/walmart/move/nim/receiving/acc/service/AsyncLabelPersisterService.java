package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.contract.prelabel.LabelingService;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import com.walmart.move.nim.receiving.core.service.PrintingAndLabellingService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class AsyncLabelPersisterService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncLabelPersisterService.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = ReceivingConstants.GENERIC_LABELING_SERVICE)
  private LabelingService labelingService;

  @Autowired private PrintingAndLabellingService printingAndLabellingService;

  private Map<String, List<String>> createPOPOLPNListMap(List<LabelData> labelDataList) {
    LOGGER.info(
        "Labelling POST: Creating map of PO-POL and lpn list for label data list size {}",
        labelDataList.size());
    Map<String, List<String>> poPOAndLPNsMap = new HashMap<>();
    String key;
    for (LabelData labelData : labelDataList) {
      if (!StringUtils.isEmpty(labelData.getLabel())) {
        key =
            labelData.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + labelData.getPurchaseReferenceLineNumber();
        List<String> lpnList = poPOAndLPNsMap.get(key);
        if (CollectionUtils.isEmpty(lpnList)) {
          lpnList = new ArrayList<>();
          poPOAndLPNsMap.put(key, lpnList);
        }
        List lpns = JacksonParser.convertJsonToObject(labelData.getLpns(), List.class);
        lpnList.addAll(lpns);
      }
    }
    return poPOAndLPNsMap;
  }

  private void partitionAndPostInBatches(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      List<String> lpns,
      HttpHeaders httpHeaders,
      String inboundDoor) {
    LOGGER.info(
        "Labelling POST: Partition the list and post in batches. LPN list size {}:", lpns.size());
    Collection<List<String>> partitionedLPNList =
        ReceivingUtils.batchifyCollection(lpns, appConfig.getLabellingServiceCallBatchCount());
    for (List<String> lpnList : partitionedLPNList) {
      preparePrintableLabelDataAndPost(
          deliveryDocument, deliveryDocumentLine, lpnList, httpHeaders, inboundDoor);
    }
  }

  private void preparePrintableLabelDataAndPost(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      List<String> lpnList,
      HttpHeaders httpHeaders,
      String inboundDoor) {
    List<PrintableLabelDataRequest> printableLabelDataRequests = new ArrayList<>();
    LOGGER.info(
        "Labelling POST: Prepare label data list and post. LPN list size {}:", lpnList.size());
    for (String lpn : lpnList) {
      printableLabelDataRequests.add(
          (PrintableLabelDataRequest)
              labelingService.getPrintableLabelDataRequest(
                  deliveryDocument,
                  deliveryDocumentLine,
                  lpn,
                  appConfig.getLabelTTLInHours(),
                  inboundDoor));
    }
    printingAndLabellingService.postToLabelling(printableLabelDataRequests, httpHeaders);
  }

  /**
   * Publis Label data to labelling service async
   *
   * @param deliveryDetails delivery details
   * @param labelDataList label data list
   * @param httpHeaders http headers
   */
  @Async
  public void publishLabelDataToLabelling(
      DeliveryDetails deliveryDetails, List<LabelData> labelDataList, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Preparing to publish label data to labelling for delivery no. {}. Start time {}",
        deliveryDetails.getDeliveryNumber(),
        new Date());
    Map<String, List<String>> poPOAndLPNsMap = createPOPOLPNListMap(labelDataList);
    String key;
    List<String> lpns;
    for (DeliveryDocument deliveryDocument : deliveryDetails.getDeliveryDocuments()) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        key =
            deliveryDocumentLine.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        lpns = poPOAndLPNsMap.get(key);
        if (!CollectionUtils.isEmpty(lpns)) {
          partitionAndPostInBatches(
              deliveryDocument,
              deliveryDocumentLine,
              lpns,
              httpHeaders,
              deliveryDetails.getDoorNumber());
        }
      }
    }
    LOGGER.info(
        "Completed publish label data to labelling for delivery no. {}. Complete time {}",
        deliveryDetails.getDeliveryNumber(),
        new Date());
  }
}
