(ns getting-started-ml.chapter08
  "第 8 章: 実践的な分類と前処理パイプライン。タイタニック号の乗客データを扱う。"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter08.transformers :as tr]
            [getting-started-ml.chapter08.tree :as tree]
            [getting-started-ml.dataset :as dataset]))

(def feature-columns
  "モデルに渡す特徴量の列。PassengerId・Ticket・Cabin は使わない。"
  [:Pclass :Sex :Age :SibSp :Parch :Fare :Embarked])

(def target
  "正解ラベルの列（1 が生存、0 が死亡）。"
  :Survived)

(defn features-table
  "行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。"
  [rows]
  {:columns feature-columns :rows (vec rows)})

(defn target-labels
  "行の Survived 列を、整数の正解ラベルにする。"
  [rows]
  (mapv #(Long/parseLong (chapter02/text % target)) rows))

(defn build-pipeline
  "Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。"
  [max-depth class-weight]
  {:transformers [(tr/group-median-imputer :Age [:Pclass :Sex])
                  (tr/most-frequent-imputer :Embarked)
                  (tr/dummy-encoder [:Sex :Embarked])]
   :max-depth max-depth
   :class-weight class-weight})

(defn to-features
  "前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。"
  [x]
  (mapv (fn [row]
          (into {} (map (fn [column]
                          (if-let [value (chapter02/number row column)]
                            [column value]
                            (throw (IllegalArgumentException.
                                    (str "欠損値が残っています: " (name column)))))))
                (:columns x)))
        (:rows x)))

(defn fit
  "訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。"
  [pipeline x t]
  (let [[fitted prepared]
        (reduce (fn [[fitted prepared] transformer]
                  (let [f (tr/fit transformer prepared)]
                    [(conj fitted f) (tr/transform f prepared)]))
                [[] x]
                (:transformers pipeline))]
    {:transformers fitted
     :columns (:columns prepared)
     :tree (tree/fit (to-features prepared) t (:columns prepared)
                     (:max-depth pipeline) (:class-weight pipeline))}))

(defn transform
  "学習済みの前処理を順に合成して、データを変換する。"
  [fitted-pipeline x]
  (reduce (fn [prepared f] (tr/transform f prepared)) x (:transformers fitted-pipeline)))

(defn features
  "前処理をして、モデルに渡す特徴量にする。"
  [fitted-pipeline x]
  (to-features (transform fitted-pipeline x)))

(defn predict
  "前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。"
  [fitted-pipeline x]
  (tree/predict (:tree fitted-pipeline) (features fitted-pipeline x)))

;; 学習済みのパイプラインの保存と読み込み

(def ^:private format-version
  "形式の版。読み込むときに確かめる。"
  1)

(defn save-model
  "学習済みのパイプライン（前処理で求めた値とモデル）を EDN で保存する。"
  [fitted-pipeline model-file]
  (io/make-parents model-file)
  (spit model-file (pr-str (assoc fitted-pipeline :format format-version))))

(defn load-model
  "保存したパイプラインを読み込む。形式が違えば失敗する。"
  [model-file]
  (let [loaded (edn/read-string (slurp model-file))]
    (when-not (= format-version (:format loaded))
      (throw (IllegalArgumentException.
              (str "対応していない形式のモデルです: " (:format loaded)))))
    (dissoc loaded :format)))

;; 評価

(def ^:private survived 1)

(defn- accuracy
  "予測が正解ラベルと一致した割合。"
  [predictions labels]
  (/ (double (count (filter true? (map = predictions labels)))) (count labels)))

(defn evaluate
  "学習済みのパイプラインを、訓練データとテストデータで評価する。"
  [fitted-pipeline split]
  (let [predictions (predict fitted-pipeline (features-table (:x-test split)))
        labels (:t-test split)]
    {:train-accuracy (accuracy (predict fitted-pipeline (features-table (:x-train split)))
                               (:t-train split))
     :test-accuracy (accuracy predictions labels)
     :found-survivors (count (filter (fn [[p l]] (and (= survived p) (= survived l)))
                                     (map vector predictions labels)))
     :survivors (count (filter #(= survived %) labels))}))

;; 実行

(def model-file
  "学習済みのパイプラインの保存先（apps/clojure/model/ は .gitignore の対象）。"
  "model/survived.model")

(def ^:private test-size 0.2)
(def ^:private seed 0)
(def ^:private max-depth 5)

(def ^:private new-passengers
  "年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性）。"
  (features-table [(zipmap feature-columns ["1" "female" "" "0" "0" "50" "C"])
                   (zipmap feature-columns ["3" "male" "" "0" "0" "8" "S"])]))

(defn run
  "クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。"
  ([] (run model-file))
  ([model-file]
   (let [rows (:rows (chapter02/load-table (str (dataset/dir) "/Survived.csv")))
         t (target-labels rows)
         split (chapter02/split-train-test rows t test-size seed)
         survivor-count (count (filter #(= survived %) t))]
     (println (str "データ件数: " (count rows)
                   "（生存 " survivor-count ", 死亡 " (- (count t) survivor-count) "）"))
     (println (str "訓練データ: " (count (:x-train split)) " 件, "
                   "テストデータ: " (count (:x-test split)) " 件"))
     (let [pipelines
           (into {}
                 (map (fn [class-weight]
                        (let [pipeline (fit (build-pipeline max-depth class-weight)
                                            (features-table (:x-train split)) (:t-train split))
                              result (evaluate pipeline split)]
                          (println (format "classWeight=%s: 訓練 %.3f, テスト %.3f, 生存者 %d 人中 %d 人を発見"
                                           (name class-weight)
                                           (:train-accuracy result) (:test-accuracy result)
                                           (:survivors result) (:found-survivors result)))
                          [class-weight pipeline])))
                 tree/class-weights)]
       (save-model (get pipelines :balanced) model-file)
       (println (str "保存したモデル: " (.getName (io/file model-file))))
       (println (str "架空の乗客の予測: " (predict (load-model model-file) new-passengers)))))))
