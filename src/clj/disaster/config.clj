(ns disaster.config
  (:require
    [disaster.config.gcloud :refer [get-all-config-secrets]]
    [cprop.core :refer [load-config]]
    [cprop.source :as source]
    [mount.core :refer [args defstate]]))

(defstate env
  :start
  (load-config
    :merge
    (concat
      [(args)
       (source/from-system-props)
       (source/from-env)]
      ;; Go get the secrets from gcloud store: if able
      (get-all-config-secrets))))
