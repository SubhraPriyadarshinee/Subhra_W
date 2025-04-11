package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.checkViolations;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_TI_HI;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.PO_POL_CANCELLED_ERROR;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.utils.constants.LabelCode;
import com.walmart.move.nim.receiving.utils.constants.LithiumIonType;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class DeliveryDocumentHelper {

  private static final Logger LOG = LoggerFactory.getLogger(DeliveryDocumentHelper.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "itemCategoryRuleSet")
  private RuleSet itemCategoryRuleSet;

  @Autowired private LithiumIonLimitedQtyRule lithiumIonLimitedQtyRule;
  @Autowired private LimitedQtyRule limitedQtyRule;
  @Autowired private LithiumIonRule lithiumIonRule;
  @Autowired private Gson gson;
  /**
   * Update deliveryDocumentLine based on the source data
   *
   * @param deliveryDocuments
   * @return list of delivery documents
   */
  public List<DeliveryDocument> updateDeliveryDocuments(List<DeliveryDocument> deliveryDocuments)
      throws ReceivingException {
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        updateDeliveryDocumentLine(deliveryDocumentLine);
      }
    }
    return deliveryDocuments;
  }

  public void updateDeliveryDocumentLine(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    updateCommonFieldsInDeliveryDocLine(deliveryDocumentLine);

    // Check WarehouseRotationTypeCode
    checkAndSetFirstExpiryFirstOut(deliveryDocumentLine);
  }

  public Boolean updateVendorCompliance(DeliveryDocumentLine deliveryDocumentLine) {
    boolean isVendorComplianceRequired = false;
    if (CollectionUtils.isEmpty(deliveryDocumentLine.getTransportationModes())) {
      return Boolean.FALSE;
    }
    boolean isLithiumIonVerificationRequired = false;
    boolean isLimitedQtyVerificationRequired = false;
    boolean isLithiumAndLimitedQtyVerificationRequired =
        lithiumIonLimitedQtyRule.validateRule(deliveryDocumentLine);
    if (isLithiumAndLimitedQtyVerificationRequired) {
      isLithiumIonVerificationRequired = true;
      isLimitedQtyVerificationRequired = true;
    } else {
      isLithiumIonVerificationRequired = lithiumIonRule.validateRule(deliveryDocumentLine);
      isLimitedQtyVerificationRequired = limitedQtyRule.validateRule(deliveryDocumentLine);
    }
    if (isLithiumIonVerificationRequired || isLimitedQtyVerificationRequired) {
      LOG.info(
          "The PO:{} and POL:{} contains either lithium or limitedQty item",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      isVendorComplianceRequired = true;
      deliveryDocumentLine.setLimitedQtyVerificationRequired(isLimitedQtyVerificationRequired);
      deliveryDocumentLine.setLithiumIonVerificationRequired(isLithiumIonVerificationRequired);
      if (deliveryDocumentLine.isLithiumIonVerificationRequired()) {
        // Deriving labelTypeCode from pkgInstruction value
        String labelTypeCode =
            ReceivingUtils.getLabelTypeCode(
                deliveryDocumentLine.getTransportationModes().get(0).getPkgInstruction());
        LOG.info(
            "PO:{} and POL:{} has labelTypeCode:{}",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            labelTypeCode);
        deliveryDocumentLine.setLabelTypeCode(labelTypeCode);
      }
    }
    return isVendorComplianceRequired;
  }

  private void checkAndSetFirstExpiryFirstOut(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    if (nonNull(deliveryDocumentLine.getAdditionalInfo())) {
      ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();

      if (nonNull(additionalInfo.getWarehouseRotationTypeCode())) {
        deliveryDocumentLine.setFirstExpiryFirstOut(
            isFirstExpiryFirstOut(additionalInfo.getWarehouseRotationTypeCode()));
      }

      // Validate the deliveryDocumentLine
      if (Boolean.TRUE.equals(deliveryDocumentLine.getFirstExpiryFirstOut())
          && Objects.isNull(additionalInfo.getWarehouseMinLifeRemainingToReceive())) {
        String errorMessage =
            format(ReceivingException.INVALID_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr());
        LOG.error(errorMessage);
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_ITEM_ERROR_CODE,
            ReceivingException.INVALID_ITEM_ERROR_HEADER);
      }
    }
  }

  public void updateCommonFieldsInDeliveryDocLine(DeliveryDocumentLine deliveryDocumentLine) {
    // Assign item UPC as GTIN
    deliveryDocumentLine.setGtin(
        !StringUtils.isBlank(deliveryDocumentLine.getItemUpc())
            ? deliveryDocumentLine.getItemUpc()
            : deliveryDocumentLine.getCaseUpc());

    // Check Lithium-Ion
    deliveryDocumentLine.setItemType(getItemType(deliveryDocumentLine.getItemType()));

    // Set isHazmat flag based on transportationModes
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED)) {
      deliveryDocumentLine.setIsHazmat(InstructionUtils.isHazmatItem(deliveryDocumentLine));
    }
  }

  /**
   * Identify an item's rotation type - FIFO(FirstInFirstOut)(Default), FEFO(FirstExpiryFirstOut)
   *
   * @param warehouseRotationTypeCode Values:
   *     <pre>
   * 1:Normal - Rotate by Received Date
   * 2:Strict - Rotate by Received Date
   * 3:Strict - Rotate by Sell By Date : capture for Expiry date
   * 4:Strict - Rotate by Sequence Number : capture "PACK Date" or "MFG Date"
   * For Rotation Code Type 4. warehouseRotationTypeCode for -ve use case
   * This should prompt a user to capture the "PACK Date" or "MFG Date" on mobile.
   * We will also need to ensure we are doing correct validation for close date on this negative  days.
   * If min receiving days is negative then date must be between (current - negative min receiving days) and current
   * </pre>
   *
   * @return Boolean
   * @throws ReceivingException
   */
  public Boolean isFirstExpiryFirstOut(String warehouseRotationTypeCode) throws ReceivingException {
    if (tenantSpecificConfigReader.getProcessExpiry() && warehouseRotationTypeCode != null) {
      if ("3".equalsIgnoreCase(warehouseRotationTypeCode)
          || (tenantSpecificConfigReader.getConfiguredFeatureFlag(FEFO_FOR_WRTC4_ENABLED)
              && "4".equalsIgnoreCase(warehouseRotationTypeCode))) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  /**
   * Check for LithiumIon to ensure the case labeled properly
   *
   * @param typeCode
   * @return String itemType
   */
  public String getItemType(String typeCode) {
    if (typeCode != null) {
      if (typeCode.equalsIgnoreCase(LabelCode.UN3090.getValue())
          || typeCode.equalsIgnoreCase(LabelCode.UN3091.getValue())) {
        return LithiumIonType.METAL.getValue();
      } else if (typeCode.equalsIgnoreCase(LabelCode.UN3480.getValue())
          || typeCode.equalsIgnoreCase(LabelCode.UN3481.getValue())) {
        return LithiumIonType.ION.getValue();
      }
    }
    return null;
  }

  public boolean isTrailerFullyDaCon(DeliveryDetails deliveryDetails) {
    return deliveryDetails
        .getDeliveryDocuments()
        .stream()
        // filter out cancelled POs. No need to check these
        .filter(
            deliveryDocument ->
                !POStatus.CNCL.name().equals(deliveryDocument.getPurchaseReferenceStatus()))
        .allMatch(
            deliveryDocument ->
                deliveryDocument
                    .getDeliveryDocumentLines()
                    .stream()
                    // filter out cancelled and rejected lines, these can be SSTK or regulated items
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
                    .allMatch(
                        deliveryDocumentLine ->
                            // Check vendor compliance for all items
                            !(itemCategoryRuleSet.validateRuleSet(
                                    docLineMapper(deliveryDocumentLine)))
                                && InstructionUtils.isDAConFreight(
                                    deliveryDocumentLine.getIsConveyable(),
                                    deliveryDocumentLine.getPurchaseRefType(),
                                    deliveryDocumentLine.getActiveChannelMethods())));
  }

  public com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine docLineMapper(
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine
          sourceDeliveryDocumentLine) {
    if (sourceDeliveryDocumentLine == null) {
      return null;
    }
    com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine
        destinationDeliveryDocumentLine1 =
            new com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine();

    destinationDeliveryDocumentLine1.setHazmatVerifiedOn(
        sourceDeliveryDocumentLine.getHazmatVerifiedOn());
    destinationDeliveryDocumentLine1.setLimitedQtyVerifiedOn(
        sourceDeliveryDocumentLine.getLimitedQtyVerifiedOn());
    destinationDeliveryDocumentLine1.setLithiumIonVerifiedOn(
        sourceDeliveryDocumentLine.getLithiumIonVerifiedOn());
    List<TransportationModes> transportationModes =
        sourceDeliveryDocumentLine.getTransportationModes();
    if (transportationModes != null) {
      destinationDeliveryDocumentLine1.setTransportationModes(new ArrayList<>(transportationModes));
    }
    return destinationDeliveryDocumentLine1;
  }

  public String getUrlForFetchingDelivery(Long deliveryNumber) {
    Map<String, String> pathParams =
        Collections.singletonMap(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    return ReceivingUtils.replacePathParams(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI, pathParams)
        .toString();
  }

  /**
   * Validate Delivery Document's Po Status if good to create instruction. Configure list of invalid
   * status values in CCM or pass empty value to disable the feature
   *
   * @param deliveryDocument
   * @throws ReceivingException
   */
  public void validatePoStatus(DeliveryDocument deliveryDocument) throws ReceivingException {
    final String poStatus =
        deliveryDocument != null ? deliveryDocument.getPurchaseReferenceStatus() : null;
    if (StringUtils.contains(
        tenantSpecificConfigReader.getCcmValue(
            getFacilityNum(),
            INVALID_PO_STATUS,
            "CNCL,HISTORY"), // POStatus.CNCL HISTORY, override in ccm
        poStatus)) {
      final String poNumber = deliveryDocument.getPurchaseReferenceNumber();
      LOG.error("can't create instruction as PO={} status={}", poNumber, poStatus);
      GdmError gdmPoLineCancelled = GdmErrorCode.getErrorValue(PO_POL_CANCELLED_ERROR);
      throw new ReceivingException(
          format(gdmPoLineCancelled.getErrorMessage(), poNumber, EMPTY_STRING),
          INTERNAL_SERVER_ERROR,
          gdmPoLineCancelled.getErrorCode(),
          gdmPoLineCancelled.getErrorHeader());
    }
  }

  /**
   * Validate Delivery Document's Po Line Status if good to create instruction. Configure list of
   * invalid status values in CCM or pass empty value to disable the feature
   *
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public void validatePoLineStatus(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    final String poLineStatus = deliveryDocumentLine.getPurchaseReferenceLineStatus();
    if (StringUtils.contains(
        tenantSpecificConfigReader.getCcmValue(
            getFacilityNum(), INVALID_PO_LINE_STATUS, POLineStatus.CANCELLED.name()),
        poLineStatus)) {
      LOG.error(
          "create instruction failed as PO={}, PoLine-{} status={}",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber(),
          poLineStatus);
      GdmError gdmPoLineCancelled = GdmErrorCode.getErrorValue(PO_POL_CANCELLED_ERROR);
      throw new ReceivingException(
          format(
              gdmPoLineCancelled.getErrorMessage(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber()),
          INTERNAL_SERVER_ERROR,
          gdmPoLineCancelled.getErrorCode(),
          gdmPoLineCancelled.getErrorHeader());
    }
  }

  public boolean isAtlasConvertedItemInFirstDocFirstLine(
      List<DeliveryDocument> deliveryDocumentList) {
    final ItemData additionalInfo =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo();
    if (nonNull(additionalInfo)) {
      return additionalInfo.isAtlasConvertedItem();
    }
    return false;
  }

  /**
   * Validate Ti Hi for 0 values. Ti or Hi can't be zero or null value
   *
   * @param deliveryDocumentLine
   */
  public void validateTiHi(DeliveryDocumentLine deliveryDocumentLine) {
    final Integer ti = deliveryDocumentLine.getPalletTie();
    final Integer hi = deliveryDocumentLine.getPalletHigh();
    if (allNotNull(ti, hi) && ti > 0 && hi > 0) {
      return;
    }
    InstructionError invalidTiHi = InstructionErrorCode.getErrorValue(INVALID_TI_HI);
    final String errorMessage = invalidTiHi.getErrorMessage();
    final String errMsg = format(errorMessage, ti, hi);
    LOG.info(errMsg);
    throw new ReceivingBadDataException(invalidTiHi.getErrorCode(), errMsg);
  }

  public void validateDeliveryDocument(DeliveryDocument deliveryDocument)
      throws ReceivingException {
    validatePoStatus(deliveryDocument);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_KAFKA_RECEIPTS_DC_FIN_VALIDATE_ENABLED, false))
      checkViolations(deliveryDocument);
  }
}
