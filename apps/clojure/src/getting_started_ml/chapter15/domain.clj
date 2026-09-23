(ns getting-started-ml.chapter15.domain
  "第 15 章のドメイン層。予測の入力と、モデル・置き場の約束。HTTP にも EDN にも依存しない。

   Clojure で「置き場の約束」を表す道具は 3 つある。関数を引数で渡す・関数を入れたマップを渡す・
   defprotocol を使う。ここは defprotocol にした。名前の付いた約束が 1 か所にまとまり、
   reify でテスト用の偽物をその場で書け、satisfies? で満たしているかを確かめられるからである。
   ただし defprotocol が決めるのはメソッドの名前と引数の数だけで、
   「無ければ ModelNotFound を投げる」という取り決めは型では書けない（store-contract のテストで確かめる）。

   モデルそのものは protocol にしない。第 10 章の分類器と同じく、モデルは「特徴量を受け取って
   予測を返す関数」でよい。包む型を作らずにアダプターが書ける。"
  (:require [getting-started-ml.chapter07 :as chapter07]
            [getting-started-ml.chapter08 :as chapter08]))

(def sales-model
  "興行収入のモデルの名前。"
  "cinema")

(def survival-model
  "生存予測のモデルの名前。"
  "survived")

(defn model-not-found
  "学習済みモデルが無いことを表す例外。メッセージにファイルのパスを含めない。"
  [model]
  (ex-info (str "学習済みモデル " model " が見つかりません")
           {:type ::model-not-found :model model}))

(defn model-not-found?
  "例外が「モデルが無い」かどうかを返す。API はこれを 503 に変える。"
  [e]
  (= ::model-not-found (:type (ex-data e))))

(defprotocol ModelStore
  "学習済みモデルの置き場の約束。読み込めなければ model-not-found の例外を投げる。"
  (load-sales-model [store] "映画の特徴量から興行収入を返す関数を読み込む。")
  (load-survival-model [store] "乗客の特徴量から生存するかどうかを返す関数を読み込む。"))

(defn linear-sales-model
  "第 7 章の線形回帰のモデルを、興行収入のモデルの約束（映画 → 数値）に合わせる。"
  [model]
  (fn [movie]
    (chapter07/predict-one model {:SNS1 (:sns1 movie)
                                  :SNS2 (:sns2 movie)
                                  :actor (:actor movie)
                                  :original (:original movie)})))

(def ^:private survived 1)

(defn- passenger-row
  "乗客を、第 8 章のパイプラインが読む CSV と同じセルの文字列の行にする。
   分からない値は空欄にすると、学習のときに求めた中央値・最頻値で補完される。"
  [{:keys [pclass sex age sib-sp parch fare embarked]}]
  {:Pclass (str pclass)
   :Sex sex
   :Age (if age (str age) "")
   :SibSp (str sib-sp)
   :Parch (str parch)
   :Fare (str fare)
   :Embarked (or embarked "")})

(defn pipeline-survival-model
  "第 8 章の学習済みパイプラインを、生存予測のモデルの約束（乗客 → 真偽値）に合わせる。"
  [pipeline]
  (fn [passenger]
    (= survived
       (first (chapter08/predict pipeline
                                 (chapter08/features-table [(passenger-row passenger)]))))))
