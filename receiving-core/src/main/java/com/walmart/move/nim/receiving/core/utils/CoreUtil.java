package com.walmart.move.nim.receiving.core.utils;

import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.MDC;

public class CoreUtil {

  public static Date addMinutesToJavaUtilDate(Date date, int minutes) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.add(Calendar.MINUTE, minutes);
    return calendar.getTime();
  }

  public static void setTenantContext(
      Integer facilityNum, String facilityCountryCode, String correlationId) {
    TenantContext.setFacilityNum(facilityNum);
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId(correlationId);
  }

  /**
   * This method is responsible for setting up @{@link org.slf4j.MDC} context. For logging purposes
   */
  public static void setMDC() {
    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
  }

  public static Map<Long, ItemDetails> getItemMap(ASNDocument asnDocument) {
    return Objects.isNull(asnDocument.getItems())
        ? new HashMap()
        : asnDocument
            .getItems()
            .stream()
            .collect(Collectors.toMap(ItemDetails::getNumber, itemDetails -> itemDetails));
  }
}
