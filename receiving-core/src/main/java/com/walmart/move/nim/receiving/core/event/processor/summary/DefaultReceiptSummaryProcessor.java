package com.walmart.move.nim.receiving.core.event.processor.summary;

import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.isPoFinalized;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getCorrelationId;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.net.URI;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component(ReceivingConstants.DEFAULT_RECEIPT_SUMMARY_PROCESSOR)
public class DefaultReceiptSummaryProcessor implements ReceiptSummaryProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultReceiptSummaryProcessor.class);

  @Autowired protected DeliveryServiceRetryableImpl deliveryService;
  @Autowired protected ReceiptService receiptService;
  @Autowired protected Gson gson;
  @Autowired protected ReceiptCustomRepository receiptCustomRepository;
  @Autowired protected TenantSpecificConfigReader configUtils;

  @ManagedConfiguration protected AppConfig appConfig;

  @Override
  public List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber) {
    return receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(deliveryNumber);
  }

  @Override
  public ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPo(
      Long deliveryNumber, HttpHeaders httpHeaders) throws ReceivingException {
    LOGGER.info("Getting delivery receipts summary by po for deliveryNumber:{}", deliveryNumber);

    String deliveryInfo = getDeliveryDetailsFromGDM(deliveryNumber, httpHeaders);
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryInfo, GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocuments = validateDeliveryDocument(gdmPOLineResponse);

    List<ReceiptSummaryResponse> poReceiptSummaryList = getReceivedQtyByPo(deliveryNumber);

    final HashMap<String, Receipt> finalizedPoReceiptMap =
        getFinalizedPoReceiptMap(deliveryNumber, httpHeaders);

    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        populateReceiptsSummaryByPo(deliveryDocuments, poReceiptSummaryList, finalizedPoReceiptMap);
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        getReceiptsSummaryByPoResponse(deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);
    return populateAsnInfo(gdmPOLineResponse, receiptSummaryQtyByPoResponse);
  }

  @Override
  public ReceiptSummaryQtyByPoLineResponse getReceiptsSummaryByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info(
        "Getting delivery receipts summary by poLine for deliveryNumber:{}, purchaseReferenceNumber:{}",
        deliveryNumber,
        purchaseReferenceNumber);

    String deliveryInfo = getDeliveryDetailsFromGDM(deliveryNumber, httpHeaders);
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryInfo, GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocuments = validateDeliveryDocument(gdmPOLineResponse);
    DeliveryDocument deliveryDocument =
        getDeliveryDocumentByPo(deliveryDocuments, purchaseReferenceNumber);

    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getReceivedQtyByPoLine(deliveryNumber, purchaseReferenceNumber);

    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines =
        populateReceiptsSummaryByPoLine(deliveryDocument, receiptSummaryResponseList);

    return getReceiptsSummaryByPoLineResponse(
        purchaseReferenceNumber, receiptSummaryQtyByPoLines, deliveryNumber, httpHeaders);
  }

  @Override
  public List<ReceiptQtySummaryByDeliveryNumberResponse> getReceiptQtySummaryByDeliveries(
      ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<ReceiptSummaryVnpkResponse> receiptSummaryResponseByDeliveryNumbersFromDB =
        receiptService.receivedQtySummaryByDeliveryNumbers(
            receiptSummaryQtyByDeliveries.getDeliveries());
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryResponseByDeliveryNumbers =
        new ArrayList<>();

    if (!CollectionUtils.isEmpty(receiptSummaryResponseByDeliveryNumbersFromDB)) {
      for (ReceiptSummaryVnpkResponse receiptSummaryResponseData :
          receiptSummaryResponseByDeliveryNumbersFromDB) {
        ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryResponsePerDelivery =
            new ReceiptQtySummaryByDeliveryNumberResponse();
        receiptSummaryResponsePerDelivery.setDeliveryNumber(
            receiptSummaryResponseData.getDeliveryNumber());
        receiptSummaryResponsePerDelivery.setReceivedQty(
            receiptSummaryResponseData.getReceivedQty());
        receiptSummaryResponsePerDelivery.setReceivedQtyUom(receiptSummaryResponseData.getQtyUOM());
        receiptSummaryResponseByDeliveryNumbers.add(receiptSummaryResponsePerDelivery);
      }
    }
    return receiptSummaryResponseByDeliveryNumbers;
  }

  @Override
  public List<ReceiptQtySummaryByPoNumbersResponse> getReceiptQtySummaryByPoNumbers(
      ReceiptSummaryQtyByPos receiptSummaryQtyByPoNumbers, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<ReceiptSummaryVnpkResponse> receiptSummaryResponseByPoNumbersFromDB =
        receiptService.receivedQtySummaryByPoNumbers(receiptSummaryQtyByPoNumbers.getPoNumbers());
    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryResponseByPoNumbers =
        new ArrayList<>();

    if (!CollectionUtils.isEmpty(receiptSummaryResponseByPoNumbersFromDB)) {
      for (ReceiptSummaryVnpkResponse receiptSummaryResponseData :
          receiptSummaryResponseByPoNumbersFromDB) {
        ReceiptQtySummaryByPoNumbersResponse receiptSummaryResponsePerPoNumber =
            new ReceiptQtySummaryByPoNumbersResponse();
        receiptSummaryResponsePerPoNumber.setPoNumber(
            receiptSummaryResponseData.getPurchaseReferenceNumber());
        receiptSummaryResponsePerPoNumber.setReceivedQty(
            receiptSummaryResponseData.getReceivedQty());
        receiptSummaryResponsePerPoNumber.setReceivedQtyUom(receiptSummaryResponseData.getQtyUOM());
        receiptSummaryResponseByPoNumbers.add(receiptSummaryResponsePerPoNumber);
      }
    }
    return receiptSummaryResponseByPoNumbers;
  }

  @Override
  public List<DeliveryDocument> getStoreDistributionByDeliveryPoPoLine(
      Long deliveryNumber,
      String poNumber,
      int poLineNumber,
      HttpHeaders headers,
      boolean isAtlasItem)
      throws ReceivingException {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  private HashMap<String, Receipt> getFinalizedPoReceiptMap(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    if (!isKotlinEnabled(httpHeaders, configUtils)) {
      return null;
    }

    final List<Receipt> receipts = receiptService.findFinalizedReceiptsFor(deliveryNumber);
    HashMap<String, Receipt> finalizedPoReceiptMap = new HashMap<>();
    for (Receipt receipt : receipts) {
      finalizedPoReceiptMap.put(receipt.getPurchaseReferenceNumber(), receipt);
    }
    LOGGER.info(
        "correlationId={}, finalized POs={}", getCorrelationId(), finalizedPoReceiptMap.keySet());

    return finalizedPoReceiptMap;
  }

  private boolean isPoFinalizedFor(
      Long deliveryNumber, String purchaseReferenceNumber, HttpHeaders httpHeaders) {
    if (org.apache.commons.lang3.StringUtils.isBlank(purchaseReferenceNumber)
        || !isKotlinEnabled(httpHeaders, configUtils)) {
      return false;
    }

    final List<Receipt> osdrMasterList =
        receiptService.findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(
            deliveryNumber, purchaseReferenceNumber);
    for (Receipt receipt : osdrMasterList) {
      LOGGER.info(
          "correlationId={}, PO={}, finalized userid={}, ts={}",
          getCorrelationId(),
          purchaseReferenceNumber,
          receipt.getFinalizedUserId(),
          receipt.getFinalizeTs());
      return isPoFinalized(receipt);
    }
    LOGGER.info(
        "correlationId={}, PO={}, not finalized ", getCorrelationId(), purchaseReferenceNumber);
    return false;
  }

  public List<DeliveryDocument> validateDeliveryDocument(GdmPOLineResponse gdmPOLineResponse)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      throw new ReceivingException(
          String.format(
              ReceivingException.NO_DELIVERY_DOCUMENTS_FOUND,
              gdmPOLineResponse.getDeliveryNumber()),
          HttpStatus.NOT_FOUND,
          ExceptionCodes.GDM_DELIVERY_DOCUMENTS_NOT_FOUND);
    }
    return deliveryDocuments;
  }

  public DeliveryDocument getDeliveryDocumentByPo(
      List<DeliveryDocument> deliveryDocuments, String purchaseReferenceNumber)
      throws ReceivingException {
    Optional<DeliveryDocument> deliveryDocument =
        deliveryDocuments
            .stream()
            .parallel()
            .filter(
                document ->
                    document.getPurchaseReferenceNumber().equalsIgnoreCase(purchaseReferenceNumber))
            .findAny();

    if (!deliveryDocument.isPresent()) {
      throw new ReceivingException(
          String.format(ReceivingException.NO_PO_FOUND, purchaseReferenceNumber),
          HttpStatus.NOT_FOUND,
          ExceptionCodes.PO_NOT_FOUND);
    }
    if (CollectionUtils.isEmpty(deliveryDocument.get().getDeliveryDocumentLines())) {
      throw new ReceivingException(
          String.format(ReceivingException.NO_PO_LINES_FOUND, purchaseReferenceNumber),
          HttpStatus.NOT_FOUND,
          ExceptionCodes.PO_LINE_NOT_FOUND);
    }
    return deliveryDocument.get();
  }

  public String getDeliveryDetailsFromGDM(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, Long> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    String gdmBaseUri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;
    URI gdmGetDeliveryUri =
        UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();
    return deliveryService.getDeliveryByURI(gdmGetDeliveryUri, httpHeaders);
  }

  public List<ReceiptSummaryResponse> getReceivedQtyByPo(Long deliveryNumber) {
    return receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber);
  }

  public List<ReceiptSummaryResponse> getReceivedQtyByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber) {
    return receiptService.getReceivedQtySummaryByPoLineInVnpk(
        deliveryNumber, purchaseReferenceNumber);
  }

  public List<ReceiptSummaryQtyByPo> populateReceiptsSummaryByPo(
      List<DeliveryDocument> deliveryDocuments,
      List<ReceiptSummaryResponse> receiptSummaryResponseList,
      HashMap<String, Receipt> finalizedPoReceiptMap) {
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos = new ArrayList<>();

    for (DeliveryDocument deliveryDocument : deliveryDocuments) {

      if (!CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
        ReceiptSummaryQtyByPo receiptSummaryQtyByPo = new ReceiptSummaryQtyByPo();
        final String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
        receiptSummaryQtyByPo.setPurchaseReferenceNumber(purchaseReferenceNumber);
        receiptSummaryQtyByPo.setFreightBillQuantity(
            deliveryDocument.getTotalPurchaseReferenceQty());
        receiptSummaryQtyByPo.setTotalBolFbq(deliveryDocument.getTotalBolFbq());
        receiptSummaryQtyByPo.setVendorName(deliveryDocument.getVendorName());
        receiptSummaryQtyByPo.setFreightTermCode(deliveryDocument.getFreightTermCode());

        if (finalizedPoReceiptMap != null
            && finalizedPoReceiptMap.get(purchaseReferenceNumber) != null) {
          receiptSummaryQtyByPo.setPoFinalized(true);
        }

        Optional<ReceiptSummaryResponse> receiptSummaryResponse =
            receiptSummaryResponseList
                .stream()
                .filter(
                    receiptSummary ->
                        receiptSummary
                            .getPurchaseReferenceNumber()
                            .equalsIgnoreCase(purchaseReferenceNumber))
                .findAny();
        if (receiptSummaryResponse.isPresent()) {
          receiptSummaryQtyByPo.setReceivedQty(
              receiptSummaryResponse.get().getReceivedQty().intValue());
        } else {
          receiptSummaryQtyByPo.setReceivedQty(0);
        }
        receiptSummaryQtyByPos.add(receiptSummaryQtyByPo);
      }
    }
    return receiptSummaryQtyByPos;
  }

  public List<ReceiptSummaryQtyByPoLine> populateReceiptsSummaryByPoLine(
      DeliveryDocument deliveryDocument, List<ReceiptSummaryResponse> receiptSummaryResponseList) {
    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines = new ArrayList<>();
    for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
      ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine = new ReceiptSummaryQtyByPoLine();
      String itemDescription =
          nonNull(deliveryDocumentLine.getDescription())
              ? deliveryDocumentLine.getDescription()
              : deliveryDocumentLine.getSecondaryDescription();

      if (StringUtils.isEmpty(itemDescription)) {
        LOGGER.warn(
            "Item description not found for Item Number:{}", deliveryDocumentLine.getItemNbr());
        itemDescription = ReceivingConstants.EMPTY_STRING;
      }
      receiptSummaryQtyByPoLine.setItemDescription(itemDescription);
      receiptSummaryQtyByPoLine.setLineNumber(
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      receiptSummaryQtyByPoLine.setItemNumber(deliveryDocumentLine.getItemNbr().intValue());
      receiptSummaryQtyByPoLine.setOrderQty(deliveryDocumentLine.getTotalOrderQty());
      if (Objects.nonNull(deliveryDocumentLine.getFreightBillQty())) {
        receiptSummaryQtyByPoLine.setFreightBillQty(deliveryDocumentLine.getFreightBillQty());
      } else {
        receiptSummaryQtyByPoLine.setFreightBillQty(ReceivingConstants.ZERO_QTY);
      }
      String vendorStockNumber =
          org.apache.commons.lang.StringUtils.isNotBlank(deliveryDocumentLine.getNdc())
              ? deliveryDocumentLine.getNdc()
              : deliveryDocumentLine.getVendorStockNumber();
      receiptSummaryQtyByPoLine.setVendorStockNumber(vendorStockNumber);

      Optional<ReceiptSummaryResponse> receiptSummaryResponse =
          receiptSummaryResponseList
              .stream()
              .filter(
                  receiptSummary ->
                      (deliveryDocument
                              .getPurchaseReferenceNumber()
                              .equalsIgnoreCase(receiptSummary.getPurchaseReferenceNumber())
                          && Objects.equals(
                              deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                              receiptSummary.getPurchaseReferenceLineNumber())))
              .findAny();
      if (receiptSummaryResponse.isPresent()) {
        receiptSummaryQtyByPoLine.setReceivedQty(
            receiptSummaryResponse.get().getReceivedQty().intValue());
      } else {
        receiptSummaryQtyByPoLine.setReceivedQty(0);
      }
      receiptSummaryQtyByPoLines.add(receiptSummaryQtyByPoLine);
    }
    return receiptSummaryQtyByPoLines;
  }

  public ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPoResponse(
      Long deliveryNumber,
      GdmPOLineResponse gdmPOLineResponse,
      List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos) {
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();

    ReceiptSummaryQtyByPoResponse response = new ReceiptSummaryQtyByPoResponse();
    Integer totalReceivedQty = 0;
    Integer totalBolFbq = 0;
    for (ReceiptSummaryQtyByPo receiptSummaryQtyByPo : receiptSummaryQtyByPos) {
      totalReceivedQty += receiptSummaryQtyByPo.getReceivedQty();
      totalBolFbq += receiptSummaryQtyByPo.getTotalBolFbq();
    }
    Integer totalFreightBillQty =
        deliveryDocuments
            .stream()
            .map(DeliveryDocument::getTotalPurchaseReferenceQty)
            .reduce(0, Integer::sum);
    response.setReceivedQty(totalReceivedQty);
    response.setDeliveryNumber(deliveryNumber);
    response.setReceivedQtyUom(VNPK);
    response.setFreightBillQuantity(totalFreightBillQty);
    response.setTotalBolFbq(totalBolFbq);
    response.setSummary(receiptSummaryQtyByPos);
    response.setDeliveryTypeCode(gdmPOLineResponse.getDeliveryTypeCode());
    response.setGdmPOLineResponse(gdmPOLineResponse);
    return response;
  }

  public ReceiptSummaryQtyByPoLineResponse getReceiptsSummaryByPoLineResponse(
      String purchaseReferenceNumber,
      List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines,
      Long deliveryNumber,
      HttpHeaders httpHeaders) {
    ReceiptSummaryQtyByPoLineResponse response =
        getReceivedQtyResponseAndSetReceivedQtyAndFbq(receiptSummaryQtyByPoLines);
    response.setSummary(receiptSummaryQtyByPoLines);
    response.setReceivedQtyUom(VNPK);
    response.setPurchaseReferenceNumber(purchaseReferenceNumber);
    response.setPoFinalized(isPoFinalizedFor(deliveryNumber, purchaseReferenceNumber, httpHeaders));

    return response;
  }

  protected ReceiptSummaryQtyByPoLineResponse getReceivedQtyResponseAndSetReceivedQtyAndFbq(
      List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines) {
    ReceiptSummaryQtyByPoLineResponse response = new ReceiptSummaryQtyByPoLineResponse();
    if (!CollectionUtils.isEmpty(receiptSummaryQtyByPoLines)) {
      receiptSummaryQtyByPoLines.sort(
          Comparator.comparing(ReceiptSummaryQtyByPoLine::getLineNumber));
      receiptSummaryQtyByPoLines.forEach(
          line -> {
            response.setTotalReceivedQty(
                response.getTotalReceivedQty()
                    + (isNull(line.getReceivedQty()) ? 0 : line.getReceivedQty()));
            response.setTotalFreightBillQty(
                response.getTotalFreightBillQty()
                    + (isNull(line.getFreightBillQty()) ? 0 : line.getFreightBillQty()));
          });
    }
    return response;
  }

  public ReceiptSummaryQtyByPoResponse populateAsnInfo(
      GdmPOLineResponse gdmPOLineResponse,
      ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse) {
    List<Shipment> shipments =
        nonNull(gdmPOLineResponse.getShipments())
                && !org.apache.commons.collections4.CollectionUtils.isEmpty(
                    gdmPOLineResponse.getShipments())
            ? gdmPOLineResponse.getShipments()
            : Collections.emptyList();
    receiptSummaryQtyByPoResponse.setShipments(shipments);
    if (!org.apache.commons.collections4.CollectionUtils.isEmpty(shipments)) {
      Integer asnQty = shipments.stream().map(Shipment::getTotalPacks).reduce(0, Integer::sum);
      receiptSummaryQtyByPoResponse.setAsnQty(asnQty);
    } else {
      receiptSummaryQtyByPoResponse.setAsnQty(0);
    }
    return receiptSummaryQtyByPoResponse;
  }
}
