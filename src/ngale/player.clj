(ns ngale.player
  (:require [clojure.string :refer [split]])
  (:require [clojure.java.shell :refer [sh]]))

; start playback on enqueue if the playlist was empty
(def autoplay true)

; play list state
(def default-state {
     :tracks [],
     :pos -1,
     :playing? false
     :idseq 0})

(defonce player
  (agent default-state))

(defn update-state
  [p key val & kvs]
  (let [res (assoc p key (if (clojure.test/function? val)
                          (val (key p))
                          val))]
    (if (and kvs (next kvs))
      (recur res (first kvs) (second kvs) (nnext kvs))
      res)))

(defmacro def-player-action
  "An action is an externally visible function whose body runs in the player
  agent's context. The current state is supplied as an extra (mandatory) first
  argument. Use a binding form to pull out those parts you need.

  The remaining params will receive values from the external caller. Vals is a
  series of key, value pairs that are used to update selected parts of the
  state. The key selects what to update, and val is either the new value
  directly, or a function of the old value."
  [name [state-binding & args] & vals]
  (let [g (gensym)]
    `(defn ~name [~@args]
       (send player (fn [~g]
         (let [~state-binding ~g]
           (update-state ~g ~@vals)))))))

(def-player-action next-track
  [{:keys [tracks pos]}]
    :pos (min (dec (count tracks)) (inc pos)))

(def-player-action previous-track
  [{:keys [pos]}]
  :pos (max 0 (dec pos)))

(def-player-action enqueue
  [{:keys [tracks pos playing? idseq]} track]
  :pos (if (empty? tracks) 0 pos)
  :tracks (conj tracks (hash-map :path track :id idseq))
  :playing? (if (empty? tracks) autoplay playing?)
  :idseq (inc idseq))

(def-player-action pause
  [p]
  :playing? false)

(def-player-action resume
  [p]
  :playing? true)

(def-player-action clear
  [p]
  :tracks []
  :pos -1
  :playing? false
  :idseq 0)

;; interface to alsa (running in a separate process)

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

(defn alsa-pause
  []
  (let [result (sh "alsaplayer" "--speed" "0")]
    (= 0 (:exit result))))

(defn alsa-resume
  []
  (let [result (sh "alsaplayer" "--speed" "1.0")]
    (= 0 (:exit result))))

(defn alsa-seek
  [sec]
  (let [result (sh "alsaplayer" "--seek" (str sec))]
    (= 0 (:exit result))))

(defn alsa-kill
  "Kill alsaplayer in a way such that it returns a non-zero exit status.
  This way alsa-play can distinguish being killed from song finishing. Kind of
  crude."
  []
  (sh "killall" "alsaplayer"))

(defn alsa-play
  "Start alsaplayer for a given track. The built in playlist features are not
  used. Instead we play a single song. If playback finishes without anyone
  interrupting us, we signal this via an implicit (next).
  
  TODO: There is a chance that this (next) will be handled out-of-order with
  other commands, but I can't think of a case where it matters."
  [track]
  ;(println "playing" track)
  (future
    (let [result (sh "alsaplayer" (:path track))]
      (if (= 0 (:exit result))
        (next-track)))))

(defn current-track
  [p]
  (if (= -1 (:pos p))
    nil
    (nth (:tracks p) (:pos p))))

(defn on-update
  "Perform whatever necessary action to bring the player up to date.
    - Pause / Resume.
    - Change tracks if track at pos has changed.
    - If the queue goes from empty to non-empty, start playing automatically.
    - etc

  This function runs in send context of the agent.

  TODO: distinguish between multiple tracks with the same path
  "
  [key ref old new]
    (if (= (current-track old) (current-track new))
      ; handle same track
      (if (not (:playing? new))
        (alsa-pause)
        (or (alsa-resume) (alsa-play (current-track new))))
      ; handle different track
      (do
        (alsa-kill)
        (if (:playing? new)
          (alsa-play (current-track new))))))
      
(remove-watch player :on-update)
(add-watch player :on-update #'on-update)

