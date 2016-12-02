(ns export-server.sharing.storage
  (:require [ring.middleware.session.store :refer [SessionStore]]
            [export-server.db.core :as db]
            [export-server.sharing.twitter-utils :refer [timestamp]]
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all]
            [camel-snake-kebab.core :refer [->kebab-case]]
            [camel-snake-kebab.extras :refer [transform-keys]])
  (:import (java.util UUID)))

(defonce sn {:twitter   1
             :facebook  2
             :linkedin  3
             :pinterest 4})

;; local storage expired time: 1 day
(defonce ^:const expired (* 60 60 24))

;; for db connecion
(defonce state (atom {}))

;; local storage: for imgs
(defonce memory (atom {}))

(defn id->sn [id]
  (first (first (filter #(= id (second %)) sn))))

(defn sn->id [name]
  (get sn (keyword name)))

(def db-spec-prod {:classname   "com.mysql.cj.jdbc.Driver"
                   :subprotocol "mysql"
                   :subname     "//localhost:3306/export_prod?characterEncoding=UTF-8&serverTimezone=UTC"
                   :user        "export_prod_user"
                   :password    "prodpass"
                   :stringtype  "unspecified"})

(def db-spec-stg {:classname   "com.mysql.cj.jdbc.Driver"
                  :subprotocol "mysql"
                  :subname     "//localhost:3306/export_stg?characterEncoding=UTF-8&serverTimezone=UTC"
                  :user        "export_stg_user"
                  :password    "stgpass"
                  :stringtype  "unspecified"})

(defn init [mode]
  (let [db-spec (case mode
                  "prod" db-spec-prod
                  db-spec-stg)
        conn (db/connection-pool db-spec)]
    (reset! state {:mode mode
                   :conn conn})))

(defn read-db [key]
  (let [auths (db/query @state (-> (select :sn :oauth-token :oauth-token-secret)
                                   (from :auth) (where [:= key :session])))
        result (reduce #(assoc %1
                         (id->sn (:sn %2))
                         (transform-keys ->kebab-case (dissoc %2 :sn)))
                       {} auths)]
    ; (prn "Storage Read session: " key result)
    result))

(defn delete-db [key]
  ; (prn "Storage Delete session: " key)
  (db/exec @state (-> (delete-from :auth) (where [:= key :session]))))

(defn write-db [key data]
  (delete-db key)
  ; (prn "Storage write session: " key data)
  (let [insert-rows (mapv (fn [[sn {token        :oauth-token
                                    token-secret :oauth-token-secret}]]
                            [key (sn->id sn) token token-secret]) data)]
    (db/exec @state (-> (insert-into :auth)
                        (columns :session :sn :oauth_token :oauth_token_secret)
                        (values insert-rows)))))

(defn read-local [key]
  (get @memory key))

(defn write-local [key data]
  (swap! memory assoc key data))

(defn delete-local [key]
  (swap! memory dissoc key))

(defn clear-old-local []
  (swap! memory (fn [memory]
                  (let [now (timestamp)
                        keys (->> memory
                                  (filter (fn [[_ {time :time}]] (> now (+ time expired))))
                                  (map first))]
                    (reduce #(dissoc %1 %2) memory keys)))))

;; session storage
(deftype DbStore []
  SessionStore
  (read-session [_ key]
    {:db    (read-db key)
     :local (read-local key)})

  (write-session [_ key data]
    (let [key (or key (str (UUID/randomUUID)))]
      (clear-old-local)
      (when (:db data)
        (write-db key (:db data)))
      (when (:local data)
        (write-local key (:local data)))
      key))

  (delete-session [_ key]
    (delete-db key)
    (delete-local key)
    nil))

(defn create-storage []
  (DbStore.))