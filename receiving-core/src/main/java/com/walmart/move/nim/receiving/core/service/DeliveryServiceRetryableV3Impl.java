package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DELIVERY_SERVICE_RETRYABLE_V3)
public class DeliveryServiceRetryableV3Impl extends DeliveryServiceImpl {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeliveryServiceRetryableV3Impl.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @ManagedConfiguration AppConfig appConfig;

  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, String.valueOf(deliveryNumber));
    pathParams.put(ReceivingConstants.UPC_NUMBER, upcNumber);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(ReceivingConstants.QUERY_GTIN, upcNumber);

    // TODO: need to assess if feature flag needed here,
    //  as only selected markets (currently will get to use this bean),
    //  which already adds a layer of market specific check
    queryParams.put(
        ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS,
        String.valueOf(
            tenantSpecificConfigReader.isFeatureFlagEnabled(
                    ReceivingConstants.IS_IQS_ITEM_SCAN_ACTIVE_CHANNELS_ENABLED)
                ? Boolean.TRUE
                : Boolean.FALSE));

    HttpHeaders forwardableHeaders = ReceivingUtils.getForwardableHttpHeaders(headers);
    forwardableHeaders.set(
        HttpHeaders.ACCEPT, ReceivingConstants.GDM_DOCUMENT_SEARCH_V3_ACCEPT_TYPE);

    String getDeliveryDocumentsUrl =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOCUMENT_SEARCH_URI_V3,
                pathParams,
                queryParams)
            .toString();

    return getDeliveryDocumentsByGtin(
        getDeliveryDocumentsUrl, forwardableHeaders, retryableRestConnector);
  }
}
