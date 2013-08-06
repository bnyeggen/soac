(ns soac.hopscotch
  "This should be considered as alpha at the moment.  Primitive-backed,
   persistent hash table data structures based on hopscotch hashing.")
(set! *warn-on-reflection* true)

;Because the vector fanout is 32, this is probably optimal
(def ^:const neighborhood 32)

;TODO: Support for object vectors, via extending vector-of or delegating to an
;instance fn

(defprotocol PrimHashTable
  ;TODO: findIndex should get the 2 possible .arrayFor, and use primitives or Java
  ;to scan them, rather than re-traversing the tree each time
  (findIndex [this i] 
    "Return the index in the given backing vector where i is found, if it is
     found.")
  (loadFactor [this] "Proportion of slots filled")
  (rehash [this i] [this] "Returns a copy of the table, with 2^i slots.")
  (shift [this free-pos item] 
    "Re-allocate slots to insert item while maintaining guarantees.  Internal
     method, should not be called directly."))

(defn- bit-mod 
  "Bit-shift based modulo-and-make-positive, for sizes that are powers of 2"
  [^long length ^long i]
  (bit-and i (unchecked-dec length)))

(defn- wrapping-inc 
  "Increment i, wrapping by length, which must be a power of 2."
  [^long length ^long i]
  (bit-and (unchecked-inc i) (unchecked-dec length)))

(defn- first-shiftable-hash
  "First position shiftable into that position"
  [^long len ^long pos]
  (->> (unchecked-subtract pos neighborhood)
    (unchecked-inc)
    (bit-mod len)))

(defn- check-position
  "If the item at pos would be in its appropriate neighborhood, return it at
   that position, else nil."
  [v ^long pos item]
  (let [len (count v)
        bottom (bit-mod len (hash item))
        top (bit-mod len (unchecked-add bottom neighborhood))]
    (if (> top bottom)
      (when (and (> top pos) (>= pos bottom) (assoc v pos item))
      (when (or  (> top pos) (>= pos bottom) (assoc v pos item)))))))

(deftype PrimHashSet
  [arr _free ^int _size ^int _capacity _meta]
  PrimHashTable
  (loadFactor [this] (/ (double _size) _capacity))
  (findIndex [this i]
    (loop [pos (bit-mod _capacity (hash i)) ctr 0]
      (when (> neighborhood ctr)
        (if (== i (get arr pos)) pos
          (recur (wrapping-inc _capacity pos)
                 (unchecked-inc ctr))))))
  (rehash [this] (rehash this 1))
  (rehash [this i] 
    (let [new-cap (bit-shift-left _capacity i)]
      (-> (PrimHashSet. (into (.empty ^clojure.lang.IPersistentCollection arr) 
                              (repeat new-cap _free))
                        _free _size new-cap _meta)
        (into (seq this)))))
  (shift [this free-pos e]
    (loop [pos (first-shiftable-hash _capacity free-pos) ct 1]
      (if (<= neighborhood ct)
        (conj (rehash this) e)
        (if-let [shift-up (check-position arr free-pos (nth arr pos))]
          (if-let [out (check-position shift-up free-pos e)]
            out
            (shift shift-up pos e))
          (recur (wrapping-inc _capacity pos)
                 (unchecked-inc ct))))))
  clojure.lang.IObj
  (withMeta [_ m] (PrimHashSet. arr _free _size _capacity m))
  (meta [_] _meta)
  clojure.lang.IPersistentSet
  (cons [this e]
    (if (> (loadFactor this) 0.8) (.cons ^clojure.lang.IPersistentSet (rehash this) e)
      (loop [pos (bit-mod _capacity (hash e)) ctr 0]
        (let [this-element (get arr pos)]
          (if (== _free this-element)
            (let [new-arr (if (> neighborhood ctr)
                            (assoc arr pos e)
                            (shift arr pos e))]
              (PrimHashSet. new-arr _free (unchecked-inc _size) (count new-arr) _meta))
            (if (== e this-element) 
              this
              (recur (wrapping-inc _capacity pos)
                     (unchecked-inc ctr))))))))
  (disjoin [this e]
    ;TODO: Rehash down if we fall below a certain load
    (if-let [pos (findIndex this e)] 
      (PrimHashSet. (assoc arr pos _free) 
                    _free 
                    (unchecked-dec _size)
                    _capacity
                    _meta)
      this))
  (contains [this e] (if (findIndex this e) true false))
  (get [this e] (when (.contains this e) e))
  (count [_] _size)
  (empty [_] (PrimHashSet. (into (.empty ^clojure.lang.IPersistentCollection arr) 
                                 (repeat 32 _free))
                             _free 0 32 nil))
  (equiv [this e] 
    (and 
      (instance? e java.util.Set)
      (== (.size ^java.util.Set e) _size)
      (.containsAll ^java.util.Set e this)))
  (seq [_] (remove #(== % _free) arr))
  clojure.lang.IFn
  (invoke [this k] (.get this k))
  clojure.lang.IHashEq
  (hasheq [this] (reduce + (map hash this)))
  java.util.Set
  (containsAll [this e] (every? (partial contains? this) e))
  (isEmpty [_] (== _size 0))
  (iterator [this] (clojure.lang.SeqIterator. (seq this)))
  (size [_] _size)
  (toArray [this] (clojure.lang.RT/seqToArray (seq this)))
  (toArray [this e] (clojure.lang.RT/seqToPassedArray (seq this) e))
  (remove [_ e] (throw (UnsupportedOperationException.)))
  (removeAll [_ e] (throw (UnsupportedOperationException.)))
  (retainAll [_ e] (throw (UnsupportedOperationException.)))
  (add [_ e] (throw (UnsupportedOperationException.)))
  (addAll [_ e] (throw (UnsupportedOperationException.)))
  (clear [_] (throw (UnsupportedOperationException.))))

;TODO: Make these configurable
(def ^{:private true} free-vals
  {:int Integer/MIN_VALUE
   :double Double/MIN_VALUE
   :float Float/MIN_VALUE
   :long Long/MIN_VALUE
   :short Short/MIN_VALUE
   :byte Byte/MIN_VALUE
   :char Character/MIN_VALUE})

(defn prim-hash-set
  [type] 
  (let [real-length 32 ;Maybe have length be a parameter 
        free-val (get free-vals type)]
    (PrimHashSet. (into (if (not= :object type) (vector-of type) []) 
                        (repeat real-length free-val))
                  free-val
                  0 real-length nil)))

;Not ready yet.  Just to define interfaces.
(deftype PrimHashMap
  [ks vs _free ^int _size ^int _capacity _meta]
  PrimHashTable
  (findIndex [this i]
    (loop [pos (bit-mod _capacity (hash i)) ctr 0]
      (when (> neighborhood ctr)
        (if (== i (get ks pos)) pos
          (recur (wrapping-inc _capacity pos)
                 (unchecked-inc ctr))))))
  (loadFactor [this] (/ (double _size) _capacity))
  (rehash [this i])
  (rehash [this] (rehash this 1))
  (shift [this free-pos item])
  clojure.lang.IObj
  (withMeta [_ m] (PrimHashMap. ks vs _free _size _capacity m))
  (meta [_] _meta)
  clojure.lang.MapEquivalence
  clojure.lang.IHashEq
  (hasheq [_])
  clojure.lang.IPersistentMap
  (assoc [_ k v])
  ;Assoc if not present
  (assocEx [this k v]
    (if (.findIndex this k) (throw (RuntimeException.))
      (assoc this k v)))
  (without [this o]
    (if-let [idx (.findIndex this o)]
      (PrimHashMap. (assoc ks idx _free) vs _free (unchecked-dec _size) _capacity _meta)
      this))
  (iterator [this] (clojure.lang.SeqIterator. (seq this)))
  (entryAt [this k]
    (when-let [idx (.findIndex this k)]
      (clojure.lang.MapEntry. (nth ks idx) (nth vs idx))))
  (count [_] _size)
  (cons [this o]
    (cond (instance? java.util.Map$Entry o)
            (assoc this (.getKey ^java.util.Map$Entry o)
                        (.getValue ^java.util.Map$Entry o))
          (instance? clojure.lang.IPersistentVector o)
            (if (== 2 (count o)) (assoc this (first o) (second o)) 
              (throw (RuntimeException.)))
          :else (loop [out this remaining (seq o)]
                  (if (empty? remaining) out
                    (recur (assoc out (.getKey ^java.util.Map$Entry (first remaining))
                                      (.getValue ^java.util.Map$Entry (first remaining)))
                           (next remaining))))))
  (empty [_] 
    (PrimHashMap.
      (into (.empty ^clojure.lang.IPersistentCollection ks) (repeat 32 _free))
      (into (.empty ^clojure.lang.IPersistentCollection vs) (repeat 32 _free))
      _free 0 32 nil))
  (equiv [_ o])
  (seq [_] (filter #(not= (first %) _free) (map #(clojure.lang.MapEntry. %1 %2) ks vs)))
  (valAt [_ k])
  clojure.lang.IFn
  (invoke [this k] (.valAt this k))
  java.util.Map
  (containsKey [_ k])
  (containsValue [this v])
  (entrySet [this])
  (keySet [_] (PrimHashSet. ks _free _size _capacity nil))
  (values [this] (filter #(not= _free %) vs))
  (size [_] _size)
  (get [this k] (.valAt this k))
  (isEmpty [this] (== 0 _size))
  (put [_ k v] (throw (UnsupportedOperationException.)))
  (putAll [_ m] (throw (UnsupportedOperationException.)))
  (remove [_ k] (throw (UnsupportedOperationException.)))
  (clear [_] (throw (UnsupportedOperationException.))))