(ns ngale.player
  (:require [clojure.string :refer [split]]
            [clojure.java.io :as jio]
            [clojure.java.shell :refer [sh]]))

;; start playback on enqueue if the playlist was empty
(def auto-play true)
(def auto-repeat false)

;; play list state
(def default-state {
     :tracks [],
     :pos 0,
     :playing? false
     :idseq 0})

(defmacro def-action
  "Defines a function that alters player state. 
  The result should be a map. It will be merged with the current state."
  [name args val]
  `(defn ~name [player# ~@(rest args)]
    (send player#
          (fn [state#]
            (let [~(first args) state#]
              (merge state# ~val))))))

(def-action error
  [state]
  {:playing? false})

(defn clip-pos [pos n] (mod pos (inc n)))
(defn playable-pos [pos n] (< pos n))

(defn alter-pos
  [{:keys [tracks pos playing?]} f]
  (let [n (count tracks)
        new-pos (clip-pos (f pos) n)]
    {:pos new-pos
     :playing? (and playing? (playable-pos new-pos n))}))

(def-action goto [s target] (alter-pos s (constantly target)))
(def-action next-track [s] (alter-pos s inc))
(def-action previous-track [s] (alter-pos s dec))
(def-action completed
  [{:keys [tracks pos] :as s}]
  (let [n (count tracks)]
    (alter-pos s (if (and auto-repeat (= (inc pos) n))
                   (constantly 0)
                   inc))))

(def-action enqueue
  [{:keys [tracks pos playing? idseq]} path]
  {:tracks (conj tracks (hash-map :path path :id idseq))
   :playing? (if (empty? tracks) auto-play playing?)
   :idseq (inc idseq)})

(def-action pause
  [p]
  {:playing? false})

(def-action resume
  [{:keys [pos tracks]}]
  {:playing? (playable-pos pos (count tracks))})

(defn clear
  [player]
  (send player (fn [state] default-state)))

(defn restart
  [player]
  (restart-agent player default-state))

;;; interface to alsa (running in a separate process)

;; used to determine if an alsa command was interrupted by alsa-kill,
;; or died spontaneously
(defonce alsa-kill-counter (atom 0))

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
  "Run alsaplayer with arguments. Returns :killed if stopped by
  alsa-kill, :done if finished with a 0 exit status, and :error otherwise."
  [& args]
  (let [limit @alsa-kill-counter
        result (apply sh (concat ["alsaplayer" "-i" "text"] (map #'str args)))]
    ;(println (str args) (:exit result))
    (cond
      (> @alsa-kill-counter limit) :killed
      (= 0 (:exit result)) :done
      :else :error)))

(defn alsa-pause [] (alsa "--speed" "0"))
(defn alsa-resume [] (alsa "--speed" "1.0"))
(defn alsa-seek [sec] (alsa "--seek" sec))

(defn alsa-kill
  "Kill any ongoing alsa-play command in such a way that it returns :killed."
  []
  (swap! alsa-kill-counter #(inc %1))
  (sh "killall" "alsaplayer"))

(defn alsa-error
  [reference path msg]
  (prn msg path)
  (error reference))

(defn alsa-play
  "Start alsaplayer for a given track. The built in playlist features are not
  used. Instead we play a single song, and if it finishes without interruption,
  we signal this by calling (completed).

  TODO: (completed) events can be handled out-of-order. This is by design, but
  it might be nice to discard them if the current track has changed.

  TODO: there is a small delay between returning and when alsa-player starts
  responding to commands. This can lead to races."
  [reference path]
  (future
    ;(println "playing" path)
    (if (and path (.exists (jio/file path)))
      (let [result (alsa path)]
        ;(println "completed" result)
        (case result
          :done (completed reference)
          :killed nil
          :error (alsa-error reference path "playback error")))
      (alsa-error reference path "not found"))))

(defn current-track
  [p]
  (if-not (>= (:pos p) (count (:tracks p)))
    (nth (:tracks p) (:pos p))))

;;; watch and control alsaplayer based on changes to @player

(defn on-update
  "Diff the two states and bring the player up to date."
  [key reference old new]
  (if (= (current-track old) (current-track new))
    ; same track
    (if-not (= (:playing? old) (:playing? new))
      (if-not (:playing? new)
        (alsa-pause)
        (when-not (= :done (alsa-resume))
          (alsa-kill)
          (alsa-play reference (:path (current-track new))))))
    ; different track
    (do
      (alsa-kill)
      (if (:playing? new)
        (alsa-play reference (:path (current-track new)))))))

