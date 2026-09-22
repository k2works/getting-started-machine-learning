# frozen_string_literal: true

module GettingStartedMl
  module Chapter15
    # モデルの名前と、読み込めるかどうか。
    ModelHealth = Data.define(:name, :ready)

    # アプリケーション層。置き場からモデルを読み込んで予測する。HTTP を知らない。
    #
    # 置き場は約束（load_sales_model・load_survival_model）を満たすものなら何でもよいので、
    # テストでは偽物を渡せる。型の宣言は無く、渡したものが約束を満たすかは呼んだときに分かる。
    class PredictionService
      def initialize(store)
        @store = store
      end

      # 映画の興行収入を予測する。モデルが無ければ ModelNotFound を投げる。
      def predict_sales(movie)
        @store.load_sales_model.predict_sales(movie)
      end

      # 乗客が生存するかを予測する。モデルが無ければ ModelNotFound を投げる。
      def survives?(passenger)
        @store.load_survival_model.survives?(passenger)
      end

      # モデルごとに読み込めるかどうかを返す。読み込めない理由は問わない。
      def health
        [ModelHealth.new(name: SALES_MODEL, ready: ready? { @store.load_sales_model }),
         ModelHealth.new(name: SURVIVAL_MODEL, ready: ready? { @store.load_survival_model })]
      end

      private

      def ready?
        yield
        true
      rescue StandardError
        false
      end
    end
  end
end
