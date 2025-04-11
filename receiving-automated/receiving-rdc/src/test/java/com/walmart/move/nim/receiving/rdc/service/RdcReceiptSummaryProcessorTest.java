package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.gdm.AsyncGdmRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.AsyncNimRdsRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsSummaryByPo;
import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsSummaryByPoResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.StoreDistribution;
import com.walmart.move.nim.receiving.core.client.orderwell.OrderWellClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcReceiptSummaryProcessorTest {
  @InjectMocks private RdcReceiptSummaryProcessor rdcReceiptSummaryProcessor;
  @Mock private RdcOsdrService rdcOsdrSummaryService;
  @Mock private AsyncGdmRestApiClient asyncGdmRestApiClient;
  @Mock private AsyncNimRdsRestApiClient asyncNimRdsRestApiClient;
  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @Mock private AppConfig appConfig;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private LabelDataService labelDataService;

  @Mock private OrderWellClient orderWellClient;

  private Gson gson = new Gson();
  File resource = null;
  OsdrSummary osdrSummary = null;

  @BeforeClass
  public void setRootUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
    resource = new ClassPathResource("OsdrReceiptsSummary.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    ReflectionTestUtils.setField(rdcReceiptSummaryProcessor, "gson", this.gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        rdcOsdrSummaryService,
        asyncGdmRestApiClient,
        asyncNimRdsRestApiClient,
        appConfig,
        receiptService,
        rdcManagedConfig,
        tenantSpecificConfigReader,
        nimRDSRestApiClient);
  }

  @Test
  public void testReceivedQtySummaryInVnpkByDelivery_Success() throws ReceivingException {
    when(rdcOsdrSummaryService.getOsdrSummary(anyLong(), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);
    List<ReceiptSummaryResponse> response =
        rdcReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(12345L);
    assertTrue(response.size() > 0);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceivedQtySummaryInVnpkByDelivery_NoReceiptsAvailableAndReturnsEmptyResponse()
      throws ReceivingException {
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_DELIVERY_RECEIPTS_REQ,
                String.format(ReceivingException.NO_RECEIPTS_FOUND_ERROR_MESSAGE, 12345L)))
        .when(rdcOsdrSummaryService)
        .getOsdrSummary(anyLong(), any(HttpHeaders.class));
    List<ReceiptSummaryResponse> response =
        rdcReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(12345L);
    assertEquals(response.size(), 0);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testGetDeliverySummaryByPoReturnsReceipts_PoReceivedInBothRDSAndAtlas_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(false);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(anyLong(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(getMockRdsPoReceiptsSummaryResponse()));
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 400);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 400);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void testGetDeliverySummaryByPoReturnsReceipts_PoReceivedOnlyInRDS_RdsIntegrationEnabled()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(false);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(anyLong(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(getMockRdsPoReceiptsSummaryResponse()));
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 100);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 100);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void
      testGetDeliverySummaryByPoReturnsReceipts_PoReceivedOnlyInRDS_NonAtlasItemsReceivingFlow_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(false);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(anyLong(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(getMockRdsPoReceiptsSummaryResponse()));
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 100);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 100);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void
      testGetDeliverySummaryByPoReturnsReceipts_PoReceivedOnlyInAtlas_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(false);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(anyLong(), any(Map.class)))
        .thenReturn(
            CompletableFuture.completedFuture(getMockRdsPoReceiptsSummaryResponseEmptyReceipts()));
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 300);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 300);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void
      testGetDeliverySummaryByPoReturnsReceipts_PoReceivedInBothRDSAndAtlas_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 300);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void testGetDeliverySummaryByPoReturnsReceipts_PoReceivedOnlyInRDS_RdsIntegrationDisabled()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 0);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void
      testGetDeliverySummaryByPoReturnsReceipts_PoReceivedOnlyInRDS_NonAtlasItemsReceivingFlow_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 0);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(0)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void
      testGetDeliverySummaryByPoReturnsReceipts_PoReceivedOnlyInAtlas_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(anyLong()))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 300);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPo> receiptSummaryQtyByPo =
        receiptSummaryQtyByPoResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("4211300997"))
            .findAny();
    if (receiptSummaryQtyByPo.isPresent()) {
      recievedQty = receiptSummaryQtyByPo.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 300);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetDeliverySummaryByPoReturnsReceiptsGDMThrowsException()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    doThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ReceivingException.GDM_GET_DELIVERY_BY_URI))
        .when(asyncGdmRestApiClient)
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));

    rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(deliveryNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(0)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetDeliverySummaryByPoReturnsReceiptsNimRDSThrowsException()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    doThrow(new ReceivingBadDataException("mockErrorCode", "mockErrorMessage"))
        .when(asyncNimRdsRestApiClient)
        .getReceivedQtySummaryByPo(anyLong(), any(Map.class));

    rdcReceiptSummaryProcessor.getReceiptsSummaryByPo(deliveryNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedInBothRDSAndAtlas_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "6506871436";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
            anyLong(), anyString(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(getMockRdsPoLineReceiptsSummaryResponse()));
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(getMockReceiptSummaryResponseByPoLine());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 400);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedOnlyInRDS_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    ;
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
            anyLong(), anyString(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(getMockRdsPoLineReceiptsSummaryResponse()));
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 100);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedOnlyInAtlas_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
            anyLong(), anyString(), any(Map.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                getMockRdsPoLineReceiptsSummaryResponseEmptyReceipts()));
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 300);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedOnlyInRDS_NonAtlasItemReceivingFlow_RdsIntegrationEnabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(false);
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    when(asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
            anyLong(), anyString(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(getMockRdsPoLineReceiptsSummaryResponse()));
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 100);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(0)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedInBothRDSAndAtlas_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "6506871436";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(getMockReceiptSummaryResponseByPoLine());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedOnlyInRDS_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedOnlyInAtlas_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);
    assertEquals((int) recievedQty, 300);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void
      testGetDeliverySummaryByPoLineReturnsReceipts_PoLineReceivedOnlyInRDS_NonAtlasItemReceivingFlow_RdsIntegrationDisabled()
          throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo());
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString()))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    Integer recievedQty = null;
    Optional<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLine =
        receiptSummaryQtyByPoLineResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(receipt -> receipt.getLineNumber().equals(1))
            .findAny();
    if (receiptSummaryQtyByPoLine.isPresent()) {
      recievedQty = receiptSummaryQtyByPoLine.get().getReceivedQty();
    }

    assertNotNull(recievedQty);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(0)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetDeliverySummaryByPoLineReturnsReceiptsGDMThrowsException()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "323232333";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    doThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ReceivingException.GDM_GET_DELIVERY_BY_URI))
        .when(asyncGdmRestApiClient)
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));

    rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
        deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(0))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetDeliverySummaryByPoLineReturnsReceiptsNimRDSThrowsException()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "323232333";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo()));
    doThrow(new ReceivingBadDataException("mockErrorCode", "mockErrorMessage"))
        .when(asyncNimRdsRestApiClient)
        .getReceivedQtySummaryByPo(anyLong(), any(Map.class));

    rdcReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
        deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
    verify(receiptService, times(0)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  public void testGetReceivedQtySummaryByDeliveries_ReceiptsExistsOnlyInRDSAndNoReceiptsInAtlas()
      throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(nimRDSRestApiClient.getReceivedQtySummaryByDeliveryNumbers(
            any(ReceiptSummaryQtyByDeliveries.class), any(Map.class)))
        .thenReturn(getReceivedQtySummaryByDeliveries());
    when(receiptService.receivedQtySummaryByDeliveryNumbers(anyList()))
        .thenReturn(Collections.emptyList());

    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponses =
        rdcReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            getMockReceiptSummaryQtyByDeliveries(), MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByDeliveryNumberResponses);
    assertTrue(receiptSummaryQtyByDeliveryNumberResponses.size() > 0);
    assertEquals(
        receiptSummaryQtyByDeliveryNumberResponses.get(0).getReceivedQty().intValue(), 323);

    verify(nimRDSRestApiClient, times(1))
        .getReceivedQtySummaryByDeliveryNumbers(
            any(ReceiptSummaryQtyByDeliveries.class), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).receivedQtySummaryByDeliveryNumbers(anyList());
  }

  @Test
  public void testGetReceivedQtySummaryByDeliveries_ReceiptsExistsBothInRDSAndAtlas()
      throws ReceivingException {
    List<ReceiptSummaryVnpkResponse> receiptSummaryList = new ArrayList<>();
    ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse1 =
        new ReceiptSummaryVnpkResponse(3243434L, 100L, "ZA");
    ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse2 =
        new ReceiptSummaryVnpkResponse(5332323L, 100L, "ZA");
    receiptSummaryList.add(receiptSummaryVnpkResponse1);
    receiptSummaryList.add(receiptSummaryVnpkResponse2);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(nimRDSRestApiClient.getReceivedQtySummaryByDeliveryNumbers(
            any(ReceiptSummaryQtyByDeliveries.class), any(Map.class)))
        .thenReturn(getReceivedQtySummaryByDeliveries());
    when(receiptService.receivedQtySummaryByDeliveryNumbers(anyList()))
        .thenReturn(receiptSummaryList);

    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponses =
        rdcReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            getMockReceiptSummaryQtyByDeliveries(), MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByDeliveryNumberResponses);
    assertTrue(receiptSummaryQtyByDeliveryNumberResponses.size() > 0);
    assertEquals(
        receiptSummaryQtyByDeliveryNumberResponses.get(0).getReceivedQty().intValue(), 423);
    assertEquals(
        receiptSummaryQtyByDeliveryNumberResponses.get(1).getReceivedQty().intValue(), 200);

    verify(nimRDSRestApiClient, times(1))
        .getReceivedQtySummaryByDeliveryNumbers(
            any(ReceiptSummaryQtyByDeliveries.class), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(1)).receivedQtySummaryByDeliveryNumbers(anyList());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetReceivedQtySummaryByDeliveries_NimRdsThrowsException()
      throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    doThrow(
            new ReceivingException(
                String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "I/O Error"),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionCodes.RDS_RECEIVED_QTY_SUMMARY_BY_DELIVERY_NUMBERS))
        .when(nimRDSRestApiClient)
        .getReceivedQtySummaryByDeliveryNumbers(
            any(ReceiptSummaryQtyByDeliveries.class), any(Map.class));

    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponses =
        rdcReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            getMockReceiptSummaryQtyByDeliveries(), MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(nimRDSRestApiClient, times(1))
        .getReceivedQtySummaryByDeliveryNumbers(
            any(ReceiptSummaryQtyByDeliveries.class), any(Map.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false);
    verify(receiptService, times(0)).receivedQtySummaryByDeliveryNumbers(anyList());
  }

  private RdsReceiptsSummaryByPoResponse getMockRdsPoReceiptsSummaryResponseEmptyReceipts()
      throws IOException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    List<RdsReceiptsSummaryByPo> rdsPoReceiptsSummaryList = new ArrayList<>();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      RdsReceiptsSummaryByPo rdsPoReceiptsSummary = new RdsReceiptsSummaryByPo();
      rdsPoReceiptsSummary.setPurchaseReferenceNumber(
          deliveryDocument.getPurchaseReferenceNumber());
      rdsPoReceiptsSummary.setReceivedQty(0);
      rdsPoReceiptsSummaryList.add(rdsPoReceiptsSummary);
    }
    RdsReceiptsSummaryByPoResponse response = new RdsReceiptsSummaryByPoResponse();
    response.setDeliveryNumber(2323223L);
    response.setReceivedQty(0);
    response.setSummary(rdsPoReceiptsSummaryList);
    return response;
  }

  private ReceiptSummaryQtyByPoLineResponse getMockRdsPoLineReceiptsSummaryResponseEmptyReceipts()
      throws IOException {
    String purchaseReferenceNumber = "4211300997";
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    Optional<DeliveryDocument> deliveryDocument =
        deliveryDocumentList
            .stream()
            .filter(
                document ->
                    document.getPurchaseReferenceNumber().equalsIgnoreCase(purchaseReferenceNumber))
            .findAny();

    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines = new ArrayList<>();
    for (DeliveryDocumentLine documentLine : deliveryDocument.get().getDeliveryDocumentLines()) {
      ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine = new ReceiptSummaryQtyByPoLine();
      receiptSummaryQtyByPoLine.setLineNumber(documentLine.getPurchaseReferenceLineNumber());
      receiptSummaryQtyByPoLine.setItemNumber(documentLine.getItemNbr().intValue());
      receiptSummaryQtyByPoLine.setReceivedQty(0);
      receiptSummaryQtyByPoLines.add(receiptSummaryQtyByPoLine);
    }
    ReceiptSummaryQtyByPoLineResponse response = new ReceiptSummaryQtyByPoLineResponse();
    response.setPurchaseReferenceNumber(purchaseReferenceNumber);
    response.setSummary(receiptSummaryQtyByPoLines);
    return response;
  }

  private RdsReceiptsSummaryByPoResponse getMockRdsPoReceiptsSummaryResponse() throws IOException {
    RdsReceiptsSummaryByPoResponse rdsPoReceiptsSummaryResponse =
        new RdsReceiptsSummaryByPoResponse();
    RdsReceiptsSummaryByPo rdsPoReceiptsSummary = new RdsReceiptsSummaryByPo();
    rdsPoReceiptsSummary.setPurchaseReferenceNumber("4211300997");
    rdsPoReceiptsSummary.setReceivedQty(100);
    List<RdsReceiptsSummaryByPo> receiptsSummaries = new ArrayList<>();
    receiptsSummaries.add(rdsPoReceiptsSummary);
    rdsPoReceiptsSummaryResponse.setReceivedQty(100);
    rdsPoReceiptsSummaryResponse.setDeliveryNumber(2323223L);
    rdsPoReceiptsSummaryResponse.setSummary(receiptsSummaries);
    return rdsPoReceiptsSummaryResponse;
  }

  private ReceiptSummaryQtyByPoLineResponse getMockRdsPoLineReceiptsSummaryResponse()
      throws IOException {
    ReceiptSummaryQtyByPoLine rdsPoReceiptsSummaryResponse = new ReceiptSummaryQtyByPoLine();
    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLineList = new ArrayList<>();
    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        new ReceiptSummaryQtyByPoLineResponse();
    rdsPoReceiptsSummaryResponse.setLineNumber(1);
    rdsPoReceiptsSummaryResponse.setReceivedQty(100);
    receiptSummaryQtyByPoLineList.add(rdsPoReceiptsSummaryResponse);
    receiptSummaryQtyByPoLineResponse.setPurchaseReferenceNumber("6506871436");
    receiptSummaryQtyByPoLineResponse.setReceivedQtyUom(ReceivingConstants.Uom.VNPK);
    receiptSummaryQtyByPoLineResponse.setSummary(receiptSummaryQtyByPoLineList);
    return receiptSummaryQtyByPoLineResponse;
  }

  private List<ReceiptQtySummaryByDeliveryNumberResponse> getReceivedQtySummaryByDeliveries() {
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryResponse = new ArrayList<>();
    ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryQtyByDeliveryNumberResponse1 =
        new ReceiptQtySummaryByDeliveryNumberResponse();
    receiptSummaryQtyByDeliveryNumberResponse1.setDeliveryNumber(3243434L);
    receiptSummaryQtyByDeliveryNumberResponse1.setReceivedQty(323L);
    receiptSummaryQtyByDeliveryNumberResponse1.setReceivedQtyUom(VNPK);
    ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryQtyByDeliveryNumberResponse2 =
        new ReceiptQtySummaryByDeliveryNumberResponse();
    receiptSummaryQtyByDeliveryNumberResponse2.setDeliveryNumber(5332323L);
    receiptSummaryQtyByDeliveryNumberResponse2.setReceivedQty(100L);
    receiptSummaryQtyByDeliveryNumberResponse2.setReceivedQtyUom(VNPK);
    receiptSummaryResponse.add(receiptSummaryQtyByDeliveryNumberResponse1);
    receiptSummaryResponse.add(receiptSummaryQtyByDeliveryNumberResponse2);
    return receiptSummaryResponse;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponse() {
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    receiptSummaryResponse.setReceivedQty(300L);
    receiptSummaryResponse.setPurchaseReferenceNumber("4211300997");
    receiptSummaryResponse.setPurchaseReferenceLineNumber(1);
    receiptSummaryResponseList.add(receiptSummaryResponse);
    return receiptSummaryResponseList;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponseByPoLine() {
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    receiptSummaryResponse.setReceivedQty(300L);
    receiptSummaryResponse.setPurchaseReferenceNumber("6506871436");
    receiptSummaryResponse.setPurchaseReferenceLineNumber(1);
    receiptSummaryResponseList.add(receiptSummaryResponse);
    return receiptSummaryResponseList;
  }

  @Test
  public void test_getReceiptsSummaryByPoResponseWithAsn() throws IOException {
    Long deliveryNumber = 31266428l;
    File resource = new ClassPathResource("GdmMappedResponseV2_RDC_deliverySummary.json").getFile();
    String deliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryResponse, GdmPOLineResponse.class);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        rdcReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmPOLineResponse.getDeliveryDocuments(), receiptSummaryResponseList, null);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQtyUom(), ReceivingConstants.Uom.VNPK);
    assertNotNull(receiptSummaryQtyByPoResponse.getShipments());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull((receiptSummaryQtyByPoResponse.getAsnQty()));
    assertEquals(receiptSummaryQtyByPoResponse.getAsnQty().intValue(), 262);
  }

  @Test
  public void test_getReceiptsSummaryByPoResponseWithOutAsn() throws IOException {
    Long deliveryNumber = 31266428L;
    File resource =
        new ClassPathResource("GdmMappedResponseV2_RDC_deliverySummaryNoAsn.json").getFile();
    String deliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryResponse, GdmPOLineResponse.class);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        rdcReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmPOLineResponse.getDeliveryDocuments(), receiptSummaryResponseList, null);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rdcReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQtyUom(), ReceivingConstants.Uom.VNPK);
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertEquals(receiptSummaryQtyByPoResponse.getShipments().size(), 0);
    assertNotNull((receiptSummaryQtyByPoResponse.getAsnQty()));
    assertEquals(receiptSummaryQtyByPoResponse.getAsnQty().intValue(), 0);
  }

  private ReceiptSummaryQtyByDeliveries getMockReceiptSummaryQtyByDeliveries() {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    receiptSummaryQtyByDeliveries.setDeliveries(Collections.singletonList("3243434,5332323"));
    return receiptSummaryQtyByDeliveries;
  }

  private Pair<Integer, List<StoreDistribution>> getMockedPairOfIntegerAndStoreDistribution()
      throws IOException {
    File resource = new ClassPathResource("NimRdsMappedResponseStoreDistribution.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<StoreDistribution> storeDistributions =
        Arrays.asList(gson.fromJson(mockResponse, StoreDistribution[].class));
    Pair<Integer, List<StoreDistribution>> mockedPair = new Pair<>(1, storeDistributions);
    return mockedPair;
  }

  @Test()
  public void test_getStoreDistributionByDeliveryNumberPoLinePoLineNumberNonEmptyPair()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "6506871436";
    Integer purchaseReferenceLineNumber = 1;

    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDocumentsPoDistByPoAndPoLine()));
    when(asyncNimRdsRestApiClient.getStoreDistributionByDeliveryDocument(
            anyString(), anyInt(), any(Map.class)))
        .thenReturn(
            CompletableFuture.completedFuture(getMockedPairOfIntegerAndStoreDistribution()));

    List<DeliveryDocument> deliveryDocuments =
        rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            MockHttpHeaders.getHeaders(),
            false);
    assertNotNull(deliveryDocuments);
    assertNotNull(deliveryDocuments.stream().map(document -> document.getDeliveryNumber()));

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getStoreDistributionByDeliveryDocument(anyString(), anyInt(), any(Map.class));
  }

  @Test()
  public void test_getStoreDistributionByDeliveryNumberPoLinePoLineNumberEmptyPair()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "6506871436";
    int purchaseReferenceLineNumber = 1;
    Pair<Integer, List<StoreDistribution>> mockedEmptyPair = new Pair<>(0, Collections.emptyList());

    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDocumentsPoDistByPoAndPoLine()));
    when(asyncNimRdsRestApiClient.getStoreDistributionByDeliveryDocument(
            anyString(), anyInt(), any(Map.class)))
        .thenReturn(CompletableFuture.completedFuture(mockedEmptyPair));

    List<DeliveryDocument> deliveryDocuments =
        rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            MockHttpHeaders.getHeaders(),
            false);
    assertNotNull(deliveryDocuments);
    assertNotNull(deliveryDocuments.stream().map(document -> document.getDeliveryNumber()));

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getStoreDistributionByDeliveryDocument(anyString(), anyInt(), any(Map.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_getStoreDistributionByDeliveryNumberPoLinePoLineNumberThrowException()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "6506871436";
    int purchaseReferenceLineNumber = 1;

    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(asyncGdmRestApiClient.getDeliveryDetails(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                MockDeliveryDocuments.getDeliveryDocumentsPoDistByPoAndPoLine()));
    doThrow(
            new ReceivingInternalException(
                String.format(
                    ReceivingException.GET_STORE_DISTR_ERROR_MESSAGE,
                    purchaseReferenceNumber,
                    "Error"),
                ReceivingException.STORE_DISTRIBUTION_SERVER_ERROR))
        .when(asyncNimRdsRestApiClient)
        .getStoreDistributionByDeliveryDocument(anyString(), anyInt(), any(Map.class));

    rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders(),
        false);

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(asyncGdmRestApiClient, times(1))
        .getDeliveryDetails(any(URI.class), any(HttpHeaders.class));
    verify(asyncNimRdsRestApiClient, times(1))
        .getStoreDistributionByDeliveryDocument(anyString(), anyInt(), any(Map.class));
  }

  @Test
  public void testGetDistributionDetailsByDeliveryPoPoLinewithMfcENabledandDisabledBoth()
      throws ReceivingException, IOException {
    Long deliveryNumber = 29905991L;
    String purchaseReferenceNumber = "2962730137";
    Integer purchaseReferenceLineNumber = 1;
    Pair<Integer, List<StoreDistribution>> mockedEmptyPair = new Pair<>(0, Collections.emptyList());
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG,
            Objects.requireNonNull(getFacilityNum())))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MFC_DISTRIBUTION_PALLET_PULL_SUPPORTED,
            Objects.requireNonNull(getFacilityNum())))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getCcmValue(getFacilityNum(), "mfcAlignedStores", "0"))
        .thenReturn("988");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getAtlasDeliveryDocumentsPoDistByPoAndPoLine());
    when(receiptService.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(8l);

    when(appConfig.getOrderWellDaOrdersPotype()).thenReturn(Collections.singletonList(3));

    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(atlasMockLabelData());
    when(orderWellClient.getStoreMFCDistributionforStoreNbrandPO(mockOrderWellRequest1()))
        .thenReturn(atlasMockOrderWellResponse1());
    when(orderWellClient.getStoreMFCDistributionforStoreNbrandPO(mockOrderWellRequest2()))
        .thenReturn(atlasMockOrderWellResponse2());

    rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders(),
        true);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetDistributionDetailsByDeliveryPoPoLinewithInvalidPotypeForDaOrders()
      throws ReceivingException, IOException {
    Long deliveryNumber = 2323223L;
    String purchaseReferenceNumber = "6506871436";
    Integer purchaseReferenceLineNumber = 1;
    Pair<Integer, List<StoreDistribution>> mockedEmptyPair = new Pair<>(0, Collections.emptyList());
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(appConfig.isMfcAlignedStoreEnabled()).thenReturn(Boolean.valueOf("true"));
    when(appConfig.getMfcAlignedStores())
        .thenReturn(Collections.singletonList(TenantContext.getFacilityNum()));
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(
            MockDeliveryDocuments
                .getAtlasDeliveryDocumentsPoDistByPoAndPoLineofInvalidPOtypeForDAOrder());
    when(receiptService.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(8l);

    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(atlasMockLabelData());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG,
            Objects.requireNonNull(getFacilityNum())))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MFC_DISTRIBUTION_PALLET_PULL_SUPPORTED,
            Objects.requireNonNull(getFacilityNum())))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getCcmValue(getFacilityNum(), "mfcAlignedStores", "0"))
        .thenReturn("988");
    when(orderWellClient.getStoreMFCDistributionforStoreNbrandPO(mockOrderWellRequest1()))
        .thenReturn(atlasMockOrderWellResponse1());
    when(orderWellClient.getStoreMFCDistributionforStoreNbrandPO(mockOrderWellRequest2()))
        .thenReturn(atlasMockOrderWellResponse2());
    when(appConfig.getOrderWellDaOrdersPotype()).thenReturn(Collections.singletonList(3));

    rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders(),
        true);
  }

  @Test()
  public void testGetDistributionDetailsByDeliveryPoPoLinehappypathonlywithMFcAllignedStores()
      throws ReceivingException, IOException {
    Long deliveryNumber = 29905991L;
    String purchaseReferenceNumber = "2962730137";
    Integer purchaseReferenceLineNumber = 1;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getAtlasDeliveryDocumentsPoDistByPoAndPoLinebyStoreNbr());
    when(receiptService.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(8l);

    when(appConfig.getOrderWellDaOrdersPotype()).thenReturn(Collections.singletonList(3));

    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(atlasMockLabelData());
    when(orderWellClient.getStoreMFCDistributionforStoreNbrandPO(any(OrderWellZoneRequest.class)))
        .thenReturn(atlasMockOrderWellResponse2());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG,
            Objects.requireNonNull(getFacilityNum())))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MFC_DISTRIBUTION_PALLET_PULL_SUPPORTED,
            Objects.requireNonNull(getFacilityNum())))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getCcmValue(getFacilityNum(), "mfcAlignedStores", "0"))
        .thenReturn("988");

    rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders(),
        true);
  }

  public void testGetDistributionDetailsByDeliveryPoPoLineWithMFCStoresOnly()
      throws ReceivingException, IOException {
    Long deliveryNumber = 29905991L;
    String purchaseReferenceNumber = "2962730137";
    Integer purchaseReferenceLineNumber = 1;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(appConfig.isMfcAlignedStoreEnabled()).thenReturn(Boolean.valueOf("true"));
    when(appConfig.getMfcAlignedStores())
        .thenReturn(Collections.singletonList(TenantContext.getFacilityNum()));
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getAtlasDeliveryDocumentsPoDistByPoAndPoLinebyStoreNbr());
    when(receiptService.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(8l);

    when(appConfig.getOrderWellDaOrdersPotype()).thenReturn(Collections.singletonList(3));

    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(atlasMockLabelData());
    when(orderWellClient.getStoreMFCDistributionforStoreNbrandPO(any(OrderWellZoneRequest.class)))
        .thenReturn(invalidatlasMockOrderWellResponse2());

    rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders(),
        true);
  }

  @Test()
  public void testGetDistributionDetailsByDeliveryPoPoLinehappypathonlyWithoutMFCStoresEnabled()
      throws ReceivingException, IOException {
    Long deliveryNumber = 29905991L;
    String purchaseReferenceNumber = "2962730137";
    Integer purchaseReferenceLineNumber = 1;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(appConfig.isMfcAlignedStoreEnabled()).thenReturn(Boolean.valueOf("false"));
    when(appConfig.getMfcAlignedStores())
        .thenReturn(Collections.singletonList(TenantContext.getFacilityNum()));
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockDeliveryDocuments.getAtlasDeliveryDocumentsPoDistByPoAndPoLinebyStoreNbr());
    when(receiptService.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(8l);
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(atlasMockLabelDatawithDestTypeasNull());

    rdcReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        MockHttpHeaders.getHeaders(),
        true);
  }

  private List<LabelData> mockLabelData() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setStatus("COMPLETE");
    labelData.setAllocation(getAllocationinstance("US", "988"));
    LabelData labelData2 = new LabelData();
    labelData2.setId(2);
    labelData2.setFacilityNum(32679);
    labelData2.setDeliveryNumber(39380405l);
    labelData2.setItemNumber(658232698l);
    labelData2.setPurchaseReferenceNumber("5030140191");
    labelData2.setPurchaseReferenceLineNumber(1);
    labelData2.setStatus("COMPLETE");
    labelData2.setAllocation(getAllocationinstance("US", "106"));
    return Arrays.asList(labelData, labelData2);
  }

  public LabelDataAllocationDTO getAllocationinstance(String countryCode, String buNumber) {
    Facility facility = new Facility();
    facility.setBuNumber(buNumber);
    facility.setCountryCode(countryCode);
    InstructionDownloadContainerDTO insDow = new InstructionDownloadContainerDTO();
    insDow.setFinalDestination(facility);
    LabelDataAllocationDTO dto = new LabelDataAllocationDTO();
    dto.setContainer(insDow);
    return dto;
  }

  private OrderWellZoneResponse atlasMockOrderWellResponse2() {
    OrderWellZoneResponse orderWellZoneResponse = new OrderWellZoneResponse();
    List<OrderWellDistributionResponse> data = new ArrayList<>();
    OrderWellDistributionResponse orderWellDistributionResponse =
        new OrderWellDistributionResponse();
    OWMfcDistribution owMfcDistribution = new OWMfcDistribution();
    owMfcDistribution.setSourceNbr(TenantContext.getFacilityNum());
    owMfcDistribution.setDestNbr(988);
    owMfcDistribution.setZone("MFC");
    owMfcDistribution.setWmtItemNbr(553008749);
    owMfcDistribution.setWhpkOrderQty(4);

    OWStoreDistribution owStoreDistribution = new OWStoreDistribution();
    owStoreDistribution.setSourceNbr(TenantContext.getFacilityNum());
    owStoreDistribution.setDestNbr(988);
    owStoreDistribution.setZone("ST");
    owStoreDistribution.setWmtItemNbr(553008749);
    owStoreDistribution.setWhpkOrderQty(4);
    orderWellDistributionResponse.setMfcDistribution(owMfcDistribution);
    orderWellDistributionResponse.setStoreDistribution(owStoreDistribution);
    data.add(orderWellDistributionResponse);
    orderWellZoneResponse.setData(data);
    return orderWellZoneResponse;
  }

  private OrderWellZoneResponse invalidatlasMockOrderWellResponse2() {
    OrderWellZoneResponse orderWellZoneResponse = new OrderWellZoneResponse();
    List<OrderWellDistributionResponse> data = new ArrayList<>();
    OrderWellDistributionResponse orderWellDistributionResponse =
        new OrderWellDistributionResponse();
    OWMfcDistribution owMfcDistribution = new OWMfcDistribution();
    owMfcDistribution.setSourceNbr(TenantContext.getFacilityNum());
    owMfcDistribution.setDestNbr(988);
    owMfcDistribution.setZone("MFC");
    owMfcDistribution.setWmtItemNbr(553008749);
    owMfcDistribution.setWhpkOrderQty(4);

    OWStoreDistribution owStoreDistribution = new OWStoreDistribution();
    owStoreDistribution.setSourceNbr(TenantContext.getFacilityNum());
    owStoreDistribution.setDestNbr(988);
    owStoreDistribution.setZone("ST");
    owStoreDistribution.setWmtItemNbr(553008749);
    owStoreDistribution.setWhpkOrderQty(4);
    orderWellDistributionResponse.setMfcDistribution(owMfcDistribution);
    orderWellDistributionResponse.setStoreDistribution(owStoreDistribution);

    OrderWellDistributionResponse orderWellDistributionResponse1 =
        new OrderWellDistributionResponse();

    OWMfcDistribution owMfcDistribution1 = new OWMfcDistribution();
    owMfcDistribution1.setSourceNbr(TenantContext.getFacilityNum());
    owMfcDistribution1.setDestNbr(9881);
    owMfcDistribution1.setZone("MFC");
    owMfcDistribution1.setWmtItemNbr(553008749);
    owMfcDistribution1.setWhpkOrderQty(4);

    OWStoreDistribution owStoreDistribution1 = new OWStoreDistribution();
    owStoreDistribution1.setSourceNbr(TenantContext.getFacilityNum());
    owStoreDistribution1.setDestNbr(9881);
    owStoreDistribution1.setZone("ST");
    owStoreDistribution1.setWmtItemNbr(553008749);
    owStoreDistribution1.setWhpkOrderQty(4);
    orderWellDistributionResponse1.setMfcDistribution(owMfcDistribution1);
    orderWellDistributionResponse1.setStoreDistribution(owStoreDistribution1);

    data.add(orderWellDistributionResponse);
    data.add(orderWellDistributionResponse1);
    orderWellZoneResponse.setData(data);
    return orderWellZoneResponse;
  }

  private OrderWellZoneResponse atlasMockOrderWellResponse1() {
    OrderWellZoneResponse orderWellZoneResponse = new OrderWellZoneResponse();
    List<OrderWellDistributionResponse> data = new ArrayList<>();
    OrderWellDistributionResponse orderWellDistributionResponse =
        new OrderWellDistributionResponse();
    OWStoreDistribution owStoreDistribution = new OWStoreDistribution();
    owStoreDistribution.setSourceNbr(TenantContext.getFacilityNum());
    owStoreDistribution.setDestNbr(3347);
    owStoreDistribution.setZone("ST");
    owStoreDistribution.setWmtItemNbr(553008749);
    owStoreDistribution.setWhpkOrderQty(4);
    orderWellDistributionResponse.setStoreDistribution(owStoreDistribution);
    data.add(orderWellDistributionResponse);
    orderWellZoneResponse.setData(data);
    return orderWellZoneResponse;
  }

  public LabelDataAllocationDTO getAllocationinstance1(
      String countryCode, String buNumber, String destType) {
    Facility facility = new Facility();
    facility.setBuNumber(buNumber);
    facility.setCountryCode(countryCode);
    facility.setDestType(destType);
    InstructionDownloadContainerDTO insDow = new InstructionDownloadContainerDTO();
    insDow.setFinalDestination(facility);
    LabelDataAllocationDTO dto = new LabelDataAllocationDTO();
    dto.setContainer(insDow);
    return dto;
  }

  private List<LabelData> atlasMockLabelData() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(4);
    labelData.setStatus("COMPLETE");
    labelData.setAllocation(getAllocationinstance1("US", "988", "STORE"));
    LabelData labelData1 = new LabelData();
    labelData1.setId(2);
    labelData1.setFacilityNum(32679);
    labelData1.setDeliveryNumber(39380405l);
    labelData1.setItemNumber(658232698l);
    labelData1.setPurchaseReferenceNumber("5030140191");
    labelData1.setPurchaseReferenceLineNumber(4);
    labelData1.setStatus("COMPLETE");
    labelData1.setAllocation(getAllocationinstance1("US", "988", "STORE"));
    return Arrays.asList(labelData, labelData1);
  }

  private OrderWellZoneRequest mockOrderWellRequest1() {
    OrderWellZoneRequest orderWellZoneRequest = new OrderWellZoneRequest();
    List<OrderWellStoreDistribution> orderWellStoreDistributions = new ArrayList<>();
    OrderWellStoreDistribution orderWellStoreDistribution = new OrderWellStoreDistribution();
    orderWellStoreDistribution.setSourceNbr(TenantContext.getFacilityNum());
    orderWellStoreDistribution.setWmtItemNbr(553008749);
    orderWellStoreDistribution.setDestNbr(3347);
    orderWellStoreDistribution.setPoNbr("2962730137");
    orderWellStoreDistribution.setPoType(3);
    orderWellStoreDistribution.setWhpkOrderQty(4);
    orderWellStoreDistributions.add(orderWellStoreDistribution);
    orderWellZoneRequest.setData(orderWellStoreDistributions);
    return orderWellZoneRequest;
  }

  private OrderWellZoneRequest mockOrderWellRequest2() {
    OrderWellZoneRequest orderWellZoneRequest = new OrderWellZoneRequest();
    List<OrderWellStoreDistribution> orderWellStoreDistributions = new ArrayList<>();
    OrderWellStoreDistribution orderWellStoreDistribution = new OrderWellStoreDistribution();
    orderWellStoreDistribution.setSourceNbr(TenantContext.getFacilityNum());
    orderWellStoreDistribution.setWmtItemNbr(553008749);
    orderWellStoreDistribution.setDestNbr(988);
    orderWellStoreDistribution.setPoNbr("6506871436");
    orderWellStoreDistribution.setPoType(3);
    orderWellStoreDistribution.setWhpkOrderQty(8);
    orderWellStoreDistributions.add(orderWellStoreDistribution);
    orderWellZoneRequest.setData(orderWellStoreDistributions);
    return orderWellZoneRequest;
  }

  private List<LabelData> atlasMockLabelDatawithDestTypeasNull() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(4);
    labelData.setStatus("COMPLETE");
    labelData.setAllocation(getAllocationinstance1("US", "988", null));
    LabelData labelData1 = new LabelData();
    labelData1.setId(2);
    labelData1.setFacilityNum(32679);
    labelData1.setDeliveryNumber(39380405l);
    labelData1.setItemNumber(658232698l);
    labelData1.setPurchaseReferenceNumber("5030140191");
    labelData1.setPurchaseReferenceLineNumber(4);
    labelData1.setStatus("COMPLETE");
    labelData1.setAllocation(getAllocationinstance1("US", "988", null));
    return Arrays.asList(labelData, labelData1);
  }

  @Test
  public void testGetReceiptQtySummaryByDeliveriesWithRdsIntegrationDisabled() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false))
        .thenReturn(true);
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponses =
        rdcReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            getMockReceiptSummaryQtyByDeliveries(), MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByDeliveryNumberResponses);
    assertTrue(receiptSummaryQtyByDeliveryNumberResponses.isEmpty());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RDS_INTEGRATION_DISABLED,
            false);
  }
}
