package com.walmart.move.nim.receiving.witron.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeliveryCacheServiceInMemoryImplTest {

  @Mock private DeliveryService deliveryService;

  private Gson gson;
  private Cache<Long, Map<DeliveryCacheKey, DeliveryCacheValue>> gdmPoLineResponseCache;

  @InjectMocks private DeliveryCacheServiceInMemoryImpl deliveryCacheServiceInMemoryImpl;

  @BeforeMethod
  public void createDeliveryCacheServiceInMemoryImpl() throws Exception {
    MockitoAnnotations.initMocks(this);

    gson = new Gson();

    gdmPoLineResponseCache = CacheBuilder.newBuilder().maximumSize(100).build();

    ReflectionTestUtils.setField(deliveryCacheServiceInMemoryImpl, "gson", gson);
    ReflectionTestUtils.setField(
        deliveryCacheServiceInMemoryImpl, "deliveryByPoPOLineCache", gdmPoLineResponseCache);
    ReflectionTestUtils.setField(
        deliveryCacheServiceInMemoryImpl, "deliveryService", deliveryService);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3");
  }

  @Test
  public void testGetDeliveryByDeliveryNumber() throws Exception {

    doAnswer(
            new Answer<String>() {
              public String answer(InvocationOnMock invocation) {
                Long deliveryNumber = (Long) invocation.getArguments()[0];
                return gson.toJson(buildGdmPOLineResponse(deliveryNumber));
              }
            })
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    IntStream.rangeClosed(1, 5)
        .forEachOrdered(
            runs -> {
              IntStream.rangeClosed(1, 8)
                  .forEachOrdered(
                      poNum -> {
                        IntStream.rangeClosed(1, 10)
                            .forEachOrdered(
                                lineNum -> {
                                  try {
                                    DeliveryCacheValue response1 =
                                        deliveryCacheServiceInMemoryImpl
                                            .getDeliveryDetailsByPoPoLine(
                                                12345l,
                                                poNum + "",
                                                lineNum,
                                                ReceivingUtils.getHeaders());
                                    assertNotNull(response1);
                                  } catch (ReceivingException e) {
                                    e.printStackTrace();
                                    fail(e.getMessage());
                                  }
                                });
                      });
            });

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testGetGdmPOLineResponseByDeliveryNumber_exception() throws Exception {

    doThrow(new ReceivingException("mock exception"))
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    try {
      DeliveryCacheValue response1 =
          deliveryCacheServiceInMemoryImpl.getDeliveryDetailsByPoPoLine(
              12345l, "123", 1, ReceivingUtils.getHeaders());
    } catch (ReceivingException e) {

      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.GDM_SERVICE_DOWN);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    }
  }

  private GdmPOLineResponse buildGdmPOLineResponse(Long deliveryNumber) {

    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryNumber(deliveryNumber);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    IntStream.rangeClosed(1, 8)
        .forEachOrdered(
            poNum -> {
              DeliveryDocument deliveryDocument = new DeliveryDocument();
              deliveryDocument.setPurchaseReferenceNumber(poNum + "");
              deliveryDocument.setTotalBolFbq(100);

              List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
              IntStream.rangeClosed(1, 10)
                  .forEachOrdered(
                      lineNum -> {
                        DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
                        deliveryDocumentLine.setPurchaseReferenceNumber(poNum + "");
                        deliveryDocumentLine.setPurchaseReferenceLineNumber(lineNum);
                        deliveryDocumentLine.setBolWeight(123.45f);
                        deliveryDocumentLines.add(deliveryDocumentLine);
                      });
              deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
              deliveryDocuments.add(deliveryDocument);
            });
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);

    return gdmPOLineResponse;
  }

  @Test
  public void testGetDeliveryDetailsByPoPoLine() throws Exception {
    try {
      Long deliveryNumber = Long.parseLong("26355167");
      String purchaseReferenceNumber = "074384333";
      Integer poLine = 1;

      Map<DeliveryCacheKey, DeliveryCacheValue> valueMap = new HashMap<>();
      valueMap.put(new DeliveryCacheKey(), new DeliveryCacheValue());
      gdmPoLineResponseCache.put(deliveryNumber, valueMap);

      List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
      DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
      deliveryDocumentLine.setPurchaseReferenceNumber(purchaseReferenceNumber);
      deliveryDocumentLine.setPurchaseReferenceLineNumber(poLine);
      deliveryDocumentLine.setBolWeight(30.81f);
      deliveryDocumentLines.add(deliveryDocumentLine);

      DeliveryDocument deliveryDocument = new DeliveryDocument();
      deliveryDocument.setPurchaseReferenceNumber(purchaseReferenceNumber);
      deliveryDocument.setTotalBolFbq(120);
      deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

      List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
      deliveryDocuments.add(deliveryDocument);

      GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
      gdmPOLineResponse.setDeliveryNumber(deliveryNumber);
      gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);

      doAnswer(
              new Answer<String>() {
                public String answer(InvocationOnMock invocation) {
                  return gson.toJson(gdmPOLineResponse);
                }
              })
          .when(deliveryService)
          .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

      DeliveryCacheValue deliveryCacheValue =
          deliveryCacheServiceInMemoryImpl.getDeliveryDetailsByPoPoLine(
              deliveryNumber, purchaseReferenceNumber, poLine, GdcHttpHeaders.getHeaders());

      assertNotNull(deliveryCacheValue);
      verify(deliveryService, times(1))
          .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
    } catch (ReceivingException e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}
