package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.constants.LabelingConstants;
import com.walmart.move.nim.receiving.acc.model.hawkeye.label.HawkEyeLabelDataTO;
import com.walmart.move.nim.receiving.acc.model.hawkeye.label.HawkEyeScanItem;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * @author sks0013 Generates store friendly label based on delivery event and delivery details
 *     passed specific to HawkEye
 */
public class HawkEyeLabelGeneratorService extends GenericLabelGeneratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkEyeLabelGeneratorService.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * create or update label data based on active channel of PO/POL
   *
   * @param checkIfCreated checks if label data already exists
   * @param deliveryDocument PO
   * @param deliveryDocumentLine PO Line
   */
  @Override
  protected List<LabelData> createOrUpdateLabelData(
      boolean checkIfCreated,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String inboundDoor)
      throws ReceivingException {
    List<LabelData> labelDataList =
        getOrCreateLabelData(checkIfCreated, deliveryDocument, deliveryDocumentLine);
    boolean shouldProcessLabel =
        InstructionUtils.isDAConFreight(
            deliveryDocumentLine.getIsConveyable(),
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods());
    if (shouldProcessLabel) {
      LOGGER.info(
          "HawkEye LG: PO {} Line {} is conveyable",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      setConveyableLinesInLabelData(deliveryDocumentLine, labelDataList, null);
    } else {
      LOGGER.info(
          "HawkEye LG: PO {} Line {} is not conveyable",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      setNonConLinesInLabelData(deliveryDocumentLine, labelDataList);
    }
    return labelDataList;
  }

  /**
   * Publish labels to Hawkeye
   *
   * @param deliveryNumber delivery number of delivery for which labels are to be published
   * @param poLineLabelDataMap List of labels per po line to be published
   */
  public void publishLabelsToAcl(
      Map<DeliveryDocumentLine, List<LabelData>> poLineLabelDataMap,
      Long deliveryNumber,
      String doorNumber,
      String trailer,
      boolean isPartialLabels) {
    LOGGER.info(
        "Publishing only delta labels to HawkEye for delivery {} with correlation id {}",
        deliveryNumber,
        TenantContext.getCorrelationId());
    publishACLLabelDataDelta(
        poLineLabelDataMap, deliveryNumber, doorNumber, trailer, ReceivingUtils.getHeaders());
  }

  @Override
  protected Map<String, Object> getMessageHeadersForLabelDataPartial(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = getBaseMessageHeaders(deliveryNumber, httpHeaders);
    messageHeaders.put(
        ACCConstants.HAWK_EYE_DELIVERY_TYPE_HEADER_NAME,
        ACCConstants.ACL_DELTA_PUBLISH_PARTIAL_DELIVERY);
    return messageHeaders;
  }

  @Override
  protected Map<String, Object> getMessageHeadersForLabelDataComplete(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = getBaseMessageHeaders(deliveryNumber, httpHeaders);
    messageHeaders.put(
        ACCConstants.HAWK_EYE_DELIVERY_TYPE_HEADER_NAME,
        ACCConstants.ACL_DELTA_PUBLISH_COMPLETE_DELIVERY);
    return messageHeaders;
  }

  @Override
  protected Map<String, Object> getBaseMessageHeaders(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = new HashMap<>();
    httpHeaders.toSingleValueMap().forEach(messageHeaders::put);
    messageHeaders.put(
        ReceivingConstants.PRODUCT_NAME_HEADER_KEY, ReceivingConstants.APP_NAME_VALUE);
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    messageHeaders.put(
        ReceivingConstants.CONTENT_ENCODING, ReceivingConstants.CONTENT_ENCODING_GZIP);
    return messageHeaders;
  }

  /**
   * Compile Label data for given delivery number
   *
   * @param poLineLabelDataMap Map of PO lines and their corresponding label data list
   * @return Label data accepted by HawkEye
   */
  @Override
  public void publishACLLabelDataPayload(
      Map<DeliveryDocumentLine, List<LabelData>> poLineLabelDataMap,
      Long deliveryNumber,
      String doorNumber,
      String trailer,
      Map<String, Object> headers) {
    Map<Long, HawkEyeScanItem> scanItemMap = new HashMap<>(); // 1 scan item per item
    poLineLabelDataMap
        .keySet()
        .forEach(
            documentLine ->
                poLineLabelDataMap
                    .get(documentLine)
                    .forEach(
                        labelData -> {
                          HawkEyeScanItem scanItem = scanItemMap.get(labelData.getItemNumber());
                          if (Objects.isNull(scanItem)) {
                            // Fresh item found, create new scan item for it and add labels
                            scanItem =
                                labelingHelperService.buildHawkEyeScanItemFromLabelDataAndPoLine(
                                    deliveryNumber, documentLine, labelData);
                            scanItemMap.put(labelData.getItemNumber(), scanItem);
                          }
                          if (StringUtils.isEmpty(scanItem.getReject())
                              && labelData.getLpnsCount() > 0) {
                            // Add labels only for conveyable freight
                            addLabelsToScanItem(
                                labelData,
                                scanItem.getExceptionLabels(),
                                scanItem.getLabels(),
                                documentLine);
                          } else {
                            // clear the exception label url set previously
                            scanItem.getExceptionLabels().setPurchaseReferenceNumber("");
                            scanItem.getExceptionLabels().setPurchaseReferenceLineNumber(null);
                            // scanItem.getExceptionLabels().setLabelData(null);
                          }
                        }));
    HawkEyeLabelDataTO hawkEyeLabelDataTO =
        HawkEyeLabelDataTO.builder()
            .defaultTCL("")
            .defaultDestination("")
            .scanItems(new ArrayList<>(scanItemMap.values()))
            .clientId(LabelingConstants.CLIENT_ID)
            .formatName(appConfig.getPreLabelFormatName())
            .deliveryNbr(deliveryNumber)
            .doorNumber(doorNumber)
            .tclTemplate(accManagedConfig.getAccPrintableZPL())
            .trailer(trailer)
            .build();

    publishACLLabelData(hawkEyeLabelDataTO, headers);
  }

  @Override
  public void publishACLLabelDataForDelivery(Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, String> pathParams =
        Collections.singletonMap(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI, pathParams)
            .toString();
    Map<DeliveryDocumentLine, List<LabelData>> poLineLabelDataMap = new HashMap<>();
    try {
      DeliveryService deliveryService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_SERVICE_KEY,
              DeliveryService.class);
      DeliveryDetails deliveryDetails = deliveryService.getDeliveryDetails(url, deliveryNumber);
      List<LabelData> labelDataForDelivery =
          labelDataService.getLabelDataByDeliveryNumberSortedBySeq(deliveryNumber);
      deliveryDetails
          .getDeliveryDocuments()
          .stream()
          .filter(
              deliveryDocument ->
                  !POStatus.CNCL.name().equals(deliveryDocument.getPurchaseReferenceStatus()))
          .forEach(
              deliveryDocument -> {
                deliveryDocument
                    .getDeliveryDocumentLines()
                    .stream()
                    .filter(
                        deliveryDocumentLine ->
                            !(POLineStatus.CANCELLED
                                    .name()
                                    .equalsIgnoreCase(
                                        deliveryDocumentLine.getPurchaseReferenceLineStatus())
                                || (!Objects.isNull(deliveryDocumentLine.getOperationalInfo())
                                    && POLineStatus.REJECTED
                                        .name()
                                        .equalsIgnoreCase(
                                            deliveryDocumentLine.getOperationalInfo().getState()))))
                    .forEach(
                        deliveryDocumentLine -> {
                          enrichDocumentLineWithPoDetails(deliveryDocumentLine, deliveryDocument);
                          List<LabelData> labelDataByPoPoLine =
                              labelDataForDelivery
                                  .stream()
                                  .filter(
                                      labelData ->
                                          labelData
                                                  .getPurchaseReferenceNumber()
                                                  .equals(
                                                      deliveryDocumentLine
                                                          .getPurchaseReferenceNumber())
                                              && labelData
                                                  .getPurchaseReferenceLineNumber()
                                                  .equals(
                                                      deliveryDocumentLine
                                                          .getPurchaseReferenceLineNumber()))
                                  .collect(Collectors.toList());
                          poLineLabelDataMap.put(deliveryDocumentLine, labelDataByPoPoLine);
                        });
              });

      publishACLLabelDataPayload(
          poLineLabelDataMap,
          deliveryNumber,
          deliveryDetails.getDoorNumber(),
          deliveryDetails.getTrailerId(),
          getMessageHeadersForLabelDataComplete(deliveryNumber, httpHeaders));
    } catch (ReceivingException receivingException) {
      LOGGER.error("Can't fetch delivery: {} details.", deliveryNumber);
    }
  }

  @Override
  protected void addLpnsToTransactionLabels(
      LabelData labelData,
      List<FormattedLabels> labels,
      DeliveryDocumentLine deliveryDocumentLine) {
    labels.add(labelingHelperService.buildHawkEyeFormattedLabel(labelData, deliveryDocumentLine));
  }
}
