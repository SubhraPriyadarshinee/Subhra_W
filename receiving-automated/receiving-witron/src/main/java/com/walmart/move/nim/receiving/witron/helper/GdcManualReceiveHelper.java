package com.walmart.move.nim.receiving.witron.helper;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MECH_CONTAINER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.N;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_VALUE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_WAREHOUSE_AREA_CD;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SLOT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Y;
import static com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder.MECH_DC_INDICATOR;
import static com.walmart.move.nim.receiving.witron.common.GdcUtil.convertDateToUTCZone;
import static java.lang.String.valueOf;
import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveRequest;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveResponse;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.OverrideInfo;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.service.GdcSlottingServiceImpl;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class GdcManualReceiveHelper {

  private static final Logger log = LoggerFactory.getLogger(GdcManualReceiveHelper.class);

  @Autowired private GlsRestApiClient glsRestApiClient;
  @Autowired private GDCFlagReader gdcFlagReader;
  @Autowired private ContainerLabelBuilder containerLabelBuilder;
  @Autowired private GdcSlottingServiceImpl slottingService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;

  public boolean buildInstructionFromGls(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders requestHttpHeader,
      OverrideInfo overrideInfo)
      throws ReceivingException {
    if (gdcFlagReader.isGLSApiEnabled()) {
      GLSReceiveResponse glsReceiveResponse =
          receive(receiveInstructionRequest, instruction, requestHttpHeader, overrideInfo);
      log.info("Set container details and move from the GLS response");
      instruction.setContainer(
          buildInstructionContainer(glsReceiveResponse, instruction, requestHttpHeader));
      instruction.setMove(buildInstructionMove(glsReceiveResponse, requestHttpHeader));
      return true;
    }
    return false;
  }

  private ContainerDetails buildInstructionContainer(
      GLSReceiveResponse glsReceiveResponse,
      Instruction instruction,
      HttpHeaders requestHttpHeader) {
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId(glsReceiveResponse.getPalletTagId());
    containerDetails.setGlsWeight(glsReceiveResponse.getWeight());
    containerDetails.setGlsWeightUOM(glsReceiveResponse.getWeightUOM());
    containerDetails.setGlsTimestamp(glsReceiveResponse.getTimestamp());
    Map<String, Object> ctrLabel = new HashMap<>();
    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabelV2(
            glsReceiveResponse, deliveryDocumentLine, requestHttpHeader);
    ctrLabel.put("clientId", containerLabel.getClientId());
    ctrLabel.put(
        "printRequests", gson.fromJson(gson.toJson(containerLabel.getPrintRequests()), List.class));
    ctrLabel.put("headers", containerLabel.getHeaders());
    containerDetails.setCtrLabel(ctrLabel);
    return containerDetails;
  }

  private LinkedTreeMap<String, Object> buildInstructionMove(
      GLSReceiveResponse glsReceiveResponse, HttpHeaders httpHeaders) {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put("toLocation", glsReceiveResponse.getSlotId());
    moveTreeMap.put("correlationID", httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put("containerTag", glsReceiveResponse.getPalletTagId());
    moveTreeMap.put("lastChangedOn", glsReceiveResponse.getTimestamp());
    moveTreeMap.put("lastChangedBy", httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    return moveTreeMap;
  }

  public GLSReceiveResponse receive(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders requestHttpHeader,
      OverrideInfo overrideInfo)
      throws ReceivingException {
    GLSReceiveRequest request =
        constructReceive(receiveInstructionRequest, instruction, requestHttpHeader, overrideInfo);
    log.info("Calling GLS API to receive with request {}", request);
    return glsRestApiClient.receive(request, requestHttpHeader);
  }

  public GLSReceiveRequest constructReceive(
      ReceiveInstructionRequest recReq,
      Instruction instruction,
      HttpHeaders httpHeaders,
      OverrideInfo oi) {
    String subcenterId =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(),
            ReceivingConstants.SUBCENTER_ID_HEADER,
            GdcConstants.SUBCENTER_DEFAULT_VALUE);
    String orgUnitId = tenantSpecificConfigReader.getOrgUnitId();
    DeliveryDocumentLine docLine = InstructionUtils.getDeliveryDocumentLine(instruction);
    GLSReceiveRequest glsReceiveRequest =
        GLSReceiveRequest.builder()
            .createUser(valueOf(httpHeaders.getFirst(USER_ID_HEADER_KEY)))
            .deliveryNumber(instruction.getDeliveryNumber())
            .doorNumber(recReq.getDoorNumber())
            .hi(docLine.getPalletHigh())
            .ti(docLine.getPalletTie())
            .itemNumber(docLine.getItemNbr())
            .overrideInd(nonNull(oi) && oi.isOverrideOverage() ? Y : N)
            .overrideUserId(nonNull(oi) ? oi.getOverrideOverageManager() : null)
            .poLineNumber(docLine.getPurchaseReferenceLineNumber())
            .poNumber(docLine.getPurchaseReferenceNumber())
            .problemTagId(instruction.getProblemTagId())
            .quantity(recReq.getQuantity())
            .quantityUOM(recReq.getQuantityUOM())
            .receiveAsCorrection(instruction.getIsReceiveCorrection() ? Y : N)
            .rotateDate(convertDateToUTCZone(recReq.getRotateDate()))
            .vnpkWgtFmtCode(docLine.getAdditionalInfo().getWeightFormatTypeCode())
            .subcenterId(Integer.parseInt(subcenterId))
            .orgUnitId(nonNull(orgUnitId) ? Integer.parseInt(orgUnitId) : null)
            .freightBillQty(docLine.getFreightBillQty())
            .build();
    return glsReceiveRequest;
  }

  public void adjustOrCancel(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders requestHttpHeader,
      boolean isGlsCallSuccess) {
    try {

      log.info("isGlsCallSuccess : {}", isGlsCallSuccess);
      if (isGlsCallSuccess) {
        log.info("In the GLS VTR/Correction flow");
        GlsAdjustPayload glsAdjustPayload =
            glsRestApiClient.createGlsAdjustPayload(
                VTR,
                instruction.getContainer().getTrackingId(),
                0,
                receiveInstructionRequest.getQuantity(),
                valueOf(requestHttpHeader.getFirst(USER_ID_HEADER_KEY)));
        glsRestApiClient.adjustOrCancel(glsAdjustPayload, requestHttpHeader);
      }
    } catch (ReceivingException ex) {
      log.error(
          "GLS:Adjust splunk alert Adjust or Cancel failed with error code {} and message {}",
          ex.getErrorResponse().getErrorCode(),
          ex.getMessage());
      throw new ReceivingBadDataException(ex.getErrorResponse().getErrorCode(), ex.getMessage());
    }
  }

  public void buildInstructionFromSlotting(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders requestHttpHeader,
      UpdateInstructionRequest updateInstructionRequest) {
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);
    ContainerDetails container = instruction.getContainer();
    String containerId = container.getTrackingId();
    SlottingPalletResponse slottingPalletGDCResponse =
        slottingService.acquireSlotManualGdc(
            deliveryDocumentLine, receiveInstructionRequest, containerId, requestHttpHeader);
    updatePrintLabelWithSlotting(slottingPalletGDCResponse, instruction);
    if (slottingPalletGDCResponse.isMechContainer()) {
      Map<String, Object> containerMiscInfo = new HashMap<>();
      containerMiscInfo.put(IS_MECH_CONTAINER, true);
      containerMiscInfo.put(AUTOMATION_TYPE, slottingPalletGDCResponse.getAutomationType());
      container.setContainerMiscInfo(containerMiscInfo);
      if (CollectionUtils.isNotEmpty(slottingPalletGDCResponse.getLocations())) {
        SlottingDivertLocations slottingDivertLocations =
            slottingPalletGDCResponse.getLocations().get(0);
        if (ContainerType.TRAY
            .getText()
            .equalsIgnoreCase(slottingDivertLocations.getContainerType()))
          updateInstructionRequest.setContainerType(ContainerType.TRAY.getText());
      }
    }
    instruction.setMove(buildInstructionMove(slottingPalletGDCResponse, requestHttpHeader));
  }

  private void updatePrintLabelWithSlotting(
      SlottingPalletResponse slottingPalletGDCResponse, Instruction instruction) {
    Map<String, Object> printJob = instruction.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");

    // update Slot
    String slotId = "";
    if (CollectionUtils.isNotEmpty(slottingPalletGDCResponse.getLocations())) {
      slotId = slottingPalletGDCResponse.getLocations().get(0).getLocation();
      for (Map<String, Object> map : labelData) {
        if (SLOT.equalsIgnoreCase(String.valueOf(map.get(PRINT_KEY)))) {
          map.put(PRINT_VALUE, slotId);
          break;
        }
      }
    } else {
      log.warn(
          "slot=[{}] not found for container id=[{}] in slotting Response={}",
          slotId,
          instruction.getContainer().getContainerId(),
          slottingPalletGDCResponse);
    }

    // update for MechDc
    labelData
        .stream()
        .filter(
            map ->
                slottingPalletGDCResponse.isMechContainer()
                    && PRINT_WAREHOUSE_AREA_CD.equalsIgnoreCase(valueOf(map.get(PRINT_KEY))))
        .findFirst()
        .ifPresent(map -> map.put(PRINT_VALUE, (MECH_DC_INDICATOR + map.get(PRINT_VALUE))));
  }

  private LinkedTreeMap<String, Object> buildInstructionMove(
      SlottingPalletResponse slottingPalletGDCResponse, HttpHeaders httpHeaders) {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    if (CollectionUtils.isNotEmpty(slottingPalletGDCResponse.getLocations())) {
      moveTreeMap.put("toLocation", slottingPalletGDCResponse.getLocations().get(0).getLocation());
      moveTreeMap.put(
          "locationSize", slottingPalletGDCResponse.getLocations().get(0).getLocationSize());
      moveTreeMap.put("slotType", slottingPalletGDCResponse.getLocations().get(0).getSlotType());
      moveTreeMap.put(
          "primeLocation", slottingPalletGDCResponse.getLocations().get(0).getPrimeLocation());
      moveTreeMap.put(
          "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
      moveTreeMap.put(
          "containerTag", slottingPalletGDCResponse.getLocations().get(0).getContainerTrackingId());
      moveTreeMap.put("lastChangedOn", LocalDateTime.now().atOffset(ZoneOffset.UTC));
      moveTreeMap.put("lastChangedBy", httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    return moveTreeMap;
  }
}
