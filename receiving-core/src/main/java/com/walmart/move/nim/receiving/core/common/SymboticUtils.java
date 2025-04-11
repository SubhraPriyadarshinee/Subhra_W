package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AVAILABLE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.symbotic.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.logging.log4j2.util.Strings;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public class SymboticUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(SymboticUtils.class);

  private SymboticUtils() {}

  public static Map<String, Object> getSymPutawayMessageHeader(
      HttpHeaders headers, String system, String eventType) {

    Map<String, Object> messageHeaders = new HashMap<>();
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    if (!StringUtils.isEmpty(userId)) messageHeaders.put(ReceivingConstants.JMS_USER_ID, userId);
    String derivedCorrelationId = headers.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    String correlationId =
        !StringUtils.isEmpty(derivedCorrelationId)
            ? derivedCorrelationId
            : UUID.randomUUID().toString();

    messageHeaders.put(ReceivingConstants.JMS_CORRELATION_ID, correlationId);
    messageHeaders.put(
        ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    messageHeaders.put(
        ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    messageHeaders.put(ReceivingConstants.SYM_MESSAGE_ID_HEADER, correlationId);
    messageHeaders.put(ReceivingConstants.SYM_PUTAWAY_ORDER_NO, correlationId);
    messageHeaders.put(ReceivingConstants.SYM_SYSTEM_KEY, system);
    messageHeaders.put(ReceivingConstants.SYM_EVENT_TYPE_KEY, eventType);
    messageHeaders.put(ReceivingConstants.CORRELATION_ID, correlationId);
    messageHeaders.put(
        ReceivingConstants.SYM_MSG_TIMESTAMP, ReceivingUtils.dateConversionToUTC(new Date()));
    messageHeaders.put(ReceivingConstants.SYM_VERSION_KEY, ReceivingConstants.SYM_VERSION_VALUE);
    messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.ATLAS_RECEIVING);
    messageHeaders.put(ReceivingConstants.JMS_REQUESTOR_ID, ReceivingConstants.RDCSYM);

    return messageHeaders;
  }

  public static boolean isPrimeSlot(String slotType) {
    return ReceivingConstants.PRIME_SLOT_TYPE.equalsIgnoreCase(slotType);
  }

  private static String formatRotateDate(Date date) {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    return simpleDateFormat.format(date);
  }

  /**
   * This method is used to create putawayAddMessage during instruction flow for different
   * SYMFreightType For SYMFreightType XDOCK, SymLabelType will be ROUTING and FreightType will be
   * XDOCK
   *
   * @param containerItem
   * @param deliveryDocument
   * @param instruction
   * @param receivedContainer
   * @param symFreightType
   * @return
   */
  public static SymPutawayMessage createPutawayAddMessage(
      ContainerItem containerItem,
      DeliveryDocument deliveryDocument,
      Instruction instruction,
      ReceivedContainer receivedContainer,
      SymFreightType symFreightType) {
    SymPutawayMessage payload = new SymPutawayMessage();
    SymPutawayItem content =
        createSymPutawayItem(containerItem, deliveryDocument, instruction, receivedContainer);
    payload.setAction(ReceivingConstants.PUTAWAY_ADD_ACTION);
    payload.setTrackingId(receivedContainer.getLabelTrackingId());
    if (SymFreightType.SSTK.equals(symFreightType)) {
      payload.setLabelType(SymLabelType.PALLET.toString());
      payload.setFreightType(SymFreightType.SSTK.toString());
      payload.setShippingLabelId(receivedContainer.getLabelTrackingId());
    } else if (SymFreightType.DA.equals(symFreightType)) {
      if (Objects.nonNull(receivedContainer.getInventoryLabelType())
          && InventoryLabelType.DA_CON_AUTOMATION_SLOTTING.equals(
              receivedContainer.getInventoryLabelType())) {
        payload.setLabelType(SymLabelType.PALLET.toString());
        payload.setFreightType(SymFreightType.DA.toString());
        payload.setShippingLabelId(EMPTY_STRING);
      } else {
        payload.setLabelType(SymLabelType.ROUTING.toString());
        payload.setFreightType(SymFreightType.DA.toString());
        payload.setShippingLabelId(receivedContainer.getLabelTrackingId());
      }
    } else if (SymFreightType.XDOCK.equals(symFreightType)) {
      payload.setLabelType(SymLabelType.SHIPPING.toString());
      payload.setFreightType(SymFreightType.XDOCK.toString());
      payload.setShippingLabelId(receivedContainer.getLabelTrackingId());
      LOGGER.info(
          "[XDK] setting symLabelType = '{}' and symFreightType = '{}' for trackingId '{}' For symFreightType XDOCK ",
          payload.getLabelType(),
          payload.getFreightType(),
          containerItem.getTrackingId());
    }

    payload.setInventoryStatus(AVAILABLE);
    payload.setContents(Collections.singletonList(content));
    return payload;
  }

  private static SymPutawayItem createSymPutawayItem(
      ContainerItem containerItem,
      DeliveryDocument deliveryDocument,
      Instruction instruction,
      ReceivedContainer receivedContainer) {

    DeliveryDocumentLine documentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Date rotateDate =
        Objects.nonNull(containerItem.getRotateDate()) ? containerItem.getRotateDate() : new Date();
    SymPutawayItem content = new SymPutawayItem();
    content.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    content.setPurchaseReferenceLineNumber(documentLine.getPurchaseReferenceLineNumber());
    content.setPoTypeCode(deliveryDocument.getPoTypeCode());
    content.setPoEvent(documentLine.getEvent());
    content.setDeliveryNumber(deliveryDocument.getDeliveryNumber());
    content.setItemNumber(documentLine.getItemNbr());
    content.setChildItemNumber(getChildContainerItemNum(instruction));
    content.setBaseDivisionCode(deliveryDocument.getBaseDivisionCode());
    content.setFinancialReportingGroupCode(deliveryDocument.getFinancialReportingGroup());
    if (Strings.isNotBlank(deliveryDocument.getVendorNumber())) {
      content.setVendorNumber(Integer.valueOf(deliveryDocument.getVendorNumber()));
    }
    content.setQuantity(instruction.getReceivedQuantity());
    content.setQuantityUOM(instruction.getReceivedQuantityUOM());
    content.setPackagedAsUom(containerItem.getPackagedAsUom());
    content.setRotateDate(formatRotateDate(rotateDate));
    content.setTi(documentLine.getPalletTie());
    content.setHi(documentLine.getPalletHigh());
    content.setDeptNumber(containerItem.getDeptNumber());
    String primeSlot = org.apache.commons.lang3.StringUtils.EMPTY;
    if (CollectionUtils.isNotEmpty(receivedContainer.getDestinations())) {
      primeSlot = receivedContainer.getDestinations().get(0).getSlot();

    } else {
      LOGGER.warn(
          "Destinations is empty or null for trackingId= {}, so setting prime slot as empty.",
          containerItem.getTrackingId());
    }
    content.setPrimeSlotId(primeSlot);
    content.setDistributions(createRdcDistributions(receivedContainer));
    return content;
  }

  private static Long getChildContainerItemNum(Instruction instruction) {
    return CollectionUtils.isNotEmpty(instruction.getChildContainers())
            && CollectionUtils.isNotEmpty(instruction.getChildContainers().get(0).getContents())
        ? instruction.getChildContainers().get(0).getContents().get(0).getItemNbr()
        : ReceivingConstants.SYM_DEFAULT_ITEM_NUM;
  }

  private static List<SymPutawayDistribution> createRdcDistributions(
      ReceivedContainer receivedContainer) {
    List<SymPutawayDistribution> symPutawayDistributions = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(receivedContainer.getDestinations())) {
      receivedContainer
          .getDestinations()
          .forEach(
              destination -> {
                symPutawayDistributions.add(
                    getDistributionFromDestination(destination, receivedContainer));
              });
    }
    return symPutawayDistributions;
  }

  private static SymPutawayDistribution getDistributionFromDestination(
      Destination destination, ReceivedContainer receivedContainer) {
    SymPutawayDistribution symPutawayDistribution =
        SymPutawayDistribution.builder()
            .storeId(
                org.apache.commons.lang.StringUtils.isNotBlank(destination.getStore())
                    ? destination.getStore()
                    : ReceivingConstants.SYM_DEFAULT_STORE_ID)
            .build();

    if (Objects.nonNull(receivedContainer.getInventoryLabelType())
        && InventoryLabelType.DA_CON_AUTOMATION_SLOTTING.equals(
            receivedContainer.getInventoryLabelType())) {
      symPutawayDistribution.setZone(EMPTY_STRING);
    } else {
      symPutawayDistribution.setZone(receivedContainer.getStorezone());
      symPutawayDistribution.setAisle(receivedContainer.getAisle());
      symPutawayDistribution.setAllocQty(receivedContainer.getPack());
    }
    return symPutawayDistribution;
  }

  public static SymPutawayMessage createSymPutawayDeleteMessage(String trackingId) {
    return SymPutawayMessage.builder()
        .action(ReceivingConstants.PUTAWAY_DELETE_ACTION)
        .trackingId(trackingId)
        .build();
  }

  public static SymPutawayMessage createHawkeyePalletAdjustmentMessage(
      ContainerItem containerItem, String trackingId, Integer quantity) {

    SymPutawayItem symPutawayItem =
        SymPutawayItem.builder()
            .itemNumber(containerItem.getItemNumber())
            .baseDivisionCode(containerItem.getBaseDivisionCode())
            .financialReportingGroupCode(containerItem.getFinancialReportingGroupCode())
            .quantity(quantity)
            .quantityUOM(ReceivingConstants.Uom.VNPK)
            .build();

    return SymPutawayMessage.builder()
        .action(ReceivingConstants.PUTAWAY_UPDATE_ACTION)
        .trackingId(trackingId)
        .contents(Collections.singletonList(symPutawayItem))
        .build();
  }

  public static boolean isValidForSymPutaway(
      String asrsAlignment, List<String> validAsrsAlignmentList, String slotType) {
    return Objects.nonNull(asrsAlignment)
        && CollectionUtils.isNotEmpty(validAsrsAlignmentList)
        && validAsrsAlignmentList.contains(asrsAlignment)
        && isPrimeSlot(slotType);
  }

  /**
   * This method is used to create putawayAddMessage during reprocess putaway request through api
   * for different SYMFreightType For SYMFreightType XDOCK, SymLabelType will be ROUTING and
   * FreightType will be XDOCK
   *
   * @param container
   * @param containerItem
   * @param freightType
   * @return
   */
  public static SymPutawayMessage createPutawayAddMessage(
      Container container, ContainerItem containerItem, String freightType) {
    SymPutawayMessage payload = new SymPutawayMessage();
    SymPutawayItem content = createSymPutawayItem(container, containerItem);
    payload.setAction(ReceivingConstants.PUTAWAY_ADD_ACTION);
    payload.setTrackingId(container.getTrackingId());
    payload.setShippingLabelId(container.getTrackingId());
    if (SymFreightType.SSTK.name().equals(freightType)) {
      payload.setLabelType(SymLabelType.PALLET.toString());
      payload.setFreightType(SymFreightType.SSTK.toString());
    } else if (SymFreightType.DA.name().equals(freightType)) {
      payload.setLabelType(SymLabelType.ROUTING.toString());
      payload.setFreightType(SymFreightType.DA.toString());
    } else if (SymFreightType.XDOCK.name().equals(freightType)) {
      payload.setLabelType(SymLabelType.SHIPPING.toString());
      payload.setFreightType(SymFreightType.XDOCK.toString());
      LOGGER.info(
          "[XDK] setting symLabelType = '{}' and symFreightType = '{}' for trackingId '{}' For symFreightType XDOCK through api flow ",
          payload.getLabelType(),
          payload.getFreightType(),
          containerItem.getTrackingId());
    }
    payload.setInventoryStatus(AVAILABLE);
    payload.setContents(Collections.singletonList(content));
    return payload;
  }

  static SymPutawayItem createSymPutawayItem(Container container, ContainerItem containerItem) {
    Date rotateDate =
        Objects.nonNull(containerItem.getRotateDate()) ? containerItem.getRotateDate() : new Date();
    SymPutawayItem content = new SymPutawayItem();
    content.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
    content.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
    content.setPoTypeCode(containerItem.getPoTypeCode());
    // ToDo: PoEvent
    content.setDeliveryNumber(container.getDeliveryNumber());
    content.setItemNumber(containerItem.getItemNumber());
    content.setChildItemNumber(ReceivingConstants.SYM_DEFAULT_ITEM_NUM);
    content.setBaseDivisionCode(containerItem.getBaseDivisionCode());
    content.setFinancialReportingGroupCode(containerItem.getFinancialReportingGroupCode());
    content.setVendorNumber(containerItem.getVendorNumber());
    content.setQuantity(containerItem.getQuantity());
    content.setQuantityUOM(containerItem.getVnpkWgtUom());
    content.setPackagedAsUom(containerItem.getPackagedAsUom());
    content.setRotateDate(formatRotateDate(rotateDate));
    content.setTi(containerItem.getActualTi());
    content.setHi(containerItem.getActualHi());
    content.setDeptNumber(containerItem.getDeptNumber());
    if (CollectionUtils.isNotEmpty(Collections.singleton(container.getDestination()))) {
      String primeSlot = container.getDestination().toString();
      content.setPrimeSlotId(primeSlot);
    }
    String storeId =
        MapUtils.isNotEmpty(container.getDestination())
                && container.getDestination().containsKey(ReceivingConstants.BU_NUMBER)
            ? container.getDestination().get(ReceivingConstants.BU_NUMBER)
            : ReceivingConstants.SYM_DEFAULT_STORE_ID;
    SymPutawayDistribution symPutawayDistribution =
        SymPutawayDistribution.builder()
            .storeId(storeId)
            .allocQty(containerItem.getQuantity())
            .build();
    content.setDistributions(Collections.singletonList(symPutawayDistribution));
    return content;
  }
}
