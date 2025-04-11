package com.walmart.move.nim.receiving.endgame.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.DEFAULT_USER;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.model.AssortmentShipper;
import com.walmart.move.nim.receiving.endgame.model.DivertDestinationFromSlotting;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/**
 * * This is the utility layer for the Endgame receiving
 *
 * @author sitakant
 */
public final class EndGameUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameUtils.class);

  private EndGameUtils() {}

  public static long calculateLabelCount(Delivery delivery) {
    Map<String, Integer> upcCountMap = new HashMap<>();
    EndGameUtils.removeCancelledDocument(delivery.getPurchaseOrders())
        .forEach(
            purchaseOrder ->
                purchaseOrder
                    .getLines()
                    .forEach(
                        line -> {
                          if (upcCountMap.get(line.getItemDetails().getOrderableGTIN()) == null) {
                            upcCountMap.put(
                                line.getItemDetails().getOrderableGTIN(),
                                line.getOrdered().getQuantity());
                            // + line.getOvgThresholdLimit().getQuantity());
                          } else {
                            upcCountMap.put(
                                line.getItemDetails().getOrderableGTIN(),
                                upcCountMap.get(line.getItemDetails().getOrderableGTIN())
                                    + line.getOrdered().getQuantity());
                            // + line.getOvgThresholdLimit().getQuantity());
                          }
                        }));

    return upcCountMap.values().stream().mapToInt(Integer::intValue).sum();
  }

  public static Boolean retriveIsFTS(Long number, Map<String, Object> itemDetails, String ftsPath) {

    if (CollectionUtils.isEmpty(itemDetails)) {
      LOGGER.error(
          "No Item Information available [itemDetails={}] for [itemNumber={}] to get fts info",
          itemDetails,
          number);
      return Boolean.FALSE;
    }

    Object ftsAttributes = getJsonPath(new Gson().toJson(itemDetails), ftsPath);
    if (Objects.isNull(ftsAttributes)) {
      return Boolean.TRUE;
    }
    LOGGER.info("FTS [FTS={}] value for [itemNumber={}]", ftsAttributes, itemDetails.get("number"));
    return Boolean.parseBoolean(String.valueOf(ftsAttributes));
  }

  public static String getInboundPrepType(Map<String, Object> itemDetails) {
    if (CollectionUtils.isEmpty(itemDetails)) {
      LOGGER.error(
          "No Item Information available [itemDetails={}] for InboundPrepType", itemDetails);
      return EndgameConstants.EMPTY_STRING;
    }
    Object inboundPrepType =
        getJsonPath(new Gson().toJson(itemDetails), EndgameConstants.INBOUND_PREP_TYPE_PATH);
    if (Objects.isNull(inboundPrepType)) {
      return EndgameConstants.EMPTY_STRING;
    }
    LOGGER.info("InboundPrepType [InboundPrepType={}]", inboundPrepType);
    return String.valueOf(inboundPrepType);
  }

  public static Object getJsonPath(String toJson, String path) {
    try {
      return JsonPath.parse(toJson).read(path);
    } catch (Exception e) {
      LOGGER.error("Path Not found in json {} : {}", path, e.getMessage());
      return null;
    }
  }
  /**
   * This is a utility method to parse string rotate date to java.util.Date
   *
   * @param strRotateDate
   * @return
   */
  public static Date parseRotateDate(String strRotateDate) {
    if (ObjectUtils.isEmpty(strRotateDate)) {
      return null;
    }
    DateFormat dateFormat = new SimpleDateFormat(EndgameConstants.UTC_DATE_FORMAT);
    dateFormat.setTimeZone(TimeZone.getTimeZone(EndgameConstants.UTC_TIME_ZONE));
    Date rotateDate;
    try {
      rotateDate = dateFormat.parse(strRotateDate);
    } catch (ParseException e) {
      LOGGER.error(
          EndgameConstants.EXCEPTION_HANDLER_ERROR_MESSAGE, ExceptionUtils.getStackTrace(e));
      return null;
    }
    return rotateDate;
  }

  /**
   * This is a utility method to extract out rotate date from delivery meta data object.
   *
   * @param deliveryMetaData
   * @param itemNumber
   * @return
   */
  public static String getItemAttributeFromDeliveryMetaData(
      DeliveryMetaData deliveryMetaData, String itemNumber, String attribute) {
    if (Objects.nonNull(deliveryMetaData)
        && !CollectionUtils.isEmpty(deliveryMetaData.getItemDetails())) {
      LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap =
          deliveryMetaData.getItemDetails();
      LinkedTreeMap<String, String> itemDetails = itemDetailsMap.get(itemNumber);
      if (!CollectionUtils.isEmpty(itemDetails) && itemDetails.containsKey(attribute)) {
        return itemDetails.get(attribute);
      }
    }
    return null;
  }

  public static List<AssortmentShipper> retriveAssortmentShipper(
      Map<String, Object> itemDetails, String assortmentPath) {

    Gson gson = new Gson();

    Object result = getJsonPath(gson.toJson(itemDetails), assortmentPath);

    if (Objects.isNull(result)) {
      return new ArrayList<AssortmentShipper>();
    }

    String assortmentShipper = gson.toJson(result);
    List<AssortmentShipper> assortmentShippers =
        gson.fromJson(
            assortmentShipper, new TypeToken<ArrayList<AssortmentShipper>>() {}.getType());
    return assortmentShippers;
  }

  public static Message<String> setDefaultHawkeyeHeaders(
      String payload, String topicName, String userId, String key) {
    if (Objects.isNull(payload)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_KAFKA_PAYLOAD, "MessageBody cannot be null ");
    }
    String corelationId =
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId();

    Message<String> message =
        getHawkeyeMessageBuilder(payload, topicName, userId, corelationId, key).build();
    return message;
  }

  public static Map<String, Object> getHawkeyeHeaderMap(
      String topicName,
      String key,
      Integer facilityNum,
      String facilityCountryCode,
      String messageId) {
    String corelationId =
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId();
    Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.TOPIC, topicName);
    headers.put(KafkaHeaders.MESSAGE_KEY, key);
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum);
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode);
    headers.put(ReceivingConstants.USER_ID_HEADER_KEY, DEFAULT_USER);
    headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, corelationId);
    headers.put(ReceivingConstants.CORRELATION_ID, corelationId);
    headers.put(ReceivingConstants.MESSAGE_ID_HEADER, messageId);
    return headers;
  }

  private static MessageBuilder<String> getHawkeyeMessageBuilder(
      String payload, String topicName, String userId, String corelationId, String key) {
    return MessageBuilder.withPayload(payload)
        .setHeader(KafkaHeaders.TOPIC, topicName)
        .setHeader(KafkaHeaders.MESSAGE_KEY, key)
        .setHeader(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum())
        .setHeader(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode())
        .setHeader(ReceivingConstants.USER_ID_HEADER_KEY, userId)
        .setHeader(ReceivingConstants.CORRELATION_ID_HEADER_KEY, corelationId)
        .setHeader(ReceivingConstants.CORRELATION_ID, corelationId)
        .setHeader(ReceivingConstants.MESSAGE_ID_HEADER, UUID.randomUUID().toString());
  }

  public static Message<String> setDefaultHawkeyeHeaders(
      String payload,
      String topicName,
      String userId,
      Map<String, Object> extraHeaders,
      String key) {

    if (Objects.isNull(payload)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_KAFKA_PAYLOAD, "MessageBody cannot be null ");
    }

    String corelationId =
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId();

    MessageBuilder<String> kafkaMessageBuilder =
        getHawkeyeMessageBuilder(payload, topicName, userId, corelationId, key);

    for (Map.Entry<String, Object> header : extraHeaders.entrySet()) {
      kafkaMessageBuilder.setHeader(header.getKey(), header.getValue());
    }

    LOGGER.info("Extra headers are placed in message. headers={}", extraHeaders);

    Message<String> message = kafkaMessageBuilder.build();

    LOGGER.info(
        "CorelationId for kafka is set to {} for topic = {} and userName = {} with payload = {}",
        corelationId,
        topicName,
        userId,
        payload);

    return message;
  }

  public static Map<String, Object> createMaaSHeaders(String userId) {
    Map<String, Object> headerMap = new HashMap<>();
    headerMap.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    headerMap.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
    headerMap.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    headerMap.put(ReceivingConstants.USER_ID_HEADER_KEY, userId);
    return headerMap;
  }

  public static Map<String, Object> createDeliveryCloseHeaders(DeliveryInfo deliveryInfo) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(EndgameConstants.DELIVERY_NUMBER, deliveryInfo.getDeliveryNumber());
    headers.put(EndgameConstants.DELIVERY_STATUS, deliveryInfo.getDeliveryStatus());
    return headers;
  }

  public static List<PurchaseOrder> removeCancelledDocument(List<PurchaseOrder> purchaseOrderList) {
    return purchaseOrderList
        .stream()
        .map(EndGameUtils::removeCancelledPOLines)
        .filter(EndGameUtils::isNonEmptyPurchaseOrder)
        .collect(Collectors.toList());
  }

  public static PurchaseOrder removeCancelledPOLines(PurchaseOrder purchaseOrder) {
    List<PurchaseOrderLine> poLines =
        purchaseOrder
            .getLines()
            .stream()
            .filter(
                poLine -> {
                  boolean isCancelled =
                      ReceivingUtils.isPOLineCancelled(
                          purchaseOrder.getPoStatus(), poLine.getPoLineStatus());
                  if (isCancelled) {
                    LOGGER.warn(
                        "PO Number = {} And LineNumber = {} is already cancelled ",
                        purchaseOrder.getPoNumber(),
                        poLine.getPoLineNumber());
                  }
                  return !isCancelled;
                })
            .collect(Collectors.toList());

    purchaseOrder.setLines(poLines);
    return purchaseOrder;
  }

  private static boolean isNonEmptyPurchaseOrder(PurchaseOrder purchaseOrder) {
    return purchaseOrder.getLines().size() > 0;
  }

  public static Integer retrieveVendorInfos(PurchaseOrder po, PurchaseOrderLine line) {

    if (!Objects.isNull(line.getVendor()) && !Objects.isNull(line.getVendor().getNumber())) {
      return line.getVendor().getNumber();
    }

    if (!Objects.isNull(po.getVendor()) && !Objects.isNull(po.getVendor().getNumber())) {
      return po.getVendor().getNumber();
    }

    return 0;
  }

  /**
   * Enrich Walmart default seller id if not available in purchase orders.
   *
   * @param purchaseOrderList
   * @param walmartSellerId
   * @param samsSellerId
   */
  public static void enrichDefaultSellerIdInPurchaseOrders(
      List<PurchaseOrder> purchaseOrderList, String walmartSellerId, String samsSellerId) {
    purchaseOrderList.forEach(
        purchaseOrder -> {
          if (ObjectUtils.isEmpty(purchaseOrder.getSellerId())) {
            purchaseOrder.setSellerId(
                getDefaultSellerId(
                    purchaseOrder.getBaseDivisionCode(), walmartSellerId, samsSellerId));
          }
        });
  }

  public static void enrichDefaultSellerIdInSlottingDivertResponse(
      List<DivertDestinationFromSlotting> divertDestinationFromSlottingList,
      String defaultSellerId) {
    divertDestinationFromSlottingList.forEach(
        divertDestinationFromSlotting -> {
          if (ObjectUtils.isEmpty(divertDestinationFromSlotting.getSellerId())) {
            divertDestinationFromSlotting.setSellerId(defaultSellerId);
          }
        });
  }

  public static Boolean isMultipleSellerIdInPurchaseOrders(List<PurchaseOrder> purchaseOrderList) {
    Set<String> sellerIdSet =
        purchaseOrderList.stream().map(PurchaseOrder::getSellerId).collect(Collectors.toSet());
    return sellerIdSet.size() > EndgameConstants.ONE ? Boolean.TRUE : Boolean.FALSE;
  }

  public static Boolean isMultipleSellerIdInSlottingDestinations(
      List<SlottingDestination> slottingDestinationList) {
    Set<String> sellerIdSet =
        slottingDestinationList
            .stream()
            .map(SlottingDestination::getSellerId)
            .collect(Collectors.toSet());
    return sellerIdSet.size() > EndgameConstants.ONE ? Boolean.TRUE : Boolean.FALSE;
  }
  /**
   * Checks if the purchase order is a WFS purchase order.
   *
   * @param purchaseOrder
   * @param walmartSellerId
   * @param samsSellerId
   * @return
   */
  public static Boolean isWFSPurchaseOrder(
      PurchaseOrder purchaseOrder, String walmartSellerId, String samsSellerId) {
    if (!ObjectUtils.isEmpty(purchaseOrder.getSellerId())
        && !purchaseOrder
            .getSellerId()
            .equalsIgnoreCase(
                getDefaultSellerId(
                    purchaseOrder.getBaseDivisionCode(), walmartSellerId, samsSellerId))) {
      return Boolean.TRUE;
    }
    return Boolean.FALSE;
  }

  /**
   * This method is responsible for sorting by MABD.
   *
   * @param purchaseOrders
   */
  public static void sortDeliveryDocumentByMustArriveByDate(List<PurchaseOrder> purchaseOrders) {
    DateFormat dateFormat = new SimpleDateFormat(EndgameConstants.SIMPLE_DATE);
    dateFormat.setTimeZone(TimeZone.getTimeZone(EndgameConstants.UTC_TIME_ZONE));
    Comparator<PurchaseOrder> compareByMustArriveByDate =
        (PurchaseOrder d1, PurchaseOrder d2) -> {
          try {
            return dateFormat
                .parse(d1.getDates().getMabd())
                .compareTo(dateFormat.parse(d2.getDates().getMabd()));
          } catch (ParseException e) {
            LOGGER.error(
                EndgameConstants.EXCEPTION_HANDLER_ERROR_MESSAGE,
                io.strati.libs.commons.lang.exception.ExceptionUtils.getStackTrace(e));
            return 0;
          }
        };
    Collections.sort(purchaseOrders, compareByMustArriveByDate);
  }

  /**
   * This method is responsible for filtering POs with old MABD.
   *
   * @param purchaseOrders
   */
  public static List<PurchaseOrder> removePurchaseOrdersWithOldMustArriveByDate(
      List<PurchaseOrder> purchaseOrders, int mabdRestricDays) {
    DateFormat dateFormat = new SimpleDateFormat(EndgameConstants.SIMPLE_DATE);
    dateFormat.setTimeZone(TimeZone.getTimeZone(EndgameConstants.UTC_TIME_ZONE));
    Date restrictDate =
        Date.from(
            LocalDate.now()
                .minusDays(mabdRestricDays)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());
    return purchaseOrders
        .stream()
        .filter(isValidMABDDate(dateFormat, restrictDate))
        .collect(Collectors.toList());
  }

  private static Predicate<PurchaseOrder> isValidMABDDate(
      DateFormat dateFormat, Date restrictDate) {
    return purchaseOrder -> {
      try {
        return dateFormat.parse(purchaseOrder.getDates().getMabd()).after(restrictDate);
      } catch (ParseException e) {
        LOGGER.error(
            EndgameConstants.EXCEPTION_HANDLER_ERROR_MESSAGE,
            io.strati.libs.commons.lang.exception.ExceptionUtils.getStackTrace(e));
        return false;
      }
    };
  }

  public static String getDefaultSellerId(
      String baseDivisionCode, String walmartSellerId, String samsSellerId) {
    return hasLength(baseDivisionCode) && baseDivisionCode.equalsIgnoreCase(SAMS_BASE_DIVISION_CODE)
        ? samsSellerId
        : walmartSellerId;
  }

  public static String getBaseDivisionCode(List<PurchaseOrder> purchaseOrders) {
    if (!isEmpty(purchaseOrders)) {
      return purchaseOrders.get(0).getBaseDivisionCode();
    }
    return WM_BASE_DIVISION_CODE;
  }

  public static ContainerTag createContainerTag(String value) {
    return new ContainerTag(value, ReceivingConstants.CONTAINER_SET);
  }

  public static boolean isVnpkPalletItem(TenantSpecificConfigReader tenantSpecificConfigReader,
                                         PurchaseOrderLine purchaseOrderLine, String baseDivCode) {
    LOGGER.info("Vnpk Pallet Feature from CCM :{} and baseDivCode:{}, purchaseOrderLine:{}",
            tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED), baseDivCode, purchaseOrderLine);
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED)
            && ReceivingConstants.SAMS_BASE_DIVISION_CODE.equalsIgnoreCase(baseDivCode)
            && Objects.nonNull(purchaseOrderLine.getVnpk().getQuantity())
            && Objects.nonNull(purchaseOrderLine.getItemDetails().getPalletTi())
            && Objects.nonNull(purchaseOrderLine.getItemDetails().getPalletHi())
            && purchaseOrderLine.getVnpk().getQuantity() == purchaseOrderLine.getItemDetails().getPalletTi() *
            purchaseOrderLine.getItemDetails().getPalletHi() * purchaseOrderLine.getVnpk().getQuantity();
  }
}
