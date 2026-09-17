---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスによるロジスティック回帰と、第 3 章の決定木を再利用したランダムフォレスト・特徴量の重要度を TDD で自作し、Protocol で共通化したインターフェースで scikit-learn と突き合わせる。"
tags: [article,getting-start-ml,python]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T02:58:16Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが 3 種類になると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、`typing.Protocol` でモデルに共通するインターフェース（`fit` と `predict`）を定義し、どのモデルも同じ関数で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作し、scikit-learn と突き合わせます。

データは第 2 章・第 3 章と同じ iris を使います。outline では Survived も候補に挙げていましたが、Survived の前処理は第 8 章の主題であり、評価指標を詳しく扱う第 11 章でも使います。この章では前処理の違いに気を取られず、モデルの違いだけを比べられるように、前処理済みの iris（第 2 章の `prepare_iris`）に絞りました。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ関数で評価する
- [ ] 実データで scikit-learn と突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。scikit-learn の `LogisticRegression` も、多クラスの場合は既定でこの方式で学習するため、突き合わせにも向いています。

### 仮実装

```python
# test/chapter10/test_logistic_regression.py
import numpy as np
import pytest

from lib.chapter10.logistic_regression import softmax


class TestSoftmax:
    def test_値がすべて同じなら確率は均等になる(self) -> None:
        probabilities = softmax(np.array([[0.0, 0.0, 0.0, 0.0]]))

        assert probabilities.tolist() == [[0.25, 0.25, 0.25, 0.25]]
```

```text
E   ModuleNotFoundError: No module named 'lib.chapter10.logistic_regression'
```

均等な確率を返す仮実装で Green にします。

```python
# lib/chapter10/logistic_regression.py
import numpy as np
from numpy.typing import NDArray


def softmax(z: NDArray[np.float64]) -> NDArray[np.float64]:
    return np.full_like(z, 1 / z.shape[1])
```

`NDArray[np.float64]` は「64 ビット浮動小数点数の NumPy 配列」を表す型ヒントです。入力は 1 行が 1 サンプル、1 列が 1 品種の 2 次元配列です。

### 三角測量

値の差が `log 2` なら、確率の比は 2 倍になるはずです。

```python
    def test_値の差が指数の比になる(self) -> None:
        probabilities = softmax(np.array([[0.0, np.log(2.0)], [np.log(3.0), 0.0]]))

        assert probabilities == pytest.approx(
            np.array([[1 / 3, 2 / 3], [3 / 4, 1 / 4]])
        )
```

```text
E       assert array([[0.5, ...  [0.5, 0.5]]) == approx([[0.33...5 ± 2.5e-07]])
E         
E         comparison failed. Mismatched elements: 4 / 4:
E         Max absolute difference: 0.25
```

定義どおりに一般化します。`axis=1, keepdims=True` で、行ごとに合計を取り、形を保ったまま割り算できるようにします。

```python
def softmax(z: NDArray[np.float64]) -> NDArray[np.float64]:
    exp = np.exp(z)
    return exp / exp.sum(axis=1, keepdims=True)
```

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```python
    def test_大きな値でもあふれずに確率を求める(self) -> None:
        probabilities = softmax(np.array([[1000.0, 1000.0]]))

        assert probabilities.tolist() == [[0.5, 0.5]]
```

```text
E       assert [[nan, nan]] == [[0.5, 0.5]]
  lib\chapter10\logistic_regression.py:6: RuntimeWarning: overflow encountered in exp
  lib\chapter10\logistic_regression.py:7: RuntimeWarning: invalid value encountered in divide
```

`exp(1000)` は 64 ビット浮動小数点数で表せる範囲を超えて無限大になり、無限大 ÷ 無限大が `nan`（非数）になりました。ソフトマックスは、すべての値から同じ数を引いても結果が変わりません（分子と分母に同じ `exp(-c)` が掛かるため）。そこで各行の最大値を引いてから `exp` を計算します。

```python
def softmax(z: NDArray[np.float64]) -> NDArray[np.float64]:
    shifted = z - z.max(axis=1, keepdims=True)
    exp = np.exp(shifted)
    return exp / exp.sum(axis=1, keepdims=True)
```

```text
============================== 3 passed in 0.07s ==============================
```

数式として正しい実装と、コンピューターの数値として正しい実装は別物です。境界の値のテストが、その違いを見つけてくれました。

## 10.4 ロジスティック回帰

### 仮実装と三角測量

第 3 章の決定木と同じく `fit` と `predict` を持つクラスにします。1 種類のラベルだけを学習する例は、最初のラベルを返す仮実装で通ります。

```python
class TestLogisticRegression:
    def test_1種類のラベルだけを学習するとそのラベルを予測する(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2]})
        t = pd.Series(["setosa", "setosa"])

        model = LogisticRegression().fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.15, 0.9]})) == [
            "setosa",
            "setosa",
        ]
```

```python
class LogisticRegression:
    def __init__(self) -> None:
        self.label = ""

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "LogisticRegression":
        self.label = str(t.iloc[0])
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        return [self.label] * len(x)
```

2 種類のラベルを境界の左右で予測する例を加えると、仮実装では通りません。

```python
    def test_2種類のラベルを境界の左右で予測する(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.8, 0.9]})
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        model = LogisticRegression().fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.15, 0.85]})) == [
            "setosa",
            "virginica",
        ]
```

```text
E       AssertionError: assert ['setosa', 'setosa'] == ['setosa', 'virginica']
E         
E         At index 1 diff: 'setosa' != 'virginica'
```

