defmodule GettingStartedMl.Chapter11 do
  @moduledoc """
  第 11 章: 評価指標と交差検証。

  混同行列・適合率・再現率・F 値・ROC 曲線・K 分割交差検証を自作し、
  `Scholar.Metrics.Classification`・`Scholar.Metrics.Regression`・
  `Scholar.ModelSelection` と突き合わせる。

  混同行列は `%{tp: .., fp: .., fn: .., tn: ..}` のマップで表す。`fn` はアトムのキーとしては
  書けるが、`cm.fn` とは書けない（`fn` は予約語なので `invalid alias` になる）。
  読むときはパターンマッチで別の名前に束縛する。

  評価関数は「正解と予測を受け取って 1 つの数を返す関数」、分類器は「訓練データを受け取って
  予測する関数を返す関数」。どちらもただの関数なので、ビヘイビアもプロトコルも要らない。
  """

  alias GettingStartedMl.{Chapter02, Chapter03, Chapter07, Dataset, Random}
  alias Scholar.Linear.LinearRegression
  alias Scholar.Metrics.Classification
  alias Scholar.Metrics.Regression
  alias Scholar.ModelSelection

  @survived_columns [:Pclass, :Age, :male]
  @survived "1"
  @tree_depth 2
  @n_splits 5
  @seed 0

  @doc "Survived.csv の特徴量の列。"
  def survived_columns, do: @survived_columns

  @doc "正例にするラベル（生存）。"
  def survived, do: @survived

  @doc "分割の数。"
  def n_splits, do: @n_splits

  @doc "分割の乱数のシード。"
  def seed, do: @seed

  ## 件数の検査

  @doc "正解と予測の件数が同じでなければ失敗する。短いほうに合わせて黙って切り詰めない。"
  def require_same_size(actual, predicted) do
    if length(actual) != length(predicted) do
      raise ArgumentError,
            "正解と予測の件数が違います（正解 #{length(actual)} 件、予測 #{length(predicted)} 件）"
    end

    :ok
  end

  ## 混同行列

  @doc "正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。"
  def confusion_matrix(actual, predicted, positive) do
    require_same_size(actual, predicted)

    counts =
      actual
      |> Enum.zip(predicted)
      |> Enum.map(fn {a, p} -> cell(a == positive, p == positive) end)
      |> Enum.frequencies()

    Map.merge(%{tp: 0, fp: 0, fn: 0, tn: 0}, counts)
  end

  ## 混同行列から求める指標

  @doc "分母が 0 なら 0 を返す割り算。NaN にしない。"
  def ratio(_numerator, 0), do: 0.0
  def ratio(_numerator, +0.0), do: 0.0
  def ratio(numerator, denominator), do: numerator / denominator

  @doc "適合率。正例と予測したうち、本当に正例だった割合。"
  def precision(%{tp: tp, fp: fp}), do: ratio(tp, tp + fp)

  @doc "再現率。本当の正例のうち、正例と予測できた割合。"
  def recall(%{tp: tp, fn: misses}), do: ratio(tp, tp + misses)

  @doc "F 値。適合率と再現率の調和平均。"
  def f1_score(cm) do
    p = precision(cm)
    r = recall(cm)
    ratio(2 * p * r, p + r)
  end

  @doc "混同行列から求める指標を、正例を決めて、正解と予測から採点する評価関数に変える。"
  def classification_metric(score, positive) do
    fn actual, predicted -> score.(confusion_matrix(actual, predicted, positive)) end
  end

  ## 正解と予測から直接求める指標

  @doc "正解率。正解と予測が一致した割合。"
  def accuracy(actual, predicted) do
    require_same_size(actual, predicted)
    ratio(Enum.count(Enum.zip(actual, predicted), fn {a, p} -> a == p end), length(actual))
  end

  @doc "平均二乗誤差（MSE）。誤差の 2 乗の平均。"
  def mean_squared_error(actual, predicted) do
    require_same_size(actual, predicted)

    actual
    |> Enum.zip_with(predicted, fn a, p -> (p - a) * (p - a) end)
    |> Enum.sum()
    |> Kernel./(length(actual))
  end

  @doc "平均。1 つも無ければ失敗する。"
  def mean(values) do
    list = Enum.to_list(values)

    if list == [] do
      raise ArgumentError, "平均を求める値が 1 つもありません"
    end

    Enum.sum(list) / length(list)
  end

  ## ROC 曲線と AUC

  @doc """
  スコア（正例らしさ）と正解（正例なら `true`）から ROC 曲線を求める。

  スコアの高いほうから閾値を下げていき、同じスコアは 1 つの点にまとめる。
  先頭には「どれも正例と予測しない」点（偽陽性率も真陽性率も 0）を置く。
  """
  def roc_curve(scores, labels) do
    require_same_size(scores, labels)
    positives = Enum.count(labels, & &1)
    negatives = length(labels) - positives

    if positives == 0 or negatives == 0 do
      raise ArgumentError, "正例と負例が両方ないと ROC 曲線を描けません"
    end

    [roc_point(:infinity, 0.0, 0.0) | roc_points(scores, labels, positives, negatives)]
  end

  @doc "ROC 曲線の下の面積（AUC）を台形則で求める。"
  def auc(curve) do
    curve
    |> Enum.chunk_every(2, 1, :discard)
    |> Enum.map(fn [left, right] ->
      (right.false_positive_rate - left.false_positive_rate) *
        (left.true_positive_rate + right.true_positive_rate) / 2.0
    end)
    |> Enum.sum()
  end

  ## K 分割交差検証

  @doc """
  シード付きの乱数で行の位置を並べ替え、`n_splits` 個のテストデータに分ける。

  件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。
  """
  def k_fold(n_samples, n_splits, seed) do
    check_splits(n_samples, n_splits)
    folds(Random.shuffle(Enum.to_list(0..(n_samples - 1)), seed), n_splits)
  end

  @doc "並べ替えずに、先頭から順に `n_splits` 個のかたまりに分ける。余りの配り方は `k_fold/3` と同じ。"
  def k_fold_sequential(n_samples, n_splits) do
    check_splits(n_samples, n_splits)
    folds(Enum.to_list(0..(n_samples - 1)), n_splits)
  end

  @doc "行の位置で値を選ぶ。"
  def pick(values, positions) do
    array = List.to_tuple(values)
    Enum.map(positions, &elem(array, &1))
  end

  @doc """
  分割ごとに分類器を訓練データで学習し、テストデータの予測を評価関数で採点する。

  `Stream.map/2` を返すので、取り出した分だけ学習する。Elixir のストリームは
  1 件ずつ実現するので、1 つ取り出せば 1 回だけ学習する。
  """
  def cross_validate(trainer, x, t, folds, metric) do
    Stream.map(folds, fn %{train: train, test: test} ->
      predict = trainer.(pick(x, train), pick(t, train))
      metric.(pick(t, test), predict.(pick(x, test)))
    end)
  end

  ## 分類器

  @doc "第 3 章の決定木の分類器。"
  def tree_trainer(columns, max_depth) do
    fn x, t ->
      tree = Chapter03.fit(x, t, columns, max_depth)
      fn features -> Chapter03.predict(tree, features) end
    end
  end

  @doc "第 7 章の線形回帰の分類器（回帰なので予測は数値）。"
  def linear_trainer(columns) do
    fn x, t ->
      model = Chapter07.fit(x, t, columns)
      fn features -> Chapter07.predict(model, features) end
    end
  end

  ## Scholar との突き合わせ

  @doc "ラベルを、正例なら 1、そうでなければ 0 の整数にする。"
  def label_codes(labels, positive) do
    Enum.map(labels, fn label -> if label == positive, do: 1, else: 0 end)
  end

  @doc """
  `Scholar.Metrics.Classification` で求めた正解率・適合率・再現率・F 値。

  **どれも f32 で返る。** ラベルは整数のテンソルなので、Scholar が
  `to_float_type/1` で決める計算の型が f32 になる。f64 を渡す口は無い。
  """
  def scholar_scores(actual, predicted, positive) do
    y_true = Nx.tensor(label_codes(actual, positive), type: :u32)
    y_pred = Nx.tensor(label_codes(predicted, positive), type: :u32)

    %{
      accuracy: Nx.to_number(Classification.accuracy(y_true, y_pred)),
      precision: Nx.to_number(Classification.binary_precision(y_true, y_pred)),
      recall: Nx.to_number(Classification.binary_recall(y_true, y_pred)),
      f1_score: Nx.to_number(Classification.f1_score(y_true, y_pred, num_classes: 2)[1])
    }
  end

  @doc "`Scholar.Metrics.Classification.confusion_matrix/3` の 2 行 2 列。行が正解、列が予測で、負例が先。"
  def scholar_confusion_matrix(actual, predicted, positive) do
    Classification.confusion_matrix(
      Nx.tensor(label_codes(actual, positive), type: :u32),
      Nx.tensor(label_codes(predicted, positive), type: :u32),
      num_classes: 2
    )
    |> Nx.to_flat_list()
  end

  @doc """
  `Scholar.Metrics.Classification.roc_auc_score/4` で求めた AUC。

  正解を f64 のテンソルにすると f64 で返る。整数にすると f32 に落ちるので、
  自作と小数 7 桁より細かく比べられない。
  """
  def scholar_auc(scores, labels) do
    y_true = Nx.tensor(Enum.map(labels, fn ok -> if ok, do: 1.0, else: 0.0 end), type: :f64)
    y_score = Nx.tensor(scores, type: :f64)

    Nx.to_number(
      Classification.roc_auc_score(
        y_true,
        y_score,
        Classification.distinct_value_indices(y_score)
      )
    )
  end

  @doc "`Scholar.Metrics.Regression.mean_square_error/3` で求めた MSE。f64 のまま返る。"
  def scholar_mean_squared_error(actual, predicted) do
    Nx.to_number(
      Regression.mean_square_error(
        Nx.tensor(actual, type: :f64),
        Nx.tensor(predicted, type: :f64)
      )
    )
  end

  @doc """
  `Scholar.ModelSelection.k_fold_split/2` が作る、分割ごとのテストデータの件数。

  Scholar は `floor(件数 / 分割数)` をすべての分割の大きさにするので、
  **余りの行はどの分割のテストデータにも入らない**。自作の `k_fold/3` とはここが違う。
  """
  def scholar_k_fold_test_sizes(n_samples, n_splits) do
    Nx.iota({n_samples, 1}, type: :f64)
    |> ModelSelection.k_fold_split(n_splits)
    |> Enum.map(fn {_train, test} -> Nx.axis_size(test, 0) end)
  end

  @doc "`Scholar.ModelSelection.cross_validate/4` で、分割ごとの MSE を求める。並べ替えはしない。"
  def scholar_cross_validate_mse(x, t, columns, n_splits) do
    folding = fn tensor -> ModelSelection.k_fold_split(tensor, n_splits) end

    ModelSelection.cross_validate(
      Nx.tensor(feature_matrix(x, columns), type: :f64),
      Nx.tensor(t, type: :f64),
      folding,
      &score_fold/2
    )
    |> Nx.to_flat_list()
  end

  ## 実データの前処理

  @doc """
  客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。

  この章では分割の前に全体の平均値で年齢の欠損値を補う、簡略化した前処理を使う。
  """
  def prepare_survived(table) do
    age_mean = Map.fetch!(Chapter02.column_means(table.rows, [:Age]), :Age)

    x =
      Enum.map(table.rows, fn row ->
        %{
          Pclass: Chapter02.number(row, :Pclass),
          Age: Chapter02.number(row, :Age) || age_mean,
          male: if(Chapter02.text(row, :Sex) == "male", do: 1.0, else: 0.0)
        }
      end)

    %{x: x, t: Enum.map(table.rows, &Chapter02.text(&1, :Survived))}
  end

  @doc "第 7 章の 4 列を特徴量に、興行収入を正解にする。特徴量の欠損値は列ごとの平均値で補う。"
  def prepare_cinema(table) do
    columns = Chapter07.feature_columns()
    means = Chapter02.column_means(table.rows, columns)

    %{
      x: Chapter02.fill_missing(table.rows, columns, means),
      t: Enum.map(table.rows, &Chapter02.number(&1, Chapter07.target()))
    }
  end

  ## 実データでの実行

  @doc "Survived の評価指標。表示する順に並べる。マップはキーの順を保たないのでリストで持つ。"
  def survived_metrics do
    [
      {"正解率", &accuracy/2},
      {"適合率", classification_metric(&precision/1, @survived)},
      {"再現率", classification_metric(&recall/1, @survived)},
      {"F値", classification_metric(&f1_score/1, @survived)}
    ]
  end

  @doc "cinema の評価指標。第 7 章の RMSE・MAE をそのまま渡す。"
  def cinema_metrics do
    [
      {"RMSE", &Chapter07.root_mean_squared_error/2},
      {"MAE", &Chapter07.mean_absolute_error/2}
    ]
  end

  @doc "同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。名前と平均の組を、指標の順に返す。"
  def evaluate(trainer, %{x: x, t: t}, metrics) do
    folds = k_fold(length(x), @n_splits, @seed)

    Enum.map(metrics, fn {name, metric} ->
      {name, mean(cross_validate(trainer, x, t, folds, metric))}
    end)
  end

  @doc "Survived.csv を深さ 2 の決定木で評価する。"
  def evaluate_survived(path) do
    evaluate(
      tree_trainer(@survived_columns, @tree_depth),
      prepare_survived(Chapter02.load_table(path)),
      survived_metrics()
    )
  end

  @doc "cinema.csv を線形回帰で評価する。"
  def evaluate_cinema(path) do
    evaluate(
      linear_trainer(Chapter07.feature_columns()),
      prepare_cinema(Chapter02.load_table(path)),
      cinema_metrics()
    )
  end

  @doc "Survived と cinema を K 分割交差検証で評価し、指標ごとの平均と Scholar との比較を表示する。"
  def run do
    dir = Dataset.dir()
    IO.puts("Survived（決定木、#{@n_splits} 分割交差検証の平均）")
    print_scores(evaluate_survived(Path.join(dir, "Survived.csv")), 4)
    IO.puts("cinema（線形回帰、#{@n_splits} 分割交差検証の平均）")
    print_scores(evaluate_cinema(Path.join(dir, "cinema.csv")), 2)
    IO.puts("Scholar の k_fold_split のテストデータの件数（#{@n_splits} 分割）")
    print_split_sizes(Path.join(dir, "Survived.csv"))
  end

  defp print_split_sizes(path) do
    n = length(Chapter02.load_table(path).rows)
    mine = Enum.map(k_fold(n, @n_splits, @seed), &length(&1.test))
    theirs = scholar_k_fold_test_sizes(n, @n_splits)
    IO.puts("  #{n} 件を自作: #{Enum.join(mine, ", ")}")
    IO.puts("  #{n} 件を Scholar: #{Enum.join(theirs, ", ")}")
  end

  defp print_scores(scores, digits) do
    Enum.each(scores, fn {name, score} -> IO.puts("  #{name}: #{format(score, digits)}") end)
  end

  defp score_fold({x_train, x_test}, {t_train, t_test}) do
    model = LinearRegression.fit(x_train, t_train)
    [Regression.mean_square_error(t_test, LinearRegression.predict(model, x_test))]
  end

  defp feature_matrix(x, columns) do
    Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end)
  end

  defp cell(true, true), do: :tp
  defp cell(false, true), do: :fp
  defp cell(true, false), do: :fn
  defp cell(false, false), do: :tn

  defp roc_point(threshold, false_positive_rate, true_positive_rate) do
    %{
      threshold: threshold,
      false_positive_rate: false_positive_rate,
      true_positive_rate: true_positive_rate
    }
  end

  # スコアの高いほうから、同じスコアのかたまりごとに正例と負例の数を足し込む。
  defp roc_points(scores, labels, positives, negatives) do
    scores
    |> Enum.zip(labels)
    |> Enum.group_by(&elem(&1, 0), &elem(&1, 1))
    |> Enum.sort_by(&elem(&1, 0), :desc)
    |> Enum.scan({nil, 0, 0}, fn {threshold, group}, {_, tp, fp} ->
      {threshold, tp + Enum.count(group, & &1), fp + Enum.count(group, &(not &1))}
    end)
    |> Enum.map(fn {threshold, tp, fp} ->
      roc_point(threshold, fp / negatives, tp / positives)
    end)
  end

  defp check_splits(n_samples, n_splits) do
    if n_splits < 2 or n_splits > n_samples do
      raise ArgumentError, "分割の数は 2 以上 #{n_samples} 以下にしてください: #{n_splits}"
    end

    :ok
  end

  # 並べた位置を n_splits 個のかたまりに分け、かたまりごとに 1 つをテストデータ、残りを訓練データにする。
  defp folds(positions, n_splits) do
    total = length(positions)

    sizes =
      Enum.map(0..(n_splits - 1), fn index ->
        div(total, n_splits) + if(index < rem(total, n_splits), do: 1, else: 0)
      end)

    {tests, []} =
      Enum.map_reduce(sizes, positions, fn size, rest -> Enum.split(rest, size) end)

    Enum.map(tests, fn test -> %{train: positions -- test, test: test} end)
  end

  # :io_lib.format はロケールに依らないので、小数点は常に「.」になる。
  defp format(value, digits) do
    ~c"~.#{digits}f" |> :io_lib.format([value]) |> to_string()
  end
end
