(clojure-version)
(def ^:dynamic *money*)
(def Money 0)
(binding [*money* (ref Money)])
(alter *money* inc)