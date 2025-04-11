package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.Collections;
import javax.mail.internet.MimeMessage;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MailServiceTest extends ReceivingTestBase {

  @InjectMocks private MailService mailService;

  private MailTemplate mailTemplate;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    String mailHtmlTemplate = "<html> Hi, please find below report </html>";
    mailTemplate =
        MailTemplate.builder()
            .reportFromAddress("abc@walmartlabs.com")
            .reportToAddresses(Collections.singletonList("xyz@walmartlabs.com"))
            .mailHtmlTemplate(mailHtmlTemplate)
            .mailSubject(ReportingConstants.STATS_REPORT_SUBJECT_LINE)
            .attachment(null)
            .build();
  }

  /**
   * This method is used to test prepareMailMimeMessage test
   *
   * @throws ReceivingException
   */
  @Test
  public void prepareMailMessageTest() throws ReceivingException {
    MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
    assertNotNull(mimeMessage);
  }
}
