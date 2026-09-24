<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter09\Standardizer;
use GettingStartedMl\Chapter12\BostonData;
use GettingStartedMl\Chapter12\Experiment;
use InvalidArgumentException;
use MathPHP\LinearAlgebra\MatrixFactory;
use MathPHP\LinearAlgebra\NumericMatrix;
use MathPHP\LinearAlgebra\Vector;

/**
 * 第 12 章: 正則化とモデル選択。
 *
 * リッジ回帰を MathPHP の行列で自作し、検証データで正則化の強さを選び、
 * Rubix ML の `Ridge` と突き合わせる。
 * **Rubix ML にラッソ回帰は無い**ので、座標降下法で自作したものがそのまま最終実装になる（ADR 013）。
 *
 * モデルは第 7 章の `LinearModel`（切片・列名・係数）をそのまま使う。
 */
final class Chapter12
{
    /** 特徴量に使う元の列。2 次の項はここから作る。 */
    public const array FEATURE_COLUMNS = ['RM', 'PTRATIO', 'LSTAT'];

    /** 正解の列。 */
    public const string TARGET = 'PRICE';

    /** z スコアの絶対値がこの値を超える値を持つ行を外れ値とする。 */
    public const float OUTLIER_THRESHOLD = 3.0;

    /** テストデータの割合。 */
    public const float TEST_SIZE = 0.3;

    /** 訓練用のうち検証データにする割合。 */
    public const float VALIDATION_SIZE = 0.3;

    /** 分割の乱数のシード。 */
    public const int SEED = 0;

    /** 試す正則化の強さ。 */
    public const array ALPHAS = [0.0, 0.1, 1.0, 10.0, 100.0];

    /**
     * ラッソ回帰の正則化の強さ。
     *
     * 自作の目的関数は件数で割らないので、件数で割る実装（Java 版・Clojure 版が使う
     * Tribuo の ElasticNetCDTrainer）の alpha=0.5 は訓練データ 47 件では 0.5 × 47 = 23.5 にあたる。
     */
    public const float LASSO_ALPHA = 23.5;

    /** 座標降下法の繰り返しの上限。 */
    private const int MAX_ITERATIONS = 10000;

    /** 係数の動きがこれより小さくなったら打ち切る。 */
    private const float TOLERANCE = 1.0e-10;

    // ---- リッジ回帰 ----

    /**
     * 平均を引いてから `(Xᵀ X + alpha I) w = Xᵀ t` を解いて係数を求め、切片は平均値から求める。
     *
     * 平均を引くのは、切片に罰則をかけないため。`$alpha` が 0 なら最小二乗法と同じ解になる。
     *
     * @param list<array<string, float>> $x
     * @param list<float>                $t
     * @param list<string>               $columns
     */
    public static function ridgeFit(array $x, array $t, array $columns, float $alpha): LinearModel
    {
        self::requireSameSize($x, $t);

        $xMeans = self::columnMeans($x, $columns);
        $tMean = array_sum($t) / count($t);
        $centered = MatrixFactory::createNumeric(self::center($x, $columns, $xMeans));
        $transposed = $centered->transpose();
        $residuals = new Vector(array_map(static fn (float $v): float => $v - $tMean, $t));

        $penalty = MatrixFactory::identity(count($columns))->scalarMultiply($alpha);

        $coefficients = array_map(
            floatval(...),
            array_values(
                $transposed->multiply($centered)
                    ->add($penalty)
                    ->solve($transposed->vectorMultiply($residuals), NumericMatrix::LU)
                    ->getVector(),
            ),
        );

        return new LinearModel($tMean - self::dot($xMeans, $coefficients), $columns, $coefficients);
    }

    // ---- ラッソ回帰（Rubix ML に無いので自作が最終実装） ----

    /** 軟しきい値作用素。絶対値が `$threshold` 以下なら 0 にし、そうでなければ 0 のほうへ縮める。 */
    public static function softThreshold(float $value, float $threshold): float
    {
        if ($value > $threshold) {
            return $value - $threshold;
        }

        if ($value < -$threshold) {
            return $value + $threshold;
        }

        return 0.0;
    }

