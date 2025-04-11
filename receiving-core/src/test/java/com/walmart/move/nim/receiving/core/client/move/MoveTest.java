package com.walmart.move.nim.receiving.core.client.move;

import static com.walmart.move.nim.receiving.core.client.move.Move.CANCELLED;
import static com.walmart.move.nim.receiving.core.client.move.Move.COMPLETED;
import static com.walmart.move.nim.receiving.core.client.move.Move.OPEN;
import static com.walmart.move.nim.receiving.core.client.move.Move.PENDING;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MoveTest {

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {}

  @Test
  public void testMove_isMoveOpenOrPending() throws ReceivingException {
    assertFalse(Move.isMoveOpenOrPending(null));

    final Move pendingMove = new Move();
    pendingMove.setStatus(PENDING);
    Move.isMoveOpenOrPending(pendingMove);
    assertTrue(Move.isMoveOpenOrPending(pendingMove));

    final Move openMove = new Move();
    openMove.setStatus(OPEN);
    Move.isMoveOpenOrPending(openMove);
    assertTrue(Move.isMoveOpenOrPending(openMove));

    final Move completedMove = new Move();
    completedMove.setStatus(COMPLETED);
    Move.isMoveOpenOrPending(completedMove);
    assertFalse(Move.isMoveOpenOrPending(completedMove));
  }

  @Test
  public void testMove_isMoveCancelled() throws ReceivingException {
    assertFalse(Move.isMoveCancelled(null));

    final Move pendingMove = new Move();
    pendingMove.setStatus(PENDING);
    Move.isMoveOpenOrPending(pendingMove);
    assertFalse(Move.isMoveCancelled(pendingMove));

    final Move openMove = new Move();
    openMove.setStatus(OPEN);
    Move.isMoveOpenOrPending(openMove);
    assertFalse(Move.isMoveCancelled(openMove));

    final Move completedMove = new Move();
    completedMove.setStatus(COMPLETED);
    Move.isMoveOpenOrPending(completedMove);
    assertFalse(Move.isMoveCancelled(completedMove));

    final Move cancelledMove = new Move();
    cancelledMove.setStatus(CANCELLED);
    Move.isMoveOpenOrPending(cancelledMove);
    assertTrue(Move.isMoveCancelled(cancelledMove));
  }
}
