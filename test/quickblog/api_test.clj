(ns quickblog.api-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [babashka.fs :as fs]
   [quickblog.api :as api])
  (:import (java.util UUID)))

(def test-dir ".test")

(use-fixtures :each (fn [test-fn] (test-fn) (fs/delete-tree test-dir)))

(defn- tmp-dir [dir-name]
  (fs/file test-dir
           (format "quickblog-test-%s-%s" dir-name (str (UUID/randomUUID)))))

(defmacro with-dirs
  "dirs is a seq of directory names; e.g. [cache-dir out-dir]"
  [dirs & body]
  (let [binding-form# (mapcat (fn [dir] [dir `(tmp-dir ~(str dir))]) dirs)]
    `(let [~@binding-form#]
       ~@body)))

(deftest new-test
  (with-dirs [posts-dir]
    (with-redefs [api/now (constantly "2022-01-02")]
      (api/new {:posts-dir posts-dir
                :file "test.md"
                :title "Test post"})
      (let [post-file (fs/file posts-dir "test.md")]
        (is (fs/exists? post-file))
        (is (= "Title: Test post\nDate: 2022-01-02\nTags: clojure\n\nWrite a blog post here!"
               (slurp post-file)))))))

(deftest migrate
  (with-dirs [posts-dir]
    (let [posts-edn (fs/file posts-dir "posts.edn")
          post-file (fs/file posts-dir "test.md")]
      (fs/create-dirs posts-dir)
      (spit posts-edn {:file "test.md"
                       :title "Test post"
                       :date "2022-01-02"
                       :tags #{"clojure"}})
      (spit post-file "Write a blog post here!")
      (api/migrate {:posts-dir posts-dir
                    :posts-file posts-edn})
      (is (= "Title: Test post\nDate: 2022-01-02\nTags: clojure\n\nWrite a blog post here!"
             (slurp post-file))))))