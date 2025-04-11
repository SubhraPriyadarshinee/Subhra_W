package com.walmart.move.nim.receiving.endgame.repositories;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.endgame.mock.data.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryMetaDataRepositoryTest extends ReceivingTestBase {
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    List<DeliveryMetaData> deliveryMetaDataList =
        MockDeliveryMetaData.getDeliveryMetaData_ForRepositoryTest();
    deliveryMetaDataRepository.saveAll(deliveryMetaDataList);
  }

  @Test
  public void
      testFindByUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrOsdrLastProcessedDateIsNull() {
    Date unloadingCompleteDateFiveDaysBack = Date.from(Instant.now().minus(Duration.ofDays(5)));

    Date osdrProcessedDateFourHoursBack = Date.from(Instant.now().minus(Duration.ofMinutes(240)));

    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository
            .findByUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                unloadingCompleteDateFiveDaysBack,
                osdrProcessedDateFourHoursBack,
                unloadingCompleteDateFiveDaysBack,
                PageRequest.of(0, 3));
    assertEquals(deliveryMetaDataList.size(), 2);
    for (DeliveryMetaData deliveryMetaData : deliveryMetaDataList) {
      if (!(deliveryMetaData.getDeliveryNumber().equals("12333336")
          || deliveryMetaData.getDeliveryNumber().equals("12333333"))) {
        assertTrue(false, "Delivery number not matched.");
      }
    }
  }

  @Test
  public void
      testFindByUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrOsdrLastProcessedDateIsNull_WithPaging() {
    Date unloadingCompleteDateFiveDaysBack = Date.from(Instant.now().minus(Duration.ofDays(5)));

    Date osdrProcessedDateFourHoursBack = Date.from(Instant.now().minus(Duration.ofMinutes(240)));

    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository
            .findByUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                unloadingCompleteDateFiveDaysBack,
                osdrProcessedDateFourHoursBack,
                unloadingCompleteDateFiveDaysBack,
                PageRequest.of(0, 1));
    assertEquals(deliveryMetaDataList.size(), 1);
  }

  @Test
  public void
      testFindByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull_Success() {
    Date unloadingCompleteDateFiveDaysBack = Date.from(Instant.now().minus(Duration.ofDays(5)));
    Date osdrProcessedDateFourHoursBack = Date.from(Instant.now().minus(Duration.ofMinutes(240)));

    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository
            .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                DeliveryStatus.COMPLETE,
                unloadingCompleteDateFiveDaysBack,
                osdrProcessedDateFourHoursBack,
                DeliveryStatus.COMPLETE,
                unloadingCompleteDateFiveDaysBack,
                PageRequest.of(0, 20));
    assertEquals(deliveryMetaDataList.size(), 2);
  }

  @Test
  public void
      testFindByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull_WithPaging() {
    Date unloadingCompleteDateFiveDaysBack = Date.from(Instant.now().minus(Duration.ofDays(5)));
    Date osdrProcessedDateFourHoursBack = Date.from(Instant.now().minus(Duration.ofMinutes(240)));

    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository
            .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                DeliveryStatus.COMPLETE,
                unloadingCompleteDateFiveDaysBack,
                osdrProcessedDateFourHoursBack,
                DeliveryStatus.COMPLETE,
                unloadingCompleteDateFiveDaysBack,
                PageRequest.of(0, 1));
    assertEquals(deliveryMetaDataList.size(), 1);
  }

  @Test
  public void
      testFindByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull_OtherDeliveryStatus() {
    Date unloadingCompleteDateFiveDaysBack = Date.from(Instant.now().minus(Duration.ofDays(5)));
    Date osdrProcessedDateFourHoursBack = Date.from(Instant.now().minus(Duration.ofMinutes(240)));
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository
            .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                DeliveryStatus.WRK,
                unloadingCompleteDateFiveDaysBack,
                osdrProcessedDateFourHoursBack,
                DeliveryStatus.WRK,
                unloadingCompleteDateFiveDaysBack,
                PageRequest.of(0, 2));
    assertEquals(deliveryMetaDataList.size(), 0);
  }
}
