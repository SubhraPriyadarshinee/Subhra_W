package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.mapper.ContainerMapper.populateContainer;
import static com.walmart.move.nim.receiving.sib.mapper.ContainerMapper.populatePackItem;
import static com.walmart.move.nim.receiving.sib.mapper.ReceiptMapper.getReceipts;
import static com.walmart.move.nim.receiving.sib.utils.Constants.*;
import static com.walmart.move.nim.receiving.sib.utils.Util.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_ON_SCAN_RECEIPT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.Util;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class PackContainerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PackContainerService.class);

  @Autowired private StoreDeliveryService deliveryService;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ReceiptService receiptService;

  @Autowired private ContainerTransformer containerTransformer;

  @Autowired private com.walmart.move.nim.receiving.core.service.ContainerService containerService;

  @Autowired private ReceivingCounterService receivingCounterService;

  @Autowired private Gson gson;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @ManagedConfiguration SIBManagedConfig sibManagedConfig;

  @ManagedConfiguration private AppConfig appConfig;

  public List<ContainerDTO> createCaseContainers(Long deliveryNumber) {
    List<ContainerDTO> containerDTOList = new ArrayList<>();

    if (!tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_PUBLISH)) {
      return containerDTOList;
    }

    List<ASNDocument> asnDocuments = getEligibleASNDocuments(deliveryNumber);

    // create and save containers
    removePallets(asnDocuments);
    List<Container> containers = createContainer(asnDocuments);

    if (CollectionUtils.isNotEmpty(containers)) {
      containerPersisterService.saveContainerAndContainerItems(
          containers,
          containers
              .stream()
              .flatMap(container -> container.getContainerItems().stream())
              .collect(Collectors.toList()));

      // create receipts
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)) {
        createReceipt(containers);
      }

      // Replacing trackingId with sscc number
      List<Container> containerToBePublished =
          containers
              .stream()
              .map(
                  container -> {
                    Util.replaceSSCCWithTrackingId(container);
                    return container;
                  })
              .collect(Collectors.toList());

      // publish containers
      containerDTOList = containerTransformer.transformList(containerToBePublished);
      LOGGER.info("Going to publish {} cases", containerDTOList.size());
      Collection<List<ContainerDTO>> batchedContainers =
          ReceivingUtils.batchifyCollection(
              containerDTOList, sibManagedConfig.getPublishContainerKafkaBatchSize());
      batchedContainers.forEach(
          containerDTOs -> {
            LOGGER.info(
                "Publishing case containers :{}",
                containerDTOs
                    .stream()
                    .map(ContainerDTO::getTrackingId)
                    .collect(Collectors.toList()));

            containerService.publishMultipleContainersToInventory(containerDTOs);
          });
    }
    return containerDTOList;
  }

  private void createReceipt(List<Container> containers) {
    // get receipts
    List<Receipt> receipts = getReceipts(containers);

    // save receipts
    Collection<List<Receipt>> batchedReceipts =
        ReceivingUtils.batchifyCollection(receipts, sibManagedConfig.getReceiptSaveBatchSize());
    batchedReceipts.forEach(receiptBatch -> receiptService.saveAll(receiptBatch));
  }

  private List<Container> createContainer(List<ASNDocument> asnDocuments) {
    List<Container> containers = new ArrayList<>();
    for (ASNDocument asnDocument : asnDocuments) {
      try {
        List<Container> createdContainers = createContainer(asnDocument);
        if (CollectionUtils.isNotEmpty(createdContainers)) {
          containers.addAll(createdContainers);
        }
      } catch (Exception e) {
        LOGGER.error("Error while processing asn {}", asnDocument, e);
      }
    }
    return containers;
  }

  private List<Container> createContainer(ASNDocument asnDocument) {
    List<Pack> packs = asnDocument.getPacks();

    if (Objects.isNull(packs) || packs.isEmpty()) {
      return null;
    }

    Set<String> existingSsccNumbers =
        containerPersisterService.findSsccByDelivery(asnDocument.getDelivery().getDeliveryNumber());

    Map<Long, ItemDetails> itemMap = Util.extractItemMapFromASN(asnDocument);

    List<Container> createdContainers = new ArrayList<>();
    for (Pack pack : asnDocument.getPacks()) {
      try {
        LOGGER.info("StoreInbound : Creating container for pack number {}", pack.getPackNumber());
        if (existingSsccNumbers.contains(getPackId(pack))) {
          LOGGER.info("StoreInbound : Container for SSCC: {} already exists", getPackId(pack));
          continue;
        }
        Container container =
            getContainer(
                asnDocument.getDelivery().getDeliveryNumber(),
                asnDocument.getShipment(),
                itemMap,
                pack);
        if (Objects.nonNull(container)) {
          createdContainers.add(container);
        }
      } catch (Exception e) {
        LOGGER.error(
            "StoreInbound : Exception while creating pack number {}", pack.getPackNumber(), e);
      }

      if (CollectionUtils.isEmpty(createdContainers)) {
        throw new ReceivingConflictException(
            ExceptionCodes.UNABLE_TO_CREATE_CONTAINER,
            "Unable to create container for shipment {} and delivery Number {}",
            asnDocument.getShipment().getDocumentId(),
            asnDocument.getDelivery().getDeliveryNumber());
      }
    }
    return createdContainers;
  }

  /**
   * Creating container for a Store Case (pack) in the ASN. Ignores MFC items
   *
   * @param deliveryNumber
   * @param shipment
   * @param itemMap
   * @param pack
   * @return container
   */
  private Container getContainer(
      Long deliveryNumber, Shipment shipment, Map<Long, ItemDetails> itemMap, Pack pack) {
    Container container = null;
    container =
        populateContainer(
            deliveryNumber,
            shipment,
            pack,
            generateTCL(LabelType.OTHERS),
            generateTCL(LabelType.TPL));

    List<ContainerItem> containerItems = new ArrayList<>();

    for (Item item : pack.getItems()) {
      // Ignoring MFC items
      if (StringUtils.equalsIgnoreCase(item.getReplenishmentCode(), "MARKET_FULFILLMENT_CENTER")) {
        continue;
      }
      ContainerItem containerItem =
          tenantSpecificConfigReader
                  .getScalingQtyEnabledForReplenishmentTypes()
                  .contains(item.getReplenishmentCode())
              ? populatePackItem(
                  pack, item, appConfig.getUomScalingPrefix(), appConfig.getScalableUomList())
              : populatePackItem(pack, item, null, null);
      populateOrDefaultWareHouseAreaCode(containerItem, itemMap);
      populateDepartmentCategory(containerItem, itemMap);
      populateDeptSubCategory(containerItem, itemMap);
      containerItem.setTrackingId(container.getTrackingId());
      if (Constants.SHIPPER.equals(item.getAssortmentType())
          && CollectionUtils.isNotEmpty(item.getChildItems())) {
        containerItems.addAll(createChildItems(containerItem, item.getChildItems()));
      } else {
        containerItems.add(containerItem);
      }
    }

    if (CollectionUtils.isEmpty(containerItems)) {
      LOGGER.warn(
          "No new eligible item for pack={}, hence ignoring it.", container.getSsccNumber());
      return null;
    }
    container.setContainerItems(containerItems);

    return container;
  }

  private void populateDepartmentCategory(
      ContainerItem containerItem, Map<Long, ItemDetails> itemMap) {
    if (Objects.nonNull(itemMap.get(containerItem.getItemNumber()))) {
      ItemDetails itemDetails = itemMap.get(containerItem.getItemNumber());
      containerItem.setDeptCatNbr(
          Objects.nonNull(itemDetails.getItemAdditonalInformation())
                  && itemDetails.getItemAdditonalInformation().containsKey(DEPT_CATEGORY_NBR)
              ? (Integer) itemDetails.getItemAdditonalInformation().get(DEPT_CATEGORY_NBR)
              : null);
    }
  }

  private void populateDeptSubCategory(
      ContainerItem containerItem, Map<Long, ItemDetails> itemMap) {
    if (Objects.nonNull(itemMap.get(containerItem.getItemNumber()))) {
      ItemDetails itemDetails = itemMap.get(containerItem.getItemNumber());
      containerItem.setDeptSubcatgNbr(
          Objects.nonNull(itemDetails.getItemAdditonalInformation())
                  && itemDetails.getItemAdditonalInformation().containsKey(DEPT_SUB_CATEGORY_NBR)
              ? (String) itemDetails.getItemAdditonalInformation().get(DEPT_SUB_CATEGORY_NBR)
              : null);
    }
  }

  public List<ContainerItem> createChildItems(ContainerItem containerItem, List<Item> childItems) {
    List<ContainerItem> childContainerItemList = new ArrayList<>();
    String packNumber = containerItem.getContainerItemMiscInfo().get(PACK_NUMBER);
    String eventType = containerItem.getContainerItemMiscInfo().get(EVENT_TYPE);
    String parentGtin = containerItem.getGtin();
    childItems.forEach(
        childItem -> {
          ContainerItem childContainerItem =
              gson.fromJson(gson.toJson(containerItem), ContainerItem.class);
          childContainerItem.setItemNumber(childItem.getItemNumber());
          childContainerItem.setGtin(childItem.getGtin());
          childContainerItem.setItemUPC(childItem.getGtin());
          // setting parent gtin as case upc to handle pdq scenario
          childContainerItem.setCaseUPC(parentGtin);
          childContainerItem.setPurchaseReferenceLineNumber(
              childItem.getInvoice().getInvoiceLineNumber());
          childContainerItem.setQuantity(
              childItem.getInventoryDetail().getDerivedQuantity().intValue());
          childContainerItem.setQuantityUOM(childItem.getInventoryDetail().getDerivedUom());
          childContainerItem.setInvoiceNumber(childItem.getInvoice().getInvoiceNumber());
          childContainerItem.setInvoiceLineNumber(childItem.getInvoice().getInvoiceLineNumber());
          childContainerItem.setCid(childItem.getReplenishmentGroupNumber());
          Map<String, String> containerItemMisc = new HashMap<>();
          containerItemMisc.put(PACK_NUMBER, packNumber);
          containerItemMisc.put(EVENT_TYPE, eventType);
          childContainerItem.setContainerItemMiscInfo(containerItemMisc);
          childContainerItemList.add(childContainerItem);
        });
    return childContainerItemList;
  }

  private void populateOrDefaultWareHouseAreaCode(
      ContainerItem containerItem, Map<Long, ItemDetails> itemMap) {

    String wareHouseAreaCode =
        Util.retrieveWarehouseAreaCode(itemMap.get(containerItem.getItemNumber()));
    containerItem.setWarehouseAreaCode(wareHouseAreaCode);
  }

  private Container retrieveContainer(ASNDocument asnDocument, Pack pack) {

    try {

      String ssccNumber =
          Objects.isNull(pack.getPalletNumber()) ? pack.getPackNumber() : pack.getPalletNumber();
      LOGGER.info("Validate Container Creation against a case with number={}", ssccNumber);
      return containerPersisterService.findBySSCCAndDeliveryNumber(
          ssccNumber, asnDocument.getDelivery().getDeliveryNumber());
    } catch (Exception exception) {
      LOGGER.error("No Container found and hence, going for container creation flow ", exception);
      return null;
    }
  }

  public String generateTCL(LabelType type) {
    ReceivingCounter receivingCounter = receivingCounterService.counterUpdation(1, type);

    long endIndex = receivingCounter.getCounterNumber() % 100000000;
    Set<String> tcls = new HashSet<>();
    for (long tclNumber = (endIndex - 1 + 1); tclNumber <= endIndex; tclNumber++) {
      tcls.add(receivingCounter.getPrefix() + prependZero(String.valueOf(tclNumber), 8));
    }
    return tcls.stream().findFirst().get();
  }

  private String prependZero(String number, int i) {
    if (number.length() == i) {
      return number;
    }
    while (i - number.length() > 0) {
      number = "0" + number;
    }
    return number;
  }

  private List<ASNDocument> getEligibleASNDocuments(Long deliveryNumber) {
    List<ASNDocument> asnDocuments = deliveryService.getAsnDocuments(deliveryNumber);
    return asnDocuments
        .stream()
        .filter(
            asnDocument ->
                sibManagedConfig
                    .getEligibleDeliverySourceTypeForCaseCreation()
                    .contains(asnDocument.getShipment().getSource().getType()))
        .collect(Collectors.toList());
  }
}
