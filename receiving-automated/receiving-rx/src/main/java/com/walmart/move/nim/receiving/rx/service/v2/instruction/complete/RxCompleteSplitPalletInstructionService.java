
package com.walmart.move.nim.receiving.rx.service.v2.instruction.complete;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.UpdateGdmStatusV2Request;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.v2.CompleteMultipleInstructionService;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.*;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED;
import static java.util.Collections.singletonList;

@Service
@Slf4j
public class RxCompleteSplitPalletInstructionService implements CompleteMultipleInstructionService {
    private static final Logger LOG =
            LoggerFactory.getLogger(RxCompleteMultipleInstructionRequestHandler.class);

    @ManagedConfiguration
    private RxManagedConfig rxManagedConfig;
    @Autowired
    private InstructionPersisterService instructionPersisterService;
    @Autowired private EpcisService epcisService;
    @Autowired private RxContainerLabelBuilder containerLabelBuilder;
    @Autowired private Gson gson;
    @Autowired private NimRdsServiceImpl nimRdsServiceImpl;
    @Autowired private ContainerService containerService;
    @Autowired private RxSlottingServiceImpl rxSlottingServiceImpl;
    @Autowired private RxInstructionHelperService rxInstructionHelperService;
    @Autowired private RxInstructionValidator rxInstructionValidator;
    @Resource
    private TenantSpecificConfigReader tenantSpecificConfigReader;
    @Resource private CompleteInstructionOutboxService completeInstructionOutboxService;
    @Resource private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
    @Resource private RxInstructionService rxInstructionService;
    @Resource private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Autowired private RxLpnUtils rxLpnUtils;

