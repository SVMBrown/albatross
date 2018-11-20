(ns albatross.weaver
  (:require [clojure.string :as string]
            [cljs.reader :as edn]
            [weaver.core :as w]
            [albatross.script-utils :as utils]
            [weaver.interop]
            ["fs" :as fs]
            ["shelljs" :as sh]))

(set! weaver.interop/*exit-fn* (fn [] (throw (js/Error. "Unknown weaver error!"))))



(defn backup-template-destinations [templates]
  (let [timestamp (js/Date.now)
        base-path (str "weaver_backups/backup_" timestamp "/")]
    (doseq [{:keys [to] :as template} templates]
      (if-let [body (try
                      (fs/readFileSync to)
                      (catch js/Error e
                        (println "Encountered error reading 'to' path for template:\n" template "\nError:\n" e "\nSkipping...\n\n\n")
                        nil))]
        (utils/write-file (string/replace to #"^(\./)?" base-path) body)))))

(defn expand-dirs [{:keys [from to] :as tmp}]
  (let [to (or to
               (let [[match dir file :as from-all] (re-matches #"(.*/)([^/]*)$" from)]
                 dir)
               "./")]
    (cond
      (re-matches #".*\.edn$" from)
      (let [[_ _ from-file]    (re-matches #"(.*/)?([^/]*)$" from)
            [_ to-dir to-file] (re-matches #"(.*/)([^/]*)$" to)
            final-to           (str to-dir (string/replace
                                            (or (not-empty to-file) from-file)
                                            #"\.edn$" ".json"))]
        [(assoc tmp :to final-to)])

      (re-matches #".*/$" from)
      (vec
       (mapcat
        (fn [file-or-dir]
          (if (or (re-matches #".*\.edn$" file-or-dir) (re-matches #".*/" file-or-dir))
            (expand-dirs (assoc tmp
                                :from (str from file-or-dir)
                                :to (str to file-or-dir)))
            []))
        (sh/ls from)))

      :else (throw (js/Error. (str "Invalid template " tmp "\n :from must be a file with .edn extension or a directory."))))))


;;NOTE: Context key is processed already by weaver processing of full albatross config
;;NOTE: There is no easy way around this as it stands but if necessary this can be solved if worth the effort
(defn weaver-hook [{:keys [templates
                           context]}]
  (let [process-template   (partial w/process-template context)
        file-templates     (vec (mapcat expand-dirs templates))]
    {:hook-fn
     (fn []
       (backup-template-destinations file-templates)
       (doseq [{:keys [from to] :as template} file-templates]
         (->> from
              utils/read-edn-file
              process-template
              clj->js
              utils/pretty-json
              (utils/write-file to))))
     :description (str "Weaver Hook:\n"
                       "Will process edn templates and generate json configs from them.\n"
                       "Existing files are backed up in weaver_configs/\n"
                       "NOTE: Files are searched for when the hook is GENERATED, not when it is run\n"
                       "Files Affected:\n"
                       (string/join "\n"
                                    (map
                                     (fn [{:keys [from to]}]
                                       (str from " --> " to))
                                     file-templates))
                       "\n")
     :templates file-templates}))
