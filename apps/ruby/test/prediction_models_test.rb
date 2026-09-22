# frozen_string_literal: true

require "test_helper"
require "rack/test"

# 第 15 章の統合テスト。実データで学習したモデルをつなぐ。学習データが無ければスキップする。
class PredictionModelsTest < Minitest::Test
  include Rack::Test::Methods

  C = GettingStartedMl::Chapter15

  def setup
    @dir = Dir.mktmpdir
  end

  def teardown
    FileUtils.remove_entry(@dir)
  end

  def data_dir
    dir = GettingStartedMl::Dataset.dir
    unless %w[cinema.csv Survived.csv].all? { |name| File.exist?(File.join(dir, name)) }
      skip "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）のでスキップする"
    end

    dir
  end

  # 実データで学習して一時ディレクトリに保存した置き場のサービス。
  def trained_service
    store = C::FileModelStore.new(@dir)
    C.train_and_save_models(data_dir, store)
    C::PredictionService.new(store)
  end

  def app
    C::Api.new(service: trained_service)
  end

  def test_保存したモデルを読み込んで予測できる
    service = trained_service
    movie = C::Movie.new(sns1: 100, sns2: 2000, actor: 300, original: 1)

    # Ruby の Random の分け方がほかの言語版と違うので、予測値もほかの言語版と一致しない
    assert_in_delta 7272.902816073627, service.predict_sales(movie), 1e-6
    assert service.survives?(C::Passenger.new(pclass: "1", sex: "female", age: "30", sib_sp: "0", parch: "0",
                                              fare: "80", embarked: "S"))
  end

  def test_学習していなければモデルを読み込めない
    health = C::PredictionService.new(C::FileModelStore.new(@dir)).health

    assert_equal [false, false], health.map(&:ready)
  end

  def test_学習したモデルで_API_が動く
    post "/cinema/sales", '{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}',
         "CONTENT_TYPE" => "application/json"

    assert_equal 200, last_response.status
    assert_match(/\A\{"sales":/, last_response.body)
  end
end