    @Override
    public CompleteMultipleInstructionResponse complete(
            BulkCompleteInstructionRequest bulkCompleteInstructionRequest,
            HttpHeaders httpHeaders) throws ReceivingException {
        CompleteMultipleInstructionResponse completeMultipleInstructionResponse =
                new CompleteMultipleInstructionResponse();
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        boolean rollbackForException = false;
        ReceiveContainersResponseBody receiveContainersResponseBody = null;
        LOG.info("Inside Split Pallet Complete method.");
        try {
            List<PrintLabelRequest> printRequests = new ArrayList<>();
            List<Instruction> modifiedInstructionList = new ArrayList<>();
            List<Container> modifiedContainerList = new ArrayList<>();
            List<ContainerItem> modifiedContainerItemsList = new ArrayList<>();
            List<Instruction> validInstructionList = new ArrayList<>();

            for (CompleteMultipleInstructionData instructionData :
                    bulkCompleteInstructionRequest.getInstructionData()) {
                LOG.info("Processing instruction: {}", instructionData.getInstructionId());
                // Getting instruction from DB.
                Instruction instructionFromDB =
                        instructionPersisterService.getInstructionById(instructionData.getInstructionId());
                rxInstructionValidator.validateInstructionStatus(instructionFromDB);

                String instructionOwner =
                        StringUtils.isNotBlank(instructionFromDB.getLastChangeUserId())
                                ? instructionFromDB.getLastChangeUserId()
                                : instructionFromDB.getCreateUserId();
                rxInstructionValidator.verifyCompleteUser(instructionFromDB, instructionOwner, userId);

                validInstructionList.add(instructionFromDB);
                LOG.info("Valid instruction: {}", instructionData.getInstructionId());

            }
            List<String> lpnTrackingIds = rxLpnUtils.get18DigitLPNs(validInstructionList.size(), httpHeaders);
            LOG.info("Tracking Ids: {}", String.join(",", lpnTrackingIds));

            SlotDetails slotDetails =
                    bulkCompleteInstructionRequest.getInstructionData().get(0).getSlotDetails();
            String manualSlot = Objects.isNull(slotDetails) ? null : slotDetails.getSlot();

            // Auto/Manual SLot with moves for Pure OneAtlas
            String messageId = validInstructionList.get(0).getMessageId();
            int locationSize =
                    Objects.isNull(slotDetails) || Objects.isNull(slotDetails.getSlotSize()) ? 0 : slotDetails.getSlotSize();
            slotDetails =
                    findSlotFromSmartSlotting(
                            messageId,
                            locationSize,
                            lpnTrackingIds,
                            validInstructionList,
                            manualSlot,
                            httpHeaders);

            LOG.info("Slot Details: {}, {}", slotDetails.getSlot(), slotDetails.getSlotSize());
            PrintLabelData containerLabel = null;
            receiveContainersResponseBody =
                    mockRDSResponseObj(lpnTrackingIds, slotDetails, validInstructionList);

            Map<String, ReceivedContainer> poPoLineTrackingIdMap =
                    buildPoPoLineReceivedContainerMap(receiveContainersResponseBody);
            Map<String, Instruction> poPoLineInstructionMap =
                    buildPoPoLineInstructionMap(validInstructionList);
            boolean isOutboxInvIntegration =
                    tenantSpecificConfigReader.getConfiguredFeatureFlag(
                            TenantContext.getFacilityNum().toString(),
                            RxConstants.ENABLE_OUTBOX_INVENTORY_INTEGRATION,
                            false);
            boolean isGdmShipmentGetByScanV4Enabled =
                    tenantSpecificConfigReader.getConfiguredFeatureFlag(
                            TenantContext.getFacilityNum().toString(),
                            IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
                            false);
            for (Map.Entry<String, ReceivedContainer> poPoLineTrackingIdEntry :
                    poPoLineTrackingIdMap.entrySet()) {
                ReceivedContainer receivedContainer = poPoLineTrackingIdEntry.getValue();
                Instruction instruction = poPoLineInstructionMap.get(poPoLineTrackingIdEntry.getKey());
                String oldLabelTrackingId = instruction.getContainer().getTrackingId();
                String newLabelTrackingId =
                        StringUtils.isNumeric(receivedContainer.getLabelTrackingId())
                                ? Long.valueOf(receivedContainer.getLabelTrackingId()).toString()
                                : receivedContainer.getLabelTrackingId();
                Container parentContainer =
                        containerService.getContainerWithChildsByTrackingId(
                                instruction.getContainer().getTrackingId(), true);

                // SET PARENT and CHILD CONTAINERS
                setParentAndChildContainers(parentContainer, newLabelTrackingId, modifiedContainerItemsList);

                LOG.info("Set tracking Id {} of container for instruction: {}", newLabelTrackingId, instruction.getId());

                // SET TRACKING ID OF PARENT CONTAINER
                instruction.getContainer().setTrackingId(newLabelTrackingId);

                // SET PARENT ID OF CHILD CONTAINERS
                setParentIdOfChildContainers(instruction, oldLabelTrackingId, newLabelTrackingId);

                DeliveryDocumentLine deliveryDocumentLine =
                        InstructionUtils.getDeliveryDocumentLine(instruction);

                // SET MOVE
                setMoveForInstruction(httpHeaders, instruction, slotDetails, userId);

                containerLabel =
                        containerLabelBuilder.generateContainerLabel(
                                receivedContainer, deliveryDocumentLine, httpHeaders, parentContainer, instruction);

                LOG.info("Generate Label: {}", containerLabel);

                Map<String, Object> ctrLabelMap =
                        getNewCtrLabel(
                                containerLabel.getClientId(), containerLabel.getPrintRequests(), httpHeaders);
                instruction.getContainer().setCtrLabel(ctrLabelMap);
                instruction.setCompleteUserId(userId);
                instruction.setCompleteTs(new Date());
                containerService.setDistributionAndComplete(userId, parentContainer);
                containerService.enrichContainerForDcfin(
                        InstructionUtils.getDeliveryDocument(instruction), parentContainer);

                printRequests.addAll(containerLabel.getPrintRequests());
                modifiedInstructionList.add(instruction);
                modifiedContainerList.add(parentContainer);

                boolean isSerializedInfo =
                        null != deliveryDocumentLine.getAdditionalInfo()
                                && null != deliveryDocumentLine.getAdditionalInfo().getSerializedInfo();

                boolean isEpcisSmartReceivingEnabled = deliveryDocumentLine.getAdditionalInfo() != null
                        && deliveryDocumentLine.getAdditionalInfo().getIsEpcisSmartReceivingEnabled();

                LOG.info("isEpcisLongTermEnabled for instruction id: {} is {}", instruction.getId(), isEpcisSmartReceivingEnabled);

                // PERSIST DATA TO GDM
                persistToGDM(httpHeaders, isGdmShipmentGetByScanV4Enabled, isSerializedInfo,
                        isEpcisSmartReceivingEnabled, parentContainer, instruction, deliveryDocumentLine);

                // SEND DETAILS TO OUTBOX
                sendToOutbox(httpHeaders, isOutboxInvIntegration,
                        isSerializedInfo, isEpcisSmartReceivingEnabled,
                        parentContainer, instruction,
                        userId, slotDetails);
            }

            // non outbox
            if (!isOutboxInvIntegration) {
                rxInstructionHelperService.persist(
                        modifiedContainerList, modifiedContainerItemsList, modifiedInstructionList, userId);
                rxInstructionHelperService.publishContainers(modifiedContainerList);
                publishSerializedData(modifiedInstructionList, httpHeaders);
            }
            completeMultipleInstructionResponse.setPrintJob(
                    getNewCtrLabel(containerLabel.getClientId(), printRequests, httpHeaders));
            return completeMultipleInstructionResponse;
        } catch (ReceivingBadDataException rbde) {
            rollbackForException = true;
            LOG.error(
                    "{} {}",
                    ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                    ExceptionUtils.getStackTrace(rbde));
            throw rbde;
        } catch (ReceivingException receivingException) {
            rollbackForException = true;
            LOG.error(
                    "{} {}",
                    ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                    ExceptionUtils.getStackTrace(receivingException));
            throw RxUtils.convertToReceivingBadDataException(receivingException);
        } catch (Exception e) {
            rollbackForException = true;
            LOG.error(
                    "{} {}",
                    ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                    ExceptionUtils.getStackTrace(e));
            throw new ReceivingBadDataException(
                    ExceptionCodes.RECEIVING_INTERNAL_ERROR,
                    ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                    e);
        } finally {
            if (rxManagedConfig.isRollbackNimRdsReceiptsEnabled() && rollbackForException) {
                rollbackContainers(receiveContainersResponseBody, httpHeaders);
            }
        }
    }

