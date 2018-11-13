(ns albatross.core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [albatross.core :as c]))

(def config1 {:name "config 1"
              :docker
              [{:url "foo/"
                :user "a"
                :pass "b"
                :images
                [{:workspace "."
                  :name "my/img"
                  :tags ["foo" "bar" "baz"]}]}]})

(def config2 {:name "config 2"
              :docker
              {:url "foo/"
               :user "a"
               :pass "b"
               :images
               [{:workspace "."
                 :name "my/img"
                 :tags ["foo" "bar" "baz"]}]}})

#_(deftest docker
  (doseq [config [config1 config2]]
    (println (:name config))
    (is (true?
         (#(and (vector? %) (every? string? %))
          (c/docker-instructions config)))
       (:name config))))
