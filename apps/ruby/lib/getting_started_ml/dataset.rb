# frozen_string_literal: true

module GettingStartedMl
  # 学習データのディレクトリを求める。
  module Dataset
    # 実データの置き場をテストや CI から差し替えるための環境変数の名前。
    ENV_NAME = "ML_DATA_DIR"

    # 既定の置き場。テストは apps/ruby で走るので、相対パスで apps/data に届く。
    DEFAULT_DIR = File.join("..", "data", "sukkiri-ml")

    # 学習データのディレクトリを返す。環境変数はテストで差し替えられるように引数で受け取る。
    def self.dir(env = ENV)
      value = env[ENV_NAME]
      value.nil? || value.empty? ? DEFAULT_DIR : value
    end
  end
end
