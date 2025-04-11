package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.LocationType;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.repositories.UserLocationRepo;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

public class UpdateUserLocationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUserLocationService.class);

  @Autowired private UserLocationRepo userLocationRepo;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Transactional
  @InjectTenantFilter
  public UserLocation updateUserLocation(
      String userId, String locationId, LocationInfo locationInfo, HttpHeaders httpHeaders) {
    LocationType locationType = LocationType.OFFLINE;
    String parentLocationId = null;
    boolean isKotlinEnabled =
        ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    // TODO Consider the scenario where location is offline door with fl mapping
    if (Objects.nonNull(locationInfo)) {
      if (locationInfo.getIsOnline()) locationType = LocationType.ONLINE;
      else if (!StringUtils.isEmpty(locationInfo.getMappedFloorLine())
          || (isKotlinEnabled && Boolean.TRUE.equals(locationInfo.getIsFloorLine())))
        locationType = LocationType.FLR_LINE;
      if (!StringUtils.isEmpty(locationInfo.getMappedParentAclLocation())) {
        parentLocationId = locationInfo.getMappedParentAclLocation();
      }
    }
    LOGGER.info("Fetching userId:{} info in USER_LOCATION table", userId);
    Optional<UserLocation> userLocationResponse = userLocationRepo.findByUserId(userId);

    if (userLocationResponse.isPresent()) {
      UserLocation userLocation = userLocationResponse.get();
      if (userLocation.getLocationId().equals(locationId)) {
        LOGGER.info(
            "User:{} is already mapped with location:{}:{}, updating createTs",
            userId,
            locationId,
            locationType);
      } else {
        LOGGER.info(
            "User:{} is mapped with location:{}:{}, so updating user "
                + "with scanned location:{}:{}",
            userId,
            userLocation.getLocationId(),
            userLocation.getLocationType(),
            locationId,
            locationType);
        userLocation.setLocationId(org.apache.commons.lang3.StringUtils.upperCase(locationId));
      }
      userLocation.setLocationType(locationType);
      userLocation.setParentLocationId(parentLocationId);
      userLocation.setCreateTs(new Date());
      return userLocationRepo.save(userLocation);
    } else {
      LOGGER.info(
          "User:{} is not mapped to any location, so mapping to " + "location:{}",
          userId,
          locationId);
      UserLocation persistUserLocation = new UserLocation();
      persistUserLocation.setUserId(userId);
      persistUserLocation.setLocationId(org.apache.commons.lang3.StringUtils.upperCase(locationId));
      persistUserLocation.setLocationType(locationType);
      persistUserLocation.setParentLocationId(parentLocationId);
      return userLocationRepo.save(persistUserLocation);
    }
  }
}
