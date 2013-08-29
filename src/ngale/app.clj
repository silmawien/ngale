(ns ngale.app
  (:use compojure.core)
  (:require [ngale.player :as player]
            [clojure.java.io :as jio]
            [ngale.songs :as songs]))

;;; app - where state roams free!

;; root directory for songs
;(def root (jio/file "./content/mp3"))
(def root (jio/file "./content/mp3-5pc"))
;(def root (jio/file "/home/mattias/mp3"))

;; list of all songs
(defonce songs (agent {}))

;; track list + more
(defonce playstate (agent player/default-state))

(defn add-watch-once
  [key fn]
  (remove-watch playstate key)
  (add-watch playstate key fn))

;; trigger alsa on updates
(add-watch-once :alsa #'player/on-update)

(defn add-meta
  "Merge metadata. Indexing coll by k is expected
  to yield a path, which is used to look up the song."
  [coll k]
  (let [ss @songs]
    (map #(merge %1 (get ss (k %1))) coll)))

;;; top-level interface
(defn playlist [] (update-in @playstate [:tracks] add-meta :path))
(defn pause [] (player/pause playstate))
(defn resume [] (player/resume playstate))
(defn next-track [] (player/next-track playstate))
(defn previous-track [] (player/previous-track playstate))
(defn clear [] (player/clear playstate))
(defn enqueue [path] (player/enqueue playstate path))
(defn query [q] (songs/query @songs q))
