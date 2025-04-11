package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.entity.LpnSwapEntity;
import com.walmart.move.nim.receiving.acc.model.*;
import com.walmart.move.nim.receiving.acc.repositories.LPNSwapRepository;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.transaction.Transactional;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class HawkeyeLpnSwapService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkeyeLpnSwapService.class);

  @Autowired private LPNSwapRepository lpnSwapRepository;
  @Autowired private ContainerService containerService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Transactional
  @InjectTenantFilter
  public LpnSwapEntity saveLPNSwapEntry(HawkeyeLpnPayload hawkeyeLpnPayload) {
    LpnSwapEntity lpnSwapEntity = getLpnSwapEntityFromPayload(hawkeyeLpnPayload);
    try {
      lpnSwapEntity = lpnSwapRepository.save(lpnSwapEntity);
    } catch (Exception ex) {
      LOGGER.error("Could not save lpnSwapEntity {}", ex.getMessage());
    }
    return lpnSwapEntity;
  }

  @Transactional
  @InjectTenantFilter
  public LpnSwapEntity updateLPNSwapEntry(LpnSwapEntity lpnSwapEntity) {
    lpnSwapEntity.setSwapStatus(EventTargetStatus.DELETE);
    lpnSwapEntity.setSwapTs(new Date());
    LOGGER.info("Updating the accPaLpnSwapEntity with status {}", EventTargetStatus.DELETE);
    return lpnSwapRepository.save(lpnSwapEntity);
  }

  public LpnSwapEntity getLpnSwapEntityFromPayload(HawkeyeLpnPayload hawkeyeLpnPayload) {
    return LpnSwapEntity.builder()
        .lpn(hawkeyeLpnPayload.getLpn())
        .swappedLPN(hawkeyeLpnPayload.getSwappedLpn())
        .destination(hawkeyeLpnPayload.getDestination())
        .swappedDestination(hawkeyeLpnPayload.getSwappedDestination())
        .groupNumber(hawkeyeLpnPayload.getGroupNumber())
        .itemNumber(hawkeyeLpnPayload.getItemNumber())
        .poNumber(hawkeyeLpnPayload.getPoNumber())
        .poType(hawkeyeLpnPayload.getPoType())
        .swapStatus(EventTargetStatus.PENDING)
        .build();
  }

  @Transactional
  public void saveContainersAfterSwap(Container firstContainer, Container swapContainer) {
    LOGGER.info("Going to save the containers after swap");
    List<Container> containers = new ArrayList<>();
    containers.add(firstContainer);
    containers.add(swapContainer);
    containerService.saveAll(containers);
    LOGGER.info("Containers saved");
  }

  public void swapAndProcessLpn(HawkeyeLpnPayload hawkeyeLpnPayload) {

    LOGGER.info(
        "Going to swap lpn {} and swap lpn {}",
        hawkeyeLpnPayload.getLpn(),
        hawkeyeLpnPayload.getSwappedLpn());
    try {
      Container firstContainer =
          containerService.getContainerByTrackingId(hawkeyeLpnPayload.getLpn());
      Container swapContainer =
          containerService.getContainerByTrackingId(hawkeyeLpnPayload.getSwappedLpn());

      if (Objects.isNull(firstContainer)) {
        LOGGER.error(
            "Error in LPN SWAP, due to Container not found for LPN {}", hawkeyeLpnPayload.getLpn());
        return;
      }
      if (Objects.isNull(swapContainer)) {
        LOGGER.error(
            "Error in LPN SWAP, due to Container not found for swapped LPN {}",
            hawkeyeLpnPayload.getSwappedLpn());
        return;
      }

      LOGGER.info(
          "ACC PA LPN Swap first container: {} and swap Container: {}",
          firstContainer.getTrackingId(),
          swapContainer.getTrackingId());

      // Save details to DB before the swap
      LpnSwapEntity lpnSwapEntity = saveLPNSwapEntry(hawkeyeLpnPayload);

      if (Objects.isNull(lpnSwapEntity)) {
        LOGGER.error("Could not save lpnSwapEntity");
      } else {
        LOGGER.info(
            "Containers Before Swap FirstContainer: {}, SwapContainer:{} ",
            getContainersAttributes(firstContainer),
            getContainersAttributes(swapContainer));

        swapContainers(firstContainer, swapContainer);

        LOGGER.info(
            "Containers After Swap FirstContainer: {}, SwapContainer:{} ",
            getContainersAttributes(firstContainer),
            getContainersAttributes(swapContainer));

        // Save Containers after swap to DB
        saveContainersAfterSwap(firstContainer, swapContainer);

        // update details to DB after the swap
        updateLPNSwapEntry(lpnSwapEntity);

        // Process Event
        eventProcess(firstContainer, swapContainer);
      }

    } catch (Exception ex) {
      LOGGER.error("Unable to swap containers exception is - {}", ExceptionUtils.getStackTrace(ex));
    }
  }

  public void eventProcess(Container firstContainer, Container swapContainer) {
    LOGGER.info(
        "Going to process the event for swap lpn {} and swap lpn {}",
        firstContainer.getTrackingId(),
        swapContainer.getTrackingId());
    try {
      HawkeyeLpnSwapEventMessage hawkeyeLpnSwapEventMessage =
          getLpnSwapEventMessage(firstContainer, swapContainer);
      EventProcessor accPaLpnSwapEventProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ACCConstants.HAWKEYE_LPN_SWAP_PROCESSOR,
              EventProcessor.class);
      accPaLpnSwapEventProcessor.processEvent(hawkeyeLpnSwapEventMessage);
    } catch (ReceivingException e) {
      LOGGER.error(
          "Unable to process ACL Verification Kafka message - {}", ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.ACL_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_NOTIFICATION_ERROR_MSG),
          e);
    }
  }

  private HawkeyeLpnSwapEventMessage getLpnSwapEventMessage(
      Container firstContainer, Container swappedContainer) {

    LOGGER.info("method getAccPaLpnSwapEventMessage() called...");

    HawkeyeLpnSwapEventMessage hawkeyeLpnSwapEventMessage = new HawkeyeLpnSwapEventMessage();

    SwapContainer finalContainer = new SwapContainer();
    SwapContainer swapContainer = new SwapContainer();

    // Set Tracking ID
    finalContainer.setTrackingId(firstContainer.getTrackingId());
    swapContainer.setTrackingId(swappedContainer.getTrackingId());

    // Set Message Id
    finalContainer.setMessageId(firstContainer.getTrackingId());
    swapContainer.setMessageId(swappedContainer.getTrackingId());

    // Set Destination
    finalContainer.setDestination(firstContainer.getDestination());
    swapContainer.setDestination(swappedContainer.getDestination());

    /**
     * Set Distribution,Purchase ref line number and Purchase Ref number to final ContainerItem list
     */
    List<SwapContent> finalContainerItemList = getSwapContentListFromContainer(firstContainer);

    /**
     * Set Distribution,Purchase ref line number and Purchase Ref number to swap ContainerItem list
     */
    List<SwapContent> swappedContainerItemList = getSwapContentListFromContainer(swappedContainer);

    finalContainer.setContents(finalContainerItemList);
    swapContainer.setContents(swappedContainerItemList);

    hawkeyeLpnSwapEventMessage.setSwapContainer(swapContainer);
    hawkeyeLpnSwapEventMessage.setFinalContainer(finalContainer);

    return hawkeyeLpnSwapEventMessage;
  }

  private List<SwapContent> getSwapContentListFromContainer(Container container) {
    List<SwapContent> containerItemList = new ArrayList<>();
    for (ContainerItem ci : container.getContainerItems()) {
      SwapContent localCI = new SwapContent();
      SwapDistribution sd;
      List<SwapDistribution> sdList = new ArrayList<>();
      for (int i = 0; i < ci.getDistributions().size(); i++) {
        sd = new SwapDistribution();
        sd.setAllocQty(ci.getDistributions().get(i).getAllocQty());
        sd.setOrderId(ci.getDistributions().get(i).getOrderId());
        sd.setItem(ci.getDistributions().get(i).getItem());
        sdList.add(sd);
      }
      localCI.setPurchaseReferenceLineNumber(ci.getPurchaseReferenceLineNumber());
      localCI.setPurchaseReferenceNumber(ci.getPurchaseReferenceNumber());
      localCI.setDistributions(sdList);
      containerItemList.add(localCI);
    }
    return containerItemList;
  }

  private String getContainersAttributes(Container container) {

    StringJoiner containerValue = new StringJoiner(",", "[", "]");
    containerValue.add("lpn: " + container.getTrackingId());
    containerValue.add("Destination: " + container.getDestination());

    if ((!Objects.isNull(container.getContainerItems())
        && container.getContainerItems().size() > 0)) {
      containerValue.add(
          "PurchaseReferenceLineNumber: "
              + container.getContainerItems().get(0).getPurchaseReferenceLineNumber());
      containerValue.add(
          "Distributions: " + container.getContainerItems().get(0).getDistributions());
    }
    return containerValue.toString();
  }

  public void swapContainers(Container firstContainer, Container swapContainer) {

    LOGGER.info(
        "Swaping first container: {} and swap Container: {}",
        firstContainer.getTrackingId(),
        swapContainer.getTrackingId());

    // Swap Destination
    Map<String, String> firstContainerDestination = firstContainer.getDestination();
    firstContainer.setDestination(swapContainer.getDestination());
    swapContainer.setDestination(firstContainerDestination);

    List<ContainerItem> firstContainerItem = firstContainer.getContainerItems();
    List<ContainerItem> swappedContainerItem = swapContainer.getContainerItems();

    try {
      for (int i = 0; i < firstContainerItem.size(); i++) {
        ContainerItem ciFirst = firstContainerItem.get(i);
        ContainerItem ciSwapped = swappedContainerItem.get(i);
        List<Distribution> dlFirst = ciFirst.getDistributions();
        Integer purRefLineNumberFirst = ciFirst.getPurchaseReferenceLineNumber();

        // Set Distributions - swapped container Distribution set to first container
        ciFirst.setDistributions(swappedContainerItem.get(i).getDistributions());

        // Set PurchaseRefLineNum - swapped container PurchaseRefLineNum set to first container
        ciFirst.setPurchaseReferenceLineNumber(
            swappedContainerItem.get(i).getPurchaseReferenceLineNumber());

        // Set Distributions - First container Distribution set to swapped container
        ciSwapped.setDistributions(dlFirst);

        // Set PurchaseRefLineNum - first container PurchaseRefLineNum set to swapped container
        ciSwapped.setPurchaseReferenceLineNumber(purRefLineNumberFirst);

        // Set first ContainerItem to list
        firstContainerItem.set(i, ciFirst);

        // Set swapped container item to list
        swappedContainerItem.set(i, ciSwapped);
      }

      // Update first container item in first container
      firstContainer.setContainerItems(firstContainerItem);

      // Update swapped container item in swap container
      swapContainer.setContainerItems(swappedContainerItem);

    } catch (IndexOutOfBoundsException ioobe) {
      LOGGER.error(
          "IndexOutOfBoundsException while swapping container ACC PA, {}", ioobe.getMessage());
    } catch (Exception ex) {
      LOGGER.error("Exception while swapping container ACC PA, {}", ex.getMessage());
    }
  }
}
