<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter09\BostonSplit;
use GettingStartedMl\Chapter09\Standardizer;
use InvalidArgumentException;
use MathPHP\LinearAlgebra\MatrixFactory;
use MathPHP\LinearAlgebra\Vector;
use Rubix\ML\Datasets\Unlabeled;
use Rubix\ML\Transformers\ZScaleStandardizer;
use Throwable;

/**
 * 第 9 章: 特徴量エンジニアリング。
 *
 * ダミー変数・標準化・多項式特徴量・外れ値・表の結合。標準化は Rubix ML の
 * ZScaleStandardizer と突き合わせる。
 *
 * この章から Shift_JIS の CSV を読む。PHP では mbstring が標準で CP932 を持つので、
 * Elixir 版が codepagex を足したのと違い、依存を増やさずに読める。
 */
final class Chapter09
{
    /** ボストンの住宅価格のデータの正解の列。 */
    public const string TARGET = 'PRICE';

    /** カテゴリ値の列。 */
    public const string CATEGORY = 'CRIME';

    /** 表を結合する鍵。 */
    public const string JOIN_KEY = 'weather_id';

    /** 変換しない（バイト列をそのまま読む）指定。 */
    public const string UTF8 = 'UTF-8';

    /** Shift_JIS の Windows 拡張。mbstring が標準で持つ。 */
    public const string CP932 = 'CP932';

    /** 四分位範囲の何倍を外れ値とみなすか。 */
    public const float DEFAULT_K = 1.5;

    /** 多項式特徴量を作る元の列。 */
    public const array COLUMNS_TO_EXPAND = ['RM', 'LSTAT', 'PTRATIO'];

    /** 2 乗の項。 */
    public const array SQUARES = ['RM^2', 'LSTAT^2', 'PTRATIO^2'];

    /** 表示が -0.00 にならないように、ごく小さい値は 0 とみなす。 */
    private const float ZERO_TOLERANCE = 1.0e-9;

    // 9.4 カテゴリ値をダミー変数にする

    /**
     * 空欄を除いたカテゴリを辞書順に並べ、先頭を除いて返す。
     *
     * pandas の get_dummies(drop_first=True) と同じで、3 つのカテゴリなら 2 列で足りる。
     *
     * @param list<string> $values
     *
     * @return list<string>
     */
    public static function categories(array $values): array
    {
        $found = array_values(array_unique(array_filter(
            $values,
            static fn (string $v): bool => trim($v) !== '',
        )));
        sort($found);

        return array_slice($found, 1);
    }

    /**
     * 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。
     *
     * 値が一致すれば "1"、それ以外は "0"。セルを文字列のままにしておくと、
     * 第 2 章の splitFeaturesAndTarget・columnMeans・fillMissing をそのまま使える。
     *
     * @param list<string> $categories
     */
    public static function encode(Table $table, string $column, array $categories): Table
    {
        $dummyColumns = array_map(
            static fn (string $c): string => "{$column}_{$c}",
            $categories,
        );

        $rows = array_map(
            static function (array $row) use ($column, $categories, $dummyColumns): array {
                $value = Chapter02::text($row, $column);
                unset($row[$column]);

                foreach ($categories as $i => $category) {
                    $row[$dummyColumns[$i]] = $category === $value ? '1' : '0';
                }

                return $row;
            },
            $table->rows,
        );

        $columns = array_values(
            array_filter($table->columns, static fn (string $c): bool => $c !== $column),
        );

        return new Table([...$columns, ...$dummyColumns], $rows);
    }

    // 9.5 特徴量を標準化する

    /**
     * Rubix ML の ZScaleStandardizer で、訓練データの値から平均と標準偏差を求めて別の値を標準化する。
     *
     * 1 列だけの標本にして渡す。自作の Standardizer と同じ値になるかを確かめるために使う。
     *
     * @param list<float> $train
     * @param list<float> $values
     *
     * @return list<float>
     */
    public static function rubixStandardize(array $train, array $values): array
    {
        $standardizer = new ZScaleStandardizer(true);
        $standardizer->fit(Unlabeled::quick(array_map(static fn (float $v): array => [$v], $train)));

        $samples = array_map(static fn (float $v): array => [$v], $values);
        $standardizer->transform($samples);

        return array_map(
            static function (array $sample): float {
                $value = $sample[0];

                // Rubix ML の標本は mixed の配列なので、数値であることを確かめてから float にする。
                if (!is_int($value) && !is_float($value)) {
                    throw new InvalidArgumentException('標準化した値が数値ではありません');
                }

                return (float) $value;
            },
            $samples,
        );
    }

    // 9.6 多項式特徴量を作る

