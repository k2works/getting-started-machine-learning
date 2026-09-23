defmodule GettingStartedMl.Chapter15Test.StoreContract do
  @moduledoc """
  モデルの置き場の約束を、テストとして書いたもの。

  behaviour が決めるのは「関数の名前と引数の数」だけなので、
  「無ければ ModelNotFoundError を投げる」という取り決めは `@callback` では書けない。
  本物の置き場とテスト用の偽物の両方にこの関数を呼ぶことで、同じ約束を確かめる。
  """

  import ExUnit.Assertions

  alias GettingStartedMl.Chapter15.{Domain, ModelNotFoundError, ModelStore}

  @movie %{sns1: 200.0, sns2: 500.0, actor: 3000.0, original: 1}
  @passenger %{
    pclass: 1,
    sex: "female",
    age: nil,
    sib_sp: 0,
    parch: 0,
    fare: 50.0,
    embarked: nil
  }

  @doc "約束の確認に使う映画の特徴量。"
  def movie, do: @movie

  @doc "約束の確認に使う乗客の特徴量。年齢と乗船港は分からない。"
  def passenger, do: @passenger

  @doc "置き場の約束を確かめる。with はモデルを 2 つ読み込める置き場、without はどちらも無い置き場。"
  def check({module, _state} = with_models, without_models) do
    # 約束: behaviour の関数がそろっている（Clojure 版の satisfies? に当たる）
    assert function_exported?(module, :load_sales_model, 1)
    assert function_exported?(module, :load_survival_model, 1)

    # 約束: モデルがあれば予測する関数を返す
    assert is_number(ModelStore.load_sales_model(with_models).(@movie))
    assert ModelStore.load_survival_model(with_models).(@passenger) in [true, false]

    # 約束: モデルが無ければ ModelNotFoundError を投げる
    for {load, model} <- [
          {&ModelStore.load_sales_model/1, Domain.sales_model()},
          {&ModelStore.load_survival_model/1, Domain.survival_model()}
        ] do
      error = assert_raise ModelNotFoundError, fn -> load.(without_models) end
      assert error.model == model
      assert Exception.message(error) == "学習済みモデル #{model} が見つかりません"
    end
  end
end

defmodule GettingStartedMl.Chapter15Test.FakeStore do
  @moduledoc "第 15 章のテストで使う偽物の置き場。実データも学習も使わずに API とサービスを確かめる。"

  alias GettingStartedMl.Chapter15.{Domain, ModelNotFoundError}

  @behaviour GettingStartedMl.Chapter15.ModelStore

  @fixed_sales 4321.5

  @doc "偽物の置き場が返す興行収入。"
  def fixed_sales, do: @fixed_sales

  @doc "モデルがあるかどうかを差し替えられる置き場を作る。"
  def new(options), do: {__MODULE__, options}

  @impl true
  def load_sales_model(%{sales: true}), do: fn _movie -> @fixed_sales end
  def load_sales_model(_options), do: raise(ModelNotFoundError, model: Domain.sales_model())

  @impl true
  def load_survival_model(%{survival: true}), do: fn _passenger -> true end

  def load_survival_model(_options),
    do: raise(ModelNotFoundError, model: Domain.survival_model())
end

