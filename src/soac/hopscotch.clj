(ns soac.hopscotch
  "This should be considered as alpha at the moment.  Primitive-backed,
   persistent hash table data structures based on hopscotch hashing."
  (:import [soac.java PersistentPrimHashMap PersistentPrimHashSet])
  (:require [clojure.core.reducers :as r]))
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

;Clojure calls clojure.core.protocols/kv-reduce on PersistentPrimHashMap in r/reduce
;and r/coll-fold if you call r/fold directly
(extend-protocol r/CollFold
  PersistentPrimHashSet
  (r/coll-fold
    [v n combinef reducef]
    (let [free (.getFree v)]
      (r/coll-fold (r/filter #(not (.equals free %)) (.getRawKeys v)) 
                   n combinef reducef))))
