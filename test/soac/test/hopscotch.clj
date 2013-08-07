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