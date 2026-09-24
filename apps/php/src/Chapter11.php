<?php

declare(strict_types=1);

namespace GettingStartedMl;

use Closure;
use GettingStartedMl\Chapter03\DecisionTree;
use GettingStartedMl\Chapter03\RubixTree;
use GettingStartedMl\Chapter11\ConfusionMatrix;
use GettingStartedMl\Chapter11\Fold;
use GettingStartedMl\Chapter11\RocPoint;
use InvalidArgumentException;
use Rubix\ML\Classifiers\ClassificationTree;
use Rubix\ML\CrossValidation\KFold;
use Rubix\ML\CrossValidation\Metrics\Accuracy;
use Rubix\ML\CrossValidation\Metrics\FBeta;
use Rubix\ML\CrossValidation\Metrics\MeanAbsoluteError;
use Rubix\ML\CrossValidation\Metrics\MeanSquaredError;
use Rubix\ML\CrossValidation\Metrics\RMSE;
use Rubix\ML\CrossValidation\Reports\ConfusionMatrix as RubixConfusionMatrix;
use Rubix\ML\CrossValidation\Reports\MulticlassBreakdown;
use Rubix\ML\Datasets\Labeled;

/**
 * 第 11 章: 評価指標と交差検証。
 *
 * 混同行列・適合率・再現率・F 値・ROC 曲線と AUC・K 分割交差検証を自作し、
 * Rubix ML の `CrossValidation\Metrics\*`・`Reports\ConfusionMatrix`・
 * `Reports\MulticlassBreakdown`・`KFold` と突き合わせる。
 *
 * 評価関数は「正解と予測を受け取って 1 つの数を返す関数」、分類器は「訓練データを
 * 受け取って予測する関数を返す関数」。どちらも `Closure` なので、インターフェースを
 * 1 つも定義せずに差し替えられる。
 */
final class Chapter11
{
    /** Survived.csv の特徴量の列。 */
    public const array SURVIVED_COLUMNS = ['Pclass', 'Age', 'male'];

    /** 正例にするラベル（生存）。 */
    public const string SURVIVED = '1';

    /** 決定木の深さ。 */
    public const int TREE_DEPTH = 2;

    /** 分割の数。 */
    public const int N_SPLITS = 5;

    /** 分割の乱数のシード。 */
    public const int SEED = 0;

    // ---- 件数の検査 ----

    /**
     * 正解と予測の件数が同じでなければ失敗する。短いほうに合わせて黙って切り詰めない。
     *
     * @param list<mixed> $actual
     * @param list<mixed> $predicted
     */
    public static function requireSameSize(array $actual, array $predicted): void
    {
        if (count($actual) !== count($predicted)) {
            throw new InvalidArgumentException(
                sprintf('正解と予測の件数が違います: %d と %d', count($actual), count($predicted)),
            );
        }
    }

    // ---- 混同行列 ----

    /**
     * 正解と予測を 1 件ずつ比べて数える。`$positive` と等しいラベルを正例、それ以外を負例とする。
     *
     * @param list<string> $actual
     * @param list<string> $predicted
     */
    public static function confusionMatrix(array $actual, array $predicted, string $positive): ConfusionMatrix
    {
        self::requireSameSize($actual, $predicted);

        $tp = $fp = $fn = $tn = 0;

        foreach ($actual as $index => $label) {
            $isPositive = $label === $positive;
            $saidPositive = $predicted[$index] === $positive;

            match (true) {
                $isPositive && $saidPositive => ++$tp,
                $saidPositive => ++$fp,
                $isPositive => ++$fn,
                default => ++$tn,
            };
        }

        return new ConfusionMatrix($tp, $fp, $fn, $tn);
    }

    /**
     * 混同行列から求める指標を、正解と予測から採点する評価関数に変える。
     *
     * @param Closure(ConfusionMatrix): float $score
     *
     * @return Closure(list<string>, list<string>): float
     */
    public static function classificationMetric(Closure $score, string $positive): Closure
    {
        return static function (array $actual, array $predicted) use ($score, $positive): float {
            /** @var list<string> $actual */
            /** @var list<string> $predicted */
            return $score(self::confusionMatrix($actual, $predicted, $positive));
        };
    }

