package com.walmart.move.nim.receiving.helper;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonSchemaValidator {

  private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

  public static boolean validateContract(String jsonSchema, String jsonMessage) {

    log.info("validateContract: Message to be validated : " + jsonMessage);
    try {
      JSONObject jsonSchemaObj = new JSONObject(new JSONTokener(jsonSchema));
      JSONObject jsonSubject = new JSONObject(new JSONTokener(jsonMessage));

      Schema schema = SchemaLoader.load(jsonSchemaObj);
      try {
        schema.validate(jsonSubject);
        log.info("validateContract: Message validated successfully");
        return true;
      } catch (ValidationException e) {
        log.error("validateContract: Message validation failed");
        log.error(e.getMessage());
        e.getCausingExceptions().stream().map(ValidationException::getMessage).forEach(log::error);
        return false;
      }
    } catch (JSONException e) {
      log.error("Unable to validate contract");
      return false;
    }
  }
}
