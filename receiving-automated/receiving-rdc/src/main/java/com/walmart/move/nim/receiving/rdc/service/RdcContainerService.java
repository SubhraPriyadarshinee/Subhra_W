package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.MATCHING_CONTAINER_NOT_FOUND;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_REQUEST_KEY;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.iqs.AsyncIqsRestApiClient;
import com.walmart.move.nim.receiving.core.client.iqs.model.ItemBulkResponseDto;
import com.walmart.move.nim.receiving.core.client.iqs.model.ItemResponseDto;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service(RDC_CONTAINER_SERVICE)
public class RdcContainerService extends AbstractContainerService {

  private static final Logger logger = LoggerFactory.getLogger(RdcContainerService.class);
  @Autowired private ContainerItemService containerItemService;
  @Autowired private SlottingServiceImpl slottingService;
  @Autowired @Lazy private AsyncIqsRestApiClient asyncIqsRestApiClient;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private InventoryService inventoryService;

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private AsyncLocationService asyncLocationService;
  @Autowired private InstructionRepository instructionRepository;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * This method gets container item details from container item table for given upc number & get
   * prime slot from Smart Slotting for all the item numbers. The latest prime slot info will be
   * updated on the container item object.
   *
   * @param upcNumber
   * @return ContainerItem
   */
  public List<ContainerItem> getContainerItemsByUpc(String upcNumber, HttpHeaders httpHeaders) {
    logger.info("Get container item details by upcNumber:{}", upcNumber);
    List<ContainerItem> containerItems =
        containerItemService.getContainerItemMetaDataByUpcNumber(upcNumber);

    if (!CollectionUtils.isEmpty(containerItems)) {
      SlottingPalletResponse slottingPalletResponse =
          slottingService.getPrimeSlot(containerItems, httpHeaders);

      if (Objects.nonNull(slottingPalletResponse)
          && !CollectionUtils.isEmpty(slottingPalletResponse.getLocations())) {
        for (ContainerItem containerItem : containerItems) {
          SlottingDivertLocations slottingDivertLocations = null;
          Optional<SlottingDivertLocations> primeSlotLocation =
              slottingPalletResponse
                  .getLocations()
                  .stream()
                  .filter(
                      divertLocation ->
                          divertLocation.getItemNbr() == containerItem.getItemNumber()
                              && Objects.nonNull(divertLocation.getLocation()))
                  .findFirst();
          if (primeSlotLocation.isPresent()) {
            slottingDivertLocations = primeSlotLocation.get();
            Map<String, String> containerItemMiscInfo = containerItem.getContainerItemMiscInfo();
            containerItemMiscInfo.put(
                ReceivingConstants.PRIME_SLOT_ID, slottingDivertLocations.getLocation());
            containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
            if (StringUtils.isNotBlank(slottingDivertLocations.getSlotType())) {
              containerItem.setSlotType(slottingDivertLocations.getSlotType());
            }

            if (StringUtils.isNotBlank(slottingDivertLocations.getAsrsAlignment())) {
              containerItem.setAsrsAlignment(slottingDivertLocations.getAsrsAlignment());
            } else if (StringUtils.isNotBlank(containerItem.getAsrsAlignment())) {
              // override previous asrsAlignment value
              containerItem.setAsrsAlignment(null);
            }
          }
        }
      }
    }
    return containerItems;
  }

  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      executionFlow = "Reprint-Container-Labels")
  public Map<String, Object> getContainerLabelsByTrackingIds(
      List<String> trackingIds, HttpHeaders httpHeaders) throws ReceivingException {
    logger.info(
        "ContainerService:: getContainerLabelsByTrackingIds: Reprint request for trackingIds: {}",
        trackingIds);
    if (CollectionUtils.isEmpty(trackingIds)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REPRINT_LABEL_REQUEST,
          ReceivingConstants.INVALID_REPRINT_LABEL_REQUEST);
    }
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    List<PrintLabelRequest> printLabelRequestList = new ArrayList<>();
    Map<String, Object> printJob = new HashMap<>();
    Gson gsonObj = new Gson();

    List<InstructionIdAndTrackingIdPair> instructionIdAndTrackingIdPairs =
        containerPersisterService.getInstructionIdsObjByTrackingIds(trackingIds);

    List<String> trackingIdsAvailableInReceiving =
        !CollectionUtils.isEmpty(instructionIdAndTrackingIdPairs)
            ? instructionIdAndTrackingIdPairs
                .stream()
                .map(InstructionIdAndTrackingIdPair::getTrackingId)
                .collect(Collectors.toList())
            : new ArrayList<>();
    List<Long> instructionsIdsFoundInReceiving =
        !CollectionUtils.isEmpty(instructionIdAndTrackingIdPairs)
            ? instructionIdAndTrackingIdPairs
                .stream()
                .map(InstructionIdAndTrackingIdPair::getInstructionId)
                .collect(Collectors.toList())
            : new ArrayList<>();
    List<String> missingTrackingIdsInReceiving =
        trackingIds
            .stream()
            .filter(item -> !trackingIdsAvailableInReceiving.contains(item))
            .collect(Collectors.toList());

    // Prepare label data for containers present in receiving db.
    if (!CollectionUtils.isEmpty(instructionsIdsFoundInReceiving)) {
      prepareLabelFormatDetails(instructionsIdsFoundInReceiving, printLabelRequestList, gsonObj);
    }
    if (appConfig.isReprintOldLabelsEnabled()) {
      // Fetch missing container details from inventory
      // Calling inventory api to get container details
      List<ContainerItemDetails> containerItemDetailsList = new ArrayList<>();
      ItemBulkResponseDto iqsResponseDto = new ItemBulkResponseDto();
      JsonObject locationInfoResponse = new JsonObject();
      List<String> missingTrackingIds = new ArrayList<>();
      if (!CollectionUtils.isEmpty(missingTrackingIdsInReceiving)) {
        try {
          String containerResponse =
              inventoryService.getBulkContainerDetails(missingTrackingIdsInReceiving, httpHeaders);
          List<ContainersResponseData> containersResponseDataList = new ArrayList<>();
          if (!Objects.isNull(containerResponse)) {
            containersResponseDataList =
                Arrays.asList(gsonObj.fromJson(containerResponse, ContainersResponseData[].class));
          }
          if (!CollectionUtils.isEmpty(containersResponseDataList)) {
            containerItemDetailsList =
                containersResponseDataList
                    .stream()
                    .flatMap(response -> response.getContainerInventories().stream())
                    .collect(Collectors.toList());

            Set<String> itemNumbers =
                containerItemDetailsList
                    .stream()
                    .flatMap(containerItemsList -> containerItemsList.getItems().stream())
                    .map(ContainerItemDetail::getItemNumber)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());

            List<String> locationNames =
                containerItemDetailsList
                    .stream()
                    .map(ContainerItemDetails::getLocationName)
                    .collect(Collectors.toList());
            // Call iqs api to get pallet ti and hi and vendor stock Id
            CompletableFuture<Optional<ItemBulkResponseDto>> asyncItemBulkResponseDto =
                asyncIqsRestApiClient.getItemDetailsFromItemNumber(
                    itemNumbers,
                    httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
                    httpHeaders);
            // Call location search api to get location name
            CompletableFuture<JsonObject> asyncBulkLocationInfoResponse =
                asyncLocationService.getBulkLocationInfo(locationNames, httpHeaders);

            CompletableFuture.allOf(asyncItemBulkResponseDto, asyncBulkLocationInfoResponse).join();

            Optional<ItemBulkResponseDto> itemBulkResponseDto = asyncItemBulkResponseDto.get();
            iqsResponseDto = itemBulkResponseDto.orElse(new ItemBulkResponseDto());
            locationInfoResponse = asyncBulkLocationInfoResponse.get();
          }
        } catch (InterruptedException | ExecutionException e) {
          logger.error(
              INTERRUPTED_EXCEPTION_ERROR,
              ExceptionUtils.getStackTrace(e),
              missingTrackingIdsInReceiving);
          logger.error(
              "Error while fetching label details for containers: {} and error: {}",
              containerItemDetailsList,
              ExceptionUtils.getStackTrace(e));
          // Restore interrupted state...
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          logger.error(
              RECEIVING_CONTAINER_DETAILS_ERROR_MESSAGE,
              missingTrackingIdsInReceiving,
              ExceptionUtils.getStackTrace(e));
          logger.error(
              "Error while fetching label details for containers: {} and error: {}",
              containerItemDetailsList,
              ExceptionUtils.getStackTrace(e));
        }
        Map<String, String> locationSizeMap = new HashMap<>();
        try {
          if (!containerItemDetailsList.isEmpty()) {
            prepareLocationSizeMap(locationInfoResponse, locationSizeMap);
            for (ContainerItemDetails container : containerItemDetailsList) {
              LabelAttributes labelAttributes =
                  populateLabelAttributes(iqsResponseDto, container, locationSizeMap, userId);
              logger.info(
                  "Label attributes :{} for trackingId: {}",
                  labelAttributes,
                  container.getTrackingId());
              // Prepare Reprint Label
              ReprintLabelHandler reprintLabelHandler =
                  tenantSpecificConfigReader.getConfiguredInstance(
                      getFacilityNum().toString(),
                      REPRINT_LABEL_HANDLER_KEY,
                      ReprintLabelHandler.class);
              printLabelRequestList.add(
                  reprintLabelHandler.populateReprintLabel(container, labelAttributes));
            }
          }
        } catch (Exception exception) {
          logger.error(
              "Error while preparing label data for containers: {} and error: {}",
              containerItemDetailsList,
              ExceptionUtils.getStackTrace(exception));
          throw new ReceivingInternalException(
              ExceptionCodes.LABEL_PREPARATION_ERROR, RECEIVING_LABEL_PREPARATION_ERROR_MESSAGE);
        }

        // Extract missing container ids and send it to client
        List<String> trackingIdsAvailableInInventory =
            containerItemDetailsList
                .stream()
                .map(ContainerItemDetails::getTrackingId)
                .collect(Collectors.toList());
        missingTrackingIds =
            missingTrackingIdsInReceiving
                .stream()
                .filter(element -> !trackingIdsAvailableInInventory.contains(element))
                .collect(Collectors.toList());
      }
      printJob.put(PRINT_MISSING_LPNS, missingTrackingIds);
    } else if (printLabelRequestList.isEmpty()) {
      // Added this code just for backward compatibility
      logger.error("Container not found for trackingIds: {}", trackingIds);
      throw new ReceivingBadDataException(
          ExceptionCodes.CONTAINER_NOT_FOUND, MATCHING_CONTAINER_NOT_FOUND);
    }

    printJob.put(PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(PRINT_CLIENT_ID_KEY, ATLAS_RECEIVING);

    // filter only the given trackingIds from the printLabelRequest
    List<PrintLabelRequest> printLabelRequestListByTrackingIds = new ArrayList<>();
    for (String trackingId : trackingIds) {
      List<PrintLabelRequest> matchedPrintLabelRequest =
          printLabelRequestList
              .stream()
              .filter(
                  printLabelRequest -> printLabelRequest.getLabelIdentifier().equals(trackingId))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(matchedPrintLabelRequest)) {
        printLabelRequestListByTrackingIds.addAll(matchedPrintLabelRequest);
      }
    }

    if (!CollectionUtils.isEmpty(printLabelRequestListByTrackingIds)) {
      updateReprintDataInPrintRequest(printLabelRequestListByTrackingIds);
      printJob.put(PRINT_REQUEST_KEY, printLabelRequestListByTrackingIds);
    } else {
      updateReprintDataInPrintRequest(printLabelRequestList);
      printJob.put(PRINT_REQUEST_KEY, printLabelRequestList);
    }
    logger.info(
        "ContainerService:: getContainerLabelsByTrackingIds: Reprint response printJob: {} for trackingIds: {}",
        printJob,
        trackingIds);
    return printJob;
  }

  public void prepareLabelFormatDetails(
      List<Long> instructionsIdsFoundInReceiving,
      List<PrintLabelRequest> printLabelRequestList,
      Gson gsonObj) {
    List<Instruction> instructionList =
        instructionRepository.findByIdIn(instructionsIdsFoundInReceiving);
    if (!CollectionUtils.isEmpty(instructionList)) {
      for (Instruction instruction : instructionList) {
        JsonArray jsonPrintRequests =
            gsonObj
                .toJsonTree(instruction.getContainer().getCtrLabel())
                .getAsJsonObject()
                .getAsJsonArray(PRINT_REQUEST_KEY);
        printLabelRequestList.addAll(
            gsonObj.fromJson(
                jsonPrintRequests, new TypeToken<ArrayList<PrintLabelRequest>>() {}.getType()));
      }
    }
  }

  public void prepareLocationSizeMap(
      JsonObject locationInfoResponse, Map<String, String> locationSizeMap) {
    if (locationInfoResponse.size() > 0) {
      JsonArray locationsArray =
          locationInfoResponse
              .get(ReceivingConstants.SUCCESS_TUPLES)
              .getAsJsonArray()
              .get(0)
              .getAsJsonObject()
              .get(ReceivingConstants.RESPONSE)
              .getAsJsonObject()
              .get(ReceivingConstants.LOCATIONS)
              .getAsJsonArray();
      locationsArray.forEach(
          location -> {
            String name =
                !location.getAsJsonObject().get(NAME).isJsonNull()
                    ? location.getAsJsonObject().get(NAME).getAsString()
                    : "";
            String size =
                !location.getAsJsonObject().get(SIZE_INCHES).isJsonNull()
                    ? location.getAsJsonObject().get(SIZE_INCHES).getAsString()
                    : "";
            locationSizeMap.put(name, size);
          });
    }
  }

  public LabelAttributes populateLabelAttributes(
      ItemBulkResponseDto itemBulkResponseDto,
      ContainerItemDetails container,
      Map<String, String> slotSizeMap,
      String userId) {
    LabelAttributes labelAttributes = new LabelAttributes();
    labelAttributes.setUserId(userId);

    String slotSize = slotSizeMap.getOrDefault(container.getLocationName(), EMPTY_STRING);
    String itemNumber =
        !CollectionUtils.isEmpty(container.getItems())
            ? String.valueOf(container.getItems().get(0).getItemNumber())
            : null;
    labelAttributes.setSlotSize(slotSize);

    List<ItemResponseDto> itemResponseDtoList =
        Objects.nonNull(itemBulkResponseDto.getPayload())
            ? itemBulkResponseDto.getPayload()
            : new ArrayList<>();
    Optional<ItemResponseDto> itemResponseDtoOptional =
        itemResponseDtoList
            .stream()
            .filter(itemResponseDto -> Objects.nonNull(itemResponseDto.getItemNbr()))
            .filter(itemResponseDto -> itemResponseDto.getItemNbr().equals(itemNumber))
            .findAny();
    Integer palletTi = null;
    Integer palletHi = null;
    if (itemResponseDtoOptional.isPresent()) {
      ItemResponseDto itemResponseDto = itemResponseDtoOptional.get();
      labelAttributes.setVendorStockNumber(itemResponseDto.getSupplierStockId());
      if (Objects.nonNull(itemResponseDto.getItemNode())) {
        if ((Objects.nonNull(itemResponseDto.getItemNode().getPalletTinode()))) {
          palletTi = itemResponseDto.getItemNode().getPalletTinode();
        }
        if (Objects.nonNull(itemResponseDto.getItemNode().getPalletHinode())) {
          palletHi = itemResponseDto.getItemNode().getPalletHinode();
        }
      }
      if ((Objects.isNull(palletTi) || Objects.isNull(palletHi))
          && Objects.nonNull(itemResponseDto.getGtins())) {
        List<ItemResponseDto.Gtins> gtins = itemResponseDto.getGtins();
        Optional<ItemResponseDto.Gtins> gtin =
            gtins
                .stream()
                .filter(item -> Objects.nonNull(item.getTradeItemGtin()))
                .filter(
                    item -> item.getTradeItemGtin().equals(itemResponseDto.getOrderablePackGtin()))
                .findAny();
        if (gtin.isPresent()) {
          if (Objects.isNull(palletTi) && Objects.nonNull(gtin.get().getPalletTiQty())) {
            palletTi = gtin.get().getPalletTiQty();
          }
          if (Objects.isNull(palletHi) && Objects.nonNull(gtin.get().getPalletHiQty())) {
            palletHi = gtin.get().getPalletHiQty();
          }
        }
      }
    }
    labelAttributes.setPalletTi(Objects.nonNull(palletTi) ? palletTi : 0);
    labelAttributes.setPalletHi(Objects.nonNull(palletHi) ? palletHi : 0);
    return labelAttributes;
  }

  /**
   * Add Reprint value to print Request
   *
   * @param printLabelRequestList
   */
  public List<PrintLabelRequest> updateReprintDataInPrintRequest(
      List<PrintLabelRequest> printLabelRequestList) {
    for (PrintLabelRequest printLabelRequest : printLabelRequestList) {
      for (LabelData dataItem : printLabelRequest.getData()) {
        if (REPRINT.equalsIgnoreCase(dataItem.getKey())) {
          dataItem.setValue(LBL_REPRINT_VALUE);
        }
      }
    }
    return printLabelRequestList;
  }
}
