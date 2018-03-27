# docufant

[![master](https://img.shields.io/travis/xlevus/docufant-clj/master.svg?style=for-the-badge)](https://travis-ci.org/xlevus/docufant-clj)

A HoneySQL wrapper facilitating using Postgresql as a document store.


[![Clojars Project](https://img.shields.io/clojars/v/docufant.svg?style=for-the-badge)](https://clojars.org/docufant)


## Usage


```clojure

(require '[docufant.core :as doc])
(require '[docufant.db :as db])


(def db-spec {:dbtype "postgresql" :dbname "my_doc_store" :user "user" :password "pass"})
(doc/init! db-spec)

(doc/create! db-spec :staff {:name "Agnes" :salary {:annual 50000}})
; {:name "Agnes", :id [:staff 1], ...}


(doc/create! :staff {:name "Bert" :salary {:annual 10000}})
; {:name "Bert", :id [:staff 2], ...}

(doc/create! :customer {:name "Edward"})
; {:name "Edward", :id [:customer 3]}


;; Select everything
(doc/select nil [])
; [{:id [:staff 1] :name "Agnes" ...}
   {:id [:staff 2] :name "Bert" ...}
   {:id [:customer 3] :name "Edward" ...}]
   
   
;; Select specific types
(doc/select :customer [])
; [{:id [:customer 3] :name "Edward" ...}]


;; Select by JSON operations
(doc/select :staff (doc/= [:salary :annual] 10000))
; [{:name "Bert" ...}]

(doc/select :staff (doc/> [:salary :annual] 10000))
; [{:name "Agnes" ...}]

;; Get a specific row
(doc/get [:customer 3])
; {:name "Edward" ...}

;; Limit/offset/order results
(doc/select :staff 
            (doc/> [:salary :annual] 1) 
            :limit 1
            :offset 1
            :order-by [[:salary :annual] :desc])
```


## TODO

* Documentation
* User-specified indexes on jsonb queries. 
* linking between documents.
* `swap!`
* Callbacks by type read/write
