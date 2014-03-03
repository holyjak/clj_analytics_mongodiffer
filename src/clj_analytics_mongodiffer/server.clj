(ns clj-analytics-mongodiffer.server
  (:require
   [clj-analytics-mongodiffer.core :refer [log] :as data]
   [clj-analytics-mongodiffer.view :as v]
   [ring.adapter.jetty :as jetty]
   ;;[ring.middleware.resource :refer :all]
   [ring.middleware.file-info :refer :all]
   [ring.middleware.file :refer :all]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.stacktrace :refer :all]
   [ring.util.response :as resp]
   [hiccup.bootstrap.middleware :refer [wrap-bootstrap-resources]])
  )

(def latest-diffs
  "Latest prod/stage diffs in the form {:created <date>, :diffs [{:name 'my.coll',..} ..]}"
  (atom nil))

(def service-instance
  "Global var to hold service instance."
  nil)

(defn get-diffs [daydiff]
  "Reload and store the diffs"
  (log "Going to reload the data and diff them...")
  (let [diffs (data/compare-data {:day-diff daydiff})]
    (swap! latest-diffs (constantly diffs))))

(defn get+render-diffs []
  ;; TODO Do we need to sort them by name or are they sorted already by Mongo?
  (v/page @latest-diffs))

(defn show-diffs
  "Show the stage/prod diff page, reloading the data if requested"
  ([] (show-diffs :reload false))
  ([_ reload?]
     (when reload?
       (get-diffs -1))
     {:status 200, :body (get+render-diffs)}))

(defn handler [req]
  (condp = (:uri req)
    "/" (show-diffs)
    "/reload" (let [daydiff (-> req
                                :params
                                (get "daydiff" "-1")
                                (Integer/parseInt))]
                (get-diffs daydiff)
                (resp/redirect "/"))
    {:status 404, :body (str "No handler for the uri '" (:uri req) "'. Supported: /, /reload")}))

(def app
  (-> handler
       (wrap-bootstrap-resources)
       ;;(wrap-resource "static") ; does't work?!
       (wrap-file "resources")
       (wrap-file-info)
       (wrap-params)
       (wrap-stacktrace-web)))

(defn- create-jetty-server []
  (jetty/run-jetty (var app) {:port 8081 :join? false}))

(defn create-server []
  "Standalone dev/prod mode."
  (alter-var-root #'service-instance
                  (constantly (create-jetty-server))))

(defn destroy-server []
  (when service-instance
    (.stop service-instance))
  (alter-var-root #'service-instance (constantly nil)))
