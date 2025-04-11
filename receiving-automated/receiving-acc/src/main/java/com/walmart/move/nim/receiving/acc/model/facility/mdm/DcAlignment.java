package com.walmart.move.nim.receiving.acc.model.facility.mdm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcAlignment {
  private Integer number;
  private DcCategory category;

  @Getter
  @Setter
  @ToString
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DcCategory {
    private List<DcSubCategory> subCategory;

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DcSubCategory {
      private String code;
      private String description;
      private DateRange dateRange;
    }
  }
}