    private void sendToOutbox(HttpHeaders httpHeaders, boolean isOutboxInvIntegration, boolean isSerializedInfo, boolean isEpcisLongTermEnabled, Container parentContainer, Instruction instruction, String userId, SlotDetails slotDetails) {
        // outbox and epcis
        if (isOutboxInvIntegration && (isSerializedInfo || isEpcisLongTermEnabled)) {
            completeInstructionOutboxService.outboxCompleteInstruction(
                    parentContainer, instruction, userId, slotDetails, httpHeaders);
        } else if (isOutboxInvIntegration) { // outbox and asn
            rxCompleteInstructionOutboxHandler.outboxCompleteInstructionAsnFlow(
                    parentContainer, instruction, userId, slotDetails, httpHeaders);
            // publish asn receive attp event
            publishSerializedData(singletonList(instruction), httpHeaders);
        }
    }

    private void persistToGDM(HttpHeaders httpHeaders,
                              boolean isGdmShipmentGetByScanV4Enabled,
                              boolean isSerializedInfo,
                              boolean isEpcisLongTermEnabled,
                              Container parentContainer,
                              Instruction instruction,
                              DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
        // call gdm to update pack status for flag isEpcisLongTermEnabled and isSerializedInfo only
        if (isGdmShipmentGetByScanV4Enabled && (isSerializedInfo || isEpcisLongTermEnabled)) {
            if (isEpcisLongTermEnabled) {
                HttpStatus response = rxDeliveryServiceImpl.updateEpcisReceivingStatus(
                        singletonList(new UpdateGdmStatusV2Request(
                                getGdmContainerIdFromContainer(parentContainer),
                                RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS)), httpHeaders);
        if (HttpStatus.OK != response) {
          log.error("[ESR] Error calling GDM updateEpcisReceivingStatus API");
          throw new ReceivingException(
              ReceivingException.GDM_UPDATE_STATUS_API_ERROR,
              response,
              ReceivingException.GDM_UPDATE_STATUS_API_ERROR_CODE,
              ReceivingException.GDM_UPDATE_STATUS_API_ERROR_HEADER);
        }

            } else {
                rxInstructionService.callGdmToUpdatePackStatus(
                        instruction, parentContainer, httpHeaders, deliveryDocumentLine);
            }

        }
    }

    private static void setMoveForInstruction(HttpHeaders httpHeaders, Instruction instruction, SlotDetails slotDetails, String userId) {
        LinkedTreeMap<String, Object> moveTreeMap = instruction.getMove();
        moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, slotDetails.getSlot());
        moveTreeMap.put(
                "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
        moveTreeMap.put("lastChangedOn", new Date());
        moveTreeMap.put("lastChangedBy", userId);
        instruction.setMove(moveTreeMap);
    }

