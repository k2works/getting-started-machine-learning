# frozen_string_literal: true

require "test_helper"
require_relative "support/model_store_contract"

# 第 15 章のファイルの置き場のテスト。手で作った小さなモデルを一時ディレクトリに保存する。
class Chapter15StoreTest < Minitest::Test
  include ModelStoreContract

  C = GettingStartedMl::Chapter15
  C8 = GettingStartedMl::Chapter08

  def setup
    @dir = Dir.mktmpdir
  end

  def teardown
    FileUtils.remove_entry(@dir)
  end

  def sales_model
    GettingStartedMl::Chapter07::LinearModel.new(intercept: 10.0, columns: %w[SNS1 SNS2 actor original],
                                                 coefficients: [1.0, 2.0, 3.0, 4.0])
  end

  # 架空の 4 人で学習したパイプライン。年齢と港の欠損値の補完まで含む。
  def survival_pipeline
    rows = [%w[1 female 30 0 0 80 S], ["3", "male", "", "0", "0", "8", "S"],
            ["1", "female", "", "1", "0", "70", "C"], ["3", "male", "40", "0", "0", "7", ""]]
    C8::Pipeline.build(max_depth: 2, class_weight: :balanced)
                .fit(C8::Survived.features(rows.map { |values| C8::Survived.passenger(values) }), [1, 0, 1, 0])
  end

  def store_with_models
    C::FileModelStore.new(@dir).tap do |store|
      store.save_sales_model(sales_model)
      store.save_survival_model(survival_pipeline)
    end
  end

  def store_without_models
    C::FileModelStore.new(File.join(@dir, "empty"))
  end

  def test_保存した線形回帰で予測できる
    # 10 + 1×100 + 2×2000 + 3×300 + 4×1
    assert_in_delta 5014.0, store_with_models.load_sales_model.predict_sales(CONTRACT_MOVIE), 1e-9
  end

  def test_保存したパイプラインは欠損値を補完してから予測する
    assert store_with_models.load_survival_model.survives?(CONTRACT_PASSENGER)
  end

  def test_モデルの形をしていないファイルは読み込めない
    File.binwrite(File.join(@dir, "cinema.dump"), Marshal.dump("モデルではない"))

    error = assert_raises(TypeError) { C::FileModelStore.new(@dir).load_sales_model }
    assert_equal "学習済みの線形回帰ではありません: String", error.message
  end
end
