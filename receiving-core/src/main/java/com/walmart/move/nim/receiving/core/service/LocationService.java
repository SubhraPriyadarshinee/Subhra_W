package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.LOCATIONS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.LOCATION_RESP_IS_EMPTY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROPERTIES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RESPONSE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SUCCESS_TUPLES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VALIDATE_LOCATION_INFO_ENABLED;
import static java.util.Objects.nonNull;

import com.google.gson.*;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaWftLocationMessagePublisher;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.LocationSummary;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Service for interacting with location Note: This might change if floor line mapping contract has
 * changes
 *
 * @author sks0013
 */
@Service
public class LocationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocationService.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private JmsPublisher jmsPublisher;

  @Resource(name = "retryableRestConnector")
  private RestConnector simpleRestConnector;

  @Autowired private KafkaWftLocationMessagePublisher kafkaWftLocationMessagePublisher;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * Returns the status of the door or floor line if it exists.
   *
   * @param locationId The location name.
   * @return online, offline or the value of the mapped floor line
   */
  public LocationInfo getDoorInfo(String locationId, Boolean isKotlinEnabled) {

    JsonObject locationResponseJsonObject = getLocationInfoAsJsonObject(locationId);
    return getLocationInfo(locationId, locationResponseJsonObject, isKotlinEnabled);
  }

  public LocationInfo getLocationInfo(
      String locationId, JsonObject locationResponseJsonObject, Boolean isKotlinEnabled) {
    String id = "";
    String locationType = "";
    String locationSubType = "";
    String locationName = "";
    JsonArray locationsArray =
        locationResponseJsonObject
            .get(ReceivingConstants.SUCCESS_TUPLES)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(ReceivingConstants.RESPONSE)
            .getAsJsonObject()
            .get(ReceivingConstants.LOCATIONS)
            .getAsJsonArray();

    if (locationsArray.size() == 0) {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ENABLE_EMPTY_LOCATION_RESPONSE_ERROR)) {
        LOGGER.info("Location response is empty or null for scanned location: {}", locationId);
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.LOCATION_RESP_IS_EMPTY,
            String.format(ReceivingConstants.LOCATION_RESP_IS_EMPTY, locationId),
            locationId);
      } else {
        // Need to decide by default, offline is chosen or exception should be thrown
        return LocationInfo.builder()
            .isOnline(Boolean.FALSE)
            .mappedFloorLine(null)
            .isFloorLine(Boolean.FALSE)
            .isMultiManifestLocation(Boolean.FALSE)
            .locationId(locationId)
            .mappedParentAclLocation(null)
            .build();
      }
    }

    JsonObject locations = locationsArray.get(0).getAsJsonObject();

    if (locations.size() > 0 && isKotlinEnabled) {
      id =
          Objects.nonNull(locations.get(ReceivingConstants.ID))
              ? locations.get(ReceivingConstants.ID).getAsString()
              : null;
      locationType =
          Objects.nonNull(locations.get(ReceivingConstants.TYPE))
              ? locations
                  .get(ReceivingConstants.TYPE)
                  .getAsJsonObject()
                  .get(ReceivingConstants.VALUE)
                  .getAsString()
              : null;
      locationSubType =
          Objects.nonNull(locations.get(ReceivingConstants.SUB_TYPE))
              ? locations
                  .get(ReceivingConstants.SUB_TYPE)
                  .getAsJsonObject()
                  .get(ReceivingConstants.VALUE)
                  .getAsString()
              : null;
      locationName =
          Objects.nonNull(locations.get(ReceivingConstants.NAME))
              ? locations.get(ReceivingConstants.NAME).getAsString()
              : null;
    }
    // tells if it is an ACL door
    if (Objects.nonNull(locations.get(ReceivingConstants.ATTRIBUTES))) {
      JsonArray attributes = locations.get(ReceivingConstants.ATTRIBUTES).getAsJsonArray();
      for (JsonElement attribute : attributes) {
        if ((ReceivingConstants.ACL.equals(
                attribute.getAsJsonObject().get(ReceivingConstants.KEY).getAsString())
            && (ReceivingConstants.Y.equals(
                attribute.getAsJsonObject().get(ReceivingConstants.VALUE).getAsString()))))
          return LocationInfo.builder()
              .isOnline(Boolean.TRUE)
              .mappedFloorLine(null)
              .isFloorLine(Boolean.FALSE)
              .isMultiManifestLocation(isMultiManifestLocation(locations))
              .mappedPbylArea(getMappedPbylArea(locations))
              .locationId(id)
              .locationName(locationName)
              .locationType(locationType)
              .locationSubType(locationSubType)
              .mappedParentAclLocation(getMappedParentAclLocation(locations))
              .build();
      }
    }

    // tells if a door has floor line attached
    if (Objects.nonNull(locations.get(ReceivingConstants.TAGS))) {
      JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
      for (JsonElement tag : tags) {
        if (ReceivingConstants.MAPPED_FLOOR_LINE.equals(
            tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString()))
          return LocationInfo.builder()
              .isOnline(Boolean.FALSE)
              .mappedFloorLine(
                  tag.getAsJsonObject()
                      .get(ReceivingConstants.TAG_NAMES)
                      .getAsJsonArray()
                      .get(0)
                      .getAsString())
              .isFloorLine(Boolean.FALSE)
              .isMultiManifestLocation(isMultiManifestLocation(locations))
              .mappedPbylArea(getMappedPbylArea(locations))
              .locationId(id)
              .locationName(locationName)
              .locationType(locationType)
              .locationSubType(locationSubType)
              .mappedParentAclLocation(getMappedParentAclLocation(locations))
              .build();
      }
    }

    // tells if the location is floor line
    if (Objects.nonNull(locations.get(ReceivingConstants.TYPE))) {
      JsonObject type = locations.get(ReceivingConstants.TYPE).getAsJsonObject();
      if (Objects.nonNull(type.get(ReceivingConstants.VALUE))
          && !StringUtils.isEmpty(type.get(ReceivingConstants.VALUE).getAsString())
          && type.get(ReceivingConstants.VALUE)
              .getAsString()
              .equals(ReceivingConstants.FLOORLINE)) {
        return LocationInfo.builder()
            .isOnline(Boolean.FALSE)
            .mappedFloorLine(isKotlinEnabled ? null : locationId)
            .isFloorLine(Boolean.TRUE)
            .isMultiManifestLocation(isMultiManifestLocation(locations))
            .mappedParentAclLocation(getMappedParentAclLocation(locations))
            .mappedPbylArea(getMappedPbylArea(locations))
            .locationId(id)
            .locationName(locationName)
            .locationType(locationType)
            .locationSubType(locationSubType)
            .build();
      }
    }
    return LocationInfo.builder()
        .isOnline(Boolean.FALSE)
        .mappedFloorLine(null)
        .isFloorLine(Boolean.FALSE)
        .isMultiManifestLocation(isMultiManifestLocation(locations))
        .mappedParentAclLocation(getMappedParentAclLocation(locations))
        .mappedPbylArea(getMappedPbylArea(locations))
        .locationId(id)
        .locationName(locationName)
        .locationType(locationType)
        .locationSubType(locationSubType)
        .build();
  }

  private String getMappedPbylArea(JsonObject locations) {
    if (Objects.nonNull(locations.get(ReceivingConstants.TAGS))) {
      JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
      for (JsonElement tag : tags) {
        if (ReceivingConstants.MAPPED_PBYL_AREA.equals(
            tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString()))
          return tag.getAsJsonObject()
              .get(ReceivingConstants.TAG_NAMES)
              .getAsJsonArray()
              .get(0)
              .getAsString();
      }
    }
    return null;
  }

  private String getMappedDecantStation(JsonObject locations) {
    if (Objects.nonNull(locations.get(ReceivingConstants.TAGS))) {
      JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
      for (JsonElement tag : tags) {
        if (ReceivingConstants.MAPPED_DECANT_STATION.equalsIgnoreCase(
            tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString()))
          return tag.getAsJsonObject()
              .get(ReceivingConstants.TAG_NAMES)
              .getAsJsonArray()
              .get(0)
              .getAsString();
      }
    }
    return null;
  }

  private String getMappedParentAclLocation(JsonObject locations) {
    if (Objects.nonNull(locations.get(ReceivingConstants.TAGS))) {
      JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
      for (JsonElement tag : tags) {
        if (ReceivingConstants.MAPPED_PARENT_ACL_LOCATION.equals(
            tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString()))
          return tag.getAsJsonObject()
              .get(ReceivingConstants.TAG_NAMES)
              .getAsJsonArray()
              .get(0)
              .getAsString();
      }
    }
    return null;
  }

  // tells if a location has multiple work stations attached.
  // This tag will be added only if a site wants an error to be thrown for scanning the parent
  // location.
  // If the site wants no hierarchy, this tag will be absent
  private boolean isMultiManifestLocation(JsonObject locations) {
    if (Objects.nonNull(locations.get(ReceivingConstants.TAGS))) {
      JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
      for (JsonElement tag : tags) {
        if (ReceivingConstants.MULTI_MANIFEST_LOCATION.equals(
            tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString()))
          return tag.getAsJsonObject()
                  .get(ReceivingConstants.TAG_NAMES)
                  .getAsJsonArray()
                  .get(0)
                  .getAsString()
                  .equals(ReceivingConstants.Y)
              ? Boolean.TRUE
              : Boolean.FALSE;
      }
    }
    return false;
  }

  @Timed(
      name = "getDoorInfoTimed",
      level1 = "uwms-receiving",
      level2 = "locationService",
      level3 = "getDoorInfo")
  @ExceptionCounted(
      name = "getDoorInfoExceptionCount",
      level1 = "uwms-receiving",
      level2 = "locationService",
      level3 = "getDoorInfo")
  private JsonObject getLocationInfoAsJsonObject(String locationName) {
    Map<String, List<String>> locationNames = new HashMap<>();
    locationNames.put(ReceivingConstants.NAMES, Collections.singletonList(locationName));

    String url = appConfig.getLocationBaseuRL() + ReceivingConstants.LOCATION_SEARCH_URI;

    ResponseEntity<String> response;
    try {
      final List<Map<String, List<String>>> request = Collections.singletonList(locationNames);
      LOGGER.info("Location url={}, for request={}", url, request);
      response = simpleRestConnector.post(url, request, ReceivingUtils.getHeaders(), String.class);
      LOGGER.info("Location locationName={}, response= {}", locationNames, response);
    } catch (RestClientResponseException e) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.LOCATION_NOT_FOUND,
          String.format(ReceivingConstants.LOCATION_NOT_FOUND, locationName));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.LOCATION_SERVICE_ERROR,
          String.format(ReceivingConstants.LOCATION_SERVICE_DOWN, locationName));
    }

    return Objects.isNull(response.getBody()) || response.getBody().isEmpty()
        ? null
        : new Gson().fromJson(response.getBody(), JsonObject.class);
  }

  @Timed(
      name = "getDoorInfoTimed",
      level1 = "uwms-receiving",
      level2 = "locationService",
      level3 = "getDoorInfo")
  @ExceptionCounted(
      name = "getDoorInfoExceptionCount",
      level1 = "uwms-receiving",
      level2 = "locationService",
      level3 = "getDoorInfo")
  public JsonObject getBulkLocationInfo(List<String> locationNames, HttpHeaders httpHeaders) {
    Map<String, List<String>> slotMap = new HashMap<>();
    slotMap.put(ReceivingConstants.NAMES, locationNames);

    LOGGER.info("Calling Location for locationNames = {}", locationNames);
    String url = appConfig.getLocationBaseuRL() + ReceivingConstants.LOCATION_SEARCH_URI;

    ResponseEntity<String> response;
    try {
      response =
          simpleRestConnector.post(
              url, Collections.singletonList(slotMap), httpHeaders, String.class);
    } catch (RestClientResponseException e) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.LOCATION_NOT_FOUND,
          String.format(ReceivingConstants.LOCATION_NOT_FOUND, locationNames));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.LOCATION_SERVICE_ERROR,
          String.format(ReceivingConstants.LOCATION_SERVICE_DOWN, locationNames));
    }

    return StringUtils.isEmpty(response.getBody())
        ? null
        : new Gson().fromJson(response.getBody(), JsonObject.class);
  }

  public JsonArray getLocationInfoAsJsonArray(String doorNumber) {
    JsonObject locationResponseJsonObject = getLocationInfoAsJsonObject(doorNumber);
    JsonArray locationsArray =
        locationResponseJsonObject
            .get(ReceivingConstants.SUCCESS_TUPLES)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(ReceivingConstants.RESPONSE)
            .getAsJsonObject()
            .get(ReceivingConstants.LOCATIONS)
            .getAsJsonArray();
    return locationsArray;
  }

  /**
   * Determine if a deliver is docked to a door having ACL or has floor line mapping
   *
   * @param doorNumber door number where the delivery is docked in
   * @return true if it has ACL or floor line mapping else false
   */
  public boolean isOnlineOrHasFloorLine(String doorNumber) {
    LOGGER.debug("Fetching location info for locationId:{}", doorNumber);
    LocationInfo locationInfo = getDoorInfo(doorNumber, false);

    return locationInfo.getIsOnline().equals(Boolean.TRUE)
        || !StringUtils.isEmpty(locationInfo.getMappedFloorLine());
  }

  public LocationInfo getLocationInfoForPbylDockTag(String pbylLocation) {
    LOGGER.info("Fetching location info for pbyl location:{}", pbylLocation);
    JsonObject locationResponseJsonObject = getLocationInfoAsJsonObject(pbylLocation);

    JsonArray locationsArray =
        locationResponseJsonObject
            .get(ReceivingConstants.SUCCESS_TUPLES)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(ReceivingConstants.RESPONSE)
            .getAsJsonObject()
            .get(ReceivingConstants.LOCATIONS)
            .getAsJsonArray();

    if (locationsArray.size() == 0) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PBYL_LOCATION,
          ReceivingConstants.INVALID_LOCATION_FOR_PBYL,
          pbylLocation);
    }

    // tells if it's a PBYL door
    if (locationsArray.size() > 0 && !isValidPbylDoor(locationsArray.get(0).getAsJsonObject()))
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PBYL_LOCATION,
          ReceivingConstants.INVALID_LOCATION_FOR_PBYL,
          pbylLocation);

    LocationInfo locationInfo = getLocationInfo(pbylLocation, locationResponseJsonObject, false);
    if (Objects.isNull(locationInfo.getMappedFloorLine())) {
      locationInfo.setMappedFloorLine(
          tenantSpecificConfigReader.getDCSpecificMoveFloorLineDestinationForNonConDockTag(
              TenantContext.getFacilityNum()));
    }
    return locationInfo;
  }

  private boolean isValidPbylDoor(JsonObject locations) {
    if (Objects.nonNull(locations.get(ReceivingConstants.TAGS))) {
      JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
      for (JsonElement tag : tags) {
        if (ReceivingConstants.PBYL_DOOR.equals(
                tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString())
            && tag.getAsJsonObject()
                .get(ReceivingConstants.TAG_NAMES)
                .getAsJsonArray()
                .get(0)
                .getAsString()
                .equals(ReceivingConstants.Y)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * This method fetches the scanned location information from location api
   *
   * @param locationId
   * @param httpHeaders
   * @return LocationInfo
   */
  public LocationInfo getLocationInfoByIdentifier(String locationId, HttpHeaders httpHeaders) {
    LocationInfo locationInfo = null;
    Boolean isFlibLocation = false;
    JsonObject locationResponseJsonObject = getLocationInfoAsJsonObject(locationId);
    JsonArray locationsArray =
        locationResponseJsonObject
            .get(ReceivingConstants.SUCCESS_TUPLES)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(ReceivingConstants.RESPONSE)
            .getAsJsonObject()
            .get(ReceivingConstants.LOCATIONS)
            .getAsJsonArray();

    if (locationsArray.size() > 0) {
      JsonObject locations = locationsArray.get(0).getAsJsonObject();
      String id =
          nonNull(locations.get(ReceivingConstants.ID))
              ? locations.get(ReceivingConstants.ID).getAsString()
              : null;
      String locationType =
          nonNull(locations.get(ReceivingConstants.TYPE))
              ? locations
                  .get(ReceivingConstants.TYPE)
                  .getAsJsonObject()
                  .get(ReceivingConstants.VALUE)
                  .getAsString()
              : null;
      String locationSubType =
          nonNull(locations.get(ReceivingConstants.SUB_TYPE))
              ? locations
                  .get(ReceivingConstants.SUB_TYPE)
                  .getAsJsonObject()
                  .get(ReceivingConstants.VALUE)
                  .getAsString()
              : null;
      String locationName =
          nonNull(locations.get(ReceivingConstants.NAME))
              ? locations.get(ReceivingConstants.NAME).getAsString()
              : null;
      String sccCode =
          nonNull(locations.get(PROPERTIES))
                  && nonNull(
                      locations.get(PROPERTIES).getAsJsonObject().get(ReceivingConstants.SC_CODE))
              ? locations
                  .get(PROPERTIES)
                  .getAsJsonObject()
                  .get(ReceivingConstants.SC_CODE)
                  .getAsString()
              : null;
      if (nonNull(locations.get(ReceivingConstants.TAGS))) {
        isFlibLocation = isFlibLocationExists(locations);
      }

      validateResponse(locationId, id, locationType, locationName, sccCode);

      locationInfo =
          LocationInfo.builder()
              .locationId(id)
              .locationType(locationType)
              .locationSubType(locationSubType)
              .locationName(locationName)
              .mappedDecantStation(getMappedDecantStation(locations))
              .sccCode(sccCode)
              .isFlibLocation(isFlibLocation)
              .build();
      if (appConfig.isWftPublishEnabled()) {
        LocationSummary locationSummary = new LocationSummary();
        locationSummary.setSource(ReceivingConstants.RDC_RECEIVING_SOURCE_FOR_WFT);
        locationSummary.setUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
        locationSummary.setReceivingTS(new Date());
        String locationTypeForWFT = locationType + ReceivingConstants.DELIM_DASH + locationName;
        locationSummary.setLocation(
            new LocationSummary.Location(Integer.parseInt(id), locationTypeForWFT, sccCode));
        Map<String, Object> messageHeaderMap =
            ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.KAFKA_WFT_LOCATION_PUBLISH_ENABLED,
            Boolean.FALSE)) {
          kafkaWftLocationMessagePublisher.publish(locationSummary, messageHeaderMap);
        } else {
          ReceivingJMSEvent receivingJMSEvent =
              new ReceivingJMSEvent(
                  messageHeaderMap,
                  new GsonBuilder()
                      .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                      .create()
                      .toJson(locationSummary));

          jmsPublisher.publish(
              ReceivingConstants.PUB_SCAN_LOCATION_TOPIC, receivingJMSEvent, Boolean.FALSE);
        }
      }
    } else {
      LOGGER.info("Location response is empty or null for locationId:{}", locationId);
      throw new ReceivingBadDataException(
          ExceptionCodes.LOCATION_RESP_IS_EMPTY,
          String.format(LOCATION_RESP_IS_EMPTY, locationId),
          locationId);
    }
    return locationInfo;
  }

  /**
   * Get Location Info without business validations
   *
   * @param locationNameRequest
   * @return
   */
  public LocationInfo getLocationInfo(String locationNameRequest) {
    JsonArray locationsArray =
        getLocationInfoAsJsonObject(locationNameRequest)
            .get(SUCCESS_TUPLES)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(RESPONSE)
            .getAsJsonObject()
            .get(LOCATIONS)
            .getAsJsonArray();
    if (locationsArray == null || locationsArray.size() <= 0) {
      LOGGER.info(
          "For locationName:{} response has empty or null locationsArray={}",
          locationNameRequest,
          locationsArray);
      throw new ReceivingBadDataException(
          ExceptionCodes.LOCATION_RESP_IS_EMPTY,
          String.format(LOCATION_RESP_IS_EMPTY, locationNameRequest),
          locationNameRequest);
    }
    JsonObject firstLocation = locationsArray.get(0).getAsJsonObject();
    String id =
        nonNull(firstLocation.get(ReceivingConstants.ID))
            ? firstLocation.get(ReceivingConstants.ID).getAsString()
            : null;
    String locationName =
        nonNull(firstLocation.get(ReceivingConstants.NAME))
            ? firstLocation.get(ReceivingConstants.NAME).getAsString()
            : null;
    Boolean isPrimeSlot =
        nonNull(firstLocation.get(ReceivingConstants.PRIME_SLOT))
            && firstLocation.get(ReceivingConstants.PRIME_SLOT).getAsBoolean();
    return LocationInfo.builder()
        .locationId(id)
        .locationName(locationName)
        .isPrimeSlot(isPrimeSlot)
        .automationType(getAutomationType(firstLocation.get(PROPERTIES)))
        .build();
  }

  private static String getAutomationType(JsonElement propertiesJsonEle) {
    if (propertiesJsonEle == null) {
      return null;
    }
    final JsonObject propertiesObj = propertiesJsonEle.getAsJsonObject();
    return nonNull(propertiesObj.get(AUTOMATION_TYPE))
        ? propertiesObj.get(AUTOMATION_TYPE).getAsString()
        : null;
  }

  private Boolean isFlibLocationExists(JsonObject locations) {
    JsonArray tags = locations.get(ReceivingConstants.TAGS).getAsJsonArray();
    for (JsonElement tag : tags) {
      if (ReceivingConstants.FLIB_DOOR.equalsIgnoreCase(
          tag.getAsJsonObject().get(ReceivingConstants.DOMAIN).getAsString())) {
        if (ReceivingConstants.FLIB_TAG_NAME.equalsIgnoreCase(
            tag.getAsJsonObject()
                .get(ReceivingConstants.TAG_NAMES)
                .getAsJsonArray()
                .get(0)
                .getAsString())) ;
        return true;
      }
    }
    return false;
  }

  private void validateResponse(
      String locationId, String id, String locationType, String locationName, String sccCode) {

    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), VALIDATE_LOCATION_INFO_ENABLED, false)) return;

    if (!(StringUtils.isNotEmpty(id)
        && StringUtils.isNotEmpty(locationType)
        && StringUtils.isNotEmpty(sccCode)
        && StringUtils.isNotEmpty(locationName))) {
      LOGGER.error(
          "The locationId:{} or locationType:{} or locationName:{} or sccCode:{} is missing in location api response",
          id,
          locationType,
          locationName,
          sccCode);
      throw new ReceivingConflictException(
          ExceptionCodes.INVALID_LOCATION_RESPONSE,
          String.format(ReceivingConstants.INVALID_LOCATION_RESPONSE, locationId),
          locationId);
    }
  }
}
