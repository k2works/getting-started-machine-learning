# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（Boston.csv・bike.tsv・weather.csv）で特徴量エンジニアリングを確かめるテスト。
# 学習データが無ければスキップする。
class BostonFeaturesTest < Minitest::Test
  C = GettingStartedMl::Chapter09

  def data_file(name)
    path = File.join(GettingStartedMl::Dataset.dir, name)
    skip "学習データ #{name} が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    path
  end

  def split
    C.prepare_boston(data_file("Boston.csv"), test_size: 0.3, seed: 0)
  end

  # Rumale の StandardScaler は Numo の stddev（件数 − 1 で割る標本標準偏差）を使うので、
  # 自作（件数で割る母標準偏差）とは sqrt((n − 1) / n) 倍ずれる
  def test_Rumale_の標準化は自作と標本標準偏差の分だけずれる
    x_train = split.x_train
    rm = rm_values(x_train)
    ours = rm_values(C::Standardizer.fit(x_train).transform(x_train))
    ratio = Math.sqrt((rm.size - 1).fdiv(rm.size))

    assert_all_close(ours.map { |value| value * ratio }, C::RumaleScaler.standardize(rm, rm))
  end

  def rm_values(x)
    x.map { |features| features.value("RM") }
  end

  def assert_all_close(expected, actual)
    assert_equal expected.size, actual.size
    expected.zip(actual).each_with_index do |(mine, other), index|
      assert_in_delta mine, other, 1e-12, "#{index} 件目"
    end
  end

  def test_weather_csvはutf8として読めない
    path = data_file("weather.csv")

    refute File.read(path).valid_encoding?
    assert_raises(CSV::InvalidEncodingError) { C.load_table(path) }
    assert_equal 3, C.load_weather(path).rows.size
  end

  def test_実行すると特徴量の組ごとの決定係数と天気ごとの平均利用者数を表示する
    %w[Boston.csv bike.tsv weather.csv].each { |name| data_file(name) }
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      訓練データ: 70 件, テストデータ: 30 件
      特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
      標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
      Rumale で標準化した RM: 平均 0.00, 標準偏差 0.9928
      決定係数:
        元の特徴量（3 列）: 訓練 0.6643, テスト 0.2932
        2 乗の項を追加（6 列）: 訓練 0.8242, テスト 0.4875
        交互作用の項も追加（9 列）: 訓練 0.8302, テスト 0.4413
      訓練データの PRICE の外れ値: 9 件
        外れ値を除いて 2 乗の項を追加: 訓練 0.7633, テスト 0.6437
      天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
    TEXT
  end
end
