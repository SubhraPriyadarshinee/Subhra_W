package com.walmart.move.nim.receiving.rc.model.gad;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class QuestionsItem {
  private String questionId;
  private List<OptionsItem> options;
  private List<QuestionsItem> questions;
}
