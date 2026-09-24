<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter09;
use GettingStartedMl\Chapter09\Standardizer;
use GettingStartedMl\Dataset;
use GettingStartedMl\Table;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter09Test extends TestCase
{
    /** @var list<string> 後始末するファイル */
    private array $paths = [];

    /** 架空の中身のファイルを作る。学習データの行はテストに書かない。 */
    private function file(string $contents, string $suffix = '.csv'): string
    {
        $path = tempnam(sys_get_temp_dir(), 'ch09') . $suffix;
        file_put_contents($path, $contents);
        $this->paths[] = $path;

        return $path;
    }

    protected function tearDown(): void
    {
        foreach ($this->paths as $path) {
            @unlink($path);
        }

        $this->paths = [];
    }

    // 9.4 カテゴリ値をダミー変数にする

    #[TestDox('カテゴリは辞書順に並べて先頭を落とす')]
    public function testカテゴリは辞書順に並べて先頭を落とす(): void
    {
        $this->assertSame(
            ['low', 'very_low'],
            Chapter09::categories(['low', 'high', 'very_low', 'high']),
        );
    }

    #[TestDox('空欄はカテゴリに数えない')]
    public function test空欄はカテゴリに数えない(): void
    {
        $this->assertSame(['b'], Chapter09::categories(['a', '', 'b', ' ']));
    }

    #[TestDox('ダミー変数の列を末尾に加え、元の列を取り除く')]
    public function testダミー変数の列を末尾に加える(): void
    {
        $table = new Table(
            ['CRIME', 'RM'],
            [
                ['CRIME' => 'high', 'RM' => '6.0'],
                ['CRIME' => 'low', 'RM' => '5.0'],
            ],
        );

        $encoded = Chapter09::encode($table, 'CRIME', ['low']);

        $this->assertSame(['RM', 'CRIME_low'], $encoded->columns);
        $this->assertSame(
            [
                ['RM' => '6.0', 'CRIME_low' => '0'],
                ['RM' => '5.0', 'CRIME_low' => '1'],
            ],
            $encoded->rows,
        );
    }

    // 9.5 特徴量を標準化する

    #[TestDox('標準化した値は平均 0・標準偏差 1 になる')]
    public function test標準化した値は平均0標準偏差1になる(): void
    {
        $x = [['a' => 1.0], ['a' => 2.0], ['a' => 4.0], ['a' => 8.0]];
        $standardized = Standardizer::fit($x, ['a'])->transformAll($x);
        $again = Standardizer::fit($standardized, ['a']);

        $this->assertEqualsWithDelta(0.0, $again->means['a'], 1e-12);
        $this->assertEqualsWithDelta(1.0, $again->stds['a'], 1e-12);
    }

    #[TestDox('標準偏差は件数 n で割る母標準偏差を使う')]
    public function test標準偏差は件数で割る(): void
    {
        $x = [['a' => 1.0], ['a' => 2.0], ['a' => 4.0], ['a' => 8.0]];
        $std = Standardizer::fit($x, ['a']);

        // 平均 3.75、偏差平方和 28.75。n で割ると 2.6810、n-1 なら 3.0957
        $this->assertEqualsWithDelta(3.75, $std->means['a'], 1e-12);
        $this->assertEqualsWithDelta(sqrt(28.75 / 4), $std->stds['a'], 1e-12);
        $this->assertNotEqualsWithDelta(sqrt(28.75 / 3), $std->stds['a'], 1e-6);
    }

    #[TestDox('Rubix ML の ZScaleStandardizer も n で割る')]
    public function testRubixの標準化と一致する(): void
    {
        $values = [1.0, 2.0, 4.0, 8.0];
        $x = array_map(static fn (float $v): array => ['a' => $v], $values);
        $ours = array_column(Standardizer::fit($x, ['a'])->transformAll($x), 'a');

        $this->assertEqualsWithDelta($ours, Chapter09::rubixStandardize($values, $values), 1e-12);
    }

    #[TestDox('すべて同じ値の列は標準化すると 0 になる')]
    public function test同じ値の列は0になる(): void
    {
        $x = [['a' => 3.0], ['a' => 3.0]];

        $this->assertSame(
            [['a' => 0.0], ['a' => 0.0]],
            Standardizer::fit($x, ['a'])->transformAll($x),
        );
    }

    #[TestDox('平均と標準偏差を持たない列はそのまま残す')]
    public function test持たない列はそのまま残す(): void
    {
        $std = Standardizer::fit([['a' => 1.0], ['a' => 3.0]], ['a']);

        // 平均 2.0・標準偏差 1.0 なので a は 2.0 になり、b は触られない。
        $this->assertSame(['a' => 2.0, 'b' => 9.0], $std->transform(['a' => 4.0, 'b' => 9.0]));
    }

    #[TestDox('1 件もない特徴量は標準化できない')]
    public function test1件もない特徴量は標準化できない(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Standardizer::fit([], ['a']);
    }

    // 9.6 多項式特徴量を作る

    #[TestDox('重複を許して 2 つの列を選ぶ組を作る')]
    public function test2つの列を選ぶ組を作る(): void
    {
        $this->assertSame(
            [['a', 'a'], ['a', 'b'], ['b', 'b']],
            Chapter09::pairsWithReplacement(['a', 'b']),
        );
    }

    #[TestDox('項の名前は 2 乗なら ^2、違う列なら空白でつなぐ')]
    public function test項の名前(): void
    {
        $this->assertSame('a^2', Chapter09::termName('a', 'a'));
        $this->assertSame('a b', Chapter09::termName('a', 'b'));
    }

    #[TestDox('元の列の後ろに 2 乗の項と交互作用の項を並べる')]
    public function test展開した列名(): void
    {
        $this->assertSame(
            ['a', 'b', 'a^2', 'a b', 'b^2'],
            Chapter09::expandedColumns(['a', 'b']),
        );
    }

    #[TestDox('多項式特徴量は元の列と積の項を持つ')]
    public function test多項式特徴量(): void
    {
        $expanded = Chapter09::expand([['a' => 2.0, 'b' => 3.0, 'c' => 9.0]], ['a', 'b']);

        $this->assertSame(
            [['a' => 2.0, 'b' => 3.0, 'a^2' => 4.0, 'a b' => 6.0, 'b^2' => 9.0]],
            $expanded,
        );
    }

    #[TestDox('指定した列だけを選ぶ')]
    public function test指定した列だけを選ぶ(): void
    {
        $this->assertSame(
            [['b' => 3.0]],
            Chapter09::selectColumns([['a' => 2.0, 'b' => 3.0]], ['b']),
        );
    }

    // 9.7 外れ値を検出する

    #[TestDox('分位数は値の間なら線形補間する')]
    public function test分位数は線形補間する(): void
    {
        $values = [1.0, 2.0, 3.0, 4.0];

        $this->assertEqualsWithDelta(1.75, Chapter09::quantile($values, 0.25), 1e-12);
        $this->assertEqualsWithDelta(2.5, Chapter09::quantile($values, 0.5), 1e-12);
        $this->assertEqualsWithDelta(3.25, Chapter09::quantile($values, 0.75), 1e-12);
    }

    #[TestDox('四分位範囲の 1.5 倍より外れた値を外れ値とする')]
    public function test外れ値を検出する(): void
    {
        $values = [1.0, 2.0, 3.0, 4.0, 100.0];

        $this->assertSame([false, false, false, false, true], Chapter09::iqrOutliers($values));
    }

    #[TestDox('訓練データからだけ外れ値の行を取り除く')]
    public function test訓練データからだけ外れ値を取り除く(): void
    {
        $split = new Chapter09\BostonSplit(
            ['a'],
            [['a' => 1.0], ['a' => 2.0], ['a' => 3.0], ['a' => 4.0], ['a' => 5.0]],
            [['a' => 9.0]],
            [1.0, 2.0, 3.0, 4.0, 100.0],
            [100.0],
        );

        $removed = Chapter09::removeTargetOutliers($split);

        $this->assertCount(4, $removed->xTrain);
        $this->assertSame([1.0, 2.0, 3.0, 4.0], $removed->tTrain);
        $this->assertSame([100.0], $removed->tTest);
    }

    // 9.8 表を結合して特徴量を増やす

    #[TestDox('タブ区切りの表を読める')]
    public function testタブ区切りの表を読める(): void
    {
        $path = $this->file("id\tcnt\n1\t10\n", '.tsv');
        $table = Chapter09::loadDelimited($path, Chapter09::UTF8, "\t");

        $this->assertSame(['id', 'cnt'], $table->columns);
        $this->assertSame([['id' => '1', 'cnt' => '10']], $table->rows);
    }

    #[TestDox('Shift_JIS の表を CP932 として読める')]
    public function testShiftJISの表を読める(): void
    {
        $path = $this->file(Chapter09::toCp932("天気\n晴れ\n"));
        $table = Chapter09::loadDelimited($path, Chapter09::CP932, ',');

        $this->assertSame(['天気'], $table->columns);
        $this->assertSame([['天気' => '晴れ']], $table->rows);
    }

    #[TestDox('Shift_JIS を UTF-8 として読むと例外にならず不正なバイト列が残る')]
    public function testShiftJISをUTF8として読む(): void
    {
        $path = $this->file(Chapter09::toCp932("天気\n晴れ\n"));
        $table = Chapter09::loadDelimited($path, Chapter09::UTF8, ',');

        // 例外も置換文字も出ない。UTF-8 として不正なままの文字列が入る。
        $this->assertFalse(mb_check_encoding($table->columns[0], 'UTF-8'));
        $this->assertSame("\x93\x56\x8b\x43", $table->columns[0]);
    }

    #[TestDox('UTF-8 かどうかを読み込む前に判定できる')]
    public function testUTF8かどうかを判定できる(): void
    {
        $this->assertFalse(mb_check_encoding(Chapter09::toCp932('天気'), 'UTF-8'));
        $this->assertTrue(mb_check_encoding('天気', 'UTF-8'));
    }

    #[TestDox('UTF-8 を CP932 として読むと例外にならず文字化けする')]
    public function testUTF8をCP932として読む(): void
    {
        $path = $this->file("天気\n晴れ\n");
        $table = Chapter09::loadDelimited($path, Chapter09::CP932, ',');

        // 変換は通る。中身は別の文字に化けているだけで、UTF-8 としては正しい。
        $this->assertTrue(mb_check_encoding($table->columns[0], 'UTF-8'));
        $this->assertNotSame('天気', $table->columns[0]);
    }

    #[TestDox('天気の表を内部結合して列を加える')]
    public function test天気の表を結合する(): void
    {
        $bike = new Table(
            ['weather_id', 'cnt'],
            [
                ['weather_id' => '1', 'cnt' => '10'],
                ['weather_id' => '2', 'cnt' => '20'],
                ['weather_id' => '9', 'cnt' => '30'],
            ],
        );
        $weather = new Table(
            ['weather_id', 'weather'],
            [
                ['weather_id' => '1', 'weather' => '晴れ'],
                ['weather_id' => '2', 'weather' => '雨'],
            ],
        );

        $joined = Chapter09::joinWeather($bike, $weather);

        // 天気の表に無い 9 の行は残らない。
        $this->assertSame(['weather_id', 'cnt', 'weather'], $joined->columns);
        $this->assertCount(2, $joined->rows);
        $this->assertSame('晴れ', $joined->rows[0]['weather']);
    }

    #[TestDox('結合の鍵が一意でなければ失敗する')]
    public function test結合の鍵が一意でなければ失敗する(): void
    {
        $weather = new Table(
            ['weather_id', 'weather'],
            [
                ['weather_id' => '1', 'weather' => '晴れ'],
                ['weather_id' => '1', 'weather' => '雨'],
            ],
        );

        $this->expectException(InvalidArgumentException::class);

        Chapter09::joinWeather(new Table(['weather_id'], []), $weather);
    }

    #[TestDox('天気ごとの平均利用者数を多い順に並べる')]
    public function test天気ごとの平均利用者数(): void
    {
        $joined = new Table(
            ['weather', 'cnt'],
            [
                ['weather' => '雨', 'cnt' => '10'],
                ['weather' => '晴れ', 'cnt' => '30'],
                ['weather' => '晴れ', 'cnt' => '50'],
            ],
        );

        $this->assertSame(
            [['晴れ', 40.0], ['雨', 10.0]],
            Chapter09::meanCountByWeather($joined),
        );
    }

    // 9.9 特徴量の効果を測る

    #[TestDox('線形回帰は完全に当てはまる直線を復元する')]
    public function test線形回帰は直線を復元する(): void
    {
        // t = 1 + 2a + 3b
        $rows = [[0.0, 0.0], [1.0, 0.0], [0.0, 1.0], [1.0, 1.0]];
        $t = [1.0, 3.0, 4.0, 6.0];

        $model = Chapter09::linearFit($rows, $t, ['a', 'b']);

        $this->assertEqualsWithDelta(1.0, $model->intercept, 1e-9);
        $this->assertEqualsWithDelta([2.0, 3.0], $model->coefficients, 1e-9);
        $this->assertEqualsWithDelta($t, $model->predictRows($rows), 1e-9);
        // 第 7 章の LinearModel をそのまま使うので、列名で係数を読むこともできる。
        $this->assertEqualsWithDelta(2.0, $model->coefficient('a'), 1e-9);
    }

    #[TestDox('互いに独立でない列があれば正規方程式を解けない')]
    public function test独立でない列は解けない(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter09::linearFit([[1.0, 2.0], [2.0, 4.0]], [1.0, 2.0], ['a', 'b']);
    }

    #[TestDox('完全に当てた予測の決定係数は 1')]
    public function test決定係数は1(): void
    {
        $this->assertEqualsWithDelta(1.0, Chapter09::rSquared([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]), 1e-12);
    }

    #[TestDox('平均を答えるだけの予測の決定係数は 0')]
    public function test決定係数は0(): void
    {
        $this->assertEqualsWithDelta(0.0, Chapter09::rSquared([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 1e-12);
    }

    // 実データ

    #[Group('data')]
    #[TestDox('実データの決定係数がほかの言語版と一致する')]
    public function test実データの決定係数が一致する(): void
    {
        if (!Dataset::exists('Boston.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('Boston.csv'));
        }

        $split = Chapter09::prepareBoston(Dataset::path('Boston.csv'), 0.3, 0);

        $this->assertCount(70, $split->xTrain);
        $this->assertCount(30, $split->xTest);

        // Java 版・Scala 版・Clojure 版・Elixir 版と一致する。
        $scores = Chapter09::scoreFeatureSet($split, Chapter09::COLUMNS_TO_EXPAND, Chapter09::COLUMNS_TO_EXPAND);

        $this->assertEqualsWithDelta(0.6056, $scores['train'], 5e-5);
        $this->assertEqualsWithDelta(0.6950, $scores['test'], 5e-5);
    }

    #[Group('data')]
    #[TestDox('実データの特徴量エンジニアリングの結果を表示する')]
    public function test実データの結果を表示する(): void
    {
        if (!Dataset::exists('Boston.csv') || !Dataset::exists('weather.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::dir());
        }

        $output = Chapter09::run();

        $this->assertStringContainsString('訓練データ: 70 件, テストデータ: 30 件', $output);
        $this->assertStringContainsString('標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00', $output);
        $this->assertStringContainsString('2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628', $output);
        $this->assertStringContainsString('訓練データの PRICE の外れ値: 8 件', $output);
        $this->assertStringContainsString('天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3', $output);
    }
}
