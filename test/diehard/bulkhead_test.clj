(ns diehard.bulkhead-test
  (:require [diehard.bulkhead :as sut]
            [clojure.test :as t :refer [deftest testing is]]))

(deftest test-bulkhead
  (testing "only two thread is allow to run bulkhead block"
    (let [bh (sut/bulkhead 2)
          counter (atom 0)
          fun (fn [_]
                (sut/with-bulkhead {:bulkhead bh}
                  (let [v (swap! counter inc)]
                    (Thread/sleep 100)
                    (swap! counter dec)
                    v)))]
      (is (every? #(<= % 2) (pmap #(fun %) (range 20)))))))
