(ns soac.test.core
  (:use [clojure.test]
        [soac.core])
  (:import [soac.core SOA]))

(deftest test-soa
  (let [a (repeatedly 1000 #(vector (rand) (rand-int 100) (rand)))
        soa (make-SOA :double :int :double)]
    (is (.isEmpty soa))
    
    (.addAll soa a)
    (is (== 1000 (count soa)))
    (is (not (.isEmpty soa)))
    
    (is (= soa a))
    (is (not (= 7 soa)))
    (is (not (= [] soa)))
    
    (trim! soa)
    (is (= soa a))
        
    (is (not (.contains soa [0.0 -1 0.0])))
    (is (== -1 (.indexOf soa [0.0 -1 0.0])))
    
    (.add soa [0.0 -1 0.0])
    (is (== (count soa) 1001))
    (is (.contains soa [0.0 -1 0.0]))
    (is (== 1000 (.indexOf soa [0.0 -1 0.0])))
    
    (.remove soa (int 1000))
    (is (not (.contains soa [0.0 -1 0.0])))
    (is (== (count soa) 1000))
    
    (let [f-elem (first soa)]
      (is (.contains soa f-elem))
      (.remove soa f-elem)
      (is (not (.contains soa f-elem))))
    (.clear soa)
    (is (.isEmpty soa))
    (is (== 0 (count soa)))))

(deftest test-sorting
  (let [soa (doto (make-SOA :double :int :double)
              (.addAll (repeatedly 1000 #(vector (rand) (rand-int 100) (rand)))))]
    (sort-SOA-inplace! soa 0)
    (is (every? #(< (first %) (second %))
                (partition 2 1 (map first (seq soa)))))))

(deftest test-iterators
  (let [soa (doto (make-SOA :double :int :double)
              (.addAll (repeatedly 1000 #(vector (rand) (rand-int 100) (rand)))))
        it (.listIterator soa)]
    (is (thrown? java.util.NoSuchElementException
          (.previous it)))
    (is (= (.next it) (.previous it)))
    (is (nil? (dotimes [_ 1000] (.next it))))
    (is (thrown? java.util.NoSuchElementException
          (.next it)))))