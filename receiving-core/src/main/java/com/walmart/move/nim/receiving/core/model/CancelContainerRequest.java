package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CancelContainerRequest {

  @NotNull private List<String> trackingIds;
}
