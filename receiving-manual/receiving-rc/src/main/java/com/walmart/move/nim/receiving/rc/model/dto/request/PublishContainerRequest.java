package com.walmart.move.nim.receiving.rc.model.dto.request;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
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
public class PublishContainerRequest {
  @Valid
  @NotEmpty(message = "rcContainerDetails cannot be null or empty")
  private List<PublishContainerItem> publishContainerItemList;
}
