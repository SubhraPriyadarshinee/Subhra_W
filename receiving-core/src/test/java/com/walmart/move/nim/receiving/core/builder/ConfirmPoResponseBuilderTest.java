package com.walmart.move.nim.receiving.core.builder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DCFinPOCloseRequestBody;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DocumentMeta;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import java.util.ArrayList;
import java.util.List;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConfirmPoResponseBuilderTest {

  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;

  @Mock private GDMRestApiClient gdmRestApiClient;

  @Mock private DCFinRestApiClient mockDCFinRestApiClient;

  @Mock private ReceiptService receiptService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private WitronDeliveryMetaDataService witronDeliveryMetaDataService;

  @InjectMocks private ConfirmPoResponseBuilder confirmPoResponseBuilder;

  @Captor private ArgumentCaptor<DCFinPOCloseRequestBody> dcFinPOCloseRequestBodyCaptor;

  private DocumentMeta mockDocumentMeta, invalidDocumentMeta;
  private List<ReceiptSummaryResponse> receivedQtySummaryByPOForDelivery;

  @BeforeMethod
  public void createConfirmPoResponseBuilder() throws Exception {
    MockitoAnnotations.initMocks(this);

    receivedQtySummaryByPOForDelivery = new ArrayList<>();
    receivedQtySummaryByPOForDelivery.add(new ReceiptSummaryResponse("123456", 1, 10l));
    receivedQtySummaryByPOForDelivery.add(new ReceiptSummaryResponse("123456", 2, 20l));
    receivedQtySummaryByPOForDelivery.add(new ReceiptSummaryResponse("987654", 1, 90l));
    receivedQtySummaryByPOForDelivery.add(new ReceiptSummaryResponse("987654", 2, 80l));

    mockDocumentMeta = new DocumentMeta();
    mockDocumentMeta.setPurchaseReferenceNumber("123456");
    mockDocumentMeta.setPoType("20");

    invalidDocumentMeta = new DocumentMeta();
    invalidDocumentMeta.setPoType("0"); // metadata will not be null
  }

  @Test
  public void test_invalidDocumentMeta_null_InvalidPoType() {
    doReturn(receivedQtySummaryByPOForDelivery)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());

    doReturn(null)
        .when(witronDeliveryMetaDataService)
        .findPurchaseOrderDetails(anyString(), anyString());

    try {
      confirmPoResponseBuilder.getDcFinPOCloseRequestBody(
          1234l, "5678", GdcHttpHeaders.getMockHeadersMap(), 100);
      fail("Not expected to reach this line instead expected to go to exception block");
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Unable to confirm this PO. Please contact your supervisor or support");
      assertEquals(e.getErrorResponse().getErrorCode(), "unableToConfirm");
    }
  }

  @Test
  public void testClosePO_closePO_isAsync_false()
      throws DCFinRestApiClientException, ReceivingException {
    doReturn(receivedQtySummaryByPOForDelivery)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    doReturn(invalidDocumentMeta)
        .when(witronDeliveryMetaDataService)
        .findPurchaseOrderDetails(anyString(), anyString());
    doNothing().when(mockDCFinRestApiClient).poClose(any(), any());

    confirmPoResponseBuilder.closePO(
        new DCFinPOCloseRequestBody(), GdcHttpHeaders.getMockHeadersMap(), false);
    verify(mockDCFinRestApiClient, atLeastOnce()).poClose(any(), any());
    verify(mockDCFinRestApiClient, times(0)).poCloseAsync(any(), any());
  }

  @Test
  public void testClosePO_closePO_isAsync_true()
      throws DCFinRestApiClientException, ReceivingException {
    doReturn(receivedQtySummaryByPOForDelivery)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    doReturn(invalidDocumentMeta)
        .when(witronDeliveryMetaDataService)
        .findPurchaseOrderDetails(anyString(), anyString());
    doNothing().when(mockDCFinRestApiClient).poClose(any(), any());

    confirmPoResponseBuilder.closePO(
        new DCFinPOCloseRequestBody(), GdcHttpHeaders.getMockHeadersMap(), true);

    verify(mockDCFinRestApiClient, times(0)).poClose(any(), any());
    verify(mockDCFinRestApiClient, times(1)).poCloseAsync(any(), any());
  }
}
