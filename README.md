# docufant

[![master](https://img.shields.io/travis/xlevus/docufant-clj/master.svg?style=for-the-badge)](https://travis-ci.org/xlevus/docufant-clj)


A Clojure library to treat Postgresql as a document store.

## Usage


```clojure

(require '[docufant.core :as doc])
(require '[docufant.db :as db])


(def db-spec {:dbtype "postgresql" :dbname "my_doc_store" :user "user" :password "pass"})
(doc/init! db-spec)

(doc/create! :staff {:name "Agnes"
                     :salary {:annual 50000}
                              :pension 5000})
; {:name "Agnes", :id [:user 1], ...}

;; Set a default connection
(db/set-default-connection! db-spec)

(doc/create! :staff {:name "Bert" 
                     :salary {:annual 10000}})
; {:name "Bert", :id [:user 2], ...}

(doc/create! :customer {:name "Edward"})
; {:name "Edward", :id [:customer 3]}


;; Select everything
(doc/select nil [])
; [{:name "Agnes" ...}
   {:name "Bert" ...}
   {:name "Edward" ...}]
   
   
;; Select specific types
(doc/select :customer [])
; [{:name "Edward" ...}]


;; Select by JSON operations
(doc/select :staff [[:= [:salary :annual] 10000]])
; [{:name "Bert" ...}]

(doc/select :staff [[:has-keys :salary [:annual :pension]]])
; [{:name "Agnes" ...}]

;; Get a specific row
(doc/get [:customer 3])
; {:name "Edward" ...}

```

## TODO

* Clojars
* Documentation
* User-specified indexes on jsonb queries. 
* `<`, `>` and other value checks.
* linking between documents.
* Limit, offset and ordering
* `swap!`
* Callbacks by type read/write
