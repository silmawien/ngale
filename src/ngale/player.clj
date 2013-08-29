(ns ngale.player
  (:require [clojure.string :refer [split]]
            [clojure.java.io :as jio]
            [clojure.java.shell :refer [sh]]
            [ngale.util :refer [add-watch-once]]))

;; start playback on enqueue if the playlist was empty
(def auto-play true)
(def auto-repeat false)

;; play list state
(def default-state {
     :tracks [],
     :pos -1,
     :playing? false
     :idseq 0})

(defonce player
  (agent default-state))

(defmacro def-action
  "Defines a function that alters player state. The first param list is the
  current state. The second param list are the actual function args.

  The value is a map that will be merged with the current state."
  [name [state-binding] args val]
  `(defn ~name ~args
    (send player
          (fn [state#]
            (let [~state-binding state#]
              (merge state# ~val))))))

(def-action completed
  [{:keys [tracks pos]}] []
    {:pos (mod (inc pos) (count tracks))
     :playing? (or (not= (inc pos) (count tracks)) auto-repeat)})

(def-action next-track
  [{:keys [tracks pos]}] []
    {:pos (min (dec (count tracks)) (inc pos))})

(def-action previous-track
  [{:keys [pos]}] []
  {:pos (if (= -1 pos) -1 (max 0 (dec pos)))})

(def-action enqueue
  [{:keys [tracks pos playing? idseq]}] [track]
  {:pos (if (empty? tracks) 0 pos)
   :tracks (conj tracks (hash-map :path track :id idseq))
   :playing? (if (empty? tracks) auto-play playing?)
   :idseq (inc idseq)})

(def-action pause
  [p] []
  {:playing? false})

(def-action resume
  [p] []
  {:playing? true})

(defn clear
  []
  (send player (fn [state] default-state)))

(defn restart
  []
  (restart-agent player default-state))

;;; interface to alsa (running in a separate process)

(defn- keyword-pairs
  [output]
    (let [lines (filter #(re-find #": " %) (split output #"\n"))]
      (map #(split % #": ") lines)))

(defn alsa-status
  "Retrieve status of running player as a map with :path, :volume, :title, etc
  etc or nil if no player is running."
  []
  (let [result (sh "alsaplayer" "--status")]
    (if (= 0 (:exit result))
      (into {} (for [[k v] (keyword-pairs (:out result))]
                    [(keyword k) v])))))

(defn alsa
  "Run alsaplayer with arguments."
  [& args]
  (let [result (apply sh (concat ["alsaplayer" "-i" "text"] (map #'str args)))]
    (= 0 (:exit result))))

(defn alsa-pause [] (alsa "--speed" "0"))
(defn alsa-resume [] (alsa "--speed" "1.0"))
(defn alsa-seek [sec] (alsa "--seek" sec))

(defn alsa-kill
  "Kill alsaplayer in a way such that it returns a non-zero exit status.
  This way alsa-play can distinguish being killed from song finishing. Kind of
  crude."
  []
  (sh "killall" "alsaplayer"))

(defn alsa-play
  "Start alsaplayer for a given track. The built in playlist features are not
  used. Instead we play a single song, and if it finishes without interruption,
  we signal this by calling (completed).

  TODO: (completed) events can be handled out-of-order. This is by design, but
  it might be nice to discard them if the current track has changed."
  [path]
  ;(println "playing" track)
  (future
    ;(println "playing" path)
    (when (and (.exists (jio/file path)) (alsa path))
      ;(println "done" path)
      (completed))))

(defn current-track
  [p]
  (if-not (= -1 (:pos p))
    (nth (:tracks p) (:pos p))))

;;; watch and control alsaplayer based on changes to @player

(defn on-update
  "Diff the two states and bring the player up to date."
  [key ref old new]
  (if (= (current-track old) (current-track new))
    ; same track
    (if-not (:playing? new)
      (alsa-pause)
      (or (alsa-resume) (alsa-play (:path (current-track new)))))
    ; different track
    (do
      (alsa-kill)
      (if (:playing? new)
        (alsa-play (:path (current-track new)))))))

(add-watch-once player :player #'on-update)

