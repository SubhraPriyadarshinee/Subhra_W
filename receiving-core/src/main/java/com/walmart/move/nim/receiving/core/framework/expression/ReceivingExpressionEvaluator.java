package com.walmart.move.nim.receiving.core.framework.expression;

/**
 * * This is the evaluator which will evaluate the expression. The implementation should either use
 * SpringExpression to evaluate or String replace etc.
 *
 * @author sitakant
 */
public interface ReceivingExpressionEvaluator {

  /**
   * * Expression and Placeholder should get pass and the implimentation should return the evaluated
   * value
   *
   * @see StandardExpressionEvaluator
   * @param expression
   * @param placeHolder
   * @return
   */
  String evaluate(String expression, PlaceHolder placeHolder);
}