### 勾配降下法で学習する

重みを少しずつ動かして、予測した確率を正解に近づけます。正解の品種を 1、それ以外を 0 とした表（one-hot 表現）を `targets` とすると、損失（交差エントロピー）を小さくする方向は「予測した確率 − 正解」から計算できます。

```python
class LogisticRegression:
    def __init__(self, learning_rate: float = 1.0, epochs: int = 5000) -> None:
        self.learning_rate = learning_rate
        self.epochs = epochs
        self.classes: list[str] = []
        self.weights = np.zeros((0, 0))
        self.bias = np.zeros(0)

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "LogisticRegression":
        features = x.to_numpy(dtype=float)
        self.classes = sorted({str(label) for label in t})
        labels = t.astype(str).to_list()
        targets = np.eye(len(self.classes))[[self.classes.index(v) for v in labels]]
        self.weights = np.zeros((features.shape[1], len(self.classes)))
        self.bias = np.zeros(len(self.classes))
        for _ in range(self.epochs):
            probabilities = softmax(features @ self.weights + self.bias)
            gradient = (probabilities - targets) / len(features)
            self.weights -= self.learning_rate * features.T @ gradient
            self.bias -= self.learning_rate * gradient.sum(axis=0)
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        scores = x.to_numpy(dtype=float) @ self.weights + self.bias
        return [self.classes[i] for i in scores.argmax(axis=1)]
```

- `np.eye(品種数)[番号のリスト]` は、単位行列から行を取り出して one-hot 表現を作る NumPy の書き方です
- `@` は行列の積です。`features @ self.weights` で、全サンプル × 全品種のスコアを一度に計算します。第 3 章の決定木は 1 行ずつ `for` で処理しましたが、ロジスティック回帰は行列の計算だけで書けます
- `predict` では確率を計算せず、スコアが最大の品種を選びます。ソフトマックスは大小関係を変えないので、結果は同じです

```text
============================== 5 passed in 0.47s ==============================
```

### 損失の記録と、学習前の予測

3 品種・2 特徴量の例と、損失が下がることと、学習前の予測がエラーになることを確かめます。

```python
    def test_3種類のラベルを2つの特徴量から予測する(self) -> None:
        x = pd.DataFrame(
            {
                "花弁長さ": [0.1, 0.2, 0.5, 0.6, 0.5, 0.6],
                "花弁幅": [0.1, 0.2, 0.1, 0.2, 0.8, 0.9],
            }
        )
        t = pd.Series(
            ["setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica"]
        )

        model = LogisticRegression().fit(x, t)

        assert model.predict(x) == t.to_list()

    def test_学習を繰り返すと損失が小さくなる(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.8, 0.9]})
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        model = LogisticRegression(epochs=100).fit(x, t)

        assert len(model.losses) == 100
        assert model.losses[-1] < model.losses[0]

    def test_学習する前に予測するとエラーになる(self) -> None:
        with pytest.raises(ValueError, match="fit で学習してから"):
            LogisticRegression().predict(pd.DataFrame({"花弁幅": [0.1]}))
```

3 品種の例は今の実装のまま通り、残りの 2 つが失敗しました。

```text
test/chapter10/test_logistic_regression.py::TestLogisticRegression::test_3種類のラベルを2つの特徴量から予測する PASSED [ 75%]
test/chapter10/test_logistic_regression.py::TestLogisticRegression::test_学習を繰り返すと損失が小さくなる FAILED [ 87%]
test/chapter10/test_logistic_regression.py::TestLogisticRegression::test_学習する前に予測するとエラーになる FAILED [100%]
E       AttributeError: 'LogisticRegression' object has no attribute 'losses'. Did you mean: 'classes'?
E       AssertionError: Regex pattern did not match.
E         Expected regex: 'fit で学習してから'
```

学習前の予測は、行列の形が合わないという NumPy のエラーになっていました。

```text
E         Actual message: 'matmul: Input operand 1 has a mismatch in its core dimension 0, with gufunc signature (n?,k),(k,m?)->(n?,m?) (size 0 is different from 1)'
```

エラーは起きていますが、読み手には原因が分かりません。第 3 章の決定木と同じメッセージを出すようにし、損失を繰り返しごとに記録します。

```python
EPSILON = 1e-12


def cross_entropy(
    probabilities: NDArray[np.float64], targets: NDArray[np.float64]
) -> float:
    return float(-np.mean(np.sum(targets * np.log(probabilities + EPSILON), axis=1)))
```

`EPSILON` は、確率が 0 のときに `log(0)` が負の無限大にならないように足す小さな値です。

```text
============================== 8 passed in 0.65s ==============================
```

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を **変更せずに** 再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。scikit-learn の `RandomForestClassifier` は「分割ごと」に特徴量を選び直すので、仕組みは少し異なります。分割ごとに選ぶには決定木の内部に手を入れる必要があるため、この章では木ごとの方式にとどめました。

### 多数決

```python
# test/chapter10/test_random_forest.py
class TestMajorityVote:
    def test_サンプルごとに最も多い予測を選ぶ(self) -> None:
        votes = [
            ["setosa", "virginica"],
            ["setosa", "virginica"],
            ["versicolor", "setosa"],
        ]

        assert majority_vote(votes) == ["setosa", "virginica"]
```

`votes` は「木ごとの予測のリスト」です。サンプルごとの多数決に組み替えるだけなので、明白な実装で書きます。

```python
def majority_vote(votes: list[list[str]]) -> list[str]:
    return [Counter(sample).most_common(1)[0][0] for sample in zip(*votes, strict=True)]
```

