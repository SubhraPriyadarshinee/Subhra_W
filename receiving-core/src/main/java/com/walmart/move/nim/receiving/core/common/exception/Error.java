package com.walmart.move.nim.receiving.core.common.exception;

import java.util.List;
import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Error {
  private String errorCode;
  private List<String> description;
}
