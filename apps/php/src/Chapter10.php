<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter03\DecisionTree;
use GettingStartedMl\Chapter03\Leaf;
use GettingStartedMl\Chapter03\Node;
use GettingStartedMl\Chapter10\Classifier;
use GettingStartedMl\Chapter10\ForestClassifier;
use GettingStartedMl\Chapter10\ForestTree;
use GettingStartedMl\Chapter10\LogisticClassifier;
use GettingStartedMl\Chapter10\RandomForest;
use GettingStartedMl\Chapter10\RubixForestClassifier;
use GettingStartedMl\Chapter10\RubixSoftmaxClassifier;
use GettingStartedMl\Chapter10\TreeClassifier;
use InvalidArgumentException;

/**
 * 第 10 章: ロジスティック回帰とアンサンブル学習。
 *
 * 分類器は Classifier という 1 つの interface で表す。第 3 章の決定木も Rubix ML の
 * モデルも、包む器を 1 つ書くだけで同じ score() に通せる。Rubix ML には
 * LogisticRegression もランダムフォレストもあるので、どちらも突き合わせられる。
 */
final class Chapter10
{
    /** テストデータの割合。 */
    public const float TEST_SIZE = 0.3;

    /** 分割の乱数のシード。 */
    public const int SEED = 0;

    /** 森に作る木の数。 */
    public const int N_ESTIMATORS = 100;

    /** 木ごと（Rubix ML は節ごと）に使う特徴量の数。 */
    public const int MAX_FEATURES = 2;

    /** 浅い決定木の深さ。 */
    public const int SHALLOW_DEPTH = 2;

    /** log 0 が -INF にならないように足すごく小さい値。 */
    private const float EPSILON = 1.0e-12;

    // 10.3 ソフトマックス関数

    /**
     * スコアを、合計が 1 になる確率に変換する。
     *
     * 最大値を引いてから exp を求めるので、大きな値でもあふれない。
     * 引いた分は分母と分子で打ち消し合うので、結果は変わらない。
     *
     * @param list<float> $z
     *
     * @return list<float>
     */
    public static function softmax(array $z): array
    {
        if ($z === []) {
            throw new InvalidArgumentException('スコアが 1 つもありません');
        }

        $maximum = max($z);
        $exps = array_map(static fn (float $v): float => exp($v - $maximum), $z);
        $total = array_sum($exps);

        return array_map(static fn (float $v): float => $v / $total, $exps);
    }

    /**
     * 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。
     *
     * 確率が 0 のときに log 0 が -INF にならないよう、ごく小さい値を足す。
     *
     * @param list<list<float>> $probabilities
     * @param list<int>         $targets
     */
    public static function crossEntropy(array $probabilities, array $targets): float
    {
        if ($probabilities === []) {
            throw new InvalidArgumentException('確率が 1 件もありません');
        }

        $sum = 0.0;

        foreach ($probabilities as $i => $probability) {
            $sum += log($probability[$targets[$i]] + self::EPSILON);
        }

        return -($sum / count($probabilities));
    }

    // 10.5 ランダムフォレスト

    /**
     * サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。
     *
     * @param list<list<string>> $votes
     *
     * @return list<string>
     */
    public static function majorityVote(array $votes): array
    {
        if ($votes === []) {
            throw new InvalidArgumentException('予測が 1 件もありません');
        }

        return array_map(
            static fn (int $sample): string => DecisionTree::majority(array_column($votes, $sample)),
            range(0, count($votes[0]) - 1),
        );
    }

    /**
     * 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。
     *
     * 乱数生成器を受け取り、状態を進めながら引く。森を作る途中で状態を引き継ぐため。
     *
     * @return list<int>
     */
    public static function bootstrapSample(int $size, Random $random): array
    {
        $rows = [];

        for ($i = 0; $i < $size; ++$i) {
            $rows[] = $random->nextInt($size);
        }

        return $rows;
    }

