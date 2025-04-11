package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.InstructionData;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryEventServiceTest extends ReceivingTestBase {
  private DeliveryUpdateMessage deliveryUpdateMessage;
  @InjectMocks private DeliveryEventService deliveryEventService;

  private List<Instruction> instructionList = new ArrayList<>();
  private List<Instruction> instructionList1 = new ArrayList<>();
  InstructionSummary instructionSummary1 = new InstructionSummary();
  @Mock private InstructionService instructionService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private Instruction instruction;
  private Instruction instruction1;
  private List<InstructionSummary> instructionSummaryList = new ArrayList<>();
  @Mock private MailService mailService;
  @Mock private ReportConfig reportConfig;
  private MimeMessage mimeMessage = null;
  private String expectedMailTemplate;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(deliveryEventService, "instructionService", instructionService);
    Properties props = new Properties();
    props.put(ReportingConstants.MAIL_HOST_KEY, ReportingConstants.MAIL_HOST_VALUE);
    Session session = Session.getDefaultInstance(props, null);
    mimeMessage = new MimeMessage(session);

    instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setActivityName("DACon");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setInstructionCode("Build Container");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setActivityName("DA");
    instruction.setPoDcNumber("32899");
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setReceivedQuantity(2);
    instruction.setCompleteTs(null);
    instruction.setProblemTagId("3423423");
    instructionList.add(instruction);

    instruction1 = new Instruction();
    instruction1.setId(Long.valueOf("1"));
    instruction1.setCreateTs(new Date());
    instruction1.setCreateUserId("sysadmin");
    instruction1.setActivityName("DACon");
    instruction1.setDeliveryNumber(Long.valueOf("21119003"));
    instruction1.setInstructionCode("Build Container");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction1.setActivityName("DA");
    instruction1.setPoDcNumber("32899");
    instruction1.setPurchaseReferenceNumber("9763140004");
    instruction1.setPurchaseReferenceLineNumber(1);
    instruction1.setProjectedReceiveQty(2);
    instruction1.setReceivedQuantity(0);
    instruction1.setCompleteTs(null);
    instruction1.setProblemTagId("3423423");
    instructionList1.add(instruction1);

    InstructionData instructionData = new InstructionData();
    instructionData.setMessageId("43335838-1a71-4961-800d-e56b2f1d75c7");
    instructionSummary1.setCreateTs(null);
    instructionSummary1.setCreateUserId("sysadmin");
    instructionSummary1.setCompleteTs(null);
    instructionSummary1.setCompleteUserId("sysadmin");
    instructionSummary1.setGtin("00000943037204");
    instructionSummary1.setId(1l);
    instructionSummary1.setInstructionData(instructionData);
    instructionSummary1.setItemDescription("HEM VALUE PACK (4)");
    instructionSummary1.setPoDcNumber("32899");
    instructionSummary1.setProjectedReceiveQty(2);
    instructionSummary1.setProjectedReceiveQtyUOM("ZA");
    instructionSummary1.setPurchaseReferenceNumber("9763140104");
    instructionSummary1.setPurchaseReferenceLineNumber(1);
    instructionSummary1.setReceivedQuantity(2);
    instructionSummary1.setReceivedQuantityUOM("ZA");
    instructionSummary1.setActivityName("DACON");
    instructionSummary1.setProblemTagId("null");
    instructionSummaryList.add(instructionSummary1);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId("qwert");

    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setEventType("FINALIZED");
    deliveryUpdateMessage.setDeliveryStatus("FNL");
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("32987");
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setDeliveryStatus("FNL");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/21119003");

    expectedMailTemplate =
        "<html><body><p style='text-align:left;font-size:14'>Hi,<br> Please Find List Of Partial Pending  Instructions.Please Complete/Cancel/VTR them.<p></p><h3 style='text-decoration: underline;'>DeliveryNumber :21119003</h3><p></p><table border='1px solid black'><tr><th>Message ID</th><th>PO DC</th><th>PO Number</th><th>Activity Name</th><th>Problem Tag ID</th><th>Received Quantity</th><th>Projected Received Quantity</th><th>Create TS</th><th>Create User</th></tr><tr><td>43335838-1a71-4961-800d-e56b2f1d75c7</td><td>32899</td><td>9763140104</td><td>DACON</td><td>null</td><td>2</td><td>2</td><td>null</td><td>sysadmin</td></tr></table><br><br><h4 style='text-decoration: underline;'> Note: </h4><p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.<p>Thanks,<br>Atlas-receiving team</p></html>";
  }

  @AfterMethod
  public void cleanup() {

    reset(instructionService);
    reset(mailService);
  }

  @Test
  public void testNoOpenInstructions() throws ReceivingException {

    when(instructionPersisterService
            .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(any(Long.class)))
        .thenReturn(null);
    deliveryEventService.processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
    verify(instructionPersisterService, times(1))
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(21119003l);
    verify(instructionService, times(0)).cancelInstruction(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void testPartiallyReceivedAgainstdelivery() throws ReceivingException {
    when(instructionPersisterService
            .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(any(Long.class)))
        .thenReturn(instructionList);
    when(mailService.prepareMailMessage(any(MailTemplate.class))).thenReturn(mimeMessage);
    doNothing().when(mailService).sendMail(any(MimeMessage.class));

    deliveryEventService.processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
    verify(instructionService, times(0)).cancelInstruction(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void testNothingReceivedAgainstDelivery() throws ReceivingException {

    when(instructionPersisterService
            .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(any(Long.class)))
        .thenReturn(instructionList1);
    when(instructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(instructionSummary1);
    deliveryEventService.processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
    verify(instructionService, times(1)).cancelInstruction(any(Long.class), any(HttpHeaders.class));
    assertEquals(instruction.getId(), instructionSummary1.getId());
  }

  @Test
  public void testcreateHtmlTemplateForPartialPendingInstructions() {
    StringBuilder actualHtmlTemplate =
        deliveryEventService.createHtmlTemplateForPartialPendingInstructions(
            instructionSummaryList, "21119003");
    assertEquals(actualHtmlTemplate.toString().trim(), expectedMailTemplate.trim());
  }
}
