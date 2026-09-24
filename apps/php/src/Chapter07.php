<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;
use MathPHP\LinearAlgebra\MatrixFactory;
use MathPHP\LinearAlgebra\NumericMatrix;
use MathPHP\LinearAlgebra\Vector;
use Rubix\ML\Datasets\Labeled;
use Rubix\ML\Regressors\Ridge;

/**
 * 第 7 章: 線形回帰による数値予測。
 *
 * 正規方程式 `(Xᵀ X) w = Xᵀ t` を MathPHP の行列で自分で解いてから、
 * Rubix ML の `Ridge` と突き合わせる。
 *
 * Rubix ML には素の線形回帰（`LinearRegression`）が無い。正則化の強さを 0 にした
 * リッジが最小二乗にあたるので、それを相手にする（ADR 013）。
 */
final class Chapter07
{
    /** 映画のデータの特徴量の列。cinema_id は映画を区別する番号なので使わない。 */
    public const array FEATURE_COLUMNS = ['SNS1', 'SNS2', 'actor', 'original'];

    /** 映画のデータの正解ラベル（興行収入）の列。 */
    public const string TARGET = 'sales';

    /** SNS2 がこの値を超え、かつ興行収入が OUTLIER_SALES 未満の映画を外れ値とする。 */
    private const float OUTLIER_SNS2 = 1000.0;
    private const float OUTLIER_SALES = 8500.0;

    private const float TEST_SIZE = 0.2;
    private const int SEED = 0;

    // ---- 評価指標 ----

    /**
     * 実測値と予測値の差を返す。件数が違えば失敗する。
     *
     * @param list<float> $t 実測値
     * @param list<float> $y 予測値
     *
     * @return list<float>
     */
    public static function residuals(array $t, array $y): array
    {
        if (count($t) !== count($y)) {
            throw new InvalidArgumentException(sprintf('件数が違います: %d と %d', count($t), count($y)));
        }

        return array_map(static fn (float $actual, float $predicted): float => $actual - $predicted, $t, $y);
    }

    /**
     * 平均絶対誤差（MAE）。誤差の絶対値の平均。
     *
     * @param list<float> $t
     * @param list<float> $y
     */
    public static function meanAbsoluteError(array $t, array $y): float
    {
        $residuals = self::residuals($t, $y);

        return array_sum(array_map(abs(...), $residuals)) / count($residuals);
    }

    /**
     * 平均二乗誤差の平方根（RMSE）。
     *
     * @param list<float> $t
     * @param list<float> $y
     */
    public static function rootMeanSquaredError(array $t, array $y): float
    {
        $residuals = self::residuals($t, $y);

        return sqrt(self::sumOfSquares($residuals) / count($residuals));
    }

    /**
     * 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
     *
     * @param list<float> $t
     * @param list<float> $y
     */
    public static function r2Score(array $t, array $y): float
    {
        $residual = self::sumOfSquares(self::residuals($t, $y));
        $mean = array_sum($t) / count($t);
        $total = self::sumOfSquares(array_map(static fn (float $v): float => $v - $mean, $t));

        if ($total === 0.0) {
            throw new InvalidArgumentException('実測値がすべて同じ値です');
        }

        return 1.0 - $residual / $total;
    }

    /**
     * 2 乗の合計。
     *
     * @param list<float> $values
     */
    public static function sumOfSquares(array $values): float
    {
        return array_sum(array_map(static fn (float $v): float => $v * $v, $values));
    }

    // ---- 学習 ----

    /**
     * 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<list<float>>
     */
    public static function designMatrix(array $x, array $columns): array
    {
        return array_map(
            static fn (array $features): array => [1.0, ...self::featureRow($features, $columns)],
            $x,
        );
    }

    /**
     * `(Xᵀ X) w = Xᵀ t` を解いて、切片と係数を求める。
     *
     * 逆行列を作らず、連立方程式として解く。MathPHP の `solve` に LU 分解を明示して、
     * 行列の中身によって解き方が変わらないようにする。
     *
     * @param list<array<string, float>> $x
     * @param list<float>                $t
     * @param list<string>               $columns
     */
    public static function fit(array $x, array $t, array $columns): LinearModel
    {
        if ($x === []) {
            throw new InvalidArgumentException('訓練データが空です');
        }

        if (count($x) !== count($t)) {
            throw new InvalidArgumentException(
                sprintf('特徴量と実測値の件数が違います: %d と %d', count($x), count($t)),
            );
        }

        $design = MatrixFactory::createNumeric(self::designMatrix($x, $columns));
        $transposed = $design->transpose();

        // getVector は int と float の混ざった配列を返すので、float に寄せてから使う。
        $weights = array_map(
            floatval(...),
            array_values(
                $transposed->multiply($design)
                    ->solve($transposed->vectorMultiply(new Vector($t)), NumericMatrix::LU)
                    ->getVector(),
            ),
        );

        return new LinearModel($weights[0], $columns, array_slice($weights, 1));
    }

