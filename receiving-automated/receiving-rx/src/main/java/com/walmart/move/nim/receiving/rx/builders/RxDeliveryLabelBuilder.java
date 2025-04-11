package com.walmart.move.nim.receiving.rx.builders;

import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class RxDeliveryLabelBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(RxDeliveryLabelBuilder.class);

  public PrintLabelData generateDeliveryLabel(
      Long deliveryNumber, int count, HttpHeaders httpHeaders) {
    LOGGER.info("Building deliveryLabel for delivery: {}", deliveryNumber);

    // Build header
    Map<String, String> headers = new HashMap<>();
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    headers.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // Build label data
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.DELIVERY_NUMBER)
            .value(deliveryNumber.toString())
            .build());
    labelDataList.add(LabelData.builder().key(RxConstants.LABEL_TIMESTAMP).value("").build());

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    labelDataList.add(LabelData.builder().key(RxConstants.USER).value(userId).build());

    // Prepare print request
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    printLabelRequest.setFormatName(RxConstants.DELIVERY_LABEL_FORMAT_NAME);
    printLabelRequest.setLabelIdentifier(deliveryNumber.toString());
    printLabelRequest.setTtlInHours(72);
    printLabelRequest.setData(labelDataList);

    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      printLabelRequests.add(printLabelRequest);
    }

    // Build container label
    PrintLabelData deliveryLabel = new PrintLabelData();
    deliveryLabel.setClientId(RxConstants.CLIENT_ID);
    deliveryLabel.setHeaders(headers);
    deliveryLabel.setPrintRequests(printLabelRequests);

    LOGGER.info("Returning deliveryLabel: {}", deliveryLabel);
    return deliveryLabel;
  }
}
