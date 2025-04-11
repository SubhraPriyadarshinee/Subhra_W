package com.walmart.move.nim.receiving.controller;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveRequest;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsLpnRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeGetLpnsRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeLabelGroupUpdateRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.LabelReadinessRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.ScopeTest;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaLabelDataPublisher;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadMessageDTO;
import com.walmart.move.nim.receiving.core.model.symbotic.LabelGroupUpdateCompletedEventMessage;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcSlotUpdateMessage;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.service.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import javax.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("test")
@RestController
@Hidden
public class TestController {

  @Autowired private WitronITService witronService;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private PrintJobService printJobService;

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  private InstructionService instructionService;

  @Autowired private ReceiptService receiptService;

  @Autowired private ContainerService containerService;

  @Resource(name = ReceivingConstants.FDE_SERVICE)
  private FdeService fdeService;

  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;

  @Autowired private ApplicationContext context;

  @Autowired private DefaultItemCatalogService itemCatalogService;

  @Autowired
  private RdcLabelGroupUpdateCompletedEventProcessor rdcLabelGroupUpdateCompletedEventProcessor;

  @Autowired private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  @ManagedConfiguration private ScopeTest scopeTest;

  @Resource(name = ReceivingConstants.KAFKA_LABEL_DATA_PUBLISHER)
  private KafkaLabelDataPublisher kafkaLabelDataPublisher;

  @Autowired private RdcInstructionDownloadProcessor rdcInstructionDownloadProcessor;

  @Resource(name = ReceivingConstants.RDC_KAFKA_VERIFICATION_EVENT_PROCESSOR)
  private RdcVerificationEventProcessor rdcVerificationEventProcessor;

  @Resource(name = ReceivingConstants.RDC_DELIVERY_EVENT_PROCESSOR)
  private RdcDeliveryEventProcessor rdcDeliveryEventProcessor;

  @Autowired RdcLabelGenerationService rdcLabelGenerationService;

  @Autowired private RdcItemUpdateProcessor rdcItemUpdateProcessor;
  @Autowired private RdcSlotUpdateEventProcessor rdcSlotUpdateEventProcessor;

  private static final Logger LOG = LoggerFactory.getLogger(TestController.class);

  @GetMapping("/scope")
  public String getTestScope() {
    return scopeTest.getScopeSpecificValue();
  }

