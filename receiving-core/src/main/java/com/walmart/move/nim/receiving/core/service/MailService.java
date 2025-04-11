package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** @author pcr000m */
@Service
public class MailService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);

  /**
   * This method is used to create mail mime message.
   *
   * @param mailTemplate mail template
   * @return
   * @throws Exception
   */
  public MimeMessage prepareMailMessage(MailTemplate mailTemplate) throws ReceivingException {
    Properties props = new Properties();
    props.put(ReportingConstants.MAIL_HOST_KEY, ReportingConstants.MAIL_HOST_VALUE);
    Session session = Session.getDefaultInstance(props, null);
    MimeMessage message = new MimeMessage(session);
    try {
      List<String> toEmailAddresses = mailTemplate.getReportToAddresses();
      String fromEmailAddress = mailTemplate.getReportFromAddress();
      InternetAddress[] targetRecipients = new InternetAddress[toEmailAddresses.size()];
      for (int index = 0; index < toEmailAddresses.size(); index++) {
        targetRecipients[index] = new InternetAddress(toEmailAddresses.get(index).trim());
      }
      message.setFrom(new InternetAddress(fromEmailAddress));
      message.setRecipients(Message.RecipientType.TO, targetRecipients);
      message.setSubject(mailTemplate.getMailSubject());

      BodyPart messageBodyPart = new MimeBodyPart();
      messageBodyPart.setContent(mailTemplate.getMailHtmlTemplate(), "text/html");
      Multipart multipart = new MimeMultipart();
      multipart.addBodyPart(messageBodyPart);
      Workbook mailAttachment = mailTemplate.getAttachment();
      if (mailAttachment != null) {
        messageBodyPart = new MimeBodyPart();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        mailAttachment.write(bos); // write excel data to a byte array
        bos.close();
        mailAttachment.close();

        DataSource fds = new ByteArrayDataSource(bos.toByteArray(), "application/vnd.ms-excel");
        messageBodyPart.setDataHandler(new DataHandler(fds));
        messageBodyPart.setFileName(
            mailTemplate.getMailReportFileName() + "_" + java.time.LocalDate.now() + ".xlsx");
        multipart.addBodyPart(messageBodyPart);
      }
      message.setContent(multipart);
    } catch (Exception exception) {
      throw new ReceivingException(ExceptionUtils.getStackTrace(exception));
    }
    return message;
  }

  /**
   * This method is used to send mail.
   *
   * @param message mail message
   * @throws ReceivingException
   */
  public void sendMail(MimeMessage message) throws ReceivingException {
    try {
      Transport.send(message);
      /* Logging without from and recepient information because of privacy violation  */
      LOGGER.info(ReportingConstants.SUCCESSFUL_MAIL_SENT_MESSAGE);
    } catch (Exception exception) {
      throw new ReceivingException(ExceptionUtils.getStackTrace(exception));
    }
  }
}
