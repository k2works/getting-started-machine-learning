defmodule GettingStartedMl.Chapter03Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter03, as: C

  defp x, do: [%{値: 1.0}, %{値: 2.0}, %{値: 3.0}, %{値: 4.0}]
  defp t, do: ["a", "a", "b", "b"]
  defp columns, do: [:値]

  describe "ジニ不純度" do
    test "ラベルが一種類なら零になる" do
      assert C.gini(["a", "a", "a"]) == 0.0
    end

    test "二種類が半々なら零点五になる" do
      assert C.gini(["a", "a", "b", "b"]) == 0.5
    end

    test "空なら零になる" do
      assert C.gini([]) == 0.0
    end
  end

  describe "最頻値" do
    test "いちばん多いラベルを返す" do
      assert C.majority(["a", "b", "b"]) == "b"
    end

    test "同数なら先に現れたほうを選ぶ" do
      assert C.majority(["b", "a"]) == "b"
      assert C.majority(["a", "b"]) == "a"
    end
  end

  describe "分割" do
    test "分けられる境界を見つける" do
      split = C.best_split(x(), t(), columns())
      assert split.feature == :値
      assert split.threshold == 2.5
      assert split.impurity == 0.0
    end

    test "ラベルが一種類なら分けない" do
      assert C.best_split(x(), ["a", "a", "a", "a"], columns()) == nil
    end

    test "データが無ければ分けない" do
      assert C.best_split([], [], columns()) == nil
    end

    test "同じ値ばかりなら分けない" do
      assert C.best_split([%{値: 1.0}, %{値: 1.0}], ["a", "b"], columns()) == nil
    end
  end

  describe "木を作る" do
    test "深さ一で葉と節ができる" do
      tree = C.fit(x(), t(), columns(), 1)
      assert C.node?(tree)
      assert C.leaf?(tree.left)
      assert C.leaf?(tree.right)
      assert tree.left.label == "a"
      assert tree.right.label == "b"
    end

    test "深さ零なら葉だけになる" do
      tree = C.fit(x(), t(), columns(), 0)
      assert C.leaf?(tree)
      assert tree.label == "a"
    end

    test "深さの上限が無ければ分けられるだけ分ける" do
      tree = C.fit(x(), t(), columns(), nil)
      assert C.predict(tree, x()) == t()
    end
  end

  describe "予測" do
    test "木をたどって予測する" do
      tree = C.fit(x(), t(), columns(), nil)
      assert C.predict_one(tree, %{値: 1.5}) == "a"
      assert C.predict_one(tree, %{値: 3.5}) == "b"
    end

    test "境界そのものは左へ進む" do
      tree = C.fit(x(), t(), columns(), 1)
      assert C.predict_one(tree, %{値: 2.5}) == "a"
    end
  end

  describe "木の表示" do
    test "字下げ付きの文字列にする" do
      tree = C.fit(x(), t(), columns(), 1)

      assert C.format_tree(tree) == """
             値 <= 2.5000
               a
             値 > 2.5000
               b
             """
    end
  end

  describe "実データ" do
    @tag :data
    test "深さごとの正解率がほかの言語版と一致する" do
      split =
        GettingStartedMl.Chapter02.prepare_iris(
          Path.join(GettingStartedMl.Dataset.dir(), "iris.csv"),
          0.3,
          0
        )

      tree = C.fit(split.x_train, split.t_train, C.feature_columns(), 2)

      assert_in_delta GettingStartedMl.Chapter01.accuracy(
                        C.predict(tree, split.x_test),
                        split.t_test
                      ),
                      0.9556,
                      0.0001
    end
  end
end
