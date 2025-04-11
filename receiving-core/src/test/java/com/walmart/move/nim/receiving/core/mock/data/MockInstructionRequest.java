package com.walmart.move.nim.receiving.core.mock.data;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

public class MockInstructionRequest {
  private static final Gson gson = new Gson();

  public static InstructionRequest getInstructionRequest() throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("UPC");
    return instructionRequest;
  }

  public static InstructionRequest getSSCCInstructionRequest() throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00000000605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("SSCC");
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocuments() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static GdmPOLineResponse getGDMPoLineResponseForPoAndPoLineNbr() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2ByPoPoLine.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(mockDeliveryDocumentsResponse, GdmPOLineResponse.class);
    return gdmPOLineResponse;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocuments_AtlasItems()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    ItemData item = new ItemData();
    item.setAtlasConvertedItem(true);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(item);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithDeliveryDocuments_AtlasItemAndIQSEnabled() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_IQSIntegrationEnabled());
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocuments4BreakPack()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_break_pack_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithDeliveryDocuments4BreakPackAtlasItemSplitPallet()
          throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_break_pack_atlas_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest getDeliveryDocumentsForSSTK_BreakPackRatioOne()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_Item_BreakPackRatio_1.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocuments_MultiPOLines()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine());
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocuments_MultiPOs()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo());
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithLimitedQtyCompliance()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLimitedQty());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithLithiumIonCompliance()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLithiumIon());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithLithiumIonAndLimitedQtyCompliance()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLithiumIonAndLimitedQtyItem());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY);
    return instructionRequest;
  }

  public static List<TransportationModes> getMockTransportationModeForLimitedQty() {
    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    return Arrays.asList(transportationModes);
  }

  public static List<TransportationModes> getMockTransportationModeForLithiumIon() {
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("965"));
    transportationModes.setMode(mode);

    return Arrays.asList(transportationModes);
  }

  public static List<TransportationModes>
      getMockTransportationModeForLithiumIonAndLimitedQtyItem() {
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("LTD-Q");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("970"));
    transportationModes.setMode(mode);

    return Arrays.asList(transportationModes);
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForUniqueItemNumber()
      throws IOException {
    File resource =
        new ClassPathResource("MockInstructionRequestWithDeliveryDocuments.json").getFile();
    String mockInstructionRequest = new String(Files.readAllBytes(resource.toPath()));
    InstructionRequest instructionRequest =
        gson.fromJson(mockInstructionRequest, new TypeToken<InstructionRequest>() {}.getType());
    return instructionRequest;
  }
}
