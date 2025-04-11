package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.Triplet;
import com.walmart.move.nim.receiving.core.model.decant.*;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class DecantService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DecantService.class);

  private static final String BASE_DIV_CODE = "supplyItems.financials.baseDivisionCode";
  private static final String FIN_REPORTING_GRP =
      "supplyItems.financials.financialReportingGroupCode";
  private static final String DEPT_NUMBER = "supplyItems.financials.deptNumber";
  private static final String ITEM_DESCRIPTION = "supplyItems.siInfo.description";
  private static final String WAREHOUSE_PACK_QTY = "supplyItems.pack.warehousePackQuantity";
  private static final String VENDOR_PACK_QTY = "supplyItems.pack.orderableQuantity";
  private static final String WAREHOUSE_PACK_SELL = "supplyItems.pack.warehousePackSell";
  private static final String ORDERABLE_GTIN = "supplyItems.itemIdentifier.orderablePackGtin";
  private static final String CONSUMABLE_GTIN = "supplyItems.itemIdentifier.consumableGtin";
  private static final String ITEM_NUMBER = "supplyItems.itemIdentifier.supplyItemNbr";
  private static final String ORDERABLE_IND = "gtins.isOrderableInd";
  private static final String WEIGHT = "gtins.gtinPhysical.weight";
  private static final String CUBE = "gtins.gtinPhysical.cube";
  private static final String ITEM_URL_PATH = "/api/scans/v2/item?enrichWithMFCAttribute=";
  private static final String API_PUBLISH_URL_PATH = "/api/publish/v2";

  @Value("${enrich.mfc.attribute.enabled:false}")
  private boolean enrichWithMFCAttributeEnabled;

  @Autowired private RetryableRestConnector retryableRestConnector;

  @Autowired private RetryService retryService;

  @ManagedConfiguration private AppConfig appConfig;

  private Gson gson;

  public DecantService() {
    this.gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
  }

  public ItemInfos retrieveItem(String gtin) {

    DecantItemRequest decantItemRequest =
        DecantItemRequest.builder()
            .facilityNumber(TenantContext.getFacilityNum().toString())
            .source("UBER")
            .type("GTIN")
            .responseGroup(
                Arrays.asList(
                    BASE_DIV_CODE,
                    FIN_REPORTING_GRP,
                    DEPT_NUMBER,
                    ITEM_DESCRIPTION,
                    WAREHOUSE_PACK_QTY,
                    VENDOR_PACK_QTY,
                    WAREHOUSE_PACK_SELL,
                    WAREHOUSE_PACK_SELL,
                    ORDERABLE_GTIN,
                    CONSUMABLE_GTIN,
                    ITEM_NUMBER,
                    ORDERABLE_IND,
                    WEIGHT,
                    CUBE))
            .ids(Arrays.asList(gtin))
            .build();

    LOGGER.info("Requesting decant server for item . RequestPayload = {}", decantItemRequest);
    ResponseEntity<DecantItemResponse> responseResponseEntity = null;
    try {
      StringBuilder urlBuilder =
          new StringBuilder(appConfig.getDecantBaseUrl())
              .append(ITEM_URL_PATH)
              .append(enrichWithMFCAttributeEnabled);
      responseResponseEntity =
          retryableRestConnector.post(
              urlBuilder.toString(),
              decantItemRequest,
              ReceivingUtils.getHeaders(),
              DecantItemResponse.class);
    } catch (Exception e) {
      LOGGER.error("Unable to retrieve item from Decant", e);
      responseResponseEntity = new ResponseEntity<>(null, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    LOGGER.info(
        "Got response from decant server . ResponseCode = {} ,  ResponsePayload = {}",
        responseResponseEntity.getStatusCode(),
        responseResponseEntity.getBody());

    return processItem(gtin, responseResponseEntity.getBody());
  }

  private ItemInfos processItem(String gtin, DecantItemResponse decantResponse) {

    if (Objects.nonNull(decantResponse) && Objects.isNull(decantResponse.getPayload())) {
      decantResponse.setPayload(new ArrayList<>());
    }

    if (Objects.isNull(decantResponse)) {
      decantResponse = new DecantItemResponse();
      decantResponse.setPayload(new ArrayList<>());
    }

    PayloadItem payloadItem =
        decantResponse
            .getPayload()
            .stream()
            .findAny()
            .orElse(
                PayloadItem.builder()
                    .supplyItems(
                        Collections.singletonList(
                            SupplyItem.builder()
                                .itemIdentifier(null)
                                .pack(null)
                                .financials(null)
                                .siInfo(null)
                                .build()))
                    .gtins(new ArrayList<>())
                    .build());

    if (Objects.isNull(payloadItem.getGtins())) {
      payloadItem.setGtins(new ArrayList<>());
    }
    GtinsItem gtinsItem =
        payloadItem
            .getGtins()
            .stream()
            .filter(orderableGtin -> orderableGtin.isOrderableInd())
            .map(gtinsItem1 -> defaultGtin(gtin, gtinsItem1))
            .findFirst()
            .orElse(defaultGtin(gtin, null));

    SupplyItem supplyItem = payloadItem.getSupplyItems().get(0);
    Triplet<String, String, Long> _itemIdentifier =
        sanitizeItemIdentifier(supplyItem.getItemIdentifier(), gtin);
    Triplet<Integer, Integer, Double> _packs = sanitizePacks(supplyItem.getPack());

    String itemDescription =
        Objects.isNull(supplyItem.getSiInfo())
                || Objects.isNull(supplyItem.getSiInfo().getDescription())
            ? StringUtils.EMPTY
            : supplyItem.getSiInfo().getDescription().getTextValue();

    return ItemInfos.builder()
        .itemNumber(_itemIdentifier.getValue3())
        .baseDivisionCode(
            Objects.isNull(supplyItem.getFinancials())
                ? "US"
                : supplyItem.getFinancials().getBaseDivisionCode())
        .orderableGtin(_itemIdentifier.getValue1())
        .consumableGtin(_itemIdentifier.getValue2())
        .deptNumber(
            Objects.isNull(supplyItem.getFinancials())
                ? -1
                : supplyItem.getFinancials().getDeptNumber())
        .descriptions(Arrays.asList(itemDescription))
        .financialReportingGroupCode(
            Objects.isNull(supplyItem.getFinancials())
                    || Objects.isNull(supplyItem.getFinancials().getFinancialReportingGroupCode())
                ? "WM"
                : supplyItem.getFinancials().getFinancialReportingGroupCode())
        .vendorNumber(0)
        .vnpkcbqty(gtinsItem.getGtinPhysical().getCube().get(0).getAmount())
        .vnpkcbuomcd(gtinsItem.getGtinPhysical().getCube().get(0).getUom())
        .vnpkQty(_packs.getValue1())
        .whpkQty(_packs.getValue2())
        .whpkSell(_packs.getValue3())
        .vnpkWgtQty(gtinsItem.getGtinPhysical().getWeight().get(0).getAmount())
        .vnpkWgtUom(gtinsItem.getGtinPhysical().getWeight().get(0).getUom())
        .build();
  }

  private Triplet<Integer, Integer, Double> sanitizePacks(Pack pack) {
    Triplet triplet = new Triplet();
    triplet.setValue1(
        Objects.isNull(pack) || Objects.isNull(pack.getOrderableQuantity())
            ? -1
            : pack.getOrderableQuantity().getAmount());
    triplet.setValue2(
        Objects.isNull(pack) || Objects.isNull(pack.getWarehousePackQuantity())
            ? -1
            : pack.getWarehousePackQuantity().getAmount());
    triplet.setValue3(
        Objects.isNull(pack) || Objects.isNull(pack.getWarehousePackSell())
            ? -1.0
            : pack.getWarehousePackQuantity().getAmount());
    return triplet;
  }

  private Triplet<String, String, Long> sanitizeItemIdentifier(
      ItemIdentifier itemIdentifier, String gtin) {
    Triplet triplet = new Triplet();
    triplet.setValue1(
        Objects.isNull(itemIdentifier) || Objects.isNull(itemIdentifier.getOrderablePackGtin())
            ? gtin
            : itemIdentifier.getOrderablePackGtin());
    triplet.setValue2(
        Objects.isNull(itemIdentifier) || Objects.isNull(itemIdentifier.getConsumableGtin())
            ? gtin
            : itemIdentifier.getConsumableGtin());
    triplet.setValue3(
        Objects.isNull(itemIdentifier) || Objects.isNull(itemIdentifier.getSupplyItemNbr())
            ? -1
            : itemIdentifier.getSupplyItemNbr());
    return triplet;
  }

  private GtinsItem defaultGtin(String gtin, GtinsItem gtinsItem) {
    if (Objects.isNull(gtinsItem)) {
      gtinsItem = new GtinsItem();
    }

    GtinPhysical gtinPhysical = new GtinPhysical();

    CubeItem cubeItem = new CubeItem();
    cubeItem.setUom("CF");
    cubeItem.setAmount(0.0f);

    WeightItem weightItem = new WeightItem();
    weightItem.setAmount(0.0f);
    weightItem.setUom("LB");

    gtinPhysical.setCube(Arrays.asList(cubeItem));
    gtinPhysical.setWeight(Arrays.asList(weightItem));

    if (Objects.isNull(gtinsItem.getGtinPhysical())
        || Objects.isNull(gtinsItem.getGtinPhysical().getCube())
        || gtinsItem.getGtinPhysical().getCube().isEmpty()
        || Objects.isNull(gtinsItem.getGtinPhysical().getWeight())
        || gtinsItem.getGtinPhysical().getWeight().isEmpty()) {
      gtinsItem.setOrderableInd(Boolean.TRUE);
      gtinsItem.setGtinPhysical(gtinPhysical);
    }

    LOGGER.info("Successfully Created Default GTIN Item for gtin={}", gtin);
    return gtinsItem;
  }

  public void initiateMessagePublish(List<DecantMessagePublishRequest> messagePublishRequests) {

    if (CollectionUtils.isNotEmpty(messagePublishRequests)) {
      LOGGER.info("Initiating message publish");

      HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
      publish(messagePublishRequests, httpHeaders);
    }
  }

  public void publishMessage(
      List<DecantMessagePublishRequest> messagePublishRequests, HttpHeaders httpHeaders) {
    publish(messagePublishRequests, httpHeaders);
  }

  private void publish(
      List<DecantMessagePublishRequest> messagePublishRequests, HttpHeaders httpHeaders) {
    String url =
        new StringBuilder(appConfig.getDecantBaseUrl()).append(API_PUBLISH_URL_PATH).toString();
    RetryEntity eventRetryEntity =
        retryService.putForRetries(
            url,
            HttpMethod.POST,
            httpHeaders,
            gson.toJson(messagePublishRequests),
            RetryTargetFlow.DECANT_MESSAGE_PUBLISH,
            EventTargetStatus.SUCCESSFUL);
    try {
      ResponseEntity<String> response =
          retryableRestConnector.post(url, messagePublishRequests, httpHeaders, String.class);
    } catch (Exception e) {
      LOGGER.error("Error while processing reject posting request for container", e);
      eventRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
      retryService.save(eventRetryEntity);
      LOGGER.info("Retry entry done {}", eventRetryEntity);
    }
  }
}
