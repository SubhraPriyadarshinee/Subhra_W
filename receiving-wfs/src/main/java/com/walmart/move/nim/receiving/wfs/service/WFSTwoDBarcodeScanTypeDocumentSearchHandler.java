package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RE_RECEIVING_SHIPMENT_NUMBER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUE_STRING;
import static com.walmart.move.nim.receiving.wfs.constants.WFSConstants.SHELF_LPN;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.PoAdditionalInfo;
import com.walmart.move.nim.receiving.core.model.ReceivingType;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.service.DeliveryDocumentsSearchHandler;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.utils.WFSUtility;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

public class WFSTwoDBarcodeScanTypeDocumentSearchHandler implements DeliveryDocumentsSearchHandler {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(WFSTwoDBarcodeScanTypeDocumentSearchHandler.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryServiceImpl;

  private Gson gsonForDate;
  @Autowired private TenantSpecificConfigReader configUtils;
  @ManagedConfiguration AppConfig appConfig;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  @Autowired WFSTclFreeHandler wfsTclFreeHandler;

  public WFSTwoDBarcodeScanTypeDocumentSearchHandler() {
    gsonForDate =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd"))
            .create();
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    LOGGER.info(
        "Entering WFSTwoDBarcodeScanTypeDocumentSearchHandler flow to fetch delivery documents");
    long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());

    // if docktag is not scanned and if we just enter the UPC,then we execute TCL-free receiving
    // flow!
    deliveryNumber =
        wfsTclFreeHandler.tclFreeReceive(instructionRequest, deliveryNumber, httpHeaders);

    // Normal GS1 Two-D Barcode based Receiving!
    List<DeliveryDocument> responseDeliveryDocuments = null;
    List<ScannedData> scannedDataList = null;

    if (!CollectionUtils.isEmpty(instructionRequest.getScannedDataList())) {
      scannedDataList = instructionRequest.getScannedDataList();
      LOGGER.info("scannedDataList = {}", scannedDataList);
    }
    String purchaseOrderPO = null;
    String gtin = null;
    Map<String, ScannedData> scannedDataMap = WFSUtility.getScannedDataMap(scannedDataList);

    // get deliveryDocument by using gtin and deliveryNumber via deliveryServiceImpl
    if (ReceivingType.GS1.getReceivingType().equalsIgnoreCase(instructionRequest.getReceivingType())
        && !CollectionUtils.isEmpty(scannedDataList)) {

      responseDeliveryDocuments =
          gs1TwoDBarcodeBasedReceiving(deliveryNumber, scannedDataList, httpHeaders);

      // Filter the response by PO from scannedDataList
      purchaseOrderPO = WFSUtility.getPoFromScannedDataMap(scannedDataMap);
      gtin = WFSUtility.getGtinFromScannedDataMap(scannedDataMap);

      if (Objects.isNull(purchaseOrderPO)) {
        LOGGER.error(
            "No Purchase Reference Number (PO) found in scanned data - Delivery: {} ScannedDataList: {}",
            deliveryNumber,
            scannedDataList);
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.NO_PO_FOUND, WFSConstants.WFS_TWO_D_BARCODE_PO_NOT_FOUND_ERROR_MSG);
      }
      responseDeliveryDocuments =
          filterDeliveryDocumentsByPO(purchaseOrderPO, responseDeliveryDocuments);

      // invalid PO scenario - Zero delivery documents returned from GDM
      if (CollectionUtils.isEmpty(responseDeliveryDocuments)) {
        LOGGER.error(
            "Invalid PO - zero delivery documents returned from GDM, Delivery: {} GTIN: {} Scanned PO: {}",
            deliveryNumber,
            gtin,
            purchaseOrderPO);
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.NO_PO_FOUND, WFSConstants.WFS_TWO_D_BARCODE_PO_NOT_FOUND_ERROR_MSG);
      }

