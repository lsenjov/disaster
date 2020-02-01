(ns disaster.config
  (:require
    [cprop.core :refer [load-config]]
    [cprop.source :as source]
    [mount.core :refer [args defstate]]))

;; Kinda dirty, but we need to make sure test-config.edn exists if it doesn't
(try (slurp "test-config.edn")
     (catch Exception e
       (spit "test-config.edn"
             (pr-str {}))))

(defstate env
  :start
  (load-config
    :merge
    [(args)
     (source/from-system-props)
     (source/from-env)]))
