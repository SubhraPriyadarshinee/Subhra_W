package com.walmart.move.nim.receiving.core.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.event.processor.update.DefaultDeliveryProcessor;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdmDeliveryUpdateListenerHelperTest {
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultDeliveryProcessor defaultDeliveryProcessor;
  @Mock private AppConfig appConfig;

  @InjectMocks private GdmDeliveryUpdateListenerHelper gdmDeliveryUpdateListenerHelper;

  private File resource = null;
  private String eventMessage;

  @BeforeClass
  public void setRootUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    resource = new ClassPathResource("delivery_update_door_assignment.json").getFile();
    eventMessage = new String(Files.readAllBytes(resource.toPath()));
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void tearDown() {
    reset(defaultDeliveryProcessor);
    reset(tenantSpecificConfigReader);
    reset(appConfig);
  }

  @Test
  public void testListenGdmDeliveryUpdateMessageIsSuccessfullyConsumedForKafkaEnabledFacilities()
      throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));
    when(tenantSpecificConfigReader.getDeliveryEventProcessor(any()))
        .thenReturn(defaultDeliveryProcessor);
    doNothing().when(defaultDeliveryProcessor).processEvent(any(MessageData.class));

    gdmDeliveryUpdateListenerHelper.doProcess(
        eventMessage, ReceivingUtils.populateKafkaHeadersFromHttpHeaders(headers));

    verify(appConfig, times(1)).getGdmDeliveryUpdateKafkaListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1)).getDeliveryEventProcessor(any());
    verify(defaultDeliveryProcessor, times(1)).processEvent(any(MessageData.class));
  }

  @Test
  public void testListenGdmDeliveryUpdateMessageIsSuccessfullyConsumedForKafkaNotEnabledFacilities()
      throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32898));
    when(tenantSpecificConfigReader.getDeliveryEventProcessor(any()))
        .thenReturn(defaultDeliveryProcessor);
    doNothing().when(defaultDeliveryProcessor).processEvent(any(MessageData.class));

    gdmDeliveryUpdateListenerHelper.doProcess(
        eventMessage, ReceivingUtils.populateKafkaHeadersFromHttpHeaders(headers));

    verify(appConfig, times(1)).getGdmDeliveryUpdateKafkaListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0)).getDeliveryEventProcessor(any());
    verify(defaultDeliveryProcessor, times(0)).processEvent(any(MessageData.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testListenException() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));

    doThrow(ReceivingException.class)
        .when(tenantSpecificConfigReader)
        .getDeliveryEventProcessor(anyString());

    gdmDeliveryUpdateListenerHelper.doProcess(
        eventMessage, ReceivingUtils.populateKafkaHeadersFromHttpHeaders(headers));

    verify(appConfig, times(1)).getGdmDeliveryUpdateKafkaListenerEnabledFacilities();
    verify(defaultDeliveryProcessor, times(0)).processEvent(any(MessageData.class));
  }
}
