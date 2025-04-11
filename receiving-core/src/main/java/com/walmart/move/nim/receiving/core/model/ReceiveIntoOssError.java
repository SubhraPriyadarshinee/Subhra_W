package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceiveIntoOssError {
  private HashMap<String, Po> errPos;
}
