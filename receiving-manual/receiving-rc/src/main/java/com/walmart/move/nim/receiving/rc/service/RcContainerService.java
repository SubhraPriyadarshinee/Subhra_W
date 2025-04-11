package com.walmart.move.nim.receiving.rc.service;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_CREATE_CONTAINER_REQUEST;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.ItemTrackerRequest;
import com.walmart.move.nim.receiving.core.service.AbstractContainerService;
import com.walmart.move.nim.receiving.core.service.ItemTrackerService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.Purge;
import com.walmart.move.nim.receiving.rc.common.RcUtils;
import com.walmart.move.nim.receiving.rc.config.RcManagedConfig;
import com.walmart.move.nim.receiving.rc.contants.ActionType;
import com.walmart.move.nim.receiving.rc.contants.ItemFeedback;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.CategoryType;
import com.walmart.move.nim.receiving.rc.model.container.RcContainer;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerAdditionalAttributes;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerDetails;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowCreateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.ReceiveContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.UpdateContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.UpdateReturnOrderDataRequest;
import com.walmart.move.nim.receiving.rc.model.gad.Answers;
import com.walmart.move.nim.receiving.rc.model.gad.Disposition;
import com.walmart.move.nim.receiving.rc.model.gad.OptionsItem;
import com.walmart.move.nim.receiving.rc.model.gdm.ReturnOrderLine;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrderLine;
import com.walmart.move.nim.receiving.rc.model.item.ItemDetails;
import com.walmart.move.nim.receiving.rc.repositories.ContainerRLogRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowItemRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowRepository;
import com.walmart.move.nim.receiving.rc.transformer.ReceivingWorkflowTransformer;
import com.walmart.move.nim.receiving.rc.util.ReceivingWorkflowUtil;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ItemTrackerCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class RcContainerService extends AbstractContainerService implements Purge {

  private static final Logger LOGGER = LoggerFactory.getLogger(RcContainerService.class);
  @Autowired private JmsPublisher jmsPublisher;
  @Autowired private ContainerRLogRepository containerRLogRepository;
  @Autowired private ItemTrackerService itemTrackerService;
  @Autowired private Gson gson;
  @Autowired private ReceivingWorkflowTransformer receivingWorkflowTransformer;
  @Autowired private RcWorkflowService rcWorkflowService;
  @Autowired private ReceivingWorkflowUtil receivingWorkflowUtil;

  @Autowired private ReceivingWorkflowRepository receivingWorkflowRepository;

  @Autowired ReceivingWorkflowItemRepository receivingWorkflowItemRepository;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @ManagedConfiguration private RcManagedConfig rcManagedConfig;

  @ManagedConfiguration private MaasTopics maasTopics;

  private Gson gsonWithDateAdapter;

  public RcContainerService() {
    gsonWithDateAdapter =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  /**
   * Create a container for Reverse Logistics.
   *
   * @param containerRequest
   * @param httpHeaders
   */
  @Transactional
  @InjectTenantFilter
  public RcContainerDetails receiveContainer(
      ReceiveContainerRequest containerRequest, HttpHeaders httpHeaders) {
    // Upfront validations for requests that require a workflow creation
    if (receivingWorkflowUtil.isWorkflowCreationRequired(containerRequest.getDisposition())) {
      receivingWorkflowUtil.validateWorkflowCreationAttributes(containerRequest);
    }

    String trackingId;
    // if trackingId is present in the request, use that for creating container, else create a new
    // LPN
    if (StringUtils.hasLength(containerRequest.getTrackingId())) {
      trackingId = containerRequest.getTrackingId();
      Optional<ContainerRLog> optionalContainerRLog =
          containerRLogRepository.findByTrackingId(trackingId);
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_ALREADY_EXISTS_FOR_TRACKING_ID_ERROR_MSG,
              trackingId);
      optionalContainerRLog.ifPresent(
          containerRLog -> {
            throw new ReceivingBadDataException(INVALID_CREATE_CONTAINER_REQUEST, errorDescription);
          });
    } else {
      trackingId = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    }
    if (StringUtils.isEmpty(trackingId)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.LPNS_NOT_FOUND, ReceivingConstants.LPNS_NOT_FOUND);
    }
    updateContainerCreationRequest(containerRequest);
    ContainerRLog container = createContainer(containerRequest, trackingId);
    container = containerRLogRepository.save(container);

    LOGGER.info("Successfully created container: {}", gsonWithDateAdapter.toJson(container));
    RcContainerDetails rcContainerDetails =
        RcContainerDetails.builder().containerRLog(container).build();

    if (receivingWorkflowUtil.isWorkflowCreationRequired(containerRequest.getDisposition())) {
      RcWorkflowCreateRequest workflowCreateRequest =
          receivingWorkflowTransformer.transformContainerToWorkflowRequest(
              containerRequest, WorkflowType.FRAUD, container.getTrackingId());
      ReceivingWorkflow receivingWorkflow =
          rcWorkflowService.createWorkflow(workflowCreateRequest, httpHeaders, false);
      LOGGER.info("Successfully created workflow: {}", receivingWorkflow.toString());
      rcContainerDetails.setReceivingWorkflow(receivingWorkflow);
    }

    if (!CollectionUtils.isEmpty(containerRequest.getReasonCodes())) {
      List<ItemTrackerRequest> itemTrackerRequests = new ArrayList<>();
      for (String reasonCode : containerRequest.getReasonCodes()) {
        ItemTrackerRequest itemTrackerRequest = new ItemTrackerRequest();
        itemTrackerRequest.setTrackingId(containerRequest.getScannedLabel());
        itemTrackerRequest.setGtin(containerRequest.getScannedItemLabel());
        itemTrackerRequest.setReasonCode(reasonCode);
        itemTrackerRequests.add(itemTrackerRequest);
      }
      rcContainerDetails.setItemTrackers(itemTrackerService.trackItems(itemTrackerRequests));
    }
    boolean ignoreSct = false;
    String ignoreWfs = null;
    String ignoreRap = null;
    if (!Objects.isNull(container)
        && !Objects.isNull(container.getReturnOrderNumber())
        && !Objects.isNull(container.getReturnOrderLineNumber())) {
      ignoreSct = false;
    } else {
      ignoreSct = true;
      ignoreWfs = ReceivingConstants.RC_MISSING_RETURN_INITIATED;
      ignoreRap = ReceivingConstants.RECEIPT;
    }
    publishContainer(
        rcContainerDetails,
        httpHeaders,
        Optional.ofNullable(containerRequest.getActionType()).orElse(ActionType.RECEIPT),
        ignoreSct,
        ignoreRap,
        ignoreWfs);
    return rcContainerDetails;
  }

  @Transactional
  @InjectTenantFilter
  public RcContainerDetails updateContainer(
      String trackingId, UpdateContainerRequest updateContainerRequest, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Beginning to update container with tracking ID - {}, request: {}",
        trackingId,
        updateContainerRequest);

    Optional<ContainerRLog> optionalContainerRLog =
        containerRLogRepository.findByTrackingId(trackingId);

    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
            trackingId);
    ContainerRLog container =
        optionalContainerRLog.orElseThrow(
            () ->
                new ReceivingDataNotFoundException(
                    ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription));
    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowByWorkflowId(updateContainerRequest.getWorkflowId());
    if (receivingWorkflowUtil.isWorkflowCreationRequired(updateContainerRequest.getDisposition())
        && Objects.isNull(receivingWorkflow)) {
      RcWorkflowCreateRequest workflowCreateRequest =
          receivingWorkflowTransformer.transformContainerToWorkflowRequest(
              transformUpdateContainerToReceiveContainer(updateContainerRequest),
              WorkflowType.FRAUD,
              container.getTrackingId());
      receivingWorkflow =
          rcWorkflowService.createWorkflow(workflowCreateRequest, httpHeaders, false);
      LOGGER.info("Successfully created workflow: {}", receivingWorkflow);
    }

    updateContainer(container, updateContainerRequest);
    containerRLogRepository.save(container);
    LOGGER.info("Successfully updated container: {}", gsonWithDateAdapter.toJson(container));

    RcContainerDetails rcContainerDetails =
        RcContainerDetails.builder()
            .containerRLog(container)
            .receivingWorkflow(receivingWorkflow)
            .build();

    publishContainer(
        rcContainerDetails,
        httpHeaders,
        ActionType.RECEIPT_UPDATE,
        false,
        ReceivingConstants.RECEIPT,
        ReceivingConstants.RECEIPT);

    return rcContainerDetails;
  }

  private void updateContainer(
      ContainerRLog container, UpdateContainerRequest updateContainerRequest) {
    Optional.ofNullable(updateContainerRequest.getLocation())
        .ifPresent(
            location -> {
              Optional.ofNullable(location.getName()).ifPresent(container::setLocation);
              Optional.ofNullable(location.getOrgUnitId())
                  .map(String::valueOf)
                  .ifPresent(container::setOrgUnitId);
            });
    Optional.ofNullable(updateContainerRequest.getScannedBin())
        .ifPresent(container::setDestinationTrackingId);
    Optional.ofNullable(updateContainerRequest.getScannedCart())
        .ifPresent(container::setDestinationParentTrackingId);
    Optional.ofNullable(updateContainerRequest.getDisposition())
        .ifPresent(
            disposition -> {
              // set disposition type to final disposition for backward compatibility
              if (!Objects.nonNull(disposition.getDispositionType())
                  && Objects.nonNull(disposition.getFinalDisposition())) {
                disposition.setDispositionType(disposition.getFinalDisposition());
              }
              Optional.ofNullable(disposition.getDispositionType())
                  .ifPresent(
                      dispositionType -> {
                        container.setFinalDispositionType(dispositionType);
                        container.setDispositionType(dispositionType);
                        container.setDestinationParentContainerType(
                            getDestinationParentContainerType(dispositionType));
                        container.setDestinationContainerType(
                            getDestinationContainerType(dispositionType));

                        if (RcConstants.DISPOSE.equalsIgnoreCase(dispositionType))
                          container.setDestinationTrackingId(null);

                        if (StringUtils.isEmpty(updateContainerRequest.getContainerTag())
                            && (RcConstants.RTV.equalsIgnoreCase(dispositionType)
                                || RcConstants.RESTOCK.equalsIgnoreCase(dispositionType))) {
                          LOGGER.error(
                              "Container cannot be updated without container tag for [dispositionType={}].",
                              dispositionType);
                          throw new ReceivingBadDataException(
                              ExceptionCodes.INVALID_UPDATE_CONTAINER_REQUEST,
                              ExceptionDescriptionConstants
                                  .INVALID_UPDATE_CONTAINER_REQUEST_CONTAINER_TAG);
                        }
                      });

              Optional.ofNullable(disposition.getProposedDisposition())
                  .ifPresent(
                      proposedDispositionType -> {
                        container.setProposedDispositionType(proposedDispositionType);
                        container.setIsOverride(
                            !disposition
                                .getProposedDisposition()
                                .equalsIgnoreCase(disposition.getFinalDisposition()));
                      });

              Optional.ofNullable(disposition.getFinalDisposition())
                  .ifPresent(container::setFinalDispositionType);

              Optional.ofNullable(disposition.getItemCondition())
                  .ifPresent(container::setItemCondition);
            });

    Optional.ofNullable(updateContainerRequest.getContainerTag())
        .ifPresent(container::setDestinationContainerTag);
    Optional.ofNullable(updateContainerRequest.getAnswers())
        .ifPresent(
            answers -> {
              Optional.ofNullable(answers.getItemCategory()).ifPresent(container::setItemCategory);
              Optional.ofNullable(answers.getQuestions()).ifPresent(container::setQuestion);
            });
    Optional.ofNullable(updateContainerRequest.getAnswers())
        .ifPresent(
            answers -> {
              if (Objects.nonNull(answers.getQuestions())
                  && Objects.nonNull(answers.getQuestions().get(0).getQuestions())) {
                List<OptionsItem> options =
                    answers.getQuestions().get(0).getQuestions().get(0).getOptions();
                OptionsItem optionItem =
                    options.stream().filter(o -> o.getIsSelected()).findFirst().get();
                container.setChosenCategory(CategoryType.getCategoryType(optionItem.getOptionId()));
              }
            });
    String userId =
        String.valueOf(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    container.setLastChangedUser(userId);
    container.setMessageId(TenantContext.getCorrelationId());
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());
  }

  private void updateContainerCreationRequest(ReceiveContainerRequest containerRequest) {

    if (Objects.isNull(containerRequest.getSalesOrder())) {
      containerRequest.setSalesOrder(
          SalesOrder.builder()
              .soNumber(RcConstants.DEFAULT_SALES_ORDER)
              .lines(
                  Collections.singletonList(
                      SalesOrderLine.builder()
                          .lineNumber(RcConstants.DEFAULT_SALES_ORDER_LINE)
                          .channel(RcConstants.DEFAULT_CHANNEL)
                          .build()))
              .build());
    }

    if (Objects.isNull(containerRequest.getItemDetails())) {
      containerRequest.setItemDetails(
          ItemDetails.builder().number(RcConstants.DEFAULT_ITEM_NUMBER).build());
    }
  }

  private ContainerRLog createContainer(
      ReceiveContainerRequest containerRequest, String trackingId) {

    ContainerRLog container = new ContainerRLog();
    container.setTrackingId(trackingId);
    container.setDeliveryNumber(
        RcUtils.createDeliveryNumberFromSoNumber(containerRequest.getSalesOrder().getSoNumber()));
    container.setDestinationTrackingId(containerRequest.getScannedBin());
    container.setDestinationParentTrackingId(containerRequest.getScannedCart());
    container.setMessageId(TenantContext.getCorrelationId());
    container.setContainerType(ReceivingConstants.EACH);
    container.setContainerStatus(ReceivingConstants.AVAILABLE);
    container.setPackageBarCodeValue(containerRequest.getScannedLabel());
    container.setPackageBarCodeType(containerRequest.getScannedLabelType());
    if (Objects.nonNull(containerRequest.getLocation())) {
      container.setLocation(containerRequest.getLocation().getName());
      if (Objects.nonNull(containerRequest.getLocation().getOrgUnitId())) {
        container.setOrgUnitId(String.valueOf(containerRequest.getLocation().getOrgUnitId()));
      }
    }
    Disposition disposition = containerRequest.getDisposition();
    if (Objects.nonNull(disposition)
        && !StringUtils.isEmpty(disposition.getProposedDisposition())
        && !StringUtils.isEmpty(disposition.getFinalDisposition())) {
      // Set default value if no disposition found
      if (Objects.nonNull(disposition.getDispositionType())) {
        container.setDispositionType(disposition.getDispositionType());
      } else {
        container.setDispositionType(disposition.getFinalDisposition());
      }
      container.setItemCondition(disposition.getItemCondition());
      container.setProposedDispositionType(disposition.getProposedDisposition());
      container.setFinalDispositionType(disposition.getFinalDisposition());
      container.setIsOverride(
          !disposition
              .getProposedDisposition()
              .equalsIgnoreCase(disposition.getFinalDisposition()));
      container.setSellerCountryCode("US");
    } else {
      LOGGER.error("Container cannot be created without disposition type.");
      throw new ReceivingBadDataException(
          INVALID_CREATE_CONTAINER_REQUEST,
          ExceptionDescriptionConstants.INVALID_CREATE_CONTAINER_REQUEST_DISPOSITION_TYPE);
    }
    // If dispositionType is not present assign the final disposition type
    if (Objects.nonNull(disposition.getDispositionType())) {
      container.setDestinationParentContainerType(
          getDestinationParentContainerType(disposition.getDispositionType()));
    } else {
      container.setDestinationParentContainerType(
          getDestinationParentContainerType(disposition.getFinalDisposition()));
    }
    container.setDestinationContainerType(
        getDestinationContainerType(disposition.getFinalDisposition()));
    if (StringUtils.isEmpty(containerRequest.getContainerTag())
        && (RcConstants.RTV.equalsIgnoreCase(disposition.getFinalDisposition())
            || RcConstants.RESTOCK.equalsIgnoreCase(disposition.getFinalDisposition()))) {
      LOGGER.error(
          "Container cannot be created without container tag for [dispositionType={}].",
          disposition.getFinalDisposition());
      throw new ReceivingBadDataException(
          INVALID_CREATE_CONTAINER_REQUEST,
          ExceptionDescriptionConstants.INVALID_CREATE_CONTAINER_REQUEST_CONTAINER_TAG);
    }
    container.setDestinationContainerTag(containerRequest.getContainerTag());

    ItemDetails item = containerRequest.getItemDetails();

    if (Objects.nonNull(item.getWeight())) {
      container.setWeight(item.getWeight().getAmount());
      container.setWeightUOM(item.getWeight().getUom());
    }
    if (Objects.nonNull(item.getCube())) {
      container.setCube(item.getCube().getAmount());
      container.setCubeUOM(item.getCube().getUom());
    }
    container.setItemNumber(item.getNumber());

    String itemUPC =
        (StringUtils.isEmpty(item.getConsumableGTIN())
                && (RcConstants.RTV.equalsIgnoreCase(disposition.getFinalDisposition())
                    || RcConstants.RESTOCK.equalsIgnoreCase(disposition.getFinalDisposition())))
            ? containerRequest.getScannedItemLabel()
            : item.getConsumableGTIN();
    String caseUPC =
        (StringUtils.isEmpty(item.getOrderableGTIN())
                && (RcConstants.RTV.equalsIgnoreCase(disposition.getFinalDisposition())
                    || RcConstants.RESTOCK.equalsIgnoreCase(disposition.getFinalDisposition())))
            ? containerRequest.getScannedItemLabel()
            : item.getOrderableGTIN();
    container.setGtin(itemUPC);
    container.setItemUPC(itemUPC);
    container.setCaseUPC(caseUPC);
    /*
     *Since Container Item description and secondary description can only hold till 80 characters
     * So storing till 80 characters
     */
    if (!CollectionUtils.isEmpty(item.getDescription())) {
      String description = item.getDescription().get(0);
      if (!StringUtils.isEmpty(description) && description.length() > 79) {
        description = description.substring(0, 79);
      }
      container.setDescription(description);
      if (item.getDescription().size() > 1) {
        String secondaryDescription = item.getDescription().get(1);
        if (!StringUtils.isEmpty(secondaryDescription) && secondaryDescription.length() > 79) {
          secondaryDescription = secondaryDescription.substring(0, 79);
        }
        container.setSecondaryDescription(secondaryDescription);
      }
    }
    container.setLegacySellerId(item.getLegacySellerId());
    container.setServiceType(item.getServiceType());
    container.setItemId(item.getItemId());
    container.setIsFragile(item.getIsFragile());
    container.setIsConsumable(item.getIsConsumable());
    container.setIsHazmat(item.getIsHazmat());
    container.setIsHazardous(item.getIsHazardous());
    container.setRegulatedItemType(item.getRegulatedItemType());
    container.setRegulatedItemLabelCode(item.getRegulatedItemLabelCode());

    container.setIsGoodwill(item.getIsGoodwill());
    container.setGoodwillReason(item.getGoodwillReason());

    container.setCtrShippable(false);
    container.setCtrReusable(false);
    container.setHasChildContainers(false);
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());
    container.setInventoryStatus(ReceivingConstants.AVAILABLE);
    String userId =
        String.valueOf(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    container.setCreateUser(userId);
    container.setLastChangedUser(userId);
    container.setTotalPurchaseReferenceQty(1);
    container.setQuantity(1);
    container.setQuantityUOM(Uom.EACHES);
    container.setVnpkQty(1);
    container.setWhpkQty(1);
    container.setPurchaseCompanyId(RcConstants.RETURN_PURCHASE_COMPANY_ID);

    if (Objects.nonNull(containerRequest.getScannedSerialNumber())) {
      container.setScannedSerialNumber(containerRequest.getScannedSerialNumber());
    }
    if (!CollectionUtils.isEmpty(item.getSerialNumbers())) {
      container.setExpectedSerialNumbers(item.getSerialNumbers());
    }

    SalesOrder so = containerRequest.getSalesOrder();
    container.setPurchaseReferenceNumber(so.getSoNumber());
    container.setSalesOrderNumber(so.getSoNumber());
    container.setInboundChannelMethod(RcConstants.DEFAULT_CHANNEL);
    container.setOutboundChannelMethod(RcConstants.DEFAULT_CHANNEL);
    // APPRC-1521 Saving Tenant ID while creating container
    container.setTenantId(so.getTenantId());

    Optional<SalesOrderLine> salesOrderLine =
        so.getLines()
            .stream()
            .filter(soLine -> soLine.getLineNumber().equals(containerRequest.getLineNumber()))
            .findFirst();
    LOGGER.info(
        "Container request line number: {}, of SO: {}",
        containerRequest.getLineNumber(),
        containerRequest.getSalesOrder().getSoNumber());
    if (salesOrderLine.isPresent()) {
      LOGGER.info(
          "Matched line number:{} of SO: {} and container request line number: {}",
          salesOrderLine.get().getLineNumber(),
          containerRequest.getSalesOrder().getSoNumber(),
          containerRequest.getLineNumber());
      container.setPurchaseReferenceLineNumber(salesOrderLine.get().getLineNumber());
      container.setSalesOrderLineNumber(salesOrderLine.get().getLineNumber());
      container.setInboundChannelMethod(salesOrderLine.get().getChannel());
      container.setOutboundChannelMethod(salesOrderLine.get().getChannel());
    } else {
      //// Revisit when GAD error is fixed on missing item in Sales order
      LOGGER.info(
          "Line number: {} not found in container request for an SO: {}",
          containerRequest.getLineNumber(),
          containerRequest.getSalesOrder().getSoNumber());
      container.setSalesOrderLineNumber(containerRequest.getLineNumber());
      container.setPurchaseReferenceLineNumber(containerRequest.getLineNumber());
      if (Objects.isNull(containerRequest.getLineNumber())) {
        LOGGER.info(
            "Container request line number is null for an SO: {}",
            containerRequest.getLineNumber(),
            containerRequest.getSalesOrder().getSoNumber());
        container.setSalesOrderLineNumber(so.getLines().get(0).getLineNumber());
        container.setPurchaseReferenceLineNumber(so.getLines().get(0).getLineNumber());
      }
    }
    List<ContainerRLog> containerRLogList =
        getReceivedContainersBySoNumberAndSalesOrderLineNumber(
            containerRequest.getSalesOrder().getSoNumber(), containerRequest.getLineNumber());
    if (!CollectionUtils.isEmpty(so.getReturnOrders())) {
      AtomicBoolean roLineFound = new AtomicBoolean(false);
      so.getReturnOrders()
          .forEach(
              ro -> {
                if (!roLineFound.get()) {
                  Optional<ReturnOrderLine> roLine =
                      ro.getLines()
                          .stream()
                          .filter(
                              roline ->
                                  roline.getSoLineNumber().equals(containerRequest.getLineNumber()))
                          .findFirst();
                  if (roLine.isPresent()) {
                    roLineFound.set(true);
                    container.setReturnOrderLineNumber(roLine.get().getLineNumber());
                    if (Objects.nonNull(roLine.get().getCarrierInformation())) {
                      container.setTrackingNumber(
                          roLine.get().getCarrierInformation().getTrackingNumber());
                    }

                    if (containerRLogList.size() >= roLine.get().getReturned().getQuantity()) {
                      container.setReturnOrderNumber(null);
                    } else {
                      container.setReturnOrderNumber(ro.getRoNumber());
                    }
                  }
                }
              });
    }
    Optional<SalesOrderLine> soLine =
        so.getLines()
            .stream()
            .filter(soLines -> soLines.getLineNumber().equals(containerRequest.getLineNumber()))
            .findFirst();
    if (soLine.isPresent()) {
      container.setSalesOrderLineId(soLine.get().getLineId());
    }

    if (container.getReturnOrderLineNumber() == null) {
      LOGGER.warn(
          "Container return order line number is null for an SO: {}, RO: {}",
          container.getSalesOrderNumber(),
          container.getReturnOrderNumber());
    }
    if (container.getSalesOrderLineNumber() == null) {
      LOGGER.warn(
          "Container sales order line number is null for an SO: {}, RO: {}",
          container.getSalesOrderNumber(),
          container.getReturnOrderNumber());
    }
    if (container.getReturnOrderNumber() == null) {
      container.setIsMissingReturnInitiated(true);
      container.setIsMissingReturnReceived(false);
    } else {
      container.setIsMissingReturnInitiated(false);
      container.setIsMissingReturnReceived(false);
    }
    container.setPackageItemIdentificationCode(containerRequest.getPackageItemIdentificationCode());
    container.setPackageItemIdentificationMessage(
        containerRequest.getPackageItemIdentificationMessage());
    container.setItemBarCodeValue(containerRequest.getScannedItemLabel());
    Answers answers = containerRequest.getAnswers();
    if (Objects.nonNull(answers)) {
      container.setItemCategory(answers.getItemCategory());
      container.setQuestion(answers.getQuestions());
    }
    if (Objects.nonNull(containerRequest.getItemCategoryDetails())) {
      container.setPrePopulatedCategory(
          containerRequest.getItemCategoryDetails().getPrePopulatedCategory());
      container.setChosenCategory(containerRequest.getItemCategoryDetails().getChosenCategory());
    }

    return container;
  }

  private String getDestinationParentContainerType(String dispositionType) {
    Type destinationParentContainerTypeToken =
        new TypeToken<Map<String, Map<String, String>>>() {
          private static final long serialVersionUID = 1L;
        }.getType();
    if (!StringUtils.isEmpty(rcManagedConfig.getDestinationParentContainerType())) {
      Map<String, Map<String, String>> parentContainerTypeMapByFacility =
          gson.fromJson(
              rcManagedConfig.getDestinationParentContainerType(),
              destinationParentContainerTypeToken);
      if (!CollectionUtils.isEmpty(parentContainerTypeMapByFacility)) {
        if (parentContainerTypeMapByFacility.containsKey(
            String.valueOf(TenantContext.getFacilityNum()))) {
          Map<String, String> parentContainerTypeMap =
              parentContainerTypeMapByFacility.get(String.valueOf(TenantContext.getFacilityNum()));
          if (!CollectionUtils.isEmpty(parentContainerTypeMap)
              && parentContainerTypeMap.containsKey(dispositionType)) {
            return parentContainerTypeMap.get(dispositionType);
          }
        }
      }
    }
    LOGGER.error(
        "Missing destination parent container type in CCM config for dispositionType = [{}]",
        dispositionType);
    throw new ReceivingBadDataException(
        INVALID_CREATE_CONTAINER_REQUEST,
        String.format(
            ExceptionDescriptionConstants.MISSING_DESTINATION_PARENT_CONTAINER_TYPE_CONFIG,
            dispositionType));
  }

  private String getDestinationContainerType(String dispositionType) {
    Type destinationContainerTypeToken =
        new TypeToken<Map<String, Map<String, String>>>() {
          private static final long serialVersionUID = 1L;
        }.getType();
    if (!StringUtils.isEmpty(rcManagedConfig.getDestinationContainerType())) {
      Map<String, Map<String, String>> containerTypeMapByFacility =
          gson.fromJson(
              rcManagedConfig.getDestinationContainerType(), destinationContainerTypeToken);
      if (!CollectionUtils.isEmpty(containerTypeMapByFacility)) {
        if (containerTypeMapByFacility.containsKey(
            String.valueOf(TenantContext.getFacilityNum()))) {
          Map<String, String> containerTypeMap =
              containerTypeMapByFacility.get(String.valueOf(TenantContext.getFacilityNum()));
          if (!CollectionUtils.isEmpty(containerTypeMap)
              && containerTypeMap.containsKey(dispositionType)) {
            return containerTypeMap.get(dispositionType);
          }
        }
      }
    }
    return null;
  }

  /**
   * Publish container
   *
   * @param containerDetails
   * @param httpHeaders
   * @param actionType
   * @param ignoreSct
   */
  public void publishContainer(
      RcContainerDetails containerDetails,
      HttpHeaders httpHeaders,
      ActionType actionType,
      Boolean ignoreSct,
      String ignoreRap,
      String ignoreWfs) {
    ContainerRLog container = containerDetails.getContainerRLog();
    List<ItemTracker> itemTrackers = containerDetails.getItemTrackers();
    RcContainerAdditionalAttributes rcContainerAdditionalAttributes =
        new RcContainerAdditionalAttributes();
    rcContainerAdditionalAttributes.setPackageItemIdentificationCode(
        container.getPackageItemIdentificationCode());
    rcContainerAdditionalAttributes.setPackageItemIdentificationMessage(
        container.getPackageItemIdentificationMessage());
    rcContainerAdditionalAttributes.setServiceType(container.getServiceType());
    rcContainerAdditionalAttributes.setReturnOrderNumber(container.getReturnOrderNumber());
    rcContainerAdditionalAttributes.setReturnOrderLineNumber(container.getReturnOrderLineNumber());
    rcContainerAdditionalAttributes.setSalesOrderLineId(container.getSalesOrderLineId());
    rcContainerAdditionalAttributes.setTrackingNumber(container.getTrackingNumber());
    rcContainerAdditionalAttributes.setItemCategory(container.getItemCategory());
    rcContainerAdditionalAttributes.setLegacySellerId(container.getLegacySellerId());
    rcContainerAdditionalAttributes.setProposedDispositionType(
        container.getProposedDispositionType());
    if (Objects.nonNull(container.getDispositionType())) {
      rcContainerAdditionalAttributes.setFinalDispositionType(container.getDispositionType());
    } else {
      rcContainerAdditionalAttributes.setFinalDispositionType(container.getFinalDispositionType());
    }
    rcContainerAdditionalAttributes.setIsOverride(container.getIsOverride());
    rcContainerAdditionalAttributes.setItemBarCodeValue(container.getItemBarCodeValue());
    rcContainerAdditionalAttributes.setItemId(container.getItemId());
    rcContainerAdditionalAttributes.setItemCondition(container.getItemCondition());
    rcContainerAdditionalAttributes.setIsConsumable(container.getIsConsumable());
    rcContainerAdditionalAttributes.setIsFragile(container.getIsFragile());
    rcContainerAdditionalAttributes.setIsHazmat(container.getIsHazmat());
    rcContainerAdditionalAttributes.setIsHazardous(container.getIsHazardous());
    rcContainerAdditionalAttributes.setRegulatedItemType(container.getRegulatedItemType());
    rcContainerAdditionalAttributes.setRegulatedItemLabelCode(
        container.getRegulatedItemLabelCode());
    rcContainerAdditionalAttributes.setItemType(container.getDestinationContainerTag());
    rcContainerAdditionalAttributes.setSellerCountryCode(container.getSellerCountryCode());
    rcContainerAdditionalAttributes.setUserAnswers(container.getQuestion());
    rcContainerAdditionalAttributes.setGoodwillReason(container.getGoodwillReason());
    rcContainerAdditionalAttributes.setIsGoodwill(container.getIsGoodwill());
    rcContainerAdditionalAttributes.setScannedSerialNumber(container.getScannedSerialNumber());
    rcContainerAdditionalAttributes.setExpectedSerialNumbers(container.getExpectedSerialNumbers());
    rcContainerAdditionalAttributes.setPotentialFraudReason(
        Optional.of(containerDetails)
            .map(RcContainerDetails::getReceivingWorkflow)
            .map(ReceivingWorkflow::getCreateReason)
            .orElse(null));
    enrichItemFeedback(rcContainerAdditionalAttributes, itemTrackers);

    RcContainerItem rcContainerItem = new RcContainerItem();
    rcContainerItem.setPurchaseReferenceNumber(container.getPurchaseReferenceNumber());
    rcContainerItem.setPurchaseReferenceLineNumber(container.getPurchaseReferenceLineNumber());
    rcContainerItem.setInboundChannelMethod(container.getInboundChannelMethod());
    rcContainerItem.setOutboundChannelMethod(container.getOutboundChannelMethod());
    rcContainerItem.setQuantity(container.getQuantity());
    rcContainerItem.setQuantityUOM(container.getQuantityUOM());
    rcContainerItem.setVnpkQty(container.getVnpkQty());
    rcContainerItem.setWhpkQty(container.getWhpkQty());
    rcContainerItem.setVnpkWgtQty(container.getWeight());
    rcContainerItem.setVnpkWgtUom(container.getWeightUOM());
    rcContainerItem.setVnpkcbqty(container.getCube());
    rcContainerItem.setVnpkcbuomcd(container.getCubeUOM());
    rcContainerItem.setSalesOrderNumber(container.getSalesOrderNumber());
    rcContainerItem.setSalesOrderLineNumber(container.getSalesOrderLineNumber());
    rcContainerItem.setDescription(container.getDescription());
    rcContainerItem.setSecondaryDescription(container.getSecondaryDescription());
    rcContainerItem.setPurchaseCompanyId(container.getPurchaseCompanyId());
    rcContainerItem.setTotalPurchaseReferenceQty(container.getTotalPurchaseReferenceQty());
    rcContainerItem.setItemNumber(container.getItemNumber());
    rcContainerItem.setGtin(container.getGtin());
    rcContainerItem.setItemUPC(container.getItemUPC());
    rcContainerItem.setCaseUPC(container.getCaseUPC());
    rcContainerItem.setPrePopulatedCategory(container.getPrePopulatedCategory());
    rcContainerItem.setChosenCategory(container.getChosenCategory());
    rcContainerItem.setAdditionalAttributes(rcContainerAdditionalAttributes);

    RcContainer rcContainer = new RcContainer();
    rcContainer.setTrackingId(container.getTrackingId());
    rcContainer.setMessageId(container.getMessageId());
    rcContainer.setLocation(container.getLocation());
    rcContainer.setDeliveryNumber(container.getDeliveryNumber());
    rcContainer.setContainerType(container.getContainerType());
    rcContainer.setContainerStatus(container.getContainerStatus());
    rcContainer.setInventoryStatus(container.getInventoryStatus());
    rcContainer.setCtrShippable(container.getCtrShippable());
    rcContainer.setCtrReusable(container.getCtrReusable());
    rcContainer.setOrgUnitId(container.getOrgUnitId());
    rcContainer.setHasChildContainers(container.getHasChildContainers());
    rcContainer.setCreateTs(container.getCreateTs());
    rcContainer.setLastChangedTs(container.getLastChangedTs());
    rcContainer.setCompleteTs(container.getCompleteTs());
    rcContainer.setPublishTs(container.getPublishTs());
    rcContainer.setCreateUser(container.getCreateUser());
    rcContainer.setLastChangedUser(container.getLastChangedUser());
    rcContainer.setDestinationParentTrackingId(container.getDestinationParentTrackingId());
    rcContainer.setDestinationParentContainerType(container.getDestinationParentContainerType());
    rcContainer.setDestinationContainerType(container.getDestinationContainerType());
    rcContainer.setDestinationTrackingId(container.getDestinationTrackingId());
    rcContainer.setDestinationContainerTag(container.getDestinationContainerTag());
    rcContainer.setPackageBarCodeValue(container.getPackageBarCodeValue());
    rcContainer.setPackageBarCodeType(container.getPackageBarCodeType());
    rcContainer.setWeight(container.getWeight());
    rcContainer.setWeightUOM(container.getWeightUOM());
    rcContainer.setCube(container.getCube());
    rcContainer.setCubeUOM(container.getCubeUOM());
    rcContainer.setDispositionType(container.getFinalDispositionType());
    rcContainer.setContents(Collections.singletonList(rcContainerItem));

    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardableHeadersWithRequestOriginator(httpHeaders);
    messageHeaders.put(ReceivingConstants.JMS_EVENT_TYPE, RcConstants.RETURNS_RECEIPT_EVENT_TYPE);
    messageHeaders.put(ReceivingConstants.ACTION_TYPE, actionType.name());

    rcContainerAdditionalAttributes.setReturnOrderNumber(container.getReturnOrderNumber());
    rcContainerAdditionalAttributes.setReturnOrderLineNumber(container.getReturnOrderLineNumber());

    messageHeaders.put(ReceivingConstants.IGNORE_SCT, ignoreSct);
    messageHeaders.put(ReceivingConstants.SCAN_MODE, ignoreRap);
    messageHeaders.put(ReceivingConstants.FLOW, ignoreWfs);

    if (rcManagedConfig.getContainerRCIDEnabled()) {
      messageHeaders.put(
          ReceivingConstants.INVENTORY_CONTAINER_TYPE,
          ReceivingConstants.INVENTORY_CONTAINER_TYPE_RCID);
    }

    // converting container Object into String
    String jsonObject = gsonWithDateAdapter.toJson(rcContainer);

    // publishing container information to inventory
    ReceivingJMSEvent jmsEvent = new ReceivingJMSEvent(messageHeaders, jsonObject);

    jmsPublisher.publish(maasTopics.getPubReceiptsTopic(), jmsEvent, Boolean.TRUE);
  }

  /**
   * This method is responsible for enriching feedback in receipts for various reason codes
   *
   * @param additionalAttributes
   * @param itemTrackers
   */
  private void enrichItemFeedback(
      RcContainerAdditionalAttributes additionalAttributes, List<ItemTracker> itemTrackers) {
    if (!CollectionUtils.isEmpty(itemTrackers)) {
      for (ItemTracker itemTracker : itemTrackers) {
        switch (itemTracker.getItemTrackerCode()) {
          case SERIAL_NUMBER_MATCHED:
            additionalAttributes.setFeedbackType(ItemFeedback.CORRECT_ITEM.getCode());
            additionalAttributes.setFeedbackReason(ItemTrackerCode.SERIAL_NUMBER_MATCHED.getCode());
            break;
          case SERIAL_NUMBER_MISMATCH:
            additionalAttributes.setFeedbackType(ItemFeedback.INCORRECT_ITEM.getCode());
            additionalAttributes.setFeedbackReason(
                ItemTrackerCode.SERIAL_NUMBER_MISMATCH.getCode());
            break;
          case SERIAL_NUMBER_MISSING:
            additionalAttributes.setFeedbackType(ItemFeedback.INCORRECT_ITEM.getCode());
            additionalAttributes.setFeedbackReason(ItemTrackerCode.SERIAL_NUMBER_MISSING.getCode());
            break;
          default:
            break;
        }
      }
    }
  }

  @Transactional
  @InjectTenantFilter
  public void deleteContainersByPackageBarcode(String packageBarcodeValue) {
    List<ContainerRLog> containerRLogList =
        containerRLogRepository.findByPackageBarCodeValue(packageBarcodeValue);
    if (CollectionUtils.isEmpty(containerRLogList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              packageBarcodeValue);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }
    containerRLogRepository.deleteByPackageBarCodeValue(packageBarcodeValue);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<ContainerRLog> containerRLogList =
        containerRLogRepository.findByIdGreaterThanEqual(
            purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    containerRLogList =
        containerRLogList
            .stream()
            .filter(containerRLog -> containerRLog.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(ContainerRLog::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(containerRLogList)) {
      LOGGER.info("Purge CONTAINER_RLOG: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = containerRLogList.get(containerRLogList.size() - 1).getId();

    LOGGER.info(
        "Purge CONTAINER_RLOG: {} records : ID {} to {} : START",
        containerRLogList.size(),
        containerRLogList.get(0).getId(),
        lastDeletedId);
    containerRLogRepository.deleteAll(containerRLogList);
    LOGGER.info("Purge CONTAINER_RLOG: END");
    return lastDeletedId;
  }

  @Transactional
  @InjectTenantFilter
  public ContainerRLog getLatestReceivedContainerByGtin(String gtin, String dispositionType) {
    Optional<ContainerRLog> optionalContainerRLog;
    String errorDescription;
    if (StringUtils.isEmpty(dispositionType)) {
      optionalContainerRLog = containerRLogRepository.findFirstByGtinOrderByCreateTsDesc(gtin);
      errorDescription =
          String.format(ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_GTIN_ERROR_MSG, gtin);
    } else {
      optionalContainerRLog =
          containerRLogRepository.findFirstByGtinAndFinalDispositionTypeOrderByCreateTsDesc(
              gtin, dispositionType);
      errorDescription =
          String.format(
              ExceptionDescriptionConstants
                  .CONTAINER_NOT_FOUND_BY_GTIN_FOR_DISPOSITION_TYPE_ERROR_MSG,
              gtin,
              dispositionType);
    }
    return optionalContainerRLog.orElseThrow(
        () ->
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription));
  }

  @Transactional
  @InjectTenantFilter
  public ContainerRLog getReceivedContainerByTrackingId(String trackingId) {
    Optional<ContainerRLog> optionalContainerRLog =
        containerRLogRepository.findByTrackingId(trackingId);
    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
            trackingId);

    return optionalContainerRLog.orElseThrow(
        () ->
            new ReceivingDataNotFoundException(
                ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription));
  }

  @Transactional
  @InjectTenantFilter
  public List<ContainerRLog> getReceivedContainersByPackageBarCode(String packageBarcodeValue) {
    List<ContainerRLog> containerRLogList =
        containerRLogRepository.findByPackageBarCodeValue(packageBarcodeValue);
    if (CollectionUtils.isEmpty(containerRLogList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              packageBarcodeValue);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }
    return containerRLogList;
  }

  @Transactional
  @InjectTenantFilter
  public List<ContainerRLog> getReceivedContainersBySoNumber(String soNumber) {
    List<ContainerRLog> containerRLogList =
        containerRLogRepository.findBySalesOrderNumber(soNumber);
    if (CollectionUtils.isEmpty(containerRLogList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_SO_NUMBER_ERROR_MSG, soNumber);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }
    Map<String, String> trackingIdToWorkFlowIdMap = new HashMap<>();
    List<String> trackingId = new ArrayList<>();
    for (ContainerRLog containerRLog : containerRLogList) {
      trackingId.add(containerRLog.getTrackingId());
    }
    List<ReceivingWorkflowItem> receivingWorkFlowItems =
        receivingWorkflowItemRepository.findByItemTrackingIdIn(trackingId);
    if (!CollectionUtils.isEmpty(receivingWorkFlowItems)) {
      for (ReceivingWorkflowItem receivingWorkFlowItem : receivingWorkFlowItems) {
        trackingIdToWorkFlowIdMap.put(
            receivingWorkFlowItem.getItemTrackingId(),
            receivingWorkFlowItem.getReceivingWorkflow().getWorkflowId());
      }
      containerRLogList.forEach(
          indcontainerRLog -> {
            if (trackingIdToWorkFlowIdMap.containsKey(indcontainerRLog.getTrackingId())) {
              indcontainerRLog.setWorkFlowId(
                  trackingIdToWorkFlowIdMap.get(indcontainerRLog.getTrackingId()));
            }
          });
    }

    return containerRLogList;
  }

  @Transactional
  @InjectTenantFilter
  public List<ContainerRLog> getReceivedContainersBySoNumberAndSalesOrderLineNumber(
      String soNumber, Integer salesOrderLineNumber) {
    List<ContainerRLog> containerRLogList =
        containerRLogRepository.findBySalesOrderNumberAndSalesOrderLineNumber(
            soNumber, salesOrderLineNumber);

    return containerRLogList;
  }

  private ReceiveContainerRequest transformUpdateContainerToReceiveContainer(
      UpdateContainerRequest updateContainerRequest) {

    return ReceiveContainerRequest.builder()
        .workflowId(updateContainerRequest.getWorkflowId())
        .workflowCreateReason(updateContainerRequest.getWorkflowCreateReason())
        .scannedLabel(updateContainerRequest.getScannedLabel())
        .scannedItemLabel(updateContainerRequest.getScannedItemLabel())
        .build();
  }

  @Transactional
  @InjectTenantFilter
  public void updateReturnOrderData(
      UpdateReturnOrderDataRequest updateReturnOrderDataRequest, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Beginning to update container with tracking ID - {}, request: {}",
        updateReturnOrderDataRequest.getRcTrackingId(),
        updateReturnOrderDataRequest);

    Optional<ContainerRLog> optionalContainerRLog =
        containerRLogRepository.findByTrackingId(updateReturnOrderDataRequest.getRcTrackingId());

    String errorDescription =
        String.format(
            ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
            updateReturnOrderDataRequest.getRcTrackingId());
    ContainerRLog container =
        optionalContainerRLog.orElseThrow(
            () ->
                new ReceivingDataNotFoundException(
                    ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription));

    if (optionalContainerRLog.isPresent()
        && container.getIsMissingReturnInitiated()
        && !container.getIsMissingReturnReceived()) {
      container.setReturnOrderNumber(updateReturnOrderDataRequest.getRoNumber());
      container.setReturnOrderLineNumber(updateReturnOrderDataRequest.getRoLineNumber());
      container.setSalesOrderLineNumber(updateReturnOrderDataRequest.getSoLineNumber());

      containerRLogRepository.updateReturnOrderData(
          updateReturnOrderDataRequest.getRoNumber(),
          updateReturnOrderDataRequest.getRoLineNumber(),
          updateReturnOrderDataRequest.getSoLineNumber(),
          updateReturnOrderDataRequest.getRcTrackingId());
      LOGGER.info(
          "Successfully updated container with Return Order Data: {}",
          gsonWithDateAdapter.toJson(container));

      RcContainerDetails rcContainerDetails =
          RcContainerDetails.builder().containerRLog(container).build();
      // TODO -> call Publish method and updating flags
      publishContainer(
          rcContainerDetails,
          httpHeaders,
          ActionType.RECEIPT_UPDATE,
          false,
          ReceivingConstants.RESCAN,
          ReceivingConstants.RECEIPT);

    } else {
      LOGGER.info("Return Order Data exists in DB: {}", gsonWithDateAdapter.toJson(container));
    }
  }
}
