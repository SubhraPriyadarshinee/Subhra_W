package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.TenantSpecificLPNCache;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class LPNCacheServiceInMemoryImpl implements LPNCacheService {
  private static final Logger LOGGER = LoggerFactory.getLogger(LPNCacheServiceInMemoryImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  private LPNService lpnService;

  private Map<Pair<String, String>, TenantSpecificLPNCache> lpnCache;

  public LPNCacheServiceInMemoryImpl() {
    this.lpnCache = new ConcurrentHashMap<>();
  }

  private final String FACILITY_NUMBER = "facilityNumber";

  /**
   * This will give a call to {@link LPNService} to retrieve required amount of LPNs and store them
   * into memory/cache/store
   *
   * @param count no of LPNs that needs to be fetched
   */
  private void pullLPN(
      TenantSpecificLPNCache tenantSpecificLPNCache, int count, HttpHeaders httpHeaders) {
    LOGGER.info("Going to pull the LPN of count={}", count);
    CompletableFuture<Set<String>> completableFutureResponse =
        getLpnService().retrieveLPN(count, httpHeaders);
    completableFutureResponse.whenCompleteAsync(
        (lpnSet, throwable) -> {
          BlockingQueue<String> lpnCache = tenantSpecificLPNCache.getLpnCache();
          if (throwable != null) {
            LOGGER.error("Error during Async processing", throwable);
          } else {
            lpnCache.addAll(lpnSet);
          }
          tenantSpecificLPNCache.setRequested(Boolean.FALSE);
        });
  }

  /**
   * This method checks if cache for passed tenant key exists or not if it doesn't exists then it
   * creates one
   *
   * @param tenantKey key to identify tenant
   * @return tenant specific lpn cache
   */
  private TenantSpecificLPNCache getOrCreateTenantSpecificLPNCache(Pair<String, String> tenantKey) {
    TenantSpecificLPNCache tenantSpecificLPNCache = lpnCache.get(tenantKey);
    if (Objects.isNull(tenantSpecificLPNCache)) {
      tenantSpecificLPNCache =
          new TenantSpecificLPNCache(
              tenantKey.getKey(),
              tenantKey.getValue(),
              new ArrayBlockingQueue<>(appConfig.getLpnCacheMaxStoreCount()),
              Boolean.FALSE);
      lpnCache.put(tenantKey, tenantSpecificLPNCache);
    }
    return tenantSpecificLPNCache;
  }

  /**
   * This method ensures that capacity of a tenant specific caches never goes below threshold
   * required capacity is max store count{@link AppConfig#lpnCacheMaxStoreCount} * lpn cache
   * threshold {@link AppConfig#lpnCacheThreshold}
   *
   * @param tenantSpecificLPNCache tenant specific lpn cache
   * @param httpHeaders http headers passed by the requester to identify tenant
   */
  private void ensureTenantSpecificLPNCacheCapacity(
      TenantSpecificLPNCache tenantSpecificLPNCache, HttpHeaders httpHeaders) {
    float requiredCapacity =
        appConfig.getLpnCacheMaxStoreCount() * appConfig.getLpnCacheThreshold();
    boolean hasRequiredCapacity = tenantSpecificLPNCache.getLpnCache().size() >= requiredCapacity;
    if (!hasRequiredCapacity && !tenantSpecificLPNCache.isRequested()) {
      int neededCapacity =
          appConfig.getLpnCacheMaxStoreCount() - tenantSpecificLPNCache.getLpnCache().size();
      LOGGER.info(
          "Requesting lpn for tenant {} - {}. Requested Nos of LPN = {}",
          tenantSpecificLPNCache.getFacilityCountryCode(),
          tenantSpecificLPNCache.getFacilityNum(),
          neededCapacity);
      tenantSpecificLPNCache.setRequested(Boolean.TRUE);
      // start Async processing
      pullLPN(tenantSpecificLPNCache, neededCapacity, httpHeaders);
    }
  }

  /**
   * Retrieves and removes the head of this memory/cache/store, waiting up to the specified wait
   * time if necessary for LPN to become available. If the {@link Queue} size reaches to a threshold
   * of {@link AppConfig#lpnCacheThreshold} then, an Async task will get initiate to {@link
   * LPNService} to fill the cache/store {@link Queue} to {@link AppConfig#lpnCacheMaxStoreCount}
   *
   * @return String
   */
  @Counted(
      name = "getLPNBasedOnTenantHitCount",
      level1 = "uwms-receiving",
      level2 = "lpnCacheServiceInMemoryImpl",
      level3 = "getLPNBasedOnTenant")
  @Timed(
      name = "getLPNBasedOnTenantTimed",
      level1 = "uwms-receiving",
      level2 = "lpnCacheServiceInMemoryImpl",
      level3 = "getLPNBasedOnTenant")
  @ExceptionCounted(
      name = "getLPNBasedOnTenantCount",
      level1 = "uwms-receiving",
      level2 = "lpnCacheServiceInMemoryImpl",
      level3 = "getLPNBasedOnTenant")
  public String getLPNBasedOnTenant(HttpHeaders httpHeaders) {
    Pair<String, String> tenantKey =
        new Pair<>(
            httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
            httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));

    HttpHeaders forwardableHttpHeaders =
        ReceivingUtils.getForwardableHttpHeaders(
            httpHeaders); // removes host and other unnecessary headers
    forwardableHttpHeaders.set(
        ReceivingConstants.PRODUCT_NAME_HEADER_KEY, ReceivingConstants.APP_NAME_VALUE);
    forwardableHttpHeaders.set(
        FACILITY_NUMBER, httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    TenantSpecificLPNCache tenantSpecificLPNCache = getOrCreateTenantSpecificLPNCache(tenantKey);
    ensureTenantSpecificLPNCacheCapacity(tenantSpecificLPNCache, forwardableHttpHeaders);
    try {
      return tenantSpecificLPNCache
          .getLpnCache()
          .poll(appConfig.getLpnCachePollTimeout(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      LOGGER.warn("LPN store poll call interrupted!", e);
      // Restore interrupted state...
      Thread.currentThread().interrupt();
      return null;
    }
  }

  public LPNService getLpnService() {
    return lpnService;
  }

  public void setLpnService(LPNService lpnService) {
    this.lpnService = lpnService;
  }

  /**
   * Returns required amount of LPNS based on tenant specified in the headers
   *
   * @param count no of LPNS required
   * @param httpHeaders http headers defining the tenant
   * @return count no of lpns
   */
  private List<String> getLPNSBasedOnTenantForBatch(int count, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<String> fetchedLPNS = new ArrayList<>();
    if (count > 0) {
      Pair<String, String> tenantKey =
          new Pair<>(
              httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
              httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
      TenantSpecificLPNCache tenantSpecificLPNCache = getOrCreateTenantSpecificLPNCache(tenantKey);
      ensureTenantSpecificLPNCacheCapacity(tenantSpecificLPNCache, httpHeaders);
      long seconds = TimeUnit.MILLISECONDS.toSeconds(appConfig.getLpnCachePollTimeout());
      try {
        synchronized (tenantSpecificLPNCache.getLpnCache()) {
          int currentSize = tenantSpecificLPNCache.getLpnCache().size();
          int retrievable = Math.min(currentSize == 0 ? 1 : currentSize, count);
          LOGGER.info(
              "Fetching {} LPN based on tenant {}. Current Size {}",
              retrievable,
              tenantKey,
              currentSize);
          while (tenantSpecificLPNCache.getLpnCache().size() < retrievable) {
            if (seconds > 0) {
              Thread.sleep(5000);
              seconds -= 5;
            } else {
              LOGGER.error(
                  "Not able to retrieve {} LPNS after waiting {} seconds. Current cache size {}",
                  retrievable,
                  seconds,
                  tenantSpecificLPNCache.getLpnCache().size());
              throw new ReceivingException(
                  ReceivingConstants.LPNS_NOT_FOUND,
                  HttpStatus.NOT_FOUND,
                  ExceptionCodes.LPNS_NOT_FOUND);
            }
          }
          tenantSpecificLPNCache.getLpnCache().drainTo(fetchedLPNS, retrievable);
        }
      } catch (InterruptedException e) {
        LOGGER.warn("LPN store poll call interrupted!", e);
        // Restore interrupted state...
        Thread.currentThread().interrupt();
        throw new ReceivingException(
            ReceivingConstants.LPNS_NOT_FOUND, HttpStatus.NOT_FOUND, ExceptionCodes.LPNS_NOT_FOUND);
      }
    }
    return fetchedLPNS;
  }

  /**
   * Returns required amount of LPNS based on tenant specified in the headers by breaking it into
   * batches
   *
   * @param count no of LPNS required
   * @param httpHeaders http headers defining the tenant
   * @return count no of lpns
   */
  @Timed(
      name = "getLPNSBasedOnTenantByTimed",
      level1 = "uwms-receiving",
      level2 = "lpnCacheServiceInMemoryImpl",
      level3 = "getLPNSBasedOnTenant")
  @ExceptionCounted(
      name = "getLPNSBasedOnTenantCount",
      level1 = "uwms-receiving",
      level2 = "lpnCacheServiceInMemoryImpl",
      level3 = "getLPNSBasedOnTenant")
  public List<String> getLPNSBasedOnTenant(int count, HttpHeaders httpHeaders)
      throws ReceivingException {

    HttpHeaders forwardableHttpHeaders =
        ReceivingUtils.getForwardableHttpHeaders(
            httpHeaders); // removes host and other unnecessary headers
    forwardableHttpHeaders.set(
        ReceivingConstants.PRODUCT_NAME_HEADER_KEY, ReceivingConstants.APP_NAME_VALUE);
    List<String> fetchedLPNS = new ArrayList<>();
    while (fetchedLPNS.size() != count) {
      fetchedLPNS.addAll(
          getLPNSBasedOnTenantForBatch(count - fetchedLPNS.size(), forwardableHttpHeaders));
    }
    return fetchedLPNS;
  }
}
