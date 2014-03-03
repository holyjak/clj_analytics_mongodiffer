(defproject clj-analytics-mongodiffer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.novemberain/monger "1.5.0"]
                 [clj-time "0.5.1"]
                 [ring/ring-core "1.1.8"]
                 [ring/ring-jetty-adapter "1.1.8"]
                 [ring/ring-devel "1.1.8"] ;; wrap-stacktrace*
                 [hiccup "1.0.3"]
                 [hiccup-bootstrap "0.1.2"] ; see https://github.com/weavejester/hiccup-bootstrap
                 ]
  :dev-dependencies [[ring-serve "0.1.2"];; nrepl: (use 'ring.util.serve) (serve your-app.core/handler)
                     ]
  :plugins [[lein-ring "0.8.6"]]
  :ring {:handler clj-analytics-mongodiffer.server/app, :auto-refresh? true})
