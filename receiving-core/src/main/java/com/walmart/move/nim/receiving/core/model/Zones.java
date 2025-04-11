package com.walmart.move.nim.receiving.core.model;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Data
public class Zones {

  private String id;
  private Temperature temperature = new Temperature();
  private List<String> purchaseOrders = new ArrayList<>();
}
