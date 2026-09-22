# frozen_string_literal: true

module GettingStartedMl
  # 第 15 章のドメイン層。予測の入力と、モデル・置き場の約束。HTTP にも Marshal にも依存しない。
  #
  # Ruby には trait もインターフェースも無いので、約束はメソッドの名前と戻り値の取り決めだけで表す。
  #
  # - 興行収入のモデル: predict_sales(movie) で数値を返す
  # - 生存予測のモデル: survives?(passenger) で真偽値を返す
  # - モデルの置き場: load_sales_model・load_survival_model でモデルを返す。無ければ ModelNotFound を投げる
  #
  # 約束を守っているかは、置き場の実装とテスト用の偽物に同じテスト（test/support/model_store_contract.rb）を
  # 走らせて確かめる。
  module Chapter15
    # モデルを読み込めない。モデルをまだ学習していないだけなので、API は 503 を返す。
    class ModelNotFound < StandardError
      attr_reader :model_name

      def initialize(model_name)
        @model_name = model_name
        super("モデルがありません: #{model_name}")
      end
    end

    # 映画の特徴量。SNS の評判 2 種類・主演の人気・原作の有無（0 か 1）。
    Movie = Data.define(:sns1, :sns2, :actor, :original) do
      # 第 7 章のモデルに渡す特徴量にする。
      def features
        Chapter02::Features.new(columns: Chapter07::Cinema::FEATURES, values: [sns1, sns2, actor, original].map(&:to_f))
      end
    end

    # 乗客の特徴量。第 8 章と同じく値は文字列で持ち、空文字列は欠損値として前処理に任せる。
    Passenger = Data.define(:pclass, :sex, :age, :sib_sp, :parch, :fare, :embarked) do
      # 第 8 章のパイプラインに渡す行にする。
      def row
        Chapter08::Survived.passenger([pclass, sex, age, sib_sp, parch, fare, embarked])
      end
    end

    # 第 7 章の線形回帰のモデルを、興行収入のモデルの約束に合わせる。
    LinearSalesModel = Data.define(:model) do
      def predict_sales(movie)
        model.predict_one(movie.features)
      end
    end

    # 第 8 章の学習済みパイプラインを、生存予測のモデルの約束に合わせる。
    PipelineSurvivalModel = Data.define(:pipeline) do
      def survives?(passenger)
        pipeline.predict(Chapter08::Survived.features([passenger.row])).first == Chapter08::Survived::SURVIVED
      end
    end
  end
end
