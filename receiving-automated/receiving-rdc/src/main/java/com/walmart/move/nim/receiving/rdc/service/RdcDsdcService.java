package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ContainerItemService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.PrintJobService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RdcDsdcService {
  private static final Logger logger = LoggerFactory.getLogger(RdcDsdcService.class);

  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private NimRDSRestApiClient nimRDSRestApiClient;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private PrintJobService printJobService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ContainerService containerService;
  @Autowired private ContainerItemService containerItemService;
  @Autowired private Gson gson;

  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "createInstructionForDSDCReceiving")
  @Transactional(rollbackFor = ReceivingBadDataException.class)
  @InjectTenantFilter
  public InstructionResponse createInstructionForDSDCReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    boolean isWksAndScanToPrintEnabled =
        RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(
            instructionRequest.getFeatureType());
    InstructionResponse instructionResponse = null;
    String labelTrackingId = null;
    int receiveQty;
    List<Container> containerList = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();

    logger.info("Create Instruction for DSDC Pack, SSCC: {}", instructionRequest.getSscc());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    // Receive DSDC pack in RDS
    logger.info("Invoke RDS to receive the container for SSCC: {}", instructionRequest.getSscc());

    DsdcReceiveRequest dsdcReceiveRequest =
        nimRdsService.getDsdcReceiveContainerRequest(instructionRequest, httpHeaders);

    TenantContext.get().setDaCaseReceivingRdsCallStart(System.currentTimeMillis());
    DsdcReceiveResponse dsdcReceiveResponse =
        nimRdsService.receiveDsdcContainerInRds(dsdcReceiveRequest, httpHeaders);
    TenantContext.get().setDaCaseReceivingRdsCallEnd(System.currentTimeMillis());

    // Pack Not found error - Chances of ASN Receiving SSCC scanned, continue to GDM Vendor
    // Based receiving.
    if (!isPackNumberAvailableInRds(
        dsdcReceiveRequest, dsdcReceiveResponse, isWksAndScanToPrintEnabled)) {
      return instructionResponse;
    }

    if (Objects.equals(dsdcReceiveResponse.getAuditFlag(), RdcConstants.DSDC_AUDIT_FLAG)) {
      labelTrackingId = instructionRequest.getSscc();
      receiveQty = RdcConstants.RDC_DSDC_AUDIT_CASE_RECEIVE_QTY;
    } else {
      labelTrackingId = dsdcReceiveResponse.getLabel_bar_code();
      receiveQty = RdcConstants.RDC_DA_CASE_RECEIVE_QTY;
    }

    Instruction instruction =
        createInstruction(instructionRequest, dsdcReceiveResponse, httpHeaders);

    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    PrintJob printJob =
        printJobService.createPrintJob(
            Long.valueOf(instructionRequest.getDeliveryNumber()),
            instruction.getId(),
            new HashSet<>(Collections.singletonList(labelTrackingId)),
            userId);

    Map<String, Object> printLabelData =
        LabelGenerator.generateDsdcPackLabel(
            dsdcReceiveRequest,
            dsdcReceiveResponse,
            printJob.getId(),
            httpHeaders,
            ReceivingUtils.getDCDateTime(dcTimeZone));
    /**
     * 1. DSDC Store Label - Received Pack - Persist all information instruction, Container,
     * ContainerItem 2. DSDC Audit Label - The pack needs to be audited, not received with this flow
     * - Persist only the instruction with Rcv_qty as 0.
     */
    if (!Objects.equals(dsdcReceiveResponse.getAuditFlag(), RdcConstants.DSDC_AUDIT_FLAG)) {
      Container container = containerService.findByTrackingId(labelTrackingId);
      List<ContainerItem> containerItemList =
          containerItemService.findByTrackingId(labelTrackingId);
      ContainerItem containerItem =
          CollectionUtils.isNotEmpty(containerItemList)
              ? containerItemList.get(0)
              : new ContainerItem();
      containerItems =
          rdcContainerUtils.buildContainerItem(
              dsdcReceiveResponse, labelTrackingId, receiveQty, containerItem);
      container =
          rdcContainerUtils.buildContainer(
              instructionRequest, dsdcReceiveResponse, instruction.getId(), userId, container);
      containerList.add(container);
      container.setContainerItems(containerItems);
    }
    updateInstruction(instruction, labelTrackingId, userId, printLabelData);

    TenantContext.get().setDsdcCaseReceivingDataPersistStart(System.currentTimeMillis());
    rdcReceivingUtils.persistReceivedContainerDetails(
        Collections.singletonList(instruction),
        containerList,
        containerItems,
        Collections.emptyList(),
        Collections.emptyList());
    TenantContext.get().setDsdcCaseReceivingDataPersistEnd(System.currentTimeMillis());

    TenantContext.get().setDsdcCaseReceivingPublishWFTCallStart(System.currentTimeMillis());
    rdcReceivingUtils.publishInstruction(
        instruction, new DeliveryDocumentLine(), receiveQty, httpHeaders, false);
    TenantContext.get().setDaCaseReceivingPublishWFTCallEnd(System.currentTimeMillis());
    instructionResponse =
        new InstructionResponseImplNew(
            instructionRequest.getDeliveryStatus(),
            Collections.emptyList(),
            instruction,
            printLabelData);
    TenantContext.get().setDsdcCaseReceivingEnd(System.currentTimeMillis());
    return instructionResponse;
  }

  private void updateInstruction(
      Instruction instruction,
      String labelTrackingId,
      String userId,
      Map<String, Object> printLabelData) {
    instruction.setReceivedQuantity(
        StringUtils.isEmpty(labelTrackingId)
            ? RdcConstants.RDC_DSDC_AUDIT_CASE_RECEIVE_QTY
            : RdcConstants.RDC_DA_CASE_RECEIVE_QTY);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    instruction.setContainer(
        rdcContainerUtils.getContainerDetails(
            labelTrackingId,
            printLabelData,
            ContainerType.CASE,
            RdcConstants.OUTBOUND_CHANNEL_METHOD_CROSSDOCK));
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
  }

  private Instruction createInstruction(
      InstructionRequest instructionRequest,
      DsdcReceiveResponse dsdcReceiveResponse,
      HttpHeaders httpHeaders) {
    Instruction instruction = new Instruction();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
    instruction.setPurchaseReferenceNumber(dsdcReceiveResponse.getPo_nbr());
    instruction.setMessageId(instructionRequest.getMessageId());
    instruction.setPoDcNumber(TenantContext.getFacilityNum().toString());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));

    // persist problem tag information in instruction table
    if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      instruction.setProblemTagId(instructionRequest.getProblemTagId());
    }

    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      instruction.setSsccNumber(instructionRequest.getSscc());
    }
    instruction.setPrintChildContainerLabels(false);
    if (Objects.equals(dsdcReceiveResponse.getAuditFlag(), (RdcConstants.DSDC_AUDIT_FLAG))) {
      instruction.setInstructionMsg(RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionMsg());
      instruction.setInstructionCode(RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionCode());
    } else {
      instruction.setInstructionMsg(RdcInstructionType.DSDC_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(RdcInstructionType.DSDC_RECEIVING.getInstructionCode());
      instruction.setProjectedReceiveQty(RdcConstants.RDC_DA_CASE_RECEIVE_QTY);
      instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    }
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setActivityName(WFTInstruction.DA.getActivityName());
    return instructionPersisterService.saveInstruction(instruction);
  }

  /**
   * This method validates if the scanned packNumber is available is RDS or not. In case of
   * WorkStationOrScanToPrint mode, block user upon error message returned from RDS. In case of
   * Mobile Qty receiving mode, we need to block user if the scanned ASN was found in RDS but not in
   * receivable status. RDS returns following error messages if the scanned SSCC matches in RDS but
   * not able to receive in RDS. In this case we need to block the user by throwing this message to
   * Client [1. Receiver is not active, 2. ASN has been queued for offline receiving, 3. PO XXXX
   * Line YY is canceled, 4. ASN has already been received]. if the scanned ASN not found in RDS
   * (i.e. ASN information was not found), in case of Mobile Qty receiving mode do not throw any
   * error message instead return false which means the Pack number is not available in RDS to fall
   * back to validate in GDM to check the scanned SSCC could be specific to SSTK Vendor ASN
   * receiving or not. If DSDC ASN is available in GDM, upon receiving DSDC packs in Mobile Qty
   * Receiving mode in Legacy RDS (Atlas Non pilot vendors) we do not need to fall back to GDM
   * instead we can blocke the user with the RDS error message for DSDC ASN NOT FOUND use case in
   * RDS
   *
   * @param dsdcReceiveRequest
   * @param dsdcReceiveResponse
   * @param isWksAndScanToPrintEnabled
   * @return
   */
  private boolean isPackNumberAvailableInRds(
      DsdcReceiveRequest dsdcReceiveRequest,
      DsdcReceiveResponse dsdcReceiveResponse,
      boolean isWksAndScanToPrintEnabled) {
    boolean isPackNumberAvailableInRds = true;
    boolean isDsdcSsccPackAvailableInGdm =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false);
    if (StringUtils.isNotEmpty(dsdcReceiveResponse.getErrorCode())) {
      String errorMessage = RdcUtils.getparsedDsdcErrorMessage(dsdcReceiveResponse);
      if (isWksAndScanToPrintEnabled
          || (!isDsdcSsccPackAvailableInGdm
              && !RdcConstants.DSDC_ASN_NOT_FOUND_IN_RDS.equals(errorMessage))
          || isDsdcSsccPackAvailableInGdm) {
        throw new ReceivingBadDataException(
            ExceptionCodes.RDS_DSDC_RECEIVE_VALIDATION_ERROR,
            String.format(
                ReceivingException.RDS_DSDC_RECEIVE_VALIDATION_ERROR_MSG,
                dsdcReceiveRequest.getPack_nbr(),
                errorMessage),
            dsdcReceiveRequest.getPack_nbr(),
            errorMessage);
      }
      isPackNumberAvailableInRds = false;
    }
    return isPackNumberAvailableInRds;
  }
}
