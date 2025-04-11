package com.walmart.move.nim.receiving.sib.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.exception.ExceptionMessage;
import com.walmart.move.nim.receiving.sib.exception.StoreExceptionCodes;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.transformer.ContainerDataTransformer;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public class CorrectionContainerEventProcessor implements ProcessExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CorrectionContainerEventProcessor.class);
  @Autowired EventRepository eventRepository;
  @Autowired ContainerDataTransformer containerDataTransformer;
  private Gson gson;

  public CorrectionContainerEventProcessor() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  private void processCorrectionContainerEventWithThreshold(
      ContainerDTO containerDTO, int pickUpTimeDelay) {
    Event containerEvent = new Event();
    containerEvent.setKey(containerDTO.getTrackingId());
    containerEvent.setDeliveryNumber(containerDTO.getDeliveryNumber());
    containerEvent.setEventType(EventType.CORRECTION);
    containerEvent.setPayload(
        gson.toJson(containerDataTransformer.transformToContainerEvent(containerDTO)));
    Date createTsInUTC;
    try {
      DateFormat dateFormat = new SimpleDateFormat(UTC_DATE_FORMAT);
      dateFormat.setTimeZone(TimeZone.getTimeZone(UTC_TIME_ZONE));
      createTsInUTC = dateFormat.parse(dateFormat.format(new Date(System.currentTimeMillis())));
    } catch (Exception exception) {
      LOGGER.error("Correction Event invalid current timestamp", exception);
      throw new ReceivingBadDataException(
          StoreExceptionCodes.INVALID_PICKUP_TS_CORRECTION,
          String.format(ExceptionMessage.INVALID_PICKUP_TS_MSG, pickUpTimeDelay));
    }
    containerEvent.setPickUpTime(CoreUtil.addMinutesToJavaUtilDate(createTsInUTC, pickUpTimeDelay));

    containerEvent.setRetryCount(0);
    containerEvent.setStatus(EventTargetStatus.PENDING);
    containerEvent.setFacilityNum(TenantContext.getFacilityNum());
    containerEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
    eventRepository.save(containerEvent);
  }

  private void updateCorrectionEventAsInvalidIfApplicable(ContainerDTO containerDTO) {
    Event event =
        eventRepository.findByKeyAndStatusAndEventType(
            containerDTO.getSsccNumber(), EventTargetStatus.PENDING, EventType.CORRECTION);
    if (Objects.nonNull(event)) {
      event.setStatus(EventTargetStatus.SUCCESSFUL);
      eventRepository.save(event);
    } else {
      LOGGER.info(
          "Not find Any Correction Event in Pending State for EventKey{} and deliveryNumber{}",
          containerDTO.getSsccNumber(),
          containerDTO.getDeliveryNumber());
    }
  }

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    if (!StringUtils.isEmpty(receivingEvent.getPayload())) {
      // ContainerDTO containerDTO = gson.fromJson(receivingEvent.getPayload(), ContainerDTO.class);
      ContainerDTO containerDTO =
          JacksonParser.convertJsonToObject(receivingEvent.getPayload(), ContainerDTO.class);
      Map<String, Object> additionAttribute = receivingEvent.getAdditionalAttributes();
      if (additionAttribute.get(ACTION_TYPE).equals(CREATE_CORRECTION_EVENT)) {

        Integer delayTime = (Integer) additionAttribute.get(EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES);

        processCorrectionContainerEventWithThreshold(containerDTO, delayTime.intValue());
      } else {

        updateCorrectionEventAsInvalidIfApplicable(containerDTO);
      }

    } else {
      LOGGER.info(
          "Event payload cannot be empty for actionType {}",
          receivingEvent.getAdditionalAttributes().get(ACTION_TYPE));
    }
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
