<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Preprocessing\DummyEncoder;
use GettingStartedMl\Preprocessing\GroupMedianImputer;
use GettingStartedMl\Preprocessing\MostFrequentImputer;
use GettingStartedMl\Preprocessing\Step;
use InvalidArgumentException;
use Rubix\ML\Datasets\Unlabeled;
use Rubix\ML\Strategies\Percentile;
use Rubix\ML\Transformers\MissingDataImputer;
use Rubix\ML\Transformers\OneHotEncoder;

/**
 * 第 8 章: 実践的な分類と前処理パイプライン。タイタニック号の乗客データを扱う。
 *
 * 前処理は `Preprocessing\Step` を実装したクラスで表し、順に並べてパイプラインにする。
 * 学習済みのパイプラインは標準の `serialize` で保存し、`unserialize` で復元する。
 *
 * Rubix ML には `MissingDataImputer`・`OneHotEncoder` があるが、どちらも
 * この章で必要な口（グループ別の補完・基準列の削除）を持たない。クラスの重みも
 * 分類器に口が無いので、その 3 つは自作になる（8.12 節）。
 */
final class Chapter08
{
    /** モデルに渡す特徴量の列。PassengerId・Name・Ticket・Cabin は使わない。 */
    public const array FEATURE_COLUMNS = ['Pclass', 'Sex', 'Age', 'SibSp', 'Parch', 'Fare', 'Embarked'];

    /** 正解ラベルの列（1 が生存、0 が死亡）。 */
    public const string TARGET = 'Survived';

    /** 生存を表すラベル。 */
    public const string SURVIVED = '1';

    /** 保存した形式の版。読み込むときに確かめる。 */
    public const int FORMAT_VERSION = 1;

    private const float TEST_SIZE = 0.2;
    private const int SEED = 0;
    private const int MAX_DEPTH = 5;

    /** @var list<string> クラスの重みの付け方 */
    private const array CLASS_WEIGHTS = [WeightedTree::NONE, WeightedTree::BALANCED];

    /** 学習済みのパイプラインの保存先（apps/php/model/ は .gitignore の対象）。 */
    public static function modelFile(): string
    {
        return 'model/survived.model';
    }

    // ---- データの形 ----

    /**
     * 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。
     *
     * @param list<array<string, string>> $rows
     */
    public static function featuresTable(array $rows): Table
    {
        return new Table(self::FEATURE_COLUMNS, $rows);
    }

    /**
     * 行の Survived 列を正解ラベルにする。
     *
     * @param list<array<string, string>> $rows
     *
     * @return list<string>
     */
    public static function targetLabels(array $rows): array
    {
        return array_map(static fn (array $row): string => Chapter02::text($row, self::TARGET), $rows);
    }

    /**
     * 中央値。件数が偶数なら中央の 2 つの平均。
     *
     * @param list<float> $values
     */
    public static function median(array $values): float
    {
        if ($values === []) {
            throw new InvalidArgumentException('値がありません');
        }

        sort($values);
        $middle = intdiv(count($values), 2);

        return count($values) % 2 === 1
            ? $values[$middle]
            : ($values[$middle - 1] + $values[$middle]) / 2.0;
    }

    // ---- パイプライン ----

    /**
     * Survived.csv 用の前処理の並び。年齢・港を補完してから、カテゴリをダミー変数にする。
     *
     * @return list<Step>
     */
    public static function steps(): array
    {
        return [
            new GroupMedianImputer('Age', ['Pclass', 'Sex']),
            new MostFrequentImputer('Embarked'),
            new DummyEncoder(['Sex', 'Embarked']),
        ];
    }

    /**
     * 前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。
     *
     * @return list<array<string, float>>
     */
    public static function toFeatures(Table $x): array
    {
        return array_map(
            static function (array $row) use ($x): array {
                $features = [];

                foreach ($x->columns as $column) {
                    $value = Chapter02::number($row, $column);

                    if ($value === null) {
                        throw new InvalidArgumentException("欠損値が残っています: {$column}");
                    }

                    $features[$column] = $value;
                }

                return $features;
            },
            $x->rows,
        );
    }

    /**
     * 訓練データで前処理とモデルを学習する。
     *
     * 前処理は、**前の前処理で変換したデータ** で学習する。港を補完してからでないと
     * ダミー変数化が空欄を見てしまう。
     *
     * @param list<string> $t
     */
    public static function fit(Table $x, array $t, int $maxDepth, string $classWeight): FittedPipeline
    {
        $fitted = [];
        $prepared = $x;

        foreach (self::steps() as $step) {
            $trained = $step->fit($prepared);
            $fitted[] = $trained;
            $prepared = $trained->apply($prepared);
        }

        return new FittedPipeline(
            $fitted,
            $prepared->columns,
            WeightedTree::fit(self::toFeatures($prepared), $t, $prepared->columns, $maxDepth, $classWeight),
        );
    }

