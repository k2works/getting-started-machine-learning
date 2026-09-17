import numpy as np

from lib.chapter13.boston_standardized import load_standardized_boston
from lib.chapter13.pca import components_needed, fit_pca, top_loadings
from lib.dataset import data_dir

THRESHOLD = 0.8
TOP_K = 3


def format_loadings(loadings: list[tuple[str, float]]) -> str:
    return ", ".join(f"{column} {value:.3f}" for column, value in loadings)


def main() -> None:
    df = load_standardized_boston(data_dir() / "Boston.csv")
    columns = list(df.columns)
    model = fit_pca(df.to_numpy(), n_components=len(columns))
    ratios = model.explained_variance_ratio
    needed = components_needed(ratios, THRESHOLD)
    print(f"データ件数: {len(df)}, 列数: {len(columns)}")
    print(
        "寄与率: "
        + ", ".join(f"PC{i + 1} {r:.4f}" for i, r in enumerate(ratios[:needed]))
    )
    cumulative = float(np.cumsum(ratios)[needed - 1])
    print(
        f"累積寄与率が {THRESHOLD} に届く主成分の数: {needed}"
        f"（累積寄与率 {cumulative:.4f}）"
    )
    for i in range(2):
        loadings = top_loadings(model.components[i], columns, k=TOP_K)
        print(f"第 {i + 1} 主成分で影響の大きい列: {format_loadings(loadings)}")


if __name__ == "__main__":
    main()