    private static void setParentIdOfChildContainers(Instruction instruction, String oldLabelTrackingId, String newLabelTrackingId) {
        List<ContainerDetails> childContainers = instruction.getChildContainers();
        if (!CollectionUtils.isEmpty(childContainers)) {
            for (ContainerDetails childContainer : childContainers) {
                if (oldLabelTrackingId.equals(childContainer.getParentTrackingId())) {
                    childContainer.setParentTrackingId(newLabelTrackingId);
                }
            }
        }

        instruction.setChildContainers(childContainers);
    }

    private static void setParentAndChildContainers(Container parentContainer,
                                                    String newLabelTrackingId,
                                                    List<ContainerItem> modifiedContainerItemsList) {
        parentContainer.setTrackingId(newLabelTrackingId);
        parentContainer.setInventoryStatus(ReceivingConstants.AVAILABLE); // FIX FOR GLSMAV-43256
        ContainerItem parentContainerItem = parentContainer.getContainerItems().get(0);
        parentContainerItem.setTrackingId(newLabelTrackingId);
        modifiedContainerItemsList.add(parentContainerItem);
        Set<Container> containerList = new HashSet<>();
        parentContainer
                .getChildContainers()
                .forEach(
                        childContainer -> {
                            childContainer.setParentTrackingId(newLabelTrackingId);
                            childContainer.setInventoryStatus(
                                    ReceivingConstants.AVAILABLE); // FIX FOR GLSMAV-43256
                            containerList.add(childContainer);
                        });
        parentContainer.setChildContainers(containerList);
    }

    private void rollbackContainers(
            ReceiveContainersResponseBody receiveContainersResponseBody, HttpHeaders httpHeaders) {
        if (Objects.nonNull(receiveContainersResponseBody)) {
            for (ReceivedContainer receivedContainer : receiveContainersResponseBody.getReceived()) {
                try {
                    nimRdsServiceImpl.quantityChange(0, receivedContainer.getLabelTrackingId(), httpHeaders);
                } catch (Exception e) {
                    LOG.error(
                            "Exception occurred while rollback containers with NimRDS with trackingId : {}",
                            receivedContainer.getLabelTrackingId(),
                            e);
                }
            }
        }
    }

    private String getPoPOLineKey(ReceivedContainer receivedContainer) {
        StringBuilder poPOLineKeyBuilder = new StringBuilder();
        poPOLineKeyBuilder.append(receivedContainer.getPoNumber());
        poPOLineKeyBuilder.append(ReceivingConstants.DELIM_DASH);
        poPOLineKeyBuilder.append(receivedContainer.getPoLine());
        return poPOLineKeyBuilder.toString();
    }

    private Map<String, ReceivedContainer> buildPoPoLineReceivedContainerMap(
            ReceiveContainersResponseBody receiveContainersResponseBody) {
        Map<String, ReceivedContainer> poPoLineReceivedContainerMap = new HashMap<>();

        receiveContainersResponseBody
                .getReceived()
                .forEach(
                        (receivedContainer) -> {
                            poPoLineReceivedContainerMap.put(
                                    getPoPOLineKey(receivedContainer), receivedContainer);
                        });

        return poPoLineReceivedContainerMap;
    }

    private String getPoPOLineKey(Instruction instruction) {
        StringBuilder poPOLineKeyBuilder = new StringBuilder();
        poPOLineKeyBuilder.append(instruction.getPurchaseReferenceNumber());
        poPOLineKeyBuilder.append(ReceivingConstants.DELIM_DASH);
        poPOLineKeyBuilder.append(instruction.getPurchaseReferenceLineNumber());
        return poPOLineKeyBuilder.toString();
    }

    private Map<String, Instruction> buildPoPoLineInstructionMap(List<Instruction> instructionList) {
        Map<String, Instruction> poPoLineInstructionMap = new HashMap<>();
        instructionList.forEach(
                (instruction) -> {
                    poPoLineInstructionMap.put(getPoPOLineKey(instruction), instruction);
                });

        return poPoLineInstructionMap;
    }

    private void publishSerializedData(List<Instruction> instructions, HttpHeaders httpHeaders) {
        try {
            CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
            completeInstructionRequest.setPartialContainer(false);
            List<EpcisRequest> requests = new ArrayList<>();
            List<String> instructionIds = new ArrayList<>();
            for (Instruction instruction : instructions) {
                requests.addAll(
                        epcisService.epcisCapturePayload(instruction, completeInstructionRequest, httpHeaders));
                instructionIds.add(instruction.getId().toString());
            }
            epcisService.publishReceiveEvents(requests, httpHeaders, String.join("_", instructionIds));
        } catch (Exception e) {
            LOG.error("Error while publishing ReceiveEvents to EPCIS", e);
        }
    }