    /**
     * 平均を引いてから座標降下法で `½‖t - Xw‖² + alpha ‖w‖₁` を最小にする。
     *
     * L1 の罰則は原点で折れているので、リッジ回帰のように行列を解いて終わりにはできない。
     * 係数を 1 つずつ順に動かし、軟しきい値作用素で 0 に寄せる。
     *
     * @param list<array<string, float>> $x
     * @param list<float>                $t
     * @param list<string>               $columns
     */
    public static function lassoFit(array $x, array $t, array $columns, float $alpha): LinearModel
    {
        self::requireSameSize($x, $t);

        $xMeans = self::columnMeans($x, $columns);
        $tMean = array_sum($t) / count($t);
        $centered = self::center($x, $columns, $xMeans);

        // 列ごとの値と、その 2 乗の合計をあらかじめ取り出しておく
        $features = [];
        $norms = [];

        foreach ($columns as $index => $_column) {
            $values = array_column($centered, $index);
            $features[] = $values;
            $norms[] = self::dot($values, $values);
        }

        $weights = array_fill(0, count($columns), 0.0);
        $residuals = array_map(static fn (float $v): float => $v - $tMean, $t);

        for ($iteration = 0; $iteration < self::MAX_ITERATIONS; ++$iteration) {
            $delta = 0.0;

            foreach ($columns as $index => $_column) {
                if ($norms[$index] === 0.0) {
                    continue;
                }

                $column = $features[$index];
                $old = $weights[$index];

                // いったん列の寄与を残差に戻してから、残差との相関で係数を決め直す
                foreach ($residuals as $row => $residual) {
                    $residuals[$row] = $residual + $old * $column[$row];
                }

                $weight = self::softThreshold(self::dot($column, $residuals), $alpha) / $norms[$index];

                foreach ($residuals as $row => $residual) {
                    $residuals[$row] = $residual - $weight * $column[$row];
                }

                $weights[$index] = $weight;
                $delta = max($delta, abs($weight - $old));
            }

            if ($delta < self::TOLERANCE) {
                break;
            }
        }

        $coefficients = array_values($weights);

        return new LinearModel($tMean - self::dot($xMeans, $coefficients), $columns, $coefficients);
    }

    // ---- モデルを読む ----

    /** 係数の絶対値の合計。正則化が強いほど小さくなる。 */
    public static function coefficientAbsSum(LinearModel $model): float
    {
        return array_sum(array_map(abs(...), $model->coefficients));
    }

    /**
     * 係数がちょうど 0 になった特徴量の名前を、列の順に返す。
     *
     * @return list<string>
     */
    public static function zeroCoefficientNames(LinearModel $model): array
    {
        $names = [];

        foreach ($model->columns as $index => $column) {
            if ($model->coefficients[$index] === 0.0) {
                $names[] = $column;
            }
        }

        return $names;
    }

    // ---- 実験の記録とモデル選択 ----

    /**
     * `$alphas` ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。
     *
     * @param list<float> $alphas
     *
     * @return list<Experiment>
     */
    public static function runRidgeExperiments(BostonData $data, array $alphas): array
    {
        return array_map(
            static function (float $alpha) use ($data): Experiment {
                $fitted = self::ridgeFit($data->xTrain, $data->tTrain, $data->featureNames, $alpha);

                return new Experiment(
                    $alpha,
                    Chapter07::r2Score($data->tTrain, $fitted->predict($data->xTrain)),
                    Chapter07::r2Score($data->tValid, $fitted->predict($data->xValid)),
                    self::coefficientAbsSum($fitted),
                );
            },
            $alphas,
        );
    }

    /**
     * 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。
     *
     * @param list<Experiment> $experiments
     */
    public static function bestExperiment(array $experiments): Experiment
    {
        if ($experiments === []) {
            throw new InvalidArgumentException('実験結果が 1 件もありません');
        }

        $best = $experiments[0];

        foreach ($experiments as $experiment) {
            if ($experiment->validationScore > $best->validationScore) {
                $best = $experiment;
            }
        }

        return $best;
    }

    /** 指定した alpha のリッジ回帰を訓練データで学習し、テストデータの決定係数を返す。 */
    public static function testScore(BostonData $data, float $alpha): float
    {
        $fitted = self::ridgeFit($data->xTrain, $data->tTrain, $data->featureNames, $alpha);

        return Chapter07::r2Score($data->tTest, $fitted->predict($data->xTest));
    }

    // ---- 外れ値を除く ----

