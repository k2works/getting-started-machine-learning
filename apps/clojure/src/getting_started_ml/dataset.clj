(ns getting-started-ml.dataset
  "学習データのディレクトリを求める。")

(def env-name
  "実データの置き場をテストや CI から差し替えるための環境変数の名前。"
  "ML_DATA_DIR")

(def default-dir
  "既定の置き場。テストは apps/clojure で走るので、相対パスで apps/data に届く。"
  "../data/sukkiri-ml")

(defn dir
  "学習データのディレクトリを返す。環境変数はテストで差し替えられるように引数で受け取る。"
  ([] (dir (System/getenv)))
  ([env]
   (let [value (get env env-name)]
     (if (or (nil? value) (empty? value)) default-dir value))))
