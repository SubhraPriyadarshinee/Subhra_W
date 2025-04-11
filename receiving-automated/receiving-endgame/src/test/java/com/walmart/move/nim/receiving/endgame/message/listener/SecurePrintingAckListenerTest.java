package com.walmart.move.nim.receiving.endgame.message.listener;

import static org.mockito.ArgumentMatchers.any;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.endgame.common.PrintingAckHelper;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SecurePrintingAckListenerTest {
  @InjectMocks private SecurePrintingAckListener securePrintingAckListener;
  @Mock private PrintingAckHelper printingAckHelper;
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testListen() {
    Mockito.doNothing().when(printingAckHelper).doProcess(any(), any());
    securePrintingAckListener.listen(
        gson.toJson(MockMessageData.getMockScanEventData()),
        MockMessageData.getMockKafkaListenerHeaders());
  }
}
