(defproject diehard "0.7.2-SNAPSHOT"
  :description "Safety utilities for Clojure"
  :url "http://github.com/sunng87/diehard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [net.jodah/failsafe "1.0.5"]
                 [org.clojure/spec.alpha "0.1.143"]]
  :plugins [[lein-codox "0.10.3"]
            [lein-eftest "0.4.0"]]
  :codox {:output-path "docs/"
          :source-uri "https://github.com/sunng87/diehard/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :deploy-repositories {"releases" :clojars})
