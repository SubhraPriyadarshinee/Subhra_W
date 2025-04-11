package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Utility to expose common Reporting functions */
public class ReportingUtils {
  protected InstructionError instructionError;

  public static Date zonedDateTimeToUTC(long dateTime, String timeZone) {

    ZonedDateTime timeZoneddatetime = Instant.ofEpochMilli(dateTime).atZone(ZoneId.of(timeZone));
    Instant instantTime = timeZoneddatetime.toInstant();
    Date dcDate = Date.from(instantTime);
    return dcDate;
  }

  public void validateDateFields(Date fromDate, Date toDate, int setInputTimeIntervalinHrs)
      throws ReceivingException {

    int timeIntervalinHrs =
        (int) TimeUnit.MILLISECONDS.toHours(toDate.getTime() - fromDate.getTime());

    if (Objects.isNull(fromDate) || Objects.isNull(toDate)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.MANDATORY_DATE_FIELDS, ReceivingConstants.MANDATORY_DATE_FIELDS);
    }
    if (Objects.nonNull(fromDate) && fromDate.compareTo(toDate) > 0) {
      throw new ReceivingBadDataException(
          ExceptionCodes.FROMDATE_GREATER, ReceivingConstants.FROMDATE_GREATER);
    }
    if (timeIntervalinHrs > setInputTimeIntervalinHrs) {
      throw new ReceivingBadDataException(
          ExceptionCodes.MAX_DATETIMEINTERVAL, ReceivingConstants.MAX_DATETIMEINTERVAL);
    }
  }
}
