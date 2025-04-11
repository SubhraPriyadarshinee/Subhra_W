package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.RESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.UNRESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemType.OVERAGE;
import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.isStorePalletPublishingDisabled;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DELIM_DASH;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_NA;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.model.problem.CreateExceptionResponse;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCOSDRService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

public class StoreProblemHandingProcessor implements ProcessExecutor, ProblemHandingProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreInboundCreateContainerProcessor.class);

  @Autowired private MFCOSDRService mfcosdrService;

  @Autowired private MFCProblemService problemService;

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private ContainerTransformer containerTransformer;
  @Autowired private ProcessInitiator processInitiator;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Override
  public boolean isAsync() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        MFCConstant.SHORTAGE_PROCESSING_IN_ASYNC_MODE,
        Boolean.FALSE);
  }

  @Override
  public void handleProblemCreation(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {
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
    if (mfcManagedConfig.isProblemRegistrationEnabled()) {
      ProblemRegistrationService problemRegistrationService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              PROBLEM_REGISTRATION_SERVICE,
              FIXIT_PROBLEM_SERVICE,
              ProblemRegistrationService.class);
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

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    LOGGER.info("StoreProblemHandlingProcessor : Entry ... parameter = {} ", receivingEvent);

    // Hack as below method support list ASN document
    List<ASNDocument> asnDocuments =
        Arrays.asList(
            JacksonParser.convertJsonToObject(receivingEvent.getPayload(), ASNDocument.class));

    Long deliveryNumber =
        asnDocuments
            .stream()
            .findAny()
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.INVALID_DATA, "No ASN Passed from callee"))
            .getDelivery()
            .getDeliveryNumber();

    List<Container> containers = mfcContainerService.findContainerByDeliveryNumber(deliveryNumber);

    if (CollectionUtils.isEmpty(containers)) {
      containers = new ArrayList<>();
    }

    Set<String> receivedContainerIds =
        containers.stream().map(Container::getSsccNumber).collect(Collectors.toSet());

    // Process one by one as none-of the system support for bulk shortage processing

    for (ASNDocument asnDocument : asnDocuments) {
      if (Objects.isNull(asnDocument.getShipment()) && !asnDocument.getShipments().isEmpty()) {
        asnDocument.setShipment(asnDocument.getShipments().get(0));
      }
      if (!mfcManagedConfig
          .getEligibleSourceTypeForShortageContainerCreation()
          .contains(asnDocument.getShipment().getSource().getType())) {
        LOGGER.info(
            "Shortage Container Creation not allowed for delivery {} with source type {}",
            asnDocument.getDelivery().getDeliveryNumber(),
            asnDocument.getShipment().getSource().getType());
        return;
      }
      Map<String, PalletInfo> palletInfoMap = MFCUtils.getPalletInfoMap(asnDocument.getPacks());
      List<ContainerDTO> shortageContainers =
          createShortageContainers(asnDocument, receivedContainerIds, palletInfoMap);

      // TODO: Need to update to use bulk Problem integration

      for (ContainerDTO containerDTO : shortageContainers) {
        ProblemLabel problemLabel =
            problemService.getProblemLabelByProblemTagIdAndDeliveryNumber(
                containerDTO.getTrackingId(), deliveryNumber);

        if (Objects.nonNull(problemLabel)) {
          // send for update
          updateOverageProblem(problemLabel);
          continue;
        }

        if (!isStorePalletPublishingDisabled(containerDTO, tenantSpecificConfigReader)) {
          // Shortage container will not be store in receiving only update to ei via csm
          //  correctionEventObjectCreation( containerDTO);
          createCorrectionEvent(containerDTO);
          //  processInitiator.initiateProcess(receivingEvent);

          // create shortage in inventory
          // shortage --> container publish --> PROBLEM_NA
          mfcContainerService
              .getContainerService()
              .publishMultipleContainersToInventory(Arrays.asList(containerDTO));
        }
        this.handleProblemCreation(asnDocument.getShipment(), containerDTO, ProblemType.SHORTAGE);
      }
    }
  }

  private void updateOverageProblem(ProblemLabel problemLabel) {
    String overageProblemLabel = OVERAGE + DELIM_DASH + UNRESOLVED;
    if (StringUtils.equalsIgnoreCase(problemLabel.getProblemStatus(), overageProblemLabel)) {
      this.handleProblemUpdation(problemLabel, OVERAGE, RESOLVED);
    }
  }

  private void createCorrectionEvent(ContainerDTO containerDTO) {

    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(
        MFCConstant.EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES,
        new Integer(mfcManagedConfig.getPalletFoundAfterUnloadThresholdTimeMinutes()));
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_CORRECTION_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(containerDTO))
            .processor(CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    processInitiator.initiateProcess(receivingEvent, null);
  }

  private List<ContainerDTO> createShortageContainers(
      ASNDocument asnDocument,
      Set<String> receivedContainerIds,
      Map<String, PalletInfo> palletInfoMap) {
    Map<String, Container> ssccContainerMap = new HashMap<>();
    for (Pack pack : asnDocument.getPacks()) {

      if (Objects.isNull(pack.getPalletNumber())) {
        LOGGER.info("Got a case and hence , ignoring . packNumber = {}", pack.getPackNumber());
        continue;
      }
      if (receivedContainerIds.contains(pack.getPalletNumber())) {
        LOGGER.info(
            "Pallet={} is already received and is not eligible for shortage flow",
            pack.getPalletNumber());
        continue;
      }

      Container container = ssccContainerMap.get(pack.getPalletNumber());
      List<ContainerItem> containerItems = getContainerItems(asnDocument, pack);
      if (Objects.nonNull(container)) {
        container.getContainerItems().addAll(containerItems);
      } else {
        container = getTransientContainer(asnDocument, pack, palletInfoMap);
        container.setContainerItems(containerItems);
        ssccContainerMap.put(pack.getPalletNumber(), container);
      }
    }

    List<Container> listContainer = ssccContainerMap.values().stream().collect(Collectors.toList());
    return containerTransformer.transformList(listContainer);
  }

  private Container getTransientContainer(
      ASNDocument asnDocument, Pack pack, Map<String, PalletInfo> palletInfoMap) {
    Container container = mfcContainerService.createContainer(asnDocument, pack, palletInfoMap);
    container.setTrackingId(container.getSsccNumber());
    container.setContainerStatus(PROBLEM_NA);
    container.setInventoryStatus(PROBLEM_NA);
    mfcContainerService.populateContainerMiscInfo(asnDocument, container, palletInfoMap);
    return container;
  }

  private List<ContainerItem> getContainerItems(ASNDocument asnDocument, Pack pack) {
    List<ContainerItem> containerItems = new ArrayList<>();
    Map<Long, ItemDetails> itemMap = CoreUtil.getItemMap(asnDocument);
    for (Item packItem : pack.getItems()) {
      ContainerItem containerItem =
          mfcContainerService.createPackItem(
              pack, packItem, itemMap.get(packItem.getItemNumber()), null);
      containerItems.add(containerItem);
    }

    return containerItems;
  }
}
