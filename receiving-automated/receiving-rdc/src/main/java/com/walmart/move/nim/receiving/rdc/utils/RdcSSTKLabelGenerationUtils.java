package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.HAZMAT_CODE_H;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.model.LabelDownloadEventMiscInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.rdc.constants.GroupType;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.model.symbotic.ShipLabelData;
import com.walmart.move.nim.receiving.rdc.model.symbotic.SymInventoryStatus;
import com.walmart.move.nim.receiving.rdc.model.symbotic.SymLabelData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class RdcSSTKLabelGenerationUtils {
  private static final Gson gson = new Gson();
  private static final Logger logger = LoggerFactory.getLogger(RdcSSTKLabelGenerationUtils.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Autowired private RdcLabelGenerationUtils rdcLabelGenerationUtils;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private RdcInstructionUtils rdcInstructionUtils;

  /**
   * This method constructs label payload to send to Hawkeye for SSTK
   *
   * @param deliveryDocumentLine
   * @param labelDataList
   * @param rejectReason
   * @param deliveryNumber
   * @return ACLLabelDataTO
   */
  public ACLLabelDataTO buildLabelDownloadPayloadForSSTK(
      DeliveryDocumentLine deliveryDocumentLine,
      List<LabelData> labelDataList,
      RejectReason rejectReason,
      Long deliveryNumber) {
    logger.info(
        "Constructing label payload to send to Hawkeye for deliveryNumber: {} and itemNumber: {}",
        deliveryNumber,
        deliveryDocumentLine.getItemNbr());
    return ACLLabelDataTO.builder()
        .groupNumber(String.valueOf(deliveryNumber))
        .scanItems(
            buildScanItemsForSSTK(
                labelDataList, deliveryDocumentLine, rejectReason, deliveryNumber))
        .build();
  }

  private List<ScanItem> buildScanItemsForSSTK(
      List<LabelData> labelDataList,
      DeliveryDocumentLine deliveryDocumentLine,
      RejectReason rejectReason,
      Long deliveryNumber) {
    ScanItem item =
        ScanItem.builder()
            .item(deliveryDocumentLine.getItemNbr())
            .itemDesc(deliveryDocumentLine.getItemDescription1())
            .groupType(GroupType.RCV_DA.toString())
            .possibleUPC(getPossibleUPCForSSTK(deliveryDocumentLine))
            .reject(Objects.nonNull(rejectReason) ? rejectReason.getRejectCode() : null)
            .labels(
                Boolean.TRUE.equals(isSSTKAutomationInEligibleItem(rejectReason))
                    ? new ArrayList<>()
                    : buildFormattedLabelsForSSTK(
                        labelDataList, deliveryDocumentLine, deliveryNumber))
            .build();

    return Collections.singletonList(item);
  }

  public PossibleUPC getPossibleUPCForSSTK(DeliveryDocumentLine deliveryDocumentLine) {
    return PossibleUPC.builder()
        .sscc(null)
        .catalogGTIN(deliveryDocumentLine.getVendorUPC())
        .consumableGTIN(deliveryDocumentLine.getItemUPC())
        .orderableGTIN(deliveryDocumentLine.getCaseUPC())
        .parsedUPC(
            Objects.nonNull(deliveryDocumentLine.getCaseUPC())
                    && deliveryDocumentLine.getCaseUPC().length() == CASE_UPC_LENGTH
                ? deliveryDocumentLine
                    .getCaseUPC()
                    .substring(CASE_UPC_STARTING_INDEX, CASE_UPC_ENDING_INDEX)
                    .replaceFirst(PARSED_UPC_REGEX_PATTERN, "")
                : null)
        .build();
  }

  private Boolean isSSTKAutomationInEligibleItem(RejectReason rejectReason) {
    return (Objects.nonNull(rejectReason)
        && (rejectReason.equals(RejectReason.RDC_SSTK)
            || rejectReason.equals(RejectReason.X_BLOCK)));
  }

  private List<FormattedLabels> buildFormattedLabelsForSSTK(
      List<LabelData> labelDataList,
      DeliveryDocumentLine deliveryDocumentLine,
      Long deliveryNumber) {
    List<FormattedLabels> labels = new ArrayList<>();
    if (!CollectionUtils.isEmpty(labelDataList)) {
      SymLabelData symLabelData =
          buildSymLabelDataForSSTK(labelDataList.get(0), deliveryDocumentLine, deliveryNumber);
      labelDataList.forEach(
          labelData -> {
            FormattedLabels formattedLabels =
                buildFormattedLabelFromLabelDataForSSTK(labelData, symLabelData);
            labels.add(formattedLabels);
          });
    }
    return labels;
  }

  private FormattedLabels buildFormattedLabelFromLabelDataForSSTK(
      LabelData labelData, SymLabelData symLabelData) {
    return FormattedLabels.builder()
        .seqNo(labelData.getLabelSequenceNbr().toString())
        .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
        .purchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber())
        .lpns(Collections.singletonList(labelData.getTrackingId()))
        .labelData(gson.toJson(symLabelData))
        .build();
  }

  private SymLabelData buildSymLabelDataForSSTK(
      LabelData labelData, DeliveryDocumentLine deliveryDocumentLine, Long deliveryNumber) {

    boolean isBreakPack;
    int cpQty;
    isBreakPack = (!deliveryDocumentLine.getWhpkQty().equals(deliveryDocumentLine.getVnpkQty()));

    cpQty =
        isBreakPack
            ? deliveryDocumentLine.getVnpkQty() / deliveryDocumentLine.getWhpkQty()
            : deliveryDocumentLine.getWhpkQty();

    return SymLabelData.builder()
        .labelTagType(
            rdcLabelGenerationUtils.getLabelTagType(
                deliveryDocumentLine.getPurchaseRefType(), isBreakPack))
        .freightType(
            rdcLabelGenerationUtils.getFreightType(
                deliveryDocumentLine.getPurchaseRefType(), isBreakPack))
        .po(String.valueOf(labelData.getPurchaseReferenceNumber()))
        .poLine(String.valueOf(labelData.getPurchaseReferenceLineNumber()))
        .poCreateDate(
            LocalDate.now().format(DateTimeFormatter.ofPattern(RdcConstants.SYM_LABEL_DATE_FORMAT)))
        .itemNumber(deliveryDocumentLine.getItemNbr())
        .labelDate(
            LocalDate.now().format(DateTimeFormatter.ofPattern(RdcConstants.SYM_LABEL_DATE_FORMAT)))
        .holdStatus(SymInventoryStatus.HOLD.getStatus())
        .isShipLabel(
            rdcLabelGenerationUtils.isShipLabel(
                deliveryDocumentLine.getPurchaseRefType(), isBreakPack))
        .shipLabelData(
            ShipLabelData.builder()
                .dataMatrix(
                    rdcLabelGenerationUtils.createTwoDDataMatrixValue(
                        String.valueOf(deliveryNumber), labelData.getItemNumber().toString()))
                .item(deliveryDocumentLine.getItemNbr())
                .upcBarcode(deliveryDocumentLine.getCaseUPC())
                .itemDesc(deliveryDocumentLine.getItemDescription1())
                .poevent(deliveryDocumentLine.getEvent())
                .cpQty(cpQty)
                .hazmatCode(
                    Boolean.TRUE.equals(
                            rdcSSTKInstructionUtils.isHazmatItemForSSTK(deliveryDocumentLine))
                        ? HAZMAT_CODE_H
                        : StringUtils.EMPTY)
                .dept(deliveryDocumentLine.getDepartment())
                .po(labelData.getPurchaseReferenceNumber())
                .poLine(String.valueOf(labelData.getPurchaseReferenceLineNumber()))
                .poCode(
                    LabelGenerator.getPoCodeByPoType(
                        (deliveryDocumentLine.getPurchaseReferenceLegacyType())))
                .build())
        .build();
  }

  /**
   * This method returns true if the LabelDownloadEvent is SSTK LabelType
   *
   * @param labelDownloadEvent
   * @return Boolean
   */
  public Boolean isSSTKLabelDownloadEvent(LabelDownloadEvent labelDownloadEvent) {
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo =
        gson.fromJson(labelDownloadEvent.getMiscInfo(), LabelDownloadEventMiscInfo.class);
    String labelType =
        Objects.nonNull(labelDownloadEventMiscInfo)
            ? labelDownloadEventMiscInfo.getLabelType()
            : Strings.EMPTY;
    return ReceivingConstants.PURCHASE_REF_TYPE_SSTK.equalsIgnoreCase(labelType);
  }

  public DeliveryDetails fetchDeliveryDetails(String url, Long deliveryNumber) {
    DeliveryDetails deliveryDetails = null;
    try {
      logger.debug("Fetching delivery info for URL : {}", url);
      DeliveryService deliveryService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_SERVICE_KEY,
              DeliveryService.class);
      deliveryDetails = deliveryService.getDeliveryDetails(url, deliveryNumber);
    } catch (ReceivingException receivingException) {
      logger.info(
          "Can't fetch delivery: {} details. Continuing to check and persist event",
          deliveryNumber);
    }
    return deliveryDetails;
  }
  /**
   * Validate asrsAlignment to check if item is Sym eligible
   *
   * @param asrsAlignment
   * @return
   */
  public Boolean isAsrsAlignmentSymEligible(String asrsAlignment) {
    return Objects.nonNull(asrsAlignment)
        && !org.apache.commons.collections4.CollectionUtils.isEmpty(
            appConfig.getValidSymAsrsAlignmentValues())
        && appConfig.getValidSymAsrsAlignmentValues().contains(asrsAlignment);
  }
  /**
   * Builds a map for purchaseReferenceNumber and a set of deliveries for that PO/Item
   *
   * @param labelDownloadEventList
   * @return poToDeliveryMap
   */
  public Map<String, Set<Long>> buildPoDeliveryMap(
      List<LabelDownloadEvent> labelDownloadEventList) {
    return labelDownloadEventList
        .stream()
        .collect(
            Collectors.groupingBy(
                LabelDownloadEvent::getPurchaseReferenceNumber,
                Collectors.mapping(LabelDownloadEvent::getDeliveryNumber, Collectors.toSet())));
  }
}
