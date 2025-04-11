package com.walmart.move.nim.receiving.core.model.sorter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.utils.constants.NoSwapReason;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProgramSorterTO {
  private String uid;
  private SorterExceptionReason exceptionReason;
  private String cartonTag;
  private String labelType;
  private String storeNbr;
  private String countryCode;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private Date labelDate;

  private Integer divisionNbr;
  private Integer quantity;
  private List<Pick> innerPicks;

  // fields added as a part of ACC PA outbound
  private String groupNumber;
  private Integer itemNumber;
  private String poNumber;
  private String poType;
  private NoSwapReason noSwapReason;
  // Added as part of sorter contract verion 2
  private String originDcNbr;
  private String shipUnitNbr;
  private SorterMessageAttribute attributes;
}
