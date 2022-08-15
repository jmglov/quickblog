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

(defn- write-test-file [dir filename content]
  (fs/create-dirs dir)
  (let [f (fs/file dir filename)]
    (spit f content)
    f))

(defn- write-test-post [posts-dir]
  (write-test-file
   posts-dir "test.md"
   "Title: Test post\nDate: 2022-01-02\nTags: clojure\n\nWrite a blog post here!"))


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
    (let [posts-edn (write-test-file posts-dir "posts.edn"
                                     {:file "test.md"
                                      :title "Test post"
                                      :date "2022-01-02"
                                      :tags #{"clojure"}})
          post-file (write-test-file posts-dir "test.md"
                                     "Write a blog post here!")]
      (api/migrate {:posts-dir posts-dir
                    :posts-file posts-edn})
      (is (= "Title: Test post\nDate: 2022-01-02\nTags: clojure\n\nWrite a blog post here!"
             (slurp post-file))))))

(deftest render
  (deftest happy-path
    (with-dirs [assets-dir
                posts-dir
                templates-dir
                cache-dir
                out-dir]
      (write-test-post posts-dir)
      (write-test-file assets-dir "asset.txt" "something")
      (api/render {:assets-dir assets-dir
                   :posts-dir posts-dir
                   :templates-dir templates-dir
                   :cache-dir cache-dir
                   :out-dir out-dir})
      (is (fs/exists? (fs/file out-dir "assets" "asset.txt")))
      (doseq [filename ["base.html" "post.html" "style.css"]]
        (is (fs/exists? (fs/file templates-dir filename))))
      (is (fs/exists? (fs/file cache-dir "test.md.pre-template.html")))
      (doseq [filename ["test.html" "index.html" "archive.html" "tags.html"
                        (fs/file "tags" "clojure.html")
                        "atom.xml" "planetclojure.xml"]]
        (is (fs/exists? (fs/file out-dir (fs/file out-dir filename))))))))
