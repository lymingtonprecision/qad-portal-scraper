(defproject lymingtonprecision/qad-portal-scraper "0.1.0-SNAPSHOT"
  :description "A library for automating interaction with QAD Supplier Portal sites"
  :url "https://github.com/lymingtonprecision/qad-portal-scraper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [prismatic/schema "1.0.4"]
                 [clj-time "0.11.0"]
                 [http-kit "2.1.19"]
                 [enlive "1.1.6"]]
  :plugins [[lein-codox "0.9.4"]]
  :codox {:source-uri "https://github.com/lymingtonprecision/qad-portal-scraper/blob/master/{filepath}#{line}"
          :output-path "./gh-pages"
          :doc-paths []
          :metadata {:doc/format :markdown}})
