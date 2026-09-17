from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

NON_SPENDING_COLUMNS = ["Channel", "Region"]


@dataclass(frozen=True)
class KMeansResult:
    labels: np.ndarray
    centers: np.ndarray
    sse: float


def load_spending(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file).drop(columns=NON_SPENDING_COLUMNS)


def standardize(df: pd.DataFrame) -> pd.DataFrame:
    return (df - df.mean()) / df.std(ddof=0)


def assign_clusters(points: np.ndarray, centers: np.ndarray) -> np.ndarray:
    differences = points[:, np.newaxis, :] - centers[np.newaxis, :, :]
    squared_distances: np.ndarray = (differences**2).sum(axis=2)
    return np.argmin(squared_distances, axis=1)


def update_centers(
    points: np.ndarray, labels: np.ndarray, previous_centers: np.ndarray
) -> np.ndarray:
    centers = previous_centers.copy()
    for k in range(len(previous_centers)):
        members = points[labels == k]
        if len(members) > 0:
            centers[k] = members.mean(axis=0)
    return centers


def sum_of_squared_errors(
    points: np.ndarray, labels: np.ndarray, centers: np.ndarray
) -> float:
    return float(((points - centers[labels]) ** 2).sum())


def choose_initial_centers(
    points: np.ndarray, n_clusters: int, seed: int
) -> np.ndarray:
    rng = np.random.default_rng(seed)
    positions = rng.choice(len(points), size=n_clusters, replace=False)
    return points[positions].copy()


def kmeans(
    points: np.ndarray, initial_centers: np.ndarray, max_iterations: int = 300
) -> KMeansResult:
    centers = initial_centers.astype(float)
    for _ in range(max_iterations):
        labels = assign_clusters(points, centers)
        new_centers = update_centers(points, labels, centers)
        if np.array_equal(new_centers, centers):
            break
        centers = new_centers
    labels = assign_clusters(points, centers)
    return KMeansResult(
        labels=labels,
        centers=centers,
        sse=sum_of_squared_errors(points, labels, centers),
    )


def best_kmeans(
    points: np.ndarray, initial_center_candidates: list[np.ndarray]
) -> KMeansResult:
    results = [kmeans(points, centers) for centers in initial_center_candidates]
    return min(results, key=lambda result: result.sse)


def kmeans_with_restarts(
    points: np.ndarray, n_clusters: int, seed: int, n_init: int = 10
) -> KMeansResult:
    candidates = [
        choose_initial_centers(points, n_clusters, seed + i) for i in range(n_init)
    ]
    return best_kmeans(points, candidates)


def sse_by_cluster_count(
    points: np.ndarray, cluster_counts: list[int], seed: int, n_init: int = 10
) -> dict[int, float]:
    return {
        n: kmeans_with_restarts(points, n, seed, n_init).sse for n in cluster_counts
    }


def summarize_clusters(df: pd.DataFrame, labels: np.ndarray) -> pd.DataFrame:
    grouped = df.groupby(labels)
    summary = grouped.mean().assign(件数=grouped.size())
    return summary[["件数", *df.columns]].sort_values("件数", ascending=False)
