(ns getting-started-ml.chapter15.validation
  "第 15 章のプレゼンテーション層。要求の JSON を読み、検証してドメインの値にする。

   Cheshire は JSON の値をそのまま Clojure のマップ・数値・文字列にするので、
   Java 版のように「読み込みと同時に型が確かめられる」ことはない。型の確認は自分で書く。"
  (:require [cheshire.core :as json]
            [clojure.string :as str])
  (:import [com.fasterxml.jackson.core JsonProcessingException]))

(def movie-types
  "興行収入の予測の要求の列と型。原作の有無は整数で受け取る（1.5 を弾くため）。"
  {"sns1" :number "sns2" :number "actor" :number "original" :integer})

(def passenger-types
  "生存の予測の要求の列と型。年齢と乗船港は省略できる。"
  {"pclass" :integer "sex" :string "age" :number "sib_sp" :integer
   "parch" :integer "fare" :number "embarked" :string})

(defn- typed?
  "値が型に合うかどうか。null と、表に無い列（型が nil）は問わない。"
  [type value]
  (case type
    nil true
    :number (or (nil? value) (number? value))
    :integer (or (nil? value) (integer? value))
    :string (or (nil? value) (string? value))))

(defn read-json
  "本文を JSON のオブジェクトとして読み、列の型を確かめる。読めないか型が合わなければ nil を返す。"
  [body types]
  (try
    (let [request (json/parse-string body)]
      (when (and (map? request)
                 (every? (fn [[field value]] (typed? (get types field) value)) request))
        request))
    (catch JsonProcessingException _ nil)))

;; 検証の規則。問題が無ければ nil を、あれば理由を返す

(defn required
  "必須の列が空なら理由を返す。"
  [field value]
  (when (nil? value) (str field " は必須です")))

(defn not-negative
  "負の数なら理由を返す。"
  [field value]
  (when (and (some? value) (neg? value)) (str field " は 0 以上にしてください")))

(defn one-of
  "選択肢の外の値なら理由を返す。選択肢は並べ替えて表示する。"
  [field value allowed]
  (when (and (some? value) (not (contains? (set allowed) value)))
    (str field " は " (str/join "、" (map str (sort allowed))) " のどれかにしてください")))

(defn validate
  "理由（nil は問題なし）を集め、1 つも無ければ f で値を作る。
   Java 版の sealed interface Validated と違い、値と理由を両方持つマップで表す。"
  [reasons f]
  (let [errors (vec (remove nil? reasons))]
    {:value (when (empty? errors) (f)) :errors errors}))

(defn valid?
  "検証の結果が正しいかどうか。"
  [validated]
  (empty? (:errors validated)))

(def ^:private original-values [0 1])
(def ^:private passenger-classes [1 2 3])
(def ^:private sexes ["female" "male"])
(def ^:private ports ["C" "Q" "S"])

(defn movie
  "検証して、正しければ映画の特徴量にする。"
  [request]
  (let [{:strs [sns1 sns2 actor original]} request]
    (validate [(required "sns1" sns1)
               (required "sns2" sns2)
               (required "actor" actor)
               (required "original" original)
               (not-negative "sns1" sns1)
               (not-negative "sns2" sns2)
               (not-negative "actor" actor)
               (one-of "original" original original-values)]
              #(hash-map :sns1 (double sns1) :sns2 (double sns2)
                         :actor (double actor) :original (long original)))))

(defn passenger
  "検証して、正しければ乗客の特徴量にする。年齢と乗船港は省略できる。"
  [request]
  (let [{:strs [pclass sex age parch fare embarked]} request
        sib-sp (get request "sib_sp")]
    (validate [(required "pclass" pclass)
               (required "sex" sex)
               (required "sib_sp" sib-sp)
               (required "parch" parch)
               (required "fare" fare)
               (one-of "pclass" pclass passenger-classes)
               (one-of "sex" sex sexes)
               (not-negative "age" age)
               (not-negative "sib_sp" sib-sp)
               (not-negative "parch" parch)
               (not-negative "fare" fare)
               (one-of "embarked" embarked ports)]
              #(hash-map :pclass (long pclass) :sex sex
                         :age (when age (double age))
                         :sib-sp (long sib-sp) :parch (long parch) :fare (double fare)
                         :embarked embarked))))
