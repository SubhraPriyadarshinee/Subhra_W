package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.PENDING_RECEIVING_LPN;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.GdmLpnDetailsResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.config.WFSManagedConfig;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.utils.WFSUtility;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.logging.log4j2.util.Strings;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

public class WFSInstructionHelperService extends InstructionHelperService {
  private static final Logger log = LoggerFactory.getLogger(WFSInstructionHelperService.class);

  @ManagedConfiguration private WFSManagedConfig wfsManagedConfig;
  @Autowired GDMRestApiClient gdmRestApiClient;
  @Autowired Gson gson;

  public Map<String, String> mapFCNumberToFCName(Map<String, String> ctrDestination) {
    try {
      String fcNameMapping =
          JsonParser.parseString(wfsManagedConfig.getFcNameMapping())
              .getAsJsonObject()
              .get(ctrDestination.get(ReceivingConstants.BU_NUMBER))
              .getAsString();
      if (Objects.nonNull(fcNameMapping)) {
        ctrDestination.put(ReceivingConstants.FACILITY_NAME, fcNameMapping);
      }
    } catch (Exception jsonException) {
      log.error("FC Number {} does not exist", ctrDestination.get(ReceivingConstants.BU_NUMBER));
    }
    return ctrDestination;
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public Map<String, Object> createContainersAndReceiptsForWFSPos(
      InstructionRequest instructionRequestFromClient,
      HttpHeaders httpHeaders,
      String userId,
      Instruction instruction)
      throws ReceivingException {

    // persisting the containers
    Container container =
        containerService.processCreateContainersForWFSPO(
            instruction, instructionRequestFromClient, httpHeaders);

    // Completing the instruction
    instruction.setCompleteUserId(userId);
    instruction.setLastChangeUserId(userId);
    instruction.setCompleteTs(new Date());

    // label qty must be in EA, (which we are setting as containerItem qty
    updatePrintJobsInInstructionForWFS(
        instruction, container.getContainerItems().get(0).getQuantity());

    instruction = instructionPersisterService.saveInstruction(instruction);

    // persisting the receipts
    receiptService.createReceiptsFromInstructionForWFSPo(instructionRequestFromClient, userId);

    // Persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(instruction.getContainer().getTrackingId());
    printJobService.createPrintJob(
        instruction.getDeliveryNumber(),
        instruction.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instruction);
    map.put("container", container);
    return map;
  }

  public void updatePrintJobsInInstructionForWFS(
      Instruction instruction, @Nullable Integer labelQty) {
    final String LBL_QTY = "QTY";
    final String LBL_LABELTIMESTAMP = "LABELTIMESTAMP";
    final String LBL_FCNAME = "FCNAME";
    Gson gson = new Gson();

    Map<String, Object> containerLabel = instruction.getContainer().getCtrLabel();
    String printRequestsJson =
        gson.toJson(containerLabel.get(ReceivingConstants.PRINT_REQUEST_KEY));
    java.lang.reflect.Type PrintLabelRequestListType =
        new TypeToken<ArrayList<PrintLabelRequest>>() {}.getType();
    List<PrintLabelRequest> printRequests =
        gson.fromJson(printRequestsJson, PrintLabelRequestListType);

    boolean isLabelQTYUpdated = false;
    boolean isLabelTimestampUpdated = false;
    boolean isFcNameUpdated = false;

    String labelTimeStamp =
        ReceivingUtils.getLabelFormatDateAndTime(
            ReceivingUtils.getDCDateTime(configUtils.getDCTimeZone(TenantContext.getFacilityNum())),
            "MM/dd/yy HH:mm:ss");

    String fcNameValue = "";

    // adding a conditional check like below, because for breakPack flow there's no container-
    // destination
    // so, it will cause NPE in mapFCNumberToFCName method

    if (Objects.nonNull(instruction.getContainer())
        && Objects.nonNull(instruction.getContainer().getCtrDestination())) {
      Map<String, String> fcNumberToFcName =
          mapFCNumberToFCName(instruction.getContainer().getCtrDestination());
      if (fcNumberToFcName.containsKey(ReceivingConstants.FACILITY_NAME)) {
        fcNameValue = fcNumberToFcName.get(ReceivingConstants.FACILITY_NAME);
      }
    }

    for (PrintLabelRequest printLabelRequest : printRequests) {
      for (LabelData labelData : printLabelRequest.getData()) {
        if (LBL_QTY.equals(labelData.getKey()) && Objects.nonNull(labelQty)) {
          // only update QTY key, if passed in labelQty is non-null
          // if QTY key found, update quantity keys value, if it is empty or null
          if (Strings.isEmpty(labelData.getValue()) || Objects.isNull(labelData.getValue())) {
            labelData.setValue(String.valueOf(labelQty));
            isLabelQTYUpdated = true;
          }
        }
        if (LBL_LABELTIMESTAMP.equals(labelData.getKey())) {
          // LABELTIMESTAMP key found
          // TODO: refactor this code into WFS to use WFSConstants.WFS_LABEL_TIMESTAMP_PATTERN
          // constant;
          labelData.setValue(labelTimeStamp);
          isLabelTimestampUpdated = true;
        }
        if (LBL_FCNAME.equalsIgnoreCase(labelData.getKey())) {
          labelData.setValue(labelData.getValue());
          isFcNameUpdated = true;
        }
      }

      // if keys not updated, then must insert
      if (!isLabelQTYUpdated && Objects.nonNull(labelQty)) {
        printLabelRequest
            .getData()
            .add(LabelData.builder().key(LBL_QTY).value(String.valueOf(labelQty)).build());
      }

      if (!isLabelTimestampUpdated) {
        printLabelRequest
            .getData()
            .add(LabelData.builder().key(LBL_LABELTIMESTAMP).value(labelTimeStamp).build());
      }

      if (!isFcNameUpdated && StringUtils.isNotEmpty(fcNameValue)) {
        printLabelRequest
            .getData()
            .add(LabelData.builder().key(LBL_FCNAME).value(fcNameValue).build());
      }
    }
    java.lang.reflect.Type PrintRequestMapListType =
        new TypeToken<ArrayList<Map<String, Object>>>() {}.getType();
    List<Map<String, Object>> updatedPrintRequestsMap =
        gson.fromJson(gson.toJson(printRequests), PrintRequestMapListType);
    containerLabel.replace(ReceivingConstants.PRINT_REQUEST_KEY, updatedPrintRequestsMap);
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public Map<String, Object> createContainersAndReceiptsForWFSPosRIR(
      ReceiveInstructionRequest receiveInstructionRequestFromClient,
      HttpHeaders httpHeaders,
      String userId,
      Instruction instruction)
      throws ReceivingException {

    // persisting the containers
    Container container =
        containerService.processCreateContainersForWFSPOwithRIR(
            instruction, receiveInstructionRequestFromClient, httpHeaders);

    // label qty must be in ZA (as this is pallet flow), (containerItem qty is EA)
    DeliveryDocumentLine deliveryDocumentLine =
        receiveInstructionRequestFromClient.getDeliveryDocumentLines().get(0);
    Integer quantityForLabel =
        ReceivingUtils.conversionToVendorPack(
            container.getContainerItems().get(0).getQuantity(),
            container.getContainerItems().get(0).getQuantityUOM(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());
    updatePrintJobsInInstructionForWFS(instruction, quantityForLabel);

    // Completing the instruction
    instruction.setCompleteUserId(userId);
    instruction.setLastChangeUserId(userId);
    instruction.setCompleteTs(new Date());
    instruction = instructionPersisterService.saveInstruction(instruction);

    // persisting the receipts
    receiptService.createReceiptsFromInstructionForWFSPoRIR(
        instruction, receiveInstructionRequestFromClient, userId);

    // Persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(instruction.getContainer().getTrackingId());
    printJobService.createPrintJob(
        instruction.getDeliveryNumber(),
        instruction.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instruction);
    map.put("container", container);
    return map;
  }

  private Map<String, Object> getPrintJobWithAdditionalAttributesForWFS(
      Instruction instructionFromDB, String rotateDate, String dcTimeZone)
      throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
    labelData.removeIf(o -> o.containsValue("QTY"));
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate));
    Map<String, Object> labelTimestampMap = new HashMap<>();
    labelTimestampMap.put("key", ReceivingConstants.WFS_LABEL_TIMESTAMP);

    String date =
        ReceivingUtils.getLabelFormatDateAndTime(
            ReceivingUtils.getDCDateTime(dcTimeZone), "yyyy/MM/dd' 'HH:mm:ss");
    labelTimestampMap.put("value", date);
    labelData.add(labelTimestampMap);
    printRequest.put("data", labelData);
    printRequests.set(0, printRequest);
    printJob.put("printRequests", printRequests);
    return printJob;
  }