`zip(*votes)` は、木ごとのリストをサンプルごとの組に並べ替えます（行と列の入れ替え）。

### ブートストラップ標本

```python
class TestBootstrapSample:
    def test_元のデータと同じ件数の行番号を重複を許して選ぶ(self) -> None:
        rows = bootstrap_sample(100, np.random.default_rng(0))

        assert len(rows) == 100
        assert all(0 <= row < 100 for row in rows)
        assert len(set(rows.tolist())) < 100

    def test_同じシードなら同じ行を選ぶ(self) -> None:
        first = bootstrap_sample(10, np.random.default_rng(42))
        second = bootstrap_sample(10, np.random.default_rng(42))

        assert first.tolist() == second.tolist()
```

```python
def bootstrap_sample(size: int, rng: np.random.Generator) -> NDArray[np.int64]:
    return rng.integers(0, size, size=size)
```

乱数生成器を引数で受け取るのは、森全体で 1 つの生成器を使い回し、シード 1 つで全部の木の乱数を再現できるようにするためです。

最初は行番号を `list[int]` で返していましたが、mypy が `x.iloc[rows]` の行で型エラーを出しました。pandas-stubs は `iloc` に `list[int]` を渡すことを想定しておらず、整数の NumPy 配列なら受け付けます。そこで NumPy 配列のまま返すように変え、テストも `tolist()` で比べる形にしました。

### 森を作る

```python
def two_species() -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame(
        {
            "がく片幅": [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3],
            "花弁幅": [0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88],
        }
    )
    t = pd.Series(["setosa"] * 5 + ["virginica"] * 5)
    return x, t


class TestRandomForest:
    def test_指定した数だけ第3章の決定木を学習する(self) -> None:
        x, t = two_species()

        model = RandomForest(n_estimators=5, max_features=1, seed=0).fit(x, t)

        assert len(model.trees) == 5
        assert all(isinstance(tree, DecisionTree) for _, tree in model.trees)

    def test_各決定木は指定した数の特徴量だけを使う(self) -> None:
        x, t = two_species()

        model = RandomForest(n_estimators=5, max_features=1, seed=0).fit(x, t)

        assert all(len(columns) == 1 for columns, _ in model.trees)

    def test_決定木の多数決で予測する(self) -> None:
        x, t = two_species()

        model = RandomForest(n_estimators=25, max_features=2, seed=0).fit(x, t)

        assert model.predict(
            pd.DataFrame({"がく片幅": [0.4, 0.4], "花弁幅": [0.13, 0.83]})
        ) == [
            "setosa",
            "virginica",
        ]

    def test_同じシードなら同じ予測になる(self) -> None:
        x, t = two_species()

        first = RandomForest(n_estimators=5, max_features=1, seed=7).fit(x, t)
        second = RandomForest(n_estimators=5, max_features=1, seed=7).fit(x, t)

        assert [columns for columns, _ in first.trees] == [
            columns for columns, _ in second.trees
        ]
        assert first.predict(x) == second.predict(x)

    def test_学習する前に予測するとエラーになる(self) -> None:
        with pytest.raises(ValueError, match="fit で学習してから"):
            RandomForest().predict(pd.DataFrame({"花弁幅": [0.1]}))
```

```text
E   ImportError: cannot import name 'RandomForest' from 'lib.chapter10.random_forest'
```

部品（多数決・ブートストラップ標本・第 3 章の決定木）がそろっているので、組み立てるだけの明白な実装で進めます。

```python
class RandomForest:
    def __init__(
        self,
        n_estimators: int = 10,
        max_features: int = 2,
        max_depth: int | None = None,
        seed: int = 0,
    ) -> None:
        self.n_estimators = n_estimators
        self.max_features = max_features
        self.max_depth = max_depth
        self.seed = seed
        self.trees: list[tuple[list[str], DecisionTree]] = []
        self.bootstrap_rows: list[NDArray[np.int64]] = []

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "RandomForest":
        rng = np.random.default_rng(self.seed)
        self.trees = []
        self.bootstrap_rows = []
        for _ in range(self.n_estimators):
            rows = bootstrap_sample(len(x), rng)
            self.bootstrap_rows.append(rows)
            chosen = rng.choice(len(x.columns), size=self.max_features, replace=False)
            columns = [str(x.columns[i]) for i in sorted(chosen)]
            tree = DecisionTree(max_depth=self.max_depth)
            tree.fit(x.iloc[rows][columns], t.iloc[rows])
            self.trees.append((columns, tree))
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        if not self.trees:
            raise ValueError("fit で学習してから predict を呼んでください")
        return majority_vote([tree.predict(x[columns]) for columns, tree in self.trees])
```

- 木ごとに「使った列」と「学習した決定木」の組を `trees` に記録します。予測のときは、その木が学習した列だけを渡します
- `rng.choice(列数, size=..., replace=False)` で、重複なしに列を選びます。`sorted` で元の列の順に戻しておくと、同じ組み合わせが同じ並びになります
- ブートストラップ標本の行番号は、特徴量の重要度を計算するときに使うので `bootstrap_rows` にも残します（10.6 節）

```text
============================= 16 passed in 0.70s ==============================
```

第 3 章の `DecisionTree` には一切手を入れていません。`fit` と `predict` という小さなインターフェースで作ってあったので、部品としてそのまま組み込めました。

## 10.6 特徴量の重要度

### 計算方法

