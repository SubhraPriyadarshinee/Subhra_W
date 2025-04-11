package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
import freemarker.template.Template;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class LabelDataProcessor {
  @Autowired ContainerService containerService;

  abstract String populateLabelData(Template jsonTemplate, ContainerMetaData containerMetaData);

  public Map<String, ContainerMetaData> getContainersMetaDataByTrackingIds(
      List<String> trackingIds) {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    List<ContainerMetaDataForPalletLabel> containerMetaDataForPalletLabels =
        containerService.getContainerAndContainerItemMetaDataForPalletLabelByTrackingIds(
            trackingIds);
    for (ContainerMetaDataForPalletLabel containerDetails : containerMetaDataForPalletLabels) {
      containerMetaDataMap.put(containerDetails.getTrackingId(), containerDetails);
    }
    return containerMetaDataMap;
  }
}