    /**
     * 列ごとの z スコア（標準偏差は件数 n − 1 で割る標本標準偏差）の絶対値が
     * `$threshold` を超える値を 1 つでも持つ行を除く。
     *
     * 第 9 章の四分位範囲による外れ値の判定とは別の規準である。
     *
     * @param list<string> $columns
     */
    public static function removeOutliers(Table $table, array $columns, float $threshold): Table
    {
        $stats = [];

        foreach ($columns as $column) {
            $values = array_map(
                static fn (array $row): float => self::requiredNumber($row, $column),
                $table->rows,
            );
            $mean = array_sum($values) / count($values);
            $squares = Chapter07::sumOfSquares(
                array_map(static fn (float $v): float => $v - $mean, $values),
            );
            $stats[$column] = [$mean, sqrt($squares / (count($values) - 1))];
        }

        $rows = array_values(array_filter(
            $table->rows,
            static function (array $row) use ($stats, $threshold): bool {
                foreach ($stats as $column => [$mean, $std]) {
                    if (abs((self::requiredNumber($row, (string) $column) - $mean) / $std) > $threshold) {
                        return false;
                    }
                }

                return true;
            },
        ));

        return new Table($table->columns, $rows);
    }

    // ---- Rubix ML のリッジ回帰 ----

    /**
     * Rubix ML の `Ridge` で学習し、自作と同じ形のモデルにする。
     *
     * `Ridge` が最小にするのは `‖t - Xw - b‖² + alpha ‖w‖²` で、自作と同じ尺度である。
     * 中心化こそしないが、1 の列を足したうえで**その列の罰則だけ 0 にする**ので、
     * 切片に罰則がかからない点も自作と同じになる。違うのは最後の一手だけで、
     * Rubix ML は `inverse()`、自作は MathPHP の LU 分解である（第 7 章と同じ関係）。
     *
     * @param list<array<string, float>> $x
     * @param list<float>                $t
     * @param list<string>               $columns
     */
    public static function rubixRidgeFit(array $x, array $t, array $columns, float $alpha): LinearModel
    {
        return Chapter07::rubixFit($x, $t, $columns, $alpha);
    }

    // ---- ボストンの住宅価格 ----

    /**
     * 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
     *
     * 標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換してから
     * 2 次の項を作る。
     */
    public static function prepareBoston(
        string $path,
        float $testSize,
        float $validationSize,
        int $seed,
    ): BostonData {
        $table = self::removeOutliers(
            Chapter02::loadTable($path),
            [self::TARGET, ...self::FEATURE_COLUMNS],
            self::OUTLIER_THRESHOLD,
        );

        $x = array_map(
            static function (array $row): array {
                $features = [];

                foreach (self::FEATURE_COLUMNS as $column) {
                    $features[$column] = self::requiredNumber($row, $column);
                }

                return $features;
            },
            $table->rows,
        );
        $t = array_map(
            static fn (array $row): string => (string) self::requiredNumber($row, self::TARGET),
            $table->rows,
        );

        $outer = Chapter02::splitTrainTest($x, $t, $testSize, $seed);
        $inner = Chapter02::splitTrainTest($outer['xTrain'], $outer['tTrain'], $validationSize, $seed);
        $scaler = Standardizer::fit($inner['xTrain'], self::FEATURE_COLUMNS);

        return new BostonData(
            self::buildFeatures($scaler, $inner['xTrain']),
            array_map(floatval(...), $inner['tTrain']),
            self::buildFeatures($scaler, $inner['xTest']),
            array_map(floatval(...), $inner['tTest']),
            self::buildFeatures($scaler, $outer['xTest']),
            array_map(floatval(...), $outer['tTest']),
            Chapter09::expandedColumns(self::FEATURE_COLUMNS),
            count($table->rows),
        );
    }

    // ---- 実データでの実行 ----

    /** 正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルとラッソ回帰の結果を返す。 */
    public static function run(?string $path = null): string
    {
        $path ??= Dataset::path('Boston.csv');
        $total = count(Chapter02::loadTable($path)->rows);
        $data = self::prepareBoston($path, self::TEST_SIZE, self::VALIDATION_SIZE, self::SEED);
        $experiments = self::runRidgeExperiments($data, self::ALPHAS);
        $best = self::bestExperiment($experiments);
        $lasso = self::lassoFit($data->xTrain, $data->tTrain, $data->featureNames, self::LASSO_ALPHA);

        return sprintf("データ件数: %d（外れ値 %d 件を除外）\n", $data->kept, $total - $data->kept)
            . sprintf(
                "訓練データ: %d 件, 検証データ: %d 件, テストデータ: %d 件\n",
                count($data->tTrain),
                count($data->tValid),
                count($data->tTest),
            )
            . sprintf("特徴量: %s\n", implode(', ', $data->featureNames))
            . "alpha  訓練 R²  検証 R²  係数の絶対値の合計\n"
            . self::formatExperiments($experiments)
            . sprintf("検証データで選んだ alpha: %s\n", self::formatNumber($best->alpha, 1))
            . sprintf(
                "テストデータの決定係数: 線形回帰 %s, リッジ回帰 %s\n",
                self::formatNumber(self::testScore($data, 0.0), 4),
                self::formatNumber(self::testScore($data, $best->alpha), 4),
            )
            . sprintf(
                "Rubix ML のリッジ回帰との係数の最大の差: %s\n",
                self::formatExponent(self::maxDifference($data, $best->alpha)),
            )
            . sprintf(
                "ラッソ回帰（alpha=%s）で係数が 0 になった特徴量: %s\n",
                self::formatNumber(self::LASSO_ALPHA, 1),
                implode(', ', self::zeroCoefficientNames($lasso)),
            );
    }

