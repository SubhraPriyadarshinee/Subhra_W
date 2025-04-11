package com.walmart.move.nim.receiving.core.framework.expression;

/**
 * *
 *
 * <p>This is the default expression evaluator which will do String Ops.
 *
 * @author sitakant
 */
public class StandardExpressionEvaluator implements ReceivingExpressionEvaluator {

  public static final ReceivingExpressionEvaluator EVALUATOR = new StandardExpressionEvaluator();

  /**
   * String operation inorder to evaluate placeholder
   *
   * @param expression
   * @param placeHolder
   * @return
   */
  @Override
  public String evaluate(String expression, PlaceHolder placeHolder) {
    String expName =
        new StringBuilder(placeHolder.getPrefix())
            .append(placeHolder.getStandardPlaceholderName())
            .append(placeHolder.getSuffix())
            .toString();

    if (!expression.contains(expName)) {
      return expression;
    }
    return expression.replace(expName, placeHolder.getValue().toString());
  }
}
