package com.walmart.move.nim.receiving.witron.publisher;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.isAtlasConvertedItem;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DC_FIN_POST_RECEIPTS_V2;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DELIM_DASH;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SPLUNK_ALERT;
import static java.util.Objects.isNull;

import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DCFinPurchaseRequestBody;
import com.walmart.move.nim.receiving.core.client.dcfin.model.Purchase;
import com.walmart.move.nim.receiving.core.client.dcfin.model.PurchaseLine;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.service.DCFinService;
import com.walmart.move.nim.receiving.utils.common.GdmToDCFinChannelMethodResolver;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.service.DeliveryCacheServiceInMemoryImpl;
import com.walmart.move.nim.receiving.witron.service.DeliveryCacheValue;
import io.strati.libs.commons.lang3.exception.ExceptionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class WitronDCFinServiceImpl extends DCFinService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WitronDCFinServiceImpl.class);
  @Autowired private DeliveryCacheServiceInMemoryImpl deliveryCache;
  @Autowired private GDCFlagReader gdcFlagReader;

  /**
   * Instead of this use below method
   *
   * <pre>postReceiptsToDCFin(Container container, HttpHeaders httpHeaders, Integer txId)</pre>
   *
   * @param container
   * @param httpHeaders
   */
  @Override
  @Deprecated
  public void postReceiptsToDCFin(
      Container container, HttpHeaders httpHeaders, boolean isAsyncRestPostEnabled) {
    LOGGER.error("No implementation for GDC");
  }

  /**
   * This method posts receipts to DC Fin POST Purchase Data V2 API
   *
   * @param container
   * @param httpHeaders
   * @param txId to add uniqueness if same correlation is in context
   */
  @Override
  public void postReceiptsToDCFin(Container container, HttpHeaders httpHeaders, Integer txId) {
    if (Boolean.FALSE.equals(appConfig.getIsReceiptPostingEnaledForDCFin())) return;

    DCFinPurchaseRequestBody dcFinPurchaseRequestBody = new DCFinPurchaseRequestBody();
    try {
      Map<String, Object> forwardablHeaderWithTenantData =
          ReceivingUtils.getForwardablHeader(httpHeaders);
      forwardablHeaderWithTenantData.put(
          ReceivingConstants.TENENT_FACLITYNUM,
          String.valueOf(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
      forwardablHeaderWithTenantData.put(
          ReceivingConstants.TENENT_COUNTRY_CODE,
          String.valueOf(httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE)));

      dcFinPurchaseRequestBody.setTxnId(getTxnId(forwardablHeaderWithTenantData, txId));
      dcFinPurchaseRequestBody.setPurchase(getPurchases(container, httpHeaders));

      final HttpHeaders dcFinRequestHeaders =
          DCFinRestApiClient.buildHttpHeaders(
              forwardablHeaderWithTenantData, appConfig.getDcFinApiKey());

      post(
          dcFinRequestHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
          dcFinPurchaseRequestBody.getTxnId(),
          appConfig.getDcFinBaseUrl() + DC_FIN_POST_RECEIPTS_V2,
          dcFinRequestHeaders,
          gson.toJson(dcFinPurchaseRequestBody),
          true);

    } catch (Exception e) {
      LOGGER.error(
          SPLUNK_ALERT + "Exception while sending data to dcFin. Error Message : {}",
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));
    }
  }

  public static String getTxnId(Map<String, Object> headersMap, Integer txId) {
    return headersMap.getOrDefault(CORRELATION_ID_HEADER_KEY, UUID.randomUUID()).toString()
        + (isNull(txId) ? EMPTY_STRING : (DELIM_DASH + txId));
  }

  private List<Purchase> getPurchases(Container container, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<Purchase> purchaseList = new ArrayList<>();
    Purchase purchase = new Purchase();
    List<PurchaseLine> purchaseLineList = new ArrayList<>();

    Set<Container> childContainers = container.getChildContainers();
    ContainerItem containerItem = null;
    if (!CollectionUtils.isEmpty(childContainers)) {
      containerItem =
          childContainers.stream().findFirst().get().getContainerItems().stream().findFirst().get();
    } else {
      containerItem = container.getContainerItems().stream().findFirst().get();
    }

    final String purchaseReferenceNumber = containerItem.getPurchaseReferenceNumber();
    final Integer purchaseReferenceLineNumber = containerItem.getPurchaseReferenceLineNumber();
    final Long deliveryNumber = container.getDeliveryNumber();
    DeliveryCacheValue gdmDeliveryCacheValue =
        deliveryCache.getDeliveryDetailsByPoPoLine(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, httpHeaders);
    if (isNull(gdmDeliveryCacheValue)) {
      LOGGER.error(
          "Couldnt find DeliveryCacheValue for delivery : {}, PO : {}, PoLine : {}",
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    }

    purchase.setDocType(gdmDeliveryCacheValue.getPurchaseReferenceLegacyType());
    purchase.setCarrierName(gdmDeliveryCacheValue.getScacCode());
    purchase.setCarrierScacCode(gdmDeliveryCacheValue.getScacCode());
    purchase.setTrailerNbr(gdmDeliveryCacheValue.getTrailerId());
    purchase.setBillCode(gdmDeliveryCacheValue.getFreightTermCode());
    purchase.setDeliveryNum(Long.toString(deliveryNumber));
    purchase.setDateReceived(container.getCompleteTs());
    purchase.setFreightBillQty(gdmDeliveryCacheValue.getTotalBolFbq());
    purchase.setDocumentNum(purchaseReferenceNumber);

    if (CollectionUtils.isEmpty(childContainers)) {
      purchaseLineList.add(getPurchaseLine(container));
    } else {
      childContainers
          .stream()
          .forEach(childContainer -> purchaseLineList.add(getPurchaseLine(childContainer)));
    }
    purchase.setLines(purchaseLineList);
    purchaseList.add(purchase);
    return purchaseList;
  }

  private PurchaseLine getPurchaseLine(Container container) {
    PurchaseLine purchaseLine = new PurchaseLine();
    ContainerItem containerItem = container.getContainerItems().get(0);
    if (gdcFlagReader.isManualGdcEnabled() && gdcFlagReader.isDCOneAtlasEnabled()) {
      purchaseLine.setAtlasItem(isAtlasConvertedItem(containerItem));
    }
    purchaseLine.setDocumentLineNo(String.valueOf(containerItem.getPurchaseReferenceLineNumber()));
    purchaseLine.setItemNumber(containerItem.getItemNumber().intValue());
    purchaseLine.setBaseDivCode(
        StringUtils.isEmpty(containerItem.getBaseDivisionCode())
            ? ReceivingConstants.BASE_DIVISION_CODE
            : containerItem.getBaseDivisionCode());
    purchaseLine.setFinancialReportGrpCode(
        StringUtils.isEmpty(containerItem.getFinancialReportingGroupCode())
            ? ReceivingConstants.COUNTRY_CODE_US
            : containerItem.getFinancialReportingGroupCode());

    String dcFinChannelMethod =
        GdmToDCFinChannelMethodResolver.getDCFinChannelMethod(
            containerItem.getInboundChannelMethod());
    purchaseLine.setInboundChannelMethod(dcFinChannelMethod);
    purchaseLine.setPrimaryQty(containerItem.getQuantity());
    purchaseLine.setLineQtyUOM(containerItem.getQuantityUOM());
    purchaseLine.setWarehousePackEachQty(containerItem.getWhpkQty());
    purchaseLine.setVendorPackEachQty(containerItem.getVnpkQty());
    purchaseLine.setSecondaryQty(container.getContainerItems().get(0).getVnpkWgtQty());
    purchaseLine.setSecondaryQtyUOM(ReceivingConstants.Uom.DCFIN_LB_ZA);
    purchaseLine.setPromoBuyInd(containerItem.getPromoBuyInd());
    purchaseLine.setWarehouseAreaCode(containerItem.getWarehouseAreaCode());
    purchaseLine.setWeightFormatType(containerItem.getWeightFormatTypeCode());
    purchaseLine.setContainerId(containerItem.getTrackingId());

    return purchaseLine;
  }
}
