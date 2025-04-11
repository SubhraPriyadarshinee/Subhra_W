package com.walmart.move.nim.receiving.core.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;

public class JacksonParser {
  private static ObjectMapper objectMapper = new ObjectMapper();

  private JacksonParser() {}

  public static <T> T convertJsonToObject(String content, Class<T> valueType) {
    try {
      objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      return objectMapper.readValue(content, valueType);
    } catch (JsonParseException | JsonMappingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    } catch (IOException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PARSE, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    }
  }

  public static <T> T convertJsonToObject(String content, TypeReference<T> typeReference) {
    try {
      objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      return objectMapper.readValue(content, typeReference);
    } catch (JsonParseException | JsonMappingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    } catch (IOException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PARSE, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    }
  }

  public static String writeValueAsString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonParseException | JsonMappingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    } catch (IOException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PARSE, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    }
  }

  public static String writeValueAsStringExcludeNull(Object value) {
    try {
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      return objectMapper.writeValueAsString(value);
    } catch (JsonParseException | JsonMappingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    } catch (IOException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PARSE, String.format(ReceivingConstants.UNABLE_TO_PARSE));
    }
  }
}