    /**
     * Rubix ML の `Ridge` で学習し、自作と同じ形のモデルにする。
     *
     * Rubix ML は切片（bias）を自分で足すので、計画行列ではなく特徴量をそのまま渡す。
     * `l2Penalty` の既定は 1.0 なので、最小二乗にするには 0.0 を明示する。
     *
     * @param list<array<string, float>> $x
     * @param list<float>                $t
     * @param list<string>               $columns
     */
    public static function rubixFit(array $x, array $t, array $columns, float $l2Penalty = 0.0): LinearModel
    {
        if ($x === []) {
            throw new InvalidArgumentException('訓練データが空です');
        }

        $samples = array_map(static fn (array $f): array => self::featureRow($f, $columns), $x);
        $ridge = new Ridge($l2Penalty);
        $ridge->train(new Labeled($samples, $t));

        $bias = $ridge->bias();
        $coefficients = $ridge->coefficients();

        if ($bias === null || $coefficients === null) {
            throw new InvalidArgumentException('リッジの学習に失敗しました');
        }

        return new LinearModel($bias, $columns, array_map(floatval(...), array_values($coefficients)));
    }

    // ---- 前処理 ----

    /** 外れ値の行を除いた表を返す。列はそのまま残す。 */
    public static function removeOutliers(Table $table): Table
    {
        $rows = array_values(
            array_filter($table->rows, static fn (array $row): bool => !self::isOutlier($row)),
        );

        return new Table($table->columns, $rows);
    }

    /**
     * 外れ値かどうかを判定する。
     *
     * @param array<string, string> $row
     */
    public static function isOutlier(array $row): bool
    {
        return self::requiredNumber($row, 'SNS2') > self::OUTLIER_SNS2
            && self::requiredNumber($row, self::TARGET) < self::OUTLIER_SALES;
    }

    /**
     * cinema.csv を読み込み、外れ値を除き、分割してから訓練データの平均値で両方を補完する。
     *
     * @return array{xTrain: list<array<string, float>>, xTest: list<array<string, float>>, tTrain: list<float>, tTest: list<float>}
     */
    public static function prepareCinema(string $path, float $testSize, int $seed): array
    {
        $rows = self::removeOutliers(Chapter02::loadTable($path))->rows;
        $t = array_map(
            static fn (array $row): string => (string) self::requiredNumber($row, self::TARGET),
            $rows,
        );
        $split = Chapter02::splitTrainTest($rows, $t, $testSize, $seed);
        $means = Chapter02::columnMeans($split['xTrain'], self::FEATURE_COLUMNS);

        return [
            'xTrain' => Chapter02::fillMissing($split['xTrain'], self::FEATURE_COLUMNS, $means),
            'xTest' => Chapter02::fillMissing($split['xTest'], self::FEATURE_COLUMNS, $means),
            'tTrain' => array_map(floatval(...), $split['tTrain']),
            'tTest' => array_map(floatval(...), $split['tTest']),
        ];
    }

    // ---- 実行 ----

    /** 映画の興行収入を線形回帰で予測し、係数と評価指標を返す。 */
    public static function run(?string $path = null): string
    {
        $path ??= Dataset::path('cinema.csv');
        $table = Chapter02::loadTable($path);
        $split = self::prepareCinema($path, self::TEST_SIZE, self::SEED);
        $model = self::fit($split['xTrain'], $split['tTrain'], self::FEATURE_COLUMNS);
        $library = self::rubixFit($split['xTrain'], $split['tTrain'], self::FEATURE_COLUMNS);
        $y = $model->predict($split['xTest']);
        $t = $split['tTest'];

        return sprintf(
            "データ件数: %d\n外れ値を除いた件数: %d\n訓練データ: %d 件, テストデータ: %d 件\n"
            . "切片: %.2f\n係数: %s\nRubix ML の切片: %.2f, 係数: %s\n"
            . "テストデータの評価: R2=%.4f, MAE=%.2f, RMSE=%.2f\n",
            count($table->rows),
            count(self::removeOutliers($table)->rows),
            count($split['xTrain']),
            count($split['xTest']),
            $model->intercept,
            self::formatCoefficients($model),
            $library->intercept,
            self::formatCoefficients($library),
            self::r2Score($t, $y),
            self::meanAbsoluteError($t, $y),
            self::rootMeanSquaredError($t, $y),
        );
    }

    /** 係数を「列名=値」の並びにする。 */
    private static function formatCoefficients(LinearModel $model): string
    {
        $parts = [];

        foreach ($model->columns as $index => $column) {
            $parts[] = sprintf('%s=%.4f', $column, $model->coefficients[$index]);
        }

        return implode(', ', $parts);
    }

    /**
     * 特徴量を列の順に並べた 1 行にする。
     *
     * @param array<string, float> $features
     * @param list<string>         $columns
     *
     * @return list<float>
     */
    private static function featureRow(array $features, array $columns): array
    {
        return array_map(
            static function (string $column) use ($features): float {
                if (!array_key_exists($column, $features)) {
                    throw new InvalidArgumentException("特徴量がありません: {$column}");
                }

                return $features[$column];
            },
            $columns,
        );
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
}
