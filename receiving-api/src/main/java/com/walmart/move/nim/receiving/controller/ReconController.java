package com.walmart.move.nim.receiving.controller;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadMessageDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.service.RdcInstructionDownloadProcessor;
import com.walmart.move.nim.receiving.reporting.service.ReconServiceSecondary;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Reconciliation controller provides APIs for either upstream system or down stream system to
 * reconcile data from what recent receiving has got in case of Database switch due to any failures.
 * Some use cases could be a. retrieve all receipts by delivery : this should return all containers
 * for given delivery .. expecting huge response data size this may container unpublished containers
 * b. retrieve all received quantities by PO/POLine , this will for given delivery c. retrieve all
 * quantities received quantities for a given PO/POLine d. all the instructions for a give delivery
 *
 * <p>Precaution: ALL methods in this controller must be read only, it must not modify data
 *
 * <p>pre-requisite: all MT headers must be provided as MTFilter appllies to this controller too.
 *
 * @author a0s01qi
 */
@RestController
@RequestMapping("recon")
public class ReconController {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReconController.class);
  public static final String EVENT_TYPE = "eventType";
  public static final String ADDHEADERS = "addheaders";
  public static final String KEY = "key";
  public static final String DEFAULT_KEY = "default_kafka_key";

  @Autowired ContainerService containerService;

  @Autowired PrintJobService printJobService;

  @Autowired ReceiptService receiptService;

  @Autowired ReconServiceSecondary reconService;

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  private InstructionService instructionService;

  @Autowired private JmsPublisher jmsPublisher;

  @Autowired private DCFinService dcFinService;

  @Autowired private Gson gson;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private EpcisService epcisService;
  @Autowired private RxInstructionService rxInstructionService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ApplicationContext applicationContext;

  @Autowired ContainerTransformer containerTransformer;
  @Autowired RdcInstructionDownloadProcessor rdcInstructionDownloadProcessor;
  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Value("${kafka.publish.restricted.topics:}")
  private List<String> kafkaPublishRestrictedTopics;

  @GetMapping(path = "/containers/{deliveryNumber}", produces = "application/json")
  @Operation(
      summary =
          "Return all containers(published or unpublished) received so far for given delivery",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: JohnDoe",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public List<Container> getAllContainersByDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    return containerService.getContainerByDeliveryNumber(deliveryNumber);
  }

  @PostMapping(path = "/publishMessage/{queueName}/publish", produces = "application/json")
  public ResponseEntity<String> publishMessage(
      @PathVariable(value = "queueName", required = true) String queueName,
      @RequestBody @Valid String message,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceivingJMSEvent receivingJMSEvent =
        new ReceivingJMSEvent(
            httpHeaders.getFirst("facilityNum"),
            httpHeaders.getFirst("facilityCountryCode"),
            ReceivingUtils.getForwardablHeader(httpHeaders),
            message);
    if (!StringUtils.startsWithIgnoreCase(queueName, ReceivingConstants.JMS_QUEUE_PREFIX)) {
      queueName = queueName.replaceAll("\\.", "/");
    }
    jmsPublisher.publish(queueName, receivingJMSEvent, Boolean.TRUE);
    return new ResponseEntity<>(message, HttpStatus.ACCEPTED);
  }

  @PostMapping(path = "/publishMessage/kafka/{topicName}/publish", produces = "application/json")
  public ResponseEntity<Void> publishMessageOnKafka(
      @PathVariable(value = "topicName", required = true) String topicName,
      @RequestBody @Valid String payload,
      @RequestHeader HttpHeaders httpHeaders) {
    try {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED,
          false)) {
        if (kafkaPublishRestrictedTopics
            .stream()
            .anyMatch(restrictedTopic -> restrictedTopic.equalsIgnoreCase(topicName))) {
          throw new Exception(ExceptionCodes.PUBLISH_TO_KAFKA_TOPIC_RESTRICTED);
        }
      }
      Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
      if (nonNull(httpHeaders.getFirst(ADDHEADERS)))
        httpHeaders.forEach(
            (key, values) -> {
              if (EVENT_TYPE.equalsIgnoreCase(key)) key = EVENT_TYPE;
              headers.put(key, httpHeaders.getFirst(key));
            });

      String key = isBlank(httpHeaders.getFirst(KEY)) ? DEFAULT_KEY : httpHeaders.getFirst(KEY);
      Message<String> message = KafkaHelper.buildKafkaMessage(key, payload, topicName, headers);
      secureKafkaTemplate.send(message);
      LOGGER.info("Successfully sent to={}, the msg={}", topicName, message);
    } catch (Exception exception) {
      LOGGER.error("Unable to publish {}", ExceptionUtils.getStackTrace(exception));
      if (ExceptionCodes.PUBLISH_TO_KAFKA_TOPIC_RESTRICTED.equalsIgnoreCase(
          exception.getMessage())) {
        throw new ReceivingInternalException(
            ExceptionCodes.PUBLISH_TO_KAFKA_TOPIC_RESTRICTED,
            String.format(
                ReceivingConstants.PUBLISH_TO_KAFKA_TOPIC_RESTRICTED_ERROR_MSG, topicName));
      }
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
              ReceivingConstants.RECON_PUBLISH_FLOW));
    }
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }

  /**
   * This method will get containers details based on instruction Id
   *
   * @param instructionId
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/containers/instruction/{instructionId}")
  public List<Container> getContainerByInstructionId(
      @PathVariable(value = "instructionId", required = true) Long instructionId)
      throws ReceivingException {
    return containerService.getContainerByInstruction(instructionId);
  }

  /**
   * This method will get printjobs details based on instruction Id
   *
   * @param instructionId
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/printjobs/instruction/{instructionId}")
  public List<PrintJob> getPrintjobByInstructionId(
      @PathVariable(value = "instructionId", required = true) Long instructionId)
      throws ReceivingException {
    return printJobService.getPrintJobByInstruction(instructionId);
  }

  /** below are MT specific test cases for receipts */
  @GetMapping("/receipts/{deliveryNumber}")
  public List<Receipt> getReceiptsForDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber) {
    return receiptService.findByDeliveryNumber(deliveryNumber);
  }

  /**
   * This API is used to get print job details based on delivery number
   *
   * @param deliveryNumber
   * @return
   */
  @GetMapping("/printjobs/{deliveryNumber}")
  public List<PrintJob> getPrintJob(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber) {
    return printJobService.getPrintJobsByDeliveryNumber(deliveryNumber);
  }

  /**
   * This API will fetch instruction along with the delivery document used to create instruction
   *
   * @param messageId message id
   * @param headers http headers
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/instruction/{messageId}", produces = "application/json")
  public ResponseEntity<InstructionResponse> instructionRequest(
      @PathVariable(value = "messageId", required = true) String messageId,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    return new ResponseEntity<>(
        instructionService.getInstructionByMessageId(messageId, headers), HttpStatus.OK);
  }

  /**
   * recon caller first need to call this api to get minimalistic info if container found in their
   * db just continue else call another api to get full details of container
   *
   * @param pastNmins
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/containers/bytime/{mins}")
  public List<Container> getContainerByTime(
      @PathVariable(value = "mins", required = true) Integer pastNmins) throws ReceivingException {
    return containerService.getContainerByTime(pastNmins);
  }

  /**
   * get full details of a container by lpn/tracking id
   *
   * @param trackingId
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/container/bytrackingid/{trackingId}")
  public Container getContainerByTrackingId(
      @PathVariable(value = "trackingId", required = true) String trackingId)
      throws ReceivingException {
    return containerService.getContainerByTrackingId(trackingId);
  }

  /**
   * post receipts to DC Fin for a given trackingId
   *
   * @param trackingId
   * @return
   * @throws ReceivingException
   */
  @PostMapping(path = "/container/{trackingId}/purchases")
  public ResponseEntity<String> postReceiptsByTrackingId(
      @PathVariable(value = "trackingId", required = true) String trackingId)
      throws ReceivingException {
    dcFinService.postReceiptsToDCFin(trackingId);
    return new ResponseEntity<>(
        String.format("Posted receipts to DC Fin for container{%s} successfully", trackingId),
        HttpStatus.OK);
  }

  /**
   * post receipts to DC Fin for a given trackingId
   *
   * @param deliveryNumber
   * @return
   * @throws ReceivingException
   */
  @PostMapping(path = "/delivery/{deliveryNumber}/container/purchases")
  public ResponseEntity<String> postReceiptsByDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    dcFinService.postReceiptsForDelivery(deliveryNumber);
    return new ResponseEntity<>(
        String.format("Posted receipts to DC Fin for delivery{%s} successfully", deliveryNumber),
        HttpStatus.OK);
  }

  @PostMapping(path = "/container/{trackingId}/receipts")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: JohnDoe",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "trackingId",
        required = true,
        example = "Example: a329870000000000000000001",
        description = "String",
        in = ParameterIn.PATH)
  })
  public String postReceiptGivenTrackingId(
      @PathVariable(value = "trackingId") String trackingId, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();

    String correlationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);

    if (correlationId == null) {
      httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    }

    // get parent container using tracking Id
    Container parentContainer = containerService.getContainerByTrackingId(trackingId);
    ContainerValidationUtils.checkIfContainerIsParentContainer(parentContainer);

    return gson.toJson(
        instructionService.getConsolidatedContainerAndPublishContainer(
            parentContainer, httpHeaders, Boolean.FALSE));
  }

  @GetMapping(path = "/WFT/search", produces = "application/json")
  @Operation(
      summary = "Return instruction based on container id as response",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<WFTResponse> getInstruction(
      @RequestParam(value = "trackingId", required = false) String trackingId,
      @RequestParam(value = "instructionId", required = false) String instructionId,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    WFTResponse wftResponse = null;
    wftResponse =
        instructionService.getInstructionAndContainerDetailsForWFT(
            trackingId, instructionId, headers);
    return new ResponseEntity<>(wftResponse, HttpStatus.OK);
  }

  @PostMapping(path = "/purchaseDetails")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32835",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: JohnDoe",
        description = "String",
        in = ParameterIn.HEADER)
  })
  public ResponseEntity<String> getReconciledDataByTime(
      @RequestBody @Valid DateTimeRange dateTimeRange, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    return new ResponseEntity<>(
        gson.toJson(
            reconService.getReconciledDataSummaryByTime(
                dateTimeRange.getFromDate(), dateTimeRange.getToDate(), httpHeaders)),
        HttpStatus.OK);
  }

  @PostMapping(path = "/receivedQty")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: JohnDoe",
        description = "String",
        in = ParameterIn.HEADER)
  })
  public ResponseEntity<String> postReceivedQtyGivenTimeAndActivityName(
      @RequestBody @Valid ActivityWithTimeRangeRequest activityWithTimeRangeRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    return new ResponseEntity<>(
        gson.toJson(
            reconService.postReceivedQtyGivenTimeAndActivityName(
                activityWithTimeRangeRequest.getActivityName(),
                activityWithTimeRangeRequest.getFromDate(),
                activityWithTimeRangeRequest.getToDate(),
                httpHeaders)),
        HttpStatus.OK);
  }

  @PostMapping(path = "/container/re-label", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get container(s) to be re-labelled for a given user.",
      description =
          "This returns list of container id(s) generated after the given trackingId for the given user and given time range. If trackingId is not provided then all labels generated for delivery for the given user will be returned. Default value of toDate is current time.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Country code",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Site Number",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "User ID",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<ReprintLabelResponse> reprintLabels(
      @RequestBody @Valid ReprintLabelRequest reprintLabelRequest,
      @RequestHeader HttpHeaders httpHeaders) {

    ReprintLabelResponse reprintLabelResponse =
        reconService.postLabels(reprintLabelRequest, httpHeaders);
    if (Objects.isNull(reprintLabelResponse)
        || CollectionUtils.isEmpty(reprintLabelResponse.getTrackingIds())) {
      return new ResponseEntity<>(reprintLabelResponse, HttpStatus.NO_CONTENT);
    }
    return new ResponseEntity<>(reprintLabelResponse, HttpStatus.OK);
  }

  @PostMapping(path = "/publish/updates/{trackingId}")
  @Operation(
      summary = "Publish receiving updates for a given trackingId.",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> publishContainerUpdate(
      @PathVariable(value = "trackingId", required = true) String trackingId,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    receiptPublisher.publishReceiptUpdate(trackingId, httpHeaders, Boolean.TRUE);

    return new ResponseEntity<>(
        String.format("Published receiving updates for container{%s} successfully", trackingId),
        HttpStatus.OK);
  }

  @PostMapping(path = "/publish/serializedData")
  @Operation(
      summary = "Publish receiving updates for a given trackingId.",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> publishSerializedData(
      @RequestParam(value = "instructionIds", required = true) String instructionIds,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    List<String> instructionIdList =
        Arrays.asList(org.apache.commons.lang3.StringUtils.split(instructionIds, ","));
    if (!CollectionUtils.isEmpty(instructionIdList)) {
      for (String instructionId : instructionIdList) {
        Instruction instruction =
            instructionPersisterService.getInstructionById(Long.valueOf(instructionId));
        httpHeaders.replace(
            ReceivingConstants.CORRELATION_ID_HEADER_KEY,
            Arrays.asList(instruction.getMessageId()));
        epcisService.publishSerializedData(
            instruction, new DeliveryDocumentLine(), new CompleteInstructionRequest(), httpHeaders);
      }
    }

    return new ResponseEntity<>(String.format("Posted Serialized data succesfully"), HttpStatus.OK);
  }

  @PostMapping(
      path = "/linkRecentShipments",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Links Recent Shipment for Rx", description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public void linkRecentShipments(
      @RequestBody(required = true) InstructionRequest instructionRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    rxInstructionService.checkForLatestShipments(instructionRequest, httpHeaders, scannedDataMap);
  }

  @PostMapping(
      path = "/jmsretry/reset",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Resets JMS Retry count", description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  public void resetJmsRetryCount(
      @RequestBody(required = true) JmsRetryResetRequest jmsRetryResetRequest) {

    reconService.resetJmsRetryCount(jmsRetryResetRequest);
  }

  @PostMapping(
      path = "/jmsretry/resetById",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Resets JMS Retry count by Id", description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  public void resetJmsRetryCountById(
      @RequestBody(required = true) ActivityWithIdRequest activityWithIdRequest) {

    reconService.resetJmsRetryCountById(activityWithIdRequest);
  }

  @Operation(
      summary = "This will fetch the latest received container by item number.",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "getContainerItemByItemNumberTimed",
      level1 = "uwms-receiving",
      level2 = "reconController",
      level3 = "getContainerItemByItemNumber")
  @ExceptionCounted(
      name = "getContainerItemByItemNumberCount",
      level1 = "uwms-receiving",
      level2 = "reconController",
      level3 = "getContainerItemByItemNumber")
  @GetMapping(path = "/container/byitem/{itemNumber}", produces = "application/json")
  public ResponseEntity<ContainerItem> getContainerItemByItemNumber(
      @PathVariable(value = "itemNumber", required = true) Long itemNumber) {
    return new ResponseEntity(containerService.getContainerByItemNumber(itemNumber), HttpStatus.OK);
  }

  @Operation(
      summary = "Publish container information to Kafka for a given trackingId.",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @PostMapping(path = "/container/{trackingId}/publish/kafka")
  public ResponseEntity<List<ContainerDTO>> publishContainerToKafka(
      @PathVariable(value = "trackingId", required = true) String trackingId)
      throws ReceivingException {
    Container container = containerPersisterService.getConsolidatedContainerForPublish(trackingId);
    List<ContainerDTO> containerDTOList =
        containerTransformer.transformList(Arrays.asList(container));
    containerService.publishMultipleContainersToInventory(containerDTOList);
    return new ResponseEntity<>(containerDTOList, HttpStatus.OK);
  }

  /*
   * This is a support api for offline receiving payload process
   *
   */
  @PostMapping(path = "/processInstructionDownload")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Calls GDM to fetch eaches details and transforms parent container",
      description = "Will be called by relayer after containers are outboxed")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Unit details were fetched, transformed and outboxed")
      })
  @Timed(
      name = "eachesDetailTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "outboxEachesDetail")
  @ExceptionCounted(
      name = "eachesDetailExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "outboxEachesDetail")
  public void processInstructionDownload(
      @RequestBody InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      @RequestHeader HttpHeaders httpHeaders) {
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    rdcInstructionDownloadProcessor.processOfflineLabelsGeneratedEvent(
        instructionDownloadMessageDTO);
  }

  @PutMapping(path = "/printing/backout/{trackingId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Backout the container if no label get printed",
      description =
          "In case the label does not get printed, it will update the container status and initiate backout")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Transaction success")})
  @Timed(
      name = "printingBackoutTimed",
      level1 = "uwms-receiving",
      level2 = "ReconController",
      level3 = "printingBackout")
  @ExceptionCounted(
      name = "printingBackoutExceptionCount",
      level1 = "uwms-receiving",
      level2 = "ReconController",
      level3 = "printingBackout")
  public void triggerBackoutPrinting(
      @PathVariable("trackingId") String trackingId, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    Container container =
        containerService.updateContainerInventoryStatus(
            trackingId, InventoryStatus.SUSPECTED.name());
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Arrays.asList(trackingId));
    containerService.cancelContainers(cancelContainerRequest, httpHeaders);
  }
}
