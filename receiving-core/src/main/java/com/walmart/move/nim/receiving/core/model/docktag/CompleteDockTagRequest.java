package com.walmart.move.nim.receiving.core.model.docktag;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CompleteDockTagRequest {
  @NotNull Long deliveryNumber;
  @NotNull String deliveryStatus;
  @NotEmpty List<String> docktags;
}
