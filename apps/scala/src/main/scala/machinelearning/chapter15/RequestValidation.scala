package machinelearning.chapter15

import io.circe.{Json, parser}

/** プレゼンテーション層。HTTP の本文（JSON）を検証して、ドメイン層の型にする。
  * 不正な項目が複数あれば、理由をすべて集めて返す。
  */
object RequestValidation:
  /** 検証の結果。正しければ値を、不正なら理由の一覧を持つ。 */
  type Validation[A] = Either[Vector[String], A]

  /** 本文を映画の特徴量にする。 */
  def parseMovie(body: String): Validation[Movie] =
    parseWith(body) { json =>
      val sns1 = nonNegativeNumber(json, "sns1")
      val sns2 = nonNegativeNumber(json, "sns2")
      val actor = nonNegativeNumber(json, "actor")
      val original = oneOf(json, "original", Map("0" -> false, "1" -> true))
      combine(sns1, sns2, actor, original)((a, b, c, d) => Movie(a, b, c, d))
    }

  /** 本文を乗客の特徴量にする。年齢と乗船港は省略できる。 */
  def parsePassenger(body: String): Validation[Passenger] =
    parseWith(body) { json =>
      val pclass = oneOf(json, "pclass", Pclass.values.map(p => p.code.toString -> p).toMap)
      val sex = oneOf(json, "sex", Sex.values.map(s => s.code -> s).toMap)
      val age = optional(json, "age")(value => nonNegative("age", number("age", value)))
      val sibSp = nonNegativeInteger(json, "sib_sp")
      val parch = nonNegativeInteger(json, "parch")
      val fare = nonNegativeNumber(json, "fare")
      val embarked =
        optional(json, "embarked")(value =>
          choose("embarked", value, Embarked.values.map(e => e.code -> e).toMap)
        )
      val errors = Vector(pclass, sex, age, sibSp, parch, fare, embarked).flatMap(errorsOf)
      if errors.nonEmpty then Left(errors)
      else
        Right(
          Passenger(
            pclass.toOption.get,
            sex.toOption.get,
            age.toOption.get,
            sibSp.toOption.get,
            parch.toOption.get,
            fare.toOption.get,
            embarked.toOption.get
          )
        )
    }

  private def errorsOf(result: Validation[?]): Vector[String] = result.left.getOrElse(Vector.empty)

  private def combine[A, B, C, D, R](
      a: Validation[A],
      b: Validation[B],
      c: Validation[C],
      d: Validation[D]
  )(build: (A, B, C, D) => R): Validation[R] =
    val errors = Vector(a, b, c, d).flatMap(errorsOf)
    if errors.nonEmpty then Left(errors)
    else Right(build(a.toOption.get, b.toOption.get, c.toOption.get, d.toOption.get))

  private def parseWith[A](body: String)(validate: Json => Validation[A]): Validation[A] =
    parser.parse(body) match
      case Left(_)     => Left(Vector("JSON の形式が正しくありません"))
      case Right(json) =>
        if json.isObject then validate(json) else Left(Vector("JSON のオブジェクトにしてください"))

  /** 項目の値。無い項目と null は None。 */
  private def field(json: Json, name: String): Option[Json] =
    json.hcursor.downField(name).focus.filterNot(_.isNull)

  private def required(name: String, value: Option[Json]): Validation[Json] =
    value.toRight(Vector(s"$name を指定してください"))

  private def number(name: String, value: Json): Validation[Double] =
    value.asNumber.map(_.toDouble).toRight(Vector(s"$name は数値にしてください"))

  private def integer(name: String, value: Json): Validation[Int] =
    value.asNumber.flatMap(_.toInt).toRight(Vector(s"$name は整数にしてください"))

  private def nonNegative[A](name: String, value: Validation[A])(using
      numeric: Numeric[A]
  ): Validation[A] =
    value.flatMap(v =>
      if numeric.toDouble(v) >= 0 then Right(v) else Left(Vector(s"$name は 0 以上にしてください"))
    )

  private def nonNegativeNumber(json: Json, name: String): Validation[Double] =
    nonNegative(name, required(name, field(json, name)).flatMap(number(name, _)))

  private def nonNegativeInteger(json: Json, name: String): Validation[Int] =
    nonNegative(name, required(name, field(json, name)).flatMap(integer(name, _)))

  /** JSON の値（数値か文字列）を、決められた値の一覧のどれかに対応づける。 */
  private def oneOf[A](json: Json, name: String, choices: Map[String, A]): Validation[A] =
    required(name, field(json, name)).flatMap(choose(name, _, choices))

  private def choose[A](name: String, value: Json, choices: Map[String, A]): Validation[A] =
    val raw = value.asString.getOrElse(value.noSpaces)
    choices.get(raw).toRight(Vector(s"$name は ${choices.keys.toSeq.sorted.mkString("、")} のどれかにしてください"))

  /** 省略できる項目。無ければ None。 */
  private def optional[A](json: Json, name: String)(
      validate: Json => Validation[A]
  ): Validation[Option[A]] =
    field(json, name).fold(Right(None))(value => validate(value).map(Some(_)))