第 3 章の Notebook では scikit-learn の `feature_importances_` を表示しました。これを自作します。決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split.impurity` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。

ただし、第 3 章の `Node` は、その節に届いた件数を持っていません。そこで、学習に使ったデータをもう一度木に流して、節ごとに件数とジニ不純度を求めます。

### 決定木 1 本の重要度

```python
# test/chapter10/test_feature_importance.py
class TestTreeImportances:
    def test_分割しない木はすべての特徴量の重要度が0(self) -> None:
        x = pd.DataFrame({"がく片幅": [0.3, 0.5], "花弁幅": [0.1, 0.2]})
        t = pd.Series(["setosa", "setosa"])

        assert tree_importances(Leaf(label="setosa"), x, t) == {
            "がく片幅": 0.0,
            "花弁幅": 0.0,
        }
```

すべて 0 を返す仮実装で通ります。次に、花弁幅だけで分割する木と、2 つの特徴量で 2 回分割する木を例にします。

```python
    def test_1回だけ分割する木は分割に使った特徴量の重要度が1(self) -> None:
        x = pd.DataFrame(
            {"がく片幅": [0.3, 0.5, 0.4, 0.6], "花弁幅": [0.1, 0.2, 0.8, 0.9]}
        )
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])
        model = DecisionTree().fit(x, t)
        assert model.tree is not None

        assert tree_importances(model.tree, x, t) == {"がく片幅": 0.0, "花弁幅": 1.0}

    def test_分割で減った不純度を件数で重み付けして割合にする(self) -> None:
        x = pd.DataFrame(
            {
                "花弁長さ": [0.1, 0.2, 0.3, 0.8, 0.7, 0.9],
                "花弁幅": [0.1, 0.1, 0.1, 0.2, 0.9, 0.9],
            }
        )
        t = pd.Series(
            ["setosa", "setosa", "setosa", "versicolor", "virginica", "virginica"]
        )
        model = DecisionTree().fit(x, t)
        assert model.tree is not None

        importances = tree_importances(model.tree, x, t)

        assert importances == pytest.approx({"花弁長さ": 7 / 11, "花弁幅": 4 / 11})
```

2 つ目の例の期待値は、手で計算して決めました。

| 節 | 件数 | 節のジニ不純度 | 分割 | 分割後のジニ不純度 | 減少量 |
|----|------|--------------|------|-----------------|--------|
| 根 | 6 | 1 − (9 + 1 + 4) / 36 = 11/18 | 花弁長さ ≤ 0.5（setosa 3 件と残り 3 件） | 3/6 × 0 + 3/6 × 4/9 = 2/9 | 6 × (11/18 − 2/9) = 7/3 |
| 右の子 | 3 | 1 − (1 + 4) / 9 = 4/9 | 花弁幅 ≤ 0.55（花弁長さでは分け切れない） | 0 | 3 × 4/9 = 4/3 |

合計 11/3 に対する割合で、花弁長さが 7/11、花弁幅が 4/11 になります。

```text
E       AssertionError: assert {'がく片幅': 0.0, '花弁幅': 0.0} == {'がく片幅': 0.0, '花弁幅': 1.0}
E         Differing items:
E         {'花弁幅': 0.0} != {'花弁幅': 1.0}
```

木をたどりながら減少量を合計し、割合に直します。

```python
def impurity_decreases(
    tree: Leaf | Node, x: pd.DataFrame, t: pd.Series, totals: dict[str, float]
) -> None:
    if isinstance(tree, Leaf):
        return
    split = tree.split
    totals[split.feature] += len(t) * (gini(t.to_list()) - split.impurity)
    goes_left = x[split.feature] <= split.threshold
    impurity_decreases(tree.left, x[goes_left], t[goes_left], totals)
    impurity_decreases(tree.right, x[~goes_left], t[~goes_left], totals)


def tree_importances(
    tree: Leaf | Node, x: pd.DataFrame, t: pd.Series
) -> dict[str, float]:
    totals = {str(column): 0.0 for column in x.columns}
    impurity_decreases(tree, x, t, totals)
    return normalize(totals)
```

データを左右に振り分ける部分は、第 3 章の `build_tree` と同じ形の再帰です。

```text
============================= 19 passed in 0.79s ==============================
```

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度（学習に使ったブートストラップ標本で計算）の平均を、もう一度割合に直したものです。木が 1 本なら、その木の重要度と一致するはずです。

```python
class TestForestImportances:
    def test_木が1本なら学習に使った行でのその木の重要度と一致する(self) -> None:
        x = pd.DataFrame(
            {
                "がく片幅": [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4],
                "花弁幅": [0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86],
            }
        )
        t = pd.Series(["setosa"] * 4 + ["virginica"] * 4)
        forest = RandomForest(n_estimators=1, max_features=2, seed=0).fit(x, t)
        columns, tree = forest.trees[0]
        rows = forest.bootstrap_rows[0]
        assert tree.tree is not None

        expected = tree_importances(tree.tree, x.iloc[rows][columns], t.iloc[rows])

        assert forest_importances(forest, x, t) == pytest.approx(expected)
```

```text
E   ImportError: cannot import name 'forest_importances' from 'lib.chapter10.feature_importance'
```

```python
def forest_importances(
    forest: RandomForest, x: pd.DataFrame, t: pd.Series
) -> dict[str, float]:
    totals = {str(column): 0.0 for column in x.columns}
    for (columns, model), rows in zip(forest.trees, forest.bootstrap_rows, strict=True):
        if model.tree is not None:
            sample_x, sample_t = x.iloc[rows][columns], t.iloc[rows]
            for feature, value in tree_importances(
                model.tree, sample_x, sample_t
            ).items():
                totals[feature] += value / len(forest.trees)
    return normalize(totals)
