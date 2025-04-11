package com.walmart.move.nim.receiving.core.model.docktag;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteDockTagResponse {
  List<String> success;
  List<String> failed;
  Long deliveryNumer;
}
