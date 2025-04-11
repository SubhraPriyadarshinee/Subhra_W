package com.walmart.move.nim.receiving.rdc.mock.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

public class MockInstructionResponse {
  private static Gson gson = new Gson();

  public static Instruction getMockInstruction() throws IOException {
    File resource = new ClassPathResource("ReceiveInstructionResponse.json").getFile();
    String mockInstruction = new String(Files.readAllBytes(resource.toPath()));
    Instruction instruction = gson.fromJson(mockInstruction, Instruction.class);
    return instruction;
  }

  public static Instruction getMockInstructionForRefresh() throws IOException {
    File resource = new ClassPathResource("RefreshInstructionResponse.json").getFile();
    String mockInstruction = new String(Files.readAllBytes(resource.toPath()));
    Instruction instruction = gson.fromJson(mockInstruction, Instruction.class);
    return instruction;
  }

  public static InstructionResponse getMockInstructionResponse() throws IOException {
    File resource = new ClassPathResource("RefreshInstructionResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    JsonObject jsonObject = JsonParser.parseString(mockResponse).getAsJsonObject();
    String deliveryStatus = String.valueOf(jsonObject.get("deliveryStatus"));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            jsonObject.get("deliveryDocuments"),
            new TypeToken<List<DeliveryDocument>>() {}.getType());
    Instruction instruction = gson.fromJson(mockResponse, Instruction.class);
    Map<String, Object> printJob =
        gson.fromJson(
            jsonObject.get("printJob"), new TypeToken<Map<String, Object>>() {}.getType());
    InstructionResponse instructionResponse =
        new InstructionResponseImplNew(deliveryStatus, deliveryDocuments, instruction, printJob);
    return instructionResponse;
  }
}
