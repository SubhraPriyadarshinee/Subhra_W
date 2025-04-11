package com.walmart.move.nim.receiving.rx.service.v2.instruction.complete;

import static com.walmart.move.nim.receiving.rx.common.RxUtils.buildOutboxEvent;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Sgtin;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ShipmentsContainersV2Request;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CompleteInstructionOutboxService extends RxCompleteInstructionOutboxHandler {

  @ManagedConfiguration private OutboxConfig outboxConfig;

  private Gson gsonBuilder;
  @Resource private Gson gson;

  @Resource private InstructionPersisterService instructionPersisterService;
  @Resource private RxDeliveryServiceImpl rxDeliveryServiceImpl;
  @Resource private ContainerService containerService;
  @Resource private Transformer<Container, ContainerDTO> rxContainerTransformer;
  @Resource private OutboxEventSinkService outboxEventSinkService;
  @Resource private InstructionFactory instructionFactory;
  @Resource private RxInstructionHelperService rxInstructionHelperService;

  @Override
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
   * @param palletContainer pallet container
   * @param instruction instruction
   * @param userId user id
   * @param slotDetails slot details
   * @param httpHeaders headers
   */
  @Override
  public void outboxCompleteInstruction(
      Container palletContainer,
      Instruction instruction,
      String userId,
      SlotDetails slotDetails,
      HttpHeaders httpHeaders) {
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    MetaData metaData = MetaData.with(CONTAINER_TRACKING_ID, palletContainer.getTrackingId());
    String eventId =
        getFacilityCountryCode() + "_" + getFacilityNum() + "_" + palletContainer.getTrackingId();
    OutboxEvent outboxEvent =
        buildOutboxEvent(
            headers,
            null,
            eventId,
            metaData,
            outboxConfig.getOutboxPolicyHttpPendingContainersV2(),
            Instant.now());

    persist(instruction, palletContainer, userId, outboxEvent);

    // create moves
    httpHeaders.set(ReceivingConstants.MOVE_TO_LOCATION, slotDetails.getSlot());
    constructAndOutboxCreateMoves(instruction, palletContainer.getTrackingId(), httpHeaders);
  }

  /**
   * creates pending containers, containerItems, updates instruction, and multiple outbox events for
   * each container and creates moves and sends attp events for all containers old and generated
   *
   * @param palletTrackingId pallet tracking id
   * @param httpHeaders headers
   * @throws ReceivingException error while fetching containers/instruction/no children
   */
  @Override
  public void pendingContainers(String palletTrackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    // misc
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
    boolean isSkipAttp = Boolean.parseBoolean(httpHeaders.getFirst(RxConstants.Headers.SKIP_ATTP));
    boolean isSkipInventory =
        Boolean.parseBoolean(httpHeaders.getFirst(RxConstants.Headers.SKIP_INVENTORY));
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(facilityNum);
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    headers.put(RxConstants.Headers.SKIP_INVENTORY, isSkipInventory);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    String eventId = countryCode + "_" + facilityNum + "_" + palletTrackingId + "_";
    List<Container> pendingContainers = new ArrayList<>();
    List<ContainerItem> pendingContainerItems = new ArrayList<>();
    List<OutboxEvent> outboxEvents = new ArrayList<>();

    // getters
    Container palletContainer = getContainerWithChildsByTrackingId(palletTrackingId);
    Set<Container> caseContainers = palletContainer.getChildContainers();

    Instruction instruction =
        instructionPersisterService.getInstructionById(palletContainer.getInstructionId());
    List<ContainerDetails> containerDetails = instruction.getChildContainers();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();

    // gdmRootContainer
    DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
            deliveryDocument.getGdmCurrentNodeDetail();
    SsccScanResponse.Container gdmRootContainer =
            gdmCurrentNodeDetail.getAdditionalInfo().getContainers().get(0);
    String gdmRootContainerId = gdmRootContainer.getId();
    eventId = eventId + gdmRootContainerId;

    // set a reference container
    Container referenceContainer;
    if (!caseContainers.isEmpty() && null != containerDetails) {
      referenceContainer = caseContainers.stream().findFirst().get();
    } else {
      log.error("[ESR] No caseContainers for trackingId {}", palletTrackingId);
      throw new ReceivingException(
          "[ESR] No caseContainers for trackingId " + palletTrackingId, HttpStatus.NO_CONTENT);
    }

    // prep pallet and case containers
    prepPalletAndCaseContainers(deliveryDocumentLine, palletContainer);
    containerService.enrichContainerForDcfin(deliveryDocument, palletContainer);
    Container outboxPalletContainer = gson.fromJson(gson.toJson(palletContainer), Container.class);

    // get containerMiscInfo details from our referenceContainer
    Map<String, Object> containerMiscInfo = referenceContainer.getContainerMiscInfo();

    // only create containers for [palletRcv] AND [auditQty != projectedRcvQty] AND
    // [!palletFlowInMultiSku]
    boolean isCreateContainers =
        RxInstructionType.RX_SER_BUILD_CONTAINER
                .getInstructionType()
                .equalsIgnoreCase(instruction.getInstructionCode())
            && additionalInfo.getAuditQty()
                != (instruction.getProjectedReceiveQty() / deliveryDocumentLine.getVendorPack())
            && !additionalInfo.isPalletFlowInMultiSku();

    // create containers logic START
    if (isCreateContainers) {
      // call gdm currentAndSiblings
      SsccScanResponse currentAndSiblingsResponse =
          getCurrentAndSiblings(
              containerMiscInfo, httpHeaders, palletContainer.getDeliveryNumber().toString());
      if (Objects.isNull(currentAndSiblingsResponse)) {
        log.info(
            "[ESR] GDM CurrentAndSiblings API did not respond, so not creating containers, returning...");
        return;
      }
      List<SsccScanResponse.Container> currentAndSiblingsContainers =
          currentAndSiblingsResponse.getContainers();

      processNewAndExistingCaseContainers(
              currentAndSiblingsContainers,
              caseContainers,
          referenceContainer,
          outboxPalletContainer,
          instruction,
          pendingContainers,
          pendingContainerItems,
          outboxEvents,
          httpHeaders,
          headers,
          eventId,
          userId);
    }
    // END

    else {
      processExistingCaseContainers(caseContainers, outboxPalletContainer, outboxEvents, headers, eventId);
    }

    containerService.enrichContainerForDcfin(deliveryDocument, palletContainer);

    log.info(
        "[ESR] pendingContainers: {}, pendingContainerItems: {}, instructionChildContainers: {}, outboxEvents: {}",
        pendingContainers.size(),
        pendingContainerItems.size(),
        containerDetails.size(),
        outboxEvents.size());
    persist(instruction, pendingContainers, pendingContainerItems, outboxEvents);
    log.info("[ESR] persisted instruction, pendingContainers, pendingContainerItems, outboxEvents");

    // send attp events
    if (!isSkipAttp) {
      constructAndOutboxAttpEvents(instruction, palletTrackingId, httpHeaders);
    }
  }

  private void prepPalletAndCaseContainers(DeliveryDocumentLine deliveryDocumentLine, Container palletContainer) {
    // getters
    AtomicReference<Map<String, String>> facilityMap = new AtomicReference<>();
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    Boolean isDscsaExemptionInd = additionalInfo.getIsDscsaExemptionInd();
    String documentType = EPCIS_TEXT;
    String documentNumber =
        deliveryDocumentLine.getShipmentDetailsList().get(0).getInboundShipmentDocId();
    Integer vendorNumber = deliveryDocumentLine.getVendorNbrDeptSeq();
    String palletSscc = deliveryDocumentLine.getPalletSSCC();
    String ndc = deliveryDocumentLine.getNdc();
    Set<Container> caseContainers = palletContainer.getChildContainers();

    // palletContainer
    palletContainer.setDocumentType(documentType);
    palletContainer.setDocumentNumber(documentNumber);
    palletContainer.getContainerItems().get(0).setVendorNumber(vendorNumber);
    palletContainer.setIsDscsaExemptionInd(isDscsaExemptionInd);
    palletContainer.setSsccNumber(palletSscc);
    // facility manipulation
    facilityMap.set(palletContainer.getFacility());
    if (facilityMap.get() != null) {
      facilityMap.get().put(COUNTRY_CODE, facilityMap.get().remove(TENENT_COUNTRY_CODE));
    }
    palletContainer.setFacility(facilityMap.get());

    // palletContainerItem
    ContainerItem palletContainerItem = palletContainer.getContainerItems().get(0);
    palletContainerItem.setVendorNumber(vendorNumber);
    int newQuantity =
        ReceivingUtils.conversionToWareHousePack(
            palletContainerItem.getQuantity(), Uom.EACHES, 0, palletContainerItem.getWhpkQty());
    palletContainerItem.setQuantity(newQuantity);
    palletContainerItem.setQuantityUOM(Uom.WHPK);
    palletContainerItem.setNationalDrugCode(ndc);
    palletContainerItem.setIsDscsaExemptionInd(isDscsaExemptionInd);
    palletContainerItem.setType(PALLET);

    AtomicReference<Date> expiryDateForPallet = new AtomicReference<>(new Date());
    // caseContainers
    caseContainers.forEach(
        caseContainer -> {
          caseContainer.setDocumentType(documentType);
          caseContainer.setDocumentNumber(documentNumber);
          caseContainer.setIsDscsaExemptionInd(isDscsaExemptionInd);
          caseContainer.setNationalDrugCode(ndc);
          caseContainer.setContainerType(SERIALIZED);
          caseContainer.setIsCompliancePack(additionalInfo.getIsCompliancePack());
          caseContainer.setPalletFlowInMultiSku(additionalInfo.isPalletFlowInMultiSku());

          // caseContainerItem
          ContainerItem caseContainerItem = caseContainer.getContainerItems().get(0);
          caseContainerItem.setIsDscsaExemptionInd(isDscsaExemptionInd);
          caseContainerItem.setNationalDrugCode(ndc);
          caseContainerItem.setVendorNumber(vendorNumber);

          // facility manipulation
          facilityMap.set(caseContainer.getFacility());
          if (facilityMap.get() != null) {
            facilityMap.get().put(COUNTRY_CODE, facilityMap.get().remove(TENENT_COUNTRY_CODE));
          }
          caseContainer.setFacility(facilityMap.get());
          //expiry date manipulation for Parent Container
          Optional<ContainerItem> containerItemWithExpiry = caseContainer.getContainerItems().stream().min(Comparator.comparing(ContainerItem::getExpiryDate));
          containerItemWithExpiry.ifPresent(containerItem -> expiryDateForPallet.set(containerItem.getExpiryDate()));
        });
    palletContainerItem.setExpiryDate(expiryDateForPallet.get());
  }

  private Container createCaseContainer(
      Container referenceContainer,
      SsccScanResponse.Container gdmContainer,
      List<Container> pendingContainers,
      String trackingId,
      String userId) {
    // getters

    // add newCaseContainer
    Container newCaseContainer = gson.fromJson(gson.toJson(referenceContainer), Container.class);
    newCaseContainer.setId(null);
    newCaseContainer.setTrackingId(trackingId);
    newCaseContainer.setCreateTs(new Date());
    newCaseContainer.setCreateUser(userId);
    newCaseContainer.setLastChangedTs(new Date());
    newCaseContainer.setLastChangedUser(userId);
    Map<String, Object> newContainerMiscInfo = newCaseContainer.getContainerMiscInfo();

    // update containerMiscInfo
    newContainerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, gdmContainer.getShipmentNumber());
    newContainerMiscInfo.put(RxConstants.GDM_CONTAINER_ID,gdmContainer.getId());
    newContainerMiscInfo.put(RxConstants.GDM_PARENT_CONTAINER_ID,gdmContainer.getParentId());
    newContainerMiscInfo.put(RxConstants.TOP_LEVEL_CONTAINER_SSCC,gdmContainer.getTopLevelContainerSscc());
    newContainerMiscInfo.put(RxConstants.TOP_LEVEL_CONTAINER_ID,gdmContainer.getTopLevelContainerId());
    newContainerMiscInfo.put(ReceivingConstants.KEY_SSCC,gdmContainer.getSscc());
    newContainerMiscInfo.put(ReceivingConstants.KEY_GTIN,gdmContainer.getGtin());
    newContainerMiscInfo.put(ReceivingConstants.KEY_SERIAL,gdmContainer.getSerial());
    newContainerMiscInfo.put(ReceivingConstants.KEY_LOT,gdmContainer.getLotNumber());
    newContainerMiscInfo.put(ReceivingConstants.KEY_EXPIRY_DATE,gdmContainer.getExpiryDate());
    newContainerMiscInfo.put(RxConstants.UNIT_COUNT,gdmContainer.getUnitCount());
    newContainerMiscInfo.put(RxConstants.CHILD_COUNT,gdmContainer.getChildCount());
    newContainerMiscInfo.put(RxConstants.HINTS,gdmContainer.getHints());
    newContainerMiscInfo.put(IS_AUDITED, false);

    newCaseContainer.setAudited(false);
    newCaseContainer.setGtin(gdmContainer.getGtin());
    newCaseContainer.setSerial(gdmContainer.getSerial());
    newCaseContainer.setLotNumber(gdmContainer.getLotNumber());
    newCaseContainer.setExpiryDate(gdmContainer.getExpiryDate());
    newCaseContainer.setSsccNumber(gdmContainer.getSscc());

    pendingContainers.add(newCaseContainer);
    log.info("[ESR] Added newCaseContainer for {}", trackingId);
    return newCaseContainer;
  }

  private void createCaseContainerItem(
      Container newCaseContainer,
      SsccScanResponse.Container gdmContainer,
      List<ContainerItem> pendingContainerItems,
      String trackingId) {
    // getters

    // add newCaseContainerItem
    ContainerItem newCaseContainerItem =
        gson.fromJson(
            gson.toJson(newCaseContainer.getContainerItems().get(0)), ContainerItem.class);
    newCaseContainerItem.setId(null);
    newCaseContainerItem.setTrackingId(trackingId);
    newCaseContainerItem.setGtin(gdmContainer.getGtin());
    newCaseContainerItem.setSerial(gdmContainer.getSerial());
    newCaseContainerItem.setLotNumber(gdmContainer.getLotNumber());
    newCaseContainerItem.setExpiryDate(parseDate(gdmContainer.getExpiryDate()));
    newCaseContainerItem.setAudited(false);

    pendingContainerItems.add(newCaseContainerItem);
    newCaseContainer.setContainerItems(Collections.singletonList(newCaseContainerItem));
    log.info("[ESR] Added newCaseContainerItem {}", trackingId);
  }

  private void updateInstruction(
      SsccScanResponse.Container gdmContainer,
      Container outboxPalletContainer,
      Container newCaseContainer,
      Instruction instruction,
      List<OutboxEvent> outboxEvents,
      String trackingId,
      Map<String, Object> headers,
      String eventId) {
    // getters
    List<ContainerDetails> instructionChildContainers = instruction.getChildContainers();

    // update instruction
    Content newInstructionChildContainerContent =
        gson.fromJson(
            gson.toJson(instructionChildContainers.get(0).getContents().get(0)), Content.class);
    newInstructionChildContainerContent.setGtin(gdmContainer.getGtin());
    newInstructionChildContainerContent.setSerial(gdmContainer.getSerial());
    newInstructionChildContainerContent.setLot(gdmContainer.getLotNumber());
    ContainerDetails newInstructionChildContainer = new ContainerDetails();
    newInstructionChildContainer.setTrackingId(trackingId);
    newInstructionChildContainer.setContents(
        Collections.singletonList(newInstructionChildContainerContent));
    instructionChildContainers.add(newInstructionChildContainer);
    instruction.setChildContainers(instructionChildContainers);
    log.info(
        "[ESR] Added newInstructionChildContainer {} for instructionId {}",
        trackingId,
        instruction.getId());

    outboxPalletContainer.setChildContainers(Collections.singleton(newCaseContainer));

    outboxEvents.add(
        buildOutboxEvent(
            headers,
            gsonBuilder.toJson(outboxPalletContainer),
            eventId,
            MetaData.emptyInstance(),
            outboxConfig.getOutboxPolicyHttpEachesDetailV2(),
            Instant.now()));
  }

  private void processNewAndExistingCaseContainers(
          List<SsccScanResponse.Container> currentAndSiblingsContainers,
          Set<Container> caseContainers,
      Container referenceContainer,
      Container outboxPalletContainer,
      Instruction instruction,
      List<Container> pendingContainers,
      List<ContainerItem> pendingContainerItems,
      List<OutboxEvent> outboxEvents,
      HttpHeaders httpHeaders,
      Map<String, Object> headers,
      String eventId,
      String userId) {
    currentAndSiblingsContainers
        .stream()
        .filter(
            x ->
                CollectionUtils.containsAny(
                        x.getHints(),
                        Arrays.asList(
                            RxConstants.GdmHints.CASE_PACK_ITEM,
                            RxConstants.GdmHints.SINGLE_SKU_PACKAGE)) // only CASE_PACK/SINGLE_SKU hints
                    && RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                        x.getReceivingStatus()) // only RECEIVED containers
                    && (Objects.nonNull(x.getGtin())
                        && Objects.nonNull(x.getSerial())
                        && Objects.nonNull(x.getLotNumber())
                        && Objects.nonNull(
                            x.getExpiryDate()))) // ignore if 2d details are not present
        .forEach(
            gdmContainer -> {
              Optional<Container> caseContainer =
                      caseContainers
                              .stream()
                              .filter(container ->
                                      container
                                              .getContainerItems()
                                              .stream()
                                              .anyMatch(x
                                                              -> (x.getGtin().equalsIgnoreCase(gdmContainer.getGtin())
                                                      && x.getSerial().equalsIgnoreCase(gdmContainer.getSerial())
                                                      )
                                              )).findFirst();
              // createCaseContainer/createCaseContainerItem/updateInstruction
              if (!caseContainer.isPresent()) {
                String trackingId = rxInstructionHelperService.generateTrackingId(httpHeaders);
                Container newCaseContainer =
                    createCaseContainer(referenceContainer, gdmContainer, pendingContainers, trackingId, userId);

                createCaseContainerItem(newCaseContainer, gdmContainer, pendingContainerItems, trackingId);

                updateInstruction(
                    gdmContainer,
                    outboxPalletContainer,
                    newCaseContainer,
                    instruction,
                    outboxEvents,
                        trackingId,
                    headers,
                    eventId);

              } else
                // existing container
                caseContainer.ifPresent(
                    container ->
                        processExistingCaseContainer(
                            container, gdmContainer, outboxPalletContainer, outboxEvents, headers, eventId));
            });
  }

  private void processExistingCaseContainers(
      Set<Container> caseContainers,
      Container outboxPalletContainer,
      List<OutboxEvent> outboxEvents,
      Map<String, Object> headers,
      String eventId) {
    // use containerMiscInfo of each caseContainer to populate fields required for existing
    // caseContainers
    caseContainers.forEach(
        caseContainer -> {
          Map<String, Object> caseContainerMiscInfo = caseContainer.getContainerMiscInfo();
          String sscc = (String) caseContainerMiscInfo.get(KEY_SSCC);
          String gtin = (String) caseContainerMiscInfo.get(KEY_GTIN);
          String serial = (String) caseContainerMiscInfo.get(KEY_SERIAL);
          String lot = (String) caseContainerMiscInfo.get(KEY_LOT);
          String expiry = (String) caseContainerMiscInfo.get(KEY_EXPIRY_DATE);

          // mock a new gdmContainer so we can reuse
          SsccScanResponse.Container gdmContainer = new SsccScanResponse.Container();
          gdmContainer.setSscc(sscc);
          gdmContainer.setSerial(serial);
          gdmContainer.setGtin(gtin);
          gdmContainer.setLotNumber(lot);
          gdmContainer.setExpiryDate(expiry);

          processExistingCaseContainer(
              caseContainer, gdmContainer, outboxPalletContainer, outboxEvents, headers, eventId);
        });
  }

  private void processExistingCaseContainer(
      Container caseContainer,
      SsccScanResponse.Container gdmContainer,
      Container outboxPalletContainer,
      List<OutboxEvent> outboxEvents,
      Map<String, Object> headers,
      String eventId) {
    // for existing containers, set these fields since inventory needs
    caseContainer.setGtin(gdmContainer.getGtin());
    caseContainer.setSerial(gdmContainer.getSerial());
    caseContainer.setLotNumber(gdmContainer.getLotNumber());
    caseContainer.setExpiryDate(gdmContainer.getExpiryDate());
    caseContainer.setSsccNumber(gdmContainer.getSscc());

    outboxPalletContainer.setChildContainers(Collections.singleton(caseContainer));

    outboxEvents.add(
        buildOutboxEvent(
            headers,
            gsonBuilder.toJson(outboxPalletContainer),
            eventId,
            MetaData.emptyInstance(),
            outboxConfig.getOutboxPolicyHttpEachesDetailV2(),
            Instant.now()));

    log.info("[ESR] processExistingCaseContainers() completed...");
  }

  private void constructAndOutboxAttpEvents(Instruction instruction, String palletTrackingId, HttpHeaders httpHeaders) {
    Container updatedPalletContainer = getContainerWithChildsByTrackingId(palletTrackingId);
    instructionFactory
        .getEpcisPostingService(instruction.getReceivingMethod())
        .publishSerializedData(updatedPalletContainer, instruction, httpHeaders);
  }

  /**
   * [calls gdm to fetch unit details for non partials] or [fetch unit containers from db for
   * partials] transforms to inventory container format, and creates outbox event to publish to
   * inventory kafka
   *
   * @param palletContainer pallet container
   * @param httpHeaders headers
   */
  @Override
  public void eachesDetail(Container palletContainer, HttpHeaders httpHeaders) {
    Optional<ContainerItem> palletContainerItem =
        palletContainer.getContainerItems().stream().findFirst();
    Optional<Container> caseContainerOptional =
        palletContainer.getChildContainers().stream().findFirst();
    AtomicReference<String> eventId = new AtomicReference<>();
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    // no need to process further since inventory publish is skipped
    if (Boolean.parseBoolean(httpHeaders.getFirst(RxConstants.Headers.SKIP_INVENTORY))) {
      log.info("[ESR] Skipping eaches-detail since skipInventory header was passed, returning...");
      return;
    }

    // process
    processNewAndExistingUnitDetails(
        caseContainerOptional, palletContainer, palletContainerItem, eventId, httpHeaders);

    // build and persist outbox message
    String body =
        gsonBuilder.toJson(
            rxContainerTransformer.transformList(Collections.singletonList(palletContainer)));
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

  private void processNewAndExistingUnitDetails(
      Optional<Container> caseContainerOptional,
      Container palletContainer,
      Optional<ContainerItem> palletContainerItem,
      AtomicReference<String> eventId,
      HttpHeaders httpHeaders) {
    caseContainerOptional.ifPresent(
        caseContainer -> {
          // for [partialRcv] or [qtySum < vnpk and caseRcv or [palletRcv and palletFlowInMultiSku]]
          // then send only scanned EAs
          String instructionCode =
              String.valueOf(
                  palletContainer.getContainerMiscInfo().get(RxConstants.INSTRUCTION_CODE));
          int quantitySum =
              caseContainer
                  .getContainerItems()
                  .stream()
                  .map(ContainerItem::getQuantity)
                  .reduce(0, Integer::sum);
          if (RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType().equals(instructionCode)
              || (quantitySum < caseContainer.getContainerItems().get(0).getVnpkQty()
                  && (Arrays.asList(
                              RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType(),
                              RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT.getInstructionType())
                          .contains(instructionCode)
                      || (RxInstructionType.RX_SER_BUILD_CONTAINER
                              .getInstructionType()
                              .equalsIgnoreCase(instructionCode)
                          && palletContainer.getPalletFlowInMultiSku())))) {
            processPartialCaseOrScannedUnits(caseContainer);
          } else {
            processGdmUnits(caseContainer, httpHeaders);
          }

          palletContainer.setChildContainers(Collections.singleton(caseContainer));
          eventId.set(caseContainer.getParentTrackingId());

          // set pallet.content.gtin = case.content.gtin
          Optional<ContainerItem> caseContainerItem =
              caseContainer.getContainerItems().stream().findFirst();
          if (palletContainerItem.isPresent() && caseContainerItem.isPresent()) {
            palletContainerItem.get().setGtin(caseContainerItem.get().getGtin());
          }
        });
  }

  private void processPartialCaseOrScannedUnits(Container caseContainer) {
    log.info("[ESR] partialCase or sending scanned EAs for {}", caseContainer.getTrackingId());
    ContainerItem caseContainerItem = caseContainer.getContainerItems().get(0);
    List<ContainerItem> unitContainerItems = new ArrayList<>();
    Container unitContainer = getContainerWithChildsByTrackingId(caseContainer.getTrackingId());
    unitContainer
        .getChildContainers()
        .forEach(
            unit -> {
              ContainerItem newUnitContainerItem =
                  gson.fromJson(gson.toJson(caseContainerItem), ContainerItem.class);
              ContainerItem unitContainerItem = unit.getContainerItems().get(0);

              newUnitContainerItem.setGtin(unitContainerItem.getGtin());
              newUnitContainerItem.setSerial(unitContainerItem.getSerial());
              newUnitContainerItem.setLotNumber(unitContainerItem.getLotNumber());
              newUnitContainerItem.setExpiryDate(unitContainerItem.getExpiryDate());
              newUnitContainerItem.setQuantity(1);
              newUnitContainerItem.setQuantityUOM(Uom.WHPK);
              newUnitContainerItem.setAudited(true);
              newUnitContainerItem.setType(UNIT);

              unitContainerItems.add(newUnitContainerItem);
            });
    caseContainer.setContainerItems(unitContainerItems);
  }

  private void processGdmUnits(Container caseContainer, HttpHeaders httpHeaders) {
    ContainerItem containerItem = caseContainer.getContainerItems().get(0);
    List<ContainerItem> unitContainerItems = new ArrayList<>();
    SsccScanResponse unitLevelContainers = getUnitLevelContainers(caseContainer, httpHeaders);
    List<SsccScanResponse.Container> gdmUnitLevelContainers = unitLevelContainers.getContainers();
    log.info(
        "[ESR] GDM unitLevelContainers returned {} units for {}",
        gdmUnitLevelContainers.size(),
        caseContainer.getTrackingId());

    gdmUnitLevelContainers
        .stream()
        .filter(
            x ->
                RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                    x.getReceivingStatus())) // only RECEIVED units
        .forEach(
            gdmUnitLevelContainer -> {
              String gtin = gdmUnitLevelContainer.getGtin();
              String serial = gdmUnitLevelContainer.getSerial();
              String lot = gdmUnitLevelContainer.getLotNumber();
              String expiry = gdmUnitLevelContainer.getExpiryDate();

              Optional<ContainerItem> scannedEach =
                  caseContainer
                      .getContainerItems()
                      .stream()
                      .filter(
                          c ->
                              c.getGtin().equalsIgnoreCase(gtin)
                                  && c.getSerial().equalsIgnoreCase(serial)
                                  && DateUtils.isSameDay(c.getExpiryDate(), parseDate(expiry))
                                  && c.getLotNumber().equalsIgnoreCase(lot))
                      .findFirst();
              ContainerItem newUnitContainerItem =
                  gson.fromJson(gson.toJson(containerItem), ContainerItem.class);

              newUnitContainerItem.setGtin(gtin);
              newUnitContainerItem.setSerial(serial);
              newUnitContainerItem.setLotNumber(lot);
              newUnitContainerItem.setExpiryDate(parseDate(expiry));
              newUnitContainerItem.setQuantity(1);
              newUnitContainerItem.setQuantityUOM(Uom.WHPK);
              newUnitContainerItem.setAudited(scannedEach.isPresent());
              newUnitContainerItem.setType(UNIT);

              unitContainerItems.add(newUnitContainerItem);
            });
    caseContainer.setContainerItems(unitContainerItems);
  }

  /** ########################### GDM API CALLS ########################### */
  public SsccScanResponse getCurrentAndSiblings(
      Map<String, Object> containerMiscInfo, HttpHeaders httpHeaders, String deliveryNumber) {
    // construct currentAndSiblingsRequest
    ShipmentsContainersV2Request currentAndSiblingsRequest = new ShipmentsContainersV2Request();
    currentAndSiblingsRequest.setDeliveryNumber(deliveryNumber);

    String sscc = (String) containerMiscInfo.get(KEY_SSCC);
    if (StringUtils.isNotBlank(sscc)) { // sscc
      currentAndSiblingsRequest.setSscc(sscc);
    } else { // sgtin
      String serial = (String) containerMiscInfo.get(KEY_SERIAL);
      String gtin = (String) containerMiscInfo.get(KEY_GTIN);
      currentAndSiblingsRequest.setSgtin(new Sgtin(serial, gtin));
    }

    // queryParams
    Map<String, String> queryParams = Collections.emptyMap();

    // currentAndSiblingsResponse
    return rxDeliveryServiceImpl.getCurrentAndSiblings(
        currentAndSiblingsRequest, httpHeaders, queryParams);
  }

  public SsccScanResponse getUnitLevelContainers(Container container, HttpHeaders httpHeaders) {
    // construct unitLevelContainersRequest
    ShipmentsContainersV2Request unitLevelContainersRequest = new ShipmentsContainersV2Request();
    unitLevelContainersRequest.setDeliveryNumber(container.getDeliveryNumber().toString());

    String sscc = container.getSsccNumber();
    if (StringUtils.isNotBlank(sscc)) { // sscc
      unitLevelContainersRequest.setSscc(sscc);
    } else { // sgtin
      String serial = container.getSerial();
      String gtin = container.getGtin();
      unitLevelContainersRequest.setSgtin(new Sgtin(serial, gtin));
    }

    // unitLevelContainersResponse
    return rxDeliveryServiceImpl.getUnitLevelContainers(unitLevelContainersRequest, httpHeaders);
  }

  private boolean isHandleAsCasePack(Instruction instruction) {
    String instructionPackageInfo = instruction.getInstructionCreatedByPackageInfo();
    SsccScanResponse.Container instructionPack = gson.fromJson(instructionPackageInfo,  SsccScanResponse.Container.class);
    return CollectionUtils.containsAny(instructionPack.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK);
  }
}
