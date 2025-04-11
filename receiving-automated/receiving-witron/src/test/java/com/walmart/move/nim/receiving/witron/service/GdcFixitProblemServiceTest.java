package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdcFixitProblemServiceTest {
  @InjectMocks private GdcFixitProblemService gdcFixitProblemService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private RestUtils restUtils;
  @Mock private AppConfig appconfig;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryService deliveryService;
  @Mock private ProblemRepository problemRepository;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private ProblemReceivingHelper problemReceivingHelper;

  private Gson gson = new Gson();
  private String countryCode = "US";
  private String facilityNum = "6071";
  private String PROBLEM_TAG = "06071704725305";
  private ProblemLabel problemLabel = new ProblemLabel();
  private String mockFixitBaseUrl = "https://mock.fixit.dev.walmart.net";
  private HttpHeaders headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(gdcFixitProblemService, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        configUtils,
        restUtils,
        appconfig,
        receiptService,
        deliveryService,
        problemRepository,
        deliveryDocumentHelper,
        problemReceivingHelper);
  }

  @Test
  public void testGetProblemTagInfo_success() throws ReceivingException, IOException {
    when(appconfig.getFixitPlatformBaseUrl()).thenReturn(mockFixitBaseUrl);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ENABLE_FIXIT_SERVICE_MESH_HEADERS,
            false))
        .thenReturn(true);
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(mockFixitProblemTagResponse()));
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    when(deliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(gson.fromJson(mockGdmPoLineResponse(), GdmPOLineResponse.class));
    doReturn(0L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    doReturn(0L).when(receiptService).getReceivedQtyByProblemIdInVnpk(anyString());
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    when(problemRepository.save(any(ProblemLabel.class))).thenReturn(problemLabel);
    when(deliveryDocumentHelper.isFirstExpiryFirstOut(anyString())).thenReturn(Boolean.TRUE);

    ProblemTagResponse response = gdcFixitProblemService.txGetProblemTagInfo(PROBLEM_TAG, headers);

    verify(deliveryService, times(1))
        .getPOLineInfoFromGDM(anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(receiptService, times(1)).getReceivedQtyByProblemIdInVnpk(anyString());
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(problemRepository, times(1)).save(any(ProblemLabel.class));
    verify(deliveryDocumentHelper, times(1)).isFirstExpiryFirstOut(anyString());

    assertNotNull(response.getProblem());
    Problem problem = response.getProblem();
    assertEquals(problem.getType(), "NOP");
    assertEquals(problem.getSlotId(), "PA02");
    assertEquals(problem.getIssueId(), "8c6b88f0-7e1c-48e0-a351-a09c8a3411e3");
    assertEquals(problem.getProblemTagId(), "06071704725305");
    assertEquals(problem.getResolutionId(), "c15ee165-b729-409e-810d-45e5ea0a9cb1");
    assertEquals(problem.getResolutionQty().intValue(), 10);
    assertEquals(problem.getReportedQty().intValue(), 10);
    assertEquals(problem.getReceivedQty().intValue(), 0);

    assertNotNull(response.getItem());
    Item item = response.getItem();
    assertEquals(item.getGtin(), "045255148435");
    assertEquals(item.getNumber().intValue(), 555291630);

    assertNotNull(response.getDeliveryDocumentLine());
    DeliveryDocumentLine deliveryDocumentLine = response.getDeliveryDocumentLine();
    assertEquals(deliveryDocumentLine.getPurchaseReferenceNumber(), "3773250394");
    assertEquals(deliveryDocumentLine.getPurchaseReferenceLineNumber(), 4);
    assertEquals(deliveryDocumentLine.getQuantity().intValue(), 10);
    assertEquals(deliveryDocumentLine.getOpenQty().intValue(), 10);
    assertEquals(deliveryDocumentLine.getFirstExpiryFirstOut(), Boolean.TRUE);
  }

  @Test
  public void testGetProblemTagInfo_blockGlsDelivery() throws ReceivingException, IOException {
    when(appconfig.getFixitPlatformBaseUrl()).thenReturn(mockFixitBaseUrl);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ENABLE_FIXIT_SERVICE_MESH_HEADERS,
            false))
        .thenReturn(true);
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(mockFixitProblemTagResponse()));
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(mockGdmPoLineResponse(), GdmPOLineResponse.class);
    gdmPOLineResponse.setDeliveryOwnership("gls");
    when(deliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(gdmPOLineResponse);
    try {
      gdcFixitProblemService.txGetProblemTagInfo(PROBLEM_TAG, headers);
    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-DELIVERY-400", e.getErrorCode());
      assertEquals("This delivery will need to be received in GLS", e.getDescription());
    }
  }

  private String mockFixitProblemTagResponse() throws IOException {
    File resource = new ClassPathResource("fixit_scanPtag_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    return mockResponse;
  }

  private String mockGdmPoLineResponse() throws IOException {
    File resource = new ClassPathResource("gdm_po_poline_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    return mockResponse;
  }
}
