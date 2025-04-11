package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.*;
import org.springframework.http.HttpHeaders;

/**
 * This service will cache lpn in bulk based on tenant and provide them on demand by tenant
 *
 * @author g0k0072
 */
public interface LPNCacheService {
  public String getLPNBasedOnTenant(HttpHeaders httpHeaders);

  public List<String> getLPNSBasedOnTenant(int count, HttpHeaders httpHeaders)
      throws ReceivingException;
}
