package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_GET_DELIVERY_BY_URI;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_GET_DELIVERY_ERROR;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaMessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.ReceivingProgressMessage;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.*;

public class AsyncPoReceivingProgressPublisherTest {
  private Gson gson = new Gson();
  @Mock private ReceiptRepository receiptRepository;
  @Mock private AppConfig appConfig;
  @Mock private KafkaMessagePublisher kafkaMessagePublisher;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @InjectMocks private AsyncPoReceivingProgressPublisher asyncPoReceivingProgressPublisher;
  Long deliveryNUmber = 12345L;
  String poNumber = "4211300997";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        deliveryService,
        deliveryMetaDataRepository,
        kafkaMessagePublisher,
        appConfig,
        receiptRepository);
  }

  @Test(dataProvider = "QuantityReceived")
  public void testPublishPoReceiveProgress_Positive1(Integer quantityReceived)
      throws ReceivingException, IOException {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("12345")
            .receiveProgress(
                "{\"totalDeliveryQty\":1860,\"poProgressDetailsList\":[{\"poReceivedPercentage\":35,\"totalPoQty\":160,\"qtyUOM\":\"ZA\",\"poNumber\":\"4211300997\"},{\"poReceivedPercentage\":25,\"totalPoQty\":900,\"qtyUOM\":\"ZA\",\"poNumber\":\"1707204860\"}]}")
            .build();
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenReturn(quantityReceived);
    when(receiptRepository.getTotalReceivedQuantityByDeliveryNumber(anyLong())).thenReturn(100);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());
    ArgumentCaptor<Object> receivingProgressMessageArgumentCaptor =
        ArgumentCaptor.forClass(Object.class);
    verify(appConfig, times(0)).getGdmBaseUrl();
    verify(receiptRepository, times(1))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(1)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(1))
        .publish(
            anyString(),
            receivingProgressMessageArgumentCaptor.capture(),
            isNull(),
            any(HashMap.class));
    ReceivingProgressMessage receivingProgressMessage =
        (ReceivingProgressMessage) receivingProgressMessageArgumentCaptor.getValue();
    assertEquals(
        "sysadmin",
        receivingProgressMessage.getPayload().getUnLoaderDetails().get(0).getUnLoaderId());
  }

  @Test
  public void testPublishPoReceiveProgress_noPublish_Positive2()
      throws ReceivingException, IOException {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("12345")
            .receiveProgress(
                "{\"totalDeliveryQty\":1860,\"poProgressDetailsList\":[{\"poReceivedPercentage\":5,\"totalPoQty\":960,\"qtyUOM\":\"ZA\",\"poNumber\":\"4211300997\"},{\"poReceivedPercentage\":25,\"totalPoQty\":900,\"qtyUOM\":\"ZA\",\"poNumber\":\"1707204860\"}]}")
            .build();
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenReturn(20);
    when(receiptRepository.getTotalReceivedQuantityByDeliveryNumber(anyLong())).thenReturn(100);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(0)).getGdmBaseUrl();
    verify(receiptRepository, times(1))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(0)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(0))
        .publish(anyString(), any(ReceivingProgressMessage.class), isNull(), any(HashMap.class));
  }

  @Test
  public void testPublishPoReceiveProgress_noPublish_Positive3()
      throws ReceivingException, IOException {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("12345")
            .receiveProgress(
                "{\"totalDeliveryQty\":1860,\"poProgressDetailsList\":[{\"poReceivedPercentage\":25,\"totalPoQty\":960,\"qtyUOM\":\"ZA\",\"poNumber\":\"4211300997\"},{\"poReceivedPercentage\":25,\"totalPoQty\":900,\"qtyUOM\":\"ZA\",\"poNumber\":\"1707204860\"}]}")
            .build();
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenReturn(0);
    when(receiptRepository.getTotalReceivedQuantityByDeliveryNumber(anyLong())).thenReturn(100);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(0)).getGdmBaseUrl();
    verify(deliveryService, times(0)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
    verify(receiptRepository, times(1))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(0)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(0))
        .publish(anyString(), any(ReceivingProgressMessage.class), isNull(), any(HashMap.class));
  }

  @Test(dataProvider = "QuantityReceivedAndSentProgress")
  public void testPublishPoReceiveProgress_Positive4(
      Integer quantityReceived, String receiveProgress) throws ReceivingException, IOException {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder().deliveryNumber("12345").receiveProgress(receiveProgress).build();
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenReturn(quantityReceived);
    when(receiptRepository.getTotalReceivedQuantityByDeliveryNumber(anyLong())).thenReturn(100);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptRepository, times(1))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(1)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(1))
        .publish(anyString(), any(ReceivingProgressMessage.class), isNull(), any(HashMap.class));
  }

  @Test
  public void testPublishPoReceiveProgress_gdmException_negative1()
      throws ReceivingException, IOException {
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(GDM_GET_DELIVERY_ERROR)
            .errorCode(GDM_GET_DELIVERY_BY_URI)
            .errorKey(ExceptionCodes.GDM_GET_DELIVERY_ERROR)
            .build();
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenReturn(20);
    when(receiptRepository.getTotalReceivedQuantityByDeliveryNumber(anyLong())).thenReturn(100);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenThrow(
            ReceivingException.builder()
                .httpStatus(NOT_FOUND)
                .errorResponse(errorResponse)
                .build());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptRepository, times(0))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(0)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(0))
        .publish(anyString(), any(ReceivingProgressMessage.class), isNull(), any(HashMap.class));
  }

  @Test
  public void testPublishPoReceiveProgress_dbError_negative2()
      throws ReceivingException, IOException {

    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenThrow(new NullPointerException());
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
    verify(receiptRepository, times(1))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(0)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(0))
        .publish(anyString(), any(ReceivingProgressMessage.class), isNull(), any(HashMap.class));
  }

  @Test
  public void testPublishPoReceiveProgress_dbError_negative3()
      throws ReceivingException, IOException {

    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenThrow(new NullPointerException());
    when(receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString()))
        .thenThrow(new NullPointerException());
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    doNothing()
        .when(kafkaMessagePublisher)
        .publish(anyString(), any(Object.class), anyString(), any(HashMap.class));

    asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
        deliveryNUmber, poNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptRepository, times(1))
        .getTotalReceivedQuantityByPOAndDeliveryNumber(anyLong(), anyString());
    verify(receiptRepository, times(0)).getTotalReceivedQuantityByDeliveryNumber(anyLong());
    verify(kafkaMessagePublisher, times(0))
        .publish(anyString(), any(ReceivingProgressMessage.class), isNull(), any(HashMap.class));
  }

  @DataProvider(name = "QuantityReceived")
  public static Object[][] quantityReceived() {
    return new Object[][] {{40}, {68}, {110}, {126}};
  }

  @DataProvider(name = "QuantityReceivedAndSentProgress")
  public static Object[][] quantityReceivedAndSentProgress() {
    return new Object[][] {
      {40, "{\"4211300997\":\"" + "25" + "\"}"},
      {68, "{\"4211300997\":\"" + "50" + "\"}"},
      {110, "{\"4211300997\":\"" + "75" + "\"}"},
      {126, "{\"4211300997\":\"" + "100" + "\"}"}
    };
  }
}
