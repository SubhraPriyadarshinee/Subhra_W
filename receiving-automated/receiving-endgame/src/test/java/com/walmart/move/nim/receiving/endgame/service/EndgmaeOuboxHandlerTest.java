package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndgmaeOuboxHandlerTest  extends ReceivingTestBase {

  @InjectMocks private EndgameOutboxHandler endgameOutboxHandler;

  @Mock private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        endgameOutboxHandler, "iOutboxPublisherService", iOutboxPublisherService);
    TenantContext.setFacilityNum(7441);
    TenantContext.setFacilityCountryCode("us");
    TenantContext.setCorrelationId("abc");
  }

  @AfterMethod
  public void resetMocks() {
    reset(iOutboxPublisherService);
  }

  @Test
  private void testSentToOutBox() {
    endgameOutboxHandler.sentToOutbox("", "", new HashMap<>());
    verify(iOutboxPublisherService, times(1))
        .publishToHTTP(
            anyString(), anyString(), anyMap(), anyString(), anyInt(), anyString(), anyMap());
  }

  @Test
  private void testSentToOutBoxWithHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put(CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    headers.put(TENENT_FACLITYNUM, getFacilityNum());
    headers.put(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    endgameOutboxHandler.sentToOutbox("", "", new HashMap<>(), headers);
    verify(iOutboxPublisherService, times(1))
        .publishToHTTP(
            anyString(), anyString(), anyMap(), anyString(), anyInt(), anyString(), anyMap());
  }

  @Test
  public void testSendToOutboxWithHttpHeaders() {
    String payload = "testPayload";
    String serviceName = "testService";
    HttpHeaders headers = new HttpHeaders();
    headers.add("testHeader", "testValue");

    endgameOutboxHandler.sendToOutbox(payload, serviceName, headers);

    verify(iOutboxPublisherService, times(1))
            .publishToHTTP(
                    anyString(),
                    eq(payload),
                    anyMap(),
                    eq(serviceName),
                    eq(TenantContext.getFacilityNum()),
                    eq(TenantContext.getFacilityCountryCode()),
                    eq(emptyMap()),
                    eq(TenantContext.getCorrelationId()));
  }

  @Test
  public void testSendToOutboxWithMapHeaders() {
    String payload = "testPayload";
    String serviceName = "testService";
    Map<String, Object> headers = new HashMap<>();
    headers.put("testHeader", "testValue");
    String partitionKey = "testPartitionKey";
    Map<String, Object> uriVariables = new HashMap<>();
    uriVariables.put("testUriVariable", "testValue");

    endgameOutboxHandler.sendToOutbox(payload, serviceName, headers, partitionKey, uriVariables);

    verify(iOutboxPublisherService, times(1))
            .publishToHTTP(
                    anyString(),
                    eq(payload),
                    eq(headers),
                    eq(serviceName),
                    eq(TenantContext.getFacilityNum()),
                    eq(TenantContext.getFacilityCountryCode()),
                    eq(uriVariables),
                    eq(partitionKey));
  }
}
