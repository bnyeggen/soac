(ns soac.core
  (:import [java.lang
            UnsupportedOperationException System IndexOutOfBoundsException]
           [java.util NoSuchElementException]))
(set! *warn-on-reflection* true)

(defprotocol buffered
  "A data structure whose internal representation has some 'growth buffer'
   that can be reduced or expanded without affecting the external
   representation.  Since there is only one implementation this could be a
   vanilla function, except for that it has to modify internal fields."
  (trim! [this])
  (expand! [this]))

;List-iterator over an SOA. Lookups only require the backing data structure
;to be a clojure.lang.Indexed.
(deftype SOAIterator
  [^clojure.lang.Indexed s
   position];atom with -0.5 at beginning
  java.util.ListIterator
  (add [this e] (throw (UnsupportedOperationException.)))
  ;It would be reasonably trivial to implement this against an SOA
  (remove [this] (throw (UnsupportedOperationException.)))
  ;It would be reasonably trivial to implement this against an SOA
  (set [this e] (throw (UnsupportedOperationException.)))
  
  (hasNext [this] (> (count s) (inc @position)))
  (hasPrevious [this] (pos? @position))
  (next [this] (if (.hasNext this)
                 (nth s (int (swap! position inc)))
                 (throw (NoSuchElementException.))))
  (previous [this] (if (.hasPrevious this)
                     (nth s (int (inc (swap! position dec))))
                     (throw (NoSuchElementException.))))
  (nextIndex [this] (int (inc @position)))
  (previousIndex [this] (int @position)))

(deftype SOA
  [^objects arrays
   asetFns
   copyFns
   ^int width
   ^:unsynchronized-mutable ^int realLength
   ^:unsynchronized-mutable ^int filledLength]
  clojure.lang.Indexed
  (count [this] filledLength)
  (nth [this i] 
    (if (>= i filledLength) 
      (throw (IndexOutOfBoundsException.))
      (loop [out (transient [])
             ct (int 0)]
        (if (== ct width) (persistent! out)
          (recur (conj! out (aget arrays ct i))
                 (unchecked-inc-int ct))))))
  (nth [this i notFound]
    (try (nth this i)
      (catch IndexOutOfBoundsException e notFound)))
  clojure.lang.ILookup
  (valAt [this key] (nth this key))
  (valAt [this key notFound] (nth this key notFound))
  java.util.RandomAccess
  java.util.List
  (add [this e]
    (if (> realLength filledLength)
      ;add normally
      (do (dotimes [i width]
            ((nth asetFns i) (aget arrays i) filledLength (nth e i)))
          (set! filledLength (unchecked-inc-int filledLength)))
      ;expand, then add
      (do 
        (expand! this)
        (.add this e)))
    true)
  (add [this i e]
    (if (> realLength filledLength)
      (do 
        (dotimes [array-ct width]
          (System/arraycopy (aget arrays array-ct)
                            i
                            (aget arrays array-ct)
                            (inc i)
                            (- filledLength i -1))
          ((nth asetFns array-ct) (aget arrays array-ct) i (nth e array-ct)))
        (set! filledLength (unchecked-inc-int filledLength)))
      (do (expand! this)
        (.add this i e))))
  (addAll [this c] (doseq [item c] (.add this item)) true)
  (addAll [this i c] 
    ;This could be made more efficient since we know we must move all
    ;the post-i elements of c by (count c)
    (loop [idx i
           remaining (seq c)]
      (when-not (empty? remaining)
        (do (.add this idx (first remaining))
          (recur (unchecked-inc-int i)
                 (rest remaining))))))
  (clear [this] (set! filledLength (int 0)))
  (contains [this o] 
    (pos? (.indexOf this o)))
  (containsAll [this c] (every? (set this) c))
  (equals [this o]
    (every? #(= (first %) (second %)) 
            (map vector this o)))
  (get [this i] (nth this i))
  (indexOf [this o]
    (loop [i (int 0)]
      (if (== i filledLength) -1
        (if (= (nth this i) o) i
          (recur (unchecked-inc-int i))))))
  (isEmpty [this] (== 0 filledLength))
  (iterator [this] (.listIterator this))
  (lastIndexOf [this o]
    (loop [i filledLength]
      (if (== i -1) -1
        (if (= (nth this i) o) i
          (recur (unchecked-dec-int i))))))
  (listIterator [this] (SOAIterator. this (atom -0.5)))
  (listIterator [this index] (SOAIterator. this (atom (- index 0.5))))
  (remove [this ^int index]
    (if (or (< 0 index) (>= index filledLength))
      (throw (IndexOutOfBoundsException.))
      (do (dotimes [i width]
            (System/arraycopy (aget arrays i)
                              (inc index)
                              (aget arrays i)
                              index
                              (- filledLength index 1)))
        (set! filledLength (unchecked-dec-int filledLength)))))
  (^boolean remove [this ^Object o]
    (let [i (.indexOf this o)]
      (if (<= 0 i) (do (.remove this i) true) false)))
  (removeAll [this c] 
    (let [s (set c)]
      (loop [i 0 m false]
        (if (== i filledLength) m
          (if (s (nth this i))
            (do (.remove this (int i))
              (recur (int i) true))
            (recur (unchecked-inc-int i) m))))))
  (retainAll [this c]
    (let [s (set c)]
      (loop [i 0 m false]
        (if (== i filledLength) m
          (if-not (s (nth this i))
            (do (.remove this (int i))
              (recur (int i) true))
            (recur (unchecked-inc-int i) m))))))
  (set [this index element]
    (dotimes [i width]
      ((nth asetFns i) arrays i index (nth element i))))
  (size [this] filledLength)
  ;No intention to support this yet.
  (subList [this fromIndex toIndex] (throw (UnsupportedOperationException.)))
  (toArray [this] (object-array this))
  (toArray [this a]
    (dotimes [i filledLength] (aset a i (nth this i)))
    a)
  buffered
  (trim! [this]
    (dotimes [i width]
      (aset arrays i
        ((nth copyFns i) 
          (aget arrays i) filledLength)))
    (set! realLength filledLength))
  (expand! [this]
    (dotimes [i (alength arrays)]
      (let [this-array (aget arrays i)]
        (aset arrays i 
              ((nth copyFns i) this-array (* 2 realLength)))))
    (set! realLength (int (* 2 realLength)))))

