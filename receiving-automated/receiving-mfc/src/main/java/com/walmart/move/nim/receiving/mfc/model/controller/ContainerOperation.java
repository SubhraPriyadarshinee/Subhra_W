package com.walmart.move.nim.receiving.mfc.model.controller;

public enum ContainerOperation {
  CONTAINER_FOUND("CONTAINER_FOUND"),
  CONTAINER_PUBLISHED("CONTAINER_PUBLISHED");

  private String status;

  ContainerOperation(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }
}
