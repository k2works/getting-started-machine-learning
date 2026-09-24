<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Branch;
use GettingStartedMl\Chapter08;
use GettingStartedMl\Csv;
use GettingStartedMl\Dataset;
use GettingStartedMl\Leaf;
use GettingStartedMl\Preprocessing\DummyEncoder;
use GettingStartedMl\Preprocessing\GroupMedianImputer;
use GettingStartedMl\Preprocessing\MostFrequentImputer;
use GettingStartedMl\Table;
use GettingStartedMl\WeightedTree;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter08Test extends TestCase
{
    /** @var list<string> 後始末するファイル */
    private array $paths = [];

    protected function tearDown(): void
    {
        foreach ($this->paths as $path) {
            @unlink($path);
        }

        $this->paths = [];
    }

    private function tempFile(string $suffix): string
    {
        $path = tempnam(sys_get_temp_dir(), 'ch08') . $suffix;
        $this->paths[] = $path;

        return $path;
    }

    /** 架空の乗客 6 人。年齢と港に空欄がある。 */
    private function passengers(): Table
    {
        return Csv::parseTable(
            "Pclass,Sex,Age,Embarked,Fare\n"
            . "1,female,30,S,100\n"
            . "1,female,40,C,120\n"
            . "1,female,,S,110\n"
            . "3,male,20,S,10\n"
            . "3,male,24,,12\n"
            . "3,male,,S,8\n",
        );
    }

    // ---- 中央値 ----

    #[TestDox('件数が奇数なら中央値は真ん中の値')]
    public function test件数が奇数なら中央値は真ん中の値(): void
    {
        $this->assertSame(3.0, Chapter08::median([5.0, 1.0, 3.0]));
    }

    #[TestDox('件数が偶数なら中央値は真ん中の 2 つの平均')]
    public function test件数が偶数なら中央値は真ん中の2つの平均(): void
    {
        $this->assertSame(2.5, Chapter08::median([4.0, 1.0, 3.0, 2.0]));
    }

    #[TestDox('値が無ければ中央値を求められない')]
    public function test値が無ければ中央値を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('値がありません');

        Chapter08::median([]);
    }

    // ---- 前処理 ----

    #[TestDox('年齢はグループごとの中央値で補完する')]
    public function test年齢はグループごとの中央値で補完する(): void
    {
        $fitted = (new GroupMedianImputer('Age', ['Pclass', 'Sex']))->fit($this->passengers());
        $filled = $fitted->apply($this->passengers());

        // 1 等・女性は 30 と 40 なので 35、3 等・男性は 20 と 24 なので 22。
        $this->assertSame('35', $filled->rows[2]['Age']);
        $this->assertSame('22', $filled->rows[5]['Age']);
    }

    #[TestDox('知らないグループは全体の中央値で補完する')]
    public function test知らないグループは全体の中央値で補完する(): void
    {
        $fitted = (new GroupMedianImputer('Age', ['Pclass', 'Sex']))->fit($this->passengers());
        $other = Csv::parseTable("Pclass,Sex,Age,Embarked,Fare\n2,male,,S,50\n");

        // 学習に無いグループなので、全体の中央値（20, 24, 30, 40 の中央＝27）を使う。
        $this->assertSame('27', $fitted->apply($other)->rows[0]['Age']);
    }

    #[TestDox('学習していない前処理は使えない')]
    public function test学習していない前処理は使えない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('学習していません: Age');

        (new GroupMedianImputer('Age', ['Pclass']))->apply($this->passengers());
    }

    #[TestDox('乗船した港は最頻値で補完する')]
    public function test乗船した港は最頻値で補完する(): void
    {
        $fitted = (new MostFrequentImputer('Embarked'))->fit($this->passengers());

        $this->assertSame('S', $fitted->apply($this->passengers())->rows[4]['Embarked']);
    }

    #[TestDox('最頻値が同数なら値の順で前のものを選ぶ')]
    public function test最頻値が同数なら値の順で前のものを選ぶ(): void
    {
        // S と C が 1 件ずつ。値の順で前の C を選ぶ。
        $table = Csv::parseTable("Embarked,Fare\nS,10\nC,20\n,30\n");
        $fitted = (new MostFrequentImputer('Embarked'))->fit($table);

        $this->assertSame('C', $fitted->apply($table)->rows[2]['Embarked']);
    }

    #[TestDox('ダミー変数化は基準の列を落とす')]
    public function testダミー変数化は基準の列を落とす(): void
    {
        $fitted = (new DummyEncoder(['Sex', 'Embarked']))->fit($this->passengers());
        $encoded = $fitted->apply($this->passengers());

        // female/male のうち基準の female を落とし、C/S のうち基準の C を落とす。
        $this->assertSame(['Pclass', 'Age', 'Fare', 'Sex_male', 'Embarked_S'], $encoded->columns);
        $this->assertSame('0', $encoded->rows[0]['Sex_male']);
        $this->assertSame('1', $encoded->rows[0]['Embarked_S']);
        $this->assertSame('1', $encoded->rows[3]['Sex_male']);
    }

    #[TestDox('学習していないカテゴリはすべて 0 になる')]
    public function test学習していないカテゴリはすべて0になる(): void
    {
        $fitted = (new DummyEncoder(['Embarked']))->fit($this->passengers());
        $other = Csv::parseTable("Pclass,Sex,Age,Embarked,Fare\n2,male,30,Q,50\n");

        $this->assertSame('0', $fitted->apply($other)->rows[0]['Embarked_S']);
    }

    // ---- 重み付きの決定木 ----

    #[TestDox('すべて同じラベルならジニ不純度は 0')]
    public function testすべて同じラベルならジニ不純度は0(): void
    {
        $this->assertEqualsWithDelta(0.0, WeightedTree::gini(['1', '1'], [1.0, 1.0]), 1e-12);
    }

    #[TestDox('半々ならジニ不純度は 0.5')]
    public function test半々ならジニ不純度は05(): void
    {
        $this->assertEqualsWithDelta(0.5, WeightedTree::gini(['1', '0'], [1.0, 1.0]), 1e-12);
    }

    #[TestDox('重みを変えると不純度が変わる')]
    public function test重みを変えると不純度が変わる(): void
    {
        // 重み 3 対 1 なら割合は 0.75 と 0.25 で、1 - (0.5625 + 0.0625) = 0.375。
        $this->assertEqualsWithDelta(0.375, WeightedTree::gini(['1', '0'], [3.0, 1.0]), 1e-12);
    }

    #[TestDox('balanced の重みは件数に反比例する')]
    public function testBalancedの重みは件数に反比例する(): void
    {
        // 3 件中、1 が 1 件・0 が 2 件。クラスは 2 つなので 3/(2*1)=1.5 と 3/(2*2)=0.75。
        $this->assertEqualsWithDelta(
            [1.5, 0.75, 0.75],
            WeightedTree::balancedWeights(['1', '0', '0']),
            1e-12,
        );
    }

    #[TestDox('重み付けなしならすべて 1')]
    public function test重み付けなしならすべて1(): void
    {
        $this->assertSame([1.0, 1.0], WeightedTree::weightsOf(['1', '0'], WeightedTree::NONE));
    }

    #[TestDox('知らない重みの付け方は使えない')]
    public function test知らない重みの付け方は使えない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('知らない重みの付け方です: heavy');

        WeightedTree::weightsOf(['1'], 'heavy');
    }

    #[TestDox('多数決は重みの合計で決め、同じなら先に現れたラベルを選ぶ')]
    public function test多数決は重みの合計で決める(): void
    {
        $this->assertSame('0', WeightedTree::majority(['1', '0', '0'], [1.0, 1.0, 1.0]));
        $this->assertSame('1', WeightedTree::majority(['1', '0', '0'], [3.0, 1.0, 1.0]));
        // 合計が同じなら先に現れた 1 を選ぶ。
        $this->assertSame('1', WeightedTree::majority(['1', '0'], [1.0, 1.0]));
    }

    #[TestDox('分けられなければ葉になる')]
    public function test分けられなければ葉になる(): void
    {
        $tree = WeightedTree::fit([['a' => 1.0], ['a' => 1.0]], ['1', '0'], ['a'], 3, WeightedTree::NONE);

        $this->assertInstanceOf(Leaf::class, $tree);
        $this->assertSame('1', $tree->label);
    }

    #[TestDox('深さの上限が 0 なら分割しない')]
    public function test深さの上限が0なら分割しない(): void
    {
        $tree = WeightedTree::fit([['a' => 1.0], ['a' => 2.0]], ['0', '1'], ['a'], 0, WeightedTree::NONE);

        $this->assertInstanceOf(Leaf::class, $tree);
    }

    #[TestDox('分割の境界は隣り合う値の中点')]
    public function test分割の境界は隣り合う値の中点(): void
    {
        $tree = WeightedTree::fit(
            [['a' => 1.0], ['a' => 3.0]],
            ['0', '1'],
            ['a'],
            1,
            WeightedTree::NONE,
        );

        $this->assertInstanceOf(Branch::class, $tree);
        $this->assertSame('a', $tree->feature);
        $this->assertSame(2.0, $tree->threshold);
        $this->assertSame(['0', '1'], WeightedTree::predict($tree, [['a' => 1.0], ['a' => 3.0]]));
    }

    #[TestDox('訓練データが空なら木を作れない')]
    public function test訓練データが空なら木を作れない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('訓練データが空です');

        WeightedTree::fit([], [], ['a'], 3, WeightedTree::NONE);
    }

    #[TestDox('balanced にすると少数派が選ばれるようになる')]
    public function testBalancedにすると少数派が選ばれる(): void
    {
        // 1 が 1 件、0 が 3 件。重み付けなしなら葉は 0、balanced なら重みが 2.0 対 0.666… で 1。
        $x = [['a' => 1.0], ['a' => 1.0], ['a' => 1.0], ['a' => 1.0]];
        $t = ['1', '0', '0', '0'];

        $none = WeightedTree::fit($x, $t, ['a'], 3, WeightedTree::NONE);
        $balanced = WeightedTree::fit($x, $t, ['a'], 3, WeightedTree::BALANCED);

        $this->assertSame(['0'], WeightedTree::predict($none, [['a' => 1.0]]));
        $this->assertSame(['1'], WeightedTree::predict($balanced, [['a' => 1.0]]));
    }

    // ---- パイプライン ----

    /** @return list<array<string, string>> */
    private function survivedRows(): array
    {
        return Csv::parseTable(
            "Pclass,Sex,Age,SibSp,Parch,Fare,Embarked,Survived\n"
            . "1,female,30,0,0,100,S,1\n"
            . "1,female,,1,0,120,C,1\n"
            . "3,male,20,0,0,10,S,0\n"
            . "3,male,24,0,0,12,,0\n"
            . "3,male,,0,0,8,S,0\n"
            . "1,female,40,1,0,110,S,1\n",
        )->rows;
    }

    #[TestDox('欠損値を含むデータで学習して予測できる')]
    public function test欠損値を含むデータで学習して予測できる(): void
    {
        $rows = $this->survivedRows();
        $pipeline = Chapter08::fit(Chapter08::featuresTable($rows), Chapter08::targetLabels($rows), 5, WeightedTree::NONE);

        $this->assertSame(
            ['1', '1', '0', '0', '0', '1'],
            Chapter08::predict($pipeline, Chapter08::featuresTable($rows)),
        );
    }

    #[TestDox('前処理をすると欠損値が残らない')]
    public function test前処理をすると欠損値が残らない(): void
    {
        $rows = $this->survivedRows();
        $pipeline = Chapter08::fit(Chapter08::featuresTable($rows), Chapter08::targetLabels($rows), 5, WeightedTree::NONE);
        $features = Chapter08::features($pipeline, Chapter08::featuresTable($rows));

        $this->assertCount(6, $features);

        foreach ($features as $row) {
            foreach ($pipeline->columns as $column) {
                $this->assertTrue(is_finite($row[$column]), "欠損値が残っています: {$column}");
            }
        }
    }

    #[TestDox('欠損値が残っていれば特徴量にできない')]
    public function test欠損値が残っていれば特徴量にできない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('欠損値が残っています: Age');

        Chapter08::toFeatures(new Table(['Age'], [['Age' => '']]));
    }

    #[TestDox('正解ラベルは Survived 列から取る')]
    public function test正解ラベルはSurvived列から取る(): void
    {
        $this->assertSame(['1', '1', '0', '0', '0', '1'], Chapter08::targetLabels($this->survivedRows()));
    }

    // ---- 保存と読み込み ----

    #[TestDox('学習済みのパイプラインは保存して復元できる')]
    public function test学習済みのパイプラインは保存して復元できる(): void
    {
        $rows = $this->survivedRows();
        $pipeline = Chapter08::fit(Chapter08::featuresTable($rows), Chapter08::targetLabels($rows), 5, WeightedTree::BALANCED);
        $path = $this->tempFile('.model');

        Chapter08::saveModel($pipeline, $path);
        $loaded = Chapter08::loadModel($path);

        $this->assertEquals($pipeline, $loaded);
        $this->assertSame(
            Chapter08::predict($pipeline, Chapter08::featuresTable($rows)),
            Chapter08::predict($loaded, Chapter08::featuresTable($rows)),
        );
    }

    #[TestDox('形式の版が違うモデルは読み込めない')]
    public function test形式の版が違うモデルは読み込めない(): void
    {
        $path = $this->tempFile('.model');
        file_put_contents($path, serialize(['format' => 99, 'pipeline' => null]));

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('対応していない形式のモデルです: 99');

        Chapter08::loadModel($path);
    }

    #[TestDox('モデルでないファイルは読み込めない')]
    public function testモデルでないファイルは読み込めない(): void
    {
        $path = $this->tempFile('.model');
        file_put_contents($path, 'これはモデルではありません');

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('モデルとして読めません');

        Chapter08::loadModel($path);
    }

    // ---- Rubix ML との突き合わせ ----

    #[TestDox('MissingDataImputer は列ごとに 1 つの値しか持てない')]
    public function testMissingDataImputerは列ごとに1つの値しか持てない(): void
    {
        $mine = (new GroupMedianImputer('Age', ['Pclass', 'Sex']))->fit($this->passengers());
        $theirs = Chapter08::rubixImputeAge($this->passengers());

        // 自作はグループごとに 35 と 22 を使い分けるが、Rubix ML はどちらの行も同じ値になる。
        $filled = $mine->apply($this->passengers());

        $this->assertNotSame($filled->rows[2]['Age'], $filled->rows[5]['Age']);
        $this->assertSame($theirs[2], $theirs[5]);
        $this->assertEqualsWithDelta(27.0, $theirs[2], 1e-12);
    }

    #[TestDox('OneHotEncoder は基準の列を落とさない')]
    public function testOneHotEncoderは基準の列を落とさない(): void
    {
        $mine = (new DummyEncoder(['Sex']))->fit($this->passengers())->apply($this->passengers());
        $theirs = Chapter08::rubixOneHotSex($this->passengers());

        // 自作は列が 1 本（Sex_male）、Rubix ML は 2 本（female と male）になる。
        $this->assertSame(1, count(array_filter($mine->columns, static fn (string $c): bool => str_starts_with($c, 'Sex_'))));
        $this->assertCount(2, $theirs[0]);
        // 値そのものは対応する。先頭の行は female なので [1, 0]。
        $this->assertSame([1, 0], $theirs[0]);
        $this->assertSame([0, 1], $theirs[3]);
    }

    // ---- 実データ ----

    #[Group('data')]
    #[TestDox('実データの評価がほかの言語版と一致する')]
    public function test実データの評価が一致する(): void
    {
        if (!Dataset::exists('Survived.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('Survived.csv'));
        }

        $output = Chapter08::run($this->tempFile('.model'));

        $this->assertStringContainsString('データ件数: 891（生存 342, 死亡 549）', $output);
        $this->assertStringContainsString('訓練データ: 712 件, テストデータ: 179 件', $output);
        $this->assertStringContainsString(
            'classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見',
            $output,
        );
        $this->assertStringContainsString(
            'classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見',
            $output,
        );
        $this->assertStringContainsString('保存したモデル: 読み込めました', $output);
        $this->assertStringContainsString('架空の乗客の予測: 1, 0', $output);
    }

    #[Group('data')]
    #[TestDox('深さ 2 では balanced にすると見つかる生存者が増える')]
    public function test深さ2ではbalancedで生存者が増える(): void
    {
        if (!Dataset::exists('Survived.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('Survived.csv'));
        }

        $split = Chapter08::survivedSplit();

        $this->assertSame(41, Chapter08::evaluate(
            Chapter08::fit(Chapter08::featuresTable($split['xTrain']), $split['tTrain'], 2, WeightedTree::NONE),
            $split,
        )['foundSurvivors']);
        $this->assertSame(68, Chapter08::evaluate(
            Chapter08::fit(Chapter08::featuresTable($split['xTrain']), $split['tTrain'], 2, WeightedTree::BALANCED),
            $split,
        )['foundSurvivors']);
    }

    #[TestDox('保存先の既定はモデルのファイル')]
    public function test保存先の既定はモデルのファイル(): void
    {
        $this->assertSame('model/survived.model', Chapter08::modelFile());
    }

    #[TestDox('ディレクトリを作れなければ保存できない')]
    public function testディレクトリを作れなければ保存できない(): void
    {
        $rows = $this->survivedRows();
        $pipeline = Chapter08::fit(Chapter08::featuresTable($rows), Chapter08::targetLabels($rows), 1, WeightedTree::NONE);
        // ファイルの下にはディレクトリを作れない。
        $blocker = $this->tempFile('.blocker');
        file_put_contents($blocker, 'x');

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('ディレクトリを作れません');

        Chapter08::saveModel($pipeline, $blocker . '/model/survived.model');
    }

    #[TestDox('無いファイルはモデルとして開けない')]
    public function test無いファイルはモデルとして開けない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('モデルを開けません');

        Chapter08::loadModel(sys_get_temp_dir() . '/ここには無いはずのファイル.model');
    }

    #[TestDox('パイプラインが入っていないファイルは読み込めない')]
    public function testパイプラインが入っていないファイルは読み込めない(): void
    {
        $path = $this->tempFile('.model');
        file_put_contents($path, serialize(['format' => Chapter08::FORMAT_VERSION, 'pipeline' => 'ただの文字列']));

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('モデルとして読めません');

        Chapter08::loadModel($path);
    }

    #[TestDox('ラベルが無ければ多数決を取れない')]
    public function testラベルが無ければ多数決を取れない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('ラベルがありません');

        WeightedTree::majority([], []);
    }

    #[TestDox('特徴量とラベルの件数が違えば木を作れない')]
    public function test特徴量とラベルの件数が違えば木を作れない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('件数が違います: 1 と 2');

        WeightedTree::fit([['a' => 1.0]], ['0', '1'], ['a'], 1, WeightedTree::NONE);
    }

    #[TestDox('ラベルと重みの件数が違えば不純度を求められない')]
    public function testラベルと重みの件数が違えば不純度を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('件数が違います: 2 と 1');

        WeightedTree::gini(['0', '1'], [1.0]);
    }

    #[TestDox('特徴量に無い列では分割を探せない')]
    public function test特徴量に無い列では分割を探せない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量がありません: b');

        WeightedTree::bestSplit([['a' => 1.0], ['a' => 2.0]], ['0', '1'], [1.0, 1.0], ['b']);
    }

    #[TestDox('分岐は知らない特徴量では進めない')]
    public function test分岐は知らない特徴量では進めない(): void
    {
        $branch = new Branch('a', 1.0, new Leaf('0'), new Leaf('1'));

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量がありません: a');

        $branch->predictOne(['b' => 1.0]);
    }

    #[TestDox('値がすべて空欄なら最頻値を求められない')]
    public function test値がすべて空欄なら最頻値を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('値がすべて空欄です: Embarked');

        (new MostFrequentImputer('Embarked'))->fit(Csv::parseTable("Embarked,Fare\n,10\n,20\n"));
    }

    #[TestDox('学習していない最頻値の補完は使えない')]
    public function test学習していない最頻値の補完は使えない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('学習していません: Embarked');

        (new MostFrequentImputer('Embarked'))->apply($this->passengers());
    }

    #[TestDox('学習していないダミー変数化は使えない')]
    public function test学習していないダミー変数化は使えない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('学習していません: Sex');

        (new DummyEncoder(['Sex']))->apply($this->passengers());
    }
}
