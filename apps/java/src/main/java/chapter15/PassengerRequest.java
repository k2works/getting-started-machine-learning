package chapter15;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** 生存の予測の要求。年齢と乗船港は省略できる。 */
public record PassengerRequest(
    Integer pclass,
    String sex,
    Double age,
    @JsonProperty("sib_sp") Integer sibSp,
    Integer parch,
    Double fare,
    String embarked) {
  private static final Set<Integer> PASSENGER_CLASSES = Set.of(1, 2, 3);
  private static final Set<String> SEXES = Set.of("male", "female");
  private static final Set<String> PORTS = Set.of("C", "Q", "S");

  /** 検証して、正しければ乗客の特徴量にする。 */
  public Validated<Passenger> validate() {
    return Validated.of(
        Arrays.asList(
            Checks.required("pclass", pclass),
            Checks.required("sex", sex),
            Checks.required("sib_sp", sibSp),
            Checks.required("parch", parch),
            Checks.required("fare", fare),
            Checks.oneOf("pclass", pclass, PASSENGER_CLASSES),
            Checks.oneOf("sex", sex, SEXES),
            Checks.notNegative("age", age),
            Checks.notNegative("sib_sp", sibSp),
            Checks.notNegative("parch", parch),
            Checks.notNegative("fare", fare),
            Checks.oneOf("embarked", embarked, PORTS)),
        () ->
            new Passenger(
                pclass,
                sex,
                age == null ? OptionalDouble.empty() : OptionalDouble.of(age),
                sibSp,
                parch,
                fare,
                Optional.ofNullable(embarked)));
  }
}
