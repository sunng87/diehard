(ns ^:no-doc diehard.util
  (:require [diehard.spec :as ds]
            [clojure.spec.alpha :as s])
  (:import [net.jodah.failsafe.function Predicate BiPredicate
            CheckedRunnable CheckedBiFunction]
           [java.util List]))

(defn verify-opt-map-keys [opt-map allowed-keys]
  (doseq [k (keys opt-map)]
    (when-not (allowed-keys k)
      (throw (IllegalArgumentException. (str "Policy option map contains unknown key " k))))))

(defn verify-opt-map-keys-with-spec [spec opt-map]
  (let [parsed (s/conform spec opt-map)]
    (if (= parsed ::s/invalid)
      (throw (ex-info "Invalid input" (s/explain-data spec opt-map)))
      parsed)))

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

(defn fn-as-bi-function [f]
  (reify CheckedBiFunction
    (apply [this t u]
      (f t u))))
