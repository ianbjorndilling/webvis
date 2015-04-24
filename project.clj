(defproject webvis "0.1.0-SNAPSHOT"
  :description "A visual web crawler."
  :url "http://github.com/ianbjorndilling/webvis"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main webvis.core
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.graphstream/gs-core "1.2"]
                 [clj-tagsoup/clj-tagsoup "0.3.0" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [http-kit "2.1.16"]
                 [commons-validator "1.4.1"]
                 [commons-httpclient/commons-httpclient "3.1"]
                 [org.clojure/data.priority-map "0.0.7"]]
  :plugins [[codox "0.8.11"]
            [lein-bin "0.3.4"]]
  :codox {:defaults {:doc/format :markdown}}
  :profiles {:uberjar {:aot :all}}
  :bin { :name "webvis" })


