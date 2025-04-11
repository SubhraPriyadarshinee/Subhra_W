package com.walmart.move.nim.receiving.acc.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSearchResponse;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSummary;
import com.walmart.move.nim.receiving.acc.model.acl.notification.EquipmentStatus;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationService;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.ArrayList;
import java.util.List;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLNotificationControllerTest extends ReceivingControllerTestBase {

  public static final String JSONPATH_LOCATION_ID = "$.aclLogs[0].locationId";
  public static final String LOCATION_NOWHERE = "nowhere";

  private Gson gson = new Gson();

  private MockMvc mockMvc;
  ACLNotificationController aclNotificationController;

  private RestResponseExceptionHandler restResponseExceptionHandler;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  @Mock private ACLNotificationService aclNotificationService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    aclNotificationController = new ACLNotificationController();
    restResponseExceptionHandler = new RestResponseExceptionHandler();

    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);

    ReflectionTestUtils.setField(
        aclNotificationController, "aclNotificationService", aclNotificationService);
    ReflectionTestUtils.setField(aclNotificationController, "gson", gson);

    this.mockMvc =
        MockMvcBuilders.standaloneSetup(aclNotificationController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void createLogSuccess() throws Exception {
    ACLNotification notification = new ACLNotification();
    notification.setLocationId("nowhere");
    List<EquipmentStatus> statusList = new ArrayList<EquipmentStatus>();
    statusList.add(EquipmentStatus.builder().code(2).value("HOST_LATE").build());
    notification.setEquipmentStatus(statusList);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/acl-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(gson.toJson(notification, ACLNotification.class)))
        .andExpect(status().isCreated());
  }

  @Test
  public void getLogSuccess() throws Exception {

    ACLNotificationSummary dummyLog =
        ACLNotificationSummary.builder().locationId(LOCATION_NOWHERE).build();
    List<ACLNotificationSummary> dummyList = new ArrayList<>();
    dummyList.add(dummyLog);
    ACLNotificationSearchResponse aclNotificationSearchResponse =
        new ACLNotificationSearchResponse(0, 10, dummyList);

    when(aclNotificationService.getAclNotificationSearchResponse(anyString(), anyInt(), anyInt()))
        .thenReturn(aclNotificationSearchResponse);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/acl-logs/nowhere?page=0&size=10")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath(JSONPATH_LOCATION_ID, is(LOCATION_NOWHERE)));
  }

  @Test
  public void invalidLocationRequest() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/acl-logs/bad*location?page=0&size=10")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }
}
