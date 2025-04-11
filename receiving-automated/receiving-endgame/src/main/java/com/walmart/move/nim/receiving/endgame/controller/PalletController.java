package com.walmart.move.nim.receiving.endgame.controller;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingForwardedException;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataRepository;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("/endgame/pallet")
public class PalletController {

  private static final Logger LOGGER = LoggerFactory.getLogger(PalletController.class);

  @Autowired private EndGameLabelingService endGameLabelingService;

  @Autowired private MovePublisher movePublisher;

  @Autowired private Gson gson;

  @Autowired private PreLabelDataRepository preLabelDataRepository;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  private EndGameDeliveryService endGameDeliveryService;

  @Resource(name = EndgameConstants.ENDGAME_RECEIVING_SERVICE)
  private EndGameReceivingService endGameReceivingService;

  @PostMapping("/v2")
  @Operation(
      summary = "Create container(s) & receipt(s) of one or more pallets",
      description = "Returns status code 201 on success")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "WMT-CorrelationId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  @Timed(
      name = "Endgame-MultiplePalletReceive",
      level1 = "uwms-receiving",
      level2 = "Endgame-MultiplePalletReceive")
  @ExceptionCounted(
      name = "Endgame-MultiplePalletReceiving-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-MultiplePalletReceiving-Exception")
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "MultiplePalletReceive")
  public ResponseEntity<PalletResponse> receiveMultiplePallet(
      @RequestBody @Valid MultiplePalletReceivingRequest multiplePalletReceivingRequest) {
    List<PreLabelData> preLabelDataList = new ArrayList<>();
    LOGGER.info(
        "Multiple Pallet Receiving : Payload = {} ", gson.toJson(multiplePalletReceivingRequest));
    List<ContainerDTO> containers = multiplePalletReceivingRequest.getContainers();

    endGameDeliveryService.publishNonSortWorkingEvent(
        containers.get(0).getDeliveryNumber(),
        multiplePalletReceivingRequest.getExtraAttributes().getDeliveryStatus());

    List<String> trackingIdList =
        containers
            .stream()
            .map(ContainerDTO::getTrackingId)
            .filter(trackingId -> !ObjectUtils.isEmpty(trackingId))
            .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(trackingIdList)) {
      /*
       tpl is scanned
      */
      trackingIdList.forEach(
          trackingId -> {
            endGameReceivingService.verifyContainerReceivable(trackingId);
            Optional<PreLabelData> optionalPreLabelData =
                endGameLabelingService.findByTcl(trackingId);
            if (optionalPreLabelData.isPresent()) {
              preLabelDataList.add(optionalPreLabelData.get());
            }
          });
      preLabelDataList.forEach(preLabelData -> preLabelData.setStatus(LabelStatus.SCANNED));

    } else {
      /*
       direct upc is scanned
      */
      Set<String> tplSet = endGameLabelingService.generateTCL(containers.size(), LabelType.TPL);
      LOGGER.info("New TPL Identifiers generated [tplSet={}]", tplSet);

      AtomicInteger atomicInteger = new AtomicInteger();
      Map<Integer, String> tplMap =
          tplSet
              .stream()
              .collect(Collectors.toMap(tpl -> atomicInteger.getAndIncrement(), tpl -> tpl));

      IntStream.range(0, containers.size())
          .forEach(
              index -> {
                ContainerItem containerItem =
                    endGameReceivingService.retrieveContainerItemFromContainer(
                        containers.get(index));
                containers.get(index).setTrackingId(tplMap.get(index));
                containerItem.setTrackingId(tplMap.get(index));
                preLabelDataList.add(createPreLabel(containers.get(index)));
              });
    }

    PalletSlotResponse palletSlotResponse =
        endGameReceivingService.getSlotLocations(
            containers, multiplePalletReceivingRequest.getExtraAttributes());
    verifyPalletSlotResponse(palletSlotResponse);

    /*
     Create container & receipts and generate label response
    */
    LabelResponse labelResponse =
        endGameReceivingService.receiveMultiplePallets(
            containers,
            palletSlotResponse,
            multiplePalletReceivingRequest.getExtraAttributes().getLegacyType());
    endGameLabelingService.saveLabels(preLabelDataList);

    PalletResponse palletResponse =
        PalletResponse.builder()
            .labelResponse(labelResponse)
            .moveDestinations(palletSlotResponse.getLocations())
            .container(containers)
            .build();

    return new ResponseEntity<>(palletResponse, HttpStatus.CREATED);
  }

  private void verifyPalletSlotResponse(PalletSlotResponse palletSlotResponse) {
    Optional<SlotLocation> optionalSlotLocation =
        palletSlotResponse
            .getLocations()
            .stream()
            .filter(
                slotLocation -> slotLocation.getType().equalsIgnoreCase(ReceivingConstants.ERROR))
            .findFirst();
    if (optionalSlotLocation.isPresent()) {
      LOGGER.error(
          EndgameConstants.LOG_SLOTTING_ERROR_RESPONSE,
          ReceivingUtils.stringfyJson(palletSlotResponse));
      SlotLocation slotLocation = optionalSlotLocation.get();
      slotLocation.setErrorCode(slotLocation.getCode());
      throw new ReceivingForwardedException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingUtils.stringfyJson(slotLocation),
          EndgameConstants.NO_SLOTS_AVAILABLE);
    }
  }

  /**
   * Get the deliveryNumber and trackingId from container and set the values for PreLabelData status
   * is SCANNED and type is TPL
   *
   * @param container
   */
  private PreLabelData createPreLabel(ContainerDTO container) {
    return PreLabelData.builder()
        .status(LabelStatus.SCANNED)
        .deliveryNumber(container.getDeliveryNumber())
        .tcl(container.getTrackingId())
        .type(LabelType.TPL)
        .build();
  }
}