    // ---- 正解と予測から直接求める指標 ----

    /**
     * 正解率。正解と予測が一致した割合。2 値でなくても求められる。
     *
     * @param list<string> $actual
     * @param list<string> $predicted
     */
    public static function accuracy(array $actual, array $predicted): float
    {
        self::requireSameSize($actual, $predicted);

        $correct = 0;

        foreach ($actual as $index => $label) {
            if ($label === $predicted[$index]) {
                ++$correct;
            }
        }

        return ConfusionMatrix::ratio($correct, count($actual));
    }

    /**
     * 平均二乗誤差（MSE）。誤差の 2 乗の平均。
     *
     * @param list<float> $actual
     * @param list<float> $predicted
     */
    public static function meanSquaredError(array $actual, array $predicted): float
    {
        self::requireSameSize($actual, $predicted);

        return Chapter07::sumOfSquares(Chapter07::residuals($actual, $predicted)) / count($actual);
    }

    /**
     * 平均。値が 1 つも無ければ失敗する。
     *
     * @param list<float> $values
     */
    public static function mean(array $values): float
    {
        if ($values === []) {
            throw new InvalidArgumentException('平均を求める値が 1 つもありません');
        }

        return array_sum($values) / count($values);
    }

    // ---- ROC 曲線と AUC ----

    /**
     * スコア（正例らしさ）と正解（正例なら true）から ROC 曲線を求める。
     *
     * スコアの高いほうから閾値を下げていき、同じスコアは 1 つの点にまとめる。
     * 先頭には「どれも正例と予測しない」点（偽陽性率も真陽性率も 0）を置く。
     *
     * @param list<float> $scores
     * @param list<bool>  $labels
     *
     * @return list<RocPoint>
     */
    public static function rocCurve(array $scores, array $labels): array
    {
        self::requireSameSize($scores, $labels);

        $positives = count(array_filter($labels));
        $negatives = count($labels) - $positives;

        if ($positives === 0 || $negatives === 0) {
            throw new InvalidArgumentException('正例と負例が両方ないと ROC 曲線を描けません');
        }

        $pairs = [];

        foreach ($scores as $index => $score) {
            $pairs[] = [$score, $labels[$index]];
        }

        // スコアは浮動小数点なので、配列の鍵にして数えることはできない（PHP は鍵を整数に丸める）。
        // 並べ替えてから、同じスコアが続くあいだ足し込む。
        usort($pairs, static fn (array $a, array $b): int => $b[0] <=> $a[0]);

        $curve = [new RocPoint(null, 0.0, 0.0)];
        $tp = $fp = 0;

        foreach ($pairs as $index => [$score, $isPositive]) {
            if ($isPositive) {
                ++$tp;
            } else {
                ++$fp;
            }

            $next = $pairs[$index + 1] ?? null;

            if ($next === null || $next[0] !== $score) {
                $curve[] = new RocPoint((float) $score, $fp / $negatives, $tp / $positives);
            }
        }

        return $curve;
    }

    /**
     * ROC 曲線の下の面積（AUC）を台形則で求める。
     *
     * @param list<RocPoint> $curve
     */
    public static function auc(array $curve): float
    {
        $area = 0.0;

        for ($i = 1; $i < count($curve); ++$i) {
            $left = $curve[$i - 1];
            $right = $curve[$i];
            $area += ($right->falsePositiveRate - $left->falsePositiveRate)
                * ($left->truePositiveRate + $right->truePositiveRate) / 2.0;
        }

        return $area;
    }

    // ---- K 分割交差検証 ----

    /**
     * シード付きの乱数で行の位置を並べ替え、`$nSplits` 個のテストデータに分ける。
     *
     * 件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。
     *
     * @return list<Fold>
     */
    public static function kFold(int $nSamples, int $nSplits, int $seed): array
    {
        self::checkSplits($nSamples, $nSplits);

        return self::folds(Random::shuffle(range(0, $nSamples - 1), $seed), $nSplits);
    }

