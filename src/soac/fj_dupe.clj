; Copyright (c) Rich Hickey. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.


;clojure.core.reducers doesn't make this stuff public, so we copy-paste and
;make it available.  We could also futz with the raw symbols, but this is
;robust to upstream changes.

(ns soac.fj-dupe
  (:require [clojure.core.reducers :as r]))

(defmacro compile-if
  "Evaluate `exp` and if it returns logical true and doesn't error, expand to
  `then`. Else expand to `else`.

  (compile-if (Class/forName \"java.util.concurrent.ForkJoinTask\")
  (do-cool-stuff-with-fork-join)
  (fall-back-to-executor-services))"
  [exp then else]
  (if (try (eval exp)
           (catch Throwable _ false))
    `(do ~then)
    `(do ~else)))

(compile-if
 (Class/forName "java.util.concurrent.ForkJoinTask")
 (do
   (defn fjinvoke [f]
     (if (java.util.concurrent.ForkJoinTask/inForkJoinPool)
       (f)
       (.invoke ^java.util.concurrent.ForkJoinPool @r/pool ^java.util.concurrent.ForkJoinTask (r/fjtask f))))

   (defn fjfork [task] (.fork ^java.util.concurrent.ForkJoinTask task))
   (defn fjjoin [task] (.join ^java.util.concurrent.ForkJoinTask task)))
 (do
   (defn fjinvoke [f]
     (if (jsr166y.ForkJoinTask/inForkJoinPool)
       (f)
       (.invoke ^jsr166y.ForkJoinPool @r/pool ^jsr166y.ForkJoinTask (r/fjtask f))))
   
   (defn fjfork [task] (.fork ^jsr166y.ForkJoinTask task))
   (defn fjjoin [task] (.join ^jsr166y.ForkJoinTask task))))