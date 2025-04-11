package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import javax.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@MappedSuperclass
@FilterDef(
    name = "tenantFilter",
    parameters = {
      @ParamDef(name = "facilityNum", type = "integer"),
      @ParamDef(name = "facilityCountryCode", type = "string")
    })
@Filter(
    name = "tenantFilter",
    condition = "facilityNum = :facilityNum and facilityCountryCode = :facilityCountryCode")
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class BaseMTEntity {

  private String facilityCountryCode;

  private Integer facilityNum;

  public BaseMTEntity() {
    this.facilityCountryCode = TenantContext.getFacilityCountryCode();
    this.facilityNum = TenantContext.getFacilityNum();
  }
}
