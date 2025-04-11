package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class OperationalInfo implements Serializable {

  private String state;

  private String time;

  private String userId;
}