    /**
     * 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。
     *
     * @param list<string> $columns
     *
     * @return list<array{string, string}>
     */
    public static function pairsWithReplacement(array $columns): array
    {
        $pairs = [];

        foreach ($columns as $i => $left) {
            foreach (array_slice($columns, $i) as $right) {
                $pairs[] = [$left, $right];
            }
        }

        return $pairs;
    }

    /** 項の名前。scikit-learn の get_feature_names_out と同じ形（RM^2・RM LSTAT）にする。 */
    public static function termName(string $left, string $right): string
    {
        return $left === $right ? "{$left}^2" : "{$left} {$right}";
    }

    /**
     * 元の列の後ろに、2 乗の項と交互作用の項の列を並べた列名を返す。
     *
     * @param list<string> $columns
     *
     * @return list<string>
     */
    public static function expandedColumns(array $columns): array
    {
        $terms = array_map(
            static fn (array $pair): string => self::termName($pair[0], $pair[1]),
            self::pairsWithReplacement($columns),
        );

        return [...$columns, ...$terms];
    }

    /**
     * 指定した列と、その 2 乗の項・交互作用の項だけを持つ特徴量にする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<array<string, float>>
     */
    public static function expand(array $x, array $columns): array
    {
        $pairs = self::pairsWithReplacement($columns);

        return array_map(
            static function (array $features) use ($columns, $pairs): array {
                $expanded = [];

                foreach ($columns as $column) {
                    $expanded[$column] = self::featureValue($features, $column);
                }

                foreach ($pairs as [$left, $right]) {
                    $expanded[self::termName($left, $right)] =
                        self::featureValue($features, $left) * self::featureValue($features, $right);
                }

                return $expanded;
            },
            $x,
        );
    }

    /**
     * 指定した列だけを選ぶ。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<array<string, float>>
     */
    public static function selectColumns(array $x, array $columns): array
    {
        return array_map(
            static function (array $features) use ($columns): array {
                $selected = [];

                foreach ($columns as $column) {
                    $selected[$column] = self::featureValue($features, $column);
                }

                return $selected;
            },
            $x,
        );
    }

    // 9.7 外れ値を検出する

    /**
     * 分位数を求める。位置が値の間にあれば前後の値から線形補間する。
     *
     * pandas の quantile の既定（linear）と同じ。
     *
     * @param list<float> $values
     */
    public static function quantile(array $values, float $q): float
    {
        if ($values === []) {
            throw new InvalidArgumentException('値が 1 件もありません');
        }

        $sorted = $values;
        sort($sorted);

        $position = (count($sorted) - 1) * $q;
        $lower = (int) floor($position);
        $upper = (int) ceil($position);

        return $sorted[$lower] + ($sorted[$upper] - $sorted[$lower]) * ($position - $lower);
    }

    /**
     * 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。
     *
     * @param list<float> $values
     *
     * @return list<bool>
     */
    public static function iqrOutliers(array $values, float $k = self::DEFAULT_K): array
    {
        $q1 = self::quantile($values, 0.25);
        $q3 = self::quantile($values, 0.75);
        $iqr = $q3 - $q1;

        return array_map(
            static fn (float $v): bool => $v < $q1 - $k * $iqr || $v > $q3 + $k * $iqr,
            $values,
        );
    }

    /**
     * 訓練データから、正解の値が外れ値の行を取り除く。
     *
     * テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいてもそのまま残す。
     */
    public static function removeTargetOutliers(BostonSplit $split): BostonSplit
    {
        $outliers = self::iqrOutliers($split->tTrain);
        $xTrain = [];
        $tTrain = [];

        foreach ($split->tTrain as $i => $value) {
            if (!$outliers[$i]) {
                $xTrain[] = $split->xTrain[$i];
                $tTrain[] = $value;
            }
        }

        return new BostonSplit($split->columns, $xTrain, $split->xTest, $tTrain, $split->tTest);
    }

    // 9.8 表を結合して特徴量を増やす

    /** 文字列を CP932（Shift_JIS）のバイト列にする。テストでファイルを用意するために使う。 */
    public static function toCp932(string $text): string
    {
        return mb_convert_encoding($text, self::CP932, self::UTF8);
    }

    /**
     * 文字コードと区切り文字を指定して読み込み、1 行目を列名にする。
     *
     * UTF8 を渡すと何も変換せず、そのままのバイト列を読む。Shift_JIS のファイルを
     * UTF8 で読んでも例外にはならず、UTF-8 として不正な文字列がそのまま入る。
     */
    public static function loadDelimited(string $path, string $encoding, string $separator): Table
    {
        $contents = @file_get_contents($path);

        if ($contents === false) {
            throw new InvalidArgumentException("ファイルを開けません: {$path}");
        }

        $text = $contents;

        if ($encoding !== self::UTF8) {
            // 変換できない文字コード名を渡したときだけ false になる。
            $converted = mb_convert_encoding($contents, self::UTF8, $encoding);

            if (!is_string($converted)) {
                throw new InvalidArgumentException("文字コードを変換できません: {$encoding}");
            }

            $text = $converted;
        }

        return Csv::parseTable($text, $separator);
    }

