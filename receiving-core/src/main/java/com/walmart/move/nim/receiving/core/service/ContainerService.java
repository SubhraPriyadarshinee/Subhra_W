package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.*;
import static com.walmart.move.nim.receiving.core.common.InstructionUtils.isNationalPO;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isOssTransfer;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.validateTrackingId;
import static com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOReasonCode.RECEIVING_CORRECTION;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.*;
import com.google.gson.JsonArray;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.*;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaHawkshawPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.*;
import com.walmart.move.nim.receiving.core.model.printlabel.GdcReprintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.v2.CreateContainerProcessor;
import com.walmart.move.nim.receiving.core.service.v2.DefaultCreateContainerProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.logging.log4j2.util.Strings;
import io.strati.metrics.annotation.ExceptionCounted;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.swing.text.html.Option;

/** @author pcr000m */
@Service(DEFAULT_CONTAINER_SERVICE)
@AllArgsConstructor
public class ContainerService extends AbstractContainerService {
  private static final Logger log = LoggerFactory.getLogger(ContainerService.class);
  public static final String ABORT_CALL_FOR_KAFKA_ERR = "abortCallForKafkaErr";

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private OsdrConfig osdrConfig;
  @ManagedConfiguration private MaasTopics maasTopics;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired ContainerRepository containerRepository;

  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ReceiptService receiptService;

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Autowired private JmsPublisher jmsPublisher;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private OSDRRecordCountAggregator osdrRecordCountAggregator;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private GDMRestApiClient gdmRestApiClient;
  @Autowired private OSDRCalculator osdrCalculator;
  @Autowired private RestUtils restUtils;
  @Autowired private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;
  @Autowired private DeliveryServiceRetryableImpl deliveryServiceRetryableImpl;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired private InventoryService inventoryService;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private AsyncPersister asyncPersister;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;
  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;

  @ManagedConfiguration KafkaConfig kafkaConfig;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  @Autowired private MoveRestApiClient moveRestApiClient;
  @Autowired @Lazy private ItemConfigApiClient itemConfigApiClient;
  @Autowired private KafkaHawkshawPublisher kafkaHawkshawPublisher;
  @Autowired private PrintLabelHelper printLabelHelper;

  /**
   * A Topic should be already created in the cluster then provided in kafkatopic config ccm. For
   * GDC pattern for the topic is ATLAS_RECEIVE_RECEIPTS_<env> Eg: ATLAS_RECEIVE_RECEIPTS_STG_INT.
   */
  @Value("${container.receiving.receipt}")
  private String inventMultiCtnrTopic;

  @Value("${publish.docktag.container.topic}")
  private String createDockTagTopic;

  private final Gson gson;

  private final JsonParser parser = new JsonParser();