      boxIdBasedAuditReceiving(
          scannedDataMap, responseDeliveryDocuments, deliveryNumber, httpHeaders);

    } else { // upc flow
      responseDeliveryDocuments =
          upcBasedReceiving(deliveryNumber, instructionRequest, httpHeaders);
      responseDeliveryDocuments =
          LPN25BasedReceiving(instructionRequest, responseDeliveryDocuments);
    }

    return responseDeliveryDocuments;
  }

  public List<DeliveryDocument> filterDeliveryDocumentsByPO(
      String purchaseOrderPO, List<DeliveryDocument> responseDeliveryDocuments) {
    LOGGER.info("Filtering Delivery Documents by PO");
    List<DeliveryDocument> filteredList = new ArrayList<>();
    if (!CollectionUtils.isEmpty(responseDeliveryDocuments)) {
      filteredList =
          responseDeliveryDocuments
              .stream()
              .filter(
                  deliveryDocument ->
                      deliveryDocument
                          .getDeliveryDocumentLines()
                          .stream()
                          .anyMatch(
                              deliveryDocumentLine ->
                                  deliveryDocumentLine
                                      .getPurchaseReferenceNumber()
                                      .equalsIgnoreCase(purchaseOrderPO)))
              .collect(Collectors.toList());
    }
    return filteredList;
  }

  // This is Re-Receiving Shelf Container Flow!
  List<DeliveryDocument> LPN25BasedReceiving(
      InstructionRequest instructionRequest, List<DeliveryDocument> responseDeliveryDocuments) {
    if (Objects.nonNull(instructionRequest.getAdditionalParams())
        && instructionRequest
            .getAdditionalParams()
            .containsKey(ReceivingConstants.IS_RE_RECEIVING_LPN_FLOW)) {
      responseDeliveryDocuments =
          filterDeliveryDocumentsByPO(
              instructionRequest
                  .getAdditionalParams()
                  .get(ReceivingConstants.PURCHASE_ORDER_NUMBER)
                  .toString(),
              responseDeliveryDocuments);
      if (!CollectionUtils.isEmpty(responseDeliveryDocuments)) {
        setPoAdditionalInfo(responseDeliveryDocuments);
        responseDeliveryDocuments
            .get(0)
            .getAdditionalInfo()
            .setShelfLPN(instructionRequest.getAdditionalParams().get(SHELF_LPN).toString());
        responseDeliveryDocuments
            .get(0)
            .getAdditionalInfo()
            .setReReceivingShipmentNumber(
                instructionRequest
                    .getAdditionalParams()
                    .get(RE_RECEIVING_SHIPMENT_NUMBER)
                    .toString()); // passed to AOS to make a shipment update call to GDM!
        responseDeliveryDocuments.get(0).getAdditionalInfo().setIsAuditRequired(Boolean.FALSE);
      }
    }
    return responseDeliveryDocuments;
  }

  List<DeliveryDocument> gs1TwoDBarcodeBasedReceiving(
      Long deliveryNumber, List<ScannedData> scannedDataList, HttpHeaders httpHeaders)
      throws ReceivingDataNotFoundException, ReceivingBadDataException {
    LOGGER.info("GS1-receivingType flow ... Delivery: {}", deliveryNumber);
    List<DeliveryDocument> responseDeliveryDocuments = null;
    for (ScannedData scannedData : scannedDataList) {
      if (ApplicationIdentifier.GTIN
          .getApplicationIdentifier()
          .equals(scannedData.getApplicationIdentifier())) {
        LOGGER.info(
            "Fetching delivery documents via GTIN in ScannedDataList, Delivery: {} GTIN: {} ",
            deliveryNumber,
            scannedData.getValue());
        try {
          String deliveryDocumentResponseByGtin =
              deliveryServiceImpl.findDeliveryDocument(
                  deliveryNumber,
                  scannedData.getValue(),
                  httpHeaders); // this function calls findDeliveryDocumentByGtin
          responseDeliveryDocuments =
              new ArrayList<>(
                  Arrays.asList(
                      gsonForDate.fromJson(
                          deliveryDocumentResponseByGtin, DeliveryDocument[].class)));
        } catch (ReceivingException e) { // item not found error
          LOGGER.error("Item not found for delivery, Delivery: {}", deliveryNumber);
          throw new ReceivingDataNotFoundException(
              ExceptionCodes.ITEM_UPC_NOT_FOUND_FOR_DELIVERY,
              WFSConstants.WFS_NOT_FOUND_ERROR_FROM_GDM);
        } catch (GDMServiceUnavailableException e) {
          // any other error thrown by gdm like GDM_NETWORK_ERROR
          LOGGER.error(ExceptionDescriptionConstants.ERROR_THROWN_BY_GDM, deliveryNumber);
          throw new ReceivingBadDataException(
              ExceptionCodes.GDM_NETWORK_ERROR, WFSConstants.WFS_ERROR_FROM_GDM);
        }
        break;
      }
    }
    return responseDeliveryDocuments;
  }

  List<DeliveryDocument> upcBasedReceiving(
      Long deliveryNumber, InstructionRequest instructionRequest, HttpHeaders httpHeaders)
      throws ReceivingBadDataException, ReceivingDataNotFoundException {
    LOGGER.info("Fetching delivery documents vis UPC flow");
    List<DeliveryDocument> responseDeliveryDocuments = null;
    try {
      String deliveryDocumentResponseByUpc =
          deliveryServiceImpl.findDeliveryDocument(
              deliveryNumber, instructionRequest.getUpcNumber(), httpHeaders);
      responseDeliveryDocuments =
          new ArrayList<>(
              Arrays.asList(
                  gsonForDate.fromJson(deliveryDocumentResponseByUpc, DeliveryDocument[].class)));
    } catch (ReceivingException e) { // item not found error
      LOGGER.error("Item not found for delivery Delivery: {}", deliveryNumber);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.ITEM_UPC_NOT_FOUND_FOR_DELIVERY,
          WFSConstants.WFS_NOT_FOUND_ERROR_FROM_GDM);
    } catch (GDMServiceUnavailableException e) {
      // any other error thrown by gdm like GDM_NETWORK_ERROR
      LOGGER.error(ExceptionDescriptionConstants.ERROR_THROWN_BY_GDM, deliveryNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NETWORK_ERROR, WFSConstants.WFS_ERROR_FROM_GDM);
    }
    return responseDeliveryDocuments;
  }

  void boxIdBasedAuditReceiving(
      Map<String, ScannedData> scannedDataMap,
      List<DeliveryDocument> responseDeliveryDocuments,
      Long deliveryNumber,
      HttpHeaders httpHeaders) {
    if (!configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED)
        || !scannedDataMap.containsKey(ApplicationIdentifier.SSCC.getApplicationIdentifier())) {
      return;
    }
    String packId =
        scannedDataMap.get(ApplicationIdentifier.SSCC.getApplicationIdentifier()).getValue();
    try {
      PoAdditionalInfo poAdditionalInfo = responseDeliveryDocuments.get(0).getAdditionalInfo();
      if (Objects.isNull(poAdditionalInfo)) poAdditionalInfo = new PoAdditionalInfo();
      responseDeliveryDocuments.get(0).setAdditionalInfo(poAdditionalInfo);
      responseDeliveryDocuments.get(0).getAdditionalInfo().setPackId(packId);
      responseDeliveryDocuments.get(0).getAdditionalInfo().setIsAuditRequired(Boolean.TRUE);
      LOGGER.info(
          "\nGetting Audit Information from GDM with packId: {},deliveryNumber: {}",
          packId,
          deliveryNumber);
      getPalletPackInformation(responseDeliveryDocuments, deliveryNumber, packId, httpHeaders);
    } catch (ReceivingException e) { // item not found error
      LOGGER.error(
          "Not found Error from GDM while fetching audit info for packid : {},deliveryNumber : {}, error = {}",
          packId,
          deliveryNumber,
          e);
    } catch (GDMServiceUnavailableException e) {
      // any other error thrown by gdm like GDM_NETWORK_ERROR
      LOGGER.error(
          "Error thrown by GDM for packId : {},deliveryNumber : {}, error = {}",
          packId,
          deliveryNumber,
          e);
    }
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  public void setPoAdditionalInfo(List<DeliveryDocument> responseDeliveryDocuments) {
    PoAdditionalInfo poAdditionalInfo = responseDeliveryDocuments.get(0).getAdditionalInfo();
    if (Objects.isNull(poAdditionalInfo)) poAdditionalInfo = new PoAdditionalInfo();
    responseDeliveryDocuments.get(0).setAdditionalInfo(poAdditionalInfo);
  }

  public void getPalletPackInformation(
      List<DeliveryDocument> responseDeliveryDocuments,
      Long deliveryNumber,
      String packId,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String response = findAuditInfoByPackId(deliveryNumber, packId, httpHeaders);
    LOGGER.info("Response from GDM for pack/audit = {}", response);

    setPoAdditionalInfo(responseDeliveryDocuments);

    // Need only one field from nested Json, so avoiding dto classes!
    String isAuditRequiredFlag =
        JsonParser.parseString(response)
            .getAsJsonObject()
            .get(WFSConstants.PACKS_KEY)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(WFSConstants.AUDIT_DETAILS)
            .getAsJsonObject()
            .get(WFSConstants.AUDIT_REQUIRED)
            .getAsString();

    // set the fields in responseDeliveryDocuments and pass it to downstream!
    responseDeliveryDocuments.get(0).getAdditionalInfo().setPackId(packId);
    responseDeliveryDocuments
        .get(0)
        .getAdditionalInfo()
        .setIsAuditRequired(isAuditRequiredFlag.equalsIgnoreCase(TRUE_STRING));

    LOGGER.info(
        "Audit required for packId = {} is {}\n",
        packId,
        isAuditRequiredFlag.equalsIgnoreCase(TRUE_STRING));
  }

  public String findAuditInfoByPackId(Long deliveryNumber, String packId, HttpHeaders headers)
      throws ReceivingException {
    Map<String, String> pathParameters = new HashMap<>();
    pathParameters.put(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    pathParameters.put(WFSConstants.IDENTIFIER, packId);
    headers.add(
        HttpHeaders.ACCEPT, WFSConstants.APPLICATION_VND_DELIVERY_SHIPMENT_SCAN_RESPONSE3_JSON);
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + WFSConstants.GDM_PALLET_PACK_SCAN_API, pathParameters)
            .toString();
    return deliveryServiceImpl.gmdRestCallResponse(url, headers, simpleRestConnector).getBody();
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
