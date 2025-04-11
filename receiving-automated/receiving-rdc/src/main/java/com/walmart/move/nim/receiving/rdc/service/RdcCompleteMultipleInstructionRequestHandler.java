package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeaders;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.move.MoveContainer;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.*;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.CommonLabelDetails;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/** @author v0k00fe */
@Component(ReceivingConstants.RDC_COMPLETE_MULTIPLE_INST_REQ_HANDLER)
public class RdcCompleteMultipleInstructionRequestHandler
    implements CompleteMultipleInstructionRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcCompleteMultipleInstructionRequestHandler.class);

  @ManagedConfiguration private AppConfig appconfig;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private Gson gson;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private PrintJobService printJobService;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private ContainerService containerService;
  @Autowired private RdcInstructionHelper rdcInstructionHelper;
  @Autowired private SlottingRestApiClient slottingRestApiClient;
  @Autowired private RdcLpnUtils rdcLpnUtils;
  @Autowired private RdcDcFinUtils rdcDcFinUtils;
  @Autowired private MovePublisher movePublisher;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Override
  public CompleteMultipleInstructionResponse complete(
      BulkCompleteInstructionRequest bulkCompleteInstructionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    CompleteMultipleInstructionResponse completeMultipleInstructionResponse =
        new CompleteMultipleInstructionResponse();
    List<Container> modifiedContainers = new ArrayList<>();
    try {
      RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
      List<Object> printRequests = new ArrayList<>();
      List<Instruction> instructionList = new ArrayList<>();
      Map<String, Instruction> poPoLineInstructionMap = new HashMap<>();
      Map<String, Instruction> itemInstructionMap = new HashMap<>();
      Map<String, String> itemTrackingIdMap = new HashMap<>();
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();

      for (CompleteMultipleInstructionData instructionData :
          bulkCompleteInstructionRequest.getInstructionData()) {
        Instruction instruction4mDB =
            instructionPersisterService.getInstructionById(instructionData.getInstructionId());
        instructionStateValidator.validate(instruction4mDB);
        ReceivingUtils.verifyUser(instruction4mDB, userId, RequestType.COMPLETE);
        instructionList.add(instruction4mDB);
        poPoLineInstructionMap.put(getInstructionKey(instruction4mDB), instruction4mDB);
      }
      slottingPalletRequest.setDeliveryNumber(instructionList.get(0).getDeliveryNumber());

      if (rdcInstructionUtils.isAtlasConvertedInstruction(instructionList.get(0))) {
        LOGGER.info("Entering into atlas converted flow");
        Map<CompleteMultipleInstructionData, Instruction> instructionDataAndInstructionMap =
            getInstructionDataAndInstructionMap(
                bulkCompleteInstructionRequest.getInstructionData(), instructionList);

        setSlottingContainerDetails(
            instructionDataAndInstructionMap,
            slottingPalletRequest,
            itemTrackingIdMap,
            itemInstructionMap,
            httpHeaders);
        SlottingPalletResponse slottingPalletResponse =
            getSlotFromSlotting(httpHeaders, slottingPalletRequest);

        processSlottingPalletResponse(
            httpHeaders,
            printRequests,
            itemInstructionMap,
            itemTrackingIdMap,
            slottingPalletResponse,
            modifiedContainers);

        List<String> trackingIdList =
            modifiedContainers
                .stream()
                .map(container -> container.getTrackingId())
                .collect(Collectors.toList());

        if (appconfig.isWftPublishEnabled()) {
          publishInstructionsForAtlasItem(httpHeaders, itemInstructionMap, itemTrackingIdMap);
        }

        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false)) {
          LOGGER.info("Publishing split pallet receipts for containers: {}", trackingIdList);
          containerService.publishMultipleContainersToInventory(
              transformer.transformList(modifiedContainers));
        }

        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false)) {
          LOGGER.info("Posting split pallet receipts to DC Fin for containers: {}", trackingIdList);
          rdcDcFinUtils.postToDCFin(modifiedContainers, ReceivingConstants.PO_TEXT);
        }

        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_MOVE_PUBLISH_ENABLED,
            false)) {
          String fromLocation = modifiedContainers.get(0).getLocation();
          String toLocation = slottingPalletResponse.getLocations().get(0).getLocation();
          LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
          moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, fromLocation);
          moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, toLocation);
          MoveType moveType =
              MoveType.builder()
                  .code(rdcManagedConfig.getMoveTypeCode())
                  .desc(rdcManagedConfig.getMoveTypeDesc())
                  .build();
          moveTreeMap.put(ReceivingConstants.MOVE_TYPE, moveType);

          List<MoveContainer> moveContainerList = new ArrayList<>();
          for (Container container : modifiedContainers) {
            Optional<Map.Entry<String, Instruction>> modifiedInstructionMap =
                itemInstructionMap
                    .entrySet()
                    .stream()
                    .filter(
                        instructionMap ->
                            instructionMap.getValue().getId().equals(container.getInstructionId()))
                    .findAny();
            modifiedInstructionMap.ifPresent(
                instruction ->
                    moveContainerList.add(
                        MoveContainer.builder()
                            .trackingId(container.getTrackingId())
                            .moveQty(instruction.getValue().getReceivedQuantity())
                            .build()));
          }
          movePublisher.publishMove(moveContainerList, moveTreeMap, httpHeaders);
        }
      } else {
        LOGGER.info("Entering into non atlas converted flow");
        Map<Instruction, SlotDetails> instructionSlotDetailsMap =
            getInstructionSlotDetailsMap(
                instructionList, bulkCompleteInstructionRequest.getInstructionData());
        ReceiveContainersResponseBody receiveContainersResponseBody = null;

        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false)) {

          Map<CompleteMultipleInstructionData, Instruction> instructionDataAndInstructionMap =
              getInstructionDataAndInstructionMap(
                  bulkCompleteInstructionRequest.getInstructionData(), instructionList);
          ReceiveContainersRequestBody receiveContainersRequestBody =
              nimRdsService.getReceiveContainersRequestBody(instructionSlotDetailsMap, userId);

          SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
              new SlottingPalletRequestWithRdsPayLoad();
          slottingPalletRequestWithRdsPayLoad.setRds(receiveContainersRequestBody);
          slottingPalletRequestWithRdsPayLoad.setAtlasOnboardedItem(false);
          slottingPalletRequest = slottingPalletRequestWithRdsPayLoad;

          setSlottingContainerDetails(
              instructionDataAndInstructionMap,
              slottingPalletRequest,
              itemTrackingIdMap,
              itemInstructionMap,
              httpHeaders);

          SlottingPalletResponse slottingPalletResponse =
              getSlotFromSlotting(httpHeaders, slottingPalletRequest);

          if (slottingPalletResponse instanceof SlottingPalletResponseWithRdsResponse) {
            SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
                (SlottingPalletResponseWithRdsResponse) slottingPalletResponse;
            receiveContainersResponseBody = slottingPalletResponseWithRdsResponse.getRds();
          }

        } else {
          receiveContainersResponseBody =
              receiveMultiContainersInRds(httpHeaders, instructionSlotDetailsMap);
        }

        if (Objects.nonNull(receiveContainersResponseBody)) {
          persistInstructionData(
              httpHeaders,
              printRequests,
              poPoLineInstructionMap,
              modifiedContainers,
              receiveContainersResponseBody.getReceived());
          publishInstructionsForNonAtlasItem(
              httpHeaders, poPoLineInstructionMap, receiveContainersResponseBody);
        }
      }

      Map<String, Object> printJob = new HashMap<>();
      printJob.put(
          ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
      printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
      printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printRequests);
      completeMultipleInstructionResponse.setPrintJob(printJob);
    } catch (ReceivingBadDataException receivingBadDataException) {
      LOGGER.error(receivingBadDataException.getMessage(), receivingBadDataException);
      throw receivingBadDataException;
    }
    return completeMultipleInstructionResponse;
  }

  private SlottingPalletResponse getSlotFromSlotting(
      HttpHeaders httpHeaders, SlottingPalletRequest slottingPalletRequest) {
    HttpHeaders forwardableHttpHeaders = getForwardableHttpHeaders(httpHeaders);
    forwardableHttpHeaders.add(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, RdcConstants.SPLIT_PALLET_SLOTTING_FEATURE_TYPE);
    return slottingRestApiClient.getSlot(slottingPalletRequest, forwardableHttpHeaders);
  }

  private void processSlottingPalletResponse(
      HttpHeaders httpHeaders,
      List<Object> printRequests,
      Map<String, Instruction> itemInstructionMap,
      Map<String, String> itemTrackingIdMap,
      SlottingPalletResponse slottingPalletResponse,
      List<Container> modifiedContainers)
      throws ReceivingException {
    List<Instruction> modifiedInstructions = new ArrayList<>();
    List<ContainerItem> modifiedContainerItems = new ArrayList<>();

    for (String item : itemInstructionMap.keySet()) {
      Instruction mappedInstruction = itemInstructionMap.get(item);
      DeliveryDocument deliveryDocument =
          gson.fromJson(mappedInstruction.getDeliveryDocument(), DeliveryDocument.class);
      String labelTrackingId = itemTrackingIdMap.get(item);
      List<Container> containersByInstruction =
          containerService.getContainerByInstruction(mappedInstruction.getId());
      for (Container container : containersByInstruction) {
        container.setTrackingId(labelTrackingId);
        if (!ObjectUtils.isEmpty(slottingPalletResponse.getLocations().get(0).getLocation())) {
          Map<String, String> destination = new HashMap<>();
          destination.put(
              ReceivingConstants.SLOT, slottingPalletResponse.getLocations().get(0).getLocation());
          container.setDestination(destination);
        }
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                String.valueOf(TenantContext.getFacilityNum()),
                RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
                false)
            && Objects.nonNull(container.getContainerMiscInfo())) {
          if (Objects.nonNull(container.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE))) {
            Date proDate =
                ReceivingUtils.parseStringToDateTime(
                    String.valueOf(
                        container.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE)));
            container.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, proDate);
          }
        }
        for (ContainerItem containerItem : container.getContainerItems()) {
          containerItem.setTrackingId(labelTrackingId);
          if (!ObjectUtils.isEmpty(
              slottingPalletResponse.getLocations().get(0).getAsrsAlignment())) {
            containerItem.setAsrsAlignment(
                slottingPalletResponse.getLocations().get(0).getAsrsAlignment());
          }
          if (!ObjectUtils.isEmpty(slottingPalletResponse.getLocations().get(0).getSlotType())) {
            containerItem.setSlotType(slottingPalletResponse.getLocations().get(0).getSlotType());
          }
          modifiedContainerItems.add(containerItem);
        }
        modifiedContainers.add(container);
      }
      final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      PrintJob printJob =
          printJobService.createPrintJob(
              mappedInstruction.getDeliveryNumber(),
              mappedInstruction.getId(),
              new HashSet<>(Arrays.asList(labelTrackingId)),
              userId);

      String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
      CommonLabelDetails commonLabelDetails =
          createCommonLabelDetails(
              slottingPalletResponse.getLocations().get(0).getLocation(),
              (int) slottingPalletResponse.getLocations().get(0).getLocationSize(),
              labelTrackingId,
              null);
      LabelFormat labelFormat =
          rdcReceivingUtils.getLabelFormatForPallet(
              deliveryDocument.getDeliveryDocumentLines().get(0));
      Map<String, Object> printLabelData =
          LabelGenerator.generatePalletLabels(
              deliveryDocument.getDeliveryDocumentLines().get(0),
              mappedInstruction.getReceivedQuantity(),
              commonLabelDetails,
              printJob.getId(),
              httpHeaders,
              ReceivingUtils.getDCDateTime(dcTimeZone),
              true,
              deliveryDocument.getDeliveryNumber(),
              labelFormat);
      List<PrintLabelRequest> printLabelList =
          (List<PrintLabelRequest>) printLabelData.get(ReceivingConstants.PRINT_REQUEST_KEY);
      printRequests.addAll(printLabelList);

      Instruction updatedInstruction =
          updateInstructionData(
              mappedInstruction,
              labelTrackingId,
              mappedInstruction.getReceivedQuantity(),
              printLabelData,
              userId,
              slottingPalletResponse.getLocations().get(0).getLocation());
      modifiedInstructions.add(updatedInstruction);
    }
    rdcInstructionHelper.persistForCompleteInstruction(
        modifiedInstructions, modifiedContainers, modifiedContainerItems);
  }

  private CommonLabelDetails createCommonLabelDetails(
      String location, Integer slotSize, String labelTrackingId, Integer receiver) {
    return CommonLabelDetails.builder()
        .labelTrackingId(labelTrackingId)
        .slot(location)
        .slotSize(slotSize)
        .receiver(receiver)
        .build();
  }

  /**
   * @param completeMultipleInstructionDataInstructionMap
   * @param slottingPalletRequest
   * @param itemTrackingIdMap
   * @param itemInstructionMap
   * @param httpHeaders
   *     <p>This method prepares Slotting pallet request with the container details. In case of Non
   *     Atlas items with SlottingPalletRequestWithRdsPayLoad, we don't need to generate 18 digit
   *     LPN containers as slotting will invoke RDS to get the containers & slot details. In case of
   *     Atlas items, 18 digit LPNs will be generated and passed in the slotting request to get the
   *     slot.
   */
  private void setSlottingContainerDetails(
      Map<CompleteMultipleInstructionData, Instruction>
          completeMultipleInstructionDataInstructionMap,
      SlottingPalletRequest slottingPalletRequest,
      Map<String, String> itemTrackingIdMap,
      Map<String, Instruction> itemInstructionMap,
      HttpHeaders httpHeaders) {

    List<String> lpns = null;
    if (!(slottingPalletRequest instanceof SlottingPalletRequestWithRdsPayLoad)) {
      lpns = rdcLpnUtils.getLPNs(completeMultipleInstructionDataInstructionMap.size(), httpHeaders);
      LOGGER.info("Successfully retrieved 18 digit lpns with count : {} ", lpns.size());
    }
    int startIndex = 0;
    List<SlottingContainerItemDetails> containerItemsDetails = new ArrayList<>();
    List<SlottingContainerDetails> slottingContainerDetails = new ArrayList<>();

    for (Map.Entry<CompleteMultipleInstructionData, Instruction> entry :
        completeMultipleInstructionDataInstructionMap.entrySet()) {
      Instruction instruction = entry.getValue();
      SlottingContainerItemDetails slottingContainerItemDetails =
          new SlottingContainerItemDetails();
      DeliveryDocumentLine deliveryDocumentLine =
          gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class)
              .getDeliveryDocumentLines()
              .get(0);

      if (!CollectionUtils.isEmpty(lpns)) {
        String trackingId = lpns.get(startIndex++);
        slottingContainerItemDetails.setContainerTrackingId(trackingId);
        itemTrackingIdMap.put(String.valueOf(deliveryDocumentLine.getItemNbr()), trackingId);
      }

      slottingContainerItemDetails.setItemNbr(deliveryDocumentLine.getItemNbr());
      Integer receivedQtyInEaches =
          ReceivingUtils.conversionToEaches(
              instruction.getReceivedQuantity(),
              instruction.getProjectedReceiveQtyUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
      // set the quantity in Eaches
      slottingContainerItemDetails.setQty(receivedQtyInEaches);
      slottingContainerItemDetails.setQtyUom(ReceivingConstants.Uom.EACHES);
      slottingContainerItemDetails.setVnpkRatio(deliveryDocumentLine.getVendorPack());
      slottingContainerItemDetails.setWhpkRatio(deliveryDocumentLine.getWarehousePack());
      containerItemsDetails.add(slottingContainerItemDetails);

      itemInstructionMap.put(String.valueOf(deliveryDocumentLine.getItemNbr()), instruction);
    }
    SlottingContainerDetails containerDetails = new SlottingContainerDetails();

    // fetching the first completeMultipleInstructionData for locationName and locationSize since
    // all will have same info
    Optional<CompleteMultipleInstructionData> optionalInstructionData =
        completeMultipleInstructionDataInstructionMap.keySet().stream().findFirst();

    if (optionalInstructionData.isPresent()) {
      CompleteMultipleInstructionData instructionData = optionalInstructionData.get();
      if (Objects.nonNull(instructionData.getSlotDetails())) {
        if (Objects.nonNull(instructionData.getSlotDetails().getSlot())) {
          containerDetails.setLocationName(instructionData.getSlotDetails().getSlot());
        }
        if (Objects.nonNull(instructionData.getSlotDetails().getSlotSize())) {
          containerDetails.setLocationSize(instructionData.getSlotDetails().getSlotSize());
        }
      }
    }
    // set container item details list
    if (!CollectionUtils.isEmpty(lpns)) {
      containerDetails.setContainerTrackingId(lpns.get(0));
    }

    slottingContainerDetails.add(containerDetails);
    containerDetails.setContainerItemsDetails(containerItemsDetails);
    slottingPalletRequest.setReceivingMethod(RdcConstants.SLOTTING_SSTK_RECEIVING_METHOD);
    slottingPalletRequest.setMessageId(TenantContext.getCorrelationId());
    // set slotting container details
    slottingPalletRequest.setContainerDetails(slottingContainerDetails);
  }

  private void publishInstructionsForNonAtlasItem(
      HttpHeaders httpHeaders,
      Map<String, Instruction> poPoLineInstructionMap,
      ReceiveContainersResponseBody receiveContainersResponseBody) {
    for (ReceivedContainer receivedContainer : receiveContainersResponseBody.getReceived()) {
      Instruction mappedInstruction =
          poPoLineInstructionMap.get(getInstructionKey(receivedContainer));
      DeliveryDocument deliveryDocument =
          gson.fromJson(mappedInstruction.getDeliveryDocument(), DeliveryDocument.class);

      publishInstruction(
          mappedInstruction,
          httpHeaders,
          receivedContainer.getLabelTrackingId(),
          deliveryDocument.getDeliveryDocumentLines().get(0));
    }
  }

  private void publishInstructionsForAtlasItem(
      HttpHeaders httpHeaders,
      Map<String, Instruction> itemInstructionMap,
      Map<String, String> itemTrackingIdMap) {
    for (String item : itemInstructionMap.keySet()) {
      Instruction mappedInstruction = itemInstructionMap.get(item);
      DeliveryDocument deliveryDocument =
          gson.fromJson(mappedInstruction.getDeliveryDocument(), DeliveryDocument.class);

      publishInstruction(
          mappedInstruction,
          httpHeaders,
          itemTrackingIdMap.get(item),
          deliveryDocument.getDeliveryDocumentLines().get(0));
    }
  }

  /**
   * Make a request to RDS to receive split pallet containers
   *
   * @param httpHeaders
   * @param instructionSlotDetailsMap
   * @return ReceiveContainersResponseBody
   */
  private ReceiveContainersResponseBody receiveMultiContainersInRds(
      HttpHeaders httpHeaders, Map<Instruction, SlotDetails> instructionSlotDetailsMap) {

    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRdsService.getMultipleContainerLabelsFromRds(instructionSlotDetailsMap, httpHeaders);
    if (CollectionUtils.isNotEmpty(receiveContainersResponseBody.getErrors())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.NIM_RDS_MULTI_LABEL_GENERIC_ERROR,
          ReceivingConstants.NIM_RDS_MULTI_LABEL_GENERIC_ERROR);
    }
    return receiveContainersResponseBody;
  }

  /**
   * @param httpHeaders
   * @param printRequests
   * @param poPoLineInstructionMap
   * @param modifiedContainers
   * @param receivedContainers
   * @throws ReceivingException
   */
  private void persistInstructionData(
      HttpHeaders httpHeaders,
      List<Object> printRequests,
      Map<String, Instruction> poPoLineInstructionMap,
      List<Container> modifiedContainers,
      List<ReceivedContainer> receivedContainers)
      throws ReceivingException {
    List<Instruction> modifiedInstructions = new ArrayList<>();
    List<ContainerItem> modifiedContainerItems = new ArrayList<>();

    for (ReceivedContainer receivedContainer : receivedContainers) {
      Instruction mappedInstruction =
          poPoLineInstructionMap.get(getInstructionKey(receivedContainer));
      DeliveryDocument deliveryDocument =
          gson.fromJson(mappedInstruction.getDeliveryDocument(), DeliveryDocument.class);
      String labelTrackingId = receivedContainer.getLabelTrackingId();

      // update existing container with new tracking id that we got from RDS
      List<Container> containersByInstruction =
          containerService.getContainerByInstruction(mappedInstruction.getId());
      for (Container container : containersByInstruction) {
        container.setTrackingId(labelTrackingId);
        if (!ObjectUtils.isEmpty(receivedContainer.getDestinations().get(0).getSlot())) {
          Map<String, String> destination = new HashMap<>();
          destination.put(
              ReceivingConstants.SLOT, receivedContainer.getDestinations().get(0).getSlot());
          container.setDestination(destination);
        }
        for (ContainerItem containerItem : container.getContainerItems()) {
          containerItem.setTrackingId(labelTrackingId);
          modifiedContainerItems.add(containerItem);
        }
        modifiedContainers.add(container);
      }

      final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      PrintJob printJob =
          printJobService.createPrintJob(
              mappedInstruction.getDeliveryNumber(),
              mappedInstruction.getId(),
              new HashSet<>(Arrays.asList(labelTrackingId)),
              userId);

      String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());

      CommonLabelDetails commonLabelDetails =
          createCommonLabelDetails(
              receivedContainer.getDestinations().get(0).getSlot(),
              receivedContainer.getDestinations().get(0).getSlot_size(),
              labelTrackingId,
              receivedContainer.getReceiver());
      LabelFormat labelFormat =
          rdcReceivingUtils.getLabelFormatForPallet(
              deliveryDocument.getDeliveryDocumentLines().get(0));
      Map<String, Object> printLabelData =
          LabelGenerator.generatePalletLabels(
              deliveryDocument.getDeliveryDocumentLines().get(0),
              mappedInstruction.getReceivedQuantity(),
              commonLabelDetails,
              printJob.getId(),
              httpHeaders,
              ReceivingUtils.getDCDateTime(dcTimeZone),
              true,
              deliveryDocument.getDeliveryNumber(),
              labelFormat);
      List<PrintLabelRequest> printLabelList =
          (List<PrintLabelRequest>) printLabelData.get(ReceivingConstants.PRINT_REQUEST_KEY);
      printRequests.addAll(printLabelList);

      Instruction updatedInstruction =
          updateInstructionData(
              mappedInstruction,
              receivedContainer.getLabelTrackingId(),
              mappedInstruction.getReceivedQuantity(),
              printLabelData,
              userId,
              receivedContainer.getDestinations().get(0).getSlot());
      modifiedInstructions.add(updatedInstruction);
    }
    rdcInstructionHelper.persistForCompleteInstruction(
        modifiedInstructions, modifiedContainers, modifiedContainerItems);
  }

  /**
   * Publish Instruction to WFT on a kafka topic
   *
   * @param instruction
   * @param httpHeaders
   * @param labelTrackingId
   * @param deliveryDocumentLine
   */
  private void publishInstruction(
      Instruction instruction,
      HttpHeaders httpHeaders,
      String labelTrackingId,
      DeliveryDocumentLine deliveryDocumentLine) {

    LOGGER.info("Publishing instruction message to WFT for labelTrackingId:{}", labelTrackingId);
    instructionHelperService.publishInstruction(
        httpHeaders,
        rdcInstructionUtils.prepareInstructionMessage(
            instruction,
            instruction.getReceivedQuantity(),
            httpHeaders,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
  }

  /**
   * This method simply updates instruction table with the completed user, timestamp, received
   * quantity and container details.
   *
   * @param instruction
   * @param labelTrackingId
   * @param receivedQuantity
   * @param printLabelData
   * @param userId
   * @param slot
   * @return Instruction
   */
  private Instruction updateInstructionData(
      Instruction instruction,
      String labelTrackingId,
      Integer receivedQuantity,
      Map<String, Object> printLabelData,
      String userId,
      String slot) {

    instruction.setContainer(
        rdcContainerUtils.getContainerDetails(
            labelTrackingId,
            printLabelData,
            ContainerType.PALLET,
            RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU));

    LinkedTreeMap<String, Object> moveTreeMap =
        Objects.isNull(instruction.getMove()) ? new LinkedTreeMap() : instruction.getMove();
    moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_BY, userId);
    moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, slot);

    instruction.setMove(moveTreeMap);
    instruction.setReceivedQuantity(receivedQuantity);
    instruction.setLastChangeUserId(userId);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());

    return instruction;
  }

  private Map<Instruction, SlotDetails> getInstructionSlotDetailsMap(
      List<Instruction> instructions, List<CompleteMultipleInstructionData> instructionData) {
    Map<Long, Instruction> instructionMap = new HashMap<>();
    for (Instruction instruction : instructions) {
      instructionMap.put(instruction.getId(), instruction);
    }
    Map<Instruction, SlotDetails> instructionSlotDetailsMap = new HashMap<>();
    for (CompleteMultipleInstructionData completeMultipleInstructionData : instructionData) {
      Instruction instruction =
          instructionMap.get(completeMultipleInstructionData.getInstructionId());
      if (Objects.nonNull(instruction)) {
        instructionSlotDetailsMap.put(
            instruction, completeMultipleInstructionData.getSlotDetails());
      }
    }
    return instructionSlotDetailsMap;
  }

  private Map<CompleteMultipleInstructionData, Instruction> getInstructionDataAndInstructionMap(
      List<CompleteMultipleInstructionData> instructionDataList, List<Instruction> instructions) {
    Map<Long, CompleteMultipleInstructionData> instructionDataHashMap =
        instructionDataList
            .stream()
            .collect(
                Collectors.toMap(
                    CompleteMultipleInstructionData::getInstructionId,
                    instructionData -> instructionData,
                    (a, b) -> b));

    Map<CompleteMultipleInstructionData, Instruction> instructionDataAndInstructionMap =
        new HashMap<>();
    instructions.forEach(
        instruction -> {
          CompleteMultipleInstructionData instructionData =
              instructionDataHashMap.get(instruction.getId());
          if (Objects.nonNull(instructionData)) {
            instructionDataAndInstructionMap.put(instructionData, instruction);
          }
        });
    return instructionDataAndInstructionMap;
  }

  private String getInstructionKey(Instruction instruction) {
    StringBuilder key = new StringBuilder();
    key.append(instruction.getPurchaseReferenceNumber());
    key.append("-");
    key.append(instruction.getPurchaseReferenceLineNumber());

    return key.toString();
  }

  private String getInstructionKey(ReceivedContainer receivedContainer) {
    StringBuilder key = new StringBuilder();
    key.append(receivedContainer.getPoNumber());
    key.append("-");
    key.append(receivedContainer.getPoLine());

    return key.toString();
  }
}
