package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.UniversalInstructionResponse;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(ReceivingConstants.DEFAULT_DOCK_TAG_SERVICE)
public class DockTagServiceImpl extends DockTagService implements Purge {

  @Override
  public List<CompleteDockTagResponse> completeDockTagsForGivenDeliveries(
      CompleteDockTagRequestsList completeDockTagRequest, HttpHeaders headers) {
    LOGGER.warn("Cannot complete dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(DockTagServiceImpl.class);

  @Override
  public DockTag createDockTag(
      String dockTagId, Long deliveryNumber, String userId, DockTagType dockTagType) {
    LOGGER.warn("Cannot create dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public void saveDockTag(DockTag dockTag) {
    LOGGER.warn("Cannot save dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public Integer countOfOpenDockTags(Long deliveryNumber) {
    LOGGER.warn(
        "Cannot count the number of dock tags. No implementation for dock tag in this tenant");
    return null;
  }

  @Override
  public String completeDockTag(String dockTagId, HttpHeaders httpHeaders) {
    LOGGER.warn("Cannot complete dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DockTagResponse partialCompleteDockTag(
      CreateDockTagRequest createDockTagRequest,
      String dockTagId,
      boolean isRetryCompleteFlag,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.warn("Cannot complete workstation dock tag. No implementation available in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public InstructionResponse receiveDockTag(
      ReceiveDockTagRequest receiveDockTagRequest, HttpHeaders httpHeaders) {
    LOGGER.warn(
        "Cannot receive floor line dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public void updateDockTagStatusAndPublish(
      ReceiveDockTagRequest receiveDockTagRequest, DockTag dockTagFromDb, HttpHeaders httpHeaders) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public ReceiveNonConDockTagResponse receiveNonConDockTag(
      String dockTagId, HttpHeaders httpHeaders) {
    LOGGER.warn("Cannot receive non con dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public UniversalInstructionResponse receiveUniversalTag(
      String dockTagId, String doorNumber, HttpHeaders httpHeaders) {
    LOGGER.warn("No implementation for lpn in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public void updateDockTagById(String dockTagId, InstructionStatus completed, String userId) {
    LOGGER.warn("Cannot update dock tag. No implementation for dock tag in this tenant");
  }

  @Override
  public String searchDockTag(SearchDockTagRequest searchDockTagRequest, InstructionStatus status) {
    LOGGER.warn("No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public CompleteDockTagResponse completeDockTags(
      CompleteDockTagRequest completeDockTagRequest, HttpHeaders headers) {
    LOGGER.warn("Cannot complete dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public InstructionResponse createDockTag(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DockTagResponse createDockTags(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<DockTag> dockTagList =
        dockTagPersisterService.getDockTagsByIdGreaterThanEqual(
            purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    dockTagList =
        dockTagList
            .stream()
            .filter(item -> item.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(DockTag::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(dockTagList)) {
      LOGGER.info("Purge DOCK_TAG: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = dockTagList.get(dockTagList.size() - 1).getId();

    LOGGER.info(
        "Purge DOCK_TAG: {} records : ID {} to {} : START",
        dockTagList.size(),
        dockTagList.get(0).getId(),
        lastDeletedId);
    dockTagPersisterService.deleteAllDockTags(dockTagList);
    LOGGER.info("Purge DOCK_TAG: END");
    return lastDeletedId;
  }
}
