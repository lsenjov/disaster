(ns disaster.middleware
  (:require
    [disaster.env :refer [defaults]]
    [disaster.middleware.oauth2 :refer [wrap-get-identity]]
    [cheshire.generate :as cheshire]
    [cognitect.transit :as transit]
    [clojure.tools.logging :as log]
    [disaster.layout :refer [error-page]]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [disaster.middleware.formats :as formats]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [disaster.config :refer [env]]
    [ring-ttl-session.core :refer [ttl-memory-store]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [ring.middleware.oauth2]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
    [buddy.auth.backends.session :refer [session-backend]]))

(defn wrap-internal-error
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-csrf
  [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))


(defn wrap-formats
  [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn on-error
  [request response]
  (error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted
  [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-error}))

(defn wrap-oauth2
  [handler]
  "Add oauth2 routes"
  (-> handler
      (wrap-get-identity)
      (ring.middleware.oauth2/wrap-oauth2 (:oauth2 env))))

(defn wrap-auth
  [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))

(defn wrap-log
  [handler identifier]
  (fn [request]
    (log/trace "Input:" identifier)
    (log/trace (dissoc request :body))
    (let [ret (handler request)]
      (log/trace "Output:" identifier)
      (log/trace (dissoc ret :body))
      ret)))

(defn- fix-debug-host
  "Takes a host string, either like localhost:3000, example.com, or example.com:3000, and strips out the :3000 (or other port) if it exists"
  [host-string]
  (when host-string
    (-> host-string
        (clojure.string/split #":" 2)
        first)))
(defn- fix-debug-scheme
  [scheme-kw]
  (if (= :http scheme-kw)
    :https
    scheme-kw))
(comment
  (fix-debug-host "localhost:3000")
  (fix-debug-host "example.com")
  (fix-debug-host nil))
(defn wrap-fix-debug-host
  "Because of proxying, the host header is likely to get changed to domain.com:3000, especially if localhost.
  We check that header, and update it accordingly"
  [handler]
  (fn [request]
    (-> request
        (update-in [:headers "host"] fix-debug-host)
        (update-in [:scheme] fix-debug-scheme)
        handler)))

(defonce *session-store
  ; 4 hours
  (ttl-memory-store (* 4 60 60)))
(defn wrap-base
  [handler]
  (-> ((:middleware defaults) handler)
      (wrap-log 2)
      wrap-auth
      wrap-oauth2
      wrap-fix-debug-host ;; Makes the oauth2 endpoints work, instead of thinking they're actually at localhost:3000
      (wrap-log 1)
      (wrap-defaults
        (-> site-defaults
            ;; Set to lax instead of leaving as strict, so oauth2 works
            (assoc-in [:session :cookie-attrs :same-site] :lax)
            (assoc-in [:session :store] *session-store)))
      wrap-internal-error))
