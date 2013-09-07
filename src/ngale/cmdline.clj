(ns ngale.cmdline
  (:require [ngale.app :as app]
            [clojure.java.io :as jio]
            [clojure.zip :as zip]))

;;; stateful cmdline interface

;; latest search results as a vector, with cursor position
(def search-state (atom [0 []]))

(def page-size 10)

(defn slice-with-index
  [v start end]
  (map vector (range start end)
       (subvec v start (min (count v) end))))

(defn format-result
  [index result]
  (let [path (first result)
        fname (.getName (jio/file path))
        {:keys [title album artist]} (second result)]
    (if-not (nil? title)
      (str index ": " title " / " artist " / " album)
      (str index ": " fname))))

(defn print-results
  []
  (let [[pos results] @search-state
        end (+ pos page-size)]
    (doseq [[n result] (slice-with-index results pos end)]
      (println (format-result n result)))
    (count results)))

(defn query
  [q]
  (swap! search-state
         (fn [s]
            [0 (vec (app/query q))]))
  (print-results))

(defn next-page [n v] (min (+ n page-size) (count v)))

(defn more
  []
  (swap! search-state
         (fn [s]
           (let [v (second s)
                 n (next-page (first s) v)]
             [n v])))
  (print-results))

(defn enqueue
  [& abbr]
  (let [specs (map #(if (vector? %) % [% (inc %)]) abbr)
        indices (mapcat (partial apply range) specs)
        state @search-state]
    (doseq [i indices]
      (app/enqueue (first ((second state) i))))))
