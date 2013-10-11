(ns soac.hopscotch
  "This should be considered as alpha at the moment.  Primitive-backed,
   persistent hash table data structures based on hopscotch hashing."
  (:import [soac.java PersistentPrimHashMap PersistentPrimHashSet])
  (:require [clojure.core.reducers :as r]
            [soac.fj-dupe :as fj]))
(set! *warn-on-reflection* true)

(def ^{:private true} free-val 
  {:int Integer/MIN_VALUE
   :float Float/MIN_VALUE
   :long Long/MIN_VALUE
   :double Double/MIN_VALUE
   :short Short/MIN_VALUE
   :char Character/MIN_VALUE
   :object (Object.)})

(defn- vec-or-vecof
  [type]
  (if (= type :object) (vector) (vector-of type)))

(defn prim-hash-set
  [type]
  (PersistentPrimHashSet/fromProto
    (vec-or-vecof type)
    (get free-val type)))

(defn prim-hash-map
  [key-type val-type]
  (PersistentPrimHashMap/fromProto
    (vec-or-vecof key-type)
    (vec-or-vecof val-type)
    (get free-val key-type)))

(defn fold-kvs
  "Similar impl to foldvec - recursively split both keys and vals until they're
   small enough to be sequentially reduced over, after combining them to a seq
   of MapEntrys"
  [ks vs n combinef reducef]
  (cond 
    (empty? ks) (combinef)
    (<= (count ks) n) (reduce reducef (combinef) (map #(clojure.lang.MapEntry. %1 %2) ks vs))
    :else
    (let [split (quot (count ks) 2)
          ks1 (subvec ks 0 split)
          ks2 (subvec ks split (count ks))
          vs1 (subvec vs 0 split)
          vs2 (subvec vs split (count vs))
          fc (fn [childks childvs] #(fold-kvs childks childvs n combinef reducef))]
      (fj/fjinvoke 
        #(let [f1 (fc ks1 vs1)
               t2 (r/fjtask (fc ks2 vs2))]
           (fj/fjfork t2)
           (combinef (f1) (fj/fjjoin t2)))))))

;Clojure calls clojure.core.protocols/kv-reduce on PersistentPrimHashMap in r/reduce
;and r/coll-fold if you call r/fold directly
(extend-protocol r/CollFold
  PersistentPrimHashSet
  (r/coll-fold
    [v n combinef reducef]
    (let [free (.getFree v)]
      (r/coll-fold (r/filter #(not (.equals free %)) (.getRawKeys v)) 
                   n combinef reducef)))
  PersistentPrimHashMap
  (r/coll-fold
    [v n combinef reducef]
    (let [free (.getFree v)
          mod-reducef #(if (.equals free (key %2)) %1
                         (reducef %1 %2))]
      (fold-kvs (.getRawKeys v) (.getRawVals v) n combinef mod-reducef))))
