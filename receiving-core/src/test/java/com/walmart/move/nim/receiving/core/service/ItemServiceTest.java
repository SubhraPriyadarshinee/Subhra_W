package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.ItemInfoResponse;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.SaveConfirmationRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ItemServiceTest extends ReceivingTestBase {

  @InjectMocks private ItemService itemService;
  @Mock private DefaultItemServiceHandler defaultItemServiceHandler;

  @Mock private ContainerRepository containerRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(12345);
    ReflectionTestUtils.setField(
        itemService,
        ItemService.class,
        "tenantSpecificConfigReader",
        tenantSpecificConfigReader,
        TenantSpecificConfigReader.class);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerRepository, tenantSpecificConfigReader, defaultItemServiceHandler);
  }

  @Test
  public void testSuccessRetrival() {

    when(containerRepository.findLatestItemByUPC(anyString(), anyString(), anyInt()))
        .thenReturn(getSuccessItemNumber());
    List<Long> itemList = itemService.findLatestItemByUPC("12345678");
    assertEquals(2, itemList.size());
    verify(containerRepository, times(1)).findLatestItemByUPC(anyString(), anyString(), anyInt());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "UPC=12345678 is not found on tenant facilityCountryCode=US facilityNum=12345.*")
  public void testFailureRetrival() {

    when(containerRepository.findLatestItemByUPC(anyString(), anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    List<Long> itemList = itemService.findLatestItemByUPC("12345678");
    verify(containerRepository, times(1)).findLatestItemByUPC(anyString(), anyString(), anyInt());
  }

  @Test
  public void testFindItemBaseDivCodeByUPC() {
    when(containerRepository.findItemBaseDivCodesByUPC(anyString(), anyString(), anyInt()))
        .thenReturn(getSuccessItemInfo());
    List<ItemInfoResponse> itemInfoResponseList = itemService.findItemBaseDivCodesByUPC("12345678");
    assertEquals(itemInfoResponseList.size(), 2);
    verify(containerRepository, times(1))
        .findItemBaseDivCodesByUPC(anyString(), anyString(), anyInt());
  }

  @Test
  public void testFindItemBaseDivCodesByUPC_Failure() {
    when(containerRepository.findItemBaseDivCodesByUPC(anyString(), anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    List<ItemInfoResponse> itemInfoResponseList = itemService.findItemBaseDivCodesByUPC("12345678");
    verify(containerRepository, times(1))
        .findItemBaseDivCodesByUPC(anyString(), anyString(), anyInt());
    assertEquals(itemInfoResponseList.size(), 0);
  }

  @Test
  public void testItemOverride_Success() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ItemServiceHandler.class)))
        .thenReturn(defaultItemServiceHandler);
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    doNothing()
        .when(defaultItemServiceHandler)
        .updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders());
    itemService.itemOverride(itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(defaultItemServiceHandler, times(1))
        .updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testItemOverride_ThrowsReceivingInternalException() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ItemServiceHandler.class)))
        .thenReturn(defaultItemServiceHandler);
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    doThrow(new ReceivingInternalException("mock_error", "mock_error"))
        .when(defaultItemServiceHandler)
        .updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders());
    itemService.itemOverride(itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(defaultItemServiceHandler, times(1))
        .updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSaveCOOConfirmationResponseThrowsBadDataException_OCC() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(false);
    saveConfirmationRequest.setIsOriginCountryCodeConditionalAcknowledged(false);
    itemService.saveConfirmationRequest(saveConfirmationRequest);
  }

  @Test
  public void testSaveCOOConfirmationResponse_Success() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveConfirmationRequest.setOriginCountryCode(Collections.singletonList("India"));
    itemService.saveConfirmationRequest(saveConfirmationRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSaveCOOConfirmationResponseThrowsBadDataException_PackType() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveConfirmationRequest.setIsPackTypeAcknowledged(false);
    itemService.saveConfirmationRequest(saveConfirmationRequest);
  }

  @Test
  public void testSaveCOOConfirmationResponseSuccess() {
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setVnpkQty(1);
    saveConfirmationRequest.setWhpkQty(1);
    itemService.saveConfirmationRequest(saveConfirmationRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSaveCOOConfirmationResponseThrowsBadDataException_OCCDisabled() {
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    saveConfirmationRequest.setIsPackTypeAcknowledged(false);
    itemService.saveConfirmationRequest(saveConfirmationRequest);
  }

  @Test
  public void testSaveCOOConfirmationResponseSuccess_Pack() {
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setVnpkQty(1);
    saveConfirmationRequest.setWhpkQty(1);
    itemService.saveConfirmationRequest(saveConfirmationRequest);
  }

  private List<Long> getSuccessItemNumber() {
    List<Long> itemNumbers = new ArrayList<>();
    itemNumbers.add(12345678L);
    itemNumbers.add(87654321L);
    return itemNumbers;
  }

  private List<ItemInfoResponse> getSuccessItemInfo() {
    List<ItemInfoResponse> itemInfoResponseList = new ArrayList<>();
    itemInfoResponseList.add(new ItemInfoResponse(1234567l, "WM"));
    itemInfoResponseList.add(new ItemInfoResponse(87654321l, "SAMS"));
    return itemInfoResponseList;
  }
}
