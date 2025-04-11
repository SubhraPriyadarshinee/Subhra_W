package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ProblemUtils.getMinimumResolutionQty;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.FIT_SERVICE_DOWN;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.FIXIT_SERVICE_DOWN;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.PTAG_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.FIT_BAD_DATA_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.GENERIC_ERROR_RE_TRY_OR_REPORT;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.fixit.ReportProblemRequest;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(ReceivingConstants.FIT_SERVICE)
public class ProblemService implements Purge {
  private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Autowired protected ReceiptService receiptService;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  protected DeliveryService deliveryService;

  @Autowired protected Gson gson;

  @Autowired private ProblemReceivingHelper problemReceivingHelper;

  @ManagedConfiguration protected AppConfig appConfig;

  @Autowired protected RestUtils restUtils;

  @Autowired protected ProblemRepository problemRepository;

  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;

  @Autowired protected TenantSpecificConfigReader configUtils;

  @Autowired private InstructionPersisterService instructionPersisterService;

  @Resource(name = ReceivingConstants.BEAN_RETRYABLE_CONNECTOR)
  protected RetryableRestConnector retryableRestConnector;

  private final JsonParser parser = new JsonParser();

  public ProblemService() {
    problemReceivingHelper = new ProblemReceivingHelper();
  }

  /**
   * This method makes an entry into problem table
   *
   * @param deliveryNumber
   * @param problemTagId
   * @param issueId
   * @param fitProblemTagResponse
   * @return ProblemLabel
   */
  @Transactional
  @InjectTenantFilter
  public ProblemLabel saveProblemLabel(
      Long deliveryNumber,
      String problemTagId,
      String issueId,
      String resolutionId,
      FitProblemTagResponse fitProblemTagResponse) {
    ProblemLabel problemLabel = problemRepository.findProblemLabelByProblemTagId(problemTagId);
    if (problemLabel == null) {
      problemLabel = new ProblemLabel();
      problemLabel.setProblemTagId(problemTagId);
    }
    problemLabel.setDeliveryNumber(deliveryNumber);
    problemLabel.setIssueId(issueId);
    problemLabel.setResolutionId(resolutionId);
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.ENABLE_PROBLEM_RESPONSE_PERSIST_FEATURE,
        false)) {
      problemLabel.setProblemResponse(gson.toJson(fitProblemTagResponse));
    }

    return problemRepository.save(problemLabel);
  }

  /**
   * This method fetches problem details from FIT
   *
   * @param problemTagId
   * @return FitProblemTagResponse
   * @throws ReceivingException
   */
  public FitProblemTagResponse getProblemDetails(String problemTagId) throws ReceivingException {
    ResponseEntity<String> response;

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(PROBLEM_TAG_ID, problemTagId);
    response =
        restUtils.get(
            appConfig.getFitBaseUrl() + PROBLEM_V1_URI + FIT_GET_PTAG_DETAILS_URI,
            ReceivingUtils.getHeaders(),
            pathParams);

    if (response.getStatusCode().series() != SUCCESSFUL) {
      if (response.getStatusCode() == SERVICE_UNAVAILABLE) {

        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(FIT_SERVICE_DOWN)
                .errorCode(GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.FIT_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();

      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(PTAG_NOT_FOUND)
                .errorCode(GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.PTAG_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    } else {
      if (StringUtils.isEmpty(response.getBody())) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(PTAG_NOT_FOUND)
                .errorCode(GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.PTAG_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    }

    return gson.fromJson(response.getBody(), FitProblemTagResponse.class);
  }

  /**
   * This method will do the necessary actions to get the problemTag information
   *
   * @param problemTag
   * @param headers
   * @return ProblemTagResponse
   * @throws ReceivingException
   */
  @Transactional
  public ProblemTagResponse txGetProblemTagInfo(String problemTag, HttpHeaders headers)
      throws ReceivingException {
    ProblemTagResponse problemTagResponse = null;
    long totalReceived = 0;
    int maxLimit = 0;

    FitProblemTagResponse fitProblemTagResponse = getProblemDetails(problemTag);
    Issue issue = fitProblemTagResponse.getIssue();
    // Get the tenant specific feature flag
    boolean isGroceryProblemReceive =
        configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.GROCERY_PROBLEM_RECEIVE_FEATURE,
            false);
    if (problemReceivingHelper.isContainerReceivable(fitProblemTagResponse)) {
      Resolution resolution = fitProblemTagResponse.getResolutions().get(0);
      GdmPOLineResponse gdmPOLineResponse =
          deliveryService.getPOLineInfoFromGDM(
              issue.getDeliveryNumber(),
              resolution.getResolutionPoNbr(),
              resolution.getResolutionPoLineNbr(),
              headers);

      DeliveryDocumentLine gdmDeliveryDocumentLine =
          Objects.nonNull(gdmPOLineResponse)
                  && !CollectionUtils.isEmpty(gdmPOLineResponse.getDeliveryDocuments())
                  && !CollectionUtils.isEmpty(
                      gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines())
              ? gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0)
              : null;

      if (Objects.isNull(gdmDeliveryDocumentLine)) {
        log.error(ReceivingException.PROBLEM_RESOLUTION_DOC_DOCLINE_NOT_FOUND_IN_DELIVERY_ERROR);
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_PROBLEM_RESOLUTION,
            ReceivingException.PROBLEM_RESOLUTION_DOC_DOCLINE_NOT_FOUND_IN_DELIVERY_ERROR);
      }

      if (InstructionUtils.isRejectedPOLine(gdmDeliveryDocumentLine)) {
        log.error(ReceivingException.PTAG_RESOLVED_BUT_LINE_REJECTED);
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.PTAG_RESOLVED_BUT_LINE_REJECTED)
                .errorCode(GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.PTAG_RESOLVED_BUT_LINE_REJECTED)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.CONFLICT)
            .errorResponse(errorResponse)
            .build();
      }
      // throw exception for resolutions raised on bpo in fixit based on feature flag
      if (Boolean.TRUE.equals(gdmPOLineResponse.getDeliveryDocuments().get(0).getImportInd())
          && configUtils.isFeatureFlagEnabled(ReceivingConstants.RESOLUTION_ON_BPO_CHECK_ENABLED)) {
        String bpoNumber = gdmPOLineResponse.getDeliveryDocuments().get(0).getBpoNumber();
        if (resolution.getResolutionPoNbr().equals(bpoNumber)) {
          log.info(ReceivingException.RESOLUTION_BPO_NOT_ALLOWED);
          throw new ReceivingException(
              ReceivingException.RESOLUTION_BPO_NOT_ALLOWED,
              HttpStatus.CONFLICT,
              RESOLUTION_ON_BPO,
              null,
              null,
              ExceptionCodes.RESOLUTION_BPO_NOT_ALLOWED,
              null);
        }
      }
      totalReceived =
          ReceivingUtils.isImportPoLineFbqEnabled(
                  gdmPOLineResponse.getDeliveryDocuments().get(0).getImportInd(), configUtils)
              ? receivedQtyByDeliveryPoAndPoLine(gdmPOLineResponse.getDeliveryNumber(), resolution)
              : receivedQtyByPoAndPoLine(resolution, gdmDeliveryDocumentLine);
      maxLimit =
          ReceivingUtils.computeEffectiveMaxReceiveQty(
              gdmDeliveryDocumentLine,
              gdmPOLineResponse.getDeliveryDocuments().get(0).getImportInd(),
              configUtils);
      log.info(
          "ProblemTagId:{} ResolutionPoNbr:{} ResolutionPoLineNbr:{} MaxLimit:{} TotalReceived:{}",
          problemTag,
          resolution.getResolutionPoNbr(),
          resolution.getResolutionPoLineNbr(),
          maxLimit,
          totalReceived);

      // Validate the line max limit
      if (totalReceived >= maxLimit) {
        if (isGroceryProblemReceive) {
          log.info("Allowed quantity for this item has been received but ignoring it");
        } else {
          log.error(ReceivingException.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED);
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(ReceivingException.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED)
                  .errorCode(GET_PTAG_ERROR_CODE)
                  .errorKey(ExceptionCodes.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.CONFLICT)
              .errorResponse(errorResponse)
              .build();
        }
      }

      // Calculate open quantity
      if (isGroceryProblemReceive) {
        int openQty = gdmDeliveryDocumentLine.getTotalOrderQty() - (int) totalReceived;
        if (openQty < 0) {
          openQty = 0;
        }
        gdmDeliveryDocumentLine.setOpenQty(openQty);
        gdmDeliveryDocumentLine.setQuantity(gdmDeliveryDocumentLine.getTotalOrderQty());
        log.info(
            "problemTag:{} quantity:{} openQty:{}",
            problemTag,
            gdmDeliveryDocumentLine.getQuantity(),
            gdmDeliveryDocumentLine.getOpenQty());
      }

      saveProblemLabel(
          Long.parseLong(issue.getDeliveryNumber()),
          problemTag,
          issue.getId(),
          resolution.getId(),
          fitProblemTagResponse);

      Long receivedQtyByProblemIdInVnpk = null;
      if (isKotlinEnabled(headers, configUtils)) {
        receivedQtyByProblemIdInVnpk = receiptService.getReceivedQtyByProblemIdInVnpk(problemTag);
      }
      problemTagResponse =
          getConsolidatedResponse(
              fitProblemTagResponse, gdmPOLineResponse, receivedQtyByProblemIdInVnpk);
    } else {
      log.error("Problem:[{}] is not ready to receive.", problemTag);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.PTAG_NOT_READY_TO_RECEIVE)
              .errorCode(GET_PTAG_ERROR_CODE)
              .errorKey(ExceptionCodes.PTAG_NOT_READY_TO_RECEIVE)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.CONFLICT)
          .errorResponse(errorResponse)
          .build();
    }

    return problemTagResponse;
  }

  protected long receivedQtyByPoAndPoLine(
      Resolution resolution, DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
    return receiptService.getReceivedQtyByPoAndPoLine(
        resolution.getResolutionPoNbr(), resolution.getResolutionPoLineNbr());
  }

  protected long receivedQtyByDeliveryPoAndPoLine(Long deliveryNumber, Resolution resolution)
      throws ReceivingException {
    return receiptService.receivedQtyByDeliveryPoAndPoLine(
        deliveryNumber, resolution.getResolutionPoNbr(), resolution.getResolutionPoLineNbr());
  }

  /**
   * This method prepares the response
   *
   * @param fitProblemTagResponse
   * @param gdmPOLineResponse
   * @param receivedQuantity
   * @return ProblemTagResponse
   * @throws ReceivingException
   */
  public ProblemTagResponse getConsolidatedResponse(
      FitProblemTagResponse fitProblemTagResponse,
      GdmPOLineResponse gdmPOLineResponse,
      Long receivedQuantity)
      throws ReceivingException {
    ProblemTagResponse response = new ProblemTagResponse();

    // Set problem data
    Problem problem = new Problem();
    problem.setProblemTagId(fitProblemTagResponse.getLabel());
    problem.setIssueId(fitProblemTagResponse.getIssue().getId());
    problem.setResolutionId(fitProblemTagResponse.getResolutions().get(0).getId());
    problem.setResolutionQty(getMinimumResolutionQty(fitProblemTagResponse));
    problem.setDeliveryNumber(fitProblemTagResponse.getIssue().getDeliveryNumber());
    problem.setUom(fitProblemTagResponse.getIssue().getUom());
    final String slot = fitProblemTagResponse.getSlot();
    problem.setSlotId(isBlank(slot) ? DEFAULT_DOOR : slot);
    problem.setType(fitProblemTagResponse.getIssue().getType());
    problem.setReportedQty(fitProblemTagResponse.getReportedQty());
    // Fixit contract not giving accurate received qty receipt
    if (Objects.nonNull(receivedQuantity)) {
      problem.setReceivedQty(receivedQuantity.intValue());
    } else {
      problem.setReceivedQty(fitProblemTagResponse.getResolutions().get(0).getAcceptedQuantity());
    }
    DeliveryDocument deliveryDocument = gdmPOLineResponse.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);

    // Set inboundDocument, this will be deprecated once client started using
    // deliveryDocumentLine
    InboundDocument inboundDocument =
        createInboundDocumentForProblemTagResponse(
            gdmPOLineResponse, deliveryDocument, deliveryDocumentLine);

    // Set item data
    Item item = createItemForProblemTagResponse(gdmPOLineResponse, deliveryDocumentLine);
    if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())) {
      ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
      if (Objects.nonNull(additionalInfo.getWarehouseRotationTypeCode())) {
        deliveryDocumentLine.setFirstExpiryFirstOut(
            deliveryDocumentHelper.isFirstExpiryFirstOut(
                additionalInfo.getWarehouseRotationTypeCode()));
      }
    }

    // Set Resolution
    setProblemResolutionReceiveType(
        problem, fitProblemTagResponse.getIssue(), deliveryDocumentLine);

    // Set GTIN
    deliveryDocumentLine.setGtin(getGtinFromLineItem(deliveryDocumentLine));

    response.setProblem(problem);
    response.setInboundDocument(inboundDocument);
    response.setItem(item);
    response.setDeliveryDocumentLine(deliveryDocumentLine);

    return response;
  }

  protected boolean isDscsaExemptionIndEnabled(DeliveryDocumentLine deliveryDocumentLine) {
    if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getIsDscsaExemptionInd())) {
      return Boolean.TRUE.equals(deliveryDocumentLine.getAdditionalInfo().getIsDscsaExemptionInd());
    } else {
      return true; // default fallback is UPC
    }
  }

  protected void setProblemResolutionReceiveType(
      Problem problem, Issue issue, DeliveryDocumentLine deliveryDocumentLine) {
    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.ENABLE_PROBLEM_RESOLUTION_TYPE)) {
      switch (issue.getType()) {
        case ReceivingConstants.PROBLEM_RESOLUTION_NL:
        case ReceivingConstants.OVG:
          if (isDscsaExemptionIndEnabled(deliveryDocumentLine)) {
            problem.setResolution(ReceivingConstants.PROBLEM_RECEIVE_UPC);
          } else if (isGrandFatheredProduct(issue.getSubType())) {
            problem.setResolution(ReceivingConstants.PROBLEM_RESOLUTION_SUBTYPE_GRANDFATHERED);
          } else {
            problem.setResolution(ReceivingConstants.PROBLEM_RECEIVE_SERIALIZED);
          }
          break;

        default:
          if (isDscsaExemptionIndEnabled(deliveryDocumentLine)) {
            problem.setResolution(ReceivingConstants.PROBLEM_RECEIVE_UPC);
          } else {
            problem.setResolution(ReceivingConstants.PROBLEM_RECEIVE_SERIALIZED);
          }
          break;
      }
    }
  }

  protected boolean isGrandFatheredProduct(String issueSubType) {
    return (StringUtils.isNotEmpty(issueSubType)
        && ReceivingConstants.PROBLEM_RESOLUTION_SUBTYPE_GRANDFATHERED.equals(issueSubType));
  }

  /**
   * Function to complete the problemTag
   *
   * @param problemTagId
   * @param problem
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public String completeProblemTag(String problemTagId, Problem problem, HttpHeaders headers)
      throws ReceivingException {
    // Get problem details from DB
    ProblemLabel problemLabel = problemRepository.findProblemLabelByProblemTagId(problemTagId);
    if (problemLabel == null) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(PTAG_NOT_FOUND)
              .errorKey(ExceptionCodes.PTAG_NOT_FOUND)
              .build();
      throw ReceivingException.builder()
          .httpStatus(INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }

    // Get received quantity for a given problemTagId
    Long receivedQuantity = receiptService.getReceivedQtyByProblemId(problemTagId);

    // Notify FIT that the problemTagId completed
    notifyCompleteProblemTag(problemTagId, problem, receivedQuantity);

    // Get received quantity for a given delivery
    List<ReceiptSummaryResponse> receiptSummaryResponses =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            problemLabel.getDeliveryNumber(), ReceivingConstants.Uom.EACHES);

    // Get delivery details
    String deliveryResponse =
        deliveryService.getDeliveryByDeliveryNumber(
            Long.parseLong(problem.getDeliveryNumber()), headers);
    String deliveryStatus =
        parser
            .parse(deliveryResponse)
            .getAsJsonObject()
            .get("deliveryStatus")
            .toString()
            .replace("\"", "");
    // Publish updated receipts to GDM if delivery is not in working status
    if (!deliveryStatus.equalsIgnoreCase(DeliveryStatus.WRK.toString())) {
      deliveryStatusPublisher.publishDeliveryStatus(
          problemLabel.getDeliveryNumber(),
          DeliveryStatus.COMPLETE.name(),
          receiptSummaryResponses,
          ReceivingUtils.getForwardablHeader(headers));
    }

    // Delete the problemTag
    problemRepository.delete(problemLabel);

    return problemTagId + " problemTag completed successfully";
  }

  /**
   * Function to notify the completion of problemTag to FIT
   *
   * @param problemTagId
   * @param receivedQuantity
   * @return
   * @throws ReceivingException
   */
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
            appConfig.getFitBaseUrl()
                + PROBLEM_V1_URI
                + ReceivingConstants.FIT_UPDATE_RECEIVED_CONTAINER_URI,
            ReceivingUtils.getHeaders(),
            pathParams,
            gson.toJson(problemResolutionRequest));

    if (response.getStatusCode().series() != SUCCESSFUL) {
      if (response.getStatusCode() == SERVICE_UNAVAILABLE) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(FIT_SERVICE_DOWN)
                .errorKey(ExceptionCodes.FIT_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.COMPLETE_PTAG_ERROR)
                .errorKey(ExceptionCodes.COMPLETE_PTAG_ERROR)
                .build();
        throw ReceivingException.builder()
            .httpStatus(INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    }

    return response.getBody();
  }

  protected ProblemResolutionRequest preparePayloadForCompleteProblem(
      Problem problem, Long receivedQuantity) {
    Resolution resolution = new Resolution();
    resolution.setId(problem.getResolutionId());
    resolution.setReceivedQuantity(receivedQuantity);
    List<Resolution> resolutions = new ArrayList<>();
    resolutions.add(resolution);
    ProblemResolutionRequest problemResolutionRequest = new ProblemResolutionRequest();
    problemResolutionRequest.setReceivedQuantity(receivedQuantity);
    problemResolutionRequest.setReceivingUser(ReceivingUtils.retrieveUserId());
    problemResolutionRequest.setResolutions(resolutions);
    return problemResolutionRequest;
  }

  protected Item createItemForProblemTagResponse(
      GdmPOLineResponse gdmPOLineResponse, DeliveryDocumentLine deliveryDocumentLine) {
    Item item = new Item();
    item.setNumber(deliveryDocumentLine.getItemNbr());
    item.setDescription(deliveryDocumentLine.getDescription());
    item.setPalletTi(deliveryDocumentLine.getPalletTie());
    item.setPalletHi(deliveryDocumentLine.getPalletHigh());
    item.setGtin(getGtinFromLineItem(deliveryDocumentLine));
    item.setDeptNumber(gdmPOLineResponse.getDeliveryDocuments().get(0).getDeptNumber());
    item.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
    item.setIsConveyable(deliveryDocumentLine.getIsConveyable());
    item.setItemType(deliveryDocumentLine.getItemType());
    item.setIsHazmat(deliveryDocumentLine.getIsHazmat());
    item.setSize(deliveryDocumentLine.getSize());
    item.setColor(deliveryDocumentLine.getColor());
    item.setCube(deliveryDocumentLine.getCube());
    item.setCubeUom(deliveryDocumentLine.getCubeUom());
    item.setWeight(deliveryDocumentLine.getWeight());
    item.setWeightUom(deliveryDocumentLine.getWeightUom());
    return item;
  }

  protected InboundDocument createInboundDocumentForProblemTagResponse(
      GdmPOLineResponse gdmPOLineResponse,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine) {
    InboundDocument inboundDocument = new InboundDocument();
    inboundDocument.setPurchaseReferenceNumber(
        gdmPOLineResponse.getDeliveryDocuments().get(0).getPurchaseReferenceNumber());
    inboundDocument.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    inboundDocument.setPurchaseCompanyId(deliveryDocument.getPurchaseCompanyId());
    inboundDocument.setPurchaseReferenceLegacyType(
        deliveryDocument.getPurchaseReferenceLegacyType());
    inboundDocument.setInboundChannelType(deliveryDocumentLine.getPurchaseRefType());
    inboundDocument.setGtin(getGtinFromLineItem(deliveryDocumentLine));
    inboundDocument.setDepartment(deliveryDocument.getDeptNumber());
    inboundDocument.setEvent(deliveryDocumentLine.getEvent());
    inboundDocument.setVendorPack(deliveryDocumentLine.getVendorPack());
    inboundDocument.setWarehousePack(deliveryDocumentLine.getWarehousePack());
    inboundDocument.setExpectedQty(deliveryDocumentLine.getTotalOrderQty());
    inboundDocument.setExpectedQtyUOM(deliveryDocumentLine.getQtyUOM());
    inboundDocument.setPoDcNumber(deliveryDocument.getPoDCNumber());
    inboundDocument.setVendorNumber(deliveryDocument.getVendorNumber());
    inboundDocument.setFinancialReportingGroup(deliveryDocument.getFinancialReportingGroup());
    inboundDocument.setBaseDivisionCode(deliveryDocument.getBaseDivisionCode());
    inboundDocument.setVendorPackCost((double) deliveryDocumentLine.getVendorPackCost());
    inboundDocument.setWhpkSell((double) deliveryDocumentLine.getWarehousePackSell());
    inboundDocument.setCurrency(deliveryDocumentLine.getCurrency());
    inboundDocument.setOverageQtyLimit(0);
    inboundDocument.setDeliveryStatus(gdmPOLineResponse.getDeliveryStatus());
    inboundDocument.setWeight(deliveryDocumentLine.getWeight());
    inboundDocument.setWeightUom(deliveryDocumentLine.getWeightUom());
    inboundDocument.setCube(deliveryDocumentLine.getCube());
    inboundDocument.setCubeUom(deliveryDocumentLine.getCubeUom());
    inboundDocument.setDescription(deliveryDocumentLine.getDescription());
    inboundDocument.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
    // TODO Remove after PROBLEM APP IS DEPRECIATED
    inboundDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    return inboundDocument;
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<ProblemLabel> problemList =
        problemRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    problemList =
        problemList
            .stream()
            .filter(item -> item.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(ProblemLabel::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(problemList)) {
      log.info("Purge PROBLEM: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = problemList.get(problemList.size() - 1).getId();

    log.info(
        "Purge PROBLEM: {} records : ID {} to {} : START",
        problemList.size(),
        problemList.get(0).getId(),
        lastDeletedId);
    problemRepository.deleteAll(problemList);
    log.info("Purge PROBLEM: END");
    return lastDeletedId;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public ProblemLabel findProblemLabelByProblemTagId(String problemId) {
    return problemRepository.findProblemLabelByProblemTagId(problemId);
  }

  @InjectTenantFilter
  @Transactional
  public void deleteProblemLabel(ProblemLabel problemLabel) {
    problemRepository.delete(problemLabel);
  }

  /**
   * Get GTIN from delivery document line
   *
   * @param deliveryDocumentLine
   * @return GTIN
   */
  protected String getGtinFromLineItem(DeliveryDocumentLine deliveryDocumentLine) {
    return Objects.nonNull(deliveryDocumentLine.getItemUpc())
        ? deliveryDocumentLine.getItemUpc()
        : deliveryDocumentLine.getCaseUpc();
  }

  /**
   * Returns the count of problem tickets for a given PO from FIXIT/FIX API
   *
   * @param poNumber
   * @param forwardableHttpHeaders
   * @return
   * @throws ReceivingException
   */
  public ProblemTicketResponseCount getProblemTicketsForPo(
      String poNumber, HttpHeaders forwardableHttpHeaders) throws ReceivingException {
    return getProblemTicketsForPoFromFixIt(poNumber, forwardableHttpHeaders);
  }

  /**
   * CallsFixIt Graph QL API to get the response and retrieve the element
   * problemTags.searchException.pageInfo.filterCount for the given PO and return the count
   *
   * @param poNumber
   * @param headers
   * @return
   * @throws ReceivingException
   */
  protected ProblemTicketResponseCount getProblemTicketsForPoFromFixIt(
      String poNumber, HttpHeaders headers) throws ReceivingException {
    ProblemTicketResponseCount count = new ProblemTicketResponseCount();
    // Write the logic to call the API
    addGraphQLHeaders(headers);
    final String url = appConfig.getFixitPlatformBaseUrl() + FIT_GRAPH_QL_URI;
    String cid = headers.getFirst(CORRELATION_ID_HEADER_KEY);
    final String fixItProblemTicketsGraphQL =
        configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            ReceivingConstants.FIXIT_PROBLEM_TICKETS_GRAPH_QL,
            ReceivingConstants.FIXIT_PROBLEM_TICKETS_GRAPH_QL_DEFAULT);
    String graphQL = String.format(fixItProblemTicketsGraphQL, poNumber);
    log.info(
        "FIXIT Problem Tickets API call - cid={}, url={}, headers={}, graphQL={}",
        cid,
        url,
        headers,
        graphQL);

    final ResponseEntity<String> apiResponse = restUtils.post(url, headers, null, graphQL);
    log.info("FIXIT Problem Tickets API call - cid={}, response={}", cid, apiResponse);
    final String problemTicketsByPONumber = apiResponse.getBody();
    validateResponse(cid, apiResponse, problemTicketsByPONumber, true);

    JsonObject jsonObject = gson.fromJson(problemTicketsByPONumber, JsonObject.class);
    int filterCount =
        (Objects.nonNull(jsonObject)
                && Objects.nonNull(jsonObject.getAsJsonObject("data"))
                && Objects.nonNull(
                    jsonObject.getAsJsonObject("data").getAsJsonObject("searchException"))
                && Objects.nonNull(
                    jsonObject
                        .getAsJsonObject("data")
                        .getAsJsonObject("searchException")
                        .getAsJsonObject("pageInfo")))
            ? jsonObject
                .getAsJsonObject("data")
                .getAsJsonObject("searchException")
                .getAsJsonObject("pageInfo")
                .get("filterCount")
                .getAsInt()
            : 0;

    count.setTicketCount(filterCount);
    return count;
  }

  /**
   * Gets all problems of type 'Not on PO' or 'Not Walmart freight' for given Delivery Number
   *
   * @param deliveryNumber
   * @param fordableHttpHeaders
   * @return
   * @throws ReceivingException
   */
  public String getProblemsForDelivery(int deliveryNumber, HttpHeaders fordableHttpHeaders)
      throws ReceivingException {
    return configUtils.getConfiguredFeatureFlag(getFacilityNum().toString(), FIXIT_ENABLED, false)
        ? getProblemsForDeliveryFromFixIt(deliveryNumber, fordableHttpHeaders)
        : getProblemsForDeliveryFromFit(deliveryNumber, fordableHttpHeaders);
  }

  /**
   * Calls FIT Graph QL to get 'Not on PO' or 'Not Walmart freight' type Issues for given Delivery
   * Number and returns the json response as-is
   *
   * @param deliveryNumber
   * @param headers
   * @return json response for 'Not on PO' or 'Not Walmart freight' type Issues
   * @throws ReceivingException
   */
  protected String getProblemsForDeliveryFromFit(int deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    // headers for Graph QL
    headers.remove(CONTENT_TYPE);
    headers.add(CONTENT_TYPE, APPLICATION_GRAPHQL);

    // Graph QL query from ccm
    final String url = appConfig.getFitBaseUrl() + FIT_GRAPH_QL_URI;
    String cid = headers.getFirst(CORRELATION_ID_HEADER_KEY);
    final String fitProblemsGraphQL =
        configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            ReceivingConstants.FIT_PROBLEMS_GRAPH_QL,
            ReceivingConstants.FIT_PROBLEMS_GRAPH_QL_DEFAULT);
    String graphQL = String.format(fitProblemsGraphQL, deliveryNumber);
    log.info("FIT cid={}, url={}, graphQL={}", cid, url, graphQL);

    final ResponseEntity<String> response = restUtils.post(url, headers, null, graphQL);
    final String problemsByDelivery = response.getBody();
    validateResponse(cid, response, problemsByDelivery, false);

    return problemsByDelivery;
  }

  /**
   * Calls FIXIT Graph QL to get 'Not on PO' or 'Not Walmart freight' type Issues for given Delivery
   * Number and returns the json response as-is
   *
   * @param deliveryNumber
   * @param headers
   * @return json response for 'Not on PO' or 'Not Walmart freight' type Issues
   * @throws ReceivingException
   */
  protected String getProblemsForDeliveryFromFixIt(int deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    addGraphQLHeaders(headers);
    // Graph QL query from ccm
    final String url = appConfig.getFixitPlatformBaseUrl() + FIT_GRAPH_QL_URI;
    String cid = headers.getFirst(CORRELATION_ID_HEADER_KEY);
    final String fixItProblemsGraphQL =
        configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            FIXIT_PROBLEMS_GRAPH_QL,
            FIXIT_PROBLEMS_GRAPH_QL_DEFAULT);
    String graphQL = String.format(fixItProblemsGraphQL, deliveryNumber);
    log.info("FIXIT cid={}, url={}, headers={}, graphQL={}", cid, url, headers, graphQL);
    final ResponseEntity<String> response = restUtils.post(url, headers, null, graphQL);
    log.info("FIXIT cid={}, response", cid, response);

    final String problemsByDelivery = response.getBody();
    validateResponse(cid, response, problemsByDelivery, true);

    return problemsByDelivery;
  }

  protected void addGraphQLHeaders(HttpHeaders headers) {
    // headers for Graph QL
    headers.add(WMT_SOURCE, APP_NAME_VALUE);
    headers.add(WMT_CHANNEL, WEB);
    if (isBlank(headers.getFirst(CONTENT_TYPE))) {
      headers.add(CONTENT_TYPE, APPLICATION_GRAPHQL);
    } else {
      headers.remove(CONTENT_TYPE);
      headers.add(CONTENT_TYPE, APPLICATION_GRAPHQL);
    }
    ReceivingUtils.getServiceMeshHeaders(
        headers,
        appConfig.getReceivingConsumerId(),
        appConfig.getFixitServiceName(),
        appConfig.getFixitServiceEnv());
  }

  protected void validateResponse(
      String cid, ResponseEntity<String> response, String problemsByDelivery, boolean isFixit)
      throws ReceivingException {
    if (SUCCESSFUL == response.getStatusCode().series()) {
      if (StringUtils.contains(problemsByDelivery, FIT_GRAPH_QL_ERROR_RESPONSE)) {
        log.error("FIT/FixIt error cid={}, response={}", cid, problemsByDelivery);
        if (isFixit) {
          throw new ReceivingDataNotFoundException(FIXIT_NOT_FOUND, FIT_BAD_DATA_ERROR_MSG);
        } else {
          throw new ReceivingDataNotFoundException(FIT_NOT_FOUND, FIT_BAD_DATA_ERROR_MSG);
        }
      }
    } else {
      if (response.getStatusCode() == SERVICE_UNAVAILABLE) {
        log.error("FIT/FixIt error cid={}, response statusCode={}", cid, response.getStatusCode());
        if (isFixit) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(FIXIT_SERVICE_DOWN)
                  .errorCode(GET_PTAG_ERROR_CODE)
                  .errorKey(ExceptionCodes.FIXIT_SERVICE_DOWN)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(INTERNAL_SERVER_ERROR)
              .errorResponse(errorResponse)
              .build();
        } else {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(FIT_SERVICE_DOWN)
                  .errorCode(GET_PTAG_ERROR_CODE)
                  .errorKey(ExceptionCodes.FIT_SERVICE_DOWN)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(INTERNAL_SERVER_ERROR)
              .errorResponse(errorResponse)
              .build();
        }
      } else {
        log.error("FIT/FixIt error cid={}, response={}", cid, response);
        if (isFixit) {
          throw new ReceivingDataNotFoundException(
              FIXIT_NOT_ACCESSIBLE, GENERIC_ERROR_RE_TRY_OR_REPORT);
        } else {
          throw new ReceivingDataNotFoundException(
              FIT_NOT_ACCESSIBLE, GENERIC_ERROR_RE_TRY_OR_REPORT);
        }
      }
    }
  }

  /**
   * Create problem tag
   *
   * @param createProblemRequest payload to create problem
   * @return response from FIT/Problem/FIXit
   * @throws ReceivingException
   */
  public String createProblemTag(String createProblemRequest) throws ReceivingException {
    ResponseEntity<String> response;
    response =
        restUtils.post(
            appConfig.getFitBaseUrl() + ReceivingConstants.CREATE_PROBLEM_URI,
            ReceivingUtils.getHeaders(),
            null,
            createProblemRequest);

    // TODO Find a solution to the detailed error messages from FIT
    if (response.getStatusCode().series() != SUCCESSFUL) {
      if (response.getStatusCode() == SERVICE_UNAVAILABLE) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(FIT_SERVICE_DOWN)
                .errorKey(ExceptionCodes.FIT_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
      if (response.getStatusCode() == NOT_FOUND) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE)
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIT)
                .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
      if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.CREATE_PTAG_ERROR_MESSAGE)
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIT)
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
                .errorCode(ReceivingException.CREATE_PTAG_ERROR_CODE_FIT)
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
                .errorKey(ExceptionCodes.CREATE_PTAG_ERROR_MESSAGE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    }
    return response.getBody();
  }

  /**
   * This method reports problem to FIT when we are trying to receive more than problem resolution
   * quantity.
   *
   * @param problemTagId
   * @param reportProblemRequest
   * @return String
   * @throws ReceivingException
   */
  public String reportProblem(
      String problemTagId, String issueId, ReportProblemRequest reportProblemRequest)
      throws ReceivingException {
    log.info("Reporting problem receiving error to Fixit for problem label: {}", problemTagId);
    ResponseEntity<String> response = null;
    Map<String, String> pathParams = new HashMap<>();

    pathParams.put(ReceivingConstants.ISSUE_ID, issueId);
    pathParams.put(ReceivingConstants.PROBLEM_LABEL, problemTagId);

    response =
        restUtils.post(
            appConfig.getFitBaseUrl()
                + PROBLEM_V1_URI
                + ReceivingConstants.FIT_UPDATE_PROBLEM_RECEIVE_ERROR_URI,
            ReceivingUtils.getHeaders(),
            pathParams,
            gson.toJson(reportProblemRequest));

    log.info(
        "Received problem reporting response:{} from FIT for problem label: {}",
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
    log.info("Successfully reported problem to FIT for problem label: {}", problemTagId);
    return response.getBody();
  }

  /**
   * This method reports problem received quantity to Fixit. Once all the problem resolution
   * quantity is received, the problem label data will be purged from the db.
   *
   * @param instruction
   * @throws ReceivingException
   */
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      externalCall = true,
      executionFlow = "completeProblem")
  @Transactional
  @InjectTenantFilter
  public void completeProblem(Instruction instruction) throws ReceivingException {
    ProblemLabel problemLabel =
        problemRepository.findProblemLabelByProblemTagId(instruction.getProblemTagId());

    if (Objects.isNull(problemLabel)) {
      log.error("Problem label not found for problemTag: {}", instruction.getProblemTagId());
      throw new ReceivingBadDataException(ExceptionCodes.PROBLEM_NOT_FOUND, PTAG_NOT_FOUND);
    }

    String problemTagId = problemLabel.getProblemTagId();
    FitProblemTagResponse fitProblemTagResponse =
        gson.fromJson(problemLabel.getProblemResponse(), FitProblemTagResponse.class);

    Problem problem = new Problem();
    problem.setResolutionId(problemLabel.getResolutionId());
    problem.setResolutionQty(fitProblemTagResponse.getResolutions().get(0).getQuantity());
    problem.setProblemTagId(instruction.getProblemTagId());
    problem.setDeliveryNumber(String.valueOf(instruction.getDeliveryNumber()));
    problem.setIssueId(problemLabel.getIssueId());

    TenantContext.get()
        .setReceiveInstrReportProblemReceivedQtyCallStart(System.currentTimeMillis());
    notifyCompleteProblemTag(problemTagId, problem, (long) instruction.getReceivedQuantity());
    TenantContext.get().setReceiveInstrReportProblemReceivedQtyCallEnd(System.currentTimeMillis());

    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Long receivedQuantity =
        instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                problemTagId);

    if (receivedQuantity == problem.getResolutionQty().longValue()) {
      log.info(
          "Successfully received all the resolution quantity: {} for problem label: {}, so deleting label from Problem table",
          problem.getResolutionQty(),
          problemTagId);
      problemRepository.delete(problemLabel);
    }
  }
}
