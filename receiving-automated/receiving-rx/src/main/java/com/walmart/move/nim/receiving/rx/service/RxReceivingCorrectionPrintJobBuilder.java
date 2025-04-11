package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class RxReceivingCorrectionPrintJobBuilder {

  @Autowired private Gson gson;

  public Map<String, Object> getPrintJobForReceivingCorrection(
      Integer adjustedQuantity, String adjustedQtyUOM, Instruction instruction4mDB) {

    Map<String, Object> ctrLabel = instruction4mDB.getContainer().getCtrLabel();
    JsonArray jsonPrintRequests =
        gson.toJsonTree(ctrLabel)
            .getAsJsonObject()
            .getAsJsonArray(RxConstants.PRINT_LABEL_PRINT_REQUESTS);
    if (Objects.nonNull(jsonPrintRequests) && jsonPrintRequests.size() > 0) {
      PrintLabelRequest printRequest =
          gson.fromJson(jsonPrintRequests.get(0).toString(), PrintLabelRequest.class);
      List<LabelData> labelDataList = printRequest.getData();
      List<LabelData> modifiedLabelData = new ArrayList<>();
      Iterator<LabelData> data = labelDataList.iterator();
      while (data.hasNext()) {
        LabelData label = data.next();
        if (RxConstants.QTY.equals(label.getKey())) {
          label.setValue(String.valueOf(adjustedQuantity));
        } else if (RxConstants.DATE.equals(label.getKey())) {
          label.setValue(new Date().toString());
        } else if (RxConstants.UOM.equals(label.getKey())) {
          label.setValue(adjustedQtyUOM);
        }
        modifiedLabelData.add(label);
      }
      printRequest.setData(modifiedLabelData);
      ctrLabel.put(RxConstants.PRINT_LABEL_PRINT_REQUESTS, Arrays.asList(printRequest));
      return ctrLabel;
    }

    return Collections.emptyMap();
  }
}