(defn- swap-indexes!
  "Swap the elements in the SOA at positions i and j.  There are currently some
   problems w/ reflection in the aget that prevent this from operating at max
   speed."
  [^SOA s i j]
  (if (>= (max i j) (count s))
    (throw (IndexOutOfBoundsException.))
    (let [old (nth s i)]
      (dotimes [array-idx (.width s)]
        (let [sub-array (aget ^objects (.arrays s) array-idx)
              aset-fn (nth (.asetFns s) array-idx)]
          ;The aget here uses reflection, can't seem to get rid of it
          ;even w/ stored hinted agets
          (aset-fn sub-array i (aget sub-array j))
          (aset-fn sub-array j (nth old array-idx)))))))

(defn sort-SOA-inplace!
  "Does an in-place sort of the SOA (constant auxilary space requirements equal
   to one struct), using the given column as the element to compare, and
   optionally taking a comparator.  If the SOA is not too large, it will be
   faster to sort the seq of structs (or if using Java, sort the .toArray) and
   populate a new SOA.
   As of right now, only allows using a single sort-column as the item for
   comparison - could use the entire outputted struct/vector, and force the
   comparator to extract the necessary information."
  ([^SOA s sort-column cmp]
    (trim! s)
    ;This is a little bit janky to express, we've basically got a doubly nested
    ;loop expressed as doubly conditional recursion
    (let [length (count s)]
      (loop [gap (count s)
             swapped true
             idx 0]
        (cond
          ;Made a complete pass with gap of 1 and no swapping => done
          (and (== idx (dec length)) (== gap 1) (not swapped)) nil
          ;Finished one pass => shrink gap and make another pass
          (>= (+ idx gap) length)
            (recur (int (max 1 (/ gap 1.247330950103979))) false 0)
          ;Inversion => swap the elements in place, mark as swapped, go to next
          ;pair
          (pos? (cmp (aget (.arrays s) sort-column idx)
                     (aget (.arrays s) sort-column (+ idx gap))))
            (do (swap-indexes! s idx (+ idx gap)) 
              (recur gap true (unchecked-inc-int idx)))
          ;Compare next pair of elements
          :else (recur gap swapped (unchecked-inc-int idx))))))
  ([^SOA s sort-column] (sort-SOA-inplace! s sort-column compare)))

;It is possible that instead of these, we could have a macro that constructs
;the hinted call directly. See for example
;http://clj-me.cgrand.net/2009/10/15/multidim-arrays/ 
(defn- copy-booleans [^booleans a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-chars [^chars a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-bytes [^bytes a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-shorts [^shorts a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-ints [^ints a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-longs [^longs a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-floats [^floats a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-doubles [^doubles a ^long s] (java.util.Arrays/copyOf a s))
(defn- copy-objects [^objects a ^long s] (java.util.Arrays/copyOf a s))

(defn ^SOA make-SOA
  "Constructor function - takes any number of keys corresponding to the types
   of the elements of the structs.  Any key not in :boolean, :char, :byte,
   :short, :int, :long, :float, or :double will result as storage in a non-
   primitive object array.  Strings equivalent to the above can also be used
   (handy for Java interop).

   One good pattern is to take a struct and decouple its primitive from non-
   primitive components, storing the latter internally as a nested map in an
   object array, and binding / destructuring while feeding in/out of the SOA.

   Example:
   (make-SOA :int :double :byte :byte :object)"
  [& types]
  ;Initial length should probably be fairly high, or why are you using this?
  (let [init-length 1024]
    (SOA. (object-array
            (for [type types]
              (case (keyword type)
                :boolean (boolean-array init-length)
                :char (char-array init-length)
                :byte (byte-array init-length)
                :short (short-array init-length)
                :int (int-array init-length)
                :long (long-array init-length)
                :float (float-array init-length)
                :double (double-array init-length)
                (object-array init-length))))
          (vec (for [type types]
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
          (vec (for [type types]
                 (case (keyword type)
                   :boolean copy-booleans
                   :char copy-chars
                   :byte copy-bytes
                   :short copy-shorts
                   :int copy-ints
                   :long copy-longs
                   :float copy-floats
                   :double copy-doubles
                   copy-objects)))
          (count types)
          init-length
          0)))
