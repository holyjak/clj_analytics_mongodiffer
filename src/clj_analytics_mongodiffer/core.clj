(ns clj-analytics-mongodiffer.core
  (:require
   [clojure.test :refer [with-test is are]]
   [clojure.data :as data]
   [monger.core :as mg]
   [monger.collection :as mc] ;; FIXME: ClassNotFoundExc. for M-. on monger.collection or .core; ok on funs
   [monger.db]
   [clj-time.core :as t])
  (:import [com.mongodb MongoOptions ServerAddress]))

(defn log [& msg]
  "Log the message to sysout"
  (locking System/out (println "LOG: " (apply str msg))))

(defn- get-collections-db [db]
  "List non-system collections in the given db"
  (log "Fetching collections from " db)
  (->> (monger.db/get-collection-names db)
       (filter #(not (mc/system-collection? %)))))

(defn get-collections [prod-db stage-db]
  "Map of prod and staging collection names"
  {:production (get-collections-db prod-db),
   :staging (get-collections-db stage-db)})

(defn- occurence-env-keys [env-colls coll]
  "Keys of environments where the given collection coll is present; ex.: #{:production :staging}"
  (->> env-colls
       (filter (fn [[_ colls]] (colls coll)))
       (map first)
       set))

(with-test

  (defn diff-prod+stage [{prod-colls :production, stage-colls :staging}]
    "Return list of {:name <collection name>, :where #{<where found; e.g. :production, :staging>}}"
    (let [p-colls (set prod-colls)
          s-colls (set stage-colls)
          colls (clojure.set/union p-colls s-colls)
          env-colls [[:production p-colls] [:staging s-colls]]]
      (map
       #(identity {:name %,
                   :where (occurence-env-keys env-colls %)})
           colls)))

  (are [result p-coll s-coll] (= result (diff-prod+stage {:production p-coll, :staging s-coll}))
       [] [] []
       [{:name "prod.coll", :where #{:production}}] ["prod.coll"] []
       [{:name "stage.coll", :where #{:staging}}] [] ["stage.coll"]
       [{:name "mycoll", :where #{:production :staging}}] ["mycoll"] ["mycoll"]))

;;; PART 2: COMPARE DATA FOR THE LAST DAY -----------------------------

(defn- epoch-days
  "Number of epoch days til today +- the given number of days"
  ([]
     (epoch-days 0))
  ([days-diff]
     (+
      (t/in-days (t/interval (t/date-time 1970) (t/now)))
      days-diff)))

(defn- epoch-days-to-date [epoch-day]
  (t/plus (t/date-time 1970) (t/days epoch-day)))

(defn latest-doc [db coll day-diff]
  {:pre [(not (nil? db)) coll (integer? day-diff)]}
  "Fetch the document from the collection coll for the epoch day today +- day-diff"
  (let [day (epoch-days day-diff)]
    (mg/with-db db
      (mc/find-one-as-map coll {:epoch_day day})))) ; 15859 = 6/6; latest avail. 15850

;;; Ex.:(latest-docs ctx {:name "movies.timetoplay.day", :where #{:production :staging}} -2)
(defn latest-docs [ctx {coll :name, where :where} day-diff]
  {:pre [(= #{:production :staging} (set (keys ctx)))
         (coll? where)]}
  "Fetch the latest mongo doc for the given day from the given collection if present => {:production <doc map>,..}"
  (let [docs (doall (map #(latest-doc (% ctx) coll day-diff) where))]
    (zipmap where docs)))

(with-test

  (defn diff-data [coll-diff docs]
   "Enhance the coll-diff map with :same true|false|nil (nil if not in both env.), :data {:production <:data from the production doc>}.
   Docs = {:production <mongo document map>, ...}"
   ;; {:prod {:data [..]} -> {:prod [..]}
   (let [prod-data (get-in docs [:production :data])
         stage-data (get-in docs [:staging :data])
         data {:production prod-data,
               :staging stage-data}
         same? (when (and prod-data stage-data)
                (= prod-data stage-data))
         diff (when-not same?
                (data/diff prod-data stage-data))]
     (print ".") ; show progress
     (merge
      coll-diff
      {:same same?, :data data, :diff diff})))

  (are [expected coll-diff docs] (= expected (diff-data coll-diff docs))
      ;; same in both
      {:data {:production "data", :staging "data"}, :same true},
      {}, {:production {:data "data"}, :staging {:data "data"}}
      ;; different data
      {:data {:production "prod-data", :staging "different-data"}, :same false},
      {}, {:production {:data "prod-data"}, :staging {:data "different-data"}}
      ;; only in one env
      {:data {:production "prod-data", :staging nil}, :same nil, :some-key 123},
      {:some-key 123}, {:production {:data "prod-data"}, :staging nil}
      ))

(defn add-data-diffs [ctx diffs day-diff]
  "For each collection: fetch and compare the latest docs/data, add the info.
   See diff-prod+stage."
  (log "Going to fetch data from the given day for each of the " (count diffs) " collections...")
  (map
   (fn [coll-diff]
     (let [docs (latest-docs ctx coll-diff day-diff)]
       (diff-data coll-diff docs)))
   diffs))

;;; Ex.: (clojure.pprint/pprint (->> diffs (filter :same) (map (juxt :name :same))))
(defn compare-data [{:keys [day-diff date] :or {day-diff -1}}]
  "Do all: fetch collections from prod/staging, compare them, compare their last day's data.
   (Runtime ~2 min.)"
  (with-open [prod-conn
              (mg/connect { :host "mongo.staging.example.com" :port 27017 }),
              stage-conn
              (mg/connect { :host "mongo.production.example.com" :port 27017 })]
    (let [compared-date (t/now)
          ctx {:production (mg/get-db prod-conn "mydb"),
               :staging (mg/get-db stage-conn "mydb")}
          env-colls (get-collections (:production ctx) (:staging ctx))
          diffs (diff-prod+stage env-colls)]
      (log (str "Going to fetch & compare data for day " date "...")) ;; only really happens here due to laziness
      ;; force eval of the lazy sequence while within the context of with-open:
      (let
          [result (with-meta
                    (doall (add-data-diffs ctx diffs day-diff))
                    {:epoch-day (epoch-days day-diff),
                     :epoch-date (.toLocalDate (epoch-days-to-date (epoch-days day-diff))),
                     :compared compared-date})]
        (log "Done fetching and comparing data")
        result))))


;;(clojure.test/run-tests)
