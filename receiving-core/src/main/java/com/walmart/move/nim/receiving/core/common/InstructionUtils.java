package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.CANCELLED_PO_INSTRUCTION;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CANCELLED_PO_INSTRUCTION_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CANCELLED_PO_INSTRUCTION_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CANCELLED_PO_INSTRUCTION_MORE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CLOSE_DATE_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.EXPIRED_PRODUCT_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.EXPIRY_THRESHOLD_DATE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXPIRY_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXPIRY_DATE_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXP_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXP_DATE_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXP_DATE_FORMAT;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.PAST_DATE_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.STORE_MIN_LIFE_NULL_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.STORE_MIN_LIFE_NULL_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_MGR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.WAREHOUSE_MIN_LIFE_NULL_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.WAREHOUSE_MIN_LIFE_NULL_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.EXPIRED_PRODUCT_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_PRODUCT_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ProblemStatus.ANSWERED_AND_READY_TO_RECEIVE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.HAZMAT_ITEM_GROUND_TRANSPORTATION;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.UTC_TIME_ZONE;
import static java.lang.String.valueOf;
import static java.util.Objects.isNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.OpenQtyCalculator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import io.strati.metrics.annotation.ExceptionCounted;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Provides utility methods to manipulate instruction model data
 *
 * @author g0k0072
 */
@Component
public class InstructionUtils {

  private static final Logger LOG = LoggerFactory.getLogger(InstructionUtils.class);
  protected GdmError gdmError;

  @Autowired private LithiumIonLimitedQtyRule lithiumIonLimitedQtyRule;
  @Autowired private LimitedQtyRule limitedQtyRule;
  @Autowired private LithiumIonRule lithiumIonRule;
  @Autowired private ItemConfigApiClient itemConfigApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private InstructionRepository instructionRepository;

  private static Gson gson = new Gson();

  public static final String USER_ROLE = "userRole";

  public static final String VTR = "VTR";

  // private InstructionUtils() {}

  /**
   * map HttpHeaders to FdeCreateContainerRequest
   *
   * @param httpHeaders
   * @param fdeCreateContainerRequest
   * @return fdeCreateContainerRequest
   */
  public static FdeCreateContainerRequest mapHttpHeadersToFdeCreateContainerRequest(
      HttpHeaders httpHeaders, FdeCreateContainerRequest fdeCreateContainerRequest) {
    fdeCreateContainerRequest.setCorrelationId(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    fdeCreateContainerRequest.setUserId(
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    return fdeCreateContainerRequest;
  }

  public static FdeCreateContainerRequest getFdeCreateContainerRequest(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders) {
    FdeCreateContainerRequest fdeCreateContainerRequest;
    fdeCreateContainerRequest =
        InstructionUtils.mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, InstructionUtils.createFdeCreateContainerRequest(instructionRequest));

    ContainerModel containerModel = InstructionUtils.createContainerModel(deliveryDocument);
    fdeCreateContainerRequest.setContainer(containerModel);
    return fdeCreateContainerRequest;
  }

  /**
   * Populate FdeCreateContainerRequest
   *
   * @param instructionRequest instruction request fro UI
   * @return FdeCreateContainerRequest
   */
  public static FdeCreateContainerRequest createFdeCreateContainerRequest(
      InstructionRequest instructionRequest) {
    FdeCreateContainerRequest fdeCreateContainerRequest = new FdeCreateContainerRequest();
    Facility facility = new Facility();
    if (TenantContext.getFacilityNum() != null) {
      facility.setBuNumber(TenantContext.getFacilityNum().toString());
      if (TenantContext.getFacilityCountryCode() != null)
        facility.setCountryCode(TenantContext.getFacilityCountryCode());
    }
    fdeCreateContainerRequest.setFacility(facility);
    fdeCreateContainerRequest.setMessageId(instructionRequest.getMessageId());
    fdeCreateContainerRequest.setDeliveryNumber(instructionRequest.getDeliveryNumber());
    fdeCreateContainerRequest.setDoorNumber(instructionRequest.getDoorNumber());
    return fdeCreateContainerRequest;
  }

  /**
   * Populate FdeCreateContainerRequest
   *
   * @param instructionRequest instruction request from UI
   * @return FdeCreateContainerRequest
   */
  public static FdeCreateContainerRequest createFdeCreateContainerRequestForWFS(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders) {
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, InstructionUtils.createFdeCreateContainerRequest(instructionRequest));
    ContainerModel containerModel = InstructionUtils.createContainerModelForWfs(deliveryDocument);
    List<Content> contents = containerModel.getContents();

    for (Content content : contents) {
      content.setSellerType(deliveryDocument.getSellerType());
      content.setSellerId(deliveryDocument.getSellerId());
      content.setReceiveQty(instructionRequest.getEnteredQty());
      content.setReceivingUnit(ReceivingConstants.EACH);
      content.setRecommendedFulfillmentType(
          instructionRequest.isMultiSKUItem() ? ReceivingConstants.PUT_FULFILLMENT_TYPE : null);
      content.setAdditionalInfo(
          deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo());
      setAdditionalInfo(deliveryDocument, content);
    }
    containerModel.setContents(contents);
    fdeCreateContainerRequest.setContainer(containerModel);
    return fdeCreateContainerRequest;
  }

