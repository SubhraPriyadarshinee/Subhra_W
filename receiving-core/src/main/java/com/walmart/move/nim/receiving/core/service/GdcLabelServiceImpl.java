package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.GdcReprintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.GDC_LABEL_SERVICE)
public class GdcLabelServiceImpl implements LabelService {
  private static final Logger logger = LoggerFactory.getLogger(RdcLabelServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private ContainerService containerService;

  @Override
  public List<PrintJobResponse> getLabels(
      Long deliveryNumber, String userId, boolean labelsByUser) {
    List<GdcReprintLabelData> reprintLabelData = null;
    List<PrintJobResponse> printJobResponseList = new ArrayList<>();
    Pageable setLmit = PageRequest.of(0, appConfig.getFetchLabelsLimit());

    if (labelsByUser) {
      reprintLabelData =
          containerService.getGdcDataForPrintingLabelByDeliveryNumberByUserId(
              deliveryNumber,
              userId,
              ReceivingConstants.containerExceptions,
              TenantContext.getFacilityNum(),
              TenantContext.getFacilityCountryCode(),
              setLmit);
    } else {
      reprintLabelData =
          containerService.getGdcDataForPrintingLabelByDeliveryNumber(
              deliveryNumber,
              ReceivingConstants.containerExceptions,
              TenantContext.getFacilityNum(),
              TenantContext.getFacilityCountryCode(),
              setLmit);
    }

    reprintLabelData
        .stream()
        .forEach(
            labelData -> {
              PrintJobResponse printJobResponse = new PrintJobResponse();
              printJobResponse.setItemDescription(
                  ReprintUtils.getDescription(
                      labelData.getDescription(), labelData.getContainerException()));
              printJobResponse.setLabelIdentifier(labelData.getTrackingId());
              printJobResponse.setUserId(labelData.getCreateUser());
              printJobResponse.setCreateTS(labelData.getCreateTs());
              Integer vendorPackQty =
                  ReceivingUtils.conversionToVendorPack(
                      labelData.getQuantity(),
                      ReceivingConstants.Uom.EACHES,
                      labelData.getVnpkQty(),
                      labelData.getWhpkQty());
              printJobResponse.setPalletQty(vendorPackQty);
              printJobResponse.setPalletQtyUOM(ReceivingConstants.Uom.VNPK);
              printJobResponseList.add(printJobResponse);
            });

    logger.info(
        "GdcLabelService::getLabels::Returning print jobs with size:{} for the delivery:{}",
        printJobResponseList.size(),
        deliveryNumber);
    return printJobResponseList;
  }

  @Override
  public ReprintLabelResponseBody getReprintLabelData(
      Set<String> requestedTrackingIds, HttpHeaders httpHeaders) {
    logger.warn("No implementation for reprint in this tenant {}", TenantContext.getFacilityNum());
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }
}
