package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER_CREATE_TS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DATE_FORMAT_ISO8601;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.SLOT_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildRequest;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClientException;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.witron.mock.data.WitronContainer;
import io.strati.libs.logging.commons.lang3.StringUtils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcSlottingServiceImplTest {

  @Mock private SlottingRestApiClient slottingRestApiClient;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private GDCFlagReader gdcFlagReader;

  @InjectMocks private GdcSlottingServiceImpl gdcSlottingServiceImpl;

  @Captor private ArgumentCaptor<SlottingPalletBuildRequest> slottingPalletBuildRequestCaptor;

  @Captor private ArgumentCaptor<SlottingPalletRequest> slottingPalletRequestArgumentCaptor;

  @BeforeMethod
  public void createWitronSlottingServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3-d4-e5");
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_ISO8601);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, dateFormat.format(new Date()));
  }

  @AfterMethod
  public void tearDown() {
    reset(slottingRestApiClient);
    reset(configUtils);
  }

  @Test
  public void test_acquireSlot() throws Exception {

    SlottingPalletBuildResponse response = new SlottingPalletBuildResponse();
    doReturn(response)
        .when(slottingRestApiClient)
        .palletBuild(any(SlottingPalletBuildRequest.class), anyMap());

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    String containerStatus = "TEST_STATUS";
    String containerTrackingId = "DUMMY_JUNIT_TRACKING_ID";
    Map<String, Object> httpHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());

    SlottingPalletBuildResponse acquireSlot =
        gdcSlottingServiceImpl.acquireSlot(
            instructionRequest, containerStatus, containerTrackingId, httpHeaders);
    assertNotNull(acquireSlot);

    verify(slottingRestApiClient).palletBuild(slottingPalletBuildRequestCaptor.capture(), anyMap());

    SlottingPalletBuildRequest capturedObj = slottingPalletBuildRequestCaptor.getValue();
    assertEquals(containerTrackingId, capturedObj.getContainer().getContainerTrackingId());
    assertEquals(containerStatus, capturedObj.getContainer().getContainerStatus());

    assertEquals("a1-b2-c3-d4-e5", capturedObj.getMessageId());
    assertEquals("123", capturedObj.getSourceLocation());
  }

  @Test
  public void test_acquireSlot_exceptionCase() throws Exception {

    SlottingRestApiClientException slottingRestApiClientException =
        new SlottingRestApiClientException();
    slottingRestApiClientException.setHttpStatus(HttpStatus.BAD_REQUEST);
    ErrorResponse errorResponse =
        new ErrorResponse(
            "GLS-SMRT-SLOTING-0001",
            "Induct Slots/staging area not found for the given source area/door");
    slottingRestApiClientException.setErrorResponse(errorResponse);

    doThrow(slottingRestApiClientException)
        .when(slottingRestApiClient)
        .palletBuild(any(SlottingPalletBuildRequest.class), anyMap());

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    String containerStatus = "TEST_STATUS";
    String containerTrackingId = "DUMMY_JUNIT_TRACKING_ID";
    Map<String, Object> httpHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());

    try {
      gdcSlottingServiceImpl.acquireSlot(
          instructionRequest, containerStatus, containerTrackingId, httpHeaders);
    } catch (ReceivingException re) {
      assertEquals(re.getErrorResponse().getErrorCode(), "createInstruction");
      assertEquals(re.getErrorResponse().getErrorHeader(), "Error in Slotting Service");
      assertEquals(re.getErrorResponse().getErrorMessage(), "Unable to determine slot");
    }
  }

  @Test
  public void test_getDivertLocation() throws Exception {

    SlottingPalletBuildResponse response = new SlottingPalletBuildResponse();
    doReturn(response)
        .when(slottingRestApiClient)
        .palletBuild(any(SlottingPalletBuildRequest.class), anyMap());

    Container container = WitronContainer.getContainer1();
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    SlottingPalletBuildResponse slottingPalletBuildResponse =
        gdcSlottingServiceImpl.getDivertLocation(container, httpHeaders, null);
    assertNotNull(slottingPalletBuildResponse);

    verify(slottingRestApiClient).palletBuild(slottingPalletBuildRequestCaptor.capture(), anyMap());

    SlottingPalletBuildRequest capturedObj = slottingPalletBuildRequestCaptor.getValue();
    assertEquals("027734368100444931", capturedObj.getContainer().getContainerTrackingId());

    assertEquals("101", capturedObj.getSourceLocation());
  }

  @Test
  public void test_getDivertLocation_error() throws Exception {

    SlottingRestApiClientException slottingRestApiClientException =
        new SlottingRestApiClientException();
    slottingRestApiClientException.setHttpStatus(HttpStatus.BAD_REQUEST);
    ErrorResponse errorResponse =
        new ErrorResponse(
            "GLS-SMRT-SLOTING-0001",
            "Induct Slots/staging area not found for the given source area/door");
    slottingRestApiClientException.setErrorResponse(errorResponse);

    doThrow(slottingRestApiClientException)
        .when(slottingRestApiClient)
        .palletBuild(any(SlottingPalletBuildRequest.class), anyMap());

    Container container = WitronContainer.getContainer1();
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    try {
      gdcSlottingServiceImpl.getDivertLocation(container, httpHeaders, null);
    } catch (ReceivingException re) {
      assertEquals(re.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  public void test_acquireSlotManualGdc_success() {

    SlottingPalletResponse response = new SlottingPalletResponse();
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    doReturn(response)
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    String containerTrackingId = "DUMMY_JUNIT_TRACKING_ID";
    HttpHeaders httpHeaders = ReceivingUtils.getForwardableHttpHeaders(ReceivingUtils.getHeaders());
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);

    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());
    SlottingPalletResponse acquireSlot =
        gdcSlottingServiceImpl.acquireSlotManualGdc(
            deliveryDocumentLine,
            mockReceiveInstructionRequest(),
            containerTrackingId,
            httpHeaders);
    assertNotNull(acquireSlot);

    verify(slottingRestApiClient)
        .getSlot(slottingPalletRequestArgumentCaptor.capture(), any(HttpHeaders.class));

    SlottingPalletRequest capturedObj = slottingPalletRequestArgumentCaptor.getValue();
    assertEquals(
        containerTrackingId, capturedObj.getContainerDetails().get(0).getContainerTrackingId());
    assertEquals("3515421377", capturedObj.getContainerDetails().get(0).getPurchaseOrderNum());

    assertEquals("a1-b2-c3-d4-e5", capturedObj.getMessageId());
    assertNotNull(
        capturedObj.getContainerDetails().get(0).getContainerItemsDetails().get(0).getRotateDate());
  }

  @Test
  public void test_acquireSlotManualGdc_request_missingerror() {

    ReceivingBadDataException receivingBadDataException =
        new ReceivingBadDataException(
            ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR, ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);

    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    doThrow(receivingBadDataException)
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());

    // Missing Container ID
    try {
      gdcSlottingServiceImpl.acquireSlotManualGdc(
          deliveryDocumentLine, mockReceiveInstructionRequest(), null, httpHeaders);
    } catch (ReceivingBadDataException re) {
      assertEquals(re.getErrorCode(), ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);
      assertEquals(re.getDescription(), ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }

    // Missing PO
    DeliveryDocumentLine documentLineMock = new DeliveryDocumentLine();
    documentLineMock.setItemNbr(342342L);
    try {
      gdcSlottingServiceImpl.acquireSlotManualGdc(
          documentLineMock, mockReceiveInstructionRequest(), "TEST-CONTAINER", httpHeaders);
    } catch (ReceivingBadDataException re) {
      assertEquals(re.getErrorCode(), ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);
      assertEquals(re.getDescription(), ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }
  }

  @Test
  public void test_acquireSlotManualGdc_stopReceivingResponse() {

    SlottingPalletResponse response = new SlottingPalletResponse();
    List<SlottingDivertLocations> locations = new ArrayList();
    SlottingDivertLocations locations1 = new SlottingDivertLocations();
    locations1.setType("error");
    locations1.setCode("GLS-SMART-SLOTING-4040009");
    locations1.setDesc("Setup issue - Prime Slot is not present for the entered item");
    locations.add(locations1);
    response.setLocations(locations);

    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    doReturn(response)
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    String containerTrackingId = "DUMMY_JUNIT_TRACKING_ID";
    HttpHeaders httpHeaders = ReceivingUtils.getForwardableHttpHeaders(ReceivingUtils.getHeaders());
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);

    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());

    // Application GLS-SMART-SLOTING-4040009 Error
    try {
      gdcSlottingServiceImpl.acquireSlotManualGdc(
          deliveryDocumentLine, mockReceiveInstructionRequest(), containerTrackingId, httpHeaders);
    } catch (ReceivingBadDataException re) {
      assertEquals(re.getErrorCode(), "GLS-RCV-SMART-SLOT-PRIME-404");
      assertTrue(
          StringUtils.contains(re.getDescription(), response.getLocations().get(0).getDesc()));
    }

    // Application SMART-SLOTTING-SERVICE-500 Error
    try {
      response.getLocations().get(0).setCode("SMART-SLOTTING-SERVICE-500");
      response
          .getLocations()
          .get(0)
          .setDesc("Unable to process request due to data setup issue or some config issue !");
      doReturn(response)
          .when(slottingRestApiClient)
          .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
      gdcSlottingServiceImpl.acquireSlotManualGdc(
          deliveryDocumentLine, mockReceiveInstructionRequest(), containerTrackingId, httpHeaders);
    } catch (ReceivingBadDataException re) {
      assertEquals(re.getErrorCode(), ExceptionCodes.SMART_SLOT_NOT_FOUND);
      assertTrue(
          StringUtils.contains(re.getDescription(), response.getLocations().get(0).getDesc()));
    }

    // Application Random Error
    try {
      response.getLocations().get(0).setCode("Random-Exception");
      response.getLocations().get(0).setDesc("Random Error");
      gdcSlottingServiceImpl.acquireSlotManualGdc(
          deliveryDocumentLine, mockReceiveInstructionRequest(), containerTrackingId, httpHeaders);
    } catch (ReceivingBadDataException re) {

      assertEquals(re.getErrorCode(), ExceptionCodes.SMART_SLOT_NOT_FOUND);
      assertTrue(
          StringUtils.contains(re.getDescription(), response.getLocations().get(0).getDesc()));
    }

    verify(slottingRestApiClient, times(3))
        .getSlot(slottingPalletRequestArgumentCaptor.capture(), any(HttpHeaders.class));
  }

  @Test
  public void test_acquireSlotManualGdc_continueReceivingError() {

    SlottingPalletResponse response = new SlottingPalletResponse();
    List<SlottingDivertLocations> locations = new ArrayList();
    SlottingDivertLocations locations1 = new SlottingDivertLocations();
    locations1.setType("error");
    locations1.setCode("GLS-SMART-SLOTING-4040008");
    locations1.setDesc(
        "No slot is available for auto-slotting. Reach out to QA or proceed with manual slotting");
    locations.add(locations1);
    response.setLocations(locations);

    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);

    doReturn(response)
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    String containerTrackingId = "DUMMY_JUNIT_TRACKING_ID";
    HttpHeaders httpHeaders = ReceivingUtils.getForwardableHttpHeaders(ReceivingUtils.getHeaders());
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);

    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(MockInstruction.getInstruction());
    SlottingPalletResponse acquireSlot =
        gdcSlottingServiceImpl.acquireSlotManualGdc(
            deliveryDocumentLine,
            mockReceiveInstructionRequest(),
            containerTrackingId,
            httpHeaders);
    assertNotNull(acquireSlot);

    verify(slottingRestApiClient)
        .getSlot(slottingPalletRequestArgumentCaptor.capture(), any(HttpHeaders.class));
    assertEquals(SLOT_NOT_FOUND, acquireSlot.getLocations().get(0).getLocation());
  }

  private ReceiveInstructionRequest mockReceiveInstructionRequest() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");
    return receiveInstructionRequest;
  }
}
