package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryItemOverride;
import com.walmart.move.nim.receiving.core.mock.data.MockTempTiHi;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.OverrideRequest;
import com.walmart.move.nim.receiving.core.model.SaveConfirmationRequest;
import com.walmart.move.nim.receiving.core.model.TemporaryPalletTiHiRequest;
import com.walmart.move.nim.receiving.core.repositories.DeliveryItemOverrideRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.Optional;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author lkotthi */
public class DeliveryItemOverrideServiceTest extends ReceivingTestBase {

  @InjectMocks private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private DeliveryItemOverrideRepository deliveryItemOverrideRepo;
  @Mock private WitronDeliveryMetaDataService witronDeliveryMetaDataService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private GDMRestApiClient gdmRestApiClient;

  private DeliveryItemOverride mockDeliveryItemOverride = MockTempTiHi.getDeliveryItemOverride();
  private DeliveryItemOverride mockDeliveryItemOverrideOptional =
      MockDeliveryItemOverride.getDeliveryItemOverride();
  private OverrideRequest overrideRequest = new OverrideRequest();
  private String deliveryNumber = "12345678";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    overrideRequest.setUserId("test1");
    overrideRequest.setPassword("test2");
    overrideRequest.setPurchaseReferenceNumber("7836237741");
    overrideRequest.setPurchaseReferenceLineNumber(1);
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryItemOverrideRepo);
  }

  @Test
  public void testSaveTemporaryPalletTiHi() throws Exception {
    mockDeliveryItemOverride.setVersion(2);
    doReturn(mockDeliveryItemOverride)
        .when(deliveryItemOverrideRepo)
        .saveAndFlush(any(DeliveryItemOverride.class));

    TemporaryPalletTiHiRequest temporaryPalletTiHiRequest = new TemporaryPalletTiHiRequest();
    temporaryPalletTiHiRequest.setVersion(1);
    temporaryPalletTiHiRequest.setPalletTi(2);

    DeliveryItemOverride response =
        deliveryItemOverrideService.saveTemporaryPalletTiHi(
            mockDeliveryItemOverride.getDeliveryNumber(),
            mockDeliveryItemOverride.getItemNumber(),
            temporaryPalletTiHiRequest,
            MockHttpHeaders.getHeaders());

    assertNotNull(response);
    assertEquals(response.getVersion().intValue(), 2);

    verify(deliveryItemOverrideRepo, times(1)).saveAndFlush(any(DeliveryItemOverride.class));
  }

  @Test
  public void testSaveTemporaryPalletTiHi_publishToGDM() throws Exception {
    mockDeliveryItemOverride.setVersion(2);
    doReturn(mockDeliveryItemOverride)
        .when(deliveryItemOverrideRepo)
        .saveAndFlush(any(DeliveryItemOverride.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.SEND_UPDATE_EVENTS_TO_GDM, false))
        .thenReturn(true);
    TemporaryPalletTiHiRequest temporaryPalletTiHiRequest = new TemporaryPalletTiHiRequest();
    temporaryPalletTiHiRequest.setVersion(1);
    temporaryPalletTiHiRequest.setPalletTi(2);
    temporaryPalletTiHiRequest.setPalletHi(2);

    DeliveryItemOverride response =
        deliveryItemOverrideService.saveTemporaryPalletTiHi(
            mockDeliveryItemOverride.getDeliveryNumber(),
            mockDeliveryItemOverride.getItemNumber(),
            temporaryPalletTiHiRequest,
            MockHttpHeaders.getHeaders());

    assertNotNull(response);
    assertEquals(response.getVersion().intValue(), 2);

    verify(gdmRestApiClient, times(1)).receivingToGDMEvent(any(), anyMap());
    verify(deliveryItemOverrideRepo, times(1)).saveAndFlush(any(DeliveryItemOverride.class));
  }

  @Test
  public void testFindFirstByDeliveryNumberAndItemNumber() {
    doReturn(Optional.of(mockDeliveryItemOverride))
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    Optional<DeliveryItemOverride> response =
        deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            mockDeliveryItemOverride.getDeliveryNumber(), mockDeliveryItemOverride.getItemNumber());

    assertNotNull(response);
    assertEquals(response.get().getTempPalletHi(), mockDeliveryItemOverride.getTempPalletHi());
    assertEquals(response.get().getTempPalletTi(), mockDeliveryItemOverride.getTempPalletTi());
  }

  @Test
  public void testDeleteByDeliveryNumberAndItemNumber() {

    doAnswer(i -> null)
        .when(deliveryItemOverrideRepo)
        .deleteByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    deliveryItemOverrideService.deleteByDeliveryNumberAndItemNumber(
        mockDeliveryItemOverride.getDeliveryNumber(), mockDeliveryItemOverride.getItemNumber());
  }

  @Test
  public void testOverridOverage() {
    DeliveryMetaData deliveryMetaDataRsp = new DeliveryMetaData();
    String poLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7836237741\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreOverage\":\"true\"}]}]}";
    deliveryMetaDataRsp.setPoLineDetails(poLineDetails);

    doReturn(deliveryMetaDataRsp)
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(anyString(), anyString(), anyString(), anyInt(), anyString());

    DeliveryMetaData deliveryMetaData =
        deliveryItemOverrideService.override(
            ReceivingConstants.OVERAGES, deliveryNumber, overrideRequest);
    Assert.assertNotNull(deliveryMetaData);
    assertEquals(deliveryMetaData.getPoLineDetails(), poLineDetails);
  }

  @Test
  public void testOverrideExpiry() {
    DeliveryMetaData deliveryMetaDataRsp = new DeliveryMetaData();
    String poLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7836237741\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreExpiry\":\"true\"}]}]}";
    deliveryMetaDataRsp.setPoLineDetails(poLineDetails);

    doReturn(deliveryMetaDataRsp)
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(anyString(), anyString(), anyString(), anyInt(), anyString());

    DeliveryMetaData deliveryMetaData =
        deliveryItemOverrideService.override(
            ReceivingConstants.EXPIRY, deliveryNumber, overrideRequest);
    Assert.assertNotNull(deliveryMetaData);
    assertEquals(deliveryMetaData.getPoLineDetails(), poLineDetails);
  }

  @Test
  public void testOverrideHaccp() {
    DeliveryMetaData deliveryMetaDataRsp = new DeliveryMetaData();
    String poLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7836237741\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"approvedHaccp\":\"true\"}]}]}";
    deliveryMetaDataRsp.setPoLineDetails(poLineDetails);

    doReturn(deliveryMetaDataRsp)
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(anyString(), anyString(), anyString(), anyInt(), anyString());

    DeliveryMetaData deliveryMetaData =
        deliveryItemOverrideService.override(
            ReceivingConstants.HACCP, deliveryNumber, overrideRequest);
    Assert.assertNotNull(deliveryMetaData);
    assertEquals(deliveryMetaData.getPoLineDetails(), poLineDetails);
  }

  @Test
  public void testUpdateDeliveryItemOverrideSameDeliveryNumber_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 12345678L, "B", "C", "B", "C", "234234", 1, false);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateDeliveryItemOverride(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void testUpdateDeliveryItemOverride_DuplicateDeliveryWithSameItemNumber_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateDeliveryItemOverride(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void testUpdateAtlasItemDeliveryItemOverrideSameDeliveryNumber_Success() {
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 12345678L, "B", "C", "B", "C", "234234", 1, true);
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(
            itemOverrideRequest.getDeliveryNumber(), itemOverrideRequest.getItemNumber());
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateDeliveryItemOverride(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
    verify(deliveryItemOverrideRepo, times(1))
        .findByDeliveryNumberAndItemNumber(
            itemOverrideRequest.getDeliveryNumber(), itemOverrideRequest.getItemNumber());
  }

  @Test
  public void testUpdateNonAtlasItemDeliveryItemOverrideSameDeliveryNumber_Success() {
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 12345678L, "B", "C", "B", "C", "234234", 1, false);
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByItemNumber(itemOverrideRequest.getItemNumber());
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateDeliveryItemOverride(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
    verify(deliveryItemOverrideRepo, times(1))
        .findTopByItemNumberOrderByLastChangedTsDesc(itemOverrideRequest.getItemNumber());
  }

  @Test
  public void
      testUpdateDeliveryItemOverrideSameDeliveryNumber_SuccessWithEmptyItemOverrideRequest() {
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 12345678L, "B", "C", "B", "C", "234234", 1, false);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateDeliveryItemOverride(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void
      testUpdateDeliveryItemOverrideSameDeliveryNumberWithNullAtlasItemFlagValues_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByItemNumber(anyLong());
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 12345678L, "B", "C", "B", "C", "234234", 1, null);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateDeliveryItemOverride(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void testupdateDeliveryItemOverrideWithCOO_withAck() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest =
        new SaveConfirmationRequest(
            232L, 32432L, Collections.singletonList("MEXICO"), 1, 1, true, false, true);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void testupdateDeliveryItemOverrideWithCOO_withNoConditionalAck() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest =
        new SaveConfirmationRequest(
            232L, 32432L, Collections.singletonList("MEXICO,AFRICA"), 1, 1, false, false, false);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void testupdateDeliveryItemOverrideWithCOO_withConditionalAck() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest =
        new SaveConfirmationRequest(
            232L, 32432L, Collections.singletonList("MEXICO,AFRICA"), 1, 1, false, true, true);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void testupdateDeliveryItemOverrideWithCOO_packFlagEnabled() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED)))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(IS_VALIDATE_PACK_TYPE_ACK_ENABLED)))
        .thenReturn(true);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest =
        new SaveConfirmationRequest(232L, 32432L, null, 1, 1, false, false, true);
    when(deliveryItemOverrideRepo.save(any(DeliveryItemOverride.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideRepo, times(1)).save(any(DeliveryItemOverride.class));
  }

  @Test
  public void updateDeliveryItemOverrideWithCOO_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveCOOConfirmationRequest = new SaveConfirmationRequest();
    saveCOOConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveCOOConfirmationRequest.setOriginCountryCode(Collections.singletonList("India"));
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveCOOConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void updateDeliveryItemOverrideWithCOOThrowsBadDataException_OCCNull() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setOriginCountryCode(null);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void updateDeliveryItemOverrideWithPack_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setWhpkQty(1);
    saveConfirmationRequest.setVnpkQty(1);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void updateDeliveryItemOverrideWithCOOThrowsBadDataException_PackNull() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void updateDeliveryItemOverrideWithOCCAndPack_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setWhpkQty(1);
    saveConfirmationRequest.setVnpkQty(1);
    saveConfirmationRequest.setOriginCountryCode(Collections.singletonList("India"));
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void updateDeliveryItemOverrideWithOCCConditionalAck_Success() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(false);
    saveConfirmationRequest.setIsOriginCountryCodeConditionalAcknowledged(true);
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setOriginCountryCode(Collections.singletonList("India"));
    saveConfirmationRequest.setWhpkQty(1);
    saveConfirmationRequest.setVnpkQty(1);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void updateDeliveryItemOverrideWithOCCAndPack_PackNull() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveConfirmationRequest.setOriginCountryCode(Collections.singletonList("India"));
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void updateDeliveryItemOverrideWithOCCAndPack_OCCNull() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsOriginCountryCodeAcknowledged(true);
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setOriginCountryCode(null);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void updateDeliveryItemOverrideWithOCCAndPack_OCCAckNull() {
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        Optional.of(mockDeliveryItemOverrideOptional);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryItemOverrideOptional)
        .when(deliveryItemOverrideRepo)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    SaveConfirmationRequest saveConfirmationRequest = new SaveConfirmationRequest();
    saveConfirmationRequest.setIsPackTypeAcknowledged(true);
    saveConfirmationRequest.setOriginCountryCode(Collections.singletonList("India"));
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, MockHttpHeaders.getHeaders());
  }
}
