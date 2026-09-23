(ns getting-started-ml.dataset-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.dataset :as dataset]))

(deftest 学習データの置き場
  (testing "環境変数が無ければ既定の場所を使う"
    (is (= "../data/sukkiri-ml" (dataset/dir {}))))
  (testing "環境変数があればその場所を使う"
    (is (= "/tmp/data" (dataset/dir {"ML_DATA_DIR" "/tmp/data"}))))
  (testing "環境変数が空なら既定の場所を使う"
    (is (= "../data/sukkiri-ml" (dataset/dir {"ML_DATA_DIR" ""})))))
