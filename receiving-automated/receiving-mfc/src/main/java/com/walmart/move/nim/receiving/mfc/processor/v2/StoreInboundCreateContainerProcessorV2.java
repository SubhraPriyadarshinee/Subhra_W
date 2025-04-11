package com.walmart.move.nim.receiving.mfc.processor.v2;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;
import static com.walmart.move.nim.receiving.mfc.common.PalletType.MFC;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.RESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.UNRESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemType.SHORTAGE;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.isStorePalletPublishingDisabled;
import static java.lang.Boolean.TRUE;

import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.common.PalletScanArchiveUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.decant.PalletScanArchiveMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.mfc.common.*;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.exception.ExceptionMessage;
import com.walmart.move.nim.receiving.mfc.model.common.ContainerEventSubType;
import com.walmart.move.nim.receiving.mfc.model.common.ContainerEventType;
import com.walmart.move.nim.receiving.mfc.model.csm.ConteinerEvent;
import com.walmart.move.nim.receiving.mfc.model.problem.CreateExceptionResponse;
import com.walmart.move.nim.receiving.mfc.processor.ProblemHandingProcessor;
import com.walmart.move.nim.receiving.mfc.processor.StoreInboundCreateContainerProcessor;
import com.walmart.move.nim.receiving.mfc.service.ContainerEventService;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.mfc.transformer.ContainerDTOEventTransformer;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class StoreInboundCreateContainerProcessorV2 extends StoreInboundCreateContainerProcessor
    implements ProblemHandingProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreInboundCreateContainerProcessorV2.class);

  protected static final List<DeliveryStatus> DELIVERY_RECEIVABLE_STATUSES =
      Arrays.asList(
          DeliveryStatus.OPEN,
          DeliveryStatus.OPN,
          DeliveryStatus.WORKING,
          DeliveryStatus.WRK,
          DeliveryStatus.ARV,
          DeliveryStatus.SCH);

  // This will get used only to check if delivery is not completed and associate is receiving mfc
  // ony pallet
  protected static final List<DeliveryStatus> DELIVERY_RECEIVABLE_STATUSES_POST_DELIVERY_COMPLETE =
      Arrays.asList(
          DeliveryStatus.OPEN,
          DeliveryStatus.OPN,
          DeliveryStatus.WORKING,
          DeliveryStatus.WRK,
          DeliveryStatus.ARV,
          DeliveryStatus.SCH,
          DeliveryStatus.UNLOADING_COMPLETE);

  @Resource(name = MFCConstant.MFC_PROBLEM_SERVICE)
  private MFCProblemService problemService;

  @Autowired private MFCContainerService mfcContainerService;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Autowired private ContainerEventService containerEventService;

  @Autowired private ContainerDTOEventTransformer containerDTOEventTransformer;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private ContainerItemRepository containerItemRepository;

  @Autowired private ContainerTransformer containerTransformer;

  @Autowired private DecantService decantService;

  @Resource(name = MFCConstant.MFC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Value("${mfc.pallet.allowed.post.delivery.complete:false}")
  private Boolean isMFCPalletAllowedPostDeliveryComplete;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  protected Container getContainerAlreadyReceived(ContainerScanRequest containerScanRequest) {
    List<Container> containers =
        mfcContainerService.findContainerBySSCC(containerScanRequest.getTrackingId());
    if (!containers.isEmpty()) {
      Set<Container> filteredContainers =
          containers
              .stream()
              .filter(
                  container -> {
                    long originalDeliveryNumber =
                        Long.parseLong(
                            container
                                .getContainerMiscInfo()
                                .getOrDefault(
                                    ORIGINAL_DELIVERY_NUMBER, container.getDeliveryNumber())
                                .toString());
                    // Pallet already received against same delivery
                    if (container.getDeliveryNumber().longValue()
                        == containerScanRequest.getDeliveryNumber().longValue()) {
                      return TRUE;
                    }
                    // Pallet part of this delivery but already received against other delivery as
                    // overage
                    if (originalDeliveryNumber
                        == containerScanRequest.getDeliveryNumber().longValue()) {
                      return TRUE;
                    }

                    if (Objects.nonNull(containerScanRequest.getOriginalDeliveryNumber())) {
                      // Pallet already received against original delivery
                      if (container.getDeliveryNumber().longValue()
                          == containerScanRequest.getOriginalDeliveryNumber().longValue()) {
                        return TRUE;
                      }
                      // Pallet already received against other delivery as overage
                      if (originalDeliveryNumber
                          == containerScanRequest.getOriginalDeliveryNumber().longValue()) {
                        return TRUE;
                      }
                    }
                    return Boolean.FALSE;
                  })
              .collect(Collectors.toSet());

      if (!filteredContainers.isEmpty()) {
        if (isFireflyEvent() || hasContainerReceivedByFirefly(filteredContainers)) {
          return filteredContainers.stream().findAny().orElse(null);
        }
        throw new ReceivingConflictException(
            ExceptionCodes.CONTAINER_ALREADY_EXISTS,
            String.format(
                "Container with pallet number %s and delivery %d already exists",
                containerScanRequest.getTrackingId(), containerScanRequest.getDeliveryNumber()));
      }
    }
    return null;
  }

  private boolean hasContainerReceivedByFirefly(Set<Container> containers) {
    return containers
        .stream()
        .anyMatch(
            container ->
                StringUtils.equals(
                    ReceivingConstants.USER_ID_AUTO_FINALIZED, container.getCreateUser()));
  }

  private boolean isFireflyEvent() {
    return StringUtils.equals(
        ReceivingConstants.USER_ID_AUTO_FINALIZED, ReceivingUtils.retrieveUserId());
  }

  @Override
  public String createContainer(ContainerScanRequest containerScanRequest) {
    ASNDocument asnDocument = getASNDocument(containerScanRequest);

    containerScanRequest.setAsnDocument(asnDocument);
    return processContainer(containerScanRequest, asnDocument);
  }

  public String processContainer(
      ContainerScanRequest containerScanRequest, ASNDocument asnDocument) {
    // fixes for delivery complete flow
    Container receivedContainer = getContainerAlreadyReceived(containerScanRequest);
    // following will make sure that already received container
    // details are retrieved from DB and sent back to UI
    if (receivedContainer != null) {
      // for data analytics, only capture when the pallet was already received and scanned by user
      // again
      if (!isFireflyEvent()) {
        PalletScanArchiveMessage palletScanArchiveMessage =
            PalletScanArchiveUtils.createPalletScanArchiveMessage(
                containerScanRequest, null, "PALLET_DUPLICATE_SCAN");
        decantService.initiateMessagePublish(
            PalletScanArchiveUtils.createDecantMessagePublishRequests(
                palletScanArchiveMessage, getGson()));
      }
      List<ContainerItem> containerItems =
          containerItemRepository.findByTrackingId(receivedContainer.getTrackingId());
      receivedContainer.setContainerItems(containerItems);

      ContainerDTO containerDTO = containerTransformer.transform(receivedContainer);
      ContainerUtils.replaceContainerWithSSCC(containerDTO);
      containerDTO.setEventType("PALLET_RECEIVED");

      return getGson().toJson(containerDTO);
    }
    isMFCPalletAllowed(containerScanRequest);

    if (Objects.nonNull(asnDocument)
        && Objects.nonNull(asnDocument.getShipments())
        && asnDocument.getShipments().size() > 1) {
      LOGGER.info(
          "Got multiple ASN Document for payload= {} and hence, sending back for user selection.",
          containerScanRequest);
      return getGson().toJson(asnDocument);
    }
    if (Objects.isNull(asnDocument.getShipment()) && !asnDocument.getShipments().isEmpty()) {
      asnDocument.setShipment(asnDocument.getShipments().get(0));
    }

    boolean isTrueOverage = isTrueOverage(containerScanRequest);
    boolean isOutOfDeliveryContext = isOutOfDeliveryContext(containerScanRequest);

    // setting overage parameter
    if (Objects.isNull(containerScanRequest.getOverageType())
        && (isTrueOverage || isOutOfDeliveryContext)) {
      LOGGER.info(
          "Fallback in case UI doesn't provide overage info delivery {} and pallet {}",
          containerScanRequest.getDeliveryNumber(),
          containerScanRequest.getTrackingId());
      containerScanRequest.setOverageType(OverageType.UNIDENTIFIED);
    }

    Container container = null;
    ContainerDTO containerDTO = null;
    if (Objects.nonNull(containerScanRequest.getOverageType())) {
      asnDocument.setOverage(TRUE);
      ProblemLabel problemLabel =
          !OverageType.isUnBilledPalletOverageType(containerScanRequest.getOverageType())
              ? problemService.getProblemLabel(
                  SHORTAGE + DELIM_DASH + UNRESOLVED,
                  containerScanRequest.getTrackingId(),
                  containerScanRequest.getOriginalDeliveryNumber())
              : null;
      if (Objects.nonNull(problemLabel)) {
        LOGGER.info(
            "Overage receiving delivery {} and pallet {}",
            containerScanRequest.getDeliveryNumber(),
            containerScanRequest.getTrackingId());
        container = mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
        populateOverageType(container, isOutOfDeliveryContext);
        container.setContainerStatus(PROBLEM_RECEIVED);
        containerDTO = containerCreation(containerScanRequest, container, asnDocument);
        Date thresholdTsInUTC = getThresholdTsInUTC(problemLabel.getCreateTs());
        updateCSM(
            containerDTO,
            resolveProblemTypeByThreshold(container.getCreateTs(), thresholdTsInUTC).name(),
            resolveProblemSubTypeByThreshold(container.getCreateTs(), thresholdTsInUTC).name());

        createReceivingEvent(containerDTO);
        updateCorrectionEventAsInvalidIfApplicable(containerDTO);
        this.handleProblemUpdation(problemLabel, SHORTAGE, RESOLVED);
        return getGson().toJson(containerDTO);
      } else if (isTrueOverage) {
        LOGGER.info(
            "True overage receiving delivery {} and pallet {}",
            containerScanRequest.getDeliveryNumber(),
            containerScanRequest.getTrackingId());
        container = mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);

        // ProblemType  is needed to create overage ticket
        String problemType =
            DELIVERY_RECEIVABLE_STATUSES.contains(
                    DeliveryStatus.valueOf(
                        asnDocument.getDelivery().getStatusInformation().getStatus()))
                ? PROBLEM_OV
                : ReceivingConstants.EMPTY_STRING;
        // ASN will be added to current delivery and hence, status is received.
        container.setContainerStatus(RECEIVED);

        containerDTO = containerCreation(containerScanRequest, container, asnDocument);

        if (StringUtils.equalsIgnoreCase(PROBLEM_OV, problemType)) {
          handleProblemCreation(asnDocument.getShipment(), containerDTO, ProblemType.OVERAGE);
        } else {
          LOGGER.info("Shortage ticket is missing and hence, receiving normally");
          // TODO : raise alert
        }
      }
    }

    if (Objects.isNull(containerDTO)) {
      LOGGER.info(
          "Normal receiving delivery {} and pallet {}",
          containerScanRequest.getDeliveryNumber(),
          containerScanRequest.getTrackingId());
      container = mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
      container.setContainerStatus(ReceivingConstants.RECEIVED);
      // Backward compatibility
      populateOverageType(container, isOutOfDeliveryContext);
      containerDTO = containerCreation(containerScanRequest, container, asnDocument);
    }

    if (isStorePalletPublishingDisabled(containerDTO, tenantSpecificConfigReader)) {
      return getGson().toJson(containerDTO);
    }

    // Publish container to CSM
    getMfcContainerService()
        .getContainerService()
        .publishMultipleContainersToInventory(Arrays.asList(containerDTO));

    return getGson().toJson(containerDTO);
  }

  protected void isMFCPalletAllowed(ContainerScanRequest containerScanRequest) {
    if (isMFCPalletAllowedPostDeliveryComplete) {
      LOGGER.warn("MFC Pallet Receiving allowed post delivery complete. Hence, allowing");
      return;
    }

    List<Pack> packs =
        containerScanRequest
            .getAsnDocument()
            .getPacks()
            .stream()
            .filter(pack -> Objects.nonNull(pack.getPalletNumber()))
            .filter(
                pack ->
                    StringUtils.equalsIgnoreCase(
                        containerScanRequest.getTrackingId(), pack.getPalletNumber()))
            .collect(Collectors.toList());

    String palletType = MFCUtils.getPalletType(packs);
    if (!StringUtils.equalsIgnoreCase(MFC.toString(), palletType)) {
      LOGGER.info("Scanned Pallet Type is not MFC Type . So, ignoring the checks");
      return;
    }

    String deliveryNumber =
        Objects.isNull(containerScanRequest.getDeliveryNumber())
            ? String.valueOf(containerScanRequest.getOriginalDeliveryNumber())
            : String.valueOf(containerScanRequest.getDeliveryNumber());

    Optional<DeliveryMetaData> _deliveryMetaData =
        deliveryMetaDataService.findByDeliveryNumber(deliveryNumber);

    if (!_deliveryMetaData.isPresent()) {
      LOGGER.info(
          "No Delivery Metadata found for deliveryNumber={} . So, ignoring the checks",
          deliveryNumber);
      return;
    }

    DeliveryMetaData deliveryMetaData = _deliveryMetaData.get();

    if (!DELIVERY_RECEIVABLE_STATUSES_POST_DELIVERY_COMPLETE.contains(
        deliveryMetaData.getDeliveryStatus())) {
      LOGGER.error(
          "Pallet={} for delivery={} is not on receivable status .",
          containerScanRequest.getTrackingId(),
          deliveryNumber);
      throw new ReceivingConflictException(
          ExceptionCodes.INVALID_PALLET,
          "Pallet is not allowed for receiving as delivery is already completed");
    }
  }

  protected ContainerEventType resolveProblemTypeByThreshold(
      Date createTsInUTC, Date thresholdTsInUTC) {
    if (createTsInUTC.after(thresholdTsInUTC)) {
      return ContainerEventType.OVERAGE;
    }
    return ContainerEventType.RECEIVING;
  }

  protected ContainerEventSubType resolveProblemSubTypeByThreshold(
      Date createTsInUTC, Date thresholdTsInUTC) {
    if (createTsInUTC.after(thresholdTsInUTC)) {
      return ContainerEventSubType.FOUND_AFTER_THRESHOLD;
    }
    return ContainerEventSubType.FOUND_WITHIN_THRESHOLD;
  }

  protected Date getThresholdTsInUTC(Date createTs) {
    DateFormat dateFormat = new SimpleDateFormat(UTC_DATE_FORMAT);
    dateFormat.setTimeZone(TimeZone.getTimeZone(UTC_TIME_ZONE));
    Date createTsInUTC;
    try {
      createTsInUTC = dateFormat.parse(dateFormat.format(createTs));
    } catch (Exception exception) {
      LOGGER.error("Invalid problem create timestamp", exception);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PROBLEM_CREATE_TS,
          String.format(ExceptionMessage.INVALID_PROBLEM_CREATE_TS_MSG, createTs));
    }
    return CoreUtil.addMinutesToJavaUtilDate(
        createTsInUTC, mfcManagedConfig.getPalletFoundAfterUnloadThresholdTimeMinutes());
  }

  protected void populateOverageType(Container container, boolean receiveWithOutDeliveryContext) {
    // OVERAGE_FROM_SAME_DELIVERY to be used in ASN add flow
    if (receiveWithOutDeliveryContext) {
      container
          .getContainerMiscInfo()
          .put(OPERATION_TYPE, OperationType.OVERAGE_FROM_SAME_DELIVERY);
      LOGGER.info(
          "Added overage receiving with out delivery context for deliveryNumber={}",
          container.getDeliveryNumber());
      return;
    }
    LOGGER.info("No Overage is detected for deliveryNumber={}", container.getDeliveryNumber());
  }

  protected boolean isTrueOverage(ContainerScanRequest containerScanRequest) {
    // For Overage delivery, originalDelivery <> currentDelivery
    // delivery status Should be sch/open/wrk and should not have unloaded
    if (Objects.nonNull(containerScanRequest.getOriginalDeliveryNumber())
        && !Objects.equals(
            containerScanRequest.getDeliveryNumber(),
            containerScanRequest.getOriginalDeliveryNumber())) {
      return true;
    }

    // Pallet is not part of existing deliveries / not billed at source.
    if (OverageType.isUnBilledPalletOverageType(containerScanRequest.getOverageType())) {
      return true;
    }
    return false;
  }

  protected boolean isOutOfDeliveryContext(ContainerScanRequest containerScanRequest) {
    // Without context , originalDelivery == currentDelivery
    // delivery should not be in sch/open/wrk and can be in unloaded or FNL
    if (!Objects.equals(
        containerScanRequest.getDeliveryNumber(),
        containerScanRequest.getOriginalDeliveryNumber())) {
      return false;
    }
    Optional<DeliveryMetaData> _deliveryMetadata =
        mfcDeliveryMetadataService.findByDeliveryNumber(
            String.valueOf(containerScanRequest.getDeliveryNumber()));
    if (_deliveryMetadata.isPresent()) {
      DeliveryMetaData deliveryMetaData = _deliveryMetadata.get();
      return !DELIVERY_RECEIVABLE_STATUSES.contains(deliveryMetaData.getDeliveryStatus());
    } else if (Objects.equals(
        containerScanRequest.getDeliveryNumber(),
        containerScanRequest.getOriginalDeliveryNumber())) {
      LOGGER.info(
          "Out of delivery context fallback if delivery metadata is not present for delivery {}",
          containerScanRequest.getDeliveryNumber());
      return true;
    }
    return false;
  }

  protected void updateCSM(ContainerDTO containerDTO, String eventType, String eventSubType) {
    if (isStorePalletPublishingDisabled(containerDTO, tenantSpecificConfigReader)) {
      return;
    }
    ConteinerEvent containerUpdate = containerDTOEventTransformer.transform(containerDTO);
    containerEventService.publishContainerUpdate(
        Optional.of(containerUpdate), containerDTO.getDeliveryNumber(), eventType, eventSubType);
    LOGGER.info("Update the container to CSM: {}", containerDTO.getTrackingId());
  }

  protected void createReceivingEvent(ContainerDTO containerDTO) {
    if (STORE.equals(containerDTO.getContainerMiscInfo().get(PALLET_TYPE))) {
      ReceivingEvent receivingEvent =
          ReceivingEvent.builder()
              .payload(getGson().toJson(containerDTO))
              .processor(STORE_CONTAINER_OVERAGE_EVENT_PROCESSOR)
              .build();
      processInitiator.initiateProcess(receivingEvent, null);
    }
  }

  protected void updateCorrectionEventAsInvalidIfApplicable(ContainerDTO containerDTO) {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(MFCConstant.ACTION_TYPE, UPDATE_CORRECTION_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(containerDTO))
            .name(MFCConstant.UPDATE_CORRECTION_EVENT)
            .processor(CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    processInitiator.initiateProcess(receivingEvent, null);
  }

  @Override
  public void handleProblemCreation(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {
    Map<String, Object> containerMisc = containerDTO.getContainerMiscInfo();
    String containerOperationType =
        containerMisc.getOrDefault(OPERATION_TYPE, MFCConstant.EMPTY_STRING).toString();

    if (!StringUtils.equalsIgnoreCase(containerOperationType, MFCConstant.OVERAGE)) {
      LOGGER.warn(
          "Pallet is not meant for overage flow execution . Hence, ignoring overage processing. palletId={}",
          containerDTO.getSsccNumber());
      return;
    }

    CreateExceptionResponse response = null;
    if (mfcManagedConfig.isProblemRegistrationEnabled()) {
      ProblemRegistrationService problemRegistrationService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              PROBLEM_REGISTRATION_SERVICE,
              FIXIT_PROBLEM_SERVICE,
              ProblemRegistrationService.class);
      try {
        response = problemRegistrationService.createProblem(shipment, containerDTO, problemType);
      } catch (Exception e) {
        LOGGER.error(
            "Problem ticket creation failed for facility:{}, container: {}",
            shipment.getSource().getNumber(),
            containerDTO.getTrackingId(),
            e);
      }
    }
    problemService.createProblemByContainer(containerDTO, problemType, response);
  }

  @Override
  public void handleProblemUpdation(
      ProblemLabel problemLabel,
      ProblemType problemType,
      ProblemResolutionType problemResolutionType) {
    ProblemRegistrationService problemRegistrationService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            PROBLEM_REGISTRATION_SERVICE,
            FIXIT_PROBLEM_SERVICE,
            ProblemRegistrationService.class);
    if (mfcManagedConfig.isProblemRegistrationEnabled()) {
      try {
        problemRegistrationService.closeProblem(problemLabel, problemType);
      } catch (Exception e) {
        LOGGER.error(
            "Problem ticket update failed for facility:{}, container: {}",
            problemLabel.getFacilityNum(),
            problemLabel.getProblemTagId(),
            e);
      }
    }
    problemService.updateProblem(problemLabel, problemType, problemResolutionType);
  }
}
