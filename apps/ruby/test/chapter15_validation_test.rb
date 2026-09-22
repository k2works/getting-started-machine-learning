# frozen_string_literal: true

require "test_helper"

# 第 15 章の要求の読み取りと検証のテスト。HTTP もモデルも要らない。
class Chapter15ValidationTest < Minitest::Test
  C = GettingStartedMl::Chapter15
  V = GettingStartedMl::Chapter15::Validation

  def test_必須の値が無ければ理由を並べて返す
    assert_equal ["sns2 は必須です", "actor は必須です", "original は必須です"],
                 V.movie({ "sns1" => 100 }).errors
  end

  def test_負の値と選択肢にない値を指摘する
    request = { "sns1" => -1, "sns2" => 2000, "actor" => 300, "original" => 2 }

    assert_equal ["sns1 は 0 以上にしてください", "original は 0、1 のどれかにしてください"],
                 V.movie(request).errors
  end

  def test_正しい要求は映画の特徴量になる
    validated = V.movie({ "sns1" => 100, "sns2" => 2000.5, "actor" => 300, "original" => 1 })

    assert_predicate validated, :valid?
    assert_equal C::Movie.new(sns1: 100, sns2: 2000.5, actor: 300, original: 1), validated.value
  end

  def test_年齢と乗船港は省略できる
    request = { "pclass" => 3, "sex" => "male", "sib_sp" => 0, "parch" => 0, "fare" => 7.25 }

    assert_equal C::Passenger.new(pclass: "3", sex: "male", age: "", sib_sp: "0", parch: "0", fare: "7.25",
                                  embarked: ""),
                 V.passenger(request).value
  end

  def test_等級と性別と乗船港の選択肢を確かめる
    request = { "pclass" => 4, "sex" => "unknown", "sib_sp" => 0, "parch" => 0, "fare" => 10, "embarked" => "X" }

    assert_equal ["pclass は 1、2、3 のどれかにしてください", "sex は female、male のどれかにしてください",
                  "embarked は C、Q、S のどれかにしてください"],
                 V.passenger(request).errors
  end

  def test_不正な要求には値が無い
    assert_nil V.movie({}).value
  end

  def test_型の合う_JSON_のオブジェクトを読む
    assert_equal({ "sns1" => 100, "original" => 1 }, V.read_json('{"sns1": 100, "original": 1}', V::MOVIE_TYPES))
  end

  def test_null_は省略と同じに扱う
    assert_equal({ "age" => nil }, V.read_json('{"age": null}', V::PASSENGER_TYPES))
  end

  def test_JSON_として読めないか型が合わなければ_nil_を返す
    ['{"sns1": "たくさん"}', '{"original": 1.5}', "{ここは JSON ではない", "", "[1, 2]"].each do |body|
      assert_nil V.read_json(body, V::MOVIE_TYPES), body
    end
  end
end
