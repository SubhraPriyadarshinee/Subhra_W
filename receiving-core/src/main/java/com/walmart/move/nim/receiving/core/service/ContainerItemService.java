package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.metrics.annotation.Timed;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContainerItemService {

  @Autowired private ContainerItemRepository containerItemRepository;

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<ContainerItem> findByTrackingId(String trackingId) {
    return containerItemRepository.findByTrackingId(trackingId);
  }

  @Transactional
  @InjectTenantFilter
  public void save(ContainerItem containerItem) {
    containerItemRepository.save(containerItem);
  }

  @Transactional
  @InjectTenantFilter
  public void saveAll(List<ContainerItem> containerItems) {
    containerItemRepository.saveAll(containerItems);
  }

  @Timed(
      name = "ContainerItemQueryTime",
      level1 = "uwms-receiving",
      level2 = "ContainerItemService",
      level3 = "getContainerItemMetaDataByUpcNumber")
  @Transactional(readOnly = true)
  public List<ContainerItem> getContainerItemMetaDataByUpcNumber(String upcNumber) {
    return containerItemRepository.getContainerItemMetaDataByUpcNumber(
        upcNumber, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
  }

  @Transactional(readOnly = true)
  public Optional<ContainerItem> getContainerItemByFacilityAndGtinAndSerial(Integer facilityNum, String facilityCountryCode, String gtin, String serial) {
    return containerItemRepository.findTopByFacilityNumAndFacilityCountryCodeAndGtinAndSerial(
            facilityNum, facilityCountryCode, gtin, serial);
  }

  @Transactional(readOnly = true)
  public Optional<ContainerItem> getContainerItemByGtinAndSerial(String gtin, String serial) {
    return containerItemRepository.findTopByGtinAndSerial(gtin, serial);
  }
}
