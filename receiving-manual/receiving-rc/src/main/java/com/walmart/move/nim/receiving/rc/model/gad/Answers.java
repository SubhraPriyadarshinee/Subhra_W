package com.walmart.move.nim.receiving.rc.model.gad;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Answers {
  private String serviceType;
  private String legacySellerId;
  private String itemCategory;
  private List<QuestionsItem> questions;
}
