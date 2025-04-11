package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.model.slotting.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;

public class MockSlottingUtils {

  public static SlottingPalletRequest getMockSlottingPalletRequestForMultipleItems() {
    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    slottingPalletRequest.setMessageId("messageId");
    slottingPalletRequest.setDoorId("100");
    slottingPalletRequest.setReceivingMethod(ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD);
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();
    SlottingContainerItemDetails slottingContainerItemDetails1 = new SlottingContainerItemDetails();
    slottingContainerItemDetails1.setItemNbr(4545452324L);
    slottingContainerItemDetailsList.add(slottingContainerItemDetails1);
    SlottingContainerItemDetails slottingContainerItemDetails2 = new SlottingContainerItemDetails();
    slottingContainerItemDetails1.setItemNbr(4545452325L);
    slottingContainerItemDetailsList.add(slottingContainerItemDetails2);
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);
    return slottingPalletRequest;
  }

  public static SlottingPalletResponse getPrimeSlotForMultiItemsFromSmartSlotting_PartialSuccess() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    slottingPalletResponse.setMessageId("3232323-2323-232323");
    List<SlottingDivertLocations> slottingDivertLocationsList = new ArrayList<>();
    SlottingDivertLocations slottingDivertLocations1 = new SlottingDivertLocations();
    slottingDivertLocations1.setLocation("A0002");
    slottingDivertLocations1.setType("success");
    slottingDivertLocations1.setLocationSize(72);
    slottingDivertLocations1.setSlotType("PRIME");
    slottingDivertLocations1.setItemNbr(4545452324L);
    slottingDivertLocations1.setAsrsAlignment("SYMBP");
    slottingDivertLocationsList.add(slottingDivertLocations1);
    SlottingDivertLocations slottingDivertLocations2 = new SlottingDivertLocations();
    slottingDivertLocations2.setType("error");
    slottingDivertLocations2.setCode("GLS-SMART-SLOTING-4040009");
    slottingDivertLocations2.setDesc(
        "Setup issue - Prime Slot is not present for the entered item");
    slottingDivertLocations2.setItemNbr(4545452325L);
    slottingDivertLocationsList.add(slottingDivertLocations2);
    slottingPalletResponse.setLocations(slottingDivertLocationsList);
    return slottingPalletResponse;
  }

  public static SlottingPalletResponse getPrimeSlotForMultiItemsFromSmartSlotting_Error() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    slottingPalletResponse.setMessageId("3232323-2323-232323");
    List<SlottingDivertLocations> slottingDivertLocationsList = new ArrayList<>();
    SlottingDivertLocations slottingDivertLocations1 = new SlottingDivertLocations();
    slottingDivertLocations1.setType("error");
    slottingDivertLocations1.setCode("GLS-SMART-SLOTING-4040009");
    slottingDivertLocations1.setDesc(
        "Setup issue - Prime Slot is not present for the entered item");
    slottingDivertLocations1.setItemNbr(4545452324L);
    slottingDivertLocationsList.add(slottingDivertLocations1);
    SlottingDivertLocations slottingDivertLocations2 = new SlottingDivertLocations();
    slottingDivertLocations2.setType("error");
    slottingDivertLocations2.setCode("GLS-SMART-SLOTING-4040009");
    slottingDivertLocations2.setDesc(
        "Setup issue - Prime Slot is not present for the entered item");
    slottingDivertLocations2.setItemNbr(4545452325L);
    slottingDivertLocationsList.add(slottingDivertLocations2);
    slottingPalletResponse.setLocations(slottingDivertLocationsList);
    return slottingPalletResponse;
  }

  public static SlottingPalletResponse getPrimeSlotForMultiItemsFromSmartSlotting() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    slottingPalletResponse.setMessageId("3232323-2323-232323");
    List<SlottingDivertLocations> slottingDivertLocationsList = new ArrayList<>();
    SlottingDivertLocations slottingDivertLocations1 = new SlottingDivertLocations();
    slottingDivertLocations1.setLocation("A0002");
    slottingDivertLocations1.setType("success");
    slottingDivertLocations1.setLocationSize(72);
    slottingDivertLocations1.setSlotType("PRIME");
    slottingDivertLocations1.setAsrsAlignment("SYMBP");
    slottingDivertLocations1.setItemNbr(4545452324L);
    slottingDivertLocationsList.add(slottingDivertLocations1);
    SlottingDivertLocations slottingDivertLocations2 = new SlottingDivertLocations();
    slottingDivertLocations2.setLocation("A0002");
    slottingDivertLocations2.setType("success");
    slottingDivertLocations2.setLocationSize(72);
    slottingDivertLocations2.setSlotType("PRIME");
    slottingDivertLocations2.setItemNbr(4545452325L);
    slottingDivertLocations2.setAsrsAlignment("SYMBP");
    slottingDivertLocationsList.add(slottingDivertLocations2);
    slottingPalletResponse.setLocations(slottingDivertLocationsList);
    return slottingPalletResponse;
  }
}
