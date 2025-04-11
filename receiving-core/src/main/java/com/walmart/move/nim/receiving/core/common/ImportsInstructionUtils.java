package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ITEM_CONFIG_ERROR_CODE;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.ImportSlottingServiceImpl;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class ImportsInstructionUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ImportsInstructionUtils.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ImportSlottingServiceImpl importSlottingService;
  @Autowired private ItemConfigApiClient itemConfigApiClient;

  public boolean isStorageTypePo(DeliveryDocument deliveryDocument) {
    int poTypeCode = deliveryDocument.getPoTypeCode();
    if (!CollectionUtils.isEmpty(appConfig.getPoTypesForStorageCheck())
        && appConfig.getPoTypesForStorageCheck().contains(poTypeCode)) {
      LOG.info("Po is storage type with POtype:{}", poTypeCode);
      return Boolean.TRUE;
    } else LOG.info("Po is not storage type with POtype:{}", poTypeCode);
    return Boolean.FALSE;
  }

  public void storageChecks(HttpHeaders httpHeaders, List<DeliveryDocument> deliveryDocuments_gdm)
      throws ReceivingException {
    DeliveryDocument deliveryDocument = deliveryDocuments_gdm.get(0);
    if (Objects.equals(Integer.valueOf(deliveryDocument.getPoDCNumber()), getFacilityNum())
        && isStorageTypePo(deliveryDocument)) {
      // call item Config service to update isAtlasConvertedItem key in delivery docs
      validateAndSetIfAtlasConvertedItem(deliveryDocument, httpHeaders);
    }
  }

  public void getPrimeSlot(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    LOG.info(
        "Calling for prime slot for item:{}",
        deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr());
    TenantContext.get().setCreateInstrGetPrimeSlotCallStart(System.currentTimeMillis());
    SlottingPalletResponse slottingPalletResponse =
        importSlottingService.getPrimeSlot(
            instructionRequest.getMessageId(),
            Arrays.asList(deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr()),
            0,
            httpHeaders);
    TenantContext.get().setCreateInstrGetPrimeSlotCallEnd(System.currentTimeMillis());
    if (Objects.nonNull(slottingPalletResponse)
        && Objects.nonNull(slottingPalletResponse.getLocations().get(0).getLocation())) {
      LOG.info(
          "Received prime slot:{} for item:{}",
          slottingPalletResponse.getLocations().get(0).getLocation(),
          deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr());
    }
  }

  public void validateAndSetIfAtlasConvertedItem(
      DeliveryDocument deliveryDocument, HttpHeaders httpHeaders) throws ReceivingException {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ITEM_CONFIG_SERVICE_ENABLED)) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      if (itemConfigApiClient.isAtlasConvertedItem(
          deliveryDocumentLine.getItemNbr(), httpHeaders)) {
        // Item is Atlas converted.
        ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
        if (Objects.isNull(itemData)) {
          itemData = new ItemData();
        }
        itemData.setAtlasConvertedItem(true);
        LOG.info("Item {} is Atlas converted item", deliveryDocumentLine.getItemNbr());
        deliveryDocumentLine.setAdditionalInfo(itemData);
      } else {
        // Throw exception is Item is not Atlas converted
        LOG.error("Item {} is not Atlas converted.", deliveryDocumentLine.getItemNbr());
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.ITEM_NOT_CONVERTED_TO_ATLAS_ERROR_MSG)
                .errorCode(ITEM_CONFIG_ERROR_CODE)
                .errorKey(ExceptionCodes.ITEM_NOT_CONVERTED_TO_ATLAS)
                .build();
        throw ReceivingException.builder()
            .httpStatus(BAD_REQUEST)
            .errorResponse(errorResponse)
            .build();
      }
    } else {
      // Throw exception if Item Config Service is not enabled.
      LOG.error("Item config service is not enabled.");
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.ITEM_CONFIG_SERVICE_NOT_ENABLED_ERROR_MSG)
              .errorCode(ITEM_CONFIG_ERROR_CODE)
              .errorKey(ExceptionCodes.ITEM_CONFIG_SERVICE_NOT_ENABLED)
              .build();
      throw ReceivingException.builder().httpStatus(CONFLICT).errorResponse(errorResponse).build();
    }
  }
}
