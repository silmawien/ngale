(ns ngale.songs
  (:import [org.jaudiotagger.audio AudioFileIO])
  (:import [org.jaudiotagger.audio.exceptions CannotReadException])
  (:import [org.jaudiotagger.tag FieldKey])
  (:import [java.util.logging Logger Level])
  (:require [clojure.string :as str]))

(def extensions #{".mpeg" ".mp3" ".mp4" ".flac" ".ogg" ".aiff" ".wma"})

(defn is-audio? [file]
  (let [name (.getName file)]
    (some #(.endsWith name %) extensions)))

(defn list-audio-files [root]
  (filter #'is-audio? (file-seq root)))

(defmacro fields [cls & names]
  "A terse way to construct a list of references to java fields."
  (let [fs (map (fn [name] `(. ~cls ~name)) names)]
    `(list ~@fs)))

(def supported-tags (fields FieldKey TITLE ALBUM ARTIST COMMENT GENRE YEAR))

;; permanently disable INFO logs
(.. Logger (getLogger "org.jaudiotagger") (setLevel Level/SEVERE))

(defn tags [file]
  (try
    (if-let [tag (.. (AudioFileIO.) (readFile file) getTag)]
      (into {}
            (filter (fn [[key val]] (not (empty? val)))
                    (map (fn [key] [(-> key str str/lower-case keyword)
                                    (.getFirst tag key)])
                          supported-tags)))
      {})
    (catch CannotReadException e {})))

(defn index-string
  [path tags]
  (let [{:keys [title album artist]} tags]
    (str/lower-case (str/join " " [path title album artist]))))

(defn add-index
  [path tags]
  (assoc tags :idx (index-string path tags)))

(defn make-song
  "Make a song entry from a file by parsing tags. If the file already exists
  in cur, tags are copied instead."
  [cur file]
  (let [path (.getPath file)]
    [path (or (cur path)
              (add-index path (tags file)))]))

(defn load-songs [cur root]
  (println "loading ...")
  (into {} (map (partial make-song cur) (list-audio-files root))))

(defn update-songs [songs root]
  (send songs (fn [cur] (load-songs cur root))))

(defn matches
  [words song]
  (let [idx (:idx (val song))]
    (every? (fn [word] (>= (.indexOf ^String idx ^String word) 0)) words)))

(defn query
  "Return the subset of songs that matches the query.
  The query consists of one or more space-delimited words. A song matches if all
  words are found in the song's index string, which is a combination of its file
  path and meta data. Matching is case-insensitive."
  [songs q]
  (let [qwords (str/split (str/lower-case q) #" +")]
    (map (fn [[path info]] (dissoc (assoc info :path path) :idx))
         (filter #(matches qwords %) songs))))

