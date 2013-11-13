(ns soac.arrvec
  (:import [soac.java.pav PersistentArrayVector PersistentByteArrayVector
            PersistentCharArrayVector PersistentDoubleArrayVector
            PersistentFloatArrayVector PersistentIntArrayVector
            PersistentLongArrayVector PersistentShortArrayVector]))

(defn array-vec
  "The equivalent of clojure.core/vec, but results in a PersistentArrayVector
   backed by a raw Object[], that evolves to a PersistentVector when it
   contains more than 32 elements."
  [targ]
  (PersistentArrayVector/create targ))

(defn array-vector 
  "The equivalent of clojure.core/vector, but results in a PersistentArrayVector
   backed by a raw Object[], that evolves to a PersistentVector when it
   contains more than 32 elements."
  [& contents]
  (PersistentArrayVector/create contents))

(defn array-vector-of
  "Create a typed primitive-array-backed persistent vector, that will evolve
   to a clojure.core.Vec (aka gvec or vector-of) when its elements exceed 256
   bytes worth of storage (that's 32 64-bit elements, and so on)"
  [t & contents]
  (case t
    :byte (PersistentByteArrayVector/create contents)
    :char (PersistentCharArrayVector/create contents)
    :double (PersistentDoubleArrayVector/create contents)
    :float (PersistentFloatArrayVector/create contents)
    :int (PersistentIntArrayVector/create contents)
    :long (PersistentLongArrayVector/create contents)
    :short (PersistentShortArrayVector/create contents)))