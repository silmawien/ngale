(ns ngale.util)

(defn add-watch-once
  [target key fn]
  (remove-watch target key)
  (add-watch target key fn))

