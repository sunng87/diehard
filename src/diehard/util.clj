(ns ^:no-doc diehard.util
  (:import [net.jodah.failsafe.function Predicate BiPredicate CheckedRunnable]
           [java.util List]))

(defn verify-opt-map-keys [opt-map allowed-keys]
  (doseq [k (keys opt-map)]
    (when-not (allowed-keys k)
      (throw (IllegalArgumentException. (str "Policy option map contains unknown key " k))))))

(defn predicate-or-value [v]
  (if (fn? v)
    (reify Predicate (test [_ c] (v c)))
    ^List (if (vector? v) v [v])))

(defn bipredicate [v]
  (reify BiPredicate
    (test [_ return-value thrown-exception]
      (v return-value thrown-exception))))

(defn fn-as-runnable [f]
  (reify CheckedRunnable
    (run [_] (f))))
