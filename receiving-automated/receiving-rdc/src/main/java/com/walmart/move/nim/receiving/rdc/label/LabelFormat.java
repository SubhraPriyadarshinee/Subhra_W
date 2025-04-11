package com.walmart.move.nim.receiving.rdc.label;

import lombok.Getter;

@Getter
public enum LabelFormat {
  DOCK_TAG("dc1000f0_format_k"),
  NEW_DOCKTAG("rdc_docktag_label_format"),
  LEGACY_SSTK("dc1000f0_format_g"),
  ATLAS_RDC_SSTK("atlas_rdc_sstk_label_format"),
  ATLAS_RDC_PALLET("atlas_rdc_pallet_label_format"),
  SSTK_TIMESTAMP("dc1000f0_format_n"),
  DA_STORE_FRIENDLY("dc1000f0_format_a"),
  ATLAS_DA_STORE_FRIENDLY("atlas_rdc_da_store_label_format"),
  DA_CONVEYABLE_INDUCT_PUT("dc1000f0_format_f"),
  ATLAS_DA_CONVEYABLE_PUT("atlas_rdc_da_put_label_format"),
  DA_NON_CONVEYABLE_VOICE_PUT("dc1000f0_format_e"),
  DSDC("dc1000f0_format_d"),
  DOTCOM("dc1000f0_format_p"),
  DSDC_AUDIT("dc1000f0_format_l"),
  ATLAS_DSDC_AUDIT("atlas_rdc_dsdc_audit_label_format"),
  ATLAS_DSDC("atlas_rdc_dsdc_label_format");

  private String format;

  private LabelFormat(String format) {
    this.format = format;
  }
}