    /** 自作と Rubix ML のリッジ回帰の、係数の差の最大値。 */
    private static function maxDifference(BostonData $data, float $alpha): float
    {
        $mine = self::ridgeFit($data->xTrain, $data->tTrain, $data->featureNames, $alpha);
        $theirs = self::rubixRidgeFit($data->xTrain, $data->tTrain, $data->featureNames, $alpha);

        return max([0.0, ...array_map(
            static fn (float $a, float $b): float => abs($a - $b),
            $mine->coefficients,
            $theirs->coefficients,
        )]);
    }

    /**
     * 実験の表を 1 行ずつ並べる。
     *
     * @param list<Experiment> $experiments
     */
    private static function formatExperiments(array $experiments): string
    {
        $lines = '';

        foreach ($experiments as $experiment) {
            $lines .= sprintf(
                "%5s  %s  %s  %s\n",
                self::formatNumber($experiment->alpha, 1),
                self::formatNumber($experiment->trainScore, 4),
                self::formatNumber($experiment->validationScore, 4),
                self::formatNumber($experiment->coefficientAbsSum, 3),
            );
        }

        return $lines;
    }

    /**
     * 標準化してから 2 次の項を作る。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<array<string, float>>
     */
    private static function buildFeatures(Standardizer $scaler, array $x): array
    {
        return Chapter09::expand($scaler->transformAll($x), self::FEATURE_COLUMNS);
    }

    /**
     * 列ごとの平均。列の順に並べる。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<float>
     */
    private static function columnMeans(array $x, array $columns): array
    {
        return array_map(
            static fn (string $column): float => array_sum(
                array_map(static fn (array $features): float => self::feature($features, $column), $x),
            ) / count($x),
            $columns,
        );
    }

    /**
     * 列ごとの平均を引いた行列にする。列の順に並べる。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     * @param list<float>                $means
     *
     * @return list<list<float>>
     */
    private static function center(array $x, array $columns, array $means): array
    {
        return array_map(
            static fn (array $features): array => array_map(
                static fn (string $column, float $mean): float => self::feature($features, $column) - $mean,
                $columns,
                $means,
            ),
            $x,
        );
    }

    /**
     * 内積。
     *
     * @param list<float> $a
     * @param list<float> $b
     */
    private static function dot(array $a, array $b): float
    {
        $sum = 0.0;

        foreach ($a as $index => $value) {
            $sum += $value * $b[$index];
        }

        return $sum;
    }

    /**
     * 特徴量の列の値を読む。列が無ければ失敗する。
     *
     * @param array<string, float> $features
     */
    private static function feature(array $features, string $column): float
    {
        if (!array_key_exists($column, $features)) {
            throw new InvalidArgumentException("特徴量がありません: {$column}");
        }

        return $features[$column];
    }

    /**
     * 欠損値を許さずに数値の列を読む。
     *
     * @param array<string, string> $row
     */
    private static function requiredNumber(array $row, string $column): float
    {
        $value = Chapter02::number($row, $column);

        if ($value === null) {
            throw new InvalidArgumentException("値が空欄です: {$column}");
        }

        return $value;
    }

    /**
     * 特徴量と正解の件数が同じでなければ失敗する。
     *
     * @param list<mixed> $x
     * @param list<mixed> $t
     */
    private static function requireSameSize(array $x, array $t): void
    {
        if (count($x) !== count($t)) {
            throw new InvalidArgumentException(
                sprintf('特徴量と正解の件数が違います: %d と %d', count($x), count($t)),
            );
        }
    }

    /** PHP 8.0 以降の sprintf はロケールに依らないので、小数点は常に「.」になる。 */
    private static function formatNumber(float $value, int $digits): string
    {
        return sprintf("%.{$digits}f", $value);
    }

    /** 指数の形にする。 */
    private static function formatExponent(float $value): string
    {
        return sprintf('%.1e', $value);
    }
}
