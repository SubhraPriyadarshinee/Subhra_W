package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MARKET_FULFILLMENT_CENTER;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.UNDERSCORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.InventoryDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.gdm.PackType;
import com.walmart.move.nim.receiving.mfc.model.mixedpallet.MixedPalletAdjustmentTO;
import com.walmart.move.nim.receiving.mfc.model.mixedpallet.PalletItem;
import com.walmart.move.nim.receiving.mfc.model.mixedpallet.StockQuantityChange;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class MixedPalletRejectService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MixedPalletRejectService.class);
  private static final String DATE_FORMAT_WITH_TIMESTAMP = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private Gson gson;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;
  @Autowired private DecantService decantService;

  @Autowired private AsyncPersister asyncPersister;

  @Value("${mixed.pallet.reject.scenario:mfcExpiry}")
  private String mixedPalletRejectScenario;

  public MixedPalletRejectService() {
    this.gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
  }

  public void processMixedPalletReject(ASNDocument asnDocument, Long deliveryNumber) {
    List<DecantMessagePublishRequest> messagePublishRequests =
        createDecantMessagePublishRequests(asnDocument, deliveryNumber);
    LOGGER.info("Initiating Reject Posting for mixed pallet.");
    decantService.initiateMessagePublish(messagePublishRequests);
    LOGGER.info("Successfully publish mfc-assortment reject to ei for delivery={}", deliveryNumber);
  }

  private List<DecantMessagePublishRequest> createDecantMessagePublishRequests(
      ASNDocument asnDocument, Long deliveryNumber) {
    List<DecantMessagePublishRequest> decantMessagePublishRequests = new ArrayList<>();

    Map<String, List<PalletItem>> packItemsMap = new HashMap<>();
    String correlationId =
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId();
    if (Objects.isNull(asnDocument)) {
      asyncPersister.publishMetric(
          "mixed_pallet_not_found",
          "uwms-receiving",
          "storeInbound",
          "storeInbound_mixedPalletProcessing");
      LOGGER.error("Delivery {} is not for mixed pallet. Hence ignoring it.", deliveryNumber);
      return Collections.emptyList();
    }
    if (CollectionUtils.isNotEmpty(asnDocument.getPacks())) {

      asnDocument
          .getPacks()
          .stream()
          .filter(
              pack -> {
                PackType gdmPackType = PackType.getPackType(pack.getPackType());
                return StringUtils.equalsIgnoreCase(
                    gdmPackType.getPackType(), PackType.MIXED_PACK.getPackType());
              })
          .forEach(
              pack -> {
                if (CollectionUtils.isNotEmpty(pack.getItems())) {
                  List<Item> mfcItems =
                      pack.getItems()
                          .stream()
                          .filter(
                              item ->
                                  MARKET_FULFILLMENT_CENTER.equalsIgnoreCase(
                                      item.getReplenishmentCode()))
                          .collect(Collectors.toList());
                  mfcItems.forEach(
                      item -> {
                        InventoryDetail inventoryDetail = item.getInventoryDetail();

                        String payloadType =
                            mfcManagedConfig.isMultiRejectEnabled()
                                ? multiItemRejectPayload(packItemsMap, pack, item, inventoryDetail)
                                : singleItemRejectPayloads(
                                    asnDocument,
                                    decantMessagePublishRequests,
                                    correlationId,
                                    pack,
                                    item,
                                    inventoryDetail);
                        LOGGER.info("PayloadType={} for mixed pallet reject created", payloadType);
                      });
                }
              });
    }

    if (mfcManagedConfig.isMultiRejectEnabled() && MapUtils.isNotEmpty(packItemsMap)) {
      populateDecantMessagePublishRequest(
          asnDocument, packItemsMap, correlationId, decantMessagePublishRequests);
    }

    return decantMessagePublishRequests;
  }

  private void populateDecantMessagePublishRequest(
      ASNDocument asnDocument,
      Map<String, List<PalletItem>> packItemsMap,
      String correlationId,
      List<DecantMessagePublishRequest> decantMessagePublishRequests) {
    if (CollectionUtils.isEmpty(asnDocument.getPacks())) {
      LOGGER.warn(" Packs cannot be empty for processing the mixed-pallet-rejection");
      return;
    }
    asnDocument
        .getPacks()
        .stream()
        .filter(
            pack -> {
              PackType gdmPackType = PackType.getPackType(pack.getPackType());
              return StringUtils.equalsIgnoreCase(
                  gdmPackType.getPackType(), PackType.MIXED_PACK.getPackType());
            })
        .forEach(
            pack -> {
              if (Objects.nonNull(packItemsMap.get(pack.getPackNumber()))) {
                MixedPalletAdjustmentTO mixedPalletAdjustment =
                    MixedPalletAdjustmentTO.builder()
                        .containerId(
                            Objects.isNull(pack.getPalletNumber())
                                ? pack.getPackNumber()
                                : pack.getPalletNumber())
                        .items(packItemsMap.get(pack.getPackNumber()))
                        .sourceCreationTimestamp(
                            MFCUtils.getDateAsString(DATE_FORMAT_WITH_TIMESTAMP))
                        .userId(ReceivingConstants.DEFAULT_USER)
                        .correlationId(correlationId)
                        .build();
                Map<String, String> additionalHeader = getAdditionalHeadersForDecantReject();
                additionalHeader.put(
                    MSG_TIMESTAMP, MFCUtils.getDateAsString(DATE_FORMAT_WITH_TIMESTAMP));
                additionalHeader.put(
                    KEY,
                    new StringBuilder()
                        .append(asnDocument.getDelivery().getDeliveryNumber())
                        .append(UNDERSCORE)
                        .append(pack.getPackNumber())
                        .toString());
                DecantMessagePublishRequest decantMessagePublishRequest =
                    DecantMessagePublishRequest.builder()
                        .message(gson.toJson(mixedPalletAdjustment))
                        .additionalHeaders(additionalHeader)
                        .scenario(mixedPalletRejectScenario)
                        .build();
                decantMessagePublishRequests.add(decantMessagePublishRequest);
              }
            });
  }

  private String multiItemRejectPayload(
      Map<String, List<PalletItem>> packItemsMap,
      Pack pack,
      Item item,
      InventoryDetail inventoryDetail) {
    List<PalletItem> palletItems =
        packItemsMap.getOrDefault(pack.getPackNumber(), new ArrayList<>());
    palletItems.add(createPalletItem(item, inventoryDetail));
    packItemsMap.put(pack.getPackNumber(), palletItems);
    return "ITEM_ADDED";
  }

  private PalletItem createPalletItem(Item item, InventoryDetail inventoryDetail) {
    return PalletItem.builder()
        .gtin(item.getGtin())
        .invoiceNumber(
            Objects.nonNull(item.getInvoice()) ? item.getInvoice().getInvoiceNumber() : null)
        .quantityUom(inventoryDetail.getReportedUom())
        .stockStateChange(
            Arrays.asList(
                StockQuantityChange.builder()
                    .quantity(
                        mfcManagedConfig.getMixedPalletRejectMultiplier()
                            * inventoryDetail.getReportedQuantity())
                    .reasonCode(mfcManagedConfig.getMixedPalletReasonCode())
                    .reasonDesc(mfcManagedConfig.getMixedPalletRejectReasonDesc())
                    .location(mfcManagedConfig.getMixedPalletRejectLocation())
                    .currentState(mfcManagedConfig.getMixedPalletCurrentState())
                    .build()))
        .previousState(mfcManagedConfig.getMixedPalletPreviousState())
        .build();
  }

  private String singleItemRejectPayloads(
      ASNDocument asnDocument,
      List<DecantMessagePublishRequest> decantMessagePublishRequests,
      String correlationId,
      com.walmart.move.nim.receiving.core.model.gdm.v3.Pack pack,
      Item item,
      InventoryDetail inventoryDetail) {
    PalletItem palletitem = createPalletItem(item, inventoryDetail);
    MixedPalletAdjustmentTO mixedPalletAdjustment =
        MixedPalletAdjustmentTO.builder()
            .containerId(
                Objects.isNull(pack.getPalletNumber())
                    ? pack.getPackNumber()
                    : pack.getPalletNumber())
            .items(Arrays.asList(palletitem))
            .sourceCreationTimestamp(MFCUtils.getDateAsString(DATE_FORMAT_WITH_TIMESTAMP))
            .userId(ReceivingConstants.DEFAULT_USER)
            .correlationId(correlationId)
            .build();
    Map<String, String> additionalHeader = getAdditionalHeadersForDecantReject();
    additionalHeader.put(MSG_TIMESTAMP, MFCUtils.getDateAsString(DATE_FORMAT_WITH_TIMESTAMP));
    additionalHeader.put(
        KEY,
        new StringBuilder()
            .append(asnDocument.getDelivery().getDeliveryNumber())
            .append(UNDERSCORE)
            .append(pack.getPackNumber())
            .append(UNDERSCORE)
            .append(item.getGtin())
            .toString());
    DecantMessagePublishRequest decantMessagePublishRequest =
        DecantMessagePublishRequest.builder()
            .message(gson.toJson(mixedPalletAdjustment))
            .additionalHeaders(additionalHeader)
            .scenario(mixedPalletRejectScenario)
            .build();
    decantMessagePublishRequests.add(decantMessagePublishRequest);
    return "SINGLE_PAYLOAD_CREATED";
  }

  private Map<String, String> getAdditionalHeadersForDecantReject() {
    Map<String, String> headers = new HashMap<>();
    headers.put(EVENT_TYPE, mfcManagedConfig.getMixedPalletRemovalEvent());
    headers.put("originatorId", mfcManagedConfig.getMixedPalletRequestOriginator());
    headers.put(ReceivingConstants.FLOW_DESCRIPTOR, MFCConstant.MIXED_PALLET_REJECT);
    return headers;
  }
}
