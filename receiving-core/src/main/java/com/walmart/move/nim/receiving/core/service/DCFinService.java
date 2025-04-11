package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getCorrelationId;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.constants.EndgameOutboxServiceName;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class DCFinService {

  private static final Logger LOG = LoggerFactory.getLogger(DCFinService.class);

  @Autowired RestUtils restUtils;

  @Autowired ContainerService containerService;

  @Autowired private RetryService jmsRecoveryService;

  @Autowired protected AsyncPersister asyncPersister;

  @Autowired private EndgameOutboxHandler endgameOutboxHandler;

  @ManagedConfiguration protected AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RapidRelayerService rapidRelayerService;

  protected Gson gson;

  public DCFinService() {
    gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
  }
  /**
   * This methods publishes all receipts for a given delivery to DC Fin. Correlation is set to
   * parent container's trackingId. This API should be invoked when retrying receipt posting.
   *
   * @param deliveryNumber parent container trackingId for which receipts are not published
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "dcFinReceiptsForDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "DCFinService",
      level3 = "postReceiptsForDelivery")
  public void postReceiptsForDelivery(Long deliveryNumber) throws ReceivingException {
    List<Container> containers = containerService.getContainerByDeliveryNumber(deliveryNumber);
    List<Container> parentContainers = ReceivingUtils.getAllParentContainers(containers);
    Map<String, String> failedContainers = new HashMap<>();
    parentContainers.forEach(
        container -> {
          try {
            postReceiptsToDCFin(container);
          } catch (ReceivingException e) {
            failedContainers.put(container.getTrackingId(), e.getMessage());
          }
        });
    if (!CollectionUtils.isEmpty(failedContainers)) {
      LOG.error("Some requests failed");
      throw new ReceivingException(
          String.format(
              ReceivingException.UNABLE_TO_POST_RECEIPTS_TO_DC_FIN_PARTIAL_ERROR,
              failedContainers.toString()),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
  /**
   * This methods publishes receipts to DC Fin. Correlation is set to parent container's trackingId.
   * This API should be invoked when retrying receipt posting.
   *
   * @param parentContainerTrackingId parent container trackingId for which receipts are not
   *     published
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "dcFinReconReceiptsRetryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "DCFinService",
      level3 = "postReceiptsToDCFin")
  public void postReceiptsToDCFin(String parentContainerTrackingId) throws ReceivingException {
    postReceiptsToDCFin(containerService.getContainerByTrackingId(parentContainerTrackingId));
  }

  /**
   * This methods publishes receipts to DC Fin for a given parent Container. Correlation is set to
   * parent container's trackingId. This API should be invoked when retrying receipt posting.
   *
   * @param parentContainer parentContainer for which receipts are not published
   * @throws ReceivingException
   */
  public void postReceiptsToDCFin(Container parentContainer) throws ReceivingException {

    // check if container is parent container
    ContainerValidationUtils.checkIfContainerIsParentContainer(parentContainer);
    postReceiptsToDCFin(
        containerService.getContainerIncludingChild(parentContainer),
        getHeadersForDCFin(parentContainer),
        false);
  }

  /**
   * This method posts receipts to DC Fin
   *
   * @param container
   * @param httpHeaders
   * @param isAsyncRestPostEnabled
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "dcFinExceptionCount",
      level1 = "uwms-receiving",
      level2 = "DCFinService",
      level3 = "postReceiptstoDCFin")
  public void postReceiptsToDCFin(
      Container container, HttpHeaders httpHeaders, boolean isAsyncRestPostEnabled)
      throws ReceivingException {
    if (!appConfig.getIsReceiptPostingEnaledForDCFin()) return;

    httpHeaders.add(ReceivingConstants.DCFIN_WMT_API_KEY, appConfig.getDcFinApiKey());
    // dc fin expects facilityCountryCode in upperCase 'US'. So making change only for dcFin call
    httpHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE).toUpperCase());

    String payload = getReceiptsPayloadForDCFin(container);

    String url =
        new StringBuilder()
            .append(appConfig.getDcFinBaseUrl())
            .append(ReceivingConstants.DC_FIN_POST_RECEIPTS)
            .toString();

    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);

    post(cId, container.getTrackingId(), url, httpHeaders, payload, isAsyncRestPostEnabled);
  }

  public void postReceiptsToDCFin(Container container, HttpHeaders httpHeaders, Integer txId) {
    LOG.error("Not implemented for other markets, only GDC implements via WitronDCFinServiceImpl");
  }

  /**
   * This method posts receipts to DC Fin
   *
   * @param url
   * @param httpHeaders
   * @param payload
   * @param isAsyncRestPostEnabled
   * @throws ReceivingException
   */
  public void post(
      String cId,
      String txnId,
      String url,
      HttpHeaders httpHeaders,
      String payload,
      boolean isAsyncRestPostEnabled)
      throws ReceivingException {

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_EG_DC_FIN_OUTBOX_PATTERN_ENABLED)) {
      endgameOutboxHandler.sendToOutbox(
          payload, EndgameOutboxServiceName.DCFIN_PURCHASE_POSTING.getServiceName(), httpHeaders);
    } else if (!Objects.isNull(httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM))
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).stream().findFirst().get(),
            ReceivingConstants.OUTBOX_PATTERN_ENABLED,
            false)) {
      rapidRelayerService.produceHttpMessage(
          DCFIN_PURCHASE_POSTING, payload, ReceivingUtils.convertHttpHeadersToHashMap(httpHeaders));
    } else {
      if (isAsyncRestPostEnabled) {
        RetryEntity eventRetryEntity =
            jmsRecoveryService.putForRetries(
                url, HttpMethod.POST, httpHeaders, payload, RetryTargetFlow.DCFIN_PURCHASE_POSTING);
        if (appConfig.getIsAsyncDCFinPostEnabled()) {
          asyncPersister.asyncPost(cId, txnId, eventRetryEntity, url, httpHeaders, payload);
        }
        return;
      }

      ResponseEntity<String> response = restUtils.post(url, httpHeaders, new HashMap<>(), payload);
      if (!response.getStatusCode().is2xxSuccessful()) {
        LOG.error(
            "CorrelationId={} Error in posting receipts to DC Fin for payload: {} response: {}",
            getCorrelationId(),
            payload,
            response.getBody());
        if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(ReceivingException.DC_FIN_SERVICE_DOWN)
                  .errorKey(ExceptionCodes.DC_FIN_SERVICE_DOWN)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
              .errorResponse(errorResponse)
              .build();
        }
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(
                    String.format(
                        ReceivingException.DC_FIN_POST_RECEIPT_ERROR,
                        response.getStatusCode(),
                        response.getBody()))
                .errorKey(ExceptionCodes.DC_FIN_POST_RECEIPT_ERROR)
                .values(new Object[] {response.getStatusCode(), response.getBody()})
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    }
  }

  /**
   * @param container
   * @return dc fin receipts json
   * @see <a
   *     href="https://collaboration.wal-mart.com/display/NGRCV/Post+receipts+to+DC-Fin">Reference
   *     Doc</a>
   */
  public String getReceiptsPayloadForDCFin(Container container) {
    DCFinReceipt dcFinReceipt = getReceiptsForDCFin(container);
    return gson.toJson(dcFinReceipt);
  }

  /**
   * Prepare receipt from given container
   *
   * @param container
   * @return
   */
  private DCFinReceipt getReceiptsForDCFin(Container container) {
    LOG.info("Entering getReceiptsForDCFin() with trackingId:{} ", container.getTrackingId());
    DCFinReceipt dcFinReceipt = new DCFinReceipt();
    dcFinReceipt.setTxnId(container.getTrackingId());
    dcFinReceipt.setPurchase(getPurchaseReferences(container));
    return dcFinReceipt;
  }

  private List<PurchaseItem> getPurchaseReferences(Container container) {
    List<PurchaseItem> purchaseItems = new ArrayList<>();
    PurchaseItem purchaseItem = getPurchaseItems(container);
    purchaseItems.add(purchaseItem);
    return purchaseItems;
  }

  private LinesItem getLine(Container container, Date completeTs) {
    LinesItem linesItem = new LinesItem();
    List<DistributionsItem> distributionsItems = new ArrayList<>();

    ContainerItem content = container.getContainerItems().get(0);
    linesItem.setDocumentLineNo(content.getPurchaseReferenceLineNumber());
    linesItem.setItemNumber(content.getItemNumber());
    linesItem.setContainerId(container.getTrackingId());
    linesItem.setParentContainerId(container.getParentTrackingId());
    linesItem.setPrimaryQty(content.getQuantity());
    linesItem.setLineQtyUOM(ReceivingConstants.Uom.EACHES);
    linesItem.setWarehousePackEachQty(content.getWhpkQty());
    linesItem.setVendorPackEachQty(content.getVnpkQty());
    linesItem.setInboundChannelMethod(content.getInboundChannelMethod());
    linesItem.setBaseDivCode(
        StringUtils.isEmpty(content.getBaseDivisionCode()) ? "WM" : content.getBaseDivisionCode());
    linesItem.setFinancialReportGrpCode(
        StringUtils.isEmpty(content.getFinancialReportingGroupCode())
            ? "US"
            : content.getFinancialReportingGroupCode());
    linesItem.setDateReceived(completeTs);
    linesItem.setIsItemVariableWeight(content.getIsVariableWeight());
    linesItem.setSecondaryQty(content.getVnpkWgtQty());
    /*
     As asked by DcFin to pass LB/ZA instead of LB
     GDM is providing LB.
     So as per discussion we will Hard code it as of now.
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

  protected DistributionsItem getLineDistribution(Distribution distribution, Container container) {
    DistributionsItem distributionsItem = new DistributionsItem();
    distributionsItem.setDestinationNumber(
        !StringUtils.isEmpty(distribution.getDestNbr())
            ? Integer.toString(distribution.getDestNbr())
            : container.getFacility().get("buNumber"));
    distributionsItem.setReceivedQty(distribution.getAllocQty());
    return distributionsItem;
  }

  public HttpHeaders getHeadersForDCFin(Container container) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingUtils.getContainerUser(container));
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, container.getTrackingId());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, container.getFacilityNum().toString());
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, container.getFacilityCountryCode());
    httpHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    return httpHeaders;
  }

  private PurchaseItem getPurchaseItems(Container container) {
    PurchaseItem purchaseItem = new PurchaseItem();
    List<LinesItem> linesItems = new ArrayList<>();

    Set<Container> childContainers = container.getChildContainers();
    purchaseItem.setDeliveryNum(Long.toString(container.getDeliveryNumber()));
    if (!CollectionUtils.isEmpty(childContainers)) {
      Optional<Container> optionalChildContainer = childContainers.stream().findFirst();
      Optional<ContainerItem> optionalChildContainerItem =
          optionalChildContainer.flatMap(value -> value.getContainerItems().stream().findFirst());
      ContainerItem firstChildContainerItem =
          optionalChildContainerItem.orElse(new ContainerItem());

      purchaseItem.setDocumentNum(firstChildContainerItem.getPurchaseReferenceNumber());
      setAdditionalAttributesForImports(purchaseItem, firstChildContainerItem);
      childContainers.forEach(
          childContainer -> linesItems.add(getLine(childContainer, container.getCompleteTs())));
    } else {
      Optional<ContainerItem> optionalContainerItem =
          container.getContainerItems().stream().findFirst();
      ContainerItem containerItem = optionalContainerItem.orElse(new ContainerItem());
      purchaseItem.setDocumentNum(containerItem.getPurchaseReferenceNumber());
      setAdditionalAttributesForImports(purchaseItem, containerItem);
      linesItems.add(getLine(container, container.getCompleteTs()));
    }

    purchaseItem.setLines(linesItems);
    return purchaseItem;
  }

  private void setAdditionalAttributesForImports(
      PurchaseItem purchaseItem, ContainerItem firstChildContainerItem) {
    purchaseItem.setImportInd(
        Objects.isNull(firstChildContainerItem.getImportInd())
            ? null
            : firstChildContainerItem.getImportInd());
    purchaseItem.setPoDCNumber(
        StringUtils.isEmpty(firstChildContainerItem.getPoDCNumber())
            ? null
            : firstChildContainerItem.getPoDCNumber());
    purchaseItem.setPoDcCountry(
        StringUtils.isEmpty(firstChildContainerItem.getPoDcCountry())
            ? null
            : firstChildContainerItem.getPoDcCountry());
  }
}
