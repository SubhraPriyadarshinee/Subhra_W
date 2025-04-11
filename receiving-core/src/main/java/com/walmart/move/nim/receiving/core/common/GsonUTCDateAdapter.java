package com.walmart.move.nim.receiving.core.common;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author a0b02ft Adapter responsible for converting any date to UTC Time zone. Also it will use
 *     the date format yyyy-MM-dd'T'HH:mm:ss.SSS'Z'. In all the queue and topic we publish date in
 *     the above format in UTC time zone.
 */
public class GsonUTCDateAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {

  private final DateFormat dateFormat;

  public GsonUTCDateAdapter(String dateFormatPattern) {
    dateFormat = new SimpleDateFormat(dateFormatPattern);
    dateFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
  }

  public GsonUTCDateAdapter() {
    dateFormat = new SimpleDateFormat(ReceivingConstants.UTC_DATE_FORMAT);
    dateFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
  }

  @Override
  public synchronized Date deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context) {
    try {
      return dateFormat.parse(json.getAsString());
    } catch (ParseException e) {
      throw new JsonParseException(e);
    }
  }

  @Override
  public synchronized JsonElement serialize(
      Date src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(dateFormat.format(src));
  }
}
