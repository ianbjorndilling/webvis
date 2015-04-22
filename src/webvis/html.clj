(ns webvis.html
  (:require [clojure.zip :as zip]
            [pl.danieljanus.tagsoup :as tag :refer [parse-string]]))

(defn html-zip
  "Returns a zipper for html using tag-soup/parse-string."
  [html]
  (-> html parse-string zip/vector-zip))

(defn body
  "Returns the loc of the body element in an html zipper."
  [z]
  (-> z zip/root zip/vector-zip zip/down zip/rightmost))

(defn elem-name
  "Returns the name keyword for the element node at loc."
  [loc]
  (when-not (zip/end? loc)
    (-> loc zip/next zip/node)))

(defn elem-attrs
  "Returns a map of element attributes."
  [loc]
  (when-not (zip/end? loc)
    (-> loc zip/next zip/next zip/node)))

(defn elem-attr
  "Returns the specified attribute value for the element node at loc."
  [loc key]
  (key (elem-attrs loc)))

(defn next-elem
  "Returns the loc of the next html element node in the zipper or the
  next node of specified name in the zipper depth first."
  ([loc]
   (let [next-loc (-> loc zip/next zip/next zip/next)]
     (if-not (or (zip/end? next-loc) (zip/branch? next-loc))
       (zip/next next-loc)
       next-loc)))
  ([loc key]
   (loop [nloc (next-elem loc)]
     (if (or (zip/end? nloc) (= (elem-name nloc) key))
       nloc
       (recur (next-elem nloc))))))


(defn meta-tags
  "Returns a sequence containing the attributes of each meta tag."
  [loc]
  (loop [next (next-elem loc)
         tags []]
    (cond
       (or (zip/end? next) (= (elem-name next) :body))
       tags
       
       (= (elem-name next) :meta)
       (recur (next-elem next) (conj tags (elem-attrs next)))
       
       :else
       (recur (next-elem next) tags))))

(defn hrefs
  "Returns a sequence of all anchor tag hrefs."
  [loc]
  (loop [anchor (-> loc body (next-elem :a))
         links []]
    (if (zip/end? anchor)
      (seq links)
      (let [href (elem-attr anchor :href)
            rel (elem-attr anchor :rel)
            next (next-elem anchor :a)]
        (if (and (seq href) (not= rel "nofollow"))
          (recur next (conj links href))
          (recur next links))))))