    /**
     * 天気 ID をキーにした表を引いて、天気の列を加える（内部結合）。
     *
     * 天気の表に無い ID の行は残さない。キーが重複すると静かに上書きされるので、
     * 件数を比べて一意でないことに気付けるようにする。
     */
    public static function joinWeather(Table $bike, Table $weather): Table
    {
        $byId = [];

        foreach ($weather->rows as $row) {
            $byId[Chapter02::text($row, self::JOIN_KEY)] = $row;
        }

        if (count($byId) !== count($weather->rows)) {
            throw new InvalidArgumentException(self::JOIN_KEY . ' が一意ではありません');
        }

        $added = array_values(
            array_filter($weather->columns, static fn (string $c): bool => $c !== self::JOIN_KEY),
        );

        $rows = [];

        foreach ($bike->rows as $row) {
            $key = Chapter02::text($row, self::JOIN_KEY);

            if (!array_key_exists($key, $byId)) {
                continue;
            }

            foreach ($added as $column) {
                $row[$column] = Chapter02::text($byId[$key], $column);
            }

            $rows[] = $row;
        }

        return new Table([...$bike->columns, ...$added], $rows);
    }

    /**
     * 天気ごとの平均利用者数を、多い順に並べて返す。
     *
     * @return list<array{string, float}>
     */
    public static function meanCountByWeather(Table $joined): array
    {
        $counts = [];

        foreach ($joined->rows as $row) {
            $counts[Chapter02::text($row, 'weather')][] = (float) Chapter02::number($row, 'cnt');
        }

        $means = [];

        foreach ($counts as $weather => $values) {
            $means[] = [(string) $weather, array_sum($values) / count($values)];
        }

        usort($means, static fn (array $a, array $b): int => $b[1] <=> $a[1]);

        return $means;
    }

    // 9.9 特徴量の効果を測る

    /**
     * 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。
     *
     * 第 7 章の `LinearModel` をそのまま使う。この章は列名を持たない行列を扱うので、
     * 列名は呼ぶ側から受け取って値の並びと対応させる。
     *
     * @param list<list<float>> $rows
     * @param list<float>       $t
     * @param list<string>      $columns
     */
    public static function linearFit(array $rows, array $t, array $columns): LinearModel
    {
        if ($rows === []) {
            throw new InvalidArgumentException('特徴量が 1 件もありません');
        }

        $design = MatrixFactory::createNumeric(
            array_map(static fn (array $row): array => [1.0, ...$row], $rows),
        );
        $transposed = $design->transpose();

        try {
            /** @var Vector $solution */
            $solution = $transposed
                ->multiply($design)
                ->solve(new Vector($transposed->vectorMultiply(new Vector($t))->getVector()));
            /** @var list<float> $beta */
            $beta = $solution->getVector();
        } catch (Throwable $error) {
            throw new InvalidArgumentException(
                '特徴量の列が互いに独立でないため、正規方程式を解けません',
                0,
                $error,
            );
        }

        foreach ($beta as $value) {
            if (is_nan($value) || is_infinite($value)) {
                throw new InvalidArgumentException('特徴量の列が互いに独立でないため、正規方程式を解けません');
            }
        }

        return new LinearModel($beta[0], $columns, array_slice($beta, 1));
    }

    /**
     * 決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。
     *
     * @param list<float> $actual
     * @param list<float> $predicted
     */
    public static function rSquared(array $actual, array $predicted): float
    {
        if ($actual === []) {
            throw new InvalidArgumentException('正解の値がありません');
        }

        $mean = array_sum($actual) / count($actual);
        $residual = 0.0;
        $total = 0.0;

        foreach ($actual as $i => $value) {
            $residual += ($value - $predicted[$i]) ** 2;
            $total += ($value - $mean) ** 2;
        }

        return 1.0 - $residual / $total;
    }

    // ボストンの住宅価格

