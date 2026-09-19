package chapter13;

/**
 * 主成分の向きに対する 1 つの列の係数。
 *
 * @param column 列名
 * @param value 主成分の向きの成分（符号付き）
 */
public record Loading(String column, double value) {}
