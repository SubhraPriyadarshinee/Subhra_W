package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class ImportSlottingServiceImpl {
  @Autowired private SlottingRestApiClient slottingRestApiClient;

  @Counted(
      name = "getPrimeSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlot")
  @Timed(
      name = "getPrimeSlotTimed",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlot")
  @ExceptionCounted(
      name = "getPrimeSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlot")
  public SlottingPalletResponse getPrimeSlot(
      String messageId, List<Long> itemNumbers, int locationSize, HttpHeaders httpHeaders) {
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_PRIME_SLOT);
    SlottingPalletRequest slottingRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    SlottingContainerItemDetails slottingContainerItemDetails = new SlottingContainerItemDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();
    slottingRequest.setMessageId(messageId);
    for (Long itemNumber : itemNumbers) {
      slottingContainerItemDetails.setItemNbr(itemNumber);
      slottingContainerItemDetailsList.add(slottingContainerItemDetails);
    }
    if (locationSize > 0) {
      slottingContainerDetails.setLocationSize(locationSize);
    }
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingRequest.setContainerDetails(slottingContainerDetailsList);
    return slottingRestApiClient.getSlot(slottingRequest, httpHeaders);
  }
}