  @GetMapping("/containers/{deliveryNumber}")
  public List<Container> getAllContainersByDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    return containerService.getContainerByDeliveryNumber(deliveryNumber);
  }

  /**
   * This API is used to test tomcat timeout, will be removed once test is successful on all the env
   * infra test
   *
   * @throws InterruptedException
   */
  @GetMapping("/timeout")
  public String testTimeout() throws InterruptedException {
    long start = System.currentTimeMillis();
    while (appConfig.getIsTomcatTimeout()) {
      Thread.sleep(3000);
    }
    long stop = System.currentTimeMillis();
    return "waited for " + (stop - start) + " millis";
  }

  @PostMapping(
      path = "/uploadprintjob",
      produces = "application/json",
      consumes = "application/json")
  public PrintJob uploadPrintJob(@RequestBody Map<String, String> body) {
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add("lpn1232233eddd");
    return printJobService.createPrintJob(
        Long.parseLong(body.get("deliveryNumber")),
        Long.parseLong(body.get("instructionId")),
        printJobLpnSet,
        "test");
  }

  /**
   * This API is used to delete instructions based on delivery number which are created for
   * integration test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @DeleteMapping(path = "/delete/instructions/{deliveryNumber}")
  public void deleteInstructions(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    instructionService.deleteInstructionList(deliveryNumber);
  }

  /**
   * This API is used to delete receipts based on delivery number which are created for integration
   * test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @DeleteMapping(path = "/delete/receipts/{deliveryNumber}")
  public void deleteReceiptsByDeliveryNumber(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    receiptService.deleteReceptList(deliveryNumber);
  }

  /**
   * This API is used to delete container based on delivery number which are created for integration
   * test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @DeleteMapping(path = "/delete/containers/{deliveryNumber}")
  public void deleteContainersByDeliveryNumber(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    containerService.deleteContainers(deliveryNumber);
  }

  /**
   * This API is used to delete TemporaryTiHi based on 1.Delivery Number & 2.Item Number which are
   * created for integration test
   *
   * @param deliveryNumber
   * @param itemNumber
   * @throws ReceivingException
   */
  @DeleteMapping(path = "/delete/deliveryItemOverride/{deliveryNumber}/itemNumber/{itemNumber}")
  public void deleteDeliveryItemOverrideByDeliveryNumberAndItemNumber(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @PathVariable(value = "itemNumber", required = true) Long itemNumber)
      throws ReceivingException {

    ReceivingUtils.validateApiAccessibility();

    deliveryItemOverrideService.deleteByDeliveryNumberAndItemNumber(deliveryNumber, itemNumber);
  }

  /**
   * This API is used to delete container based on delivery number which are created for integration
   * test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @DeleteMapping(path = "/delete/printjobs/{deliveryNumber}")
  public void deletePrintjobsByDeliveryNumber(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    printJobService.deletePrintjobs(deliveryNumber);
  }

  @PostMapping(
      path = "/uploadreceipt",
      produces = "application/json",
      consumes = "application/json")
  public Receipt saveAReceipt(@RequestBody Map<String, String> body) {
    Receipt receipt1 = new Receipt();
    receipt1.setDeliveryNumber(Long.valueOf(body.get("deliveryNumber")));
    receipt1.setDoorNumber(body.get("doorNumber"));
    receipt1.setPurchaseReferenceNumber("9763140007");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setQuantity(2);
    receipt1.setQuantityUom("ZA");
    receipt1.setVnpkQty(48);
    receipt1.setWhpkQty(4);
    receipt1.setEachQty(96);
    receipt1.setCreateUserId("sysadmin");
    receipt1.setProblemId("1");
    return receiptService.saveReceipt(receipt1);
  }

  @PostMapping(path = "/test/fde", produces = "application/json")
  public ResponseEntity<String> testFdeReceive(
      @RequestBody @Valid FdeCreateContainerRequest fdeCreateContainerRequest,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    String instruction;
    instruction = fdeService.receive(fdeCreateContainerRequest, headers);
    return new ResponseEntity<>(instruction, HttpStatus.CREATED);
  }

  @Autowired ReceiptRepository receiptRepository;

  @Autowired InstructionRepository instructionRepository;

  @Transactional
  @DeleteMapping(path = "/receipt/{purchaseReferenceNumber}", produces = "application/json")
  public void deleteReceiptsByPO(
      @PathVariable(value = "purchaseReferenceNumber", required = true)
          String purchaseReferenceNumber)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    receiptRepository.deleteByPurchaseReferenceNumber(purchaseReferenceNumber);
  }

  @Transactional
  @DeleteMapping(path = "/instruction/{purchaseReferenceNumber}", produces = "application/json")
  public void deleteInstructionsByPO(
      @PathVariable(value = "purchaseReferenceNumber", required = true)
          String purchaseReferenceNumber)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    instructionRepository.deleteByPurchaseReferenceNumber(purchaseReferenceNumber);
  }

  @GetMapping(path = "/shutdown")
  public void shutdownApplication() throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    int exitCode = SpringApplication.exit(context, (ExitCodeGenerator) () -> 0);
    System.exit(exitCode);
  }

  @PostMapping(path = "/putaway/{testCase}", produces = "application/json")
  public String putawayIT(
      @PathVariable("testCase") String testCase,
      @RequestParam(required = false) String trackingId,
      @RequestParam(required = false) Long deliveryNumber,
      @RequestParam(required = false) Integer itemNumber,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    return witronService.routeTestCase(
        testCase, trackingId, deliveryNumber, itemNumber, httpHeaders);
  }

  @DeleteMapping(
      path = "/delete/containers/{purchaseReferenceNumber}/{purchaseReferenceLineNumber}",
      produces = "application/json")
  public void deleteContainersByPOAndPoLine(
      @PathVariable(value = "purchaseReferenceNumber") String purchaseReferenceNumber,
      @PathVariable(value = "purchaseReferenceLineNumber") Integer purchaseReferenceLineNumber) {
    ReceivingUtils.validateApiAccessibility();
    containerService.deleteContainersByPoAndPoLine(
        purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  /**
   * This API is used to delete all receiving based on delivery number which are created for
   * integration test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @DeleteMapping(path = "/delete/{deliveryNumber}")
  public void deleteReceivingDataByDeliveryNumber(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    instructionService.deleteByDeliveryNumber(deliveryNumber);
    receiptService.deleteByDeliveryNumber(deliveryNumber);
    try {
      containerService.deleteContainers(deliveryNumber);
    } catch (Exception e) {
    }
    printJobService.deleteByDeliveryNumber(deliveryNumber);
    itemCatalogService.deleteItemCatalogUpdatelogs(deliveryNumber);
  }

  @PostMapping(path = "/receive", produces = "application/json")
  public ResponseEntity testGLSReceive(
      @RequestBody @Valid GLSReceiveRequest glsReceiveRequest, @RequestHeader HttpHeaders headers) {
    ReceivingUtils.validateApiAccessibility();
    String response = witronService.glsReceive(glsReceiveRequest, headers);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(path = "/createTag", produces = "application/json")
  public ResponseEntity testCreateGlsLpn(
      @RequestBody @Valid GlsLpnRequest glsLpnRequest, @RequestHeader HttpHeaders headers) {
    ReceivingUtils.validateApiAccessibility();
    String response = witronService.createGlsLpn(glsLpnRequest, headers);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(path = "/adjust", produces = "application/json")
  public ResponseEntity testGLSAdjust(
      @RequestBody @Valid GlsAdjustPayload glsAdjustPayload, @RequestHeader HttpHeaders headers) {
    ReceivingUtils.validateApiAccessibility();
    String response = witronService.glsAdjust(glsAdjustPayload, headers);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @GetMapping(path = "/deliveryDetails/{deliveryNumber}", produces = "application/json")
  public ResponseEntity testGLSDeliveryDetails(
      @PathVariable(value = "deliveryNumber", required = true) String deliveryNumber,
      @RequestHeader HttpHeaders headers) {
    ReceivingUtils.validateApiAccessibility();
    String response = witronService.glsGetDeliveryDetails(deliveryNumber, headers);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(path = "/label-instructions-download")
  public ResponseEntity processInstructionsDownload(
      @RequestBody InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      @RequestHeader HttpHeaders httpHeaders) {
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }

  @PostMapping(path = "/verificationMessage", consumes = "application/json")
  public ResponseEntity processVerificationEvent(
      @RequestBody RdcVerificationMessage rdcVerificationMessage,
      @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    rdcVerificationMessage.setHttpHeaders(httpHeaders);
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }

  @PostMapping(path = "/completedMessage", consumes = "application/json")
  public ResponseEntity processCompletedEvent(
      @RequestBody LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage,
      @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    labelGroupUpdateCompletedEventMessage.setHttpHeaders(httpHeaders);
    rdcLabelGroupUpdateCompletedEventProcessor.processEvent(labelGroupUpdateCompletedEventMessage);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }

  /**
   * This API is used to check whether the location is ready for linking new delivery
   *
   * @param labelReadinessRequest
   * @throws ReceivingException
   */
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @GetMapping(path = "/label/group/readiness", produces = "application/json")
  public ResponseEntity testLabelReadiness(
      @QueryParam(value = "labelReadinessRequest") LabelReadinessRequest labelReadinessRequest,
      @RequestHeader HttpHeaders headers) {
    ReceivingUtils.validateApiAccessibility();
    ResponseEntity response =
        hawkeyeRestApiClient.checkLabelGroupReadinessStatus(labelReadinessRequest, headers);
    return new ResponseEntity<>(response, response.getStatusCode());
  }

  @PostMapping(path = "/delivery/update", consumes = "application/json")
  public ResponseEntity processGdmUpdateEvent(
      @RequestBody DeliveryUpdateMessage deliveryUpdateMessage,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    deliveryUpdateMessage.setHttpHeaders(httpHeaders);
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }

  @PostMapping(path = "/getLpns", consumes = "application/json")
  public Optional<List<String>> getLpnsFromHawkeye(
      @RequestBody HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    return hawkeyeRestApiClient.getLpnsFromHawkeye(hawkeyeGetLpnsRequest, httpHeaders);
  }

  @PostMapping(path = "/sendlabelgroupupdate/{deliveryNumber}", consumes = "application/json")
  public void sendDeliveryLink(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestBody HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingBadDataException, ReceivingInternalException {
    ReceivingUtils.validateApiAccessibility();
    hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
        hawkeyeLabelGroupUpdateRequest, deliveryNumber, httpHeaders);
  }

  @PostMapping(path = "/item/update", consumes = "application/json")
  public ResponseEntity processGdmItemUpdateEvent(
      @RequestBody ItemUpdateMessage itemUpdateMessage, @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    itemUpdateMessage.setHttpHeaders(httpHeaders);
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }

  @PostMapping(path = "/slot/update", consumes = "application/json")
  public ResponseEntity processSlotUpdateEvent(
      @RequestBody RdcSlotUpdateMessage rdcSlotUpdateMessage,
      @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    rdcSlotUpdateMessage.setHttpHeaders(httpHeaders);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }
}
