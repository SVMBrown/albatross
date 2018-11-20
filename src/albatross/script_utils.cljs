(ns albatross.script-utils
  (:require
   [cljs.reader :as edn]
   ["fs" :as fs]))

(defn read-edn-file [file-name]
  (-> file-name (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(defn pretty-json [js-obj]
  (.stringify js/JSON js-obj nil 2))

(defn write-file [path body]
  (let [[_ dir file] (re-matches #"(.*/)?([^/]*)$" path)]
    (fs/mkdirSync dir #js {:recursive true})
    (fs/writeFileSync path body)))

(defn process-cli-args
  "Processes command line args from a {:flag-key [\"flag\" \"path\"]} map."
  [cli-flags cli-args]
  (reduce
   (fn [acc [flag val]]
     (if-let [path (get cli-flags flag)]
       (assoc-in acc path val)
       acc))
   {}
   (partition 2 cli-args)))
