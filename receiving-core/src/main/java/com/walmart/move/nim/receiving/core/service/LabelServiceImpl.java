package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import com.walmart.move.nim.receiving.core.model.LabelIdAndTrackingIdPair;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import com.walmart.move.nim.receiving.core.repositories.LabelMetaDataRepository;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import freemarker.template.Template;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(ReceivingConstants.CC_LABEL_SERVICE)
public class LabelServiceImpl implements LabelService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LabelServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private ContainerService containerService;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private Gson gson;

  @Autowired private LabelMetaDataRepository labelMetaDataRepository;

  @Autowired private ApplicationContext applicationContext;

  @Autowired private LabelPersisterService labelPersisterService;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  @Autowired private TenantSpecificConfigReader configUtils;

  @Override
  public List<PrintJobResponse> getLabels(
      Long deliveryNumber, String userId, boolean labelsByUser) {
    List<ReprintLabelData> reprintLabelData = null;
    List<PrintJobResponse> printJobResponseList = new ArrayList<>();
    Pageable setLmit = PageRequest.of(0, appConfig.getMaxAllowedReprintLabels());

    if (labelsByUser) {
      reprintLabelData =
          containerService.getLabelDataByDeliveryNumberByUserId(
              deliveryNumber, userId, ContainerException.DOCK_TAG.getText(), setLmit);
    } else {
      reprintLabelData =
          containerService.getLabelDataByDeliveryNumber(
              deliveryNumber, ContainerException.DOCK_TAG.getText(), setLmit);
    }

    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN)) {
      reprintLabelData = removePalletLabelForDaCon(reprintLabelData);
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
              printJobResponseList.add(printJobResponse);
            });

    LOGGER.info(
        "Returning print jobs with size:{} for the delivery:{}",
        printJobResponseList.size(),
        deliveryNumber);
    return printJobResponseList;
  }

  /**
   * @param requestedTrackingIds
   * @param httpHeaders
   * @return
   */
  @Override
  public ReprintLabelResponseBody getReprintLabelData(
      Set<String> requestedTrackingIds, HttpHeaders httpHeaders) {
    ReprintLabelResponseBody response = new ReprintLabelResponseBody();
    Map<Integer, Template> labelIdAndJsonTemplateMap = new HashMap<>();
    List<PrintLabelRequest> printRequestList = new ArrayList<>();

    // Fetch labelIDs for requested trackingIds
    List<LabelIdAndTrackingIdPair> listOfLabelIdAndTrackingIdPair =
        containerService.getLabelIdsByTrackingIdsWhereLabelIdNotNull(requestedTrackingIds);
    if (CollectionUtils.isEmpty(listOfLabelIdAndTrackingIdPair)) {
      return response;
    }

    Map<Integer, List<String>> groupTrackingIdsByLabelIdMap = new HashMap<>();

    // This map is for O(1) lookup to get labelId by provided trackingId
    Map<String, Integer> trackingIdToLabelIdMap = new HashMap<>();

    for (LabelIdAndTrackingIdPair labelIdAndTrackingIdPair : listOfLabelIdAndTrackingIdPair) {
      if (!groupTrackingIdsByLabelIdMap.containsKey(labelIdAndTrackingIdPair.getLabelId())) {
        groupTrackingIdsByLabelIdMap.put(labelIdAndTrackingIdPair.getLabelId(), new ArrayList<>());
      }
      groupTrackingIdsByLabelIdMap
          .get(labelIdAndTrackingIdPair.getLabelId())
          .add(labelIdAndTrackingIdPair.getTrackingId());
      trackingIdToLabelIdMap.put(
          labelIdAndTrackingIdPair.getTrackingId(), labelIdAndTrackingIdPair.getLabelId());
    }

    // get all Template based on tenant at once before processing
    List<LabelMetaData> labelMetaDataList =
        labelPersisterService.getLabelMetaDataByLabelIdsIn(groupTrackingIdsByLabelIdMap.keySet());
    for (LabelMetaData labelMetaData : labelMetaDataList) {
      String jsonTemplate = labelMetaData.getJsonTemplate();
      PrintLabelRequest labelPrintRequest = gson.fromJson(jsonTemplate, PrintLabelRequest.class);
      List<LabelData> labelDataList = labelPrintRequest.getData();
      labelPrintRequest.setData(labelDataList);
      jsonTemplate = gson.toJson(labelPrintRequest);
      try {
        labelIdAndJsonTemplateMap.put(
            labelMetaData.getLabelId(), ReprintUtils.getTemplate(jsonTemplate));
      } catch (IOException ex) {
        LOGGER.info("{} Error while creating template for labelId {}", ex, labelMetaData.getId());
      }
    }

    Map<String, ContainerMetaData> containerMetaDataByTrackingIds =
        getContainerMetaDataByTrackingIds(groupTrackingIdsByLabelIdMap);

    for (Map.Entry<String, ContainerMetaData> containerMetaData :
        containerMetaDataByTrackingIds.entrySet()) {

      String trackingId = containerMetaData.getKey();
      LOGGER.info("Started creating label data for trackingId: {}", trackingId);
      try {
        PrintLabelRequest labelReprintRequest;

        LabelDataProcessor labelDataProcessor =
            getLabelSpecificProcessor(
                LabelFormatId.getProcessorByLabelId(trackingIdToLabelIdMap.get(trackingId)));
        if (Objects.nonNull(labelDataProcessor)) {
          String payload =
              labelDataProcessor.populateLabelData(
                  labelIdAndJsonTemplateMap.get(trackingIdToLabelIdMap.get(trackingId)),
                  containerMetaData.getValue());
          if (!StringUtils.isEmpty(payload)) {
            labelReprintRequest = gson.fromJson(payload, PrintLabelRequest.class);
            printRequestList.add(labelReprintRequest);
            LOGGER.info("Successfully created label data for trackingId: {}", trackingId);
          }
        }
      } catch (Exception e) {
        LOGGER.info("Error in template string: {} for trackingId: {}", e, trackingId);
      }
    }

    response.setHeaders(ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders));
    response.setClientId("AllocPlanner");
    response.setPrintRequests(printRequestList);
    return response;
  }

  private Map<String, ContainerMetaData> getContainerMetaDataByTrackingIds(
      Map<Integer, List<String>> groupOfAllTrackingIdsByLabelId) {
    Map<String, ContainerMetaData> trackingIdToContainerMetaDataMap = new HashMap<>();
    for (Map.Entry<Integer, List<String>> entry : groupOfAllTrackingIdsByLabelId.entrySet()) {
      LabelDataProcessor labelDataProcessor =
          getLabelSpecificProcessor(LabelFormatId.getProcessorByLabelId(entry.getKey()));
      if (Objects.nonNull(labelDataProcessor)) {
        trackingIdToContainerMetaDataMap.putAll(
            labelDataProcessor.getContainersMetaDataByTrackingIds(entry.getValue()));
      }
    }
    return trackingIdToContainerMetaDataMap;
  }

  private LabelDataProcessor getLabelSpecificProcessor(String labelFormatId) {
    try {
      return (LabelDataProcessor) this.applicationContext.getBean(labelFormatId);
    } catch (BeansException beansException) {
      LOGGER.error(
          "ReprintLabelService::getBean::No bean specified with beanName={}", labelFormatId);
      return null;
    }
  }

  public List<LabelData> getLocaleSpecificValue(List<LabelData> labelDataList) {
    for (LabelData labelData : labelDataList) {
      try {
        String localeSpecificKey =
            resourceBundleMessageSource.getMessage(
                labelData.getKey(), null, LocaleContextHolder.getLocale());
        if (!StringUtils.isEmpty(localeSpecificKey)) labelData.setKey(localeSpecificKey);
      } catch (NoSuchMessageException noSuchMessageException) {
        LOGGER.error(
            "{} Error while getting locale specific print label key {}",
            noSuchMessageException,
            labelData.getKey());
      }
    }
    return labelDataList;
  }

  private List<ReprintLabelData> removePalletLabelForDaCon(
      List<ReprintLabelData> reprintLabelDataList) {
    Set<String> parentTrackingIds = new HashSet<>();
    for (ReprintLabelData data : reprintLabelDataList) {
      parentTrackingIds.add(data.getParentTrackingId());
    }
    List<ReprintLabelData> filteredList =
        reprintLabelDataList
            .stream()
            .filter(
                reprintLabelData -> !parentTrackingIds.contains(reprintLabelData.getTrackingId()))
            .collect(Collectors.toList());

    LOGGER.info("filteredList: {}", filteredList);
    return filteredList;
  }
}