    private List<Map<String, Object>> transformToPrintLabelRequestMap(
            List<PrintLabelRequest> printRequests) {

        return gson.fromJson(gson.toJson(printRequests), List.class);
    }

  private String getGdmContainerIdFromContainer(Container container) {
    AtomicReference<String> receivingContainerType =
        new AtomicReference<>(container.getRcvgContainerType());
    AtomicReference<String> containerId = new AtomicReference<>();

    // FULL_PALLET
    if (RxConstants.ReceivingTypes.FULL_PALLET.equalsIgnoreCase(receivingContainerType.get())) {
      containerId.set((String) container.getContainerMiscInfo().get(RxConstants.GDM_CONTAINER_ID));
    } else {
      container
          .getChildContainers()
          .forEach(
              caseContainer -> {
                receivingContainerType.set(caseContainer.getRcvgContainerType());

                // CASE
                if (ReceivingConstants.CASE.equalsIgnoreCase(receivingContainerType.get())) {
                  containerId.set(
                      (String)
                          caseContainer.getContainerMiscInfo().get(RxConstants.GDM_CONTAINER_ID));
                }

                // PARTIAL_CASE
                if (RxConstants.ReceivingTypes.PARTIAL_CASE.equalsIgnoreCase(receivingContainerType.get())) {
                  Container unitContainer =
                      completeInstructionOutboxService.getContainerWithChildsByTrackingId(
                          caseContainer.getTrackingId());
                  unitContainer
                      .getChildContainers()
                      .forEach(
                          unit ->
                              containerId.set(
                                  (String)
                                      unit.getContainerMiscInfo()
                                          .get(RxConstants.GDM_CONTAINER_ID)));
                }
              });
    }

    return containerId.get();
  }

    private Map<String, Object> getNewCtrLabel(
            String clientId, List<PrintLabelRequest> printRequests, HttpHeaders httpHeaders) {
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

        Map<String, Object> ctrLabel = new HashMap<>();
        ctrLabel.put("clientId", clientId);
        ctrLabel.put("headers", headers);
        ctrLabel.put("printRequests", transformToPrintLabelRequestMap(printRequests));

        return ctrLabel;
    }

    public ReceiveContainersResponseBody mockRDSResponseObj(
            List<String> trackingIds, SlotDetails slotDetails, List<Instruction> validInstructions) {
        ReceiveContainersResponseBody receiveContainersResponseBody =
                new ReceiveContainersResponseBody();
        List<ReceivedContainer> received = new ArrayList<>();
        for (int i = 0; i < validInstructions.size(); i++) {
            Instruction instruction = validInstructions.get(i);
            ReceivedContainer receivedContainer = new ReceivedContainer();
            receivedContainer.setLabelTrackingId(trackingIds.get(i));
            receivedContainer.setPoNumber(instruction.getPurchaseReferenceNumber());
            receivedContainer.setPoLine(instruction.getPurchaseReferenceLineNumber());
            Destination destination = new Destination();
            destination.setSlot(slotDetails.getSlot());
            receivedContainer.setDestinations(Collections.singletonList(destination));
            received.add(receivedContainer);
        }
        receiveContainersResponseBody.setReceived(received);
        return receiveContainersResponseBody;
    }

    private SlotDetails findSlotFromSmartSlotting(
            String messageId,
            int locationSize,
            List<String> lpnTrackingIds,
            List<Instruction> validInstructionList,
            String manualSlot,
            HttpHeaders httpHeaders)
            throws ReceivingException {

        SlottingPalletResponse slottingRxPalletResponse =
                rxSlottingServiceImpl.acquireSlotMultiPallets(
                        messageId, locationSize, lpnTrackingIds, validInstructionList, manualSlot, httpHeaders);
        SlottingDivertLocations rxDivertLocations = slottingRxPalletResponse.getLocations().get(0);
        SlotDetails slotDetails = new SlotDetails();
        slotDetails.setSlot(rxDivertLocations.getLocation());
        slotDetails.setSlotSize((int) rxDivertLocations.getLocationSize());
        return slotDetails;
    }
}
