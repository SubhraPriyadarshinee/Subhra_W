package com.walmart.move.nim.receiving.acc.repositories;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class UserLocationRepoTest extends ReceivingTestBase {

  @Autowired private UserLocationRepo userLocationRepo;

  private UserLocation userLocation;

  @BeforeClass
  public void setup() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);

    UserLocation userLocation1 = new UserLocation();
    userLocation1.setLocationId("100");
    userLocation1.setUserId("sysadmin.32987");

    userLocationRepo.save(userLocation1);
  }

  @Test
  public void testForUserNotFoundInDB() {
    Optional<UserLocation> userLocationResponse = userLocationRepo.findByUserId("sysadmin1.32987");
    assertFalse(userLocationResponse.isPresent());
  }

  @Test
  public void testForUserFoundInDB() {
    Optional<UserLocation> userLocationResponse = userLocationRepo.findByUserId("sysadmin.32987");
    assertTrue(userLocationResponse.isPresent());
    assertTrue(userLocationResponse.get().getUserId().equalsIgnoreCase("sysadmin.32987"));
  }
}
