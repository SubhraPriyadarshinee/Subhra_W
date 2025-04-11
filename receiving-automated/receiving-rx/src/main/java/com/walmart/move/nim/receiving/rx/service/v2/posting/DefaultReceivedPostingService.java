package com.walmart.move.nim.receiving.rx.service.v2.posting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class DefaultReceivedPostingService implements EpcisPostingService {

  @SneakyThrows
  @Override
  public void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {
    throw new ReceivingException("Unknown instruction receiving method");
  }
}
