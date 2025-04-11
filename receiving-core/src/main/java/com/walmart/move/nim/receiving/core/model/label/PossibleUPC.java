package com.walmart.move.nim.receiving.core.model.label;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PossibleUPC {
  private String sscc;
  private String orderableGTIN;
  private String consumableGTIN;
  private String catalogGTIN;
  private String parsedUPC;

  @JsonIgnore
  public List<String> getPossibleUpcAsList() {
    List<String> possibleUpcList = new ArrayList<>();
    if (Objects.nonNull(sscc)) {
      possibleUpcList.add(sscc);
    }
    if (Objects.nonNull(catalogGTIN)) {
      possibleUpcList.add(catalogGTIN);
    }
    return possibleUpcList.stream().distinct().collect(Collectors.toList());
  }

  @JsonIgnore
  public Set<String> getOrderableGtinAndCatalogGtin() {
    return Stream.of(this.orderableGTIN, this.catalogGTIN)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }
}
