package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.config.FdeConfig;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.FdeSpec;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * FDE service that will interact with fulfillment provider based on channel method Mocked for now
 *
 * @author g0k0072
 */
public abstract class FdeService {
  private static final Logger log = LoggerFactory.getLogger(FdeService.class);
  @ManagedConfiguration private FdeConfig fdeConfig;

  protected InstructionError instructionError;

  protected Gson gson;

  public FdeService() {
    gson = new Gson();
  }

  public abstract String receive(
      FdeCreateContainerRequest fdeCreateContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException;
  /**
   * This method is used to get description for the error given by OF during instruction request
   *
   * @param response
   * @return
   * @throws ReceivingException
   * @throws JSONException
   */
  protected InstructionError getErrorMessageFromResponse(String response) {
    JsonObject errorResponse = gson.fromJson(response, JsonObject.class);
    JsonArray errorMessage = (JsonArray) errorResponse.get(ReceivingConstants.OF_ERROR_MESSAGES);
    if (Objects.nonNull(errorMessage) && !errorMessage.isJsonNull()) {
      JsonObject errorObject = errorMessage.get(0).getAsJsonObject();
      instructionError =
          InstructionErrorCode.getErrorValue(
              errorObject.get(ReceivingConstants.OF_ERROR_CODE).getAsString());
      if (Objects.isNull(instructionError)) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
      }
    } else {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
    }
    return instructionError;
  }

  // Creating separate method to forward the same error message returned by Allocation Planner
  protected String getAllocationDetailedErrorMessage(String response) {
    JsonObject errorResponse = gson.fromJson(response, JsonObject.class);
    JsonArray errorMessage = (JsonArray) errorResponse.get(ReceivingConstants.OF_ERROR_MESSAGES);
    if (!errorMessage.isJsonNull() && !errorMessage.get(0).getAsJsonObject().isJsonNull()) {
      JsonObject errorObject = errorMessage.get(0).getAsJsonObject();
      if (Objects.nonNull(errorObject.get(ReceivingConstants.OF_ERROR_DETAILED_DESC))) {
        return errorObject.get(ReceivingConstants.OF_ERROR_DETAILED_DESC).getAsString();
      }
    }
    return null;
  }

  protected String getEndPointUrl(String purchaseRefType) {
    String endPointUrl = null;
    String defaultEndPointUrl = null;

    Type fdeSpecListType =
        new TypeToken<ArrayList<FdeSpec>>() {
          private static final long serialVersionUID = 1L;
        }.getType();

    List<FdeSpec> fdeSpecList = gson.fromJson(fdeConfig.getSpec(), fdeSpecListType);

    // get all elements for given purchaseRefType
    fdeSpecList =
        fdeSpecList
            .stream()
            .filter(
                e ->
                    !CollectionUtils.isEmpty(e.getChannelMethod())
                        && e.getChannelMethod().contains(purchaseRefType))
            .collect(Collectors.toList());

    // populate default url and endpoint url if facilityNum is present
    for (FdeSpec spec : fdeSpecList) {
      if (CollectionUtils.isEmpty(spec.getFacilityNum())) {
        defaultEndPointUrl = spec.getEndPoint();
      } else if (spec.getFacilityNum().contains(TenantContext.getFacilityNum())) {
        endPointUrl = spec.getEndPoint();
        break;
      }
    }
    // if facility specific endpoint is not present then return default
    if (StringUtils.isEmpty(endPointUrl)) endPointUrl = defaultEndPointUrl;

    return endPointUrl;
  }

  protected String findFulfillmentType(FdeCreateContainerRequest fdeCreateContainerRequest)
      throws ReceivingException {
    String endPointUrl = null;
    String purchaseRefType = null;
    String errorMessage = null;

    if (!CollectionUtils.isEmpty(fdeCreateContainerRequest.getContainer().getContents())) {
      purchaseRefType =
          fdeCreateContainerRequest.getContainer().getContents().get(0).getPurchaseRefType();
    } else {
      if (!CollectionUtils.isEmpty(fdeCreateContainerRequest.getContainer().getChildContainers())
          && !CollectionUtils.isEmpty(
              fdeCreateContainerRequest.getContainer().getChildContainers().get(0).getContents())) {
        purchaseRefType =
            fdeCreateContainerRequest
                .getContainer()
                .getChildContainers()
                .get(0)
                .getContents()
                .get(0)
                .getPurchaseRefType();
      }
    }

    if (purchaseRefType == null) {
      instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.NO_PURCHASE_REF_TYPE_ERROR);
      log.error(instructionError.getErrorMessage());
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(instructionError.getErrorMessage())
              .errorCode(instructionError.getErrorCode())
              .errorHeader(instructionError.getErrorHeader())
              .errorKey(ExceptionCodes.NO_PURCHASE_REF_TYPE_ERROR)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }

    endPointUrl = getEndPointUrl(purchaseRefType);

    if (endPointUrl == null) {
      instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.NO_MATCHING_CAPABALITY_ERROR);
      errorMessage = String.format(instructionError.getErrorMessage(), purchaseRefType);
      log.error(errorMessage);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(errorMessage)
              .errorCode(instructionError.getErrorCode())
              .errorHeader(instructionError.getErrorHeader())
              .errorKey(ExceptionCodes.NO_MATCHING_CAPABALITY_ERROR)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }

    return endPointUrl;
  }

  protected String getInstructionErrorKey(InstructionError instructionError) {
    String instructionName = instructionError.name();
    String instructionErrorString = String.join("-", instructionName.split("_"));
    String errorKey = "GLS-RCV-" + instructionErrorString + "-500";
    return errorKey;
  }
}
