package com.walmart.move.nim.receiving.sib.model.inventory;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class InventorySearchResponse {
  private Integer totalRecords;
  private NextPage nextPage;
  private Integer totalPages;
  private List<AggrInvListItem> aggrInvList;

  private Map<String, Object> additionalProperties = new HashMap<>();

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(String key, Object value) {
    additionalProperties.put(key, value);
  }
}