  public static void setAdditionalInfo(DeliveryDocument deliveryDocument, Content content) {
    if (Objects.nonNull(deliveryDocument.getAdditionalInfo())) {
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getPackId()))
        content.setSsccPackId(deliveryDocument.getAdditionalInfo().getPackId());
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getIsAuditRequired()))
        content.setIsAuditRequired(deliveryDocument.getAdditionalInfo().getIsAuditRequired());
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getReceivingScanTimeStamp()))
        content.setReceivingScanTimeStamp(
            deliveryDocument.getAdditionalInfo().getReceivingScanTimeStamp());
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getShelfLPN()))
        content.setShelfLPN(deliveryDocument.getAdditionalInfo().getShelfLPN());
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo().getReReceivingShipmentNumber()))
        content.setReReceivingShipmentNumber(
            deliveryDocument.getAdditionalInfo().getReReceivingShipmentNumber());
    }
  }

  public static FdeCreateContainerRequest prepareFdeCreateContainerRequestForOnConveyor(
      HttpHeaders httpHeaders, InstructionRequest instructionRequest, String userId) {

    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, InstructionUtils.createFdeCreateContainerRequest(instructionRequest));
    ContainerModel containerModel =
        InstructionUtils.createContainerModel(instructionRequest.getDeliveryDocuments().get(0));
    containerModel.getContents().get(0).setOnConveyor(Boolean.TRUE);
    /*TODO: Planner not expecting as per current implementation
    containerModel
        .getContents()
        .get(0)
        .setIsManualReceivingEnabled(instructionRequest.getIsManualReceivingEnabled());*/
    fdeCreateContainerRequest.setContainer(containerModel);
    String trackingId =
        instructionRequest.isManualReceivingEnabled()
                || ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
                    instructionRequest.getFeatureType())
            ? null
            : instructionRequest.getMessageId();
    fdeCreateContainerRequest.setTrackingId(trackingId);
    fdeCreateContainerRequest.setUserId(userId);
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    String channelType =
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods());
    fdeCreateContainerRequest.getContainer().getContents().get(0).setPurchaseRefType(channelType);
    return fdeCreateContainerRequest;
  }

  /**
   * Static factory method to create ContainerModel out of deliveryDocument
   *
   * @param deliveryDocument
   * @return containerModel
   */
  public static ContainerModel createContainerModel(DeliveryDocument deliveryDocument) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    ContainerModel containerModel = new ContainerModel();
    containerModel.setWeight(deliveryDocumentLine.getWeight());
    containerModel.setWeightUom(deliveryDocumentLine.getWeightUom());
    containerModel.setCube(deliveryDocumentLine.getCube());
    containerModel.setCubeUom(deliveryDocumentLine.getCubeUom());
    containerModel.setContents(Collections.singletonList(createContent(deliveryDocument)));
    return containerModel;
  }

  /**
   * Static factory method to create ContainerModel out of deliveryDocument for wfs (taking
   * weightUom, cubeUom from deliveryDocumentLine.additionalInfo)
   *
   * @param deliveryDocument
   * @return containerModel
   */
  public static ContainerModel createContainerModelForWfs(DeliveryDocument deliveryDocument) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    ContainerModel containerModel = new ContainerModel();
    containerModel.setWeight(
        Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getWeightQty()));
    containerModel.setWeightUom(deliveryDocumentLine.getAdditionalInfo().getWeightQtyUom());
    containerModel.setCube(Float.parseFloat(deliveryDocumentLine.getAdditionalInfo().getCubeQty()));
    containerModel.setCubeUom(deliveryDocumentLine.getAdditionalInfo().getCubeUomCode());
    containerModel.setContents(Collections.singletonList(createContent(deliveryDocument)));

    return containerModel;
  }

  public static Content createContent(DeliveryDocument deliveryDocument) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Content content = new Content();
    content.setGtin(deliveryDocumentLine.getGtin());
    content.setItemNbr(deliveryDocumentLine.getItemNbr());
    content.setBaseDivisionCode(deliveryDocument.getBaseDivisionCode());
    content.setFinancialReportingGroup(deliveryDocument.getFinancialReportingGroup());
    content.setPurchaseCompanyId(deliveryDocument.getPurchaseCompanyId());
    content.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    content.setPurchaseReferenceLineNumber(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    content.setPoDcNumber(deliveryDocument.getPoDCNumber());
    content.setImportInd(deliveryDocument.getImportInd());
    content.setPurchaseRefType(deliveryDocumentLine.getPurchaseRefType());
    content.setQtyUom(deliveryDocumentLine.getQtyUOM());
    content.setVendorPack(deliveryDocumentLine.getVendorPack());
    content.setWarehousePack(deliveryDocumentLine.getWarehousePack());
    content.setOpenQty(deliveryDocumentLine.getOpenQty());
    content.setTotalOrderQty(deliveryDocumentLine.getTotalOrderQty());
    content.setPalletTie(deliveryDocumentLine.getPalletTie());
    content.setPalletHigh(deliveryDocumentLine.getPalletHigh());
    int maxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    content.setMaxReceiveQty(maxReceiveQty);
    content.setWarehousePackSell(deliveryDocumentLine.getWarehousePackSell());
    content.setVendorPackCost(deliveryDocumentLine.getVendorPackCost());
    content.setCurrency(deliveryDocumentLine.getCurrency());
    content.setDeptNumber(
        StringUtils.isEmpty(deliveryDocument.getDeptNumber())
            ? null
            : Integer.parseInt(deliveryDocument.getDeptNumber()));
    content.setColor(deliveryDocumentLine.getColor());
    content.setSize(deliveryDocumentLine.getSize());
    content.setDescription(deliveryDocumentLine.getDescription());
    content.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
    content.setIsConveyable(deliveryDocumentLine.getIsConveyable());
    content.setIsHazmat(deliveryDocumentLine.getIsHazmat());
    content.setPurchaseReferenceLegacyType(deliveryDocument.getPurchaseReferenceLegacyType());
    content.setVendorNumber(deliveryDocument.getVendorNumber());
    content.setEvent(deliveryDocumentLine.getEvent());
    content.setProfiledWarehouseArea(deliveryDocumentLine.getProfiledWarehouseArea());
    content.setCaseUPC(deliveryDocumentLine.getCaseUpc());
    content.setVendorUPC(deliveryDocumentLine.getVendorUPC());

    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    content.setWarehouseAreaCode(itemData == null ? null : itemData.getWarehouseAreaCode());
    content.setWarehouseAreaCodeValue(
        WarehouseAreaCodes.getwareHouseCodeMapping(content.getWarehouseAreaCode()));
    return content;
  }

  /**
   * This method is used to create fdeContainerRequest for PoCon
   *
   * @param instructionRequest
   * @param openQty
   * @param httpHeaders
   * @return
   */
  public static FdeCreateContainerRequest populateCreateContainerRequestForPoCon(
      InstructionRequest instructionRequest, Integer openQty, HttpHeaders httpHeaders) {

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Content content = new Content();
    content.setItemNbr(ReceivingConstants.DUMMY_ITEM_NUMBER);
    content.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    content.setPurchaseReferenceLegacyType(deliveryDocument.getPurchaseReferenceLegacyType());
    content.setPoDcNumber(deliveryDocument.getPoDCNumber());
    content.setOpenQty(openQty);
    content.setMaxReceiveQty(openQty);
    content.setTotalOrderQty(openQty);
    content.setQtyUom(ReceivingConstants.Uom.VNPK);
    content.setPurchaseRefType(deliveryDocumentLine.getPurchaseRefType());
    content.setBaseDivisionCode(deliveryDocument.getBaseDivisionCode());
    content.setFinancialReportingGroup(deliveryDocument.getFinancialReportingGroup());
    if (!isNull(deliveryDocument.getPoDcCountry())) {
      content.setPoDcCountry(deliveryDocument.getPoDcCountry());
    }

    List<Content> contents = new ArrayList<>();
    contents.add(content);
    ContainerModel containerModel = new ContainerModel();
    containerModel.setContents(contents);

    FdeCreateContainerRequest fdeCreateContainerRequest =
        mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, createFdeCreateContainerRequest(instructionRequest));
    fdeCreateContainerRequest.setContainer(containerModel);

    return fdeCreateContainerRequest;
  }

  /**
   * Map instruction entity with delivery document details
   *
   * @param deliveryDocument fetched from GDM
   * @return Instruction
   */
  public static Instruction mapDeliveryDocumentToInstruction(
      DeliveryDocument deliveryDocument, Instruction instruction) {
    DeliveryDocumentLine deliveryDocumentLine = null;
    deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    instruction.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    instruction.setPoDcNumber(deliveryDocument.getPoDCNumber());
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    if (isNationalPO(deliveryDocumentLine.getPurchaseRefType())) {
      instruction.setGtin(deliveryDocumentLine.getGtin());
      instruction.setItemDescription(deliveryDocumentLine.getDescription());
      instruction.setPurchaseReferenceLineNumber(
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      instruction.setFirstExpiryFirstOut(deliveryDocumentLine.getFirstExpiryFirstOut());
    }
    return instruction;
  }

  public static Instruction mapHttpHeaderToInstruction(
      HttpHeaders httpHeaders, Instruction instruction) {
    instruction.setCreateUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    return instruction;
  }

  /**
   * Create Instruction out of instructionRequest
   *
   * @param instructionRequest
   * @return instruction
   */
  public static Instruction createInstruction(InstructionRequest instructionRequest) {
    Instruction instruction = new Instruction();
    instruction.setMessageId(instructionRequest.getMessageId());
    instruction.setDeliveryNumber(Long.parseLong(instructionRequest.getDeliveryNumber()));
    instruction.setProblemTagId(instructionRequest.getProblemTagId());
    instruction.setCreateTs(new Date());
    return instruction;
  }

  /**
   * Populate instruction entity with the instruction response from FDE which are common to UPC and
   * S2S receiving
   *
   * @param instruction instruction entity
   * @param fdeCreateContainerResponse response from FDE
   * @return Instruction
   */
  public static Instruction populateInstructionCommonWithFdeResponse(
      Instruction instruction, FdeCreateContainerResponse fdeCreateContainerResponse) {
    instruction.setPrintChildContainerLabels(
        fdeCreateContainerResponse.isPrintChildContainerLabels());
    instruction.setInstructionMsg(fdeCreateContainerResponse.getInstructionMsg());
    instruction.setInstructionCode(fdeCreateContainerResponse.getInstructionCode());
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setContainer(fdeCreateContainerResponse.getContainer());
    instruction.setProviderId(fdeCreateContainerResponse.getProviderId());
    instruction.setActivityName(fdeCreateContainerResponse.getActivityName());
    instruction.setMove(fdeCreateContainerResponse.getMove());
    return instruction;
  }

  public static boolean isRegulatedItemType(VendorCompliance regulatedItemType) {
    if (!isNull(regulatedItemType)
        && (regulatedItemType.equals(VendorCompliance.LITHIUM_ION)
            || regulatedItemType.equals(VendorCompliance.LIMITED_QTY)
            || regulatedItemType.equals(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY)))
      return true;
    return false;
  }

  /**
   * Populates instruction with document details and response from FDE
   *
   * @param instructionRequest
   * @param fdeCreateContainerResponse
   * @return instruction
   */
  public static Instruction processInstructionResponse(
      Instruction instruction,
      InstructionRequest instructionRequest,
      FdeCreateContainerResponse fdeCreateContainerResponse) {
    DeliveryDocument deliveryDocument = null;
    DeliveryDocumentLine deliveryDocumentLine = null;
    int vnpkQty = 0;
    int whpkQty = 0;
    boolean isNationalPo = false;

    boolean isNonNationalPoRequest =
        StringUtils.isNotEmpty(instructionRequest.getNonNationPo()) ? Boolean.TRUE : Boolean.FALSE;

    if (!isNonNationalPoRequest) {
      deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

      deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
      isNationalPo =
          isNationalPO(deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseRefType());
    }

    if (!isNonNationalPoRequest
        && deliveryDocumentLine.getVendorPack() != null
        && deliveryDocumentLine.getVendorPack() != 0
        && deliveryDocumentLine.getWarehousePack() != null
        && deliveryDocumentLine.getWarehousePack() != 0
        && isNationalPo) {
      vnpkQty = deliveryDocumentLine.getVendorPack();
      whpkQty = deliveryDocumentLine.getWarehousePack();
    } else {
      vnpkQty = 1;
      whpkQty = 1;
    }
    instruction.setProjectedReceiveQty(
        ReceivingUtils.conversionToVendorPack(
            fdeCreateContainerResponse.getProjectedQty(),
            fdeCreateContainerResponse.getProjectedQtyUom(),
            vnpkQty,
            whpkQty));
    if (fdeCreateContainerResponse.isPrintChildContainerLabels()) {
      instruction.setChildContainers(fdeCreateContainerResponse.getChildContainers());
      Labels labels = new Labels();
      List<String> availableLabels = new ArrayList<>();
      List<ContainerDetails> containerDetailsList = fdeCreateContainerResponse.getChildContainers();
      for (ContainerDetails containerDetails : containerDetailsList) {
        availableLabels.add(containerDetails.getTrackingId());
      }
      labels.setAvailableLabels(availableLabels);
      labels.setUsedLabels(new ArrayList<>());
      instruction.setLabels(labels);
    }
    instruction = populateInstructionCommonWithFdeResponse(instruction, fdeCreateContainerResponse);
    instruction.setManualInstruction(instructionRequest.isManualReceivingEnabled());
    return instruction;
  }

  /**
   * Populates instruction with document details and response from FDE for WFS
   *
   * @param instructionRequest
   * @param fdeCreateContainerResponse
   * @return instruction
   */
  public static Instruction processInstructionResponseForWFS(
      Instruction instruction,
      InstructionRequest instructionRequest,
      FdeCreateContainerResponse fdeCreateContainerResponse) {

    instruction.setProjectedReceiveQty(fdeCreateContainerResponse.getProjectedQty());
    instruction.setManualInstruction(instructionRequest.isManualReceivingEnabled());
    instruction.setPrintChildContainerLabels(
        fdeCreateContainerResponse.isPrintChildContainerLabels());
    instruction.setInstructionMsg(fdeCreateContainerResponse.getInstructionMsg());
    instruction.setInstructionCode(fdeCreateContainerResponse.getInstructionCode());
    instruction.setProjectedReceiveQtyUOM(fdeCreateContainerResponse.getProjectedQtyUom());
    instruction.setContainer(fdeCreateContainerResponse.getContainer());
    instruction.setProviderId(fdeCreateContainerResponse.getProviderId());
    instruction.setActivityName(fdeCreateContainerResponse.getActivityName());
    return instruction;
  }

  /**
   * Populate container for parent and child container for S2S
   *
   * @param gdmContainerResponse container response from GDM
   * @return Container
   */
  public static ContainerModel populateContainer(ContainerResponseData gdmContainerResponse) {
    ContainerModel container = new ContainerModel();
    container.setTrackingId(gdmContainerResponse.getLabel());

    Facility destination = new Facility();
    destination.setBuNumber(gdmContainerResponse.getDestinationNumber().toString());
    destination.setCountryCode(gdmContainerResponse.getDestinationCountryCode());
    container.setCtrDestination(destination);
    container.setWeight(gdmContainerResponse.getWeight());
    container.setWeightUom(gdmContainerResponse.getWeightUOM());
    container.setCube(gdmContainerResponse.getCube());
    container.setCubeUom(gdmContainerResponse.getCubeUOM());

    List<Content> contents = new ArrayList<>();
    if (!CollectionUtils.isEmpty(gdmContainerResponse.getItems())) {
      gdmContainerResponse
          .getItems()
          .forEach(
              gdmContainerItemResponse -> {
                Content content = new Content();
                content.setPurchaseReferenceNumber(
                    gdmContainerItemResponse.getPurchaseOrder().getPurchaseReferenceNumber());
                content.setGtin(gdmContainerItemResponse.getItemUpc());
                content.setItemNbr(Long.valueOf(gdmContainerItemResponse.getItemNumber()));
                content.setBaseDivisionCode(gdmContainerItemResponse.getBaseDivCode());
                content.setFinancialReportingGroup(
                    gdmContainerItemResponse.getPurchaseOrder().getFinancialGroupCode());
                // The contract documentation says purchase ref type is channel method from Inbound
                // Document (or channel type?) SSTK, DIST, CROSSDOCK, DSDC, etc.
                content.setPurchaseRefType(gdmContainerResponse.getChannel());
                content.setQty(gdmContainerItemResponse.getItemQuantity());
                content.setQtyUom(gdmContainerItemResponse.getQuantityUOM());
                contents.add(content);
              });
    }
    container.setContents(contents);
    return container;
  }

  /**
   * Populated FDE request for S2S receiving
   *
   * @param instructionRequest instruction request from client
   * @param shipmentResponseData Container response form GDM
   * @param httpHeaders headers form client
   * @return request payload for FDE
   */
  public static FdeCreateContainerRequest populateCreateContainerRequest(
      InstructionRequest instructionRequest,
      ShipmentResponseData shipmentResponseData,
      HttpHeaders httpHeaders) {
    // Add header
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, InstructionUtils.createFdeCreateContainerRequest(instructionRequest));
    if (shipmentResponseData.getContainer().getInvoiceNumber() != null) {

      fdeCreateContainerRequest.setInvoiceNumber(
          shipmentResponseData.getContainer().getInvoiceNumber().toString());
    }
    // Add container
    ContainerModel container = populateContainer(shipmentResponseData.getContainer());
    fdeCreateContainerRequest.setContainer(container);

    // Add child Container
    List<ContainerModel> childContainers = new ArrayList<>();
    shipmentResponseData
        .getContainer()
        .getContainers()
        .forEach(
            gdmChildContainerResponse -> {
              ContainerModel childContainer = populateContainer(gdmChildContainerResponse);
              childContainers.add(childContainer);
            });
    fdeCreateContainerRequest.getContainer().setChildContainers(childContainers);

    return fdeCreateContainerRequest;
  }

  /**
   * Process instruction response from FDE for S2S
   *
   * @param instructionRequest instruction request from client
   * @param fdeCreateContainerResponse response from FDE
   * @param totalQuantity total received quantity
   * @param httpHeaders headers from client
   * @return instruction entity
   */
  public static Instruction processInstructionResponseForS2S(
      InstructionRequest instructionRequest,
      FdeCreateContainerResponse fdeCreateContainerResponse,
      int totalQuantity,
      HttpHeaders httpHeaders) {
    Instruction instruction = new Instruction();
    instruction.setDeliveryNumber(Long.parseLong(instructionRequest.getDeliveryNumber()));
    instruction.setSsccNumber(instructionRequest.getAsnBarcode());
    instruction.setMessageId(instructionRequest.getMessageId());
    instruction.setReceivedQuantity(0);
    instruction.setProjectedReceiveQty(totalQuantity);
    instruction.setCreateUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    instruction.setChildContainers(fdeCreateContainerResponse.getChildContainers());
    instruction = populateInstructionCommonWithFdeResponse(instruction, fdeCreateContainerResponse);
    return instruction;
  }

  /**
   * Computes move quantity based on quantity in container items or child container size
   *
   * @param container
   * @return move qty
   */
  public static int getMoveQuantity(Container container) {
    int moveQty = 0;
    List<ContainerItem> containerItems = container.getContainerItems();
    if (!CollectionUtils.isEmpty(containerItems)) {
      for (ContainerItem containerItem : containerItems) {
        moveQty += containerItem.getQuantity();
      }
    } else {
      moveQty = container.getChildContainers().size();
    }
    return moveQty;
  }

  /**
   * update instrcution request object to reuse publish instruction to WFM
   *
   * @return updateInstructionRequest
   */
  public static UpdateInstructionRequest prepareRequestForASNInstructionUpdate() {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    DocumentLine documentLine = new DocumentLine();
    documentLine.setVnpkQty(1);
    documentLine.setWhpkQty(1);
    List<DocumentLine> documentLines = new ArrayList<>();
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);
    return updateInstructionRequest;
  }

  /**
   * Converts list of instruction to list of InstructionSummary that contains the minimal info
   * required by UI
   *
   * @param instructionList list of instruction entity objects
   * @return instructionSummaryList contains minimal info required by UI
   */
  public static List<InstructionSummary> convertToInstructionSummaryResponseList(
      List<Instruction> instructionList) {
    List<InstructionSummary> instructionSummaryList = new ArrayList<>();

    for (Instruction instruction : instructionList) {
      instructionSummaryList.add(convertToInstructionSummary(instruction));
    }
    return instructionSummaryList;
  }

  private static void validateLifeExpectancy(
      boolean override,
      Integer minReceivingDays,
      Date userEnteredDate,
      Date thresholdDate,
      Date currentDate,
      String currentDateString,
      String thresholdDateString,
      boolean isKotlinEnabled)
      throws ReceivingException {
    if (minReceivingDays < 0) {
      validateNegativeMinLife(
          override,
          userEnteredDate,
          thresholdDate,
          currentDate,
          currentDateString,
          isKotlinEnabled);
    } else {
      validatePositiveMinLife(
          override,
          userEnteredDate,
          thresholdDate,
          currentDate,
          currentDateString,
          thresholdDateString,
          isKotlinEnabled);
    }
  }

  /**
   * Converts instruction InstructionSummary that contains the minimal info required by UI
   *
   * @return instructionSummaryList contains minimal info required by UI
   */
  public static InstructionSummary convertToInstructionSummary(Instruction instruction) {
    InstructionSummary instructionSummary = new InstructionSummary();
    InstructionData instructionData = new InstructionData();
    ContainerModel container = new ContainerModel();
    Facility ctrDestination = new Facility();
    if (instruction.getContainer() != null) {
      if (instruction.getContainer().getCtrDestination() != null) {
        ctrDestination.setBuNumber(instruction.getContainer().getCtrDestination().get("buNumber"));
        ctrDestination.setCountryCode(
            instruction.getContainer().getCtrDestination().get("countryCode"));
      }
      container.setTrackingId(instruction.getContainer().getTrackingId());
    }
    if (!CollectionUtils.isEmpty(instruction.getMove())) {
      container.setContainerLocation(
          Objects.toString(instruction.getMove().get("toLocation"), null));
    }
    container.setCtrDestination(ctrDestination);
    instructionData.setContainer(container);
    instructionData.setMessageId(instruction.getMessageId());
    instructionSummary.setProblemTagId(instruction.getProblemTagId());
    instructionSummary.setInstructionData(instructionData);
    instructionSummary.setCompleteTs(instruction.getCompleteTs());
    instructionSummary.setCompleteUserId(instruction.getCompleteUserId());
    instructionSummary.setCreateTs(instruction.getCreateTs());
    instructionSummary.setCreateUserId(instruction.getCreateUserId());
    instructionSummary.setLastChangeTs(instruction.getLastChangeTs());
    instructionSummary.setLastChangeUserId(instruction.getLastChangeUserId());
    instructionSummary.setGtin(instruction.getGtin());
    instructionSummary.setDeliveryDocument(instruction.getDeliveryDocument());
    instructionSummary.setId(instruction.getId());
    instructionSummary.set_id(instruction.getId());
    instructionSummary.setItemDescription(instruction.getItemDescription());
    DeliveryDocumentLine deliveryDocumentLine = getDeliveryDocumentLine(instruction);
    if (Objects.nonNull(deliveryDocumentLine)) {
      if (StringUtils.isNotBlank(deliveryDocumentLine.getNdc())) {
        instructionSummary.setNdc(deliveryDocumentLine.getNdc());
      } else {
        instructionSummary.setNdc(deliveryDocumentLine.getVendorStockNumber());
      }
      instructionSummary.setItemNumber(deliveryDocumentLine.getItemNbr());
      instructionSummary.setPurchaseRefType(deliveryDocumentLine.getPurchaseRefType());
    }

    instructionSummary.setInstructionCode(instruction.getInstructionCode());
    instructionSummary.setSscc(instruction.getSsccNumber());
    instructionSummary.setSsccNumber(instruction.getSsccNumber());
    instructionSummary.setPoDcNumber(instruction.getPoDcNumber());
    instructionSummary.setProjectedReceiveQty(instruction.getProjectedReceiveQty());
    instructionSummary.setProjectedReceiveQtyUOM(instruction.getProjectedReceiveQtyUOM());
    instructionSummary.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    instructionSummary.setPurchaseReferenceLineNumber(instruction.getPurchaseReferenceLineNumber());
    instructionSummary.setReceivedQuantity(instruction.getReceivedQuantity());
    instructionSummary.setReceivedQuantityUOM(instruction.getReceivedQuantityUOM());
    instructionSummary.setFirstExpiryFirstOut(instruction.getFirstExpiryFirstOut());
    instructionSummary.setActivityName(instruction.getActivityName());
    instructionSummary.setDockTagId(instruction.getDockTagId());
    instructionSummary.setMove(instruction.getMove());
    instructionSummary.setInstructionSetId(instruction.getInstructionSetId());
    return instructionSummary;
  }

  public static DeliveryDocumentLine getDeliveryDocumentLine(Instruction instruction) {
    DeliveryDocument deliveryDocument = getDeliveryDocument(instruction);

    if (isNull(deliveryDocument)
        || CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
      return null;
    }
    return deliveryDocument.getDeliveryDocumentLines().get(0);
  }

  // Block if White wood pallet weight exceed 2100 lbs
  public static void validatePalletWeight(
      DeliveryDocumentLine deliveryDocumentLine,
      ReceiveInstructionRequest receiveInstructionRequest,
      Float whiteWoodMaxWeight)
      throws ReceivingException {

    if (!ContainerType.WHITEWOOD
        .getText()
        .equalsIgnoreCase(receiveInstructionRequest.getContainerType())) return;

    String weightTypeCode = deliveryDocumentLine.getAdditionalInfo().getWeightFormatTypeCode();
    Integer quantity = receiveInstructionRequest.getQuantity();

    LOG.info("Validate white wood pallet weight for type Code {}", weightTypeCode);
    Float weight =
        ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(weightTypeCode)
            ? deliveryDocumentLine.getBolWeight()
            : deliveryDocumentLine.getAdditionalInfo().getWeight();
    LOG.info("White wood weight received in instruction document line {}", weight);
    weight = isNull(weight) ? 0.0f : weight;
    LOG.info("Final White wood weight {} and quantity {}", weight, quantity);

    LOG.info("Max white wood pallet weight allowed {}", whiteWoodMaxWeight);
    if ((quantity * weight) > whiteWoodMaxWeight) {
      LOG.error(
          "Error occurred as White wood typeCode {}, weight {}, quantity {} exceeds max weight {}",
          weightTypeCode,
          weight,
          quantity,
          whiteWoodMaxWeight);
      throw new ReceivingException(
          ReceivingException.EXCEED_WHITE_WOOD_PALLET_WIGHT,
          BAD_REQUEST,
          ReceivingException.INVALID_WEIGHT_ERROR_CODE,
          ReceivingException.INVALID_WEIGHT_ERROR_HEADER);
    }
  }

  public static DeliveryDocument getDeliveryDocument(Instruction instruction) {
    if (isNull(instruction.getDeliveryDocument())
        || ReceivingConstants.DUMMY_PURCHASE_REF_NUMBER.equalsIgnoreCase(
            instruction.getPurchaseReferenceNumber())) {
      return null;
    }
    return gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
  }

  /**
   * Validate if we are on strict rotation by sell date
   *
   * @param isMinLifeExpectancyV2validation
   * @param firstExpiryFirstOut
   * @param documentLine
   * @param isManagerOverrideIgnoreExpiry
   * @param isKotlinEnabled
   * @throws ReceivingException
   */
  public static void validateThresholdForSellByDate(
      boolean isMinLifeExpectancyV2validation,
      Boolean firstExpiryFirstOut,
      DocumentLine documentLine,
      boolean isManagerOverrideIgnoreExpiry,
      boolean isKotlinEnabled)
      throws ReceivingException {
    Integer minReceivingDays = documentLine.getWarehouseMinLifeRemainingToReceive();
    Date userEnteredDate = documentLine.getRotateDate();
    LOG.info(
        "Enter validateThresholdForSellByDate() with firstExpiryFirstOut :{} userEnteredDate :{} minReceivingDays :{}, isManagerOverrideIgnoreExpiry={}",
        firstExpiryFirstOut,
        userEnteredDate,
        minReceivingDays,
        isManagerOverrideIgnoreExpiry);

    String errorMessage = null;
    if (Boolean.TRUE.equals(firstExpiryFirstOut)) {
      if (minReceivingDays == null) {
        errorMessage =
            String.format(ReceivingException.INVALID_ITEM_ERROR_MSG, documentLine.getItemNumber());
        LOG.error(errorMessage);
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(errorMessage)
                .errorCode(ReceivingException.INVALID_ITEM_ERROR_CODE)
                .errorHeader(ReceivingException.INVALID_ITEM_ERROR_HEADER)
                .errorKey(ExceptionCodes.INVALID_ITEM_ERROR_MSG)
                .values(new Object[] {documentLine.getItemNumber()})
                .build();
        throw ReceivingException.builder()
            .httpStatus(BAD_REQUEST)
            .errorResponse(errorResponse)
            .build();
      }

      if (userEnteredDate == null) {
        LOG.error(ReceivingException.INVALID_EXP_DATE);
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.INVALID_EXP_DATE)
                .errorCode(INVALID_EXP_DATE_ERROR_CODE)
                .errorHeader(CLOSE_DATE_ERROR_HEADER)
                .errorKey(ExceptionCodes.INVALID_EXP_DATE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(BAD_REQUEST)
            .errorResponse(errorResponse)
            .build();
      }

      Date thresholdDate =
          Date.from(
              Instant.now().plus(minReceivingDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
      final Date currentDate = Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS));
      final String currentDateString = InstructionUtils.getLocalDateTimeAsString(currentDate);
      final String thresholdDateString = InstructionUtils.getLocalDateTimeAsString(thresholdDate);
      final String userEnteredDateString =
          InstructionUtils.getLocalDateTimeAsString(userEnteredDate);
      LOG.info(
          "thresholdDate={},thresholdDateString={} currentDate={}, currentDateString={}, userEnteredDate={}",
          thresholdDate,
          thresholdDateString,
          currentDate,
          currentDateString,
          userEnteredDateString);
      /*
      On supervisor override, if min receiving days is positive, we should not allow any date but only dates current or greater
      On supervisor override, if min receiving days is negative, we should not allow any date but only dates current or past
      On normal flow, if min receiving days is negative then date must be between (current  + negative min receiving days) and current
      On normal flow, if min receiving days is positive then date must be between current and (current  + positive min receiving days)
      */
      if (!isMinLifeExpectancyV2validation) {
        if (!isManagerOverrideIgnoreExpiry
            && (minReceivingDays != 0 && userEnteredDate.before(thresholdDate))) {
          errorMessage =
              InstructionUtils.getUpdateDateErrorMessage(
                  INVALID_EXP_DATE_ERROR_MSG, userEnteredDate, thresholdDateString);
          LOG.error(errorMessage);
          if (isKotlinEnabled) {
            throw new ReceivingBadDataException(EXPIRED_PRODUCT_DATE_ERROR_CODE, errorMessage);
          }
          HashMap<String, String> mapForJson = new HashMap<>();
          mapForJson.put(EXPIRY_THRESHOLD_DATE, thresholdDateString);

          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(errorMessage)
                  .errorCode(INVALID_EXP_DATE_ERROR_CODE)
                  .errorHeader(CLOSE_DATE_ERROR_HEADER)
                  .errorInfo(mapForJson)
                  .errorKey(ExceptionCodes.INVALID_EXP_DATE_ERROR_MSG)
                  .values(new Object[] {userEnteredDate, thresholdDateString})
                  .build();
          throw ReceivingException.builder()
              .httpStatus(BAD_REQUEST)
              .errorResponse(errorResponse)
              .build();
        }
      } else {
        validateLifeExpectancy(
            isManagerOverrideIgnoreExpiry,
            minReceivingDays,
            userEnteredDate,
            thresholdDate,
            currentDate,
            currentDateString,
            thresholdDateString,
            isKotlinEnabled);
      }
    }
  }

  private static void validatePositiveMinLife(
      boolean isManagerOverrideIgnoreExpiry,
      Date userEnteredDate,
      Date thresholdDate,
      Date currentDate,
      String currentDateString,
      String thresholdDateString,
      boolean isKotlinEnabled)
      throws ReceivingException {
    if (userEnteredDate.before(currentDate)) {
      throwUpdateDateError(
          isKotlinEnabled ? INVALID_PRODUCT_DATE_ERROR_CODE : UPDATE_DATE_ERROR_CODE,
          EXPIRED_PRODUCT_HEADER,
          PAST_DATE_MSG,
          userEnteredDate,
          currentDateString,
          isKotlinEnabled);
    }
    if (userEnteredDate.before(thresholdDate) && !isManagerOverrideIgnoreExpiry) {
      throwUpdateDateError(
          isKotlinEnabled ? EXPIRED_PRODUCT_DATE_ERROR_CODE : INVALID_EXP_DATE_ERROR_CODE,
          CLOSE_DATE_ERROR_HEADER,
          INVALID_EXP_DATE_ERROR_MSG,
          userEnteredDate,
          thresholdDateString,
          isKotlinEnabled);
    }
  }

  private static void validateNegativeMinLife(
      boolean isManagerOverrideIgnoreExpiry,
      Date userEnteredDate,
      Date thresholdDate,
      Date currentDate,
      String currentDateString,
      boolean isKotlinEnabled)
      throws ReceivingException {
    final boolean isUserEnteredFutureDate = userEnteredDate.after(currentDate);
    if (isUserEnteredFutureDate) {
      throwUpdateDateError(
          isKotlinEnabled ? INVALID_PRODUCT_DATE_ERROR_CODE : UPDATE_DATE_ERROR_CODE,
          UPDATE_DATE_ERROR_HEADER,
          UPDATE_DATE_ERROR_MSG,
          thresholdDate,
          currentDateString,
          isKotlinEnabled);
    } else {
      if (userEnteredDate.before(thresholdDate) && !isManagerOverrideIgnoreExpiry) {
        throwUpdateDateError(
            isKotlinEnabled ? EXPIRED_PRODUCT_DATE_ERROR_CODE : INVALID_EXP_DATE_ERROR_CODE,
            UPDATE_DATE_ERROR_HEADER,
            UPDATE_DATE_ERROR_MGR_MSG,
            thresholdDate,
            currentDateString,
            isKotlinEnabled);
      }
    }
  }

  private static void throwUpdateDateError(
      String code, String header, String msg, Date date1, String date2, boolean isKotlinEnabled)
      throws ReceivingException {
    String errorMessage;
    errorMessage = InstructionUtils.getUpdateDateErrorMessage(msg, date1, date2);
    LOG.error(errorMessage);
    if (isKotlinEnabled) {
      throw new ReceivingBadDataException(code, errorMessage);
    }
    HashMap<String, String> mapForJson = new HashMap<>();
    mapForJson.put(EXPIRY_THRESHOLD_DATE, date2);
    throw new ReceivingException(errorMessage, BAD_REQUEST, code, header, mapForJson);
  }

  public static String getLocalDateTimeAsString(Date date) {
    return date.toInstant()
        .atZone(ZoneId.of(UTC_TIME_ZONE))
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern(INVALID_EXP_DATE_FORMAT));
  }

  public static String getUpdateDateErrorMessage(
      String errMessage, Date sellByDateEntered, String expiryThresholdDate) {
    final String sellByDateEnteredString =
        sellByDateEntered
            .toInstant()
            .atZone(ZoneId.of(UTC_TIME_ZONE))
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern(INVALID_EXP_DATE_FORMAT));
    return String.format(errMessage, sellByDateEnteredString, expiryThresholdDate);
  }

  /**
   * This method will check PO DC number and user session facility number are same or not.
   *
   * @param purchaseRefType
   * @return
   */
  public static boolean isNationalPO(String purchaseRefType) {

    return (!StringUtils.isBlank(purchaseRefType)
        && !PurchaseReferenceType.POCON.name().equalsIgnoreCase(purchaseRefType));
  }

  public static boolean isCancelledPOOrPOLine(DeliveryDocument deliveryDocument) {

    String purchaseRefStatus = deliveryDocument.getPurchaseReferenceStatus();
    String purchaseRefLineStatus =
        deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineStatus();
    if (ReceivingUtils.isPOLineCancelled(purchaseRefStatus, purchaseRefLineStatus)) return true;

    return false;
  }

  public static boolean cancelPOPOLCheckNotRequired(
      DeliveryDocument deliveryDocument, boolean isAllowPOCONRcvOnCancelledPOPOL) {
    if (isAllowPOCONRcvOnCancelledPOPOL
        && ReceivingConstants.POCON_ACTIVITY_NAME.equals(
            deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseRefType())) {
      return true;
    }
    return false;
  }

  public static boolean isRejectedPOLine(DeliveryDocumentLine deliveryDocumentLine) {

    if (!isNull(deliveryDocumentLine)) {
      OperationalInfo operationalInfo = deliveryDocumentLine.getOperationalInfo();
      if (!isNull(operationalInfo)
          && !StringUtils.isBlank(operationalInfo.getState())
          && operationalInfo.getState().equalsIgnoreCase(POLineStatus.REJECTED.name())) return true;
    }
    return false;
  }

  /**
   * Populate create container request with Freight bull quantity
   *
   * @param instructionRequest instruction request fro UI
   * @param httpHeaders headers
   * @return fdeCreateContainerRequest cbr request
   */
  public static FdeCreateContainerRequest populateCreateContainerRequestWithFbq(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Integer freightBillQty = deliveryDocumentLine.getFreightBillQty();
    if (isNull(freightBillQty)) {
      GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.MISSING_FREIGHT_BILL_QTY);

      String errorMessage =
          String.format(
              gdmError.getErrorMessage(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
      LOG.error(errorMessage);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(errorMessage)
              .errorCode(gdmError.getErrorCode())
              .errorHeader(gdmError.getErrorHeader())
              .errorKey(ExceptionCodes.MISSING_FREIGHT_BILL_QTY)
              .values(
                  new Object[] {
                    deliveryDocumentLine.getPurchaseReferenceNumber(),
                    deliveryDocumentLine.getPurchaseReferenceLineNumber()
                  })
              .build();
      throw ReceivingException.builder()
          .httpStatus(BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
    FdeCreateContainerRequest fdeCreateContainerRequest =
        mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, createFdeCreateContainerRequest(instructionRequest));
    fdeCreateContainerRequest.setContainer(createContainerModel(deliveryDocument));

    Content content = fdeCreateContainerRequest.getContainer().getContents().get(0);
    Integer receivedQty = deliveryDocumentLine.getTotalOrderQty() - content.getOpenQty();
    content.setFreightBillQty(deliveryDocumentLine.getFreightBillQty());
    content.setOpenQty(Math.min(freightBillQty, content.getMaxReceiveQty()) - receivedQty);
    return fdeCreateContainerRequest;
  }

  /**
   * Populate Witron label with additional attributes
   *
   * @param instruction
   * @param rotateDate
   * @param userId
   * @param dcTimeZone
   * @return printjob
   */
  public static Map<String, Object> getPrintJobWithWitronAttributes(
      Instruction instruction,
      String rotateDate,
      String userId,
      String printerName,
      String dcTimeZone) {
    Map<String, Object> printJob = instruction.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");

    Map<String, Object> rotateDateMap = new HashMap<>();
    rotateDateMap.put("key", "rotateDate");
    rotateDateMap.put("value", rotateDate);
    labelData.add(rotateDateMap);

    Map<String, Object> printDateMap = new HashMap<>();
    printDateMap.put("key", "printDate");
    printDateMap.put("value", ReceivingUtils.getDcDateTime(dcTimeZone));
    labelData.add(printDateMap);

    Map<String, Object> userIdMap = new HashMap<>();
    userIdMap.put("key", "userId");
    userIdMap.put("value", userId);
    labelData.add(userIdMap);

    Map<String, Object> printerIdMap = new HashMap<>();
    printerIdMap.put("key", "printerId");
    printerIdMap.put("value", printerName);
    labelData.add(printerIdMap);

    printRequest.put("data", labelData);
    printRequests.set(0, printRequest);
    printJob.put("printRequests", printRequests);

    return printJob;
  }

  /**
   * This method checks whether the delivery document contains DA Conveyable freight, if so it will
   * return true. Otherwise false will be returned.
   *
   * @param instructionRequest
   * @return isDACo 543
   */
  public static boolean isDAConRequest(InstructionRequest instructionRequest) {
    return isDAConFreight(
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsConveyable(),
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseRefType(),
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getActiveChannelMethods());
  }

  /**
   * Check if the purchase reference type is CROSSU/CROSSMU and Conveyable
   *
   * @param isConveyable
   * @param purchaseRefType
   * @param activeChannelMethods
   * @return
   */
  public static boolean isDAConFreight(
      boolean isConveyable, String purchaseRefType, List<String> activeChannelMethods) {
    return isConveyable && isDAFreight(purchaseRefType, activeChannelMethods);
  }

  public static boolean isDAFreight(String purchaseRefType, List<String> activeChannelMethods) {
    String poType = getPurchaseRefTypeIncludingChannelFlip(purchaseRefType, activeChannelMethods);
    return (PurchaseReferenceType.CROSSMU.name().equalsIgnoreCase(poType)
        || PurchaseReferenceType.CROSSU.name().equalsIgnoreCase(poType)
        || PurchaseReferenceType.CROSSA.name().equalsIgnoreCase(poType));
  }
  /**
   * Prepare and returns place on conveyor instruction
   *
   * @param gtin
   * @return instruction
   */
  public static Instruction getPlaceOnConveyorInstruction(
      String gtin, DeliveryDocument deliveryDocument, Long deliveryNumber) {
    Instruction instruction = new Instruction();
    instruction.setInstructionCode("Place On Conveyor");
    instruction.setInstructionMsg("Place item on conveyor instruction");
    instruction.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setDeliveryNumber(deliveryNumber);
    instruction.setGtin(gtin);
    if (Objects.nonNull(deliveryDocument))
      instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  public static Instruction getItemCollisionInstruction(
      Long deliveryNumber, String commonItemsList) {
    Instruction itemCollisionInstruction = new Instruction();
    itemCollisionInstruction.setDeliveryNumber(deliveryNumber);
    itemCollisionInstruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    itemCollisionInstruction.setInstructionCode(ReceivingConstants.ITEM_COLLISION_INSTRUCTION_CODE);
    itemCollisionInstruction.setInstructionMsg(
        String.format(
            ReceivingConstants.ITEM_COLLISION_INSTRUCTION_MESSAGE,
            commonItemsList,
            deliveryNumber));
    return itemCollisionInstruction;
  }

  /**
   * Get the purchase reference type based on activeChannelMethods
   *
   * @param purchaseRefType
   * @param activeChannelMethods
   * @return
   */
  public static String getPurchaseRefTypeIncludingChannelFlip(
      String purchaseRefType, List<String> activeChannelMethods) {
    if (PurchaseReferenceType.CROSSMU.name().equalsIgnoreCase(purchaseRefType)
        || PurchaseReferenceType.CROSSA.name().equalsIgnoreCase(purchaseRefType)) {
      return purchaseRefType;
    } else if (isPurchaseRefSSTKUOrCROSSU(purchaseRefType)
        && !CollectionUtils.isEmpty(activeChannelMethods)) {
      if (activeChannelMethods.size() == 1) {
        return activeChannelMethods.get(0);
      } else if (activeChannelMethods.contains(PurchaseReferenceType.SSTKU.name())) {
        return PurchaseReferenceType.SSTKU.name();
      }
    }
    return purchaseRefType;
  }

  public static boolean isPurchaseRefSSTKUOrCROSSU(String purchaseRefType) {
    return PurchaseReferenceType.SSTKU.name().equals(purchaseRefType)
        || PurchaseReferenceType.CROSSU.name().equals(purchaseRefType);
  }

  public static FdeCreateContainerRequest populateCreateContainerRequestForNonNationalPOs(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();

    List<Content> contents = new ArrayList<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      Content content = new Content();
      content.setItemNbr(ReceivingConstants.DUMMY_ITEM_NUMBER);
      content.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
      content.setPoDcNumber(deliveryDocument.getPoDCNumber());
      content.setOpenQty(deliveryDocument.getQuantity());
      content.setQtyUom(ReceivingConstants.Uom.VNPK);
      content.setPurchaseRefType(instructionRequest.getNonNationPo());
      content.setPurchaseReferenceLegacyType(deliveryDocument.getPurchaseReferenceLegacyType());
      content.setBaseDivisionCode(deliveryDocument.getBaseDivisionCode());
      content.setFinancialReportingGroup(deliveryDocument.getFinancialReportingGroup());
      contents.add(content);
    }

    ContainerModel containerModel = new ContainerModel();
    containerModel.setContents(contents);

    FdeCreateContainerRequest fdeCreateContainerRequest =
        mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, createFdeCreateContainerRequest(instructionRequest));
    fdeCreateContainerRequest.setContainer(containerModel);

    return fdeCreateContainerRequest;
  }

  /**
   * This method is used to filter out DSDC PO's
   *
   * @param deliveryDocuments
   * @return
   * @throws ReceivingException
   */
  public static List<DeliveryDocument> filteroutDSDCPos(List<DeliveryDocument> deliveryDocuments)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      if (!deliveryDocument
          .getPurchaseReferenceLegacyType()
          .equals(ReceivingConstants.DSDC_PURCHASE_REF_LEGACY_TYPE)) {
        deliveryDocumentList.add(deliveryDocument);
      }
    }
    if (deliveryDocumentList.isEmpty()) {
      GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.DSDC_PO_INFO_ERROR);
      throw new ReceivingException(
          gdmError.getErrorMessage(),
          BAD_REQUEST,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument> filterDAConDeliveryDocumentsForManualReceiving(
      List<DeliveryDocument> deliveryDocuments) {
    LOG.info("Filtering DA CON PO's when manual receiving is enabled {} ", deliveryDocuments);
    List<DeliveryDocument> deliveryDocumentListDACon = new ArrayList<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (InstructionUtils.isDAConFreight(
            deliveryDocumentLine.getIsConveyable(),
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods()))
          deliveryDocumentListDACon.add(deliveryDocument);
      }
    }
    return deliveryDocumentListDACon;
  }

  /**
   * @param labelIdentifier
   * @param httpHeaders
   * @param labelDataList
   * @return ctr label in format compatible with android printing
   */
  public static Map<String, Object> getContainerLabelWithNewPrintingFmt(
      String labelIdentifier, HttpHeaders httpHeaders, List<Map<String, Object>> labelDataList) {
    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", labelIdentifier);
    printRequest.put("formatName", "dock_tag_atlas");
    printRequest.put("ttlInHours", 72);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<>();
    printRequestList.add(printRequest);

    Map<String, String> headers = new HashMap<>();
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    headers.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", ReceivingConstants.RECEIVING_PROVIDER_ID);
    containerLabel.put("headers", headers);
    containerLabel.put("printRequests", printRequestList);
    return containerLabel;
  }

  /**
   * @param labelIdentifier
   * @param labelDataList
   * @return ctr label in format compatible with old printing
   */
  public static Map<String, Object> getContainerLabelWithOldPrintingFmt(
      String labelIdentifier, List<Map<String, Object>> labelDataList) {
    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("ttlInHours", 72);
    containerLabel.put("data", labelDataList);
    containerLabel.put("labelData", labelDataList);
    containerLabel.put("labelIdentifier", labelIdentifier);
    containerLabel.put("clientId", ReceivingConstants.RECEIVING_PROVIDER_ID);
    containerLabel.put("clientID", ReceivingConstants.RECEIVING_PROVIDER_ID);
    containerLabel.put("formatId", "dock_tag_atlas");
    containerLabel.put("formatID", "dock_tag_atlas");
    return containerLabel;
  }

  /**
   * Create update instruction request payload used for verification scan and manual receiving
   *
   * @param location Door or ACL location
   * @param deliveryNum delivery number
   * @param purRefType purchase reference type
   * @param deliveryDocument delivery document
   * @return update instruction request
   */
  public static UpdateInstructionRequest getInstructionUpdateRequestForOnConveyor(
      String location, Long deliveryNum, String purRefType, DeliveryDocument deliveryDocument) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDoorNumber(location);
    updateInstructionRequest.setDeliveryNumber(deliveryNum);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());
    updateInstructionRequest.setFacility(facility);

    DocumentLine documentLine = new DocumentLine();
    documentLine.setQuantity(1);
    documentLine.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    documentLine.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    documentLine.setTotalPurchaseReferenceQty(deliveryDocument.getTotalPurchaseReferenceQty());
    documentLine.setPurchaseRefType(purRefType);
    documentLine.setQuantityUOM(ReceivingConstants.Uom.VNPK);
    documentLine.setPurchaseCompanyId(Integer.parseInt(deliveryDocument.getPurchaseCompanyId()));
    documentLine.setGtin(deliveryDocumentLine.getGtin());
    documentLine.setItemNumber(deliveryDocumentLine.getItemNbr());
    documentLine.setVnpkQty(deliveryDocumentLine.getVendorPack());
    documentLine.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    documentLine.setVendorPackCost(deliveryDocumentLine.getVendorPackCost().doubleValue());
    documentLine.setWhpkSell(deliveryDocumentLine.getWarehousePackSell().doubleValue());
    documentLine.setDeptNumber(Integer.parseInt(deliveryDocument.getDeptNumber()));
    documentLine.setVendorNumber(deliveryDocument.getVendorNumber());
    documentLine.setPromoBuyInd(deliveryDocumentLine.getPromoBuyInd());
    List<DocumentLine> documentLineList = new ArrayList<>();
    documentLineList.add(documentLine);

    updateInstructionRequest.setDeliveryDocumentLines(documentLineList);
    return updateInstructionRequest;
  }

  public static boolean isChannelFlippedToSSTKForDelivery(
      List<DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments
        .stream()
        .anyMatch(
            deliveryDocument ->
                deliveryDocument
                    .getDeliveryDocumentLines()
                    .stream()
                    .anyMatch(
                        deliveryDocumentLine ->
                            !isDAConFreight(
                                deliveryDocumentLine.getIsConveyable(),
                                deliveryDocumentLine.getPurchaseRefType(),
                                deliveryDocumentLine.getActiveChannelMethods())));
  }

  /**
   * This method filters delivery document by po/pol
   *
   * @param deliveryDocuments
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return List of select deliveryDocumentLine by PO/POL
   */
  public static List<DeliveryDocument> filterDeliveryDocumentByPOPOL(
      List<DeliveryDocument> deliveryDocuments,
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber) {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();

    DeliveryDocument deliveryDocument = null;
    DeliveryDocumentLine documentLine = null;
    if (Objects.nonNull(deliveryDocuments)) {
      for (DeliveryDocument po : deliveryDocuments) {
        if (Objects.nonNull(po.getDeliveryDocumentLines())
            && po.getPurchaseReferenceNumber().equals(purchaseReferenceNumber)) {
          deliveryDocument = po;
          for (DeliveryDocumentLine poLine : po.getDeliveryDocumentLines()) {
            if (po.getPurchaseReferenceNumber().equals(purchaseReferenceNumber)
                && poLine.getPurchaseReferenceLineNumber() == purchaseReferenceLineNumber) {
              documentLine = poLine;
              break;
            }
          }
        }
      }
    }

    if (Objects.nonNull(deliveryDocument)) {
      deliveryDocument.getDeliveryDocumentLines().clear();

      if (Objects.nonNull(documentLine)) {
        deliveryDocument.getDeliveryDocumentLines().add(documentLine);
      }
      deliveryDocumentList.add(deliveryDocument);
    }

    return deliveryDocumentList;
  }

  public static boolean isMultiPoPol(List<DeliveryDocument> deliveryDocuments) {
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      return false;
    } else if (deliveryDocuments.size() > 1) {
      return true;
    } else if (CollectionUtils.isEmpty(deliveryDocuments.get(0).getDeliveryDocumentLines())) {
      return false;
    } else if (deliveryDocuments.get(0).getDeliveryDocumentLines().size() > 1) {
      return true;
    } else {
      return false;
    }
  }

  public static String getOriginalChannelMethod(String poType) {
    if (isNull(poType)) {
      return null;
    }
    if (PoType.contains(poType)) {
      poType = PoType.valueOf(poType).getpoType();
    } else {
      poType = PoType.SSTK.getpoType();
      LOG.error(ReceivingException.CHANNEL_METHOD_UNKNOWN, poType);
    }
    return poType;
  }

  public static void checkIfDeliveryStatusReceivable(DeliveryDocument deliveryDocuments_gdm)
      throws ReceivingException {
    String errorMessage = null;
    String deliveryStatus = deliveryDocuments_gdm.getDeliveryStatus().toString();
    String deliveryLegacyStatus = deliveryDocuments_gdm.getDeliveryLegacyStatus();
    // Delivery which is in Working or Open state with out pending problem can be receivable .
    if (ReceivingUtils.checkIfDeliveryWorkingOrOpen(deliveryStatus, deliveryLegacyStatus)) return;
    if (ReceivingUtils.needToCallReopen(deliveryStatus, deliveryLegacyStatus)) {
      errorMessage =
          String.format(
              ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE_REOPEN, deliveryLegacyStatus);
      LOG.error(errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE_REOPEN,
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
    } else {
      errorMessage =
          String.format(ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE, deliveryLegacyStatus);
      LOG.error(errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE,
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
    }
  }

  public static void checkIfDeliveryDocumentHasWgtCbAdditionalInfo(
      DeliveryDocument deliveryDocuments_gdm) throws ReceivingException {
    String errorMessage;
    DeliveryDocumentLine firstDocLine = deliveryDocuments_gdm.getDeliveryDocumentLines().get(0);
    if (StringUtils.isEmpty(firstDocLine.getAdditionalInfo().getWeightQty())
        || StringUtils.isEmpty(firstDocLine.getAdditionalInfo().getCubeQty())
        || StringUtils.isEmpty(firstDocLine.getAdditionalInfo().getWeightQtyUom())
        || StringUtils.isEmpty(firstDocLine.getAdditionalInfo().getCubeUomCode())) {
      errorMessage =
          String.format(
              ReceivingException.DELIVERY_DOC_MISSING_WGT_CUBE,
              firstDocLine.getItemUpc(),
              firstDocLine.getItemNbr());
      LOG.error(errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ReceivingException.DELIVERY_DOC_MISSING_WGT_CUBE,
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
    }
  }

  public static boolean isReceiptPostingRequired(
      String deliveryStatus, List<String> stateReasonCodes) {
    if (!StringUtils.isEmpty(deliveryStatus)
        && !DeliveryStatus.WRK.name().equalsIgnoreCase(deliveryStatus)
        && !(DeliveryStatus.OPN.name().equalsIgnoreCase(deliveryStatus)
            && (!CollectionUtils.isEmpty(stateReasonCodes)
                && (stateReasonCodes.contains(DeliveryReasonCodeState.DELIVERY_REOPENED.name())
                    || stateReasonCodes.contains(
                        DeliveryReasonCodeState.PENDING_DOCK_TAG.name()))))) {
      return true;
    }
    return false;
  }

  @ExceptionCounted(
      name = "OFrequestExceptionCount",
      level1 = "uwms-receiving",
      level2 = "FdeService",
      level3 = "instructionDetails")
  public static String fdeExceptionExcludedNoAllocation(InstructionError instructionError)
      throws ReceivingException {
    throw new ReceivingException(
        instructionError.getErrorMessage(),
        HttpStatus.INTERNAL_SERVER_ERROR,
        instructionError.getErrorCode(),
        instructionError.getErrorHeader());
  }

  @ExceptionCounted(
      name = "OFrequestExceptionCount",
      level1 = "uwms-receiving",
      level2 = "FdeService",
      level3 = "instructionDetails")
  public static String fdeExceptionExcludedNoAllocation(
      InstructionError instructionError, String errorKey, Object... values)
      throws ReceivingException {
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(instructionError.getErrorMessage())
            .errorCode(instructionError.getErrorCode())
            .errorHeader(instructionError.getLocaliseErrorHeader())
            .errorKey(errorKey)
            .values(new Object[] {values})
            .build();
    throw ReceivingException.builder()
        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .errorResponse(errorResponse)
        .build();
  }

  // Created a new method for custom message in instruction exception
  @ExceptionCounted(
      name = "OFrequestExceptionCount",
      level1 = "uwms-receiving",
      level2 = "FdeService",
      level3 = "instructionDetails")
  public static String fdeExceptionExcludedNoAllocationDetailedAllocationErrorMessage(
      String errorMessage, String errorCode, String errorHeader, String errorKey, Object... values)
      throws ReceivingException {
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .errorHeader(errorHeader)
            .errorKey(errorKey)
            .values(new Object[] {values})
            .build();
    throw ReceivingException.builder()
        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .errorResponse(errorResponse)
        .build();
  }

  public static List<DeliveryDocument> filterCancelledPoPoLine(
      List<DeliveryDocument> deliveryDocuments) throws ReceivingException {
    List<DeliveryDocument> activeDeliveryDocuments =
        deliveryDocuments
            .stream()
            .filter(
                deliveryDocument -> {
                  List<DeliveryDocumentLine> activeDeliveryDocumentLines =
                      deliveryDocument
                          .getDeliveryDocumentLines()
                          .stream()
                          .filter(
                              deliveryDocumentLine ->
                                  !ReceivingUtils.isPOLineCancelled(
                                      deliveryDocument.getPurchaseReferenceStatus(),
                                      deliveryDocumentLine.getPurchaseReferenceLineStatus()))
                          .collect(Collectors.toList());
                  if (activeDeliveryDocumentLines.size() == 0) {
                    return false;
                  } else {
                    deliveryDocument.setDeliveryDocumentLines(activeDeliveryDocumentLines);
                    return true;
                  }
                })
            .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(activeDeliveryDocuments)) {
      throw new ReceivingException(
          String.format(
              CANCELLED_PO_INSTRUCTION,
              deliveryDocuments.get(0).getPurchaseReferenceNumber(),
              deliveryDocuments.size() > 1
                  ? " + " + (deliveryDocuments.size() - 1) + CANCELLED_PO_INSTRUCTION_MORE
                  : ReceivingConstants.EMPTY_STRING,
              deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr()),
          BAD_REQUEST,
          CANCELLED_PO_INSTRUCTION_ERROR_CODE,
          CANCELLED_PO_INSTRUCTION_ERROR_HEADER);
    }
    return activeDeliveryDocuments;
  }

  /**
   * @param instruction
   * @param receiveInstructionRequest
   * @return
   */
  public static UpdateInstructionRequest constructUpdateInstructionRequest(
      Instruction instruction, ReceiveInstructionRequest receiveInstructionRequest) {
    LOG.info("Enter constructUpdateInstructionRequest with ID: {}", instruction.getId());
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setFlowDescriptor(receiveInstructionRequest.getFlowDescriptor());
    DeliveryDocument poInfo = getDeliveryDocument(instruction);
    DeliveryDocumentLine poLineInfo = getDeliveryDocumentLine(instruction);

    if (Objects.nonNull(poInfo) && Objects.nonNull(poLineInfo)) {
      updateInstructionRequest.setDeliveryNumber(instruction.getDeliveryNumber());
      updateInstructionRequest.setDoorNumber(receiveInstructionRequest.getDoorNumber());
      updateInstructionRequest.setContainerType(receiveInstructionRequest.getContainerType());
      updateInstructionRequest.setPbylLocation(receiveInstructionRequest.getPbylLocation());

      Map<String, String> facilityMap = new HashMap<>();
      facilityMap.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());
      facilityMap.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
      updateInstructionRequest.setFacility(facilityMap);
      updateInstructionRequest.setDeliveryDocumentLines(
          Collections.singletonList(
              mapInstructionDeliveryDocumentToDeliveryDocumentLine(
                  poInfo,
                  poLineInfo,
                  receiveInstructionRequest.getQuantity(),
                  receiveInstructionRequest.getQuantityUOM(),
                  receiveInstructionRequest.getRotateDate())));
    }
    updateInstructionRequest.setUserRole(receiveInstructionRequest.getUserRole());
    LOG.info("Exit constructUpdateInstructionRequest with ID: {}", instruction.getId());
    return updateInstructionRequest;
  }

  public static UpdateInstructionRequest constructUpdateInstructionRequestForCancelContainer(
      Instruction instruction, Container container) {
    LOG.info(
        "Enter constructUpdateInstructionRequest with ID: {}",
        instruction.getId() + " for Cancel Container");
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    List<ContainerItem> containerItems = container.getContainerItems();
    ContainerItem containerItem = containerItems.get(0);

    DeliveryDocument poInfo = getDeliveryDocument(instruction);
    DeliveryDocumentLine poLineInfo = getDeliveryDocumentLine(instruction);

    if (Objects.nonNull(poInfo) && Objects.nonNull(poLineInfo)) {

      Map<String, String> facilityMap = new HashMap<>();
      facilityMap.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());
      facilityMap.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
      updateInstructionRequest.setFacility(facilityMap);

      updateInstructionRequest.setDeliveryDocumentLines(
          Collections.singletonList(
              mapInstructionDeliveryDocumentToDeliveryDocumentLine(
                  poInfo,
                  poLineInfo,
                  instruction.getReceivedQuantity(),
                  instruction.getReceivedQuantityUOM(),
                  containerItem.getRotateDate())));

      if (Objects.nonNull(containerItem.getContainerItemMiscInfo())
          && Objects.nonNull(containerItem.getContainerItemMiscInfo().get(USER_ROLE))) {
        String userRole = containerItem.getContainerItemMiscInfo().get(USER_ROLE);
        updateInstructionRequest.setUserRole(userRole);
      }
      updateInstructionRequest.setCreateUser(instruction.getCreateUserId());
      updateInstructionRequest.setEventType(VTR);
    }

    LOG.info(
        "Exit constructUpdateInstructionRequest with ID: {}",
        instruction.getId() + " for Cancel Container");
    return updateInstructionRequest;
  }

  public static DocumentLine mapInstructionDeliveryDocumentToDeliveryDocumentLine(
      DeliveryDocument poInfo,
      DeliveryDocumentLine poLineInfo,
      Integer quantity,
      String quantityUom,
      Date rotateDate) {
    int maxReceiveQty =
        poLineInfo.getTotalOrderQty()
            + Optional.ofNullable(poLineInfo.getOverageQtyLimit()).orElse(0);
    return mapInstructionDeliveryDocumentToDeliveryDocumentLine(
        poInfo, poLineInfo, quantity, quantityUom, rotateDate, (long) maxReceiveQty);
  }

  /**
   * Maps delivery document (with one po line) stored in instruction table to a single PO line can
   * be used in update instruction request body.
   *
   * @param poInfo delivery document from instruction stored in table
   * @param poLineInfo po line from stored delivery doc in table
   * @param quantity quantity to receive
   * @param quantityUom uom of quantity to receive
   * @param rotateDate rotateDate from {@link ReceiveInstructionRequest}
   * @return
   */
  public static DocumentLine mapInstructionDeliveryDocumentToDeliveryDocumentLine(
      DeliveryDocument poInfo,
      DeliveryDocumentLine poLineInfo,
      Integer quantity,
      String quantityUom,
      Date rotateDate,
      Long maxReceiveQty) {
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPoDCNumber(poInfo.getPoDCNumber());
    documentLine.setPoDeptNumber(poInfo.getDeptNumber());
    documentLine.setPurchaseCompanyId(Integer.valueOf(poInfo.getPurchaseCompanyId()));
    documentLine.setTotalPurchaseReferenceQty(poInfo.getTotalPurchaseReferenceQty());
    documentLine.setBaseDivisionCode(poInfo.getBaseDivisionCode());
    documentLine.setFinancialReportingGroupCode(poInfo.getFinancialReportingGroup());
    documentLine.setVendorNumber(poInfo.getVendorNumber());
    documentLine.setPurchaseReferenceNumber(poLineInfo.getPurchaseReferenceNumber());
    documentLine.setPurchaseReferenceLineNumber(poLineInfo.getPurchaseReferenceLineNumber());
    documentLine.setPurchaseRefType(poLineInfo.getPurchaseRefType());
    documentLine.setGtin(poLineInfo.getGtin());
    documentLine.setItemNumber(poLineInfo.getItemNbr());
    documentLine.setRotateDate(rotateDate);
    documentLine.setDeptNumber(Integer.valueOf(poLineInfo.getDepartment()));
    documentLine.setPalletTi(poLineInfo.getPalletTie());
    documentLine.setPalletHi(poLineInfo.getPalletHigh());
    documentLine.setQuantity(quantity);
    documentLine.setQuantityUOM(quantityUom);
    documentLine.setExpectedQty(Long.valueOf(poLineInfo.getTotalOrderQty()));
    documentLine.setFreightBillQty(poLineInfo.getFreightBillQty());
    documentLine.setMaxOverageAcceptQty(Long.valueOf(poLineInfo.getOverageQtyLimit()));
    documentLine.setVnpkQty(poLineInfo.getVendorPack());
    documentLine.setWhpkQty(poLineInfo.getWarehousePack());
    documentLine.setVendorPackCost(Double.valueOf(poLineInfo.getVendorPackCost()));
    documentLine.setWhpkSell(Double.valueOf(poLineInfo.getWarehousePackSell()));
    documentLine.setVnpkWgtQty(poLineInfo.getWeight());
    documentLine.setVnpkWgtUom(poLineInfo.getWeightUom());
    documentLine.setVnpkcbqty(poLineInfo.getCube());
    documentLine.setVnpkcbuomcd(poLineInfo.getCubeUom());
    documentLine.setDescription(poLineInfo.getDescription());
    documentLine.setSecondaryDescription(poLineInfo.getSecondaryDescription());
    documentLine.setPromoBuyInd(poLineInfo.getPromoBuyInd());
    documentLine.setTotalReceivedQty(poLineInfo.getTotalReceivedQty());
    documentLine.setMaxReceiveQty(maxReceiveQty);

    if (poLineInfo.getAdditionalInfo() != null) {
      ItemData itemData = poLineInfo.getAdditionalInfo();
      documentLine.setProfiledWarehouseArea(itemData.getProfiledWarehouseArea());
      documentLine.setWarehouseMinLifeRemainingToReceive(
          itemData.getWarehouseMinLifeRemainingToReceive());
    }

    return documentLine;
  }

  public static Boolean hasMoreUniqueItems(List<DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments
            .stream()
            .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
            .map(DeliveryDocumentLine::getItemNbr)
            .distinct()
            .count()
        > 1;
  }

  public static boolean isInstructionRequestSsccOrLpn(InstructionRequest instructionRequest) {
    if (isNull(instructionRequest.getReceivingType())) {
      return false;
    }
    return instructionRequest
            .getReceivingType()
            .equalsIgnoreCase(ReceivingType.SSCC.getReceivingType())
        || instructionRequest
            .getReceivingType()
            .equalsIgnoreCase(ReceivingConstants.WORK_STATION_SSCC)
        || instructionRequest
            .getReceivingType()
            .equalsIgnoreCase(ReceivingConstants.SCAN_TO_PRINT_SSCC)
        || instructionRequest
            .getReceivingType()
            .equalsIgnoreCase(ReceivingType.LPN.getReceivingType());
  }

  public static void logReceivedQuantityDetails(
      Instruction instruction4mDB,
      DeliveryDocumentLine deliveryDocumentLine,
      DeliveryDocument deliveryDocument,
      boolean isAsnReceivingEnabled) {
    if (isAsnReceivingEnabled) {
      LOG.info(
          "RECEIVING_BY_SCANNING_AND_RECEIVED_QUANTITY upc={}, delivery={}, SSCC={}, purchaseReferenceNumber={}, "
              + "purchaseReferenceLineNumber={}, "
              + "overageQtyLimit={}, "
              + "asnQuantity={}, "
              + "ti={}, "
              + "hi={}, "
              + "OpenQuantity={}, "
              + "poQuantity={}, "
              + "receivedQuantity={}, vendorId={},",
          instruction4mDB.getGtin(),
          instruction4mDB.getDeliveryNumber(),
          instruction4mDB.getSsccNumber(),
          instruction4mDB.getPurchaseReferenceNumber(),
          instruction4mDB.getPurchaseReferenceLineNumber(),
          deliveryDocumentLine.getOverageQtyLimit(),
          deliveryDocumentLine.getShippedQty(),
          deliveryDocumentLine.getPalletTie(),
          deliveryDocumentLine.getPalletHigh(),
          deliveryDocumentLine.getOpenQty(),
          deliveryDocumentLine.getMaxReceiveQty(),
          instruction4mDB.getReceivedQuantity(),
          deliveryDocument.getVendorNumber());
    }
  }

  public static void validateExpiryDate(Date expiryDate, Instruction instructionResponse)
      throws ReceivingException {

    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(instructionResponse.getDeliveryDocument(), DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    Integer warehouseMinLife = deliveryDocumentLine.getWarehouseMinLifeRemainingToReceive();
    Integer storeMinLife = deliveryDocumentLine.getStoreMinLifeRemaining();

    if (Objects.nonNull(expiryDate)) {
      if (Objects.nonNull(warehouseMinLife) && Objects.nonNull(storeMinLife)) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, (warehouseMinLife + storeMinLife));
        Date minExpiryDate = calendar.getTime();

        if (expiryDate.before(minExpiryDate)) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorCode(INVALID_EXPIRY_DATE_ERROR_CODE)
                  .errorMessage(INVALID_EXPIRY_DATE_ERROR_MSG)
                  .errorKey(ExceptionCodes.INVALID_EXPIRY_DATE_ERROR_CODE)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.BAD_REQUEST)
              .errorResponse(errorResponse)
              .build();
        }

      } else {
        if (Objects.isNull(warehouseMinLife)) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorCode(WAREHOUSE_MIN_LIFE_NULL_ERROR_CODE)
                  .errorMessage(WAREHOUSE_MIN_LIFE_NULL_ERROR_MSG)
                  .errorKey(ExceptionCodes.WAREHOUSE_MIN_LIFE_NULL_ERROR_CODE)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.BAD_REQUEST)
              .errorResponse(errorResponse)
              .build();

        } else if (Objects.isNull(storeMinLife)) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorCode(STORE_MIN_LIFE_NULL_ERROR_CODE)
                  .errorMessage(STORE_MIN_LIFE_NULL_ERROR_MSG)
                  .errorKey(ExceptionCodes.STORE_MIN_LIFE_NULL_ERROR_CODE)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.BAD_REQUEST)
              .errorResponse(errorResponse)
              .build();
        }
      }
    }
  }

  public static Instruction getCCOverageAlertInstruction(
      InstructionRequest instructionRequest, List<DeliveryDocument> deliveryDocuments) {
    Gson gson = new Gson();
    Instruction instruction = new Instruction();
    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));
    instruction.setInstructionCode(ReportingConstants.CC_OVERAGE_PALLET);
    instruction.setInstructionMsg(ReportingConstants.CC_OVERAGE_PALLET);
    instruction.setGtin(instructionRequest.getUpcNumber());
    instruction.setDeliveryDocument(gson.toJson(deliveryDocuments.get(0)));

    return instruction;
  }
  /**
   * Set the field <code>receivingUnit</code> in <code>FdeCreateContainerRequest</code> irrespective
   * of whether it is set or not The field is in <code>
   * fdeCreateContainerRequest.container.contents.[*]</code>
   *
   * @see FdeCreateContainerRequest
   * @param fdeCreateContainerRequest
   * @param receivingUnit
   */
  public static void setFdeCreateContainerRequestReceivingUnit(
      @NotNull FdeCreateContainerRequest fdeCreateContainerRequest, String receivingUnit) {
    if (!isNull(fdeCreateContainerRequest)) {
      fdeCreateContainerRequest
          .getContainer()
          .getContents()
          .forEach(content -> content.setReceivingUnit(receivingUnit));
    }
  }

  // Regulated Item Validation
  public Boolean isVendorComplianceRequired(DeliveryDocumentLine deliveryDocumentLine) {
    boolean isVendorComplianceRequired = false;
    boolean isLithiumIonVerificationRequired = false;
    boolean isLimitedQtyVerificationRequired = false;
    boolean isLithiumAndLimitedQtyVerificationRequired =
        lithiumIonLimitedQtyRule.validateRule(deliveryDocumentLine);
    if (isLithiumAndLimitedQtyVerificationRequired) {
      isLithiumIonVerificationRequired = true;
      isLimitedQtyVerificationRequired = true;
    } else {
      isLithiumIonVerificationRequired = lithiumIonRule.validateRule(deliveryDocumentLine);
      isLimitedQtyVerificationRequired = limitedQtyRule.validateRule(deliveryDocumentLine);
    }
    if (isLithiumIonVerificationRequired || isLimitedQtyVerificationRequired) {
      LOG.info(
          "The PO:{} and POL:{} contains either lithium or limitedQty item",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      isVendorComplianceRequired = true;
      deliveryDocumentLine.setLimitedQtyVerificationRequired(isLimitedQtyVerificationRequired);
      deliveryDocumentLine.setLithiumIonVerificationRequired(isLithiumIonVerificationRequired);
      if (deliveryDocumentLine.isLithiumIonVerificationRequired()) {
        // Deriving labelTypeCode from pkgInstruction value
        String labelTypeCode =
            getLabelTypeCode(
                deliveryDocumentLine.getTransportationModes().get(0).getPkgInstruction());
        LOG.info(
            "PO:{} and POL:{} has labelTypeCode:{}",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            labelTypeCode);
        deliveryDocumentLine.setLabelTypeCode(labelTypeCode);
      }
    }
    return isVendorComplianceRequired;
  }

  public String getLabelTypeCode(List<String> pkgInstructions) {
    String labelTypeCode = null;
    if (!CollectionUtils.isEmpty(pkgInstructions)) {
      for (String pkgInstruction : pkgInstructions) {
        switch (pkgInstruction) {
          case ReceivingConstants.PKG_INSTRUCTION_CODE_965:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3480;
            break;

          case ReceivingConstants.PKG_INSTRUCTION_CODE_966:
          case ReceivingConstants.PKG_INSTRUCTION_CODE_967:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3481;
            break;

          case ReceivingConstants.PKG_INSTRUCTION_CODE_968:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3090;
            break;

          case ReceivingConstants.PKG_INSTRUCTION_CODE_969:
          case ReceivingConstants.PKG_INSTRUCTION_CODE_970:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3091;
            break;

          default:
            break;
        }
      }
    }
    return labelTypeCode;
  }

  /**
   * Checks External ItemConfigService if given item(s) are Atlas Converted Items or not. If
   * converted then the item at line level is marked as true for attribute `AtlasConvertedItem` else
   * it will remain as false
   *
   * @param deliveryDocuments
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void validateAtlasConvertedItems(
      List<DeliveryDocument> deliveryDocuments, HttpHeaders httpHeaders) throws ReceivingException {

    Set<Long> itemConfigRequest =
        deliveryDocuments
            .stream()
            .flatMap(doc -> doc.getDeliveryDocumentLines().stream())
            .map(DeliveryDocumentLine::getItemNbr)
            .collect(Collectors.toSet());

    try {
      Set<String> atlasConvertedItems =
          itemConfigApiClient
              .searchAtlasConvertedItems(itemConfigRequest, httpHeaders)
              .parallelStream()
              .map(ItemConfigDetails::getItem)
              .collect(Collectors.toSet());
      deliveryDocuments.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      String itemNumber = valueOf(deliveryDocumentLine.getItemNbr());
                      ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
                      if (isNull(itemData)) {
                        itemData = new ItemData();
                      }
                      if (!isEmpty(atlasConvertedItems)
                          && atlasConvertedItems.contains(itemNumber)) {
                        itemData.setAtlasConvertedItem(true);
                        LOG.info("Item {} is Atlas converted item", itemNumber);
                      }
                      deliveryDocumentLine.setAdditionalInfo(itemData);
                    });
          });
    } catch (ItemConfigRestApiClientException e) {
      LOG.error(
          "Error when searching atlas converted items errorCode = {} and error message = {} ",
          e.getHttpStatus(),
          ExceptionUtils.getMessage(e));
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode());
    }
  }

  public void updateDeliveryDocForAtlasConvertedItems(
      List<DeliveryDocument> gdmDeliveryDocumentList,
      HttpHeaders httpHeaders,
      boolean isItemConfigServiceEnabled)
      throws ReceivingException {
    if (isItemConfigServiceEnabled) {
      validateAtlasConvertedItems(gdmDeliveryDocumentList, httpHeaders);
    } else {
      // populate isAtlasConvertedItem as false in additionalInfo by default
      gdmDeliveryDocumentList.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      if (isNull(deliveryDocumentLine.getAdditionalInfo())) {
                        deliveryDocumentLine.setAdditionalInfo(new ItemData());
                      }
                    });
          });
    }
  }

  private static boolean isItemXBlocked(DeliveryDocumentLine deliveryDocumentLine) {
    String handlingCode = deliveryDocumentLine.getHandlingCode();
    return Arrays.asList(ReceivingConstants.X_BLOCK_ITEM_HANDLING_CODES).contains(handlingCode);
  }

  public static void validateItemXBlocked(DeliveryDocumentLine deliveryDocumentLine) {
    if (isItemXBlocked(deliveryDocumentLine)) {
      LOG.error(
          "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_X_BLOCKED_ERROR,
          String.format(
              ReceivingConstants.X_BLOCK_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
  }

  public static void checkForXBlockedItems(List<DeliveryDocument> deliveryDocuments) {
    List<DeliveryDocumentLine> xBlockedLines =
        deliveryDocuments
            .stream()
            .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
            .filter(InstructionUtils::isItemXBlocked)
            .collect(Collectors.toList());
    if (!xBlockedLines.isEmpty()) {
      LOG.error(
          "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
          xBlockedLines.get(0).getItemNbr(),
          xBlockedLines.get(0).getHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_X_BLOCKED_ERROR,
          String.format(
              ReceivingConstants.X_BLOCK_ITEM_ERROR_MSG, xBlockedLines.get(0).getItemNbr()),
          String.valueOf(xBlockedLines.get(0).getItemNbr()));
    }
  }

  /**
   * This Method used to check line level fbq for import non SSTK freights
   *
   * <p>if deliveryDocument.importInd = true, and in lines refType = SSTKU, its valid if
   * deliveryDocument.importInd = true, and refType = CROSSMU, and non null / non zero fbq on line,
   * its valid if deliveryDocument.importInd = false, then all lines of this are valid, and document
   * itself is valid if deliveryDocument.importInd = null, then all lines of this are valid, and
   * document itself is valid
   *
   * @param deliveryDocuments
   */
  public static void filterValidDeliveryDocumentsWithLineLevelFbq(
      DeliveryDocument deliveryDocuments) {
    if (Objects.nonNull(deliveryDocuments.getImportInd())
        && Boolean.TRUE.equals(deliveryDocuments.getImportInd())) {
      DeliveryDocumentLine lines = deliveryDocuments.getDeliveryDocumentLines().get(0);
      String effectiveChannelMethod =
          InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
              lines.getPurchaseRefType(), lines.getActiveChannelMethods());
      if (PurchaseReferenceType.CROSSMU.name().equals(effectiveChannelMethod)
          && (Objects.isNull(lines.getFreightBillQty()) || lines.getFreightBillQty() == 0)) {
        LOG.error(
            "Line level FBQ is not present for the Delivery {} PO {} PO Line {}",
            deliveryDocuments.getDeliveryNumber(),
            deliveryDocuments.getPurchaseReferenceNumber(),
            deliveryDocuments.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber());
        throw new ReceivingBadDataException(
            ExceptionCodes.LINE_LEVEL_FBQ_MISSING_ERROR,
            ReceivingConstants.LINE_LEVEL_FBQ_CHECK_ERROR_MESSAGE,
            String.valueOf(deliveryDocuments.getDeliveryNumber()),
            deliveryDocuments.getPurchaseReferenceNumber(),
            String.valueOf(
                deliveryDocuments
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getPurchaseReferenceLineNumber()));
      }
    }
  }

  /**
   * * This function validates for hazmat item with below condition:
   *
   * <ul>
   *   <li>transportation_mode = 1
   *   <li>Hazmat Class != ORM-D
   *   <li>reg_code="UN"
   *   <li>dot_nbr is not null
   * </ul>
   *
   * @param deliveryDocumentLine
   * @return true - If it is hazmat item false - otherwise
   */
  public static Boolean isHazmatItem(DeliveryDocumentLine deliveryDocumentLine) {
    Boolean isItemTransportationModeValidatedForHazmat =
        isItemTransportationModeValidatedForHazmat(deliveryDocumentLine.getTransportationModes());
    LOG.info(
        "Hazmat Information retrieved from Receiving using RDS Validations for Item:[{}], HazmatValidation:[{}]",
        deliveryDocumentLine.getItemNbr(),
        isItemTransportationModeValidatedForHazmat);
    return isItemTransportationModeValidatedForHazmat;
  }

  public static Boolean isItemTransportationModeValidatedForHazmat(
      List<TransportationModes> transportationModes) {
    if (!CollectionUtils.isEmpty(transportationModes)) {
      List<TransportationModes> groundTransportationModesFiltered =
          transportationModes
              .stream()
              .filter(
                  transportationMode ->
                      transportationMode.getMode().getCode() == HAZMAT_ITEM_GROUND_TRANSPORTATION)
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(groundTransportationModesFiltered)) {
        TransportationModes transportationMode = groundTransportationModesFiltered.get(0);
        if (Objects.nonNull(transportationMode.getDotHazardousClass())
            && StringUtils.isNotBlank(transportationMode.getDotHazardousClass().getCode())
            && !transportationMode
                .getDotHazardousClass()
                .getCode()
                .equals(ReceivingConstants.HAZMAT_ITEM_OTHER_REGULATED_MATERIAL)
            && StringUtils.isNotBlank(transportationMode.getDotRegionCode())
            && transportationMode
                .getDotRegionCode()
                .equals(ReceivingConstants.HAZMAT_ITEM_REGION_CODE_UN)
            && StringUtils.isNotBlank(transportationMode.getDotIdNbr())) return true;
      }
    }
    return false;
  }

  /**
   * Check if the instruction request is for the Labels Missing case in Manual Receiving, or
   * Overflow Receiving flow. <code>isLabelsMissingFlow</code>: this will be true when vendor
   * compliance is required, but verification will not be done as user does manual labelling. In
   * labels missing flow, it will be true in 2nd, 3rd API calls to create instruction endpoint.
   *
   * <ul>
   *   <li>regulatedItemType in instruction request should not be set
   *   <li>vendorComplianceValidated in instructionRequest should not be set
   *   <li>isVendorComplianceRequired(deliveryDocumentLine) is true (also sets verification required
   *       flags)
   * </ul>
   *
   * @param instructionRequest
   * @return
   */
  public boolean isLabelsMissingFlowManualOVFConv(InstructionRequest instructionRequest) {
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    if (!deliveryDocuments.isEmpty()
        && Objects.nonNull(deliveryDocuments.get(0).getDeliveryDocumentLines())
        && !deliveryDocuments.get(0).getDeliveryDocumentLines().isEmpty()
        && Objects.nonNull(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0))
        && isTransportationModesPresent(deliveryDocuments)) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
      // this step sets lithium-ion/ limited qty verification required flags as a side effect
      boolean isLabelsMissingFlow =
          isVendorComplianceRequired(deliveryDocumentLine)
              && Objects.isNull(instructionRequest.getRegulatedItemType())
              && Boolean.FALSE.equals(instructionRequest.isVendorComplianceValidated());

      return isLabelsMissingFlow;
    }
    return false;
  }

  /**
   * @param instructionRequest calculates flag <code>isGetAllocationsRequest</code>: this is used to
   *     distinguish between 2nd and 3rd (+ subsequent) API requests. 3rd request is done when user
   *     sees enter-qty screen, and provides quantity to be received (in cases). In this case client
   *     is supposed to set quantity field in deliveryDocument to 1, and that is checked to get this
   *     flag. In case this is true, we should directly enter createManualInstruction, and send back
   *     the instruction and labels.
   * @return
   */
  public boolean isLabelsMissingFlowManualOVFConvGetAllocationsRequest(
      InstructionRequest instructionRequest) {
    // TODO: change this name to make sense
    return instructionRequest
        .getDeliveryDocuments()
        .stream()
        .anyMatch(deliveryDocument -> new Integer(1).equals(deliveryDocument.getQuantity()));
  }

  public static boolean isTransportationModesPresent(List<DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments
        .stream()
        .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
        .anyMatch(
            deliveryDocumentLine ->
                !CollectionUtils.isEmpty(deliveryDocumentLine.getTransportationModes()));
  }

  public static Optional<InstructionError> getFdeInstructionError(
      ReceivingException receivingException) {
    // TODO: instead of parsing on all values of the enum, add an implementation of ErrorResponse
    // like FdeErrorResponse, which has a field called instructionError which can be populated while
    // building the error response. ReceivingException stores this errorResponse as a field, so we
    // can access the instructionError originally stored using this
    InstructionError instructionError = null;
    if (Objects.nonNull(receivingException.getErrorResponse())
        && Objects.nonNull(receivingException.getErrorResponse().getErrorMessage())) {
      String errorMessage = receivingException.getErrorResponse().getErrorMessage().toString();
      if (errorMessage.equalsIgnoreCase(InstructionError.NO_ALLOCATION.getErrorMessage()))
        instructionError = InstructionError.NO_ALLOCATION;
      if (errorMessage.equalsIgnoreCase(InstructionError.CHANNEL_FLIP.getErrorMessage()))
        instructionError = InstructionError.CHANNEL_FLIP;
      if (errorMessage.equalsIgnoreCase(InstructionError.INVALID_ALLOCATION.getErrorMessage()))
        instructionError = InstructionError.INVALID_ALLOCATION;
      if (errorMessage.equalsIgnoreCase(
          InstructionError.PBYL_DOCKTAG_NOT_PRINTED.getErrorMessage()))
        instructionError = InstructionError.PBYL_DOCKTAG_NOT_PRINTED;
    }
    return Optional.ofNullable(instructionError);
  }

  public static Instruction acknowledgePendingInstruction(
      InstructionRequest instructionRequest,
      boolean isCountryCodeACKEnabled,
      boolean originCountryCodeAcknowledged,
      boolean originCountryCodeAcknowledgedCoditionalACk,
      boolean ispackAckEnabled,
      boolean packAcknowledged) {
    Instruction instruction = new Instruction();
    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));
    instruction.setGtin(instructionRequest.getUpcNumber());
    if (isCountryCodeACKEnabled
        && !originCountryCodeAcknowledged
        && !originCountryCodeAcknowledgedCoditionalACk
        && ispackAckEnabled
        && !packAcknowledged) {
      instruction.setInstructionCode(ReportingConstants.ITEM_COO_AND_PACK_SIZE_VALIDATION_PENDING);
      instruction.setInstructionMsg(ReportingConstants.ITEM_COO_AND_PACK_SIZE_VALIDATION_MESSAGE);
      return instruction;
    } else if (isCountryCodeACKEnabled
        && !originCountryCodeAcknowledged
        && !originCountryCodeAcknowledgedCoditionalACk) {
      instruction.setInstructionCode(ReportingConstants.OCC_ACK_PENDING);
      instruction.setInstructionMsg(ReportingConstants.OCC_ACK_MESSAGE);
      return instruction;
    } else if (ispackAckEnabled && !packAcknowledged) {
      instruction.setInstructionCode(ReportingConstants.PACK_ACK_PENDING);
      instruction.setInstructionMsg(ReportingConstants.PACK_ACK_MESSAGE);
      return instruction;
    }
    return null;
  }

  /**
   * This method checks weather any problem tag exists in ANSWERED_READY_TO_RECEIVE state for the
   * scanned/selected upc
   *
   * @param upcNumber
   * @param deliveryDocument
   * @param problemTagTypesList
   * @throws ReceivingBadDataException
   */
  public static void checkIfProblemTagPresent(
      String upcNumber, DeliveryDocument deliveryDocument, List<String> problemTagTypesList)
      throws ReceivingBadDataException {
    List<DeliveryDocumentLine> deliveryDocumentLines = deliveryDocument.getDeliveryDocumentLines();
    deliveryDocumentLines.forEach(
        deliveryDocumentLine -> {
          List<ProblemData> problemDataList = deliveryDocumentLine.getProblems();
          if (!CollectionUtils.isEmpty(problemDataList)) {
            Optional<ProblemData> problemData =
                problemDataList
                    .stream()
                    .filter(tag -> problemTagTypesList.contains(tag.getType()))
                    .filter(tag -> ANSWERED_AND_READY_TO_RECEIVE.toString().equals(tag.getStatus()))
                    .findAny();
            if (problemData.isPresent()) {
              throw new ReceivingBadDataException(
                  ExceptionCodes.PROBLEM_TAG_FOUND_FOR_SCANNED_UPC,
                  String.format(
                      ReceivingException.PROBLEM_TAG_FOUND_FOR_SCANNED_UPC_ERROR_CODE, upcNumber));
            }
          }
        });
  }

  public boolean checkIfNewInstructionCanBeCreated(
      DeliveryDocumentLine deliveryDocumentLine,
      DeliveryDocument deliveryDocument,
      ImmutablePair<Long, Long> openQtyReceivedQtyPair) {
    long deliveryNumber = deliveryDocument.getDeliveryNumber();
    String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
    int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();
    OpenQtyResult openQtyResult;
    OpenQtyCalculator qtyCalculator =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.OPEN_QTY_CALCULATOR,
            ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR,
            OpenQtyCalculator.class);
    openQtyResult =
        qtyCalculator.calculate(
            deliveryDocument.getDeliveryNumber(), deliveryDocument, deliveryDocumentLine);
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedStart(System.currentTimeMillis());
    int totalReceivedQty = Math.toIntExact(openQtyReceivedQtyPair.getRight());
    int maxReceiveQty;
    Long pendingInstructionsCumulativeProjectedReceivedQty;
    Integer fbqpendingInstructionsCumulativeProjectedReceivedQty;
    if (Objects.nonNull(openQtyResult.getFlowType())
        && OpenQtyFlowType.FBQ.equals(openQtyResult.getFlowType())) {
      maxReceiveQty =
          ReceivingUtils.conversionToEaches(
              Math.toIntExact(openQtyResult.getMaxReceiveQty()),
              ReceivingConstants.Uom.VNPK,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
      fbqpendingInstructionsCumulativeProjectedReceivedQty =
          (Optional.ofNullable(
                  instructionRepository
                      .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                          deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber))
              .orElse(0));
      pendingInstructionsCumulativeProjectedReceivedQty =
          Long.valueOf(
              ReceivingUtils.conversionToEaches(
                  fbqpendingInstructionsCumulativeProjectedReceivedQty,
                  ReceivingConstants.Uom.VNPK,
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack()));
    } else {
      maxReceiveQty = Math.toIntExact(openQtyResult.getMaxReceiveQty());
      pendingInstructionsCumulativeProjectedReceivedQty =
          instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
              purchaseReferenceNumber, purchaseReferenceLineNumber);
    }
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedEnd(System.currentTimeMillis());
    boolean isPendingInstructionExist =
        pendingInstructionsCumulativeProjectedReceivedQty != null
            && pendingInstructionsCumulativeProjectedReceivedQty > 0;
    if (isPendingInstructionExist
        && (pendingInstructionsCumulativeProjectedReceivedQty + totalReceivedQty)
            >= maxReceiveQty) {
      return false;
    } else return true;
  }
}
