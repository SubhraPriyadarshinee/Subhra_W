package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequest;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AuditService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);

  @Resource(name = ReceivingConstants.BEAN_RETRYABLE_CONNECTOR)
  private RestConnector retryableRestConnector;

  private @ManagedConfiguration AppConfig appConfig;

  @Autowired private Gson gson;

  @Timed(name = "Audit-Flag", level1 = "uwms-receiving", level2 = "Audit-Flag")
  @ExceptionCounted(
      name = "Audit-Flag-Exception",
      level1 = "uwms-receiving",
      level2 = "Audit-Flag-Exception")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "Audit-Flag",
      type = Type.REST,
      externalCall = true)
  public List<AuditFlagResponse> retrieveItemAuditInfo(
      AuditFlagRequest auditFlagRequest, HttpHeaders httpHeaders) {
    LOGGER.info("Audit Request: {}", gson.toJson(auditFlagRequest));
    String auditFlagUrl = appConfig.getAuditBaseUrl() + ReceivingConstants.AUDIT_FLAG_PATH;
    HttpEntity httpEntity = new HttpEntity(gson.toJson(auditFlagRequest), httpHeaders);

    ResponseEntity<String> responseEntity = null;
    try {
      responseEntity =
          retryableRestConnector.exchange(auditFlagUrl, HttpMethod.POST, httpEntity, String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          auditFlagUrl,
          auditFlagRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.AUDIT_INFO_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.AUDIT_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          auditFlagUrl,
          auditFlagRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.AUDIT_INFO_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.AUDIT_SERVICE_DOWN_ERROR_MSG, e.getMessage()));
    }

    if (Objects.isNull(responseEntity) || !responseEntity.hasBody()) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_INFO_MESSAGE,
          auditFlagUrl,
          auditFlagRequest,
          responseEntity);
      throw new ReceivingBadDataException(
          ExceptionCodes.AUDIT_INFO_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.AUDIT_SERVICE_DOWN_ERROR_MSG, auditFlagRequest));
    }
    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE,
        auditFlagUrl,
        auditFlagRequest,
        responseEntity.getBody());
    List<AuditFlagResponse> auditFlagResponseList =
        Arrays.asList(gson.fromJson(responseEntity.getBody(), AuditFlagResponse[].class));
    LOGGER.info("AuditInfo: {}", gson.toJson(auditFlagResponseList));
    return auditFlagResponseList;
  }
}
