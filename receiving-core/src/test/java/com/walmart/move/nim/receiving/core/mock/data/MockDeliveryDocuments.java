package com.walmart.move.nim.receiving.core.mock.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
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

  public static List<DeliveryDocument> getDeliveryDocumentsForSSTK_IQSIntegrationEnabled()
      throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_SSTK_Item_IQSIntegration.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
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

  public static List<DeliveryDocument> getDeliveryDocumentsForMultipleDA() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfDA_Items.json").getFile();
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

  public static List<DeliveryDocument> getDeliveryDocumentsForSingleDSDC() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_Single_DSDC_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMoreThanOneSSTKPo()
      throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument>
      getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items_AtlasConvertedItems.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return deliveryDocumentList;
  }

  public static List<DeliveryDocument>
      getDeliveryDocumentsForMoreThanOneSSTKPoWithAllLinesCancelled() throws IOException {

    File resource =
        new ClassPathResource("GdmMappedResponseV2_Multi_SSTK_PO_With_All_Cancelled_lines.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.addAll(
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    return deliveryDocumentList;
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

  public static List<DeliveryDocument> getDSDCDeliveryDocuments() throws IOException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_ListOfDA_Items_For_DSDC.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }
}
