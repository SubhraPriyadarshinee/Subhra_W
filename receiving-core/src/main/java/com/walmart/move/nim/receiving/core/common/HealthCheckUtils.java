package com.walmart.move.nim.receiving.core.common;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import org.springframework.stereotype.Component;

@Component
public class HealthCheckUtils {

  @PersistenceContext EntityManager em;

  /** Checks system Timestamp of DB server */
  public void checkDatabaseHealth() {
    Query query = em.createNativeQuery("SELECT CURRENT_TIMESTAMP");
    query.getResultList().get(0);
  }
}
