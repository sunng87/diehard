(ns diehard.spec-test
  (:require [diehard.spec :as s]
            [diehard.util :as u]
            [clojure.spec.alpha :as cs]
            [clojure.test :as t :refer [deftest testing is]]))

(deftest spec-check-test
  (is (= {:concurrency 2}
         (u/verify-opt-map-keys-with-spec :bulkhead/bulkhead-new {:concurrency 2})))
  (try
    (u/verify-opt-map-keys-with-spec :bulkhead/bulkhead-new {:concurrency "2"})
    (is false)
    (catch Exception e
      (is (not-empty (:clojure.spec.alpha/problems (ex-data e)))))))
