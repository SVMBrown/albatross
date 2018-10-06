(ns ^:figwheel-hooks albatross.core
  (:require
   [clojure.string :as string]
   ["fs" :as fs]))

(println "This text is printed from src/albatross/core.cljs. Go ahead and edit it and see reloading in action.")

(defn multiply [a b] (* a b))

(defonce child-process (js/require "child_process"))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))
(def error-log js/console.error)
(def warn-log js/console.warn)
(def info-log js/console.info)
(def debug-log js/console.debug)

(defn- convert-and-error-log [arg]
  (js/console.error
   (if (or (string? arg) (= (type arg) js/Error))
     arg
     (clj->js arg))))

(defn warn-and-exit [& msgs]
  (doseq [msg msgs]
    (convert-and-error-log msg))
  (js/process.exit 1))


(defn get-env
  ([key]
   (get-env (name key) nil))
  ([key not-found]
   (if-some [result (aget (.-env js/process) (name key))]
     result
     not-found)))

(defn exec-safe
  ([command] (exec-safe
              command
              {:timeout 180000
               :encoding "UTF-8"}))
  ([command opts]
   (try
     (.execSync child-process
                command
                (->> opts
                     (merge
                      {:timeout 180000
                       :encoding "UTF-8"})
                     (clj->js)))
     (catch js/Error e
       (.error js/console e)
       (throw e)))))

(defn docker-deploy
  [{:keys [tag workspace user pass url]}]
  (cond

    (nil? tag)
    (do (.error js/console "cannot use docker image without provided tag")
        (.exit js/process 1))

    (nil? workspace)
    (do (.error js/console "cannot use docker image without workspace")
        (.exit js/process 1))

    (nil? user)
    (do (.error js/console "cannot use docker image without provided user")
        (.exit js/process 1))

    (nil? pass)
    (do (.error js/console "cannot use docker image without provided pass")
        (.exit js/process 1))

    (nil? url)
    (do (.error js/console "cannot use docker image without provided url")
        (.exit js/process 1))

    :else
    (do
      (exec-safe (str "docker build -t " tag " " workspace))
      (exec-safe (str "docker login -u " user " -p " pass " " url))
      (exec-safe (str "docker push " tag)))))

(defn kubectl []
  (println "I don't work yet!"))


;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  (.log js/console "Reloaded!")
)