    /** 学習済みの前処理を順に合成して、データを変換する。 */
    public static function transform(FittedPipeline $pipeline, Table $x): Table
    {
        foreach ($pipeline->steps as $step) {
            $x = $step->apply($x);
        }

        return $x;
    }

    /**
     * 前処理をして、モデルに渡す特徴量にする。
     *
     * @return list<array<string, float>>
     */
    public static function features(FittedPipeline $pipeline, Table $x): array
    {
        return self::toFeatures(self::transform($pipeline, $x));
    }

    /**
     * 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。
     *
     * @return list<string>
     */
    public static function predict(FittedPipeline $pipeline, Table $x): array
    {
        return WeightedTree::predict($pipeline->tree, self::features($pipeline, $x));
    }

    // ---- 保存と読み込み ----

    /**
     * 学習済みのパイプラインを標準の `serialize` で保存する。
     *
     * 前処理も木も `readonly class` と配列だけでできているので、そのまま往復する。
     */
    public static function saveModel(FittedPipeline $pipeline, string $path): void
    {
        $dir = dirname($path);

        // mkdir も失敗を警告で知らせるので、@ で抑えて戻り値で判定する。
        if (!is_dir($dir) && !@mkdir($dir, 0o777, true) && !is_dir($dir)) {
            throw new InvalidArgumentException("ディレクトリを作れません: {$dir}");
        }

        file_put_contents($path, serialize(['format' => self::FORMAT_VERSION, 'pipeline' => $pipeline]));
    }

    /**
     * 保存したパイプラインを読み込む。形式が違えば失敗する。
     *
     * `allowed_classes` に読み込んでよいクラスを並べる。これを省くと、ファイルに
     * 書かれたどんなクラスでも作られてしまう。Elixir 版が `:erlang.binary_to_term`
     * に `:safe` を付けたのと同じ用心である。
     */
    public static function loadModel(string $path): FittedPipeline
    {
        $contents = @file_get_contents($path);

        if ($contents === false) {
            throw new InvalidArgumentException("モデルを開けません: {$path}");
        }

        // unserialize は読めない内容を例外ではなく警告で知らせるので、@ で抑えて
        // 戻り値の false で判定する。PHPUnit は警告をテストの失敗として扱う設定なので、
        // ここで抑えておかないと「読めないファイルを読む」テストが書けない。
        $loaded = @unserialize($contents, ['allowed_classes' => [
                FittedPipeline::class,
                Branch::class,
                Leaf::class,
                GroupMedianImputer::class,
                MostFrequentImputer::class,
                DummyEncoder::class,
            ]]);

        if (!is_array($loaded) || !array_key_exists('format', $loaded)) {
            throw new InvalidArgumentException("モデルとして読めません: {$path}");
        }

        if ($loaded['format'] !== self::FORMAT_VERSION) {
            throw new InvalidArgumentException(
                '対応していない形式のモデルです: ' . var_export($loaded['format'], true),
            );
        }

        $pipeline = $loaded['pipeline'] ?? null;

        if (!$pipeline instanceof FittedPipeline) {
            throw new InvalidArgumentException("モデルとして読めません: {$path}");
        }

        return $pipeline;
    }

    // ---- Rubix ML との突き合わせ ----

    /**
     * Rubix ML の `MissingDataImputer` で年齢を補完し、補完後の年齢を並べて返す。
     *
     * 中央値の Strategy は無いので、`Percentile(50)` を使う。列ごとに 1 つの値しか
     * 持てないので、グループごとに使い分けることはできない。
     *
     * @return list<float>
     */
    public static function rubixImputeAge(Table $x): array
    {
        $samples = array_map(
            static function (array $row): array {
                $value = Chapter02::number($row, 'Age');

                // Rubix ML は欠損値を NAN で表す。
                return [$value ?? NAN];
            },
            $x->rows,
        );

        $imputer = new MissingDataImputer(new Percentile(50.0));
        $dataset = new Unlabeled($samples);
        $imputer->fit($dataset);
        $dataset->apply($imputer);

        $filled = [];

        foreach ($dataset->samples() as $sample) {
            $value = $sample[0];
            $filled[] = is_numeric($value) ? (float) $value : NAN;
        }

        return $filled;
    }

