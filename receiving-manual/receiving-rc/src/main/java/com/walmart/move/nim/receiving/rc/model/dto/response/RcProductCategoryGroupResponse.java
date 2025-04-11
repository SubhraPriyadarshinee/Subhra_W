package com.walmart.move.nim.receiving.rc.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Builder
@ToString
@EqualsAndHashCode
public class RcProductCategoryGroupResponse {
  private Long id;
  private String l0;
  private String l1;
  private String l2;
  private String l3;
  private String productType;
  private String group;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date createTs;

  private String createUser;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date lastChangedTs;

  private String lastChangedUser;
}
