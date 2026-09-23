(ns getting-started-ml.chapter15.validation-test
  "第 15 章: 要求の JSON の読み取りと検証のテスト。"
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter15.validation :as v]))

(deftest JSON_を読んで型を確かめる
  (testing "型の合う要求はマップとして読める"
    (is (= {"sns1" 100 "sns2" 2000 "actor" 300 "original" 1}
           (v/read-json "{\"sns1\": 100, \"sns2\": 2000, \"actor\": 300, \"original\": 1}"
                        v/movie-types))))
  (testing "JSON として読めないか型が合わなければ nil を返す"
    (doseq [body ["{\"sns1\": \"たくさん\"}" "{\"original\": 1.5}" "{ここは JSON ではない" "" "[1, 2]"]]
      (is (nil? (v/read-json body v/movie-types)) body)))
  (testing "null と表に無い列は型を問わない"
    (is (= {"age" nil "unknown" "なんでも"}
           (v/read-json "{\"age\": null, \"unknown\": \"なんでも\"}" v/passenger-types)))))

(deftest 映画の特徴量を検証する
  (testing "正しい要求は映画の特徴量になる"
    (let [validated (v/movie {"sns1" 100 "sns2" 2000 "actor" 300 "original" 1})]
      (is (v/valid? validated))
      (is (= {:sns1 100.0 :sns2 2000.0 :actor 300.0 :original 1} (:value validated)))))
  (testing "足りない列は必須として理由を並べる"
    (is (= ["sns1 は必須です" "sns2 は必須です" "actor は必須です" "original は必須です"]
           (:errors (v/movie {})))))
  (testing "不正な要求には値が無い"
    (is (nil? (:value (v/movie {})))))
  (testing "負の値と選択肢の外の値を弾く"
    (is (= ["sns1 は 0 以上にしてください" "original は 0、1 のどれかにしてください"]
           (:errors (v/movie {"sns1" -1 "sns2" 2000 "actor" 300 "original" 2}))))))

(deftest 乗客の特徴量を検証する
  (testing "年齢と乗船港は省略できる"
    (let [validated (v/passenger {"pclass" 1 "sex" "female" "sib_sp" 0 "parch" 0 "fare" 80})]
      (is (v/valid? validated))
      (is (= {:pclass 1 :sex "female" :age nil :sib-sp 0 :parch 0 :fare 80.0 :embarked nil}
             (:value validated)))))
  (testing "選択肢の外の値を弾く"
    (is (= ["pclass は 1、2、3 のどれかにしてください"
            "sex は female、male のどれかにしてください"
            "embarked は C、Q、S のどれかにしてください"]
           (:errors (v/passenger {"pclass" 4 "sex" "おんな" "sib_sp" 0 "parch" 0 "fare" 80
                                  "embarked" "X"}))))))
