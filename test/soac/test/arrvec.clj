(ns soac.test.arrvec
  (:use clojure.test
        soac.arrvec)
  (:import [soac.java.pav PersistentArrayVector]))

(deftest test-arrvec
  (let [s (repeatedly PersistentArrayVector/PERSISTENT_VECTOR_THRESHOLD #(rand-int 1000))
        v (vec s)
        arv (array-vec s)]
    (is (= v arv))
    (is (= (conj v -1) (conj arv -1)))
    (is (= (pop v) (pop arv)))
    (is (= (assoc v 0 -1) (assoc arv 0 -1)))
    (is (empty? (.empty ^PersistentArrayVector arv)))
    (is (instance? PersistentArrayVector arv))
    (is (instance? clojure.lang.PersistentVector (conj arv 99)))))