    /**
     * 並べ替えずに、先頭から順に `$nSplits` 個のかたまりに分ける。余りの配り方は kFold と同じ。
     *
     * @return list<Fold>
     */
    public static function kFoldSequential(int $nSamples, int $nSplits): array
    {
        self::checkSplits($nSamples, $nSplits);

        return self::folds(range(0, $nSamples - 1), $nSplits);
    }

    /**
     * 行の位置で値を選ぶ。
     *
     * @template T
     *
     * @param list<T>   $values
     * @param list<int> $positions
     *
     * @return list<T>
     */
    public static function pick(array $values, array $positions): array
    {
        return array_map(
            static function (int $position) use ($values): mixed {
                if (!array_key_exists($position, $values)) {
                    throw new InvalidArgumentException("行がありません: {$position}");
                }

                return $values[$position];
            },
            $positions,
        );
    }

    /**
     * 分割ごとに分類器を訓練データで学習し、テストデータの予測を評価関数で採点する。
     *
     * @template TLabel
     *
     * @param Closure(list<array<string, float>>, list<TLabel>): (Closure(array<string, float>): TLabel) $trainer
     * @param list<array<string, float>>                                                                 $x
     * @param list<TLabel>                                                                               $t
     * @param list<Fold>                                                                                 $folds
     * @param Closure(list<TLabel>, list<TLabel>): float                                                 $metric
     *
     * @return list<float>
     */
    public static function crossValidate(
        Closure $trainer,
        array $x,
        array $t,
        array $folds,
        Closure $metric,
    ): array {
        return array_map(
            static function (Fold $fold) use ($trainer, $x, $t, $metric): float {
                $predict = $trainer(self::pick($x, $fold->train), self::pick($t, $fold->train));

                return $metric(
                    self::pick($t, $fold->test),
                    array_map($predict, self::pick($x, $fold->test)),
                );
            },
            $folds,
        );
    }

    // ---- 分類器 ----

    /**
     * 第 3 章の決定木の分類器。
     *
     * @param list<string> $columns
     *
     * @return Closure(list<array<string, float>>, list<string>): (Closure(array<string, float>): string)
     */
    public static function treeTrainer(array $columns, ?int $maxDepth = null): Closure
    {
        return static function (array $x, array $t) use ($columns, $maxDepth): Closure {
            /** @var list<array<string, float>> $x */
            /** @var list<string> $t */
            $tree = DecisionTree::fit($x, $t, $columns, $maxDepth);

            return static fn (array $features): string => DecisionTree::predictOne($tree->tree, $features);
        };
    }

    /**
     * 第 7 章の線形回帰の分類器（回帰なので予測は数値）。
     *
     * @param list<string> $columns
     *
     * @return Closure(list<array<string, float>>, list<float>): (Closure(array<string, float>): float)
     */
    public static function linearTrainer(array $columns): Closure
    {
        return static function (array $x, array $t) use ($columns): Closure {
            /** @var list<array<string, float>> $x */
            /** @var list<float> $t */
            $model = Chapter07::fit($x, $t, $columns);

            return $model->predictOne(...);
        };
    }

    // ---- Rubix ML との突き合わせ ----

    /**
     * `Rubix\ML\CrossValidation\Metrics\Accuracy` の正解率。予測が先、正解があとの順で渡す。
     *
     * @param list<string> $actual
     * @param list<string> $predicted
     */
    public static function rubixAccuracy(array $actual, array $predicted): float
    {
        return (new Accuracy())->score($predicted, $actual);
    }

    /**
     * `Rubix\ML\CrossValidation\Metrics\FBeta` の F 値。
     *
     * **2 値でも「正例だけの F 値」ではない。** クラスごとに適合率と再現率を求めてから
     * 平均し、その平均どうしで調和平均を取るので、自作の 2 値の F 値とは別の数になる。
     *
     * @param list<string> $actual
     * @param list<string> $predicted
     */
    public static function rubixFBeta(array $actual, array $predicted): float
    {
        return (new FBeta())->score($predicted, $actual);
    }

