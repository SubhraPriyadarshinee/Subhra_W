package com.walmart.move.nim.receiving.core.mock.data;

import com.google.gson.*;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import org.testng.util.Strings;

public class MockInstructionResponse {
  private static final Gson gson = new Gson();

  private static final Gson gsonForUnixDate = getGsonForUnixDate();

  private static Gson getGsonForUnixDate() {
    GsonBuilder builder = new GsonBuilder();
    // Register an adapter to manage the date types as long values
    builder.registerTypeAdapter(
        Date.class,
        new JsonDeserializer<Date>() {
          public Date deserialize(
              JsonElement json, Type typeOfT, JsonDeserializationContext context)
              throws JsonParseException {
            return new Date(json.getAsJsonPrimitive().getAsLong());
          }
        });
    return builder.create();
  }

  private static String readJsonFileToString(String path) {
    String response = null;
    try {
      String filePath = new File(path).getCanonicalPath();
      response = new String(Files.readAllBytes(Paths.get(filePath)));
      if (!Strings.isNullOrEmpty(response)) {
        return response;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static InstructionResponse getManualInstructionResponse() {
    String jsonResponse =
        readJsonFileToString(
            "../receiving-test/src/main/resources/json/manual_instruction_create_response.json");
    InstructionResponse manualInstructionResponse =
        gsonForUnixDate.fromJson(jsonResponse, InstructionResponseImplNew.class);
    return manualInstructionResponse;
  }

  public static InstructionResponse getManualInstructionResponseForAutoCaseReceive(
      List<DeliveryDocument> deliveryDocuments) {
    String jsonResponse =
        readJsonFileToString(
            "../receiving-test/src/main/resources/json/manual_instruction_create_response.json");
    InstructionResponse manualInstructionResponse =
        gsonForUnixDate.fromJson(jsonResponse, InstructionResponseImplNew.class);
    manualInstructionResponse.setDeliveryDocuments(deliveryDocuments);
    Instruction instruction = manualInstructionResponse.getInstruction();
    instruction.setInstructionCode(ReceivingConstants.SCAN_TO_PRINT_INSTRUCTION_CODE);
    manualInstructionResponse.setInstruction(instruction);
    return manualInstructionResponse;
  }
}
