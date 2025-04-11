package com.walmart.move.nim.receiving.rx.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxCancelInstructionReceiptPublisherTest {

  @Mock private JmsPublisher jmsPublisher;

  @InjectMocks
  private RxCancelInstructionReceiptPublisher rxCancelInstructionReceiptPublisher =
      new RxCancelInstructionReceiptPublisher();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
  }

  @AfterMethod
  public void tearDown() {
    reset(jmsPublisher);
  }

  @Test
  public void test_publishReceipt() {

    Instruction mockCancelledInstruction = new Instruction();
    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("MOCK_TRACKING_ID");
    mockCancelledInstruction.setContainer(mockContainerDetails);

    HttpHeaders mockHttpHeaders = new HttpHeaders();

    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    rxCancelInstructionReceiptPublisher.publishReceipt(mockCancelledInstruction, mockHttpHeaders);

    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }
}
