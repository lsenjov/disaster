(ns disaster.config.gcloud
  "For getting config details from gcloud secret manager"
  (:require
    [clojure.tools.logging :as log]
    [clojure.core.async :as async]
    [clojure.edn]
    [cprop.tools])
  (:import
    (com.google.cloud.secretmanager.v1beta1 SecretManagerServiceClient ProjectName AccessSecretVersionRequest)))

(def secret-names
  "A list of all secrets with config items we need"
  ["projects/539558190007/secrets/config_oauth2"])

;; So, I have no idea _why_, but if we do a with-open on this exact thing,
;; it fails due to being unable to schedule something in the thread pool
;; So we initialise it and just kinda... let it run free?
(defn create-client
  []
  (try
    (SecretManagerServiceClient/create)
    ;; Likely to fail because of inability to get credentials in test environ
    (catch Exception e
      (do
        (log/error "Could not create secret client:" e)
        nil))))

(defn fetch-secret
  "Given a secret version name string, returns the value"
  [client ^String secret-version-name]
  (-> client
      (.accessSecretVersion
        ;; We need to construct an actual request
        (-> (AccessSecretVersionRequest/newBuilder)
            (.setName secret-version-name)
            .build))
      .getPayload
      .getData
      .toStringUtf8))
(defn process-secret
  "Try and get the value stored in the secret. Return an empty map if failure"
  [client secret-name]
  (try
    (->> secret-name
         ;; Get all secrets
         (.listSecretVersions client)
         ;; Get the list
         .iterateAll .iterator iterator-seq
         ;; We only care about the most recent secret.
         first
         .getName
         ;; We have the name of the most recent version, go get it
         (fetch-secret client)
         ;; We expect to be reading edn maps from these config secrets
         (clojure.edn/read-string))
    (catch Exception e
      (do
        (log/error "Could not fetch secret versions for:" secret-name)
        (log/error "Exeption:" e)
        {}))))
(defn get-all-config-secrets
  "Gets a list of all config secrets in a map. Throws an exception if it something fails"
  ([]
   (if-let [client (create-client)]
     (let [ret (get-all-config-secrets client)]
       ;; Problem is we're closing things too early. Close it in 3 seconds time and ignore it in the meantime
       (async/go (Thread/sleep 3000) (.close client))
       ;; Actually return the response
       ret)))
  ([client]
   ;; If we don't let it sleep a moment, it fails because the library needs to extract its own library
   ;; But it doesn't have a "oh yeah I'll wait a fucking moment and tell you when it's done"
   (if client
     (do
       (Thread/sleep 1000)
       (map (partial process-secret client) secret-names))
     [] ; return empty collection on failure
     )))
