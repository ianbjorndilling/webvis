(ns webvis.html-test
  (:require [clojure.test :refer :all]
            [webvis.html :refer :all]
            [clojure.zip :as zip]))

(def html-str
  "<html><head><title>Test</title></head><body><div id='foo'></div><a href='http://example.com'></a></body></html>")

(def default-zipper
  (html-zip html-str))

(defn zipper? [z]
  (contains? (meta z) :zip/make-node))



(deftest test-html-zip
  (is (zipper? default-zipper))
  (is (zipper? default-zipper)))

(deftest test-body
  (is (= :body (-> default-zipper body zip/down zip/node))))

(deftest test-elem-name
  (is (= :body (-> default-zipper body elem-name))))

(deftest test-next-elem
  (let [loc (next-elem default-zipper)]
    (testing "arity 1"
      (is (= :head (elem-name loc)))
      (is (= :title (-> loc next-elem elem-name)))
      (is (zip/end? (-> loc next-elem next-elem next-elem next-elem next-elem next-elem))))
    (testing "arity 2"
      (is (= :a (-> loc (next-elem :a) elem-name)))
      (is (= :div (-> loc (next-elem :div) elem-name)))
      (is (zip/end? (next-elem loc :span))))))

(deftest test-elem-attr
  (let [loc (-> default-zipper body next-elem)]
    (is (= "foo" (elem-attr loc :id)))))
