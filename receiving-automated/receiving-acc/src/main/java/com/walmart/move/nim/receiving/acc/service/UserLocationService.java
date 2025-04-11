package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.model.UserLocationRequest;
import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.acc.repositories.UserLocationRepo;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class UserLocationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserLocationService.class);

  @ManagedConfiguration private AppConfig appConfig;

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Autowired private LocationService locationService;

  @Autowired private ACLService aclService;

  @Autowired private UpdateUserLocationService updateUserLocationService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private UserLocationRepo userLocationRepo;

  @Autowired private LabelDataService labelDataService;

  @Resource(name = ReceivingConstants.DELIVERY_EVENT_PERSISTER_SERVICE)
  private DeliveryEventPersisterService deliveryEventPersisterService;

  @Autowired private PreLabelDeliveryService genericPreLabelDeliveryEventProcessor;

  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;

  public LocationInfo getLocationInfo(
      UserLocationRequest userLocationRequest, HttpHeaders headers) {

    final boolean isKotlinEnabled = isKotlinEnabled(headers, tenantSpecificConfigReader);
    String locationId = userLocationRequest.getLocationId();
    LOGGER.info("Fetching location info for locationId:{}", locationId);
    LocationInfo locationInfo = locationService.getDoorInfo(locationId, isKotlinEnabled);

    if (Objects.isNull(userLocationRequest.getIsOverflowReceiving())
        || !userLocationRequest.getIsOverflowReceiving()) {
      createUserLocationMapping(locationId, locationInfo, headers);

      if (Objects.nonNull(locationInfo)) {
        DeliveryDetails deliveryDetails;
        Long deliveryNumber = userLocationRequest.getDeliveryNumber();
        String url = deliveryDocumentHelper.getUrlForFetchingDelivery(deliveryNumber);
        try {
          LOGGER.debug("FALLBACK: Fetching delivery info for URL:{}", url);
          DeliveryService deliveryService =
              tenantSpecificConfigReader.getConfiguredInstance(
                  TenantContext.getFacilityNum().toString(),
                  ReceivingConstants.DELIVERY_SERVICE_KEY,
                  DeliveryService.class);
          deliveryDetails = deliveryService.getDeliveryDetails(url, deliveryNumber);

          if (tenantSpecificConfigReader.isFeatureFlagEnabled(
                  ACCConstants.PREGEN_FALLBACK_ENABLED, TenantContext.getFacilityNum())
              && ACCUtils.checkIfLocationIsEitherOnlineOrFloorLine(locationInfo)) {
            preLabelGenerationFallbackCheck(deliveryNumber, deliveryDetails, headers);
          }
          // Check if FBQ is present in each delivery line
          if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ACCConstants.ENABLE_DELIVERY_LINE_LEVEL_FBQ_CHECK)) {
            LOGGER.info("FALLBACK: Validating line level FBQ for delivery {}", deliveryNumber);
            checkForValidFBQInDelivery(deliveryDetails);
          }
          if (accManagedConfig.isFullyDaConEnabled()
              && deliveryDocumentHelper.isTrailerFullyDaCon(deliveryDetails)) {
            LOGGER.info("Scanned delivery {} contains only DA Con items", deliveryNumber);
            locationInfo.setIsFullyDaCon(Boolean.TRUE);
          }
        } catch (ReceivingException receivingException) {
          LOGGER.error("FALLBACK: Can't fetch delivery: {} details.", deliveryNumber);
        }

        // delivery link should be done only after a potential republish of labels via fallback
        if (locationInfo.getIsOnline().equals(Boolean.TRUE)) {
          LOGGER.info("Publishing delivery and location message to ACL");
          DeliveryAndLocationMessage message = new DeliveryAndLocationMessage();
          message.setDeliveryNbr(userLocationRequest.getDeliveryNumber().toString());
          message.setLocation(locationId);
          message.setUserId(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

          tenantSpecificConfigReader
              .getConfiguredInstance(
                  String.valueOf(TenantContext.getFacilityNum()),
                  ReceivingConstants.DELIVERY_LINK_SERVICE,
                  DeliveryLinkService.class)
              .updateDeliveryLink(Collections.singletonList(message), headers);
        }
      }
    }
    return locationInfo;
  }

  private void createUserLocationMapping(
      String locationId, LocationInfo locationInfo, HttpHeaders headers) {
    String siteId = headers.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    siteId = siteId.length() < 5 ? "0" + siteId : siteId;
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY) + ".s" + siteId;

    updateUserLocationService.updateUserLocation(userId, locationId, locationInfo, headers);
  }

  public LocationInfo createUserLocationMappingForFloorLine(
      String scannedLocation, HttpHeaders httpHeaders) {
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    LOGGER.info("Fetching location info for locationId:{}", scannedLocation);
    LocationInfo locationInfo = locationService.getDoorInfo(scannedLocation, isKotlinEnabled);
    validateScannedLocationAtFloorLineOrWorkStation(locationInfo, scannedLocation);
    createUserLocationMapping(scannedLocation, locationInfo, httpHeaders);
    return locationInfo;
  }

  private void validateScannedLocationAtFloorLineOrWorkStation(
      LocationInfo locationInfo, String scannedLocation) {
    if (locationInfo.getIsOnline().equals(Boolean.FALSE)
        && (locationInfo.getIsFloorLine().equals(Boolean.FALSE)
            && StringUtils.isEmpty(locationInfo.getMappedParentAclLocation()))) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_FLOOR_LINE_LOCATION,
          ReceivingConstants.INVALID_LOCATION_FOR_FLOOR_LINE,
          scannedLocation);
    } else if (Boolean.TRUE.equals(locationInfo.getIsMultiManifestLocation())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_FLOOR_LINE_LOCATION_MULTI_MANIFEST,
          ReceivingConstants.INVALID_LOCATION_FOR_FLOOR_LINE_MUTLI_MANIFEST,
          scannedLocation);
    }
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<String> getUsersAtLocation(String locationId, boolean excludeFacilityNum) {
    List<UserLocation> allByLocationId;
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_MANIFEST)) {
      allByLocationId =
          userLocationRepo.findAllByLocationIdOrParentLocationId(locationId, locationId);
    } else {
      locationId = org.apache.commons.lang3.StringUtils.upperCase(locationId);
      allByLocationId = userLocationRepo.findAllByLocationId(locationId);
    }
    if (Objects.isNull(allByLocationId) || allByLocationId.isEmpty()) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.USER_NOT_FOUND,
          String.format(ACCConstants.USER_NOT_FOUND_MSG, locationId));
    }

    List<String> users = new ArrayList<>();
    allByLocationId.forEach(
        userLocation -> {
          if (!StringUtils.isEmpty(userLocation.getUserId())) {
            if (excludeFacilityNum) {
              users.add(userLocation.getUserId().split("\\.s")[0]);
            } else {
              users.add(userLocation.getUserId());
            }
          }
        });

    if (users.isEmpty()) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.USER_NOT_FOUND,
          String.format(ACCConstants.USER_NOT_FOUND_MSG, locationId));
    }
    return users;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByLocation(String locationId) {
    locationId = org.apache.commons.lang3.StringUtils.upperCase(locationId);
    userLocationRepo.deleteByLocationId(locationId);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<UserLocation> getByUser(String user) {
    return userLocationRepo.findAllByUserId(user);
  }

  /**
   * This method checks for pre label generation, and triggers a fallback mechanism if no labels are
   * found. Republish check is also performed by checking ACL.
   *
   * @param deliveryNumber Delivery number of the door which was scanned
   */
  private void preLabelGenerationFallbackCheck(
      Long deliveryNumber, DeliveryDetails deliveryDetails, HttpHeaders headers) {
    Integer numberOfLabelsForDelivery = labelDataService.countByDeliveryNumber(deliveryNumber);
    if (!isLabelGenFallbackApplicable(deliveryDetails)) {
      LOGGER.info(
          "Not triggering label generation fallback for delivery: {} as it contains only pass-through freights.",
          deliveryNumber);
      return;
    }
    if (Objects.isNull(numberOfLabelsForDelivery)
        || numberOfLabelsForDelivery.equals(0)
        || shouldTriggerPreLabelGenerationFallback(deliveryDetails, deliveryNumber)) {
      // Entering fallback mechanism
      LOGGER.info(
          "FALLBACK: All or some labels were not found for delivery {}. Performing fallback label generation",
          deliveryNumber);
      String url = deliveryDocumentHelper.getUrlForFetchingDelivery(deliveryNumber);
      DeliveryUpdateMessage deliveryUpdateMessage =
          ACCUtils.getDeliveryUpdateMessageForFallback(deliveryNumber, url);
      CompletableFuture<Void> futureResult = triggerFallbackLabelGeneration(deliveryUpdateMessage);
      try {
        // This will block the main thread, but response should be given only after successful PLG
        futureResult.get(accManagedConfig.getFallbackGenerationTimeout(), TimeUnit.SECONDS);
      } catch (ExecutionException | TimeoutException e) {
        LOGGER.error("FALLBACK: Exception occurred during fallback. {}", e.getClass().getName());
        throw new ReceivingInternalException(
            ExceptionCodes.UNABLE_TO_FALLBACK,
            String.format(ReceivingConstants.FALLBACK_GENERATION_ERROR, deliveryNumber));
      } catch (InterruptedException ie) {
        LOGGER.error("FALLBACK: Thread interrupted. Details : {}", ie.getMessage());
        Thread.currentThread().interrupt();
        throw new ReceivingInternalException(
            ExceptionCodes.UNABLE_TO_FALLBACK,
            String.format(ReceivingConstants.LOCATION_SERVICE_DOWN, deliveryNumber));
      }
      return;
    }
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ACCConstants.ENABLE_REPUBLISH_FALLBACK_CHECK))
      preLabelGenRepublishFallbackCheck(deliveryNumber, headers, numberOfLabelsForDelivery);
  }

  private void checkForValidFBQInDelivery(DeliveryDetails deliveryDetails) {
    if (!CollectionUtils.isEmpty(deliveryDetails.getDeliveryDocuments())) {
      boolean hasValidDeliveryDocumentLine =
          deliveryDetails
              .getDeliveryDocuments()
              .stream()
              .filter(
                  deliveryDocument ->
                      !CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines()))
              .anyMatch(
                  deliveryDocument ->
                      (deliveryDocument
                              .getDeliveryDocumentLines()
                              .stream()
                              .anyMatch(
                                  deliveryDocumentLine ->
                                      Optional.ofNullable(deliveryDocumentLine.getFreightBillQty())
                                              .orElse(0)
                                          > 0))
                          || !Boolean.TRUE.equals(deliveryDocument.getImportInd()));

      if (!hasValidDeliveryDocumentLine) {
        LOGGER.error(
            "No valid FBQ Qty lines available in deliveryNumber {} deliveryDetails: {}",
            deliveryDetails.getDeliveryNumber(),
            deliveryDetails);
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_DELIVERY_LINE_LEVEL_FBQ,
            ExceptionDescriptionConstants.INVALID_DELIVERY_LINE_LEVEL_FBQ_DESC,
            String.valueOf(deliveryDetails.getDeliveryNumber()));
      }
    }
  }

  private boolean shouldTriggerPreLabelGenerationFallback(
      DeliveryDetails deliveryDetails, Long deliveryNumber) {
    boolean triggerFallback = false;
    int countOfInProgressAndPendingEvents =
        deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(deliveryNumber);
    if (countOfInProgressAndPendingEvents > 0) {
      LOGGER.info(
          "FALLBACK: Triggering fallback because there are {} delivery events still being processed",
          countOfInProgressAndPendingEvents);
      return true;
    }
    List<LabelData> labelDataForDelivery =
        labelDataService.getLabelDataByDeliveryNumber(deliveryNumber);
    triggerFallback =
        deliveryDetails
            .getDeliveryDocuments()
            .stream()
            .filter(document -> !ReceivingUtils.isPassThroughFreight(document))
            .filter(
                deliveryDocument ->
                    !POStatus.CNCL.name().equals(deliveryDocument.getPurchaseReferenceStatus()))
            .anyMatch(
                deliveryDocument ->
                    deliveryDocument
                        .getDeliveryDocumentLines()
                        .stream()
                        .filter(
                            deliveryDocumentLine ->
                                !(POLineStatus.CANCELLED
                                        .name()
                                        .equalsIgnoreCase(
                                            deliveryDocumentLine.getPurchaseReferenceLineStatus())
                                    || (!Objects.isNull(deliveryDocumentLine.getOperationalInfo())
                                        && POLineStatus.REJECTED
                                            .name()
                                            .equalsIgnoreCase(
                                                deliveryDocumentLine
                                                    .getOperationalInfo()
                                                    .getState()))))
                        .anyMatch(
                            deliveryDocumentLine -> {
                              List<LabelData> labelDataByPoPoLine =
                                  labelDataForDelivery
                                      .stream()
                                      .filter(
                                          labelData ->
                                              labelData
                                                      .getPurchaseReferenceNumber()
                                                      .equals(
                                                          deliveryDocumentLine
                                                              .getPurchaseReferenceNumber())
                                                  && labelData
                                                      .getPurchaseReferenceLineNumber()
                                                      .equals(
                                                          deliveryDocumentLine
                                                              .getPurchaseReferenceLineNumber()))
                                      .collect(Collectors.toList());
                              // If labelDataByPoPoLine is empty, it means that a new line was
                              // added for which labels are not available
                              if (CollectionUtils.isEmpty(labelDataByPoPoLine)) {
                                LOGGER.info(
                                    "FALLBACK: Triggering fallback since labels were not found for PO: {}, Line: {}",
                                    deliveryDocumentLine.getPurchaseReferenceNumber(),
                                    deliveryDocumentLine.getPurchaseReferenceLineNumber());
                                return true;
                              }
                              // If labelDataByPoPoLine is not empty, proceed to check that the
                              // has it's conveyability and qty has remained same
                              else {
                                return labelDataByPoPoLine
                                    .stream()
                                    .anyMatch(
                                        labelData ->
                                            // Below check channel flip
                                            ((InstructionUtils.isDAConFreight(
                                                        deliveryDocumentLine.getIsConveyable(),
                                                        deliveryDocumentLine.getPurchaseRefType(),
                                                        deliveryDocumentLine
                                                            .getActiveChannelMethods())
                                                    ^ labelData.getIsDAConveyable())
                                                // Below checks quantity for only the ordered
                                                // labels
                                                || (labelData.getIsDAConveyable()
                                                    && LabelType.ORDERED.equals(
                                                        labelData.getLabelType())
                                                    && !labelData
                                                        .getLpnsCount()
                                                        .equals(
                                                            deliveryDocumentLine
                                                                .getExpectedQty()))));
                              }
                            }));

    if (!triggerFallback) {
      LOGGER.info(
          "FALLBACK: No issues found with the labels for delivery: {}. Not triggering fallback",
          deliveryNumber);
    }
    return triggerFallback;
  }

  private void preLabelGenRepublishFallbackCheck(
      Long deliveryNumber, HttpHeaders headers, Integer numberOfLabelsForDelivery) {
    ACLLabelCount aclLabelCount = aclService.fetchLabelsFromACL(deliveryNumber);
    // TODO: Add count match logic in next fix
    if (Objects.isNull(aclLabelCount)) {
      LOGGER.info(
          "FALLBACK: Republishing labels to ACL since no labels were found for delivery {}",
          deliveryNumber);
      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.LABEL_GENERATOR_SERVICE,
              GenericLabelGeneratorService.class)
          .publishACLLabelDataForDelivery(deliveryNumber, headers);
    }
  }

  private CompletableFuture<Void> triggerFallbackLabelGeneration(
      DeliveryUpdateMessage deliveryUpdateMessage) {
    Integer facilityNum = TenantContext.getFacilityNum();
    String facilityCountryCode = TenantContext.getFacilityCountryCode();
    String correlationId = TenantContext.getCorrelationId();

    return CompletableFuture.runAsync(
        () -> {
          ReceivingUtils.setTenantContext(
              facilityNum.toString(),
              facilityCountryCode,
              correlationId,
              this.getClass().getName());
          genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
        });
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public UserLocation getLastUserAtLocation(String locationId) {
    locationId = org.apache.commons.lang3.StringUtils.upperCase(locationId);
    return userLocationRepo.findFirstByLocationIdOrderByCreateTsDesc(locationId);
  }

  private boolean isLabelGenFallbackApplicable(DeliveryDetails deliveryDetails) {
    List<DeliveryDocument> filteredPos =
        deliveryDetails
            .getDeliveryDocuments()
            .stream()
            .filter(document -> !ReceivingUtils.isPassThroughFreight(document))
            .collect(Collectors.toList());
    return !CollectionUtils.isEmpty(filteredPos);
  }
}
