package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import org.springframework.http.HttpHeaders;

public interface EpcisPostingService {
  void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders);
}
