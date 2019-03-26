(ns ^:no-doc diehard.util
  (:require [diehard.spec :as ds]
            [clojure.spec.alpha :as s])
  (:import [net.jodah.failsafe.function CheckedRunnable CheckedConsumer
            CheckedFunction]
           [java.util List]
           [java.util.function Predicate BiPredicate]))

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
    (reify Predicate (test [_ c] (boolean (v c))))
    ^List (if (vector? v) v [v])))

(defn predicate [v]
  (reify Predicate
    (test [_ return-value]
      (boolean (v return-value)))))

(defn bipredicate [v]
  (reify BiPredicate
    (test [_ return-value thrown-exception]
      (boolean (v return-value thrown-exception)))))

(defn fn-as-runnable [f]
  (reify CheckedRunnable
    (run [_] (f))))

(defn fn-as-consumer [f]
  (reify CheckedConsumer
    (accept [_ t] (f t))))

(defn fn-as-checked-function [f]
  (reify CheckedFunction
    (apply [_ t] (f t))))
