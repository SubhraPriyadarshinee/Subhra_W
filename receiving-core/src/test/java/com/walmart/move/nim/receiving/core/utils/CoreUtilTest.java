package com.walmart.move.nim.receiving.core.utils;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.UTC_DATE_FORMAT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.UTC_TIME_ZONE;
import static org.testng.Assert.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.mockito.InjectMocks;
import org.springframework.test.context.ActiveProfiles;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class CoreUtilTest {
  @InjectMocks private CoreUtil coreUtil;

  @Test
  public void addMinutesToJavaUtilDateTest() {
    Date createTsInUTC = null;
    Date date = new Date(System.currentTimeMillis());
    try {
      DateFormat dateFormat = new SimpleDateFormat(UTC_DATE_FORMAT);
      dateFormat.setTimeZone(TimeZone.getTimeZone(UTC_TIME_ZONE));
      createTsInUTC = dateFormat.parse(dateFormat.format(date));
    } catch (Exception e) {
      fail("Unable to parse date " + e.getMessage());
    }
    Date result = coreUtil.addMinutesToJavaUtilDate(createTsInUTC, 10);
    assertTrue(result.after(date));
  }
}
