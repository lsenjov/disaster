(ns disaster.middleware.oauth2
  "Helpers for getting identities from tokens"
  (:require
    [disaster.config :refer [env]]
    [clojure.tools.logging :as log]
    [clj-http.client :as http]
    [oauth.client :as oauth]
    [disaster.middleware.oauth2-jwt :refer [unsign]]
    ))

(defn- wrap-get-identity-inner
  [request]
  (cond
    ;; It already has an identity, cool
    (:identity request)
    request
    ;; No access tokens? Cool
    (not (get request :oauth2/access-tokens))
    request

    ;; Now we need to do something
    :else
    ;; XXX this just does google at the moment
    (let [token (get-in request [:oauth2/access-tokens :google :id-token])
          endpoint (get-in env [:oauth2 :google :jwks-uri])
          ;; Unsign
          unsigned-token (try (unsign endpoint token)
                              (catch Exception e
                                (log/error "Couldn't unsign token!")
                                nil
                                ))
          ]
      (if unsigned-token
        (assoc-in request [:session :oauth2/identity] unsigned-token)
        request
        ))))
(defn wrap-get-identity
  "If there's no identity on the request, go fetch it and associate it if there's an assigned oauth2 token"
  [handler]
  (fn [request]
    (-> request
        wrap-get-identity-inner
        handler)))
