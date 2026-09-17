from lib.appendix_a.bank_exercise import (
    fit_final,
    prepare_bank,
    score,
    select_best,
    tune_max_depth,
)
from lib.dataset import data_dir

SEED = 0
MAX_DEPTHS = list(range(1, 16))


def main() -> None:
    split = prepare_bank(data_dir() / "Bank.csv", seed=SEED)
    print(
        f"訓練データ: {len(split.x_train)} 件, 検証データ: {len(split.x_valid)} 件, "
        f"テストデータ: {len(split.x_test)} 件"
    )
    print("深さ\t訓練 正解率\t検証 正解率\t検証 再現率\t検証 F値")
    results = tune_max_depth(split, MAX_DEPTHS)
    for r in results:
        print(
            f"{r.max_depth}\t{r.train.accuracy:.4f}\t{r.valid.accuracy:.4f}\t"
            f"{r.valid.recall:.4f}\t{r.valid.f1:.4f}"
        )
    best = select_best(results)
    pipeline = fit_final(split, best.max_depth)
    test = score(split.t_test, pipeline.predict(split.x_test))
    print(f"\n選んだ深さ: {best.max_depth}")
    print(
        f"テストデータ: 正解率 {test.accuracy:.4f}, 適合率 {test.precision:.4f}, "
        f"再現率 {test.recall:.4f}, F値 {test.f1:.4f}"
    )


if __name__ == "__main__":
    main()
