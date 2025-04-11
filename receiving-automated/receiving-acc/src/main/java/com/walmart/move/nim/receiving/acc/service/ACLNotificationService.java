package com.walmart.move.nim.receiving.acc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.constants.ACLError;
import com.walmart.move.nim.receiving.acc.constants.ACLErrorCode;
import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import com.walmart.move.nim.receiving.acc.model.NotificationSource;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSearchResponse;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSummary;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLSumoNotification;
import com.walmart.move.nim.receiving.acc.model.acl.notification.EquipmentStatus;
import com.walmart.move.nim.receiving.acc.repositories.ACLNotificationLogRepository;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ApplicationBaseException;
import com.walmart.move.nim.receiving.core.config.SumoConfig;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.sumo.SumoContent;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.core.service.Purge;
import com.walmart.move.nim.receiving.core.service.SumoService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class ACLNotificationService implements Purge {

  @Autowired SumoService sumoService;

  @ManagedConfiguration private SumoConfig sumoConfig;

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Autowired private ACLNotificationLogRepository aclNotificationLogRepository;

  @Autowired private Gson gson;

  @Autowired private UserLocationService userLocationService;

  @Autowired private LocationService locationService;

  private static final int ACL_LOG_PAGE_SIZE_LIMIT = 250;

  private static final Logger LOGGER = LoggerFactory.getLogger(ACLNotificationService.class);

  private static final String MAIL_NA_MSG = "--NA--";
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.DEFAULT_REPORT_SERVICE)
  private ReportService reportService;

  private static final String[] ACL_NOTIFICATION_REPORT_COLUMN_NAMES = {
    "Location Id",
    "Equipment Name",
    "Error Code",
    "Error Description",
    "Error Message",
    "Error Log Time"
  };

  /**
   * Find users at ACL notification and send them push alert via sumo service.
   *
   * @param notification
   * @param facilityNum
   * @param facilityCountryCode
   */
  @Timed(
      name = "aclNotificationTimed",
      level1 = "uwms-receiving",
      level2 = "aclNotificationService",
      level3 = "sendNotificationToSumo")
  @ExceptionCounted(
      name = "aclNotificationExceptionCount",
      level1 = "uwms-receiving",
      level2 = "aclNotificationService",
      level3 = "sendNotificationToSumo")
  @Async
  public void sendNotificationToSumo(
      ACLNotification notification, Integer facilityNum, String facilityCountryCode) {
    // set context as this is async thread
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setFacilityNum(facilityNum);
    if (notification
        .getEquipmentStatus()
        .stream()
        .anyMatch(equipmentStatus -> !StringUtils.isEmpty(equipmentStatus.getStatus()))) {
      /* This is being done, so that contract b/w client and Receiving backend remains same.*/
      convertToACLNotificationFormat(notification);
    }

    String locationId = notification.getLocationId();
    try {

      if (tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_MANIFEST)
          && tenantSpecificConfigReader.isFeatureFlagEnabled(
              ACCConstants.ENABLE_PARENT_ACL_LOCATION_CHECK)) {
        String parentLocation =
            locationService.getDoorInfo(locationId, Boolean.FALSE).getMappedParentAclLocation();
        if (!StringUtils.isEmpty(parentLocation)) {
          locationId = parentLocation;
          notification.setLocationId(locationId);
        }
      }

      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED)) {
        ACLNotification aclNotification = getFilteredList(notification);

        if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.IS_SUMO2_ENABLED)) {
          List<String> userIds = userLocationService.getUsersAtLocation(locationId, true);

          if (CollectionUtils.isEmpty(aclNotification.getEquipmentStatus())) {
            LOGGER.info("No equipment status for notification {} exists, returning", notification);
            return;
          }
          LOGGER.info("Preparing to send notification {} to sumo 2.0", notification);
          ResponseEntity<String> resp =
              sumoService.sendNotificationToSumo2(
                  new ACLSumoNotification(
                      sumoConfig.getContentAvailable() == 0
                          ? sumoConfig.getAclNotificationTitle()
                          : null,
                      sumoConfig.getContentAvailable() == 0
                          ? sumoConfig.getAclNotificationAlert()
                          : null,
                      aclNotification,
                      new SumoContent(sumoConfig.getContentAvailable())),
                  userIds);
          saveACLMessage(notification, resp.getBody());
        } else {
          List<String> userId = userLocationService.getUsersAtLocation(locationId, false);

          LOGGER.info("Preparing to send notification {} to sumo", notification);
          ResponseEntity<String> resp =
              sumoService.sendNotificationToSumo(
                  new ACLSumoNotification(
                      sumoConfig.getAclNotificationTitle(),
                      sumoConfig.getAclNotificationAlert(),
                      aclNotification),
                  userId);
          saveACLMessage(notification, resp.getBody());
        }
      }
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED)) {
        List<String> userId = userLocationService.getUsersAtLocation(locationId, false);

        LOGGER.info("Preparing to send notification {} using MQTT", notification);
        sumoService.sendNotificationUsingMqtt(
            new ACLSumoNotification(
                sumoConfig.getAclNotificationTitle(),
                sumoConfig.getAclNotificationAlert(),
                getFilteredList(notification)),
            userId);
        saveACLMessage(notification, null);
      }
    } catch (ApplicationBaseException e) {
      LOGGER.error("Error while sending notification {}", ExceptionUtils.getStackTrace(e));
      saveACLMessage(notification);
    } catch (Exception e) {
      LOGGER.error("Exception while sending notification {}", ExceptionUtils.getStackTrace(e));
      saveACLMessage(notification);
    } finally {
      TenantContext.clear();
    }
  }

  private void convertToACLNotificationFormat(ACLNotification notification) {
    // Don't process cleared messages
    notification
        .getEquipmentStatus()
        .removeIf(equipmentStatus -> Boolean.TRUE.equals(equipmentStatus.getCleared()));
    notification
        .getEquipmentStatus()
        .stream()
        .forEach(
            equipmentStatus -> {
              equipmentStatus.setValue(equipmentStatus.getStatus());
              equipmentStatus.setMessage(equipmentStatus.getDisplayMessage());
              equipmentStatus.setZone(
                  Objects.isNull(equipmentStatus.getZone()) ? -1 : equipmentStatus.getZone());
            });
  }

  private ACLNotification getFilteredList(ACLNotification notification) {
    // create a new object for sending filtered list to sumo as all message received by sumo should
    // be saved in DB
    ACLNotification aclNotification = new ACLNotification();
    aclNotification.setLocationId(notification.getLocationId());
    aclNotification.setEquipmentName(notification.getEquipmentName());
    aclNotification.setEquipmentType(notification.getEquipmentType());
    aclNotification.setEquipmentStatus(
        notification
            .getEquipmentStatus()
            .stream()
            .filter(
                equipmentStatus ->
                    !accManagedConfig
                        .getAclNotificationIgnoreCodeList()
                        .contains(equipmentStatus.getCode()))
            .collect(Collectors.toList()));
    return aclNotification;
  }

  @InjectTenantFilter
  public List<NotificationLog> saveAclNotification(List<NotificationLog> aclLogs) {
    return aclNotificationLogRepository.saveAll(aclLogs);
  }

  public List<NotificationLog> saveACLMessage(ACLNotification aclNotification) {
    return saveACLMessage(aclNotification, null);
  }

  public List<NotificationLog> saveACLMessage(ACLNotification aclNotification, String response) {
    List<NotificationLog> aclLogs = new ArrayList<>();

    List<EquipmentStatus> statusList = aclNotification.getEquipmentStatus();
    statusList.forEach(
        status -> {
          NotificationLog log = getAclNotificationLog(aclNotification, status);
          log.setSumoResponse(response);
          aclLogs.add(log);
        });
    return saveAclNotification(aclLogs);
  }

  private NotificationLog getAclNotificationLog(
      ACLNotification aclNotification, EquipmentStatus status) {
    return NotificationLog.builder()
        .type(NotificationSource.ACL)
        .locationId(org.apache.commons.lang3.StringUtils.upperCase(aclNotification.getLocationId()))
        .notificationMessage(convertLogForSave(aclNotification, status))
        .build();
  }

  private String convertLogForSave(ACLNotification aclNotification, EquipmentStatus status) {
    return gson.toJson(
        ACLNotificationSummary.builder()
            .locationId(
                org.apache.commons.lang3.StringUtils.upperCase(aclNotification.getLocationId()))
            .equipmentStatus(
                EquipmentStatus.builder()
                    .code(status.getCode())
                    .value(status.getValue())
                    .message(status.getDisplayMessage())
                    .zone(status.getZone())
                    .msgSequence(status.getMsgSequence())
                    .componentId(
                        !Objects.isNull(status.getComponentId())
                            ? status.getComponentId()
                            : Objects.isNull(status.getZone())
                                ? new Integer("-1").toString()
                                : status.getZone().toString())
                    .build())
            .equipmentType(aclNotification.getEquipmentType())
            .equipmentName(aclNotification.getEquipmentName())
            .updatedTs(
                !StringUtils.isEmpty(status.getStatusTimestamp())
                    ? ReceivingUtils.parseDate(status.getStatusTimestamp())
                    : ReceivingUtils.parseUtcDateTime(aclNotification.getUpdatedTs()))
            .build());
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public ACLNotificationSearchResponse getAclNotificationSearchResponse(
      String locationId, int pageIndex, int pageSize) {
    LOGGER.info(
        "Getting device feed for location: {} with pageIndex: {} and pageSize: {}",
        locationId,
        pageIndex,
        pageSize);

    // don't let anyone download the database in one page of results
    if (pageSize > ACL_LOG_PAGE_SIZE_LIMIT) {
      pageSize = ACL_LOG_PAGE_SIZE_LIMIT;
    }
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_MANIFEST)
        && tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_PARENT_ACL_LOCATION_CHECK)) {
      String parentLocation =
          locationService.getDoorInfo(locationId, Boolean.FALSE).getMappedParentAclLocation();
      if (!StringUtils.isEmpty(parentLocation)) {
        locationId = parentLocation;
      }
    }
    PageRequest pageRequest = PageRequest.of(pageIndex, pageSize, Sort.by("logTs").descending());
    locationId = org.apache.commons.lang3.StringUtils.upperCase(locationId);

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_MANIFEST)
        && tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_PARENT_ACL_LOCATION_CHECK)) {
      String parentLocation =
          locationService.getDoorInfo(locationId, Boolean.FALSE).getMappedParentAclLocation();
      if (!StringUtils.isEmpty(parentLocation)) {
        locationId = parentLocation;
      }
    }
    List<NotificationLog> aclLogs =
        aclNotificationLogRepository.findByLocationId(locationId, pageRequest);

    ACLNotificationSearchResponse response = new ACLNotificationSearchResponse();
    response.setAclLogs(convertToSummaryList(aclLogs));
    response.setPageNumber(pageRequest.getPageNumber());
    response.setPageSize(pageRequest.getPageSize());

    return response;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<NotificationLog> getAclNotificationLogsByLocation(
      String locationId, PageRequest pageRequest) {
    locationId = org.apache.commons.lang3.StringUtils.upperCase(locationId);
    return aclNotificationLogRepository.findByLocationId(locationId, pageRequest);
  }

  private List<ACLNotificationSummary> convertToSummaryList(List<NotificationLog> aclLogs) {
    List<ACLNotificationSummary> aclNotificationSummaryList = new ArrayList<>();
    aclLogs.forEach(aclLog -> aclNotificationSummaryList.add(convertToSummary(aclLog)));
    return aclNotificationSummaryList
        .stream()
        .filter(
            aclNotificationSummary ->
                !accManagedConfig
                    .getAclNotificationIgnoreCodeList()
                    .contains(aclNotificationSummary.getEquipmentStatus().getCode()))
        .collect(Collectors.toList());
  }

  private ACLNotificationSummary convertToSummary(NotificationLog aclLog) {
    ACLNotificationSummary aclNotificationSummary =
        gson.fromJson(aclLog.getNotificationMessage(), ACLNotificationSummary.class);

    aclNotificationSummary.setEquipmentStatus(
        getEquipmentDetails(aclNotificationSummary.getEquipmentStatus()));
    return aclNotificationSummary;
  }

  private EquipmentStatus getEquipmentDetails(EquipmentStatus equipmentStatus) {
    ACLError errorValue = ACLErrorCode.getErrorValue(equipmentStatus.getCode());

    if (!Objects.isNull(errorValue)) {
      equipmentStatus.setValue(errorValue.toString());
      equipmentStatus.setZone(errorValue.getZone());
      equipmentStatus.setMessage(errorValue.getMessage());
    }

    return equipmentStatus;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByLocation(String locationId) {
    locationId = org.apache.commons.lang3.StringUtils.upperCase(locationId);
    aclNotificationLogRepository.deleteByLocationId(locationId);
  }

  /*InjectTenantFilter not required, used for reporting*/
  @Transactional(readOnly = true)
  public List<NotificationLog> getAclLogsByDate(Date fromDate, Date toDate) {
    return aclNotificationLogRepository.findAllByLogTsBetween(fromDate, toDate);
  }

  /**
   * This method is used to create excel sheet report for ACL Notification logs
   *
   * @return
   */
  public Workbook createExcelReportForAclNotificationLogs(
      List<NotificationLog> aclNotificationLogs) {

    Workbook workbook = new XSSFWorkbook();

    for (Integer facilityNumber :
        tenantSpecificConfigReader.getEnabledFacilityNumListForFeature(
            ReceivingConstants.ACL_NOTIFICATION_ENABLED)) {

      List<NotificationLog> aclNotificationLogsByFacility =
          aclNotificationLogs
              .stream()
              .filter(o -> facilityNumber.equals(o.getFacilityNum()))
              .collect(Collectors.toList());

      if (!CollectionUtils.isEmpty(aclNotificationLogsByFacility)) {
        Sheet sheet = workbook.createSheet(facilityNumber + "-aclNotificationLogs");
        CellStyle headerCellStyle = reportService.getCellStyle(workbook);

        Row headerRow = sheet.createRow(0);

        String[] columnNames = ACL_NOTIFICATION_REPORT_COLUMN_NAMES;

        // Create header cells
        for (int i = 0; i < columnNames.length; i++) {
          Cell cell = headerRow.createCell(i);
          cell.setCellValue(columnNames[i]);
          cell.setCellStyle(headerCellStyle);
        }

        int rowNum = 1;
        for (NotificationLog aclNotificationLog : aclNotificationLogsByFacility) {
          Row row = sheet.createRow(rowNum++);
          ACLNotificationSummary aclNotificationSummary =
              gson.fromJson(
                  aclNotificationLog.getNotificationMessage(), ACLNotificationSummary.class);
          row.createCell(0).setCellValue(aclNotificationLog.getLocationId());
          row.createCell(1).setCellValue(aclNotificationSummary.getEquipmentName());
          row.createCell(2).setCellValue(aclNotificationSummary.getEquipmentStatus().getCode());
          row.createCell(3).setCellValue(aclNotificationSummary.getEquipmentStatus().getValue());
          row.createCell(4)
              .setCellValue(
                  getEquipmentMessage(aclNotificationSummary.getEquipmentStatus().getCode()));
          row.createCell(5).setCellValue(aclNotificationLog.getLogTs());
        }

        // Resize all columns to fit the content size
        for (int i = 0; i < columnNames.length; i++) {
          sheet.autoSizeColumn(i);
        }
      }
    }
    return workbook;
  }

  private String getEquipmentMessage(Integer code) {
    if (code != null && !Objects.isNull(ACLErrorCode.getErrorValue(code))) {
      return ACLErrorCode.getErrorValue(code).getMessage();
    }
    return MAIL_NA_MSG;
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<NotificationLog> notificationLogList =
        aclNotificationLogRepository.findByIdGreaterThanEqual(
            purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    notificationLogList =
        notificationLogList
            .stream()
            .filter(item -> item.getLogTs().before(deleteDate))
            .sorted(Comparator.comparing(NotificationLog::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(notificationLogList)) {
      LOGGER.info("Purge NOTIFICATION_LOG: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = notificationLogList.get(notificationLogList.size() - 1).getId();

    LOGGER.info(
        "Purge NOTIFICATION_LOG: {} records : ID {} to {} : START",
        notificationLogList.size(),
        notificationLogList.get(0).getId(),
        lastDeletedId);
    aclNotificationLogRepository.deleteAll(notificationLogList);
    LOGGER.info("Purge NOTIFICATION_LOG: END");
    return lastDeletedId;
  }
}
