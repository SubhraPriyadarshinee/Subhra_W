package com.walmart.move.nim.receiving.core.advice;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class RestResponseExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(RestResponseExceptionHandler.class);

  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  @Autowired public AsyncPersister asyncPersister;

  @ExceptionHandler(value = {ReceivingException.class})
  protected ResponseEntity<Object> handleError(
      ReceivingException ex, WebRequest request, HandlerMethod handlerMethod) {
    // for custom medusa metrics log
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE, ExceptionUtils.getStackTrace(ex));
    asyncPersister.publishMetric(request, ex, handlerMethod, TenantContext.get());
    addLocalisedErrorMessage(ex);
    return handleExceptionInternal(
        ex, ex.getErrorResponse(), new HttpHeaders(), ex.getHttpStatus(), request);
  }

  private void addLocalisedErrorMessage(ReceivingException exception) {
    try {
      String errorKey = exception.getErrorResponse().getErrorKey();
      Object[] values = exception.getErrorResponse().getValues();
      if (!org.springframework.util.StringUtils.isEmpty(errorKey)) {
        String localisedErrorMsg =
            resourceBundleMessageSource.getMessage(errorKey, null, LocaleContextHolder.getLocale());
        if (!org.springframework.util.StringUtils.isEmpty(localisedErrorMsg)
            && ArrayUtils.isNotEmpty(values)) {
          localisedErrorMsg = String.format(localisedErrorMsg, values);
        }
        if (!org.springframework.util.StringUtils.isEmpty(localisedErrorMsg)) {
          exception.getErrorResponse().setLocalisedErrorMessage(localisedErrorMsg);
        }
      }
    } catch (Exception e) {
      LOGGER.error(
          ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE,
          ExceptionUtils.getStackTrace(exception));
    }
  }

  @ExceptionHandler(value = {ReceivingDataNotFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorModel notFoundException(ReceivingDataNotFoundException e) {
    LOGGER.error("Error code for the exception = {}", e.getErrorCode());
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_TYPE,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e));
    ErrorModel errorModel = createErrorModel(e);
    return errorModel;
  }

  @ExceptionHandler(value = {ReceivingInternalException.class})
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorModel internalException(ReceivingInternalException e) {
    LOGGER.error("Error code for the exception = {}", e.getErrorCode());
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_TYPE,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e));

    ErrorModel errorModel = createErrorModel(e);
    return errorModel;
  }

  @ExceptionHandler(value = {ReceivingBadDataException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorModel badDataException(ReceivingBadDataException e) {
    LOGGER.error("Error code for the exception = {}", e.getErrorCode());
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_TYPE,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e));
    ErrorModel errorModel = createErrorModel(e);
    return errorModel;
  }

  @ExceptionHandler(value = {ReceivingConflictException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorModel conflictException(ReceivingConflictException e) {
    LOGGER.error("Error code for the exception = {}", e.getErrorCode());
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_TYPE,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e));
    ErrorModel errorModel = createErrorModel(e);
    return errorModel;
  }

  @ExceptionHandler(value = {ReceivingNotImplementedException.class})
  @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
  public ErrorModel notImplemented(ReceivingNotImplementedException e) {
    LOGGER.error("Error code for the exception = {}", e.getErrorCode());
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_TYPE,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e));
    ErrorModel errorModel = createErrorModel(e);
    return errorModel;
  }

  @ExceptionHandler(value = {Exception.class})
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorModel handleError(Exception exception) {
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE,
        ExceptionUtils.getStackTrace(exception));
    return ErrorModel.builder()
        .errorCode("GLS-RCV-500")
        .errorMessage(exception.getMessage())
        .description("Unable to process")
        .build();
  }

  @ExceptionHandler(value = {GDMServiceUnavailableException.class})
  private ResponseEntity<Object> handleError(
      GDMServiceUnavailableException ex, WebRequest request) {
    return handleExceptionInternal(
        ex, ex.getErrorResponse(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  @ExceptionHandler(value = {GDMTrailerTemperatureAllPoFinalizedException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorModel gdmTrailerTemperatureAllPOFinalizedPException(
      GDMTrailerTemperatureAllPoFinalizedException e) {
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_DETAIL,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e),
        e.getErrorCode());
    return createGDMErrorModel(e);
  }

  @ExceptionHandler(value = {GDMTrailerTemperaturePartialPoFinalizedException.class})
  @ResponseStatus(HttpStatus.PARTIAL_CONTENT)
  public ErrorModel gdmTrailerTemperaturePartialPOFinalizedPException(
      GDMTrailerTemperaturePartialPoFinalizedException e) {
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_DETAIL,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e),
        e.getErrorCode());
    return createGDMErrorModel(e);
  }

  @ExceptionHandler(value = {GDMTrailerTemperatureBadRequestException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorModel gdmTrailerTemperatureBadRequestException(
      GDMTrailerTemperatureBadRequestException e) {
    LOGGER.error(
        ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE_DETAIL,
        e.getClass().getName(),
        ExceptionUtils.getStackTrace(e),
        e.getErrorCode());
    return createGDMErrorModel(e);
  }

  @ExceptionHandler(ReceivingForwardedException.class)
  public ResponseEntity<Map<String, Object>> appForwardedException(
      ReceivingForwardedException exception) {
    LOGGER.error(
        "Got an error while processing the request [exception={}]",
        ReceivingUtils.stringfyJson(exception));
    Map<String, Object> errorResponse =
        ReceivingUtils.jsonStringToMap(exception.getErrorResponseBody());
    if (Objects.nonNull(errorResponse)) {
      errorResponse.put("receivingDescription", exception.getReceivingDescription());
    }
    return new ResponseEntity<>(errorResponse, exception.getHttpStatus());
  }

  private ErrorModel createErrorModel(ApplicationBaseException e) {

    Object[] values = java.util.Objects.isNull(e.getValues()) ? null : e.getValues();

    // Just in case if this error code is not found in message.properties we are returning
    // description instead of throwing error
    String errorMessage =
        resourceBundleMessageSource.getMessage(
            e.getErrorCode(),
            values,
            StringUtils.defaultIfBlank(
                e.getDescription(), ReceivingConstants.DEFAULT_ERROR_MESSAGE),
            LocaleContextHolder.getLocale());

    return ErrorModel.builder()
        .errorCode(e.getErrorCode())
        .errorMessage(errorMessage)
        .description(e.getDescription())
        .build();
  }

  private ErrorModel createGDMErrorModel(GDMTrailerTemperatureBaseException e) {
    return ErrorModel.builder()
        .errorCode(e.getErrorCode())
        .errorMessage(e.getErrorMessage())
        .description(e.getDescription())
        .build();
  }
}
