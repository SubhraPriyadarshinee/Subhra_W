/**
 * MTFilter = MultiTenant filter. atlas receiving is a multitenant solution incoming http request
 * must contain facilityNum, facilityCountryCode http headers this method extracts those values from
 * http header and set in ThreadLocal variables. those variables are used in various layers further
 * down in the code such as injecting a where clause in db queries
 */
package com.walmart.move.nim.receiving.core.filter;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MTFilter implements Filter {
  private static final Logger LOGGER = LoggerFactory.getLogger(MTFilter.class);

  @Override
  public void destroy() {
    MDC.clear();
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    // TODO: Temporary fix for UI integration
    ((HttpServletResponse) res).setHeader("Access-Control-Allow-Origin", "*");
    ((HttpServletResponse) res).setHeader("Access-Control-Allow-Headers", "*");
    ((HttpServletResponse) res).setHeader("Access-Control-Allow-Methods", "*");
    HttpServletRequestWrapper httpRequest =
        new HttpServletRequestWrapper((HttpServletRequest) req) {
          private Set<String> headerNameSet;

          @Override
          public Enumeration<String> getHeaderNames() {
            if (headerNameSet == null) {
              // first time this method is called, cache the wrapped request's header names:
              headerNameSet = new HashSet<>();
              Enumeration<String> wrappedHeaderNames = super.getHeaderNames();
              while (wrappedHeaderNames.hasMoreElements()) {
                String headerName = wrappedHeaderNames.nextElement();
                if (!ReceivingConstants.HOST.equalsIgnoreCase(headerName)) {
                  headerNameSet.add(headerName);
                }
              }
            }
            return Collections.enumeration(headerNameSet);
          }

          @Override
          public Enumeration<String> getHeaders(String name) {
            if (ReceivingConstants.HOST.equalsIgnoreCase(name)) {
              return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
          }

          @Override
          public String getHeader(String name) {
            if (ReceivingConstants.HOST.equalsIgnoreCase(name)) {
              return null;
            }
            return super.getHeader(name);
          }
        };

    // Enabling CORS for swagger
    if (allowMT(httpRequest)) {
      chain.doFilter(httpRequest, res);
    } else {
      String facilityNum = httpRequest.getHeader(ReceivingConstants.TENENT_FACLITYNUM);
      String facilityCountryCode = httpRequest.getHeader(ReceivingConstants.TENENT_COUNTRY_CODE);
      String userId = httpRequest.getHeader(ReceivingConstants.USER_ID_HEADER_KEY);
      String correlationId = httpRequest.getHeader(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
      String clientRequestTime = httpRequest.getHeader(ReceivingConstants.CLIENT_REQUEST_TIME);
      String subcenterId = httpRequest.getHeader(ReceivingConstants.SUBCENTER_ID_HEADER);
      String orgUnitId = httpRequest.getHeader(ReceivingConstants.ORG_UNIT_ID_HEADER);

      if (!StringUtils.isEmpty(facilityNum)
          && !StringUtils.isEmpty(facilityCountryCode)
          && !StringUtils.isEmpty(userId)) {

        TenantContext.setFacilityCountryCode(facilityCountryCode);
        TenantContext.setFacilityNum(Integer.parseInt(facilityNum));
        TenantContext.setCorrelationId(correlationId);
        TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, userId);
        if (nonNull(subcenterId)) TenantContext.setSubcenterId(Integer.parseInt(subcenterId));
        if (isNotBlank(orgUnitId)) TenantContext.setOrgUnitId(Integer.parseInt(orgUnitId));

        MDC.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum);
        MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode);
        MDC.put(ReceivingConstants.USER_ID_HEADER_KEY, userId);
        MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
        MDC.put(ReceivingConstants.CLIENT_REQUEST_TIME, clientRequestTime);

        try {
          chain.doFilter(httpRequest, res);
        } finally {
          /*Clear MDC context once served the request
           * Clear Tenant context in ThreadLocal*/
          MDC.clear();
          TenantContext.clear();
        }
      } else {
        LOGGER.info("{} {}", "MT ", "no tenant id/user provided its a bad request");
        ((HttpServletResponse) res)
            .sendError(HttpStatus.BAD_REQUEST.value(), ReceivingConstants.INVALID_TENENT_INFO);
      }
    }
  }

  private boolean allowMT(HttpServletRequest httpRequest) {
    if ((httpRequest.getMethod() != null
            && httpRequest.getMethod().equals(HttpMethod.OPTIONS.name())
        || httpRequest.getRequestURI().startsWith("/v2/api-docs")
        || (httpRequest.getRequestURI().equals("/report/stats")
            || httpRequest.getRequestURI().equals("/report/search")
            || httpRequest.getRequestURI().equals("/report/stats/")
            || httpRequest.getRequestURI().startsWith("/report/authenticate")
            || httpRequest.getRequestURI().equals("/report/search/")))) {
      return true;
    }
    return false;
  }

  @Override
  public void init(FilterConfig arg0) {
    // TODO Auto-generated method stub
  }
}