    /**
     * `Rubix\ML\CrossValidation\Reports\MulticlassBreakdown` の、正例のクラスの行。
     *
     * このレポートはクラスごとに 2 値の指標を出すので、正例の行だけを読めば
     * 自作の適合率・再現率・F 値と同じ定義になる。
     *
     * @param list<string> $actual
     * @param list<string> $predicted
     *
     * @return array{precision: float, recall: float, f1Score: float}
     */
    public static function rubixClassScores(array $actual, array $predicted, string $positive): array
    {
        /** @var array{classes: array<array-key, array<string, float>>} $report */
        $report = (new MulticlassBreakdown())->generate($predicted, $actual)->toArray();
        $classes = $report['classes'];

        if (!array_key_exists($positive, $classes)) {
            throw new InvalidArgumentException("正例のクラスがありません: {$positive}");
        }

        $row = $classes[$positive];

        return [
            'precision' => $row['precision'],
            'recall' => $row['recall'],
            'f1Score' => $row['f1 score'],
        ];
    }

    /**
     * `Rubix\ML\CrossValidation\Reports\ConfusionMatrix` の 2 次元の表。
     *
     * **外側の鍵が予測、内側の鍵が正解** である（`$matrix[$prediction][$label]` と数えている）。
     * 行を正解にする書き方が多いので、向きを実測で確かめてから読む。
     *
     * @param list<string> $actual
     * @param list<string> $predicted
     *
     * @return array<array-key, array<array-key, int>>
     */
    public static function rubixConfusionMatrix(array $actual, array $predicted): array
    {
        /** @var array<array-key, array<array-key, int>> $matrix */
        $matrix = (new RubixConfusionMatrix())->generate($predicted, $actual)->toArray();

        return $matrix;
    }

    /**
     * Rubix ML の回帰の指標。
     *
     * **どれも符号を反転した値を返す。** Rubix ML の指標は「大きいほどよい」に
     * そろえてあるので、誤差の指標は 0 以下の数になる。自作と比べるには符号を戻す。
     *
     * @param list<float> $actual
     * @param list<float> $predicted
     *
     * @return array{mse: float, rmse: float, mae: float}
     */
    public static function rubixRegressionScores(array $actual, array $predicted): array
    {
        return [
            'mse' => -(new MeanSquaredError())->score($predicted, $actual),
            'rmse' => -(new RMSE())->score($predicted, $actual),
            'mae' => -(new MeanAbsoluteError())->score($predicted, $actual),
        ];
    }

    /**
     * `Rubix\ML\Datasets\Labeled::fold()` が作る、分割ごとのテストデータの件数。
     *
     * Rubix ML は `floor(件数 / 分割数)` をすべての分割の大きさにするので、
     * **余りの行はどの分割のテストデータにも入らない**。自作の kFold とはここが違う。
     *
     * @return list<int>
     */
    public static function rubixFoldTestSizes(int $nSamples, int $nSplits): array
    {
        $samples = array_fill(0, $nSamples, [0.0]);
        $labels = array_map(strval(...), range(0, $nSamples - 1));

        return array_map(
            static fn (Labeled $fold): int => $fold->numSamples(),
            Labeled::quick($samples, $labels)->fold($nSplits),
        );
    }

