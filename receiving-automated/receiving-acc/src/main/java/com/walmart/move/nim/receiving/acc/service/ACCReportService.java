package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.reporting.model.ReportDeliveryDetails;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class ACCReportService extends ReportService {

  @Autowired private LabelDataService labelDataService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public String getDeliveryDetailsForReport(long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryService deliveryService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    String deliveryDetails =
        deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
    ReportDeliveryDetails reportDeliveryDetails =
        JacksonParser.convertJsonToObject(deliveryDetails, ReportDeliveryDetails.class);
    List<LabelData> labelDataForDelivery =
        labelDataService.getLabelDataByDeliveryNumber(deliveryNumber);
    reportDeliveryDetails
        .getDeliveryDocuments()
        .forEach(
            reportDeliveryDocument ->
                reportDeliveryDocument
                    .getDeliveryDocumentLines()
                    .forEach(
                        reportDeliveryDocumentLine -> {
                          List<LabelData> labelDataByPoLine =
                              labelDataForDelivery
                                  .stream()
                                  .filter(
                                      labelData ->
                                          labelData
                                                  .getPurchaseReferenceNumber()
                                                  .equals(
                                                      reportDeliveryDocumentLine
                                                          .getPurchaseReferenceNumber())
                                              && labelData
                                                  .getPurchaseReferenceLineNumber()
                                                  .equals(
                                                      reportDeliveryDocumentLine
                                                          .getPurchaseReferenceLineNumber()))
                                  .collect(Collectors.toList());
                          labelDataByPoLine.forEach(
                              labelData -> {
                                switch (labelData.getLabelType()) {
                                  case ORDERED:
                                    reportDeliveryDocumentLine.setOrderedLabelCount(
                                        labelData.getLpnsCount());
                                    break;
                                  case OVERAGE:
                                    reportDeliveryDocumentLine.setOverageLabelCount(
                                        labelData.getLpnsCount());
                                    break;
                                  case EXCEPTION:
                                    reportDeliveryDocumentLine.setExceptionLabelCount(
                                        labelData.getLpnsCount());
                                    break;
                                  default:
                                    reportDeliveryDocumentLine.setOrderedLabelCount(null);
                                    reportDeliveryDocumentLine.setOverageLabelCount(null);
                                    reportDeliveryDocumentLine.setExceptionLabelCount(null);
                                    break;
                                }
                              });
                        }));
    return JacksonParser.writeValueAsString(reportDeliveryDetails);
  }
}
