package com.walmart.move.nim.receiving.endgame.common;

import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.ItemProperties.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.model.AssortmentShipper;
import com.walmart.move.nim.receiving.endgame.model.Attributes;
import com.walmart.move.nim.receiving.endgame.model.DivertDestinationFromSlotting;
import com.walmart.move.nim.receiving.endgame.model.DivertDestinationToHawkeye;
import com.walmart.move.nim.receiving.endgame.model.DivertRequestItem;
import com.walmart.move.nim.receiving.endgame.model.EndGameSlottingData;
import com.walmart.move.nim.receiving.endgame.model.SlottingDivertRequest;
import com.walmart.move.nim.receiving.endgame.model.UpdateAttributes;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/** Provides static utility methods for manipulating slotting related model data */
public class SlottingUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(SlottingUtils.class);

  private SlottingUtils() {}

  /**
   * Generate upc-divertRequest map.
   *
   * @param purchaseOrderList
   * @param processedItemMap
   * @param itemNumbers
   * @param ftsPath
   * @param assortmentPath
   * @return
   */
  //  TODO: Reduce parameters by creating a POJO, maybe
  public static Map<String, DivertRequestItem> generateUPCDivertRequestMap(
      List<PurchaseOrder> purchaseOrderList,
      Map<String, Map<String, Object>> processedItemMap,
      Set<Long> itemNumbers,
      String ftsPath,
      String assortmentPath,
      Map<Long, UpdateAttributes> updateAttributesMap) {
    Map<String, DivertRequestItem> upcDivertRequestMap = new HashMap<>();
    purchaseOrderList.forEach(
        purchaseOrder ->
            purchaseOrder
                .getLines()
                .forEach(
                    line -> {
                      if (itemNumbers.contains(line.getItemDetails().getNumber())) {
                        DivertRequestItem divertRequestItem;
                        String key =
                            new StringBuilder(line.getItemDetails().getOrderableGTIN())
                                .append(EndgameConstants.DELIM_DASH)
                                .append(purchaseOrder.getSellerId())
                                .toString();
                        if (isNull(upcDivertRequestMap.get(key))) {
                          UpdateAttributes updateAttributes =
                              updateAttributesMap.get(line.getItemDetails().getNumber());
                          updateAttributes =
                              isNull(updateAttributes) ? new UpdateAttributes() : updateAttributes;
                          divertRequestItem =
                              DivertRequestItem.builder()
                                  .itemNbr(line.getItemDetails().getNumber())
                                  .sellerId(purchaseOrder.getSellerId())
                                  .baseDivisionCode(purchaseOrder.getBaseDivisionCode())
                                  .totalOpenQty(line.getOpenQuantity())
                                  .totalOrderQty(line.getOrdered().getQuantity())
                                  .maxReceiveQty(
                                      ReceivingUtils.conversionToVendorPack(
                                          line.getOrdered().getQuantity()
                                              + line.getOvgThresholdLimit().getQuantity(),
                                          line.getOrdered().getUom(),
                                          line.getVnpk().getQuantity(),
                                          line.getWhpk().getQuantity()))
                                  .qtyUom(ReceivingConstants.Uom.VNPK)
                                  .itemDetails(
                                      processedItemMap.get(
                                          String.valueOf(line.getItemDetails().getNumber())))
                                  .caseUPC(line.getItemDetails().getOrderableGTIN())
                                  .itemUPC(line.getItemDetails().getConsumableGTIN())
                                  .possibleUPCs(
                                      getAllPossibleUPC(
                                          processedItemMap.get(
                                              String.valueOf(line.getItemDetails().getNumber())),
                                          line.getItemDetails().getConsumableGTIN(),
                                          assortmentPath))
                                  .isConveyable(line.getItemDetails().getConveyable())
                                  .isHazmat(line.getItemDetails().getHazmat())
                                  .isFTS(
                                      EndGameUtils.retriveIsFTS(
                                          line.getItemDetails().getNumber(),
                                          processedItemMap.get(
                                              String.valueOf(line.getItemDetails().getNumber())),
                                          ftsPath))
                                  .isRotateDateCaptured(
                                      !ObjectUtils.isEmpty(updateAttributes.getRotateDate()))
                                  .isRotateDateExpired(updateAttributes.isExpired())
                                  .isAuditEnabled(updateAttributes.getAuditEnabled())
                                  .build();
                        } else {
                          divertRequestItem = upcDivertRequestMap.get(key);
                          divertRequestItem.setTotalOpenQty(
                              divertRequestItem.getTotalOpenQty() + line.getOpenQuantity());
                          divertRequestItem.setTotalOrderQty(
                              divertRequestItem.getTotalOrderQty()
                                  + ReceivingUtils.conversionToVendorPack(
                                      line.getOrdered().getQuantity(),
                                      line.getOrdered().getUom(),
                                      line.getVnpk().getQuantity(),
                                      line.getWhpk().getQuantity()));
                          divertRequestItem.setMaxReceiveQty(
                              divertRequestItem.getMaxReceiveQty()
                                  + ReceivingUtils.conversionToVendorPack(
                                      line.getOrdered().getQuantity()
                                          + line.getOvgThresholdLimit().getQuantity(),
                                      line.getOrdered().getUom(),
                                      line.getVnpk().getQuantity(),
                                      line.getWhpk().getQuantity()));
                        }
                        upcDivertRequestMap.put(key, divertRequestItem);
                      }
                    }));
    return upcDivertRequestMap;
  }

  public static List<String> getAllPossibleUPC(
      Map<String, Object> mdmResponse, String consumableGTIN, String assortmentPath) {

    List<String> possibleUPCs = new ArrayList<>();
    possibleUPCs.add(addPrefixAndSuffix(consumableGTIN));
    List<AssortmentShipper> assortmentShippers =
        EndGameUtils.retriveAssortmentShipper(mdmResponse, assortmentPath);

    for (AssortmentShipper assortmentShipper : assortmentShippers) {
      possibleUPCs.add(
          addPrefixAndSuffix(
              assortmentShipper.getRelatedItem().getAttributes().getConsumableGtin()));
      possibleUPCs.add(
          addPrefixAndSuffix(
              assortmentShipper.getRelatedItem().getAttributes().getOrderablePackGtin()));
      possibleUPCs.add(
          addPrefixAndSuffix(
              assortmentShipper.getRelatedItem().getAttributes().getWarehousePackGtin()));
    }

    return possibleUPCs;
  }

  private static String addPrefixAndSuffix(String gtin) {
    return EndgameConstants.AT + gtin + EndgameConstants.AT;
  }

  /**
   * Populate slotting divert request by iterating over gdm delivery document.
   *
   * @param upcDivertRequestMap the upc consolidated item map
   * @return the slotting divert request
   */
  public static SlottingDivertRequest populateSlottingDivertRequest(
      Map<String, DivertRequestItem> upcDivertRequestMap) {
    SlottingDivertRequest slottingDivertRequest = new SlottingDivertRequest();
    slottingDivertRequest.setMessageId(UUID.randomUUID().toString());
    slottingDivertRequest.setDivertRequestItems(new ArrayList<>(upcDivertRequestMap.values()));
    return slottingDivertRequest;
  }

  /**
   * Populate endgame slotting data to be sent over Kafka using slotting response and manipulated
   * GDM data.
   *
   * @param divertDestinationsFromSlotting
   * @param deliveryNumber the delivery number
   * @param doorNumber the door number
   * @param upcConsolidatedItemMap the upc consolidated item map
   * @return the endgame slotting data
   */
  public static EndGameSlottingData populateEndgameSlottingData(
      List<DivertDestinationFromSlotting> divertDestinationsFromSlotting,
      Long deliveryNumber,
      String doorNumber,
      Map<String, DivertRequestItem> upcConsolidatedItemMap) {
    EndGameSlottingData endgameSlottingData = new EndGameSlottingData();
    endgameSlottingData.setDeliveryNumber(deliveryNumber);
    endgameSlottingData.setDoorNumber(doorNumber);
    List<DivertDestinationToHawkeye> destinationToHawkeyeList = new ArrayList<>();
    divertDestinationsFromSlotting.forEach(
        destinationFromSlotting -> {
          String key =
              new StringBuilder(destinationFromSlotting.getCaseUPC())
                  .append(EndgameConstants.DELIM_DASH)
                  .append(destinationFromSlotting.getSellerId())
                  .toString();
          DivertRequestItem divertRequestItem = upcConsolidatedItemMap.get(key);
          DivertDestinationToHawkeye destinationToHawkeye =
              DivertDestinationToHawkeye.builder()
                  .caseUPC(destinationFromSlotting.getCaseUPC())
                  .destination(destinationFromSlotting.getDivertLocation())
                  .possibleUPCs(divertRequestItem.getPossibleUPCs())
                  .maxCaseQty(divertRequestItem.getMaxReceiveQty())
                  .sellerId(divertRequestItem.getSellerId())
                  .build();

          // Overriding the FTS attributes from Slotting . If it is present
          Map<String, String> slottingAttributes = destinationFromSlotting.getAttributes();
          Map<String, Object> itemDetails = divertRequestItem.getItemDetails();
          String itemTag = null;
          if (!CollectionUtils.isEmpty(slottingAttributes)) {
            if (slottingAttributes.containsKey(EndgameConstants.ATTRIBUTES_FTS)
                && !ObjectUtils.isEmpty(slottingAttributes.get(EndgameConstants.ATTRIBUTES_FTS))) {
              String slottingFTSAttributes =
                  slottingAttributes.get(EndgameConstants.ATTRIBUTES_FTS);
              divertRequestItem.setIsFTS(Boolean.valueOf(slottingFTSAttributes));
            }
            if (slottingAttributes.containsKey(ReceivingConstants.ITEM_TAG)
                && !ObjectUtils.isEmpty(slottingAttributes.get(EndgameConstants.ITEM_TAG))) {
              itemTag = slottingAttributes.get(EndgameConstants.ITEM_TAG);
            }
          }
          Attributes attributes =
              Attributes.builder()
                  .totable(isItemTotable(divertRequestItem.getItemDetails()))
                  .isFHSExceeded(isFHSExceeded(divertRequestItem.getItemDetails()))
                  .isConsumable(divertRequestItem.getIsConveyable())
                  .isHazmat(divertRequestItem.getIsHazmat())
                  .isFTS(divertRequestItem.getIsFTS())
                  .itemTag(itemTag)
                  .itemPrep(EndGameUtils.getInboundPrepType(itemDetails))
                  .build();
          destinationToHawkeye.setAttributes(attributes);
          destinationToHawkeyeList.add(destinationToHawkeye);
        });
    endgameSlottingData.setDestinations(destinationToHawkeyeList);
    return endgameSlottingData;
  }

  private static Boolean isItemTotable(Map<String, Object> itemDetails) {
    Map<String, Object> dcProperties =
        ReceivingUtils.convertValue(
            itemDetails.get(EndgameConstants.DC_PROPERTIES),
            new TypeReference<Map<String, Object>>() {});
    if (nonNull(dcProperties)) {
      Map<String, Object> fcAttributes =
          ReceivingUtils.convertValue(
              dcProperties.get(FC_ATTRIBUTES), new TypeReference<Map<String, Object>>() {});
      if (nonNull(fcAttributes) && nonNull(fcAttributes.get(TOTABLE))) {
        return Boolean.valueOf(String.valueOf(fcAttributes.get(TOTABLE)));
      }
    }
    return null;
  }

  private static Boolean isFHSExceeded(Map<String, Object> itemDetails) {
    List<String> fhsCodes = new ArrayList<>(Arrays.asList("2", "3", "4", "5", "6", "7", "8"));
    Map<String, Object> gtinHazmat =
        ReceivingUtils.convertValue(
            itemDetails.get(GTIN_HAZMAT), new TypeReference<Map<String, Object>>() {});
    if (nonNull(gtinHazmat)) {
      Map<String, Object> slotting =
          ReceivingUtils.convertValue(
              gtinHazmat.get(SLOTTING), new TypeReference<Map<String, Object>>() {});
      if (nonNull(slotting) && nonNull(slotting.get(CODE))) {
        return fhsCodes.contains(String.valueOf(slotting.get(CODE)));
      }
    }
    return null;
  }

  /**
   * Populate slotting entity from destination in slotting response.
   *
   * @param divertDestination the destination
   * @return the slotting destination
   */
  public static SlottingDestination populateSlottingEntity(
      DivertDestinationToHawkeye divertDestination) {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC(divertDestination.getCaseUPC());
    slottingDestination.setPossibleUPCs(String.join(",", divertDestination.getPossibleUPCs()));
    slottingDestination.setDestination(divertDestination.getDestination());
    slottingDestination.setSellerId(divertDestination.getSellerId());
    return slottingDestination;
  }

  /**
   * Populate endgame slotting data to be sent for an update to Hawkeye using data from our DB.
   *
   * @param slottingDestination
   * @param attributes
   * @return the end game slotting data
   */
  public static EndGameSlottingData populateEndgameSlottingData(
      SlottingDestination slottingDestination, Attributes attributes) {
    EndGameSlottingData endgameSlottingData = new EndGameSlottingData();
    DivertDestinationToHawkeye divert =
        DivertDestinationToHawkeye.builder()
            .caseUPC(slottingDestination.getCaseUPC())
            .sellerId(slottingDestination.getSellerId())
            .destination(slottingDestination.getDestination())
            .attributes(attributes)
            .build();
    endgameSlottingData.setDestinations(Collections.singletonList(divert));
    return endgameSlottingData;
  }

  public static Attributes populateAttributes(
      Map<String, String> attributeMap, Map<String, Object> itemDetails) {
    if (isNull(attributeMap)) {
      return null;
    }
    Boolean isFTS =
        isNull(attributeMap.get(EndgameConstants.ATTRIBUTES_FTS))
            ? Boolean.FALSE
            : Boolean.valueOf(attributeMap.get(EndgameConstants.ATTRIBUTES_FTS));

    Attributes attributes =
        Attributes.builder()
            .isConsumable(null)
            .isFTS(isFTS)
            .totable(isItemTotable(itemDetails))
            .isFHSExceeded(isFHSExceeded(itemDetails))
            .isHazmat(null)
            .itemTag(attributeMap.get(ReceivingConstants.ITEM_TAG))
            .itemPrep(EndGameUtils.getInboundPrepType(itemDetails))
            .build();

    LOGGER.info("Divert Attributes to be sent to hawkeye [attributes={}]", attributes);
    return attributes;
  }
}
