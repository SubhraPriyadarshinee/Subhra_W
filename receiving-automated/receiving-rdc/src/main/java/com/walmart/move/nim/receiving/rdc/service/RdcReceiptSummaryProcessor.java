package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MFC_ALIGNED_STORES;

import com.walmart.move.nim.receiving.core.client.gdm.AsyncGdmRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.AsyncNimRdsRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.PoLineDistribution;
import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsSummaryByPo;
import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsSummaryByPoResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.StoreDistribution;
import com.walmart.move.nim.receiving.core.client.orderwell.OrderWellClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class RdcReceiptSummaryProcessor extends DefaultReceiptSummaryProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcReceiptSummaryProcessor.class);

  @Resource(name = RdcConstants.RDC_OSDR_SERVICE)
  private RdcOsdrService rdcOsdrSummaryService;

  @Autowired private AsyncNimRdsRestApiClient asyncNimRdsRestApiClient;
  @Autowired private AsyncGdmRestApiClient asyncGdmRestApiClient;
  @Autowired private NimRDSRestApiClient nimRDSRestApiClient;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private LabelDataService labelDataService;

  @Autowired OrderWellClient orderWellClient;

  @Override
  public List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber) {
    LOGGER.info("Getting delivery receipts for deliveryNumber:{}", deliveryNumber);
    try {
      OsdrSummary osdrSummaryResponse =
          rdcOsdrSummaryService.getOsdrSummary(deliveryNumber, ReceivingUtils.getHeaders());
      return ReceivingUtils.getReceiptSummaryResponseForRDC(osdrSummaryResponse);
    } catch (Exception ex) {
      LOGGER.error(
          String.format(
              ReceivingException.UNABLE_TO_GET_DELIVERY_RECEIPTS, deliveryNumber, ex.getMessage()));
      return Collections.emptyList();
    }
  }

  /**
   * This method gets the delivery summary by po level for given delivery numbers 1. Gets delivery
   * documents from GDM & received qty from RDS through async calls 2. Fetches received qty in Atlas
   * system by po level 3. Combines the overall received qty from RDS and Atlas system 4. Populates
   * delivery summary by combining GDM delivery response with the overall received qty 5. Prepares
   * delivery summary response including overall fbq, receivedQty, summary
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @return ReceiptSummaryQtyByPoResponse
   * @throws ReceivingException
   */
  @Override
  public ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPo(
      Long deliveryNumber, HttpHeaders httpHeaders) throws ReceivingException {
    LOGGER.info("Getting delivery receipts summary for deliveryNumber:{}", deliveryNumber);
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos = new ArrayList<>();
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    try {
      List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
      Map<String, Object> httpHeadersMap =
          ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

      Map<String, Long> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
      String gdmBaseUri =
          appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_V2_URI_INCLUDE_DUMMYPO;
      URI gdmGetDeliveryUri =
          UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();

      TenantContext.get()
          .setGdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoAsyncCallStart(
              System.currentTimeMillis());

      RdsReceiptsSummaryByPoResponse rdsReceiptsSummaryByPoResponse =
          new RdsReceiptsSummaryByPoResponse();
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), RdcConstants.IS_RDS_INTEGRATION_DISABLED, false)) {
        rdsReceiptsSummaryByPoResponse.setSummary(Collections.emptyList());
        String gdmPOLineResponseString =
            deliveryService.getDeliveryByURI(gdmGetDeliveryUri, httpHeaders);
        gdmPOLineResponse = gson.fromJson(gdmPOLineResponseString, GdmPOLineResponse.class);
      } else {
        CompletableFuture<String> deliveryFuture =
            asyncGdmRestApiClient.getDeliveryDetails(gdmGetDeliveryUri, httpHeaders);
        CompletableFuture<RdsReceiptsSummaryByPoResponse> nimRdsFuture =
            asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(deliveryNumber, httpHeadersMap);

        CompletableFuture.allOf(deliveryFuture, nimRdsFuture).join();
        gdmPOLineResponse = gson.fromJson(deliveryFuture.get(), GdmPOLineResponse.class);
        rdsReceiptsSummaryByPoResponse = nimRdsFuture.get();
      }

      TenantContext.get()
          .setGdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoAsyncCallEnd(
              System.currentTimeMillis());

      List<DeliveryDocument> deliveryDocuments = validateDeliveryDocument(gdmPOLineResponse);
      List<RdsReceiptsSummaryByPo> rdsPoReceiptsSummaryList =
          rdsReceiptsSummaryByPoResponse.getSummary();

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false)) {
        receiptSummaryResponseList = getReceivedQtyByPo(deliveryNumber);
      }

      if (!CollectionUtils.isEmpty(rdsPoReceiptsSummaryList)) {
        receiptSummaryResponseList =
            combineReceiptsSummaryByPo(receiptSummaryResponseList, rdsPoReceiptsSummaryList);
      }

      receiptSummaryQtyByPos =
          populateReceiptsSummaryByPo(deliveryDocuments, receiptSummaryResponseList, null);

    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ReceivingInternalException(
          ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_CODE,
          "Error while fetching delivery detail from GDM");
    } catch (Exception e) {
      String errorMessage =
          Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage();
      LOGGER.error(
          "Error while fetching PO receipts summary for deliveryNumber {}, Error {}",
          deliveryNumber,
          errorMessage);
      throw new ReceivingException(
          String.format(
              ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_MESSAGE,
              deliveryNumber,
              errorMessage),
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_CODE);
    }
    return getReceiptsSummaryByPoResponse(
        deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);
  }

  /**
   * This method gets the delivery summary by poLine level for given delivery & po number 1. Gets
   * delivery documents from GDM & received qty from RDS by poLine through async calls 2. Fetches
   * received qty in Atlas system by poLine level 3. Combines the overall received qty from RDS and
   * Atlas system 4. Populates delivery summary by combining GDM delivery response with the overall
   * received qty 5. Prepares delivery summary response including poLine receipts summary.
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @param purchaseReferenceNumber
   * @return ReceiptSummaryQtyByPoLineResponse
   * @throws ReceivingException
   */
  @Override
  public ReceiptSummaryQtyByPoLineResponse getReceiptsSummaryByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info("Getting delivery receipts summary for deliveryNumber:{}", deliveryNumber);
    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines = new ArrayList<>();
    try {
      List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
      Map<String, Object> httpHeadersMap =
          ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

      Map<String, Long> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
      String gdmBaseUri =
          appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_V2_URI_INCLUDE_DUMMYPO;
      URI gdmGetDeliveryUri =
          UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();

      TenantContext.get()
          .setGdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoLineAsyncCallStart(
              System.currentTimeMillis());

      ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
          new ReceiptSummaryQtyByPoLineResponse();
      GdmPOLineResponse gdmPOLineResponse;
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), RdcConstants.IS_RDS_INTEGRATION_DISABLED, false)) {
        receiptSummaryQtyByPoLineResponse.setSummary(Collections.emptyList());
        String gdmPOLineResponseString =
            deliveryService.getDeliveryByURI(gdmGetDeliveryUri, httpHeaders);
        gdmPOLineResponse = gson.fromJson(gdmPOLineResponseString, GdmPOLineResponse.class);
      } else {
        CompletableFuture<String> deliveryFuture =
            asyncGdmRestApiClient.getDeliveryDetails(gdmGetDeliveryUri, httpHeaders);
        CompletableFuture<ReceiptSummaryQtyByPoLineResponse> nimRdsFuture =
            asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
                deliveryNumber, purchaseReferenceNumber, httpHeadersMap);

        CompletableFuture.allOf(deliveryFuture, nimRdsFuture).join();
        receiptSummaryQtyByPoLineResponse = nimRdsFuture.get();
        gdmPOLineResponse = gson.fromJson(deliveryFuture.get(), GdmPOLineResponse.class);
      }

      TenantContext.get()
          .setGdmDeliveryInfoAndNimRdsReceivedQtySummaryByPoLineAsyncCallEnd(
              System.currentTimeMillis());

      List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
      DeliveryDocument deliveryDocument =
          getDeliveryDocumentByPo(deliveryDocuments, purchaseReferenceNumber);

      List<ReceiptSummaryQtyByPoLine> rdsReceiptsSummaryQtyByPoLine =
          receiptSummaryQtyByPoLineResponse.getSummary();

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false)) {
        receiptSummaryResponseList =
            getReceivedQtyByPoLine(deliveryNumber, purchaseReferenceNumber);
      }

      if (!CollectionUtils.isEmpty(rdsReceiptsSummaryQtyByPoLine)) {
        receiptSummaryResponseList =
            combineReceiptsSummaryByPoLine(
                purchaseReferenceNumber, receiptSummaryResponseList, rdsReceiptsSummaryQtyByPoLine);
      }

      receiptSummaryQtyByPoLines =
          populateReceiptsSummaryByPoLine(deliveryDocument, receiptSummaryResponseList);

    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ReceivingInternalException(
          ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_CODE,
          "Error while fetching delivery detail from GDM");
    } catch (Exception e) {
      String errorMessage =
          Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage();
      LOGGER.error(
          "Error while fetching PO receipts summary for deliveryNumber: {}, poNumber: {}, Error {}",
          deliveryNumber,
          purchaseReferenceNumber,
          errorMessage);
      throw new ReceivingException(
          String.format(
              ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_LINE_ERROR_MESSAGE,
              purchaseReferenceNumber,
              errorMessage),
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_LINE_ERROR_CODE);
    }
    return getReceiptsSummaryByPoLineResponse(
        purchaseReferenceNumber, receiptSummaryQtyByPoLines, null, null);
  }

  /**
   * This method combines the received qty from RDS and Atlas system If nothing received in atlas
   * system, it populates RDS received details in the summary response if po received against both
   * RDS/Atlas, it sums the received qty for the po number in both systems
   *
   * @param receiptSummaryResponseList
   * @param rdsReceiptsSummaryByPos
   * @return
   */
  private List<ReceiptSummaryResponse> combineReceiptsSummaryByPo(
      List<ReceiptSummaryResponse> receiptSummaryResponseList,
      List<RdsReceiptsSummaryByPo> rdsReceiptsSummaryByPos) {
    List<ReceiptSummaryResponse> receiptExistsOnlyInRds = new ArrayList<>();

    if (CollectionUtils.isEmpty(receiptSummaryResponseList)) {
      receiptSummaryResponseList = new ArrayList<>();
      for (RdsReceiptsSummaryByPo rdsPoReceiptSummary : rdsReceiptsSummaryByPos) {
        receiptExistsOnlyInRds.add(
            new ReceiptSummaryVnpkResponse(
                rdsPoReceiptSummary.getPurchaseReferenceNumber(),
                rdsPoReceiptSummary.getReceivedQty().longValue()));
      }
    } else {
      for (RdsReceiptsSummaryByPo rdsPoReceiptSummary : rdsReceiptsSummaryByPos) {
        receiptSummaryResponseList =
            receiptSummaryResponseList
                .stream()
                .parallel()
                .map(
                    receiptSummaryResponse -> {
                      if (receiptSummaryResponse
                          .getPurchaseReferenceNumber()
                          .equalsIgnoreCase(rdsPoReceiptSummary.getPurchaseReferenceNumber())) {
                        receiptSummaryResponse.setReceivedQty(
                            receiptSummaryResponse.getReceivedQty()
                                + rdsPoReceiptSummary.getReceivedQty());
                      } else {
                        receiptExistsOnlyInRds.add(
                            new ReceiptSummaryVnpkResponse(
                                rdsPoReceiptSummary.getPurchaseReferenceNumber(),
                                rdsPoReceiptSummary.getReceivedQty().longValue()));
                      }
                      return receiptSummaryResponse;
                    })
                .collect(Collectors.toList());
      }
    }

    if (!CollectionUtils.isEmpty(receiptExistsOnlyInRds)) {
      receiptSummaryResponseList.addAll(receiptExistsOnlyInRds);
    }
    return receiptSummaryResponseList;
  }

  /**
   * This method combines the received qty from RDS and Atlas system. If poLine is not received in
   * atlas system, it populates RDS received poLine in the summary response. If poLine received
   * against both RDS/Atlas, it sums the received qty for the po number in both systems
   *
   * @param receiptSummaryResponseList
   * @param rdsReceiptsSummaryByPoLineList
   * @return
   */
  private List<ReceiptSummaryResponse> combineReceiptsSummaryByPoLine(
      String purchaseReferenceNumber,
      List<ReceiptSummaryResponse> receiptSummaryResponseList,
      List<ReceiptSummaryQtyByPoLine> rdsReceiptsSummaryByPoLineList) {
    List<ReceiptSummaryResponse> receiptExistsOnlyInRds = new ArrayList<>();

    if (CollectionUtils.isEmpty(receiptSummaryResponseList)) {
      receiptSummaryResponseList = new ArrayList<>();
      for (ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine : rdsReceiptsSummaryByPoLineList) {
        receiptExistsOnlyInRds.add(
            new ReceiptSummaryVnpkResponse(
                purchaseReferenceNumber,
                receiptSummaryQtyByPoLine.getLineNumber(),
                receiptSummaryQtyByPoLine.getReceivedQty().longValue()));
      }
    } else {
      for (ReceiptSummaryQtyByPoLine rdsReceiptsSummaryByPoLine : rdsReceiptsSummaryByPoLineList) {
        receiptSummaryResponseList =
            receiptSummaryResponseList
                .stream()
                .parallel()
                .map(
                    receiptSummaryResponse -> {
                      if (receiptSummaryResponse
                          .getPurchaseReferenceLineNumber()
                          .equals(rdsReceiptsSummaryByPoLine.getLineNumber())) {
                        receiptSummaryResponse.setReceivedQty(
                            receiptSummaryResponse.getReceivedQty()
                                + rdsReceiptsSummaryByPoLine.getReceivedQty());
                      } else {
                        receiptExistsOnlyInRds.add(
                            new ReceiptSummaryVnpkResponse(
                                purchaseReferenceNumber,
                                rdsReceiptsSummaryByPoLine.getLineNumber(),
                                rdsReceiptsSummaryByPoLine.getReceivedQty().longValue()));
                      }
                      return receiptSummaryResponse;
                    })
                .collect(Collectors.toList());
      }
    }

    if (!CollectionUtils.isEmpty(receiptExistsOnlyInRds)) {
      receiptSummaryResponseList.addAll(receiptExistsOnlyInRds);
    }
    return receiptSummaryResponseList;
  }

  @Override
  public ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPoResponse(
      Long deliveryNumber,
      GdmPOLineResponse gdmPOLineResponse,
      List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos) {
    ReceiptSummaryQtyByPoResponse response =
        super.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);
    return populateAsnInfo(gdmPOLineResponse, response);
  }

  /**
   * This method fetches total received qty by delivery numbers. It combines total received qty from
   * RDS & Atlas systems to return the total received qty.
   *
   * @param receiptSummaryQtyByDeliveries
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Override
  public List<ReceiptQtySummaryByDeliveryNumberResponse> getReceiptQtySummaryByDeliveries(
      ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<ReceiptQtySummaryByDeliveryNumberResponse> rdsReceiptSummaryResponse;
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), RdcConstants.IS_RDS_INTEGRATION_DISABLED, false)) {
      rdsReceiptSummaryResponse = Collections.emptyList();
    } else {
      rdsReceiptSummaryResponse =
          nimRDSRestApiClient.getReceivedQtySummaryByDeliveryNumbers(
              receiptSummaryQtyByDeliveries,
              ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders));
    }
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false)) {
      List<ReceiptSummaryVnpkResponse> atlasReceiptSummaryResponse =
          receiptService.receivedQtySummaryByDeliveryNumbers(
              receiptSummaryQtyByDeliveries.getDeliveries());
      if (!CollectionUtils.isEmpty(atlasReceiptSummaryResponse)) {
        for (ReceiptQtySummaryByDeliveryNumberResponse rdsreceiptSummaryByDelivery :
            rdsReceiptSummaryResponse) {
          List<ReceiptSummaryVnpkResponse> atlasReceiptSummary =
              atlasReceiptSummaryResponse
                  .stream()
                  .filter(
                      receiptSummaryByDelivery ->
                          receiptSummaryByDelivery
                              .getDeliveryNumber()
                              .equals(rdsreceiptSummaryByDelivery.getDeliveryNumber()))
                  .collect(Collectors.toList());
          if (!CollectionUtils.isEmpty(atlasReceiptSummary)) {
            rdsreceiptSummaryByDelivery.setReceivedQty(
                rdsreceiptSummaryByDelivery.getReceivedQty()
                    + atlasReceiptSummary.get(0).getReceivedQty());
          }
        }
      }
    }
    return rdsReceiptSummaryResponse;
  }

  @Override
  public List<DeliveryDocument> getStoreDistributionByDeliveryPoPoLine(
      Long deliveryNumber,
      String poNumber,
      int poLineNumber,
      HttpHeaders httpHeaders,
      boolean isAtlasItem) {

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    try {
      if (!isAtlasItem) {
        Map<String, Object> httpHeadersMap =
            ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

        String gdmBaseUri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;
        URI gdmGetDeliveryUri =
            getGdmDeliveryUri(gdmBaseUri, deliveryNumber, poNumber, poLineNumber);

        TenantContext.get()
            .setGdmDeliveryInfoAndNimRdsStoreDistributionByDeliveryPoPoLineAsyncCallStart(
                System.currentTimeMillis());
        CompletableFuture<String> deliveryFuture =
            asyncGdmRestApiClient.getDeliveryDetails(gdmGetDeliveryUri, httpHeaders);

        CompletableFuture<Pair<Integer, List<StoreDistribution>>> nimRdsFuture =
            asyncNimRdsRestApiClient.getStoreDistributionByDeliveryDocument(
                poNumber, poLineNumber, httpHeadersMap);

        CompletableFuture.allOf(deliveryFuture, nimRdsFuture).join();
        TenantContext.get()
            .setGdmDeliveryInfoAndNimRdsStoreDistributionByDeliveryPoPoLineAsyncCallEnd(
                System.currentTimeMillis());

        GdmPOLineResponse gdmPOLineResponse =
            gson.fromJson(deliveryFuture.get(), GdmPOLineResponse.class);
        deliveryDocuments = validateDeliveryDocument(gdmPOLineResponse);
        DeliveryDocumentLine deliveryDocumentLine =
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

        Integer totalReceiveQtyInWhpk = nimRdsFuture.get().getKey();
        Integer totalReceivedQtyInVnpk =
            ReceivingUtils.conversionToVendorPack(
                totalReceiveQtyInWhpk,
                ReceivingConstants.Uom.WHPK,
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
        Integer totalOrderQty =
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getTotalOrderQty();
        Integer openQty = totalOrderQty - totalReceivedQtyInVnpk;
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setOpenQty(openQty);
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .setTotalReceivedQty(totalReceivedQtyInVnpk);

        List<PoLineDistribution> poLineDistributions =
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getDistributions();
        List<StoreDistribution> storeDistributionsFromRDS = nimRdsFuture.get().getValue();
        populateStoreDistributionsByDeliveryDocuments(
            deliveryDocuments, storeDistributionsFromRDS, poLineDistributions);

      } else {
        LOGGER.info(
            "Item is Atlas Item for delivery:{}, PO:{}, POLine:{}",
            deliveryNumber,
            poNumber,
            poLineNumber);
        deliveryDocuments =
            getDistributionDetailsByDeliveryPoPoLine(
                deliveryNumber, poNumber, poLineNumber, httpHeaders);
      }
    } catch (InterruptedException ex) {
      LOGGER.error(
          "InterruptedException occur while fetching Delivery Document "
              + "or Store Distribution Pair for deliveryNumber: {}, poNumber: {}",
          deliveryNumber,
          poNumber);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      String errorMessage =
          Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage();
      LOGGER.error(
          "Error while fetching Delivery Document or Store Distribution Pair for deliveryNumber: {}, poNumber: {}, Error {}",
          deliveryNumber,
          poNumber,
          errorMessage);
      throw new ReceivingInternalException(
          String.format(ReceivingException.GET_STORE_DISTR_ERROR_MESSAGE, poNumber, errorMessage),
          ReceivingException.STORE_DISTRIBUTION_SERVER_ERROR);
    }
    return deliveryDocuments;
  }

  private URI getGdmDeliveryUri(
      String gdmBaseUri, Long deliveryNumber, String poNumber, int poLineNumber) {
    Map<String, Long> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    URI gdmGetDeliveryUri =
        UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();
    gdmGetDeliveryUri =
        UriComponentsBuilder.fromUriString(gdmGetDeliveryUri.toString())
            .queryParam(ReceivingConstants.PO_NUMBER, poNumber)
            .queryParam(ReceivingConstants.PO_LINE_NUMBER, Integer.toString(poLineNumber))
            .queryParam(ReceivingConstants.INCLUDE_DISTRIBUTIONS, Boolean.TRUE.toString())
            .build()
            .toUri();
    return gdmGetDeliveryUri;
  }

  private List<DeliveryDocument> getDistributionDetailsByDeliveryPoPoLine(
      Long deliveryNumber, String poNumber, int poLineNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info(
        "Trying to get Store Distribution Details for Atlas Item from GDM for deliveryNumber : {} , poNumber :{} ,"
            + "poLineNumber :{}  for FacilityNumber :{}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        getFacilityNum());

    // Get GDM Response
    String gdmBaseUri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;
    URI gdmGetDeliveryUri = getGdmDeliveryUri(gdmBaseUri, deliveryNumber, poNumber, poLineNumber);
    String gdmResponse = deliveryService.getDeliveryByURI(gdmGetDeliveryUri, httpHeaders);
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponse, GdmPOLineResponse.class);
    LOGGER.info(
        "GdmPOLineResponse Atlas Item Response for deliveryNumber : {} , poNumber :{} ,"
            + "poLineNumber :{}  for FacilityNumber :{} is :{}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        getFacilityNum(),
        gdmPOLineResponse.getDeliveryDocuments());
    List<DeliveryDocument> deliveryDocuments = validateDeliveryDocument(gdmPOLineResponse);

    // Get Total Received Qty
    Long totalReceivedQty =
        receiptService.receivedQtyByDeliveryPoAndPoLine(deliveryNumber, poNumber, poLineNumber);

    // populate Received Qty details
    Integer totalOrderQty =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getTotalOrderQty();
    Integer openQty = totalOrderQty - Math.toIntExact(totalReceivedQty);
    LOGGER.info(
        "For deliveryNumber : {} , poNumber :{} ,"
            + "poLineNumber :{} , OrderedQty is :{}, RecievedQty is :{}, OpenQty is :{}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        totalOrderQty,
        totalReceivedQty,
        openQty);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setOpenQty(openQty);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTotalReceivedQty(Math.toIntExact(totalReceivedQty));

    // populate distributions
    List<PoLineDistribution> poLineDistributions =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getDistributions();

    List<LabelData> labelDataList =
        labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            poNumber, poLineNumber);
    LOGGER.info(
        "For deliveryNumber : {} , poNumber :{} ," + "poLineNumber :{} , label data recievd is :{}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        labelDataList);

    Integer poType = deliveryDocuments.get(0).getPoTypeCode();
    Long itemNbr = deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr();
    Map<String, Long> storeMfcOrderedQtyDistributionVals = new HashMap<>();
    Map<String, Long> storeMfcRecievedQtyDistributionVals = new HashMap<>();
    if (!poLineDistributions.isEmpty()) {
      storeMfcOrderedQtyDistributionVals =
          prepareStoreMFCOrderedQtyDistribution(itemNbr, poType, poNumber, poLineDistributions);
      storeMfcRecievedQtyDistributionVals = prepareStoreMFCRecievedQtyDistribution(labelDataList);
    }
    return populateAtlasStoreMFCDistributionsByDeliveryDocuments(
        deliveryDocuments,
        storeMfcOrderedQtyDistributionVals,
        storeMfcRecievedQtyDistributionVals,
        poLineDistributions);
  }

  private Map<String, Long> prepareStoreMFCOrderedQtyDistribution(
      Long itemNbr, Integer poType, String poNumber, List<PoLineDistribution> poLineDistributions)
      throws ReceivingException {
    Map<String, Long> storeMFCOrderedQtyDistribution = new HashMap<>();
    boolean isMFCDistributionPalletPullSupported =
        tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MFC_DISTRIBUTION_PALLET_PULL_SUPPORTED, getFacilityNum());
    Set<Integer> eligibleMfcAlignedSet = new HashSet<>();
    String eligibleMfcAligned =
        configUtils.getCcmValue(TenantContext.getFacilityNum(), MFC_ALIGNED_STORES, "0");
    if (Objects.nonNull(eligibleMfcAligned)) {
      eligibleMfcAlignedSet =
          Arrays.stream(eligibleMfcAligned.split(","))
              .map(Integer::valueOf)
              .collect(Collectors.toSet());
      LOGGER.info(
          "The eligibleMfcAligned  Stores :{} for a facility Number :{}",
          eligibleMfcAligned,
          getFacilityNum());
    }
    for (PoLineDistribution currentPoLineDistribution : poLineDistributions) {
      if (isMFCDistributionPalletPullSupported
          && eligibleMfcAlignedSet.contains(
              Integer.valueOf(currentPoLineDistribution.getStoreNumber()))) {
        LOGGER.info(
            "The store :{} is mfcalligned stores so we will get Ordered qty distribution per Store and MFC level From OrderWELl",
            getFacilityNum());
        prepareMfcEnabledStoresOrderedQtyDistribution(
            itemNbr, poType, poNumber, currentPoLineDistribution, storeMFCOrderedQtyDistribution);
      } else {
        LOGGER.info(
            "The store :{} is not a mfcalligned stores so we will get Ordered Qty distribution per Store level only from GDM PO Line Distribution",
            getFacilityNum());
        prepareMfcDisabledStoresOrderedQtyDistribution(
            itemNbr, poType, poNumber, currentPoLineDistribution, storeMFCOrderedQtyDistribution);
      }
    }
    return storeMFCOrderedQtyDistribution;
  }

  private Map<String, Long> prepareMfcDisabledStoresOrderedQtyDistribution(
      Long itemNbr,
      Integer poType,
      String poNumber,
      PoLineDistribution currentPOLineDistribution,
      Map<String, Long> storeMFCOrderedQtyDistribution) {
    storeMFCOrderedQtyDistribution.put(
        prepareToAtlasConvertedStoreNumber(currentPOLineDistribution.getStoreNumber(), null),
        Long.valueOf(currentPOLineDistribution.getOrderQty()));
    return storeMFCOrderedQtyDistribution;
  }

  private Map<String, Long> prepareStoreMFCRecievedQtyDistribution(List<LabelData> labelDataList) {
    Map<String, Long> storeVals = new HashMap<>();
    if (!labelDataList.isEmpty()) {
      storeVals =
          labelDataList
              .stream()
              .filter(list -> list.getStatus().equals(LabelInstructionStatus.COMPLETE.name()))
              .collect(
                  Collectors.groupingBy(
                      list ->
                          prepareToAtlasConvertedStoreNumber(
                              list.getAllocation()
                                  .getContainer()
                                  .getFinalDestination()
                                  .getBuNumber(),
                              list.getAllocation()
                                  .getContainer()
                                  .getFinalDestination()
                                  .getDestType()),
                      Collectors.counting()));
      return storeVals;
    }
    return storeVals;
  }

  private Map<String, Long> prepareMfcEnabledStoresOrderedQtyDistribution(
      Long itemNbr,
      Integer poTypeCode,
      String poNumber,
      PoLineDistribution currentPoLineDistribution,
      Map<String, Long> storeMFCOrderedQtyDistribution)
      throws ReceivingException {
    LOGGER.info("Trying to Create StoreMFCOrderedQtyDistribution");
    if (validPOtypeforDAOrders(poTypeCode)) {
      LOGGER.info("PoType for mentioned current PoLinedistribution is :{}", poTypeCode);
      OrderWellZoneRequest orderWellZoneRequest = new OrderWellZoneRequest();
      List<OrderWellStoreDistribution> orderWellStoreDistributions = new ArrayList<>();
      OrderWellStoreDistribution orderWellStoreDistribution =
          prepareOrderWellZoneRequestforCurrentPoLineDistribution(
              itemNbr, poNumber, poTypeCode, currentPoLineDistribution);
      orderWellStoreDistributions.add(orderWellStoreDistribution);
      orderWellZoneRequest.setData(orderWellStoreDistributions);
      OrderWellZoneResponse orderWellZoneResponse =
          orderWellClient.getStoreMFCDistributionforStoreNbrandPO(orderWellZoneRequest);
      if (Objects.nonNull(orderWellZoneResponse)) {
        if (orderWellZoneResponse.getData().size() == 1) {
          OrderWellDistributionResponse orderWellDistributionResponse =
              orderWellZoneResponse.getData().get(0);
          if (Objects.nonNull(orderWellDistributionResponse.getMfcDistribution())
              && orderWellDistributionResponse.getMfcDistribution().getWhpkOrderQty() != 0) {
            OWMfcDistribution owMfcDistribution =
                orderWellDistributionResponse.getMfcDistribution();
            storeMFCOrderedQtyDistribution.put(
                prepareToAtlasConvertedStoreNumber(
                    String.valueOf(owMfcDistribution.getDestNbr()), owMfcDistribution.getZone()),
                (long) owMfcDistribution.getWhpkOrderQty());
          }
          if (Objects.nonNull(orderWellDistributionResponse.getStoreDistribution())
              && orderWellDistributionResponse.getStoreDistribution().getWhpkOrderQty() != 0) {
            OWStoreDistribution owStoreDistribution =
                orderWellDistributionResponse.getStoreDistribution();
            storeMFCOrderedQtyDistribution.put(
                prepareToAtlasConvertedStoreNumber(
                    String.valueOf(owStoreDistribution.getDestNbr()),
                    ReceivingConstants.LABEL_TYPE_STORE),
                (long) owStoreDistribution.getWhpkOrderQty());
          }
        } else {
          LOGGER.error(
              String.format(
                  ReceivingException.ORDERWELL_INVALID_RESPONSE,
                  poNumber,
                  currentPoLineDistribution.getStoreNumber()),
              HttpStatus.INTERNAL_SERVER_ERROR,
              ReceivingException.ORDERWELL_INVALID_RESPONSE_ERROR_CODE);
        }
      } else {
        if (Objects.nonNull(currentPoLineDistribution.getStoreNumber())) {
          LOGGER.error(
              String.format(
                  ReceivingException.ORDERWELL_ERROR_MESSAGE,
                  poNumber,
                  currentPoLineDistribution.getStoreNumber()),
              HttpStatus.INTERNAL_SERVER_ERROR,
              ReceivingException.ORDERWELL_ERROR_CODE);
        }
      }
      LOGGER.info("Completed Creation of  StoreMFCOrderedQtyDistribution Map");
      return storeMFCOrderedQtyDistribution;
    } else {
      LOGGER.error(
          "Invalid PO type for the DA orders {}, Error {}",
          poTypeCode,
          "Invalid PO Type for the DA Orders");
      throw new ReceivingException(
          String.format(
              ReceivingException.INVALID_PO_TYPE_FOR_DA_ORDERS,
              poTypeCode,
              "Invalid PO Type for the DA orders"),
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.ORDERWELL_INVALID_POTYPE_FOR_DA_ORDER);
    }
  }

  private OrderWellStoreDistribution prepareOrderWellZoneRequestforCurrentPoLineDistribution(
      Long itemNbr,
      String poNumber,
      Integer poTypeCode,
      PoLineDistribution currentPoLineDistribution) {
    LOGGER.info(
        "Trying to create Orderwell Request for current POLineDistruibution :{}",
        currentPoLineDistribution);
    OrderWellStoreDistribution orderWellStoreDistribution = new OrderWellStoreDistribution();
    orderWellStoreDistribution.setSourceNbr(getFacilityNum());
    orderWellStoreDistribution.setWmtItemNbr(Math.toIntExact(itemNbr));
    orderWellStoreDistribution.setDestNbr(
        Integer.valueOf(currentPoLineDistribution.getStoreNumber()));
    orderWellStoreDistribution.setPoNbr(poNumber);
    orderWellStoreDistribution.setPoType(poTypeCode);
    orderWellStoreDistribution.setWhpkOrderQty(currentPoLineDistribution.getOrderQty());
    return orderWellStoreDistribution;
  }

  private boolean validPOtypeforDAOrders(Integer poTypeCode) {
    return appConfig.getOrderWellDaOrdersPotype().contains(poTypeCode);
  }

  private String prepareToAtlasConvertedStoreNumber(String destNbr, String destType) {
    if (Objects.isNull(destType)) destType = ReceivingConstants.LABEL_TYPE_STORE;
    return destNbr.concat(ReceivingConstants.DELIM_DASH).concat(destType);
  }

  private List<DeliveryDocument> populateStoreDistributionsByDeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments,
      List<StoreDistribution> storeDistributions,
      List<PoLineDistribution> poLineDistributionsFromGdm) {
    for (PoLineDistribution poLineDistribution : poLineDistributionsFromGdm) {
      if (!CollectionUtils.isEmpty(storeDistributions)) {
        storeDistributions.forEach(
            storeDistribution -> {
              if (poLineDistribution
                  .getStoreNumber()
                  .equalsIgnoreCase(Integer.toString(storeDistribution.getStoreNbr()))) {
                poLineDistribution.setReceivedQty(storeDistribution.getWhpkDistribQty());
              }
            });
      } else {
        poLineDistribution.setReceivedQty(0);
      }
    }

    // Append zeros as prefix for 5 digit store number
    poLineDistributionsFromGdm.forEach(
        poLineDistribution -> {
          if (Objects.isNull(poLineDistribution.getReceivedQty())) {
            poLineDistribution.setReceivedQty(0);
          }
          poLineDistribution.setStoreNumber(
              StringUtils.leftPad(
                  poLineDistribution.getStoreNumber(), RdcConstants.STORE_NUMBER_MAX_LENGTH, "0"));
        });

    // Sort store numbers
    poLineDistributionsFromGdm =
        poLineDistributionsFromGdm
            .stream()
            .sorted(Comparator.comparing(PoLineDistribution::getStoreNumber))
            .collect(Collectors.toList());

    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setDistributions(poLineDistributionsFromGdm);

    return deliveryDocuments;
  }

  private List<DeliveryDocument> populateAtlasStoreMFCDistributionsByDeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments,
      Map<String, Long> storeMfcOrderedQtyDistributionVals,
      Map<String, Long> storeMfcRecievedQtyDistributionVals,
      List<PoLineDistribution> poLineDistributionsFromGdm) {
    LOGGER.info(
        "Populating  entries for poLineDistributionsFromGdm from AtlasStoreMFCDistributionsByDeliveryDocuments");
    List<PoLineDistribution> poLineDistributionsForGdm = new ArrayList<>();
    Set<Integer> eligibleMfcAlignedSet = new HashSet<>();
    List<PoLineDistribution> sortedPoLineDistributionsForGdm;
    if (!CollectionUtils.isEmpty(poLineDistributionsFromGdm)
        && storeMfcOrderedQtyDistributionVals.size() != 0) {
      for (PoLineDistribution poLineDistributionFromGdmObject : poLineDistributionsFromGdm) {
        String mfcKey =
            prepareToAtlasConvertedStoreNumber(
                poLineDistributionFromGdmObject.getStoreNumber(), ReceivingConstants.MFC);
        String storeKey =
            prepareToAtlasConvertedStoreNumber(
                poLineDistributionFromGdmObject.getStoreNumber(),
                ReceivingConstants.LABEL_TYPE_STORE);
        if (storeMfcOrderedQtyDistributionVals.containsKey(mfcKey)) {
          PoLineDistribution poLineDistributionForGDMObject = new PoLineDistribution();
          poLineDistributionForGDMObject.setStoreNumber(mfcKey);
          poLineDistributionForGDMObject.setOrderQty(
              Math.toIntExact(storeMfcOrderedQtyDistributionVals.get(mfcKey)));
          poLineDistributionForGDMObject.setQtyUOM(poLineDistributionFromGdmObject.getQtyUOM());
          poLineDistributionForGDMObject.setReceivedQty(
              Math.toIntExact(
                  storeMfcRecievedQtyDistributionVals.getOrDefault(mfcKey, Long.valueOf(0))));
          poLineDistributionsForGdm.add(poLineDistributionForGDMObject);
        }
        if (storeMfcOrderedQtyDistributionVals.containsKey(storeKey)) {
          PoLineDistribution poLineDistributionForGDMObject = new PoLineDistribution();
          poLineDistributionForGDMObject.setStoreNumber(storeKey);
          poLineDistributionForGDMObject.setOrderQty(
              Math.toIntExact(storeMfcOrderedQtyDistributionVals.get(storeKey)));
          poLineDistributionForGDMObject.setQtyUOM(poLineDistributionFromGdmObject.getQtyUOM());
          poLineDistributionForGDMObject.setReceivedQty(
              Math.toIntExact(
                  storeMfcRecievedQtyDistributionVals.getOrDefault(storeKey, Long.valueOf(0))));
          poLineDistributionsForGdm.add(poLineDistributionForGDMObject);
        }
      }
      LOGGER.info(
          "Process Complete for poLineDistributionsFromGdm entries from AtlasStoreMFCDistributionsByDeliveryDocuments");
    } else {
      LOGGER.error(
          "Error to create POLineDistribution for STORE and MFC level since StoreMFCOrderedQtyMap or GDMPODistributionObject is empty");
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_DATA, ReceivingException.ORDERWELL_ORDERED_ERROR_MESSAGE);
    }
    boolean isMFCDistributionPalletPullSupported =
        tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MFC_DISTRIBUTION_PALLET_PULL_SUPPORTED,
            Objects.requireNonNull(getFacilityNum()));

    String eligibleMfcAligned =
        configUtils.getCcmValue(TenantContext.getFacilityNum(), MFC_ALIGNED_STORES, "0");
    if (Objects.nonNull(eligibleMfcAligned)) {
      eligibleMfcAlignedSet =
          Arrays.stream(eligibleMfcAligned.split(","))
              .map(Integer::valueOf)
              .collect(Collectors.toSet());
      LOGGER.info(
          "The eligibleMfcAligned  Stores :{} for a facility Number :{}",
          eligibleMfcAligned,
          getFacilityNum());
    }
    // Append zeros as prefix for 5 digit store number
    Set<Integer> finalEligibleMfcAlignedSet = eligibleMfcAlignedSet;
    poLineDistributionsForGdm.forEach(
        poLineDistribution -> {
          if (Objects.isNull(poLineDistribution.getReceivedQty())) {
            poLineDistribution.setReceivedQty(0);
          }
          if (isMFCDistributionPalletPullSupported
              && finalEligibleMfcAlignedSet.contains(
                  getOriginalStoreNumber(poLineDistribution.getStoreNumber()))) {
            poLineDistribution.setStoreNumber(
                preparePaddedStoreNumber(poLineDistribution.getStoreNumber()));
          } else {
            poLineDistribution.setStoreNumber(
                prepareUnPaddedStoreNumber(poLineDistribution.getStoreNumber()));
          }
        });
    LOGGER.info("The new poLineDistributionsForGdm created is :{}", poLineDistributionsForGdm);
    // Sort store numbers
    sortedPoLineDistributionsForGdm =
        poLineDistributionsForGdm
            .stream()
            .sorted(Comparator.comparing(PoLineDistribution::getStoreNumber))
            .collect(Collectors.toList());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setDistributions(sortedPoLineDistributionsForGdm);

    return deliveryDocuments;
  }

  private String preparePaddedStoreNumber(String storeNumber) {
    LOGGER.info("The store Number that is to be Padded is :{}", storeNumber);
    String[] parts = storeNumber.split(ReceivingConstants.DELIM_DASH);
    String storeNbr = parts[0];
    int numericStoreNbr = Integer.parseInt(storeNbr);
    String paddedStoreNbr = String.format("%05d", numericStoreNbr);
    LOGGER.info("The store Number that is Padded is :{}", paddedStoreNbr);
    return paddedStoreNbr.concat(ReceivingConstants.DELIM_DASH).concat(parts[1]);
  }

  private String prepareUnPaddedStoreNumber(String storeNumber) {
    LOGGER.info("The store Number that is to be UnPadded is :{}", storeNumber);
    String[] parts = storeNumber.split(ReceivingConstants.DELIM_DASH);
    String storeNbr = parts[0];
    int numericStoreNbr = Integer.parseInt(storeNbr);
    String paddedStoreNbr = String.format("%05d", numericStoreNbr);
    LOGGER.info("The store Number that is Padded is :{}", paddedStoreNbr);
    return paddedStoreNbr;
  }

  private int getOriginalStoreNumber(String storeNumber) {
    LOGGER.info("The store Number with MFC/ Store Tag is  :{}", storeNumber);
    String[] parts = storeNumber.split(ReceivingConstants.DELIM_DASH);
    String storeNbr = parts[0];
    LOGGER.info("The store Number without MFC/ Store Tag is  :{}", storeNumber);
    return Integer.parseInt(storeNbr);
  }
}