  public ContainerService() {
    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  /**
   * This is used to return the printJob objects that are needed for client to print labels. Since
   * we are not storing this in our db like our old code, we will be passing along the print job
   * information that we have gotten from Order Filling.
   *
   * @param childContainers
   * @param receivedQuantity
   * @param quantityToBeReceived
   * @return
   */
  public Map<String, Object> getCreatedChildContainerLabels(
      List<ContainerDetails> childContainers,
      Integer receivedQuantity,
      Integer quantityToBeReceived) {
    Map<String, Object> labelData = new HashMap<>();
    List<Map<String, Object>> createdContainerLabelList = new ArrayList<>();
    labelData.put(
        ReceivingConstants.PRINT_HEADERS_KEY,
        childContainers
            .get(receivedQuantity)
            .getCtrLabel()
            .get(ReceivingConstants.PRINT_HEADERS_KEY));
    labelData.put(
        ReceivingConstants.PRINT_CLIENT_ID_KEY,
        childContainers
            .get(receivedQuantity)
            .getCtrLabel()
            .get(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    List<Map<String, Object>> printRequests;
    for (int i = 0; i < quantityToBeReceived; i++) {
      printRequests =
          (List)
              childContainers
                  .get(receivedQuantity++)
                  .getCtrLabel()
                  .get(ReceivingConstants.PRINT_REQUEST_KEY);
      createdContainerLabelList.add(printRequests.get(0));
    }
    labelData.put(ReceivingConstants.PRINT_REQUEST_KEY, createdContainerLabelList);
    return labelData;
  }

  /**
   * This is used to return the printJob objects that are needed for client to print labels. Since
   * we are not storing this in our db like our old code, we will be passing along the print job
   * information that we have gotten from Order Filling.
   *
   * @param childContainers
   * @param receivedQuantity
   * @param quantityToBeReceived
   * @return
   */
  public List<Map<String, Object>> getOldFormatCreatedChildContainerLabels(
      List<ContainerDetails> childContainers,
      Integer receivedQuantity,
      Integer quantityToBeReceived) {
    List<Map<String, Object>> createdContainerLabelList = new ArrayList<>();
    for (int i = 0; i < quantityToBeReceived; i++) {
      createdContainerLabelList.add(childContainers.get(receivedQuantity++).getCtrLabel());
    }
    return createdContainerLabelList;
  }

  /**
   * This is used to retrieve the tracking id's for all child containers being received. It will
   * start receiving based on the amount being received. (receivedQuantity)
   *
   * @param childContainers
   * @param receivedQuantity
   * @param quantityToBeReceived
   * @return
   */
  public List<String> getCreatedChildContainerTrackingIds(
      List<ContainerDetails> childContainers,
      Integer receivedQuantity,
      Integer quantityToBeReceived) {
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < quantityToBeReceived; i++) {
      labels.add(childContainers.get(receivedQuantity++).getTrackingId());
    }
    return labels;
  }

  /**
   * This will create parent container if it doesn't exists and add childs to it if it has any
   * childs. If parent already exists, just add all childs to it.
   *
   * @param instruction
   * @param instructionRequest
   * @param httpHeaders
   * @throws Exception
   */
  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public void processCreateContainers(
      Instruction instruction, UpdateInstructionRequest instructionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (!containerPersisterService.checkIfContainerExist(
        instruction.getContainer().getTrackingId())) {
      constructParentContainer(instruction, instructionRequest);
      if (instruction.getChildContainers() != null && instruction.getLabels() != null) {
        checkForPendingChildContainerCreation(
            instruction,
            instruction.getLabels().getAvailableLabels().size(),
            instruction.getContainer().getTrackingId(),
            instructionRequest);
      }
    } else {
      if (instruction.getChildContainers() != null && instruction.getLabels() != null) {
        checkForPendingChildContainerCreation(
            instruction,
            instruction.getLabels().getAvailableLabels().size(),
            instruction.getContainer().getTrackingId(),
            instructionRequest);
      } else {

        DocumentLine containerItemDetail = instructionRequest.getDeliveryDocumentLines().get(0);
        updateContainerItem(
            instruction, containerItemDetail, httpHeaders.get(USER_ID_HEADER_KEY).get(0));
      }
    }
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public void processCreateChildContainers(
      Instruction instruction,
      UpdateInstructionRequest instructionRequest,
      ContainerDetails childContainerDetails,
      ContainerDetails scannedContainer)
      throws ReceivingException {
    if (!containerPersisterService.checkIfContainerExist(
        instruction.getContainer().getTrackingId())) {
      constructParentContainer(instruction, instructionRequest, Boolean.TRUE);
    }
    if (!StringUtils.isEmpty(childContainerDetails.getTrackingId())
        && !containerPersisterService.checkIfContainerExist(
            childContainerDetails.getTrackingId())) {
      checkForPendingChildContainerCreation(
          instruction,
          instruction.getContainer().getTrackingId(),
          instructionRequest,
          Arrays.asList(childContainerDetails),
          Boolean.FALSE);
    }
    checkForPendingChildContainerCreation(
        instruction,
        scannedContainer.getParentTrackingId(),
        instructionRequest,
        Arrays.asList(scannedContainer),
        Boolean.FALSE);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Integer receivedContainerQuantityByTrackingId(String trackingId) {
    return containerItemRepository.receivedContainerQuantityByTrackingId(trackingId);
  }

  /**
   * This will extract the container to be created from an instruction then it will construct the
   * parent ContainerModel.
   *
   * @param instruction
   * @param instructionRequest
   * @return
   */
  private Container constructParentContainer(
      Instruction instruction, UpdateInstructionRequest instructionRequest)
      throws ReceivingException {
    boolean hasChild = (instruction.getChildContainers() != null);
    Container container =
        constructContainer(
            instruction, instruction.getContainer(), Boolean.TRUE, hasChild, instructionRequest);
    // Map weight properties from GLS receive for Manual GDC
    mapWeightForManualGDC(container, instruction);
    return containerPersisterService.saveContainer(container);
  }

  public Container constructContainerList(
      Instruction instruction, UpdateInstructionRequest instructionRequest)
      throws ReceivingException {
    boolean hasChild = (instruction.getChildContainers() != null);
    Container container =
        constructContainer(
            instruction, instruction.getContainer(), Boolean.TRUE, hasChild, instructionRequest);
    // Map weight properties from GLS receive for Manual GDC
    mapWeightForManualGDC(container, instruction);
    return container;
  }

  private Container mapWeightForManualGDC(Container container, Instruction instruction) {
    boolean isManualGDC =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    boolean isOneAtlas =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    DeliveryDocument document =
        new Gson().fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

    // Only for full gls items weight will be mapped
    if ((isManualGDC && !isOneAtlas)
        || (isOneAtlas
            && !deliveryDocumentHelper.isAtlasConvertedItemInFirstDocFirstLine(
                Arrays.asList(document)))) {
      container.setWeight(new Float(instruction.getContainer().getGlsWeight()));
      container.setWeightUOM(instruction.getContainer().getGlsWeightUOM());
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
      try {
        Date date = formatter.parse(instruction.getContainer().getGlsTimestamp());
        container.setCreateTs(date);
      } catch (ParseException e) {
        log.error(
            "Unable to parse gls timestamp. ts = {}", instruction.getContainer().getGlsTimestamp());
        container.setCreateTs(new Date());
      }
    }
    return container;
  }

  private Container constructParentContainer(
      Instruction instruction, UpdateInstructionRequest instructionRequest, Boolean hasChild)
      throws ReceivingException {
    Container container =
        constructContainer(
            instruction, instruction.getContainer(), Boolean.TRUE, hasChild, instructionRequest);
    return containerPersisterService.saveContainer(container);
  }

  /**
   * This will extract the container to be created from an instruction then it will construct the
   * parent ContainerModel.
   *
   * @param instruction
   * @param instructionRequest
   * @param onConveyor
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public Container createAndCompleteParentContainer(
      Instruction instruction, UpdateInstructionRequest instructionRequest, boolean onConveyor)
      throws ReceivingException {
    Container container =
        constructContainer(
            instruction,
            instruction.getContainer(),
            Boolean.TRUE,
            Boolean.FALSE,
            instructionRequest);
    container.setOnConveyor(onConveyor);
    setDistributionAndComplete(instruction.getCreateUserId(), container);
    container.setLastChangedTs(new Date());
    return containerPersisterService.saveContainer(container);
  }

  /**
   * This method will check pending child containers to created
   *
   * @param instruction
   * @param availableChildContainerCount
   * @param parentTrackingId
   * @param instructionRequest
   * @throws ReceivingException
   */
  private void checkForPendingChildContainerCreation(
      Instruction instruction,
      Integer availableChildContainerCount,
      String parentTrackingId,
      UpdateInstructionRequest instructionRequest)
      throws ReceivingException {
    if (instructionRequest.getDeliveryDocumentLines().get(0).getQuantity()
        > availableChildContainerCount) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.CONTAINER_EXCEEDS_QUANTITY)
              .errorKey(ExceptionCodes.CONTAINER_EXCEEDS_QUANTITY)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
    constructChildContainer(instruction, parentTrackingId, instructionRequest);
  }

  /**
   * This method will check pending child containers to created
   *
   * @param instruction
   * @param parentTrackingId
   * @param instructionRequest
   * @throws ReceivingException
   */
  private void checkForPendingChildContainerCreation(
      Instruction instruction,
      String parentTrackingId,
      UpdateInstructionRequest instructionRequest,
      List<ContainerDetails> childContainerDtls,
      Boolean isParent)
      throws ReceivingException {
    constructChildParentContainer(
        instruction, parentTrackingId, instructionRequest, childContainerDtls, isParent);
  }

  private void constructChildParentContainer(
      Instruction instruction,
      String parentTrackingId,
      UpdateInstructionRequest instructionRequest,
      List<ContainerDetails> childContainerDtls,
      Boolean isParent)
      throws ReceivingException {
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    boolean hasChild = (instruction.getChildContainers() != null);
    for (ContainerDetails childContainerDetails : childContainerDtls) {
      Container container =
          constructContainer(
              instruction, childContainerDetails, isParent, hasChild, instructionRequest);
      container.setParentTrackingId(parentTrackingId);
      if (!CollectionUtils.isEmpty(container.getContainerItems())) {
        List<ContainerItem> containerDetails = container.getContainerItems();
        populateReceivedQty(containerDetails, instructionRequest);
        containerItems.addAll(containerDetails);
      }
      containers.add(container);
    }
    containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
  }

  private void populateReceivedQty(
      List<ContainerItem> containerDetails, UpdateInstructionRequest instructionRequest) {
    DocumentLine item = instructionRequest.getDeliveryDocumentLines().get(0);
    containerDetails.forEach(
        containerItem -> {
          if (Uom.EACHES.equalsIgnoreCase(item.getQuantityUOM())) {
            containerItem.setQuantity(item.getQuantity());
          }
        });
  }

  /**
   * This will extract the containers to be created from an instruction then it will construct the
   * child containers.
   *
   * @param instruction
   * @param parentTrackingId
   * @param instructionRequest
   */
  private void constructChildContainer(
      Instruction instruction, String parentTrackingId, UpdateInstructionRequest instructionRequest)
      throws ReceivingException {
    DocumentLine containerItemDetail = instructionRequest.getDeliveryDocumentLines().get(0);
    List<ContainerDetails> childContainerDtls =
        instruction
            .getChildContainers()
            .subList(
                instruction.getReceivedQuantity(),
                instruction.getReceivedQuantity() + containerItemDetail.getQuantity());
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    for (ContainerDetails childContainerDetails : childContainerDtls) {
      Container container =
          constructContainer(
              instruction, childContainerDetails, Boolean.FALSE, Boolean.FALSE, instructionRequest);
      container.setParentTrackingId(parentTrackingId);
      if (!CollectionUtils.isEmpty(container.getContainerItems())) {
        containerItems.addAll(container.getContainerItems());
      }
      containers.add(container);
    }
    containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
  }

  private void setDoorForPbylDockTagReceiving(UpdateInstructionRequest updateInstructionRequest) {
    if (Objects.nonNull(updateInstructionRequest)
        && !StringUtils.isEmpty(updateInstructionRequest.getPbylLocation())) {
      log.info("Setting door for pbyl dock tag {}", updateInstructionRequest.getPbylLocation());
      updateInstructionRequest.setDoorNumber(updateInstructionRequest.getPbylLocation());
      return;
    }

    if (Objects.nonNull(updateInstructionRequest)
        && !StringUtils.isEmpty(updateInstructionRequest.getPbylDockTagId())) {
      DockTagService dockTagService =
          configUtils.getConfiguredInstance(
              getFacilityNum().toString(),
              ReceivingConstants.DOCK_TAG_SERVICE,
              DockTagService.class);
      DockTag dockTagById =
          dockTagService.getDockTagById(updateInstructionRequest.getPbylDockTagId());
      if (Objects.nonNull(dockTagById) && !StringUtils.isEmpty(dockTagById.getScannedLocation())) {
        log.info("Setting door for pbyl dock tag {}", dockTagById.getScannedLocation());
        updateInstructionRequest.setDoorNumber(dockTagById.getScannedLocation());
      }
    }
  }

  /**
   * This method will create container object for both parent and child Containers
   *
   * @param instruction
   * @param containerDtls
   * @param isParent
   * @param hasChildren
   * @param updateInstructionRequest
   * @return ContainerModel
   * @throws ReceivingException
   */
  public Container constructContainer( // NOSONAR
      Instruction instruction,
      ContainerDetails containerDtls,
      Boolean isParent,
      Boolean hasChildren,
      UpdateInstructionRequest updateInstructionRequest)
      throws ReceivingException {
    DeliveryDocument documentLine =
        new Gson().fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    boolean isNationalPo =
        isNationalPO(
            updateInstructionRequest.getDeliveryDocumentLines().get(0).getPurchaseRefType());

    Container container = new Container();
    Map<String, Object> containerMiscInfo =
        containerDtls.getContainerMiscInfo() == null
            ? new HashMap<String, Object>()
            : containerDtls.getContainerMiscInfo();
    if (!isNationalPo) {
      int receivedQty = updateInstructionRequest.getDeliveryDocumentLines().get(0).getQuantity();
      container.setCube(
          documentLine.getCubeQty() / documentLine.getTotalPurchaseReferenceQty() * receivedQty);
      container.setCubeUOM(documentLine.getCubeUOM());
      container.setWeight(
          documentLine.getWeight() / documentLine.getTotalPurchaseReferenceQty() * receivedQty);
      container.setWeightUOM(documentLine.getWeightUOM());
        if (Objects.nonNull(instruction.getOriginalChannel())) {
            Optional.ofNullable(instruction.getOriginalChannel())
          .ifPresent(
              x ->
                  containerMiscInfo.put(
                      LabelDataConstants.ORIGINAL_CHANNEL, instruction.getOriginalChannel()));
      Optional.ofNullable(updateInstructionRequest.getFacility())
          .ifPresent(
              x ->
                  containerMiscInfo.put(
                      ORIGIN_DC_NBR, updateInstructionRequest.getFacility().get(BU_NUMBER)));
      if (Objects.nonNull(documentLine.getDeliveryDocumentLines())
          && Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0))
          && Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0).getAdditionalInfo())) {
        containerMiscInfo.put(
            ReceivingConstants.DOCUMENT_ID,
            documentLine.getDeliveryDocumentLines().get(0).getAdditionalInfo().getDocumentId());
        containerMiscInfo.put(
            ReceivingConstants.SHIPMENT_NUMBER,
            documentLine.getDeliveryDocumentLines().get(0).getAdditionalInfo().getShipmentNumber());
      }
        if (Objects.nonNull(documentLine.getDeliveryDocumentLines())
                && Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0))
                && Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0).getAdditionalInfo())) {
          containerMiscInfo.put(
                  ReceivingConstants.DOCUMENT_ID,
                  documentLine.getDeliveryDocumentLines().get(0).getAdditionalInfo().getDocumentId());
          containerMiscInfo.put(
                  ReceivingConstants.SHIPMENT_NUMBER,
                  documentLine.getDeliveryDocumentLines().get(0).getAdditionalInfo().getShipmentNumber());
        }
      }
      container.setContainerMiscInfo(containerMiscInfo);
    }

    Integer orgUnitId;
    if (FLOW_RECEIVE_INTO_OSS.equalsIgnoreCase(updateInstructionRequest.getFlowDescriptor())
        && instruction.getOrgUnitId() != null) {
      orgUnitId = instruction.getOrgUnitId();
      containerMiscInfo.put(FLOW_DESCRIPTOR, FLOW_RECEIVE_INTO_OSS);
    } else {
      orgUnitId =
          isNotBlank(configUtils.getOrgUnitId())
              ? Integer.valueOf(configUtils.getOrgUnitId())
              : null;
    }
    container.setContainerMiscInfo(containerMiscInfo);

    container.setMessageId(instruction.getMessageId());
    container.setInventoryStatus(containerDtls.getInventoryStatus());
    container.setCtrReusable(containerDtls.getCtrReusable());
    container.setCtrShippable(containerDtls.getCtrShippable());
    container.setTrackingId(containerDtls.getTrackingId());
    container.setInstructionId(instruction.getId());
    container.setActivityName(instruction.getActivityName());
    // set door number for pbyl dock tag receiving
    setDoorForPbylDockTagReceiving(updateInstructionRequest);
    container.setLocation(updateInstructionRequest.getDoorNumber());

    if (nonNull(orgUnitId)) container.setSubcenterId(orgUnitId);
    /*
     * The source for containerType is pallet instruction from OF and UpdateInstructionRequest payload.
     * The priority is to use containerType received from client.
     */
    String containerType =
        updateInstructionRequest.getContainerType() != null
                && !StringUtils.isEmpty(updateInstructionRequest.getContainerType())
            ? updateInstructionRequest.getContainerType()
            : containerDtls.getCtrType();
    container.setContainerType(containerType);
    container.setContainerStatus(containerDtls.getCtrStatus());
    container.setDeliveryNumber(instruction.getDeliveryNumber());
    container.setFacility(updateInstructionRequest.getFacility());
    container.setDestination(containerDtls.getCtrDestination());
    container.setCreateTs(getContainerCreateTs());
    container.setCreateUser(getUserId(instruction));

    if (ReceivingConstants.VENDOR_PACK.equalsIgnoreCase(containerType)) {
      container.setLabelId(getLabelId(instruction.getActivityName(), containerType));
    }

    // Check for container is parent or not and does it contain container or not
    if (isParent && hasChildren) {
      container.setContainerItems(null);
      return container;
    }

    // FIX for MCC_GO_LIVE
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);
    final DeliveryDocument deliveryDocument = InstructionUtils.getDeliveryDocument(instruction);

    container.setIsConveyable(deliveryDocumentLine.getIsConveyable());
    float containerWeight = 0f;
    int totalReceivedQtyInVnpk = 0;
    boolean variableWeightItemExists = false;

    List<ContainerItem> containerItems = new ArrayList<>();
    for (DocumentLine item : updateInstructionRequest.getDeliveryDocumentLines()) {
      ContainerItem containerItem = new ContainerItem();
      containerItem.setTrackingId(containerDtls.getTrackingId());
      containerItem.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
      containerItem.setInboundChannelMethod(deliveryDocumentLine.getPurchaseRefType());
      containerItem.setOutboundChannelMethod(instruction.getContainer().getOutboundChannelMethod());
      containerItem.setTotalPurchaseReferenceQty(deliveryDocument.getTotalPurchaseReferenceQty());
      containerItem.setPurchaseCompanyId(
          NumberUtils.createInteger(deliveryDocument.getPurchaseCompanyId()));
      containerItem.setPoDeptNumber(deliveryDocument.getDeptNumber());
      String baseDivCode =
          deliveryDocument.getBaseDivisionCode() != null
              ? deliveryDocument.getBaseDivisionCode()
              : ReceivingConstants.BASE_DIVISION_CODE;
      containerItem.setBaseDivisionCode(baseDivCode);
      String fRG =
          deliveryDocument.getFinancialReportingGroup() != null
              ? deliveryDocument.getFinancialReportingGroup()
              : getFacilityCountryCode();

      containerItem.setFinancialReportingGroupCode(fRG);

      containerItem.setOrderableQuantity(deliveryDocumentLine.getOrderableQuantity());
      containerItem.setWarehousePackQuantity(deliveryDocumentLine.getWarehousePackQuantity());
      if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())) {
        containerItem.setWeightFormatTypeCode(
            deliveryDocumentLine.getAdditionalInfo().getWeightFormatTypeCode());
      }
      containerItem.setPoTypeCode(documentLine.getPoTypeCode());

      if (nonNull(orgUnitId)) containerItem.setOrgUnitId(orgUnitId);

      if (Objects.nonNull(deliveryDocument)) {
        ContainerUtils.setAttributesForImports(
            deliveryDocument.getPoDCNumber(),
            deliveryDocument.getPoDcCountry(),
            deliveryDocument.getImportInd(),
            containerItem);
      }

      if (isNationalPo) {
        containerItem.setPurchaseReferenceLineNumber(
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
        containerItem.setDeptNumber(
            Objects.nonNull(deliveryDocument)
                    && !StringUtils.isEmpty(deliveryDocument.getDeptNumber())
                ? Integer.valueOf(deliveryDocument.getDeptNumber())
                : null);
        containerItem.setItemNumber(deliveryDocumentLine.getItemNbr());
        containerItem.setVendorGS128(item.getVendorGS128()); // always null
        containerItem.setGtin(deliveryDocumentLine.getGtin());
        containerItem.setVnpkQty(deliveryDocumentLine.getVendorPack());
        containerItem.setWhpkQty(deliveryDocumentLine.getWarehousePack());
        containerItem.setVnpkcbqty(deliveryDocumentLine.getCube());
        containerItem.setVnpkcbuomcd(deliveryDocumentLine.getCubeUom());
        containerItem.setDescription(deliveryDocumentLine.getDescription());
        containerItem.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
        containerItem.setActualTi(deliveryDocumentLine.getPalletTie());
        containerItem.setActualHi(deliveryDocumentLine.getPalletHigh());
        containerItem.setVnpkWgtQty(deliveryDocumentLine.getWeight());
        containerItem.setVnpkWgtUom(deliveryDocumentLine.getWeightUom());

        // set expiry date and lot number
        containerItem.setLotNumber(
            Objects.nonNull(updateInstructionRequest.getLotNumber())
                ? sanitize(updateInstructionRequest.getLotNumber())
                : null);
        containerItem.setExpiryDate(
            Objects.nonNull(updateInstructionRequest.getExpiryDate())
                ? updateInstructionRequest.getExpiryDate()
                : null);

        // Override with ItemMDM data if available
        if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())) {
          ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();

          if (Objects.nonNull(additionalInfo.getWarehouseAreaCode())) {
            containerItem.setWarehouseAreaCode(additionalInfo.getWarehouseAreaCode());
          }
          if (Objects.nonNull(additionalInfo.getWarehouseGroupCode())) {
            containerItem.setWarehouseGroupCode(additionalInfo.getWarehouseGroupCode());
          }
          if (Objects.nonNull(additionalInfo.getWarehouseRotationTypeCode())) {
            containerItem.setWarehouseRotationTypeCode(
                additionalInfo.getWarehouseRotationTypeCode());
          }
          if (Objects.nonNull(additionalInfo.getProfiledWarehouseArea())) {
            containerItem.setProfiledWarehouseArea(additionalInfo.getProfiledWarehouseArea());
          }

          if (Objects.nonNull(additionalInfo.getWeight())) {
            containerItem.setVnpkWgtQty(additionalInfo.getWeight());
            containerItem.setVnpkWgtUom(additionalInfo.getWeightUOM());
          }

          containerItem.setRecall(additionalInfo.getRecall());
        }

        // Populate the ti-hi from instruction's delivery document

        if (Boolean.TRUE.equals(isParent)) {
          // for SSTK case
          containerItem.setQuantity(
              ReceivingUtils.conversionToEaches(
                  item.getQuantity(), // this needs to come from client only
                  item.getQuantityUOM(), // this needs to come from client only
                  containerItem.getVnpkQty(),
                  containerItem.getWhpkQty()));
          containerItem.setDistributions(instruction.getContainer().getDistributions());
        } else {
          // for DA case
          containerItem.setQuantity(containerItem.getVnpkQty()); // making uniform with above code
          containerItem.setDistributions(containerDtls.getDistributions());
        }
        containerItem.setVendorPackCost(
            ReceivingUtils.parseFloatToDouble(deliveryDocumentLine.getVendorPackCost()));
        containerItem.setWhpkSell(
            ReceivingUtils.parseFloatToDouble(deliveryDocumentLine.getWarehousePackSell()));
        containerItem.setRotateDate(item.getRotateDate());
        containerItem.setVendorGS128(item.getVendorGS128()); // always null
        containerItem.setPromoBuyInd(deliveryDocumentLine.getPromoBuyInd());
        containerItem.setPackagedAsUom(appConfig.getPackagedAsUom());
        containerItem.setVendorNumber(
            NumberUtils.createInteger(deliveryDocument.getVendorNumber()));
        containerItem.setVendorNbrDeptSeq(deliveryDocument.getVendorNbrDeptSeq());

        Map<String, String> containerItemMiscInfo = new HashMap<>();
        if (Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0).getSize())) {
          containerItemMiscInfo.put(
              LabelDataConstants.LABEL_FIELD_SIZE,
              documentLine.getDeliveryDocumentLines().get(0).getSize());
        }
        if (Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0).getColor())) {
          containerItemMiscInfo.put(
              LabelDataConstants.LABEL_FIELD_COLOR,
              documentLine.getDeliveryDocumentLines().get(0).getColor());
        }
        if (Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0).getIsHazmat())
            && Boolean.TRUE.equals(documentLine.getDeliveryDocumentLines().get(0).getIsHazmat())) {
          containerItemMiscInfo.put(LabelDataConstants.LABEL_FIELD_IS_HAZMAT, "H");
        }
        if (Objects.nonNull(documentLine.getDeliveryDocumentLines().get(0).getEvent())) { // NOSONAR
          containerItemMiscInfo.put(
              LabelDataConstants.LABEL_FIELD_PO_EVENT,
              documentLine.getDeliveryDocumentLines().get(0).getEvent());
        }

        if (configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false)) {
          Map<String, String> destination = new HashMap<>();
          LinkedTreeMap<String, Object> moveTreeMap = instruction.getMove();
          if (MapUtils.isNotEmpty(moveTreeMap)
              && nonNull(moveTreeMap.get(ReceivingConstants.MOVE_TO_LOCATION))) {
            destination.put(
                ReceivingConstants.SLOT,
                String.valueOf(moveTreeMap.get(ReceivingConstants.MOVE_TO_LOCATION)));
            destination.put(
                ReceivingConstants.SLOT_TYPE,
                String.valueOf(moveTreeMap.get(ReceivingConstants.SLOT_TYPE)));
            container.setDestination(destination);
          }
        }

        // Set GDC ContainerItemMiscInfo
        setGDCContainerMiscInfo(
            deliveryDocument,
            deliveryDocumentLine,
            containerItemMiscInfo,
            updateInstructionRequest.getUserRole());

        // Set max allowed storage information
        if (configUtils.getConfiguredFeatureFlag(
                getFacilityNum().toString(), SAFEGUARD_MAX_ALLOWED_STORAGE, false)
            && item.getMaxAllowedStorageDate() != null
            && item.getMaxAllowedStorageDays() != null) {
          containerItemMiscInfo.put(
              MAX_ALLOWED_STORAGE_DATE, String.valueOf(item.getMaxAllowedStorageDate()));
          containerItemMiscInfo.put(
              MAX_ALLOWED_STORAGE_DAYS, String.valueOf(item.getMaxAllowedStorageDays()));
        }

        // set container item misc value is not empty
        if (MapUtils.isNotEmpty(containerItemMiscInfo)) {
          containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
        }

        if (configUtils.isBOLWeightCheckEnabled(getFacilityNum())
            && ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(
                containerItem.getWeightFormatTypeCode())) {
          // Check BOL weight for line item
          purchaseReferenceValidator.validateVariableWeight(deliveryDocumentLine);

          variableWeightItemExists = true;
          containerItem.setVnpkWgtQty(deliveryDocumentLine.getBolWeight());
          containerItem.setVnpkWgtUom(ReceivingConstants.Uom.LB);
          log.info(
              "Vendorpack weight qty:{} for variable weight item:{}",
              containerItem.getVnpkWgtQty(),
              containerItem.getItemNumber());

          totalReceivedQtyInVnpk =
              ReceivingUtils.conversionToVendorPack(
                  containerItem.getQuantity(), containerItem.getQuantityUOM(),
                  containerItem.getVnpkQty(), containerItem.getWhpkQty());
          containerWeight += containerItem.getVnpkWgtQty() * totalReceivedQtyInVnpk;
        }

        List<ScannedData> scannedDataList = updateInstructionRequest.getScannedDataList();
        if (!CollectionUtils.isEmpty(scannedDataList)) {
          Map<String, String> scannedDataMap = new HashMap<>();
          scannedDataList.forEach(
              scannedData -> {
                scannedDataMap.put(scannedData.getKey(), scannedData.getValue());
              });

          containerItem.setGtin(scannedDataMap.get(ReceivingConstants.KEY_GTIN));
          containerItem.setLotNumber(scannedDataMap.get(ReceivingConstants.KEY_LOT));
          containerItem.setSerial(scannedDataMap.get(ReceivingConstants.KEY_SERIAL));
          try {
            containerItem.setExpiryDate(
                DateUtils.parseDate(
                    scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE),
                    ReceivingConstants.EXPIRY_DATE_FORMAT));
          } catch (ParseException e) {
            throw new ReceivingException(e.getMessage());
          }
        }
      } else {
        containerItem.setQuantity(item.getQuantity());
        containerItem.setItemNumber(ReceivingConstants.DUMMY_ITEM_NUMBER);
        containerItem.setVnpkQty(ReceivingConstants.PO_CON_VNPK_WHPK_QTY);
        containerItem.setWhpkQty(ReceivingConstants.PO_CON_VNPK_WHPK_QTY);
      }
      containerItems.add(containerItem);
    }

    if (variableWeightItemExists) {
      log.info("Container weight:{} for LPN:{}", containerWeight, container.getTrackingId());
      container.setWeight(containerWeight);
      container.setWeightUOM(ReceivingConstants.Uom.LB);
    }

    container.setContainerItems(containerItems);
    // Need to populate sscc number
    container.setSsccNumber(instruction.getSsccNumber());
    return container;
  }

  private String getUserId(Instruction instruction) {
    return Strings.isNotEmpty(instruction.getLastChangeUserId())
        ? instruction.getLastChangeUserId()
        : instruction.getCreateUserId();
  }

  // Set containerMiscInfo for GDC
  public void setGDCContainerMiscInfo(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      Map<String, String> containerItemMiscInfo,
      String userRole)
      throws ReceivingException {
    boolean isDCOneAtlas =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    boolean isGDCAutomatedDC =
        !configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    boolean isItemConverted =
        Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())
            ? deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()
            : false;
    boolean isOneAtlasConverted = isDCOneAtlas && isItemConverted;

    if (isOneAtlasConverted || isGDCAutomatedDC) {
      // Set GDC OSS transfer PO Info
      isTransferFormOSSForGDC(deliveryDocument, deliveryDocumentLine, containerItemMiscInfo);
      if (isDCOneAtlas) {
        // Set item converted value
        containerItemMiscInfo.put(
            IS_ATLAS_CONVERTED_ITEM,
            String.valueOf(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()));
      }

      if (isNotBlank(userRole)) containerItemMiscInfo.put(USER_ROLE, userRole);
    }
  }

  // check and persist values if transfer po from OSS for GDC
  private void isTransferFormOSSForGDC(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      Map<String, String> containerItemMiscInfo)
      throws ReceivingException {

    // if OSS PO
    Integer poTypeCode = deliveryDocument.getPoTypeCode();
    String fromPoLineDCNumber = deliveryDocumentLine.getFromPoLineDCNumber();

    if (isOssTransfer(poTypeCode, fromPoLineDCNumber, configUtils)) {
      if (Objects.nonNull(deliveryDocumentLine.getFromOrgUnitId())) {
        containerItemMiscInfo.put(TO_SUBCENTER, configUtils.getOrgUnitId());
        containerItemMiscInfo.put(
            FROM_SUBCENTER, String.valueOf(deliveryDocumentLine.getFromOrgUnitId()));
        containerItemMiscInfo.put(PO_TYPE, String.valueOf(deliveryDocument.getPoTypeCode()));
        containerItemMiscInfo.put(IS_RECEIVE_FROM_OSS, TRUE_STRING);
      } else {
        throw new ReceivingException(
            "OSS: Transfer PO Missing required fromOrgUnitId value from GDM", BAD_REQUEST);
      }
    }
  }

  public Container createDockTagContainer(
      Instruction dockTagInstruction,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      boolean markCompleted) {
    Container container =
        getContainerForDockTag(dockTagInstruction, instructionRequest, httpHeaders);

    if (markCompleted) {
      container.setLastChangedTs(new Date());
      container.setCompleteTs(new Date());
      container.setPublishTs(new Date());
      container.setLastChangedUser(httpHeaders.getFirst(USER_ID_HEADER_KEY));
    }
    return containerPersisterService.saveContainer(container);
  }

  public Container getContainerForDockTag(
      Instruction dockTagInstruction,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders) {
    Container container = new Container();
    container.setCreateUser(httpHeaders.getFirst(USER_ID_HEADER_KEY));
    container.setLastChangedUser(httpHeaders.getFirst(USER_ID_HEADER_KEY));
    container.setMessageId(dockTagInstruction.getMessageId());
    container.setInventoryStatus(dockTagInstruction.getContainer().getInventoryStatus());
    container.setCtrReusable(dockTagInstruction.getContainer().getCtrReusable());
    container.setCtrShippable(dockTagInstruction.getContainer().getCtrShippable());
    container.setTrackingId(dockTagInstruction.getContainer().getTrackingId());
    container.setInstructionId(dockTagInstruction.getId());
    container.setLocation(instructionRequest.getDoorNumber());
    container.setDeliveryNumber(dockTagInstruction.getDeliveryNumber());
    container.setContainerType(ContainerType.PALLET.name());
    container.setActivityName(dockTagInstruction.getActivityName());
    container.setLabelId(
        getLabelId(dockTagInstruction.getActivityName(), ContainerType.PALLET.name()));
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setIsConveyable(Boolean.FALSE);
    if (!CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
      container.setIsConveyable(
          instructionRequest
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getIsConveyable());
    }
    container.setOnConveyor(Boolean.FALSE);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, getFacilityNum().toString());
    container.setFacility(facility);
    return container;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerIncludingChild(Container parentContainer) throws ReceivingException {
    log.info(
        "Entering getContainerIncludingChild() with parentTrackingId:{}",
        parentContainer.getTrackingId());
    return getContainerIncludingChild(parentContainer, Uom.EACHES);
  }

  /**
   * This method will return parentChild container Details based on parentContainer
   *
   * @param parentContainer
   * @return ContainerModel
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerIncludingChild(Container parentContainer, String quantityUOM)
      throws ReceivingException {
    if (parentContainer == null || StringUtils.isEmpty(parentContainer.getTrackingId())) {
      throw new ReceivingException(
          "Either parentContainer is null or trackingId is not present", HttpStatus.BAD_REQUEST);
    }
    log.info(
        "Entering getContainerIncludingChild() with parentTrackingId:{}, quantityUOM:{}",
        parentContainer.getTrackingId(),
        quantityUOM);

    Set<Container> childContainerList =
        containerRepository.findAllByParentTrackingId(parentContainer.getTrackingId());
    if (CollectionUtils.isEmpty(childContainerList)) {
      parentContainer.setChildContainers(childContainerList);
      return parentContainer;
    }

    List<String> childContainerTrackingIds = new ArrayList<>();
    childContainerList.forEach(
        child -> {
          if (null != child.getContainerMiscInfo()) {
            child.setAudited(
                (Boolean) child.getContainerMiscInfo().getOrDefault(IS_AUDITED, false));
          }
          childContainerTrackingIds.add(child.getTrackingId());
        });

    List<ContainerItem> childContainerItems =
        containerItemRepository.findByTrackingIdIn(childContainerTrackingIds);

    Map<String, List<ContainerItem>> childContainerItemMap = new HashMap<>();
    for (ContainerItem containerItem : childContainerItems) {
      if (!ObjectUtils.isEmpty(containerItem) && quantityUOM.equalsIgnoreCase(WHPK)) {
        containerItem.setQuantity(containerItem.getQuantity() / containerItem.getWhpkQty());
        containerItem.setQuantityUOM(quantityUOM);
      }
      if (!CollectionUtils.isEmpty(childContainerItemMap.get(containerItem.getTrackingId()))) {
        childContainerItemMap.get(containerItem.getTrackingId()).add(containerItem);
      } else {
        childContainerItemMap.put(
            containerItem.getTrackingId(), new ArrayList<>(Arrays.asList(containerItem)));
      }
    }

    childContainerList.forEach(
        child -> child.setContainerItems(childContainerItemMap.get(child.getTrackingId())));

    parentContainer.setChildContainers(childContainerList);
    return parentContainer;
  }

  /**
   * This method will update container and container_item
   *
   * @param instruction
   * @param documentLine document line
   * @param userId user id
   */
  @Transactional
  @InjectTenantFilter
  public void updateContainerItem(
      Instruction instruction, DocumentLine documentLine, String userId) {

    boolean isNationalPo = isNationalPO(documentLine.getPurchaseRefType());
    String trackerId = instruction.getContainer().getTrackingId();
    Container container = containerRepository.findByTrackingId(trackerId);
    List<ContainerItem> containerItems = containerItemRepository.findByTrackingId(trackerId);
    if (!CollectionUtils.isEmpty(containerItems)) {
      ContainerItem containerItem = containerItems.get(0);
      container.setLastChangedUser(userId);
      if (isNationalPo) {
        Integer quantityInEach =
            ReceivingUtils.conversionToEaches(
                documentLine.getQuantity(),
                documentLine.getQuantityUOM(),
                documentLine.getVnpkQty(),
                documentLine.getWhpkQty());
        containerItem.setQuantity(containerItem.getQuantity() + quantityInEach);
        containerItem.setRotateDate(documentLine.getRotateDate());
      } else {
        DeliveryDocument deliveryDocument =
            new Gson().fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        Float updatedWeightQty =
            deliveryDocument.getWeight()
                / deliveryDocument.getTotalPurchaseReferenceQty()
                * (containerItem.getQuantity() + documentLine.getQuantity());
        Float updatedCubeQty =
            deliveryDocument.getCubeQty()
                / deliveryDocument.getTotalPurchaseReferenceQty()
                * (containerItem.getQuantity() + documentLine.getQuantity());
        container.setWeight(updatedWeightQty);
        container.setCube(updatedCubeQty);
        containerItem.setQuantity(containerItem.getQuantity() + documentLine.getQuantity());
      }
      containerItemRepository.save(containerItem);
    }
    containerRepository.save(container);
  }

  private Date getContainerCreateTs() {
    if (!configUtils.getConfiguredFeatureFlag(
        String.valueOf(TenantContext.getFacilityNum()),
        IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM,
        true)) {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
      if (Objects.nonNull(getAdditionalParams())
          && Objects.nonNull(getAdditionalParams().get(CONTAINER_CREATE_TS))
          && getAdditionalParams().containsKey(CONTAINER_CREATE_TS)) {
        try {
          return (formatter.parse(getAdditionalParams().get(CONTAINER_CREATE_TS).toString()));
        } catch (Exception e) {
          log.error(
              "Unable to parse the container Create Ts= {}",
              getAdditionalParams().get(CONTAINER_CREATE_TS));
        }
      }
    }
    return new Date();
  }

  private List<Distribution> getAllocatedDistribution(ContainerItem containerItem) {
    List<Distribution> allocatedDistribution = new ArrayList<>();
    List<Distribution> distributionList = containerItem.getDistributions();
    if (!CollectionUtils.isEmpty(distributionList)) {
      Integer remainingQtyToBeAllocated = containerItem.getQuantity();
      for (Distribution distributionDetails : distributionList) {
        if (distributionDetails.getAllocQty() <= remainingQtyToBeAllocated) {
          allocatedDistribution.add(distributionDetails);
          remainingQtyToBeAllocated = remainingQtyToBeAllocated - distributionDetails.getAllocQty();
          if (remainingQtyToBeAllocated == 0) {
            break;
          }
        } else {
          distributionDetails.setAllocQty(remainingQtyToBeAllocated);
          allocatedDistribution.add(distributionDetails);
          break;
        }
      }
    }
    return allocatedDistribution;
  }

  /**
   * This method used to update distribution( on complete action)
   *
   * @param trackerId
   * @return
   * @throws ReceivingException
   */
  @Transactional
  public Container containerComplete(String trackerId, String userId) throws ReceivingException {
    List<ContainerItem> containerItems = null;
    Container container = containerPersisterService.getContainerDetails(trackerId);
    if (container == null) {
      log.error("container is null for given lpn={}", trackerId);
      throw new ReceivingException(
          ReceivingException.MATCHING_CONTAINER_NOT_FOUND, HttpStatus.BAD_REQUEST);
    }
    setDistributionAndComplete(userId, container);
    return containerPersisterService.saveContainer(container);
  }

  @Transactional
  public List<Container> containerListComplete(List<Container> containerList, String userId) {
    containerList.forEach(
        container -> {
          setDistributionAndComplete(userId, container);
        });
    saveAll(containerList);
    return containerList;
  }

  public void setDistributionAndComplete(String userId, Container container) {
    List<ContainerItem> containerItems;
    containerItems = container.getContainerItems();
    if (containerItems != null) {
      containerItems.forEach(
          containerItem -> {
            containerItem.setDistributions(getAllocatedDistribution(containerItem));
            if (configUtils.getConfiguredFeatureFlag( // true default flow else set false in tenant
                    getFacilityNum().toString(), ADJUST_HI_ENABLED, true)
                && containerItem.getQuantity() != null
                && containerItem.getActualTi() != null) {
              Integer qtyInVendorPack =
                  ReceivingUtils.conversionToVendorPack(
                      containerItem.getQuantity(),
                      containerItem.getQuantityUOM(),
                      containerItem.getVnpkQty(),
                      containerItem.getWhpkQty());
              Integer adjustedHi =
                  (int) Math.ceil((double) qtyInVendorPack / (double) containerItem.getActualTi());
              containerItem.setActualHi(adjustedHi);
            }
          });
      container.setContainerItems(containerItems);
    }

    container.setLabelId(getLabelId(container.getActivityName(), container.getContainerType()));
    container.setCompleteTs(new Date());
    // updating publishTs
    container.setPublishTs(new Date());
    container.setLastChangedUser(userId);
  }

  /**
   * Publish the consolidated container(parent with child) to the Mass Construct the response for
   * move depending on PO Con and PO Line . By Default this method will enable JMSRetry true
   *
   * @param container consolidated container
   * @param headers
   * @return
   */
  public void publishContainer(Container container, Map<String, Object> headers) {
    publishContainer(container, headers, Boolean.TRUE);
  }

  /**
   * @param trackingId
   * @param headers
   * @param containerStatus
   */
  public void publishContainerWithStatus(
      String trackingId, HttpHeaders headers, String containerStatus) {
    try {
      Container container = this.getContainerByTrackingId(trackingId);
      log.info(
          "Going to publish container with status {} and tracking id [{}]",
          containerStatus,
          trackingId);
      container.setContainerStatus(containerStatus);
      publishExceptionContainer(
          containerPersisterService.saveAndFlushContainer(container), headers, Boolean.TRUE);

    } catch (ReceivingException e) {
      log.error(
          "Problem while publish the container with status {} of trackingId [{}].",
          containerStatus,
          trackingId);
    }
  }

  /**
   * Method publish multiple containers
   *
   * @param trackingIdList
   * @param headers
   * @param containerStatus
   */
  public void publishContainerListWithStatus(
      List<String> trackingIdList, HttpHeaders headers, String containerStatus) {
    String currentId = null;
    try {
      Set<Container> containers = this.getContainerListByTrackingIdList(trackingIdList);
      for (Container container : containers) {
        currentId = container.getTrackingId();
        log.info(
            "Going to publish a container from list with status {} and tracking id [{}]",
            containerStatus,
            currentId);
        container.setContainerStatus(containerStatus);
        publishExceptionContainer(
            containerPersisterService.saveAndFlushContainer(container), headers, Boolean.TRUE);
      }
    } catch (ReceivingException e) {
      log.error(
          "Problem while publish the container with status {} of id {} from the List {}",
          containerStatus,
          currentId,
          trackingIdList);
    }
  }

  /**
   * Publish the consolidated container(parent with child) to the Mass Construct the response for
   * move depending on PO Con and PO Line . In this publishContainer method, user have to pass the
   * argument (putForRetry either True or false) depending on the need of enabling JMSRetry
   *
   * @param container
   * @param headers
   * @param putForRetry
   */
  public void publishContainer(
      Container container, Map<String, Object> headers, Boolean putForRetry) {
    MessagePublisher messagePublisher =
        configUtils.getConfiguredInstance(
            String.valueOf(getFacilityNum()),
            ReceivingConstants.RECEIPT_EVENT_HANDLER,
            MessagePublisher.class);

    String containerJson = gson.toJson(container);
    ContainerDTO containerDTO = gson.fromJson(containerJson, ContainerDTO.class);

    // Set To Be Audited Tag for GDC One Atlas Items
    setToBeAuditedTagGDC(containerDTO);

    // Set Subcenter details is receiving from OSS for GDC One Atlas Items
    addSubcenterInfo(containerDTO);

    // set additional attributes for SCT
    receiptPublisher.addAdditionalData(containerDTO);

    messagePublisher.publish(containerDTO, headers);

    // this method has to be deleted when kafka Listener are available in OP, Inventory
    // and if want to enable kafka communication then we have to add receiptEventHandler=
    // kafkaReceiptProcessor in CCM under tenant specific configuration for any facility number
    // And you have to add the topic name to key atlas.receipts.topic under KafkaTopic CCM config
    // Below method is being added for SCT communication through kafka for International MCC market
    publishToKafkaTopic(containerDTO, headers);
  }

  public void addSubcenterInfo(ContainerDTO containerDTO) {

    final Map<String, Object> containerMiscInfo = containerDTO.getContainerMiscInfo();
    if (containerMiscInfo != null
        && nonNull(containerMiscInfo.get(FLOW_DESCRIPTOR))
        && FLOW_RECEIVE_INTO_OSS.equalsIgnoreCase(
            containerMiscInfo.get(FLOW_DESCRIPTOR).toString())) {
      OrgUnitIdInfo orgUnitIdInfo = new OrgUnitIdInfo();
      orgUnitIdInfo.setDestinationId(containerDTO.getOrgUnitId());
      orgUnitIdInfo.setSourceId(containerDTO.getOrgUnitId());
      containerDTO.setOrgUnitIdInfo(orgUnitIdInfo);
    } else if (!CollectionUtils.isEmpty(containerDTO.getContainerItems())) {
      Map<String, String> containerItemMiscInfo =
          containerDTO.getContainerItems().get(0).getContainerItemMiscInfo();
      log.info("persisted containerItemMiscInfo {}", containerItemMiscInfo);
      if (Objects.nonNull(containerItemMiscInfo)
          && Objects.nonNull(containerItemMiscInfo.get(FROM_ORG_UNIT_ID))) {
        Integer toOrgUnitId =
            Objects.nonNull(configUtils.getOrgUnitId())
                ? Integer.valueOf(configUtils.getOrgUnitId())
                : null;
        OrgUnitIdInfo orgUnitIdInfo = new OrgUnitIdInfo();
        orgUnitIdInfo.setSourceId(
            Integer.valueOf(String.valueOf(containerItemMiscInfo.get(FROM_ORG_UNIT_ID))));
        orgUnitIdInfo.setDestinationId(toOrgUnitId);
        containerDTO.setOrgUnitIdInfo(orgUnitIdInfo);
      }
    }
  }

  public void setToBeAuditedTagGDC(ContainerDTO containerDTO) {
    // If One Atlas Enabled DC Set To Be Audited Tag for GDC One Atlas Items
    List<ContainerTag> tags = new ArrayList<>();
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false)) {
      ContainerTag toBeAuditTag = new ContainerTag(CONTAINER_TO_BE_AUDITED, CONTAINER_SET);
      tags.add(toBeAuditTag);

      Map<String, String> destination = containerDTO.getDestination();
      if (MapUtils.isNotEmpty(destination) && PRIME.equalsIgnoreCase(destination.get(SLOT_TYPE))) {
        // Set PRIME Putaway if slotType is prime
        ContainerTag primeTag = new ContainerTag(PUTAWAY_TO_PRIME, CONTAINER_SET);
        tags.add(primeTag);
      }
      containerDTO.setTags(tags);
    }
  }

  private void publishToKafkaTopic(ContainerDTO container, Map<String, Object> headers) {

    boolean isKafkaReceiptPublishEnabled =
        configUtils.isFeatureFlagEnabled(
            ReceivingConstants.KAFKA_RECEIPT_PUBLISH_ENABLED, getFacilityNum());
    if (isKafkaReceiptPublishEnabled) {
      MessagePublisher kafkaMessagePublisher =
          configUtils.getConfiguredInstance(
              String.valueOf(getFacilityNum()),
              ReceivingConstants.KAFKA_RECEIPT_EVENT_HANDLER,
              MessagePublisher.class);
      kafkaMessagePublisher.publish(container, headers);
    }
  }

  /**
   * Publish the exception container on MaaS. In this publishExceptionContainer method, user have to
   * pass the argument (putForRetry either True or false) depending on the need of enabling JMSRetry
   *
   * @param container
   * @param httpHeaders
   * @param putForRetry
   */
  public void publishExceptionContainer(
      Container container, HttpHeaders httpHeaders, Boolean putForRetry) {
    // converting container Object into String
    String jsonObject = gson.toJson(container);

    Map<String, Object> headersToSend = getForwardablHeaderWithTenantData(httpHeaders);
    ContainerDTO containerDTO = gson.fromJson(jsonObject, ContainerDTO.class);

    // publishing container information to inventory
    // publishing on JMS or Kafka depending upon the facility number
    MessagePublisher messagePublisher =
        configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.EXCEPTION_CONTAINER_PUBLISHER,
            ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER,
            MessagePublisher.class);
    messagePublisher.publish(containerDTO, headersToSend);
  }

  /**
   * Backout the container
   *
   * @param trackingId
   * @param headers
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "backoutReceiptsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerService",
      level3 = "backoutContainers")
  public void backoutContainer(String trackingId, HttpHeaders headers) throws ReceivingException {
    log.info("Entering backoutContainer() with trackingId[{}]", trackingId);

    Container container = containerAdjustmentValidator.getValidContainer(trackingId);

    /*
     *  Get the proper implementation of PutawayService based on tenant
     */
    PutawayService putawayService =
        configUtils.getPutawayServiceByFacility(getFacilityNum().toString());

    /*
     * Create receipts with negative quantity
     */
    Long deliveryNumber = container.getDeliveryNumber();
    String userId = headers.get(USER_ID_HEADER_KEY).get(0);
    final List<Receipt> receipts = new ArrayList<>(container.getContainerItems().size());
    container
        .getContainerItems()
        .forEach(
            containerItem -> {
              Receipt receipt = new Receipt();
              receipt.setDeliveryNumber(deliveryNumber);
              receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
              receipt.setPurchaseReferenceLineNumber(
                  containerItem.getPurchaseReferenceLineNumber());
              receipt.setVnpkQty(containerItem.getVnpkQty());
              receipt.setWhpkQty(containerItem.getWhpkQty());
              receipt.setEachQty(containerItem.getQuantity() * -1);
              receipt.setQuantity(
                  ReceivingUtils.conversionToVendorPack(
                          containerItem.getQuantity(),
                          containerItem.getQuantityUOM(),
                          containerItem.getVnpkQty(),
                          containerItem.getWhpkQty())
                      * -1);
              receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
              receipt.setCreateUserId(userId);
              receipt.setCreateTs(new Date());
              if (PurchaseReferenceType.DSDC
                      .toString()
                      .equalsIgnoreCase(containerItem.getInboundChannelMethod())
                  || PurchaseReferenceType.POCON
                      .toString()
                      .equalsIgnoreCase(containerItem.getInboundChannelMethod())) {
                receipt.setPalletQty(-1);
              }
              receipts.add(receipt);
            });

    // Get delivery details
    // TODO: here it is supposed to use deliveryServiceImpl and this will be default configured, so
    // it will be used
    DeliveryService deliveryService =
        configUtils.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    String deliveryResponse = deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, headers);
    DeliveryDetails deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryResponse, DeliveryDetails.class);

    /*
     * Updating container Status and saving receipts
     */

    containerPersisterService.updateContainerStatusAndSaveReceipts(
        trackingId, ReceivingConstants.STATUS_BACKOUT, userId, receipts);

    /*
     * Publish delivery status with list of received quantity
     */
    if (InstructionUtils.isReceiptPostingRequired(
        deliveryDetails.getDeliveryStatus(), deliveryDetails.getStateReasonCodes())) {
      deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          DeliveryStatus.COMPLETE.name(),
          receiptService.getReceivedQtySummaryByPOForDelivery(
              deliveryNumber, ReceivingConstants.Uom.EACHES),
          ReceivingUtils.getForwardablHeader(headers));
    }

    /*
     * Send delete putaway request message
     */
    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_DELETE_ACTION, headers);

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(trackingId, headers, Boolean.TRUE);

    log.info("Given container [{}] backout successfully", container.getTrackingId());
  }

  /**
   * For adjustment messages with reason code 11 and NOT an outbound container (container is in
   * receiving db) If the quantity change is the whole pallet, send RTU delete message If the
   * quantity change is less than the whole pallet, send RTU delete message and Putaway Message with
   * new quantity
   *
   * @param trackingId
   * @param damageQty
   * @param headers
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "processDamageAdjustmentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerService",
      level3 = "processDamageAdjustment")
  public void processDamageAdjustment(String trackingId, Integer damageQty, HttpHeaders headers)
      throws ReceivingException {
    log.info(
        "Entering processDamageAdjustment() with trackingId[{}], damageQty[{}]",
        trackingId,
        damageQty);

    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);
    if (container == null || container.getContainerItems().isEmpty()) {
      log.warn(ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      throw new ReceivingException(
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    }

    // As per inventory contract, damageQty will always negative value
    Integer remainingQty = container.getContainerItems().get(0).getQuantity() + damageQty;

    PutawayService putawayService =
        configUtils.getPutawayServiceByFacility(getFacilityNum().toString());

    if (remainingQty == 0) {
      gdcPutawayPublisher.publishMessage(container, PUTAWAY_DELETE_ACTION, headers);
    } else {
      // Adjust the container before sending RTU
      Container adjustedContainer = adjustContainerByQty(true, container, remainingQty);

      gdcPutawayPublisher.publishMessage(adjustedContainer, PUTAWAY_UPDATE_ACTION, headers);
    }
  }

  /**
   * This method is responsible for processing vendor damage adjustment. This will get invoked
   * when @{@link com.walmart.move.nim.receiving.core.message.listener.InventoryAdjustmentListener}
   * will get a adjustment event for Reason code 53.
   *
   * @param trackingId
   * @param adjustment
   * @param headers
   * @throws ReceivingException
   */
  public void processVendorDamageAdjustment(
      String trackingId, JsonObject adjustment, HttpHeaders headers) throws ReceivingException {
    log.info("Entering processVendorDamageAdjustment() with trackingId[{}]", trackingId);
    /*
     * Get container details
     */
    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);

    /*
     * Run through the validations
     */

    if (container == null) {
      String errorDescription =
          String.format(ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_ERROR_MSG, trackingId);
      log.warn(errorDescription);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }

    CancelContainerResponse response =
        containerAdjustmentValidator.validateContainerForAdjustment(container);

    if (response != null) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.INVALID_INVENTORY_ADJUSTMENT_ERROR_MSG,
              response.getErrorCode(),
              response.getTrackingId());
      log.warn(errorDescription);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_INVENTORY_ADJUSTMENT_DATA, errorDescription);
    }

    String userId = headers.getFirst(USER_ID_HEADER_KEY);
    Integer adjustmentValue =
        Integer.parseInt(adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).toString());
    String adjustmentValueUom = null;
    if (!adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM).isJsonNull()) {
      adjustmentValueUom =
          adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM).getAsString();
    }
    List<Receipt> receipts = new ArrayList<>(container.getContainerItems().size());

    // fetching master record
    Optional<Receipt> osdrMasterReceiptOptional =
        osdrRecordCountAggregator.findOsdrMasterReceipt(
            container.getDeliveryNumber(),
            container.getContainerItems().get(0).getPurchaseReferenceNumber(),
            container.getContainerItems().get(0).getPurchaseReferenceLineNumber());

    for (ContainerItem containerItem : container.getContainerItems()) {
      Receipt receipt =
          createReceiptForVDMClaimType(
              container, userId, adjustmentValue, adjustmentValueUom, containerItem);
      receipts.add(receipt);

      // OSDR

      if (osdrMasterReceiptOptional.isPresent()) {
        Receipt osdrMasterReceipt = osdrMasterReceiptOptional.get();
        osdrMasterReceipt.setFbDamagedQty(
            (int) ReceivingUtils.applyDefaultValue(osdrMasterReceipt.getFbDamagedQty())
                + (-1 * adjustmentValue));
        osdrMasterReceipt.setFbDamagedClaimType(VDM_CLAIM_TYPE);
        if (!StringUtils.isEmpty(adjustmentValueUom)
            && ReceivingConstants.EACHES.equalsIgnoreCase(adjustmentValueUom)) {
          osdrMasterReceipt.setFbDamagedQtyUOM(ReceivingConstants.Uom.EACHES);
        }
        osdrMasterReceipt.setFbDamagedReasonCode(OSDRCode.D53);
        receipts.add(osdrMasterReceipt);
      } else {
        Receipt clonedReceipt = SerializationUtils.clone(receipt);
        clonedReceipt.setOsdrMaster(1);
        receipts.add(clonedReceipt);
      }
    }

    container.setLastChangedUser(userId);
    containerPersisterService.createReceiptAndContainer(receipts, container);

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(trackingId, headers, Boolean.TRUE);
  }

  private Receipt createReceiptForVDMClaimType(
      Container container,
      String userId,
      Integer adjustmentValue,
      String adjustmentValueUom,
      ContainerItem containerItem) {
    Integer containerItemQty = containerItem.getQuantity();
    containerItem.setQuantity(containerItemQty + adjustmentValue);
    Receipt receipt = setReceiptAttributes(container, userId, containerItem);
    receipt.setFbDamagedQty(-1 * adjustmentValue);
    receipt.setFbDamagedClaimType(VDM_CLAIM_TYPE);
    receipt.setEachQty(-receipt.getFbDamagedQty());
    if (!StringUtils.isEmpty(adjustmentValueUom)
        && ReceivingConstants.EACHES.equalsIgnoreCase(adjustmentValueUom)) {
      receipt.setFbDamagedQtyUOM(Uom.EACHES);
    }
    receipt.setFbDamagedReasonCode(OSDRCode.D53);
    return receipt;
  }

  private Receipt setReceiptAttributes(
      Container container, String userId, ContainerItem containerItem) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(container.getDeliveryNumber());
    receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
    receipt.setVnpkQty(containerItem.getVnpkQty());
    receipt.setWhpkQty(containerItem.getWhpkQty());
    receipt.setCreateUserId(userId);
    return receipt;
  }

  /**
   * This method is responsible for processing concealed shortage or overage adjustment. This will
   * get invoked when @{@link
   * com.walmart.move.nim.receiving.core.message.listener.InventoryAdjustmentListener} will get a
   * adjustment event for Reason code 54 or 55 respectively.
   *
   * @param trackingId
   * @param adjustment
   * @param headers
   * @throws ReceivingException
   */
  public void processConcealedShortageOrOverageAdjustment(
      String trackingId, JsonObject adjustment, HttpHeaders headers) throws ReceivingException {
    log.info(
        "Entering processConcealedShortageOrOverageAdjustment() with trackingId[{}]", trackingId);

    /*
     * Get container details
     */
    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);

    /*
     * Run through the validations
     */

    if (container == null) {
      String errorDescription =
          String.format(ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_ERROR_MSG, trackingId);
      log.warn(errorDescription);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }

    CancelContainerResponse response =
        containerAdjustmentValidator.validateContainerForAdjustment(container);

    if (response != null) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.INVALID_INVENTORY_ADJUSTMENT_ERROR_MSG,
              response.getErrorCode(),
              response.getTrackingId());
      log.warn(errorDescription);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_INVENTORY_ADJUSTMENT_DATA, errorDescription);
    }

    String userId = headers.getFirst(USER_ID_HEADER_KEY);
    int adjustmentValue = adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).getAsInt();
    int reasonCode = adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE).getAsInt();
    final List<Receipt> receipts = new ArrayList<>(container.getContainerItems().size());

    Optional<Receipt> osdrMasterReceiptOptional =
        osdrRecordCountAggregator.findOsdrMasterReceipt(
            container.getDeliveryNumber(),
            container.getContainerItems().get(0).getPurchaseReferenceNumber(),
            container.getContainerItems().get(0).getPurchaseReferenceLineNumber());

    container
        .getContainerItems()
        .forEach(
            containerItem -> {
              Receipt receipt =
                  createReceiptForConcealedShortage(
                      container, userId, adjustmentValue, reasonCode, containerItem);
              receipts.add(receipt);

              // OSDR
              if (osdrMasterReceiptOptional.isPresent()) {
                Receipt osdrMasterReceipt = osdrMasterReceiptOptional.get();
                osdrMasterReceipt.setFbConcealedShortageQty(
                    (int)
                            (ReceivingUtils.applyDefaultValue(
                                osdrMasterReceipt.getFbConcealedShortageQty()))
                        + (-1 * adjustmentValue));
                osdrMasterReceipt.setFbConcealedShortageReasonCode(null);
                receipts.add(osdrMasterReceipt);
              } else {
                Receipt clonedReceipt = SerializationUtils.clone(receipt);
                clonedReceipt.setFbShortReasonCode(null);
                clonedReceipt.setFbOverReasonCode(null);
                clonedReceipt.setFbConcealedShortageReasonCode(null);
                clonedReceipt.setOsdrMaster(1);
                receipts.add(clonedReceipt);
              }
            });
    container.setLastChangedUser(userId);
    containerPersisterService.createReceiptAndContainer(receipts, container);

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(trackingId, headers, Boolean.TRUE);
  }

  private Receipt createReceiptForConcealedShortage(
      Container container,
      String userId,
      int adjustmentValue,
      int reasonCode,
      ContainerItem containerItem) {
    Receipt receipt = setReceiptAttributes(container, userId, containerItem);

    containerItem.setQuantity(containerItem.getQuantity() + adjustmentValue);
    receipt.setFbConcealedShortageQty(-adjustmentValue);
    if (reasonCode == ReceivingConstants.RCS_CONCEALED_SHORTAGE_REASON_CODE) {
      receipt.setFbConcealedShortageReasonCode(OSDRCode.S54);
    } else if (reasonCode == ReceivingConstants.RCO_CONCEALED_OVERAGE_REASON_CODE) {
      receipt.setFbConcealedShortageReasonCode(OSDRCode.O55);
    }
    receipt.setEachQty(-receipt.getFbConcealedShortageQty());
    return receipt;
  }

  public void processRIPNegativeDamagedAdjustment(
      String trackingId,
      Long deliveryNumber,
      Integer reasonCode,
      JsonObject item,
      JsonObject adjustment,
      String userId) {
    log.info(
        "Entering processRIPNegativeDamagedAdjustment() with trackingId [{}] and deliveryNumber [{}]",
        trackingId,
        deliveryNumber);
    Integer adjustmentValue =
        Integer.parseInt(adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).toString());
    String adjustmentValueUom = null;
    if (!adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM).isJsonNull()) {
      adjustmentValueUom =
          adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM).getAsString();
    }
    JsonObject poDetail =
        (JsonObject) item.getAsJsonArray(ReceivingConstants.INVENTORY_ADJUSTMENT_PO_DETAILS).get(0);
    String purchaseRefNumber =
        poDetail.get(ReceivingConstants.INVENTORY_ADJUSTMENT_PURCHASE_REF_NUM).getAsString();
    Integer purchaseRefLineNumber =
        Integer.parseInt(
            poDetail.get(ReceivingConstants.INVENTORY_ADJUSTMENT_PURCHASE_REF_LINE_NUM).toString());

    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt =
        createReceiptForRIPNegativeDamaged(
            item,
            reasonCode,
            adjustmentValue,
            adjustmentValueUom,
            purchaseRefNumber,
            purchaseRefLineNumber,
            deliveryNumber);
    receipt.setCreateUserId(userId);
    receipts.add(receipt);

    Receipt osderMasterReceipt =
        createOSDRMasterReceiptForRIPNegativeDamaged(
            receipt,
            reasonCode,
            deliveryNumber,
            purchaseRefNumber,
            purchaseRefLineNumber,
            adjustmentValue,
            adjustmentValueUom);
    receipts.add(osderMasterReceipt);
    receiptService.saveAll(receipts);
  }

  private Receipt createReceiptForRIPNegativeDamaged(
      JsonObject item,
      Integer reasonCode,
      Integer adjustmentValue,
      String adjustmentValueUom,
      String purchaseRefNumber,
      Integer purchaseRefLineNumber,
      Long deliveryNumber) {
    log.info(
        "Entering createReceiptForRIPNegativeDamaged() with reasonCode [{}] and deliveryNumber [{}]",
        reasonCode,
        deliveryNumber);
    Integer vnpkQty =
        Integer.parseInt(item.get(ReceivingConstants.INVENTORY_VNPK_RATIO).toString());
    Integer whpkQty =
        Integer.parseInt(item.get(ReceivingConstants.INVENTORY_WHPK_RATIO).toString());
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(purchaseRefNumber);
    receipt.setPurchaseReferenceLineNumber(purchaseRefLineNumber);
    receipt.setVnpkQty(vnpkQty);
    receipt.setWhpkQty(whpkQty);
    if (reasonCode.equals(VDM_REASON_CODE)) {
      receipt.setFbDamagedQty(-1 * adjustmentValue);
      receipt.setEachQty(-receipt.getFbDamagedQty());
      if (ObjectUtils.isNotEmpty(adjustmentValueUom)
          && ReceivingConstants.EACHES.equalsIgnoreCase(adjustmentValueUom)) {
        receipt.setFbDamagedQtyUOM(Uom.EACHES);
      }
      receipt.setFbDamagedClaimType(VDM_CLAIM_TYPE);
      receipt.setFbDamagedReasonCode(OSDRCode.D53);
    } else if (reasonCode.equals(RCS_CONCEALED_SHORTAGE_REASON_CODE)) {
      receipt.setFbConcealedShortageQty(-1 * adjustmentValue);
      receipt.setEachQty(-receipt.getFbConcealedShortageQty());
      receipt.setFbConcealedShortageReasonCode(OSDRCode.S54);
    }
    return receipt;
  }

  private Receipt createOSDRMasterReceiptForRIPNegativeDamaged(
      Receipt receipt,
      Integer reasonCode,
      Long deliveryNumber,
      String purchaseRefNumber,
      Integer purchaseRefLineNumber,
      Integer adjustmentValue,
      String adjustmentValueUom) {
    Optional<Receipt> osdrMasterReceiptOptional =
        osdrRecordCountAggregator.findOsdrMasterReceipt(
            deliveryNumber, purchaseRefNumber, purchaseRefLineNumber);
    Receipt osdrMasterReceipt = null;
    if (osdrMasterReceiptOptional.isPresent()) {
      osdrMasterReceipt = osdrMasterReceiptOptional.get();
      if (reasonCode.equals(VDM_REASON_CODE)) {
        osdrMasterReceipt.setFbDamagedQty(
            (int) ReceivingUtils.applyDefaultValue(osdrMasterReceipt.getFbDamagedQty())
                + (-1 * adjustmentValue));
        osdrMasterReceipt.setFbDamagedClaimType(VDM_CLAIM_TYPE);
        if (ObjectUtils.isNotEmpty(adjustmentValueUom)
            && ReceivingConstants.EACHES.equalsIgnoreCase(adjustmentValueUom)) {
          osdrMasterReceipt.setFbDamagedQtyUOM(ReceivingConstants.Uom.EACHES);
        }
        osdrMasterReceipt.setFbDamagedReasonCode(OSDRCode.D53);
      } else if (reasonCode.equals(RCS_CONCEALED_SHORTAGE_REASON_CODE)) {
        osdrMasterReceipt.setFbConcealedShortageQty(
            (int) (ReceivingUtils.applyDefaultValue(osdrMasterReceipt.getFbConcealedShortageQty()))
                + (-1 * adjustmentValue));
        osdrMasterReceipt.setFbConcealedShortageReasonCode(null);
      }
    } else {
      osdrMasterReceipt = SerializationUtils.clone(receipt);
      if (reasonCode.equals(RCS_CONCEALED_SHORTAGE_REASON_CODE)) {
        osdrMasterReceipt.setFbConcealedShortageReasonCode(null);
      }
      osdrMasterReceipt.setOsdrMaster(1);
    }
    return osdrMasterReceipt;
  }
  /**
   * Create and publish container in case of S2S
   *
   * @param instruction
   * @param gdmContainerResponse
   * @param headers
   */
  @Transactional
  @InjectTenantFilter
  public Container createAndPublishContainersForS2S(
      Instruction instruction,
      ContainerResponseData gdmContainerResponse,
      Map<String, Object> headers) {
    Container parentContainer = populateContainerForS2S(instruction, gdmContainerResponse, true);
    parentContainer.setCreateUser(headers.get(USER_ID_HEADER_KEY).toString());
    parentContainer.setCompleteTs(new Date());
    parentContainer.setPublishTs(new Date());
    parentContainer.setLastChangedUser(headers.get(USER_ID_HEADER_KEY).toString());
    containerPersisterService.saveContainer(parentContainer);
    Set<Container> childContainers = new HashSet<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    if (!CollectionUtils.isEmpty(gdmContainerResponse.getContainers())) {
      Container childContainer = null;
      for (ContainerResponseData gdmChildContainerResponse : gdmContainerResponse.getContainers()) {
        childContainer = populateContainerForS2S(instruction, gdmChildContainerResponse, false);
        containerItems.addAll(childContainer.getContainerItems());
        childContainers.add(childContainer);
      }
      List<Container> childContainerList = new ArrayList<>();
      childContainerList.addAll(childContainers);
      containerPersisterService.saveContainerAndContainerItems(childContainerList, containerItems);
    }
    parentContainer.setChildContainers(childContainers);
    publishContainer(parentContainer, headers);
    return parentContainer;
  }

  /**
   * Populate container and container item for S2S
   *
   * @param instruction
   * @param gdmContainerResponse
   * @param isParent
   * @return container
   */
  private Container populateContainerForS2S(
      Instruction instruction, ContainerResponseData gdmContainerResponse, boolean isParent) {
    Container container = new Container();
    container.setInstructionId(instruction.getId());
    container.setMessageId(instruction.getMessageId());
    container.setTrackingId(gdmContainerResponse.getLabel());
    container.setWeightUOM(gdmContainerResponse.getWeightUOM());
    container.setCube(gdmContainerResponse.getCube());
    container.setCubeUOM(gdmContainerResponse.getCubeUOM());
    container.setDestination(instruction.getContainer().getCtrDestination());
    container.setInventoryStatus(instruction.getContainer().getInventoryStatus());
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    container.setParentTrackingId(isParent ? "" : instruction.getContainer().getTrackingId());
    container.setCreateTs(new Date());
    if (isParent) {
      container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
      container.setCtrReusable(instruction.getContainer().getCtrReusable());
      container.setCtrShippable(instruction.getContainer().getCtrShippable());
    }
    List<ContainerItem> containerItems = new ArrayList<>();
    if (!CollectionUtils.isEmpty(gdmContainerResponse.getItems())) {
      for (ContainerItemResponseData gdmContainerItemResponse : gdmContainerResponse.getItems()) {
        ContainerItem containerItem = new ContainerItem();
        ContainerPOResponseData purchaseOrder = gdmContainerItemResponse.getPurchaseOrder();
        containerItem.setPurchaseReferenceNumber(purchaseOrder.getPurchaseReferenceNumber());
        containerItem.setPurchaseReferenceLineNumber(
            purchaseOrder.getPurchaseReferenceLineNumber());
        containerItem.setItemNumber(Long.valueOf(gdmContainerItemResponse.getItemNumber()));

        if (isParent) {
          containerItem.setQuantity(
              ReceivingUtils.conversionToEaches(
                  gdmContainerItemResponse.getItemQuantity(),
                  gdmContainerItemResponse.getQuantityUOM(),
                  1,
                  1));
        } else {
          container.setParentTrackingId(instruction.getContainer().getTrackingId());
          containerItem.setQuantity(purchaseOrder.getVendorPackQuantity());
        }

        containerItem.setGtin(gdmContainerItemResponse.getItemUpc());
        containerItem.setInboundChannelMethod(gdmContainerResponse.getChannel());
        containerItem.setOutboundChannelMethod(
            instruction.getContainer().getOutboundChannelMethod());
        containerItem.setTrackingId(gdmContainerResponse.getLabel());
        containerItem.setBaseDivisionCode(gdmContainerItemResponse.getBaseDivCode());
        containerItem.setFinancialReportingGroupCode(purchaseOrder.getFinancialGroupCode());
        containerItem.setVnpkQty(purchaseOrder.getVendorPackQuantity());
        containerItem.setWhpkQty(purchaseOrder.getWarehousePackQuantity());
        containerItem.setWhpkSell(purchaseOrder.getWarehousePackSell());
        containerItems.add(containerItem);
      }
    }

    container.setContainerItems(containerItems);
    container.setDeliveryNumber(instruction.getDeliveryNumber());
    Map<String, String> facility = new HashMap<>();
    facility.put("buNumber", instruction.getFacilityNum().toString());
    facility.put("countryCode", instruction.getFacilityCountryCode());
    container.setFacility(facility);
    return container;
  }

  /**
   * This method is deleting containers which are created for integration test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public void deleteContainers(Long deliveryNumber) throws ReceivingException {
    List<Container> containerList = getContainerByDeliveryNumber(deliveryNumber);
    if (containerList == null || containerList.isEmpty()) {
      throw new ReceivingException(
          "no record's found for this delivery number in container table", HttpStatus.NOT_FOUND);
    }
    for (Container container : containerList) {
      containerItemRepository.deleteAll(container.getContainerItems());
    }
    containerRepository.deleteAll(containerList);
  }

  /**
   * This method will delete containers by PO and PoLine which are created for integration test
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public void deleteContainersByPoAndPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    List<String> trackingIdList =
        containerItemRepository.getTrackingIdByPoAndPoLine(
            purchaseReferenceNumber, purchaseReferenceLineNumber);
    List<String> parentTrackingIdList =
        containerRepository.getParentTrackingIdByPoAndPoLine(
            purchaseReferenceNumber, purchaseReferenceLineNumber);
    if (CollectionUtils.isEmpty(trackingIdList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PO_POLINE_ERROR_MSG,
              purchaseReferenceNumber,
              purchaseReferenceLineNumber);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }
    List<List<String>> partitionedTrackingIdList = ListUtils.partition(trackingIdList, 100);
    for (List<String> trackingIds : partitionedTrackingIdList) {
      containerItemRepository.deleteByTrackingIdIn(trackingIds);
      containerRepository.deleteByTrackingIdIn(trackingIds);
    }
    if (!CollectionUtils.isEmpty(parentTrackingIdList)) {
      List<List<String>> partitionedParentTrackingIdList =
          ListUtils.partition(parentTrackingIdList, 100);
      for (List<String> parentTrackingIds : partitionedParentTrackingIdList) {
        containerRepository.deleteByTrackingIdIn(parentTrackingIds);
      }
    }
  }

  /**
   * This method will get container details by instruction id for integration test
   *
   * @param instructionId
   * @return
   * @throws ReceivingException
   */
  // TODO: Need to look into a better approach to get ContainerItems instead of making a call for 1
  // Id in a FOR loop
  @Transactional
  @InjectTenantFilter
  public List<Container> getContainerByInstruction(Long instructionId) throws ReceivingException {
    List<Container> containerList = containerRepository.findByInstructionId(instructionId);
    if (containerList == null || containerList.isEmpty()) {
      throw new ReceivingException(
          "no record's found for this instruction id in container table", HttpStatus.NOT_FOUND);
    }

    for (Container container : containerList) {
      container.setContainerItems(
          containerItemRepository.findByTrackingId(container.getTrackingId()));
    }
    return containerList;
  }

  @Transactional
  @InjectTenantFilter
  public List<Container> getContainerByDeliveryNumber(Long deliveryNumber)
      throws ReceivingException {
    List<Container> containerList =
        containerPersisterService.getContainerByDeliveryNumber(deliveryNumber);
    if (CollectionUtils.isEmpty(containerList)) {
      throw new ReceivingException(
          "no record's found for this delivery number in container table", HttpStatus.NOT_FOUND);
    }
    return containerList;
  }

  /**
   * Prepare container object using container request
   *
   * @param deliveryNumber
   * @param containerRequest
   * @param userId
   * @return Container
   */
  public Container prepareContainer(
      Long deliveryNumber, ContainerRequest containerRequest, String userId) {
    Container container = new Container();
    container.setTrackingId(containerRequest.getTrackingId());
    container.setMessageId(containerRequest.getMessageId());
    container.setLocation(containerRequest.getLocation());
    container.setDeliveryNumber(deliveryNumber);
    container.setFacility(containerRequest.getFacility());
    container.setContainerType(containerRequest.getCtrType());
    container.setContainerStatus(containerRequest.getCtrStatus());
    container.setWeight(containerRequest.getCtrWght());
    container.setWeightUOM(containerRequest.getCtrWghtUom());
    container.setCube(containerRequest.getCtrCube());
    container.setCubeUOM(containerRequest.getCtrCubeUom());
    container.setCtrShippable(containerRequest.getCtrShippable());
    container.setCtrReusable(containerRequest.getCtrReusable());
    container.setInventoryStatus(containerRequest.getInventoryStatus());
    if (isNotBlank(containerRequest.getOrgUnitId()))
      container.setSubcenterId(
          isNotBlank(containerRequest.getOrgUnitId())
              ? Integer.valueOf(containerRequest.getOrgUnitId())
              : null);
    container.setCreateUser(userId);
    container.setCreateTs(new Date());
    container.setLastChangedUser(userId);
    container.setLastChangedTs(new Date());
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());
    if (containerRequest.getContents() == null || containerRequest.getContents().isEmpty()) {
      container.setContainerItems(null);
    } else {
      List<ContainerItem> containerItems = new ArrayList<>();
      containerRequest
          .getContents()
          .forEach(
              content -> {
                ContainerItem containerItem = new ContainerItem();
                containerItem.setTrackingId(containerRequest.getTrackingId());
                containerItem.setPurchaseReferenceNumber(content.getPurchaseReferenceNumber());
                containerItem.setPurchaseReferenceLineNumber(
                    content.getPurchaseReferenceLineNumber());
                containerItem.setInboundChannelMethod(content.getInboundChannelMethod());
                containerItem.setOutboundChannelMethod(content.getOutboundChannelMethod());
                containerItem.setTotalPurchaseReferenceQty(content.getTotalPurchaseReferenceQty());
                containerItem.setPurchaseCompanyId(content.getPurchaseCompanyId());
                containerItem.setDeptNumber(content.getDeptNumber());
                containerItem.setPoDeptNumber(content.getPoDeptNumber());
                containerItem.setItemNumber(content.getItemNumber());
                containerItem.setGtin(content.getGtin());
                containerItem.setQuantity(
                    ReceivingUtils.conversionToEaches(
                        content.getQuantity(),
                        content.getQuantityUom(),
                        content.getVnpkQty(),
                        content.getWhpkQty()));
                containerItem.setVnpkQty(content.getVnpkQty());
                containerItem.setWhpkQty(content.getWhpkQty());
                containerItem.setVendorPackCost(content.getVendorPackCost());
                containerItem.setWhpkSell(content.getWhpkSell());
                containerItem.setBaseDivisionCode(
                    content.getBaseDivisionCode() != null
                        ? content.getBaseDivisionCode()
                        : ReceivingConstants.BASE_DIVISION_CODE);
                containerItem.setFinancialReportingGroupCode(
                    content.getFinancialReportingGroupCode() != null
                        ? content.getFinancialReportingGroupCode()
                        : getFacilityCountryCode());
                containerItem.setRotateDate(content.getRotateDate());
                containerItem.setDistributions(content.getDistributions());
                containerItem.setVnpkWgtQty(content.getVnpkWgtQty());
                containerItem.setVnpkWgtUom(content.getVnpkWgtQtyUom());
                containerItem.setVnpkcbqty(content.getVnpkCubeQty());
                containerItem.setVnpkcbuomcd(content.getVnpkCubeQtyUom());
                containerItem.setDescription(content.getDescription());
                containerItem.setSecondaryDescription(content.getSecondaryDescription());
                containerItem.setVendorNumber(content.getVendorNumber());
                containerItem.setLotNumber(content.getLotNumber());
                containerItem.setActualTi(content.getActualTi());
                containerItem.setActualHi(content.getActualHi());
                containerItem.setPackagedAsUom(content.getPackagedAsUom());
                containerItem.setPromoBuyInd(content.getPromoBuyInd());

                containerItems.add(containerItem);
              });
      container.setContainerItems(containerItems);
    }

    return container;
  }

  /**
   * @param pastNmins
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public List<Container> getContainerByTime(Integer pastNmins) throws ReceivingException {

    List<Container> containerList =
        containerRepository.findByCreateTsGreaterThanEqual(
            Date.from(Instant.now().minus(pastNmins, ChronoUnit.MINUTES)));
    return containerList;
  }

  /**
   * @param trackingId
   * @param includeChilds
   * @return
   * @throws ReceivingException
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerWithChildsByTrackingId(String trackingId, boolean includeChilds)
      throws ReceivingException {
    log.info(
        "Entering getContainerWithChildsByTrackingId() with trackingId {} includeChilds {}",
        trackingId,
        includeChilds);
    return getContainerWithChildsByTrackingId(trackingId, includeChilds, Uom.EACHES);
  }

  /**
   * @param trackingId
   * @param includeChilds
   * @param quantityUOM
   * @return
   * @throws ReceivingException
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerWithChildsByTrackingId(
      String trackingId, boolean includeChilds, String quantityUOM) throws ReceivingException {
    // automatedDc
    if (!configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false)) {
      validateTrackingId(trackingId);
    }

    Container container = getContainerByTrackingId(trackingId, quantityUOM);

    if (includeChilds) {

      container = getContainerIncludingChild(container, quantityUOM);

      if (!CollectionUtils.isEmpty(container.getChildContainers()))
        container.setHasChildContainers(true);
    } else {
      container.setChildContainers(Collections.emptySet());
    }

    log.info(
        "Exiting getContainerWithChildsByTrackingId() with trackingId {} includeChilds {}",
        trackingId,
        includeChilds);

    return container;
  }

  /**
   * @param trackingId
   * @return
   * @throws ReceivingException
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerByTrackingId(String trackingId) throws ReceivingException {
    log.info("Entering getContainerByTrackingId() with trackingId: {}", trackingId);
    return getContainerByTrackingId(trackingId, Uom.EACHES);
  }

  /**
   * @param trackingIds
   * @return
   * @throws ReceivingException
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Set<Container> getContainerListByTrackingIdList(List<String> trackingIds)
      throws ReceivingException {
    log.info("Entering getContainerListByTrackingIdList() with trackingIds: {}", trackingIds);
    return containerRepository.findByTrackingIdIn(trackingIds);
  }

  /**
   * @param trackingId
   * @return
   * @throws ReceivingException
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerByTrackingId(String trackingId, String quantityUOM)
      throws ReceivingException {

    Container container = containerRepository.findByTrackingId(trackingId);
    log.info(
        "getContainerByTrackingId lpn={}, deliveryNumber={}",
        trackingId,
        container != null ? container.getDeliveryNumber() : null);

    if (container == null) {
      log.error("no container found for lpn={}", trackingId);
      throw new ReceivingException(
          LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
          NOT_FOUND,
          LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
          LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER);
    }

    List<ContainerItem> containerItems =
        containerItemRepository.findByTrackingId(container.getTrackingId());
    if (!CollectionUtils.isEmpty(containerItems)) {
      ContainerItem containerItem = containerItems.get(0);
      if (!ObjectUtils.isEmpty(containerItem)) {
        switch (quantityUOM) {
          case VNPK:
            containerItem.setQuantity(containerItem.getQuantity() / containerItem.getVnpkQty());
            break;
          case WHPK:
            containerItem.setQuantity(containerItem.getQuantity() / containerItem.getWhpkQty());
            break;
          default:
            break;
        }
        containerItem.setQuantityUOM(quantityUOM);
      }
    }
    container.setContainerItems(containerItems);
    return container;
  }

  /**
   * Adjusts the container with given quantity and generates a printJob with given printerId value
   *
   * @param trackingId
   * @param containerUpdateRequest
   * @param httpHeaders
   * @return ContainerUpdateResponse having updated Container and printJob
   * @throws ReceivingException
   */
  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId, ContainerUpdateRequest containerUpdateRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    UpdateContainerQuantityRequestHandler updateContainerQuantityHandler =
        configUtils.getConfiguredInstance(
            getFacilityNum().toString(),
            UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER_KEY,
            UpdateContainerQuantityRequestHandler.class);

    return updateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId, containerUpdateRequest, httpHeaders);
  }

  public void isBackoutContainer(String trackingId, String containerStatus)
      throws ReceivingException {
    if (ReceivingConstants.STATUS_BACKOUT.equals(containerStatus)) {
      log.error("Container is already backed out, so can't adjust quantity for lpn={}", trackingId);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG_CONTAINER_BACKOUT,
          BAD_REQUEST,
          ExceptionCodes.CONTAINER_IS_ALREADY_BACKED_OUT,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER_CONTAINER_BACKOUT);
    }
  }

  public void adjustQuantityInInventoryService(
      String cId,
      String trackingId,
      Integer diffQuantityInVnpk_INV,
      HttpHeaders httpHeaders,
      ContainerItem containerItem,
      Integer initialQtyInEa)
      throws ReceivingException {

    Integer diffQuantityInEaches_INV =
        ReceivingUtils.conversionToEaches(
            diffQuantityInVnpk_INV, VNPK, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    adjustQuantityByEachesInInventoryService(
        cId, trackingId, diffQuantityInEaches_INV, httpHeaders, containerItem, initialQtyInEa);
  }

  public void adjustQuantityByEachesInInventoryService(
      String cId,
      String trackingId,
      Integer adjustByInEa,
      HttpHeaders httpHeaders,
      ContainerItem ci,
      Integer initialQtyInEa)
      throws ReceivingException {
    String url;
    String request;
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    ResponseEntity<String> response;
    if (configUtils.getConfiguredFeatureFlag(getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_ITEMS_URI_ADJUST_V2;
      request =
          inventoryRestApiClient.createInventoryAdjustRequest(
              trackingId,
              ci.getItemNumber().toString(),
              ci.getBaseDivisionCode(),
              ci.getFinancialReportingGroupCode(),
              adjustByInEa,
              initialQtyInEa);
      httpHeaders.add(IDEM_POTENCY_KEY, cId + "-" + trackingId);
      httpHeaders.add(FLOW_NAME, ADJUSTMENT_FLOW);
      log.info(
          "step:8 cId={}, adjust inventory Service for Request={}, Headers={}",
          cId,
          request,
          httpHeaders);
      response = restUtils.post(url, httpHeaders, new HashMap<>(), request);
    } else {
      url = appConfig.getInventoryBaseUrl() + INVENTORY_ITEMS_URI;
      request = getInvAdjustmentV1Request(trackingId, adjustByInEa, ci);
      log.info(
          "step:8 cId={}, adjust inventory Service for Request={}, Headers={}",
          cId,
          request,
          httpHeaders);
      response = restUtils.put(url, httpHeaders, new HashMap<>(), request);
    }
    if (OK != response.getStatusCode()) {
      log.error(
          "error calling inventory Service for url={}  Request={}, response={}, Headers={}",
          url,
          request,
          response,
          httpHeaders);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
  }

  private String getInvAdjustmentV1Request(
      String trackingId, Integer diffQuantityInEaches_INV, ContainerItem containerItem) {
    String jsonRequest;
    Map<String, Object> keyValue = new LinkedHashMap<>();
    keyValue.put(INVENTORY_ADJUSTMENT_TRACKING_ID, trackingId);
    keyValue.put(ITEM_NUMBER, containerItem.getItemNumber());
    keyValue.put(INVENTORY_BASE_DIVISION_CODE, containerItem.getBaseDivisionCode());
    keyValue.put(INVENTORY_ADJUSTMENT_QTY_UOM, containerItem.getQuantityUOM());
    keyValue.put(
        INVENTORY_FINANCIAL_REPORTING_GROUP, containerItem.getFinancialReportingGroupCode());
    keyValue.put(INVENTORY_ADJUST_BY, diffQuantityInEaches_INV);
    keyValue.put(INVENTORY_ADJUSTMENT_REASON_CODE, INVENTORY_RECEIVING_CORRECTION_REASON_CODE);
    jsonRequest = gson.toJson(keyValue);
    return jsonRequest;
  }

  public void postFinalizePoOsdrToGdm(
      HttpHeaders httpHeaders,
      Long deliveryNumber,
      String purchaseReferenceNumber,
      FinalizePORequestBody finalizePORequestBody)
      throws ReceivingException, GDMRestApiClientException {
    log.info(
        "step:8 cId={}, postFinalizePoOsdrToGdm for deliveryNumber={}, purchaseReferenceNumber={}",
        httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
        deliveryNumber,
        purchaseReferenceNumber);
    final Map<String, Object> forwardablHeaders = getForwardablHeaderWithTenantData(httpHeaders);
    finalizePORequestBody.setFinalizedTime(new Date());
    finalizePORequestBody.setUserId(forwardablHeaders.get(USER_ID_HEADER_KEY).toString());
    finalizePORequestBody.setReasonCode(RECEIVING_CORRECTION);

    gdmRestApiClient.persistFinalizePoOsdrToGdm(
        deliveryNumber, purchaseReferenceNumber, finalizePORequestBody, forwardablHeaders);
  }

  public ContainerItem getContainerItem(String cId, Container container) throws ReceivingException {
    log.info("step:1 cId={} containerItem", cId);

    final List<ContainerItem> containerItemList = container.getContainerItems();

    if (CollectionUtils.isEmpty(containerItemList)) {
      log.error("cId={}, getContainerItem containerItemList is null or empty ", cId);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
    return containerItemList.get(0);
  }

  public Integer adjustContainerItemQuantityAndGetDiff(
      String cId,
      Integer newQuantityInVNPK_UI,
      ContainerUpdateResponse response,
      Container container,
      ContainerItem containerItem,
      Integer quantityInVnpk_INV)
      throws ReceivingException {
    log.info("step:4 cId={} adjust Quantity In Container", cId);

    Integer newQuantityInEaches_UI =
        ReceivingUtils.conversionToEaches(
            newQuantityInVNPK_UI, VNPK, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    final Integer quantityInEaches_INV =
        ReceivingUtils.conversionToEaches(
            quantityInVnpk_INV, VNPK, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    final Integer quantityInEaches_RCV = containerItem.getQuantity();
    Integer diffQuantityInEaches_RCV;
    Integer newQuantityInEaches_RCV;
    final boolean isQuantitySyncRcvAndInv = (quantityInEaches_RCV - quantityInEaches_INV) == 0;
    if (isQuantitySyncRcvAndInv) {
      diffQuantityInEaches_RCV = newQuantityInEaches_UI - quantityInEaches_RCV;
      newQuantityInEaches_RCV = newQuantityInEaches_UI;
    } else {
      // TODO if intial rcv=12, inv=9, newQty/AdjQty=9, newInvDiff=-9; later RCV=1(12-9)>no error
      log.info(
          "Quantity is out of sync between Inventory={} and Receiving={} in eaches",
          quantityInEaches_INV,
          quantityInEaches_RCV);
      diffQuantityInEaches_RCV = newQuantityInEaches_UI - quantityInEaches_INV;
      newQuantityInEaches_RCV = quantityInEaches_RCV + diffQuantityInEaches_RCV;
    }

    if (diffQuantityInEaches_RCV == 0) {
      log.error(
          "requested newQuantityInEaches_UI={} is same as quantityInEaches_RCV={}",
          newQuantityInEaches_UI,
          quantityInEaches_RCV);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_SAME_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }

    // containerItem, container updates
    container = adjustContainerByQty(false, container, newQuantityInEaches_RCV);
    containerItem = container.getContainerItems().get(0);
    containerItemRepository.save(containerItem);
    // 0 new quantity will delete in inventory so mark as backout
    if (newQuantityInVNPK_UI == 0) {
      log.info("cId={}, pallet correction to 0 sets container to backout i.e lpn-cancel", cId);
      container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    }
    containerPersisterService.saveContainer(container);

    response.setContainer(container);
    return diffQuantityInEaches_RCV;
  }

  @Transactional
  @InjectTenantFilter
  public void adjustQuantityInReceiptUseInventoryData(
      String cId,
      Integer newQuantityInVnpk_UI,
      Container container,
      ContainerItem containerItem,
      Integer freightBillQty,
      Integer diffQuantityInEaches_RCV,
      Receipt masterReceipt)
      throws ReceivingException {

    Long deliveryNumber = container.getDeliveryNumber();
    log.info(
        "step:5 cId={}, adjust Receipts newQuantityInVnpk_UI={},deliveryNumber={}, freightBillQty={}, diffQuantityInEaches_RCV={}",
        cId,
        newQuantityInVnpk_UI,
        deliveryNumber,
        freightBillQty,
        diffQuantityInEaches_RCV);

    if (isNull(newQuantityInVnpk_UI)
        || isNull(deliveryNumber)
        || isNull(containerItem)
        || isNull(containerItem.getPurchaseReferenceNumber())
        || isNull(containerItem.getPurchaseReferenceLineNumber())) {
      log.error(
          "error in adjustQuantityInReceiptUseInventoryData quantity or deliveryNumber or purchase ref or ref line number null");
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
    final String purchaseReferenceNumber = containerItem.getPurchaseReferenceNumber();
    final Integer purchaseReferenceLineNumber = containerItem.getPurchaseReferenceLineNumber();

    Receipt newReceiptForDiff =
        createDiffReceipt(
            container,
            containerItem,
            diffQuantityInEaches_RCV,
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            masterReceipt);

    receiptService.saveReceipt(newReceiptForDiff);

    Long revisedReceiveQty =
        receiptService.receivedQtyByDeliveryPoAndPoLine(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
    updateReceiptWithOsdrCalc(revisedReceiveQty.intValue(), freightBillQty, masterReceipt);

    receiptService.saveReceipt(masterReceipt);
  }

  public void validatePOClose(
      ContainerItem containerItem,
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Receipt masterReceipt)
      throws ReceivingException {
    Map<String, String> containerItemMiscInfo = containerItem.getContainerItemMiscInfo();
    boolean isOSSTransfer =
        Objects.nonNull(containerItemMiscInfo)
            && containerItemMiscInfo.containsKey(IS_RECEIVE_FROM_OSS)
            && Boolean.valueOf(containerItemMiscInfo.get(IS_RECEIVE_FROM_OSS));

    log.info("DCFin: check PO is Finalized");
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false)) {

      if (!dcFinRestApiClient.isPoFinalizedInDcFin(
          deliveryNumber.toString(), purchaseReferenceNumber)) {
        log.error(
            "PO is not Finalized in DCFin for deliveryNumber={}, poNumber={}",
            deliveryNumber,
            purchaseReferenceNumber);
        throw new ReceivingException(
            String.format(
                ADJUST_PALLET_QUANTITY_ERROR_MSG_PO_NOT_FINALIZE_DCFIN, purchaseReferenceNumber),
            BAD_REQUEST,
            ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
            ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      } else {
        throwIfOSSTransferPO(isOSSTransfer, purchaseReferenceNumber);
      }
    } else if (isNull(masterReceipt)
        || isNull(masterReceipt.getFinalizedUserId())
        || isNull(masterReceipt.getFinalizeTs())) {
      log.error(
          "PO is not Finalized for deliveryNumber={}, poNumber={}, masterReceipt={}",
          deliveryNumber,
          purchaseReferenceNumber,
          masterReceipt);
      throw new ReceivingException(
          String.format(ADJUST_PALLET_QUANTITY_ERROR_MSG_PO_NOT_FINALIZE, purchaseReferenceNumber),
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
    } else {
      throwIfOSSTransferPO(isOSSTransfer, purchaseReferenceNumber);
    }
  }

  private void throwIfOSSTransferPO(boolean isOSSTransfer, String purchaseReferenceNumber) {
    if (isOSSTransfer) {
      log.error(
          "Block Receiving correction as PO {} Finalized and OSS Transfer",
          purchaseReferenceNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REQUEST,
          ReceivingException.OSS_TRANSFER_PO_FINALIZED_CORRECTION_ERROR);
    }
  }

  private Receipt createDiffReceipt(
      Container container,
      ContainerItem containerItem,
      Integer diffQuantityInEaches,
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      Receipt masterReceipt) {
    Receipt adjustedReceipt = new Receipt();
    adjustedReceipt.setCreateTs(Date.from(Instant.now()));
    adjustedReceipt.setDeliveryNumber(deliveryNumber);
    adjustedReceipt.setDoorNumber(container.getLocation());
    adjustedReceipt.setEachQty(diffQuantityInEaches);
    adjustedReceipt.setFacilityCountryCode(containerItem.getFacilityCountryCode());
    adjustedReceipt.setFacilityNum(containerItem.getFacilityNum());
    adjustedReceipt.setFinalizedUserId(masterReceipt.getFinalizedUserId());
    adjustedReceipt.setFinalizeTs(masterReceipt.getFinalizeTs());
    adjustedReceipt.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);
    adjustedReceipt.setPurchaseReferenceNumber(purchaseReferenceNumber);
    // TODO adjustedReceipt.setCreateUserId();//inventory or receiving

    int differenceOfQuantityInVendorPack =
        ReceivingUtils.conversionToVendorPack(
            diffQuantityInEaches,
            containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(),
            containerItem.getWhpkQty());

    adjustedReceipt.setQuantity(differenceOfQuantityInVendorPack);
    adjustedReceipt.setQuantityUom(VNPK);
    adjustedReceipt.setVnpkQty(containerItem.getVnpkQty());
    adjustedReceipt.setWhpkQty(containerItem.getWhpkQty());
    return adjustedReceipt;
  }

  private void updateReceiptWithOsdrCalc(
      Integer receivedQty, Integer freightBillQty, Receipt masterReceipt) {
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setReceiveQty(receivedQty);
    receivingCountSummary.setProblemQty(masterReceipt.getFbProblemQty());
    receivingCountSummary.setDamageQty(masterReceipt.getFbDamagedQty());
    receivingCountSummary.setRejectedQty(masterReceipt.getFbRejectedQty());
    receivingCountSummary.setTotalFBQty(freightBillQty);
    osdrCalculator.calculate(receivingCountSummary);
    masterReceipt.setFbOverQty(receivingCountSummary.getOverageQty());
    masterReceipt.setFbOverQtyUOM(receivingCountSummary.getOverageQtyUOM());
    masterReceipt.setFbShortQty(receivingCountSummary.getShortageQty());
    masterReceipt.setFbShortQtyUOM(receivingCountSummary.getShortageQtyUOM());
  }

  /**
   * Validate the receiving correction
   *
   * @param deliveryNumber
   * @param cId
   * @param newQuantityInVnpk_UI
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public void adjustQuantityValidation(
      Long deliveryNumber,
      String cId,
      Integer newQuantityInVnpk_UI,
      DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    log.info(
        "step:3 cId={}, adjust Quantity Validation, newQuantityInVnpk_UI={}",
        cId,
        newQuantityInVnpk_UI);

    Integer actualTi = deliveryDocumentLine.getPalletTie();
    Integer actualHi = deliveryDocumentLine.getPalletHigh();

    // Enrich the PalletTi from local DB if it's available.
    Optional<DeliveryItemOverride> itemOverrideOptional =
        deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            deliveryNumber, deliveryDocumentLine.getItemNbr());
    if (itemOverrideOptional.isPresent()) {
      DeliveryItemOverride itemOverride = itemOverrideOptional.get();
      actualTi = itemOverride.getTempPalletTi();
      if (Objects.nonNull(itemOverride.getTempPalletHi())) {
        actualHi = itemOverride.getTempPalletHi();
      }
    }

    if (isNull(actualHi) || isNull(actualTi) || newQuantityInVnpk_UI > (actualHi * actualTi)) {
      log.error(
          "Ti Hi check failed newQuantityInVnpk_UI={}, actualTi={} , actualHi={}",
          newQuantityInVnpk_UI,
          actualTi,
          actualHi);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_TIHI_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_TIHI_ERROR_HEADER);
    }
  }

  /**
   * calls PUTAWAY service call to Quantity Adjustment To Witron
   *
   * @param cId
   * @param newQuantityInVnpk_UI
   * @param httpHeaders
   * @param updatedContainer
   * @param facilityNum
   * @param quantityInVnkp_UI
   * @param isQuantitySync_RCV_vs_INV
   * @throws ReceivingException
   */
  public void adjustQuantityInPutawayService(
      String cId,
      Integer newQuantityInVnpk_UI,
      HttpHeaders httpHeaders,
      Container initialContainer,
      Container updatedContainer,
      String facilityNum,
      Integer quantityInVnkp_UI,
      boolean isQuantitySync_RCV_vs_INV)
      throws ReceivingException {
    log.info(
        "step:7 cId={}, PutawayService for={}, newQuantity={} vnpk, quantity={} vnkp, is RCV & INV sync={}",
        cId,
        facilityNum,
        newQuantityInVnpk_UI,
        quantityInVnkp_UI,
        isQuantitySync_RCV_vs_INV);

    if (isQuantitySync_RCV_vs_INV) {
      if (newQuantityInVnpk_UI == 0) {
        gdcPutawayPublisher.publishMessage(initialContainer, PUTAWAY_DELETE_ACTION, httpHeaders);
      } else {
        gdcPutawayPublisher.publishMessage(updatedContainer, PUTAWAY_UPDATE_ACTION, httpHeaders);
      }
    } else {

      Container updatedContainer_RTU = SerializationUtils.clone(updatedContainer);
      ContainerItem cItem = updatedContainer.getContainerItems().get(0);
      Integer newQuantityInEaches =
          ReceivingUtils.conversionToEaches(
              newQuantityInVnpk_UI == 0 ? quantityInVnkp_UI : newQuantityInVnpk_UI,
              VNPK,
              cItem.getVnpkQty(),
              cItem.getWhpkQty());
      updatedContainer_RTU = adjustContainerByQty(true, updatedContainer_RTU, newQuantityInEaches);

      if (newQuantityInVnpk_UI == 0) {
        gdcPutawayPublisher.publishMessage(
            updatedContainer_RTU, PUTAWAY_DELETE_ACTION, httpHeaders);
      } else {
        gdcPutawayPublisher.publishMessage(
            updatedContainer_RTU, PUTAWAY_UPDATE_ACTION, httpHeaders);
      }
    }
  }

  /**
   * @param cId
   * @param instructionId
   * @return Instruction
   */
  public Instruction getInstruction(String cId, Long instructionId) throws ReceivingException {
    log.info("step:2 cId={}, instruction Id={} ", cId, instructionId);

    Optional<Instruction> instructionOptional = instructionRepository.findById(instructionId);
    if (!instructionOptional.isPresent()) {
      log.error("getInstruction returned null or empty for instructionId={}", instructionId);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
    return instructionOptional.get();
  }

  /**
   * This will extract the container to be created from an instruction then it will construct the
   * parent ContainerModel.
   *
   * @param instruction
   * @param instructionRequest
   * @throws Exception
   */
  @Transactional
  @InjectTenantFilter
  public Container processCreateContainersForNonNationalPO(
      Instruction instruction, InstructionRequest instructionRequest, HttpHeaders headers) {
    Container container =
        constructContainerForNonNationalPo(
            instruction, instruction.getContainer(), instructionRequest);
    Date currentDate = new Date();
    container.setCompleteTs(currentDate);
    // updating publishTs
    container.setPublishTs(currentDate);
    container.setLastChangedTs(currentDate);
    container.setLastChangedUser(headers.getFirst(USER_ID_HEADER_KEY));

    return containerPersisterService.saveContainer(container);
  }

  @Transactional
  @InjectTenantFilter
  public Container processCreateContainersForWFSPO(
      Instruction instruction, InstructionRequest instructionRequest, HttpHeaders headers) {
    Container container = constructContainerForWFSPo(instruction, instructionRequest);
    Date currentDate = new Date();
    container.setCompleteTs(currentDate);
    container.setPublishTs(currentDate);
    container.setLastChangedTs(currentDate);
    container.setLastChangedUser(headers.getFirst(USER_ID_HEADER_KEY));
    return containerPersisterService.saveContainer(container);
  }

  @Transactional
  @InjectTenantFilter
  public Container processCreateContainersForWFSPOwithRIR(
      Instruction instruction,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders headers) {
    Container container = constructContainerForWFSPoRIR(instruction, receiveInstructionRequest);
    Date currentDate = new Date();
    container.setCompleteTs(currentDate);
    container.setPublishTs(currentDate);
    container.setLastChangedTs(currentDate);
    container.setLastChangedUser(headers.getFirst(USER_ID_HEADER_KEY));
    return containerPersisterService.saveContainer(container);
  }

  /**
   * @param parentTrackingId
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> getContainerByParentTrackingIdAndContainerStatus(
      String parentTrackingId, String containerStatus) {

    return containerRepository.findAllByParentTrackingIdAndContainerStatus(
        parentTrackingId, containerStatus);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Set<Container> getContainerByParentTrackingId(String trackingId) {
    return containerRepository.findAllByParentTrackingId(trackingId);
  }

  public List<Container> getContainers(
      String orderByColumnName, String sortOrder, int page, int limit, boolean parentOnly) {

    return containerPersisterService.getContainers(
        orderByColumnName, sortOrder, page, limit, parentOnly);
  }

  /**
   * This method will create container object for both parent and child Containers
   *
   * @param instruction
   * @param containerDtls
   * @param instructionRequest
   * @return ContainerModel
   */
  private Container constructContainerForNonNationalPo(
      Instruction instruction,
      ContainerDetails containerDtls,
      InstructionRequest instructionRequest) {

    List<DeliveryDocument> deliveryDocument = instructionRequest.getDeliveryDocuments();

    Container container = new Container();
    float consolidatedCubeQty = 0.0f;
    float consolidatedWeight = 0.0f;

    for (DeliveryDocument document : deliveryDocument) {
      float weight;
      float cube;
      if (Objects.nonNull(document.getPalletQty())
          && document.getPalletQty() > 0
          && document.getReceivedPalletCount() + 1 > document.getPalletQty()) {
        weight = 1F;
        cube = 1F;
      } else {
        weight =
            calculateAverageValue(
                document.getWeight(),
                document.getTotalPurchaseReferenceQty(),
                document.getQuantity());
        cube =
            calculateAverageValue(
                document.getCubeQty(),
                document.getTotalPurchaseReferenceQty(),
                document.getQuantity());
      }
      consolidatedWeight += weight;
      consolidatedCubeQty += cube;
    }

    consolidatedWeight = (consolidatedWeight < 1) ? 1 : consolidatedWeight;
    consolidatedCubeQty = (consolidatedCubeQty < 1) ? 1 : consolidatedCubeQty;

    container.setCube(consolidatedCubeQty);
    container.setWeight(consolidatedWeight);
    container.setMessageId(instruction.getMessageId());
    container.setInventoryStatus(containerDtls.getInventoryStatus());
    container.setCtrReusable(containerDtls.getCtrReusable());
    container.setCtrShippable(containerDtls.getCtrShippable());
    container.setTrackingId(containerDtls.getTrackingId());
    container.setInstructionId(instruction.getId());
    container.setLocation(instructionRequest.getDoorNumber());
    container.setContainerType(containerDtls.getCtrType());
    container.setActivityName(instruction.getActivityName());
    container.setLabelId(
        getLabelId(containerDtls.getOutboundChannelMethod(), containerDtls.getCtrType()));
    container.setContainerStatus(containerDtls.getCtrStatus());
    container.setDeliveryNumber(instruction.getDeliveryNumber());
    HashMap<String, String> facility = new HashMap<>();
    facility.put("buNumber", getFacilityNum().toString());
    facility.put("countryCode", getFacilityCountryCode());
    container.setFacility(facility);

    container.setDestination(containerDtls.getCtrDestination());

    container.setCreateUser(instruction.getCreateUserId());
    container.setOrgUnitId(
        nonNull(containerDtls.getOrgUnitId()) ? containerDtls.getOrgUnitId().toString() : null);

    if (Objects.nonNull(instruction.getOriginalChannel())) {
      Map<String, Object> containerMiscInfo = new HashMap<>();
      containerMiscInfo.put(LabelDataConstants.ORIGINAL_CHANNEL, instruction.getOriginalChannel());
      containerMiscInfo.put(ORIGIN_DC_NBR, facility.get(BU_NUMBER));
      container.setContainerMiscInfo(containerMiscInfo);
    }

    List<ContainerItem> containerItems = new ArrayList<>();
    deliveryDocument.forEach(
        document -> {
          ContainerItem containerItem = new ContainerItem();
          containerItem.setTrackingId(containerDtls.getTrackingId());
          containerItem.setPurchaseReferenceNumber(document.getPurchaseReferenceNumber());
          containerItem.setInboundChannelMethod(instructionRequest.getNonNationPo());
          containerItem.setOutboundChannelMethod(
              instruction.getContainer().getOutboundChannelMethod());
          containerItem.setTotalPurchaseReferenceQty(document.getTotalPurchaseReferenceQty());
          containerItem.setPurchaseCompanyId(Integer.parseInt(document.getPurchaseCompanyId()));
          containerItem.setBaseDivisionCode(document.getBaseDivisionCode());
          containerItem.setFinancialReportingGroupCode(document.getFinancialReportingGroup());
          containerItem.setVnpkQty(NON_NATIONAL_VNPK_WHPK_QTY);
          containerItem.setWhpkQty(NON_NATIONAL_VNPK_WHPK_QTY);
          containerItem.setPoTypeCode(document.getPoTypeCode());
          containerItem.setQuantity(document.getQuantity());
          containerItem.setItemNumber(DUMMY_ITEM_NUMBER);
          containerItem.setIsMultiPoPallet(deliveryDocument.size() > 1);

          ContainerUtils.setAttributesForImports(
              document.getPoDCNumber(),
              document.getPoDcCountry(),
              document.getImportInd(),
              containerItem);

          containerItems.add(containerItem);
        });
    container.setCubeUOM(deliveryDocument.get(0).getCubeUOM());
    container.setWeightUOM(deliveryDocument.get(0).getWeightUOM());

    container.setContainerItems(containerItems);
    return container;
  }

  public void setPackInformation(DeliveryDocument deliveryDocument, Container container) {
    if (Objects.nonNull(deliveryDocument.getAdditionalInfo())) {
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getPackId()))
        container.setSsccNumber(deliveryDocument.getAdditionalInfo().getPackId());
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getIsAuditRequired()))
        container.setIsAuditRequired(deliveryDocument.getAdditionalInfo().getIsAuditRequired());
    }
  }

  Container constructContainerForWFSPo(
      Instruction instruction, InstructionRequest instructionRequest) {

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    ContainerDetails containerDtls = instruction.getContainer();
    Container container = new Container();

    container.setMessageId(instructionRequest.getMessageId());
    container.setInventoryStatus(containerDtls.getInventoryStatus());
    container.setCtrReusable(containerDtls.getCtrReusable());
    container.setCtrShippable(containerDtls.getCtrShippable());
    container.setTrackingId(containerDtls.getTrackingId());
    container.setInstructionId(instruction.getId());
    container.setActivityName(instruction.getActivityName());

    container.setLocation(
        InventoryStatus.AVAILABLE.name().equals(containerDtls.getInventoryStatus())
            ? ReceivingConstants.ZERO_STRING
            : instructionRequest.getDoorNumber());
    container.setDeliveryNumber(Long.parseLong(instructionRequest.getDeliveryNumber()));
    container.setContainerType(containerDtls.getCtrType());

    container.setLabelId(getLabelId(instruction.getActivityName(), container.getContainerType()));
    container.setIsConveyable(
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsConveyable());
    container.setOnConveyor(Boolean.FALSE);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, instruction.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, instruction.getFacilityNum().toString());
    container.setFacility(facility);
    container.setDestination(containerDtls.getCtrDestination());

    container.setCreateUser(instruction.getCreateUserId());
    container.setChildContainers(new HashSet<Container>());
    container.setWeight(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getWeightQty())
            * instructionRequest.getEnteredQty());
    container.setWeightUOM(deliveryDocumentLine.getAdditionalInfo().getWeightQtyUom());
    container.setCube(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getCubeQty())
            * instructionRequest.getEnteredQty());
    container.setCubeUOM(deliveryDocumentLine.getAdditionalInfo().getCubeUomCode());

    setPackInformation(deliveryDocument, container);

    container.setContainerMiscInfo(getContainerMiscInfoForWFS(instructionRequest, container));

    container.setContainerItems(
        getContainerItemFromContainerDetailsForWFS(containerDtls, instructionRequest));

    return container;
  }

  public Map<String, Object> getContainerMiscInfoForWFS(
      InstructionRequest instructionRequest, Container container) {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    if (Objects.nonNull(instructionRequest.getAdditionalParams())
        && instructionRequest
            .getAdditionalParams()
            .containsKey(ReceivingConstants.IS_RE_RECEIVING_LPN_FLOW)) {
      log.info("Container LPN : {} is Re-received in ICC!", container.getTrackingId());
      containerMiscInfo.put(IS_RE_RECEIVING_CONTAINER, Boolean.TRUE);
      List<ContainerTag> containerTagList = new ArrayList<>();
      containerTagList.add(new ContainerTag(TWO_TIER, ReceivingConstants.CONTAINER_SET));
      container.setTags(containerTagList);
      /*
      - If Re-Receiving happens by scanning the lpn, then backout should be prevented during quantity corrections in receiving-web!
      - The Re-received container shouldn't be VTR'ed from INV-UI, so while publishing container adding tags to distinguish the flows!
          - The tags are utilized by Inventory-UI WEB, to disable the option of VTR!
      */
    }
    return containerMiscInfo;
  }

  private Container constructContainerForWFSPoRIR(
      Instruction instruction, ReceiveInstructionRequest receiveInstructionRequest) {

    DeliveryDocument deliveryDocument = InstructionUtils.getDeliveryDocument(instruction);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    ContainerDetails containerDtls = instruction.getContainer();
    Container container = new Container();

    container.setMessageId(instruction.getMessageId());
    container.setInventoryStatus(containerDtls.getInventoryStatus());
    container.setCtrReusable(containerDtls.getCtrReusable());
    container.setCtrShippable(containerDtls.getCtrShippable());
    container.setTrackingId(containerDtls.getTrackingId());
    container.setInstructionId(instruction.getId());
    container.setActivityName(instruction.getActivityName());

    container.setLocation(receiveInstructionRequest.getDoorNumber());
    container.setDeliveryNumber(instruction.getDeliveryNumber());
    container.setContainerType(containerDtls.getCtrType());

    container.setLabelId(getLabelId(instruction.getActivityName(), container.getContainerType()));
    container.setIsConveyable(Boolean.FALSE);
    container.setOnConveyor(Boolean.FALSE);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, instruction.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, instruction.getFacilityNum().toString());
    container.setFacility(facility);
    container.setDestination(containerDtls.getCtrDestination());

    container.setCreateUser(instruction.getCreateUserId());
    container.setChildContainers(new HashSet<Container>());
    container.setWeight(
        deliveryDocumentLine.getVendorPack()
            * receiveInstructionRequest.getQuantity()
            * Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getWeightQty()));
    container.setWeightUOM(deliveryDocumentLine.getAdditionalInfo().getWeightQtyUom());
    container.setCube(
        deliveryDocumentLine.getVendorPack()
            * receiveInstructionRequest.getQuantity()
            * Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getCubeQty()));
    container.setCubeUOM(deliveryDocumentLine.getAdditionalInfo().getCubeUomCode());

    container.setContainerItems(
        getContainerItemFromContainerDetailsForWFSRIR(
            containerDtls, instruction, receiveInstructionRequest));

    return container;
  }

  private List<ContainerItem> getContainerItemFromContainerDetailsForWFS(
      ContainerDetails containerDtls, InstructionRequest instructionRequest) {
    List<ContainerItem> containerItems = new ArrayList<>();

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(containerDtls.getTrackingId());
    containerItem.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    containerItem.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    containerItem.setInboundChannelMethod(deliveryDocumentLine.getPurchaseRefType());
    containerItem.setOutboundChannelMethod(containerDtls.getOutboundChannelMethod());
    containerItem.setTotalPurchaseReferenceQty(deliveryDocument.getTotalPurchaseReferenceQty());
    containerItem.setPurchaseCompanyId(
        NumberUtils.createInteger(deliveryDocument.getPurchaseCompanyId()));
    containerItem.setDeptNumber(
        (!StringUtils.isEmpty(deliveryDocument.getDeptNumber()))
            ? Integer.valueOf(deliveryDocument.getDeptNumber())
            : null);
    containerItem.setItemNumber(deliveryDocumentLine.getItemNbr());
    containerItem.setGtin(deliveryDocumentLine.getGtin());
    containerItem.setQuantity(instructionRequest.getEnteredQty());
    containerItem.setQuantityUOM(instructionRequest.getEnteredQtyUOM());
    containerItem.setItemUPC(deliveryDocumentLine.getItemUpc());
    containerItem.setCaseUPC(deliveryDocumentLine.getCaseUpc());

    containerItem.setVnpkQty(deliveryDocumentLine.getVendorPack());
    containerItem.setWhpkQty(deliveryDocumentLine.getWarehousePack());

    containerItem.setVendorPackCost(
        ReceivingUtils.parseFloatToDouble(deliveryDocumentLine.getVendorPackCost()));

    containerItem.setWhpkSell(
        ReceivingUtils.parseFloatToDouble(deliveryDocumentLine.getWarehousePackSell()));
    String baseDivCode =
        deliveryDocument.getBaseDivisionCode() != null
            ? deliveryDocument.getBaseDivisionCode()
            : ReceivingConstants.BASE_DIVISION_CODE;

    containerItem.setBaseDivisionCode(baseDivCode);

    String fRG =
        deliveryDocument.getFinancialReportingGroup() != null
            ? deliveryDocument.getFinancialReportingGroup()
            : getFacilityCountryCode();
    containerItem.setFinancialReportingGroupCode(fRG);

    containerItem.setDistributions(containerDtls.getDistributions());

    containerItem.setPoTypeCode(deliveryDocument.getPoTypeCode());
    containerItem.setPoDCNumber(deliveryDocument.getPoDCNumber());
    containerItem.setPoDcCountry(deliveryDocument.getPoDcCountry());
    containerItem.setPoDeptNumber(deliveryDocument.getDeptNumber());
    containerItem.setVnpkWgtQty(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getWeightQty())
            * deliveryDocumentLine.getVendorPack());
    containerItem.setVnpkWgtUom(deliveryDocumentLine.getAdditionalInfo().getWeightQtyUom());
    containerItem.setVnpkcbqty(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getCubeQty())
            * deliveryDocumentLine.getVendorPack());
    containerItem.setVnpkcbuomcd(deliveryDocumentLine.getAdditionalInfo().getCubeUomCode());
    containerItem.setDescription(deliveryDocumentLine.getDescription());
    containerItem.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());

    containerItem.setSellerId(deliveryDocument.getSellerId());
    containerItem.setSellerType(deliveryDocument.getSellerType());

    // set sellerTrustLevel
    if (Objects.nonNull(deliveryDocument)
        && Objects.nonNull(deliveryDocument.getAdditionalInfo())
        && Objects.nonNull(deliveryDocument.getAdditionalInfo().getReceivingTier()))
      containerItem.setSellerTrustLevel(
          deliveryDocument.getAdditionalInfo().getReceivingTier().toString());

    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IMAGE_URL, deliveryDocumentLine.getImageUrl());

    if (ReceivingType.GS1
        .getReceivingType()
        .equalsIgnoreCase(instructionRequest.getReceivingType())) {
      containerItemMiscInfo.put(
          ReceivingConstants.ITEM_BARCODE_VALUE,
          instructionRequest.getPreviouslyScannedDataList().get(0));
      containerItemMiscInfo.put(
          GS1_BARCODE, instructionRequest.getPreviouslyScannedDataList().get(0));
      List<ScannedData> scannedDataList = instructionRequest.getScannedDataList();
      for (ScannedData scannedData : scannedDataList) {
        if (scannedData
            .getApplicationIdentifier()
            .equalsIgnoreCase(ApplicationIdentifier.QTY.getApplicationIdentifier())) {
          containerItemMiscInfo.put(
              ReceivingConstants.EXPECTED_PACK_QUANTITY, scannedData.getValue());
          break;
        }
      }
    } else { // upc flow
      containerItemMiscInfo.put(
          ReceivingConstants.ITEM_BARCODE_VALUE, instructionRequest.getUpcNumber());
      if (!CollectionUtils.isEmpty(instructionRequest.getPreviouslyScannedDataList()))
        containerItemMiscInfo.put(
            GS1_BARCODE, instructionRequest.getPreviouslyScannedDataList().get(0));
    }

    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);

    containerItems.add(containerItem);

    return containerItems;
  }

  private List<ContainerItem> getContainerItemFromContainerDetailsForWFSRIR(
      ContainerDetails containerDtls,
      Instruction instruction,
      ReceiveInstructionRequest receiveInstructionRequest) {
    List<ContainerItem> containerItems = new ArrayList<>();

    DeliveryDocument deliveryDocument = InstructionUtils.getDeliveryDocument(instruction);
    DeliveryDocumentLine deliveryDocumentLine =
        receiveInstructionRequest.getDeliveryDocumentLines().get(0);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(containerDtls.getTrackingId());
    containerItem.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    containerItem.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    containerItem.setInboundChannelMethod(deliveryDocumentLine.getPurchaseRefType());
    containerItem.setOutboundChannelMethod(containerDtls.getOutboundChannelMethod());
    containerItem.setTotalPurchaseReferenceQty(deliveryDocument.getTotalPurchaseReferenceQty());
    containerItem.setPurchaseCompanyId(
        NumberUtils.createInteger(deliveryDocument.getPurchaseCompanyId()));
    containerItem.setDeptNumber(
        (!StringUtils.isEmpty(deliveryDocument.getDeptNumber()))
            ? Integer.valueOf(deliveryDocument.getDeptNumber())
            : null);
    containerItem.setItemNumber(deliveryDocumentLine.getItemNbr());
    containerItem.setGtin(deliveryDocumentLine.getGtin());
    containerItem.setItemUPC(deliveryDocumentLine.getItemUpc());
    containerItem.setCaseUPC(deliveryDocumentLine.getCaseUpc());

    // Now instruction receivedQty WILL be in ZA, so convert to EA for containerItem
    containerItem.setQuantity(
        ReceivingUtils.conversionToEaches(
            instruction.getReceivedQuantity(),
            ReceivingConstants.Uom.VNPK,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    containerItem.setVnpkQty(deliveryDocumentLine.getVendorPack());
    containerItem.setWhpkQty(deliveryDocumentLine.getWarehousePack());

    containerItem.setVendorPackCost(
        ReceivingUtils.parseFloatToDouble(deliveryDocumentLine.getVendorPackCost()));

    containerItem.setWhpkSell(
        ReceivingUtils.parseFloatToDouble(deliveryDocumentLine.getWarehousePackSell()));
    String baseDivCode =
        deliveryDocument.getBaseDivisionCode() != null
            ? deliveryDocument.getBaseDivisionCode()
            : ReceivingConstants.BASE_DIVISION_CODE;

    containerItem.setBaseDivisionCode(baseDivCode);

    String fRG =
        deliveryDocument.getFinancialReportingGroup() != null
            ? deliveryDocument.getFinancialReportingGroup()
            : getFacilityCountryCode();
    containerItem.setFinancialReportingGroupCode(fRG);

    containerItem.setDistributions(containerDtls.getDistributions());
    containerItem.setDistributions(getAllocatedDistribution(containerItem));

    containerItem.setPoTypeCode(deliveryDocument.getPoTypeCode());
    containerItem.setPoDCNumber(deliveryDocument.getPoDCNumber());
    containerItem.setPoDcCountry(deliveryDocument.getPoDcCountry());
    containerItem.setPoDeptNumber(deliveryDocument.getDeptNumber());
    containerItem.setVnpkWgtQty(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getWeightQty())
            * deliveryDocumentLine.getVendorPack());
    containerItem.setVnpkWgtUom(deliveryDocumentLine.getAdditionalInfo().getWeightQtyUom());
    containerItem.setVnpkcbqty(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getCubeQty())
            * deliveryDocumentLine.getVendorPack());
    containerItem.setVnpkcbuomcd(deliveryDocumentLine.getAdditionalInfo().getCubeUomCode());
    containerItem.setDescription(deliveryDocumentLine.getDescription());
    containerItem.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());

    containerItem.setSellerId(deliveryDocument.getSellerId());
    containerItem.setSellerType(deliveryDocument.getSellerType());

    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IMAGE_URL, deliveryDocumentLine.getImageUrl());
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);

    containerItems.add(containerItem);

    return containerItems;
  }

  @Transactional
  @InjectTenantFilter
  public void saveAll(List<Container> containers) {
    List<ContainerItem> containerItems = new ArrayList<>();
    for (Container container : containers) {
      containerItems.addAll(container.getContainerItems());
    }
    containerRepository.saveAll(containers);
    containerItemRepository.saveAll(containerItems);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteContainersByTrackingIds(List<String> trackingIdList) {
    List<List<String>> partitionedTrackingIdList = ListUtils.partition(trackingIdList, 100);
    for (List<String> trackingIds : partitionedTrackingIdList) {
      containerItemRepository.deleteByTrackingIdIn(trackingIds);
      containerRepository.deleteByTrackingIdIn(trackingIds);
    }
  }

  private float calculateAverageValue(float weightOrCube, Integer totalPoQty, Integer receivedQty) {
    return (weightOrCube / totalPoQty) * receivedQty;
  }

  public List<CancelContainerResponse> cancelContainers(
      CancelContainerRequest cancelContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    return configUtils
        .getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.CANCEL_CONTAINER_PROCESSOR,
            CancelContainerProcessor.class)
        .cancelContainers(cancelContainerRequest, httpHeaders);
  }

  public List<CancelContainerResponse> swapContainers(
      List<SwapContainerRequest> swapContainerRequest, HttpHeaders httpHeaders) {
    return configUtils
        .getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.CANCEL_CONTAINER_PROCESSOR,
            CancelContainerProcessor.class)
        .swapContainers(swapContainerRequest, httpHeaders);
  }

  /**
   * update container data
   *
   * @param container
   * @param includeInventoryData
   * @param httpHeaders
   * @return Container
   * @throws ReceivingException
   */
  public Container updateContainerData(
      Container container, boolean includeInventoryData, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<ContainerItem> containerItems = container.getContainerItems();
    boolean isOneAtlas =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    if (!CollectionUtils.isEmpty(containerItems) && !ObjectUtils.isEmpty(containerItems.get(0))) {
      ContainerItem containerItem = containerItems.get(0);

      // Enrich the PalletTi from local DB if it's available, otherwise get it from GDM
      enrichItemOverrideIfAvailable(container, containerItem, httpHeaders);

      if (includeInventoryData) {
        InventoryContainerDetails inventoryContainerDetails;
        try {
          inventoryContainerDetails =
              inventoryService.getInventoryContainerDetails(container.getTrackingId(), httpHeaders);
        } catch (ReceivingDataNotFoundException exc) {
          if (ExceptionCodes.INVENTORY_NOT_FOUND.equals(exc.getErrorCode())) {
            log.error("Inventory Not Found error for lpn={}", container.getTrackingId());
            throw new ReceivingDataNotFoundException(
                ExceptionCodes.WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND,
                ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND_ERROR_MSG);
          }
          throw exc;
        }
        if (configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR)) {
          // This flag will be enabled only in WFS market
          if (ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils)) {
            if (!InventoryStatus.PICKED
                .name()
                .equalsIgnoreCase(inventoryContainerDetails.getContainerStatus())) {
              log.error(
                  "Invalid status in INV for corrections in mobile with trackingId={}, INV containerStatus={} is not PICKED",
                  inventoryContainerDetails.getContainerStatus(),
                  container.getTrackingId());
              throw new ReceivingBadDataException(
                  ExceptionCodes.WFS_INVALID_GET_CONTAINER_FOR_CORRECTION_INV_STATUS_NOT_PICKED,
                  ReceivingConstants
                      .WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG,
                  inventoryContainerDetails.getContainerStatus());
            }
          } else {
            // Check for Web UI
            if (!InventoryStatus.PICKED
                    .name()
                    .equals(inventoryContainerDetails.getContainerStatus())
                && !InventoryStatus.AVAILABLE
                    .name()
                    .equals(inventoryContainerDetails.getContainerStatus())) {
              log.error(
                  "Invalid status in INV for corrections in web with trackingId={}, INV containerStatus={} is neither PICKED or AVAILABLE",
                  container.getTrackingId(),
                  inventoryContainerDetails.getContainerStatus());
              throw new ReceivingBadDataException(
                  ExceptionCodes
                      .WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_INV_STATUS_NOT_PICKED_OR_AVAILABLE,
                  ReceivingConstants
                      .WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG);
            }
          }
        }
        // generic method to validate inventory status only if added in ccm
        validateInventoryStatusIfEnabled(inventoryContainerDetails);
        // call method for validation
        // inventoryContainerDetails.inventoryQty is in VNPK
        log.info(
            "InventoryQtyInVnpk={} for Pallet={}",
            inventoryContainerDetails.getInventoryQty(),
            container.getTrackingId());
        containerItem.setInventoryQuantity(inventoryContainerDetails.getInventoryQty());
        containerItem.setInventoryQuantityUOM(ReceivingConstants.Uom.VNPK);
      }
    }

    container.setContainerItems(containerItems);
    return container;
  }

  private void validateInventoryStatusIfEnabled(
      InventoryContainerDetails inventoryContainerDetails) {
    String invalidInventoryStatusForGetByTrackingId =
        configUtils.getCcmValue(
            getFacilityNum(), INVALID_INVENTORY_STATUS_FOR_GET_BY_TRACKING_ID, EMPTY_STRING);
    if (isNotBlank(invalidInventoryStatusForGetByTrackingId)
        && invalidInventoryStatusForGetByTrackingId.contains(
            inventoryContainerDetails.getContainerStatus())) {
      throw new ReceivingBadDataException(
          ExceptionCodes
              .WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_INV_STATUS_NOT_PICKED_OR_AVAILABLE,
          ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG);
    }
  }

  private void enrichItemOverrideIfAvailable(
      Container container, ContainerItem containerItem, HttpHeaders httpHeaders)
      throws ReceivingException {
    final Long itemNumber = containerItem.getItemNumber();
    Optional<DeliveryItemOverride> itemOverrideOptional =
        deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            container.getDeliveryNumber(), itemNumber);
    if (itemOverrideOptional.isPresent()) {
      DeliveryItemOverride itemOverride = itemOverrideOptional.get();
      containerItem.setActualTi(itemOverride.getTempPalletTi());
      if (Objects.nonNull(itemOverride.getTempPalletHi())) {
        containerItem.setActualHi(itemOverride.getTempPalletHi());
      }
      log.info(
          "Updated TI and Hi with overriden ti={}, Hi={} for Pallet={}",
          itemOverride.getTempPalletTi(),
          itemOverride.getTempPalletHi(),
          container.getTrackingId());
    } else {
      GdmPOLineResponse gdmPOLineResponse =
          deliveryServiceRetryableImpl.getPOLineInfoFromGDM(
              container.getDeliveryNumber().toString(),
              containerItem.getPurchaseReferenceNumber(),
              containerItem.getPurchaseReferenceLineNumber(),
              httpHeaders);

      if (!CollectionUtils.isEmpty(gdmPOLineResponse.getDeliveryDocuments())
          && !CollectionUtils.isEmpty(
              gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines())) {
        DeliveryDocumentLine deliveryDocumentLine =
            gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
        containerItem.setActualTi(deliveryDocumentLine.getPalletTie());
        containerItem.setActualHi(deliveryDocumentLine.getPalletHigh());
        log.info(
            "Updated ti/hi with observe ti={} hi={} for Pallet={}",
            deliveryDocumentLine.getPalletTie(),
            deliveryDocumentLine.getPalletHigh(),
            container.getTrackingId());
      }
    }
  }

  @Transactional
  @InjectTenantFilter
  public Container findByTrackingId(String trackingId) {
    return containerRepository.findByTrackingId(trackingId);
  }

  public void deleteContainers(List<String> trackingIds, HttpHeaders httpHeaders) {

    String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    DeleteContainersRequestHandler deleteContainersRequestHandler =
        configUtils.getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELETE_CONTAINERS_HANDLER,
            DeleteContainersRequestHandler.class);
    deleteContainersRequestHandler.deleteContainersByTrackingId(trackingIds, httpHeaders);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-144. Multi Pallet Container(s) are published to Kafka
   * topic that Inventory, SCT, DcFin, YMS etc are subscribing to
   *
   * @param containers list of container
   */
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      externalCall = true,
      executionFlow = "INVT-CTR-Pub")
  public void publishMultipleContainersToInventory(List<ContainerDTO> containers) {
    publishMultipleContainersToInventory(containers, null);
  }

  public void publishMultipleContainersToInventory(
      List<ContainerDTO> containers, HttpHeaders httpHeadersUi) {

    Optional<ContainerDTO> optionalContainer = containers.stream().findAny();
    Object kafkaKey =
        optionalContainer.isPresent()
            ? optionalContainer.get().getDeliveryNumber()
            : "default_kafka_key";
    String kafkaValue = "";
    Map<String, Object> httpHeaders;
    try {
      if (httpHeadersUi == null) {
        httpHeaders = new HashMap<>();
      } else {
        httpHeaders = ReceivingUtils.getForwardablHeader(httpHeadersUi);
        if (httpHeadersUi.containsKey(IGNORE_INVENTORY))
          httpHeaders.put(IGNORE_INVENTORY, httpHeadersUi.get(IGNORE_INVENTORY));

        if (!CollectionUtils.isEmpty(containers)
            && !CollectionUtils.isEmpty(containers.get(0).getContainerItems())) {
          httpHeaders.put(
              IDEM_POTENCY_KEY, containers.get(0).getContainerItems().get(0).getTrackingId());
        }
      }
      headersForRxIntTest(containers, httpHeaders);
      ReceivingUtils.populateFreightTypeInHeader(containers, httpHeaders, configUtils);

      // publish to kafka with hawkshaw only when it's enabled in tenant level in configs i.e
      // default just publish to kafka // Subscribers: Inventory, sct, DcFin,yms...
      kafkaValue = gson.toJson(containers);
      kafkaHawkshawPublisher.publishKafkaWithHawkshaw(
          kafkaKey, kafkaValue, inventMultiCtnrTopic, httpHeaders, containers.getClass().getName());
      log.info("sending KafkaWithHawkshaw key={}, to topic={}", kafkaKey, inventMultiCtnrTopic);
    } catch (Exception exception) {
      log.error(
          "{}failed kafkaMsg to topic={}, key={}, value={}, stack={}",
          SPLUNK_ALERT,
          kafkaKey,
          kafkaValue,
          inventMultiCtnrTopic,
          ExceptionUtils.getStackTrace(exception));

      log.error(
          "Publish MultiContainer to kafka failed for topic {} with key {}",
          inventMultiCtnrTopic,
          kafkaKey,
          exception);

      if (configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(), ABORT_CALL_FOR_KAFKA_ERR, true)) {
        throw new ReceivingInternalException(
            ExceptionCodes.KAFKA_NOT_ACCESSABLE,
            String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, MULTIPLE_PALLET_RECEIVING_FLOW));
      }
    }
  }

  // FOR INTEGRATION TEST ONLY
  private void headersForRxIntTest(List<ContainerDTO> containers, Map<String, Object> httpHeaders) {
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ENABLE_ATLAS_INVENTORY_TEST, false)) {
      httpHeaders.put(TENENT_FACLITYNUM, "32709");
      containers.forEach(
          container -> {
            if (container.getFacility() != null) {
              container.getFacility().put(BU_NUMBER, "32709");
            }
            container
                .getChildContainers()
                .forEach(
                    child -> {
                      if (child.getFacility() != null) {
                        child.getFacility().put(BU_NUMBER, "32709");
                      }
                    });
          });
    }
  }

  @Transactional
  public List<ReprintLabelData> getDataForPrintingLabelByDeliveryNumber(
      Long deliveryNumber,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      Pageable pageable) {
    return containerRepository.getDataForPrintingLabelByDeliveryNumber(
        deliveryNumber, containerExceptions, facilityNum, facilityCode, STATUS_BACKOUT, pageable);
  }

  @Transactional
  public List<ReprintLabelData> getDataForPrintingLabelByDeliveryNumberByUserId(
      Long deliveryNumber,
      String userId,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      Pageable pageable) {
    return containerRepository.getDataForPrintingLabelByDeliveryNumberByUserId(
        deliveryNumber,
        userId,
        containerExceptions,
        facilityNum,
        facilityCode,
        STATUS_BACKOUT,
        pageable);
  }

  @Transactional
  public List<GdcReprintLabelData> getGdcDataForPrintingLabelByDeliveryNumber(
      Long deliveryNumber,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      Pageable pageable) {
    return containerRepository.getGdcDataForPrintingLabelByDeliveryNumber(
        deliveryNumber, containerExceptions, facilityNum, facilityCode, STATUS_BACKOUT, pageable);
  }

  @Transactional
  public List<GdcReprintLabelData> getGdcDataForPrintingLabelByDeliveryNumberByUserId(
      Long deliveryNumber,
      String userId,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      Pageable pageable) {
    return containerRepository.getGdcDataForPrintingLabelByDeliveryNumberByUserId(
        deliveryNumber,
        userId,
        containerExceptions,
        facilityNum,
        facilityCode,
        STATUS_BACKOUT,
        pageable);
  }

  @Transactional
  public List<ReprintLabelData> getLabelDataByDeliveryNumber(
      Long deliveryNumber, String dockTagExceptionCode, Pageable pageable) {
    return containerRepository.getLabelDataByDeliveryNumber(
        deliveryNumber, dockTagExceptionCode, getFacilityNum(), getFacilityCountryCode(), pageable);
  }

  @Transactional
  public List<ReprintLabelData> getLabelDataByDeliveryNumberByUserId(
      Long deliveryNumber, String userId, String dockTagExceptionCode, Pageable pageable) {
    return containerRepository.getLabelDataByDeliveryNumberByUserId(
        deliveryNumber,
        userId,
        dockTagExceptionCode,
        getFacilityNum(),
        getFacilityCountryCode(),
        pageable);
  }

  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      executionFlow = "Reprint-Container-Labels")
  public Map<String, Object> getContainerLabelsByTrackingIds(
      List<String> trackingIds, HttpHeaders httpHeaders) throws ReceivingException {
    if (CollectionUtils.isEmpty(trackingIds)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REPRINT_LABEL_REQUEST,
          ReceivingConstants.INVALID_REPRINT_LABEL_REQUEST);
    }
    Map<String, Object> printJob = new HashMap<>();
    List<Long> instructionIds =
        containerPersisterService.getInstructionIdsByTrackingIds(trackingIds);
    if (CollectionUtils.isEmpty(instructionIds)) {
      log.error("Container not found for trackingIds: {}", gson.toJson(trackingIds));
      throw new ReceivingBadDataException(
          ExceptionCodes.CONTAINER_NOT_FOUND, MATCHING_CONTAINER_NOT_FOUND);
    } else {
      List<Instruction> instructionList = instructionRepository.findByIdIn(instructionIds);
      if (!CollectionUtils.isEmpty(instructionList)) {
        List<PrintLabelRequest> printLabelRequestList = new ArrayList<>();
        Gson gsonBuilder = new Gson();
        for (Instruction instruction : instructionList) {
          List<Map<String, Object>> listOfLabels =
              (List<Map<String, Object>>)
                  instruction.getContainer().getCtrLabel().get("printRequests");
          if (configUtils.isFeatureFlagEnabled(
                  ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN)
              && instruction.getChildContainers() != null) {
            List<Map<String, Object>> palletLabel =
                listOfLabels
                    .stream()
                    .filter(
                        map ->
                            map.containsKey("formatName")
                                && map.get("formatName")
                                    .equals(
                                        configUtils.getCcmValue(
                                            getFacilityNum(),
                                            ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT,
                                            ReceivingConstants.EMPTY_STRING)))
                    .collect(Collectors.toList());
            if (Objects.nonNull(palletLabel)
                && trackingIds.contains(palletLabel.get(0).get("labelIdentifier"))) {
              log.error(
                  "Requested label={} is a pallet label and pallet label printing is disabled",
                  trackingIds);
              throw new ReceivingBadDataException(
                  ExceptionCodes.PALLET_LABEL_CAN_NOT_BE_PRINTED,
                  ReceivingConstants.PALLET_LABEL_PRINTING_DISABLED);
            }
          }
          List<Map<String, Object>> filterPrintDataForRequestedLabels =
              listOfLabels
                  .stream()
                  .filter(
                      map ->
                          map.containsKey("labelIdentifier")
                              && trackingIds.contains(map.get("labelIdentifier")))
                  .collect(Collectors.toList());

          JsonArray jsonPrintRequests =
              (JsonArray) gsonBuilder.toJsonTree(filterPrintDataForRequestedLabels);
          List<PrintLabelRequest> printLabelRequests =
              gsonBuilder.fromJson(
                  jsonPrintRequests, new TypeToken<ArrayList<PrintLabelRequest>>() {}.getType());
          printLabelHelper.updatePrintLabels(instruction, printLabelRequests);
          printLabelRequestList.addAll(printLabelRequests);
        }
        printJob.put(PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
        printJob.put(PRINT_CLIENT_ID_KEY, ATLAS_RECEIVING);
        printJob.put(PRINT_REQUEST_KEY, printLabelRequestList);
      }
    }
    return printJob;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Optional<Container> findOneContainerByInstructionId(long instructionId) {

    List<Container> containerList = containerRepository.findByInstructionId(instructionId);
    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(containerList)) {
      Container container = containerList.get(0);
      container.setContainerItems(
          containerItemRepository.findByTrackingId(container.getTrackingId()));
      return Optional.of(container);
    }
    return Optional.empty();
  }

  @Transactional
  public List<ContainerMetaDataForCaseLabel> getContainerMetaDataForCaseLabelByTrackingIds(
      List<String> trackingIds) {
    return containerRepository.getContainerMetaDataForCaseLabelByTrackingIds(
        trackingIds, getFacilityNum(), getFacilityCountryCode());
  }

  @Transactional
  @InjectTenantFilter
  public List<ContainerMetaDataForPalletLabel> getContainerMetaDataForPalletLabelByTrackingIds(
      List<String> trackingIds) {
    return containerRepository.getContainerMetaDataForpalletLabelByTrackingIds(trackingIds);
  }

  @Transactional
  public List<ContainerMetaDataForPalletLabel> getContainerItemMetaDataForPalletLabelByTrackingIds(
      List<String> trackingIds) {
    return containerRepository.getContainerItemMetaDataForPalletLabelByTrackingIds(
        trackingIds, getFacilityNum(), getFacilityCountryCode());
  }

  @Transactional
  public List<ContainerMetaDataForPalletLabel>
      getContainerAndContainerItemMetaDataForPalletLabelByTrackingIds(List<String> trackingIds) {
    return containerRepository.getContainerAndContainerItemMetaDataForPalletLabelByTrackingIds(
        trackingIds, getFacilityNum(), getFacilityCountryCode());
  }

  @Transactional
  public List<ContainerMetaDataForNonNationalPoLabel>
      getContainerItemMetaDataForNonNationalLabelByTrackingIds(List<String> trackingIds) {
    return containerRepository.getContainerItemMetaDataForNonNationalLabelByTrackingIds(
        trackingIds, getFacilityNum(), getFacilityCountryCode());
  }

  @Transactional
  @InjectTenantFilter
  public List<ContainerMetaDataForDockTagLabel>
      getContainerItemMetaDataForDockTagLabelByTrackingIds(List<String> trackingIds) {
    return containerRepository.getContainerItemMetaDataForDockTagLabelByTrackingIds(trackingIds);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelIdAndTrackingIdPair> getLabelIdsByTrackingIdsWhereLabelIdNotNull(
      Set<String> trackingIds) {
    return containerRepository.getLabelIdsByTrackingIdsWhereLabelIdNotNull(trackingIds);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public ContainerItem getContainerByItemNumber(Long itemNumber) {
    Optional<ContainerItem> optionalContainerItem =
        containerItemRepository.findFirstByItemNumberOrderByIdDesc(itemNumber);

    return optionalContainerItem.orElseThrow(
        () ->
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_ITEM_ERROR_MSG,
                    itemNumber)));
  }

  /**
   * Http call to Inventory posting receipts. if http call fails it will retry 5 times
   *
   * @param container
   * @param httpHeaders
   */
  public void postReceiptsReceiveAsCorrection(Container container, HttpHeaders httpHeaders) {
    Gson gsonDateFormatBuilder = new GsonBuilder().setDateFormat(INVENTORY_DATE_FORMAT).create();
    Map<String, Object> containerPayloadMap = new HashMap<>();
    String containerJson = gson.toJson(container);
    ContainerDTO containerDTO = gson.fromJson(containerJson, ContainerDTO.class);

    // Set To Be Audited Tag for GDC One Atlas Items
    setToBeAuditedTagGDC(containerDTO);

    // Set Subcenter details is receiving from OSS for GDC One Atlas Items
    addSubcenterInfo(containerDTO);

    containerPayloadMap.put(INVENTORY_RECEIPT, gsonDateFormatBuilder.toJson(containerDTO));
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    httpHeaders.add(IDEM_POTENCY_KEY, container.getTrackingId());
    final String url;
    if (configUtils.getConfiguredFeatureFlag(getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION;

    } else {
      url = appConfig.getInventoryBaseUrl() + INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION;
    }

    asyncPersister.persistAsyncHttp(
        POST,
        url,
        gsonDateFormatBuilder.toJson(containerPayloadMap),
        httpHeaders,
        RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public ContainerItem getContainerItemByUpcAndItemNumber(String upc, Long itemNumber) {

    TenantContext.get()
        .setFetchContainerItemByUpcAndItemNumberCallStart(System.currentTimeMillis());
    Optional<ContainerItem> optionalContainerItem =
        containerItemRepository.findTopByItemNumberAndItemUPCOrderByIdDesc(itemNumber, upc);
    TenantContext.get().setFetchContainerItemByUpcAndItemNumberCallEnd(System.currentTimeMillis());

    return optionalContainerItem.orElseThrow(
        () ->
            new ReceivingBadDataException(
                ExceptionCodes.CONTAINER_ITEM_DATA_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants
                        .CONTAINER_ITEM_NOT_FOUND_BY_ITEM_AND_UPC_ERROR_MSG,
                    itemNumber,
                    upc),
                String.valueOf(itemNumber),
                upc));
  }

  @Transactional
  @InjectTenantFilter
  public List<Container> findByDeliveryNumberAndShipmentIdNotNull(Long deliveryNumber) {
    return containerRepository.findByDeliveryNumberAndShipmentIdIsNotNull(deliveryNumber);
  }

  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      externalCall = true,
      executionFlow = "docktag-container-publish")
  public void publishDockTagContainer(Container container) {
    String payload = gson.toJson(container);

    Object kafkaKey =
        !org.apache.commons.lang.StringUtils.isEmpty(payload)
            ? container.getTrackingId()
            : ReceivingConstants.DEFAULT_KAFKA_KEY;
    log.info("Kafka key for publishing container message is: {}", kafkaKey);

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(kafkaKey, payload, createDockTagTopic);
      if (kafkaConfig.isInventoryOnSecureKafka()) {
        secureKafkaTemplate.send(message);
        log.info(
            "Secure Kafka: Successfully published docktag container: {} on topic: {}",
            container.getTrackingId(),
            createDockTagTopic);
      }
    } catch (Exception exception) {
      log.error(
          "Error in publishing docktag container: {} and error: {}",
          container.getTrackingId(),
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, ReceivingConstants.CONTAINERS_PUBLISH));
    }
  }

  @Transactional(readOnly = true)
  public int receivedContainerQuantityBySSCC(String ssccNumber) {
    return containerPersisterService.receivedContainerQuantityBySSCC(ssccNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Container> getContainerByDeliveryNumber(
      Long deliveryNumber, Consumer<Container> processContainerData) throws ReceivingException {
    List<Container> containers = getContainerByDeliveryNumber(deliveryNumber);
    if (!CollectionUtils.isEmpty(containers) && Objects.nonNull(processContainerData))
      containers.forEach(container -> processContainerData.accept(container));
    return containers;
  }

  public String receiveContainer(String trackingId, ContainerScanRequest containerScanRequest) {
    containerScanRequest.setTrackingId(trackingId);
    CreateContainerProcessor createContainerProcessor =
        configUtils.getConfiguredInstance(
            String.valueOf(getFacilityNum()),
            ReceivingConstants.CONTAINER_CREATE_PROCESSOR,
            DefaultCreateContainerProcessor.DEFAULT_CREATE_CONTAINER_PROCESSOR,
            CreateContainerProcessor.class);

    return createContainerProcessor.createContainer(containerScanRequest);
  }

  public boolean isAtlasConvertedItem(String trackingId) throws ReceivingException {
    final Container container = getContainerByTrackingId(trackingId, Uom.EACHES);
    return ContainerUtils.isAtlasConvertedItem(container.getContainerItems().get(0));
  }

  @Transactional
  @InjectTenantFilter
  public List<PalletHistory> getReceivedHistoryByDeliveryNumber(Long deliveryNumber)
      throws ReceivingException {
    return containerPersisterService.findReceivedHistoryByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<PalletHistory> getReceivedHistoryByDeliveryNumberWithPO(
      Long deliveryNumber, String po, Integer poLine) throws ReceivingException {
    return containerPersisterService.findReceivedHistoryByDeliveryNumberWithPO(
        deliveryNumber, po, poLine);
  }

  @Deprecated
  private void validateInventoryAndMoveStatusForCorrection(
      InventoryContainerDetails inventoryContainerDetails,
      String trackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info("validateInventoryAndMoveStatusForCorrection for trackingId={}", trackingId);
    boolean isBlockCorrection = false;
    String invalidInventoryStatus =
        configUtils.getCcmValue(
            getFacilityNum(), INVALID_INVENTORY_STATUS_CORRECTION, EMPTY_STRING);
    String invalidMoveStatus =
        configUtils.getCcmValue(
            getFacilityNum(), INVALID_MOVE_TYPE_AND_STATUS_CORRECTION, EMPTY_STRING);

    if (nonNull(invalidInventoryStatus)
        && !invalidInventoryStatus.isEmpty()
        && invalidInventoryStatus.contains(inventoryContainerDetails.getContainerStatus())) {
      log.error(
          "Invalid status from inventory for corrections with trackingId={}, INV containerStatus={} is not Valid",
          trackingId,
          inventoryContainerDetails.getContainerStatus());
      isBlockCorrection = true;
    } else if (nonNull(invalidMoveStatus) && !invalidMoveStatus.isEmpty()) {
      JsonArray moveResponse =
          moveRestApiClient.getMoveContainerByContainerId(trackingId, httpHeaders);

      if (nonNull(moveResponse)
          && !moveResponse.isEmpty()
          && moveResponse.get(0).getAsJsonObject().has(MOVE_STATUS)) {
        String moveStatus = moveResponse.get(0).getAsJsonObject().get(MOVE_STATUS).getAsString();
        if (invalidMoveStatus.contains(moveStatus)) {
          log.error(
              "Invalid status from move for corrections with trackingId={}, containerStatus={} is not Valid",
              trackingId,
              moveStatus);
          isBlockCorrection = true;
        }
      } else {
        log.error("Move sent invalid response for trackingId={}", trackingId);
        isBlockCorrection = true;
      }
    }

    log.info(
        "validateInventoryAndMoveStatusForCorrection for trackingId={}, isBlockCorrection={}",
        trackingId,
        isBlockCorrection);
    if (isBlockCorrection) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CORRECTION_ERROR,
          ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG);
    }
  }

  public Container updateContainerInventoryStatus(String trackingId, String updatedInventoryStatus)
      throws ReceivingException {
    Container container = getContainerByTrackingId(trackingId);
    container.setInventoryStatus(updatedInventoryStatus);
    return this.containerRepository.save(container);
  }

  // REQUIRED FOR DCFIN RECEIPT KAFKA CONTRACT
  public void enrichContainerForDcfin(
      DeliveryDocument deliveryDocument, Container parentContainer) {
    final Map<String, Object> miscInfoMap = new HashMap<>();
    if (Objects.nonNull(deliveryDocument.getTotalBolFbq())
        && (!deliveryDocument.getTotalBolFbq().equals(0)
            || Objects.isNull(deliveryDocument.getFreightBillQty()))) {
      miscInfoMap.put(FREIGHTBILL_QTY, deliveryDocument.getTotalBolFbq());
    } else if (Objects.nonNull(deliveryDocument.getFreightBillQty())) {
      miscInfoMap.put(FREIGHTBILL_QTY, deliveryDocument.getFreightBillQty());
    }
    miscInfoMap.put(TRAILER_NUMBER, deliveryDocument.getTrailerId());
    setFinancialGroupCode(parentContainer, deliveryDocument.getFinancialReportingGroup());
    setContainerMiscInfo(parentContainer, miscInfoMap);
    setCompleteTsInChildContainer(parentContainer);
  }

  // REQUIRED FOR DCFIN RECEIPT KAFKA CONTRACT
  private void setContainerMiscInfo(
      Container parentContainer, Map<String, Object> miscInfoEntryToAdd) {
    if (parentContainer.isHasChildContainers()) {
      for (Container childContainer : parentContainer.getChildContainers()) {
        Map<String, Object> miscInfo =
            Optional.ofNullable(childContainer.getContainerMiscInfo()).orElse(new HashMap<>());
        miscInfo.putAll(miscInfoEntryToAdd);
        childContainer.setContainerMiscInfo(miscInfo);
      }
    } else {
      Map<String, Object> miscInfo =
          Optional.ofNullable(parentContainer.getContainerMiscInfo()).orElse(new HashMap<>());
      miscInfo.putAll(miscInfoEntryToAdd);
      parentContainer.setContainerMiscInfo(miscInfo);
    }
  }

  // REQUIRED FOR DCFIN RECEIPT KAFKA CONTRACT
  private void setFinancialGroupCode(Container container, String financialGroupCode) {
    if (Objects.nonNull(container.getContainerItems())) {
      container
          .getContainerItems()
          .forEach(
              containerItem -> containerItem.setFinancialReportingGroupCode(financialGroupCode));
    }
  }

  // REQUIRED FOR DCFIN RECEIPT KAFKA CONTRACT
  private static void setCompleteTsInChildContainer(Container container) {
    if (Objects.nonNull(container.getChildContainers())) {
      container
          .getChildContainers()
          .forEach(child -> child.setCompleteTs(container.getCompleteTs()));
    }
  }

}
