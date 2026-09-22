# frozen_string_literal: true

# モデルの置き場の約束を、テストとして書いたもの。Rust 版の trait ModelStore に当たる。
#
# Ruby は「約束を満たすつもりだ」と宣言できないので、置き場の実装とテスト用の偽物の両方に
# このモジュールを include して、同じテストを走らせる。include する側は次の 2 つを定義する。
#
# - store_with_models: 2 つのモデルを読み込める置き場
# - store_without_models: どちらのモデルも無い置き場
module ModelStoreContract
  C = GettingStartedMl::Chapter15

  CONTRACT_MOVIE = C::Movie.new(sns1: 100, sns2: 2000, actor: 300, original: 1)
  CONTRACT_PASSENGER = C::Passenger.new(pclass: "1", sex: "female", age: "", sib_sp: "0", parch: "0", fare: "80",
                                        embarked: "")

  def test_約束_モデルがあれば予測できるモデルを返す
    store = store_with_models

    assert_kind_of Numeric, store.load_sales_model.predict_sales(CONTRACT_MOVIE)
    assert_includes [true, false], store.load_survival_model.survives?(CONTRACT_PASSENGER)
  end

  def test_約束_モデルが無ければ_ModelNotFound_を投げる
    store = store_without_models

    assert_equal C::SALES_MODEL, assert_raises(C::ModelNotFound) { store.load_sales_model }.model_name
    assert_equal C::SURVIVAL_MODEL, assert_raises(C::ModelNotFound) { store.load_survival_model }.model_name
  end
end
