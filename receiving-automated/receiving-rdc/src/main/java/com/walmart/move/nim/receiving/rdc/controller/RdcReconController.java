package com.walmart.move.nim.receiving.rdc.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.SymAsrsSorterMapping;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("rdc/recon")
@Tag(
    name = "Recon Service to publish containers to external systems",
    description = "rdcReconController")
public class RdcReconController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcReconController.class);

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired ContainerTransformer containerTransformer;
  @Autowired ContainerService containerService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Autowired private JMSSorterPublisher jmsSorterPublisher;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;

  @Operation(
      summary = "Publish container information to Kafka for a given trackingId.",
      description = "This will return a 200")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @PostMapping(path = "/container/publish/kafka")
  public ResponseEntity<String> publishContainerToKafka(
      @RequestBody List<String> trackingIdList, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    if (!CollectionUtils.isEmpty(trackingIdList)) {
      for (String trackingId : trackingIdList) {
        Container container =
            containerPersisterService.getConsolidatedContainerForPublish(trackingId);
        if (ReceivingConstants.STATUS_BACKOUT.equalsIgnoreCase(container.getContainerStatus())) {
          return new ResponseEntity<>(
              String.format(ReceivingConstants.BACKOUT_CONTAINER_RESPONSE, trackingId),
              HttpStatus.BAD_REQUEST);
        }
        container = rdcContainerUtils.convertDateFormatForProDate(container, trackingId);
        List<ContainerDTO> containerDTOList =
            containerTransformer.transformList(Collections.singletonList(container));
        containerService.publishMultipleContainersToInventory(containerDTOList, httpHeaders);
      }
      return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
    }
    return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
  }

  @PostMapping(path = "/container/{trackingId}/receipts")
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
        required = true,
        example = "Example: JohnDoe",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "trackingId",
        required = true,
        example = "Example: a329870000000000000000001",
        description = "String",
        in = ParameterIn.PATH)
  })
  public String postReceiptGivenTrackingId(
      @PathVariable(value = "trackingId") String trackingId, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    Container container = containerPersisterService.getConsolidatedContainerForPublish(trackingId);
    List<ContainerDTO> containerDTOList =
        containerTransformer.transformList(Collections.singletonList(container));
    Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
    return gson.toJson(containerDTOList);
  }

  @Operation(
      summary = "Republish sorter divert messages to Athena for given trackingIds",
      description = "This will return a 200")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @PostMapping(path = "/publish/sorter/divert")
  public ResponseEntity<String> republishSorterDivertMessagesToAthena(
      @RequestBody List<String> trackingIds, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    if (!CollectionUtils.isEmpty(trackingIds)) {
      for (String trackingId : trackingIds) {
        Container container =
            containerPersisterService.getConsolidatedContainerForPublish(trackingId);
        String labelType = getSorterLabelType(container);
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false)) {
          kafkaAthenaPublisher.publishLabelToSorter(container, labelType);
        } else {
          jmsSorterPublisher.publishLabelToSorter(container, labelType);
        }
      }
      return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
    }
    return new ResponseEntity<>(ReceivingConstants.ERROR, HttpStatus.BAD_REQUEST);
  }

  /**
   * This method prepares Sorter Label type based on container
   *
   * @param container
   * @return
   */
  private String getSorterLabelType(Container container) {
    String labelType = "";
    if (container.isHasChildContainers()) {
      labelType = LabelType.PUT.name();
    } else if (appConfig
        .getValidSymAsrsAlignmentValues()
        .contains(container.getContainerItems().get(0).getAsrsAlignment())) {
      labelType =
          StringUtils.isNotBlank(rdcManagedConfig.getSymEligibleLabelType())
              ? rdcManagedConfig.getSymEligibleLabelType()
              : SymAsrsSorterMapping.valueOf(
                      container.getContainerItems().get(0).getAsrsAlignment())
                  .getSymLabelType();
    } else {
      labelType = LabelType.STORE.name();
    }
    return labelType;
  }

  @Operation(
      summary = "Publish container information to EI for given trackingIds",
      description = "This will return a 200")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @PostMapping(path = "/publish/ei")
  public ResponseEntity<String> publishContainersToEI(
      @RequestBody List<String> trackingIds,
      @RequestParam(value = "eventType", required = false) String eiEventType,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    if (!CollectionUtils.isEmpty(trackingIds)) {
      for (String trackingId : trackingIds) {
        Container container =
            containerPersisterService.getConsolidatedContainerForPublish(trackingId);
        if (StringUtils.isNotBlank(eiEventType)) {
          publishToEIbyEventType(container, eiEventType);
        } else {
          publishToEIByInventoryStatus(container);
        }
      }
      return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
    }
    return new ResponseEntity<>(ReceivingConstants.ERROR, HttpStatus.BAD_REQUEST);
  }

  @Operation(
      summary = "Publish putaway information to Kafka for a given trackingId.",
      description = "This will return a 200")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @PostMapping(path = "/container/publish/putaway")
  public ResponseEntity<String> republishPutawayMessage(
      @RequestBody List<String> trackingIds, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    if (CollectionUtils.isEmpty(trackingIds)) {
      return new ResponseEntity<>(ReceivingConstants.ERROR, HttpStatus.BAD_REQUEST);
    }
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  private String[] mapEventTypesForEI(String eventType) {
    String[] eiEvents = null;
    switch (eventType) {
      case ReceivingConstants.EI_DR_EVENT:
        eiEvents = ReceivingConstants.EI_DC_RECEIVING_EVENT;
        break;
      case ReceivingConstants.EI_DP_EVENT:
        eiEvents = ReceivingConstants.EI_DC_PICKED_EVENT;
        break;
      case ReceivingConstants.EI_DV_EVENT:
        eiEvents = ReceivingConstants.EI_DC_VOID_EVENT;
        break;
    }
    return eiEvents;
  }

  private void publishToEIByInventoryStatus(Container container) {
    String[] eiEvents;
    if (container.getContainerStatus().equals(ReceivingConstants.STATUS_BACKOUT)) {
      eiEvents = ReceivingConstants.EI_DC_VOID_EVENT;
    } else {
      eiEvents =
          container.getInventoryStatus().equals(InventoryStatus.PICKED.name())
              ? ReceivingConstants.EI_DC_RECEIVING_AND_PICK_EVENTS
              : ReceivingConstants.EI_DC_RECEIVING_EVENT;
    }
    rdcContainerUtils.publishContainerToEI(container, eiEvents);
  }

  private void publishToEIbyEventType(Container container, String eventType) {
    String[] eiEvents = mapEventTypesForEI(eventType);
    if (Objects.nonNull(eiEvents)) {
      rdcContainerUtils.publishContainerToEI(container, eiEvents);
    }
  }
}
