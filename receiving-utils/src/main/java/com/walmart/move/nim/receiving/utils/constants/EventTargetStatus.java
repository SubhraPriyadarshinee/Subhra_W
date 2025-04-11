/** */
package com.walmart.move.nim.receiving.utils.constants;

/**
 * This is the event target status type enum
 *
 * @author sitakant
 */
public enum EventTargetStatus {
  /** when one event try to publish and it failed to do so. */
  PENDING,

  /** when one event is on retry process */
  IN_RETRY,

  /** mark for the deletion after successful publish */
  DELETE,

  /** when processing of an event is in progress */
  IN_PROGRESS,

  /** when processing of an event has gotten suspended */
  STALE,
  /** when processing of an event failed * */
  FAILED,
  /** when processing of an event is successful */
  SUCCESSFUL,
  /**
   * Processing an event for a delivery is not successful or when another event is in
   * EVENT_IN_PROGRESS for the same delivery
   */
  EVENT_PENDING,
  /** Processing an event for a delivery is in progress */
  EVENT_IN_PROGRESS
}
