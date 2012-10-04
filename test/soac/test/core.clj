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
    (is (== -1 (.lastIndexOf soa [0.0 -1 0.0])))
    
    (.add soa [0.0 -1 0.0])
    (is (== (count soa) 1001))
    (is (.contains soa [0.0 -1 0.0]))
    (is (== 1000 (.indexOf soa [0.0 -1 0.0])))
    (is (== 1000 (.lastIndexOf soa [0.0 -1 0.0])))
    
    (.remove soa (int 1000))
    (is (not (.contains soa [0.0 -1 0.0])))
    (is (== (count soa) 1000))
    
    (let [f-elem (first soa)]
      (is (.contains soa f-elem))
      (.remove soa f-elem)
      (is (not (.contains soa f-elem))))
    (.clear soa)
    (is (.isEmpty soa))
    (is (== 0 (count soa)))
    
    (.add soa [0.0 -1 0.0])
    (.add soa [0.0 -1 0.0])
    (is (== 1 (.lastIndexOf soa [0.0 -1 0.0])))))

(deftest test-immutable-SOA
  (let [s1 (-> (immutable-SOA :double :int :double)
             (conj [0.0 -1 0.0]))]
    (is (== 1 (count s1)))
    (is (= [0.0 -1 0.0] (first s1)))
    (trim! s1)
    (is (== 1 (count s1)))
    (is (= [0.0 -1 0.0] (first s1)))
    (let [e1 [(rand) (rand-int 100) (rand)]
          e2 [(rand) (rand-int 100) (rand)]
          s2 (conj s1 e1)
          s3 (conj s1 e2)]
      (is (== 2 (count s2)))
      (is (= e1 (last s2)))
      ;test twice w/ different elements to check split-and-edit functionality
      (is (== 2 (count s3)))
      (is (= e2 (last s3)))
      (is (not= s2 s3)))))

(deftest test-sorting
  (let [soa (doto (make-SOA :double :int :double)
              (.addAll (repeatedly 1000 #(vector (rand) (rand-int 100) (rand)))))]
    (sort-SOA-inplace! soa 0)
    (is (every? #(< (first %) (second %))
                (partition 2 1 (map first (seq soa)))))))

(deftest test-SOA-iterators
  (let [soa (doto (make-SOA :double :int :double)
              (.addAll (repeatedly 1000 #(vector (rand) (rand-int 100) (rand)))))
        it (.listIterator soa)]
    (is (thrown? java.util.NoSuchElementException
          (.previous it)))
    (is (= (.next it) (.previous it)))
    (is (nil? (dotimes [_ 1000] (.next it))))
    (is (thrown? java.util.NoSuchElementException
          (.next it)))))

(deftest test-immutable-SOA-iterators
  (let [soa (into (immutable-SOA :double :int :double)
              (repeatedly 1000 #(vector (rand) (rand-int 100) (rand))))
        it (.listIterator soa)]
    (is (thrown? java.util.NoSuchElementException
          (.previous it)))
    (is (= (.next it) (.previous it)))
    (is (nil? (dotimes [_ 1000] (.next it))))
    (is (thrown? java.util.NoSuchElementException
          (.next it)))))

(deftest test-immutable-SOA-seq
  (let [soa (into (immutable-SOA :double :int :double)
                  (repeatedly 1000 #(vector (rand) (rand-int 100) (rand))))
        sseq (seq soa)]
    (is (== 1000 (count sseq) (count soa)))
    (is (= [0.0 1 0.0] (first (cons [0.0 1 0.0] sseq))))
    (is (= (last soa) (last sseq)))
    (is (= (first soa) (first sseq)))))

(deftest test-speed
  (let [s (vec (repeatedly 500000 
                 #(vector (rand) (rand-int 100) (rand))))
        soa (make-SOA :double :int :double)
        im-soa (immutable-SOA :double :int :double)]
    (print "Vector construction: ")
    (time (into [] s))
    (print "Immutable SOA construction: ")
    (time (into im-soa s))
    (print "Mutable SOA contruction: ")
    (time (.addAll soa s)))
  (let [v (vec (repeatedly 262144 
                 #(vector (rand) (rand-int 100) (rand))))
        im-soa (into (immutable-SOA :double :int :double) v)
        soa (doto (make-SOA :double :int :double) (.addAll v))]
    (print "Vector traversal: ")
    (time (last v))
    (print "Immutable SOA traversal: ")
    (time (last im-soa))
    (print "Mutable SOA traversal: ")
    (time (last soa))))