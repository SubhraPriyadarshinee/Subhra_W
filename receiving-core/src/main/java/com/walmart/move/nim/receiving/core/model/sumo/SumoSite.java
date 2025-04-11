package com.walmart.move.nim.receiving.core.model.sumo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SumoSite {
  String countryCode;
  String domain;
  Integer siteId;
  private List<String> userIds;
}
