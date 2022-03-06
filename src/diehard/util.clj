(ns ^:no-doc diehard.util
  (:require [clojure.spec.alpha :as s])
  (:import [dev.failsafe.function CheckedRunnable CheckedConsumer
            CheckedFunction CheckedSupplier CheckedBiPredicate CheckedPredicate]
           [dev.failsafe.event EventListener]))

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
  (cond
    (fn? v) (reify CheckedPredicate (test [_ c] (boolean (v c))))
    (vector? v) (reify CheckedPredicate (test [_ c] (contains? (set v) c)))
    :else (reify CheckedPredicate (test [_ c] (= c v)))))

(defn predicate [v]
  (reify CheckedPredicate
    (test [_ return-value]
      (boolean (v return-value)))))

(defn bipredicate [v]
  (reify CheckedBiPredicate
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

(defn fn-as-checked-supplier [f]
  (reify CheckedSupplier
    (get [_] (f))))

(defn as-vector [v]
  (if (vector? v) v [v]))

(defn wrap-event-listener [f]
  (reify EventListener
    (accept [_ e]
      (f e))))
