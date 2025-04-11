package com.walmart.move.nim.receiving.core.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.OpenDockTagCount;
import com.walmart.move.nim.receiving.core.model.UniversalInstructionResponse;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author sks0013
 *     <p>Interface for dock tag service since it can have different implementations in different
 *     sites
 */
public abstract class DockTagService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DockTagService.class);
  @Autowired protected InventoryService inventoryService;

  @Autowired protected LocationService locationService;
  @Autowired protected DockTagPersisterService dockTagPersisterService;
  @Autowired protected ReceiptService receiptService;
  @Autowired protected DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired protected ContainerService containerService;

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  protected InstructionService instructionService;

  protected JsonParser parser;

  public DockTagService() {
    parser = new JsonParser();
  }

  public abstract InstructionResponse createDockTag(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException;

  public abstract DockTagResponse createDockTags(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException;

  public abstract void saveDockTag(DockTag dockTag);

  public abstract String completeDockTag(String dockTagId, HttpHeaders httpHeaders);

  public abstract DockTagResponse partialCompleteDockTag(
      CreateDockTagRequest createDockTagRequest,
      String dockTagId,
      boolean retry,
      HttpHeaders httpHeaders)
      throws ReceivingException;

  public abstract String searchDockTag(
      SearchDockTagRequest searchDockTagRequest, InstructionStatus status);

  public DockTagDTO searchDockTagById(String dockTagId) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, "Functionality not supported");
  }

  public abstract Integer countOfOpenDockTags(Long deliveryNumber);

  public abstract DockTag createDockTag(
      @NotNull String dockTagId,
      @NotNull Long deliveryNumber,
      String userId,
      DockTagType dockTagType);

  public abstract InstructionResponse receiveDockTag(
      ReceiveDockTagRequest receiveDockTagRequest, HttpHeaders httpHeaders);

  public abstract void updateDockTagStatusAndPublish(
      ReceiveDockTagRequest receiveDockTagRequest, DockTag dockTagFromDb, HttpHeaders httpHeaders);

  public abstract ReceiveNonConDockTagResponse receiveNonConDockTag(
      String dockTagId, HttpHeaders httpHeaders);

  public abstract UniversalInstructionResponse receiveUniversalTag(
      String dockTagId, String doorNumber, HttpHeaders httpHeaders) throws ReceivingException;

  public abstract void updateDockTagById(String dockTagId, InstructionStatus status, String userId);

  public abstract CompleteDockTagResponse completeDockTags(
      CompleteDockTagRequest completeDockTagRequest, HttpHeaders headers);

  public abstract List<CompleteDockTagResponse> completeDockTagsForGivenDeliveries(
      CompleteDockTagRequestsList completeDockTagRequest, HttpHeaders headers);

  public Integer countOfPendingDockTags(Long deliveryNumber) {
    return dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(
        deliveryNumber, ReceivingUtils.getPendingDockTagStatus());
  }

  protected void markCompleteAndDeleteFromInventory(HttpHeaders headers, DockTag dockTag) {
    inventoryService.deleteContainer(dockTag.getDockTagId(), headers);
    // set completed status, only if delete in inventory is successful.
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteUserId(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    dockTag.setCompleteTs(new Date());
  }

  protected void publishDockTagContainer(
      HttpHeaders headers, List<String> dockTagIds, String containerStatus) {
    containerService.publishContainerListWithStatus(dockTagIds, headers, containerStatus);
  }

  protected void publishDockTagContainer(
      HttpHeaders headers, String dockTagId, String containerStatus) {
    LOGGER.info("Going to publish container for dockTagId {}.", dockTagId);
    containerService.publishContainerWithStatus(dockTagId, headers, containerStatus);
  }

  public DockTag getDockTagById(String dockTagId) {
    return dockTagPersisterService.getDockTagByDockTagId(dockTagId);
  }

  protected void checkAndPublishPendingDockTags(
      HttpHeaders headers, String deliveryStatus, Long deliveryNumber) {
    DeliveryService deliveryService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    if (StringUtils.isEmpty(deliveryStatus)) {
      try {
        // TODO: here for ACC if deliveryService = retryableDeliveryServiceV3,
        // then this flow will also use retryable instead of the intended
        String deliveryResponse =
            deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, headers);
        deliveryStatus =
            parser
                .parse(deliveryResponse)
                .getAsJsonObject()
                .get("deliveryStatus")
                .toString()
                .replace("\"", "");
      } catch (ReceivingException e) {
        LOGGER.error(
            "Unable to determine deliveryStatus for delivery {}. Hence not publishing receipts to GDM.",
            deliveryNumber);
        return;
      }
    }
    // check if there are any pending dock tag & publish
    if (!DeliveryStatus.WRK.name().equals(deliveryStatus)
        && Integer.valueOf(0).equals(countOfPendingDockTags(deliveryNumber))) {

      try {
        deliveryService.completeDelivery(deliveryNumber, false, headers);
      } catch (ReceivingException receivingException) {
        LOGGER.error(
            String.format(
                ReceivingException.COMPLETE_DELIVERY_DEFAULT_ERROR_MESSAGE,
                deliveryNumber,
                receivingException.getMessage()));
      }
    }
  }

  public void autoCompleteDocks(int dockTagAutoCompleteHours, int pageSize) {}

  public void autoCompleteDocks(int pageSize) {
    JsonElement autoDockTagCompleteHours =
        tenantSpecificConfigReader.getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.AUTO_DOCK_TAG_COMPLETE_HOURS);
    int dockTagAutoCompleteHours =
        Objects.nonNull(autoDockTagCompleteHours)
            ? autoDockTagCompleteHours.getAsInt()
            : ReceivingConstants.DEFAULT_AUTO_DOCK_TAG_COMPLETE_HOURS;

    Calendar cal = Calendar.getInstance();
    LOGGER.info("AutoCompleteDockTag: Current time {}", cal.getTime());
    cal.add(Calendar.HOUR, -dockTagAutoCompleteHours);
    Date fromDate = cal.getTime();

    LOGGER.info("AutoCompleteDockTag: Before {}", fromDate);
    List<DockTag> dockTags =
        dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            ReceivingUtils.getPendingDockTagStatus(), fromDate, PageRequest.of(0, pageSize));

    if (CollectionUtils.isEmpty(dockTags)) {
      LOGGER.info("AutoCompleteDockTag: Nothing to complete. Returning");
      return;
    }
    LOGGER.info("AutoCompleteDockTag: No. of docktags {}", dockTags.size());
    // get all unique delivery number
    completeDockTags(dockTags);
  }

  protected void completeDockTags(List<DockTag> dockTags) {
    Set<Long> deliveries = new HashSet<>();
    dockTags.forEach(dockTag -> deliveries.add(dockTag.getDeliveryNumber()));
    if (CollectionUtils.isEmpty(deliveries)) {
      LOGGER.info("AutoCompleteDockTag: Nothing to complete. Returning");
      return;
    }

    List<String> dockTagIds = new ArrayList<>();

    // delete from inventory
    dockTags.forEach(
        dockTag -> {
          TenantContext.setFacilityNum(dockTag.getFacilityNum());
          TenantContext.setFacilityCountryCode(dockTag.getFacilityCountryCode());
          dockTagIds.add(dockTag.getDockTagId());
          markCompleteAndDeleteFromInventory(ReceivingUtils.getHeaders(), dockTag);
          LOGGER.info("Auto-completed :docktag {}", dockTag.getDockTagId());
        });

    // Publish the Docktags based upon status
    if (!dockTagIds.isEmpty()) {
      publishDockTagContainer(
          ReceivingUtils.getHeaders(), dockTagIds, ReceivingConstants.STATUS_COMPLETE);
    }

    // save in DB
    dockTagPersisterService.saveAllDockTags(dockTags);

    // check & publish receipts to GDM for each delivery
    deliveries.forEach(
        del -> {
          DockTag dockTag =
              dockTags.stream().filter(dt -> del.equals(dt.getDeliveryNumber())).findAny().get();
          TenantContext.setFacilityNum(dockTag.getFacilityNum());
          TenantContext.setFacilityCountryCode(dockTag.getFacilityCountryCode());
          checkAndPublishPendingDockTags(ReceivingUtils.getHeaders(), null, del);
          LOGGER.info("Auto-completed :docktag for delivery {}", del);
        });
  }

  public List<DockTag> searchAllDockTagForGivenTenant(InstructionStatus status) {

    List<DockTag> dockTagResponse = null;
    LOGGER.info(
        "Fetching all docktags for facility number {} and country code {}",
        TenantContext.getFacilityNum(),
        TenantContext.getFacilityCountryCode());
    dockTagResponse =
        dockTagPersisterService.getDockTagsByStatuses(
            ReceivingUtils.getPendingDockTagStatus().contains(status)
                ? ReceivingUtils.getPendingDockTagStatus()
                : Collections.singletonList(status));
    return CollectionUtils.isEmpty(dockTagResponse) ? null : dockTagResponse;
  }

  public OpenDockTagCount countDockTag(InstructionStatus status) {
    int totalDockTags;
    switch (status) {
      case CREATED:
      case UPDATED:
        totalDockTags =
            dockTagPersisterService.getCountOfDockTagsByStatuses(
                ReceivingUtils.getPendingDockTagStatus());
        break;
      default:
        totalDockTags =
            dockTagPersisterService.getCountOfDockTagsByStatuses(
                Arrays.asList(InstructionStatus.values()));
    }
    OpenDockTagCount dockTagCount =
        OpenDockTagCount.builder().count(Integer.valueOf(totalDockTags)).build();
    return dockTagCount;
  }

  public void validateDockTagFromDb(
      DockTag dockTagFromDb, String dockTagId, String dtNotFoundMsg, HttpHeaders httpHeaders) {
    if (Objects.isNull(dockTagFromDb)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DOCK_TAG_NOT_FOUND, String.format(dtNotFoundMsg, dockTagId), dockTagId);
    } else {
      if (Objects.nonNull(dockTagFromDb.getCompleteTs())) {
        if (!ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_DOCK_TAG,
              String.format(
                  ReceivingConstants.DOCKTAG_ALREADY_COMPLETED_OLD_ERROR_MSG,
                  dockTagId,
                  dockTagFromDb.getCompleteUserId()),
              dockTagId,
              dockTagFromDb.getCompleteUserId());
        }
        String docktagCompleteTsString =
            ReceivingUtils.getFormattedDateString(
                dockTagFromDb.getCompleteTs(), "yyyy/MM/dd' 'HH:mm:ss");
        throw new ReceivingBadDataException(
            ExceptionCodes.DOCKTAG_ALREADY_COMPLETED_ERROR,
            String.format(
                ReceivingConstants.DOCKTAG_ALREADY_COMPLETED_ERROR_MSG,
                dockTagId,
                dockTagFromDb.getCompleteUserId(),
                docktagCompleteTsString),
            dockTagId,
            dockTagFromDb.getCompleteUserId(),
            docktagCompleteTsString);
      }
    }
  }
}
