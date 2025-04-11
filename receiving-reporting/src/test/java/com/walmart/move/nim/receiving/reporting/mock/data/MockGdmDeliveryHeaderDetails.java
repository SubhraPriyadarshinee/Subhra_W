package com.walmart.move.nim.receiving.reporting.mock.data;

import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MockGdmDeliveryHeaderDetails {

  public static List<GdmDeliveryHeaderDetailsResponse> getGdmDeliveryHeaderDetailsResponse() {
    return Collections.singletonList(
        GdmDeliveryHeaderDetailsResponse.builder()
            .deliveryNumber(12345678L)
            .arrivedTimeStamp(new Date(2020, Calendar.JANUARY, 1, 12, 0, 0).toInstant())
            .receivingFirstCompletedTimeStamp(
                new Date(2020, Calendar.JANUARY, 1, 14, 0, 0).toInstant())
            .receivingCompletedTimeStamp(new Date(2020, Calendar.JANUARY, 1, 15, 0, 0).toInstant())
            .doorOpenTimeStamp(new Date(2020, Calendar.JANUARY, 1, 13, 0, 0).toInstant())
            .status(DeliveryStatus.COMPLETE)
            .build());
  }
}