    /**
     * `Rubix\ML\CrossValidation\KFold` で決定木を交差検証したときの正解率の平均。
     *
     * **シードを渡す口が無い。** `test()` の中で `randomize()` を呼ぶので、
     * 同じ入力でも実行のたびに値が変わりうる。分割ごとのスコアも取り出せず、平均だけが返る。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public static function rubixKFoldAccuracy(
        array $x,
        array $t,
        array $columns,
        int $maxDepth,
        int $nSplits,
    ): float {
        $tree = new ClassificationTree(
            $maxDepth,
            maxLeafSize: 1,
            minPurityIncrease: 0.0,
            maxFeatures: count($columns),
            maxBins: RubixTree::MAX_BINS,
        );

        return (new KFold($nSplits))->test(
            $tree,
            new Labeled(RubixTree::samples($x, $columns), $t),
            new Accuracy(),
        );
    }

    // ---- 実データの前処理 ----

    /**
     * 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。
     *
     * この章では分割の前に全体の平均値で年齢の欠損値を補う、簡略化した前処理を使う。
     *
     * @return array{x: list<array<string, float>>, t: list<string>}
     */
    public static function prepareSurvived(Table $table): array
    {
        $ageMean = Chapter02::columnMeans($table->rows, ['Age'])['Age'];

        return [
            'x' => array_map(
                static fn (array $row): array => [
                    'Pclass' => self::requiredNumber($row, 'Pclass'),
                    'Age' => Chapter02::number($row, 'Age') ?? $ageMean,
                    'male' => Chapter02::text($row, 'Sex') === 'male' ? 1.0 : 0.0,
                ],
                $table->rows,
            ),
            't' => array_map(
                static fn (array $row): string => Chapter02::text($row, 'Survived'),
                $table->rows,
            ),
        ];
    }

    /**
     * 第 7 章の 4 列を特徴量に、興行収入を正解にする。特徴量の欠損値は列ごとの平均値で補う。
     *
     * @return array{x: list<array<string, float>>, t: list<float>}
     */
    public static function prepareCinema(Table $table): array
    {
        $columns = Chapter07::FEATURE_COLUMNS;
        $means = Chapter02::columnMeans($table->rows, $columns);

        return [
            'x' => Chapter02::fillMissing($table->rows, $columns, $means),
            't' => array_map(
                static function (array $row): float {
                    $value = Chapter02::number($row, Chapter07::TARGET);

                    if ($value === null) {
                        throw new InvalidArgumentException('興行収入が空欄です');
                    }

                    return $value;
                },
                $table->rows,
            ),
        ];
    }

    // ---- 実データでの実行 ----

    /**
     * Survived の評価指標。表示する順に並べる。
     *
     * @return list<array{string, Closure(list<string>, list<string>): float}>
     */
    public static function survivedMetrics(): array
    {
        return [
            ['正解率', self::accuracy(...)],
            ['適合率', self::classificationMetric(
                static fn (ConfusionMatrix $cm): float => $cm->precision(),
                self::SURVIVED,
            )],
            ['再現率', self::classificationMetric(
                static fn (ConfusionMatrix $cm): float => $cm->recall(),
                self::SURVIVED,
            )],
            ['F値', self::classificationMetric(
                static fn (ConfusionMatrix $cm): float => $cm->f1Score(),
                self::SURVIVED,
            )],
        ];
    }

    /**
     * cinema の評価指標。第 7 章の RMSE・MAE をそのまま渡す。
     *
     * @return list<array{string, Closure(list<float>, list<float>): float}>
     */
    public static function cinemaMetrics(): array
    {
        return [
            ['RMSE', Chapter07::rootMeanSquaredError(...)],
            ['MAE', Chapter07::meanAbsoluteError(...)],
        ];
    }

    /**
     * 同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。
     *
     * @template TLabel
     *
     * @param Closure(list<array<string, float>>, list<TLabel>): (Closure(array<string, float>): TLabel) $trainer
     * @param list<array<string, float>>                                                                 $x
     * @param list<TLabel>                                                                               $t
     * @param list<array{string, Closure(list<TLabel>, list<TLabel>): float}>                            $metrics
     *
     * @return list<array{string, float}>
     */
    public static function evaluate(Closure $trainer, array $x, array $t, array $metrics): array
    {
        $folds = self::kFold(count($x), self::N_SPLITS, self::SEED);

        return array_map(
            static fn (array $metric): array => [
                $metric[0],
                self::mean(self::crossValidate($trainer, $x, $t, $folds, $metric[1])),
            ],
            $metrics,
        );
    }