    // 10.6 特徴量の重要度

    /**
     * 決定木 1 本の重要度。合計が 1 になるようにする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     *
     * @return array<string, float>
     */
    public static function treeImportances(DecisionTree $tree, array $x, array $t, array $columns): array
    {
        $totals = array_fill_keys($columns, 0.0);

        foreach (self::impurityDecreases($tree->tree, $x, $t) as [$feature, $amount]) {
            $totals[$feature] += $amount;
        }

        return self::normalize($totals);
    }

    /**
     * 木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     *
     * @return array<string, float>
     */
    public static function forestImportances(RandomForest $forest, array $x, array $t, array $columns): array
    {
        $totals = array_fill_keys($columns, 0.0);
        $count = count($forest->trees);

        foreach ($forest->trees as $tree) {
            [$sampleX, $sampleT] = self::sampleOf($tree, $x, $t);

            foreach (self::treeImportances($tree->tree, $sampleX, $sampleT, $tree->columns) as $feature => $value) {
                $totals[$feature] += $value / $count;
            }
        }

        return self::normalize($totals);
    }

    // 10.7 モデル共通の約束

    /**
     * 分類器を訓練データで学習させてから、訓練データとテストデータの正解率を求める。
     *
     * @param array{xTrain: list<array<string, float>>, xTest: list<array<string, float>>, tTrain: list<string>, tTest: list<string>} $split
     * @param list<string>                                                                                                          $columns
     *
     * @return array{train: float, test: float}
     */
    public static function score(Classifier $classifier, array $split, array $columns): array
    {
        $model = $classifier->fit($split['xTrain'], $split['tTrain'], $columns);

        return [
            'train' => Chapter01::accuracy($model->predict($split['xTrain']), $split['tTrain']),
            'test' => Chapter01::accuracy($model->predict($split['xTest']), $split['tTest']),
        ];
    }

    /**
     * 名前と分類器。表示する順に並べる。
     *
     * @return list<array{string, Classifier}>
     */
    public static function models(): array
    {
        return [
            ['決定木（深さ ' . self::SHALLOW_DEPTH . '）', new TreeClassifier(self::SHALLOW_DEPTH)],
            ['ロジスティック回帰', new LogisticClassifier()],
            [
                'ランダムフォレスト（' . self::N_ESTIMATORS . ' 本）',
                new ForestClassifier(self::N_ESTIMATORS, self::MAX_FEATURES, null, self::SEED),
            ],
            [
                'ランダムフォレスト（' . self::N_ESTIMATORS . ' 本・深さ ' . self::SHALLOW_DEPTH . '）',
                new ForestClassifier(self::N_ESTIMATORS, self::MAX_FEATURES, self::SHALLOW_DEPTH, self::SEED),
            ],
            ['Rubix ML ソフトマックス（既定の学習率 0.01）', new RubixSoftmaxClassifier()],
            ['Rubix ML ソフトマックス（学習率 1.0）', new RubixSoftmaxClassifier(0.0, 1000, 1.0)],
            [
                'Rubix ML ランダムフォレスト（' . self::N_ESTIMATORS . ' 本）',
                new RubixForestClassifier(self::N_ESTIMATORS, self::MAX_FEATURES),
            ],
        ];
    }