    /** CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。 */
    public static function prepareBoston(string $path, float $testSize, int $seed): BostonSplit
    {
        $table = Chapter02::loadTable($path);
        $crimes = array_map(
            static fn (array $row): string => Chapter02::text($row, self::CATEGORY),
            $table->rows,
        );
        $encoded = self::encode($table, self::CATEGORY, self::categories($crimes));

        [$columns, $rows, $labels] = Chapter02::splitFeaturesAndTarget($encoded, self::TARGET);
        $prices = array_map(
            static fn (string $label): float => (float) $label,
            $labels,
        );

        $split = Chapter02::splitTrainTest($rows, array_map(strval(...), $prices), $testSize, $seed);
        $means = Chapter02::columnMeans($split['xTrain'], $columns);

        return new BostonSplit(
            $columns,
            Chapter02::fillMissing($split['xTrain'], $columns, $means),
            Chapter02::fillMissing($split['xTest'], $columns, $means),
            array_map(floatval(...), $split['tTrain']),
            array_map(floatval(...), $split['tTest']),
        );
    }

    /**
     * 多項式特徴量を作って terms の項を選び、標準化してから線形回帰で学習し、決定係数を求める。
     *
     * 平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使う。
     *
     * @param list<string> $columns
     * @param list<string> $terms
     *
     * @return array{train: float, test: float}
     */
    public static function scoreFeatureSet(BostonSplit $split, array $columns, array $terms): array
    {
        $train = self::selectColumns(self::expand($split->xTrain, $columns), $terms);
        $test = self::selectColumns(self::expand($split->xTest, $columns), $terms);
        $standardizer = Standardizer::fit($train, $terms);
        $xTrain = Standardizer::toRows($standardizer->transformAll($train), $terms);
        $xTest = Standardizer::toRows($standardizer->transformAll($test), $terms);
        $model = self::linearFit($xTrain, $split->tTrain, $terms);

        return [
            'train' => self::rSquared($split->tTrain, $model->predictRows($xTrain)),
            'test' => self::rSquared($split->tTest, $model->predictRows($xTest)),
        ];
    }

    /**
     * 特徴量の組の名前と、使う項。表示する順に並べる。
     *
     * @return list<array{string, list<string>}>
     */
    public static function featureSets(): array
    {
        return [
            ['元の特徴量', self::COLUMNS_TO_EXPAND],
            ['2 乗の項を追加', [...self::COLUMNS_TO_EXPAND, ...self::SQUARES]],
            ['交互作用の項も追加', self::expandedColumns(self::COLUMNS_TO_EXPAND)],
        ];
    }

    /** ボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。 */
    public static function run(?string $dir = null): string
    {
        $dir ??= Dataset::dir();
        $split = self::prepareBoston($dir . '/Boston.csv', 0.3, 0);

        $standardized = Standardizer::fit($split->xTrain, $split->columns)->transformAll($split->xTrain);
        $check = Standardizer::fit($standardized, $split->columns);

        $lines = [
            sprintf('訓練データ: %d 件, テストデータ: %d 件', count($split->xTrain), count($split->xTest)),
            '特徴量の列: ' . implode(', ', $split->columns),
            sprintf(
                '標準化した訓練データの RM: 平均 %s, 標準偏差 %s',
                self::formatNumber($check->means['RM'], 2),
                self::formatNumber($check->stds['RM'], 2),
            ),
            '決定係数:',
        ];

        foreach (self::featureSets() as [$name, $terms]) {
            $scores = self::scoreFeatureSet($split, self::COLUMNS_TO_EXPAND, $terms);
            $lines[] = sprintf('  %s（%d 列）: %s', $name, count($terms), self::formatScores($scores));
        }

        $outliers = count(array_filter(self::iqrOutliers($split->tTrain)));
        $withoutOutliers = self::scoreFeatureSet(
            self::removeTargetOutliers($split),
            self::COLUMNS_TO_EXPAND,
            [...self::COLUMNS_TO_EXPAND, ...self::SQUARES],
        );

        $lines[] = sprintf('訓練データの PRICE の外れ値: %d 件', $outliers);
        $lines[] = '  外れ値を除いて 2 乗の項を追加: ' . self::formatScores($withoutOutliers);

        $joined = self::joinWeather(
            self::loadDelimited($dir . '/bike.tsv', self::UTF8, "\t"),
            self::loadDelimited($dir . '/weather.csv', self::CP932, ','),
        );
        $means = array_map(
            static fn (array $pair): string => sprintf('%s=%s', $pair[0], self::formatNumber($pair[1], 1)),
            self::meanCountByWeather($joined),
        );

        $lines[] = '天気ごとの平均利用者数: ' . implode(', ', $means);

        return implode("\n", $lines) . "\n";
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

    /** @param array{train: float, test: float} $scores */
    private static function formatScores(array $scores): string
    {
        return sprintf(
            '訓練 %s, テスト %s',
            self::formatNumber($scores['train'], 4),
            self::formatNumber($scores['test'], 4),
        );
    }

    /** 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。 */
    private static function formatNumber(float $value, int $digits): string
    {
        return sprintf("%.{$digits}f", abs($value) < self::ZERO_TOLERANCE ? 0.0 : $value);
    }
}
