package com.walmart.move.nim.receiving.acc.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.acc.service.RoboDepalEventProcessor;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RoboDepalContainerStatusListenerTest {
  @InjectMocks private RoboDepalContainerStatusListener depalContainerStatusListener;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RoboDepalEventProcessor depalEventProcessor;

  private String eventMessage;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    String dataPath;
    try {
      dataPath =
          new File(
                  "../../receiving-core/src/test/resources/Depal_container_status_change_listener.json")
              .getCanonicalPath();
      eventMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(depalEventProcessor);
  }

  @Test
  public void testListen() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED))
        .thenReturn(true);
    depalContainerStatusListener.listen(
        eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(depalEventProcessor, times(1)).processEvent(any(MessageData.class));
  }

  @Test
  public void testListen_RoboDepalFeatureDisabled() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED))
        .thenReturn(false);
    depalContainerStatusListener.listen(
        eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(depalEventProcessor, times(0)).processEvent(any(MessageData.class));
  }

  @Test
  public void testListen_emptyMessage() {
    depalContainerStatusListener.listen("", MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(depalEventProcessor, times(0)).processEvent(any(MessageData.class));
  }

  @Test
  public void testListen_throwsException() {
    try {
      when(tenantSpecificConfigReader.isFeatureFlagEnabled(
              ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED))
          .thenReturn(true);
      doThrow(ReceivingException.class).when(depalEventProcessor).processEvent(any());
      depalContainerStatusListener.listen(
          eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    } catch (Exception e) {
      assert (true);
    }
  }
}
