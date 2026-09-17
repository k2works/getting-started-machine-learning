from pathlib import Path

import pandas as pd


def standardize_boston(df: pd.DataFrame) -> pd.DataFrame:
    filled = df.fillna(df.mean(numeric_only=True))
    dummies = pd.get_dummies(filled["CRIME"], drop_first=True, dtype=float)
    numeric = filled.drop(columns=["CRIME"]).join(dummies).astype(float)
    return (numeric - numeric.mean()) / numeric.std(ddof=0)


def load_standardized_boston(csv_file: Path) -> pd.DataFrame:
    return standardize_boston(pd.read_csv(csv_file))
