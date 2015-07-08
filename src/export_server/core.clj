(ns export-server.core
  (:use [compojure.route :only [not-found]]
        org.httpkit.server
        compojure.core)
  (:require [ring.util.response]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [export-server.web-handlers :as web]
            [export-server.cmd-handlers :as cmd]
            [export-server.utils.rasterizator :as rasterizontor]
            [export-server.utils.dictionary :as dict]
            [compojure.route :as route]
            [clojure.java.io :as io])
  (:gen-class))


;====================================================================================
; Server Usage
;====================================================================================
(defn server-usage [options-summary]
  (->> ["AnyChart Export Server"
        ""
        "Action: server"
        "Usage: java -jar anychart-export.jar server [options]"
        "See https://github.com/AnyChart/export-server for HTTP API."
        ""
        "Options:"
        options-summary
        ""
        "Please, see http://docs.anychart.com for more info."
        ]
       (string/join \newline)))


;====================================================================================
; Command Line Usage
;====================================================================================

(defn cmd-usage [options-summary]
  (->> ["AnyChart Export Server"
        ""
        "Action: cmd"
        "Usage: java -jar anychart-export.jar cmd [options]"
        ""
        "Options:"
        options-summary
        ""
        "Please, see http://docs.anychart.com for more info."
        ]
       (string/join \newline)))


(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))


;====================================================================================
; Common Usage
;====================================================================================
(def common-options
  [
   ;Server Args--------------------------------------------------------------------------------------------
   ["-P" "--port PORT" "Port number for the server."
    :default (:port dict/defaults)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--host HOST" "Ip, if has many ips to bind."
    :default (:host dict/defaults)
    ]
   ["-a" "--allow-scripts-executing ALLOW_SCRIPTS_EXECUTING" "Allow to execute violent scripts in phantom js."
    :parse-fn #(or (= "true" %) (= "1" %))
    :default true
    ]

   ;Command Line Common Args--------------------------------------------------------------------------------------------
   ["-s" "--script SCRIPT" "JavaScript String to Execute."
    :default nil
    ]
   ["-i" "--input-file INPUT_FILE" "JavaScript file to Execute"
    :default nil
    ]
   ["-o" "--output-file OUTPUT_FILE" "Output File name, file extentions is optional."
    :default "anychart"
    ]
   ["-p" "--output-path OUTPUT_PATH" "Output File Directory"
    :default ""
    ]
   ["-t" "--type TYPE" "Type of the output file."
    :default (:type dict/defaults)
    :validate [#(contains? #{"png" "jpg" "svg" "pdf"} %) "Type must be one of the following types: png, jpg, svg, pdf"]]

   ["-c" "--container-id CONTAINER_ID" "Container id."
    :default (:container-id dict/defaults)
    ]

   ["-W" "--container-width CONTAINER_WIDTH" "Container width."
    :default (:container-width dict/defaults)
    ]

   ["-L" "--container-height CONTAINER_HEIGHT" "Container height"
    :default (:container-height dict/defaults)
    ]


   ;Export Images Args--------------------------------------------------------------------------------------------------
   ["-w" "--image-width IMAGE_WIDTH" "Image width."
    :default (:image-width dict/defaults)
    :parse-fn #(Integer/parseInt %)
    ]

   ["-l" "--image-height IMAGE_HEIGHT" "Image height"
    :default (:image-height dict/defaults)
    :parse-fn #(Integer/parseInt %)
    ]
   ["-f" "--force-transparent-white FORCE_TRANSPARENT_WHITE" "Force transparent to white"
    :default (:force-transparent-white dict/defaults)
    ]

   ["-q" "--jpg-quality JPG_QUALITY" "Image quality,"
    :default (:jpg-quality dict/defaults)
    :parse-fn #(Float/parseFloat %)
    ]


   ;Export PDF Args--------------------------------------------------------------------------------------------------
   ["-S" "--pdf-size PDF-SIZE" "PDF Size"
    :default (:pdf-size dict/defaults)
    :parse-fn #(keyword %)
    ]

   ["-x" "--pdf-x PDF-X" "Pad X"
    :default (:pdf-x dict/defaults)
    :parse-fn #(Integer/parseInt %)
    ]

   ["-y" "--pdf-y PDF-Y" "Pdf Y"
    :default (:pdf-y dict/defaults)
    :parse-fn #(Integer/parseInt %)
    ]

   ["-O" "--pdf-landscape PDF-LANDSCAPE" "PDF Orientation"
    :default (:pdf-landscape dict/defaults)
    ]

   ;Export PDF Args--------------------------------------------------------------------------------------------------
   ["-h" "--help"]
   ])

(defn usage []
  (->> ["AnyChart Export Server"
        ""
        "Usage: java -jar anychart-export.jar action [options]"
        "Actions:"
        "  server    Start a new instance of AnyChart Export Server."
        "            Use --help arg with action for more info."
        ""
        "  cmd       Run Export Server once"
        "            Use --help arg with action for more info."
        ""
        "Please, see http://docs.anychart.com for more info."
        ]
       (string/join \newline)))


;====================================================================================
; Actions
;====================================================================================

(defroutes app-routes
           (GET "/status" [] "ok")
           (POST "/status" [] "ok")
           (POST "/png" [] web/png)
           (POST "/jpg" [] web/jpg)
           (POST "/svg" [] web/svg)
           (POST "/pdf" [] web/pdf)
           (route/not-found "<p>Page not found.</p>"))

(def app (-> app-routes wrap-params))

(defn start-server [options summary]
  (if (:help options) (exit 0 (server-usage summary)))
  (prn (str "Starting export server on " (:host options) ":" (:port options)))
  (rasterizontor/setup-phantom)
  (run-server app {:port (:port options) :ip (:host options)}))


(defn cmd-export [options summary]
  (if (:help options) (exit 0 (cmd-usage summary)))
  (let [script (:script options)
        file (:input-file options)]
    (cond
      (and (nil? script) (nil? file)) (exit 1 (error-msg ["script or file should be specified in 'cmd' mode."]))
      (and file (not (.exists (io/file file)))) (exit 1 (error-msg ["Input File not exists."]))
      :else (case (:type options)
              "png" (cmd/png options)
              "jpg" (cmd/jpg options)
              "svg" (cmd/svg options)
              "pdf" (cmd/pdf options)))))



;====================================================================================
; Main
;====================================================================================
(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args common-options)]
    (cond (not= (count arguments) 1) (exit 1 (usage))
          errors (exit 1 (error-msg errors)))
    (reset! web/allow-script-executing (:allow-scripts-executing options))
    (case (first arguments)
      "server" (start-server options summary)
      "cmd" (cmd-export options summary)
      (exit 1 (usage)))))