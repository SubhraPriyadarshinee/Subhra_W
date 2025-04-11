package com.walmart.move.nim.receiving.rx.service.v2.instruction.complete;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.FULL_PALLET;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.PARTIAL_CASE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.springframework.http.HttpStatus.OK;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.UpdateGdmStatusV2Request;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.*;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CompleteInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CompleteInstructionService implements com.walmart.move.nim.receiving.core.service.v2.CompleteInstructionService {

  @Resource private Gson gson;

  @Resource private RxInstructionService rxInstructionService;
  @Resource private ContainerService containerService;
  @Resource private RxLpnUtils rxLpnUtils;
  @Resource private RxContainerLabelBuilder rxContainerLabelBuilder;
  @Resource private RxFixitProblemService rxFixitProblemService;
  @Resource private RxDeliveryServiceImpl rxDeliveryServiceImpl;

  @Resource private CompleteInstructionDataValidator completeInstructionDataValidator;
  @Resource private CompleteInstructionOutboxService completeInstructionOutboxService;

  @Override
  public InstructionResponse completeInstruction(
      Long instructionId,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String lpnTrackingId;
    ReceiveContainersResponseBody receiveContainersResponseBody;

    try {
      TenantContext.get().setCompleteInstrStart(System.currentTimeMillis());

      // validate instruction and user
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      Instruction instruction =
          completeInstructionDataValidator.validateAndGetInstruction(instructionId, userId);

      // get deliveryDocument/deliveryDocumentLine
      DeliveryDocument deliveryDocument =
          gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      // fallback check
      boolean epcisSmartReceivingFlow =
          completeInstructionDataValidator.isEpcisSmartReceivingFlow(instruction, deliveryDocument);
      if (!epcisSmartReceivingFlow) { // bau flows
        log.info(
            "[ESR] Complete instruction request is NON EPCIS or epcisSmartReceiving flag is false, falling back to BAU flows");
        return rxInstructionService.completeInstruction(
            instructionId, completeInstructionRequest, httpHeaders);
      }

      // get trackingId from lpn
      lpnTrackingId = rxLpnUtils.get18DigitLPNs(1, httpHeaders).get(0);

      // call slotting for auto slot only
      callSmartSlottingForAutoSlot(
              completeInstructionRequest,
              instruction,
              deliveryDocumentLine,
              lpnTrackingId,
              httpHeaders);

      // receiveContainersResponseBody
      receiveContainersResponseBody =
          rxInstructionService.mockRDSResponseObj(
              lpnTrackingId, completeInstructionRequest.getSlotDetails());

      // updates
      Container palletContainer =
          updateInstructionAndPalletAndCaseContainers(instruction, lpnTrackingId, userId);

      // set moveMap
      ReceivedContainer receivedContainer = receiveContainersResponseBody.getReceived().get(0);
      constructAndSetMoveMap(instruction, receivedContainer, httpHeaders);

      // generate containerLabel
      generateAndSetContainerLabel(
          instruction, palletContainer, deliveryDocumentLine, receivedContainer, httpHeaders);

      // problem flows
      executeProblemFlows(instruction, deliveryDocumentLine, httpHeaders);

      // updateEpcisReceivingStatus
      updateEpcisReceivingStatus(palletContainer, httpHeaders);

      // enrichment
      RxInstructionService.enrichPayloadToPublishToSCT(instruction, palletContainer);
      containerService.enrichContainerForDcfin(deliveryDocument, palletContainer);

      // outboxCompleteInstruction
      TenantContext.get().setCompleteInstrPersistDBCallStart(System.currentTimeMillis());
      SlotDetails slotDetails = completeInstructionRequest.getSlotDetails();
      completeInstructionOutboxService.outboxCompleteInstruction(
          palletContainer, instruction, userId, slotDetails, httpHeaders);
      TenantContext.get().setCompleteInstrPersistDBCallEnd(System.currentTimeMillis());

      // convert quantities for UI
      RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(instruction);

      // construct final response
      InstructionResponse response;
      response =
          new InstructionResponseImplNew(
              null, null, instruction, instruction.getContainer().getCtrLabel());

      TenantContext.get().setCompleteInstrEnd(System.currentTimeMillis());

      // log times
      rxInstructionService.calculateAndLogElapsedTimeSummary();

      return response;

    } catch (ReceivingBadDataException | ReceivingException exception) {
      log.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(exception));
      throw exception;
    } catch (Exception e) {
      log.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          e);
    }
  }

  private void callSmartSlottingForAutoSlot(
      CompleteInstructionRequest completeInstructionRequest,
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      String lpnTrackingId,
      HttpHeaders httpHeaders) {
    boolean isManualSlot =
        Objects.nonNull(completeInstructionRequest.getSlotDetails())
            && Objects.nonNull(completeInstructionRequest.getSlotDetails().getSlot());
    if (!isManualSlot) {
      TenantContext.get().setCompleteInstrSlottingCallStart(System.currentTimeMillis());
      rxInstructionService.findSlotFromSmartSlotting(
          completeInstructionRequest,
          httpHeaders,
          instruction,
          deliveryDocumentLine,
          lpnTrackingId,
          true);
      TenantContext.get().setCompleteInstrSlottingCallEnd(System.currentTimeMillis());
    }
  }

  private Container updateInstructionAndPalletAndCaseContainers(
      Instruction instruction, String lpnTrackingId, String userId) throws ReceivingException {
    // update palletContainer
    Container palletContainer =
        containerService.getContainerWithChildsByTrackingId(
            instruction.getContainer().getTrackingId(), true);
    palletContainer.setTrackingId(lpnTrackingId);
    palletContainer.setInventoryStatus(AVAILABLE);

    // update palletContainerItem
    ContainerItem palletContainerItem = palletContainer.getContainerItems().get(0);
    palletContainerItem.setTrackingId(lpnTrackingId);

    containerService.setDistributionAndComplete(userId, palletContainer);

    // update caseContainers
    Set<Container> caseContainers =
        rxInstructionService.updateParentContainerTrackingId(palletContainer, lpnTrackingId);
    palletContainer.setChildContainers(caseContainers);

    // update instruction
    instruction.getContainer().setTrackingId(lpnTrackingId);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());

    // update instructionChildContainers
    String oldLabelTrackingId = instruction.getContainer().getTrackingId();
    List<ContainerDetails> instructionChildContainers = instruction.getChildContainers();
    if (!CollectionUtils.isEmpty(instructionChildContainers)) {
      for (ContainerDetails instructionChildContainer : instructionChildContainers) {
        if (oldLabelTrackingId.equals(instructionChildContainer.getParentTrackingId())) {
          instructionChildContainer.setParentTrackingId(lpnTrackingId);
        }
      }
    }
    instruction.setChildContainers(instructionChildContainers);

    return palletContainer;
  }

  private void constructAndSetMoveMap(
      Instruction instruction, ReceivedContainer receivedContainer, HttpHeaders httpHeaders) {
    LinkedTreeMap<String, Object> moveTreeMap = instruction.getMove();
    String moveCorrelationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, receivedContainer);
    moveTreeMap.put(MOVE_CORRELATION_ID, moveCorrelationId);
    moveTreeMap.put(MOVE_LAST_CHANGED_ON, new Date());
    moveTreeMap.put(MOVE_LAST_CHANGED_BY, userId);
    instruction.setMove(moveTreeMap);
  }

  private void generateAndSetContainerLabel(
      Instruction instruction,
      Container palletContainer,
      DeliveryDocumentLine deliveryDocumentLine,
      ReceivedContainer receivedContainer,
      HttpHeaders httpHeaders) {
    PrintLabelData containerLabel =
        rxContainerLabelBuilder.generateContainerLabel(
            receivedContainer, deliveryDocumentLine, httpHeaders, palletContainer, instruction);
    instruction
        .getContainer()
        .setCtrLabel(rxInstructionService.getNewCtrLabel(containerLabel, httpHeaders));
  }

  private void executeProblemFlows(
      Instruction instruction, DeliveryDocumentLine deliveryDocumentLine, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (StringUtils.isNotBlank(instruction.getProblemTagId())) {
      TenantContext.get().setCompleteInstrCompleteProblemCallStart(System.currentTimeMillis());
      rxFixitProblemService.completeProblem(instruction, httpHeaders, deliveryDocumentLine);
      rxInstructionService.publishDeliveryStatus(instruction.getDeliveryNumber(), httpHeaders);
      TenantContext.get().setCompleteInstrCompleteProblemCallEnd(System.currentTimeMillis());
    }
  }

  @SneakyThrows
  private void updateEpcisReceivingStatus(
      Container palletContainer, HttpHeaders httpHeaders) {
    List<UpdateGdmStatusV2Request> updateGdmStatusV2Request = new ArrayList<>();
    AtomicReference<String> receivingContainerType =
        new AtomicReference<>(palletContainer.getRcvgContainerType());

    // FULL_PALLET
    if (FULL_PALLET.equalsIgnoreCase(receivingContainerType.get())) {
      String containerId = (String) palletContainer.getContainerMiscInfo().get("gdmContainerId");
      updateGdmStatusV2Request.add(
          new UpdateGdmStatusV2Request(
              containerId, RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS));
    } else {
      palletContainer
          .getChildContainers()
          .forEach(
              caseContainer -> {
                receivingContainerType.set(caseContainer.getRcvgContainerType());

                // CASE
                if (CASE.equalsIgnoreCase(receivingContainerType.get())) {
                  String containerId =
                      (String) caseContainer.getContainerMiscInfo().get("gdmContainerId");
                  updateGdmStatusV2Request.add(
                      new UpdateGdmStatusV2Request(
                          containerId, RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS));
                }

                // PARTIAL_CASE
                if (PARTIAL_CASE.equalsIgnoreCase(receivingContainerType.get())) {
                  Container unitContainer =
                      completeInstructionOutboxService.getContainerWithChildsByTrackingId(
                          caseContainer.getTrackingId());
                  unitContainer
                      .getChildContainers()
                      .forEach(
                          unit -> {
                            String containerId =
                                (String) unit.getContainerMiscInfo().get("gdmContainerId");
                            updateGdmStatusV2Request.add(
                                new UpdateGdmStatusV2Request(
                                    containerId,
                                    RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS));
                          });
                }
              });
    }

    // update gdm status
    HttpStatus responseStatusCode =
        rxDeliveryServiceImpl.updateEpcisReceivingStatus(updateGdmStatusV2Request, httpHeaders);

    if (OK != responseStatusCode) {
      log.error("[ESR] Error calling GDM updateEpcisReceivingStatus API");
      throw new ReceivingException(
          GDM_UPDATE_STATUS_API_ERROR,
          responseStatusCode,
          GDM_UPDATE_STATUS_API_ERROR_CODE,
          GDM_UPDATE_STATUS_API_ERROR_HEADER);
    }
  }
}
