package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Item;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class NimRdsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NimRdsService.class);

  @Autowired private NimRDSRestApiClient rdsRestApiClient;
  @Autowired private ReceiptService receiptService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;

  /**
   * Fetch received quantity from RDS for single or multiple PO and all of it's lines
   *
   * @param deliveryDocuments
   * @param httpHeaders
   * @return ReceivedQuantityResponseFromRDS
   */
  @TimeTracing(
      component = AppComponent.RDS,
      type = Type.REST,
      executionFlow = "Fetch-receivedQty-for-single-or-multi-PO")
  public ReceivedQuantityResponseFromRDS getReceivedQtyByDeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments, HttpHeaders httpHeaders, String upcNumber) {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> combinedReceivedQtyByPoAndPoLineMapInVnpk = new HashMap<>();
    Map<String, Long> receivedQtyByPoAndPoLineMapInWhpk;
    List<OrderLines> orderLinesList = new ArrayList<>();
    Boolean isAllAtlasConvertedItems = false;
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Integer atlasReceivedQtyInVnpk = null;

    // force received qty check to be done only in atlas receipts table, do not refer RDS
    boolean isRdsReceivingBlocked =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RDS_RECEIVING_BLOCKED,
            false);
    if (isRdsReceivingBlocked) {
      // override all items as atlas item
      deliveryDocuments.forEach(
          deliveryDocument ->
              deliveryDocument
                  .getDeliveryDocumentLines()
                  .forEach(
                      deliveryDocumentLine ->
                          deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true)));
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false)) {
      Pair<Boolean, Map<String, Long>> atlasConvertedItemsMap =
          getAtlasReceivedQtyMapByPoPoLine(deliveryDocuments);
      isAllAtlasConvertedItems = atlasConvertedItemsMap.getKey();
      combinedReceivedQtyByPoAndPoLineMapInVnpk.putAll(atlasConvertedItemsMap.getValue());
    }
    boolean inProgressDaPOsReceivedQtyValidationInLegacyRequired =
        isInProgressPOsValidationInLegacyRequired(deliveryDocuments, upcNumber);

    if (Boolean.TRUE.equals(!isAllAtlasConvertedItems)
        || inProgressDaPOsReceivedQtyValidationInLegacyRequired) {
      TenantContext.get()
          .setNimRdsReceivedQtyByDeliveryDocumentsCallStart(System.currentTimeMillis());
      Map<String, Object> httpHeadersMap =
          ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

      RdsReceiptsRequest rdsReceiptsRequest = new RdsReceiptsRequest();
      deliveryDocuments.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      if (!deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()
                          || inProgressDaPOsReceivedQtyValidationInLegacyRequired) {
                        OrderLines orderLine = new OrderLines();
                        orderLine.setPoNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
                        orderLine.setPoLine(deliveryDocumentLine.getPurchaseReferenceLineNumber());
                        orderLinesList.add(orderLine);
                      }
                    });
          });

      rdsReceiptsRequest.setOrderLines(orderLinesList);
      try {
        rdsReceiptsResponse = rdsRestApiClient.quantityReceived(rdsReceiptsRequest, httpHeadersMap);
      } catch (ReceivingBadDataException e) {
        if (!inProgressDaPOsReceivedQtyValidationInLegacyRequired) {
          throw e;
        }
      }
      if (inProgressDaPOsReceivedQtyValidationInLegacyRequired) {
        receivedQuantityResponseFromRDS =
            ReceivingUtils.handleRdsResponseForInProgressPOs(rdsReceiptsResponse);
      } else {
        receivedQuantityResponseFromRDS = ReceivingUtils.handleRDSResponse(rdsReceiptsResponse);
      }

      receivedQtyByPoAndPoLineMapInWhpk =
          receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();
      for (DeliveryDocument deliveryDocument : deliveryDocuments) {
        for (DeliveryDocumentLine deliveryDocumentLine :
            deliveryDocument.getDeliveryDocumentLines()) {
          String key =
              deliveryDocumentLine.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + deliveryDocumentLine.getPurchaseReferenceLineNumber();
          if (combinedReceivedQtyByPoAndPoLineMapInVnpk.containsKey(key)) {
            atlasReceivedQtyInVnpk = combinedReceivedQtyByPoAndPoLineMapInVnpk.get(key).intValue();
          }
          if (receivedQtyByPoAndPoLineMapInWhpk.containsKey(key)) {
            int totalReceivedQtyInWhpk = receivedQtyByPoAndPoLineMapInWhpk.get(key).intValue();
            if (totalReceivedQtyInWhpk > 0) {
              Integer totalReceivedQtyInVnpk =
                  ReceivingUtils.conversionToVendorPack(
                      totalReceivedQtyInWhpk,
                      ReceivingConstants.Uom.WHPK,
                      deliveryDocumentLine.getVendorPack(),
                      deliveryDocumentLine.getWarehousePack());
              LOGGER.info(
                  "Received quantity for PO: {} and POL: {} from RDS in WHPK: {} and VNPK:{}",
                  deliveryDocumentLine.getPurchaseReferenceNumber(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                  totalReceivedQtyInWhpk,
                  totalReceivedQtyInVnpk);
              totalReceivedQtyInVnpk =
                  Objects.nonNull(atlasReceivedQtyInVnpk)
                      ? (atlasReceivedQtyInVnpk + totalReceivedQtyInVnpk)
                      : totalReceivedQtyInVnpk;
              combinedReceivedQtyByPoAndPoLineMapInVnpk.put(
                  key, Long.valueOf(totalReceivedQtyInVnpk));
              if (inProgressDaPOsReceivedQtyValidationInLegacyRequired) {
                if (rdcInstructionUtils.isDADocument(deliveryDocument)) {
                  // Overriding flag to false as the item is received in legacy for DA PO
                  LOGGER.info(
                      "Override atlas converted item as false for itemNumber:{}",
                      deliveryDocumentLine.getItemNbr());
                  deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
                }
              }
            } else {
              int totalReceivedQtyInVnpk =
                  Objects.nonNull(atlasReceivedQtyInVnpk)
                      ? atlasReceivedQtyInVnpk
                      : totalReceivedQtyInWhpk;
              combinedReceivedQtyByPoAndPoLineMapInVnpk.put(key, (long) totalReceivedQtyInVnpk);
            }
          }
        }
      }
      TenantContext.get()
          .setNimRdsReceivedQtyByDeliveryDocumentsCallEnd(System.currentTimeMillis());
      LOGGER.info(
          "LatencyCheck ReceivedQtyByDeliveryDocuments at ts={} time in totalTimeTakenforReceivedQtyByDeliveryDocuments={}, and correlationId={}",
          TenantContext.get().getNimRdsReceivedQtyByDeliveryDocumentsCallStart(),
          ReceivingUtils.getTimeDifferenceInMillis(
              TenantContext.get().getNimRdsReceivedQtyByDeliveryDocumentsCallStart(),
              TenantContext.get().getNimRdsReceivedQtyByDeliveryDocumentsCallEnd()),
          TenantContext.getCorrelationId());
    } else {
      receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    }

    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(
        combinedReceivedQtyByPoAndPoLineMapInVnpk);

    if (Boolean.TRUE.equals(!isAllAtlasConvertedItems)
        && MapUtils.isEmpty(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine())) {
      if (MapUtils.isEmpty(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine())) {
        Long deliveryNumber = deliveryDocuments.get(0).getDeliveryNumber();
        LOGGER.info(
            "No active line is available to receive for the scanned UPC: {} and delivery: {}",
            upcNumber,
            deliveryNumber);
        throw new ReceivingBadDataException(
            ExceptionCodes.NO_ACTIVE_PO_LINES_TO_RECEIVE,
            String.format(
                ReceivingException.NO_ACTIVE_PO_LINES_TO_RECEIVE, deliveryNumber, upcNumber),
            upcNumber,
            deliveryNumber.toString());
      } else {
        LOGGER.error(
            "Error response received from RDS for for PO:{}, POL:{}",
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber(),
            deliveryDocuments
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber());
        String key =
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getPurchaseReferenceLineNumber();
        Map<String, String> errorMapFromRds =
            receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine();
        String errorMessage = errorMapFromRds.get(key);
        throw new ReceivingBadDataException(
            ExceptionCodes.GET_RECEIPTS_ERROR_RESPONSE_IN_RDS,
            String.format(ReceivingConstants.GET_RECEIPTS_ERROR_RESPONSE_IN_RDS, errorMessage),
            errorMessage);
      }
    }

    return receivedQuantityResponseFromRDS;
  }

  private Pair<Boolean, Map<String, Long>> getAtlasReceivedQtyMapByPoPoLine(
      List<DeliveryDocument> deliveryDocuments) {
    Map<String, Long> atlasReceivedQtyByPoAndPoLineMap = new HashMap<>();
    List<Integer> atlasConvertedItems = new ArrayList<>();
    List<Integer> nonAtlasConvertedItems = new ArrayList<>();
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    deliveryDocuments.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    if (deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
                      poNumberList.add(deliveryDocumentLine.getPurchaseReferenceNumber());
                      poLineNumberSet.add(deliveryDocumentLine.getPurchaseReferenceLineNumber());
                      atlasConvertedItems.add(deliveryDocumentLine.getItemNbr().intValue());
                    } else {
                      nonAtlasConvertedItems.add(deliveryDocumentLine.getItemNbr().intValue());
                    }
                  });
        });

    if (CollectionUtils.isNotEmpty(poNumberList) && CollectionUtils.isNotEmpty(poLineNumberSet)) {
      LOGGER.info("Get receipts quantity for atlas converted items {}", atlasConvertedItems);
      List<ReceiptSummaryQtyByPoAndPoLineResponse> qtyByPoAndPoLineList =
          receiptService.receivedQtyInVNPKByPoAndPoLineList(poNumberList, poLineNumberSet);
      for (ReceiptSummaryQtyByPoAndPoLineResponse qtyByPoAndPoLine : qtyByPoAndPoLineList) {
        String key =
            qtyByPoAndPoLine.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + qtyByPoAndPoLine.getPurchaseReferenceLineNumber();
        atlasReceivedQtyByPoAndPoLineMap.put(key, qtyByPoAndPoLine.getReceivedQty());
      }

      deliveryDocuments.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      String poPoLineKey =
                          deliveryDocumentLine.getPurchaseReferenceNumber()
                              + ReceivingConstants.DELIM_DASH
                              + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                      if (!atlasReceivedQtyByPoAndPoLineMap.containsKey(poPoLineKey)
                          && deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
                        atlasReceivedQtyByPoAndPoLineMap.put(poPoLineKey, 0L);
                      }
                    });
          });
    }

    return new Pair<>(
        CollectionUtils.isEmpty(nonAtlasConvertedItems), atlasReceivedQtyByPoAndPoLineMap);
  }

  /**
   * Fetch received quantity from RDS for delivery document line
   *
   * @param documentLine
   * @param httpHeaders
   * @return ReceivedQuantityResponseFromRDS
   */
  @TimeTracing(
      component = AppComponent.RDS,
      type = Type.REST,
      executionFlow = "Fetch-receivedQty-for-delivery-documentLine")
  public ReceivedQuantityResponseFromRDS getReceivedQtyByDeliveryDocumentLine(
      DeliveryDocumentLine documentLine, HttpHeaders httpHeaders) {
    TenantContext.get()
        .setNimRdsReceivedQtyByDeliveryDocumentLineCallStart(System.currentTimeMillis());
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    RdsReceiptsRequest rdsReceiptsRequest = new RdsReceiptsRequest();
    List<OrderLines> orderLinesList = new ArrayList<>();
    OrderLines orderLine = new OrderLines();
    orderLine.setPoNumber(documentLine.getPurchaseReferenceNumber());
    orderLine.setPoLine(documentLine.getPurchaseReferenceLineNumber());
    orderLinesList.add(orderLine);
    rdsReceiptsRequest.setOrderLines(orderLinesList);

    RdsReceiptsResponse rdsReceiptsResponse =
        rdsRestApiClient.quantityReceived(rdsReceiptsRequest, httpHeadersMap);

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        ReceivingUtils.handleRDSResponse(rdsReceiptsResponse);
    TenantContext.get()
        .setNimRdsReceivedQtyByDeliveryDocumentLineCallEnd(System.currentTimeMillis());
    LOGGER.info(
        "LatencyCheck ReceivedQtyByDeliveryDocuments at ts={} time in totalTimeTakenforReceivedQtyByDeliveryDocumentLine={}, and correlationId={}",
        TenantContext.get().getNimRdsReceivedQtyByDeliveryDocumentLineCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getNimRdsReceivedQtyByDeliveryDocumentLineCallStart(),
            TenantContext.get().getNimRdsReceivedQtyByDeliveryDocumentLineCallEnd()),
        TenantContext.getCorrelationId());

    return receivedQuantityResponseFromRDS;
  }

  /**
   * Fetch item details from RDS for given item numbers when IQS integration is disabled and fetch
   * slot details from RDS when smart slotting integration is disabled
   *
   * @param deliveryDocumentList
   * @param httpHeaders
   */
  @TimeTracing(component = AppComponent.RDS, type = Type.REST, executionFlow = "Fetch-item-details")
  public void updateAdditionalItemDetails(
      List<DeliveryDocument> deliveryDocumentList, HttpHeaders httpHeaders) {
    TenantContext.get().setCreateInstrUpdateItemDetailsNimRdsCallStart(System.currentTimeMillis());
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    Set<String> items = new HashSet<>();
    deliveryDocumentList
        .stream()
        .forEach(
            document -> {
              document
                  .getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      documentLine -> {
                        items.add(String.valueOf(documentLine.getItemNbr()));
                      });
            });

    List<String> itemNumbers = new ArrayList<>(items);
    ItemDetailsResponseBody itemDetailsResponse =
        rdsRestApiClient.itemDetails(itemNumbers, httpHeadersMap);
    if (itemDetailsResponse != null) {
      if (CollectionUtils.isNotEmpty(itemDetailsResponse.getFound())) {
        Map<Long, Item> itemDetailMap =
            itemDetailsResponse
                .getFound()
                .stream()
                .collect(Collectors.toMap(Item::getItem_nbr, Function.identity()));

        deliveryDocumentList
            .stream()
            .forEach(
                document -> {
                  document
                      .getDeliveryDocumentLines()
                      .stream()
                      .forEach(
                          deliveryDocumentLine -> {
                            Item itemData = itemDetailMap.get(deliveryDocumentLine.getItemNbr());
                            ItemData additionalItemInfo = deliveryDocumentLine.getAdditionalInfo();
                            if (additionalItemInfo == null) {
                              additionalItemInfo = new ItemData();
                            }
                            /* When smart slotting integration is disabled get prime Slot details from
                             * RDS. For freight identification scenario, we are not displaying slot information
                             * to user, so we need to request slot information from RDS only when scanned UPC matches
                             * with only one item on either single or multiple PO or PO lines.
                             */
                            if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
                                    TenantContext.getFacilityNum().toString(),
                                    RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
                                    false)
                                && itemNumbers.size() == 1) {
                              validatePrimeSlotInfoExists(itemData, deliveryDocumentLine);
                              if (Objects.nonNull(itemData.getPrimeSlot())) {
                                additionalItemInfo.setPrimeSlot(itemData.getPrimeSlot());
                              }
                              additionalItemInfo.setPrimeSlotSize(itemData.getPrimeSlotSize());
                            }
                            // when iqs integration is disabled get item details from RDS
                            if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
                                TenantContext.getFacilityNum().toString(),
                                ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
                                false)) {
                              additionalItemInfo.setHandlingCode(itemData.getHandlingCode());
                              additionalItemInfo.setPackTypeCode(
                                  RdcUtils.getPackTypeCodeByBreakPackRatio(deliveryDocumentLine));
                              additionalItemInfo.setSymEligibleIndicator(
                                  itemData.getSym_eligible_ind());
                              int hazardousCode = itemData.getIs_hazardous();
                              additionalItemInfo.setIsHazardous(hazardousCode);
                              Boolean isHazardous = hazardousCode == HAZMAT_ITEM;
                              deliveryDocumentLine.setIsHazmat(isHazardous);

                              // Set Pallet Ti-Hi to 100 if they are coming as zero from RDS
                              Integer palletTi = itemData.getTi() == 0 ? 100 : itemData.getTi();
                              Integer palletHi = itemData.getHi() == 0 ? 100 : itemData.getHi();
                              additionalItemInfo.setPalletTi(palletTi);
                              additionalItemInfo.setPalletHi(palletHi);
                              // default ti x hi pop up message needed only for SSTK
                              if (rdcInstructionUtils.isSSTKDocument(document)) {
                                if (palletTi == 100 && palletHi == 100) {
                                  additionalItemInfo.setIsDefaultTiHiUsed(true);
                                }
                              }
                              String itemHandlingMethod =
                                  PACKTYPE_HANDLINGCODE_MAP.get(
                                      StringUtils.join(
                                          additionalItemInfo.getPackTypeCode(),
                                          additionalItemInfo.getHandlingCode()));
                              additionalItemInfo.setItemHandlingMethod(
                                  Objects.isNull(itemHandlingMethod)
                                      ? INVALID_HANDLING_METHOD_OR_PACK_TYPE
                                      : itemHandlingMethod);
                              String itemPackAndHandlingCode =
                                  StringUtils.join(
                                      additionalItemInfo.getPackTypeCode(),
                                      additionalItemInfo.getHandlingCode());
                              additionalItemInfo.setItemPackAndHandlingCode(
                                  itemPackAndHandlingCode);
                              deliveryDocumentLine.setAdditionalInfo(additionalItemInfo);
                            }
                          });
                });
      } else if (itemNumbers.size() == 1
          && CollectionUtils.isNotEmpty(itemDetailsResponse.getErrors())) {
        throw new ReceivingBadDataException(
            ExceptionCodes.ITEM_DETAILS_NOT_FOUND_IN_RDS,
            String.format(
                ReceivingException.ITEM_DETAILS_NOT_FOUND_IN_RDS,
                itemDetailsResponse.getErrors().get(0).getItem_nbr(),
                itemDetailsResponse.getErrors().get(0).getMessage()),
            itemDetailsResponse.getErrors().get(0).getItem_nbr());
      }
    }
    TenantContext.get().setCreateInstrUpdateItemDetailsNimRdsCallEnd(System.currentTimeMillis());
    LOGGER.info(
        "LatencyCheck UpdateAdditionalItemDetails at ts={} time in totalTimeTakenforUpdateAdditionalItemDetails={}, and correlationId={}",
        TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallStart(),
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallEnd()),
        TenantContext.getCorrelationId());
  }

  /**
   * This method validates prime slot details from NimRds & it will throw error when the freight
   * type is SSTK and does not have any prime slot details. If the freight type is DA then it will
   * not throw any error even the item is not having any prime slot details
   */
  private void validatePrimeSlotInfoExists(
      Item itemData, DeliveryDocumentLine deliveryDocumentLine) {
    if (!ObjectUtils.allNotNull(itemData.getPrimeSlot(), itemData.getPrimeSlotSize())
        && ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
      LOGGER.error(
          "RDS didn't return expected prime slot data for item:{}. [prime slot:{}, prime-slot size:{}]",
          itemData.getItem_nbr(),
          itemData.getPrimeSlot(),
          itemData.getPrimeSlotSize());
      String packTypeHandlingCodeShortName =
          PACKTYPE_HANDLINGCODE_MAP.get(
              StringUtils.join(itemData.getPackType(), itemData.getHandlingCode()));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_RDS_PRIME_SLOTTING_REQ,
          String.format(
              ReceivingConstants.RDS_PRIME_SLOT_ERROR_MSG,
              String.valueOf(itemData.getItem_nbr()),
              StringUtils.isNotBlank(packTypeHandlingCodeShortName)
                  ? packTypeHandlingCodeShortName
                  : NOT_APPLICABLE),
          String.valueOf(itemData.getItem_nbr()),
          StringUtils.isNotBlank(packTypeHandlingCodeShortName)
              ? packTypeHandlingCodeShortName
              : NOT_APPLICABLE);
    }
  }

  @TimeTracing(
      component = AppComponent.RDS,
      type = Type.REST,
      executionFlow = "getContainerLabelFromRds")
  public ReceiveContainersResponseBody getContainerLabelFromRDS(
      Instruction instruction,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders) {
    TenantContext.get().setNimRdsReceiveContainersCallStart(System.currentTimeMillis());
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    Map<Instruction, SlotDetails> instructionSlotDetailsMap = new HashMap<>();
    instruction.setReceivedQuantity(receiveInstructionRequest.getQuantity());
    instruction.setReceivedQuantityUOM(receiveInstructionRequest.getQuantityUOM());

    instructionSlotDetailsMap.put(instruction, receiveInstructionRequest.getSlotDetails());

    ReceiveContainersRequestBody receiveContainersRequestBody =
        getReceiveContainersRequestBody(instructionSlotDetailsMap, userId);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        rdsRestApiClient.receiveContainers(receiveContainersRequestBody, httpHeadersMap);

    if (CollectionUtils.isEmpty(receiveContainersResponseBody.getReceived())) {
      LOGGER.error(
          "Error while receiving containers in RDS for PO: {} and POL: {}",
          receiveInstructionRequest.getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber(),
          receiveInstructionRequest
              .getDeliveryDocumentLines()
              .get(0)
              .getPurchaseReferenceLineNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVE_CONTAINERS_RDS_ERROR,
          String.format(
              ReceivingConstants.NO_CONTAINERS_RECEIVED_IN_RDS,
              receiveInstructionRequest
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getPurchaseReferenceNumber(),
              receiveInstructionRequest
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getPurchaseReferenceLineNumber()),
          receiveInstructionRequest.getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber(),
          receiveInstructionRequest
              .getDeliveryDocumentLines()
              .get(0)
              .getPurchaseReferenceLineNumber());
    }
    TenantContext.get().setNimRdsReceiveContainersCallEnd(System.currentTimeMillis());
    LOGGER.info(
        "LatencyCheck ReceiveContainers at ts={} time in totalTimeTakenforReceiveContainers={}, and correlationId={}",
        TenantContext.get().getNimRdsReceiveContainersCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getNimRdsReceiveContainersCallStart(),
            TenantContext.get().getNimRdsReceiveContainersCallEnd()),
        TenantContext.getCorrelationId());

    return receiveContainersResponseBody;
  }

  @TimeTracing(component = AppComponent.RDS, type = Type.REST, executionFlow = "quantityChange")
  public void quantityChange(Integer adjustedQty, String scanTag, HttpHeaders httpHeaders) {
    TenantContext.get().setNimRdsUpdateQuantityCallStart(System.currentTimeMillis());
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    QuantityChangeRequestBody quantityChangeRequestBody = new QuantityChangeRequestBody();
    quantityChangeRequestBody.setQuantity(adjustedQty);
    quantityChangeRequestBody.setScanTag(scanTag);
    quantityChangeRequestBody.setUserId(userId);

    rdsRestApiClient.quantityChange(quantityChangeRequestBody, httpHeadersMap);
    TenantContext.get().setNimRdsUpdateQuantityCallEnd(System.currentTimeMillis());
    LOGGER.info(
        "LatencyCheck UpdateQuantity at ts={} time in totalTimeTakenforUpdateQuantity={}, and correlationId={}",
        TenantContext.get().getNimRdsUpdateQuantityCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getNimRdsUpdateQuantityCallStart(),
            TenantContext.get().getNimRdsUpdateQuantityCallEnd()),
        TenantContext.getCorrelationId());
  }

  public ReceiveContainersRequestBody getReceiveContainersRequestBody(
      Map<Instruction, SlotDetails> instructionToSlotMap, String userId) {

    List<ContainerOrder> containerOrders = new ArrayList<>();
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();

    Instruction instructionData =
        instructionToSlotMap.entrySet().stream().findFirst().get().getKey();
    String fromLocation =
        instructionData.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString();
    String deliveryNumber = instructionData.getDeliveryNumber().toString();
    String doorNumber = getLastDoorNumber(fromLocation, deliveryNumber);

    for (Map.Entry<Instruction, SlotDetails> instructionToSlotEntry :
        instructionToSlotMap.entrySet()) {
      SlotDetails slotDetails = instructionToSlotEntry.getValue();
      Instruction instruction = instructionToSlotEntry.getKey();
      DeliveryDocumentLine documentLine = InstructionUtils.getDeliveryDocumentLine(instruction);

      ContainerOrder containerOrder = new ContainerOrder();
      containerOrder.setId(instruction.getMessageId());
      containerOrder.setPoNumber(instruction.getPurchaseReferenceNumber());
      containerOrder.setPoLine(instruction.getPurchaseReferenceLineNumber());
      containerOrder.setManifest(instruction.getDeliveryNumber().toString());
      containerOrder.setDoorNum(doorNumber);
      containerOrder.setUserId(userId);
      containerOrder.setReceivedUomTxt(instruction.getProjectedReceiveQtyUOM());
      // For Split pallet all the containers that we send in RDS payload should have same
      // ContainerGroupId
      containerOrder.setContainerGroupId(
          Objects.nonNull(instruction.getInstructionSetId())
              ? String.valueOf(instruction.getInstructionSetId())
              : instruction.getMessageId());
      containerOrder.setQty(instruction.getReceivedQuantity());
      containerOrder.setExpectedTi(documentLine.getPalletTie());
      containerOrder.setExpectedHi(documentLine.getPalletHigh());
      if (ObjectUtils.allNotNull(documentLine.getVendorPack(), documentLine.getWarehousePack())) {
        Integer breakPackRatio =
            Math.floorDiv(documentLine.getVendorPack(), documentLine.getWarehousePack());
        if (breakPackRatio > 0) {
          containerOrder.setBreakpackRatio(breakPackRatio);
        }
      }

      if (Objects.nonNull(slotDetails)) {
        if (StringUtils.isNotBlank(slotDetails.getSlot())) {
          // Manual Slotting
          SlottingOverride slottingOverride = new SlottingOverride();
          slottingOverride.setSlottingType(RDS_SLOTTING_TYPE_MANUAL);
          if (Objects.nonNull(slotDetails.getSlotSize())) {
            slottingOverride.setSlotSize(slotDetails.getSlotSize());
          }
          slottingOverride.setSlot(slotDetails.getSlot());
          slottingOverride.setXrefDoor(containerOrder.getDoorNum());
          if (Objects.nonNull(slotDetails.getSlotRange())) {
            slottingOverride.setSlotRangeEnd(slotDetails.getSlotRange());
          }
          containerOrder.setSlottingOverride(slottingOverride);
        } else {
          // Auto Slotting
          containerOrder.setSstkSlotSize(slotDetails.getSlotSize());
        }
      }

      // Split pallet Auto slotting expects slotting type as SPLIT
      if (Objects.nonNull(instruction.getInstructionSetId())
          && Objects.isNull(containerOrder.getSlottingOverride())) {
        SlottingOverride splitPalletSlottingOverride = new SlottingOverride();
        splitPalletSlottingOverride.setSlottingType(RDS_SLOTTING_TYPE_SPLIT);
        containerOrder.setSlottingOverride(splitPalletSlottingOverride);
      }

      containerOrders.add(containerOrder);
    }
    receiveContainersRequestBody.setContainerOrders(containerOrders);

    return receiveContainersRequestBody;
  }

  /**
   * @param instructionRequest
   * @param deliveryDocumentLine
   * @param httpHeaders
   * @param containerCount
   * @param receiveInstructionRequest
   * @return
   */
  public ReceiveContainersRequestBody getReceiveContainersRequestBodyForDAReceiving(
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      Integer containerCount,
      ReceiveInstructionRequest receiveInstructionRequest) {
    /* In case of printing 1 container (Slotting & VOICE_PUT & NONCON_RTS_PUT) printing,
    received qty should be same as what the user is entered else
    it can be default 1 case qty */
    Integer quantity =
        Objects.nonNull(receiveInstructionRequest)
                && (containerCount == RdcConstants.CONTAINER_COUNT_ONE)
            ? receiveInstructionRequest.getQuantity()
            : RdcConstants.RDC_DA_CASE_RECEIVE_QTY;
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String messageId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();

    // Auto slotting
    if (Objects.nonNull(receiveInstructionRequest)
        && Objects.nonNull(receiveInstructionRequest.getPalletQuantities())) {
      for (PalletQuantities palletQuantity : receiveInstructionRequest.getPalletQuantities()) {
        ContainerOrder containerOrder =
            getContainerOrderPayLoad(
                receiveInstructionRequest,
                palletQuantity.getQuantity(),
                getIndexedMessageId(messageId, palletQuantity.getPallet()),
                deliveryDocumentLine,
                instructionRequest,
                userId);
        containerOrders.add(containerOrder);
      }
    } else {
      for (int index = 0; index < containerCount; index++) {
        ContainerOrder containerOrder =
            getContainerOrderPayLoad(
                receiveInstructionRequest,
                quantity,
                getIndexedMessageId(messageId, index),
                deliveryDocumentLine,
                instructionRequest,
                userId);
        containerOrders.add(containerOrder);
      }
    }
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  private String getIndexedMessageId(String messageId, int index) {
    return messageId + ReceivingConstants.DELIM_DASH + index;
  }

  /**
   * @param receiveInstructionRequest
   * @param quantity
   * @param messageId
   * @param deliveryDocumentLine
   * @param instructionRequest
   * @param userId
   * @return
   */
  private ContainerOrder getContainerOrderPayLoad(
      ReceiveInstructionRequest receiveInstructionRequest,
      Integer quantity,
      String messageId,
      DeliveryDocumentLine deliveryDocumentLine,
      InstructionRequest instructionRequest,
      String userId) {
    String doorNumber =
        getLastDoorNumber(
            instructionRequest.getDoorNumber(),
            String.valueOf(instructionRequest.getDeliveryNumber()));
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setId(messageId);
    containerOrder.setPoNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    containerOrder.setPoLine(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    containerOrder.setManifest(instructionRequest.getDeliveryNumber());
    containerOrder.setDoorNum(doorNumber);
    boolean isSlottingPayLoad =
        Objects.nonNull(receiveInstructionRequest)
            && Objects.nonNull(receiveInstructionRequest.getSlotDetails());

    if (isSlottingPayLoad) {
      SlotDetails slotDetails = receiveInstructionRequest.getSlotDetails();
      SlottingOverride slottingOverride = new SlottingOverride();
      if (Objects.nonNull(slotDetails.getSlot())) {
        slottingOverride.setSlot(slotDetails.getSlot());
        slottingOverride.setSlottingType(SLOTTING_MANUAL);
      } else {
        slottingOverride.setSlottingType(SLOTTING_DA);
      }
      if (Objects.nonNull(slotDetails.getSlotSize())) {
        slottingOverride.setSlotSize(slotDetails.getSlotSize());
      }
      if (Objects.nonNull(slotDetails.getStockType())) {
        slottingOverride.setStockType(slotDetails.getStockType());
      }
      if (Objects.nonNull(slotDetails.getCrossReferenceDoor())) {
        slottingOverride.setXrefDoor(slotDetails.getCrossReferenceDoor());
      }
      if (Objects.nonNull(slotDetails.getSlotRange())) {
        slottingOverride.setSlotRangeEnd(slotDetails.getSlotRange());
      }
      if (Objects.nonNull(slotDetails.getMaxPallet())) {
        slottingOverride.setMaxPallet(slotDetails.getMaxPallet());
      }
      containerOrder.setSlottingOverride(slottingOverride);
      containerOrder.setExpectedHi(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
      containerOrder.setExpectedTi(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
      containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.VNPK);
    } else {
      if (rdcReceivingUtils.isWhpkReceiving(deliveryDocumentLine, receiveInstructionRequest)) {
        containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.WHPK);
      } else {
        containerOrder.setReceivedUomTxt(ReceivingConstants.Uom.VNPK);
      }
    }

    if (Objects.nonNull(receiveInstructionRequest)
        && Objects.nonNull(receiveInstructionRequest.getIsLessThanCase())) {
      containerOrder.setIsLessThanCase(receiveInstructionRequest.getIsLessThanCase());
    }
    containerOrder.setQty(quantity);
    containerOrder.setUserId(userId);
    containerOrder.setBreakpackRatio(RdcUtils.getBreakPackRatio(deliveryDocumentLine));
    return containerOrder;
  }

  @TimeTracing(
      component = AppComponent.RDS,
      type = Type.REST,
      executionFlow = "getMultipleContainerLabelFromRds")
  public ReceiveContainersResponseBody getMultipleContainerLabelsFromRds(
      Map<Instruction, SlotDetails> instructionSlotDetailsMap, HttpHeaders httpHeaders) {
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    ReceiveContainersRequestBody receiveContainersRequestBody =
        getReceiveContainersRequestBody(instructionSlotDetailsMap, userId);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        rdsRestApiClient.receiveContainers(receiveContainersRequestBody, httpHeadersMap);
    return receiveContainersResponseBody;
  }

  /**
   * This method checks whether given door is actual door or work station location. If it's valid
   * door then it returns that door number. Otherwise, it fetches last door information from
   * DELIVERY_METADATA table by delivery number and returns the retrieved door number. If no records
   * found in DELIVERY_METADATA table then it defaults door to 999.
   *
   * @param location
   * @param deliveryNumber
   * @return lastDoorNumber
   */
  public String getLastDoorNumber(String location, String deliveryNumber) {
    String lastDoorNumber = null;
    if (!NumberUtils.isParsable(location)) {
      LOGGER.info(
          "Location: {} is not a door, fetching last doorNumber from DELIVERY METADATA table for delivery: {}",
          location,
          deliveryNumber);
      Optional<DeliveryMetaData> deliveryMetaData =
          rdcDeliveryMetaDataService.findByDeliveryNumber(deliveryNumber);
      if (deliveryMetaData.isPresent()
          && StringUtils.isNotBlank(deliveryMetaData.get().getDoorNumber())) {
        LOGGER.info(
            "Found doorNumber: {} for delivery: {} in delivery metadata table",
            deliveryMetaData.get().getDoorNumber(),
            deliveryNumber);
        lastDoorNumber = deliveryMetaData.get().getDoorNumber();
      } else {
        LOGGER.error(
            "Delivery metadata information is not found OR doorNumber is not available in Delivery metadata table for delivery: {}, defaulting door to 999",
            deliveryNumber);
        lastDoorNumber = ReceivingConstants.DEFAULT_DOOR;
      }
    } else {
      lastDoorNumber = location;
    }
    return lastDoorNumber;
  }

  /**
   * This service invokes nim RDS service api to back out given DA labels
   *
   * @param scanTagList
   * @param httpHeaders
   */
  @TimeTracing(component = AppComponent.RDS, type = Type.REST, executionFlow = "backoutDALabels")
  public void backoutDALabels(List<String> scanTagList, HttpHeaders httpHeaders) {
    TenantContext.get().setNimRdsBackoutDALabelStart(System.currentTimeMillis());
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    DALabelBackoutRequest daLabelBackoutRequest =
        DALabelBackoutRequest.builder().labels(scanTagList).userId(userId).build();

    DALabelBackoutResponse daLabelBackoutResponse =
        rdsRestApiClient.labelBackout(daLabelBackoutRequest, httpHeadersMap);
    List<DABackoutLabel> failedLabels =
        daLabelBackoutResponse
            .getDaBackoutLabels()
            .stream()
            .filter(
                daLabel ->
                    !daLabel
                        .getReturnCode()
                        .equals(RdcConstants.NIM_RDS_DA_BACK_OUT_LABEL_RESPONSE_SUCCESS_CODE))
            .collect(Collectors.toList());

    if (CollectionUtils.isNotEmpty(failedLabels)) {
      if (daLabelBackoutResponse.getDaBackoutLabels().size() == failedLabels.size()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.UNABLE_TO_CANCEL_LABEL,
            String.format(
                ExceptionCodes.UNABLE_TO_CANCEL_LABEL,
                failedLabels.get(0).getScanTag(),
                failedLabels.get(0).getReturnText()),
            failedLabels.get(0).getScanTag(),
            failedLabels.get(0).getReturnText());
      }
      for (DABackoutLabel daBackoutLabel : failedLabels) {
        LOGGER.error(
            "Error while processing backout label with scanTag={}, and the error message={}",
            daBackoutLabel.getScanTag(),
            daBackoutLabel.getReturnText());
      }
    }

    TenantContext.get().setNimRdsBackoutDALabelEnd(System.currentTimeMillis());
    LOGGER.info(
        "LatencyCheck backoutDALabels at ts={} time in totalTimeTakenForBackoutDALabels={}, and correlationId={}",
        TenantContext.get().getNimRdsBackoutDALabelStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getNimRdsBackoutDALabelStart(),
            TenantContext.get().getNimRdsBackoutDALabelEnd()),
        TenantContext.getCorrelationId());
  }

  /**
   * This service invokes nim RDS service api to receive a DSDC pack
   *
   * @param instructionRequest
   * @param httpHeaders
   */
  public DsdcReceiveRequest getDsdcReceiveContainerRequest(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {

    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    final String correlationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    String doorNumber =
        getLastDoorNumber(
            instructionRequest.getDoorNumber(), instructionRequest.getDeliveryNumber());
    return DsdcReceiveRequest.builder()
        ._id(correlationId)
        .manifest(instructionRequest.getDeliveryNumber())
        .doorNum(doorNumber)
        .pack_nbr(instructionRequest.getSscc())
        .userId(userId)
        .build();
  }

  /**
   * This service invokes nim RDS service api to receive a DSDC pack
   *
   * @param dsdcReceiveRequest
   * @param httpHeaders
   */
  @TimeTracing(
      component = AppComponent.RDS,
      type = Type.REST,
      executionFlow = "receiveDsdcContainerInRds")
  public DsdcReceiveResponse receiveDsdcContainerInRds(
      DsdcReceiveRequest dsdcReceiveRequest, HttpHeaders httpHeaders) {
    TenantContext.get().setNimRdsReceiveDsdcStart(System.currentTimeMillis());

    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    DsdcReceiveResponse dsdcReceiveResponse =
        rdsRestApiClient.receiveDsdcPack(dsdcReceiveRequest, httpHeadersMap);
    TenantContext.get().setNimRdsReceiveDsdcEnd(System.currentTimeMillis());
    LOGGER.info(
        "LatencyCheck dsdcReceive at ts={} time in totalTimeTakenForDsdcReceive={}, and correlationId={}",
        TenantContext.get().getNimRdsReceiveDsdcStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getNimRdsReceiveDsdcStart(),
            TenantContext.get().getNimRdsReceiveDsdcEnd()),
        TenantContext.getCorrelationId());

    return dsdcReceiveResponse;
  }

  /**
   * This method validates if the given items are Atlas converted items and if any DA POs exist in
   * the delivery documents then returns too as this delivery document is eligible for the legacy
   * received qty validations.
   *
   * @param deliveryDocumentList
   * @param upcNumber
   * @return
   */
  private boolean isInProgressPOsValidationInLegacyRequired(
      List<DeliveryDocument> deliveryDocumentList, String upcNumber) {
    boolean isInProgressPOsReceivedQtyValidationInLegacyRequired = false;
    boolean isDaOneAtlasEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            IS_LEGACY_RCVD_QTY_CHECK_ENABLED_FOR_DA_ATLAS_ITEMS,
            false);
    if (isDaOneAtlasEnabled) {
      List<DeliveryDocument> daDocumentList = new ArrayList<>();
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setDeliveryNumber(
          String.valueOf(deliveryDocumentList.get(0).getDeliveryNumber()));
      instructionRequest.setUpcNumber(upcNumber);
      try {
        daDocumentList =
            rdcInstructionUtils.filterDADeliveryDocuments(deliveryDocumentList, instructionRequest);
      } catch (ReceivingBadDataException receivingBadDataException) {
        LOGGER.error(
            "Found Non DA PO for the given delivery:{} and UPC:{}",
            instructionRequest.getDeliveryNumber(),
            instructionRequest.getUpcNumber());
      }
      if (CollectionUtils.isNotEmpty(daDocumentList)) {
        // filter only atlas converted items
        daDocumentList =
            daDocumentList
                .stream()
                .filter(
                    deliveryDocument ->
                        deliveryDocument
                            .getDeliveryDocumentLines()
                            .stream()
                            .anyMatch(
                                deliveryDocumentLine ->
                                    Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())
                                        && deliveryDocumentLine
                                            .getAdditionalInfo()
                                            .isAtlasConvertedItem()))
                .collect(Collectors.toList());
        isInProgressPOsReceivedQtyValidationInLegacyRequired =
            CollectionUtils.isNotEmpty(daDocumentList);
      }
    }
    return isInProgressPOsReceivedQtyValidationInLegacyRequired;
  }
}
