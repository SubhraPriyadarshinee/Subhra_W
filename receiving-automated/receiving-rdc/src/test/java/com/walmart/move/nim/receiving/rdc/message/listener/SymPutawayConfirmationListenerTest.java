package com.walmart.move.nim.receiving.rdc.message.listener;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.data.MockSymPutawayConfirmationMessage;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SymPutawayConfirmationListenerTest {
  @InjectMocks private SymPutawayConfirmationListener symPutawayConfirmationListener;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private AppConfig appConfig;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
    ReflectionTestUtils.setField(symPutawayConfirmationListener, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(containerPersisterService, appConfig, tenantSpecificConfigReader);
  }

  @Test
  public void testListenerSkipMessagesForNotEnabledFacilities() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32944));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(MockContainer_CompleteStatus("c060200000200000003510353"));
    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE, getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testListenHappyPath() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32818));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(MockContainer_CompleteStatus("c060200000200000003510353"));
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE, getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void testPutawayConfirmationListenerToUpdateOnlyPutawayStatus_isInvalidLpn() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32818));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(MockContainer_CompleteStatus("099970200027724762"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any(Container.class)))
        .thenReturn(MockContainer_CompleteStatus("099970200027724762"));

    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE_INVALID_LPN,
        getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerPersisterService, times(0)).saveContainer(any(Container.class));
  }

  @Test
  public void testListenHappyPathDoesntUpdateContainerWhenStatusIsNotComplete() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32818));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(MockContainer_PutAwayCompleteStatus());
    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE, getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testListenContainerNotFound() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32818));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(null);

    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE, getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testListenInvalidMessage() {
    symPutawayConfirmationListener.listen(null, getKafkaHeaders());
  }

  @Test
  public void testListenTrackingIdNotFound() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32818));

    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.INVALID_PUTAWAY_CONFIRMATION_MESSAGE_EMPTY_TRACKING_ID,
        getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testListenInvalidException() {
    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.VALID_PUTAWAY_CONFIRMATION_MESSAGE,
        MockMessageHeaders.getMockKafkaListenerHeaders());
  }

  @Test
  public void testListenPutawayConfirmationWithErrordetails() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32818));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(MockContainer_CompleteStatus("c060200000200000003510353"));
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    symPutawayConfirmationListener.listen(
        MockSymPutawayConfirmationMessage.PUTAWAY_CONFIRMATION_MESSAGE_WITH_ERROR_DETAILS,
        getKafkaHeaders());

    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  private Map<String, byte[]> getKafkaHeaders() {
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getMockKafkaListenerHeaders();
    messageHeaders.put(ReceivingConstants.SYM_SYSTEM_KEY, "SYM2".getBytes());
    messageHeaders.put(ReceivingConstants.SYM_MESSAGE_ID_HEADER, "123456".getBytes());
    messageHeaders.put(ReceivingConstants.SYM_EVENT_TYPE_KEY, "PUTAWAY_CONFIRMATION".getBytes());
    messageHeaders.put(ReceivingConstants.SYM_MSG_TIMESTAMP, "2021-03-23T13:53:38.048Z".getBytes());
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    return messageHeaders;
  }

  private Container MockContainer_CompleteStatus(String trackingId) {
    Container container = new Container();
    container.setId(323323L);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    container.setDeliveryNumber(4232323L);
    container.setTrackingId(trackingId);
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, true);
    container.setContainerMiscInfo(containerMiscInfo);
    return container;
  }

  private Container MockContainer_PutAwayCompleteStatus() {
    Container container = new Container();
    container.setId(323323L);
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    container.setDeliveryNumber(4232323L);
    container.setTrackingId("c060200000200000003510353");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, true);
    container.setContainerMiscInfo(containerMiscInfo);
    return container;
  }
}
