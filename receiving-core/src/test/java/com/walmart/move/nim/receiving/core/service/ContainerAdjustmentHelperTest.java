package com.walmart.move.nim.receiving.core.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.Date;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ContainerAdjustmentHelperTest extends ReceivingTestBase {

  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Mock private AppConfig appConfig;
  @Mock private InstructionRepository instructionRepository;

  @InjectMocks private ContainerAdjustmentHelper containerAdjustmentHelper;

  private HttpHeaders httpHeaders;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String trackingId = "lpn1";
  private final JsonParser jsonParser = new JsonParser();

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(containerAdjustmentHelper, "jsonParser", jsonParser);
  }

  @AfterMethod
  public void shutdownMocks() {
    reset(
        deliveryService,
        receiptService,
        containerPersisterService,
        containerAdjustmentValidator,
        appConfig,
        instructionRepository);
  }

  @Test
  public void test_validateContainerForAdjustment_backout_status() throws ReceivingException {
    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.OPN.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    when(containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(
            any(Container.class)))
        .thenReturn(
            new CancelContainerResponse(
                trackingId,
                ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE,
                ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345L);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(mockContainer, httpHeaders);

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_putaway_complete_status()
      throws ReceivingException {
    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.OPN.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    when(containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(
            any(Container.class)))
        .thenReturn(
            new CancelContainerResponse(
                trackingId,
                ReceivingException.CONTAINER_ALREADY_SLOTTED_ERROR_CODE,
                ReceivingException.CONTAINER_ALREADY_SLOTTED_ERROR_MSG));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345L);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(mockContainer, httpHeaders);

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CONTAINER_ALREADY_SLOTTED_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CONTAINER_ALREADY_SLOTTED_ERROR_MSG);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_empty_container_items()
      throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.OPN.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    when(containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(
            any(Container.class)))
        .thenReturn(
            new CancelContainerResponse(
                trackingId,
                ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_CODE,
                ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345L);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(mockContainer, httpHeaders);

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_CODE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_delivery_fnl_status() throws ReceivingException {

    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.FNL.name());

    doReturn(mockDeliveryResponse.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345L);
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(mockContainer, httpHeaders);

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(),
        ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void test_validateContainerForAdjustment_gdm_error() throws ReceivingException {

    doThrow(new ReceivingException("MOCK_ERROR_MESSAGE"))
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345L);
    mockContainer.setTrackingId(trackingId);

    CancelContainerResponse validateContainerForAdjustmentResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(mockContainer, httpHeaders);

    assertNotNull(validateContainerForAdjustmentResponse);

    assertEquals(
        validateContainerForAdjustmentResponse.getErrorCode(), ExceptionCodes.GDM_NOT_ACCESSIBLE);
    assertEquals(
        validateContainerForAdjustmentResponse.getErrorMessage(),
        ReceivingException.GDM_SERVICE_DOWN);

    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testAdjustReceipts() {
    Receipt receipt = containerAdjustmentHelper.adjustReceipts(getMockContainer());
    assertNotNull(receipt);
    assertEquals(receipt.getQuantity(), Integer.valueOf(-20));
    assertEquals(receipt.getEachQty(), Integer.valueOf(-80));
  }

  @Test
  public void testAdjustReceiptsWithQuantity() {
    Receipt receipt = containerAdjustmentHelper.adjustReceipts(getMockContainer(), 1, 1);
    assertNotNull(receipt);
    assertEquals(receipt.getQuantity(), Integer.valueOf(-1));
    assertEquals(receipt.getEachQty(), Integer.valueOf(-1));
  }

  @Test
  public void testPersistAdjustedContainerAndReceiptsIsSuccess() {
    Container container = getMockContainer();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    Receipt receipt = containerAdjustmentHelper.adjustReceipts(container);

    when(receiptService.saveReceipt(any(Receipt.class))).thenReturn(new Receipt());
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(new Container());

    containerAdjustmentHelper.persistAdjustedReceiptsAndContainer(receipt, container);

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);

    verify(receiptService).saveReceipt(receiptCaptor.capture());
    verify(containerPersisterService).saveContainer(containerCaptor.capture());

    assertNotNull(receiptCaptor.getValue());
    assertEquals(receiptCaptor.getValue().getQuantity(), Integer.valueOf(-20));
    assertEquals(receiptCaptor.getValue().getEachQty(), Integer.valueOf(-80));

    assertNotNull(containerCaptor.getValue());
    assertTrue(
        containerCaptor
            .getValue()
            .getContainerStatus()
            .equalsIgnoreCase(ReceivingConstants.STATUS_BACKOUT));
  }

  @Test
  public void testAdjustPalletQuantityWithPositiveNumberAdjustmentsReturnsUpdatedPallet() {
    Container container =
        containerAdjustmentHelper.adjustPalletQuantity(
            6, getMockContainer(), ReceivingConstants.DEFAULT_USER);
    assertNotNull(container);
    assertTrue(container.getLastChangedUser().equalsIgnoreCase(ReceivingConstants.DEFAULT_USER));
    assertNotNull(container.getLastChangedTs());
    assertNotNull(container.getContainerItems());
    assertEquals(container.getContainerItems().get(0).getQuantity(), Integer.valueOf(6));
  }

  @Test
  public void testAdjustPalletQuantityWithNegativeNumberAdjustmentsReturnsUpdatedPallet() {
    Container container =
        containerAdjustmentHelper.adjustPalletQuantity(
            60, getMockContainer(), ReceivingConstants.DEFAULT_USER);
    assertNotNull(container);
    assertTrue(container.getLastChangedUser().equalsIgnoreCase(ReceivingConstants.DEFAULT_USER));
    assertNotNull(container.getLastChangedTs());
    assertNotNull(container.getContainerItems());
    assertEquals(container.getContainerItems().get(0).getQuantity(), Integer.valueOf(60));
  }

  @Test
  public void testAdjustQuantityInReceiptWithPositiveAdjustmentReturnsUpdatedReceipt() {
    Receipt receipt =
        containerAdjustmentHelper.adjustQuantityInReceipt(
            15, ReceivingConstants.Uom.VNPK, getMockContainer(), "sysadmin");
    assertNotNull(receipt);
    assertEquals(receipt.getQuantity(), Integer.valueOf(-5));
    assertEquals(receipt.getEachQty(), Integer.valueOf(-20));
  }

  @Test
  public void testAdjustQuantityInReceiptWithNegativeAdjustmentReturnsUpdatedReceipt() {
    Receipt receipt =
        containerAdjustmentHelper.adjustQuantityInReceipt(
            -15, ReceivingConstants.Uom.VNPK, getMockContainer(), "sysadmin");
    assertNotNull(receipt);
    assertEquals(receipt.getQuantity(), Integer.valueOf(-5));
    assertEquals(receipt.getEachQty(), Integer.valueOf(-20));
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }
}
