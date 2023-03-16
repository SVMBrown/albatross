(ns albatross.script-utils
  (:require
   [cljs.reader :as edn]
   ["shelljs" :as sh]
   ["fs" :as fs]))

(defmulti write-file-with-format (fn [fmt & _]
                                   fmt))

(defn read-edn-file [file-name]
  (-> file-name (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(defn pretty-json [js-obj]
  (.stringify js/JSON js-obj nil 2))

(defn write-file [path body]
  (let [[_ dir file] (re-matches #"(.*/)?([^/]*)$" path)]
    (sh/mkdir "-p" dir)
    (fs/writeFileSync path body)))

(defmethod write-file-with-format :json [_ path body]
  (->> body
       clj->js
       pretty-json
       (write-file path)))

(defmethod write-file-with-format :edn [_ path body]
  (binding [*print-fn-bodies* true
            *print-length* nil
            *print-dup* nil
            *print-level* nil
            *print-readably* true]
    (->> body
         (pr-str)
         (write-file path))))

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
