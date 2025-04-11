package com.walmart.move.nim.receiving.core.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.mockrunner.mock.jms.MockTextMessage;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.event.processor.update.DefaultDeliveryProcessor;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryUpdateListenerTest extends ReceivingTestBase {

  @InjectMocks private DeliveryUpdateListener deliveryUpdateListener;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultDeliveryProcessor defaultDeliveryProcessor;
  @Mock private AppConfig appConfig;

  File resource = null;
  private Gson gson;
  private TextMessage textMessage = new MockTextMessage();

  @BeforeClass
  public void setRootUp() throws IOException, JMSException {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();

    textMessage.setStringProperty(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    textMessage.setStringProperty(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    textMessage.setStringProperty(
        ReceivingConstants.JMS_CORRELATION_ID, UUID.randomUUID().toString());

    resource = new ClassPathResource("delivery_update_door_assignment.json").getFile();
    textMessage.setText(new String(Files.readAllBytes(resource.toPath())));
    ReflectionTestUtils.setField(deliveryUpdateListener, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(defaultDeliveryProcessor);
    reset(tenantSpecificConfigReader);
    reset(appConfig);
  }

  @Test
  public void testListenIsSuccessForKafkaNotEnabledFacilities()
      throws ReceivingException, JMSException {
    when(appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32918));
    when(tenantSpecificConfigReader.getDeliveryEventProcessor(any()))
        .thenReturn(defaultDeliveryProcessor);
    doNothing().when(defaultDeliveryProcessor).processEvent(any(MessageData.class));

    deliveryUpdateListener.listen(textMessage, MockMessageHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmDeliveryUpdateKafkaListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1)).getDeliveryEventProcessor(any());
    verify(defaultDeliveryProcessor, times(1)).processEvent(any(MessageData.class));
  }

  @Test
  public void testListenIsSuccessForKafkaEnabledFacilities()
      throws ReceivingException, JMSException {
    when(appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));

    deliveryUpdateListener.listen(textMessage, MockMessageHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmDeliveryUpdateKafkaListenerEnabledFacilities();
  }
}
