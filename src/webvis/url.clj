(ns webvis.url
  (:require [clojure.string :as str])
  (:import [org.apache.commons.httpclient.util URIUtil]
           [org.apache.commons.httpclient URI]
           [org.apache.commons.validator.routines UrlValidator]))

(defn valid? [spec]
  (.isValid (UrlValidator. (into-array ["http" "https"])) spec))

(defn encode [spec]
  (URIUtil/encodePath spec "UTF-8"))

(defn decode [spec]
  (URIUtil/decode spec "UTF-8"))

(defn- hashbang?
  "Returns true if the URI has a hashbang fragment"
  [uri]
  (if-let [fragment (.getFragment uri)]
    (and (not (empty? fragment)) (= (.charAt fragment 0) \!))
    false))

(defn- fragment->query
  "Returns an escaped fragment string with & symbol also escaped. Use
  for moving the fragment to the query string."
  [uri]
  (-> uri
      .getEscapedFragment
      (str/replace #"&" "%26")
      (subs 1)))

(defn- query-str [qs s]
  (if (seq qs)
    (str qs "&" s)
    s))

(defn- escaped-fragment!
  [uri]
  (let [qs (.getEscapedQuery uri)]
    (doto uri
      (.setEscapedQuery (query-str qs "_escaped_fragment_=")))))

(defn escaped-fragment
  [spec]
  (str (escaped-fragment! (URI. spec true))))

(defn- spiderable! [uri]
  (when (hashbang? uri)
    (let [fr (fragment->query uri)]
      (doto uri
        escaped-fragment!
        (.setEscapedQuery (str (.getEscapedQuery uri) fr))
        (.setFragment nil))))
  uri)

(defn- normalize-no-path! [uri]
  (when-not (.getPath uri)
    (.setPath uri "/")))

(defn normalize
  ([spec]
   (str (doto (URI. (decode spec))
          .normalize
          normalize-no-path!
          spiderable!)))
  ([context spec]
   (str (doto (URI. (URI. (decode context)) (decode spec))
          .normalize
          normalize-no-path!
          spiderable!))))

(defn get-host
  "Returns the URI's hostname."
  [url]
  (.getHost (URI. url true)))
