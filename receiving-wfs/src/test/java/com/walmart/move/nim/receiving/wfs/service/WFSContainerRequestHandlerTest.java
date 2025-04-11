package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_KOTLIN_CLIENT;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static org.testng.Assert.assertSame;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSContainerRequestHandlerTest {

  @Mock private ContainerService containerService;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private InventoryService inventoryService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private WFSContainerRequestHandler wfsContainerRequestHandler;

  HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerService);
    reset(containerAdjustmentHelper);
    reset(inventoryService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void test_getContainerByTrackingId() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.PALLET.name());
    when(containerService.getContainerWithChildsByTrackingId(
            anyString(), anyBoolean(), anyString()))
        .thenReturn(mockContainer);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(null);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "true");

    Container containerResponse =
        wfsContainerRequestHandler.getContainerByTrackingId(
            "97123456789", true, "EA", false, httpHeaders);

    assertNotNull(containerResponse);
    assertSame(containerResponse.getInventoryStatus(), InventoryStatus.PICKED.name());
    assertSame(containerResponse.getContainerType(), ContainerType.PALLET.name());
  }

  @Test
  public void test_getContainerByTrackingId_ThrowsException_withoutErrorCode()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    doAnswer(
            invocationOnMock -> {
              ErrorResponse errorResponse =
                  ErrorResponse.builder().errorCode("").errorMessage("").build();
              throw ReceivingException.builder().errorResponse(errorResponse).build();
            })
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean(), anyString());
    try {
      wfsContainerRequestHandler.getContainerByTrackingId(
          "97123456789", true, "EA", false, mockHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.RECEIVING_INTERNAL_ERROR);
    }
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetContainerByTrackingId() throws ReceivingException {
    when(containerService.getContainerWithChildsByTrackingId(
            anyString(), anyBoolean(), anyString()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.INVENTORY_ERROR_CODE,
                HttpStatus.BAD_REQUEST,
                ReceivingException.INVALID_CONTAINER_TYPE_ERROR_MSG));
    String trackingId = "";
    wfsContainerRequestHandler.getContainerByTrackingId(
        trackingId, Boolean.TRUE, ReceivingConstants.Uom.VNPK, false, mockHeaders);
  }

  @Test
  public void testGetContainerByTrackingId_ContainerNotFoundInDB_InventoryNotFound()
      throws ReceivingException {
    when(containerService.getContainerWithChildsByTrackingId(
            anyString(), anyBoolean(), anyString()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
                HttpStatus.NOT_FOUND,
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER));
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.INVENTORY_NOT_FOUND,
                ReceivingConstants.INVENTORY_NOT_FOUND_MESSAGE));
    try {
      wfsContainerRequestHandler.getContainerByTrackingId(
          "97123456789", true, ReceivingConstants.Uom.EACHES, false, mockHeaders);
      fail("ReceivingDataNotFoundException expected");
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(
          exc.getErrorCode(), ExceptionCodes.WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND);
    }
  }

  @Test
  public void testGetContainerByTrackingId_ContainerNotFoundInDB_DestinationContainer()
      throws ReceivingException {
    when(containerService.getContainerWithChildsByTrackingId(
            anyString(), anyBoolean(), anyString()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
                HttpStatus.NOT_FOUND,
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER));
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(new InventoryContainerDetails(12, InventoryStatus.PICKED.name(), 65610, 0));
    try {
      wfsContainerRequestHandler.getContainerByTrackingId(
          "97123456789", true, ReceivingConstants.Uom.EACHES, false, mockHeaders);
      fail("ReceivingBadDataException expected");
    } catch (ReceivingBadDataException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.WFS_INVALID_INDUCT_LPN_DESTINATION_CTR);
    }
  }

  @Test
  public void testGetContainerByTrackingId_ContainerNotFoundInDB_NotDestinationContainer()
      throws ReceivingException {
    when(containerService.getContainerWithChildsByTrackingId(
            anyString(), anyBoolean(), anyString()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
                HttpStatus.NOT_FOUND,
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
                ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER));
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(new InventoryContainerDetails(12, InventoryStatus.PICKED.name(), 0, 0));
    try {
      wfsContainerRequestHandler.getContainerByTrackingId(
          "97123456789", true, ReceivingConstants.Uom.EACHES, false, mockHeaders);
      fail("ReceivingException expected");
    } catch (ReceivingException exc) {
      assertEquals(
          exc.getErrorResponse().getErrorCode(),
          ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE);
    }
  }

  @Test
  public void testValidateContainerForMobile_Corrections_InvalidInventoryStatusAndContainerType()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "true");
    try {
      wfsContainerRequestHandler.validateContainerForMobile(mockContainer);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(), ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_PICKED_PALLET);
      assertEquals(
          exc.getDescription(),
          WFSConstants.INVALID_CONTAINER_FOR_WFS_INV_STATUS_AND_CTR_TYPE_EXCEPTION_MSG);
    }
  }

  @Test
  public void testValidateContainerForMobile_Corrections_BackoutContainer()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.PALLET.name());
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "true");
    try {
      wfsContainerRequestHandler.validateContainerForMobile(mockContainer);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(), ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_BACKOUT_CTR);
      assertEquals(
          exc.getDescription(), WFSConstants.INVALID_CONTAINER_FOR_WFS_CTR_STATUS_EXCEPTION_MSG);
    }
  }

  @Test
  public void testValidateContainerForWebUI_CorrectionFlow_InvalidContainerType()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setContainerType(ContainerType.PALLET.name());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, false, httpHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_CTR_TYPE_NOT_VENDORPACK);
    }
  }

  @Test
  public void testValidateContainerForWebUI_CorrectionFlow_InvalidInventoryStatus()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, false, httpHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes
              .WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_CTR_STATUS_NOT_PICKED_OR_AVAILABLE);
    }
  }

  @Test
  public void testValidateContainerForWebUI_CorrectionFlow_BackoutContainer()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, false, httpHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(), ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_BACKOUT_CTR);
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_FinalizedDelivery_FlagDisabled()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    mockContainer.setDeliveryNumber(4114290L);
    CancelContainerResponse validateDeliveryStatusResponse = new CancelContainerResponse();
    validateDeliveryStatusResponse.setErrorCode(
        ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(validateDeliveryStatusResponse);
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
    } catch (ReceivingBadDataException exc) {
      fail("No exception should be thrown!");
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_FinalizedDelivery_FlagEnabled()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.PALLET.name());
    mockContainer.setDeliveryNumber(4114290L);
    CancelContainerResponse validateDeliveryStatusResponse = new CancelContainerResponse();
    validateDeliveryStatusResponse.setErrorCode(
        ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(validateDeliveryStatusResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_DELIVERY_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT)))
        .thenReturn(Boolean.TRUE);
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_DELIVERY_FNL);
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_GDMNotAccessible_FlagEnabled() {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.PALLET.name());
    mockContainer.setDeliveryNumber(4114290L);

    CancelContainerResponse validateDeliveryStatusResponse = new CancelContainerResponse();
    validateDeliveryStatusResponse.setErrorCode(ExceptionCodes.GDM_NOT_ACCESSIBLE);

    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(validateDeliveryStatusResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_DELIVERY_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT)))
        .thenReturn(Boolean.TRUE);

    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
      fail("ReceivingException should be thrown!");
    } catch (ReceivingException exc) {
      assertEquals(exc.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(exc.getErrorResponse().getErrorCode(), ExceptionCodes.GDM_NOT_ACCESSIBLE);
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_InvalidInventoryStatus()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    mockContainer.setDeliveryNumber(4114290L);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(null);

    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_INV_STATUS_CTR_TYPE);
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_BackoutContainer()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    mockContainer.setDeliveryNumber(4114290L);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(null);
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_CTR_STATUS_BACKOUT);
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_InvalidInventoryAPIContainerStatus()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    mockContainer.setDeliveryNumber(4114290L);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(null);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_CONTAINER_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT)))
        .thenReturn(Boolean.TRUE);
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(new InventoryContainerDetails(12, InventoryStatus.PICKED.name(), 0, 0));
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
      fail("ReceivingBadDataException should be thrown!");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_INV_STATUS);
    }
  }

  @Test
  public void testValidateContainerForWebUI_RestartDecant_InventoryAPIError_InventoryNotFound()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    mockContainer.setContainerType(ContainerType.VENDORPACK.getText());
    mockContainer.setDeliveryNumber(4114290L);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any()))
        .thenReturn(null);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_CONTAINER_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT)))
        .thenReturn(Boolean.TRUE);
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.INVENTORY_NOT_FOUND,
                ReceivingConstants.INVENTORY_NOT_FOUND_MESSAGE));
    try {
      wfsContainerRequestHandler.validateContainerForWebUI(mockContainer, true, mockHeaders);
      fail("ReceivingDataNotFoundException should be thrown!");
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(
          exc.getErrorCode(), ExceptionCodes.WFS_INVALID_LABEL_FOR_RESTART_DCNT_INV_NOT_FOUND);
    }
  }
}
