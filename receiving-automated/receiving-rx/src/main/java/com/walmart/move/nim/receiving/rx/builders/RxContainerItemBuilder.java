package com.walmart.move.nim.receiving.rx.builders;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.ParseException;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.UTC_TIME_ZONE;

@Component
public class RxContainerItemBuilder {

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired
  private TenantSpecificConfigReader tenantSpecificConfigReader;

  public ContainerItem build(
      String trackingId,
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Map<String, ScannedData> scannedDataMap) {

    DeliveryDocument deliveryDocument = InstructionUtils.getDeliveryDocument(instruction);
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);

    DocumentLine item = updateInstructionRequest.getDeliveryDocumentLines().get(0);
    String dcTimeZone;
    if (Objects.nonNull(TenantContext.getFacilityNum())) {
      dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    } else {
      dcTimeZone = UTC_TIME_ZONE;
    }

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    containerItem.setInboundChannelMethod(deliveryDocumentLine.getPurchaseRefType());
    containerItem.setOutboundChannelMethod(instruction.getContainer().getOutboundChannelMethod());
    containerItem.setTotalPurchaseReferenceQty(item.getTotalPurchaseReferenceQty());
    containerItem.setPurchaseCompanyId(item.getPurchaseCompanyId());
    containerItem.setPoDeptNumber(deliveryDocumentLine.getDeptNumber());
    String baseDivCode =
        item.getBaseDivisionCode() != null
            ? item.getBaseDivisionCode()
            : ReceivingConstants.BASE_DIVISION_CODE;
    containerItem.setBaseDivisionCode(baseDivCode);
    String fRG =
        item.getFinancialReportingGroupCode() != null
            ? item.getFinancialReportingGroupCode()
            : TenantContext.getFacilityCountryCode();

    containerItem.setFinancialReportingGroupCode(fRG);

    containerItem.setOrderableQuantity(deliveryDocumentLine.getOrderableQuantity());
    containerItem.setWarehousePackQuantity(deliveryDocumentLine.getWarehousePackQuantity());
    containerItem.setPoTypeCode(deliveryDocument.getPoTypeCode());
    containerItem.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    containerItem.setDeptNumber(item.getDeptNumber());
    containerItem.setItemNumber(
        Objects.isNull(item.getItemNumber())
            ? deliveryDocumentLine.getItemNbr()
            : item.getItemNumber());
    containerItem.setVendorGS128(item.getVendorGS128());
    containerItem.setGtin(item.getGtin());
    containerItem.setVnpkQty(item.getVnpkQty());
    containerItem.setWhpkQty(item.getWhpkQty());
    containerItem.setVnpkcbqty(deliveryDocumentLine.getCube());
    containerItem.setVnpkcbuomcd(deliveryDocumentLine.getCubeUom());
    containerItem.setDescription(deliveryDocumentLine.getDescription());
    containerItem.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
    containerItem.setActualTi(deliveryDocumentLine.getPalletTie());
    containerItem.setActualHi(deliveryDocumentLine.getPalletHigh());

    containerItem.setDistributions(instruction.getContainer().getDistributions());

    containerItem.setVendorPackCost(item.getVendorPackCost());
    containerItem.setWhpkSell(item.getWhpkSell());
    try {
      containerItem.setRotateDate(
              DateUtils.parseDate(
                      (ReceivingUtils.getDCDateTime(dcTimeZone).toLocalDate().toString()),
                      RxConstants.ROTATE_DATE_FORMAT));
    } catch (ParseException e) {
      throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_ROTATE_DATE_ERROR_CODE,
              RxConstants.INVALID_ROTATE_DATE);
    }
    containerItem.setVendorGS128(item.getVendorGS128());
    containerItem.setPromoBuyInd(item.getPromoBuyInd());
    containerItem.setPackagedAsUom(appConfig.getPackagedAsUom());
    containerItem.setVendorNumber(
        item.getVendorNumber() != null ? Integer.parseInt(item.getVendorNumber()) : null);

    containerItem.setVendorNbrDeptSeq(deliveryDocument.getVendorNbrDeptSeq());
    containerItem.setVnpkWgtQty(deliveryDocumentLine.getWeight());
    containerItem.setVnpkWgtUom(deliveryDocumentLine.getWeightUom());
    if (MapUtils.isNotEmpty(scannedDataMap)) {
      try {
        ScannedData expDateScannedData = scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE);
        containerItem.setExpiryDate(
            DateUtils.parseDate(
                expDateScannedData.getValue(), ReceivingConstants.EXPIRY_DATE_FORMAT));
      } catch (ParseException e) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_SCANNED_DATA_EXPIRY_DATE,
            RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
      }

      ScannedData lotScannedData = scannedDataMap.get(ReceivingConstants.KEY_LOT);
      containerItem.setLotNumber(lotScannedData.getValue());

      ScannedData keyScannedData = scannedDataMap.get(ReceivingConstants.KEY_SERIAL);
      containerItem.setSerial(keyScannedData.getValue());

      ScannedData gtinScannedData = scannedDataMap.get(ReceivingConstants.KEY_GTIN);
      containerItem.setGtin(gtinScannedData.getValue());
    }
    return containerItem;
  }
}
