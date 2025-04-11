package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CREATE_PROBLEM_URI;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.Problem;
import com.walmart.move.nim.receiving.core.model.ProblemResolutionRequest;
import com.walmart.move.nim.receiving.core.model.fixit.ReportProblemRequest;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.FIXIT_PLATFORM_SERVICE)
public class ProblemServiceFixit extends ProblemService {

  private static final Logger logger = LoggerFactory.getLogger(ProblemServiceFixit.class);

  @Resource(name = ReceivingConstants.BEAN_RETRYABLE_CONNECTOR)
  private RetryableRestConnector retryableRestConnector;

  private HttpHeaders getServiceMeshHeaders() {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.ENABLE_FIXIT_SERVICE_MESH_HEADERS,
        false)) {
      httpHeaders =
          ReceivingUtils.getServiceMeshHeaders(
              httpHeaders,
              appConfig.getReceivingConsumerId(),
              appConfig.getFixitServiceName(),
              appConfig.getFixitServiceEnv());
    }
    return httpHeaders;
  }

  /**
   * This method fetches problem details from FIT
   *
   * @param problemTagId
   * @return FitProblemTagResponse
   * @throws ReceivingException
   */
  @Counted(
      name = "getProblemTagHitCount",
      level1 = "uwms-receiving",
      level2 = "RdcFixitProblemService",
      level3 = "txGetProblemTagInfo")
  @Timed(
      name = "getProblemTagAPITimed",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "getProblemDetails")
  @ExceptionCounted(
      name = "getProblemTagAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "getProblemDetails")
  @Override
  public FitProblemTagResponse getProblemDetails(String problemTagId) throws ReceivingException {
    ResponseEntity<String> response;

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.PROBLEM_TAG_ID, problemTagId);
    response =
        restUtils.get(
            appConfig.getFixitPlatformBaseUrl()
                + ReceivingConstants.PROBLEM_V1_URI
                + ReceivingConstants.FIXIT_GET_PTAG_DETAILS_URI,
            getServiceMeshHeaders(),
            pathParams);

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {

        throw new ReceivingException(
            ReceivingException.FIXIT_SERVICE_DOWN,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.GET_PTAG_ERROR_CODE);

      } else {
        throw new ReceivingException(
            ReceivingException.PTAG_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            ReceivingException.GET_PTAG_ERROR_CODE);
      }
    } else {
      if (StringUtils.isEmpty(response.getBody())) {
        throw new ReceivingException(
            ReceivingException.PTAG_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            ReceivingException.GET_PTAG_ERROR_CODE);
      }
    }

    return gson.fromJson(response.getBody(), FitProblemTagResponse.class);
  }

  /**
   * Function to notify the completion of problemTag to FIXIT
   *
   * @param problemTagId
   * @param receivedQuantity
   * @return
   * @throws ReceivingException
   */
  @Counted(
      name = "updateProblemReceivedQtyHitCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "notifyCompleteProblemTag")
  @Timed(
      name = "updateProblemReceivedQtyAPITimed",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "notifyCompleteProblemTag")
  @ExceptionCounted(
      name = "updateProblemReceivedQtyAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "notifyCompleteProblemTag")
  @Override
  public String notifyCompleteProblemTag(
      String problemTagId, Problem problem, Long receivedQuantity) throws ReceivingException {
    // Prepare the path parameters
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.ISSUE_ID, problem.getIssueId());
    pathParams.put(ReceivingConstants.PROBLEM_LABEL, problemTagId);
    // Prepare the payload
    ProblemResolutionRequest problemResolutionRequest =
        preparePayloadForCompleteProblem(problem, receivedQuantity);

    ResponseEntity<String> response = null;

    response =
        restUtils.post(
            appConfig.getFixitPlatformBaseUrl()
                + ReceivingConstants.PROBLEM_V1_URI
                + ReceivingConstants.FIXIT_UPDATE_RECEIVED_CONTAINER_URI,
            getServiceMeshHeaders(),
            pathParams,
            gson.toJson(problemResolutionRequest));

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            ReceivingException.FIXIT_SERVICE_DOWN, HttpStatus.INTERNAL_SERVER_ERROR);
      } else {
        throw new ReceivingException(
            ReceivingException.COMPLETE_PTAG_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }

    return response.getBody();
  }

  /**
   * Create problem tag
   *
   * @param createProblemRequest payload to create problem
   * @return response from FIT/Problem/FIXit
   * @throws ReceivingException
   */
  @Counted(
      name = "createProblemTagHitCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "createProblemTag")
  @Timed(
      name = "createProblemTagAPITimed",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "createProblemTag")
  @ExceptionCounted(
      name = "createProblemTagAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "createProblemTag")
  public String createProblemTag(String createProblemRequest) throws ReceivingException {
    ResponseEntity<String> response;
    response =
        restUtils.post(
            appConfig.getFixitPlatformBaseUrl() + CREATE_PROBLEM_URI,
            getServiceMeshHeaders(),
            null,
            createProblemRequest);

    // TODO Find a solution to the detailed error messages from FIXIT
    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.FIXIT_SERVICE_DOWN)
                .errorKey(ExceptionCodes.FIXIT_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
      if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE)
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT)
                .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
      if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE)
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT)
                .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.BAD_REQUEST)
            .errorResponse(errorResponse)
            .build();
      }
      if (response.getStatusCode() == HttpStatus.CONFLICT) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE)
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT)
                .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.CONFLICT)
            .errorResponse(errorResponse)
            .build();
      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE)
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT)
                .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    }
    return response.getBody();
  }

  /**
   * This method reports problem to Fixit when we are trying to receive more than problem resolution
   * quantity.
   *
   * @param problemTagId
   * @param reportProblemRequest
   * @return String
   * @throws ReceivingException
   */
  @Counted(
      name = "reportProblemErrorHitCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "reportProblemError")
  @Timed(
      name = "reportProblemErrorAPITimed",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "reportProblemError")
  @ExceptionCounted(
      name = "reportProblemErrorAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "ProblemServiceFixit",
      level3 = "reportProblemError")
  public String reportProblem(
      String problemTagId, String issueId, ReportProblemRequest reportProblemRequest)
      throws ReceivingException {
    logger.info("Reporting problem receiving error to Fixit for problem label: {}", problemTagId);
    ResponseEntity<String> response;
    Map<String, String> pathParams = new HashMap<>();

    pathParams.put(ReceivingConstants.ISSUE_ID, issueId);
    pathParams.put(ReceivingConstants.PROBLEM_LABEL, problemTagId);

    String uri =
        ReceivingUtils.replacePathParams(
                appConfig.getFixitPlatformBaseUrl()
                    + ReceivingConstants.PROBLEM_V1_URI
                    + ReceivingConstants.FIXIT_UPDATE_PROBLEM_RECEIVE_ERROR_URI,
                pathParams)
            .toString();

    response =
        restUtils.post(uri, getServiceMeshHeaders(), pathParams, gson.toJson(reportProblemRequest));

    logger.info(
        "Received problem reporting response:{} from Fixit for problem label: {}",
        response,
        problemTagId);

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            ReceivingException.FIXIT_SERVICE_DOWN, HttpStatus.INTERNAL_SERVER_ERROR);
      } else if (response.getStatusCode() == HttpStatus.NOT_FOUND
          || response.getStatusCode() == HttpStatus.BAD_REQUEST
          || response.getStatusCode() == HttpStatus.CONFLICT) {
        throw new ReceivingException(
            ReceivingException.REPORT_PROBLEM_ERROR_MESSAGE,
            response.getStatusCode(),
            ReceivingException.REPORT_PROBLEM_ERROR_CODE_FIXIT);
      } else {
        throw new ReceivingException(
            ReceivingException.REPORT_PROBLEM_ERROR_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
    logger.info("Successfully reported problem to Fixit for problem label: {}", problemTagId);
    return response.getBody();
  }
}
