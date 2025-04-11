package com.walmart.move.nim.receiving.witron.common;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DCFIN_API_DISABLED;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.*;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GDCFlagReader {

  @Autowired private TenantSpecificConfigReader configUtils;

  public boolean isManualGdcEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
  }

  public boolean isAutomatedDC() {
    return !isManualGdcEnabled();
  }

  public boolean isGdcLabelV2Enabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_GDC_LABEL_V2_ENABLED, false);
  }

  public boolean publishToWitronDisabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), PUBLISH_TO_WITRON_DISABLED, false);
  }

  public boolean publishToWFTDisabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), PUBLISH_TO_WFT_DISABLED, false);
  }

  public boolean isDCFinApiDisabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_DCFIN_API_DISABLED, false);
  }

  public boolean isGLSApiEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.IS_GLS_API_ENABLED, false);
  }

  public boolean isSmartSlottingApiDisabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_SMART_SLOTTING_DISABLED, false);
  }

  public boolean isLpnGenApiDisabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_LPN_GEN_DISABLED, false);
  }

  public boolean isItemConfigApiEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ITEM_CONFIG_SERVICE_ENABLED, false);
  }

  public boolean isDCOneAtlasEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
  }

  public boolean isIncludePalletCount() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_INCLUDE_PALLET_COUNT, true);
  }

  public boolean isKafkaReceiptsEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_KAFKA_RECEIPTS_ENABLED, false);
  }

  public boolean isMqReceiptsEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_MQ_RECEIPTS_ENABLED, true);
  }

  public boolean isDCFinHttpReceiptsEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_DC_FIN_HTTP_RECEIPTS_ENABLED, true);
  }

  public boolean isIgnoreAdjFromInventory() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IGNORE_ADJ_FROM_INVENTORY, false);
  }

  public boolean isKafkaReceiptsDcFinValidateEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_KAFKA_RECEIPTS_DC_FIN_VALIDATE_ENABLED, false);
  }

  public boolean isReceivingInstructsPutAwayMoveToMM() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM, true);
  }

  public boolean isReceivingProgressPubEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_RECEIVING_PROGRESS_PUB_ENABLED, false);
  }

  public String getVirtualPrimeSlotForIntoOss(String orgUnitId) {
    return configUtils.getCcmValue(
            getFacilityNum(), VIRTUAL_PRIME_SLOT_INTO_OSS, DEFAULT_VIRTUAL_PRIME_SLOT_INTO_OSS)
        + "_"
        + orgUnitId;
  }

  public boolean isOssVtrEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_OSS_VTR_ENABLED, false);
  }

  public boolean publishVtrToWFTDisabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), PUBLISH_VTR_TO_WFT_DISABLED, false);
  }
}
