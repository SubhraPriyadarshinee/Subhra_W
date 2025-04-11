package com.walmart.move.nim.receiving.rdc.mock.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.model.ContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

public class MockDeliveryDocuments {
  private static Gson gson = new Gson();

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      getDeliveryDocumentsForSSTKV2() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> gdmDocuments =
        Arrays.asList(
            gson.fromJson(
                mockResponse,
                com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static String getDeliveryDocumentsByPoAndPoLine() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2ByPoPoLine.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getDeliveryDocumentsPoDistByPoAndPoLine() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseDeliveryDocumentV2ByPoPoLine.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK_IQSIntegrationEnabled()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_Item_IQSIntegration.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static ContainerItemDetails getInventoryContainerDetails_withChannelType()
      throws IOException {
    File resource =
        new ClassPathResource("InventoryContainerDetailsSuccessResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ContainerItemDetails containerItemDetails =
        gson.fromJson(mockResponse, ContainerItemDetails.class);
    return containerItemDetails;
  }

  public static ContainerItemDetails getInventoryContainerDetails_withOutChannelType()
      throws IOException {
    File resource =
        new ClassPathResource("InventoryContainerDetailsSuccessResponse_WithoutChannelType.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ContainerItemDetails containerItemDetails =
        gson.fromJson(mockResponse, ContainerItemDetails.class);
    return containerItemDetails;
  }

  public static ContainerItemDetails
      getInventoryContainerDetails_withOutChannelType_WithoutPoLineDetails() throws IOException {
    File resource =
        new ClassPathResource("InventoryContainerDetailsSuccessResponse_WithoutPOLine.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ContainerItemDetails containerItemDetails =
        gson.fromJson(mockResponse, ContainerItemDetails.class);
    return containerItemDetails;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK_AtlasConvertedItem()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_AtlasConvertedItem.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK_MissingTixHi()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_Item_MissingTixHi.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForXBlockItem() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_XBlock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK_ZeroTixHi() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_Item_ZeroTixHi.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSingleDA() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_DA_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSingleDA_NoHandlingCode()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_DA_Item_NoHandlingCode.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsByPoPoLineNumberForSingleDA()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_DA_CaseConvey_ByPoPoLineNbr.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSingleDABreakPack()
      throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_DA_BreakPack_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMultipleDA() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfDA_Items.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMultipleDA_differentMABD()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_ListOfDA_Items_DifferentMABD.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMixedPO() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMixedPO_differentItemNumbers()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_DA_MixedPO_DifferentItems.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMixedPO_DifferentMABD()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items_DifferentMABD.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSingleDSDC() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_Single_DSDC_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMoreThanOneSSTKPo()
      throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static List<DeliveryDocument>
      getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items_AtlasConvertedItems.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static List<DeliveryDocument>
      getDeliveryDocumentsForMoreThanOneSSTKPoWithAllLinesCancelled() throws IOException {

    File resource =
        new ClassPathResource("GdmMappedResponseV2_Multi_SSTK_PO_With_All_Cancelled_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static List<DeliveryDocument>
      getDeliveryDocumentsForMoreThanOneSSTKPoWithPartiallyCancelledLines() throws IOException {
    File resource =
        new ClassPathResource(
                "GdmMappedResponseV2_Multi_SSTK_PO_with_partially_cancelled_po_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_PO_with_more_than_one_line.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForOneSSTKPoWithAllCancelledLines()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_PO_with_all_cancelled_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument>
      getDeliveryDocumentsForOneSSTKPoWithAllPartiallyCancelledLines() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_PO_with_partially_cancelled_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    return deliveryDocumentList;
  }

  public static DeliveryDocument getDeliveryDocumentForReceiveInstructionFromInstructionEntity()
      throws IOException {
    File resource =
        new ClassPathResource("ReceiveInstructionDeliveryDocumentFromInstructionEntity.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
    return deliveryDocument;
  }

  public static DeliveryDocument
      getDeliveryDocumentForReceiveInstructionFromInstructionEntityWithoutFinancialGroupCode()
          throws IOException {
    File resource =
        new ClassPathResource(
                "ReceiveInstructionDeliveryDocumentFromInstructionEntityWithoutFinancialGroupCode.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
    return deliveryDocument;
  }

  public static ReceivingException getPoLineCancelledException(
      String poNumber, Integer poLineNumber) {
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_POL_CANCELLED_ERROR);
    String errorMessage = String.format(gdmError.getErrorMessage(), poNumber, poLineNumber);
    return new ReceivingException(
        errorMessage,
        HttpStatus.INTERNAL_SERVER_ERROR,
        gdmError.getErrorCode(),
        gdmError.getErrorHeader());
  }

  public static ReceivingException getPoLineRejectedException(Integer poLineNumber) {
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_LINE_REJECTION_ERROR);
    String errorMessage = String.format(gdmError.getErrorMessage(), poLineNumber);
    return new ReceivingException(
        errorMessage,
        HttpStatus.INTERNAL_SERVER_ERROR,
        gdmError.getErrorCode(),
        gdmError.getErrorHeader());
  }

  public static DeliveryDocument
      getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem()
          throws IOException {
    File resource =
        new ClassPathResource("ReceiveInstructionDeliveryDocumentFromInstructionAtlasItem.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
    return deliveryDocument;
  }

  public static String getDeliveryDetailsByUriIncludesDummyPo() throws IOException {
    File resource =
        new ClassPathResource("GdmDeliveryDetailsResponseV2_IncludeDummyPO.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static List<DeliveryDocument> getDeliveryDocumentForDAItemWithDifferentUOMQuantity()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_DA_Item_Different_UOMs.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForRtsPut() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_Put_Label.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMulipleDADifferentDelivery()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseListOfMultipleDeliveryDA.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static String getAtlasDeliveryDocumentsPoDistByPoAndPoLine() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseDeliveryDocumentV2ByPoPoLine1.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getAtlasDeliveryDocumentsPoDistByPoAndPoLineofInvalidPOtypeForDAOrder()
      throws IOException {
    File resource =
        new ClassPathResource(
                "GdmMappedResponseDeliveryDocumentV2ByPoPoLineofInValidPOtypeForDAOrder.json")
            .getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String getAtlasDeliveryDocumentsPoDistByPoAndPoLinebyStoreNbr() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseDeliveryDocumentV2ByPoPoLineByStoreNbr.json")
            .getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForDACasePackAutomationSlotting()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_CasePackDAAutomationSlotting_Item.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      getDeliveryDocumentsForMoreThanOneSSTKPoWithAllLinesCancelled_V2() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_Multi_SSTK_PO_With_All_Cancelled_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(
        Arrays.asList(
            gson.fromJson(
                mockResponse,
                com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument[].class)));
  }

  public static List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      getDeliveryDocumentsForOneSSTKPoWithAllPartiallyCancelledLines_V2() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_PO_with_partially_cancelled_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocumentList =
        new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(
            gson.fromJson(
                mockResponse,
                com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument[].class)));
    return deliveryDocumentList;
  }
}
