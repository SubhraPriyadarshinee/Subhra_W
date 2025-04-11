package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.InstructionIdAndTrackingIdPair;
import com.walmart.move.nim.receiving.core.model.PalletHistory;
import com.walmart.move.nim.receiving.core.model.ReceipPutawayQtySummaryByContainer;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemCustomRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * This service is responsible for persisting smaller transaction related to container. As in spring
 * transaction boundary is not getting created if caller method and called method are in same class.
 *
 * <p>Reason - The problem here is, that Spring's AOP proxies don't extend but rather wrap your
 * service instance to intercept calls. This has the effect, that any call to "this" from within
 * your service instance is directly invoked on that instance and cannot be intercepted by the
 * wrapping proxy (the proxy is not even aware of any such call).
 */
@Service(ReceivingConstants.CONTAINER_PERSISTER_SERVICE)
public class ContainerPersisterService implements Purge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPersisterService.class);
  @Autowired private ReceiptRepository receiptRepository;
  @Autowired private ContainerRepository containerRepository;
  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ContainerItemCustomRepository containerItemCustomRepository;

  @ManagedConfiguration private AppConfig appConfig;

  @Transactional
  @InjectTenantFilter
  public boolean checkIfContainerExist(String trackingId) {
    return containerRepository.existsByTrackingId(trackingId);
  }

  /**
   * This method will get container details based on tracker id
   *
   * @param parentTrackingId
   * @return ContainerModel
   */
  // TODO: Need to look into a better approach to get ContainerItems instead of making a call for 1
  // Id in a FOR loop
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Set<Container> getContainerDetailsByParentTrackingId(String parentTrackingId) {
    Set<Container> containersByParentTrackingId =
        containerRepository.findAllByParentTrackingId(parentTrackingId);
    if (CollectionUtils.isNotEmpty(containersByParentTrackingId)) {
      containersByParentTrackingId.forEach(
          container -> {
            container.setContainerItems(
                containerItemRepository.findByTrackingId(container.getTrackingId()));
          });
    }
    return containersByParentTrackingId;
  }

  /**
   * This method will get container details based on tracker id
   *
   * @param trackingId
   * @return ContainerModel
   */
  @Transactional
  @InjectTenantFilter
  public Container getContainerDetails(String trackingId) {
    Container container = containerRepository.findByTrackingId(trackingId);
    if (container != null) {
      List<ContainerItem> containerItems = containerItemRepository.findByTrackingId(trackingId);
      container.setContainerItems(containerItems);
    }
    return container;
  }

  /**
   * This method gets instruction Ids based on trackingIds
   *
   * @param trackingIds
   * @return instructionIds
   */
  @Transactional
  @InjectTenantFilter
  public List<Long> getInstructionIdsByTrackingIds(List<String> trackingIds) {
    return containerRepository.getInstructionIdsByTrackingIds(
        trackingIds, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
  }

  @Transactional
  @InjectTenantFilter
  public List<InstructionIdAndTrackingIdPair> getInstructionIdsObjByTrackingIds(
      List<String> trackingIds) {
    return containerRepository.getInstructionIdsObjByTrackingIds(
        trackingIds, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
  }

  /**
   * This method is responsible for persisting the containers.
   *
   * @param containerList
   * @return container
   */
  @Transactional
  @InjectTenantFilter
  public void saveContainers(List<Container> containerList) {
    containerRepository.saveAll(containerList);
  }

  /**
   * This method is responsible for persisting the container.
   *
   * @param container
   * @return container
   */
  @Transactional
  @InjectTenantFilter
  public Container saveContainer(Container container) {
    if (container.getContainerItems() != null && !container.getContainerItems().isEmpty()) {
      containerItemRepository.saveAll(container.getContainerItems());
    }
    return containerRepository.save(container);
  }

  /**
   * This method is responsible for save container and flush and return the updated container.
   *
   * @param container
   * @return container
   */
  @Transactional
  @InjectTenantFilter
  public Container saveAndFlushContainer(Container container) {
    if (container.getContainerItems() != null && !container.getContainerItems().isEmpty()) {
      containerItemRepository.saveAll(container.getContainerItems());
    }
    return containerRepository.saveAndFlush(container);
  }

  @Transactional
  @InjectTenantFilter
  public void saveContainerAndContainerItems(
      List<Container> containers, List<ContainerItem> containerItems) {
    containerRepository.saveAll(containers);
    containerItemRepository.saveAll(containerItems);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteContainerItems(List<ContainerItem> containerItems) {
    containerItemRepository.deleteAll(containerItems);
  }

  /**
   * This method is responsible for deleting container and container items given a list of tracking
   * ids
   *
   * @param trackingIds
   */
  @Transactional
  @InjectTenantFilter
  public void deleteContainerAndContainerItemsGivenTrackingId(List<String> trackingIds) {
    containerRepository.deleteByTrackingIdIn(trackingIds);
    containerItemRepository.deleteByTrackingIdIn(trackingIds);
    LOGGER.info(
        "Existing TrackingIds deleted from container and container_item table {}", trackingIds);
  }

  /**
   * This method is responsible for persisting the receipts and container.
   *
   * @param receipts
   * @param container
   */
  @Transactional
  @InjectTenantFilter
  public void createReceiptAndContainer(List<Receipt> receipts, Container container) {
    saveContainer(container);
    receiptRepository.saveAll(receipts);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - This method is responsible for persisting the
   * receipts, containers and container items
   *
   * @param receipts the list of receipts
   * @param containers the list of containers
   * @param containerItems the list of container items
   */
  @Transactional
  @InjectTenantFilter
  public void createMultipleReceiptAndContainer(
      List<Receipt> receipts, List<Container> containers, List<ContainerItem> containerItems) {
    saveContainerAndContainerItems(containers, containerItems);
    receiptRepository.saveAll(receipts);
  }

  /**
   * This method will update container status in DB
   *
   * @param trackerId
   * @param status
   * @param userId
   * @param receipts
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public void updateContainerStatusAndSaveReceipts(
      String trackerId, String status, String userId, List<Receipt> receipts) {
    Container container = getContainerDetails(trackerId);
    container.setContainerStatus(status);
    container.setLastChangedUser(userId);
    // Setting containerId to null as labels for backout containers can not be reprinted
    container.setLabelId(null);
    containerRepository.save(container);
    receiptRepository.saveAll(receipts);
  }

  /**
   * This method will update Container, ContainerItem and add new Receipt to db
   * WitronContainerServiceTest
   *
   * @param container
   * @param ci
   * @param userId
   * @param receipts
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public void updateContainerContainerItemReceipt(
      Container container, ContainerItem ci, String userId, List<Receipt> receipts) {
    container.setLastChangedUser(userId);
    containerRepository.save(container);
    containerItemRepository.save(ci);
    receiptRepository.saveAll(receipts);
  }
  /**
   * Get the container details with child containers excluding child contents
   *
   * @param trackingId
   * @return Container
   */
  @Transactional
  @InjectTenantFilter
  public Container getContainerWithChildContainersExcludingChildContents(String trackingId) {
    Container container = containerRepository.findByTrackingId(trackingId);
    if (container != null) {
      List<ContainerItem> containerItems = containerItemRepository.findByTrackingId(trackingId);
      container.setContainerItems(containerItems);

      Set<Container> childContainers = containerRepository.findAllByParentTrackingId(trackingId);
      container.setChildContainers(childContainers);
    }

    return container;
  }

  /**
   * Get the list of containers with container items for a given delivery.
   *
   * @param deliveryNumber
   * @return @{@link List}
   */
  @Transactional
  @InjectTenantFilter
  public List<Container> getContainerByDeliveryNumber(Long deliveryNumber) {
    List<Container> containers = containerRepository.findByDeliveryNumber(deliveryNumber);
    getContainerDetails(containers);
    return containers;
  }

  private void getContainerDetails(List<Container> containers) {
    if (!CollectionUtils.isEmpty(containers)) {
      Set<String> trackingIds =
          containers.stream().map(Container::getTrackingId).collect(Collectors.toSet());
      List<ContainerItem> containerItemList = new ArrayList<>();
      Collection<List<String>> partitionedTrackingIds =
          ReceivingUtils.batchifyCollection(trackingIds, appConfig.getInSqlBatchSize());
      partitionedTrackingIds.forEach(
          trackingIdSet ->
              containerItemList.addAll(containerItemRepository.findByTrackingIdIn(trackingIdSet)));
      containers.forEach(
          container ->
              container.setContainerItems(
                  containerItemList
                      .stream()
                      .filter(ci -> ci.getTrackingId().equalsIgnoreCase(container.getTrackingId()))
                      .collect(Collectors.toList())));
    }
  }

  /**
   * Method to fetch container based by parentTrackingId and status
   *
   * @param parentTrackingIds
   * @param containerStatusList
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public List<Container> getContainerByParentTrackingIdInAndContainerStatus(
      List<String> parentTrackingIds, List<String> containerStatusList) {
    Collection<List<String>> partitionedParentTrackingIds =
        ReceivingUtils.batchifyCollection(parentTrackingIds, appConfig.getInSqlBatchSize());
    List<Container> containers = new ArrayList<>();
    partitionedParentTrackingIds.forEach(
        trackingIdSet ->
            containers.addAll(
                containerRepository.findByParentTrackingIdInAndContainerStatusIn(
                    trackingIdSet, containerStatusList)));
    getContainerDetails(containers);
    return containers;
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<Container> containerList =
        containerRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);
    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    containerList =
        containerList
            .stream()
            .filter(container -> container.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(Container::getId))
            .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(containerList)) {
      LOGGER.info("Purge CONTAINER: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = containerList.get(containerList.size() - 1).getId();

    LOGGER.info(
        "Purge CONTAINER: {} records : ID {} to {} : START",
        containerList.size(),
        containerList.get(0).getId(),
        lastDeletedId);
    List<String> trackingIds = new ArrayList<>();
    containerList.stream().forEach(container -> trackingIds.add(container.getTrackingId()));
    // delete all containerItems for these containers
    if (!CollectionUtils.isEmpty(trackingIds)) {
      LOGGER.info("Purge CONTAINER-ITEM trackingIds : {} ", trackingIds);
      containerItemRepository.deleteByTrackingIdIn(trackingIds);
    }
    containerRepository.deleteAll(containerList);
    LOGGER.info("Purge CONTAINER: END");
    return lastDeletedId;
  }

  /**
   * Get the container details including child containers
   *
   * @param trackingId
   * @return ConsolidatedContainer
   * @throws ReceivingException
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Container getConsolidatedContainerForPublish(String trackingId) throws ReceivingException {
    LOGGER.info("Entering getConsolidatedContainerForPublish() with trackingId:{}", trackingId);
    if (StringUtils.isEmpty(trackingId)) {
      throw new ReceivingException("trackingId should not be null", HttpStatus.BAD_REQUEST);
    }

    Container container = getContainerDetails(trackingId);

    if (container == null) {
      throw new ReceivingException("container not found", HttpStatus.BAD_REQUEST);
    } else if (isOfflineContainer(container)) {
      /**
       * Setting ASN number and DocType as ASN for offline - will be used as part of
       * /container/publish/kafka API call
       */
      container.setAsnNumber(container.getShipmentId());
      container.setDocumentType(DocumentType.ASN.getDocType());
    }

    Set<Container> childContainerList = containerRepository.findAllByParentTrackingId(trackingId);
    if (CollectionUtils.isEmpty(childContainerList)) {
      container.setChildContainers(childContainerList);
      return container;
    }

    List<String> childContainerTrackingIds = new ArrayList<>();
    childContainerList.forEach(
        child -> {
          childContainerTrackingIds.add(child.getTrackingId());
          if (isOfflineContainer(container)) {
            /**
             * Setting ASN number and DocType as ASN for offline - will be used as part of
             * /container/publish/kafka API call
             */
            child.setAsnNumber(child.getShipmentId());
            child.setDocumentType(DocumentType.ASN.getDocType());
          }
        });

    List<ContainerItem> childContainerItems =
        containerItemRepository.findByTrackingIdIn(childContainerTrackingIds);

    Map<String, List<ContainerItem>> childContainerItemMap = new HashMap<>();
    for (ContainerItem containerItem : childContainerItems) {
      if (!CollectionUtils.isEmpty(childContainerItemMap.get(containerItem.getTrackingId()))) {
        childContainerItemMap.get(containerItem.getTrackingId()).add(containerItem);
      } else {
        childContainerItemMap.put(
            containerItem.getTrackingId(), new ArrayList<>(Arrays.asList(containerItem)));
      }
    }

    childContainerList.forEach(
        child -> child.setContainerItems(childContainerItemMap.get(child.getTrackingId())));

    container.setChildContainers(childContainerList);
    container.setHasChildContainers(!CollectionUtils.isEmpty(childContainerList));

    return container;
  }

  /**
   * Check if container has label type as XDK1/XDK2 in misc info - for offline
   *
   * @param container
   * @return
   */
  private static boolean isOfflineContainer(Container container) {
    return container.getContainerMiscInfo() != null
        && container.getContainerMiscInfo().containsKey(ReceivingConstants.INVENTORY_LABEL_TYPE)
        && (ReceivingConstants.XDK1.equals(
                container.getContainerMiscInfo().get(ReceivingConstants.INVENTORY_LABEL_TYPE))
            || ReceivingConstants.XDK2.equals(
                container.getContainerMiscInfo().get(ReceivingConstants.INVENTORY_LABEL_TYPE)));
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> getContainers(
      String orderByColumnName, String sortOrder, int page, int limit, boolean parentOnly) {

    Sort sortByColumnAndOrder = null;
    if (ReceivingConstants.ORDER_DESC.equals(sortOrder)) {
      sortByColumnAndOrder = Sort.by(orderByColumnName).descending();
    } else {
      sortByColumnAndOrder = Sort.by(orderByColumnName).ascending();
    }
    Pageable containerPageable = PageRequest.of(page, limit, sortByColumnAndOrder);
    Page<Container> containersPage = null;
    if (parentOnly) {
      containersPage = containerRepository.findByParentTrackingIdIsNull(containerPageable);
    } else {
      containersPage = containerRepository.findAll(containerPageable);
    }
    List<Container> resultList = new ArrayList<>();
    containersPage
        .getContent()
        .forEach(
            container -> {
              if (CollectionUtils.isNotEmpty(container.getContainerItems())
                  && Objects.isNull(container.getContainerItems().get(0).getQuantity())) {
                int calculatedValue =
                    calculateQtyFromChildContainers(container.getChildContainers());
                container.getContainerItems().get(0).setQuantity(calculatedValue);
                container.getContainerItems().get(0).setQuantityUOM(ReceivingConstants.Uom.EACHES);
              }

              resultList.add(container);
            });

    return resultList;
  }

  @Transactional(readOnly = true)
  public int receivedContainerQuantityBySSCCAndStatus(String ssccNumber) {
    Integer receivedQty =
        containerItemRepository.receivedContainerQuantityBySSCCAndStatus(
            ssccNumber,
            ReceivingConstants.STATUS_BACKOUT,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
    return receivedQty == null ? 0 : receivedQty;
  }

  @Transactional(readOnly = true)
  public int receivedContainerQuantityBySSCC(String ssccNumber) {
    Integer receivedQty =
        containerItemRepository.receivedContainerQuantityBySSCCAndStatusIsNull(
            ssccNumber, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    return receivedQty == null ? 0 : receivedQty;
  }

  @Transactional(readOnly = true)
  public Optional<List<Long>> receivedContainerDetailsBySSCC(String ssccNumber) {
    return containerItemRepository.receivedContainerDetailsBySSCC(
        ssccNumber,
        ReceivingConstants.STATUS_BACKOUT,
        TenantContext.getFacilityNum(),
        TenantContext.getFacilityCountryCode());
  }

  private int calculateQtyFromChildContainers(Set<Container> childContainers) {
    int quantity = 0;
    if (CollectionUtils.isNotEmpty(childContainers)) {
      for (Container container : childContainers) {
        quantity += container.getContainerItems().get(0).getQuantity();
      }
    }
    return quantity;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Optional<ContainerItem> findByInvoiceNumber(String invoiceNumber) {
    return containerItemRepository.findTopByInvoiceNumberOrderByInvoiceLineNumberDesc(
        invoiceNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findBySSCCAndInventoryStatus(String sscc, String inventoryStatus) {
    return containerRepository.findAllBySsccNumberAndInventoryStatus(sscc, inventoryStatus);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findAllBySSCCAndInventoryStatus(String sscc, String inventoryStatus) {

    List<Container> containers =
        containerRepository.findAllBySsccNumberAndInventoryStatus(sscc, inventoryStatus);

    List<String> trackingIds =
        containers.stream().map(cntr -> cntr.getTrackingId()).collect(Collectors.toList());

    List<ContainerItem> containerItems = containerItemRepository.findByTrackingIdIn(trackingIds);

    if (Objects.isNull(containerItems) || containerItems.isEmpty()) {
      return containers;
    }

    ContainerUtils.populateContainerItemInContainer(containers, containerItems);

    return containers;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Container findBySSCCAndDeliveryNumber(String sscc, Long deliveryNumber) {
    Container container =
        containerRepository.findTopBySsccNumberAndDeliveryNumber(sscc, deliveryNumber);
    if (Objects.isNull(container)) {
      throw new ReceivingInternalException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          String.format(
              "Container with sscc = %s and deliveryNumber=%s is not found in system",
              sscc, deliveryNumber));
    }
    List<ContainerItem> containerItems =
        containerItemRepository.findByTrackingIdIn(Arrays.asList(container.getTrackingId()));
    ContainerUtils.populateContainerItemInContainer(Arrays.asList(container), containerItems);
    return container;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findBySSCCAndDeliveryNumberIn(String sscc, List<Long> deliveries) {
    List<Container> containers =
        containerRepository.findBySsccNumberAndDeliveryNumberIn(sscc, deliveries);
    if (Objects.isNull(containers) || containers.isEmpty()) {
      return new ArrayList<>();
    }
    findAllContainerItemsForContainerList(containers);
    return containers;
  }

  private void findAllContainerItemsForContainerList(List<Container> containers) {
    List<String> trackingIds =
        containers
            .stream()
            .map(container -> container.getTrackingId())
            .collect(Collectors.toList());
    List<ContainerItem> containerItems = containerItemRepository.findByTrackingIdIn(trackingIds);
    ContainerUtils.populateContainerItemInContainer(containers, containerItems);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findAllBySSCC(String sscc) {
    return containerRepository.findAllBySsccNumber(sscc);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findAllBySSCCIn(List<String> ssccList) {
    List<Container> containerList = containerRepository.findAllBySsccNumberIn(ssccList);
    findAllContainerItemsForContainerList(containerList);
    return containerList;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<ContainerItem> findAllItemByTrackingId(List<String> trackingIds) {
    return containerItemRepository.findByTrackingIdIn(new ArrayList<>(trackingIds));
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> getContainerListByTrackingIdList(List<String> trackingIds) {
    Set<Container> distinctContainer = containerRepository.findByTrackingIdIn(trackingIds);

    List<String> _allContainerIds =
        distinctContainer
            .stream()
            .map(container -> container.getTrackingId())
            .collect(Collectors.toList());

    List<ContainerItem> containerItems =
        containerItemRepository.findByTrackingIdIn(_allContainerIds);

    List<Container> containers = new ArrayList<>(distinctContainer);

    ContainerUtils.populateContainerItemInContainer(containers, containerItems);
    return containers;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<String> getExistingParentTrackingIds(List<String> parentTrackingIds) {
    return containerRepository.getExistingParentTrackingIds(parentTrackingIds);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Set<String> findSsccByDelivery(Long deliveryNumber) {
    return containerRepository.findSsccByDelivery(deliveryNumber);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<Container> findByDeliveryNumberAndSsccIn(Long deliveryNumber, List<String> sscc) {
    List<Container> containers =
        containerRepository.findAllByDeliveryNumberAndSsccNumberIn(deliveryNumber, sscc);
    if (Objects.nonNull(containers) && !containers.isEmpty()) {
      List<ContainerItem> containerItems =
          containerItemRepository.findByTrackingIdIn(
              containers.stream().map(Container::getTrackingId).collect(Collectors.toList()));
      ContainerUtils.populateContainerItemInContainer(containers, containerItems);
    }
    return containers;
  }

  @Transactional
  @InjectTenantFilter
  public List<Container> getContainerByDeliveryNumberIn(List<Long> deliveryNumbers) {
    List<Container> containers = containerRepository.findByDeliveryNumberIn(deliveryNumbers);
    getContainerDetails(containers);
    return containers;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> findContainerByDeliveryNumber(Long deliveryNumber) {
    return containerRepository.findByDeliveryNumber(deliveryNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<PalletHistory> findReceivedHistoryByDeliveryNumber(Long deliveryNumber) {
    return containerRepository.findByOnlyDeliveryNumber(deliveryNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<PalletHistory> findReceivedHistoryByDeliveryNumberWithPO(
      Long deliveryNumber, String po, Integer poLine) {
    return containerRepository.findByDeliveryNumberWithPO(deliveryNumber, po, poLine);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Container getContainerDetailsWithoutChild(String trackingId) {
    Container container = containerRepository.findByTrackingId(trackingId);
    if (container != null) {
      List<ContainerItem> containerItems = containerItemRepository.findByTrackingId(trackingId);
      container.setContainerItems(containerItems);
      Set<Container> childContainerList = new HashSet<>();
      container.setChildContainers(childContainerList);
    }
    return container;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<ReceipPutawayQtySummaryByContainer> getReceiptPutawayQtySummaryByDeliveryNumber(
      Long deliveryNumber) {
    return containerItemCustomRepository.getReceiptPutawayQtySummaryByDeliveryNumber(
        deliveryNumber);
  }

  /**
   * Get the list of containers with container items for a given instruction id.
   *
   * @param instructionId
   * @return @{@link List}
   */
  @Transactional
  @InjectTenantFilter
  public List<Container> getContainersByInstructionId(Long instructionId) {
    List<Container> containers = containerRepository.findByInstructionId(instructionId);
    getContainerDetails(containers);
    return containers;
  }
}
