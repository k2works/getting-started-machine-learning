defmodule GettingStartedMl.Chapter10 do
  @moduledoc """
  第 10 章: ロジスティック回帰とアンサンブル学習。

  分類器は「訓練データを受け取り、予測する関数を返す関数」で表す。
  Elixir には interface も protocol の宣言も要らず、第 3 章の決定木も
  Scholar のロジスティック回帰も、同じ形の関数に包むだけで同じ `score/3` で評価できる。

      分類器 = fn x, t, columns -> fn x -> ラベルの並び end end

  Scholar にランダムフォレストは無いので、ここで作る森がそのまま最終実装になる。
  """

  alias GettingStartedMl.{Chapter01, Chapter02, Chapter03, Dataset, Random}
  alias Scholar.Linear.LogisticRegression

  @epsilon 1.0e-12
  @default_learning_rate 1.0
  @default_epochs 5000
  @n_estimators 100
  @max_features 2
  @shallow_depth 2
  @seed 0

  @doc "森に作る木の数。"
  def n_estimators, do: @n_estimators

  ## ソフトマックス関数と交差エントロピー

  @doc """
  スコアを、合計が 1 になる確率に変換する。

  最大値を引いてから `exp` を求めるので、大きな値でもあふれない。
  引いた分は分母と分子で打ち消し合うので、結果は変わらない。
  """
  def softmax(z) do
    maximum = Enum.max(z)
    exps = Enum.map(z, &:math.exp(&1 - maximum))
    total = Enum.sum(exps)

    Enum.map(exps, &(&1 / total))
  end

  @doc """
  交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。

  確率が 0 のときに `log 0` が `-Infinity` にならないよう、ごく小さい値を足す。
  """
  def cross_entropy(probabilities, targets) do
    sum =
      probabilities
      |> Enum.zip(targets)
      |> Enum.map(fn {p, target} -> :math.log(Enum.at(p, target) + @epsilon) end)
      |> Enum.sum()

    -(sum / length(probabilities))
  end

  ## ロジスティック回帰

  @doc """
  バッチ勾配降下法で重みと切片を学習する。品種は名前の順に並べる。

  重みは `weights[特徴量][品種]` のリストのリストで持ち、ループの順（特徴量が外、
  品種が内）を Java 版・Clojure 版にそろえる。浮動小数点の足し算は順によって
  結果が変わるので、数値を一致させるには順まで合わせる。
  """
  def logistic_fit(
        x,
        t,
        columns,
        learning_rate \\ @default_learning_rate,
        epochs \\ @default_epochs
      ) do
    classes = t |> Enum.uniq() |> Enum.sort()
    targets = Enum.map(t, fn label -> Enum.find_index(classes, &(&1 == label)) end)
    rows = Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end)
    weights = List.duplicate(List.duplicate(0.0, length(classes)), length(columns))
    bias = List.duplicate(0.0, length(classes))

    {weights, bias, losses} =
      descend(rows, targets, weights, bias, learning_rate, length(rows), epochs, [])

    %{
      columns: columns,
      classes: classes,
      weights: weights,
      bias: bias,
      losses: Enum.reverse(losses)
    }
  end

  @doc "スコアが最大の品種を予測する。同じ値なら先に現れたほうを選ぶ。"
  def logistic_predict(%{columns: columns, classes: classes, weights: weights, bias: bias}, x) do
    Enum.map(x, fn features ->
      scores = columns |> Enum.map(&Map.fetch!(features, &1)) |> row_scores(weights, bias)

      Enum.at(classes, argmax(scores))
    end)
  end

  @doc "ロジスティック回帰の分類器。学習して、予測する関数を返す。"
  def logistic_trainer(learning_rate \\ @default_learning_rate, epochs \\ @default_epochs) do
    fn x, t, columns ->
      model = logistic_fit(x, t, columns, learning_rate, epochs)

      fn x -> logistic_predict(model, x) end
    end
  end

  ## ランダムフォレスト

  @doc "サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。"
  def majority_vote([first | _] = votes) do
    Enum.map(0..(length(first) - 1)//1, fn sample ->
      votes |> Enum.map(&Enum.at(&1, sample)) |> Chapter03.majority()
    end)
  end

  @doc """
  0 から `size - 1` までの行番号を、重複を許して `size` 個選ぶ。

  乱数の状態を受け取り、使い終わった状態と一緒に返す。森を作る途中で
  状態を引き継ぐ必要があるので、シードではなく状態を回す。
  """
  def bootstrap_sample(size, state) do
    Enum.map_reduce(1..size//1, state, fn _, state -> Random.next_int(state, size) end)
  end

  @doc """
  ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を `n_estimators` 本学習する。

  第 3 章の `fit/4` は純粋な関数なので、包むアダプターは要らない。
  """
  def forest_fit(x, t, columns, n_estimators, max_features, max_depth, seed) do
    xs = List.to_tuple(x)
    ts = List.to_tuple(t)

    {trees, _state} =
      Enum.map_reduce(1..n_estimators//1, Random.new(seed), fn _, state ->
        {rows, state} = bootstrap_sample(tuple_size(xs), state)
        {shuffled, state} = shuffle_with_state(columns, state)
        chosen = MapSet.new(Enum.take(shuffled, max_features))
        # 列の順は元のまま残す
        tree_columns = Enum.filter(columns, &MapSet.member?(chosen, &1))
        sample_x = Enum.map(rows, &Map.take(elem(xs, &1), tree_columns))
        sample_t = Enum.map(rows, &elem(ts, &1))

        {%{
           columns: tree_columns,
           rows: rows,
           tree: Chapter03.fit(sample_x, sample_t, tree_columns, max_depth)
         }, state}
      end)

    %{trees: trees}
  end

  @doc "木ごとの予測を多数決でまとめる。"
  def forest_predict(%{trees: trees}, x) do
    trees
    |> Enum.map(fn %{columns: columns, tree: tree} ->
      Chapter03.predict(tree, Enum.map(x, &Map.take(&1, columns)))
    end)
    |> majority_vote()
  end

  @doc "ランダムフォレストの分類器。`max_depth` が `nil` なら深さの上限なし。"
  def forest_trainer(n_estimators, max_features, max_depth, seed) do
    fn x, t, columns ->
      forest = forest_fit(x, t, columns, n_estimators, max_features, max_depth, seed)

      fn x -> forest_predict(forest, x) end
    end
  end

  @doc "第 3 章の決定木の分類器。"
  def tree_trainer(max_depth) do
    fn x, t, columns ->
      tree = Chapter03.fit(x, t, columns, max_depth)

      fn x -> Chapter03.predict(tree, x) end
    end
  end

  ## 特徴量の重要度

  @doc "決定木 1 本の重要度。合計が 1 になるようにする。"
  def tree_importances(tree, x, t, columns) do
    tree
    |> impurity_decreases(x, t)
    |> Enum.reduce(Map.new(columns, &{&1, 0.0}), fn {feature, amount}, totals ->
      Map.update!(totals, feature, &(&1 + amount))
    end)
    |> normalize()
  end

  @doc "木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。"
  def forest_importances(%{trees: trees}, x, t, columns) do
    xs = List.to_tuple(x)
    ts = List.to_tuple(t)

    trees
    |> Enum.reduce(Map.new(columns, &{&1, 0.0}), fn tree, totals ->
      sample_x = Enum.map(tree.rows, &Map.take(elem(xs, &1), tree.columns))
      sample_t = Enum.map(tree.rows, &elem(ts, &1))

      tree.tree
      |> tree_importances(sample_x, sample_t, tree.columns)
      |> Enum.reduce(totals, fn {feature, value}, acc ->
        Map.update!(acc, feature, &(&1 + value / length(trees)))
      end)
    end)
    |> normalize()
  end

  ## Scholar のロジスティック回帰

  @doc """
  Scholar のロジスティック回帰を、同じ形の分類器にする。

  Scholar はラベルを 0 から始まる整数で受け取るので、品種の名前を名前の順の
  番号に直して渡し、予測を名前に戻す。テンソルは `type: :f64` を明示する。
  """
  def scholar_logistic_trainer(opts \\ []) do
    fn x, t, columns ->
      classes = t |> Enum.uniq() |> Enum.sort()
      targets = Enum.map(t, fn label -> Enum.find_index(classes, &(&1 == label)) end)

      model =
        LogisticRegression.fit(
          to_tensor(x, columns),
          Nx.tensor(targets),
          Keyword.put(opts, :num_classes, length(classes))
        )

      fn x ->
        model
        |> LogisticRegression.predict(to_tensor(x, columns))
        |> Nx.to_flat_list()
        |> Enum.map(&Enum.at(classes, &1))
      end
    end
  end

  ## 実データでの実行

  @doc "分類器を訓練データで学習させてから、訓練データとテストデータの正解率を求める。"
  def score(trainer, split, columns) do
    predict = trainer.(split.x_train, split.t_train, columns)

    %{
      train: Chapter01.accuracy(predict.(split.x_train), split.t_train),
      test: Chapter01.accuracy(predict.(split.x_test), split.t_test)
    }
  end

  @doc "名前と分類器。表示する順に並べる。"
  def models do
    [
      {"決定木（深さ #{@shallow_depth}）", tree_trainer(@shallow_depth)},
      {"ロジスティック回帰", logistic_trainer()},
      {"ランダムフォレスト（#{@n_estimators} 本）", forest_trainer(@n_estimators, @max_features, nil, @seed)},
      {"ランダムフォレスト（#{@n_estimators} 本・深さ #{@shallow_depth}）",
       forest_trainer(@n_estimators, @max_features, @shallow_depth, @seed)},
      {"Scholar ロジスティック回帰", scholar_logistic_trainer()}
    ]
  end

  @doc "モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。"
  def run do
    split = Chapter02.prepare_iris(Path.join(Dataset.dir(), "iris.csv"), 0.3, @seed)
    columns = Chapter03.feature_columns()

    IO.puts("モデル\t訓練データ\tテストデータ")

    for {label, trainer} <- models() do
      scores = score(trainer, split, columns)
      IO.puts("#{label}\t#{format_score(scores.train)}\t#{format_score(scores.test)}")
    end

    forest =
      forest_fit(split.x_train, split.t_train, columns, @n_estimators, @max_features, nil, @seed)

    importances = forest_importances(forest, split.x_train, split.t_train, columns)

    IO.puts("")
    IO.puts("ランダムフォレスト（#{@n_estimators} 本）の特徴量の重要度:")

    for column <- columns do
      IO.puts("#{column}\t#{format_score(importances[column])}")
    end
  end

  # 1 エポックぶん進める。損失は新しいものを先頭に積み、最後に並びを戻す。
  defp descend(_rows, _targets, weights, bias, _rate, _n, 0, losses),
    do: {weights, bias, losses}

  defp descend(rows, targets, weights, bias, rate, n, epochs, losses) do
    probabilities = Enum.map(rows, &softmax(row_scores(&1, weights, bias)))
    loss = cross_entropy(probabilities, targets)

    errors = Enum.zip_with(probabilities, targets, &error/2)
    weights = update_weights(weights, rows, errors, rate, n)
    bias = update_bias(bias, errors, rate, n)

    descend(rows, targets, weights, bias, rate, n, epochs - 1, [loss | losses])
  end

  # 誤差は「確率 − 正解」。正解の品種だけ 1 を引く。
  defp error(probabilities, target) do
    Enum.with_index(probabilities, fn value, i -> if i == target, do: value - 1.0, else: value end)
  end

  # 1 行のスコア（品種ごと）。特徴量を外、品種を内にして足し込む。
  defp row_scores(row, weights, bias) do
    row
    |> Enum.zip(weights)
    |> Enum.reduce(bias, fn {value, for_feature}, acc ->
      Enum.zip_with(acc, for_feature, fn sum, weight -> sum + value * weight end)
    end)
  end

  defp update_weights(weights, rows, errors, rate, n) do
    zero = Enum.map(weights, fn for_feature -> Enum.map(for_feature, fn _ -> 0.0 end) end)

    gradient =
      rows
      |> Enum.zip(errors)
      |> Enum.reduce(zero, fn {row, error}, acc -> add_outer_product(acc, row, error) end)

    Enum.zip_with(weights, gradient, fn for_feature, gradients ->
      Enum.zip_with(for_feature, gradients, fn weight, g -> weight - rate * g / n end)
    end)
  end

  # acc[特徴量][品種] に「その行の特徴量 × 誤差」を足す。
  defp add_outer_product(acc, row, error) do
    Enum.zip_with(acc, row, fn for_feature, value ->
      Enum.zip_with(for_feature, error, fn sum, e -> sum + value * e end)
    end)
  end

  defp update_bias(bias, errors, rate, n) do
    gradient =
      Enum.reduce(errors, Enum.map(bias, fn _ -> 0.0 end), fn error, acc ->
        Enum.zip_with(acc, error, fn sum, e -> sum + e end)
      end)

    Enum.zip_with(bias, gradient, fn b, g -> b - rate * g / n end)
  end

  defp argmax(values) do
    values
    |> Enum.with_index()
    |> Enum.reduce(fn {value, i}, {best, best_i} ->
      if value > best, do: {value, i}, else: {best, best_i}
    end)
    |> elem(1)
  end

  # 渡された乱数の状態で Fisher-Yates の並べ替えをする。第 2 章の shuffle/2 と
  # 同じ手順だが、シードではなく状態を受け取って続きから引ける。
  defp shuffle_with_state(items, state) do
    array = List.to_tuple(items)
    last = tuple_size(array) - 1

    {shuffled, state} =
      if last < 1 do
        {array, state}
      else
        Enum.reduce(last..1//-1, {array, state}, fn i, {acc, state} ->
          {j, next} = Random.next_int(state, i + 1)
          {swap(acc, i, j), next}
        end)
      end

    {Tuple.to_list(shuffled), state}
  end

  defp swap(tuple, i, j) do
    at_i = elem(tuple, i)
    at_j = elem(tuple, j)

    tuple |> put_elem(i, at_j) |> put_elem(j, at_i)
  end

  # 分割ごとに減った不純度（件数で重み付け）を、{列, 減った量} の並びにする。
  # 第 3 章で決めたとおり、葉は :label、節は :split を持つ。網羅性は検査されない。
  defp impurity_decreases(%{label: _}, _x, _t), do: []

  defp impurity_decreases(%{split: split, left: left, right: right}, x, t) do
    {to_left, to_right} =
      x
      |> Enum.zip(t)
      |> Enum.split_with(fn {features, _} ->
        Map.fetch!(features, split.feature) <= split.threshold
      end)

    [{split.feature, length(t) * (Chapter03.gini(t) - split.impurity)}] ++
      impurity_decreases(left, Enum.map(to_left, &elem(&1, 0)), Enum.map(to_left, &elem(&1, 1))) ++
      impurity_decreases(
        right,
        Enum.map(to_right, &elem(&1, 0)),
        Enum.map(to_right, &elem(&1, 1))
      )
  end

  defp normalize(totals) do
    total = totals |> Map.values() |> Enum.sum()

    if total == 0.0 do
      totals
    else
      Map.new(totals, fn {feature, value} -> {feature, value / total} end)
    end
  end

  defp to_tensor(x, columns) do
    Nx.tensor(Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end),
      type: :f64
    )
  end

  defp format_score(value), do: ~c"~.4f" |> :io_lib.format([value]) |> to_string()
end
