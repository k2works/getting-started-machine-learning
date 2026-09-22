# frozen_string_literal: true

module GettingStartedMl
  # 第 9 章の Boston データの前処理と、特徴量の組ごとの決定係数。
  module Chapter09
    # 正解の列。
    BOSTON_TARGET = "PRICE"
    # ダミー変数にするカテゴリの列。
    BOSTON_CATEGORY = "CRIME"

    # 訓練データとテストデータの決定係数。
    Scores = Data.define(:train, :test)

    module_function

    # CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する →
    # 訓練データの平均値で両方の欠損値を補完する」を行う。
    def prepare_boston(csv_file, test_size:, seed:)
      columns, rows, prices = boston_features_and_prices(Chapter02::Table.load(csv_file))
      split = Chapter02.split_train_test(rows, prices, test_size:, seed:)
      means = Chapter02.column_means(split.x_train, columns)

      split.with(x_train: Chapter02.fill_missing(split.x_train, columns, means),
                 x_test: Chapter02.fill_missing(split.x_test, columns, means))
    end

    # CRIME をダミー変数にしてから、特徴量の列・行・価格に分ける。価格は数値に読み直す。
    def boston_features_and_prices(table)
      crimes = table.rows.map { |row| row.text(BOSTON_CATEGORY) }
      encoded = encode(table, BOSTON_CATEGORY, categories(crimes))
      columns, rows, labels = Chapter02.split_features_and_target(encoded, BOSTON_TARGET)

      [columns, rows, labels.map { |label| Float(label) }]
    end

    # 多項式特徴量を作って項を選び、訓練データで標準化の平均と標準偏差を求めてから学習し、決定係数を測る。
    def score_feature_set(split, columns, terms)
      x_train, x_test = standardized_terms(split, columns, terms)
      model = LinearModel.fit(x_train, split.t_train)

      Scores.new(train: r_squared(split.t_train, model.predict(x_train)),
                 test: r_squared(split.t_test, model.predict(x_test)))
    end

    # 訓練データとテストデータで項を作って選び、訓練データの平均と標準偏差で標準化した値の並びにする。
    def standardized_terms(split, columns, terms)
      train, test = [split.x_train, split.x_test].map { |x| select(expand(x, columns), terms) }
      standardizer = Standardizer.fit(train)

      [train, test].map { |x| standardizer.transform(x).map(&:values) }
    end
  end
end
