(ns disaster.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [disaster.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[disaster started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[disaster has shut down successfully]=-"))
   :middleware wrap-dev})
