/**
 * TenantFilterInjectorAspect inject where clouse in jpa queries so query returns data only for
 * given facilityNum and facilityCountryCode this way receiving utilizes same data base for all
 * tenants. receiving tenancy model is discriminator column(s)
 */
package com.walmart.move.nim.receiving.core.advice;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/** @author m0g028p */
@Aspect
@Component
public class TenantFilterInjectorAspect {

  private static final String TENANT_FILTER = "tenantFilter";

  @PersistenceContext(unitName = "primaryPersistenceUnit")
  EntityManager entityManager;

  @Before(value = "@annotation(injectTenantFilter)")
  public void injectWhereClause(JoinPoint pip, InjectTenantFilter injectTenantFilter) {
    org.hibernate.Filter filter = entityManager.unwrap(Session.class).enableFilter(TENANT_FILTER);
    addTenantFilter(filter);
  }

  private void addTenantFilter(Filter filter) {
    filter.setParameter(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
    filter.setParameter(
        ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    filter.validate();
  }
}
