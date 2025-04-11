package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import com.walmart.move.nim.receiving.core.repositories.LabelMetaDataRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_LABEL_SERVICE)
public class RdcLabelServiceImpl implements LabelService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcLabelServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private ContainerService containerService;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private Gson gson;

  @Autowired private LabelMetaDataRepository labelMetaDataRepository;

  @Autowired private ApplicationContext applicationContext;

  @Override
  public List<PrintJobResponse> getLabels(
      Long deliveryNumber, String userId, boolean labelsByUser) {
    List<ReprintLabelData> reprintLabelData = null;
    List<PrintJobResponse> printJobResponseList = new ArrayList<>();
    Pageable setLmit = PageRequest.of(0, appConfig.getFetchLabelsLimit());

    if (labelsByUser) {
      reprintLabelData =
          containerService.getDataForPrintingLabelByDeliveryNumberByUserId(
              deliveryNumber,
              userId,
              ReceivingConstants.containerExceptions,
              TenantContext.getFacilityNum(),
              TenantContext.getFacilityCountryCode(),
              setLmit);
    } else {
      reprintLabelData =
          containerService.getDataForPrintingLabelByDeliveryNumber(
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
              if (Objects.nonNull(labelData.getSsccNumber())) {
                printJobResponse.setSsccNumber(labelData.getSsccNumber());
              }
              printJobResponseList.add(printJobResponse);
            });

    LOGGER.info(
        "Returning print jobs with size:{} for the delivery:{}",
        printJobResponseList.size(),
        deliveryNumber);
    return printJobResponseList;
  }

  @Override
  public ReprintLabelResponseBody getReprintLabelData(
      Set<String> requestedTrackingIds, HttpHeaders httpHeaders) {
    LOGGER.warn("No implementation for reprint in this tenant {}", TenantContext.getFacilityNum());
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }
}
