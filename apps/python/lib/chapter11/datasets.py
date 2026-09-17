import pandas as pd


def prepare_survived(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame(
        {
            "Pclass": df["Pclass"],
            "Age": df["Age"].fillna(df["Age"].mean()),
            "male": (df["Sex"] == "male").astype(int),
        }
    )
    return x, df["Survived"]


CINEMA_FEATURES = ["SNS1", "SNS2", "actor", "original"]


def prepare_cinema(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    x = df[CINEMA_FEATURES]
    return x.fillna(x.mean()), df["sales"]
