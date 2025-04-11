package com.walmart.move.nim.receiving.core.common;

import static org.apache.commons.lang3.StringUtils.leftPad;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayItem;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayMessage;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

public class GdcPutawayUtils {
  private static final Logger LOG = LoggerFactory.getLogger(GdcPutawayUtils.class);

  private GdcPutawayUtils() {}

  /**
   * @param httpHeaders
   * @return witronPutawayHeaders
   */
  public static Map<String, Object> prepareWitronPutawayHeaders(HttpHeaders httpHeaders) {
    Map<String, Object> witronPutawayHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    final String correlationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    witronPutawayHeaders.put(ReceivingConstants.JMS_EVENT_TYPE, ReceivingConstants.PUTAWAY_REQUEST);
    witronPutawayHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER, correlationId);
    witronPutawayHeaders.put(ReceivingConstants.CORRELATION_ID, correlationId);
    witronPutawayHeaders.put(
        ReceivingConstants.JMS_MESSAGE_TS, ReceivingUtils.dateConversionToUTC(new Date()));
    witronPutawayHeaders.put(ReceivingConstants.JMS_REQUESTOR_ID, ReceivingConstants.RECEIVING);
    witronPutawayHeaders.put(
        ReceivingConstants.JMS_MESSAGE_VERSION, ReceivingConstants.WITRON_HAWKEYE_MESSAGE_VERSION);

    return witronPutawayHeaders;
  }

  /**
   * @param container
   * @param action
   * @return WitronPutawayMessage
   */
  public static WitronPutawayMessage prepareWitronPutawayMessage(
      Container container, String action) {
    WitronPutawayMessage witronPutawayMessage = new WitronPutawayMessage();
    witronPutawayMessage.setAction(action);
    witronPutawayMessage.setDeliveryNumber(container.getDeliveryNumber());
    witronPutawayMessage.setTrackingId(container.getTrackingId());
    witronPutawayMessage.setParentTrackingId(container.getParentTrackingId());
    witronPutawayMessage.setCompleteTs(container.getCompleteTs());
    witronPutawayMessage.setCube(container.getCube());
    witronPutawayMessage.setCubeUOM(container.getCubeUOM());
    witronPutawayMessage.setInventoryStatus(container.getInventoryStatus());
    witronPutawayMessage.setSourceLocationId(container.getLocation());
    if (container.getContainerType() != null) {
      witronPutawayMessage.setContainerType(
          ContainerType.getTypeEnum(container.getContainerType()).getCode().toString());
    }
    if (ObjectUtils.defaultIfNull(container.getWeight(), new Float(0)).intValue() == 0) {
      witronPutawayMessage.setWeight(ContainerUtils.calculateWeight(container));
      witronPutawayMessage.setWeightUOM(ContainerUtils.getDefaultWeightUOM(container));
    } else {
      witronPutawayMessage.setWeight(container.getWeight());
      witronPutawayMessage.setWeightUOM(container.getWeightUOM());
    }

    List<WitronPutawayItem> contents = new ArrayList<>();
    if (container.getContainerItems() != null) {
      container
          .getContainerItems()
          .forEach(
              containerItem -> {
                WitronPutawayItem witronPutawayItem = new WitronPutawayItem();
                witronPutawayItem.setPurchaseReferenceNumber(
                    containerItem.getPurchaseReferenceNumber());
                witronPutawayItem.setPurchaseReferenceLineNumber(
                    containerItem.getPurchaseReferenceLineNumber());
                witronPutawayItem.setItemNumber(containerItem.getItemNumber());
                witronPutawayItem.setBaseDivisionCode(containerItem.getBaseDivisionCode());
                witronPutawayItem.setFinancialReportingGroupCode(
                    containerItem.getFinancialReportingGroupCode());
                witronPutawayItem.setQuantity(containerItem.getQuantity());
                witronPutawayItem.setQuantityUOM(containerItem.getQuantityUOM());
                witronPutawayItem.setPackagedAsUom(containerItem.getPackagedAsUom());
                witronPutawayItem.setTi(containerItem.getActualTi());
                witronPutawayItem.setHi(containerItem.getActualHi());
                witronPutawayItem.setPoTypeCode(containerItem.getPoTypeCode());
                witronPutawayItem.setDeptNumber(containerItem.getDeptNumber());
                witronPutawayItem.setLotNumber(containerItem.getLotNumber());
                witronPutawayItem.setRotateDate(
                    DateFormatUtils.format(
                        containerItem.getRotateDate(), ReceivingConstants.SIMPLE_DATE));
                if (Objects.nonNull(containerItem.getVendorNbrDeptSeq())) {
                  witronPutawayItem.setVendorNumber(
                      leftPad(
                          containerItem.getVendorNbrDeptSeq().toString(),
                          ReceivingConstants.VENDOR_DEPT_SEQ_NUMBERS_DEFAULT_SIZE,
                          ReceivingConstants.ZERO_STRING));
                }
                contents.add(witronPutawayItem);
              });
    }
    witronPutawayMessage.setContents(contents);

    return witronPutawayMessage;
  }
}
