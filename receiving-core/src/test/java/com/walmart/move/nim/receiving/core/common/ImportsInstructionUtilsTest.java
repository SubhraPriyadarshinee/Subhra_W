package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ITEM_CONFIG_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.ITEM_CONFIG_SERVICE_NOT_ENABLED_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.ITEM_NOT_CONVERTED_TO_ATLAS_ERROR_MSG;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.ImportSlottingServiceImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests to validate methods in ImportsInstruction utils having logic */
public class ImportsInstructionUtilsTest {
  @InjectMocks private static ImportsInstructionUtils importsInstructionUtils;
  @Mock private static TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private static ItemConfigApiClient itemConfigApiClient;
  @Mock private AppConfig appConfig;
  @Mock private ImportSlottingServiceImpl importSlottingService;

  @BeforeMethod
  public void beforeMethod() {
    MockitoAnnotations.openMocks(this);
  }

  @BeforeMethod
  public void importsInstructionUtils() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32888);
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(appConfig);
  }

  @Test
  public void testIfAtlasConvertedItemIsSetToTrue() throws IOException, ReceivingException {
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
      deliveryDocumentLine.setItemNbr(572730130L);
    }
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ITEM_CONFIG_SERVICE_ENABLED)))
        .thenReturn(true);
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    when(itemConfigApiClient.isAtlasConvertedItem(572730130L, mockHeaders)).thenReturn(true);
    importsInstructionUtils.validateAndSetIfAtlasConvertedItem(deliveryDocument, mockHeaders);
    for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
      assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
    }
  }

  @Test
  public void testIfReceivingExceptionIsThrownIfItemConfigServiceIsNotEnabled() throws IOException {

    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ITEM_CONFIG_SERVICE_ENABLED)))
        .thenReturn(false);
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    try {
      importsInstructionUtils.validateAndSetIfAtlasConvertedItem(deliveryDocument, mockHeaders);
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorMessage(), ITEM_CONFIG_SERVICE_NOT_ENABLED_ERROR_MSG);
      assertEquals(err.getErrorCode(), ITEM_CONFIG_ERROR_CODE);
      assertEquals(e.getHttpStatus(), CONFLICT);
    }
  }

  @Test
  public void testIfReceivingExceptionIsThrownIfItemIsNotAtlasConverted() throws IOException {
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
      deliveryDocumentLine.setItemNbr(572730131L);
    }
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ITEM_CONFIG_SERVICE_ENABLED)))
        .thenReturn(true);
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    try {
      when(itemConfigApiClient.isAtlasConvertedItem(572730131L, mockHeaders)).thenReturn(false);
      importsInstructionUtils.validateAndSetIfAtlasConvertedItem(deliveryDocument, mockHeaders);
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorMessage(), ITEM_NOT_CONVERTED_TO_ATLAS_ERROR_MSG);
      assertEquals(err.getErrorCode(), ITEM_CONFIG_ERROR_CODE);
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
    }
  }

  @Test
  public void test_potypescheck_true() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setPoTypeCode(20);
    when(appConfig.getPoTypesForStorageCheck()).thenReturn(Arrays.asList(20));
    Boolean result = importsInstructionUtils.isStorageTypePo(deliveryDocument);
    assertEquals(result, Boolean.TRUE);
  }

  @Test
  public void test_potypescheck_false() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setPoTypeCode(30);
    when(appConfig.getPoTypesForStorageCheck()).thenReturn(Arrays.asList(20));
    Boolean result = importsInstructionUtils.isStorageTypePo(deliveryDocument);
    assertEquals(result, Boolean.FALSE);
  }

  @Test
  public void getPrimeSlotTest() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getImportHeaders();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine line = new DeliveryDocumentLine();
    line.setItemNbr(Long.valueOf("12345"));
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(line));
    InstructionRequest request = new InstructionRequest();
    request.setMessageId("123-123-323");
    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);
    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);
    doReturn(mockSlottingResponseBody)
        .when(importSlottingService)
        .getPrimeSlot("123-123-323", Arrays.asList(Long.valueOf(12345)), 0, httpHeaders);
    importsInstructionUtils.getPrimeSlot(request, deliveryDocument, httpHeaders);
  }
}
