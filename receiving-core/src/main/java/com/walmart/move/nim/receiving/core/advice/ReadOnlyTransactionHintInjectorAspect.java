/**
 * TenantFilterInjectorAspect inject where clouse in jpa queries so query returns data only for
 * given facilityNum and facilityCountryCode this way receiving utilizes same data base for all
 * tenants. receiving tenancy model is discriminator column(s)
 */
package com.walmart.move.nim.receiving.core.advice;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Aspect
@Component
public class ReadOnlyTransactionHintInjectorAspect {

  @PersistenceContext(unitName = "primaryPersistenceUnit")
  private EntityManager entityManager;

  @Before(value = "@annotation(readOnlyTransaction)")
  public void injectWhereClause(JoinPoint pip, ReadOnlyTransaction readOnlyTransaction) {
    Session session = entityManager.unwrap(Session.class);
    session.setDefaultReadOnly(true);
  }
}
