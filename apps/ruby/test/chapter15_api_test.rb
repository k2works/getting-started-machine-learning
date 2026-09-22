# frozen_string_literal: true

require "test_helper"
require "rack/test"
require_relative "support/chapter15_fakes"

# 第 15 章の予測 API のテスト。置き場を偽物に差し替えるので、学習データが無くても走る。
class Chapter15ApiTest < Minitest::Test
  include Rack::Test::Methods

  C = GettingStartedMl::Chapter15

  def setup
    @store = Chapter15Fakes::FakeModelStore.new(sales: true, survival: true)
  end

  # rack-test が呼ぶ Rack アプリケーション。サーバーは起動しない。
  def app
    C::Api.new(service: C::PredictionService.new(@store))
  end

  def post_json(path, body)
    post path, body, "CONTENT_TYPE" => "application/json"
  end

  def test_興行収入を予測して_JSON_で返す
    post_json "/cinema/sales", '{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}'

    assert_equal 200, last_response.status
    assert_equal "application/json", last_response.media_type
    assert_equal '{"sales":4321.5}', last_response.body
  end

  def test_生存を予測して_JSON_で返す
    post_json "/survived",
              '{"pclass": 1, "sex": "female", "age": 30, "sib_sp": 0, "parch": 0, "fare": 80, "embarked": "S"}'

    assert_equal 200, last_response.status
    assert_equal '{"survived":true}', last_response.body
  end

  def test_年齢と乗船港は省略できる
    post_json "/survived", '{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 7.25}'

    assert_equal 200, last_response.status
  end

  def test_入力が不正なら四百二十二と理由の一覧を返す
    post_json "/cinema/sales", '{"sns1": 100}'

    assert_equal 422, last_response.status
    assert_equal({ "detail" => ["sns2 は必須です", "actor は必須です", "original は必須です"] },
                 JSON.parse(last_response.body))
  end

  def test_選択肢にない値を指摘する
    post_json "/survived", '{"pclass": 4, "sex": "unknown", "sib_sp": 0, "parch": 0, "fare": 10}'

    assert_equal 422, last_response.status
    assert_includes last_response.body, "pclass は 1、2、3 のどれかにしてください"
    assert_includes last_response.body, "sex は female、male のどれかにしてください"
  end

  def test_JSON_として読めなければ四百二十二を返し内部の例外を漏らさない
    ['{"sns1": "たくさん"}', "{ここは JSON ではない", ""].each do |body|
      post_json "/cinema/sales", body

      assert_equal 422, last_response.status, body
      assert_equal({ "detail" => ["JSON の形式または値の型が正しくありません"] }, JSON.parse(last_response.body))
    end
  end

  def test_モデルが無ければ五百三を返す
    @store = Chapter15Fakes::FakeModelStore.new(sales: false, survival: true)
    post_json "/cinema/sales", '{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}'

    assert_equal 503, last_response.status
    assert_equal '{"detail":"モデルがありません: cinema"}', last_response.body
  end

  def test_それ以外の失敗は五百を返し理由を漏らさない
    @store = Chapter15Fakes::BrokenModelStore.new
    post_json "/survived", '{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 7.25}'

    assert_equal 500, last_response.status
    assert_equal '{"detail":"予測できませんでした"}', last_response.body
  end

  def test_ヘルスチェックはモデルごとの状態を返す
    get "/health"

    assert_equal 200, last_response.status
    assert_equal '{"status":"ok","models":{"cinema":true,"survived":true}}', last_response.body
  end

  # rack-test は 1 つのテストの中で app を 1 度しか作らないので、置き場を替えるならテストを分ける
  def test_読み込めないモデルがあればヘルスチェックは_degraded_を返す
    @store = Chapter15Fakes::FakeModelStore.new(sales: true, survival: false)
    get "/health"

    assert_equal '{"status":"degraded","models":{"cinema":true,"survived":false}}', last_response.body
  end

  def test_知らないパスは四百四許していないメソッドは四百五を返す
    get "/unknown"

    assert_equal 404, last_response.status

    get "/cinema/sales"

    assert_equal 405, last_response.status
    assert_equal "POST", last_response.headers["Allow"]
  end
end
