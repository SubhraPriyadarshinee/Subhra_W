package com.walmart.move.nim.receiving.core.common;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.Instant;

/** @author sks0013 Adapter responsible for converting Instant objects */
public class GsonUTCInstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

  @Override
  public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
    return Instant.parse(json.getAsString());
  }

  @Override
  public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(src.toString());
  }
}
