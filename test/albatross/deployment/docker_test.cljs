(ns albatross.deployment.docker-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [albatross.core :as c]))

(def config-docker
  {:deployment [{:albatross/name "docker 1"
                 :albatross/type :docker
                 :url "foo/"
                 :user "a"
                 :pass "b"
                 :images
                 [{:workspace "."
                   :name "my/img"
                   :tags ["foo" "bar" "baz"]}]}]})

(deftest docker
  (let [instruction-list (c/generate-instructions config-docker)]
    (is
     (vector? instruction-list))
    (is
     (every? string? instruction-list)
     (apply str "Some instructions are not strings:\n"
            (interpose "\n" (map (fn [[idx val]] (str "(index: " idx "): " val)) (remove (comp string? second) (map-indexed vector instruction-list))))))))
