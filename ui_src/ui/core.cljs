(ns ui.core
  (:import goog.string)
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string :refer [split-lines]]
            [cljs-uuid-utils.core :as uuid])
  )

(def join-lines (partial string/join "\n"))

(enable-console-print!)

(defonce state (atom 0))
(defonce shell-result (atom ""))
(defonce command (atom ""))

(defonce proc (js/require "child_process"))

(defn append-to-out [out]
  (swap! shell-result str out))

(def files (reagent/atom {}))
(def monitor (reagent/atom ""))

(defn run-process []
  (when-not (empty? @command)
    (println "Running command" @command)
    (let [[cmd & args] (string/split @command #"\s")
          js-args (clj->js (or args []))
          p (.spawn proc cmd js-args)]
      (.on p "error" (comp append-to-out
                           #(str % "\n")))
      (.on (.-stderr p) "data" append-to-out)
      (.on (.-stdout p) "data" append-to-out))
    (reset! command "")))


(defn dateString [date]
           (str (goog.string/format "%02s" (+ 1 (.getMonth date))) "/" (.getDate date) "/" (.getFullYear date) " "
                (.getHours date) ":" (.getMinutes date) ":" (.getSeconds date)))

(defn filtered-state [st]
  (filter
    #(goog.string/contains
       (string/upper-case (:file-name %))
       (string/upper-case st))
    (vals @files)))

(defn filtered-table [st]
  (if (< 0 (count @files))
    [:table.table
     [:tr
      [:th.tableHeader  "File Name"]
      [:th.tableHeader "Last Modified"]
      [:th.tableHeader "UUID"]
      [:th ""]]
     (for [{:keys [file-name last-modified uid]} (filtered-state st)]
       ^{:key uid} [:tr {:key uid}
        [:td.dataCell file-name]
        [:td.dataCell (dateString (js/Date. last-modified))]
        [:td.dataCell (str uid)]
        [:td [:button {:on-click #(swap! files dissoc uid)} "DELETE"]]])
     ]))

(defn file-table []
  (let [filter-pred (reagent/atom "")]
    (fn []
      [:div
       [:div "Filter: " [:input {:type "text"
                                 :on-change
                                       (fn [e] (reset! filter-pred (.-value (.-target e))))}]]
      [filtered-table @filter-pred]])))


(defn file-picker []
  [:input {:type "file" :multiple true
           :on-change (fn [e]
                        (let [file-list (.-files (.-currentTarget e))
                              list-len (.-length file-list)]
                          (doseq [i (range list-len)]
                            (let [file (.item file-list i)
                                  uid (uuid/make-random-uuid)]
                              (swap! files assoc uid {:file-name (.-name file)
                                                      :last-modified (.-lastModified file)
                                                      :uid uid})

                              )
                            ) (js/alert @files)))}])

(defn root-component []
  [:div
   [file-picker]
   [file-table]
   [:button
    {:on-click #(swap! state inc)}
    (str "Clicked " @state " times")]
   [:p
    [:form
     {:on-submit (fn [^js/Event e]
                   (.preventDefault e)
                   (run-process))}
     [:input#command
      {:type        :text
       :on-change   (fn [^js/Event e]
                      (reset! command
                              ^js/String (.-value (.-target e))))
       :value       @command
       :placeholder "type in shell command"}]]]
   [:pre (join-lines (take 100 (reverse (split-lines @shell-result))))]])

(reagent/render
  [root-component]
  (js/document.getElementById "app-container"))
