from lib.chapter14.kmeans import (
    kmeans_with_restarts,
    load_spending,
    sse_by_cluster_count,
    standardize,
    summarize_clusters,
)
from lib.dataset import data_dir

SEED = 0
N_INIT = 10
CLUSTER_COUNTS = list(range(1, 11))
N_CLUSTERS = 5


def main() -> None:
    df = load_spending(data_dir() / "Wholesale.csv")
    points = standardize(df).to_numpy()
    print(f"データ件数: {len(df)}（支出額 {len(df.columns)} 列）")
    print(f"クラスタ数ごとの SSE（初期中心 {N_INIT} 通りの最小値）:")
    for n, sse in sse_by_cluster_count(points, CLUSTER_COUNTS, SEED, N_INIT).items():
        print(f"  {n:>2}: {sse:.2f}")
    result = kmeans_with_restarts(points, N_CLUSTERS, SEED, N_INIT)
    print(f"クラスタ数 {N_CLUSTERS} のクラスタごとの件数と平均支出額:")
    summary = summarize_clusters(df, result.labels).round(0).astype(int)
    print(summary.to_string())


if __name__ == "__main__":
    main()
