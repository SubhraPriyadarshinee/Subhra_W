package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUDIT_V2_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IN_CONSISTENT_VENDOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.NON_TRUSTED_VENDOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUSTED_VENDOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VENDORPACK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WAREHOUSEPACK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.ReceiptForOsrdProcess;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.mock.data.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ObjectUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameDeliveryMetaDataServiceTest extends ReceivingTestBase {
  @InjectMocks private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryPOMap deliveryPOMap;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  private List<AuditFlagResponse> auditFlagResponseList;
  private Gson gson;

  private String itemNumber = "561298341";
  private String rotateDate = "2019-02-15T00:00:00.000Z";
  private String divert = "DECANT";

  @BeforeClass
  public void setRootUp() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        endGameDeliveryMetaDataService, "deliveryMetaDataRepository", deliveryMetaDataRepository);
    ReflectionTestUtils.setField(endGameDeliveryMetaDataService, "gson", gson);
    TenantContext.setFacilityNum(4321);
    TenantContext.setFacilityCountryCode("US");
    auditFlagResponseList = new ArrayList<>();
  }

  @AfterMethod
  public void resetMocks() {
    reset(deliveryMetaDataRepository);
  }

  @Test
  public void testUpdateForItemDetails() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    endGameDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        deliveryMetaData, itemNumber, rotateDate, divert);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.ROTATE_DATE),
        rotateDate);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.DIVERT_DESTINATION),
        divert);
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testUpdateForItemDetails_DeliveryMetaDataContainsUpdatedExpiryDate() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails(rotateDate, divert);
    endGameDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        deliveryMetaData, itemNumber, rotateDate, divert);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.ROTATE_DATE),
        rotateDate);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.DIVERT_DESTINATION),
        divert);
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testUpdateForItemDetails_ExpiryDateUpdateListenerHavingNoData() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    endGameDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        deliveryMetaData, itemNumber, "", "");
    assertTrue(
        ObjectUtils.isEmpty(
            EndGameUtils.getItemAttributeFromDeliveryMetaData(
                deliveryMetaData, itemNumber, EndgameConstants.ROTATE_DATE)));
    assertTrue(
        ObjectUtils.isEmpty(
            EndGameUtils.getItemAttributeFromDeliveryMetaData(
                deliveryMetaData, itemNumber, EndgameConstants.DIVERT_DESTINATION)));
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testUpdateForItemDetails_NewUpdateReplaceOldExpiryDate() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails(rotateDate, divert);
    String newDate = "2019-02-15T00:00:00.000Z";
    String newDivert = "PALLET_BUILD";
    endGameDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        deliveryMetaData, itemNumber, newDate, newDivert);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.ROTATE_DATE),
        newDate);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.DIVERT_DESTINATION),
        newDivert);
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testUpdateForItemDetails_WithDifferentItem() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithDifferentItem();
    endGameDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        deliveryMetaData, itemNumber, rotateDate, divert);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.ROTATE_DATE),
        rotateDate);
    assertEquals(
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.DIVERT_DESTINATION),
        divert);
    assertEquals(deliveryMetaData.getItemDetails().size(), 2);
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testFindAndUpdateForOsdrProcessing() {
    when(deliveryMetaDataRepository.findAllByDeliveryNumberInOrderByCreatedDate(anyList()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    when(deliveryMetaDataRepository.saveAll(anyList()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    ReceiptForOsrdProcess receiptForOsrdProcess1 = new ReceiptForOsrdProcess();
    receiptForOsrdProcess1.setDeliveryNumber(12333333L);
    receiptForOsrdProcess1.setPurchaseReferenceNumber("101");
    ReceiptForOsrdProcess receiptForOsrdProcess2 = new ReceiptForOsrdProcess();
    receiptForOsrdProcess2.setDeliveryNumber(12333333L);
    receiptForOsrdProcess2.setPurchaseReferenceNumber("102");
    when(receiptService.fetchReceiptForOsrdProcess(any()))
        .thenReturn(Arrays.asList(receiptForOsrdProcess1, receiptForOsrdProcess2));
    List<DeliveryMetaData> deliveryMetaDataList =
        endGameDeliveryMetaDataService.findAndUpdateForOsdrProcessing(5, 240, 10, deliveryPOMap);
    assertEquals(deliveryMetaDataList.size(), 1);
  }

  @Test
  public void testFindAndUpdateForOsdrProcessing_EmptyList() {
    List<DeliveryMetaData> deliveryMetaDataList =
        endGameDeliveryMetaDataService.findAndUpdateForOsdrProcessing(5, 240, 10, deliveryPOMap);
    assertEquals(deliveryMetaDataList.size(), 0);
    verify(deliveryMetaDataRepository, times(0)).saveAll(anyList());
  }

  @Test
  public void testUpdateAuditInfo() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(TRUSTED_VENDOR, EACHES);
    auditFlagResponseList.add(auditFlagResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(false);
    endGameDeliveryMetaDataService.updateAuditInfo(deliveryMetaData, auditFlagResponseList);
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateAuditInfoForTrustedVendor() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(TRUSTED_VENDOR, EACHES);
    auditFlagResponseList.add(auditFlagResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    endGameDeliveryMetaDataService.updateAuditInfo(deliveryMetaData, auditFlagResponseList);
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateAuditInfoForNonTrustedVendor() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(NON_TRUSTED_VENDOR, VENDORPACK);
    auditFlagResponseList.add(auditFlagResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    endGameDeliveryMetaDataService.updateAuditInfo(deliveryMetaData, auditFlagResponseList);
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateAuditInfoForInconsistentVendor() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(IN_CONSISTENT_VENDOR, WAREHOUSEPACK);
    auditFlagResponseList.add(auditFlagResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    endGameDeliveryMetaDataService.updateAuditInfo(deliveryMetaData, auditFlagResponseList);
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateMetadataForInConsistentVendorWithQty() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(true);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    List<PurchaseOrder> purchaseOrderList =
        Arrays.asList(
            gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    boolean result =
        endGameDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(
            purchaseOrderList, 2, 123456L);
    assertFalse(result);
  }

  @Test
  public void testUpdateMetadataForInConsistentVendorWithoutQty() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithoutReceivedQty(true);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    List<PurchaseOrder> purchaseOrderList =
        Arrays.asList(
            gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    boolean result =
        endGameDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(
            purchaseOrderList, 2, Long.parseLong(deliveryMetaData.getDeliveryNumber()));
    assertTrue(result);
  }

  @Test
  public void testUpdateMetadataForTrustedVendor() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(false);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    List<PurchaseOrder> purchaseOrderList =
        Arrays.asList(
            gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    boolean result =
        endGameDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(
            purchaseOrderList, 2, Long.parseLong(deliveryMetaData.getDeliveryNumber()));
    assertFalse(result);
  }

  @Test
  public void testUpdateMetadataWithoutMetadata() {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    List<PurchaseOrder> purchaseOrderList =
        Arrays.asList(
            gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    try {
      endGameDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(
          purchaseOrderList, 2, 123456L);
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testReceivedQtyFromMetadataForTrustedVendor() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(false);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    PurchaseOrder purchaseOrder =
        Arrays.asList(
                gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class))
            .get(0);
    int receivedQty =
        endGameDeliveryMetaDataService.getReceivedQtyFromMetadata(
            purchaseOrder.getLines().get(0).getItemDetails().getNumber(),
            Long.parseLong(deliveryMetaData.getDeliveryNumber()));
    assertEquals(receivedQty, 1);
  }

  @Test
  public void testReceivedQtyFromMetadataForTrustedVendorWithoutPurchaseOrder() {
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(false);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    int receivedQty =
        endGameDeliveryMetaDataService.getReceivedQtyFromMetadata(
            1234L, Long.parseLong(deliveryMetaData.getDeliveryNumber()));
    assertEquals(receivedQty, 0);
  }

  @Test
  public void testFindDoorStatusForEmptyCase() throws ReceivingException {
    DeliveryList deliveryList = DeliveryList.builder().data(new ArrayList<>()).build();
    String deliveryResponse = gson.toJson(deliveryList);
    when(deliveryService.getDeliveryDocumentBySearchCriteria(anyString()))
        .thenReturn(deliveryResponse);
    DeliveryDoorSummary doorSummary =
        endGameDeliveryMetaDataService.findDoorStatus(7552, "US", "200");
    assertFalse(doorSummary.isDoorOccupied());
  }

  @Test
  public void testFindDoorStatusForAssignedCase() throws ReceivingException {
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(4567892L);
    delivery.setDoorNumber("200");

    DeliveryList deliveryList =
        DeliveryList.builder().data(Collections.singletonList(delivery)).build();
    String deliveryResponse = gson.toJson(deliveryList);
    when(deliveryService.getDeliveryDocumentBySearchCriteria(anyString()))
        .thenReturn(deliveryResponse);
    DeliveryDoorSummary doorSummary =
        endGameDeliveryMetaDataService.findDoorStatus(7552, "US", "200");
    assertTrue(doorSummary.isDoorOccupied());
  }

  private AuditFlagResponse getAuditFlagResponse(String vendorType, String qtyUom) {
    return AuditFlagResponse.builder()
        .itemNumber(9213971L)
        .vendorNumber(8)
        .flaggedQty(100)
        .orderedQty(100)
        .vnpkRatio(10)
        .isCaseFlagged(true)
        .qtyUom(qtyUom)
        .vendorType(vendorType)
        .isFrequentlyReceivedQuantityRequired(false)
        .receivedQuantity(String.valueOf(0))
        .build();
  }

  @Test
  public void testgetReceivedQtyFromMetadataWithoutAuditCheck_WithReceivedQty() {
    DeliveryMetaData deliveryMetaData = MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(false);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.of(deliveryMetaData));

    int receivedQty = endGameDeliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L, 123456L);
    assertEquals(receivedQty, 1);
  }

  @Test
  public void testgetReceivedQtyFromMetadataWithoutAuditCheck_WithoutReceivedQty() {
    DeliveryMetaData deliveryMetaData = MockDeliveryMetaData.getDeliveryMetaData_WithoutReceivedQty(false);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.of(deliveryMetaData));

    int receivedQty = endGameDeliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L, 123456L);
    assertEquals(receivedQty, 0);
  }

  @Test
  public void testgetReceivedQtyFromMetadataWithoutAuditCheck_NoItemDetails() {
    DeliveryMetaData deliveryMetaData = MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.of(deliveryMetaData));

    int receivedQty = endGameDeliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L, 123456L);
    assertEquals(receivedQty, 0);
  }

  @Test
  public void testgetReceivedQtyFromMetadataWithoutAuditCheck_DeliveryMetaDataNotFound() {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());

    try {
      endGameDeliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L, 123456L);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getMessage(), String.format(EndgameConstants.DELIVERY_METADATA_NOT_FOUND_ERROR_MSG, 123456L));
    }
  }

    @Test
    public void testUpdateAuditInfo_TrustedVendorWithFrequentlyReceivedQuantityRequired() {
        DeliveryMetaData deliveryMetaData = MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(true);
        when(deliveryMetaDataRepository.findByDeliveryNumber("12333333")).thenReturn(Optional.of(deliveryMetaData));
        when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(false);
        AuditFlagResponse auditFlagResponse = AuditFlagResponse.builder()
                .itemNumber(561298341L)
                .vendorType("TRUSTED_VENDOR")
                .isFrequentlyReceivedQuantityRequired(true)
                .receivedQuantity("10")
                .build();
        endGameDeliveryMetaDataService.updateAuditInfo(deliveryMetaData, Collections.singletonList(auditFlagResponse));
        int itemDetails = endGameDeliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L, 12333333L);
        assertEquals(itemDetails, 1);
    }

    @Test
    public void testUpdateAuditInfo_TrustedVendorWithoutFrequentlyReceivedQuantityRequired() {
        DeliveryMetaData deliveryMetaData = MockDeliveryMetaData.getDeliveryMetaData_WithoutReceivedQty(true);
        when(deliveryMetaDataRepository.findByDeliveryNumber("12333333")).thenReturn(Optional.of(deliveryMetaData));
        when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(false);
        AuditFlagResponse auditFlagResponse = AuditFlagResponse.builder()
                .itemNumber(561298341L)
                .vendorType("TRUSTED_VENDOR")
                .isFrequentlyReceivedQuantityRequired(true)
                .receivedQuantity("10")
                .build();
        endGameDeliveryMetaDataService.updateAuditInfo(deliveryMetaData, Collections.singletonList(auditFlagResponse));
        int itemDetails = endGameDeliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L, 12333333L);
        assertEquals(itemDetails, 0);
    }

}
