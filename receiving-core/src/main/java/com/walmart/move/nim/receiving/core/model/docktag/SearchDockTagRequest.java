package com.walmart.move.nim.receiving.core.model.docktag;

import java.util.List;
import javax.validation.constraints.NotEmpty;
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
public class SearchDockTagRequest {

  @NotEmpty(message = "deliveryNumbers cannot be empty")
  List<String> deliveryNumbers;
}
