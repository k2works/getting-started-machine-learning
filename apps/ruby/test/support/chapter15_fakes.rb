# frozen_string_literal: true

# 第 15 章のテスト用の偽物。学習データもファイルも使わずに、API とサービスを確かめる。
# lib ではなく test に置くので、本番のコードからは見えない（Rust 版の tests/ の StubStore と同じ位置）。
module Chapter15Fakes
  C = GettingStartedMl::Chapter15

  # 決まった値を返す興行収入のモデル。
  FixedSales = Data.define(:sales) do
    def predict_sales(_movie)
      sales
    end
  end

  # 決まった値を返す生存予測のモデル。
  FixedSurvival = Data.define(:survived) do
    def survives?(_passenger)
      survived
    end
  end

  # モデルがあるかどうかを差し替えられる置き場。
  FakeModelStore = Data.define(:sales, :survival) do
    def load_sales_model
      raise C::ModelNotFound, C::SALES_MODEL unless sales

      FixedSales.new(sales: 4321.5)
    end

    def load_survival_model
      raise C::ModelNotFound, C::SURVIVAL_MODEL unless survival

      FixedSurvival.new(survived: true)
    end
  end

  # モデルを読み込むと、モデルが無いこととは別の理由で失敗する置き場。
  class BrokenModelStore
    def load_sales_model
      raise IOError, "ディスクを読めません"
    end

    def load_survival_model
      raise IOError, "ディスクを読めません"
    end
  end
end
