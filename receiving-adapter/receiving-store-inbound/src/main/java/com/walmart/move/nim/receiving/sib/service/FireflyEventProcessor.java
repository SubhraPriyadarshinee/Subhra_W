package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.utils.Constants.ISO_FORMAT_STRING_REQUEST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_AUTO_FINALIZED;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.PalletScanArchiveUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.FireflyEvent;
import com.walmart.move.nim.receiving.core.model.GdmDeliverySummary;
import com.walmart.move.nim.receiving.core.model.GdmTimeCriteria;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.model.decant.PalletScanArchiveMessage;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchResponse;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.sib.config.FireflyConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FireflyEventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(FireflyEventProcessor.class);

  private static final String SCHEDULED_TYPE = "scheduled";
  private static final String COMMA_DELIMITER = ",";
  private static final String ALLOW_ALL_DELIMITER = "*";
  private static final String PALLET_AUTO_RECEIVED = "PALLET_AUTO_RECEIVED";
  private static final String PALLET_NOT_FOUND = "PALLET_NOT_FOUND";
  private static final String PALLET_DUPLICATE_SCAN = "PALLET_DUPLICATE_SCAN";
  private static final String RECEIVED_FIREFLY_SIGNAL = "RECEIVED_FIREFLY_SIGNAL";
  private Gson gson;

  @Autowired private ContainerService containerService;

  @Autowired private DecantService decantService;

  @Autowired private StoreDeliveryService storeDeliveryService;

  @ManagedConfiguration private FireflyConfig fireflyConfig;

  @ManagedConfiguration private AppConfig appConfig;

  public FireflyEventProcessor() {
    this.gson = new GsonBuilder().setDateFormat(ISO_FORMAT_STRING_REQUEST).create();
  }

  public void doProcessEvent(FireflyEvent fireflyEvent) {
    LOGGER.info("Processing FireflyEvent");
    if (!isFireflyEventEnabled(fireflyEvent)) {
      LOGGER.info(
          "Firefly event was ignored with eventName: {} assetType: {} businessUnitNumber: {}.",
          fireflyEvent.getEventName(),
          fireflyEvent.getAssetType(),
          fireflyEvent.getBusinessUnitNumber());
      return;
    }

    GdmDeliverySummary gdmDeliverySummary = findDelivery(fireflyEvent);
    // if delivery is found in GDM then only try to receive container
    // otherwise just archive event in SCT table by calling Decant API
    if (gdmDeliverySummary != null && gdmDeliverySummary.getDeliveryNumber() != null) {
      archiveFireflyEventByDecant(gdmDeliverySummary, fireflyEvent, RECEIVED_FIREFLY_SIGNAL);
      ContainerScanRequest containerScanRequest =
          createContainerScanRequest(
              gdmDeliverySummary.getDeliveryNumber().toString(),
              fireflyEvent.getTempComplianceInd());
      String containerResponse =
          containerService.receiveContainer(fireflyEvent.getAssetId(), containerScanRequest);
      ContainerDTO containerDTO = gson.fromJson(containerResponse, ContainerDTO.class);
      String eventType =
          containerWasReceivedByAssociate(containerDTO)
              ? PALLET_DUPLICATE_SCAN
              : PALLET_AUTO_RECEIVED;
      archiveFireflyEventByDecant(gdmDeliverySummary, fireflyEvent, eventType);
    } else {
      archiveFireflyEventByDecant(null, fireflyEvent, PALLET_NOT_FOUND);
    }
  }

  private boolean containerWasReceivedByAssociate(ContainerDTO containerDTO) {
    return containerDTO != null && !USER_ID_AUTO_FINALIZED.equals(containerDTO.getCreateUser());
  }

  private GdmDeliverySummary findDelivery(FireflyEvent fireflyEvent) {
    GdmDeliverySearchRequest gdmDeliverySearchRequest = new GdmDeliverySearchRequest();
    gdmDeliverySearchRequest.setPalletNumber(fireflyEvent.getAssetId());
    Instant eventAssociationTime = ReceivingUtils.parseTime(fireflyEvent.getAssociationTime());
    gdmDeliverySearchRequest.setTimeCriteria(getTimeCriteria(eventAssociationTime));
    GdmDeliverySummary gdmDeliverySummary = null;
    try {
      gdmDeliverySummary = searchDeliveryInGdm(gdmDeliverySearchRequest);
    } catch (ReceivingDataNotFoundException ex) {
      LOGGER.error("Delivery not found in GDM");
    } catch (ReceivingInternalException ex) {
      String errorMessage = "Received unexpected response from GDM";
      LOGGER.error(errorMessage, ex);
      throw new ReceivingInternalException(
          ExceptionCodes.UNEXPECTED_FIREFLY_EVENT_ERROR, errorMessage);
    }
    return gdmDeliverySummary;
  }

  private void archiveFireflyEventByDecant(
      GdmDeliverySummary gdmDeliverySummary, FireflyEvent fireflyEvent, String type) {
    LOGGER.info(
        "Initiating message publish for the pallet {} with type {}",
        fireflyEvent.getAssetId(),
        type);
    PalletScanArchiveMessage message =
        PalletScanArchiveUtils.createPalletScanArchiveMessage(null, fireflyEvent, type);
    message.setDeliveryNumber(
        Optional.ofNullable(gdmDeliverySummary)
            .map(summary -> gdmDeliverySummary.getDeliveryNumber().toString())
            .orElse(null));
    List<DecantMessagePublishRequest> decantMessagePublishRequests =
        PalletScanArchiveUtils.createDecantMessagePublishRequests(message, gson);
    decantService.initiateMessagePublish(decantMessagePublishRequests);
    LOGGER.info("Successfully published message for the pallet. {}", fireflyEvent.getAssetId());
  }

  /**
   * Try to search the Delivery in the GDM by the given pallet number - if GDM delivery is found
   * then proceed with next steps i.e. receiving - else if, no delivery found then just log the
   * event in the SCT table - else, GDM call fails or not available, throw new
   * ReceivingInternalException which will be retried
   *
   * @param gdmDeliverySearchRequest
   * @return
   * @throws GDMRestApiClientException
   */
  private GdmDeliverySummary searchDeliveryInGdm(
      GdmDeliverySearchRequest gdmDeliverySearchRequest) {
    GdmDeliverySearchResponse gdmDeliverySearchResponse =
        storeDeliveryService.searchDelivery(gdmDeliverySearchRequest);
    GdmDeliverySummary gdmDeliverySummary = null;
    if (gdmDeliverySearchResponse != null
        && CollectionUtils.isNotEmpty(gdmDeliverySearchResponse.getDeliveries())) {
      gdmDeliverySummary = gdmDeliverySearchResponse.getDeliveries().get(0);
    } else {
      LOGGER.error("GDM response contains no deliveries");
    }
    return gdmDeliverySummary;
  }

  private List<GdmTimeCriteria> getTimeCriteria(Instant associateTime) {
    GdmTimeCriteria gdmTimeCriteria = new GdmTimeCriteria();
    gdmTimeCriteria.setType(SCHEDULED_TYPE);
    Instant current = Instant.now();
    gdmTimeCriteria.setTo(getTimeCriteriaTo(current));
    gdmTimeCriteria.setFrom(getTimeCriteriaFrom(associateTime, current));
    return Collections.singletonList(gdmTimeCriteria);
  }

  private Instant getTimeCriteriaTo(Instant current) {
    int daysPeriod = appConfig.getGdmSearchPalletRequestTimeToAdditionInDays();
    return current.plus(daysPeriod, ChronoUnit.DAYS);
  }

  /**
   * when associationTime is more than DAYS_PERIOD days older than current, use the time that is
   * DAYS_PERIOD days older than current, otherwise use the associationTime;
   */
  private Instant getTimeCriteriaFrom(Instant associateTime, Instant current) {
    int daysPeriod = appConfig.getGdmSearchPalletRequestTimeRangeInDays();
    Instant daysBeforeCurrent = current.minus(daysPeriod, ChronoUnit.DAYS);
    return associateTime == null || associateTime.compareTo(daysBeforeCurrent) < 0
        ? daysBeforeCurrent
        : associateTime;
  }

  private boolean isFireflyEventEnabled(FireflyEvent event) {
    return isEventEnabledByField(
            event.getEventName(), fireflyConfig.getFireflyEventEnabledEventNames())
        && isEventEnabledByField(
            event.getAssetType(), fireflyConfig.getFireflyEventEnabledAssetTypes())
        && isEventEnabledByField(
            event.getBusinessUnitNumber().toString(), fireflyConfig.getFireflyEventEnabledStores());
  }

  private boolean isEventEnabledByField(String eventValue, String configValue) {
    if (StringUtils.isBlank(configValue)) {
      return false;
    } else if (ALLOW_ALL_DELIMITER.equals(configValue)) {
      return true;
    }
    Set<String> enabledConfigValues =
        new HashSet<>(Arrays.asList(configValue.split(COMMA_DELIMITER)));
    return enabledConfigValues.contains(eventValue);
  }

  private ContainerScanRequest createContainerScanRequest(
      String deliveryNumber, Character tempComplianceInd) {
    ContainerScanRequest containerScanRequest = new ContainerScanRequest();
    containerScanRequest.setDeliveryNumber(Long.parseLong(deliveryNumber));
    containerScanRequest.setMiscInfo(createContainerMiscInfoRequest(tempComplianceInd));
    return containerScanRequest;
  }

  private Map<String, Object> createContainerMiscInfoRequest(Character c) {
    Map<String, Object> miscInfo = new HashMap<>();
    miscInfo.put(ReceivingConstants.IS_RECEIVED_THROUGH_AUTOMATED_SIGNAL, Boolean.TRUE);
    if (c != null && 'Y' == c) {
      miscInfo.put(ReceivingConstants.IS_TEMP_COMPLIANCE, true);
    } else if (c != null && 'N' == c) {
      miscInfo.put(ReceivingConstants.IS_TEMP_COMPLIANCE, false);
    }
    return miscInfo;
  }
}
