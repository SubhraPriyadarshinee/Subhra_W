package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.wfs.constants.WFSConstants.SHELF_LPN;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ApplicationBaseException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.UniversalInstructionResponse;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.GdmLpnDetailsResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class WFSDockTagService extends DockTagService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WFSDockTagService.class);

  private Gson gson;
  private Gson gsonForDate;

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private PrintJobService printJobService;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private ContainerPersisterService containerPersisterService;

  @Resource(name = WFSConstants.WFS_INSTRUCTION_SERVICE)
  private WFSInstructionService wfsInstructionService;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Autowired private InstructionHelperService instructionHelperService;

  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;

  @Resource(name = WFSConstants.WFS_LABEL_ID_PROCESSOR)
  private LabelIdProcessor labelIdProcessor;

  public WFSDockTagService() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    gsonForDate =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd"))
            .create();
  }

  public static Pair<PrintLabelData, Map<String, PrintLabelRequest>> generateDockTagLabels(
      CreateDockTagRequest createDockTagRequest,
      List<DockTag> dockTagData,
      HttpHeaders httpHeaders) {
    Map<String, PrintLabelRequest> dockTagPrintRequestMap = new HashMap<>();
    List<PrintLabelRequest> printRequests = new ArrayList<>();

    dockTagData.forEach(
        dockTag -> {
          List<LabelData> datumList = new ArrayList<>();
          datumList.add(new LabelData("DOOR", createDockTagRequest.getDoorNumber()));
          datumList.add(new LabelData("DATE", new SimpleDateFormat("MM/dd/yy").format(new Date())));
          datumList.add(new LabelData("LPN", dockTag.getDockTagId()));
          datumList.add(
              new LabelData(
                  "FULLUSERID", httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY)));
          datumList.add(new LabelData("DELIVERYNBR", dockTag.getDeliveryNumber().toString()));
          datumList.add(new LabelData("DOCKTAGTYPE", dockTag.getDockTagType().getText()));

          PrintLabelRequest printLabelRequest = new PrintLabelRequest();
          printLabelRequest.setData(datumList);
          printLabelRequest.setLabelIdentifier(dockTag.getDockTagId());
          printLabelRequest.setFormatName("dock_tag_atlas");
          printLabelRequest.setTtlInHours(72);
          printRequests.add(printLabelRequest);
          dockTagPrintRequestMap.put(dockTag.getDockTagId(), printLabelRequest);
        });

    PrintLabelData printLabelData = new PrintLabelData();
    printLabelData.setClientId(ReceivingConstants.ATLAS_RECEIVING);
    printLabelData.setPrintRequests(printRequests);
    printLabelData.setHeaders(ContainerUtils.getPrintRequestHeaders(httpHeaders));

    return new Pair<>(printLabelData, dockTagPrintRequestMap);
  }

  @Transactional
  @InjectTenantFilter
  public DockTagResponse createDockTags(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    LOGGER.info("Creating docktags for the request {}", createDockTagRequest);
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    // Generate docktags
    List<String> dockTagIds = getLPNDockTagIds(createDockTagRequest, headers);
    List<DockTag> dockTags = new ArrayList<>();
    for (String dockTagId : dockTagIds) {
      DockTag dockTag = new DockTag();
      dockTag.setDockTagId(dockTagId);
      dockTag.setDeliveryNumber(createDockTagRequest.getDeliveryNumber());
      dockTag.setScannedLocation(createDockTagRequest.getDoorNumber());
      dockTag.setCreateUserId(userId);
      dockTag.setLastChangedUserId(userId);
      dockTag.setDockTagType(DockTagType.ATLAS_RECEIVING);
      dockTag.setDockTagStatus(InstructionStatus.CREATED);
      dockTags.add(dockTag);
    }
    dockTagPersisterService.saveAllDockTags(dockTags);

    // Get Label Data
    Pair<PrintLabelData, Map<String, PrintLabelRequest>> printLabelDataPair =
        generateDockTagLabels(createDockTagRequest, dockTags, headers);
    PrintLabelData labelData = printLabelDataPair.getKey();

    // Create DockTag Instructions
    List<Instruction> docktagInstructions = new ArrayList<>();
    for (String dockTagId : dockTagIds) {
      Instruction dockTagInstruction =
          wfsInstructionService.getDockTagInstruction(createDockTagRequest, dockTagId, headers);

      docktagInstructions.add(dockTagInstruction);
    }
    List<Instruction> docktagInstructions4mDB = instructionRepository.saveAll(docktagInstructions);

    // Create DockTag Containers
    List<Container> docktagContainers = new ArrayList<>();
    List<PrintJob> printJobs = new ArrayList<>();
    Container baseContainer = getDockTagContainer(createDockTagRequest, headers);
    for (Instruction instruction4mDB : docktagInstructions4mDB) {
      Container dockTagContainer = SerializationUtils.clone(baseContainer);
      dockTagContainer.setTrackingId(instruction4mDB.getDockTagId());
      dockTagContainer.setMessageId(instruction4mDB.getDockTagId());
      dockTagContainer.setInstructionId(instruction4mDB.getId());
      docktagContainers.add(dockTagContainer);

      // Publish Container
      wfsInstructionService.publishAndGetInstructionResponse(
          dockTagContainer, instruction4mDB, headers);

      PrintJob printJob = new PrintJob();
      printJob.setDeliveryNumber(instruction4mDB.getDeliveryNumber());
      printJob.setInstructionId(instruction4mDB.getId());
      printJob.setLabelIdentifier(new HashSet<>(Arrays.asList(instruction4mDB.getDockTagId())));
      printJob.setCreateUserId(userId);
      printJobs.add(printJob);
    }
    containerPersisterService.saveContainers(docktagContainers);

    // Persist Print Jobs
    printJobService.savePrintJobs(printJobs);

    LOGGER.info("Completed creating docktags for the request {}", createDockTagRequest);

    return DockTagResponse.builder()
        .deliveryNumber(createDockTagRequest.getDeliveryNumber())
        .dockTags(dockTagIds)
        .printData(labelData)
        .build();
  }

  public Container getDockTagContainer(
      CreateDockTagRequest createDockTagRequest, HttpHeaders httpHeaders) {
    Container container = new Container();
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    container.setCtrReusable(Boolean.FALSE);
    container.setCtrShippable(Boolean.FALSE);
    container.setInstructionId(ReceivingConstants.DUMMY_INSTRUCTION_ID);
    container.setLocation(createDockTagRequest.getDoorNumber());
    container.setDeliveryNumber(createDockTagRequest.getDeliveryNumber());
    container.setContainerType(ContainerType.PALLET.name());
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setIsConveyable(Boolean.FALSE);
    container.setOnConveyor(Boolean.FALSE);
    container.setLabelId(
        labelIdProcessor.getLabelId(ReceivingConstants.DOCK_TAG, container.getContainerType()));

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());
    container.setFacility(facility);

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    container.setCreateUser(userId);
    container.setLastChangedUser(userId);
    container.setLastChangedTs(new Date());
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());

    return container;
  }

  @Override
  public void saveDockTag(DockTag dockTag) {
    dockTagPersisterService.saveDockTag(dockTag);
  }

  @Override
  public Integer countOfOpenDockTags(Long deliveryNumber) {
    return countOfPendingDockTags(deliveryNumber);
  }

  private List<String> getLPNDockTagIds(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    TenantContext.get().setCreateDockTagFetchLpnsCallStart(System.currentTimeMillis());
    int count = createDockTagRequest.getCount() == null ? 1 : createDockTagRequest.getCount();
    List<String> lpns = lpnCacheService.getLPNSBasedOnTenant(count, headers);
    TenantContext.get().setCreateDockTagFetchLpnsCallEnd(System.currentTimeMillis());
    return lpns;
  }

  @Override
  public InstructionResponse createDockTag(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) {
    LOGGER.warn("No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public String searchDockTag(SearchDockTagRequest searchDockTagRequest, InstructionStatus status) {
    List<String> deliveryNumbers = searchDockTagRequest.getDeliveryNumbers();
    validateDeliveryNumbers(deliveryNumbers);
    List<Long> longDeliveryNums = new ArrayList<>();
    for (String deliveryNumber : deliveryNumbers) {
      longDeliveryNums.add(Long.parseLong(deliveryNumber));
    }
    List<DockTag> dockTagResponse = null;
    if (StringUtils.isEmpty(status)) {
      LOGGER.info("Fetching docktag for deliveries {}", longDeliveryNums);
      dockTagResponse = dockTagPersisterService.getDockTagsByDeliveries(longDeliveryNums);
    } else {
      LOGGER.info("Fetching docktag for deliveries {} and status {}", longDeliveryNums, status);
      dockTagResponse =
          dockTagPersisterService.getDockTagsByDeliveriesAndStatuses(
              longDeliveryNums,
              status.equals(InstructionStatus.CREATED)
                  ? ReceivingUtils.getPendingDockTagStatus()
                  : Collections.singletonList(status));
    }
    return CollectionUtils.isEmpty(dockTagResponse) ? null : gson.toJson(dockTagResponse);
  }

  private void validateDeliveryNumbers(List<String> deliveryNumbers) {
    for (String deliveryNumber : deliveryNumbers) {
      if (org.springframework.util.StringUtils.isEmpty(deliveryNumber)
          || !ReceivingUtils.isNumeric(deliveryNumber)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_DATA, ReceivingConstants.INVALID_DELIVERY_NUMBER);
      }
    }
  }

  @Override
  public void updateDockTagById(String dockTagId, InstructionStatus status, String userId) {
    LOGGER.warn("No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Deprecated
  @Override
  public CompleteDockTagResponse completeDockTags(
      CompleteDockTagRequest completeDockTagRequest, HttpHeaders headers) {

    List<String> docktags = completeDockTagRequest.getDocktags();

    List<DockTag> dockTagsFromDb = dockTagPersisterService.getDockTagsByDockTagIds(docktags);
    if (org.springframework.util.CollectionUtils.isEmpty(dockTagsFromDb)) {
      return CompleteDockTagResponse.builder().failed(docktags).build();
    }
    List<String> failed = new ArrayList<>();

    if (dockTagsFromDb.size() != docktags.size()) {
      List<String> dockTagIds =
          dockTagsFromDb.stream().map(DockTag::getDockTagId).collect(Collectors.toList());
      // validate if all dockIds are present in DB
      for (String dockTag : docktags) {
        if (!dockTagIds.contains(dockTag)) {
          failed.add(dockTag);
        }
      }
    }
    List<String> dockTagIds = new ArrayList<>();
    for (DockTag dockTag : dockTagsFromDb) {
      if (Objects.nonNull(dockTag.getCompleteTs())) {
        // This is already complete- no changes required
        continue;
      }
      dockTagIds.add(dockTag.getDockTagId());
      try {
        markCompleteAndDeleteFromInventory(headers, dockTag);
      } catch (ApplicationBaseException e) {
        failed.add(dockTag.getDockTagId());
      }
    }

    // Publish the Docktags based upon status
    if (!dockTagIds.isEmpty()) {
      publishDockTagContainer(headers, dockTagIds, ReceivingConstants.STATUS_COMPLETE);
    }
    // update DB
    dockTagPersisterService.saveAllDockTags(dockTagsFromDb);
    checkAndPublishPendingDockTags(
        headers,
        completeDockTagRequest.getDeliveryStatus(),
        completeDockTagRequest.getDeliveryNumber());

    List<String> success = new ArrayList<>();
    for (String s : docktags) {
      if (!failed.contains(s)) success.add(s);
    }

    return CompleteDockTagResponse.builder().failed(failed).success(success).build();
  }

  @Override
  public List<CompleteDockTagResponse> completeDockTagsForGivenDeliveries(
      CompleteDockTagRequestsList completeDockTagRequest, HttpHeaders headers) {
    LOGGER.warn("Cannot complete dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DockTag createDockTag(
      String dockTagId, Long deliveryNumber, String userId, DockTagType dockTagType) {
    LOGGER.warn("Cannot create dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public InstructionResponse receiveDockTag(
      ReceiveDockTagRequest receiveDockTagRequest, HttpHeaders httpHeaders) {
    LOGGER.warn(
        "Cannot receive floor line dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public ReceiveNonConDockTagResponse receiveNonConDockTag(
      String dockTagId, HttpHeaders httpHeaders) {
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    return getReceiveNonConDockTagResponse(dockTagId, httpHeaders, dockTagFromDb);
  }

  private ReceiveNonConDockTagResponse getReceiveNonConDockTagResponse(
      String dockTagId, HttpHeaders httpHeaders, DockTag dockTagFromDb) {
    validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);

    DeliveryDetails deliveryDetails = null;
    Long deliveryNumber = dockTagFromDb.getDeliveryNumber();
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)) {
      try {
        DeliveryService deliveryService =
            tenantSpecificConfigReader.getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DELIVERY_SERVICE_KEY,
                DeliveryService.class);
        String deliveryByDeliveryNumberStr =
            deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
        deliveryDetails = gsonForDate.fromJson(deliveryByDeliveryNumberStr, DeliveryDetails.class);
        // check if delivery is closed, then call reopen
        instructionHelperService.reopenDeliveryIfNeeded(
            deliveryNumber,
            deliveryDetails.getDeliveryStatus(),
            ReceivingUtils.getHeaders(),
            deliveryDetails.getDeliveryLegacyStatus());
      } catch (ReceivingException receivingException) {
        LOGGER.error("Can't fetch delivery: {} details.", deliveryNumber);
      }
    }
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    dockTagFromDb.setLastChangedUserId(userId);
    dockTagFromDb.setDockTagStatus(InstructionStatus.UPDATED);

    if (Objects.isNull(deliveryDetails)) {
      // if auto reopen flag is enabled, then we will be returning the DeliveryDetails fetched form
      // GDM in response
      // else, deliveryDetails in response should ONLY have the deliveryNumber field, (others null)
      deliveryDetails = new DeliveryDetails();
      deliveryDetails.setDeliveryNumber(deliveryNumber);
    }
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        ReceiveNonConDockTagResponse.builder().delivery(deliveryDetails).build();
    publishDockTagContainer(
        httpHeaders, dockTagFromDb.getDockTagId(), ReceivingConstants.STATUS_ACTIVE);
    dockTagPersisterService.saveDockTag(dockTagFromDb);
    return receiveNonConDockTagResponse;
  }

  private InstructionResponse receiveLpnFlow(
      String lpnNumber, String doorNumber, HttpHeaders httpHeaders) throws ReceivingException {

    DeliveryService deliveryService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    String lpnDetailsByLpnNumberStr =
        deliveryService.getLpnDetailsByLpnNumber(lpnNumber, httpHeaders);
    GdmLpnDetailsResponse gdmLpnDetailsResponse =
        gsonForDate.fromJson(lpnDetailsByLpnNumberStr, GdmLpnDetailsResponse.class);
    validateGDMResponse(gdmLpnDetailsResponse);
    InstructionResponse instructionResponse =
        fetchDeliveryDocument(gdmLpnDetailsResponse, doorNumber, httpHeaders);
    return fetchInstructions(gdmLpnDetailsResponse, instructionResponse, doorNumber, httpHeaders);
  }

  void validateGDMResponse(GdmLpnDetailsResponse gdmLpnDetailsResponse) {
    if (CollectionUtils.isEmpty(gdmLpnDetailsResponse.getPacks())
        || CollectionUtils.isEmpty(gdmLpnDetailsResponse.getShipments())
        || org.apache.commons.lang.StringUtils.isEmpty(
            gdmLpnDetailsResponse.getPacks().get(0).getReceivingStatus()))
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR, String.format(ReceivingException.LPN_DETAILS_NOT_FOUND));
    if (Objects.isNull(gdmLpnDetailsResponse.getShipments().get(0).getShipmentDetail()))
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR, String.format(ReceivingException.GDM_GET_DELIVERY_ERROR));
    if (ReceivingConstants.RECEIVING_STATUS_RECEIVED.equalsIgnoreCase(
        gdmLpnDetailsResponse.getPacks().get(0).getReceivingStatus()))
      throw new ReceivingInternalException(
          ExceptionCodes.RE_RECEIVING_LPN_ALREADY_RECEIVED,
          String.format(
              ReceivingConstants.RECEIVING_ERROR_INVALID_RECEIVING_STATUS,
              gdmLpnDetailsResponse.getPacks().get(0).getPackNumber()),
          gdmLpnDetailsResponse.getPacks().get(0).getPackNumber());
  }

  private InstructionResponse fetchInstructions(
      GdmLpnDetailsResponse gdmLpnDetailsResponse,
      InstructionResponse instructionResponse,
      String doorNumber,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(UUID.randomUUID().toString());
    instructionRequest.setEnteredQtyUOM("EA");
    instructionRequest.setDeliveryNumber(
        String.valueOf(
            gdmLpnDetailsResponse
                .getShipments()
                .get(0)
                .getShipmentDetail()
                .getReportedDeliveryNumber()));
    instructionRequest.setDoorNumber(doorNumber);
    instructionRequest.setUpcNumber(
        gdmLpnDetailsResponse.getPacks().get(0).getItems().get(0).getGtin());
    instructionRequest.setMultiSKUItem(false);
    instructionRequest.setReceivingType("LPN");
    instructionRequest.setDoorNumber(doorNumber);
    instructionRequest.setEnteredQty(
        gdmLpnDetailsResponse
            .getPacks()
            .get(0)
            .getItems()
            .get(0)
            .getInventoryDetail()
            .getReportedQuantity()
            .intValue());
    Map<String, Object> additionalParams = new HashMap<>();
    additionalParams.put(ReceivingConstants.IS_RE_RECEIVING_LPN_FLOW, Boolean.TRUE);
    additionalParams.put(SHELF_LPN, gdmLpnDetailsResponse.getPacks().get(0).getPackNumber());
    additionalParams.put(
        ReceivingConstants.RE_RECEIVING_SHIPMENT_NUMBER,
        gdmLpnDetailsResponse.getShipments().get(0).getShipmentNumber());
    instructionRequest.setAdditionalParams(additionalParams);
    instructionRequest.setDeliveryDocuments(instructionResponse.getDeliveryDocuments());
    ObjectMapper objectMapper = new ObjectMapper();
    String instructionRequestString;
    try {
      instructionRequestString = objectMapper.writeValueAsString(instructionRequest);
    } catch (JsonProcessingException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PARSE, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    }
    return instructionServiceByFacility.serveInstructionRequest(
        instructionRequestString, httpHeaders);
  }

  private InstructionResponse fetchDeliveryDocument(
      GdmLpnDetailsResponse gdmLpnDetailsResponse, String doorNumber, HttpHeaders httpHeaders)
      throws ReceivingException {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(UUID.randomUUID().toString());
    LOGGER.info("MessagedId = {}", instructionRequest.getMessageId());
    instructionRequest.setEnteredQtyUOM("EA");
    instructionRequest.setDeliveryNumber(
        String.valueOf(
            gdmLpnDetailsResponse
                .getShipments()
                .get(0)
                .getShipmentDetail()
                .getReportedDeliveryNumber()));
    instructionRequest.setUpcNumber(
        gdmLpnDetailsResponse.getPacks().get(0).getItems().get(0).getGtin());
    instructionRequest.setMultiSKUItem(false);
    instructionRequest.setReceivingType("LPN");
    instructionRequest.setDoorNumber(doorNumber);
    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    Map<String, Object> additionalParams = new HashMap<>();
    additionalParams.put(ReceivingConstants.IS_RE_RECEIVING_LPN_FLOW, Boolean.TRUE);
    additionalParams.put(SHELF_LPN, gdmLpnDetailsResponse.getPacks().get(0).getPackNumber());
    additionalParams.put(
        ReceivingConstants.PURCHASE_ORDER_NUMBER,
        gdmLpnDetailsResponse.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    additionalParams.put(
        ReceivingConstants.RE_RECEIVING_SHIPMENT_NUMBER,
        gdmLpnDetailsResponse.getShipments().get(0).getShipmentNumber());
    instructionRequest.setAdditionalParams(additionalParams);
    ObjectMapper objectMapper = new ObjectMapper();
    String instructionRequestString;
    try {
      instructionRequestString = objectMapper.writeValueAsString(instructionRequest);
    } catch (JsonProcessingException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PARSE, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    }
    return instructionServiceByFacility.serveInstructionRequest(
        instructionRequestString, httpHeaders);
  }

  @Override
  public UniversalInstructionResponse receiveUniversalTag(
      String universalTag, String doorNumber, HttpHeaders httpHeaders) throws ReceivingException {
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(universalTag);

    UniversalInstructionResponse universalInstructionResponse = null;
    if (Objects.isNull(dockTagFromDb)) {
      InstructionResponse instructionResponse =
          receiveLpnFlow(universalTag, doorNumber, httpHeaders);
      universalInstructionResponse =
          UniversalInstructionResponse.builder()
              .deliveryDocuments(instructionResponse.getDeliveryDocuments())
              .deliveryStatus(instructionResponse.getDeliveryStatus())
              .instruction(instructionResponse.getInstruction())
              .instructions(instructionResponse.getInstructions())
              .build();
      InstructionService instructionServiceByFacility =
          tenantSpecificConfigReader.getInstructionServiceByFacility(
              TenantContext.getFacilityNum().toString());

      instructionServiceByFacility.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    } else {
      ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
          getReceiveNonConDockTagResponse(universalTag, httpHeaders, dockTagFromDb);
      universalInstructionResponse =
          UniversalInstructionResponse.builder()
              .delivery(receiveNonConDockTagResponse.getDelivery())
              .locationInfo(receiveNonConDockTagResponse.getLocationInfo())
              .build();
    }

    return universalInstructionResponse;
  }

  @Override
  public String completeDockTag(String dockTagId, HttpHeaders httpHeaders) {
    LOGGER.warn("Cannot complete dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DockTagResponse partialCompleteDockTag(
      CreateDockTagRequest createDockTagRequest,
      String dockTagId,
      boolean isRetryCompleteFlag,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.warn("Cannot complete workstation dock tag. No implementation available in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public void updateDockTagStatusAndPublish(
      ReceiveDockTagRequest receiveDockTagRequest, DockTag dockTagFromDb, HttpHeaders httpHeaders) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }
}
