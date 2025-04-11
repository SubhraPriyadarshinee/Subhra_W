package com.walmart.move.nim.receiving.endgame.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_USER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FTS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_MDM_ITEM_NUMBER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_MDM_ITEM_UPC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OFFER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SELLER_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.UTC_DATE_FORMAT;
import static java.lang.Long.parseLong;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.model.decant.FTSPublishMessage;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class DeliveryHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryHelper.class);

  @Autowired private ItemMDMService itemMDMService;
  @Autowired private DecantService decantService;
  @Autowired private Gson gson;

  public void processItemUpdateFromSSOT(
      Set<Long> itemNumbers, HttpHeaders httpHeaders, String baseDivisionCode) {
    Map<String, Object> itemDetails =
        itemMDMService.retrieveItemDetails(itemNumbers, httpHeaders, baseDivisionCode, true, true);
    List<DecantMessagePublishRequest> messagePublishRequests =
        prepareMessagePublishRequest(itemDetails);
    if (!messagePublishRequests.isEmpty()) {
      decantService.publishMessage(messagePublishRequests, httpHeaders);
    }
  }

  private List<DecantMessagePublishRequest> prepareMessagePublishRequest(
      Map<String, Object> itemResponseForSSOT) {
    List<Map<String, Object>> foundItems =
        ReceivingUtils.convertValue(
            itemResponseForSSOT.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM),
            new TypeReference<List<Map<String, Object>>>() {});
    return foundItems.stream().map(this::createMessagePublishRequest).collect(Collectors.toList());
  }

  private DecantMessagePublishRequest createMessagePublishRequest(Map<String, Object> item) {
    String sellerId = "0";
    Map<String, Object> offer =
        ReceivingUtils.convertValue(item.get(OFFER), new TypeReference<Map<String, Object>>() {});
    if (nonNull(offer)) {
      String offerSellerId =
          ReceivingUtils.convertValue(offer.get(SELLER_ID), new TypeReference<String>() {});
      sellerId = offerSellerId == null ? "0" : offerSellerId;
    }
    FTSPublishMessage ftsPublishMessage =
        FTSPublishMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .trackingId(item.get(ITEM_MDM_ITEM_UPC).toString())
            .userId(DEFAULT_USER)
            .eventType(FTS)
            .containerCreatedDate(new SimpleDateFormat(UTC_DATE_FORMAT).format(new Date()))
            .upc(item.get(ITEM_MDM_ITEM_UPC).toString())
            .itemNumber(parseLong(item.get(ITEM_MDM_ITEM_NUMBER).toString()))
            .sellerId(sellerId)
            .endTime(new SimpleDateFormat(UTC_DATE_FORMAT).format(new Date()))
            .build();
    return DecantMessagePublishRequest.builder()
        .message(gson.toJson(ftsPublishMessage))
        .scenario(FTS)
        .build();
  }
}
