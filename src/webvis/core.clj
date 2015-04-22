(ns webvis.core
  (:require [clojure.string :as str]
            [clojure.set :refer [union]]
            [clojure.core.async :as async :refer [<!! >!! <! >!]]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [webvis.url :as url]
            [webvis.html :as html]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.set :refer [union]])
  (:import [org.graphstream.graph.implementations Graphs MultiGraph]
           [org.graphstream.ui.swingViewer Viewer$CloseFramePolicy]
           [java.util.concurrent PriorityBlockingQueue])
  (:gen-class))


(defrecord ^:private ^:no-doc Spider [ch-out ch-pending workers blacklist])

(def user-agent-str "Webvis bot.")

(def queued-links
  (PriorityBlockingQueue. 100 #(< (:depth %1) (:depth %2))))
(def found-urls (atom #{}))
(def web (Graphs/synchronizedGraph (MultiGraph. "Webvis Graph")))

(defn- default-display! []
  (.display web))

(defn display!
  "Displays the window the web graph will render in."
  []
  (doto (default-display!)
    (.setCloseFramePolicy Viewer$CloseFramePolicy/CLOSE_VIEWER)))

(defn clear!
  "Clears the web graph. Disclaimer: This function is currently broken
  and should not be used. The synchornizedGraph will not clear
  synchornized node references, causing some errors when rebuilding
  the web."
  []
  (reset! found-urls {})
  (.clear queued-links)
  (.clear web))

(defn- add-node! [label]
  (if-let [node (.getNode web label)]
    node
    (doto (.addNode web label)
      (.setAttribute "ui.label" (into-array String [label])))))

(defn- add-edge! [from to]
  (let [edge-name (str from "->" to)
        edge (.getEdge web edge-name)]
    (when-not edge
      (let [node-from (add-node! from)
            node-to (add-node! to)]
        (.addEdge web edge-name node-from node-to true)))))

(defn- html? [res]
  (when-let [content-type (-> res :headers :content-type)]
    (re-find #"^text\/html" content-type)))

(defn- meta-name-fragment
  "Returns the first found meta tag named 'fragment' with a content of
  '!'. See:
  https://developers.google.com/webmasters/ajax-crawling/docs/specification"
  [loc]
  (first (filter #(and (= (:name %) "fragment")
                       (= (:content %) "!"))
                 (html/meta-tags loc))))

(defn- url->label [url]
  (re-find #"\w+\.\w+$" (url/get-host url)))

(defn- matching-host? [a b]
  (= (url->label a) (url->label b)))

(defn- fetch [url out pending opts]
  (http/get url (merge {:as :text :user-agent user-agent-str} opts)
            #(async/go
               (when (html? %)
                 (>! out %))
               (<! pending))))

(defn- start-fetching [url ch-out ch-pending max-depth]
  (loop [req {:url url :depth 0}]
    (if (and (>!! ch-pending (:url req))
             (or (neg? max-depth) (<= (:depth req) max-depth)))
      (do (fetch (:url req) ch-out ch-pending (dissoc req :url))
          (recur (.take queued-links)))
      (do (log/info "Killing spider.")
          (async/close! ch-pending)
          (async/close! ch-out)))))

(defn- blacklisted? [blacklist url]
  (if (some #(re-find (re-pattern (str % "$")) (url/get-host url))
            blacklist)
    true false))

(defn- remove-blacklisted [blacklist links]
  (remove (partial blacklisted? blacklist) links))

(defn- extract-links [context res-body blacklist]
  (let [zhtml (html/html-zip res-body)]
    (if (meta-name-fragment zhtml)
      (seq [(url/escaped-fragment context)])
      (->> (html/hrefs zhtml)
           (map (partial url/normalize context))
           (filter url/valid?)
           (remove (partial matching-host? context))
           (remove-blacklisted blacklist)))))

(defn- register-found! [links]
  (loop []
    (let [old-val @found-urls
          new-val (union old-val (set links))]
      (if (compare-and-set! found-urls old-val new-val)
        old-val
        (recur)))))

(defn- enqueue-links! [links context depth]
  (doseq [link links]
    (.put queued-links {:url link
                        :depth (inc depth)
                        :from-url context})))

(defn- remove-found [links redirects]
  (let [found (register-found! (union links (set redirects)))]
    (remove found links)))


(defn- start-working
  [ch-in workers blacklist]
  (loop [{:keys [body status error]
          {:keys [trace-redirects url depth from-url]} :opts} (<!! ch-in)]
    (cond
      error
      (log/info error)
      
      (>= status 400)
      (log/info "Server error at" url "- Status:" status)

      (not (blacklisted? blacklist url))
      (do
        (log/info "Crawling" url)
        (try
          (-> (extract-links url body blacklist)
              (remove-found trace-redirects)
              (enqueue-links! url depth))
          (when from-url
            (add-edge! (url->label from-url) (url->label url)))
          (catch Exception e (log/error e)))))
    (when (contains? @workers (.getName (Thread/currentThread)))
      (when-let [next (<!! ch-in)]
        (recur next)))))

(defn spawn-worker
  "Adds another worker thread to the spider."
  [spider]
  (let [workers (:workers spider)
        th (Thread.
            #(start-working (:ch-out spider) workers (:blacklist spider)))]
    (.setName th (str "Worker-" (.getId th)))
    (swap! workers assoc (.getName th) th)
    (.start th)))

(defn create-spider
  "Returns a spider record with a specified maximum concurrent http
  requests and an optional set of blacklisted domains."
  ([max-concurrent-reqs blacklist]
   (->Spider (async/chan)
            (async/chan max-concurrent-reqs)
            (atom {})
            (set blacklist)))
  ([max-concurrent-reqs]
   (create-spider max-concurrent-reqs #{})))

(defn- build-web-blocking
  [spider url worker-count max-depth]
  (let [n-url (url/normalize url)
        label (url->label n-url)]
    (dotimes [n worker-count]
      (spawn-worker spider))
    (when-not (.getNode web label)
      (add-node! label))
    (start-fetching n-url (:ch-out spider) (:ch-pending spider) max-depth)))

(defn build-web
  "Begins the process of crawling sites, starting at the specified
  root url. The crawler will not crawl beyond the specified max
  depth. Once there are no more urls to crawl, it will terminate."
  [spider url worker-count max-depth]
  (async/thread
    (build-web-blocking spider url worker-count max-depth)))

(defn freeze!
  "Kills all workers, freezing the spider until more workers are added."
  [spider]
  (let [workers (:workers spider)
        w-ths (vals @workers)]
    (reset! workers {})
    (doseq [th w-ths]
      (.join th))))

(defn kill!
  "Kills the spider, terminating any running threads associated with
  it and closing all channels. If a worker id is passed in, it will
  kill the worker with that id instead."
  ([spider]
   (freeze! spider)
   (async/close! (:ch-out spider))
   (async/close! (:ch-pending spider)))
  ([spider wid]
   (let [workers (:workers spider)
         w-th (-> wid workers :thread)]
     (swap! (:workers spider) disj wid)
     (.join w-th))))


(def cli-opts
  [["-d" "--depth" "Maximum depth to search. A depth of -1 will continue indefinately. Defaults to -1."
    :default -1
    :parse-fn #(Integer/parseInt %)]
   ["-b" "--blacklist" "Domains to avoid."
    :parse-fn #(str/split % #"\s+")]
   ["-w" "--workers" "Number of workers. Defaults to 1."
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 %) "Must have at least 1 worker."]]
   ["-c" "--concurrency" "The maximum number of concurrent http requests. Defaults to 2."
    :default 2
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 %) "Must allow at least 1 request."]]
   ["-h" "--help"]])


(defn -main [& args]
  (let [{:keys [arguments options summary errors]}
        (parse-opts args cli-opts)]
    (if (:help options)
      (println summary)
      (let [{:keys [depth workers concurrency blacklist]} options
            sp (create-spider concurrency blacklist)]
        (default-display!)
        (build-web-blocking sp (first args) workers depth)))))
