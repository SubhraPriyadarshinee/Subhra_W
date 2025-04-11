package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.rx.common.RxUtils.buildOutboxEvent;
import static com.walmart.move.nim.receiving.rx.common.RxUtils.getManufactureDetailByPackItem;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.lang.String.valueOf;
import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.time.DateUtils.isSameDay;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.FeatureFlag;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PackItemResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitRequestMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitSerialRequest;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.collections.CollectionUtils;
import io.strati.libs.logging.commons.lang3.StringUtils;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
public class RxCompleteInstructionOutboxHandler {

  private Gson gsonBuilder;
  @Resource private Gson gson;
  @Resource private ContainerPersisterService containerPersisterService;
  @Resource private InstructionPersisterService instructionPersisterService;
  @Resource private RxDeliveryServiceImpl rxDeliveryService;
  @Resource private ContainerService containerService;
  @Resource private Transformer<Container, ContainerDTO> rxContainerTransformer;
  @Resource private OutboxEventSinkService outboxEventSinkService;
  @Resource private RxInstructionHelperService rxInstructionHelperService;
  @Resource private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Resource private EpcisService epcisService;
  @Resource private RxInstructionService rxInstructionService;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private OutboxConfig outboxConfig;

  @PostConstruct
  public void init() {
    gsonBuilder =
            new GsonBuilder()
                    .excludeFieldsWithoutExposeAnnotation()
                    .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                    .create();
  }

  /**
   * creates outbox event and persists container, instruction
   *
   * @param container parent container
   * @param instruction instruction
   * @param userId user id
   * @param slotDetails slot details
   * @param httpHeaders headers
   */
  public void outboxCompleteInstruction(
          Container container,
          Instruction instruction,
          String userId,
          SlotDetails slotDetails,
          HttpHeaders httpHeaders) {
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    headers.put(ReceivingConstants.MOVE_TO_LOCATION, slotDetails.getSlot());
    MetaData metaData = MetaData.with("trackingId", container.getTrackingId());
    String eventId =
            getFacilityCountryCode() + "_" + getFacilityNum() + "_" + container.getTrackingId();
    OutboxEvent outboxEvent =
        buildOutboxEvent(
            headers, null, eventId, metaData, OUTBOX_POLICY_HTTP_PENDING_CONTAINERS, Instant.now());
            buildOutboxEvent(
                    headers,
                    null,
                    eventId,
                    metaData,
                    outboxConfig.getOutboxPolicyHttpPendingContainers(),
                    Instant.now());

    persist(instruction, container, userId, outboxEvent);
  }

