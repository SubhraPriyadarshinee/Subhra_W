package com.walmart.move.nim.receiving.core.job;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.core.service.OsdrProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * This is a cron job for processing osdr details. This is scheduled based on ccm
 * configuration @{@link OsdrConfig}
 */
@Conditional(EnableInPrimaryRegionNodeCondition.class)
@Component
public class OsdrSchedulerJob {
  private static final Logger LOGGER = LoggerFactory.getLogger(OsdrSchedulerJob.class);
  @ManagedConfiguration private OsdrConfig osdrConfig;
  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Scheduled(cron = "${osdr.config.scheduler.cron}")
  @SchedulerLock(
      name = "${shedlock.prefix:}" + "OsdrSchedulerJob_osdrScheduler_fc",
      lockAtLeastFor = 60000,
      lockAtMostFor = 90000)
  @TimeTracing(
      component = AppComponent.CORE,
      type = com.walmart.move.nim.receiving.core.advice.Type.SCHEDULER,
      flow = "OSDR-Job")
  public void osdrScheduler() {
    LOGGER.info("OsdrSchedulerJob started.");
    List<OsdrConfigSpecification> osdrConfigSpecifications = getOsdrConfigSpecifications();
    if (!CollectionUtils.isEmpty(osdrConfigSpecifications)) {
      String correlationId = UUID.randomUUID().toString();
      for (OsdrConfigSpecification osdrConfigSpecification : osdrConfigSpecifications) {
        if (shouldExecuteForTenant(osdrConfigSpecification)) continue;

        setTenantContext(osdrConfigSpecification, correlationId);
        setMDC();
        try {
          OsdrProcessor osdrProcessor =
              tenantSpecificConfigReader.getConfiguredInstance(
                  String.valueOf(TenantContext.getFacilityNum()),
                  ReceivingConstants.OSDR_PROCESSOR,
                  OsdrProcessor.class);
          osdrProcessor.process(osdrConfigSpecification);
          LOGGER.info(
              "Osdr processed for [osdrConfigSpecification={}]",
              gson.toJson(osdrConfigSpecification));
        } catch (Exception e) {
          LOGGER.error(
              ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE, ExceptionUtils.getStackTrace(e));
        }
      }
    } else {
      LOGGER.warn(
          "OsdrConfigSpecification is not available [osdrConfigSpecification={}]",
          gson.toJson(osdrConfig.getSpecifications()));
    }
    clearTenantContext();
    LOGGER.info("OsdrSchedulerJob completed.");
  }

  private boolean shouldExecuteForTenant(OsdrConfigSpecification osdrConfigSpecification) {
    int currentMinute = Calendar.getInstance().get(Calendar.MINUTE);
    if (osdrConfigSpecification.getFrequencyFactor() != 0
        && !(currentMinute % osdrConfigSpecification.getFrequencyFactor() == 0)) {
      LOGGER.warn(
          "No Matching OSDR for = {} . So, moving on...",
          ReceivingUtils.stringfyJson(osdrConfigSpecification));
      return true;
    }
    return false;
  }

  /**
   * This method is responsible for getting the list of @{@link OsdrConfigSpecification}
   *
   * @return @{@link List}
   */
  private List<OsdrConfigSpecification> getOsdrConfigSpecifications() {
    String specifications = osdrConfig.getSpecifications();
    if (StringUtils.isEmpty(specifications)) {
      return null;
    }
    Type OsdrConfigSpecificationType =
        new TypeToken<ArrayList<OsdrConfigSpecification>>() {
          private static final long serialVersionUID = 1L;
        }.getType();
    return gson.fromJson(specifications, OsdrConfigSpecificationType);
  }

  /**
   * This method is responsible for setting up tenant context.
   *
   * @param osdrConfigSpecification
   * @param correlationId
   */
  private void setTenantContext(
      OsdrConfigSpecification osdrConfigSpecification, String correlationId) {
    TenantContext.setFacilityNum(osdrConfigSpecification.getFacilityNum());
    TenantContext.setFacilityCountryCode(osdrConfigSpecification.getFacilityCountryCode());
    TenantContext.setCorrelationId(correlationId);
  }

  /** This method is responsible for setting up @{@link MDC} context. */
  private void setMDC() {
    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
  }

  private void clearTenantContext() {
    TenantContext.clear();
    MDC.clear();
  }
}