    /**
     * Survived.csv を深さ 2 の決定木で評価する。
     *
     * @return list<array{string, float}>
     */
    public static function evaluateSurvived(string $path): array
    {
        ['x' => $x, 't' => $t] = self::prepareSurvived(Chapter02::loadTable($path));

        return self::evaluate(
            self::treeTrainer(self::SURVIVED_COLUMNS, self::TREE_DEPTH),
            $x,
            $t,
            self::survivedMetrics(),
        );
    }

    /**
     * cinema.csv を線形回帰で評価する。
     *
     * @return list<array{string, float}>
     */
    public static function evaluateCinema(string $path): array
    {
        ['x' => $x, 't' => $t] = self::prepareCinema(Chapter02::loadTable($path));

        return self::evaluate(
            self::linearTrainer(Chapter07::FEATURE_COLUMNS),
            $x,
            $t,
            self::cinemaMetrics(),
        );
    }

    /** Survived と cinema を K 分割交差検証で評価し、Rubix ML の分け方と比べる。 */
    public static function run(?string $dir = null): string
    {
        $dir ??= Dataset::dir();
        $survivedPath = $dir . '/Survived.csv';

        return sprintf("Survived（決定木、%d 分割交差検証の平均）\n", self::N_SPLITS)
            . self::formatScores(self::evaluateSurvived($survivedPath), 4)
            . sprintf("cinema（線形回帰、%d 分割交差検証の平均）\n", self::N_SPLITS)
            . self::formatScores(self::evaluateCinema($dir . '/cinema.csv'), 2)
            . sprintf("Rubix ML の fold のテストデータの件数（%d 分割）\n", self::N_SPLITS)
            . self::formatSplitSizes($survivedPath);
    }

    /** 自作と Rubix ML の分割ごとのテストデータの件数を並べる。 */
    private static function formatSplitSizes(string $path): string
    {
        $total = count(Chapter02::loadTable($path)->rows);
        $mine = array_map(
            static fn (Fold $fold): int => count($fold->test),
            self::kFold($total, self::N_SPLITS, self::SEED),
        );

        return sprintf("  %d 件を自作: %s\n", $total, implode(', ', $mine))
            . sprintf(
                "  %d 件を Rubix ML: %s\n",
                $total,
                implode(', ', self::rubixFoldTestSizes($total, self::N_SPLITS)),
            );
    }

    /**
     * 指標の名前とスコアを 1 行ずつ並べる。
     *
     * @param list<array{string, float}> $scores
     */
    private static function formatScores(array $scores, int $digits): string
    {
        $lines = '';

        foreach ($scores as [$name, $score]) {
            $lines .= sprintf("  %s: %.{$digits}f\n", $name, $score);
        }

        return $lines;
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

    /** 分割の数が使える範囲にあるかを確かめる。 */
    private static function checkSplits(int $nSamples, int $nSplits): void
    {
        if ($nSplits < 2 || $nSplits > $nSamples) {
            throw new InvalidArgumentException(
                sprintf('分割の数は 2 以上 %d 以下にしてください: %d', $nSamples, $nSplits),
            );
        }
    }

    /**
     * 並べた位置を `$nSplits` 個のかたまりに分け、かたまりごとに 1 つをテストデータ、
     * 残りを訓練データにする。余りは先頭の分割から 1 件ずつ配る。
     *
     * @param list<int> $positions
     *
     * @return list<Fold>
     */
    private static function folds(array $positions, int $nSplits): array
    {
        $total = count($positions);
        $folds = [];
        $offset = 0;

        for ($index = 0; $index < $nSplits; ++$index) {
            $size = intdiv($total, $nSplits) + ($index < $total % $nSplits ? 1 : 0);
            $test = array_slice($positions, $offset, $size);
            $offset += $size;

            $folds[] = new Fold(
                array_values(array_filter(
                    $positions,
                    static fn (int $position): bool => !in_array($position, $test, true),
                )),
                $test,
            );
        }

        return $folds;
    }
}
