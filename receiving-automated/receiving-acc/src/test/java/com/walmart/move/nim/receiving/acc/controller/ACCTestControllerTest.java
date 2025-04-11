package com.walmart.move.nim.receiving.acc.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONEOPS_ENVIRONMENT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationService;
import com.walmart.move.nim.receiving.acc.service.UserLocationService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.service.DefaultCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.Arrays;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACCTestControllerTest extends ReceivingTestBase {

  private static final String TEST_DOOR_NUMBER = "123";
  private static final String TEST_USER = "sysadmin.s32987";
  private MockMvc mockMvc;
  private ACCTestController ACCTestController;
  @Mock private ACLNotificationService aclNotificationService;

  @Mock private UserLocationService userLocationService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultCompleteDeliveryProcessor defaultCompleteDeliveryProcessor;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ACCTestController = new ACCTestController();
    ReflectionTestUtils.setField(
        ACCTestController, "aclNotificationService", aclNotificationService);
    ReflectionTestUtils.setField(ACCTestController, "userLocationService", userLocationService);
    ReflectionTestUtils.setField(
        ACCTestController, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    System.setProperty(ONEOPS_ENVIRONMENT, "dev");
    this.mockMvc = MockMvcBuilders.standaloneSetup(ACCTestController).build();
  }

  @Test
  public void testGetAclLogs() throws Exception {
    when(aclNotificationService.getAclNotificationLogsByLocation(eq(TEST_DOOR_NUMBER), any()))
        .thenReturn(Arrays.asList(NotificationLog.builder().build()));

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/automated/test/acl-logs/" + TEST_DOOR_NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testDeleteByDoor() throws Exception {

    doNothing().when(userLocationService).deleteByLocation(TEST_DOOR_NUMBER);
    doNothing().when(aclNotificationService).deleteByLocation(TEST_DOOR_NUMBER);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/automated/test/door/" + TEST_DOOR_NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testGetUsers() throws Exception {
    when(userLocationService.getByUser(TEST_USER)).thenReturn(Arrays.asList(new UserLocation()));

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/automated/test/location-users/" + TEST_USER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testAutoCompleteDeliveries() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(defaultCompleteDeliveryProcessor);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/automated/test/delivery/autoComplete")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testAutoCompleteDeliveriesThrowsException() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(null); // throws NPE
    mockMvc.perform(
        MockMvcRequestBuilders.post("/automated/test/delivery/autoComplete")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(httpHeaders));
  }
}
