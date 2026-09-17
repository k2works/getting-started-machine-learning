from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray


@dataclass(frozen=True)
class PcaModel:
    mean: NDArray[np.float64]
    components: NDArray[np.float64]
    explained_variance: NDArray[np.float64]
    explained_variance_ratio: NDArray[np.float64]


def covariance_matrix(x: NDArray[np.float64]) -> NDArray[np.float64]:
    centered = x - x.mean(axis=0)
    return (centered.T @ centered) / (len(x) - 1)


def normalize_signs(components: NDArray[np.float64]) -> NDArray[np.float64]:
    largest = np.argmax(np.abs(components), axis=1)
    signs = np.sign(components[np.arange(len(components)), largest])
    return components * signs[:, np.newaxis]


def fit_pca(x: NDArray[np.float64], n_components: int) -> PcaModel:
    eigenvalues, eigenvectors = np.linalg.eigh(covariance_matrix(x))
    order = np.argsort(eigenvalues)[::-1][:n_components]
    return PcaModel(
        mean=x.mean(axis=0),
        components=normalize_signs(eigenvectors[:, order].T),
        explained_variance=eigenvalues[order],
        explained_variance_ratio=eigenvalues[order] / eigenvalues.sum(),
    )


def transform(model: PcaModel, x: NDArray[np.float64]) -> NDArray[np.float64]:
    return (x - model.mean) @ model.components.T


def top_loadings(
    component: NDArray[np.float64], columns: Sequence[str], k: int
) -> list[tuple[str, float]]:
    order = np.argsort(np.abs(component))[::-1][:k]
    return [(columns[i], float(component[i])) for i in order]


def components_needed(ratios: NDArray[np.float64], threshold: float) -> int:
    cumulative = np.cumsum(ratios)
    return int(np.argmax(cumulative >= threshold)) + 1