  /**
   * creates outbox event to publish to inventory kafka for ASN flow only
   *
   * @param container parent container
   * @param instruction instruction
   * @param userId user id
   * @param slotDetails slot details
   * @param httpHeaders headers
   */
  public void outboxCompleteInstructionAsnFlow(
          Container container,
          Instruction instruction,
          String userId,
          SlotDetails slotDetails,
          HttpHeaders httpHeaders) {
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    // set doc type/number and vendor and childContainers qty
    DeliveryDocument deliveryDocument =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ContainerItem containerItemFirst = container.getContainerItems().get(0);
    String documentType, documentNumber;
    Integer vendorNumber;
    ContainerItem containerItem = container.getContainerItems().get(0);
    String itemGtin = containerItem.getGtin();
    Boolean isDscsa = Boolean.TRUE;
    if (null != deliveryDocumentLine.getAdditionalInfo()) {
      isDscsa = deliveryDocumentLine.getAdditionalInfo().getIsDscsaExemptionInd();
    }
    final Boolean isDscsaExceptionInd = isDscsa;
    if (RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING
            .getInstructionType()
            .equalsIgnoreCase(instruction.getInstructionCode())
            || RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING
            .getInstructionType()
            .equalsIgnoreCase(instruction.getInstructionCode())) {
      documentType = PO_TEXT;
      documentNumber = deliveryDocument.getPurchaseReferenceNumber();
      vendorNumber =
              StringUtils.isNotBlank(deliveryDocument.getVendorNumber())
                      ? Integer.valueOf(deliveryDocument.getVendorNumber())
                      : null;
      containerItem.setIsDscsaExemptionInd(isDscsaExceptionInd);
      containerItemFirst.setIsCompliancePack(deliveryDocumentLine.getComplianceItem());
    } else {
      String inboundShipmentDocId =
              deliveryDocumentLine.getShipmentDetailsList().get(0).getInboundShipmentDocId();
      documentType = ASN_TEXT;
      documentNumber = inboundShipmentDocId;
      vendorNumber = deliveryDocumentLine.getVendorNbrDeptSeq();
      AtomicReference<Date> expiryDateForPallet = new AtomicReference<>(new Date());;
      Set<Container> caseContainers = container.getChildContainers();
      caseContainers.forEach(caseContainer -> {
        Optional<ContainerItem> containerItemWithExpiry = caseContainer.getContainerItems().stream().min(Comparator.comparing(ContainerItem::getExpiryDate));
        containerItemWithExpiry.ifPresent(containerItemWithExp -> expiryDateForPallet.set(containerItemWithExp.getExpiryDate()));
      });
      //expiry date manipulation for Parent Container
      containerItem.setExpiryDate(expiryDateForPallet.get());
    }
    container.setDocumentType(documentType);
    container.setDocumentNumber(documentNumber);
    container.setIsDscsaExemptionInd(isDscsaExceptionInd);
    container.setIsCompliancePack(deliveryDocumentLine.getComplianceItem());
    container
            .getChildContainers()
            .forEach(
                    child -> {
                      child.setDocumentType(documentType);
                      child.setDocumentNumber(documentNumber);
                      ContainerItem childContainerItem = child.getContainerItems().get(0);
                      childContainerItem.setVendorNumber(vendorNumber);
                      childContainerItem.setQuantity(
                              conversionToWareHousePack(
                                      childContainerItem.getQuantity(),
                                      Uom.EACHES,
                                      0,
                                      childContainerItem.getWhpkQty()));
                      childContainerItem.setQuantityUOM(Uom.WHPK);
                      childContainerItem.setGtin(itemGtin);
                      child.setContainerType(SERIALIZED);
                    });

    correctScannedInfoLevels(container, instruction);

    if (RxInstructionType.BUILD_PARTIAL_CONTAINER
            .getInstructionType()
            .equalsIgnoreCase(instruction.getInstructionCode())) {
      for (Container child : container.getChildContainers()) {
        Container childContainer = getContainerWithChildsByTrackingId(child.getTrackingId());
        if (!CollectionUtils.isEmpty(childContainer.getChildContainers())) {
          child.setContainerItems(
                  childContainer
                          .getChildContainers()
                          .stream()
                          .filter(
                                  innerChildContainer ->
                                          !CollectionUtils.isEmpty(innerChildContainer.getContainerItems()))
                          .flatMap(innerChildContainer -> innerChildContainer.getContainerItems().stream())
                          .collect(Collectors.toList()));
          child.getContainerItems().forEach(
                  childContainerItem -> {
                    childContainerItem.setQuantity(
                            conversionToWareHousePack(
                                    childContainerItem.getQuantity(),
                                    Uom.EACHES,
                                    0,
                                    childContainerItem.getWhpkQty()));
                    childContainerItem.setQuantityUOM(Uom.WHPK);
                  }
          );
        }
      }
    }

    // compute quantity as pallet quantity/pallet whpk quantity
    int newQuantity =
            conversionToWareHousePack(
                    containerItem.getQuantity(), Uom.EACHES, 0, containerItem.getWhpkQty());
    // over-riding qty to Whpk and send EA as UOM as per Inventory requirement. This is later
    // overridden for receiving.
    containerItem.setQuantity(newQuantity);
    containerItem.setQuantityUOM(Uom.WHPK);
    containerItem.setVendorNumber(vendorNumber);

    String ndc = deliveryDocumentLine.getNdc();
    containerItem.setIsDscsaExemptionInd(isDscsaExceptionInd);
    containerItem.setNationalDrugCode(ndc);
    containerItem.setType(PALLET);

    // ITERATE THROUGH CASES
    container
            .getChildContainers()
            .forEach(
                    (child) -> {
                      child.setNationalDrugCode(ndc);
                      child.setIsDscsaExemptionInd(isDscsaExceptionInd);
                      // ITERATE THROUGH ITEMS
                      child
                              .getContainerItems()
                              .forEach(
                                      (item) -> {
                                        item.setIsDscsaExemptionInd(isDscsaExceptionInd);
                                        item.setNationalDrugCode(ndc);
                                      });
                    });

    String body = gsonBuilder.toJson(rxContainerTransformer.transformList(singletonList(container)));
    String eventId = container.getTrackingId();
    headers.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    headers.put(ATLAS_KAFKA_IDEMPOTENCY, eventId);
    headers.put(FLOW_NAME, RECEIVING);
    headers.put(MARKET_TYPE, MARKET_TYPE_PHARMACY);
    headers.put(DOCUMENT_TYPE, ASN_TEXT);
    headers.put(ReceivingConstants.API_VERSION, ReceivingConstants.API_VERSION_VALUE);
    MetaData metaData = MetaData.with("key", eventId);
    OutboxEvent outboxEvent =
            buildOutboxEvent(
                    headers,
                    body,
                    eventId,
                    metaData,
                    outboxConfig.getOutboxPolicyKafkaInventory(),
                    Instant.now());
    int originalEachQty =
            conversionToEaches(containerItem.getQuantity(), Uom.WHPK, 0, containerItem.getWhpkQty());
    // resetting qty with original eaches qty
    container.getContainerItems().get(0).setQuantity(originalEachQty);
    container.getContainerItems().get(0).setQuantityUOM(Uom.EACHES);
    container
            .getChildContainers()
            .forEach(
                    child -> {
                      ContainerItem childContainerItem = child.getContainerItems().get(0);
                      childContainerItem.setQuantity(
                              conversionToEaches(
                                      childContainerItem.getQuantity(),
                                      Uom.WHPK,
                                      0,
                                      childContainerItem.getWhpkQty()));
                      childContainerItem.setQuantityUOM(Uom.EACHES);
                    });
    persist(instruction, container, userId, outboxEvent);

    // create moves
    if(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ENABLE_OUTBOX_CREATE_MOVES, false)) {
      httpHeaders.set(ReceivingConstants.MOVE_TO_LOCATION, slotDetails.getSlot());
      constructAndOutboxCreateMoves(instruction, container.getTrackingId(), httpHeaders);
    }
  }

  /**
   * For ASN removing move case level Details 1 level Up
   *
   * @param container
   * @param instruction
   */
  private void correctScannedInfoLevels(Container container, Instruction instruction) {
    if (!RxInstructionType.BUILD_PARTIAL_CONTAINER
            .getInstructionType()
            .equalsIgnoreCase(instruction.getInstructionCode())
            && !RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING
            .getInstructionType()
            .equalsIgnoreCase(instruction.getInstructionCode())
            && !RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING
            .getInstructionType()
            .equalsIgnoreCase(instruction.getInstructionCode())) {

      if (!CollectionUtils.isEmpty(container.getChildContainers())) {
        for (Container childContainer : container.getChildContainers()) {
          correctScannedInfoLevel(childContainer);
        }
      }
    }
  }

  private void correctScannedInfoLevel(Container childContainer) {
    if (CollectionUtils.isEmpty(childContainer.getContainerItems())) {
      return;
    }
    ContainerItem childContainerItem = childContainer.getContainerItems().get(0);

    if (childContainerItem.getExpiryDate() != null) {
      String expiryDate = ReceivingUtils.dateConversionToUTC(childContainerItem.getExpiryDate());
      childContainer.setExpiryDate(expiryDate);
    }
    childContainer.setSerial(childContainerItem.getSerial());
    childContainer.setLotNumber(childContainerItem.getLotNumber());
    childContainer.setGtin(childContainerItem.getGtin());

    childContainerItem.setSerial(null);
    childContainerItem.setExpiryDate(null);
    childContainerItem.setLotNumber(null);
  }

  /**
   * creates pending containers, container items, updates instruction, and multiple outbox events
   * for each container and sends attp events for all containers old and generated
   *
   * @param parentTrackingId parent tracking id
   * @param httpHeaders headers
   * @throws ReceivingException error while fetching containers/instruction/no children
   */
  public void pendingContainers(String parentTrackingId, HttpHeaders httpHeaders)
          throws ReceivingException {
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(facilityNum);
    Map<String, Object> headers = getForwardablHeaderWithTenantData(httpHeaders);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    String eventId = countryCode + "_" + facilityNum + "_" + parentTrackingId + "_";

    Container parentContainer = getContainerWithChildsByTrackingId(parentTrackingId);
    Instruction instruction =
            instructionPersisterService.getInstructionById(parentContainer.getInstructionId());

    Set<Container> childContainers = parentContainer.getChildContainers();
    List<ContainerDetails> containerDetails = instruction.getChildContainers();
    Container referenceContainer;
    if (!childContainers.isEmpty() && null != containerDetails)
      referenceContainer = childContainers.stream().findFirst().get();
    else {
      log.error("No child containers found for tracking id {}", parentTrackingId);
      throw new ReceivingException(
              "No child containers found for tracking id " + parentTrackingId, NO_CONTENT);
    }

    List<Container> pendingContainers = new ArrayList<>();
    List<ContainerItem> pendingContainerItems = new ArrayList<>();
    List<OutboxEvent> outboxEvents = new ArrayList<>();
    AtomicReference<Map<String, String>> facilityMap = new AtomicReference<>();

    DeliveryDocument deliveryDocument =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    List<ManufactureDetail> serializedInfo = additionalInfo.getSerializedInfo();
    if (null == serializedInfo) {
      log.error("serializedInfo is null");
      throw new ReceivingException("serializedInfo is null", NO_CONTENT);
    }
    log.info("serializedInfo size {}", serializedInfo.size());
    String ndc = deliveryDocumentLine.getNdc();
    Boolean isDscsaExemptionInd = deliveryDocumentLine.getAdditionalInfo().getIsDscsaExemptionInd();

    // set doc type/number and vendor
    String documentType = EPCIS_TEXT,
            documentNumber =
                    deliveryDocumentLine.getShipmentDetailsList().get(0).getInboundShipmentDocId();
    Integer vendorNumber = deliveryDocumentLine.getVendorNbrDeptSeq();
    parentContainer.setDocumentType(documentType);
    parentContainer.setDocumentNumber(documentNumber);
    parentContainer.setIsDscsaExemptionInd(isDscsaExemptionInd);
    parentContainer.getContainerItems().get(0).setVendorNumber(vendorNumber);
    childContainers.forEach(
            child -> {
              child.setDocumentType(documentType);
              child.setDocumentNumber(documentNumber);
              child.getContainerItems().get(0).setVendorNumber(vendorNumber);
            });

    // pallet sscc computation
    String palletSscc = deliveryDocumentLine.getPalletSSCC();
    if (null == palletSscc) {
      palletSscc = additionalInfo.getPalletOfCase();
    }
    parentContainer.setSsccNumber(palletSscc);

    // facility map manipulation
    facilityMap.set(parentContainer.getFacility());
    if (facilityMap.get() != null) {
      facilityMap.get().put(COUNTRY_CODE, facilityMap.get().remove(TENENT_COUNTRY_CODE));
    }
    parentContainer.setFacility(facilityMap.get());
    containerService.enrichContainerForDcfin(deliveryDocument, parentContainer);

    Container outboxParentContainer = gson.fromJson(gson.toJson(parentContainer), Container.class);
    // only create containers for pallet recv and audit qty is not same as projected recvd qty
    boolean isCreateContainers =
            RxInstructionType.RX_SER_BUILD_CONTAINER
                    .getInstructionType()
                    .equalsIgnoreCase(instruction.getInstructionCode())
                    && additionalInfo.getAuditQty()
                    != (instruction.getProjectedReceiveQty() / deliveryDocumentLine.getVendorPack())  && !additionalInfo.isPalletFlowInMultiSku();

    serializedInfo
            .stream()
            .filter(info -> !info.getReportedUom().equalsIgnoreCase(Uom.EACHES)) // ignore EAs
            .forEach(
                    info -> {
                      String trackingId = ndc + "_" + info.getSerial();
                      Optional<Container> childContainer =
                              childContainers
                                      .stream()
                                      .filter(child -> child.getTrackingId().equalsIgnoreCase(trackingId))
                                      .findFirst();
                      if (!childContainer.isPresent() && isCreateContainers) {
                        // add container
                        Container newContainer =
                                gson.fromJson(gson.toJson(referenceContainer), Container.class);
                        newContainer.setId(null);
                        newContainer.setTrackingId(trackingId);
                        newContainer.setCreateTs(new Date());
                        newContainer.setCreateUser(userId);
                        newContainer.setLastChangedTs(new Date());
                        newContainer.setLastChangedUser(userId);
                        newContainer.getContainerMiscInfo().put(IS_AUDITED, false);
                        newContainer.setAudited(false);
                        newContainer.setGtin(info.getGtin());
                        newContainer.setSerial(info.getSerial());
                        newContainer.setLotNumber(info.getLot());
                        newContainer.setExpiryDate(info.getExpiryDate());
                        newContainer.setSsccNumber(info.getSscc());
                        newContainer.setNationalDrugCode(ndc);
                        newContainer.setIsDscsaExemptionInd(isDscsaExemptionInd);
                        newContainer.setContainerType(SERIALIZED);
                        newContainer.setDocumentNumber(documentNumber);
                        newContainer.setDocumentType(documentType);
                        newContainer.setIsCompliancePack(additionalInfo.getIsCompliancePack());
                        facilityMap.set(newContainer.getFacility());
                        if (facilityMap.get() != null) {
                          facilityMap
                                  .get()
                                  .put(COUNTRY_CODE, facilityMap.get().remove(TENENT_COUNTRY_CODE));
                        }
                        newContainer.setFacility(facilityMap.get());
                        pendingContainers.add(newContainer);
                        log.info("added container {}", trackingId);

                        // add container item
                        ContainerItem newContainerItem =
                                gson.fromJson(
                                        gson.toJson(newContainer.getContainerItems().get(0)), ContainerItem.class);
                        newContainerItem.setId(null);
                        newContainerItem.setTrackingId(trackingId);
                        newContainerItem.setGtin(info.getGtin());
                        newContainerItem.setSerial(info.getSerial());
                        newContainerItem.setLotNumber(info.getLot());
                        newContainerItem.setExpiryDate(parseDate(info.getExpiryDate()));
                        newContainerItem.setAudited(false);
                        newContainerItem.setVendorNumber(vendorNumber);
                        pendingContainerItems.add(newContainerItem);
                        newContainer.setContainerItems(singletonList(newContainerItem));
                        log.info("added container item {}", trackingId);

                        // update instruction
                        ContainerDetails newContainerDetails = new ContainerDetails();
                        newContainerDetails.setTrackingId(trackingId);
                        Content newContent =
                                gson.fromJson(
                                        gson.toJson(containerDetails.get(0).getContents().get(0)), Content.class);
                        newContent.setGtin(info.getGtin());
                        newContent.setSerial(info.getSerial());
                        newContent.setLot(info.getLot());
                        newContainerDetails.setContents(singletonList(newContent));
                        containerDetails.add(newContainerDetails);
                        instruction.setChildContainers(containerDetails);
                        log.info("added instruction content {}", trackingId);
                        outboxParentContainer.setChildContainers(singleton(newContainer));
                        outboxEvents.add(
                                buildOutboxEvent(
                                        headers,
                                        gsonBuilder.toJson(outboxParentContainer),
                                        eventId + trackingId,
                                        MetaData.emptyInstance(),
                                        outboxConfig.getOutboxPolicyHttpEachesDetail(),
                                        Instant.now()));
                      } else if (childContainer.isPresent()) {
                        // for existing containers, set these fields since inventory needs
                        childContainer.get().setGtin(info.getGtin());
                        childContainer.get().setSerial(info.getSerial());
                        childContainer.get().setLotNumber(info.getLot());
                        childContainer.get().setExpiryDate(info.getExpiryDate());
                        childContainer.get().setSsccNumber(info.getSscc());
                        childContainer.get().setNationalDrugCode(ndc);
                        childContainer.get().setIsDscsaExemptionInd(isDscsaExemptionInd);
                        childContainer.get().setContainerType(SERIALIZED);
                        childContainer.get().setIsCompliancePack(additionalInfo.getIsCompliancePack());
                        childContainer.get().setPalletFlowInMultiSku(additionalInfo.isPalletFlowInMultiSku());
                        facilityMap.set(childContainer.get().getFacility());
                        if (facilityMap.get() != null) {
                          facilityMap
                                  .get()
                                  .put(COUNTRY_CODE, facilityMap.get().remove(TENENT_COUNTRY_CODE));
                        }
                        childContainer.get().setFacility(facilityMap.get());
                        outboxParentContainer.setChildContainers(singleton(childContainer.get()));
                        outboxEvents.add(
                                buildOutboxEvent(
                                        headers,
                                        gsonBuilder.toJson(outboxParentContainer),
                                        eventId + trackingId,
                                        MetaData.emptyInstance(),
                                        outboxConfig.getOutboxPolicyHttpEachesDetail(),
                                        Instant.now()));
                      }
                    });

    // in case receiving scannedCase will contain case scan data
    ManufactureDetail scannedCase = additionalInfo.getScannedCase();
    if (null != scannedCase
            && ((null != scannedCase.getGtin() && null != scannedCase.getSerial())
            || null != scannedCase.getSscc())) {
      Optional<Container> scannedChild =
              childContainers
                      .stream()
                      .filter(child -> !child.getTrackingId().contains("_"))
                      .findFirst();
      scannedChild.ifPresent(
              child -> {
                child.setGtin(scannedCase.getGtin());
                child.setSerial(scannedCase.getSerial());
                child.setLotNumber(scannedCase.getLot());
                child.setExpiryDate(scannedCase.getExpiryDate());
                child.setSsccNumber(scannedCase.getSscc());
                child.setNationalDrugCode(ndc);
                child.setIsDscsaExemptionInd(isDscsaExemptionInd);
                child.setContainerType(SERIALIZED);
                child.setIsCompliancePack(additionalInfo.getIsCompliancePack());
                outboxParentContainer.setChildContainers(singleton(child));
                outboxEvents.add(
                        buildOutboxEvent(
                                headers,
                                gsonBuilder.toJson(outboxParentContainer),
                                eventId + child.getTrackingId(),
                                MetaData.emptyInstance(),
                                outboxConfig.getOutboxPolicyHttpEachesDetail(),
                                Instant.now()));
              });
    }
    containerService.enrichContainerForDcfin(deliveryDocument, parentContainer);

    log.info("==> pending containers  {}", pendingContainers.size());
    log.info("==> pending container items  {}", pendingContainerItems.size());
    log.info("==> new instruction content size  {}", containerDetails.size());
    log.info("==> outbox events size  {}", outboxEvents.size());
    persist(instruction, pendingContainers, pendingContainerItems, outboxEvents);
    log.info("persisted to db");

    // create moves
    if(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ENABLE_OUTBOX_CREATE_MOVES, false)) {
      constructAndOutboxCreateMoves(instruction, parentTrackingId, httpHeaders);
    }

    // send attp events for scanned and pendingContainers
    Container updatedParentContainer = getContainerWithChildsByTrackingId(parentTrackingId);
    epcisService.constructAndOutboxEpcisEvent(updatedParentContainer, instruction, httpHeaders);
  }

  /**
   * [calls gdm to fetch unit details for non partials] or [fetch unit containers from db for
   * partials] transforms to inventory container format, and creates outbox event to publish to
   * inventory kafka
   *
   * @param container parent container
   * @param httpHeaders headers
   */
  public void eachesDetail(Container container, HttpHeaders httpHeaders) {
    Optional<Container> childContainer = container.getChildContainers().stream().findFirst();
    AtomicReference<String> eventId = new AtomicReference<>();
    AtomicReference<String> ndc = new AtomicReference<>("");
    AtomicReference<Boolean> isDscsaExemptionInd = new AtomicReference<>(Boolean.FALSE);

    childContainer.ifPresent(
            child -> {
              ndc.set(child.getNationalDrugCode());
              isDscsaExemptionInd.set(child.getIsDscsaExemptionInd());
              // for [partial case flow] or [case recv and qtySum < vnpk] then send only scanned EAs
              String instructionCode =
                      String.valueOf(container.getContainerMiscInfo().get(INSTRUCTION_CODE));
              int quantitySum =
                      child
                              .getContainerItems()
                              .stream()
                              .map(ContainerItem::getQuantity)
                              .reduce(0, Integer::sum);
              if (RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType().equals(instructionCode)
                      || (quantitySum < child.getContainerItems().get(0).getVnpkQty()
                      && (RxInstructionType.RX_SER_CNTR_CASE_SCAN
                      .getInstructionType()
                      .equalsIgnoreCase(instructionCode)
                      || RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT
                      .getInstructionType()
                      .equalsIgnoreCase(instructionCode)
                      || (RxInstructionType.RX_SER_BUILD_CONTAINER
                      .getInstructionType()
                      .equalsIgnoreCase(instructionCode) && container.getPalletFlowInMultiSku())))) {
                log.info("partial case or sending scanned EAs for {}", child.getTrackingId());
                ContainerItem containerItem = child.getContainerItems().get(0);
                List<ContainerItem> containerItems = new ArrayList<>();
                Container unitContainer = getContainerWithChildsByTrackingId(child.getTrackingId());
                unitContainer
                        .getChildContainers()
                        .forEach(
                                unitChild -> {
                                  ContainerItem newContainerItem =
                                          gson.fromJson(gson.toJson(containerItem), ContainerItem.class);
                                  ContainerItem unitChildContainerItem = unitChild.getContainerItems().get(0);
                                  newContainerItem.setGtin(unitChildContainerItem.getGtin());
                                  newContainerItem.setSerial(unitChildContainerItem.getSerial());
                                  newContainerItem.setLotNumber(unitChildContainerItem.getLotNumber());
                                  newContainerItem.setExpiryDate(unitChildContainerItem.getExpiryDate());
                                  newContainerItem.setQuantity(1);
                                  newContainerItem.setQuantityUOM(Uom.WHPK);
                                  newContainerItem.setAudited(true);
                                  newContainerItem.setNationalDrugCode(String.valueOf(ndc));
                                  newContainerItem.setIsDscsaExemptionInd(isDscsaExemptionInd.get());
                                  newContainerItem.setType(UNIT);
                                  containerItems.add(newContainerItem);
                                });
                child.setContainerItems(containerItems);
              } else {
                PackItemResponse packItemResponse = getUnitSerializedInfo(child, httpHeaders);
                List<Item> items = packItemResponse.getPacks().get(0).getItems();
                log.info("gdm returned {} items for {}", items.size(), child.getTrackingId());
                ContainerItem containerItem = child.getContainerItems().get(0);
                List<ContainerItem> containerItems = new ArrayList<>();
                items.forEach(
                        item -> {
                          String gtin = item.getGtin();
                          String serial = item.getSerial();
                          ManufactureDetail manufactureDetail = getManufactureDetailByPackItem(item, null);
                          String lot = manufactureDetail.getLot();
                          String expiry = manufactureDetail.getExpiryDate();
                          Optional<ContainerItem> scannedEach =
                                  child
                                          .getContainerItems()
                                          .stream()
                                          .filter(
                                                  c ->
                                                          c.getGtin().equalsIgnoreCase(gtin)
                                                                  && c.getSerial().equalsIgnoreCase(serial)
                                                                  && isSameDay(c.getExpiryDate(), parseDate(expiry))
                                                                  && c.getLotNumber().equalsIgnoreCase(lot))
                                          .findFirst();
                          ContainerItem newContainerItem =
                                  gson.fromJson(gson.toJson(containerItem), ContainerItem.class);
                          newContainerItem.setGtin(gtin);
                          newContainerItem.setSerial(serial);
                          newContainerItem.setLotNumber(lot);
                          newContainerItem.setExpiryDate(parseDate(expiry));
                          newContainerItem.setQuantity(1);
                          newContainerItem.setQuantityUOM(Uom.WHPK);
                          newContainerItem.setAudited(scannedEach.isPresent());
                          newContainerItem.setNationalDrugCode(String.valueOf(ndc));
                          newContainerItem.setIsDscsaExemptionInd(isDscsaExemptionInd.get());
                          newContainerItem.setType(UNIT);
                          containerItems.add(newContainerItem);
                        });
                child.setContainerItems(containerItems);
              }
              container.setChildContainers(singleton(child));
              eventId.set(child.getParentTrackingId());
            });

    Map<String, Object> headers = getForwardablHeaderWithTenantData(httpHeaders);
    // FOR INTEGRATION TEST ONLY
    boolean isRxAtlasInventoryTestEnabled =
            tenantSpecificConfigReader.getConfiguredFeatureFlag(
                    TenantContext.getFacilityNum().toString(), ENABLE_ATLAS_INVENTORY_TEST, false);
    if (isRxAtlasInventoryTestEnabled) {
      headers.put(TENENT_FACLITYNUM, "32709");
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
    }
    //
    // compute quantity as pallet quantity/pallet whpk quantity
    ContainerItem containerItem = container.getContainerItems().get(0);
    int newQuantity =
            conversionToWareHousePack(
                    containerItem.getQuantity(), Uom.EACHES, 0, containerItem.getWhpkQty());
    containerItem.setQuantity(newQuantity);
    containerItem.setQuantityUOM(Uom.WHPK);
    containerItem.setNationalDrugCode(String.valueOf(ndc));
    containerItem.setIsDscsaExemptionInd(isDscsaExemptionInd.get());
    containerItem.setType(PALLET);
    if (childContainer.isPresent()
            && !CollectionUtils.isEmpty(childContainer.get().getContainerItems())) {
      containerItem.setGtin(childContainer.get().getContainerItems().get(0).getGtin());
    }
    String body = gsonBuilder.toJson(rxContainerTransformer.transformList(singletonList(container)));
    headers.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    headers.put(ReceivingConstants.API_VERSION, ReceivingConstants.API_VERSION_VALUE);
    headers.put(ATLAS_KAFKA_IDEMPOTENCY, eventId.get());
    headers.put(FLOW_NAME, RECEIVING);
    headers.put(MARKET_TYPE, MARKET_TYPE_PHARMACY);
    headers.put(DOCUMENT_TYPE, EPCIS_TEXT);
    headers.put(EVENT_STATUS, RECEIVED);
    MetaData metaData = MetaData.with("key", eventId);
    OutboxEvent outboxEvent =
            buildOutboxEvent(
                    headers,
                    body,
                    eventId.get(),
                    metaData,
                    outboxConfig.getOutboxPolicyKafkaInventory(),
                    Instant.now());
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  /**
   * utility method to call gdm unit serial api
   *
   * @param container child container
   * @param httpHeaders headers
   * @return gdm pack response
   */
  @SneakyThrows
  private PackItemResponse getUnitSerializedInfo(Container container, HttpHeaders httpHeaders) {
    UnitSerialRequest unitSerialRequest = new UnitSerialRequest();
    List<UnitRequestMap> identifier = new ArrayList<>();

    if (null == container.getSsccNumber()) {
      if (null == container.getGtin() && null == container.getSerial()) {
        log.error("Container {} missing sscc and sgtin", container.getTrackingId());
        throw new ReceivingException(
                "Container " + container.getTrackingId() + " missing sscc and sgtin");
      } else {
        UnitRequestMap gtinIdentifier = new UnitRequestMap();
        gtinIdentifier.setKey(KEY_GTIN);
        gtinIdentifier.setValue(container.getGtin());
        identifier.add(gtinIdentifier);
        UnitRequestMap serialIdentifier = new UnitRequestMap();
        serialIdentifier.setKey(KEY_SERIAL);
        serialIdentifier.setValue(container.getSerial());
        identifier.add(serialIdentifier);
      }
    } else {
      UnitRequestMap ssccIdentifier = new UnitRequestMap();
      ssccIdentifier.setKey(SSCC);
      ssccIdentifier.setValue(container.getSsccNumber());
      identifier.add(ssccIdentifier);
    }
    unitSerialRequest.setDeliveryNumber(container.getDeliveryNumber().toString());
    unitSerialRequest.setIdentifier(identifier);
    return rxDeliveryService.getUnitSerializedInfo(unitSerialRequest, httpHeaders);
  }

  /**
   * utility method to parse date string to object
   *
   * @param date date string
   * @return date object
   */
  @SneakyThrows
  protected Date parseDate(String date) {
    return DateUtils.parseDate(date, SIMPLE_DATE);
  }

  /**
   * utility method to get container by tracking id (also for use in lambdas)
   *
   * @param trackingId tracking id
   * @return container
   */
  @SneakyThrows
  public Container getContainerWithChildsByTrackingId(String trackingId) {
    return containerService.getContainerWithChildsByTrackingId(trackingId, true);
  }

  /**
   * persist instruction, container with child, create print job, and outbox event
   *
   * @param instruction instruction
   * @param container container
   * @param userId userId
   * @param outboxEvent outbox event
   */
  @Transactional
  @InjectTenantFilter
  public void persist(
          Instruction instruction, Container container, String userId, OutboxEvent outboxEvent) {
    log.info("Persist in Instruction , Container  and Outbox ");
    rxInstructionHelperService.persist(container, instruction, userId);
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  /**
   * persist instruction, containers, container items, and outbox events
   *
   * @param instruction instruction
   * @param containers pending containers
   * @param containerItems pending container items
   * @param outboxEvents outbox events
   */
  @Transactional
  @InjectTenantFilter
  public void persist(
          Instruction instruction,
          List<Container> containers,
          List<ContainerItem> containerItems,
          List<OutboxEvent> outboxEvents) {
    instructionPersisterService.saveInstruction(instruction);
    containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
    outboxEventSinkService.saveAllEvent(outboxEvents);
  }

  /**
   * construct epcis headers and outbox for given policy
   *
   * @param epcisRequest epcis request object
   * @param httpHeaders headers
   * @param trackingId parent tracking id
   * @param executionTs outbox execution ts
   */
  public void outboxEpcisEvent(
          EpcisRequest epcisRequest, HttpHeaders httpHeaders, String trackingId, Instant executionTs) {
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String eventId = countryCode + "_" + facilityNum + "_" + trackingId + "_PS";
    String correlationId = UUID.randomUUID().toString();
    String epcisJson = gson.toJson(epcisRequest);
    Map<String, Object> headers = constructEpcisHeaders(correlationId, httpHeaders, facilityNum);
    OutboxEvent outboxEvent =
            buildOutboxEvent(
                    headers,
                    epcisJson,
                    eventId,
                    MetaData.emptyInstance(),
                    outboxConfig.getOutboxPolicyHttpPsV3Capture(),
                    executionTs);
    log.info(
            "ATTP eventId:{} | correlationId:{} | eventJson: {}", eventId, correlationId, epcisJson);
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  /**
   * construct epcis headers and outbox for given policy
   *
   * @param epcisRequestList epcis request list
   * @param httpHeaders headers
   * @param instructionId instruction id
   */
  public void outboxEpcisEvents(
          List<EpcisRequest> epcisRequestList, HttpHeaders httpHeaders, String instructionId) {
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String eventId = countryCode + "_" + facilityNum + "_" + instructionId + "_PS_ASN";
    String correlationId = UUID.randomUUID().toString();
    String epcisJson = gson.toJson(epcisRequestList);
    Map<String, Object> headers = constructEpcisHeaders(correlationId, httpHeaders, facilityNum);
    OutboxEvent outboxEvent =
            buildOutboxEvent(
                    headers,
                    epcisJson,
                    eventId,
                    MetaData.emptyInstance(),
                    outboxConfig.getOutboxPolicyHttpPsV2CaptureMany(),
                    Instant.now());
    log.info(
            "ASN ATTP eventId:{} | correlationId:{} | eventJson: {}",
            eventId,
            correlationId,
            epcisJson);
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  /**
   * construct epcis headers and outbox clubbed epcis events
   *
   * @param epcisRequests epcis request objects
   * @param httpHeaders headers
   * @param trackingId parent tracking id
   * @param executionTs outbox execution ts
   */
  public void outboxClubbedEpcisEvents(
          Set<EpcisRequest> epcisRequests,
          String clubbed,
          HttpHeaders httpHeaders,
          String trackingId,
          Instant executionTs) {
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    String eventId = countryCode + "_" + facilityNum + "_" + trackingId + "_PS";
    String correlationId = UUID.randomUUID().toString();
    String epcisJson = gson.toJson(epcisRequests);
    Map<String, Object> headers = constructEpcisHeaders(correlationId, httpHeaders, facilityNum);
    OutboxEvent outboxEvent =
            buildOutboxEvent(
                    headers,
                    epcisJson,
                    eventId,
                    MetaData.with("clubbed", clubbed),
                    outboxConfig.getOutboxPolicyHttpPsV3CaptureMany(),
                    executionTs);
    log.info(
            "CLUBBED ATTP eventId:{} | clubbed:{} | correlationId:{} | eventJson: {}",
            eventId,
            clubbed,
            correlationId,
            epcisJson);
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  /**
   * construct epcis headers
   *
   * @param correlationId correlation id
   * @param httpHeaders http headers
   * @param facilityNum facility
   * @return map of headers
   */
  public Map<String, Object> constructEpcisHeaders(
          String correlationId, HttpHeaders httpHeaders, int facilityNum) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ACCEPT, APPLICATION_JSON_VALUE);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    headers.put(WM_CORRELATIONID, correlationId);
    headers.put(WM_USERID, httpHeaders.getFirst(USER_ID_HEADER_KEY));
    headers.put(WM_SITEID, valueOf(facilityNum));
    headers.put(WM_CLIENT, WM_CLIENT_DC);
    headers.put(WM_WORKFLOW, WM_WORKFLOW_DSD);
    headers.put(WM_CONSUMER_ID, appConfig.getEpcisConsumerId());
    headers.put(WM_SVC_NAME, appConfig.getEpcisServiceName());
    headers.put(WM_SVC_ENV, appConfig.getEpcisServiceEnv());
    headers.put(WM_SVC_VERSION, appConfig.getEpcisServiceVersion());
    return headers;
  }

  @FeatureFlag(ENABLE_OUTBOX_CREATE_MOVES)
  public void constructAndOutboxCreateMoves(
          Instruction instruction, String parentTrackingId, HttpHeaders httpHeaders) {
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot(httpHeaders.getFirst(ReceivingConstants.MOVE_TO_LOCATION));

    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setPartialContainer(RxUtils.isPartialInstruction(instruction.getInstructionCode()));
    completeInstructionRequest.setSlotDetails(slotDetails);

    boolean isDCOneAtlasEnabled =
            tenantSpecificConfigReader.getConfiguredFeatureFlag(
                    TenantContext.getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);

    if (Objects.isNull(gson)) gson = new Gson();

    DeliveryDocument deliveryDocument =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    rxInstructionService.findSlotFromSmartSlotting(
            completeInstructionRequest,
            httpHeaders,
            instruction,
            deliveryDocumentLine,
            parentTrackingId,
            isDCOneAtlasEnabled);
  }
}
