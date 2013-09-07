(ns ngale.cmdline
  (:require [ngale.app :as app]
            [clojure.java.io :as jio]
            [clojure.zip :as zip]))

;;; abstract cmdline list interface

;; refers to the result set atom that #'more will act on
(def result-set (atom {:pos 0 :values [] :fmt nil}))

(def page-size 10)

(defn- slice-with-index
  [v start end]
  (map vector (range start end)
       (subvec v start (min (count v) end))))

(defn- print-page
  [{:keys [pos values fmt]}]
  (doseq [[i row] (slice-with-index values pos (+ pos page-size))]
    (println i ":" (fmt row)))
  (count values))

(defn- next-page [n v] (min (+ n page-size) (count v)))

(defn- pgdn
  "Page down in a result set, printing the new page."
  [{:keys [pos values fmt] :as state}]
  (let [newpos (next-page pos values)
        result (assoc state :pos newpos)]
    result))

(defn more
  "Display more of the active result set."
  []
  (print-page (swap! result-set pgdn)))

(defn- format-track
  [{:keys [path title artist album]}]
  (if-not (nil? title)
    (str title " / " artist " / " album)
    (.getName (jio/file path))))

;;; concrete list commands

;; search
(def search-results (atom []))

(defn query
  "Search songs."
  [q]
  (swap! search-results (fn [x] (vec (app/query q))))
  (print-page
    (swap! result-set
           (fn [s]
             {:pos 0 :values @search-results :fmt format-track}))))

(defn enqueue
  "Enqueues from search-results."
  [& specs]
  (let [indices (mapcat #(if (vector? %) (apply range %) [%]) specs)
        hits @search-results]
    (doseq [i indices]
      (app/enqueue (:path (hits i))))))

;; player state
(defn ps
  "Display player state."
  []
  (print-page
    (let [{:keys [tracks pos playing]} (app/playlist)]
      (swap! result-set
             (fn [s]
               {:pos pos :values (vec tracks) :fmt format-track})))))

