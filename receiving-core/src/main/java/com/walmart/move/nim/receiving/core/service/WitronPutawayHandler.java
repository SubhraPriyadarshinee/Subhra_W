package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.Facility;
import com.walmart.move.nim.receiving.core.model.PutawayHeader;
import com.walmart.move.nim.receiving.core.model.PutawayItem;
import com.walmart.move.nim.receiving.core.model.PutawayMessage;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Deprecated and advised to use GdcPutawayPublisher */
@Service
@Deprecated
public class WitronPutawayHandler implements PutawayService {
  private static final Logger log = LoggerFactory.getLogger(WitronPutawayHandler.class);
  @Autowired private JmsPublisher jmsPublisher;

  private final Gson gson;

  public WitronPutawayHandler() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }
  /** Deprecated and advised to use GdcPutawayPublisher */
  @Override
  @Deprecated
  public void publishPutaway(Container container, String action, HttpHeaders httpHeaders) {
    final String requestOriginator = httpHeaders.getFirst(REQUEST_ORIGINATOR);
    if (SOURCE_APP_NAME_WITRON.equalsIgnoreCase(requestOriginator)) {
      log.info("stop putaway/rtu message to witron as requestOriginator={}", requestOriginator);
      return;
    }
    ReceivingJMSEvent receivingJMSEvent = getReceivingJMSEvent(container, action, httpHeaders);

    // Publish putaway message
    jmsPublisher.publish(ReceivingConstants.PUB_PUTAWAY_RTU_TOPIC, receivingJMSEvent, Boolean.TRUE);
  }

  /**
   * Order of events containers is the order of events get published so, the sequence order is key
   * how the events get published. For Putaway the sequence is first 'delete' event followed by
   * 'add' event as next NOTE: Deprecated and advised to use 'action=update'
   *
   * @param deleteContainer
   * @param addContainer
   * @param httpHeaders
   */
  @Override
  @Deprecated
  public void publishPutawaySequentially(
      Container deleteContainer, Container addContainer, HttpHeaders httpHeaders) {

    ArrayList<ReceivingJMSEvent> eventsSequence = new ArrayList<>(2);
    // Do not change this sequence order: 'delete' then 'add'
    eventsSequence.add(getReceivingJMSEvent(deleteContainer, PUTAWAY_DELETE_ACTION, httpHeaders));
    eventsSequence.add(getReceivingJMSEvent(addContainer, PUTAWAY_ADD_ACTION, httpHeaders));

    jmsPublisher.publishSequentially(
        ReceivingConstants.PUB_PUTAWAY_RTU_TOPIC, eventsSequence, Boolean.TRUE);
  }

  public ReceivingJMSEvent getReceivingJMSEvent(
      Container container, String action, HttpHeaders httpHeaders) {
    // Prepare putaway headers
    Map<String, Object> jmsHeaders = ReceivingUtils.getForwardablHeader(httpHeaders);
    String correlationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    if (correlationId != null && !StringUtils.isEmpty(correlationId))
      jmsHeaders.put(ReceivingConstants.CORRELATION_ID, correlationId);

    jmsHeaders.put(ReceivingConstants.JMS_EVENT_TYPE, ReceivingConstants.PUTAWAY_EVENT_TYPE);
    jmsHeaders.put(ReceivingConstants.JMS_REQUESTOR_ID, ReceivingConstants.RECEIVING);
    jmsHeaders.put(
        ReceivingConstants.JMS_MESSAGE_TS, ReceivingUtils.dateConversionToUTC(new Date()));
    jmsHeaders.put(ReceivingConstants.JMS_MESSAGE_VERSION, 1);

    // Prepare putaway message
    PutawayMessage putawayMessage = preparePutwayMessage(container, action, httpHeaders);

    return new ReceivingJMSEvent(jmsHeaders, gson.toJson(putawayMessage));
  }

  /**
   * Prepare putaway message
   *
   * @param container
   * @param action
   * @param httpHeaders
   * @return PutawayMessage
   */
  public PutawayMessage preparePutwayMessage(
      Container container, String action, HttpHeaders httpHeaders) {
    PutawayMessage putawayMessage = new PutawayMessage();
    putawayMessage.setAction(action);

    // Optional header in the message body
    PutawayHeader putawayHeader = new PutawayHeader();
    putawayHeader.setEventType(ReceivingConstants.PUTAWAY_EVENT_TYPE);
    putawayHeader.setMessageId(container.getMessageId());
    putawayHeader.setCorrelationId(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    putawayHeader.setRequestorId(ReceivingConstants.RECEIVING);
    putawayHeader.setMsgTimestamp(new Date());
    putawayHeader.setFacilityNum(
        Integer.parseInt(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
    putawayHeader.setFacilityCountryCode(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    putawayHeader.setVersion(1);
    putawayMessage.setHeader(putawayHeader);

    putawayMessage.setDeliveryNumber(container.getDeliveryNumber().toString());
    putawayMessage.setTrackingId(container.getTrackingId());
    putawayMessage.setParentTrackingId(container.getParentTrackingId());

    Facility facility = new Facility();
    if (container.getFacility() != null) {
      facility.setBuNumber(container.getFacility().get("buNumber"));
      facility.setCountryCode(container.getFacility().get("countryCode"));
    }
    putawayMessage.setFacility(facility);

    Facility destination = new Facility();
    if (container.getDestination() != null) {
      destination.setBuNumber(container.getDestination().get("buNumber"));
      destination.setCountryCode(container.getDestination().get("countryCode"));
    }
    putawayMessage.setDestination(destination);
    if (container.getContainerType() != null) {
      putawayMessage.setContainerType(
          ContainerType.getTypeEnum(container.getContainerType()).getCode().toString());
    }
    putawayMessage.setCube(container.getCube());
    putawayMessage.setCubeUOM(container.getCubeUOM());
    if (nonNull(container.getSubcenterId()))
      putawayMessage.setOrgUnitId(String.valueOf(container.getSubcenterId()));
    putawayMessage.setLocation(container.getLocation());
    putawayMessage.setInventoryStatus(container.getInventoryStatus());
    putawayMessage.setCtrShippable(container.getCtrShippable());
    putawayMessage.setCtrReusable(container.getCtrReusable());
    putawayMessage.setCreateTs(container.getCreateTs());
    putawayMessage.setCreateUser(container.getCreateUser());
    putawayMessage.setLastChangedTs(container.getLastChangedTs());
    putawayMessage.setLastChangedUser(container.getLastChangedUser());
    putawayMessage.setPublishTs(container.getPublishTs());
    putawayMessage.setCompleteTs(container.getCompleteTs());

    // If not set previously, calculate container weight & uom from items found
    if (ObjectUtils.defaultIfNull(container.getWeight(), new Float(0)).intValue() == 0) {
      putawayMessage.setWeight(ContainerUtils.calculateWeight(container));
      putawayMessage.setWeightUOM(ContainerUtils.getDefaultWeightUOM(container));
    } else {
      putawayMessage.setWeight(container.getWeight());
      putawayMessage.setWeightUOM(container.getWeightUOM());
    }

    List<PutawayItem> contents = new ArrayList<>();
    if (container.getContainerItems() != null) {
      container
          .getContainerItems()
          .forEach(
              containerItem -> {
                PutawayItem putawayItem = new PutawayItem();
                putawayItem.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
                putawayItem.setPurchaseReferenceLineNumber(
                    containerItem.getPurchaseReferenceLineNumber());
                putawayItem.setInboundChannelMethod(containerItem.getInboundChannelMethod());
                putawayItem.setOutboundChannelMethod(containerItem.getOutboundChannelMethod());
                putawayItem.setTotalPurchaseReferenceQty(
                    containerItem.getTotalPurchaseReferenceQty());
                putawayItem.setGtin(containerItem.getGtin());
                putawayItem.setItemNumber(containerItem.getItemNumber());
                if (containerItem.getDeptNumber() != null) {
                  putawayItem.setDeptNumber(containerItem.getDeptNumber().toString());
                }
                putawayItem.setQuantity(containerItem.getQuantity());
                putawayItem.setQuantityUOM(containerItem.getQuantityUOM());
                putawayItem.setVnpkQty(containerItem.getVnpkQty());
                putawayItem.setWhpkQty(containerItem.getWhpkQty());
                putawayItem.setVendorPackCost(containerItem.getVendorPackCost());
                putawayItem.setWhpkSell(containerItem.getWhpkSell());
                putawayItem.setRotateDate(containerItem.getRotateDate());
                putawayItem.setBaseDivisionCode(containerItem.getBaseDivisionCode());
                putawayItem.setFinancialReportingGroupCode(
                    containerItem.getFinancialReportingGroupCode());
                putawayItem.setTi(containerItem.getActualTi());
                putawayItem.setHi(containerItem.getActualHi());
                putawayItem.setLotNumber(containerItem.getLotNumber());
                putawayItem.setVendorNumber(
                    getZeroPrefixPaddedNineDigitVendor(containerItem.getVendorNbrDeptSeq()));
                putawayItem.setPackagedAsUom(containerItem.getPackagedAsUom());
                contents.add(putawayItem);
              });
    }
    putawayMessage.setContents(contents);

    return putawayMessage;
  }

  // down stream need full 9 digit vendor number including dept and sequence
  public String getZeroPrefixPaddedNineDigitVendor(Integer vendorNbrDeptSeqInteger) {
    if (Objects.nonNull(vendorNbrDeptSeqInteger))
      return org.apache.commons.lang3.StringUtils.leftPad(
          vendorNbrDeptSeqInteger.toString(), VENDOR_DEPT_SEQ_NUMBERS_DEFAULT_SIZE, ZERO_STRING);
    else return null;
  }
}
