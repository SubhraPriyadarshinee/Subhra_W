package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.client.dcfin.model.DCFinPurchaseRequestBody;
import com.walmart.move.nim.receiving.core.client.dcfin.model.Purchase;
import com.walmart.move.nim.receiving.core.client.dcfin.model.PurchaseLine;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DistributionsItem;
import com.walmart.move.nim.receiving.utils.common.GdmToDCFinChannelMethodResolver;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import java.util.*;
import java.util.stream.Stream;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class DCFinServiceV2 extends DCFinService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DCFinServiceV2.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  /**
   * https://jira.walmart.com/browse/SCTNGMS-120 - Send the multiple containers to dc fin
   *
   * @param containers the list of containers
   * @param httpHeaders mandatory headers
   * @param isAsyncRestPostEnabled for Dcfin
   * @param deliveryMetaData for additional fields in DcFin
   * @param docType the document type
   */
  @ExceptionCounted(
      name = "dcFinV2ExceptionCount",
      level1 = "uwms-receiving",
      level2 = "DCFinServiceV2",
      level3 = "postReceiptUpdateToDCFin")
  public void postReceiptUpdateToDCFin(
      List<Container> containers,
      HttpHeaders httpHeaders,
      boolean isAsyncRestPostEnabled,
      DeliveryMetaData deliveryMetaData,
      String docType) {

    String baseDivisionCode = getBaseDivisionCode(containers);
    if ((!appConfig.getIsReceiptPostingEnaledForDCFin())
        || tenantSpecificConfigReader.isReceiptPostingDisabled(baseDivisionCode)) return;

    if (isSamsBaseDivisionCode(baseDivisionCode)) {
      httpHeaders.set(TENENT_FACLITYNUM, tenantSpecificConfigReader.overwriteFacilityInfo());
    }

    httpHeaders.add(ReceivingConstants.DCFIN_WMT_API_KEY, appConfig.getDcFinApiKey());
    // dc fin expects facilityCountryCode in upperCase 'US'. So making change only
    // for dcFin call
    httpHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE).toUpperCase());

    String txnId = UUID.randomUUID().toString();

    String payload = getReceiptUpdatePayloadForDCFin(containers, txnId, deliveryMetaData, docType);

    String url =
        new StringBuilder()
            .append(appConfig.getDcFinBaseUrl())
            .append(ReceivingConstants.DC_FIN_POST_RECEIPTS_V2)
            .toString();

    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);

    try {
      post(cId, txnId, url, httpHeaders, payload, isAsyncRestPostEnabled);

    } catch (ReceivingException re) {
      LOGGER.error(
          "Unable to post the receipt to DCFin [exception={}]", ExceptionUtils.getStackTrace(re));
      throw new ReceivingInternalException(
          ExceptionCodes.DCFIN_CONTAINER_ERROR, "Unable to publish containers to DCFin");
    }
  }

  private static String getBaseDivisionCode(List<Container> containers) {
    return containers
        .stream()
        .findFirst()
        .map(Container::getContainerItems)
        .map(List::stream)
        .orElse(Stream.empty())
        .findFirst()
        .map(ContainerItem::getBaseDivisionCode)
        .orElse(BASE_DIVISION_CODE);
  }

  private static boolean isSamsBaseDivisionCode(String baseDivisionCode) {
    return SAMS_BASE_DIVISION_CODE.equalsIgnoreCase(baseDivisionCode);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-120 - Send the multiple containers to dc fin
   *
   * @param containers the list of containers
   * @param txnId transaction id for purchases and containers
   * @param deliveryMetaData for additional fields in DcFin
   * @param docType the document type
   * @return receipt request body
   */
  private String getReceiptUpdatePayloadForDCFin(
      List<Container> containers, String txnId, DeliveryMetaData deliveryMetaData, String docType) {
    DCFinPurchaseRequestBody dCFinPurchaseRequestBody = new DCFinPurchaseRequestBody();
    dCFinPurchaseRequestBody.setTxnId(txnId);
    dCFinPurchaseRequestBody.setPurchase(getPurchases(containers, deliveryMetaData, docType));
    return gson.toJson(dCFinPurchaseRequestBody);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-120 - Send the multiple containers to dc fin
   *
   * @param containers the list of containers
   * @param deliveryMetaData for additional fields in DcFin
   * @param docType the document type
   * @return list of purchases
   */
  private List<Purchase> getPurchases(
      List<Container> containers, DeliveryMetaData deliveryMetaData, String docType) {
    List<Purchase> purchases = new ArrayList<>();
    boolean isSamsBaseDiv = isSamsBaseDivisionCode(getBaseDivisionCode(containers));
    Integer overwrittenFacilityNumber =
        Integer.valueOf(tenantSpecificConfigReader.overwriteFacilityInfo());
    containers.forEach(
        container -> {
          Purchase purchase = getPurchases(container, deliveryMetaData, docType);
          if (isSamsBaseDiv) {
            purchase.setOriginFacilityNum(overwrittenFacilityNumber);
            purchase
                .getLines()
                .stream()
                .map(PurchaseLine::getDistributions)
                .flatMap(Collection::stream)
                .forEach(
                    distributionsItem ->
                        distributionsItem.setDestinationNumber(
                            overwrittenFacilityNumber.toString()));
          }
          purchases.add(purchase);
        });
    return purchases;
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-120 - Send the multiple containers to dc fin
   *
   * @param container in the list of containers
   * @param deliveryMetaData for additional fields in DcFin
   * @param docType the document type
   * @return purchase for each container
   */
  private Purchase getPurchases(
      Container container, DeliveryMetaData deliveryMetaData, String docType) {
    Purchase purchase = new Purchase();

    List<PurchaseLine> lines = new ArrayList<>();
    Optional<ContainerItem> containerItem = container.getContainerItems().stream().findFirst();

    if (Objects.nonNull(container.getAsnNumber())) {
      purchase.setDocumentNum(container.getAsnNumber());
      purchase.setDocType(DocumentType.ASN.getDocType());
      purchase.setChannelMethod(
          String.valueOf(container.getContainerMiscInfo().get(ReceivingConstants.CHANNEL_METHOD)));
    } else {
      purchase.setDocumentNum(
          containerItem.isPresent() ? containerItem.get().getPurchaseReferenceNumber() : null);
      purchase.setDocType(docType);
    }

    lines.add(getLines(container));

    purchase.setDateReceived(container.getCompleteTs());
    purchase.setDeliveryNum(Long.toString(container.getDeliveryNumber()));
    purchase.setFreightBillQty(container.getContainerItems().get(0).getTotalPurchaseReferenceQty());
    purchase.setLines(lines);

    if (!Objects.isNull(deliveryMetaData)) {
      purchase.setCarrierName(deliveryMetaData.getCarrierName());
      purchase.setCarrierScacCode(deliveryMetaData.getCarrierScacCode());
      purchase.setTrailerNbr(deliveryMetaData.getTrailerNumber());
      purchase.setBillCode(deliveryMetaData.getBillCode());
    }

    Map<String, Object> containerMiscInfo = container.getContainerMiscInfo();
    if (MapUtils.isNotEmpty(containerMiscInfo)) {
      if (Objects.nonNull(containerMiscInfo.get(ReceivingConstants.PRO_DATE))) {
        purchase.setProDate((Date) containerMiscInfo.get(ReceivingConstants.PRO_DATE));
      }

      /**
       * While receiving imports freights in RDC, DC Fin expects originType, originFacilityNumber,
       * originFacilityCountryCode attributes as part of receipts message.
       */
      if (!StringUtils.isEmpty(containerMiscInfo.get(ReceivingConstants.PURCHASE_REF_LEGACY_TYPE))
          && ReceivingConstants.IMPORTS_PO_TYPES.contains(
              containerMiscInfo.get(ReceivingConstants.PURCHASE_REF_LEGACY_TYPE))) {
        if (Objects.nonNull(containerMiscInfo.get(ReceivingConstants.ORIGIN_FACILITY_NUMBER))) {
          purchase.setOriginFacilityNum(
              Integer.valueOf(
                  String.valueOf(
                      containerMiscInfo.get(ReceivingConstants.ORIGIN_FACILITY_NUMBER))));
        }
        if (Objects.nonNull(containerMiscInfo.get(ReceivingConstants.ORIGIN_TYPE))) {
          purchase.setOriginType(
              String.valueOf(containerMiscInfo.get(ReceivingConstants.ORIGIN_TYPE).toString()));
        }
        purchase.setOriginFacilityCountryCode(TenantContext.getFacilityCountryCode());
      }
    }

    return purchase;
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-120 - Send the multiple containers to dc fin
   *
   * @param container in the list of containers
   * @return purchase line
   */
  private PurchaseLine getLines(Container container) {
    PurchaseLine linesItem = new PurchaseLine();
    List<DistributionsItem> distributionsItems = new ArrayList<>();

    ContainerItem content = container.getContainerItems().get(0);
    linesItem.setDocumentLineNo(content.getPurchaseReferenceLineNumber().toString());
    linesItem.setItemNumber(content.getItemNumber().intValue());
    linesItem.setSellerId(content.getSellerId());
    linesItem.setContainerId(container.getTrackingId());
    linesItem.setPrimaryQty(content.getQuantity());
    linesItem.setLineQtyUOM(ReceivingConstants.Uom.EACHES);
    linesItem.setWarehousePackEachQty(content.getWhpkQty());
    linesItem.setVendorPackEachQty(content.getVnpkQty());
    linesItem.setInboundChannelMethod(
        GdmToDCFinChannelMethodResolver.getDCFinChannelMethod(content.getInboundChannelMethod()));
    linesItem.setBaseDivCode(
        ObjectUtils.isEmpty(content.getBaseDivisionCode())
            ? BASE_DIVISION_CODE
            : content.getBaseDivisionCode());
    linesItem.setFinancialReportGrpCode(
        StringUtils.isEmpty(content.getFinancialReportingGroupCode())
            ? "US"
            : content.getFinancialReportingGroupCode());
    linesItem.setFreightBillQty(content.getTotalPurchaseReferenceQty());
    linesItem.setSecondaryQty(content.getVnpkWgtQty());
    /*
     * As asked by DcFin to pass LB/ZA instead of LB GDM is providing LB. So as per
     * discussion we will Hard code it as of now.
     */
    linesItem.setSecondaryQtyUOM(ReceivingConstants.Uom.DCFIN_LB_ZA);
    content
        .getDistributions()
        .stream()
        .forEach(
            distribution -> distributionsItems.add(getLineDistribution(distribution, container)));
    linesItem.setDistributions(distributionsItems);
    return linesItem;
  }
}
