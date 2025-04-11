package com.walmart.move.nim.receiving.core.event.processor.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryProcessUpdateProcessorTest {
  String OPERATION_TYPE = "operationType";
  String PALLET_TYPE = "palletType";
  @InjectMocks private DeliveryProcessUpdateProcessor deliveryProcessUpdateProcessor;
  @Mock private AppConfig appConfig;
  @Mock private ReceiptService receiptService;
  @Mock private KafkaTemplate kafkaTemplate;
  private Gson gson;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(Integer.valueOf("32679"));
    TenantContext.setCorrelationId("aafa2fcc-d299-4663-aa64-ba6f79704635");
    gson = new Gson();
    ReflectionTestUtils.setField(deliveryProcessUpdateProcessor, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(kafkaTemplate, receiptService, appConfig);
  }

  @Test
  void testDoExecute_Success() throws ReceivingException {
    ReceivingEvent receivingEvent = getReceivingEvent();
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        getReceiptSummaryQtyByPoResponse();
    when(receiptService.getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryQtyByPoResponse);
    deliveryProcessUpdateProcessor.doExecute(receivingEvent);
    verify(receiptService, times(1))
        .getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoInVnpk(any(Long.class));
  }

  @Test
  void testDoExecute_Success_EmptyPurchaseOrders() throws ReceivingException {
    ReceivingEvent receivingEvent = getReceivingEvent();
    BOLDatum bolDatum = new BOLDatum(123L, "123");
    GdmPOLineResponse gdmPOLineResponse = mockGdmPOLineResponse();
    gdmPOLineResponse.getDeliveryDocuments().get(0).setBolNumbers(Arrays.asList(bolDatum));
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        getReceiptSummaryQtyByPoResponse();
    receiptSummaryQtyByPoResponse.setGdmPOLineResponse(gdmPOLineResponse);
    receiptSummaryQtyByPoResponse.setSummary(Collections.emptyList());
    receiptSummaryQtyByPoResponse.getGdmPOLineResponse().setWorkingUserId("User");
    when(receiptService.getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryQtyByPoResponse);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    receiptSummaryResponse.setReceivedQty(123L);
    receiptSummaryResponseList.add(receiptSummaryResponse);
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(receiptSummaryResponseList);
    TenantContext.setCorrelationId(null);
    deliveryProcessUpdateProcessor.doExecute(receivingEvent);
    verify(receiptService, times(1))
        .getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(any(Long.class));
  }

  @Test
  void testDoExecute_ReceivingException() throws ReceivingException {
    ReceivingEvent receivingEvent = getReceivingEvent();
    when(receiptService.getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class)))
        .thenThrow(new ReceivingException("Error", HttpStatus.NOT_FOUND));
    deliveryProcessUpdateProcessor.doExecute(receivingEvent);
    verify(receiptService, times(1))
        .getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoInVnpk(any(Long.class));
  }

  @Test
  void isAsync() {
    when(appConfig.isYmsUpdateAsyncEnable()).thenReturn(Boolean.valueOf("true"));
    boolean isYmsUpdateAsyncEnable = deliveryProcessUpdateProcessor.isAsync();
    assertTrue(isYmsUpdateAsyncEnable);
  }

  private ReceivingEvent getReceivingEvent() {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(
        ReceivingConstants.EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES, new Integer(20));
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_CORRECTION_EVENT);
    return ReceivingEvent.builder()
        .payload(JacksonParser.writeValueAsString(getContainerDTO()))
        .processor(ReceivingConstants.CORRECTION_CONTAINER_EVENT_PROCESSOR)
        .additionalAttributes(additionalAttribute)
        .build();
  }

  private ContainerDTO getContainerDTO() {
    Map<String, Object> miscInfo = new HashMap<>();
    miscInfo.put(OPERATION_TYPE, "Normal");
    miscInfo.put(PALLET_TYPE, "MFC");
    return ContainerDTO.builder()
        .trackingId("9876543210")
        .deliveryNumber(123456789L)
        .ssccNumber("dummySSCC")
        .containerMiscInfo(miscInfo)
        .containerItems(new ArrayList<>())
        .shipmentId("1234567890")
        .build();
  }

  private ReceiptSummaryQtyByPoResponse getReceiptSummaryQtyByPoResponse() {
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        new ReceiptSummaryQtyByPoResponse();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos = new ArrayList<>();
    ReceiptSummaryQtyByPo receiptSummaryQtyByPo = new ReceiptSummaryQtyByPo();
    receiptSummaryQtyByPo.setPurchaseReferenceNumber("12345");
    receiptSummaryQtyByPo.setFreightBillQuantity(12);
    receiptSummaryQtyByPo.setReceivedQty(6);
    receiptSummaryQtyByPos.add(receiptSummaryQtyByPo);
    GdmPOLineResponse gdmPOLineResponse = mockGdmPOLineResponse();
    gdmPOLineResponse.getDeliveryDocuments().get(0).setBolNumbers(null);
    receiptSummaryQtyByPoResponse.setDeliveryNumber(21119003L);
    receiptSummaryQtyByPoResponse.setReceivedQty(100);
    receiptSummaryQtyByPoResponse.setSummary(receiptSummaryQtyByPos);
    receiptSummaryQtyByPoResponse.setGdmPOLineResponse(gdmPOLineResponse);
    receiptSummaryQtyByPoResponse.setSummary(receiptSummaryQtyByPos);
    return receiptSummaryQtyByPoResponse;
  }

  private GdmPOLineResponse mockGdmPOLineResponse() {
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    ItemData additionalInfo = new ItemData();
    deliveryDocumentLine.setVendorNbrDeptSeq(40);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(10);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLine.setVendorPackCost(100F);
    deliveryDocumentLine.setWarehousePackSell(100F);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setPurchaseReferenceNumber("12345");
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setDeptNumber("40");
    deliveryDocument.setTotalPurchaseReferenceQty(123);
    deliveryDocuments.add(deliveryDocument);
    gdmPOLineResponse.setDeliveryNumber(12345L);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);
    return gdmPOLineResponse;
  }
}
