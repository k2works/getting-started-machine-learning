# frozen_string_literal: true

require "test_helper"
require_relative "support/chapter15_fakes"
require_relative "support/model_store_contract"

# 第 15 章の予測サービスのテスト。置き場は偽物に差し替える。
class Chapter15ServiceTest < Minitest::Test
  include ModelStoreContract

  C = GettingStartedMl::Chapter15
  Fake = Chapter15Fakes::FakeModelStore

  # 偽物も置き場の約束を満たしていることを、本物と同じテストで確かめる。
  def store_with_models
    Fake.new(sales: true, survival: true)
  end

  def store_without_models
    Fake.new(sales: false, survival: false)
  end

  def service(sales: true, survival: true)
    C::PredictionService.new(Fake.new(sales:, survival:))
  end

  def test_置き場のモデルで興行収入を予測する
    assert_in_delta 4321.5, service.predict_sales(CONTRACT_MOVIE), 1e-12
  end

  def test_置き場のモデルで生存を予測する
    assert service.survives?(CONTRACT_PASSENGER)
  end

  def test_モデルが無ければ_ModelNotFound_をそのまま投げる
    assert_raises(C::ModelNotFound) { service(sales: false).predict_sales(CONTRACT_MOVIE) }
  end

  def test_ヘルスチェックはモデルごとに読み込めるかどうかを返す
    assert_equal [C::ModelHealth.new(name: "cinema", ready: true), C::ModelHealth.new(name: "survived", ready: false)],
                 service(survival: false).health
  end

  def test_モデルが無いこと以外の失敗でも読み込めないと答える
    assert_equal [false, false], C::PredictionService.new(Chapter15Fakes::BrokenModelStore.new).health.map(&:ready)
  end
end
