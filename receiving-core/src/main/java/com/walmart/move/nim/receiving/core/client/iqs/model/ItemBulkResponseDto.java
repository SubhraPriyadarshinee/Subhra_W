package com.walmart.move.nim.receiving.core.client.iqs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemBulkResponseDto {

  private String status;
  private List<ItemResponseDto> payload;
  private List<Error> errors;

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Data
  public static class Error {

    private String code;
    private String field;
    private String description;
    private String severity;
    private String category;
  }
}
