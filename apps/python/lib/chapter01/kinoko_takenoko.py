import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Person:
    height: int
    weight: int
    age_group: int
    faction: str


@dataclass(frozen=True)
class Features:
    height: int
    weight: int
    age_group: int


def load_people(csv_file: Path) -> list[Person]:
    with csv_file.open(encoding="utf-8-sig", newline="") as f:
        return [
            Person(
                height=int(row["身長"]),
                weight=int(row["体重"]),
                age_group=int(row["年代"]),
                faction=row["派閥"],
            )
            for row in csv.DictReader(f)
        ]


def split_features_and_labels(people: list[Person]) -> tuple[list[Features], list[str]]:
    features = [Features(p.height, p.weight, p.age_group) for p in people]
    labels = [p.faction for p in people]
    return features, labels


def predict_by_rule(features: Features) -> str:
    if features.age_group == 20:
        return "きのこ"
    return "たけのこ"


def accuracy(predictions: list[str], labels: list[str]) -> float:
    correct = sum(1 for p, t in zip(predictions, labels, strict=True) if p == t)
    return correct / len(labels)
