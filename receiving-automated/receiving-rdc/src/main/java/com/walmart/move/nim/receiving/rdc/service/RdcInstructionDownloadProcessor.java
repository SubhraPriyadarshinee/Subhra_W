package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.EventType.*;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.azure.AzureStorageUtils;
import com.walmart.move.nim.receiving.core.client.orderservice.OrderServiceRestApiClient;
import com.walmart.move.nim.receiving.core.client.orderservice.model.LpnUpdateRequest;
import com.walmart.move.nim.receiving.core.client.orderservice.model.LpnsInfo;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.AzureBlobException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.instruction.*;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_KAFKA_INSTRUCTION_DOWNLOAD_PROCESSOR)
public class RdcInstructionDownloadProcessor implements EventProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcInstructionDownloadProcessor.class);

  @Autowired private AzureStorageUtils azureStorageUtils;
  @Autowired private Gson gson;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private LabelDataService labelDataService;
  @Autowired private LabelDownloadEventService labelDownloadEventService;
  @Autowired RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcOfflineReceiveService rdcOfflineReceiveService;
  @ManagedConfiguration RdcManagedConfig rdcManagedConfig;
  @Autowired private OrderServiceRestApiClient orderServiceRestApiClient;
  @Autowired private ContainerPersisterService containerPersisterService;

  @Value("${label.data.insert.batch.size:100}")
  private int labelDataBatchSize;

  @Value("${fetch.existing.lpn.limit:1000}")
  private int fetchExistingLpnLimit;

  private static final Logger LOG = LoggerFactory.getLogger(RdcInstructionDownloadProcessor.class);

  @Override
  public void processEvent(MessageData messageData) {
    try {
      InstructionDownloadMessageDTO instructionDownloadMessageDTO =
          (InstructionDownloadMessageDTO) messageData;
      List<String> eventTypeList =
          instructionDownloadMessageDTO.getHttpHeaders().get(ReceivingConstants.EVENT_TYPE);
      String eventTypeVal =
          CollectionUtils.isNotEmpty(eventTypeList)
              ? eventTypeList.get(0)
              : ReceivingConstants.EMPTY_STRING;
      LOGGER.info("Instruction download event type {}", eventTypeVal);

      EventType eventType = EventType.valueOfEventType(eventTypeVal);
      switch (eventType) {
        case OFFLINE_RECEIVING:
          processOfflineLabelsGeneratedEvent(instructionDownloadMessageDTO);
          break;
        case LABELS_GENERATED:
        case LABELS_UPDATED:
          processLabelsGeneratedEvent(instructionDownloadMessageDTO);
          break;
        case LABELS_CANCELLED:
          processLabelsCancelledEvent(instructionDownloadMessageDTO);
          break;
        case PO_CANCELLED:
          processPOCancelledEvent(instructionDownloadMessageDTO);
          break;
        case PO_LINE_CANCELLED:
          processPOLineCancelledEvent(instructionDownloadMessageDTO);
          break;
        default:
          LOGGER.error("Invalid instruction download event type {}", eventTypeVal);
          break;
      }
    } catch (Exception exception) {
      LOG.error("Error occurred while instruction download processEvent", exception);
    }
  }

  /**
   * Process offline receiving event implementation
   *
   * @param instructionDownloadMessageDTO
   */
  public void processOfflineLabelsGeneratedEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    List<LabelData> labelDataList = null;
    try {
      // Transform & persist the label data
      labelDataList = downloadLabelAndPersist(instructionDownloadMessageDTO);

      if (CollectionUtils.isNotEmpty(labelDataList)) {
        LOGGER.info(
            "Label data list fetched from Blob with item number : {}, having label data size {} with delivery nbr {}",
            labelDataList.get(0).getItemNumber(),
            labelDataList.size(),
            instructionDownloadMessageDTO.getDeliveryNumber());
        // Trigger offline flow
        LOGGER.info(
            "Triggering Offline Receiving flow for delivery : {}",
            instructionDownloadMessageDTO.getDeliveryNumber());
        rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
            labelDataList, instructionDownloadMessageDTO);
      } else {
        LOGGER.info(
            "Offline Receiving flow didn't trigger due to ineligibility for delivery : {}",
            instructionDownloadMessageDTO.getDeliveryNumber());
      }
    } catch (Exception exception) {
      LOG.error(
          "Error occurred while processing offline receiving flow for delivery: {}",
          instructionDownloadMessageDTO.getDeliveryNumber(),
          exception);
    }
  }

  /**
   * Labels Generated Event implementation
   *
   * @param instructionDownloadMessageDTO
   */
  private void processLabelsGeneratedEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    TenantContext.get().setLabelDownloadEventIncludingAutomationStart(System.currentTimeMillis());
    List<LabelData> labelDataList = null;
    labelDataList = downloadLabelAndPersist(instructionDownloadMessageDTO);
    try {
      LOGGER.info("Triggering automation flow");
      if (rdcManagedConfig.isPublishLabelsToHawkeyeByAsyncEnabled()) {
        rdcLabelGenerationService.processLabelsForAutomationAsync(
            instructionDownloadMessageDTO, labelDataList);
      } else {
        rdcLabelGenerationService.processLabelsForAutomation(
            instructionDownloadMessageDTO, labelDataList);
      }
    } catch (Exception exception) {
      LOG.error("Error occurred while sending labels to Hawkeye", exception);
    }
  }

  private List<LabelData> downloadLabelAndPersist(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    TenantContext.get().setInstructionDownloadStart(System.currentTimeMillis());
    LOGGER.info("Instruction download labels generated event started");
    List<LabelData> labelDataList = null;
    try {
      if (CollectionUtils.isNotEmpty(instructionDownloadMessageDTO.getBlobStorage())) {
        InstructionDownloadBlobStorageDTO blobStorageDTO =
            instructionDownloadMessageDTO.getBlobStorage().get(0);
        LOGGER.info("Instruction download data blob url {}", blobStorageDTO.getBlobUri());
        TenantContext.get().setInstructionBlobDownloadStart(System.currentTimeMillis());
        byte blobData[] = null;
        try {
          String blobName = getBlobName(blobStorageDTO.getBlobUri());
          String containerName = getContainerName(blobStorageDTO.getBlobUri());
          blobData = azureStorageUtils.downloadWithRetry(containerName, blobName);
          LOGGER.info(
              "Successfully downloaded instructions for blob URL:{}", blobStorageDTO.getBlobUri());
        } catch (AzureBlobException ex) {
          LOGGER.error(
              "Error occurred while downloading blob data from url {}",
              blobStorageDTO.getBlobUri(),
              ex);
          return labelDataList;
        } finally {
          TenantContext.get().setInstructionBlobDownloadEnd(System.currentTimeMillis());
        }
        labelDataList = saveLabelData(blobData, instructionDownloadMessageDTO);
      } else {
        LOGGER.info(
            "Missing instruction download blobStorage details, because labels were already generated for delivery number {}",
            instructionDownloadMessageDTO.getDeliveryNumber());
      }
      TenantContext.get().setInstructionDownloadEnd(System.currentTimeMillis());
    } catch (Exception exception) {
      LOG.error("Error occurred while instruction labels generated event", exception);
    } finally {
      labelsGeneratedEventTimeTakenLog();
    }
    return labelDataList;
  }

  /**
   * Label data insertion or update
   *
   * @param blobData
   * @param instructionDownloadMessageDTO
   */
  private List<LabelData> saveLabelData(
      byte[] blobData, InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    List<LabelData> labelDataList = null;
    try {
      List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
          transformInstructionBlobData(blobData);
      LOGGER.info(
          "Instruction download total records {}", instructionDownloadBlobDataDTOList.size());
      /**
       * Need to set this blob data in miscOfflineRecvInfo that's needed for container and
       * containerItem preparation
       */
      List<String> eventTypeList =
          instructionDownloadMessageDTO.getHttpHeaders().get(ReceivingConstants.EVENT_TYPE);
      String eventTypeVal =
          org.apache.commons.collections4.CollectionUtils.isNotEmpty(eventTypeList)
              ? eventTypeList.get(0)
              : ReceivingConstants.EMPTY_STRING;
      EventType eventType = EventType.valueOfEventType(eventTypeVal);
      if (OFFLINE_RECEIVING.equals(eventType)) {
        Map<String, InstructionDownloadBlobDataDTO> miscOfflineRcvInfoMap =
            instructionDownloadBlobDataDTOList
                .stream()
                .collect(
                    Collectors.toMap(
                        instructionDownloadBlobDataDTO ->
                            instructionDownloadBlobDataDTO.getContainer().getTrackingId(),
                        Function.identity()));
        instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(miscOfflineRcvInfoMap);
      }

      if (LABELS_UPDATED.equals(eventType)) {
        LOGGER.info(
            "Instruction download for Handling code change LABELS UPDATE event for purchaseReferenceNumber: {}, "
                + "itemNumber :{}",
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber(),
            instructionDownloadMessageDTO.getItemNumber());
        labelDataList =
            downloadLabelsForHandlingCodeUpdates(
                instructionDownloadBlobDataDTOList, instructionDownloadMessageDTO);
        LOGGER.info("Completed label instruction data insertion for item handling code updates");
      } else {
        labelDataList =
            downloadLabelsFromBlobData(
                instructionDownloadBlobDataDTOList, instructionDownloadMessageDTO, eventType);
        LOGGER.info("Completed label instruction data insertion");
      }
    } catch (Exception exception) {
      LOG.error("Error occurred while persisting labels in label data", exception);
    }
    return labelDataList;
  }

  /**
   * @param instructionDownloadBlobDataDTOList
   * @param instructionDownloadMessageDTO
   * @return
   */
  private List<LabelData> downloadLabelsFromBlobData(
      List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList,
      InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      EventType eventType) {
    List<LabelData> labelDataList = new ArrayList<>();
    List<String> trackingIds =
        instructionDownloadBlobDataDTOList
            .stream()
            .map(instruction -> instruction.getContainer().getTrackingId())
            .collect(Collectors.toList());
    TenantContext.get().setFetchExistingLpnInLabelDataStart(System.currentTimeMillis());
    List<LabelData> existingLabelDataList = new ArrayList<>();
    // limiting the lpns while loading from LABEL_DATA table
    Lists.partition(trackingIds, fetchExistingLpnLimit)
        .forEach(
            lpns -> {
              existingLabelDataList.addAll(labelDataService.findByTrackingIdIn(lpns));
            });
    TenantContext.get().setFetchExistingLpnInLabelDataEnd(System.currentTimeMillis());

    Map<String, LabelData> labelDataByLpns =
        existingLabelDataList
            .stream()
            .collect(Collectors.toMap(LabelData::getTrackingId, Function.identity()));

    /**
     * labelDataByLpns has tracking id that are having existing data in label_data table This needs
     * to be deleted from container / container item table This ATLAS generated LPN collision is
     * observed in production for WPM REPACK containers.
     */
    if (OFFLINE_RECEIVING.equals(eventType)) {
      List<String> existingLabelDataTrackingIds = new ArrayList<>(labelDataByLpns.keySet());
      LOGGER.info("Existing TrackingIds present in label_data {}", existingLabelDataTrackingIds);
      containerPersisterService.deleteContainerAndContainerItemsGivenTrackingId(
          existingLabelDataTrackingIds);
    }

    labelDataList =
        transformLabelDataEntity(
            instructionDownloadMessageDTO, instructionDownloadBlobDataDTOList, labelDataByLpns);

    /*
     * Adding the feature flag to avoid persistence of the label data for Offline Flow.
     * The persistence of Label Data for Offline would occur in the same transaction as of
     * receipt, container and container items.
     */
    if (CollectionUtils.isNotEmpty(labelDataList) && !OFFLINE_RECEIVING.equals(eventType)) {
      TenantContext.get().setInsertLabelDataStart(System.currentTimeMillis());
      Lists.partition(labelDataList, labelDataBatchSize)
          .forEach(labelDataBatch -> labelDataService.saveAllAndFlush(labelDataBatch));
      TenantContext.get().setInsertLabelDataEnd(System.currentTimeMillis());
    } else if (CollectionUtils.isNotEmpty(labelDataList)
        && OFFLINE_RECEIVING.equals(eventType)
        && (!rdcManagedConfig.getEnableSingleTransactionForOffline())) {
      LOGGER.info(
          "Enable prepare consolidated containers still '{}' for deliveryNbr '{}' ",
          rdcManagedConfig.getEnableSingleTransactionForOffline(),
          labelDataList.get(0).getDeliveryNumber());
      TenantContext.get().setInsertLabelDataStart(System.currentTimeMillis());
      Lists.partition(labelDataList, labelDataBatchSize)
          .forEach(labelDataBatch -> labelDataService.saveAllAndFlush(labelDataBatch));
      TenantContext.get().setInsertLabelDataEnd(System.currentTimeMillis());
    }
    return labelDataList;
  }

  /**
   * This method downloads the new labels published by orders for the handling code updates. If the
   * previousTrackingIds exists with Available status then we need to cancel those lpns. The
   * cancelled LPNS will be Voided at Hawkeye end. The new labels shared by orders will be persisted
   * in Receiving as new labels. Once the new labels were created at Receiving in Label Data we will
   * send the label updates to OP with the Successfully updated & failed to cancel the lPNs as those
   * are already in Received/Cancelled status.
   *
   * @param instructionDownloadBlobDataDTOList
   * @param instructionDownloadMessageDTO
   * @return
   */
  private List<LabelData> downloadLabelsForHandlingCodeUpdates(
      List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList,
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    List<LabelData> previousTrackingIdsLabelDataList = new ArrayList<>();
    List<LabelData> updatedTrackingIdsLabelDataList = new ArrayList<>();
    List<LabelData> updateLabelDataList = new ArrayList<>();
    List<String> previousLabelTrackingIds = new ArrayList<>();

    // break pack instructions
    boolean isChildContainersExist =
        CollectionUtils.isNotEmpty(
            instructionDownloadBlobDataDTOList
                .stream()
                .filter(
                    instructionDownloadBlobDataDTO ->
                        CollectionUtils.isNotEmpty(
                            instructionDownloadBlobDataDTO.getChildContainers()))
                .collect(Collectors.toList()));

    if (isChildContainersExist) {
      List<String> childTrackingIds = new ArrayList<>();
      instructionDownloadBlobDataDTOList.forEach(
          instructionDownloadBlobDataDTO -> {
            if (CollectionUtils.isNotEmpty(instructionDownloadBlobDataDTO.getChildContainers())) {
              instructionDownloadBlobDataDTO
                  .getChildContainers()
                  .forEach(
                      childContainer -> {
                        childTrackingIds.add(childContainer.getPrevTrackingId());
                      });
            }
          });
      previousLabelTrackingIds = childTrackingIds.stream().distinct().collect(Collectors.toList());
    } else {
      previousLabelTrackingIds =
          instructionDownloadBlobDataDTOList
              .stream()
              .map(instruction -> instruction.getContainer().getPrevTrackingId())
              .distinct()
              .collect(Collectors.toList());
    }

    TenantContext.get().setFetchPreviousTrackingInLabelDataStart(System.currentTimeMillis());
    Lists.partition(previousLabelTrackingIds, fetchExistingLpnLimit)
        .forEach(
            lpns -> {
              previousTrackingIdsLabelDataList.addAll(labelDataService.findByTrackingIdIn(lpns));
            });
    TenantContext.get().setFetchPreviousTrackingInLabelDataEnd(System.currentTimeMillis());

    /* filter only the available trackingIds which needs to be persisted as newly created labels due to
    handling code change */
    List<LabelData> prevTrackingIdsLabelDataListWithAvailableStatus =
        previousTrackingIdsLabelDataList
            .stream()
            .filter(
                previousTrackingIdLabelData ->
                    previousTrackingIdLabelData
                        .getStatus()
                        .equals(LabelInstructionStatus.AVAILABLE.name()))
            .collect(Collectors.toList());
    List<LabelData> prevTrackingIdsLabelDataListWithNonAvailableStatus =
        previousTrackingIdsLabelDataList
            .stream()
            .filter(
                previousTrackingIdLabelData ->
                    !previousTrackingIdLabelData
                        .getStatus()
                        .equals(LabelInstructionStatus.AVAILABLE.name()))
            .collect(Collectors.toList());

    // cancel lpns in label data
    if (CollectionUtils.isNotEmpty(prevTrackingIdsLabelDataListWithAvailableStatus)) {
      rdcLabelGenerationService.updateLabelStatusToCancelled(
          prevTrackingIdsLabelDataListWithAvailableStatus,
          instructionDownloadMessageDTO.getHttpHeaders(),
          ReceivingConstants.PURCHASE_REF_TYPE_DA);
      LOGGER.info("Update Label status as cancelled for the previous Available label data");
    }

    List<InstructionDownloadBlobDataDTO> filteredInstructionDataForAvailableTrackingIds =
        instructionDownloadBlobDataDTOList
            .stream()
            .filter(
                instructionDownloadBlobDataDTO ->
                    filterPrevTrackingIdsFromBlobData(
                        instructionDownloadBlobDataDTO,
                        prevTrackingIdsLabelDataListWithAvailableStatus,
                        isChildContainersExist))
            .collect(Collectors.toList());

    List<InstructionDownloadBlobDataDTO> filteredInstructionDataForNonAvailableTrackingIds =
        instructionDownloadBlobDataDTOList
            .stream()
            .filter(
                instructionDownloadBlobDataDTO ->
                    filterPrevTrackingIdsFromBlobData(
                        instructionDownloadBlobDataDTO,
                        prevTrackingIdsLabelDataListWithNonAvailableStatus,
                        isChildContainersExist))
            .collect(Collectors.toList());

    List<String> updatedLabelTrackingIdsToBePersisted =
        filteredInstructionDataForAvailableTrackingIds
            .stream()
            .map(
                instructionDownloadBlobDataDTO ->
                    instructionDownloadBlobDataDTO.getContainer().getTrackingId())
            .distinct()
            .collect(Collectors.toList());

    TenantContext.get().setFetchUpdatedTrackingInLabelDataStart(System.currentTimeMillis());
    Lists.partition(updatedLabelTrackingIdsToBePersisted, fetchExistingLpnLimit)
        .forEach(
            updatedLpns -> {
              updatedTrackingIdsLabelDataList.addAll(
                  labelDataService.findByTrackingIdIn(updatedLpns));
            });
    TenantContext.get().setFetchUpdatedTrackingInLabelDataEnd(System.currentTimeMillis());
    Map<String, LabelData> labelDataByLpns =
        updatedTrackingIdsLabelDataList
            .stream()
            .collect(Collectors.toMap(LabelData::getTrackingId, Function.identity()));

    updateLabelDataList =
        transformLabelDataEntity(
            instructionDownloadMessageDTO,
            filteredInstructionDataForAvailableTrackingIds,
            labelDataByLpns);

    if (CollectionUtils.isNotEmpty(updateLabelDataList)) {
      TenantContext.get().setInsertUpdatedLabelDataStart(System.currentTimeMillis());
      Lists.partition(updateLabelDataList, labelDataBatchSize)
          .forEach(labelDataBatch -> labelDataService.saveAllAndFlush(labelDataBatch));
      TenantContext.get().setInsertUpdatedLabelDataEnd(System.currentTimeMillis());
      LOGGER.info("Persisted updated new labels in Label data for handling code change");

      // send label updates to OP
      publishLabelUpdatesToOP(
          filteredInstructionDataForAvailableTrackingIds,
          filteredInstructionDataForNonAvailableTrackingIds,
          instructionDownloadMessageDTO,
          isChildContainersExist);
    }
    return updateLabelDataList;
  }

  /**
   * @param instructionDownloadBlobDataDTO
   * @return
   */
  private List<String> filterPrevTrackingIdForChildContainers(
      InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO) {
    return instructionDownloadBlobDataDTO
        .getChildContainers()
        .stream()
        .map(InstructionDownloadChildContainerDTO::getPrevTrackingId)
        .collect(Collectors.toList());
  }

  /**
   * @param instructionDownloadBlobDataDTO
   * @param availablePrevTrackingIdsLabelDataList
   * @return
   */
  private boolean filterPrevTrackingIdsFromBlobData(
      InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO,
      List<LabelData> availablePrevTrackingIdsLabelDataList,
      boolean isChildContainerExists) {
    if (isChildContainerExists) {
      List<String> childTrackingIds =
          instructionDownloadBlobDataDTO
              .getChildContainers()
              .stream()
              .map(InstructionDownloadChildContainerDTO::getPrevTrackingId)
              .collect(Collectors.toList());
      availablePrevTrackingIdsLabelDataList =
          availablePrevTrackingIdsLabelDataList
              .stream()
              .filter(available -> childTrackingIds.contains(available.getTrackingId()))
              .collect(Collectors.toList());
    } else {
      availablePrevTrackingIdsLabelDataList =
          availablePrevTrackingIdsLabelDataList
              .stream()
              .filter(
                  available ->
                      available
                          .getTrackingId()
                          .equals(
                              instructionDownloadBlobDataDTO.getContainer().getPrevTrackingId()))
              .collect(Collectors.toList());
    }

    return CollectionUtils.isNotEmpty(availablePrevTrackingIdsLabelDataList);
  }

  /**
   * This method will send label update to OP with the success/failed Lpns list. Success LPNs are
   * the labels which are successfully replaced at receiving end. Failed LPNs are the labels which
   * are already received/cancelled in receiving but Orders do not received latest receiving status
   * updates.
   *
   * @param filteredInstructionDataForAvailableTrackingIds
   * @param filteredInstructionDataForNonAvailableTrackingIds
   * @param instructionDownloadMessageDTO
   */
  private void publishLabelUpdatesToOP(
      List<InstructionDownloadBlobDataDTO> filteredInstructionDataForAvailableTrackingIds,
      List<InstructionDownloadBlobDataDTO> filteredInstructionDataForNonAvailableTrackingIds,
      InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      boolean isChildContainersExist) {
    List<LpnsInfo> successLpns = new ArrayList<>();
    List<LpnsInfo> failedLpns = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(filteredInstructionDataForNonAvailableTrackingIds)) {
      if (isChildContainersExist) {
        filteredInstructionDataForNonAvailableTrackingIds.forEach(
            nonAvailableTrackingIdBlobData ->
                nonAvailableTrackingIdBlobData
                    .getChildContainers()
                    .forEach(
                        childContainer ->
                            failedLpns.add(
                                LpnsInfo.builder()
                                    .prevTrackingId(childContainer.getPrevTrackingId())
                                    .trackingId(childContainer.getTrackingId())
                                    .build())));
      } else {
        filteredInstructionDataForNonAvailableTrackingIds.forEach(
            nonAvailableTrackingIdBlobData ->
                failedLpns.add(
                    LpnsInfo.builder()
                        .prevTrackingId(
                            nonAvailableTrackingIdBlobData.getContainer().getPrevTrackingId())
                        .trackingId(nonAvailableTrackingIdBlobData.getContainer().getTrackingId())
                        .build()));
      }
    }

    if (CollectionUtils.isNotEmpty(filteredInstructionDataForAvailableTrackingIds)) {
      if (isChildContainersExist) {
        filteredInstructionDataForAvailableTrackingIds.forEach(
            availableTrackingIdBlobData ->
                availableTrackingIdBlobData
                    .getChildContainers()
                    .forEach(
                        childContainer ->
                            successLpns.add(
                                LpnsInfo.builder()
                                    .prevTrackingId(childContainer.getPrevTrackingId())
                                    .trackingId(childContainer.getTrackingId())
                                    .build())));
      } else {
        filteredInstructionDataForAvailableTrackingIds.forEach(
            availableTrackingIdBlobData ->
                successLpns.add(
                    LpnsInfo.builder()
                        .prevTrackingId(
                            availableTrackingIdBlobData.getContainer().getPrevTrackingId())
                        .trackingId(availableTrackingIdBlobData.getContainer().getTrackingId())
                        .build()));
      }
    }

    LOGGER.info(
        "Triggering Label update to OP for Handling code change on PoNumber:{}, itemNumber:{}",
        instructionDownloadMessageDTO.getPoNumber(),
        instructionDownloadMessageDTO.getItemNumber());
    // send label update to OP
    orderServiceRestApiClient.sendLabelUpdate(
        LpnUpdateRequest.builder()
            .purchaseReferenceNumber(instructionDownloadMessageDTO.getPoNumber())
            .itemNumber(instructionDownloadMessageDTO.getItemNumber())
            .successLpns(successLpns)
            .failedLpns(failedLpns)
            .build(),
        instructionDownloadMessageDTO.getHttpHeaders());
  }

  /**
   * Update Label data status as Cancelled for the given LPNs (trackingIds)
   *
   * @param instructionDownloadMessageDTO
   */
  private void processLabelsCancelledEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    LOGGER.info("Instruction download labels cancelled event started");
    try {
      if (CollectionUtils.isNotEmpty(instructionDownloadMessageDTO.getTrackingIds())) {
        LOGGER.info(
            "Processing cancel label event for lpns:{} and size:{}",
            instructionDownloadMessageDTO.getTrackingIds(),
            instructionDownloadMessageDTO.getTrackingIds().size());
        List<LabelData> labelDataList =
            labelDataService.findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds());
        LOGGER.info(
            "Updating the label status to CANCELLED for lpn's of size:{}", labelDataList.size());
        rdcLabelGenerationService.updateLabelStatusToCancelled(
            labelDataList,
            instructionDownloadMessageDTO.getHttpHeaders(),
            ReceivingConstants.PURCHASE_REF_TYPE_DA);
      } else {
        LOGGER.info("Missing lpn information in the cancel labels event");
      }
    } catch (Exception exception) {
      LOG.error("Error occurred while instruction labels cancelled event", exception);
    }
  }

  /**
   * PO Cancelled Event implementation
   *
   * @param instructionDownloadMessageDTO
   */
  private void processPOCancelledEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    LOGGER.info("Instruction download po cancelled event started");
    try {
      List<LabelData> labelDataList =
          labelDataService.fetchByPurchaseReferenceNumber(
              instructionDownloadMessageDTO.getPoNumber());
      LOGGER.info(
          "Processing PO cancel event for PO: {} and number of labels: {}",
          instructionDownloadMessageDTO.getPoNumber(),
          labelDataList.size());
      rdcLabelGenerationService.updateLabelStatusToCancelled(
          labelDataList,
          instructionDownloadMessageDTO.getHttpHeaders(),
          ReceivingConstants.PURCHASE_REF_TYPE_DA);
    } catch (Exception exception) {
      LOG.error("Error occurred while instruction po cancelled event", exception);
    }
  }

  /**
   * PO Line Cancelled Event implementation
   *
   * @param instructionDownloadMessageDTO
   */
  private void processPOLineCancelledEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    LOGGER.info("Instruction download po line cancelled event started");
    try {
      List<LabelData> labelDataList =
          labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              instructionDownloadMessageDTO.getPoNumber(),
              instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());
      LOGGER.info(
          "Processing PO line cancelled event for PO: {}, POL: {} and number of labels: {}",
          instructionDownloadMessageDTO.getPoNumber(),
          instructionDownloadMessageDTO.getPurchaseReferenceLineNumber(),
          labelDataList.size());
      rdcLabelGenerationService.updateLabelStatusToCancelled(
          labelDataList,
          instructionDownloadMessageDTO.getHttpHeaders(),
          ReceivingConstants.PURCHASE_REF_TYPE_DA);
    } catch (Exception exception) {
      LOG.error("Error occurred while instruction po line cancelled event", exception);
    }
  }

  /**
   * Fetch bob name
   *
   * @param url
   * @return
   */
  private String getBlobName(String url) {
    int index1 = url.lastIndexOf("/");
    String blobName = url.substring(index1 + 1);
    return blobName;
  }

  /**
   * Fetch container name
   *
   * @param url
   * @return
   */
  private String getContainerName(String url) {
    int index1 = url.lastIndexOf("/");
    int index2 = url.substring(0, index1).lastIndexOf("/");
    String containerName = url.substring(index2 + 1, index1);
    return containerName;
  }

  /**
   * Blob JSON to DTO conversion
   *
   * @param blobData
   * @return
   */
  private List<InstructionDownloadBlobDataDTO> transformInstructionBlobData(byte blobData[]) {
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        Collections.emptyList();
    try {
      String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
      instructionDownloadBlobDataDTOList =
          gson.fromJson(
              blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());
    } catch (Exception ex) {
      LOGGER.error("Error occurred while converting blob json data to dto", ex);
    }
    return instructionDownloadBlobDataDTOList;
  }

  /**
   * DTO to Entity Conversion
   *
   * @param instructionDownloadMessageDTO
   * @param instructionDownloadBlobDataDTOList
   * @param labelDataByLpns
   * @return
   */
  private List<LabelData> transformLabelDataEntity(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList,
      Map<String, LabelData> labelDataByLpns) {
    List<String> eventTypeList =
        instructionDownloadMessageDTO.getHttpHeaders().get(ReceivingConstants.EVENT_TYPE);
    String eventTypeVal =
        org.apache.commons.collections4.CollectionUtils.isNotEmpty(eventTypeList)
            ? eventTypeList.get(0)
            : ReceivingConstants.EMPTY_STRING;
    EventType eventType = EventType.valueOfEventType(eventTypeVal);

    return instructionDownloadBlobDataDTOList
        .stream()
        .map(
            instruction -> {
              InstructionDownloadContainerDTO instructionDownloadContainerDTO =
                  instruction.getContainer();
              boolean isLabelExist =
                  labelDataByLpns.containsKey(instructionDownloadContainerDTO.getTrackingId());
              if (isLabelExist && !(OFFLINE_RECEIVING.equals(eventType))) {
                LOG.error(
                    "Tracking id {} already exist",
                    instructionDownloadContainerDTO.getTrackingId());
                return null;
              }
              LabelData labelData =
                  labelDataByLpns.getOrDefault(
                      instructionDownloadContainerDTO.getTrackingId(), new LabelData());
              String facilityNum =
                  instructionDownloadMessageDTO
                      .getHttpHeaders()
                      .get(ReceivingConstants.TENENT_FACLITYNUM)
                      .get(0);
              labelData.setFacilityNum(Integer.valueOf(facilityNum));
              String facilityCountryCode =
                  instructionDownloadMessageDTO
                      .getHttpHeaders()
                      .get(ReceivingConstants.TENENT_COUNTRY_CODE)
                      .get(0);
              labelData.setFacilityCountryCode(facilityCountryCode);
              labelData.setDeliveryNumber(instruction.getDeliveryNbr());
              labelData.setPurchaseReferenceNumber(instruction.getPoNbr());
              labelData.setPurchaseReferenceLineNumber(instruction.getPoLineNbr());
              labelData.setTrackingId(instructionDownloadContainerDTO.getTrackingId());
              labelData.setLabelSequenceNbr(instruction.getSequence());
              labelData.setStatus(LabelInstructionStatus.AVAILABLE.name());
              labelData.setOrderQuantity(instruction.getProjectedQty());
              labelData.setCreateTs(new Date());
              if (Objects.nonNull(instructionDownloadContainerDTO.getSscc())) {
                labelData.setSscc(instructionDownloadContainerDTO.getSscc());
              }
              // DSDC Asn Number
              if (Objects.nonNull(instruction.getAsnNumber())) {
                labelData.setAsnNumber(instruction.getAsnNumber());
              }

              LabelDataAllocationDTO labelDataAllocationDTO = new LabelDataAllocationDTO();
              labelDataAllocationDTO.setContainer(instructionDownloadContainerDTO);
              labelDataAllocationDTO.setChildContainers(instruction.getChildContainers());
              labelData.setAllocation(labelDataAllocationDTO);
              labelData.setDestStrNbr(
                  new Integer(
                      labelDataAllocationDTO.getContainer().getFinalDestination().getBuNumber()));

              /*in case of case pack labels the item details can be fetched from distributions, for
              break pack labels we can refer child container for the item details */
              if (CollectionUtils.isNotEmpty(instructionDownloadContainerDTO.getDistributions())) {
                InstructionDownloadDistributionsDTO instructionDownloadDistributionsDTO =
                    instructionDownloadContainerDTO.getDistributions().get(0);
                InstructionDownloadItemDTO instructionDownloadItemDTO =
                    instructionDownloadDistributionsDTO.getItem();
                labelData.setItemNumber(instructionDownloadItemDTO.getItemNbr());
                labelData.setVnpk(instructionDownloadItemDTO.getVnpk());
                labelData.setWhpk(instructionDownloadItemDTO.getWhpk());
                labelData.setQuantityUOM(instructionDownloadDistributionsDTO.getQtyUom());
                labelData.setQuantity(instructionDownloadDistributionsDTO.getAllocQty());
              } else if (CollectionUtils.isNotEmpty(instruction.getChildContainers())) {
                List<InstructionDownloadChildContainerDTO> childContainerDTOS =
                    instruction.getChildContainers();
                /* do not need item details for DSDC label instructions. Only DSDC instructions
                can have SSCC in the instructions payload */
                if (CollectionUtils.isNotEmpty(childContainerDTOS.get(0).getDistributions())
                    && StringUtils.isBlank(labelData.getSscc())) {
                  InstructionDownloadItemDTO childContainerItem =
                      childContainerDTOS.get(0).getDistributions().get(0).getItem();
                  labelData.setItemNumber(childContainerItem.getItemNbr());
                  labelData.setVnpk(childContainerItem.getVnpk());
                  labelData.setWhpk(childContainerItem.getWhpk());
                  labelData.setQuantityUOM(instruction.getProjectedQtyUom());
                  labelData.setQuantity(instruction.getProjectedQty());
                }
              }
              /** added for offline flow (Possible values : XDK1, XDK2) - for channel method */
              if (Objects.nonNull(instruction.getAsnNumber())) {
                labelData.setChannelMethod(instruction.getContainer().getChannelMethod());
              }

              /**
               * For offline receiving, if it is Case pack - take label type from container level if
               * it is break pack + WPM - take label type from container level if it is break pack
               * from CC/Imports - take label type from child container level
               */
              if (OFFLINE_RECEIVING.equals(eventType)) {
                labelData.setSourceFacilityNumber(instruction.getSourceFacilityNumber());
                if (CollectionUtils.isEmpty(instruction.getChildContainers())) {
                  labelData.setLabel(instructionDownloadContainerDTO.getLabelType());
                } else if ((rdcManagedConfig
                            .getWpmSites()
                            .contains(instruction.getSourceFacilityNumber())
                        || rdcManagedConfig
                            .getRdc2rdcSites()
                            .contains(instruction.getSourceFacilityNumber()))
                    && CollectionUtils.isNotEmpty(instruction.getChildContainers())) {
                  labelData.setLabel(instructionDownloadContainerDTO.getLabelType());
                } else if (CollectionUtils.isNotEmpty(instruction.getChildContainers())) {
                  labelData.setLabel(instruction.getChildContainers().get(0).getLabelType());
                }
              }
              return labelData;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /** Time taken logging */
  private void labelsGeneratedEventTimeTakenLog() {
    long timeTakenForInstructionDownloadByPoAndItemNumber =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getInstructionDownloadStart(),
            TenantContext.get().getInstructionDownloadEnd());
    long timeTakenForInstructionBlobDownload =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getInstructionBlobDownloadStart(),
            TenantContext.get().getInstructionBlobDownloadEnd());
    long timeTakenForLabelDataSelection =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchExistingLpnInLabelDataStart(),
            TenantContext.get().getFetchExistingLpnInLabelDataEnd());
    long timeTakenForLabelDataInsertion =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getInsertLabelDataStart(),
            TenantContext.get().getInsertLabelDataEnd());
    long timeTakenForLabelDataSelectionOnPreviousTrackingIds =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchPreviousTrackingInLabelDataStart(),
            TenantContext.get().getFetchPreviousTrackingInLabelDataEnd());
    long timeTakenForLabelDataSelectionOnUpdatedTrackingIds =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchUpdatedTrackingInLabelDataStart(),
            TenantContext.get().getFetchUpdatedTrackingInLabelDataStart());
    long timeTakenForUpdatedLabelDataInsertion =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getInsertUpdatedLabelDataStart(),
            TenantContext.get().getInsertUpdatedLabelDataStart());
    long timeTakenForCancelledStatusUpdateInLabelData =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getUpdateTrackingIdStatusCancelledStart(),
            TenantContext.get().getUpdateTrackingIdStatusCancelledStart());
    LOG.info(
        "LatencyCheck: Total time taken for instruction download event; timeTakenForInstructionDownloadByPoAndItemNumber = {} ,timeTakenForInstructionBlobDownload = {} and timeTakenForLabelDataSelection = {} and timeTakenForLabelDataInsertion = {},"
            + " timeTakenForLabelDataSelectionOnPreviousTrackingIds = {}, timeTakenForLabelDataSelectionOnUpdatedTrackingIds = {},"
            + "timeTakenForUpdatedLabelDataInsertion = {}, timeTakenForCancelledStatusUpdateInLabelData = {} and correlationId={}",
        timeTakenForInstructionDownloadByPoAndItemNumber,
        timeTakenForInstructionBlobDownload,
        timeTakenForLabelDataSelection,
        timeTakenForLabelDataInsertion,
        timeTakenForLabelDataSelectionOnPreviousTrackingIds,
        timeTakenForLabelDataSelectionOnUpdatedTrackingIds,
        timeTakenForUpdatedLabelDataInsertion,
        timeTakenForCancelledStatusUpdateInLabelData,
        TenantContext.getCorrelationId());
  }
}
