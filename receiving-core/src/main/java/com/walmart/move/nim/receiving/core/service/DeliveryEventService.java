package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.InstructionData;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DELIVERY_EVENT_SERVICE)
public class DeliveryEventService {

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  private InstructionService instructionService;

  @Autowired private InstructionPersisterService instructionPersisterService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  private static final String[] PARTIAL_PENDING_INSTR_COLUMN_NAMES = {
    "Message ID",
    "PO DC",
    "PO Number",
    "Activity Name",
    "Problem Tag ID",
    "Received Quantity",
    "Projected Received Quantity",
    "Create TS",
    "Create User"
  };

  @ManagedConfiguration private ReportConfig reportConfig;
  @Autowired private MailService mailService;
  List<InstructionSummary> instructionSummaryList;
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryEventService.class);

  /**
   * This method has to implemented to process the events after being received by message listener.
   *
   * @param deliveryUpdateMessage
   * @throws ReceivingException
   * @return
   */
  public void processOpenInstuctionsAfterDeliveryFinalized(
      DeliveryUpdateMessage deliveryUpdateMessage) throws ReceivingException {

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    instructionSummaryList = new ArrayList<InstructionSummary>();
    LOGGER.info(
        "Received: {} event from GDM with {} deliveryNumber and {} status.",
        deliveryUpdateMessage.getEventType(),
        deliveryUpdateMessage.getDeliveryNumber(),
        deliveryUpdateMessage.getDeliveryStatus());
    List<Instruction> getOpenInstructionsForCompletedDelivery =
        instructionPersisterService
            .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                Long.parseLong(deliveryUpdateMessage.getDeliveryNumber()));
    if (getOpenInstructionsForCompletedDelivery != null
        && !getOpenInstructionsForCompletedDelivery.isEmpty()) {
      LOGGER.info("Open Instructions Count: {}", getOpenInstructionsForCompletedDelivery.size());
      getOpenInstructionsForCompletedDelivery.forEach(
          instruction -> {
            if (instruction.getReceivedQuantity() == 0) {
              try {
                instructionService.cancelInstruction(instruction.getId(), httpHeaders);
                LOGGER.info("Cancelled Instruction: {}", instruction.getId());
              } catch (ReceivingException e) {
                LOGGER.error(
                    "{} {} {}",
                    ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE,
                    e.getErrorResponse(),
                    ExceptionUtils.getStackTrace(e));
              }
            } else {
              instructionSummaryList = getInstructionSummaryList(instruction);
            }
          });
      if (instructionSummaryList != null && instructionSummaryList.size() != 0) {
        sendPartialInstructionEmailNotification(
            instructionSummaryList,
            ReceivingUtils.sanitize(deliveryUpdateMessage.getDeliveryNumber()));
      }
    }
  }

  private void sendPartialInstructionEmailNotification(
      List<InstructionSummary> instructionSummaryList, String deliveryNumber)
      throws ReceivingException {
    StringBuilder mailHtmlTemplate =
        createHtmlTemplateForPartialPendingInstructions(instructionSummaryList, deliveryNumber);
    MailTemplate mailTemplate =
        MailTemplate.builder()
            .reportFromAddress(reportConfig.getReportFromAddress())
            .reportToAddresses(
                tenantSpecificConfigReader.getEmailIdListByTenant(
                    TenantContext.getFacilityNum().toString()))
            .mailSubject(ReportingConstants.EMAIL_SUBJECT_PARTIALPENDING)
            .mailReportFileName(ReportingConstants.EMAIL_SUBJECT_PARTIALPENDING)
            .mailHtmlTemplate(mailHtmlTemplate.toString())
            .build();
    MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
    mailService.sendMail(mimeMessage);
  }

  private List<InstructionSummary> getInstructionSummaryList(Instruction instruction) {
    InstructionSummary instructionSummary = new InstructionSummary();
    InstructionData instructionData = new InstructionData();
    instructionData.setMessageId(instruction.getMessageId());
    instructionSummary.setInstructionData(instructionData);
    instructionSummary.setPoDcNumber(instruction.getPoDcNumber());
    instructionSummary.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    instructionSummary.setReceivedQuantity(instruction.getReceivedQuantity());
    instructionSummary.setProjectedReceiveQty(instruction.getProjectedReceiveQty());
    instructionSummary.setActivityName(instruction.getActivityName());
    instructionSummary.setProblemTagId(instruction.getProblemTagId());
    instructionSummary.setCreateTs(instruction.getCreateTs());
    instructionSummary.setCreateUserId(instruction.getCreateUserId());
    instructionSummaryList.add(instructionSummary);
    return instructionSummaryList;
  }

  /**
   * This method is used generate mail body html template for pending partial report.
   *
   * @param instructionSummaryList List of pending instructions
   * @param deliveryNumber Delivery number
   * @return
   */
  public StringBuilder createHtmlTemplateForPartialPendingInstructions(
      List<InstructionSummary> instructionSummaryList, String deliveryNumber) {
    StringBuilder mailHtmlTemplate = new StringBuilder();
    mailHtmlTemplate.append("<html><body>");
    mailHtmlTemplate.append(
        "<p style='text-align:left;font-size:14'>Hi,<br> Please Find List Of Partial Pending "
            + " Instructions.Please Complete/Cancel/VTR them.");
    mailHtmlTemplate.append("<p></p>");

    mailHtmlTemplate
        .append("<h3 style='text-decoration: underline;'>DeliveryNumber :")
        .append(deliveryNumber)
        .append("</h3>");
    mailHtmlTemplate.append("<p></p>");

    // creating table headers
    StringBuilder tableBody = new StringBuilder();
    // creation of table body
    instructionSummaryList.forEach(
        i ->
            tableBody
                .append("<tr><td>")
                .append(i.getInstructionData().getMessageId())
                .append("</td><td>")
                .append(i.getPoDcNumber())
                .append("</td><td>")
                .append(i.getPurchaseReferenceNumber())
                .append("</td><td>")
                .append(i.getActivityName())
                .append("</td><td>")
                .append(i.getProblemTagId())
                .append("</td><td>")
                .append(i.getReceivedQuantity())
                .append("</td><td>")
                .append(i.getProjectedReceiveQty())
                .append("</td><td>")
                .append(i.getCreateTs())
                .append("</td><td>")
                .append(i.getCreateUserId())
                .append("</td></tr>"));
    String tableHeader =
        "<table border='1px solid black'><tr><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[0]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[1]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[2]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[3]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[4]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[5]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[6]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[7]
            + "</th><th>"
            + PARTIAL_PENDING_INSTR_COLUMN_NAMES[8]
            + "</th></tr>";
    mailHtmlTemplate.append(tableHeader).append(tableBody).append("</table><br><br>");

    mailHtmlTemplate.append("<h4 style='text-decoration: underline;'> Note: </h4>");
    mailHtmlTemplate.append(
        "<p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.");
    mailHtmlTemplate.append("<p>Thanks,<br>Atlas-receiving team</p></html>");
    return mailHtmlTemplate;
  }
}
