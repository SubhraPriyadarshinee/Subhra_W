package com.walmart.move.nim.receiving.rx.service.v2.posting;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.repositories.ProcessingInfoRepository;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
import io.strati.configuration.annotation.ManagedConfiguration;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProblemCaseReceivedPostingService extends EpcisService implements EpcisPostingService {

  @ManagedConfiguration AppConfig appConfig;
  @ManagedConfiguration RxManagedConfig rxManagedConfig;
  @Resource private Gson gson;
  @Resource private ProcessingInfoRepository processingInfoRepository;
  @Resource private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;

  @Override
  public void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {}
}
