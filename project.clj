(defproject export-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Dphantomjs.binary.path=/phantomjs/bin/phantomjs"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;http
                 [http-kit "2.1.13"]
                 [compojure "1.1.6"]
                 [ring "1.2.2"]

                 ;phantomJS
                 [org.apache.httpcomponents/httpclient "4.3.1"]
                 [clj-webdriver/clj-webdriver "0.6.0"]
                 [com.github.detro.ghostdriver/phantomjsdriver "1.1.0"]

                 ;params validation
                 [bouncer "0.3.2"]

                 ;command line args
                 [org.clojure/tools.cli "0.3.1"]

                 ;rasterization
                 [clj-pdf "1.11.17"]
                 [tikkba "0.5.0"]

                 ;JSON
                 [cheshire "5.3.1"]

                 ;tests
                 [peridot "0.2.2"]]
  :plugins [[lein-localrepo "0.5.3"]]
  :main ^:aot export-server.core
  :jar-name "anychart-export-server.jar"
  :uberjar-name "anychart-export.jar"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})