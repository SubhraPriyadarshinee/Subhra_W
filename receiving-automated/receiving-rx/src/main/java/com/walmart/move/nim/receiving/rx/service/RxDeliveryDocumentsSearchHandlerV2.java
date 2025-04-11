package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RxDeliveryDocumentsMapperV2;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Sgtin;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ShipmentsContainersV2Request;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryDocumentsSearchHandler;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

import java.util.*;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RxDeliveryDocumentsSearchHandlerV2 implements DeliveryDocumentsSearchHandler {

  @Resource private Gson gson;
  @Resource private RxDeliveryDocumentsMapperV2 rxDeliveryDocumentsMapperV2;
  @Resource private CreateInstructionDataValidator createInstructionDataValidator;
  @Resource private CreateInstructionServiceHelper createInstructionServiceHelper;
  @Resource private RxDeliveryServiceImpl rxDeliveryServiceImpl;
  @Resource private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * @param instructionRequest instruction request
   * @param httpHeaders headers
   * @return delivery documents OR EMPTY for [isEpcisDataNotFound && isAutoSwitchEpcisToAsn] || !isEpcisFlagEnabled
   */
  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    //Removing SSCC in the request for scans inside Multi-Sku screen
    if (RxReceivingType.MULTI_SKU_FLOW.equals(createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest))) {
      instructionRequest.setSscc("");
    }
    ShipmentsContainersV2Request shipmentsContainersV2Request = getShipmentsContainersV2RequestFromInstruction(instructionRequest);

    // queryParams
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("includeLevel", "0");

    // currentNodeResponse
    SsccScanResponse currentNodeResponse =
        rxDeliveryServiceImpl.getCurrentNode(
            shipmentsContainersV2Request, httpHeaders, queryParams);

    //get details of the Parent to display the details of Multi-SkuPallet
    boolean isMultiSkuPalletResponse = false;
    RxReceivingType receivingTypeFromUI = createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest);
    boolean isMultiSkuReceivingType = Objects.nonNull(receivingTypeFromUI) && receivingTypeFromUI.equals(RxReceivingType.MULTI_SKU_FLOW);

    if (Objects.nonNull(currentNodeResponse) && createInstructionServiceHelper.isMultiSkuRootNode(currentNodeResponse)
            && !isMultiSkuReceivingType) {
      ShipmentsContainersV2Request shipmentContainersParentV2Request = constructRequestForMultiSkuPallet(currentNodeResponse.getAdditionalInfo().getContainers().get(0), instructionRequest.getDeliveryNumber());
      currentNodeResponse =
              rxDeliveryServiceImpl.getCurrentNode(
                      shipmentContainersParentV2Request, httpHeaders, queryParams);
      isMultiSkuPalletResponse = true;
    }

    // validate currentNodeResponse
    if (Objects.isNull(currentNodeResponse) || !createInstructionDataValidator.validateCurrentNodeResponse(currentNodeResponse,isMultiSkuReceivingType)) {
      log.info("[LT] Returning Empty response. BAU flows to be executed");
      return Collections.emptyList();
    }

    //validate 2D barcode - expiry and lot (For multisku, sscc in instructionRequest would be non-null)
    if(StringUtils.isEmpty(instructionRequest.getSscc()) && !isMultiSkuPalletResponse){
      createInstructionDataValidator.validateCurrentNodeExpiryAndLot(instructionRequest, currentNodeResponse);
    }

    log.info("[LT] GDM CurrentNode API response {} ", currentNodeResponse);

    // proceed with mapping
    return rxDeliveryDocumentsMapperV2.mapGdmResponse(currentNodeResponse);
  }

  private static ShipmentsContainersV2Request getShipmentsContainersV2RequestFromInstruction(InstructionRequest instructionRequest) {
    // construct currentNodeRequest
    String sscc = instructionRequest.getSscc();
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    ShipmentsContainersV2Request shipmentsContainersV2Request = new ShipmentsContainersV2Request();
    shipmentsContainersV2Request.setDeliveryNumber(instructionRequest.getDeliveryNumber());
    if (StringUtils.isNotBlank(sscc)) {
      shipmentsContainersV2Request.setSscc(sscc);
    } else { // sgtin
      String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
      String serial = scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey()).getValue();
      shipmentsContainersV2Request.setSgtin(new Sgtin(serial, gtin));
    }
    return shipmentsContainersV2Request;
  }

  /**
   * @param instructionRequest instruction request
   * @param httpHeaders headers
   * @param parentId parent id query param
   * @return GDM currentAndSiblings API response OR NULL if 204
   */
  public SsccScanResponse getCurrentAndSiblings(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders, String parentId) {
    // construct currentAndSiblingsRequest
    String sscc = instructionRequest.getSscc();
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    ShipmentsContainersV2Request currentAndSiblingsRequest = new ShipmentsContainersV2Request();
    currentAndSiblingsRequest.setDeliveryNumber(instructionRequest.getDeliveryNumber());
    if (StringUtils.isNotBlank(sscc)) { // sscc
      currentAndSiblingsRequest.setSscc(sscc);
    } else { // sgtin
      String gtin = scannedDataMap.get(ReceivingConstants.KEY_GTIN).getValue();
      String serial = scannedDataMap.get(ReceivingConstants.KEY_SERIAL).getValue();
      currentAndSiblingsRequest.setSgtin(new Sgtin(serial, gtin));
    }
    log.info("Calling GDM's currentAndSiblings API with request {}", currentAndSiblingsRequest);

    // queryParams
    Map<String, String> queryParams = new HashMap<>();
    if (parentId != null) {
      queryParams.put("parentId", parentId);
      log.info("Calling GDM's currentAndSiblings API with Parent ID {}", parentId);
    }

    // currentAndSiblingsResponse
    return rxDeliveryServiceImpl.getCurrentAndSiblings(
        currentAndSiblingsRequest, httpHeaders, queryParams);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(String deliveryNumber, Integer itemNumber, HttpHeaders httpHeaders) throws ReceivingException {
    throw new ReceivingNotImplementedException(
            ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
            ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  private ShipmentsContainersV2Request constructRequestForMultiSkuPallet(SsccScanResponse.Container rootLevelMultiSkuContainer, String deliveryNumber) {
    ShipmentsContainersV2Request shipmentContainersParentV2Request = new ShipmentsContainersV2Request();
    shipmentContainersParentV2Request.setDeliveryNumber(deliveryNumber);
    if (StringUtils.isNotBlank(rootLevelMultiSkuContainer.getSscc())) { // sscc
      shipmentContainersParentV2Request.setSscc(rootLevelMultiSkuContainer.getSscc());
    } else { // sgtin
      String gtin = rootLevelMultiSkuContainer.getGtin();
      String serial = rootLevelMultiSkuContainer.getSerial();
      shipmentContainersParentV2Request.setSgtin(new Sgtin(serial, gtin));
    }
    return shipmentContainersParentV2Request;
  }
}
