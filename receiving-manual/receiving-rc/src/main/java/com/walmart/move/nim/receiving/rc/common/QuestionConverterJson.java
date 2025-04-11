package com.walmart.move.nim.receiving.rc.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.rc.model.gad.QuestionsItem;
import java.lang.reflect.Type;
import java.util.List;

public class QuestionConverterJson {
  private static Gson gson = new Gson();

  private QuestionConverterJson() {
    // Object creation not required
  }

  public static String convertToDatabaseColumn(List<QuestionsItem> questionsItems) {
    return gson.toJson(questionsItems);
  }

  public static List<QuestionsItem> convertToEntityAttribute(String questionsItems) {
    Type questionsItemsType =
        new TypeToken<List<QuestionsItem>>() {

          /** */
          private static final long serialVersionUID = 1L;
        }.getType();

    return gson.fromJson(questionsItems, questionsItemsType);
  }
}
