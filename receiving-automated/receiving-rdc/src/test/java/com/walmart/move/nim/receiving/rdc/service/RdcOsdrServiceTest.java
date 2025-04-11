package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_AUDIT_USER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OSDR_EVENT_TYPE_VALUE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.osdr.CutOverType;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.AuditLogPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockReceiptsData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class RdcOsdrServiceTest {

  @Mock private NgrRestApiClient ngrClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DockTagService dockTagService;
  @InjectMocks private RdcOsdrService rdcOsdrService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private ContainerPersisterService containerPersisterService;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";
  @Mock private PendingAuditTags pendingAuditTags;
  @Mock private AuditLogPersisterService auditLogPersisterService;

  Gson gson = new Gson();
  File resource = null;

  @BeforeMethod
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        ngrClient,
        tenantSpecificConfigReader,
        dockTagService,
        rdcManagedConfig,
        receiptCustomRepository,
        containerPersisterService,
        pendingAuditTags,
        containerItemRepository);
  }

  @Test
  public void test_getOsdrSummary() throws ReceivingException, IOException {
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345l, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getOpenDockTags().getCount().intValue(), 1);
    osdrSummaryResponse
        .getSummary()
        .forEach(
            summary -> {
              assertEquals(summary.getCutOverType(), CutOverType.NON_ATLAS.getType());
            });
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
  }

  @Test
  public void test_getOsdrSummaryWhenOneAtlasEnabled() throws ReceivingException, IOException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NGR_SERVICES_DISABLED,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345l, MockHttpHeaders.getHeaders());
    assertNotNull(osdrSummaryResponse);
    assertNotNull(osdrSummaryResponse.getTs());
    assertEquals(osdrSummaryResponse.getUserId(), DEFAULT_AUDIT_USER);
    assertEquals(osdrSummaryResponse.getEventType(), OSDR_EVENT_TYPE_VALUE);
    assertFalse(osdrSummaryResponse.getAuditPending());
  }

  @Test
  public void test_getOsdrSummary_old_osdr_contract() throws ReceivingException, IOException {
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(false);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345l, MockHttpHeaders.getHeaders());
    assertNull(osdrSummaryResponse.getSummary().get(0).getCutOverType());
    assertFalse(osdrSummaryResponse.getSummary().get(0).getLines().get(0).isAtlasConvertedItem());
  }

  @Test
  public void test_getOsdrSummary_EmptyReceipts() throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(1).build());
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class))).thenReturn(osdrSummary);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    osdrSummaryResponse
        .getSummary()
        .forEach(
            summary -> {
              assertEquals(summary.getCutOverType(), CutOverType.NON_ATLAS.getType());
            });
    assertEquals(osdrSummaryResponse.getOpenDockTags().getCount().intValue(), 1);
    assertEquals(osdrSummaryResponse.getSummary().size(), 0);
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_NoneReceivedInAtlas_NoneReceivedInRDS_EmptyReceipts()
          throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    osdrSummary.setAuditPending(true);
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class))).thenReturn(osdrSummary);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(Collections.emptyList());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    assertNotNull(osdrSummaryResponse);
    osdrSummaryResponse
        .getSummary()
        .forEach(
            summary -> {
              assertEquals(summary.getCutOverType(), CutOverType.NON_ATLAS.getType());
            });
    assertEquals(osdrSummaryResponse.getSummary().size(), 0);
    assertEquals(osdrSummaryResponse.getOpenDockTags().getCount().intValue(), 1);

    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(0))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void test_getOsdrSummary_AtlasReceivingEnabled_NoneReceivedInAtlas_AllReceivedInRDS()
      throws ReceivingException, IOException {
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(Collections.emptyList());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    osdrSummaryResponse
        .getSummary()
        .forEach(
            summary -> {
              assertEquals(summary.getCutOverType(), CutOverType.NON_ATLAS.getType());
            });
    assertTrue(osdrSummaryResponse.getSummary().size() > 0);
    assertEquals(
        osdrSummaryResponse.getSummary().get(0).getPurchaseReferenceNumber(), "1467778615");
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdQty(), Integer.valueOf(8));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getOrderFilledQty(), Integer.valueOf(10));
    assertEquals(osdrSummaryResponse.getOpenDockTags().getCount().intValue(), 1);
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(0))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_SinglePoPoLReceivedInAtlas_NoneReceivedInRDS()
          throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class))).thenReturn(osdrSummary);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasSinglePoPoL());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliverySinglePoPoL());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    osdrSummaryResponse
        .getSummary()
        .forEach(
            summary -> {
              assertEquals(summary.getCutOverType(), CutOverType.ATLAS.getType());
            });
    assertTrue(osdrSummaryResponse.getSummary().size() == 1);
    assertEquals(
        osdrSummaryResponse.getSummary().get(0).getPurchaseReferenceNumber(), "1467778615");
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdQty(), Integer.valueOf(30));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getOrderFilledQty(), Integer.valueOf(20));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdPackCount(), Integer.valueOf(2));
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_SinglePoMultiPoLReceivedInAtlas_NoneReceivedInRDS()
          throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class))).thenReturn(osdrSummary);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasSinglePoMultiPoL());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliverySinglePoPoL());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);
    assertNotNull(osdrSummaryResponse);
    osdrSummaryResponse
        .getSummary()
        .forEach(
            summary -> {
              assertEquals(summary.getCutOverType(), CutOverType.ATLAS.getType());
            });
    assertEquals(osdrSummaryResponse.getSummary().size(), 1);
    assertEquals(osdrSummaryResponse.getSummary().get(0).getLines().size(), 3);
    assertEquals(
        osdrSummaryResponse.getSummary().get(0).getPurchaseReferenceNumber(), "1467778615");
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdQty(), Integer.valueOf(120));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getOrderFilledQty(), Integer.valueOf(70));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdPackCount(), Integer.valueOf(2));
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_SinglePoMultiPoLReceivedInAtlas_SamePoDifferentLineReceivedInRDS()
          throws ReceivingException, IOException {
    String mixedPo = "1467778615";
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasSinglePoMultiPoL());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliverySinglePoPoL());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NGR_SERVICES_DISABLED,
            false))
        .thenReturn(false);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    List<String> atlasReceiptPoList =
        MockReceiptsData.getReceiptsFromAtlasSinglePoMultiPoL()
            .stream()
            .map(ReceiptSummaryResponse::getPurchaseReferenceNumber)
            .collect(Collectors.toList());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (mixedPo.equals(summary.getPurchaseReferenceNumber())) {
        assertEquals(CutOverType.MIXED.getType(), summary.getCutOverType());
      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertEquals(osdrSummaryResponse.getSummary().size(), 16);
    assertEquals(osdrSummaryResponse.getSummary().get(0).getLines().size(), 4);
    assertEquals(
        osdrSummaryResponse.getSummary().get(0).getPurchaseReferenceNumber(), "1467778615");
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdQty(), Integer.valueOf(128));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getOrderFilledQty(), Integer.valueOf(80));
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdPackCount(), Integer.valueOf(2));
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_SinglePoMultiPoLReceivedInAtlas_DifferentPoPoLReceivedInRDS()
          throws ReceivingException, IOException {
    String onlyAtlasPo = "1467778699";
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasDifferentPoL());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliveryDifferentPo());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    List<String> atlasReceiptPoList =
        MockReceiptsData.getReceiptsFromAtlasDifferentPoL()
            .stream()
            .map(ReceiptSummaryResponse::getPurchaseReferenceNumber)
            .collect(Collectors.toList());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (onlyAtlasPo.equals(summary.getPurchaseReferenceNumber())) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());
      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 17);

    Optional<OsdrPo> osdrPo =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals("1467778699"))
            .findAny();
    if (osdrPo.isPresent()) {
      assertEquals(osdrPo.get().getPurchaseReferenceNumber(), "1467778699");
      assertEquals(osdrPo.get().getRcvdQty(), Integer.valueOf(60));
      assertEquals(osdrPo.get().getOrderFilledQty(), Integer.valueOf(150));
      assertEquals(osdrPo.get().getLines().size(), 3);
      assertEquals(osdrPo.get().getLines().get(0).getRcvdQty(), Integer.valueOf(10));
      assertEquals(osdrPo.get().getLines().get(1).getRcvdQty(), Integer.valueOf(20));
      assertEquals(osdrPo.get().getLines().get(2).getRcvdQty(), Integer.valueOf(30));
      assertEquals(osdrPo.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(40));
      assertEquals(osdrPo.get().getLines().get(1).getOrderFilledQty(), Integer.valueOf(50));
      assertEquals(osdrPo.get().getLines().get(2).getOrderFilledQty(), Integer.valueOf(60));
    }

    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void test_getOsdrSummary_AtlasReceivingEnabled_MultiPoPoLReceivedInAtlasAndRDS()
      throws ReceivingException, IOException {
    String mixedPo = "1621130072";
    String onlyAtlasPo = "1467778688";
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasMultiPoPoL());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliveryForMultiplePO());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (summary.getPurchaseReferenceNumber().equals(mixedPo)) {
        assertEquals(CutOverType.MIXED.getType(), summary.getCutOverType());

      } else if (summary.getPurchaseReferenceNumber().equals(onlyAtlasPo)) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());

      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 17);

    Optional<OsdrPo> osdrPo1 =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals("1467778688"))
            .findAny();
    if (osdrPo1.isPresent()) {
      assertEquals(osdrPo1.get().getPurchaseReferenceNumber(), "1467778688");
      assertEquals(osdrPo1.get().getRcvdQty(), Integer.valueOf(30));
      assertEquals(osdrPo1.get().getOrderFilledQty(), Integer.valueOf(45));
      assertEquals(osdrPo1.get().getLines().size(), 2);
      assertEquals(osdrPo1.get().getLines().get(0).getRcvdQty(), Integer.valueOf(10));
      assertEquals(osdrPo1.get().getLines().get(1).getRcvdQty(), Integer.valueOf(20));
      assertEquals(osdrPo1.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(15));
      assertEquals(osdrPo1.get().getLines().get(1).getOrderFilledQty(), Integer.valueOf(30));
    }

    Optional<OsdrPo> osdrPo2 =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals("1621130072"))
            .findAny();
    if (osdrPo2.isPresent()) {
      assertEquals(osdrPo2.get().getPurchaseReferenceNumber(), "1621130072");
      assertEquals(osdrPo2.get().getRcvdQty(), Integer.valueOf(794));
      assertEquals(osdrPo2.get().getOrderFilledQty(), Integer.valueOf(395));
      assertEquals(osdrPo2.get().getRcvdPackCount(), Integer.valueOf(7));
      assertEquals(osdrPo2.get().getLines().size(), 43);
    }

    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void test_getOsdrSummary_AtlasReceivingEnabled_RcvdPackCountCases()
      throws ReceivingException, IOException {
    List<String> mixedPo = Arrays.asList("1621130072");
    List<String> onlyAtlasPo = Arrays.asList("1467778688", "1669115709");
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsForMultipleRDSandAtlasPO());
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliveryForMultipleRDSandAtlasPO());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (mixedPo.contains(summary.getPurchaseReferenceNumber())) {
        assertEquals(CutOverType.MIXED.getType(), summary.getCutOverType());

      } else if (onlyAtlasPo.contains(summary.getPurchaseReferenceNumber())) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());

      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 17);

    Optional<OsdrPo> osdrPo1 =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals("1467778688"))
            .findAny();
    if (osdrPo1.isPresent()) {
      assertEquals(osdrPo1.get().getPurchaseReferenceNumber(), "1467778688");
      assertEquals(osdrPo1.get().getRcvdQty(), Integer.valueOf(30));
      assertEquals(osdrPo1.get().getRcvdPackCount(), Integer.valueOf(0));
      assertEquals(osdrPo1.get().getOrderFilledQty(), Integer.valueOf(45));
      assertEquals(osdrPo1.get().getLines().size(), 2);
      assertEquals(osdrPo1.get().getLines().get(0).getRcvdQty(), Integer.valueOf(10));
      assertEquals(osdrPo1.get().getLines().get(1).getRcvdQty(), Integer.valueOf(20));
      assertEquals(osdrPo1.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(15));
      assertEquals(osdrPo1.get().getLines().get(1).getOrderFilledQty(), Integer.valueOf(30));
    }

    Optional<OsdrPo> osdrPo2 =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals("1621130072"))
            .findAny();
    if (osdrPo2.isPresent()) {
      assertEquals(osdrPo2.get().getPurchaseReferenceNumber(), "1621130072");
      assertEquals(osdrPo2.get().getRcvdQty(), Integer.valueOf(754));
      assertEquals(osdrPo2.get().getOrderFilledQty(), Integer.valueOf(355));
      assertEquals(osdrPo2.get().getRcvdPackCount(), Integer.valueOf(7));
      assertEquals(osdrPo2.get().getLines().size(), 42);
    }
    Optional<OsdrPo> osdrPo3 =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals("1669115709"))
            .findAny();
    if (osdrPo3.isPresent()) {
      assertEquals(osdrPo3.get().getPurchaseReferenceNumber(), "1669115709");
      assertEquals(osdrPo3.get().getRcvdQty(), Integer.valueOf(136));
      assertEquals(osdrPo3.get().getOrderFilledQty(), Integer.valueOf(65));
      assertEquals(osdrPo3.get().getRcvdPackCount(), Integer.valueOf(5));
      assertEquals(osdrPo3.get().getLines().size(), 1);
    }

    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void test_getOsdrDetails() throws ReceivingException, IOException {
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(null);
    when(auditLogPersisterService.getAuditTagCountByDeliveryNumberAndAuditStatus(anyLong(), any()))
        .thenReturn(5);
    OsdrSummary osdrSummary = rdcOsdrService.getOsdrDetails(12345l, null, null, "sysadmin");
    assertNotNull(osdrSummary);
    assertEquals(osdrSummary.getOpenDockTags().getCount().intValue(), 0);
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_createOsdrPo() {
    rdcOsdrService.createOsdrPo(new ReceivingCountSummary());
  }

  private OsdrSummary getReceiptsFromRDS() throws IOException {
    OsdrSummary osdrSummary = null;
    resource = new ClassPathResource("OsdrReceiptsSummary.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    return osdrSummary;
  }

  private OsdrSummary getReceiptsFromOfflineAndRds() throws IOException {
    OsdrSummary osdrSummary = null;
    resource = new ClassPathResource("osdrSummaryOffline.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    return osdrSummary;
  }

  private OsdrSummary getReceiptsFromRDSForDSDCDelivery() throws IOException {
    OsdrSummary osdrSummary = null;
    resource = new ClassPathResource("OsdrReceiptsSummaryForDSDC.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    return osdrSummary;
  }

  private OsdrSummary getReceiptsFromRdsSinglePo() throws IOException {
    OsdrSummary osdrSummary = null;
    resource = new ClassPathResource("OsdrReceiptsSummarySinglePo.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    return osdrSummary;
  }

  @Test
  public void test_getOsdrSummary_AtlasReceivingEnabled_Consolidate_Multiple_Po_Lines()
      throws ReceivingException, IOException {
    String mixedPo = "1621130072";
    String onlyAtlasPo = "1467778688";
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    List<ReceiptSummaryVnpkResponse> atlasReceipts =
        MockReceiptsData.getReceiptsFromAtlasMultiPoPoL();
    ReceiptSummaryVnpkResponse newReceipt =
        new ReceiptSummaryVnpkResponse(mixedPo, 1, "ZA", 10L, 20L);
    atlasReceipts.add(newReceipt);
    List<ReceiptSummaryVnpkResponse> atlasRcvdPackCountReceipts =
        MockReceiptsData.getRcvdPackCountByDeliveryForMultiplePO();
    ReceiptSummaryVnpkResponse newRcvdPackCountReceipt = new ReceiptSummaryVnpkResponse(mixedPo, 0);
    atlasRcvdPackCountReceipts.add(newRcvdPackCountReceipt);
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(atlasRcvdPackCountReceipts);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong())).thenReturn(atlasReceipts);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (summary.getPurchaseReferenceNumber().equals(mixedPo)) {
        assertEquals(CutOverType.MIXED.getType(), summary.getCutOverType());
      } else if (summary.getPurchaseReferenceNumber().equals(onlyAtlasPo)) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());
      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 17);
    Optional<OsdrPo> osdrPo =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals(mixedPo))
            .findAny();
    assertEquals(osdrPo.get().getPurchaseReferenceNumber(), mixedPo);
    assertEquals(osdrPo.get().getRcvdQty(), Integer.valueOf(804));
    assertEquals(osdrPo.get().getOrderFilledQty(), Integer.valueOf(415));
    assertEquals(osdrPo.get().getLines().size(), 43);
    assertEquals(osdrPo.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(35));
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_Consolidate_Multiple_Po_Lines_CalculatePutawayQtyFromContainers_SamePOReceivedInOfflineAndAtlas()
          throws ReceivingException, IOException {
    String atlasRdsReceivedPoPoLine = "1079701343";
    String onlyAtlasPo = "1467778688";
    List<ReceipPutawayQtySummaryByContainer> receipPutawayQtySummaryByContainers =
        new ArrayList<>();
    ReceipPutawayQtySummaryByContainer receipPutawayQtySummaryByContainer =
        new ReceipPutawayQtySummaryByContainer("1079701343", 1, 10L);
    receipPutawayQtySummaryByContainers.add(receipPutawayQtySummaryByContainer);
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromOfflineAndRds());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    List<ReceiptSummaryVnpkResponse> atlasReceipts = new ArrayList<>();
    ReceiptSummaryVnpkResponse newReceipt =
        new ReceiptSummaryVnpkResponse(atlasRdsReceivedPoPoLine, 1, "ZA", 20L, 10L);
    atlasReceipts.add(newReceipt);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong())).thenReturn(atlasReceipts);
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliveryForMultiplePO());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    when(containerPersisterService.getReceiptPutawayQtySummaryByDeliveryNumber(anyLong()))
        .thenReturn(receipPutawayQtySummaryByContainers);

    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (summary.getPurchaseReferenceNumber().equals(atlasRdsReceivedPoPoLine)) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());
      } else if (summary.getPurchaseReferenceNumber().equals(onlyAtlasPo)) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());
      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 34);
    Optional<OsdrPo> osdrPo =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(
                summary -> summary.getPurchaseReferenceNumber().equals(atlasRdsReceivedPoPoLine))
            .findAny();
    assertEquals(osdrPo.get().getPurchaseReferenceNumber(), atlasRdsReceivedPoPoLine);
    assertEquals(osdrPo.get().getRcvdQty(), Integer.valueOf(200));
    assertEquals(osdrPo.get().getOrderFilledQty(), Integer.valueOf(10));
    assertEquals(osdrPo.get().getLines().size(), 1);
    assertEquals(osdrPo.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(10));
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_Consolidate_Multiple_Po_Lines_CalculatePutawayQtyFromContainers()
          throws ReceivingException, IOException {
    String mixedPo = "1621130072";
    String onlyAtlasPo = "1467778688";
    List<ReceipPutawayQtySummaryByContainer> receipPutawayQtySummaryByContainers =
        new ArrayList<>();
    ReceipPutawayQtySummaryByContainer receipPutawayQtySummaryByContainer =
        new ReceipPutawayQtySummaryByContainer("1621130072", 1, 10L);
    receipPutawayQtySummaryByContainers.add(receipPutawayQtySummaryByContainer);
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDS());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    List<ReceiptSummaryVnpkResponse> atlasReceipts =
        MockReceiptsData.getReceiptsFromAtlasMultiPoPoL();
    ReceiptSummaryVnpkResponse newReceipt =
        new ReceiptSummaryVnpkResponse(mixedPo, 1, "ZA", 10L, 20L);
    atlasReceipts.add(newReceipt);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong())).thenReturn(atlasReceipts);
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliveryForMultiplePO());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    when(containerPersisterService.getReceiptPutawayQtySummaryByDeliveryNumber(anyLong()))
        .thenReturn(receipPutawayQtySummaryByContainers);

    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (summary.getPurchaseReferenceNumber().equals(mixedPo)) {
        assertEquals(CutOverType.MIXED.getType(), summary.getCutOverType());
      } else if (summary.getPurchaseReferenceNumber().equals(onlyAtlasPo)) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());
      } else {
        assertEquals(CutOverType.NON_ATLAS.getType(), summary.getCutOverType());
      }
    }
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 17);
    Optional<OsdrPo> osdrPo =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals(mixedPo))
            .findAny();
    assertEquals(osdrPo.get().getPurchaseReferenceNumber(), mixedPo);
    assertEquals(osdrPo.get().getRcvdQty(), Integer.valueOf(804));
    assertEquals(osdrPo.get().getOrderFilledQty(), Integer.valueOf(345));
    assertEquals(osdrPo.get().getLines().size(), 43);
    assertEquals(osdrPo.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(25));
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_Consolidate_Multiple_Po_Lines_DSDCDeliveries_PackCountFromRDS()
          throws ReceivingException, IOException {
    String mixedPo = "6805687930";
    String onlyAtlasPo = "1467778688";
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDSForDSDCDelivery());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    List<ReceiptSummaryVnpkResponse> atlasReceipts =
        MockReceiptsData.getReceiptsFromAtlasMultiPoPoL();
    ReceiptSummaryVnpkResponse newReceipt =
        new ReceiptSummaryVnpkResponse(mixedPo, 1, "ZA", 10L, 20L);
    atlasReceipts.add(newReceipt);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong())).thenReturn(atlasReceipts);
    List<ReceiptSummaryVnpkResponse> atlasRcvdPackCountReceipts =
        MockReceiptsData.getRcvdPackCountByDeliveryForMultiplePO();
    ReceiptSummaryVnpkResponse newRcvdPackCountReceipt = new ReceiptSummaryVnpkResponse(mixedPo, 2);
    atlasRcvdPackCountReceipts.add(newRcvdPackCountReceipt);
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(atlasRcvdPackCountReceipts);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    for (OsdrPo summary : osdrSummaryResponse.getSummary()) {
      if (summary.getPurchaseReferenceNumber().equals(mixedPo)) {
        assertEquals(CutOverType.MIXED.getType(), summary.getCutOverType());
      } else if (summary.getPurchaseReferenceNumber().equals(onlyAtlasPo)) {
        assertEquals(CutOverType.ATLAS.getType(), summary.getCutOverType());
      }
    }

    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 4);
    Optional<OsdrPo> osdrPo =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals(mixedPo))
            .findAny();
    assertEquals(osdrPo.get().getPurchaseReferenceNumber(), mixedPo);
    assertEquals(osdrPo.get().getRcvdQty(), Integer.valueOf(9061));
    assertEquals(osdrPo.get().getRcvdPackCount(), Integer.valueOf(358));
    assertEquals(osdrPo.get().getOrderFilledQty(), Integer.valueOf(9071));
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void test_getOsdrSummary_AtlasReceivingEnabled_Consolidate_Multiple_Po_Lines_Same_Line()
      throws ReceivingException, IOException {
    String mixedPo = "1467778615";
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRdsSinglePo());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    List<ReceiptSummaryVnpkResponse> atlasReceipts =
        MockReceiptsData.getReceiptsFromAtlasSinglePoPoL();
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong())).thenReturn(atlasReceipts);
    when(receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(anyLong()))
        .thenReturn(MockReceiptsData.getRcvdPackCountByDeliverySinglePoPoL());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    String osdrResponse = gson.toJson(osdrSummaryResponse);
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 1);
    Optional<OsdrPo> osdrPo =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(summary -> summary.getPurchaseReferenceNumber().equals(mixedPo))
            .findAny();
    if (osdrPo.isPresent()) {
      assertEquals(osdrPo.get().getPurchaseReferenceNumber(), mixedPo);
      assertEquals(osdrPo.get().getRcvdQty(), Integer.valueOf(38));
      assertEquals(osdrPo.get().getOrderFilledQty(), Integer.valueOf(30));
      assertEquals(osdrPo.get().getLines().size(), 1);
      assertEquals(osdrPo.get().getLines().get(0).getOrderFilledQty(), Integer.valueOf(30));
      assertEquals(CutOverType.ATLAS.getType(), osdrPo.get().getCutOverType());
    }
    verify(receiptCustomRepository, times(1))
        .getReceivedPackCountSummaryByDeliveryNumber(anyLong());
  }

  private OsdrSummary getReceiptsFromRDSForMultiplePO() throws IOException {
    OsdrSummary osdrSummary = null;
    resource = new ClassPathResource("OsdrReceiptsSummary_MultiplePO.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    return osdrSummary;
  }

  private OsdrSummary getReceiptsFromRDSForDifferentPO() throws IOException {
    OsdrSummary osdrSummary = null;
    resource = new ClassPathResource("OsdrReceiptsSummary_DifferentPO.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    return osdrSummary;
  }

  @Test
  public void test_getOsdrSummary_AtlasReceivingEnabled_MultiPoPoLLessThanCase()
      throws ReceivingException, IOException {
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDSForMultiplePO());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasMultiPoPoLLessThanCase());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    List<OsdrPo> osdrPoList =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(osdrPo -> osdrPo.getPurchaseReferenceNumber().equals("1621130073"))
            .collect(Collectors.toList());

    assertNotNull(osdrSummaryResponse);
    assertNotNull(osdrPoList);
    assertTrue(osdrPoList.size() > 0);
    assertTrue(osdrPoList.get(0).isLessThanCaseRcvd());
    assertEquals(osdrPoList.get(0).getRcvdQtyUom(), ReceivingConstants.Uom.VNPK);
    assertEquals(osdrPoList.get(0).getRcvdQty().intValue(), 0);
    List<OsdrPoLine> osdrPoLineList =
        osdrPoList
            .get(0)
            .getLines()
            .stream()
            .filter(osdrPoLine -> osdrPoLine.getLineNumber() == 3)
            .collect(Collectors.toList());
    assertTrue(osdrPoLineList.size() > 0);
    assertTrue(osdrPoLineList.get(0).isLessThanCaseRcvd());

    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
  }

  @Test
  public void
      test_getOsdrSummary_AtlasReceivingEnabled_SamePoSameLineHasLessThanCaseAndVendorPackReceived()
          throws ReceivingException, IOException {
    when(ngrClient.getDeliveryReceipts(anyLong(), any(HttpHeaders.class)))
        .thenReturn(getReceiptsFromRDSForDifferentPO());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);
    when(receiptCustomRepository.receiptSummaryByDelivery(anyLong()))
        .thenReturn(MockReceiptsData.getReceiptsFromAtlasDifferentPoPoLLessThanCase());
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(1);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    OsdrSummary osdrSummaryResponse =
        rdcOsdrService.getOsdrSummary(12345L, MockHttpHeaders.getHeaders());
    List<OsdrPo> osdrPoList =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .filter(osdrPo -> osdrPo.getPurchaseReferenceNumber().equals("1669115709"))
            .collect(Collectors.toList());
    assertNotNull(osdrPoList);
    assertTrue(osdrPoList.size() > 0);
    assertTrue(osdrPoList.get(0).isLessThanCaseRcvd());
    assertEquals(osdrPoList.get(0).getRcvdQtyUom(), ReceivingConstants.Uom.VNPK);
    assertEquals(osdrPoList.get(0).getRcvdQty().intValue(), 30);
    List<OsdrPoLine> osdrPoLineList =
        osdrPoList
            .get(0)
            .getLines()
            .stream()
            .filter(osdrPoLine -> osdrPoLine.getLineNumber() == 3)
            .collect(Collectors.toList());
    assertTrue(osdrPoLineList.size() > 0);
    assertTrue(osdrPoLineList.get(0).isLessThanCaseRcvd());

    assertNotNull(osdrSummaryResponse);
    verify(ngrClient, times(1)).getDeliveryReceipts(anyLong(), any(HttpHeaders.class));
    verify(dockTagService, times(1)).countOfOpenDockTags(anyLong());
  }
}
