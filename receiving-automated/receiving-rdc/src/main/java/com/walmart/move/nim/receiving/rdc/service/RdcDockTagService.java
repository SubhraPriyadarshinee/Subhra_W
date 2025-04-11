package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DUMMY_INSTRUCTION_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary.Location;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary.UserInfo;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.repositories.DockTagCustomRepository;
import com.walmart.move.nim.receiving.rdc.utils.RdcDeliveryStatusUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang3.exception.ExceptionUtils;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

public class RdcDockTagService extends DockTagService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcDockTagService.class);

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private PrintJobService printJobService;
  @Autowired private TenantSpecificReportConfig tenantSpecificReportConfig;
  @Autowired private DockTagCustomRepository dockTagCustomRepository;
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private RdcMessagePublisher rdcMessagePublisher;
  @Autowired private DeliveryServiceRetryableImpl deliveryService;
  @Autowired private ContainerService containerService;
  @Autowired private Gson gson;

  @Resource(name = RdcConstants.RDC_INSTRUCTION_SERVICE)
  private RdcInstructionService rdcInstructionService;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Resource(name = RdcConstants.RDC_OSDR_SERVICE)
  private RdcOsdrService rdcOsdrSummaryService;

  private Gson gsonWithDateAdapter;

  public RdcDockTagService() {
    gsonWithDateAdapter =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  @Transactional
  @InjectTenantFilter
  public DockTagResponse createDockTags(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    TenantContext.get().setCreateDockTagStart(System.currentTimeMillis());
    LOGGER.info("Creating docktags for the request {}", createDockTagRequest);
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String source =
        (headers.getFirst(ReceivingConstants.WMT_REQ_SOURCE) != null)
            ? headers.getFirst(ReceivingConstants.WMT_REQ_SOURCE)
            : DockTagType.ATLAS_RECEIVING.getText();
    validateTrailerNumberAndCarrierCode(createDockTagRequest);

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
      dockTag.setDockTagType(DockTagType.valueOf(source));
      dockTag.setDockTagStatus(InstructionStatus.CREATED);
      dockTags.add(dockTag);
    }
    dockTagPersisterService.saveAllDockTags(dockTags);

    TenantContext.get().setCreateDockTagGenerateLabelDataCallStart(System.currentTimeMillis());
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    boolean isNewDockTagLabelFormatEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);

    // Get Label Data
    Pair<PrintLabelData, Map<String, PrintLabelRequest>> printLabelDataPair =
        LabelGenerator.generateDockTagLabels(
            createDockTagRequest,
            dockTags,
            headers,
            ReceivingUtils.getDCDateTime(dcTimeZone),
            isNewDockTagLabelFormatEnabled);
    TenantContext.get().setCreateDockTagGenerateLabelDataCallEnd(System.currentTimeMillis());
    PrintLabelData labelData = printLabelDataPair.getKey();
    Map<String, PrintLabelRequest> dockTagPrintRequestMap = printLabelDataPair.getValue();

    // Create DockTag Instructions
    TenantContext.get().setCreateDockTagInstrCallStart(System.currentTimeMillis());
    Instruction baseInstruction = getDockTagInstruction(createDockTagRequest, headers);
    List<Instruction> docktagInstructions = new ArrayList<>();
    for (String dockTagId : dockTagIds) {
      Instruction dockTagInstruction = SerializationUtils.clone(baseInstruction);
      dockTagInstruction.setMessageId(dockTagId);
      dockTagInstruction.setDockTagId(dockTagId);
      dockTagInstruction.getContainer().setTrackingId(dockTagId);
      Map<String, Object> ctrLabel =
          getPrintLabelDataForReprint(headers, dockTagPrintRequestMap.get(dockTagId));
      dockTagInstruction.getContainer().setCtrLabel(ctrLabel);
      docktagInstructions.add(dockTagInstruction);
    }
    List<Instruction> docktagInstructions4mDB = instructionRepository.saveAll(docktagInstructions);
    TenantContext.get().setCreateDockTagInstrCallStart(System.currentTimeMillis());

    // Create DockTag Containers
    TenantContext.get().setCreateDockTagContainerCallStart(System.currentTimeMillis());
    List<Container> docktagContainers = new ArrayList<>();
    List<PrintJob> printJobs = new ArrayList<>();
    Container baseContainer = getDockTagContainer(createDockTagRequest, headers);

    /**
     * Freight type and delivery type information's are required when we print new dock tag for the
     * partial complete scenario. So we are persisting this information in container misc info for
     * later reference
     */
    if (isNewDockTagLabelFormatEnabled) {
      Map<String, Object> containerMiscInfo = new HashMap<>();
      if (StringUtils.isNotBlank(createDockTagRequest.getDeliveryTypeCode())
          && StringUtils.isNotBlank(createDockTagRequest.getFreightType())) {
        containerMiscInfo.put(
            ReceivingConstants.DELIVERY_TYPE_CODE, createDockTagRequest.getDeliveryTypeCode());
        containerMiscInfo.put(
            ReceivingConstants.FREIGHT_TYPE, createDockTagRequest.getFreightType());
        baseContainer.setContainerMiscInfo(containerMiscInfo);
      }
    }
    for (Instruction instruction4mDB : docktagInstructions4mDB) {
      Container dockTagContainer = SerializationUtils.clone(baseContainer);
      dockTagContainer.setTrackingId(instruction4mDB.getDockTagId());
      dockTagContainer.setMessageId(instruction4mDB.getDockTagId());
      dockTagContainer.setInstructionId(instruction4mDB.getId());
      docktagContainers.add(dockTagContainer);

      // Publish Container & Move
      rdcInstructionService.publishContainerAndMove(
          instruction4mDB.getDockTagId(), dockTagContainer, headers);

      PrintJob printJob = new PrintJob();
      printJob.setDeliveryNumber(instruction4mDB.getDeliveryNumber());
      printJob.setInstructionId(instruction4mDB.getId());
      printJob.setLabelIdentifier(new HashSet<>(Arrays.asList(instruction4mDB.getDockTagId())));
      printJob.setCreateUserId(userId);
      printJobs.add(printJob);
    }
    containerPersisterService.saveContainers(docktagContainers);
    TenantContext.get().setCreateDockTagContainerCallEnd(System.currentTimeMillis());

    // Persist Print Jobs
    printJobService.savePrintJobs(printJobs);

    // WFT Publish
    if (appConfig.isWftPublishEnabled()) {
      instructionHelperService.publishInstruction(
          headers, getPublishInstructionSummary(dockTags, headers));
    }

    LOGGER.info("Completed creating docktags for the request {}", createDockTagRequest);
    DockTagResponse dockTagResponse =
        DockTagResponse.builder()
            .deliveryNumber(createDockTagRequest.getDeliveryNumber())
            .dockTags(dockTagIds)
            .printData(labelData)
            .build();

    TenantContext.get().setCreateDockTagEnd(System.currentTimeMillis());
    calculateAndLogElapsedTimeSummary4CreateDockTag();
    return dockTagResponse;
  }

  private Map<String, Object> getPrintLabelDataForReprint(
      HttpHeaders httpHeaders, PrintLabelRequest printLabelRequest) {
    Map<String, Object> printdata = new HashMap<>();
    printdata.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printdata.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printdata.put(
        ReceivingConstants.PRINT_REQUEST_KEY, Collections.singletonList(printLabelRequest));
    return printdata;
  }

  /**
   * Prepare and returns create dock tag instruction
   *
   * @return instruction
   */
  public Instruction getDockTagInstruction(
      CreateDockTagRequest createDockTagRequest, HttpHeaders httpHeaders) {

    Instruction instruction = new Instruction();
    instruction.setInstructionCode(ReceivingConstants.DOCK_TAG);
    instruction.setInstructionMsg(ReceivingConstants.DOCKTAG_INSTRUCTION_MESSAGE);
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setContainer(getDockTagContainerDetails());
    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setDeliveryNumber(createDockTagRequest.getDeliveryNumber());
    instruction.setPurchaseReferenceNumber("");
    instruction.setPurchaseReferenceLineNumber(null);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setCreateUserId(userId);
    // mark it complete and save
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId(userId);
    return instruction;
  }

  private ContainerDetails getDockTagContainerDetails() {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setCtrType(ContainerType.PALLET.getText());
    containerDetails.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    return containerDetails;
  }

  public Container getDockTagContainer(
      CreateDockTagRequest createDockTagRequest, HttpHeaders httpHeaders) {
    Container container = new Container();
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    container.setCtrReusable(Boolean.FALSE);
    container.setCtrShippable(Boolean.FALSE);
    container.setInstructionId(DUMMY_INSTRUCTION_ID);
    container.setLocation(createDockTagRequest.getDoorNumber());
    container.setDeliveryNumber(createDockTagRequest.getDeliveryNumber());
    container.setContainerType(ContainerType.PALLET.getText());
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setIsConveyable(Boolean.FALSE);
    container.setOnConveyor(Boolean.FALSE);

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

  public void saveDockTags(List<DockTag> dockTags) {
    dockTagPersisterService.saveAllDockTags(dockTags);
  }

  @Override
  public Integer countOfOpenDockTags(Long deliveryNumber) {
    return countOfPendingDockTags(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public DockTagData completeDockTagById(String dockTagId, HttpHeaders httpHeaders) {
    TenantContext.get().setCompleteDockTagStart(System.currentTimeMillis());
    LOGGER.info("Entering complete dock tag for {}", dockTagId);
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
    try {
      markCompleteAndDeleteFromInventory(httpHeaders, dockTagFromDb);
    } catch (ApplicationBaseException applicationBaseException) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_COMPLETE_DOCKTAG,
          String.format(ReceivingConstants.UNABLE_TO_COMPLETE_DOCKTAG, dockTagId),
          dockTagId);
    }
    saveDockTag(dockTagFromDb);
    DockTagData dockTagData = getDockTagData(dockTagFromDb);
    TenantContext.get().setCreateDockTagEnd(System.currentTimeMillis());
    LOGGER.warn(
        "LatencyCheck CompleteDockTagById at ts={} time in totalTimeTakenForCompleteDockTagById={}, and correlationId={}",
        TenantContext.get().getCompleteDockTagStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteDockTagStart(),
            TenantContext.get().getCompleteDockTagEnd()),
        TenantContext.getCorrelationId());

    Integer totalDockTagsCount =
        dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(dockTagData.getDeliveryNumber());
    processDockTagCompleteMessage(
        dockTagData.getDeliveryNumber(),
        dockTagData.getDockTagId(),
        httpHeaders,
        totalDockTagsCount);
    return dockTagData;
  }

  public List<DockTagData> searchDockTag(
      Optional<String> dockTagId,
      Optional<Long> deliveryNumber,
      Optional<Long> fromDateTime,
      Optional<Long> toDateTime) {
    TenantContext.get().setSearchDockTagStart(System.currentTimeMillis());
    Date fromDate = null;
    Date toDate = null;
    if (fromDateTime.isPresent() && toDateTime.isPresent()) {
      String facilityNumber = TenantContext.getFacilityNum().toString();
      fromDate = new Date(fromDateTime.get());
      toDate = new Date(toDateTime.get());
      String timeZone = tenantSpecificReportConfig.getDCTimeZone(facilityNumber);
      fromDate = ReportingUtils.zonedDateTimeToUTC(fromDateTime.get(), timeZone);
      toDate = ReportingUtils.zonedDateTimeToUTC(toDateTime.get(), timeZone);
    }

    String dockTag = dockTagId.isPresent() ? dockTagId.get() : null;
    Long deliveryNum = deliveryNumber.isPresent() ? deliveryNumber.get() : null;

    LOGGER.info(
        "Querying for dockTags with dockTagId:{}, delivery number:{}, fromDate:{} and toDate:{}",
        dockTag,
        deliveryNum,
        fromDate,
        toDate);

    List<DockTagData> dockTagListFromDB =
        dockTagCustomRepository.searchDockTags(
            deliveryNum,
            dockTag,
            fromDate,
            toDate,
            TenantContext.getFacilityNum().toString(),
            TenantContext.getFacilityCountryCode());

    if (CollectionUtils.isEmpty(dockTagListFromDB)) {
      validateSearchedDockTagFromDb(
          null, null, ReceivingConstants.DATA_NOT_FOUND_FOR_THE_GIVEN_SEARCH_CRITERIA);
    }

    List<DockTagData> dockTagResult = new ArrayList<>();
    dockTagListFromDB.forEach(
        dt -> {
          validateSearchedDockTagFromDb(
              dt, dt.getDockTagId(), ReceivingConstants.DOCK_TAG_NOT_FOUND_RDC_MESSAGE);
          dockTagResult.add(dt);
        });
    TenantContext.get().setSearchDockTagEnd(System.currentTimeMillis());
    LOGGER.warn(
        "LatencyCheck SearchDockTagWithSearchParams at ts={} time in totalTimeTakenforSearchDockTag={}, and correlationId={}",
        TenantContext.get().getSearchDockTagStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getSearchDockTagStart(), TenantContext.get().getSearchDockTagEnd()),
        TenantContext.getCorrelationId());
    return dockTagResult;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public DockTagData receiveDockTag(String dockTagId, HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.get().setReceiveDockTagStart(System.currentTimeMillis());
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
    if (Objects.isNull(httpHeaders.get(ReceivingConstants.WMT_REQ_SOURCE))) {
      LOGGER.info("Receive dockTag request is received from Atlas app for dockTagId:{}", dockTagId);
      Map<String, Long> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, dockTagFromDb.getDeliveryNumber());
      String gdmBaseUri =
          appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_V2_URI_INCLUDE_DUMMYPO;
      URI gdmGetDeliveryUri =
          UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();
      String deliveryResponse = deliveryService.getDeliveryByURI(gdmGetDeliveryUri, httpHeaders);
      return getDockTagData(dockTagFromDb, deliveryResponse);
    }
    LOGGER.info("Receive dockTag request is received from NGR app for dockTagId:{}", dockTagId);
    DockTagData dockTagData = getDockTagData(dockTagFromDb);
    TenantContext.get().setReceiveDockTagEnd(System.currentTimeMillis());
    LOGGER.warn(
        "LatencyCheck ReceiveDockTag at ts={} time in totalTimeTakenforReceiveDockTag={}, and correlationId={}",
        TenantContext.get().getReceiveDockTagStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveDockTagStart(),
            TenantContext.get().getReceiveDockTagEnd()),
        TenantContext.getCorrelationId());
    return dockTagData;
  }

  private List<String> getLPNDockTagIds(
      CreateDockTagRequest createDockTagRequest, HttpHeaders headers) throws ReceivingException {
    TenantContext.get().setCreateDockTagFetchLpnsCallStart(System.currentTimeMillis());
    int count = createDockTagRequest.getCount() == null ? 1 : createDockTagRequest.getCount();
    List<String> lpns = lpnCacheService.getLPNSBasedOnTenant(count, headers);
    TenantContext.get().setCreateDockTagFetchLpnsCallEnd(System.currentTimeMillis());
    return lpns;
  }

  private PublishInstructionSummary getPublishInstructionSummary(
      List<DockTag> dockTags, HttpHeaders headers) {
    PublishInstructionSummary instructionSummary = new PublishInstructionSummary();
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String correlationID = headers.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    instructionSummary.setActivityName(WFTInstruction.DOCKTAG.getActivityName());
    instructionSummary.setMessageId(correlationID);
    instructionSummary.setInstructionCode(WFTInstruction.DOCKTAG.getCode());
    instructionSummary.setInstructionMsg(WFTInstruction.DOCKTAG.getMessage());
    instructionSummary.setInstructionExecutionTS(dockTags.get(0).getCreateTs());
    instructionSummary.setInstructionStatus(InstructionStatus.CREATED.getInstructionStatus());
    instructionSummary.setUpdatedQty(dockTags.size());
    instructionSummary.setUserInfo(new UserInfo(userId, null));
    String locationId = headers.getFirst(RdcConstants.WFT_LOCATION_ID);
    String locationType = headers.getFirst(RdcConstants.WFT_LOCATION_TYPE);
    String sccCode = headers.getFirst(RdcConstants.WFT_SCC_CODE);
    instructionSummary.setLocation(new Location(locationId, locationType, sccCode));

    return instructionSummary;
  }

  private void validateSearchedDockTagFromDb(
      DockTagData dockTagData, String dockTagId, String dtNotFoundMsg) {
    if (Objects.isNull(dockTagData)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DATA_NOT_FOUND_FOR_THE_GIVEN_SEARCH_CRITERIA, dtNotFoundMsg);
    } else {
      if (Objects.nonNull(dockTagData.getCompleteTs())) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_DOCK_TAG,
            String.format(
                ReceivingConstants.DOCK_TAG_ALREADY_RECEIVED_MESSAGE,
                dockTagId,
                dockTagData.getCompleteUserId()),
            dockTagId);
      }
    }
  }

  /*
   * This method validates for trailerNumber and carrierScacCode from the request
   * if either of the parameters are not available then will get the details
   * from delivery meta data & update createDockTagRequest
   *
   * @param createDockTagRequest
   */
  private void validateTrailerNumberAndCarrierCode(CreateDockTagRequest createDockTagRequest) {
    if (StringUtils.isBlank(RdcUtils.getStringValue(createDockTagRequest.getTrailerNumber()))
        || StringUtils.isBlank(createDockTagRequest.getCarrier())) {
      LOGGER.info(
          "Carrier or trailer number is not found in the createDockTagRequest for DeliveryNumber : {}",
          createDockTagRequest.getDeliveryNumber());
      DeliveryMetaData deliveryMetaData =
          fetchDeliveryMetaData(createDockTagRequest.getDeliveryNumber());
      if (Objects.nonNull(deliveryMetaData)) {
        createDockTagRequest.setTrailerNumber(deliveryMetaData.getTrailerNumber());
        createDockTagRequest.setCarrier(deliveryMetaData.getCarrierScacCode());
      }
    }
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public DeliveryMetaData fetchDeliveryMetaData(Long deliveryNumber) {
    List<DeliveryMetaData> deliveries =
        deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(
            RdcUtils.getStringValue(deliveryNumber));
    return CollectionUtils.isNotEmpty(deliveries) ? deliveries.get(0) : null;
  }

  private DockTagData getDockTagData(DockTag dockTag) {
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    return (Objects.nonNull(dockTag))
        ? DockTagData.builder()
            .dockTagId(dockTag.getDockTagId())
            .deliveryNumber(dockTag.getDeliveryNumber())
            .status(dockTag.getDockTagStatus())
            .scannedLocation(dockTag.getScannedLocation())
            .facilityNum(dockTag.getFacilityNum())
            .facilityCountryCode(dockTag.getFacilityCountryCode())
            .createUserId(dockTag.getCreateUserId())
            .createTs(
                Objects.nonNull(dockTag.getCreateTs()) ? dockTag.getCreateTs().getTime() : null)
            .lastChangedUserId(dockTag.getLastChangedUserId())
            .lastChangedTs(new Date().getTime())
            .completeUserId(dockTag.getCompleteUserId())
            .completeTs(
                Objects.nonNull(dockTag.getCompleteTs())
                    ? RdcUtils.getDateByDCTimezone(dcTimeZone, dockTag.getCompleteTs()).getTime()
                    : null)
            .build()
        : null;
  }

  private DockTagData getDockTagData(DockTag dockTag, String deliveryInfo) {
    return (Objects.nonNull(dockTag))
        ? DockTagData.builder()
            .dockTagId(dockTag.getDockTagId())
            .deliveryNumber(dockTag.getDeliveryNumber())
            .status(dockTag.getDockTagStatus())
            .scannedLocation(dockTag.getScannedLocation())
            .facilityNum(dockTag.getFacilityNum())
            .facilityCountryCode(dockTag.getFacilityCountryCode())
            .createUserId(dockTag.getCreateUserId())
            .createTs(
                Objects.nonNull(dockTag.getCreateTs()) ? dockTag.getCreateTs().getTime() : null)
            .lastChangedUserId(dockTag.getLastChangedUserId())
            .lastChangedTs(new Date().getTime())
            .completeUserId(dockTag.getCompleteUserId())
            .completeTs(
                Objects.nonNull(dockTag.getCompleteTs()) ? dockTag.getCompleteTs().getTime() : null)
            .deliveryInfo(deliveryInfo)
            .build()
        : null;
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
    TenantContext.get().setSearchDockTagStart(System.currentTimeMillis());
    List<String> deliveryNumbers = searchDockTagRequest.getDeliveryNumbers();
    validateDeliveryNumbers(deliveryNumbers);
    List<Long> longDeliveryNums = new ArrayList<>();
    for (String deliveryNumber : deliveryNumbers) {
      longDeliveryNums.add(Long.parseLong(deliveryNumber));
    }
    List<DockTag> dockTagResponse = null;
    if (org.springframework.util.StringUtils.isEmpty(status)) {
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
    String response =
        CollectionUtils.isEmpty(dockTagResponse)
            ? null
            : gsonWithDateAdapter.toJson(dockTagResponse);
    TenantContext.get().setSearchDockTagEnd(System.currentTimeMillis());
    LOGGER.warn(
        "LatencyCheck SearchDockTagWithSearchRequest at ts={} time in totalTimeTakenforSearchDockTag={}, and correlationId={}",
        TenantContext.get().getSearchDockTagStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getSearchDockTagStart(), TenantContext.get().getSearchDockTagEnd()),
        TenantContext.getCorrelationId());
    return response;
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

  @Override
  public CompleteDockTagResponse completeDockTags(
      CompleteDockTagRequest completeDockTagRequest, HttpHeaders headers) {
    List<String> docktagsList = completeDockTagRequest.getDocktags();
    List<DockTag> dockTagsFromDb = dockTagPersisterService.getDockTagsByDockTagIds(docktagsList);
    if (CollectionUtils.isEmpty(dockTagsFromDb)) {
      return CompleteDockTagResponse.builder().failed(docktagsList).build();
    }

    List<String> failedDockTagList = new ArrayList<>();
    List<String> successDockTagList = new ArrayList<>();

    if (dockTagsFromDb.size() != docktagsList.size()) {
      List<String> dockTagIds =
          dockTagsFromDb.stream().map(DockTag::getDockTagId).collect(Collectors.toList());
      for (String dockTag : docktagsList) {
        if (!dockTagIds.contains(dockTag)) {
          failedDockTagList.add(dockTag);
        }
      }
    }

    Integer totalDockTagsCount =
        dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(
            completeDockTagRequest.getDeliveryNumber());

    List<String> dockTagIds = new ArrayList<>();

    for (DockTag dockTag : dockTagsFromDb) {
      if (Objects.nonNull(dockTag.getCompleteTs())) {
        LOGGER.info("Docktag {} is already completed", dockTag);
        continue;
      }
      try {
        dockTagIds.add(dockTag.getDockTagId());
        markCompleteAndDeleteFromInventory(headers, dockTag);
        saveDockTag(dockTag);
        processDockTagCompleteMessage(
            completeDockTagRequest.getDeliveryNumber(),
            dockTag.getDockTagId(),
            headers,
            totalDockTagsCount);
      } catch (ApplicationBaseException e) {
        failedDockTagList.add(dockTag.getDockTagId());
      }
    }

    for (String dockTag : docktagsList) {
      if (!failedDockTagList.contains(dockTag)) successDockTagList.add(dockTag);
    }
    return CompleteDockTagResponse.builder()
        .failed(failedDockTagList)
        .success(successDockTagList)
        .deliveryNumer(completeDockTagRequest.getDeliveryNumber())
        .build();
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
    LOGGER.warn("Cannot receive non con dock tag. No implementation for dock tag in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public UniversalInstructionResponse receiveUniversalTag(
      String dockTagId, String doorNumber, HttpHeaders httpHeaders) {
    LOGGER.warn("Cannot receive universal tag. No implementation for lpn in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  protected void markCompleteAndDeleteFromInventory(HttpHeaders headers, DockTag dockTag) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
        false)) {
      LOGGER.info(
          "Inventory integration is enabled, making a request to delete docktag:{} from inventory DB",
          dockTag.getDockTagId());
      headers = getForwardableHttpHeadersWithRequestOriginator(headers);

      inventoryService.deleteContainer(dockTag.getDockTagId(), headers);
    }
    // mark docktag status as complete and update completeTs & completeUserId.
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteUserId(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    dockTag.setCompleteTs(new Date());
  }

  @Override
  public String completeDockTag(String dockTagId, HttpHeaders httpHeaders) {
    return gson.toJson(completeDockTagById(dockTagId, httpHeaders));
  }

  private void calculateAndLogElapsedTimeSummary4CreateDockTag() {

    long timeTakenForCreateDTPublishContainerAndMove =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateDTPublishContainerAndMoveCallStart(),
            TenantContext.get().getCreateDTPublishContainerAndMoveCallEnd());

    long timeTakenForCreateDockTagFetchLpnsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateDockTagFetchLpnsCallStart(),
            TenantContext.get().getCreateDockTagFetchLpnsCallEnd());

    long timeTakenForCreateDockTagGenerateLabelDataCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateDockTagGenerateLabelDataCallStart(),
            TenantContext.get().getCreateDockTagGenerateLabelDataCallEnd());

    long timeTakenForCreateDockTagInstrCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateDockTagInstrCallStart(),
            TenantContext.get().getCreateDockTagInstrCallEnd());

    long timeTakenForCreateDockTagContainerCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateDockTagContainerCallStart(),
            TenantContext.get().getCreateDockTagContainerCallEnd());

    long totalTimeTakenforCreateDockTag =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateDockTagStart(), TenantContext.get().getCreateDockTagEnd());

    LOGGER.warn(
        "LatencyCheck CreateDockTag at ts={} time in timeTakenForCreateDTPublishContainerAndMove={}, "
            + "timeTakenForCreateDockTagFetchLpnsCall={}, timeTakenForCreateDockTagGenerateLabelDataCall={}, "
            + "timeTakenForCreateDockTagInstrCall={}, timeTakenForCreateDockTagContainerCall={}, totalTimeTakenforCreateDockTag={}, and correlationId={}",
        TenantContext.get().getCreateDockTagStart(),
        timeTakenForCreateDTPublishContainerAndMove,
        timeTakenForCreateDockTagFetchLpnsCall,
        timeTakenForCreateDockTagGenerateLabelDataCall,
        timeTakenForCreateDockTagInstrCall,
        timeTakenForCreateDockTagContainerCall,
        totalTimeTakenforCreateDockTag,
        TenantContext.getCorrelationId());
  }

  private void processDockTagCompleteMessage(
      Long deliveryNumber, String dockTagId, HttpHeaders headers, Integer totalDockTagsCount) {
    LOGGER.info("Publishing complete docktag status update for delivery {}", deliveryNumber);
    Integer countOfOpenDockTags = countOfOpenDockTags(deliveryNumber);
    Map<String, Object> deliveryStatusMessageHeaders =
        RdcDeliveryStatusUtils.getDeliveryStatusMessageHeaders(headers, deliveryNumber);

    if (countOfOpenDockTags.equals(0)) {
      try {
        OsdrSummary osdrSummaryResponse =
            rdcOsdrSummaryService.getOsdrSummary(deliveryNumber, headers);
        rdcMessagePublisher.publishDeliveryReceipts(
            osdrSummaryResponse, deliveryStatusMessageHeaders);
      } catch (ReceivingException receivingException) {
        LOGGER.error(
            "Unable to get OSDRSummary for delivery:{}; error:{}",
            deliveryNumber,
            ExceptionUtils.getStackTrace(receivingException));
      }
    }

    DeliveryInfo deliveryStatusMessage =
        RdcDeliveryStatusUtils.getDockTagDeliveryInfo(deliveryNumber, dockTagId);
    deliveryStatusMessage.setTagCount(totalDockTagsCount);
    deliveryStatusMessage.setRemainingTags(countOfOpenDockTags);
    rdcMessagePublisher.publishDeliveryStatus(deliveryStatusMessage, deliveryStatusMessageHeaders);
  }

  public void autoCompleteDocks(int dockTagAutoCompleteHours, int pageSize) {
    JsonElement autoDockTagCompleteHours =
        tenantSpecificConfigReader.getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.AUTO_DOCK_TAG_COMPLETE_HOURS);
    dockTagAutoCompleteHours =
        Objects.nonNull(autoDockTagCompleteHours)
            ? autoDockTagCompleteHours.getAsInt()
            : ReceivingConstants.DEFAULT_AUTO_DOCK_TAG_COMPLETE_HOURS;
    Calendar cal = Calendar.getInstance();
    LOGGER.info(
        "AutoCompleteDockTag: Current time {}, dockTagAutoCompleteHours: {} ,for facilityNumber: {}",
        cal.getTime(),
        dockTagAutoCompleteHours,
        TenantContext.getFacilityNum().toString());
    cal.add(Calendar.HOUR, -dockTagAutoCompleteHours);
    Date fromDate = cal.getTime();
    HttpHeaders headers = ReceivingUtils.getHeaders();
    LOGGER.info("AutoCompleteDockTag: Before {}", fromDate);

    List<DockTag> dockTags =
        dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            ReceivingUtils.getPendingDockTagStatus(), fromDate, PageRequest.of(0, pageSize));

    if (org.springframework.util.CollectionUtils.isEmpty(dockTags)) {
      LOGGER.info(
          "No open dockTags to complete as of {}, currentTime for facilityNumber: {} Returning",
          cal.getTime(),
          headers.getFirst(TENENT_FACLITYNUM));
      return;
    }
    LOGGER.info("AutoCompleteDockTag: No.of dockTags {}", dockTags.size());
    for (DockTag docktag : dockTags) {
      try {
        completeDockTagById(docktag.getDockTagId(), headers);
      } catch (Exception e) {
        LOGGER.error(
            "Failed to complete dockTag: {} for facilityNumber: {} with exception: {},continuing for other dockTags",
            docktag.getDockTagId(),
            headers.getFirst(TENENT_FACLITYNUM),
            ExceptionUtils.getStackTrace(e));
      }
    }
  }

  /**
   * When user completes a dock tag in workstation location, he/she can choose an option like
   * "pallet empty" or "partial". If option is "pallet empty" then client will be invoking complete
   * dock tag api. If option is "partial" then client will be sending a flag "retry" with the value
   * TRUE or FALSE. If flag value is TRUE then this api will create a new dock tag, otherwise this
   * api will complete the given dock tag and creates a new dock tag.
   *
   * @param createDockTagRequest
   * @param dockTagId
   * @param isRetryCompleteFlag
   * @param httpHeaders
   * @return DockTagResponse
   * @throws ReceivingException
   */
  @Override
  @Transactional
  @InjectTenantFilter
  public DockTagResponse partialCompleteDockTag(
      CreateDockTagRequest createDockTagRequest,
      String dockTagId,
      boolean isRetryCompleteFlag,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.get().setPartialCompleteDockTagStart(System.currentTimeMillis());
    LOGGER.info(
        "Inside partialCompleteDockTag for dockTag: {} and isRetryCompleteFlag: {}",
        dockTagId,
        isRetryCompleteFlag);
    DockTagResponse dockTagResponse = null;
    DockTag dockTagFromDB = null;
    try {
      dockTagFromDB = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
      if (!isRetryCompleteFlag) {
        validateDockTagFromDb(
            dockTagFromDB, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
        markCompleteAndDeleteFromInventory(httpHeaders, dockTagFromDB);
        saveDockTag(dockTagFromDB);
      }
      populateFreightTypeAndDeliveryType(createDockTagRequest, dockTagId);
      dockTagResponse = createDockTags(createDockTagRequest, httpHeaders);
      Integer totalDockTagsCount =
          dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(
              dockTagFromDB.getDeliveryNumber());
      processDockTagCompleteMessage(
          dockTagFromDB.getDeliveryNumber(),
          dockTagFromDB.getDockTagId(),
          httpHeaders,
          totalDockTagsCount);
    } catch (ReceivingInternalException rie) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          rie.getDescription());
      if (rie.getErrorCode().equals(ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY)) {
        throw new ReceivingInternalException(
            ExceptionCodes.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR,
            String.format(ReceivingConstants.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR_MSG, dockTagId),
            dockTagId);
      }
    } catch (ReceivingDataNotFoundException rdnfe) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          rdnfe.getDescription());
      if (rdnfe.getErrorCode().equals(ExceptionCodes.DOCK_TAG_NOT_FOUND)) {
        throw new ReceivingInternalException(
            rdnfe.getErrorCode(),
            String.format(ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, dockTagId),
            dockTagId);
      }
      if (rdnfe.getErrorCode().equals(ExceptionCodes.INVENTORY_NOT_FOUND)) {
        throw new ReceivingInternalException(
            ExceptionCodes.INVENTORY_NOT_FOUND,
            String.format(ReceivingConstants.INVENTORY_NOT_FOUND_MESSAGE, dockTagId),
            dockTagId);
      }
    } catch (ReceivingBadDataException rbde) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          rbde.getDescription());
      if (ExceptionCodes.DOCKTAG_ALREADY_COMPLETED_ERROR.equals(rbde.getErrorCode())) {
        throw new ReceivingInternalException(
            rbde.getErrorCode(), rbde.getMessage(), rbde.getValues());
      }
    } catch (ReceivingException re) {
      LOGGER.error(
          "Error in partialCompleteDockTag() for docktag: {}, reason: {}",
          dockTagId,
          re.getErrorResponse().getErrorInfo().toString());
      throw new ReceivingInternalException(
          ExceptionCodes.PARTIAL_DOCKTAG_CREATION_ERROR,
          String.format(
              ReceivingConstants.PARTIAL_DOCKTAG_CREATION_ERROR_MSG,
              dockTagFromDB.getDockTagId(),
              dockTagFromDB.getDeliveryNumber().toString()),
          dockTagFromDB.getDockTagId(),
          dockTagFromDB.getDeliveryNumber().toString());
    }

    TenantContext.get().setPartialCompleteDockTagEnd(System.currentTimeMillis());
    LOGGER.warn(
        "LatencyCheck partialCompleteDockTag at ts={} time in totalTimeTakenForPartialCompleteDockTag={}, and correlationId={}",
        TenantContext.get().getPartialCompleteDockTagStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getPartialCompleteDockTagStart(),
            TenantContext.get().getPartialCompleteDockTagEnd()),
        TenantContext.getCorrelationId());
    return dockTagResponse;
  }

  private void populateFreightTypeAndDeliveryType(
      CreateDockTagRequest createDockTagRequest, String dockTagId) {
    if (StringUtils.isBlank(createDockTagRequest.getFreightType())
        || StringUtils.isBlank(createDockTagRequest.getDeliveryTypeCode())) {
      Container container = containerService.findByTrackingId(dockTagId);
      if (Objects.nonNull(container) && Objects.nonNull(container.getContainerMiscInfo())) {
        String freightType =
            StringUtils.isBlank(createDockTagRequest.getFreightType())
                ? String.valueOf(
                    container.getContainerMiscInfo().get(ReceivingConstants.FREIGHT_TYPE))
                : createDockTagRequest.getFreightType();

        String deliveryTypeCode =
            StringUtils.isBlank(createDockTagRequest.getDeliveryTypeCode())
                ? String.valueOf(
                    container.getContainerMiscInfo().get(ReceivingConstants.DELIVERY_TYPE_CODE))
                : createDockTagRequest.getDeliveryTypeCode();

        createDockTagRequest.setFreightType(freightType);
        createDockTagRequest.setDeliveryTypeCode(deliveryTypeCode);
      }
    }
  }

  @Override
  public void updateDockTagStatusAndPublish(
      ReceiveDockTagRequest receiveDockTagRequest, DockTag dockTagFromDb, HttpHeaders httpHeaders) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }
}