```

Green になったあと、`tree_importances` と `forest_importances` で重複していた「合計で割って割合にする」処理を `normalize` に切り出しました。

```python
def normalize(totals: dict[str, float]) -> dict[str, float]:
    total = sum(totals.values())
    if total == 0.0:
        return totals
    return {feature: value / total for feature, value in totals.items()}
```

```text
============================= 20 passed in 0.86s ==============================
```

## 10.7 モデル共通のインターフェース

### Protocol で「fit と predict を持つもの」を表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも `fit(x, t)` と `predict(x)` を持っています。Java のインターフェースのように継承関係を宣言しなくても、Python では `typing.Protocol` で「このメソッドを持っていればよい」という型を定義できます（構造的部分型）。

まず、テスト用の単純なモデル `AlwaysSetosa` で、評価関数の振る舞いを決めます。

```python
# test/chapter10/test_classifier.py
class AlwaysSetosa:
    def fit(self, x: pd.DataFrame, t: pd.Series) -> "AlwaysSetosa":
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        return ["setosa"] * len(x)


def small_split() -> TrainTestSplit:
    return TrainTestSplit(
        x_train=pd.DataFrame({"花弁幅": [0.1, 0.2, 0.8, 0.9]}),
        x_test=pd.DataFrame({"花弁幅": [0.15, 0.25]}),
        t_train=pd.Series(["setosa", "setosa", "virginica", "virginica"]),
        t_test=pd.Series(["setosa", "setosa"]),
    )


class TestEvaluate:
    def test_学習させてから訓練データとテストデータの正解率を求める(self) -> None:
        assert evaluate(AlwaysSetosa(), small_split()) == Score(train=0.5, test=1.0)
```

```text
E   ModuleNotFoundError: No module named 'lib.chapter10.classifier'
```

```python
# lib/chapter10/classifier.py
from dataclasses import dataclass
from typing import Protocol, Self

import pandas as pd

from lib.chapter01.kinoko_takenoko import accuracy
from lib.chapter02.iris_preprocessing import TrainTestSplit


class Classifier(Protocol):
    def fit(self, x: pd.DataFrame, t: pd.Series) -> Self: ...

    def predict(self, x: pd.DataFrame) -> list[str]: ...


@dataclass(frozen=True)
class Score:
    train: float
    test: float


def evaluate(model: Classifier, split: TrainTestSplit) -> Score:
    model.fit(split.x_train, split.t_train)
    return Score(
        train=accuracy(model.predict(split.x_train), split.t_train.to_list()),
        test=accuracy(model.predict(split.x_test), split.t_test.to_list()),
    )
```

- `-> Self` は「自分自身と同じ型を返す」ことを表します。`DecisionTree.fit` は `DecisionTree` を、`LogisticRegression.fit` は `LogisticRegression` を返すので、どちらも `Classifier` の条件を満たします
- 正解率は第 1 章の `accuracy`、分割結果は第 2 章の `TrainTestSplit` を再利用しています

### 3 つのモデルを同じ関数で評価する

```python
    def test_第3章の決定木と自作のモデルを同じ関数で評価できる(self) -> None:
        models: list[Classifier] = [
            DecisionTree(max_depth=1),
            LogisticRegression(),
            RandomForest(n_estimators=5, max_features=1, seed=0),
        ]

        scores = [evaluate(model, small_split()) for model in models]

        assert scores == [Score(train=1.0, test=1.0)] * 3
```

このテストは、`evaluate` を実装した後に書いたので、最初から通りました（Red を経ていません）。実行時の振る舞いより、`models: list[Classifier]` という型注釈に意味があるテストです。第 3 章の `DecisionTree` は `Classifier` を知らずに書かれていますが、mypy は `fit` と `predict` の形を照らし合わせて、このリストに入れてよいと判断します。メソッドの名前や引数の型を変えると、mypy がこの行でエラーを出します。

```bash
uv run mypy lib/chapter10 test/chapter10
```

```text
Success: no issues found in 12 source files
```

## 10.8 実データで突き合わせる

### モデルを比べる

`python -m lib.chapter10` で、iris のテストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。

```python
# lib/chapter10/__main__.py
from lib.chapter02.iris_preprocessing import prepare_iris
from lib.chapter03.decision_tree import DecisionTree
from lib.chapter10.classifier import Classifier, evaluate
from lib.chapter10.feature_importance import forest_importances
from lib.chapter10.logistic_regression import LogisticRegression
from lib.chapter10.random_forest import RandomForest
from lib.dataset import data_dir

TEST_SIZE = 0.3
SEED = 0
N_ESTIMATORS = 100


def models() -> list[tuple[str, Classifier]]:
    return [
        ("決定木（深さ 2）", DecisionTree(max_depth=2)),
        ("ロジスティック回帰", LogisticRegression()),
        (
            f"ランダムフォレスト（{N_ESTIMATORS} 本）",
            RandomForest(n_estimators=N_ESTIMATORS, max_features=2, seed=SEED),
        ),
        (
            f"ランダムフォレスト（{N_ESTIMATORS} 本・深さ 2）",
            RandomForest(
                n_estimators=N_ESTIMATORS, max_features=2, max_depth=2, seed=SEED
            ),
        ),
    ]


