package com.walmart.move.nim.receiving.sib.exception;

public interface ExceptionMessage {
  String INVALID_DEST_TRACKING_ID_MSG = "Dest tracking id not set for SSCC %s.";
  String INVALID_PICKUP_TS_MSG = "Correction Event invalid current timestamp %s.";
}
