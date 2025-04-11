/**
 * TenantFilterInjectorAspectSecondary inject where clause in jpa queries for the secondary data
 * source
 */
package com.walmart.move.nim.receiving.reporting.advice;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/** @author sks0013 */
@Aspect
@Component
public class TenantFilterInjectorAspectSecondary {

  private static final String TENANT_FILTER = "tenantFilter";

  @PersistenceContext(unitName = ReportingConstants.SECONDARY_PERSISTENCE_UNIT)
  EntityManager secondaryEntityManager;

  @Before(value = "@annotation(injectTenantFilterSecondary)")
  public void injectWhereClauseForSecondary(
      JoinPoint pip, InjectTenantFilterSecondary injectTenantFilterSecondary) {
    Filter filter = secondaryEntityManager.unwrap(Session.class).enableFilter(TENANT_FILTER);
    addTenantFilter(filter);
  }

  private void addTenantFilter(Filter filter) {
    filter.setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
    filter.setParameter(
        ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    filter.validate();
  }
}
