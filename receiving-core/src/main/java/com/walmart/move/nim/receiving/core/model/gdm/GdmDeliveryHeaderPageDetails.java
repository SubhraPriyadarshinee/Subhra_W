package com.walmart.move.nim.receiving.core.model.gdm;

import lombok.Data;

@Data
public class GdmDeliveryHeaderPageDetails {
  private int size;
  private int number;
  private int numberOfElements;
  private int totalElements;
  private int totalPages;
}