defmodule GettingStartedMl.Chapter15Test do
  use ExUnit.Case, async: true
  import Plug.Conn
  import Plug.Test

  alias GettingStartedMl.{Chapter07, Chapter08, Dataset}
  alias GettingStartedMl.Chapter15, as: C
  alias GettingStartedMl.Chapter15.{Api, Domain, FileStore, ModelStore, Service, Validation}
  alias GettingStartedMl.Chapter15Test.{FakeStore, StoreContract}

  @ready %{sales: true, survival: true}

  defp temp_dir(name) do
    Path.join(System.tmp_dir!(), "chapter15-#{name}-#{System.unique_integer([:positive])}")
  end

  defp sales_model, do: Chapter07.model(10.0, Chapter07.feature_columns(), [1.0, 2.0, 3.0, 4.0])

  # 女性が生存し、男性が死亡する 4 件の作り物のデータ。
  defp survival_rows do
    Enum.map(
      [
        ["1", "female", "30", "0", "0", "80", "C"],
        ["3", "male", "40", "0", "0", "8", "S"],
        ["2", "female", "20", "1", "0", "30", "S"],
        ["3", "male", "25", "0", "0", "10", "S"]
      ],
      &Map.new(Enum.zip(Chapter08.feature_columns(), &1))
    )
  end

  defp survival_pipeline do
    Chapter08.fit(
      Chapter08.build_pipeline(2, :balanced),
      Chapter08.features_table(survival_rows()),
      [1, 0, 1, 0]
    )
  end

  defp store_with_models do
    store = FileStore.new(temp_dir("with"))
    FileStore.save_sales_model(store, sales_model())
    FileStore.save_survival_model(store, survival_pipeline())
    store
  end

  defp store_without_models, do: FileStore.new(temp_dir("without"))

  defp call(store_options, conn), do: Api.call(conn, FakeStore.new(store_options))

  defp post_json(path, body), do: conn(:post, path, body)

  defp decoded(conn), do: Jason.decode!(conn.resp_body)

  describe "要求の JSON を読んで型を確かめる" do
    test "型の合う要求はマップとして読める" do
      assert {:ok, %{"sns1" => 100, "sns2" => 2000, "actor" => 300, "original" => 1}} =
               Validation.read_json(
                 ~s({"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}),
                 Validation.movie_types()
               )
    end

    test "JSON として読めないか型が合わなければ :error を返す" do
      for body <- [~s({"sns1": "たくさん"}), ~s({"original": 1.5}), "{ここは JSON ではない", "", "[1, 2]"] do
        assert Validation.read_json(body, Validation.movie_types()) == :error, body
      end
    end

    test "null と表に無い列は型を問わない" do
      assert {:ok, %{"age" => nil, "unknown" => "なんでも"}} =
               Validation.read_json(
                 ~s({"age": null, "unknown": "なんでも"}),
                 Validation.passenger_types()
               )
    end
  end

  describe "映画の特徴量を検証する" do
    test "正しい要求は映画の特徴量になる" do
      validated =
        Validation.movie(%{"sns1" => 100, "sns2" => 2000, "actor" => 300, "original" => 1})

      assert Validation.valid?(validated)
      assert validated.value == %{sns1: 100.0, sns2: 2000.0, actor: 300.0, original: 1}
    end

    test "足りない列は必須として理由を並べる" do
      assert Validation.movie(%{}).errors == [
               "sns1 は必須です",
               "sns2 は必須です",
               "actor は必須です",
               "original は必須です"
             ]
    end

    test "不正な要求には値が無い" do
      assert Validation.movie(%{}).value == nil
    end

    test "負の値と選択肢の外の値を弾く" do
      assert Validation.movie(%{"sns1" => -1, "sns2" => 2000, "actor" => 300, "original" => 2}).errors ==
               ["sns1 は 0 以上にしてください", "original は 0、1 のどれかにしてください"]
    end
  end

  describe "乗客の特徴量を検証する" do
    test "年齢と乗船港は省略できる" do
      validated =
        Validation.passenger(%{
          "pclass" => 1,
          "sex" => "female",
          "sib_sp" => 0,
          "parch" => 0,
          "fare" => 80
        })

      assert Validation.valid?(validated)

      assert validated.value == %{
               pclass: 1,
               sex: "female",
               age: nil,
               sib_sp: 0,
               parch: 0,
               fare: 80.0,
               embarked: nil
             }
    end

    test "選択肢の外の値を弾く" do
      errors =
        Validation.passenger(%{
          "pclass" => 4,
          "sex" => "おんな",
          "sib_sp" => 0,
          "parch" => 0,
          "fare" => 80,
          "embarked" => "X"
        }).errors

      assert errors == [
               "pclass は 1、2、3 のどれかにしてください",
               "sex は female、male のどれかにしてください",
               "embarked は C、Q、S のどれかにしてください"
             ]
    end
  end

  describe "置き場" do
    test "ファイルの置き場は置き場の約束を満たす" do
      StoreContract.check(store_with_models(), store_without_models())
    end

    test "偽物の置き場も置き場の約束を満たす" do
      StoreContract.check(FakeStore.new(@ready), FakeStore.new(%{sales: false, survival: false}))
    end

    test "保存した線形回帰で予測できる" do
      # 10 + 1×200 + 2×500 + 3×3000 + 4×1
      sales = ModelStore.load_sales_model(store_with_models()).(StoreContract.movie())
      assert_in_delta sales, 10_214.0, 1.0e-9
    end

    test "保存したパイプラインで予測できる" do
      assert ModelStore.load_survival_model(store_with_models()).(StoreContract.passenger())
    end

    test "保存先のディレクトリは無ければ作られる" do
      dir = temp_dir("makes-parents")
      FileStore.save_sales_model(FileStore.new(dir), sales_model())
      assert File.dir?(dir)
    end
  end

  describe "サービス" do
    test "置き場からモデルを読んで予測する" do
      store = FakeStore.new(@ready)
      assert Service.predict_sales(store, StoreContract.movie()) == FakeStore.fixed_sales()
      assert Service.predict_survival(store, StoreContract.passenger()) == true
    end

    test "ヘルスチェックはモデルごとに読み込めるかどうかを返す" do
      assert Service.health(FakeStore.new(@ready)) == %{"cinema" => true, "survived" => true}

      assert Service.health(FakeStore.new(%{sales: true, survival: false})) ==
               %{"cinema" => true, "survived" => false}
    end

    test "モデルが無ければ予測は失敗する" do
      store = FakeStore.new(%{sales: false, survival: false})

      assert_raise GettingStartedMl.Chapter15.ModelNotFoundError, fn ->
        Service.predict_sales(store, StoreContract.movie())
      end

      assert_raise GettingStartedMl.Chapter15.ModelNotFoundError, fn ->
        Service.predict_survival(store, StoreContract.passenger())
      end
    end
  end

  describe "予測 API" do
    test "ヘルスチェックはすべて読み込めれば ok を返す" do
      response = call(@ready, conn(:get, "/health"))

      assert response.status == 200

      assert get_resp_header(response, "content-type") ==
               ["application/json; charset=utf-8"]

      assert decoded(response) == %{
               "status" => "ok",
               "models" => %{"cinema" => true, "survived" => true}
             }
    end

    test "読み込めないモデルがあれば degraded を返す" do
      response = call(%{sales: true, survival: false}, conn(:get, "/health"))

      assert decoded(response) == %{
               "status" => "degraded",
               "models" => %{"cinema" => true, "survived" => false}
             }
    end

    test "興行収入を予測する" do
      response =
        call(
          @ready,
          post_json("/cinema/sales", ~s({"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}))
        )

      assert response.status == 200
      assert decoded(response) == %{"sales" => FakeStore.fixed_sales()}
    end

    test "生存を予測する" do
      response =
        call(
          @ready,
          post_json(
            "/survived",
            ~s({"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 80})
          )
        )

      assert response.status == 200
      assert decoded(response) == %{"survived" => true}
    end

    test "検証に落ちた入力は 422 で理由を並べて返す" do
      response =
        call(
          @ready,
          post_json("/cinema/sales", ~s({"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}))
        )

      assert response.status == 422

      assert decoded(response) == %{
               "detail" => ["sns1 は 0 以上にしてください", "original は 0、1 のどれかにしてください"]
             }
    end

    test "JSON として読めない入力も 422 にする" do
      response = call(@ready, post_json("/cinema/sales", ~s({"sns1": "たくさん"})))

      assert response.status == 422
      assert decoded(response) == %{"detail" => ["JSON の形式または値の型が正しくありません"]}
    end

    test "モデルが無ければ 503 を返す" do
      response =
        call(
          %{sales: false, survival: false},
          post_json("/cinema/sales", ~s({"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}))
        )

      assert response.status == 503
      assert decoded(response) == %{"detail" => "学習済みモデル cinema が見つかりません"}
    end

    test "予測が失敗すれば 500 を返し、内部の事情を出さない" do
      response =
        Api.call(
          post_json(
            "/cinema/sales",
            ~s({"sns1": 100, "sns2": 2000, "actor": 300, "original": 1})
          ),
          {GettingStartedMl.Chapter15Test.BrokenStore, nil}
        )

      assert response.status == 500
      assert decoded(response) == %{"detail" => "予測できませんでした"}
    end

    test "知らないパスは 404 を返す" do
      response = call(@ready, conn(:get, "/unknown"))

      assert response.status == 404
      assert decoded(response) == %{"detail" => "見つかりません"}
    end

    test "許していないメソッドは 405 を返す" do
      response = call(@ready, conn(:get, "/cinema/sales"))

      assert response.status == 405
      assert get_resp_header(response, "allow") == ["POST"]
      assert decoded(response) == %{"detail" => "許していないメソッドです"}
    end
  end

  describe "ドメインのアダプター" do
    test "線形回帰のモデルは映画を受け取って数値を返す関数になる" do
      predict = Domain.linear_sales_model(sales_model())
      assert_in_delta predict.(StoreContract.movie()), 10_214.0, 1.0e-9
    end

    test "パイプラインは乗客を受け取って真偽値を返す関数になる" do
      predict = Domain.pipeline_survival_model(survival_pipeline())
      assert predict.(StoreContract.passenger()) == true

      assert predict.(%{
               pclass: 3,
               sex: "male",
               age: 40.0,
               sib_sp: 0,
               parch: 0,
               fare: 8.0,
               embarked: "S"
             }) == false
    end
  end

  describe "実データ" do
    defp trained_store do
      store = FileStore.new(temp_dir("trained"))
      C.train_and_save_models(Dataset.dir(), store)
      store
    end

    @tag :data
    test "実データで学習したモデルを保存して予測できる" do
      store = trained_store()

      # Java 版・Scala 版・Clojure 版と同じ分割・同じ手順なので、予測値もほぼ一致する。
      # 完全には一致せず、Elixir 版は 7730.457421687016（差は 1e-11 未満）。
      # 正規方程式を解くのが Nx.LinAlg.solve か Java の実装かの違いで、丸めの順が変わる
      assert_in_delta Service.predict_sales(store, StoreContract.movie()),
                      7730.457421687023,
                      1.0e-6

      assert Service.predict_sales(store, StoreContract.movie()) === 7730.457421687016
      assert Service.predict_survival(store, StoreContract.passenger()) == true
    end

    @tag :data
    test "保存と読み込みを挟んでも予測値は 1 ビットも変わらない" do
      cinema =
        Chapter07.prepare_cinema(Path.join(Dataset.dir(), "cinema.csv"), 0.2, 0)

      model = Chapter07.fit(cinema.x_train, cinema.t_train, Chapter07.feature_columns())
      direct = Domain.linear_sales_model(model).(StoreContract.movie())

      store = FileStore.new(temp_dir("roundtrip"))
      FileStore.save_sales_model(store, model)

      # :erlang.term_to_binary は浮動小数点数をそのままのビット列で書くので、往復で桁が落ちない
      assert Service.predict_sales(store, StoreContract.movie()) === direct
    end

    @tag :data
    test "実データで学習したモデルを使う API が予測を返す" do
      store = trained_store()

      assert Jason.decode!(Api.call(conn(:get, "/health"), store).resp_body) == %{
               "status" => "ok",
               "models" => %{"cinema" => true, "survived" => true}
             }

      sales =
        Api.call(
          post_json(
            "/cinema/sales",
            ~s({"sns1": 200, "sns2": 500, "actor": 3000, "original": 1})
          ),
          store
        )

      assert_in_delta Jason.decode!(sales.resp_body)["sales"], 7730.457421687023, 1.0e-6

      survived =
        Api.call(
          post_json(
            "/survived",
            ~s({"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 50})
          ),
          store
        )

      assert Jason.decode!(survived.resp_body) == %{"survived" => true}
    end
  end
end

defmodule GettingStartedMl.Chapter15Test.BrokenStore do
  @moduledoc "約束を破る置き場。予測のときに ModelNotFoundError 以外の例外を投げる。"

  @behaviour GettingStartedMl.Chapter15.ModelStore

  @impl true
  def load_sales_model(_state), do: raise(RuntimeError, "内部の秘密")

  @impl true
  def load_survival_model(_state), do: raise(RuntimeError, "内部の秘密")
end
