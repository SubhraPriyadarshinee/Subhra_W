package com.walmart.move.nim.receiving.endgame.model;

import java.time.LocalDateTime;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PoReceiptRequest {
  @NotBlank(message = "FromDateTime can not be blank")
  private LocalDateTime fromDateTime;

  @NotBlank(message = "ToDateTime can not be blank")
  private LocalDateTime toDateTime;
}
