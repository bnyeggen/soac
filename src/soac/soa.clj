(ns soac.soa
  (:import [soac.java.soa ImmutableArraySOA MutableSOA PersistentVectorSOA])
  (:require [soac.fj-dupe]))
(set! *warn-on-reflection* true)

(def ^:private typed-access-fns (atom {}))
(def ^:const INITIAL-LENGTH 4)

;Macro-generated versions of these are somehow slower in tests.
(defn- copy-booleans [^booleans a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-chars [^chars a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-bytes [^bytes a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-shorts [^shorts a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-ints [^ints a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-longs [^longs a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-floats [^floats a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-doubles [^doubles a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-objects [^objects a ^long s] (java.util.Arrays/copyOf a s))

(defn- aget-booleans [^booleans a i] (aget a i))
(defn- aget-chars [^chars a i] (aget a i))
(defn- aget-bytes [^bytes a i] (aget a i))
(defn- aget-shorts [^shorts a i] (aget a i))
(defn- aget-ints [^ints a i] (aget a i))
(defn- aget-longs [^longs a i] (aget a i))
(defn- aget-floats [^floats a i] (aget a i))
(defn- aget-doubles [^doubles a i] (aget a i))
(defn- aget-objects [^objects a i] (aget a i))

(defn- get-interned-access-fns
  [types]
  (if (contains? @typed-access-fns types)
    (get @typed-access-fns types)
    (-> (swap! typed-access-fns assoc types
               {:asetFns (into-array clojure.lang.IFn
                           (for [type types]
                             (case (keyword type)
                               :boolean aset-boolean
                               :char aset-char
                               :byte aset-byte
                               :short aset-short
                               :int aset-int
                               :long aset-long
                               :float aset-float
                               :double aset-double
                               aset)))
                :agetFns (into-array clojure.lang.IFn 
                           (for [type types]
                             (case (keyword type)
                               :boolean aget-booleans
                               :char aget-chars
                               :byte aget-bytes
                               :short aget-shorts
                               :int aget-ints
                               :long aget-longs
                               :float aget-floats
                               :double aget-doubles
                               aget)))
                :copyFns (into-array clojure.lang.IFn 
                           (for [type types]
                             (case (keyword type)
                               :boolean copy-booleans
                               :char copy-chars
                               :byte copy-bytes
                               :short copy-shorts
                               :int copy-ints
                               :long copy-longs
                               :float copy-floats
                               :double copy-doubles
                               copy-objects)))})
      (get types))))

(defn ^MutableSOA mutable-SOA
  [& types]
  ;Initial length should probably be fairly high, or why are you using this?
  (let [access-fns (get-interned-access-fns types)]
    (MutableSOA.
      (object-array
            (for [type types]
              (case (keyword type)
                :boolean (boolean-array INITIAL-LENGTH)
                :char (char-array INITIAL-LENGTH)
                :byte (byte-array INITIAL-LENGTH)
                :short (short-array INITIAL-LENGTH)
                :int (int-array INITIAL-LENGTH)
                :long (long-array INITIAL-LENGTH)
                :float (float-array INITIAL-LENGTH)
                :double (double-array INITIAL-LENGTH)
                (object-array INITIAL-LENGTH))))
      (:copyFns access-fns)
      (:asetFns access-fns)
      (:agetFns access-fns)
      0
      INITIAL-LENGTH)))

(defn ^ImmutableArraySOA immutable-SOA [& types]
  (let [access-fns (get-interned-access-fns types)]
    (ImmutableArraySOA.
      (object-array
        (for [type types]
          (case (keyword type)
            :boolean (boolean-array INITIAL-LENGTH)
            :char (char-array INITIAL-LENGTH)
            :byte (byte-array INITIAL-LENGTH)
            :short (short-array INITIAL-LENGTH)
            :int (int-array INITIAL-LENGTH)
            :long (long-array INITIAL-LENGTH)
            :float (float-array INITIAL-LENGTH)
            :double (double-array INITIAL-LENGTH)
            (object-array INITIAL-LENGTH))))
      (:copyFns access-fns)
      (:asetFns access-fns)
      (:agetFns access-fns)
      (java.util.concurrent.atomic.AtomicInteger. 0)
      0
      INITIAL-LENGTH)))

(defn ^PersistentVectorSOA vector-SOA [& types]
  (let [access-fns (get-interned-access-fns types)]
    (PersistentVectorSOA. 
      (into-array clojure.lang.IPersistentVector 
        (for [type types :let [k (keyword type)]]
          (if (#{:boolean :char :byte :short :int :long :float :double} k)
            (vector-of k)
            []))))))

(defn- foldlist
  [^java.util.List v n combinef reducef]
  (cond
   (empty? v) (combinef)
   (<= (count v) n) (reduce reducef (combinef) v)
   :else
   (let [split (quot (.size v) 2)
         v1 (.subList v 0 split)
         v2 (.subList v split (.size v))
         fc (fn [child] #(foldlist child n combinef reducef))]
     (soac.fj-dupe/fjinvoke
      #(let [f1 (fc v1)
             t2 (clojure.core.reducers/fjtask (fc v2))]
         (soac.fj-dupe/fjfork t2)
         (combinef (f1) (soac.fj-dupe/fjjoin t2)))))))

(extend-protocol clojure.core.reducers/CollFold
  MutableSOA
  (coll-fold
    [coll n combinef reducef]
    (foldlist coll n combinef reducef)))
