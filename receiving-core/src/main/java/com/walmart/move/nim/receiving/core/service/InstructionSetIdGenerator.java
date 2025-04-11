package com.walmart.move.nim.receiving.core.service;

/** @author v0k00fe */
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InstructionSetIdGenerator {

  @Autowired private InstructionRepository instructionRepository;

  public Long generateInstructionSetId() {
    return instructionRepository.getNextInstructionSetId();
  }
}
