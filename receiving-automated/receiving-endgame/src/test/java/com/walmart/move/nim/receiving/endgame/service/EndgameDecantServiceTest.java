package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.endgame.model.DimensionPayload;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndgameDecantServiceTest extends ReceivingTestBase {

  @Mock private KafkaTemplate kafkaTemplate;

  @Mock private KafkaConfig kafkaConfig;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @InjectMocks private EndgameDecantService endgameDecantService;

  @Mock private IOutboxPublisherService iOutboxPublisherService;

  private Gson gson;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    this.gson = new Gson();
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(54321);
    TenantContext.setCorrelationId("1234");
    ReflectionTestUtils.setField(endgameDecantService, "gson", this.gson);
    ReflectionTestUtils.setField(endgameDecantService, "decantDimensionTopic", "SOME_TOPIC");
    when(tenantSpecificConfigReader.isOutboxEnabledForVendorDimensionEvents()).thenReturn(false);
  }

  @AfterMethod
  public void resetMocks() {
    reset(kafkaTemplate);
  }

  @Test
  public void successPublishTest() {
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    DimensionPayload dimensionPayload = new DimensionPayload();
    dimensionPayload.setItemNumber(123L);
    endgameDecantService.publish(dimensionPayload);
    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void successPublishTest_outbox() {
    when(tenantSpecificConfigReader.isOutboxEnabledForVendorDimensionEvents()).thenReturn(true);
    DimensionPayload dimensionPayload = new DimensionPayload();
    dimensionPayload.setItemNumber(123L);
    endgameDecantService.publish(dimensionPayload);
    verify(iOutboxPublisherService, times(1))
            .publishToHTTP(anyString(), anyString(), anyMap(), any(), anyInt(), anyString(), anyMap());
  }

}
