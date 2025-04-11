package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.repositories.DockTagRepository;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DockTagPersisterService {

  @Autowired private DockTagRepository dockTagRepository;

  @Transactional
  @InjectTenantFilter
  public void saveDockTag(DockTag dockTag) {
    dockTagRepository.save(dockTag);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> saveAllDockTags(List<DockTag> dockTags) {
    return dockTagRepository.saveAll(dockTags);
  }

  @Transactional
  @InjectTenantFilter
  public Integer getCountOfDockTagsByDeliveryAndStatuses(
      Long deliveryNumber, List<InstructionStatus> dockTagStatuses) {
    return dockTagRepository.countByDeliveryNumberAndDockTagStatusIn(
        deliveryNumber, dockTagStatuses);
  }

  @Transactional
  @InjectTenantFilter
  public int getCountOfDockTagsByStatuses(List<InstructionStatus> dockTagStatuses) {
    return dockTagRepository.countByDockTagStatusIn(dockTagStatuses);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public int getCountOfDockTagsByDeliveryNumber(Long deliveryNumber) {
    return dockTagRepository.countByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public DockTag getDockTagByDockTagId(String dockTagId) {
    return dockTagRepository.findByDockTagId(dockTagId);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getDockTagsByDockTagIds(List<String> dockTagIds) {
    return dockTagRepository.findByDockTagIdIn(dockTagIds);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getDockTagsByStatuses(List<InstructionStatus> dockTagStatuses) {
    return dockTagRepository.findByDockTagStatusIn(dockTagStatuses);
  }

  @Transactional
  public List<DockTag> getDockTagsByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable) {
    return dockTagRepository.findByIdGreaterThanEqual(lastDeleteId, pageable);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getDockTagsByDockTagStatusesAndCreateTsLessThan(
      List<InstructionStatus> dockTagStatus, Date currentTime, Pageable pageable) {
    return dockTagRepository.findByDockTagStatusInAndCreateTsLessThanOrderByCreateTs(
        dockTagStatus, currentTime, pageable);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getDockTagsByDeliveries(List<Long> deliveryNumberList) {
    return dockTagRepository.findByDeliveryNumberIn(deliveryNumberList);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getDockTagsByDeliveriesAndStatuses(
      List<Long> deliveryNumberList, List<InstructionStatus> dockTagStatuses) {
    return dockTagRepository.findByDeliveryNumberInAndDockTagStatusIn(
        deliveryNumberList, dockTagStatuses);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteDockTagsForDelivery(Long deliveryNumber) {
    dockTagRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getDockTagsByDelivery(Long deliveryNumber) {
    return dockTagRepository.findByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<DockTag> getAttachedDockTagsByScannedLocation(String scannedLocation) {
    return dockTagRepository.findByScannedLocationAndDockTagStatus(
        scannedLocation, InstructionStatus.UPDATED);
  }

  @Transactional
  public void deleteAllDockTags(List<DockTag> dockTags) {
    dockTagRepository.deleteAll(dockTags);
  }
}
