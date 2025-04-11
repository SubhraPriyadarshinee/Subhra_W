package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "reportConfig")
@Getter
public class ReportConfig {
  @Property(propertyName = "specifications")
  private String specifications;

  @Property(propertyName = "report.from.email.address")
  private String reportFromAddress;

  @Property(propertyName = "report.from.pharmacy.email.address")
  private String pharmacyReportFromAddress;

  @Property(propertyName = "stats.report.to.email.addresses")
  private List<String> statsReportToAddresses;

  @Property(propertyName = "stats.report.generation.for.last.x.days")
  private int statsReportGenerationForLastXdays;

  @Property(propertyName = "item.catalog.report.to.email.addresses")
  private List<String> itemCatalogReportToAddresses;

  @Property(propertyName = "item.catalog.report.to.pharmacy.email.addresses")
  private List<String> pharmacyItemCatalogReportToAddresses;

  @Property(propertyName = "item.catalog.report.pharmacy.generation.for.last.x.days")
  private int itemCatalogReportPharmacyGenerationForLastXdays;

  @Property(propertyName = "item.catalog.report.generation.for.last.x.days")
  private int itemCatalogReportGenerationForLastXdays;

  @Property(propertyName = "item.catalog.report.generation.exclude.dc.list")
  private List<Integer> itemCatalogReportExcludeDcList;

  @Property(propertyName = "acl.notification.report.generation.for.last.x.days")
  private int aclNotificationReportGenerationForLastXdays;

  @Property(propertyName = "acl.notification.report.to.email.addresses")
  private List<String> aclNotificationReportToAddress;

  @Property(propertyName = "pending.instruction.alert.to.email.addresses")
  private List<String> partialPendingAlertToAddresses;

  @Property(propertyName = "tenant.config")
  private String tenantConfig;

  @Property(propertyName = "custom.time.range.enabled")
  private boolean isCustomTimeRangeEnabled;

  @Property(propertyName = "enable.email.report")
  private boolean isEmailReportEnabled;

  @Property(propertyName = "enable.dashboard.report")
  private boolean isDashboardReportEnabled;

  @Property(propertyName = "los.goal.hrs")
  private int losGoalInHours;

  @Property(propertyName = "item.catalog.report.pharmacy.generation.include.dc.list")
  private List<Integer> itemCatalogReportPharmacyIncludeDcList;

  @Property(propertyName = "metrics.report.pharmacy.generation.include.dc.list")
  private List<Integer> metricsReportPharmacyIncludeDcList;

  @Property(propertyName = "metrics.report.pharmacy.generation.for.last.x.days")
  private int metricsReportGenerationForLastXdays;

  @Property(propertyName = "metrics.report.to.pharmacy.email.addresses")
  private List<String> metricsReportToAddresses;

  @Property(propertyName = "metrics.enable.pharmacy.email.report")
  private boolean isPharmacyEmailReportEnabled;

  @Property(propertyName = "tenantOpsEmailRecipients")
  private String tenantOpsEmailRecipients;

  @Property(propertyName = "atlas.da.break.pack.backout.report.from.email.address")
  private String atlasDaBreakPackBackOutReportFromEmailAddress;

  @Property(propertyName = "atlas.da.break.pack.backout.report.to.email.address")
  private List<String> atlasDaBreakPackBackOutReportToEmailAddress;

  @Property(propertyName = "atlas.da.break.pack.backout.report.facility.id")
  private List<Integer> atlasDaBreakPackBackOutReportFacilityIds;

  @Property(propertyName = "atlas.da.break.pack.backout.report.records.fetch.count")
  private Integer atlasDaBreakPackBackOutReportRecordsFetchCount;
}
