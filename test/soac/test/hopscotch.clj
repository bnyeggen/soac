(ns soac.test.hopscotch
  (:use clojure.test
        soac.hopscotch))

(deftest test-set
  (let [a (prim-hash-set :int)
        b (into #{} (repeatedly 100 #(rand-int 200)))
        c (into a b)]
    (is (every? #(contains? c %) b))
    (is (= b c))))

(deftest test-map
  (let [a (prim-hash-map :int :double)
        b (into {} (repeatedly 100 #(vector (rand-int 200) (rand))))
        c (into a b)]
    (is (== (count b) (count c)))
    (is (every? #(contains? c %) (keys b)))
    (is (every? #(= (get b %) (get c %)) (keys b)))))

(deftest test-set-disj
  (let [a (into #{} (repeatedly 100 #(rand-int 200)))
        b (take 30 (shuffle (vec a)))
        d (reduce disj (into (prim-hash-set :int) a) b)
        e (reduce disj a b)]
    (is (= d e))))

(deftest test-map-disj
  (let [a (into {} (repeatedly 100 #(vector (rand-int 200) (rand))))
        b (into (prim-hash-map :int :double) a)
        c (take 30 (shuffle (keys a)))
        d (reduce dissoc a c)
        e (reduce dissoc b c)]
    (is (= d e))))

(deftest test-overwrite
  (is (== 1 (count (into (prim-hash-set :int) [4 4]))))
  (let [a (-> (prim-hash-map :int :double) (assoc 1 2.0) (assoc 1 3.0))]
    (is (== 1 (count a)))
    (is (== 3.0 (get a 1)))))

(deftest test-boxing
  (is (contains? (conj (prim-hash-set :int) (int 4)) (long 4)))
  (is (== 1 (count (into (prim-hash-set :int) [(long 4) (int 4)])))))