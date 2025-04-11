package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.contants.ActionType;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerDetails;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublishContainerItem {
  @NotNull(message = "actionType cannot be null")
  private ActionType actionType;

  @NotNull(message = "ignoreSct cannot be null")
  private Boolean ignoreSct;

  @NotNull(message = "ignoreRap cannot be null")
  private String ignoreRap;

  @NotNull(message = "ignoreWfs cannot be null")
  private String ignoreWfs;

  @Valid
  @NotNull(message = "rcContainerDetails cannot be null")
  private RcContainerDetails rcContainerDetails;
}
