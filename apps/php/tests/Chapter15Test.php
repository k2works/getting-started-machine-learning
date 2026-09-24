<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter07;
use GettingStartedMl\Chapter08;
use GettingStartedMl\Chapter15;
use GettingStartedMl\Chapter15\Api;
use GettingStartedMl\Chapter15\Domain;
use GettingStartedMl\Chapter15\FileStore;
use GettingStartedMl\Chapter15\LinearSalesModel;
use GettingStartedMl\Chapter15\ModelNotFoundException;
use GettingStartedMl\Chapter15\Request;
use GettingStartedMl\Chapter15\Response;
use GettingStartedMl\Chapter15\Service;
use GettingStartedMl\Chapter15\Validation;
use GettingStartedMl\Chapter15\ValidationException;
use GettingStartedMl\Csv;
use GettingStartedMl\Dataset;
use GettingStartedMl\LinearModel;
use GettingStartedMl\WeightedTree;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter15Test extends TestCase
{
    /** ほかの言語版と突き合わせる興行収入の予測値（Java 版・Scala 版・Clojure 版の値）。 */
    private const float JVM_SALES = 7730.457421687023;

    /** PHP 版の予測値。正規方程式を解く実装が違うので、最後の 2 桁だけほかの言語版と違う。 */
    private const float PHP_SALES = 7730.457421687019;

    /** @var list<string> 後始末するディレクトリ */
    private array $dirs = [];

    protected function tearDown(): void
    {
        foreach ($this->dirs as $dir) {
            foreach (glob($dir . '/*') ?: [] as $file) {
                @unlink($file);
            }

            @rmdir($dir);
        }

        $this->dirs = [];
    }

    private function tempDir(string $name): string
    {
        $dir = sys_get_temp_dir() . '/ch15-' . $name . '-' . bin2hex(random_bytes(6));
        $this->dirs[] = $dir;

        return $dir;
    }

    /** 作り物の線形回帰のモデル。実データの係数は使わない。 */
    private function fakeLinearModel(): LinearModel
    {
        return new LinearModel(1000.0, Chapter07::FEATURE_COLUMNS, [1.0, 2.0, 3.0, 0.5]);
    }

    /** 女性が生存し、男性が死亡する作り物の乗客 4 人で学習したパイプライン。 */
    private function fakePipeline(): \GettingStartedMl\FittedPipeline
    {
        $table = Csv::parseTable(
            "Pclass,Sex,Age,SibSp,Parch,Fare,Embarked\n"
            . "1,female,30,0,0,100,S\n"
            . "1,female,40,0,0,120,C\n"
            . "3,male,20,0,0,10,S\n"
            . "3,male,24,0,0,12,S\n",
        );

        return Chapter08::fit(
            Chapter08::featuresTable($table->rows),
            ['1', '1', '0', '0'],
            3,
            WeightedTree::NONE,
        );
    }

    /** 作り物のモデルを 2 つとも保存した置き場。 */
    private function storeWithModels(): FileStore
    {
        $store = new FileStore($this->tempDir('with'));
        $store->saveSalesModel($this->fakeLinearModel());
        $store->saveSurvivalModel($this->fakePipeline());

        return $store;
    }

    // ---- 要求の読み込みと検証 ----

    #[TestDox('JSON のオブジェクトを読める')]
    public function testJSONのオブジェクトを読める(): void
    {
        $this->assertSame(
            ['sns1' => 200, 'original' => 1],
            Validation::readJson('{"sns1": 200, "original": 1}', Validation::MOVIE_TYPES),
        );
    }

    #[TestDox('JSON として読めない本文は弾く')]
    public function testJSONとして読めない本文は弾く(): void
    {
        $this->expectException(ValidationException::class);
        Validation::readJson('{', Validation::MOVIE_TYPES);
    }

    #[TestDox('オブジェクトでない JSON は弾く')]
    public function testオブジェクトでないJSONは弾く(): void
    {
        $this->expectException(ValidationException::class);
        Validation::readJson('[1, 2]', Validation::MOVIE_TYPES);
    }

    #[TestDox('列の型が合わなければ弾く')]
    public function test列の型が合わなければ弾く(): void
    {
        $this->expectException(ValidationException::class);
        Validation::readJson('{"sns1": "たくさん"}', Validation::MOVIE_TYPES);
    }

    #[TestDox('整数の列に小数が来たら弾く')]
    public function test整数の列に小数が来たら弾く(): void
    {
        $this->expectException(ValidationException::class);
        Validation::readJson('{"original": 1.5}', Validation::MOVIE_TYPES);
    }

    #[TestDox('表に無い列と null は型を問わない')]
    public function test表に無い列とnullは型を問わない(): void
    {
        $this->assertSame(
            ['unknown' => 'なんでも', 'sns1' => null],
            Validation::readJson('{"unknown": "なんでも", "sns1": null}', Validation::MOVIE_TYPES),
        );
    }

    #[TestDox('映画の要求を特徴量にする')]
    public function test映画の要求を特徴量にする(): void
    {
        $movie = Validation::movie(['sns1' => 200, 'sns2' => 500.5, 'actor' => 3000, 'original' => 1]);

        $this->assertSame(200.0, $movie->sns1);
        $this->assertSame(500.5, $movie->sns2);
        $this->assertSame(3000.0, $movie->actor);
        $this->assertSame(1, $movie->original);
    }

    #[TestDox('映画の検証の理由をまとめて返す')]
    public function test映画の検証の理由をまとめて返す(): void
    {
        try {
            Validation::movie(['sns1' => -1, 'sns2' => 2000, 'actor' => 300, 'original' => 2]);
            $this->fail('検証を通ってしまいました');
        } catch (ValidationException $e) {
            $this->assertSame(
                ['sns1 は 0 以上にしてください', 'original は 0、1 のどれかにしてください'],
                $e->reasons,
            );
        }
    }

    #[TestDox('映画の必須の列が無ければ理由を返す')]
    public function test映画の必須の列が無ければ理由を返す(): void
    {
        try {
            Validation::movie([]);
            $this->fail('検証を通ってしまいました');
        } catch (ValidationException $e) {
            $this->assertSame(
                ['sns1 は必須です', 'sns2 は必須です', 'actor は必須です', 'original は必須です'],
                $e->reasons,
            );
        }
    }

    #[TestDox('乗客の年齢と乗船港は省略できる')]
    public function test乗客の年齢と乗船港は省略できる(): void
    {
        $passenger = Validation::passenger([
            'pclass' => 1,
            'sex' => 'female',
            'sib_sp' => 0,
            'parch' => 0,
            'fare' => 50,
        ]);

        $this->assertNull($passenger->age);
        $this->assertNull($passenger->embarked);
        $this->assertSame(50.0, $passenger->fare);
    }

    #[TestDox('乗客の選択肢の外の値は理由を返す')]
    public function test乗客の選択肢の外の値は理由を返す(): void
    {
        try {
            Validation::passenger([
                'pclass' => 4,
                'sex' => 'unknown',
                'age' => -1,
                'sib_sp' => 0,
                'parch' => 0,
                'fare' => 50,
                'embarked' => 'X',
            ]);
            $this->fail('検証を通ってしまいました');
        } catch (ValidationException $e) {
            $this->assertSame(
                [
                    'pclass は 1、2、3 のどれかにしてください',
                    'sex は female、male のどれかにしてください',
                    'age は 0 以上にしてください',
                    'embarked は C、Q、S のどれかにしてください',
                ],
                $e->reasons,
            );
        }
    }

    // ---- 置き場の約束 ----

    #[TestDox('ファイルの置き場は置き場の約束を満たす')]
    public function testファイルの置き場は置き場の約束を満たす(): void
    {
        StoreContract::check($this->storeWithModels(), new FileStore($this->tempDir('empty')));
    }

    #[TestDox('偽物の置き場も置き場の約束を満たす')]
    public function test偽物の置き場も置き場の約束を満たす(): void
    {
        StoreContract::check(new FakeStore(), new FakeStore(false, false));
    }

    #[TestDox('壊れたファイルはモデルが無いのとは違う失敗になる')]
    public function test壊れたファイルはモデルが無いのとは違う失敗になる(): void
    {
        $dir = $this->tempDir('broken');
        mkdir($dir, 0o777, true);
        file_put_contents($dir . '/cinema.model', 'これはモデルではありません');

        $this->expectException(\InvalidArgumentException::class);
        (new FileStore($dir))->loadSalesModel();
    }

    #[TestDox('保存した線形回帰のモデルは 1 ビットも変わらずに戻る')]
    public function test保存した線形回帰のモデルは変わらずに戻る(): void
    {
        $model = new LinearModel(6114.5955056944, Chapter07::FEATURE_COLUMNS, [0.1234567890123456, 2.0, 3.0, 0.5]);
        $store = new FileStore($this->tempDir('roundtrip'));
        $store->saveSalesModel($model);

        $direct = (new LinearSalesModel($model))->predict(StoreContract::movie());

        $this->assertSame($direct, $store->loadSalesModel()->predict(StoreContract::movie()));
    }

    // ---- サービス ----

    #[TestDox('サービスは置き場のモデルで予測する')]
    public function testサービスは置き場のモデルで予測する(): void
    {
        $service = new Service(new FakeStore());

        $this->assertSame(FakeStore::FIXED_SALES, $service->predictSales(StoreContract::movie()));
        $this->assertTrue($service->predictSurvival(StoreContract::passenger()));
    }

    #[TestDox('ヘルスチェックはモデルごとに読み込めるかを返す')]
    public function testヘルスチェックはモデルごとに読み込めるかを返す(): void
    {
        $this->assertSame(
            ['cinema' => true, 'survived' => true],
            (new Service(new FakeStore()))->health(),
        );
        $this->assertSame(
            ['cinema' => false, 'survived' => true],
            (new Service(new FakeStore(false, true)))->health(),
        );
    }

    #[TestDox('モデルの名前は置き場の順に並ぶ')]
    public function testモデルの名前は置き場の順に並ぶ(): void
    {
        $this->assertSame(['cinema', 'survived'], Domain::MODEL_NAMES);
    }

    // ---- API ----

    /** @return array{int, array<string, mixed>} */
    private function call(string $method, string $path, string $body, \GettingStartedMl\Chapter15\ModelStore $store): array
    {
        $response = (new Api(new Service($store)))->handle(new Request($method, $path, $body));
        $decoded = json_decode($response->body, true);
        $this->assertIsArray($decoded);
        $this->assertSame('application/json; charset=utf-8', $response->headers['Content-Type']);

        /** @var array<string, mixed> $decoded */
        return [$response->status, $decoded];
    }

    #[TestDox('ヘルスチェックはモデルがそろえば ok を返す')]
    public function testヘルスチェックはモデルがそろえばokを返す(): void
    {
        [$status, $body] = $this->call('GET', '/health', '', new FakeStore());

        $this->assertSame(200, $status);
        $this->assertSame(['status' => 'ok', 'models' => ['cinema' => true, 'survived' => true]], $body);
    }

    #[TestDox('モデルが欠けたヘルスチェックは degraded を返す')]
    public function testモデルが欠けたヘルスチェックはdegradedを返す(): void
    {
        [$status, $body] = $this->call('GET', '/health', '', new FakeStore(false, true));

        $this->assertSame(200, $status);
        $this->assertSame(['status' => 'degraded', 'models' => ['cinema' => false, 'survived' => true]], $body);
    }

    #[TestDox('興行収入の予測を JSON で返す')]
    public function test興行収入の予測をJSONで返す(): void
    {
        [$status, $body] = $this->call(
            'POST',
            '/cinema/sales',
            '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}',
            new FakeStore(),
        );

        $this->assertSame(200, $status);
        $this->assertSame(['sales' => FakeStore::FIXED_SALES], $body);
    }

    #[TestDox('生存の予測を JSON で返す')]
    public function test生存の予測をJSONで返す(): void
    {
        [$status, $body] = $this->call(
            'POST',
            '/survived',
            '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 50.0}',
            new FakeStore(),
        );

        $this->assertSame(200, $status);
        $this->assertSame(['survived' => true], $body);
    }

    #[TestDox('検証に落ちた要求は 422 と理由を返す')]
    public function test検証に落ちた要求は422と理由を返す(): void
    {
        [$status, $body] = $this->call(
            'POST',
            '/cinema/sales',
            '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}',
            new FakeStore(),
        );

        $this->assertSame(422, $status);
        $this->assertSame(
            ['detail' => ['sns1 は 0 以上にしてください', 'original は 0、1 のどれかにしてください']],
            $body,
        );
    }

    #[TestDox('読めない JSON は 422 を返す')]
    public function test読めないJSONは422を返す(): void
    {
        [$status, $body] = $this->call('POST', '/cinema/sales', '{"sns1": "たくさん"}', new FakeStore());

        $this->assertSame(422, $status);
        $this->assertSame(['detail' => ['JSON の形式または値の型が正しくありません']], $body);
    }

    #[TestDox('モデルが無ければ 503 を返す')]
    public function testモデルが無ければ503を返す(): void
    {
        [$status, $body] = $this->call(
            'POST',
            '/cinema/sales',
            '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}',
            new FakeStore(false, true),
        );

        $this->assertSame(503, $status);
        $this->assertSame(['detail' => '学習済みモデル cinema が見つかりません'], $body);
    }

    #[TestDox('予測がほかの理由で落ちたら 500 を返す')]
    public function test予測がほかの理由で落ちたら500を返す(): void
    {
        $dir = $this->tempDir('broken-api');
        mkdir($dir, 0o777, true);
        file_put_contents($dir . '/cinema.model', 'これはモデルではありません');

        [$status, $body] = $this->call(
            'POST',
            '/cinema/sales',
            '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}',
            new FileStore($dir),
        );

        $this->assertSame(500, $status);
        $this->assertSame(['detail' => '予測できませんでした'], $body);
    }

    #[TestDox('知らないパスは 404 を返す')]
    public function test知らないパスは404を返す(): void
    {
        [$status, $body] = $this->call('GET', '/unknown', '', new FakeStore());

        $this->assertSame(404, $status);
        $this->assertSame(['detail' => '見つかりません'], $body);
    }

    #[TestDox('許していないメソッドは 405 と Allow を返す')]
    public function test許していないメソッドは405とAllowを返す(): void
    {
        $response = (new Api(new Service(new FakeStore())))->handle(new Request('GET', '/cinema/sales', ''));

        $this->assertSame(405, $response->status);
        $this->assertSame('POST', $response->headers['Allow']);
    }

    #[TestDox('応答の JSON は日本語をそのまま書く')]
    public function test応答のJSONは日本語をそのまま書く(): void
    {
        $response = (new Api(new Service(new FakeStore())))->handle(new Request('GET', '/unknown', ''));

        $this->assertSame('{"detail":"見つかりません"}', $response->body);
    }

    #[TestDox('応答は状態・ヘッダー・本文を持つ値である')]
    public function test応答は状態ヘッダー本文を持つ値である(): void
    {
        $response = Response::json(200, ['ok' => true]);

        $this->assertSame(200, $response->status);
        $this->assertSame('{"ok":true}', $response->body);
    }

    // ---- 実データ ----

    #[Group('data')]
    #[TestDox('実データで学習したモデルの予測がほかの言語版と一致する')]
    public function test実データの予測がほかの言語版と一致する(): void
    {
        if (!Dataset::exists('cinema.csv') || !Dataset::exists('Survived.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::dir());
        }

        $store = new FileStore($this->tempDir('data'));
        Chapter15::trainAndSaveModels(Dataset::dir(), $store);
        $service = new Service($store);

        // Java 版・Scala 版・Clojure 版と同じ分割・同じ手順なので、予測値もほぼ一致する。
        // 完全には一致せず、PHP 版は 7730.457421687019（Elixir 版は …016、差は 1e-11 未満）。
        // 正規方程式を解くのが MathPHP の LU 分解か Java の行列ライブラリかの違いで、丸めの順が変わる。
        $this->assertEqualsWithDelta(self::JVM_SALES, $service->predictSales(StoreContract::movie()), 1.0e-6);
        $this->assertSame(self::PHP_SALES, $service->predictSales(StoreContract::movie()));
        $this->assertTrue($service->predictSurvival(StoreContract::passenger()));
        $this->assertFalse($service->predictSurvival(
            new \GettingStartedMl\Chapter15\Passenger(3, 'male', null, 0, 0, 8.0, 'S'),
        ));
        $this->assertSame(['cinema' => true, 'survived' => true], $service->health());
    }

    #[Group('data')]
    #[TestDox('保存と読み込みを挟んでも予測値は 1 ビットも変わらない')]
    public function test保存と読み込みを挟んでも予測値は変わらない(): void
    {
        if (!Dataset::exists('cinema.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('cinema.csv'));
        }

        $split = Chapter07::prepareCinema(Dataset::path('cinema.csv'), 0.2, 0);
        $model = Chapter07::fit($split['xTrain'], $split['tTrain'], Chapter07::FEATURE_COLUMNS);
        $direct = (new LinearSalesModel($model))->predict(StoreContract::movie());

        $store = new FileStore($this->tempDir('roundtrip-data'));
        $store->saveSalesModel($model);

        $this->assertSame($direct, $store->loadSalesModel()->predict(StoreContract::movie()));
    }

    #[Group('data')]
    #[TestDox('学習して保存すると起動の案内を返す')]
    public function test学習して保存すると起動の案内を返す(): void
    {
        if (!Dataset::exists('cinema.csv') || !Dataset::exists('Survived.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::dir());
        }

        $output = Chapter15::run($this->tempDir('run'));

        $this->assertStringContainsString('モデル cinema: true', $output);
        $this->assertStringContainsString('モデル survived: true', $output);
        $this->assertStringContainsString((string) Chapter15::PORT, $output);
    }

    #[TestDox('学習データが無ければ置き場は空のままである')]
    public function test学習データが無ければ置き場は空のままである(): void
    {
        $store = new FileStore($this->tempDir('nodata'));

        $this->expectException(ModelNotFoundException::class);
        $store->loadSurvivalModel();
    }

    #[TestDox('第 8 章のパイプラインを乗客の予測に使える')]
    public function test第8章のパイプラインを乗客の予測に使える(): void
    {
        $model = new \GettingStartedMl\Chapter15\PipelineSurvivalModel($this->fakePipeline());

        $this->assertTrue($model->predict(StoreContract::passenger()));
        $this->assertFalse($model->predict(
            new \GettingStartedMl\Chapter15\Passenger(3, 'male', 24.0, 0, 0, 12.0, 'S'),
        ));
    }
}
