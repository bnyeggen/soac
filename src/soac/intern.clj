(ns soac.intern
  (:import [java.util.concurrent ConcurrentHashMap]
           [soac.java.intern InternedSuffixPool VectorPrefixPool]))
(set! *warn-on-reflection* true)

;Easier to embed the type hint here than in the macro
(defn- put-if-absent  [^ConcurrentHashMap m k v] 
  (.putIfAbsent m k v))

(defmacro with-interns
  "interns => [intern1 intern2...]
   Evaluates body while making available many functions, bound to the
   symbols in interns. Each fn, when called, returns a deduped reference to
   its argument with respect to any previously-called arguments to that fn.
   
   Example: 
   (with-interns [intern]
     (into {}
       (for [k (range 100000)]
         [k (intern (vec (repeatedly 3 #(rand-int 10))))])))
   There will only be ~1000 underlying 3-long vectors stored on the heap."
  [interns & body]
  (cond (= (count interns) 0)
          `(do ~@body)
        (symbol? (interns 0))
          `(let [intern# (ConcurrentHashMap.)
                 ~(interns 0) #(if-let [v# (put-if-absent intern# % %)] v# %)]
             (with-interns ~(subvec interns 1) ~@body))))

(defn dedupe-seq
  "Returns the given data structure with all equal components replaced by
   pointers to the same interned instance."
  [s]
  (with-interns [intern]
    (into (empty s) (map intern s))))

(defn intern-suffix 
  "Take the given sequence, and attempt to replace as much of the tail as
   possible with existing cached instances from the given pool."
  [^InternedSuffixPool pool s]
  (.intern pool s))

(defmacro with-suffix-intern
  "interns => [intern1 intern2...]
   Binds the given intern symbols to functions which take an ISeq, and return
   an equal copy where, to the extent possible, the tail has been replaced by
   the tails of previous arguments.  Given these functions, execute the body.

   The actual sequence replacement is handled via .cons(), so it is only really
   efficient for linked list-type data structures (existing cons-based seqs, 
   and various Lists, but typically not vector- or array-backed seqs)."
  [interns & body]
  (cond (= (count interns) 0)
          `(do ~@body)
        (symbol? (interns 0))
          `(let [intern# (InternedSuffixPool.)
                 ~(interns 0) (partial intern-suffix intern#)]
             (with-interns ~(subvec interns 1) ~@body))))

(defn intern-vector-prefix
  "Take the given IPersistentVector, and attempt to replace as much of the
   head as possible with shared structure from existing cached instances in the
   given pool."
  [^VectorPrefixPool pool v]
  (.intern pool v))

(defmacro with-vector-prefix-intern
  "interns => [intern1 intern2...]
   Binds the given intern symbols to functions which take an IPersistentVector,
   and return an equal copy where, to the extent possible, the head has been
   replaced by the headss of previous arguments.  Given these functions,
   execute the body."
  [interns & body]
  (cond (= (count interns) 0)
          `(do ~@body)
        (symbol? (interns 0))
          `(let [intern# (VectorPrefixPool.)
                 ~(interns 0) (partial intern-vector-prefix intern#)]
             (with-interns ~(subvec interns 1) ~@body))))