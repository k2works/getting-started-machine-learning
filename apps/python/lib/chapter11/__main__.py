from lib.chapter11.experiments import N_SPLITS, evaluate_cinema, evaluate_survived
from lib.dataset import data_dir


def main() -> None:
    print(f"Survived（決定木、{N_SPLITS} 分割交差検証の平均）")
    for name, score in evaluate_survived(data_dir() / "Survived.csv").items():
        print(f"  {name}: {score:.4f}")
    print(f"cinema（線形回帰、{N_SPLITS} 分割交差検証の平均）")
    for name, score in evaluate_cinema(data_dir() / "cinema.csv").items():
        print(f"  {name}: {score:.2f}")


if __name__ == "__main__":
    main()
