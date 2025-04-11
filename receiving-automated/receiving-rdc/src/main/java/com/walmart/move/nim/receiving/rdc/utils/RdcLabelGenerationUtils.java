package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.HAZMAT_CODE_H;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Facility;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.label.*;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymLabelType;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RdcLabelGenerationUtils {
  private static final Gson gson = new Gson();
  private static final Logger logger = LoggerFactory.getLogger(RdcLabelGenerationUtils.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  /**
   * This method constructs label payload to send to Hawkeye
   *
   * @param deliveryDocument
   * @param labelDataList
   * @param rejectReason
   * @return ACLLabelDataTO
   */
  public ACLLabelDataTO buildLabelDownloadForHawkeye(
      DeliveryDocument deliveryDocument, List<LabelData> labelDataList, RejectReason rejectReason) {
    logger.info(
        "Constructing label payload to send to Hawkeye for deliveryNumber: {} and itemNumber: {}",
        deliveryDocument.getDeliveryNumber(),
        deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr());
    return ACLLabelDataTO.builder()
        .groupNumber(String.valueOf(deliveryDocument.getDeliveryNumber()))
        .scanItems(buildScanItems(labelDataList, deliveryDocument, rejectReason))
        .build();
  }

  private List<ScanItem> buildScanItems(
      List<LabelData> labelDataList, DeliveryDocument deliveryDocument, RejectReason rejectReason) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ScanItem item =
        ScanItem.builder()
            .item(deliveryDocumentLine.getItemNbr())
            .itemDesc(deliveryDocumentLine.getDescription())
            .groupType(GroupType.RCV_DA.toString())
            .possibleUPC(getPossibleUPC(deliveryDocumentLine))
            .reject(Objects.nonNull(rejectReason) ? rejectReason.getRejectCode() : null)
            .labels(
                Boolean.TRUE.equals(isInvalidItemForAutomation(rejectReason, deliveryDocumentLine))
                    ? new ArrayList<>()
                    : buildFormattedLabels(labelDataList, deliveryDocument))
            .build();

    return Collections.singletonList(item);
  }

  private List<FormattedLabels> buildFormattedLabels(
      List<LabelData> labelDataList, DeliveryDocument deliveryDocument) {
    List<FormattedLabels> labels = new ArrayList<>();
    labelDataList = RdcUtils.filterLabelDataWith25DigitLpns(labelDataList);
    labelDataList.forEach(
        labelData -> {
          FormattedLabels formattedLabels =
              buildFormattedLabelFromLabelData(labelData, deliveryDocument);
          labels.add(formattedLabels);
        });
    return labels;
  }

  private FormattedLabels buildFormattedLabelFromLabelData(
      LabelData labelData, DeliveryDocument deliveryDocument) {
    // TODO: Add labelType for Sorter/Athena integration
    FormattedLabels formattedLabels =
        FormattedLabels.builder()
            .seqNo(labelData.getLabelSequenceNbr().toString())
            .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
            .purchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber())
            .lpns(Collections.singletonList(labelData.getTrackingId()))
            .labelData(gson.toJson(buildSymLabelData(labelData, deliveryDocument)))
            .build();

    if (Objects.nonNull(labelData.getAllocation())) {
      formattedLabels.setDestination(getDestination(labelData));
    }
    return formattedLabels;
  }

  private SymLabelData buildSymLabelData(LabelData labelData, DeliveryDocument deliveryDocument) {

    boolean isBreakPack = false;
    int cpQty = 0;
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    isBreakPack =
        deliveryDocumentLine.getPackType().equals("B")
            || (!deliveryDocumentLine
                .getWarehousePack()
                .equals(deliveryDocumentLine.getVendorPack()));
    cpQty =
        isBreakPack
            ? deliveryDocumentLine.getVendorPack() / deliveryDocumentLine.getWarehousePack()
            : deliveryDocumentLine.getWarehousePack();

    SymLabelData symLabelData =
        SymLabelData.builder()
            .labelTagType(getLabelTagType(deliveryDocumentLine.getPurchaseRefType(), isBreakPack))
            .freightType(getFreightType(deliveryDocumentLine.getPurchaseRefType(), isBreakPack))
            .po(String.valueOf(labelData.getPurchaseReferenceNumber()))
            .poLine(String.valueOf(labelData.getPurchaseReferenceLineNumber()))
            .poCreateDate(
                LocalDate.now()
                    .format(DateTimeFormatter.ofPattern(RdcConstants.SYM_LABEL_DATE_FORMAT)))
            .itemNumber(deliveryDocumentLine.getItemNbr())
            .labelDate(
                LocalDate.now()
                    .format(DateTimeFormatter.ofPattern(RdcConstants.SYM_LABEL_DATE_FORMAT)))
            .holdStatus(SymInventoryStatus.HOLD.getStatus())
            .isShipLabel(isShipLabel(deliveryDocumentLine.getPurchaseRefType(), isBreakPack))
            .shipLabelData(
                ShipLabelData.builder()
                    .dataMatrix(
                        createTwoDDataMatrixValue(
                            String.valueOf(deliveryDocument.getDeliveryNumber()),
                            labelData.getItemNumber().toString()))
                    .item(deliveryDocumentLine.getItemNbr())
                    .upcBarcode(deliveryDocumentLine.getCaseUpc())
                    .itemDesc(deliveryDocumentLine.getDescription())
                    .poevent(deliveryDocumentLine.getEvent())
                    .cpQty(cpQty)
                    .hazmatCode(
                        Boolean.TRUE.equals(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine))
                            ? HAZMAT_CODE_H
                            : StringUtils.EMPTY)
                    .dept(deliveryDocumentLine.getDeptNumber())
                    .po(labelData.getPurchaseReferenceNumber())
                    .poLine(String.valueOf(labelData.getPurchaseReferenceLineNumber()))
                    .poCode(
                        LabelGenerator.getPoCodeByPoType(
                            Integer.parseInt(deliveryDocument.getPurchaseReferenceLegacyType())))
                    .build())
            .build();

    if (Objects.nonNull(labelData.getAllocation())) {
      symLabelData.setOriginStore(getOriginStore(labelData));
      symLabelData.getShipLabelData().setStoreZone(getStoreZone(labelData));
    }
    return symLabelData;
  }

  public String getStoreZone(LabelData labelData) {
    return labelData.getAllocation().getContainer().getDistributions().get(0).getItem().getZone();
  }

  public String getFreightType(String purchaseRefType, boolean isBreakPack) {
    if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType)) {
      return isBreakPack ? SymFreightType.BRPK.toString() : SymFreightType.DA.toString();
    }

    if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType)) {
      return SymFreightType.SSTK.toString();
    }
    return SymFreightType.DSDC.toString();
  }

  public Boolean isShipLabel(String purchaseRefType, boolean isBreakPack) {
    return ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType) && isBreakPack
        || ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType);
  }

  public String getLabelTagType(String purchaseRefType, boolean isBreakPack) {
    if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType)) {
      return isBreakPack ? SymLabelType.SHIPPING.toString() : SymLabelType.ROUTING.toString();
    }

    if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType)) {
      return SymLabelType.PALLET.toString();
    }
    if (SymFreightType.DSDC.toString().equalsIgnoreCase(purchaseRefType)) {
      return SymLabelType.SHIPPING.toString();
    }
    return SymLabelType.ROUTING.toString();
  }

  public Integer getOriginStore(LabelData data) {
    return Integer.valueOf(data.getAllocation().getContainer().getFinalDestination().getBuNumber());
  }

  public String createTwoDDataMatrixValue(String deliveryNumber, String itemNumber) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED,
        false)) {
      return StringUtils.join(deliveryNumber, "Y", itemNumber);
    }
    return null;
  }

  public DestinationData getDestination(LabelData labelData) {
    Facility destination = labelData.getAllocation().getContainer().getFinalDestination();
    InstructionDownloadItemDTO item =
        labelData.getAllocation().getContainer().getDistributions().get(0).getItem();
    return DestinationData.builder()
        .buNumber(Integer.valueOf(destination.getBuNumber()))
        .countryCode(destination.getCountryCode())
        .aisle(item.getAisle())
        .zone(item.getZone())
        .pickBatch(item.getPickBatch())
        .printBatch(item.getPrintBatch())
        .build();
  }

  public PossibleUPC getPossibleUPC(DeliveryDocumentLine deliveryDocumentLine) {
    return PossibleUPC.builder()
        .sscc(null)
        .catalogGTIN(
            Objects.nonNull(deliveryDocumentLine.getVendorUPC())
                ? deliveryDocumentLine.getVendorUPC()
                : deliveryDocumentLine.getCatalogGTIN())
        .consumableGTIN(
            Objects.nonNull(deliveryDocumentLine.getItemUpc())
                ? deliveryDocumentLine.getItemUpc()
                : deliveryDocumentLine.getConsumableGTIN())
        .orderableGTIN(
            Objects.nonNull(deliveryDocumentLine.getCaseUpc())
                ? deliveryDocumentLine.getCaseUpc()
                : deliveryDocumentLine.getOrderableGTIN())
        .parsedUPC(
            Objects.nonNull(deliveryDocumentLine.getCaseUpc())
                    && deliveryDocumentLine.getCaseUpc().length() == CASE_UPC_LENGTH
                ? deliveryDocumentLine
                    .getCaseUpc()
                    .substring(CASE_UPC_STARTING_INDEX, CASE_UPC_ENDING_INDEX)
                    .replaceFirst(PARSED_UPC_REGEX_PATTERN, "")
                : null)
        .build();
  }

  private Boolean isInvalidItemForAutomation(
      RejectReason rejectReason, DeliveryDocumentLine deliveryDocumentLine) {
    return (Objects.nonNull(rejectReason)
            && (rejectReason.equals(RejectReason.RDC_NONCON)
                || rejectReason.equals(RejectReason.RDC_SSTK)
                || rejectReason.equals(RejectReason.X_BLOCK)
                || rejectReason.equals(RejectReason.RDC_MASTER_PACK)))
        || !hasValidItemPackTypeAndHandlingCode(deliveryDocumentLine);
  }

  private Boolean hasValidItemPackTypeAndHandlingCode(DeliveryDocumentLine deliveryDocumentLine) {
    return appConfig
        .getValidItemPackTypeHandlingCodeCombinations()
        .contains(
            String.join(
                "",
                deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(),
                deliveryDocumentLine.getAdditionalInfo().getHandlingCode()));
  }

  public PossibleUPC getPossibleUPCv2(
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine) {
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

  public boolean isSSTKPilotDeliveryEnabled() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED,
        false);
  }

  public boolean isAtlasSSTKPilotDelivery(Long deliveryNumber) {
    return !rdcManagedConfig.getAtlasSSTKPilotDeliveries().isEmpty()
        && rdcManagedConfig.getAtlasSSTKPilotDeliveries().contains(String.valueOf(deliveryNumber));
  }

  public List<LabelDownloadEvent> filterLabelDownloadEventWithPilotDelivery(
      List<LabelDownloadEvent> labelDownloadEventList) {
    if (isSSTKPilotDeliveryEnabled()) {
      labelDownloadEventList =
          labelDownloadEventList
              .stream()
              .filter(
                  labelDownloadEvent ->
                      isAtlasSSTKPilotDelivery(labelDownloadEvent.getDeliveryNumber()))
              .collect(Collectors.toList());
    }
    return labelDownloadEventList;
  }
}
