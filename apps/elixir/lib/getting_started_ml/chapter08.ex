defmodule GettingStartedMl.Chapter08 do
  @moduledoc """
  第 8 章: 実践的な分類と前処理パイプライン。タイタニック号の乗客データを扱う。

  前処理は `:type` の鍵を持つマップで表し、`fit_step/2`・`apply_step/2` を
  関数節のパターンマッチで振り分ける。Clojure 版の `defmulti` と違い、
  節は 1 つのモジュールに並ぶので、どんな前処理があるかはこのファイルを読めば分かる。

  決定木は Scholar に無いので、第 3 章の決定木を「1 件ごとの重み」を通す形に書き直した
  ものが最終実装になる。クラスの重みは Scholar の前処理にも口が無い。
  """

  alias GettingStartedMl.{Chapter02, Dataset}

  @feature_columns [:Pclass, :Sex, :Age, :SibSp, :Parch, :Fare, :Embarked]
  @target :Survived
  @class_weights [:none, :balanced]
  @survived 1

  # 形式の版。読み込むときに確かめる。
  @format_version 1

  @test_size 0.2
  @seed 0
  @max_depth 5

  @doc "モデルに渡す特徴量の列。PassengerId・Ticket・Cabin は使わない。"
  def feature_columns, do: @feature_columns

  @doc "正解ラベルの列（1 が生存、0 が死亡）。"
  def target, do: @target

  @doc "クラスの重みの付け方。`:none` は重みを付けない、`:balanced` は件数に反比例する重みを付ける。"
  def class_weights, do: @class_weights

  @doc "学習済みのパイプラインの保存先（apps/elixir/model/ は .gitignore の対象）。"
  def model_file, do: "model/survived.model"

  @doc "行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。"
  def features_table(rows), do: %{columns: @feature_columns, rows: rows}

  @doc "行の Survived 列を、整数の正解ラベルにする。"
  def target_labels(rows), do: Enum.map(rows, &String.to_integer(Chapter02.text(&1, @target)))

  ## 前処理

  @doc "グループ別の中央値で補完する前処理を作る。"
  def group_median_imputer(column, by), do: %{type: :group_median, column: column, by: by}

  @doc "最頻値で補完する前処理を作る。"
  def most_frequent_imputer(column), do: %{type: :most_frequent, column: column}

  @doc "ダミー変数化する前処理を作る。"
  def dummy_encoder(columns), do: %{type: :dummy, columns: columns}

  @doc "中央値。件数が偶数なら中央の 2 つの平均。"
  def median(values) do
    sorted = Enum.sort(values)
    middle = div(length(sorted), 2)

    if rem(length(sorted), 2) == 1 do
      Enum.at(sorted, middle)
    else
      (Enum.at(sorted, middle - 1) + Enum.at(sorted, middle)) / 2
    end
  end

  @doc "訓練データから変換に必要な値を求め、学習済みの前処理を返す。"
  def fit_step(%{type: :group_median, column: column, by: by} = transformer, x) do
    known = Enum.reject(x.rows, &Chapter02.missing?(&1, column))

    medians =
      known
      |> Enum.group_by(&group_of(&1, by))
      |> Map.new(fn {group, rows} ->
        {group, median(Enum.map(rows, &Chapter02.number(&1, column)))}
      end)

    transformer
    |> Map.put(:medians, medians)
    |> Map.put(:overall_median, median(Enum.map(known, &Chapter02.number(&1, column))))
  end

  def fit_step(%{type: :most_frequent, column: column} = transformer, x) do
    # 値の順に並べ、厳密な不等号で畳むので、同数なら値の順で前のものを選ぶ
    {value, _count} =
      x.rows
      |> Enum.reject(&Chapter02.missing?(&1, column))
      |> Enum.map(&Chapter02.text(&1, column))
      |> Enum.frequencies()
      |> Enum.sort_by(&elem(&1, 0))
      |> Enum.reduce(fn entry, best ->
        if elem(entry, 1) > elem(best, 1), do: entry, else: best
      end)

    Map.put(transformer, :most_frequent, value)
  end

  def fit_step(%{type: :dummy, columns: columns} = transformer, x) do
    # 学習した値は「{列名, カテゴリの並び} の組のリスト」。マップはキーの順を保たない
    Map.put(
      transformer,
      :dummies,
      Enum.map(columns, fn column -> {column, tl(categories_of(x, column))} end)
    )
  end

  @doc "学習済みの前処理でデータを変換する。"
  def apply_step(%{type: :group_median, column: column, by: by} = fitted, x) do
    %{
      x
      | rows:
          Enum.map(x.rows, fn row ->
            if Chapter02.missing?(row, column) do
              value = Map.get(fitted.medians, group_of(row, by), fitted.overall_median)
              Map.put(row, column, to_string(value))
            else
              row
            end
          end)
    }
  end

  def apply_step(%{type: :most_frequent, column: column} = fitted, x) do
    %{
      x
      | rows:
          Enum.map(x.rows, fn row ->
            if Chapter02.missing?(row, column),
              do: Map.put(row, column, fitted.most_frequent),
              else: row
          end)
    }
  end

  def apply_step(%{type: :dummy, dummies: dummies}, x) do
    columns =
      Enum.reduce(dummies, x.columns, fn {column, categories}, columns ->
        Enum.reject(columns, &(&1 == column)) ++
          Enum.map(categories, &dummy_column(column, &1))
      end)

    %{columns: columns, rows: Enum.map(x.rows, &encode_row(&1, dummies))}
  end

  # 1 行を、学習したカテゴリごとの 0 と 1 の列に広げる。元の列はそのまま残る。
  defp encode_row(row, dummies) do
    Enum.reduce(dummies, row, fn {column, categories}, encoded ->
      encode_column(encoded, column, categories, Chapter02.text(row, column))
    end)
  end

  defp encode_column(encoded, column, categories, value) do
    Enum.reduce(categories, encoded, fn category, encoded ->
      Map.put(encoded, dummy_column(column, category), if(value == category, do: "1", else: "0"))
    end)
  end

  ## クラスの重みを付けた決定木

  @doc "重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。"
  def weighted_gini(labels, weights) do
    total = Enum.sum(weights)

    1.0 -
      Enum.sum(
        Enum.map(weight_sums(labels, weights), fn {_label, weight} ->
          weight / total * (weight / total)
        end)
      )
  end

  @doc "クラスの件数に反比例する重み（件数 ÷ (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。"
  def balanced_weights(t) do
    counts = Enum.frequencies(t)
    Enum.map(t, &(length(t) / (map_size(counts) * counts[&1])))
  end

  @doc "クラスの重みの付け方から、1 件ごとの重みを求める。"
  def weights_of(t, :none), do: List.duplicate(1.0, length(t))
  def weights_of(t, :balanced), do: balanced_weights(t)
  def weights_of(_t, class_weight), do: raise(ArgumentError, "知らない重みの付け方です: #{class_weight}")

  @doc "重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。"
  def weighted_majority(labels, weights) do
    {label, _weight} =
      Enum.reduce(weight_sums(labels, weights), fn entry, best ->
        if elem(entry, 1) > elem(best, 1), do: entry, else: best
      end)

    label
  end

  @doc "左右の重み付き不純度の、重みによる平均が最も小さくなる分割を返す。分けられなければ `nil`。"
  def best_split([], _t, _w, _columns), do: nil

  def best_split(x, t, w, columns) do
    if weighted_gini(t, w) == 0.0 do
      nil
    else
      case Enum.flat_map(columns, &candidates(x, t, w, &1, Enum.sum(w))) do
        [] -> nil
        all -> Enum.min_by(all, & &1.impurity)
      end
    end
  end

  @doc "訓練データから、クラスの重みを付けた決定木を作る。"
  def fit_tree(x, t, columns, max_depth, class_weight) do
    build(x, t, weights_of(t, class_weight), columns, max_depth)
  end

  @doc "木をたどって 1 件のラベルを予測する。"
  def predict_tree_one(%{label: label}, _features), do: label

  def predict_tree_one(%{split: split, left: left, right: right}, features) do
    if goes_left?(split, features),
      do: predict_tree_one(left, features),
      else: predict_tree_one(right, features)
  end

  @doc "特徴量ごとのラベルを予測する。"
  def predict_tree(tree, x), do: Enum.map(x, &predict_tree_one(tree, &1))

  ## パイプライン

  @doc "Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。"
  def build_pipeline(max_depth, class_weight) do
    %{
      transformers: [
        group_median_imputer(:Age, [:Pclass, :Sex]),
        most_frequent_imputer(:Embarked),
        dummy_encoder([:Sex, :Embarked])
      ],
      max_depth: max_depth,
      class_weight: class_weight
    }
  end

  @doc "前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。"
  def to_features(x) do
    Enum.map(x.rows, fn row -> Map.new(x.columns, &feature_of(row, &1)) end)
  end

  defp feature_of(row, column) do
    case Chapter02.number(row, column) do
      nil -> raise ArgumentError, "欠損値が残っています: #{column}"
      value -> {column, value}
    end
  end

  @doc "訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。"
  def fit(pipeline, x, t) do
    {fitted, prepared} =
      Enum.reduce(pipeline.transformers, {[], x}, fn transformer, {fitted, prepared} ->
        step = fit_step(transformer, prepared)
        {fitted ++ [step], apply_step(step, prepared)}
      end)

    %{
      transformers: fitted,
      columns: prepared.columns,
      tree:
        fit_tree(
          to_features(prepared),
          t,
          prepared.columns,
          pipeline.max_depth,
          pipeline.class_weight
        )
    }
  end

  @doc "学習済みの前処理を順に合成して、データを変換する。"
  def transform(fitted_pipeline, x) do
    Enum.reduce(fitted_pipeline.transformers, x, &apply_step/2)
  end

  @doc "前処理をして、モデルに渡す特徴量にする。"
  def features(fitted_pipeline, x), do: to_features(transform(fitted_pipeline, x))

  @doc "前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。"
  def predict(fitted_pipeline, x) do
    predict_tree(fitted_pipeline.tree, features(fitted_pipeline, x))
  end

  ## 保存と読み込み

  @doc """
  学習済みのパイプラインを `:erlang.term_to_binary/1` で保存する。

  前処理もモデルもマップとリストだけでできているので、そのまま往復できる。
  """
  def save_model(fitted_pipeline, path) do
    File.mkdir_p!(Path.dirname(path))
    File.write!(path, :erlang.term_to_binary(Map.put(fitted_pipeline, :format, @format_version)))
  end

  @doc """
  保存したパイプラインを読み込む。形式が違えば失敗する。

  `:safe` を付けると、ファイルに書かれた未知のアトムを作らない。
  信頼できないファイルでアトムを増やされないための指定で、Clojure 版が
  `clojure.core/read-string` ではなく `clojure.edn/read-string` を使ったのと同じ用心。
  """
  def load_model(path) do
    loaded =
      try do
        :erlang.binary_to_term(File.read!(path), [:safe])
      rescue
        ArgumentError -> reraise ArgumentError, [message: "モデルとして読めません: #{path}"], __STACKTRACE__
      end

    case loaded do
      %{format: @format_version} -> Map.delete(loaded, :format)
      %{format: version} -> raise ArgumentError, "対応していない形式のモデルです: #{inspect(version)}"
      _ -> raise ArgumentError, "モデルとして読めません: #{path}"
    end
  end

  ## 評価

  @doc "学習済みのパイプラインを、訓練データとテストデータで評価する。"
  def evaluate(fitted_pipeline, split) do
    predictions = predict(fitted_pipeline, features_table(split.x_test))
    labels = split.t_test

    %{
      train_accuracy:
        accuracy(predict(fitted_pipeline, features_table(split.x_train)), split.t_train),
      test_accuracy: accuracy(predictions, labels),
      found_survivors:
        Enum.count(Enum.zip(predictions, labels), fn {p, l} ->
          p == @survived and l == @survived
        end),
      survivors: Enum.count(labels, &(&1 == @survived))
    }
  end

  ## 実行

  @doc "クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。"
  def run(path \\ model_file()) do
    rows = Chapter02.load_table(Path.join(Dataset.dir(), "Survived.csv")).rows
    t = target_labels(rows)
    split = Chapter02.split_train_test(rows, t, @test_size, @seed)
    survivors = Enum.count(t, &(&1 == @survived))

    IO.puts("データ件数: #{length(rows)}（生存 #{survivors}, 死亡 #{length(t) - survivors}）")
    IO.puts("訓練データ: #{length(split.x_train)} 件, テストデータ: #{length(split.x_test)} 件")

    pipelines =
      Map.new(@class_weights, fn class_weight ->
        pipeline =
          fit(
            build_pipeline(@max_depth, class_weight),
            features_table(split.x_train),
            split.t_train
          )

        result = evaluate(pipeline, split)

        IO.puts(
          "classWeight=#{class_weight}: 訓練 #{format(result.train_accuracy)}, " <>
            "テスト #{format(result.test_accuracy)}, " <>
            "生存者 #{result.survivors} 人中 #{result.found_survivors} 人を発見"
        )

        {class_weight, pipeline}
      end)

    save_model(pipelines[:balanced], path)
    loaded = load_model(path)
    IO.puts("保存したモデル: #{if loaded == pipelines[:balanced], do: "読み込めました", else: "読み込めません"}")
    IO.puts("架空の乗客の予測: #{inspect(predict(loaded, new_passengers()))}")
  end

  # 年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性）。
  defp new_passengers do
    features_table(
      Enum.map(
        [
          ["1", "female", "", "0", "0", "50", "C"],
          ["3", "male", "", "0", "0", "8", "S"]
        ],
        &Map.new(Enum.zip(@feature_columns, &1))
      )
    )
  end

  # 行のグループ。by の列の値を並べたリストで、マップのキーに使う。
  defp group_of(row, by), do: Enum.map(by, &Chapter02.text(row, &1))

  # 列の値を重複なく並べ替える。欠損値は除く。
  defp categories_of(x, column) do
    x.rows
    |> Enum.reject(&Chapter02.missing?(&1, column))
    |> Enum.map(&Chapter02.text(&1, column))
    |> Enum.uniq()
    |> Enum.sort()
  end

  # ダミー変数の列の名前。「元の列名_カテゴリ」にする。
  defp dummy_column(column, category), do: :"#{column}_#{category}"

  # ラベルごとの重みの合計を、ラベルが先に現れた順のリストで返す。
  # マップはキーの順を保たないので、順を使いたいところではリストで持つ。
  defp weight_sums(labels, weights) do
    sums =
      Enum.zip_reduce(labels, weights, %{}, fn label, weight, acc ->
        Map.update(acc, label, weight, &(&1 + weight))
      end)

    labels |> Enum.uniq() |> Enum.map(&{&1, sums[&1]})
  end

  # 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
  defp candidates(x, t, w, feature, total) do
    sorted =
      x
      |> Enum.map(&Map.fetch!(&1, feature))
      |> Enum.zip(Enum.zip(t, w))
      |> Enum.sort_by(&elem(&1, 0))

    values = Enum.map(sorted, &elem(&1, 0))
    labels = Enum.map(sorted, fn {_value, {label, _weight}} -> label end)
    weights = Enum.map(sorted, fn {_value, {_label, weight}} -> weight end)

    1..(length(sorted) - 1)//1
    |> Enum.filter(fn i -> Enum.at(values, i - 1) != Enum.at(values, i) end)
    |> Enum.map(fn i ->
      {left_labels, right_labels} = Enum.split(labels, i)
      {left_weights, right_weights} = Enum.split(weights, i)

      %{
        feature: feature,
        threshold: (Enum.at(values, i - 1) + Enum.at(values, i)) / 2.0,
        impurity:
          (Enum.sum(left_weights) * weighted_gini(left_labels, left_weights) +
             Enum.sum(right_weights) * weighted_gini(right_labels, right_weights)) / total
      }
    end)
  end

  defp goes_left?(split, features), do: Map.fetch!(features, split.feature) <= split.threshold

  # 深さの上限まで分割を繰り返して木を作る。max_depth が nil なら上限なし。
  defp build(x, t, w, columns, max_depth) do
    split = if max_depth == 0, do: nil, else: best_split(x, t, w, columns)

    if is_nil(split) do
      %{label: weighted_majority(t, w)}
    else
      {left, right} =
        Enum.split_with(Enum.zip(x, Enum.zip(t, w)), &goes_left?(split, elem(&1, 0)))

      next_depth = if max_depth, do: max_depth - 1

      %{
        split: split,
        left: branch(left, columns, next_depth),
        right: branch(right, columns, next_depth)
      }
    end
  end

  defp branch(triples, columns, max_depth) do
    build(
      Enum.map(triples, &elem(&1, 0)),
      Enum.map(triples, fn {_x, {label, _weight}} -> label end),
      Enum.map(triples, fn {_x, {_label, weight}} -> weight end),
      columns,
      max_depth
    )
  end

  defp accuracy(predictions, labels) do
    Enum.count(Enum.zip(predictions, labels), fn {p, l} -> p == l end) / length(labels)
  end

  defp format(value), do: ~c"~.3f" |> :io_lib.format([value]) |> to_string()
end
