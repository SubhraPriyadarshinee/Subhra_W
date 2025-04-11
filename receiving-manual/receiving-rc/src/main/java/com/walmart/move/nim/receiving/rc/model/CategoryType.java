package com.walmart.move.nim.receiving.rc.model;

public enum CategoryType {
  CATEGORY_A("1111", "A"),
  CATEGORY_B("1112", "B"),
  CATEGORY_C("1113", "C");

  private String optionId;
  private String category;

  CategoryType(String optionId, String category) {

    this.optionId = optionId;
    this.category = category;
  }

  public String getOptionId() {
    return optionId;
  }

  public void setOptionId(String optionId) {
    this.optionId = optionId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public static String getCategoryType(String optionId) {
    String categoryType = null;
    for (CategoryType type : CategoryType.values()) {
      if (type.getOptionId().equalsIgnoreCase(optionId)) {
        categoryType = type.getCategory();
        break;
      }
    }
    return categoryType;
  }
}
