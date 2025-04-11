package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.createDcFinAdjustRequest;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CONFIRM_PO_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.PO_ALREADY_FINALIZED;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.conversionToVendorPack;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FLOW_NAME;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IDEM_POTENCY_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_EXCEPTIONS_URI;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_VTR_V2;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INV_V2_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MANUAL_GDC_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SPLUNK_ALERT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.STATUS_BACKOUT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUE_STRING;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_FLOW;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_DESC;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.client.inventory.model.ContainerAdjustmentData;
import com.walmart.move.nim.receiving.core.client.inventory.model.InventoryVtrRequest;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.ReceiptHelper;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.InventoryExceptionRequest;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Component(value = ReceivingConstants.DEFAULT_CANCEL_CONTAINER_PROCESSOR)
public class DefaultCancelContainerProcessor implements CancelContainerProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultCancelContainerProcessor.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private RestUtils restUtils;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private Gson gson;
  @Autowired private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;

  @Autowired private GlsRestApiClient glsRestApiClient;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  @Autowired private ReceiptHelper receiptHelper;
  @Autowired private MovePublisher movePublisher;
  @Autowired @Lazy private ItemConfigApiClient itemConfig;

  /**
   * Notify receiving adjustment to Inventory
   *
   * @param trackingId
   * @param httpHeaders
   * @return CancelContainerResponse
   */
  private CancelContainerResponse notifyVtrToInv(String trackingId, HttpHeaders httpHeaders) {
    CancelContainerResponse cancelContainerResponse = null;
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    String url;
    String requestBody;

    if (configUtils.getConfiguredFeatureFlag(getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_VTR_V2;
      httpHeaders.add(
          IDEM_POTENCY_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY) + "-" + trackingId);
      httpHeaders.add(FLOW_NAME, VTR_FLOW);
      requestBody =
          gson.toJson(
              InventoryVtrRequest.builder()
                  .containerAdjustmentData(
                      ContainerAdjustmentData.builder()
                          .reasonCode(VTR_REASON_CODE)
                          .reasonDesc(VTR_REASON_DESC)
                          .comment(VTR_REASON_DESC)
                          .trackingId(trackingId)
                          .vtrFlag(TRUE_STRING)
                          .build())
                  .build());
    } else {
      InventoryExceptionRequest inventoryExceptionRequest = new InventoryExceptionRequest();
      inventoryExceptionRequest.setTrackingId(trackingId);
      inventoryExceptionRequest.setComment(ReceivingConstants.VTR_COMMENT);
      inventoryExceptionRequest.setReasonCode(String.valueOf(VTR_REASON_CODE));

      url = appConfig.getInventoryBaseUrl() + INVENTORY_EXCEPTIONS_URI;
      requestBody = gson.toJson(inventoryExceptionRequest);
    }

    LOGGER.info("VtrToInv URL={},  Request={}", url, requestBody);
    ResponseEntity<String> response =
        restUtils.post(url, httpHeaders, new HashMap<>(), requestBody);
    LOGGER.info("VtrToInv trackingId={},  Response={}", trackingId, response);

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      LOGGER.error(
          "VtrToInv post URL={},  Request&Headers={},  {},  Response={}",
          url,
          requestBody,
          httpHeaders,
          response);
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        cancelContainerResponse =
            new CancelContainerResponse(
                trackingId,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      } else {
        cancelContainerResponse =
            new CancelContainerResponse(
                trackingId,
                ReceivingException.INVENTORY_ERROR_CODE,
                ReceivingException.INVENTORY_ERROR_MSG);
      }
    }

    return cancelContainerResponse;
  }

  public CancelContainerResponse notifyVtrToDcFin(Container container, HttpHeaders httpHeaders) {
    final String txnId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final DcFinAdjustRequest vtrRequest =
        createDcFinAdjustRequest(container, txnId, VTR_REASON_CODE, null);

    final Map<String, Object> header = getForwardablHeaderWithTenantData(httpHeaders);
    dcFinRestApiClient.adjustOrVtr(vtrRequest, header);
    return null;
  }

  /**
   * @param cancelContainerRequest
   * @param httpHeaders
   * @return List<CancelContainerResponse>
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public List<CancelContainerResponse> cancelContainers(
      CancelContainerRequest cancelContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info(
        "Enter txCancelContainers() with trackingIds :{}", cancelContainerRequest.getTrackingIds());

    List<CancelContainerResponse> responseList = new ArrayList<>();
    for (String trackingId : cancelContainerRequest.getTrackingIds()) {
      CancelContainerResponse response = cancelContainer(trackingId, httpHeaders);

      if (response != null) {
        responseList.add(response);
      }
    }

    LOGGER.info("Exit txCancelContainers() with list of failure responses :{}", responseList);
    return responseList;
  }

  /**
   * @param swapContainerRequest
   * @return
   * @throws ReceivingException
   */
  @Override
  public List<CancelContainerResponse> swapContainers(
      List<SwapContainerRequest> swapContainerRequest, HttpHeaders httpHeaders) {
    LOGGER.warn("Cannot swap container. No implementation for this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  /**
   * @param trackingId
   * @param httpHeaders
   * @return CancelContainerResponse
   * @throws ReceivingException
   */
  private CancelContainerResponse cancelContainer(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    CancelContainerResponse response = null;

    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);

    if (container == null) {
      return new CancelContainerResponse(
          trackingId,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
    }

    response = getValidatePOConfirmationResponse(trackingId, container);
    if (response != null) {
      return response;
    }

    response = containerAdjustmentValidator.validateContainerForAdjustment(container);

    // if delivery finalised do not allow VTR
    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_FNL_CHECK_ENABLED)) {
      // Validate delivery status. Finalised delivery will not be allowed for receiving correction
      CancelContainerResponse validateDeliveryStatusResponse =
          containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
              container.getTrackingId(),
              container.getDeliveryNumber(),
              ReceivingUtils.getHeaders());
      if (Objects.nonNull(validateDeliveryStatusResponse)) {
        return new CancelContainerResponse(
            trackingId,
            validateDeliveryStatusResponse.getErrorCode(),
            validateDeliveryStatusResponse.getErrorMessage());
      }
    }

    // Skip notifying receiving adjustment to inventory if the container is not valid
    if (response != null) {
      return response;
    }
    final ContainerItem containerItem = container.getContainerItems().get(0);
    // Automation vs Manual Dc, One Atlas, GLS Flags start
    final boolean isManualGdc =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);

    boolean isOneAtlas =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    StringBuilder itemState = new StringBuilder("");
    final Long itemNumber = containerItem.getItemNumber();
    // One Atlas, GLS Flags end

    // FullGls(isManualGdc && !isOneAtlas)
    if ((isManualGdc && !isOneAtlas)
        || itemConfig.isOneAtlasNotConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
      // for fullGLS and OneAtlasAndNotConverted (6097 all items, 6085-nonConvertedItems)
      response = getContainerCancelResponseWithGLSInfo(container, httpHeaders);
      if (response != null) {
        return response;
      }
    }

    // automatedDc(!isManualGdc)
    if (!isManualGdc
        || itemConfig.isOneAtlasConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
      // Notify receiving adjustment to inventory. Inventory will post back to Receiving.
      // Then Receiving will 1.change backout status, 2.adjust recipts qty 3. sends delete to RTU
      // AutomatedDc 8852; OneAtlas With ConvertedItem (6085 with converted)
      response = notifyVtrToInv(trackingId, httpHeaders);
      if (response != null) {
        return response;
      }
    }

    if (itemConfig.isOneAtlasNotConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
      // oneAtlas and nonConvertedItem (eg 6085 nonConverted)
      response = notifyVtrToDcFin(container, httpHeaders);
      if (response != null) {
        return response;
      }
    }

    // Generate cancel move on VTR
    if (itemConfig.isOneAtlasConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
      movePublisher.publishCancelMove(trackingId, httpHeaders);
    }

    return null;
  }

  private CancelContainerResponse getValidatePOConfirmationResponse(
      String trackingId, Container container) throws ReceivingException {
    if (configUtils.isPoConfirmationFlagEnabled(getFacilityNum())) {
      for (ContainerItem containerItem : container.getContainerItems()) {
        Long deliveryNumber = container.getDeliveryNumber();
        String poReferenceNumber = containerItem.getPurchaseReferenceNumber();
        boolean poFinalized =
            receiptService.isPOFinalized(deliveryNumber.toString(), poReferenceNumber);
        if (poFinalized) {
          LOGGER.error(
              "For po = {}, valid business error = {}", poReferenceNumber, PO_ALREADY_FINALIZED);
          return new CancelContainerResponse(
              trackingId, CONFIRM_PO_ERROR_CODE, PO_ALREADY_FINALIZED);
        }
      }
    }
    return null;
  }

  private CancelContainerResponse getContainerCancelResponseWithGLSInfo(
      Container container, HttpHeaders httpHeaders) {
    final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);

    Optional<ContainerItem> getFirstContainerItem =
        container.getContainerItems().stream().findFirst();
    List<Receipt> receipts = null;
    GlsAdjustPayload glsAdjustPayload = new GlsAdjustPayload();

    try {
      if (getFirstContainerItem.isPresent()) {
        LOGGER.info("Set Quantity and Qty UOM from container item");
        ContainerItem containerItem = getFirstContainerItem.get();
        glsAdjustPayload =
            glsRestApiClient.createGlsAdjustPayload(
                ReceivingConstants.VTR,
                container.getTrackingId(),
                0,
                conversionToVendorPack(
                    containerItem.getQuantity(),
                    containerItem.getQuantityUOM(),
                    containerItem.getVnpkQty(),
                    containerItem.getWhpkQty()),
                userId);

        receipts =
            receiptHelper.getReceipts(
                userId, containerItem, -containerItem.getQuantity(), container.getDeliveryNumber());
      }
      glsRestApiClient.adjustOrCancel(glsAdjustPayload, httpHeaders);
      containerPersisterService.updateContainerStatusAndSaveReceipts(
          container.getTrackingId(), STATUS_BACKOUT, userId, receipts);
    } catch (ReceivingException ex) {
      LOGGER.error(
          "Failed to call GLS errorCode {}, errorMsg {}",
          ex.getErrorResponse().getErrorCode(),
          ex.getErrorResponse().getErrorMessage());

      String errorMsg =
          StringUtils.isNotBlank(String.valueOf(ex.getErrorResponse().getErrorMessage()))
              ? String.valueOf(ex.getErrorResponse().getErrorMessage())
              : ReceivingException.VTR_ERROR_MSG;
      return new CancelContainerResponse(
          container.getTrackingId(), ReceivingException.VTR_ERROR_CODE, errorMsg);
    } catch (Exception ex) {
      LOGGER.error(
          "{}Manual GDC VTR failed to back out container with error={}, stackTrace={}",
          SPLUNK_ALERT,
          ex.getMessage(),
          getStackTrace(ex));
      return new CancelContainerResponse(
          container.getTrackingId(),
          ReceivingException.VTR_ERROR_CODE,
          ReceivingException.VTR_ERROR_MSG);
    }
    return null;
  }
}
