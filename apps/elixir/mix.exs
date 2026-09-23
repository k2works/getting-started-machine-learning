defmodule GettingStartedMl.MixProject do
  use Mix.Project

  def project do
    [
      app: :getting_started_ml,
      version: "0.1.0",
      elixir: "~> 1.18",
      elixirc_options: [warnings_as_errors: true],
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      # NimbleCSV.define/2 が生成するパーサーはこちらが書いたコードではないので、
      # カバレッジの集計から外す（入れると総計が 47% まで落ちる）
      test_coverage: [ignore_modules: [GettingStartedMl.Csv.Parser], summary: [threshold: 70]]
    ]
  end

  def application do
    [extra_applications: [:logger]]
  end

  defp deps do
    [
      {:nimble_csv, "~> 1.3"},
      {:codepagex, "~> 0.1"},
      {:nx, "~> 0.13"},
      {:scholar, "~> 0.4"},
      {:plug, "~> 1.20"},
      {:bandit, "~> 1.12"},
      {:jason, "~> 1.4"},
      {:credo, "~> 1.7", only: [:dev, :test], runtime: false}
    ]
  end
end
