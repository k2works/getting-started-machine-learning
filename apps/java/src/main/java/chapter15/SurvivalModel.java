package chapter15;

/** 乗客が生存するかを判定するモデルの約束。 */
@FunctionalInterface
public interface SurvivalModel {
  boolean survives(Passenger passenger);
}
