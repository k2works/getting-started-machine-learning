# frozen_string_literal: true

require_relative "chapter15/domain"
require_relative "chapter15/validation"
require_relative "chapter15/store"
require_relative "chapter15/service"
require_relative "chapter15/api"

module GettingStartedMl
  # 第 15 章: 機械学習 API とモジュール設計。第 7・8 章のモデルを Sinatra の予測 API として公開する。
  #
  # 層はファイルで分ける。domain（入力とモデル・置き場の約束）、service（予測）、store（Marshal の保存先）、
  # validation と api（HTTP）。ここは組み立てと起動だけを受け持つ。
  module Chapter15
    # テストデータの割合と乱数のシード。第 7・8 章と同じ条件にする。
    TEST_SIZE = 0.2
    SEED = 0
    # 生存予測の決定木の深さ。
    MAX_DEPTH = 5
    # API を待ち受けるポート。
    PORT = 8015
    # 学習済みモデルの保存先（apps/ruby/model/ は .gitignore の対象）。
    MODEL_DIR = "model"

    # 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。
    def self.train_and_save_models(data_dir, store)
      split = Chapter07::Cinema.prepare(File.join(data_dir, "cinema.csv"), test_size: TEST_SIZE, seed: SEED)
      store.save_sales_model(Chapter07.fit(split.x_train, split.t_train))
      store.save_survival_model(survival_pipeline(File.join(data_dir, "Survived.csv")))
    end

    # 第 8 章と同じ条件で、生存予測のパイプラインを学習する。
    def self.survival_pipeline(csv_file)
      rows = Chapter02::Table.load(csv_file).rows
      split = Chapter02.split_train_test(rows, Chapter08::Survived.target(rows), test_size: TEST_SIZE, seed: SEED)

      Chapter08::Pipeline.build(max_depth: MAX_DEPTH, class_weight: :balanced)
                         .fit(Chapter08::Survived.features(split.x_train), split.t_train)
    end

    # モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。
    def self.run(out = $stdout)
      store = FileModelStore.new(MODEL_DIR)
      train_and_save_models(Dataset.dir, store)
      service = PredictionService.new(store)

      service.health.each { |model| out.puts "モデル #{model.name}: #{model.ready}" }
      out.puts "http://localhost:#{PORT} で待ち受けます"
      out.flush
      serve(Api.new(service:))
    end

    # Puma で待ち受ける。rackup の Handler が、Rack のアプリケーションとサーバーをつなぐ。
    def self.serve(app)
      require "rackup"

      Rackup::Handler.get("puma").run(app, Host: "127.0.0.1", Port: PORT, Silent: true)
    end
  end
end
