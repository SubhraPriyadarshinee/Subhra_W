package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.config.FdeConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.ContainerModel;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.Facility;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerResponse;
import com.walmart.move.nim.receiving.data.MockFdeSpec;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FdeServiceTest extends ReceivingTestBase {

  @InjectMocks private FdeServiceImpl fdeService;
  @Mock private FdeConfig fdeConfig;
  @Mock private RestUtils restUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private InstructionError instructionError;

  private Gson gson = new Gson();
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private FdeCreateContainerRequest fdeCreateContainerRequest = new FdeCreateContainerRequest();
  private FdeCreateContainerRequest fdeCreateContainerRequestForS2S =
      new FdeCreateContainerRequest();
  private FdeCreateContainerResponse fdeCreateContainerResponse =
      MockInstruction.getFdeCreateContainerResponse();

  private static final String URL1 = "https://url1/lpn";
  private static final String URL2 = "https://url2/lpn";
  private static final String POCONURL1 = "https://url1/pocon/lpn";
  private static final String POCONURL2 = "https://url2/pocon/lpn";

  @BeforeClass
  public void initTenantContext() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);

    Content content = new Content();
    content.setGtin("00016017103993");
    content.setItemNbr(556565795L);
    content.setBaseDivisionCode("WM");
    content.setFinancialReportingGroup("US");
    content.setPurchaseCompanyId(String.valueOf(1));
    content.setPurchaseReferenceNumber(String.valueOf(999557344));
    content.setPoDcNumber(String.valueOf(32899));
    content.setVendorNumber(String.valueOf(234336140));
    content.setDeptNumber(14);
    content.setPurchaseReferenceLegacyType(String.valueOf(33));
    content.setPurchaseReferenceLineNumber(1);
    content.setPurchaseRefType("CROSSU");
    content.setQtyUom(ReceivingConstants.Uom.VNPK);
    content.setVendorPack(4);
    content.setWarehousePack(4);
    content.setOpenQty(22);
    content.setTotalOrderQty(22);
    content.setPalletTie(4);
    content.setPalletHigh(4);
    content.setMaxReceiveQty(33);
    content.setWarehousePackSell(33.48F);
    content.setVendorPackCost(41.94F);
    content.setCurrency("");
    content.setColor("");
    content.setSize("");
    content.setDescription("TR ED 3PC FRY/GRL RD");
    content.setSecondaryDescription("3PC PAN SET");
    content.setIsConveyable(true);
    content.setIsHazmat(Boolean.FALSE);
    content.setEvent("POS REPLEN");

    List<Content> contents = new ArrayList<>();
    contents.add(content);

    // Populate Container Model
    ContainerModel containerModel = new ContainerModel();
    containerModel.setWeight(11.94F);
    containerModel.setWeightUom("LB");
    containerModel.setCube(1.373F);
    containerModel.setCubeUom("CF");
    containerModel.setContents(contents);

    // Populate Facility
    Facility facility = new Facility();
    facility.setCountryCode("US");
    facility.setBuNumber("32899");

    // Populate fde create container request
    fdeCreateContainerRequest.setContainer(containerModel);
    fdeCreateContainerRequest.setFacility(facility);
    fdeCreateContainerRequest.setMessageId("74mxms333dkjsnfds");
    fdeCreateContainerRequest.setDeliveryNumber(String.valueOf(88278907));
    fdeCreateContainerRequest.setCorrelationId(
        MockHttpHeaders.getHeaders().getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    fdeCreateContainerRequest.setDoorNumber("100");
    fdeCreateContainerRequest.setUserId(
        MockHttpHeaders.getHeaders().getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    Content contentS2S = new Content();

    contentS2S.setPurchaseReferenceNumber(String.valueOf(338117271));
    contentS2S.setPurchaseReferenceLineNumber(1);
    contentS2S.setGtin("0752113650145");
    contentS2S.setItemNbr(0L);
    contentS2S.setBaseDivisionCode("WM");
    contentS2S.setFinancialReportingGroup("US");
    contentS2S.setPurchaseRefType("S2S");
    contentS2S.setQty(1);
    contentS2S.setQtyUom(ReceivingConstants.Uom.EACHES);

    List<Content> contentsS2S = new ArrayList<>();
    contentsS2S.add(contentS2S);

    ContainerModel childContainerModelS2S = new ContainerModel();
    childContainerModelS2S.setContents(contentsS2S);
    childContainerModelS2S.setCtrType("Carton");
    childContainerModelS2S.setTrackingId("00000077670100204725");
    childContainerModelS2S.setCtrDestination(facility);
    childContainerModelS2S.setCube(1.373F);
    childContainerModelS2S.setCubeUom("CF");

    List<ContainerModel> childContainerModelsS2S = new ArrayList<>();
    childContainerModelsS2S.add(childContainerModelS2S);

    ContainerModel containerModelS2S = new ContainerModel();
    containerModelS2S.setCtrType("Pallet");
    containerModelS2S.setTrackingId("00100077672010779635");
    containerModelS2S.setWeight(146F);
    containerModelS2S.setWeightUom("lb");
    containerModelS2S.setCtrDestination(facility);
    containerModelS2S.setCube(1.373F);
    containerModelS2S.setCubeUom("CF");
    containerModelS2S.setChildContainers(childContainerModelsS2S);

    fdeCreateContainerRequestForS2S.setContainer(containerModelS2S);
    fdeCreateContainerRequestForS2S.setFacility(facility);
    fdeCreateContainerRequestForS2S.setMessageId("74mxms333dkjsnfds");
    fdeCreateContainerRequest.setDeliveryNumber(String.valueOf(20030791));
    fdeCreateContainerRequest.setCorrelationId(
        MockHttpHeaders.getHeaders().getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    fdeCreateContainerRequest.setDoorNumber("100");
    fdeCreateContainerRequest.setUserId(
        MockHttpHeaders.getHeaders().getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @AfterMethod
  public void resetMocks() {
    reset(fdeConfig);
    reset(restUtils);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void test_receive_SSTK() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType("SSTKU");
    fdeCreateContainerRequest.getContainer().getContents().get(0).setIsConveyable(Boolean.FALSE);
    doReturn(
            new ResponseEntity<String>(gson.toJson(fdeCreateContainerResponse), HttpStatus.CREATED))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }
  }

  @Test
  public void test_receive_DA() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(
            new ResponseEntity<String>(gson.toJson(fdeCreateContainerResponse), HttpStatus.CREATED))
        .when(restUtils)
        .post(anyString(), any(), any(), anyString());

    try {
      ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      verify(restUtils, times(1)).post(anyString(), headerCaptor.capture(), anyMap(), anyString());
      HttpHeaders capturedHttpHeaders = headerCaptor.getValue();
      assertEquals(
          capturedHttpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
          TenantContext.getFacilityNum().toString());
      assertEquals(
          capturedHttpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE).toUpperCase(),
          TenantContext.getFacilityCountryCode().toUpperCase());
      assertEquals(capturedHttpHeaders.getContentType(), MediaType.APPLICATION_JSON_UTF8);
    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }
  }

  @Test
  public void test_receive_S2S() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(
            new ResponseEntity<String>(gson.toJson(fdeCreateContainerResponse), HttpStatus.CREATED))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      fdeService.receive(fdeCreateContainerRequestForS2S, httpHeaders);
    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }
  }

  @Test
  public void test_receive_isPOCON() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType("POCON");
    doReturn(
            new ResponseEntity<String>(gson.toJson(fdeCreateContainerResponse), HttpStatus.CREATED))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }
  }

  @Test
  public void testreceiveUnknownChannelType() {
    fdeCreateContainerRequest
        .getContainer()
        .getContents()
        .get(0)
        .setPurchaseRefType("PURCH_REF_TYPE");
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);

    try {
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(
              ReceivingException.FDE_RECEIVE_NO_MATCHING_CAPABILITY_FOUND, "PURCH_REF_TYPE"));
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType(null);

    try {
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.FDE_RECEIVE_EMPTY_PURCHASE_REF_TYPE);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }

    fdeCreateContainerRequest.getContainer().getContents().remove(0);

    try {
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.FDE_RECEIVE_EMPTY_PURCHASE_REF_TYPE);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }

    fdeCreateContainerRequestForS2S
        .getContainer()
        .getChildContainers()
        .get(0)
        .getContents()
        .remove(0);

    try {
      fdeService.receive(fdeCreateContainerRequestForS2S, httpHeaders);
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.FDE_RECEIVE_EMPTY_PURCHASE_REF_TYPE);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  public void testFindFulfillmentTypeOld() {
    // test backwardCompatibility
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC_OLD);
    try {
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), URL1);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testFindFulfillmentType() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    try {
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), URL1);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testFindFulfillmentTypeWhenMultipleProviderDefault() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC_MULTIPLE_PROVIDER);
    try {
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), URL1);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testFindFulfillmentTypeWhenMultipleProviderForFacility() {
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC_MULTIPLE_PROVIDER);
    try {
      TenantContext.setFacilityNum(6561);
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), URL2);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testFindFulfillmentTypePOCON() {

    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType("POCON");
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    try {
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), POCONURL1);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testFindFulfillmentTypePOCONMultipleProviderDefault() {

    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType("POCON");
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC_MULTIPLE_PROVIDER);
    try {
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), POCONURL1);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testFindFulfillmentTypePOCONForAFacility() {

    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType("POCON");
    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC_MULTIPLE_PROVIDER);
    try {
      TenantContext.setFacilityNum(6561);
      assertEquals(fdeService.findFulfillmentType(fdeCreateContainerRequest), POCONURL2);
    } catch (ReceivingException e) {
      fail("No exception expected");
    }
  }

  @Test
  public void testreceiveNullResponseFromOF() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(null).when(restUtils).post(any(), any(), any(), any());

    try {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);

    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(instructionError.getErrorMessage(), "null"));
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    reset(fdeConfig);
    reset(restUtils);
  }

  @Test
  public void test_receiveClientSeriesError() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(
            new ResponseEntity<String>(
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00009\",\"desc\":\"No allocations\"}]}",
                HttpStatus.CONFLICT))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00009");
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveServerSeriesError() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(
            new ResponseEntity<String>(
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00010\",\"desc\":\"internal server error\"}]}",
                HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveServerSeriesError_AllocationErrorMessage() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)))
        .thenReturn(Boolean.TRUE);
    doReturn(
            new ResponseEntity<String>(
                "{\"messages\":[{\"type\":\"error\",\"code\":\"GLS-OF-BE-00010\",\"desc\":\"internal server error\",\"detailed_desc\":\"Test detailed message\"}]}",
                HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), "Test detailed message");
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void test_receiveOFServiceDown() {

    when(fdeConfig.getSpec()).thenReturn(MockFdeSpec.MOCK_FDE_SPEC);
    doReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE))
        .when(restUtils)
        .post(any(), any(), any(), any());

    try {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_NETWORK_ERROR);
      fdeService.receive(fdeCreateContainerRequest, httpHeaders);
      assertTrue(false);

    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorMessage(), instructionError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorCode(), instructionError.getErrorCode());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
