package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@NoArgsConstructor
public class ActivityWithIdRequest {

  private String activityName;

  @NotEmpty private List<Long> ids;
}
