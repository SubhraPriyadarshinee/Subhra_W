package com.walmart.move.nim.receiving.rc.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.service.Purge;
import com.walmart.move.nim.receiving.rc.contants.PackageTrackerCode;
import com.walmart.move.nim.receiving.rc.entity.PackageRLog;
import com.walmart.move.nim.receiving.rc.model.dto.request.PackageTrackerRequest;
import com.walmart.move.nim.receiving.rc.repositories.PackageRLogRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

public class RcPackageTrackerService implements Purge {
  private static final Logger LOGGER = LoggerFactory.getLogger(RcPackageTrackerService.class);
  @Autowired private PackageRLogRepository packageRLogRepository;

  @Transactional
  @InjectTenantFilter
  public void trackPackageStatus(PackageTrackerRequest packageTrackerRequest) {
    if (!EnumUtils.isValidEnum(PackageTrackerCode.class, packageTrackerRequest.getReasonCode())) {
      LOGGER.error(
          "Package cannot be tracked without reason code [packageTrackerRequest={}]",
          packageTrackerRequest);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PACKAGE_TRACKER_REQUEST,
          ExceptionDescriptionConstants.INVALID_PACKAGE_TRACKER_REQUEST_REASON_CODE);
    }
    PackageRLog packageRLog = new PackageRLog();
    packageRLog.setPackageBarCodeValue(packageTrackerRequest.getScannedLabel());
    packageRLog.setPackageBarCodeType(packageTrackerRequest.getScannedLabelType());
    packageRLog.setPackageCost(packageTrackerRequest.getPackageCost());
    packageRLog.setIsHighValue(packageTrackerRequest.getIsHighValue());
    // TODO: This will be removed in next commit when UI will be deployed to have
    // isSerialScanRequired mandatory till then we are having this check
    packageRLog.setIsSerialScanRequired(
        Objects.nonNull(packageTrackerRequest.getIsSerialScanRequired())
            ? packageTrackerRequest.getIsSerialScanRequired()
            : Boolean.FALSE);
    packageRLog.setPackageTrackerCode(
        PackageTrackerCode.valueOf(packageTrackerRequest.getReasonCode()));
    String userId =
        String.valueOf(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    packageRLog.setCreateUser(userId);
    packageRLogRepository.save(packageRLog);
  }

  @Transactional
  @InjectTenantFilter
  public List<PackageRLog> getTrackedPackage(String packageBarCodeValue) {
    List<PackageRLog> packageRLogList =
        packageRLogRepository.findByPackageBarCodeValue(packageBarCodeValue);
    if (CollectionUtils.isEmpty(packageRLogList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.PACKAGE_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              packageBarCodeValue);
      throw new ReceivingDataNotFoundException(ExceptionCodes.PACKAGE_NOT_FOUND, errorDescription);
    }
    return packageRLogList;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteTrackedPackage(String packageBarCodeValue) {
    List<PackageRLog> packageRLogList =
        packageRLogRepository.findByPackageBarCodeValue(packageBarCodeValue);
    if (CollectionUtils.isEmpty(packageRLogList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.PACKAGE_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              packageBarCodeValue);
      throw new ReceivingDataNotFoundException(ExceptionCodes.PACKAGE_NOT_FOUND, errorDescription);
    }
    packageRLogRepository.deleteByPackageBarCodeValue(packageBarCodeValue);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<PackageRLog> packageRLogList =
        packageRLogRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    packageRLogList =
        packageRLogList
            .stream()
            .filter(packageRLog -> packageRLog.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(PackageRLog::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(packageRLogList)) {
      LOGGER.info("Purge PACKAGE_RLOG: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = packageRLogList.get(packageRLogList.size() - 1).getId();

    LOGGER.info(
        "Purge PACKAGE_RLOG: {} records : ID {} to {} : START",
        packageRLogList.size(),
        packageRLogList.get(0).getId(),
        lastDeletedId);
    packageRLogRepository.deleteAll(packageRLogList);
    LOGGER.info("Purge PACKAGE_RLOG: END");
    return lastDeletedId;
  }
}
