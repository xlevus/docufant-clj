(ns docufant.db-test
  (:require [docufant.db :as db]
            [docufant.postgres :as pg]
            [docufant.options :refer [with-options]]
            [honeysql.core :as sql]
            [clojure.test :as t :refer [deftest testing is]]))



(deftest test-indexname
  (with-options {}
    (is (= "dfidx_docufant_a_b"
           (db/indexname {:path [:a :b] })))

    (is (= "dfidx_docufant_a_b__gin"
           (db/indexname {:path [:a :b]
                             :index-type :gin})))

    (is (= "dfidx_docufant_a__mytype__uniq"
           (db/indexname {:path [:a]
                             :type :mytype
                             :unique true})))

    (is (= "dfidx_docufant_a_b_c__uniq"
           (db/indexname {:path [:a :b :c]
                             :unique true})))
    ))


(deftest test-indexes
  (with-options {}
    (is (= ["CREATE UNIQUE INDEX IF NOT EXISTS dfidx_docufant_p_p__uniq ON docufant ((_data #> '{p,p}')) WHERE TRUE"]
           (-> (db/build-index {:unique true
                                :path [:p :p]}))))

    (is (= ["CREATE INDEX IF NOT EXISTS dfidx_docufant_p__animal ON docufant ((_data -> 'p')) WHERE _type = 'animal'"]
           (-> (db/build-index {:type :animal
                                :path [:p]}))))

    (is (= ["CREATE INDEX IF NOT EXISTS dfidx_docufant___gin__animal ON docufant USING gin(_data jsonb_ops) WHERE _type = 'animal'"]
           (-> (db/build-index {:index-type :gin
                                :type :animal}))))
    ))
