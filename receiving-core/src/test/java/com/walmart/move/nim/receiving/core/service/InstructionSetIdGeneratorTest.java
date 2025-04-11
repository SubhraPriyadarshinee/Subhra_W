package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.*;
import org.testng.annotations.BeforeClass;

public class InstructionSetIdGeneratorTest {

  @Mock private InstructionRepository instructionRepository;
  @InjectMocks private InstructionSetIdGenerator InstructionSetIdGenerator;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test_generateInstructionSetId() {

    doReturn(1l).when(instructionRepository).getNextInstructionSetId();

    assertTrue(InstructionSetIdGenerator.generateInstructionSetId() == 1);
  }
}
