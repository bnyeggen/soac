(ns soac.core
  (:import [java.lang
            UnsupportedOperationException System IndexOutOfBoundsException]
           [java.util NoSuchElementException Arrays BitSet]
           [java.util.concurrent.atomic AtomicInteger]))
(set! *warn-on-reflection* true)

(def ^:const ARRAY-EXPANSION-FACTOR 1.25)
(def ^:const INITIAL-LENGTH 4)
(def ^:private typed-access-fns (atom {}))

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
(defn- copy-bits [^BitSet a ^long s] (.get a 0 s))

(defn- aget-booleans [^booleans a i] (aget a i))
(defn- aget-chars [^chars a i] (aget a i))
(defn- aget-bytes [^bytes a i] (aget a i))
(defn- aget-shorts [^shorts a i] (aget a i))
(defn- aget-ints [^ints a i] (aget a i))
(defn- aget-longs [^longs a i] (aget a i))
(defn- aget-floats [^floats a i] (aget a i))
(defn- aget-doubles [^doubles a i] (aget a i))
(defn- aget-objects [^objects a i] (aget a i))
(defn- aget-bits [^BitSet a i] (.get a i))

(defn- aset-bit [^BitSet a ^long i v] 
  (.set ^BitSet a i (boolean (not (or (false? v) (nil? v)
                                      (and (number? v) (== v 0)))))))

(defn- expand-size [current] 
  (int (max (inc current) (* ARRAY-EXPANSION-FACTOR current))))

(defn- get-interned-access-fns
  [types]
  (if (contains? @typed-access-fns types)
    (get @typed-access-fns types)
    (-> (swap! typed-access-fns assoc types
               {:asetFns (object-array
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
                               :bit aset-bit
                               aset)))
                :agetFns (object-array 
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
                               :bit aget-bits
                               aget)))
                :copyFns (object-array 
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
                               :bit copy-bits
                               copy-objects)))})
      (get types))))