def main() -> None:
    split = prepare_iris(data_dir() / "iris.csv", test_size=TEST_SIZE, seed=SEED)
    print("モデル\t訓練データ\tテストデータ")
    for name, model in models():
        score = evaluate(model, split)
        print(f"{name}\t{score.train:.4f}\t{score.test:.4f}")

    forest = RandomForest(n_estimators=N_ESTIMATORS, max_features=2, seed=SEED)
    forest.fit(split.x_train, split.t_train)
    print(f"\nランダムフォレスト（{N_ESTIMATORS} 本）の特徴量の重要度:")
    for feature, value in forest_importances(
        forest, split.x_train, split.t_train
    ).items():
        print(f"{feature}\t{value:.4f}")


if __name__ == "__main__":
    main()
```

```bash
uv run python -m lib.chapter10
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.9238	0.8889
ランダムフォレスト（100 本）	1.0000	0.8667
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9556

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.1942
がく片幅	0.1257
花弁長さ	0.1904
花弁幅	0.4897
```

`models()` の戻り値の型が `list[tuple[str, Classifier]]` なので、`for name, model in models()` のループでは、モデルの種類を気にせず `evaluate` を呼べます。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.8667）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、決定木より複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです

### scikit-learn と突き合わせる

`test/chapter10/test_iris_models.py` で、scikit-learn と突き合わせます。

```python
@pytest.fixture(scope="module")
def iris_split() -> TrainTestSplit:
    return prepare_iris(data_dir() / "iris.csv", test_size=0.3, seed=0)


@requires_data("iris.csv")
class TestIrisModels:
    def test_ロジスティック回帰は正則化なしのscikit_learnと予測が一致する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = LogisticRegression().fit(iris_split.x_train, iris_split.t_train)
        library = LibraryLogisticRegression(C=np.inf, max_iter=1000)
        library.fit(iris_split.x_train, iris_split.t_train)

        assert model.predict(iris_split.x_test) == list(
            library.predict(iris_split.x_test)
        )

    def test_ロジスティック回帰はテストデータの45件中40件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        score = evaluate(LogisticRegression(), iris_split)

        assert score.test == pytest.approx(40 / 45)

    def test_ランダムフォレストはテストデータの45件中39件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = RandomForest(n_estimators=100, max_features=2, seed=0)

        score = evaluate(model, iris_split)

        assert (score.train, score.test) == (1.0, pytest.approx(39 / 45))

    def test_scikit_learnのランダムフォレストはテストデータの45件中40件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        library = RandomForestClassifier(n_estimators=100, random_state=0)
        library.fit(iris_split.x_train, iris_split.t_train)

        assert library.score(iris_split.x_test, iris_split.t_test) == pytest.approx(
            40 / 45
        )

    def test_深さ3の決定木の重要度はscikit_learnと一致する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = DecisionTree(max_depth=3).fit(iris_split.x_train, iris_split.t_train)
        library = DecisionTreeClassifier(max_depth=3, random_state=0)
        library.fit(iris_split.x_train, iris_split.t_train)
        assert model.tree is not None

        importances = tree_importances(
            model.tree, iris_split.x_train, iris_split.t_train
        )

        assert list(importances.values()) == pytest.approx(
            list(library.feature_importances_)
        )
```

比較の条件は、モデルごとに次のようにそろえました。

| 比較 | 条件 | 結果 |
|------|------|------|
| ロジスティック回帰 | scikit-learn は既定で L2 正則化（`C=1.0`）を掛けるので、`C=np.inf` で正則化なしにそろえる。使った scikit-learn 1.9.1 では `penalty` 引数が非推奨になっており、`C` で指定する | テストデータ 45 件の予測が一致。訓練 0.9238・テスト 0.8889 |
| ランダムフォレスト | 木の数 100、特徴量 2 つ。ただし特徴量を選ぶ単位（自作は木ごと、scikit-learn は分割ごと）と乱数の使い方が違うので、予測の一致は求めない | 自作 0.8667（39/45）、scikit-learn 0.8889（40/45） |
| 決定木の重要度 | 深さ 3 | 4 つの特徴量すべてで一致（花弁幅 0.9216、がく片長さ 0.0477、花弁長さ 0.0308、がく片幅 0） |

ロジスティック回帰の係数は、最適化の方法（自作は勾配降下法、scikit-learn は L-BFGS 法）や、止めるまでの繰り返し回数が違うので、値そのものは一致しません。予測は、確率が最大の品種が同じであれば一致します。

決定木の重要度は深さ 3 では一致しましたが、深さを制限しない木では、予測は一致するのに重要度がずれました（自作のがく片長さ 0.0619 に対して scikit-learn は 0.0428）。深い節では、不純度が同じになる分割候補が複数の特徴量にまたがって現れます。自作の `best_split` は列の順で先に見つかった特徴量を選びますが、scikit-learn は調べる特徴量の順番を乱数で決めるので、同じ不純度の候補から別の特徴量を選ぶことがあります。分け方が同じなら予測は変わりませんが、どの特徴量の手柄になるかが変わるのです。重要度は「そのモデルがどう分けたか」の記録であり、特徴量そのものの価値の絶対的な指標ではないことに注意してください。

テストの実行結果です。

```bash
uv run pytest test/chapter10
```

```text
============================= 28 passed in 7.07s ==============================
```

データが無い環境では、実データのテスト 6 件がスキップされます。

```text
======================== 22 passed, 6 skipped in 2.06s ========================
```

`main` の表示テスト（`test_実行するとモデルごとの正解率とランダムフォレストの重要度を表示する`）は、`main` を先に書いて表示を確かめてから書いたので、Red を経ていません。表示内容を固定するためのテストです。

## 10.9 Notebook で探索する

Notebook は `apps/python/notebooks/chapter10_ensemble_exploration.ipynb` にあります。

### ロジスティック回帰の損失の推移

```python
logistic = LogisticRegression().fit(split.x_train, split.t_train)
losses = pd.Series(logistic.losses, name="交差エントロピー")
losses.plot(logy=True, title="勾配降下法の繰り返し回数と損失")
losses.iloc[[0, 9, 99, 999, 4999]].round(4)
```

```text
0       1.0986
9       0.8123
99      0.3899
999     0.1944
4999    0.1558
Name: 交差エントロピー, dtype: float64
```

最初の損失 1.0986 は、3 品種に均等な確率（1/3）を出したときの交差エントロピー（log 3）です。重みがすべて 0 から始まるので、最初はどの品種にも同じ確率を出します。縦軸を対数にしたグラフでは、最初の 100 回で急に下がり、その後はゆっくり下がり続けます。既定の繰り返し回数を 5000 回にしたのは、iris で 20000 回まで増やしても訓練データ・テストデータの正解率が変わらなかったためです。

```python
pd.DataFrame(
    logistic.weights, index=split.x_train.columns, columns=logistic.classes
).round(2)
```

```text
       Iris-setosa  Iris-versicolor  Iris-virginica
