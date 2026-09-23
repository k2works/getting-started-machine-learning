(ns getting-started-ml.chapter14
  "第 14 章: K-means によるクラスタリング。

   点は数値のベクタ、点の集まりはそのベクタで表す。中心が変わらなくなるまで
   「割り当て」と「中心の更新」を繰り返し、Tribuo の KMeansTrainer と SSE で比べる。"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter09 :as chapter09]
            [getting-started-ml.dataset :as dataset])
  (:import [java.util.logging Level Logger]
           [org.tribuo MutableDataset]
           [org.tribuo.clustering ClusterID ClusteringFactory]
           [org.tribuo.clustering.kmeans KMeansModel KMeansTrainer KMeansTrainer$Initialisation]
           [org.tribuo.impl ArrayExample]
           [org.tribuo.math.distance L2Distance]
           [org.tribuo.provenance SimpleDataSourceProvenance]))

(def default-max-iterations
  "更新の回数の既定の上限（scikit-learn の KMeans と同じ）。"
  300)

(def default-n-init
  "初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。"
  10)

;; ## 割り当てと中心の更新

(defn squared-distance
  "2 点間の距離の 2 乗。"
  [a b]
  (reduce + (map (fn [x y] (let [d (- x y)] (* d d))) a b)))

(defn assign-clusters
  "各点を、最も近い中心のクラスタ番号に割り当てる。
   距離が同じなら先に並ぶ中心を選ぶように、厳密な < で畳む。"
  [points centers]
  (mapv (fn [point]
          (first (reduce (fn [[nearest best] [k center]]
                           (let [d (squared-distance point center)]
                             (if (< d best) [k d] [nearest best])))
                         [0 Double/POSITIVE_INFINITY]
                         (map-indexed vector centers))))
        points))

