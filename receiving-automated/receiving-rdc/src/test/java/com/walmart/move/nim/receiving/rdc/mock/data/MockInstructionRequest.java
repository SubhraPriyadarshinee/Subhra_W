package com.walmart.move.nim.receiving.rdc.mock.data;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
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

  public static InstructionRequest getInstructionRequest_WorkStation() throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("UPC");
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDAReceivingFeatureType() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("UPC");
    instructionRequest.setFeatureType("SCAN_TO_PRINT");
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDAQtyReceiving() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setUpcNumber("00605388022945");
    instructionRequest.setReceivingType("UPC");
    return instructionRequest;
  }

  public static InstructionRequest getSSCCInstructionRequest() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00000000605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("SSCC");
    instructionRequest.setUpcNumber("00000000605388022945");
    return instructionRequest;
  }

  public static InstructionRequest getSSCCInstructionRequestWithWorkStationEnabled() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00000000605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("SSCC");
    instructionRequest.setFeatureType("WORK_STATION");
    instructionRequest.setUpcNumber("00000000605388022945");
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

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForXBlockItem()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource = new ClassPathResource("GdmMappedResponseV2_XBlock.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsWithShipmentDetails()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource =
        new ClassPathResource("GdmMappedPackResponseToDeliveryDocumentLines.json").getFile();
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

  public static InstructionRequest getInstructionRequestForThreeScanDocktag() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setFeatureType(RdcConstants.THREE_SCAN_DOCKTAG_FEATURE_TYPE);
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

  public static InstructionRequest getSSCCInstructionRequestForDsdc() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00000000605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("SSCC");
    instructionRequest.setUpcNumber("00000000605388022945");
    instructionRequest.setFeatureType("WORK_STATION");
    return instructionRequest;
  }

  public static InstructionRequest getSSCCInstructionRequestForDsdcQtyReceiving() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00000000605388022945");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("SSCC");
    instructionRequest.setUpcNumber("00000000605388022945");
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestForRtsPut() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("6426754a-747d-4942-ae62-b1c7a687a03e");
    instructionRequest.setDeliveryNumber("21371350");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00075353395530");
    instructionRequest.setDoorNumber("102");
    instructionRequest.setReceivingType("WORK_STATION_UPC");
    instructionRequest.setFeatureType("WORK_STATION");
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestForRtsPutWithScanToPrintFeatureType() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("6426754a-747d-4942-ae62-b1c7a687a03e");
    instructionRequest.setDeliveryNumber("21371350");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00075353395530");
    instructionRequest.setDoorNumber("102");
    instructionRequest.setReceivingType("WORK_STATION_UPC");
    instructionRequest.setFeatureType("SCAN_TO_PRINT");
    return instructionRequest;
  }

  public static InstructionRequest getSSCCInstructionRequestForDsdcWithDeliveryDocuments()
      throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("24925342");
    instructionRequest.setDeliveryStatus("OPN");
    instructionRequest.setSscc("00000747640093895202");
    instructionRequest.setDoorNumber("455");
    instructionRequest.setReceivingType("SSCC");
    instructionRequest.setFeatureType("WORK_STATION");
    return instructionRequest;
  }
}