がく片長さ        -5.99             1.50            4.49
がく片幅          9.30            -4.11           -5.20
花弁長さ         -5.43            -1.69            7.12
花弁幅         -13.73            -1.32           15.05
```

重みの表は、決定木のルールと同じく「モデルが何を手がかりにしたか」を読むのに使えます。花弁幅の重みは setosa で大きく負、virginica で大きく正です。花弁幅が大きいほど virginica のスコアが上がり setosa のスコアが下がる、という第 3 章の決定木と同じ傾向を、ロジスティック回帰は連続的な重みとして学習しています。

### モデル別の特徴量の重要度

```python
tree = DecisionTree(max_depth=3).fit(split.x_train, split.t_train)
forest = RandomForest(n_estimators=100, max_features=2, seed=0)
forest.fit(split.x_train, split.t_train)
library = RandomForestClassifier(n_estimators=100, random_state=0)
library.fit(split.x_train, split.t_train)

importances = pd.DataFrame(
    {
        "決定木（深さ 3）": tree_importances(tree.tree, split.x_train, split.t_train),
        "ランダムフォレスト（自作）": forest_importances(
            forest, split.x_train, split.t_train
        ),
        "ランダムフォレスト（scikit-learn）": pd.Series(
            library.feature_importances_, index=split.x_train.columns
        ),
    }
)
importances.plot.barh(title="モデル別の特徴量の重要度")
importances.round(4)
```

```text
       決定木（深さ 3）  ランダムフォレスト（自作）  ランダムフォレスト（scikit-learn）
がく片長さ     0.0477         0.1942                   0.1626
がく片幅      0.0000         0.1257                   0.0707
花弁長さ      0.0308         0.1904                   0.1957
花弁幅       0.9216         0.4897                   0.5710
```

横棒グラフで並べると、3 つのモデルとも花弁幅が最も重要という点は共通しています。決定木は花弁幅に 9 割以上が集中するのに対し、ランダムフォレストは他の特徴量にも分散します。自作と scikit-learn のランダムフォレストでは、がく片幅の重要度に差があります。特徴量を木ごとに選ぶ自作の方式では、がく片幅と別の特徴量だけを持つ木が一定の割合でできるため、がく片幅で分割する機会が増えるからだと考えられます。

### 森の大きさと正解率

```python
rows = []
for n_estimators in [1, 5, 10, 25, 50, 100]:
    score = evaluate(
        RandomForest(n_estimators=n_estimators, max_features=2, seed=0), split
    )
    rows.append(
        {"木の数": n_estimators, "訓練データ": score.train, "テストデータ": score.test}
    )
sizes = pd.DataFrame(rows).set_index("木の数")
sizes.plot(marker="o", title="ランダムフォレストの木の数と正解率")
sizes.round(4)
```

```text
      訓練データ  テストデータ
木の数                
1    0.8857  0.6444
5    0.9619  0.6444
10   1.0000  0.8444
25   1.0000  0.8889
50   1.0000  0.8667
100  1.0000  0.8667
```

木が 1 本のときは、使える特徴量が 2 つに限られ、ブートストラップ標本で学習しているため、テストデータの正解率は 0.6444 にとどまります。木を増やすと正解率が上がり、25 本以降はおおむね横ばいです。`evaluate` を使い回しているので、ループで条件を変えるだけで比較できます。

グラフの画像は、第 2 章と同じ理由で記事に載せていません。学習データを配置して、手元の Notebook で確認してください。

<details>
<summary>この章の完成コード（lib/chapter10/logistic_regression.py）</summary>

```python
import numpy as np
import pandas as pd
from numpy.typing import NDArray

EPSILON = 1e-12


def softmax(z: NDArray[np.float64]) -> NDArray[np.float64]:
    shifted = z - z.max(axis=1, keepdims=True)
    exp = np.exp(shifted)
    return exp / exp.sum(axis=1, keepdims=True)


def cross_entropy(
    probabilities: NDArray[np.float64], targets: NDArray[np.float64]
) -> float:
    return float(-np.mean(np.sum(targets * np.log(probabilities + EPSILON), axis=1)))


