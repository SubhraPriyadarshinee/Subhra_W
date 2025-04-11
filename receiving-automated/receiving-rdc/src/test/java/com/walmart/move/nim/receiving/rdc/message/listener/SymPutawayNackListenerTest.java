package com.walmart.move.nim.receiving.rdc.message.listener;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.data.MockSymPutawayConfirmationMessage;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SymPutawayNackListenerTest {

  @InjectMocks private SymPutawayNackListener symPutawayNackListener;
  @Mock private AppConfig appConfig;

  private Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String SYM2 = "SYM2";
  private static final String MESSAGE_ID = "1236565";
  private static final String PUTAWAY_CONFIRMATION = "PUTAWAY_CONFIRMATION";
  private static final String SYM_MSG_TIMESTAMP_VAL = "2021-03-23T13:53:38.048Z";
  private static final String LABEL_ID_VAL = "K32818000010007461";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
    ReflectionTestUtils.setField(symPutawayNackListener, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(appConfig);
  }

  @Test
  public void testListenerToSkipMessagesForNotEnabledFacilities() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.valueOf("32944")));
    symPutawayNackListener.listen(
        MockSymPutawayConfirmationMessage.VALID_SYM_NACK_MESSAGE, getValidKafkaHeaders());
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
  }

  @Test
  public void testListenerForHappyPath() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.valueOf(facilityNum)));
    symPutawayNackListener.listen(
        MockSymPutawayConfirmationMessage.VALID_SYM_NACK_MESSAGE, getValidKafkaHeaders());
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
  }

  @Test
  public void testListenerForInvalidMessage() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.valueOf(facilityNum)));
    symPutawayNackListener.listen(null, getValidKafkaHeaders());
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testListenerThrowsException() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.valueOf(facilityNum)));
    symPutawayNackListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE,
        MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
  }

  private Map<String, byte[]> getValidKafkaHeaders() {
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getMockKafkaListenerHeaders();
    messageHeaders.put(ReceivingConstants.SYM_SYSTEM_KEY, SYM2.getBytes());
    messageHeaders.put(ReceivingConstants.SYM_MESSAGE_ID_HEADER, MESSAGE_ID.getBytes());
    messageHeaders.put(ReceivingConstants.SYM_EVENT_TYPE_KEY, PUTAWAY_CONFIRMATION.getBytes());
    messageHeaders.put(ReceivingConstants.SYM_MSG_TIMESTAMP, SYM_MSG_TIMESTAMP_VAL.getBytes());
    messageHeaders.put(ReceivingConstants.LABEL_ID_KEY, LABEL_ID_VAL.getBytes());
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    return messageHeaders;
  }
}
