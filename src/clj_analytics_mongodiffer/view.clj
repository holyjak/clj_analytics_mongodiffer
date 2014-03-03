(ns clj-analytics-mongodiffer.view
  (:require clojure.data
            [clojure.walk :refer [prewalk]]
            [hiccup.core :refer :all]
            [hiccup.page :refer :all]
            [hiccup.bootstrap.page :refer :all]
            [clojure.pprint]
            [clj-time.core :as t]))

(def default-to-fake "Use the fake data if no real data supplied (for development)" true)

(def fake-data "Fake data for testing w/o the DB; see default-to-fake"
  ^{:epoch-day 0,
    :epoch-date (t/now)
    :compared (t/now)}
  [{:name "coll.bothSame.day.SKU_X" :where #{:production :staging} :same true :data {:production [["k" 1]], :staging [["k" 1]]}}
   {:name "coll.stage1.day.GRP" :where #{:staging} :same nil :data {:staging [["y" 100]]}}
   {:name "coll.stage2" :where #{:staging} :same nil :data {:staging [(vec (repeat 50 ["x" 123]))]}}
   {:name "coll.prod" :where #{:production} :same nil :data {:production [["k" 1]]}}
   (let [prod [["same" 0] ["k" 1] ["same2" 2]],
         stage [["same" 0] ["k" 2] ["same2" 2]]]
     {:name "coll.bothDiff.day"
     :where #{:production :staging},
     :same false,
     :data {:production prod, :staging stage},
     :diff (clojure.data/diff prod stage)})])

(defn hide-if [cond attrs]
  "Add html/css attributes to the given attrs map to hide the element if cond is true"
  (if cond
    (merge-with str attrs {:class "initially-hidden collapse "})
    attrs))

(defn render-same [same]
  "Marker of whether data in both envs is the same: nothing, green 'OK', or red 'DIFF'."
  (cond
   (nil? same) ""
   same [:span.text-success "OK"]
   :else [:span.text-error "DIFF"]))

(defn color-for-envs [env where]
  "Background color for a collection that is only in one env: green as 'only here', redish as 'missing here'"
  (cond
   (= #{:production :staging} where) ""
   (where env) "rgb(193, 252, 185)"
   :else "rgb(255, 174, 163)"))

(defn render-coll [env {:keys [name where same hide-initially?]}]
  "Collections overview - render a single collection (diff)"
  [:li (hide-if hide-initially? {:style (str "background-color:" (color-for-envs env where) ";")})
   (render-same same) " "
   [:span
    ;; if only in one environment, namely the other one -> do not show the name as not here
    (when (and (nil? same) (not (contains? where env))) {:style "visibility:hidden;"})
    name]])

(defn render-colls [env diffs]
  "Render the overview list of Mongo collections"
  [:div
   [:p [:em "(Showing only collections containing '.day' in the name by default.)"]]
   [:ol.collection-overview
     (map (partial render-coll env) diffs)]])

(defn render-data-content [env-name data]
  "Data from the mongo document"
  [:pre
   {:style "font-size:9px"}
   env-name ":"  (let [data-str (clojure.pprint/cl-format nil "~A" data)]
                   (if (> (count data-str) 253)
                     (str (subs data-str 0 255) "...")
                     data-str))])

(defn hide-data-initially? [{:keys [same]}]
  (not (false? same)))

(defn render-data-diff "Render clojure.data/diff outcome" [[only-prod only-stage same]]
  (->>
   (map (fn [p s] (when (and p s) {:p p, :s s})) only-prod only-stage)
   (partition-by nil?)
   (mapcat #(if (nil? (first %))
           [(count %)]
           %))))


(defn render-data-row [env {:keys [name where same data diff] :as comparison}]
  "Render data comparison for the given collection"
  [:li
   (hide-if (hide-data-initially? comparison) {})
   (render-same same) " "
   name
   [:div
    (render-data-content "Prod." (:production data))
    (render-data-content "Stage" (:staging data))
    (render-data-content "Diff." (render-data-diff diff))]])

(defn render-data [env diffs]
  [:div
   [:p [:em "(Showing only present in both but different by default.)"]]
   [:ol.data-details
    (map (partial render-data-row env) diffs)]])

(defn count-at [env diffs]
  "Count collection in the given environment"
  (count (filter (comp env :where) diffs)))

(defn count-datadiff [diffs]
  (count (filter (comp false? :same) diffs)))

(defn mark-diffs-to-hide [diffs]
  "Which collections (diffs) are less interesting and should be originally hidden?
  Currently we hide all collections that are not daily summaries, i.e. do not contain .day[.group]"
  (map
   #(assoc % :hide-initially? (not (re-matches #".*\.day(\.\w+)?" (:name %))))
   diffs))

(defn page
  ([] (page fake-data))
  ([diffs]
     (let [actual-diffs (if (and (empty? diffs) default-to-fake) fake-data diffs)
           diffs
           (with-meta
             (mark-diffs-to-hide actual-diffs)
             (meta actual-diffs))]
       (html5
       [:head
        [:title "Data Diff Stage/Prod"]
        (include-js  "/static/js/zepto/zepto.min.js")
        ;; zepto-boostrap compatibility
        (include-js  "/static/js/zepto/data.js")
        (include-js  "/static/js/zepto/selector.js")
        [:script {:type "text/javascript"}
         "Zepto.browser = {webkit: true};window.jQuery = Zepto;"]
        (include-bootstrap)]
       [:body
        (fixed-layout
         [:h1 "Daily data comparison of staging and production"]

         [:p#loadingDysplay]
         [:div#controls.btn-group {:data-toggle "buttons-checkbox"}
          [:button {:type "button",
                    :class "btn",
                    :data-toggle "collapse",
                    :data-target ".collection-overview .initially-hidden",
                    :title "By default we show only *.day* collections; click to show all"}
           "Show all collections"]
          [:button {:type "button",
                    :class "btn"
                    :data-toggle "collapse"
                    :data-target ".data-details .initially-hidden",
                    :title "Be default we show only the collections that differ in prod and staging; click to show those that are same as well"}
           "Show all data"]]
         [:form {:action "/reload", :method "get", :style "display:inline"}
          [:a
           {:href "#",
            :onclick "alert('Going to reload data; this will take some time, do not worry. Click OK to start.');$('#loadingDysplay').prepend('<p>Loading the latest data...</p>').css({ backgroundColor: 'red', fontSize: 28, height: '3em' });$(this).closest('form').submit();",
            :class "btn btn-danger"
            :style "margin-left:1em"}
           "Reload with latest data for day:" ]
          [:input {:type "number", :name "daydiff",
                   :max "0", :value "-1"
                   :title "The day to render data for, counted from today (= 0",
                   :style "width:2em; margin-left:5px;",
                   :required "true"}]
          " (2-10 min)"]
         [:span#comparedDate {:style "float:right"}
          (let [meta (meta diffs)
                compared (some->
                          (:compared meta)
                          (.toLocalTime))]
            (str
             "Data epoch day: "
             (:epoch-day meta)
             " (" (:epoch-date meta)
             "), compared " compared))]

         [:p "Jump to: "
          [:a {:href "#p-coll-overview"} "Collection list"]
          " | "
          [:a {:href "#p-data-details"} "Details of data differences"]]

         (if (empty? diffs)
           [:p.text-warning "There are no data to display. Click on the 'Reload' link above. Beware: it will take few minutes."]
           [:div.row
            [:div.span5

             [:h2 "Staging"]
             [:h3#p-data-details "Data details (diff: " (count-datadiff diffs) ")"]
             (render-data :staging diffs)

             [:h3#p-coll-overview "Collection overview (" (count-at :staging diffs) ")"]
             (render-colls :staging diffs)
             ]
            [:div.span1 " "] ;; separator space
            [:div.span5 {:style "border-left:1px solid grey"}
             [:h2 "Production"]

             [:h3 "Data details (diff: " (count-datadiff diffs) ")"]
             (render-data :production diffs)

             [:h3 "Collection overview (" (count-at :production diffs) ")"]
             (render-colls :production diffs)
             ]
           ]))]))))
