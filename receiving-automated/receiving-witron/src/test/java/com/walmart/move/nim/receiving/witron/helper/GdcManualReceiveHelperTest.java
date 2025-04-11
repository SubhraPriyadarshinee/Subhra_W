package com.walmart.move.nim.receiving.witron.helper;

import static com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient.GLS_RECEIVE_ERROR_CODE;
import static java.lang.Integer.valueOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveRequest;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveResponse;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.OverrideInfo;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.service.GdcSlottingServiceImpl;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdcManualReceiveHelperTest {
  @Mock private GlsRestApiClient glsRestApiClient;
  @Mock private GDCFlagReader gdcFlagReader;
  @Mock private ContainerLabelBuilder containerLabelBuilder;
  @Mock private GdcSlottingServiceImpl slottingService;
  @Mock private TenantSpecificConfigReader configUtils;

  Gson gson = new Gson();

  @InjectMocks private GdcManualReceiveHelper gdcManualReceiveHelper;

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private final String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);

  ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(gdcManualReceiveHelper, "gson", gson);
    TenantContext.setFacilityNum(valueOf(facilityNum));
  }

  @AfterMethod
  public void tearDown() {
    reset(gdcFlagReader);
    reset(glsRestApiClient);
    reset(configUtils);
  }

  @Test
  public void testBuildInstructionFromGLS() throws ReceivingException, IOException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    File resource = new ClassPathResource("gls_receiveResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(gdcFlagReader.isGLSApiEnabled()).thenReturn(true);
    when(glsRestApiClient.receive(any(), any()))
        .thenReturn(new Gson().fromJson(mockResponse, GLSReceiveResponse.class));
    when(containerLabelBuilder.generateContainerLabelV2(any(), any(), any()))
        .thenReturn(new ContainerLabel());
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("1");

    OverrideInfo overrideInfo = new OverrideInfo();
    Boolean value =
        gdcManualReceiveHelper.buildInstructionFromGls(
            receiveInstructionRequest, instruction, httpHeaders, overrideInfo);

    assertEquals(instruction.getContainer().getTrackingId(), "TAG-1");
    assertEquals(instruction.getMove().get("toLocation"), "SLOT-1");
    assertEquals(instruction.getContainer().getGlsWeight(), 386.7192);
    assertEquals(instruction.getContainer().getGlsWeightUOM(), "LB");
    assertTrue(value);
  }

  @Test
  public void testReceiveSuccess() throws ReceivingException, IOException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    File resource = new ClassPathResource("gls_receiveResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(glsRestApiClient.receive(any(), any()))
        .thenReturn(new Gson().fromJson(mockResponse, GLSReceiveResponse.class));
    when(containerLabelBuilder.generateContainerLabelV2(any(), any(), any()))
        .thenReturn(new ContainerLabel());
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("1");

    OverrideInfo overrideInfo = new OverrideInfo();
    GLSReceiveResponse glsReceiveResponse =
        gdcManualReceiveHelper.receive(
            receiveInstructionRequest, instruction, httpHeaders, overrideInfo);

    assertEquals(glsReceiveResponse.getWeightUOM(), "LB");
    assertEquals(glsReceiveResponse.getWeight(), new Double(386.7192));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceive_exception() throws ReceivingException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("1");
    when(glsRestApiClient.receive(any(), any()))
        .thenThrow(
            new ReceivingException(
                "gls receive failed", HttpStatus.INTERNAL_SERVER_ERROR, GLS_RECEIVE_ERROR_CODE));

    OverrideInfo overrideInfo = new OverrideInfo();
    gdcManualReceiveHelper.receive(
        receiveInstructionRequest, instruction, httpHeaders, overrideInfo);
  }

  @Test
  public void testReceiveMapping() {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("1");
    when(configUtils.getOrgUnitId()).thenReturn("2");
    OverrideInfo overrideInfo = new OverrideInfo();
    GLSReceiveRequest glsReceiveRequest =
        gdcManualReceiveHelper.constructReceive(
            receiveInstructionRequest, instruction, httpHeaders, overrideInfo);

    assertTrue(glsReceiveRequest.getQuantity() == 1);
    assertTrue("V".equalsIgnoreCase(glsReceiveRequest.getVnpkWgtFmtCode()));
    assertTrue("105".equalsIgnoreCase(glsReceiveRequest.getDoorNumber()));
    assertEquals(
        glsReceiveRequest.getRotateDate(),
        receiveInstructionRequest.getRotateDate().toInstant().atOffset(ZoneOffset.UTC).toString());
    assertTrue(glsReceiveRequest.getFreightBillQty() == 170);
  }

  @Test
  public void testReceiveMapping_withReceivingCorrection() {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setIsReceiveCorrection(true);

    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("1");

    OverrideInfo overrideInfo = new OverrideInfo();
    GLSReceiveRequest glsReceiveRequest =
        gdcManualReceiveHelper.constructReceive(
            receiveInstructionRequest, instruction, httpHeaders, overrideInfo);

    assertTrue(glsReceiveRequest.getQuantity() == 1);
    assertTrue("Y".equalsIgnoreCase(glsReceiveRequest.getReceiveAsCorrection()));
  }

  @Test
  public void testAdjustOrCancel_exception() throws ReceivingException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    when(glsRestApiClient.createGlsAdjustPayload(any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    when(glsRestApiClient.adjustOrCancel(any(), any()))
        .thenThrow(
            new ReceivingException(
                "Adjust or Cancel failed",
                HttpStatus.INTERNAL_SERVER_ERROR,
                GLS_RECEIVE_ERROR_CODE));

    Boolean isSuccess = true;
    try {

      gdcManualReceiveHelper.adjustOrCancel(
          receiveInstructionRequest, instruction, httpHeaders, isSuccess);
    } catch (ReceivingBadDataException ex) {
      assertTrue("glsReceiveFailed".equalsIgnoreCase(ex.getErrorCode()));
    }
  }

  @Test
  public void testAdjustOrCancel_success() throws ReceivingException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    when(glsRestApiClient.adjustOrCancel(any(), any())).thenReturn(new GlsAdjustPayload());
    when(glsRestApiClient.createGlsAdjustPayload(any(), any(), any(), any(), any()))
        .thenCallRealMethod();

    gdcManualReceiveHelper.adjustOrCancel(
        receiveInstructionRequest, instruction, httpHeaders, true);

    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
  }

  @Test
  public void testAdjustOrCancel_receiveNotCalled() throws ReceivingException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    when(glsRestApiClient.adjustOrCancel(any(), any())).thenReturn(new GlsAdjustPayload());
    when(glsRestApiClient.createGlsAdjustPayload(any(), any(), any(), any(), any()))
        .thenCallRealMethod();

    gdcManualReceiveHelper.adjustOrCancel(
        receiveInstructionRequest, instruction, httpHeaders, false);

    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
  }

  @Test
  public void testBuildInstructionFromSlotting() throws IOException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    File resource = new ClassPathResource("slotting_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(slottingService.acquireSlotManualGdc(any(), any(), any(), any()))
        .thenReturn(new Gson().fromJson(mockResponse, SlottingPalletResponse.class));
    when(containerLabelBuilder.generateContainerLabelV2(any(), any(), any()))
        .thenReturn(new ContainerLabel());
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    gdcManualReceiveHelper.buildInstructionFromSlotting(
        receiveInstructionRequest, instruction, httpHeaders, updateInstructionRequest);

    assertEquals(instruction.getMove().get("toLocation"), "H0005");
    assertEquals(String.valueOf(instruction.getMove().get("locationSize")), "72");
    assertEquals(instruction.getMove().get("containerTag"), "TEST-CONTAINER");
    assertEquals(instruction.getMove().get("slotType"), "prime");
    assertEquals(instruction.getMove().get("primeLocation"), "H0005");
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) instruction.getContainer().getCtrLabel().get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
    assertEquals(labelData.get(5).get("key"), "slot");
    assertEquals(labelData.get(5).get("value"), "H0005");
  }

  @Test
  public void testBuildInstructionFromSlotting_MechDC_label() throws IOException {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("LB");

    Instruction instruction = MockInstruction.getManualGdcInstruction();

    File resource = new ClassPathResource("slotting_response_mechDc.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    when(slottingService.acquireSlotManualGdc(any(), any(), any(), any()))
        .thenReturn(new Gson().fromJson(mockResponse, SlottingPalletResponse.class));
    final ContainerLabel value = new ContainerLabel();
    when(containerLabelBuilder.generateContainerLabelV2(any(), any(), any())).thenReturn(value);
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    gdcManualReceiveHelper.buildInstructionFromSlotting(
        receiveInstructionRequest, instruction, httpHeaders, updateInstructionRequest);

    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) instruction.getContainer().getCtrLabel().get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
    assertEquals(labelData.get(5).get("key"), "slot");
    assertEquals(labelData.get(5).get("value"), "H0005");
    assertEquals(labelData.get(9).get("key"), "WA");
    assertEquals(labelData.get(9).get("value"), "M-DD");
  }
}
