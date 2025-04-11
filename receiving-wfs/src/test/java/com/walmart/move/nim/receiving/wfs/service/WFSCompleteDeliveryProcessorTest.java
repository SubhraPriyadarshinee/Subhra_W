package com.walmart.move.nim.receiving.wfs.service;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.joda.time.DateTime;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSCompleteDeliveryProcessorTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private InstructionService instructionService;
  @InjectMocks private WFSCompleteDeliveryProcessor wfsCompleteDeliveryProcessor;
  private HttpHeaders headers;
  private static final String facilityNum = "4093";
  private static final int pageNumber = 0;
  private static final long deliveryNumber = 123456;
  private static final String poNbr = "4445530688";
  private static final long openInstructionCount = 1L;
  private static final long closedInstructionCount = 0L;
  private static final String countryCode = "US";
  private List<ReceiptSummaryResponse> receiptSummaryEachesResponse;
  private List<ReceiptSummaryResponse> receiptSummaryResponseVNPK;
  private Gson gson = new Gson();

  private Receipt getMockReceipt() {
    Receipt receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId("sysadmin");
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(21119003L);
    receipt.setDoorNumber("171");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140005");
    receipt.setQuantity(2);
    receipt.setQuantityUom("ZA");
    receipt.setVnpkQty(2);
    receipt.setWhpkQty(4);
    return receipt;
  }

  private List<InstructionDetails> getPendingInstructions() {
    InstructionDetails instructionDetails1 =
        InstructionDetails.builder()
            .id(2323232323l)
            .deliveryNumber(1234567L)
            .lastChangeUserId("sysadmin2")
            .receivedQuantity(0)
            .createUserId("sysadmin")
            .build();

    InstructionDetails instructionDetails2 =
        InstructionDetails.builder()
            .id(212142234342l)
            .deliveryNumber(1234567L)
            .lastChangeUserId(null)
            .receivedQuantity(0)
            .createUserId("sysadmin")
            .build();

    List<InstructionDetails> instructionListWithOpenInstructions = new ArrayList<>();
    instructionListWithOpenInstructions.add(instructionDetails1);
    instructionListWithOpenInstructions.add(instructionDetails2);
    return instructionListWithOpenInstructions;
  }

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(wfsCompleteDeliveryProcessor, "gson", gson);
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);

    receiptSummaryEachesResponse = new ArrayList<>();
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));

    receiptSummaryResponseVNPK = new ArrayList<>();
    receiptSummaryResponseVNPK.add(
        new ReceiptSummaryEachesResponse("8763140001", 1, null, Long.valueOf(96)));
    receiptSummaryResponseVNPK.add(
        new ReceiptSummaryEachesResponse("8763140002", 1, null, Long.valueOf(96)));
    receiptSummaryResponseVNPK.add(
        new ReceiptSummaryEachesResponse("8763140003", 1, null, Long.valueOf(144)));
  }

  @BeforeMethod
  public void setUp() {}

  @AfterMethod
  public void resetMocks() {
    reset(
        instructionRepository,
        receiptRepository,
        receiptService,
        tenantSpecificConfigReader,
        deliveryStatusPublisher);
  }

  @Test
  public void testCompleteDeliveryKotlinNotEnabled() throws ReceivingException {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    Map<String, Object> deliveryCompleteHeaders = ReceivingUtils.getForwardablHeader(headers);
    deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_STATUS, DeliveryStatus.COMPLETE.name());
    List<Receipt> receipts = new ArrayList<>();
    doReturn(TRUE).when(tenantSpecificConfigReader).isPoConfirmationFlagEnabled(anyInt());
    doReturn(receipts)
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(anyLong(), anyInt());
    doReturn(closedInstructionCount)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
    when(ReceivingUtils.isKotlinEnabled(headers, tenantSpecificConfigReader)).thenReturn(FALSE);
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.EACHES);
    doReturn(deliveryInfo)
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.COMPLETE.name(),
            receiptSummaryEachesResponse,
            deliveryCompleteHeaders);
    wfsCompleteDeliveryProcessor.completeDelivery(deliveryNumber, false, headers);
    assertNotNull(deliveryInfo);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.EACHES);
    verify(receiptService, times(0))
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.VNPK);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.COMPLETE.name(),
            receiptSummaryEachesResponse,
            deliveryCompleteHeaders);
  }

  @Test
  public void testCompleteDeliveryKotlinEnabled() throws ReceivingException {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    Map<String, Object> deliveryCompleteHeaders = ReceivingUtils.getForwardablHeader(headers);
    deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_STATUS, DeliveryStatus.COMPLETE.name());
    List<Receipt> receipts = new ArrayList<>();
    doReturn(TRUE).when(tenantSpecificConfigReader).isPoConfirmationFlagEnabled(anyInt());
    doReturn(receipts)
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(anyLong(), anyInt());
    doReturn(closedInstructionCount)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
    when(ReceivingUtils.isKotlinEnabled(headers, tenantSpecificConfigReader)).thenReturn(TRUE);
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.EACHES);
    doReturn(receiptSummaryResponseVNPK)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.VNPK);
    doReturn(deliveryInfo)
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.COMPLETE.name(),
            receiptSummaryEachesResponse,
            deliveryCompleteHeaders);
    wfsCompleteDeliveryProcessor.completeDelivery(deliveryNumber, false, headers);
    assertNotNull(deliveryInfo);
    assertNotNull(deliveryInfo.getReceipts());
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.EACHES);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.VNPK);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.COMPLETE.name(),
            receiptSummaryEachesResponse,
            deliveryCompleteHeaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testValidateForOpenInstructionsException() throws ReceivingException {
    doReturn(openInstructionCount)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
    wfsCompleteDeliveryProcessor.validateForOpenInstructions(deliveryNumber);
    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
  }

  @Test
  public void testValidateForOpenInstructions() throws ReceivingException {
    doReturn(closedInstructionCount)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
    wfsCompleteDeliveryProcessor.validateForOpenInstructions(deliveryNumber);
    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
  }

  @Test
  public void testConstructDeliveryCompleteHeaders() {
    Map<String, Object> headerMap =
        wfsCompleteDeliveryProcessor.constructDeliveryCompleteHeaders(deliveryNumber, headers);
    assertNotNull(headerMap);
    assertEquals(deliveryNumber, headerMap.get(ReceivingConstants.DELIVERY_NUMBER));
    assertEquals(DeliveryStatus.COMPLETE.name(), headerMap.get(ReceivingConstants.DELIVERY_STATUS));
  }

  @Test
  public void testAutoCompleteDeliveriesNoPendingInstruction()
      throws IOException, ReceivingException {
    JsonObject json = new JsonObject();
    json.addProperty("maxAllowedTime", 2);
    json.addProperty("actualTime", 3);
    JsonElement maxAllowedTime = json.get("maxAllowedTime");
    JsonElement actualTime = json.get("actualTime");
    Receipt receiptWithIdealTime = getMockReceipt();
    List<InstructionDetails> instructions = new ArrayList<>();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    assertNotNull(gdmDeliveryStatusResponse);

    when(deliveryService.fetchDeliveriesByStatus(
            anyList(), anyList(), any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);
    when(instructionHelperService.checkIfListContainsAnyPendingInstruction(instructions))
        .thenReturn(TRUE);
    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            anyLong(), any(int.class)))
        .thenReturn(instructions);
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);

    doReturn(maxAllowedTime)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            Integer.valueOf(facilityNum), ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR);

    doReturn(actualTime)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            Integer.valueOf(facilityNum), ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR);
    wfsCompleteDeliveryProcessor.autoCompleteDeliveries(Integer.valueOf(facilityNum));
  }

  @Test
  public void testAutoCompleteDeliveriesWithPendingInstructions()
      throws IOException, ReceivingException {
    JsonObject json = new JsonObject();
    json.addProperty("maxAllowedTime", 3);
    json.addProperty("actualTime", 2);
    JsonElement maxAllowedTime = json.get("maxAllowedTime");
    JsonElement actualTime = json.get("actualTime");
    Receipt receiptWithIdealTime = getMockReceipt();
    List<InstructionDetails> instructions = new ArrayList<>();
    InstructionSummary instructionSummary = new InstructionSummary();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    assertNotNull(gdmDeliveryStatusResponse);

    when(deliveryService.fetchDeliveriesByStatus(
            anyList(), anyList(), any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);
    when(instructionHelperService.checkIfListContainsAnyPendingInstruction(instructions))
        .thenReturn(TRUE);
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);
    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            any(Long.class), any(Integer.class)))
        .thenReturn(getPendingInstructions());
    when(instructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(instructionSummary);

    doReturn(maxAllowedTime)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            Integer.valueOf(facilityNum), ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR);

    doReturn(actualTime)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            Integer.valueOf(facilityNum), ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR);
    wfsCompleteDeliveryProcessor.autoCompleteDeliveries(Integer.valueOf(facilityNum));
  }

  @Test
  public void testAutoCompleteDeliveriesActualTimeLessThanMaxTimeAllowed()
      throws IOException, ReceivingException {
    JsonObject json = new JsonObject();
    json.addProperty("maxAllowedTime", 3);
    json.addProperty("actualTime", 2);
    JsonElement maxAllowedTime = json.get("maxAllowedTime");
    JsonElement actualTime = json.get("actualTime");
    Receipt receiptWithIdealTime = getMockReceipt();
    List<InstructionDetails> instructions = new ArrayList<>();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    assertNotNull(gdmDeliveryStatusResponse);

    when(deliveryService.fetchDeliveriesByStatus(
            anyList(), anyList(), any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);
    when(instructionHelperService.checkIfListContainsAnyPendingInstruction(instructions))
        .thenReturn(TRUE);
    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            anyLong(), any(int.class)))
        .thenReturn(instructions);
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);
    doReturn(maxAllowedTime)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            Integer.valueOf(facilityNum), ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR);
    doReturn(actualTime)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            Integer.valueOf(facilityNum), ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR);
    wfsCompleteDeliveryProcessor.autoCompleteDeliveries(Integer.valueOf(facilityNum));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testExceptionForValidateMasterReceiptsForPoConfirmation() throws ReceivingException {
    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(Long.valueOf(deliveryNumber));
    receipt.setPurchaseReferenceNumber(poNbr);
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setOsdrMaster(1);
    receipt.setFinalizeTs(null);
    receipts.add(receipt);

    doReturn(TRUE).when(tenantSpecificConfigReader).isPoConfirmationFlagEnabled(anyInt());
    doReturn(receipts)
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(anyLong(), anyInt());
    wfsCompleteDeliveryProcessor.validateMasterReceiptsForPoConfirmation(deliveryNumber);
  }

  @Test
  public void testValidateMasterReceiptsForPoConfirmation() throws ReceivingException {
    List<Receipt> receipts = new ArrayList<>();
    doReturn(TRUE).when(tenantSpecificConfigReader).isPoConfirmationFlagEnabled(anyInt());
    doReturn(receipts)
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(anyLong(), anyInt());
    wfsCompleteDeliveryProcessor.validateMasterReceiptsForPoConfirmation(deliveryNumber);
  }
}
