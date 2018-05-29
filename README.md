# docufant

[![master](https://img.shields.io/travis/xlevus/docufant-clj/master.svg?style=for-the-badge)](https://travis-ci.org/xlevus/docufant-clj)

A postgres backed document store library. Supports traditional SQL features such as transactions, indexes, unique values, and strict relationships between documents, while foregoing strict schemas.

Under the hood, docufant is powered by HoneySQL and java.jdbc.


[![Clojars Project](https://img.shields.io/clojars/v/docufant.svg?style=for-the-badge)](https://clojars.org/docufant)


## Basic Usage


```clojure

(require '[docufant.core :as doc])


(def db-spec {:dbtype "postgresql" :dbname "my_doc_store" :user "user" :password "pass"})
(doc/init! db-spec)

(def agnes (doc/create! db-spec :staff {:name "Agnes" :salary {:annual 50000}}))
; {:name "Agnes", :id [:staff 1], ...}

(def bert (doc/create! db-spec :staff {:name "Bert" :salary {:annual 10000}}))
; {:name "Bert", :id [:staff 2], ...}

(def edward (doc/create! db-spec :customer {:name "Edward"}))
; {:name "Edward", :id [:customer 3]}


;; Select everything
(doc/select db-spec nil [])
; [{:id [:staff 1], :name "Agnes" ...}
;  {:id [:staff 2], :name "Bert" ...}
;  {:id [:customer 3], :name "Edward" ...}]
   
   
;; Select specific types
(doc/select db-spec :customer [])
; [{:id [:customer 3], :name "Edward" ...}]


;; Select by JSON operations
(doc/select db-spec :staff (doc/= [:salary :annual] 10000))
; [{:name "Bert" ...}]

(doc/select db-spec :staff (doc/> [:salary :annual] 10000))
; [{:name "Agnes" ...}]

;; Get a specific row
(doc/get db-spec [:customer 3])
; {:name "Edward" ...}

;; Limit/offset/order results
(doc/select db-spec :staff 
            (doc/> [:salary :annual] 1) 
            :limit 1
            :offset 1
            :order-by [[:salary :annual] :desc])
            
            
```


## Indexes
Fields within documents can be indexed, allowing faster retrival of rows and unique values in given document keys.

Additionally, a GIN index can be created to speed up queries on all fields.

```clojure
##
(require '[docufant.core :as doc])


(doc/init! db-spec 
           ;; Add a GIN index to generally speed things up.
           {:index-type :gin :type nil}
           ;; Ensure all Staff have unique email addresses.
           {:type :staff :path [:contact :email] :unique true})
```


## Document Links

Documents can be linked together with named one-directional relationships, and then queried by
these relationships.

```clojure
(doc/link! db-spec :boss agnes bert)
; nil

;; Select linked instances
(doc/select db-spec :staff :linked-to [:boss agnes])
; [{:name "Bert" ...}]
```


## Benchmarks

A trivial benchmark script has been written. At the moment, it just tests a simple three-column table.

The benchmark can be run with:

```bash
lein with-profile +benchmark run
```

### Results:

| Engine   | Indexes | Benchmark           | Time (ms) |
+----------|---------|---------------------|-----------|
| Docufant | No      | Insert 10k Rows     | 109389.13 |
| Docufant | No      | Select butchers     |     68.92 |
| Docufant | No      | Select old butchers |     48.89 |
| SQL      | No      | Insert 10k Rows     |  68324.54 |
| SQL      | No      | Select butchers     |     24.53 |
| SQL      | No      | Select old butchers |     13.99 |
| Docufant | Yes     | Select butchers     |     51.82 |
| Docufant | Yes     | Select old butchers |     37.74 |
| SQL      | Yes     | Select butchers     |     15.37 |
| SQL      | Yes     | Select old butchers |     14.97 |

## TODO

* Documentation
* `swap!`
* Callbacks by type read/write
