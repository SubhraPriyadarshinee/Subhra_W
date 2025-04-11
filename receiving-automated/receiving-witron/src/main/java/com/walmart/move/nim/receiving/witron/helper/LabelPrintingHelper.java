package com.walmart.move.nim.receiving.witron.helper;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_LABEL_ROTATE_DATE_MM_DD_YYYY;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class LabelPrintingHelper {

  private static final Logger log = LoggerFactory.getLogger(LabelPrintingHelper.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private GDCFlagReader gdcFlagReader;

  public Map<String, Object> getLabelData(
      Instruction instruction,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders) {

    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(getFacilityNum());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String rotateDateAsString =
        nonNull(receiveInstructionRequest.getRotateDate())
            ? new SimpleDateFormat(PRINT_LABEL_ROTATE_DATE_MM_DD_YYYY)
                .format(receiveInstructionRequest.getRotateDate())
            : "-";

    return constructLabelDataV2(
        instruction, receiveInstructionRequest, userId, dcTimeZone, rotateDateAsString);
  }

  private Map<String, Object> constructLabelDataV2(
      Instruction instruction,
      ReceiveInstructionRequest receiveInstructionRequest,
      String userId,
      String dcTimeZone,
      String rotateDateAsString) {
    Map<String, Object> printJob = instruction.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");

    generateMap("rotateDate", rotateDateAsString, labelData);
    generateMap("printDate", ReceivingUtils.getDcDateTime(dcTimeZone), labelData);
    generateMap("userId", userId, labelData);
    generateMap("pltQty", receiveInstructionRequest.getQuantity(), labelData);
    generateMap("printerId", receiveInstructionRequest.getPrinterName(), labelData);

    printRequest.put("data", labelData);
    printRequests.set(0, printRequest);
    printJob.put("printRequests", printRequests);

    return printJob;
  }

  private void generateMap(String key, Object value, List<Map<String, Object>> data) {
    Map<String, Object> map = new HashMap<>();
    map.put("key", key);
    map.put("value", value);
    data.add(map);
  }
}
