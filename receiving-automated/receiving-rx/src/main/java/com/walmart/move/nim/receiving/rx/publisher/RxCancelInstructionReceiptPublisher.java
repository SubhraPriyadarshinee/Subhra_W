package com.walmart.move.nim.receiving.rx.publisher;

import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.PublishReceiptsCancelInstruction;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class RxCancelInstructionReceiptPublisher {

  @Autowired private JmsPublisher jmsPublisher;

  public void publishReceipt(Instruction cancelledInstruction, HttpHeaders httpHeaders) {
    // Prepare the payload to publish receipt with ZERO quantity
    PublishReceiptsCancelInstruction.ContentsData contentsData =
        new PublishReceiptsCancelInstruction.ContentsData(
            cancelledInstruction.getPurchaseReferenceNumber(),
            cancelledInstruction.getPurchaseReferenceLineNumber(),
            0,
            ReceivingConstants.Uom.EACHES);
    PublishReceiptsCancelInstruction receiptsCancelInstruction =
        new PublishReceiptsCancelInstruction();
    receiptsCancelInstruction.setMessageId(cancelledInstruction.getMessageId());
    receiptsCancelInstruction.setTrackingId(cancelledInstruction.getContainer().getTrackingId());
    receiptsCancelInstruction.setDeliveryNumber(cancelledInstruction.getDeliveryNumber());
    receiptsCancelInstruction.setContents(Arrays.asList(contentsData));
    receiptsCancelInstruction.setActivityName(cancelledInstruction.getActivityName());

    ReceivingJMSEvent receivingJMSEvent =
        new ReceivingJMSEvent(
            ReceivingUtils.getForwardablHeader(httpHeaders),
            new GsonBuilder()
                .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                .create()
                .toJson(receiptsCancelInstruction));
    jmsPublisher.publish(ReceivingConstants.PUB_RECEIPTS_TOPIC, receivingJMSEvent, Boolean.TRUE);
  }
}