class LogisticRegression:
    def __init__(self, learning_rate: float = 1.0, epochs: int = 5000) -> None:
        self.learning_rate = learning_rate
        self.epochs = epochs
        self.classes: list[str] = []
        self.weights = np.zeros((0, 0))
        self.bias = np.zeros(0)
        self.losses: list[float] = []

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "LogisticRegression":
        features = x.to_numpy(dtype=float)
        self.classes = sorted({str(label) for label in t})
        labels = t.astype(str).to_list()
        targets = np.eye(len(self.classes))[[self.classes.index(v) for v in labels]]
        self.weights = np.zeros((features.shape[1], len(self.classes)))
        self.bias = np.zeros(len(self.classes))
        self.losses = []
        for _ in range(self.epochs):
            probabilities = softmax(features @ self.weights + self.bias)
            self.losses.append(cross_entropy(probabilities, targets))
            gradient = (probabilities - targets) / len(features)
            self.weights -= self.learning_rate * features.T @ gradient
            self.bias -= self.learning_rate * gradient.sum(axis=0)
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        if not self.classes:
            raise ValueError("fit で学習してから predict を呼んでください")
        scores = x.to_numpy(dtype=float) @ self.weights + self.bias
        return [self.classes[i] for i in scores.argmax(axis=1)]
```

</details>

<details>
<summary>この章の完成コード（lib/chapter10/random_forest.py）</summary>

```python
from collections import Counter

import numpy as np
import pandas as pd
from numpy.typing import NDArray

from lib.chapter03.decision_tree import DecisionTree


def majority_vote(votes: list[list[str]]) -> list[str]:
    return [Counter(sample).most_common(1)[0][0] for sample in zip(*votes, strict=True)]


def bootstrap_sample(size: int, rng: np.random.Generator) -> NDArray[np.int64]:
    return rng.integers(0, size, size=size)


class RandomForest:
    def __init__(
        self,
        n_estimators: int = 10,
        max_features: int = 2,
        max_depth: int | None = None,
        seed: int = 0,
    ) -> None:
        self.n_estimators = n_estimators
        self.max_features = max_features
        self.max_depth = max_depth
        self.seed = seed
        self.trees: list[tuple[list[str], DecisionTree]] = []
        self.bootstrap_rows: list[NDArray[np.int64]] = []

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "RandomForest":
        rng = np.random.default_rng(self.seed)
        self.trees = []
        self.bootstrap_rows = []
        for _ in range(self.n_estimators):
            rows = bootstrap_sample(len(x), rng)
            self.bootstrap_rows.append(rows)
            chosen = rng.choice(len(x.columns), size=self.max_features, replace=False)
            columns = [str(x.columns[i]) for i in sorted(chosen)]
            tree = DecisionTree(max_depth=self.max_depth)
            tree.fit(x.iloc[rows][columns], t.iloc[rows])
            self.trees.append((columns, tree))
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        if not self.trees:
            raise ValueError("fit で学習してから predict を呼んでください")
        return majority_vote([tree.predict(x[columns]) for columns, tree in self.trees])
```

</details>

<details>
<summary>この章の完成コード（lib/chapter10/feature_importance.py）</summary>

```python
import pandas as pd

from lib.chapter03.decision_tree import Leaf, Node, gini
from lib.chapter10.random_forest import RandomForest


def normalize(totals: dict[str, float]) -> dict[str, float]:
    total = sum(totals.values())
    if total == 0.0:
        return totals
    return {feature: value / total for feature, value in totals.items()}


def impurity_decreases(
    tree: Leaf | Node, x: pd.DataFrame, t: pd.Series, totals: dict[str, float]
) -> None:
    if isinstance(tree, Leaf):
        return
    split = tree.split
    totals[split.feature] += len(t) * (gini(t.to_list()) - split.impurity)
    goes_left = x[split.feature] <= split.threshold
    impurity_decreases(tree.left, x[goes_left], t[goes_left], totals)
    impurity_decreases(tree.right, x[~goes_left], t[~goes_left], totals)


def tree_importances(
    tree: Leaf | Node, x: pd.DataFrame, t: pd.Series
) -> dict[str, float]:
    totals = {str(column): 0.0 for column in x.columns}
    impurity_decreases(tree, x, t, totals)
    return normalize(totals)


def forest_importances(
    forest: RandomForest, x: pd.DataFrame, t: pd.Series
) -> dict[str, float]:
    totals = {str(column): 0.0 for column in x.columns}
    for (columns, model), rows in zip(forest.trees, forest.bootstrap_rows, strict=True):
        if model.tree is not None:
            sample_x, sample_t = x.iloc[rows][columns], t.iloc[rows]
            for feature, value in tree_importances(
                model.tree, sample_x, sample_t
            ).items():
                totals[feature] += value / len(forest.trees)
    return normalize(totals)
```

</details>

## 10.10 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通のインターフェースで評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値であふれた。境界の値のテストで見つけ、最大値を引く方法で直した
2. **行列による学習** — ロジスティック回帰の勾配降下法を、NumPy の行列の積で全サンプルまとめて計算した
3. **部品の再利用** — 第 3 章の決定木を変更せずに組み合わせ、ブートストラップ標本と特徴量の部分集合でランダムフォレストを作った
4. **Protocol による共通インターフェース** — 継承を使わずに「`fit` と `predict` を持つもの」を型で表し、3 つのモデルを同じ `evaluate` で評価した。mypy がモデルとインターフェースの食い違いを検出する
5. **突き合わせの条件をそろえる** — 正則化の有無をそろえたロジスティック回帰は予測が一致した。ランダムフォレストや深い木の重要度は、乱数や同点の扱いの違いで一致しないことを確かめ、その理由を説明した

iris のテストデータ 45 件では、どのモデルも 1〜3 件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
