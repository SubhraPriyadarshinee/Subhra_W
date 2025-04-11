package com.walmart.move.nim.receiving.core.service;

import static java.lang.Integer.valueOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.GdcPutawayPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.ContainerRequest;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReceiveContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private ReceiveContainerService receiveContainerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceiptService receiptService;
  @Mock private ContainerService containerService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;

  private Receipt receipt;
  private Container container;
  private Long deliveryNumber = 21231313L;
  private List<Receipt> receipts = new ArrayList<>();
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Map<String, Object> maasHeaders;
  private String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
  private String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
  private ContainerRequest containerRequest = MockContainer.getContainerRequest();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(valueOf(facilityNum));

    maasHeaders = ReceivingUtils.getForwardablHeader(httpHeaders);
    maasHeaders.put(ReceivingConstants.IDEM_POTENCY_KEY, containerRequest.getTrackingId());

    receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setDoorNumber(containerRequest.getLocation());
    receipt.setPurchaseReferenceNumber(
        containerRequest.getContents().get(0).getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(
        containerRequest.getContents().get(0).getPurchaseReferenceLineNumber());
    receipt.setQuantity(containerRequest.getContents().get(0).getQuantity());
    receipt.setQuantityUom(containerRequest.getContents().get(0).getQuantityUom());
    receipts.add(receipt);

    container = new Container();
    container.setDeliveryNumber(deliveryNumber);
    container.setMessageId(containerRequest.getMessageId());
    container.setTrackingId(containerRequest.getTrackingId());
    container.setLocation(containerRequest.getLocation());
    container.setContainerType("PALLET");
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(receiptService);
    reset(receiptRepository);
    reset(containerService);
    reset(containerPersisterService);
    reset(gdcPutawayPublisher);
  }

  @Test
  public void testReceiveContainer_WitronPutaway() throws ReceivingException {
    when(receiptService.prepareReceipts(deliveryNumber, containerRequest, userId))
        .thenReturn(receipts);
    when(containerService.prepareContainer(deliveryNumber, containerRequest, userId))
        .thenReturn(container);
    doNothing().when(containerPersisterService).createReceiptAndContainer(receipts, container);
    doNothing().when(containerService).publishContainer(container, maasHeaders);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

    Container containerResponse =
        receiveContainerService.receiveContainer(deliveryNumber, containerRequest, httpHeaders);

    assertEquals(containerResponse.getDeliveryNumber(), deliveryNumber);
    assertEquals(containerResponse.getMessageId(), containerRequest.getMessageId());
    assertEquals(containerResponse.getTrackingId(), containerRequest.getTrackingId());

    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void testReceiveContainer_DefaultPutaway() throws ReceivingException {
    when(receiptService.prepareReceipts(deliveryNumber, containerRequest, userId))
        .thenReturn(receipts);
    when(containerService.prepareContainer(deliveryNumber, containerRequest, userId))
        .thenReturn(container);
    doNothing().when(containerPersisterService).createReceiptAndContainer(receipts, container);
    doNothing().when(containerService).publishContainer(container, maasHeaders);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

    Container containerResponse =
        receiveContainerService.receiveContainer(deliveryNumber, containerRequest, httpHeaders);

    assertEquals(containerResponse.getDeliveryNumber(), deliveryNumber);
    assertEquals(containerResponse.getMessageId(), containerRequest.getMessageId());
    assertEquals(containerResponse.getTrackingId(), containerRequest.getTrackingId());

    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void testReceiveContainerFailed() {
    try {
      when(receiptService.prepareReceipts(deliveryNumber, containerRequest, userId))
          .thenReturn(receipts);
      when(containerService.prepareContainer(deliveryNumber, containerRequest, userId))
          .thenReturn(container);
      doThrow(RuntimeException.class)
          .when(containerPersisterService)
          .createReceiptAndContainer(receipts, container);

      receiveContainerService.receiveContainer(deliveryNumber, containerRequest, httpHeaders);
      assert (false);
    } catch (Exception exception) {
      assert (true);
    }
  }
}
