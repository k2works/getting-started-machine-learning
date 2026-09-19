package chapter15;

import java.util.Optional;
import java.util.OptionalDouble;

/** 乗客の特徴量。年齢と乗船港は分からないことがある。 */
public record Passenger(
    int pclass,
    String sex,
    OptionalDouble age,
    int sibSp,
    int parch,
    double fare,
    Optional<String> embarked) {}
