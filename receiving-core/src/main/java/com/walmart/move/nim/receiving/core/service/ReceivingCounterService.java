package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.repositories.ReceivingCounterRepository;
import java.util.Optional;
import javax.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivingCounterService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivingCounterService.class);

  private static final String COUNTER_TYPE_TCL = "ENDGAME";
  private static final String COUNTER_TYPE_TPL = "ENDGAME_TPL";
  private static final String OTHERS = "OTHERS";
  private static final String ENDGAME_LABEL_PREFIX = "T";
  private static final String ENDGAME_PALLET_LABEL_PREFIX = "P";
  private static final char DEFAULT_TCL_CHAR_ASCII = 'D';
  private static final char TPL_CHAR_ASCII = 'P';
  private static final int MAX_TCL = 100000000;
  @Autowired private ReceivingCounterRepository receivingCounterRepository;

  @Retryable(
      value = {PersistenceException.class},
      maxAttemptsExpression = "${max.retry.count}",
      backoff = @Backoff(delayExpression = "${retry.delay}"))
  @Transactional
  @InjectTenantFilter
  public ReceivingCounter counterUpdation(long quantity, LabelType type) {
    ReceivingCounter receivingCounter = null;
    String counterType = "";
    if (type.equals(LabelType.TCL)) {
      counterType = COUNTER_TYPE_TCL;
    } else if (type.equals(LabelType.TPL)) {
      counterType = COUNTER_TYPE_TPL;
    } else {
      counterType = OTHERS;
    }
    Optional<ReceivingCounter> optionalTCLCounter =
        receivingCounterRepository.findByType(counterType);

    LOGGER.info("ReceivingCounter: going to calculate the range for [quantity={}]", quantity);
    if (optionalTCLCounter.isPresent()) {
      receivingCounter = optionalTCLCounter.get();
    } else {
      receivingCounter = new ReceivingCounter();
      receivingCounter.setType(counterType);
    }
    counterOperation(receivingCounter, quantity);
    LOGGER.info(
        "ReceivingCounter: calculated for [quantity={}] and [prefix={}]",
        quantity,
        receivingCounter.getPrefix());
    return receivingCounter;
  }

  private void counterOperation(ReceivingCounter receivingCounter, long quantity) {
    String _labelPrefix =
        receivingCounter.getType().equals(COUNTER_TYPE_TCL)
            ? ENDGAME_LABEL_PREFIX
            : ENDGAME_PALLET_LABEL_PREFIX;
    if (receivingCounter.getCounterNumber() + quantity > (MAX_TCL - 1)) {
      receivingCounter.setCounterNumber(0);
      char prefix = receivingCounter.getPrefixIndex();
      receivingCounter.setPrefixIndex(++prefix);
      counterOperation(receivingCounter, quantity);
    }

    char index = receivingCounter.getPrefixIndex();

    // skipping D for Default & P for TPL
    if (receivingCounter.getPrefixIndex() > 'Z') {
      receivingCounter.setPrefixIndex('A');
    }
    if (!(Character.isAlphabetic(index) && Character.isUpperCase(index))) {
      index = 'A';
    } else {
      if (DEFAULT_TCL_CHAR_ASCII == receivingCounter.getPrefixIndex()) {
        index = (char) (receivingCounter.getPrefixIndex() + 1);
      }
    }

    receivingCounter.setPrefixIndex(index);
    receivingCounter.setPrefix(_labelPrefix + receivingCounter.getPrefixIndex());
    receivingCounter.setCounterNumber(receivingCounter.getCounterNumber() + quantity);
    receivingCounterRepository.save(receivingCounter);
  }
}
