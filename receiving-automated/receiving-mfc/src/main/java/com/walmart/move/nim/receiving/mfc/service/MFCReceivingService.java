package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.ENABLE_DECANT_RECEIPT;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class MFCReceivingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MFCReceivingService.class);

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private MFCReceiptService mfcReceiptService;

  @Autowired private ContainerItemRepository containerItemRepository;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * Perform receiving by finding containers for the request and creating receipts.
   *
   * @param receiptDTO the receipt dto
   * @return the list
   */
  public List<Receipt> performReceiving(CommonReceiptDTO receiptDTO) {

    Set<QuantityType> quantityTypes =
        receiptDTO
            .getQuantities()
            .stream()
            .map(quantity -> quantity.getType())
            .collect(Collectors.toSet());
    LOGGER.info("Quantity type for receivings = {} ", quantityTypes);

    boolean enableDecantingReceipt =
        tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_DECANT_RECEIPT);
    LOGGER.warn("{} is set as {}" + ENABLE_DECANT_RECEIPT, enableDecantingReceipt);

    if (enableDecantingReceipt
        && (quantityTypes.contains(QuantityType.DECANTED)
            || quantityTypes.contains(QuantityType.OVERAGE)
            || quantityTypes.contains(QuantityType.SHORTAGE))) {
      LOGGER.warn("Not processing the decanted event for request = {} ", receiptDTO);
      return null;
    }

    List<Container> containerList = mfcContainerService.detectContainers(receiptDTO);
    LOGGER.info(
        "Selected container for gtin={} and trackingId = {}  is containers = {} ",
        receiptDTO.getGtin(),
        receiptDTO.getContainerId(),
        containerList);

    List<Receipt> receiptList = new ArrayList<>();
    Set<ContainerItem> selectedContainerItemSet =
        updateContainersAndGetReceipts(receiptDTO, containerList, receiptList);
    containerItemRepository.saveAll(selectedContainerItemSet);
    LOGGER.info(
        "Receipt created for container for gtin={} and trackingId = {}  is  {} ",
        receiptDTO.getGtin(),
        receiptDTO.getContainerId(),
        receiptList);
    return mfcReceiptService.saveReceipt(receiptList);
  }

  /**
   * Fill Quantity in containerItems and add new Receipt in receiptList. ContainerList is a filtered
   * list for only the provided ContainerId or GTIN
   */
  private Set<ContainerItem> updateContainersAndGetReceipts(
      CommonReceiptDTO receiptDTO, List<Container> containerList, List<Receipt> receiptList) {
    Map<Integer, ContainerItem> containerItemsToUpdate = new HashMap<>();
    Container randomContainer = containerList.stream().findFirst().get();
    ContainerItem randomContainerItem =
        randomContainer.getContainerItems().stream().findFirst().get();
    receiptDTO
        .getQuantities()
        .stream()
        .filter(
            qty ->
                Arrays.asList(
                        QuantityType.DECANTED,
                        QuantityType.DAMAGE,
                        QuantityType.REJECTED,
                        QuantityType.COLD_CHAIN_REJECT,
                        QuantityType.NOTMFCASSORTMENT,
                        QuantityType.FRESHNESSEXPIRATION,
                        QuantityType.MFCOVERSIZE,
                        QuantityType.MFC_TO_STORE_TRANSFER,
                        QuantityType.WRONG_TEMP_ZONE,
                        QuantityType.NGR_SHORTAGE,
                        QuantityType.NGR_REJECT)
                    .contains(qty.getType()))
        .forEach(
            quantity -> {
              int qtyToFulfill =
                  ReceivingUtils.conversionToEaches(
                      quantity.getValue().intValue(),
                      quantity.getUom(),
                      randomContainerItem.getVnpkQty(),
                      randomContainerItem.getWhpkQty());
              String uom = randomContainerItem.getQuantityUOM();
              for (Container container : containerList) {
                if (qtyToFulfill <= 0) break;
                for (ContainerItem containerItem : container.getContainerItems()) {
                  if (qtyToFulfill <= 0) break;
                  long containerItemQty =
                      ReceivingUtils.conversionToEaches(
                          containerItem.getQuantity(),
                          containerItem.getQuantityUOM(),
                          containerItem.getVnpkQty(),
                          containerItem.getWhpkQty());
                  if (Objects.isNull(containerItem.getOrderFilledQty())) {
                    containerItem.setOrderFilledQty(0L);
                    containerItem.setOrderFilledQtyUom(uom);
                  }
                  long capacity = containerItemQty - containerItem.getOrderFilledQty();
                  if (capacity > 0) {
                    if (qtyToFulfill > capacity) {
                      receiptList.add(
                          updateContainerItemAndCreateReceipt(
                              container, containerItem, quantity, capacity, uom));
                    } else {
                      receiptList.add(
                          updateContainerItemAndCreateReceipt(
                              container, containerItem, quantity, qtyToFulfill, uom));
                    }
                    qtyToFulfill -= capacity;
                    containerItemsToUpdate.put(containerItem.getInvoiceLineNumber(), containerItem);
                    LOGGER.debug("Updating orderFilledQty in containerItem: {}", containerItem);
                  }
                }
              }
              if (qtyToFulfill > 0) {
                receiptList.add(
                    updateContainerItemAndCreateReceipt(
                        randomContainer, randomContainerItem, quantity, qtyToFulfill, uom));
                containerItemsToUpdate.put(
                    randomContainerItem.getInvoiceLineNumber(), randomContainerItem);
              }
            });
    return new HashSet<>(containerItemsToUpdate.values());
  }

  private Receipt updateContainerItemAndCreateReceipt(
      Container container,
      ContainerItem containerItem,
      Quantity quantity,
      long qty,
      String qtyUom) {
    MFCUtils.populateContainerItemOrderFilledQty(containerItem, quantity.getType(), qty, qtyUom);
    return MFCUtils.createContainerReceipt(container, containerItem, quantity, (int) qty, qtyUom);
  }
}