    /**
     * Rubix ML の `OneHotEncoder` で Sex をダミー変数にし、行ごとの 0/1 を返す。
     *
     * カテゴリを 1 つも落とさないので、列は female と male の 2 本になる。
     *
     * @return list<list<int>>
     */
    public static function rubixOneHotSex(Table $x): array
    {
        $samples = array_map(
            static fn (array $row): array => [Chapter02::text($row, 'Sex')],
            $x->rows,
        );

        $encoder = new OneHotEncoder();
        $dataset = new Unlabeled($samples);
        $encoder->fit($dataset);
        $dataset->apply($encoder);

        $encoded = [];

        foreach ($dataset->samples() as $sample) {
            $row = [];

            foreach ($sample as $value) {
                $row[] = is_numeric($value) ? (int) $value : 0;
            }

            $encoded[] = $row;
        }

        return $encoded;
    }

    // ---- 評価 ----

    /**
     * Survived.csv を読み込んで訓練データとテストデータに分ける。
     *
     * @return array{xTrain: list<array<string, string>>, xTest: list<array<string, string>>, tTrain: list<string>, tTest: list<string>}
     */
    public static function survivedSplit(?string $path = null): array
    {
        $rows = Chapter02::loadTable($path ?? Dataset::path('Survived.csv'))->rows;

        return Chapter02::splitTrainTest($rows, self::targetLabels($rows), self::TEST_SIZE, self::SEED);
    }

    /**
     * 学習済みのパイプラインを、訓練データとテストデータで評価する。
     *
     * @param array{xTrain: list<array<string, string>>, xTest: list<array<string, string>>, tTrain: list<string>, tTest: list<string>} $split
     *
     * @return array{trainAccuracy: float, testAccuracy: float, foundSurvivors: int, survivors: int}
     */
    public static function evaluate(FittedPipeline $pipeline, array $split): array
    {
        $predictions = self::predict($pipeline, self::featuresTable($split['xTest']));
        $labels = $split['tTest'];
        $found = 0;

        foreach ($predictions as $index => $prediction) {
            if ($prediction === self::SURVIVED && $labels[$index] === self::SURVIVED) {
                ++$found;
            }
        }

        return [
            'trainAccuracy' => self::accuracy(
                self::predict($pipeline, self::featuresTable($split['xTrain'])),
                $split['tTrain'],
            ),
            'testAccuracy' => self::accuracy($predictions, $labels),
            'foundSurvivors' => $found,
            'survivors' => count(array_filter($labels, static fn (string $l): bool => $l === self::SURVIVED)),
        ];
    }

    // ---- 実行 ----

    /** クラスの重みごとの評価結果を返し、学習済みのパイプラインを保存して読み込む。 */
    public static function run(?string $path = null): string
    {
        $path ??= self::modelFile();
        $rows = Chapter02::loadTable(Dataset::path('Survived.csv'))->rows;
        $t = self::targetLabels($rows);
        $split = Chapter02::splitTrainTest($rows, $t, self::TEST_SIZE, self::SEED);
        $survivors = count(array_filter($t, static fn (string $l): bool => $l === self::SURVIVED));

        $lines = [
            sprintf('データ件数: %d（生存 %d, 死亡 %d）', count($rows), $survivors, count($t) - $survivors),
            sprintf('訓練データ: %d 件, テストデータ: %d 件', count($split['xTrain']), count($split['xTest'])),
        ];

        $pipelines = [];

        foreach (self::CLASS_WEIGHTS as $classWeight) {
            $pipeline = self::fit(
                self::featuresTable($split['xTrain']),
                $split['tTrain'],
                self::MAX_DEPTH,
                $classWeight,
            );
            $pipelines[$classWeight] = $pipeline;
            $result = self::evaluate($pipeline, $split);

            $lines[] = sprintf(
                'classWeight=%s: 訓練 %.3f, テスト %.3f, 生存者 %d 人中 %d 人を発見',
                $classWeight,
                $result['trainAccuracy'],
                $result['testAccuracy'],
                $result['survivors'],
                $result['foundSurvivors'],
            );
        }

        self::saveModel($pipelines[WeightedTree::BALANCED], $path);
        $loaded = self::loadModel($path);

        $lines[] = '保存したモデル: ' . ($loaded == $pipelines[WeightedTree::BALANCED] ? '読み込めました' : '読み込めません');
        $lines[] = '架空の乗客の予測: ' . implode(', ', self::predict($loaded, self::newPassengers()));

        return implode("\n", $lines) . "\n";
    }

    /** 年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性）。 */
    private static function newPassengers(): Table
    {
        return self::featuresTable([
            array_combine(self::FEATURE_COLUMNS, ['1', 'female', '', '0', '0', '50', 'C']),
            array_combine(self::FEATURE_COLUMNS, ['3', 'male', '', '0', '0', '8', 'S']),
        ]);
    }

    /**
     * @param list<string> $predictions
     * @param list<string> $labels
     */
    private static function accuracy(array $predictions, array $labels): float
    {
        $hits = 0;

        foreach ($predictions as $index => $prediction) {
            if ($prediction === $labels[$index]) {
                ++$hits;
            }
        }

        return $hits / count($labels);
    }
}
