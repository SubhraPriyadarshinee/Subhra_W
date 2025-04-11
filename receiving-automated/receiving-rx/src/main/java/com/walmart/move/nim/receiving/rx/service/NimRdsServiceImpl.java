package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.SlotDetails;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class NimRdsServiceImpl {

  @Autowired private NimRDSRestApiClient nimRDSRestApiClient;

  private ReceiveContainersRequestBody getReceiveContainersRequestBody4FullCase(
      Instruction instruction,
      CompleteInstructionRequest completeInstructionRequest,
      String userId) {

    SlotDetails slotDetails = completeInstructionRequest.getSlotDetails();
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();

    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setId(instruction.getContainer().getTrackingId());
    containerOrder.setPoNumber(instruction.getPurchaseReferenceNumber());
    containerOrder.setPoLine(instruction.getPurchaseReferenceLineNumber());
    containerOrder.setManifest(instruction.getDeliveryNumber().toString());
    containerOrder.setDoorNum(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    containerOrder.setUserId(userId);
    containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.VNPK);
    containerOrder.setContainerGroupId(instruction.getContainer().getTrackingId());

    if (!RxUtils.isUpcReceivingInstruction(instruction.getInstructionCode())) {
      Map<String, Integer> lotNumberQtyMap = new HashMap<>();
      instruction
          .getChildContainers()
          .forEach(
              container -> {
                Content childContainerContent = container.getContents().get(0);
                String lotNumber = StringUtils.upperCase(childContainerContent.getLot());
                int lotQty = lotNumberQtyMap.getOrDefault(lotNumber, 0);
                lotQty += childContainerContent.getQty();

                lotNumberQtyMap.put(lotNumber, lotQty);
              });

      Content containerContent = instruction.getContainer().getContents().get(0);
      int vnpkQty = containerContent.getVendorPack();
      int whpkQty = containerContent.getWarehousePack();

      List<LotDetails> lotDetailsList = new ArrayList<>(lotNumberQtyMap.size());
      int calculatedTotalReceivedQtyInVnpk = 0;
      for (Entry<String, Integer> lotNumberQtyMapEntry : lotNumberQtyMap.entrySet()) {
        LotDetails lotDetails = new LotDetails();
        lotDetails.setLotNumber(lotNumberQtyMapEntry.getKey());
        Double convertedVendorPacks =
            conversionToVendorPack(
                lotNumberQtyMapEntry.getValue(), containerContent.getQtyUom(), vnpkQty, whpkQty);
        if (!isWhole(convertedVendorPacks)) {
          // Using Partial Case request body & slotToPrime = true, if the lot quantity is not
          // summing up to a vendor pack
          return getReceiveContainersRequestBody4PartialCase(
              instruction, completeInstructionRequest, userId);
        }
        lotDetails.setQty(convertedVendorPacks.intValue());
        lotDetailsList.add(lotDetails);

        calculatedTotalReceivedQtyInVnpk += convertedVendorPacks;
      }

      containerOrder.setLotNumbers(lotDetailsList);
      containerOrder.setQty(calculatedTotalReceivedQtyInVnpk);
    } else {
      List<LotDetails> lotDetailsList = new ArrayList<>();
      LotDetails lotDetails = new LotDetails();
      lotDetails.setLotNumber(RxConstants.DEFAULT_RDS_LOT);
      Content containerContent = instruction.getContainer().getContents().get(0);
      int casesReceived =
          ReceivingUtils.conversionToVendorPackRoundUp(
              instruction.getReceivedQuantity(),
              instruction.getReceivedQuantityUOM(),
              containerContent.getVendorPack(),
              containerContent.getWarehousePack());
      lotDetails.setQty(casesReceived);
      lotDetailsList.add(lotDetails);

      containerOrder.setLotNumbers(lotDetailsList);
      containerOrder.setQty(casesReceived);
    }

    if (Objects.nonNull(slotDetails)) {
      if (StringUtils.isNotBlank(slotDetails.getSlot())) {
        // Manual Sloting
        SlottingOverride slottingOverride = new SlottingOverride();
        slottingOverride.setSlottingType(RxConstants.RDS_SLOTTING_TYPE_MANUAL);
        slottingOverride.setSlotSize(slotDetails.getSlotSize());
        slottingOverride.setSlot(slotDetails.getSlot());
        slottingOverride.setSlotRangeEnd(slotDetails.getSlotRange());
        slottingOverride.setXrefDoor(containerOrder.getDoorNum());

        containerOrder.setSlottingOverride(slottingOverride);
      } else {
        // Auto Sloting
        containerOrder.setSstkSlotSize(slotDetails.getSlotSize());
      }
    }
    receiveContainersRequestBody.setContainerOrders(Arrays.asList(containerOrder));

    return receiveContainersRequestBody;
  }

  private ReceiveContainersRequestBody getReceiveContainersRequestBody4PartialCase(
      Instruction instruction,
      CompleteInstructionRequest completeInstructionRequest,
      String userId) {
    SlotDetails slotDetails = completeInstructionRequest.getSlotDetails();
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();

    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setId(instruction.getContainer().getTrackingId());
    containerOrder.setPoNumber(instruction.getPurchaseReferenceNumber());
    containerOrder.setPoLine(instruction.getPurchaseReferenceLineNumber());
    containerOrder.setManifest(instruction.getDeliveryNumber().toString());
    containerOrder.setDoorNum(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    containerOrder.setUserId(userId);
    containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.WHPK);
    containerOrder.setContainerGroupId(instruction.getContainer().getTrackingId());
    containerOrder.setSlotToPrime(true);
    containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.WHPK);

    if (!RxUtils.isUpcReceivingInstruction(instruction.getInstructionCode())) {
      Map<String, Integer> lotNumberQtyMap = new HashMap<>();
      instruction
          .getChildContainers()
          .forEach(
              container -> {
                Content childContainerContent = container.getContents().get(0);
                String lotNumber = StringUtils.upperCase(childContainerContent.getLot());
                int lotQty = lotNumberQtyMap.getOrDefault(lotNumber, 0);
                lotQty += childContainerContent.getQty();

                lotNumberQtyMap.put(lotNumber, lotQty);
              });

      Content containerContent = instruction.getContainer().getContents().get(0);
      int vnpkQty = containerContent.getVendorPack();
      int whpkQty = containerContent.getWarehousePack();

      List<LotDetails> lotDetailsList = new ArrayList<>(lotNumberQtyMap.size());
      int calculatedTotalReceivedQtyInWhpk = 0;
      for (Entry<String, Integer> lotNumberQtyMapEntry : lotNumberQtyMap.entrySet()) {
        LotDetails lotDetails = new LotDetails();
        lotDetails.setLotNumber(lotNumberQtyMapEntry.getKey());
        Integer convertedWareHousePacks =
            ReceivingUtils.conversionToWareHousePack(
                lotNumberQtyMapEntry.getValue(), containerContent.getQtyUom(), vnpkQty, whpkQty);
        lotDetails.setQty(convertedWareHousePacks);

        lotDetailsList.add(lotDetails);
        calculatedTotalReceivedQtyInWhpk += convertedWareHousePacks;
      }
      containerOrder.setLotNumbers(lotDetailsList);
      containerOrder.setQty(calculatedTotalReceivedQtyInWhpk);

    } else {
      List<LotDetails> lotDetailsList = new ArrayList<>();
      LotDetails lotDetails = new LotDetails();
      lotDetails.setLotNumber(RxConstants.DEFAULT_RDS_LOT);
      Content containerContent = instruction.getContainer().getContents().get(0);
      int calculatedTotalReceivedQtyInWhpk =
          ReceivingUtils.conversionToWareHousePack(
              instruction.getReceivedQuantity(),
              instruction.getReceivedQuantityUOM(),
              containerContent.getVendorPack(),
              containerContent.getWarehousePack());
      lotDetails.setQty(calculatedTotalReceivedQtyInWhpk);
      lotDetailsList.add(lotDetails);

      containerOrder.setLotNumbers(lotDetailsList);
      containerOrder.setQty(calculatedTotalReceivedQtyInWhpk);
    }

    if (Objects.nonNull(slotDetails)) {
      if (StringUtils.isNotBlank(slotDetails.getSlot())) {
        // Manual Sloting
        SlottingOverride slottingOverride = new SlottingOverride();
        slottingOverride.setSlottingType(RxConstants.RDS_SLOTTING_TYPE_MANUAL);
        slottingOverride.setSlotSize(slotDetails.getSlotSize());
        slottingOverride.setSlot(slotDetails.getSlot());
        slottingOverride.setSlotRangeEnd(slotDetails.getSlotRange());
        slottingOverride.setXrefDoor(containerOrder.getDoorNum());

        containerOrder.setSlottingOverride(slottingOverride);
      } else {
        // Auto Sloting
        containerOrder.setSstkSlotSize(slotDetails.getSlotSize());
        SlottingOverride slottingOverride = new SlottingOverride();
        slottingOverride.setSlottingType(RxConstants.RDS_SLOTTING_TYPE_MANUAL);
        slottingOverride.setSlotSize(slotDetails.getSlotSize());
        slottingOverride.setSlot(RxConstants.DEFAULT_SLOT);

        containerOrder.setSlottingOverride(slottingOverride);
      }
    }
    receiveContainersRequestBody.setContainerOrders(Arrays.asList(containerOrder));

    return receiveContainersRequestBody;
  }

  public ReceiveContainersResponseBody acquireSlot(
      Instruction instruction,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders) {
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    ReceiveContainersRequestBody receiveContainersRequestBody;
    if (completeInstructionRequest.isPartialContainer()) {
      receiveContainersRequestBody =
          getReceiveContainersRequestBody4PartialCase(
              instruction, completeInstructionRequest, userId);
    } else {
      receiveContainersRequestBody =
          getReceiveContainersRequestBody4FullCase(instruction, completeInstructionRequest, userId);
    }
    return nimRDSRestApiClient.receiveContainers(receiveContainersRequestBody, httpHeadersMap);
  }

  public void quantityChange(Integer adjustedQty, String scanTag, HttpHeaders httpHeaders) {

    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    QuantityChangeRequestBody quantityChangeRequestBody = new QuantityChangeRequestBody();
    quantityChangeRequestBody.setQuantity(adjustedQty);
    quantityChangeRequestBody.setScanTag(scanTag);
    quantityChangeRequestBody.setUserId(userId);

    nimRDSRestApiClient.quantityChange(quantityChangeRequestBody, httpHeadersMap);
  }

  private Double conversionToVendorPack(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    Double quantityInDouble = Double.valueOf(quantity);
    Double vnpkQtyInDouble = Double.valueOf(vnpkQty);
    Double whpkQtyInDouble = Double.valueOf(whpkQty);
    Double vendorPackQtyInDouble;
    switch (uom) {
      case ReceivingConstants.Uom.EACHES:
        vendorPackQtyInDouble = quantityInDouble / vnpkQtyInDouble;
        break;
      case ReceivingConstants.Uom.WHPK:
        vendorPackQtyInDouble = (quantityInDouble * whpkQtyInDouble) / vnpkQtyInDouble;
        break;
      default:
        vendorPackQtyInDouble = quantityInDouble;
    }
    return vendorPackQtyInDouble;
  }

  private boolean isWhole(Double value) {
    return (value % 1 == 0);
  }

  public ReceiveContainersResponseBody acquireSlotForSplitPallet(
      List<Instruction> instructions, SlotDetails slotDetails, HttpHeaders httpHeaders) {

    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrderList = new ArrayList<>();
    for (Instruction instruction : instructions) {

      ContainerOrder containerOrder = new ContainerOrder();
      containerOrder.setId(instruction.getContainer().getTrackingId());
      containerOrder.setPoNumber(instruction.getPurchaseReferenceNumber());
      containerOrder.setPoLine(instruction.getPurchaseReferenceLineNumber());
      containerOrder.setManifest(instruction.getDeliveryNumber().toString());
      containerOrder.setDoorNum(
          instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
      containerOrder.setUserId(userId);
      containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.VNPK);
      containerOrder.setContainerGroupId(String.valueOf(instruction.getInstructionSetId()));

      if (!RxUtils.isUpcReceivingInstruction(instruction.getInstructionCode())) {
        Map<String, Integer> lotNumberQtyMap = new HashMap<>();
        instruction
            .getChildContainers()
            .forEach(
                container -> {
                  Content childContainerContent = container.getContents().get(0);
                  String lotNumber = StringUtils.upperCase(childContainerContent.getLot());
                  int lotQty = lotNumberQtyMap.getOrDefault(lotNumber, 0);
                  lotQty += childContainerContent.getQty();

                  lotNumberQtyMap.put(lotNumber, lotQty);
                });

        Content containerContent = instruction.getContainer().getContents().get(0);
        List<LotDetails> lotDetailsList = new ArrayList<>(lotNumberQtyMap.size());
        int calculatedTotalReceivedQtyInVnpk = 0;
        for (Entry<String, Integer> lotNumberQtyMapEntry : lotNumberQtyMap.entrySet()) {
          LotDetails lotDetails = new LotDetails();
          lotDetails.setLotNumber(lotNumberQtyMapEntry.getKey());
          Double convertedVendorPacks =
              conversionToVendorPack(
                  lotNumberQtyMapEntry.getValue(),
                  containerContent.getQtyUom(),
                  containerContent.getVendorPack(),
                  containerContent.getWarehousePack());
          lotDetails.setQty(convertedVendorPacks.intValue());
          lotDetailsList.add(lotDetails);

          calculatedTotalReceivedQtyInVnpk += convertedVendorPacks;
        }

        containerOrder.setLotNumbers(lotDetailsList);
        containerOrder.setQty(calculatedTotalReceivedQtyInVnpk);
      } else {
        List<LotDetails> lotDetailsList = new ArrayList<>();
        LotDetails lotDetails = new LotDetails();
        lotDetails.setLotNumber(RxConstants.DEFAULT_RDS_LOT);
        Content containerContent = instruction.getContainer().getContents().get(0);
        int casesReceived =
            ReceivingUtils.conversionToVendorPackRoundUp(
                instruction.getReceivedQuantity(),
                instruction.getReceivedQuantityUOM(),
                containerContent.getVendorPack(),
                containerContent.getWarehousePack());
        lotDetails.setQty(casesReceived);
        lotDetailsList.add(lotDetails);

        containerOrder.setLotNumbers(lotDetailsList);
        containerOrder.setQty(casesReceived);
      }

      // Manual Sloting
      SlottingOverride slottingOverride = new SlottingOverride();
      slottingOverride.setSlottingType(RxConstants.RDS_SLOTTING_TYPE_MANUAL);
      slottingOverride.setSlotSize(slotDetails.getSlotSize());
      slottingOverride.setSlot(slotDetails.getSlot());
      slottingOverride.setSlotRangeEnd(slotDetails.getSlotRange());
      slottingOverride.setXrefDoor(containerOrder.getDoorNum());

      containerOrder.setSlottingOverride(slottingOverride);
      containerOrderList.add(containerOrder);
    }
    receiveContainersRequestBody.setContainerOrders(containerOrderList);

    return nimRDSRestApiClient.receiveContainers(receiveContainersRequestBody, httpHeadersMap);
  }
}
