package com.walmart.move.nim.receiving.sib.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.exception.ExceptionMessage;
import com.walmart.move.nim.receiving.sib.exception.StoreExceptionCodes;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class AutoDeliveryCompleteEventProcessor implements ProcessExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AutoDeliveryCompleteEventProcessor.class);

  private Gson gson;

  public AutoDeliveryCompleteEventProcessor() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Autowired EventRepository eventRepository;

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    Event event = new Event();
    Map<String, Object> additionAttribute = receivingEvent.getAdditionalAttributes();

    if (StringUtils.isBlank(receivingEvent.getPayload())) {
      LOGGER.info(
          "Event payload cannot be empty for action type {}",
          additionAttribute.get(ReceivingConstants.ACTION_TYPE));
      return;
    }
    Long deliveryNumber = Long.valueOf(receivingEvent.getPayload());
    if (!ReceivingConstants.CREATE_DELIVERY_COMPLETE_EVENT.equalsIgnoreCase(
        String.valueOf(additionAttribute.get(ReceivingConstants.ACTION_TYPE)))) {
      LOGGER.info(
          "Invalid action type {} for delivery Number {}",
          additionAttribute.get(ReceivingConstants.ACTION_TYPE),
          deliveryNumber);
      return;
    }
    Integer delayTime =
        (Integer) additionAttribute.getOrDefault(EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES, 30);
    event.setKey(receivingEvent.getPayload());
    event.setDeliveryNumber(deliveryNumber);
    event.setEventType(EventType.DELIVERY_AUTO_COMPLETE);
    Date createTsInUTC;
    try {
      DateFormat dateFormat = new SimpleDateFormat(UTC_DATE_FORMAT);
      dateFormat.setTimeZone(TimeZone.getTimeZone(UTC_TIME_ZONE));
      createTsInUTC = dateFormat.parse(dateFormat.format(new Date(System.currentTimeMillis())));
    } catch (Exception exception) {
      LOGGER.error(
          "Auto Complete Event invalid current timestamp for delivery {}",
          deliveryNumber,
          exception);
      throw new ReceivingBadDataException(
          StoreExceptionCodes.INVALID_PICKUP_TS_CORRECTION,
          String.format(ExceptionMessage.INVALID_PICKUP_TS_MSG, delayTime));
    }
    event.setPickUpTime(CoreUtil.addMinutesToJavaUtilDate(createTsInUTC, delayTime));

    event.setRetryCount(0);
    event.setStatus(EventTargetStatus.PENDING);
    event.setFacilityNum(TenantContext.getFacilityNum());
    event.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
    eventRepository.save(event);
    LOGGER.info(
        "Creating {} event for delivery {}.", event.getEventType(), event.getDeliveryNumber());
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
