(ns albatross.weaver
  (:require [clojure.string :as string]
            [weaver.core :as w]
            [weaver.interop]))

(set! weaver.interop/*exit-fn* (fn [] (throw (js/Error. "Unknown weaver error!"))))
