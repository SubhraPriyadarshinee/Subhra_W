package com.walmart.move.nim.receiving.core.repositories;

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryItemOverrideRepositoryTest extends ReceivingTestBase {

  @Autowired private DeliveryItemOverrideRepository deliveryItemOverrideRepo;

  private DeliveryItemOverride deliveryItemOverride1;
  private DeliveryItemOverride deliveryItemOverride2;
  private DeliveryItemOverride deliveryItemOverride3;
  private DeliveryItemOverride deliveryItemOverride4;
  private List<DeliveryItemOverride> deliveryItemOverrides;

  @BeforeClass
  public void insertDataIntoH2Db() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    deliveryItemOverride1 = new DeliveryItemOverride();
    deliveryItemOverride2 = new DeliveryItemOverride();
    deliveryItemOverride3 = new DeliveryItemOverride();
    deliveryItemOverride4 = new DeliveryItemOverride();
    deliveryItemOverrides = new ArrayList<>();

    deliveryItemOverride1.setDeliveryNumber(Long.parseLong("63346417631"));
    deliveryItemOverride1.setItemNumber(Long.parseLong("336531"));
    deliveryItemOverride1.setTempPalletTi(2);
    deliveryItemOverride1.setTempPalletHi(1);

    deliveryItemOverride2.setDeliveryNumber(Long.parseLong("63346417632"));
    deliveryItemOverride2.setItemNumber(Long.parseLong("336532"));
    deliveryItemOverride2.setTempPalletTi(3);
    deliveryItemOverride2.setTempPalletHi(2);

    deliveryItemOverride3.setDeliveryNumber(Long.parseLong("63346417633"));
    deliveryItemOverride3.setItemNumber(Long.parseLong("336533"));
    deliveryItemOverride3.setTempPalletTi(4);
    deliveryItemOverride3.setTempPalletHi(3);

    deliveryItemOverride4.setDeliveryNumber(Long.parseLong("63346417634"));
    deliveryItemOverride4.setItemNumber(Long.parseLong("336534"));
    deliveryItemOverride4.setTempPalletTi(5);
    deliveryItemOverride4.setTempPalletHi(4);

    deliveryItemOverrides.add(deliveryItemOverride1);
    deliveryItemOverrides.add(deliveryItemOverride2);
    deliveryItemOverrides.add(deliveryItemOverride3);
    deliveryItemOverrides.add(deliveryItemOverride4);

    deliveryItemOverrideRepo.saveAll(deliveryItemOverrides);
  }

  @Test
  public void testFindFirstByDeliveryNumberAndItemNumber() {
    Optional<DeliveryItemOverride> response1 =
        deliveryItemOverrideRepo.findByDeliveryNumberAndItemNumber(
            Long.parseLong("63346417631"), Long.parseLong("336531"));
    assertEquals(response1.get().getTempPalletTi(), deliveryItemOverride1.getTempPalletTi());
    assertEquals(response1.get().getTempPalletHi(), deliveryItemOverride1.getTempPalletHi());

    Optional<DeliveryItemOverride> response2 =
        deliveryItemOverrideRepo.findByDeliveryNumberAndItemNumber(
            Long.parseLong("63346417634"), Long.parseLong("336534"));
    assertEquals(response2.get().getTempPalletTi(), deliveryItemOverride4.getTempPalletTi());
    assertEquals(response2.get().getTempPalletHi(), deliveryItemOverride4.getTempPalletHi());

    Optional<DeliveryItemOverride> response3 =
        deliveryItemOverrideRepo.findByDeliveryNumberAndItemNumber(
            Long.parseLong("63346417635"), Long.parseLong("336535"));
    assertFalse(response3.isPresent());
  }
}
