package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PrintLabelHelper {

  public void updatePrintLabels(
      Instruction instruction, List<PrintLabelRequest> printLabelRequests) {
    for (PrintLabelRequest printLabelRequest : printLabelRequests) {
      Map<String, LabelData> labelDataMap =
          printLabelRequest
              .getData()
              .stream()
              .collect(Collectors.toMap(LabelData::getKey, Function.identity(), (o, n) -> n));

      // Add LabelData based on activityName
      addLabelDataBasedOnActivityName(instruction, labelDataMap);

      // Replace the data in printLabelRequest with the values in labelDataMap, sorted by key
      List<LabelData> sortedLabelDataList =
          labelDataMap
              .values()
              .stream()
              .sorted(Comparator.comparing(LabelData::getKey))
              .collect(Collectors.toList());
      printLabelRequest.setData(sortedLabelDataList);
    }
  }

  private void addLabelDataBasedOnActivityName(
      Instruction instruction, Map<String, LabelData> labelDataMap) {
    Predicate<Instruction> isNonNationalInstruction =
        i ->
            i.getActivityName().equalsIgnoreCase(PurchaseReferenceType.POCON.toString())
                || i.getActivityName().equalsIgnoreCase(PurchaseReferenceType.DSDC.toString());

    if (isNonNationalInstruction.test(instruction)) {
      // Add CHANNELMETHOD if not exists
      addLabelDataIfNotExists(labelDataMap, "CHANNELMETHOD", instruction.getOriginalChannel());
      addLabelDataIfNotExists(
          labelDataMap, "QTY", String.valueOf(instruction.getReceivedQuantity()));
    }

    // other conditions can be added here in the future
  }

  private void addLabelDataIfNotExists(
      Map<String, LabelData> labelDataMap, String key, String value) {
    if (!labelDataMap.containsKey(key)) {
      LabelData labelData = new LabelData();
      labelData.setKey(key);
      labelData.setValue(value);
      labelDataMap.put(key, labelData);
    }
  }
}
