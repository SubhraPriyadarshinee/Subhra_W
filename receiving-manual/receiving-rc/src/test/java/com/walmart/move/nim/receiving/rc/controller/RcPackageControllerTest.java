package com.walmart.move.nim.receiving.rc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rc.model.dto.request.PackageTrackerRequest;
import com.walmart.move.nim.receiving.rc.service.RcPackageTrackerService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcPackageControllerTest extends ReceivingControllerTestBase {
  @InjectMocks private RcPackageController rcPackageController;
  @Mock private RcPackageTrackerService rcPackageTrackerService;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  private RestResponseExceptionHandler restResponseExceptionHandler;
  private MockMvc mockMvc;
  private Gson gson;
  private PackageTrackerRequest packageTrackerRequest;
  private PackageTrackerRequest packageTrackerRequestInvalid;

  @BeforeClass
  public void init() throws IOException {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    String dataPathPackageTrackerRequest =
        new File("../../receiving-test/src/main/resources/json/RcPackageTrackerRequest.json")
            .getCanonicalPath();
    packageTrackerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageTrackerRequest))),
            PackageTrackerRequest.class);
    String dataPathPackageTrackerRequestInvalid =
        new File("../../receiving-test/src/main/resources/json/RcPackageTrackerRequestInvalid.json")
            .getCanonicalPath();
    packageTrackerRequestInvalid =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageTrackerRequestInvalid))),
            PackageTrackerRequest.class);
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rcPackageController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(rcPackageTrackerService);
  }

  @Test
  public void testTrackPackage() throws Exception {
    doNothing().when(rcPackageTrackerService).trackPackageStatus(any(PackageTrackerRequest.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/package/tracker")
                .content(gson.toJson(packageTrackerRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());
    verify(rcPackageTrackerService, times(1)).trackPackageStatus(any(PackageTrackerRequest.class));
  }

  @Test
  public void testTrackPackageForInvalidReasonCode() throws Exception {
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_PACKAGE_TRACKER_REQUEST,
                ExceptionDescriptionConstants.INVALID_PACKAGE_TRACKER_REQUEST_REASON_CODE))
        .when(rcPackageTrackerService)
        .trackPackageStatus(any(PackageTrackerRequest.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/package/tracker")
                .content(gson.toJson(packageTrackerRequestInvalid))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
    verify(rcPackageTrackerService, times(1)).trackPackageStatus(any(PackageTrackerRequest.class));
  }
}
