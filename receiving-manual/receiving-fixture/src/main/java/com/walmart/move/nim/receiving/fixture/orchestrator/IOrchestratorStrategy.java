package com.walmart.move.nim.receiving.fixture.orchestrator;

public interface IOrchestratorStrategy<T> {
  void orchestrateEvent(T t);
}
