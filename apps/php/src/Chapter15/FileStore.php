<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use GettingStartedMl\Chapter08;
use GettingStartedMl\FittedPipeline;
use GettingStartedMl\LinearModel;
use InvalidArgumentException;

/**
 * 学習済みモデルをディレクトリのファイルに保存し、読み込む置き場。
 *
 * 第 8 章で、学習済みのパイプラインが `serialize`／`unserialize` の往復で
 * 戻ることを確かめてある。第 7 章の線形回帰のモデルも `readonly class` と配列なので、
 * 同じやり方で保存できる。
 *
 * 保存のメソッドは interface に入れない。読み込みは API が使うが、保存は学習のときにしか
 * 使わないので、API から見える約束を小さく保つ。
 */
final readonly class FileStore implements ModelStore
{
    /** 保存した形式の版。読み込むときに確かめる。 */
    public const int FORMAT_VERSION = 1;

    /** ディレクトリを置き場にする。ディレクトリはまだ無くてもよい。 */
    public function __construct(private string $modelDir)
    {
    }

    public function loadSalesModel(): SalesModel
    {
        $path = $this->modelFile(Domain::SALES_MODEL);
        $this->requireFile($path, Domain::SALES_MODEL);
        $contents = @file_get_contents($path);

        if ($contents === false) {
            throw new InvalidArgumentException("モデルを開けません: {$path}");
        }

        // unserialize は読めない内容を例外ではなく警告で知らせるので、@ で抑えて false で判定する。
        // allowed_classes を省くと、ファイルに書かれたどんなクラスでも作られてしまう（第 8 章）。
        $loaded = @unserialize($contents, ['allowed_classes' => [LinearModel::class]]);

        if (
            !is_array($loaded)
            || ($loaded['format'] ?? null) !== self::FORMAT_VERSION
            || !(($loaded['model'] ?? null) instanceof LinearModel)
        ) {
            throw new InvalidArgumentException("モデルとして読めません: {$path}");
        }

        return new LinearSalesModel($loaded['model']);
    }

    public function loadSurvivalModel(): SurvivalModel
    {
        $path = $this->modelFile(Domain::SURVIVAL_MODEL);
        $this->requireFile($path, Domain::SURVIVAL_MODEL);

        return new PipelineSurvivalModel(Chapter08::loadModel($path));
    }

    /** 第 7 章の線形回帰のモデルを保存する。 */
    public function saveSalesModel(LinearModel $model): void
    {
        $path = $this->modelFile(Domain::SALES_MODEL);
        $this->makeDir();
        file_put_contents($path, serialize(['format' => self::FORMAT_VERSION, 'model' => $model]));
    }

    /** 第 8 章の学習済みパイプラインを、第 8 章の saveModel で保存する。 */
    public function saveSurvivalModel(FittedPipeline $pipeline): void
    {
        Chapter08::saveModel($pipeline, $this->modelFile(Domain::SURVIVAL_MODEL));
    }

    private function modelFile(string $model): string
    {
        return $this->modelDir . '/' . $model . '.model';
    }

    private function makeDir(): void
    {
        // mkdir も失敗を警告で知らせるので、@ で抑えて戻り値で判定する（第 8 章）。
        if (!is_dir($this->modelDir) && !@mkdir($this->modelDir, 0o777, true) && !is_dir($this->modelDir)) {
            throw new InvalidArgumentException("ディレクトリを作れません: {$this->modelDir}");
        }
    }

    /** ファイルが無ければ「モデルが無い」に変える。 */
    private function requireFile(string $path, string $model): void
    {
        if (!is_file($path)) {
            throw new ModelNotFoundException($model);
        }
    }
}
