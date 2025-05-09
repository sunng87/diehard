clojure -T:build remove-snapshot
clojure -T:build deploy
clojure -T:build bump-patch-version
clojure -T:build add-snapshot
