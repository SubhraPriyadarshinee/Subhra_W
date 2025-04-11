package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertSame;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.utils.RdcProblemUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcFitProblemServiceTest {
  @Mock private RdcProblemUtils rdcProblemUtils;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private AppConfig appconfig;
  @Mock private RestUtils restUtils;

  @InjectMocks private RdcFitProblemService rdcFitProblemService;

  protected Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private HttpHeaders headers = null;
  private static final String PROBLEM_TAG = "testProblemTag";
  private static final String deliveryNumber = "123231212";
  private static final String issueId = "1aa1-2bb2-3cc3-4dd4-5ee5";
  private static final String userId = "sysadmin";
  private static final String SLOT_1 = "A0001";
  private static final String OVG_TYPE = "OVG";

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(rdcFitProblemService, "gson", gson);
    ReflectionTestUtils.setField(rdcFitProblemService, "gson", gson);
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @AfterMethod
  public void resetMocks() {
    reset(rdcProblemUtils);
  }

  @Test
  public void testReceivedQtyByPoAndPoLine() throws IOException, ReceivingException {
    Resolution resolution = new Resolution();
    resolution.setResolutionPoNbr("8458708162");
    resolution.setResolutionPoLineNbr(1);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    when(rdcProblemUtils.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine))
        .thenReturn(10L);
    Long receivedQtyByPoAndPol =
        rdcFitProblemService.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);
    assertNotNull(receivedQtyByPoAndPol);
    assertSame(receivedQtyByPoAndPol, 10L);
  }

  @Test
  public void testGetProblemTagInfo_containerReceivable_nonAtlasItem()
      throws ReceivingException, IOException {
    FitProblemTagResponse fitProblemTagResponse = mockFitProblemTagResponse();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ENABLE_FIXIT_SERVICE_MESH_HEADERS,
            false))
        .thenReturn(true);
    when(appconfig.getFixitPlatformBaseUrl()).thenReturn("FixitBaseUrl");
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(gson.toJson(mockFitProblemTagResponse())));
    when(rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, PROBLEM_TAG, headers))
        .thenReturn(new ProblemTagResponse());
    rdcFitProblemService.txGetProblemTagInfo(PROBLEM_TAG, headers);
    verify(rdcProblemUtils, times(1))
        .txGetProblemTagInfo(any(FitProblemTagResponse.class), anyString(), any(HttpHeaders.class));
  }

  private FitProblemTagResponse mockFitProblemTagResponse() throws IOException {

    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    List<Resolution> resolutions1 = new ArrayList<>();

    Issue issue1 = new Issue();
    Resolution resolution1 = new Resolution();
    resolution1.setId("1");
    resolution1.setResolutionPoNbr(
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0).getPurchaseReferenceNumber());
    resolution1.setResolutionPoLineNbr(1);
    resolution1.setQuantity(10);
    resolution1.setAcceptedQuantity(2);
    resolution1.setRemainingQty(8);
    resolutions1.add(resolution1);
    issue1.setDeliveryNumber(deliveryNumber);
    issue1.setId(issueId);
    issue1.setItemNumber(84834L);
    issue1.setStatus(ProblemStatus.ANSWERED.toString());
    issue1.setResolutionStatus(ProblemStatus.PARTIAL_RESOLUTION.toString());
    issue1.setType(OVG_TYPE);

    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setId(1L);
    problemLabel.setProblemTagId(PROBLEM_TAG);
    problemLabel.setDeliveryNumber(Long.valueOf(deliveryNumber));
    problemLabel.setIssueId(issueId);
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    problemLabel.setLastChangeUserId(userId);
    problemLabel.setCreateTs(new Date());
    problemLabel.setLastChangeTs(new Date());

    fitProblemTagResponse.setLabel(PROBLEM_TAG);
    fitProblemTagResponse.setRemainingQty(10);
    fitProblemTagResponse.setReportedQty(10);
    fitProblemTagResponse.setSlot(SLOT_1);
    fitProblemTagResponse.setIssue(issue1);
    fitProblemTagResponse.setResolutions(resolutions1);

    return fitProblemTagResponse;
  }
}