(defprotocol buffered
  "A data structure whose internal representation has some 'growth buffer'
   that can be reduced or expanded without affecting the external
   representation."
  (trim! [this] "Remove the buffer, minimizing memory usage.  Further adds will
    require an expansion.")
  (expand! [this] "Increase the buffer of the target.")
  (get-raw-buffers [this] [this n] "Return the raw data backing the collection,
    or a particular sub-array"))

;List-iterator over an SOA. Lookups only require the backing data structure
;to be a clojure.lang.Indexed.
;Because Iterator.next() actually resolves and returns the elemtn, this is not
;as speedy as for instance a seq over a vector.
(deftype SOAIterator
  [^clojure.lang.Indexed s
   position];atom with -0.5 at beginning; TODO: Replace this w/ AtomicInteger
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

(deftype SOASeq
  [s ^int position]
  clojure.lang.ISeq
  (count [this] (- (count s) position))
  (cons [this o] (cons o this))
  (empty [this] '())
  (equiv [this o] (every? true? (map = this o)))
  (seq [this] this)
  (first [this] (nth s position))
  (next [this] (if (> (count s) (inc position))
                 (SOASeq. s (inc position)) nil))
  (more [this] (let [m (next this)] (if-not (nil? m) m '())))
  clojure.lang.Indexed
  (nth [this i]
    (nth s (+ position i)))
  (nth [this i notfound]
    (try (nth this i)
      (catch IndexOutOfBoundsException e notfound))))

(deftype SOA
  [^objects arrays
   ^objects asetFns
   ^objects agetFns
   ^objects copyFns
   ^short width ;I sure hope you don't have more than 32k columns
   ^:unsynchronized-mutable ^int realLength
   ^:unsynchronized-mutable ^int filledLength]
  clojure.lang.Seqable
  (seq [this] (SOASeq. this 0))
  clojure.lang.Indexed
  (count [this] filledLength)
  (nth [this i] 
    (if (>= i filledLength) 
      (throw (IndexOutOfBoundsException.))
      (loop [out (transient [])
             ct (int 0)]
        (if (== ct (int width)) (persistent! out)
          (recur (conj! out ((aget agetFns ct) (aget arrays ct) i))
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
            ((aget asetFns i) (aget arrays i) filledLength (nth e i)))
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
          ((aget asetFns array-ct) (aget arrays array-ct) i (nth e array-ct)))
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
    (<= 0 (.indexOf this o)))
  (containsAll [this c] (every? (set this) c))
  (equals [this o]
    (if-not (instance? java.util.Collection o) false
      (every? #(= (first %) (second %))
              (map vector this o))))
  (get [this i] (nth this i))
  (indexOf [this o]
    (loop [i (int 0)]
      (if (== i filledLength) -1
        (if (= (nth this i) o) i
          (recur (unchecked-inc-int i))))))
  (isEmpty [this] (== 0 filledLength))
  (iterator [this] (.listIterator this))
  (lastIndexOf [this o]
    (loop [i (unchecked-dec-int (count this))]
      (if (== i -1) -1
        (if (= (nth this i) o) i
          (recur (unchecked-dec-int i))))))
  (listIterator [this] (SOAIterator. this (atom -0.5)))
  (listIterator [this index] (SOAIterator. this (atom (- index 0.5))))
  (remove [this ^int index]
    (if (or (> 0 index) (>= index filledLength))
      (throw (IndexOutOfBoundsException.))
      (do (dotimes [array-n width]
            (System/arraycopy (aget arrays array-n)
                              (inc index)
                              (aget arrays array-n)
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
      ((aget asetFns i) arrays i index (nth element i))))
  (size [this] filledLength)
  ;No intention to support this yet.
  (subList [this fromIndex toIndex] (throw (UnsupportedOperationException.)))
  (toArray [this] (object-array this))
  (toArray [this a]
    (dotimes [i filledLength] (aset a i (nth this i)))
    a)
  buffered
  (get-raw-buffers [this] arrays)
  (get-raw-buffers [this n] (aget arrays n))
  (trim! [this]
    (dotimes [i width]
      (aset arrays i
        ((aget copyFns i) 
          (aget arrays i) filledLength)))
    (set! realLength filledLength))
  (expand! [this]
    (dotimes [i (alength arrays)]
      (let [this-array (aget arrays i)]
        (aset arrays i 
              ((aget copyFns i) this-array (expand-size realLength)))))
    (set! realLength (int (expand-size realLength)))))

(defn- swap-indexes!
  "Swap the elements in the SOA at positions i and j."
  [^SOA s ^objects aget-fns i j]
  (if (>= (max i j) (count s))
    (throw (IndexOutOfBoundsException.))
    (let [old (nth s i)]
      (dotimes [array-idx (.width s)]
        (let [sub-array (aget ^objects (.arrays s) array-idx)
              aset-fn (aget ^objects (.asetFns s) array-idx)
              aget-fn (aget aget-fns array-idx)]
          ;The aget here uses reflection, can't seem to get rid of it
          ;even w/ stored hinted agets
          (aset-fn sub-array i (aget-fn sub-array j))
          (aset-fn sub-array j (nth old array-idx)))))))

(defn sort-SOA-inplace!
  "Does an in-place sort of the SOA (constant auxilary space requirements equal
   to one struct), using the given column as the element to compare, and
   optionally taking a comparator.  If the SOA is not too large, it will be
   faster to sort the seq of structs and populate a new SOA.
   As of right now, only allows using a single sort-column as the item for
   comparison - could use the entire outputted struct/vector, and force the
   comparator to extract the necessary information.

   Uses a comb sort for average n*log(n) performance."
  ([^SOA s sort-column cmp]
    ;Trimming ensures the underlying arrays will be sorted even including the
    ;unfilled elements (since there will be none).
    (trim! s)
    ;This is a little bit janky to express, we've basically got a doubly nested
    ;loop expressed as doubly conditional recursion
    (let [length (count s)
          sort-array (aget ^objects (.arrays s) sort-column)
          aget-fns (.agetFns s)
          sort-aget-fn (nth aget-fns sort-column)]
      (loop [gap (count s)
             swapped true
             idx 0]
        (cond
          ;Made a complete pass with gap of 1 and no swapping => done
          (and (== idx (dec length)) (== gap 1) (not swapped)) nil
          ;Finished a pass => shrink gap by 1/(1-e^(-phi)) and make another
          ;pass
          (>= (+ idx gap) length)
            (recur (int (max 1 (/ gap 1.247330950103979))) false 0)
          ;Inversion => swap the elements in place, mark as swapped, go to next
          ;pair
          (pos? (cmp (sort-aget-fn sort-array idx)
                     (sort-aget-fn sort-array (+ idx gap))))
          (do (swap-indexes! s aget-fns idx (+ idx gap)) 
            (recur gap true (unchecked-inc-int idx)))
          ;Compare next pair of elements
          :else (recur gap swapped (unchecked-inc-int idx))))))
  ([^SOA s sort-column] (sort-SOA-inplace! s sort-column compare)))

(defn sort-SOA-external!
  "Sort the SOA using external storage, by the given column."
  [^SOA s sort-column]
  ;SOASeq doesn't implement toArray, so we manually put it in
  (let [sorted-structs (sort-by #(nth % sort-column) (object-array s))]
    (.clear s)
    (.addAll s sorted-structs)
    (trim! s)))

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
  (let [access-fns (get-interned-access-fns types)]
    (SOA. (object-array
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
                :bit (BitSet. INITIAL-LENGTH)
                (object-array INITIAL-LENGTH))))
          (:asetFns access-fns)
          (:agetFns access-fns)
          (:copyFns access-fns)
          (count types)
          INITIAL-LENGTH
          0)))

;This is only efficient if you're only conj'ing onto the most recent
;"version".  Editing or expanding from an earlier version will result in quite
;a bit of copying and probably higher total memory usage than a Clojure vector
;with better structural sharing.
(deftype ImmutableSOA
  [;Shared between different seqs - tells you if you're holding an "old"
   ;version and need to copy the backing structure to conj on a (presumably
   ;different) element.  "Split" when backing data diverges due to an edit
   ;or expansion
   ^:unsynchronized-mutable ^AtomicInteger sharedNextUnfilledIndex
   ^objects arrays
   ^objects asetFns
   ^objects agetFns
   ^objects copyFns
   ^short width ;Max of 32k columns
   ^:unsynchronized-mutable ^int realLength
   ^int nextUnfilledIndex]
  clojure.lang.IPersistentCollection
  (count [this] nextUnfilledIndex)
  ;Really, conj - can add anywhere
  (cons [this o]
    (when (== realLength nextUnfilledIndex) (expand! this))
    ;If the element I'm trying to add hasn't been filled by another instance
    ;that shares the same backing data, I can simply add it to the end of my
    ;data and increment both the shared and my own nextUnfilledIndex
    (if (.compareAndSet sharedNextUnfilledIndex 
          nextUnfilledIndex 
          (unchecked-inc-int nextUnfilledIndex))
      ;Add to end and futz w/ counters
      (do (dotimes [array-idx width]
            ((aget asetFns array-idx) 
              (aget arrays array-idx) nextUnfilledIndex (nth o array-idx)))
        (ImmutableSOA. 
          sharedNextUnfilledIndex arrays asetFns agetFns copyFns width realLength
          (unchecked-inc-int nextUnfilledIndex)))
      ;Otherwise, functionally an edit, have to make new copy of backing data
      (let [new-arrays (object-array width)]
        (dotimes [array-idx width]
          (aset new-arrays array-idx 
            ((aget copyFns array-idx) (aget arrays array-idx) realLength))
          ((aget asetFns array-idx) 
            (aget new-arrays array-idx) nextUnfilledIndex (nth o array-idx)))
        (ImmutableSOA.
          (AtomicInteger. (.get sharedNextUnfilledIndex))
          new-arrays
          asetFns
          agetFns
          copyFns
          width
          realLength
          (unchecked-inc-int nextUnfilledIndex)))))
  (equiv [this o]
    (if-not (instance? java.util.Collection o) false
      (every? #(= (first %) (second %))
              (map vector this o))))
  ;TODO: make a bona fide seq that doesn't depend on resolving each element
  ;for traversal.  Should be much faster.
  (seq [this] (SOASeq. this 0))
  clojure.lang.Indexed
  (nth [this i] 
    (if (>= i nextUnfilledIndex) 
      (throw (IndexOutOfBoundsException.))
      (loop [out (transient [])
             ct (int 0)]
        (if (== ct (int width)) (persistent! out)
          (recur (conj! out ((aget agetFns ct) (aget arrays ct) i))
                 (unchecked-inc-int ct))))))
  (nth [this i notFound]
    (try (nth this i)
      (catch IndexOutOfBoundsException e notFound)))  
  clojure.lang.ILookup
  (valAt [this key] (nth this key))
  (valAt [this key notFound] (nth this key notFound))
  java.util.RandomAccess
  buffered
  (get-raw-buffers [this] arrays)
  (get-raw-buffers [this n] (aget arrays n))
  ;In this implementation, trimming only helps if the only reference to the 
  ;current backing arrays goes away, otherwise it's obviously a net space
  ;loss.  Also, currently does not trim away "hidden" elements less than 
  ;firstFilledIndex
  (trim! [this]
    ;"Split" the shared counter, since it will refer to an isolated copy of the
    ;data 
    (set! sharedNextUnfilledIndex (AtomicInteger. (.get sharedNextUnfilledIndex)))
    (dotimes [i width]
      (aset arrays i
        ((aget copyFns i) 
          (aget arrays i) nextUnfilledIndex)))
    (set! realLength nextUnfilledIndex))
  ;Ditto
  (expand! [this]
    (set! sharedNextUnfilledIndex (AtomicInteger. (.get sharedNextUnfilledIndex)))
    (dotimes [i (alength arrays)]
      (let [this-array (aget arrays i)]
        (aset arrays i 
              ((aget copyFns i) this-array (expand-size realLength)))))
    (set! realLength (int (expand-size realLength))))
  java.util.List
  (indexOf [this o]
    (loop [i (int 0)]
      (if (== i (count this)) -1
        (if (= (nth this i) o) i
          (recur (unchecked-inc-int i))))))
  (lastIndexOf [this o]
    (loop [i (unchecked-dec-int (count this))]
      (if (== i -1) -1
        (if (= (nth this i) o) i
          (recur (unchecked-dec-int i))))))
  ;May implement this later
  (subList [this fromIndex toIndex] (throw (UnsupportedOperationException.)))
  
  (listIterator [this] (SOAIterator. this (atom -0.5)))
  (listIterator [this index] (SOAIterator. this (atom (- index 0.5))))
  (contains [this o] (<= 0 (.indexOf this o)))
  (containsAll [this c] (every? (set this) c))
  (equals [this o] (.equiv this o))
  (get [this i] (nth this i))
  (isEmpty [this] (zero? (count this)))
  (iterator [this] (.listIterator this))
  (size [this] (count this))
  (toArray [this] (object-array this))
  (toArray [this a]
    (dotimes [i nextUnfilledIndex] (aset a i (nth this i)))
    a)
  ;No mutatin'
  (add [this e] (throw (UnsupportedOperationException.)))
  (add [this i e] (throw (UnsupportedOperationException.)))
  (addAll [this c] (throw (UnsupportedOperationException.)))
  (addAll [this i c] (throw (UnsupportedOperationException.)))
  (clear [this] (throw (UnsupportedOperationException.)))
  (remove [this ^int index] (throw (UnsupportedOperationException.)))
  (^boolean remove [this ^Object o] (throw (UnsupportedOperationException.)))
  (removeAll [this c] (throw (UnsupportedOperationException.)))
  (retainAll [this c] (throw (UnsupportedOperationException.)))
  (set [this index element] (throw (UnsupportedOperationException.))))

(defn immutable-SOA [& types]
  (let [access-fns (get-interned-access-fns types)]
    (ImmutableSOA. 
      (AtomicInteger. 0)
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
            :bit (BitSet. INITIAL-LENGTH)
            (object-array INITIAL-LENGTH))))
      (:asetFns access-fns)
      (:agetFns access-fns)
      (:copyFns access-fns)
      (count types)
      INITIAL-LENGTH
      0)))

(defn find-sorted
  "Assuming the SOA is sorted by the given column, conduct a binary search
   looking for the given value, and return that element of the SOA.
   Obviously this doesn't work very well for boolean / bit arrays."
  [s col-number targ]
  ;This reflection may or may not actually have performance impacts - finding
  ;in an array of 1m takes ~ 0.1ms even without the hint
  (let 
    [i (-> (get-raw-buffers s col-number) (Arrays/binarySearch targ))]
    (if (>= i 0) (nth s i) nil)))