  public InstructionResponse prepareWFSInstructionResponse(
      Instruction instruction, Container consolidatedContainer, String dcTimeZone)
      throws ReceivingException {
    Map<String, Object> printJob = null;

    String rotateDate =
        (Boolean.TRUE.equals(instruction.getFirstExpiryFirstOut())
                && !Objects.isNull(
                    consolidatedContainer.getContainerItems().get(0).getRotateDate()))
            ? new SimpleDateFormat("MM/dd/yy")
                .format(consolidatedContainer.getContainerItems().get(0).getRotateDate())
            : "-";

    printJob = getPrintJobWithAdditionalAttributesForWFS(instruction, rotateDate, dcTimeZone);

    return new InstructionResponseImplNew(null, null, instruction, printJob);
  }

  void checkForPendingShelfContainers(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    if (configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED,
            false)
        && ReceivingType.UPC
            .getReceivingType()
            .equalsIgnoreCase(instructionRequest.getReceivingType())) {
      String upcNumber = instructionRequest.getUpcNumber();
      if (StringUtils.isEmpty(upcNumber)
          && !CollectionUtils.isEmpty(instructionRequest.getScannedDataList())) {
        upcNumber =
            WFSUtility.getGtinFromScannedDataMap(
                WFSUtility.getScannedDataMap(instructionRequest.getScannedDataList()));
        // todo: Refactor/Remove this if condition, as this Will happen iff receivingType is GS1,
        // where its always UPC here!
      }
      String pendingReReceivingContainerResponse =
          gdmRestApiClient.getReReceivingContainerResponseFromGDM(
              instructionRequest.getUpcNumber(), httpHeaders);

      if (StringUtils.isEmpty(pendingReReceivingContainerResponse)) {
        log.info("No Pending Receiving Container Response from GDM!");
        return;
      }
      GdmLpnDetailsResponse gdmLpnDetailsResponse =
          gson.fromJson(pendingReReceivingContainerResponse, GdmLpnDetailsResponse.class);
      List<Pack> packList = gdmLpnDetailsResponse.getPacks();
      if (!CollectionUtils.isEmpty(packList)) {
        // todo: check for other indicator for re-receiving flow!
        List<String> lpnList =
            packList
                .stream()
                .map(Pack::getPackNumber)
                .filter(packId -> packId.length() == 25)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(lpnList)) {
          return;
        }
        String error = String.format(PENDING_RECEIVING_LPN, lpnList.size(), upcNumber);
        log.error(error);
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(error)
                .errorKey(ExceptionCodes.PENDING_LPN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            .errorResponse(errorResponse)
            .build();
      }
    }
  }
}