(defn update-centers
  "クラスタごとに、割り当てられた点の平均を新しい中心にする。
   点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま残す。"
  [points labels previous]
  (let [members (group-by second (map vector points labels))]
    (mapv (fn [k]
            (if-let [assigned (seq (map first (get members k)))]
              (mapv #(/ % (count assigned)) (apply mapv + assigned))
              (nth previous k)))
          (range (count previous)))))

(defn sum-of-squared-errors
  "各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。"
  [points labels centers]
  (reduce + (map (fn [point label] (squared-distance point (nth centers label)))
                 points labels)))

(defn fit
  "中心が変わらなくなるか、更新の回数が上限に達するまで、割り当てと中心の更新を繰り返す。"
  ([points initial-centers] (fit points initial-centers default-max-iterations))
  ([points initial-centers max-iterations]
   (let [centers (loop [centers initial-centers
                        iteration 0]
                   (if (>= iteration max-iterations)
                     centers
                     (let [next-centers (update-centers points
                                                        (assign-clusters points centers)
                                                        centers)]
                       (if (= next-centers centers)
                         centers
                         (recur next-centers (inc iteration))))))
         labels (assign-clusters points centers)]
     {:labels labels
      :centers centers
      :sse (sum-of-squared-errors points labels centers)})))

;; ## 初期中心と繰り返し

(defn choose-initial-centers
  "シード付きの乱数で点を並べ替え、先頭から n-clusters 個を初期中心にする。
   第 2 章の java.util.Random のシャッフルを使うので、Java 版・Scala 版と同じ点を選ぶ。"
  [points n-clusters seed]
  (mapv #(nth points %)
        (take n-clusters (chapter02/shuffle-with-seed (vec (range (count points))) seed))))

(defn best
  "初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。"
  [points initial-center-candidates]
  (reduce (fn [a b] (if (< (:sse b) (:sse a)) b a))
          (mapv #(fit points %) initial-center-candidates)))

(defn fit-with-restarts
  "シードを 1 ずつずらして初期中心を n-init 通り選び、SSE が最小の結果を返す。"
  ([points n-clusters seed] (fit-with-restarts points n-clusters seed default-n-init))
  ([points n-clusters seed n-init]
   (best points (mapv #(choose-initial-centers points n-clusters (+ seed %)) (range n-init)))))

(defn sse-by-cluster-count
  "クラスタ数ごとに、初期中心を n-init 通り試した最小の SSE を、クラスタ数の順に並べて返す。
   マップは順序を保たないので、[クラスタ数 SSE] の組のベクタにする。"
  ([points cluster-counts seed] (sse-by-cluster-count points cluster-counts seed default-n-init))
  ([points cluster-counts seed n-init]
   (mapv (fn [n] [n (:sse (fit-with-restarts points n seed n-init))]) cluster-counts)))

;; ## Tribuo の KMeansTrainer

(def ^:private threads
  "Tribuo の学習に使うスレッド数。結果を再現できるように 1 にする。"
  1)

(defn- feature-names
  "Tribuo は特徴量を名前の順に並べるので、列の順と名前の順が一致するように 0 埋めする。"
  [dimensions]
  (into-array String (map #(format "x%02d" %) (range dimensions))))

(defn tribuo-dataset
  "点の集まりを、クラスタ番号の無い Tribuo のデータセットにする。"
  [points]
  (let [factory (ClusteringFactory.)
        dataset (MutableDataset. (SimpleDataSourceProvenance. "points" factory) factory)
        names (feature-names (count (first points)))]
    (doseq [point points]
      (.add dataset (ArrayExample. ^ClusterID ClusteringFactory/UNASSIGNED_CLUSTER_ID
                                   ^"[Ljava.lang.String;" names
                                   (double-array point))))
    dataset))

(defn tribuo-centers
  "k-means++ で初期中心を選んで学習し、中心を 1 行に 1 つずつ並べて返す。"
  [points n-clusters seed]
  (let [trainer (KMeansTrainer. n-clusters default-max-iterations (L2Distance.)
                                KMeansTrainer$Initialisation/PLUSPLUS threads seed)
        ^KMeansModel model (.train trainer (tribuo-dataset points))]
    (mapv #(vec (.toArray %)) (.getCentroidVectors model))))

(defn tribuo-sse
  "Tribuo で学習した中心に自作の割り当てをして、自作と同じ定義の SSE を求める。"
  [points n-clusters seed]
  (let [centers (tribuo-centers points n-clusters seed)]
    (sum-of-squared-errors points (assign-clusters points centers) centers)))

(defn tribuo-best-sse
  "シードを 1 ずつずらして n-init 回学習し、最小の SSE を返す。"
  [points n-clusters seed n-init]
  (reduce min (map #(tribuo-sse points n-clusters (+ seed %)) (range n-init))))

;; ## 卸売業者の顧客ごとの支出額

(def ^:private categories
  "区分を表す番号で、支出額ではない列。"
  #{:Channel :Region})

(defn spending
  "Channel と Region を除いた支出額の列を読み込む。欠損値があれば失敗する。"
  [table]
  (let [columns (vec (remove categories (:columns table)))]
    {:columns columns
     :x (mapv (fn [row]
                (into {} (map (fn [column]
                                [column (or (chapter02/number row column)
                                            (throw (IllegalArgumentException.
                                                    (str "空欄があります: " (name column)))))]))
                      columns))
              (:rows table))}))

(defn load-spending
  "CSV を読み込んで支出額の列だけにする。"
  [csv-file]
  (spending (chapter02/load-table csv-file)))

(defn standardize
  "第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点にする。"
  [x columns]
  (let [standardized (chapter09/standardize-all (chapter09/standardizer x columns) x)]
    (mapv (fn [features] (mapv #(get features %) columns)) standardized)))

(defn summarize-clusters
  "クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。"
  [x columns labels]
  (->> (map vector x labels)
       (group-by second)
       (mapv (fn [[cluster members]]
               (let [rows (mapv first members)]
                 {:cluster cluster
                  :count (count rows)
                  :means (into {} (map (fn [column]
                                         [column (/ (reduce + (map #(get % column) rows))
                                                    (count rows))]))
                               columns)})))
       (sort-by (juxt #(- (:count %)) :cluster))
       vec))

;; ## 実データでの実行

(def ^:private seed
  "初期中心の乱数のシード。"
  0)

(def ^:private cluster-counts
  "エルボー法で試すクラスタ数。"
  (vec (range 1 11)))

(def ^:private n-clusters
  "特徴を読むために選んだクラスタ数。"
  5)

(defn run
  "卸売業者の顧客を支出額で K-means にかけ、エルボー法の SSE とクラスタごとの特徴を表示する。"
  []
  ;; Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
  (.setLevel (Logger/getLogger "org.tribuo") Level/WARNING)
  (let [{:keys [columns x]} (load-spending (str (dataset/dir) "/Wholesale.csv"))
        points (standardize x columns)]
    (println (str "データ件数: " (count x) "（支出額 " (count columns) " 列）"))
    (println (str "クラスタ数ごとの SSE（初期中心 " default-n-init " 通りの最小値）:"))
    (println "クラスタ数\t自作\tTribuo（k-means++）")
    (doseq [[n sse] (sse-by-cluster-count points cluster-counts seed)]
      (println (str n "\t" (format "%.2f" sse) "\t"
                    (format "%.2f" (tribuo-best-sse points n seed default-n-init)))))
    (println)
    (println (str "クラスタ数 " n-clusters " のクラスタごとの件数と平均支出額:"))
    (println (str/join "\t" (into ["クラスタ" "件数"] (map name) columns)))
    (doseq [{:keys [cluster means] n :count}
            (summarize-clusters x columns (:labels (fit-with-restarts points n-clusters seed)))]
      (println (str/join "\t" (into [(str cluster) (str n)]
                                    (map #(format "%.0f" (get means %)))
                                    columns))))))
