package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.acc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.DockTagPersisterService;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

public class ACCDockTagService extends DockTagService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACCDockTagService.class);

  private static final int MILLISECONDS_IN_AN_HOUR = 60 * 60 * 1000;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  private Gson gson;

  private Gson gsonForDate;

  @Resource(name = ACCConstants.ACC_INSTRUCTION_SERVICE)
  private ACCInstructionService accInstructionService;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private DockTagPersisterService dockTagPersisterService;

  @Autowired private LabelDataService labelDataService;
  @Autowired private ObjectMapper objectMapper;

  public ACCDockTagService() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    gsonForDate =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd"))
            .create();
  }

  @Override
  public DockTag createDockTag(
      @NotNull String dockTagId,
      @NotNull Long deliveryNumber,
      @Nullable String userId,
      DockTagType dockTagType) {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId(userId);
    dockTag.setDeliveryNumber(deliveryNumber);
    dockTag.setDockTagId(dockTagId);
    dockTag.setDockTagStatus(InstructionStatus.CREATED);
    dockTag.setDockTagType(dockTagType);
    return dockTag;
  }

  @Override
  public void saveDockTag(DockTag dockTag) {
    dockTagPersisterService.saveDockTag(dockTag);
  }

  @Override
  public Integer countOfOpenDockTags(Long deliveryNumber) {
    return countOfPendingDockTags(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  private String completeDockTagAndGetDeliveryStatus(
      String dockTagId, DockTag dockTagFromDb, HttpHeaders httpHeaders) {
    String deliveryResponse = null;
    Long deliveryNumber = dockTagFromDb.getDeliveryNumber();
    boolean isKotlinEnabled =
        ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    try {
      markCompleteAndDeleteFromInventory(httpHeaders, dockTagFromDb);
      saveDockTag(dockTagFromDb);
      publishDockTagContainer(
          httpHeaders, dockTagFromDb.getDockTagId(), ReceivingConstants.STATUS_COMPLETE);

      DeliveryService deliveryService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_SERVICE_KEY,
              DeliveryService.class);
      deliveryResponse = deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
    } catch (ReceivingException rex) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, exception code: {}",
          dockTagId,
          rex.getErrorResponse().getErrorKey());
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PROCESS_DOCK_TAG,
          String.format(ReceivingConstants.DOCK_TAG_ERROR_MESSAGE, dockTagId),
          dockTagId,
          rex.getMessage());
    } catch (ReceivingInternalException rie) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          rie.getDescription());
      if (isKotlinEnabled
          && rie.getErrorCode().equals(ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY)) {
        throw new ReceivingInternalException(
            ExceptionCodes.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR,
            String.format(ReceivingConstants.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR_MSG, dockTagId),
            dockTagId);
      }
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PROCESS_DOCK_TAG,
          String.format(ReceivingConstants.DOCK_TAG_ERROR_MESSAGE, dockTagId),
          dockTagId,
          rie.getMessage());
    } catch (ReceivingDataNotFoundException rdnfe) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          rdnfe.getDescription());
      if (isKotlinEnabled && rdnfe.getErrorCode().equals(ExceptionCodes.INVENTORY_NOT_FOUND)) {
        throw new ReceivingInternalException(
            ExceptionCodes.INVENTORY_NOT_FOUND,
            String.format(ReceivingConstants.INVENTORY_NOT_FOUND_MESSAGE, dockTagId),
            dockTagId);
      }
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PROCESS_DOCK_TAG,
          String.format(ReceivingConstants.DOCK_TAG_ERROR_MESSAGE, dockTagId),
          dockTagId,
          rdnfe.getMessage());
    }
    String deliveryStatus =
        parser
            .parse(deliveryResponse)
            .getAsJsonObject()
            .get("deliveryStatus")
            .toString()
            .replace("\"", "");
    return deliveryStatus;
  }

  @Override
  public String completeDockTag(String dockTagId, HttpHeaders httpHeaders) {
    LOGGER.info("Entering complete dock tag for {}", dockTagId);
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
    Long deliveryNumber = dockTagFromDb.getDeliveryNumber();
    String deliveryStatus =
        completeDockTagAndGetDeliveryStatus(dockTagId, dockTagFromDb, httpHeaders);
    checkAndPublishPendingDockTags(httpHeaders, deliveryStatus, deliveryNumber);
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    if (isKotlinEnabled) {
      return gson.toJson(getDockTagData(dockTagFromDb));
    } else {
      return gson.toJson(dockTagFromDb);
    }
  }

  @Override
  @Transactional
  public DockTagResponse partialCompleteDockTag(
      CreateDockTagRequest createDockTagRequest,
      String dockTagId,
      boolean isRetryCompleteFlag,
      HttpHeaders httpHeaders) {
    LOGGER.info("Entering partial complete dock tag for {}", dockTagId);
    String deliveryStatus = null;
    DockTag dockTagFromDb = null;
    DockTagResponse dockTagResponse = null;
    try {
      dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
      if (!isRetryCompleteFlag) {
        validateDockTagFromDb(
            dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
        deliveryStatus = completeDockTagAndGetDeliveryStatus(dockTagId, dockTagFromDb, httpHeaders);
      }
      // create new floor line docktag
      PrintLabelData dockTagPrintLabelData = null;
      InstructionResponse dockTagInstructionResponse =
          accInstructionService.createFloorLineDockTag(
              getInstructionRequest(createDockTagRequest), httpHeaders);
      Instruction dockTagInstruction = dockTagInstructionResponse.getInstruction();
      if (Objects.nonNull(dockTagInstruction.getContainer())) {
        dockTagPrintLabelData =
            objectMapper.convertValue(
                dockTagInstruction.getContainer().getCtrLabel(), PrintLabelData.class);
      }
      dockTagResponse =
          DockTagResponse.builder()
              .dockTags(Collections.singletonList(dockTagInstruction.getDockTagId()))
              .deliveryNumber(dockTagInstruction.getDeliveryNumber())
              .printData(dockTagPrintLabelData)
              .build();
      checkAndPublishPendingDockTags(
          httpHeaders, deliveryStatus, dockTagFromDb.getDeliveryNumber());

    } catch (ReceivingDataNotFoundException rdnfe) {
      if (rdnfe.getErrorCode().equals(ExceptionCodes.DOCK_TAG_NOT_FOUND)) {
        throw new ReceivingInternalException(
            rdnfe.getErrorCode(),
            String.format(ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, dockTagId),
            dockTagId);
      }
    } catch (ReceivingBadDataException rbde) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          rbde.getDescription());
      if (ExceptionCodes.DOCKTAG_ALREADY_COMPLETED_ERROR.equals(rbde.getErrorCode())) {
        throw new ReceivingInternalException(
            rbde.getErrorCode(), rbde.getMessage(), rbde.getValues());
      }
    } catch (ReceivingException rex) {
      LOGGER.error(
          "Error occurred while creating new DockTag, in partial DockTag flow. cId={}, errorMsg={}",
          TenantContext.getCorrelationId(),
          rex.getErrorResponse());
      throw new ReceivingInternalException(
          ExceptionCodes.PARTIAL_DOCKTAG_CREATION_ERROR,
          String.format(
              ReceivingConstants.PARTIAL_DOCKTAG_CREATION_ERROR_MSG,
              dockTagFromDb.getDockTagId(),
              dockTagFromDb.getDeliveryNumber().toString()),
          dockTagFromDb.getDockTagId(),
          dockTagFromDb.getDeliveryNumber().toString());
    }
    return dockTagResponse;
  }

  private DockTagData getDockTagData(DockTag dockTag) {
    return (Objects.nonNull(dockTag))
        ? DockTagData.builder()
            .id(dockTag.getId())
            .dockTagId(dockTag.getDockTagId())
            .deliveryNumber(dockTag.getDeliveryNumber())
            .createUserId(dockTag.getCreateUserId())
            .createTs(
                Objects.nonNull(dockTag.getCreateTs()) ? dockTag.getCreateTs().getTime() : null)
            .completeUserId(dockTag.getCompleteUserId())
            .completeTs(
                Objects.nonNull(dockTag.getCompleteTs()) ? dockTag.getCompleteTs().getTime() : null)
            .dockTagStatus(dockTag.getDockTagStatus())
            .lastChangedUserId(dockTag.getLastChangedUserId())
            .lastChangedTs(
                Objects.nonNull(dockTag.getLastChangedTs())
                    ? dockTag.getLastChangedTs().getTime()
                    : null)
            .scannedLocation(dockTag.getScannedLocation())
            .dockTagType(dockTag.getDockTagType())
            .facilityCountryCode(dockTag.getFacilityCountryCode())
            .facilityNum(dockTag.getFacilityNum())
            .build()
        : null;
  }

  @Override
  public InstructionResponse receiveDockTag(
      ReceiveDockTagRequest receiveDockTagRequest, HttpHeaders httpHeaders) {
    final String dockTagId = receiveDockTagRequest.getDockTagId();
    final String floorLineLocation = receiveDockTagRequest.getMappedFloorLineLocation();
    final String workstationLocation = receiveDockTagRequest.getWorkstationLocation();
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
    List<DockTag> attachedActiveDockTags = null;
    try {
      attachedActiveDockTags =
          validateForMultiManifestDockTag(dockTagFromDb, workstationLocation, floorLineLocation);
    } catch (ReceivingBadDataException exc) {
      if (ExceptionCodes.CONFLICT_DOCK_TAG_MMR.equals(exc.getErrorCode())
          && tenantSpecificConfigReader.isFeatureFlagEnabled(
              ReceivingConstants.FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED)) {
        // for floor line, instead of throwing exception, build a response and return that
        String commonItemsList = (String) exc.getValues()[0];
        InstructionResponse itemCollisionResponse = new InstructionResponseImplNew();
        itemCollisionResponse.setInstruction(
            InstructionUtils.getItemCollisionInstruction(
                dockTagFromDb.getDeliveryNumber(), commonItemsList));
        return itemCollisionResponse;
      } else
        throw new ReceivingBadDataException(exc.getErrorCode(), exc.getMessage(), exc.getValues());
    }

    updateDockTagStatusAndPublish(receiveDockTagRequest, dockTagFromDb, httpHeaders);

    LOGGER.info(
        "DTG: {} Publishing delivery and location message to ACL with location {}",
        dockTagId,
        floorLineLocation);
    List<DeliveryAndLocationMessage> deliveryAndLocationMessageList = new ArrayList<>();

    for (DockTag attachedActiveDockTag : attachedActiveDockTags) {
      DeliveryAndLocationMessage message = new DeliveryAndLocationMessage();
      message.setDeliveryNbr(attachedActiveDockTag.getDeliveryNumber().toString());
      if (!StringUtils.isEmpty(workstationLocation)) {
        LOGGER.info(
            "MMR: Dock tag {} found to be a part of multi manifest",
            attachedActiveDockTag.getDockTagId());
        // setting location for multi manifest dock tags
        if (Objects.equals(attachedActiveDockTag.getDockTagId(), dockTagId)) {
          // setting location for current dock tag
          LOGGER.info(
              "MMR: Dock tag {} to be linked to location {}",
              attachedActiveDockTag.getDockTagId(),
              workstationLocation);
          message.setLocation(workstationLocation);
        } else {
          // setting location for other dock tags
          LOGGER.info(
              "MMR: Dock tag {} to be linked to location {}",
              attachedActiveDockTag.getDockTagId(),
              attachedActiveDockTag.getWorkstationLocation());
          message.setLocation(attachedActiveDockTag.getWorkstationLocation());
        }
      } else {
        // setting location for single manifest dock tag
        LOGGER.info(
            "DTG: Dock tag {} to be linked to location {}",
            attachedActiveDockTag,
            floorLineLocation);
        message.setLocation(floorLineLocation);
      }
      message.setUserId(
          attachedActiveDockTag.getDockTagId().equals(dockTagId)
              ? userId
              : attachedActiveDockTag.getLastChangedUserId());
      deliveryAndLocationMessageList.add(message);
    }

    tenantSpecificConfigReader
        .getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DELIVERY_LINK_SERVICE,
            DeliveryLinkService.class)
        .updateDeliveryLink(deliveryAndLocationMessageList, httpHeaders);

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(null);
    instructionResponse.setInstruction(
        InstructionUtils.getPlaceOnConveyorInstruction(
            null, null, dockTagFromDb.getDeliveryNumber()));
    // return how to set if expiry is already ignored as fefo is not setting any more
    return instructionResponse;
  }

  @Override
  public ReceiveNonConDockTagResponse receiveNonConDockTag(
      String dockTagId, HttpHeaders httpHeaders) {
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
    validateNonConDockTag(dockTagFromDb, dockTagId);

    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    final Long deliveryNumber = dockTagFromDb.getDeliveryNumber();
    final String pbylLocation = dockTagFromDb.getScannedLocation();
    dockTagFromDb.setLastChangedUserId(userId);
    dockTagFromDb.setDockTagStatus(InstructionStatus.UPDATED);

    DeliveryDetails deliveryDetails;
    LocationInfo locationInfo;
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse;
    try {
      DeliveryService deliveryService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_SERVICE_KEY,
              DeliveryService.class);
      String deliveryByDeliveryNumberStr =
          deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
      deliveryDetails = gsonForDate.fromJson(deliveryByDeliveryNumberStr, DeliveryDetails.class);
      // TODO Remove this call once UI starts using pbyl scan location api
      locationInfo = locationService.getLocationInfoForPbylDockTag(pbylLocation);
      receiveNonConDockTagResponse =
          ReceiveNonConDockTagResponse.builder()
              .delivery(deliveryDetails)
              .locationInfo(locationInfo)
              .build();
    } catch (ReceivingException e) {
      Object[] values = new Object[2];
      values[0] = dockTagId;
      values[1] = e.getMessage();
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PROCESS_NON_CON_DOCK_TAG,
          String.format(ReceivingConstants.DOCK_TAG_ERROR_MESSAGE, dockTagId),
          values);
    }

    publishDockTagContainer(
        httpHeaders, dockTagFromDb.getDockTagId(), ReceivingConstants.STATUS_ACTIVE);
    dockTagPersisterService.saveDockTag(dockTagFromDb);
    return receiveNonConDockTagResponse;
  }

  @Override
  public UniversalInstructionResponse receiveUniversalTag(
      String dockTagId, String doorNumber, HttpHeaders httpHeaders) {
    LOGGER.warn("Cannot receive universal tag. No implementation for lpn in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  private void validateNonConDockTag(DockTag dockTag, String dockTagId) {
    if (!DockTagType.NON_CON.equals(dockTag.getDockTagType())) {
      Object[] values = new Object[1];
      values[0] = dockTagId;
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_NON_CON_DOCK_TAG,
          String.format(ReceivingConstants.DOCK_TAG_NOT_NON_CON_MESSAGE, dockTagId),
          values);
    }
  }

  private void validateFloorLineDockTag(DockTag dockTag, String dockTagId) {
    // TODO Change the condition to check for FLOOR_LINE once the data in production is ready
    if (DockTagType.NON_CON.equals(dockTag.getDockTagType())) {
      Object[] values = new Object[1];
      values[0] = dockTagId;
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_FLOOR_LINE_DOCK_TAG,
          String.format(ReceivingConstants.DOCK_TAG_NOT_FLOOR_LINE_MESSAGE, dockTagId),
          values);
    }
  }

  @Override
  @Transactional
  @InjectTenantFilter
  public void updateDockTagById(String dockTagId, InstructionStatus status, String userId) {
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    dockTagFromDb.setCompleteUserId(userId);
    dockTagFromDb.setCompleteTs(new Date());
    dockTagFromDb.setDockTagStatus(status);
    dockTagPersisterService.saveDockTag(dockTagFromDb);
  }

  @Override
  public String searchDockTag(SearchDockTagRequest searchDockTagRequest, InstructionStatus status) {
    List<String> deliveryNumbers = searchDockTagRequest.getDeliveryNumbers();
    validateDeliveryNumbers(deliveryNumbers);
    List<Long> longDeliveryNums = new ArrayList<>();
    for (String deliveryNumber : deliveryNumbers) {
      longDeliveryNums.add(Long.parseLong(deliveryNumber));
    }
    List<DockTag> dockTagResponse = null;
    if (StringUtils.isEmpty(status)) {
      LOGGER.info("Fetching docktag for deliveries {}", longDeliveryNums);
      dockTagResponse = dockTagPersisterService.getDockTagsByDeliveries(longDeliveryNums);
    } else {
      LOGGER.info("Fetching docktag for deliveries {} and status {}", longDeliveryNums, status);
      dockTagResponse =
          dockTagPersisterService.getDockTagsByDeliveriesAndStatuses(
              longDeliveryNums,
              status.equals(InstructionStatus.CREATED)
                  ? ReceivingUtils.getPendingDockTagStatus()
                  : Collections.singletonList(status));
    }
    return CollectionUtils.isEmpty(dockTagResponse) ? null : gson.toJson(dockTagResponse);
  }

  @Override
  public DockTagDTO searchDockTagById(String dockTagId) {
    DockTag dockTagResponse = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    DockTagDTO dockTagDTO = new DockTagDTO();
    if (Objects.nonNull(dockTagResponse)) {
      dockTagDTO.setDockTagId(dockTagResponse.getDockTagId());
      dockTagDTO.setDeliveryNumber(dockTagResponse.getDeliveryNumber());
      dockTagDTO.setDockTagStatus(dockTagResponse.getDockTagStatus());
      dockTagDTO.setDockTagType(dockTagResponse.getDockTagType());
    }
    return dockTagDTO;
  }

  private void validateDeliveryNumbers(List<String> deliveryNumbers) {
    for (String deliveryNumber : deliveryNumbers) {
      if (StringUtils.isEmpty(deliveryNumber) || !ReceivingUtils.isNumeric(deliveryNumber)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_DATA, ReceivingConstants.INVALID_DELIVERY_NUMBER);
      }
    }
  }

  @Deprecated
  @Override
  public CompleteDockTagResponse completeDockTags(
      CompleteDockTagRequest completeDockTagRequest, HttpHeaders headers) {

    List<String> docktags = completeDockTagRequest.getDocktags();

    List<DockTag> dockTagsFromDb = dockTagPersisterService.getDockTagsByDockTagIds(docktags);
    if (CollectionUtils.isEmpty(dockTagsFromDb)) {
      return CompleteDockTagResponse.builder().failed(docktags).build();
    }
    List<String> failed = new ArrayList<>();

    if (dockTagsFromDb.size() != docktags.size()) {
      List<String> dockTagIds =
          dockTagsFromDb.stream().map(DockTag::getDockTagId).collect(Collectors.toList());
      // validate if all dockIds are present in DB
      for (String dockTag : docktags) {
        if (!dockTagIds.contains(dockTag)) {
          failed.add(dockTag);
        }
      }
    }
    List<String> dockTagIds = new ArrayList<>();
    for (DockTag dockTag : dockTagsFromDb) {
      if (Objects.nonNull(dockTag.getCompleteTs())) {
        // This is already complete- no changes required
        continue;
      }
      dockTagIds.add(dockTag.getDockTagId());
      try {
        markCompleteAndDeleteFromInventory(headers, dockTag);
      } catch (ApplicationBaseException e) {
        failed.add(dockTag.getDockTagId());
      }
    }

    // Publish the Docktags based upon status
    if (!dockTagIds.isEmpty()) {
      publishDockTagContainer(headers, dockTagIds, ReceivingConstants.STATUS_COMPLETE);
    }
    // update DB
    dockTagPersisterService.saveAllDockTags(dockTagsFromDb);
    checkAndPublishPendingDockTags(
        headers,
        completeDockTagRequest.getDeliveryStatus(),
        completeDockTagRequest.getDeliveryNumber());

    List<String> success = new ArrayList<>();
    for (String s : docktags) {
      if (!failed.contains(s)) success.add(s);
    }

    return CompleteDockTagResponse.builder().failed(failed).success(success).build();
  }

  @Override
  public List<CompleteDockTagResponse> completeDockTagsForGivenDeliveries(
      CompleteDockTagRequestsList completeDockTagRequests, HttpHeaders headers) {

    List<String> docktags = new ArrayList<>();
    List<String> dockTagIds = new ArrayList<>();
    for (CompleteDockTagRequest completeDockTagRequest : completeDockTagRequests.getList())
      docktags.addAll(completeDockTagRequest.getDocktags());

    List<DockTag> dockTagsFromDb = dockTagPersisterService.getDockTagsByDockTagIds(docktags);

    List<String> validDockTagIds =
        dockTagsFromDb.stream().map(DockTag::getDockTagId).collect(Collectors.toList());

    if (validDockTagIds.size() > ACCConstants.MAX_DOCKTAGS_ALLOWED_TO_COMPLETE) {
      throw new ReceivingBadDataException(
          ExceptionCodes.MAX_ALLOWED_DOCKTAGS_TO_COMPLETE,
          ReceivingConstants.EXCEED_MAX_ALLOWED_DOCKTAG_REQUEST);
    }

    List<CompleteDockTagResponse> completeDockTagResponses = new ArrayList<>();
    if (CollectionUtils.isEmpty(validDockTagIds)) {
      for (CompleteDockTagRequest completeDockTagRequest : completeDockTagRequests.getList()) {
        CompleteDockTagResponse completeDockTagResponse = new CompleteDockTagResponse();
        completeDockTagResponse.setFailed(completeDockTagRequest.getDocktags());
        completeDockTagResponse.setDeliveryNumer(completeDockTagRequest.getDeliveryNumber());
        completeDockTagResponses.add(completeDockTagResponse);
      }
      return completeDockTagResponses;
    }

    Map<String, DockTag> dockTagMap = new HashMap<>();
    for (DockTag dockTag : dockTagsFromDb) {
      dockTagMap.put(dockTag.getDockTagId(), dockTag);
    }

    for (CompleteDockTagRequest completeDockTagRequest : completeDockTagRequests.getList()) {
      List<String> failedToCompleteDockTags = new ArrayList<>();
      List<String> succeedToCompleteDockTags = new ArrayList<>();
      CompleteDockTagResponse completeDockTagResponse = new CompleteDockTagResponse();
      for (String dockTagId : completeDockTagRequest.getDocktags()) {
        if (!validDockTagIds.contains(dockTagId)) {
          failedToCompleteDockTags.add(dockTagId);
          continue;
        }
        DockTag dockTag = dockTagMap.get(dockTagId);
        if (Objects.nonNull(dockTag.getCompleteTs())) {
          succeedToCompleteDockTags.add(dockTagId);
          continue;
        }
        try {
          dockTagIds.add(dockTag.getDockTagId());
          markCompleteAndDeleteFromInventory(headers, dockTag);
          succeedToCompleteDockTags.add(dockTagId);
        } catch (ApplicationBaseException e) {
          failedToCompleteDockTags.add(dockTagId);
        }
      }

      completeDockTagResponse.setDeliveryNumer(completeDockTagRequest.getDeliveryNumber());
      if (!failedToCompleteDockTags.isEmpty())
        completeDockTagResponse.setFailed(failedToCompleteDockTags);
      if (!succeedToCompleteDockTags.isEmpty())
        completeDockTagResponse.setSuccess(succeedToCompleteDockTags);
      completeDockTagResponses.add(completeDockTagResponse);
    }

    // Publish the Docktags based upon status
    if (!dockTagIds.isEmpty()) {
      publishDockTagContainer(headers, dockTagIds, ReceivingConstants.STATUS_COMPLETE);
    }

    // update DB
    dockTagPersisterService.saveAllDockTags(dockTagsFromDb);
    for (CompleteDockTagRequest completeDockTagRequest : completeDockTagRequests.getList()) {
      checkAndPublishPendingDockTags(
          headers,
          completeDockTagRequest.getDeliveryStatus(),
          completeDockTagRequest.getDeliveryNumber());
    }

    return completeDockTagResponses;
  }

  @Override
  public InstructionResponse createDockTag(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    if (DockTagType.FLOOR_LINE.equals(createDockTagRequest.getDockTagType())) {
      return accInstructionService.createFloorLineDockTag(
          getInstructionRequest(createDockTagRequest), headers);
    } else {
      return accInstructionService.createNonConDockTag(
          getInstructionRequest(createDockTagRequest),
          headers,
          createDockTagRequest.getMappedPbylArea());
    }
  }

  @Override
  public DockTagResponse createDockTags(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    LOGGER.warn("Cannot create multiple dock tags. No implementation in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  private InstructionRequest getInstructionRequest(CreateDockTagRequest createDockTagRequest) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber(createDockTagRequest.getDeliveryNumber().toString());
    instructionRequest.setDoorNumber(createDockTagRequest.getDoorNumber());
    return instructionRequest;
  }

  protected List<DockTag> validateForMultiManifestDockTag(
      DockTag dockTag, String workstationLocation, String floorLineLocation) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_MANIFEST)
        && !StringUtils.isEmpty(workstationLocation)) {
      LOGGER.info(
          "MMR: Entering multi manifest receiving for dock tag {} ", dockTag.getDockTagId());

      Long deliveryNumber = dockTag.getDeliveryNumber();

      // Get all dock tags attached at the ACL
      List<DockTag> attachedDockTags =
          dockTagPersisterService.getAttachedDockTagsByScannedLocation(floorLineLocation);

      LOGGER.info(
          "MMR: Found {} dock tag(s) linked at the ACL {}", attachedDockTags, floorLineLocation);

      List<DockTag> inactiveDockTags = new ArrayList<>(); // contains stale dock tags
      List<Long> activeDeliveries =
          new ArrayList<>(); // contains active deliveries which need to be linked

      // find unique delivery number list
      List<Long> attachedUniqueDeliveryNumbers =
          attachedDockTags
              .stream()
              .map(DockTag::getDeliveryNumber)
              .distinct()
              .collect(Collectors.toList());

      DockTag activeDockTagAtLocation =
          attachedDockTags
              .stream()
              .filter(
                  dt ->
                      !dt.getDockTagId().equals(dockTag.getDockTagId())
                          && workstationLocation.equals(dt.getWorkstationLocation()))
              .findAny()
              .orElse(null);

      if (Objects.nonNull(activeDockTagAtLocation)) {
        throw new ReceivingConflictException(
            ExceptionCodes.CONFLICT_DOCK_TAG_MMR_LOCATION_IN_USE,
            String.format(
                ReceivingConstants.DOCK_TAG_LOCATION_IN_US_MMR_ERROR_MESSAGE, workstationLocation),
            activeDockTagAtLocation.getDockTagId());
      }

      LOGGER.info(
          "MMR: Found {} deliveries linked at the ACL {}",
          attachedUniqueDeliveryNumbers,
          floorLineLocation);

      // For each unique delivery, find out whether it is active or idle
      for (Long attachedUniqueDeliveryNumber : attachedUniqueDeliveryNumbers) {

        // get idle duration for dock tag from CCM
        double maxAllowedDeliveryIdleTimeInHours =
            tenantSpecificConfigReader
                .getCcmConfigValue(
                    TenantContext.getFacilityNum(),
                    ACCConstants.MAX_DELIVERY_MMR_IDLE_DURATION_IN_HOUR)
                .getAsDouble();

        // get the latest receipt for that delivery
        Receipt recentReceipt =
            receiptService.findLatestReceiptByDeliveryNumber(attachedUniqueDeliveryNumber);

        // find idle duration for that delivery
        double actualDeliveryIdleDuration =
            Objects.nonNull(recentReceipt)
                ? ReceivingUtils.convertMiliSecondsInhours(
                    (new Date().getTime() - recentReceipt.getCreateTs().getTime()))
                : maxAllowedDeliveryIdleTimeInHours
                    + 1; // since we will now check each dock tag for idleness, here we can include
        // no receipt deliveries

        if (actualDeliveryIdleDuration > maxAllowedDeliveryIdleTimeInHours) {

          long cutoffTime =
              new Date().getTime()
                  - (long) (maxAllowedDeliveryIdleTimeInHours * MILLISECONDS_IN_AN_HOUR);

          LOGGER.info(
              "MMR: Delivery: {} was found to be inactive, Idle duration: {}",
              attachedUniqueDeliveryNumber,
              actualDeliveryIdleDuration);
          // find all attached dock tags for that delivery
          List<DockTag> attachedDockTagByDelivery =
              attachedDockTags
                  .stream()
                  .filter(
                      dt ->
                          dt.getDeliveryNumber().equals(attachedUniqueDeliveryNumber)
                              // exclude currently scanned dock tag
                              && !dt.getDockTagId().equalsIgnoreCase(dockTag.getDockTagId()))
                  .collect(Collectors.toList());

          // add one more filter of dock tag receive time
          List<DockTag> attachedDockTagsByDeliveryFilteringByDockTagReceiveTime =
              attachedDockTagByDelivery
                  .stream()
                  .filter(dt -> dt.getLastChangedTs().before(new Date(cutoffTime)))
                  .collect(Collectors.toList());

          if (attachedDockTagsByDeliveryFilteringByDockTagReceiveTime.size()
              < attachedDockTagByDelivery.size()) {
            // some of the dock tags of this delivery are active, so need to add to check its items
            activeDeliveries.add(attachedUniqueDeliveryNumber);
          }

          // add to inactive dock tag list so that they can be auto completed
          inactiveDockTags.addAll(attachedDockTagsByDeliveryFilteringByDockTagReceiveTime);

          // remove from active dock tag list so that delivery link is correct
          attachedDockTags.removeAll(attachedDockTagsByDeliveryFilteringByDockTagReceiveTime);
        } else {
          LOGGER.info(
              "MMR: Delivery {} was found to be active, Idle duration: {}",
              attachedUniqueDeliveryNumber,
              actualDeliveryIdleDuration);

          // no stale dock tags found for this delivery, so add in active delivery list
          activeDeliveries.add(attachedUniqueDeliveryNumber);
        }
      }

      // remove current delivery number from the list, so that we can match with existing deliveries
      activeDeliveries.remove(deliveryNumber);

      // if current dock tag was removed due to other stale dock tags, add it
      if (!attachedDockTags.contains(dockTag)) {
        LOGGER.info(
            "MMR: Current dock tag {} delivery {} was found to be active. Adding dock tag back to active list",
            dockTag.getDockTagId(),
            deliveryNumber);
        inactiveDockTags.remove(dockTag);
        attachedDockTags.add(dockTag);
      }

      // no need to run query if no other delivery was found
      if (!activeDeliveries.isEmpty()) {
        checkIfItemCollisionExists(dockTag, deliveryNumber, activeDeliveries);
      }

      LOGGER.info("MMR: Inactive dock tags that will be auto-completed {}", inactiveDockTags);

      // complete all inactive dock tags
      completeDockTags(inactiveDockTags);

      LOGGER.info("MMR: Active dock tags that will be linked {}", attachedDockTags);
      // return the attached dock tags so that link can be performed
      return attachedDockTags;
    }

    // no multi manifest, so return the current dock tag to be linked
    return Collections.singletonList(dockTag);
  }

  private void checkIfItemCollisionExists(
      DockTag dockTag, Long deliveryNumber, List<Long> activeDeliveries) {

    List<Object> commonItems =
        new ArrayList<>(
            labelDataService.findIfCommonItemExistsForDeliveries(activeDeliveries, deliveryNumber));
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ACCConstants.ENABLE_POSSIBLE_UPC_BASED_ITEM_COLLISION_MMR)) {
      findCommonItemsByPossibleUPC(activeDeliveries, deliveryNumber, commonItems);
    }
    if (!commonItems.isEmpty()) {
      LOGGER.info("MMR: Common items found for dock tag {}", dockTag.getDockTagId());
      throw new ReceivingBadDataException(
          ExceptionCodes.CONFLICT_DOCK_TAG_MMR,
          String.format(
              ReceivingConstants.DOCK_TAG_COMMON_ITEM_UPC_MMR_ERROR_MESSAGE,
              dockTag.getDockTagId()),
          formatCommonItemList(commonItems),
          String.valueOf(deliveryNumber));
    }
  }

  private void findCommonItemsByPossibleUPC(
      List<Long> activeDeliveries, Long currentDeliveryNumber, List<Object> commonItems) {

    // Check common items by possibleUPC
    // Assuming multiple 'distinct' possibleUPC json are available for given delivery
    // - Query label_data for distinct possibleUPC json for current delivery number

    List<Pair<Long, String>> currentDeliveryItemPossibleUPCPairs =
        labelDataService.findItemPossibleUPCPairsForDeliveryNumber(currentDeliveryNumber);
    Map<Long, Set<String>> currentDeliveryItemUPCsMap = new HashMap<>();
    for (Pair<Long, String> itemPossibleUPCPair :
        CollectionUtils.emptyIfNull(currentDeliveryItemPossibleUPCPairs)) {
      Long itemNumber = itemPossibleUPCPair.getKey();
      PossibleUPC possibleUPC = gson.fromJson(itemPossibleUPCPair.getValue(), PossibleUPC.class);
      Set<String> upcSet = currentDeliveryItemUPCsMap.getOrDefault(itemNumber, new HashSet<>());
      upcSet.addAll(possibleUPC.getOrderableGtinAndCatalogGtin());
      currentDeliveryItemUPCsMap.put(itemNumber, upcSet);
    }

    List<String> activeDeliveriesPossibleUPCsList =
        labelDataService.findPossibleUPCsForDeliveryNumbersIn(activeDeliveries);
    Set<String> activeDeliveriesUPCSet = new HashSet<>();
    for (String possibleUPCJson : CollectionUtils.emptyIfNull(activeDeliveriesPossibleUPCsList)) {
      PossibleUPC possibleUPC = gson.fromJson(possibleUPCJson, PossibleUPC.class);
      activeDeliveriesUPCSet.addAll(possibleUPC.getOrderableGtinAndCatalogGtin());
    }

    currentDeliveryItemUPCsMap.forEach(
        (itemNumber, upcSet) -> {
          if (!commonItems.contains(itemNumber)) {
            Set<String> commonUPCs =
                upcSet
                    .stream()
                    .filter(activeDeliveriesUPCSet::contains)
                    .collect(Collectors.toSet());
            if (!CollectionUtils.isEmpty(commonUPCs)) {
              commonItems.add(itemNumber);
            }
          }
        });
  }

  private String formatCommonItemList(List<Object> commonItems) {
    if (commonItems.isEmpty()) {
      return ACCConstants.NO_COMMON_ITEMS;
    }
    String commonItemsString =
        commonItems
            .stream()
            .limit(ACCConstants.MAX_ITEMS_IN_MMR_ERROR)
            .map(String::valueOf)
            .collect(Collectors.joining(", "));

    return commonItems.size() > ACCConstants.MAX_ITEMS_IN_MMR_ERROR
        ? commonItemsString + "..."
        : commonItemsString;
  }

  @Override
  protected void markCompleteAndDeleteFromInventory(HttpHeaders headers, DockTag dockTag) {
    inventoryService.deleteContainer(dockTag.getDockTagId(), headers);
    // set completed status, only if delete in inventory is successful.
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteUserId(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    if (isRoboDepalCellDockTag(dockTag.getCompleteUserId())) {
      String isoTimestamp = headers.getFirst(ReceivingConstants.DOCKTAG_EVENT_TIMESTAMP);
      dockTag.setCompleteTs(ReceivingUtils.parseIsoTimeFormat(isoTimestamp));
    } else {
      dockTag.setCompleteTs(new Date());
    }
  }

  public boolean isRoboDepalCellDockTag(String userId) {
    return tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED)
        && !org.apache.commons.lang3.StringUtils.isEmpty(appConfig.getRoboDepalUserId())
        && appConfig.getRoboDepalUserId().equalsIgnoreCase(userId);
  }

  @Override
  public void updateDockTagStatusAndPublish(
      ReceiveDockTagRequest receiveDockTagRequest, DockTag dockTagFromDb, HttpHeaders httpHeaders) {
    final String dockTagId = receiveDockTagRequest.getDockTagId();
    final String floorLineLocation = receiveDockTagRequest.getMappedFloorLineLocation();
    final String workstationLocation = receiveDockTagRequest.getWorkstationLocation();
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    validateFloorLineDockTag(dockTagFromDb, dockTagId);
    dockTagFromDb.setLastChangedUserId(userId);
    dockTagFromDb.setDockTagStatus(InstructionStatus.UPDATED);
    dockTagFromDb.setScannedLocation(floorLineLocation);
    dockTagFromDb.setWorkstationLocation(workstationLocation);

    publishDockTagContainer(
        httpHeaders, dockTagFromDb.getDockTagId(), ReceivingConstants.STATUS_ACTIVE);

    dockTagPersisterService.saveDockTag(dockTagFromDb);
  }
}
