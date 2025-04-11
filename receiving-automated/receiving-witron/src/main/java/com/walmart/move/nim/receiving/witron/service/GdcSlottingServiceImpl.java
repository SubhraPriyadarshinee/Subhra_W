package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.SLOTTING_ROTATE_DATE_FORMAT_YYYY_MM_DD;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.SLOT_NOT_FOUND;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ObjectUtils.anyNotNull;

import com.walmart.move.nim.receiving.core.client.slotting.SlottingContainer;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingContainerContents;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildRequest;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class GdcSlottingServiceImpl {

  private static final Logger LOGGER = LoggerFactory.getLogger(GdcSlottingServiceImpl.class);
  private static final String ERROR_TAG = "error";

  @Autowired private SlottingRestApiClient slottingRestApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private GDCFlagReader gdcFlagReader;

  public @Valid SlottingPalletBuildResponse acquireSlot(
      InstructionRequest instructionRequest,
      String containerStatus,
      String containerTrackingId,
      Map<String, Object> httpHeaders)
      throws ReceivingException {

    SlottingPalletBuildRequest slottingPalletBuildRequest = new SlottingPalletBuildRequest();
    slottingPalletBuildRequest.setMessageId(
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    slottingPalletBuildRequest.setSourceLocation(instructionRequest.getDoorNumber());
    slottingPalletBuildRequest.setContainer(
        getSlottingContainer(
            containerStatus,
            containerTrackingId,
            instructionRequest.getDeliveryDocuments().get(0)));

    try {
      return slottingRestApiClient.palletBuild(slottingPalletBuildRequest, httpHeaders);
    } catch (SlottingRestApiClientException e) {
      LOGGER.error(e.getMessage(), e);
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue("SLOTTING_GENERIC_ERROR");
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          e.getHttpStatus(),
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }
  }

  private SlottingContainer getSlottingContainer(
      String containerStatus, String containerTrackingId, DeliveryDocument deliveryDocument) {
    SlottingContainer slottingContainer = new SlottingContainer();
    slottingContainer.setContainerStatus(containerStatus);
    slottingContainer.setContainerTrackingId(containerTrackingId);

    List<SlottingContainerContents> containerContents = new ArrayList<>();
    for (DeliveryDocumentLine documentLine : deliveryDocument.getDeliveryDocumentLines()) {
      SlottingContainerContents containerContent = new SlottingContainerContents();
      containerContent.setGtin(documentLine.getGtin());
      containerContent.setItemNbr(documentLine.getItemNbr());

      ItemData itemData = documentLine.getAdditionalInfo();
      containerContent.setWarehouseAreaCode(Integer.valueOf(itemData.getWarehouseAreaCode()));
      containerContent.setGroupCode(itemData.getWarehouseGroupCode());
      final String profiledWarehouseArea =
          StringUtils.isNotBlank(itemData.getProfiledWarehouseArea())
              ? itemData.getProfiledWarehouseArea()
              : documentLine.getProfiledWarehouseArea();
      containerContent.setProfiledWarehouseArea(profiledWarehouseArea);

      containerContents.add(containerContent);
    }
    slottingContainer.setContents(containerContents);
    return slottingContainer;
  }

  /**
   * Get the destination from slotting
   *
   * @param container
   * @param httpHeaders
   * @return SlottingPalletBuildResponse
   * @throws ReceivingException
   */
  public SlottingPalletBuildResponse getDivertLocation(
      Container container, HttpHeaders httpHeaders, String statusForSlotting)
      throws ReceivingException {
    SlottingPalletBuildResponse slottingResponse = null;
    List<SlottingContainerContents> slottingContainerContents = new ArrayList<>();
    for (ContainerItem containerItem : container.getContainerItems()) {
      SlottingContainerContents slottingContainerContent = new SlottingContainerContents();
      slottingContainerContent.setItemNbr(containerItem.getItemNumber());
      slottingContainerContent.setGtin(containerItem.getGtin());
      slottingContainerContent.setProfiledWarehouseArea(containerItem.getProfiledWarehouseArea());
      slottingContainerContent.setWarehouseAreaCode(
          containerItem.getWarehouseAreaCode() != null
              ? Integer.valueOf(containerItem.getWarehouseAreaCode())
              : null);
      slottingContainerContent.setGroupCode(containerItem.getWarehouseGroupCode());
      slottingContainerContents.add(slottingContainerContent);
    }

    SlottingContainer slottingContainer = new SlottingContainer();

    slottingContainer.setContainerStatus(statusForSlotting);
    slottingContainer.setContainerTrackingId(container.getTrackingId());
    slottingContainer.setContents(slottingContainerContents);

    SlottingPalletBuildRequest slottingRequest = new SlottingPalletBuildRequest();
    slottingRequest.setMessageId(
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    slottingRequest.setSourceLocation(
        container.getLocation()); // onHold result saved in container.location for offHold
    slottingRequest.setContainer(slottingContainer);

    try {
      Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
      slottingResponse = slottingRestApiClient.palletBuild(slottingRequest, headers);
    } catch (SlottingRestApiClientException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode());
    }

    return slottingResponse;
  }

  public SlottingPalletResponse acquireSlotManualGdc(
      DeliveryDocumentLine deliveryDocumentLine,
      ReceiveInstructionRequest receiveInstructionRequest,
      String containerTrackingId,
      HttpHeaders httpHeaders) {
    httpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);
    httpHeaders.set(
        GdcConstants.SUBCENTER_ID_HEADER,
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(),
            ReceivingConstants.SUBCENTER_ID_HEADER,
            GdcConstants.SUBCENTER_DEFAULT_VALUE));
    httpHeaders.set(
        ReceivingConstants.ORG_UNIT_ID_HEADER,
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(),
            ReceivingConstants.ORG_UNIT_ID_HEADER,
            ReceivingConstants.ORG_UNIT_ID_DEFAULT_VALUE));

    String rotateDate =
        nonNull(receiveInstructionRequest.getRotateDate())
            ? new SimpleDateFormat(SLOTTING_ROTATE_DATE_FORMAT_YYYY_MM_DD)
                .format(receiveInstructionRequest.getRotateDate())
            : new SimpleDateFormat(SLOTTING_ROTATE_DATE_FORMAT_YYYY_MM_DD).format(new Date());

    SlottingPalletRequest slottingGdcPalletRequest = new SlottingPalletRequest();
    slottingGdcPalletRequest.setMessageId(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    // Generate Move - Yes -- Slotting sends the put-away instruction to move component to generate
    // the move
    // Generate Move - No -- Same Old flow, receiving sends the put-away instruction to move
    // component to generate the move
    slottingGdcPalletRequest.setGenerateMove(!gdcFlagReader.isReceivingInstructsPutAwayMoveToMM());

    SlottingContainerDetails slottingGdcContainerDetails = new SlottingContainerDetails();
    List<SlottingContainerDetails> slottingGdcContainerDetailsList = new ArrayList<>();
    SlottingContainerItemDetails slottingGdcContainerItemDetails =
        new SlottingContainerItemDetails();
    List<SlottingContainerItemDetails> slottingGdcContainerItemDetailsList = new ArrayList<>();

    slottingGdcContainerItemDetails.setItemNbr(deliveryDocumentLine.getItemNbr());
    slottingGdcContainerItemDetails.setQty(receiveInstructionRequest.getQuantity());
    slottingGdcContainerItemDetails.setQtyUom(receiveInstructionRequest.getQuantityUOM());
    slottingGdcContainerItemDetails.setVnpkRatio(deliveryDocumentLine.getVendorPack());
    slottingGdcContainerItemDetails.setWhpkRatio(deliveryDocumentLine.getWarehousePack());
    slottingGdcContainerItemDetails.setWareHouseTi(deliveryDocumentLine.getPalletTie());
    slottingGdcContainerItemDetails.setWareHouseHi(deliveryDocumentLine.getPalletHigh());
    if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())
        && Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getWhpkDimensions())) {
      slottingGdcContainerItemDetails.setWhpkHeight(
          deliveryDocumentLine.getAdditionalInfo().getWhpkDimensions().getHeight());
      slottingGdcContainerItemDetails.setWhpkWidth(
          deliveryDocumentLine.getAdditionalInfo().getWhpkDimensions().getWidth());
      slottingGdcContainerItemDetails.setWhpkLength(
          deliveryDocumentLine.getAdditionalInfo().getWhpkDimensions().getDepth());
      slottingGdcContainerItemDetails.setWhpkWeight(
          deliveryDocumentLine.getAdditionalInfo().getWeight());
    }

    slottingGdcContainerItemDetails.setRotateDate(rotateDate);
    slottingGdcContainerItemDetailsList.add(slottingGdcContainerItemDetails);

    slottingGdcContainerDetails.setContainerItemsDetails(slottingGdcContainerItemDetailsList);
    slottingGdcContainerDetails.setContainerTrackingId(containerTrackingId);
    slottingGdcContainerDetails.setPurchaseOrderNum(
        deliveryDocumentLine.getPurchaseReferenceNumber());
    slottingGdcContainerDetails.setPurchaseOrderLineNum(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    // Setting the location to be used by slotting to send the location for move component to create
    // the moves
    slottingGdcContainerDetails.setFromLocation(receiveInstructionRequest.getDoorNumber());
    if (Objects.nonNull(TenantContext.getAdditionalParams())
        && Objects.nonNull(
            TenantContext.getAdditionalParams().get(ReceivingConstants.CONTAINER_CREATE_TS))) {
      slottingGdcContainerDetails.setContainerCreateTs(
          TenantContext.getAdditionalParams().get(CONTAINER_CREATE_TS).toString());
    }
    slottingGdcContainerDetailsList.add(slottingGdcContainerDetails);

    slottingGdcPalletRequest.setContainerDetails(slottingGdcContainerDetailsList);

    validatedRequest(slottingGdcPalletRequest);
    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingGdcPalletRequest, httpHeaders);

    validateGdcSlottingResponse(slottingPalletResponse, slottingGdcPalletRequest);
    return slottingPalletResponse;
  }

  /**
   * This method validates Smart Slotting Response and throws error. If Slotting returns
   * GLS-SMART-SLOTING-4040008 Error response then will set Slot to Slot not found and continue
   * receiving
   *
   * @param slottingPalletResponse
   * @param slottingPalletRequest
   */
  private void validateGdcSlottingResponse(
      SlottingPalletResponse slottingPalletResponse, SlottingPalletRequest slottingPalletRequest) {
    if (CollectionUtils.isNotEmpty(slottingPalletResponse.getLocations())) {

      for (SlottingDivertLocations location : slottingPalletResponse.getLocations()) {
        if (ERROR_TAG.equals(location.getType())) {
          LOGGER.error(
              "Error response from Smart Slotting for request {}, response body = {}",
              slottingPalletRequest,
              slottingPalletResponse);
          Object[] errorMessageValues = new Object[] {location.getCode(), location.getDesc()};

          if (SLOTTING_AUTO_SLOT_NOT_AVAILABLE.equals(location.getCode())) {
            slottingPalletResponse.getLocations().get(0).setLocation(SLOT_NOT_FOUND);
          } else if (SLOTTING_PRIME_SLOT_NOT_FOUND.equals(location.getCode())) {
            throw new ReceivingBadDataException(
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                String.format(SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
                String.valueOf(
                    slottingPalletRequest
                        .getContainerDetails()
                        .get(0)
                        .getContainerItemsDetails()
                        .get(0)
                        .getItemNbr()));
          } else {
            throw new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
                errorMessageValues);
          }
        }
      }
    }
  }

  private void validatedRequest(SlottingPalletRequest slottingGdcPalletRequest) {
    LOGGER.info("Request body for slotting api {}", slottingGdcPalletRequest);

    List<SlottingContainerDetails> slottingContainerDetailsList =
        slottingGdcPalletRequest.getContainerDetails();
    if (Objects.isNull(slottingGdcPalletRequest.getContainerDetails())
        || StringUtils.isEmpty(slottingContainerDetailsList.get(0).getPurchaseOrderNum())
        || StringUtils.isEmpty(slottingContainerDetailsList.get(0).getContainerTrackingId())) {
      LOGGER.error("Missing mandatory data to getSlot request {}", slottingGdcPalletRequest);
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR, ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }

    List<SlottingContainerItemDetails> slottingContainerItemDetailsList =
        slottingGdcPalletRequest.getContainerDetails().get(0).getContainerItemsDetails();
    if (Objects.isNull(slottingContainerItemDetailsList.get(0))
        || StringUtils.isEmpty(slottingContainerItemDetailsList.get(0).getRotateDate())
        || !anyNotNull(
            slottingContainerItemDetailsList.get(0).getItemNbr(),
            slottingContainerItemDetailsList.get(0).getVnpkRatio(),
            slottingContainerItemDetailsList.get(0).getWhpkRatio(),
            slottingContainerItemDetailsList.get(0).getWareHouseTi(),
            slottingContainerItemDetailsList.get(0).getWareHouseHi(),
            slottingContainerItemDetailsList.get(0).getWhpkHeight(),
            slottingContainerItemDetailsList.get(0).getWhpkWeight(),
            slottingContainerItemDetailsList.get(0).getWhpkLength(),
            slottingContainerItemDetailsList.get(0).getWhpkWidth())) {
      LOGGER.error("Missing mandatory data to getSlot request {}", slottingGdcPalletRequest);
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR, ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }
  }
}
