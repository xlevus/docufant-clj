(ns docufant.db-test
  (:require [docufant.db :as db]
            [docufant.postgres :as pg]
            [honeysql.core :as sql]
            [clojure.test :as t :refer [deftest testing is]]))


(def ^:dynamic *db-spec* {})


(deftest test-indexname
  (is (= "dfidx_docufant_a_b"
         (db/indexname {} {:path [:a :b] })))

  (is (= "dfidx_docufant_a_b__gin"
         (db/indexname {} {:path [:a :b]
                           :index-type :gin})))

  (is (= "dfidx_docufant_a__mytype__uniq"
         (db/indexname {} {:path [:a]
                           :type :mytype
                           :unique true})))

  (is (= "dfidx_docufant_a_b_c__uniq"
         (db/indexname {} {:path [:a :b :c]
                           :unique true})))

  (is (= "dfidx_foo_a_b"
         (db/indexname {:tablename "foo" :db-spec {}} {:path [:a :b] })))

  )


(deftest test-indexes
  (is (= ["CREATE UNIQUE INDEX IF NOT EXISTS dfidx_docufant_p_p__uniq ON docufant ((_data #> ?)) WHERE TRUE"
          (pg/text-array [:p :p])]
         (-> (db/build-index *db-spec* {:unique true
                                        :path [:p :p]})
             (sql/format))))

  (is (= ["CREATE INDEX IF NOT EXISTS dfidx_docufant___gin__animal ON docufant USING gin(_data jsonb_ops) WHERE _type = ?"
          "animal"]
         (-> (db/build-index *db-spec* {:index-type :gin
                                        :type :animal})
             (sql/format))))
  )