    /** モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。 */
    public static function run(?string $path = null): string
    {
        $split = Chapter02::prepareIris($path ?? Dataset::path('iris.csv'), self::TEST_SIZE, self::SEED);
        $columns = Chapter03::featureColumns();

        $lines = ["モデル\t訓練データ\tテストデータ"];

        foreach (self::models() as [$name, $classifier]) {
            $scores = self::score($classifier, $split, $columns);
            $lines[] = sprintf("%s\t%.4f\t%.4f", $name, $scores['train'], $scores['test']);
        }

        $forest = RandomForest::fit(
            $split['xTrain'],
            $split['tTrain'],
            $columns,
            self::N_ESTIMATORS,
            self::MAX_FEATURES,
            null,
            self::SEED,
        );
        $ours = self::forestImportances($forest, $split['xTrain'], $split['tTrain'], $columns);
        $theirs = (new RubixForestClassifier(self::N_ESTIMATORS, self::MAX_FEATURES))
            ->importances($split['xTrain'], $split['tTrain'], $columns);

        $lines[] = '';
        $lines[] = 'ランダムフォレスト（' . self::N_ESTIMATORS . ' 本）の特徴量の重要度:';
        $lines[] = "特徴量\t自作\tRubix ML";

        foreach ($columns as $column) {
            $lines[] = sprintf("%s\t%.4f\t%.4f", $column, $ours[$column], $theirs[$column]);
        }

        return implode("\n", $lines) . "\n";
    }

    // 章をまたいで使う小さな道具

    /**
     * ラベルの種類を名前の順に並べる。
     *
     * @param list<string> $t
     *
     * @return list<string>
     */
    public static function classesOf(array $t): array
    {
        $classes = array_values(array_unique($t));
        sort($classes);

        return $classes;
    }

    /**
     * 特徴量を列の順に並べた数値の行にする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<list<float>>
     */
    public static function toRows(array $x, array $columns): array
    {
        return array_map(
            static fn (array $features): array => array_map(
                static fn (string $c): float => self::featureValue($features, $c),
                $columns,
            ),
            $x,
        );
    }

    /**
     * 指定した列だけを取り出す。
     *
     * @param array<string, float> $features
     * @param list<string>         $columns
     *
     * @return array<string, float>
     */
    public static function takeColumns(array $features, array $columns): array
    {
        $taken = [];

        foreach ($columns as $column) {
            $taken[$column] = self::featureValue($features, $column);
        }

        return $taken;
    }

    /**
     * 木が学習に使った標本をもう一度作る。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     *
     * @return array{list<array<string, float>>, list<string>}
     */
    private static function sampleOf(ForestTree $tree, array $x, array $t): array
    {
        $sampleX = [];
        $sampleT = [];

        foreach ($tree->rows as $row) {
            $sampleX[] = self::takeColumns($x[$row], $tree->columns);
            $sampleT[] = $t[$row];
        }

        return [$sampleX, $sampleT];
    }

    /**
     * 分割ごとに減った不純度（件数で重み付け）を、[列, 減った量] の並びにする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     *
     * @return list<array{string, float}>
     */
    private static function impurityDecreases(Leaf|Node $tree, array $x, array $t): array
    {
        if ($tree instanceof Leaf) {
            return [];
        }

        $split = $tree->split;
        $leftX = [];
        $leftT = [];
        $rightX = [];
        $rightT = [];

        foreach ($x as $i => $features) {
            if ($split->goesLeft(self::featureValue($features, $split->feature))) {
                $leftX[] = $features;
                $leftT[] = $t[$i];
            } else {
                $rightX[] = $features;
                $rightT[] = $t[$i];
            }
        }

        return [
            [$split->feature, count($t) * (DecisionTree::gini($t) - $split->impurity)],
            ...self::impurityDecreases($tree->left, $leftX, $leftT),
            ...self::impurityDecreases($tree->right, $rightX, $rightT),
        ];
    }

    /**
     * 合計が 1 になるように割る。全部 0 ならそのまま返す。
     *
     * @param array<string, float> $totals
     *
     * @return array<string, float>
     */
    private static function normalize(array $totals): array
    {
        $total = array_sum($totals);

        if ($total === 0.0) {
            return $totals;
        }

        return array_map(static fn (float $value): float => $value / $total, $totals);
    }

    /**
     * 列の値を読む。列が無ければ失敗する。
     *
     * @param array<string, float> $features
     */
    private static function featureValue(array $features, string $column): float
    {
        if (!array_key_exists($column, $features)) {
            throw new InvalidArgumentException("列がありません: {$column}");
        }

        return $features[$column];
    }
}
