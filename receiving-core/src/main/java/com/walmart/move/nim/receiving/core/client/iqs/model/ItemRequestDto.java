package com.walmart.move.nim.receiving.core.client.iqs.model;

import java.util.Collection;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemRequestDto {

  private Collection<String> ids;
  private String facilityNumber;
}
