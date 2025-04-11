package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LPNCacheServiceInMemoryImplTest extends ReceivingTestBase {
  @Mock private AppConfig appConfig;

  @Mock private LPNService lpnService;

  private final String[] lpnSet1 = {
    "c32987000000000000000001",
    "c32987000000000000000002",
    "c32987000000000000000003",
    "c32987000000000000000004",
    "c32987000000000000000005",
    "c32987000000000000000006"
  };

  private final String[] lpnSet2 = {
    "c32987000000000000000007",
    "c32987000000000000000008",
    "c32987000000000000000009",
    "c32987000000000000000010"
  };

  private final String[] lpnSet3 = {"c32987000000000000000007"};

  @InjectMocks private LPNCacheServiceInMemoryImpl lpnCacheServiceInMemoryImpl;

  private HttpHeaders httpHeaders;

  @BeforeClass
  private void setup() {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    MockitoAnnotations.openMocks(this);
    httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    doReturn(6).when(appConfig).getLpnCacheMaxStoreCount();
    doReturn(.5f).when(appConfig).getLpnCacheThreshold();
    doReturn(3000).when(appConfig).getLpnCachePollTimeout();
  }

  @AfterTest
  private void resetMocks() {
    reset(lpnService);
    reset(appConfig);
  }

  @Test
  public void testGetLPNForNewTanant_shouldReturnLPNAfterCallingLPNService() {
    TenantContext.setFacilityNum(32987);
    httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet1))))
        .when(lpnService)
        .retrieveLPN(anyInt(), any());
    String lpn = lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders);
    assertNotEquals(lpn, null);
    verify(lpnService, times(1)).retrieveLPN(eq(6), any(HttpHeaders.class));
  }

  @Test
  public void testGetLPNForExistingTanant_shouldReturnLPNAndNotCallLPNServiceIfInCapacity() {
    TenantContext.setFacilityNum(32899);
    httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32899");
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet1))))
        .when(lpnService)
        .retrieveLPN(eq(6), any(HttpHeaders.class));
    // Setup cache for a tenant with max capacity 6
    String lpn1 = lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders);
    assertNotEquals(lpn1, null);
    verify(lpnService, times(1)).retrieveLPN(eq(6), any(HttpHeaders.class));
    // Call get lpn as the cache is already created for the tenant
    reset(lpnService);
    String lpn2 = lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders);
    assertNotEquals(lpn2, null);
    assertNotEquals(lpn1, lpn2);
    // Was above threshold till last fetch. So, should not call lpn service
    verify(lpnService, times(0)).retrieveLPN(eq(6), any(HttpHeaders.class));
  }

  @Test
  public void testGetLPNForOldTanant_shouldReturnLPNAndCallLPNServiceIfOutOfCapacity() {
    TenantContext.setFacilityNum(6159);
    httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "6159");
    reset(lpnService);
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet1))))
        .when(lpnService)
        .retrieveLPN(eq(6), any(HttpHeaders.class));
    // getLPNBasedOnTenant will setup cache for a tenant with max capacity 6 and return lpn
    assertNotEquals(lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders), null);
    verify(lpnService, times(1)).retrieveLPN(eq(6), any(HttpHeaders.class));
    // subsequent call will not call lpnService as the cache already created for the tenant
    reset(lpnService);
    assertNotEquals(lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders), null);
    assertNotEquals(lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders), null);
    assertNotEquals(lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders), null);
    // Was above threshold till last fetch. So, should not call lpn service
    verify(lpnService, times(0)).retrieveLPN(eq(6), any(HttpHeaders.class));
    // In next fetch since the cache went below threshold. So, call lpn service to replenish cache
    // with next set of lpns
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet2))))
        .when(lpnService)
        .retrieveLPN(eq(4), any(HttpHeaders.class));
    lpnCacheServiceInMemoryImpl.getLPNBasedOnTenant(httpHeaders);
    verify(lpnService, times(1)).retrieveLPN(eq(4), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetLPNSWhenLPNServiceReturnsEmptyLPNSet_shouldThrowReceivingException()
      throws ReceivingException {
    TenantContext.setFacilityNum(32899);
    httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32899");
    doReturn(CompletableFuture.completedFuture(new HashSet<>()))
        .when(lpnService)
        .retrieveLPN(eq(6), any(HttpHeaders.class));
    lpnCacheServiceInMemoryImpl.getLPNSBasedOnTenant(6, httpHeaders);
  }

  @Test
  public void testGetLPNSBasedOnTenant_happyPath() throws ReceivingException {
    TenantContext.setFacilityNum(32891);
    httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32891");
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet1))))
        .when(lpnService)
        .retrieveLPN(eq(6), any(HttpHeaders.class));
    List<String> lpns = lpnCacheServiceInMemoryImpl.getLPNSBasedOnTenant(6, httpHeaders);
    assertEquals(lpns.size(), 6);
  }

  @Test
  public void testGetLPNSBasedOnTenant_AskedBeyondCapacity() throws ReceivingException {
    TenantContext.setFacilityNum(32892);
    httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32892");
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet1))))
        .when(lpnService)
        .retrieveLPN(eq(6), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(new HashSet<>(Arrays.asList(lpnSet3))))
        .when(lpnService)
        .retrieveLPN(eq(1), any(HttpHeaders.class));

    lpnCacheServiceInMemoryImpl.getLPNSBasedOnTenant(7, httpHeaders);
  }
}
