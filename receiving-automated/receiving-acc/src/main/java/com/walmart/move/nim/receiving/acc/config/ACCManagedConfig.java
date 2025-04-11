package com.walmart.move.nim.receiving.acc.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "accManagedConfig")
@Getter
public class ACCManagedConfig {

  @Property(propertyName = "lpn.exception.threshold")
  private float exceptionLPNThreshold;

  @Property(propertyName = "acl.base.url")
  private String aclBaseUrl;

  @Property(propertyName = "hawkeye.base.url")
  private String hawkEyeBaseUrl;

  @Property(propertyName = "pregen.fallback.enabled")
  private boolean pregenFallbackEnabled;

  @Property(propertyName = "fallback.label.generation.timeout")
  private int fallbackGenerationTimeout;

  @Property(propertyName = "acl.notification.ignoredList")
  private List<Integer> aclNotificationIgnoreCodeList;

  @Property(propertyName = "rcv.base.url")
  private String rcvBaseUrl;

  @Property(propertyName = "pregen.scheduler.retries.count")
  private int pregenSchedulerRetriesCount;

  @Property(propertyName = "pregen.scheduler.stale.check.timeout")
  private int pregenStaleCheckTimeout;

  @Property(propertyName = "delivery.sys.reopen.life.min")
  private int sysReopenedLifeInMin;

  @Property(propertyName = "acl.item.catalog.enabled")
  private boolean aclItemCatalogEnabled;

  @Property(propertyName = "multi.po.auto.select.enabled")
  private boolean multiPOAutoSelectEnabled;

  @Property(propertyName = "label.post.enabled")
  private boolean labelPostEnabled;

  @Property(propertyName = "label.delta.publish.enabled")
  private boolean labelDeltaPublishEnabled;

  @Property(propertyName = "facility.mdm.base.url")
  private String facilityMDMBaseUrl;

  @Property(propertyName = "facility.mdm.dc.alignment.path")
  private String facilityMDMDCAlignmentPath;

  @Property(propertyName = "facility.mdm.api.key")
  private String facilityMDMApiKey;

  @Property(propertyName = "facility.mdm.api.call.batch.size")
  private int facilityMDMApiCallBatchSize;

  @Property(propertyName = "facility.mdm.dc.alignment.sub.category")
  private String facilityMDMDCAlignmentSubCategory;

  @Property(propertyName = "acc.printable.zpl")
  private String accPrintableZPL;

  @Property(propertyName = "enable.fully.da.con")
  private boolean fullyDaConEnabled;
}
