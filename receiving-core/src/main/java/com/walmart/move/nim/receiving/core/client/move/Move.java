package com.walmart.move.nim.receiving.core.client.move;

import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Move {
  public static final String HAUL = "haul";
  public static final String PUTAWAY = "putaway";
  public static final String OPEN = "open";
  public static final String PENDING = "PENDING";
  public static final String WORKING = "WORKING";
  public static final String ONHOLD = "ONHOLD";
  public static final String COMPLETED = "COMPLETED";
  public static final String CANCELLED = "CANCELLED";

  @Expose private int id;
  @Expose private String type;
  @Expose private String status;
  @Expose private String containerId;
  @Expose private String fromLocation;
  @Expose private String toLocation;
  @Expose private int priority;

  public static boolean isMoveOpenOrPending(Move move) {
    return move != null
        && (OPEN.equalsIgnoreCase(move.getStatus()) || PENDING.equalsIgnoreCase(move.getStatus()));
  }

  public static boolean isMoveCancelled(Move move) {
    return move != null && CANCELLED.equalsIgnoreCase(move.getStatus());
  }

  public static boolean isMoveNullOpenPendingOnHoldCancelled(Move move) {
    return move == null
        || OPEN.equalsIgnoreCase(move.getStatus())
        || PENDING.equalsIgnoreCase(move.getStatus())
        || ONHOLD.equalsIgnoreCase(move.getStatus())
        || CANCELLED.equalsIgnoreCase(move.getStatus());
  }
}
