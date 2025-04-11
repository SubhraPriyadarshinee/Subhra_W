package com.walmart.move.nim.receiving.endgame.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WM_BASE_DIVISION_CODE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.model.SlottingDestinationDTO;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.*;
import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * *
 *
 * <p>This is the recon API for endgame
 *
 * @author sitakant
 */
@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("/endgame/test")
public class EndgameTestController {

  @Resource(name = EndgameConstants.ENDGAME_LABELING_SERVICE)
  private EndGameLabelingService endGameLabelingService;

  @Resource(name = EndgameConstants.ENDGAME_SLOTTING_SERVICE)
  private EndGameSlottingService endGameSlottingService;

  @Autowired ItemMDMService itemMDMService;

  /**
   * * Delete API to delete the delivery by deliveryNumber from receiving database. This will get
   * used in DIT
   *
   * @param deliveryNumber
   */
  @DeleteMapping("/deliveries/{deliveryNumber}")
  @Operation(
      summary = "Delete Delivery Details for a given Delivery Number",
      description = "Given the Delivery Number it will remove the delivery details from the db.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(name = "deliveryNumber", description = "Delivery Number", in = ParameterIn.PATH)
  })
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "delete Delivery")
  public void deleteDelivery(@PathVariable("deliveryNumber") String deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    // TODO like search needs to be changed as per new design
    endGameLabelingService.deleteTCLByDeliveryNumber(Long.valueOf(deliveryNumber));
    endGameLabelingService.deleteDeliveryMetaData(deliveryNumber);
  }

  // TODO: Update E2E that it's moved to recon and remove this API
  @GetMapping("/deliveries/{deliveryNumber}")
  @Operation(
      summary = "Get the label Details for a given Delivery Number",
      description = "Given the Delivery Number it will return the label details.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(name = "deliveryNumber", description = "Delivery Number", in = ParameterIn.PATH)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getDeliveryMetaData")
  public ResponseEntity<DeliveryMetaData> getDeliveryMetaData(
      @PathVariable("deliveryNumber") String deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    Optional<DeliveryMetaData> deliveryMetaData =
        endGameLabelingService.findDeliveryMetadataByDeliveryNumber(deliveryNumber);
    deliveryMetaData.orElseThrow(
        () ->
            new ReceivingDataNotFoundException(
                ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                String.format(EndgameConstants.METADATA_NOT_FOUND_ERROR_MSG, deliveryNumber)));
    return new ResponseEntity(deliveryMetaData, HttpStatus.OK);
  }

  @GetMapping("/tcls/{tcl}")
  @Operation(
      summary = "Get the label Details for a given label number",
      description = "Given the label Number it will return the label details.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(name = "tcl", description = "label Number", in = ParameterIn.PATH)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getTCLDetails")
  public Map<String, String> getTCLDetails(@PathVariable("tcl") String tcl) {
    Map<String, String> responseMap = new HashMap<>();
    Optional<PreLabelData> optionalPreLabelData = endGameLabelingService.findByTcl(tcl);
    PreLabelData preLabelData =
        optionalPreLabelData.orElseThrow(
            () ->
                new ReceivingDataNotFoundException(
                    ExceptionCodes.TCL_NOT_FOUND,
                    String.format(EndgameConstants.TCL_NOT_FOUND_ERROR_MSG, tcl)));
    responseMap.put(EndgameConstants.TCL.toLowerCase(), preLabelData.getTcl());
    responseMap.put(EndgameConstants.STATUS, preLabelData.getStatus().getStatus());
    return responseMap;
  }

  @PutMapping("/destinations/{upc}")
  @Operation(
      summary = "Update Slotting Destination",
      description = "Given the upc and Slotting Destination details it will update in the DB.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(name = "upc", description = "UPC Number", in = ParameterIn.PATH)
  })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Slotting Destination to be updated",
      required = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.REST,
      flow = "updateSlottingDestination")
  public SlottingDestination updateSlottingDestination(
      @PathVariable("upc") String caseUPC,
      @RequestBody SlottingDestinationDTO slottingDestinationDTO) {
    ReceivingUtils.validateApiAccessibility();
    SlottingDestination slottingDestination =
        ReceivingUtils.convertValue(
            slottingDestinationDTO, new TypeReference<SlottingDestination>() {});
    return endGameSlottingService.updateDestination(caseUPC, slottingDestination);
  }

  @PostMapping("/items")
  @Operation(
      summary = "Get Item MDM Details",
      description = "Given the item numbers get the item Details.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
  })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "List of item numbers",
      required = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getMDMData")
  public Map<String, Object> getMDMData(
      @RequestBody Set<Long> itemNumbers,
      @RequestParam(
              value = "baseDivisionCode",
              required = false,
              defaultValue = WM_BASE_DIVISION_CODE)
          String baseDivCode) {
    return itemMDMService.retrieveItemDetails(
        itemNumbers, ReceivingUtils.getHeaders(), baseDivCode, Boolean.FALSE, Boolean.FALSE);
  }
}
