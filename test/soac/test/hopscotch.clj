(ns soac.test.hopscotch
  (:use clojure.test
        soac.hopscotch)
  (:require [clojure.core.reducers :as r])
  (:import [java.util HashMap HashSet]))

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
    (is (= d e))
    (is (empty? (reduce disj (into (prim-hash-set :int) (range 100)) (range 100))))))

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

(deftest test-reducers
  (is (->> [1 2 3 4 5] (into (prim-hash-set :int)) (r/reduce +) (== 15)))
  (is (->> [[0 1][2 3][4 5]] (into (prim-hash-map :int :int)) (r/reduce +) (== 15) )))

(deftest ^:performance test-speed
  (let [to-insert (long-array (repeatedly 1000000 #(rand-int Integer/MAX_VALUE)))
        clj-set (do (print "Clojure set insert: ")
                  (time (into #{} to-insert)))
        java-set (do (print "Java set insert: ")
                   (time (let [a (HashSet.)]
                           (doseq [e to-insert] (.add ^HashSet a e))
                           a)))
        imm-set (do (print "Immutable primitive set insert: ")
                  (time (into (prim-hash-set :long) to-insert)))]
    (println)
    (print "Clojure set lookup: ")
    (time (every? #(contains? clj-set %) to-insert))
    (print "Java set lookup: ")
    (time (every? #(.contains ^HashSet java-set %) to-insert))
    (print "Immutable primitive set lookup: ")
    (time (every? #(contains? imm-set %) to-insert))
    (println)
    (print "Clojure set removal: ")
    (time (reduce disj clj-set to-insert))
    (print "Java set removal: ")
    (time (doseq [e to-insert] (.remove ^HashSet java-set e)))
    (print "Immutable primitive set removal: ")
    (time (reduce disj imm-set to-insert))
    (println)
    (print "Clojure set traversal: ")
    (time (doseq [e clj-set]))
    (print "Java set traversal: ")
    (time (doseq [e java-set]))
    (print "Immutable primitive set traversal: ")
    (time (doseq [e imm-set]))))